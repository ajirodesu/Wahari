package dev.citali.needle.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import dev.citali.needle.ui.theme.WahariLayout
import dev.citali.needle.ui.theme.WahariTokens
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Sheet anchors: hidden, default height, near-full height. */
enum class SheetValue { Closed, Partial, Expanded }

/** Fling thresholds in dp/s. Positive velocity is downward (finger moving down). */
internal const val SHEET_FLING_DP = 1000f

/** A fast downward fling skips the middle anchor straight to Closed. */
internal const val SHEET_FLING_SKIP_DP = 2000f

/**
 * Pure anchor choice: which snap point a release at [current] (px, 0 = expanded)
 * with [velocityDp] (+down / −up, dp/s) settles to.
 *
 * Slow releases snap to the nearest anchor; fast flings honor direction, with a
 * fast fling down skipping straight to Closed.
 */
internal fun resolveSheetTarget(
    current: Float,
    velocityDp: Float,
    partialPx: Float,
    expandedPx: Float,
    allowExpand: Boolean,
): SheetValue {
    // Fast fling down from EXPANDED skips PARTIAL straight to CLOSED.
    if (velocityDp > SHEET_FLING_SKIP_DP && current < partialPx) return SheetValue.Closed
    if (velocityDp < -SHEET_FLING_DP) {
        return if (allowExpand) SheetValue.Expanded else SheetValue.Partial
    }
    if (velocityDp > SHEET_FLING_DP) {
        return if (current < partialPx) SheetValue.Partial else SheetValue.Closed
    }
    // Otherwise the nearest anchor (past 50% progress).
    val candidates = listOf(
        SheetValue.Expanded to 0f,
        SheetValue.Partial to partialPx,
        SheetValue.Closed to expandedPx,
    ).filter { (v, _) -> v != SheetValue.Expanded || allowExpand }
    return candidates.minByOrNull { (_, px) -> abs(current - px) }?.first
        ?: SheetValue.Partial
}

/** Finger-tracking state kept outside snapshots: written per touch delta, never recomposed. */
private class SheetDragTracker {
    var active = false
    var anchor = 0f
}

/**
 * Claude-style bottom sheet shared by every modal in the app.
 *
 * - One [offset] Animatable (px, 0 = expanded) is the single position source,
 *   keyed on pixel sizes only so programmatic changes animate instead of
 *   jumping. Releases settle with a spring that starts from the release
 *   velocity; anchor choice lives in [resolveSheetTarget] and is unit-tested.
 * - Drags accumulate 1:1 in a [SheetDragTracker] synchronously per delta, so
 *   queued frames can never compute from a stale offset and lose movement.
 * - Nested scroll contract: swipe up below EXPANDED grows the sheet before the
 *   content scrolls; swipe down scrolls content first and only drags the sheet
 *   once the content is at its top; leftover fling velocity passes through
 *   with pointer-coordinate signs (down = +y, no flip).
 * - Touching during an animation stops it and hands control to the finger.
 * - Axis lock is decided once, past touch slop, for the whole drag; a plain
 *   tap never consumes, so clicks still fire.
 * - Only the first pointer is tracked; anything else is ignored.
 */
