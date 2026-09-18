package com.switchbridge.controller.mapping

import com.switchbridge.controller.domain.StickTuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.sqrt

class StickProcessorTest {
    private val identity = StickTuning(innerDeadZone = 0f, outerDeadZone = 1f)

    @Test
    fun insideInnerDeadZoneReturnsZero() {
        val tuning = StickTuning(innerDeadZone = 0.2f)
        assertEquals(0f to 0f, StickProcessor.process(0f, 0f, tuning))
        assertEquals(0f to 0f, StickProcessor.process(0.1f, 0.1f, tuning))
        assertEquals(0f to 0f, StickProcessor.process(0f, -0.2f, tuning))
    }

    @Test
    fun deadZoneIsRadialNotSquare() {
        // Each axis alone is below 0.2, but the magnitude (0.269) is not.
        val (x, y) = StickProcessor.process(0.19f, 0.19f, StickTuning(innerDeadZone = 0.2f))
        assertTrue(x > 0f && y > 0f)
    }

    @Test
    fun outerDeadZoneReachesFullMagnitude() {
        val tuning = StickTuning(innerDeadZone = 0.1f, outerDeadZone = 0.8f)
        val (x, y) = StickProcessor.process(0.8f, 0f, tuning)
        assertEquals(1f, x, 0.0001f)
        assertEquals(0f, y, 0.0001f)

        val (bx, by) = StickProcessor.process(0f, -0.95f, tuning)
        assertEquals(0f, bx, 0.0001f)
        assertEquals(-1f, by, 0.0001f)
    }

    @Test
    fun diagonalDirectionIsPreserved() {
        val tuning = StickTuning(innerDeadZone = 0.15f, outerDeadZone = 0.9f, exponent = 2f)
        val (x, y) = StickProcessor.process(0.4f, -0.3f, tuning)
        assertEquals(0.4f / -0.3f, x / y, 0.0001f)
        assertTrue(x > 0f && y < 0f)

        val diagonal = StickProcessor.process(1f / sqrt(2f), 1f / sqrt(2f), identity)
        assertEquals(diagonal.first, diagonal.second, 0.0001f)
        assertEquals(1f, hypot(diagonal.first, diagonal.second), 0.0001f)
    }

    @Test
    fun exponentTwoAtHalfTravelGivesQuarter() {
        val (x, _) = StickProcessor.process(0.5f, 0f, identity.copy(exponent = 2f))
        assertEquals(0.25f, x, 0.0001f)
    }

    @Test
    fun gainClampsToOne() {
        val tuning = identity.copy(gain = 2f)
        assertEquals(1f, StickProcessor.process(0.75f, 0f, tuning).first, 0.0001f)
        assertEquals(0.6f, StickProcessor.process(0.3f, 0f, tuning).first, 0.0001f)
        val (x, y) = StickProcessor.process(0.7f, 0.7f, tuning)
        assertEquals(1f, hypot(x, y), 0.0001f)
    }

    @Test
    fun defaultsBehaveLinearlyBetweenDeadZones() {
        val tuning = StickTuning.Default
        for (input in listOf(0.2f, 0.4f, 0.6f, 0.8f)) {
            val expected = (input - 0.08f) / (0.95f - 0.08f)
            assertEquals(expected, StickProcessor.process(input, 0f, tuning).first, 0.0001f)
        }
        assertEquals(0.5f, StickProcessor.process(0.5f, 0f, identity).first, 0.0001f)
    }

    @Test
    fun invertYOnlyFlipsVerticalComponent() {
        val (x, y) = StickProcessor.process(0.5f, 0.5f, identity.copy(invertY = true))
        assertEquals(0.5f, x, 0.0001f)
        assertEquals(-0.5f, y, 0.0001f)
    }

    @Test
    fun sanitizedKeepsRangesAndMinimumGap() {
        val tuning = StickTuning(
            innerDeadZone = 0.9f,
            outerDeadZone = 0.1f,
            exponent = 10f,
            gain = Float.NaN,
        ).sanitized()
        assertEquals(0.40f, tuning.innerDeadZone, 0.0001f)
        assertEquals(0.70f, tuning.outerDeadZone, 0.0001f)
        assertEquals(3f, tuning.exponent, 0.0001f)
        assertEquals(1f, tuning.gain, 0.0001f)
        assertTrue(tuning.outerDeadZone >= tuning.innerDeadZone + StickTuning.MIN_DEAD_ZONE_GAP)
    }
}
