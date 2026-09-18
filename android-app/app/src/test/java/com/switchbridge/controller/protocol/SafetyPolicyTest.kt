package com.switchbridge.controller.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyPolicyTest {
    @Test
    fun disconnectProducesOneImmediateNeutralSnapshot() {
        val neutral = SafetyPolicy.disconnectedPacket()
        assertFalse(neutral.connected)
        assertEquals(128, neutral.report.lx)
        assertTrue(neutral.report.buttons.isEmpty())
    }

    @Test
    fun stopProducesExactlyThreeNeutralSnapshots() {
        val burst = SafetyPolicy.stopBurst()
        assertEquals(3, burst.size)
        assertTrue(burst.all { !it.connected && it.report.buttons.isEmpty() })
    }

    @Test
    fun ackExpiresAtOneSecond() {
        assertTrue(SafetyPolicy.isAckFresh(1_000, 1_999))
        assertFalse(SafetyPolicy.isAckFresh(1_000, 2_000))
        assertFalse(SafetyPolicy.isAckFresh(null, 2_000))
    }

    @Test
    fun trackerCalculatesRttAndHandlesSequenceReuse() {
        val tracker = AckTracker()
        assertEquals(null, tracker.roundTripMs(12, 100))
        tracker.recordSent(12, 100)
        assertEquals(25L, tracker.roundTripMs(12, 125))
        tracker.recordSent(268, 200)
        assertEquals(10L, tracker.roundTripMs(12, 210))
    }
}
