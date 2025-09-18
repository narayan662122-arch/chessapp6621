package com.chessbot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * BotPollerService handles all communication with the Telegram Bot API as a background service.
 * 
 * Key responsibilities:
 * - Poll for new messages from the Telegram bot using getUpdates
 * - Send player moves to the bot using sendMessage
 * - Parse UCI moves from bot responses
 * - Handle HTTP communication with proper error handling
 * - Run as Android foreground service
 * 
 * This service maintains the connection with the Colab bot running 24/7
 * and ensures reliable message exchange for chess move coordination.
 */
class BotPollerService(
    private val context: Context,
    private val botToken: String,
    private val onMoveReceived: (String) -> Unit
) : Service() {
    
    companion object {
        private const val TAG = "BotPollerService"
        private const val TELEGRAM_BASE_URL = "https://api.telegram.org/bot"
        private const val POLL_TIMEOUT = 30 // seconds
        private const val POLL_LIMIT = 10 // max messages per poll
        private const val REQUEST_TIMEOUT = 45L // seconds
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "chess_bot_channel"
        
        // JSON media type for HTTP requests
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(REQUEST_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(REQUEST_TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(REQUEST_TIMEOUT, TimeUnit.SECONDS)
        .build()
    
    private var lastUpdateId = 0
    private var pollingJob: Job? = null
    private var isRunning = false
    private var chatId: Long? = null
    private val moveCallback: (String) -> Unit = onMoveReceived
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "BotPollerService created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        startPolling()
        return START_STICKY // Restart if killed by system
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        cleanup()
        Log.d(TAG, "BotPollerService destroyed")
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Chess Bot Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Chess Bot Telegram Integration Service"
                setShowBadge(false)
                setSound(null, null)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
    
    /**
     * Start polling for messages from the Telegram bot
     */
    fun startPolling() {
        if (isRunning) {
            Log.d(TAG, "Polling is already running")
            return
        }
        
        isRunning = true
        pollingJob = CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "Starting Telegram bot polling...")
            while (isRunning) {
                try {
                    pollForUpdates()
                    delay(1000) // Short delay between polls
                } catch (e: Exception) {
                    Log.e(TAG, "Error in polling loop", e)
                    delay(5000) // Wait longer on error
                }
            }
        }
    }
    
    /**
     * Stop polling for messages
     */
    fun stopPolling() {
        isRunning = false
        pollingJob?.cancel()
        Log.d(TAG, "Telegram bot polling stopped")
    }
    
    /**
     * Send a move to the Telegram bot
     */
    fun sendMove(move: String) {
        if (chatId == null) {
            Log.w(TAG, "No chat ID available. Cannot send move: $move")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                sendMessage(chatId!!, "Player move: $move")
                Log.d(TAG, "Sent move to bot: $move")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send move $move", e)
            }
        }
    }
    
    /**
     * Send a text message to the specified chat
     */
    private fun sendMessage(chatId: Long, text: String) {
        val url = "$TELEGRAM_BASE_URL$botToken/sendMessage"
        
        val jsonBody = JSONObject().apply {
            put("chat_id", chatId)
            put("text", text)
        }
        
        val requestBody = jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()
        
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to send message: ${response.code}")
            }
            
            val responseBody = response.body?.string() ?: ""
            val jsonResponse = JSONObject(responseBody)
            
            if (!jsonResponse.getBoolean("ok")) {
                throw IOException("Telegram API error: ${jsonResponse.optString("description")}")
            }
        }
    }
    
    /**
     * Poll for new updates from Telegram
     */
    private fun pollForUpdates() {
        val url = "$TELEGRAM_BASE_URL$botToken/getUpdates"
        
        val jsonBody = JSONObject().apply {
            put("offset", lastUpdateId + 1)
            put("limit", POLL_LIMIT)
            put("timeout", POLL_TIMEOUT)
        }
        
        val requestBody = jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()
        
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "Failed to poll updates: ${response.code}")
                return
            }
            
            val responseBody = response.body?.string() ?: ""
            val jsonResponse = JSONObject(responseBody)
            
            if (!jsonResponse.getBoolean("ok")) {
                Log.w(TAG, "Telegram API error: ${jsonResponse.optString("description")}")
                return
            }
            
            val updates = jsonResponse.getJSONArray("result")
            processUpdates(updates)
        }
    }
    
    /**
     * Process received updates from Telegram
     */
    private fun processUpdates(updates: JSONArray) {
        for (i in 0 until updates.length()) {
            val update = updates.getJSONObject(i)
            val updateId = update.getInt("update_id")
            
            if (updateId > lastUpdateId) {
                lastUpdateId = updateId
            }
            
            // Check if update contains a message
            if (update.has("message")) {
                val message = update.getJSONObject("message")
                processMessage(message)
            }
        }
    }
    
    /**
     * Process individual message from bot
     */
    private fun processMessage(message: JSONObject) {
        try {
            // Store chat ID for sending replies
            val chat = message.getJSONObject("chat")
            chatId = chat.getLong("id")
            
            // Check if message has text content
            if (!message.has("text")) {
                return
            }
            
            val messageText = message.getString("text")
            val messageId = message.getInt("message_id")
            
            Log.d(TAG, "Received message [$messageId]: $messageText")
            
            // Parse UCI moves from bot message
            val uciMove = extractUCIMove(messageText)
            if (uciMove != null) {
                Log.d(TAG, "Extracted UCI move: $uciMove")
                moveCallback(uciMove)
            } else {
                Log.d(TAG, "No valid UCI move found in message: $messageText")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing message", e)
        }
    }
    
    /**
     * Extract UCI move from bot message text
     * Looks for patterns like "e2e4", "Nf3", or "Bot move: e2e4"
     */
    private fun extractUCIMove(text: String): String? {
        // Common UCI move pattern: letter+number+letter+number (e.g., e2e4)
        val uciPattern = Regex("[a-h][1-8][a-h][1-8][qrnb]?", RegexOption.IGNORE_CASE)
        val matches = uciPattern.findAll(text)
        
        for (match in matches) {
            val move = match.value.lowercase()
            if (isValidUCIMove(move)) {
                return move
            }
        }
        
        return null
    }
    
    /**
     * Basic validation for UCI move format
     */
    private fun isValidUCIMove(move: String): Boolean {
        if (move.length < 4 || move.length > 5) return false
        
        val fromFile = move[0]
        val fromRank = move[1]
        val toFile = move[2]
        val toRank = move[3]
        
        return fromFile in 'a'..'h' && fromRank in '1'..'8' &&
               toFile in 'a'..'h' && toRank in '1'..'8'
    }
    
    /**
     * Send a test message to verify bot connectivity
     */
    fun testConnection() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "$TELEGRAM_BASE_URL$botToken/getMe"
                val request = Request.Builder().url(url).build()
                
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string() ?: ""
                        val jsonResponse = JSONObject(responseBody)
                        
                        if (jsonResponse.getBoolean("ok")) {
                            val botInfo = jsonResponse.getJSONObject("result")
                            val botName = botInfo.getString("username")
                            Log.d(TAG, "Successfully connected to bot: @$botName")
                        } else {
                            Log.e(TAG, "Bot connection failed: ${jsonResponse.optString("description")}")
                        }
                    } else {
                        Log.e(TAG, "HTTP error testing connection: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception testing bot connection", e)
            }
        }
    }
    
    /**
     * Get bot information and status
     */
    fun getBotInfo() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "$TELEGRAM_BASE_URL$botToken/getMe"
                val request = Request.Builder().url(url).build()
                
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string() ?: ""
                        Log.d(TAG, "Bot info response: $responseBody")
                    } else {
                        Log.e(TAG, "Failed to get bot info: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting bot info", e)
            }
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        stopPolling()
        httpClient.dispatcher.executorService.shutdown()
    }
}