package dev.citali.needle.engine

import android.content.Context
import dev.citali.needle.tools.PhoneTools

/** Small, non-secret preferences for the on-device assistant. */
object NeedlePrefs {

    private const val FILE = "needle_prefs"
    private const val KEY_PACKS = "tool_packs"
    private const val KEY_TELEGRAM_TOKEN = "telegram_token"
    private const val KEY_TELEGRAM_ENABLED = "telegram_enabled"
    private const val KEY_TELEGRAM_ADMIN_IDS = "telegram_admin_ids"
    private const val KEY_A11Y_SETUP = "accessibility_setup_completed"
    private const val KEY_A11Y_KEEP = "a11y_keep_enabled"
    private const val KEY_MAX_TOKENS = "max_new_tokens"
    private const val KEY_SHOW_REASONING = "show_reasoning"
    private const val KEY_AGENT_MODE = "agent_mode"
    private const val KEY_INTRO_COMPLETED = "intro_completed"
    private const val KEY_SETUP_STEP = "setup_step"
    private const val KEY_SETUP_COMPLETE = "setup_complete"
    private const val KEY_NEEDLE3_SOURCE = "needle3_install_source"

    const val SETUP_INTRO = "intro"
    const val SETUP_NEEDLE = "needle"
    const val SETUP_ACCESSIBILITY = "accessibility"
    const val SETUP_DONE = "done"

    const val NEEDLE3_SRC_DOWNLOAD = "download"
    const val NEEDLE3_SRC_IMPORT = "import"
    const val NEEDLE3_SRC_EXISTING = "existing"

    const val AGENT_MODE_NORMAL = "normal"
    const val AGENT_MODE_AUTOMATE = "automate"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun toolPacks(context: Context): Set<PhoneTools.Pack> =
        deserializeToolPacks(prefs(context).getStringSet(KEY_PACKS, null))

    fun setToolPacks(context: Context, packs: Set<PhoneTools.Pack>) {
        prefs(context).edit().putStringSet(KEY_PACKS, serializeToolPacks(packs)).apply()
    }

    /**
     * Pure serialization for the tool-pack selection, so the round-trip is
     * covered by unit tests. Unknown names (e.g. from a newer app version)
     * are dropped without resetting the known packs.
     */
    fun serializeToolPacks(packs: Set<PhoneTools.Pack>): Set<String> =
        packs.map { it.name }.toSet()

    fun deserializeToolPacks(stored: Set<String>?): Set<PhoneTools.Pack> {
        if (stored == null) return PhoneTools.defaultPacks
        val packs = stored.mapNotNull { name ->
            PhoneTools.Pack.entries.firstOrNull { it.name == name }
        }.toSet()
        return packs.ifEmpty { setOf(PhoneTools.Pack.CORE) }
    }

    /**
     * Telegram bot token. The ciphertext lives in [SecureStore] (Android
     * Keystore); this is the single accessor, so callers never need to know
     * where the bytes are. A legacy plaintext value is migrated once on read.
     */
    fun telegramToken(context: Context): String =
        dev.citali.needle.pilot.data.SecureStore.getTelegramToken(context)
            ?: migrateTelegramToken(context)

    fun setTelegramToken(context: Context, token: String) {
        val clean = token.trim()
        if (clean.isEmpty()) {
            dev.citali.needle.pilot.data.SecureStore.saveTelegramToken(context, "")
            prefs(context).edit().remove(KEY_TELEGRAM_TOKEN).apply()
            return
        }
        if (dev.citali.needle.pilot.data.SecureStore.saveTelegramToken(context, clean)) {
            prefs(context).edit().remove(KEY_TELEGRAM_TOKEN).apply()
        } else {
            // Keystore unavailable: keep the token in prefs rather than lose it.
            prefs(context).edit().putString(KEY_TELEGRAM_TOKEN, clean).apply()
        }
    }

    private fun migrateTelegramToken(context: Context): String {
        val legacy = prefs(context).getString(KEY_TELEGRAM_TOKEN, "").orEmpty().trim()
        if (legacy.isBlank()) return ""
        return if (dev.citali.needle.pilot.data.SecureStore.saveTelegramToken(context, legacy)) {
            prefs(context).edit().remove(KEY_TELEGRAM_TOKEN).apply()
            legacy
        } else {
            legacy
        }
    }

