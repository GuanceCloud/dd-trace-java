import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.instrumentation.jdbc.SQLCommenter

class SQLCommenterTest extends AgentTestRunner {

  def "test encode Sql Comment"() {
    setup:
    injectSysConfig("dd.service", ddService)
    injectSysConfig("dd.env", ddEnv)
    injectSysConfig("dd.version", ddVersion)

    when:
    String sqlWithComment = ""
    if (injectTrace) {
      sqlWithComment = SQLCommenter.inject(query, dbService, traceParent, true, appendComment)
    } else {
      if (appendComment) {
        sqlWithComment = SQLCommenter.append(query, dbService)
      } else {
        sqlWithComment = SQLCommenter.prepend(query, dbService)
      }
    }

    sqlWithComment == expected

    then:
    sqlWithComment == expected

    where:
    query                                                                                         | ddService      | ddEnv  | dbService    | ddVersion     | injectTrace | appendComment                             | traceParent                                               | expected
    "SELECT * FROM foo"                                                                           | "SqlCommenter" | "Test" | "my-service" | "TestVersion" | true        | true  | "00-00000000000000007fffffffffffffff-000000024cb016ea-00" | "SELECT * FROM foo /*ddps='SqlCommenter',dddbs='my-service',dde='Test',ddpv='TestVersion',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-00'*/"
    "{call dogshelterProc(?, ?)}"                                                                 | "SqlCommenter" | "Test" | "my-service" | "TestVersion" | true        | true  | "00-00000000000000007fffffffffffffff-000000024cb016ea-00" | "{call dogshelterProc(?, ?)} /*ddps='SqlCommenter',dddbs='my-service',dde='Test',ddpv='TestVersion',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-00'*/"
    "SELECT * FROM foo"                                                                           | ""             | "Test" | ""           | "TestVersion" | true        | true  | "00-00000000000000007fffffffffffffff-000000024cb016ea-00" | "SELECT * FROM foo /*dde='Test',ddpv='TestVersion',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-00'*/"
    "SELECT * FROM foo"                                                                           | ""             | "Test" | "my-service" | "TestVersion" | true        | true  | "00-00000000000000007fffffffffffffff-000000024cb016ea-00" | "SELECT * FROM foo /*dddbs='my-service',dde='Test',ddpv='TestVersion',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-00'*/"
    "SELECT * FROM foo"                                                                           | ""             | "Test" | ""           | ""            | true        | true  | "00-00000000000000007fffffffffffffff-000000024cb016ea-00" | "SELECT * FROM foo /*dde='Test',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-00'*/"
    "SELECT * FROM foo"                                                                           | ""             | ""     | ""           | ""            | true        | true  | "00-00000000000000007fffffffffffffff-000000024cb016ea-00" | "SELECT * FROM foo /*traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-00'*/"
    "SELECT * from FOO -- test query"                                                             | "SqlCommenter" | "Test" | "my-service" | "TestVersion" | true        | true  | "00-00000000000000007fffffffffffffff-000000024cb016ea-01" | "SELECT * from FOO -- test query /*ddps='SqlCommenter',dddbs='my-service',dde='Test',ddpv='TestVersion',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-01'*/"
    "SELECT /* customer-comment */ * FROM foo"                                                    | "SqlCommenter" | "Test" | "my-service" | "TestVersion" | true        | true  | "00-00000000000000007fffffffffffffff-000000024cb016ea-01" | "SELECT /* customer-comment */ * FROM foo /*ddps='SqlCommenter',dddbs='my-service',dde='Test',ddpv='TestVersion',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-01'*/"
    "SELECT * FROM foo"                                                                           | "SqlCommenter" | "Test" | "my-service" | "TestVersion" | false       | true  | null                                                      | "SELECT * FROM foo /*ddps='SqlCommenter',dddbs='my-service',dde='Test',ddpv='TestVersion'*/"
    "SELECT /* customer-comment */ * FROM foo"                                                    | "SqlCommenter" | "Test" | "my-service" | "TestVersion" | false       | true  | null                                                      | "SELECT /* customer-comment */ * FROM foo /*ddps='SqlCommenter',dddbs='my-service',dde='Test',ddpv='TestVersion'*/"
    "SELECT * from FOO -- test query"                                                             | "SqlCommenter" | "Test" | "my-service" | "TestVersion" | false       | true  | null                                                      | "SELECT * from FOO -- test query /*ddps='SqlCommenter',dddbs='my-service',dde='Test',ddpv='TestVersion'*/"
    ""                                                                                            | "SqlCommenter" | "Test" | "my-service" | "TestVersion" | true        | true  | "00-00000000000000007fffffffffffffff-000000024cb016ea-00" | ""
    "   "                                                                                         | "SqlCommenter" | "Test" | "my-service" | "TestVersion" | true        | true  | "00-00000000000000007fffffffffffffff-000000024cb016ea-01" | "    /*ddps='SqlCommenter',dddbs='my-service',dde='Test',ddpv='TestVersion',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-01'*/"
    "SELECT * FROM foo /*dddbs='my-service',dde='Test',ddps='SqlCommenter',ddpv='TestVersion'*/"  | "SqlCommenter" | "Test" | "my-service" | "TestVersion" | false       | true  | null                                                      | "SELECT * FROM foo /*dddbs='my-service',dde='Test',ddps='SqlCommenter',ddpv='TestVersion'*/"
    "SELECT * FROM foo /*dde='Test',ddps='SqlCommenter',ddpv='TestVersion'*/"                     | "SqlCommenter" | "Test" | "my-service" | "TestVersion" | false       | true  | null                                                      | "SELECT * FROM foo /*dde='Test',ddps='SqlCommenter',ddpv='TestVersion'*/"
    "SELECT * FROM foo /*ddps='SqlCommenter',ddpv='TestVersion'*/"                                | "SqlCommenter" | "Test" | "my-service" | "TestVersion" | false       | true  | null                                                      | "SELECT * FROM foo /*ddps='SqlCommenter',ddpv='TestVersion'*/"
    "SELECT * FROM foo /*ddpv='TestVersion'*/"                                                    | "SqlCommenter" | "Test" | "my-service" | "TestVersion" | false       | true  | null                                                      | "SELECT * FROM foo /*ddpv='TestVersion'*/"
    "/*ddjk its a customer */ SELECT * FROM foo"                                                  | "SqlCommenter" | "Test" | "my-service" | "TestVersion" | false       | true  | null                                                      | "/*ddjk its a customer */ SELECT * FROM foo /*ddps='SqlCommenter',dddbs='my-service',dde='Test',ddpv='TestVersion'*/"
    "SELECT * FROM foo /*traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-01'*/" | "SqlCommenter" | "Test" | "my-service" | "TestVersion" | false       | true  | null                                                      | "SELECT * FROM foo /*traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-01'*/"
    "/*customer-comment*/ SELECT * FROM foo"                                                      | "SqlCommenter" | "Test" | "my-service" | "TestVersion" | false       | true  | null                                                      | "/*customer-comment*/ SELECT * FROM foo /*ddps='SqlCommenter',dddbs='my-service',dde='Test',ddpv='TestVersion'*/"
    "/*traceparent"                                                                               | "SqlCommenter" | "Test" | "my-service" | "TestVersion" | false       | true  | null                                                      | "/*traceparent /*ddps='SqlCommenter',dddbs='my-service',dde='Test',ddpv='TestVersion'*/"
    "SELECT * FROM foo"                                                                           | "SqlCommenter" | "Test" | "my-service" | "TestVersion" | true        | false | "00-00000000000000007fffffffffffffff-000000024cb016ea-00" | "/*ddps='SqlCommenter',dddbs='my-service',dde='Test',ddpv='TestVersion',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-00'*/ SELECT * FROM foo"
    "SELECT * FROM foo"                                                                           | ""             | "Test" | ""           | "TestVersion" | true        | false | "00-00000000000000007fffffffffffffff-000000024cb016ea-00" | "/*dde='Test',ddpv='TestVersion',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-00'*/ SELECT * FROM foo"
    "SELECT * FROM foo"                                                                           | ""             | "Test" | "my-service" | "TestVersion" | true        | false | "00-00000000000000007fffffffffffffff-000000024cb016ea-00" | "/*dddbs='my-service',dde='Test',ddpv='TestVersion',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-00'*/ SELECT * FROM foo"
    "SELECT * FROM foo"                                                                           | ""             | "Test" | ""           | ""            | true        | false | "00-00000000000000007fffffffffffffff-000000024cb016ea-00" | "/*dde='Test',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-00'*/ SELECT * FROM foo"
    "SELECT * FROM foo"                                                                           | ""             | ""     | ""           | ""            | true        | false | "00-00000000000000007fffffffffffffff-000000024cb016ea-00" | "/*traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-00'*/ SELECT * FROM foo"
    "SELECT * from FOO -- test query"                                                             | "SqlCommenter" | "Test" | "my-service" | "TestVersion" | true        | false | "00-00000000000000007fffffffffffffff-000000024cb016ea-01" | "/*ddps='SqlCommenter',dddbs='my-service',dde='Test',ddpv='TestVersion',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-01'*/ SELECT * from FOO -- test query"
    "SELECT /* customer-comment */ * FROM foo"                                                    | "SqlCommenter" | "Test" | "my-service" | "TestVersion" | true        | false | "00-00000000000000007fffffffffffffff-000000024cb016ea-01" | "/*ddps='SqlCommenter',dddbs='my-service',dde='Test',ddpv='TestVersion',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-01'*/ SELECT /* customer-comment */ * FROM foo"
    "SELECT * FROM foo"                                                                           | "SqlCommenter" | "Test" | "my-service" | "TestVersion" | false       | false | null                                                      | "/*ddps='SqlCommenter',dddbs='my-service',dde='Test',ddpv='TestVersion'*/ SELECT * FROM foo"
    "SELECT /* customer-comment */ * FROM foo"                                                    | "SqlCommenter" | "Test" | "my-service" | "TestVersion" | false       | false | null                                                      | "/*ddps='SqlCommenter',dddbs='my-service',dde='Test',ddpv='TestVersion'*/ SELECT /* customer-comment */ * FROM foo"
    "SELECT * from FOO -- test query"                                                             | "SqlCommenter" | "Test" | "my-service" | "TestVersion" | false       | false | null                                                      | "/*ddps='SqlCommenter',dddbs='my-service',dde='Test',ddpv='TestVersion'*/ SELECT * from FOO -- test query"
    ""                                                                                            | "SqlCommenter" | "Test" | "my-service" | "TestVersion" | true        | false | "00-00000000000000007fffffffffffffff-000000024cb016ea-00" | ""
    "   "                                                                                         | "SqlCommenter" | "Test" | "my-service" | "TestVersion" | true        | false | "00-00000000000000007fffffffffffffff-000000024cb016ea-01" | "/*ddps='SqlCommenter',dddbs='my-service',dde='Test',ddpv='TestVersion',traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-01'*/    "
    "/*dddbs='my-service',dde='Test',ddps='SqlCommenter',ddpv='TestVersion'*/ SELECT * FROM foo"  | "SqlCommenter" | "Test" | "my-service" | "TestVersion" | false       | false | null                                                      | "/*dddbs='my-service',dde='Test',ddps='SqlCommenter',ddpv='TestVersion'*/ SELECT * FROM foo"
    "/*dde='Test',ddps='SqlCommenter',ddpv='TestVersion'*/ SELECT * FROM foo"                     | "SqlCommenter" | "Test" | "my-service" | "TestVersion" | false       | false | null                                                      | "/*dde='Test',ddps='SqlCommenter',ddpv='TestVersion'*/ SELECT * FROM foo"
    "/*ddps='SqlCommenter',ddpv='TestVersion'*/ SELECT * FROM foo"                                | "SqlCommenter" | "Test" | "my-service" | "TestVersion" | false       | false | null                                                      | "/*ddps='SqlCommenter',ddpv='TestVersion'*/ SELECT * FROM foo"
    "/*ddpv='TestVersion'*/ SELECT * FROM foo"                                                    | "SqlCommenter" | "Test" | "my-service" | "TestVersion" | false       | false | null                                                      | "/*ddpv='TestVersion'*/ SELECT * FROM foo"
    "/*ddjk its a customer */ SELECT * FROM foo"                                                  | "SqlCommenter" | "Test" | "my-service" | "TestVersion" | false       | false | null                                                      | "/*ddps='SqlCommenter',dddbs='my-service',dde='Test',ddpv='TestVersion'*/ /*ddjk its a customer */ SELECT * FROM foo"
    "/*traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-01'*/ SELECT * FROM foo" | "SqlCommenter" | "Test" | "my-service" | "TestVersion" | false       | false | null                                                      | "/*traceparent='00-00000000000000007fffffffffffffff-000000024cb016ea-01'*/ SELECT * FROM foo"
    "/*customer-comment*/ SELECT * FROM foo"                                                      | "SqlCommenter" | "Test" | "my-service" | "TestVersion" | false       | false | null                                                      | "/*ddps='SqlCommenter',dddbs='my-service',dde='Test',ddpv='TestVersion'*/ /*customer-comment*/ SELECT * FROM foo"
    "/*traceparent"                                                                               | "SqlCommenter" | "Test" | "my-service" | "TestVersion" | false       | false | null                                                      | "/*ddps='SqlCommenter',dddbs='my-service',dde='Test',ddpv='TestVersion'*/ /*traceparent"
  }
}
