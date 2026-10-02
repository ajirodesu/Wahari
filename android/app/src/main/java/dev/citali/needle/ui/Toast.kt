package dev.citali.needle.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.citali.needle.ui.theme.WahariIcons
import dev.citali.needle.ui.theme.WahariLayout
import dev.citali.needle.ui.theme.WahariTokens
import dev.citali.needle.ui.theme.WahariTypography

/** Centered toast pill. Top = status inset + 6dp. */
@Composable
fun WahariToast(message: String?, visible: Boolean, modifier: Modifier = Modifier) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = statusTop + 6.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        val toastMax = maxWidth - 36.dp
        AnimatedVisibility(
            visible = visible && message != null,
            enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { -10 },
            exit = fadeOut(tween(220)) + slideOutVertically(tween(220)) { -10 },
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = toastMax)
                    .clip(CircleShape)
                    .background(WahariTokens.bgToast)
                    .border(1.dp, WahariTokens.borderToast, CircleShape)
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(
                    WahariIcons.check,
                    contentDescription = null,
                    tint = WahariTokens.success,
                    modifier = Modifier.size(WahariLayout.toastIcon),
                )
                Text(text = message.orEmpty(), style = WahariTypography.toast, maxLines = 1)
            }
        }
    }
}
