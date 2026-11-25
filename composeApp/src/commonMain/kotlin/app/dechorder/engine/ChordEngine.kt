package app.dechorder.engine

import app.dechorder.dsp.DetectionParams
import app.dechorder.dsp.NoteDetector
import app.dechorder.dsp.Spectrum
import app.dechorder.music.ChordNamer
import app.dechorder.music.Notes
import app.dechorder.music.Reading
import kotlin.math.max
import kotlin.math.roundToInt

/** Analysis options, read afresh for every frame so setting changes apply immediately. */
data class EngineConfig(
    val a4: Double = 440.0,
    val sensitivity: Double = 0.5,
    val noiseGateDb: Double = -60.0,
    val changeDelayMs: Int = 150,
    /** At most this many notes are reported (the strongest ones); null for no limit. */
    val maxNotes: Int? = null,
)

/** A note currently sounding, with its relative strength 0..1. */
data class ActiveNote(val midi: Int, val strength: Float)

/** Everything the UI shows, published after every analysis frame. */
data class LiveState(
    val listening: Boolean = false,
    val starting: Boolean = false,
    val error: String? = null,
    val inputName: String = "",
    val levelDb: Float = -120f,
    /** Notes sounding now, low to high. */
    val notes: List<ActiveNote> = emptyList(),
    /** The settled reading: the chord (or note/interval) on display. Null when nothing is sounding. */
    val reading: Reading? = null,
)

/**
 * The live analysis pipeline. Feed it audio with [push]; it analyses an overlapping window every
 * [HOP] samples: spectral peaks → notes (smoothed over a few frames) → chord name → the chord on
 * display, which only changes once a new one has been heard for the chosen delay.
 *
 * Use one engine per input stream. Not thread-safe: call from one thread at a time.
 */
class ChordEngine(private val sampleRate: Int) {
    companion object {
        const val WINDOW = 8192
        const val HOP = 2048
        private const val MIN_HZ = 38.0
        private const val MAX_HZ = 6000.0
        private const val DYNAMIC_RANGE_DB = 50.0
        private const val SILENCE_SECONDS = 0.4
        private const val SUSPECT_WEIGHT = 0.7
        /** Semitones from a note to its 3rd, 5th, 6th, 7th, 9th and 10th harmonics (those adding a new pitch class or a fifth). */
        private val OVERTONE_OFFSETS = setOf(19, 28, 31, 34, 38, 40)

        private const val MIDI_NOTES = 128
        /** Level reported before any sound has been heard (dBFS). */
        private const val SILENT_DB = -120.0
        /** A frame count that means "long ago", for state that has never happened yet. */
        private const val LONG_AGO = 1000

        /** Spectral peaks this far below the noise gate are still used, so quiet chord tones aren't lost (dB). */
        private const val PEAK_FLOOR_BELOW_GATE_DB = 25.0

        // Attack detection.
        /** Frames of level history an attack is measured against. */
        private const val ONSET_HISTORY_FRAMES = 4
        /** Rise above the quietest recent frame that counts as a new attack (dB). */
        private const val ONSET_JUMP_DB = 4.0
        /** A note returning after this many frames of silence counts as a new attack. */
        private const val REATTACK_GAP_FRAMES = 10

        // Note tracking: each frame, evidence = evidence * EVIDENCE_DECAY + (1 if detected).
        private const val EVIDENCE_DECAY = 0.5
        /** Evidence needed to turn a note on: two detections in a row reach 1.5. */
        private const val NOTE_ON_EVIDENCE = 1.4
        /** A note turns off once its evidence falls below this: three frames after its last detection, for a held note. */
        private const val NOTE_OFF_EVIDENCE = 0.3
        /** Share of an active note's previous salience kept when it is detected again. */
        private const val SALIENCE_SMOOTHING = 0.6
        /** Factor applied to a note's salience in each frame it isn't detected. */
        private const val SALIENCE_DECAY = 0.7

        /** Extra frames after an attack during which fewer of the same notes may still be a new chord. */
        private const val FADE_GRACE_FRAMES = 2
    }

