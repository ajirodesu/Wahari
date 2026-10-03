package dev.citali.needle.tools.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ranking is pure: active sessions first, then strategy support, then the
 * system default. A named-but-absent app yields no candidates (never a
 * silent substitution) plus the closest installed alternatives.
 */
class DiscoveryRankTest {

    private val table = mapOf(
        "com.google.android.youtube" to MediaAppDiscovery.Labeled(
            "YouTube", setOf("search-intent", "media-session", "view"),
        ),
        "com.spotify.music" to MediaAppDiscovery.Labeled(
            "Spotify", setOf("search-intent", "media-session"),
        ),
        "org.videolan.vlc" to MediaAppDiscovery.Labeled(
            "VLC", setOf("view", "library"),
        ),
    )
    private val aliases = mapOf("yt" to "youtube", "spot" to "spotify")

    private fun request(app: String? = null) =
        PlayRequest(query = "Enchanted", rawApp = app, mediaType = "any", source = "any")

    @Test
    fun namedAppMatchesByAlias() {
        val found = MediaAppDiscovery.rank(table, request("yt"), aliases, emptySet(), null)
        assertEquals("com.google.android.youtube", found.candidates.first().packageName)
    }

    @Test
    fun namedAppMatchesByLabelTypo() {
        val found = MediaAppDiscovery.rank(table, request("youtub"), aliases, emptySet(), null)
        assertEquals("com.google.android.youtube", found.candidates.first().packageName)
    }

    @Test
    fun absentAppYieldsAlternativesNotSubstitution() {
        val found = MediaAppDiscovery.rank(table, request("NopeApp"), aliases, emptySet(), null)
        assertTrue(found.candidates.isEmpty())
        assertTrue(found.alternatives.isNotEmpty())
    }

    @Test
    fun activeFirstThenStrategySupport() {
        val found = MediaAppDiscovery.rank(
            table, request(), aliases,
            active = setOf("org.videolan.vlc"),
            defaultPkg = "com.google.android.youtube",
        )
        assertEquals("org.videolan.vlc", found.candidates.first().packageName)
        // Among the inactive, YouTube (3 strategies) outranks Spotify (2).
        assertEquals("com.google.android.youtube", found.candidates[1].packageName)
    }

    @Test
    fun defaultBreaksStrategyTies() {
        val tied = mapOf(
            "com.a.one" to MediaAppDiscovery.Labeled("A One", setOf("view")),
            "com.b.two" to MediaAppDiscovery.Labeled("B Two", setOf("view")),
        )
        val found = MediaAppDiscovery.rank(tied, request(), aliases, emptySet(), "com.b.two")
        assertEquals(
            listOf("com.b.two", "com.a.one"),
            found.candidates.map { it.packageName },
        )
    }
}
