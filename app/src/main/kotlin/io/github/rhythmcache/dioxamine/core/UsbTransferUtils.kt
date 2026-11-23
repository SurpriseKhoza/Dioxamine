package io.github.rhythmcache.dioxamine.core

/**
 * Shared tuning constants and helpers for the Android USB host bulk transports
 * (ADB and Fastboot).
 */
internal object UsbTransfer {
    /**
     * Android's usbfs bulk path caps a single bulkTransfer() at 16 KiB, so this is the largest
     * request worth issuing per call.
     */
    const val MAX_USBFS_CHUNK = 16 * 1024

    /**
     * Idle-poll slice for blocking reads. Reads are issued in slices so a long-running device
     * operation (large flash, erase) does not trip a single short timeout, while a real error
     * (unplug, stall) still surfaces immediately.
     */
    const val READ_POLL_SLICE_MS = 1_000

    /** Upper bound on back-to-back zero-length packets we will skip before giving up. */
    const val MAX_CONSECUTIVE_ZLP = 16

    /**
     * Largest transfer size <= [MAX_USBFS_CHUNK] that is a whole multiple of [maxPacketSize].
     * Keeping transfers packet-aligned avoids short-packet ambiguity on IN endpoints and gives
     * the best throughput on OUT endpoints.
     */
    fun alignedChunkSize(maxPacketSize: Int): Int {
        if (maxPacketSize <= 0) return MAX_USBFS_CHUNK
        val aligned = (MAX_USBFS_CHUNK / maxPacketSize) * maxPacketSize
        return if (aligned > 0) aligned else maxPacketSize
    }
}
