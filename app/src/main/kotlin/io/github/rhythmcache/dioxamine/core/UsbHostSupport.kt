package io.github.rhythmcache.dioxamine.core

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/**
 * USB host (OTG) capability check, plus an optional root helper that declares the
 * android.hardware.usb.host feature through a systemless Magisk / KernelSU / APatch module.
 *
 * Some phones have a USB port, controller and kernel that already support host mode, but the
 * firmware never declares the feature, so PackageManager hides USB host from every app. Adding
 * the missing feature XML under /system/etc/permissions fixes exactly that case.
 *
 * It cannot add host mode to hardware or a kernel that does not support it.
 */
object UsbHostSupport {
    private const val MODULE_ID = "dioxamine_usbhost"
    private const val MODULE_DIR = "/data/adb/modules/$MODULE_ID"
    private const val OK_MARKER = "DIOXAMINE_OK"
    private const val ROOT_TIMEOUT_MS = 60_000L

    data class ShellResult(
        val ok: Boolean,
        val output: String,
    )

    fun hasUsbHost(context: Context): Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)

    suspend fun installModule(): ShellResult {
        val root = runAsRoot("id")
        if (!root.output.contains("uid=0")) {
            return ShellResult(false, root.output.ifBlank { "Root access was not granted." })
        }
        val result = runAsRoot(installScript())
        return ShellResult(result.output.contains(OK_MARKER), result.output.replace(OK_MARKER, "").trim())
    }

    suspend fun removeModule(): ShellResult {
        val root = runAsRoot("id")
        if (!root.output.contains("uid=0")) {
            return ShellResult(false, root.output.ifBlank { "Root access was not granted." })
        }
        val result = runAsRoot("[ -d $MODULE_DIR ] && touch $MODULE_DIR/remove\necho $OK_MARKER")
        return ShellResult(result.output.contains(OK_MARKER), result.output.replace(OK_MARKER, "").trim())
    }

    private fun installScript(): String =
        """
        mkdir -p $MODULE_DIR/system/etc/permissions
        rm -f $MODULE_DIR/remove $MODULE_DIR/disable
        cat > $MODULE_DIR/module.prop <<'EOF'
        id=$MODULE_ID
        name=Dioxamine USB Host Feature
        version=1.0
        versionCode=1
        author=Dioxamine
        description=Declares android.hardware.usb.host so apps can use USB OTG. Only helps if the hardware and kernel support host mode.
        EOF
        cat > $MODULE_DIR/system/etc/permissions/android.hardware.usb.host.xml <<'EOF'
        <?xml version="1.0" encoding="utf-8"?>
        <permissions>
            <feature name="android.hardware.usb.host" />
        </permissions>
        EOF
        chown -R 0:0 $MODULE_DIR
        chmod 755 $MODULE_DIR $MODULE_DIR/system $MODULE_DIR/system/etc $MODULE_DIR/system/etc/permissions
        chmod 644 $MODULE_DIR/module.prop $MODULE_DIR/system/etc/permissions/android.hardware.usb.host.xml
        chcon -R u:object_r:system_file:s0 $MODULE_DIR/system 2>/dev/null
        echo $OK_MARKER
        """.trimIndent()

    private suspend fun runAsRoot(script: String): ShellResult {
        val result =
            withTimeoutOrNull(ROOT_TIMEOUT_MS) {
                runInterruptible(Dispatchers.IO) {
                    val process =
                        try {
                            ProcessBuilder("su").redirectErrorStream(true).start()
                        } catch (e: IOException) {
                            return@runInterruptible ShellResult(false, "su is not available: ${e.message}")
                        }
                    try {
                        process.outputStream.bufferedWriter().use { writer ->
                            writer.write(script)
                            writer.write("\nexit\n")
                            writer.flush()
                        }
                        val text = process.inputStream.bufferedReader().readText()
                        process.waitFor()
                        ShellResult(true, text.trim())
                    } finally {
                        process.destroy()
                    }
                }
            }
        return result ?: ShellResult(false, "Timed out waiting for root access.")
    }
}
