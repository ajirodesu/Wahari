package dev.citali.needle.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.citali.needle.engine.ChatController
import dev.citali.needle.engine.ModelDownloadController
import dev.citali.needle.engine.ModelRepository
import dev.citali.needle.engine.NeedleEngine
import dev.citali.needle.engine.NeedlePrefs
import dev.citali.needle.pilot.data.HistoryStore
import dev.citali.needle.pilot.data.PilotSettings
import dev.citali.needle.pilot.data.SecureStore
import dev.citali.needle.pilot.data.SettingsStore
import dev.citali.needle.remote.NeedleRemoteService
import dev.citali.needle.remote.TelegramBridge
import dev.citali.needle.tools.AccessibilityState
import dev.citali.needle.tools.ActivityBridges
import dev.citali.needle.tools.DevicePermissions
import dev.citali.needle.tools.PhoneTools
import dev.citali.needle.ui.theme.NeedleTheme
import dev.citali.needle.ui.theme.WahariTokens
import dev.citali.needle.ui.theme.WahariTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val engine by NeedleEngine.state.collectAsStateWithLifecycle()
    val download by ModelDownloadController.state.collectAsStateWithLifecycle()
    val telegram by TelegramBridge.state.collectAsStateWithLifecycle()
    val settingsFlow = remember(context) { SettingsStore.settings(context) }
    val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = PilotSettings())
    val historyFlow = remember(context) { HistoryStore.entries(context) }
    val history by historyFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var packs by remember { mutableStateOf(NeedlePrefs.toolPacks(context)) }
    // Tool catalogues are rebuilt from scratch on every call, so count each
    // pack once instead of on every recomposition (e.g. each keystroke below).
    val packToolCounts = remember(context) {
        PhoneTools.Pack.entries.associateWith { PhoneTools.tools(context, setOf(it)).size }
    }
    var maxTokens by remember { mutableStateOf(NeedlePrefs.maxNewTokens(context).toFloat()) }
    var showReasoning by remember { mutableStateOf(NeedlePrefs.showReasoning(context)) }
    var telegramToken by remember { mutableStateOf(NeedlePrefs.telegramToken(context)) }
    var telegramEnabled by remember { mutableStateOf(NeedlePrefs.telegramEnabled(context)) }
    var adminIdsInput by remember {
        mutableStateOf(NeedlePrefs.telegramAdminIds(context).sorted().joinToString(", "))
    }
    var adminIdsError by remember { mutableStateOf<String?>(null) }
    var endpointUrl by remember { mutableStateOf(settings.endpointUrl) }
    var apiPath by remember { mutableStateOf(settings.apiPath) }
    var model by remember { mutableStateOf(settings.model) }
    var fallbacks by remember { mutableStateOf(settings.fallbackModels.joinToString("\n")) }
    var apiKeyInput by remember { mutableStateOf("") }
    var hasApiKey by remember { mutableStateOf(false) }
    // Live accessibility service state (OS truth, re-queried on resume) plus
    // durable setup history. Disabling the service later shows as disabled
    // without erasing the historical completion.
    var a11yServiceOn by remember {
        mutableStateOf(DevicePermissions.isAccessibilityServiceEnabled(context))
    }
    var a11ySetupDone by remember {
        mutableStateOf(NeedlePrefs.accessibilitySetupCompleted(context))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val on = DevicePermissions.isAccessibilityServiceEnabled(context)
                a11yServiceOn = on
                if (on && !NeedlePrefs.accessibilitySetupCompleted(context)) {
                    NeedlePrefs.setAccessibilitySetupCompleted(context, true)
                }
                a11ySetupDone = NeedlePrefs.accessibilitySetupCompleted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Drafts are editing values; the DataStore flow is the saved truth. Sync
    // drafts from the store until the user edits, so saved values loaded
    // asynchronously after first composition are shown instead of defaults —
    // and defaults never overwrite what is stored.
    var providerTouched by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) ModelDownloadController.import(context, uri)
    }

    LaunchedEffect(Unit) {
        hasApiKey = withContext(Dispatchers.IO) { SecureStore.hasApiKey(context) }
        NeedleEngine.refresh(context)
    }
    LaunchedEffect(settings) {
        if (!providerTouched) {
            endpointUrl = settings.endpointUrl
            apiPath = settings.apiPath
            model = settings.model
            fallbacks = settings.fallbackModels.joinToString("\n")
        }
    }

    PageColumn {
        Section(
            title = "On-device model",
            subtitle = "Needle ${ModelRepository.ENGINE_VERSION} by Cactus Compute, " +
                "${ModelRepository.humanSize(ModelRepository.WEIGHTS_BYTES)} of weights, runs entirely offline.",
        ) {
            KeyValue("Engine", if (engine.engineAvailable) "available" else "not in this build")
            KeyValue("Status", engine.status.name.lowercase().replace('_', ' '))
            if (engine.detail.isNotBlank()) {
                Text(engine.detail, style = WahariTypography.sectionSubtitle)
            }
            if (download.running) {
                WahariProgress(fraction = { download.fraction })
                Text(download.writtenLabel, style = WahariTypography.optionTag)
                SecondaryButton(text = "Cancel", onClick = { ModelDownloadController.cancel() })
            } else if (engine.modelLoaded || ModelRepository.hasWeights(context)) {
                ActionRow {
                    SecondaryButton(
                        text = "Delete model weights",
                        onClick = { ModelDownloadController.delete(context) },
                    )
                }
            } else {
                ActionRow {
                    PrimaryButton(text = "Download", onClick = { ModelDownloadController.download(context) })
                    SecondaryButton(text = "Import a .cact file", onClick = { importLauncher.launch(arrayOf("*/*")) })
                }
                ActionRow {
                    SecondaryButton(
                        text = "Delete model weights",
                        enabled = false,
                        onClick = { ModelDownloadController.delete(context) },
                    )
                }
            }
            download.message?.let { message ->
                Text(
                    message,
                    style = WahariTypography.sectionSubtitle.copy(
                        color = if (download.error) WahariTokens.danger else WahariTokens.accent,
                    ),
                )
            }
            Text(
                "SHA-256 ${ModelRepository.WEIGHTS_SHA256.take(24)}…",
                style = WahariTypography.optionTag,
            )
        }

        Section(
            title = "Tool packs",
            subtitle = "Every declared tool shares the model's context, so enable only what you need.",
        ) {
            PhoneTools.Pack.entries.forEach { pack ->
                ToggleRow(
                    title = pack.label,
                    subtitle = "${pack.summary} · ${packToolCounts[pack] ?: 0} tools",
                    checked = pack in packs,
                    onCheckedChange = { enabled ->
                        packs = if (enabled) packs + pack else packs - pack
                        if (packs.isEmpty()) packs = setOf(PhoneTools.Pack.CORE)
                        NeedlePrefs.setToolPacks(context, packs)
                        scope.launch { NeedleEngine.unload() }
                    },
                )
            }
            Text(
                "Changing packs reloads the tool catalogue on the next message.",
                style = WahariTypography.sectionSubtitle,
            )
        }

        RemoteControlCard(
            token = telegramToken,
            onTokenChange = { telegramToken = it },
            adminIdsInput = adminIdsInput,
            onAdminIdsChange = {
                adminIdsInput = it
                adminIdsError = null
            },
            adminIdsError = adminIdsError,
            running = telegram.running,
            handled = telegram.handled,
            denied = telegram.denied,
            lastDeniedUser = telegram.lastDeniedUser,
            lastMessage = telegram.lastMessage,
            listenerError = telegram.error,
            enabled = telegramEnabled,
            onSave = {
                val (ids, invalid) = NeedlePrefs.parseTelegramAdminIds(adminIdsInput)
                if (invalid.isNotEmpty()) {
                    adminIdsError = "These do not look like numeric user IDs: ${invalid.take(3).joinToString(", ")}"
                } else if (ids.isEmpty()) {
                    adminIdsError = "Save at least one numeric user ID — send /myid to the bot to see yours."
                } else {
                    adminIdsError = null
                    NeedlePrefs.setTelegramToken(context, telegramToken)
                    NeedlePrefs.setTelegramAdminIds(context, ids)
                    adminIdsInput = ids.sorted().joinToString(", ")
                    ChatController.addSystem("Remote access saved for ${ids.size} admin${if (ids.size == 1) "" else "s"}.")
                }
            },
            onToggle = {
                val (ids, invalid) = NeedlePrefs.parseTelegramAdminIds(adminIdsInput)
                if (!telegramEnabled) {
                    if (telegramToken.isBlank()) {
                        adminIdsError = "Paste the bot token from @BotFather first."
                    } else if (invalid.isNotEmpty()) {
                        adminIdsError = "These do not look like numeric user IDs: ${invalid.take(3).joinToString(", ")}"
                    } else if (ids.isEmpty()) {
                        adminIdsError = "Save at least one numeric user ID — send /myid to the bot to see yours."
                    } else {
                        adminIdsError = null
                        NeedlePrefs.setTelegramToken(context, telegramToken)
                        NeedlePrefs.setTelegramAdminIds(context, ids)
                        adminIdsInput = ids.sorted().joinToString(", ")
                        NeedlePrefs.setTelegramEnabled(context, true)
                        telegramEnabled = true
                        NeedleRemoteService.start(context, telegramToken)
                    }
                } else {
                    NeedlePrefs.setTelegramEnabled(context, false)
                    telegramEnabled = false
                    NeedleRemoteService.stop(context)
                    TelegramBridge.stop()
                }
            },
        )

        Section(
            title = "Automation",
            subtitle = "How the screen-automation loop decides and what it asks first.",
        ) {
            ToggleRow(
                title = "Use the on-device model",
                subtitle = "Otherwise tasks need a remote provider, or fall back to the built-in routines.",
                checked = settings.useOnDeviceModel,
                onCheckedChange = { scope.launch { SettingsStore.setUseOnDeviceModel(context, it) } },
            )
            ToggleRow(
                title = "Redact sensitive values",
                subtitle = "Passwords, OTPs, card numbers and IDs are masked before the model sees them.",
                checked = settings.redactSensitiveValues,
                onCheckedChange = { scope.launch { SettingsStore.setRedactSensitiveValues(context, it) } },
            )
            ToggleRow(
                title = "Confirm high-risk steps",
                subtitle = "Sending, deleting, paying or granting permissions asks again.",
                checked = settings.highRiskConfirmations,
                onCheckedChange = { scope.launch { SettingsStore.setHighRiskConfirmations(context, it) } },
            )
            ToggleRow(
                title = "Floating stop button",
                subtitle = "Shows the TaskPilot pill over other apps while a task runs.",
                checked = settings.showOverlay,
                onCheckedChange = { scope.launch { SettingsStore.setShowOverlay(context, it) } },
            )
            ToggleRow(
                title = "Log the screen summary",
                subtitle = "Adds a redacted node count for each observation.",
                checked = settings.showTreeSummary,
                onCheckedChange = { scope.launch { SettingsStore.setShowTreeSummary(context, it) } },
            )
            ToggleRow(
                title = "Log safety validation",
                subtitle = "Explains each action's risk classification.",
                checked = settings.showValidation,
                onCheckedChange = { scope.launch { SettingsStore.setShowValidation(context, it) } },
            )
            ActionRow {
                SecondaryButton(
                    text = "Accessibility settings",
                    onClick = { DevicePermissions.openAccessibilitySettings(context) },
                )
                SecondaryButton(
                    text = "App permissions",
                    onClick = { DevicePermissions.openAppSettings(context) },
                )
            }
            val a11yState = AccessibilityState.resolve(a11ySetupDone, a11yServiceOn)
            KeyValue(
                "Screen automation",
                when (a11yState) {
                    AccessibilityState.UiState.READY -> "Enabled and available"
                    AccessibilityState.UiState.DISABLED_AFTER_SETUP -> "Completed before · currently disabled"
                    AccessibilityState.UiState.NEVER_CONFIGURED -> "Not configured"
                },
            )
            if (a11yState != AccessibilityState.UiState.READY) {
                Text(
                    if (a11yState == AccessibilityState.UiState.DISABLED_AFTER_SETUP) {
                        "The service is currently off. Re-enable it above — your setup progress is kept."
                    } else {
                        "Enable the service above to use screen automation."
                    },
                    style = WahariTypography.sectionSubtitle,
                )
            }
            if (history.isNotEmpty()) {
                Text("Recent tasks", style = WahariTypography.optionTitle.copy(fontSize = 14.spFix()))
                Text("Only the command text and its outcome are stored.", style = WahariTypography.sectionSubtitle)
                history.take(6).forEach { entry ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            entry.command.take(46),
                            style = WahariTypography.sectionSubtitle,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            softWrap = true,
                        )
                        Text(
                            entry.status,
                            style = WahariTypography.optionTag,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                WahariTextButton(text = "Clear history", onClick = { scope.launch { HistoryStore.clear(context) } })
            }
        }

        Section(
            title = "Remote AI provider",
            subtitle = "Optional. Any OpenAI-compatible endpoint is used only when the on-device model is off.",
        ) {
            WahariTextField(
                value = endpointUrl,
                onValueChange = { endpointUrl = it; providerTouched = true },
                label = "Base URL",
                singleLine = true,
            )
            WahariTextField(
                value = apiPath,
                onValueChange = { apiPath = it; providerTouched = true },
                label = "Chat path",
                singleLine = true,
            )
            WahariTextField(
                value = model,
                onValueChange = { model = it; providerTouched = true },
                label = "Model",
                singleLine = true,
            )
            WahariTextField(
                value = fallbacks,
                onValueChange = { fallbacks = it; providerTouched = true },
                label = "Fallback models (one per line)",
                maxLines = 3,
            )
            WahariTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                label = if (hasApiKey) "Replace API key (stored encrypted)" else "API key",
                singleLine = true,
            )
            ActionRow {
                PrimaryButton(
                    text = "Save provider",
                    onClick = {
                        scope.launch {
                            SettingsStore.setProvider(
                                context,
                                endpointUrl,
                                model,
                                apiPath,
                                fallbacks.split('\n').map { it.trim() }.filter { it.isNotBlank() },
                            )
                            if (apiKeyInput.isNotBlank()) {
                                val saved = withContext(Dispatchers.IO) { SecureStore.saveApiKey(context, apiKeyInput) }
                                hasApiKey = withContext(Dispatchers.IO) { SecureStore.hasApiKey(context) }
                                apiKeyInput = ""
                                ChatController.addSystem(if (saved) "Provider saved; the key is encrypted with the device keystore." else "The key could not be stored.")
                            } else {
                                ChatController.addSystem("Provider settings saved.")
                            }
                            providerTouched = false
                        }
                    },
                )
                SecondaryButton(
                    text = "Clear key",
                    enabled = hasApiKey,
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { SecureStore.clearApiKey(context) }
                            hasApiKey = false
                            providerTouched = false
                            ChatController.addSystem("Stored API key removed.")
                        }
                    },
                )
            }
            KeyValue("Key stored", if (hasApiKey) "yes" else "no")
        }

        Section(title = "Answer length", subtitle = "Longer answers reserve more of the model's context.") {
            Text("Max new tokens: ${maxTokens.toInt()}", style = WahariTypography.assistantBullet)
            WahariSlider(
                value = maxTokens,
                onValueChange = { maxTokens = it },
                valueRange = 128f..1024f,
                onValueChangeFinished = { NeedlePrefs.setMaxNewTokens(context, maxTokens.toInt()) },
            )
            ToggleRow(
                title = "Show reasoning and confidence",
                subtitle = "Both come from the model itself on every turn.",
                checked = showReasoning,
                onCheckedChange = {
                    showReasoning = it
                    NeedlePrefs.setShowReasoning(context, it)
                },
            )
        }

        AboutCard(
            engineVersion = ModelRepository.ENGINE_VERSION,
            foregroundHooksActive = ActivityBridges.hasCamera,
        )
    }
}

