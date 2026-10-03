package dev.citali.needle.tools

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Settings
import dev.citali.needle.engine.NeedlePrefs
import dev.citali.needle.pilot.accessibility.NeedleAccessibilityService

/**
 * Keeps Wahari's accessibility service enabled once the user has set it up.
 *
 * Android never lets an app flip its own accessibility switch: the user must
 * enable it by hand. The only supported self-enable path is the
 * signature-level `WRITE_SECURE_SETTINGS` permission, granted once via adb
 * (or Shizuku/root). Everything here is a no-op without that grant, so
 * behaviour without it is exactly what it is today.
 *
 * Only the in-app switch ([NeedlePrefs.setA11yKeepEnabled]) may turn
 * keep-enabled off. Observed OS state never overwrites the flag.
 */
object AccessibilityAutoEnable {

    const val WRITE_SECURE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS"
    const val ADB_COMMAND = "adb shell pm grant com.ajirodesu.wahari android.permission.WRITE_SECURE_SETTINGS"

    private const val MAX_ATTEMPTS_PER_MINUTE = 3
    private const val WINDOW_MS = 60_000L
    private val attemptTimes = ArrayDeque<Long>()

    fun hasSecurePermission(context: Context): Boolean =
        context.checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun component(context: Context): ComponentName =
        ComponentName(context, NeedleAccessibilityService::class.java)

    /**
     * Pure string merge for the `ENABLED_ACCESSIBILITY_SERVICES` colon list.
     * Preserves other apps' entries, de-duplicates, and matches both the short
     * (`package/class`) and long (`package/full.class.Name`) component forms.
     */
    fun mergeEnabledServices(current: String?, component: String, enable: Boolean): String {
        val short = component
        // The same class can appear in short (relative) or long (fully
        // qualified) form. A leading dot is relative to the package.
        val long = if ('/' in component) {
            val (pkg, cls) = component.split('/', limit = 2)
            val full = when {
                cls.startsWith('.') -> pkg + cls
                '.' in cls -> cls
                else -> "$pkg.$cls"
            }
            "$pkg/$full"
        } else {
            component
        }
        fun isOurs(entry: String): Boolean =
            entry.equals(short, ignoreCase = true) || entry.equals(long, ignoreCase = true)
        val kept = (current.orEmpty().split(':'))
            .map { it.trim() }
            .filter { it.isNotEmpty() && !isOurs(it) }
            .distinct()
        return if (enable) (kept + short).joinToString(":") else kept.joinToString(":")
    }

    private fun allowAttempt(): Boolean {
        val now = SystemClock.elapsedRealtime()
        while (attemptTimes.isNotEmpty() && now - attemptTimes.first() > WINDOW_MS) {
            attemptTimes.removeFirst()
        }
        if (attemptTimes.size >= MAX_ATTEMPTS_PER_MINUTE) return false
        attemptTimes.addLast(now)
        return true
    }

    /**
     * Makes sure our service is in the enabled-services list and the master
     * accessibility switch is on. Returns true when the setting now contains
     * us. Does nothing without the secure-settings grant or when the user
     * switched keep-enabled off. Rate-limited: at most 3 writes per minute so
     * a conflicting system state can never spin.
     */
    fun ensure(context: Context): Boolean {
        if (!NeedlePrefs.a11yKeepEnabled(context)) return false
        if (!hasSecurePermission(context)) return false
        if (!allowAttempt()) return false
        return runCatching {
            val resolver = context.contentResolver
            val flat = component(context).flattenToString()
            val current = Settings.Secure.getString(
                resolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            )
            val merged = mergeEnabledServices(current, flat, enable = true)
            if (merged != (current.orEmpty())) {
                Settings.Secure.putString(
                    resolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    merged,
                )
            }
            Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            true
        }.getOrDefault(false)
    }

    /**
     * Removes our service from the enabled-services list (user asked to turn
     * automation off). Needs the secure-settings grant; otherwise the caller
     * must send the user to system settings.
     */
    fun remove(context: Context): Boolean {
        if (!hasSecurePermission(context)) return false
        return runCatching {
            val resolver = context.contentResolver
            val flat = component(context).flattenToString()
            val current = Settings.Secure.getString(
                resolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            )
            val merged = mergeEnabledServices(current, flat, enable = false)
            if (merged != (current.orEmpty())) {
                Settings.Secure.putString(
                    resolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    merged,
                )
            }
            true
        }.getOrDefault(false)
    }

    /**
     * Forces a rebind when the OS says enabled but the service never connects:
     * briefly removes our entry and re-adds it. Needs the grant.
     */
    fun rebind(context: Context): Boolean {
        if (!hasSecurePermission(context)) return false
        if (!allowAttempt()) return false
        return runCatching {
            remove(context)
            // Yield so the system processes the removal before the re-add;
            // without this the two writes can coalesce and nothing rebinds.
            Thread.sleep(400)
            ensure(context)
        }.getOrDefault(false)
    }
}
