package cn.aeolusdev.netinfo.ui.screens

import android.Manifest
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.content.ContextCompat
import cn.aeolusdev.netinfo.model.ConnectionState
import cn.aeolusdev.netinfo.model.NetworkDetailInfo
import cn.aeolusdev.netinfo.model.NetworkType
import cn.aeolusdev.netinfo.model.primaryLocalIpAddress
import cn.aeolusdev.netinfo.ui.connectionStateLabel
import cn.aeolusdev.netinfo.ui.networkTypeIcon
import cn.aeolusdev.netinfo.ui.networkTypeLabel
import cn.aeolusdev.netinfo.ui.components.SignalStrengthBar
import cn.aeolusdev.netinfo.ui.components.rssiToDescription

@Composable
fun StatusScreen(viewModel: NetworkViewModel) {
    val networkInfo by viewModel.networkInfo.collectAsState()
    var detailsExpanded by remember { mutableStateOf(false) }

    RequestLocationPermissions()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OverviewCard(networkInfo = networkInfo)
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

@Composable
private fun RequestLocationPermissions() {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(Unit) {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFineLocation && !hasCoarseLocation) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }
}

@Composable
private fun OverviewCard(networkInfo: NetworkDetailInfo) {
    val contentColor = overviewContentColor(networkInfo)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (networkInfo.connectionState) {
                ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primaryContainer
                ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.errorContainer
                ConnectionState.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = networkTypeIcon(networkInfo),
                    contentDescription = "网络类型",
                    modifier = Modifier.size(56.dp),
                    tint = contentColor
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = networkTypeLabel(networkInfo),
                        style = MaterialTheme.typography.headlineSmall,
                        color = contentColor
                    )
                    Text(
                        text = buildStatusSubtitle(networkInfo),
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor
                    )
                }
            }

            if (networkInfo.isVpnActive) {
                StatusBadge(
                    text = "VPN 已开启",
                    imageVector = Icons.Filled.Lock,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            }

            if (networkInfo.updatesPaused) {
                Text(
                    text = "自动刷新已暂停",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            }

            when (networkInfo.networkType) {
                NetworkType.WIFI -> WifiOverviewRows(networkInfo, contentColor)
                NetworkType.MOBILE -> MobileOverviewRows(networkInfo, contentColor)
                NetworkType.ETHERNET -> EthernetOverviewRows(networkInfo, contentColor)
                NetworkType.UNKNOWN -> UnknownOverviewRows(networkInfo, contentColor)
            }
        }
    }
}

