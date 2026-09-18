package com.switchbridge.controller.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.switchbridge.controller.BridgeUiState
import com.switchbridge.controller.description
import com.switchbridge.controller.domain.AxisDiagnostic
import com.switchbridge.controller.domain.StickSide
import com.switchbridge.controller.domain.StickTuning
import com.switchbridge.controller.network.WifiVerification
import com.switchbridge.controller.ui.theme.BridgeBackground
import com.switchbridge.controller.ui.theme.BridgePanel
import com.switchbridge.controller.ui.theme.HandshakeIndigo
import com.switchbridge.controller.ui.theme.InactiveRail
import com.switchbridge.controller.ui.theme.MutedInk
import com.switchbridge.controller.ui.theme.SignalTeal
import com.switchbridge.controller.ui.theme.WarningOrange
import java.util.Locale

@Composable
fun SwitchBridgeScreen(
    state: BridgeUiState,
    onAddressChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onToggleBridge: () -> Unit,
    onTuningChange: (StickSide, (StickTuning) -> StickTuning) -> Unit,
    onTuningReset: (StickSide) -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onDimScreen: () -> Unit,
) {
    val switchConnected = state.transport.ackFresh && state.transport.ack?.switchConnected == true
    val handshake = state.transport.ackFresh && state.transport.ack?.handshakeComplete == true
    val wifiWarning = state.wifi.verification != WifiVerification.EXPECTED_NETWORK

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BridgeBackground)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("SWITCH BRIDGE", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Android gamepad · UDP link · Switch input",
            color = MutedInk,
            style = MaterialTheme.typography.bodyMedium,
        )

        BridgeRail(
            ds4 = state.rawGamepad.connected,
            udp = state.transport.running,
            esp = state.transport.ackFresh,
            switchConnected = switchConnected,
        )

        ControllerSetupCard(
            controllerConnected = state.rawGamepad.connected,
            onOpenBluetoothSettings = onOpenBluetoothSettings,
        )

        if (wifiWarning) {
            WarningCard(state.wifi.description())
        }
        state.notice?.let { message -> WarningCard(message) }
        state.transport.lastError?.let { message -> WarningCard(message) }

        SectionCard("Connection") {
            StatusLine("DualShock 4", if (state.rawGamepad.connected) "Connected" else "No gamepad")
            StatusLine("Device", state.rawGamepad.deviceName)
            StatusLine("Wi-Fi", state.wifi.description())
            StatusLine("ESP32", if (state.transport.ackFresh) "ACK active" else "No ACK")
            StatusLine("Switch", if (switchConnected) "Connected" else "Disconnected")
            StatusLine("Handshake", if (handshake) "Complete" else "Pending")
        }

        SectionCard("UDP target") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = state.endpoint.address,
                    onValueChange = onAddressChange,
                    label = { Text("ESP32 IP") },
                    singleLine = true,
                    enabled = !state.transport.running,
                    isError = state.endpoint.parsed == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1.8f),
                )
                OutlinedTextField(
                    value = state.endpoint.port,
                    onValueChange = onPortChange,
                    label = { Text("Port") },
                    singleLine = true,
                    enabled = !state.transport.running,
                    isError = state.endpoint.parsed == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onToggleBridge,
                enabled = state.transport.running || state.endpointValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.transport.running) WarningOrange else SignalTeal,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.transport.running) "Stop bridge" else "Start bridge")
            }
        }

        SectionCard("Background") {
            StatusLine(
                "Gamepad capture",
                if (state.inputFocused) "Active" else "Paused · sending neutral",
            )
            StatusLine(
                "Floating bubble",
                if (state.overlayAllowed) "Allowed" else "Not allowed",
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "While the bridge is active you can leave the app: a floating bubble keeps the gamepad " +
                    "connected. If you tap another app, the bridge sends neutral until you tap the bubble. " +
                    "The power button stops Android from delivering gamepad input; use \"Screen off\" instead.",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedInk,
            )
            Spacer(Modifier.height(10.dp))
            if (!state.overlayAllowed) {
                OutlinedButton(onClick = onRequestOverlayPermission, modifier = Modifier.fillMaxWidth()) {
                    Text("Allow display over other apps")
                }
                Spacer(Modifier.height(6.dp))
            }
            Button(
                onClick = onDimScreen,
                enabled = state.transport.running,
                colors = ButtonDefaults.buttonColors(containerColor = HandshakeIndigo),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Screen off")
            }
        }

        SectionCard("Telemetry") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("PACKETS", state.transport.sentPackets.toString())
                Metric("SEQUENCE", state.transport.lastSequence?.toString() ?: "—")
                Metric("APPROX. RTT", state.transport.approximateRttMs?.let { "$it ms" } ?: "—")
            }
            state.transport.ack?.let { ack ->
                Spacer(Modifier.height(10.dp))
                Text(
                    "ACK seq=${ack.lastSequence} · ESP age=${ack.packetAgeMs} ms · status=0x${ack.rawStatus.hex()}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MutedInk,
                )
            }
        }

        SectionCard("Switch state") {
            val report = state.switchState
            Text(
                "B1 0x${report.bank(1).hex()}   B2 0x${report.bank(2).hex()}   B3 0x${report.bank(3).hex()}",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = HandshakeIndigo,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                report.buttons.sortedBy { it.ordinal }.joinToString(" · ") { it.label }
                    .ifBlank { "No buttons pressed" },
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            StickVisualizerPair(state.rawGamepad.axes, report, state.tuning)
            Text(
                "Gray dot: raw position · green dot: sent value",
                style = MaterialTheme.typography.labelMedium,
                color = MutedInk,
                modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
            )
            AxisValue("L2", report.l2)
            AxisValue("R2", report.r2)
        }

        SectionCard("Stick sensitivity") {
            StickTuningPanel(
                axes = state.rawGamepad.axes,
                report = state.switchState,
                tuning = state.tuning,
                onTuningChange = onTuningChange,
                onTuningReset = onTuningReset,
            )
        }

        SectionCard("Raw diagnostics") {
            val keys = state.rawGamepad.recentKeys
            Text("Recent buttons", style = MaterialTheme.typography.labelMedium, color = MutedInk)
            Text(
                if (keys.isEmpty()) "No events" else keys.joinToString("\n") {
                    "${if (it.pressed) "↓" else "↑"} ${it.name} (${it.keyCode})"
                },
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text("Reported axes", style = MaterialTheme.typography.labelMedium, color = MutedInk)
            if (state.rawGamepad.rawAxes.isEmpty()) {
                Text("Move a stick or trigger to see values.")
            } else {
                state.rawGamepad.rawAxes.forEach { RawAxisLine(it) }
            }
        }

        Text(
            "Without input focus neutral is sent; stopping the bridge sends three neutral states.",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedInk,
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }
}

@Composable
private fun ControllerSetupCard(
    controllerConnected: Boolean,
    onOpenBluetoothSettings: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(!controllerConnected) }
    LaunchedEffect(controllerConnected) {
        expanded = !controllerConnected
    }

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = BridgePanel),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Connect the DualShock 4", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (controllerConnected) "Gamepad ready" else "Complete these steps before starting",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (controllerConnected) SignalTeal else MutedInk,
                    )
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Hide" else "Show guide")
                }
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(Modifier.height(12.dp))
                SetupStep("1", "Hold SHARE + PS until the light bar flashes.")
                SetupStep("2", "Open Bluetooth and select “Wireless Controller”.")
                SetupStep("3", "Come back here and check that the DS4 dot is green.")
                SetupStep("4", "Connect the phone to the SwitchBridge Wi-Fi with password switchbridge.")
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onOpenBluetoothSettings,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open Bluetooth settings")
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Precise location is only requested to verify the Wi-Fi network name.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedInk,
                )
            }
        }
    }
}

