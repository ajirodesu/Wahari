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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
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

/** Hard cap for pasted/typed drafts: the model context stays predictable. */
internal const val COMPOSER_MAX_LENGTH = 4000

/** Single-line vs stacked decision and visible line count. Pure, unit-tested. */
internal data class ComposerLayout(val stacked: Boolean, val visibleLines: Int)

internal fun composerLayout(
    hasText: Boolean,
    hasNewline: Boolean,
    singleWidthDp: Float,
    inlineBudgetDp: Float,
    wrappedLines: Int,
    maxLines: Int,
): ComposerLayout {
    val stacked = hasNewline || (hasText && singleWidthDp > inlineBudgetDp)
    val effectiveMax = maxLines.coerceIn(2, 6)
    val visible = if (!stacked) 1 else wrappedLines.coerceIn(1, effectiveMax)
    return ComposerLayout(stacked, visible)
}

/** Truncates over-long drafts, keeping the caret inside the text. Pure, unit-tested. */
internal fun coerceComposerLength(value: TextFieldValue): TextFieldValue {
    if (value.text.length <= COMPOSER_MAX_LENGTH) return value
    val text = value.text.take(COMPOSER_MAX_LENGTH)
    val selection = TextRange(
        value.selection.start.coerceIn(0, text.length),
        value.selection.end.coerceIn(0, text.length),
    )
    return value.copy(text = text, selection = selection, composition = null)
}

/**
 * Wahari composer capsule. One [BasicTextField] instance is shared between Mode A
 * (single line, 48dp total) and Mode B (stacked, lines x 22 + 66) via movable content,
 * so text, caret, selection, focus and the keyboard survive every mode switch.
 *
 * @param windowWidth real window width; the mode threshold and the Mode B line
 * count always use the final active-state capsule width (window - 28dp),
 * never the width mid-animation.
 * @param capsuleWidth current capsule width (layout width only).
 * @param maxLines line cap after the short-window rule (2..6).
 */
