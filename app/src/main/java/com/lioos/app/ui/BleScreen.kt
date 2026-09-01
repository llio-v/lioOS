package com.lioos.app.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lioos.app.ble.BleManager

private fun requiredBlePermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 31)
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    else
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

@Composable
fun BleScreen(modifier: Modifier = Modifier, ble: BleManager, activity: Activity) {
    val ctx = LocalContext.current
    val devices by ble.devices.collectAsStateWithLifecycle()
    val scanning by ble.scanning.collectAsStateWithLifecycle()
    val services by ble.services.collectAsStateWithLifecycle()
    val log by ble.log.collectAsStateWithLifecycle()

    var selected by remember { mutableStateOf<String?>(null) }
    var writeSvc by remember { mutableStateOf("") }
    var writeChar by remember { mutableStateOf("") }
    var writeVal by remember { mutableStateOf("") }

    fun ensurePerms(): Boolean {
        val missing = requiredBlePermissions().any {
            ActivityCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing) {
            ActivityCompat.requestPermissions(activity, requiredBlePermissions(), 42)
            return false
        }
        return true
    }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (ensurePerms()) {
                    if (scanning) ble.stopScan() else ble.startScan()
                }
            }) { Text(if (scanning) "Stop scan" else "Scan") }
            if (selected != null) {
                OutlinedButton(onClick = { ble.disconnect(); selected = null }) {
                    Text("Disconnect")
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("Devices (${devices.size})", style = MaterialTheme.typography.titleMedium)
        LazyColumn(Modifier.weight(1f)) {
            items(devices) { d ->
                ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Text("${d.name ?: "(no name)"}  ${d.rssi} dBm")
                        Text(d.address, fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall)
                        d.manufacturerHex?.let {
                            Text("mfg: $it", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = {
                            if (ensurePerms()) { ble.connect(d.address); selected = d.address }
                        }) { Text("Connect + enumerate") }
                    }
                }
            }
        }

        if (services.isNotEmpty()) {
            Text("GATT services", style = MaterialTheme.typography.titleMedium)
            LazyColumn(Modifier.heightIn(max = 180.dp)) {
                items(services) { s ->
                    Column(Modifier.padding(vertical = 2.dp)) {
                        Text(s.uuid, fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall)
                        s.characteristics.forEach { c ->
                            Text("  └ ${c.uuid} [${c.properties.joinToString()}]",
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Text("Write / fuzz (your own device)",
                style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(writeSvc, { writeSvc = it },
                label = { Text("service UUID") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(writeChar, { writeChar = it },
                label = { Text("characteristic UUID") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(writeVal, { writeVal = it },
                label = { Text("value (hex)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { ble.write(writeSvc.trim(), writeChar.trim(), writeVal.trim()) }) {
                    Text("Write")
                }
                OutlinedButton(onClick = { ble.fuzz(writeSvc.trim(), writeChar.trim()) }) {
                    Text("Fuzz")
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("Log", style = MaterialTheme.typography.titleSmall)
        LazyColumn(Modifier.heightIn(max = 120.dp)) {
            items(log) { line ->
                Text(line, style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace)
            }
        }
    }
}
