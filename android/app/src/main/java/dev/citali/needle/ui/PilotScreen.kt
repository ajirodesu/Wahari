package dev.citali.needle.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.citali.needle.pilot.agent.AgentEngine
import dev.citali.needle.pilot.agent.Plan
import dev.citali.needle.tools.DevicePermissions
import dev.citali.needle.ui.theme.WahariLayout
import dev.citali.needle.ui.theme.WahariTokens
import dev.citali.needle.ui.theme.WahariTypography
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val TASKPILOT_PACKAGE = "dev.citali.taskpilot"

/** Inline plan card in option-card dress, shown under the user's command in Automate mode. */
@Composable
fun PlanCard(
    plan: Plan,
    accessibilityOn: Boolean,
    onOpenAccessibility: () -> Unit,
    onApproveAndRun: () -> Unit,
    onDiscard: () -> Unit,
    onHandOff: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(WahariTokens.bgCard)
            .border(1.dp, WahariTokens.borderSubtle, RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Plan · ${plan.steps.size} steps", style = WahariTypography.optionTitle)
        Column(
            modifier = Modifier.heightIn(max = WahariLayout.stagesMax).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            plan.steps.forEach { step ->
            Text(
                "• ${step.title}" + if (step.highRisk) "   [high-risk]" else "",
                style = WahariTypography.optionDescription.copy(color = WahariTokens.textSecondary),
            )
                Text(
                    "   ${step.detail}",
                    style = WahariTypography.optionDescription,
                )
            }
            if (plan.subGoals.isNotEmpty()) {
                plan.subGoals.forEachIndexed { index, goal ->
                    Text("${index + 1}. $goal", style = WahariTypography.optionDescription)
                }
            }
        }
        VerticalGap(8)
        ActionRow {
            SecondaryButton(text = "Discard", onClick = onDiscard)
            PrimaryButton(
                text = "Approve and run",
                enabled = accessibilityOn && !AgentEngine.isActive(),
                onClick = onApproveAndRun,
            )
        }
        if (!accessibilityOn) {
            SecondaryButton(text = "Open accessibility settings", onClick = onOpenAccessibility)
            Text(
                "Turn on the Wahari accessibility service first.",
                style = WahariTypography.optionDescription.copy(color = WahariTokens.danger),
            )
        }
        onHandOff?.let { handOff ->
            WahariTextButton(text = "Hand off to the TaskPilot app", onClick = handOff)
        }
    }
}

/** Inline run card bound to AgentEngine.state. */
@Composable
fun RunCard(
    state: AgentEngine.EngineState,
    onStop: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(WahariTokens.bgCard)
            .border(1.dp, WahariTokens.borderSubtle, RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Run · ${state.phase.name.lowercase()}", style = WahariTypography.optionTitle)
        if (state.statusText.isNotBlank()) {
            Text(state.statusText, style = WahariTypography.optionDescription)
        }
        state.question?.let { question ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(WahariTokens.bgSurface)
                    .border(1.dp, WahariTokens.borderSubtle, RoundedCornerShape(16.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    if (question.highRisk) "Confirm a high-risk step" else "Wahari needs an answer",
                    style = WahariTypography.optionTitle.copy(fontSize = 14.spFix()),
                )
                Text(question.text, style = WahariTypography.optionDescription.copy(color = WahariTokens.textSecondary))
                ActionRow {
                    SecondaryButton(text = "Decline", onClick = { AgentEngine.answer(false) })
                    PrimaryButton(text = "Approve", onClick = { AgentEngine.answer(true) })
                }
            }
        }
        VerticalGap(4)
        ActionRow {
            if (AgentEngine.isActive()) {
                SecondaryButton(text = "Stop", onClick = onStop)
                if (state.phase == AgentEngine.Phase.PAUSED) {
                    SecondaryButton(text = "Resume", onClick = onResume)
                } else {
                    SecondaryButton(text = "Pause", onClick = onPause)
                }
            } else {
                SecondaryButton(text = "Clear", onClick = onClear)
            }
        }
        state.log.takeLast(24).forEach { entry ->
            Text(
                "${timeLabel(entry.timeMillis)}  ${entry.message}",
                style = WahariTypography.monoBlock.copy(
                    color = when (entry.level) {
                        AgentEngine.LogLevel.ERROR -> WahariTokens.danger
                        AgentEngine.LogLevel.WARN -> WahariTokens.textMuted
                        AgentEngine.LogLevel.ACTION -> WahariTokens.accent
                        else -> WahariTokens.textMuted
                    },
                ),
            )
        }
    }
}

private fun Int.spFix() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)

fun isTaskPilotInstalled(context: Context): Boolean =
    runCatching { context.packageManager.getLaunchIntentForPackage(TASKPILOT_PACKAGE) != null }.getOrDefault(false)

fun copyToClipboard(context: Context, text: String) {
    runCatching {
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("Wahari", text))
    }
}

fun handOffToTaskPilot(context: Context, command: String) {
    copyToClipboard(context, command)
    context.packageManager.getLaunchIntentForPackage(TASKPILOT_PACKAGE)?.let { launch ->
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(launch) }
    }
}

/** Unified check: the OS setting counts even while the service is still binding. */
fun accessibilityOn(context: Context): Boolean =
    dev.citali.needle.tools.AccessibilityStatus.current(context) !=
        dev.citali.needle.tools.AccessibilityStatus.Status.DISABLED

fun openAccessibilitySettings(context: Context) {
    DevicePermissions.openAccessibilitySettings(context)
}

private fun timeLabel(millis: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(millis))

@Preview(name = "Plan card", widthDp = 412, heightDp = 700)
@Composable
private fun PreviewPlanCard() {
    dev.citali.needle.ui.theme.NeedleTheme {
        Column(
            Modifier
                .fillMaxWidth()
                .background(WahariTokens.bgMain)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlanCard(
                plan = Plan(
                    command = "Open Settings and turn on battery saver",
                    steps = listOf(
                        dev.citali.needle.pilot.agent.PlanStep("Open Settings", "Launch Android Settings."),
                        dev.citali.needle.pilot.agent.PlanStep("Find Battery Saver", "Locate the Battery Saver setting."),
                        dev.citali.needle.pilot.agent.PlanStep("Enable Battery Saver", "Change the setting after the approved plan."),
                    ),
                    intent = dev.citali.needle.pilot.agent.TaskIntent.Generic("battery-saver"),
                ),
                accessibilityOn = false,
                onOpenAccessibility = {},
                onApproveAndRun = {},
                onDiscard = {},
                onHandOff = {},
            )
        }
    }
}

@Preview(name = "Run card", widthDp = 412, heightDp = 700)
@Composable
private fun PreviewRunCard() {
    dev.citali.needle.ui.theme.NeedleTheme {
        Column(
            Modifier
                .fillMaxWidth()
                .background(WahariTokens.bgMain)
                .padding(18.dp),
        ) {
            RunCard(
                state = AgentEngine.EngineState(
                    phase = AgentEngine.Phase.RUNNING,
                    command = "Open Settings and turn on battery saver",
                    statusText = "Step 2 of 3 · Find Battery Saver",
                    log = listOf(
                        AgentEngine.LogEntry(0L, AgentEngine.LogLevel.ACTION, "Opened Settings"),
                    ),
                ),
                onStop = {},
                onPause = {},
                onResume = {},
                onClear = {},
            )
        }
    }
}
