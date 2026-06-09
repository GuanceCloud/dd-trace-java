package datadog.trace.instrumentation.vertx_redis_client;

import datadog.trace.bootstrap.InstrumentationContext;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisConnection;
import io.vertx.redis.client.RedisOptions;
import net.bytebuddy.asm.Advice;

public class RedisConnectionConstructAdvice {
  @Advice.OnMethodExit(suppress = Throwable.class)
  public static void afterConstructor(
      @Advice.Argument(3) final NetSocket netSocket,
      @Advice.Argument(4) final RedisOptions options,
      @Advice.This final RedisConnection thiz) {
    final SocketAddress socketAddress = netSocket != null ? netSocket.remoteAddress() : null;
    final String configuredHost = RedisOptionsHostParser.configuredHost(options);
    if (socketAddress != null || (configuredHost != null && !configuredHost.isEmpty())) {
      InstrumentationContext.get(RedisConnection.class, VertxRedisConnectionInfo.class)
          .put(thiz, new VertxRedisConnectionInfo(socketAddress, configuredHost));
    }
  }

  // Only apply this advice for versions that we instrument 3.9.x
  private static void muzzleCheck() {
    Redis.createClient(null, "somehost"); // added in 3.9.x
  }
}
