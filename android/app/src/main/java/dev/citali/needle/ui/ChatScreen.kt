package dev.citali.needle.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.citali.needle.engine.ChatController
import dev.citali.needle.engine.NeedleEngine
import dev.citali.needle.engine.NeedlePrefs
import dev.citali.needle.pilot.agent.AgentEngine
import dev.citali.needle.pilot.agent.AppSpec
import dev.citali.needle.pilot.agent.CommandPlanner
import dev.citali.needle.pilot.agent.Plan
import dev.citali.needle.pilot.data.AppInventory
import dev.citali.needle.pilot.data.SecureStore
import dev.citali.needle.ui.theme.NeedleTheme
import dev.citali.needle.ui.theme.WahariIcons
import dev.citali.needle.ui.theme.WahariLayout
import dev.citali.needle.ui.theme.WahariTokens
import dev.citali.needle.ui.theme.WahariTypography
import dev.citali.needle.ui.theme.contentSidePadding
import dev.citali.needle.ui.theme.navInset
import dev.citali.needle.ui.theme.statusInset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val exampleCommands = listOf(
    "turn on the flashlight",
    "what is my battery level?",
    "vibrate for 1 second",
    "copy Hello World to clipboard",
    "what network am I on?",
    "where am I right now?",
    "say out loud that the battery is low",
    "open WhatsApp",
)

private data class AutomateEntry(
    val id: Long,
    val command: String,
    val plan: Plan?,
)

