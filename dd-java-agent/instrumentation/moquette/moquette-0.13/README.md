# Moquette broker tracing

This module targets the synchronous MQTT 3.x broker API used by Moquette 0.13.
The 0.14 publication cannot be validated because its parent POM is unavailable.
The base tests run against the published 0.13 binary. Latest dependency tests
exercise the latest compatible release below 0.16. Moquette 0.16+ changes the
receive API to asynchronous routing and is excluded by a muzzle method reference.

Attach the Java agent to the Java application embedding Moquette. Instrumentation
is enabled by default; disable it with `-Ddd.integration.moquette.enabled=false`.

| Operation | Measured boundary |
| --- | --- |
| `moquette.receive` | `MQTTConnection.processPublish`: incoming PUBLISH validation and synchronous routing, including immediate delivery dispatch and scheduling intercept callbacks |
| `moquette.publish` | `PostOffice.internalPublish`: routing a message published by the embedding application, inheriting its active span |
| `moquette.deliver` | `MQTTConnection.sendIfWritableElseDrop`, for PUBLISH packets only: dispatch to the Netty channel |
| `moquette.on_publish` | Application `InterceptHandler.onPublish` execution, including nested business calls |

Resource names include both the operation and topic (for example,
`moquette.receive devices/events` and `moquette.deliver devices/events`) so
resource-based trace views distinguish the broker stages. These are broker spans,
not spans from external producer or consumer processes.

Spans include topic, client ID where available, QoS, retained flag, duplicate flag,
and `messaging.system=mqtt`. Payloads are never added to span tags or tracing logs; usernames are not collected.
Thrown exceptions are recorded and scopes close on both successful and exceptional
returns. Intercept callback overrides calling `super.onPublish` produce one callback
span. Executor context instrumentation connects asynchronous intercept callbacks
to the receive span.

## Timing and propagation limits

Delivery spans measure channel dispatch, not network write completion or the
subscriber's QoS acknowledgment. A receive span does not wait for asynchronous
application callbacks; those have their own child spans. Retransmitted packets
are individual delivery attempts and carry their duplicate flag.

MQTT 3.x has no User Properties carrier. Default tracing leaves payloads unchanged
and incoming messages start independent traces. With the explicit
`-Ddd.trace.mqtt.payload.propagation.enabled=true` option, adapted Paho MQTT 3
producers and consumers can join the broker trace using a binary payload envelope.
The broker forwards its receive context and removes the envelope from application
interception callbacks. Payload bytes are copied only for opt-in framing.

Every producer and recipient must support the envelope format. See
[the Paho MQTT 3 integration](../../mqtt/paho-mqtt-1.2.5/README.md) for configuration,
wire compatibility, retained-message behavior, and framing limits. Retained replay
and asynchronous delivery use stored context when available; ordinary unframed
messages retain the default behavior. MQTT 5 propagation requires a separate
integration for a broker version supporting MQTT 5.

## Verification

Run the `muzzle`, `test`, and `latestDepTest` tasks for
`:dd-java-agent:instrumentation:moquette:moquette-0.13`. The legacy 0.13 artifact
is resolved from a narrowly scoped JCenter mirror because it is absent from Maven
Central and the original Bintray endpoint has retired.
