package dev.citali.needle.tools

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import dev.citali.needle.pilot.accessibility.NeedleAccessibilityService

/**
 * The permissions the phone tools ask for, and a friendly name for each, so the
 * Tools tab can request exactly what a tool needs and explain what it unlocks.
 */
object DevicePermissions {

    data class Entry(
        val permission: String,
        val label: String,
        val why: String,
        val minSdk: Int = 0,
        val maxSdk: Int = Int.MAX_VALUE,
        /** Stable capability id grouping permissions (see PermissionPlan). */
        val capability: String = "",
    )

    val all: List<Entry> = listOf(
        Entry(Manifest.permission.POST_NOTIFICATIONS, "Notifications", "Post status updates from the assistant", Build.VERSION_CODES.TIRAMISU, capability = "notifications"),
        Entry(Manifest.permission.ACCESS_FINE_LOCATION, "Location", "Read GPS position and Wi-Fi details", Build.VERSION_CODES.M, capability = "location"),
        Entry(Manifest.permission.ACCESS_COARSE_LOCATION, "Approximate location", "Cheaper location fix when GPS is off", Build.VERSION_CODES.M, capability = "location"),
        Entry(Manifest.permission.CAMERA, "Camera", "Take photos on request", Build.VERSION_CODES.M, capability = "camera"),
        Entry(Manifest.permission.RECORD_AUDIO, "Microphone", "Voice input for the chat box", Build.VERSION_CODES.M, capability = "mic"),
        Entry(Manifest.permission.SEND_SMS, "Send SMS", "Send text messages you dictate", Build.VERSION_CODES.M, capability = "sms"),
        Entry(Manifest.permission.READ_SMS, "Read SMS", "Read the recent inbox on request", Build.VERSION_CODES.M, capability = "sms"),
        Entry(Manifest.permission.CALL_PHONE, "Phone calls", "Place calls directly instead of opening the dialer", Build.VERSION_CODES.M, capability = "calls"),
        Entry(Manifest.permission.READ_CALL_LOG, "Call log", "Summarise recent calls", Build.VERSION_CODES.M, capability = "call_log"),
        Entry(Manifest.permission.READ_CONTACTS, "Contacts", "Look up names and numbers", Build.VERSION_CODES.M, capability = "contacts"),
        Entry(Manifest.permission.READ_PHONE_STATE, "Phone state", "Report carrier, SIM and network type", Build.VERSION_CODES.M, capability = "phone_state"),
        Entry(Manifest.permission.READ_MEDIA_AUDIO, "Music files", "Play downloaded songs", Build.VERSION_CODES.TIRAMISU, capability = "media_files"),
        Entry(Manifest.permission.READ_MEDIA_VIDEO, "Video files", "Play downloaded videos", Build.VERSION_CODES.TIRAMISU, capability = "media_files"),
        Entry(
            Manifest.permission.READ_EXTERNAL_STORAGE, "Stored media", "Play downloaded songs and videos",
            Build.VERSION_CODES.M, 32, "media_files",
        ),
    )

    /** Permissions this build can still ask for on this device. */
    fun requestable(): List<String> = all
        .filter { Build.VERSION.SDK_INT in it.minSdk..it.maxSdk }
        .map { it.permission }

    fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * Whether the system would show a rationale (hence a dialog) for this
     * permission right now. False both before the first request and after
     * "Don't ask again", so callers must combine it with a persisted
     * requested-before flag (see NeedlePrefs) to detect permanent denial.
     */
    fun canAskAgain(context: Context, permission: String): Boolean =
        (context as? Activity)?.shouldShowRequestPermissionRationale(permission) == true

    fun missing(context: Context): List<String> = requestable().filterNot { granted(context, it) }

    fun label(permission: String): String =
        all.firstOrNull { it.permission == permission }?.label ?: permission.substringAfterLast('.')

    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun openWriteSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /**
     * The real OS truth: is Wahari's Accessibility Service currently enabled
     * in system settings? Read from Settings.Secure, so it is correct even
     * when the service process binding ([NeedleAccessibilityService.instance])
     * is momentarily null (cold start, reboot, temporary disconnect).
     *
     * This is the *live* state only. Historical setup completion is stored
     * separately in durable prefs and must not be derived from this.
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, NeedleAccessibilityService::class.java).flattenToString()
        val enabled = runCatching {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            )
        }.getOrNull() ?: return NeedleAccessibilityService.isConnected()
        if (enabled.isBlank()) return false
        return enabled.split(':').any { entry ->
            entry == expected ||
                entry.endsWith("/" + NeedleAccessibilityService::class.java.name, ignoreCase = true)
        }
    }

    fun canWriteSettings(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.System.canWrite(context)

    /** True when the system will not kill background work for battery saving. */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        runCatching {
            val power = context.getSystemService(PowerManager::class.java) ?: return false
            power.isIgnoringBatteryOptimizations(context.packageName)
        }.getOrDefault(false)

    /** Asks for the battery exemption, falling back to the settings list. */
    fun openBatterySettings(context: Context) {
        val packageUri = Uri.fromParts("package", context.packageName, null)
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(direct) }.isSuccess) return
        val list = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(list) }.isSuccess) return
        openAppSettings(context)
    }

    /**
     * Opens App info, where the "Remove permissions if app is unused"
     * auto-reset toggle lives (Android 12+ silently revokes rarely used apps
     * otherwise). There is no dedicated system intent for the switch itself,
     * so App info is the right screen; the row copy says where to tap.
     */
    fun openAutoRevokeSettings(context: Context) {
        openAppSettings(context)
    }
}
