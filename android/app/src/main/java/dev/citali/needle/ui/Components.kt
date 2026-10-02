package dev.citali.needle.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.citali.needle.ui.theme.WahariLayout
import dev.citali.needle.ui.theme.WahariTokens
import dev.citali.needle.ui.theme.WahariTypography

/** Press feedback uses scale only, never the default ink ring. */
fun Modifier.wahariPress(scale: Float = 0.96f): Modifier = composedPress(this, scale)

private fun composedPress(modifier: Modifier, scale: Float): Modifier {
    return modifier.graphicsLayer { scaleX = 1f; scaleY = 1f }
}

@Composable
private fun pressScale(interactionSource: MutableInteractionSource, scale: Float): Float {
    val pressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (pressed) scale else 1f,
        animationSpec = androidx.compose.animation.core.TweenSpec(durationMillis = 140),
        label = "press",
    ).value
}

/** A titled card used all over the app. */
@Composable
fun Section(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(WahariTokens.bgSurface)
            .border(1.dp, WahariTokens.borderSubtle, RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = WahariTypography.sectionTitle)
            subtitle?.let { Text(it, style = WahariTypography.sectionSubtitle) }
        }
        content()
    }
}

@Composable
fun KeyValue(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = WahariTypography.assistantBullet.copy(fontSize = androidx.compose.ui.unit.TextUnit.Unspecified),
            color = WahariTokens.textMuted,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            softWrap = true,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = WahariTypography.assistantBullet,
            color = WahariTokens.textPrimary,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            softWrap = true,
        )
    }
}

@Composable
fun ToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = WahariTypography.assistantBody.copy(lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified, color = Color.White))
            subtitle?.let {
                Text(it, style = WahariTypography.sectionSubtitle)
            }
        }
        WahariSwitch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun WahariSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val thumbOffset by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
        label = "switch",
    )
    val alpha = if (enabled) 1f else 0.4f
    Box(
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
            .size(width = 44.dp, height = 26.dp)
            .clip(CircleShape)
            .background(if (checked) WahariTokens.accent else WahariTokens.bgIcon)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = { onCheckedChange(!checked) },
            )
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset { androidx.compose.ui.unit.IntOffset((thumbOffset * (18.dp.toPx())).toInt(), 0) }
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActionRow(spacing: Int = 8, content: @Composable () -> Unit) {
    // FlowRow wraps buttons to the next line on narrow screens instead of
    // overflowing or clipping them. No caller passes weighted children.
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.dp),
    ) { content() }
}

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction, 0.96f)
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(
                when {
                    !enabled -> WahariTokens.bgIcon
                    pressed -> WahariTokens.accentPressed
                    else -> WahariTokens.accent
                },
            )
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp)
            .heightIn(min = 44.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = WahariTypography.assistantBody.copy(
                fontSize = 15.sp, lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                color = if (enabled) Color.White else WahariTokens.buttonDisabledText,
            ),
            maxLines = 2,
        )
    }
}

@Composable
fun SecondaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction, 0.96f)
    val alpha = if (enabled) 1f else 0.4f
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .clip(CircleShape)
            .background(if (pressed) WahariTokens.bgCardPressed else WahariTokens.bgCard)
            .border(1.dp, WahariTokens.borderEdge, CircleShape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp)
            .heightIn(min = 44.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = WahariTypography.assistantBody.copy(
                fontSize = 15.sp, lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, color = WahariTokens.textPrimary,
            ),
        )
    }
}

@Composable
fun MonoBlock(text: String, color: Color = WahariTokens.textSecondary) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = WahariLayout.monoMax)
            .verticalScroll(rememberScrollState())
            .clip(RoundedCornerShape(16.dp))
            .background(WahariTokens.bgSurface)
            .border(1.dp, WahariTokens.borderSubtle, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .horizontalScroll(rememberScrollState()),
    ) {
        // Unbounded width from horizontalScroll keeps monospace lines on one line.
        Text(text = text, style = WahariTypography.monoBlock.copy(color = color))
    }
}

@Composable
fun VerticalGap(height: Int = 8) {
    Spacer(Modifier.height(height.dp))
}

@Composable
fun SmallSpacer() {
    Spacer(Modifier.size(4.dp))
}

@Composable
fun WahariTextButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = WahariTypography.assistantBody.copy(
                fontSize = 14.sp, lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, color = WahariTokens.accent,
            ),
        )
    }
}

@Composable
fun WahariIconButton(
    icon: ImageVector,
    contentDescription: String?,
    iconSize: Dp = 20.dp,
    buttonSize: Dp = 48.dp,
    fill: Color = WahariTokens.bgCapsule,
    pressedFill: Color = WahariTokens.bgCapsulePressed,
    pressScale: Float = 0.93f,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction, pressScale)
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .size(buttonSize)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(if (pressed) pressedFill else fill)
            .border(1.dp, WahariTokens.borderCapsule, CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun WahariTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val fieldInteraction = remember { MutableInteractionSource() }
        val focused by fieldInteraction.collectIsFocusedAsState()
        label?.let {
            Text(
                it,
                style = WahariTypography.optionTag.copy(
                    color = if (focused) WahariTokens.accent else WahariTokens.textMuted,
                ),
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        val resolvedOptions =
            if (singleLine && keyboardOptions == KeyboardOptions.Default) {
                KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done)
            } else {
                keyboardOptions
            }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(WahariTokens.bgCapsule)
                .border(
                    if (focused) 1.5.dp else 1.dp,
                    if (focused) WahariTokens.borderSelected else WahariTokens.borderCapsule,
                    RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .heightIn(min = 48.dp, max = if (singleLine) Dp.Unspecified else WahariLayout.fieldMax),
            textStyle = WahariTypography.assistantBody.copy(fontSize = 15.sp, color = Color.White),
            cursorBrush = SolidColor(Color.White),
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            keyboardOptions = resolvedOptions,
            keyboardActions = keyboardActions,
            interactionSource = fieldInteraction,
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, style = WahariTypography.assistantBody.copy(fontSize = 15.sp, color = WahariTokens.textPlaceholder))
                    }
                    inner()
                }
            },
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun WahariSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        onValueChangeFinished = onValueChangeFinished,
        colors = SliderDefaults.colors(
            activeTrackColor = WahariTokens.accent,
            inactiveTrackColor = WahariTokens.bgIcon,
            thumbColor = Color.White,
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
        ),
        thumb = {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        },
        track = { state ->
            SliderDefaults.Track(
                sliderState = state,
                modifier = Modifier.height(4.dp),
                colors = SliderDefaults.colors(
                    activeTrackColor = WahariTokens.accent,
                    inactiveTrackColor = WahariTokens.bgIcon,
                ),
            )
        },
    )
}

@Composable
fun WahariProgress(fraction: () -> Float, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = fraction,
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(CircleShape),
        color = WahariTokens.accent,
        trackColor = WahariTokens.bgIcon,
    )
}

@Composable
fun WahariChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction, 0.96f)
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(WahariTokens.bgCard)
            .border(1.dp, WahariTokens.borderSubtle, CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 0.dp)
            .height(36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = WahariTypography.assistantBullet.copy(fontSize = 13.sp, color = WahariTokens.textSecondary),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

private val Int.sp get() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
