package app.dechorder

import app.dechorder.Synth.midis
import app.dechorder.engine.ChordEngine
import app.dechorder.engine.EngineConfig
import app.dechorder.engine.LiveState
import app.dechorder.music.Notes
import app.dechorder.music.Reading
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DetectionTest {
    private fun run(engine: ChordEngine, audio: FloatArray, config: EngineConfig = EngineConfig()): List<LiveState> {
        val states = ArrayList<LiveState>()
        var i = 0
        val chunk = FloatArray(1024)
        while (i < audio.size) {
            val n = minOf(chunk.size, audio.size - i)
            audio.copyInto(chunk, 0, i, i + n)
            engine.push(chunk, n, config) { states += it }
            i += n
        }
        return states
    }

    private fun listen(audio: FloatArray, config: EngineConfig = EngineConfig()): LiveState {
        val engine = ChordEngine(Synth.RATE)
        return run(engine, audio, config).last()
    }

    private fun symbol(s: LiveState): String = when (val r = s.reading) {
        is Reading.ChordReading -> r.chord.symbol()
        is Reading.SingleNote -> "note " + Notes.name(r.notes.first())
        is Reading.Interval -> "interval"
        null -> "none"
    }

    private fun names(s: LiveState) = s.notes.map { Notes.nameWithOctave(it.midi) }

    private fun assertChord(expected: String, vararg notes: String, config: EngineConfig = EngineConfig()) {
        val state = listen(Synth.notes(midis(*notes), 1.0), config)
        assertEquals(expected, symbol(state), "notes heard: ${names(state)}")
    }

    @Test fun majorTriad() = assertChord("C", "C4", "E4", "G4")
    @Test fun minorTriadLow() = assertChord("Am", "A2", "E3", "A3", "C4")
    @Test fun inversion() = assertChord("G/B", "B2", "D4", "G4")
    @Test fun dominantSeventh() = assertChord("E7", "E2", "B2", "D3", "G#3")
    @Test fun minorSeventh() = assertChord("Dm7", "D3", "F3", "A3", "C4")
    @Test fun majorSeventh() = assertChord("Fmaj7", "F2", "C3", "E3", "A3")
    @Test fun wideVoicing() = assertChord("C", "C2", "G3", "C4", "E4")
    @Test fun highChord() = assertChord("Bb", "Bb4", "D5", "F5")

    @Test
    fun otherTuning() {
        val audio = Synth.notes(midis("D3", "F#3", "A3"), 1.0, a4 = 432.0)
        assertEquals("D", symbol(listen(audio, EngineConfig(a4 = 432.0))))
    }

    @Test
    fun singleNoteLightsOneKey() {
        val state = listen(Synth.notes(midis("A3"), 1.0))
        assertEquals("note A", symbol(state))
        assertEquals(listOf("A3"), names(state))
    }

    @Test
    fun lightsTheNotesPlayed() {
        val state = listen(Synth.notes(midis("C3", "E3", "G3"), 1.0))
        assertEquals(listOf("C3", "E3", "G3"), names(state))
    }

    @Test
    fun silenceIsQuiet() {
        val state = listen(Synth.silence(1.0))
        assertEquals("none", symbol(state))
        assertTrue(state.notes.isEmpty())
    }

    /** The chords shown over time, without repeats. */
    private fun shown(states: List<LiveState>): List<String> =
        states.mapNotNull { (it.reading as? Reading.ChordReading)?.chord?.symbol() }
            .fold(emptyList()) { acc, c -> if (acc.lastOrNull() == c) acc else acc + c }

    @Test
    fun longDecayKeepsTheChord() {
        for (chord in listOf(midis("C3", "E3", "G3", "C4"), midis("E2", "B2", "E3", "G#3", "B3"), midis("D3", "A3", "D4", "F4"))) {
            val engine = ChordEngine(Synth.RATE)
            val names = shown(run(engine, Synth.notes(chord, 4.0)))
            assertEquals(1, names.size, "a fading chord was shown as several: $names")
        }
    }

    @Test fun minorTriad() = assertChord("Cm", "C3", "G3", "C4", "Eb4")

    @Test
    fun quietPlaying() {
        val state = listen(Synth.notes(midis("C3", "G3", "C4", "Eb4"), 1.0, amplitude = 0.01, noiseDb = -80.0))
        assertEquals("Cm", symbol(state), "notes heard: ${names(state)}")
    }

    @Test
    fun noisyRoom() {
        val state = listen(Synth.notes(midis("G2", "D3", "G3", "B3", "F4"), 1.0, noiseDb = -42.0))
        assertEquals("G7", symbol(state), "notes heard: ${names(state)}")
    }

    @Test
    fun followsAProgression() {
        val engine = ChordEngine(Synth.RATE)
        val progression = listOf(
            midis("C3", "E3", "G3", "C4"),
            midis("F2", "A3", "C4", "F4"),
            midis("G2", "B3", "D4", "G4"),
            midis("A2", "C4", "E4", "A4"),
        )
        val states = progression.withIndex().flatMap { (i, chord) -> run(engine, Synth.notes(chord, 1.2, seed = i + 10)) }
        assertEquals(listOf("C", "F", "G", "Am"), shown(states))
    }

    @Test
    fun clearsAfterSilence() {
        val engine = ChordEngine(Synth.RATE)
        run(engine, Synth.notes(midis("C3", "E3", "G3"), 1.0))
        assertEquals("none", symbol(run(engine, Synth.silence(1.0)).last()))
    }

    @Test
    fun noteLimitKeepsTheStrongestNotes() {
        val chord = midis("C3", "E3", "G3", "B3", "D4", "F#4")
        val unlimited = listen(Synth.notes(chord, 1.0))
        assertTrue(unlimited.notes.size > 4, "expected more than 4 notes without a limit: ${names(unlimited)}")
        val limited = listen(Synth.notes(chord, 1.0), EngineConfig(maxNotes = 4))
        assertEquals(4, limited.notes.size, "notes heard: ${names(limited)}")
        assertTrue(names(limited).all { it in names(unlimited) }, "limited ${names(limited)} vs ${names(unlimited)}")
    }

    @Test
    fun noteLimitAboveTheChordSizeChangesNothing() {
        val state = listen(Synth.notes(midis("D3", "F3", "A3", "C4"), 1.0), EngineConfig(maxNotes = 4))
        assertEquals("Dm7", symbol(state), "notes heard: ${names(state)}")
        assertEquals(listOf("D3", "F3", "A3", "C4"), names(state))
    }
}
