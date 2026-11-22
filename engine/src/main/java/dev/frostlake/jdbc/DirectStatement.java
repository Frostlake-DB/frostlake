/*
 * Copyright 2026 MLorek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.frostlake.jdbc;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.ExecutionResult;
import dev.frostlake.executor.DynamicStatementCount;
import dev.frostlake.executor.StatementCancellation;
import dev.frostlake.executor.commands.FailureCode;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.NumericType;

import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Direct Statement implementation for embedded DatabaseEngine
 */
public class DirectStatement extends AbstractFrostlakeStatement {

    /** The token of the statement running now, which {@link #cancel()} reaches; null between statements. */
    private volatile StatementCancellation running;

    private final Connection connection;
    private final DatabaseEngine engine;
    // Every result the last (possibly multi-statement) execution produced, one per statement, and the walk
    // over them: which answer rows and which an update count, and where getMoreResults() stands.
    private List<ResultSet> results = new ArrayList<>();
    private JdbcResultWalk walk = JdbcResultWalk.none();
    // The result set handed out for the current result, and those getMoreResults(KEEP_CURRENT_RESULT) left open.
    private DirectResultSet currentResultSet;
    private final List<DirectResultSet> keptResultSets = new ArrayList<>();
    private final List<String> batchStatements = new ArrayList<>();
    private final List<Long> generatedKeys = new ArrayList<>();
    private String lastQueryId;
    private boolean returnGeneratedKeys = false;
    private static long keySequence = 0;

    public DirectStatement(final Connection connection, final DatabaseEngine engine) {
        this.connection = connection;
        this.engine = engine;
    }


    @Override
    protected int sessionStatementCount() {
        return connection instanceof DirectConnection ? ((DirectConnection) connection).getMultiStatementCount() : 1;
    }

    @Override
    protected void sessionStatementCount(final int count) {
        if (connection instanceof DirectConnection) {
            ((DirectConnection) connection).setMultiStatementCount(count);
        }
    }

    @Override
    protected int statementCount(final String sql) {
        return JdbcMultiStatement.countStatements(sql);
    }

    /**
     * Run a request through the engine, gated first, and keep its query id. The count it asked for also gates the
     * text of an EXECUTE IMMEDIATE among its statements (see DynamicStatementCount).
     */
    private List<ResultSet> run(final String sql) throws SQLException {
        checkClosed();
        requireSqlText(sql);
        if (connection instanceof DirectConnection) {
            ((DirectConnection) connection).requireSession();
        }
        applyMultiStatementGate(sql);
        // Executing closes the result set the previous execution handed out, as JDBC defines it.
        closeCurrentResultSet();
        final ExecutionResult result;
        final Integer displacedCount = DynamicStatementCount.beginRequest(desiredStatementCount(),
            JdbcMultiStatement.countStatements(sql));
        // The statement runs under a token cancel() reaches from another thread and the query timeout bounds.
        final StatementCancellation token = new StatementCancellation(queryTimeout());
        running = token;
        StatementCancellation.offer(token);
        try {
            result = connection instanceof DirectConnection
                ? ((DirectConnection) connection).executeScoped(sql)
                : engine.execute(sql);
        } catch (final RuntimeException e) {
            // The exception carries the account's code and SQLSTATE of its family (live-verified).
            final FailureCode family = FailureCode.ofClient(e);
            throw family == null ? new SQLException(e.getMessage(), e)
                : new SQLException(e.getMessage(), family.state(), family.code(), e);
        } finally {
            StatementCancellation.offer(null);
            running = null;
            DynamicStatementCount.endRequest(displacedCount);
        }
        lastQueryId = result.getQueryId();
        if (!result.isSuccess()) {
            final FailureCode family = FailureCode.ofClientMessage(result.getErrorMessage());
            throw family == null ? new SQLException(result.getErrorMessage())
                : new SQLException(result.getErrorMessage(), family.state(), family.code());
        }
        return result.getResultSets();
    }

