package com.switchbridge.controller.mapping

import com.switchbridge.controller.domain.RangeSpec
import org.junit.Assert.assertEquals
import org.junit.Test

class AxisNormalizerTest {
    private val standard = RangeSpec(-1f, 1f, 0f)

    @Test
    fun stickHasNoPerAxisDeadZoneWhenHardwareFlatIsZero() {
        assertEquals(0.08f, AxisNormalizer.normalizeStick(0.08f, standard), 0.0001f)
        assertEquals(-0.05f, AxisNormalizer.normalizeStick(-0.05f, standard), 0.0001f)
        assertEquals(0.5f, AxisNormalizer.normalizeStick(0.5f, standard), 0.0001f)
        assertEquals(1f, AxisNormalizer.normalizeStick(1f, standard), 0.0001f)
        assertEquals(-1f, AxisNormalizer.normalizeStick(-1f, standard), 0.0001f)
    }

    @Test
    fun stickNormalizesAsymmetricRanges() {
        val range = RangeSpec(0f, 255f, 0f)
        assertEquals(0f, AxisNormalizer.normalizeStick(127.5f, range), 0.0001f)
        assertEquals(1f, AxisNormalizer.normalizeStick(255f, range), 0.0001f)
        assertEquals(-1f, AxisNormalizer.normalizeStick(0f, range), 0.0001f)
    }

    @Test
    fun hardwareFlatIsKeptAsSafetyDeadZone() {
        val range = RangeSpec(-1f, 1f, 0.2f)
        assertEquals(0f, AxisNormalizer.normalizeStick(0.2f, range), 0.0001f)
        assertEquals(0.375f, AxisNormalizer.normalizeStick(0.5f, range), 0.0001f)
    }

    @Test
    fun triggerSupportsMinusOneToOneRangesAndDeadZone() {
        val trigger = RangeSpec(-1f, 1f, 0f)
        assertEquals(0f, AxisNormalizer.normalizeTrigger(-1f, trigger), 0.0001f)
        assertEquals(1f, AxisNormalizer.normalizeTrigger(1f, trigger), 0.0001f)
        assertEquals((0.5f - 0.08f) / 0.92f, AxisNormalizer.normalizeTrigger(0f, trigger), 0.0001f)
    }

    @Test
    fun quantizationHasExactCenterAndVerticalInversion() {
        assertEquals(0, AxisNormalizer.quantizeStick(-1f))
        assertEquals(128, AxisNormalizer.quantizeStick(0f))
        assertEquals(255, AxisNormalizer.quantizeStick(1f))
        assertEquals(255, AxisNormalizer.quantizeStick(-1f, invert = true))
        assertEquals(0, AxisNormalizer.quantizeStick(1f, invert = true))
        assertEquals(0, AxisNormalizer.quantizeTrigger(0f))
        assertEquals(255, AxisNormalizer.quantizeTrigger(1f))
    }

    @Test
    fun dequantizationRoundTripsQuantizedValues() {
        for (value in listOf(-1f, -0.5f, 0f, 0.25f, 1f)) {
            val byte = AxisNormalizer.quantizeStick(value)
            assertEquals(byte, AxisNormalizer.quantizeStick(AxisNormalizer.dequantizeStick(byte)))
        }
        assertEquals(0f, AxisNormalizer.dequantizeStick(128), 0f)
    }
}

