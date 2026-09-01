package com.lioos.app.badkb

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.lioos.app.badusb.DuckyScript
import com.lioos.app.badusb.DuckyStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * BadKB — the phone acts as a **Bluetooth HID keyboard** and injects keystrokes
 * into a paired host (PC / phone / TV). Uses Android's BluetoothHidDevice API
 * (API 28+), so it needs no root and no external hardware.
 *
 * Flow: register the HID app → the phone is now a BT keyboard → pair with the
 * target from system Bluetooth settings → connect here → send a Ducky script.
 *
 * Authorized use only: run payloads against machines you own or may test.
 */
@SuppressLint("MissingPermission")
class BadKbManager(context: Context) {

    private val appContext = context.applicationContext
    private val adapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private var hid: BluetoothHidDevice? = null
    private var host: BluetoothDevice? = null

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log

    private val _state = MutableStateFlow("idle")
    val state: StateFlow<String> = _state

    private val _registered = MutableStateFlow(false)
    val registered: StateFlow<Boolean> = _registered

    fun supported(): Boolean = Build.VERSION.SDK_INT >= 28 && adapter != null

    private fun log(m: String) { _log.value = (listOf("• $m") + _log.value).take(200) }

    // Standard boot-keyboard HID report descriptor → 8-byte reports
    // [modifier, reserved, k1..k6], matching DuckyScript output.
    private val reportDescriptor = byteArrayOf(
        0x05, 0x01, 0x09, 0x06, 0xA1.toByte(), 0x01,
        0x05, 0x07, 0x19, 0xE0.toByte(), 0x29, 0xE7.toByte(),
        0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95.toByte(), 0x08, 0x81.toByte(), 0x02,
        0x95.toByte(), 0x01, 0x75, 0x08, 0x81.toByte(), 0x01,
        0x95.toByte(), 0x06, 0x75, 0x08, 0x15, 0x00, 0x25, 0x65,
        0x05, 0x07, 0x19, 0x00, 0x29, 0x65, 0x81.toByte(), 0x00,
        0xC0.toByte()
    )

    @RequiresApi(28)
    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            _registered.value = registered
            log(if (registered) "HID keyboard registered — now pair the target with this phone"
                else "HID app unregistered")
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            host = if (state == BluetoothProfile.STATE_CONNECTED) device else host
            _state.value = when (state) {
                BluetoothProfile.STATE_CONNECTED -> "connected: ${device.name ?: device.address}"
                BluetoothProfile.STATE_CONNECTING -> "connecting…"
                BluetoothProfile.STATE_DISCONNECTED -> "disconnected"
                else -> "state=$state"
            }
            log(_state.value)
        }
    }

    @RequiresApi(28)
    fun register() {
        if (!supported()) { log("needs Android 9+ and Bluetooth"); return }
        adapter.getProfileProxy(appContext, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile != BluetoothProfile.HID_DEVICE) return
                hid = proxy as BluetoothHidDevice
                val sdp = BluetoothHidDeviceAppSdpSettings(
                    "lioOS BadKB",
                    "lioOS Bluetooth keyboard",
                    "lioOS",
                    BluetoothHidDevice.SUBCLASS1_KEYBOARD,
                    reportDescriptor
                )
                val ok = hid?.registerApp(
                    sdp, null, null, appContext.mainExecutor, callback
                ) ?: false
                log("registerApp -> $ok")
            }

            override fun onServiceDisconnected(profile: Int) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    hid = null; _registered.value = false
                }
            }
        }, BluetoothProfile.HID_DEVICE)
    }

    /** Paired devices the phone can try to connect to as a keyboard. */
    fun bondedTargets(): List<BluetoothDevice> =
        try { adapter?.bondedDevices?.toList() ?: emptyList() } catch (e: Exception) { emptyList() }

    @RequiresApi(28)
    fun connect(device: BluetoothDevice) {
        val h = hid ?: run { log("register first"); return }
        host = device
        val ok = h.connect(device)
        log("connect(${device.name ?: device.address}) -> $ok")
    }

    @RequiresApi(28)
    fun disconnect() {
        val h = hid ?: return
        host?.let { h.disconnect(it) }
    }

    @RequiresApi(28)
    fun unregister() {
        hid?.unregisterApp()
        _registered.value = false
    }

    /** Compiles the Ducky script and types it on the connected host. */
    @RequiresApi(28)
    fun sendScript(script: String) {
        val h = hid ?: run { log("register first"); return }
        val target = host ?: run { log("no connected host"); return }
        val steps = DuckyScript().compile(script)
        var sent = 0
        for (step in steps) {
            when (step) {
                is DuckyStep.Delay -> Thread.sleep(step.ms)
                is DuckyStep.Report -> {
                    // reportId 0 (descriptor has no report ID)
                    val ok = h.sendReport(target, 0, step.bytes)
                    if (ok) sent++ else log("sendReport failed")
                    Thread.sleep(6) // small gap so the host registers each key
                }
            }
        }
        log("sent $sent reports")
    }
}
