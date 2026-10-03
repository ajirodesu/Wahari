package dev.citali.needle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drawer gestures on the scaffold in isolation (no engine, no activity).
 * Needs an emulator or device; compile-checked in CI.
 *
 * Opening swipes start inside the content (not at the screen edge) because
 * the edge band belongs to the system back gesture. Scrim taps land clear
 * of the drawer regardless of screen density.
 */
@RunWith(AndroidJUnit4::class)
class DrawerGestureTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setContent(initialOpen: Boolean = false) {
        var open by mutableStateOf(initialOpen)
        rule.setContent {
            SwipeableSidebarScaffold(
                open = open,
                onOpenChange = { open = it },
                drawerWidth = 320.dp,
                gesturesEnabled = true,
                drawerContent = {
                    Box(Modifier.fillMaxSize().background(Color.DarkGray)) {
                        Text("drawer-body")
                    }
                },
                content = {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .testTag("chat_list")
                            .background(Color.Black),
                    ) {
                        Text("chat-body")
                    }
                },
            )
        }
    }

    /** Swipe right starting well inside the content. */
    private fun openSwipe() {
        rule.onNodeWithTag("chat_list").performTouchInput {
            swipe(Offset(width * 0.2f, centerY), Offset(width * 0.8f, centerY), 300)
        }
        rule.waitForIdle()
    }

    /** Tap the scrim clear of the drawer on any density. */
    private fun scrimTap() {
        rule.onNodeWithTag("drawer_scrim").performTouchInput {
            click(Offset(width * 0.9f, centerY))
        }
        rule.waitForIdle()
    }

    @Test
    fun swipeRightOnContentOpensDrawer() {
        setContent()
        openSwipe()
        rule.onNodeWithTag("nav_drawer").assertIsDisplayed()
    }

    @Test
    fun scrimTapClosesDrawer() {
        setContent(initialOpen = true)
        rule.onNodeWithTag("nav_drawer").assertIsDisplayed()
        scrimTap()
        rule.onNodeWithTag("nav_drawer").assertIsNotDisplayed()
    }

    @Test
    fun swipeLeftOnDrawerClosesIt() {
        setContent(initialOpen = true)
        rule.onNodeWithTag("nav_drawer").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        rule.onNodeWithTag("nav_drawer").assertIsNotDisplayed()
    }

    @Test
    fun rapidToggleEndsClosed() {
        setContent()
        openSwipe()
        scrimTap()
        openSwipe()
        scrimTap()
        rule.onNodeWithTag("nav_drawer").assertIsNotDisplayed()
    }

    @Test
    fun chatTappableAfterClose() {
        setContent()
        openSwipe()
        scrimTap()
        rule.onNodeWithText("chat-body").assertIsDisplayed()
        openSwipe()
        rule.onNodeWithTag("nav_drawer").assertIsDisplayed()
    }

    @Test
    fun drawerBodyVisibleWhenOpen() {
        setContent()
        openSwipe()
        rule.onNodeWithText("drawer-body").assertIsDisplayed()
    }
}
