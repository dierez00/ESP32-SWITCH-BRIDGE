package com.switchbridge.controller.protocol

import com.switchbridge.controller.domain.SwitchButton
import com.switchbridge.controller.domain.SwitchControllerState
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PacketCodecTest {
    @Test
    fun neutralPacketHasExactWireFormat() {
        val packet = PacketCodec.encode(SwitchControllerState.Neutral, false, 0)

        assertEquals(16, packet.size)
        assertArrayEquals(
            intArrayOf(
                0x53, 0x42, 0x01, 0x00, 0x00,
                0x00, 0x00, 0x00,
                0x80, 0x80, 0x80, 0x80,
                0x00, 0x00, 0x00, 0x4C,
            ),
            packet.map { it.toInt() and 0xFF }.toIntArray(),
        )
    }

    @Test
    fun encodesButtonsAxesAndConnectedFlag() {
        val report = SwitchControllerState(
            buttons = setOf(
                SwitchButton.Y,
                SwitchButton.A,
                SwitchButton.R,
                SwitchButton.ZR,
                SwitchButton.PLUS,
                SwitchButton.HOME,
                SwitchButton.UP,
                SwitchButton.LEFT,
                SwitchButton.L,
                SwitchButton.ZL,
            ),
            lx = 0,
            ly = 255,
            rx = 17,
            ry = 238,
            l2 = 64,
            r2 = 192,
        )

        val packet = PacketCodec.encode(report, true, 255)

        assertEquals(0x01, packet[3].u())
        assertEquals(0xFF, packet[4].u())
        assertEquals(0xC9, packet[5].u())
        assertEquals(0x12, packet[6].u())
        assertEquals(0xCA, packet[7].u())
        assertEquals(0, packet[8].u())
        assertEquals(255, packet[9].u())
        assertEquals(0, packet[14].u())
        assertEquals(Crc8.atm(packet, 15), packet[15].u())
    }

    @Test
    fun sequenceIsEncodedModuloOneByte() {
        assertEquals(255, PacketCodec.encode(SwitchControllerState.Neutral, false, 255)[4].u())
        assertEquals(0, PacketCodec.encode(SwitchControllerState.Neutral, false, 256)[4].u())
    }

    private fun Byte.u() = toInt() and 0xFF
}