@Composable
fun ChatScreen(
    agentMode: String,
    onOpenSheet: () -> Unit,
    onToast: (String) -> Unit,
    newChatSignal: Int,
    popupIndex: Int?,
    onPopupIndex: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val messages by ChatController.messages.collectAsStateWithLifecycle()
    val busy by ChatController.busy.collectAsStateWithLifecycle()
    val agentState by AgentEngine.state.collectAsStateWithLifecycle()
    val showReasoning = NeedlePrefs.showReasoning(context)

    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var composerFocused by remember { mutableStateOf(false) }
    val automateEntries = remember { mutableStateListOf<AutomateEntry>() }
    var automateId by remember { mutableStateOf(0L) }
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    var dockHeight by remember { mutableStateOf(140.dp) }
    // Set on the user's own send; the follow effect below always scrolls for it.
    var forceScroll by remember { mutableIntStateOf(0) }
    // True while our pre-popup scroll runs, so it never closes the popup it is revealing.
    var programmaticScroll by remember { mutableStateOf(false) }

    LaunchedEffect(newChatSignal) {
        if (newChatSignal > 0) {
            ChatController.clear()
            automateEntries.clear()
            onPopupIndex(null)
            focusManager.clearFocus(force = true)
        }
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                field = TextFieldValue(spoken)
            }
        } else {
            ChatController.addSystem("No speech was recognised.", error = true)
        }
    }
    fun launchMic() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your command")
        }
        runCatching { micLauncher.launch(intent) }
            .onFailure { ChatController.addSystem("No speech recogniser is available on this device.", error = true) }
    }

    fun itemCount(): Int {
        var total = 0
        if (messages.isEmpty() && automateEntries.isEmpty()) total++ else total += messages.size
        total += automateEntries.size
        if (automateEntries.isNotEmpty() && agentState.phase != AgentEngine.Phase.IDLE) total++
        if (busy) total++
        return total
    }

    fun nearBottom(): Boolean {
        val info = listState.layoutInfo
        if (info.totalItemsCount == 0) return true
        val last = info.visibleItemsInfo.lastOrNull() ?: return true
        if (last.index < info.totalItemsCount - 1) return false
        val slop = with(density) { 120.dp.toPx() }
        return info.viewportEndOffset - last.offset - last.size < slop
    }

    fun scrollToLast() {
        scope.launch {
            val total = itemCount()
            if (total > 0) runCatching { listState.animateScrollToItem(total - 1) }
        }
    }

    // Follow new content only when the user is already near the bottom; the user's
    // own send always scrolls via forceScroll. Never pulls the user out of history.
    LaunchedEffect(messages.size, automateEntries.size, agentState.phase, busy, forceScroll) {
        if (forceScroll > 0) {
            forceScroll = 0
            scrollToLast()
        } else if (nearBottom()) {
            scrollToLast()
        }
    }

    // A user-driven scroll closes an open popup; our pre-popup scroll does not.
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && !programmaticScroll && popupIndex != null) {
            onPopupIndex(null)
        }
    }

    val statusTop = statusInset()
    val sidePad = contentSidePadding()

    fun doSend(raw: String) {
        val query = raw.trim()
        if (query.isEmpty() || busy) return
        onPopupIndex(null)
        if (agentMode == NeedlePrefs.AGENT_MODE_AUTOMATE) {
            val entryId = ++automateId
            scope.launch {
                val apiKey = withContext(Dispatchers.IO) { SecureStore.getApiKey(context) }
                val onDeviceReady = NeedleEngine.state.value.status == NeedleEngine.Status.READY
                val plan = CommandPlanner.parse(
                    command = query,
                    aiAvailable = onDeviceReady || !apiKey.isNullOrBlank(),
                    resolveApp = { q: String ->
                        AppInventory.resolve(context, q)?.let { app -> AppSpec(app.packageName, app.label) }
                    },
                )
                automateEntries.add(AutomateEntry(entryId, query, plan))
                field = TextFieldValue()
                focusManager.clearFocus(force = true)
                keyboard?.hide()
                forceScroll++
            }
        } else {
            ChatController.send(context, query)
            field = TextFieldValue()
            focusManager.clearFocus(force = true)
            keyboard?.hide()
            forceScroll++
        }
    }

    // Open the popup only after scrolling the bubble (plus its popup) fully into
    // the clear band below the top nav and above the dock. No flipping below.
    val listTopClearPx by rememberUpdatedState(
        with(density) { (statusTop + WahariLayout.navBand + WahariLayout.popupGap).toPx().toInt() },
    )
    fun openPopup(index: Int) {
        scope.launch {
            onPopupIndex(index)
            programmaticScroll = true
            try {
                runCatching {
                    listState.animateScrollToItem(index, scrollOffset = -listTopClearPx)
                }
            } finally {
                programmaticScroll = false
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(WahariTokens.bgMain)) {
        val windowWidth = maxWidth
        val windowHeight = maxHeight
        val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        val maxLines = WahariLayout.maxComposerLines(windowHeight - imeBottom)
        val dockActive = composerFocused || field.text.isNotBlank()
        val dockSide by animateDpAsState(
            targetValue = if (dockActive) WahariLayout.dockSideActive else WahariLayout.dockSideInactive,
            animationSpec = tween(durationMillis = WahariTokens.DOCK_MARGIN_MS, easing = WahariTokens.mainEasing),
            label = "dockSide",
        )
        val capsuleWidth = windowWidth - dockSide * 2

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .widthIn(max = WahariLayout.contentMax)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = sidePad,
                    end = sidePad,
                    top = statusTop + WahariLayout.listTopExtra,
                    bottom = dockHeight + WahariLayout.listBottomExtra,
                ),
                verticalArrangement = Arrangement.spacedBy(WahariLayout.messageGap),
            ) {
                if (messages.isEmpty() && automateEntries.isEmpty()) {
                    item(key = "empty", contentType = "empty") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "Ask in plain English. The model runs on this phone, picks the tool it needs, " +
                                    "and tells you what it did.",
                                style = WahariTypography.assistantBody.copy(color = WahariTokens.textMuted),
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                exampleCommands.chunked(2).forEach { row ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        row.forEach { example ->
                                            WahariChip(
                                                text = example,
                                                onClick = { field = TextFieldValue(example) },
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                        // Keep two-column alignment on a partial last row.
                                        repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }
                    }
                }
                itemsIndexed(
                    messages,
                    key = { index, m -> "${m.at}-${m.text.hashCode()}-$index" },
                    contentType = { _, m -> m.role },
                ) { index, message ->
                    when (message.role) {
                        ChatController.Role.USER -> UserBubble(
                            text = message.text,
                            popupOpen = popupIndex == index,
                            onTogglePopup = {
                                if (popupIndex == index) onPopupIndex(null) else openPopup(index)
                            },
                            onCopy = {
                                copyPlain(context, message.text.trim())
                                onPopupIndex(null)
                                onToast("Copied to clipboard")
                            },
                            onRetry = {
                                field = TextFieldValue(message.text)
                                onPopupIndex(null)
                                focusRequester.requestFocus()
                                onToast("Message ready to retry")
                            },
                        )
                        ChatController.Role.ASSISTANT -> AssistantMessage(message = message, showReasoning = showReasoning)
                        ChatController.Role.SYSTEM -> SystemMessage(message = message)
                    }
                }
                automateEntries.forEach { entry ->
                    item(key = "auto-${entry.id}", contentType = "automate") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            UserBubble(
                                text = entry.command,
                                popupOpen = false,
                                onTogglePopup = {},
                                onCopy = {},
                                onRetry = {},
                                static = true,
                            )
                            entry.plan?.let { plan ->
                                PlanCard(
                                    plan = plan,
                                    accessibilityOn = agentState.serviceConnected,
                                    onOpenAccessibility = { openAccessibilitySettings(context) },
                                    onApproveAndRun = { AgentEngine.start(context, plan) },
                                    onDiscard = { automateEntries.remove(entry) },
                                    onHandOff = if (isTaskPilotInstalled(context)) {
                                        { handOffToTaskPilot(context, entry.command) }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                    }
                }
                if (automateEntries.isNotEmpty() && agentState.phase != AgentEngine.Phase.IDLE) {
                    item(key = "run", contentType = "run") {
                        RunCard(
                            state = agentState,
                            onStop = { AgentEngine.stop() },
                            onPause = { AgentEngine.pause() },
                            onResume = { AgentEngine.resume() },
                            onClear = { AgentEngine.reset() },
                        )
                    }
                }
                if (busy) {
                    item(key = "busy", contentType = "busy") {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(WahariLayout.busyIndicator),
                                strokeWidth = 2.dp,
                                color = WahariTokens.accent,
                                trackColor = WahariTokens.bgIcon,
                            )
                            Text("Thinking on-device…", style = WahariTypography.busyLabel)
                        }
                    }
                }
            }
        }

        // Tap anywhere outside a bubble closes the popup; scrolls pass through.
        if (popupIndex != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(popupIndex) {
                        detectTapGestures(onTap = { onPopupIndex(null) })
                    },
            )
        }

        // Bottom dock.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(WahariTokens.bottomDockGradient())
                .imePadding(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val navBottom = navInset()
            val dockBottom = maxOf(WahariLayout.dockBottomMin, navBottom)
            Column(
                modifier = Modifier
                    .widthIn(max = WahariLayout.contentMax)
                    .fillMaxWidth()
                    .padding(start = dockSide, end = dockSide, top = WahariLayout.dockTop, bottom = dockBottom)
                    .onSizeChanged { size ->
                        dockHeight = with(density) { size.height.toDp() }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Composer(
                    field = field,
                    onFieldChange = { field = it },
                    busy = busy,
                    onSend = { doSend(field.text) },
                    onMic = { launchMic() },
                    onGrid = onOpenSheet,
                    onEmptyPrimary = {
                        onToast("Voice mode active")
                        launchMic()
                    },
                    focusRequester = focusRequester,
                    onFocusedChange = { composerFocused = it },
                    windowWidth = windowWidth,
                    capsuleWidth = capsuleWidth,
                    maxLines = maxLines,
                )
            }
        }
    }
}

