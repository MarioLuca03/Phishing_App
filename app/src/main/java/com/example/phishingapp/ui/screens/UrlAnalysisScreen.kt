package com.example.phishingapp.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.example.phishingapp.domain.UrlAnalysisResult
import com.example.phishingapp.domain.UrlAnalyzer
import com.example.phishingapp.ui.components.InfoRow
import com.example.phishingapp.ui.components.InfoRowWithExplanation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun UrlAnalysisScreen(
    initialUrl: String = "",
    onBack: () -> Unit
) {
    // Folosim remember cu key pentru a actualiza când se schimbă initialUrl
    var urlInput by remember(initialUrl) { 
        mutableStateOf(initialUrl) 
    }
    var analysisResult by remember { mutableStateOf<UrlAnalysisResult?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val urlAnalyzer = remember { UrlAnalyzer(context) }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    
    // Animație pentru rezultat
    val showResult = analysisResult != null
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A1929))
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header - buton simplu fără animații care pot cauza glitch-uri
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onBack,
                modifier = Modifier
                    .shadow(4.dp, RoundedCornerShape(8.dp)),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color.White
                )
            ) {
                Text("← Înapoi", color = Color.White)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Analiză URL",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.weight(1f))
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Input field cu umbră și animație
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
                    .shadow(8.dp, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("Introdu URL-ul de analizat", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF0080FE),
                        unfocusedBorderColor = Color.Gray
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Button cu umbră și animație
        val buttonScale by animateFloatAsState(
            targetValue = if (isLoading) 0.95f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "buttonScale"
        )
        
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(400, delayMillis = 200)) + 
                    scaleIn(initialScale = 0.9f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        ) {
            Button(
                onClick = {
                    if (urlInput.isNotBlank()) {
                        isLoading = true
                        analysisResult = null
                        
                        // Analizează URL-ul
                        coroutineScope.launch {
                            val result = withContext(Dispatchers.IO) {
                                urlAnalyzer.analyzeUrl(urlInput)
                            }
                            analysisResult = result
                            isLoading = false
                            // Scroll la rezultat după animație
                            kotlinx.coroutines.delay(500)
                            scrollState.animateScrollTo(scrollState.maxValue)
                        }
                    }
                },
                enabled = !isLoading && urlInput.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .graphicsLayer {
                        scaleX = buttonScale
                        scaleY = buttonScale
                    }
                    .shadow(12.dp, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0080FE)
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 6.dp,
                    pressedElevation = 2.dp
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White
                    )
                } else {
                    Text("Analizează URL", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Results cu animație de apariție și scroll
        AnimatedVisibility(
            visible = showResult,
            enter = fadeIn(animationSpec = tween(600)) + 
                    slideInVertically(
                        initialOffsetY = { it / 2 },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ) + scaleIn(
                        initialScale = 0.9f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                    ),
            exit = fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 0.9f)
        ) {
            analysisResult?.let { result ->
                AnalysisResultCard(result)
            }
        }
        
        // Spațiu extra pentru scroll
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun AnalysisResultCard(result: UrlAnalysisResult) {
    val riskColor = when {
        result.riskScore >= 71 -> Color(0xFFFF4444) // Roșu pentru 71-100
        result.riskScore >= 45 -> Color(0xFFFFAA00) // Galben pentru 45-70
        else -> Color(0xFF00AA44) // Verde pentru 0-44
    }
    
    val riskStatus = when {
        result.riskScore >= 71 -> "PERICULOS"
        result.riskScore >= 45 -> "SUSPECT"
        else -> "SIGUR"
    }
    
    // Animație pentru culoarea de risc
    val animatedColor by animateColorAsState(
        targetValue = riskColor,
        animationSpec = tween(800),
        label = "riskColor"
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E293B)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            // Scor final de risc cu animație
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Scor final",
                    fontSize = 18.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                
                // Număr animat
                val animatedScore by animateIntAsState(
                    targetValue = result.riskScore,
                    animationSpec = tween(1000, easing = FastOutSlowInEasing),
                    label = "riskScore"
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "$animatedScore/100",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = animatedColor
                    )
                }
            }
            
            // Text explicativ pentru scor
            Text(
                text = "Medie între scorul heuristice și scorul ML",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp)
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Status badge cu umbră
            Surface(
                color = animatedColor.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .shadow(8.dp, RoundedCornerShape(12.dp)),
                tonalElevation = 4.dp
            ) {
                Text(
                    text = riskStatus,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = animatedColor
                )
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Divider(
                color = Color.Gray.copy(alpha = 0.3f),
                thickness = 1.dp,
                modifier = Modifier.shadow(2.dp)
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Informații despre domeniu
            result.domain?.let { domain ->
                InfoRow("Domeniu:", domain)
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            InfoRow("HTTPS:", if (result.hasHttps) "✅ Da" else "❌ Nu")
            Spacer(modifier = Modifier.height(12.dp))
            
            result.sslValid?.let {
                InfoRowWithExplanation(
                    label = "SSL Valid:",
                    value = if (it) "✅ Da" else "❌ Nu",
                    explanation = if (it) {
                        "Certificate SSL valid - conexiunea este securizată și criptată"
                    } else {
                        "Certificate SSL invalid - risc de interceptare a datelor"
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            InfoRow("Redirecționări:", if (result.hasRedirects) "⚠️ Da" else "✅ Nu")
            Spacer(modifier = Modifier.height(12.dp))
            
            InfoRow("Parametri suspecti:", if (result.suspiciousParams) "⚠️ Da" else "✅ Nu")
            
            Spacer(modifier = Modifier.height(20.dp))
            Divider(
                color = Color.Gray.copy(alpha = 0.3f),
                thickness = 1.dp,
                modifier = Modifier.shadow(2.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            
            // Scoruri separate
            Text(
                text = "Detalii scoruri:",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            // Scor heuristice
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E293B),
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📋 Scor heuristice:",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${result.patternBasedScore}/100",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                result.patternBasedScore >= 71 -> Color(0xFFFF4444)
                                result.patternBasedScore >= 45 -> Color(0xFFFFAA00)
                                else -> Color(0xFF00AA44)
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Bazat pe ${result.findings.size} verificări pattern-based",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        lineHeight = 16.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Afișare scor ML
            result.mlScore?.let { mlScore ->
                Spacer(modifier = Modifier.height(12.dp))
                Divider(
                    color = Color.Gray.copy(alpha = 0.3f),
                    thickness = 1.dp,
                    modifier = Modifier.shadow(2.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    color = if (mlScore >= 71) {
                        Color(0xFFFF4444).copy(alpha = 0.2f)
                    } else if (mlScore >= 45) {
                        Color(0xFFFFAA00).copy(alpha = 0.2f)
                    } else {
                        Color(0xFF1E293B)
                    },
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🤖 Scor ML (Random Forest):",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "$mlScore/100",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    mlScore >= 71 -> Color(0xFFFF4444)
                                    mlScore >= 45 -> Color(0xFFFFAA00)
                                    else -> Color(0xFF00AA44)
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Bazat pe analiză automatizată cu model Random Forest (16 caracteristici URL)",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            lineHeight = 16.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            } ?: run {
                // Dacă ML nu e disponibil
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E293B),
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "🤖 Scor ML:",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Nu disponibil (modelul ML necesită Context)",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Divider(
                color = Color.Gray.copy(alpha = 0.3f),
                thickness = 1.dp,
                modifier = Modifier.shadow(2.dp)
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Findings cu animație staggered
            Text(
                text = "Detalii analiză:",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            if (result.findings.isEmpty()) {
                Text(
                    text = "✅ Nu s-au detectat probleme.",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )
            } else {
                result.findings.forEachIndexed { index, finding ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(animationSpec = tween(400, delayMillis = index * 100)) +
                                slideInHorizontally(
                                    initialOffsetX = { -it },
                                    animationSpec = tween(400, delayMillis = index * 100)
                                )
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .shadow(4.dp, RoundedCornerShape(8.dp)),
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF0F172A),
                            tonalElevation = 2.dp
                        ) {
                            Text(
                                text = "• $finding",
                                fontSize = 14.sp,
                                color = Color.White,
                                modifier = Modifier.padding(12.dp),
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
