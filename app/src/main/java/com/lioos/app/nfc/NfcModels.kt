package com.lioos.app.nfc

/** A saved NFC scan. Bytes are stored hex-encoded so it survives JSON. */
data class SavedTag(
    val id: String,                    // human label
    val uidHex: String,                // tag UID
    val techList: List<String>,        // Android tech classes seen
    val atqaHex: String? = null,
    val sakHex: String? = null,
    val ndefText: String? = null,      // decoded NDEF text records, if any
    val blocksHex: List<String> = emptyList(), // raw MIFARE blocks, if dumped
    val savedAt: Long = System.currentTimeMillis()
)

object Hex {
    private val HEX = "0123456789ABCDEF".toCharArray()

    fun encode(bytes: ByteArray?): String {
        if (bytes == null) return ""
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    fun decode(s: String): ByteArray {
        val clean = s.replace(" ", "").replace(":", "")
        require(clean.length % 2 == 0) { "odd hex length" }
        val out = ByteArray(clean.length / 2)
        var i = 0
        while (i < clean.length) {
            out[i / 2] = ((Character.digit(clean[i], 16) shl 4) +
                    Character.digit(clean[i + 1], 16)).toByte()
            i += 2
        }
        return out
    }
}
