package app.dechorder

import app.dechorder.music.Notes
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Piano-like test tones: inharmonic partials with a falling spectrum and a slow decay. */
object Synth {
    const val RATE = 44100

    fun notes(
        midis: List<Int>,
        seconds: Double,
        a4: Double = 440.0,
        amplitude: Double = 0.12,
        noiseDb: Double = -70.0,
        seed: Int = 1,
    ): FloatArray {
        val n = (seconds * RATE).toInt()
        val out = FloatArray(n)
        val rnd = Random(seed)
        for (midi in midis) {
            val f0 = Notes.frequency(midi, a4)
            // Bass strings have stronger upper partials than the fundamental, like a real piano.
            val b = 1.5e-4
            for (h in 1..12) {
                val f = h * f0 * sqrt(1 + b * h * h)
                if (f > RATE / 2 * 0.9) break
                val amp = amplitude * (if (midi < 45 && h == 1) 0.5 else 1.0) / h.toDouble().pow(1.1)
                val phase = rnd.nextDouble() * 2 * PI
                val decay = 0.6 + 0.25 * h
                for (i in 0 until n) {
                    val t = i.toDouble() / RATE
                    out[i] += (amp * exp(-decay * t) * sin(2 * PI * f * t + phase)).toFloat()
                }
            }
        }
        val noise = 10.0.pow(noiseDb / 20) * sqrt(3.0)
        for (i in 0 until n) out[i] += ((rnd.nextDouble() * 2 - 1) * noise).toFloat()
        return out
    }

    fun silence(seconds: Double, noiseDb: Double = -80.0): FloatArray =
        notes(emptyList(), seconds, noiseDb = noiseDb)

    fun midi(name: String): Int {
        val letter = name.takeWhile { !it.isDigit() && it != '-' }
        val octave = name.drop(letter.length).toInt()
        val pc = (Notes.SHARP_NAMES.indexOf(letter).takeIf { it >= 0 } ?: Notes.FLAT_NAMES.indexOf(letter))
        require(pc >= 0) { "bad note $name" }
        return (octave + 1) * 12 + pc
    }

    fun midis(vararg names: String) = names.map { midi(it) }
}
