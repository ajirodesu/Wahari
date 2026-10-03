package dev.citali.needle.tools.media.strategies

import android.content.Context
import android.content.Intent
import android.util.Log
import dev.citali.needle.tools.media.MediaAppDiscovery
import dev.citali.needle.tools.media.MediaPlaybackVerifier
import dev.citali.needle.tools.media.PlayRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

/**
 * Strategy 4: resume what is already there, else plain launch.
 *
 * When the candidate player holds an idle session, `play()` on its
 * controller is the programmatic equivalent of pressing the media play key.
 * Otherwise the app is simply launched and reported as [AttemptResult.Opened].
 * Skipped for local-only requests (resuming could start online content).
 */
object ResumeStrategy : MediaStrategy {

    override val id = "resume"
    const val TAG = "MediaPlayback"

    override suspend fun attempt(
        context: Context,
        request: PlayRequest,
        candidate: MediaAppDiscovery.PlayerCandidate,
    ): AttemptResult {
        if (request.wantsLocal()) {
            return AttemptResult.Declined("local-only request skips resume")
        }
        val controller = MediaPlaybackVerifier.activeControllers(context).firstOrNull { c ->
            runCatching { c.packageName }.getOrNull().equals(candidate.packageName, ignoreCase = true)
        }
        if (controller != null) {
            try {
                controller.transportControls.play()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.d(TAG, "resume: play rejected by ${candidate.label}: ${e.message}")
                return AttemptResult.Error("Resume rejected by ${candidate.label}.")
            }
            val confirmed = withTimeoutOrNull(6_000L) {
                while (true) {
                    coroutineContext.ensureActive()
                    if (MediaPlaybackVerifier.isPlaying(context, candidate.packageName)) {
                        return@withTimeoutOrNull true
                    }
                    delay(500)
                }
                @Suppress("UNREACHABLE_CODE")
                false
            } == true
            if (confirmed) {
                val now = MediaPlaybackVerifier.nowPlayingTitle(context, candidate.packageName)
                Log.d(TAG, "resume: playing in ${candidate.label}")
                return AttemptResult.Success(now)
            }
            return AttemptResult.Error("${candidate.label} did not resume.")
        }
        val launch = context.packageManager.getLaunchIntentForPackage(candidate.packageName)
            ?: return AttemptResult.Declined("${candidate.label} has no launcher screen.")
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(launch)
            Log.d(TAG, "resume: launched ${candidate.label}")
            AttemptResult.Opened("app launched")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AttemptResult.Error("Could not open ${candidate.label}: ${e.message}")
        }
    }
}
