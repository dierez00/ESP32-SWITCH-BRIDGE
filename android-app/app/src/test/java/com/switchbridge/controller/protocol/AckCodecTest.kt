package com.switchbridge.controller.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AckCodecTest {
    @Test
    fun decodesValidAck() {
        val bytes = byteArrayOf(0x53, 0x41, 0x01, 0x07, 0x2A, 0x0F, 0x00, 0x00)
        bytes[7] = Crc8.atm(bytes, 7).toByte()

        val ack = AckCodec.decode(bytes)
        assertNotNull(ack)
        ack!!

        assertTrue(ack.switchConnected)
        assertTrue(ack.handshakeComplete)
        assertTrue(ack.recentInput)
        assertEquals(42, ack.lastSequence)
        assertEquals(15, ack.packetAgeMs)
    }

    @Test
    fun decodesIndependentStatusBits() {
        val bytes = byteArrayOf(0x53, 0x41, 0x01, 0x02, 0, 0, 0, 0)
        bytes[7] = Crc8.atm(bytes, 7).toByte()
        val ack = AckCodec.decode(bytes)
        assertNotNull(ack)
        ack!!
        assertFalse(ack.switchConnected)
        assertTrue(ack.handshakeComplete)
        assertFalse(ack.recentInput)
    }

    @Test
    fun rejectsWrongLengthHeaderVersionAndCrc() {
        val valid = byteArrayOf(0x53, 0x41, 0x01, 0, 0, 0, 0, 0)
        valid[7] = Crc8.atm(valid, 7).toByte()
        assertNull(AckCodec.decode(valid, 7))
        assertNull(AckCodec.decode(valid.copyOf().also { it[0] = 0x00 }))
        assertNull(AckCodec.decode(valid.copyOf().also { it[2] = 0x02 }))
        assertNull(AckCodec.decode(valid.copyOf().also { it[7] = (it[7].toInt() xor 1).toByte() }))
    }
}
