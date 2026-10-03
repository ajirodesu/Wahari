package dev.citali.needle.tools

import android.content.Context
import dev.citali.needle.pilot.accessibility.NeedleAccessibilityService
import kotlinx.coroutines.delay

/**
 * Single source of truth for accessibility state, replacing the scattered
 * `isConnected()` / `isAccessibilityServiceEnabled()` checks.
 *
 * The OS setting is the truth about *enabled*; the bound instance is only
 * about *connected right now*. After a cold start, swipe-away or reboot the
 * setting is on while the service is still binding — that window used to read
 * as "disabled everywhere", sending users through setup again.
 */
object AccessibilityStatus {

    enum class Status {
        /** OS setting on and the service is bound: automation is usable. */
        ENABLED_BOUND,

        /** OS setting on but the service is not bound yet (cold start, OEM kill). */
        ENABLED_BINDING,

        /** OS setting off: automation is unavailable. */
        DISABLED,
    }

    fun osEnabled(context: Context): Boolean =
        DevicePermissions.isAccessibilityServiceEnabled(context)

    fun current(context: Context): Status = when {
        NeedleAccessibilityService.isConnected() -> Status.ENABLED_BOUND
        osEnabled(context) -> Status.ENABLED_BINDING
        else -> Status.DISABLED
    }

    /**
     * Waits for the service to bind (cold start, process restart). Returns
     * false after [timeoutMs] so callers can report BLOCKED instead of hanging.
     */
    suspend fun awaitBound(timeoutMs: Long = 5_000L): Boolean {
        if (NeedleAccessibilityService.isConnected()) return true
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            delay(250)
            if (NeedleAccessibilityService.isConnected()) return true
        }
        return NeedleAccessibilityService.isConnected()
    }
}
