package com.example.phishingapp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.phishingapp.domain.UrlAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class SmsScanner(
    private val context: Context,
    private val urlAnalyzer: UrlAnalyzer,
    private val onPhishingDetected: (String, String, List<String>) -> Unit // (sender, message, urls)
) {
    
    // Patternuri comune de phishing în SMS
    private val phishingPatterns = listOf(
        // Română
        "(?i)(?:cont.*blocat|cont.*suspendat|cont.*închis|cont.*expirat)",
        "(?i)(?:urgent|imediAT|acțiune.*necesară|acțiune.*imediată)",
        "(?i)(?:premiu|câștigat|ai.*câștigat|felicitări)",
        "(?i)(?:bancă|banc.*contactează|banc.*verifică)",
        "(?i)(?:actualizează.*date|verifică.*cont|confirmă.*identitate)",
        "(?i)(?:click.*aici|accesează.*link|urmărește.*link)",
        "(?i)(?:expiră.*în|expiră.*la|acțiune.*în.*24.*ore)",
        "(?i)(?:suspendat|blocat|deblocare|reactivare)",
        
        // Engleză
        "(?i)(?:account.*blocked|account.*suspended|account.*closed|account.*expired)",
        "(?i)(?:urgent|immediate.*action|action.*required)",
        "(?i)(?:prize|won|you.*won|congratulations)",
        "(?i)(?:bank|bank.*contact|bank.*verify)",
        "(?i)(?:update.*details|verify.*account|confirm.*identity)",
        "(?i)(?:click.*here|access.*link|follow.*link)",
        "(?i)(?:expires.*in|expires.*at|action.*in.*24.*hours)",
        "(?i)(?:suspended|blocked|unlock|reactivate)"
    )
    
    private val urlPattern = Pattern.compile(
        "(https?://)?(www\\.)?[-a-zA-Z0-9@:%._+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_+.~#?&/=]*)"
    )
    
    private val compiledPhishingPatterns = phishingPatterns.map { Pattern.compile(it) }
    
    class SmsReceiver(
        private val smsScanner: SmsScanner
    ) : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                for (message in messages) {
                    smsScanner.scanSms(message.displayOriginatingAddress ?: "", message.messageBody ?: "")
                }
            }
        }
    }
    
    fun scanSms(sender: String, messageBody: String) {
        // Extrage URL-uri din mesaj
        val urls = extractUrls(messageBody)
        
        // Verifică patternuri de phishing
        val hasPhishingPattern = checkPhishingPatterns(messageBody)
        
        if (hasPhishingPattern || urls.isNotEmpty()) {
            // Analizează URL-urile
            CoroutineScope(Dispatchers.IO).launch {
                val suspiciousUrls = mutableListOf<String>()
                
                for (url in urls) {
                    val result = urlAnalyzer.analyzeUrl(url)
                    if (result.isSuspicious || result.isDangerous) {
                        suspiciousUrls.add(url)
                    }
                }
                
                // Dacă detectăm phishing sau URL-uri suspecte, trimitem notificare
                if (hasPhishingPattern || suspiciousUrls.isNotEmpty()) {
                    CoroutineScope(Dispatchers.Main).launch {
                        onPhishingDetected(sender, messageBody, suspiciousUrls)
                    }
                }
            }
        }
    }
    
    private fun checkPhishingPatterns(message: String): Boolean {
        for (pattern in compiledPhishingPatterns) {
            if (pattern.matcher(message).find()) {
                return true
            }
        }
        return false
    }
    
    private fun extractUrls(text: String): List<String> {
        val urls = mutableListOf<String>()
        val matcher = urlPattern.matcher(text)
        while (matcher.find()) {
            matcher.group()?.let { urls.add(it) }
        }
        return urls
    }
}



