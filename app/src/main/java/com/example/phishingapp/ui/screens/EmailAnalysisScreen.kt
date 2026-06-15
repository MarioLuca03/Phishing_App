package com.example.phishingapp.ui.screens

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.phishingapp.domain.EmailAnalysisResult
import com.example.phishingapp.domain.EmailAnalyzer
import com.example.phishingapp.domain.UrlAnalyzer
import com.example.phishingapp.service.ImageTextExtractor
import com.example.phishingapp.ui.components.InfoRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.regex.Pattern

@Composable
fun EmailAnalysisScreen(
    onBack: () -> Unit
) {
    var senderInput by remember { mutableStateOf("") }
    var subjectInput by remember { mutableStateOf("") }
    var contentInput by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var extractedText by remember { mutableStateOf("") }
    var analysisResult by remember { mutableStateOf<EmailAnalysisResult?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isExtractingText by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val context = LocalContext.current
    val urlAnalyzer = remember { UrlAnalyzer(context) }
    val emailAnalyzer = remember { EmailAnalyzer(urlAnalyzer, context) }
    val textExtractor = remember { ImageTextExtractor(context) }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    
    val showResult = analysisResult != null
    
    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            extractedText = ""
            errorMessage = null
            
            // Extrage textul automat din imagine
            coroutineScope.launch {
                isExtractingText = true
                try {
                    val text = withContext(Dispatchers.IO) {
                        textExtractor.extractTextFromUri(it)
                    }
                    
                    if (!text.isNullOrBlank()) {
                        extractedText = text
                        // Parsare automată îmbunătățită a email-ului din textul extras
                        parseEmailFromText(text).let { parsed ->
                            senderInput = parsed.first
                            subjectInput = parsed.second
                            contentInput = parsed.third
                            
                            // Dacă sender-ul e gol, încercăm să extragem din întregul text
                            if (senderInput.isBlank()) {
                                senderInput = text
                            }
                        }
                    } else {
                        errorMessage = "Nu s-a putut extrage text din imagine. Asigură-te că imaginea este clară și conține text."
                    }
                } catch (e: Exception) {
                    errorMessage = "Eroare la extragerea textului: ${e.message}"
                } finally {
                    isExtractingText = false
                }
            }
        }
    }
    
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            imagePickerLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A1929))
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
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
                text = "Analiză Email",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.weight(1f))
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Mod imagine (singura opțiune disponibilă)
        ImageInputMode(
            selectedImageUri = selectedImageUri,
            extractedText = extractedText,
            isExtractingText = isExtractingText,
            errorMessage = errorMessage,
            onSelectImage = {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) 
                        == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        imagePickerLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    } else {
                        permissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                    }
                } else {
                    imagePickerLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            },
            senderInput = senderInput,
            subjectInput = subjectInput,
            contentInput = contentInput,
            onSenderChange = { senderInput = it },
            onSubjectChange = { subjectInput = it },
            onContentChange = { contentInput = it }
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Analyze button
        Button(
            onClick = {
                if (senderInput.isNotBlank() && contentInput.isNotBlank()) {
                    isLoading = true
                    analysisResult = null
                    errorMessage = null
                    
                    coroutineScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            emailAnalyzer.analyzeEmail(
                                sender = senderInput,
                                subject = subjectInput,
                                content = contentInput
                            )
                        }
                        analysisResult = result
                        isLoading = false
                        delay(500)
                        scrollState.animateScrollTo(scrollState.maxValue)
                    }
                } else {
                    errorMessage = "Completează câmpurile De la și Conținut"
                }
            },
            enabled = !isLoading && !isExtractingText && senderInput.isNotBlank() && contentInput.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
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
                Text("Analizează Email", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        
        // Error message
        errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFFF4444).copy(alpha = 0.2f),
                tonalElevation = 2.dp
            ) {
                Text(
                    text = "⚠️ $error",
                    color = Color(0xFFFF4444),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Results
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
                EmailAnalysisResultCard(result)
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun ImageInputMode(
    selectedImageUri: Uri?,
    extractedText: String,
    isExtractingText: Boolean,
    errorMessage: String?,
    onSelectImage: () -> Unit,
    senderInput: String,
    subjectInput: String,
    contentInput: String,
    onSenderChange: (String) -> Unit,
    onSubjectChange: (String) -> Unit,
    onContentChange: (String) -> Unit
) {
    // Buton pentru selectare imagine
    Button(
        onClick = onSelectImage,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(12.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF00AA44)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 6.dp,
            pressedElevation = 2.dp
        )
    ) {
        Text("📷 Selectează imagine cu email", fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
    
    Spacer(modifier = Modifier.height(16.dp))
    
    // Afișează imaginea selectată
    selectedImageUri?.let { uri ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            AsyncImage(
                model = uri,
                contentDescription = "Email image",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentScale = ContentScale.Fit
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Loading pentru extragerea textului
        if (isExtractingText) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF1E293B),
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color(0xFF0080FE)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Extragere text din imagine...",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
        }
        
        // Afișează textul extras (pentru verificare)
        if (extractedText.isNotBlank() && !isExtractingText) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF1E293B),
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "✅ Text extras:",
                        color = Color(0xFF00AA44),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = extractedText.take(200) + if (extractedText.length > 200) "..." else "",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    
    // Câmpuri editabile (populate automat, dar pot fi modificate)
    // Sender input
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        OutlinedTextField(
            value = senderInput,
            onValueChange = onSenderChange,
            label = { Text("De la (From):", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF0080FE),
                unfocusedBorderColor = Color.Gray
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            placeholder = { Text("ex: noreply@example.com", color = Color.Gray.copy(alpha = 0.5f)) }
        )
    }
    
    Spacer(modifier = Modifier.height(16.dp))
    
    // Subject input
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        OutlinedTextField(
            value = subjectInput,
            onValueChange = onSubjectChange,
            label = { Text("Subiect (Subject):", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF0080FE),
                unfocusedBorderColor = Color.Gray
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            placeholder = { Text("Subiectul email-ului (opțional)", color = Color.Gray.copy(alpha = 0.5f)) }
        )
    }
    
    Spacer(modifier = Modifier.height(16.dp))
    
    // Content input
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        OutlinedTextField(
            value = contentInput,
            onValueChange = onContentChange,
            label = { Text("Conținut email:", color = Color.Gray) },
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF0080FE),
                unfocusedBorderColor = Color.Gray
            ),
            shape = RoundedCornerShape(12.dp),
            maxLines = 15,
            placeholder = { Text("Textul extras din imagine...", color = Color.Gray.copy(alpha = 0.5f)) }
        )
    }
}

