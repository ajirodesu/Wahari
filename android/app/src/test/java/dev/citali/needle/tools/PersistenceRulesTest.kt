package dev.citali.needle.tools

import dev.citali.needle.engine.NeedlePrefs
import dev.citali.needle.tools.AccessibilityState.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The persistence contract, without Android: setup gating, accessibility
 * resolution, admin-ID parsing and tool-pack round-trips must all behave
 * deterministically so restarts restore exactly what the user left.
 */
class PersistenceRulesTest {

    // ---- Setup gate ------------------------------------------------------

    @Test
    fun freshInstallShowsSetup() {
        assertTrue(AccessibilityState.shouldShowSetup(introCompleted = false, setupComplete = false))
    }

    @Test
    fun unfinishedSetupShowsSetup() {
        assertTrue(AccessibilityState.shouldShowSetup(introCompleted = true, setupComplete = false))
    }

    @Test
    fun completedSetupNeverShowsSetup() {
        assertFalse(AccessibilityState.shouldShowSetup(introCompleted = true, setupComplete = true))
    }

    // ---- Accessibility resolution ----------------------------------------

    @Test
    fun enabledServiceIsReady() {
        assertEquals(UiState.READY, AccessibilityState.resolve(setupCompleted = false, serviceEnabled = true))
        assertEquals(UiState.READY, AccessibilityState.resolve(setupCompleted = true, serviceEnabled = true))
    }

    @Test
    fun disabledServiceAfterSetupKeepsHistory() {
        // Disabling the service later must NOT erase setup completion.
        assertEquals(
            UiState.DISABLED_AFTER_SETUP,
            AccessibilityState.resolve(setupCompleted = true, serviceEnabled = false),
        )
    }

    @Test
    fun neverConfiguredStaysNeverConfigured() {
        assertEquals(
            UiState.NEVER_CONFIGURED,
            AccessibilityState.resolve(setupCompleted = false, serviceEnabled = false),
        )
    }

    // ---- Admin IDs --------------------------------------------------------

    @Test
    fun adminIdsAcceptMixedSeparators() {
        val (ids, invalid) = NeedlePrefs.parseTelegramAdminIds("123, 456 789\n987;321")
        assertEquals(setOf(123L, 456L, 789L, 987L, 321L), ids)
        assertTrue(invalid.isEmpty())
    }

    @Test
    fun adminIdsRejectGarbageAndZero() {
        val (ids, invalid) = NeedlePrefs.parseTelegramAdminIds("123, abc, 0, , 456")
        assertEquals(setOf(123L, 456L), ids)
        assertEquals(listOf("abc", "0"), invalid)
    }

    @Test
    fun adminIdsEmptyStaysEmpty() {
        val (ids, invalid) = NeedlePrefs.parseTelegramAdminIds("  ,, \n ")
        assertTrue(ids.isEmpty())
        assertTrue(invalid.isEmpty())
    }

    @Test
    fun adminIdsDeduplicate() {
        val (ids, _) = NeedlePrefs.parseTelegramAdminIds("42,42 42")
        assertEquals(setOf(42L), ids)
    }

    // ---- Tool packs -------------------------------------------------------

    @Test
    fun packsRoundTrip() {
        val packs = setOf(PhoneTools.Pack.CORE, PhoneTools.Pack.MEDIA)
        assertEquals(packs, NeedlePrefs.deserializeToolPacks(NeedlePrefs.serializeToolPacks(packs)))
    }

    @Test
    fun packsMissingValueFallsBackToDefaults() {
        assertEquals(PhoneTools.defaultPacks, NeedlePrefs.deserializeToolPacks(null))
    }

    @Test
    fun packsUnknownNamesFallBackToCore() {
        assertEquals(setOf(PhoneTools.Pack.CORE), NeedlePrefs.deserializeToolPacks(setOf("NOPE")))
    }

    @Test
    fun packsUnknownNamesDoNotResetKnownPacks() {
        // An app update adding a pack must not wipe the user's selection.
        assertEquals(
            setOf(PhoneTools.Pack.DEVICE),
            NeedlePrefs.deserializeToolPacks(setOf("DEVICE", "FUTURE_PACK")),
        )
    }

    @Test
    fun packsEmptySelectionFallsBackToCore() {
        assertEquals(setOf(PhoneTools.Pack.CORE), NeedlePrefs.deserializeToolPacks(emptySet()))
    }
}
