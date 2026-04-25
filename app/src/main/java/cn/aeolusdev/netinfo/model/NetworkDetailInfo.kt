package cn.aeolusdev.netinfo.model

enum class NetworkType {
    WIFI,
    ETHERNET,
    UNKNOWN
}

enum class ConnectionState {
    CONNECTED,
    DISCONNECTED,
    UNKNOWN
}

data class NetworkDetailInfo(
    val networkType: NetworkType = NetworkType.UNKNOWN,
    val interfaceName: String = "",
    val ipv4Addresses: List<String> = emptyList(),
    val ipv6Addresses: List<String> = emptyList(),
    val prefixLength: Int = 0,
    val gateway: String? = null,
    val dnsServers: List<String> = emptyList(),
    val macAddress: String? = null,
    val isMetered: Boolean = false,
    val connectionState: ConnectionState = ConnectionState.UNKNOWN,

    // WiFi specific
    val ssid: String? = null,
    val bssid: String? = null,
    val rssi: Int? = null,
    val linkSpeedMbps: Int? = null,
    val frequencyMhz: Int? = null,
    val channel: Int? = null,
    val wifiStandard: String? = null,

    // Ethernet specific
    val ethernetLinkSpeedMbps: Int? = null,
    val duplex: String? = null
)
