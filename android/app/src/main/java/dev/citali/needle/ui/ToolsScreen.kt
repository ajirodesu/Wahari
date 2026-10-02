package dev.citali.needle.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import dev.citali.needle.engine.NeedlePrefs
import dev.citali.needle.tools.ActivityBridges
import dev.citali.needle.tools.DevicePermissions
import dev.citali.needle.tools.PhoneTools
import dev.citali.needle.ui.theme.NeedleTheme
import dev.citali.needle.ui.theme.WahariTypography
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun ToolsContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val packs = remember { NeedlePrefs.toolPacks(context) }
    val output = remember { mutableStateListOf<String>() }

    var permissions by remember { mutableStateOf(DevicePermissions.missing(context)) }
    var sayText by remember { mutableStateOf("Hello from Wahari") }
    var shareText by remember { mutableStateOf("Sent from Wahari") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissions = DevicePermissions.missing(context)
    }

    PageColumn {
        Section(
            title = "Permissions",
            subtitle = if (permissions.isEmpty()) {
                "Everything the enabled tools need is granted."
            } else {
                "Missing: " + permissions.joinToString(", ") { DevicePermissions.label(it) }
            },
        ) {
            ActionRow {
                PrimaryButton(
                    text = if (permissions.isEmpty()) "Re-check" else "Grant missing",
                    onClick = {
                        if (permissions.isEmpty()) {
                            permissions = DevicePermissions.missing(context)
                        } else {
                            permissionLauncher.launch(permissions.toTypedArray())
                        }
                    },
                )
                SecondaryButton(text = "App settings", onClick = { DevicePermissions.openAppSettings(context) })
            }
            ActionRow {
                SecondaryButton(
                    text = if (DevicePermissions.canWriteSettings(context)) "System settings granted" else "Allow brightness control",
                    enabled = !DevicePermissions.canWriteSettings(context),
                    onClick = { DevicePermissions.openWriteSettings(context) },
                )
            }
        }

        Section(title = "Quick actions", subtitle = "Run a tool on its own, without asking the model.") {
            ActionRow {
                SecondaryButton(text = "Battery", onClick = { scope.quick(context, "get_battery_status", output) })
                SecondaryButton(text = "Flash on", onClick = { scope.quick(context, "set_flashlight", output, JSONObject().put("on", true)) })
                SecondaryButton(text = "Flash off", onClick = { scope.quick(context, "set_flashlight", output, JSONObject().put("on", false)) })
            }
            ActionRow {
                SecondaryButton(text = "Vibrate", onClick = { scope.quick(context, "vibrate", output, JSONObject().put("milliseconds", 400)) })
                SecondaryButton(text = "Location", onClick = { scope.quick(context, "get_location", output) })
                SecondaryButton(text = "Wi-Fi", onClick = { scope.quick(context, "get_wifi_info", output) })
            }
            ActionRow {
                SecondaryButton(
                    text = "Screenshot",
                    onClick = { scope.quick(context, "take_screenshot", output) },
                )
                SecondaryButton(
                    text = "Contacts",
                    onClick = { scope.quick(context, "list_contacts", output, JSONObject().put("limit", 5)) },
                )
                SecondaryButton(
                    text = "Call log",
                    onClick = { scope.quick(context, "get_call_log", output, JSONObject().put("limit", 5)) },
                )
            }

            WahariTextField(
                value = sayText,
                onValueChange = { sayText = it },
                label = "Text to speak",
            )
            ActionRow {
                SecondaryButton(
                    text = "Speak",
                    onClick = { scope.quick(context, "speak", output, JSONObject().put("text", sayText)) },
                )
                SecondaryButton(
                    text = "Copy",
                    onClick = { scope.quick(context, "set_clipboard", output, JSONObject().put("text", sayText)) },
                )
                SecondaryButton(text = "Read clipboard", onClick = { scope.quick(context, "get_clipboard", output) })
            }

            WahariTextField(
                value = shareText,
                onValueChange = { shareText = it },
                label = "Text to share",
            )
            SecondaryButton(
                text = "Open share sheet",
                onClick = { scope.quick(context, "share_text", output, JSONObject().put("text", shareText)) },
            )
        }

        Section(
            title = "Camera & identity",
            subtitle = "These two need the app on screen; they use the phone's own capture and unlock prompts.",
        ) {
            ActionRow {
                SecondaryButton(
                    text = "Take a photo",
                    enabled = ActivityBridges.hasCamera,
                    onClick = {
                        scope.launch {
                            val result = ActivityBridges.photoCapture?.invoke() ?: "The app is not in the foreground."
                            output.add(0, result)
                        }
                    },
                )
                SecondaryButton(
                    text = "Fingerprint check",
                    enabled = ActivityBridges.hasBiometric,
                    onClick = {
                        scope.launch {
                            val result = ActivityBridges.biometricCheck?.invoke("Confirm it is you") ?: "The app is not in the foreground."
                            output.add(0, result)
                        }
                    },
                )
            }
        }

        Section(title = "Output", subtitle = "The newest result is at the top.") {
            if (output.isEmpty()) {
                Text("Nothing yet.", style = WahariTypography.sectionSubtitle)
            }
            output.take(8).forEach { line ->
                MonoBlock(line)
            }
            if (output.isNotEmpty()) {
                SecondaryButton(text = "Clear", onClick = { output.clear() })
            }
        }

        Section(
            title = "Tools the model can call",
            subtitle = "${PhoneTools.tools(context, packs).size} declared with the tool packs you enabled.",
        ) {
            PhoneTools.describe(context, packs).forEach { line ->
                Text(line, style = WahariTypography.sectionSubtitle)
            }
            Text(
                "Change packs in Settings if the engine reports that the tool list does not fit.",
                style = WahariTypography.sectionSubtitle,
            )
        }
        VerticalGap()
    }
}

private fun CoroutineScope.quick(
    context: Context,
    name: String,
    output: MutableList<String>,
    args: JSONObject = JSONObject(),
) {
    launch {
        val result = PhoneTools.run(context, NeedlePrefs.toolPacks(context), name, args)
        output.add(0, "$name → $result")
    }
}

@Preview(name = "Tools page", widthDp = 412, heightDp = 890)
@Composable
private fun PreviewTools() {
    NeedleTheme {
        PageShell(title = "Tools", onBack = {}) { ToolsContent() }
    }
}
