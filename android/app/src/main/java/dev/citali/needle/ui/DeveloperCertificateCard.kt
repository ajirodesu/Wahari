package dev.citali.needle.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.citali.needle.engine.DeveloperCertificate
import dev.citali.needle.ui.theme.WahariTokens
import dev.citali.needle.ui.theme.WahariTypography

/**
 * In-app developer certificate: who built Wahari plus the runtime APK
 * signing fingerprint so a sideloaded install can be verified by eye.
 */
@Composable
fun DeveloperCertificateCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val info = remember(context) { DeveloperCertificate.read(context) }
    var copied by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(28.dp)
    val innerShape = RoundedCornerShape(18.dp)

    val (pillText, pillColor, dotColor) = when (info?.verification) {
        DeveloperCertificate.Verification.VERIFIED ->
            Triple("VERIFIED", Color(0x224ADE80), WahariTokens.success)
        DeveloperCertificate.Verification.MISMATCH ->
            Triple("MISMATCH", Color(0x33F87171), Color(0xFFF87171))
        DeveloperCertificate.Verification.UNPINNED ->
            Triple("UNPINNED", Color(0x1F1D88E5), Color(0xFF7AB8FF))
        else -> Triple("UNKNOWN", Color(0x228E8E96), WahariTokens.textMuted)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF1D2028), Color(0xFF141417), Color(0xFF101012)),
                ),
            )
            .border(1.dp, Color(0x2BFFFFFF), cardShape)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "DEVELOPER CERTIFICATE",
                style = WahariTypography.optionTag.copy(
                    color = WahariTokens.textMuted,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = androidx.compose.ui.unit.TextUnit(1.6f, androidx.compose.ui.unit.TextUnitType.Sp),
                ),
            )
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(pillColor)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    pillText,
                    style = WahariTypography.optionTag.copy(
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF2B9BF0), Color(0xFF155A9C)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Badge,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    DeveloperCertificate.APP_NAME,
                    style = WahariTypography.sheetTitle.copy(
                        fontSize = androidx.compose.ui.unit.TextUnit(17f, androidx.compose.ui.unit.TextUnitType.Sp),
                    ),
                )
                Text(
                    "Developed by ${DeveloperCertificate.DEVELOPER}",
                    style = WahariTypography.sectionSubtitle.copy(color = WahariTokens.textSecondary),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(innerShape)
                .background(Color.Black.copy(alpha = 0.32f))
                .border(1.dp, Color(0x1AFFFFFF), innerShape)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            CertRow(label = "Package", value = info?.packageName ?: DeveloperCertificate.PACKAGE_NAME)
            CertRow(
                label = "Version",
                value = if (info != null) "${info.versionName} (${info.versionCode})" else "unknown",
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "SHA-256",
                    style = WahariTypography.sectionSubtitle.copy(color = WahariTokens.textMuted),
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    info?.sha256 ?: "unavailable",
                    style = WahariTypography.assistantBullet.copy(
                        color = WahariTokens.textPrimary,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = androidx.compose.ui.unit.TextUnit(11.5f, androidx.compose.ui.unit.TextUnitType.Sp),
                    ),
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        copyFingerprint(context, info?.sha256.orEmpty())
                        copied = true
                    },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Copy fingerprint",
                        tint = WahariTokens.textMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            if (copied) {
                Text(
                    "Fingerprint copied — compare with apksigner --print-certs.",
                    style = WahariTypography.sectionSubtitle.copy(color = WahariTokens.textMuted),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }

        Text(
            when (info?.verification) {
                DeveloperCertificate.Verification.VERIFIED ->
                    "Signature matches the pinned release key."
                DeveloperCertificate.Verification.MISMATCH ->
                    "Signature does NOT match the pinned key — do not trust this build."
                else ->
                    "Unpinned project-key build: verify the fingerprint against the release SHA256SUMS."
            },
            style = WahariTypography.sectionSubtitle.copy(color = WahariTokens.textSecondary),
        )
    }
}

@Composable
private fun CertRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            label,
            style = WahariTypography.sectionSubtitle.copy(color = WahariTokens.textMuted),
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = WahariTypography.assistantBullet.copy(
                color = WahariTokens.textPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = androidx.compose.ui.unit.TextUnit(13.5f, androidx.compose.ui.unit.TextUnitType.Sp),
            ),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

private fun copyFingerprint(context: Context, fingerprint: String) {
    if (fingerprint.isEmpty()) return
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Wahari signing fingerprint", fingerprint))
}
