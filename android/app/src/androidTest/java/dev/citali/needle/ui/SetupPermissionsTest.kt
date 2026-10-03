package dev.citali.needle.ui

import android.Manifest
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import dev.citali.needle.engine.NeedlePrefs
import dev.citali.needle.ui.theme.NeedleTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Fresh-install Permissions step. Needs an emulator or device;
 * compile-checked in CI. Prefs are cleared per test through the same file
 * the app uses, so each test starts from a genuine fresh install.
 */
@RunWith(AndroidJUnit4::class)
class SetupPermissionsTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun freshInstall() {
        app.getSharedPreferences("needle_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
        NeedlePrefs.setIntroCompleted(app, true)
        NeedlePrefs.setSetupComplete(app, false)
    }

    private fun showFlow() {
        rule.setContent {
            NeedleTheme {
                SetupFlow(onFinished = {})
            }
        }
    }

    @Test
    fun permissionsPageShowsFirstAfterIntro() {
        showFlow()
        rule.onNodeWithText("Required permissions").assertIsDisplayed()
        rule.onNodeWithText("Allow all").assertIsDisplayed()
    }

    @Test
    fun continueDisabledUntilFirstRun() {
        showFlow()
        rule.onNodeWithText("Continue").assertIsNotEnabled()
    }

    @Test
    fun skipGoesToNeedle() {
        showFlow()
        rule.onNodeWithText("Skip").performScrollTo()
        rule.onNodeWithText("Skip").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("On-device model").assertIsDisplayed()
    }

    @Test
    fun visitedEnablesContinue() {
        NeedlePrefs.markPermissionsRequested(app, listOf(Manifest.permission.RECORD_AUDIO))
        showFlow()
        rule.onNodeWithText("Continue").assertIsEnabled()
    }

    @Test
    fun permanentlyDeniedRowOpensSettings() {
        // Requested before, never granted, test activity never asked:
        // rationale is false, so the row must offer app settings.
        NeedlePrefs.markPermissionsRequested(
            app,
            listOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS),
        )
        showFlow()
        rule.onNodeWithText("Open app settings").performScrollTo()
        rule.onNodeWithText("Open app settings").assertIsDisplayed()
        rule.onNodeWithText(
            "Android will not show the request again. Open app settings to allow it.",
        ).assertIsDisplayed()
    }

    @Test
    fun prefsRoundTripDiagnostic() {
        NeedlePrefs.markPermissionsRequested(app, listOf(Manifest.permission.RECORD_AUDIO))
        assertTrue(NeedlePrefs.permissionsEverRequested(app))
        assertTrue(
            NeedlePrefs.requestedPermissions(app).contains(Manifest.permission.RECORD_AUDIO),
        )
    }

    @Test
    fun setupOrderIsPermissionsThenNeedle() {
        // Fresh install on this device: no runtime grants, so Permissions
        // comes first even though the model is also missing.
        assertEquals("Permissions", firstIncompleteStep(app)?.name)
    }

    @Test
    fun recreationStaysOnPermissions() {
        // Process death restores the persisted step; the gate recomputes the
        // same page from the same prefs (history itself is rememberSaveable).
        NeedlePrefs.setSetupStep(app, NeedlePrefs.SETUP_PERMISSIONS)
        assertEquals(NeedlePrefs.SETUP_PERMISSIONS, NeedlePrefs.setupStep(app))
        assertEquals("Permissions", firstIncompleteStep(app)?.name)
    }

    @Test
    fun nextBatchDiagnostic() {
        val first = dev.citali.needle.tools.PermissionPlan.nextBatch(app)
        assertTrue("first=$first", first.isNotEmpty())
    }

    @Test
    fun allowAllSequencesBatchesThenStops() {
        val requested = mutableListOf<List<String>>()
        var boardRef: PermissionBoardState? = null
        rule.setContent {
            NeedleTheme {
                // Window insets like production: a bare board starts under the
                // status bar, where taps land outside the touchable region.
                Column(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(16.dp),
                ) {
                    val board = rememberPermissionBoardState(
                        fakeRequest = { batch, done ->
                            requested.add(batch)
                            // Fake grants change nothing on the device: the chain
                            // must still terminate instead of looping forever.
                            done(batch.associateWith { true })
                        },
                    )
                    boardRef = board
                    PermissionBoard(state = board)
                }
            }
        }
        // Drive the chain directly: proves sequencing independent of tap injection.
        rule.runOnIdle { boardRef?.startAllowAll() }
        rule.waitForIdle()
        assertTrue("batches=$requested", requested.isNotEmpty())
        assertTrue(
            "batches=$requested",
            requested.flatten().distinct().size == requested.flatten().size,
        )
        assertTrue("batches=$requested", requested.first().contains(Manifest.permission.RECORD_AUDIO))
        // Location travels as one batch.
        assertTrue(
            "batches=$requested",
            requested.any {
                it.contains(Manifest.permission.ACCESS_FINE_LOCATION) &&
                    it.contains(Manifest.permission.ACCESS_COARSE_LOCATION)
            },
        )
        rule.onNodeWithText("Allow all").assertIsDisplayed()
    }

    @Test
    fun allowAllButtonStartsChainOnTap() {
        val requested = mutableListOf<List<String>>()
        var boardRef: PermissionBoardState? = null
        rule.setContent {
            NeedleTheme {
                Column(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(16.dp),
                ) {
                    val board = rememberPermissionBoardState(
                        fakeRequest = { batch, done ->
                            requested.add(batch)
                            done(batch.associateWith { true })
                        },
                    )
                    boardRef = board
                    PermissionBoard(state = board)
                }
            }
        }
        val button = rule.onNodeWithText("Allow all")
        button.assertIsDisplayed()
        button.assertIsEnabled()
        button.performClick()
        rule.waitForIdle()
        assertTrue(
            "running=${boardRef?.allowAllRunning} batches=$requested",
            requested.isNotEmpty(),
        )
    }
}
