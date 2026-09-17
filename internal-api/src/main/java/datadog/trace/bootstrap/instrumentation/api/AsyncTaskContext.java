package datadog.trace.bootstrap.instrumentation.api;

import datadog.context.Context;
import datadog.context.ContextScope;

/** Internal execution marker for work handed off to an asynchronous task executor. */
public final class AsyncTaskContext {
  public static final long NOT_ASYNC_TASK = -1L;

  private static final ThreadLocal<Long> SUBMITTING_THREAD_ID = new ThreadLocal<>();

  private AsyncTaskContext() {}

  /** Activates the task marker and closes the propagated context when the returned scope closes. */
  public static ContextScope activate(
      final long submittingThreadId, final ContextScope propagatedScope) {
    if (submittingThreadId == NOT_ASYNC_TASK) {
      return propagatedScope;
    }
    final Long previousThreadId = SUBMITTING_THREAD_ID.get();
    final Context context = propagatedScope == null ? Context.current() : propagatedScope.context();
    SUBMITTING_THREAD_ID.set(submittingThreadId);
    return new ContextScope() {
      @Override
      public Context context() {
        return context;
      }

      @Override
      public void close() {
        if (previousThreadId == null) {
          SUBMITTING_THREAD_ID.remove();
        } else {
          SUBMITTING_THREAD_ID.set(previousThreadId);
        }
        if (propagatedScope != null) {
          propagatedScope.close();
        }
      }
    };
  }

  /** Returns the submitting thread id, or {@link #NOT_ASYNC_TASK} outside an async task. */
  public static long submittingThreadId() {
    final Long threadId = SUBMITTING_THREAD_ID.get();
    return threadId == null ? NOT_ASYNC_TASK : threadId;
  }
}
