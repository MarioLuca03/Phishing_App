package com.example.phishingapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import com.example.phishingapp.R
import com.example.phishingapp.domain.UrlAnalyzer
import com.example.phishingapp.service.ClipboardMonitor
import com.example.phishingapp.service.ClipboardForegroundService
import com.example.phishingapp.service.SmsScanner
import com.example.phishingapp.ui.NotificationHelper
import com.example.phishingapp.ui.screens.EmailAnalysisScreen
import com.example.phishingapp.ui.screens.SettingsScreen
import com.example.phishingapp.ui.screens.UrlAnalysisScreen
import com.example.phishingapp.ui.theme.PhishingAppTheme

enum class Screen {
    MAIN, URL_ANALYSIS, EMAIL_ANALYSIS, SETTINGS
}

class MainActivity : ComponentActivity() {
    
    private lateinit var clipboardMonitor: ClipboardMonitor
    private lateinit var smsScanner: SmsScanner
    private var clipboardMonitoringEnabled = true // Activ by default - notificările vor apărea automat
    private var smsScanningEnabled = false
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            if (clipboardMonitoringEnabled) {
                startForegroundService()
                android.util.Log.d("MainActivity", "Foreground service started after permissions granted")
            }
            // SMS scanning funcționează automat prin BroadcastReceiver (deja înregistrat în onCreate)
            if (smsScanningEnabled) {
                android.util.Log.d("MainActivity", "SMS scanning is enabled (receiver already registered)")
            }
        } else {
            android.util.Log.w("MainActivity", "Some permissions were denied")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Creează canalul de notificări
        NotificationHelper.createNotificationChannel(this)
        
        // Verifică dacă aplicația a fost deschisă dintr-o notificare cu acțiune
        val action = intent.getStringExtra("action")
        val urlFromNotification = intent.getStringExtra("url")
        
        // Inițializează serviciile
        val urlAnalyzer = UrlAnalyzer(this)
        
        clipboardMonitor = ClipboardMonitor(this, urlAnalyzer) { url, status ->
            NotificationHelper.showClipboardNotification(this, url, status)
        }
        
        smsScanner = SmsScanner(this, urlAnalyzer) { sender, message, urls ->
            NotificationHelper.showSmsNotification(this, sender, message, urls)
        }
        
        // Pornește automat monitorizarea clipboard-ului prin Foreground Service
        // Verifică permisiunile și pornește serviciul
        requestPermissionsForClipboard()
        
        // Pornește Foreground Service pentru monitorizare continuă
        if (clipboardMonitoringEnabled) {
            startForegroundService()
        }
        
        // Înregistrează receiver pentru SMS (pentru Android 4.4+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                val receiver = SmsScanner.SmsReceiver(smsScanner)
                val filter = android.content.IntentFilter(android.provider.Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Pentru API 33+ folosim flag-ul pentru export
                    registerReceiver(receiver, filter, android.content.Context.RECEIVER_EXPORTED)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Pentru API 26-32 folosim flag-ul NOT_EXPORTED
                    registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
                } else {
                    // Pentru API < 26 folosim metoda fără flags
                    registerReceiver(receiver, filter)
                }
            } catch (e: Exception) {
                // Dacă nu avem permisiuni, ignorăm eroarea
            }
        }
        
        setContent {
            PhishingAppTheme {
                MainScreen(
                    initialScreen = if (action == "verify_url" && urlFromNotification != null) {
                        Screen.URL_ANALYSIS to urlFromNotification
                    } else {
                        null
                    },
                    onClipboardMonitoringChanged = { enabled ->
                        clipboardMonitoringEnabled = enabled
                        if (enabled) {
                            // Verifică permisiunile și pornește serviciul
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                                    != PackageManager.PERMISSION_GRANTED) {
                                    requestPermissionsForClipboard()
                                } else {
                                    // Pornește atât Foreground Service cât și verifică Accessibility Service
                                    startForegroundService()
                                    // Accessibility Service trebuie activat manual de utilizator
                                    android.util.Log.d("MainActivity", "Clipboard monitoring enabled. Please activate Accessibility Service for full background support.")
                                }
                            } else {
                                startForegroundService()
                            }
                        } else {
                            stopForegroundService()
                            clipboardMonitor.stopMonitoring()
                            android.util.Log.d("MainActivity", "Clipboard monitoring stopped by user")
                        }
                    },
                    onSmsScanningChanged = { enabled ->
                        smsScanningEnabled = enabled
                        if (enabled) {
                            requestSmsPermissions()
                        }
                    }
                )
            }
        }
    }
    
    private fun requestPermissionsForClipboard() {
        val permissionsToRequest = mutableListOf<String>()
        
        // Pentru Android 13+ (API 33+), avem nevoie de permisiunea pentru notificări
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            // Dacă nu avem permisiuni, le cerem
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            // Dacă permisiunile sunt deja acordate, pornește serviciul direct
            android.util.Log.d("MainActivity", "Permissions already granted, starting foreground service")
            startForegroundService()
            clipboardMonitoringEnabled = true
        }
    }
    
    private fun startForegroundService() {
        try {
            ClipboardForegroundService.startService(this)
            android.util.Log.d("MainActivity", "Foreground service started")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error starting foreground service: ${e.message}", e)
        }
    }
    
    private fun stopForegroundService() {
        try {
            ClipboardForegroundService.stopService(this)
            android.util.Log.d("MainActivity", "Foreground service stopped")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error stopping foreground service: ${e.message}", e)
        }
    }
    
    private fun requestPermissions() {
        // Această metodă este apelată din UI când utilizatorul activează switch-ul manual
        requestPermissionsForClipboard()
    }
    
    private fun requestSmsPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECEIVE_SMS)
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}

