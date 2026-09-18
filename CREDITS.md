# Credits and provenance

ESP32 Switch Bridge is an adaptation, not an independently authored replacement
for the original controller implementation.

## Original project

- Creator/project owner: **ghostside-net**.
- Source: [ESP32-Switch-Controller-Joycon](https://github.com/ghostside-net/ESP32-Switch-Controller-Joycon).
- Contribution: the original ESP32 Bluetooth controller foundation and project setup.
- Retained local upstream baseline: `d7e062e`.

Original author identities and contributions remain in the Git history; existing
license and copyright notices must be preserved. This credit does not imply
that the original creator endorses or maintains this adaptation.

## Adaptation maintained by dierez00

The adaptation commit `e7f2af3` is dated **2026-09-13** in the retained history.
It replaces the hardcoded GPIO-triggered macro with a Wi-Fi/UDP input bridge,
adds the Android companion app, CRC/sequence validation and neutral-state
failsafe, and adds reconnection/serial diagnostics. The current firmware
presents a **Pro Controller**; the upstream repository name does not mean the
current adaptation implements a Joy-Con.

The maintainer reports permission to publish this adaptation with attribution.
That permission does not remove the obligations of the retained [GPL v3.0
license](LICENSE). Keep this file with redistributions and make the corresponding
source available when distributing binaries. Preserve dependency notices too;
for example, the Gradle wrapper retains its own Apache license notice.