    private val spectrum = Spectrum(sampleRate, WINDOW)
    private val detector = NoteDetector()
    private val frame = FloatArray(WINDOW)
    private var filled = 0
    private var sinceHop = 0

    // Note tracking: a leaky count of recent detections per MIDI note, with hysteresis.
    private val evidence = DoubleArray(MIDI_NOTES)
    private val salience = DoubleArray(MIDI_NOTES)
    private val active = BooleanArray(MIDI_NOTES)
    private val lastActiveFrame = IntArray(MIDI_NOTES) { -LONG_AGO }
    private var frameIndex = 0

    // Attack detection and settling.
    private val recentLevels = DoubleArray(ONSET_HISTORY_FRAMES) { SILENT_DB }
    private var framesSinceOnset = LONG_AGO
    private var settled: Reading? = null
    private var pending: Reading? = null
    private var pendingFrames = 0
    private var silentFrames = 0

    /** Adds [count] samples (-1..1). [onFrame] is called with the new state after every analysed frame. */
    fun push(samples: FloatArray, count: Int, config: EngineConfig, onFrame: (LiveState) -> Unit) {
        var i = 0
        while (i < count) {
            val n = minOf(count - i, HOP - sinceHop)
            // Slide the window left and append.
            frame.copyInto(frame, 0, n, WINDOW)
            samples.copyInto(frame, WINDOW - n, i, i + n)
            filled = minOf(WINDOW, filled + n)
            sinceHop += n
            i += n
            if (sinceHop >= HOP) {
                sinceHop = 0
                onFrame(analyze(config))
            }
        }
    }

    private fun analyze(config: EngineConfig): LiveState {
        val levelDb = Spectrum.rmsDb(frame, WINDOW - HOP, WINDOW)
        val frameDb = Spectrum.rmsDb(frame, WINDOW - WINDOW / 2, WINDOW)
        val params = DetectionParams.forSensitivity(config.sensitivity).let { p ->
            config.maxNotes?.let { p.copy(maxNotes = minOf(p.maxNotes, it.coerceAtLeast(1))) } ?: p
        }

        val detected = if (filled >= WINDOW / 2 && frameDb >= config.noiseGateDb) {
            spectrum.analyze(frame)
            val minDb = config.noiseGateDb - PEAK_FLOOR_BELOW_GATE_DB
            val peaks = spectrum.peaks(MIN_HZ, MAX_HZ, params.minPeakProminenceDb, DYNAMIC_RANGE_DB, minDb)
            detector.detect(peaks, config.a4, params)
        } else {
            emptyList()
        }

        // An attack: the level jumps above the recent minimum.
        var onset = levelDb - recentLevels.min() >= ONSET_JUMP_DB && levelDb >= config.noiseGateDb
        recentLevels.copyInto(recentLevels, 0, 1, recentLevels.size)
        recentLevels[recentLevels.size - 1] = levelDb
        frameIndex++

        // Track notes: a note turns on after two detections in a row and off a few frames after the last.
        val hit = BooleanArray(MIDI_NOTES)
        for (d in detected) {
            hit[d.midi] = true
            salience[d.midi] = if (active[d.midi]) {
                SALIENCE_SMOOTHING * salience[d.midi] + (1 - SALIENCE_SMOOTHING) * d.salience
            } else {
                d.salience
            }
        }
        for (m in 0 until MIDI_NOTES) {
            evidence[m] = evidence[m] * EVIDENCE_DECAY + if (hit[m]) 1.0 else 0.0
            if (!active[m] && evidence[m] >= NOTE_ON_EVIDENCE) {
                active[m] = true
                // A note that wasn't heard for a while is a new attack too (a quiet note added to a chord).
                if (frameIndex - lastActiveFrame[m] > REATTACK_GAP_FRAMES) onset = true
            } else if (active[m] && evidence[m] < NOTE_OFF_EVIDENCE) {
                active[m] = false
            }
            if (active[m]) lastActiveFrame[m] = frameIndex
            if (!hit[m]) salience[m] *= SALIENCE_DECAY
        }
        if (onset) framesSinceOnset = 0 else framesSinceOnset++

        var notes = (0 until MIDI_NOTES).filter { active[it] }
        // Notes still fading from an earlier frame can add up to more than the limit; keep the strongest.
        config.maxNotes?.let { limit ->
            if (notes.size > limit) notes = notes.sortedByDescending { salience[it] }.take(limit.coerceAtLeast(1)).sorted()
        }
        val maxSal = notes.maxOfOrNull { salience[it] }?.takeIf { it > 0 } ?: 1.0
        val weights = notes.map { (salience[it] / maxSal).coerceIn(0.0, 1.0) }
        settle(name(notes, weights), config)

        return LiveState(
            listening = true,
            levelDb = levelDb.toFloat(),
            notes = notes.mapIndexed { idx, m -> ActiveNote(m, weights[idx].toFloat()) },
            reading = settled,
        )
    }

