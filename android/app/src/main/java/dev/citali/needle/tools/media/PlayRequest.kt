package dev.citali.needle.tools.media

/**
 * The model's single tool call, decoded by the executor.
 */
data class PlayRequest(
    val query: String,
    /** App name as the model said it, or null when it named none. */
    val rawApp: String?,
    /** "music", "video" or "any". */
    val mediaType: String = "any",
    /** "online", "local" or "any". */
    val source: String = "any",
) {
    fun wantsLocal(): Boolean = source == "local"
    fun wantsOnline(): Boolean = source == "online"
}
