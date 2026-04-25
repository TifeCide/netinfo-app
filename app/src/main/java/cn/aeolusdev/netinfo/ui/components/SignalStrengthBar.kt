package cn.aeolusdev.netinfo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SignalWifi0Bar
import androidx.compose.material.icons.filled.SignalWifi1Bar
import androidx.compose.material.icons.filled.SignalWifi2Bar
import androidx.compose.material.icons.filled.SignalWifi3Bar
import androidx.compose.material.icons.filled.SignalWifi4Bar

/**
 * Displays a Wi-Fi signal strength icon based on RSSI value.
 * RSSI levels: >= -55 excellent, -55..-70 good, -70..-80 fair, < -80 poor
 */
@Composable
fun SignalStrengthBar(
    rssi: Int,
    modifier: Modifier = Modifier
) {
    val (icon, tint) = when {
        rssi >= -55 -> Icons.Filled.SignalWifi4Bar to MaterialTheme.colorScheme.primary
        rssi >= -70 -> Icons.Filled.SignalWifi3Bar to MaterialTheme.colorScheme.primary
        rssi >= -80 -> Icons.Filled.SignalWifi2Bar to MaterialTheme.colorScheme.tertiary
        rssi >= -90 -> Icons.Filled.SignalWifi1Bar to MaterialTheme.colorScheme.error
        else -> Icons.Filled.SignalWifi0Bar to MaterialTheme.colorScheme.error
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Signal strength $rssi dBm",
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

fun rssiToDescription(rssi: Int): String = when {
    rssi >= -55 -> "极佳 ($rssi dBm)"
    rssi >= -70 -> "良好 ($rssi dBm)"
    rssi >= -80 -> "一般 ($rssi dBm)"
    rssi >= -90 -> "较弱 ($rssi dBm)"
    else -> "极差 ($rssi dBm)"
}
