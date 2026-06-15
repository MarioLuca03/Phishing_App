package com.example.phishingapp.service

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import com.example.phishingapp.domain.UrlAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class ClipboardMonitor(
    private val context: Context,
    private val urlAnalyzer: UrlAnalyzer,
    private val onUrlDetected: (String, String) -> Unit // (url, status)
) {
    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    // Pattern îmbunătățit pentru detectarea URL-urilor
    private val urlPattern = Pattern.compile(
        "(https?://[^\\s<>\"{}|\\\\^`\\[\\]]+)|" +  // URL-uri cu http/https
        "(www\\.[^\\s<>\"{}|\\\\^`\\[\\]]+\\.[a-zA-Z]{2,})|" +  // www.URL-uri
        "([a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9]*\\.[a-zA-Z]{2,}[^\\s<>\"{}|\\\\^`\\[\\]]*)"  // Domenii simple
    )
    
    private var lastClipboardText: String? = null
    private var isMonitoring = false
    
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        android.util.Log.d("ClipboardMonitor", "Clipboard changed! Listener triggered.")
        checkClipboard()
    }
    
    fun startMonitoring() {
        if (isMonitoring) {
            android.util.Log.d("ClipboardMonitor", "Already monitoring, skipping")
            return
        }
        
        try {
            isMonitoring = true
            clipboardManager.addPrimaryClipChangedListener(clipboardListener)
            android.util.Log.d("ClipboardMonitor", "Clipboard monitoring started")
            // Verifică imediat la pornire
            checkClipboard()
        } catch (e: Exception) {
            android.util.Log.e("ClipboardMonitor", "Error starting monitoring: ${e.message}", e)
            isMonitoring = false
        }
    }
    
    fun stopMonitoring() {
        if (!isMonitoring) return
        
        isMonitoring = false
        try {
            clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        } catch (e: Exception) {
            // Ignoră erorile la eliminarea listener-ului
        }
    }
    
    private fun checkClipboard() {
        android.util.Log.d("ClipboardMonitor", "checkClipboard called, isMonitoring=$isMonitoring")
        
        if (!isMonitoring) {
            android.util.Log.w("ClipboardMonitor", "Not monitoring, skipping check")
            return
        }
        
        try {
            val clip = clipboardManager.primaryClip
            if (clip == null) {
                android.util.Log.d("ClipboardMonitor", "No clipboard data")
                return
            }
            if (clip.itemCount == 0) {
                android.util.Log.d("ClipboardMonitor", "Clipboard is empty")
                return
            }
            
            val text = clip.getItemAt(0)?.text?.toString()
            if (text == null || text.isBlank()) {
                android.util.Log.d("ClipboardMonitor", "Clipboard text is null or empty")
                return
            }
            
            android.util.Log.d("ClipboardMonitor", "Clipboard text found: ${text.take(100)}")
            
            // Evită verificări duplicate - dar doar pentru texte identice
            if (text.trim() == lastClipboardText?.trim()) {
                android.util.Log.d("ClipboardMonitor", "Same text as last time, skipping")
                return
            }
            
            // Reset lastClipboardText doar dacă am găsit ceva nou
            lastClipboardText = text.trim()
            
            // Extrage URL-uri din text - simplificat și mai agresiv
            var foundUrls = mutableListOf<String>()
            
            // Caută URL-uri cu http/https
            val httpPattern = Regex("""https?://[^\s<>"{}|\\^`\[\]]+""")
            foundUrls.addAll(httpPattern.findAll(text).map { it.value })
            
            // Caută www.URL-uri
            val wwwPattern = Regex("""www\.[^\s<>"{}|\\^`\[\]]+\.\w{2,}""")
            foundUrls.addAll(wwwPattern.findAll(text).map { "https://${it.value}" })
            
            // Caută domenii simple (ex: google.com)
            val domainPattern = Regex("""[a-zA-Z0-9][a-zA-Z0-9.-]*\.[a-zA-Z]{2,}[^\s<>"{}|\\^`\[\]]*""")
            foundUrls.addAll(domainPattern.findAll(text).map { 
                val domain = it.value
                if (!domain.startsWith("http")) "https://$domain" else domain
            })
            
            // Elimină duplicate-uri
            foundUrls = foundUrls.distinct().toMutableList()
            
            android.util.Log.d("ClipboardMonitor", "Found ${foundUrls.size} URL(s) in clipboard: $foundUrls")
            
            // Analizează primul URL găsit
            if (foundUrls.isNotEmpty()) {
                val urlToAnalyze = foundUrls[0]
                android.util.Log.d("ClipboardMonitor", "Processing URL: $urlToAnalyze")
                analyzeUrlAsync(urlToAnalyze)
            } else {
                android.util.Log.d("ClipboardMonitor", "No URLs found in clipboard text. Full text was: ${text.take(200)}")
            }
        } catch (e: Exception) {
            android.util.Log.e("ClipboardMonitor", "Error checking clipboard: ${e.message}", e)
            e.printStackTrace()
        }
    }
    
    private fun analyzeUrlAsync(url: String) {
        android.util.Log.d("ClipboardMonitor", "Starting URL analysis for: $url")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = urlAnalyzer.analyzeUrl(url)
                val status = when {
                    result.isDangerous -> "PERICULOS"
                    result.isSuspicious -> "SUSPECT"
                    else -> "SIGUR"
                }
                
                android.util.Log.d("ClipboardMonitor", "URL analysis complete: status=$status, riskScore=${result.riskScore}")
                
                // Trimite notificarea pe thread-ul principal
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        android.util.Log.d("ClipboardMonitor", "Calling onUrlDetected callback with url=$url, status=$status")
                        onUrlDetected(url, status)
                        android.util.Log.d("ClipboardMonitor", "Notification callback completed")
                    } catch (e: Exception) {
                        android.util.Log.e("ClipboardMonitor", "Error showing notification: ${e.message}", e)
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ClipboardMonitor", "Error analyzing URL: ${e.message}", e)
                e.printStackTrace()
            }
        }
    }
    
    fun extractUrlsFromText(text: String): List<String> {
        val urls = mutableListOf<String>()
        val matcher = urlPattern.matcher(text)
        while (matcher.find()) {
            matcher.group()?.let { urls.add(it) }
        }
        return urls
    }
}

