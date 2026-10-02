package dev.citali.needle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.citali.needle.engine.NeedlePrefs
import dev.citali.needle.ui.theme.WahariIcons
import dev.citali.needle.ui.theme.WahariLayout
import dev.citali.needle.ui.theme.WahariTokens
import dev.citali.needle.ui.theme.WahariTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class SheetSnap { Closed, Half, Full }

private fun SheetSnap.toValue(): SheetValue = when (this) {
    SheetSnap.Closed -> SheetValue.Closed
    SheetSnap.Half -> SheetValue.Partial
    SheetSnap.Full -> SheetValue.Expanded
}

private fun SheetValue.toSnap(): SheetSnap = when (this) {
    SheetValue.Closed -> SheetSnap.Closed
    SheetValue.Partial -> SheetSnap.Half
    SheetValue.Expanded -> SheetSnap.Full
}

/** Agent Mode bottom sheet: option cards on the shared draggable sheet. */
@Composable
fun AgentModeSheet(
    snap: SheetSnap,
    screenHeight: androidx.compose.ui.unit.Dp,
    selected: String,
    onSnap: (SheetSnap) -> Unit,
    onSelect: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var selectJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    DraggableBottomSheet(
        value = snap.toValue(),
        onValueChange = { onSnap(it.toSnap()) },
        partialHeight = screenHeight * WahariLayout.SHEET_HALF_FRACTION,
        expandedHeight = screenHeight - maxOf(48.dp, androidx.compose.foundation.layout.WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
        enableHorizontalDismiss = true,
        header = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { if (snap == SheetSnap.Full) onSnap(SheetSnap.Half) else onSnap(SheetSnap.Closed) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(WahariTokens.handle),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Agent Mode", style = WahariTypography.sheetTitle)
            }
        },
        content = {
            val navBottom = androidx.compose.foundation.layout.WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            Column(
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 28.dp + navBottom),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                OptionCard(
                    icon = WahariIcons.messageSquare,
                    title = "Normal",
                    description = "Chat that acts. Needle 3 picks the tool that matches your sentence and the app runs it natively, like the flashlight, battery, SMS or opening an app",
                    tags = listOf(
                        listOf(WahariIcons.wrench to "Tool calls", WahariIcons.cpu to "On-device"),
                        listOf(WahariIcons.wifiOff to "Works offline"),
                    ),
                    selected = selected == NeedlePrefs.AGENT_MODE_NORMAL,
                    onClick = {
                        onSelect(NeedlePrefs.AGENT_MODE_NORMAL)
                        selectJob?.cancel()
                        selectJob = scope.launch { delay(260); onSnap(SheetSnap.Closed) }
                    },
                )
                OptionCard(
                    icon = WahariIcons.infinity,
                    title = "Automate",
                    description = "Describe a job and Needle 3 reviews a plan with you, then drives the screen one validated action at a time, asking before anything high-risk",
                    tags = listOf(
                        listOf(WahariIcons.terminal to "Plan review", WahariIcons.bot to "Screen control"),
                        listOf(WahariIcons.shieldAlert to "Safety checks"),
                    ),
                    selected = selected == NeedlePrefs.AGENT_MODE_AUTOMATE,
                    onClick = {
                        onSelect(NeedlePrefs.AGENT_MODE_AUTOMATE)
                        selectJob?.cancel()
                        selectJob = scope.launch { delay(260); onSnap(SheetSnap.Closed) }
                    },
                )
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionCard(
    icon: ImageVector,
    title: String,
    description: String,
    tags: List<List<Pair<ImageVector, String>>>,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = androidx.compose.animation.core.animateFloatAsState(
        if (pressed) 0.985f else 1f, label = "opt",
    ).value
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(24.dp))
            .background(if (selected) WahariTokens.bgCardSelected else WahariTokens.bgCard)
            .border(
                1.5.dp,
                if (selected) WahariTokens.borderSelected else Color.Transparent,
                RoundedCornerShape(24.dp),
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .padding(top = 2.dp)
                .clip(CircleShape)
                .background(if (selected) WahariTokens.accent else WahariTokens.bgIcon),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(WahariLayout.optionIcon))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = WahariTypography.optionTitle.copy(
                    color = if (selected) WahariTokens.accent else WahariTokens.textPrimary,
                ),
            )
            Spacer(Modifier.height(5.dp))
            Text(description, style = WahariTypography.optionDescription)
            Spacer(Modifier.height(12.dp))
            tags.forEachIndexed { rowIndex, row ->
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    row.forEach { (tagIcon, tagLabel) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(tagIcon, contentDescription = null, tint = WahariTokens.textMuted, modifier = Modifier.size(WahariLayout.tagIcon))
                            Text(tagLabel, style = WahariTypography.optionTag)
                        }
                    }
                }
                if (rowIndex < tags.lastIndex) Spacer(Modifier.height(8.dp))
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}

@androidx.compose.ui.tooling.preview.Preview(name = "Sheet fontscale 1.3", widthDp = 412, heightDp = 890, fontScale = 1.3f)
@Composable
private fun PreviewSheetFontScale() {
    dev.citali.needle.ui.theme.NeedleTheme {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .background(WahariTokens.bgSheet),
        ) {
            AgentModeSheet(
                snap = SheetSnap.Half,
                screenHeight = 890.dp,
                selected = NeedlePrefs.AGENT_MODE_AUTOMATE,
                onSnap = {},
                onSelect = {},
            )
        }
    }
}