private fun copyPlain(context: Context, text: String) {
    runCatching {
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("Wahari", text))
    }
}

@Composable
private fun AssistantMessage(message: ChatController.Message, showReasoning: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RichAssistantText(text = message.text)
        if (message.tools.isNotEmpty()) {
            Text(
                "Called: ${message.tools.joinToString(", ")}",
                style = WahariTypography.modelExtra,
            )
        }
        if (message.results.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                message.results.forEach { result ->
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
                        Text(result, style = WahariTypography.monoBlock)
                    }
                }
            }
        }
        if (showReasoning) {
            message.reasoning?.let { reasoning ->
                Text("Reasoning: $reasoning", style = WahariTypography.modelExtra)
            }
            message.confidence?.let { confidence ->
                Text("Confidence: $confidence%", style = WahariTypography.modelExtra)
            }
        }
    }
}

@Composable
private fun RichAssistantText(text: String) {
    // Split paragraphs on blank lines; single newlines stay inside a paragraph.
    val blocks = remember(text) { text.replace("\r\n", "\n").split("\n\n") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { block ->
            val lines = block.split('\n').filter { it.isNotBlank() || block.isBlank() }
            if (block.isBlank()) return@forEach
            if (lines.all { it.startsWith("• ") || it.startsWith("- ") }) {
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    lines.forEach { bullet ->
                        Text(bulletAnnotated(bullet), style = WahariTypography.assistantBullet)
                    }
                }
            } else {
                lines.forEach { line ->
                    Text(richAnnotated(line.ifBlank { " " }), style = WahariTypography.assistantBody)
                }
            }
        }
    }
}

