package com.switchbridge.controller.protocol

object Crc8 {
    fun atm(bytes: ByteArray, length: Int = bytes.size): Int {
        require(length in 0..bytes.size)
        var crc = 0
        for (index in 0 until length) {
            crc = crc xor (bytes[index].toInt() and 0xFF)
            repeat(8) {
                crc = if (crc and 0x80 != 0) {
                    ((crc shl 1) xor 0x07) and 0xFF
                } else {
                    (crc shl 1) and 0xFF
                }
            }
        }
        return crc
    }
}

