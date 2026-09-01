package com.lioos.app.badusb

import java.io.DataOutputStream

/** Thin wrapper around a root (`su`) shell. */
object RootShell {

    fun isAvailable(): Boolean = runCatching {
        val p = ProcessBuilder("which", "su").redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText().trim()
        p.waitFor()
        out.isNotEmpty()
    }.getOrDefault(false)

    /** Runs a block of shell script as root, returns combined output. */
    fun run(script: String): String {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "sh"))
            DataOutputStream(p.outputStream).use { os ->
                os.writeBytes(script)
                os.writeBytes("\nexit\n")
                os.flush()
            }
            val out = p.inputStream.bufferedReader().readText()
            val err = p.errorStream.bufferedReader().readText()
            p.waitFor()
            (out + err).trim()
        } catch (e: Exception) {
            "error: ${e.message}"
        }
    }
}
