package com.switchbridge.controller.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.switchbridge.controller.domain.ControllerTuning
import com.switchbridge.controller.domain.NormalizedAxes
import com.switchbridge.controller.domain.StickPreset
import com.switchbridge.controller.domain.StickSide
import com.switchbridge.controller.domain.StickTuning
import com.switchbridge.controller.domain.SwitchControllerState
import com.switchbridge.controller.mapping.AxisNormalizer
import com.switchbridge.controller.mapping.StickProcessor
import com.switchbridge.controller.ui.theme.SignalTeal
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val TRAIL_LENGTH = 12
private const val CURVE_SAMPLES = 64

/** Raw position (before tuning) and bytes sent for one stick. */
data class StickReading(
    val rawX: Float,
    val rawY: Float,
    val sentX: Int,
    val sentY: Int,
) {
    /** Processed position in Android convention (positive Y is down), undoing the protocol inversion. */
    val processedX: Float get() = AxisNormalizer.dequantizeStick(sentX)
    val processedY: Float get() = -AxisNormalizer.dequantizeStick(sentY)
}

fun StickSide.reading(axes: NormalizedAxes, report: SwitchControllerState): StickReading = when (this) {
    StickSide.LEFT -> StickReading(axes.lx, axes.ly, report.lx, report.ly)
    StickSide.RIGHT -> StickReading(axes.rx, axes.ry, report.rx, report.ry)
}

@Composable
fun StickVisualizerPair(
    axes: NormalizedAxes,
    report: SwitchControllerState,
    tuning: ControllerTuning,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 320.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StickSide.entries.forEach { side ->
                    StickVisualizer(side, side.reading(axes, report), tuning[side])
                }
            }
        } else {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StickSide.entries.forEach { side ->
                    StickVisualizer(side, side.reading(axes, report), tuning[side])
                }
            }
        }
    }
}

@Composable
fun StickVisualizer(
    side: StickSide,
    reading: StickReading,
    tuning: StickTuning,
    modifier: Modifier = Modifier,
    showTrail: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val processedTarget = Offset(reading.processedX, reading.processedY)
    // Critically damped, very stiff spring: settles in ~40 ms with no bounce.
    val processed = animateOffsetAsState(
        targetValue = processedTarget,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh),
        label = "stick-${side.name}",
    )
    val trail = remember { mutableStateListOf<Offset>() }
    LaunchedEffect(processedTarget, showTrail) {
        if (!showTrail) {
            trail.clear()
            return@LaunchedEffect
        }
        if (trail.lastOrNull() != processedTarget) {
            trail.add(processedTarget)
            if (trail.size > TRAIL_LENGTH) trail.removeAt(0)
        }
    }

    val magnitudePercent = (hypot(reading.processedX, reading.processedY).coerceAtMost(1f) * 100f).roundToInt()
    val description = "${side.label} stick: X ${reading.sentX}, Y ${reading.sentY}, " +
        "magnitude $magnitudePercent percent"

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(side.label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Canvas(
            Modifier
                .size(140.dp)
                .semantics { contentDescription = description },
        ) {
            val radius = size.minDimension / 2f - 6.dp.toPx()
            val center = this.center
            val hairline = 1.dp.toPx()
            fun toCanvas(position: Offset) = Offset(
                center.x + position.x.coerceIn(-1f, 1f) * radius,
                center.y + position.y.coerceIn(-1f, 1f) * radius,
            )

            drawCircle(colors.surfaceVariant.copy(alpha = 0.45f), radius, center)
            drawCircle(colors.outline, radius, center, style = Stroke(1.5f.dp.toPx()))
            drawLine(colors.outline.copy(alpha = 0.5f), Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), hairline)
            drawLine(colors.outline.copy(alpha = 0.5f), Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), hairline)
            drawCircle(colors.primary.copy(alpha = 0.14f), tuning.innerDeadZone * radius, center)
            drawCircle(colors.primary.copy(alpha = 0.35f), tuning.innerDeadZone * radius, center, style = Stroke(hairline))
            drawCircle(
                color = colors.primary.copy(alpha = 0.6f),
                radius = tuning.outerDeadZone * radius,
                center = center,
                style = Stroke(hairline, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
            )

            trail.forEachIndexed { index, point ->
                val alpha = (index + 1f) / (trail.size + 1f) * 0.35f
                drawCircle(SignalTeal.copy(alpha = alpha), 3.dp.toPx(), toCanvas(point))
            }

            drawCircle(colors.onSurfaceVariant.copy(alpha = 0.75f), 3.dp.toPx(), toCanvas(Offset(reading.rawX, reading.rawY)))
            drawCircle(SignalTeal, 7.dp.toPx(), toCanvas(processed.value))
            drawCircle(colors.surface, 7.dp.toPx(), toCanvas(processed.value), style = Stroke(1.5f.dp.toPx()))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "X ${reading.sentX.toString().padStart(3)}  Y ${reading.sentY.toString().padStart(3)}",
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
        Text(
            "$magnitudePercent%",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = colors.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickTuningPanel(
    axes: NormalizedAxes,
    report: SwitchControllerState,
    tuning: ControllerTuning,
    onTuningChange: (StickSide, (StickTuning) -> StickTuning) -> Unit,
    onTuningReset: (StickSide) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 600.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                StickSide.entries.forEach { side ->
                    Column(Modifier.weight(1f)) {
                        Text("${side.label} stick", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        StickTuningEditor(side, side.reading(axes, report), tuning[side], onTuningChange, onTuningReset)
                    }
                }
            }
        } else {
            var selected by rememberSaveable { mutableStateOf(StickSide.LEFT) }
            Column {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    StickSide.entries.forEachIndexed { index, side ->
                        SegmentedButton(
                            selected = selected == side,
                            onClick = { selected = side },
                            shape = SegmentedButtonDefaults.itemShape(index, StickSide.entries.size),
                        ) {
                            Text(side.label)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                StickTuningEditor(selected, selected.reading(axes, report), tuning[selected], onTuningChange, onTuningReset)
            }
        }
    }
}

@Composable
private fun StickTuningEditor(
    side: StickSide,
    reading: StickReading,
    tuning: StickTuning,
    onTuningChange: (StickSide, (StickTuning) -> StickTuning) -> Unit,
    onTuningReset: (StickSide) -> Unit,
) = Column(Modifier.fillMaxWidth()) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StickVisualizer(side, reading, tuning, showTrail = false)
        ResponseCurve(tuning, hypot(reading.rawX, reading.rawY))
    }
    Spacer(Modifier.height(12.dp))

    TuningSlider(
        label = "Inner dead zone",
        value = tuning.innerDeadZone,
        range = StickTuning.INNER_DEAD_ZONE_RANGE,
        format = ::percent,
    ) { value -> onTuningChange(side) { it.copy(innerDeadZone = value) } }
    TuningSlider(
        label = "Outer dead zone",
        value = tuning.outerDeadZone,
        range = StickTuning.OUTER_DEAD_ZONE_RANGE,
        format = ::percent,
    ) { value -> onTuningChange(side) { it.copy(outerDeadZone = value) } }
    TuningSlider(
        label = "Curve (exponent)",
        value = tuning.exponent,
        range = StickTuning.EXPONENT_RANGE,
        format = { "%.2f".format(Locale.US, it) },
    ) { value -> onTuningChange(side) { it.copy(exponent = value) } }
    TuningSlider(
        label = "Sensitivity (gain)",
        value = tuning.gain,
        range = StickTuning.GAIN_RANGE,
        format = { "×%.2f".format(Locale.US, it) },
    ) { value -> onTuningChange(side) { it.copy(gain = value) } }

    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Invert Y axis", style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = tuning.invertY,
            onCheckedChange = { checked -> onTuningChange(side) { it.copy(invertY = checked) } },
            colors = SwitchDefaults.colors(checkedTrackColor = SignalTeal),
        )
    }

    Spacer(Modifier.height(6.dp))
    Text("Presets", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StickPreset.entries.forEach { preset ->
            val active = tuning.exponent == preset.exponent && tuning.gain == preset.gain
            OutlinedButton(
                onClick = { onTuningChange(side, preset::applyTo) },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                Text(
                    preset.label,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active) SignalTeal else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = { onTuningReset(side) }) {
            Text("Reset ${side.label.lowercase(Locale.ROOT)} stick")
        }
    }
}

