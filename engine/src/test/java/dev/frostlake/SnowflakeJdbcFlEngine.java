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
package dev.frostlake;

import dev.frostlake.executor.StatementCount;
import dev.frostlake.http.SnowflakeEndpoint;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.NumericType;
import dev.frostlake.values.VariantJsonText;
import dev.frostlake.values.VariantValue;
import net.snowflake.client.api.statement.SnowflakeStatement;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A {@link DatabaseEngine} whose SQL surface ({@code execute} / {@code executeQuery} / {@code executeUpdate}) runs
 * through Snowflake's own JDBC driver, logged in to a {@link SnowflakeEndpoint} that serves this very engine — the
 * {@code SF_JDBC_FL=1} backend for {@link BaseDatabaseTest}, turning the suite into an audit of the endpoint and of
 * how the account's driver reads what it answers. The endpoint runs each statement on one of its own threads, where
 * the embedded engine answers directly.
 *
 * <p>What the engine did with each statement is recorded there ({@link ServedStatement}) and audits what the driver
 * hands back. A refusal must reach the client in the engine's own words; the engine's own exception is then
 * rethrown, keeping its class, and a refusal worded otherwise fails as such. A column whose type the driver names as
 * the engine does keeps the engine's type, and a cell the driver reads back as the engine's value comes back as the
 * engine's cell, keeping its class; anything else comes back as the driver read it (see
 * {@link SnowflakeDriverRows}), so a real disagreement reaches the assertion. A DML grid keeps the engine's
 * affected-row marks. The session's database, schema and AUTOCOMMIT after each statement are pushed into the
 * engine, and its role where a USE ROLE moved it, so tests that read internals see the context the SQL left; a
 * transaction stays the endpoint session's.
 */
public class SnowflakeJdbcFlEngine extends DatabaseEngine {

    /** The threads of an HTTP server's request pool, and of the Snowflake endpoint's asynchronous queries. */
    private static final String SERVER_THREADS = "frostlake-http-";
    private static final String ASYNC_THREADS = "frostlake-snowflake-async";
    /** How far a FLOAT read from JSON text may stand from the engine's double, relative to its size. */
    private static final double FLOAT_TEXT_TOLERANCE = 1e-8;

    private SnowflakeEndpoint endpoint;
    private Connection connection;
    /** What the engine last did for the endpoint, set on the server's thread and read on the test's. */
    private volatile ServedStatement served;

    private String lastSyncedDatabase;
    private String lastSyncedSchema;
    private boolean lastSyncedAutoCommit = true;

    @Override
    public ExecutionResult execute(final String sql) {
        if (serving()) {
            return serve(sql);
        }
        if (Thread.holdsLock(this)) {
            // A client of the engine's own in this JVM - a direct connection runs its statements on the engine's
            // monitor - is answered by the engine itself: the endpoint, which runs its requests on that monitor too,
            // could not answer while this thread holds it.
            return super.execute(sql);
        }
        return executeOverDriver(sql);
    }

    /** Whether this thread is a server's, running a statement the driver sent: the engine answers it itself. */
    private static boolean serving() {
        final String name = Thread.currentThread().getName();
        return name.startsWith(SERVER_THREADS) || name.startsWith(ASYNC_THREADS);
    }

    /** Runs a statement for the endpoint, recording what it answered and the session's context after it. */
    private ExecutionResult serve(final String sql) {
        try {
            final ExecutionResult result = super.execute(sql);
            served = new ServedStatement(sql, result, null, getCurrentDatabase(), getCurrentSchema(), getCurrentRole(),
                getTransactionManager().isAutoCommit());
            return result;
        } catch (final RuntimeException failed) {
            served = new ServedStatement(sql, null, failed, getCurrentDatabase(), getCurrentSchema(), getCurrentRole(),
                getTransactionManager().isAutoCommit());
            throw failed;
        }
    }

    private ExecutionResult executeOverDriver(final String sql) {
        final List<ResultSet> results = new ArrayList<ResultSet>();
        String queryId = null;
        try {
            open();
            try (final Statement statement = connection.createStatement()) {
                syncEngineToSession(statement);
                // Each submission declares its own statement count, as the live and FL-over-JDBC harnesses' do, so a
                // script passes the gate and the session's count stays the session's.
                final int statements = StatementCount.countStatements(sql);
                final SnowflakeStatement driverStatement = statement.unwrap(SnowflakeStatement.class);
                driverStatement.setParameter("MULTI_STATEMENT_COUNT", statements);
                served = null;
                if (statements <= 1) {
                    // Every statement is read through executeQuery, which hands back the endpoint's result for it:
                    // the rows of a query, the count grid of a DML statement, the status sentence of anything else.
                    try (final java.sql.ResultSet rs = statement.executeQuery(sql)) {
                        results.add(engineShaped(sql, SnowflakeDriverRows.convert(rs), 0, true));
                    }
                } else {
                    readScript(statement, sql, statements, results);
                }
                queryId = driverStatement.getQueryID();
            }
            syncSessionToEngine();
        } catch (final SQLException e) {
            syncSessionToEngine();
            throw refusal(sql, e);
        }
        return new ExecutionResult(true, results, null, queryId);
    }

