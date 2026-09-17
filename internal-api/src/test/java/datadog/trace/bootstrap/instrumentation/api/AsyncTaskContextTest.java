package datadog.trace.bootstrap.instrumentation.api;

import static datadog.trace.bootstrap.instrumentation.api.AsyncTaskContext.NOT_ASYNC_TASK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import datadog.context.Context;
import datadog.context.ContextKey;
import datadog.context.ContextScope;
import org.junit.jupiter.api.Test;

class AsyncTaskContextTest {

  @Test
  void activatesAndRestoresNestedTaskMarkers() {
    assertEquals(NOT_ASYNC_TASK, AsyncTaskContext.submittingThreadId());

    try (ContextScope outer = AsyncTaskContext.activate(11L, null)) {
      assertEquals(11L, AsyncTaskContext.submittingThreadId());
      assertSame(Context.current(), outer.context());

      try (ContextScope inner = AsyncTaskContext.activate(22L, null)) {
        assertEquals(22L, AsyncTaskContext.submittingThreadId());
      }

      assertEquals(11L, AsyncTaskContext.submittingThreadId());
    }

    assertEquals(NOT_ASYNC_TASK, AsyncTaskContext.submittingThreadId());
  }

  @Test
  void delegatesContextAndClose() {
    Context context = Context.root().with(ContextKey.named("async-task-test"), "value");
    RecordingScope delegate = new RecordingScope(context);

    ContextScope scope = AsyncTaskContext.activate(11L, delegate);

    assertSame(context, scope.context());
    scope.close();
    assertEquals(1, delegate.closeCount);
    assertEquals(NOT_ASYNC_TASK, AsyncTaskContext.submittingThreadId());
  }

  @Test
  void leavesNonAsyncScopesUntouched() {
    RecordingScope delegate = new RecordingScope(Context.root());

    assertSame(delegate, AsyncTaskContext.activate(NOT_ASYNC_TASK, delegate));
    assertEquals(NOT_ASYNC_TASK, AsyncTaskContext.submittingThreadId());
  }

  private static final class RecordingScope implements ContextScope {
    private final Context context;
    private int closeCount;

    private RecordingScope(Context context) {
      this.context = context;
    }

    @Override
    public Context context() {
      return context;
    }

    @Override
    public void close() {
      closeCount++;
    }
  }
}
