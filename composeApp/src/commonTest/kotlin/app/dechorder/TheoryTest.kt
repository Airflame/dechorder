package app.dechorder

import app.dechorder.Synth.midis
import app.dechorder.music.ChordNamer
import app.dechorder.music.Reading
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TheoryTest {
    private fun name(vararg notes: String): String {
        val r = ChordNamer.read(midis(*notes))
        return when (r) {
            is Reading.ChordReading -> r.chord.symbol()
            is Reading.SingleNote -> "note"
            is Reading.Interval -> "interval"
            null -> "none"
        }
    }

    @Test
    fun namesTriadsAndInversions() {
        assertEquals("C", name("C4", "E4", "G4"))
        assertEquals("C/E", name("E3", "C4", "G4"))
        assertEquals("C/G", name("G2", "C4", "E4"))
        assertEquals("Am", name("A3", "C4", "E4"))
        assertEquals("Bdim", name("B3", "D4", "F4"))
        assertEquals("Csus4", name("C4", "F4", "G4"))
        assertEquals("Csus4", name("C3", "F3", "G3"))      // same notes: the bass decides
        assertEquals("Fsus2", name("F3", "G3", "C4"))
        assertEquals("Caug", name("C4", "E4", "G#4"))
    }

    @Test
    fun namesSeventhsAndExtensions() {
        assertEquals("C7", name("C3", "E3", "G3", "Bb3"))
        assertEquals("C7", name("C3", "E3", "Bb3"))          // fifth left out
        assertEquals("Am7", name("A2", "C4", "E4", "G4"))
        assertEquals("C6", name("C3", "E3", "G3", "A3"))
        assertEquals("Fmaj7", name("F3", "A3", "C4", "E4"))
        assertEquals("Dm7", name("D3", "F3", "A3", "C4"))
        assertEquals("Bm7b5", name("B2", "D3", "F3", "A3"))
        assertEquals("Ebdim7", name("D#3", "F#3", "A3", "C4"))
        assertEquals("Cadd9", name("C3", "E3", "G3", "D4"))
        assertEquals("D9", name("D3", "F#3", "A3", "C4", "E4"))
        assertEquals("Cmaj9", name("C3", "E3", "G3", "B3", "D4"))
        assertEquals("G7/B", name("B2", "D3", "F3", "G3"))
    }

    @Test
    fun namesPowerChordsNotesAndIntervals() {
        assertEquals("E5", name("E2", "B2", "E3"))
        assertEquals("note", name("A3", "A4"))
        assertEquals("interval", name("C4", "E4"))
        val r = ChordNamer.read(midis("C4", "F4"))
        assertIs<Reading.ChordReading>(r)
        assertEquals("F5/C", r.chord.symbol())
    }
}
