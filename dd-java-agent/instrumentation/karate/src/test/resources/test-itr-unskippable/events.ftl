[ {
  "content" : {
    "duration" : ${content_duration},
    "error" : 0,
    "meta" : {
      "component" : "karate",
      "env" : "none",
      "library_version" : ${content_meta_library_version},
      "step.name" : "* print 'first'"
    },
    "metrics" : {
      "step.endLine" : 7,
      "step.startLine" : 7
    },
    "name" : "karate.step",
    "parent_id" : ${content_parent_id},
    "resource" : "* print 'first'",
    "service" : "worker.org.gradle.process.internal.worker.gradleworkermain",
    "span_id" : ${content_span_id},
    "start" : ${content_start},
    "trace_id" : ${content_trace_id}
  },
  "type" : "span",
  "version" : 1
}, {
  "content" : {
    "duration" : ${content_duration_2},
    "error" : 0,
    "meta" : {
      "component" : "karate",
      "env" : "none",
      "library_version" : ${content_meta_library_version},
      "step.name" : "* print 'second'"
    },
    "metrics" : {
      "step.endLine" : 10,
      "step.startLine" : 10
    },
    "name" : "karate.step",
    "parent_id" : ${content_parent_id_2},
    "resource" : "* print 'second'",
    "service" : "worker.org.gradle.process.internal.worker.gradleworkermain",
    "span_id" : ${content_span_id_2},
    "start" : ${content_start_2},
    "trace_id" : ${content_trace_id_2}
  },
  "type" : "span",
  "version" : 1
}, {
  "content" : {
    "duration" : ${content_duration_3},
    "error" : 0,
    "meta" : {
      "_dd.p.tid" : ${content_meta__dd_p_tid},
      "component" : "karate",
      "dummy_ci_tag" : "dummy_ci_tag_value",
      "env" : "none",
      "library_version" : ${content_meta_library_version},
      "span.kind" : "test_suite_end",
      "test.framework" : "karate",
      "test.framework_version" : ${content_meta_test_framework_version},
      "test.module" : "karate",
      "test.status" : "pass",
      "test.suite" : "[org/example/test_unskippable] test unskippable",
      "test.traits" : "{\"category\":[\"foo\"]}",
      "test.type" : "test",
      "test_session.name" : "session-name"
    },
    "metrics" : {
      "_dd.host.vcpu_count" : ${content_metrics__dd_host_vcpu_count}
    },
    "name" : "karate.test_suite",
    "resource" : "[org/example/test_unskippable] test unskippable",
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
    "error" : 0,
    "itr_correlation_id" : "itrCorrelationId",
    "meta" : {
      "_dd.profiling.ctx" : "test",
      "_dd.tracer_host" : ${content_meta__dd_tracer_host},
      "component" : "karate",
      "dummy_ci_tag" : "dummy_ci_tag_value",
      "env" : "none",
      "language" : "jvm",
      "library_version" : ${content_meta_library_version},
      "runtime-id" : ${content_meta_runtime_id},
      "span.kind" : "test",
      "test.framework" : "karate",
      "test.framework_version" : ${content_meta_test_framework_version},
      "test.itr.forced_run" : "true",
      "test.itr.unskippable" : "true",
      "test.module" : "karate",
      "test.name" : "first scenario",
      "test.status" : "pass",
      "test.suite" : "[org/example/test_unskippable] test unskippable",
      "test.traits" : "{\"category\":[\"bar\",\"datadog_itr_unskippable\",\"foo\"]}",
      "test.type" : "test",
      "test_session.name" : "session-name"
    },
    "metrics" : {
      "_dd.host.vcpu_count" : ${content_metrics__dd_host_vcpu_count_2},
      "_dd.profiling.enabled" : 0,
      "_dd.trace_span_attribute_schema" : 0,
      "process_id" : ${content_metrics_process_id}
    },
    "name" : "karate.test",
    "parent_id" : ${content_parent_id_3},
    "resource" : "[org/example/test_unskippable] test unskippable.first scenario",
    "service" : "worker.org.gradle.process.internal.worker.gradleworkermain",
    "span_id" : ${content_parent_id},
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
    "error" : 0,
    "itr_correlation_id" : "itrCorrelationId",
    "meta" : {
      "_dd.profiling.ctx" : "test",
      "_dd.tracer_host" : ${content_meta__dd_tracer_host},
      "component" : "karate",
      "dummy_ci_tag" : "dummy_ci_tag_value",
      "env" : "none",
      "language" : "jvm",
      "library_version" : ${content_meta_library_version},
      "runtime-id" : ${content_meta_runtime_id},
      "span.kind" : "test",
      "test.framework" : "karate",
      "test.framework_version" : ${content_meta_test_framework_version},
      "test.module" : "karate",
      "test.name" : "second scenario",
      "test.status" : "pass",
      "test.suite" : "[org/example/test_unskippable] test unskippable",
      "test.traits" : "{\"category\":[\"foo\"]}",
      "test.type" : "test",
      "test_session.name" : "session-name"
    },
    "metrics" : {
      "_dd.host.vcpu_count" : ${content_metrics__dd_host_vcpu_count_3},
      "_dd.profiling.enabled" : 0,
      "_dd.trace_span_attribute_schema" : 0,
      "process_id" : ${content_metrics_process_id}
    },
    "name" : "karate.test",
    "parent_id" : ${content_parent_id_3},
    "resource" : "[org/example/test_unskippable] test unskippable.second scenario",
    "service" : "worker.org.gradle.process.internal.worker.gradleworkermain",
    "span_id" : ${content_parent_id_2},
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
    "error" : 0,
    "meta" : {
      "_dd.p.tid" : ${content_meta__dd_p_tid_2},
      "_dd.profiling.ctx" : "test",
      "_dd.tracer_host" : ${content_meta__dd_tracer_host},
      "component" : "karate",
      "dummy_ci_tag" : "dummy_ci_tag_value",
      "env" : "none",
      "language" : "jvm",
      "library_version" : ${content_meta_library_version},
      "runtime-id" : ${content_meta_runtime_id},
      "span.kind" : "test_session_end",
      "test.command" : "karate",
      "test.framework" : "karate",
      "test.framework_version" : ${content_meta_test_framework_version},
      "test.itr.tests_skipping.enabled" : "true",
      "test.itr.tests_skipping.type" : "test",
      "test.status" : "pass",
      "test.type" : "test",
      "test_session.name" : "session-name"
    },
    "metrics" : {
      "_dd.host.vcpu_count" : ${content_metrics__dd_host_vcpu_count_4},
      "_dd.profiling.enabled" : 0,
      "_dd.trace_span_attribute_schema" : 0,
      "process_id" : ${content_metrics_process_id},
      "test.itr.tests_skipping.count" : 0
    },
    "name" : "karate.test_session",
    "resource" : "karate",
    "service" : "worker.org.gradle.process.internal.worker.gradleworkermain",
    "start" : ${content_start_6},
    "test_session_id" : ${content_test_session_id}
  },
  "type" : "test_session_end",
  "version" : 1
}, {
  "content" : {
    "duration" : ${content_duration_7},
    "error" : 0,
    "meta" : {
      "_dd.p.tid" : ${content_meta__dd_p_tid_3},
      "component" : "karate",
      "dummy_ci_tag" : "dummy_ci_tag_value",
      "env" : "none",
      "library_version" : ${content_meta_library_version},
      "span.kind" : "test_module_end",
      "test.framework" : "karate",
      "test.framework_version" : ${content_meta_test_framework_version},
      "test.itr.tests_skipping.enabled" : "true",
      "test.itr.tests_skipping.type" : "test",
      "test.module" : "karate",
      "test.status" : "pass",
      "test.type" : "test",
      "test_session.name" : "session-name"
    },
    "metrics" : {
      "_dd.host.vcpu_count" : ${content_metrics__dd_host_vcpu_count_5},
      "test.itr.tests_skipping.count" : 0
    },
    "name" : "karate.test_module",
    "resource" : "karate",
    "service" : "worker.org.gradle.process.internal.worker.gradleworkermain",
    "start" : ${content_start_7},
    "test_module_id" : ${content_test_module_id},
    "test_session_id" : ${content_test_session_id}
  },
  "type" : "test_module_end",
  "version" : 1
} ]