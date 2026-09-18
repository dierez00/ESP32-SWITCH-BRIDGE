package com.switchbridge.controller.protocol

import com.switchbridge.controller.domain.EspAck

object AckCodec {
    const val ACK_SIZE = 8

    fun decode(bytes: ByteArray, length: Int = bytes.size): EspAck? {
        if (length != ACK_SIZE || bytes.size < ACK_SIZE) return null
        if ((bytes[0].toInt() and 0xFF) != 0x53) return null
        if ((bytes[1].toInt() and 0xFF) != 0x41) return null
        if ((bytes[2].toInt() and 0xFF) != 0x01) return null
        if ((bytes[7].toInt() and 0xFF) != Crc8.atm(bytes, length = 7)) return null

        val status = bytes[3].toInt() and 0xFF
        return EspAck(
            switchConnected = status and 0x01 != 0,
            handshakeComplete = status and 0x02 != 0,
            recentInput = status and 0x04 != 0,
            lastSequence = bytes[4].toInt() and 0xFF,
            packetAgeMs = bytes[5].toInt() and 0xFF,
            rawStatus = status,
        )
    }
}

