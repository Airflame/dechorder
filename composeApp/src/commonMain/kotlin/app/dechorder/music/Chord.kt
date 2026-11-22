package app.dechorder.music

/**
 * A chord quality: its intervals above the root and how it is written.
 *
 * @param prior small bonus for common chords, used to break near-ties when naming a set of notes
 * @param fifthOptional the perfect fifth may be left out (as players often do) without changing the name
 */
enum class Quality(
    val intervals: List<Int>,
    val symbol: String,
    val prior: Double,
    val fifthOptional: Boolean,
) {
    MAJ(listOf(0, 4, 7), "", 0.15, true),
    MIN(listOf(0, 3, 7), "m", 0.15, true),
    DIM(listOf(0, 3, 6), "dim", 0.05, false),
    AUG(listOf(0, 4, 8), "aug", 0.0, false),
    SUS2(listOf(0, 2, 7), "sus2", 0.0, false),
    SUS4(listOf(0, 5, 7), "sus4", 0.05, false),
    POWER(listOf(0, 7), "5", 0.0, false),
    MAJ6(listOf(0, 4, 7, 9), "6", 0.05, true),
    MIN6(listOf(0, 3, 7, 9), "m6", 0.05, true),
    DOM7(listOf(0, 4, 7, 10), "7", 0.1, true),
    MAJ7(listOf(0, 4, 7, 11), "maj7", 0.1, true),
    MIN7(listOf(0, 3, 7, 10), "m7", 0.1, true),
    MINMAJ7(listOf(0, 3, 7, 11), "m(maj7)", 0.0, true),
    DIM7(listOf(0, 3, 6, 9), "dim7", 0.05, false),
    HDIM7(listOf(0, 3, 6, 10), "m7b5", 0.05, false),
    SEVENSUS4(listOf(0, 5, 7, 10), "7sus4", 0.03, false),
    AUG7(listOf(0, 4, 8, 10), "7#5", 0.0, false),
    ADD9(listOf(0, 2, 4, 7), "add9", 0.03, true),
    MADD9(listOf(0, 2, 3, 7), "m(add9)", 0.02, true),
    MAJ69(listOf(0, 2, 4, 7, 9), "6/9", 0.0, true),
    DOM9(listOf(0, 2, 4, 7, 10), "9", 0.03, true),
    MAJ9(listOf(0, 2, 4, 7, 11), "maj9", 0.03, true),
    MIN9(listOf(0, 2, 3, 7, 10), "m9", 0.03, true),
    DOM7B9(listOf(0, 1, 4, 7, 10), "7b9", 0.0, true),
    DOM7S9(listOf(0, 3, 4, 7, 10), "7#9", 0.0, true),
    DOM13(listOf(0, 4, 7, 9, 10), "13", 0.0, true),
    MIN11(listOf(0, 2, 3, 5, 7, 10), "m11", 0.0, true),
    ;

    /** Tones that must sound for the name to apply. */
    val required: List<Int> get() = if (fifthOptional) intervals.filter { it != 7 } else intervals
}

/** A chord with an optional bass note other than the root (a slash chord such as C/E). */
data class Chord(val root: Int, val quality: Quality, val bass: Int = root) {
    val pitchClasses: Set<Int> get() = quality.intervals.map { Notes.pc(root + it) }.toSet()
    val isInversion: Boolean get() = bass != root

    fun symbol(): String {
        val base = Notes.name(root) + quality.symbol
        return if (isInversion) base + "/" + Notes.name(bass) else base
    }
}

/** What the notes currently sounding amount to. */
sealed interface Reading {
    /** Notes (MIDI numbers, low to high) the reading was made from. */
    val notes: List<Int>

    data class SingleNote(override val notes: List<Int>) : Reading
    data class Interval(override val notes: List<Int>) : Reading {
        val semitones: Int get() = Notes.pc(notes.last() - notes.first())
    }
    data class ChordReading(val chord: Chord, override val notes: List<Int>) : Reading
}