@Composable
private fun StatusBadge(
    text: String,
    imageVector: ImageVector,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(999.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun WifiOverviewRows(networkInfo: NetworkDetailInfo, contentColor: Color) {
    networkInfo.ssid?.let {
        OverviewInfoRow(
            label = "SSID",
            value = it,
            color = contentColor,
            icon = { Icon(Icons.Filled.Wifi, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
    }
    networkInfo.rssi?.let { rssi ->
        OverviewInfoRow(
            label = "信号强度",
            value = rssiToDescription(rssi),
            color = contentColor,
            icon = { SignalStrengthBar(rssi = rssi, modifier = Modifier.size(18.dp)) }
        )
    }
    OverviewInfoRow(
        label = "IP 地址",
        value = primaryIp(networkInfo),
        color = contentColor,
        icon = { Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(18.dp)) }
    )
    networkInfo.gateway?.let { gateway ->
        OverviewInfoRow(
            label = "网关",
            value = gateway,
            color = contentColor,
            icon = { Icon(Icons.Filled.Router, contentDescription = null, modifier = Modifier.size(18.dp)) },
            trailingContent = { GatewayOpenButton(gateway = gateway, tint = contentColor) }
        )
    }
}

@Composable
private fun MobileOverviewRows(networkInfo: NetworkDetailInfo, contentColor: Color) {
    networkInfo.carrierName?.let {
        OverviewInfoRow(
            label = "运营商",
            value = it,
            color = contentColor,
            icon = { Icon(Icons.Filled.Business, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
    }
    networkInfo.mobileNetworkLabel?.let {
        OverviewInfoRow(
            label = "网络制式",
            value = it,
            color = contentColor,
            icon = { Icon(Icons.Filled.NetworkCell, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
    }
    networkInfo.fiveGMode?.let {
        OverviewInfoRow(
            label = "5G 组网",
            value = it,
            color = contentColor,
            icon = { Icon(Icons.Filled.Memory, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
    }
    networkInfo.cellId?.let {
        OverviewInfoRow(
            label = "CID",
            value = it,
            color = contentColor,
            icon = { Icon(Icons.Filled.Memory, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
    }
    networkInfo.locationAreaCode?.let {
        OverviewInfoRow(
            label = "LAC/TAC",
            value = it,
            color = contentColor,
            icon = { Icon(Icons.Filled.Memory, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
    }
    OverviewInfoRow(
        label = "IP 地址",
        value = primaryIp(networkInfo),
        color = contentColor,
        icon = { Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(18.dp)) }
    )
}

@Composable
private fun EthernetOverviewRows(networkInfo: NetworkDetailInfo, contentColor: Color) {
    OverviewInfoRow(
        label = "IP 地址",
        value = primaryIp(networkInfo),
        color = contentColor,
        icon = { Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(18.dp)) }
    )
    networkInfo.gateway?.let { gateway ->
        OverviewInfoRow(
            label = "网关",
            value = gateway,
            color = contentColor,
            icon = { Icon(Icons.Filled.Router, contentDescription = null, modifier = Modifier.size(18.dp)) },
            trailingContent = { GatewayOpenButton(gateway = gateway, tint = contentColor) }
        )
    }
    networkInfo.linkSpeedMbps?.let {
        OverviewInfoRow(
            label = "连接速率",
            value = "$it Mbps",
            color = contentColor,
            icon = { Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.size(18.dp)) }
        )
    }
}

@Composable
private fun UnknownOverviewRows(networkInfo: NetworkDetailInfo, contentColor: Color) {
    OverviewInfoRow(
        label = "连接状态",
        value = connectionStateLabel(networkInfo),
        color = contentColor,
        icon = { Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(18.dp)) }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailsCard(
    networkInfo: NetworkDetailInfo,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = onToggle, onLongClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "详情信息",
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    networkInfo.ipv4Addresses.forEachIndexed { index, ip ->
                        DetailRow(label = if (index == 0) "IPv4 地址" else "IPv4 #${index + 1}", value = ip)
                    }
                    networkInfo.ipv6Addresses.forEachIndexed { index, ip ->
                        DetailRow(label = if (index == 0) "IPv6 地址" else "IPv6 #${index + 1}", value = ip)
                    }

                    if (networkInfo.prefixLength in 1..32) {
                        DetailRow(
                            label = "子网掩码",
                            value = prefixLengthToSubnetMask(networkInfo.prefixLength)
                        )
                    }

                    networkInfo.macAddress?.let { DetailRow(label = "MAC 地址", value = it) }
                    networkInfo.gateway?.let { gateway ->
                        DetailRow(
                            label = "默认网关",
                            value = gateway,
                            trailingContent = if (supportsGatewayQuickOpen(networkInfo)) {
                                { GatewayOpenButton(gateway = gateway) }
                            } else {
                                null
                            }
                        )
                    }
                    networkInfo.dnsServers.forEachIndexed { index, dns ->
                        DetailRow(label = "DNS ${index + 1}", value = dns)
                    }

                    if (networkInfo.interfaceName.isNotBlank()) {
                        DetailRow(label = "接口名称", value = networkInfo.interfaceName)
                    }

                    if (networkInfo.networkType == NetworkType.WIFI) {
                        networkInfo.bssid?.let { DetailRow(label = "BSSID", value = it) }
                        networkInfo.frequencyMhz?.let { DetailRow(label = "频率", value = "$it MHz") }
                        networkInfo.channel?.let { DetailRow(label = "信道", value = it.toString()) }
                        networkInfo.wifiStandard?.let { DetailRow(label = "网络标准", value = it) }
                    }

                    if (networkInfo.networkType == NetworkType.MOBILE) {
                        networkInfo.carrierName?.let { DetailRow(label = "运营商", value = it) }
                        networkInfo.mobileNetworkLabel?.let { DetailRow(label = "网络制式", value = it) }
                        networkInfo.cellId?.let { DetailRow(label = "基站 ID (CID)", value = it) }
                        networkInfo.locationAreaCode?.let { DetailRow(label = "位置区码 (LAC/TAC)", value = it) }
                        networkInfo.fiveGMode?.let { DetailRow(label = "5G 组网", value = it) }
                    }

                    if (networkInfo.networkType == NetworkType.ETHERNET) {
                        networkInfo.duplex?.let { DetailRow(label = "双工模式", value = it) }
                        networkInfo.ethernetLinkSpeedMbps?.let {
                            DetailRow(label = "以太网速率", value = "$it Mbps")
                        }
                    }

                    networkInfo.linkSpeedMbps?.let {
                        if (networkInfo.networkType != NetworkType.ETHERNET) {
                            DetailRow(label = "连接速率", value = "$it Mbps")
                        }
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
private fun OverviewInfoRow(
    label: String,
    value: String,
    color: Color,
    icon: @Composable () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = color.copy(alpha = 0.82f),
            modifier = Modifier.width(84.dp)
        )
        CopyableValueText(
            value = value,
            color = color,
            modifier = Modifier.weight(1f)
        )
        trailingContent?.invoke()
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(112.dp)
        )
        CopyableValueText(
            value = value,
            modifier = Modifier.weight(1f)
        )
        trailingContent?.invoke()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CopyableValueText(
    value: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    context: Context = LocalContext.current
) {
    val clipboardManager = LocalClipboardManager.current
    Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.combinedClickable(
            onClick = { },
            onLongClick = {
                clipboardManager.setText(AnnotatedString(value))
                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
            }
        )
    )
}

private fun primaryIp(networkInfo: NetworkDetailInfo): String {
    return networkInfo.primaryLocalIpAddress() ?: "—"
}

private fun buildStatusSubtitle(networkInfo: NetworkDetailInfo): String {
    val parts = buildList {
        add(connectionStateLabel(networkInfo))
        when (networkInfo.networkType) {
            NetworkType.WIFI -> networkInfo.wifiStandard?.let(::add)
            NetworkType.MOBILE -> {
                networkInfo.mobileNetworkLabel?.let(::add)
                networkInfo.fiveGMode?.let { add("组网 $it") }
            }

            else -> Unit
        }
    }
    return parts.joinToString(" · ")
}

private fun supportsGatewayQuickOpen(networkInfo: NetworkDetailInfo): Boolean {
    return networkInfo.networkType == NetworkType.WIFI || networkInfo.networkType == NetworkType.ETHERNET
}

@Composable
private fun GatewayOpenButton(
    gateway: String,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    val context = LocalContext.current
    IconButton(
        onClick = { openGatewayInBrowser(context, gateway) },
        modifier = Modifier.size(28.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.OpenInBrowser,
            contentDescription = "在浏览器中打开网关",
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun overviewContentColor(networkInfo: NetworkDetailInfo) = when (networkInfo.connectionState) {
    ConnectionState.CONNECTED -> MaterialTheme.colorScheme.onPrimaryContainer
    ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.onErrorContainer
    ConnectionState.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun prefixLengthToSubnetMask(prefixLength: Int): String {
    val mask = if (prefixLength == 0) 0 else (-1 shl (32 - prefixLength))
    return "${(mask shr 24) and 0xFF}.${(mask shr 16) and 0xFF}.${(mask shr 8) and 0xFF}.${mask and 0xFF}"
}

private fun openGatewayInBrowser(context: Context, gateway: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, gatewayUri(gateway)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, "无法打开网关地址", Toast.LENGTH_SHORT).show()
    }
}

private fun gatewayUri(gateway: String): Uri {
    val normalized = gateway.substringBefore('%')
    val host = if (':' in normalized && !normalized.startsWith("[")) {
        "[$normalized]"
    } else {
        normalized
    }
    return Uri.parse("http://$host")
}
