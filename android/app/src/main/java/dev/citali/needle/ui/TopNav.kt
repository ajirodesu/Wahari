package dev.citali.needle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.citali.needle.ui.theme.WahariIcons
import dev.citali.needle.ui.theme.WahariTokens
import dev.citali.needle.ui.theme.WahariTypography

/** Floating top nav over the chat. Container height = status inset + 56dp. */
@Composable
fun TopNav(
    onMenu: () -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(WahariTokens.topNavGradient())
            .padding(top = statusTop)
            .height(56.dp)
            .padding(horizontal = 14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WahariIconButton(
                icon = WahariIcons.menu,
                contentDescription = "Open sidebar",
                iconSize = 20.dp,
                buttonSize = 48.dp,
                fill = WahariTokens.bgCapsule,
                onClick = onMenu,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            WahariIconButton(
                icon = WahariIcons.rotateCcw,
                contentDescription = "New chat",
                iconSize = 20.dp,
                buttonSize = 48.dp,
                fill = WahariTokens.bgCapsule,
                onClick = onNewChat,
            )
        }
    }
}

/** Page variant: single back button left, title centered in the 56dp band. */
@Composable
fun PageNav(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(WahariTokens.topNavGradient())
            .padding(top = statusTop)
            .height(56.dp)
            .padding(horizontal = 14.dp),
    ) {
        Box(modifier = Modifier.align(Alignment.CenterStart)) {
            WahariIconButton(
                icon = WahariIcons.arrowLeft,
                contentDescription = "Back",
                iconSize = 20.dp,
                buttonSize = 48.dp,
                fill = WahariTokens.bgCapsule,
                onClick = onBack,
            )
        }
        androidx.compose.material3.Text(
            text = title,
            style = WahariTypography.pageTitle,
            modifier = Modifier.align(Alignment.Center),
            maxLines = 1,
        )
    }
}
