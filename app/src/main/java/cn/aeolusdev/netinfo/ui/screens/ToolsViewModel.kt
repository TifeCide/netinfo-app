package cn.aeolusdev.netinfo.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale

class ToolsViewModel : ViewModel() {

    private val _pingState = MutableStateFlow(PingUiState())
    val pingState: StateFlow<PingUiState> = _pingState.asStateFlow()

    private val _lanScanState = MutableStateFlow(LanScanUiState())
    val lanScanState: StateFlow<LanScanUiState> = _lanScanState.asStateFlow()

    private val _traceState = MutableStateFlow(TraceUiState())
    val traceState: StateFlow<TraceUiState> = _traceState.asStateFlow()

    fun updatePingHost(host: String) {
        _pingState.update { it.copy(hostInput = host) }
    }

    fun startPing() {
        val host = _pingState.value.hostInput.trim()
        if (_pingState.value.isRunning) return

        if (!isValidHost(host)) {
            _pingState.update {
                it.copy(
                    latestResult = PingResult(
                        host = host.ifBlank { "未填写目标" },
                        status = "无效目标",
                        summary = "请输入有效的主机名、IPv4 或 IPv6 地址",
                        rawOutput = "目标为空或格式不合法"
                    )
                )
            }
            return
        }

        viewModelScope.launch {
            _pingState.update { it.copy(isRunning = true) }
            val result = runPing(host)
            _pingState.update {
                it.copy(
                    isRunning = false,
                    latestResult = result
                )
            }
        }
    }

    fun updateTraceHost(host: String) {
        _traceState.update { it.copy(hostInput = host) }
    }

    fun startTraceroute() {
        val host = _traceState.value.hostInput.trim()
        if (_traceState.value.isRunning) return

        if (!isValidHost(host)) {
            _traceState.update {
                it.copy(
                    latestRun = TraceRun(
                        host = host.ifBlank { "未填写目标" },
                        summary = "请输入有效的主机名、IPv4 或 IPv6 地址",
                        hops = emptyList(),
                        rawOutput = "目标为空或格式不合法"
                    )
                )
            }
            return
        }

        viewModelScope.launch {
            _traceState.update { it.copy(isRunning = true) }
            val result = runTraceroute(host)
            _traceState.update {
                it.copy(
                    isRunning = false,
                    latestRun = result
                )
            }
        }
    }

    fun startLanScan(localIpv4: String?, prefixLength: Int) {
        if (_lanScanState.value.isRunning) return

        if (localIpv4.isNullOrBlank()) {
            _lanScanState.update {
                it.copy(
                    latestResult = LanScanResult(
                        subnet = "不可用",
                        summary = "当前没有可用于扫描的 IPv4 地址",
                        devices = emptyList()
                    )
                )
            }
            return
        }

        val targetRange = buildScanTargets(localIpv4, prefixLength)
            ?: run {
                _lanScanState.update {
                    it.copy(
                        latestResult = LanScanResult(
                            subnet = "不可用",
                            summary = "无法根据当前地址生成扫描范围",
                            devices = emptyList()
                        )
                    )
                }
                return
            }

        viewModelScope.launch {
            _lanScanState.update {
                it.copy(
                    isRunning = true,
                    scannedCount = 0,
                    totalHosts = targetRange.hosts.size,
                    currentSubnet = targetRange.label
                )
            }

            val devices = mutableListOf<LanDevice>()
            targetRange.hosts.chunked(16).forEach { batch ->
                val foundInBatch = withContext(Dispatchers.IO) {
                    batch.map { host ->
                        async {
                            probeLanHost(host)
                        }
                    }.awaitAll().filterNotNull()
                }
                devices += foundInBatch
                _lanScanState.update {
                    it.copy(scannedCount = (it.scannedCount + batch.size).coerceAtMost(targetRange.hosts.size))
                }
            }

            val sortedDevices = devices.sortedBy { ipToLong(it.ip) ?: Long.MAX_VALUE }
            _lanScanState.update {
                it.copy(
                    isRunning = false,
                    latestResult = LanScanResult(
                        subnet = targetRange.label,
                        summary = if (sortedDevices.isEmpty()) {
                            "未发现可响应的主机"
                        } else {
                            "发现 ${sortedDevices.size} 台在线设备"
                        },
                        devices = sortedDevices
                    )
                )
            }
        }
    }

    private suspend fun runPing(host: String): PingResult = withContext(Dispatchers.IO) {
        val commandResult = executeCommand(
            args = listOf("ping", "-c", "4", "-W", "3", host),
            timeoutMs = 10_000
        )
        return@withContext parsePingResult(host, commandResult)
    }

