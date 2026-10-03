package dev.citali.needle.engine

/**
 * What Wahari promises about device compatibility.
 *
 * One universal APK installs on any Android 7.0+ phone, tablet, foldable,
 * Chromebook or emulator. Only arm64-v8a carries the real Needle engine;
 * every other ABI builds a stub `libneedlejni.so` so the app installs and
 * runs there and honestly reports the engine as unavailable.
 */
object DeviceCompatibility {

    /** Floor for installation (Android 7.0, Nougat). */
    const val MIN_SDK = 24

    /** ABIs shipped in the universal APK. */
    val INSTALL_ABIS: List<String> = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

    /** ABIs that carry the real on-device inference engine. */
    val ENGINE_ABIS: Set<String> = setOf("arm64-v8a")

    /** True when this ABI can run on-device inference. */
    fun isEngineAbi(abi: String): Boolean = abi in ENGINE_ABIS

    /**
     * True when at least one of the device's supported ABIs ships the engine.
     * Empty input means unknown → false (the app then says so, never guesses).
     */
    fun deviceHasEngine(supportedAbis: Array<String>?): Boolean {
        if (supportedAbis.isNullOrEmpty()) return false
        return supportedAbis.any { isEngineAbi(it.trim()) }
    }

    /** Short user-facing line for the engine status rows. */
    fun engineStatusLine(supportedAbis: Array<String>?): String =
        if (deviceHasEngine(supportedAbis)) "available"
        else "not on this CPU (needs 64-bit ARM)"
}
