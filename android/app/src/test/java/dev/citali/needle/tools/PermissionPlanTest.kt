package dev.citali.needle.tools

import dev.citali.needle.engine.NeedlePrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Permission plan: grouping, API/hardware filtering, denial states, batch
 * ordering and the setup-step round trip. All inputs are explicit parameters
 * (sdk, hardware, grant maps) so no device is needed.
 */
class PermissionPlanTest {

    private val sdk26 = 26
    private val sdk33 = 33
    private val sdk34 = 34

    private fun cap(id: String): PermissionPlan.Capability =
        PermissionPlan.capabilities.first { it.id == id }

    @Test
    fun groupMembershipMatchesSpec() {
        val groups = PermissionPlan.capabilities.groupBy({ it.group }, { it.id })
        assertEquals(
            setOf("mic", "notifications"),
            groups[PermissionPlan.Group.VOICE_NOTIFY]?.toSet(),
        )
        assertEquals(
            setOf("sms", "calls", "call_log", "phone_state", "contacts"),
            groups[PermissionPlan.Group.MESSAGES_CALLS]?.toSet(),
        )
        assertTrue(
            groups[PermissionPlan.Group.DEVICE]?.containsAll(listOf("camera", "location")) == true,
        )
        assertTrue(
            groups[PermissionPlan.Group.SPECIAL]?.containsAll(
                listOf("brightness", "battery", "auto_revoke"),
            ) == true,
        )
    }

    @Test
    fun notificationsHiddenBelowApi33() {
        assertFalse(
            PermissionPlan.applicable(cap("notifications"), sdk26, hasTelephony = true, hasCamera = true),
        )
        assertTrue(
            PermissionPlan.applicable(cap("notifications"), sdk33, hasTelephony = true, hasCamera = true),
        )
    }

    @Test
    fun hardwareFiltering() {
        assertFalse(
            PermissionPlan.applicable(cap("sms"), sdk34, hasTelephony = false, hasCamera = true),
        )
        assertFalse(
            PermissionPlan.applicable(cap("camera"), sdk34, hasTelephony = true, hasCamera = false),
        )
        assertTrue(
            PermissionPlan.applicable(cap("contacts"), sdk34, hasTelephony = false, hasCamera = false),
        )
        assertTrue(
            PermissionPlan.applicable(cap("location"), sdk26, hasTelephony = false, hasCamera = false),
        )
    }

    @Test
    fun mediaFilesUseVersionedPermissions() {
        val audio33 = PermissionPlan.runtimePermissions(cap("media_files"), sdk33)
        assertTrue(audio33.contains("android.permission.READ_MEDIA_AUDIO"))
        assertTrue(audio33.contains("android.permission.READ_MEDIA_VIDEO"))
        assertTrue(audio33.none { it.endsWith("READ_EXTERNAL_STORAGE") })
        val legacy = PermissionPlan.runtimePermissions(cap("media_files"), sdk26)
        assertEquals(listOf("android.permission.READ_EXTERNAL_STORAGE"), legacy)
    }

    @Test
    fun statusDerivationMatrix() {
        // Granted always wins.
        assertEquals(
            PermissionPlan.PermStatus.GRANTED,
            PermissionPlan.permStatus(granted = true, canAskAgain = false, requestedBefore = true),
        )
        // Never asked: deniable, not blocked.
        assertEquals(
            PermissionPlan.PermStatus.DENIED,
            PermissionPlan.permStatus(granted = false, canAskAgain = false, requestedBefore = false),
        )
        // Asked + rationale: deniable again.
        assertEquals(
            PermissionPlan.PermStatus.DENIED,
            PermissionPlan.permStatus(granted = false, canAskAgain = true, requestedBefore = true),
        )
        // Asked + no rationale: "Don't ask again" (or Android 11+ double-deny).
        assertEquals(
            PermissionPlan.PermStatus.PERMANENTLY_DENIED,
            PermissionPlan.permStatus(granted = false, canAskAgain = false, requestedBefore = true),
        )
    }

