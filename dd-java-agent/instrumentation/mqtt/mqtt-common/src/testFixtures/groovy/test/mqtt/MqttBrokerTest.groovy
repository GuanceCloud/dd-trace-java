package test.mqtt

import datadog.trace.agent.test.InstrumentationSpecification
import datadog.trace.bootstrap.instrumentation.api.Tags
import io.moquette.broker.Server
import io.moquette.broker.config.MemoryConfig
import spock.lang.Shared

/** Embedded broker shared by the MQTT client instrumentation suites. */
abstract class MqttBrokerTest extends InstrumentationSpecification {
  @Shared Server broker

  def setupSpec() {
    def properties = new Properties()
    properties.setProperty('host', '127.0.0.1')
    properties.setProperty('port', '0')
    properties.setProperty('websocket_port', 'disabled')
    properties.setProperty('persistent_store', '')
    properties.setProperty('allow_anonymous', 'true')
    properties.setProperty('netty.epoll', 'false')
    properties.setProperty('netty.mqtt.message_size', '1048576')
    broker = new Server()
    broker.startServer(new MemoryConfig(properties), interceptHandlers())
  }

  def cleanupSpec() {
    broker?.stopServer()
    // Moquette 0.13 leaves its interception executor running after stopServer().
    def field = Server.getDeclaredField('interceptor')
    field.accessible = true
    def interceptor = field.get(broker)
    def stop = interceptor.class.getDeclaredMethod('stop')
    stop.accessible = true
    stop.invoke(interceptor)
  }
  protected List interceptHandlers() {
    return []
  }

  protected void mqttSpan(def trace, String operation, String component, String topic, String clientId, int qos, def parentSpan = null, Throwable failure = null) {
    trace.span {
      operationName operation
      resourceName operation + ' ' + topic
      serviceName datadog.trace.api.Config.get().serviceName
      spanType 'queue'
      if (parentSpan == null) {
        parent()
      } else {
        childOf parentSpan
      }
      errored (failure != null)
      tags {
        defaultTagsWithExplicitPeerService()
        "$Tags.COMPONENT" component
        "$Tags.SPAN_KIND" (operation == 'mqtt.publish' ? Tags.SPAN_KIND_PRODUCER : Tags.SPAN_KIND_CONSUMER)
        'messaging.system' 'mqtt'
        'messaging.destination.name' topic
        'mqtt.topic' topic
        'mqtt.clientid' clientId
        'mqtt.serverURI' ('tcp://127.0.0.1:' + broker.port)
        'peer.hostname' '127.0.0.1'
        'peer.port' broker.port
        'peer.service' ('127.0.0.1:' + broker.port)
        'mqtt.message.qos' qos
        'mqtt.message.retained' false
        if (component == 'fusesource-mqtt') {
          'mqtt.message.duplicate' false
        }
        if (failure != null) {
          errorTags(failure.class, failure.message)
        }
      }
    }
  }
}
