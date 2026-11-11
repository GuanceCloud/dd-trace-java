package datadog.trace.instrumentation.mqtt;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import org.junit.jupiter.api.Test;
import org.tabletest.junit.TableTest;

class MqttBrokerInfoTest {
  @TableTest({
    "Scenario    | Input                                     | Uri               | Host    | Port",
    "tcp         | tcp://broker                              | tcp://broker:1883 | broker  | 1883",
    "tls         | ssl://broker                              | ssl://broker:8883 | broker  | 8883",
    "websocket   | wss://broker/mqtt?token=secret            | wss://broker:443  | broker  | 443 ",
    "credentials | tcp://user:secret@broker:2883/path?secret | tcp://broker:2883 | broker  | 2883",
    "ipv6        | tcp://[::1]:1883                          | tcp://[::1]:1883  | '[::1]' | 1883"
  })
  void recordsBrokerWithoutCredentials(String input, String uri, String host, int port) {
    AgentSpan span = mock(AgentSpan.class);
    MqttBrokerInfo.parse(input).decorate(span);
    verify(span).setTag("mqtt.serverURI", uri);
    verify(span).setTag("peer.hostname", host);
    verify(span).setTag("peer.port", port);
    verify(span).setTag("peer.service", host + ":" + port);
  }

  @Test
  void ignoresMalformedAndNonNetworkAddresses() {
    AgentSpan span = mock(AgentSpan.class);
    MqttBrokerInfo.parse("invalid address").decorate(span);
    MqttBrokerInfo.parse("local://broker_name").decorate(span);
    verifyNoInteractions(span);
  }
}
