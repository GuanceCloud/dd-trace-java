[ {
  "type" : "test_suite_end",
  "version" : 1,
  "content" : {
    "test_session_id" : ${content_test_session_id},
    "test_module_id" : ${content_test_module_id},
    "test_suite_id" : ${content_test_suite_id},
    "service" : "worker.org.gradle.process.internal.worker.gradleworkermain",
    "name" : "testng.test_suite",
    "resource" : "org.example.TestError",
    "start" : ${content_start},
    "duration" : ${content_duration},
    "error" : 0,
    "metrics" : { },
    "meta" : {
      "test.type" : "test",
      "os.architecture" : ${content_meta_os_architecture},
      "test.source.file" : "dummy_source_path",
      "test.module" : "testng-6",
      "test.status" : "fail",
      "runtime.name" : ${content_meta_runtime_name},
      "runtime.vendor" : ${content_meta_runtime_vendor},
      "env" : "none",
      "os.platform" : ${content_meta_os_platform},
      "dummy_ci_tag" : "dummy_ci_tag_value",
      "os.version" : ${content_meta_os_version},
      "library_version" : ${content_meta_library_version},
      "component" : "testng",
      "span.kind" : "test_suite_end",
      "test.suite" : "org.example.TestError",
      "runtime.version" : ${content_meta_runtime_version},
      "test.framework_version" : ${content_meta_test_framework_version},
      "test.framework" : "testng"
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
    "service" : "worker.org.gradle.process.internal.worker.gradleworkermain",
    "name" : "testng.test",
    "resource" : "org.example.TestError.test_error",
    "start" : ${content_start_2},
    "duration" : ${content_duration_2},
    "error" : 1,
    "metrics" : {
      "process_id" : ${content_metrics_process_id},
      "_dd.profiling.enabled" : 0,
      "_dd.trace_span_attribute_schema" : 0,
      "test.source.end" : 18,
      "test.source.start" : 12
    },
    "meta" : {
      "os.architecture" : ${content_meta_os_architecture},
      "test.source.file" : "dummy_source_path",
      "test.source.method" : "test_error()V",
      "test.module" : "testng-6",
      "test.status" : "fail",
      "language" : "jvm",
      "runtime.name" : ${content_meta_runtime_name},
      "os.platform" : ${content_meta_os_platform},
      "test.codeowners" : "[\"owner1\",\"owner2\"]",
      "os.version" : ${content_meta_os_version},
      "library_version" : ${content_meta_library_version},
      "test.name" : "test_error",
      "span.kind" : "test",
      "test.suite" : "org.example.TestError",
      "runtime.version" : ${content_meta_runtime_version},
      "runtime-id" : ${content_meta_runtime_id},
      "test.type" : "test",
      "runtime.vendor" : ${content_meta_runtime_vendor},
      "env" : "none",
      "dummy_ci_tag" : "dummy_ci_tag_value",
      "component" : "testng",
      "error.type" : "java.lang.IllegalArgumentException",
      "_dd.profiling.ctx" : "test",
      "error.message" : ${content_meta_error_message},
      "error.stack" : ${content_meta_error_stack},
      "test.framework_version" : ${content_meta_test_framework_version},
      "test.framework" : "testng"
    }
  }
}, {
  "type" : "test_session_end",
  "version" : 1,
  "content" : {
    "test_session_id" : ${content_test_session_id},
    "service" : "worker.org.gradle.process.internal.worker.gradleworkermain",
    "name" : "testng.test_session",
    "resource" : "testng-6",
    "start" : ${content_start_3},
    "duration" : ${content_duration_3},
    "error" : 0,
    "metrics" : {
      "process_id" : ${content_metrics_process_id},
      "test.itr.tests_skipping.count" : 0,
      "_dd.profiling.enabled" : 0,
      "_dd.trace_span_attribute_schema" : 0
    },
    "meta" : {
      "os.architecture" : ${content_meta_os_architecture},
      "test.status" : "fail",
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
      "runtime.vendor" : ${content_meta_runtime_vendor},
      "env" : "none",
      "dummy_ci_tag" : "dummy_ci_tag_value",
      "component" : "testng",
      "_dd.profiling.ctx" : "test",
      "test.itr.tests_skipping.type" : "test",
      "test.command" : "testng-6",
      "test.framework_version" : ${content_meta_test_framework_version},
      "test.framework" : "testng"
    }
  }
}, {
  "type" : "test_module_end",
  "version" : 1,
  "content" : {
    "test_session_id" : ${content_test_session_id},
    "test_module_id" : ${content_test_module_id},
    "service" : "worker.org.gradle.process.internal.worker.gradleworkermain",
    "name" : "testng.test_module",
    "resource" : "testng-6",
    "start" : ${content_start_4},
    "duration" : ${content_duration_4},
    "error" : 0,
    "metrics" : {
      "test.itr.tests_skipping.count" : 0
    },
    "meta" : {
      "test.type" : "test",
      "os.architecture" : ${content_meta_os_architecture},
      "test.module" : "testng-6",
      "test.status" : "fail",
      "runtime.name" : ${content_meta_runtime_name},
      "runtime.vendor" : ${content_meta_runtime_vendor},
      "env" : "none",
      "os.platform" : ${content_meta_os_platform},
      "dummy_ci_tag" : "dummy_ci_tag_value",
      "os.version" : ${content_meta_os_version},
      "library_version" : ${content_meta_library_version},
      "component" : "testng",
      "span.kind" : "test_module_end",
      "test.itr.tests_skipping.type" : "test",
      "runtime.version" : ${content_meta_runtime_version},
      "test.framework_version" : ${content_meta_test_framework_version},
      "test.framework" : "testng",
      "test.itr.tests_skipping.enabled" : "true"
    }
  }
} ]