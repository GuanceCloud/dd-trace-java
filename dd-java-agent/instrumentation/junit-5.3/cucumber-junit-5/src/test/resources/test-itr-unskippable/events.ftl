[ {
  "content" : {
    "duration" : ${content_duration},
    "error" : 0,
    "meta" : {
      "_dd.p.tid" : ${content_meta__dd_p_tid},
      "component" : "junit",
      "dummy_ci_tag" : "dummy_ci_tag_value",
      "env" : "none",
      "library_version" : ${content_meta_library_version},
      "span.kind" : "test_suite_end",
      "test.framework" : "cucumber",
      "test.framework_version" : ${content_meta_test_framework_version},
      "test.module" : "cucumber-junit-5",
      "test.status" : "pass",
      "test.suite" : "classpath:org/example/cucumber/calculator/basic_arithmetic_unskippable.feature:Basic Arithmetic",
      "test.type" : "test",
      "test_session.name" : "session-name"
    },
    "metrics" : {
      "_dd.host.vcpu_count" : ${content_metrics__dd_host_vcpu_count}
    },
    "name" : "junit.test_suite",
    "resource" : "classpath:org/example/cucumber/calculator/basic_arithmetic_unskippable.feature:Basic Arithmetic",
    "service" : "worker.org.gradle.process.internal.worker.gradleworkermain",
    "start" : ${content_start},
    "test_module_id" : ${content_test_module_id},
    "test_session_id" : ${content_test_session_id},
    "test_suite_id" : ${content_test_suite_id}
  },
  "type" : "test_suite_end",
  "version" : 1
}, {
  "content" : {
    "duration" : ${content_duration_2},
    "error" : 0,
    "itr_correlation_id" : "itrCorrelationId",
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
      "test.framework" : "cucumber",
      "test.framework_version" : ${content_meta_test_framework_version},
      "test.itr.forced_run" : "true",
      "test.itr.unskippable" : "true",
      "test.module" : "cucumber-junit-5",
      "test.name" : "Addition",
      "test.status" : "pass",
      "test.suite" : "classpath:org/example/cucumber/calculator/basic_arithmetic_unskippable.feature:Basic Arithmetic",
      "test.traits" : "{\"category\":[\"datadog_itr_unskippable\"]}",
      "test.type" : "test",
      "test_session.name" : "session-name"
    },
    "metrics" : {
      "_dd.host.vcpu_count" : ${content_metrics__dd_host_vcpu_count_2},
      "_dd.profiling.enabled" : 0,
      "_dd.trace_span_attribute_schema" : 0,
      "process_id" : ${content_metrics_process_id}
    },
    "name" : "junit.test",
    "parent_id" : ${content_parent_id},
    "resource" : "classpath:org/example/cucumber/calculator/basic_arithmetic_unskippable.feature:Basic Arithmetic.Addition",
    "service" : "worker.org.gradle.process.internal.worker.gradleworkermain",
    "span_id" : ${content_span_id},
    "start" : ${content_start_2},
    "test_module_id" : ${content_test_module_id},
    "test_session_id" : ${content_test_session_id},
    "test_suite_id" : ${content_test_suite_id},
    "trace_id" : ${content_trace_id}
  },
  "type" : "test",
  "version" : 2
}, {
  "content" : {
    "duration" : ${content_duration_3},
    "error" : 0,
    "meta" : {
      "_dd.p.tid" : ${content_meta__dd_p_tid_2},
      "_dd.profiling.ctx" : "test",
      "_dd.tracer_host" : ${content_meta__dd_tracer_host},
      "component" : "junit",
      "dummy_ci_tag" : "dummy_ci_tag_value",
      "env" : "none",
      "language" : "jvm",
      "library_version" : ${content_meta_library_version},
      "runtime-id" : ${content_meta_runtime_id},
      "span.kind" : "test_session_end",
      "test.command" : "cucumber-junit-5",
      "test.framework" : "cucumber",
      "test.framework_version" : ${content_meta_test_framework_version},
      "test.itr.tests_skipping.enabled" : "true",
      "test.itr.tests_skipping.type" : "test",
      "test.status" : "pass",
      "test.type" : "test",
      "test_session.name" : "session-name"
    },
    "metrics" : {
      "_dd.host.vcpu_count" : ${content_metrics__dd_host_vcpu_count_3},
      "_dd.profiling.enabled" : 0,
      "_dd.trace_span_attribute_schema" : 0,
      "process_id" : ${content_metrics_process_id},
      "test.itr.tests_skipping.count" : 0
    },
    "name" : "junit.test_session",
    "resource" : "cucumber-junit-5",
    "service" : "worker.org.gradle.process.internal.worker.gradleworkermain",
    "start" : ${content_start_3},
    "test_session_id" : ${content_test_session_id}
  },
  "type" : "test_session_end",
  "version" : 1
}, {
  "content" : {
    "duration" : ${content_duration_4},
    "error" : 0,
    "meta" : {
      "_dd.p.tid" : ${content_meta__dd_p_tid_3},
      "component" : "junit",
      "dummy_ci_tag" : "dummy_ci_tag_value",
      "env" : "none",
      "library_version" : ${content_meta_library_version},
      "span.kind" : "test_module_end",
      "test.framework" : "cucumber",
      "test.framework_version" : ${content_meta_test_framework_version},
      "test.itr.tests_skipping.enabled" : "true",
      "test.itr.tests_skipping.type" : "test",
      "test.module" : "cucumber-junit-5",
      "test.status" : "pass",
      "test.type" : "test",
      "test_session.name" : "session-name"
    },
    "metrics" : {
      "_dd.host.vcpu_count" : ${content_metrics__dd_host_vcpu_count_4},
      "test.itr.tests_skipping.count" : 0
    },
    "name" : "junit.test_module",
    "resource" : "cucumber-junit-5",
    "service" : "worker.org.gradle.process.internal.worker.gradleworkermain",
    "start" : ${content_start_4},
    "test_module_id" : ${content_test_module_id},
    "test_session_id" : ${content_test_session_id}
  },
  "type" : "test_module_end",
  "version" : 1
} ]