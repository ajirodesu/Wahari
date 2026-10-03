package dev.citali.needle.tools.media

/**
 * Outcome of a play_media request.
 *
 * Only [Playing] means playback was verified (active media session or
 * observed player UI). [Launched] means the app opened but playback is
 * unconfirmed — it must never be phrased as success to the user.
 */
sealed interface MediaPlaybackResult {

    /** Playback confirmed in [app] via [strategy]; [nowPlaying] when known. */
    data class Playing(
        val app: String,
        val strategy: String,
        val nowPlaying: String? = null,
    ) : MediaPlaybackResult

    /** [app] opened via [strategy] but playback could not be confirmed. */
    data class Launched(
        val app: String,
        val strategy: String,
    ) : MediaPlaybackResult

    /** No playback: a browser search page was opened instead. */
    data class Fallback(val url: String) : MediaPlaybackResult

    /**
     * Nothing happened, with a human-readable [reason] and the closest
     * installed [alternatives] so the model can ask the user.
     */
    data class Failed(
        val reason: String,
        val alternatives: List<String> = emptyList(),
    ) : MediaPlaybackResult

    /**
     * Short plain-text string for the model: one or two lines, no JSON
     * envelope, so the reply stays small enough for the on-device context.
     */
    fun toModelString(): String = when (this) {
        is Playing -> buildString {
            append("Playing: ")
            nowPlaying?.takeIf { it.isNotBlank() }?.let { append("'$it' in ") }
            append("$app via $strategy.")
        }
        is Launched -> "$app is open (via $strategy) but playback is unconfirmed. " +
            "Tell the user it is open, not playing."
        is Fallback -> "Could not start playback. Opened a search page instead: $url"
        is Failed -> buildString {
            append("Could not play anything: $reason")
            if (alternatives.isNotEmpty()) {
                append(" Installed players: ${alternatives.take(3).joinToString(", ")}.")
            }
        }
    }
}