    /**
     * Take a request's results as the walk's: each answers rows or the update count its statement marked.
     * With {@code firstAsRows} the first answers rows whatever it is — {@code executeQuery} hands a single
     * statement's grid back as a result set even when it is an update count or a status line.
     */
    private void take(final List<ResultSet> produced, final boolean firstAsRows) {
        results = produced;
        final List<Long> counts = new ArrayList<>();
        for (int i = 0; i < produced.size(); i++) {
            counts.add(firstAsRows && i == 0 ? null : produced.get(i).getJdbcUpdateCount());
        }
        walk = new JdbcResultWalk(counts);
    }

    /**
     * Snowflake's rule: a single statement answers its result as a result set — a DML statement its count
     * grid, DDL its status line — while a request of several must begin with rows, and otherwise has run and
     * is refused all the same.
     */
    public java.sql.ResultSet executeQuery(final String sql) throws SQLException {
        final List<ResultSet> produced = run(sql);
        if (produced.isEmpty()) {
            take(produced, false);
            throw new SQLException("Query did not return a result set");
        }
        if (produced.size() > 1 && produced.get(0).getJdbcUpdateCount() != null) {
            take(produced, false);
            throw JdbcResultWalk.firstResultIsACount();
        }
        take(produced, true);
        currentResultSet = new DirectResultSet(this, produced.get(0));
        return currentResultSet;
    }

    @Override
    public int executeUpdate(final String sql) throws SQLException {
        return executeUpdate(sql, Statement.NO_GENERATED_KEYS);
    }

    @Override
    public int executeUpdate(final String sql, final int autoGeneratedKeys) throws SQLException {
        return (int) update(sql, autoGeneratedKeys);
    }

    /**
     * The first statement's update count. A statement that answers rows has run by the time it is refused
     * (live-verified: a refused {@code CALL} has still done its work).
     */
    private long update(final String sql, final int autoGeneratedKeys) throws SQLException {
        generatedKeys.clear();
        returnGeneratedKeys = (autoGeneratedKeys == Statement.RETURN_GENERATED_KEYS);
        take(run(sql), false);
        if (walk.currentIsRows()) {
            throw JdbcResultWalk.notAnUpdate(sql);
        }
        // If this is an INSERT and generated keys are requested, create a synthetic key
        if (returnGeneratedKeys && sql.trim().toUpperCase(Locale.ROOT).startsWith("INSERT")) {
            // Generate a simple sequential key (in real implementation, would get from auto-increment column)
            generatedKeys.add(++keySequence);
        }
        return Math.max(walk.updateCount(), 0);
    }

    @Override
    public long executeLargeUpdate(final String sql) throws SQLException {
        return update(sql, Statement.NO_GENERATED_KEYS);
    }

    @Override
    public long executeLargeUpdate(final String sql, final int autoGeneratedKeys) throws SQLException {
        return update(sql, autoGeneratedKeys);
    }

    @Override
    public long executeLargeUpdate(final String sql, final int[] columnIndexes) throws SQLException {
        return update(sql, Statement.RETURN_GENERATED_KEYS);
    }

    @Override
    public long executeLargeUpdate(final String sql, final String[] columnNames) throws SQLException {
        return update(sql, Statement.RETURN_GENERATED_KEYS);
    }

    /** True when the first statement answers rows; false when it answers an update count. */
    @Override
    public boolean execute(final String sql) throws SQLException {
        take(run(sql), false);
        return walk.currentIsRows();
    }

    /** The current result's rows, or null when it is an update count or the walk has passed the end. */
    @Override
    public java.sql.ResultSet getResultSet() throws SQLException {
        checkClosed();
        if (!walk.currentIsRows()) {
            return null;
        }
        if (currentResultSet == null) {
            currentResultSet = new DirectResultSet(this, results.get(walk.position()));
        }
        return currentResultSet;
    }

