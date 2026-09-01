package com.lioos.app.badusb

import android.content.Context
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber

/**
 * BadUSB via an EXTERNAL board — the NO-ROOT path.
 *
 * Connect an ESP32 / Digispark / "USB Rubber Ducky"-style board over USB-OTG.
 * Flash it with a firmware that: (a) enumerates as a USB HID keyboard to the
 * target PC, and (b) exposes a USB-serial control channel to the phone. We
 * push the raw Ducky Script over serial; the board replays the keystrokes.
 *
 * This is the practical way to get real HID injection from an un-rooted phone,
 * because stock Android can't be a USB keyboard on its own.
 */
class BadUsbSerial(private val context: Context) {

    private var port: UsbSerialPort? = null

    fun listDevices(): List<String> {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        return UsbSerialProber.getDefaultProber()
            .findAllDrivers(manager)
            .map { it.device.deviceName + " (VID:${it.device.vendorId})" }
    }

    fun connect(baud: Int = 115200): Boolean {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
        val driver = drivers.firstOrNull() ?: return false
        val connection = manager.openDevice(driver.device) ?: return false
        val p = driver.ports.first()
        p.open(connection)
        p.setParameters(baud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        port = p
        return true
    }

    /** Sends the raw Ducky Script text; the board firmware interprets it. */
    fun sendScript(script: String, onLog: (String) -> Unit) {
        val p = port ?: run { onLog("no serial device connected"); return }
        try {
            val payload = (script.trimEnd() + "\n").toByteArray()
            p.write(payload, 2000)
            onLog("sent ${payload.size} bytes to board")
        } catch (e: Exception) {
            onLog("serial write failed: ${e.message}")
        }
    }

    fun disconnect() {
        try { port?.close() } catch (_: Exception) {}
        port = null
    }
}
