package com.datadog.profiling.controller.pyroscope;

import datadog.trace.api.EndpointCheckpointer;
import datadog.trace.api.EndpointTracker;
import datadog.trace.api.config.ProfilingConfig;
import datadog.trace.bootstrap.config.provider.ConfigProvider;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;

public class PyroscopeEndpointCheckpointer implements EndpointCheckpointer {
  private final boolean isEndpointCollectionEnabled;

  private final PyroscopeProfiler profiler;

  public PyroscopeEndpointCheckpointer() {
    this(PyroscopeProfiler.getInstance(),ConfigProvider.getInstance());
  }

  public PyroscopeEndpointCheckpointer(PyroscopeProfiler profiler,ConfigProvider configProvider){
    this.profiler = profiler;
    this.isEndpointCollectionEnabled =
        configProvider.getBoolean(
            ProfilingConfig.PROFILING_ENDPOINT_COLLECTION_ENABLED,
            ProfilingConfig.PROFILING_ENDPOINT_COLLECTION_ENABLED_DEFAULT);
  }

  @Override
  public EndpointTracker onRootSpanStarted(AgentSpan rootSpan) {
//    return isEndpointCollectionEnabled
//        ? new EndpointEvent(rootSpan.getSpanId())
//        : EndpointTracker.NO_OP;
    if (isEndpointCollectionEnabled) {
      profiler.start(rootSpan);
    }
    return null;
  }

  @Override
  public void onRootSpanFinished(AgentSpan rootSpan, EndpointTracker tracker) {
    if (isEndpointCollectionEnabled && rootSpan != null) {
      CharSequence resourceName = rootSpan.getResourceName();
      if (resourceName != null) {
//        datadogProfiler.recordTraceRoot(rootSpan.getSpanId(), resourceName.toString());
      }
      //todo
      profiler.finish(rootSpan);
    }
  }


}