    /**
     * The result set handed out for the current result while it is still open, or null.
     *
     * @return the open result set, or null
     */
    protected java.sql.ResultSet openResultSet() throws SQLException {
        return currentResultSet == null || currentResultSet.isClosed() ? null : currentResultSet;
    }

    private void closeCurrentResultSet() throws SQLException {
        if (currentResultSet != null) {
            currentResultSet.close();
            currentResultSet = null;
        }
    }

    /**
     * The grid behind the current result whichever way the driver reports it: the rows of a query, and the
     * count grid or status line of a statement the driver reports as an update count — what
     * {@code executeQuery} would have handed back for that statement on its own. Null past the end.
     *
     * @return the current result's grid, or null
     */
    public java.sql.ResultSet getResultGrid() throws SQLException {
        checkClosed();
        final ResultSet grid = currentEngineResultSet();
        return grid == null ? null : new DirectResultSet(this, grid);
    }

    /**
     * The engine-level result of the last execution, for subclasses that need row access without
     * disturbing the JDBC cursor (e.g. the callable statement's OUT-parameter extraction).
     */
    protected ResultSet currentEngineResultSet() {
        final int position = walk.position();
        return position < results.size() ? results.get(position) : null;
    }

    /** Leave a whole prepared batch behind as one update count, as the driver's array-bound batch does. */
    protected void takeBatchCount(final long count) {
        currentResultSet = null;
        results = new ArrayList<>();
        walk = JdbcResultWalk.ofCount(count);
    }

    @Override
    public void close() throws SQLException {
        // Closing the statement closes every result set it handed out that is still open (live-verified).
        closeCurrentResultSet();
        for (final DirectResultSet kept : keptResultSets) {
            kept.close();
        }
        keptResultSets.clear();
        closed = true;
        results = new ArrayList<>();
        walk = JdbcResultWalk.none();
    }

    // Unimplemented methods

    /** The most rows a result of this statement hands out from now on, 0 for every row (live-verified). */
    @Override
    public void cancel() throws SQLException {
        checkClosed();
        // From any thread: the running statement ends at its next check with "SQL execution canceled", and this
        // object runs the next one as usual (live-verified).
        final StatementCancellation token = running;
        if (token != null) {
            token.cancel();
        }
    }

    /** The current result's update count: -1 when it answers rows, and once the walk has passed the end. */
    @Override
    public int getUpdateCount() throws SQLException {
        checkClosed();
        return (int) walk.updateCount();
    }

    @Override
    public long getLargeUpdateCount() throws SQLException {
        checkClosed();
        return walk.updateCount();
    }

    /**
     * Move to the next statement's result, closing the current result set; see {@link JdbcResultWalk#next()} for
     * what it answers.
     */
    @Override
    public boolean getMoreResults() throws SQLException {
        return getMoreResults(Statement.CLOSE_CURRENT_RESULT);
    }

    /**
     * Move to the next statement's result, the current result set closed, kept open or closed with every one kept
     * before it, as {@code current} says (see {@link JdbcResultWalk#next()} for what it answers).
     */
    @Override
    public boolean getMoreResults(final int current) throws SQLException {
        checkClosed();
        if (current == Statement.KEEP_CURRENT_RESULT) {
            if (currentResultSet != null) {
                keptResultSets.add(currentResultSet);
            }
            currentResultSet = null;
        } else {
            closeCurrentResultSet();
            if (current == Statement.CLOSE_ALL_RESULTS) {
                for (final DirectResultSet kept : keptResultSets) {
                    kept.close();
                }
                keptResultSets.clear();
            }
        }
        return walk.next();
    }

    @Override
    public void addBatch(final String sql) throws SQLException {
        checkClosed();
        if (sql == null || sql.trim().isEmpty()) {
            throw new SQLException("Batch SQL cannot be null or empty");
        }
        batchStatements.add(sql);
    }

