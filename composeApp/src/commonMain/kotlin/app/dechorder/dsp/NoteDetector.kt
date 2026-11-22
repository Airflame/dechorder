package app.dechorder.dsp

import app.dechorder.music.Notes
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A note found in one analysis frame. */
data class DetectedNote(val midi: Int, val salience: Double, val freq: Double)

/** How eagerly notes are reported. Built from the user's sensitivity setting. */
data class DetectionParams(
    val minPeakProminenceDb: Double = 10.0,
    val relativeThreshold: Double = 0.22,
    val minSalience: Double = 20.0,
    val minFundamentalDb: Double = 6.0,
    val maxNotes: Int = 8,
) {
    companion object {
        /** @param sensitivity 0 (only clear notes) .. 1 (also faint ones) */
        fun forSensitivity(sensitivity: Double): DetectionParams {
            val s = sensitivity.coerceIn(0.0, 1.0)
            fun lerp(a: Double, b: Double) = a + (b - a) * s
            return DetectionParams(
                minPeakProminenceDb = lerp(14.0, 6.0),
                relativeThreshold = lerp(0.35, 0.10),
                minSalience = lerp(30.0, 10.0),
            )
        }
    }
}

/**
 * Finds the notes sounding in a frame from its spectral peaks (iterative estimation and cancellation).
 *
 * Every peak near an equal-tempered pitch proposes a note. A note's salience is the weighted sum of
 * the peaks found at its harmonics. The most salient note is taken, the part of its harmonics it
 * explains is removed (anything sticking out above the note's own spectral envelope is left, as that
 * may be another note), and the search repeats until the remaining candidates are too weak.
 */
class NoteDetector(
    val minMidi: Int = 28,    // E1
    val maxMidi: Int = 100,   // E7
    private val maxHarmonics: Int = 16,
    /** Harmonics counted in a note's salience; more are cancelled, so a bass note's high overtones don't become notes. */
    private val salienceHarmonics: Int = 8,
) {
    fun detect(peaks: List<Peak>, a4: Double, params: DetectionParams): List<DetectedNote> {
        if (peaks.isEmpty()) return emptyList()
        val sorted = peaks.sortedBy { it.freq }
        val freq = DoubleArray(sorted.size) { sorted[it].freq }
        val residual = DoubleArray(sorted.size) { sorted[it].prominence }
        // For each peak, the odd part of the harmonic number it was explained as (1 = an octave of a chosen
        // note, 3 = a fifth above one, 5+ = a third or seventh). A note on an overtone that adds a new pitch
        // class would change the chord's name, so it must stand out more clearly.
        val overtone = IntArray(sorted.size)

        val isCandidate = BooleanArray(maxMidi + 1)
        for (f in freq) {
            val m = Notes.midiOf(f, a4)
            val r = m.roundToInt()
            if (abs(m - r) <= 0.35 && r in minMidi..maxMidi) isCandidate[r] = true
        }

        val result = ArrayList<DetectedNote>()
        var first = 0.0
        val matched = IntArray(maxHarmonics)
        val bestMatched = IntArray(maxHarmonics)
        while (result.size < params.maxNotes) {
            var bestMidi = -1
            var bestSal = 0.0
            var bestCount = 0
            for (m in minMidi..maxMidi) {
                if (!isCandidate[m]) continue
                val count = match(m, a4, freq, residual, matched)
                if (count == 0) continue
                val needed = params.minFundamentalDb * if (overtone[matched[0]] > 3) 2 else 1
                if (residual[matched[0]] < needed) continue
                var sal = 0.0
                for (h in 0 until minOf(count, salienceHarmonics)) if (matched[h] >= 0) sal += residual[matched[h]] / sqrt(h + 1.0)
                if (sal > bestSal) {
                    bestSal = sal
                    bestMidi = m
                    bestCount = count
                    matched.copyInto(bestMatched, 0, 0, count)
                }
            }
            if (bestMidi < 0 || bestSal < params.minSalience) break
            if (result.isNotEmpty() && bestSal < params.relativeThreshold * first) break
            if (result.isEmpty()) first = bestSal
            result += DetectedNote(bestMidi, bestSal, freq[bestMatched[0]])
            isCandidate[bestMidi] = false
            cancel(bestMatched, bestCount, residual, overtone)
        }
        return result
    }

    /**
     * Fills [out] with the peak index matched to each harmonic of [midi] (-1 if none) and returns
     * the number of harmonics checked, or 0 when the fundamental itself has no peak.
     */
    private fun match(midi: Int, a4: Double, freq: DoubleArray, residual: DoubleArray, out: IntArray): Int {
        val f0 = Notes.frequency(midi, a4)
        val top = freq.last()
        var count = 0
        for (h in 1..maxHarmonics) {
            // Piano strings are slightly inharmonic: upper partials run sharp.
            val target = h * f0 * sqrt(1 + 1e-4 * h * h)
            if (target > top * 1.03) break
            val tolerance = 2.0.pow((35.0 + 4.0 * (h - 1)) / 1200.0)
            out[h - 1] = strongestIn(freq, residual, target / tolerance, target * tolerance)
            count = h
            if (h == 1 && out[0] < 0) return 0
        }
        return count
    }

    private fun strongestIn(freq: DoubleArray, residual: DoubleArray, lo: Double, hi: Double): Int {
        var i = lowerBound(freq, lo)
        var best = -1
        while (i < freq.size && freq[i] <= hi) {
            if (best < 0 || residual[i] > residual[best]) best = i
            i++
        }
        return best
    }

    private fun lowerBound(a: DoubleArray, x: Double): Int {
        var lo = 0
        var hi = a.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (a[mid] < x) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /**
     * Removes what the chosen note explains. Each harmonic keeps only what sticks out above the
     * note's own spectral envelope (the mean of the neighbouring harmonics), and at most 30 %
     * of its level: a harmonic much louder than its neighbours may be another note sounding there.
     */
    private fun cancel(matched: IntArray, count: Int, residual: DoubleArray, overtone: IntArray) {
        val level = DoubleArray(count) { if (matched[it] >= 0) residual[matched[it]] else -1.0 }
        for (h in 0 until count) {
            val j = matched[h]
            if (j < 0) continue
            var sum = 0.0
            var n = 0
            for (k in intArrayOf(h - 1, h + 1)) {
                if (k in 0 until count && level[k] >= 0) { sum += level[k]; n++ }
            }
            val envelope = if (n == 0) level[h] else sum / n
            residual[j] = maxOf(0.0, level[h] - maxOf(envelope, 0.7 * level[h]))
            if (h == 0) continue
            var odd = h + 1
            while (odd % 2 == 0) odd /= 2
            overtone[j] = maxOf(overtone[j], odd)
        }
    }
}
