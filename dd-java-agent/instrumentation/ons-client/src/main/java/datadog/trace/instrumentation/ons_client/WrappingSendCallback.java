package datadog.trace.instrumentation.ons_client;

import static datadog.trace.bootstrap.instrumentation.api.Java8BytecodeBridge.spanFromScope;

import com.aliyun.openservices.ons.api.OnExceptionContext;
import com.aliyun.openservices.ons.api.SendCallback;
import com.aliyun.openservices.ons.api.SendResult;
import datadog.context.ContextScope;

public class WrappingSendCallback implements SendCallback {
  private final SendCallback callback;
  private final ContextScope scope;

  public WrappingSendCallback(SendCallback callback, ContextScope scope) {
    this.callback = callback;
    this.scope = scope;
  }

  @Override
  public void onSuccess(SendResult sendResult) {
    spanFromScope(scope).setTag("sendAsync_status", "success");
    scope.close();
    spanFromScope(scope).finish();
    callback.onSuccess(sendResult);
  }

  @Override
  public void onException(OnExceptionContext context) {
    spanFromScope(scope).setTag("sendAsync_status", "exception");
    spanFromScope(scope).addThrowable(context.getException());
    scope.close();
    spanFromScope(scope).finish();
    callback.onException(context);
  }
}