    @Override
    public void clearBatch() throws SQLException {
        checkClosed();
        batchStatements.clear();
    }

    @Override
    /**
     * Live runs every batched statement even when one fails: each entry reports its real
     * affected-row count (not SUCCESS_NO_INFO), a failed entry is marked {@code EXECUTE_FAILED},
     * and after the batch a {@link BatchUpdateException} carries the FIRST failure's message,
     * SQLSTATE and error code together with the complete update-count array.
     */
    public int[] executeBatch() throws SQLException {
        checkClosed();
        final int[] counts = new int[batchStatements.size()];
        int index = 0;
        SQLException firstFailure = null;

        try {
            for (final String sql : batchStatements) {
                try {
                    // A statement that answers rows is not refused in a batch: it runs, and its entry is
                    // SUCCESS_NO_INFO (live-verified).
                    take(run(sql), false);
                    counts[index] = walk.currentIsRows() ? SUCCESS_NO_INFO : (int) walk.updateCount();
                } catch (final SQLException e) {
                    counts[index] = EXECUTE_FAILED;
                    if (firstFailure == null) {
                        firstFailure = e;
                    }
                }
                index++;
            }
        } finally {
            // Clear batch after execution
            batchStatements.clear();
        }

        if (firstFailure != null) {
            throw new BatchUpdateException(firstFailure.getMessage(), firstFailure.getSQLState(),
                    firstFailure.getErrorCode(), counts, firstFailure);
        }
        return counts;
    }

    @Override
    public Connection getConnection() throws SQLException {
        checkClosed();
        return connection;
    }

    /**
     * The engine query id of the last statement this object executed, or null before the first —
     * the direct-transport twin of the Snowflake driver's {@code SnowflakeStatement.getQueryID()}.
     */
    public String getQueryId() {
        return lastQueryId;
    }

    @Override
    public java.sql.ResultSet getGeneratedKeys() throws SQLException {
        checkClosed();

        // Create a simple ResultSet with generated keys
        final List<ResultSetColumn> columns = new ArrayList<>();
        columns.add(new ResultSetColumn(
            "GENERATED_KEY",
            new NumericType("BIGINT", 19, 0)
        ));

        final List<Row> rows = new ArrayList<>();
        for (final Long key : generatedKeys) {
            final List<Object> values = new ArrayList<>();
            values.add(key);
            rows.add(new Row(values));
        }

        final ResultSet resultSet = new ResultSet(columns, rows);
        return new DirectResultSet(this, resultSet);
    }

    @Override
    public int executeUpdate(final String sql, final int[] columnIndexes) throws SQLException {
        // Treat as RETURN_GENERATED_KEYS if column indexes specified
        return executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
    }

    @Override
    public int executeUpdate(final String sql, final String[] columnNames) throws SQLException {
        // Treat as RETURN_GENERATED_KEYS if column names specified
        return executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
    }

    @Override
    public boolean execute(final String sql, final int autoGeneratedKeys) throws SQLException {
        checkClosed();
        generatedKeys.clear();
        returnGeneratedKeys = (autoGeneratedKeys == Statement.RETURN_GENERATED_KEYS);

        final boolean hasResultSet = execute(sql);

        // If this is an INSERT and generated keys are requested, create a synthetic key
        if (returnGeneratedKeys && sql.trim().toUpperCase(Locale.ROOT).startsWith("INSERT")) {
            generatedKeys.add(++keySequence);
        }

        return hasResultSet;
    }

    @Override
    public boolean execute(final String sql, final int[] columnIndexes) throws SQLException {
        // Treat as RETURN_GENERATED_KEYS if column indexes specified
        return execute(sql, Statement.RETURN_GENERATED_KEYS);
    }

    @Override
    public boolean execute(final String sql, final String[] columnNames) throws SQLException {
        // Treat as RETURN_GENERATED_KEYS if column names specified
        return execute(sql, Statement.RETURN_GENERATED_KEYS);
    }

}
