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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.citali.needle.engine.NeedlePrefs
import dev.citali.needle.tools.DevicePermissions
import dev.citali.needle.tools.PermissionPlan
import dev.citali.needle.ui.theme.WahariTokens
import dev.citali.needle.ui.theme.WahariTypography

/**
 * Shared permission UI for the setup Permissions page and the Tools screen.
 *
 * One Allow-all sequence plus grouped capability rows with live status.
 * State always comes from the OS on read; [rememberPermissionBoardState]
 * re-snapshots on resume and after every request round.
 *
 * @param fakeRequest test hook: replaces the system dialog with an immediate
 * callback. Null in production.
 */
data class PermissionBoardState(
    val states: List<PermissionPlan.CapState>,
    val visited: Boolean,
    val runtimeComplete: Boolean,
    val allowAllRunning: Boolean,
    val startAllowAll: () -> Unit,
    val requestCapability: (PermissionPlan.Capability) -> Unit,
)

@Composable
fun rememberPermissionBoardState(
    fakeRequest: ((List<String>, (Map<String, Boolean>) -> Unit) -> Unit)? = null,
    setupOnly: Boolean = false,
): PermissionBoardState {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var allowAllRunning by rememberSaveable { mutableStateOf(false) }
    // Monotonic chain trigger: every completed round bumps it, so the
    // follower effect restarts exactly once per round. (A boolean flag
    // re-set synchronously inside the effect collapses true→true and the
    // chain stalls after two batches.)
    var advanceSignal by remember { mutableIntStateOf(0) }
    // True while a system dialog is on screen: never fire a second request
    // on top of it (Android drops simultaneous requests silently).
    var awaitingResult by remember { mutableStateOf(false) }
    // Batches already tried in this Allow-all run. Denied batches stay
    // missing, so without this the chain would retry the first denial
    // forever; with it, a denial moves the chain to the next batch.
    val attemptedBatches = rememberSaveable(
        saver = androidx.compose.runtime.saveable.Saver(
            save = { ArrayList(it) },
            restore = { it.toMutableStateList() },
        ),
    ) {
        mutableStateListOf<String>()
    }

    val states = remember(tick) { PermissionPlan.snapshot(context) }
    val visited = remember(tick) { NeedlePrefs.permissionsEverRequested(context) }
    val runtimeComplete = remember(states) { PermissionPlan.runtimeComplete(states) }

    val realLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        awaitingResult = false
        NeedlePrefs.markPermissionsRequested(context, result.keys)
        tick++
        if (allowAllRunning) advanceSignal++
    }

    fun fireBatch(batch: List<String>) {
        if (batch.isEmpty()) return
        android.util.Log.d("PermissionBoard", "fireBatch: $batch fake=${fakeRequest != null}")
        awaitingResult = true
        if (fakeRequest != null) {
            fakeRequest(batch) { result ->
                awaitingResult = false
                NeedlePrefs.markPermissionsRequested(context, result.keys)
                tick++
                if (allowAllRunning) advanceSignal++
            }
        } else {
            realLauncher.launch(batch.toTypedArray())
        }
    }

    /** All missing batches, minus permanently-denied permissions (a dialog
     * for those would silently do nothing) and minus tried batches. */
    fun pendingBatches(): List<List<String>> {
        val sdk = android.os.Build.VERSION.SDK_INT
        val (telephony, camera) = PermissionPlan.hardwareFlags(context)
        val requested = NeedlePrefs.requestedPermissions(context)
        val caps = PermissionPlan.capabilities.let { all ->
            if (setupOnly) all.filter { it.inSetup } else all
        }
        return PermissionPlan.requestBatches(
            caps = caps,
            sdk = sdk,
            hasTelephony = telephony,
            hasCamera = camera,
            granted = { DevicePermissions.granted(context, it) },
        ).map { batch ->
            batch.filter { perm ->
                PermissionPlan.permStatus(
                    granted = false,
                    canAskAgain = DevicePermissions.canAskAgain(context, perm),
                    requestedBefore = perm in requested,
                ) != PermissionPlan.PermStatus.PERMANENTLY_DENIED
            }
        }.filter { it.isNotEmpty() && it.joinToString(",") !in attemptedBatches }
    }

    fun launchNextBatch() {
        android.util.Log.d(
            "PermissionBoard",
            "launchNextBatch entry awaiting=$awaitingResult running=$allowAllRunning",
        )
        if (awaitingResult) return
        val next = pendingBatches().firstOrNull()
        android.util.Log.d("PermissionBoard", "launchNextBatch: next=$next")
        if (next == null) {
            allowAllRunning = false
        } else {
            attemptedBatches.add(next.joinToString(","))
            fireBatch(next)
        }
    }

    fun openSpecial(kind: PermissionPlan.SpecialKind) {
        when (kind) {
            PermissionPlan.SpecialKind.WRITE_SETTINGS -> DevicePermissions.openWriteSettings(context)
            PermissionPlan.SpecialKind.BATTERY -> DevicePermissions.openBatterySettings(context)
            PermissionPlan.SpecialKind.AUTO_REVOKE -> DevicePermissions.openAutoRevokeSettings(context)
        }
        tick++
    }

    fun requestCapability(cap: PermissionPlan.Capability) {
        android.util.Log.d("PermissionBoard", "requestCapability: ${cap.id}")
        // A manual tap owns the dialog slot: stop any Allow-all chain first
        // so two requests never overlap (the system drops the second one).
        allowAllRunning = false
        cap.special?.let {
            openSpecial(it)
            return
        }
        val sdk = android.os.Build.VERSION.SDK_INT
        val requested = NeedlePrefs.requestedPermissions(context)
        val missing = PermissionPlan.runtimePermissions(cap, sdk)
            .filterNot { DevicePermissions.granted(context, it) }
        if (missing.isEmpty()) {
            tick++
            return
        }
        val askable = missing.filter { perm ->
            PermissionPlan.permStatus(
                granted = false,
                canAskAgain = DevicePermissions.canAskAgain(context, perm),
                requestedBefore = perm in requested,
            ) != PermissionPlan.PermStatus.PERMANENTLY_DENIED
        }
        if (askable.isEmpty()) {
            DevicePermissions.openAppSettings(context)
        } else {
            fireBatch(askable)
        }
    }

    // Continues the Allow-all chain after each round. Guarded by the running
    // flag so a manual row tap that stopped the chain cannot fire a stray
    // batch from a signal that was already in flight.
    LaunchedEffect(advanceSignal) {
        android.util.Log.d("PermissionBoard", "chain-effect signal=$advanceSignal running=$allowAllRunning")
        if (advanceSignal > 0 && allowAllRunning) {
            launchNextBatch()
        }
    }

    fun startAllowAll() {
        android.util.Log.d("PermissionBoard", "startAllowAll")
        allowAllRunning = true
        awaitingResult = false
        attemptedBatches.clear()
        launchNextBatch()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                tick++
                // Picks the chain back up after rotation, process death, or a
                // dropped dialog. No-op unless a run is active and idle.
                if (allowAllRunning) launchNextBatch()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return PermissionBoardState(
        states = states,
        visited = visited,
        runtimeComplete = runtimeComplete,
        allowAllRunning = allowAllRunning,
        startAllowAll = ::startAllowAll,
        requestCapability = ::requestCapability,
    )
}

