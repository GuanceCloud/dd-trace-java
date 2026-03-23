import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace

import com.alibaba.druid.pool.DruidDataSource
import datadog.trace.agent.test.InstrumentationSpecification
import datadog.trace.api.DDSpanTypes
import datadog.trace.bootstrap.instrumentation.api.Tags

class DruidPreparedStatementTest extends InstrumentationSpecification {

  def "prepared statement on druid wrapper generates span"() {
    setup:
    def ds = new DruidDataSource()
    ds.setUrl("jdbc:h2:mem:druid_wrapper_test;DB_CLOSE_DELAY=-1")
    ds.setDriverClassName("org.h2.Driver")
    ds.setInitialSize(1)
    ds.setMaxActive(1)

    def initConnection = ds.getConnection()
    def initStatement = initConnection.createStatement()
    initStatement.execute("CREATE TABLE IF NOT EXISTS T_TEST (ID INT PRIMARY KEY, NAME VARCHAR(32))")
    initStatement.execute("MERGE INTO T_TEST KEY(ID) VALUES (1, 'alice')")
    initStatement.close()
    initConnection.close()

    TEST_WRITER.waitForTraces(1)
    TEST_WRITER.clear()

    when:
    runUnderTrace("parent") {
      def connection = ds.getConnection()
      def statement = connection.prepareStatement("SELECT NAME FROM T_TEST WHERE ID = ?")
      statement.setInt(1, 1)
      def resultSet = statement.executeQuery()
      assert resultSet.next()
      assert resultSet.getString(1) == "alice"
      resultSet.close()
      statement.close()
      connection.close()
    }

    then:
    assertTraces(1) {
      trace(2) {
        span {
          operationName "parent"
        }
        span {
          operationName "h2.query"
          serviceName "h2"
          resourceName "SELECT NAME FROM T_TEST WHERE ID = ?"
          spanType DDSpanTypes.SQL
          childOfPrevious()
          errored false
          measured true
          tags(false) {
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "h2"
            "$Tags.DB_INSTANCE" "druid_wrapper_test"
            "$Tags.DB_OPERATION" "SELECT"
          }
        }
      }
    }

    cleanup:
    ds?.close()
  }
}
