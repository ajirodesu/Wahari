package dev.citali.needle.tools.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppAliasResolverTest {

    private val json = """
        {"aliases": {"yt": "youtube", " ytm ": "YouTube Music", "spot": "spotify", "": "blank", "blank": ""}}
    """.trimIndent()

    @Test
    fun parsesAndNormalizes() {
        val aliases = AppAliasResolver.parseAliases(json)
        assertEquals("youtube", aliases["yt"])
        assertEquals("YouTube Music", aliases["ytm"])
        assertTrue("blank" !in aliases)
        assertTrue("" !in aliases)
    }

    @Test
    fun malformedJsonYieldsEmpty() {
        assertTrue(AppAliasResolver.parseAliases("nope").isEmpty())
        assertTrue(AppAliasResolver.parseAliases("{}").isEmpty())
    }

    @Test
    fun expandsAliases() {
        val aliases = AppAliasResolver.parseAliases(json)
        assertEquals("youtube", AppAliasResolver.canonicalName(" YT ", aliases))
        assertEquals("YouTube Music", AppAliasResolver.canonicalName("ytm", aliases))
    }

    @Test
    fun passesThroughUnknownNames() {
        val aliases = AppAliasResolver.parseAliases(json)
        assertEquals("VLC", AppAliasResolver.canonicalName("VLC", aliases))
        assertEquals("", AppAliasResolver.canonicalName(null, aliases))
        assertEquals("", AppAliasResolver.canonicalName("  ", aliases))
    }
}
