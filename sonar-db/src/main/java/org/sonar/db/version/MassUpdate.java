/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.db.version;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLType;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import org.sonar.core.util.ProgressLogger;
import org.sonar.db.BatchSession;
import org.sonar.db.Database;

import static com.google.common.base.Preconditions.checkState;

public class MassUpdate {

  @FunctionalInterface
  public interface Handler {
    /**
     * Convert some column values of a given row.
     *
     * @return true if the row must be updated, else false. If false, then the update parameter must not be touched.
     */
    boolean handle(Select.Row row, SqlStatement update) throws SQLException;
  }

  @FunctionalInterface
  public interface MultiHandler {
    /**
     * Convert some column values of a given row.
     *
     * @param updateIndex 0-based
     * @return true if the row must be updated, else false. If false, then the update parameter must not be touched.
     */
    boolean handle(Select.Row row, SqlStatement update, int updateIndex) throws SQLException;
  }

  private final Database db;
  private final Connection readConnection;
  private final Connection writeConnection;
  private final AtomicLong counter = new AtomicLong(0L);
  private final ProgressLogger progress = ProgressLogger.create(getClass(), counter);

  private Select select;
  private List<String> updateStatements = new ArrayList<>(1);

  MassUpdate(Database db, Connection readConnection, Connection writeConnection) {
    this.db = db;
    this.readConnection = readConnection;
    this.writeConnection = new ConcurrentPreparedStatementLoggingConnection(writeConnection);
  }

  public SqlStatement select(String sql) throws SQLException {
    this.select = SelectImpl.create(db, readConnection, sql);
    return this.select;
  }

  public MassUpdate update(String sql) throws SQLException {
    this.updateStatements.add(sql);
    return this;
  }

  public MassUpdate rowPluralName(String s) {
    this.progress.setPluralLabel(s);
    return this;
  }

  public void execute(Handler handler) throws SQLException {
    checkState(select != null && !updateStatements.isEmpty(), "SELECT or UPDATE requests are not defined");
    checkState(updateStatements.size() == 1, "There should be only one update when using a " + Handler.class.getName());

    progress.start();
    try {
      UpsertImpl update = UpsertImpl.create(writeConnection, updateStatements.iterator().next());
      select.scroll(row -> callSingleHandler(handler, update, row));
      closeUpsertImpl(update);

      // log the total number of processed rows
      progress.log();
    } finally {
      progress.stop();
    }
  }

  private void callSingleHandler(Handler handler, Upsert update, Select.Row row) throws SQLException {
    if (handler.handle(row, update)) {
      update.addBatch();
    }
    counter.getAndIncrement();
  }

  public void execute(MultiHandler handler) throws SQLException {
    checkState(select != null && !updateStatements.isEmpty(), "SELECT or UPDATE(s) requests are not defined");

    progress.start();
    try {
      try (MultiHandlerRowHandler rowHandler = new MultiHandlerRowHandler(handler)) {
        select.scroll(rowHandler);
      }

      // log the total number of processed rows
      progress.log();
    } finally {
      progress.stop();
    }
  }

  private static class ConcurrentPreparedStatementLoggingConnection implements Connection {
    private final Connection delegate;
    private int ppsCount;

    public ConcurrentPreparedStatementLoggingConnection(Connection delegate) {
      this.delegate = delegate;
      ppsCount = 0;
    }

    public Statement createStatement() throws SQLException {
      return delegate.createStatement();
    }

    public PreparedStatement prepareStatement(String sql) throws SQLException {
      return new WrappedPreparedStatement(delegate.prepareStatement(sql));
    }

    public CallableStatement prepareCall(String sql) throws SQLException {
      return delegate.prepareCall(sql);
    }

    public String nativeSQL(String sql) throws SQLException {
      return delegate.nativeSQL(sql);
    }

    public void setAutoCommit(boolean autoCommit) throws SQLException {
      delegate.setAutoCommit(autoCommit);
    }

    public boolean getAutoCommit() throws SQLException {
      return delegate.getAutoCommit();
    }

    public void commit() throws SQLException {
      delegate.commit();
    }

    public void rollback() throws SQLException {
      delegate.rollback();
    }

    public void close() throws SQLException {
      delegate.close();
    }

    public boolean isClosed() throws SQLException {
      return delegate.isClosed();
    }

