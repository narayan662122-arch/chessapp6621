package com.chessbot

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

/**
 * OverlayController manages the real Android overlay UI controls for the chess bot application.
 * 
 * Key responsibilities:
 * - Create and manage actual overlay window using WindowManager
 * - Provide Start/Pause/Resume buttons for bot control
 * - Handle Board Flip toggle functionality
 * - Manage overlay visibility and interaction
 * - Coordinate between UI actions and service operations
 */
class OverlayController(
    private val context: Context,
    private val botPollerService: BotPollerService,
    private val boardMapper: BoardMapper
) {
    
    companion object {
        private const val TAG = "OverlayController"
    }
    
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private var isOverlayVisible = false
    private var currentState = BotState.STOPPED
    
    // UI Elements
    private var btnStartStop: Button? = null
    private var btnPauseResume: Button? = null
    private var btnFlipBoard: Button? = null
    
    /**
     * Possible states of the chess bot
     */
    enum class BotState {
        STOPPED,
        RUNNING,
        PAUSED
    }
    
    init {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }
    
    /**
     * Show the overlay controls
     */
    fun showOverlay() {
        if (isOverlayVisible) {
            Log.d(TAG, "Overlay is already visible")
            return
        }
        
        try {
            createOverlayView()
            addOverlayToWindow()
            isOverlayVisible = true
            updateButtonStates()
            Log.d(TAG, "Overlay displayed on screen")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show overlay", e)
        }
    }
    
    /**
     * Hide the overlay controls
     */
    fun hideOverlay() {
        if (!isOverlayVisible) {
            Log.d(TAG, "Overlay is already hidden")
            return
        }
        
        try {
            windowManager?.removeView(overlayView)
            overlayView = null
            isOverlayVisible = false
            Log.d(TAG, "Overlay hidden")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hide overlay", e)
        }
    }
    
    /**
     * Create the overlay view with buttons
     */
    private fun createOverlayView() {
        val inflater = LayoutInflater.from(context)
        overlayView = inflater.inflate(R.layout.overlay_layout, null)
        
        // Get button references
        btnStartStop = overlayView?.findViewById(R.id.btnStartStop)
        btnPauseResume = overlayView?.findViewById(R.id.btnPauseResume)
        btnFlipBoard = overlayView?.findViewById(R.id.btnFlipBoard)
        
        // Set click listeners
        btnStartStop?.setOnClickListener {
            when (currentState) {
                BotState.STOPPED -> onStartPressed()
                else -> onStopPressed()
            }
        }
        
        btnPauseResume?.setOnClickListener {
            when (currentState) {
                BotState.RUNNING -> onPausePressed()
                BotState.PAUSED -> onResumePressed()
                else -> { /* Do nothing */ }
            }
        }
        
        btnFlipBoard?.setOnClickListener {
            onFlipBoardPressed()
        }
    }
    
    /**
     * Add the overlay view to the window
     */
    private fun addOverlayToWindow() {
        val layoutParams = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            
            // Fix for Android 10 and MIUI compatibility
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            }
            
            // MIUI-specific flags for better compatibility
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.END
            x = 20 // Margin from right edge
            y = 100 // Margin from top
        }
        
        try {
            windowManager?.addView(overlayView, layoutParams)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay (MIUI restriction possible): ${e.message}")
            // Show user-friendly error for MIUI restrictions
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Overlay permission required for MIUI devices. Please enable manually.", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    /**
     * Update button states based on current bot state
     */
    private fun updateButtonStates() {
        when (currentState) {
            BotState.STOPPED -> {
                btnStartStop?.text = context.getString(R.string.start_game)
                btnPauseResume?.visibility = View.GONE
            }
            BotState.RUNNING -> {
                btnStartStop?.text = context.getString(R.string.stop_game)
                btnPauseResume?.text = context.getString(R.string.pause_game)
                btnPauseResume?.visibility = View.VISIBLE
            }
            BotState.PAUSED -> {
                btnStartStop?.text = context.getString(R.string.stop_game)
                btnPauseResume?.text = context.getString(R.string.resume_game)
                btnPauseResume?.visibility = View.VISIBLE
            }
        }
    }
    
    /**
     * Handle Start button press
     */
    fun onStartPressed() {
        if (currentState != BotState.STOPPED) {
            Log.w(TAG, "Cannot start: Bot is already ${currentState.name.lowercase()}")
            return
        }
        
        Log.d(TAG, "Starting chess bot...")
        
        // Enable tap executor service
        val tapExecutorService = TapExecutorService.getInstance()
        tapExecutorService?.enableService()
        
        // Start polling for Telegram messages
        botPollerService.startPolling()
        
        // Test bot connectivity
        botPollerService.testConnection()
        
        currentState = BotState.RUNNING
        updateButtonStates()
        Log.d(TAG, "Chess bot started successfully")
    }
    
    /**
     * Handle Pause button press
     */
    fun onPausePressed() {
        if (currentState != BotState.RUNNING) {
            Log.w(TAG, "Cannot pause: Bot is not running")
            return
        }
        
        Log.d(TAG, "Pausing chess bot...")
        
        // Pause tap executor
        val tapExecutorService = TapExecutorService.getInstance()
        tapExecutorService?.pause()
        
        // Note: We don't stop polling, just pause move execution
        currentState = BotState.PAUSED
        updateButtonStates()
        Log.d(TAG, "Chess bot paused")
    }
    
    /**
     * Handle Resume button press
     */
    fun onResumePressed() {
        if (currentState != BotState.PAUSED) {
            Log.w(TAG, "Cannot resume: Bot is not paused")
            return
        }
        
        Log.d(TAG, "Resuming chess bot...")
        
        // Resume tap executor
        val tapExecutorService = TapExecutorService.getInstance()
        tapExecutorService?.resume()
        
        currentState = BotState.RUNNING
        updateButtonStates()
        Log.d(TAG, "Chess bot resumed")
    }
    
    /**
     * Handle Stop button press
     */
    fun onStopPressed() {
        if (currentState == BotState.STOPPED) {
            Log.d(TAG, "Bot is already stopped")
            return
        }
        
        Log.d(TAG, "Stopping chess bot...")
        
        // Stop all services
        botPollerService.stopPolling()
        val tapExecutorService = TapExecutorService.getInstance()
        tapExecutorService?.disableService()
        
        currentState = BotState.STOPPED
        updateButtonStates()
        Log.d(TAG, "Chess bot stopped")
    }
    
    /**
     * Handle Flip Board button press
     */
    fun onFlipBoardPressed() {
        Log.d(TAG, "Toggling board flip...")
        boardMapper.flipBoard()
        
        // Set board mapper to tap executor service
        val tapExecutorService = TapExecutorService.getInstance()
        tapExecutorService?.setBoardMapper(boardMapper)
        
        Log.d(TAG, "Board coordinate mapping updated, flipped: ${boardMapper.isBoardFlipped()}")
    }
    
    /**
     * Get current overlay state
     */
    fun getCurrentState(): BotState = currentState
    
    /**
     * Check if overlay is visible
     */
    fun isOverlayVisible(): Boolean = isOverlayVisible
    
    /**
     * Emergency shutdown
     */
    fun emergencyShutdown() {
        Log.w(TAG, "EMERGENCY SHUTDOWN INITIATED")
        onStopPressed()
        val tapExecutorService = TapExecutorService.getInstance()
        tapExecutorService?.emergencyStop()
        hideOverlay()
        Log.w(TAG, "Emergency shutdown completed")
    }
}