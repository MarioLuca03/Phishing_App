package com.example.phishingapp.domain

import java.net.URL
import java.net.URLDecoder
import javax.net.ssl.HttpsURLConnection
import kotlin.math.max
import kotlin.math.min
import android.util.Base64 as AndroidBase64
import android.content.Context
import java.util.regex.Pattern

data class UrlAnalysisResult(
    val riskScore: Int, // 0-100 (scor final = medie între pattern-based și ML)
    val patternBasedScore: Int, // 0-100 (scor din reguli heuristice)
    val mlScore: Int?, // 0-100 (scor ML Random Forest)
    val isSafe: Boolean,
    val isSuspicious: Boolean,
    val isDangerous: Boolean,
    val findings: List<String>, // Findings din verificări heuristice (pentru informații detaliate)
    val domain: String?,
    val hasRedirects: Boolean,
    val hasHttps: Boolean,
    val sslValid: Boolean?,
    val suspiciousParams: Boolean
)

class UrlAnalyzer(private val context: Context? = null) {
    
    // ML Scorer instance (optional, requires Context)
    private val mlScorer: UrlMlScorer? by lazy {
        context?.let { UrlMlScorer(it) }
    }
    
    // Liste extinse de domenii legitime cunoscute (branduri populare - ținte frecvente de phishing)
    private val legitimateDomains = setOf(
        // Search engines & Social media
        "google.com", "google.ro", "facebook.com", "twitter.com", "instagram.com",
        "linkedin.com", "youtube.com", "tiktok.com", "snapchat.com",
        
        // Tech companies
        "microsoft.com", "apple.com", "amazon.com", "adobe.com", "oracle.com",
        "ibm.com", "salesforce.com", "zoom.us",
        
        // E-commerce & Payment
        "ebay.com", "paypal.com", "stripe.com", "shopify.com",
        "alibaba.com", "aliexpress.com",
        
        // Banking & Financial
        "visa.com", "mastercard.com", "americanexpress.com",
        "bancatransilvania.ro", "ing.ro", "raiffeisen.ro", "brd.ro", 
        "bcr.ro", "unicredit.ro", "revolut.com",
        
        // Email providers
        "gmail.com", "yahoo.com", "outlook.com", "hotmail.com",
        "protonmail.com", "icloud.com",
        
        // Entertainment & Streaming
        "netflix.com", "spotify.com", "disney.com", "hulu.com",
        "primevideo.com",
        
        // Gaming
        "steam.com", "epicgames.com", "roblox.com", "xbox.com",
        "playstation.com", "nintendo.com",
        
        // Cloud & Storage
        "dropbox.com", "onedrive.com", "icloud.com", "mega.nz",
        
        // Communication
        "whatsapp.com", "telegram.org", "discord.com", "skype.com",
        
        // Travel & Services
        "airbnb.com", "uber.com", "booking.com", "expedia.com",
        
        // Development
        "github.com", "gitlab.com", "bitbucket.org",
        
        // News & Media
        "bbc.com", "cnn.com", "nytimes.com"
    )
    
    // TLD-uri suspecte frecvent folosite în phishing
    private val suspiciousTlds = setOf(
        "zip", "xyz", "click", "top", "live", "info", "ru", "tk", "cyou",
        "ml", "ga", "cf", "gq", "online", "site", "website", "space",
        "download", "stream", "press", "news", "tech", "store", "shop"
    )
    
    // Providers de hosting gratuit / suspect
    private val freeHostingProviders = setOf(
        "000webhostapp.com", "weebly.com", "blogspot.com", "wordpress.com",
        "tumblr.com", "wix.com", "sites.google.com", "github.io",
        "netlify.app", "vercel.app", "herokuapp.com", "glitch.me",
        "repl.it", "codepen.io", "jsfiddle.net"
    )
    
    // Homoglyphs - caractere Unicode care arată similar cu cele latine
    // Map de la caractere latine la homoglyphs posibili (chirilic, grec, etc.)
    private val homoglyphMap = mapOf(
        'a' to setOf('а', 'α'), // а = chirilic, α = grec
        'e' to setOf('е', 'ε'), // е = chirilic, ε = grec
        'o' to setOf('о', 'ο'), // о = chirilic, ο = grec
        'p' to setOf('р', 'ρ'), // р = chirilic, ρ = grec
        'c' to setOf('с'),      // с = chirilic
        'x' to setOf('х'),      // х = chirilic
        'y' to setOf('у'),      // у = chirilic
        'i' to setOf('і', 'ι'), // і = chirilic, ι = grec
        'r' to setOf('г'),      // г = chirilic
        'n' to setOf('п'),      // п = chirilic
        'm' to setOf('м'),      // м = chirilic
        'l' to setOf('I', '1')  // I și 1 pot fi confundate cu l
    )
    
