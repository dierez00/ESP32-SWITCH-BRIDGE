package com.switchbridge.controller.network

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class WifiVerification {
    UNAVAILABLE,
    PERMISSION_REQUIRED,
    LOCATION_DISABLED,
    EXPECTED_NETWORK,
    OTHER_NETWORK,
    UNKNOWN_SSID,
}

data class WifiConnectionState(
    val network: Network? = null,
    val connected: Boolean = false,
    val ssid: String? = null,
    val verification: WifiVerification = WifiVerification.UNAVAILABLE,
)

class WifiMonitor(private val context: Context) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val locationManager = context.getSystemService(LocationManager::class.java)
    private val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)
    private val _state = MutableStateFlow(WifiConnectionState())
    val state: StateFlow<WifiConnectionState> = _state.asStateFlow()

    private var registered = false
    private var callback: ConnectivityManager.NetworkCallback? = null

    fun start() {
        if (registered) return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        val newCallback = createCallback()
        callback = newCallback
        try {
            connectivityManager.registerNetworkCallback(request, newCallback)
            registered = true
            refresh()
        } catch (_: SecurityException) {
            _state.value = WifiConnectionState(verification = WifiVerification.PERMISSION_REQUIRED)
        }
    }

    fun stop() {
        val activeCallback = callback ?: return
        if (registered) runCatching { connectivityManager.unregisterNetworkCallback(activeCallback) }
        registered = false
        callback = null
        _state.value = WifiConnectionState()
    }

    fun refresh() {
        val network = currentWifiNetwork() ?: run {
            _state.value = WifiConnectionState()
            return
        }
        updateFrom(network, connectivityManager.getNetworkCapabilities(network))
    }

    private fun createCallback(): ConnectivityManager.NetworkCallback {
        val handleAvailable: (Network) -> Unit = { network ->
            updateFrom(network, connectivityManager.getNetworkCapabilities(network))
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            object : ConnectivityManager.NetworkCallback(
                ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO,
            ) {
                override fun onAvailable(network: Network) = handleAvailable(network)
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    updateFrom(network, capabilities)
                }
                override fun onLost(network: Network) = handleLost(network)
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = handleAvailable(network)
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    updateFrom(network, capabilities)
                }
                override fun onLost(network: Network) = handleLost(network)
            }
        }
    }

    private fun handleLost(network: Network) {
        if (_state.value.network != network) return
        refresh()
    }

    private fun updateFrom(network: Network, capabilities: NetworkCapabilities?) {
        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) return
        val hasPermission = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val locationEnabled = locationManager.isLocationEnabled
        val rawSsid = if (!hasPermission || !locationEnabled) {
            null
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (capabilities.transportInfo as? WifiInfo)?.ssid
        } else {
            @Suppress("DEPRECATION")
            wifiManager.connectionInfo?.ssid
        }
        val ssid = rawSsid
            ?.removeSurrounding("\"")
            ?.takeUnless { it == UNKNOWN_SSID || it.isBlank() }

        val verification = when {
            !hasPermission -> WifiVerification.PERMISSION_REQUIRED
            !locationEnabled -> WifiVerification.LOCATION_DISABLED
            ssid == EXPECTED_SSID -> WifiVerification.EXPECTED_NETWORK
            ssid != null -> WifiVerification.OTHER_NETWORK
            else -> WifiVerification.UNKNOWN_SSID
        }
        _state.value = WifiConnectionState(
            network = network,
            connected = true,
            ssid = ssid,
            verification = verification,
        )
    }

    @Suppress("DEPRECATION")
    private fun currentWifiNetwork(): Network? = connectivityManager.allNetworks.firstOrNull { network ->
        connectivityManager.getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    companion object {
        const val EXPECTED_SSID = "SwitchBridge"
        private const val UNKNOWN_SSID = "<unknown ssid>"
    }
}
