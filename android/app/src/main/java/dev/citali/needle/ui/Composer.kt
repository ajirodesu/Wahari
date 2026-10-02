package dev.citali.needle.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.citali.needle.ui.theme.Geist
import dev.citali.needle.ui.theme.WahariIcons
import dev.citali.needle.ui.theme.WahariLayout
import dev.citali.needle.ui.theme.WahariTokens
import kotlin.math.roundToInt

/**
 * Wahari composer capsule. One [BasicTextField] instance is shared between Mode A
 * (single line, 44dp total) and Mode B (stacked, lines x 22 + 62) via movable content,
 * so text, caret, selection, focus and the keyboard survive every mode switch.
 *
 * @param windowWidth real window width; the mode threshold always uses the final
 * active-state capsule width (window - 28dp), never the width mid-animation.
 * @param capsuleWidth current capsule width (drives the Mode B text width).
 * @param maxLines line cap after the short-window rule (2..6).
 */
@Composable
fun Composer(
    field: TextFieldValue,
    onFieldChange: (TextFieldValue) -> Unit,
    busy: Boolean,
    onSend: () -> Unit,
    onMic: () -> Unit,
    onGrid: () -> Unit,
    onEmptyPrimary: () -> Unit,
    focusRequester: FocusRequester,
    onFocusedChange: (Boolean) -> Unit,
    windowWidth: Dp,
    capsuleWidth: Dp,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    // 15sp text on a 22dp line, derived from dp so the row math is exact at any font scale.
    val inputStyle = TextStyle(
        fontFamily = Geist,
        fontSize = with(density) { 15.dp.toSp() },
        lineHeight = with(density) { WahariLayout.composerLine.toSp() },
        letterSpacing = (-0.15).sp,
        color = Color.White,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        ),
    )
    val stackedStyle = inputStyle.copy(color = WahariTokens.textPrimary)

    val text = field.text
    // Threshold measured against the final active width, not the width mid-animation.
    val activeCapsuleWidth = windowWidth - WahariLayout.dockSideActive * 2
    val inlineBudget = activeCapsuleWidth - WahariLayout.capsuleBorder - WahariLayout.modeAFixedContent
    val singleWidth = measurer.measure(text.replace('\n', ' '), style = inputStyle).size.width
    val singleWidthDp = with(density) { singleWidth.toDp() }
    val stacked = text.contains('\n') || (text.isNotEmpty() && singleWidthDp > inlineBudget)

    val linePx = with(density) { WahariLayout.composerLine.toPx() }
    val textWidthPx = with(density) { (capsuleWidth - WahariLayout.modeBTextOverhead).toPx() }
        .toInt().coerceAtLeast(10)
    val measuredLines = if (!stacked) {
        1
    } else {
        val probe = if (text.endsWith('\n')) "$text " else text.ifEmpty { " " }
        val h = measurer.measure(
            probe,
            style = stackedStyle,
            constraints = Constraints(maxWidth = textWidthPx),
        ).size.height
        maxOf(1, (h / linePx).roundToInt())
    }
    val visibleLines = if (!stacked) 1 else measuredLines.coerceIn(2, maxLines.coerceIn(2, 6))
    val capsuleHeight: Dp = if (!stacked) {
        WahariLayout.capsuleHeight
    } else {
        (visibleLines * 22).dp + WahariLayout.modeBChrome
    }
    val animatedH by animateDpAsState(
        targetValue = capsuleHeight,
        animationSpec = tween(durationMillis = WahariTokens.CAPSULE_MS, easing = WahariTokens.mainEasing),
        label = "capsule",
    )

    val hasContent = text.isNotBlank()
    val latestField by rememberUpdatedState(field)
    val latestSend by rememberUpdatedState(onSend)
    val latestChange by rememberUpdatedState(onFieldChange)
    val keyHandler = Modifier.onPreviewKeyEvent { event ->
        if (event.key == Key.Enter) {
            // KeyDown only, key repeat ignored, so a held Enter cannot send twice.
            val native = event.nativeKeyEvent
            if (native.action == AndroidKeyEvent.ACTION_DOWN && native.repeatCount == 0) {
                if (event.isShiftPressed) {
                    // Shift+Enter inserts a newline replacing the selection (single-line
                    // fields would otherwise swallow it, stranding the user in Mode A).
                    val cur = latestField
                    val start = minOf(cur.selection.start, cur.selection.end).coerceIn(0, cur.text.length)
                    val end = maxOf(cur.selection.start, cur.selection.end).coerceIn(0, cur.text.length)
                    val updated = cur.text.substring(0, start) + "\n" + cur.text.substring(end)
                    latestChange(
                        cur.copy(
                            text = updated,
                            selection = androidx.compose.ui.text.TextRange(start + 1),
                        ),
                    )
                } else {
                    latestSend()
                }
                true
            } else {
                false
            }
        } else {
            false
        }
    }

    val textField = remember {
        movableContentOf {
            BasicTextField(
                value = latestField,
                onValueChange = latestChange,
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .onFocusChanged { onFocusedChange(it.isFocused) }
                    .then(keyHandler),
                textStyle = if (stacked) stackedStyle else inputStyle,
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { latestSend() }),
                singleLine = !stacked,
                decorationBox = { inner ->
                    Box {
                        if (latestField.text.isEmpty()) {
                            Text(
                                "Ask Wahari",
                                style = (if (stacked) stackedStyle else inputStyle)
                                    .copy(color = WahariTokens.textPlaceholder),
                            )
                        }
                        inner()
                    }
                },
            )
        }
    }

    Box(
        modifier = modifier
            .width(capsuleWidth)
            .height(animatedH)
            .clip(WahariTokens.capsuleShape)
            .background(WahariTokens.bgCapsule)
            .border(1.dp, WahariTokens.borderCapsule, WahariTokens.capsuleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { focusRequester.requestFocus() },
            ),
    ) {
        if (!stacked) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WahariLayout.capsuleHeight)
                    .padding(start = 4.dp, end = WahariLayout.modeARightPad),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Grid hit area: 10 + 24 + 10 = 44 wide; icon visually at 10 + 24 box.
                CapsuleHitButton(width = 44.dp, onClick = onGrid, label = "Agent Mode") {
                    Icon(WahariIcons.layoutGrid, contentDescription = null, tint = Color.White, modifier = Modifier.size(WahariLayout.capsuleIcon))
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(WahariLayout.composerLine),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    textField()
                }
                // Mic hit area: 4 + 24 + 8 = 36 wide.
                CapsuleHitButton(width = 36.dp, onClick = onMic, label = "Voice input", iconOffsetStart = 4.dp) {
                    Icon(WahariIcons.mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(WahariLayout.capsuleIcon))
                }
                PrimaryCircleButton(
                    hasContent = hasContent,
                    busy = busy,
                    onClick = { if (hasContent) latestSend() else onEmptyPrimary() },
                )
            }
        } else {
            val viewportH = visibleLines * 22
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 8.dp,
                        end = WahariLayout.modeBRightPad,
                        top = WahariLayout.modeBTopPad,
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(viewportH.dp),
                ) {
                    textField()
                }
                Spacer(Modifier.height(WahariLayout.modeBToolbarGap))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WahariLayout.toolbarHeight),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CapsuleHitButton(width = 24.dp, height = WahariLayout.toolbarHeight, onClick = onGrid, label = "Agent Mode") {
                        Icon(WahariIcons.layoutGrid, contentDescription = null, tint = Color.White, modifier = Modifier.size(WahariLayout.capsuleIcon))
                    }
                    Spacer(Modifier.weight(1f))
                    CapsuleHitButton(width = 30.dp, height = WahariLayout.toolbarHeight, onClick = onMic, label = "Voice input") {
                        Icon(WahariIcons.mic, contentDescription = null, tint = Color.White, modifier = Modifier.size(WahariLayout.capsuleIcon))
                    }
                    Spacer(Modifier.width(8.dp))
                    // Always the arrow in Mode B; a tap with blank text does nothing.
                    PrimaryCircleButton(
                        hasContent = true,
                        busy = busy,
                        onClick = { if (text.isNotBlank()) latestSend() },
                    )
                }
            }
        }
    }
}