    fun telegramEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_TELEGRAM_ENABLED, false)

    fun setTelegramEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_TELEGRAM_ENABLED, enabled).apply()
    }

    /**
     * Application-level accessibility setup history: true once the user has
     * completed the Wahari Accessibility setup. Survives restarts, force-stop
     * and reboot; cleared only by Clear data / reinstall. This is NOT the
     * live service state — query [DevicePermissions.isAccessibilityServiceEnabled]
     * for whether the service is currently enabled.
     */
    fun accessibilitySetupCompleted(context: Context): Boolean =
        prefs(context).getBoolean(KEY_A11Y_SETUP, false)

    fun setAccessibilitySetupCompleted(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_A11Y_SETUP, value).apply()
        if (value) {
            // First completion opts into keep-enabled; only the in-app switch
            // may turn it back off.
            setA11yKeepEnabled(context, true)
        }
    }

    /**
     * User intent: the accessibility service must stay enabled. Defaults to
     * true once setup has finished; only the in-app switch may change it —
     * observed OS state must never overwrite it. Written with commit() because
     * boot receivers and observers act on it before any UI exists.
     */
    fun a11yKeepEnabled(context: Context): Boolean {
        val prefs = prefs(context)
        if (prefs.contains(KEY_A11Y_KEEP)) return prefs.getBoolean(KEY_A11Y_KEEP, true)
        return accessibilitySetupCompleted(context)
    }

    fun setA11yKeepEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_A11Y_KEEP, value).commit()
    }

    /**
     * Telegram admin allowlist: the numeric user IDs permitted to drive the
     * bot. Empty means nobody is authorized yet (secure default) — commands
     * are refused until at least one ID is saved. Find an ID with /myid.
     */
    fun telegramAdminIds(context: Context): Set<Long> =
        prefs(context).getStringSet(KEY_TELEGRAM_ADMIN_IDS, null)
            ?.mapNotNull { it.toLongOrNull()?.takeIf { id -> id != 0L } }
            ?.toSet() ?: emptySet()

    fun setTelegramAdminIds(context: Context, ids: Set<Long>) {
        val clean = ids.filter { it != 0L }.map { it.toString() }.toSet()
        prefs(context).edit().putStringSet(KEY_TELEGRAM_ADMIN_IDS, clean).apply()
    }

    fun isTelegramAdmin(context: Context, userId: Long): Boolean {
        if (userId == 0L) return false
        val admins = telegramAdminIds(context)
        return admins.isNotEmpty() && userId in admins
    }

    /** Parse the admin-ID field: digits separated by commas, spaces or newlines. */
    fun parseTelegramAdminIds(raw: String): Pair<Set<Long>, List<String>> {
        val ids = linkedSetOf<Long>()
        val invalid = mutableListOf<String>()
        raw.split(',', ' ', '\n', '\r', '\t', ';').forEach { part ->
            val token = part.trim()
            if (token.isEmpty()) return@forEach
            val id = token.toLongOrNull()?.takeIf { it != 0L }
            if (id == null) invalid.add(token) else ids.add(id)
        }
        return ids to invalid
    }

    fun maxNewTokens(context: Context): Int = prefs(context).getInt(KEY_MAX_TOKENS, 512).coerceIn(128, 1024)

    fun setMaxNewTokens(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_MAX_TOKENS, value.coerceIn(128, 1024)).apply()
    }

    fun showReasoning(context: Context): Boolean = prefs(context).getBoolean(KEY_SHOW_REASONING, true)

    fun setShowReasoning(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_REASONING, value).apply()
    }

    fun agentMode(context: Context): String =
        prefs(context).getString(KEY_AGENT_MODE, AGENT_MODE_NORMAL)
            .takeIf { it == AGENT_MODE_AUTOMATE || it == AGENT_MODE_NORMAL }
            ?: AGENT_MODE_NORMAL

    fun setAgentMode(context: Context, value: String) {
        val safe = if (value == AGENT_MODE_AUTOMATE) AGENT_MODE_AUTOMATE else AGENT_MODE_NORMAL
        prefs(context).edit().putString(KEY_AGENT_MODE, safe).apply()
    }

    /** First-install Intro. False on a fresh install; persisted immediately on Continue/Skip. */
    fun introCompleted(context: Context): Boolean =
        prefs(context).getBoolean(KEY_INTRO_COMPLETED, false)

    fun setIntroCompleted(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_INTRO_COMPLETED, value).apply()
    }

    /** Current setup step; survives rotation, recreation and restart. */
    fun setupStep(context: Context): String =
        prefs(context).getString(KEY_SETUP_STEP, SETUP_INTRO)
            .takeIf { it == SETUP_NEEDLE || it == SETUP_ACCESSIBILITY || it == SETUP_DONE }
            ?: SETUP_INTRO

    fun setSetupStep(context: Context, value: String) {
        val safe = when (value) {
            SETUP_NEEDLE, SETUP_ACCESSIBILITY, SETUP_DONE -> value
            else -> SETUP_INTRO
        }
        prefs(context).edit().putString(KEY_SETUP_STEP, safe).apply()
    }

    fun setupComplete(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SETUP_COMPLETE, false)

    fun setSetupComplete(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_SETUP_COMPLETE, value).apply()
    }

    /** Where the installed model came from; empty when nothing is installed. */
    fun needle3InstallSource(context: Context): String =
        prefs(context).getString(KEY_NEEDLE3_SOURCE, "").orEmpty()

    fun setNeedle3InstallSource(context: Context, value: String) {
        val safe = when (value) {
            NEEDLE3_SRC_DOWNLOAD, NEEDLE3_SRC_IMPORT, NEEDLE3_SRC_EXISTING -> value
            else -> ""
        }
        prefs(context).edit().putString(KEY_NEEDLE3_SOURCE, safe).apply()
    }
}
