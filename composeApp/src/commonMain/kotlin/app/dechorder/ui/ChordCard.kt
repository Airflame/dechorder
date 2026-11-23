package app.dechorder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dechorder.engine.LiveState
import app.dechorder.music.Notes
import app.dechorder.music.Quality
import app.dechorder.music.Reading

private const val REFERENCE_SIZE = 100f
/** Horizontal space in the card that isn't text (dp): the accent bar and the paddings around it. */
private const val TEXT_INSET = 10f + 5f + 14f + 10f

/** The big display: the chord sounding now and its notes. */
@Composable
fun ChordCard(state: LiveState, modifier: Modifier = Modifier) {
    Panel(modifier) {
        val reading = state.reading
        if (!state.listening) {
            Text(
                state.error ?: if (state.starting) "Starting the microphone…"
                else "Press Listen and play some chords.\nDechorder names them and lights up their notes on the keyboard.",
                Modifier.align(Alignment.Center).padding(16.dp),
                color = if (state.error != null) Palette.Playhead else Palette.TextDim,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            return@Panel
        }

        val (title, subtitle) = when (reading) {
            is Reading.ChordReading -> reading.chord.symbol() to ""
            is Reading.SingleNote -> Notes.name(reading.notes.first()) to "single note"
            is Reading.Interval ->
                reading.notes.map { Notes.pc(it) }.distinct().joinToString("–") { Notes.name(it) } to
                    Notes.intervalName(reading.semitones)
            null -> "–" to ""
        }
        val accent = if (reading is Reading.ChordReading) Palette.Accent
        else if (reading != null) Palette.TextDim else Palette.Border

        BoxWithConstraints(Modifier.fillMaxSize()) {
            // One size for every chord: as big as the card allows (about a third of its height), but small
            // enough that the widest chord name without its bass (e.g. "Ebm(maj7)") fits the width. A slash
            // chord too wide for one line puts its bass on a second line ("Ebm(maj7)" / "/Ab"), so the size
            // leaves room in height for two lines.
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val widest = remember(measurer, density) {
                val style = TextStyle(fontSize = REFERENCE_SIZE.sp, fontWeight = FontWeight.Bold)
                fun width(text: String) = measurer.measure(text, style).size.width
                val root = Notes.COMMON_NAMES.maxBy { width(it) }
                val quality = Quality.entries.map { it.symbol }.maxBy { width(it) }
                with(density) { width("$root$quality").toDp().value } * 1.04f   // a little spare for kerning
            }
            val textWidth = maxWidth.value - TEXT_INSET
            val bigSize = minOf(
                maxHeight.value * 0.32f,
                (maxHeight.value - 28f) / 2.25f,   // two title lines, the notes line and the paddings
                REFERENCE_SIZE * textWidth / widest,
            ).coerceIn(1f, 130f)
            val titleStyle = TextStyle(fontSize = bigSize.sp, lineHeight = bigSize.sp, fontWeight = FontWeight.Bold)
            // The bass follows the last "/" (the 6/9 chord has one of its own).
            val slash = if (reading is Reading.ChordReading && reading.chord.isInversion) title.lastIndexOf('/') else -1
            val shownTitle = if (slash > 0 &&
                measurer.measure(title, titleStyle).size.width > with(density) { textWidth.dp.toPx() }
            ) title.substring(0, slash) + "\n" + title.substring(slash) else title
            Row(Modifier.fillMaxSize().padding(vertical = 12.dp)) {
                Box(Modifier.padding(start = 10.dp, top = 2.dp, bottom = 2.dp).width(5.dp).fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp)).background(accent))
                Column(Modifier.weight(1f).fillMaxHeight().padding(start = 14.dp, end = 10.dp), verticalArrangement = Arrangement.Center) {
                    Text(
                        shownTitle,
                        color = if (reading == null) Palette.TextFaint else Palette.Text,
                        style = titleStyle,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle.isNotEmpty()) {
                        Text(subtitle, color = Palette.TextDim, fontSize = (bigSize * 0.3f).coerceIn(16f, 30f).sp, maxLines = 1)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (state.notes.isEmpty()) "listening…" else state.notes.joinToString("  ") { Notes.nameWithOctave(it.midi) },
                        color = Palette.TextDim,
                        fontSize = (bigSize * 0.22f).coerceIn(13f, 24f).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
