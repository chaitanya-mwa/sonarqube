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
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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
    this.writeConnection = writeConnection;
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