    /**
     * Reads a script's results, one for each of its statements, as the live harness reads them: a result set where
     * the driver says the statement answered rows, and otherwise the count it reports.
     */
    private void readScript(final Statement statement, final String sql, final int statements,
                            final List<ResultSet> results) throws SQLException {
        boolean answersRows = statement.execute(sql);
        for (int child = 0; child < statements; child++) {
            if (child > 0) {
                answersRows = statement.getMoreResults();
            }
            final java.sql.ResultSet rows = answersRows ? statement.getResultSet() : null;
            if (rows != null) {
                try (final java.sql.ResultSet rs = rows) {
                    results.add(engineShaped(sql, SnowflakeDriverRows.convert(rs), child, false));
                }
                continue;
            }
            final int count = statement.getUpdateCount();
            if (count >= 0) {
                results.add(countGrid(sql, count, child));
            }
        }
    }

    /**
     * A count the driver reported for a script's statement: the engine's own grid for it, as a copy of its own since
     * the endpoint has read the grid through, or a grid of the count.
     */
    private ResultSet countGrid(final String sql, final int count, final int child) {
        final ResultSet own = servedResult(sql, child);
        if (own != null) {
            return own.detachedCopy();
        }
        final List<ResultSetColumn> columns = new ArrayList<ResultSetColumn>();
        columns.add(new ResultSetColumn("number of rows affected", NumericType.NUMBER));
        final List<Object> values = new ArrayList<Object>();
        values.add(Long.valueOf(count));
        final List<Row> rows = new ArrayList<Row>();
        rows.add(new Row(values));
        return new ResultSet(columns, rows);
    }

    /** The engine's own result for a statement of the text the endpoint just ran, or null. */
    private ResultSet servedResult(final String sql, final int child) {
        final ServedStatement record = served;
        if (record == null || record.result() == null || !sql.equals(record.sql())
                || record.result().getResultSets() == null || record.result().getResultSets().size() <= child) {
            return null;
        }
        return record.result().getResultSets().get(child);
    }

    /**
     * The driver's reading of a result, with what agrees with the engine's own result taken from it: a column whose
     * type the driver names as the engine does, and a cell the driver reads back as the engine's value. The engine's
     * affected-row marks go with it; without the engine's result a single statement's DML grid is marked as the live
     * harness marks it.
     */
    private ResultSet engineShaped(final String sql, final ResultSet read, final int child, final boolean single) {
        final ResultSet own = servedResult(sql, child);
        if (own == null || own.getColumns().size() != read.getColumns().size()
                || own.getRows().size() != read.getRows().size()) {
            return single ? SnowflakeDriverRows.markAffectedRows(sql, read) : read;
        }
        final List<ResultSetColumn> columns = new ArrayList<ResultSetColumn>();
        for (int c = 0; c < read.getColumns().size(); c++) {
            final ResultSetColumn driverColumn = read.getColumns().get(c);
            final ResultSetColumn engineColumn = own.getColumns().get(c);
            columns.add(sameType(driverColumn, engineColumn) ? engineColumn : driverColumn);
        }
        // A TIMESTAMP_LTZ is its moment, which the driver shows at the session's offset.
        final boolean[] momentOnly = new boolean[read.getColumns().size()];
        for (int c = 0; c < momentOnly.length; c++) {
            momentOnly[c] = read.getColumns().get(c).getDataType() != null
                && "TIMESTAMP_LTZ".equals(typeName(read.getColumns().get(c)));
        }
        final List<Row> rows = new ArrayList<Row>();
        for (int r = 0; r < read.getRows().size(); r++) {
            final List<Object> values = new ArrayList<Object>();
            for (int c = 0; c < read.getColumns().size(); c++) {
                final Object driverValue = read.getRows().get(r).getValue(c);
                final Object engineValue = own.getRows().get(r).getValue(c);
                values.add(agrees(driverValue, engineValue, momentOnly[c]) ? engineValue : driverValue);
            }
            rows.add(new Row(values));
        }
        final ResultSet shaped = new ResultSet(columns, rows);
        if (own.getUpdateCount() != null) {
            shaped.markUpdateCount(own.getUpdateCount().longValue());
        }
        shaped.markJdbcUpdateCount(own.getJdbcUpdateCount());
        if (own.isScriptingAnswer()) {
            shaped.markScriptingAnswer();
        }
        return shaped;
    }

