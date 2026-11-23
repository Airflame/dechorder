package app.dechorder.ui

import androidx.compose.runtime.mutableStateOf
import app.dechorder.engine.EngineConfig
import app.dechorder.music.Notes
import app.dechorder.platform.KeyValueStore
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Keyboard sizes. AUTO picks one to suit the window width. */
enum class KeyboardRange(val label: String, val low: Int, val high: Int) {
    AUTO("Fit to screen", 0, 0),
    FULL("88 keys (A0–C8)", Notes.PIANO_LOWEST, Notes.PIANO_HIGHEST),
    KEYS_76("76 keys (E1–G7)", 28, 103),
    KEYS_61("61 keys (C2–C7)", 36, 96),
    KEYS_49("49 keys (C2–C6)", 36, 84),
    KEYS_37("37 keys (C3–C6)", 48, 84),
}

/** User settings, observable by Compose and saved as they change. */
class AppSettings(private val store: KeyValueStore) {
    var a4 by pref("a4", 440, String::toIntOrNull)
    var sensitivity by pref("sensitivity", 0.5f, String::toFloatOrNull)
    var noiseGateDb by pref("noiseGateDb", -60f, String::toFloatOrNull)
    var changeDelayMs by pref("changeDelayMs", 150, String::toIntOrNull)
    var keyboardRange by pref("keyboardRange", KeyboardRange.AUTO) { v -> KeyboardRange.entries.firstOrNull { it.name == v } }
    var showNoteNames by pref("showNoteNames", true, String::toBooleanStrictOrNull)
    /** Input device id; empty = the system default. */
    var inputDevice by pref("inputDevice", "") { it }
    var keepScreenOn by pref("keepScreenOn", true, String::toBooleanStrictOrNull)

    fun engineConfig() = EngineConfig(
        a4 = a4.toDouble(),
        sensitivity = sensitivity.toDouble(),
        noiseGateDb = noiseGateDb.toDouble(),
        changeDelayMs = changeDelayMs,
    )

    private fun <T> pref(key: String, default: T, parse: (String) -> T?): ReadWriteProperty<Any?, T> =
        object : ReadWriteProperty<Any?, T> {
            private val state = mutableStateOf(store.get(key)?.let(parse) ?: default)
            override fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
                if (state.value == value) return
                state.value = value
                store.put(key, value.toString())
            }
        }
}
