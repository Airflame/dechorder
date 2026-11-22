package app.dechorder.music

import kotlin.math.ln
import kotlin.math.pow

/** Pitch helpers. Pitch classes are 0..11 with C = 0; notes are MIDI numbers (A4 = 69, middle C = 60). */
object Notes {
    val SHARP_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val FLAT_NAMES = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")

    /** The usual spelling when no key is known: the accidental most often seen in chord charts. */
    val COMMON_NAMES = listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")

    private val BLACK = setOf(1, 3, 6, 8, 10)

    const val PIANO_LOWEST = 21   // A0
    const val PIANO_HIGHEST = 108 // C8

    fun pc(midi: Int): Int = ((midi % 12) + 12) % 12
    fun octave(midi: Int): Int = midi / 12 - 1
    fun isBlack(midi: Int): Boolean = pc(midi) in BLACK

    fun frequency(midi: Double, a4: Double = 440.0): Double = a4 * 2.0.pow((midi - 69.0) / 12.0)
    fun frequency(midi: Int, a4: Double = 440.0): Double = frequency(midi.toDouble(), a4)

    /** Fractional MIDI number of a frequency. */
    fun midiOf(freq: Double, a4: Double = 440.0): Double = 69.0 + 12.0 * ln(freq / a4) / ln(2.0)

    /** Pitch-class name, e.g. "C", "Eb". */
    fun name(pc: Int): String = COMMON_NAMES[pc(pc)]

    /** Note name with octave, e.g. "C4", "Bb2". */
    fun nameWithOctave(midi: Int): String = name(pc(midi)) + octave(midi)

    private val INTERVAL_NAMES = listOf(
        "unison", "minor second", "major second", "minor third", "major third", "perfect fourth",
        "tritone", "perfect fifth", "minor sixth", "major sixth", "minor seventh", "major seventh",
    )

    fun intervalName(semitones: Int): String = INTERVAL_NAMES[pc(semitones)]
}
