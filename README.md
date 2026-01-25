# ESP32 as a working Nintendo Switch Controller/Joycon
Let an ESP32 connect as a Pro Controller or Joycon to a Nintendo Switch and run custom Macros.

Pre-built firmware file will be provided soon.

## Setup in VS Code
Project is been programmed with PlatformIO in VS Code.
Necessary files are all included, to build yourself.

> [!NOTE]
> Code has been tested on a ESP32 DevKit v1.

## Flaws and problems
Code is fully functional and working. But still the pairing handshake with the Switch is not yet perfect. When entering the controller menu on the Switch, the ESP repeatedly connects and disconnects. But pairing works anyways. Normal connection after restart works afterwards without any problems.

> [!NOTE]
> Macros are hardcoded for now, so changing macro requires rebuild and reupload of firmware.

## Upcoming improvements
Following improvements to implement are:
- Handshake/Pairing improvements
- Advanced settings for controller colors
- Advanced features for macros
  - Flexible macros to set by Wifi or other ways
- More external hardware support
  - e.g.: button functions, LEDs


> [!IMPORTANT]
> The implemented macro in this repository is a working macro for shiny hunting in Pokemon Legends ZA wildzone 5.

## License
Code is published under GPU v3.0 license.