    // Patternuri de phishing în parametri URL
    private val suspiciousParamPatterns = listOf(
        "password", "pass", "pwd", "pin", "security", "verify", "confirm",
        "account", "login", "credential", "update", "urgent", "action",
        "validate", "secure", "authentication", "suspended", "blocked"
    )
    
    // Caractere suspecte folosite în typosquatting
    private val suspiciousCharReplacements = mapOf(
        'o' to setOf('0', 'O'),
        'i' to setOf('1', 'l', 'I'),
        'e' to setOf('3'),
        'a' to setOf('@', '4'),
        's' to setOf('5', '$'),
        'g' to setOf('9', '6'),
        't' to setOf('7')
    )
    
    suspend fun analyzeUrl(urlString: String): UrlAnalysisResult {
        val findings = mutableListOf<String>()
        var riskScore = 0
        
        try {
            val decodedUrl = URLDecoder.decode(urlString, "UTF-8")
            val url = URL(if (!decodedUrl.startsWith("http")) "https://$decodedUrl" else decodedUrl)
            val domain = url.host?.lowercase()
            
            if (domain == null) {
                return UrlAnalysisResult(
                    riskScore = 100,
                    patternBasedScore = 100,
                    mlScore = null,
                    isSafe = false,
                    isSuspicious = false,
                    isDangerous = true,
                    findings = listOf("URL invalid - nu poate fi analizat"),
                    domain = null,
                    hasRedirects = false,
                    hasHttps = false,
                    sslValid = null,
                    suspiciousParams = true
                )
            }
            
            // 1. Verificare dacă e domeniu legitim (înainte de HTTPS pentru a ajusta riscul)
            val cleanDomain = domain.removePrefix("www.")
            val isLegitimateDomain = legitimateDomains.any { legitimate ->
                cleanDomain == legitimate || cleanDomain.endsWith(".$legitimate")
            }
            
            // 2. Verificare HTTPS
            val hasHttps = url.protocol == "https"
            if (!hasHttps) {
                // Pentru domenii legitime, HTTP este mai puțin suspect (ex: Apple folosește HTTP pe unele pagini)
                if (isLegitimateDomain) {
                    riskScore += 5 // Risc mic pentru domenii legitime
                    findings.add("⚠️ URL-ul nu folosește HTTPS, dar domeniul pare legitim")
                } else {
                    riskScore += 30
                    findings.add("⚠️ URL-ul nu folosește HTTPS (comunicația nu este criptată)")
                }
            }
            
            // 3. Verificare SSL Certificate (simplificat)
            val sslValid = if (hasHttps) {
                try {
                    val connection = url.openConnection() as? HttpsURLConnection
                    connection?.connectTimeout = 5000
                    connection?.readTimeout = 5000
                    connection?.connect()
                    val isValid = connection?.responseCode == HttpsURLConnection.HTTP_OK
                    connection?.disconnect()
                    if (!isValid) {
                        riskScore += 20
                        findings.add("⚠️ Certificate SSL suspect sau invalid - datele tale pot fi interceptate")
                    }
                    isValid
                } catch (e: Exception) {
                    riskScore += 15
                    findings.add("⚠️ Eroare la verificarea certificatului SSL - nu se poate confirma securitatea conexiunii")
                    false
                }
            } else {
                null
            }
            
            // 4. Detectare domenii false (typosquatting + homoglyphs)
            // Dacă e deja legitim, skip checks detaliate pentru a evita false positives
            val domainCheck = if (!isLegitimateDomain) {
                checkDomainSquatting(domain, findings)
            } else {
                DomainCheckResult(riskIncrease = 0) // Domeniu legitim confirmat
            }
            riskScore += domainCheck.riskIncrease
            
            // 5. Verificare TLD suspect (skip pentru domenii legitime)
            val tldCheck = if (!isLegitimateDomain) {
                checkSuspiciousTld(domain, findings)
            } else {
                0 // Skip pentru domenii legitime
            }
            riskScore += tldCheck
            
            // 6. Verificare hosting gratuit / provider suspect (skip pentru domenii legitime)
            val freeHostingCheck = if (!isLegitimateDomain) {
                checkFreeHostingProvider(domain, urlString, findings)
            } else {
                0 // Skip pentru domenii legitime
            }
            riskScore += freeHostingCheck
            
            // 7. Verificare redirecționări (simplificat - verifică patternuri comune)
            val hasRedirects = checkForRedirectPatterns(urlString, findings)
            if (hasRedirects) {
                riskScore += 25
            }
            
            // 8. Analiză structură URL și parametri suspecti
            val suspiciousParams = checkSuspiciousParameters(urlString, findings)
            if (suspiciousParams) {
                riskScore += 20
            }
            
            // 9. Verificare caractere neobișnuite în domeniu (inclusiv homoglyphs)
            checkUnusualCharacters(domain, findings)?.let {
                riskScore += it
            }
            
            // 10. Verificare lungime și complexitate suspectă
            if (urlString.length > 150) {
                riskScore += 10
                findings.add("⚠️ URL foarte lung (posibil ascuns cu redirectări)")
            }
            
            // 11. Detectare IP în loc de domeniu
            val ipCheck = checkIpAddress(domain, findings)
            riskScore += ipCheck
            
            // 12. Detectare caracter '@' în URL
            val atCharCheck = checkAtCharacter(urlString, findings)
            riskScore += atCharCheck
            
            // 13. Deep URL decoding (3 nivele) și detectare obfuscation
            val deeplyDecodedUrl = deepUrlDecoding(urlString)
            val obfuscationCheck = checkEncodedObfuscation(deeplyDecodedUrl, findings)
            riskScore += obfuscationCheck
            
            // 14. Detecție double-domain (paypal.com.evil.com)
            val doubleDomainCheck = checkDoubleDomain(domain, findings)
            riskScore += doubleDomainCheck
            
            // 15. Detectare Base64 în query
            val base64Check = checkBase64InQuery(urlString, findings)
            riskScore += base64Check
            
            // 16. Detectare javascript/data-uri în query
            val jsDataUriCheck = checkJavascriptDataUri(urlString, findings)
            riskScore += jsDataUriCheck
            
            // 17. Detectare port neobișnuit
            val portCheck = checkUnusualPort(url, findings)
            riskScore += portCheck
            
            // 18. Detectare exces cifre în domeniu
            val digitCheck = checkExcessiveDigitsInDomain(domain, findings)
            riskScore += digitCheck
            
            // 19. Expandare URL scurtat (pentru link-uri scurte cunoscute)
            val shortUrlCheck = checkShortUrlExpansion(urlString, domain, findings)
            riskScore += shortUrlCheck
            
            // 20. Detectare branduri multiple în path
            val multipleBrandsCheck = checkMultipleBrandsInPath(url.path ?: "", findings)
            riskScore += multipleBrandsCheck
            
            // Normalizare scor heuristice între 0-100
            val patternBasedScore = min(100, riskScore)
            
            // ML Analysis (Random Forest model)
            var mlScore: Int? = null
            
            mlScorer?.let { scorer ->
                try {
                    mlScore = scorer.score(urlString)
                    android.util.Log.d("UrlAnalyzer", "Pattern-based Score: $patternBasedScore, ML Score: $mlScore")
                } catch (e: Exception) {
                    android.util.Log.e("UrlAnalyzer", "Error calculating ML score: ${e.message}", e)
                }
            }
            
            // Scor final = medie între heuristice și ML
            val finalRiskScore = if (mlScore != null) {
                // Medie simplă între cele două scoruri
                ((patternBasedScore + mlScore) / 2).toInt()
            } else {
                // Dacă ML nu e disponibil, folosim doar pattern-based
                patternBasedScore
            }
            
            // Determina statusul bazat pe scorul ML
            val isSafe = finalRiskScore < 30
            val isSuspicious = finalRiskScore in 30..60
            val isDangerous = finalRiskScore > 60
            
            if (isSafe && findings.isEmpty()) {
                findings.add("✅ URL pare sigur (bazat pe analiza ML)")
            }
            
            return UrlAnalysisResult(
                riskScore = finalRiskScore,
                patternBasedScore = patternBasedScore,
                mlScore = mlScore,
                isSafe = isSafe,
                isSuspicious = isSuspicious,
                isDangerous = isDangerous,
                findings = findings,
                domain = domain,
                hasRedirects = hasRedirects,
                hasHttps = hasHttps,
                sslValid = sslValid,
                suspiciousParams = suspiciousParams
            )
            
        } catch (e: Exception) {
            return UrlAnalysisResult(
                riskScore = 100,
                patternBasedScore = 100,
                mlScore = null,
                isSafe = false,
                isSuspicious = false,
                isDangerous = true,
                findings = listOf("❌ Eroare la analiză: ${e.message}"),
                domain = null,
                hasRedirects = false,
                hasHttps = false,
                sslValid = null,
                suspiciousParams = true
            )
        }
    }
    