private fun Int.spFix() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)

/** Premium remote-control card: token + admin user IDs, access status and listener health. */
@Composable
private fun RemoteControlCard(
    token: String,
    onTokenChange: (String) -> Unit,
    adminIdsInput: String,
    onAdminIdsChange: (String) -> Unit,
    adminIdsError: String?,
    running: Boolean,
    handled: Int,
    denied: Int,
    lastDeniedUser: Long?,
    lastMessage: String?,
    listenerError: String?,
    enabled: Boolean,
    onSave: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(28.dp)
    val innerShape = RoundedCornerShape(18.dp)
    val (parsedIds, _) = remember(adminIdsInput) {
        NeedlePrefs.parseTelegramAdminIds(adminIdsInput)
    }
    val secured = parsedIds.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF1A2230), Color(0xFF141417), Color(0xFF101012)),
                ),
            )
            .border(1.dp, Color(0x2BFFFFFF), cardShape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Eyebrow + status pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "REMOTE CONTROL",
                style = WahariTypography.optionTag.copy(
                    color = WahariTokens.textMuted,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = androidx.compose.ui.unit.TextUnit(1.6f, androidx.compose.ui.unit.TextUnitType.Sp),
                ),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(
                    text = if (running) "RUNNING" else "STOPPED",
                    active = running,
                )
                StatusPill(
                    text = if (secured) "RESTRICTED" else "NOT SECURED",
                    active = secured,
                )
            }
        }

        // Header: shield mark + title + toggle affordance
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF2B9BF0), Color(0xFF155A9C)),
                        ),
                    )
                    .border(1.dp, Color(0x66FFFFFF), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Telegram access",
                    style = WahariTypography.sheetTitle.copy(fontSize = androidx.compose.ui.unit.TextUnit(19f, androidx.compose.ui.unit.TextUnitType.Sp)),
                )
                Text(
                    if (secured) {
                        "Only ${parsedIds.size} admin${if (parsedIds.size == 1) "" else "s"} can run commands."
                    } else {
                        "Add your user ID to lock the bot to you."
                    },
                    style = WahariTypography.sectionSubtitle.copy(color = WahariTokens.textSecondary),
                )
            }
        }

        Text(
            "Text the phone through a Telegram bot. Messages run the same on-device model — every sender ID is checked against your allowlist before anything runs.",
            style = WahariTypography.assistantBullet.copy(
                color = WahariTokens.textSecondary,
                lineHeight = androidx.compose.ui.unit.TextUnit(21f, androidx.compose.ui.unit.TextUnitType.Sp),
            ),
        )

        // Token + user ID fields side by side
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WahariTextField(
                value = token,
                onValueChange = onTokenChange,
                label = "Bot token from @BotFather",
                placeholder = "123456:ABC-DEF…",
                singleLine = true,
                modifier = Modifier.weight(1.6f),
            )
            WahariTextField(
                value = adminIdsInput,
                onValueChange = onAdminIdsChange,
                label = "Admin user IDs",
                placeholder = "123456789",
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            "Send /myid to the bot to see a numeric ID. Separate several IDs with commas.",
            style = WahariTypography.optionTag,
        )
        adminIdsError?.let {
            Text(it, style = WahariTypography.sectionSubtitle.copy(color = WahariTokens.danger))
        }

        ActionRow {
            PrimaryButton(text = "Save access", onClick = onSave)
            SecondaryButton(text = if (enabled) "Turn off" else "Turn on", onClick = onToggle)
        }

        // Listener health panel
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(innerShape)
                .background(Color.Black.copy(alpha = 0.32f))
                .border(1.dp, Color(0x1AFFFFFF), innerShape)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            RemoteMetaRow(label = "Listener", value = if (running) "Running" else "Stopped")
            AboutDivider()
            RemoteMetaRow(
                label = "Access",
                value = if (secured) {
                    "Restricted to ${parsedIds.size} admin${if (parsedIds.size == 1) "" else "s"}"
                } else {
                    "Locked — save an ID"
                },
            )
            if (handled > 0) {
                AboutDivider()
                RemoteMetaRow(label = "Commands handled", value = handled.toString())
            }
            if (denied > 0) {
                AboutDivider()
                RemoteMetaRow(
                    label = "Blocked senders",
                    value = denied.toString() + (lastDeniedUser?.let { " · last $it" } ?: ""),
                )
            }
        }
        lastMessage?.let { MonoBlock("last: $it") }
        listenerError?.let {
            Text(it, style = WahariTypography.sectionSubtitle.copy(color = WahariTokens.danger))
        }

        // Security promise
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(innerShape)
                .background(Color(0x141D88E5))
                .border(1.dp, Color(0x2E1D88E5), innerShape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0x221D88E5)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = null,
                    tint = Color(0xFF7AB8FF),
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.weight(1f)) {
                Text(
                    "Allowlist enforced on-device",
                    style = WahariTypography.assistantBullet.copy(
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = androidx.compose.ui.unit.TextUnit(13.5f, androidx.compose.ui.unit.TextUnitType.Sp),
                    ),
                )
                Text(
                    "Unknown senders get a refusal and never reach the model.",
                    style = WahariTypography.sectionSubtitle.copy(color = Color(0xFFA9C7E8)),
                )
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, active: Boolean) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (active) Color(0x224ADE80) else Color(0x228E8E96))
            .border(
                1.dp,
                if (active) Color(0x554ADE80) else Color(0x338E8E96),
                CircleShape,
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            style = WahariTypography.optionTag.copy(
                color = if (active) Color(0xFF86EFAC) else WahariTokens.textSecondary,
                fontWeight = FontWeight.SemiBold,
                fontSize = androidx.compose.ui.unit.TextUnit(10.5f, androidx.compose.ui.unit.TextUnitType.Sp),
            ),
        )
    }
}

