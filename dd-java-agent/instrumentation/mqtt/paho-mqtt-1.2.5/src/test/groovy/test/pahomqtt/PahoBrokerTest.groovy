package test.pahomqtt

import static java.util.concurrent.TimeUnit.SECONDS
import test.mqtt.MqttBrokerTest
import java.util.concurrent.CompletableFuture
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

abstract class PahoBrokerTest extends MqttBrokerTest {
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