    /** Whether the driver's column names the engine's: the same label and the same type's name. */
    private static boolean sameType(final ResultSetColumn driverColumn, final ResultSetColumn engineColumn) {
        if (!driverColumn.getName().equalsIgnoreCase(engineColumn.getName())) {
            return false;
        }
        if (driverColumn.getDataType() == null || engineColumn.getDataType() == null) {
            return driverColumn.getDataType() == engineColumn.getDataType();
        }
        return typeName(driverColumn).equals(typeName(engineColumn));
    }

    private static String typeName(final ResultSetColumn column) {
        final String name = String.valueOf(column.getDataType().getName()).toUpperCase(Locale.ROOT);
        final int parameters = name.indexOf('(');
        final String family = parameters < 0 ? name : name.substring(0, parameters);
        switch (family) {
            case "DOUBLE":
            case "REAL":
            case "FLOAT4":
            case "FLOAT8":
                return "FLOAT";
            case "DECIMAL":
            case "NUMERIC":
            case "INT":
            case "INTEGER":
            case "BIGINT":
                return "NUMBER";
            case "TEXT":
            case "STRING":
            case "CHAR":
                return "VARCHAR";
            case "TIMESTAMP":
            case "DATETIME":
                return "TIMESTAMP_NTZ";
            default:
                return family;
        }
    }

    /**
     * Whether the driver read a cell back as the engine's value: equal, the same number (a FLOAT as near as JSON's text
     * of it), the same date and time to the digits the driver's value carries — a TIMESTAMP_LTZ the same moment,
     * whatever offset it is shown at — or the same text, a semi-structured cell by its client text.
     */
    private static boolean agrees(final Object driverValue, final Object engineValue, final boolean momentOnly) {
        if (driverValue == null || engineValue == null) {
            return driverValue == null && engineValue == null;
        }
        if (driverValue.equals(engineValue)) {
            return true;
        }
        if (driverValue instanceof Double && engineValue instanceof Number) {
            // JSON results carry a FLOAT as text of nine decimals or so, as the account's do.
            final double read = ((Double) driverValue).doubleValue();
            final double own = ((Number) engineValue).doubleValue();
            return read == own || Math.abs(read - own) <= FLOAT_TEXT_TOLERANCE * Math.max(1.0, Math.abs(own));
        }
        if (driverValue instanceof Number && engineValue instanceof Number) {
            try {
                return new BigDecimal(driverValue.toString()).compareTo(new BigDecimal(engineValue.toString())) == 0;
            } catch (final NumberFormatException notDecimal) {
                return driverValue.toString().equals(engineValue.toString());
            }
        }
        if (driverValue instanceof OffsetDateTime || driverValue instanceof ZonedDateTime) {
            final OffsetDateTime shown = driverValue instanceof OffsetDateTime ? (OffsetDateTime) driverValue
                : ((ZonedDateTime) driverValue).toOffsetDateTime();
            final OffsetDateTime held = engineValue instanceof OffsetDateTime ? (OffsetDateTime) engineValue
                : engineValue instanceof ZonedDateTime ? ((ZonedDateTime) engineValue).toOffsetDateTime() : null;
            if (held == null) {
                return false;
            }
            final OffsetDateTime read = momentOnly ? shown.withOffsetSameInstant(ZoneOffset.UTC) : shown;
            final OffsetDateTime own = momentOnly ? held.withOffsetSameInstant(ZoneOffset.UTC) : held;
            return read.getOffset().equals(own.getOffset())
                && read.toLocalDateTime().equals(cutTo(own.toLocalDateTime(), read.getNano()));
        }
        if (driverValue instanceof LocalDateTime) {
            final LocalDateTime local = engineValue instanceof OffsetDateTime
                ? ((OffsetDateTime) engineValue).toLocalDateTime()
                : engineValue instanceof ZonedDateTime ? ((ZonedDateTime) engineValue).toLocalDateTime()
                : engineValue instanceof LocalDateTime ? (LocalDateTime) engineValue
                : engineValue instanceof LocalDate ? ((LocalDate) engineValue).atStartOfDay() : null;
            return local != null && driverValue.equals(cutTo(local, ((LocalDateTime) driverValue).getNano()));
        }
        if (driverValue instanceof LocalTime && engineValue instanceof LocalTime) {
            // A driver TIME carries whole seconds.
            final LocalTime own = (LocalTime) engineValue;
            final int unit = precisionOf(((LocalTime) driverValue).getNano());
            return driverValue.equals(own.withNano(own.getNano() - own.getNano() % unit));
        }
        if (engineValue instanceof VariantValue) {
            final VariantValue variant = (VariantValue) engineValue;
            final JsonNode node = variant.node();
            if (node != null && node.isString()) {
                return node.asString().equals(driverValue.toString());
            }
            return VariantJsonText.clientTextOf(variant).equals(driverValue.toString());
        }
        return driverValue.toString().equals(engineValue.toString());
    }

