package dev.citali.needle.tools.media

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log

/**
 * Confirms playback through the platform's active media sessions.
 *
 * [MediaSessionManager.getActiveSessions] only reveals sessions to an
 * enabled [MediaListenerService]: without the user's opt-in every query
 * returns empty and verification degrades to accessibility observation,
 * then to [MediaPlaybackResult.Launched].
 */
class MediaListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("MediaPlayback", "notification listener connected")
    }
}

object MediaPlaybackVerifier {

    const val TAG = "MediaPlayback"

    fun listenerComponent(context: Context): ComponentName =
        ComponentName(context, MediaListenerService::class.java)

    /** Not a public SDK constant: read by its documented settings key. */
    private const val ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners"

    fun listenerEnabled(context: Context): Boolean {
        val flat = runCatching {
            Settings.Secure.getString(context.contentResolver, ENABLED_NOTIFICATION_LISTENERS)
        }.getOrNull().orEmpty()
        if (flat.isBlank()) return false
        return flat.split(':').any { it.contains(context.packageName, ignoreCase = true) }
    }

    fun activeControllers(context: Context): List<MediaController> {
        if (!listenerEnabled(context)) return emptyList()
        val manager = context.getSystemService(MediaSessionManager::class.java)
            ?: return emptyList()
        return runCatching {
            manager.getActiveSessions(listenerComponent(context))
        }.getOrDefault(emptyList())
    }

    fun activePackages(context: Context): Set<String> =
        activeControllers(context).mapNotNullTo(LinkedHashSet()) { controller ->
            runCatching { controller.packageName }.getOrNull()
        }

    fun isPlaying(context: Context, packageName: String): Boolean =
        activeControllers(context).any { controller ->
            runCatching { controller.packageName }.getOrNull()
                .equals(packageName, ignoreCase = true) &&
                controller.playbackState?.state == PlaybackState.STATE_PLAYING
        }

    /** "Title — Artist" from session metadata, or null when unknown. */
    fun nowPlayingTitle(context: Context, packageName: String): String? {
        val controller = activeControllers(context).firstOrNull { c ->
            runCatching { c.packageName }.getOrNull().equals(packageName, ignoreCase = true) &&
                c.playbackState?.state == PlaybackState.STATE_PLAYING
        } ?: return null
        val metadata = controller.metadata ?: return null
        val title = metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
            ?.takeIf { it.isNotBlank() } ?: return null
        val artist = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)
            ?.takeIf { it.isNotBlank() }
        return if (artist != null) "$title — $artist" else title
    }
}
