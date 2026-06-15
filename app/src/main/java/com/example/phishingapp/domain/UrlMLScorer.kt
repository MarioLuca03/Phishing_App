package com.example.phishingapp.domain
import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import kotlin.math.min

class UrlMlScorer(context: Context) {

    private val featureImportance: List<Float>

    init {
        val jsonStr = context.assets.open("rf_url_model.json")
            .bufferedReader().use(BufferedReader::readText)

        val json = JSONObject(jsonStr)
        val importances = json.getJSONArray("feature_importance")

        val list = mutableListOf<Float>()
        for (i in 0 until importances.length()) {
            list.add(importances.getDouble(i).toFloat())
        }
        featureImportance = list
    }

    private fun extractFeatures(url: String): FloatArray {
        val lower = url.lowercase()

        return floatArrayOf(
            url.length.toFloat(),
            url.count { it == '-' }.toFloat(),
            url.count { it == '.' }.toFloat(),
            url.count { it == '/' }.toFloat(),
            url.count { it.isDigit() }.toFloat(),
            url.count { it.isLetter() }.toFloat(),
            if (url.startsWith("https")) 1f else 0f,
            if ('@' in url) 1f else 0f,
            if (url.indexOf("//", 7) != -1) 1f else 0f,
            if ('?' in url) 1f else 0f,
            if ('=' in url) 1f else 0f,
            if ("login" in lower) 1f else 0f,
            if ("secure" in lower) 1f else 0f,
            if ("update" in lower) 1f else 0f,
            if ("account" in lower) 1f else 0f,
            if ("bank" in lower) 1f else 0f,
        )
    }

    fun score(url: String): Int {
        val feats = extractFeatures(url)
        var score = 0f

        for (i in feats.indices) {
            score += feats[i] * featureImportance[i]
        }

        val normalized = min(100f, score * 10f)
        return normalized.toInt()
    }
    
    /**
     * Returnează observații despre ce a detectat modelul ML
     */
    fun getObservations(url: String): List<String> {
        val lower = url.lowercase()
        val observations = mutableListOf<String>()
        val feats = extractFeatures(url)
        
        // Feature names pentru observații
        val featureNames = listOf(
            "Lungime URL",
            "Număr cratime (-)",
            "Număr puncte (.)",
            "Număr slash-uri (/)",
            "Număr cifre",
            "Număr litere",
            "Folosește HTTPS",
            "Conține '@'",
            "Double slash",
            "Conține query string",
            "Conține '='",
            "Conține 'login'",
            "Conține 'secure'",
            "Conține 'update'",
            "Conține 'account'",
            "Conține 'bank'"
        )
        
        // Calculează contribuția fiecărui feature
        val contributions = mutableListOf<Pair<String, Float>>()
        var totalContribution = 0f
        
        for (i in feats.indices) {
            val contribution = feats[i] * featureImportance[i]
            if (contribution > 0.1f) { // Doar features cu contribuție semnificativă
                contributions.add(Pair(featureNames[i], contribution))
                totalContribution += contribution
            }
        }
        
        // Sortează după contribuție descrescător
        contributions.sortByDescending { it.second }
        
        // Adaugă observații
        if (contributions.isNotEmpty()) {
            observations.add("Modelul ML a analizat ${featureNames.size} caracteristici URL.")
            observations.add("")
            observations.add("Caracteristici detectate (cele mai importante):")
            
            // Calculează procentele relative la contribuția totală
            contributions.take(5).forEach { (feature, contribution) ->
                val percentage = if (totalContribution > 0f) {
                    ((contribution / totalContribution) * 100f).toInt()
                } else {
                    0
                }
                observations.add("  • $feature: ${percentage}% din total")
            }
            
            observations.add("")
            
            // Observații specifice
            if (feats[0] > 100) { // Lungime URL
                observations.add("⚠️ URL foarte lung (${feats[0].toInt()} caractere) - poate indica obfuscare")
            }
            if (feats[6] == 0f) { // HTTPS
                observations.add("⚠️ Nu folosește HTTPS - conexiune necriptată")
            }
            if (feats[7] == 1f) { // '@'
                observations.add("🚨 Conține caracter '@' - foarte suspect!")
            }
            if (feats[9] == 1f || feats[10] == 1f) { // Query string sau '='
                observations.add("⚠️ Conține parametri query - verifică parametrii suspici")
            }
            if (feats[11] == 1f || feats[13] == 1f || feats[14] == 1f) { // login, update, account
                observations.add("⚠️ Conține cuvinte cheie comune în phishing: login/update/account")
            }
            if (feats[15] == 1f) { // bank
                observations.add("🚨 Conține cuvântul 'bank' - foarte suspect în context phishing!")
            }
        }
        
        return observations
    }
}