    /**
     * Names the notes. A fairly quiet note exactly where a lower note has a strong overtone (an octave and
     * a fifth, two octaves and a third, …) may be that overtone; if the other notes make a full chord
     * on their own, the chord is named without it (otherwise E with B2 in the bass reads as Eadd9 while
     * B2's third harmonic, F#4, rings during the attack).
     */
    private fun name(notes: List<Int>, weights: List<Double>): Reading? {
        val full = ChordNamer.read(notes, weights)
        val suspect = notes.indices.filter { i ->
            weights[i] < SUSPECT_WEIGHT && notes.any { low -> low < notes[i] && (notes[i] - low) in OVERTONE_OFFSETS }
        }
        if (suspect.isEmpty()) return full
        val keep = notes.indices - suspect.toSet()
        val reduced = ChordNamer.read(keep.map { notes[it] }, keep.map { weights[it] })
        return if (reduced is Reading.ChordReading && reduced.chord.pitchClasses.size >= 3) reduced else full
    }

    private fun framesFor(ms: Int): Int = max(1, (ms / 1000.0 * sampleRate / HOP).roundToInt())

    /**
     * What a reading shows, ignoring octaves and voicing: two readings with equal identities are the same
     * thing on screen. A chord is its root, quality and bass; a note or interval is its pitch classes.
     */
    private fun identity(r: Reading): Any = when (r) {
        is Reading.ChordReading -> r.chord
        else -> r.notes.map { Notes.pc(it) }.toSet()
    }

    private fun pitchClasses(r: Reading): Set<Int> = when (r) {
        is Reading.ChordReading -> r.chord.pitchClasses + r.chord.bass
        else -> r.notes.map { Notes.pc(it) }.toSet()
    }

    /** Decides whether the frame's reading replaces the one on display. */
    private fun settle(reading: Reading?, config: EngineConfig) {
        val current = settled
        if (reading == null) {
            pending = null
            pendingFrames = 0
            if (current != null && ++silentFrames * HOP.toDouble() / sampleRate >= SILENCE_SECONDS) settled = null
            return
        }
        silentFrames = 0
        val id = identity(reading)
        if (current != null && id == identity(current)) {
            settled = reading   // same chord; refresh the notes
            pending = null
            pendingFrames = 0
            return
        }
        pendingFrames = if (pending?.let(::identity) == id) pendingFrames + 1 else 1
        pending = reading
        // Notes of a chord die away at different speeds. Without a new attack, a reading made of
        // fewer of the same notes is the chord fading, not a new chord.
        val fading = current != null && framesSinceOnset > framesFor(config.changeDelayMs) + FADE_GRACE_FRAMES &&
            pitchClasses(current).containsAll(pitchClasses(reading))
        if (!fading && pendingFrames >= framesFor(config.changeDelayMs)) {
            settled = reading
            pending = null
            pendingFrames = 0
        }
    }
}
