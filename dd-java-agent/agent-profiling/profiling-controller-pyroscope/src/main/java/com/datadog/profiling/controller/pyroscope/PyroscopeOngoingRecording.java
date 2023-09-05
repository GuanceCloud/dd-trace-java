package com.datadog.profiling.controller.pyroscope;

import com.datadog.profiling.controller.OngoingRecording;
import com.datadog.profiling.controller.RecordingData;
import datadog.trace.api.profiling.ProfilingSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.time.Instant;

public class PyroscopeOngoingRecording implements OngoingRecording {
  private static final Logger log = LoggerFactory.getLogger(PyroscopeOngoingRecording.class);
  private final String name;
  private final PyroscopeProfiler profiler;
  public PyroscopeOngoingRecording(String name,PyroscopeProfiler profiler) {
    this.name = name;
    this.profiler = profiler;
  }

  @Nonnull
  @Override
  public RecordingData stop() {
    return null;
  }

  @Nonnull
  @Override
  public RecordingData snapshot(@Nonnull Instant start, ProfilingSnapshot.Kind kind) {
    log.debug("PyroscopeOngoingRecording: Taking recording snapshot for time range {} - {}", start, Instant.now());
    return new PyroscopeRecordingData(start, Instant.now(), kind,profiler);
//    log.debug("Taking recording snapshot for time range {} - {}", start, Instant.now());
//    ObjectName targetName = recordingId;
//    try {
//      if (!(boolean) helper.getRecordingAttribute(targetName, "Running")) {
//        throw new RuntimeException("Recording " + name + " is not active");
//      }
//
//      targetName = helper.cloneRecording(targetName);
//      return new OracleJdkRecordingData(
//          name, targetName, start, getEndTime(helper, targetName, Instant.now()), kind, helper);
//    } catch (IOException e) {
//      throw new RuntimeException("Unable to take snapshot for recording " + name, e);
//    }
  }

  @Override
  public void close() {

  }


}
