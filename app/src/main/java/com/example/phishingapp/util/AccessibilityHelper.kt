package com.example.phishingapp.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.example.phishingapp.service.ClipboardAccessibilityService

object AccessibilityHelper {
    
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        try {
            val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            
            // Verifică dacă serviciul nostru este activat
            val serviceClassName = ClipboardAccessibilityService::class.java.name
            val servicePackageName = context.packageName
            
            val isEnabled = enabledServices.any { serviceInfo ->
                val enabledServiceName = serviceInfo.resolveInfo.serviceInfo.name
                val enabledPackageName = serviceInfo.resolveInfo.serviceInfo.packageName
                
                android.util.Log.d("AccessibilityHelper", "Checking service: $enabledServiceName in package $enabledPackageName")
                android.util.Log.d("AccessibilityHelper", "Looking for: $serviceClassName in package $servicePackageName")
                
                enabledServiceName == serviceClassName && enabledPackageName == servicePackageName
            }
            
            android.util.Log.d("AccessibilityHelper", "Accessibility service enabled: $isEnabled")
            return isEnabled
        } catch (e: Exception) {
            android.util.Log.e("AccessibilityHelper", "Error checking accessibility service: ${e.message}", e)
            return false
        }
    }
    
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
        android.util.Log.d("AccessibilityHelper", "Opened accessibility settings")
    }
}