@Composable
fun StyledButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(Color(0xFF0080FE)),
        modifier = modifier
            .width(220.dp)
            .height(56.dp)
            .shadow(12.dp, RoundedCornerShape(20.dp)),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 8.dp,
            pressedElevation = 4.dp,
            hoveredElevation = 10.dp
        )
    ) {
        Text(
            text = text, 
            fontSize = 18.sp, 
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun MainScreen(
    initialScreen: Pair<Screen, String>? = null, // (screen, url)
    onClipboardMonitoringChanged: (Boolean) -> Unit = {},
    onSmsScanningChanged: (Boolean) -> Unit = {}
) {
    // Inițializează ecranul și URL-ul dacă vine din notificare
    var currentScreen by remember { 
        mutableStateOf(initialScreen?.first ?: Screen.MAIN) 
    }
    var initialUrl by remember { 
        mutableStateOf(initialScreen?.second ?: "") 
    }
    
    var clipboardMonitoringEnabled by remember { mutableStateOf(true) } // Activ by default
    var smsScanningEnabled by remember { mutableStateOf(false) }
    var helpVisible by remember { mutableStateOf(false) }
    
    // Reset initialUrl după ce a fost folosit
    LaunchedEffect(initialUrl) {
        if (initialUrl.isNotEmpty() && currentScreen == Screen.URL_ANALYSIS) {
            // URL-ul va fi folosit de UrlAnalysisScreen
            // Resetăm după un scurt delay pentru a permite copierea valorii
            kotlinx.coroutines.delay(100)
        }
    }
    
    // Animație pentru tranziții între ecrane
    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            if (targetState == Screen.MAIN) {
                // Revenire la ecranul principal - slide din stânga
                (slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(400)
                ) + fadeIn(animationSpec = tween(400)) togetherWith
                        slideOutHorizontally(
                            targetOffsetX = { it },
                            animationSpec = tween(400)
                        ) + fadeOut(animationSpec = tween(400)))
            } else {
                // Navigare către alt ecran - slide din dreapta
                (slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(400)
                ) + fadeIn(animationSpec = tween(400)) togetherWith
                        slideOutHorizontally(
                            targetOffsetX = { -it },
                            animationSpec = tween(400)
                        ) + fadeOut(animationSpec = tween(400)))
            }.using(
                SizeTransform(clip = false)
            )
        },
        label = "screenTransition"
    ) { screen ->
        when (screen) {
            Screen.MAIN -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0A1929))
                ) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Logo cu animație
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(animationSpec = tween(600)) + 
                                    scaleIn(initialScale = 0.5f, animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy
                                    ))
                        ) {
                            Surface(
                                modifier = Modifier
                                    .size(150.dp)
                                    .padding(bottom = 32.dp)
                                    .shadow(16.dp, RoundedCornerShape(75.dp)),
                                shape = RoundedCornerShape(75.dp),
                                color = Color(0xFF1E293B),
                                tonalElevation = 8.dp
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.logo),
                                    contentDescription = "app logo",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        
                        // Titlu cu animație
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(animationSpec = tween(600, delayMillis = 200)) +
                                    slideInVertically(
                                        initialOffsetY = { it / 2 },
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                                    )
                        ) {
                            Text(
                                text = "Phishing App",
                                fontSize = 32.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(animationSpec = tween(600, delayMillis = 300))
                        ) {
                            Text(
                                text = "Protecție împotriva phishing-ului",
                                fontSize = 14.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(bottom = 40.dp)
                            )
                        }
                        
                        // Butoane cu animații staggered
                        StyledButton(
                            text = "Analizează URL",
                            onClick = { currentScreen = Screen.URL_ANALYSIS },
                            modifier = Modifier.animateEnterExit(
                                enter = fadeIn(animationSpec = tween(400, delayMillis = 400)) +
                                        slideInVertically(
                                            initialOffsetY = { it },
                                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                                        )
                            )
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        StyledButton(
                            text = "Analizează Email",
                            onClick = { currentScreen = Screen.EMAIL_ANALYSIS },
                            modifier = Modifier.animateEnterExit(
                                enter = fadeIn(animationSpec = tween(400, delayMillis = 500)) +
                                        slideInVertically(
                                            initialOffsetY = { it },
                                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                                        )
                            )
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        StyledButton(
                            text = "Setări",
                            onClick = { currentScreen = Screen.SETTINGS },
                            modifier = Modifier.animateEnterExit(
                                enter = fadeIn(animationSpec = tween(400, delayMillis = 500)) +
                                        slideInVertically(
                                            initialOffsetY = { it },
                                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                                        )
                            )
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        StyledButton(
                            text = "Help",
                            onClick = { helpVisible = !helpVisible },
                            modifier = Modifier.animateEnterExit(
                                enter = fadeIn(animationSpec = tween(100, delayMillis = 700)) +
                                        slideInVertically(
                                            initialOffsetY = { it },
                                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                                        )
                            )
                        )
                    }
                    
                    // Help box cu animație și auto-dismiss după 5 secunde
                    LaunchedEffect(helpVisible) {
                        if (helpVisible) {
                            delay(5000) // 5 secunde
                            helpVisible = false
                        }
                    }
                    
                    AnimatedVisibility(
                        visible = helpVisible,
                        enter = fadeIn(animationSpec = tween(300)) + 
                                scaleIn(initialScale = 0.8f, animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy
                                )),
                        exit = fadeOut(animationSpec = tween(300)) + 
                                scaleOut(targetScale = 0.8f),
                        modifier = Modifier.align(Alignment.Center)
                            .offset(y = 280.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .width(340.dp)
                                .shadow(16.dp, RoundedCornerShape(20.dp)),
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF1E293B),
                            tonalElevation = 8.dp
                        ) {
                            Column(modifier = Modifier.padding(24.dp)) {
                                Text(
                                    text = "Despre aplicație",
                                    fontSize = 20.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                                Text(
                                    text = "• Analizează URL-uri pentru detectare phishing\n" +
                                            "• Analizează email-uri pentru detectare phishing\n" +
                                            "• Monitorizează clipboard-ul pentru linkuri copiate\n" +
                                            "• Scanează SMS-uri pentru patternuri suspecte\n" +
                                            "• Verifică SSL/TLS pentru conexiuni securizate\n" +
                                            "• Detectă homoglyphs, TLD-uri suspecte, hosting gratuit\n" +
                                            "• Acuratețe aproximativ 90%\n\n" +
                                            "ℹ️ SSL/TLS: Protocol care criptează datele între tine și site. " +
                                            "Un certificate SSL valid asigură că comunicarea este sigură.",
                                    fontSize = 14.sp,
                                    color = Color.White,
                                    lineHeight = 22.sp
                                )
                            }
                        }
                    }
                }
            }
            Screen.URL_ANALYSIS -> {
                UrlAnalysisScreen(
                    initialUrl = initialUrl,
                    onBack = { 
                        currentScreen = Screen.MAIN
                        initialUrl = "" // Reset după ce se închide
                    }
                )
            }
            Screen.EMAIL_ANALYSIS -> {
                EmailAnalysisScreen(onBack = { currentScreen = Screen.MAIN })
            }
            Screen.SETTINGS -> {
                SettingsScreen(
                    onBack = { currentScreen = Screen.MAIN },
                    clipboardMonitoringEnabled = clipboardMonitoringEnabled,
                    onClipboardMonitoringChanged = { enabled ->
                        clipboardMonitoringEnabled = enabled
                        onClipboardMonitoringChanged(enabled)
                    },
                    smsScanningEnabled = smsScanningEnabled,
                    onSmsScanningChanged = { enabled ->
                        smsScanningEnabled = enabled
                        onSmsScanningChanged(enabled)
                    }
                )
            }
        }
    }
}
