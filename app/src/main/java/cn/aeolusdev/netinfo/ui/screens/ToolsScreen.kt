package cn.aeolusdev.netinfo.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import cn.aeolusdev.netinfo.model.ConnectionState
import cn.aeolusdev.netinfo.model.ExternalNetworkInfo
import cn.aeolusdev.netinfo.model.NetworkDetailInfo
import cn.aeolusdev.netinfo.model.primaryLocalIpAddress
import java.util.Locale

@Composable
fun ToolsScreen(
    pingState: PingUiState,
    lanScanState: LanScanUiState,
    traceState: TraceUiState,
    onOpenPing: () -> Unit,
    onOpenExternalInfo: () -> Unit,
    onOpenLanScanner: () -> Unit,
    onOpenTraceroute: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ToolEntryCard(
                title = "Ping",
                description = "连通性检测、延迟与丢包诊断",
                footer = pingCardFooter(pingState),
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Speed,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                },
                onClick = onOpenPing
            )
        }

        item {
            ToolEntryCard(
                title = "外网信息",
                description = "查看公网 IP、地区、运营商与时区",
                footer = "进入查看完整外网出口信息",
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Public,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                },
                onClick = onOpenExternalInfo
            )
        }

        item {
            ToolEntryCard(
                title = "局域网扫描仪",
                description = "扫描当前网段内可响应的在线设备",
                footer = lanScannerFooter(lanScanState),
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Router,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                },
                onClick = onOpenLanScanner
            )
        }

        item {
            ToolEntryCard(
                title = "路由跟踪",
                description = "查看数据包到目标主机的大致路径",
                footer = traceFooter(traceState),
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Timeline,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                },
                onClick = onOpenTraceroute
            )
        }
    }
}

@Composable
fun PingToolScreen(
    state: PingUiState,
    onHostChange: (String) -> Unit,
    onStartPing: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "输入目标主机后执行 4 次 Ping，并生成简要诊断报告。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = state.hostInput,
                    onValueChange = onHostChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("主机地址") },
                    placeholder = { Text("例如：1.1.1.1 或 example.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onStartPing() }),
                    enabled = !state.isRunning
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onStartPing,
                        enabled = !state.isRunning && state.hostInput.isNotBlank()
                    ) {
                        Text("开始测试")
                    }

                    if (state.isRunning) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
        }

        state.latestResult?.let { result ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = result.status,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = result.host,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = result.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )

                    if (result.transmitted != null || result.received != null) {
                        Text(
                            text = "发送 ${result.transmitted ?: "-"} / 接收 ${result.received ?: "-"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            RawOutputCard(
                title = "原始输出",
                content = result.rawOutput
            )
        }
    }
}

@Composable
fun ExternalInfoToolScreen(
    networkInfo: NetworkDetailInfo,
    onRefresh: () -> Unit
) {
    val externalInfo = networkInfo.externalInfo

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "当前出口信息",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "基于当前联网状态实时刷新",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "刷新"
                        )
                    }
                }

                if (networkInfo.connectionState != ConnectionState.CONNECTED) {
                    Text(
                        text = "当前未连接网络，无法获取外网出口信息。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    ExternalInfoContent(externalInfo = externalInfo)
                }
            }
        }
    }
}

