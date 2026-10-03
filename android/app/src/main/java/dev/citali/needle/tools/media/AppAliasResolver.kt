package dev.citali.needle.tools.media

import android.content.Context
import org.json.JSONObject

/**
 * Expands the model's shorthand ("yt", "ytm", "spot") to a canonical app
 * name before fuzzy matching. The table is JSON data
 * (`assets/app_aliases.json`), never code, so new aliases ship without a
 * rebuild of the logic.
 */
object AppAliasResolver {

    @Volatile
    private var cached: Map<String, String>? = null

    /** Pure parser, covered by unit tests. */
    fun parseAliases(json: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return out
        val table = root.optJSONObject("aliases") ?: return out
        val keys = table.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = table.optString(key).trim()
            if (key.isNotBlank() && value.isNotBlank()) {
                out[FuzzyMatch.normalize(key)] = value
            }
        }
        return out
    }

    fun loadAliases(context: Context): Map<String, String> {
        cached?.let { return it }
        val parsed = runCatching {
            context.assets.open("app_aliases.json").bufferedReader().use { it.readText() }
        }.mapCatching { parseAliases(it) }.getOrDefault(emptyMap())
        cached = parsed
        return parsed
    }

    /** Alias-expanded match string for [rawApp], e.g. "yt" → "youtube". */
    fun canonicalName(rawApp: String?, aliases: Map<String, String>): String {
        val want = FuzzyMatch.normalize(rawApp.orEmpty())
        if (want.isEmpty()) return ""
        return aliases[want] ?: rawApp!!.trim()
    }
}
