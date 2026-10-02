package dev.citali.needle.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.citali.needle.R

/** Single source of truth for the Wahari visual layer. No hex literals anywhere else in UI code. */
object WahariTokens {
    val bgMain = Color(0xFF000000)
    val bgSurface = Color(0xFF121214)
    val bgSheet = Color(0xFF17171A)
    val bgCard = Color(0xFF202024)
    val bgCardPressed = Color(0xFF29292E)
    val bgCardSelected = Color(0xFF232730)
    val bgIcon = Color(0xFF2C2C32)
    val bgCapsule = Color(0xFF212121)
    val bgCapsulePressed = Color(0xFF333338)
    val bgDrawer = Color(0xFF141416)
    val bgPopup = Color(0xFF232327)
    val bgToast = Color(0xFF1C1C1F)

    val accent = Color(0xFF1D88E5)
    val accentPressed = Color(0xFF1877CB)
    val userBubble = Color(0xFF1961A5)
    val userBubbleActive = Color(0xFF1B69B2)

    val borderSubtle = Color(0x14FFFFFF)
    val borderCapsule = Color(0xFF424242)
    val borderEdge = Color(0x1FFFFFFF)
    val borderToast = Color(0x24FFFFFF)
    val borderSelected = Color(0xA61D88E5)
    val popupDivider = Color(0x1AFFFFFF)
    val popupButtonPressed = Color(0x2EFFFFFF)
    val menuPressedFill = Color(0x14FFFFFF)
    val buttonDisabledText = Color(0xFF6B6B73)

    val textPrimary = Color(0xFFF4F4F6)
    val textAi = Color(0xFFECECEE)
    val textSecondary = Color(0xFFD2D2D7)
    val textTag = Color(0xFFB5B5BE)
    val textMuted = Color(0xFF8E8E96)
    val textPlaceholder = Color(0xFF8E8E93)
    val popupButtonText = Color(0xFFF0F0F3)
    val settingsGearIcon = Color(0xFFE2E2E6)
    val handle = Color(0xFF52525A)
    val iconOnDark = Color(0xFFFFFFFF)
    val success = Color(0xFF4ADE80)
    val danger = Color(0xFFF87171)

    val mainEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    val pressEasing = CubicBezierEasing(0.2f, 0.8f, 0.4f, 1f)

    const val PRESS_MS = 140
    const val POPUP_MS = 200
    const val TOAST_MS = 220
    const val DOCK_MARGIN_MS = 280
    const val DRAWER_MS = 300
    const val SCRIM_MS = 280
    const val SHEET_MS = 340
    const val CAPSULE_MS = 240
    const val GLYPH_FADE_MS = 180
    const val GLYPH_MOVE_MS = 200

    /** Kept from the HTML's status-bar element; sized to the real status-bar inset. Non-interactive. */
    fun statusScrim(): Brush = Brush.verticalGradient(
        0.00f to Color.Black.copy(alpha = 0.96f),
        0.28f to Color.Black.copy(alpha = 0.88f),
        0.58f to Color.Black.copy(alpha = 0.60f),
        0.84f to Color.Black.copy(alpha = 0.20f),
        1.00f to Color.Black.copy(alpha = 0.00f),
    )

    fun topNavGradient(): Brush = Brush.verticalGradient(
        0.00f to Color.Black.copy(alpha = 0.98f),
        0.42f to Color.Black.copy(alpha = 0.92f),
        0.75f to Color.Black.copy(alpha = 0.65f),
        1.00f to Color.Black.copy(alpha = 0.00f),
    )

    fun bottomDockGradient(): Brush = Brush.verticalGradient(
        0.00f to Color.Black.copy(alpha = 0.00f),
        0.52f to Color.Black.copy(alpha = 0.72f),
        1.00f to Color.Black.copy(alpha = 0.95f),
    )

    fun sidebarHeaderGradient(): Brush = Brush.verticalGradient(
        0.00f to Color.Black.copy(alpha = 0.88f),
        0.65f to Color.Black.copy(alpha = 0.40f),
        1.00f to Color.Transparent,
    )

