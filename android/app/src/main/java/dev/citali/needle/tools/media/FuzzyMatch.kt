package dev.citali.needle.tools.media

import java.util.Locale
import kotlin.math.min

/**
 * Single reusable fuzzy matcher for app names, titles and artists.
 *
 * Scores are 0..1: 1.0 exact, ~0.9 prefix, ~0.7 word-boundary/substring,
 * token overlap scaled to 0.5, near-typos (edit distance ≤ 2) at 0.55.
 * Pure functions, covered by unit tests.
 */
object FuzzyMatch {

    fun normalize(text: String): String =
        text.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun score(rawQuery: String, rawTarget: String): Double {
        val q = normalize(rawQuery)
        val t = normalize(rawTarget)
        if (q.isEmpty() || t.isEmpty()) return 0.0
        if (q == t) return 1.0
        if (t.startsWith(q)) return 0.9
        if (q.startsWith(t)) return 0.85
        if (Regex("\\b" + Regex.escape(q)).containsMatchIn(t)) return 0.7
        if (q in t) return 0.6
        val qTokens = q.split(' ').filter { it.length > 1 }.toSet()
        val tTokens = t.split(' ').toSet()
        if (qTokens.isNotEmpty()) {
            val hit = qTokens.count { it in tTokens }
            if (hit > 0) return 0.5 * hit / qTokens.size
        }
        if (q.length >= 4 && t.length >= 4 && editDistance(q, t) <= 2) return 0.55
        return 0.0
    }

    /** Best of [candidates] for [query], or null when all score zero. */
    fun best(query: String, candidates: List<String>): Pair<String, Double>? =
        candidates.map { it to score(query, it) }
            .filter { it.second > 0.0 }
            .maxByOrNull { it.second }

    internal fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                curr[j] = min(
                    min(prev[j] + 1, curr[j - 1] + 1),
                    prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1,
                )
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[b.length]
    }
}