/**
 * Capsule icon button. The hit area tiles exactly against its neighbors (no overlap)
 * and is clipped by the capsule; the visual icon keeps its HTML position.
 */
@Composable
private fun CapsuleHitButton(
    width: Dp,
    onClick: () -> Unit,
    label: String,
    height: Dp = WahariLayout.capsuleHeight,
    iconOffsetStart: Dp? = null,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = animateFloatAsState(
        if (pressed) 0.9f else 1f,
        animationSpec = tween(WahariTokens.PRESS_MS, easing = WahariTokens.pressEasing),
        label = "capsuleBtn",
    ).value
    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = label,
                onClick = onClick,
            ),
        contentAlignment = if (iconOffsetStart == null) Alignment.Center else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .size(WahariLayout.iconBox)
                .then(if (iconOffsetStart == null) Modifier else Modifier.padding(start = iconOffsetStart)),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
fun PrimaryCircleButton(
    hasContent: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = animateFloatAsState(
        if (pressed) 0.92f else 1f,
        animationSpec = tween(WahariTokens.PRESS_MS, easing = WahariTokens.pressEasing),
        label = "send",
    ).value
    val shown by animateFloatAsState(
        if (hasContent) 1f else 0f,
        animationSpec = tween(WahariTokens.GLYPH_FADE_MS, easing = WahariTokens.pressEasing),
        label = "glyph",
    )
    Box(
        modifier = Modifier
            .size(WahariLayout.sendCircle)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (pressed) WahariTokens.accentPressed else WahariTokens.accent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Glyphs share the clipped 32dp circle; the inactive one never shows mid-state
        // beyond the crossfade and never intercepts touches (no clickables inside).
        Box(
            modifier = Modifier.graphicsLayer {
                alpha = 1f - shown
                val s = 1f - 0.4f * shown
                scaleX = s
                scaleY = s
                translationY = -4.dp.toPx() * shown
            },
        ) {
            Icon(WahariIcons.audioLines, contentDescription = null, tint = Color.White, modifier = Modifier.size(WahariLayout.sendGlyph))
        }
        Box(
            modifier = Modifier.graphicsLayer {
                alpha = shown
                val s = 0.6f + 0.4f * shown
                scaleX = s
                scaleY = s
                translationY = 4.dp.toPx() * (1f - shown)
            },
        ) {
            Icon(WahariIcons.arrowUp, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(WahariLayout.sendGlyph))
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Capsule Mode A", widthDp = 412, heightDp = 200)
@Composable
private fun PreviewCapsuleA() {
    dev.citali.needle.ui.theme.NeedleTheme {
        androidx.compose.foundation.layout.Box(
            Modifier
                .fillMaxWidth()
                .background(WahariTokens.bgMain)
                .padding(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Composer(
                field = TextFieldValue(""),
                onFieldChange = {},
                busy = false,
                onSend = {},
                onMic = {},
                onGrid = {},
                onEmptyPrimary = {},
                focusRequester = remember { FocusRequester() },
                onFocusedChange = {},
                windowWidth = 412.dp,
                capsuleWidth = 412.dp - 68.dp,
                maxLines = 6,
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Capsule Mode A active", widthDp = 412, heightDp = 200)
@Composable
private fun PreviewCapsuleAActive() {
    dev.citali.needle.ui.theme.NeedleTheme {
        androidx.compose.foundation.layout.Box(
            Modifier
                .fillMaxWidth()
                .background(WahariTokens.bgMain)
                .padding(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Composer(
                field = TextFieldValue("turn on the flashlight"),
                onFieldChange = {},
                busy = false,
                onSend = {},
                onMic = {},
                onGrid = {},
                onEmptyPrimary = {},
                focusRequester = remember { FocusRequester() },
                onFocusedChange = {},
                windowWidth = 412.dp,
                capsuleWidth = 412.dp - 28.dp,
                maxLines = 6,
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Capsule Mode B 3 lines", widthDp = 412, heightDp = 300)
@Composable
private fun PreviewCapsuleB3() {
    dev.citali.needle.ui.theme.NeedleTheme {
        androidx.compose.foundation.layout.Box(
            Modifier
                .fillMaxWidth()
                .background(WahariTokens.bgMain)
                .padding(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Composer(
                field = TextFieldValue("Line one is fairly long text here\nLine two\nLine three"),
                onFieldChange = {},
                busy = false,
                onSend = {},
                onMic = {},
                onGrid = {},
                onEmptyPrimary = {},
                focusRequester = remember { FocusRequester() },
                onFocusedChange = {},
                windowWidth = 412.dp,
                capsuleWidth = 412.dp - 28.dp,
                maxLines = 6,
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Capsule Mode B 6+ lines", widthDp = 412, heightDp = 400)
@Composable
private fun PreviewCapsuleB6() {
    dev.citali.needle.ui.theme.NeedleTheme {
        androidx.compose.foundation.layout.Box(
            Modifier
                .fillMaxWidth()
                .background(WahariTokens.bgMain)
                .padding(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Composer(
                field = TextFieldValue((1..8).joinToString("\n") { "Line $it with enough words to wrap around" }),
                onFieldChange = {},
                busy = false,
                onSend = {},
                onMic = {},
                onGrid = {},
                onEmptyPrimary = {},
                focusRequester = remember { FocusRequester() },
                onFocusedChange = {},
                windowWidth = 412.dp,
                capsuleWidth = 412.dp - 28.dp,
                maxLines = 6,
            )
        }
    }
}