@Composable
private fun TuningSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                format(value),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = SignalTeal,
            )
        }
        Slider(
            value = value,
            onValueChange = { onValueChange((it * 100f).roundToInt() / 100f) },
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = SignalTeal, activeTrackColor = SignalTeal),
            modifier = Modifier.semantics { contentDescription = "$label ${format(value)}" },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val rangeStyle = MaterialTheme.typography.labelMedium
            val rangeColor = MaterialTheme.colorScheme.onSurfaceVariant
            Text(format(range.start), style = rangeStyle, color = rangeColor)
            Text(format(range.endInclusive), style = rangeStyle, color = rangeColor)
        }
    }
}

/** Response curve plot: input 0..1 on X, output 0..1 on Y. */
@Composable
private fun ResponseCurve(tuning: StickTuning, rawMagnitude: Float) {
    val colors = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Response", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Canvas(
            Modifier
                .size(120.dp)
                .semantics {
                    contentDescription = "Response curve with exponent " +
                        "%.2f and gain %.2f".format(Locale.US, tuning.exponent, tuning.gain)
                },
        ) {
            val w = size.width
            val h = size.height
            val hairline = 1.dp.toPx()
            fun point(input: Float, output: Float) = Offset(input * w, h - output * h)

            drawRect(colors.surfaceVariant.copy(alpha = 0.45f))
            drawRect(colors.primary.copy(alpha = 0.12f), size = size.copy(width = tuning.innerDeadZone * w))
            drawLine(
                color = colors.outline,
                start = point(0f, 0f),
                end = point(1f, 1f),
                strokeWidth = hairline,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
            )
            drawLine(
                color = colors.primary.copy(alpha = 0.5f),
                start = point(tuning.outerDeadZone, 0f),
                end = point(tuning.outerDeadZone, 1f),
                strokeWidth = hairline,
            )

            val curve = Path()
            for (i in 0..CURVE_SAMPLES) {
                val input = i / CURVE_SAMPLES.toFloat()
                val output = StickProcessor.process(input, 0f, tuning.copy(invertY = false)).first
                val p = point(input, output)
                if (i == 0) curve.moveTo(p.x, p.y) else curve.lineTo(p.x, p.y)
            }
            drawPath(curve, SignalTeal, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))

            val input = rawMagnitude.coerceIn(0f, 1f)
            val output = StickProcessor.process(input, 0f, tuning).first
            drawCircle(SignalTeal, 4.dp.toPx(), point(input, output))
            drawRect(colors.outline, style = Stroke(hairline))
        }
    }
}

private fun percent(value: Float): String = "${(value * 100f).roundToInt()}%"
