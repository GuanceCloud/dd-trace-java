package datadog.trace.instrumentation.fusesourcemqtt;

import static datadog.trace.instrumentation.fusesourcemqtt.FuseSourceMqttDecorator.PRODUCER;

import datadog.context.ContextScope;
import org.fusesource.mqtt.client.Callback;

/**
 * Activates the send span around the application's completion callback. Raw Callback types also
 * support the Java 1.4 uber JAR, whose interfaces have no type parameters.
 */
public final class PublishCallback implements Callback {
  private final Callback delegate;
  private final PublishState state;

  public PublishCallback(Callback delegate, PublishState state) {
    this.delegate = delegate;
    this.state = state;
  }

  @Override
  public void onSuccess(Object value) {
    try (ContextScope scope = state.activate()) {
      try {
        if (delegate != null) {
          delegate.onSuccess(value);
        }
      } catch (Throwable error) {
        PRODUCER.onError(state.span, error);
        throw error;
      }
    } finally {
      state.finish(null);
    }
  }

  @Override
  public void onFailure(Throwable error) {
    try (ContextScope scope = state.activate()) {
      try {
        if (delegate != null) {
          delegate.onFailure(error);
        }
      } finally {
        PRODUCER.onError(state.span, error);
      }
    } finally {
      state.finish(error);
    }
  }
}