    private fun checkDomainSquatting(domain: String, findings: MutableList<String>): DomainCheckResult {
        var riskIncrease = 0
        
        // Elimină www. pentru comparație
        val cleanDomain = domain.removePrefix("www.")
        
        // Verifică dacă domeniul este unul legitim cunoscut (inclusiv subdomenii)
        val isLegitimate = legitimateDomains.any { legitimate ->
            cleanDomain == legitimate || 
            cleanDomain.endsWith(".$legitimate") || // Subdomenii: support.apple.com, www.apple.com
            cleanDomain.split(".").lastOrNull() == legitimate.split(".").lastOrNull() && 
            cleanDomain.contains(legitimate.split(".").first()) // Recunoaște subdomenii: support.apple.com, store.apple.com
        }
        
        if (isLegitimate) {
            return DomainCheckResult(riskIncrease = 0)
        }
        
        // Verifică typosquatting - domenii foarte similare cu cele legitime
        for (legitimate in legitimateDomains) {
            val similarity = calculateSimilarity(cleanDomain, legitimate)
            
            // Dacă e foarte similar dar nu e același
            if (similarity > 0.7 && similarity < 1.0) {
                riskIncrease += 40
                findings.add("🚨 Domeniu suspect: '$domain' este foarte similar cu '$legitimate' (posibil typosquatting)")
                return DomainCheckResult(riskIncrease = riskIncrease)
            }
            
            // Verifică domenii cu caractere înlocuite (ex: g00gle.com, paypaI.com)
            if (hasCharacterSubstitutions(cleanDomain, legitimate)) {
                riskIncrease += 35
                findings.add("🚨 Domeniu fals detectat: '$domain' imită '$legitimate' cu caractere înlocuite")
                return DomainCheckResult(riskIncrease = riskIncrease)
            }
            
            // Verifică homoglyph attacks (caractere Unicode asemănătoare)
            if (hasHomoglyphSubstitution(cleanDomain, legitimate)) {
                riskIncrease += 45
                findings.add("🚨 ATAC HOMOGLYPH detectat: '$domain' imită '$legitimate' cu caractere Unicode similare (ex: chirilic)")
                return DomainCheckResult(riskIncrease = riskIncrease)
            }
            
            // Verifică domenii care conțin numele unui domeniu legitim dar cu prefix/sufix
            if (cleanDomain.contains(legitimate.split(".")[0]) && cleanDomain != legitimate) {
                riskIncrease += 25
                findings.add("⚠️ Domeniu suspect: '$domain' conține referințe la '$legitimate'")
            }
        }
        
        // Verifică subdomenii suspecte
        if (cleanDomain.count { it == '.' } > 2) {
            riskIncrease += 15
            findings.add("⚠️ Prea multe subdomenii - posibil redirecționare")
        }
        
        return DomainCheckResult(riskIncrease = riskIncrease)
    }
    
