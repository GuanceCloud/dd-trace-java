package dd.trace.instrumentation.springwebflux.gateway

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.cloud.gateway.filter.GlobalFilter
import org.springframework.cloud.gateway.route.RouteLocator
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import reactor.core.publisher.Mono

import java.nio.charset.StandardCharsets

@SpringBootApplication
class SpringCloudGatewayTestApplication {

  @Bean
  RouteLocator gatewayRoutes(RouteLocatorBuilder builder) {
    return builder.routes()
    .route("system-route", route -> route.path("/system/**")
    .filters(filters -> filters.stripPrefix(1))
    .uri("http://localhost:1"))
    .build()
  }

  @Bean
  GlobalFilter shortCircuitGatewayFilter() {
    return { exchange, chain ->
      byte[] body = "OK".getBytes(StandardCharsets.UTF_8)
      exchange.response.statusCode = HttpStatus.OK
      exchange.response.headers.contentType = MediaType.TEXT_PLAIN
      return exchange.response.writeWith(
      Mono.just(exchange.response.bufferFactory().wrap(body)))
    } as GlobalFilter
  }
}
