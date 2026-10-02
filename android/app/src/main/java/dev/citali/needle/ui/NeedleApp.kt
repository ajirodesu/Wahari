package dev.citali.needle.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.citali.needle.engine.NeedlePrefs
import dev.citali.needle.ui.theme.NeedleTheme
import dev.citali.needle.ui.theme.WahariLayout
import dev.citali.needle.ui.theme.WahariTokens
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Page { Home, Tools, Settings }

@Composable
fun NeedleApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var page by rememberSaveable { mutableStateOf(Page.Home) }
    var agentMode by rememberSaveable { mutableStateOf(NeedlePrefs.agentMode(context)) }
    var sheetSnap by rememberSaveable { mutableStateOf(SheetSnap.Closed) }
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var popupIndex by rememberSaveable { mutableIntStateOf(-1) }
    var newChatSignal by rememberSaveable { mutableIntStateOf(0) }

    var toastMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var toastVisible by rememberSaveable { mutableStateOf(false) }
    var toastJob by remember { mutableStateOf<Job?>(null) }
    fun showToast(text: String) {
        toastJob?.cancel()
        toastMessage = text
        toastVisible = true
        toastJob = scope.launch {
            delay(2000)
            toastVisible = false
        }
    }

    // At most one transient surface at a time; switching hides the keyboard.
    fun dismissTransients() {
        sheetSnap = SheetSnap.Closed
        popupIndex = -1
        focusManager.clearFocus(force = true)
        keyboard?.hide()
    }

    // Setup gate: first-install Intro and resumable Needle/Accessibility setup
    // run before the normal app. All states are persisted; a completed setup
    // never shows again.
    var setupOpen by rememberSaveable {
        mutableStateOf(
            !NeedlePrefs.introCompleted(context) || !NeedlePrefs.setupComplete(context),
        )
    }

    // Back press priority: popup, then page to Home. The drawer and the sheet
    // own their BackHandlers inside their components, enabled only when open.
    // All disabled while the setup flow owns the back stack.
    BackHandler(enabled = !setupOpen && drawerOpen == false && sheetSnap == SheetSnap.Closed && popupIndex != -1 && page == Page.Home) {
        popupIndex = -1
    }
    BackHandler(enabled = !setupOpen && drawerOpen == false && sheetSnap == SheetSnap.Closed && popupIndex == -1 && page != Page.Home) {
        page = Page.Home
    }

    NeedleTheme {
        if (setupOpen) {
            SetupFlow(onFinished = { setupOpen = false })
        } else {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(WahariTokens.bgMain),
            ) {
                val screenW = maxWidth
                val drawerWidth = minOf(screenW * WahariLayout.DRAWER_FRACTION, WahariLayout.drawerMax)
                val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

                SwipeableSidebarScaffold(
                    open = drawerOpen,
                    onOpenChange = { drawerOpen = it },
                    drawerWidth = drawerWidth,
                    gesturesEnabled = page == Page.Home && sheetSnap == SheetSnap.Closed,
                    onGestureStart = {
                        popupIndex = -1
                        focusManager.clearFocus(force = true)
                        keyboard?.hide()
                    },
                    drawerContent = {
                        SidebarDrawer(
                            drawerWidth = drawerWidth,
                            onTools = {
                                dismissTransients()
                                drawerOpen = false
                                page = Page.Tools
                            },
                            onSettings = {
                                dismissTransients()
                                drawerOpen = false
                                page = Page.Settings
                            },
                        )
                    },
                ) {
                    // 1. Page content.
                    Box(Modifier.fillMaxSize()) {
                        ChatScreen(
                            agentMode = agentMode,
                            onOpenSheet = {
                                dismissTransients()
                                sheetSnap = SheetSnap.Half
                            },
                            onToast = ::showToast,
                            newChatSignal = newChatSignal,
                            popupIndex = if (popupIndex < 0) null else popupIndex,
                            onPopupIndex = { popupIndex = it ?: -1 },
                        )
                        AnimatedVisibility(
                            visible = page != Page.Home,
                            enter = slideInHorizontally(tween(WahariTokens.DRAWER_MS, easing = WahariTokens.mainEasing)) { it } + fadeIn(tween(WahariTokens.DRAWER_MS)),
                            exit = slideOutHorizontally(tween(WahariTokens.DRAWER_MS, easing = WahariTokens.mainEasing)) { it } + fadeOut(tween(WahariTokens.DRAWER_MS)),
                        ) {
                            when (page) {
                                Page.Tools -> PageShell(title = "Tools", onBack = { page = Page.Home }) {
                                    ToolsContent()
                                }
                                Page.Settings -> PageShell(title = "Settings", onBack = { page = Page.Home }) {
                                    SettingsContent()
                                }
                                Page.Home -> Unit
                            }
                        }
                    }

                    // 2. Top nav (Home only; pages draw their own PageNav).
                    if (page == Page.Home) {
                        TopNav(
                            onMenu = {
                                dismissTransients()
                                drawerOpen = !drawerOpen
                            },
                            onNewChat = { newChatSignal++ },
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    }

                    // 3. Status scrim over the top inset: gradient only, never touches.
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(statusTop)
                            .background(WahariTokens.statusScrim()),
                    )

                    // 4. Toast above everything except drawer/sheet.
                    WahariToast(
                        message = toastMessage,
                        visible = toastVisible,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }

                // 5. Sheet scrim + bottom sheet.
                AgentModeSheet(
                    snap = sheetSnap,
                    screenHeight = maxHeight,
                    selected = agentMode,
                    onSnap = { sheetSnap = it },
                    onSelect = { mode ->
                        agentMode = mode
                        NeedlePrefs.setAgentMode(context, mode)
                    },
                )
            }
        }
    }
}

@Preview(name = "Sidebar open", widthDp = 412, heightDp = 890)
@Composable
private fun PreviewSidebar() {
    NeedleTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(WahariTokens.bgMain),
        ) {
            SidebarDrawer(onTools = {}, onSettings = {}, drawerWidth = 320.dp)
        }
    }
}

@Preview(name = "Sheet half", widthDp = 412, heightDp = 890)
@Composable
private fun PreviewSheetHalf() {
    NeedleTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(WahariTokens.bgMain),
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

@Preview(name = "Sheet full", widthDp = 412, heightDp = 890)
@Composable
private fun PreviewSheetFull() {
    NeedleTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(WahariTokens.bgMain),
        ) {
            AgentModeSheet(
                snap = SheetSnap.Full,
                screenHeight = 890.dp,
                selected = NeedlePrefs.AGENT_MODE_NORMAL,
                onSnap = {},
                onSelect = {},
            )
        }
    }
}