@Composable
fun Composer(
    field: TextFieldValue,
    onFieldChange: (TextFieldValue) -> Unit,
    busy: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
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
    val keyboard = LocalSoftwareKeyboardController.current
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
    // Widths measured against the final active width, never the width
    // mid-animation: both the mode threshold and the Mode B line count use it,
    // so neither flickers while the dock slides.
    val activeCapsuleWidth = windowWidth - WahariLayout.dockSideActive * 2
    val inlineBudget = activeCapsuleWidth - WahariLayout.capsuleBorder - WahariLayout.modeAFixedContent
    val singleWidth = measurer.measure(text.replace('\n', ' '), style = inputStyle).size.width
    val singleWidthDp = with(density) { singleWidth.toDp() }
    val activeTextWidthPx = with(density) {
        (activeCapsuleWidth - WahariLayout.modeBTextOverhead).toPx()
    }.toInt().coerceAtLeast(10)

    val linePx = with(density) { WahariLayout.composerLine.toPx() }
    val wrappedLines = if (!text.contains('\n') && (text.isEmpty() || singleWidthDp <= inlineBudget)) {
        1
    } else {
        val probe = if (text.endsWith('\n')) "$text " else text.ifEmpty { " " }
        val h = measurer.measure(
            probe,
            style = stackedStyle,
            constraints = Constraints(maxWidth = activeTextWidthPx),
        ).size.height
        maxOf(1, (h / linePx).roundToInt())
    }
    val layout = composerLayout(
        hasText = text.isNotEmpty(),
        hasNewline = text.contains('\n'),
        singleWidthDp = singleWidthDp.value,
        inlineBudgetDp = inlineBudget.value,
        wrappedLines = wrappedLines,
        maxLines = maxLines,
    )
    val stacked = layout.stacked
    val visibleLines = layout.visibleLines
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
    // Every captured value goes through rememberUpdatedState: the movable
    // content keeps one BasicTextField instance across Mode A <-> B (caret,
    // selection, focus and IME state survive), while callbacks, styles and
    // the focus requester always stay current (density, font scale, caller).
    val latestField by rememberUpdatedState(field)
    val latestSend by rememberUpdatedState(onSend)
    val latestChange by rememberUpdatedState(onFieldChange)
    val latestStacked by rememberUpdatedState(stacked)
    val latestFocusRequester by rememberUpdatedState(focusRequester)
    val latestFocusChanged by rememberUpdatedState(onFocusedChange)
    val latestInputStyle by rememberUpdatedState(inputStyle)
    val latestStackedStyle by rememberUpdatedState(stackedStyle)
    val keyHandler = Modifier.onPreviewKeyEvent { event ->
        if (event.key == Key.Enter) {
            // KeyDown only, key repeat ignored, so a held Enter cannot send twice.
            val native = event.nativeKeyEvent
            if (native.action == AndroidKeyEvent.ACTION_DOWN && native.repeatCount == 0) {
                if (event.isShiftPressed) {
                    // Shift+Enter inserts a newline replacing the selection.
                    val cur = latestField
                    val start = minOf(cur.selection.start, cur.selection.end).coerceIn(0, cur.text.length)
                    val end = maxOf(cur.selection.start, cur.selection.end).coerceIn(0, cur.text.length)
                    val updated = cur.text.substring(0, start) + "\n" + cur.text.substring(end)
                    latestChange(
                        coerceComposerLength(
                            cur.copy(
                                text = updated,
                                selection = androidx.compose.ui.text.TextRange(start + 1),
                            ),
                        ),
                    )
                } else {
                    // Hardware Enter sends; the soft keyboard always inserts a
                    // newline (ImeAction.Default below) and sending is the button.
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
    val latestKeyHandler by rememberUpdatedState(keyHandler)
    val latestMaxLines by rememberUpdatedState(maxLines.coerceIn(2, 6))
    // Caret top in px, refreshed on every text layout; the follow effect
    // below keeps it inside the viewport without animation while typing.
    var caretTopPx by remember { mutableFloatStateOf(0f) }

    val textField = remember {
        movableContentOf {
            BasicTextField(
                value = latestField,
                onValueChange = { latestChange(coerceComposerLength(it)) },
                modifier = Modifier
                    .testTag("composer_field")
                    .focusRequester(latestFocusRequester)
                    .onFocusChanged { latestFocusChanged(it.isFocused) }
                    .semantics { contentDescription = "Ask Wahari" }
                    .then(latestKeyHandler),
                textStyle = if (latestStacked) latestStackedStyle else latestInputStyle,
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    autoCorrectEnabled = true,
                    keyboardType = KeyboardType.Text,
                    // Soft Enter inserts a newline; Send travels via the button
                    // (or hardware Enter). Never toggled, so the IME connection
                    // survives every Mode A <-> B switch.
                    imeAction = ImeAction.Default,
                ),
                // Always multi-line: a soft Enter in Mode A flips to Mode B in
                // the same frame instead of being swallowed.
                singleLine = false,
                maxLines = latestMaxLines,
                decorationBox = { inner ->
                    Box {
                        if (latestField.text.isEmpty()) {
                            Text(
                                "Ask Wahari",
                                style = (if (latestStacked) latestStackedStyle else latestInputStyle)
                                    .copy(color = WahariTokens.textPlaceholder),
                            )
                        }
                        inner()
                    }
                },
                onTextLayout = { layoutResult ->
                    val cursor = latestField.selection.start.coerceIn(0, latestField.text.length)
                    caretTopPx = layoutResult.getCursorRect(cursor).top
                },
            )
        }
    }

    val modeBScrollState = rememberScrollState()
    // Keep the caret visible while typing: instant scroll, no animation, and
    // only when the caret actually left the viewport (editing an earlier line
    // never yanks the view to the bottom).
    LaunchedEffect(caretTopPx, visibleLines) {
        if (!stacked) return@LaunchedEffect
        val viewportH = visibleLines * linePx
        val viewTop = modeBScrollState.value.toFloat()
        when {
            caretTopPx < viewTop -> modeBScrollState.scrollTo(caretTopPx.toInt().coerceAtLeast(0))
            caretTopPx + linePx > viewTop + viewportH ->
                modeBScrollState.scrollTo((caretTopPx + linePx - viewportH).toInt().coerceAtLeast(0))
        }
    }

    Box(
        modifier = modifier
            .testTag("composer")
            .width(capsuleWidth)
            .height(animatedH)
            // No .clip: the background keeps the rounded shape, but clipping
            // the content would cut off text selection handles. The border
            // below still draws rounded.
            .background(WahariTokens.bgCapsule, WahariTokens.capsuleShape)
            .border(1.dp, WahariTokens.borderCapsule, WahariTokens.capsuleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    focusRequester.requestFocus()
                    // Focused but IME dismissed via Back: requesting focus
                    // alone reopens nothing, so ask the keyboard explicitly.
                    keyboard?.show()
                },
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
                // Grid hit area: 48 wide, icon visually where the 44dp box put it.
                CapsuleHitButton(width = 48.dp, onClick = onGrid, label = "Agent Mode", iconOffsetStart = 10.dp, testTag = "composer_grid") {
                    Icon(WahariIcons.layoutGrid, contentDescription = "Agent Mode", tint = Color.White, modifier = Modifier.size(WahariLayout.capsuleIcon))
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(WahariLayout.composerLine),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    textField()
                }
                // Mic hit area: 48 wide, icon visually where the 36dp box put it.
                CapsuleHitButton(width = 48.dp, onClick = onMic, label = "Voice input", iconOffsetStart = 0.dp, testTag = "composer_mic") {
                    Icon(WahariIcons.mic, contentDescription = "Voice input", tint = Color.White, modifier = Modifier.size(WahariLayout.capsuleIcon))
                }
                PrimaryCircleButton(
                    mode = when {
                        busy -> CircleMode.Stop
                        hasContent -> CircleMode.Send
                        else -> CircleMode.Voice
                    },
                    onClick = {
                        when {
                            busy -> onStop()
                            hasContent -> latestSend()
                            else -> onEmptyPrimary()
                        }
                    },
                    onClickLabel = when {
                        busy -> "Stop"
                        hasContent -> "Send"
                        else -> "Voice mode"
                    },
                    testTag = "composer_primary",
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
                        .height(viewportH.dp)
                        .verticalScroll(modeBScrollState),
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
                    CapsuleHitButton(width = 48.dp, height = WahariLayout.toolbarHeight, onClick = onGrid, label = "Agent Mode", iconOffsetStart = 0.dp, testTag = "composer_grid") {
                        Icon(WahariIcons.layoutGrid, contentDescription = "Agent Mode", tint = Color.White, modifier = Modifier.size(WahariLayout.capsuleIcon))
                    }
                    Spacer(Modifier.weight(1f))
                    CapsuleHitButton(width = 48.dp, height = WahariLayout.toolbarHeight, onClick = onMic, label = "Voice input", iconOffsetStart = 3.dp, testTag = "composer_mic") {
                        Icon(WahariIcons.mic, contentDescription = "Voice input", tint = Color.White, modifier = Modifier.size(WahariLayout.capsuleIcon))
                    }
                    Spacer(Modifier.width(8.dp))
                    // Arrow normally; stop glyph while generating (tap cancels),
                    // and a blank-text tap does nothing.
                    PrimaryCircleButton(
                        mode = if (busy) CircleMode.Stop else CircleMode.Send,
                        hitHeight = WahariLayout.toolbarHeight,
                        onClick = {
                            if (busy) {
                                onStop()
                            } else if (text.isNotBlank()) {
                                latestSend()
                            }
                        },
                        onClickLabel = if (busy) "Stop" else "Send",
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
    testTag: String? = null,
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
            .then(if (testTag == null) Modifier else Modifier.testTag(testTag))
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
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
    mode: CircleMode,
    onClick: () -> Unit,
    onClickLabel: String,
    hitHeight: Dp = WahariLayout.capsuleHeight,
    testTag: String = "composer_primary",
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = animateFloatAsState(
        if (pressed) 0.92f else 1f,
        animationSpec = tween(WahariTokens.PRESS_MS, easing = WahariTokens.pressEasing),
        label = "send",
    ).value
    // Voice <-> send crossfade, with the stop glyph layered over both while busy.
    val shown: Float by animateFloatAsState(
        if (mode == CircleMode.Send) 1f else 0f,
        animationSpec = tween(WahariTokens.GLYPH_FADE_MS, easing = WahariTokens.pressEasing),
        label = "glyph",
    )
    val stopping: Float by animateFloatAsState(
        if (mode == CircleMode.Stop) 1f else 0f,
        animationSpec = tween(WahariTokens.GLYPH_FADE_MS, easing = WahariTokens.pressEasing),
        label = "stop",
    )
    // Hit area meets the 48dp minimum; the 36dp circle stays glued to the
    // trailing edge so the visual does not move.
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = hitHeight)
            .testTag(testTag)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = onClickLabel,
                onClick = onClick,
            ),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Box(
            modifier = Modifier
                .size(WahariLayout.sendCircle)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (pressed) WahariTokens.accentPressed else WahariTokens.accent),
            contentAlignment = Alignment.Center,
        ) {
            // Glyphs share the clipped circle; inactive ones never show mid-state
            // beyond the crossfade and never intercept touches (no clickables inside).
            Box(
                modifier = Modifier.graphicsLayer {
                    alpha = (1f - shown) * (1f - stopping)
                    val s = 1f - 0.4f * shown
                    scaleX = s
                    scaleY = s
                    translationY = -4.dp.toPx() * shown
                },
            ) {
                Icon(WahariIcons.audioLines, contentDescription = "Voice mode", tint = Color.White, modifier = Modifier.size(WahariLayout.sendGlyph))
            }
            Box(
                modifier = Modifier.graphicsLayer {
                    alpha = shown * (1f - stopping)
                    val s = 0.6f + 0.4f * shown
                    scaleX = s
                    scaleY = s
                    translationY = 4.dp.toPx() * (1f - shown)
                },
            ) {
                Icon(WahariIcons.arrowUp, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(WahariLayout.sendGlyph))
            }
            Box(
                modifier = Modifier.graphicsLayer { alpha = stopping },
            ) {
                Icon(WahariIcons.square, contentDescription = "Stop", tint = Color.White, modifier = Modifier.size(WahariLayout.sendGlyph))
            }
        }
    }
}

/** Primary circle states: dictate entry, send, or cancel the in-flight turn. */
enum class CircleMode { Voice, Send, Stop }


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
                onStop = {},
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
                onStop = {},
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
                onStop = {},
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
                onStop = {},
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