    public DatabaseMetaData getMetaData() throws SQLException {
      return delegate.getMetaData();
    }

    public void setReadOnly(boolean readOnly) throws SQLException {
      delegate.setReadOnly(readOnly);
    }

    public boolean isReadOnly() throws SQLException {
      return delegate.isReadOnly();
    }

    public void setCatalog(String catalog) throws SQLException {
      delegate.setCatalog(catalog);
    }

    public String getCatalog() throws SQLException {
      return delegate.getCatalog();
    }

    public void setTransactionIsolation(int level) throws SQLException {
      delegate.setTransactionIsolation(level);
    }

    public int getTransactionIsolation() throws SQLException {
      return delegate.getTransactionIsolation();
    }

    public SQLWarning getWarnings() throws SQLException {
      return delegate.getWarnings();
    }

    public void clearWarnings() throws SQLException {
      delegate.clearWarnings();
    }

    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
      return delegate.createStatement(resultSetType, resultSetConcurrency);
    }

    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
      return new WrappedPreparedStatement(delegate.prepareStatement(sql, resultSetType, resultSetConcurrency));
    }

    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
      return delegate.prepareCall(sql, resultSetType, resultSetConcurrency);
    }

    public Map<String, Class<?>> getTypeMap() throws SQLException {
      return delegate.getTypeMap();
    }

    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
      delegate.setTypeMap(map);
    }

    public void setHoldability(int holdability) throws SQLException {
      delegate.setHoldability(holdability);
    }

    public int getHoldability() throws SQLException {
      return delegate.getHoldability();
    }

    public Savepoint setSavepoint() throws SQLException {
      return delegate.setSavepoint();
    }

    public Savepoint setSavepoint(String name) throws SQLException {
      return delegate.setSavepoint(name);
    }

    public void rollback(Savepoint savepoint) throws SQLException {
      delegate.rollback(savepoint);
    }

    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
      delegate.releaseSavepoint(savepoint);
    }

    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
      return delegate.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
      return new WrappedPreparedStatement(delegate.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability));
    }

    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
      return delegate.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
      return new WrappedPreparedStatement(delegate.prepareStatement(sql, autoGeneratedKeys));
    }

    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
      return new WrappedPreparedStatement(delegate.prepareStatement(sql, columnIndexes));
    }

    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
      return new WrappedPreparedStatement(delegate.prepareStatement(sql, columnNames));
    }

    public Clob createClob() throws SQLException {
      return delegate.createClob();
    }

    public Blob createBlob() throws SQLException {
      return delegate.createBlob();
    }

    public NClob createNClob() throws SQLException {
      return delegate.createNClob();
    }

    public SQLXML createSQLXML() throws SQLException {
      return delegate.createSQLXML();
    }

    public boolean isValid(int timeout) throws SQLException {
      return delegate.isValid(timeout);
    }

    public void setClientInfo(String name, String value) throws SQLClientInfoException {
      delegate.setClientInfo(name, value);
    }

    public void setClientInfo(Properties properties) throws SQLClientInfoException {
      delegate.setClientInfo(properties);
    }

    public String getClientInfo(String name) throws SQLException {
      return delegate.getClientInfo(name);
    }

    public Properties getClientInfo() throws SQLException {
      return delegate.getClientInfo();
    }

    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
      return delegate.createArrayOf(typeName, elements);
    }

    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
      return delegate.createStruct(typeName, attributes);
    }

    public void setSchema(String schema) throws SQLException {
      delegate.setSchema(schema);
    }

    public String getSchema() throws SQLException {
      return delegate.getSchema();
    }

    public void abort(Executor executor) throws SQLException {
      delegate.abort(executor);
    }

    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
      delegate.setNetworkTimeout(executor, milliseconds);
    }

    public int getNetworkTimeout() throws SQLException {
      return delegate.getNetworkTimeout();
    }

    public <T> T unwrap(Class<T> iface) throws SQLException {
      return delegate.unwrap(iface);
    }

    public boolean isWrapperFor(Class<?> iface) throws SQLException {
      return delegate.isWrapperFor(iface);
    }

    private class WrappedPreparedStatement implements PreparedStatement {
      private final PreparedStatement preparedStatement;

      public WrappedPreparedStatement(PreparedStatement preparedStatement) {
        System.err.println(String.format("There is %s PreparedStatement open", ppsCount));
        ppsCount++;
        this.preparedStatement = preparedStatement;
      }

      public ResultSet executeQuery() throws SQLException {
        return preparedStatement.executeQuery();
      }

      public int executeUpdate() throws SQLException {
        return preparedStatement.executeUpdate();
      }

      public void setNull(int parameterIndex, int sqlType) throws SQLException {
        preparedStatement.setNull(parameterIndex, sqlType);
      }

      public void setBoolean(int parameterIndex, boolean x) throws SQLException {
        preparedStatement.setBoolean(parameterIndex, x);
      }

      public void setByte(int parameterIndex, byte x) throws SQLException {
        preparedStatement.setByte(parameterIndex, x);
      }

      public void setShort(int parameterIndex, short x) throws SQLException {
        preparedStatement.setShort(parameterIndex, x);
      }

      public void setInt(int parameterIndex, int x) throws SQLException {
        preparedStatement.setInt(parameterIndex, x);
      }

      public void setLong(int parameterIndex, long x) throws SQLException {
        preparedStatement.setLong(parameterIndex, x);
      }

      public void setFloat(int parameterIndex, float x) throws SQLException {
        preparedStatement.setFloat(parameterIndex, x);
      }

      public void setDouble(int parameterIndex, double x) throws SQLException {
        preparedStatement.setDouble(parameterIndex, x);
      }

      public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
        preparedStatement.setBigDecimal(parameterIndex, x);
      }

      public void setString(int parameterIndex, String x) throws SQLException {
        preparedStatement.setString(parameterIndex, x);
      }

      public void setBytes(int parameterIndex, byte[] x) throws SQLException {
        preparedStatement.setBytes(parameterIndex, x);
      }

      public void setDate(int parameterIndex, java.sql.Date x) throws SQLException {
        preparedStatement.setDate(parameterIndex, x);
      }

      public void setTime(int parameterIndex, Time x) throws SQLException {
        preparedStatement.setTime(parameterIndex, x);
      }

      public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
        preparedStatement.setTimestamp(parameterIndex, x);
      }

      public void setAsciiStream(int parameterIndex, InputStream x, int length) throws SQLException {
        preparedStatement.setAsciiStream(parameterIndex, x, length);
      }

      @Deprecated
      public void setUnicodeStream(int parameterIndex, InputStream x, int length) throws SQLException {
        preparedStatement.setUnicodeStream(parameterIndex, x, length);
      }

      public void setBinaryStream(int parameterIndex, InputStream x, int length) throws SQLException {
        preparedStatement.setBinaryStream(parameterIndex, x, length);
      }

      public void clearParameters() throws SQLException {
        preparedStatement.clearParameters();
      }

      public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
        preparedStatement.setObject(parameterIndex, x, targetSqlType);
      }

      public void setObject(int parameterIndex, Object x) throws SQLException {
        preparedStatement.setObject(parameterIndex, x);
      }

      public boolean execute() throws SQLException {
        return preparedStatement.execute();
      }

      public void addBatch() throws SQLException {
        preparedStatement.addBatch();
      }

      public void setCharacterStream(int parameterIndex, Reader reader, int length) throws SQLException {
        preparedStatement.setCharacterStream(parameterIndex, reader, length);
      }

      public void setRef(int parameterIndex, Ref x) throws SQLException {
        preparedStatement.setRef(parameterIndex, x);
      }

      public void setBlob(int parameterIndex, Blob x) throws SQLException {
        preparedStatement.setBlob(parameterIndex, x);
      }

      public void setClob(int parameterIndex, Clob x) throws SQLException {
        preparedStatement.setClob(parameterIndex, x);
      }

      public void setArray(int parameterIndex, Array x) throws SQLException {
        preparedStatement.setArray(parameterIndex, x);
      }

      public ResultSetMetaData getMetaData() throws SQLException {
        return preparedStatement.getMetaData();
      }

      public void setDate(int parameterIndex, java.sql.Date x, Calendar cal) throws SQLException {
        preparedStatement.setDate(parameterIndex, x, cal);
      }

      public void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException {
        preparedStatement.setTime(parameterIndex, x, cal);
      }

      public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException {
        preparedStatement.setTimestamp(parameterIndex, x, cal);
      }

      public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {
        preparedStatement.setNull(parameterIndex, sqlType, typeName);
      }

      public void setURL(int parameterIndex, URL x) throws SQLException {
        preparedStatement.setURL(parameterIndex, x);
      }

      public ParameterMetaData getParameterMetaData() throws SQLException {
        return preparedStatement.getParameterMetaData();
      }

      public void setRowId(int parameterIndex, RowId x) throws SQLException {
        preparedStatement.setRowId(parameterIndex, x);
      }

      public void setNString(int parameterIndex, String value) throws SQLException {
        preparedStatement.setNString(parameterIndex, value);
      }

      public void setNCharacterStream(int parameterIndex, Reader value, long length) throws SQLException {
        preparedStatement.setNCharacterStream(parameterIndex, value, length);
      }

      public void setNClob(int parameterIndex, NClob value) throws SQLException {
        preparedStatement.setNClob(parameterIndex, value);
      }

      public void setClob(int parameterIndex, Reader reader, long length) throws SQLException {
        preparedStatement.setClob(parameterIndex, reader, length);
      }

      public void setBlob(int parameterIndex, InputStream inputStream, long length) throws SQLException {
        preparedStatement.setBlob(parameterIndex, inputStream, length);
      }

      public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {
        preparedStatement.setNClob(parameterIndex, reader, length);
      }

      public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
        preparedStatement.setSQLXML(parameterIndex, xmlObject);
      }

      public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException {
        preparedStatement.setObject(parameterIndex, x, targetSqlType, scaleOrLength);
      }

      public void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException {
        preparedStatement.setAsciiStream(parameterIndex, x, length);
      }

      public void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException {
        preparedStatement.setBinaryStream(parameterIndex, x, length);
      }

      public void setCharacterStream(int parameterIndex, Reader reader, long length) throws SQLException {
        preparedStatement.setCharacterStream(parameterIndex, reader, length);
      }

      public void setAsciiStream(int parameterIndex, InputStream x) throws SQLException {
        preparedStatement.setAsciiStream(parameterIndex, x);
      }

      public void setBinaryStream(int parameterIndex, InputStream x) throws SQLException {
        preparedStatement.setBinaryStream(parameterIndex, x);
      }

      public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {
        preparedStatement.setCharacterStream(parameterIndex, reader);
      }

      public void setNCharacterStream(int parameterIndex, Reader value) throws SQLException {
        preparedStatement.setNCharacterStream(parameterIndex, value);
      }

      public void setClob(int parameterIndex, Reader reader) throws SQLException {
        preparedStatement.setClob(parameterIndex, reader);
      }

      public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException {
        preparedStatement.setBlob(parameterIndex, inputStream);
      }

      public void setNClob(int parameterIndex, Reader reader) throws SQLException {
        preparedStatement.setNClob(parameterIndex, reader);
      }

      public void setObject(int parameterIndex, Object x, SQLType targetSqlType, int scaleOrLength) throws SQLException {
        preparedStatement.setObject(parameterIndex, x, targetSqlType, scaleOrLength);
      }

      public void setObject(int parameterIndex, Object x, SQLType targetSqlType) throws SQLException {
        preparedStatement.setObject(parameterIndex, x, targetSqlType);
      }

      public long executeLargeUpdate() throws SQLException {
        return preparedStatement.executeLargeUpdate();
      }

      public ResultSet executeQuery(String sql) throws SQLException {
        return preparedStatement.executeQuery(sql);
      }

      public int executeUpdate(String sql) throws SQLException {
        return preparedStatement.executeUpdate(sql);
      }

      public void close() throws SQLException {
        ppsCount--;
        preparedStatement.close();
      }

      public int getMaxFieldSize() throws SQLException {
        return preparedStatement.getMaxFieldSize();
      }

      public void setMaxFieldSize(int max) throws SQLException {
        preparedStatement.setMaxFieldSize(max);
      }

      public int getMaxRows() throws SQLException {
        return preparedStatement.getMaxRows();
      }

      public void setMaxRows(int max) throws SQLException {
        preparedStatement.setMaxRows(max);
      }

      public void setEscapeProcessing(boolean enable) throws SQLException {
        preparedStatement.setEscapeProcessing(enable);
      }

      public int getQueryTimeout() throws SQLException {
        return preparedStatement.getQueryTimeout();
      }

      public void setQueryTimeout(int seconds) throws SQLException {
        preparedStatement.setQueryTimeout(seconds);
      }

      public void cancel() throws SQLException {
        preparedStatement.cancel();
      }

      public SQLWarning getWarnings() throws SQLException {
        return preparedStatement.getWarnings();
      }

      public void clearWarnings() throws SQLException {
        preparedStatement.clearWarnings();
      }

      public void setCursorName(String name) throws SQLException {
        preparedStatement.setCursorName(name);
      }

      public boolean execute(String sql) throws SQLException {
        return preparedStatement.execute(sql);
      }

      public ResultSet getResultSet() throws SQLException {
        return preparedStatement.getResultSet();
      }

      public int getUpdateCount() throws SQLException {
        return preparedStatement.getUpdateCount();
      }

      public boolean getMoreResults() throws SQLException {
        return preparedStatement.getMoreResults();
      }

      public void setFetchDirection(int direction) throws SQLException {
        preparedStatement.setFetchDirection(direction);
      }

      public int getFetchDirection() throws SQLException {
        return preparedStatement.getFetchDirection();
      }

      public void setFetchSize(int rows) throws SQLException {
        preparedStatement.setFetchSize(rows);
      }

      public int getFetchSize() throws SQLException {
        return preparedStatement.getFetchSize();
      }

      public int getResultSetConcurrency() throws SQLException {
        return preparedStatement.getResultSetConcurrency();
      }

      public int getResultSetType() throws SQLException {
        return preparedStatement.getResultSetType();
      }

      public void addBatch(String sql) throws SQLException {
        preparedStatement.addBatch(sql);
      }

      public void clearBatch() throws SQLException {
        preparedStatement.clearBatch();
      }

      public int[] executeBatch() throws SQLException {
        return preparedStatement.executeBatch();
      }

      public Connection getConnection() throws SQLException {
        return preparedStatement.getConnection();
      }

      public boolean getMoreResults(int current) throws SQLException {
        return preparedStatement.getMoreResults(current);
      }

      public ResultSet getGeneratedKeys() throws SQLException {
        return preparedStatement.getGeneratedKeys();
      }

      public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        return preparedStatement.executeUpdate(sql, autoGeneratedKeys);
      }

      public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
        return preparedStatement.executeUpdate(sql, columnIndexes);
      }

      public int executeUpdate(String sql, String[] columnNames) throws SQLException {
        return preparedStatement.executeUpdate(sql, columnNames);
      }

      public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
        return preparedStatement.execute(sql, autoGeneratedKeys);
      }

      public boolean execute(String sql, int[] columnIndexes) throws SQLException {
        return preparedStatement.execute(sql, columnIndexes);
      }

      public boolean execute(String sql, String[] columnNames) throws SQLException {
        return preparedStatement.execute(sql, columnNames);
      }

      public int getResultSetHoldability() throws SQLException {
        return preparedStatement.getResultSetHoldability();
      }

      public boolean isClosed() throws SQLException {
        return preparedStatement.isClosed();
      }

      public void setPoolable(boolean poolable) throws SQLException {
        preparedStatement.setPoolable(poolable);
      }

      public boolean isPoolable() throws SQLException {
        return preparedStatement.isPoolable();
      }

      public void closeOnCompletion() throws SQLException {
        preparedStatement.closeOnCompletion();
      }

      public boolean isCloseOnCompletion() throws SQLException {
        return preparedStatement.isCloseOnCompletion();
      }

      public long getLargeUpdateCount() throws SQLException {
        return preparedStatement.getLargeUpdateCount();
      }

      public void setLargeMaxRows(long max) throws SQLException {
        preparedStatement.setLargeMaxRows(max);
      }

      public long getLargeMaxRows() throws SQLException {
        return preparedStatement.getLargeMaxRows();
      }

      public long[] executeLargeBatch() throws SQLException {
        return preparedStatement.executeLargeBatch();
      }

      public long executeLargeUpdate(String sql) throws SQLException {
        return preparedStatement.executeLargeUpdate(sql);
      }

      public long executeLargeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        return preparedStatement.executeLargeUpdate(sql, autoGeneratedKeys);
      }

      public long executeLargeUpdate(String sql, int[] columnIndexes) throws SQLException {
        return preparedStatement.executeLargeUpdate(sql, columnIndexes);
      }

      public long executeLargeUpdate(String sql, String[] columnNames) throws SQLException {
        return preparedStatement.executeLargeUpdate(sql, columnNames);
      }

      public <T> T unwrap(Class<T> iface) throws SQLException {
        return preparedStatement.unwrap(iface);
      }

      public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return preparedStatement.isWrapperFor(iface);
      }
    }
  }

  private class MultiHandlerRowHandler implements Select.RowHandler, AutoCloseable {
    private final Multimap<String, SqlStatementBufferingProxy> proxiesPerUpdateStatement = ArrayListMultimap.create();
    private final MultiHandler handler;
    private int rowCount = 0;

    private MultiHandlerRowHandler(MultiHandler handler) {
      this.handler = handler;
    }

    @Override
    public void handle(Select.Row row) throws SQLException {
      if (rowCount == BatchSession.MAX_BATCH_SIZE) {
        executeStatements();
      }
      int statementIndex = 0;
      for (String updateStatement : updateStatements) {
        SqlStatementBufferingProxy buffer = new SqlStatementBufferingProxy();
        if (handler.handle(row, buffer, statementIndex)) {
          proxiesPerUpdateStatement.put(updateStatement, buffer);
        }
        statementIndex++;
      }
      rowCount++;
    }

    @Override
    public void close() throws SQLException {
      if (rowCount > 0) {
        executeStatements();
      }
    }

    private void executeStatements() throws SQLException {
      for (String updateStatement : updateStatements) {
        UpsertImpl update = UpsertImpl.create(writeConnection, updateStatement);
        for (SqlStatementBufferingProxy bufferingProxy : proxiesPerUpdateStatement.get(updateStatement)) {
          bufferingProxy.accept(update);
          update.addBatch();
        }
        closeUpsertImpl(update);
      }

      counter.addAndGet(rowCount);
      rowCount = 0;
    }
  }

  @FunctionalInterface
  private interface SqlConsumer<T> {
    void accept(T t) throws SQLException;
  }

  private static final class SqlStatementBufferingProxy implements SqlStatement<SqlStatementBufferingProxy>, SqlConsumer<SqlStatement> {
    private final List<SqlStatementSetterCall> setterCalls = new ArrayList<>();

    @Override
    public void accept(SqlStatement preparedStatement) throws SQLException {
      for (SqlStatementSetterCall setterCall : setterCalls) {
        setterCall.execute(preparedStatement);
      }
    }

    @Override
    public SqlStatementBufferingProxy setBoolean(int columnIndex, @Nullable Boolean value) throws SQLException {
      setterCalls.add(pstmt -> pstmt.setBoolean(columnIndex, value));
      return this;
    }

    @Override
    public SqlStatementBufferingProxy setDate(int columnIndex, @Nullable Date value) throws SQLException {
      setterCalls.add(pstmt -> pstmt.setDate(columnIndex, value));
      return this;
    }

    @Override
    public SqlStatementBufferingProxy setDouble(int columnIndex, @Nullable Double value) throws SQLException {
      setterCalls.add(pstmt -> pstmt.setDouble(columnIndex, value));
      return this;
    }

    @Override
    public SqlStatementBufferingProxy setInt(int columnIndex, @Nullable Integer value) throws SQLException {
      setterCalls.add(pstmt -> pstmt.setInt(columnIndex, value));
      return this;
    }

    @Override
    public SqlStatementBufferingProxy setLong(int columnIndex, @Nullable Long value) throws SQLException {
      setterCalls.add(pstmt -> pstmt.setLong(columnIndex, value));
      return this;
    }

    @Override
    public SqlStatementBufferingProxy setString(int columnIndex, @Nullable String value) throws SQLException {
      setterCalls.add(pstmt -> pstmt.setString(columnIndex, value));
      return this;
    }

    @Override
    public SqlStatementBufferingProxy setBytes(int columnIndex, @Nullable byte[] data) throws SQLException {
      setterCalls.add(pstmt -> pstmt.setBytes(columnIndex, data));
      return this;
    }

    @Override
    public SqlStatementBufferingProxy close() {
      setterCalls.clear();
      return this;
    }
  }

  @FunctionalInterface
  private interface SqlStatementSetterCall {
    void execute(SqlStatement stmt) throws SQLException;
  }

  private static void closeUpsertImpl(UpsertImpl update) throws SQLException {
    if (update.getBatchCount() > 0L) {
      update.execute().commit();
    }
    update.close();
  }

}
