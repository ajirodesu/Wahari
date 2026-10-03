package dev.citali.needle.tools

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dev.citali.needle.engine.NeedlePrefs

/**
 * The permission plan shared by the setup Permissions page and the Tools
 * screen: capability groups, API-level and hardware filtering, denial
 * states, batch ordering and "what is still missing".
 *
 * Everything decision-shaped here is pure (sdk/hardware/grants passed in)
 * and covered by unit tests. The [snapshot] / [needsPermissionsPage] /
 * [nextBatch] helpers bind it to a Context. [DevicePermissions] stays the
 * shared permission catalog; capabilities reference it, never duplicate it.
 */
object PermissionPlan {

    enum class Group { VOICE_NOTIFY, MESSAGES_CALLS, DEVICE, SPECIAL }

    enum class SpecialKind { WRITE_SETTINGS, BATTERY, AUTO_REVOKE }

    data class Capability(
        val id: String,
        val title: String,
        val group: Group,
        /** False for capabilities shown only in Tools, never in setup. */
        val inSetup: Boolean = true,
        val minSdk: Int = 0,
        val needsTelephony: Boolean = false,
        val needsCamera: Boolean = false,
        val special: SpecialKind? = null,
    )

    enum class PermStatus { GRANTED, DENIED, PERMANENTLY_DENIED }

    enum class CapStatus { GRANTED, DENIED, PERMANENTLY_DENIED, APPROXIMATE }

    data class CapState(
        val capability: Capability,
        val status: CapStatus,
        /** Human reason string(s) reused from the permission catalog. */
        val unlocks: String,
    )

    val capabilities: List<Capability> = listOf(
        Capability("mic", "Microphone", Group.VOICE_NOTIFY),
        Capability("notifications", "Notifications", Group.VOICE_NOTIFY, minSdk = Build.VERSION_CODES.TIRAMISU),
        Capability("sms", "SMS", Group.MESSAGES_CALLS, needsTelephony = true),
        Capability("calls", "Phone calls", Group.MESSAGES_CALLS, needsTelephony = true),
        Capability("call_log", "Call log", Group.MESSAGES_CALLS, needsTelephony = true),
        Capability("phone_state", "Phone state", Group.MESSAGES_CALLS, needsTelephony = true),
        Capability("contacts", "Contacts", Group.MESSAGES_CALLS),
        Capability("camera", "Camera", Group.DEVICE, needsCamera = true),
        Capability("location", "Location", Group.DEVICE),
        Capability("media_files", "Stored media", Group.DEVICE, inSetup = false),
        Capability("brightness", "System settings", Group.SPECIAL, special = SpecialKind.WRITE_SETTINGS),
        Capability("battery", "Background reliability", Group.SPECIAL, special = SpecialKind.BATTERY),
        Capability("auto_revoke", "Permission auto-reset", Group.SPECIAL, special = SpecialKind.AUTO_REVOKE),
    )

    /** Runtime permissions of a capability on this API level. */
    fun runtimePermissions(capability: Capability, sdk: Int): List<String> =
        DevicePermissions.all
            .filter {
                it.capability == capability.id &&
                    sdk >= it.minSdk && sdk <= it.maxSdk && sdk >= capability.minSdk
            }
            .map { it.permission }
            .distinct()

    fun applicable(capability: Capability, sdk: Int, hasTelephony: Boolean, hasCamera: Boolean): Boolean {
        if (sdk < capability.minSdk) return false
        if (capability.needsTelephony && !hasTelephony) return false
        if (capability.needsCamera && !hasCamera) return false
        if (capability.special != null) return true
        return runtimePermissions(capability, sdk).isNotEmpty()
    }

    /**
     * Per-permission state. Permanent denial cannot be read directly:
     * the rationale flag is false both before the first request and after
     * "Don't ask again", so only requested-before + no-rationale means blocked.
     * On Android 11+ two denials also silence the dialog — same bucket.
     */
    fun permStatus(granted: Boolean, canAskAgain: Boolean, requestedBefore: Boolean): PermStatus =
        when {
            granted -> PermStatus.GRANTED
            requestedBefore && !canAskAgain -> PermStatus.PERMANENTLY_DENIED
            else -> PermStatus.DENIED
        }