@Composable
fun LanScannerToolScreen(
    networkInfo: NetworkDetailInfo,
    state: LanScanUiState,
    onStartScan: (String?, Int) -> Unit
) {
    val localIpv4 = networkInfo.ipv4Addresses.firstOrNull()
    val localAddress = localIpv4 ?: networkInfo.primaryLocalIpAddress()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "基于当前 IPv4 网段进行快速扫描。大网段会自动缩到当前 /24 段，避免耗时过长。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "本机地址：${localAddress ?: "不可用"}",
                    style = MaterialTheme.typography.bodyMedium
                )

                Button(
                    onClick = { onStartScan(localIpv4, networkInfo.prefixLength) },
                    enabled = !state.isRunning
                ) {
                    Text(if (state.isRunning) "扫描中" else "开始扫描")
                }

                if (state.isRunning) {
                    Text(
                        text = "正在扫描 ${state.currentSubnet.orEmpty()} (${state.scannedCount}/${state.totalHosts})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        state.latestResult?.let { result ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = result.subnet,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = result.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (result.devices.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "在线设备",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        result.devices.forEachIndexed { index, device ->
                            if (index > 0) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = device.ip,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = device.latencyMs?.let {
                                        "${String.format(Locale.US, "%.1f", it)} ms"
                                    } ?: "在线",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TracerouteToolScreen(
    state: TraceUiState,
    onHostChange: (String) -> Unit,
    onStartTrace: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "按 TTL 递增方式做近似路由跟踪，结果取决于目标与中间设备是否响应 ICMP。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = state.hostInput,
                    onValueChange = onHostChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("目标主机") },
                    placeholder = { Text("例如：8.8.8.8") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onStartTrace() }),
                    enabled = !state.isRunning
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onStartTrace,
                        enabled = !state.isRunning && state.hostInput.isNotBlank()
                    ) {
                        Text("开始跟踪")
                    }
                    if (state.isRunning) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
        }

        state.latestRun?.let { run ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = run.host,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = run.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (run.hops.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "跳点列表",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        run.hops.forEachIndexed { index, hop ->
                            if (index > 0) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${hop.ttl}. ${hop.node}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = hop.latencyMs?.let {
                                            "${String.format(Locale.US, "%.1f", it)} ms"
                                        } ?: "超时",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (hop.reachedTarget) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                                Text(
                                    text = hop.rawLine,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            RawOutputCard(
                title = "调试输出",
                content = run.rawOutput
            )
        }
    }
}

@Composable
private fun ToolEntryCard(
    title: String,
    description: String,
    footer: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.large
                    ),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = footer,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null
            )
        }
    }
}

@Composable
private fun ExternalInfoContent(externalInfo: ExternalNetworkInfo) {
    val hasData = listOf(
        externalInfo.ip,
        externalInfo.city,
        externalInfo.region,
        externalInfo.country,
        externalInfo.org,
        externalInfo.timeZone
    ).any { !it.isNullOrBlank() }

    if (!hasData && externalInfo.error.isNullOrBlank()) {
        Text(
            text = "正在获取外网信息…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    externalInfo.ip?.let { ExternalInfoRow("公网 IP", it) }
    externalInfo.city?.let { ExternalInfoRow("城市", it) }
    externalInfo.region?.let { ExternalInfoRow("地区", it) }
    externalInfo.country?.let {
        val displayText = listOf(externalInfo.flagEmoji.takeIf(String::isNotBlank), it)
            .filterNotNull()
            .joinToString(" ")
        ExternalInfoRow("国家", displayText)
    }
    externalInfo.org?.let { ExternalInfoRow("运营商", it) }
    externalInfo.timeZone?.let { ExternalInfoRow("时区", it) }

    externalInfo.error?.let {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "获取失败：$it",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun ExternalInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Language,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.65f)
        )
    }
}

@Composable
private fun RawOutputCard(title: String, content: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            SelectionContainer {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun pingCardFooter(state: PingUiState): String {
    return when {
        state.isRunning -> "正在执行 Ping 测试"
        state.latestResult != null -> {
            "${state.latestResult.host} · ${state.latestResult.summary}"
        }
        else -> "进入后开始连通性测试"
    }
}

private fun lanScannerFooter(state: LanScanUiState): String {
    return when {
        state.isRunning -> "正在扫描 ${state.scannedCount}/${state.totalHosts}"
        state.latestResult != null -> "${state.latestResult.subnet} · ${state.latestResult.summary}"
        else -> "进入后扫描当前局域网"
    }
}

private fun traceFooter(state: TraceUiState): String {
    return when {
        state.isRunning -> "正在执行路由跟踪"
        state.latestRun != null -> "${state.latestRun.host} · ${state.latestRun.summary}"
        else -> "进入后查看路由路径"
    }
}
