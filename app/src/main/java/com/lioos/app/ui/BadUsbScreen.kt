package com.lioos.app.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.lioos.app.badusb.BadUsbConfigfs
import com.lioos.app.badusb.BadUsbSerial
import com.lioos.app.badusb.DuckyScript
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Mode { ROOT, SERIAL }

@Composable
fun BadUsbScreen(modifier: Modifier = Modifier, context: Context) {
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(Mode.SERIAL) }
    var script by remember { mutableStateOf(DuckyScript.SAMPLE) }
    var log by remember { mutableStateOf(listOf<String>()) }
    val serial = remember { BadUsbSerial(context) }

    fun addLog(s: String) { log = (listOf("• $s") + log).take(100) }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("BadUSB", style = MaterialTheme.typography.titleMedium)
        Text(
            "Authorized use only — run payloads against machines you own or are " +
                "permitted to test.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary
        )
        Spacer(Modifier.height(8.dp))

        // Mode selector
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == Mode.SERIAL, onClick = { mode = Mode.SERIAL },
                label = { Text("External board") })
            FilterChip(selected = mode == Mode.ROOT, onClick = { mode = Mode.ROOT },
                label = { Text("Root (configfs)") })
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = script, onValueChange = { script = it },
            label = { Text("Ducky Script") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 260.dp)
        )

        Spacer(Modifier.height(8.dp))
        when (mode) {
            Mode.SERIAL -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val devs = serial.listDevices()
                    addLog(if (devs.isEmpty()) "no USB-serial board found"
                    else devs.joinToString())
                }) { Text("Detect board") }
                Button(onClick = {
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { serial.connect() }
                        if (!ok) { addLog("connect failed — is the board plugged in via OTG?"); return@launch }
                        withContext(Dispatchers.IO) { serial.sendScript(script) { addLog(it) } }
                    }
                }) { Text("Send payload") }
            }

            Mode.ROOT -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { BadUsbConfigfs.probe() }
                            addLog(if (ok) "root + gadget configfs available"
                            else "unavailable: needs root & HID-capable kernel")
                        }
                    }) { Text("Probe") }
                    OutlinedButton(onClick = {
                        scope.launch {
                            val out = withContext(Dispatchers.IO) { BadUsbConfigfs.setup() }
                            addLog(out.take(160))
                        }
                    }) { Text("Setup gadget") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        scope.launch {
                            val steps = DuckyScript().compile(script)
                            withContext(Dispatchers.IO) {
                                BadUsbConfigfs.run(steps) { addLog(it) }
                            }
                        }
                    }) { Text("Run payload") }
                    OutlinedButton(onClick = {
                        scope.launch {
                            val out = withContext(Dispatchers.IO) { BadUsbConfigfs.teardown() }
                            addLog(out.take(160))
                        }
                    }) { Text("Remove gadget") }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("Log", style = MaterialTheme.typography.titleSmall)
        LazyColumn(Modifier.weight(1f)) {
            items(log) { line ->
                Text(line, style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace)
            }
        }
    }
}
