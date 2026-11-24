package app.dechorder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dechorder.engine.LiveState
import app.dechorder.platform.Platform
import kotlin.math.roundToInt

/** The settings: input, tuning, detection and display options. Changes are saved and apply at once. */
@Composable
internal fun SettingsPanel(platform: Platform, settings: AppSettings, state: LiveState, modifier: Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val devices = remember { platform.inputDevices() }
        if (devices.isNotEmpty()) {
            Text("Input", color = Palette.Text, fontSize = 13.sp)
            val options = listOf("") + devices.map { it.id }
            Dropdown(
                selected = settings.inputDevice.takeIf { it in options } ?: "",
                options = options,
                label = { id -> if (id.isEmpty()) "System default" else devices.first { it.id == id }.name },
                onSelect = { settings.inputDevice = it },
                modifier = Modifier.fillMaxWidth(),
            )
            Hint("Takes effect the next time you press Listen.")
        }

        Text("Tuning: A4 = ${settings.a4} Hz", color = Palette.Text, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FlatButton("−", { settings.a4 = (settings.a4 - 1).coerceAtLeast(415) })
            FlatButton("+", { settings.a4 = (settings.a4 + 1).coerceAtMost(466) })
            FlatButton("440", { settings.a4 = 440 })
        }

        Text("Sensitivity: ${(settings.sensitivity * 100).roundToInt()}%", color = Palette.Text, fontSize = 13.sp)
        AccentSlider(settings.sensitivity, { settings.sensitivity = it }, 0f..1f, Modifier.fillMaxWidth())
        Hint("Higher finds quieter notes but may add notes that aren't there.")

        Text("Ignore sounds below ${settings.noiseGateDb.roundToInt()} dB", color = Palette.Text, fontSize = 13.sp)
        AccentSlider(settings.noiseGateDb, { settings.noiseGateDb = it.roundToInt().toFloat() }, -80f..-20f,
            Modifier.fillMaxWidth())
        LevelBar(levelFraction(state.levelDb), Modifier.fillMaxWidth(), marker = levelFraction(settings.noiseGateDb))
        Hint("The bar shows the input level now; the white mark is the threshold. Raise it in a noisy room.")

        Text("Chord change delay: ${settings.changeDelayMs} ms", color = Palette.Text, fontSize = 13.sp)
        AccentSlider(settings.changeDelayMs.toFloat(), { settings.changeDelayMs = (it / 10).roundToInt() * 10 },
            50f..500f, Modifier.fillMaxWidth())
        Hint("How long a new chord must sound before it is shown. Longer is steadier, shorter reacts faster.")

        Text("Keyboard", color = Palette.Text, fontSize = 13.sp)
        Dropdown(settings.keyboardRange, KeyboardRange.entries.toList(), { it.label },
            { settings.keyboardRange = it }, Modifier.fillMaxWidth())
        CheckRow("Show note names on lit keys", settings.showNoteNames, { settings.showNoteNames = it })
        if (platform.isMobile) {
            CheckRow("Keep the screen on while listening", settings.keepScreenOn, { settings.keepScreenOn = it })
        }
        Hint("Dechorder works entirely offline: the sound is analysed on this device and never sent anywhere.")
    }
}
