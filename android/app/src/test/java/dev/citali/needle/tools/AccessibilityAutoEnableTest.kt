package dev.citali.needle.tools

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The secure-settings list belongs to the whole system: enabling Wahari must
 * never drop or duplicate other apps' entries, in either component form.
 */
class AccessibilityAutoEnableTest {

    private val service =
        "com.ajirodesu.wahari/dev.citali.needle.pilot.accessibility.NeedleAccessibilityService"

    @Test
    fun enableAddsToAnEmptyList() {
        assertEquals(service, AccessibilityAutoEnable.mergeEnabledServices(null, service, true))
        assertEquals(service, AccessibilityAutoEnable.mergeEnabledServices("", service, true))
    }

    @Test
    fun enablePreservesOtherAppsAndDeduplicates() {
        val current = "com.other.app/.Service:$service"
        assertEquals(
            "com.other.app/.Service:$service",
            AccessibilityAutoEnable.mergeEnabledServices(current, service, true),
        )
    }

    @Test
    fun enableMatchesShortAndLongFormsOfTheSameClass() {
        // ".Bar" is relative to the package: same class as "com.foo.Bar",
        // so enabling adds one entry, not two.
        val current = "com.other.app/.Service:com.foo/com.foo.Bar"
        assertEquals(
            "com.other.app/.Service:com.foo/.Bar",
            AccessibilityAutoEnable.mergeEnabledServices(current, "com.foo/.Bar", true),
        )
        assertEquals(
            "com.other.app/.Service",
            AccessibilityAutoEnable.mergeEnabledServices(current, "com.foo/.Bar", false),
        )
    }

    @Test
    fun disableRemovesOursAndKeepsOthers() {
        assertEquals(
            "com.other.app/.Service",
            AccessibilityAutoEnable.mergeEnabledServices("com.other.app/.Service:$service", service, false),
        )
        assertEquals("", AccessibilityAutoEnable.mergeEnabledServices(service, service, false))
        assertEquals("", AccessibilityAutoEnable.mergeEnabledServices(null, service, false))
    }

    @Test
    fun similarButDifferentClassesAreLeftAlone() {
        // ".pilot…" under our package denotes com.ajirodesu.wahari.pilot…,
        // NOT our service (dev.citali.needle…): enabling or disabling ours
        // must neither absorb nor strip it.
        val other = "com.ajirodesu.wahari/.pilot.accessibility.NeedleAccessibilityService"
        assertEquals(
            "$other:$service",
            AccessibilityAutoEnable.mergeEnabledServices(other, service, true),
        )
        assertEquals(
            other,
            AccessibilityAutoEnable.mergeEnabledServices("$other:$service", service, false),
        )
    }
}
