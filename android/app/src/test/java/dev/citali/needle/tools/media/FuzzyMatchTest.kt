package dev.citali.needle.tools.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyMatchTest {

    @Test
    fun exactBeatsPrefixBeatsSubstring() {
        assertEquals(1.0, FuzzyMatch.score("YouTube", "youtube"), 0.0)
        assertTrue(FuzzyMatch.score("you", "youtube") > FuzzyMatch.score("tube", "youtube"))
        assertTrue(FuzzyMatch.score("tube", "youtube") > 0.0)
    }

    @Test
    fun blanksScoreZero() {
        assertEquals(0.0, FuzzyMatch.score("", "youtube"), 0.0)
        assertEquals(0.0, FuzzyMatch.score("yt", ""), 0.0)
    }

    @Test
    fun tokenOverlapCounts() {
        assertTrue(FuzzyMatch.score("enchanted taylor", "Enchanted — Taylor Swift") > 0.0)
        assertEquals(0.0, FuzzyMatch.score("xyzzy plugh", "Enchanted"), 0.0)
    }

    @Test
    fun nearTyposStillMatch() {
        assertTrue(FuzzyMatch.score("spotfy", "spotify") > 0.5)
        assertTrue(FuzzyMatch.score("youtub", "youtube") > 0.5)
    }

    @Test
    fun bestPicksHighest() {
        val best = FuzzyMatch.best("youtube", listOf("com.spotify.music", "com.google.android.youtube"))
        assertEquals("com.google.android.youtube", best?.first)
        assertNull(FuzzyMatch.best("xyzzy", listOf("abc", "def")))
    }
}
