package cn.aeolusdev.netinfo.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.ui.graphics.vector.ImageVector
import cn.aeolusdev.netinfo.model.ConnectionState
import cn.aeolusdev.netinfo.model.NetworkDetailInfo
import cn.aeolusdev.netinfo.model.NetworkType

fun networkTypeIcon(networkInfo: NetworkDetailInfo): ImageVector {
    return when (networkInfo.networkType) {
        NetworkType.WIFI -> {
            if (networkInfo.connectionState == ConnectionState.CONNECTED) {
                Icons.Filled.Wifi
            } else {
                Icons.Filled.WifiOff
            }
        }

        NetworkType.MOBILE -> Icons.Filled.SignalCellularAlt
        NetworkType.ETHERNET -> Icons.Filled.Cable
        NetworkType.UNKNOWN -> Icons.Filled.WifiOff
    }
}

fun networkTypeLabel(networkInfo: NetworkDetailInfo): String {
    return when (networkInfo.networkType) {
        NetworkType.WIFI -> "Wi-Fi"
        NetworkType.MOBILE -> "移动数据"
        NetworkType.ETHERNET -> "以太网"
        NetworkType.UNKNOWN -> "未知网络"
    }
}

fun connectionStateLabel(networkInfo: NetworkDetailInfo): String {
    return when (networkInfo.connectionState) {
        ConnectionState.CONNECTED -> "已连接"
        ConnectionState.DISCONNECTED -> "已断开"
        ConnectionState.UNKNOWN -> "检测中..."
    }
}
