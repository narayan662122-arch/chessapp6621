package com.chessbot

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check for overlay permission (required for chess bot)
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivityForResult(intent, 1234)
        }
        
        // Start the chess bot service
        val serviceIntent = Intent(this, BotPollerService::class.java)
        startForegroundService(serviceIntent)
        
        finish() // Close main activity after starting service
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1234 && Settings.canDrawOverlays(this)) {
            // Permission granted, restart the service
            val serviceIntent = Intent(this, BotPollerService::class.java)
            startForegroundService(serviceIntent)
        }
    }
}