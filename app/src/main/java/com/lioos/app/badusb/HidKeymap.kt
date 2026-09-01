package com.lioos.app.badusb

/** USB HID Usage IDs (Keyboard/Keypad page 0x07) for a US layout. */
object HidKeymap {
    // Modifier bitmask (byte 0 of the report)
    const val MOD_LCTRL = 0x01
    const val MOD_LSHIFT = 0x02
    const val MOD_LALT = 0x04
    const val MOD_LGUI = 0x08   // Windows / Command
    const val MOD_RCTRL = 0x10
    const val MOD_RSHIFT = 0x20
    const val MOD_RALT = 0x40

    /** Named keys used by Ducky Script. */
    val named: Map<String, Int> = mapOf(
        "ENTER" to 0x28, "RETURN" to 0x28,
        "ESC" to 0x29, "ESCAPE" to 0x29,
        "BACKSPACE" to 0x2A,
        "TAB" to 0x2B,
        "SPACE" to 0x2C,
        "CAPSLOCK" to 0x39,
        "DELETE" to 0x4C, "DEL" to 0x4C,
        "INSERT" to 0x49,
        "HOME" to 0x4A, "END" to 0x4D,
        "PAGEUP" to 0x4B, "PAGEDOWN" to 0x4E,
        "UP" to 0x52, "UPARROW" to 0x52,
        "DOWN" to 0x51, "DOWNARROW" to 0x51,
        "LEFT" to 0x50, "LEFTARROW" to 0x50,
        "RIGHT" to 0x4F, "RIGHTARROW" to 0x4F,
        "F1" to 0x3A, "F2" to 0x3B, "F3" to 0x3C, "F4" to 0x3D,
        "F5" to 0x3E, "F6" to 0x3F, "F7" to 0x40, "F8" to 0x41,
        "F9" to 0x42, "F10" to 0x43, "F11" to 0x44, "F12" to 0x45,
        "PRINTSCREEN" to 0x46, "SCROLLLOCK" to 0x47, "PAUSE" to 0x48,
        "MENU" to 0x65, "APP" to 0x65
    )

    /** Modifier aliases usable as combo prefixes (GUI r, CTRL ALT DEL). */
    val modifierAlias: Map<String, Int> = mapOf(
        "CTRL" to MOD_LCTRL, "CONTROL" to MOD_LCTRL,
        "SHIFT" to MOD_LSHIFT,
        "ALT" to MOD_LALT,
        "GUI" to MOD_LGUI, "WINDOWS" to MOD_LGUI, "WIN" to MOD_LGUI, "COMMAND" to MOD_LGUI
    )

    /** Map a single printable char to (usageId, needsShift). */
    fun charToUsage(c: Char): Pair<Int, Boolean>? {
        return when (c) {
            in 'a'..'z' -> (0x04 + (c - 'a')) to false
            in 'A'..'Z' -> (0x04 + (c - 'A')) to true
            in '1'..'9' -> (0x1E + (c - '1')) to false
            '0' -> 0x27 to false
            ' ' -> 0x2C to false
            '-' -> 0x2D to false
            '_' -> 0x2D to true
            '=' -> 0x2E to false
            '+' -> 0x2E to true
            '[' -> 0x2F to false
            '{' -> 0x2F to true
            ']' -> 0x30 to false
            '}' -> 0x30 to true
            '\\' -> 0x31 to false
            '|' -> 0x31 to true
            ';' -> 0x33 to false
            ':' -> 0x33 to true
            '\'' -> 0x34 to false
            '"' -> 0x34 to true
            '`' -> 0x35 to false
            '~' -> 0x35 to true
            ',' -> 0x36 to false
            '<' -> 0x36 to true
            '.' -> 0x37 to false
            '>' -> 0x37 to true
            '/' -> 0x38 to false
            '?' -> 0x38 to true
            '!' -> 0x1E to true
            '@' -> 0x1F to true
            '#' -> 0x20 to true
            '$' -> 0x21 to true
            '%' -> 0x22 to true
            '^' -> 0x23 to true
            '&' -> 0x24 to true
            '*' -> 0x25 to true
            '(' -> 0x26 to true
            ')' -> 0x27 to true
            else -> null
        }
    }
}
