package dev.citali.needle.tools.media.strategies

import android.content.Context
import android.media.AudioManager
import android.util.Log
import dev.citali.needle.pilot.accessibility.NeedleAccessibilityService
import dev.citali.needle.pilot.accessibility.UiNode
import dev.citali.needle.pilot.accessibility.UiSnapshot
import dev.citali.needle.pilot.agent.Action
import dev.citali.needle.tools.media.MediaAppDiscovery
import dev.citali.needle.tools.media.PlayRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.Locale
import kotlin.coroutines.coroutineContext

/**
 * Strategy 3: drive the app's own search UI through the existing
 * accessibility service (the one the user already enabled for automation —
 * no second service to toggle).
 *
 * Launch → search → type → submit → tap first playable result → verify the
 * player UI. Enforces a step budget and an overall timeout, declines when
 * the service is off, never touches password fields, and aborts if the
 * foreground package changes unexpectedly mid-run.
 *
 * Selector profiles live in `assets/media_app_profiles.json` (data, not
 * code); apps without a profile use the generic `"*"` heuristic profile.
 */
object AccessibilityStrategy : MediaStrategy {

    override val id = "accessibility"
    const val TAG = "MediaPlayback"
    private const val OVERALL_TIMEOUT_MS = 60_000L
    private const val MAX_STEPS = 14

    data class MediaProfile(
        val packageName: String,
        val searchTerms: List<String>,
        val searchResourceIds: List<String>,
        val resultResourceIds: List<String>,
        val excludeResultTerms: List<String>,
        val playerSignals: List<String>,
    )

    /** Pure parser so the profile data is covered by JVM tests. */
    fun parseProfiles(json: String): List<MediaProfile> {
        val out = mutableListOf<MediaProfile>()
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return out
        val array = root.optJSONArray("profiles") ?: return out
        for (i in 0 until array.length()) {
            val entry = array.optJSONObject(i) ?: continue
            val pkg = entry.optString("package").trim()
            if (pkg.isEmpty()) continue
            out += MediaProfile(
                packageName = pkg,
                searchTerms = entry.optJSONArray("searchTerms")?.strings() ?: listOf("search"),
                searchResourceIds = entry.optJSONArray("searchResourceIds")?.strings() ?: emptyList(),
                resultResourceIds = entry.optJSONArray("resultResourceIds")?.strings() ?: emptyList(),
                excludeResultTerms = entry.optJSONArray("excludeResultTerms")?.strings() ?: emptyList(),
                playerSignals = entry.optJSONArray("playerSignals")?.strings() ?: listOf("pause"),
            )
        }
        return out
    }

    private fun org.json.JSONArray.strings(): List<String> =
        (0 until length()).mapNotNull { optString(it).trim().takeIf { s -> s.isNotEmpty() } }

    @Volatile
    private var cached: List<MediaProfile>? = null

    fun loadProfiles(context: Context): List<MediaProfile> {
        cached?.let { return it }
        val parsed = runCatching {
            context.assets.open("media_app_profiles.json").bufferedReader().use { it.readText() }
        }.mapCatching { parseProfiles(it) }.getOrDefault(emptyList())
        cached = parsed
        return parsed
    }

    fun profileFor(profiles: List<MediaProfile>, packageName: String): MediaProfile =
        profiles.firstOrNull { it.packageName.equals(packageName, ignoreCase = true) }
            ?: profiles.firstOrNull { it.packageName == "*" }
            ?: MediaProfile(packageName, listOf("search"), emptyList(), emptyList(), emptyList(), listOf("pause"))

    override suspend fun attempt(
        context: Context,
        request: PlayRequest,
        candidate: MediaAppDiscovery.PlayerCandidate,
    ): AttemptResult {
        if (NeedleAccessibilityService.instance == null) {
            Log.d(TAG, "accessibility: service off, declining")
            return AttemptResult.Declined("accessibility service off")
        }
        return withTimeoutOrNull(OVERALL_TIMEOUT_MS) {
            runSteps(context.applicationContext, candidate, request.query)
        } ?: AttemptResult.Error("Timed out driving ${candidate.label} — the search took too long.")
    }

