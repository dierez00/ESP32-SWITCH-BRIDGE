package com.switchbridge.controller

import android.app.Application
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.lifecycle.AndroidViewModel
import com.switchbridge.controller.domain.ControllerTuning
import com.switchbridge.controller.domain.Endpoint
import com.switchbridge.controller.domain.RawGamepadState
import com.switchbridge.controller.domain.StickSide
import com.switchbridge.controller.domain.StickTuning
import com.switchbridge.controller.domain.SwitchControllerState
import com.switchbridge.controller.network.TransportStatus
import com.switchbridge.controller.network.WifiConnectionState
import com.switchbridge.controller.network.WifiVerification
import kotlinx.coroutines.flow.StateFlow

data class EndpointInput(
    val address: String = DEFAULT_ADDRESS,
    val port: String = DEFAULT_PORT.toString(),
) {
    val parsed: Endpoint?
        get() {
            val octets = address.split('.')
            val validAddress = octets.size == 4 && octets.all { part ->
                part.isNotEmpty() && part.length <= 3 &&
                    part.toIntOrNull()?.let { it in 0..255 } == true
            }
            val parsedPort = port.toIntOrNull()
            return if (validAddress && parsedPort?.let { it in 1..65_535 } == true) {
                Endpoint(address, parsedPort)
            } else {
                null
            }
        }

    companion object {
        const val DEFAULT_ADDRESS = "192.168.4.1"
        const val DEFAULT_PORT = 4210
    }
}

data class BridgeUiState(
    val rawGamepad: RawGamepadState = RawGamepadState(),
    val switchState: SwitchControllerState = SwitchControllerState.Neutral,
    val transport: TransportStatus = TransportStatus(),
    val wifi: WifiConnectionState = WifiConnectionState(),
    val endpoint: EndpointInput = EndpointInput(),
    val tuning: ControllerTuning = ControllerTuning.Default,
    val notice: String? = null,
    val inputFocused: Boolean = false,
    val overlayAllowed: Boolean = false,
) {
    val endpointValid: Boolean get() = endpoint.parsed != null
}

/** Bridges the UI and [BridgeController], which lives as long as the process. */
class BridgeViewModel(application: Application) : AndroidViewModel(application) {
    private val controller = BridgeController.get(application)

    val uiState: StateFlow<BridgeUiState> = controller.uiState

    fun onActivityStarted() = controller.onAppStarted()

    fun onActivityStopped() = controller.onAppStopped()

    fun onWindowFocusChanged(hasFocus: Boolean) =
        controller.onInputFocusChanged(InputOwner.ACTIVITY, hasFocus)

    fun onKeyEvent(event: KeyEvent): Boolean = controller.onKeyEvent(event)

    fun onMotionEvent(event: MotionEvent): Boolean = controller.onMotionEvent(event)

    fun updateAddress(value: String) = controller.updateAddress(value)

    fun updatePort(value: String) = controller.updatePort(value)

    fun updateTuning(stick: StickSide, transform: (StickTuning) -> StickTuning) =
        controller.updateTuning(stick, transform)

    fun resetTuning(stick: StickSide) = controller.resetTuning(stick)

    fun refreshWifi() = controller.refreshWifi()

    fun startBridge() = controller.startBridge()

    fun stopBridge() = controller.stopBridge()
}

fun WifiConnectionState.description(): String = when (verification) {
    WifiVerification.UNAVAILABLE -> "No Wi-Fi"
    WifiVerification.PERMISSION_REQUIRED -> "Permission required to verify SSID"
    WifiVerification.LOCATION_DISABLED -> "Enable location to verify SSID"
    WifiVerification.EXPECTED_NETWORK -> "Connected to SwitchBridge"
    WifiVerification.OTHER_NETWORK -> "Current network: ${ssid ?: "unknown"}"
    WifiVerification.UNKNOWN_SSID -> "Wi-Fi connected; SSID cannot be verified"
}
