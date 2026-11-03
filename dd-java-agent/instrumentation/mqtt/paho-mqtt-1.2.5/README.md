# Paho MQTT 3 tracing

This integration targets Eclipse Paho `org.eclipse.paho.client.mqttv3` 1.2.5+.
It covers `MqttClient` and `MqttAsyncClient` publishing, global `MqttCallback`
handlers, and subscription-specific `IMqttMessageListener` handlers.

Producer spans (`mqtt.publish`) run until Paho processes delivery completion,
including the application's delivery/action callbacks. Consumer spans
(`mqtt.consume`) cover delivery to application callbacks. User action listeners,
tokens, message properties, and caller-owned payload arrays are preserved.

Each process keeps its configured service name. Resource names contain the action
and Topic, so a trace view can distinguish publishing, broker receive/delivery,
and consumption.

## Opt-in producer → broker → consumer propagation

MQTT 3 has no User Properties carrier. Propagation therefore requires an explicit
payload framing contract, disabled by default. Enable it on the producer, broker,
and consumer JVMs using the same agent build:

```shell
-Ddd.trace.mqtt.payload.propagation.enabled=true
-Ddd.trace.agent.url=http://127.0.0.1:9529
```

Equivalent environment variable: `DD_TRACE_MQTT_PAYLOAD_PROPAGATION_ENABLED=true`.
Use distinct `dd.service` values for the producer, broker, and consumer.

The producer wraps the business bytes and trace propagation headers. The Moquette
0.13 integration extracts the producer context and forwards a frame containing
the broker receive context. The consumer extracts that context, then delivers the
original business bytes to the application. Broker interception callbacks also
see the original payload. Frames are opaque binary data; no JSON parsing or
application-specific payload schema is required.

All recipients must support this framing contract. An uninstrumented consumer
receives the wire envelope. Keep the option disabled when peers have not been
adapted. Configure Moquette's `netty.mqtt.message_size` to leave room for the frame
metadata in addition to the application's maximum payload.

The broker receive span is the parent of both immediate delivery dispatch and
remote consumer processing. Dispatch duration does not include the consumer's
processing or QoS acknowledgment duration. Retained and queued messages preserve
the serialized context; delayed spans can arrive after the original trace.

An empty retained publish is sent without a frame so it still deletes the retained
message. That deletion cannot propagate trace context through its empty payload.
Ordinary non-retained empty messages can carry a frame.

Framing metadata is capped at 64 fields and 8 KiB. If metadata cannot be encoded
within these bounds, the original message is sent without injected context.
Malformed or unknown frames are treated as ordinary payloads. Payload contents
are not added to span tags or tracing logs.

## Verification

Run `test`, `latestDepTest`, `payloadPropagationTest`, and `muzzle` for
`:dd-java-agent:instrumentation:mqtt:paho-mqtt-1.2.5`.

- Base tests exercise both public client APIs, global/subscription callbacks,
  asynchronous completion, errors, and unchanged payloads with framing disabled.
- Payload propagation tests exercise producer/broker/consumer parent relationships,
  QoS 0/1/2, binary payloads, broker observers, retained replay/deletion, and literal
  business data that itself resembles an envelope.
- The shared codec has separate unit tests under `mqtt-common`.
