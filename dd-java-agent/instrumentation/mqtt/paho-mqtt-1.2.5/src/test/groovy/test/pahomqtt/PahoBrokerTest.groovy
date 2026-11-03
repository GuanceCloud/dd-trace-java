package test.pahomqtt

import static java.util.concurrent.TimeUnit.SECONDS
import datadog.trace.agent.test.InstrumentationSpecification
import io.moquette.broker.Server
import io.moquette.broker.config.MemoryConfig
import java.util.concurrent.CompletableFuture
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import spock.lang.Shared

abstract class PahoBrokerTest extends InstrumentationSpecification {
  @Shared Server broker

  def setupSpec() {
    def properties = new Properties()
    properties.setProperty("host", "127.0.0.1")
    properties.setProperty("port", "0")
    properties.setProperty("websocket_port", "disabled")
    properties.setProperty("persistent_store", "")
    properties.setProperty("allow_anonymous", "true")
    properties.setProperty("netty.epoll", "false")
    properties.setProperty("netty.mqtt.message_size", "1048576")
    broker = new Server()
    broker.startServer(new MemoryConfig(properties), interceptHandlers())
  }

  def cleanupSpec() {
    broker?.stopServer()
    // Moquette 0.13 leaves this executor running in stopServer().
    def field = Server.getDeclaredField("interceptor")
    field.accessible = true
    def interceptor = field.get(broker)
    def stop = interceptor.class.getDeclaredMethod("stop")
    stop.accessible = true
    stop.invoke(interceptor)
  }

  protected List interceptHandlers() {
    return []
  }

  protected MqttClient client(String id, CompletableFuture<MqttMessage> received = null, Closure handler = null) {
    def client = new MqttClient("tcp://127.0.0.1:" + broker.port, id, new MemoryPersistence())
    client.setTimeToWait(10000)
    client.setCallback(new MqttCallback() {
        @Override void connectionLost(Throwable cause) {}
        @Override void deliveryComplete(IMqttDeliveryToken token) {}
        @Override void messageArrived(String topic, MqttMessage message) {
          try {
            handler?.call(message)
          } finally {
            received?.complete(message)
          }
        }
      })
    client.connect()
    return client
  }

  protected static void close(MqttClient client) {
    if (client != null) {
      if (client.connected) {
        client.disconnect()
      }
      client.close()
    }
  }
}
