package br.com.centralmidia.android.core

import java.text.Normalizer
import java.util.Locale

object Matching {
    private val stopWords = setOf(
        "de", "do", "da", "dos", "das", "e", "em", "no", "na", "nos", "nas",
        "a", "o", "as", "os",
    )

    fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        val withoutMarks = decomposed.replace(Regex("\\p{Mn}+"), "")
        return withoutMarks
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    private fun compact(value: String): String =
        normalize(value).replace(" ", "")

    private fun publisherHostKey(value: String): String {
        val cleaned = value.trim().lowercase(Locale.ROOT)
        val base = if ("." in cleaned) cleaned.substringBefore(".") else cleaned
        return compact(base)
    }

    fun subjectMatches(text: String, subject: String): Boolean {
        val haystack = normalize(text)
        val wanted = normalize(subject)
        if (wanted.isBlank()) return true
        if (" $wanted " in " $haystack ") return true

        val hayTokens = haystack.split(" ").filter { it.isNotBlank() }.toSet()
        val wantedTokens = wanted.split(" ").filter { it.isNotBlank() }
        if (wantedTokens.size == 1) return wantedTokens.first() in hayTokens

        val meaningful = wantedTokens.filter { it.length >= 3 && it !in stopWords }
        return meaningful.isNotEmpty() && meaningful.all { it in hayTokens }
    }

    fun demandVehicleMatches(actualSource: String, vehicle: String): Boolean {
        if (vehicle.isBlank()) return true
        val actual = normalize(actualSource)
        val actualKey = compact(actualSource)
        val hostKey = publisherHostKey(actualSource)
        val wanted = normalize(vehicle)
        val wantedKey = compact(vehicle)

        if (actual == wanted || actualKey == wantedKey || hostKey == wantedKey) return true

        val tokens = wanted.split(" ").filter { it.length >= 2 && it !in stopWords }
        return tokens.size >= 2 &&
            wantedKey.length >= 5 &&
            (actualKey.startsWith(wantedKey) || hostKey.startsWith(wantedKey))
    }

    fun sourceMatchesStrict(actualSource: String, selected: NewsSource): Boolean {
        val actualNormalized = normalize(actualSource)
        val actualKey = compact(actualSource)
        val hostKey = publisherHostKey(actualSource)

        for (candidate in listOf(selected.name) + selected.aliases) {
            val candidateNormalized = normalize(candidate)
            val candidateKey = compact(candidate)
            if (candidateNormalized.isBlank() || candidateKey.isBlank()) continue

            if (
                actualNormalized == candidateNormalized ||
                actualKey == candidateKey ||
                hostKey == candidateKey
            ) {
                return true
            }

            val tokens = candidateNormalized
                .split(" ")
                .filter { it.length >= 2 && it !in stopWords }

            if (
                tokens.size >= 2 &&
                candidateKey.length >= 6 &&
                (actualKey.startsWith(candidateKey) || hostKey.startsWith(candidateKey))
            ) {
                return true
            }
        }
        return false
    }
}
