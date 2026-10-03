package dev.citali.needle.tools.media.strategies

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import dev.citali.needle.tools.media.AppResolver
import dev.citali.needle.tools.media.MediaAppDiscovery
import dev.citali.needle.tools.media.MediaPlaybackResult

/**
 * Strategy 5 (terminal): open a search page for the query in the browser.
 * Always reports [MediaPlaybackResult.Fallback] — never success. Kept out
 * of the [MediaStrategy] chain because it does not iterate candidates or
 * confirm anything; the executor invokes it once for the top candidate.
 */
object WebFallbackStrategy {

    const val TAG = "MediaPlayback"

    fun open(
        context: Context,
        candidate: MediaAppDiscovery.PlayerCandidate,
        query: String,
    ): MediaPlaybackResult {
        val url = AppResolver.searchUrl(candidate.packageName, query)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            Log.d(TAG, "web: opened $url")
            MediaPlaybackResult.Fallback(url)
        } catch (e: Exception) {
            Log.d(TAG, "web: no browser for $url: ${e.message}")
            MediaPlaybackResult.Failed("No browser could open the search page.")
        }
    }
}
