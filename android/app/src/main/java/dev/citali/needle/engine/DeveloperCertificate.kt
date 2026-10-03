package dev.citali.needle.engine

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dev.citali.needle.BuildConfig
import java.security.MessageDigest

/**
 * Developer certificate for Wahari.
 *
 * Binds the developer identity (AjiroDesu) to the APK signing certificate so
 * an installed app can prove "this is Wahari, signed with this key" at
 * runtime. Shown in Settings → Developer certificate and verified against the
 * optional pinned fingerprint baked in via WAHARI_EXPECTED_CERT_SHA256.
 */
object DeveloperCertificate {

    const val DEVELOPER = "AjiroDesu"
    const val APP_NAME = "Wahari"
    const val PACKAGE_NAME = "com.ajirodesu.wahari"
    const val ENGINE = "Needle 3 · Cactus Compute (Apache-2.0)"
    const val AUTOMATION_CORE = "TaskPilot · TherealCitali (MIT)"

    enum class Verification { VERIFIED, MISMATCH, UNPINNED, UNKNOWN }

    data class CertificateInfo(
        val packageName: String,
        val versionName: String,
        val versionCode: Long,
        /** Colon-separated uppercase SHA-256, e.g. "AA:BB:…", or "" when unknown. */
        val sha256: String,
        val verification: Verification,
    )

    /** "aabb…" / "AA-BB…" / "AA:BB…" → "AA:BB:…". Empty stays empty. */
    fun normalizeFingerprint(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val hex = raw.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }
        if (hex.isEmpty()) return ""
        return hex.chunked(2).joinToString(":")
    }

    /** SHA-256 of raw certificate bytes, formatted with [normalizeFingerprint]. */
    fun formatSha256(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(":") { "%02X".format(it) }
    }

    fun fingerprintsMatch(a: String?, b: String?): Boolean {
        val x = normalizeFingerprint(a)
        val y = normalizeFingerprint(b)
        return x.isNotEmpty() && x == y
    }

    fun verify(runtimeSha256: String?, expectedSha256: String?): Verification {
        val runtime = normalizeFingerprint(runtimeSha256)
        if (runtime.isEmpty()) return Verification.UNKNOWN
        val expected = normalizeFingerprint(expectedSha256)
        if (expected.isEmpty()) return Verification.UNPINNED
        return if (runtime == expected) Verification.VERIFIED else Verification.MISMATCH
    }

    fun buildCertificateText(info: CertificateInfo): String =
        buildString {
            appendLine("$APP_NAME — Developer Certificate")
            appendLine("Developer: $DEVELOPER")
            appendLine("Package: ${info.packageName}")
            appendLine("Version: ${info.versionName} (${info.versionCode})")
            appendLine("SHA-256: ${info.sha256.ifEmpty { "unknown" }}")
            appendLine("Status: ${info.verification}")
            appendLine("Engine: $ENGINE")
            appendLine("Automation: $AUTOMATION_CORE")
        }.trimEnd()

    /**
     * Read this app's signing certificate at runtime. Never throws: returns
     * null only when the signatures cannot be read.
     */
    fun read(context: Context, expectedSha256: String? = expectedFromBuild()): CertificateInfo? {
        return try {
            val pm = context.packageManager
            val pkg = context.packageName
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
            }
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signing = info.signingInfo ?: return null
                if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
            } else {
                @Suppress("DEPRECATION")
                info.signatures
            }
            val first = signatures?.firstOrNull()?.toByteArray() ?: return null
            val sha256 = formatSha256(first)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            CertificateInfo(
                packageName = pkg,
                versionName = info.versionName ?: "unknown",
                versionCode = versionCode,
                sha256 = sha256,
                verification = verify(sha256, expectedSha256),
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Fingerprint pinned at build time via WAHARI_EXPECTED_CERT_SHA256, or "". */
    fun expectedFromBuild(): String {
        return try {
            normalizeFingerprint(BuildConfig.WAHARI_EXPECTED_CERT_SHA256)
        } catch (_: Exception) {
            ""
        }
    }
}
