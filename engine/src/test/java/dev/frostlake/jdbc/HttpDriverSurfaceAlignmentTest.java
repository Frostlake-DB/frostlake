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

import dev.frostlake.http.DatabaseHttpServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The HTTP transport answers as the direct one does for the surfaces the direct tests pin to the account's driver:
 * metadata patterns with their escape and case ({@code MetadataPatternMatchingTest}), the nullability of an
 * expression column ({@code ResultSetMetaDataNullableTest}), {@code getMoreResults(int)} and the statement's close
 * ({@code StatementResultLifecycleTest}), a prepared query behind a comment ({@code PreparedDescribeLeadingTokensTest})
 * and a scaled {@code getBigDecimal} ({@code ScaledBigDecimalReadTest}).
 */
public class HttpDriverSurfaceAlignmentTest {

    private static DatabaseHttpServer server;
    private static String httpUrl;

    private Connection http;

    @BeforeAll
    public static void startServer() throws IOException {
        final int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        server = new DatabaseHttpServer(port);
        server.start();
        httpUrl = "jdbc:frostlake://localhost:" + port;
    }

    @AfterAll
    public static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @BeforeEach
    public void setup() throws SQLException {
        http = DriverManager.getConnection(httpUrl);
        try (Statement s = http.createStatement()) {
            s.execute("CREATE OR REPLACE DATABASE http_align");
            s.execute("USE DATABASE http_align");
            s.execute("USE SCHEMA PUBLIC");
            s.execute("CREATE TABLE A_B (id INT)");
            s.execute("CREATE TABLE AXB (id INT)");
            s.execute("CREATE TABLE \"mixed\" (id INT PRIMARY KEY)");
            s.execute("CREATE TABLE MIXED (id2 INT PRIMARY KEY)");
            s.execute("ALTER SESSION SET MULTI_STATEMENT_COUNT = 0");
        }
    }

    @AfterEach
    public void teardown() throws SQLException {
        try (Statement s = http.createStatement()) {
            s.execute("DROP DATABASE IF EXISTS http_align");
        } finally {
            http.close();
        }
    }

    private static String column(final ResultSet rs, final String label) throws SQLException {
        final StringBuilder out = new StringBuilder();
        try (rs) {
            while (rs.next()) {
                out.append(out.length() == 0 ? "" : ",").append(rs.getString(label));
            }
        }
        return out.toString();
    }

    private String tables(final String pattern) throws SQLException {
        return column(http.getMetaData().getTables("HTTP_ALIGN", "PUBLIC", pattern, null), "TABLE_NAME");
    }

    /** Patterns honour the escape, a trailing escape matches nothing, and names compare case-sensitively. */
    @Test
    public void metadataPatternsHonourTheEscapeAndCase() throws SQLException {
        assertEquals("A_B", tables("A\\_B"));
        assertEquals("AXB,A_B", tables("A_B"));
        assertEquals("", tables("A\\"));
        assertEquals("mixed", tables("mixed"));
        assertEquals("", tables("Mixed"));
        final DatabaseMetaData md = http.getMetaData();
        assertEquals("ID", column(md.getColumns("HTTP_ALIGN", "PUBLIC", "mixed", null), "COLUMN_NAME"));
        assertEquals("ID2", column(md.getPrimaryKeys("HTTP_ALIGN", "PUBLIC", "MIXED"), "COLUMN_NAME"));
        assertEquals("", column(md.getTables("http_align", "PUBLIC", "%", null), "TABLE_NAME"));
    }

    /** A NULL literal reads columnNullable, a non-NULL one columnNoNulls. */
    @Test
    public void aNullLiteralReadsNullable() throws SQLException {
        try (Statement s = http.createStatement();
             ResultSet rs = s.executeQuery("SELECT NULL AS n, NULL::NUMBER(10,2) AS d, 1 AS one")) {
            final ResultSetMetaData md = rs.getMetaData();
            assertEquals(ResultSetMetaData.columnNullable, md.isNullable(1));
            assertEquals(ResultSetMetaData.columnNullable, md.isNullable(2));
            assertEquals(ResultSetMetaData.columnNoNulls, md.isNullable(3));
        }
    }

    /** KEEP_CURRENT_RESULT leaves a result set readable; CLOSE_ALL_RESULTS closes it; close() closes the rest. */
    @Test
    public void getMoreResultsHonoursItsArgument() throws SQLException {
        final Statement st = http.createStatement();
        assertTrue(st.execute("SELECT 1; SELECT 2; SELECT 3; SELECT 4"));
        final ResultSet a = st.getResultSet();
        assertTrue(st.getMoreResults(Statement.KEEP_CURRENT_RESULT));
        assertFalse(a.isClosed());
        assertTrue(a.next());
        assertEquals(1, a.getInt(1));
        final ResultSet b = st.getResultSet();
        assertTrue(st.getMoreResults(Statement.CLOSE_ALL_RESULTS));
        assertTrue(a.isClosed());
        assertTrue(b.isClosed());
        final ResultSet c = st.getResultSet();
        assertTrue(st.getMoreResults(Statement.KEEP_CURRENT_RESULT));
        final ResultSet d = st.getResultSet();
        st.close();
        assertTrue(c.isClosed());
        assertTrue(d.isClosed());
    }

    /** A prepared query behind a comment or a parenthesis is described. */
    @Test
    public void aPreparedQueryBehindACommentIsDescribed() throws SQLException {
        final String[] queries = {"-- c\nSELECT ? AS x", "/* c */ SELECT ? AS x", "(SELECT ? AS x)"};
        for (final String sql : queries) {
            try (PreparedStatement ps = http.prepareStatement(sql)) {
                final ResultSetMetaData md = ps.getMetaData();
                assertNotNull(md, sql);
                assertEquals("X", md.getColumnLabel(1), sql);
            }
        }
    }

    /** getBigDecimal(column, scale) rounds half up to the scale. */
    @Test
    @SuppressWarnings("deprecation")
    public void aScaledBigDecimalIsRounded() throws SQLException {
        try (Statement s = http.createStatement();
             ResultSet rs = s.executeQuery("SELECT 1.123456789012::NUMBER(20,12) AS v, 1.25::NUMBER(10,2) AS w")) {
            assertTrue(rs.next());
            assertEquals("1.1", rs.getBigDecimal(1, 1).toPlainString());
            assertEquals("1.3", rs.getBigDecimal("W", 1).toPlainString());
        }
    }
}
