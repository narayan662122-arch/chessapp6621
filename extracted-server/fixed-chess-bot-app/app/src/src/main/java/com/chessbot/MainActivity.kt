package com.chessbot

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import android.view.accessibility.AccessibilityManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*

/**
 * MainActivity serves as the entry point for the Chess Bot Android application.
 * 
 * Key responsibilities:
 * - Initialize all services and components
 * - Handle Android lifecycle management
 * - Request necessary permissions (SYSTEM_ALERT_WINDOW, Accessibility)
 * - Coordinate communication between services
 */
class MainActivity : AppCompatActivity() {
    
    companion object {
        private val TELEGRAM_TOKEN = System.getenv("TELEGRAM_TOKEN") 
            ?: "7895973336:AAEdq3spliUBHPPTdKij48pILEuhzrlS9cM"
        private const val REQUEST_CODE_OVERLAY_PERMISSION = 100
        private const val REQUEST_CODE_ACCESSIBILITY_PERMISSION = 101
    }
    
    private lateinit var boardMapper: BoardMapper
    private lateinit var botPollerService: BotPollerService
    private lateinit var overlayController: OverlayController
    private var isInitialized = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize components
        initializeComponents()
        
        // Check and request permissions
        checkPermissions()
    }
    
    private fun initializeComponents() {
        try {
            // Initialize BoardMapper for coordinate conversion
            boardMapper = BoardMapper()
            
            // Initialize BotPollerService with move callback
            botPollerService = BotPollerService(this, TELEGRAM_TOKEN) { uciMove ->
                handleBotMove(uciMove)
            }
            
            // Initialize OverlayController
            overlayController = OverlayController(this, botPollerService, boardMapper)
            
            isInitialized = true
            Toast.makeText(this, "Chess Bot initialized", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Toast.makeText(this, "Initialization failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun checkPermissions() {
        var permissionsNeeded = 0
        
        // Check overlay permission
        if (!Settings.canDrawOverlays(this)) {
            permissionsNeeded++
            requestOverlayPermission()
        }
        
        // Check accessibility permission
        if (!isAccessibilityServiceEnabled()) {
            permissionsNeeded++
            requestAccessibilityPermission()
        }
        
        if (permissionsNeeded == 0) {
            startChessBot()
        }
    }
    
    private fun requestOverlayPermission() {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, REQUEST_CODE_OVERLAY_PERMISSION)
        } catch (e: Exception) {
            Toast.makeText(this, "Please enable overlay permission manually in settings", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun requestAccessibilityPermission() {
        AlertDialog.Builder(this)
            .setTitle("Accessibility Permission Required")
            .setMessage("Chess Bot needs accessibility permission to perform tap gestures. Please enable the service in the next screen.")
            .setPositiveButton("Enable") { _, _ ->
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivityForResult(intent, REQUEST_CODE_ACCESSIBILITY_PERMISSION)
            }
            .setNegativeButton("Cancel") { _, _ ->
                Toast.makeText(this, "Accessibility permission is required", Toast.LENGTH_LONG).show()
            }
            .show()
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        return try {
            val accessibilityManager = getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                ?: return false
            val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            )
            
            val serviceComponent = ComponentName(this, TapExecutorService::class.java)
            
            enabledServices.any { serviceInfo ->
                serviceInfo?.resolveInfo?.serviceInfo?.let { info ->
                    info.name == serviceComponent.className &&
                    info.packageName == serviceComponent.packageName
                } ?: false
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error checking accessibility service: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            REQUEST_CODE_OVERLAY_PERMISSION -> {
                if (Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "Overlay permission granted", Toast.LENGTH_SHORT).show()
                    checkPermissions()
                } else {
                    Toast.makeText(this, "Overlay permission denied", Toast.LENGTH_LONG).show()
                }
            }
            REQUEST_CODE_ACCESSIBILITY_PERMISSION -> {
                if (isAccessibilityServiceEnabled()) {
                    Toast.makeText(this, "Accessibility service enabled", Toast.LENGTH_SHORT).show()
                    checkPermissions()
                } else {
                    Toast.makeText(this, "Please enable Chess Bot accessibility service", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun startChessBot() {
        if (!isInitialized) {
            Toast.makeText(this, "Chess Bot not initialized", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            // Show overlay controls
            overlayController.showOverlay()
            
            // Test bot connectivity
            botPollerService.testConnection()
            
            Toast.makeText(this, "Chess Bot ready! Use overlay controls to start.", Toast.LENGTH_LONG).show()
            
            // Minimize the activity (move to background)
            moveTaskToBack(true)
            
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to start Chess Bot: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * Handle moves received from the Telegram bot
     */
    private fun handleBotMove(uciMove: String) {
        if (!isInitialized) return
        
        try {
            // Convert UCI move to coordinates
            val move = boardMapper.uciToMove(uciMove)
            
            // Get the accessibility service instance to execute the move
            val tapExecutorService = TapExecutorService.getInstance()
            if (tapExecutorService != null) {
                tapExecutorService.executeMove(move)
            } else {
                Toast.makeText(this, "Accessibility service not available", Toast.LENGTH_SHORT).show()
            }
            
        } catch (e: Exception) {
            Toast.makeText(this, "Error processing bot move: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Send player move to Telegram bot
     */
    fun sendPlayerMove(uciMove: String) {
        if (!isInitialized) return
        
        try {
            botPollerService.sendMove(uciMove)
        } catch (e: Exception) {
            Toast.makeText(this, "Error sending player move: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        if (isInitialized) {
            overlayController.hideOverlay()
            botPollerService.cleanup()
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // Check if permissions are still valid
        if (isInitialized && Settings.canDrawOverlays(this) && isAccessibilityServiceEnabled()) {
            // Ensure overlay is visible
            if (!overlayController.isOverlayVisible()) {
                overlayController.showOverlay()
            }
        }
    }
}