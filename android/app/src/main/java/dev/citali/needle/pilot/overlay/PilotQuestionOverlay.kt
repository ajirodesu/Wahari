package dev.citali.needle.pilot.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.citali.needle.pilot.agent.AgentEngine
import dev.citali.needle.ui.PrimaryButton
import dev.citali.needle.ui.SecondaryButton
import dev.citali.needle.ui.WahariTextField
import dev.citali.needle.ui.theme.NeedleTheme
import dev.citali.needle.ui.theme.WahariIcons
import dev.citali.needle.ui.theme.WahariLayout
import dev.citali.needle.ui.theme.WahariTokens
import dev.citali.needle.ui.theme.WahariTypography

/**
 * Floating question prompt shown while a task is waiting on the user.
 *
 * The in-app AlertDialog could only be seen from inside TaskPilot. Because the
 * agent drives *other* apps, the question was raised while a different app was
 * in the foreground, so it was invisible until the user happened to switch back
 * -- and to the user the task simply looked stalled and then abandoned.
 *
 * This renders the same question as a TYPE_ACCESSIBILITY_OVERLAY window, so it
 * appears above whatever app is on screen. It owns its own lifecycle for the
 * same reason [PilotOverlay] does: a ComposeView outside an Activity has no
 * view-tree owners and will throw during its first traversal without them.
 */
class PilotQuestionOverlay(private val service: AccessibilityService) :
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val windowManager: WindowManager =
        service.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private var view: ComposeView? = null

    init {
        savedStateController.performRestore(null)
    }

    fun show(question: AgentEngine.Question) {
        hide()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        val composeView = ComposeView(service).apply {
            setViewTreeLifecycleOwner(this@PilotQuestionOverlay)
            setViewTreeViewModelStoreOwner(this@PilotQuestionOverlay)
            setViewTreeSavedStateRegistryOwner(this@PilotQuestionOverlay)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                NeedleTheme {
                    QuestionContent(
                        question = question,
                        onApprove = { text -> AgentEngine.answer(true, text) },
                        onDecline = { AgentEngine.answer(false) },
                    )
                }
            }
        }

        // Focusable: the user may need to type an answer, which requires the IME.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            dimAmount = 0.55f
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        runCatching { windowManager.addView(composeView, params) }
            .onSuccess {
                view = composeView
                lifecycleRegistry.currentState = Lifecycle.State.RESUMED
            }
            .onFailure {
                // If the window cannot be shown the in-app dialog still exists.
                lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
            }
    }

    fun hide() {
        val current = view
        view = null
        if (current != null) {
            runCatching { lifecycleRegistry.currentState = Lifecycle.State.CREATED }
            runCatching { windowManager.removeView(current) }
        }
        runCatching { lifecycleRegistry.currentState = Lifecycle.State.DESTROYED }
        runCatching { store.clear() }
    }
}

@Composable
private fun QuestionContent(
    question: AgentEngine.Question,
    onApprove: (String?) -> Unit,
    onDecline: () -> Unit,
) {
    var answerText by remember(question) { mutableStateOf("") }
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val bodyMax = maxHeight * 0.85f
        Box(
            modifier = Modifier
                .padding(20.dp)
                .widthIn(max = WahariLayout.overlayMax)
                .heightIn(max = bodyMax)
                .clip(RoundedCornerShape(28.dp))
                .background(WahariTokens.bgSheet)
                .border(1.dp, WahariTokens.borderEdge, RoundedCornerShape(28.dp))
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (question.highRisk) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(WahariTokens.bgIcon),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            WahariIcons.shieldAlert,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Text(
                    text = if (question.highRisk) "Confirmation required" else "Wahari needs an answer",
                    style = WahariTypography.sheetTitle.copy(fontSize = 20.spFix()),
                )
            }
            Text(
                text = question.text,
                style = WahariTypography.assistantBody.copy(
                    fontSize = 15.spFix(),
                    color = WahariTokens.textSecondary,
                ),
            )
            if (!question.highRisk) {
                WahariTextField(
                    value = answerText,
                    onValueChange = { answerText = it },
                    label = "Your answer",
                    minLines = 2,
                )
            }
            @OptIn(ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SecondaryButton(onClick = onDecline, text = if (question.highRisk) "Decline" else "Cancel task")
                PrimaryButton(
                    onClick = { onApprove(answerText.takeIf { it.isNotBlank() }) },
                    text = if (question.highRisk) "Approve" else "Continue",
                )
            }
        }
        }
    }
}

private fun Int.spFix() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)

@Preview(name = "Question overlay", widthDp = 412, heightDp = 400)
@Composable
private fun PreviewQuestion() {
    NeedleTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(WahariTokens.bgMain)
                .padding(vertical = 24.dp),
        ) {
            QuestionContent(
                question = AgentEngine.Question("Send the message to Ada?", highRisk = true),
                onApprove = {},
                onDecline = {},
            )
        }
    }
}
