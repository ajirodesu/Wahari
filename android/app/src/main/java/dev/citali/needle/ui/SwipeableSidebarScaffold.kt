package dev.citali.needle.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.citali.needle.ui.theme.WahariTokens
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/**
 * Pure settle rule: fling beats position; below the fling speed the 50%
 * mark decides; a weak fling against a mostly-completed drag is ignored.
 *
 * @param progress 0 = closed, 1 = open.
 * @param velocityDp signed swipe speed in the opening direction, dp/s.
 */
internal fun resolveDrawerTarget(
    progress: Float,
    velocityDp: Float,
    thresholdDp: Float = 1000f,
): Boolean {
    val p = progress.coerceIn(0f, 1f)
    if (velocityDp > thresholdDp) {
        if (p < 0.2f && velocityDp < thresholdDp * 2f) return false
        return true
    }
    if (velocityDp < -thresholdDp) {
        if (p > 0.8f && velocityDp > -thresholdDp * 2f) return true
        return false
    }
    return p > 0.5f
}

/**
 * One shared horizontal-drag state machine for the open detector, the scrim
 * and the drawer. Tracks only the first pointer, ignores the rest, aborts
 * the moment a child consumes the stream (scrolls, text selection and taps
 * keep priority), and axis-locks once per gesture.
 *
 * @param ignoreEdgePx dead band at both horizontal edges for the system back
 * gesture; 0 disables the check.
 * @param lockHorizontal true once the gesture commits to horizontal.
 * @param onLock first horizontal move past slop (slop already discounted).
 * @param onDelta claimed horizontal delta in px, applied by the caller
 * synchronously (no coroutine hop, no frame of lag).
 * @param onRelease finger lifted with the release velocity in px/s.
 * @param onCancel gesture aborted (cancellation, pointer vanishing):
 * bookkeeping plus a settle to the nearest anchor; cancellation rethrown.
 */
private suspend fun AwaitPointerEventScope.drawerDragGesture(
    slopPx: Float,
    ignoreEdgePx: Float,
    lockHorizontal: (totalX: Float, totalY: Float) -> Boolean,
    onLock: () -> Unit,
    onDelta: (dx: Float) -> Unit,
    onRelease: (velocityX: Float) -> Unit,
    onCancel: () -> Unit,
) {
    val down = awaitFirstDown(requireUnconsumed = false)
    if (ignoreEdgePx > 0f) {
        val x = down.position.x
        if (x < ignoreEdgePx || x > size.width - ignoreEdgePx) return
    }
    val tracker = VelocityTracker()
    tracker.addPosition(down.uptimeMillis, down.position)
    var totalX = 0f
    var totalY = 0f
    var locked = false
    var slopDropped = false
    try {
        while (true) {
            val event = awaitPointerEvent()
            val change: PointerInputChange? = event.changes.firstOrNull { it.id == down.id }
            if (change == null) {
                // Tracked pointer vanished (multi-touch lift edge case).
                if (event.changes.none { it.pressed }) {
                    if (locked) {
                        onRelease(0f)
                    }
                    return
                }
                continue
            }
            if (change.isConsumed) return
            if (!change.pressed) {
                if (!locked) return
                val v = runCatching { tracker.calculateVelocity().x }.getOrDefault(0f)
                change.consume()
                onRelease(v)
                return
            }
            val dx = change.position.x - change.previousPosition.x
            val dy = change.position.y - change.previousPosition.y
            tracker.addPosition(change.uptimeMillis, change.position)
            if (!locked) {
                totalX += dx
                totalY += dy
                if (abs(totalX) < slopPx && abs(totalY) < slopPx) continue
                if (!lockHorizontal(totalX, totalY)) return
                locked = true
                onLock()
            }
            change.consume()
            // First claimed move drops the slop distance: the surface is under
            // the finger on the very first frame, with no jump.
            val applied = if (!slopDropped) {
                slopDropped = true
                val sx = sign(totalX) * slopPx.coerceAtMost(abs(totalX))
                val sy = sign(totalY) * slopPx.coerceAtMost(abs(totalY))
                totalX -= sx
                totalY -= sy
                dx - sign(dx) * slopPx.coerceAtMost(abs(dx))
            } else {
                totalX += dx
                totalY += dy
                dx
            }
            onDelta(applied)
        }
    } catch (e: CancellationException) {
        if (locked) onCancel()
        throw e
    }
}

