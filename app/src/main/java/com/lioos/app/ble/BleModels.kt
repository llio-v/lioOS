package com.lioos.app.ble

data class BleDeviceInfo(
    val address: String,
    val name: String?,
    val rssi: Int,
    val connectable: Boolean,
    val serviceUuids: List<String>,
    val manufacturerHex: String?
)

data class GattCharacteristic(
    val uuid: String,
    val properties: List<String>,
    val valueHex: String? = null
)

data class GattServiceInfo(
    val uuid: String,
    val characteristics: List<GattCharacteristic>
)
