package app.dechorder.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dechorder.engine.LiveListener
import app.dechorder.engine.LiveState
import app.dechorder.platform.Platform

/** The whole app, shared by Android and the desktop. */
@Composable
fun DechorderApp(platform: Platform) {
    val settings = remember { AppSettings(platform.store) }
    val scope = rememberCoroutineScope()
    val listener = remember {
        LiveListener(platform, scope, config = { settings.engineConfig() },
            deviceId = { settings.inputDevice.ifEmpty { null } })
    }
    val state by listener.state.collectAsState()
    DisposableEffect(Unit) { onDispose { listener.stop() } }
    LaunchedEffect(state.listening, settings.keepScreenOn) {
        platform.keepScreenOn(state.listening && settings.keepScreenOn)
    }

    DechorderTheme {
        Box(Modifier.fillMaxSize().background(Palette.Window).safeDrawingPadding()) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(10.dp)) {
                val wide = maxWidth >= 860.dp || (maxWidth > maxHeight && maxWidth >= 640.dp)
                var showSettings by remember { mutableStateOf(false) }
                val range = settings.keyboardRange.let {
                    if (it != KeyboardRange.AUTO) it
                    else if (maxWidth >= 1000.dp) KeyboardRange.FULL
                    else if (maxWidth >= 600.dp) KeyboardRange.KEYS_61
                    else KeyboardRange.KEYS_49
                }
                if (wide) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Main(state, settings, range, compact = false, twoRows = false, onListen = listener::toggle,
                            settingsButton = null, modifier = Modifier.weight(1f).fillMaxHeight())
                        GroupBox("Settings", Modifier.width(340.dp).fillMaxHeight()) {
                            SettingsPanel(platform, settings, state, Modifier.fillMaxSize())
                        }
                    }
                } else if (showSettings) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FlatButton("‹ Back", { showSettings = false })
                            Spacer(Modifier.width(12.dp))
                            Text("Settings", color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        SettingsPanel(platform, settings, state, Modifier.fillMaxSize())
                    }
                } else {
                    // Portrait phone: the keyboard in two rows, so the keys are twice as wide.
                    Main(state, settings, range, compact = true, twoRows = maxHeight > maxWidth, onListen = listener::toggle,
                        settingsButton = { showSettings = true }, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun Main(
    state: LiveState,
    settings: AppSettings,
    range: KeyboardRange,
    compact: Boolean,
    twoRows: Boolean,
    onListen: () -> Unit,
    settingsButton: (() -> Unit)?,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Header(state, onListen, settingsButton)
        val rows = (if (twoRows) splitKeyboard(range.low, range.high) else null)
            ?.let { (lower, upper) -> listOf(upper, lower) }   // treble above bass
            ?: listOf(range.low..range.high)
        val slots = rows.maxOf { whiteCount(it) }
        val split = rows.size == 2
        // One keyboard row: the card stops at 260 dp and the keyboard sits right under it, rather than
        // leaving a tall empty card. Two rows (portrait phone): the card and the keyboard share the
        // screen half-and-half.
        GroupBox("Chord", Modifier.fillMaxWidth().weight(1f, fill = split)) {
            val card = if (split) Modifier.weight(1f)
            else Modifier.weight(1f, fill = false).heightIn(min = 120.dp, max = 260.dp)
            ChordCard(state, Modifier.fillMaxWidth().then(card))
            BoxWithConstraints(Modifier.fillMaxWidth().then(if (split) Modifier.weight(1f) else Modifier)) {
                // One row: keys about six times as long as they are wide, like a real piano.
                val singleHeight = (maxWidth / slots * 6).coerceIn(90.dp, if (compact) 150.dp else 200.dp)
                Column(if (split) Modifier.fillMaxSize() else Modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val rowHeight = if (split) Modifier.weight(1f) else Modifier.height(singleHeight)
                    for (row in rows) {
                        // A shorter row is extended to the same length (the bass row then also shows the
                        // first key or two of the treble row), so both rows end in line.
                        val shown = extendKeyboard(row, slots)
                        PianoKeyboard(
                            shown.first, shown.last, state.notes, settings.showNoteNames,
                            Modifier.fillMaxWidth().then(rowHeight),
                            slots = slots, fullLow = range.low, fullHigh = range.high,
                        )
                    }
                }
            }
            Text(
                "${range.label}  ·  A4 = ${settings.a4} Hz",
                color = Palette.TextFaint, fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun Header(state: LiveState, onListen: () -> Unit, settingsButton: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(AppIcon, contentDescription = null, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(8.dp))
        Text("Dechorder", color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            val status = when {
                state.error != null -> "Stopped"
                state.starting -> "Starting…"
                state.listening -> "Listening" + if (state.inputName.isNotEmpty()) " · ${state.inputName}" else ""
                else -> "Idle"
            }
            Text(status, color = Palette.TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            LevelBar(levelFraction(state.levelDb), Modifier.fillMaxWidth())
        }
        Spacer(Modifier.width(12.dp))
        if (settingsButton != null) {
            FlatButton("Settings", settingsButton)
            Spacer(Modifier.width(8.dp))
        }
        FlatButton(
            if (state.listening || state.starting) "Stop" else "Listen",
            onListen,
            accent = true,
            filled = state.listening,
            leading = {
                Box(Modifier.size(8.dp).clip(CircleShape)
                    .background(if (state.listening) Palette.Playhead else Palette.Accent))
            },
        )
    }
}
