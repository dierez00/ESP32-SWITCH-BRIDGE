package com.switchbridge.controller.mapping

import android.view.KeyEvent
import com.switchbridge.controller.domain.ControllerTuning
import com.switchbridge.controller.domain.RawGamepadState
import com.switchbridge.controller.domain.SwitchButton
import com.switchbridge.controller.domain.SwitchControllerState

object GamepadMapper {
    fun map(raw: RawGamepadState, tuning: ControllerTuning): SwitchControllerState {
        if (!raw.connected) return SwitchControllerState.Neutral

        val keys = raw.pressedKeys
        val buttons = buildSet {
            mapKey(keys, KeyEvent.KEYCODE_BUTTON_X, SwitchButton.Y)
            mapKey(keys, KeyEvent.KEYCODE_BUTTON_Y, SwitchButton.X)
            mapKey(keys, KeyEvent.KEYCODE_BUTTON_A, SwitchButton.B)
            mapKey(keys, KeyEvent.KEYCODE_BUTTON_B, SwitchButton.A)
            mapKey(keys, KeyEvent.KEYCODE_BUTTON_L1, SwitchButton.L)
            mapKey(keys, KeyEvent.KEYCODE_BUTTON_R1, SwitchButton.R)
            mapKey(keys, KeyEvent.KEYCODE_BUTTON_SELECT, SwitchButton.MINUS)
            mapKey(keys, KeyEvent.KEYCODE_BUTTON_START, SwitchButton.PLUS)
            mapKey(keys, KeyEvent.KEYCODE_BUTTON_THUMBL, SwitchButton.LEFT_STICK)
            mapKey(keys, KeyEvent.KEYCODE_BUTTON_THUMBR, SwitchButton.RIGHT_STICK)
            mapKey(keys, KeyEvent.KEYCODE_BUTTON_MODE, SwitchButton.HOME)
            mapKey(keys, KeyEvent.KEYCODE_BUTTON_1, SwitchButton.CAPTURE)

            if (KeyEvent.KEYCODE_DPAD_DOWN in keys || raw.axes.hatY > 0.5f) add(SwitchButton.DOWN)
            if (KeyEvent.KEYCODE_DPAD_UP in keys || raw.axes.hatY < -0.5f) add(SwitchButton.UP)
            if (KeyEvent.KEYCODE_DPAD_RIGHT in keys || raw.axes.hatX > 0.5f) add(SwitchButton.RIGHT)
            if (KeyEvent.KEYCODE_DPAD_LEFT in keys || raw.axes.hatX < -0.5f) add(SwitchButton.LEFT)

            if (KeyEvent.KEYCODE_BUTTON_L2 in keys || raw.axes.l2 > 0.5f) add(SwitchButton.ZL)
            if (KeyEvent.KEYCODE_BUTTON_R2 in keys || raw.axes.r2 > 0.5f) add(SwitchButton.ZR)
        }

        val (lx, ly) = StickProcessor.process(raw.axes.lx, raw.axes.ly, tuning.left)
        val (rx, ry) = StickProcessor.process(raw.axes.rx, raw.axes.ry, tuning.right)

        return SwitchControllerState(
            buttons = buttons,
            lx = AxisNormalizer.quantizeStick(lx),
            ly = AxisNormalizer.quantizeStick(ly, invert = true),
            rx = AxisNormalizer.quantizeStick(rx),
            ry = AxisNormalizer.quantizeStick(ry, invert = true),
            l2 = AxisNormalizer.quantizeTrigger(raw.axes.l2),
            r2 = AxisNormalizer.quantizeTrigger(raw.axes.r2),
        )
    }

    private fun MutableSet<SwitchButton>.mapKey(
        keys: Set<Int>,
        keyCode: Int,
        button: SwitchButton,
    ) {
        if (keyCode in keys) add(button)
    }
}

