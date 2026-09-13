package com.switchbridge.controller.network

import android.net.Network
import android.os.SystemClock
import com.switchbridge.controller.domain.ControllerSnapshot
import com.switchbridge.controller.domain.Endpoint
import com.switchbridge.controller.domain.EspAck
import com.switchbridge.controller.protocol.AckCodec
import com.switchbridge.controller.protocol.AckTracker
import com.switchbridge.controller.protocol.PacketCodec
import com.switchbridge.controller.protocol.SafetyPolicy
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

data class TransportStatus(
    val running: Boolean = false,
    val sentPackets: Long = 0,
    val lastSequence: Int? = null,
    val ack: EspAck? = null,
    val ackFresh: Boolean = false,
    val approximateRttMs: Long? = null,
    val lastError: String? = null,
)

class UdpBridge {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lifecycleMutex = Mutex()
    private val sendMutex = Mutex()
    private val ackTracker = AckTracker()
    private val _status = MutableStateFlow(TransportStatus())
    val status: StateFlow<TransportStatus> = _status.asStateFlow()

    @Volatile
    private var active = false
    private var socket: DatagramSocket? = null
    private var senderJob: Job? = null
    private var receiverJob: Job? = null
    private var timeoutJob: Job? = null
    private var sequence = 0
    private var snapshotState: StateFlow<ControllerSnapshot>? = null
    @Volatile
    private var lastAckAtMs: Long? = null

    suspend fun start(
        network: Network,
        endpoint: Endpoint,
        snapshots: StateFlow<ControllerSnapshot>,
    ): Boolean = lifecycleMutex.withLock {
        if (active) return@withLock true
        val opened = try {
            withContext(Dispatchers.IO) {
                DatagramSocket().also { datagramSocket ->
                    network.bindSocket(datagramSocket)
                    datagramSocket.connect(
                        InetSocketAddress(InetAddress.getByName(endpoint.address), endpoint.port),
                    )
                    datagramSocket.soTimeout = RECEIVE_TIMEOUT_MS
                }
            }
        } catch (error: Exception) {
            _status.value = TransportStatus(lastError = error.userMessage())
            return@withLock false
        }

        socket = opened
        snapshotState = snapshots
        sequence = 0
        ackTracker.reset()
        lastAckAtMs = null
        active = true
        _status.value = TransportStatus(running = true)
        senderJob = scope.launch { senderLoop() }
        receiverJob = scope.launch { receiverLoop(opened) }
        timeoutJob = scope.launch { timeoutLoop() }
        true
    }

    suspend fun stopWithNeutral() = lifecycleMutex.withLock {
        if (!active) return@withLock
        active = false
        senderJob?.cancelAndJoin()
        senderJob = null

        SafetyPolicy.stopBurst().forEachIndexed { index, neutral ->
            sendPacket(neutral)
            if (index < SafetyPolicy.STOP_NEUTRAL_PACKETS - 1) delay(SafetyPolicy.SEND_PERIOD_MS)
        }

        socket?.close()
        socket = null
        receiverJob?.cancelAndJoin()
        timeoutJob?.cancelAndJoin()
        receiverJob = null
        timeoutJob = null
        snapshotState = null
        _status.update { it.copy(running = false, ackFresh = false) }
    }

    fun sendImmediateNeutral() {
        if (!active) return
        scope.launch { sendPacket(SafetyPolicy.disconnectedPacket()) }
    }

    fun requestStopWithNeutral(): Job = scope.launch { stopWithNeutral() }

    fun closeAfterNeutral() {
        scope.launch {
            stopWithNeutral()
            scope.coroutineContext[Job]?.cancel()
        }
    }

    private suspend fun senderLoop() {
        var nextSendNs = SystemClock.elapsedRealtimeNanos()
        while (scope.isActive && active) {
            snapshotState?.value?.let { sendPacket(it) }
            nextSendNs += SafetyPolicy.SEND_PERIOD_MS * NANOS_PER_MILLISECOND
            val remainingNs = nextSendNs - SystemClock.elapsedRealtimeNanos()
            if (remainingNs > NANOS_PER_MILLISECOND) {
                delay(remainingNs / NANOS_PER_MILLISECOND)
            } else if (remainingNs > 0) {
                yield()
            } else if (remainingNs < -SafetyPolicy.SEND_PERIOD_MS * NANOS_PER_MILLISECOND) {
                nextSendNs = SystemClock.elapsedRealtimeNanos()
            }
        }
    }

    private suspend fun sendPacket(snapshot: ControllerSnapshot) = sendMutex.withLock {
        val currentSocket = socket ?: return@withLock
        val currentSequence = sequence
        val bytes = PacketCodec.encode(
            report = snapshot.report,
            controllerConnected = snapshot.connected,
            sequence = currentSequence,
        )
        try {
            val sentAt = SystemClock.elapsedRealtime()
            withContext(Dispatchers.IO) {
                currentSocket.send(DatagramPacket(bytes, bytes.size))
            }
            ackTracker.recordSent(currentSequence, sentAt)
            sequence = (sequence + 1) and 0xFF
            _status.update {
                it.copy(
                    sentPackets = it.sentPackets + 1,
                    lastSequence = currentSequence,
                    lastError = null,
                )
            }
        } catch (error: Exception) {
            if (active) _status.update { it.copy(lastError = error.userMessage()) }
        }
    }

    private suspend fun receiverLoop(currentSocket: DatagramSocket) {
        val buffer = ByteArray(64)
        val packet = DatagramPacket(buffer, buffer.size)
        while (scope.isActive && active) {
            try {
                packet.length = buffer.size
                currentSocket.receive(packet)
                val ack = AckCodec.decode(packet.data, packet.length) ?: continue
                val receivedAt = SystemClock.elapsedRealtime()
                lastAckAtMs = receivedAt
                _status.update {
                    it.copy(
                        ack = ack,
                        ackFresh = true,
                        approximateRttMs = ackTracker.roundTripMs(ack.lastSequence, receivedAt),
                        lastError = null,
                    )
                }
            } catch (_: SocketTimeoutException) {
                // Wake periodically so cancellation and the ACK watchdog stay responsive.
            } catch (_: SocketException) {
                if (active) _status.update { it.copy(lastError = "UDP socket closed unexpectedly") }
                return
            } catch (error: Exception) {
                if (active) _status.update { it.copy(lastError = error.userMessage()) }
            }
        }
    }

    private suspend fun timeoutLoop() {
        while (scope.isActive && active) {
            val now = SystemClock.elapsedRealtime()
            val fresh = SafetyPolicy.isAckFresh(lastAckAtMs, now)
            _status.update { current ->
                if (current.ackFresh == fresh) current else current.copy(ackFresh = fresh)
            }
            delay(ACK_CHECK_PERIOD_MS)
        }
    }

    private fun Throwable.userMessage(): String =
        localizedMessage?.takeIf { it.isNotBlank() } ?: javaClass.simpleName

    private companion object {
        const val RECEIVE_TIMEOUT_MS = 250
        const val ACK_CHECK_PERIOD_MS = 100L
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
