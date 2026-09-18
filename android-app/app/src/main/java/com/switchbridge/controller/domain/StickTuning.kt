package com.switchbridge.controller.domain

enum class StickSide(val label: String) {
    LEFT("Left"),
    RIGHT("Right"),
}

data class StickTuning(
    val innerDeadZone: Float = DEFAULT_INNER_DEAD_ZONE,
    val outerDeadZone: Float = DEFAULT_OUTER_DEAD_ZONE,
    val exponent: Float = DEFAULT_EXPONENT,
    val gain: Float = DEFAULT_GAIN,
    val invertY: Boolean = false,
) {
    /** Clamps each parameter to its range and guarantees `outer >= inner + MIN_DEAD_ZONE_GAP`. */
    fun sanitized(): StickTuning {
        val inner = innerDeadZone.finiteOr(DEFAULT_INNER_DEAD_ZONE)
            .coerceIn(INNER_DEAD_ZONE_RANGE.start, INNER_DEAD_ZONE_RANGE.endInclusive)
        val outer = outerDeadZone.finiteOr(DEFAULT_OUTER_DEAD_ZONE)
            .coerceIn(OUTER_DEAD_ZONE_RANGE.start, OUTER_DEAD_ZONE_RANGE.endInclusive)
            .coerceAtLeast(inner + MIN_DEAD_ZONE_GAP)
            .coerceAtMost(1f)
        return copy(
            innerDeadZone = inner.coerceAtMost(outer - MIN_DEAD_ZONE_GAP),
            outerDeadZone = outer,
            exponent = exponent.finiteOr(DEFAULT_EXPONENT)
                .coerceIn(EXPONENT_RANGE.start, EXPONENT_RANGE.endInclusive),
            gain = gain.finiteOr(DEFAULT_GAIN)
                .coerceIn(GAIN_RANGE.start, GAIN_RANGE.endInclusive),
        )
    }

    companion object {
        const val DEFAULT_INNER_DEAD_ZONE = 0.08f
        const val DEFAULT_OUTER_DEAD_ZONE = 0.95f
        const val DEFAULT_EXPONENT = 1f
        const val DEFAULT_GAIN = 1f
        const val MIN_DEAD_ZONE_GAP = 0.2f

        val INNER_DEAD_ZONE_RANGE = 0f..0.40f
        val OUTER_DEAD_ZONE_RANGE = 0.70f..1f
        val EXPONENT_RANGE = 0.5f..3f
        val GAIN_RANGE = 0.5f..2f

        val Default = StickTuning()
    }
}

data class ControllerTuning(
    val left: StickTuning = StickTuning.Default,
    val right: StickTuning = StickTuning.Default,
) {
    operator fun get(side: StickSide): StickTuning = when (side) {
        StickSide.LEFT -> left
        StickSide.RIGHT -> right
    }

    fun with(side: StickSide, tuning: StickTuning): ControllerTuning = when (side) {
        StickSide.LEFT -> copy(left = tuning)
        StickSide.RIGHT -> copy(right = tuning)
    }

    companion object {
        val Default = ControllerTuning()
    }
}

/** Curve presets: keep the dead zones and inversion chosen by the user. */
enum class StickPreset(val label: String, val exponent: Float, val gain: Float) {
    LINEAR("Linear", 1f, 1f),
    PRECISION("Precision", 1.8f, 1f),
    FAST("Fast", 0.7f, 1.2f),
    ;

    fun applyTo(tuning: StickTuning): StickTuning =
        tuning.copy(exponent = exponent, gain = gain)
}

private fun Float.finiteOr(fallback: Float): Float = if (isFinite()) this else fallback