@Composable
private fun SetupStep(number: String, instruction: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(HandshakeIndigo, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number,
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            instruction,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BridgeRail(ds4: Boolean, udp: Boolean, esp: Boolean, switchConnected: Boolean) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = BridgePanel),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RailNode("DS4", ds4, Modifier.weight(1f))
            RailArrow(ds4 && udp)
            RailNode("UDP", udp, Modifier.weight(1f))
            RailArrow(udp && esp)
            RailNode("ESP32", esp, Modifier.weight(1f))
            RailArrow(esp && switchConnected)
            RailNode("SWITCH", switchConnected, Modifier.weight(1f))
        }
    }
}

@Composable
private fun RailNode(label: String, active: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(13.dp)
                .background(if (active) SignalTeal else InactiveRail, CircleShape),
        )
        Spacer(Modifier.height(7.dp))
        Text(
            label,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
private fun RailArrow(active: Boolean) {
    Text(
        "→",
        color = if (active) SignalTeal else InactiveRail,
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(bottom = 18.dp),
    )
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = BridgePanel),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun WarningCard(message: String) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFFFE9E3)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("!", color = WarningOrange, fontWeight = FontWeight.Black, fontSize = 20.sp)
            Spacer(Modifier.width(10.dp))
            Text(message, color = WarningOrange, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MutedInk)
        Spacer(Modifier.width(14.dp))
        Text(value, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = MutedInk)
        Text(value, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
private fun AxisValue(label: String, value: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        Text(label, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.width(34.dp))
        LinearProgressIndicator(
            progress = { value / 255f },
            color = SignalTeal,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.weight(1f).height(7.dp),
        )
        Text(
            value.toString().padStart(3, '0'),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(42.dp),
        )
    }
}

@Composable
private fun RawAxisLine(axis: AxisDiagnostic) {
    Text(
        String.format(
            Locale.US,
            "%s (%d)  % .3f  [% .2f..% .2f] flat %.3f",
            axis.name,
            axis.axis,
            axis.rawValue,
            axis.min,
            axis.max,
            axis.flat,
        ),
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    )
}

private fun Int.hex(): String = toString(16).uppercase(Locale.US).padStart(2, '0')
