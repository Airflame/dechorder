package app.dechorder.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dechorder.engine.ActiveNote
import app.dechorder.music.Notes

/**
 * A piano keyboard from [low] to [high] (MIDI numbers, both white keys) whose keys light up for the
 * notes sounding. Brighter means louder; the lowest note (the bass) is marked with a dot.
 */
@Composable
fun PianoKeyboard(
    low: Int,
    high: Int,
    notes: List<ActiveNote>,
    showNames: Boolean,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        val lit = notes.associate { it.midi to it.strength }
        val bass = notes.minOfOrNull { it.midi }
        val whites = (low..high).filter { !Notes.isBlack(it) }
        if (whites.isEmpty()) return@Canvas
        val felt = 4.dp.toPx()
        val top = felt
        val whiteW = size.width / whites.size
        val whiteH = size.height - top
        val blackW = whiteW * 0.62f
        val blackH = whiteH * 0.62f
        val radius = CornerRadius(minOf(3.dp.toPx(), whiteW * 0.2f))

        drawRect(Palette.KeyFelt, Offset.Zero, Size(size.width, felt))

        // White keys.
        val whiteX = HashMap<Int, Float>()
        whites.forEachIndexed { i, midi ->
            val x = i * whiteW
            whiteX[midi] = x
            val s = lit[midi]
            val fill = if (s != null) Palette.mix(Palette.KeyWhite, Palette.Accent, 0.55f + 0.45f * s) else Palette.KeyWhite
            drawRoundRect(fill, Offset(x + 0.5f, top), Size(whiteW - 1f, whiteH), radius)
            drawRect(Palette.KeyWhiteEdge, Offset(x + whiteW - 1f, top), Size(1f, whiteH))
            val labelBottom = top + whiteH - 4.dp.toPx()
            if (s != null && showNames && whiteW >= 11.dp.toPx()) {
                label(measurer, Notes.name(midi), x, whiteW, labelBottom, Color(0xFF0B2A17), bold = true)
            } else if (Notes.pc(midi) == 0 && whiteW >= 9.dp.toPx()) {
                label(measurer, "C${Notes.octave(midi)}", x, whiteW, labelBottom, Palette.TextFaint, bold = false)
            }
            if (midi == bass) dot(Offset(x + whiteW / 2, top + whiteH * 0.72f), whiteW, Color(0xFF0B2A17))
        }

        // Black keys, centred on the gap between their neighbours.
        for (midi in low..high) {
            if (!Notes.isBlack(midi)) continue
            val left = whiteX[midi - 1] ?: continue
            val x = left + whiteW - blackW / 2
            val s = lit[midi]
            val fill = if (s != null) Palette.mix(Palette.AccentDark, Palette.Accent, 0.4f + 0.6f * s) else Palette.KeyBlack
            drawRoundRect(fill, Offset(x, top), Size(blackW, blackH), CornerRadius(radius.x * 0.7f))
            if (s != null && showNames && blackW >= 9.dp.toPx()) {
                label(measurer, Notes.name(midi), x, blackW, top + blackH - 4.dp.toPx(), Color(0xFF0B2A17), bold = true, small = true)
            }
            if (midi == bass) dot(Offset(x + blackW / 2, top + blackH * 0.62f), blackW, Color(0xFF0B2A17))
        }

        // Notes beyond the keys shown: an arrow at that end.
        if (notes.any { it.midi < low }) arrow(left = true, top, whiteH)
        if (notes.any { it.midi > high }) arrow(left = false, top, whiteH)
    }
}

private fun DrawScope.label(
    measurer: TextMeasurer, text: String, x: Float, width: Float, bottom: Float, color: Color,
    bold: Boolean, small: Boolean = false,
) {
    val style = TextStyle(color = color, fontSize = if (small) 9.sp else 10.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    val layout = measurer.measure(text, style)
    if (layout.size.width > width + 2) return
    drawText(layout, topLeft = Offset(x + (width - layout.size.width) / 2, bottom - layout.size.height))
}

private fun DrawScope.dot(center: Offset, keyWidth: Float, color: Color) {
    drawCircle(color, radius = minOf(keyWidth * 0.18f, 4.dp.toPx()), center = center)
}

private fun DrawScope.arrow(left: Boolean, top: Float, height: Float) {
    val w = 10.dp.toPx()
    val h = 16.dp.toPx()
    val cy = top + height * 0.3f
    val x0 = if (left) 2.dp.toPx() else size.width - 2.dp.toPx()
    val dir = if (left) 1 else -1
    val path = Path().apply {
        moveTo(x0, cy)
        lineTo(x0 + dir * w, cy - h / 2)
        lineTo(x0 + dir * w, cy + h / 2)
        close()
    }
    drawPath(path, Palette.Accent)
}
