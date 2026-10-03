package dev.citali.needle.tools.media

import android.provider.MediaStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure parts of app resolution: alias normalization, MediaStore focus
 * mapping and fallback URL building. PackageManager resolution itself
 * needs a device and is exercised on-device.
 */
class AppResolverTest {

    @Test
    fun normalizeTrimsAndLowercases() {
        assertEquals("yt", AppResolver.normalize("  YT "))
        assertEquals("", AppResolver.normalize(null))
    }

    @Test
    fun focusMappingMatchesMediaStore() {
        assertEquals(
            MediaStore.Audio.Media.ENTRY_CONTENT_TYPE,
            AppResolver.focusFor("music"),
        )
        assertEquals(
            MediaStore.Video.Media.CONTENT_TYPE,
            AppResolver.focusFor("VIDEO"),
        )
        assertNull(AppResolver.focusFor("any"))
        assertNull(AppResolver.focusFor("podcast"))
    }

    @Test
    fun searchUrlsArePerApp() {
        val music = AppResolver.searchUrl(AppResolver.YOUTUBE_MUSIC, "Enchanted Taylor Swift")
        assertTrue(music.startsWith("https://music.youtube.com/search?q="))
        val spotify = AppResolver.searchUrl(AppResolver.SPOTIFY, "Enchanted Taylor Swift")
        assertTrue(spotify.startsWith("https://open.spotify.com/search/"))
        val generic = AppResolver.searchUrl("com.example.player", "Enchanted Taylor Swift")
        assertTrue(generic.startsWith("https://www.youtube.com/results?search_query="))
        assertTrue(" " !in generic && " " !in music)
    }
}
