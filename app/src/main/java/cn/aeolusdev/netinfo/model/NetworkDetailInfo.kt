package cn.aeolusdev.netinfo.model

enum class NetworkType {
    WIFI,
    MOBILE,
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
    val updatesPaused: Boolean = false,
    val isVpnActive: Boolean = false,

    // WiFi specific
    val ssid: String? = null,
    val bssid: String? = null,
    val rssi: Int? = null,
    val linkSpeedMbps: Int? = null,
    val frequencyMhz: Int? = null,
    val channel: Int? = null,
    val wifiStandard: String? = null,

    // Mobile specific
    val carrierName: String? = null,
    val mobileNetworkLabel: String? = null,
    val cellId: String? = null,
    val locationAreaCode: String? = null,
    val fiveGMode: String? = null,

    // Ethernet specific
    val ethernetLinkSpeedMbps: Int? = null,
    val duplex: String? = null,

    // External network info
    val externalInfo: ExternalNetworkInfo = ExternalNetworkInfo()
)

data class ExternalNetworkInfo(
    val ip: String? = null,
    val city: String? = null,
    val region: String? = null,
    val country: String? = null,
    val org: String? = null,
    val timeZone: String? = null,
    val flagEmoji: String = "",
    val error: String? = null
)

fun NetworkDetailInfo.primaryLocalIpAddress(): String? {
    return ipv4Addresses.firstOrNull() ?: ipv6Addresses.firstOrNull()
}