@Composable
fun DraggableBottomSheet(
    value: SheetValue,
    onValueChange: (SheetValue) -> Unit,
    partialHeight: Dp,
    expandedHeight: Dp,
    modifier: Modifier = Modifier,
    allowExpand: Boolean = true,
    enableHorizontalDismiss: Boolean = false,
    scrimMaxAlpha: Float = WahariTokens.SCRIM_SHEET,
    sheetShape: Shape = WahariTokens.sheetShape,
    header: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.(ScrollState) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val partialPx = with(density) { partialHeight.toPx() }
    val expandedPx = with(density) { expandedHeight.toPx() }
    fun anchorPx(v: SheetValue): Float = when (v) {
        SheetValue.Closed -> expandedPx
        SheetValue.Partial -> (expandedPx - partialPx).coerceAtLeast(0f)
        SheetValue.Expanded -> 0f
    }

    // The Animatable is keyed on pixel sizes only: recreating it on every
    // `value` change used to jump straight to the target anchor instead of
    // animating. Programmatic changes animate via LaunchedEffect below.
    val offset = remember(partialPx, expandedPx) { Animatable(anchorPx(value)) }
    val drag = remember { SheetDragTracker() }
    var settling by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    var dismissPx by remember { mutableFloatStateOf(0f) }
    var sheetWidthPx by remember { mutableFloatStateOf(0f) }
    val scroll = rememberScrollState()
    val rubberPx = with(density) { 48.dp.toPx() }

    fun pxPerDp(): Float = with(density) { 1.dp.toPx() }

    /** Ends finger tracking. Idempotent; safe to call from every release path. */
    fun endDrag() {
        drag.active = false
        dragging = false
    }

    /** Starts finger tracking: cancels any settle animation and stops the spring. */
    fun beginDrag() {
        if (drag.active) return
        drag.active = true
        drag.anchor = offset.value
        dragging = true
        settleJob?.cancel()
        settling = false
        scope.launch { offset.stop() }
    }

    fun settleSheet(velocityPx: Float = 0f) {
        endDrag()
        settleJob?.cancel()
        settling = true
        settleJob = scope.launch {
            val velocityDp = velocityPx / pxPerDp()
            val target = resolveSheetTarget(
                current = offset.value,
                velocityDp = velocityDp,
                partialPx = anchorPx(SheetValue.Partial),
                expandedPx = expandedPx,
                allowExpand = allowExpand,
            )
            try {
                offset.animateTo(
                    anchorPx(target),
                    animationSpec = spring(
                        dampingRatio = 0.85f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    initialVelocity = velocityPx.coerceIn(-12000f, 12000f),
                )
            } finally {
                dismissPx = 0f
                settling = false
            }
            onValueChange(target)
        }
    }

    // Programmatic changes (open, scrim tap, Back, option select): smooth, no velocity.
    LaunchedEffect(value, partialPx, expandedPx) {
        if (!dragging && abs(offset.value - anchorPx(value)) > 1f) {
            endDrag()
            settleJob?.cancel()
            settleJob = scope.launch {
                try {
                    offset.animateTo(
                        anchorPx(value),
                        animationSpec = spring(
                            dampingRatio = 0.85f,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    )
                } finally {
                    settling = false
                }
            }
        }
    }

    if (value == SheetValue.Closed && !dragging && !settling &&
        offset.value >= expandedPx - 1f
    ) {
        return
    }

    val minOffset = if (allowExpand) 0f else anchorPx(SheetValue.Partial)

    fun applyVerticalDrag(dy: Float) {
        // dy > 0 = finger moving down = sheet shrinking (offset grows).
        // The anchor accumulates synchronously per delta, so queued frames can
        // never compute from a stale offset and lose movement; every frame
        // snaps to the latest anchor and the sheet tracks the finger 1:1.
        beginDrag()
        drag.anchor += dy
        val desired = drag.anchor
        val clamped = when {
            // Rubber-band above EXPANDED: ~20% of the finger movement.
            desired < minOffset -> minOffset + (desired - minOffset) * 0.2f
            desired > expandedPx -> expandedPx
            else -> desired
        }.coerceIn(minOffset - rubberPx, expandedPx)
        drag.anchor = clamped
        val target = clamped
        scope.launch {
            offset.stop()
            offset.snapTo(target)
        }
    }

    val nested = remember(offset, partialPx, expandedPx, allowExpand) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val dy = available.y
                // Swipe up: grow the sheet first; content scrolls only at EXPANDED.
                if (dy < 0f && offset.value > minOffset + 1f) {
                    val grow = (-dy).coerceAtMost(offset.value - minOffset)
                    if (grow > 0f) {
                        applyVerticalDrag(-grow)
                        return Offset(0f, -grow)
                    }
                }
                // Swipe down: content scrolls first; the sheet drags at scroll top.
                if (dy > 0f && scroll.value == 0) {
                    applyVerticalDrag(dy)
                    return Offset(0f, dy)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                // Leftover goes to the sheet (same rules as pre-scroll).
                return onPreScroll(available, source)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // Hand the leftover fling to the sheet so the motion continues.
                // Velocity follows pointer coordinates (down = +y), the same
                // convention as the drag deltas and VelocityTracker, so no
                // sign flip: a downward fling settles down, an upward one up.
                settleSheet(available.y)
                return available
            }
        }
    }

    val headerDrag = Modifier.pointerInput(partialPx, expandedPx, allowExpand, enableHorizontalDismiss) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            val slop = viewConfiguration.touchSlop
            var totalX = 0f
            var totalY = 0f
            var lockedVertical = false
            var lockedHorizontal = false
            var slopDropped = false
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                    if (change.isConsumed) return@awaitEachGesture
                    if (!change.pressed) {
                        if (lockedVertical || lockedHorizontal) {
                            val v = runCatching { tracker.calculateVelocity() }.getOrNull()
                            if (lockedHorizontal) {
                                val width = sheetWidthPx.coerceAtLeast(1f)
                                val flingX = abs(v?.x ?: 0f) > SHEET_FLING_DP * pxPerDp()
                                if (abs(dismissPx) > width / 4f || (flingX && dismissPx != 0f)) {
                                    val target = width * sign(dismissPx).let { if (it == 0f) 1f else it }
                                    settling = true
                                    settleJob?.cancel()
                                    settleJob = scope.launch {
                                        try {
                                            animate(
                                                initialValue = dismissPx,
                                                targetValue = target,
                                                animationSpec = spring(0.85f, Spring.StiffnessMediumLow),
                                                initialVelocity = (v?.x ?: 0f).coerceIn(-12000f, 12000f),
                                            ) { frame, _ -> dismissPx = frame }
                                        } finally {
                                            dismissPx = 0f
                                            settling = false
                                        }
                                        onValueChange(SheetValue.Closed)
                                    }
                                } else {
                                    settling = true
                                    settleJob?.cancel()
                                    settleJob = scope.launch {
                                        try {
                                            animate(
                                                initialValue = dismissPx,
                                                targetValue = 0f,
                                                animationSpec = spring(0.85f, Spring.StiffnessMediumLow),
                                            ) { frame, _ -> dismissPx = frame }
                                        } finally {
                                            settling = false
                                        }
                                    }
                                }
                            } else {
                            settleSheet(v?.y ?: 0f)
                        }
                        endDrag()
                        } else {
                            // Plain tap: never consume, so handle clicks still fire.
                            return@awaitEachGesture
                        }
                        change.consume()
                        return@awaitEachGesture
                    }
                    val dx = change.position.x - change.previousPosition.x
                    val dy = change.position.y - change.previousPosition.y
                    tracker.addPosition(change.uptimeMillis, change.position)
                    if (!lockedVertical && !lockedHorizontal) {
                        totalX += dx
                        totalY += dy
                        if (abs(totalX) < slop && abs(totalY) < slop) continue
                        // Axis lock: the dominant axis wins for the whole drag.
                        if (abs(totalY) >= abs(totalX) || !enableHorizontalDismiss) {
                            lockedVertical = true
                            // Drop the slop from the first claimed move: no jump.
                            totalY -= sign(totalY) * slop.coerceAtMost(abs(totalY))
                        } else {
                            lockedHorizontal = true
                            totalX -= sign(totalX) * slop.coerceAtMost(abs(totalX))
                        }
                        beginDrag()
                    }
                    if (lockedHorizontal) {
                        change.consume()
                        totalX += dx
                        val width = sheetWidthPx.coerceAtLeast(1f)
                        dismissPx = totalX.coerceIn(-width, width)
                    } else if (lockedVertical) {
                        change.consume()
                        totalY += dy
                        // First claimed move drops the slop distance: no jump.
                        if (!slopDropped) {
                            slopDropped = true
                            applyVerticalDrag(dy - sign(dy) * slop.coerceAtMost(abs(dy)))
                        } else {
                            applyVerticalDrag(dy)
                        }
                    }
                }
            } catch (_: Exception) {
                // Pointer cancelled: settle to the nearest anchor, never stuck.
                if (lockedVertical || lockedHorizontal) {
                    endDrag()
                    dismissPx = 0f
                    settleSheet()
                }
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = (1f - offset.value / expandedPx.coerceAtLeast(1f)) * scrimMaxAlpha }
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onValueChange(SheetValue.Closed) },
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = WahariLayout.sheetMax)
                .fillMaxWidth()
                .height(expandedHeight)
                .onSizeChanged { sheetWidthPx = it.width.toFloat() }
                .graphicsLayer {
                    translationY = offset.value
                    translationX = dismissPx
                    val w = sheetWidthPx.coerceAtLeast(1f)
                    alpha = (1f - abs(dismissPx) / (w * 0.9f)).coerceIn(0f, 1f)
                }
                .clip(sheetShape)
                .background(WahariTokens.bgSheet)
                .imePadding(),
        ) {
            Column(Modifier.fillMaxWidth().then(headerDrag)) {
                header()
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .nestedScroll(nested)
                    .verticalScroll(scroll),
            ) {
                content(scroll)
            }
        }
    }

    BackHandler(enabled = value != SheetValue.Closed) {
        if (value == SheetValue.Expanded && allowExpand) {
            onValueChange(SheetValue.Partial)
        } else {
            onValueChange(SheetValue.Closed)
        }
    }
}
