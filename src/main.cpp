#include <Arduino.h>
#include <WiFi.h>
#include <WiFiUdp.h>

#include "SwitchController.h"
#include "UdpProtocol.h"

// Optional: -D SWITCH_COEX_PREFER_BT=1 in platformio.ini build_flags.
#ifndef SWITCH_COEX_PREFER_BT
#define SWITCH_COEX_PREFER_BT 0
#endif
#if SWITCH_COEX_PREFER_BT
#include "esp_coexist.h"
#endif

namespace {

constexpr char kApSsid[] = "SwitchBridge";
constexpr char kApPassword[] = "switchbridge";
constexpr uint16_t kUdpPort = 4210;
constexpr uint32_t kInputTimeoutMs = 150;
constexpr uint32_t kAckIntervalMs = 250;
constexpr uint32_t kDiagnosticsIntervalMs = 1000;
constexpr uint32_t kReportIntervalMs = 15;
constexpr uint32_t kHandshakeReportIntervalMs = 500;

struct BridgeState {
    bool senderKnown = false;
    bool sequenceActive = false;
    bool flowActive = false;
    bool firstPacketLogged = false;
    IPAddress senderIp;
    uint16_t senderPort = 0;
    uint8_t lastSequence = 0;
    uint32_t lastPacketMs = 0;
    SwitchBridgeProtocol::InputPacket input = {};
};

struct Diagnostics {
    uint32_t validPackets = 0;
    uint32_t appliedPackets = 0;
    uint32_t invalidCrc = 0;
    uint32_t invalidLength = 0;
    uint32_t invalidHeader = 0;
    uint32_t duplicatePackets = 0;
    uint32_t outOfOrderPackets = 0;
    uint32_t sequenceLosses = 0;
};

SwitchController controller;
WiFiUDP udp;
BridgeState bridge;
Diagnostics diagnostics;

uint32_t lastReportMs = 0;
uint32_t lastAckMs = 0;
uint32_t lastDiagnosticsMs = 0;
bool ackSent = false;
bool switchLinkSeen = false;
bool udpReady = false;

uint32_t elapsedSince(uint32_t now, uint32_t then) {
    return now - then;
}

bool hasRecentInput(uint32_t now) {
    return bridge.sequenceActive &&
           elapsedSince(now, bridge.lastPacketMs) < kInputTimeoutMs;
}

void setNeutralControllerState() {
    controller.setButtons(0, 0, 0);
    controller.setSticks(128, 128, 128, 128);
}

void setDMacAddress() {
    // The Nintendo OUI must remain unchanged. The Bluetooth address is the
    // configured base address plus two.
    uint8_t dmacAddress[6] = {0xD4, 0xF0, 0x57, 0x45, 0x56, 0x33};
    esp_base_mac_addr_set(dmacAddress);
}

bool startSoftAp() {
    const IPAddress apIp(192, 168, 4, 1);
    const IPAddress gateway(192, 168, 4, 1);
    const IPAddress subnet(255, 255, 255, 0);

    WiFi.mode(WIFI_AP);
    WiFi.setSleep(false);

    if (!WiFi.softAPConfig(apIp, gateway, subnet)) {
        Serial.println("Failed to configure SoftAP IP.");
        return false;
    }
    if (!WiFi.softAP(kApSsid, kApPassword)) {
        Serial.println("Failed to start SoftAP.");
        return false;
    }
    if (!udp.begin(kUdpPort)) {
        Serial.println("Failed to open UDP port.");
        return false;
    }

    Serial.println("SoftAP started.");
    Serial.printf("SSID: %s\n", kApSsid);
    Serial.printf("IP: %s\n", WiFi.softAPIP().toString().c_str());
    Serial.printf("UDP port: %u\n", static_cast<unsigned>(kUdpPort));
    return true;
}

void acceptInputPacket(
    const SwitchBridgeProtocol::InputPacket &packet,
    const IPAddress &senderIp,
    uint16_t senderPort,
    uint32_t now
) {
    if (bridge.sequenceActive) {
        const uint8_t delta = static_cast<uint8_t>(
            packet.sequence - bridge.lastSequence);
        const SwitchBridgeProtocol::SequenceRelation relation =
            SwitchBridgeProtocol::compareSequence(
                packet.sequence, bridge.lastSequence);
        if (relation == SwitchBridgeProtocol::SequenceRelation::Duplicate) {
            ++diagnostics.duplicatePackets;
            return;
        }
        if (relation == SwitchBridgeProtocol::SequenceRelation::OutOfOrder) {
            ++diagnostics.outOfOrderPackets;
            return;
        }
        diagnostics.sequenceLosses += static_cast<uint32_t>(delta - 1);
    }

    const bool recovering = bridge.firstPacketLogged && !bridge.flowActive;

    bridge.senderKnown = true;
    bridge.senderIp = senderIp;
    bridge.senderPort = senderPort;
    bridge.sequenceActive = true;
    bridge.flowActive = true;
    bridge.lastSequence = packet.sequence;
    bridge.lastPacketMs = now;
    bridge.input = packet;
    ++diagnostics.appliedPackets;

    if (!bridge.firstPacketLogged) {
        bridge.firstPacketLogged = true;
        Serial.println(">>> First valid UDP packet received; bridge active. <<<");
    } else if (recovering) {
        Serial.println(">>> UDP stream recovered. <<<");
    }
}

void receiveUdpPackets(uint32_t now) {
    if (!udpReady) return;

    int packetSize = 0;
    while ((packetSize = udp.parsePacket()) > 0) {
        const IPAddress senderIp = udp.remoteIP();
        const uint16_t senderPort = udp.remotePort();

        if (packetSize != static_cast<int>(SwitchBridgeProtocol::kInputPacketSize)) {
            ++diagnostics.invalidLength;
            udp.flush();
            continue;
        }

        uint8_t bytes[SwitchBridgeProtocol::kInputPacketSize] = {};
        const int bytesRead = udp.read(bytes, sizeof(bytes));
        if (bytesRead != static_cast<int>(sizeof(bytes))) {
            ++diagnostics.invalidLength;
            udp.flush();
            continue;
        }

        SwitchBridgeProtocol::InputPacket packet = {};
        const SwitchBridgeProtocol::ParseResult result =
            SwitchBridgeProtocol::parseInputPacket(bytes, sizeof(bytes), packet);

        if (result == SwitchBridgeProtocol::ParseResult::InvalidCrc) {
            ++diagnostics.invalidCrc;
            continue;
        }
        if (result == SwitchBridgeProtocol::ParseResult::InvalidHeader) {
            ++diagnostics.invalidHeader;
            continue;
        }
        if (result != SwitchBridgeProtocol::ParseResult::Ok) {
            ++diagnostics.invalidLength;
            continue;
        }

        ++diagnostics.validPackets;
        acceptInputPacket(packet, senderIp, senderPort, now);
    }
}

void handleInputTimeout(uint32_t now) {
    if (!bridge.flowActive ||
        elapsedSince(now, bridge.lastPacketMs) < kInputTimeoutMs) {
        return;
    }

    bridge.flowActive = false;
    bridge.sequenceActive = false;
    bridge.input.controllerConnected = false;
    Serial.println(">>> UDP stream lost; neutral state applied. <<<");
}

void sendAck(uint32_t now) {
    if (!udpReady || !bridge.senderKnown) return;
    if (ackSent && elapsedSince(now, lastAckMs) < kAckIntervalMs) return;

    uint8_t status = 0;
    if (controller.isConnected()) status |= 0x01;
    if (controller.isHandshakeComplete()) status |= 0x02;
    if (hasRecentInput(now)) status |= 0x04;

    const uint32_t rawAge = elapsedSince(now, bridge.lastPacketMs);
    const uint8_t age = rawAge > 255 ? 255 : static_cast<uint8_t>(rawAge);
    uint8_t ack[SwitchBridgeProtocol::kAckPacketSize] = {};
    SwitchBridgeProtocol::buildAckPacket(
        status, bridge.lastSequence, age, ack, sizeof(ack));

    if (udp.beginPacket(bridge.senderIp, bridge.senderPort)) {
        udp.write(ack, sizeof(ack));
        udp.endPacket();
    }
    ackSent = true;
    lastAckMs = now;
}

const char *pmModeName(uint8_t mode) {
    switch (mode) {
        case ESP_BT_PM_MD_ACTIVE: return "active";
        case ESP_BT_PM_MD_HOLD: return "hold";
        case ESP_BT_PM_MD_SNIFF: return "sniff";
        case ESP_BT_PM_MD_PARK: return "park";
        default: return "?";
    }
}

void sendSerialDiagnostics(uint32_t now) {
    if (elapsedSince(now, lastDiagnosticsMs) < kDiagnosticsIntervalMs) return;
    lastDiagnosticsMs = now;

    const SwitchLinkStats link = controller.stats();
    Serial.printf(
        "BT pm=%s modes=%lu tx_fail=%lu subcmd=%lu subcmd_unk=%lu(0x%02X) "
        "policy=%lu reconnects=%lu\n",
        pmModeName(link.lastPmMode),
        static_cast<unsigned long>(link.pmModeChanges),
        static_cast<unsigned long>(link.reportTxFailures),
        static_cast<unsigned long>(link.subcommands),
        static_cast<unsigned long>(link.unknownSubcommands),
        link.lastUnknownSubcommand,
        static_cast<unsigned long>(link.policyApplied),
        static_cast<unsigned long>(link.reconnectAttempts));

    if (bridge.senderKnown) {
        Serial.printf(
            "UDP valid=%lu applied=%lu crc=%lu length=%lu header=%lu "
            "duplicates=%lu out_of_order=%lu lost=%lu age=%lums L2=%u R2=%u "
            "BT=%d handshake=%d\n",
            static_cast<unsigned long>(diagnostics.validPackets),
            static_cast<unsigned long>(diagnostics.appliedPackets),
            static_cast<unsigned long>(diagnostics.invalidCrc),
            static_cast<unsigned long>(diagnostics.invalidLength),
            static_cast<unsigned long>(diagnostics.invalidHeader),
            static_cast<unsigned long>(diagnostics.duplicatePackets),
            static_cast<unsigned long>(diagnostics.outOfOrderPackets),
            static_cast<unsigned long>(diagnostics.sequenceLosses),
            static_cast<unsigned long>(elapsedSince(now, bridge.lastPacketMs)),
            bridge.input.l2,
            bridge.input.r2,
            controller.isConnected(),
            controller.isHandshakeComplete());
    } else {
        Serial.printf(
            "UDP valid=%lu applied=%lu crc=%lu length=%lu header=%lu "
            "duplicates=%lu out_of_order=%lu lost=%lu age=N/A L2=0 R2=0 "
            "BT=%d handshake=%d\n",
            static_cast<unsigned long>(diagnostics.validPackets),
            static_cast<unsigned long>(diagnostics.appliedPackets),
            static_cast<unsigned long>(diagnostics.invalidCrc),
            static_cast<unsigned long>(diagnostics.invalidLength),
            static_cast<unsigned long>(diagnostics.invalidHeader),
            static_cast<unsigned long>(diagnostics.duplicatePackets),
            static_cast<unsigned long>(diagnostics.outOfOrderPackets),
            static_cast<unsigned long>(diagnostics.sequenceLosses),
            controller.isConnected(),
            controller.isHandshakeComplete());
    }
}

void updateSwitch(uint32_t now) {
    if (!controller.isConnected()) {
        switchLinkSeen = false;
        return;
    }

    if (!switchLinkSeen) {
        switchLinkSeen = true;
        lastReportMs = now;
    }

    if (!controller.isHandshakeComplete()) {
        if (elapsedSince(now, lastReportMs) >= kHandshakeReportIntervalMs) {
            setNeutralControllerState();
            controller.sendReport();
            Serial.println("Waiting for Switch handshake; neutral report sent...");
            lastReportMs = now;
        }
        return;
    }

    if (elapsedSince(now, lastReportMs) < kReportIntervalMs) return;

    // Fixed 15 ms grid (~66 Hz) without drift; resync after a long stall.
    lastReportMs += kReportIntervalMs;
    if (elapsedSince(now, lastReportMs) >= kReportIntervalMs) {
        lastReportMs = now;
    }

    if (hasRecentInput(now) && bridge.input.controllerConnected) {
        controller.setButtons(
            bridge.input.b1, bridge.input.b2, bridge.input.b3);
        controller.setSticks(
            bridge.input.lx, bridge.input.ly,
            bridge.input.rx, bridge.input.ry);
    } else {
        setNeutralControllerState();
    }

    controller.sendReport();
}

}  // namespace

void setup() {
    // Must happen before either radio stack is initialized.
    setDMacAddress();

    Serial.begin(115200);
    delay(1000);
    Serial.println("System startup...");

    udpReady = startSoftAp();

    if (controller.begin(CT_PRO_CONTROLLER)) {
        Serial.println("Controller ready!");
    } else {
        Serial.println("Controller error!");
    }

#if SWITCH_COEX_PREFER_BT
    // Give the HID link more RF time than Wi-Fi; UDP input tolerates 150 ms.
    const esp_err_t coexErr = esp_coex_preference_set(ESP_COEX_PREFER_BT);
    Serial.printf("Coexistence prefer BT: %s\n", esp_err_to_name(coexErr));
#endif
}

void loop() {
    const uint32_t now = millis();
    receiveUdpPackets(now);
    handleInputTimeout(now);
    sendAck(now);
    controller.service(now);
    updateSwitch(now);
    sendSerialDiagnostics(now);
    delay(1);
}
