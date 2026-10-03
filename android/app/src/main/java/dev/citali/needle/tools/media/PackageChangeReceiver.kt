package dev.citali.needle.tools.media

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Drops the discovery cache when the installed-app set changes. */
class PackageChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REMOVED,
            Intent.ACTION_PACKAGE_REPLACED,
            -> MediaAppDiscovery.invalidate()
        }
    }
}
