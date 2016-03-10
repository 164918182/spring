/**
 *    Copyright 2010-2015 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.mybatis.spring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.sql.SQLException;

import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.support.PersistenceExceptionTranslator;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import com.mockrunner.mock.jdbc.MockConnection;
import com.mockrunner.mock.jdbc.MockResultSet;

/**
 * 
 *
 * @version $Id$
 */
public abstract class AbstractMyBatisSpringTest {

  protected static PooledMockDataSource dataSource = new PooledMockDataSource();

  protected static SqlSessionFactory sqlSessionFactory;

  protected static ExecutorInterceptor executorInterceptor = new ExecutorInterceptor();

  protected static DataSourceTransactionManager txManager;

  protected static PersistenceExceptionTranslator exceptionTranslator;

  protected MockConnection connection;

  protected MockConnection connectionTwo;

  @BeforeClass
  public static void setupBase() throws Exception {
    // create an SqlSessionFactory that will use SpringManagedTransactions
    SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
    factoryBean.setMapperLocations(new Resource[] { new ClassPathResource("org/mybatis/spring/TestMapper.xml") });
    // note running without SqlSessionFactoryBean.configLocation set => default configuration
    factoryBean.setDataSource(dataSource);
    factoryBean.setPlugins(new Interceptor[] { executorInterceptor });

    exceptionTranslator = new MyBatisExceptionTranslator(dataSource, true);

    sqlSessionFactory = factoryBean.getObject();

    txManager = new DataSourceTransactionManager(dataSource);
  }

  protected void assertNoCommit() {
    assertNoCommitJdbc();
    assertNoCommitSession();
  }

  protected void assertNoCommitJdbc() {
    assertEquals("should not call commit on Connection", 0, connection.getNumberCommits());
    assertEquals("should not call rollback on Connection", 0, connection.getNumberRollbacks());
  }

  protected void assertNoCommitSession() {
    assertEquals("should not call commit on SqlSession", 0, executorInterceptor.getCommitCount());
    assertEquals("should not call rollback on SqlSession", 0, executorInterceptor.getRollbackCount());
  }

  protected void assertCommit() {
    assertCommitJdbc();
    assertCommitSession();
  }

  protected void assertCommitJdbc() {
    assertEquals("should call commit on Connection", 1, connection.getNumberCommits());
    assertEquals("should not call rollback on Connection", 0, connection.getNumberRollbacks());
  }

  protected void assertCommitSession() {
    assertEquals("should call commit on SqlSession", 1, executorInterceptor.getCommitCount());
    assertEquals("should not call rollback on SqlSession", 0, executorInterceptor.getRollbackCount());
  }

  protected void assertRollback() {
    assertEquals("should not call commit on Connection", 0, connection.getNumberCommits());
    assertEquals("should call rollback on Connection", 1, connection.getNumberRollbacks());
    assertEquals("should not call commit on SqlSession", 0, executorInterceptor.getCommitCount());
    assertEquals("should call rollback on SqlSession", 1, executorInterceptor.getRollbackCount());
  }

  protected void assertSingleConnection() {
    assertEquals("should only call DataSource.getConnection() once", 1, dataSource.getConnectionCount());
  }

  protected void assertExecuteCount(int count) {
    assertEquals("should have executed " + count + " SQL statements", count, connection
        .getPreparedStatementResultSetHandler().getExecutedStatements().size());
  }

  protected void assertConnectionClosed(MockConnection connection) {
    try {
      if ((connection != null) && !connection.isClosed()) {
        fail("Connection is not closed");
      }
    } catch (SQLException sqle) {
      fail("cannot call Connection.isClosed() " + sqle.getMessage());
    }
  }

  protected MockConnection createMockConnection() {
    // this query must be the same as the query in TestMapper.xml
    MockResultSet rs = new MockResultSet("SELECT 1");
    rs.addRow(new Object[] { 1 });

    MockConnection con = new MockConnection();
    con.getPreparedStatementResultSetHandler().prepareResultSet("SELECT 1", rs);

    return con;
  }

  /*
   * Setup a new Connection before each test since its closed state will need to be checked
   * afterwards and there is no Connection.open().
   */
  @Before
  public void setupConnection() throws SQLException {
    dataSource.reset();
    connection = createMockConnection();
    connectionTwo = createMockConnection();
    dataSource.addConnection(connectionTwo);
    dataSource.addConnection(connection);
  }

  @Before
  public void resetExecutorInterceptor() {
    executorInterceptor.reset();
  }

  @After
  public void validateConnectionClosed() {
    assertConnectionClosed(connection);

    connection = null;
  }

}
