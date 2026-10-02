package dev.citali.needle.ui.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Layout constants from the Wahari HTML reference (1 CSS px = 1 dp).
 * Nothing here depends on the 412 x 890 preview size; window-dependent values
 * are derived from real constraints and insets at the call site.
 */
object WahariLayout {
    /** 56dp band below the status inset that holds the nav buttons. */
    val navBand = 56.dp
    /** Horizontal padding of nav rows (plus any display-cutout inset). */
    val navSide = 14.dp
    /** Nav circle buttons. LastChat ChromePill. */
    val navButton = 48.dp
    val navIcon = 20.dp

    /** Chat list gaps. */
    val contentSide = 18.dp
    val messageGap = 24.dp
    /** List top padding = status inset + this; bottom = dock height + this. */
    val listTopExtra = 66.dp
    val listBottomExtra = 36.dp

    /** Dock paddings. Sides animate 34 <-> 14; top and bottom never change. */
    val dockSideInactive = 34.dp
    val dockSideActive = 14.dp
    val dockTop = 16.dp
    val dockBottomMin = 24.dp

    /** Composer capsule: 48dp tall including its border, 22dp radius in both modes. LastChat ChromePill. */
    val capsuleHeight = 48.dp
    val modeALeftPad = 4.dp
    val modeARightPad = 6.dp
    val modeBTopPad = 12.dp
    val modeBRightPad = 10.dp
    val modeBLeftPad = 8.dp
    val modeBToolbarGap = 8.dp
    val toolbarHeight = 36.dp
    /** Fixed row content in Mode A: 4 pad + 44 grid + 36 mic + 36 send + 6 pad = 126dp. */
    val modeAFixedContent = 126.dp
    /** Capsule border eats 2dp of the width. */
    val capsuleBorder = 2.dp
    /** Text width in Mode B: capsule width minus left/right padding and border. */
    val modeBTextOverhead = 20.dp
    /** Composer line box. */
    val composerLine = 22.dp
    /** Capsule height = visibleLines x 22 + 66. */
    val modeBChrome = 66.dp
    val composerMaxLines = 6

    /** Icon boxes and glyphs. */
    val iconBox = 24.dp
    val capsuleIcon = 20.dp
    val sendCircle = 36.dp
    val sendGlyph = 17.dp
    val popupIcon = 14.dp
    val optionIcon = 20.dp
    val tagIcon = 14.dp
    val toastIcon = 14.dp
    val busyIndicator = 16.dp

    /** Popup metrics. */
    val popupGap = 8.dp
    val popupTail = 10.dp
    val popupTailRight = 22.dp
    val popupTailDrop = 5.dp
    const val LONG_PRESS_MS = 420L

    /** User bubble: max 82% of the content width. */
    const val BUBBLE_MAX_FRACTION = 0.82f

    /** Drawer: 82% of the screen, max 320dp. */
    const val DRAWER_FRACTION = 0.82f
    val drawerMax = 320.dp
    val drawerHeader = 60.dp

    /** Sheet snaps. */
    const val SHEET_HALF_FRACTION = 0.5f

    /** Content max widths (no visual change at 640dp or narrower). */
    val contentMax = 640.dp
    val sheetMax = 640.dp
    val overlayMax = 560.dp

    /** Overlay clamp margin. */
    val overlayMargin = 8.dp

    /** Cap for tool-result / mono blocks. */
    val monoMax = 240.dp
    /** Cap for multi-line text fields. */
    val fieldMax = 160.dp
    /** Cap for plan stage lists. */
    val stagesMax = 280.dp

    /**
     * Short-window rule: the capsule can never push the top nav off screen.
     * Mirrors the HTML's 6-line cap on tall windows.
     */
    fun maxComposerLines(windowHeightAboveIme: Dp): Int {
        val lines = floor((windowHeightAboveIme * 0.4f - modeBChrome) / composerLine).toInt()
        return min(composerMaxLines, max(2, lines))
    }
}

/** Real status-bar inset height. */
@Composable
fun statusInset(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

/** Real navigation-bar inset height. */
@Composable
fun navInset(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/** Horizontal content padding honoring the display cutout. */
@Composable
fun contentSidePadding(base: Dp = WahariLayout.contentSide): Dp {
    val cutout = WindowInsets.displayCutout.asPaddingValues()
    val dir = LocalLayoutDirection.current
    val side = maxOf(
        cutout.calculateLeftPadding(dir),
        cutout.calculateRightPadding(dir),
    )
    return maxOf(base, side)
}
