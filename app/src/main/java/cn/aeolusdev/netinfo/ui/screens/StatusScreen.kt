package cn.aeolusdev.netinfo.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import cn.aeolusdev.netinfo.model.ConnectionState
import cn.aeolusdev.netinfo.model.NetworkDetailInfo
import cn.aeolusdev.netinfo.model.NetworkType
import cn.aeolusdev.netinfo.ui.components.SignalStrengthBar
import cn.aeolusdev.netinfo.ui.components.rssiToDescription

@Composable
fun StatusScreen(viewModel: NetworkViewModel) {
    val networkInfo by viewModel.networkInfo.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    var detailsExpanded by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                SmallFloatingActionButton(
                    onClick = {
                        val summary = buildSummaryText(networkInfo)
                        clipboardManager.setText(AnnotatedString(summary))
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "复制信息")
                }
                Spacer(modifier = Modifier.size(8.dp))
                ExtendedFloatingActionButton(
                    onClick = { viewModel.refresh() },
                    icon = { Icon(Icons.Filled.Refresh, contentDescription = "刷新") },
                    text = { Text("刷新") }
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(
                top = 16.dp,
                bottom = 88.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                NetworkTypeCard(networkInfo = networkInfo)
            }

            item {
                MainInfoCard(networkInfo = networkInfo)
            }

            item {
                DetailsCard(
                    networkInfo = networkInfo,
                    expanded = detailsExpanded,
                    onToggle = { detailsExpanded = !detailsExpanded }
                )
            }
        }
    }
}

