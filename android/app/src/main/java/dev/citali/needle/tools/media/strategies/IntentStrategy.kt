package dev.citali.needle.tools.media.strategies

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.util.Log
import dev.citali.needle.tools.media.AppResolver
import dev.citali.needle.tools.media.MediaAppDiscovery
import dev.citali.needle.tools.media.PlayRequest

/**
 * Strategy 1: the platform media-search intent, targeted at the candidate.
 *
 * Returns [AttemptResult.Opened] at best: a fired intent proves the app
 * opened, never that playback started.
 */
object IntentStrategy : MediaStrategy {

    override val id = "search-intent"
    const val TAG = "MediaPlayback"

    /**
     * Splits "title by artist" so the matching artist/title extras can be
     * set alongside the raw query. Pure, covered by unit tests.
     */
    fun parseQueryParts(query: String): Pair<String, String?> {
        val by = Regex("\\s+by\\s+", RegexOption.IGNORE_CASE).split(query.trim(), limit = 2)
        if (by.size == 2 && by[0].isNotBlank() && by[1].isNotBlank()) {
            return by[0].trim() to by[1].trim()
        }
        return query.trim() to null
    }

    override suspend fun attempt(
        context: Context,
        request: PlayRequest,
        candidate: MediaAppDiscovery.PlayerCandidate,
    ): AttemptResult {
        val (title, artist) = parseQueryParts(request.query)
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            setPackage(candidate.packageName)
            putExtra(SearchManager.QUERY, request.query)
            putExtra(MediaStore.EXTRA_MEDIA_TITLE, title)
            artist?.let { putExtra(MediaStore.EXTRA_MEDIA_ARTIST, it) }
            AppResolver.focusFor(request.mediaType)?.let { focus ->
                putExtra(MediaStore.EXTRA_MEDIA_FOCUS, focus)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val handlers = runCatching {
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrDefault(emptyList())
        if (handlers.isEmpty()) {
            Log.d(TAG, "search-intent: no media-search handler in ${candidate.packageName}")
            return AttemptResult.Declined("no media-search handler")
        }
        return try {
            context.startActivity(intent)
            Log.d(TAG, "search-intent: launched media search in ${candidate.label}")
            AttemptResult.Opened("media search launched")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.d(TAG, "search-intent: start failed for ${candidate.label}: ${e.message}")
            AttemptResult.Error("Media search failed in ${candidate.label}.")
        }
    }
}