    private fun calculateSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        
        val longer = if (s1.length > s2.length) s1 else s2
        val shorter = if (s1.length > s2.length) s2 else s1
        
        if (longer.length == 0) return 1.0
        
        val editDistance = levenshteinDistance(s1.lowercase(), s2.lowercase())
        return (longer.length - editDistance).toDouble() / longer.length
    }
    
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        
        return dp[s1.length][s2.length]
    }
    
    private fun hasCharacterSubstitutions(domain: String, legitimate: String): Boolean {
        if (domain.length != legitimate.length) return false
        
        var substitutions = 0
        for (i in domain.indices) {
            val domainChar = domain[i]
            val legitChar = legitimate[i]
            
            if (domainChar != legitChar) {
                val possibleReplacements = suspiciousCharReplacements[legitChar.lowercaseChar()]
                if (possibleReplacements?.contains(domainChar.lowercaseChar()) == true) {
                    substitutions++
                } else {
                    return false
                }
            }
        }
        
        return substitutions > 0 && substitutions <= 2
    }
    
    private fun checkForRedirectPatterns(urlString: String, findings: MutableList<String>): Boolean {
        val redirectPatterns = listOf(
            "redirect", "goto", "url=", "link=", "ref=", "r=", "u=",
            "destination=", "target=", "return=", "continue="
        )
        
        val lowerUrl = urlString.lowercase()
        for (pattern in redirectPatterns) {
            if (lowerUrl.contains(pattern)) {
                findings.add("⚠️ Detectat pattern de redirecționare: '$pattern'")
                return true
            }
        }
        
        // Verifică linkuri scurte suspecte
        val shortLinkDomains = listOf("bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly")
        val domain = URL(if (!urlString.startsWith("http")) "https://$urlString" else urlString).host
        if (domain in shortLinkDomains) {
            findings.add("⚠️ Link scurt detectat - nu se poate verifica destinația finală")
            return true
        }
        
        return false
    }
    
    private fun checkSuspiciousParameters(urlString: String, findings: MutableList<String>): Boolean {
        val queryStart = urlString.indexOf('?')
        if (queryStart == -1) return false
        
        val query = urlString.substring(queryStart + 1).lowercase()
        
        for (pattern in suspiciousParamPatterns) {
            if (query.contains(pattern)) {
                findings.add("⚠️ Parametru suspect detectat: '$pattern' (poate încerca să obțină date personale)")
                return true
            }
        }
        
        return false
    }
    
    private fun checkSuspiciousTld(domain: String, findings: MutableList<String>): Int {
        val tld = domain.substringAfterLast('.', "")
        
        if (tld in suspiciousTlds) {
            findings.add("🚨 TLD suspect detectat: '.$tld' - frecvent folosit în phishing")
            return 25
        }
        
        return 0
    }
    
    private fun checkFreeHostingProvider(domain: String, urlString: String, findings: MutableList<String>): Int {
        var riskIncrease = 0
        
        // Verifică dacă domeniul sau subdomeniul este un provider gratuit
        val cleanDomain = domain.removePrefix("www.")
        
        for (provider in freeHostingProviders) {
            if (cleanDomain.contains(provider) || cleanDomain.endsWith(".$provider")) {
                riskIncrease += 30
                findings.add("🚨 Hosting gratuit detectat: '$provider' - site-urile de phishing folosesc adesea platforme gratuite")
                break
            }
        }
        
        // Verifică URL-uri foarte lungi pe platforme gratuite (pattern comun în phishing)
        if (urlString.length > 200 && cleanDomain.contains(".")) {
            // Verifică dacă e pe un subdomeniu (posibil platformă gratuită)
            val parts = cleanDomain.split(".")
            if (parts.size >= 3) {
                riskIncrease += 15
                findings.add("⚠️ URL foarte lung pe subdomeniu - posibil site temporar de phishing")
            }
        }
        
        return riskIncrease
    }
    
    private fun hasHomoglyphSubstitution(domain: String, legitimate: String): Boolean {
        if (domain.length != legitimate.length) return false
        
        // Normalizează domeniile pentru comparație (elimină homoglyphs)
        val normalizedDomain = normalizeHomoglyphs(domain)
        val normalizedLegitimate = normalizeHomoglyphs(legitimate)
        
        // Dacă după normalizare sunt identice, dar originalele diferă, avem homoglyphs
        if (normalizedDomain == normalizedLegitimate && domain != legitimate) {
            return true
        }
        
        // Verifică direct dacă există caractere homoglyph
        var hasHomoglyphs = false
        for (i in domain.indices) {
            val domainChar = domain[i]
            val legitChar = legitimate[i]
            
            if (domainChar != legitChar) {
                // Verifică dacă este un homoglyph
                val latinChar = legitChar.lowercaseChar()
                if (homoglyphMap.containsKey(latinChar)) {
                    if (homoglyphMap[latinChar]?.contains(domainChar) == true) {
                        hasHomoglyphs = true
                        break
                    }
                }
                // Verifică invers - dacă caracterul din domeniu e un homoglyph
                for ((latin, glyphs) in homoglyphMap) {
                    if (glyphs.contains(domainChar) && latin == legitChar.lowercaseChar()) {
                        hasHomoglyphs = true
                        break
                    }
                }
                if (hasHomoglyphs) break
            }
        }
        
        return hasHomoglyphs
    }
    
    private fun normalizeHomoglyphs(text: String): String {
        var normalized = text.lowercase()
        
        // Înlocuiește homoglyphs cu caracterele latine corespunzătoare
        for ((latin, glyphs) in homoglyphMap) {
            for (glyph in glyphs) {
                normalized = normalized.replace(glyph, latin)
            }
        }
        
        return normalized
    }
    
    private fun checkUnusualCharacters(domain: String, findings: MutableList<String>): Int? {
        // Verifică caractere neobișnuite în domeniu (exclude homoglyphs care sunt verificate separat)
        val unusualChars = domain.filter { char ->
            val isAsciiLetterOrDigit = char.isLetterOrDigit() && char.code < 128
            val isDotOrDash = char == '.' || char == '-'
            !isAsciiLetterOrDigit && !isDotOrDash
        }
        
        if (unusualChars.isNotEmpty()) {
            findings.add("⚠️ Domeniu conține caractere neobișnuite: $unusualChars")
            return 15
        }
        
        // Verifică homoglyphs direct în domeniu
        for ((latin, glyphs) in homoglyphMap) {
            for (glyph in glyphs) {
                if (domain.contains(glyph)) {
                    findings.add("⚠️ Detectat caracter homoglyph '$glyph' (similar cu '$latin') - posibil încercare de păcălire")
                    return 20
                }
            }
        }
        
        // Verifică dacă are prea multe cratime
        if (domain.count { it == '-' } > 3) {
            findings.add("⚠️ Domeniu cu prea multe cratime (posibil suspect)")
            return 10
        }
        
        return null
    }
    
    private data class DomainCheckResult(val riskIncrease: Int)
    
    /**
     * 11. Detectare IP în loc de domeniu
     * URL-urile care folosesc IP direct sunt suspecte (ex: http://192.168.1.1/login)
     */
    private fun checkIpAddress(domain: String?, findings: MutableList<String>): Int {
        if (domain == null) return 0
        
        val ipPattern = Pattern.compile(
            "^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
        )
        
        val cleanDomain = domain.removePrefix("www.")
        if (ipPattern.matcher(cleanDomain).matches()) {
            findings.add("🚨 URL folosește adresă IP directă în loc de domeniu - foarte suspect!")
            return 40
        }
        
        return 0
    }
    
    /**
     * 12. Detectare caracter '@' în URL
     * URL-uri cu '@' pot fi folosite pentru credential injection (ex: http://user@evil.com@legit.com)
     */
    private fun checkAtCharacter(urlString: String, findings: MutableList<String>): Int {
        val atCount = urlString.count { it == '@' }
        if (atCount > 0) {
            // Permite doar un '@' la început pentru user:pass@domain format valid
            val firstAt = urlString.indexOf('@')
            val hasProtocol = urlString.contains("://")
            
            if (atCount > 1 || (atCount == 1 && firstAt > 0 && !hasProtocol)) {
                findings.add("🚨 URL conține caracter '@' suspect - posibil credential injection attack")
                return 35
            } else if (atCount == 1 && firstAt > 0) {
                findings.add("⚠️ URL conține '@' după protocol - verifică manual")
                return 15
            }
        }
        
        return 0
    }
    
    /**
     * 13. Deep URL decoding (3 nivele) și detectare obfuscation
     * Decodă URL-ul de mai multe ori pentru a detecta encoding multiplu
     */
    private fun deepUrlDecoding(urlString: String): String {
        var decoded = urlString
        var level = 0
        var previousDecoded = ""
        
        while (level < 3 && decoded != previousDecoded) {
            previousDecoded = decoded
            try {
                decoded = URLDecoder.decode(decoded, "UTF-8")
                level++
            } catch (e: Exception) {
                break
            }
        }
        
        return decoded
    }
    
    /**
     * 13b. Detectare encoded obfuscation (%, unicode)
     */
    private fun checkEncodedObfuscation(decodedUrl: String, findings: MutableList<String>): Int {
        var riskIncrease = 0
        
        // Verifică procentaj de encoding în URL
        val encodedChars = decodedUrl.count { it == '%' }
        val totalChars = decodedUrl.length
        
        if (totalChars > 0) {
            val encodingRatio = encodedChars.toDouble() / totalChars
            if (encodingRatio > 0.15) { // Mai mult de 15% encoding
                riskIncrease += 25
                findings.add("🚨 URL conține prea mult encoding (${(encodingRatio * 100).toInt()}%) - posibil obfuscation")
            } else if (encodingRatio > 0.05) { // Mai mult de 5%
                riskIncrease += 10
                findings.add("⚠️ URL conține encoding semnificativ - verifică manual")
            }
        }
        
        // Verifică encoding Unicode suspect (mai mult de 10% caractere non-ASCII)
        val nonAsciiChars = decodedUrl.count { it.code > 127 }
        if (totalChars > 0) {
            val unicodeRatio = nonAsciiChars.toDouble() / totalChars
            if (unicodeRatio > 0.10) {
                riskIncrease += 20
                findings.add("🚨 URL conține multe caractere Unicode (${(unicodeRatio * 100).toInt()}%) - posibil obfuscation")
            }
        }
        
        // Verifică encoding multiplu (dacă după decoding mai există encoding)
        val doubleEncodedPattern = Pattern.compile("%25[0-9A-Fa-f]{2}")
        if (doubleEncodedPattern.matcher(decodedUrl).find()) {
            riskIncrease += 30
            findings.add("🚨 Detectat encoding dublu (%%XX) - încercare clară de obfuscation!")
        }
        
        return riskIncrease
    }
    
    /**
     * 14. Detecție double-domain (paypal.com.evil.com)
     * Atacatori folosesc subdomenii care conțin numele unui domeniu legitim
     */
    private fun checkDoubleDomain(domain: String?, findings: MutableList<String>): Int {
        if (domain == null) return 0
        
        val cleanDomain = domain.removePrefix("www.").lowercase()
        val parts = cleanDomain.split(".")
        
        if (parts.size < 3) return 0 // Nu e double-domain dacă are mai puțin de 3 părți
        
        // Verifică fiecare pereche de părți consecutive
        for (i in 0 until parts.size - 1) {
            val possibleLegitimateDomain = "${parts[i]}.${parts[i + 1]}"
            
            // Verifică dacă această combinație este un domeniu legitim cunoscut
            if (legitimateDomains.any { legitimate ->
                    possibleLegitimateDomain == legitimate || 
                    legitimate.endsWith(".$possibleLegitimateDomain")
                }) {
                // Dacă domeniul complet nu se termină cu domeniul legitim, e suspect
                if (!cleanDomain.endsWith(possibleLegitimateDomain) && parts.size > 2) {
                    findings.add("🚨 DOUBLE-DOMAIN detectat: '$domain' imită '$possibleLegitimateDomain' - tipic pentru phishing!")
                    return 45
                }
            }
        }
        
        return 0
    }
    
    /**
     * 15. Detectare Base64 în query
     * Parametri Base64 pot ascunde coduri malitioase
     */
    private fun checkBase64InQuery(urlString: String, findings: MutableList<String>): Int {
        val queryStart = urlString.indexOf('?')
        if (queryStart == -1) return 0
        
        val query = urlString.substring(queryStart + 1)
        val paramPattern = Pattern.compile("([^=&]+)=([^&]*)")
        val matcher = paramPattern.matcher(query)
        
        var riskIncrease = 0
        var base64Count = 0
        
        while (matcher.find()) {
            val paramValue = matcher.group(2) ?: ""
            if (isBase64String(paramValue) && paramValue.length > 10) {
                base64Count++
                if (base64Count == 1) {
                    findings.add("⚠️ Detectat parametru Base64 în query: ${matcher.group(1)}")
                }
            }
        }
        
        if (base64Count > 0) {
            riskIncrease = 20 + (base64Count * 5)
            if (base64Count > 1) {
                findings.add("🚨 $base64Count parametri Base64 detectați - posibil cod ascuns!")
            }
        }
        
        return min(40, riskIncrease)
    }
    
    /**
     * Verifică dacă un string este Base64 valid
     */
    private fun isBase64String(str: String): Boolean {
        if (str.isEmpty() || str.length % 4 != 0) return false
        
        val base64Pattern = Pattern.compile("^[A-Za-z0-9+/]*={0,2}$")
        if (!base64Pattern.matcher(str).matches()) return false
        
        try {
            AndroidBase64.decode(str, AndroidBase64.DEFAULT)
            return true
        } catch (e: Exception) {
            return false
        }
    }
    
    /**
     * 16. Detectare javascript/data-uri în query
     * JavaScript sau data URIs în query sunt foarte suspecte
     */
    private fun checkJavascriptDataUri(urlString: String, findings: MutableList<String>): Int {
        val lowerUrl = urlString.lowercase()
        
        var riskIncrease = 0
        
        // Detectare javascript: protocol
        if (lowerUrl.contains("javascript:") || lowerUrl.contains("javascript%3a")) {
            riskIncrease += 50
            findings.add("🚨🚨🚨 PROTOCOL JAVASCRIPT detectat - cod malitios!")
        }
        
        // Detectare data: URI
        if (lowerUrl.contains("data:text/html") || lowerUrl.contains("data:text/plain") || 
            lowerUrl.contains("data%3atext") || lowerUrl.contains("data%3Atext")) {
            riskIncrease += 45
            findings.add("🚨🚨 DATA URI detectat - posibil cod HTML/JavaScript ascuns!")
        }
        
        // Detectare vbscript: sau file://
        if (lowerUrl.contains("vbscript:") || lowerUrl.contains("file://") || 
            lowerUrl.contains("file%3a//")) {
            riskIncrease += 40
            findings.add("🚨 Protocol periculos detectat (vbscript/file) - foarte suspect!")
        }
        
        return min(50, riskIncrease)
    }
    
    /**
     * 17. Detectare port neobișnuit
     * Porturile standard sunt 80 (HTTP) și 443 (HTTPS)
     */
    private fun checkUnusualPort(url: URL, findings: MutableList<String>): Int {
        val port = url.port
        if (port == -1) return 0 // Port implicit (80 sau 443)
        
        val standardPorts = setOf(80, 443, 8080, 8443)
        if (port !in standardPorts) {
            findings.add("⚠️ Port neobișnuit detectat: $port (standard: 80, 443)")
            
            // Porturi foarte suspecte (ex: 21, 22, 25, etc.)
            val suspiciousPorts = setOf(21, 22, 23, 25, 110, 143, 993, 995, 3306, 5432, 3389)
            if (port in suspiciousPorts) {
                findings.add("🚨 Port suspect ($port) - folosit de servicii care nu ar trebui expuse!")
                return 25
            }
            
            return 15
        }
        
        return 0
    }
    
    /**
     * 18. Detectare exces cifre în domeniu
     * Domeniile legitime au rareori multe cifre consecutive
     */
    private fun checkExcessiveDigitsInDomain(domain: String?, findings: MutableList<String>): Int {
        if (domain == null) return 0
        
        val cleanDomain = domain.removePrefix("www.")
        
        // Verifică cifre consecutive (mai mult de 4)
        val consecutiveDigits = Pattern.compile("\\d{5,}")
        if (consecutiveDigits.matcher(cleanDomain).find()) {
            findings.add("🚨 Domeniu conține multe cifre consecutive - foarte suspect!")
            return 30
        }
        
        // Verifică procentaj de cifre în domeniu
        val digitCount = cleanDomain.count { it.isDigit() }
        val totalChars = cleanDomain.length
        
        if (totalChars > 0) {
            val digitRatio = digitCount.toDouble() / totalChars
            if (digitRatio > 0.5) { // Mai mult de 50% cifre
                findings.add("🚨 Domeniu are prea multe cifre (${(digitRatio * 100).toInt()}%) - probabil fals!")
                return 35
            } else if (digitRatio > 0.3) { // Mai mult de 30%
                findings.add("⚠️ Domeniu are multe cifre (${(digitRatio * 100).toInt()}%) - verifică manual")
                return 15
            }
        }
        
        return 0
    }
    
    /**
     * 19. Expandare URL scurtat (pentru link-uri scurte cunoscute)
     * Încearcă să expandeze link-urile scurte pentru a vedea destinația reală
     */
    private fun checkShortUrlExpansion(urlString: String, domain: String?, findings: MutableList<String>): Int {
        if (domain == null) return 0
        
        val shortUrlServices = mapOf(
            "bit.ly" to "https://bitly.com",
            "tinyurl.com" to "https://tinyurl.com",
            "t.co" to "https://twitter.com",
            "goo.gl" to "https://google.com",
            "ow.ly" to "https://hootsuite.com",
            "is.gd" to "https://is.gd",
            "buff.ly" to "https://buffer.com",
            "short.link" to "https://short.link",
            "rebrand.ly" to "https://rebrandly.com",
            "tiny.cc" to "https://tiny.cc"
        )
        
        val cleanDomain = domain.removePrefix("www.")
        if (shortUrlServices.containsKey(cleanDomain)) {
            findings.add("⚠️ Link scurt detectat ($cleanDomain) - nu se poate verifica destinația finală fără expandare")
            findings.add("💡 Deschide link-ul cu precauție - ar putea conduce la un site de phishing!")
            return 20
        }
        
        return 0
    }
    
    /**
     * 20. Detectare branduri multiple în path
     * Path-uri care conțin mai multe branduri legitime sunt suspecte
     */
    private fun checkMultipleBrandsInPath(path: String, findings: MutableList<String>): Int {
        if (path.isEmpty() || path == "/") return 0
        
        val pathLower = path.lowercase()
        val detectedBrands = mutableListOf<String>()
        
        // Extrage numele brandurilor din lista de domenii legitime
        val brandNames = legitimateDomains.map { it.split(".")[0] }.distinct()
        
        for (brand in brandNames) {
            if (pathLower.contains(brand, ignoreCase = true)) {
                detectedBrands.add(brand)
            }
        }
        
        if (detectedBrands.size > 1) {
            findings.add("🚨 Path conține mai multe branduri (${detectedBrands.joinToString(", ")}) - foarte suspect!")
            return 30
        } else if (detectedBrands.size == 1 && pathLower.contains("verify") || 
                   pathLower.contains("secure") || pathLower.contains("update")) {
            findings.add("⚠️ Path combină brand legitim cu cuvinte cheie suspecte - verifică manual")
            return 15
        }
        
        return 0
    }
}


