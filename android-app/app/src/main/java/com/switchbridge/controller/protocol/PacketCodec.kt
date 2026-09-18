package com.switchbridge.controller.protocol

import com.switchbridge.controller.domain.SwitchControllerState

object PacketCodec {
    const val PACKET_SIZE = 16

    fun encode(
        report: SwitchControllerState,
        controllerConnected: Boolean,
        sequence: Int,
    ): ByteArray = ByteArray(PACKET_SIZE).apply {
        this[0] = 0x53
        this[1] = 0x42
        this[2] = 0x01
        this[3] = if (controllerConnected) 0x01 else 0x00
        this[4] = sequence.toByte()
        this[5] = report.bank(1).toByte()
        this[6] = report.bank(2).toByte()
        this[7] = report.bank(3).toByte()
        this[8] = report.lx.toByte()
        this[9] = report.ly.toByte()
        this[10] = report.rx.toByte()
        this[11] = report.ry.toByte()
        this[12] = report.l2.toByte()
        this[13] = report.r2.toByte()
        this[14] = 0x00
        this[15] = Crc8.atm(this, length = 15).toByte()
    }
}

