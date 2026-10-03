package dev.citali.needle.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.citali.needle.engine.ModelDownloadController
import dev.citali.needle.engine.ModelRepository
import dev.citali.needle.engine.NeedleEngine
import dev.citali.needle.engine.NeedlePrefs
import dev.citali.needle.pilot.accessibility.NeedleAccessibilityService
import dev.citali.needle.tools.DevicePermissions
import dev.citali.needle.ui.theme.NeedleTheme
import dev.citali.needle.ui.theme.WahariLayout
import dev.citali.needle.ui.theme.WahariTokens
import dev.citali.needle.ui.theme.WahariTypography
import dev.citali.needle.ui.theme.contentSidePadding
import kotlinx.coroutines.launch

private enum class SetupPage { Intro, Needle, Accessibility }

/** First incomplete setup step, or null when everything is done. */
private fun firstIncompleteStep(context: android.content.Context): SetupPage? =
    if (!ModelRepository.hasWeights(context)) SetupPage.Needle
    else if (!DevicePermissions.isAccessibilityServiceEnabled(context)) SetupPage.Accessibility
    else null

/** Live service state: the OS setting is the truth, the bound instance a fast path. */
private fun isServiceOn(context: android.content.Context): Boolean =
    NeedleAccessibilityService.isConnected() || DevicePermissions.isAccessibilityServiceEnabled(context)

/**
 * First-run setup: Intro (once) → Needle 3 → Accessibility → app.
 *
 * State is persisted in [NeedlePrefs] on every transition, so rotation,
 * recreation, process death and restart all resume exactly where setup
 * stopped. Back never reopens the Intro once it is completed.
 */
