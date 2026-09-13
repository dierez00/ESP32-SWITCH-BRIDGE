package com.switchbridge.controller.mapping

import com.switchbridge.controller.domain.StickTuning
import kotlin.math.hypot
import kotlin.math.pow

/**
 * Radial stick processing: circular dead zone, rescaling up to the outer zone,
 * response curve and gain, preserving the original direction.
 */
object StickProcessor {
    fun process(x: Float, y: Float, t: StickTuning): Pair<Float, Float> {
        val magnitude = hypot(x, y)
        if (!magnitude.isFinite() || magnitude <= t.innerDeadZone) return 0f to 0f

        val span = t.outerDeadZone - t.innerDeadZone
        var r = if (span > 0f) ((magnitude - t.innerDeadZone) / span).coerceIn(0f, 1f) else 1f
        r = r.pow(t.exponent)
        r = (r * t.gain).coerceIn(0f, 1f)

        val outX = (x / magnitude * r).coerceIn(-1f, 1f)
        val outY = (y / magnitude * r).coerceIn(-1f, 1f)
        return outX to if (t.invertY) -outY else outY
    }
}