@Composable
private fun RemoteMetaRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            label,
            style = WahariTypography.sectionSubtitle.copy(color = WahariTokens.textMuted),
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            value,
            style = WahariTypography.assistantBullet.copy(
                color = WahariTokens.textPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = androidx.compose.ui.unit.TextUnit(13.5f, androidx.compose.ui.unit.TextUnitType.Sp),
            ),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

/** Premium About card: brand header, spec sheet, privacy promise and provenance. */
@Composable
private fun AboutCard(
    engineVersion: String,
    foregroundHooksActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val cardShape = RoundedCornerShape(28.dp)
    val innerShape = RoundedCornerShape(18.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF1D2028), Color(0xFF141417), Color(0xFF101012)),
                ),
            )
            .border(1.dp, Color(0x2BFFFFFF), cardShape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Eyebrow + premium badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "ABOUT",
                style = WahariTypography.optionTag.copy(
                    color = WahariTokens.textMuted,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = androidx.compose.ui.unit.TextUnit(1.6f, androidx.compose.ui.unit.TextUnitType.Sp),
                ),
            )
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0x1F1D88E5))
                    .border(1.dp, Color(0x551D88E5), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    "STABLE BUILD",
                    style = WahariTypography.optionTag.copy(
                        color = Color(0xFF7AB8FF),
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = androidx.compose.ui.unit.TextUnit(0.8f, androidx.compose.ui.unit.TextUnitType.Sp),
                        fontSize = androidx.compose.ui.unit.TextUnit(10.5f, androidx.compose.ui.unit.TextUnitType.Sp),
                    ),
                )
            }
        }

        // Brand header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF2B9BF0), Color(0xFF155A9C)),
                        ),
                    )
                    .border(1.dp, Color(0x66FFFFFF), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "W",
                    style = WahariTypography.pageTitle.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = androidx.compose.ui.unit.TextUnit(24f, androidx.compose.ui.unit.TextUnitType.Sp),
                    ),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "Wahari",
                        style = WahariTypography.sheetTitle.copy(fontSize = androidx.compose.ui.unit.TextUnit(19f, androidx.compose.ui.unit.TextUnitType.Sp)),
                    )
                    Icon(
                        imageVector = Icons.Filled.Verified,
                        contentDescription = null,
                        tint = Color(0xFF5AA9FF),
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    "Crafted by AjiroDesu",
                    style = WahariTypography.sectionSubtitle.copy(color = WahariTokens.textSecondary),
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1D88E5))
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            ) {
                Text(
                    "v$engineVersion",
                    style = WahariTypography.optionTag.copy(color = Color.White, fontWeight = FontWeight.SemiBold),
                )
            }
        }

        Text(
            "Wahari turns your words into on-device phone actions. The model, accessibility snapshots and history never leave this phone — camera and fingerprint checks are the only steps that need you on screen.",
            style = WahariTypography.assistantBullet.copy(
                color = WahariTokens.textSecondary,
                lineHeight = androidx.compose.ui.unit.TextUnit(21f, androidx.compose.ui.unit.TextUnitType.Sp),
            ),
        )

        // Spec sheet
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(innerShape)
                .background(Color.Black.copy(alpha = 0.32f))
                .border(1.dp, Color(0x1AFFFFFF), innerShape)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            AboutMetaRow(icon = Icons.Filled.Smartphone, label = "App", value = "Wahari")
            AboutDivider()
            AboutMetaRow(icon = Icons.Filled.Memory, label = "Engine", value = "Needle · Apache-2.0")
            AboutDivider()
            AboutMetaRow(icon = Icons.Filled.SmartToy, label = "Automation", value = "TaskPilot (MIT)")
            AboutDivider()
            AboutMetaRow(icon = Icons.Filled.Person, label = "Core author", value = "TherealCitali")
            AboutDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Visibility,
                    contentDescription = null,
                    tint = WahariTokens.textMuted,
                    modifier = Modifier.size(17.dp),
                )
                Text(
                    "Foreground hooks",
                    style = WahariTypography.sectionSubtitle.copy(color = WahariTokens.textMuted),
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (foregroundHooksActive) Color(0x224ADE80) else Color(0x228E8E96),
                        )
                        .border(
                            1.dp,
                            if (foregroundHooksActive) Color(0x554ADE80) else Color(0x338E8E96),
                            CircleShape,
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (foregroundHooksActive) WahariTokens.success else WahariTokens.textMuted,
                                ),
                        )
                        Text(
                            if (foregroundHooksActive) "Active" else "Inactive",
                            style = WahariTypography.optionTag.copy(
                                color = if (foregroundHooksActive) Color(0xFF86EFAC) else WahariTokens.textSecondary,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    }
                }
            }
        }

        // Privacy promise
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(innerShape)
                .background(Color(0x144ADE80))
                .border(1.dp, Color(0x2E4ADE80), innerShape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0x224ADE80)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = null,
                    tint = Color(0xFF86EFAC),
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.weight(1f)) {
                Text(
                    "100% offline · Private by design",
                    style = WahariTypography.assistantBullet.copy(
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = androidx.compose.ui.unit.TextUnit(13.5f, androidx.compose.ui.unit.TextUnitType.Sp),
                    ),
                )
                Text(
                    "Nothing is uploaded. Everything stays on this device.",
                    style = WahariTypography.sectionSubtitle.copy(color = Color(0xFFB7E4C7)),
                )
            }
        }

        // Provenance link
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(innerShape)
                .background(WahariTokens.bgCard)
                .border(1.dp, WahariTokens.borderSubtle, innerShape)
                .clickable { uriHandler.openUri("https://github.com/TherealCitali") }
                .padding(horizontal = 14.dp, vertical = 13.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(WahariTokens.bgIcon),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Code,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        "Open-source core",
                        style = WahariTypography.sectionSubtitle.copy(color = WahariTokens.textMuted),
                    )
                    Text(
                        "github.com/TherealCitali",
                        style = WahariTypography.assistantBullet.copy(
                            color = WahariTokens.textPrimary,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
                Icon(
                    imageVector = Icons.Filled.OpenInNew,
                    contentDescription = "Open GitHub",
                    tint = WahariTokens.accent,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Text(
            "© 2026 Wahari · Engine by Cactus Compute",
            style = WahariTypography.optionTag.copy(color = WahariTokens.textMuted),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun AboutMetaRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = WahariTokens.textMuted,
            modifier = Modifier.size(17.dp),
        )
        Text(
            label,
            style = WahariTypography.sectionSubtitle.copy(color = WahariTokens.textMuted),
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            value,
            style = WahariTypography.assistantBullet.copy(
                color = WahariTokens.textPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = androidx.compose.ui.unit.TextUnit(13.5f, androidx.compose.ui.unit.TextUnitType.Sp),
            ),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

@Composable
private fun AboutDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0x14FFFFFF)),
    )
}

@Preview(name = "Settings page", widthDp = 412, heightDp = 890)
@Composable
private fun PreviewSettings() {
    NeedleTheme {
        PageShell(title = "Settings", onBack = {}) { SettingsContent() }
    }
}
