package dev.citali.needle.tools.media

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The model acts on these strings: success must only ever be claimed for
 * verified playback, and an opened-but-unconfirmed app must say so plainly.
 */
class MediaPlaybackResultTest {

    @Test
    fun playingNamesTrackAppAndStrategy() {
        val text = MediaPlaybackResult.Playing("YouTube Music", "media-session", "Enchanted — Taylor Swift")
            .toModelString()
        assertTrue(text.startsWith("Playing:"))
        assertTrue(text.contains("Enchanted") && text.contains("YouTube Music") && text.contains("media-session"))
    }

    @Test
    fun playingWithoutTitleStillReads() {
        val text = MediaPlaybackResult.Playing("VLC", "local").toModelString()
        assertTrue(text.startsWith("Playing:") && text.contains("VLC"))
    }

    @Test
    fun launchedNeverClaimsPlaying() {
        val text = MediaPlaybackResult.Launched("YouTube", "search-intent").toModelString()
        assertTrue(text.contains("unconfirmed"))
        assertTrue(!text.contains("Playing"))
    }

    @Test
    fun fallbackCarriesTheUrl() {
        val url = "https://www.youtube.com/results?search_query=x"
        val text = MediaPlaybackResult.Fallback(url).toModelString()
        assertTrue(text.contains(url) && text.contains("not start playback"))
    }

    @Test
    fun failedCarriesReasonAndAlternatives() {
        val text = MediaPlaybackResult.Failed(
            "\"Nope\" is not installed on this device.",
            listOf("YouTube", "VLC"),
        ).toModelString()
        assertTrue(text.contains("Nope") && text.contains("YouTube") && text.contains("VLC"))
    }
}