private fun capabilityIcon(id: String): ImageVector = when (id) {
    "mic" -> dev.citali.needle.ui.theme.WahariIcons.mic
    "notifications" -> dev.citali.needle.ui.theme.WahariIcons.bell
    "sms" -> dev.citali.needle.ui.theme.WahariIcons.messageSquare
    "calls" -> dev.citali.needle.ui.theme.WahariIcons.phone
    "call_log" -> dev.citali.needle.ui.theme.WahariIcons.history
    "phone_state" -> dev.citali.needle.ui.theme.WahariIcons.smartphone
    "contacts" -> dev.citali.needle.ui.theme.WahariIcons.users
    "camera" -> dev.citali.needle.ui.theme.WahariIcons.camera
    "location" -> dev.citali.needle.ui.theme.WahariIcons.mapPin
    "media_files" -> dev.citali.needle.ui.theme.WahariIcons.audioLines
    "brightness" -> dev.citali.needle.ui.theme.WahariIcons.sun
    "battery" -> dev.citali.needle.ui.theme.WahariIcons.batteryCharging
    else -> dev.citali.needle.ui.theme.WahariIcons.shieldAlert
}

private fun groupTitle(group: PermissionPlan.Group): String = when (group) {
    PermissionPlan.Group.VOICE_NOTIFY -> "Voice and notifications"
    PermissionPlan.Group.MESSAGES_CALLS -> "Messages and calls"
    PermissionPlan.Group.DEVICE -> "Device"
    PermissionPlan.Group.SPECIAL -> "Special access"
}

