package app.dechorder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dechorder.engine.LiveState
import app.dechorder.music.Notes
import app.dechorder.music.Reading

/** The big display: the chord sounding now and its notes. */
@Composable
fun ChordCard(state: LiveState, compact: Boolean, modifier: Modifier = Modifier) {
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

        Row(Modifier.fillMaxSize().padding(vertical = 12.dp)) {
            Box(Modifier.padding(start = 10.dp, top = 2.dp, bottom = 2.dp).width(5.dp).fillMaxHeight()
                .clip(RoundedCornerShape(2.dp)).background(accent))
            Column(Modifier.weight(1f).padding(start = 14.dp, end = 12.dp), verticalArrangement = Arrangement.Center) {
                val big = if (compact) 52.sp else 68.sp
                Text(
                    title,
                    color = if (reading == null) Palette.TextFaint else Palette.Text,
                    fontSize = big,
                    lineHeight = big,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotEmpty()) {
                    Text(subtitle, color = Palette.TextDim, fontSize = if (compact) 16.sp else 19.sp, maxLines = 1)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (state.notes.isEmpty()) "listening…" else state.notes.joinToString("  ") { Notes.nameWithOctave(it.midi) },
                    color = Palette.TextDim,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
