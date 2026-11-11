# MQTT client tracing

The Guance agent automatically records MQTT 3 publishing and consumption for:

| Client | Tested baseline | Additional coverage |
| --- | --- | --- |
| Eclipse Paho `org.eclipse.paho.client.mqttv3` | 1.2.5 | Latest dependency tests |
| FuseSource `org.fusesource.mqtt-client:mqtt-client` | 1.12 | 1.16 and the Java 1.4 uber JAR |
| HiveMQ `com.hivemq:hivemq-mqtt-client` | 1.3.0 | Latest 1.x dependency tests |

Publishing creates a `mqtt.publish` producer span under the active application
span. Consumption creates a separate trace rooted at `mqtt.consume`. Each span
keeps the application's service name and records the broker in `peer.service`,
`peer.hostname`, and `peer.port`. `messaging.system=mqtt`, the topic, client ID,
QoS, and retained flag identify the operation. Broker credentials and URI query
parameters are not reported. These fields describe client-side dependencies;
broker internals and the console's rendering are separate concerns.

FuseSource covers callback, future, and blocking publishing, and delivery to its
connection listener. Callbacks invoked during future completion run under the
publish span without replacing the application's callback registration. For blocking receives,
the consumer span describes delivery to the client's receive buffer, rather than
the subsequent application processing.

HiveMQ covers blocking and asynchronous publishing and each subscribed reactive
publish. Spans end on completion, error, or reactive cancellation. Asynchronous
consumer callbacks are traced on their actual executor thread. Reactive delivery
preserves demand, cancellation, and the subscription acknowledgement signal.
Blocking consumption records a span only when a receive returns a message; it
does not keep a scope open after returning. Operators added by the application
that schedule further work require their usual context propagation support.
HiveMQ MQTT 5 operations are not instrumented by this integration.

Disable either new integration with `DD_TRACE_FUSESOURCE_MQTT_ENABLED=false` or
`DD_TRACE_HIVEMQ_MQTT_ENABLED=false`.

Neither new integration changes payloads or transmits trace context through MQTT.
Paho's existing optional payload propagation remains disabled by default; see its
[separate documentation](paho-mqtt-1.2.5/README.md) if explicitly required.

Run `muzzle`, `test`, and `latestDepTest` for both new modules, and `uberTest` and
`uberLatestDepTest` for FuseSource. Shared broker identity tests live in
`mqtt-common`. Paho's normal and payload-propagation suites verify the shared
metadata regression paths.
