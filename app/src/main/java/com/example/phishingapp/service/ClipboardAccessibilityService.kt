package com.example.phishingapp.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.util.Log
import com.example.phishingapp.domain.UrlAnalyzer
import com.example.phishingapp.ui.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class ClipboardAccessibilityService : AccessibilityService() {
    
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var urlAnalyzer: UrlAnalyzer
    private var lastClipboardText: String? = null
    
    // Pattern pentru detectarea URL-urilor
    private val urlPattern = Pattern.compile(
        "(https?://[^\\s<>\"{}|\\\\^`\\[\\]]+)|" +  // URL-uri cu http/https
        "(www\\.[^\\s<>\"{}|\\\\^`\\[\\]]+\\.[a-zA-Z]{2,})|" +  // www.URL-uri
        "([a-zA-Z0-9][a-zA-Z0-9.-]*\\.[a-zA-Z]{2,}[^\\s<>\"{}|\\\\^`\\[\\]]*)"  // Domenii simple
    )
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "ClipboardAccessibilityService connected")
        
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        urlAnalyzer = UrlAnalyzer(this)
        
        // Monitorizează schimbările clipboard-ului
        clipboardManager.addPrimaryClipChangedListener(clipboardListener)
        
        // Verifică imediat clipboard-ul curent
        checkClipboard()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing listener", e)
        }
        Log.d(TAG, "ClipboardAccessibilityService destroyed")
    }
    
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        Log.d(TAG, "=== Clipboard changed detected by Accessibility Service ===")
        Log.d(TAG, "Service is running: ${this@ClipboardAccessibilityService}")
        checkClipboard()
    }
    
    private fun checkClipboard() {
        try {
            val clip = clipboardManager.primaryClip ?: return
            if (clip.itemCount == 0) {
                Log.d(TAG, "Clipboard is empty")
                return
            }
            
            val text = clip.getItemAt(0)?.text?.toString()
            if (text == null || text.isBlank()) {
                Log.d(TAG, "Clipboard text is null or empty")
                return
            }
            
            // Evită verificări duplicate - dar doar pentru texte identice
            if (text.trim() == lastClipboardText?.trim()) {
                Log.d(TAG, "Same clipboard text, skipping: ${text.take(50)}")
                return
            }
            
            // Reset lastClipboardText doar dacă am găsit ceva nou
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
                Log.d(TAG, "Processing first URL: ${foundUrls[0]}")
                analyzeUrl(foundUrls[0])
            } else {
                Log.d(TAG, "No URLs found in clipboard text. Text was: ${text.take(200)}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking clipboard", e)
        }
    }
    
    private fun analyzeUrl(url: String) {
        Log.d(TAG, "Analyzing URL: $url")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = urlAnalyzer.analyzeUrl(url)
                val status = when {
                    result.isDangerous -> "PERICULOS"
                    result.isSuspicious -> "SUSPECT"
                    else -> "SIGUR"
                }
                
                Log.d(TAG, "URL analysis complete: status=$status, riskScore=${result.riskScore}")
                
                // Trimite notificarea pe thread-ul principal
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        Log.d(TAG, "Attempting to show notification for URL: $url")
                        NotificationHelper.showClipboardNotification(applicationContext, url, status)
                        Log.d(TAG, "Notification shown successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to show notification: ${e.message}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error analyzing URL", e)
            }
        }
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Nu avem nevoie să procesăm evenimente de accesibilitate
        // Folosim doar pentru a accesa clipboard-ul în background
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }
    
    companion object {
        private const val TAG = "ClipboardAccessibility"
    }
}

