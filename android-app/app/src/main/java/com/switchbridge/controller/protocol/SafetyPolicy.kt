package com.switchbridge.controller.protocol

import com.switchbridge.controller.domain.ControllerSnapshot
import com.switchbridge.controller.domain.SwitchControllerState

object SafetyPolicy {
    const val SEND_PERIOD_MS = 15L
    const val ACK_TIMEOUT_MS = 1_000L
    const val STOP_NEUTRAL_PACKETS = 3

    fun disconnectedPacket(): ControllerSnapshot = ControllerSnapshot(
        connected = false,
        report = SwitchControllerState.Neutral,
    )

    fun stopBurst(): List<ControllerSnapshot> =
        List(STOP_NEUTRAL_PACKETS) { disconnectedPacket() }

    fun isAckFresh(lastAckAtMs: Long?, nowMs: Long): Boolean =
        lastAckAtMs != null && nowMs - lastAckAtMs < ACK_TIMEOUT_MS
}