    fun capabilityStatus(
        capability: Capability,
        states: Map<String, PermStatus>,
        fineGranted: Boolean,
        coarseGranted: Boolean,
    ): CapStatus {
        if (capability.id == "location" && !fineGranted && coarseGranted) {
            return CapStatus.APPROXIMATE
        }
        val perms = states.values.toSet()
        return when {
            perms.isNotEmpty() && perms.all { it == PermStatus.GRANTED } -> CapStatus.GRANTED
            perms.any { it == PermStatus.PERMANENTLY_DENIED } -> CapStatus.PERMANENTLY_DENIED
            else -> CapStatus.DENIED
        }
    }

    fun satisfied(status: CapStatus): Boolean =
        status == CapStatus.GRANTED || status == CapStatus.APPROXIMATE

    /** True when every runtime capability is granted (special rows excluded). */
    fun runtimeComplete(states: List<CapState>): Boolean =
        states.filter { it.capability.special == null }.all { satisfied(it.status) }

    /**
     * Runtime batches in request order, skipping granted permissions and
     * inapplicable capabilities. Location's permissions always travel
     * together (required on Android 12+).
     */
    fun requestBatches(
        caps: List<Capability> = capabilities,
        sdk: Int,
        hasTelephony: Boolean,
        hasCamera: Boolean,
        granted: (String) -> Boolean,
    ): List<List<String>> = caps
        .filter { it.special == null && applicable(it, sdk, hasTelephony, hasCamera) }
        .mapNotNull { cap ->
            runtimePermissions(cap, sdk).filterNot(granted).takeIf { it.isNotEmpty() }
        }

    // ---- Context-bound helpers -------------------------------------------

    fun hardwareFlags(context: Context): Pair<Boolean, Boolean> {
        val pm = context.packageManager
        return Pair(
            runCatching { pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) }.getOrDefault(false),
            runCatching { pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }.getOrDefault(false),
        )
    }

    fun unlocksFor(capability: Capability): String {
        val catalog = DevicePermissions.all
            .filter { it.capability == capability.id }
            .map { it.why }
            .distinct()
        if (catalog.isNotEmpty()) return catalog.joinToString("; ")
        return when (capability.special) {
            SpecialKind.WRITE_SETTINGS -> "Change screen brightness from the chat"
            SpecialKind.BATTERY -> "Keep remote control listening while the phone is idle"
            SpecialKind.AUTO_REVOKE -> "Stop Android from silently removing Wahari's permissions"
            null -> ""
        }
    }

    /** Live snapshot: never cached, always read from the OS. */
    fun snapshot(context: Context): List<CapState> {
        val sdk = Build.VERSION.SDK_INT
        val (telephony, camera) = hardwareFlags(context)
        val requested = NeedlePrefs.requestedPermissions(context)
        val fine = DevicePermissions.granted(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = DevicePermissions.granted(context, android.Manifest.permission.ACCESS_COARSE_LOCATION)
        return capabilities
            .filter { applicable(it, sdk, telephony, camera) }
            .map { cap ->
                val status = when (cap.special) {
                    SpecialKind.WRITE_SETTINGS ->
                        if (DevicePermissions.canWriteSettings(context)) CapStatus.GRANTED else CapStatus.DENIED
                    SpecialKind.BATTERY ->
                        if (DevicePermissions.isIgnoringBatteryOptimizations(context)) CapStatus.GRANTED
                        else CapStatus.DENIED
                    SpecialKind.AUTO_REVOKE -> CapStatus.DENIED
                    null -> {
                        val states = runtimePermissions(cap, sdk).associateWith { perm ->
                            permStatus(
                                granted = DevicePermissions.granted(context, perm),
                                canAskAgain = DevicePermissions.canAskAgain(context, perm),
                                requestedBefore = perm in requested,
                            )
                        }
                        capabilityStatus(cap, states, fine, coarse)
                    }
                }
                CapState(cap, status, unlocksFor(cap))
            }
    }

    /** Setup shows the Permissions page while runtime capabilities are missing. */
    fun needsPermissionsPage(context: Context): Boolean =
        snapshot(context).any { it.capability.inSetup && it.capability.special == null && !satisfied(it.status) }

    /** First non-empty runtime batch, or empty when nothing is missing. */
    fun nextBatch(context: Context, setupOnly: Boolean = false): List<String> {
        val sdk = Build.VERSION.SDK_INT
        val (telephony, camera) = hardwareFlags(context)
        val caps = if (setupOnly) capabilities.filter { it.inSetup } else capabilities
        return requestBatches(
            caps = caps,
            sdk = sdk,
            hasTelephony = telephony,
            hasCamera = camera,
            granted = { DevicePermissions.granted(context, it) },
        ).firstOrNull().orEmpty()
    }
}
