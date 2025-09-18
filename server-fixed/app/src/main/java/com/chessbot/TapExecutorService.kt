package com.chessbot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.*

/**
 * TapExecutorService implements AccessibilityService for executing tap gestures.
 * 
 * Key responsibilities:
 * - Execute tap1 → tap2 sequence for chess moves using real gesture dispatch
 * - Handle coordinate-based gesture execution on Android device
 * - Manage service state (enabled/paused)
 * - Provide singleton access for MainActivity coordination
 */
class TapExecutorService : AccessibilityService() {
    
    companion object {
        private const val TAG = "TapExecutorService"
        private var instance: TapExecutorService? = null
        
        fun getInstance(): TapExecutorService? = instance
    }
    
    private lateinit var boardMapper: BoardMapper
    private var isServiceEnabled = false
    private var isPaused = false
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isServiceEnabled = true
        boardMapper = BoardMapper()
        Log.d(TAG, "TapExecutorService connected and ready")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isServiceEnabled = false
        Log.d(TAG, "TapExecutorService destroyed")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to handle accessibility events for our use case
        // This service is only used for gesture dispatch
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "TapExecutorService interrupted")
    }
    
    /**
     * Enable the tap execution service
     */
    fun enableService() {
        if (isServiceEnabled) {
            isPaused = false
            Log.d(TAG, "TapExecutorService enabled")
        }
    }
    
    /**
     * Disable the tap execution service
     */
    fun disableService() {
        isPaused = true
        Log.d(TAG, "TapExecutorService disabled")
    }
    
    /**
     * Pause gesture execution
     */
    fun pause() {
        isPaused = true
        Log.d(TAG, "TapExecutorService paused")
    }
    
    /**
     * Resume gesture execution
     */
    fun resume() {
        isPaused = false
        Log.d(TAG, "TapExecutorService resumed")
    }
    
    /**
     * Check if service is ready to execute gestures
     */
    fun isReady(): Boolean = isServiceEnabled && !isPaused
    
    /**
     * Execute a chess move as a tap1 → tap2 sequence
     */
    fun executeMove(move: BoardMapper.Move): Boolean {
        if (!isReady()) {
            Log.w(TAG, "TapExecutorService not ready. Enabled: $isServiceEnabled, Paused: $isPaused")
            return false
        }
        
        return try {
            Log.d(TAG, "Executing chess move: $move")
            
            // Validate coordinates are within board boundaries
            if (!boardMapper.isValidCoordinate(move.from)) {
                Log.e(TAG, "Invalid 'from' coordinate: ${move.from}")
                return false
            }
            
            if (!boardMapper.isValidCoordinate(move.to)) {
                Log.e(TAG, "Invalid 'to' coordinate: ${move.to}")
                return false
            }
            
            // Execute the tap sequence
            executeTapSequence(move.from, move.to)
            
            Log.d(TAG, "Move execution completed successfully")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error executing move", e)
            false
        }
    }
    
    /**
     * Execute the actual tap sequence with real Android gestures
     */
    private fun executeTapSequence(from: BoardMapper.Coordinate, to: BoardMapper.Coordinate) {
        Log.d(TAG, "Step 1: Tapping source square at $from")
        performTap(from)
        
        // Use Handler instead of Thread.sleep to avoid ANR
        Handler(Looper.getMainLooper()).postDelayed({
            Log.d(TAG, "Step 2: Tapping destination square at $to")
            performTap(to)
            Log.d(TAG, "Tap sequence completed: $from → $to")
        }, 150)
    }
    
    /**
     * Perform individual tap at specified coordinate using AccessibilityService.dispatchGesture()
     */
    private fun performTap(coordinate: BoardMapper.Coordinate) {
        val gestureBuilder = GestureDescription.Builder()
        
        // Create a path for the tap gesture
        val path = Path()
        path.moveTo(coordinate.x.toFloat(), coordinate.y.toFloat())
        
        // Create stroke description for a tap (100ms duration)
        val strokeDescription = GestureDescription.StrokeDescription(path, 0, 100)
        gestureBuilder.addStroke(strokeDescription)
        
        // Dispatch the gesture
        val gestureResult = dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.d(TAG, "Tap gesture completed at (${coordinate.x}, ${coordinate.y})")
            }
            
            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.w(TAG, "Tap gesture cancelled at (${coordinate.x}, ${coordinate.y})")
            }
        }, null)
        
        if (!gestureResult) {
            Log.e(TAG, "Failed to dispatch tap gesture at (${coordinate.x}, ${coordinate.y})")
        }
    }
    
    /**
     * Execute multiple moves in sequence
     */
    fun executeMoveSequence(moves: List<BoardMapper.Move>, delayBetweenMoves: Long = 1000) {
        if (!isReady()) {
            Log.w(TAG, "TapExecutorService not ready for move sequence")
            return
        }
        
        Log.d(TAG, "Executing move sequence: ${moves.size} moves")
        
        // Use coroutines instead of Thread.sleep
        CoroutineScope(Dispatchers.Main).launch {
            moves.forEachIndexed { index, move ->
                if (!isPaused) {
                    Log.d(TAG, "Executing move ${index + 1}/${moves.size}")
                    executeMove(move)
                    
                    // Use coroutine delay instead of Thread.sleep
                    if (index < moves.size - 1) {
                        delay(delayBetweenMoves)
                    }
                } else {
                    Log.d(TAG, "Move sequence paused at move ${index + 1}")
                    return@launch
                }
            }
            Log.d(TAG, "Move sequence completed")
        }
    }
    
    /**
     * Set board mapper instance
     */
    fun setBoardMapper(mapper: BoardMapper) {
        boardMapper = mapper
    }
    
    /**
     * Validate tap coordinates against board boundaries
     */
    fun validateMoveCoordinates(move: BoardMapper.Move): Boolean {
        val fromValid = boardMapper.isValidCoordinate(move.from)
        val toValid = boardMapper.isValidCoordinate(move.to)
        
        if (!fromValid) {
            Log.w(TAG, "Invalid 'from' coordinate: ${move.from}")
        }
        
        if (!toValid) {
            Log.w(TAG, "Invalid 'to' coordinate: ${move.to}")
        }
        
        return fromValid && toValid
    }
    
    /**
     * Get service status information
     */
    fun getStatus(): String {
        return when {
            !isServiceEnabled -> "Disabled"
            isPaused -> "Paused"
            else -> "Ready"
        }
    }
    
    /**
     * Simulate emergency stop
     */
    fun emergencyStop() {
        isPaused = true
        Log.w(TAG, "EMERGENCY STOP: All tap execution halted")
    }
    
    /**
     * Get detailed service information for debugging
     */
    fun getServiceInfo(): Map<String, Any> {
        return mapOf(
            "enabled" to isServiceEnabled,
            "paused" to isPaused,
            "ready" to isReady(),
            "status" to getStatus(),
            "board_flipped" to boardMapper.isBoardFlipped()
        )
    }
}