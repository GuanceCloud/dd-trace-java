package datadog.trace.instrumentation.jdbc;

import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.CallDepthThreadLocalMap;
import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.jdbc.DBInfo;
import datadog.trace.bootstrap.instrumentation.jdbc.DBQueryInfo;
import net.bytebuddy.asm.Advice;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.nameStartsWith;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.jdbc.JDBCDecorator.*;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public abstract class AbstractPreparedStatementInstrumentation extends Instrumenter.Tracing
    implements Instrumenter.ForBootstrap {

  public AbstractPreparedStatementInstrumentation(
      String instrumentationName, String... additionalNames) {
    super(instrumentationName, additionalNames);
  }

  @Override
  public String[] helperClassNames() {
    return new String[]{
        packageName + ".JDBCDecorator",
    };
  }

  @Override
  public Map<String, String> contextStore() {
    Map<String, String> contextStore = new HashMap<>(4);
    contextStore.put("java.sql.Statement", DBQueryInfo.class.getName());
    contextStore.put("java.sql.Connection", DBInfo.class.getName());
    return contextStore;
  }

  @Override
  public void adviceTransformations(AdviceTransformation transformation) {
    transformation.applyAdvice(
        nameStartsWith("execute").and(takesArguments(0)).and(isPublic()),
        AbstractPreparedStatementInstrumentation.class.getName() + "$PreparedStatementAdvice");
    transformation.applyAdvice(
        nameStartsWith("setString").and(takesArguments(2)).and(isPublic()),
        AbstractPreparedStatementInstrumentation.class.getName() + "$SetStringAdvice");
  }

  public static class SetStringAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void StartSetString(
        @Advice.Argument(0) final int index, @Advice.Argument(1) final String arg,
        @Advice.This final PreparedStatement statement) {
      System.out.println("-------------into-----------------");
      System.out.println("--------------SetStringAdvice----------------");
      System.out.println("--------------" + index + "----------------");
      System.out.println("--------------" + arg + "----------------");
      System.out.println("set args to context");
//      int depth = CallDepthThreadLocalMap.incrementCallDepth(Statement.class);
//      if (depth > 0) {
//        return;
//      }
      try {
        ContextStore<Statement, DBQueryInfo> contextStore = InstrumentationContext.get(Statement.class, DBQueryInfo.class);
        if (contextStore == null) {
          System.out.println("------------------(contextStore == null)--------------------------------");
          return;
        }

        DBQueryInfo queryInfo = contextStore.get(statement);
        if (null == queryInfo) {
          logMissingQueryInfo(statement);
          System.out.println("------------------MissingQueryInfo--------------------------------");
          return;
        }
        queryInfo.setVal(index, arg.toString());
        contextStore.put(statement, queryInfo);
        System.out.println("----------------------------put---------out-------------");
      } catch (SQLException e) {
        System.out.println("----------------------------put error----------------------" + e);
        return;
      }

    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown final Throwable throwable) {
      System.out.println("-------------into--------OnMethodExit---------");
    }
  }

  public static class PreparedStatementAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AgentScope onEnter(@Advice.This final Statement statement) {
      int depth = CallDepthThreadLocalMap.incrementCallDepth(Statement.class);
      if (depth > 0) {
        return null;
      }
      try {
        Connection connection = statement.getConnection();
        DBQueryInfo queryInfo =
            InstrumentationContext.get(Statement.class, DBQueryInfo.class).get(statement);
        if (null == queryInfo) {
          logMissingQueryInfo(statement);
          return null;
        }

        final AgentSpan span = startSpan(DATABASE_QUERY);
        DECORATE.afterStart(span);
        DECORATE.onConnection(
            span, connection, InstrumentationContext.get(Connection.class, DBInfo.class));
        DECORATE.onPreparedStatement(span, queryInfo);
        return activateSpan(span);
      } catch (SQLException e) {
        logSQLException(e);
        // if we can't get the connection for any reason
        return null;
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final AgentScope scope, @Advice.Thrown final Throwable throwable) {
      if (scope == null) {
        return;
      }
      DECORATE.onError(scope.span(), throwable);
      DECORATE.beforeFinish(scope.span());
      scope.close();
      scope.span().finish();
      CallDepthThreadLocalMap.reset(Statement.class);
    }
  }
}
