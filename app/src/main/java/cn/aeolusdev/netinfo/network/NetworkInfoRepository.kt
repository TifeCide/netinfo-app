package cn.aeolusdev.netinfo.network

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.CellIdentityNr
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoTdscdma
import android.telephony.CellInfoWcdma
import android.telephony.PhoneStateListener
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import cn.aeolusdev.netinfo.model.ConnectionState
import cn.aeolusdev.netinfo.model.ExternalNetworkInfo
import cn.aeolusdev.netinfo.model.NetworkDetailInfo
import cn.aeolusdev.netinfo.model.NetworkType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.NetworkInterface
import java.net.URL
import java.util.Locale

@SuppressLint("MissingPermission")
class NetworkInfoRepository(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @Suppress("DEPRECATION")
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val baseTelephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()

    private val _networkInfo = MutableStateFlow(NetworkDetailInfo())
    val networkInfo: StateFlow<NetworkDetailInfo> = _networkInfo.asStateFlow()

    private var refreshJob: Job? = null
    private var networkCallbackRegistered = false
    private var telephonyListenerRegistered = false
    @Volatile
    private var isPaused = false
    private var registeredTelephonyManager: TelephonyManager? = null
    private var lastDisplayInfo: TelephonyDisplayInfo? = null

    private val networkCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
            override fun onAvailable(network: android.net.Network) = refresh()
            override fun onLost(network: android.net.Network) = refresh()
            override fun onCapabilitiesChanged(
                network: android.net.Network,
                networkCapabilities: NetworkCapabilities
            ) = refresh()
            override fun onLinkPropertiesChanged(
                network: android.net.Network,
                linkProperties: android.net.LinkProperties
            ) = refresh()
        }
    } else {
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) = refresh()
            override fun onLost(network: android.net.Network) = refresh()
            override fun onCapabilitiesChanged(
                network: android.net.Network,
                networkCapabilities: NetworkCapabilities
            ) = refresh()
            override fun onLinkPropertiesChanged(
                network: android.net.Network,
                linkProperties: android.net.LinkProperties
            ) = refresh()
        }
    }

    @Suppress("DEPRECATION")
    private val legacyPhoneStateListener = object : PhoneStateListener() {
        override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
            lastDisplayInfo = telephonyDisplayInfo
            refresh()
        }
    }

    private val telephonyCallback =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
                override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                    lastDisplayInfo = telephonyDisplayInfo
                    refresh()
                }
            }
        } else {
            null
        }

    fun start() {
        registerNetworkCallback()
        registerTelephonyListener()
        startPeriodicRefresh()
        refresh()
    }

    fun stop() {
        refreshJob?.cancel()
        unregisterNetworkCallback()
        unregisterTelephonyListener()
        scope.coroutineContext.cancelChildren()
    }

    fun refresh() {
        if (isPaused) {
            return
        }

        scope.launch {
            refreshMutex.withLock {
                if (isPaused) {
                    _networkInfo.value = _networkInfo.value.copy(updatesPaused = true)
                    return@withLock
                }

                val latestInfo = loadNetworkInfo()
                _networkInfo.value = latestInfo.copy(updatesPaused = isPaused)
            }
        }
    }

    fun setPaused(paused: Boolean) {
        if (isPaused == paused) {
            return
        }

        isPaused = paused
        _networkInfo.value = _networkInfo.value.copy(updatesPaused = paused)

        if (paused) {
            refreshJob?.cancel()
            unregisterNetworkCallback()
            unregisterTelephonyListener()
        } else {
            registerNetworkCallback()
            registerTelephonyListener()
            startPeriodicRefresh()
            refresh()
        }
    }

    private fun startPeriodicRefresh() {
        refreshJob?.cancel()
        if (isPaused) {
            return
        }

        refreshJob = scope.launch {
            while (isActive) {
                delay(3_000)
                refresh()
            }
        }
    }

    private fun registerNetworkCallback() {
        if (networkCallbackRegistered) {
            return
        }

        val request = NetworkRequest.Builder().build()
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
            networkCallbackRegistered = true
        } catch (_: Exception) {
        }
    }

    private fun unregisterNetworkCallback() {
        if (!networkCallbackRegistered) {
            return
        }

        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {
        } finally {
            networkCallbackRegistered = false
        }
    }

    private fun registerTelephonyListener() {
        if (telephonyListenerRegistered) {
            return
        }

        val telephonyManager = getActiveTelephonyManager()
        registeredTelephonyManager = telephonyManager

        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && telephonyCallback != null -> {
                    telephonyManager.registerTelephonyCallback(
                        context.mainExecutor,
                        telephonyCallback
                    )
                    telephonyListenerRegistered = true
                }

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    @Suppress("DEPRECATION")
                    telephonyManager.listen(
                        legacyPhoneStateListener,
                        PhoneStateListener.LISTEN_DISPLAY_INFO_CHANGED
                    )
                    telephonyListenerRegistered = true
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun unregisterTelephonyListener() {
        if (!telephonyListenerRegistered) {
            return
        }

        val telephonyManager = registeredTelephonyManager ?: getActiveTelephonyManager()
        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && telephonyCallback != null -> {
                    telephonyManager.unregisterTelephonyCallback(telephonyCallback)
                }

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    @Suppress("DEPRECATION")
                    telephonyManager.listen(legacyPhoneStateListener, PhoneStateListener.LISTEN_NONE)
                }
            }
        } catch (_: Exception) {
        } finally {
            telephonyListenerRegistered = false
            registeredTelephonyManager = null
        }
    }

    private suspend fun loadNetworkInfo(): NetworkDetailInfo {
        val activeNetwork = connectivityManager.activeNetwork ?: return disconnectedInfo()
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            ?: return disconnectedInfo()
        val activeLinkData = extractLinkData(connectivityManager.getLinkProperties(activeNetwork))
        val isVpnActive = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        val preferredTransport = resolveNetworkType(capabilities, activeLinkData.interfaceName)
        val displayNetwork = resolveDisplayNetwork(activeNetwork, preferredTransport)

        val networkType = displayNetwork?.networkType
            ?.takeIf { it != NetworkType.UNKNOWN }
            ?: preferredTransport
        val displayCapabilities = displayNetwork?.capabilities ?: capabilities
        val interfaceName = displayNetwork?.interfaceName ?: activeLinkData.interfaceName
        val ipv4List = displayNetwork?.ipv4Addresses ?: activeLinkData.ipv4Addresses
        val ipv6List = displayNetwork?.ipv6Addresses ?: activeLinkData.ipv6Addresses
        val prefixLen = displayNetwork?.prefixLength ?: activeLinkData.prefixLength
        val gateway = displayNetwork?.gateway ?: activeLinkData.gateway
        val dnsServers = displayNetwork?.dnsServers ?: activeLinkData.dnsServers
        val macAddress = displayNetwork?.macAddress ?: getMacAddress(interfaceName)
        val isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

        val localInfo = when (networkType) {
            NetworkType.WIFI -> buildWifiInfo(
                capabilities = displayCapabilities,
                interfaceName = interfaceName,
                ipv4List = ipv4List,
                ipv6List = ipv6List,
                prefixLen = prefixLen,
                gateway = gateway,
                dnsServers = dnsServers,
                macAddress = macAddress,
                isMetered = isMetered
            )

            NetworkType.MOBILE -> buildMobileInfo(
                capabilities = displayCapabilities,
                interfaceName = interfaceName,
                ipv4List = ipv4List,
                ipv6List = ipv6List,
                prefixLen = prefixLen,
                gateway = gateway,
                dnsServers = dnsServers,
                macAddress = macAddress,
                isMetered = isMetered
            )

            NetworkType.ETHERNET -> buildEthernetInfo(
                interfaceName = interfaceName,
                ipv4List = ipv4List,
                ipv6List = ipv6List,
                prefixLen = prefixLen,
                gateway = gateway,
                dnsServers = dnsServers,
                macAddress = macAddress,
                isMetered = isMetered
            )

            NetworkType.UNKNOWN -> NetworkDetailInfo(
                networkType = NetworkType.UNKNOWN,
                interfaceName = interfaceName,
                ipv4Addresses = ipv4List,
                ipv6Addresses = ipv6List,
                prefixLength = prefixLen,
                gateway = gateway,
                dnsServers = dnsServers,
                macAddress = macAddress,
                isMetered = isMetered,
                connectionState = ConnectionState.CONNECTED,
                isVpnActive = isVpnActive
            )
        }

        val externalInfo = fetchExternalInfo(localInfo.externalInfo)
        return localInfo.copy(
            externalInfo = externalInfo,
            isVpnActive = isVpnActive
        )
    }

    private fun disconnectedInfo(): NetworkDetailInfo {
        return _networkInfo.value.copy(
            networkType = NetworkType.UNKNOWN,
            interfaceName = "",
            ipv4Addresses = emptyList(),
            ipv6Addresses = emptyList(),
            prefixLength = 0,
            gateway = null,
            dnsServers = emptyList(),
            macAddress = null,
            isMetered = false,
            connectionState = ConnectionState.DISCONNECTED,
            isVpnActive = false,
            ssid = null,
            bssid = null,
            rssi = null,
            linkSpeedMbps = null,
            frequencyMhz = null,
            channel = null,
            wifiStandard = null,
            carrierName = null,
            mobileNetworkLabel = null,
            cellId = null,
            locationAreaCode = null,
            fiveGMode = null,
            ethernetLinkSpeedMbps = null,
            duplex = null,
            externalInfo = ExternalNetworkInfo(),
            updatesPaused = isPaused
        )
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
        val ssid = rawSsid
            ?.removeSurrounding("\"")
            ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
        val bssid = wifiInfo?.bssid?.takeIf { it != "02:00:00:00:00:00" }
        val rssi = wifiInfo?.rssi
        val linkSpeed = wifiInfo?.linkSpeed?.takeIf { it > 0 }
        val frequency = wifiInfo?.frequency?.takeIf { it > 0 }
        val channel = frequency?.let(::calculateChannel)

        val wifiStandard = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            when (wifiInfo?.wifiStandard) {
                ScanResult.WIFI_STANDARD_11AX -> "Wi-Fi 6"
                ScanResult.WIFI_STANDARD_11AC -> "Wi-Fi 5"
                ScanResult.WIFI_STANDARD_11N -> "Wi-Fi 4"
                ScanResult.WIFI_STANDARD_LEGACY -> "Wi-Fi"
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

    private fun buildMobileInfo(
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
        val telephonyManager = getActiveTelephonyManager()
        val dataNetworkType = try {
            telephonyManager.dataNetworkType
        } catch (_: SecurityException) {
            TelephonyManager.NETWORK_TYPE_UNKNOWN
        }

        val (mobileLabel, fiveGMode) = resolveMobileNetwork(dataNetworkType, lastDisplayInfo)
        val cellDetails = getRegisteredCellDetails(telephonyManager)
        val carrierName = try {
            telephonyManager.networkOperatorName?.takeIf { it.isNotBlank() }
        } catch (_: SecurityException) {
            null
        }

        val bandwidthMbps = capabilities.linkDownstreamBandwidthKbps
            .takeIf { it > 0 }
            ?.div(1000)
            ?.takeIf { it > 0 }

        return NetworkDetailInfo(
            networkType = NetworkType.MOBILE,
            interfaceName = interfaceName,
            ipv4Addresses = ipv4List,
            ipv6Addresses = ipv6List,
            prefixLength = prefixLen,
            gateway = gateway,
            dnsServers = dnsServers,
            macAddress = macAddress,
            isMetered = isMetered,
            connectionState = ConnectionState.CONNECTED,
            linkSpeedMbps = bandwidthMbps,
            carrierName = carrierName,
            mobileNetworkLabel = mobileLabel,
            cellId = cellDetails.cellId,
            locationAreaCode = cellDetails.locationAreaCode,
            fiveGMode = fiveGMode
        )
    }

    private fun buildEthernetInfo(
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
            connectionState = ConnectionState.CONNECTED
        )
    }

    private fun getRegisteredCellDetails(telephonyManager: TelephonyManager): CellDetails {
        if (!hasLocationPermission()) {
            return CellDetails()
        }

        val registeredCell = try {
            telephonyManager.allCellInfo
                ?.firstOrNull { it.isRegistered }
                ?: telephonyManager.allCellInfo?.firstOrNull()
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        } ?: return CellDetails()

        return when {
            registeredCell is CellInfoGsm -> CellDetails(
                cellId = registeredCell.cellIdentity.cid.takeIfAvailable()?.toString(),
                locationAreaCode = registeredCell.cellIdentity.lac.takeIfAvailable()?.toString()
            )

            registeredCell is CellInfoWcdma -> CellDetails(
                cellId = registeredCell.cellIdentity.cid.takeIfAvailable()?.toString(),
                locationAreaCode = registeredCell.cellIdentity.lac.takeIfAvailable()?.toString()
            )

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && registeredCell is CellInfoTdscdma -> CellDetails(
                cellId = registeredCell.cellIdentity.cid.takeIfAvailable()?.toString(),
                locationAreaCode = registeredCell.cellIdentity.lac.takeIfAvailable()?.toString()
            )

            registeredCell is CellInfoLte -> CellDetails(
                cellId = registeredCell.cellIdentity.ci.takeIfAvailable()?.toString(),
                locationAreaCode = registeredCell.cellIdentity.tac.takeIfAvailable()?.toString()
            )

            registeredCell is CellInfoCdma -> CellDetails(
                cellId = registeredCell.cellIdentity.basestationId.takeIfAvailable()?.toString(),
                locationAreaCode = registeredCell.cellIdentity.networkId.takeIfAvailable()?.toString()
            )

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && registeredCell is CellInfoNr -> {
                val cellIdentity = registeredCell.cellIdentity as CellIdentityNr
                CellDetails(
                    cellId = cellIdentity.nci.takeIfAvailable()?.toString(),
                    locationAreaCode = cellIdentity.tac.takeIfAvailable()?.toString()
                )
            }

            else -> CellDetails()
        }
    }

    private fun resolveMobileNetwork(
        dataNetworkType: Int,
        displayInfo: TelephonyDisplayInfo?
    ): Pair<String?, String?> {
        if (dataNetworkType == TelephonyManager.NETWORK_TYPE_NR) {
            return "5G" to "SA"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            when (displayInfo?.overrideNetworkType) {
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
                TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> {
                    return "5G" to "NSA"
                }
            }
        }

        val label = when (dataNetworkType) {
            TelephonyManager.NETWORK_TYPE_LTE,
            TelephonyManager.NETWORK_TYPE_IWLAN -> "4G LTE"

            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_EHRPD,
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_EVDO_B,
            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSPA -> "3G"

            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_1xRTT,
            TelephonyManager.NETWORK_TYPE_IDEN,
            TelephonyManager.NETWORK_TYPE_GSM -> "2G"

            else -> null
        }

        return label to null
    }

    private fun fetchExternalInfo(previous: ExternalNetworkInfo): ExternalNetworkInfo {
        return try {
            val connection = (URL("https://ipinfo.io/json").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4_000
                readTimeout = 4_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "NetInfo/1.0")
            }

            connection.inputStream.bufferedReader().use { reader ->
                val json = JSONObject(reader.readText())
                val country = json.optString("country").takeIf { it.isNotBlank() }
                ExternalNetworkInfo(
                    ip = json.optString("ip").takeIf { it.isNotBlank() },
                    city = json.optString("city").takeIf { it.isNotBlank() },
                    region = json.optString("region").takeIf { it.isNotBlank() },
                    country = country,
                    org = json.optString("org").takeIf { it.isNotBlank() },
                    timeZone = json.optString("timezone").takeIf { it.isNotBlank() },
                    flagEmoji = countryToFlagEmoji(country),
                    error = null
                )
            }
        } catch (e: Exception) {
            previous.copy(error = e.message ?: "获取失败")
        }
    }

    private fun getActiveTelephonyManager(): TelephonyManager {
        val dataSubscriptionId = SubscriptionManager.getDefaultDataSubscriptionId()
        return if (dataSubscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            baseTelephonyManager.createForSubscriptionId(dataSubscriptionId)
        } else {
            baseTelephonyManager
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun getMacAddress(interfaceName: String): String? {
        return try {
            if (interfaceName.isBlank()) {
                null
            } else {
                val networkInterface = NetworkInterface.getByName(interfaceName) ?: return null
                val hardwareAddress = networkInterface.hardwareAddress ?: return null
                hardwareAddress.joinToString(":") { byte -> "%02X".format(byte) }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractLinkData(linkProperties: android.net.LinkProperties?): LinkData {
        if (linkProperties == null) {
            return LinkData()
        }

        val ipv4List = mutableListOf<String>()
        val ipv6List = mutableListOf<String>()
        var prefixLen = 0

        linkProperties.linkAddresses.forEach { linkAddress ->
            val hostAddress = linkAddress.address.hostAddress ?: return@forEach
            if (linkAddress.address.address.size == 4) {
                ipv4List += hostAddress
                prefixLen = linkAddress.prefixLength
            } else if (!hostAddress.startsWith("fe80", ignoreCase = true)) {
                ipv6List += hostAddress
            }
        }

        val gateway = linkProperties.routes
            .firstOrNull { it.isDefaultRoute && it.gateway != null }
            ?.gateway
            ?.hostAddress

        val dnsServers = linkProperties.dnsServers
            .mapNotNull { it.hostAddress }

        return LinkData(
            interfaceName = linkProperties.interfaceName.orEmpty(),
            ipv4Addresses = ipv4List,
            ipv6Addresses = ipv6List,
            prefixLength = prefixLen,
            gateway = gateway,
            dnsServers = dnsServers
        )
    }

    private fun resolveDisplayNetwork(
        activeNetwork: android.net.Network,
        preferredTransport: NetworkType
    ): DisplayNetworkSnapshot? {
        return connectivityManager.allNetworks
            .mapNotNull(::snapshotNetwork)
            .filter(::isPhysicalDisplayCandidate)
            .sortedWith(
                compareBy<DisplayNetworkSnapshot>(
                    { transportMismatchRank(it.networkType, preferredTransport) },
                    { if (it.isValidated) 0 else 1 },
                    { if (it.hasInternet) 0 else 1 },
                    { interfaceFamilyPriority(it.interfaceName) },
                    { if (it.ipv4Addresses.isNotEmpty()) 0 else 1 },
                    { if (it.network == activeNetwork) 0 else 1 },
                    { it.interfaceName.lowercase(Locale.US) }
                )
            )
            .firstOrNull()
    }

    private fun snapshotNetwork(network: android.net.Network): DisplayNetworkSnapshot? {
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return null
        val linkData = extractLinkData(connectivityManager.getLinkProperties(network))
        val interfaceName = linkData.interfaceName

        return DisplayNetworkSnapshot(
            network = network,
            capabilities = capabilities,
            interfaceName = interfaceName,
            networkType = resolveNetworkType(capabilities, interfaceName),
            isVpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
            hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            ipv4Addresses = linkData.ipv4Addresses,
            ipv6Addresses = linkData.ipv6Addresses,
            prefixLength = linkData.prefixLength,
            gateway = linkData.gateway,
            dnsServers = linkData.dnsServers,
            macAddress = getMacAddress(interfaceName)
        )
    }

    private fun isPhysicalDisplayCandidate(snapshot: DisplayNetworkSnapshot): Boolean {
        if (snapshot.interfaceName.isBlank() || snapshot.isVpn || isVirtualInterfaceName(snapshot.interfaceName)) {
            return false
        }

        if (snapshot.ipv4Addresses.isEmpty() && snapshot.ipv6Addresses.isEmpty()) {
            return false
        }

        if (snapshot.networkType == NetworkType.UNKNOWN && interfaceFamilyPriority(snapshot.interfaceName) >= 3) {
            return false
        }

        val networkInterface = try {
            NetworkInterface.getByName(snapshot.interfaceName)
        } catch (_: Exception) {
            null
        } ?: return true

        val isUp = runCatching { networkInterface.isUp }.getOrDefault(true)
        val isLoopback = runCatching { networkInterface.isLoopback }.getOrDefault(false)
        val isVirtual = runCatching { networkInterface.isVirtual }.getOrDefault(false)
        return isUp && !isLoopback && !isVirtual
    }

    private fun transportMismatchRank(
        candidateType: NetworkType,
        preferredTransport: NetworkType
    ): Int {
        return if (preferredTransport == NetworkType.UNKNOWN) {
            0
        } else if (candidateType == preferredTransport) {
            0
        } else {
            1
        }
    }

    private fun resolveNetworkType(
        capabilities: NetworkCapabilities?,
        interfaceName: String
    ): NetworkType {
        return when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> NetworkType.WIFI
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> NetworkType.MOBILE
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> NetworkType.ETHERNET
            else -> networkTypeFromInterfaceName(interfaceName)
        }
    }

    private fun networkTypeFromInterfaceName(interfaceName: String): NetworkType {
        return when (interfaceName.lowercase(Locale.US)) {
            in setOf("wlan", "wifi") -> NetworkType.WIFI
            else -> when {
                interfaceName.startsWith("wlan", ignoreCase = true) ||
                    interfaceName.startsWith("wifi", ignoreCase = true) -> NetworkType.WIFI

                interfaceName.startsWith("rmnet", ignoreCase = true) ||
                    interfaceName.startsWith("ccmni", ignoreCase = true) ||
                    interfaceName.startsWith("wwan", ignoreCase = true) -> NetworkType.MOBILE

                interfaceName.startsWith("eth", ignoreCase = true) ||
                    interfaceName.startsWith("en", ignoreCase = true) -> NetworkType.ETHERNET

                else -> NetworkType.UNKNOWN
            }
        }
    }

    private fun interfaceFamilyPriority(interfaceName: String): Int {
        return when {
            interfaceName.startsWith("wlan", ignoreCase = true) ||
                interfaceName.startsWith("wifi", ignoreCase = true) -> 0

            interfaceName.startsWith("rmnet", ignoreCase = true) ||
                interfaceName.startsWith("ccmni", ignoreCase = true) ||
                interfaceName.startsWith("wwan", ignoreCase = true) -> 1

            interfaceName.startsWith("eth", ignoreCase = true) ||
                interfaceName.startsWith("en", ignoreCase = true) -> 2

            else -> 3
        }
    }

    private fun isVirtualInterfaceName(interfaceName: String): Boolean {
        return when {
            interfaceName.startsWith("tun", ignoreCase = true) -> true
            interfaceName.startsWith("ppp", ignoreCase = true) -> true
            interfaceName.startsWith("tap", ignoreCase = true) -> true
            interfaceName.startsWith("utun", ignoreCase = true) -> true
            interfaceName.startsWith("ipsec", ignoreCase = true) -> true
            interfaceName.startsWith("wg", ignoreCase = true) -> true
            interfaceName.startsWith("clat", ignoreCase = true) -> true
            else -> false
        }
    }

    private fun calculateChannel(frequencyMhz: Int): Int {
        return when {
            frequencyMhz == 2484 -> 14
            frequencyMhz in 2412..2472 -> (frequencyMhz - 2412) / 5 + 1
            frequencyMhz in 5170..5825 -> (frequencyMhz - 5170) / 5 + 34
            frequencyMhz in 5955..7115 -> (frequencyMhz - 5955) / 5 + 1
            else -> 0
        }
    }

    private fun inferWifiStandard(frequencyMhz: Int?): String? {
        return when {
            frequencyMhz == null -> null
            frequencyMhz > 5900 -> "Wi-Fi 6E"
            frequencyMhz > 4900 -> "Wi-Fi 5"
            frequencyMhz in 2400..2500 -> "Wi-Fi 4"
            else -> null
        }
    }

    private fun countryToFlagEmoji(countryCode: String?): String {
        if (countryCode.isNullOrBlank() || countryCode.length != 2) {
            return ""
        }

        return countryCode.uppercase(Locale.US)
            .map { char -> String(Character.toChars(char.code - 65 + 0x1F1E6)) }
            .joinToString("")
    }

    private fun Int.takeIfAvailable(): Int? {
        return takeIf { it != Integer.MAX_VALUE && it >= 0 }
    }

    private fun Long.takeIfAvailable(): Long? {
        return takeIf { it != Long.MAX_VALUE && it >= 0L }
    }

    private data class CellDetails(
        val cellId: String? = null,
        val locationAreaCode: String? = null
    )

    private data class LinkData(
        val interfaceName: String = "",
        val ipv4Addresses: List<String> = emptyList(),
        val ipv6Addresses: List<String> = emptyList(),
        val prefixLength: Int = 0,
        val gateway: String? = null,
        val dnsServers: List<String> = emptyList()
    )

    private data class DisplayNetworkSnapshot(
        val network: android.net.Network,
        val capabilities: NetworkCapabilities,
        val interfaceName: String,
        val networkType: NetworkType,
        val isVpn: Boolean,
        val hasInternet: Boolean,
        val isValidated: Boolean,
        val ipv4Addresses: List<String>,
        val ipv6Addresses: List<String>,
        val prefixLength: Int,
        val gateway: String?,
        val dnsServers: List<String>,
        val macAddress: String?
    )
}