    @Test
    fun capabilityStatusAggregates() {
        val sms = cap("sms")
        val granted = mapOf(
            "android.permission.SEND_SMS" to PermissionPlan.PermStatus.GRANTED,
            "android.permission.READ_SMS" to PermissionPlan.PermStatus.GRANTED,
        )
        assertEquals(
            PermissionPlan.CapStatus.GRANTED,
            PermissionPlan.capabilityStatus(sms, granted, fineGranted = false, coarseGranted = false),
        )
        val mixed = mapOf(
            "android.permission.SEND_SMS" to PermissionPlan.PermStatus.GRANTED,
            "android.permission.READ_SMS" to PermissionPlan.PermStatus.PERMANENTLY_DENIED,
        )
        assertEquals(
            PermissionPlan.CapStatus.PERMANENTLY_DENIED,
            PermissionPlan.capabilityStatus(sms, mixed, fineGranted = false, coarseGranted = false),
        )
        val denied = mapOf(
            "android.permission.SEND_SMS" to PermissionPlan.PermStatus.DENIED,
            "android.permission.READ_SMS" to PermissionPlan.PermStatus.DENIED,
        )
        assertEquals(
            PermissionPlan.CapStatus.DENIED,
            PermissionPlan.capabilityStatus(sms, denied, fineGranted = false, coarseGranted = false),
        )
    }

    @Test
    fun locationApproximateOnly() {
        val location = cap("location")
        val states = mapOf(
            "android.permission.ACCESS_FINE_LOCATION" to PermissionPlan.PermStatus.DENIED,
            "android.permission.ACCESS_COARSE_LOCATION" to PermissionPlan.PermStatus.GRANTED,
        )
        assertEquals(
            PermissionPlan.CapStatus.APPROXIMATE,
            PermissionPlan.capabilityStatus(location, states, fineGranted = false, coarseGranted = true),
        )
        assertTrue(PermissionPlan.satisfied(PermissionPlan.CapStatus.APPROXIMATE))
        assertFalse(PermissionPlan.satisfied(PermissionPlan.CapStatus.DENIED))
    }

    @Test
    fun batchOrderingSkipsGrantedAndInapplicable() {
        // Everything granted except SMS: one batch, SMS pair together.
        val batches = PermissionPlan.requestBatches(
            sdk = sdk34,
            hasTelephony = true,
            hasCamera = true,
            granted = { it != "android.permission.SEND_SMS" && it != "android.permission.READ_SMS" },
        )
        assertEquals(1, batches.size)
        assertEquals(
            setOf("android.permission.SEND_SMS", "android.permission.READ_SMS"),
            batches.single().toSet(),
        )
    }

    @Test
    fun batchOrderFollowsCapabilityOrder() {
        val batches = PermissionPlan.requestBatches(
            sdk = sdk34,
            hasTelephony = true,
            hasCamera = true,
            granted = { false },
        )
        assertTrue(batches.isNotEmpty())
        // Mic first, location's pair travels together in one batch.
        assertEquals(listOf("android.permission.RECORD_AUDIO"), batches.first())
        val location = batches.first { it.contains("android.permission.ACCESS_FINE_LOCATION") }
        assertTrue(location.contains("android.permission.ACCESS_COARSE_LOCATION"))
        // Special rows never appear as runtime batches.
        assertTrue(batches.flatten().none { it.isBlank() })
    }

    @Test
    fun batchesSkipMissingHardware() {
        val batches = PermissionPlan.requestBatches(
            sdk = sdk34,
            hasTelephony = false,
            hasCamera = false,
            granted = { false },
        )
        val flat = batches.flatten().joinToString(" ")
        assertTrue("SEND_SMS" !in flat && "CAMERA" !in flat)
        assertTrue("RECORD_AUDIO" in flat && "ACCESS_FINE_LOCATION" in flat)
    }

    @Test
    fun setupMembershipIsExact() {
        val setup = PermissionPlan.capabilities.filter { it.inSetup }.map { it.id }.toSet()
        assertEquals(
            setOf(
                "mic", "notifications", "sms", "calls", "call_log", "phone_state",
                "contacts", "camera", "location", "brightness", "battery", "auto_revoke",
            ),
            setup,
        )
        assertTrue(PermissionPlan.capabilities.first { it.id == "media_files" }.let { !it.inSetup })
    }

    @Test
    fun setupStepRoundTrip() {
        assertEquals(NeedlePrefs.SETUP_PERMISSIONS, NeedlePrefs.sanitizeSetupStep("permissions"))
        assertEquals(NeedlePrefs.SETUP_NEEDLE, NeedlePrefs.sanitizeSetupStep("needle"))
        assertEquals(NeedlePrefs.SETUP_ACCESSIBILITY, NeedlePrefs.sanitizeSetupStep("accessibility"))
        assertEquals(NeedlePrefs.SETUP_DONE, NeedlePrefs.sanitizeSetupStep("done"))
        assertEquals(NeedlePrefs.SETUP_INTRO, NeedlePrefs.sanitizeSetupStep("needle-typo"))
        assertEquals(NeedlePrefs.SETUP_INTRO, NeedlePrefs.sanitizeSetupStep(null))
        assertEquals(NeedlePrefs.SETUP_INTRO, NeedlePrefs.sanitizeSetupStep(""))
    }
}
