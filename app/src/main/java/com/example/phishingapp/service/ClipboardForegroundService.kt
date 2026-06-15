package com.example.phishingapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.phishingapp.MainActivity
import com.example.phishingapp.R
import com.example.phishingapp.domain.UrlAnalyzer
import com.example.phishingapp.ui.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ClipboardForegroundService : Service() {
    
    private var serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var urlAnalyzer: UrlAnalyzer
    private var lastClipboardText: String? = null
    private var isMonitoring = false
    
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        Log.d(TAG, "=== CLIPBOARD CHANGED DETECTED BY FOREGROUND SERVICE ===")
        Log.d(TAG, "Listener triggered! Service is running: ${this@ClipboardForegroundService}")
        checkClipboard()
    }
    
    companion object {
        private const val TAG = "ClipboardForeground"
        private const val CHANNEL_ID = "clipboard_foreground_channel"
        private const val NOTIFICATION_ID = 1001
        
        const val ACTION_START = "com.example.phishingapp.START_FOREGROUND"
        const val ACTION_STOP = "com.example.phishingapp.STOP_FOREGROUND"
        
        fun startService(context: Context) {
            val intent = Intent(context, ClipboardForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, ClipboardForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Foreground service onCreate")
        
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        urlAnalyzer = UrlAnalyzer(this)
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Monitorizare activă"))
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand called with action: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START -> {
                Log.d(TAG, "ACTION_START received, starting monitoring")
                startMonitoring()
            }
            ACTION_STOP -> {
                Log.d(TAG, "ACTION_STOP received, stopping service")
                stopMonitoring()
                stopSelf()
            }
            else -> {
                // Dacă serviciul este repornit automat de sistem, reîncepe monitorizarea
                Log.d(TAG, "No action or unknown action, starting monitoring anyway")
                startMonitoring()
            }
        }
        
        return START_STICKY // Serviciul se repornește automat dacă este oprit de sistem
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null // Serviciu nelegat
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Foreground service onDestroy")
        stopMonitoring()
        serviceScope.cancel()
    }
    
    private fun startMonitoring() {
        if (isMonitoring) {
            Log.d(TAG, "Already monitoring, skipping")
            return
        }
        
        try {
            isMonitoring = true
            clipboardManager.addPrimaryClipChangedListener(clipboardListener)
            Log.d(TAG, "Foreground service monitoring started")
            
            // Actualizează notificarea
            updateNotification("Monitorizare activă - Ascultă clipboard-ul")
            
            // Verifică imediat clipboard-ul curent
            checkClipboard()
            
            // Verificare periodică a clipboard-ului (backup pentru Android 10+)
            serviceScope.launch {
                while (isMonitoring) {
                    delay(2000) // Verifică la fiecare 2 secunde
                    if (isMonitoring) {
                        Log.d(TAG, "Periodic clipboard check")
                        checkClipboard()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting monitoring: ${e.message}", e)
            isMonitoring = false
        }
    }
    
    private fun stopMonitoring() {
        if (!isMonitoring) return
        
        isMonitoring = false
        try {
            clipboardManager.removePrimaryClipChangedListener(clipboardListener)
            Log.d(TAG, "Foreground service monitoring stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping monitoring: ${e.message}", e)
        }
    }
    
    private fun checkClipboard() {
        Log.d(TAG, "checkClipboard called, isMonitoring=$isMonitoring")
        
        if (!isMonitoring) {
            Log.w(TAG, "Not monitoring, skipping check")
            return
        }
        
        try {
            val clip = clipboardManager.primaryClip
            if (clip == null) {
                Log.d(TAG, "Clipboard is null")
                return
            }
            
            if (clip.itemCount == 0) {
                Log.d(TAG, "Clipboard is empty")
                return
            }
            
            val text = clip.getItemAt(0)?.text?.toString()
            if (text == null || text.isBlank()) {
                Log.d(TAG, "Clipboard text is null or blank")
                return
            }
            
            Log.d(TAG, "Clipboard text found: ${text.take(100)}")
            
            // Evită verificări duplicate
            if (text.trim() == lastClipboardText?.trim()) {
                Log.d(TAG, "Same clipboard text, skipping: ${text.take(50)}")
                return
            }
            
            lastClipboardText = text.trim()
            Log.d(TAG, "Checking clipboard text: ${text.take(100)}")
            
            // Extrage URL-uri
            var foundUrls = mutableListOf<String>()
            
            // Caută URL-uri cu http/https
            val httpPattern = Regex("""https?://[^\s<>"{}|\\^`\[\]]+""")
            foundUrls.addAll(httpPattern.findAll(text).map { it.value })
            
            // Caută www.URL-uri
            val wwwPattern = Regex("""www\.[^\s<>"{}|\\^`\[\]]+\.\w{2,}""")
            foundUrls.addAll(wwwPattern.findAll(text).map { "https://${it.value}" })
            
            // Caută domenii simple
            val domainPattern = Regex("""[a-zA-Z0-9][a-zA-Z0-9.-]*\.[a-zA-Z]{2,}[^\s<>"{}|\\^`\[\]]*""")
            foundUrls.addAll(domainPattern.findAll(text).map { 
                val domain = it.value
                if (!domain.startsWith("http")) "https://$domain" else domain
            })
            
            // Elimină duplicate-uri
            foundUrls = foundUrls.distinct().toMutableList()
            
            Log.d(TAG, "Found ${foundUrls.size} URL(s) in clipboard: $foundUrls")
            
            if (foundUrls.isNotEmpty()) {
                val urlToAnalyze = foundUrls[0]
                Log.d(TAG, "Processing URL: $urlToAnalyze")
                analyzeUrlAsync(urlToAnalyze)
            } else {
                Log.d(TAG, "No URLs found in clipboard text. Text was: ${text.take(200)}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking clipboard: ${e.message}", e)
        }
    }
    
    private fun analyzeUrlAsync(url: String) {
        Log.d(TAG, "Starting URL analysis for: $url")
        serviceScope.launch(Dispatchers.IO) {
            try {
                val result = urlAnalyzer.analyzeUrl(url)
                val status = when {
                    result.isDangerous -> "PERICULOS"
                    result.isSuspicious -> "SUSPECT"
                    else -> "SIGUR"
                }
                
                Log.d(TAG, "URL analysis complete: status=$status, riskScore=${result.riskScore}")
                
                // Trimite notificarea
                launch(Dispatchers.Main) {
                    try {
                        Log.d(TAG, "Showing notification for URL: $url with status: $status")
                        NotificationHelper.showClipboardNotification(applicationContext, url, status)
                        
                        // Actualizează notificarea foreground service
                        updateNotification("Link detectat și analizat: $status")
                        
                        // După 5 secunde, revine la mesajul normal
                        launch(Dispatchers.Main) {
                            delay(5000)
                            updateNotification("Monitorizare activă - Ascultă clipboard-ul")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error showing notification: ${e.message}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error analyzing URL: ${e.message}", e)
            }
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Monitorizare Clipboard",
                NotificationManager.IMPORTANCE_LOW // IMPORTANCE_LOW pentru a nu fi deranjant
            ).apply {
                description = "Serviciu pentru monitorizarea continuă a clipboard-ului"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val stopIntent = Intent(this, ClipboardForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔗 Monitorizare Clipboard")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Oprește",
                stopPendingIntent
            )
            .setOngoing(true) // Notificare persistentă
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}