    /** A date and time cut to the fractional digits a value the driver read carries. */
    private static LocalDateTime cutTo(final LocalDateTime value, final int readNanos) {
        return value.withNano(value.getNano() - value.getNano() % precisionOf(readNanos));
    }

    /** The unit, in nanoseconds, of the last fractional digit a value carries: a second for none, down to one. */
    private static int precisionOf(final int nanos) {
        int unit = 1_000_000_000;
        while (unit > 1 && nanos % unit != 0) {
            unit /= 10;
        }
        return unit;
    }

    /**
     * The exception a refused statement throws: the engine's own where the driver reported it in the engine's words,
     * a failure naming both where it reported otherwise, and the driver's where the engine never ran the statement.
     */
    private RuntimeException refusal(final String sql, final SQLException e) {
        final ServedStatement record = served;
        if (record == null || !sql.equals(record.sql())) {
            return new RuntimeException(e.getMessage(), e);
        }
        if (record.failure() == null) {
            return new IllegalStateException("The Snowflake driver failed on the endpoint's answer: " + e.getMessage(),
                e);
        }
        final String told = e.getMessage();
        final String own = record.failure().getMessage();
        if (own == null ? told != null : !own.equals(told)) {
            return new IllegalStateException("The Snowflake driver reported \"" + told
                + "\" where the engine refused the statement with \"" + own + "\"", e);
        }
        return record.failure();
    }

    /** Starts the endpoint over this engine and logs the driver in, once. */
    private void open() throws SQLException {
        if (connection != null) {
            return;
        }
        try {
            endpoint = SnowflakeEndpoint.start(this);
        } catch (final IOException e) {
            throw new IllegalStateException("Cannot start the Snowflake endpoint: " + e.getMessage(), e);
        }
        connection = SnowflakeJdbcFl.connect(endpoint);
    }

    // The session's context is the endpoint's, while tests read and move the engine's own between statements. After a
    // statement the session is authoritative — what the endpoint recorded is pushed into the engine — and before one
    // the engine wins only where internals moved it since the last push.
    private void syncEngineToSession(final Statement statement) throws SQLException {
        final String database = getCurrentDatabase();
        if (database != null && !database.equals(lastSyncedDatabase)) {
            statement.execute("USE DATABASE IDENTIFIER('" + database.replace("'", "''") + "')");
        }
        final String schema = getCurrentSchema();
        if (database != null && schema != null && !schema.equals(lastSyncedSchema)) {
            statement.execute("USE SCHEMA IDENTIFIER('" + schema.replace("'", "''") + "')");
        }
        final boolean autoCommit = getTransactionManager().isAutoCommit();
        if (autoCommit != lastSyncedAutoCommit) {
            statement.execute("ALTER SESSION SET AUTOCOMMIT = " + (autoCommit ? "TRUE" : "FALSE"));
        }
    }

    private void syncSessionToEngine() {
        final ServedStatement record = served;
        if (record == null) {
            return;
        }
        lastSyncedDatabase = record.database();
        lastSyncedSchema = record.schema();
        getCatalog().restoreContext(lastSyncedDatabase, lastSyncedSchema);
        if (record.role() != null && !record.role().equals(getCurrentRole())) {
            // A USE ROLE moved the session's role: the engine's own session follows, so a test that builds what it
            // expects from the engine's role builds it from the session's.
            getSessionContext().setCurrentRole(record.role());
        }
        lastSyncedAutoCommit = record.autoCommit();
        getTransactionManager().setAutoCommit(lastSyncedAutoCommit);
    }

    /** Closes the driver's session, which ends it on the endpoint, then the endpoint and the engine. */
    @Override
    public void shutdown() {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (final SQLException e) {
            // Closing the driver's session must not mask the engine's shutdown.
        } finally {
            if (endpoint != null) {
                SnowflakeJdbcFl.closeOnServerThread(endpoint);
            }
            super.shutdown();
        }
    }
}
