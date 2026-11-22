package app.dechorder.platform

import kotlinx.coroutines.CoroutineDispatcher

/** A stream of mono audio samples from a microphone or other input. */
interface AudioInput {
    val sampleRate: Int
    val name: String

    /** Blocks until samples are available and fills [buffer] with values in -1..1. Returns the count, or -1 once stopped. */
    fun read(buffer: FloatArray): Int

    /** Stops the stream; a blocked [read] returns. Safe to call from any thread, more than once. */
    fun stop()

    /** Frees the device. Called by the reading thread after its last [read]. */
    fun release()
}

data class InputDevice(val id: String, val name: String)

/** Small persistent key-value storage for settings. */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

/** What the shared app needs from Android or the desktop. */
interface Platform {
    /** A phone or tablet (touch, small screen) rather than a desktop computer. */
    val isMobile: Boolean
    /** Dispatcher for blocking audio reads. */
    val audioDispatcher: CoroutineDispatcher
    val store: KeyValueStore

    /** Inputs the user can choose between; empty if the platform only offers the default one. */
    fun inputDevices(): List<InputDevice>

    /** Opens an input (null = the default one). Throws with a readable message on failure. */
    fun openInput(deviceId: String?): AudioInput

    /** Asks for microphone access if needed; true when it is granted. */
    suspend fun requestMicrophonePermission(): Boolean

    fun keepScreenOn(on: Boolean)
}
