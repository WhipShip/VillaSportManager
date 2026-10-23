package com.example.villasportmanager.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Line icons used across the Kayan Club UI (24×24, 2px round strokes).
 * Tint them with Icon(tint = …) — the black stroke is replaced by the tint.
 */
object KayanIcons {

    private fun lineIcon(name: String, vararg paths: String, strokeWidth: Float = 2f): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        )
        paths.forEach { d ->
            builder.addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
        return builder.build()
    }

    /** Circle as path data (cx, cy, r). */
    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0"

    val Back by lazy { lineIcon("Back", "M19 12H5M11 18l-6-6 6-6") }
    val Forward by lazy { lineIcon("Forward", "M5 12h14M13 6l6 6-6 6", strokeWidth = 2.2f) }
    val ChevronRight by lazy { lineIcon("ChevronRight", "M9 6l6 6-6 6") }
    val Check by lazy { lineIcon("Check", "M5 12.5L10 17l9-10", strokeWidth = 2.4f) }
    val Close by lazy { lineIcon("Close", "M6 6l12 12M18 6L6 18") }
    val Menu by lazy { lineIcon("Menu", "M4 7h16M4 12h16M4 17h16") }
    val Home by lazy { lineIcon("Home", "M3 10.5L12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z") }
    val Bookings by lazy { lineIcon("Bookings", "M8 6h13M8 12h13M8 18h13M3.5 6h0.01M3.5 12h0.01M3.5 18h0.01") }
    val Calendar by lazy { lineIcon("Calendar", "M5 5h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2zM16 3v4M8 3v4M3 10h18") }
    val Update by lazy { lineIcon("Update", "M20 11a8 8 0 1 0-2.3 5.7M20 4v7h-7") }
    val Logout by lazy { lineIcon("Logout", "M15 4h4a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1h-4M10 17l5-5-5-5M15 12H3") }
    val User by lazy { lineIcon("User", circle(12f, 8f, 4f), "M4 21a8 8 0 0 1 16 0") }
    val Lock by lazy { lineIcon("Lock", "M6 11h12a1 1 0 0 1 1 1v8a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1v-8a1 1 0 0 1 1-1zM8 11V7a4 4 0 0 1 8 0v4") }

    // Club services
    val Gym by lazy { lineIcon("Gym", "M6 7v10M18 7v10M3 10v4M21 10v4M6 12h12") }
    val Swimming by lazy { lineIcon("Swimming", "M2 9c2 0 2-2 4-2s2 2 4 2 2-2 4-2 2 2 4 2 2-2 4-2M2 16c2 0 2-2 4-2s2 2 4 2 2-2 4-2 2 2 4 2 2-2 4-2") }
    val Tennis by lazy { lineIcon("Tennis", circle(10f, 9f, 6f), "M14.5 13.5l6 6") }
    val Beauty by lazy { lineIcon("Beauty", "M12 3v4M12 17v4M3 12h4M17 12h4M6 6l2.5 2.5M15.5 15.5L18 18M6 18l2.5-2.5M15.5 8.5L18 6") }
    val Vr by lazy { lineIcon("Vr", "M5 7h14a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2h-4l-2-3h-2l-2 3H5a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2z") }
    val Aerobics by lazy { lineIcon("Aerobics", "M3 12h4l3-8 4 16 3-8h4") }
}
