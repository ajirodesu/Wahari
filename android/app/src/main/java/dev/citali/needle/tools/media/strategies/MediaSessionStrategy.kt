package dev.citali.needle.tools.media.strategies

import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.SystemClock
import android.service.media.MediaBrowserService
import android.util.Log
import dev.citali.needle.tools.media.MediaAppDiscovery
import dev.citali.needle.tools.media.MediaPlaybackVerifier
import dev.citali.needle.tools.media.PlayRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Strategy 2: drive the target app's MediaBrowserService directly.
 *
 * Connects, calls `playFromSearch`, then polls the playback state: only
 * [PlaybackState.STATE_PLAYING] becomes [AttemptResult.Success].
 * Rejected connections, timeouts and errors decline so the chain can
 * continue with the accessibility fallback. Uses framework APIs only (21+),
 * no extra dependencies.
 */
object MediaSessionStrategy : MediaStrategy {

    override val id = "media-session"
    const val TAG = "MediaPlayback"
    private const val CONNECT_TIMEOUT_MS = 15_000L
    private const val PLAY_TIMEOUT_MS = 8_000L

    private fun extras(request: PlayRequest): Bundle = Bundle().apply {
        putString(SearchManager.QUERY, request.query)
        val (title, artist) = IntentStrategy.parseQueryParts(request.query)
        putString(android.provider.MediaStore.EXTRA_MEDIA_TITLE, title)
        artist?.let { putString(android.provider.MediaStore.EXTRA_MEDIA_ARTIST, it) }
    }

    override suspend fun attempt(
        context: Context,
        request: PlayRequest,
        candidate: MediaAppDiscovery.PlayerCandidate,
    ): AttemptResult {
        val service = runCatching {
            context.packageManager.queryIntentServices(
                Intent(MediaBrowserService.SERVICE_INTERFACE),
                0,
            )
        }.getOrDefault(emptyList())
            .firstOrNull { it.serviceInfo.packageName == candidate.packageName }
            ?.serviceInfo
            ?: run {
                Log.d(TAG, "media-session: no browser service in ${candidate.packageName}")
                return AttemptResult.Declined("no browser service")
            }
        val appCtx = context.applicationContext
        var browser: MediaBrowser? = null
        try {
            return withTimeoutOrNull(CONNECT_TIMEOUT_MS) {val connected = CompletableDeferred<MediaBrowser>()
                val callback = object : MediaBrowser.ConnectionCallback() {
                    override fun onConnected() {
                        browser?.let { if (!connected.isCompleted) connected.complete(it) }
                    }

                    override fun onConnectionFailed() {
                        if (!connected.isCompleted) {
                            connected.completeExceptionally(SecurityException("connection rejected"))
                        }
                    }

                    override fun onConnectionSuspended() {
                        if (!connected.isCompleted) {
                            connected.completeExceptionally(IllegalStateException("connection suspended"))
                        }
                    }
                }
                // The framework browser posts connection callbacks to its
                // creating thread's looper, and tool coroutines run on a
                // looper-less worker: create and connect on Main, wait here.
                browser = withContext(Dispatchers.Main) {
                    MediaBrowser(
                        appCtx,
                        ComponentName(candidate.packageName, service.name),
                        callback,
                        null,
                    ).also { it.connect() }
                }
                val alive = try {
                    connected.await()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.d(TAG, "media-session: ${candidate.label} refused the connection")
                    return@withTimeoutOrNull AttemptResult.Declined("connection refused")
                }
                try {
                    val token = alive.sessionToken
                    val controller = try {
                        MediaController(appCtx, token)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.d(TAG, "media-session: no session token in ${candidate.label}")
                        return@withTimeoutOrNull AttemptResult.Declined("no session token")
                    }
                    try {
                        controller.transportControls.playFromSearch(request.query, extras(request))
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.d(TAG, "media-session: play rejected by ${candidate.label}: ${e.message}")
                        return@withTimeoutOrNull AttemptResult.Declined("play rejected")
                    }
                    val deadline = SystemClock.uptimeMillis() + PLAY_TIMEOUT_MS
                    while (SystemClock.uptimeMillis() < deadline) {
                        currentCoroutineContext().ensureActive()
                        if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
                            Log.d(TAG, "media-session: playing in ${candidate.label}")
                            val now = MediaPlaybackVerifier.nowPlayingTitle(context, candidate.packageName)
                            return@withTimeoutOrNull AttemptResult.Success(now)
                        }
                        delay(500)
                    }
                    Log.d(TAG, "media-session: ${candidate.label} never reached playing state")
                    AttemptResult.Opened("session connected, playback unconfirmed")
                } finally {
                    runCatching { alive.disconnect() }
                }
            } ?: AttemptResult.Declined("connection timed out")
        } finally {
            runCatching { browser?.disconnect() }
        }
    }
}
