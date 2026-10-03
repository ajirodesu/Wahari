package dev.citali.needle.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Anchor choice for the bottom sheet.
 *
 * Convention under test: velocity is in dp/s with pointer-coordinate signs,
 * positive = finger moving down. A fast fling down from Full closes the
 * sheet; a fast fling up opens it; slow releases snap to the nearest anchor.
 */
class SheetTargetTest {

    private val partial = 500f
    private val closed = 1000f

    private fun target(
        current: Float,
        velocityDp: Float = 0f,
        allowExpand: Boolean = true,
    ): SheetValue = resolveSheetTarget(current, velocityDp, partial, closed, allowExpand)

    @Test
    fun fastFlingDownFromFullCloses() {
        assertEquals(SheetValue.Closed, target(current = 100f, velocityDp = 2500f))
    }

    @Test
    fun fastFlingDownFromHalfCloses() {
        assertEquals(SheetValue.Closed, target(current = 700f, velocityDp = 2500f))
    }

    @Test
    fun ordinaryFlingDownStepsOneAnchor() {
        // From Full, a moderate fling down lands on Partial…
        assertEquals(SheetValue.Partial, target(current = 100f, velocityDp = 1500f))
        // …and from Partial it closes.
        assertEquals(SheetValue.Closed, target(current = 600f, velocityDp = 1500f))
    }

    @Test
    fun fastFlingUpOpens() {
        assertEquals(SheetValue.Expanded, target(current = 500f, velocityDp = -2500f))
        assertEquals(SheetValue.Expanded, target(current = 900f, velocityDp = -1500f))
    }

    @Test
    fun flingUpWithoutExpandLandsOnPartial() {
        assertEquals(SheetValue.Partial, target(current = 900f, velocityDp = -1500f, allowExpand = false))
    }

    @Test
    fun slowReleaseSnapsToNearestAnchor() {
        assertEquals(SheetValue.Expanded, target(current = 200f))
        assertEquals(SheetValue.Partial, target(current = 400f))
        assertEquals(SheetValue.Partial, target(current = 600f))
        assertEquals(SheetValue.Closed, target(current = 900f))
    }

    @Test
    fun withoutExpandNearestIgnoresExpanded() {
        assertEquals(SheetValue.Partial, target(current = 100f, allowExpand = false))
        assertEquals(SheetValue.Closed, target(current = 900f, allowExpand = false))
    }
}
