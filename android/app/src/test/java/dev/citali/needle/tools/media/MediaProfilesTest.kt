package dev.citali.needle.tools.media

import dev.citali.needle.tools.media.strategies.AccessibilityStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Selector profiles are data, not code: parsing and package matching must
 * hold for whatever ships in res/raw/media_profiles.json.
 */
class MediaProfilesTest {

    private val json = """
        {"profiles": [
          {"package": "com.google.android.youtube",
           "searchTerms": ["search youtube", "search"],
           "excludeResultTerms": ["home", "shorts"],
           "playerSignals": ["pause"]},
          {"package": "*",
           "searchTerms": ["search"],
           "excludeResultTerms": ["home"],
           "playerSignals": ["pause"]}
        ]}
    """.trimIndent()

    @Test
    fun parsesProfiles() {
        val profiles = AccessibilityStrategy.parseProfiles(json)
        assertEquals(2, profiles.size)
        assertEquals("com.google.android.youtube", profiles[0].packageName)
        assertTrue(profiles[0].searchTerms.contains("search"))
        assertTrue(profiles[0].excludeResultTerms.contains("shorts"))
    }

    @Test
    fun prefersExactPackageThenGeneric() {
        val profiles = AccessibilityStrategy.parseProfiles(json)
        assertEquals(
            "com.google.android.youtube",
            AccessibilityStrategy.profileFor(profiles, "com.google.android.youtube").packageName,
        )
        assertEquals(
            "*",
            AccessibilityStrategy.profileFor(profiles, "com.example.player").packageName,
        )
    }

    @Test
    fun malformedJsonYieldsNoProfiles() {
        assertTrue(AccessibilityStrategy.parseProfiles("not json").isEmpty())
        assertTrue(AccessibilityStrategy.parseProfiles("{}").isEmpty())
    }

    @Test
    fun emptyProfilesStillYieldAGenericFallback() {
        val fallback = AccessibilityStrategy.profileFor(emptyList(), "com.example.player")
        assertEquals("search", fallback.searchTerms.single())
        assertTrue(fallback.playerSignals.contains("pause"))
    }

    @Test
    fun resourceIdSelectorsAreOptional() {
        val parsed = AccessibilityStrategy.parseProfiles(json)
        assertTrue(parsed.all { it.searchResourceIds.isEmpty() })
        val withIds = AccessibilityStrategy.parseProfiles(
            """{"profiles": [{"package": "com.x", "searchResourceIds": ["search_box"]}]}""",
        )
        assertEquals(listOf("search_box"), withIds.single().searchResourceIds)
        assertEquals(listOf("search"), withIds.single().searchTerms)
    }
}
