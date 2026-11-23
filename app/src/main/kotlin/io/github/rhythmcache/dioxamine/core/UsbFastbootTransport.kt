package io.github.rhythmcache.dioxamine.core

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.os.SystemClock
import io.github.rhythmcache.fastboot.FastbootException
import io.github.rhythmcache.fastboot.FastbootTransport

/**
 * Fastboot USB Interface Matcher (Class 0xFF, Subclass 0x42, Protocol 0x03)
 * and Android USB Transport implementation for fastboot-kt.
 */
object UsbFastbootTransport {
    val FASTBOOT_INTERFACE_MATCHER = UsbInterfaceMatcher(0xFF, 0x42, 0x03)

    fun findFastbootInterface(device: UsbDevice): UsbInterface? =
        UsbPacketTransport.findMatchingInterface(device, FASTBOOT_INTERFACE_MATCHER)

    fun findFastbootEndpoints(iface: UsbInterface): Pair<UsbEndpoint, UsbEndpoint>? =
        UsbPacketTransport.findAdbEndpoints(iface)
}

/**
 * @param timeoutMs per-call timeout for bulk OUT transfers.
 * @param readTimeoutMs overall idle limit for a bulk IN read. Fastboot devices can stay silent
 *   for a long time while flashing or erasing before sending OKAY/FAIL, so this is much larger
 *   than [timeoutMs]. Reads are polled in short slices (see [UsbTransfer.READ_POLL_SLICE_MS]).
 */
class AndroidUsbFastbootTransport(
    private val connection: UsbDeviceConnection,
    private val iface: UsbInterface,
    private val inEndpoint: UsbEndpoint,
    private val outEndpoint: UsbEndpoint,
    private val timeoutMs: Int = 10000,
    private val readTimeoutMs: Long = 60_000L,
) : FastbootTransport {

    // Largest packet-aligned request Android's usbfs will accept in a single call.
    private val writeChunkSize = UsbTransfer.alignedChunkSize(outEndpoint.maxPacketSize)

    @Volatile
    private var isClosed = false

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        if (isClosed) throw FastbootException.Io("USB transport is closed")
        var bytesWritten = 0
        while (bytesWritten < length) {
            if (isClosed) throw FastbootException.Io("USB transport closed during write")
            val chunkSize = minOf(length - bytesWritten, writeChunkSize)
            val ret = connection.bulkTransfer(outEndpoint, buffer, offset + bytesWritten, chunkSize, timeoutMs)
            if (ret <= 0) {
                throw FastbootException.Io("USB bulk write failed ($ret)")
            }
            bytesWritten += ret
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (isClosed) throw FastbootException.Io("USB transport is closed")

        val deadline = SystemClock.elapsedRealtime() + readTimeoutMs
        var zlpCount = 0
        while (true) {
            val start = SystemClock.elapsedRealtime()
            val ret = connection.bulkTransfer(inEndpoint, buffer, offset, length, UsbTransfer.READ_POLL_SLICE_MS)
            if (ret > 0) return ret
            if (isClosed) throw FastbootException.Io("USB transport closed during read")

            val now = SystemClock.elapsedRealtime()
            if (ret == 0) {
                // Zero-length packet: the device terminated a transfer on a packet boundary.
                // It carries no data, so keep waiting for the real response.
                if (++zlpCount > UsbTransfer.MAX_CONSECUTIVE_ZLP) {
                    throw FastbootException.Io("USB bulk read: too many consecutive zero-length packets")
                }
            } else {
                // ret < 0: Android reports both an idle timeout and a hard error as -1.
                // A timeout consumes (nearly) the whole poll slice; a real error such as an
                // unplug or endpoint stall returns right away.
                if (now - start < UsbTransfer.READ_POLL_SLICE_MS / 2) {
                    throw FastbootException.Io("USB bulk read failed ($ret)")
                }
                zlpCount = 0
            }
            if (now >= deadline) {
                throw FastbootException.Io("USB bulk read timed out after ${readTimeoutMs}ms")
            }
        }
    }

    override fun reset() {
        // Reset USB interface if needed
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        runCatching { connection.releaseInterface(iface) }
        runCatching { connection.close() }
    }
}
