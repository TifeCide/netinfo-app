package cn.aeolusdev.netinfo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Displays Wi-Fi signal strength as 4 ascending bars based on RSSI.
 * RSSI levels: >= -55 excellent (4 bars), -55..-70 good (3 bars),
 *              -70..-80 fair (2 bars), -80..-90 weak (1 bar), < -90 no signal (0 bars)
 */
@Composable
fun SignalStrengthBar(
    rssi: Int,
    modifier: Modifier = Modifier,
    barCount: Int = 4
) {
    val activeBars = when {
        rssi >= -55 -> 4
        rssi >= -70 -> 3
        rssi >= -80 -> 2
        rssi >= -90 -> 1
        else -> 0
    }

    val activeColor = when {
        rssi >= -70 -> MaterialTheme.colorScheme.primary
        rssi >= -80 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    val inactiveColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier.size(20.dp, 18.dp)) {
        val spacing = size.width * 0.1f
        val barWidth = (size.width - spacing * (barCount - 1)) / barCount

        for (i in 0 until barCount) {
            val barHeightFraction = (i + 1).toFloat() / barCount
            val barHeight = size.height * barHeightFraction
            val x = i * (barWidth + spacing)
            val y = size.height - barHeight
            val color: Color = if (i < activeBars) activeColor else inactiveColor
            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}

fun rssiToDescription(rssi: Int): String = when {
    rssi >= -55 -> "极佳 ($rssi dBm)"
    rssi >= -70 -> "良好 ($rssi dBm)"
    rssi >= -80 -> "一般 ($rssi dBm)"
    rssi >= -90 -> "较弱 ($rssi dBm)"
    else -> "极差 ($rssi dBm)"
}
