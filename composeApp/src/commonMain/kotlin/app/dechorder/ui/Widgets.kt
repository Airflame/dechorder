package app.dechorder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A titled frame like Qt's QGroupBox. */
@Composable
fun GroupBox(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier) {
        Column(
            Modifier.padding(top = 8.dp)
                .border(1.dp, Palette.BorderStrong, RoundedCornerShape(4.dp))
                .padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
        Text(
            title,
            Modifier.padding(start = 8.dp).background(Palette.Window).padding(horizontal = 3.dp),
            color = Palette.Text,
            fontSize = 13.sp,
        )
    }
}

/** A rounded dark panel like Chordfinder's player display. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(10.dp)).background(Palette.Base)
            .border(1.dp, Palette.Border, RoundedCornerShape(10.dp)),
        content = content,
    )
}

/** A flat Qt-style push button. With [accent] it gets the green outline of a default button. */
@Composable
fun FlatButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Boolean = false,
    filled: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    padding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
) {
    val shape = RoundedCornerShape(4.dp)
    val bg = when {
        filled -> Palette.Accent
        else -> Palette.Raised
    }
    val border = when {
        !enabled -> Palette.Border
        accent || filled -> Palette.Accent
        else -> Palette.BorderStrong
    }
    val fg = when {
        !enabled -> Palette.TextFaint
        filled -> Color(0xFF0B2A17)
        else -> Palette.Text
    }
    Row(
        modifier.defaultMinSize(minHeight = 30.dp).clip(shape).background(bg).border(1.dp, border, shape)
            .clickable(enabled = enabled, onClick = onClick).padding(padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = fg, fontSize = 13.sp, fontWeight = if (filled) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
    }
}

@Composable
fun CheckRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(4.dp)).clickable { onChange(!checked) }.padding(end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onChange,
            modifier = Modifier.size(34.dp),
            colors = CheckboxDefaults.colors(
                checkedColor = Palette.Accent,
                uncheckedColor = Palette.TextDim,
                checkmarkColor = Color(0xFF0B2A17),
            ),
        )
        Text(text, color = Palette.Text, fontSize = 13.sp)
    }
}

/** A form row: label on the left (fixed width), content on the right, like QFormLayout. */
@Composable
fun FormRow(label: String, labelWidth: Dp = 104.dp, content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(labelWidth), color = Palette.Text, fontSize = 13.sp)
        content()
    }
}

@Composable
fun AccentSlider(
    value: Float,
    onChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
) {
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = range,
        steps = steps,
        modifier = modifier.height(28.dp),
        colors = SliderDefaults.colors(
            thumbColor = Palette.Accent,
            activeTrackColor = Palette.Accent,
            inactiveTrackColor = Palette.BorderStrong,
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
        ),
    )
}

/** A combo box: shows the selection, opens a menu of options. */
@Composable
fun <T> Dropdown(
    selected: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(4.dp)
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().defaultMinSize(minHeight = 30.dp).clip(shape).background(Palette.Raised)
                .border(1.dp, Palette.BorderStrong, shape).clickable(enabled = enabled) { open = true }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label(selected), Modifier.weight(1f), color = if (enabled) Palette.Text else Palette.TextFaint,
                fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("▾", color = Palette.TextDim, fontSize = 13.sp)
        }
        DropdownMenu(open, onDismissRequest = { open = false }, containerColor = Palette.Raised) {
            for (o in options) {
                DropdownMenuItem(
                    text = { Text(label(o), fontSize = 13.sp, color = if (o == selected) Palette.Accent else Palette.Text) },
                    onClick = { open = false; onSelect(o) },
                    modifier = Modifier.height(34.dp),
                )
            }
        }
    }
}

/** Qt-style tabs: a row of tab headers over a framed page. */
@Composable
fun TabBar(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        tabs.forEachIndexed { i, t ->
            val sel = i == selected
            val shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
            Box(
                Modifier.clip(shape).background(if (sel) Palette.Raised else Palette.Window)
                    .border(1.dp, if (sel) Palette.BorderStrong else Palette.Border, shape)
                    .clickable { onSelect(i) }.padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(t, color = if (sel) Palette.Text else Palette.TextDim, fontSize = 13.sp)
            }
        }
    }
}

/** A thin rounded level/progress bar in the accent colour. */
@Composable
fun LevelBar(fraction: Float, modifier: Modifier = Modifier, color: Color = Palette.Accent, marker: Float? = null) {
    Box(modifier.height(6.dp).clip(RoundedCornerShape(3.dp)).background(Palette.BorderStrong)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
        if (marker != null) {
            Box(Modifier.fillMaxWidth(marker.coerceIn(0f, 1f)).height(6.dp)) {
                Box(Modifier.align(Alignment.CenterEnd).width(2.dp).height(6.dp).background(Palette.Text))
            }
        }
    }
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, color = Palette.TextDim, fontSize = 12.sp, lineHeight = 16.sp)
}
