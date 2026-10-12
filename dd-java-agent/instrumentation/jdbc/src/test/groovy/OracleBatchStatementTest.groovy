import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace

import datadog.trace.agent.test.InstrumentationSpecification
import datadog.trace.api.DDSpanTypes
import datadog.trace.bootstrap.instrumentation.api.Tags
import java.sql.Statement
import oracle.jdbc.internal.OracleCallableStatement
import test.TestConnection
import test.TestDatabaseMetaData

class OracleBatchStatementTest extends InstrumentationSpecification {

  def "Oracle #statementType batch generates one #operation span without configured matching types"() {
    setup:
    def connection = oracleConnection()
    def statement = oracleWrapper(statementType, connection)
    connection.preparedStatement = statement
    def prepared = connection.prepareStatement(query)

    when:
    def counts = runUnderTrace("parent") {
      prepared.executeBatch()
    }

    then:
    counts == ([1, 1] as int[])
    assertTraces(1) {
      trace(2) {
        span {
          operationName "parent"
        }
        span {
          operationName "oracle.query"
          serviceName "oracle"
          resourceName query
          spanType DDSpanTypes.SQL
          childOfPrevious()
          errored false
          measured true
          tags(false) {
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "oracle"
            "$Tags.DB_INSTANCE" "xe"
            "$Tags.DB_OPERATION" operation
          }
        }
      }
    }

    where:
    statementType | operation | query
    "Prepared"    | "INSERT"  | "INSERT INTO batch_test (id) VALUES (?)"
    "Prepared"    | "UPDATE"  | "UPDATE batch_test SET id = ? WHERE id = ?"
    "Callable"    | "INSERT"  | "INSERT INTO batch_test (id) VALUES (?)"
    "Callable"    | "UPDATE"  | "UPDATE batch_test SET id = ? WHERE id = ?"
  }

  def "Oracle plain statement batch without prepared SQL does not create a prepared span"() {
    setup:
    def statement = oracleWrapper("", oracleConnection())

    when:
    def counts = runUnderTrace("parent") {
      statement.executeBatch()
    }

    then:
    counts == ([1, 1] as int[])
    assertTraces(1) {
      trace(1) {
        span {
          operationName "parent"
        }
      }
    }
  }

  private static TestConnection oracleConnection() {
    def connection = new TestConnection(false)
    def metadata = new TestDatabaseMetaData()
    metadata.setURL("jdbc:oracle:thin:@localhost:1521:XE")
    connection.setMetaData(metadata)
    return connection
  }

  /** Uses the real Oracle wrapper and inherited executeBatch method without a database. */
  private Statement oracleWrapper(String statementType, TestConnection connection) {
    def delegate = Stub(OracleCallableStatement, additionalInterfaces: [Class.forName("oracle.jdbc.driver.Wrappable")]) {
      getConnection() >> connection
      executeBatch() >> ([1, 1] as int[])
    }
    def wrapperType = Class.forName("oracle.jdbc.driver.Oracle${statementType}StatementWrapper")
    def parameterType = Class.forName("oracle.jdbc.Oracle${statementType}Statement")
    def constructor = wrapperType.getDeclaredConstructor(parameterType)
    constructor.setAccessible(true)
    return constructor.newInstance(delegate) as Statement
  }
}
