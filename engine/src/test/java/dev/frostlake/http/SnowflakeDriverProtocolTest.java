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
package dev.frostlake.http;

import dev.frostlake.LiveSnowflake;
import dev.frostlake.config.EngineConfig;
import net.snowflake.client.api.connection.SnowflakeConnection;
import net.snowflake.client.api.resultset.QueryStatus;
import net.snowflake.client.api.resultset.SnowflakeResultSet;
import net.snowflake.client.api.statement.SnowflakeStatement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * The Snowflake JDBC driver, unchanged, connected to this server by its address:
 * {@code jdbc:snowflake://127.0.0.1:<port>/?account=<any>&ssl=off}. Its login opens a session in the database and
 * schema it names; DDL and DML answer the counts the driver reads from their rows; a query answers its columns' types
 * and values in the driver's JSON result form; a refusal carries its SQLSTATE and vendor code; prepared statements bind
 * their values and batch; a text of several statements answers each result in order; and the driver's metadata calls
 * run. Engine only: the server under test is the local one.
 */
public class SnowflakeDriverProtocolTest {

    private static final Logger logger = LoggerFactory.getLogger(SnowflakeDriverProtocolTest.class);
    private static DatabaseHttpServer server;
    private static String url;
    private static Path stageRoot;

    @BeforeAll
    public static void startServer() throws IOException {
        assumeFalse(LiveSnowflake.enabled(), "the server under test is the local one");
        final int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        stageRoot = Files.createTempDirectory("sfp-stages");
        final EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_HTTP_PORT, String.valueOf(port));
        config.setProperty(EngineConfig.PROP_STAGE_INTERNAL_LOCAL_ROOT, stageRoot.toString());
        server = new DatabaseHttpServer(config);
        server.start();
        url = "jdbc:snowflake://127.0.0.1:" + port + "/?account=frostlake&ssl=off";
    }

    @AfterAll
    public static void stopServer() {
        if (server != null) {
            server.stop();
        }
        if (stageRoot != null) {
            deleteRecursively(stageRoot.toFile());
        }
    }

    private static void deleteRecursively(final File file) {
        final File[] children = file.listFiles();
        if (children != null) {
            for (final File child : children) {
                deleteRecursively(child);
            }
        }
        if (!file.delete()) {
            logger.debug("Could not delete {}", file);
        }
    }

    @BeforeEach
    public void createDatabase() throws SQLException {
        try (Connection connection = connect(null);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE OR REPLACE DATABASE sfp_db");
        }
    }

    private static Connection connect(final String database) throws SQLException {
        final Properties properties = new Properties();
        properties.put("user", "any_user");
        properties.put("password", "any_password");
        properties.put("disablePlatformDetection", "true");
        if (database != null) {
            properties.put("db", database);
            properties.put("schema", "public");
        }
        return DriverManager.getConnection(url, properties);
    }

    /** A result's rows, each cell as getString reads it, joined by {@code |}. */
    private static List<String> rows(final ResultSet rs) throws SQLException {
        final List<String> rows = new ArrayList<>();
        final ResultSetMetaData metaData = rs.getMetaData();
        while (rs.next()) {
            final StringBuilder row = new StringBuilder();
            for (int i = 1; i <= metaData.getColumnCount(); i++) {
                row.append(i > 1 ? "|" : "").append(rs.getString(i));
            }
            rows.add(row.toString());
        }
        return rows;
    }

    @Test
    public void theLoginOpensASessionInTheNamespaceItNames() throws SQLException {
        try (Connection connection = connect("sfp_db")) {
            assertEquals("SFP_DB", connection.getCatalog());
            assertEquals("PUBLIC", connection.getSchema());
        }
    }

    @Test
    public void statementsAnswerTheirCountsAndTheirValues() throws SQLException {
        try (Connection connection = connect("sfp_db");
             Statement statement = connection.createStatement()) {
            assertEquals(0, statement.executeUpdate("CREATE TABLE t (id INT, amount NUMBER(10,2), ok BOOLEAN, d DATE, "
                + "ts TIMESTAMP_NTZ, tz TIMESTAMP_TZ, v VARIANT, b BINARY)"));
            assertEquals(1, statement.executeUpdate("INSERT INTO t SELECT 1, 1.5, TRUE, '2024-02-29', "
                + "'2024-02-29 12:34:56.123', '2024-02-29 12:34:56.5 +05:30', PARSE_JSON('[1]'), "
                + "TO_BINARY('ABCD', 'HEX')"));
            assertEquals(2, statement.executeUpdate("INSERT INTO t (id) VALUES (2), (3)"));
            assertEquals(2, statement.executeUpdate("UPDATE t SET ok = FALSE WHERE id > 1"));
            assertEquals(1, statement.executeUpdate("DELETE FROM t WHERE id = 3"));
            try (ResultSet rs = statement.executeQuery("SELECT * FROM t ORDER BY id")) {
                final ResultSetMetaData metaData = rs.getMetaData();
                assertEquals("NUMBER", metaData.getColumnTypeName(2));
                assertEquals(2, metaData.getScale(2));
                assertEquals("TIMESTAMPNTZ", metaData.getColumnTypeName(5));
                assertEquals(List.of(
                    "1|1.50|TRUE|2024-02-29|2024-02-29 12:34:56.123|2024-02-29 12:34:56.500 +0530|[\n  1\n]|ABCD",
                    "2|null|FALSE|null|null|null|null|null"), rows(rs));
            }
        }
    }

    @Test
    public void aRefusalCarriesItsStateAndCode() throws SQLException {
        try (Connection connection = connect("sfp_db");
             Statement statement = connection.createStatement()) {
            final SQLException missing = assertThrows(SQLException.class, new Executable() {
                @Override
                public void execute() throws SQLException {
                    statement.executeQuery("SELECT * FROM no_such_table");
                }
            });
            assertEquals("42S02", missing.getSQLState());
            assertEquals(2003, missing.getErrorCode());
            final SQLException several = assertThrows(SQLException.class, new Executable() {
                @Override
                public void execute() throws SQLException {
                    statement.execute("SELECT 1; SELECT 2");
                }
            });
            assertEquals("0A000", several.getSQLState());
            assertEquals(8, several.getErrorCode());
        }
    }

    @Test
    public void preparedStatementsBindTheirValuesAndBatch() throws SQLException {
        try (Connection connection = connect("sfp_db")) {
            try (PreparedStatement select = connection.prepareStatement("SELECT ?, ?, ? WHERE ? > 1")) {
                select.setInt(1, 42);
                select.setString(2, "it's");
                select.setDate(3, Date.valueOf("2024-01-02"));
                select.setLong(4, 2);
                try (ResultSet rs = select.executeQuery()) {
                    assertEquals(List.of("42|it's|2024-01-02"), rows(rs));
                }
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE p (id INT, name VARCHAR)");
            }
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO p VALUES (?, ?)")) {
                insert.setInt(1, 1);
                insert.setString(2, "one");
                assertEquals(1, insert.executeUpdate());
                insert.setInt(1, 2);
                insert.setString(2, "two");
                insert.addBatch();
                insert.setInt(1, 3);
                insert.setString(2, "three");
                insert.addBatch();
                assertArrayEquals(new int[] {1, 1}, insert.executeBatch());
            }
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT id, name FROM p ORDER BY id")) {
                assertEquals(List.of("1|one", "2|two", "3|three"), rows(rs));
            }
        }
    }

    @Test
    public void aTextOfSeveralStatementsAnswersEachResultInOrder() throws SQLException {
        try (Connection connection = connect("sfp_db");
             Statement statement = connection.createStatement()) {
            statement.execute("ALTER SESSION SET MULTI_STATEMENT_COUNT = 0");
            statement.execute("SELECT 1; CREATE TABLE m (x INT); INSERT INTO m VALUES (1), (2); SELECT x FROM m "
                + "ORDER BY x");
            final List<String> answers = new ArrayList<>();
            while (true) {
                final ResultSet rs = statement.getResultSet();
                if (rs != null) {
                    answers.add(String.join(",", rows(rs)));
                    rs.close();
                } else if (statement.getUpdateCount() != -1) {
                    answers.add("count " + statement.getUpdateCount());
                } else {
                    break;
                }
                if (!statement.getMoreResults() && statement.getUpdateCount() == -1) {
                    break;
                }
            }
            assertEquals(List.of("1", "count 0", "count 2", "1,2"), answers);
        }
    }

    @Test
    public void theDriversMetadataCallsRun() throws SQLException {
        try (Connection connection = connect("sfp_db");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE listed (x INT)");
            try (ResultSet rs = connection.getMetaData().getTables("SFP_DB", "PUBLIC", "LISTED", null)) {
                final List<String> found = rows(rs);
                assertEquals(1, found.size());
                assertTrue(found.get(0).startsWith("SFP_DB|PUBLIC|LISTED|TABLE|"), found.get(0));
            }
        }
    }

    /** A prepared statement's columns and parameters, as getMetaData and getParameterMetaData read them. */
    private static String described(final Connection connection, final String sql) throws SQLException {
        try (PreparedStatement prepared = connection.prepareStatement(sql)) {
            final StringBuilder out = new StringBuilder();
            final ResultSetMetaData metaData = prepared.getMetaData();
            out.append(metaData.getColumnCount());
            for (int i = 1; i <= metaData.getColumnCount(); i++) {
                out.append(" [").append(metaData.getColumnLabel(i)).append(':').append(metaData.getColumnTypeName(i))
                    .append('(').append(metaData.getPrecision(i)).append(',').append(metaData.getScale(i))
                    .append(") null=").append(metaData.isNullable(i)).append(']');
            }
            final ParameterMetaData parameters = prepared.getParameterMetaData();
            out.append(" params=").append(parameters.getParameterCount());
            for (int i = 1; i <= parameters.getParameterCount(); i++) {
                out.append(' ').append(parameters.getParameterTypeName(i));
            }
            return out.toString();
        }
    }

    @Test
    public void aPreparedStatementIsDescribedBeforeItRuns() throws SQLException {
        try (Connection connection = connect("sfp_db")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE d (x INT NOT NULL, s VARCHAR(10), d DATE, n NUMBER(10,2))");
            }
            assertEquals("2 [X:NUMBER(38,0) null=0] [Y:NUMBER(38,0) null=0] params=0",
                described(connection, "SELECT x, x + 1 AS y FROM d"));
            assertEquals("4 [X:NUMBER(38,0) null=0] [S:VARCHAR(10,0) null=1] [D:DATE(10,0) null=1] "
                + "[N:NUMBER(10,2) null=1] params=0", described(connection, "SELECT * FROM d"));
            assertEquals("1 [P:VARCHAR(1,0) null=0] params=1 text", described(connection, "SELECT ? AS p"));
            assertEquals("1 [X:NUMBER(38,0) null=0] params=2 text text",
                described(connection, "SELECT x FROM d WHERE x = ? AND s = ?"));
            assertEquals("2 [P:NUMBER(5,1) null=0] [Q:DATE(10,0) null=0] params=2 text text",
                described(connection, "SELECT ?::NUMBER(5,1) AS p, ?::DATE AS q"));
            assertEquals("2 [P:VARCHAR(1,0) null=0] [Q:VARCHAR(1,0) null=0] params=2 text text",
                described(connection, "SELECT :1 AS p, :2 AS q"));
            assertEquals("2 [T:VARCHAR(6,0) null=0] [P:VARCHAR(1,0) null=0] params=1 text",
                described(connection, "SELECT 'it''s ?' AS t, ? AS p"));
            assertEquals("1 [P:VARCHAR(1,0) null=0] params=1 text",
                described(connection, "SELECT ? AS p -- a ? in a comment"));
            assertEquals("1 [number of rows inserted:NUMBER(19,0) null=0] params=2 text text",
                described(connection, "INSERT INTO d (x, s) VALUES (?, ?)"));
            assertEquals("2 [number of rows updated:NUMBER(19,0) null=0] "
                + "[number of multi-joined rows updated:NUMBER(19,0) null=0] params=2 text text",
                described(connection, "UPDATE d SET s = ? WHERE x = ?"));
            assertEquals("1 [number of rows deleted:NUMBER(19,0) null=0] params=1 text",
                described(connection, "DELETE FROM d WHERE x = ?"));
            assertEquals("1 [number of rows updated:NUMBER(19,0) null=0] params=1 text", described(connection,
                "MERGE INTO d t USING (SELECT ? AS k) v ON t.x = v.k WHEN MATCHED THEN UPDATE SET s = 'm'"));
            assertEquals("1 [status:VARCHAR(16777216,0) null=1] params=0",
                described(connection, "CREATE TABLE d2 (a INT)"));
            assertEquals("1 [status:VARCHAR(16777216,0) null=1] params=0",
                described(connection, "USE SCHEMA sfp_db.public"));
            assertEquals("0 params=0", described(connection, "ALTER SESSION SET TIMEZONE = 'UTC'"));
            assertEquals("1 [C:NUMBER(18,0) null=0] params=1 text",
                described(connection, "SELECT COUNT(*) AS c FROM d WHERE n > ?"));
            // Nothing a describe compiles runs: no table was made, and d holds no row.
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM d")) {
                assertEquals(List.of("0"), rows(rs));
            }
            assertThrows(SQLException.class, new Executable() {
                @Override
                public void execute() throws SQLException {
                    connection.createStatement().executeQuery("SELECT * FROM d2");
                }
            });
        }
    }

    @Test
    public void aDescribeRefusesWhatWouldNotCompile() throws SQLException {
        try (Connection connection = connect("sfp_db")) {
            final SQLException missing = assertThrows(SQLException.class, new Executable() {
                @Override
                public void execute() throws SQLException {
                    described(connection, "SELECT x FROM no_such_table");
                }
            });
            assertEquals("42S02", missing.getSQLState());
            assertEquals(2003, missing.getErrorCode());
            final SQLException several = assertThrows(SQLException.class, new Executable() {
                @Override
                public void execute() throws SQLException {
                    described(connection, "SELECT 1 AS a; SELECT 2 AS b");
                }
            });
            assertEquals("Actual statement count 2 did not match the desired statement count 1.",
                several.getMessage());
            final SQLException syntax = assertThrows(SQLException.class, new Executable() {
                @Override
                public void execute() throws SQLException {
                    described(connection, "SELECT 1 x y");
                }
            });
            assertEquals("SQL compilation error:\nsyntax error line 1 at position 11 unexpected 'y'.",
                syntax.getMessage());
        }
    }

    /** A batch of rows bound with setObject, and the counts executeBatch answers, or its refusal's message. */
    private static String batch(final Connection connection, final String sql, final Object[]... rows) {
        try (PreparedStatement prepared = connection.prepareStatement(sql)) {
            for (final Object[] row : rows) {
                for (int i = 0; i < row.length; i++) {
                    prepared.setObject(i + 1, row[i]);
                }
                prepared.addBatch();
            }
            return Arrays.toString(prepared.executeBatch());
        } catch (final SQLException refused) {
            return "REFUSED " + refused.getMessage();
        }
    }

    @Test
    public void anInsertBatchIsBoundAsArraysInOneStatement() throws SQLException {
        try (Connection connection = connect("sfp_db")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE b (x INT NOT NULL, s VARCHAR(10))");
            }
            final String insert = "INSERT INTO b (x, s) VALUES (?, ?)";
            assertEquals("[1, 1, 1]", batch(connection, insert, new Object[] {10, "p"}, new Object[] {11, "q"},
                new Object[] {12, "r"}));
            // One statement: a row no column takes refuses the whole batch, and no row of it is inserted.
            assertEquals("REFUSED DML operation to table B failed on column X with error: NULL result in a "
                + "non-nullable column", batch(connection, insert, new Object[] {20, "u"}, new Object[] {null, "v"},
                new Object[] {22, "w"}));
            assertEquals("REFUSED DML operation to table B failed on column X with error: Numeric value 'zz' is not "
                + "recognized", batch(connection, insert, new Object[] {"30", "a"}, new Object[] {"zz", "b"}));
            // Only a whole value of the first row is bound.
            assertEquals("REFUSED SQL compilation error: error line 1 at position 26\nBind variable ? not set.",
                batch(connection, "INSERT INTO b (x) VALUES (? + 1)", new Object[] {90}, new Object[] {91}));
            assertEquals("REFUSED SQL compilation error: error line 1 at position 37\nBind variable ? not set.",
                batch(connection, "INSERT INTO b (x, s) VALUES (?, ?), (?, 'k')", new Object[] {70, "a", 71},
                    new Object[] {72, "b", 73}));
            assertEquals("[1, 1]", batch(connection, "INSERT INTO b (x, s) VALUES (:1, :2)", new Object[] {80, "a"},
                new Object[] {81, "b"}));
            assertEquals("[1, 1]", batch(connection, "INSERT INTO b (x, s) VALUES (?, 'z')", new Object[] {100},
                new Object[] {101}));
            // An UPDATE is sent row by row, so each row answers its own count.
            assertEquals("[1, 0, 1]", batch(connection, "UPDATE b SET s = ? WHERE x = ?", new Object[] {"A", 10},
                new Object[] {"B", 99}, new Object[] {"C", 11}));
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT x, s FROM b ORDER BY x")) {
                assertEquals(List.of("10|A", "11|C", "12|r", "80|a", "81|b", "100|z", "101|z"), rows(rs));
            }
            // INSERT OVERWRITE takes the whole batch at once, so every row of it stays.
            assertEquals("[1, 1]", batch(connection, "INSERT OVERWRITE INTO b (x, s) VALUES (?, ?)",
                new Object[] {110, "a"}, new Object[] {111, "b"}));
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT x, s FROM b ORDER BY x")) {
                assertEquals(List.of("110|a", "111|b"), rows(rs));
            }
        }
    }

    /** Every row of a query, each cell as getString reads it and the class getObject answers, per column. */
    private static List<String> readings(final Connection connection, final String sql) throws SQLException {
        final List<String> out = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            final ResultSetMetaData metaData = rs.getMetaData();
            while (rs.next()) {
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    final Object object = rs.getObject(i);
                    out.add(metaData.getColumnLabel(i) + "=" + rs.getString(i) + " ("
                        + (object == null ? "null" : object.getClass().getSimpleName()) + ")");
                }
            }
        }
        return out;
    }

    @Test
    public void resultsComeAsArrowUnlessTheSessionAsksForJson() throws SQLException {
        try (Connection connection = connect("sfp_db");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE a (n NUMBER(38,0), d NUMBER(10,3), f FLOAT, b BOOLEAN, dt DATE, "
                + "tm TIME(3), ntz TIMESTAMP_NTZ(9), ltz TIMESTAMP_LTZ(3), tz TIMESTAMP_TZ(6), v VARIANT, "
                + "bin BINARY, df DECFLOAT, s VARCHAR)");
            statement.execute("INSERT INTO a SELECT 1, 1.5, 0.1::FLOAT + 0.2::FLOAT, TRUE, '2024-02-29', "
                + "'12:34:56.789', '2024-02-29 12:34:56.123456789', '1969-12-31 23:59:59.5 +00:00', "
                + "'2024-02-29 12:34:56.5 +05:30', PARSE_JSON('{\"k\": [1, 2]}'), TO_BINARY('ABCD', 'HEX'), "
                + "'-1.25e-3'::DECFLOAT, 'it''s'");
            statement.execute("INSERT INTO a (n, s) VALUES (12345678901234567890123, NULL), (NULL, '')");
            final String query = "SELECT * FROM a ORDER BY n NULLS LAST";
            final List<String> arrow = readings(connection, query);
            statement.execute("ALTER SESSION SET JDBC_QUERY_RESULT_FORMAT = 'JSON'");
            final List<String> json = readings(connection, query);
            assertEquals(json.size(), arrow.size());
            for (int i = 0; i < json.size(); i++) {
                if (json.get(i).startsWith("F=0.3")) {
                    // A FLOAT reads as Java's text of the double on Arrow results, as on the account's default.
                    assertEquals("F=0.30000000000000004 (Double)", arrow.get(i));
                    assertEquals("F=0.3 (Double)", json.get(i));
                } else if ("N=1 (Long)".equals(json.get(i))) {
                    // A batch holding a number no 64-bit integer holds carries the whole column as decimals, so every
                    // whole number of it reads as a BigDecimal, as on the account (live-verified).
                    assertEquals("N=1 (BigDecimal)", arrow.get(i));
                } else {
                    assertEquals(json.get(i), arrow.get(i));
                }
            }
            assertTrue(arrow.contains("N=12345678901234567890123 (BigDecimal)"), arrow.toString());
            assertTrue(arrow.contains("NTZ=2024-02-29 12:34:56.123 (Timestamp)"), arrow.toString());
            assertTrue(arrow.contains("BIN=ABCD (byte[])"), arrow.toString());
            assertTrue(arrow.contains("DF=-0.00125 (BigDecimal)"), arrow.toString());
            assertEquals(List.of("N=1 (Long)", "N=2 (Long)"),
                readings(connection, "SELECT column1 AS n FROM VALUES (1), (2)"));
            statement.execute("ALTER SESSION UNSET JDBC_QUERY_RESULT_FORMAT");
            assertEquals(List.of("X=0.30000000000000004 (Double)"),
                readings(connection, "SELECT 0.1::FLOAT + 0.2::FLOAT AS x"));
            assertEquals(List.of(), readings(connection, "SELECT * FROM a WHERE FALSE"));
        }
        // A connection that names the JSON format among its properties reads JSON results from its first statement.
        final Properties properties = new Properties();
        properties.put("user", "any_user");
        properties.put("password", "any_password");
        properties.put("disablePlatformDetection", "true");
        properties.put("JDBC_QUERY_RESULT_FORMAT", "JSON");
        try (Connection json = DriverManager.getConnection(url, properties)) {
            assertEquals(List.of("X=0.3 (Double)"), readings(json, "SELECT 0.1::FLOAT + 0.2::FLOAT AS x"));
        }
    }

    @Test
    public void putAndGetMoveFilesThroughTheStage() throws Exception {
        final Path work = Files.createTempDirectory("sfp-put");
        try {
            final Path source = work.resolve("p4.csv");
            Files.write(source, "1,a\n2,b\n".getBytes(StandardCharsets.UTF_8));
            final Path out = work.resolve("out");
            try (Connection connection = connect("sfp_db");
                 Statement statement = connection.createStatement()) {
                statement.execute("CREATE STAGE st");
                try (ResultSet rs = statement.executeQuery("PUT 'file://" + source + "' @st/sub/")) {
                    assertTrue(rs.next());
                    assertEquals("p4.csv", rs.getString("source"));
                    assertEquals("p4.csv.gz", rs.getString("target"));
                    assertEquals("GZIP", rs.getString("target_compression"));
                    assertEquals("UPLOADED", rs.getString("status"));
                }
                try (ResultSet rs = statement.executeQuery("SELECT $1, $2 FROM @st/sub/ ORDER BY 1")) {
                    assertEquals(List.of("1|a", "2|b"), rows(rs));
                }
                try (ResultSet rs = statement.executeQuery("LIST @st")) {
                    assertTrue(rs.next());
                    assertEquals("st/sub/p4.csv.gz", rs.getString(1));
                }
                try (ResultSet rs = statement.executeQuery("GET @st/sub/ 'file://" + out + "'")) {
                    assertTrue(rs.next());
                    assertEquals("sub/p4.csv.gz", rs.getString("file"));
                    assertEquals("DOWNLOADED", rs.getString("status"));
                }
                try (InputStream in = new GZIPInputStream(Files.newInputStream(out.resolve("p4.csv.gz")))) {
                    assertEquals("1,a\n2,b\n", new String(in.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        } finally {
            deleteRecursively(work.toFile());
        }
    }

    @Test
    public void anAsynchronousQueryIsFollowedByItsId() throws SQLException {
        try (Connection connection = connect("sfp_db");
             Statement statement = connection.createStatement()) {
            final SnowflakeConnection snowflake = connection.unwrap(SnowflakeConnection.class);
            final ResultSet async = statement.unwrap(SnowflakeStatement.class).executeAsyncQuery("SELECT 41 + 1 AS x");
            assertEquals(List.of("42"), rows(async));
            final String asyncId = async.unwrap(SnowflakeResultSet.class).getQueryID();
            assertEquals("SUCCESS", snowflake.getQueryStatus(asyncId).getName());
            // An earlier query's rows read again by its query ID.
            final String earlier;
            try (ResultSet first = statement.executeQuery("SELECT 7 AS y")) {
                earlier = first.unwrap(SnowflakeResultSet.class).getQueryID();
            }
            assertEquals(List.of("7"), rows(snowflake.createResultSet(earlier)));
            // An asynchronous statement that fails is told as failed, with its error.
            final ResultSet failing = statement.unwrap(SnowflakeStatement.class)
                .executeAsyncQuery("SELECT * FROM no_such_table");
            final SQLException refused = assertThrows(SQLException.class, new Executable() {
                @Override
                public void execute() throws SQLException {
                    rows(failing);
                }
            });
            assertTrue(refused.getMessage().contains("FAILED_WITH_ERROR"), refused.getMessage());
            final QueryStatus failed = snowflake.getQueryStatus(failing.unwrap(SnowflakeResultSet.class).getQueryID());
            assertEquals("FAILED_WITH_ERROR", failed.getName());
            assertEquals(2003, failed.getErrorCode());
        }
    }
    @Test
    public void vectorsIntervalsAndStructuredValuesReadAsOnTheAccount() throws SQLException {
        try (Connection connection = connect("sfp_db");
             Statement statement = connection.createStatement()) {
            // Arrow, the driver's default: a VECTOR as its elements, an interval as a Period or a Duration where it
            // is stored in 64 bits or fewer and as its nanoseconds where wider, as the account answers (live-verified).
            assertEquals(List.of("V=[1.5,2.0] (String)"),
                readings(connection, "SELECT [1.5, 2]::VECTOR(FLOAT, 2) AS v"));
            assertEquals(List.of("V=[1,2,3] (String)"), readings(connection, "SELECT [1, 2, 3]::VECTOR(INT, 3) AS v"));
            assertEquals(List.of("V=null (null)"), readings(connection, "SELECT NULL::VECTOR(INT, 3) AS v"));
            assertEquals(List.of("I=P1Y2M (Period)"),
                readings(connection, "SELECT '1-2'::INTERVAL YEAR TO MONTH AS i"));
            assertEquals(List.of("I=P1Y2M (Period)"),
                readings(connection, "SELECT '1-2'::INTERVAL YEAR(3) TO MONTH AS i"));
            assertEquals(List.of("I=P5M (Period)"), readings(connection, "SELECT '5'::INTERVAL MONTH AS i"));
            assertEquals(List.of("I=93784500000000 (BigDecimal)"),
                readings(connection, "SELECT '1 02:03:04.5'::INTERVAL DAY TO SECOND AS i"));
            assertEquals(List.of("I=PT26H3M4.5S (Duration)"),
                readings(connection, "SELECT '1 02:03:04.5'::INTERVAL DAY(2) TO SECOND(3) AS i"));
            assertEquals(List.of("I=PT1.25S (Duration)"), readings(connection, "SELECT '1.25'::INTERVAL SECOND AS i"));
            assertEquals(List.of("I=null (null)", "J=null (null)"), readings(connection,
                "SELECT NULL::INTERVAL DAY TO SECOND AS i, NULL::INTERVAL YEAR TO MONTH AS j"));
            try (ResultSet rs = statement.executeQuery("SELECT [1.5, 2]::VECTOR(FLOAT, 2) AS v, "
                    + "'1 02:03:04.5'::INTERVAL DAY TO SECOND AS i, '1-2'::INTERVAL YEAR TO MONTH AS j")) {
                final ResultSetMetaData metaData = rs.getMetaData();
                assertEquals("VECTOR", metaData.getColumnTypeName(1));
                assertEquals(50003, metaData.getColumnType(1));
                assertEquals("INTERVAL_DAY_TIME", metaData.getColumnTypeName(2));
                assertEquals(50006, metaData.getColumnType(2));
                assertEquals(3, metaData.getScale(2));
                assertEquals("INTERVAL_YEAR_MONTH", metaData.getColumnTypeName(3));
                assertEquals(50005, metaData.getColumnType(3));
                assertTrue(rs.next());
                assertEquals("FLOAT", rs.getArray(1).getBaseTypeName());
                assertEquals("[1.5, 2.0]", Arrays.toString((Object[]) rs.getArray(1).getArray()));
            }
            // A structured value reads as the semi-structured value it holds, laid out at the account's default
            // JSON_INDENT of 2 while the session sets none.
            final List<String> structured = List.of("A=[\n  1,\n  2\n] (String)", "O={\n  \"k\": 1\n} (String)",
                "M={\n  \"a\": 1\n} (String)");
            final String structuredQuery = "SELECT [1, 2]::ARRAY(INT) AS a, {'k': 1}::OBJECT(k INT) AS o, "
                + "{'a': 1}::MAP(VARCHAR, INT) AS m";
            assertEquals(structured, readings(connection, structuredQuery));
            assertEquals(List.of("2"), values(statement, "SHOW PARAMETERS LIKE 'JSON_INDENT' IN SESSION", "value"));
            statement.execute("ALTER SESSION SET JSON_INDENT = 0");
            assertEquals(List.of("A=[1,2] (String)", "O={\"k\":1} (String)", "M={\"a\":1} (String)"),
                readings(connection, structuredQuery));
            statement.execute("ALTER SESSION UNSET JSON_INDENT");
            // JSON results: a VECTOR's text with six decimals, an interval's months or nanoseconds.
            statement.execute("ALTER SESSION SET JDBC_QUERY_RESULT_FORMAT = 'JSON'");
            assertEquals(structured, readings(connection, structuredQuery));
            assertEquals(List.of("V=[1.500000,2.000000] (String)"),
                readings(connection, "SELECT [1.5, 2]::VECTOR(FLOAT, 2) AS v"));
            assertEquals(List.of("14"), values(statement, "SELECT '1-2'::INTERVAL YEAR TO MONTH AS i", "I"));
            assertEquals(List.of("-14"), values(statement, "SELECT '-1-2'::INTERVAL YEAR TO MONTH AS i", "I"));
            assertEquals(List.of("-93784500000000"),
                values(statement, "SELECT '-1 02:03:04.5'::INTERVAL DAY TO SECOND AS i", "I"));
            assertEquals(List.of("1250000000"), values(statement, "SELECT '1.25'::INTERVAL SECOND AS i", "I"));
        }
    }

    /** One column's cells of a query, as getString reads them. */
    private static List<String> values(final Statement statement, final String sql, final String column)
            throws SQLException {
        final List<String> out = new ArrayList<>();
        try (ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                out.add(rs.getString(column));
            }
        }
        return out;
    }
}
