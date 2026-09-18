# ESP32 Switch Bridge 🎮

An open-source ESP32 firmware that emulates a Nintendo Switch Pro Controller over
Bluetooth Classic and forwards input received over Wi-Fi/UDP. Together with the
companion Android app in [`android-app/`](android-app/), it lets you play on a
Switch with a DualShock 4 (or any gamepad Android recognizes).

> **Origin and credit:** This is dierez00's adaptation of
> [ghostside-net/ESP32-Switch-Controller-Joycon](https://github.com/ghostside-net/ESP32-Switch-Controller-Joycon).
> The original ESP32 controller foundation is credited to its creator; the Android/UDP
> bridge is the adaptation maintained here. See [CREDITS.md](CREDITS.md) for provenance
> and [the publication guide](docs/PUBLISHING.md) before sharing a copy.

```text
DualShock 4 -> Android app -> Wi-Fi/UDP -> ESP32 -> Bluetooth Classic -> Switch
```

> [!NOTE]
> Earlier versions of this firmware ran a hardcoded macro (Pokémon Legends: Z-A
> shiny hunting) triggered from GPIO4. That mode has been replaced by the UDP
> bridge. It is still available in the git history before this change.

---

## 🚀 Features

- **Native Emulation:** Presents itself to the Switch as a Pro Controller
  (HID descriptor, handshake and SPI flash responses handled by `SwitchController`).
- **Wi-Fi Bridge:** The ESP32 starts its own access point and receives compact,
  CRC-protected 16-byte input packets over UDP.
- **Fail-safe Input:** Duplicate or out-of-order packets are dropped, and the
  controller goes neutral if the input stream stops for 150 ms.
- **Link Maintenance:** Deferred Bluetooth event logging, per-second diagnostics
  over serial and automatic reconnection to the known Switch when the HID link drops.
- **Android Companion App:** Reads the gamepad through standard Android APIs
  (no root), maps it to the Switch layout and streams it to the ESP32.

---

## 🛠️ Hardware Requirements

- **Target Board:** Classic ESP32 DevKit / ESP-WROOM-32 with 4 MB flash and
  Bluetooth Classic. ESP32-S2/S3/C3 are **not** supported by this firmware.
  Hardware pairing, controller mappings and latency must be verified on your devices.
- **Console:** Nintendo Switch / Nintendo Switch Lite / Nintendo Switch OLED.
- **Phone:** Android 10 or later (`minSdk 29`) with a paired DualShock 4.

---

## 💻 Firmware Setup & Installation

The project is built with **PlatformIO** (VS Code extension or CLI). All required
configuration is bundled in the repository.

### VS Code + PlatformIO (recommended)

1. Install VS Code and the **PlatformIO IDE** extension, then restart VS Code.
2. Clone or download this repository. In VS Code choose **File > Open Folder**
   and select the repository root containing `platformio.ini`. Do **not** create
   a New Project or open `android-app/` as the firmware project.
3. Wait for PlatformIO to finish loading the project and its dependencies.
   In **PlatformIO > Project Tasks > esp32dev > General**, select **Build**.
4. Connect the ESP32 with a USB **data** cable. Select **Upload**, then **Monitor**
   (115200 baud). Uploading replaces the firmware already on the board.
5. Confirm the monitor reports the access point, then follow the pairing steps below.

### PlatformIO CLI

Use the VS Code PlatformIO terminal, or a shell with `pio` installed. Run these
commands from the repository root; replace the serial-port placeholder:


```bash
pio run -e esp32dev
pio run -e esp32dev -t upload --upload-port <your-serial-port>
pio device monitor --port <your-serial-port> --baud 115200
```

To list serial ports, run `pio device list`. If automatic selection chooses the
wrong board, supply `--upload-port` as above and select the same port for monitoring.

`platformio.ini` uses Arduino **and** ESP-IDF, the `esp32dev` environment, 4 MB
flash and `huge_app.csv` partitions. Keep `sdkconfig.defaults` and
`sdkconfig.esp32dev`; this is not a generic Arduino-only project. The
`espressif32` platform is currently unpinned, so a fresh dependency resolution
may differ from an existing setup. Record your PlatformIO/platform versions
when reporting a reproducible build instead of assuming all versions work.

### Upload and monitor troubleshooting

- **No serial device:** Try a known data cable and another USB port; check the
  USB-to-serial driver for your board and serial-device permissions on your OS.
- **Upload stuck connecting:** Hold **BOOT** while upload starts, release after
  connection, and retry. Close other programs using the serial port.
- **Unreadable/no logs:** Confirm the correct port and **115200** baud; reset the board.
- **Build fails:** Preserve the error and tool versions. Do not replace the
  bundled ESP-IDF configuration with a New Project template to hide the failure.

Optional build flag: `-D SWITCH_COEX_PREFER_BT=1` makes the Wi-Fi/Bluetooth
coexistence scheduler prefer Bluetooth.

## 📱 Android App

See [`android-app/README.md`](android-app/README.md) for build instructions and usage.
In short:

```bash
cd android-app
./gradlew assembleDebug
```

---

## 🎮 How to Connect & Use

1. **Power the ESP32** and check the serial monitor shows the SoftAP started.
   It creates this network:
   - SSID: `SwitchBridge`
   - Password: `switchbridge`
   - ESP32 IP: `192.168.4.1`
   - UDP port: `4210`
2. **Pair with the Switch:** Open **Controllers > Change Grip/Order** and wait
   for `Pro Controller` to appear. The monitor reports the Bluetooth connection
   and then `Handshake complete; report mode 0x30 active`.
3. **Connect the phone:** Pair the DualShock 4 in Android's Bluetooth settings,
   join the `SwitchBridge` Wi-Fi network (accept staying connected without
   Internet) and start the bridge in the app.
4. **Play:** Buttons and sticks should respond on the Switch's controller test
   screen. The per-second diagnostics should show `BT=1` and `handshake=1` with
   no CRC errors or losses under normal conditions.

If you stop sending while holding a button, within ~150 ms the monitor prints
`UDP stream lost; neutral state applied` and the controller returns to neutral.
Resuming prints `UDP stream recovered`.

Before the handshake completes, the firmware keeps sending a periodic neutral
report to start negotiation. Afterwards it sends Switch reports every 15 ms.

---

## 📡 UDP Protocol

Both packet types use CRC-8/ATM (polynomial `0x07`, init `0x00`, no reflection,
xorout `0x00`).

### Input packet (Android → ESP32, 16 bytes)

| Bytes | Content |
| --- | --- |
| 0–2 | Header `53 42 01` |
| 3 | Flags: bit 0 = gamepad connected |
| 4 | Sequence number (`uint8`, wraps `255 -> 0`) |
| 5–7 | Button bytes B1, B2, B3 (already in Switch layout) |
| 8–11 | Sticks LX, LY, RX, RY |
| 12–13 | Analog triggers L2, R2 (diagnostics only) |
| 14 | Reserved |
| 15 | CRC-8/ATM over bytes 0–14 |

| Byte | Bits |
| --- | --- |
| B1 | `01 Y`, `02 X`, `04 B`, `08 A`, `40 R`, `80 ZR` |
| B2 | `01 Minus`, `02 Plus`, `04 RStick`, `08 LStick`, `10 Home`, `20 Capture` |
| B3 | `01 Down`, `02 Up`, `04 Right`, `08 Left`, `40 L`, `80 ZL` |

ZL and ZR are taken from B3 and B1; L2 and R2 are only shown in serial diagnostics.

### ACK packet (ESP32 → sender, 8 bytes)

| Bytes | Content |
| --- | --- |
| 0–2 | Header `53 41 01` |
| 3 | Status: bit 0 = Switch BT connected, bit 1 = handshake complete, bit 2 = recent UDP input |
| 4 | Last accepted sequence |
| 5 | Age of last packet in ms (capped at 255) |
| 6 | Reserved (`00`) |
| 7 | CRC-8/ATM over bytes 0–6 |

Repeated or older sequences are discarded. After a 150 ms gap the next valid
packet starts a new sequence.

---

## ⚠️ Current Limitations

- **Handshake Instability:** When entering *Change Grip/Order*, the ESP32 may
  connect and disconnect a few times before the link becomes stable.
- **Fixed Network Settings:** SSID, password, IP and port are compile-time
  constants in `src/main.cpp`.
- **Latency:** Input goes through Wi-Fi and Bluetooth on the same radio; expect
  some added latency compared to a native controller.

---

## 📄 License

This adaptation retains the upstream GNU GPL v3.0 license. See [LICENSE](LICENSE)
and [CREDITS.md](CREDITS.md). Redistributing the firmware or APK requires
complying with the license, including providing the corresponding source.
This project is not affiliated with Nintendo or Sony.
