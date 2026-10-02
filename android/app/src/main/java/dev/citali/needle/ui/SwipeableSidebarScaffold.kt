package dev.citali.needle.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import dev.citali.needle.ui.theme.WahariTokens
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Full-page sidebar scaffold with LastChat-style gestures.
 *
 * - Swipe RIGHT from anywhere on the page opens the drawer (children with
 *   horizontal scrolling or text selection keep priority: the detector aborts
 *   as soon as a child consumes the stream).
 * - Swipe LEFT anywhere closes it. Scrim tap and system Back close it too.
 * - One [progress] source (0f..1f) drives the drawer offset and the scrim
 *   alpha, both read inside draw-scope lambdas so dragging never recomposes.
 * - Release settles with a spring starting from the release velocity.
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
    var dragging by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf(false) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val shown = open || dragging || settling

    fun flingThresholdPx(): Float =
        with(density) { 1000.dp.toPx() }



    /** Release with velocity: spring from the current position, then sync state. */
    fun releaseSettle(velocityPx: Float) {
        android.util.Log.d("DrawerDbg", "releaseSettle vPx=$velocityPx prog=${progress.value}")
        val v = velocityPx * direction
        val threshold = flingThresholdPx()
        val target = when {
            v > threshold -> true
            v < -threshold -> false
            else -> progress.value > 0.5f
        }
        dragging = false
        settling = true
        settleJob?.cancel()
        settleJob = scope.launch {
            val vv = (velocityPx / widthPx.coerceAtLeast(1f)).coerceIn(-8f, 8f)
            progress.animateTo(
                if (target) 1f else 0f,
                spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                initialVelocity = vv,
            )
            settling = false
            onOpenChange(target)
        }
    }

    // External state changes (hamburger, scrim tap, Back): tween-free spring.
    LaunchedEffect(open) {
        if (!dragging && abs(progress.value - (if (open) 1f else 0f)) > 0.001f) {
            settleJob?.cancel()
            settleJob = scope.launch {
                progress.animateTo(
                    if (open) 1f else 0f,
                    spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                )
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        content()

        // ---- Open detector: full page, above content, consumes only after a
        // ---- clearly-horizontal slop; children keep priority via consumed-check.
        if (gesturesEnabled && !open) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(gesturesEnabled, widthPx, direction) {
                        // Coexist with Android's predictive back: never claim the
                        // outermost edge band reserved for the system gesture.
                        ViewCompat.setSystemGestureExclusionRects(
                            view,
                            listOf(
                                android.graphics.Rect(
                                    0, 0,
                                    (36.dp.toPx()).toInt(), size.height,
                                ),
                            ),
                        )
                        val slop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            var totalX = 0f
                            var totalY = 0f
                            var locked = false
                            var started = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: continue
                                    if (change.isConsumed) return@awaitEachGesture
                                    if (!change.pressed) {
                                        if (!locked) {
                                            return@awaitEachGesture
                                        }
                                        if (locked) {
                                            val v = runCatching { tracker.calculateVelocity().x }
                                                .getOrDefault(0f)
                                            releaseSettle(v)
                                        }
                                        change.consume()
                                        return@awaitEachGesture
                                    }
                                    val dx = change.position.x - change.previousPosition.x
                                    val dy = change.position.y - change.previousPosition.y
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    if (!locked) {
                                        totalX += dx
                                        totalY += dy
                                        val ax = abs(totalX)
                                        val ay = abs(totalY)
                                        if (ax < slop && ay < slop) continue
                                        // Axis lock: vertical-first aborts, horizontal takes over.
                                        if (ay >= ax || totalX * direction <= 0f) {
                                            return@awaitEachGesture
                                        }
                                        locked = true
                                        dragging = true
                                        settleJob?.cancel()
                                        scope.launch { progress.stop() }
                                        onGestureStart()
                                    }
                                    if (locked) {
                                        change.consume()
                                        // Start from the slop-adjusted position: no jump.
                                        if (!started) {
                                            started = true
                                            totalX -= sign(totalX) * slop
                                        } else {
                                            totalX += dx
                                        }
                                        scope.launch {
                                            progress.snapTo(
                                                (totalX * direction / widthPx).coerceIn(0f, 1f),
                                            )
                                        }
                                    }
                                }
                            } catch (_: Exception) {
                                // Cancelled mid-drag: settle to the nearest anchor.
                                if (locked) {
                                    releaseSettle(0f)
                                }
                            }
                        }
                    },
            )
        }

        // ---- Scrim + close layer. Above chat (blocks its touches), below drawer.
        if (shown) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = progress.value * WahariTokens.SCRIM_SIDEBAR }
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onOpenChange(false) },
                    )
                    .pointerInput(widthPx, direction) {
                        val slop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            var totalX = 0f
                            var totalY = 0f
                            var locked = false
                            var started = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: continue
                                    if (change.isConsumed) return@awaitEachGesture
                                    if (!change.pressed) {
                                        if (!locked) {
                                            return@awaitEachGesture
                                        }
                                        if (locked) {
                                            val v = runCatching { tracker.calculateVelocity().x }
                                                .getOrDefault(0f)
                                            releaseSettle(v)
                                        }
                                        change.consume()
                                        return@awaitEachGesture
                                    }
                                    val dx = change.position.x - change.previousPosition.x
                                    val dy = change.position.y - change.previousPosition.y
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    if (!locked) {
                                        totalX += dx
                                        totalY += dy
                                        if (abs(totalX) < slop && abs(totalY) < slop) {
                                            continue
                                        }
                                        if (abs(totalY) >= abs(totalX)) return@awaitEachGesture
                                        locked = true
                                        dragging = true
                                        settleJob?.cancel()
                                        scope.launch { progress.stop() }
                                    }
                                    if (locked) {
                                        change.consume()
                                        if (!started) {
                                            started = true
                                            totalX -= sign(totalX) * slop
                                        } else {
                                            totalX += dx
                                        }
                                        val base = if (open) 1f else progress.value
                                        scope.launch {
                                            progress.snapTo(
                                                (base + totalX * direction / widthPx).coerceIn(0f, 1f),
                                            )
                                        }
                                    }
                                }
                            } catch (_: Exception) {
                                if (locked) {
                                    releaseSettle(0f)
                                }
                            }
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
                    .graphicsLayer {
                        val w = drawerWidth.toPx()
                        translationX = -(1f - progress.value) * w * direction
                    }
                    .clip(RectangleShape)
                    .pointerInput(widthPx, direction) {
                        // Leftward drags starting on the drawer close it;
                        // vertical scrolls and taps pass through untouched.
                        val slop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            var totalX = 0f
                            var totalY = 0f
                            var locked = false
                            var started = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: continue
                                    if (change.isConsumed) return@awaitEachGesture
                                    if (!change.pressed) {
                                        if (!locked) {
                                            return@awaitEachGesture
                                        }
                                        if (locked) {
                                            val v = runCatching { tracker.calculateVelocity().x }
                                                .getOrDefault(0f)
                                            releaseSettle(v)
                                        }
                                        change.consume()
                                        return@awaitEachGesture
                                    }
                                    val dx = change.position.x - change.previousPosition.x
                                    val dy = change.position.y - change.previousPosition.y
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    if (!locked) {
                                        totalX += dx
                                        totalY += dy
                                        if (abs(totalX) < slop && abs(totalY) < slop) {
                                            continue
                                        }
                                        if (abs(totalY) >= abs(totalX)) return@awaitEachGesture
                                        locked = true
                                        dragging = true
                                        settleJob?.cancel()
                                        scope.launch { progress.stop() }
                                    }
                                    if (locked) {
                                        change.consume()
                                        if (!started) {
                                            started = true
                                            totalX -= sign(totalX) * slop
                                        } else {
                                            totalX += dx
                                        }
                                        scope.launch {
                                            progress.snapTo(
                                                (1f + totalX * direction / widthPx).coerceIn(0f, 1f),
                                            )
                                        }
                                    }
                                }
                            } catch (_: Exception) {
                                if (locked) {
                                    releaseSettle(0f)
                                }
                            }
                        }
                    },
            ) {
                drawerContent()
            }
        }

        BackHandler(enabled = open) {
            onOpenChange(false)
        }
    }
}
