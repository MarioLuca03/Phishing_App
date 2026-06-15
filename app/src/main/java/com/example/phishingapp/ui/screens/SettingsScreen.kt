package com.example.phishingapp.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.phishingapp.util.AccessibilityHelper
import com.example.phishingapp.ui.NotificationHelper
import com.example.phishingapp.service.ClipboardForegroundService
import android.app.ActivityManager
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    clipboardMonitoringEnabled: Boolean,
    onClipboardMonitoringChanged: (Boolean) -> Unit,
    smsScanningEnabled: Boolean,
    onSmsScanningChanged: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A1929))
            .padding(16.dp)
    ) {
        // Header - buton simplu fără animații care pot cauza glitch-uri
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.shadow(4.dp, RoundedCornerShape(8.dp)),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color.White
                )
            ) {
                Text("← Înapoi", color = Color.White)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Setări",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.weight(1f))
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Settings cards cu animații și umbre
        val context = LocalContext.current
        var accessibilityEnabled by remember { mutableStateOf(AccessibilityHelper.isAccessibilityServiceEnabled(context)) }
        
        // Verifică periodic dacă serviciul este activat
        LaunchedEffect(Unit) {
            while (true) {
                delay(2000) // Verifică la fiecare 2 secunde
                val isEnabled = AccessibilityHelper.isAccessibilityServiceEnabled(context)
                if (isEnabled != accessibilityEnabled) {
                    accessibilityEnabled = isEnabled
                    android.util.Log.d("SettingsScreen", "Accessibility service status changed: $isEnabled")
                }
            }
        }
        
        // Re-verifică la fiecare recompunere
        LaunchedEffect(Unit) {
            accessibilityEnabled = AccessibilityHelper.isAccessibilityServiceEnabled(context)
        }
        
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(400, delayMillis = 100)) +
                    slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                    )
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(12.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E293B)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Monitorizare Clipboard",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (accessibilityEnabled) {
                                    "✅ Accessibility Service activ - funcționează în background"
                                } else if (clipboardMonitoringEnabled) {
                                    "⚠️ Activează Accessibility Service pentru funcționare completă"
                                } else {
                                    "❌ Monitorizarea este oprită"
                                },
                                fontSize = 13.sp,
                                color = when {
                                    accessibilityEnabled -> Color(0xFF00AA44)
                                    clipboardMonitoringEnabled -> Color(0xFFFFAA00)
                                    else -> Color.Gray
                                }
                            )
                        }
                        Switch(
                            checked = clipboardMonitoringEnabled,
                            onCheckedChange = onClipboardMonitoringChanged,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF0080FE),
                                checkedTrackColor = Color(0xFF0080FE).copy(alpha = 0.5f),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.Gray.copy(alpha = 0.3f)
                            )
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Butoane de test
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                // Test notificare
                                android.util.Log.d("SettingsScreen", "Test notification button clicked")
                                NotificationHelper.showClipboardNotification(
                                    context,
                                    "https://test-link.com",
                                    "SIGUR"
                                )
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0080FE)
                            )
                        ) {
                            Text("🔔 Test", color = Color.White, fontSize = 12.sp)
                        }
                        
                        Button(
                            onClick = {
                                // Test clipboard - copiază un URL de test
                                try {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("Test URL", "https://www.google.com/test-link")
                                    clipboard.setPrimaryClip(clip)
                                    android.util.Log.d("SettingsScreen", "Copied test URL to clipboard: https://www.google.com/test-link")
                                    
                                    // Trimite o notificare de confirmare
                                    NotificationHelper.showClipboardNotification(
                                        context,
                                        "https://www.google.com/test-link",
                                        "SIGUR"
                                    )
                                } catch (e: Exception) {
                                    android.util.Log.e("SettingsScreen", "Error copying to clipboard: ${e.message}", e)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00AA44)
                            )
                        ) {
                            Text("📋 Test Copy", color = Color.White, fontSize = 12.sp)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Info și buton pentru Accessibility Service
                    if (clipboardMonitoringEnabled) {
                        if (accessibilityEnabled) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF00AA44).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "✅ Accessibility Service activ!",
                                        fontSize = 12.sp,
                                        color = Color(0xFF00AA44),
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 18.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Monitorizarea funcționează complet în background. Vei primi notificări automat când copiezi linkuri, chiar și când aplicația este închisă sau telefonul este blocat.",
                                        fontSize = 11.sp,
                                        color = Color.Gray,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        } else {
                            // Buton pentru activare Accessibility Service
                            Button(
                                onClick = {
                                    AccessibilityHelper.openAccessibilitySettings(context)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF00AA44)
                                )
                            ) {
                                Text(
                                    "🔧 Activează Accessibility Service (Recomandat)",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFFFFAA00).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "ℹ️ Pentru funcționare completă în background:",
                                        fontSize = 12.sp,
                                        color = Color(0xFFFFAA00),
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 18.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "1. Apasă butonul de mai sus\n2. Găsește 'Phishing Detector' sau 'Phishing App'\n3. Activează toggle-ul pentru serviciul de accesibilitate\n4. Revino în aplicație - va funcționa automat!",
                                        fontSize = 11.sp,
                                        color = Color.Gray,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFFFFAA00).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "ℹ️ Activează monitorizarea:",
                                    fontSize = 12.sp,
                                    color = Color(0xFFFFAA00),
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 18.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Folosește toggle-ul de mai sus pentru a activa/dezactiva monitorizarea. Când este activ, vei primi notificări automat când copiezi linkuri.",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(400, delayMillis = 200)) +
                    slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                    )
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(12.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E293B)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Scanare SMS",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Detectează phishing în mesaje SMS",
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                        }
                        Switch(
                            checked = smsScanningEnabled,
                            onCheckedChange = onSmsScanningChanged,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF0080FE),
                                checkedTrackColor = Color(0xFF0080FE).copy(alpha = 0.5f),
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = Color.Gray.copy(alpha = 0.3f)
                            )
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Notă cu animație
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(400, delayMillis = 300))
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E293B),
                tonalElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ℹ️ Notă: Accessibility Service este necesar pentru funcționare completă în background pe Android 10+. Fără el, monitorizarea funcționează doar când aplicația este deschisă.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        lineHeight = 18.sp
                    )
                    if (!clipboardMonitoringEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "💡 Activează toggle-ul de mai sus pentru a începe monitorizarea clipboard-ului!",
                            fontSize = 12.sp,
                            color = Color(0xFF0080FE),
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}
