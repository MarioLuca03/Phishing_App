package com.example.phishingapp.domain

import android.content.Context

data class EmailAnalysisResult(
    val riskScore: Int, // 0-100
    val isSafe: Boolean,
    val isSuspicious: Boolean,
    val isDangerous: Boolean,
    val findings: List<String>,
    val sender: String?,
    val subject: String?,
    val hasSuspiciousLinks: Boolean,
    val hasSuspiciousHeaders: Boolean,
    val suspiciousContent: Boolean,
    val suspiciousSender: Boolean
)

class EmailAnalyzer(
    private val urlAnalyzer: UrlAnalyzer,
    private val context: Context? = null
) {
    
    // Patternuri de phishing în subiect email
    private val suspiciousSubjectPatterns = listOf(
        "(?i)(?:urgent|imediAT|action.*required|acțiune.*necesară)",
        "(?i)(?:verify|verifică|confirmă|confirm.*account)",
        "(?i)(?:suspendat|blocat|expirat|închis|closed|suspended)",
        "(?i)(?:premiu|câștigat|won|felicitări|congratulations)",
        "(?i)(?:security.*alert|alertă.*securitate)",
        "(?i)(?:update.*required|actualizare.*necesară)",
        "(?i)(?:limited.*time|timp.*limitat)",
        "(?i)(?:click.*here|click.*aici|accesează)"
    )
    
    // Patternuri de phishing în conținut email
    private val suspiciousContentPatterns = listOf(
        "(?i)(?:click.*link|accesează.*link|urmărește.*link)",
        "(?i)(?:urgent.*action|acțiune.*urgentă)",
        "(?i)(?:your.*account.*will.*be|contul.*tău.*va.*fi)",
        "(?i)(?:verify.*identity|verifică.*identitatea)",
        "(?i)(?:password.*expire|parolă.*expiră)",
        "(?i)(?:confirm.*email|confirmă.*email)",
        "(?i)(?:limited.*offer|ofertă.*limitată)",
        "(?i)(?:act.*now|acționează.*acum)",
        "(?i)(?:suspended.*account|cont.*suspendat)",
        "(?i)(?:unusual.*activity|activitate.*neobișnuită)"
    )
    
    // Domenii suspecte pentru sender
    private val suspiciousSenderDomains = listOf(
        "gmail.com", "yahoo.com", "hotmail.com", "outlook.com"
    ).map { it.lowercase() }
    
    // Headers suspecte
    private val suspiciousHeaderPatterns = listOf(
        "from.*=.*\\(.*\\)", // Encoded sender names
        "reply-to.*different", // Different reply-to
        "return-path.*different" // Different return path
    )
    
    suspend fun analyzeEmail(
        sender: String,
        subject: String = "",
        content: String,
        headers: Map<String, String> = emptyMap()
    ): EmailAnalysisResult {
        val findings = mutableListOf<String>()
        var riskScore = 0
        
        try {
            // 1. Analiză sender
            val senderCheck = checkSender(sender, findings)
            riskScore += senderCheck.riskIncrease
            
            // 2. Analiză subiect (opțional - doar dacă este furnizat)
            val subjectCheck = if (subject.isNotBlank()) {
                checkSubject(subject, findings)
            } else {
                0
            }
            riskScore += subjectCheck
            
            // 3. Analiză conținut pentru patternuri suspecte (îNAINTE de link-uri pentru context mai bun)
            val contentCheck = checkContent(content, findings)
            riskScore += contentCheck
            
            // 4. Extrage și analizează link-uri din conținut (cu detalii extinse)
            val linksCheck = extractAndAnalyzeLinks(content, findings)
            riskScore += linksCheck.riskIncrease
            
            // 5. Analiză headers (dacă sunt disponibile)
            val headersCheck = checkHeaders(headers, findings)
            riskScore += headersCheck
            
            // 6. Verifică attachment-uri suspecte și alte indicatori (din conținut)
            val attachmentCheck = checkAttachments(content, findings)
            riskScore += attachmentCheck
            
            // Normalizare scor între 0-100
            riskScore = minOf(100, riskScore)
            
            // Determina statusul
            val isSafe = riskScore < 30
            val isSuspicious = riskScore in 30..60
            val isDangerous = riskScore > 60
            
            if (isSafe && findings.isEmpty()) {
                findings.add("✅ Email pare sigur")
            }
            
            return EmailAnalysisResult(
                riskScore = riskScore,
                isSafe = isSafe,
                isSuspicious = isSuspicious,
                isDangerous = isDangerous,
                findings = findings,
                sender = sender,
                subject = subject,
                hasSuspiciousLinks = linksCheck.hasSuspiciousLinks,
                hasSuspiciousHeaders = headersCheck > 0,
                suspiciousContent = contentCheck > 0,
                suspiciousSender = senderCheck.isSuspicious
            )
            
        } catch (e: Exception) {
            return EmailAnalysisResult(
                riskScore = 100,
                isSafe = false,
                isSuspicious = false,
                isDangerous = true,
                findings = listOf("❌ Eroare la analiză: ${e.message}"),
                sender = sender,
                subject = subject,
                hasSuspiciousLinks = true,
                hasSuspiciousHeaders = true,
                suspiciousContent = true,
                suspiciousSender = true
            )
        }
    }
    
    private fun checkSender(sender: String, findings: MutableList<String>): SenderCheckResult {
        var riskIncrease = 0
        var isSuspicious = false
        
        // Extrage email-ul din sender (poate fi "Name <email@domain.com>")
        val emailRegex = Regex("""([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,})""")
        val emailMatch = emailRegex.find(sender)
        val email = emailMatch?.value ?: sender
        
        // Verifică format email valid
        if (!email.contains("@")) {
            riskIncrease += 30
            findings.add("🚨 Format sender invalid - posibil fals")
            return SenderCheckResult(riskIncrease, true)
        }
        
        val parts = email.split("@")
        if (parts.size != 2) {
            riskIncrease += 25
            findings.add("⚠️ Format email sender suspect")
            return SenderCheckResult(riskIncrease, true)
        }
        
        val senderDomain = parts[1].lowercase()
        
        // Verifică dacă domeniul sender-ului este suspect
        // Email-uri de la companii legitime nu ar trebui să vină de la gmail/yahoo
        if (senderDomain in suspiciousSenderDomains) {
            // Nu e neapărat periculos, dar e suspect dacă se pretinde că e de la o companie
            riskIncrease += 10
            findings.add("⚠️ Email trimis de la provider public ($senderDomain) - poate fi fals")
            isSuspicious = true
        }
        
        // Verifică dacă numele din sender diferă de domeniu
        if (sender.contains("<") && sender.contains(">")) {
            val namePart = sender.substringBefore("<").trim()
            if (namePart.isNotEmpty() && !email.contains(namePart.split("@")[0].lowercase())) {
                riskIncrease += 15
                findings.add("⚠️ Nume sender nu corespunde cu adresa email - posibil spoofing")
                isSuspicious = true
            }
        }
        
        return SenderCheckResult(riskIncrease, isSuspicious)
    }
    
    private fun checkSubject(subject: String, findings: MutableList<String>): Int {
        // Subject este opțional acum, nu mai verificăm dacă e gol
        if (subject.isBlank()) {
            return 0
        }
        
        var riskIncrease = 0
        
        for (pattern in suspiciousSubjectPatterns) {
            if (Regex(pattern).containsMatchIn(subject)) {
                riskIncrease += 20
                findings.add("⚠️ Subiect conține patternuri comune de phishing: \"$subject\"")
                break
            }
        }
        
        // Verifică dacă subiectul conține multe caractere speciale (posibil spam)
        val specialCharCount = subject.count { !it.isLetterOrDigit() && it != ' ' && it != '-' && it != '_' }
        if (specialCharCount > subject.length / 3) {
            riskIncrease += 10
            findings.add("⚠️ Subiect conține multe caractere speciale (posibil spam): \"$subject\"")
        }
        
        return riskIncrease
    }
    
    private suspend fun extractAndAnalyzeLinks(
        content: String,
        findings: MutableList<String>
    ): LinksCheckResult {
        // Extrage toate link-urile din conținut
        val urlPattern = Regex("""(https?://[^\s<>"{}|\\^`\[\]]+)""", RegexOption.IGNORE_CASE)
        val links = urlPattern.findAll(content).map { it.value }.toList()
        
        if (links.isEmpty()) {
            return LinksCheckResult(0, false, emptyList())
        }
        
        var riskIncrease = 0
        var hasSuspiciousLinks = false
        var suspiciousLinkCount = 0
        val linkDetails = mutableListOf<String>()
        
        findings.add("📎 Detectat(e) ${links.size} link(uri) în email")
        
        for ((index, link) in links.withIndex()) {
            try {
                val urlResult = urlAnalyzer.analyzeUrl(link)
                
                if (urlResult.isSuspicious || urlResult.isDangerous) {
                    suspiciousLinkCount++
                    hasSuspiciousLinks = true
                    
                    val riskLevel = if (urlResult.isDangerous) "PERICULOS" else "SUSPECT"
                    val emoji = if (urlResult.isDangerous) "🚨" else "⚠️"
                    
                    if (urlResult.isDangerous) {
                        riskIncrease += 30
                    } else {
                        riskIncrease += 20
                    }
                    
                    // Detalii despre link
                    val linkDetail = buildString {
                        append("$emoji Link $riskLevel #${index + 1}: $link\n")
                        append("   • Scor de risc: ${urlResult.riskScore}/100\n")
                        
                        // Adaugă principalele findings din analiza URL
                        if (urlResult.findings.isNotEmpty()) {
                            append("   • Probleme detectate:\n")
                            urlResult.findings.take(3).forEach { finding ->
                                append("     - ${finding.replace("⚠️", "").replace("🚨", "").trim()}\n")
                            }
                        }
                        
                        // Detalii specifice
                        if (urlResult.domain != null) {
                            append("   • Domeniu: ${urlResult.domain}\n")
                        }
                        if (!urlResult.hasHttps) {
                            append("   • ⚠️ Nu folosește HTTPS\n")
                        }
                        if (urlResult.hasRedirects) {
                            append("   • ⚠️ Conține redirecționări suspecte\n")
                        }
                        if (urlResult.suspiciousParams) {
                            append("   • ⚠️ Parametri suspecti detectați\n")
                        }
                    }
                    
                    linkDetails.add(linkDetail.trim())
                } else {
                    linkDetails.add("✅ Link #${index + 1}: $link - Pare sigur (Scor: ${urlResult.riskScore}/100)")
                }
            } catch (e: Exception) {
                riskIncrease += 15
                findings.add("⚠️ Link invalid sau suspect: $link - Eroare: ${e.message}")
                linkDetails.add("❌ Link #${index + 1}: $link - Nu poate fi analizat")
                hasSuspiciousLinks = true
            }
        }
        
        if (suspiciousLinkCount == links.size && links.isNotEmpty()) {
            riskIncrease += 10
            findings.add("🚨 TOATE link-urile din email sunt suspecte! NU accesa link-urile!")
        } else if (suspiciousLinkCount > 0) {
            findings.add("⚠️ $suspiciousLinkCount din ${links.size} link(uri) sunt suspecte sau periculoase")
        }
        
        // Adaugă detaliile despre link-uri în findings
        if (linkDetails.isNotEmpty()) {
            findings.add("") // Separator
            findings.add("🔍 Detalii despre link-uri:")
            findings.addAll(linkDetails)
        }
        
        return LinksCheckResult(riskIncrease, hasSuspiciousLinks, linkDetails)
    }
    
    private fun checkContent(content: String, findings: MutableList<String>): Int {
        if (content.isBlank()) {
            findings.add("⚠️ Email-ul nu conține text - poate fi suspect")
            return 5
        }
        
        var riskIncrease = 0
        val detectedPatterns = mutableListOf<String>()
        
        // Verifică fiecare pattern și notează ce s-a găsit
        for (pattern in suspiciousContentPatterns) {
            val regex = Regex(pattern)
            val matches = regex.findAll(content).toList()
            if (matches.isNotEmpty()) {
                detectedPatterns.add(pattern)
                // Extrage contextul în care s-a găsit pattern-ul
                matches.take(2).forEach { match ->
                    val contextStart = maxOf(0, match.range.first - 30)
                    val contextEnd = minOf(content.length, match.range.last + 30)
                    val context = content.substring(contextStart, contextEnd).trim()
                    findings.add("⚠️ Pattern suspect găsit: \"${context.take(60)}...\"")
                }
            }
        }
        
        if (detectedPatterns.isNotEmpty()) {
            riskIncrease += 15 + (detectedPatterns.size * 5)
            findings.add("🚨 Conținut conține ${detectedPatterns.size} tip(uri) de patternuri comune de phishing detectate!")
        }
        
        // Verifică dacă email-ul cere acțiune urgentă - cu detalii
        val urgentPattern = Regex("""(?i)(?:urgent|imediAT|now|acum|within.*24.*hour|within.*48.*hour)""")
        val urgentMatches = urgentPattern.findAll(content).toList()
        if (urgentMatches.isNotEmpty()) {
            riskIncrease += 15
            findings.add("🚨 Email folosește presiune psihologică - menționează urgență de ${urgentMatches.size} ori")
            urgentMatches.take(2).forEach { match ->
                val context = content.substring(
                    maxOf(0, match.range.first - 20),
                    minOf(content.length, match.range.last + 20)
                )
                findings.add("   → Context: \"${context.trim()}\"")
            }
        }
        
        // Verifică dacă cere date personale - cu detalii
        val personalDataPattern = Regex("""(?i)(?:password|parolă|credit.*card|card.*number|cvv|ssn|cod.*pin|cod.*securitate|security.*code)""")
        val personalMatches = personalDataPattern.findAll(content).toList()
        if (personalMatches.isNotEmpty()) {
            riskIncrease += 30
            findings.add("🚨🚨🚨 ATENȚIE: Email cere date personale sensibile!")
            findings.add("   → Tipuri de date cerute: ${personalMatches.map { it.value.lowercase() }.distinct().joinToString(", ")}")
            findings.add("   → NICIODATĂ nu trimite astfel de date prin email - acesta este un atac de phishing!")
        }
        
        // Verifică solicitări de click
        val clickPattern = Regex("""(?i)(?:click.*here|click.*aici|accesează.*link|urmărește.*link|click.*now)""")
        if (clickPattern.containsMatchIn(content)) {
            riskIncrease += 10
            findings.add("⚠️ Email conține solicitări repetate de click pe link-uri")
        }
        
        // Verifică greșeli gramaticale sau formatare ciudată (posibil indicator)
        if (content.length < 50) {
            riskIncrease += 5
            findings.add("⚠️ Email foarte scurt - poate fi suspect")
        }
        
        return riskIncrease
    }
    
    private fun checkHeaders(headers: Map<String, String>, findings: MutableList<String>): Int {
        if (headers.isEmpty()) {
            return 0
        }
        
        var riskIncrease = 0
        
        // Verifică header From vs Reply-To
        val from = headers["From"]?.lowercase() ?: ""
        val replyTo = headers["Reply-To"]?.lowercase() ?: ""
        
        if (replyTo.isNotEmpty() && from.isNotEmpty() && replyTo != from) {
            riskIncrease += 20
            findings.add("⚠️ Header-uri suspecte: From diferă de Reply-To (posibil spoofing)")
        }
        
        // Verifică Return-Path
        val returnPath = headers["Return-Path"]?.lowercase() ?: ""
        if (returnPath.isNotEmpty() && from.isNotEmpty() && !returnPath.contains(from.split("@")[1])) {
            riskIncrease += 15
            findings.add("⚠️ Return-Path diferă de domeniul From (posibil fals)")
        }
        
        // Verifică SPF/DKIM (dacă sunt disponibile)
        val spf = headers["Received-SPF"] ?: ""
        if (spf.contains("fail", ignoreCase = true)) {
            riskIncrease += 30
            findings.add("🚨 SPF check FAILED - email-ul poate fi falsificat!")
        }
        
        return riskIncrease
    }
    
    private fun checkAttachments(content: String, findings: MutableList<String>): Int {
        // Caută mențiuni de attachment-uri suspecte
        val attachmentPatterns = listOf(
            "(?i)(?:download.*attachment|descarcă.*fișier)",
            "(?i)(?:open.*file|deschide.*fișier)",
            "(?i)(?:\\.exe|\\.bat|\\.scr|\\.vbs|\\.js)",
        )
        
        var riskIncrease = 0
        
        for (pattern in attachmentPatterns) {
            if (Regex(pattern).containsMatchIn(content)) {
                riskIncrease += 25
                findings.add("🚨 Email menționează attachment-uri suspecte - NU descărca fișiere necunoscute!")
                break
            }
        }
        
        return riskIncrease
    }
    
    private data class SenderCheckResult(val riskIncrease: Int, val isSuspicious: Boolean)
    private data class LinksCheckResult(
        val riskIncrease: Int, 
        val hasSuspiciousLinks: Boolean,
        val linkDetails: List<String> = emptyList()
    )
}

