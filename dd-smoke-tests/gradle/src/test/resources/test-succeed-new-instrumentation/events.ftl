[ {
  "type" : "test_session_end",
  "version" : 1,
  "content" : {
    "test_session_id" : ${content_test_session_id},
    "service" : "test-gradle-service",
    "name" : "gradle.test_session",
    "resource" : ":",
    "start" : ${content_start},
    "duration" : ${content_duration},
    "error" : 0,
    "metrics" : {
      "process_id" : ${content_metrics_process_id},
      "test.itr.tests_skipping.count" : 1,
      "_dd.profiling.enabled" : 0,
      "_dd.trace_span_attribute_schema" : 0,
      "test.code_coverage.lines_pct" : 57
    },
    "meta" : {
      "_dd.p.tid" : ${content_meta__dd_p_tid},
      "os.architecture" : ${content_meta_os_architecture},
      "test.status" : "pass",
      "_dd.ci.itr.tests_skipped" : "true",
      "ci.workspace_path" : ${content_meta_ci_workspace_path},
      "language" : "jvm",
      "runtime.name" : ${content_meta_runtime_name},
      "os.platform" : ${content_meta_os_platform},
      "os.version" : ${content_meta_os_version},
      "library_version" : ${content_meta_library_version},
      "span.kind" : "test_session_end",
      "runtime.version" : ${content_meta_runtime_version},
      "runtime-id" : ${content_meta_runtime_id},
      "test.itr.tests_skipping.enabled" : "true",
      "test.type" : "test",
      "env" : "integration-test",
      "runtime.vendor" : ${content_meta_runtime_vendor},
      "component" : "gradle",
      "test.code_coverage.enabled" : "true",
      "test.toolchain" : ${content_meta_test_toolchain},
      "test.itr.tests_skipping.type" : "test",
      "test.command" : "gradle test",
      "test.framework_version" : "4.10",
      "test.framework" : "junit4"
    }
  }
}, {
  "type" : "test_module_end",
  "version" : 1,
  "content" : {
    "test_session_id" : ${content_test_session_id},
    "test_module_id" : ${content_test_module_id},
    "service" : "test-gradle-service",
    "name" : "gradle.test_module",
    "resource" : ":test",
    "start" : ${content_start_2},
    "duration" : ${content_duration_2},
    "error" : 0,
    "metrics" : {
      "test.itr.tests_skipping.count" : 1,
      "test.code_coverage.lines_pct" : 57
    },
    "meta" : {
      "_dd.p.tid" : ${content_meta__dd_p_tid_2},
      "os.architecture" : ${content_meta_os_architecture},
      "test.module" : ":test",
      "test.status" : "pass",
      "_dd.ci.itr.tests_skipped" : "true",
      "ci.workspace_path" : ${content_meta_ci_workspace_path},
      "runtime.name" : ${content_meta_runtime_name},
      "os.platform" : ${content_meta_os_platform},
      "os.version" : ${content_meta_os_version},
      "library_version" : ${content_meta_library_version},
      "span.kind" : "test_module_end",
      "runtime.version" : ${content_meta_runtime_version},
      "test.itr.tests_skipping.enabled" : "true",
      "test.type" : "test",
      "env" : "integration-test",
      "runtime.vendor" : ${content_meta_runtime_vendor},
      "component" : "gradle",
      "test.code_coverage.enabled" : "true",
      "test.itr.tests_skipping.type" : "test",
      "test.command" : "gradle test",
      "test.framework_version" : "4.10",
      "test.framework" : "junit4"
    }
  }
}, {
  "type" : "test_suite_end",
  "version" : 1,
  "content" : {
    "test_session_id" : ${content_test_session_id},
    "test_module_id" : ${content_test_module_id},
    "test_suite_id" : ${content_test_suite_id},
    "service" : "test-gradle-service",
    "name" : "junit.test_suite",
    "resource" : "datadog.smoke.TestSucceed",
    "start" : ${content_start_3},
    "duration" : ${content_duration_3},
    "error" : 0,
    "metrics" : {
      "process_id" : ${content_metrics_process_id_2},
      "_dd.profiling.enabled" : 0,
      "_dd.trace_span_attribute_schema" : 0
    },
    "meta" : {
      "_dd.p.tid" : ${content_meta__dd_p_tid_3},
      "os.architecture" : ${content_meta_os_architecture},
      "test.source.file" : "src/test/java/datadog/smoke/TestSucceed.java",
      "test.status" : "pass",
      "ci.workspace_path" : ${content_meta_ci_workspace_path},
      "language" : "jvm",
      "runtime.name" : ${content_meta_runtime_name},
      "os.platform" : ${content_meta_os_platform},
      "os.version" : ${content_meta_os_version},
      "library_version" : ${content_meta_library_version},
      "span.kind" : "test_suite_end",
      "test.suite" : "datadog.smoke.TestSucceed",
      "runtime.version" : ${content_meta_runtime_version},
      "runtime-id" : ${content_meta_runtime_id_2},
      "test.type" : "test",
      "env" : "integration-test",
      "runtime.vendor" : ${content_meta_runtime_vendor},
      "component" : "junit",
      "test.framework_version" : "4.10",
      "test.framework" : "junit4"
    }
  }
}, {
  "type" : "test",
  "version" : 2,
  "content" : {
    "trace_id" : ${content_trace_id},
    "span_id" : ${content_span_id},
    "parent_id" : ${content_parent_id},
    "test_session_id" : ${content_test_session_id},
    "test_module_id" : ${content_test_module_id},
    "test_suite_id" : ${content_test_suite_id},
    "service" : "test-gradle-service",
    "name" : "junit.test",
    "resource" : "datadog.smoke.TestSucceed.test_succeed",
    "start" : ${content_start_4},
    "duration" : ${content_duration_4},
    "error" : 0,
    "metrics" : {
      "process_id" : ${content_metrics_process_id_2},
      "_dd.profiling.enabled" : 0,
      "_dd.trace_span_attribute_schema" : 0,
      "test.source.end" : 12,
      "test.source.start" : 9
    },
    "meta" : {
      "_dd.p.tid" : ${content_meta__dd_p_tid_4},
      "os.architecture" : ${content_meta_os_architecture},
      "test.source.file" : "src/test/java/datadog/smoke/TestSucceed.java",
      "test.source.method" : "test_succeed()V",
      "test.status" : "pass",
      "ci.workspace_path" : ${content_meta_ci_workspace_path},
      "language" : "jvm",
      "runtime.name" : ${content_meta_runtime_name},
      "os.platform" : ${content_meta_os_platform},
      "os.version" : ${content_meta_os_version},
      "library_version" : ${content_meta_library_version},
      "test.name" : "test_succeed",
      "span.kind" : "test",
      "test.suite" : "datadog.smoke.TestSucceed",
      "runtime.version" : ${content_meta_runtime_version},
      "runtime-id" : ${content_meta_runtime_id_2},
      "test.type" : "test",
      "env" : "integration-test",
      "runtime.vendor" : ${content_meta_runtime_vendor},
      "component" : "junit",
      "test.framework_version" : "4.10",
      "test.framework" : "junit4"
    }
  }
}, {
  "type" : "test",
  "version" : 2,
  "content" : {
    "trace_id" : ${content_trace_id_2},
    "span_id" : ${content_span_id_2},
    "parent_id" : ${content_parent_id},
    "test_session_id" : ${content_test_session_id},
    "test_module_id" : ${content_test_module_id},
    "test_suite_id" : ${content_test_suite_id},
    "service" : "test-gradle-service",
    "name" : "junit.test",
    "resource" : "datadog.smoke.TestSucceed.test_to_skip_with_itr",
    "start" : ${content_start_5},
    "duration" : ${content_duration_5},
    "error" : 0,
    "metrics" : {
      "process_id" : ${content_metrics_process_id_2},
      "_dd.profiling.enabled" : 0,
      "_dd.trace_span_attribute_schema" : 0,
      "test.source.end" : 17,
      "test.source.start" : 14
    },
    "meta" : {
      "_dd.p.tid" : ${content_meta__dd_p_tid_5},
      "os.architecture" : ${content_meta_os_architecture},
      "test.source.file" : "src/test/java/datadog/smoke/TestSucceed.java",
      "test.source.method" : "test_to_skip_with_itr()V",
      "test.status" : "skip",
      "ci.workspace_path" : ${content_meta_ci_workspace_path},
      "language" : "jvm",
      "runtime.name" : ${content_meta_runtime_name},
      "os.platform" : ${content_meta_os_platform},
      "os.version" : ${content_meta_os_version},
      "library_version" : ${content_meta_library_version},
      "test.name" : "test_to_skip_with_itr",
      "span.kind" : "test",
      "test.suite" : "datadog.smoke.TestSucceed",
      "runtime.version" : ${content_meta_runtime_version},
      "runtime-id" : ${content_meta_runtime_id_2},
      "test.type" : "test",
      "test.skip_reason" : "Skipped by Datadog Intelligent Test Runner",
      "env" : "integration-test",
      "runtime.vendor" : ${content_meta_runtime_vendor},
      "component" : "junit",
      "test.skipped_by_itr" : "true",
      "test.framework_version" : "4.10",
      "test.framework" : "junit4"
    }
  }
} ]