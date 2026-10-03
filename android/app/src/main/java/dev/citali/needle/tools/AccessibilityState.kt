package dev.citali.needle.tools

/**
 * Persistence rules for setup gating and accessibility state.
 *
 * These are pure functions (no Android APIs) so the persistence contract is
 * covered by JVM unit tests. The Android-dependent halves live in
 * [NeedlePrefs] (durable setup history) and [DevicePermissions]
 * (live OS service state).
 */
object AccessibilityState {

    /** Current accessibility UI state, derived from setup history + OS truth. */
    enum class UiState {
        /** Service is currently enabled: automation is usable. */
        READY,

        /** Setup completed before, but the service is currently disabled. */
        DISABLED_AFTER_SETUP,

        /** The service was never configured. */
        NEVER_CONFIGURED,
    }

    /**
     * Combines persisted setup completion with the live OS service state.
     *
     * Disabling the service later never erases the historical completion:
     * (setupCompleted=true, serviceEnabled=false) resolves to
     * DISABLED_AFTER_SETUP, not NEVER_CONFIGURED.
     */
    fun resolve(setupCompleted: Boolean, serviceEnabled: Boolean): UiState = when {
        serviceEnabled -> UiState.READY
        setupCompleted -> UiState.DISABLED_AFTER_SETUP
        else -> UiState.NEVER_CONFIGURED
    }

    /**
     * Whether the first-run setup flow must be shown. Only a genuine first
     * install (intro not completed) or an unfinished setup shows it; clearing
     * app data / reinstall is the only way back once both are done.
     */
    fun shouldShowSetup(introCompleted: Boolean, setupComplete: Boolean): Boolean =
        !introCompleted || !setupComplete
}