    private suspend fun runSteps(
        context: Context,
        candidate: MediaAppDiscovery.PlayerCandidate,
        query: String,
    ): AttemptResult {
        val service = NeedleAccessibilityService.instance
            ?: return AttemptResult.Declined("accessibility service off")
        val pkg = candidate.packageName
        val profile = profileFor(loadProfiles(context), pkg)
        var steps = 0
        suspend fun step(action: Action): Boolean {
            if (++steps > MAX_STEPS) return false
            coroutineContext.ensureActive()
            return service.execute(action)
        }
        suspend fun snap(): UiSnapshot? {
            coroutineContext.ensureActive()
            return runCatching { service.captureSnapshot(true) }.getOrNull()
        }
        // Aborts when the foreground leaves the target app mid-run (a dialog,
        // a wrong tap). One re-read tolerates transitions.
        suspend fun checkForeground(retries: Int = 1): Boolean {
            repeat(retries + 1) { attempt ->
                val current = snap()?.packageName
                if (current.equals(pkg, ignoreCase = true)) return true
                if (attempt < retries) delay(800)
            }
            return false
        }

        // 1. Launch and wait for the foreground.
        Log.d(TAG, "accessibility: launching ${candidate.label}")
        if (!step(Action.OpenApp(pkg, candidate.label))) {
            return AttemptResult.Error("${candidate.label} could not be opened.")
        }
        if (!awaitPackage(::snap, pkg, 8_000L)) {
            return AttemptResult.Error("${candidate.label} did not come to the foreground.")
        }

        // 2. Find the search entry: resource-id first, then an editable field
        //    already on screen, then a matching control. Password fields are
        //    never touched (editableNodes() already excludes them; re-checked).
        var snapshot = snap() ?: return AttemptResult.Error("Could not read the screen.")
        var field = findSearchField(snapshot, profile)
        if (field == null) {
            val entry = profile.searchTerms.firstNotNullOfOrNull { term ->
                snapshot.firstByTextOrDescContaining(term, clickableOnly = true)
            } ?: return AttemptResult.Error("No search field found in ${candidate.label}.")
            if (!step(Action.Tap("#${entry.id}", "Open search"))) {
                return AttemptResult.Error("Could not open search in ${candidate.label}.")
            }
            delay(1_200)
            if (!checkForeground()) {
                return AttemptResult.Error("Left ${candidate.label} unexpectedly.")
            }
            snapshot = snap() ?: return AttemptResult.Error("Could not read the screen.")
            field = findSearchField(snapshot, profile)
                ?: return AttemptResult.Error("No search field found in ${candidate.label}.")
        }

        // 3. Type the query and submit.
        if (!step(Action.Type("#${field.id}", query))) {
            return AttemptResult.Error("Could not type into ${candidate.label} search.")
        }
        delay(800)
        step(Action.Key(Action.KeyAction.ENTER))
        delay(2_500)
        if (!checkForeground()) {
            return AttemptResult.Error("Left ${candidate.label} unexpectedly.")
        }

        // 4. Tap the first playable result.
        snapshot = snap() ?: return AttemptResult.Error("Could not read the results.")
        val pick = pickResult(snapshot, profile, query)
            ?: return AttemptResult.Error("No playable result for \"$query\" in ${candidate.label}.")
        Log.d(TAG, "accessibility: tapping result '${pick.text?.take(60)}'")
        step(Action.Tap("#${pick.id}", "Play result"))

        // 5. Verify: foreground package plus a player signal (pause control)
        //    or system-wide music activity.
        val audio = context.getSystemService(AudioManager::class.java)
        val deadline = System.currentTimeMillis() + 8_000L
        while (System.currentTimeMillis() < deadline) {
            coroutineContext.ensureActive()
            val current = snap()
            if (current?.packageName.equals(pkg, ignoreCase = true)) {
                val pauseShown = current != null && hasPlayerSignal(current, profile)
                val musicActive = runCatching { audio?.isMusicActive == true }.getOrDefault(false)
                if (pauseShown || musicActive) {
                    Log.d(TAG, "accessibility: playing in ${candidate.label}")
                    return AttemptResult.Success(null)
                }
            } else {
                return AttemptResult.Error("Left ${candidate.label} unexpectedly.")
            }
            delay(600)
        }
        return AttemptResult.Opened("search completed, playback unconfirmed")
    }

    private suspend fun awaitPackage(
        snap: suspend () -> UiSnapshot?,
        pkg: String,
        timeoutMs: Long,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            coroutineContext.ensureActive()
            if (snap()?.packageName.equals(pkg, ignoreCase = true)) return true
            delay(500)
        }
        return snap()?.packageName.equals(pkg, ignoreCase = true)
    }

    /** Search box by resource-id, else any non-password editable field. */
    internal fun findSearchField(snapshot: UiSnapshot, profile: MediaProfile): UiNode? {
        if (profile.searchResourceIds.isNotEmpty()) {
            snapshot.root?.flatten()?.firstOrNull { node ->
                node.editable && !node.password &&
                    profile.searchResourceIds.any { wanted ->
                        node.resourceId?.substringAfterLast('/')?.equals(wanted, ignoreCase = true) == true
                    }
            }?.let { return it }
        }
        return snapshot.editableNodes().firstOrNull()
    }

    /** First clickable titled node that is not chrome, preferring duration-bearing rows. */
    internal fun pickResult(snapshot: UiSnapshot, profile: MediaProfile, query: String): UiNode? {
        val words = query.lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.length > 2 }
        val base = snapshot.clickableNodes().filter { node ->
            val text = node.text?.trim().orEmpty()
            if (text.length < 3) return@filter false
            val hay = (text + " " + node.contentDescription.orEmpty()).lowercase(Locale.ROOT)
            if (profile.excludeResultTerms.any { it.lowercase(Locale.ROOT) in hay }) return@filter false
            true
        }
        if (base.isEmpty()) return null
        // Resource-id match is a preference, not a gate: id schemes vary by
        // app version, so an empty preferred set falls back to every candidate.
        val preferred = if (profile.resultResourceIds.isEmpty()) {
            base
        } else {
            base.filter { node ->
                val resId = node.resourceId?.substringAfterLast('/')?.lowercase(Locale.ROOT).orEmpty()
                profile.resultResourceIds.any { it.lowercase(Locale.ROOT) in resId }
            }.ifEmpty { base }
        }
        // Prefer rows that look like media: a duration ("3:45") plus query words.
        val duration = Regex("\\d+:\\d+")
        preferred.firstOrNull { node ->
            val hay = (node.text.orEmpty() + " " + node.contentDescription.orEmpty())
            duration.containsMatchIn(hay) &&
                words.any { it in hay.lowercase(Locale.ROOT) }
        }?.let { return it }
        return preferred.firstOrNull { node ->
            val hay = (node.text.orEmpty() + " " + node.contentDescription.orEmpty()).lowercase(Locale.ROOT)
            words.any { it in hay }
        } ?: preferred.first()
    }

    private fun hasPlayerSignal(snapshot: UiSnapshot, profile: MediaProfile): Boolean {
        val signals = profile.playerSignals.map { it.lowercase(Locale.ROOT) }
        return snapshot.root?.flatten()?.any { node ->
            val hay = (node.text.orEmpty() + " " + node.contentDescription.orEmpty()).lowercase(Locale.ROOT)
            signals.any { it in hay }
        } == true
    }
}