private fun bulletAnnotated(line: String): AnnotatedString = buildAnnotatedString {
    append("• ")
    appendInlineRich(line.removePrefix("• ").removePrefix("- "))
}

private fun richAnnotated(line: String): AnnotatedString = buildAnnotatedString {
    appendInlineRich(line)
}

private fun AnnotatedString.Builder.appendInlineRich(raw: String) {
    val bold = Regex("\\*\\*(.+?)\\*\\*")
    var last = 0
    bold.findAll(raw).forEach { match ->
        if (match.range.first > last) append(raw.substring(last, match.range.first))
        withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) {
            append(match.groupValues[1])
        }
        last = match.range.last + 1
    }
    if (last < raw.length) append(raw.substring(last))
}

@Composable
private fun SystemMessage(message: ChatController.Message) {
    Text(
        message.text,
        style = WahariTypography.systemMessage.copy(
            color = if (message.error) WahariTokens.danger else WahariTokens.textMuted,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun UserBubble(
    text: String,
    popupOpen: Boolean,
    onTogglePopup: () -> Unit,
    onCopy: () -> Unit,
    onRetry: () -> Unit,
    static: Boolean = false,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Spacer(Modifier.weight(1f - WahariLayout.BUBBLE_MAX_FRACTION))
        Column(
            modifier = Modifier.weight(WahariLayout.BUBBLE_MAX_FRACTION, fill = false),
            horizontalAlignment = Alignment.End,
        ) {
            if (popupOpen) {
                MessagePopup(onCopy = onCopy, onRetry = onRetry)
                Spacer(Modifier.height(WahariLayout.popupGap))
            }
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val scale = animateFloatAsState(
                if (pressed) 0.98f else 1f,
                animationSpec = tween(WahariTokens.PRESS_MS, easing = WahariTokens.pressEasing),
                label = "bubble",
            ).value
            Box {
                Box(
                    modifier = Modifier
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(WahariTokens.userBubbleShape)
                        .background(if (popupOpen) WahariTokens.userBubbleActive else WahariTokens.userBubble)
                        .then(
                            if (static) {
                                Modifier
                            } else {
                                Modifier.pointerInput(Unit) {
                                    detectTapGestures(
                                        onLongPress = { onTogglePopup() },
                                        onTap = { onTogglePopup() },
                                    )
                                }
                            },
                        )
                        .padding(start = 18.dp, end = 18.dp, top = 11.dp, bottom = 12.dp),
                ) {
                    Text(text, style = WahariTypography.userBubble)
                }
                if (popupOpen) {
                    // Tail: 10dp square rotated 45deg, border on right and bottom edges only.
                    // Negative offset is decoration overlap (spec 9: tail bottom -5dp), not layout.
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = WahariLayout.popupTailRight)
                            .offset(y = -WahariLayout.popupTailDrop)
                            .size(WahariLayout.popupTail)
                            .graphicsLayer { rotationZ = 45f }
                            .background(WahariTokens.bgPopup)
                            .drawBehind {
                                val w = Stroke(1.dp.toPx())
                                drawLine(
                                    WahariTokens.borderEdge,
                                    start = Offset(size.width, 0f),
                                    end = Offset(size.width, size.height),
                                    strokeWidth = w.width,
                                )
                                drawLine(
                                    WahariTokens.borderEdge,
                                    start = Offset(0f, size.height),
                                    end = Offset(size.width, size.height),
                                    strokeWidth = w.width,
                                )
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun MessagePopup(onCopy: () -> Unit, onRetry: () -> Unit) {
    val enter by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = WahariTokens.POPUP_MS, easing = WahariTokens.mainEasing),
        label = "popupIn",
    )
    Row(
        modifier = Modifier
            .graphicsLayer {
                alpha = enter
                scaleX = 0.92f + 0.08f * enter
                scaleY = 0.92f + 0.08f * enter
                translationY = 6.dp.toPx() * (1f - enter)
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f)
            }
            .clip(WahariTokens.popupShape)
            .background(WahariTokens.bgPopup)
            .border(1.dp, WahariTokens.borderEdge, WahariTokens.popupShape)
            .padding(horizontal = 5.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PopupButton(icon = WahariIcons.copy, label = "Copy", onClick = onCopy)
        Box(
            modifier = Modifier
                .padding(horizontal = 1.dp)
                .size(width = 1.dp, height = 16.dp)
                .background(WahariTokens.popupDivider),
        )
        PopupButton(icon = WahariIcons.rotateCcw, label = "Retry", onClick = onRetry)
    }
}

@Composable
private fun PopupButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = animateFloatAsState(
        if (pressed) 0.94f else 1f,
        animationSpec = tween(120, easing = WahariTokens.pressEasing),
        label = "popup",
    ).value
    Row(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(WahariTokens.popupButtonShape)
            .background(if (pressed) WahariTokens.popupButtonPressed else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = label, tint = WahariTokens.popupButtonText, modifier = Modifier.size(WahariLayout.popupIcon))
        Text(label, style = WahariTypography.popupButton)
    }
}

private fun Int.spFix() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)

@Preview(name = "Home with popup", widthDp = 412, heightDp = 890)
@Composable
private fun PreviewHome() {
    NeedleTheme {
        Box(Modifier.fillMaxSize().background(WahariTokens.bgMain)) {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 124.dp)) {
                Text("Yooo! What's good?", style = WahariTypography.assistantBody)
                Spacer(Modifier.height(24.dp))
                UserBubble(text = "Is Wahari a great name?", popupOpen = true, onTogglePopup = {}, onCopy = {}, onRetry = {})
            }
        }
    }
}

@Preview(name = "Home 320 wide", widthDp = 320, heightDp = 568)
@Composable
private fun PreviewHomeSmall() {
    NeedleTheme {
        Box(Modifier.fillMaxSize().background(WahariTokens.bgMain)) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Spacer(Modifier.height(100.dp))
                Text("Yooo! What's good?", style = WahariTypography.assistantBody)
                UserBubble(
                    text = "https://example.com/" + "very-long-path-segment/".repeat(12),
                    popupOpen = false,
                    onTogglePopup = {},
                    onCopy = {},
                    onRetry = {},
                )
                Text("A".repeat(10000), style = WahariTypography.assistantBody)
            }
        }
    }
}

