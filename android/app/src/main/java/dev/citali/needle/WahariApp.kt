package dev.citali.needle

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import dev.citali.needle.engine.NeedlePrefs
import dev.citali.needle.tools.AccessibilityAutoEnable

/**
 * Application entry point: keeps the accessibility service enabled across
 * process restarts without any UI being open.
 *
 * Watches the secure enabled-services list and re-adds Wahari if the system
 * or an OEM panel removes it — but only while the user's keep-enabled flag is
 * on and the secure-settings grant is held. Never fights the user: turning
 * the in-app switch off stops all of this.
 */
class WahariApp : Application() {

    private val servicesUri: Uri = Settings.Secure.getUriFor(
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    )

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            if (uri != null && uri != servicesUri) return
            ensureKept()
        }

        override fun onChange(selfChange: Boolean) = ensureKept()
    }

    override fun onCreate() {
        super.onCreate()
        ensureKept()
        runCatching {
            contentResolver.registerContentObserver(
                servicesUri,
                false,
                observer,
            )
        }
    }

    private fun ensureKept() {
        if (!NeedlePrefs.a11yKeepEnabled(this)) return
        // ensure() itself no-ops without the grant and is rate-limited, so
        // this call is cheap and cannot loop against the system.
        runCatching { AccessibilityAutoEnable.ensure(this) }
    }
}

/** Re-applies keep-enabled after reboot or app update. */
class BootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: android.content.Context, intent: android.content.Intent?) {
        val action = intent?.action ?: return
        if (action != android.content.Intent.ACTION_BOOT_COMPLETED &&
            action != android.content.Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != android.content.Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        if (!NeedlePrefs.a11yKeepEnabled(context)) return
        // A receiver lives ~10 s; the write is one Settings.Secure call.
        // goAsync() keeps the process until it finishes.
        val pending = goAsync()
        Thread {
            try {
                runCatching { AccessibilityAutoEnable.ensure(context) }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
