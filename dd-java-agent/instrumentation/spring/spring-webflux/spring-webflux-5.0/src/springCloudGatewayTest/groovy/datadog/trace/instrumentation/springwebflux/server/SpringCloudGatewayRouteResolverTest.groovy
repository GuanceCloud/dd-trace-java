package datadog.trace.instrumentation.springwebflux.server

import org.springframework.cloud.gateway.route.Route
import org.springframework.cloud.gateway.handler.AsyncPredicate
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Mono
import spock.lang.Specification

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_PREDICATE_MATCHED_PATH_ATTR
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_PREDICATE_MATCHED_PATH_ROUTE_ID_ATTR
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR

class SpringCloudGatewayRouteResolverTest extends Specification {

  def "resolves only a path matched by the selected route"() {
    setup:
    def exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/system/post/list"))
    def routeBuilder = Route.async()
    routeBuilder.id(selectedRouteId)
    routeBuilder.uri("http://localhost")
    routeBuilder.asyncPredicate({ ignored -> Mono.just(true) } as AsyncPredicate)
    def route = routeBuilder.build()
    exchange.attributes.put(GATEWAY_ROUTE_ATTR, route)
    exchange.attributes.put(GATEWAY_PREDICATE_MATCHED_PATH_ROUTE_ID_ATTR, matchedRouteId)
    if (matchedPath != null) {
      exchange.attributes.put(GATEWAY_PREDICATE_MATCHED_PATH_ATTR, matchedPath)
    }

    expect:
    SpringCloudGatewayRouteResolver.resolve(exchange) == expectedRoute

    where:
    selectedRouteId | matchedRouteId | matchedPath  || expectedRoute
    "selected"      | "selected"     | "/system/**" || "/system/**"
    "selected"      | "candidate"    | "/stale/**"  || null
    "selected"      | "selected"     | null          || null
  }
}
