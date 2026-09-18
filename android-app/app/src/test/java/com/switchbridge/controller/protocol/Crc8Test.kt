package com.switchbridge.controller.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class Crc8Test {
    @Test
    fun standardCheckVector() {
        assertEquals(0xF4, Crc8.atm("123456789".encodeToByteArray()))
    }

    @Test
    fun emptyInputUsesZeroInitialValue() {
        assertEquals(0, Crc8.atm(byteArrayOf()))
    }
}

