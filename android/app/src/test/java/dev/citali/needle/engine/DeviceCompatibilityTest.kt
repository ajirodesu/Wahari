package dev.citali.needle.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The universal APK must stay installable on every ABI while promising
 * inference on arm64 only — pin that contract here.
 */
class DeviceCompatibilityTest {

    @Test
    fun engineRunsOnArm64Only() {
        assertTrue(DeviceCompatibility.isEngineAbi("arm64-v8a"))
        assertFalse(DeviceCompatibility.isEngineAbi("armeabi-v7a"))
        assertFalse(DeviceCompatibility.isEngineAbi("x86_64"))
        assertFalse(DeviceCompatibility.isEngineAbi("x86"))
        assertFalse(DeviceCompatibility.isEngineAbi(""))
    }

    @Test
    fun everyShippedAbiIsKnown() {
        assertEquals(
            listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86"),
            DeviceCompatibility.INSTALL_ABIS,
        )
    }

    @Test
    fun deviceWithArm64HasEngine() {
        assertTrue(DeviceCompatibility.deviceHasEngine(arrayOf("arm64-v8a", "armeabi-v7a")))
        assertFalse(DeviceCompatibility.deviceHasEngine(arrayOf("armeabi-v7a")))
        assertFalse(DeviceCompatibility.deviceHasEngine(arrayOf("x86_64")))
        assertFalse(DeviceCompatibility.deviceHasEngine(null))
        assertFalse(DeviceCompatibility.deviceHasEngine(arrayOf()))
    }
}
