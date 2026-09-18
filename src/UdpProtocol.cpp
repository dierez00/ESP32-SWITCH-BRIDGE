#include "UdpProtocol.h"

namespace SwitchBridgeProtocol {

uint8_t crc8Atm(const uint8_t *data, size_t length) {
    uint8_t crc = 0x00;
    for (size_t i = 0; i < length; ++i) {
        crc ^= data[i];
        for (uint8_t bit = 0; bit < 8; ++bit) {
            crc = (crc & 0x80) != 0
                ? static_cast<uint8_t>((crc << 1) ^ 0x07)
                : static_cast<uint8_t>(crc << 1);
        }
    }
    return crc;
}

ParseResult parseInputPacket(
    const uint8_t *bytes,
    size_t length,
    InputPacket &packet
) {
    if (bytes == nullptr || length != kInputPacketSize) {
        return ParseResult::InvalidLength;
    }
    if (bytes[0] != 0x53 || bytes[1] != 0x42 || bytes[2] != 0x01) {
        return ParseResult::InvalidHeader;
    }
    if (crc8Atm(bytes, 15) != bytes[15]) {
        return ParseResult::InvalidCrc;
    }

    // Decode each byte explicitly; never depend on compiler struct layout.
    packet.controllerConnected = (bytes[3] & 0x01) != 0;
    packet.sequence = bytes[4];
    packet.b1 = bytes[5];
    packet.b2 = bytes[6];
    packet.b3 = bytes[7];
    packet.lx = bytes[8];
    packet.ly = bytes[9];
    packet.rx = bytes[10];
    packet.ry = bytes[11];
    packet.l2 = bytes[12];
    packet.r2 = bytes[13];
    return ParseResult::Ok;
}

SequenceRelation compareSequence(uint8_t candidate, uint8_t previous) {
    const uint8_t delta = static_cast<uint8_t>(candidate - previous);
    if (delta == 0) return SequenceRelation::Duplicate;
    if (delta < 128) return SequenceRelation::Newer;
    return SequenceRelation::OutOfOrder;
}

bool buildAckPacket(
    uint8_t status,
    uint8_t lastSequence,
    uint8_t packetAgeMs,
    uint8_t *output,
    size_t outputLength
) {
    if (output == nullptr || outputLength != kAckPacketSize) return false;

    output[0] = 0x53;
    output[1] = 0x41;
    output[2] = 0x01;
    output[3] = status;
    output[4] = lastSequence;
    output[5] = packetAgeMs;
    output[6] = 0x00;
    output[7] = crc8Atm(output, 7);
    return true;
}

}  // namespace SwitchBridgeProtocol
