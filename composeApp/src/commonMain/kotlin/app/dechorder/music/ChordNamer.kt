package app.dechorder.music

/**
 * Names the chord formed by a set of sounding notes.
 *
 * Every root and quality is scored against the pitch classes heard: chord tones that sound count for
 * it, notes outside the chord count against it, and a missing (optional) fifth costs a little. The
 * lowest note decides between equally good names (A C E G over A is Am7, over C it is C6) and
 * produces slash chords for inversions (C/E).
 */
object ChordNamer {
    private const val EXTRA_PENALTY = 1.2
    private const val MISSING_FIFTH_PENALTY = 0.3
    private const val BASS_IS_ROOT_BONUS = 0.4
    private const val BASS_OUTSIDE_PENALTY = 0.3
    private const val COMPLEXITY_PENALTY = 0.1   // per tone beyond a triad

    /**
     * @param notes MIDI numbers of the sounding notes
     * @param weights optional per-note weight (0..1, same order as [notes]); quiet notes count less
     */
    fun read(notes: List<Int>, weights: List<Double>? = null): Reading? {
        if (notes.isEmpty()) return null
        val order = notes.indices.sortedBy { notes[it] }
        val sorted = order.map { notes[it] }
        val pcWeight = DoubleArray(12)
        for (i in order) {
            val w = weights?.get(i)?.coerceIn(0.0, 1.0) ?: 1.0
            val pc = Notes.pc(notes[i])
            pcWeight[pc] = maxOf(pcWeight[pc], 0.5 + 0.5 * w)
        }
        val pcs = (0 until 12).filter { pcWeight[it] > 0 }
        val bass = Notes.pc(sorted.first())
        return when (pcs.size) {
            1 -> Reading.SingleNote(sorted)
            2 -> {
                val interval = Notes.pc(pcs[1] - pcs[0])
                when (interval) {
                    7 -> Reading.ChordReading(Chord(pcs[0], Quality.POWER, bass), sorted)
                    5 -> Reading.ChordReading(Chord(pcs[1], Quality.POWER, bass), sorted)
                    else -> Reading.Interval(sorted)
                }
            }
            else -> best(pcWeight, bass)?.let { Reading.ChordReading(it, sorted) } ?: Reading.Interval(sorted)
        }
    }

    /** Best chord for weighted pitch classes, or null if no root/quality fits at all. */
    fun best(pcWeight: DoubleArray, bass: Int): Chord? {
        var bestChord: Chord? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for (root in 0 until 12) {
            if (pcWeight[root] <= 0) continue
            for (q in Quality.entries) {
                if (q == Quality.POWER) continue
                val s = score(pcWeight, bass, root, q) ?: continue
                if (s > bestScore) {
                    bestScore = s
                    bestChord = Chord(root, q, bass)
                }
            }
        }
        return bestChord
    }

    private fun score(pcWeight: DoubleArray, bass: Int, root: Int, q: Quality): Double? {
        for (i in q.required) if (pcWeight[Notes.pc(root + i)] <= 0) return null
        val chordPcs = q.intervals.map { Notes.pc(root + it) }.toSet()
        var covered = 0.0
        var extra = 0.0
        for (pc in 0 until 12) {
            val w = pcWeight[pc]
            if (w <= 0) continue
            if (pc in chordPcs) covered += w else extra += w
        }
        var s = covered - EXTRA_PENALTY * extra + q.prior
        if (q.fifthOptional && 7 in q.intervals && pcWeight[Notes.pc(root + 7)] <= 0) s -= MISSING_FIFTH_PENALTY
        s -= COMPLEXITY_PENALTY * (q.intervals.size - 3).coerceAtLeast(0)
        if (bass == root) s += BASS_IS_ROOT_BONUS
        else if (bass !in chordPcs) s -= BASS_OUTSIDE_PENALTY
        return s
    }
}
