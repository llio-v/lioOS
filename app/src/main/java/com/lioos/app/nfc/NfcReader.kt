package com.lioos.app.nfc

import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA

/** Turns a discovered [Tag] into a [SavedTag] snapshot. */
object NfcReader {

    fun read(tag: Tag): SavedTag {
        val uid = Hex.encode(tag.id)
        val techs = tag.techList.map { it.substringAfterLast('.') }

        var atqa: String? = null
        var sak: String? = null
        NfcA.get(tag)?.let { a ->
            atqa = Hex.encode(a.atqa)
            sak = String.format("%02X", a.sak.toInt() and 0xFF)
        }

        val ndefText = readNdef(tag)
        val blocks = dumpMifare(tag)

        return SavedTag(
            id = "Tag $uid",
            uidHex = uid,
            techList = techs,
            atqaHex = atqa,
            sakHex = sak,
            ndefText = ndefText,
            blocksHex = blocks
        )
    }

    private fun readNdef(tag: Tag): String? {
        val ndef = Ndef.get(tag) ?: return null
        return try {
            ndef.connect()
            val msg: NdefMessage = ndef.ndefMessage ?: return null
            msg.records.joinToString("\n") { rec ->
                val payload = rec.payload
                if (payload.isEmpty()) "" else {
                    // TNF well-known text record: first byte is status, then lang code
                    val langLen = payload[0].toInt() and 0x3F
                    String(payload, 1 + langLen, payload.size - 1 - langLen, Charsets.UTF_8)
                }
            }.ifBlank { null }
        } catch (e: Exception) {
            null
        } finally {
            try { ndef.close() } catch (_: Exception) {}
        }
    }

    /** Best-effort MIFARE dump using default keys (only your own cards). */
    private fun dumpMifare(tag: Tag): List<String> {
        // MIFARE Ultralight
        MifareUltralight.get(tag)?.let { ul ->
            return try {
                ul.connect()
                val pages = mutableListOf<String>()
                var page = 0
                while (page < 16) {
                    val data = ul.readPages(page) // returns 4 pages (16 bytes)
                    pages.add(Hex.encode(data))
                    page += 4
                }
                pages
            } catch (e: Exception) {
                emptyList()
            } finally {
                try { ul.close() } catch (_: Exception) {}
            }
        }

        // MIFARE Classic with default keys
        MifareClassic.get(tag)?.let { mc ->
            return try {
                mc.connect()
                val out = mutableListOf<String>()
                val defaultKey = MifareClassic.KEY_DEFAULT
                for (sector in 0 until mc.sectorCount) {
                    val auth = mc.authenticateSectorWithKeyA(sector, defaultKey)
                    if (!auth) continue
                    val first = mc.sectorToBlock(sector)
                    val count = mc.getBlockCountInSector(sector)
                    for (i in 0 until count) {
                        val block = mc.readBlock(first + i)
                        out.add(Hex.encode(block))
                    }
                }
                out
            } catch (e: Exception) {
                emptyList()
            } finally {
                try { mc.close() } catch (_: Exception) {}
            }
        }

        return emptyList()
    }
}
