package app.dechorder.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Colours after Chordfinder's dark Qt look. */
object Palette {
    val Window = Color(0xFF1E1E1E)
    val Base = Color(0xFF262626)         // cards, tables, inputs
    val Raised = Color(0xFF2D2D2D)       // buttons, selected tab
    val Border = Color(0xFF3C3C3C)
    val BorderStrong = Color(0xFF4A4A4A)
    val Text = Color(0xFFEDEDED)
    val TextDim = Color(0xFF9A9A9A)
    val TextFaint = Color(0xFF6B6B6B)
    val Accent = Color(0xFF2EE07A)       // the green of Chordfinder's progress bars and sliders
    val AccentDark = Color(0xFF1B8F4C)
    val Playhead = Color(0xFFE5484D)
    val Selection = Color(0xFF34393F)

    val KeyWhite = Color(0xFFECECEC)
    val KeyWhiteEdge = Color(0xFFB9B9B9)
    val KeyBlack = Color(0xFF151515)
    val KeyFelt = Color(0xFF7A1E24)

    fun mix(a: Color, b: Color, f: Float): Color = lerp(a, b, f.coerceIn(0f, 1f))
}

@Composable
fun DechorderTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Palette.Accent,
        onPrimary = Color(0xFF0B2A17),
        secondary = Palette.Accent,
        background = Palette.Window,
        onBackground = Palette.Text,
        surface = Palette.Window,
        onSurface = Palette.Text,
        surfaceVariant = Palette.Base,
        onSurfaceVariant = Palette.TextDim,
        surfaceContainer = Palette.Raised,
        surfaceContainerHigh = Palette.Raised,
        surfaceContainerHighest = Palette.Raised,
        outline = Palette.BorderStrong,
        outlineVariant = Palette.Border,
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
