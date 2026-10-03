package dev.citali.needle.tools.media

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.service.media.MediaBrowserService
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.coroutineContext

/**
 * Finds every app that can plausibly play media, from every available
 * signal, merged and de-duplicated by package. Nothing is hardcoded:
 * players are discovered, ranked and driven dynamically.
 *
 * Signals: MEDIA_PLAY_FROM_SEARCH handlers, MediaBrowserService
 * declarations, ACTION_VIEW audio/video handlers, launchable apps whose
 * ApplicationInfo category is audio/video, and currently active media
 * sessions. The merged table is cached for 60 s; [invalidate] drops it on
 * package install/remove.
 */
object MediaAppDiscovery {

    data class PlayerCandidate(
        val packageName: String,
        val label: String,
        val strategies: Set<String>,
        val active: Boolean = false,
    )

    data class Discovery(
        val candidates: List<PlayerCandidate>,
        /** Closest installed labels when a named app is absent. */
        val alternatives: List<String>,
    )

    /** Merged table entry with its user-visible label resolved. */
    data class Labeled(
        val label: String,
        val strategies: Set<String>,
    )

    const val MIN_NAMED_SCORE = 0.5
    private const val TTL_MS = 60_000L
    private const val MAX_CANDIDATES = 4

    @Volatile
    private var cache: Pair<Long, Map<String, MutableSet<String>>>? = null

    fun invalidate() {
        cache = null
    }

    suspend fun discover(context: Context, request: PlayRequest): Discovery =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            coroutineContext.ensureActive()
            val table = cachedPlayers(app)
            val aliases = AppAliasResolver.loadAliases(app)
            val active = MediaPlaybackVerifier.activePackages(app)
            coroutineContext.ensureActive()
            val defaultPkg = defaultHandler(app, request)
            val labeled = table.mapValues { (pkg, strategies) ->
                Labeled(label(app.packageManager, pkg), strategies)
            }
            rank(labeled, request, aliases, active, defaultPkg)
        }

    private fun cachedPlayers(context: Context): Map<String, MutableSet<String>> {
        val now = System.currentTimeMillis()
        cache?.let { (at, table) ->
            if (now - at < TTL_MS) return table
        }
        val fresh = collect(context)
        cache = now to fresh
        return fresh
    }

    private fun collect(context: Context): Map<String, MutableSet<String>> {
        val pm = context.packageManager
        val table = LinkedHashMap<String, MutableSet<String>>()
        fun tag(pkg: String, strategy: String) {
            if (pkg == context.packageName) return
            table.getOrPut(pkg) { LinkedHashSet() }.add(strategy)
        }
        // 1. Media-search intent handlers.
        runCatching {
            pm.queryIntentActivities(
                Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH),
                PackageManager.MATCH_DEFAULT_ONLY,
            )
        }.getOrDefault(emptyList()).forEach { tag(it.activityInfo.packageName, "search-intent") }
        // 2. MediaBrowserService declarations.
        runCatching {
            pm.queryIntentServices(Intent(MediaBrowserService.SERVICE_INTERFACE), 0)
        }.getOrDefault(emptyList()).forEach { tag(it.serviceInfo.packageName, "media-session") }
        // 3. ACTION_VIEW audio/video handlers.
        for (mime in listOf("audio/*", "video/*")) {
            runCatching {
                pm.queryIntentActivities(
                    Intent(Intent.ACTION_VIEW).setType(mime),
                    PackageManager.MATCH_DEFAULT_ONLY,
                )
            }.getOrDefault(emptyList()).forEach { tag(it.activityInfo.packageName, "view") }
        }
        // 4. Launchable apps categorized as audio/video (API 26+).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val launchable = pm.queryIntentActivities(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                    0,
                ).map { it.activityInfo.packageName }.toSet()
                pm.getInstalledApplications(0).filter { info ->
                    (info.category == android.content.pm.ApplicationInfo.CATEGORY_AUDIO ||
                        info.category == android.content.pm.ApplicationInfo.CATEGORY_VIDEO) &&
                        info.packageName in launchable
                }
            }.getOrDefault(emptyList()).forEach { tag(it.packageName, "library") }
        }
        return table
    }

    private fun label(pm: PackageManager, pkg: String): String =
        runCatching {
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info).toString().ifBlank { pkg }
        }.getOrDefault(pkg)

    private fun defaultHandler(context: Context, request: PlayRequest): String? {
        val mime = if (request.mediaType == "video") "video/*" else "audio/*"
        return runCatching {
            context.packageManager.resolveActivity(
                Intent(Intent.ACTION_VIEW).setType(mime),
                PackageManager.MATCH_DEFAULT_ONLY,
            )?.activityInfo?.packageName
        }.getOrNull()
    }

    /**
     * Pure ranking, covered by unit tests. Order: fuzzy match to the named
     * app, then active sessions, then strategy support, then system default.
     * A named app that matches nothing yields no candidates (never a silent
     * substitution) plus the closest installed alternatives.
     */
    fun rank(
        table: Map<String, Labeled>,
        request: PlayRequest,
        aliases: Map<String, String>,
        active: Set<String>,
        defaultPkg: String?,
    ): Discovery {
        val named = AppAliasResolver.canonicalName(request.rawApp, aliases)
        if (named.isNotBlank()) {
            val scored = table.map { (pkg, entry) ->
                val score = maxOf(
                    FuzzyMatch.score(named, entry.label),
                    FuzzyMatch.score(named, pkg),
                    FuzzyMatch.score(named, pkg.substringAfterLast('.')),
                )
                Triple(pkg, entry.label, score)
            }
            val alternatives = scored.sortedByDescending { it.third }
                .take(3).map { it.second }.distinct()
            val matched = scored.filter { it.third >= MIN_NAMED_SCORE }
                .sortedWith(
                    compareByDescending<Triple<String, String, Double>> { it.third }
                        .thenByDescending { it.first in active }
                        .thenByDescending { table[it.first]?.strategies?.size ?: 0 },
                )
                .take(MAX_CANDIDATES)
                .map { (pkg, label, _) ->
                    PlayerCandidate(pkg, label, table[pkg]?.strategies ?: emptySet(), pkg in active)
                }
            return Discovery(matched, alternatives)
        }
        val ranked = table.map { (pkg, entry) ->
            PlayerCandidate(
                packageName = pkg,
                label = entry.label,
                strategies = entry.strategies,
                active = pkg in active,
            )
        }.sortedWith(
            compareByDescending<PlayerCandidate> { it.active }
                .thenByDescending { it.strategies.size }
                .thenBy { if (it.packageName == defaultPkg) 0 else 1 }
                .thenBy { it.label.lowercase() },
        ).take(MAX_CANDIDATES)
        return Discovery(ranked, emptyList())
    }
}
