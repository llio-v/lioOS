package com.lioos.app.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.lioos.app.kiosk.KioskManager

@Composable
fun SystemScreen(modifier: Modifier = Modifier, kiosk: KioskManager, activity: Activity) {
    val ctx = LocalContext.current
    var status by remember { mutableStateOf(kiosk.status()) }
    var enabled by remember { mutableStateOf(kiosk.isKioskEnabled()) }

    Column(
        modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())
    ) {
        Text("Kiosk / OS mode", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Locks the phone into lioOS — Home, Recents and the status bar are " +
                "blocked, and the device boots straight into the app. Full lockdown " +
                "needs device-owner (one ADB command below). Without it you still get " +
                "basic screen-pinning that the user can exit.",
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(12.dp))
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Status", style = MaterialTheme.typography.titleSmall)
                Text(status, fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Kiosk enabled")
            Switch(checked = enabled, onCheckedChange = { on ->
                enabled = on
                kiosk.setKioskEnabled(on)
                if (on) kiosk.enterLockTask(activity) else kiosk.exitLockTask(activity)
                status = kiosk.status()
            })
        }

        Spacer(Modifier.height(8.dp))
        if (!kiosk.isDeviceOwner()) {
            Text("Enable full lockdown (run once on a PC)",
                style = MaterialTheme.typography.titleSmall)
            Text(
                "The phone must have NO Google account added yet (factory-fresh or " +
                    "after a reset). Connect USB, enable USB debugging, then run:",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(6.dp))
            val cmd = kiosk.deviceOwnerCommand()
            ElevatedCard(Modifier.fillMaxWidth()) {
                Text(
                    cmd,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = { copy(ctx, cmd) }) { Text("Copy command") }
            Spacer(Modifier.height(6.dp))
            Text(
                "To undo device-owner later: adb shell dpm remove-active-admin " +
                    "com.lioos.app/com.lioos.app.kiosk.LioDeviceAdminReceiver",
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Text("Device-owner active — full kiosk lockdown available.",
                color = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                kiosk.clearOwnerPolicy()
                kiosk.setKioskEnabled(false)
                kiosk.exitLockTask(activity)
                enabled = false
                status = kiosk.status()
                Toast.makeText(ctx, "Kiosk policy cleared", Toast.LENGTH_SHORT).show()
            }) { Text("Clear kiosk policy") }
        }
    }
}

private fun copy(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("lioOS", text))
    Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
}
