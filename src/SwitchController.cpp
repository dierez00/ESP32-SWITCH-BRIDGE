#include "SwitchController.h"

#include <algorithm>
#include <atomic>
#include <cstring>

#include "nvs_flash.h"

// Nintendo HID descriptor and replies
static uint8_t hid_descriptor[] = {0x05, 0x01, 0x09, 0x05, 0xa1, 0x01, 0x06, 0x01, 0xff, 0x85, 0x21, 0x09, 0x21, 0x75, 0x08, 0x95, 0x30, 0x81, 0x02, 0x85, 0x30, 0x09, 0x30, 0x75, 0x08, 0x95, 0x30, 0x81, 0x02, 0x85, 0x31, 0x09, 0x31, 0x75, 0x08, 0x96, 0x69, 0x01, 0x81, 0x02, 0x85, 0x32, 0x09, 0x32, 0x75, 0x08, 0x96,
    0x69, 0x01, 0x81, 0x02, 0x85, 0x33, 0x09, 0x33, 0x75, 0x08, 0x96, 0x69, 0x01, 0x81, 0x02, 0x85, 0x3f, 0x05, 0x09, 0x19, 0x01, 0x29, 0x10, 0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95, 0x10, 0x81, 0x02, 0x05, 0x01, 0x09, 0x39, 0x15, 0x00, 0x25, 0x07, 0x75, 0x04, 0x95, 0x01, 0x81, 0x42, 0x05, 0x09, 0x75, 0x04, 0x95,
    0x01, 0x81, 0x01, 0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x09, 0x33, 0x09, 0x34, 0x16, 0x00, 0x00, 0x27, 0xff, 0xff, 0x00, 0x00, 0x75, 0x10, 0x95, 0x04, 0x81, 0x02, 0x06, 0x01, 0xff, 0x85, 0x01, 0x09, 0x01, 0x75, 0x08, 0x95, 0x30, 0x91, 0x02, 0x85, 0x10, 0x09, 0x10, 0x75, 0x08, 0x95, 0x30, 0x91, 0x02, 0x85, 0x11,
    0x09, 0x11, 0x75, 0x08, 0x95, 0x30, 0x91, 0x02, 0x85, 0x12, 0x09, 0x12, 0x75, 0x08, 0x95, 0x30, 0x91, 0x02, 0xc0};
