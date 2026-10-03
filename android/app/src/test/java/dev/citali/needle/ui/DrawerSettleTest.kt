package dev.citali.needle.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drawer settle rule: fling beats position, slow releases snap at 50%, and
 * a weak fling against a mostly-completed drag is ignored. Velocity is in
 * the opening direction, dp/s.
 */
class DrawerSettleTest {

    @Test
    fun fastFlingOpensFromAnywhere() {
        assertTrue(resolveDrawerTarget(0.05f, 2500f))
        assertTrue(resolveDrawerTarget(0.5f, 1500f))
    }

    @Test
    fun fastFlingClosesFromAnywhere() {
        assertFalse(resolveDrawerTarget(0.95f, -2500f))
        assertFalse(resolveDrawerTarget(0.5f, -1500f))
    }

    @Test
    fun slowReleaseSnapsAtHalf() {
        assertTrue(resolveDrawerTarget(0.51f, 0f))
        assertFalse(resolveDrawerTarget(0.5f, 0f))
        assertFalse(resolveDrawerTarget(0.1f, 200f))
    }

    @Test
    fun thresholdIsExclusive() {
        // Exactly at the fling speed: position decides.
        assertTrue(resolveDrawerTarget(0.9f, 1000f))
        assertTrue(resolveDrawerTarget(0.9f, -1000f))
        assertFalse(resolveDrawerTarget(0.1f, -1000f))
    }

    @Test
    fun weakOpposingFlingIgnoredWhenMostlyComplete() {
        // 90% open with a mild closing fling: stays open.
        assertTrue(resolveDrawerTarget(0.9f, -1500f))
        // …but a hard fling still closes it.
        assertFalse(resolveDrawerTarget(0.9f, -2500f))
        // Mirror: 10% open with a mild opening fling stays closed.
        assertFalse(resolveDrawerTarget(0.1f, 1500f))
        assertTrue(resolveDrawerTarget(0.1f, 2500f))
    }

    @Test
    fun progressClamped() {
        assertTrue(resolveDrawerTarget(4f, 0f))
        assertFalse(resolveDrawerTarget(-4f, 0f))
    }
}
