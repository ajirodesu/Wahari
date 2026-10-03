package dev.citali.needle.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Exact icon data from Appendix A of the reskin prompt. 24x24 viewBox,
 * stroke-width 2, round caps/joins, never filled. Parsed once and cached.
 */
object WahariIcons {
    private fun icon(vararg d: String): ImageVector =
        ImageVector.Builder(
            name = "wahari",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            d.forEach { data ->
                addPath(
                    pathData = PathParser().parsePathString(data).toNodes(),
                    stroke = SolidColor(Color.White),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private fun rect(x: Float, y: Float, w: Float, h: Float, rx: Float): String {
        if (rx <= 0f) return "M$x $y H${x + w} V${y + h} H$x Z"
        return "M${x + rx} $y H${x + w - rx} " +
            "A$rx $rx 0 0 1 ${x + w} ${y + rx} " +
            "V${y + h - rx} " +
            "A$rx $rx 0 0 1 ${x + w - rx} ${y + h} " +
            "H${x + rx} " +
            "A$rx $rx 0 0 1 $x ${y + h - rx} " +
            "V${y + rx} " +
            "A$rx $rx 0 0 1 ${x + rx} $y Z"
    }

    private fun circle(cx: Float, cy: Float, r: Float): String =
        "M${cx - r} $cy A$r $r 0 1 0 ${cx + r} $cy A$r $r 0 1 0 ${cx - r} $cy Z"

    val menu: ImageVector by lazy { icon("M4 8H20", "M4 16H20") }
    val rotateCcw: ImageVector by lazy {
        icon("M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8", "M3 3v5h5")
    }
    val copy: ImageVector by lazy {
        icon(rect(9f, 9f, 13f, 13f, 2f), "M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1")
    }
    val pencil: ImageVector by lazy {
        icon("M12 20h9", "M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z")
    }
    val layoutGrid: ImageVector by lazy {
        icon(
            rect(3.5f, 3.5f, 6.5f, 6.5f, 1.8f),
            rect(14f, 3.5f, 6.5f, 6.5f, 1.8f),
            rect(14f, 14f, 6.5f, 6.5f, 1.8f),
            rect(3.5f, 14f, 6.5f, 6.5f, 1.8f),
        )
    }
    val mic: ImageVector by lazy {
        icon(
            "M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z",
            "M19 10v2a7 7 0 0 1-14 0v-2",
            "M12 19V22",
        )
    }
    val audioLines: ImageVector by lazy {
        icon("M2 10v4", "M6 6v12", "M10 3v18", "M14 7v10", "M18 5v14", "M22 10v4")
    }
    val arrowUp: ImageVector by lazy { icon("m5 12 7-7 7 7", "M12 19V5") }
    val settings: ImageVector by lazy {
        icon(
            "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z",
            circle(12f, 12f, 3f),
        )
    }
    val wrench: ImageVector by lazy {
        icon("M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z")
    }
    val messageSquare: ImageVector by lazy {
        icon("M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z")
    }
    val infinity: ImageVector by lazy {
        icon("M18.178 8c5.096 0 5.096 8 0 8-5.095 0-7.133-8-12.739-8-4.585 0-4.585 8 0 8 5.606 0 7.644-8 12.74-8z")
    }
    val terminal: ImageVector by lazy { icon("M4 17L10 11L4 5", "M12 19H20") }
    val bot: ImageVector by lazy {
        icon(
            "M12 8V4H8",
            rect(4f, 8f, 16f, 12f, 2f),
            "M2 14h2",
            "M20 14h2",
            "M15 13v2",
            "M9 13v2",
        )
    }
    val shieldAlert: ImageVector by lazy {
        icon(
            "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z",
            "M12 8v4",
            "M12 16h.01",
        )
    }
    val cpu: ImageVector by lazy {
        icon(
            rect(4f, 4f, 16f, 16f, 2f),
            rect(9f, 9f, 6f, 6f, 1f),
            "M15 2v2",
            "M15 20v2",
            "M2 15h2",
            "M2 9h2",
            "M20 15h2",
            "M20 9h2",
            "M9 2v2",
            "M9 20v2",
        )
    }
    val wifiOff: ImageVector by lazy {
        icon(
            "M2 2L22 22",
            "M12 20h.01",
            "M8.5 16.429a5 5 0 0 1 7 0",
            "M5 12.859a10 10 0 0 1 5.17-2.69",
            "M19 12.859a10 10 0 0 0-2.007-1.523",
            "M2 8.82a15 15 0 0 1 4.177-2.643",
            "M22 8.82a15 15 0 0 0-11.288-3.764",
        )
    }
    val check: ImageVector by lazy { icon("M20 6L9 17L4 12") }
    val arrowLeft: ImageVector by lazy { icon("m12 19-7-7 7-7", "M19 12H5") }
    val x: ImageVector by lazy { icon("M18 6L6 18", "m6 6 12 12") }
    val square: ImageVector by lazy { icon(rect(5f, 5f, 14f, 14f, 2f)) }
    val bell: ImageVector by lazy {
        icon(
            "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9",
            "M10.3 21a1.94 1.94 0 0 0 3.4 0",
        )
    }
    val phone: ImageVector by lazy {
        icon("M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z")
    }
    val history: ImageVector by lazy {
        icon(
            "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8",
            "M3 3v5h5",
            "M12 7v5l4 2",
        )
    }
    val users: ImageVector by lazy {
        icon(
            "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2",
            circle(9f, 7f, 4f),
            "M22 21v-2a4 4 0 0 0-3-3.87",
            "M16 3.13a4 4 0 0 1 0 7.75",
        )
    }
    val camera: ImageVector by lazy {
        icon(
            "M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2.5-3z",
            circle(12f, 13f, 3f),
        )
    }
    val mapPin: ImageVector by lazy {
        icon(
            "M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0Z",
            circle(12f, 10f, 3f),
        )
    }
    val sun: ImageVector by lazy {
        icon(
            circle(12f, 12f, 4f),
            "M12 2v2",
            "M12 20v2",
            "m4.93 4.93 1.41 1.41",
            "m17.66 17.66 1.41 1.41",
            "M2 12h2",
            "M20 12h2",
            "m6.34 17.66-1.41 1.41",
            "m19.07 4.93-1.41 1.41",
        )
    }
    val batteryCharging: ImageVector by lazy {
        icon(
            rect(2f, 7f, 16f, 10f, 2f),
            "M22 11v2",
            "m11 7-3 5h4l-3 5",
        )
    }
    val smartphone: ImageVector by lazy { icon(rect(7f, 2f, 14f, 20f, 2f), "M12 18h.01") }
}
