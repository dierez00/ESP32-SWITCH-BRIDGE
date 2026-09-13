#ifndef SWITCH_CONTROLLER_H
#define SWITCH_CONTROLLER_H

#include <Arduino.h>
#include "esp_bt.h"
#include "esp_bt_main.h"
#include "esp_bt_device.h"
#include "esp_gap_bt_api.h"
#include "esp_hidd_api.h"

// Buttons on Byte 1
#define BTN_Y 0x01
#define BTN_X 0x02
#define BTN_B 0x04
#define BTN_A 0x08
#define BTN_R 0x40
#define BTN_ZR 0x80
// Buttons on Byte 2
#define BTN_MINUS 0x01
#define BTN_PLUS 0x02
#define BTN_RSTICK 0x04
#define BTN_LSTICK 0x08
#define BTN_HOME 0x10
#define BTN_CAPTURE 0x20
// Buttons on Byte 3
#define BTN_DOWN 0x01
#define BTN_UP 0x02
#define BTN_RIGHT 0x04
#define BTN_LEFT 0x08
#define BTN_L 0x40
#define BTN_ZL 0x80

// Different controller types
enum ControllerType {
    CT_PRO_CONTROLLER = 0x03,
    CT_JOYCON_L = 0x01,
    CT_JOYCON_R = 0x02
};

// Counters are written from the Bluetooth tasks and copied for diagnostics.
struct SwitchLinkStats {
    uint32_t pmModeChanges;
    uint8_t lastPmMode;
    uint32_t reportTxFailures;
    uint32_t subcommands;
    uint32_t unknownSubcommands;
    uint8_t lastUnknownSubcommand;
    uint32_t reconnectAttempts;
    uint32_t policyApplied;
};

class SwitchController {
public:
    SwitchController();
    bool begin(ControllerType type = CT_PRO_CONTROLLER);
    bool isConnected();
    bool isHandshakeComplete();

    // Call from loop(): prints deferred Bluetooth events and reconnects to
    // the known Switch when the HID link drops.
    void service(uint32_t now);
    SwitchLinkStats stats();

    void setButtons(uint8_t b1, uint8_t b2, uint8_t b3);
    void setSticks(uint8_t lx, uint8_t ly, uint8_t rx, uint8_t ry);
    void sendReport();

private:
    static void hid_cb(esp_hidd_cb_event_t event, esp_hidd_cb_param_t *param);
    static void gap_cb(esp_bt_gap_cb_event_t event, esp_bt_gap_cb_param_t *param);
    static ControllerType _activeType;

    uint32_t _lastReconnectMs = 0;
    uint32_t _reconnectDelayMs = 0;
};

#endif