    fun sidebarTopInsetGradient(): Brush = Brush.verticalGradient(
        0.00f to Color.Black.copy(alpha = 0.96f),
        1.00f to Color.Black.copy(alpha = 0.88f),
    )

    const val SCRIM_SIDEBAR = 0.65f
    const val SCRIM_SHEET = 0.72f

    val capsuleShape = RoundedCornerShape(22.dp)
    val userBubbleShape = RoundedCornerShape(20.dp)
    val popupShape = RoundedCornerShape(30.dp)
    val popupButtonShape = RoundedCornerShape(20.dp)
    val optionCardShape = RoundedCornerShape(24.dp)
    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val drawerItemShape = RoundedCornerShape(16.dp)
}

internal val geistFontFamily = FontFamily(
    Font(R.font.geist_light, FontWeight.Light),
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
    Font(R.font.geist_bold, FontWeight.Bold),
)

/** Alias used by the composer, which derives its metrics from dp (see 10.2). */
val Geist: FontFamily get() = geistFontFamily

internal fun wahariStyle(
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    fontWeight: FontWeight = FontWeight.Normal,
    letterSpacing: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    color: Color,
): TextStyle = TextStyle(
    fontFamily = geistFontFamily,
    fontWeight = fontWeight,
    fontSize = fontSize,
    lineHeight = lineHeight,
    letterSpacing = letterSpacing,
    color = color,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)

/**
 * Wahari type scale. Geist is bundled under res/font (SIL OFL, see licenses/OFL-Geist.txt).
 * The composer input styles are built at the call site from dp (see Composer) so the
 * 44dp row and the 22dp-per-line math stay exact at any system font scale.
 */
object WahariTypography {
    val assistantBody = wahariStyle(15.sp, 23.1.sp, FontWeight.Normal, (-0.15).sp, WahariTokens.textAi)
    val assistantBold = wahariStyle(15.sp, 23.1.sp, FontWeight.Bold, (-0.15).sp, Color.White)
    val assistantBullet = wahariStyle(14.5.sp, 21.75.sp, FontWeight.Normal, color = WahariTokens.textSecondary)
    val userBubble = wahariStyle(15.sp, 21.75.sp, FontWeight.Normal, (-0.1).sp, Color.White)
    val popupButton = wahariStyle(13.sp, fontWeight = FontWeight.Medium, color = Color(0xFFF0F0F3))
    val toast = wahariStyle(13.sp, fontWeight = FontWeight.Medium, color = Color.White)
    val sidebarWordmark = wahariStyle(22.sp, 44.sp, FontWeight.Bold, (-0.4).sp, Color.White)
    val sidebarMenuItem = wahariStyle(16.sp, fontWeight = FontWeight.Medium, color = WahariTokens.textAi)
    val sheetTitle = wahariStyle(20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp, color = WahariTokens.textPrimary)
    val optionTitle = wahariStyle(16.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp, color = WahariTokens.textPrimary)
    val optionDescription = wahariStyle(13.sp, 18.7.sp, FontWeight.Normal, (-0.1).sp, WahariTokens.textMuted)
    val optionTag = wahariStyle(12.sp, fontWeight = FontWeight.Normal, color = WahariTokens.textTag)
    val pageTitle = wahariStyle(20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp, color = WahariTokens.textPrimary)
    val sectionTitle = wahariStyle(16.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp, color = WahariTokens.textPrimary)
    val sectionSubtitle = wahariStyle(13.sp, fontWeight = FontWeight.Normal, color = WahariTokens.textMuted)
    val systemMessage = wahariStyle(13.5.sp, fontWeight = FontWeight.Normal, color = WahariTokens.textMuted)
    val modelExtra = wahariStyle(13.sp, fontWeight = FontWeight.Normal, color = WahariTokens.textMuted)
    val monoBlock = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        color = WahariTokens.textSecondary,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        ),
    )
    val busyLabel = wahariStyle(14.sp, fontWeight = FontWeight.Normal, color = WahariTokens.textMuted)
}
