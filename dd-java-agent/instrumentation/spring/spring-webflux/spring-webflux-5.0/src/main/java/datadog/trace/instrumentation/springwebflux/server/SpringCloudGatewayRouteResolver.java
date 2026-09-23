package datadog.trace.instrumentation.springwebflux.server;

import datadog.trace.api.GenericClassValue;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import org.springframework.web.server.ServerWebExchange;

public final class SpringCloudGatewayRouteResolver {
  private static final String ATTRIBUTE_PREFIX =
      "org.springframework.cloud.gateway.support.ServerWebExchangeUtils.";
  private static final String GATEWAY_ROUTE_ATTRIBUTE = ATTRIBUTE_PREFIX + "gatewayRoute";
  private static final String MATCHED_PATH_ATTRIBUTE =
      ATTRIBUTE_PREFIX + "gatewayPredicateMatchedPathAttr";
  private static final String MATCHED_PATH_ROUTE_ID_ATTRIBUTE =
      ATTRIBUTE_PREFIX + "gatewayPredicateMatchedPathRouteIdAttr";

  private static final ClassValue<RouteIdAccessor> ROUTE_ID_ACCESSORS =
      GenericClassValue.of(RouteIdAccessor::new);

  private SpringCloudGatewayRouteResolver() {}

  public static String resolve(ServerWebExchange exchange) {
    final Object matchedPath = exchange.getAttribute(MATCHED_PATH_ATTRIBUTE);
    final Object matchedPathRouteId = exchange.getAttribute(MATCHED_PATH_ROUTE_ID_ATTRIBUTE);
    final Object selectedRoute = exchange.getAttribute(GATEWAY_ROUTE_ATTRIBUTE);

    if (!(matchedPath instanceof String)
        || !(matchedPathRouteId instanceof String)
        || selectedRoute == null) {
      return null;
    }

    final String selectedRouteId =
        ROUTE_ID_ACCESSORS.get(selectedRoute.getClass()).getId(selectedRoute);
    return matchedPathRouteId.equals(selectedRouteId) ? (String) matchedPath : null;
  }

  static final class RouteIdAccessor {
    private final MethodHandle getId;

    RouteIdAccessor(Class<?> routeClass) {
      MethodHandle getId = null;
      try {
        getId =
            java.lang.invoke.MethodHandles.publicLookup()
                .findVirtual(routeClass, "getId", MethodType.methodType(String.class))
                .asType(MethodType.methodType(String.class, Object.class));
      } catch (Throwable ignored) {
      }
      this.getId = getId;
    }

    String getId(Object route) {
      if (getId == null) {
        return null;
      }
      try {
        return (String) getId.invokeExact(route);
      } catch (Throwable ignored) {
        return null;
      }
    }
  }
}