@Preview(name = "Landscape home", widthDp = 892, heightDp = 412)
@Composable
private fun PreviewLandscape() {
    NeedleTheme {
        Box(Modifier.fillMaxSize().background(WahariTokens.bgMain)) {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 110.dp)) {
                Text("Yooo! What's good?", style = WahariTypography.assistantBody)
                Spacer(Modifier.height(24.dp))
                UserBubble(text = "Is Wahari a great name?", popupOpen = false, onTogglePopup = {}, onCopy = {}, onRetry = {})
            }
        }
    }
}

@Preview(name = "Home fontscale 1.3", widthDp = 412, heightDp = 890, fontScale = 1.3f)
@Composable
private fun PreviewHomeFontScale() {
    NeedleTheme {
        Box(Modifier.fillMaxSize().background(WahariTokens.bgMain)) {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 124.dp)) {
                Text("Yooo! What's good?", style = WahariTypography.assistantBody)
                Spacer(Modifier.height(24.dp))
                UserBubble(text = "Is Wahari a great name?", popupOpen = false, onTogglePopup = {}, onCopy = {}, onRetry = {})
            }
        }
    }
}

@Preview(name = "Stress tool result", widthDp = 412, heightDp = 890)
@Composable
private fun PreviewStressResult() {
    NeedleTheme {
        Box(Modifier.fillMaxSize().background(WahariTokens.bgMain)) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 124.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                AssistantMessage(
                    message = ChatController.Message(
                        role = ChatController.Role.ASSISTANT,
                        text = "Here is the scan output.",
                        tools = listOf("scan_wifi_networks"),
                        results = listOf((1..300).joinToString("\n") { i -> "network-$i ssid=Home-$i rssi=${-50 - i % 30}" }),
                    ),
                    showReasoning = true,
                )
            }
        }
    }
}
