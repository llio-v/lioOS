package com.lioos.app.nfc

import android.content.Context
import android.nfc.cardemulation.HostApduService
import android.os.Bundle

/**
 * Host Card Emulation service. When lioOS is the active HCE app and a reader
 * selects our AID, we answer APDUs from the currently "emulated" saved tag.
 *
 * Note: HCE can only emulate ISO-DEP / APDU-based cards (like the in-app
 * reader test AID). It cannot clone the UID of a MIFARE Classic — no phone
 * can, the UID is fixed in the secure element. So emulation here means
 * "replay the stored NDEF / APDU responses", which is the honest limit of
 * software emulation on Android.
 */
class LioHceService : HostApduService() {

    companion object {
        private val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
        private val SW_UNKNOWN = byteArrayOf(0x6A.toByte(), 0x82.toByte())

        // SELECT AID command header (CLA INS P1 P2)
        private val SELECT_HEADER = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00)

        const val PREFS = "hce_prefs"
        const val KEY_PAYLOAD = "emulated_payload_hex"

        /** Called from the UI to choose which saved tag is emulated. */
        fun setEmulatedPayload(ctx: Context, payloadHex: String) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_PAYLOAD, payloadHex).apply()
        }
    }

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null) return SW_UNKNOWN

        // Respond OK to the SELECT AID, then serve the stored payload on read.
        if (isSelectApdu(commandApdu)) {
            return SW_OK
        }

        val payloadHex = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PAYLOAD, null)
        return if (payloadHex.isNullOrBlank()) {
            SW_OK
        } else {
            Hex.decode(payloadHex) + SW_OK
        }
    }

    override fun onDeactivated(reason: Int) { /* field lost / new AID selected */ }

    private fun isSelectApdu(apdu: ByteArray): Boolean {
        if (apdu.size < SELECT_HEADER.size) return false
        for (i in SELECT_HEADER.indices) {
            if (apdu[i] != SELECT_HEADER[i]) return false
        }
        return true
    }
}
