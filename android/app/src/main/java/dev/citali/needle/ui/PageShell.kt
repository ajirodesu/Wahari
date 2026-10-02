package dev.citali.needle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.citali.needle.ui.theme.WahariLayout
import dev.citali.needle.ui.theme.WahariTokens
import dev.citali.needle.ui.theme.contentSidePadding

/** Full-screen derived page: bgMain, floating PageNav, scrolling content under it. */
@Composable
fun PageShell(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val sidePad = contentSidePadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WahariTokens.bgMain),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = WahariLayout.contentMax)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = sidePad,
                    end = sidePad,
                    top = statusTop + 66.dp,
                    bottom = 28.dp + navBottom,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { content() }
            }
        }
        PageNav(
            title = title,
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

/** Simple vertical stack for page content inside PageShell. */
@Composable
fun PageColumn(content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
}
