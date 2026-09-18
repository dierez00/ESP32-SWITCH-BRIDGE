package com.switchbridge.controller.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import com.switchbridge.controller.BridgeController
import com.switchbridge.controller.InputOwner
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Floating bubble that keeps gamepad input focus while the app is in the background.
 * Android only delivers gamepad events to the focused window; if the user taps another app, it
 * takes focus and the bridge sends neutral until the bubble is tapped again.
 */
class ControllerOverlay(
    private val context: Context,
    private val controller: BridgeController,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var bubble: CaptureBubble? = null
    private val params = WindowManager.LayoutParams(
        0,
        0,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        title = "SwitchBridge"
    }

    val showing: Boolean get() = bubble != null

    fun show(): Boolean {
        if (bubble != null) return true
        if (!Settings.canDrawOverlays(context)) return false
        val density = context.resources.displayMetrics.density
        val size = (BUBBLE_SIZE_DP * density).roundToInt()
        params.width = size
        params.height = size
        if (params.x == 0 && params.y == 0) {
            params.x = (context.resources.displayMetrics.widthPixels - size - 12 * density).roundToInt()
            params.y = (context.resources.displayMetrics.heightPixels * 0.3f).roundToInt()
        }
        setFocusable(true)
        val view = CaptureBubble(context)
        return try {
            windowManager.addView(view, params)
            bubble = view
            true
        } catch (_: RuntimeException) {
            false
        }
    }

    fun hide() {
        val view = bubble ?: return
        bubble = null
        controller.onInputFocusChanged(InputOwner.OVERLAY, false)
        runCatching { windowManager.removeViewImmediate(view) }
    }

    fun setLinkActive(active: Boolean) {
        bubble?.linkActive = active
    }

    private fun setFocusable(focusable: Boolean) {
        params.flags = if (focusable) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
    }

    private fun updateLayout() {
        val view = bubble ?: return
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    @SuppressLint("ViewConstructor")
    private inner class CaptureBubble(context: Context) : View(context) {
        private val density = resources.displayMetrics.density
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
            color = Color.WHITE
        }
        private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 12f * density
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        private var focused = false
        private var downRawX = 0f
        private var downRawY = 0f
        private var downX = 0
        private var downY = 0
        private var dragging = false

        var linkActive = false
            set(value) {
                field = value
                invalidate()
            }

        init {
            contentDescription = "SwitchBridge gamepad capture"
        }

        override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
            super.onWindowFocusChanged(hasWindowFocus)
            focused = hasWindowFocus
            controller.onInputFocusChanged(InputOwner.OVERLAY, hasWindowFocus)
            invalidate()
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (controller.onKeyEvent(event)) return true
            // A non-gamepad key (e.g. Back) cannot be forwarded to the app underneath:
            // give up focus until the user taps the bubble again.
            if (event.action == KeyEvent.ACTION_DOWN) {
                setFocusable(false)
                updateLayout()
            }
            return super.dispatchKeyEvent(event)
        }

        override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
            controller.onMotionEvent(event) || super.dispatchGenericMotionEvent(event)

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    downX = params.x
                    downY = params.y
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) dragging = true
                    if (dragging) {
                        params.x = downX + dx.roundToInt()
                        params.y = downY + dy.roundToInt()
                        updateLayout()
                    }
                }
                MotionEvent.ACTION_UP -> if (!dragging) performClick()
            }
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            if (params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0) {
                setFocusable(true)
                updateLayout()
            }
            return true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val radius = width / 2f - ring.strokeWidth
            fill.color = when {
                !focused -> PAUSED_COLOR
                linkActive -> ACTIVE_COLOR
                else -> WAITING_COLOR
            }
            canvas.drawCircle(width / 2f, height / 2f, radius, fill)
            canvas.drawCircle(width / 2f, height / 2f, radius, ring)
            val text = if (focused) "DS4" else "II"
            canvas.drawText(text, width / 2f, height / 2f - (label.ascent() + label.descent()) / 2f, label)
        }
    }

    private companion object {
        const val BUBBLE_SIZE_DP = 56
        const val ACTIVE_COLOR = 0xF000796B.toInt()
        const val WAITING_COLOR = 0xF03F51B5.toInt()
        const val PAUSED_COLOR = 0xE060787F.toInt()
    }
}
