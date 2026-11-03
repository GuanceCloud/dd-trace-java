# MQTT payload frame version 1

The opt-in MQTT 3 transport frame is shared by the Paho client and Moquette broker
integrations. This is a wire-format change and is disabled by default.

All integer fields use network byte order.

| Field | Format |
| --- | --- |
| Magic and version | `44 44 4d 51 54 54 54 52 41 43 45 00 01 00 0d 0a` |
| Header byte length | unsigned 32-bit length, at most 8192 |
| Header count | unsigned 16-bit count, 1–64 |
| Each header | unsigned 16-bit key length, UTF-8 key, unsigned 16-bit value length, UTF-8 value |
| Business payload | remaining bytes, including an empty body |

Keys are nonempty and at most 256 bytes. Values are nonempty. The header count and
all key/value length fields are included in the 8192-byte metadata limit. Duplicate
keys, truncated fields, invalid lengths, and unknown versions are not decoded.

Client encoding always treats application bytes as opaque, even when those bytes
happen to form a valid frame. Broker reframing replaces only the outer transport
headers and retains the inner business bytes. Buffer positions and caller-owned
payload arrays are not modified.

The header map carries the configured trace propagator's fields. The codec does
not log headers or payloads. Its parser bounds metadata allocations independently
of the business payload size.
