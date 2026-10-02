package dev.citali.needle.engine

import android.content.Context
import dev.citali.needle.tools.PhoneTools

/** Small, non-secret preferences for the on-device assistant. */
object NeedlePrefs {

    private const val FILE = "needle_prefs"
    private const val KEY_PACKS = "tool_packs"
    private const val KEY_TELEGRAM_TOKEN = "telegram_token"
    private const val KEY_TELEGRAM_ENABLED = "telegram_enabled"
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

    fun toolPacks(context: Context): Set<PhoneTools.Pack> {
        val stored = prefs(context).getStringSet(KEY_PACKS, null) ?: return PhoneTools.defaultPacks
        val packs = stored.mapNotNull { name -> PhoneTools.Pack.entries.firstOrNull { it.name == name } }.toSet()
        return packs.ifEmpty { setOf(PhoneTools.Pack.CORE) }
    }

    fun setToolPacks(context: Context, packs: Set<PhoneTools.Pack>) {
        prefs(context).edit().putStringSet(KEY_PACKS, packs.map { it.name }.toSet()).apply()
    }

    fun telegramToken(context: Context): String = prefs(context).getString(KEY_TELEGRAM_TOKEN, "").orEmpty()

    fun setTelegramToken(context: Context, token: String) {
        prefs(context).edit().putString(KEY_TELEGRAM_TOKEN, token.trim()).apply()
    }

    fun telegramEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_TELEGRAM_ENABLED, false)

    fun setTelegramEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_TELEGRAM_ENABLED, enabled).apply()
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
