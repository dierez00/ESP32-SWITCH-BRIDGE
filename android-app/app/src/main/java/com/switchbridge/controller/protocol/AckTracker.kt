package com.switchbridge.controller.protocol

class AckTracker {
    private val sentAtMs = LongArray(256) { NOT_SENT }

    @Synchronized
    fun reset() {
        sentAtMs.fill(NOT_SENT)
    }

    @Synchronized
    fun recordSent(sequence: Int, timestampMs: Long) {
        sentAtMs[sequence and 0xFF] = timestampMs
    }

    @Synchronized
    fun roundTripMs(sequence: Int, receivedAtMs: Long): Long? {
        val sent = sentAtMs[sequence and 0xFF]
        return if (sent == NOT_SENT || receivedAtMs < sent) null else receivedAtMs - sent
    }

    private companion object {
        const val NOT_SENT = Long.MIN_VALUE
    }
}
