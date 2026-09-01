package com.lioos.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import com.lioos.app.nfc.Hex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * BLE scanning + GATT exploration. The write/fuzz helpers are meant for
 * testing YOUR OWN devices (an ESP32, a dev board, your own peripheral) —
 * enumerating services and probing characteristics you are authorized to poke.
 */
@SuppressLint("MissingPermission")
class BleManager(context: Context) {

    private val btManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter = btManager.adapter
    private val appContext = context.applicationContext

    private val _devices = MutableStateFlow<List<BleDeviceInfo>>(emptyList())
    val devices: StateFlow<List<BleDeviceInfo>> = _devices

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    private val _services = MutableStateFlow<List<GattServiceInfo>>(emptyList())
    val services: StateFlow<List<GattServiceInfo>> = _services

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log

    private val found = LinkedHashMap<String, BleDeviceInfo>()
    private var gatt: BluetoothGatt? = null

    fun isReady(): Boolean = adapter != null && adapter.isEnabled

    private fun log(msg: String) {
        _log.value = (listOf("• $msg") + _log.value).take(200)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val dev = result.device
            val rec = result.scanRecord
            val mfg = rec?.manufacturerSpecificData
            var mfgHex: String? = null
            if (mfg != null && mfg.size() > 0) {
                val id = mfg.keyAt(0)
                mfgHex = String.format("%04X:", id) + Hex.encode(mfg.valueAt(0))
            }
            val info = BleDeviceInfo(
                address = dev.address,
                name = rec?.deviceName ?: dev.name,
                rssi = result.rssi,
                connectable = if (android.os.Build.VERSION.SDK_INT >= 26)
                    result.isConnectable else true,
                serviceUuids = rec?.serviceUuids?.map { it.uuid.toString() } ?: emptyList(),
                manufacturerHex = mfgHex
            )
            found[dev.address] = info
            _devices.value = found.values.sortedByDescending { it.rssi }
        }

        override fun onScanFailed(errorCode: Int) {
            log("scan failed: $errorCode")
            _scanning.value = false
        }
    }

    fun startScan() {
        if (!isReady()) { log("Bluetooth is off"); return }
        found.clear()
        _devices.value = emptyList()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        adapter.bluetoothLeScanner?.startScan(null, settings, scanCallback)
        _scanning.value = true
        log("scanning…")
    }

    fun stopScan() {
        if (!isReady()) return
        adapter.bluetoothLeScanner?.stopScan(scanCallback)
        _scanning.value = false
        log("scan stopped")
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                log("connected, discovering services…")
                g.discoverServices()
            } else {
                log("disconnected")
                _services.value = emptyList()
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val list = g.services.map { svc ->
                GattServiceInfo(
                    uuid = svc.uuid.toString(),
                    characteristics = svc.characteristics.map { ch ->
                        GattCharacteristic(ch.uuid.toString(), propsOf(ch))
                    }
                )
            }
            _services.value = list
            log("found ${list.size} services")
        }

        override fun onCharacteristicRead(
            g: BluetoothGatt, ch: BluetoothGattCharacteristic, status: Int
        ) {
            log("read ${ch.uuid}: ${Hex.encode(ch.value)}")
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt, ch: BluetoothGattCharacteristic, status: Int
        ) {
            log("write ${ch.uuid} status=$status")
        }
    }

    fun connect(address: String) {
        val dev = adapter.getRemoteDevice(address)
        gatt?.close()
        gatt = dev.connectGatt(appContext, false, gattCallback)
        log("connecting to $address")
    }

    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
    }

    /** Write a hex value to a characteristic on YOUR device. */
    fun write(serviceUuid: String, charUuid: String, valueHex: String) {
        val g = gatt ?: run { log("not connected"); return }
        val svc = g.getService(java.util.UUID.fromString(serviceUuid)) ?: return
        val ch = svc.getCharacteristic(java.util.UUID.fromString(charUuid)) ?: return
        ch.value = Hex.decode(valueHex)
        g.writeCharacteristic(ch)
    }

    /**
     * Simple boundary/value fuzzer for a writable characteristic on your own
     * peripheral — sends a set of edge-case payloads and logs the result.
     */
    fun fuzz(serviceUuid: String, charUuid: String, maxLen: Int = 20) {
        val g = gatt ?: run { log("not connected"); return }
        val svc = g.getService(java.util.UUID.fromString(serviceUuid)) ?: return
        val ch = svc.getCharacteristic(java.util.UUID.fromString(charUuid)) ?: return
        val cases = listOf(
            ByteArray(0),
            byteArrayOf(0x00),
            byteArrayOf(0xFF.toByte()),
            ByteArray(maxLen) { 0x00 },
            ByteArray(maxLen) { 0xFF.toByte() },
            ByteArray(maxLen) { (it and 0xFF).toByte() },
            "A".repeat(maxLen).toByteArray()
        )
        cases.forEachIndexed { i, payload ->
            ch.value = payload
            val ok = g.writeCharacteristic(ch)
            log("fuzz #$i len=${payload.size} queued=$ok")
        }
    }

    private fun propsOf(ch: BluetoothGattCharacteristic): List<String> {
        val p = ch.properties
        val out = mutableListOf<String>()
        if (p and BluetoothGattCharacteristic.PROPERTY_READ != 0) out += "READ"
        if (p and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) out += "WRITE"
        if (p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) out += "WRITE_NR"
        if (p and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) out += "NOTIFY"
        if (p and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) out += "INDICATE"
        return out
    }
}
