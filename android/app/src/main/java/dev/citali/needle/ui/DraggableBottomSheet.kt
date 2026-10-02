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

/**
 * Claude-style bottom sheet shared by every modal in the app.
 *
 * - One [offset] Animatable (px, 0 = expanded) is the single position source.
 *   Drags drive it with snapTo (1:1, no lag); releases settle with a spring
 *   that starts from the release velocity, so nothing ever jumps.
 * - The offset is read inside draw-scope lambdas only: dragging invalidates
 *   draw/layout of the sheet, never the whole screen.
 * - Nested scroll contract: swipe up at PARTIAL grows the sheet before the
 *   content scrolls; swipe down scrolls content first and only drags the sheet
 *   once the content is at its top; leftover fling velocity passes through.
 * - Touching during an animation stops it and hands control to the finger.
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

    val offset = remember(value, partialPx, expandedPx) { Animatable(anchorPx(value)) }
    var settling by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    var dismissPx by remember { mutableFloatStateOf(0f) }
    var sheetWidthPx by remember { mutableFloatStateOf(0f) }
    val scroll = rememberScrollState()

    fun flingThresholdDp(): Float = 1000f
    fun pxPerDp(): Float = with(density) { 1.dp.toPx() }

    fun settleSheet(velocityPx: Float = 0f) {
        settleJob?.cancel()
        settling = true
        settleJob = scope.launch {
            val velocityDp = velocityPx / pxPerDp()
            val current = offset.value
            val partial = anchorPx(SheetValue.Partial)
            val target = when {
                // Fast fling down from EXPANDED skips PARTIAL straight to CLOSED.
                velocityDp > 2000f && current < partial -> SheetValue.Closed
                velocityDp < -flingThresholdDp() -> {
                    if (allowExpand) SheetValue.Expanded else SheetValue.Partial
                }
                velocityDp > flingThresholdDp() -> {
                    if (current < partial) SheetValue.Partial else SheetValue.Closed
                }
                // Otherwise the nearest anchor (past 50% progress).
                else -> {
                    val candidates = listOf(
                        SheetValue.Expanded to 0f,
                        SheetValue.Partial to partial,
                        SheetValue.Closed to expandedPx,
                    ).filter { (v, _) -> v == SheetValue.Expanded && allowExpand || v != SheetValue.Expanded }
                    candidates.minByOrNull { (_, px) -> abs(current - px) }?.first
                        ?: SheetValue.Partial
                }
            }
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

    // Programmatic changes (open, scrim tap, Back): smooth, no velocity.
    LaunchedEffect(value) {
        if (!settling && abs(offset.value - anchorPx(value)) > 1f && !dragging) {
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
        val desired = offset.value + dy
        scope.launch {
            offset.stop()
            val clamped = when {
                // Rubber-band above EXPANDED: ~20% of the finger movement.
                desired < minOffset -> minOffset + (desired - minOffset) * 0.2f
                desired > expandedPx -> expandedPx
                else -> desired
            }
            offset.snapTo(clamped.coerceIn(minOffset - with(density) { 48.dp.toPx() }, expandedPx))
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
                // NOTE: the framework reports this velocity sign-inverted relative
                // to drag deltas (verified on-device: a downward release fling
                // arrives negative), so negate it into pointer coordinates.
                settleSheet(-available.y)
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
                                val flingX = abs(v?.x ?: 0f) > flingThresholdDp() * pxPerDp()
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
                            dragging = false
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
                        dragging = true
                        settleJob?.cancel()
                        settling = false
                        scope.launch { offset.stop() }
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
                    dragging = false
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
