package dev.citali.needle.tools.media.strategies

import android.content.Context
import dev.citali.needle.tools.media.MediaAppDiscovery
import dev.citali.needle.tools.media.PlayRequest

/**
 * One playback strategy. New strategies are added by implementing this
 * interface and listing the object in the executor's phase lists — the
 * executor itself does not change.
 */
interface MediaStrategy {

    /** Stable id used in logs and [dev.citali.needle.tools.media.MediaPlaybackResult]. */
    val id: String

    /**
     * One attempt against [candidate]. Never throws: return [AttemptResult.Declined]
     * when the strategy cannot apply, [AttemptResult.Error] with a reason when it
     * tried and failed. Cancellation propagates as usual.
     */
    suspend fun attempt(
        context: Context,
        request: PlayRequest,
        candidate: MediaAppDiscovery.PlayerCandidate,
    ): AttemptResult
}

/**
 * Strategy-level outcome. Only [Success] is confirmed playback; [Opened] is
 * an unconfirmed launch the executor may keep as best-so-far.
 */
sealed interface AttemptResult {
    data class Success(val nowPlaying: String? = null) : AttemptResult
    data class Opened(val note: String? = null) : AttemptResult
    data class Declined(val reason: String) : AttemptResult
    data class Error(val reason: String) : AttemptResult
}
