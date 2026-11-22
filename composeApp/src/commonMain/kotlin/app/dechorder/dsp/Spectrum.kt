package app.dechorder.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** A spectral peak: interpolated frequency, level in dBFS and how far it stands above the local noise floor. */
data class Peak(val freq: Double, val db: Double, val prominence: Double)

/**
 * Turns a frame of audio into spectral peaks: Hann window, zero-padded FFT, a robust local noise floor,
 * and parabolic interpolation of each peak.
 */
class Spectrum(val sampleRate: Int, val windowSize: Int, val fftSize: Int = windowSize * 2) {
    private val fft = Fft(fftSize)
    private val window = DoubleArray(windowSize) { 0.5 - 0.5 * cos(2 * PI * it / (windowSize - 1)) }
    private val re = DoubleArray(fftSize)
    private val im = DoubleArray(fftSize)
    val bins = fftSize / 2 + 1
    val binHz = sampleRate.toDouble() / fftSize

    /** Level of each bin in dBFS (a full-scale sine reads about 0 dB). */
    val db = DoubleArray(bins)
    private val floor = DoubleArray(bins)
    private val prefix = DoubleArray(bins + 1)
    private val clipped = DoubleArray(bins)

    /** Computes [db] for a frame of [windowSize] samples in -1..1. */
    fun analyze(frame: FloatArray, offset: Int = 0) {
        for (i in 0 until windowSize) {
            re[i] = frame[offset + i] * window[i]
            im[i] = 0.0
        }
        for (i in windowSize until fftSize) {
            re[i] = 0.0
            im[i] = 0.0
        }
        fft.transform(re, im)
        // A Hann-windowed sine of amplitude A peaks at A * N / 4.
        val scale = 4.0 / windowSize
        for (k in 0 until bins) {
            val m = sqrt(re[k] * re[k] + im[k] * im[k]) * scale
            db[k] = 20 * log10(m + 1e-9)
        }
    }

    /** Local noise floor: a windowed mean of the spectrum with the peaks clipped off (two passes). */
    private fun computeFloor(lowBin: Int, highBin: Int) {
        fun halfWidth(k: Int): Int = max(24, (k * 0.2).toInt())  // about ±1/3 octave, at least ±65 Hz
        fun windowMean(src: DoubleArray, dst: DoubleArray) {
            prefix[0] = 0.0
            for (k in 0 until bins) prefix[k + 1] = prefix[k] + src[k]
            for (k in lowBin..highBin) {
                val h = halfWidth(k)
                val a = max(0, k - h)
                val b = min(bins - 1, k + h)
                dst[k] = (prefix[b + 1] - prefix[a]) / (b - a + 1)
            }
        }
        windowMean(db, floor)
        for (k in 0 until bins) clipped[k] = if (k in lowBin..highBin) min(db[k], floor[k]) else db[k]
        windowMean(clipped, floor)
    }

    /**
     * Peaks between [minHz] and [maxHz] that stand at least [minProminenceDb] above the local floor and
     * within [dynamicRangeDb] of the loudest peak. Call after [analyze].
     */
    fun peaks(minHz: Double, maxHz: Double, minProminenceDb: Double, dynamicRangeDb: Double, minDb: Double): List<Peak> {
        val lo = max(3, (minHz / binHz).toInt())
        val hi = min(bins - 4, (maxHz / binHz).toInt() + 1)
        computeFloor(lo, hi)
        val candidates = ArrayList<Peak>()
        var k = lo
        while (k <= hi) {
            val v = db[k]
            if (v > db[k - 1] && v >= db[k + 1] && v >= db[k - 2] && v >= db[k + 2] && v >= db[k - 3] && v >= db[k + 3]) {
                val a = db[k - 1]
                val c = db[k + 1]
                val denom = a - 2 * v + c
                val p = if (denom < 0) (0.5 * (a - c) / denom).coerceIn(-0.5, 0.5) else 0.0
                val peakDb = v - 0.25 * (a - c) * p
                val prom = peakDb - floor[k]
                if (prom >= minProminenceDb && peakDb >= minDb) {
                    candidates += Peak((k + p) * binHz, peakDb, prom)
                }
                k += 2
            } else {
                k++
            }
        }
        if (candidates.isEmpty()) return candidates
        val top = candidates.maxOf { it.db }
        return candidates.filter { it.db >= top - dynamicRangeDb }
    }

    companion object {
        /** Root-mean-square level of samples in dBFS. */
        fun rmsDb(samples: FloatArray, from: Int = 0, to: Int = samples.size): Double {
            if (to <= from) return -120.0
            var sum = 0.0
            for (i in from until to) sum += samples[i].toDouble().pow(2)
            return 10 * log10(sum / (to - from) + 1e-12)
        }
    }
}
