# lioOS

A Flipper-Zero-style multitool for Android. Single app, five tools:

- **NFC** — read tags, save dumps to disk, emulate saved payloads over HCE
- **BLE** — scan, connect, enumerate GATT, write/fuzz your own peripherals
- **BadUSB** — Ducky Script engine with two delivery paths (rooted HID gadget, or external board)
- **BadKB** — the phone acts as a **Bluetooth HID keyboard** and types a Ducky script on a paired host (no root, no extra hardware)
- **System** — kiosk / appliance mode via device-owner (Lock Task), turning the phone into a locked lioOS device

lioOS also registers as a **HOME launcher** and **auto-starts on boot**, so it can open by default on startup.

---

## Build & run

Open the folder in **Android Studio** (Koala or newer) and press Run. Android Studio
provides the Gradle wrapper JAR automatically. From CLI, first generate the wrapper:

```bash
gradle wrapper --gradle-version 8.9
./gradlew :app:assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

- **compileSdk / targetSdk:** 34   **minSdk:** 26 (Android 8.0)
- Kotlin 2.0.20, Jetpack Compose (Material 3), AGP 8.5.2

## Make it the default startup app

After install: **Settings → Apps → Default apps → Home app → lioOS**.
It then opens on boot and when you press Home. Auto-start is also handled by
`BootReceiver` (needs the app launched once so it isn't in "stopped" state).

---

## What actually works on a stock phone vs. what needs hardware

Being honest about the limits — an Android phone is not a Flipper:

| Tool | Stock phone | Notes |
|------|-------------|-------|
| NFC read (UID, NDEF, MIFARE dump w/ default keys) | ✅ | needs NFC hardware |
| NFC save to file | ✅ | JSON in app storage |
| NFC emulate | ⚠️ partial | HCE can replay **APDU/NDEF** payloads only. It **cannot** clone a MIFARE Classic UID — the UID lives in the tag's fixed silicon; no phone can spoof it. |
| BLE scan / GATT enumerate | ✅ | standard Android BLE |
| BLE write / fuzz | ✅ | for **your own** peripherals |
| BadUSB (root path) | ⚠️ device-specific | needs root **and** a kernel with `CONFIG_USB_CONFIGFS_F_HID` + peripheral-mode USB. Then it's a real USB keyboard. |
| BadUSB (external board) | ✅ | ESP32 / Digispark over USB-OTG; the board is the keyboard, the phone drives it over serial |
| BadKB (Bluetooth HID keyboard) | ✅ | Android 9+ `BluetoothHidDevice`; the phone types on a paired host. Target must accept pairing — not a covert attack. |
| Kiosk / device-owner | ✅ | needs one ADB `dpm set-device-owner` on an account-free device |

### BadUSB — external board firmware

The no-root path expects a board that (a) enumerates to the target PC as a USB
HID keyboard and (b) accepts Ducky Script over its USB-serial link. Flash an
ESP32 with a Ducky-compatible firmware (e.g. an ESP32-S2/S3 running a
`USBHIDKeyboard` sketch that reads lines from `Serial` and types them). lioOS
sends the raw script; the board replays keystrokes.

---

## Legal / scope

lioOS is for **authorized** testing and learning — your own devices, your own
machines, or systems you have written permission to test. Deliberately excluded:
BLE popup-spam/flooding, RF/Wi-Fi jamming, and cloning access cards you don't
own. Those harm third parties and aren't tooling this project ships.

---

## Layout

```
app/src/main/java/com/lioos/app/
  MainActivity.kt          NFC reader-mode host + Compose shell (3 tabs)
  BootReceiver.kt          auto-start on boot
  nfc/
    NfcReader.kt           Tag -> SavedTag (UID, NDEF, MIFARE dump)
    NfcStore.kt            JSON persistence of saved tags
    LioHceService.kt       HostApduService, serves emulated payload
    NfcModels.kt           SavedTag + Hex utils
  ble/
    BleManager.kt          scan, GATT connect/enumerate, write, fuzz
    BleModels.kt
  badusb/
    DuckyScript.kt         Ducky Script -> HID reports
    HidKeymap.kt           US HID usage tables
    BadUsbConfigfs.kt      rooted USB-gadget HID path
    BadUsbSerial.kt        external-board (USB-serial) path
    RootShell.kt           su helper
  badkb/
    BadKbManager.kt        Bluetooth HID keyboard (BluetoothHidDevice)
  kiosk/
    KioskManager.kt        device-owner + Lock Task appliance mode
    LioDeviceAdminReceiver.kt
  ui/
    NfcScreen.kt  BleScreen.kt  BadUsbScreen.kt  BadKbScreen.kt  SystemScreen.kt
```
