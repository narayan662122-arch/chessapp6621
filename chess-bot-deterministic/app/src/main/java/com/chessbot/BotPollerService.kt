package com.chessbot

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class BotPollerService : Service() {
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(1, notification)
        
        // TODO: Implement chess bot polling logic
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "CHESS_BOT_CHANNEL",
            "Chess Bot Service", 
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "CHESS_BOT_CHANNEL")
            .setContentTitle("Chess Bot Active")
            .setContentText("Chess bot is running for Redmi 9 Activ")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .build()
    }
}