static uint8_t reply02[] = {0x00, 0x8E, 0x00, 0x00, 0x00, 0x00, 0x08, 0x80, 0x00, 0x00, 0x00, 0x00, 0x82, 0x02, 0x04, 0x00, 0x03, 0x02, 0xD4, 0xF0, 0x57, 0x6E, 0xF0, 0xD7, 0x01, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
static uint8_t reply08[] = {0x01, 0x8E, 0x00, 0x00, 0x00, 0x00, 0x08, 0x80, 0x00, 0x00, 0x00, 0x00, 0x80, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0,  0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
static uint8_t reply03[] = {0x04, 0x8E, 0x00, 0x00, 0x00, 0x00, 0x08, 0x80, 0x00, 0x00, 0x00, 0x00, 0x80, 0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
static uint8_t reply04[] = {0x0A, 0x8E, 0x00, 0x00, 0x00, 0x00, 0x08, 0x80, 0x00, 0x00, 0x00, 0x00, 0x83, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x2c, 0x01, 0x2c, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
static uint8_t spi_reply_address_0[] = {0x02, 0x8E, 0x00, 0x00, 0x00, 0x00, 0x08, 0x80, 0x00, 0x00, 0x00, 0x00, 0x90, 0x10, 0x00, 0x60, 0x00, 0x00, 0x10, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0x00, 0x00, 0x03, 0xA0, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
static uint8_t spi_reply_address_0x50[] = {0x03, 0x8E, 0x00, 0x00, 0x00, 0x00, 0x08, 0x80, 0x00, 0x00, 0x00, 0x00, 0x90, 0x10, 0x50, 0x60, 0x00, 0x00, 0x0D, 0x23, 0x23, 0x23, 0xff, 0xff, 0xff, 0x95, 0x15, 0x15, 0x15, 0x15, 0x95, 0xff, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
static uint8_t spi_reply_address_0x80[] = {0x0B, 0x8E, 0x00, 0x00, 0x00, 0x00, 0x08, 0x80, 0x00, 0x00, 0x00, 0x00, 0x90, 0x10, 0x80, 0x60, 0x00, 0x00, 0x18, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0x00, 0x00, 0x00, 0x00, 0x00};
static uint8_t spi_reply_address_0x98[] = {0x0C, 0x8E, 0x00, 0x00, 0x00, 0x00, 0x08, 0x80, 0x00, 0x00, 0x00, 0x00, 0x90, 0x10, 0x98, 0x60, 0x00, 0x00, 0x12, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
static uint8_t spi_reply_address_0x10[] = {0x0D, 0x8E, 0x00, 0x00, 0x00, 0x00, 0x08, 0x80, 0x00, 0x00, 0x00, 0x00, 0x90, 0x10, 0x10, 0x80, 0x00, 0x00, 0x18, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0x00, 0x00, 0x00, 0x00, 0x00};
static uint8_t spi_reply_address_0x3d[] = {0x0E, 0x8E, 0x00, 0x00, 0x00, 0x00, 0x08, 0x80, 0x00, 0x00, 0x00, 0x00, 0x90, 0x10, 0x3D, 0x60, 0x00, 0x00, 0x19, 0x00, 0x07, 0x70, 0x00, 0x08, 0x80, 0x00, 0x07, 0x70, 0x00, 0x08, 0x80, 0x00, 0x07, 0x70, 0x00, 0x07, 0x70, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xff, 0xff, 0x00, 0x00, 0x00, 0x00};
static uint8_t spi_reply_address_0x20[] = {0x10, 0x8E, 0x00, 0x00, 0x00, 0x00, 0x08, 0x80, 0x00, 0x00, 0x00, 0x00, 0x90, 0x10, 0x20, 0x60, 0x00, 0x00, 0x18, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0x00, 0x00, 0x00, 0x00, 0x00};
static uint8_t reply4001[] = {0x15, 0x8E, 0x00, 0x00, 0x00, 0x00, 0x08, 0x80, 0x00, 0x00, 0x00, 0x00, 0x80, 0x40, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
static uint8_t reply4801[] = {0x1A, 0x8E, 0x00, 0x00, 0x00, 0x00, 0x08, 0x80, 0x00, 0x00, 0x00, 0x00, 0x80, 0x48, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
static uint8_t reply3001[] = {0x1C, 0x8E, 0x00, 0x00, 0x00, 0x00, 0x08, 0x80, 0x00, 0x00, 0x00, 0x00, 0x80, 0x30, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
static uint8_t r3333_l[] = {0x03, 0x8E, 0x84, 0x00, 0x12, 0x01, 0x18, 0x80, 0x01, 0x18, 0x80, 0x80, 0x80, 0x21, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
static uint8_t r3333_r[] = {0x31, 0x8e, 0x00, 0x00, 0x00, 0x00, 0x08, 0x80, 0x00, 0x08, 0x80, 0x00, 0xa0, 0x21, 0x01, 0x00, 0x00, 0x00, 0x03, 0x00, 0x05, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7b, 0x00};
static uint8_t r3333_pro[] = {0x31, 0x8e, 0x00, 0x00, 0x00, 0x00, 0x08, 0x80, 0x00, 0x08, 0x80, 0x00, 0xa0, 0x21, 0x01, 0x00, 0x00, 0x00, 0x03, 0x00, 0x05, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7b, 0x00};

// Bluedroid internals (private bt headers). Every HID report makes Bluedroid's
// power manager request sniff mode immediately (bta_hd_act.c + bta_dm_cfg.c HD
// spec). Outside the grip menu the Switch negotiates sniff itself, the two
// requests collide (LMP 0x23) and the Switch drops the HID channels. The PM
// never initiates sniff when the peer's link policy lacks it (bta_dm_pm.c).
extern "C" {
void bta_sys_clear_policy(uint8_t id, uint8_t policy, uint8_t *peer_addr);
uint8_t BTM_SetLinkPolicy(uint8_t *remote_bda, uint16_t *settings);
}

static constexpr uint8_t kBtaIdHd = 20;
static constexpr uint16_t kHciEnableRoleSwitch = 0x0001;
static constexpr uint16_t kHciEnableSniff = 0x0004;

// 0: keep the link active; the controller also rejects sniff requested by the
//    Switch. The Switch drops the HID channels ~300 ms after connecting.
// 1: never initiate sniff locally, but accept sniff requested by the Switch.
#ifndef SWITCH_LINK_POLICY
#define SWITCH_LINK_POLICY 1
#endif

// Registration is asynchronous. These objects must outlive begin() because
// Bluedroid consumes their pointers later from its own task.
static esp_hidd_app_param_t hid_app_param = {};
static esp_hidd_qos_param_t hid_qos = {};

// Shared between the Arduino loop and the Bluetooth callbacks.
static portMUX_TYPE s_mux = portMUX_INITIALIZER_UNLOCKED;
static uint8_t s_buttons[3] = {0, 0, 0};
static uint8_t s_sticks[4] = {128, 128, 128, 128};
static esp_bd_addr_t s_hostAddress = {};
static bool s_hostKnown = false;
static SwitchLinkStats s_stats = {};

static std::atomic<uint8_t> s_timer{0};
static std::atomic<bool> s_connected{false};
static std::atomic<bool> s_connecting{false};
static std::atomic<bool> s_handshakeComplete{false};
static std::atomic<uint32_t> s_pendingEvents{0};

enum PendingEvent : uint32_t {
    EVT_OPEN = 1 << 0,
    EVT_CLOSE = 1 << 1,
    EVT_CONNECTING = 1 << 2,
    EVT_OPEN_FAILED = 1 << 3,
    EVT_HANDSHAKE = 1 << 4,
    EVT_POLICY = 1 << 5,
};

static void postEvent(uint32_t event) {
    s_pendingEvents.fetch_or(event);
}

static void rememberHost(const uint8_t *address) {
    portENTER_CRITICAL(&s_mux);
    memcpy(s_hostAddress, address, sizeof(s_hostAddress));
    s_hostKnown = true;
    portEXIT_CRITICAL(&s_mux);
}

static void applyLinkPolicy(uint8_t *address) {
    // Runs in the BTC task: one uint16 update in the BTA peer record plus a
    // queued HCI Write Link Policy command.
    bta_sys_clear_policy(kBtaIdHd, kHciEnableSniff, address);
#if SWITCH_LINK_POLICY == 1
    uint16_t settings = kHciEnableRoleSwitch | kHciEnableSniff;
    BTM_SetLinkPolicy(address, &settings);
#else
    (void)kHciEnableRoleSwitch;
#endif
    portENTER_CRITICAL(&s_mux);
    ++s_stats.policyApplied;
    portEXIT_CRITICAL(&s_mux);
    postEvent(EVT_POLICY);
}

static uint16_t expandStickTo12Bits(uint8_t value) {
    // Keep Android's neutral value (128) exactly at the calibrated Switch
    // center (0x800), while still reaching both 12-bit endpoints.
    if (value <= 128) {
        return static_cast<uint16_t>(value) << 4;
    }

    return 0x800U +
           ((static_cast<uint32_t>(value - 128) * 0x7FFU + 63U) / 127U);
}

static void packStick(uint8_t *target, uint8_t x, uint8_t y) {
    const uint16_t x12 = expandStickTo12Bits(x);
    const uint16_t y12 = expandStickTo12Bits(y);

    target[0] = static_cast<uint8_t>(x12);
    target[1] = static_cast<uint8_t>(((x12 >> 8) & 0x0F) |
                                     ((y12 & 0x0F) << 4));
    target[2] = static_cast<uint8_t>(y12 >> 4);
}

// Fills timer, buttons and sticks (bytes 0 and 2..10) with the live state so
// every report the Switch receives agrees with the latest input.
static void fillInputState(uint8_t *report) {
    report[0] = s_timer.fetch_add(1);
    portENTER_CRITICAL(&s_mux);
    const uint8_t b0 = s_buttons[0], b1 = s_buttons[1], b2 = s_buttons[2];
    const uint8_t lx = s_sticks[0], ly = s_sticks[1];
    const uint8_t rx = s_sticks[2], ry = s_sticks[3];
    portEXIT_CRITICAL(&s_mux);
    report[2] = b0; report[3] = b1; report[4] = b2;
    packStick(&report[5], lx, ly);
    packStick(&report[8], rx, ry);
}

static esp_err_t sendSubcommandReply(const uint8_t *tpl, size_t len) {
    uint8_t reply[64];
    if (len > sizeof(reply)) return ESP_ERR_INVALID_SIZE;
    memcpy(reply, tpl, len);
    fillInputState(reply);
    return esp_bt_hid_device_send_report(
        ESP_HIDD_REPORT_TYPE_INTRDATA, 0x21, len, reply);
}

// Plain ACK (0x80 + subcommand id) for subcommands without payload, such as
// HOME light (0x38) or MCU state (0x22). Unanswered ones get retried.
static esp_err_t sendSubcommandAck(uint8_t subcommand) {
    uint8_t reply[48] = {0};
    reply[1] = 0x8E;
    reply[12] = 0x80;
    reply[13] = subcommand;
    return sendSubcommandReply(reply, sizeof(reply));
}

// Default values
ControllerType SwitchController::_activeType = CT_PRO_CONTROLLER;

SwitchController::SwitchController() {}

void SwitchController::gap_cb(esp_bt_gap_cb_event_t event, esp_bt_gap_cb_param_t *param) {
    if (event != ESP_BT_GAP_MODE_CHG_EVT) return;
    portENTER_CRITICAL(&s_mux);
    ++s_stats.pmModeChanges;
    s_stats.lastPmMode = param->mode_chg.mode;
    portEXIT_CRITICAL(&s_mux);
}

// Callback handler
void SwitchController::hid_cb(esp_hidd_cb_event_t event, esp_hidd_cb_param_t *param) {
    switch (event) {
        case ESP_HIDD_INIT_EVT: {
            if (param->init.status != ESP_HIDD_SUCCESS) {
                Serial.printf("HID Device init failed: status=%d\n",
                              param->init.status);
                break;
            }

            esp_err_t err = esp_bt_hid_device_register_app(
                &hid_app_param, &hid_qos, &hid_qos);
            if (err != ESP_OK) {
                Serial.printf("Could not register HID Device app: %s\n",
                              esp_err_to_name(err));
            }
            break;
        }
        // Check for known devices after BT setup is ready
        case ESP_HIDD_REGISTER_APP_EVT: {
            if (param->register_app.status != ESP_HIDD_SUCCESS) {
                Serial.printf("HID Device registration failed: status=%d\n",
                              param->register_app.status);
                break;
            }

            // Bluedroid keeps the HID virtual-cable host independently from the
            // generic bond list. This is the Switch address; using bond[0] is no
            // longer safe because the DS4 is bonded too.
            if (param->register_app.in_use) {
                const uint8_t *address = param->register_app.bd_addr;
                Serial.printf(
                    "Reconnecting HID Device to Switch [%02X:%02X:%02X:%02X:%02X:%02X]...\n",
                    address[0], address[1], address[2],
                    address[3], address[4], address[5]);
                rememberHost(address);
                esp_err_t err = esp_bt_hid_device_connect(param->register_app.bd_addr);
                if (err == ESP_OK) {
                    s_connecting = true;
                } else {
                    Serial.printf("Could not request connection to Switch: %s\n",
                                  esp_err_to_name(err));
                }
            } else {
                int count = esp_bt_gap_get_bond_device_num();
                esp_bd_addr_t *addresses = count > 0
                    ? static_cast<esp_bd_addr_t *>(malloc(sizeof(esp_bd_addr_t) * count))
                    : nullptr;

                if (addresses != nullptr &&
                    esp_bt_gap_get_bond_device_list(&count, addresses) == ESP_OK) {
                    const uint8_t *address = addresses[0];
                    Serial.printf(
                        "No virtual cable; trying bonded host [%02X:%02X:%02X:%02X:%02X:%02X]...\n",
                        address[0], address[1], address[2],
                        address[3], address[4], address[5]);
                    esp_err_t err = esp_bt_hid_device_connect(addresses[0]);
                    if (err != ESP_OK) {
                        Serial.printf("Could not request HID Device connection: %s\n",
                                      esp_err_to_name(err));
                    }
                } else {
                    Serial.println("Switch not bonded; waiting for pairing...");
                    esp_bt_gap_set_scan_mode(
                        ESP_BT_CONNECTABLE, ESP_BT_GENERAL_DISCOVERABLE);
                }
                free(addresses);
            }
            break;
        }
        // Connected event
        case ESP_HIDD_OPEN_EVT: {
            s_handshakeComplete = false;
            const bool connected = param->open.status == ESP_HIDD_SUCCESS &&
                param->open.conn_status == ESP_HIDD_CONN_STATE_CONNECTED;

            if (connected) {
                // Before any reply is sent: stop the local PM from forcing sniff.
                applyLinkPolicy(param->open.bd_addr);
                rememberHost(param->open.bd_addr);
                s_connecting = false;
                s_connected = true;
                postEvent(EVT_OPEN);
            } else if (param->open.status == ESP_HIDD_SUCCESS &&
                       param->open.conn_status == ESP_HIDD_CONN_STATE_CONNECTING) {
                s_connecting = true;
                postEvent(EVT_CONNECTING);
            } else {
                s_connected = false;
                s_connecting = false;
                postEvent(EVT_OPEN_FAILED);
            }
            break;
        }
        // Disconnected event
        case ESP_HIDD_CLOSE_EVT:
            s_connected = false;
            s_connecting = false;
            s_handshakeComplete = false;
            postEvent(EVT_CLOSE);
            esp_bt_gap_set_scan_mode(ESP_BT_CONNECTABLE, ESP_BT_GENERAL_DISCOVERABLE);
            break;
        case ESP_HIDD_SEND_REPORT_EVT:
            if (param->send_report.status != ESP_HIDD_SUCCESS) {
                portENTER_CRITICAL(&s_mux);
                ++s_stats.reportTxFailures;
                portEXIT_CRITICAL(&s_mux);
            }
            break;
        // Replying to switch requests
        case ESP_HIDD_INTR_DATA_EVT: {
            if (!s_connected || param->intr_data.data == nullptr) {
                break;
            }

            // OUTPUT 0x10 contains only the packet counter and eight rumble
            // bytes. The Switch sends it continuously during normal use; it
            // has no subcommand to answer and must not generate serial traffic
            // from inside the Bluetooth callback.
            if (param->intr_data.report_id == 0x10) {
                break;
            }

            // Normal subcommands arrive in OUTPUT 0x01. Its payload needs the
            // packet counter, eight rumble bytes and the subcommand byte.
            if (param->intr_data.report_id != 0x01 ||
                param->intr_data.len < 10) {
                break;
            }

            const uint8_t* p = param->intr_data.data;
            const uint16_t len = param->intr_data.len;
            // Byte 9 for request ID
            const uint8_t subcommand = p[9];
            portENTER_CRITICAL(&s_mux);
            ++s_stats.subcommands;
            portEXIT_CRITICAL(&s_mux);

            bool handled = true;
            if (subcommand == 0x02) {
                sendSubcommandReply(reply02, sizeof(reply02));
            } else if (subcommand == 0x08) {
                sendSubcommandReply(reply08, sizeof(reply08));
            } else if (subcommand == 0x03) {
                esp_err_t err = sendSubcommandReply(reply03, sizeof(reply03));

                // Subcommand 0x03 selects the input report mode. Only start
                // periodic 0x30 reports after the Switch explicitly requests it.
                if (err == ESP_OK && len >= 11 && p[10] == 0x30) {
                    s_handshakeComplete = true;
                    postEvent(EVT_HANDSHAKE);
                }
            } else if (subcommand == 0x04) {
                sendSubcommandReply(reply04, sizeof(reply04));
            }
            // SPI requests
            else if (subcommand == 0x10 && len >= 12) {
                if (p[10] == 0x00 && p[11] == 0x60) {
                    sendSubcommandReply(spi_reply_address_0, sizeof(spi_reply_address_0));
                } else if (p[10] == 0x50 && p[11] == 0x60) {
                    sendSubcommandReply(spi_reply_address_0x50, sizeof(spi_reply_address_0x50));
                } else if (p[10] == 0x80 && p[11] == 0x60) {
                    sendSubcommandReply(spi_reply_address_0x80, sizeof(spi_reply_address_0x80));
                } else if (p[10] == 0x98 && p[11] == 0x60) {
                    sendSubcommandReply(spi_reply_address_0x98, sizeof(spi_reply_address_0x98));
                } else if (p[10] == 0x10 && p[11] == 0x80) {
                    sendSubcommandReply(spi_reply_address_0x10, sizeof(spi_reply_address_0x10));
                } else if (p[10] == 0x3D && p[11] == 0x60) {
                    sendSubcommandReply(spi_reply_address_0x3d, sizeof(spi_reply_address_0x3d));
                } else if (p[10] == 0x20 && p[11] == 0x60) {
                    sendSubcommandReply(spi_reply_address_0x20, sizeof(spi_reply_address_0x20));
                } else {
                    // An SPI read needs its data; an empty ACK would be wrong.
                    handled = false;
                }
            }
            // IMU and sensors
            else if (subcommand == 0x40) {
                sendSubcommandReply(reply4001, sizeof(reply4001));
            } else if (subcommand == 0x48) {
                sendSubcommandReply(reply4801, sizeof(reply4801));
            } else if (subcommand == 0x30) {
                sendSubcommandReply(reply3001, sizeof(reply3001));
            } else if (subcommand == 0x21 && len >= 11 && p[10] == 0x21) {
                switch (_activeType) {
                    case CT_JOYCON_L:
                        sendSubcommandReply(r3333_l, sizeof(r3333_l));
                        break;
                    case CT_JOYCON_R:
                        sendSubcommandReply(r3333_r, sizeof(r3333_r));
                        break;
                    default:
                        sendSubcommandReply(r3333_pro, sizeof(r3333_pro));
                        break;
                }
            } else {
                // 0x34, 0x38 (HOME light), 0x22 (MCU state), 0x01, 0x06, ...
                sendSubcommandAck(subcommand);
                handled = false;
            }

            if (!handled) {
                portENTER_CRITICAL(&s_mux);
                ++s_stats.unknownSubcommands;
                s_stats.lastUnknownSubcommand = subcommand;
                portEXIT_CRITICAL(&s_mux);
            }
            break;
        }
        default:
            break;
    }
}

bool SwitchController::begin(ControllerType type) {
    _activeType = type;

    uint8_t bt_mac[6];
    esp_read_mac(bt_mac, ESP_MAC_BT);

    Serial.printf("Bluetooth MAC: %02X:%02X:%02X:%02X:%02X:%02X\n", bt_mac[0], bt_mac[1], bt_mac[2], bt_mac[3], bt_mac[4], bt_mac[5]);

    // Change replies to fit active controller mode
    reply02[16] = (uint8_t)_activeType;
    reply02[18] = bt_mac[0];
    reply02[19] = bt_mac[1];
    reply02[20] = bt_mac[2];
    reply02[21] = bt_mac[3];
    reply02[22] = bt_mac[4];
    reply02[23] = bt_mac[5];
    reply02[25] = (_activeType == CT_PRO_CONTROLLER) ? 0x02 : 0x01;

    spi_reply_address_0[37] = (uint8_t)_activeType;

    if (_activeType == CT_PRO_CONTROLLER) {
        spi_reply_address_0x50[25] = 0x95; spi_reply_address_0x50[26] = 0x15; spi_reply_address_0x50[27] = 0x15;
        spi_reply_address_0x50[28] = 0x15; spi_reply_address_0x50[29] = 0x15; spi_reply_address_0x50[30] = 0x95;
    } else {
        for(int i=25; i<=30; i++) spi_reply_address_0x50[i] = 0xFF;
    }

    // BT startup logic
    esp_err_t ret;

    ret = nvs_flash_init();
    if (ret == ESP_ERR_NVS_NO_FREE_PAGES || ret == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        nvs_flash_erase();
        ret = nvs_flash_init();
    }

    // Arduino BT startup
    if (!btStart()) {
        Serial.println("btStart failed!");
        return false;
    }

    // Bluedroid activation
    if (esp_bluedroid_get_status() == ESP_BLUEDROID_STATUS_UNINITIALIZED) {
        ret = esp_bluedroid_init();
        if (ret != ESP_OK) {
            Serial.printf("Bluedroid Init failed: %s\n", esp_err_to_name(ret));
            return false;
        }
    }

    if (esp_bluedroid_get_status() == ESP_BLUEDROID_STATUS_INITIALIZED) {
        ret = esp_bluedroid_enable();
        if (ret != ESP_OK) {
            Serial.printf("Bluedroid Enable failed: %s\n", esp_err_to_name(ret));
            return false;
        }
    }

    delay(100);

    ret = esp_bt_gap_register_callback(gap_cb);
    if (ret != ESP_OK) {
        Serial.printf("GAP callback registration failed: %s\n",
                      esp_err_to_name(ret));
    }

    /*  CoD definition
        cod.service might vary with other controller types! */
    esp_bt_cod_t cod;
    cod.major = 0b00101;
    cod.minor = 0b000010;
    cod.service = 0b00000000010;
    esp_bt_gap_set_cod(cod, ESP_BT_INIT_COD);

    esp_bt_sp_param_t param_type = ESP_BT_SP_IOCAP_MODE;
    esp_bt_io_cap_t iocap = ESP_BT_IO_CAP_NONE;
    esp_bt_gap_set_security_param(param_type, &iocap, sizeof(uint8_t));

    // Device name based on controller type
    const char* device_name;
    if (_activeType == CT_JOYCON_L) device_name = "Joy-Con (L)";
    else if (_activeType == CT_JOYCON_R) device_name = "Joy-Con (R)";
    else device_name = "Pro Controller";

    // BT app parameters
    hid_app_param.name = device_name;
    hid_app_param.description = "Gamepad";
    hid_app_param.provider = "Nintendo";
    hid_app_param.subclass = 0x08;
    hid_app_param.desc_list = hid_descriptor;
    hid_app_param.desc_list_len = sizeof(hid_descriptor);
    
    // HID register logic
    ret = esp_bt_hid_device_register_callback(hid_cb);
    if (ret != ESP_OK) {
        Serial.printf("HID callback registration failed: %s\n",
                      esp_err_to_name(ret));
        return false;
    }

    ret = esp_bt_hid_device_init();
    if (ret != ESP_OK) {
        Serial.printf("HID Device init request failed: %s\n",
                      esp_err_to_name(ret));
        return false;
    }
    
    // BT active
    esp_bt_dev_set_device_name(device_name);
    esp_bt_gap_set_scan_mode(ESP_BT_CONNECTABLE, ESP_BT_GENERAL_DISCOVERABLE);

    return true;
}

void SwitchController::service(uint32_t now) {
    static constexpr uint32_t kFirstRetryMs = 1000;
    static constexpr uint32_t kMaxRetryMs = 15000;
    static constexpr uint32_t kConnectTimeoutMs = 10000;

    const uint32_t events = s_pendingEvents.exchange(0);
    if (events & EVT_CONNECTING) Serial.println("Switch HID connecting...");
    if (events & EVT_OPEN) {
        Serial.println("Switch HID connected; waiting for handshake...");
    }
    if (events & EVT_POLICY) {
        Serial.printf("Link policy applied: %s\n",
                      SWITCH_LINK_POLICY == 1
                          ? "no local sniff, accept sniff from Switch"
                          : "link active (no sniff)");
    }
    if (events & EVT_HANDSHAKE) {
        Serial.println("Handshake complete; report mode 0x30 active.");
        // Only a completed handshake proves the link is healthy; resetting on
        // OPEN would retry every second when the Switch keeps dropping us.
        _reconnectDelayMs = 0;
    }
    if (events & (EVT_CLOSE | EVT_OPEN_FAILED)) {
        Serial.println(events & EVT_CLOSE ? "Switch HID disconnected."
                                          : "Failed to open HID.");
        if (_reconnectDelayMs == 0) _reconnectDelayMs = kFirstRetryMs;
        _lastReconnectMs = now;
    }

    if (s_connected) return;
    if (s_connecting) {
        // Bluedroid normally reports the outcome; don't wait forever for it.
        if (_reconnectDelayMs == 0 || now - _lastReconnectMs < kConnectTimeoutMs) return;
        s_connecting = false;
    }
    if (_reconnectDelayMs == 0 || now - _lastReconnectMs < _reconnectDelayMs) return;

    esp_bd_addr_t address;
    portENTER_CRITICAL(&s_mux);
    const bool known = s_hostKnown;
    memcpy(address, s_hostAddress, sizeof(address));
    portEXIT_CRITICAL(&s_mux);
    if (!known) return;

    _lastReconnectMs = now;
    _reconnectDelayMs = std::min(_reconnectDelayMs * 2, kMaxRetryMs);
    portENTER_CRITICAL(&s_mux);
    ++s_stats.reconnectAttempts;
    portEXIT_CRITICAL(&s_mux);

    Serial.println("Retrying connection to Switch...");
    if (esp_bt_hid_device_connect(address) == ESP_OK) {
        s_connecting = true;
    }
}

SwitchLinkStats SwitchController::stats() {
    portENTER_CRITICAL(&s_mux);
    const SwitchLinkStats copy = s_stats;
    portEXIT_CRITICAL(&s_mux);
    return copy;
}

// Sending current controller state
void SwitchController::sendReport() {
    if (!s_connected) return;
    uint8_t rep[48] = {0};
    rep[1] = 0x8E;
    fillInputState(rep);
    esp_bt_hid_device_send_report(ESP_HIDD_REPORT_TYPE_INTRDATA, 0x30, sizeof(rep), rep);
}

bool SwitchController::isConnected() { return s_connected; }
bool SwitchController::isHandshakeComplete() { return s_handshakeComplete; }

void SwitchController::setButtons(uint8_t b1, uint8_t b2, uint8_t b3) {
    portENTER_CRITICAL(&s_mux);
    s_buttons[0] = b1; s_buttons[1] = b2; s_buttons[2] = b3;
    portEXIT_CRITICAL(&s_mux);
}

void SwitchController::setSticks(uint8_t lx, uint8_t ly, uint8_t rx, uint8_t ry) {
    portENTER_CRITICAL(&s_mux);
    s_sticks[0] = lx; s_sticks[1] = ly; s_sticks[2] = rx; s_sticks[3] = ry;
    portEXIT_CRITICAL(&s_mux);
}
