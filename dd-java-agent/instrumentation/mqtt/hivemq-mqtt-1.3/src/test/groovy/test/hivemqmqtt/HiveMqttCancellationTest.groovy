package test.hivemqmqtt

import static datadog.trace.agent.test.utils.TraceUtils.basicSpan
import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace
import static java.util.concurrent.TimeUnit.SECONDS

import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish
import datadog.trace.agent.test.InstrumentationSpecification
import io.reactivex.Flowable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class HiveMqttCancellationTest extends InstrumentationSpecification {
  def 'cancelling a publish waiting for broker acknowledgement finishes its span'() {
    setup:
    def server = new ServerSocket(0)
    def receivedPublish = new CompletableFuture<Void>()
    def stop = new CountDownLatch(1)
    def executor = Executors.newSingleThreadExecutor()
    // The broker accepts CONNECT and deliberately withholds PUBACK.
    def serverTask = executor.submit {
      server.accept().withCloseable { socket ->
        def input = new DataInputStream(socket.inputStream)
        assert readPacket(input) == 1
        socket.outputStream.write([0x20, 2, 0, 0] as byte[])
        socket.outputStream.flush()
        assert readPacket(input) == 3
        receivedPublish.complete(null)
        assert stop.await(10, SECONDS)
      }
    }
    def client = Mqtt3Client.builder().identifier('cancel-producer').serverHost('127.0.0.1').serverPort(server.localPort).buildAsync()
    client.connect().get(10, SECONDS)
    def disposable

    when:
    runUnderTrace('request') {
      disposable = client.toRx().publish(Flowable.just(Mqtt3Publish.builder()
        .topic('test/cancel').qos(MqttQos.AT_LEAST_ONCE).payload([1, 2] as byte[]).build())).subscribe()
    }
    receivedPublish.get(10, SECONDS)

    then:
    TEST_WRITER.size() == 0

    when:
    disposable.dispose()

    then:
    assertTraces(1) {
      trace(2) {
        sortSpansByStart()
        basicSpan(it, 'request')
        span {
          operationName 'mqtt.publish'
          resourceName 'mqtt.publish test/cancel'
          spanType 'queue'
          childOf span(0)
          errored false
          tags {
            defaultTagsWithExplicitPeerService()
            'component' 'hivemq-mqtt'
            'span.kind' 'producer'
            'messaging.system' 'mqtt'
            'messaging.destination.name' 'test/cancel'
            'mqtt.topic' 'test/cancel'
            'mqtt.clientid' 'cancel-producer'
            'mqtt.serverURI' ('tcp://127.0.0.1:' + server.localPort)
            'peer.hostname' '127.0.0.1'
            'peer.port' server.localPort
            'peer.service' ('127.0.0.1:' + server.localPort)
            'mqtt.message.qos' 1
            'mqtt.message.retained' false
          }
        }
      }
    }

    cleanup:
    disposable?.dispose()
    stop.countDown()
    server.close()
    serverTask.get(10, SECONDS)
    executor.shutdownNow()
    try {
      client?.disconnect()?.get(10, SECONDS)
    } catch (java.util.concurrent.ExecutionException ignored) {}
  }

  private static int readPacket(DataInputStream input) {
    int header = input.readUnsignedByte()
    int length = 0
    int multiplier = 1
    int digit
    do {
      digit = input.readUnsignedByte()
      length += (digit & 127) * multiplier
      multiplier *= 128
    } while ((digit & 128) != 0)
      input.readFully(new byte[length])
    return header >> 4
  }
}
