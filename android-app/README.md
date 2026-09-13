# Switch Bridge (Android app)

Switch Bridge turns input from a DualShock 4 connected to Android into compact reports for an ESP32. The app only uses Android's standard gamepad APIs and UDP: it needs no root, does not control Bluetooth and does not emulate an HID device.

## Requirements

- Android 10 or later (`minSdk 29`).
- DualShock 4 paired from Android's Bluetooth settings.
- ESP32 running the firmware from this repository (Wi-Fi access point + UDP receiver).

Defaults:

- SSID: `SwitchBridge`
- Password: `switchbridge`
- ESP32: `192.168.4.1`
- UDP port: `4210`

## Usage

1. Hold **Share + PS** on the DualShock 4 until the light bar flashes.
2. On Android open **Settings > Bluetooth**, select **Wireless Controller** and complete pairing.
3. Connect the phone to the `SwitchBridge` Wi-Fi network using the password `switchbridge`. Accept staying connected even though the network has no Internet access.
4. Open Switch Bridge. Grant precise location when Android asks; it is only used to check the Wi-Fi network name.
5. Confirm the IP and port, tap **Start bridge** and keep the app visible. The screen stays on while the bridge is active.
6. Check the DS4 → UDP → ESP32 → Switch status line and the live values. Raw keycodes and axes help diagnose mappings that differ between phone firmwares.

On launch, the app automatically requests the only runtime permissions it needs (coarse and precise location to read the SSID). The screen includes an expandable **Connect the DualShock 4** guide and a button that opens Bluetooth settings directly. Internet and network state permissions are normal permissions granted at install time; no Bluetooth permission is requested because input arrives through `InputDevice`.

### Background

While the bridge is active, a foreground service (persistent notification with a **Stop** button) keeps the UDP socket, Wi-Fi and gamepad reading alive even if you leave the app.

- Android only delivers gamepad events to the focused window. When you leave the app, a **floating bubble** appears to keep that focus; it requires the **Display over other apps** permission (button in the *Background* card). If you tap another app, focus moves to it and the bridge sends neutral until you tap the bubble again.
- Once the power button is pressed, Android stops delivering gamepad input to every app: the bridge keeps sending neutral so the link is not lost. To play with the screen "off", use **Screen off** (black at minimum brightness; double tap or Back to return).
- Without input focus, neutral is sent and held buttons are released. Stopping the bridge sends three neutral states. Android cannot guarantee that delivery if the process is force-killed.

## Building

The project uses JDK 17, Gradle 9.6.0, Android Gradle Plugin 9.4.0, SDK 37, Build Tools 36.0.0, Kotlin/Compose Compiler 2.2.10 and Compose BOM 2026.08.00.

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

The debug APK is generated at:

`app/build/outputs/apk/debug/app-debug.apk`

## Protocol

The app sends exactly 16 bytes every 15 ms. The ESP32 must reply to the source UDP port with an 8-byte ACK. Both formats use CRC-8/ATM (`poly=0x07`, `init=0x00`, no reflection, `xorout=0x00`). An ACK is considered stale after 1 second. See the [main README](../README.md#-udp-protocol) for the full packet layout.
