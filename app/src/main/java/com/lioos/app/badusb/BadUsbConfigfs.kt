package com.lioos.app.badusb

/**
 * BadUSB via the Linux USB gadget subsystem (configfs) — the ROOTED path.
 *
 * Requires: root, and a kernel built with CONFIG_USB_CONFIGFS_F_HID and the
 * phone's USB controller able to run in peripheral/device mode. When that
 * holds, we register a standard HID keyboard gadget and stream 8-byte reports
 * to /dev/hidg0, so the host PC sees a real keyboard.
 *
 * This is the honest constraint: without a compatible kernel this path is
 * unavailable, and the app falls back to the external-board path.
 */
object BadUsbConfigfs {

    private const val GADGET = "/config/usb_gadget/lioos"
    private const val HIDG = "/dev/hidg0"

    // Standard boot-keyboard HID report descriptor (8-byte reports).
    private const val REPORT_DESC_HEX =
        "05010906a101050719e029e71500250175019508810295017508810195057501050" +
        "8190129059102950175039101950675081500256505071900296581000c0"

    fun probe(): Boolean {
        if (!RootShell.isAvailable()) return false
        val r = RootShell.run("ls -d /config/usb_gadget 2>/dev/null || " +
                "ls -d /sys/kernel/config/usb_gadget 2>/dev/null")
        return r.contains("usb_gadget")
    }

    /** Creates and binds the HID keyboard gadget. Returns shell output. */
    fun setup(): String {
        val udcCmd = "ls /sys/class/udc | head -n1"
        val desc = REPORT_DESC_HEX
        val script = """
            set -e
            CFG=/config/usb_gadget
            [ -d ${'$'}CFG ] || CFG=/sys/kernel/config/usb_gadget
            G=${'$'}CFG/lioos
            mkdir -p ${'$'}G
            echo 0x1d6b > ${'$'}G/idVendor
            echo 0x0104 > ${'$'}G/idProduct
            echo 0x0100 > ${'$'}G/bcdDevice
            echo 0x0200 > ${'$'}G/bcdUSB
            mkdir -p ${'$'}G/strings/0x409
            echo "lioos0001" > ${'$'}G/strings/0x409/serialnumber
            echo "lioOS" > ${'$'}G/strings/0x409/manufacturer
            echo "lioOS BadUSB Keyboard" > ${'$'}G/strings/0x409/product
            mkdir -p ${'$'}G/functions/hid.usb0
            echo 1 > ${'$'}G/functions/hid.usb0/protocol
            echo 1 > ${'$'}G/functions/hid.usb0/subclass
            echo 8 > ${'$'}G/functions/hid.usb0/report_length
            echo -ne "$(printf '$desc' | sed 's/../\\\\x&/g')" > ${'$'}G/functions/hid.usb0/report_desc
            mkdir -p ${'$'}G/configs/c.1/strings/0x409
            echo "lioOS HID" > ${'$'}G/configs/c.1/strings/0x409/configuration
            ln -sf ${'$'}G/functions/hid.usb0 ${'$'}G/configs/c.1/ || true
            UDC=$( $udcCmd )
            echo ${'$'}UDC > ${'$'}G/UDC
            echo "gadget bound to ${'$'}UDC"
        """.trimIndent()
        return RootShell.run(script)
    }

    fun teardown(): String {
        val script = """
            CFG=/config/usb_gadget
            [ -d ${'$'}CFG ] || CFG=/sys/kernel/config/usb_gadget
            G=${'$'}CFG/lioos
            echo "" > ${'$'}G/UDC 2>/dev/null || true
            rm -f ${'$'}G/configs/c.1/hid.usb0 2>/dev/null || true
            rmdir ${'$'}G/configs/c.1/strings/0x409 2>/dev/null || true
            rmdir ${'$'}G/configs/c.1 2>/dev/null || true
            rmdir ${'$'}G/functions/hid.usb0 2>/dev/null || true
            rmdir ${'$'}G/strings/0x409 2>/dev/null || true
            rmdir ${'$'}G 2>/dev/null || true
            echo "gadget removed"
        """.trimIndent()
        return RootShell.run(script)
    }

    /** Streams compiled Ducky steps to /dev/hidg0 as root. */
    fun run(steps: List<DuckyStep>, onLog: (String) -> Unit) {
        val sb = StringBuilder()
        for (step in steps) {
            when (step) {
                is DuckyStep.Delay -> {
                    val sec = step.ms / 1000.0
                    sb.append("sleep ").append(sec).append("\n")
                }
                is DuckyStep.Report -> {
                    // printf octal escapes -> raw bytes into the hid device
                    val octal = step.bytes.joinToString("") {
                        "\\" + String.format("%03o", it.toInt() and 0xFF)
                    }
                    sb.append("printf '").append(octal).append("' > ").append(HIDG).append("\n")
                }
            }
        }
        val out = RootShell.run(sb.toString())
        onLog("payload sent (${steps.size} steps). ${out.take(120)}")
    }
}
