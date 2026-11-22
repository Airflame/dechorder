package app.dechorder

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import app.dechorder.platform.AudioInput
import app.dechorder.platform.InputDevice
import app.dechorder.platform.KeyValueStore
import app.dechorder.platform.Platform
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class AndroidPlatform(private val activity: MainActivity) : Platform {
    override val isMobile = true
    override val audioDispatcher: CoroutineDispatcher = Dispatchers.IO

    override val store = object : KeyValueStore {
        private val prefs = activity.getSharedPreferences("dechorder", Context.MODE_PRIVATE)
        override fun get(key: String): String? = prefs.getString(key, null)
        override fun put(key: String, value: String) = prefs.edit().putString(key, value).apply()
    }

    override fun inputDevices(): List<InputDevice> = emptyList()

    override fun openInput(deviceId: String?): AudioInput {
        // UNPROCESSED (or VOICE_RECOGNITION) skips the automatic gain and noise suppression meant for calls,
        // which would otherwise squash the music.
        val audioManager = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val unprocessed = audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        val source = if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION
        return AndroidInput(source)
    }

    override suspend fun requestMicrophonePermission(): Boolean = activity.requestMicrophone()

    override fun keepScreenOn(on: Boolean) = activity.keepScreenOn(on)
}

private class AndroidInput(source: Int) : AudioInput {
    override val sampleRate = 44100
    override val name = "Microphone"
    @Volatile private var stopped = false

    @SuppressLint("MissingPermission") // granted before the input is opened
    private val record: AudioRecord = run {
        val min = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        if (min <= 0) throw IllegalStateException("This device can't record audio at $sampleRate Hz.")
        val r = AudioRecord(source, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT,
            maxOf(min, sampleRate / 5 * 4))
        if (r.state != AudioRecord.STATE_INITIALIZED) {
            r.release()
            throw IllegalStateException("The microphone is not available. Another app may be using it.")
        }
        r.startRecording()
        r
    }

    override fun read(buffer: FloatArray): Int {
        if (stopped) return -1
        val n = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
        return if (stopped) -1 else if (n < 0) throw IllegalStateException("Reading the microphone failed ($n).") else n
    }

    override fun stop() {
        if (stopped) return
        stopped = true
        runCatching { record.stop() }
    }

    override fun release() = record.release()
}
