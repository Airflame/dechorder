package app.dechorder

import app.dechorder.platform.AudioInput
import app.dechorder.platform.InputDevice
import app.dechorder.platform.KeyValueStore
import app.dechorder.platform.Platform
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.util.prefs.Preferences
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.Mixer
import javax.sound.sampled.TargetDataLine

class DesktopPlatform : Platform {
    override val isMobile = false
    override val audioDispatcher: CoroutineDispatcher = Dispatchers.IO

    override val store = object : KeyValueStore {
        private val prefs = Preferences.userRoot().node("app/dechorder")
        override fun get(key: String): String? = prefs.get(key, null)
        override fun put(key: String, value: String) = prefs.put(key, value)
    }

    private fun lineInfo(rate: Float) = DataLine.Info(TargetDataLine::class.java, format(rate))
    private fun format(rate: Float) = AudioFormat(rate, 16, 1, true, false)

    /** Mixers that can record (microphones, line-in, "Stereo Mix" for the computer's own sound). */
    private fun captureMixers(): List<Mixer.Info> = AudioSystem.getMixerInfo().filter { info ->
        runCatching { AudioSystem.getMixer(info).isLineSupported(lineInfo(44100f)) }.getOrDefault(false)
    }

    override fun inputDevices(): List<InputDevice> =
        captureMixers().map { InputDevice(it.name, it.name) }.distinctBy { it.id }

    override fun openInput(deviceId: String?): AudioInput {
        val mixer = deviceId?.let { id -> captureMixers().firstOrNull { it.name == id } }
        var lastError: Exception? = null
        for (rate in listOf(44100f, 48000f)) {
            try {
                val info = lineInfo(rate)
                val line = (if (mixer != null) AudioSystem.getMixer(mixer).getLine(info) else AudioSystem.getLine(info))
                    as TargetDataLine
                line.open(format(rate), 4096 * 2)
                line.start()
                return DesktopInput(line, rate.toInt(), mixer?.name ?: "Default input")
            } catch (e: LineUnavailableException) {
                lastError = e
            } catch (e: IllegalArgumentException) {
                lastError = e
            }
        }
        throw IllegalStateException("Could not open the audio input. Check that a microphone is connected " +
            "and not in use by another app." + (lastError?.message?.let { " ($it)" } ?: ""))
    }

    override suspend fun requestMicrophonePermission() = true
    override fun keepScreenOn(on: Boolean) {}
}

private class DesktopInput(private val line: TargetDataLine, override val sampleRate: Int, override val name: String) : AudioInput {
    @Volatile private var stopped = false
    private var bytes = ByteArray(0)

    override fun read(buffer: FloatArray): Int {
        if (stopped) return -1
        if (bytes.size != buffer.size * 2) bytes = ByteArray(buffer.size * 2)
        val n = line.read(bytes, 0, bytes.size)
        if (stopped || n <= 0) return if (stopped) -1 else 0
        val samples = n / 2
        for (i in 0 until samples) {
            val v = (bytes[2 * i].toInt() and 0xFF) or (bytes[2 * i + 1].toInt() shl 8)
            buffer[i] = v / 32768f
        }
        return samples
    }

    override fun stop() {
        if (stopped) return
        stopped = true
        line.stop()
        line.flush()
    }

    override fun release() = line.close()
}