/**
 * Full-page sidebar scaffold with LastChat-style gestures.
 *
 * - The open detector wraps the content, so it observes pointer streams in
 *   the Main pass after children: lists, buttons and text selection keep
 *   priority via the consumed-check, and swipes work starting anywhere else,
 *   including the message list, the dock and the composer (but never inside
 *   an active text selection).
 * - Drag starts inside the system back-gesture edge bands are ignored on
 *   both edges (RTL-aware by symmetry), so the OS gesture always wins.
 * - One [progress] Animatable is the single settled-position source, with a
 *   plain [dragPos] float tracking the finger synchronously per delta
 *   (draw-scope reads only, no recomposition storm); releases spring from
 *   the release velocity after re-syncing.
 * - State leads the animation: `onOpenChange(target)` fires on release and
 *   the spring follows, so `open`, Back handling and the toggle are never
 *   stale mid-settle.
 */
@Composable
fun SwipeableSidebarScaffold(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    drawerWidth: Dp,
    gesturesEnabled: Boolean,
    onGestureStart: () -> Unit = {},
    drawerContent: @Composable () -> Unit,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val view = LocalView.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val direction = if (isRtl) -1f else 1f
    val widthPx = with(density) { drawerWidth.toPx() }
    val progress = remember { Animatable(0f) }
    // Finger position, written synchronously per delta (draw-scope reads only,
    // so no recomposition storm). The Animatable is for settling only.
    var dragPos by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf(false) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    // Anchor the in-flight settle heads for: LaunchedEffect skips while it
    // matches `open`, so the release spring and the state follower never run
    // two animations at once. Null when no settle owns the motion.
    var settlingTarget by remember { mutableStateOf<Boolean?>(null) }
    val shown = open || dragging || settling
    val springSpec = spring<Float>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)

    fun settleSpring(target01: Float, velocity01: Float, fromDrag: Boolean = false) {
        settleJob?.cancel()
        settling = true
        settlingTarget = target01 >= 1f
        val launched = scope.launch {
            try {
                progress.stop()
                // Re-sync after a drag: a settling spring cancelled mid-flight
                // may have left progress behind the finger. Never snap on a
                // programmatic settle (dragPos is stale then).
                if (fromDrag) progress.snapTo(dragPos)
                progress.animateTo(target01, springSpec, initialVelocity = velocity01.coerceIn(-8f, 8f))
            } finally {
                // A superseding settle replaces settleJob first; only the
                // latest job may clear the flags.
                if (settleJob === coroutineContext[Job]) {
                    settling = false
                    settlingTarget = null
                }
            }
        }
        settleJob = launched
    }

    /** Release: compute the anchor, publish state first, animate after. */
    fun release(velocityPx: Float) {
        val velocityDp = (velocityPx * direction) / density.density
        val target = resolveDrawerTarget(dragPos, velocityDp)
        dragging = false
        onOpenChange(target)
        val velocity01 = (velocityPx * direction / widthPx.coerceAtLeast(1f)).coerceIn(-8f, 8f)
        settleSpring(if (target) 1f else 0f, velocity01, fromDrag = true)
    }

    /** Aborted gesture: nearest anchor, never stuck dragging. */
    fun cancelSettle() {
        val target = dragPos > 0.5f
        dragging = false
        onOpenChange(target)
        settleSpring(if (target) 1f else 0f, 0f, fromDrag = true)
    }

    /** First horizontal move past slop: snapshot position, stop any spring. */
    fun beginDrag() {
        dragPos = progress.value
        dragging = true
        settleJob?.cancel()
        settling = false
        settlingTarget = null
        scope.launch { progress.stop() }
    }

    // External state changes (hamburger, scrim tap, Back, predictive back):
    // the spring follows state, so a mid-animation retarget just redirects.
    LaunchedEffect(open) {
        if (!dragging && settlingTarget != open &&
            abs(progress.value - (if (open) 1f else 0f)) > 0.001f
        ) {
            settleSpring(if (open) 1f else 0f, 0f)
        }
    }

    Box(Modifier.fillMaxSize()) {
        // ---- Content wrapped by the open detector (observes after children).
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (gesturesEnabled && !open) {
                        Modifier.pointerInput(gesturesEnabled, widthPx, direction) {
                            val slop = viewConfiguration.touchSlop
                            // System back-gesture edge bands, both edges: the OS
                            // gesture always wins. Insets are already px.
                            val system = runCatching {
                                ViewCompat.getRootWindowInsets(view)
                                    ?.getInsets(WindowInsetsCompat.Type.systemGestures())
                            }.getOrNull()
                            val ignoreEdgePx = maxOf(system?.left ?: 0, system?.right ?: 0, 0).toFloat()
                            awaitEachGesture {
                                drawerDragGesture(
                                    slopPx = slop,
                                    ignoreEdgePx = ignoreEdgePx.toFloat(),
                                    // Opening direction only; vertical-first aborts.
                                    lockHorizontal = { totalX, totalY ->
                                        abs(totalY) < abs(totalX) && totalX * direction > 0f
                                    },
                                    onLock = {
                                        beginDrag()
                                        onGestureStart()
                                    },
                                    onDelta = { dx ->
                                        dragPos =
                                            (dragPos + dx * direction / widthPx).coerceIn(0f, 1f)
                                    },
                                    onRelease = { velocityX -> release(velocityX) },
                                    onCancel = { cancelSettle() },
                                )
                            }
                        }
                    } else {
                        Modifier
                    },
                )
                // TalkBack must not land behind the scrim while the drawer is open.
                .then(if (open) Modifier.clearAndSetSemantics {} else Modifier),
        ) {
            content()
        }

        // ---- Scrim + close layer. Above chat (blocks its touches), below drawer.
        if (shown) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val pos = if (dragging) dragPos else progress.value
                        alpha = pos * WahariTokens.SCRIM_SIDEBAR
                    }
                    .background(Color.Black)
                    .testTag("drawer_scrim")
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "Close navigation",
                        onClick = { onOpenChange(false) },
                    )
                    .pointerInput(widthPx, direction, open) {
                        val slop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            drawerDragGesture(
                                slopPx = slop,
                                ignoreEdgePx = 0f,
                                lockHorizontal = { totalX, totalY -> abs(totalY) < abs(totalX) },
                                onLock = {
                                    beginDrag()
                                },
                                onDelta = { dx ->
                                    dragPos =
                                        (dragPos + dx * direction / widthPx).coerceIn(0f, 1f)
                                },
                                onRelease = { velocityX -> release(velocityX) },
                                onCancel = { cancelSettle() },
                            )
                        }
                    },
            )
        }

        // ---- Drawer, translated by progress read in the draw lambda.
        if (shown) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(drawerWidth)
                    .fillMaxSize()
                    .testTag("nav_drawer")
                    .graphicsLayer {
                        val w = drawerWidth.toPx()
                        val pos = if (dragging) dragPos else progress.value
                        translationX = -(1f - pos) * w * direction
                    }
                    .clip(RectangleShape)
                    .semantics {
                        paneTitle = "Navigation"
                        dismiss {
                            onOpenChange(false)
                            true
                        }
                    }
                    .pointerInput(widthPx, direction, open) {
                        val slop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            drawerDragGesture(
                                slopPx = slop,
                                ignoreEdgePx = 0f,
                                lockHorizontal = { totalX, totalY -> abs(totalY) < abs(totalX) },
                                onLock = {
                                    beginDrag()
                                },
                                onDelta = { dx ->
                                    dragPos =
                                        (dragPos + dx * direction / widthPx).coerceIn(0f, 1f)
                                },
                                onRelease = { velocityX -> release(velocityX) },
                                onCancel = { cancelSettle() },
                            )
                        }
                    },
            ) {
                drawerContent()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            PredictiveBackHandler(enabled = open && !dragging) {
                // Scrub the drawer with the back gesture; state already open.
                settleJob?.cancel()
                try {
                    progress.stop()
                } catch (_: CancellationException) {
                    // Stop was itself cancelled: fall through and restore below.
                }
                var completed = false
                try {
                    it.collect { event ->
                        progress.snapTo((1f - event.progress).coerceIn(0f, 1f))
                    }
                    completed = true
                } finally {
                    if (completed) {
                        onOpenChange(false)
                    } else {
                        // Gesture cancelled: scrub back open; state never changed.
                        settleSpring(1f, 0f)
                    }
                }
            }
        } else {
            BackHandler(enabled = open) {
                onOpenChange(false)
            }
        }
    }
}
