package dev.citali.needle.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Bottom-sheet gestures in isolation. Needs an emulator or device;
 * compile-checked in CI.
 */
@RunWith(AndroidJUnit4::class)
class SheetGestureTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun setContent(initial: SheetValue = SheetValue.Partial) {
        var value by mutableStateOf(initial)
        rule.setContent {
            DraggableBottomSheet(
                value = value,
                onValueChange = { value = it },
                partialHeight = 300.dp,
                expandedHeight = 700.dp,
                header = { Text("sheet-title") },
                content = {
                    Column(Modifier.fillMaxWidth().height(600.dp)) {
                        Text("sheet-item")
                    }
                },
            )
        }
    }

    @Test
    fun headerDragUpExpands() {
        setContent(SheetValue.Partial)
        rule.onNodeWithTag("sheet_header", useUnmergedTree = true)
            .performTouchInput { swipeUp() }
        rule.waitForIdle()
        rule.onNodeWithText("sheet-item").assertIsDisplayed()
        // Expanded shows the tall content region; collapsing returns.
        rule.onNodeWithTag("sheet_header", useUnmergedTree = true)
            .performTouchInput { swipeDown() }
        rule.waitForIdle()
        rule.onNodeWithTag("sheet").assertIsDisplayed()
    }

    @Test
    fun headerDragDownFromHalfCloses() {
        setContent(SheetValue.Partial)
        // Long fast drag down from the header: travels well past the
        // halfway point, so the sheet closes instead of snapping back.
        rule.onNodeWithTag("sheet_header", useUnmergedTree = true)
            .performTouchInput { swipe(topCenter, bottomCenter.copy(y = bottomCenter.y + 1200f), 250) }
        rule.waitForIdle()
        rule.onNodeWithTag("sheet", useUnmergedTree = true).assertIsNotDisplayed()
    }

    @Test
    fun scrimTapCloses() {
        setContent(SheetValue.Partial)
        rule.onNodeWithTag("sheet_scrim", useUnmergedTree = true).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("sheet", useUnmergedTree = true).assertIsNotDisplayed()
    }

    @Test
    fun backClosesFromExpanded() {
        setContent(SheetValue.Expanded)
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithTag("sheet", useUnmergedTree = true).assertIsNotDisplayed()
    }

    @Test
    fun contentDragsSheet() {
        setContent(SheetValue.Partial)
        rule.onNodeWithTag("sheet_content", useUnmergedTree = true)
            .performTouchInput { swipeUp() }
        rule.waitForIdle()
        rule.onNodeWithTag("sheet").assertIsDisplayed()
    }
}
