package com.lioos.app

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.lioos.app.badkb.BadKbManager
import com.lioos.app.ble.BleManager
import com.lioos.app.kiosk.KioskManager
import com.lioos.app.nfc.NfcReader
import com.lioos.app.nfc.NfcStore
import com.lioos.app.nfc.SavedTag
import com.lioos.app.ui.BadKbScreen
import com.lioos.app.ui.BadUsbScreen
import com.lioos.app.ui.BleScreen
import com.lioos.app.ui.NfcScreen
import com.lioos.app.ui.SystemScreen

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var store: NfcStore
    private lateinit var bleManager: BleManager
    private lateinit var badKb: BadKbManager
    private lateinit var kiosk: KioskManager

    // last scanned tag, observed by the NFC screen
    private val lastTag = mutableStateOf<SavedTag?>(null)
    private val savedTags = mutableStateOf<List<SavedTag>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        store = NfcStore(this)
        bleManager = BleManager(this)
        badKb = BadKbManager(this)
        kiosk = KioskManager(this)
        savedTags.value = store.load()

        setContent { LioApp() }
    }

    override fun onResume() {
        super.onResume()
        // Reader mode: intercept tags without the OS dispatch dialog.
        nfcAdapter?.enableReaderMode(
            this, this,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null
        )
        // If the user turned kiosk on, re-pin every time we come foreground.
        if (kiosk.isKioskEnabled()) kiosk.enterLockTask(this)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    override fun onTagDiscovered(tag: Tag) {
        val scanned = NfcReader.read(tag)
        runOnUiThread { lastTag.value = scanned }
    }

    private fun saveTag(tag: SavedTag) {
        savedTags.value = store.add(tag)
    }

    private fun deleteTag(tag: SavedTag) {
        savedTags.value = store.remove(tag)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun LioApp() {
        var tab by remember { mutableStateOf(0) }
        val tabs = listOf(
            Triple("NFC", Icons.Filled.Memory, 0),
            Triple("BLE", Icons.Filled.Bluetooth, 1),
            Triple("BadUSB", Icons.Filled.Usb, 2),
            Triple("BadKB", Icons.Filled.Keyboard, 3),
            Triple("System", Icons.Filled.Lock, 4)
        )
        MaterialTheme(colorScheme = darkColorScheme()) {
            Scaffold(
                topBar = { TopAppBar(title = { Text("lioOS") }) },
                bottomBar = {
                    NavigationBar {
                        tabs.forEach { (label, icon: ImageVector, idx) ->
                            NavigationBarItem(
                                selected = tab == idx,
                                onClick = { tab = idx },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            ) { pad ->
                val mod = Modifier.padding(pad)
                when (tab) {
                    0 -> NfcScreen(
                        modifier = mod,
                        nfcAvailable = nfcAdapter != null,
                        lastTag = lastTag.value,
                        saved = savedTags.value,
                        onSave = { saveTag(it) },
                        onDelete = { deleteTag(it) },
                        context = this
                    )
                    1 -> BleScreen(modifier = mod, ble = bleManager, activity = this)
                    2 -> BadUsbScreen(modifier = mod, context = this)
                    3 -> BadKbScreen(modifier = mod, kb = badKb, activity = this)
                    4 -> SystemScreen(modifier = mod, kiosk = kiosk, activity = this)
                }
            }
        }
    }
}
