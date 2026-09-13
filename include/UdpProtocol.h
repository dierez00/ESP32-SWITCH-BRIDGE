#ifndef UDP_PROTOCOL_H
#define UDP_PROTOCOL_H

#include <stddef.h>
#include <stdint.h>

namespace SwitchBridgeProtocol {

constexpr size_t kInputPacketSize = 16;
constexpr size_t kAckPacketSize = 8;

struct InputPacket {
    bool controllerConnected;
    uint8_t sequence;
    uint8_t b1;
    uint8_t b2;
    uint8_t b3;
    uint8_t lx;
    uint8_t ly;
    uint8_t rx;
    uint8_t ry;
    uint8_t l2;
    uint8_t r2;
};

enum class ParseResult {
    Ok,
    InvalidLength,
    InvalidHeader,
    InvalidCrc,
};

enum class SequenceRelation {
    Newer,
    Duplicate,
    OutOfOrder,
};

uint8_t crc8Atm(const uint8_t *data, size_t length);

ParseResult parseInputPacket(
    const uint8_t *bytes,
    size_t length,
    InputPacket &packet);

SequenceRelation compareSequence(uint8_t candidate, uint8_t previous);

bool buildAckPacket(
    uint8_t status,
    uint8_t lastSequence,
    uint8_t packetAgeMs,
    uint8_t *output,
    size_t outputLength);

}  // namespace SwitchBridgeProtocol

#endif
