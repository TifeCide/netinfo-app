package cn.aeolusdev.netinfo.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import cn.aeolusdev.netinfo.model.ConnectionState
import cn.aeolusdev.netinfo.model.NetworkDetailInfo
import cn.aeolusdev.netinfo.model.NetworkType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.NetworkInterface

@SuppressLint("MissingPermission")
class NetworkInfoRepository(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @Suppress("DEPRECATION")
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val _networkInfo = MutableStateFlow(NetworkDetailInfo())
    val networkInfo: StateFlow<NetworkDetailInfo> = _networkInfo.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            FLAG_INCLUDE_LOCATION_INFO
        else
            0
    ) {
        override fun onAvailable(network: Network) {
            refreshNetworkInfo(network)
        }

        override fun onLost(network: Network) {
            _networkInfo.value = NetworkDetailInfo(connectionState = ConnectionState.DISCONNECTED)
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            refreshNetworkInfo(network, networkCapabilities)
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            refreshNetworkInfo(network)
        }
    }

    fun start() {
        val request = NetworkRequest.Builder().build()
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // Immediately populate current state
        connectivityManager.activeNetwork?.let { refreshNetworkInfo(it) }
            ?: run {
                _networkInfo.value = NetworkDetailInfo(connectionState = ConnectionState.DISCONNECTED)
            }
    }

    fun stop() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun refresh() {
        connectivityManager.activeNetwork?.let { refreshNetworkInfo(it) }
            ?: run {
                _networkInfo.value = NetworkDetailInfo(connectionState = ConnectionState.DISCONNECTED)
            }
    }

    private fun refreshNetworkInfo(
        network: Network,
        caps: NetworkCapabilities? = null
    ) {
        val capabilities = caps ?: connectivityManager.getNetworkCapabilities(network)
        val linkProperties = connectivityManager.getLinkProperties(network)

        if (capabilities == null) {
            _networkInfo.value = NetworkDetailInfo(connectionState = ConnectionState.DISCONNECTED)
            return
        }

        val networkType = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
            else -> NetworkType.UNKNOWN
        }

        val isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

        // Parse LinkProperties
        val interfaceName = linkProperties?.interfaceName ?: ""
        val ipv4List = mutableListOf<String>()
        val ipv6List = mutableListOf<String>()
        var prefixLen = 0

        linkProperties?.linkAddresses?.forEach { linkAddr ->
            val addrStr = linkAddr.address.hostAddress ?: return@forEach
            if (linkAddr.address.address.size == 4) {
                ipv4List.add(addrStr)
                prefixLen = linkAddr.prefixLength
            } else {
                if (!addrStr.startsWith("fe80", ignoreCase = true)) {
                    ipv6List.add(addrStr)
                }
            }
        }

        val gateway = linkProperties?.routes
            ?.firstOrNull { it.isDefaultRoute && it.gateway != null }
            ?.gateway?.hostAddress

        val dnsServers = linkProperties?.dnsServers
            ?.mapNotNull { it.hostAddress }
            ?: emptyList()

        val macAddress = getMacAddress(interfaceName)

        val info = when (networkType) {
            NetworkType.WIFI -> buildWifiInfo(
                capabilities, interfaceName, ipv4List, ipv6List,
                prefixLen, gateway, dnsServers, macAddress, isMetered
            )
            NetworkType.ETHERNET -> buildEthernetInfo(
                capabilities, interfaceName, ipv4List, ipv6List,
                prefixLen, gateway, dnsServers, macAddress, isMetered
            )
            else -> NetworkDetailInfo(
                networkType = NetworkType.UNKNOWN,
                interfaceName = interfaceName,
                ipv4Addresses = ipv4List,
                ipv6Addresses = ipv6List,
                prefixLength = prefixLen,
                gateway = gateway,
                dnsServers = dnsServers,
                macAddress = macAddress,
                isMetered = isMetered,
                connectionState = ConnectionState.CONNECTED
            )
        }

        _networkInfo.value = info
    }

    @SuppressLint("MissingPermission")
    private fun buildWifiInfo(
        capabilities: NetworkCapabilities,
        interfaceName: String,
        ipv4List: List<String>,
        ipv6List: List<String>,
        prefixLen: Int,
        gateway: String?,
        dnsServers: List<String>,
        macAddress: String?,
        isMetered: Boolean
    ): NetworkDetailInfo {
        val wifiInfo: WifiInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            capabilities.transportInfo as? WifiInfo
        } else {
            @Suppress("DEPRECATION")
            wifiManager.connectionInfo
        }

        val rawSsid = wifiInfo?.ssid
        val ssid = rawSsid?.removeSurrounding("\"")?.takeIf { it.isNotEmpty() && it != "<unknown ssid>" }
        val bssid = wifiInfo?.bssid?.takeIf { it != "02:00:00:00:00:00" }
        val rssi = wifiInfo?.rssi
        val linkSpeed = wifiInfo?.linkSpeed?.takeIf { it > 0 }
        val frequency = wifiInfo?.frequency?.takeIf { it > 0 }
        val channel = frequency?.let { calculateChannel(it) }

        val wifiStandard: String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            when (wifiInfo?.wifiStandard) {
                ScanResult.WIFI_STANDARD_11AX -> "Wi-Fi 6 (802.11ax)"
                ScanResult.WIFI_STANDARD_11AC -> "Wi-Fi 5 (802.11ac)"
                ScanResult.WIFI_STANDARD_11N -> "Wi-Fi 4 (802.11n)"
                ScanResult.WIFI_STANDARD_LEGACY -> "Wi-Fi (802.11a/b/g)"
                else -> inferWifiStandard(frequency)
            }
        } else {
            inferWifiStandard(frequency)
        }

        return NetworkDetailInfo(
            networkType = NetworkType.WIFI,
            interfaceName = interfaceName,
            ipv4Addresses = ipv4List,
            ipv6Addresses = ipv6List,
            prefixLength = prefixLen,
            gateway = gateway,
            dnsServers = dnsServers,
            macAddress = macAddress,
            isMetered = isMetered,
            connectionState = ConnectionState.CONNECTED,
            ssid = ssid,
            bssid = bssid,
            rssi = rssi,
            linkSpeedMbps = linkSpeed,
            frequencyMhz = frequency,
            channel = channel,
            wifiStandard = wifiStandard
        )
    }

    private fun buildEthernetInfo(
        capabilities: NetworkCapabilities,
        interfaceName: String,
        ipv4List: List<String>,
        ipv6List: List<String>,
        prefixLen: Int,
        gateway: String?,
        dnsServers: List<String>,
        macAddress: String?,
        isMetered: Boolean
    ): NetworkDetailInfo {
        return NetworkDetailInfo(
            networkType = NetworkType.ETHERNET,
            interfaceName = interfaceName,
            ipv4Addresses = ipv4List,
            ipv6Addresses = ipv6List,
            prefixLength = prefixLen,
            gateway = gateway,
            dnsServers = dnsServers,
            macAddress = macAddress,
            isMetered = isMetered,
            connectionState = ConnectionState.CONNECTED,
            ethernetLinkSpeedMbps = null,
            duplex = null
        )
    }

    private fun getMacAddress(interfaceName: String): String? {
        return try {
            if (interfaceName.isEmpty()) return null
            val networkInterface = NetworkInterface.getByName(interfaceName) ?: return null
            val hardwareAddress = networkInterface.hardwareAddress ?: return null
            hardwareAddress.joinToString(":") { byte -> "%02X".format(byte) }
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateChannel(frequencyMhz: Int): Int {
        return when {
            frequencyMhz in 2412..2484 -> {
                if (frequencyMhz == 2484) 14
                else (frequencyMhz - 2412) / 5 + 1
            }
            frequencyMhz in 5170..5825 -> (frequencyMhz - 5170) / 5 + 34
            frequencyMhz in 5955..7115 -> (frequencyMhz - 5955) / 5 + 1
            else -> 0
        }
    }

    private fun inferWifiStandard(frequencyMhz: Int?): String? {
        return when {
            frequencyMhz == null -> null
            frequencyMhz > 5900 -> "Wi-Fi 6E (802.11ax)"
            frequencyMhz > 4900 -> "Wi-Fi 5/6 (5GHz)"
            frequencyMhz in 2400..2500 -> "Wi-Fi 4/5 (2.4GHz)"
            else -> null
        }
    }
}