    private suspend fun runTraceroute(host: String): TraceRun = withContext(Dispatchers.IO) {
        val rawLines = mutableListOf<String>()
        val hops = mutableListOf<TraceHop>()

        for (ttl in 1..15) {
            val result = executeCommand(
                args = listOf("ping", "-c", "1", "-W", "1", "-t", ttl.toString(), host),
                timeoutMs = 4_000
            )
            rawLines += "TTL $ttl\n${result.output.ifBlank { result.error.orEmpty() }}"
            val hop = parseTraceHop(ttl, host, result)
            hops += hop
            if (hop.reachedTarget) {
                break
            }
        }

        val reachedTarget = hops.any { it.reachedTarget }
        val summary = if (reachedTarget) {
            "共 ${hops.size} 跳，已到达目标"
        } else if (hops.isEmpty()) {
            "未获得有效路由响应"
        } else {
            "探测完成，共 ${hops.size} 跳，目标未确认到达"
        }

        return@withContext TraceRun(
            host = host,
            summary = summary,
            hops = hops,
            rawOutput = rawLines.joinToString("\n\n")
        )
    }

    private fun parsePingResult(host: String, result: CommandResult): PingResult {
        val output = result.output.ifBlank { result.error.orEmpty() }.ifBlank { "无输出" }
        val transmitted = Regex("(\\d+)\\s+packets transmitted")
            .find(output)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        val received = Regex("(\\d+)\\s+(?:packets )?received")
            .find(output)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        val loss = Regex("(\\d+)%\\s+packet loss")
            .find(output)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        val averageLatency = Regex("=\\s*[\\d.]+/([\\d.]+)/")
            .find(output)
            ?.groupValues
            ?.getOrNull(1)
            ?.toDoubleOrNull()

        val status = when {
            result.exitCode == 0 && loss == 0 && averageLatency != null && averageLatency <= 40 -> "网络优秀"
            result.exitCode == 0 && loss != null && loss <= 10 -> "网络良好"
            result.exitCode == 0 && loss != null && loss <= 40 -> "网络一般"
            result.exitCode == 0 -> "可达"
            received != null && received > 0 -> "不稳定"
            else -> "不可达"
        }

        val summaryParts = buildList {
            add(status)
            loss?.let { add("$it% 丢包") }
            averageLatency?.let {
                add("平均 ${String.format(Locale.US, "%.1f", it)} ms")
            }
            if (loss == null && result.exitCode != 0) {
                add(result.error ?: "命令执行失败")
            }
        }

        return PingResult(
            host = host,
            status = status,
            summary = summaryParts.joinToString(" · "),
            rawOutput = output,
            transmitted = transmitted,
            received = received,
            packetLossPercent = loss,
            averageLatencyMs = averageLatency
        )
    }

    private fun parseTraceHop(ttl: Int, host: String, result: CommandResult): TraceHop {
        val output = result.output.ifBlank { result.error.orEmpty() }
        val latencyMs = Regex("time[=<]([\\d.]+)\\s*ms")
            .find(output)
            ?.groupValues
            ?.getOrNull(1)
            ?.toDoubleOrNull()

        val hopAddress = Regex("From\\s+([^\\s:]+)")
            .find(output)
            ?.groupValues
            ?.getOrNull(1)
            ?: Regex("bytes from\\s+([^\\s:]+)")
                .find(output)
                ?.groupValues
                ?.getOrNull(1)
            ?: extractFirstIpToken(output)
            ?: "*"

        val reachedTarget = result.exitCode == 0 &&
            (hopAddress.equals(host, ignoreCase = true) || output.contains("bytes from", ignoreCase = true))

        return TraceHop(
            ttl = ttl,
            node = hopAddress,
            latencyMs = latencyMs,
            reachedTarget = reachedTarget,
            rawLine = output.lineSequence().firstOrNull { it.isNotBlank() } ?: "请求超时"
        )
    }

    private fun buildScanTargets(localIpv4: String, prefixLength: Int): ScanTargetRange? {
        val local = ipToLong(localIpv4) ?: return null
        val effectivePrefix = prefixLength.coerceIn(16, 30)
        val hostCount = (1L shl (32 - effectivePrefix)) - 2L

        val mask = ((0xFFFFFFFFL shl (32 - effectivePrefix)) and 0xFFFFFFFFL)
        val network = local and mask
        val broadcast = network or (mask xor 0xFFFFFFFFL)

        val useTrimmed24 = hostCount > 254
        val start = if (useTrimmed24) {
            (local and 0xFFFFFF00L) + 1L
        } else {
            network + 1L
        }
        val end = if (useTrimmed24) {
            (local and 0xFFFFFF00L) + 254L
        } else {
            broadcast - 1L
        }

        val subnetLabel = if (useTrimmed24) {
            "${longToIp(local and 0xFFFFFF00L)}/24"
        } else {
            "${longToIp(network)}/$effectivePrefix"
        }

        val hosts = (start..end)
            .map(::longToIp)
            .filter { it != localIpv4 }

        return ScanTargetRange(
            label = subnetLabel,
            hosts = hosts
        )
    }

