package com.switchbridge.controller.domain

data class RangeSpec(
    val min: Float,
    val max: Float,
    val flat: Float,
)

data class NormalizedAxes(
    val lx: Float = 0f,
    val ly: Float = 0f,
    val rx: Float = 0f,
    val ry: Float = 0f,
    val l2: Float = 0f,
    val r2: Float = 0f,
    val hatX: Float = 0f,
    val hatY: Float = 0f,
)

data class AxisDiagnostic(
    val axis: Int,
    val name: String,
    val rawValue: Float,
    val min: Float,
    val max: Float,
    val flat: Float,
)

data class KeyDiagnostic(
    val keyCode: Int,
    val name: String,
    val pressed: Boolean,
)

data class RawGamepadState(
    val connected: Boolean = false,
    val deviceId: Int? = null,
    val deviceName: String = "No gamepad",
    val pressedKeys: Set<Int> = emptySet(),
    val axes: NormalizedAxes = NormalizedAxes(),
    val rawAxes: List<AxisDiagnostic> = emptyList(),
    val recentKeys: List<KeyDiagnostic> = emptyList(),
)

enum class SwitchButton(
    val bank: Int,
    val mask: Int,
    val label: String,
) {
    Y(1, 0x01, "Y"),
    X(1, 0x02, "X"),
    B(1, 0x04, "B"),
    A(1, 0x08, "A"),
    R(1, 0x40, "R"),
    ZR(1, 0x80, "ZR"),
    MINUS(2, 0x01, "Minus"),
    PLUS(2, 0x02, "Plus"),
    RIGHT_STICK(2, 0x04, "R Stick"),
    LEFT_STICK(2, 0x08, "L Stick"),
    HOME(2, 0x10, "Home"),
    CAPTURE(2, 0x20, "Capture"),
    DOWN(3, 0x01, "Down"),
    UP(3, 0x02, "Up"),
    RIGHT(3, 0x04, "Right"),
    LEFT(3, 0x08, "Left"),
    L(3, 0x40, "L"),
    ZL(3, 0x80, "ZL"),
}

data class SwitchControllerState(
    val buttons: Set<SwitchButton> = emptySet(),
    val lx: Int = 128,
    val ly: Int = 128,
    val rx: Int = 128,
    val ry: Int = 128,
    val l2: Int = 0,
    val r2: Int = 0,
) {
    fun bank(number: Int): Int = buttons
        .asSequence()
        .filter { it.bank == number }
        .fold(0) { value, button -> value or button.mask }

    companion object {
        val Neutral = SwitchControllerState()
    }
}

data class ControllerSnapshot(
    val connected: Boolean = false,
    val report: SwitchControllerState = SwitchControllerState.Neutral,
)

data class Endpoint(
    val address: String,
    val port: Int,
)

data class EspAck(
    val switchConnected: Boolean,
    val handshakeComplete: Boolean,
    val recentInput: Boolean,
    val lastSequence: Int,
    val packetAgeMs: Int,
    val rawStatus: Int,
)

