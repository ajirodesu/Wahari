package dev.citali.needle.tools.media.strategies

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import dev.citali.needle.tools.media.FuzzyMatch
import dev.citali.needle.tools.media.MediaAppDiscovery
import dev.citali.needle.tools.media.MediaPlaybackVerifier
import dev.citali.needle.tools.media.PlayRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

/**
 * Strategy 0: the on-device library. Skipped for online requests and without
 * storage permission. Queries MediaStore audio/video by title, artist, album
 * and display name with a fuzzy score threshold, then opens the best match
 * in the candidate player. Below the threshold it declines — an online
 * catalog request must never be answered with a wrong local file.
 */
object LocalLibraryStrategy : MediaStrategy {

    override val id = "local"
    const val TAG = "MediaPlayback"
    const val MIN_SCORE = 0.55

    private data class LocalMatch(val uri: Uri, val mime: String, val title: String)

    fun hasStorageAccess(context: Context): Boolean =
        audioGranted(context) || videoGranted(context)

    private fun audioGranted(context: Context): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    private fun videoGranted(context: Context): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    override suspend fun attempt(
        context: Context,
        request: PlayRequest,
        candidate: MediaAppDiscovery.PlayerCandidate,
    ): AttemptResult {
        if (request.wantsOnline()) {
            return AttemptResult.Declined("online-only request skips the local library")
        }
        if (!hasStorageAccess(context)) {
            Log.d(TAG, "local: storage permission denied, skipping")
            return AttemptResult.Declined("storage permission denied")
        }
        val match = withContext(Dispatchers.IO) { bestLocalMatch(context, request) }
            ?: return AttemptResult.Declined("no local file matches \"${request.query}\"")
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(match.uri, match.mime)
            setPackage(candidate.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val handlers = runCatching {
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrDefault(emptyList())
        if (handlers.isEmpty()) {
            Log.d(TAG, "local: ${candidate.label} cannot open ${match.mime}")
            return AttemptResult.Declined("${candidate.label} cannot open local files")
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            return AttemptResult.Error("Could not open the local file: ${e.message}")
        }
        Log.d(TAG, "local: opened '${match.title}' in ${candidate.label}, verifying")
        val confirmed = withTimeoutOrNull(5_000L) {
            while (true) {
                coroutineContext.ensureActive()
                if (MediaPlaybackVerifier.isPlaying(context, candidate.packageName)) return@withTimeoutOrNull true
                delay(500)
            }
            @Suppress("UNREACHABLE_CODE")
            false
        } == true
        return if (confirmed) {
            val now = MediaPlaybackVerifier.nowPlayingTitle(context, candidate.packageName) ?: match.title
            AttemptResult.Success(now)
        } else {
            AttemptResult.Opened("local file opened, playback unconfirmed")
        }
    }

    private fun bestLocalMatch(context: Context, request: PlayRequest): LocalMatch? {
        val query = request.query.trim()
        if (query.isEmpty()) return null
        val wantsAudio = request.mediaType != "video" && audioGranted(context)
        val wantsVideo = request.mediaType != "music" && videoGranted(context)
        var best: LocalMatch? = null
        var bestScore = 0.0
        if (wantsAudio) {
            scoreCollection(
                context,
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                "audio/*",
                query,
            )?.let { (match, score) ->
                if (score > bestScore) {
                    bestScore = score
                    best = match
                }
            }
        }
        if (wantsVideo) {
            scoreCollection(
                context,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                "video/*",
                query,
            )?.let { (match, score) ->
                if (score > bestScore) {
                    bestScore = score
                    best = match
                }
            }
        }
        return if (bestScore >= MIN_SCORE) best else null.also {
            Log.d(TAG, "local: best score $bestScore for '$query' (threshold $MIN_SCORE)")
        }
    }

    private fun scoreCollection(
        context: Context,
        collection: Uri,
        fallbackMime: String,
        query: String,
    ): Pair<LocalMatch, Double>? {
        val like = "%$query%"
        val selection = "${MediaStore.MediaColumns.TITLE} LIKE ? OR " +
            "${MediaStore.MediaColumns.ARTIST} LIKE ? OR " +
            "${MediaStore.MediaColumns.ALBUM} LIKE ? OR " +
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
        val args = arrayOf(like, like, like, like)
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.TITLE,
            MediaStore.MediaColumns.ARTIST,
            MediaStore.MediaColumns.ALBUM,
            MediaStore.MediaColumns.DISPLAY_NAME,
        )
        var best: Pair<LocalMatch, Double>? = null
        runCatching {
            context.contentResolver.query(collection, projection, selection, args, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.ALBUM)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                var rows = 0
                while (cursor.moveToNext() && rows++ < 200) {
                    val title = cursor.getString(titleCol).orEmpty()
                    val fields = listOf(
                        title,
                        cursor.getString(artistCol).orEmpty(),
                        cursor.getString(albumCol).orEmpty(),
                        cursor.getString(nameCol).orEmpty(),
                    )
                    val score = fields.maxOf { FuzzyMatch.score(query, it) }
                    if (best == null || score > best!!.second) {
                        val uri = ContentUris.withAppendedId(collection, cursor.getLong(idCol))
                        val mime = runCatching {
                            context.contentResolver.getType(uri)
                        }.getOrNull()?.takeIf { it.isNotBlank() } ?: fallbackMime
                        best = LocalMatch(uri, mime, title.ifBlank { "local file" }) to score
                    }
                }
            }
        }
        return best
    }
}
