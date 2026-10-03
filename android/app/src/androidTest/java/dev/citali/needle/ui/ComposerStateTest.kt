package dev.citali.needle.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.citali.needle.ui.theme.NeedleTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Composer states in isolation. Needs an emulator or device;
 * compile-checked in CI.
 */
@RunWith(AndroidJUnit4::class)
class ComposerStateTest {

    @get:Rule
    val rule = createComposeRule()

    private var sent = 0
    private var stopped = 0
    private var voiceTapped = 0

    private fun setContent(
        initial: String = "",
        busy: Boolean = false,
    ) {
        sent = 0
        stopped = 0
        voiceTapped = 0
        var field by mutableStateOf(TextFieldValue(initial))
        rule.setContent {
            NeedleTheme {
                Composer(
                    field = field,
                    onFieldChange = { field = coerceComposerLength(it) },
                    busy = busy,
                    onSend = { sent++ },
                    onStop = { stopped++ },
                    onMic = {},
                    onGrid = {},
                    onEmptyPrimary = { voiceTapped++ },
                    focusRequester = FocusRequester(),
                    onFocusedChange = {},
                    windowWidth = 412.dp,
                    capsuleWidth = 384.dp,
                    maxLines = 6,
                )
            }
        }
    }

    @Test
    fun emptyShowsVoiceMode() {
        setContent()
        rule.onNodeWithContentDescription("Voice mode", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("composer_primary", useUnmergedTree = true).performClick()
        rule.waitForIdle()
        assertEquals(1, voiceTapped)
        assertEquals(0, sent)
    }

    @Test
    fun textShowsSend() {
        setContent()
        rule.onNodeWithTag("composer_field", useUnmergedTree = true).performTextInput("hello")
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Send", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("composer_primary", useUnmergedTree = true).performClick()
        rule.waitForIdle()
        assertEquals(1, sent)
    }

    @Test
    fun busyShowsStop() {
        setContent(initial = "hello", busy = true)
        rule.onNodeWithContentDescription("Stop", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag("composer_primary", useUnmergedTree = true).performClick()
        rule.waitForIdle()
        assertEquals(1, stopped)
        assertEquals(0, sent)
    }

    @Test
    fun longTextKeepsContentAcrossModes() {
        setContent()
        val long = "word ".repeat(80)
        rule.onNodeWithTag("composer_field", useUnmergedTree = true).performTextInput(long)
        rule.waitForIdle()
        rule.onNodeWithTag("composer_field", useUnmergedTree = true)
            .assertTextEquals(long)
    }

    @Test
    fun placeholderVisibleWhenEmpty() {
        setContent()
        rule.onNodeWithText("Ask Wahari").assertIsDisplayed()
    }

    @Test
    fun micAndGridHaveLabels() {
        setContent()
        rule.onNodeWithContentDescription("Voice input", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithContentDescription("Agent Mode", useUnmergedTree = true).assertIsDisplayed()
    }
}