// Funcție helper pentru parsarea email-ului din text extras
fun parseEmailFromText(text: String): Triple<String, String, String> {
    var sender = ""
    var subject = ""
    var content = text
    
    // Încearcă să găsească "From:" sau "De la:" (mai multe variante)
    val fromPatterns = listOf(
        Pattern.compile("""(?i)(?:from|de\s+la|expeditor|sender):\s*([^\n\r]+)""", Pattern.MULTILINE),
        Pattern.compile("""(?i)(?:from|de\s+la):\s*([^<\n\r]+)""", Pattern.MULTILINE),
        Pattern.compile("""(?i)<([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,})>""", Pattern.MULTILINE)
    )
    
    for (pattern in fromPatterns) {
        val matcher = pattern.matcher(text)
        if (matcher.find()) {
            sender = matcher.group(1)?.trim()?.replace("<", "")?.replace(">", "") ?: ""
            if (sender.isNotBlank()) break
        }
    }
    
    // Dacă nu s-a găsit sender prin pattern-uri, caută primul email din text
    if (sender.isBlank()) {
        val emailPattern = Pattern.compile("""([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,})""")
        val emailMatcher = emailPattern.matcher(text)
        if (emailMatcher.find()) {
            sender = emailMatcher.group(1) ?: ""
        }
    }
    
    // Încearcă să extragă subject-ul
    val subjectPatterns = listOf(
        Pattern.compile("""(?i)(?:subject|subiect):\s*([^\n\r]+)""", Pattern.MULTILINE),
        Pattern.compile("""(?i)subject:\s*(.+?)(?:\n|$)""", Pattern.MULTILINE)
    )
    
    for (pattern in subjectPatterns) {
        val matcher = pattern.matcher(text)
        if (matcher.find()) {
            subject = matcher.group(1)?.trim() ?: ""
            if (subject.isNotBlank()) break
        }
    }
    
    // Elimină header-urile comune din conținut pentru a păstra doar mesajul
    content = content
        .replace(Regex("""(?i)(?:from|de\s+la|expeditor|sender):[^\n\r]*"""), "")
        .replace(Regex("""(?i)(?:subject|subiect):[^\n\r]*"""), "")
        .replace(Regex("""(?i)(?:to|către|destinatar):[^\n\r]*"""), "")
        .replace(Regex("""(?i)(?:date|dată):[^\n\r]*"""), "")
        .replace(Regex("""(?i)(?:reply-to|răspunde|răspuns):[^\n\r]*"""), "")
        .replace(Regex("""(?i)(?:sent|trimis):[^\n\r]*"""), "")
        .replace(Regex("""(?i)(?:return-path):[^\n\r]*"""), "")
        .trim()
    
    // Dacă conținutul este prea scurt, probabil că am eliminat prea mult - folosește textul original
    if (content.length < 20 && text.length > 50) {
        content = text
    }
    
    return Triple(sender, subject, content)
}