private data class ChipStyle(val text: String, val color: Color)

private fun chipFor(status: PermissionPlan.CapStatus): ChipStyle = when (status) {
    PermissionPlan.CapStatus.GRANTED -> ChipStyle("Allowed", WahariTokens.success)
    PermissionPlan.CapStatus.APPROXIMATE -> ChipStyle("Approximate only", WahariTokens.accent)
    PermissionPlan.CapStatus.PERMANENTLY_DENIED -> ChipStyle("Blocked · open Settings", WahariTokens.danger)
    PermissionPlan.CapStatus.DENIED -> ChipStyle("Not allowed", WahariTokens.textMuted)
}

private fun actionFor(state: PermissionPlan.CapState): String? = when (state.status) {
    PermissionPlan.CapStatus.GRANTED -> null
    PermissionPlan.CapStatus.APPROXIMATE ->
        if (state.capability.id == "location") "Allow precise" else null
    PermissionPlan.CapStatus.PERMANENTLY_DENIED -> "Open app settings"
    PermissionPlan.CapStatus.DENIED -> when (state.capability.special) {
        PermissionPlan.SpecialKind.WRITE_SETTINGS -> "Allow"
        PermissionPlan.SpecialKind.BATTERY -> "Allow"
        PermissionPlan.SpecialKind.AUTO_REVOKE -> "Turn off auto-reset"
        null -> "Allow"
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.4f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            style = WahariTypography.optionTag.copy(color = Color.White, fontWeight = FontWeight.SemiBold),
        )
    }
}

@Composable
private fun CapabilityRow(
    state: PermissionPlan.CapState,
    onAction: () -> Unit,
) {
    val chip = chipFor(state.status)
    val actionLabel = actionFor(state)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .then(
                if (actionLabel != null) {
                    Modifier.clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = onAction,
                    )
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(WahariTokens.bgIcon),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = capabilityIcon(state.capability.id),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                state.capability.title,
                style = WahariTypography.assistantBullet.copy(
                    color = WahariTokens.textPrimary,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Text(
                state.unlocks,
                style = WahariTypography.sectionSubtitle.copy(color = WahariTokens.textSecondary),
            )
            if (state.status == PermissionPlan.CapStatus.PERMANENTLY_DENIED) {
                Text(
                    "Android will not show the request again. Open app settings to allow it.",
                    style = WahariTypography.sectionSubtitle.copy(color = WahariTokens.textMuted),
                )
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusChip(chip.text, chip.color)
            if (actionLabel != null) {
                WahariTextButton(text = actionLabel, onClick = onAction)
            }
        }
    }
}

/**
 * Allow-all button, grouped capability rows and the auto-reset tip.
 * Embed in a page; the page owns Continue/Skip or nothing.
 */
@Composable
fun PermissionBoard(
    state: PermissionBoardState = rememberPermissionBoardState(),
    showAllowAll: Boolean = true,
    setupOnly: Boolean = false,
) {
    if (showAllowAll) {
        ActionRow {
            PrimaryButton(
                text = if (state.allowAllRunning) "Requesting…" else "Allow all",
                enabled = !state.allowAllRunning,
                onClick = state.startAllowAll,
            )
        }
        if (state.allowAllRunning) {
            Text(
                "Answer each system prompt once. The next one appears automatically.",
                style = WahariTypography.sectionSubtitle.copy(color = WahariTokens.textSecondary),
            )
        }
    }
    // Setup shows exactly the specified membership (no stored-media row);
    // Tools shows everything, including capabilities setup skips.
    val visible = state.states.filter { !setupOnly || it.capability.inSetup }
    PermissionPlan.Group.entries.forEach { group ->
        val rows = visible.filter { it.capability.group == group }
        if (rows.isNotEmpty()) {
            Text(
                groupTitle(group),
                style = WahariTypography.optionTitle.copy(
                    color = WahariTokens.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                ),
                modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
            )
            rows.forEach { row ->
                CapabilityRow(state = row, onAction = { state.requestCapability(row.capability) })
            }
        }
    }
}
