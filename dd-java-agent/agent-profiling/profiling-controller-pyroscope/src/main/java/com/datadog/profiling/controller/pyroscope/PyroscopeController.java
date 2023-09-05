package com.datadog.profiling.controller.pyroscope;

import com.datadog.profiling.controller.Controller;
import com.datadog.profiling.controller.OngoingRecording;
import com.datadog.profiling.controller.UnsupportedEnvironmentException;
import datadog.trace.bootstrap.config.provider.ConfigProvider;

import javax.annotation.Nonnull;

public final class PyroscopeController implements Controller {

  private final PyroscopeProfiler profiler;

  public PyroscopeController(@Nonnull final ConfigProvider configProvider) {
    this.profiler = PyroscopeProfiler.newInstance();
  }


  @Nonnull
  @Override
  public OngoingRecording createRecording(@Nonnull String recordingName) throws UnsupportedEnvironmentException {
    return new PyroscopeOngoingRecording(recordingName,profiler);
  }
}
