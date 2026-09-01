package com.lioos.app.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.lioos.app.nfc.LioHceService
import com.lioos.app.nfc.SavedTag

@Composable
fun NfcScreen(
    modifier: Modifier = Modifier,
    nfcAvailable: Boolean,
    lastTag: SavedTag?,
    saved: List<SavedTag>,
    onSave: (SavedTag) -> Unit,
    onDelete: (SavedTag) -> Unit,
    context: Context
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        if (!nfcAvailable) {
            Text("This device has no NFC hardware.", color = MaterialTheme.colorScheme.error)
            return
        }

        Text("Read", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        if (lastTag == null) {
            Text("Hold a tag to the back of the phone…")
        } else {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(12.dp).verticalScroll(rememberScrollState()).heightIn(max = 220.dp)
                ) {
                    Text("UID: ${lastTag.uidHex}", fontFamily = FontFamily.Monospace)
                    Text("Tech: ${lastTag.techList.joinToString()}")
                    lastTag.atqaHex?.let { Text("ATQA: $it  SAK: ${lastTag.sakHex}") }
                    lastTag.ndefText?.let { Text("NDEF: $it") }
                    if (lastTag.blocksHex.isNotEmpty()) {
                        Text("Blocks (${lastTag.blocksHex.size}):")
                        lastTag.blocksHex.forEach {
                            Text(it, fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                onSave(lastTag)
                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
            }) { Text("Save this tag") }
        }

        Spacer(Modifier.height(16.dp))
        Text("Saved (${saved.size})", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))

        LazyColumn(Modifier.weight(1f)) {
            items(saved) { tag ->
                ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(tag.uidHex, fontFamily = FontFamily.Monospace)
                        Text(tag.techList.joinToString(),
                            style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton(onClick = {
                                // Choose payload to serve over HCE: NDEF bytes,
                                // first block, or the UID as a fallback.
                                val payload = tag.blocksHex.firstOrNull()
                                    ?: tag.uidHex
                                LioHceService.setEmulatedPayload(context, payload)
                                Toast.makeText(
                                    context,
                                    "Emulating via HCE. Set lioOS as default NFC-payment/other app in system settings.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }) { Text("Emulate") }
                            TextButton(onClick = { onDelete(tag) }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }
}
