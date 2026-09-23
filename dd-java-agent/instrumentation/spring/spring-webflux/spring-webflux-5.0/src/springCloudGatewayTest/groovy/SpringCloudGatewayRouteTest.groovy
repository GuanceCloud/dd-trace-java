import datadog.trace.agent.test.InstrumentationSpecification
import datadog.trace.bootstrap.instrumentation.api.Tags
import dd.trace.instrumentation.springwebflux.gateway.SpringCloudGatewayTestApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient

@SpringBootTest(
webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
classes = SpringCloudGatewayTestApplication,
properties = [
  "spring.main.web-application-type=reactive",
  "spring.cloud.gateway.metrics.enabled=false"
])
class SpringCloudGatewayRouteTest extends InstrumentationSpecification {

  @LocalServerPort
  int port

  WebClient client = WebClient.builder().clientConnector(new ReactorClientHttpConnector()).build()

  def "uses the matched Spring Cloud Gateway path as the HTTP route"() {
    setup:
    String url = "http://localhost:$port/system/post/list"

    when:
    def response = client.get().uri(url).retrieve().toBodilessEntity().block()

    then:
    response.statusCode.value() == 200
    TEST_WRITER.waitForTraces(2)
    def serverTrace = TEST_WRITER.find { trace ->
      trace.any { span -> span.operationName.toString() == "netty.request" }
    }
    serverTrace != null
    serverTrace.size() == 2

    def serverSpan = serverTrace.find { span -> span.operationName.toString() == "netty.request" }
    def handlerSpan = serverTrace.find { span -> span.operationName.toString() != "netty.request" }
    serverSpan.resourceName.toString() == "GET /system/**"
    serverSpan.getTag(Tags.HTTP_ROUTE) == "/system/**"
    handlerSpan.parentId == serverSpan.spanId
  }
}
