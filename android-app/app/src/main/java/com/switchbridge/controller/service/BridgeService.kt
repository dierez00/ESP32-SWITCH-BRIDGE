package com.switchbridge.controller.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.switchbridge.controller.BridgeController
import com.switchbridge.controller.MainActivity
import com.switchbridge.controller.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the process alive while the UDP bridge is active and shows the floating bubble that
 * captures the gamepad when the app is not visible. Stops only when the bridge stops.
 */
class BridgeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var controller: BridgeController
    private lateinit var overlay: ControllerOverlay
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var observing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        controller = BridgeController.get(this)
        overlay = ControllerOverlay(this, controller)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NOTIFICATION_ID,
            buildNotification(NotificationStatus()),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        if (intent?.action == ACTION_STOP) {
            controller.stopBridge()
            // If already observing, stopSelf() arrives once the bridge finishes sending neutral.
            if (!observing) stopSelf()
            return START_NOT_STICKY
        }
        if (!controller.bridgeRunning.value) {
            stopSelf()
            return START_NOT_STICKY
        }
        acquireLocks()
        observe()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        overlay.hide()
        releaseLocks()
        super.onDestroy()
    }

    private fun observe() {
        if (observing) return
        observing = true

        scope.launch {
            controller.bridgeRunning.collect { running -> if (!running) stopSelf() }
        }
        scope.launch {
            combine(controller.bridgeRunning, controller.appVisible) { running, visible -> running && !visible }
                .distinctUntilChanged()
                .collect { wanted -> if (wanted) overlay.show() else overlay.hide() }
        }
        scope.launch {
            controller.uiState
                .map { state ->
                    NotificationStatus(
                        inputFocused = state.inputFocused,
                        espLinked = state.transport.ackFresh,
                        switchConnected = state.transport.ackFresh && state.transport.ack?.switchConnected == true,
                        overlayMissing = !state.overlayAllowed,
                    )
                }
                .distinctUntilChanged()
                .collect { status ->
                    overlay.setLinkActive(status.switchConnected)
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, buildNotification(status))
                }
        }
    }

    // No timeout on purpose: the session lasts as long as the user wants and is released when the bridge stops.
    @SuppressLint("WakelockTimeout")
    private fun acquireLocks() {
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SwitchBridge:bridge")
                .apply {
                    setReferenceCounted(false)
                    acquire()
                }
        }
        if (wifiLock == null) {
            wifiLock = applicationContext.getSystemService(WifiManager::class.java)
                .createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "SwitchBridge:udp")
                .apply {
                    setReferenceCounted(false)
                    acquire()
                }
        }
    }

    private fun releaseLocks() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        wifiLock?.takeIf { it.isHeld }?.release()
        wifiLock = null
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Bridge active",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows that the gamepad → Switch bridge keeps sending in the background."
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(status: NotificationStatus): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, BridgeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = when {
            !status.inputFocused && status.overlayMissing ->
                "Gamepad paused: open the app or allow the floating bubble"
            !status.inputFocused -> "Gamepad paused (sending neutral): tap the bubble or open the app"
            status.switchConnected -> "Gamepad active · Switch connected"
            status.espLinked -> "Gamepad active · waiting for Switch"
            else -> "Gamepad active · no response from ESP32"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bridge)
            .setContentTitle("Switch Bridge running")
            .setContentText(text)
            .setContentIntent(openApp)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private data class NotificationStatus(
        val inputFocused: Boolean = true,
        val espLinked: Boolean = false,
        val switchConnected: Boolean = false,
        val overlayMissing: Boolean = false,
    )

    companion object {
        private const val CHANNEL_ID = "bridge"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_STOP = "com.switchbridge.controller.action.STOP_BRIDGE"

        /** Returns false if Android rejects the service (e.g. it was requested from the background). */
        fun start(context: Context): Boolean = try {
            ContextCompat.startForegroundService(context, Intent(context, BridgeService::class.java))
            true
        } catch (_: IllegalStateException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