    private fun probeLanHost(ip: String): LanDevice? {
        val pingResult = executeCommand(
            args = listOf("ping", "-c", "1", "-W", "1", ip),
            timeoutMs = 2_000
        )
        val output = pingResult.output.ifBlank { pingResult.error.orEmpty() }
        val latencyMs = Regex("time[=<]([\\d.]+)\\s*ms")
            .find(output)
            ?.groupValues
            ?.getOrNull(1)
            ?.toDoubleOrNull()

        if (pingResult.exitCode == 0) {
            return LanDevice(ip = ip, latencyMs = latencyMs)
        }

        return if (canConnect(ip)) {
            LanDevice(ip = ip, latencyMs = latencyMs)
        } else {
            null
        }
    }

    private fun canConnect(ip: String): Boolean {
        val commonPorts = listOf(80, 443, 53)
        return commonPorts.any { port ->
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), 120)
                    true
                }
            }.getOrDefault(false)
        }
    }

    private fun executeCommand(args: List<String>, timeoutMs: Long): CommandResult {
        return try {
            val process = ProcessBuilder(args)
                .redirectErrorStream(true)
                .start()

            val startedAt = System.currentTimeMillis()
            while (true) {
                try {
                    process.exitValue()
                    break
                } catch (_: IllegalThreadStateException) {
                    if (System.currentTimeMillis() - startedAt >= timeoutMs) {
                        process.destroyForcibly()
                        return CommandResult(
                            exitCode = -1,
                            output = "",
                            error = "执行超时"
                        )
                    }
                    Thread.sleep(50)
                }
            }

            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            CommandResult(
                exitCode = process.exitValue(),
                output = output,
                error = null
            )
        } catch (e: Exception) {
            CommandResult(
                exitCode = -1,
                output = "",
                error = e.message ?: "执行失败"
            )
        }
    }

    private fun isValidHost(host: String): Boolean {
        if (host.isBlank()) return false

        val hostnameRegex = Regex(
            "^[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?)*$"
        )
        val ipv4Regex = Regex("^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$")
        val ipv6Regex = Regex("^[0-9a-fA-F:]+$")
        return hostnameRegex.matches(host) || ipv4Regex.matches(host) || ipv6Regex.matches(host)
    }

    private fun extractFirstIpToken(text: String): String? {
        return Regex("((?:\\d{1,3}\\.){3}\\d{1,3}|[0-9a-fA-F]*:[0-9a-fA-F:]+)")
            .find(text)
            ?.value
    }

    private fun ipToLong(ip: String): Long? {
        val parts = ip.split(".")
        if (parts.size != 4) return null
        var value = 0L
        for (part in parts) {
            val segment = part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
            value = (value shl 8) or segment.toLong()
        }
        return value
    }

    private fun longToIp(value: Long): String {
        return listOf(
            (value shr 24) and 0xFF,
            (value shr 16) and 0xFF,
            (value shr 8) and 0xFF,
            value and 0xFF
        ).joinToString(".")
    }
}

data class PingUiState(
    val hostInput: String = "",
    val isRunning: Boolean = false,
    val latestResult: PingResult? = null
)

data class PingResult(
    val host: String,
    val status: String,
    val summary: String,
    val rawOutput: String,
    val transmitted: Int? = null,
    val received: Int? = null,
    val packetLossPercent: Int? = null,
    val averageLatencyMs: Double? = null
)

data class LanScanUiState(
    val isRunning: Boolean = false,
    val scannedCount: Int = 0,
    val totalHosts: Int = 0,
    val currentSubnet: String? = null,
    val latestResult: LanScanResult? = null
)

data class LanScanResult(
    val subnet: String,
    val summary: String,
    val devices: List<LanDevice>
)

data class LanDevice(
    val ip: String,
    val latencyMs: Double? = null
)

data class TraceUiState(
    val hostInput: String = "",
    val isRunning: Boolean = false,
    val latestRun: TraceRun? = null
)

data class TraceRun(
    val host: String,
    val summary: String,
    val hops: List<TraceHop>,
    val rawOutput: String
)

data class TraceHop(
    val ttl: Int,
    val node: String,
    val latencyMs: Double? = null,
    val reachedTarget: Boolean = false,
    val rawLine: String
)

private data class CommandResult(
    val exitCode: Int,
    val output: String,
    val error: String?
)

private data class ScanTargetRange(
    val label: String,
    val hosts: List<String>
)
