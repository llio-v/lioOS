package com.lioos.app.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lioos.app.badkb.BadKbManager
import com.lioos.app.badusb.DuckyScript
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BadKbScreen(modifier: Modifier = Modifier, kb: BadKbManager, activity: Activity) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val log by kb.log.collectAsStateWithLifecycle()
    val state by kb.state.collectAsStateWithLifecycle()
    val registered by kb.registered.collectAsStateWithLifecycle()

    var script by remember { mutableStateOf(DuckyScript.SAMPLE) }
    var targets by remember { mutableStateOf(kb.bondedTargets()) }

    fun ensureConnectPerm(): Boolean {
        if (Build.VERSION.SDK_INT >= 31 &&
            ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                activity, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 43
            )
            return false
        }
        return true
    }

    Column(modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("BadKB — Bluetooth keyboard", style = MaterialTheme.typography.titleMedium)
        Text(
            "The phone becomes a Bluetooth HID keyboard and types the script on a " +
                "paired host. No root, no extra hardware. Authorized targets only.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary
        )

        if (!kb.supported()) {
            Spacer(Modifier.height(8.dp))
            Text("Requires Android 9+ and Bluetooth.",
                color = MaterialTheme.colorScheme.error)
            return
        }

        Spacer(Modifier.height(8.dp))
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("State: $state", fontFamily = FontFamily.Monospace)
                Text("HID registered: $registered", fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(Modifier.height(8.dp))
        // Step 1: register the keyboard
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { if (ensureConnectPerm()) kb.register() }) {
                Text("1 · Register keyboard")
            }
            OutlinedButton(onClick = { kb.unregister() }) { Text("Unregister") }
        }
        Text(
            "After registering, open the target's Bluetooth settings and pair with " +
                "\"lioOS BadKB\".",
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(8.dp))
        // Step 2: pick a paired target + connect
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("2 · Paired targets", style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = { targets = kb.bondedTargets() }) { Text("Refresh") }
        }
        if (targets.isEmpty()) {
            Text("No paired devices yet — pair the target with lioOS BadKB first.",
                style = MaterialTheme.typography.bodySmall)
        } else {
            targets.forEach { d ->
                ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Text(d.name ?: "(no name)")
                        Text(d.address, fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = {
                            if (ensureConnectPerm()) kb.connect(d)
                        }) { Text("Connect") }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        // Step 3: script + send
        Text("3 · Payload (Ducky Script)", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = script, onValueChange = { script = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 240.dp)
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                scope.launch { withContext(Dispatchers.IO) { kb.sendScript(script) } }
            }) { Text("Send payload") }
            OutlinedButton(onClick = { kb.disconnect() }) { Text("Disconnect") }
        }

        Spacer(Modifier.height(8.dp))
        Text("Log", style = MaterialTheme.typography.titleSmall)
        LazyColumn(Modifier.heightIn(max = 160.dp)) {
            items(log) { line ->
                Text(line, style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace)
            }
        }
    }
}
