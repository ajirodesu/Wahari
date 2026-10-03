package dev.citali.needle.tools.media

import android.provider.MediaStore
import java.net.URLEncoder

/**
 * Stateless helpers for media playback: MediaStore focus mapping and
 * per-app search URLs. App resolution lives in [AppAliasResolver] (names)
 * and [MediaAppDiscovery] (installed players) — nothing here assumes any
 * app is installed.
 */
object AppResolver {

    const val YOUTUBE = "com.google.android.youtube"
    const val YOUTUBE_MUSIC = "com.google.android.apps.youtube.music"
    const val SPOTIFY = "com.spotify.music"

    fun normalize(raw: String?): String = FuzzyMatch.normalize(raw.orEmpty())

    /** MediaStore focus extra for the intent strategy, or null for "any". */
    fun focusFor(mediaType: String): String? = when (normalize(mediaType)) {
        "music" -> MediaStore.Audio.Media.ENTRY_CONTENT_TYPE
        "video" -> MediaStore.Video.Media.CONTENT_TYPE
        else -> null
    }

    fun searchUrl(packageName: String, query: String): String {
        val encoded = runCatching { URLEncoder.encode(query, "UTF-8") }.getOrDefault(query)
        return when (packageName) {
            YOUTUBE_MUSIC -> "https://music.youtube.com/search?q=$encoded"
            SPOTIFY -> "https://open.spotify.com/search/$encoded"
            else -> "https://www.youtube.com/results?search_query=$encoded"
        }
    }
}
