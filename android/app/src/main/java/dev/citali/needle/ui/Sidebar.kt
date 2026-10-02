package dev.citali.needle.ui

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.citali.needle.ui.theme.WahariIcons
import dev.citali.needle.ui.theme.WahariLayout
import dev.citali.needle.ui.theme.WahariTokens
import dev.citali.needle.ui.theme.WahariTypography

/**
 * Sidebar drawer panel only (the scrim and gestures live in
 * [SwipeableSidebarScaffold]). Position is read inside the draw lambda so
 * dragging never recomposes the panel.
 */
@Composable
fun SidebarDrawer(
    onTools: () -> Unit,
    onSettings: () -> Unit,
    drawerWidth: Dp,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(drawerWidth)
            .background(WahariTokens.bgDrawer)
            .border(1.dp, WahariTokens.borderSubtle),
    ) {
            val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusTop)
                    .background(WahariTokens.bgDrawer),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WahariLayout.navBand)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(WahariLayout.navButton)
                        .padding(start = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = "Wahari",
                        style = WahariTypography.sidebarWordmark,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        softWrap = false,
                    )
                }
                val gearInteraction = remember { MutableInteractionSource() }
                val gearPressed by gearInteraction.collectIsPressedAsState()
                val gearScale = animateFloatAsState(
                    if (gearPressed) 0.94f else 1f,
                    animationSpec = tween(WahariTokens.PRESS_MS, easing = WahariTokens.pressEasing),
                    label = "gear",
                ).value
                Box(
                    modifier = Modifier
                        .size(WahariLayout.navButton)
                        .graphicsLayer { scaleX = gearScale; scaleY = gearScale }
                        .clip(CircleShape)
                        .background(WahariTokens.bgCapsule)
                        .border(1.dp, WahariTokens.borderCapsule, CircleShape)
                        .clickable(interactionSource = gearInteraction, indication = null, onClick = onSettings),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        WahariIcons.settings,
                        contentDescription = "Settings",
                        tint = WahariTokens.settingsGearIcon,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 28.dp + navBottom),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val itemInteraction = remember { MutableInteractionSource() }
                val pressed by itemInteraction.collectIsPressedAsState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (pressed) WahariTokens.menuPressedFill else Color.Transparent)
                        .clickable(interactionSource = itemInteraction, indication = null, onClick = onTools)
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        WahariIcons.wrench,
                        contentDescription = null,
                        tint = if (pressed) Color.White else WahariTokens.textAi,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = "Tools",
                        style = WahariTypography.sidebarMenuItem.copy(
                            color = if (pressed) Color.White else WahariTokens.textAi,
                        ),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
    }
}
