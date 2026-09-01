package com.lioos.app.badusb

/** One emitted step: either an 8-byte HID report to send, or a delay. */
sealed class DuckyStep {
    data class Report(val bytes: ByteArray) : DuckyStep()
    data class Delay(val ms: Long) : DuckyStep()
}

/**
 * Parses a subset of Rubber Ducky / USB Rubber Ducky script and compiles it
 * to a flat list of HID keyboard reports + delays. Each key press expands to
 * a "key down" report and a "release all" report (0x00 x8).
 *
 * Supported: REM, STRING, DELAY, ENTER/GUI/CTRL/ALT/SHIFT combos, named keys,
 * REPEAT, DEFAULTDELAY/DEFAULT_DELAY.
 */
class DuckyScript {

    private val release = ByteArray(8) // all zeros = keys up

    fun compile(script: String): List<DuckyStep> {
        val steps = mutableListOf<DuckyStep>()
        var defaultDelay = 0L
        var lastLine: String? = null

        val lines = script.replace("\r\n", "\n").split("\n")
        for (raw in lines) {
            val line = raw.trimEnd()
            if (line.isBlank()) continue
            val upper = line.trim().uppercase()

            when {
                upper.startsWith("REM") -> { /* comment */ }

                upper.startsWith("DEFAULTDELAY") || upper.startsWith("DEFAULT_DELAY") -> {
                    defaultDelay = line.trim().substringAfter(' ', "0").trim().toLongOrNull() ?: 0L
                }

                upper.startsWith("DELAY") -> {
                    val ms = line.trim().substringAfter(' ', "0").trim().toLongOrNull() ?: 0L
                    steps.add(DuckyStep.Delay(ms))
                }

                upper.startsWith("STRING") -> {
                    val text = line.trim().substring("STRING".length).trimStart()
                    typeString(text, steps)
                    lastLine = line
                }

                upper.startsWith("REPEAT") -> {
                    val n = line.trim().substringAfter(' ', "1").trim().toIntOrNull() ?: 1
                    val prev = lastLine
                    if (prev != null) {
                        repeat(n) { emitLine(prev, steps) }
                    }
                }

                else -> {
                    emitLine(line.trim(), steps)
                    lastLine = line
                }
            }

            if (defaultDelay > 0) steps.add(DuckyStep.Delay(defaultDelay))
        }
        return steps
    }

    private fun emitLine(line: String, steps: MutableList<DuckyStep>) {
        val tokens = line.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return

        var modifiers = 0
        var usage = 0

        for (tok in tokens) {
            val up = tok.uppercase()
            when {
                HidKeymap.modifierAlias.containsKey(up) ->
                    modifiers = modifiers or HidKeymap.modifierAlias[up]!!
                HidKeymap.named.containsKey(up) ->
                    usage = HidKeymap.named[up]!!
                tok.length == 1 -> {
                    val m = HidKeymap.charToUsage(tok[0])
                    if (m != null) {
                        usage = m.first
                        if (m.second) modifiers = modifiers or HidKeymap.MOD_LSHIFT
                    }
                }
            }
        }
        pressReport(modifiers, usage, steps)
    }

    private fun typeString(text: String, steps: MutableList<DuckyStep>) {
        for (c in text) {
            val m = HidKeymap.charToUsage(c) ?: continue
            val mod = if (m.second) HidKeymap.MOD_LSHIFT else 0
            pressReport(mod, m.first, steps)
        }
    }

    private fun pressReport(modifiers: Int, usage: Int, steps: MutableList<DuckyStep>) {
        val down = ByteArray(8)
        down[0] = modifiers.toByte()
        down[2] = usage.toByte()
        steps.add(DuckyStep.Report(down))
        steps.add(DuckyStep.Report(release.copyOf()))
    }

    companion object {
        val SAMPLE = """
            REM lioOS BadUSB demo — opens Notepad and types (Windows target)
            DELAY 500
            GUI r
            DELAY 300
            STRING notepad
            ENTER
            DELAY 600
            STRING Hello from lioOS BadUSB
            ENTER
        """.trimIndent()
    }
}
