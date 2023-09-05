package com.datadog.profiling.controller.pyroscope;

import com.datadog.profiling.controller.RecordingData;
import com.datadog.profiling.controller.RecordingInputStream;
import io.pyroscope.javaagent.Profiler;
import io.pyroscope.labels.Pyroscope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.time.Instant;

public class PyroscopeRecordingData extends RecordingData {
  private static final Logger log = LoggerFactory.getLogger(PyroscopeRecordingData.class);
  private final PyroscopeProfiler profiler;

  public PyroscopeRecordingData(Instant start, Instant end, Kind kind,PyroscopeProfiler profiler) {
    super(start, end, kind);
    this.profiler = profiler;
    this.setUpload(false);
  }

  @Nonnull
  @Override
  public RecordingInputStream getStream() throws IOException {
    // todo
//    return new RecordingInputStream(profiler.);
    log.info("开始上报profiler数据");
    return null;
  }

  @Override
  public void release() {

  }

  @Nonnull
  @Override
  public String getName() {
    return "PyroscopeRecordingData";
  }

}