@Composable
fun SetupFlow(onFinished: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val history = rememberSaveable(
        saver = androidx.compose.runtime.saveable.Saver(
            save = { ArrayList(it) },
            restore = { it.toMutableStateList() },
        ),
    ) {
        mutableStateListOf(
            if (!NeedlePrefs.introCompleted(context)) {
                SetupPage.Intro.name
            } else {
                firstIncompleteStep(context)?.name ?: SetupPage.Accessibility.name
            },
        )
    }
    val current = SetupPage.valueOf(history.last())
    var accessibilityOn by remember { mutableStateOf(isServiceOn(context)) }
    var weightsPresent by remember { mutableStateOf(ModelRepository.hasWeights(context)) }
    var a11ySetupDone by remember { mutableStateOf(NeedlePrefs.accessibilitySetupCompleted(context)) }

    fun persistStep(page: SetupPage) {
        NeedlePrefs.setSetupStep(
            context,
            when (page) {
                SetupPage.Intro -> NeedlePrefs.SETUP_INTRO
                SetupPage.Needle -> NeedlePrefs.SETUP_NEEDLE
                SetupPage.Accessibility -> NeedlePrefs.SETUP_ACCESSIBILITY
            },
        )
    }

    fun goTo(page: SetupPage) {
        if (history.lastOrNull() != page.name) history.add(page.name)
        persistStep(page)
    }

    fun goBack() {
        if (history.size > 1) {
            history.removeAt(history.lastIndex)
            persistStep(SetupPage.valueOf(history.last()))
        }
    }

    fun finish() {
        NeedlePrefs.setSetupStep(context, NeedlePrefs.SETUP_DONE)
        NeedlePrefs.setSetupComplete(context, true)
        if (isServiceOn(context)) {
            NeedlePrefs.setAccessibilitySetupCompleted(context, true)
        }
        onFinished()
    }

    fun advanceFrom(page: SetupPage) {
        // Continue means the current step just resolved: move to the first
        // step that is still incomplete, or finish when nothing is left.
        val next = firstIncompleteStep(context)
        if (next == null || next == page) {
            finish()
        } else {
            goTo(next)
        }
    }

    fun skipFrom(page: SetupPage) {
        // Skip leaves the step unresolved and walks the fixed order, so a
        // skipped Needle step still leads to Accessibility instead of finishing.
        val order = listOf(SetupPage.Needle, SetupPage.Accessibility)
        val remaining = order.drop(order.indexOf(page) + 1)
        if (remaining.isEmpty()) {
            finish()
        } else {
            goTo(remaining.first())
        }
    }

    // Re-check real states on every resume: model file and service state.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityOn = isServiceOn(context)
                weightsPresent = ModelRepository.hasWeights(context)
                a11ySetupDone = NeedlePrefs.accessibilitySetupCompleted(context)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // Enabling the service in system settings records setup completion and
    // auto-advances the flow. Completion is durable; the live OS state is
    // re-queried on every resume instead of trusting the stored flag.
    LaunchedEffect(accessibilityOn, current) {
        if (accessibilityOn && !NeedlePrefs.accessibilitySetupCompleted(context)) {
            NeedlePrefs.setAccessibilitySetupCompleted(context, true)
            a11ySetupDone = true
        }
        if (current == SetupPage.Accessibility && accessibilityOn) {
            advanceFrom(SetupPage.Accessibility)
        }
    }

    // Back walks the page history; the Intro is never re-entered this way.
    BackHandler(enabled = history.size > 1) { goBack() }
    BackHandler(enabled = history.size <= 1) { /* absorb: setup has no earlier screen */ }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val sidePad = contentSidePadding()
    Box(
        modifier = Modifier.fillMaxSize().background(WahariTokens.bgMain),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                (slideInHorizontally(tween(300, easing = WahariTokens.mainEasing)) { it } + fadeIn(tween(300)))
                    .togetherWith(
                        (slideOutHorizontally(tween(300, easing = WahariTokens.mainEasing)) { -it } + fadeOut(tween(300))),
                    )
            },
            label = "setup",
        ) { page ->
            LazyColumn(
                modifier = Modifier.widthIn(max = WahariLayout.contentMax).fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = sidePad,
                    end = sidePad,
                    top = statusTop + 66.dp,
                    bottom = 28.dp + navBottom,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    when (page) {
                        SetupPage.Intro -> IntroStep(
                            onContinue = {
                                NeedlePrefs.setIntroCompleted(context, true)
                                advanceFrom(SetupPage.Intro)
                            },
                            onSkip = {
                                NeedlePrefs.setIntroCompleted(context, true)
                                advanceFrom(SetupPage.Intro)
                            },
                        )
                        SetupPage.Needle -> NeedleStep(
                            weightsPresent = weightsPresent,
                            onWeightsChanged = { weightsPresent = ModelRepository.hasWeights(context) },
                            onContinue = { advanceFrom(SetupPage.Needle) },
                            onSkip = { skipFrom(SetupPage.Needle) },
                        )
                        SetupPage.Accessibility -> AccessibilityStep(
                            enabled = accessibilityOn,
                            setupCompletedBefore = a11ySetupDone,
                            onContinue = { advanceFrom(SetupPage.Accessibility) },
                            onSkip = { skipFrom(SetupPage.Accessibility) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IntroStep(onContinue: () -> Unit, onSkip: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Wahari", style = WahariTypography.sidebarWordmark)
        Text(
            "An on-device agent that acts on your words. Everything runs on this phone.",
            style = WahariTypography.assistantBody.copy(color = WahariTokens.textMuted),
        )
        Section(
            title = "Chat that acts",
            subtitle = "Needle 3 picks the tool that matches your sentence: flashlight, battery, SMS, apps.",
        ) {}
        Section(
            title = "Screen automation",
            subtitle = "Describe a job and the agent drives the screen one validated action at a time.",
        ) {}
        ActionRow {
            PrimaryButton(text = "Continue", onClick = onContinue)
            WahariTextButton(text = "Skip", onClick = onSkip)
        }
    }
}

@Composable
private fun NeedleStep(
    weightsPresent: Boolean,
    onWeightsChanged: () -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val download by ModelDownloadController.state.collectAsStateWithLifecycle()
    var pendingSource by remember { mutableStateOf("") }
    var verifying by remember { mutableStateOf(false) }
    var verified by remember { mutableStateOf(false) }
    var verifyError by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            pendingSource = NeedlePrefs.NEEDLE3_SRC_IMPORT
            verified = false
            verifyError = null
            ModelDownloadController.import(context, uri)
        }
    }

    // Record where a successful install came from; pre-existing files count as existing.
    // Download/import already checksum-validate, so their success also marks verified.
    // The file check refreshes on every state change: nothing else observes the
    // download finishing while this page is visible.
    LaunchedEffect(download.message, download.error, download.running) {
        onWeightsChanged()
        if (!download.error && download.message != null && !download.running &&
            ModelRepository.hasWeights(context)
        ) {
            if (NeedlePrefs.needle3InstallSource(context).isBlank()) {
                NeedlePrefs.setNeedle3InstallSource(
                    context,
                    pendingSource.ifBlank { NeedlePrefs.NEEDLE3_SRC_EXISTING },
                )
            }
            if (pendingSource.isNotBlank()) {
                verified = true
                verifyError = null
                pendingSource = ""
                NeedleEngine.refresh(context)
            }
        }
    }

    fun startVerify() {
        verifying = true
        verifyError = null
        scope.launch {
            val ok = ModelRepository.verify(context)
            verifying = false
            if (ok) {
                verified = true
                if (NeedlePrefs.needle3InstallSource(context).isBlank()) {
                    NeedlePrefs.setNeedle3InstallSource(
                        context,
                        pendingSource.ifBlank { NeedlePrefs.NEEDLE3_SRC_EXISTING },
                    )
                }
                NeedleEngine.refresh(context)
            } else {
                verified = false
                verifyError = "That file did not pass validation. Download or import the model again."
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("On-device model", style = WahariTypography.pageTitle)
        Section(
            title = "Cactus Needle 3",
            subtitle = "Needle ${ModelRepository.ENGINE_VERSION} by Cactus Compute, " +
                "${ModelRepository.humanSize(ModelRepository.WEIGHTS_BYTES)} of weights, runs entirely offline. " +
                "Downloaded once, verified, then never leaves this phone.",
        ) {
            KeyValue("Status", if (weightsPresent) "on this device" else "not installed")
            if (download.running) {
                WahariProgress(fraction = { download.fraction })
                Text(download.writtenLabel, style = WahariTypography.optionTag)
                SecondaryButton(text = "Cancel", onClick = { ModelDownloadController.cancel() })
            } else {
                ActionRow {
                    PrimaryButton(
                        text = "Download Cactus Needle 3",
                        onClick = {
                            pendingSource = NeedlePrefs.NEEDLE3_SRC_DOWNLOAD
                            verified = false
                            verifyError = null
                            ModelDownloadController.download(context)
                        },
                    )
                }
                ActionRow {
                    SecondaryButton(
                        text = "Import Cactus Needle 3",
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
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
            verifyError?.let { error ->
                Text(error, style = WahariTypography.sectionSubtitle.copy(color = WahariTokens.danger))
            }
            if (weightsPresent && !verified && !verifying && !download.running) {
                ActionRow {
                    SecondaryButton(text = "Verify & continue", onClick = { startVerify() })
                }
            }
            if (verifying) {
                Text("Verifying checksum…", style = WahariTypography.optionTag)
            }
            if (verified) {
                Text(
                    "Model verified and ready.",
                    style = WahariTypography.sectionSubtitle.copy(color = WahariTokens.accent),
                )
            }
        }
        ActionRow {
            PrimaryButton(text = "Continue", enabled = verified, onClick = onContinue)
            WahariTextButton(text = "Skip", onClick = onSkip)
        }
        if (!verified) {
            Text(
                "Continue unlocks once the model is verified. You can also skip and install it later from Settings.",
                style = WahariTypography.sectionSubtitle,
            )
        }
    }
}

@Composable
private fun AccessibilityStep(
    enabled: Boolean,
    setupCompletedBefore: Boolean,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Screen automation", style = WahariTypography.pageTitle)
        Section(
            title = "Accessibility access",
            subtitle = "Screen automation needs Accessibility access so the agent can see supported " +
                "interface elements and perform one approved action at a time.",
        ) {
            KeyValue(
                "Service",
                when {
                    enabled -> "Accessibility service is enabled"
                    setupCompletedBefore -> "Completed before · currently disabled"
                    else -> "Accessibility service is disabled"
                },
            )
            if (!enabled && setupCompletedBefore) {
                Text(
                    "You completed this setup before. Re-enable the service below — nothing else needs redoing.",
                    style = WahariTypography.sectionSubtitle,
                )
            }
            ActionRow {
                PrimaryButton(
                    text = "Open Accessibility Settings",
                    onClick = { DevicePermissions.openAccessibilitySettings(context) },
                )
            }
            Text(
                "This opens the real Android settings. Returning here re-checks the service automatically.",
                style = WahariTypography.sectionSubtitle,
            )
        }
        ActionRow {
            PrimaryButton(text = "Continue", enabled = enabled, onClick = onContinue)
            WahariTextButton(text = "Skip", onClick = onSkip)
        }
    }
}

@Preview(name = "Setup intro", widthDp = 412, heightDp = 890)
@Composable
private fun PreviewSetupIntro() {
    NeedleTheme {
        Box(Modifier.fillMaxSize().background(WahariTokens.bgMain)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IntroStep(onContinue = {}, onSkip = {})
            }
        }
    }
}

@Preview(name = "Setup needle", widthDp = 412, heightDp = 890)
@Composable
private fun PreviewSetupNeedle() {
    NeedleTheme {
        Box(Modifier.fillMaxSize().background(WahariTokens.bgMain)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NeedleStep(weightsPresent = false, onWeightsChanged = {}, onContinue = {}, onSkip = {})
            }
        }
    }
}

@Preview(name = "Setup accessibility", widthDp = 412, heightDp = 890)
@Composable
private fun PreviewSetupAccessibility() {
    NeedleTheme {
        Box(Modifier.fillMaxSize().background(WahariTokens.bgMain)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AccessibilityStep(enabled = false, setupCompletedBefore = false, onContinue = {}, onSkip = {})
            }
        }
    }
}
