package com.switchbridge.controller.mapping

import com.switchbridge.controller.domain.RangeSpec
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

object AxisNormalizer {
    const val MIN_DEAD_ZONE = 0.08f

    /**
     * Centers and normalizes a stick axis to -1..1. Does not apply a minimum dead zone: the radial
     * dead zone is handled by [StickProcessor]. Only honors the hardware `flat` value when > 0.
     */
    fun normalizeStick(raw: Float, range: RangeSpec): Float {
        val center = (range.min + range.max) / 2f
        val span = if (raw >= center) range.max - center else center - range.min
        if (span <= 0f) return 0f
        val normalized = ((raw - center) / span).coerceIn(-1f, 1f)
        val flat = (range.flat / span).coerceIn(0f, 0.99f)
        return if (flat > 0f) applyDeadZone(normalized, flat) else normalized
    }

    fun normalizeTrigger(raw: Float, range: RangeSpec): Float {
        val span = range.max - range.min
        if (span <= 0f) return 0f
        val normalized = ((raw - range.min) / span).coerceIn(0f, 1f)
        val flat = (range.flat / span).coerceIn(0f, 0.99f)
        val deadZone = maxOf(MIN_DEAD_ZONE, flat)
        if (normalized <= deadZone) return 0f
        return ((normalized - deadZone) / (1f - deadZone)).coerceIn(0f, 1f)
    }

    fun applyDeadZone(value: Float, deadZone: Float): Float {
        val clamped = value.coerceIn(-1f, 1f)
        val zone = deadZone.coerceIn(0f, 0.99f)
        if (abs(clamped) <= zone) return 0f
        return sign(clamped) * ((abs(clamped) - zone) / (1f - zone))
    }

    fun quantizeStick(value: Float, invert: Boolean = false): Int {
        val normalized = (if (invert) -value else value).coerceIn(-1f, 1f)
        return if (normalized >= 0f) {
            128 + (normalized * 127f).roundToInt()
        } else {
            128 + (normalized * 128f).roundToInt()
        }.coerceIn(0, 255)
    }

    /** Approximate inverse of [quantizeStick] (without inversion). */
    fun dequantizeStick(value: Int): Float {
        val offset = value.coerceIn(0, 255) - 128
        return if (offset >= 0) offset / 127f else offset / 128f
    }

    fun quantizeTrigger(value: Float): Int =
        (value.coerceIn(0f, 1f) * 255f).roundToInt()
}

