package dev.citali.needle.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The developer certificate binds the install to a signing key, so the
 * fingerprint helpers it relies on are pinned here.
 */
class DeveloperCertificateTest {

    @Test
    fun normalizeAcceptsBareLowercaseAndDashForms() {
        assertEquals("AA:BB:0C", DeveloperCertificate.normalizeFingerprint("aabb0c"))
        assertEquals("AA:BB:0C", DeveloperCertificate.normalizeFingerprint("aa-bb-0c"))
        assertEquals("AA:BB:0C", DeveloperCertificate.normalizeFingerprint("aa:bb:0c"))
    }

    @Test
    fun normalizeEmptyStaysEmpty() {
        assertEquals("", DeveloperCertificate.normalizeFingerprint(null))
        assertEquals("", DeveloperCertificate.normalizeFingerprint(""))
        assertEquals("", DeveloperCertificate.normalizeFingerprint(":::"))
    }

    @Test
    fun fingerprintsMatchIgnoresFormatting() {
        assertTrue(DeveloperCertificate.fingerprintsMatch("aa:bb:cc", "AA-BB-CC"))
        assertFalse(DeveloperCertificate.fingerprintsMatch("aa:bb:cc", "aa:bb:cd"))
        assertFalse(DeveloperCertificate.fingerprintsMatch("", "AA:BB:CC"))
    }

    @Test
    fun verifyReportsUnpinnedWhenNothingIsPinned() {
        assertEquals(
            DeveloperCertificate.Verification.UNPINNED,
            DeveloperCertificate.verify("AA:BB:CC", null),
        )
        assertEquals(
            DeveloperCertificate.Verification.UNKNOWN,
            DeveloperCertificate.verify(null, "AA:BB:CC"),
        )
    }

    @Test
    fun verifyMatchesAgainstPinnedKey() {
        assertEquals(
            DeveloperCertificate.Verification.VERIFIED,
            DeveloperCertificate.verify("aa:bb:cc", "AA:BB:CC"),
        )
        assertEquals(
            DeveloperCertificate.Verification.MISMATCH,
            DeveloperCertificate.verify("AA:BB:CC", "AA:BB:CD"),
        )
    }

    @Test
    fun certificateTextCarriesDeveloperAndFingerprint() {
        val info = DeveloperCertificate.CertificateInfo(
            packageName = "com.ajirodesu.wahari",
            versionName = "0.0.7",
            versionCode = 7L,
            sha256 = "AA:BB:CC",
            verification = DeveloperCertificate.Verification.UNPINNED,
        )
        val text = DeveloperCertificate.buildCertificateText(info)
        assertTrue(text.contains("AjiroDesu"))
        assertTrue(text.contains("AA:BB:CC"))
        assertTrue(text.contains("com.ajirodesu.wahari"))
    }
}
