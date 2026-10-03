package dev.citali.needle.tools.media

import android.content.Context
import android.util.Log
import dev.citali.needle.tools.media.strategies.AccessibilityStrategy
import dev.citali.needle.tools.media.strategies.AttemptResult
import dev.citali.needle.tools.media.strategies.IntentStrategy
import dev.citali.needle.tools.media.strategies.LocalLibraryStrategy
import dev.citali.needle.tools.media.strategies.MediaSessionStrategy
import dev.citali.needle.tools.media.strategies.MediaStrategy
import dev.citali.needle.tools.media.strategies.ResumeStrategy
import dev.citali.needle.tools.media.strategies.WebFallbackStrategy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Runs a play_media request through the ordered strategy chain and stops at
 * the first CONFIRMED success.
 *
 * Phase 1 tries every ranked candidate across strategies 0–3 (local,
 * search-intent, media-session, accessibility) inside a bounded attempt
 * budget. Phase 2 is resume-or-launch, then the web fallback. A local-only
 * request never leaves strategy 0: online playback must not answer it.
 *
 * Runs on the caller's coroutine (tool handlers already run off the main
 * thread) with structured concurrency throughout, and is cancellable at
 * every strategy boundary.
 */
object MediaPlaybackExecutor {

    const val TAG = "MediaPlayback"

    /** Phase 1: per-candidate playback strategies, in order. */
    private val phase1: List<MediaStrategy> = listOf(
        LocalLibraryStrategy,
        IntentStrategy,
        MediaSessionStrategy,
        AccessibilityStrategy,
    )

    /** Maximum strategy attempts across all candidates (phase 1). */
    private const val ATTEMPT_BUDGET = 10

    /** Maximum candidates taken from discovery ranking. */
    private const val CANDIDATE_CAP = 3

    suspend fun play(context: Context, request: PlayRequest): MediaPlaybackResult {
        val query = request.query.trim()
        if (query.isEmpty()) return MediaPlaybackResult.Failed("There is nothing to play.")
        val discovery = MediaAppDiscovery.discover(context, request.copy(query = query))
        if (discovery.candidates.isEmpty()) {
            val named = AppAliasResolver.canonicalName(
                request.rawApp,
                AppAliasResolver.loadAliases(context),
            ).ifBlank { null }
            return MediaPlaybackResult.Failed(
                reason = if (named != null) {
                    "\"${request.rawApp?.trim()}\" is not installed on this device."
                } else {
                    "No media player is installed on this device."
                },
                alternatives = discovery.alternatives,
            )
        }
        Log.d(TAG, "play: '$query' type=${request.mediaType} source=${request.source} " +
            "candidates=${discovery.candidates.map { it.packageName }}")

        var attempts = 0
        var launched: MediaPlaybackResult.Launched? = null
        fun keepLaunched(app: String, strategy: String) {
            if (launched == null) launched = MediaPlaybackResult.Launched(app, strategy)
        }

        suspend fun attempt(
            strategy: MediaStrategy,
            candidate: MediaAppDiscovery.PlayerCandidate,
        ): AttemptResult {
            return try {
                val started = android.os.SystemClock.uptimeMillis()
                val outcome = strategy.attempt(context, request.copy(query = query), candidate)
                val took = android.os.SystemClock.uptimeMillis() - started
                Log.d(TAG, "${strategy.id} on ${candidate.packageName}: $outcome (${took}ms)")
                outcome
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "${strategy.id} on ${candidate.packageName} crashed: ${e.message}")
                AttemptResult.Declined("strategy crashed")
            }
        }

        // Phase 1: candidates in ranked order across strategies 0–3.
        for (candidate in discovery.candidates.take(CANDIDATE_CAP)) {
            for (strategy in phase1) {
                if (attempts >= ATTEMPT_BUDGET) break
                if (request.wantsLocal() && strategy.id != LocalLibraryStrategy.id) break
                coroutineContext.ensureActive()
                attempts++
                when (val outcome = attempt(strategy, candidate)) {
                    is AttemptResult.Success -> {
                        val now = outcome.nowPlaying
                            ?: MediaPlaybackVerifier.nowPlayingTitle(context, candidate.packageName)
                        return MediaPlaybackResult.Playing(candidate.label, strategy.id, now)
                    }
                    is AttemptResult.Opened -> keepLaunched(candidate.label, strategy.id)
                    is AttemptResult.Declined -> Log.d(TAG, "${strategy.id} declined: ${outcome.reason}")
                    is AttemptResult.Error -> Log.d(TAG, "${strategy.id} failed: ${outcome.reason}")
                }
            }
            if (request.wantsLocal()) break
        }
        if (request.wantsLocal()) {
            return launched
                ?: MediaPlaybackResult.Failed("No local file matches \"$query\".")
        }

        // Phase 2 (strategy 4): resume an idle session, else plain launch.
        coroutineContext.ensureActive()
        for (candidate in discovery.candidates.take(CANDIDATE_CAP)) {
            when (val outcome = attempt(ResumeStrategy, candidate)) {
                is AttemptResult.Success -> {
                    val now = outcome.nowPlaying
                        ?: MediaPlaybackVerifier.nowPlayingTitle(context, candidate.packageName)
                    return MediaPlaybackResult.Playing(candidate.label, ResumeStrategy.id, now)
                }
                is AttemptResult.Opened -> keepLaunched(candidate.label, ResumeStrategy.id)
                is AttemptResult.Declined -> Unit
                is AttemptResult.Error -> Log.d(TAG, "resume failed: ${outcome.reason}")
            }
        }

        // Phase 3 (strategy 5): web fallback for the top candidate.
        coroutineContext.ensureActive()
        val top = discovery.candidates.first()
        val web = WebFallbackStrategy.open(context, top, query)
        if (web is MediaPlaybackResult.Fallback) return web
        return launched
            ?: MediaPlaybackResult.Failed("None of the playback strategies worked for ${top.label}.")
    }
}
