package dev.citali.needle.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import dev.citali.needle.ui.theme.WahariLayout
import dev.citali.needle.ui.theme.WahariTokens
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

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

/**
 * Claude-style bottom sheet shared by every modal in the app.
 *
 * - One [offset] Animatable (px, 0 = expanded) is the single position source,
 *   keyed on pixel sizes only so programmatic changes animate instead of
 *   jumping. Releases settle with a spring that starts from the release
 *   velocity; anchor choice lives in [resolveSheetTarget] and is unit-tested.
 * - Drags write it synchronously per delta (no coroutine hop, no stale reads);
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
    // Finger position, written synchronously per delta (draw-scope reads only,
    // so no recomposition storm). The Animatable is for settling only.
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    // Anchor the in-flight settle is heading for: LaunchedEffect skips while
    // it matches `value`, so the release spring and the state follower never
    // run two animations at once. Null when no settle owns the motion.
    var settlingTarget by remember { mutableStateOf<SheetValue?>(null) }
    var dismissPx by remember { mutableFloatStateOf(0f) }
    var sheetWidthPx by remember { mutableFloatStateOf(0f) }
    val scroll = rememberScrollState()
    val rubberPx = with(density) { 48.dp.toPx() }
    // True while the sheet (not just the content) consumed motion in the
    // current gesture: gates onPostFling so a pure content fling that merely
    // hits its bound can never teleport the sheet to another anchor.
    var sheetConsumed by remember { mutableStateOf(false) }

    fun pxPerDp(): Float = with(density) { 1.dp.toPx() }

    /** Live position: the finger while dragging, the spring otherwise. */
    fun currentOffset(): Float = if (dragging) dragOffset else offset.value

    /** Ends finger tracking. Idempotent; safe to call from every release path. */
    fun endDrag() {
        dragging = false
    }

    /** First horizontal move past slop: snapshot position, stop any spring. */
    fun beginDrag() {
        dragOffset = offset.value
        dragging = true
        settlingTarget = null
        settleJob?.cancel()
        settling = false
        scope.launch { offset.stop() }
    }

    fun settleSheet(velocityPx: Float = 0f, fromDrag: Boolean = false) {
        endDrag()
        val velocityDp = velocityPx / pxPerDp()
        // Live finger position: direct writes go to dragOffset during a drag
        // while the Animatable stays frozen, so offset.value is stale here.
        // settleSheet only ever runs after beginDrag (release, fling,
        // cancellation), hence dragOffset is always the truth.
        val target = resolveSheetTarget(
            current = dragOffset,
            velocityDp = velocityDp,
            partialPx = anchorPx(SheetValue.Partial),
            expandedPx = expandedPx,
            allowExpand = allowExpand,
        )
        // State first: the animation follows, never the other way round.
        sheetConsumed = false
        settlingTarget = target
        onValueChange(target)
        settleJob?.cancel()
        settling = true
        val launched = scope.launch {
            try {
                offset.stop()
                // Re-sync after a drag (see the sidebar for why); never snap
                // on a programmatic settle (dragOffset is stale then).
                if (fromDrag) offset.snapTo(dragOffset)
                offset.animateTo(
                    anchorPx(target),
                    animationSpec = spring(
                        dampingRatio = 0.85f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    initialVelocity = velocityPx.coerceIn(-12000f, 12000f),
                )
            } finally {
                // Only the latest settle may clear the flags.
                if (settleJob === coroutineContext[Job]) {
                    dismissPx = 0f
                    settling = false
                    settlingTarget = null
                }
            }
        }
        settleJob = launched
    }

    // Programmatic changes (open, scrim tap, Back, option select): smooth, no
    // velocity. Skipped while the release spring already heads for `value`.
    LaunchedEffect(value, partialPx, expandedPx) {
        if (!dragging && settlingTarget != value &&
            abs(offset.value - anchorPx(value)) > 1f
        ) {
            endDrag()
            sheetConsumed = false
            settlingTarget = value
            settleJob?.cancel()
            val launched = scope.launch {
                try {
                    offset.animateTo(
                        anchorPx(value),
                        animationSpec = spring(
                            dampingRatio = 0.85f,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    )
                } finally {
                    if (settleJob === coroutineContext[Job]) {
                        settling = false
                        settlingTarget = null
                    }
                }
            }
            settleJob = launched
        }
    }

    // Hooks above always run; only the drawn content is conditional, so the
    // sheet can never strand a Back handler, an effect, or a modifier setup.
    val renderSheet = value != SheetValue.Closed || dragging || settling

    val minOffset = if (allowExpand) 0f else anchorPx(SheetValue.Partial)

    fun applyVerticalDrag(dy: Float) {
        // dy > 0 = finger moving down = sheet shrinking (offset grows).
        // Synchronous per-delta write (the spring was stopped in beginDrag):
        // no coroutine hop, no stale reads, under the finger same-frame.
        beginDrag()
        sheetConsumed = true
        val desired = dragOffset + dy
        dragOffset = when {
            // Rubber-band above EXPANDED: ~20% of the finger movement.
            desired < minOffset -> minOffset + (desired - minOffset) * 0.2f
            desired > expandedPx -> expandedPx
            else -> desired
        }.coerceIn(minOffset - rubberPx, expandedPx)
    }

    val nested = remember(offset, partialPx, expandedPx, allowExpand, value) {
        object : NestedScrollConnection {
            // Single contract: swipe up grows the sheet before the content
            // scrolls; swipe down scrolls the content first and drags the
            // sheet only at scroll top. Returns what the sheet consumed.
            fun absorb(dy: Float): Float {
                if (dy < 0f && currentOffset() > minOffset + 1f) {
                    val grow = (-dy).coerceAtMost(currentOffset() - minOffset)
                    if (grow > 0f) {
                        applyVerticalDrag(-grow)
                        return -grow
                    }
                    return 0f
                }
                if (dy > 0f && scroll.value == 0) {
                    applyVerticalDrag(dy)
                    return dy
                }
                return 0f
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                return Offset(0f, absorb(available.y))
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                // True leftover only: what the content did not take.
                return Offset(0f, absorb(available.y))
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                // Sheet mid-travel takes the fling first, so the content does
                // not fling underneath a moving sheet.
                if (sheetConsumed && abs(currentOffset() - anchorPx(value)) > 1f) {
                    settleSheet(available.y, fromDrag = true)
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // A pure content fling that merely hit its bound must never
                // teleport the sheet: settle only when the sheet took part.
                if (sheetConsumed && abs(currentOffset() - anchorPx(value)) > 1f) {
                    settleSheet(available.y, fromDrag = true)
                    return available
                }
                sheetConsumed = false
                return Velocity.Zero
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
                                    // State first through the same settle path, then
                                    // slide the visual off; the animation follows.
                                    val target = width * sign(dismissPx).let { if (it == 0f) 1f else it }
                                    endDrag()
                                    sheetConsumed = false
                                    settlingTarget = SheetValue.Closed
                                    onValueChange(SheetValue.Closed)
                                    settleJob?.cancel()
                                    settling = true
                                    val launched = scope.launch {
                                        try {
                                            animate(
                                                initialValue = dismissPx,
                                                targetValue = target,
                                                animationSpec = spring(0.85f, Spring.StiffnessMediumLow),
                                                initialVelocity = (v?.x ?: 0f).coerceIn(-12000f, 12000f),
                                            ) { frame, _ -> dismissPx = frame }
                                        } finally {
                                            if (settleJob === coroutineContext[Job]) {
                                                dismissPx = 0f
                                                settling = false
                                                settlingTarget = null
                                            }
                                        }
                                    }
                                    settleJob = launched
                                } else {
                                    endDrag()
                                    settlingTarget = null
                                    settleJob?.cancel()
                                    settling = true
                                    val launched = scope.launch {
                                        try {
                                            animate(
                                                initialValue = dismissPx,
                                                targetValue = 0f,
                                                animationSpec = spring(0.85f, Spring.StiffnessMediumLow),
                                            ) { frame, _ -> dismissPx = frame }
                                        } finally {
                                            if (settleJob === coroutineContext[Job]) {
                                                settling = false
                                            }
                                        }
                                    }
                                    settleJob = launched
                                }
                            } else {
                                settleSheet(v?.y ?: 0f, fromDrag = true)
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
                    settleSheet(fromDrag = true)
                }
            }
        }
    }

    if (renderSheet) {
        BoxWithConstraints(modifier.fillMaxSize().testTag("sheet")) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val pos = if (dragging) dragOffset else offset.value
                        alpha = (1f - pos / expandedPx.coerceAtLeast(1f)) * scrimMaxAlpha
                    }
                    .background(Color.Black)
                    .testTag("sheet_scrim")
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "Close",
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
                        translationY = if (dragging) dragOffset else offset.value
                        translationX = dismissPx
                        val w = sheetWidthPx.coerceAtLeast(1f)
                        alpha = (1f - abs(dismissPx) / (w * 0.9f)).coerceIn(0f, 1f)
                    }
                    .clip(sheetShape)
                    .background(WahariTokens.bgSheet)
                    .imePadding()
                    .semantics {
                        dismiss("Close") {
                            onValueChange(SheetValue.Closed)
                            true
                        }
                        customActions = buildList {
                            if (allowExpand && value != SheetValue.Expanded) {
                                add(
                                    CustomAccessibilityAction("Expand") {
                                        onValueChange(SheetValue.Expanded)
                                        true
                                    },
                                )
                            }
                            if (value != SheetValue.Partial) {
                                add(
                                    CustomAccessibilityAction("Collapse to half") {
                                        onValueChange(if (allowExpand) SheetValue.Partial else SheetValue.Closed)
                                        true
                                    },
                                )
                            }
                        }
                    },
            ) {
                Column(Modifier.fillMaxWidth().testTag("sheet_header").then(headerDrag)) {
                    header()
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("sheet_content")
                        .nestedScroll(nested)
                        .verticalScroll(scroll),
                ) {
                    content(scroll)
                }
            }
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        PredictiveBackHandler(enabled = value != SheetValue.Closed && !dragging) {
            // Scrub toward closed with the back gesture; state already open.
            settleJob?.cancel()
            try {
                offset.stop()
            } catch (_: CancellationException) {
                // Stop was itself cancelled: restore below.
            }
            val start = offset.value
            val closedAnchor = anchorPx(SheetValue.Closed)
            var completed = false
            try {
                it.collect { event ->
                    offset.snapTo(start + (closedAnchor - start) * event.progress.coerceIn(0f, 1f))
                }
                completed = true
            } finally {
                if (completed) {
                    onValueChange(SheetValue.Closed)
                } else {
                    // Gesture cancelled: glide back to the current anchor.
                    settlingTarget = value
                    settleJob?.cancel()
                    val launched = scope.launch {
                        try {
                            offset.animateTo(
                                anchorPx(value),
                                animationSpec = spring(
                                    dampingRatio = 0.85f,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            )
                        } finally {
                            if (settleJob === coroutineContext[Job]) {
                                settling = false
                                settlingTarget = null
                            }
                        }
                    }
                    settleJob = launched
                }
            }
        }
    } else {
        BackHandler(enabled = value != SheetValue.Closed) {
            if (value == SheetValue.Expanded && allowExpand) {
                onValueChange(SheetValue.Partial)
            } else {
                onValueChange(SheetValue.Closed)
            }
        }
    }
}
