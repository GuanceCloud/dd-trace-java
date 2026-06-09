package datadog.trace.instrumentation.lettuce5;

import datadog.trace.api.Config;
import datadog.trace.api.naming.SpanNaming;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.api.InternalSpanTypes;
import datadog.trace.bootstrap.instrumentation.api.Tags;
import datadog.trace.bootstrap.instrumentation.api.UTF8BytesString;
import datadog.trace.bootstrap.instrumentation.decorator.DBTypeProcessingDatabaseClientDecorator;
import io.lettuce.core.RedisURI;
import io.lettuce.core.protocol.CommandArgs;
import io.lettuce.core.protocol.RedisCommand;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

public class LettuceClientDecorator extends DBTypeProcessingDatabaseClientDecorator<RedisURI> {
  public static final CharSequence REDIS_CLIENT = UTF8BytesString.create("redis-client");
  public static final LettuceClientDecorator DECORATE = new LettuceClientDecorator();
  public static final CharSequence OPERATION_NAME =
      UTF8BytesString.create(SpanNaming.instance().namingSchema().cache().operation("redis"));
  private static final String SERVICE_NAME =
      SpanNaming.instance().namingSchema().cache().service("redis");

  public boolean RedisCommandRaw = Config.get().getRedisCommandArgs();

  @Override
  protected String[] instrumentationNames() {
    return new String[] {"lettuce"};
  }

  @Override
  protected String service() {
    return SERVICE_NAME;
  }

  @Override
  protected CharSequence component() {
    return REDIS_CLIENT;
  }

  @Override
  protected CharSequence spanType() {
    return InternalSpanTypes.REDIS;
  }

  @Override
  protected String dbType() {
    return "redis";
  }

  @Override
  protected String dbUser(final RedisURI connection) {
    return null;
  }

  @Override
  protected String dbInstance(final RedisURI connection) {
    return null;
  }

  @Override
  protected String dbHostname(RedisURI redisURI) {
    return redisURI.getHost();
  }

  @Override
  public AgentSpan onConnection(final AgentSpan span, final RedisURI connection) {
    if (connection != null) {
      setPeerPort(span, connection.getPort());

      span.setTag("db.redis.dbIndex", connection.getDatabase());
    }
    return super.onConnection(span, connection);
  }

  public AgentSpan onConnection(final AgentSpan span, final LettuceConnectionInfo connectionInfo) {
    if (connectionInfo == null) {
      return span;
    }

    if (!connectionInfo.isCapturePeerAddress()
        || Config.get().isPeerHostnameFromConfigEnabled()) {
      onConnection(span, connectionInfo.getRedisURI());
    }
    if (connectionInfo.isCapturePeerAddress() && Config.get().isPeerHostnameFromConfigEnabled()) {
      onPeerAddress(span, connectionInfo.getRemoteAddress());
    }
    return span;
  }

  private AgentSpan onPeerAddress(final AgentSpan span, final SocketAddress remoteAddress) {
    if (remoteAddress instanceof InetSocketAddress) {
      InetSocketAddress inetSocketAddress = (InetSocketAddress) remoteAddress;
      InetAddress address = inetSocketAddress.getAddress();
      if (address != null) {
        String ip = address.getHostAddress();
        if (address instanceof Inet4Address) {
          span.setTag(Tags.PEER_HOST_IPV4, ip);
        } else if (address instanceof Inet6Address) {
          span.setTag(Tags.PEER_HOST_IPV6, ip);
        }
      }
      setPeerPort(span, inetSocketAddress.getPort());
    }
    return span;
  }

  public AgentSpan onCommand(final AgentSpan span, final RedisCommand command) {
    if (command.getArgs() != null && RedisCommandRaw) {
      CommandArgs args = command.getArgs();
      span.setTag("redis.command.args", args.toString());
    }
    final String commandName = LettuceInstrumentationUtil.getCommandName(command);
    span.setResourceName(LettuceInstrumentationUtil.getCommandResourceName(commandName));
    return span;
  }

  public String resourceNameForConnection(final RedisURI redisURI) {
    return "CONNECT:"
        + redisURI.getHost()
        + ":"
        + redisURI.getPort()
        + "/"
        + redisURI.getDatabase();
  }
}
