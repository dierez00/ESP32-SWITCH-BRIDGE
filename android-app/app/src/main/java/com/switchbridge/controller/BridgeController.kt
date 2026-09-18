package com.switchbridge.controller

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.core.content.edit
import com.switchbridge.controller.domain.ControllerSnapshot
import com.switchbridge.controller.domain.ControllerTuning
import com.switchbridge.controller.domain.StickSide
import com.switchbridge.controller.domain.StickTuning
import com.switchbridge.controller.domain.SwitchControllerState
import com.switchbridge.controller.input.GamepadInputManager
import com.switchbridge.controller.mapping.GamepadMapper
import com.switchbridge.controller.network.UdpBridge
import com.switchbridge.controller.network.WifiMonitor
import com.switchbridge.controller.service.BridgeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Who holds gamepad input focus. Android only delivers events to the focused window. */
enum class InputOwner {
    ACTIVITY,
    OVERLAY,
}

/**
 * Process-scoped bridge state shared by the Activity and [BridgeService], so the UDP session
 * survives leaving the app. Everything is used from the main thread.
 */
class BridgeController private constructor(context: Context) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val preferences = app.getSharedPreferences(PREFERENCES, 0)
    private val udpBridge = UdpBridge()
    private val snapshots = MutableStateFlow(ControllerSnapshot())
    private val inputManager = GamepadInputManager(app) {
        snapshots.value = ControllerSnapshot()
        udpBridge.sendImmediateNeutral()
    }
    private val wifiMonitor = WifiMonitor(app)
    private val endpoint = MutableStateFlow(
        EndpointInput(
            address = preferences.getString(KEY_ADDRESS, EndpointInput.DEFAULT_ADDRESS)
                ?: EndpointInput.DEFAULT_ADDRESS,
            port = preferences.getInt(KEY_PORT, EndpointInput.DEFAULT_PORT).toString(),
        ),
    )
    private val notice = MutableStateFlow<String?>(null)
    private val tuning = MutableStateFlow(loadTuning())
    private var pendingTuningSave: Job? = null
    private val focusOwners = MutableStateFlow<Set<InputOwner>>(emptySet())
    private val overlayAllowed = MutableStateFlow(Settings.canDrawOverlays(app))
    private val _appVisible = MutableStateFlow(false)

    val appVisible: StateFlow<Boolean> = _appVisible.asStateFlow()
    val bridgeRunning: StateFlow<Boolean> = udpBridge.status
        .map { it.running }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, false)
    private val inputFocused = focusOwners
        .map { it.isNotEmpty() }
        .distinctUntilChanged()

    private val sentInput = combine(inputManager.state, tuning, inputFocused) { raw, currentTuning, focused ->
        Triple(raw, currentTuning, if (focused) GamepadMapper.map(raw, currentTuning) else SwitchControllerState.Neutral)
    }
    private val backgroundState = combine(notice, inputFocused, overlayAllowed) { currentNotice, focused, overlay ->
        Triple(currentNotice, focused, overlay)
    }

    val uiState: StateFlow<BridgeUiState> = combine(
        sentInput,
        udpBridge.status,
        wifiMonitor.state,
        endpoint,
        backgroundState,
    ) { (raw, currentTuning, report), transport, wifi, endpointInput, (currentNotice, focused, overlay) ->
        BridgeUiState(
            rawGamepad = raw,
            switchState = report,
            transport = transport,
            wifi = wifi,
            endpoint = endpointInput,
            tuning = currentTuning,
            notice = currentNotice,
            inputFocused = focused,
            overlayAllowed = overlay,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = BridgeUiState(endpoint = endpoint.value, tuning = tuning.value),
    )

    init {
        scope.launch {
            sentInput.collect { (raw, _, report) ->
                snapshots.value = ControllerSnapshot(raw.connected, report)
            }
        }
        scope.launch {
            bridgeRunning.collect { running ->
                if (!running && !_appVisible.value) stopMonitors()
            }
        }
    }

    fun onAppStarted() {
        _appVisible.value = true
        overlayAllowed.value = Settings.canDrawOverlays(app)
        inputManager.start()
        wifiMonitor.start()
    }

    fun onAppStopped() {
        _appVisible.value = false
        flushTuningSave()
        // While the bridge is active, the service keeps reading the gamepad and shows the floating bubble.
        if (!udpBridge.status.value.running) stopMonitors()
    }

    fun onInputFocusChanged(owner: InputOwner, hasFocus: Boolean) {
        focusOwners.update { if (hasFocus) it + owner else it - owner }
        // Without focus, key-up events never arrive: drop held buttons so none get stuck.
        if (focusOwners.value.isEmpty()) inputManager.clearInputs()
    }

    fun onKeyEvent(event: KeyEvent): Boolean = inputManager.onKeyEvent(event)

    fun onMotionEvent(event: MotionEvent): Boolean = inputManager.onMotionEvent(event)

    fun updateAddress(value: String) {
        if (udpBridge.status.value.running) return
        endpoint.value = endpoint.value.copy(address = value.trim())
        notice.value = null
    }

    fun updatePort(value: String) {
        if (udpBridge.status.value.running) return
        endpoint.value = endpoint.value.copy(port = value.filter(Char::isDigit).take(5))
        notice.value = null
    }

    fun updateTuning(stick: StickSide, transform: (StickTuning) -> StickTuning) {
        tuning.update { current -> current.with(stick, transform(current[stick]).sanitized()) }
        scheduleTuningSave()
    }

    fun resetTuning(stick: StickSide) {
        updateTuning(stick) { StickTuning.Default }
    }

    fun refreshWifi() {
        wifiMonitor.refresh()
    }

    fun startBridge() {
        if (udpBridge.status.value.running) return
        val parsedEndpoint = endpoint.value.parsed ?: run {
            notice.value = "Check the IPv4 address and port."
            return
        }
        val wifiNetwork = wifiMonitor.state.value.network ?: run {
            notice.value = "Connect the phone to a Wi-Fi network before starting."
            return
        }
        preferences.edit {
            putString(KEY_ADDRESS, parsedEndpoint.address)
            putInt(KEY_PORT, parsedEndpoint.port)
        }
        notice.value = null
        scope.launch {
            if (!udpBridge.start(wifiNetwork, parsedEndpoint, snapshots)) {
                notice.value = udpBridge.status.value.lastError ?: "Could not open the UDP bridge."
                return@launch
            }
            if (!BridgeService.start(app)) {
                notice.value = "Could not start the background service; keep the app open."
            }
        }
    }

    fun stopBridge() {
        udpBridge.requestStopWithNeutral()
    }

    private fun stopMonitors() {
        inputManager.stop(resetState = false)
        wifiMonitor.stop()
        focusOwners.value = emptySet()
    }

    private fun scheduleTuningSave() {
        pendingTuningSave?.cancel()
        pendingTuningSave = scope.launch {
            delay(TUNING_SAVE_DEBOUNCE_MS)
            saveTuning(tuning.value)
            pendingTuningSave = null
        }
    }

    private fun flushTuningSave() {
        val pending = pendingTuningSave ?: return
        pending.cancel()
        pendingTuningSave = null
        saveTuning(tuning.value)
    }

    private fun loadTuning(): ControllerTuning = ControllerTuning(
        left = loadStickTuning(StickSide.LEFT),
        right = loadStickTuning(StickSide.RIGHT),
    )

    private fun loadStickTuning(side: StickSide): StickTuning {
        val default = StickTuning.Default
        return StickTuning(
            innerDeadZone = preferences.getFloat(tuningKey(side, KEY_INNER), default.innerDeadZone),
            outerDeadZone = preferences.getFloat(tuningKey(side, KEY_OUTER), default.outerDeadZone),
            exponent = preferences.getFloat(tuningKey(side, KEY_EXPONENT), default.exponent),
            gain = preferences.getFloat(tuningKey(side, KEY_GAIN), default.gain),
            invertY = preferences.getBoolean(tuningKey(side, KEY_INVERT_Y), default.invertY),
        ).sanitized()
    }

    // apply() updates the in-memory cache and writes to disk in the background.
    private fun saveTuning(value: ControllerTuning) {
        preferences.edit {
            StickSide.entries.forEach { side ->
                val stick = value[side]
                putFloat(tuningKey(side, KEY_INNER), stick.innerDeadZone)
                putFloat(tuningKey(side, KEY_OUTER), stick.outerDeadZone)
                putFloat(tuningKey(side, KEY_EXPONENT), stick.exponent)
                putFloat(tuningKey(side, KEY_GAIN), stick.gain)
                putBoolean(tuningKey(side, KEY_INVERT_Y), stick.invertY)
            }
        }
    }

    companion object {
        private const val PREFERENCES = "switch_bridge_settings"
        private const val KEY_ADDRESS = "esp_address"
        private const val KEY_PORT = "esp_port"
        private const val KEY_INNER = "inner_dead_zone"
        private const val KEY_OUTER = "outer_dead_zone"
        private const val KEY_EXPONENT = "exponent"
        private const val KEY_GAIN = "gain"
        private const val KEY_INVERT_Y = "invert_y"
        private const val TUNING_SAVE_DEBOUNCE_MS = 300L

        // Only holds the application context, which lives as long as the process.
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: BridgeController? = null

        fun get(context: Context): BridgeController =
            instance ?: synchronized(this) {
                instance ?: BridgeController(context).also { instance = it }
            }

        private fun tuningKey(side: StickSide, parameter: String): String =
            "tuning_${side.name.lowercase()}_$parameter"
    }
}
