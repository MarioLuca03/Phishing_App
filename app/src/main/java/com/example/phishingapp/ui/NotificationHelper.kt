package com.example.phishingapp.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.phishingapp.MainActivity

object NotificationHelper {
    private const val CHANNEL_ID = "phishing_alerts"
    private const val CHANNEL_NAME = "Alerta Phishing"
    
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance)
            channel.description = "Notificări pentru tentative de phishing detectate"
            
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    fun showClipboardNotification(context: Context, url: String, status: String) {
        android.util.Log.d("NotificationHelper", "=== showClipboardNotification called ===")
        android.util.Log.d("NotificationHelper", "URL: $url")
        android.util.Log.d("NotificationHelper", "Status: $status")
        
        try {
            
            // Intent pentru deschiderea aplicației (click pe notificare)
            val mainIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            
            val mainPendingIntent = PendingIntent.getActivity(
                context,
                0,
                mainIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            // Intent pentru butonul "Verifică link" - deschide direct ecranul de analiză URL
            val verifyIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("action", "verify_url")
                putExtra("url", url)
            }
            
            val verifyPendingIntent = PendingIntent.getActivity(
                context,
                System.currentTimeMillis().toInt(),
                verifyIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            val icon = when (status) {
                "PERICULOS" -> android.R.drawable.ic_dialog_alert
                "SUSPECT" -> android.R.drawable.ic_dialog_info
                else -> android.R.drawable.ic_menu_info_details
            }
            
            val color = when (status) {
                "PERICULOS" -> 0xFFFF4444.toInt()
                "SUSPECT" -> 0xFFFFAA00.toInt()
                else -> 0xFF00AA44.toInt()
            }
            
            // Truncate URL dacă e prea lung pentru notificare
            val displayUrl = if (url.length > 50) {
                url.take(47) + "..."
            } else {
                url
            }
            
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(icon)
                .setContentTitle("🔗 Link detectat în clipboard")
                .setContentText("Vrei să verifici acest link?")
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText("Link detectat: $displayUrl\nStatus: $status\n\nApasă 'Verifică link' pentru analiză detaliată"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setColor(color)
                .setContentIntent(mainPendingIntent)
                .addAction(
                    android.R.drawable.ic_menu_info_details,
                    "Verifică link",
                    verifyPendingIntent
                )
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setDefaults(NotificationCompat.DEFAULT_ALL) // Sunet și vibrație
                .setVibrate(longArrayOf(0, 250, 250, 250)) // Vibrație
                .setShowWhen(true) // Afișează timpul
                .build()
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Verifică dacă notificările sunt permise
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (!notificationManager.areNotificationsEnabled()) {
                    android.util.Log.e("NotificationHelper", "Notifications are disabled for this app")
                }
            }
            
            val notificationId = System.currentTimeMillis().toInt()
            try {
                notificationManager.notify(notificationId, notification)
                android.util.Log.d("NotificationHelper", "Notification sent successfully with ID: $notificationId")
            } catch (e: Exception) {
                android.util.Log.e("NotificationHelper", "Failed to show notification: ${e.message}", e)
                throw e
            }
        } catch (e: Exception) {
            android.util.Log.e("NotificationHelper", "Error showing notification: ${e.message}", e)
        }
    }
    
    fun showSmsNotification(context: Context, sender: String, message: String, urls: List<String>) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val urlText = if (urls.isNotEmpty()) {
            "\nLink-uri suspecte: ${urls.joinToString(", ")}"
        } else {
            "\nMesajul conține patternuri de phishing"
        }
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ Posibil phishing detectat în SMS")
            .setContentText("De la: $sender")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("De la: $sender\n\n$message$urlText"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(0xFFFF4444.toInt())
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}

