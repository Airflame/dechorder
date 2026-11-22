package app.dechorder.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** The Dechorder icon: three piano keys on a dark tile, the middle one lit green, with black keys between. */
val AppIcon: ImageVector by lazy {
    ImageVector.Builder("Dechorder", 48.dp, 48.dp, 48f, 48f).apply {
        path(fill = SolidColor(Color(0xFF262626))) {
            moveTo(10f, 2f); lineTo(38f, 2f)
            arcTo(8f, 8f, 0f, false, true, 46f, 10f); lineTo(46f, 38f)
            arcTo(8f, 8f, 0f, false, true, 38f, 46f); lineTo(10f, 46f)
            arcTo(8f, 8f, 0f, false, true, 2f, 38f); lineTo(2f, 10f)
            arcTo(8f, 8f, 0f, false, true, 10f, 2f); close()
        }
        fun key(x: Float, color: Color) = path(fill = SolidColor(color)) {
            moveTo(x, 9f); lineTo(x + 9f, 9f); lineTo(x + 9f, 37f)
            arcTo(2f, 2f, 0f, false, true, x + 7f, 39f); lineTo(x + 2f, 39f)
            arcTo(2f, 2f, 0f, false, true, x, 37f); close()
        }
        key(9f, Color(0xFFECECEC))
        key(19.5f, Color(0xFF2EE07A))
        key(30f, Color(0xFFECECEC))
        fun black(x: Float) = path(fill = SolidColor(Color(0xFF151515))) {
            moveTo(x, 9f); lineTo(x + 5f, 9f); lineTo(x + 5f, 26f); lineTo(x, 26f); close()
        }
        black(15.5f)
        black(27f)
    }.build()
}
