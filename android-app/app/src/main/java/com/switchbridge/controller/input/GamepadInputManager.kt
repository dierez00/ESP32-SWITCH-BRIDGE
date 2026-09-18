package com.switchbridge.controller.input

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.switchbridge.controller.domain.AxisDiagnostic
import com.switchbridge.controller.domain.KeyDiagnostic
import com.switchbridge.controller.domain.NormalizedAxes
import com.switchbridge.controller.domain.RangeSpec
import com.switchbridge.controller.domain.RawGamepadState
import com.switchbridge.controller.mapping.AxisNormalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GamepadInputManager(
    context: Context,
    private val onSelectedDeviceDisconnected: () -> Unit = {},
) : InputManager.InputDeviceListener {
    private val inputManager = context.getSystemService(InputManager::class.java)
    private val _state = MutableStateFlow(RawGamepadState())
    val state: StateFlow<RawGamepadState> = _state.asStateFlow()

    private var listening = false

    fun start() {
        if (listening) return
        listening = true
        inputManager.registerInputDeviceListener(this, null)
        selectFirstAvailable()
    }

    fun stop(resetState: Boolean = true) {
        if (!listening) return
        listening = false
        inputManager.unregisterInputDeviceListener(this)
        if (resetState) _state.value = RawGamepadState()
    }

    /** Releases buttons and centers axes while keeping the selected gamepad. */
    fun clearInputs() {
        _state.value = _state.value.copy(pressedKeys = emptySet(), axes = NormalizedAxes())
    }

    fun onKeyEvent(event: KeyEvent): Boolean {
        val device = event.device ?: return false
        if (!isGameController(device)) return false
        if (!ensureSelected(device)) return false

        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount > 0) return true
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return true

        val pressed = event.action == KeyEvent.ACTION_DOWN
        val current = _state.value
        val keys = current.pressedKeys.toMutableSet().apply {
            if (pressed) add(event.keyCode) else remove(event.keyCode)
        }
        val diagnostic = KeyDiagnostic(
            keyCode = event.keyCode,
            name = KeyEvent.keyCodeToString(event.keyCode),
            pressed = pressed,
        )
        _state.value = current.copy(
            pressedKeys = keys,
            recentKeys = (listOf(diagnostic) + current.recentKeys).take(MAX_RECENT_KEYS),
        )
        return true
    }

    fun onMotionEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_MOVE) return false
        val device = event.device ?: return false
        if (!isGameController(device)) return false
        if (!ensureSelected(device)) return false

        val axes = NormalizedAxes(
            lx = readStick(event, device, MotionEvent.AXIS_X),
            ly = readStick(event, device, MotionEvent.AXIS_Y),
            rx = readFirstStick(event, device, MotionEvent.AXIS_Z, MotionEvent.AXIS_RX),
            ry = readFirstStick(event, device, MotionEvent.AXIS_RZ, MotionEvent.AXIS_RY),
            l2 = readFirstTrigger(event, device, MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_BRAKE),
            r2 = readFirstTrigger(event, device, MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_GAS),
            hatX = readStick(event, device, MotionEvent.AXIS_HAT_X),
            hatY = readStick(event, device, MotionEvent.AXIS_HAT_Y),
        )
        val diagnostics = device.motionRanges
            .asSequence()
            .filter(::isControllerRange)
            .map { range ->
                AxisDiagnostic(
                    axis = range.axis,
                    name = MotionEvent.axisToString(range.axis),
                    rawValue = event.getAxisValue(range.axis),
                    min = range.min,
                    max = range.max,
                    flat = range.flat,
                )
            }
            .distinctBy { it.axis }
            .sortedBy { it.axis }
            .toList()

        _state.value = _state.value.copy(axes = axes, rawAxes = diagnostics)
        return true
    }

    override fun onInputDeviceAdded(deviceId: Int) {
        if (!_state.value.connected) selectFirstAvailable()
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        if (_state.value.deviceId != deviceId) return
        onSelectedDeviceDisconnected()
        _state.value = RawGamepadState()
        selectFirstAvailable()
    }

    override fun onInputDeviceChanged(deviceId: Int) {
        if (_state.value.deviceId != deviceId) return
        val device = inputManager.getInputDevice(deviceId)
        _state.value = if (device != null && isGameController(device)) {
            RawGamepadState(connected = true, deviceId = device.id, deviceName = device.name)
        } else {
            onSelectedDeviceDisconnected()
            RawGamepadState()
        }
    }

    private fun ensureSelected(device: InputDevice): Boolean {
        val selectedId = _state.value.deviceId
        if (selectedId != null) return selectedId == device.id
        _state.value = RawGamepadState(
            connected = true,
            deviceId = device.id,
            deviceName = device.name,
        )
        return true
    }

    private fun selectFirstAvailable() {
        val selected = InputDevice.getDeviceIds()
            .asSequence()
            .mapNotNull(inputManager::getInputDevice)
            .filter(::isGameController)
            .sortedBy { it.id }
            .firstOrNull()
        _state.value = if (selected == null) {
            RawGamepadState()
        } else {
            RawGamepadState(
                connected = true,
                deviceId = selected.id,
                deviceName = selected.name,
            )
        }
    }

    private fun readFirstStick(
        event: MotionEvent,
        device: InputDevice,
        vararg candidates: Int,
    ): Float {
        for (axis in candidates) {
            val range = motionRange(device, event, axis)
            if (range != null) {
                return AxisNormalizer.normalizeStick(event.getAxisValue(axis), range.toSpec())
            }
        }
        return 0f
    }

    private fun readFirstTrigger(
        event: MotionEvent,
        device: InputDevice,
        vararg candidates: Int,
    ): Float {
        for (axis in candidates) {
            val range = motionRange(device, event, axis)
            if (range != null) {
                return AxisNormalizer.normalizeTrigger(event.getAxisValue(axis), range.toSpec())
            }
        }
        return 0f
    }

    private fun readStick(event: MotionEvent, device: InputDevice, axis: Int): Float {
        val range = motionRange(device, event, axis) ?: return 0f
        return AxisNormalizer.normalizeStick(event.getAxisValue(axis), range.toSpec())
    }

    private fun motionRange(
        device: InputDevice,
        event: MotionEvent,
        axis: Int,
    ): InputDevice.MotionRange? =
        device.getMotionRange(axis, event.source) ?: device.getMotionRange(axis)

    private fun InputDevice.MotionRange.toSpec() = RangeSpec(min, max, flat)

    private fun isControllerRange(range: InputDevice.MotionRange): Boolean =
        range.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
            range.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD

    private fun isGameController(device: InputDevice): Boolean =
        device.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            device.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK

    private companion object {
        const val MAX_RECENT_KEYS = 8
    }
}
