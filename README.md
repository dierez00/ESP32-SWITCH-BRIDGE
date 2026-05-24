# ESP32 Switch Macro Controller 🎮

An open-source ESP32 firmware that allows an ESP32 development board to emulate a Nintendo Switch Pro Controller or Joy-Con via Bluetooth, enabling the execution of automated hardware-level macros.

---

## 🚀 Features

- **Native Emulation:** Simulates a Nintendo Switch Pro Controller or Joy-Con over Bluetooth (BLE).
- **Hardware-Level Macros:** Execute automated button sequences directly from the micro-controller, bypassing the need for external injection hardware.
- **Pre-configured Automation:** Comes out of the box with a working automation script for **Shiny Hunting in Pokémon Legends: Z-A (Wildzone 5)**.
- **Lightweight & Portable:** Built on top of robust ESP32 Bluetooth libraries, highly responsive once paired.

---

## 🛠️ Hardware Requirements

- **Tested Board:** ESP32 DevKit v1 (any standard ESP-WROOM-32 based board should work).
- **Console:** Nintendo Switch / Nintendo Switch Lite / Nintendo Switch OLED.

---

## 💻 Software Setup & Installation

The project is built using **PlatformIO** inside **Visual Studio Code**. All required libraries and configurations are bundled within the repository.

### Prerequisites
1. Download and install Visual Studio Code.
2. Install the PlatformIO IDE extension from the VS Code Marketplace.

### Building from Source
1. Klone dieses Repository auf deinen lokalen Computer (Nutze dafür den Git-Repository-Link aus dem grünen "Code"-Button auf GitHub).
2. Open the cloned folder in VS Code using PlatformIO.
3. Let PlatformIO automatically pull the required toolchains and dependencies.
4. Connect your ESP32 via USB.
5. Click the PlatformIO: Build button (checkmark icon) to compile.
6. Click the PlatformIO: Upload button (arrow icon) to flash the firmware onto your board.

> [!TIP]  
> *Pre-built firmware files (.bin) will be provided in the Releases section soon for easy flashing without a full development environment.*

---

## 🎮 How to Connect & Use

1. **Power the ESP32:** Connect your flashed ESP32 to a power source (USB ports on the Switch Dock, a power bank, or your PC).
2. **Open Switch Settings:** Navigate to the **Controllers** menu on your Nintendo Switch home screen, then select **Change Grip/Order**.
3. **Pairing:** The ESP32 will broadcast itself as a controller. Wait for the console to recognize it.
4. **Execution:** Once connected, the hardcoded macro will automatically initialize and execute its routine.

---

## ⚠️ Current Limitations & Flaws

- **Handshake Unstability:** The pairing handshake with the Switch is functional but not yet flawless. When entering the *Change Grip/Order* menu, the ESP32 may repeatedly connect and disconnect a few times before establishing a stable connection. Once paired, normal re-connections after restarting the ESP32 work seamlessly without issues.
- **Hardcoded Macros:** Macros are currently hardcoded into the source code. Changing the button sequence or timing requires editing the code, rebuilding, and re-flashing the firmware.

---

## 🔮 Upcoming Improvements

The following features and enhancements are planned for future releases:

- [ ] **Pairing Stability:** Refining the BLE handshake protocol to eliminate the connect/disconnect loop in the controller menu.
- [ ] **Dynamic Macro Configuration:** Implementing a Web Server interface over Wi-Fi (or a mobile app) to create, save, and change macros on the fly without re-flashing.
- [ ] **Customization:** Adding advanced settings to change the emulated controller's body and button colors as seen in the Switch UI.
- [ ] **Physical Hardware Integration:** Adding support for external components such as physical buttons to select/start macros and status LEDs for visual feedback.

---

## 📄 License

This project is licensed under the GNU GPL v3.0 License. See the LICENSE file for more details.
