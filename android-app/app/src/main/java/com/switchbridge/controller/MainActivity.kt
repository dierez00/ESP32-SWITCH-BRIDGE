package com.switchbridge.controller

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.switchbridge.controller.ui.SwitchBridgeScreen
import com.switchbridge.controller.ui.theme.SwitchBridgeTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<BridgeViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            var permissionRequestedThisLaunch by rememberSaveable { mutableStateOf(false) }
            var startAfterPermission by rememberSaveable { mutableStateOf(false) }
            var screenDimmed by rememberSaveable { mutableStateOf(false) }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) {
                viewModel.refreshWifi()
                if (startAfterPermission) viewModel.startBridge()
                startAfterPermission = false
            }

            LaunchedEffect(Unit) {
                if (STARTUP_PERMISSIONS.any(::isMissing) && !permissionRequestedThisLaunch) {
                    permissionRequestedThisLaunch = true
                    startAfterPermission = false
                    permissionLauncher.launch(STARTUP_PERMISSIONS)
                }
            }

            DisposableEffect(state.transport.running) {
                if (state.transport.running) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                onDispose {
                    if (state.transport.running) {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
            }

            LaunchedEffect(state.transport.running) {
                if (!state.transport.running) screenDimmed = false
            }

            DisposableEffect(screenDimmed) {
                applyScreenDimmed(screenDimmed)
                onDispose { if (screenDimmed) applyScreenDimmed(false) }
            }

            SwitchBridgeTheme {
                Box(Modifier.fillMaxSize()) {
                    SwitchBridgeScreen(
                        state = state,
                        onAddressChange = viewModel::updateAddress,
                        onPortChange = viewModel::updatePort,
                        onOpenBluetoothSettings = {
                            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        },
                        onToggleBridge = {
                            if (state.transport.running) {
                                viewModel.stopBridge()
                            } else if (isMissing(Manifest.permission.ACCESS_FINE_LOCATION)) {
                                startAfterPermission = true
                                permissionLauncher.launch(STARTUP_PERMISSIONS)
                            } else {
                                viewModel.refreshWifi()
                                viewModel.startBridge()
                            }
                        },
                        onTuningChange = viewModel::updateTuning,
                        onTuningReset = viewModel::resetTuning,
                        onRequestOverlayPermission = {
                            startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    "package:$packageName".toUri(),
                                ),
                            )
                        },
                        onDimScreen = { screenDimmed = true },
                    )

                    if (screenDimmed) {
                        BackHandler { screenDimmed = false }
                        DimmedScreen(onExit = { screenDimmed = false })
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onActivityStarted()
    }

    override fun onStop() {
        viewModel.onActivityStopped()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        viewModel.onWindowFocusChanged(hasFocus)
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        viewModel.onKeyEvent(event) || super.dispatchKeyEvent(event)

    override fun onGenericMotionEvent(event: MotionEvent): Boolean =
        viewModel.onMotionEvent(event) || super.onGenericMotionEvent(event)

    private fun isMissing(permission: String): Boolean =
        checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED

    /** Minimum brightness and hidden bars: looks off on OLED, but the window keeps gamepad focus. */
    private fun applyScreenDimmed(dimmed: Boolean) {
        window.attributes = window.attributes.apply {
            screenBrightness = if (dimmed) {
                DIMMED_BRIGHTNESS
            } else {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
        }
        val insets = WindowCompat.getInsetsController(window, window.decorView)
        if (dimmed) {
            insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insets.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insets.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private companion object {
        const val DIMMED_BRIGHTNESS = 0.01f

        val STARTUP_PERMISSIONS = buildList {
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }
}

@Composable
private fun DimmedScreen(onExit: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onExit() }) },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            "Bridge active · double tap to return",
            color = Color.White.copy(alpha = 0.18f),
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 32.dp),
        )
    }
}
