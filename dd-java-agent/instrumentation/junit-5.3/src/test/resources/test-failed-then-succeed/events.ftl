[ {
  "content" : {
    "duration" : ${content_duration},
    "error" : 0,
    "meta" : {
      "_dd.p.tid" : ${content_meta__dd_p_tid},
      "_dd.profiling.ctx" : "test",
      "_dd.tracer_host" : ${content_meta__dd_tracer_host},
      "component" : "junit",
      "dummy_ci_tag" : "dummy_ci_tag_value",
      "env" : "none",
      "language" : "jvm",
      "library_version" : ${content_meta_library_version},
      "runtime-id" : ${content_meta_runtime_id},
      "span.kind" : "test_session_end",
      "test.command" : "junit-5.3",
      "test.framework" : "junit5",
      "test.framework_version" : ${content_meta_test_framework_version},
      "test.status" : "fail",
      "test.type" : "test",
      "test_session.name" : "session-name"
    },
    "metrics" : {
      "_dd.host.vcpu_count" : ${content_metrics__dd_host_vcpu_count},
      "_dd.profiling.enabled" : 0,
      "_dd.trace_span_attribute_schema" : 0,
      "process_id" : ${content_metrics_process_id}
    },
    "name" : "junit.test_session",
    "resource" : "junit-5.3",
    "service" : "worker.org.gradle.process.internal.worker.gradleworkermain",
    "start" : ${content_start},
    "test_session_id" : ${content_test_session_id}
  },
  "type" : "test_session_end",
  "version" : 1
}, {
  "content" : {
    "duration" : ${content_duration_2},
    "error" : 0,
    "meta" : {
      "_dd.p.tid" : ${content_meta__dd_p_tid_2},
      "component" : "junit",
      "dummy_ci_tag" : "dummy_ci_tag_value",
      "env" : "none",
      "library_version" : ${content_meta_library_version},
      "span.kind" : "test_module_end",
      "test.framework" : "junit5",
      "test.framework_version" : ${content_meta_test_framework_version},
      "test.module" : "junit-5.3",
      "test.status" : "fail",
      "test.type" : "test",
      "test_session.name" : "session-name"
    },
    "metrics" : {
      "_dd.host.vcpu_count" : ${content_metrics__dd_host_vcpu_count_2}
    },
    "name" : "junit.test_module",
    "resource" : "junit-5.3",
    "service" : "worker.org.gradle.process.internal.worker.gradleworkermain",
    "start" : ${content_start_2},
    "test_module_id" : ${content_test_module_id},
    "test_session_id" : ${content_test_session_id}
  },
  "type" : "test_module_end",
  "version" : 1
}, {
  "content" : {
    "duration" : ${content_duration_3},
    "error" : 0,
    "meta" : {
      "_dd.p.tid" : ${content_meta__dd_p_tid_3},
      "component" : "junit",
      "dummy_ci_tag" : "dummy_ci_tag_value",
      "env" : "none",
      "library_version" : ${content_meta_library_version},
      "span.kind" : "test_suite_end",
      "test.codeowners" : "[\"owner1\",\"owner2\"]",
      "test.framework" : "junit5",
      "test.framework_version" : ${content_meta_test_framework_version},
      "test.module" : "junit-5.3",
      "test.source.file" : "dummy_source_path",
      "test.status" : "fail",
      "test.suite" : "org.example.TestFailedThenSucceed",
      "test.type" : "test",
      "test_session.name" : "session-name"
    },
    "metrics" : {
      "_dd.host.vcpu_count" : ${content_metrics__dd_host_vcpu_count_3},
      "test.source.end" : 19,
      "test.source.start" : 11
    },
    "name" : "junit.test_suite",
    "resource" : "org.example.TestFailedThenSucceed",
    "service" : "worker.org.gradle.process.internal.worker.gradleworkermain",
    "start" : ${content_start_3},
    "test_module_id" : ${content_test_module_id},
    "test_session_id" : ${content_test_session_id},
    "test_suite_id" : ${content_test_suite_id}
  },
  "type" : "test_suite_end",
  "version" : 1
}, {
  "content" : {
    "duration" : ${content_duration_4},
    "error" : 1,
    "meta" : {
      "_dd.profiling.ctx" : "test",
      "_dd.tracer_host" : ${content_meta__dd_tracer_host},
      "component" : "junit",
      "dummy_ci_tag" : "dummy_ci_tag_value",
      "env" : "none",
      "error.message" : ${content_meta_error_message},
      "error.stack" : ${content_meta_error_stack},
      "error.type" : "org.opentest4j.AssertionFailedError",
      "language" : "jvm",
      "library_version" : ${content_meta_library_version},
      "runtime-id" : ${content_meta_runtime_id},
      "span.kind" : "test",
      "test.codeowners" : "[\"owner1\",\"owner2\"]",
      "test.framework" : "junit5",
      "test.framework_version" : ${content_meta_test_framework_version},
      "test.module" : "junit-5.3",
      "test.name" : "test_failed_then_succeed",
      "test.source.file" : "dummy_source_path",
      "test.source.method" : "test_failed_then_succeed()V",
      "test.status" : "fail",
      "test.suite" : "org.example.TestFailedThenSucceed",
      "test.type" : "test",
      "test_session.name" : "session-name"
    },
    "metrics" : {
      "_dd.host.vcpu_count" : ${content_metrics__dd_host_vcpu_count_4},
      "_dd.profiling.enabled" : 0,
      "_dd.trace_span_attribute_schema" : 0,
      "process_id" : ${content_metrics_process_id},
      "test.source.end" : 18,
      "test.source.start" : 12
    },
    "name" : "junit.test",
    "parent_id" : ${content_parent_id},
    "resource" : "org.example.TestFailedThenSucceed.test_failed_then_succeed",
    "service" : "worker.org.gradle.process.internal.worker.gradleworkermain",
    "span_id" : ${content_span_id},
    "start" : ${content_start_4},
    "test_module_id" : ${content_test_module_id},
    "test_session_id" : ${content_test_session_id},
    "test_suite_id" : ${content_test_suite_id},
    "trace_id" : ${content_trace_id}
  },
  "type" : "test",
  "version" : 2
}, {
  "content" : {
    "duration" : ${content_duration_5},
    "error" : 1,
    "meta" : {
      "_dd.profiling.ctx" : "test",
      "_dd.tracer_host" : ${content_meta__dd_tracer_host},
      "component" : "junit",
      "dummy_ci_tag" : "dummy_ci_tag_value",
      "env" : "none",
      "error.message" : ${content_meta_error_message},
      "error.stack" : ${content_meta_error_stack_2},
      "error.type" : "org.opentest4j.AssertionFailedError",
      "language" : "jvm",
      "library_version" : ${content_meta_library_version},
      "runtime-id" : ${content_meta_runtime_id},
      "span.kind" : "test",
      "test.codeowners" : "[\"owner1\",\"owner2\"]",
      "test.framework" : "junit5",
      "test.framework_version" : ${content_meta_test_framework_version},
      "test.is_retry" : "true",
      "test.module" : "junit-5.3",
      "test.name" : "test_failed_then_succeed",
      "test.source.file" : "dummy_source_path",
      "test.source.method" : "test_failed_then_succeed()V",
      "test.status" : "fail",
      "test.suite" : "org.example.TestFailedThenSucceed",
      "test.type" : "test",
      "test_session.name" : "session-name"
    },
    "metrics" : {
      "_dd.host.vcpu_count" : ${content_metrics__dd_host_vcpu_count_5},
      "_dd.profiling.enabled" : 0,
      "_dd.trace_span_attribute_schema" : 0,
      "process_id" : ${content_metrics_process_id},
      "test.source.end" : 18,
      "test.source.start" : 12
    },
    "name" : "junit.test",
    "parent_id" : ${content_parent_id},
    "resource" : "org.example.TestFailedThenSucceed.test_failed_then_succeed",
    "service" : "worker.org.gradle.process.internal.worker.gradleworkermain",
    "span_id" : ${content_span_id_2},
    "start" : ${content_start_5},
    "test_module_id" : ${content_test_module_id},
    "test_session_id" : ${content_test_session_id},
    "test_suite_id" : ${content_test_suite_id},
    "trace_id" : ${content_trace_id_2}
  },
  "type" : "test",
  "version" : 2
}, {
  "content" : {
    "duration" : ${content_duration_6},
    "error" : 1,
    "meta" : {
      "_dd.profiling.ctx" : "test",
      "_dd.tracer_host" : ${content_meta__dd_tracer_host},
      "component" : "junit",
      "dummy_ci_tag" : "dummy_ci_tag_value",
      "env" : "none",
      "error.message" : ${content_meta_error_message},
      "error.stack" : ${content_meta_error_stack_3},
      "error.type" : "org.opentest4j.AssertionFailedError",
      "language" : "jvm",
      "library_version" : ${content_meta_library_version},
      "runtime-id" : ${content_meta_runtime_id},
      "span.kind" : "test",
      "test.codeowners" : "[\"owner1\",\"owner2\"]",
      "test.framework" : "junit5",
      "test.framework_version" : ${content_meta_test_framework_version},
      "test.is_retry" : "true",
      "test.module" : "junit-5.3",
      "test.name" : "test_failed_then_succeed",
      "test.source.file" : "dummy_source_path",
      "test.source.method" : "test_failed_then_succeed()V",
      "test.status" : "fail",
      "test.suite" : "org.example.TestFailedThenSucceed",
      "test.type" : "test",
      "test_session.name" : "session-name"
    },
    "metrics" : {
      "_dd.host.vcpu_count" : ${content_metrics__dd_host_vcpu_count_6},
      "_dd.profiling.enabled" : 0,
      "_dd.trace_span_attribute_schema" : 0,
      "process_id" : ${content_metrics_process_id},
      "test.source.end" : 18,
      "test.source.start" : 12
    },
    "name" : "junit.test",
    "parent_id" : ${content_parent_id},
    "resource" : "org.example.TestFailedThenSucceed.test_failed_then_succeed",
    "service" : "worker.org.gradle.process.internal.worker.gradleworkermain",
    "span_id" : ${content_span_id_3},
    "start" : ${content_start_6},
    "test_module_id" : ${content_test_module_id},
    "test_session_id" : ${content_test_session_id},
    "test_suite_id" : ${content_test_suite_id},
    "trace_id" : ${content_trace_id_3}
  },
  "type" : "test",
  "version" : 2
}, {
  "content" : {
    "duration" : ${content_duration_7},
    "error" : 0,
    "meta" : {
      "_dd.profiling.ctx" : "test",
      "_dd.tracer_host" : ${content_meta__dd_tracer_host},
      "component" : "junit",
      "dummy_ci_tag" : "dummy_ci_tag_value",
      "env" : "none",
      "language" : "jvm",
      "library_version" : ${content_meta_library_version},
      "runtime-id" : ${content_meta_runtime_id},
      "span.kind" : "test",
      "test.codeowners" : "[\"owner1\",\"owner2\"]",
      "test.framework" : "junit5",
      "test.framework_version" : ${content_meta_test_framework_version},
      "test.is_retry" : "true",
      "test.module" : "junit-5.3",
      "test.name" : "test_failed_then_succeed",
      "test.source.file" : "dummy_source_path",
      "test.source.method" : "test_failed_then_succeed()V",
      "test.status" : "pass",
      "test.suite" : "org.example.TestFailedThenSucceed",
      "test.type" : "test",
      "test_session.name" : "session-name"
    },
    "metrics" : {
      "_dd.host.vcpu_count" : ${content_metrics__dd_host_vcpu_count_7},
      "_dd.profiling.enabled" : 0,
      "_dd.trace_span_attribute_schema" : 0,
      "process_id" : ${content_metrics_process_id},
      "test.source.end" : 18,
      "test.source.start" : 12
    },
    "name" : "junit.test",
    "parent_id" : ${content_parent_id},
    "resource" : "org.example.TestFailedThenSucceed.test_failed_then_succeed",
    "service" : "worker.org.gradle.process.internal.worker.gradleworkermain",
    "span_id" : ${content_span_id_4},
    "start" : ${content_start_7},
    "test_module_id" : ${content_test_module_id},
    "test_session_id" : ${content_test_session_id},
    "test_suite_id" : ${content_test_suite_id},
    "trace_id" : ${content_trace_id_4}
  },
  "type" : "test",
  "version" : 2
}, {
  "content" : {
    "duration" : ${content_duration_8},
    "error" : 0,
    "meta" : {
      "env" : "none",
      "library_version" : ${content_meta_library_version}
    },
    "metrics" : { },
    "name" : "set_up",
    "parent_id" : ${content_span_id},
    "resource" : "set_up",
    "service" : "worker.org.gradle.process.internal.worker.gradleworkermain",
    "span_id" : ${content_span_id_5},
    "start" : ${content_start_8},
    "trace_id" : ${content_trace_id}
  },
  "type" : "span",
  "version" : 1
}, {
  "content" : {
    "duration" : ${content_duration_9},
    "error" : 0,
    "meta" : {
      "env" : "none",
      "library_version" : ${content_meta_library_version}
    },
    "metrics" : { },
    "name" : "set_up",
    "parent_id" : ${content_span_id_2},
    "resource" : "set_up",
    "service" : "worker.org.gradle.process.internal.worker.gradleworkermain",
    "span_id" : ${content_span_id_6},
    "start" : ${content_start_9},
    "trace_id" : ${content_trace_id_2}
  },
  "type" : "span",
  "version" : 1
}, {
  "content" : {
    "duration" : ${content_duration_10},
    "error" : 0,
    "meta" : {
      "env" : "none",
      "library_version" : ${content_meta_library_version}
    },
    "metrics" : { },
    "name" : "set_up",
    "parent_id" : ${content_span_id_3},
    "resource" : "set_up",
    "service" : "worker.org.gradle.process.internal.worker.gradleworkermain",
    "span_id" : ${content_span_id_7},
    "start" : ${content_start_10},
    "trace_id" : ${content_trace_id_3}
  },
  "type" : "span",
  "version" : 1
}, {
  "content" : {
    "duration" : ${content_duration_11},
    "error" : 0,
    "meta" : {
      "env" : "none",
      "library_version" : ${content_meta_library_version}
    },
    "metrics" : { },
    "name" : "set_up",
    "parent_id" : ${content_span_id_4},
    "resource" : "set_up",
    "service" : "worker.org.gradle.process.internal.worker.gradleworkermain",
    "span_id" : ${content_span_id_8},
    "start" : ${content_start_11},
    "trace_id" : ${content_trace_id_4}
  },
  "type" : "span",
  "version" : 1
}, {
  "content" : {
    "duration" : ${content_duration_12},
    "error" : 0,
    "meta" : {
      "env" : "none",
      "library_version" : ${content_meta_library_version}
    },
    "metrics" : { },
    "name" : "tear_down",
    "parent_id" : ${content_span_id},
    "resource" : "tear_down",
    "service" : "worker.org.gradle.process.internal.worker.gradleworkermain",
    "span_id" : ${content_span_id_9},
    "start" : ${content_start_12},
    "trace_id" : ${content_trace_id}
  },
  "type" : "span",
  "version" : 1
}, {
  "content" : {
    "duration" : ${content_duration_13},
    "error" : 0,
    "meta" : {
      "env" : "none",
      "library_version" : ${content_meta_library_version}
    },
    "metrics" : { },
    "name" : "tear_down",
    "parent_id" : ${content_span_id_2},
    "resource" : "tear_down",
    "service" : "worker.org.gradle.process.internal.worker.gradleworkermain",
    "span_id" : ${content_span_id_10},
    "start" : ${content_start_13},
    "trace_id" : ${content_trace_id_2}
  },
  "type" : "span",
  "version" : 1
}, {
  "content" : {
    "duration" : ${content_duration_14},
    "error" : 0,
    "meta" : {
      "env" : "none",
      "library_version" : ${content_meta_library_version}
    },
    "metrics" : { },
    "name" : "tear_down",
    "parent_id" : ${content_span_id_3},
    "resource" : "tear_down",
    "service" : "worker.org.gradle.process.internal.worker.gradleworkermain",
    "span_id" : ${content_span_id_11},
    "start" : ${content_start_14},
    "trace_id" : ${content_trace_id_3}
  },
  "type" : "span",
  "version" : 1
}, {
  "content" : {
    "duration" : ${content_duration_15},
    "error" : 0,
    "meta" : {
      "env" : "none",
      "library_version" : ${content_meta_library_version}
    },
    "metrics" : { },
    "name" : "tear_down",
    "parent_id" : ${content_span_id_4},
    "resource" : "tear_down",
    "service" : "worker.org.gradle.process.internal.worker.gradleworkermain",
    "span_id" : ${content_span_id_12},
    "start" : ${content_start_15},
    "trace_id" : ${content_trace_id_4}
  },
  "type" : "span",
  "version" : 1
} ]