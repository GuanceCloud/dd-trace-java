package datadog.trace.instrumentation.xxl_job_2_3x;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.api.Java8BytecodeBridge.spanFromScope;
import static datadog.trace.instrumentation.xxl_job_2_3x.JobConstants.*;
import static datadog.trace.instrumentation.xxl_job_2_3x.JobDecorator.DECORATOR;

import com.xxl.job.core.handler.IJobHandler;
import datadog.context.ContextScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import net.bytebuddy.asm.Advice;

public class SimpleJobAdvice {
  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static ContextScope execute(
      @Advice.This IJobHandler jobHandler, @Advice.FieldValue("jobId") int jobId) {
    String operationName = JobType.SIMPLE_JOB + "/id/" + jobId;
    AgentSpan span = DECORATOR.createSpan(operationName);
    span.setTag(JOB_TYPE, JobType.SIMPLE_JOB);
    span.setTag(JOB_ID, jobId);
    //    String jobParam = com.xxl.job.core.context.XxlJobHelper.getJobParam();
    //    if (!StringUtil.isNullOrEmpty(jobParam)){
    //      span.setTag(JOB_PARAM, jobParam);
    //    }
    ContextScope agentScope = activateSpan(span);
    return agentScope;
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void exit(
      @Advice.Enter final ContextScope scope, @Advice.Thrown final Throwable throwable) {
    if (scope == null) {
      return;
    }
    DECORATOR.error(scope, throwable);
    DECORATOR.beforeFinish(spanFromScope(scope));
    scope.close();
    spanFromScope(scope).finish();
  }
}