@Composable
private fun NetworkTypeCard(networkInfo: NetworkDetailInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (networkInfo.connectionState) {
                ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primaryContainer
                ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = when (networkInfo.networkType) {
                    NetworkType.WIFI -> if (networkInfo.connectionState == ConnectionState.CONNECTED)
                        Icons.Filled.Wifi else Icons.Filled.WifiOff
                    NetworkType.ETHERNET -> Icons.Filled.Cable
                    else -> Icons.Filled.WifiOff
                },
                contentDescription = "网络类型",
                modifier = Modifier.size(48.dp),
                tint = when (networkInfo.connectionState) {
                    ConnectionState.CONNECTED -> MaterialTheme.colorScheme.onPrimaryContainer
                    ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.onErrorContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Column {
                Text(
                    text = when (networkInfo.networkType) {
                        NetworkType.WIFI -> "Wi-Fi"
                        NetworkType.ETHERNET -> "以太网"
                        else -> "未知网络"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = when (networkInfo.connectionState) {
                        ConnectionState.CONNECTED -> MaterialTheme.colorScheme.onPrimaryContainer
                        ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = when (networkInfo.connectionState) {
                        ConnectionState.CONNECTED -> "已连接"
                        ConnectionState.DISCONNECTED -> "已断开"
                        else -> "检测中..."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (networkInfo.connectionState) {
                        ConnectionState.CONNECTED -> MaterialTheme.colorScheme.onPrimaryContainer
                        ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun MainInfoCard(networkInfo: NetworkDetailInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "基本信息",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // WiFi: SSID
            if (networkInfo.networkType == NetworkType.WIFI) {
                networkInfo.ssid?.let {
                    InfoRow(
                        icon = { Icon(Icons.Filled.Wifi, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        label = "SSID",
                        value = it
                    )
                }
                networkInfo.rssi?.let { rssi ->
                    InfoRow(
                        icon = { SignalStrengthBar(rssi = rssi, modifier = Modifier.size(18.dp)) },
                        label = "信号强度",
                        value = rssiToDescription(rssi)
                    )
                }
            }

            // IP Address
            val primaryIp = networkInfo.ipv4Addresses.firstOrNull()
                ?: networkInfo.ipv6Addresses.firstOrNull()
                ?: "—"
            InfoRow(
                icon = { Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = "IP 地址",
                value = primaryIp
            )

            // Gateway
            networkInfo.gateway?.let {
                InfoRow(
                    icon = { Icon(Icons.Filled.Router, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    label = "网关",
                    value = it
                )
            }

            // DNS
            if (networkInfo.dnsServers.isNotEmpty()) {
                InfoRow(
                    icon = { Icon(Icons.Outlined.Dns, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    label = "DNS",
                    value = networkInfo.dnsServers.firstOrNull() ?: "—"
                )
            }

            // Link speed
            val speed = networkInfo.linkSpeedMbps ?: networkInfo.ethernetLinkSpeedMbps
            speed?.let {
                InfoRow(
                    icon = { Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    label = "连接速率",
                    value = "$it Mbps"
                )
            }
        }
    }
}

@Composable
private fun DetailsCard(
    networkInfo: NetworkDetailInfo,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "详细信息",
                    style = MaterialTheme.typography.titleMedium
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开"
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // All IPs
                    networkInfo.ipv4Addresses.forEachIndexed { idx, ip ->
                        DetailRow(label = if (idx == 0) "IPv4 地址" else "IPv4 #${idx + 1}", value = ip)
                    }
                    networkInfo.ipv6Addresses.forEachIndexed { idx, ip ->
                        DetailRow(label = if (idx == 0) "IPv6 地址" else "IPv6 #${idx + 1}", value = ip)
                    }

                    // Subnet mask
                    if (networkInfo.prefixLength > 0) {
                        DetailRow(label = "子网掩码", value = prefixLengthToSubnetMask(networkInfo.prefixLength))
                    }

                    // MAC
                    networkInfo.macAddress?.let {
                        DetailRow(label = "MAC 地址", value = it)
                    }

                    // Gateway
                    networkInfo.gateway?.let {
                        DetailRow(label = "默认网关", value = it)
                    }

                    // DNS list
                    networkInfo.dnsServers.forEachIndexed { idx, dns ->
                        DetailRow(label = "DNS ${idx + 1}", value = dns)
                    }

                    // Interface
                    if (networkInfo.interfaceName.isNotEmpty()) {
                        DetailRow(label = "接口名称", value = networkInfo.interfaceName)
                    }

                    // WiFi specific
                    if (networkInfo.networkType == NetworkType.WIFI) {
                        networkInfo.bssid?.let { DetailRow(label = "BSSID", value = it) }
                        networkInfo.frequencyMhz?.let { DetailRow(label = "频率", value = "${it} MHz") }
                        networkInfo.channel?.let { DetailRow(label = "信道", value = it.toString()) }
                        networkInfo.wifiStandard?.let { DetailRow(label = "网络标准", value = it) }
                    }

                    // Ethernet specific
                    if (networkInfo.networkType == NetworkType.ETHERNET) {
                        networkInfo.duplex?.let { DetailRow(label = "双工模式", value = it) }
                        networkInfo.ethernetLinkSpeedMbps?.let { DetailRow(label = "以太网速率", value = "$it Mbps") }
                    }

                    DetailRow(
                        label = "按流量计费",
                        value = if (networkInfo.isMetered) "是" else "否"
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    icon: @Composable () -> Unit,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Divider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun prefixLengthToSubnetMask(prefixLength: Int): String {
    val mask = if (prefixLength == 0) 0 else (-1 shl (32 - prefixLength))
    return "${(mask shr 24) and 0xFF}.${(mask shr 16) and 0xFF}.${(mask shr 8) and 0xFF}.${mask and 0xFF}"
}

private fun buildSummaryText(info: NetworkDetailInfo): String {
    return buildString {
        appendLine("网络类型: ${when (info.networkType) { NetworkType.WIFI -> "Wi-Fi"; NetworkType.ETHERNET -> "以太网"; else -> "未知" }}")
        appendLine("连接状态: ${when (info.connectionState) { ConnectionState.CONNECTED -> "已连接"; ConnectionState.DISCONNECTED -> "已断开"; else -> "未知" }}")
        info.ssid?.let { appendLine("SSID: $it") }
        info.ipv4Addresses.firstOrNull()?.let { appendLine("IP 地址: $it") }
        info.gateway?.let { appendLine("网关: $it") }
        info.dnsServers.firstOrNull()?.let { appendLine("DNS: $it") }
        info.macAddress?.let { appendLine("MAC 地址: $it") }
    }.trimEnd()
}