@Composable
fun EmailAnalysisResultCard(result: EmailAnalysisResult) {
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
            // Scor de risc
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Scor de risc",
                    fontSize = 18.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                
                val animatedScore by animateIntAsState(
                    targetValue = result.riskScore,
                    animationSpec = tween(1000, easing = FastOutSlowInEasing),
                    label = "riskScore"
                )
                
                Text(
                    text = "$animatedScore/100",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = animatedColor
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Status badge
            Surface(
                color = animatedColor.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.shadow(8.dp, RoundedCornerShape(12.dp)),
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
            
            // Email info
            result.sender?.let { sender ->
                InfoRow("De la:", sender)
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            result.subject?.let { subject ->
                if (subject.isNotBlank()) {
                    InfoRow("Subiect:", subject)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            
            Divider(
                color = Color.Gray.copy(alpha = 0.3f),
                thickness = 1.dp,
                modifier = Modifier.shadow(2.dp)
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            InfoRow("Link-uri suspecte:", if (result.hasSuspiciousLinks) "⚠️ Da" else "✅ Nu")
            Spacer(modifier = Modifier.height(12.dp))
            
            InfoRow("Header-uri suspecte:", if (result.hasSuspiciousHeaders) "⚠️ Da" else "✅ Nu")
            Spacer(modifier = Modifier.height(12.dp))
            
            InfoRow("Conținut suspect:", if (result.suspiciousContent) "⚠️ Da" else "✅ Nu")
            Spacer(modifier = Modifier.height(12.dp))
            
            InfoRow("Sender suspect:", if (result.suspiciousSender) "⚠️ Da" else "✅ Nu")
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Divider(
                color = Color.Gray.copy(alpha = 0.3f),
                thickness = 1.dp,
                modifier = Modifier.shadow(2.dp)
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Findings
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
                        enter = fadeIn(animationSpec = tween(400, delayMillis = index * 50)) +
                                slideInHorizontally(
                                    initialOffsetX = { -it },
                                    animationSpec = tween(400, delayMillis = index * 50)
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
