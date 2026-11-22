package app.dechorder

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.dechorder.ui.AppIcon
import app.dechorder.ui.DechorderApp
import java.awt.Dimension

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Dechorder",
        icon = rememberVectorPainter(AppIcon),
        state = rememberWindowState(width = 1200.dp, height = 640.dp),
    ) {
        window.minimumSize = Dimension(420, 520)
        DechorderApp(remember { DesktopPlatform() })
    }
}
