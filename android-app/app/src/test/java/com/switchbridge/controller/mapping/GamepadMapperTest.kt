package com.switchbridge.controller.mapping

import android.view.KeyEvent
import com.switchbridge.controller.domain.ControllerTuning
import com.switchbridge.controller.domain.NormalizedAxes
import com.switchbridge.controller.domain.RawGamepadState
import com.switchbridge.controller.domain.StickTuning
import com.switchbridge.controller.domain.SwitchButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GamepadMapperTest {
    @Test
    fun mapsEveryDefaultDigitalButton() {
        val raw = connected(
            keys = setOf(
                KeyEvent.KEYCODE_BUTTON_X,
                KeyEvent.KEYCODE_BUTTON_A,
                KeyEvent.KEYCODE_BUTTON_B,
                KeyEvent.KEYCODE_BUTTON_Y,
                KeyEvent.KEYCODE_BUTTON_L1,
                KeyEvent.KEYCODE_BUTTON_R1,
                KeyEvent.KEYCODE_BUTTON_L2,
                KeyEvent.KEYCODE_BUTTON_R2,
                KeyEvent.KEYCODE_BUTTON_SELECT,
                KeyEvent.KEYCODE_BUTTON_START,
                KeyEvent.KEYCODE_BUTTON_THUMBL,
                KeyEvent.KEYCODE_BUTTON_THUMBR,
                KeyEvent.KEYCODE_BUTTON_MODE,
                KeyEvent.KEYCODE_BUTTON_1,
            ),
        )

        assertEquals(
            setOf(
                SwitchButton.Y, SwitchButton.B, SwitchButton.A, SwitchButton.X,
                SwitchButton.L, SwitchButton.R, SwitchButton.ZL, SwitchButton.ZR,
                SwitchButton.MINUS, SwitchButton.PLUS,
                SwitchButton.LEFT_STICK, SwitchButton.RIGHT_STICK,
                SwitchButton.HOME, SwitchButton.CAPTURE,
            ),
            GamepadMapper.map(raw, ControllerTuning.Default).buttons,
        )
    }

    @Test
    fun combinesKeyAndHatDpadSources() {
        val mapped = GamepadMapper.map(
            connected(
                keys = setOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_DOWN),
                axes = NormalizedAxes(hatX = 1f, hatY = -1f),
            ),
            ControllerTuning.Default,
        )
        assertTrue(mapped.buttons.containsAll(SwitchButton.entries.filter { it.bank == 3 && it.mask < 0x10 }))
    }

    @Test
    fun analogTriggerThresholdIsStrictlyGreaterThanHalf() {
        val atThreshold = map(connected(axes = NormalizedAxes(l2 = 0.5f, r2 = 0.5f)))
        assertFalse(SwitchButton.ZL in atThreshold.buttons)
        assertFalse(SwitchButton.ZR in atThreshold.buttons)

        val above = map(connected(axes = NormalizedAxes(l2 = 0.501f, r2 = 1f)))
        assertTrue(SwitchButton.ZL in above.buttons)
        assertTrue(SwitchButton.ZR in above.buttons)
    }

    @Test
    fun disconnectedStateIsAlwaysNeutral() {
        val mapped = GamepadMapper.map(
            RawGamepadState(
                connected = false,
                pressedKeys = setOf(KeyEvent.KEYCODE_BUTTON_A),
                axes = NormalizedAxes(lx = 1f, l2 = 1f),
            ),
            ControllerTuning.Default,
        )
        assertEquals(128, mapped.lx)
        assertEquals(0, mapped.l2)
        assertTrue(mapped.buttons.isEmpty())
    }

    @Test
    fun restQuantizesExactlyToCenterEvenWithDrift() {
        val atRest = map(connected())
        assertEquals(listOf(128, 128, 128, 128), listOf(atRest.lx, atRest.ly, atRest.rx, atRest.ry))

        val drift = map(connected(axes = NormalizedAxes(lx = 0.05f, ly = -0.04f, rx = -0.06f, ry = 0.03f)))
        assertEquals(listOf(128, 128, 128, 128), listOf(drift.lx, drift.ly, drift.rx, drift.ry))
    }

    @Test
    fun extremesQuantizeToFullRangeWithProtocolYInversion() {
        val horizontal = map(connected(axes = NormalizedAxes(lx = 1f, rx = -1f)))
        assertEquals(listOf(255, 128, 0, 128), listOf(horizontal.lx, horizontal.ly, horizontal.rx, horizontal.ry))

        // Android: negative Y is up; the protocol inverts it (up = 255).
        val vertical = map(connected(axes = NormalizedAxes(ly = -1f, ry = 1f)))
        assertEquals(listOf(128, 255, 128, 0), listOf(vertical.lx, vertical.ly, vertical.rx, vertical.ry))
    }

    @Test
    fun appliesTuningPerStick() {
        val tuning = ControllerTuning(
            left = StickTuning(innerDeadZone = 0.4f),
            right = StickTuning(innerDeadZone = 0f, outerDeadZone = 1f, invertY = true),
        )
        val mapped = GamepadMapper.map(
            connected(axes = NormalizedAxes(lx = 0.3f, rx = 0.5f, ry = -1f)),
            tuning,
        )
        assertEquals(128, mapped.lx)
        // rx=0.5, ry=-1 → magnitude > 1 clamped to 1; invertY makes "up" be sent as down.
        assertTrue(mapped.rx in 129..254)
        assertTrue(mapped.ry < 128)
    }

    private fun map(raw: RawGamepadState) = GamepadMapper.map(raw, ControllerTuning.Default)

    private fun connected(
        keys: Set<Int> = emptySet(),
        axes: NormalizedAxes = NormalizedAxes(),
    ) = RawGamepadState(connected = true, pressedKeys = keys, axes = axes)
}

