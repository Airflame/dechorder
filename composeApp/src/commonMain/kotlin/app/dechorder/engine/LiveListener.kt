package app.dechorder.engine

import app.dechorder.platform.AudioInput
import app.dechorder.platform.Platform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile

/** Runs the audio input through a [ChordEngine] and publishes the result as a [StateFlow]. */
class LiveListener(
    private val platform: Platform,
    private val scope: CoroutineScope,
    private val config: () -> EngineConfig,
    private val deviceId: () -> String?,
) {
    private val _state = MutableStateFlow(LiveState())
    val state: StateFlow<LiveState> = _state.asStateFlow()

    private val engine = ChordEngine()
    private var job: Job? = null

    @Volatile private var input: AudioInput? = null

    val isRunning: Boolean get() = job?.isActive == true

    fun start() {
        if (isRunning) return
        _state.update { it.copy(starting = true, error = null) }
        job = scope.launch {
            if (!platform.requestMicrophonePermission()) {
                _state.update {
                    it.copy(starting = false, listening = false,
                        error = "Dechorder needs access to the microphone to hear the music.")
                }
                return@launch
            }
            try {
                withContext(platform.audioDispatcher) { run() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update {
                    it.copy(starting = false, listening = false,
                        error = e.message ?: e::class.simpleName ?: "The audio input failed.")
                }
            }
        }
    }

    private suspend fun run() {
        val opened = platform.openInput(deviceId())
        input = opened
        try {
            engine.begin(opened.sampleRate)
            _state.update {
                it.copy(listening = true, starting = false, error = null, inputName = opened.name,
                    sampleRate = opened.sampleRate)
            }
            val buffer = FloatArray(1024)
            val context = kotlin.coroutines.coroutineContext
            while (context.isActive) {
                val n = opened.read(buffer)
                if (n < 0) break
                engine.push(buffer, n, config()) { s ->
                    // After stop() the UI already shows "stopped"; don't overwrite it with a late frame.
                    if (context.isActive) _state.value = s.copy(inputName = opened.name)
                }
            }
        } finally {
            opened.stop()
            opened.release()
            input = null
        }
    }

    fun stop() {
        input?.stop()
        job?.cancel()
        job = null
        _state.update { it.copy(listening = false, starting = false, notes = emptyList(), reading = null, levelDb = -120f) }
    }

    fun toggle() = if (isRunning) stop() else start()
}
