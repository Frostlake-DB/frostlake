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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Over the HTTP transport, a statement that fails among several of one request carries the multi-statement helper's
 * code and state, 100132 / P0000, as it does over the in-process one (see MultiStatementFailureCodeTest), while a
 * statement sent on its own keeps its own pair.
 */
public class MultiStatementFailureCodeHttpTest {

    private static DatabaseHttpServer server;
    private static String jdbcUrl;

    @BeforeAll
    public static void startServer() throws IOException {
        final int port;
        try (final ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        server = new DatabaseHttpServer(port);
        server.start();
        jdbcUrl = "jdbc:frostlake://localhost:" + port;
    }

    @AfterAll
    public static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    /** What a request fails with, read as {@code "code state"}. */
    private static String pairOf(final Statement statement, final String sql) {
        try {
            statement.execute(sql);
            fail("no failure: " + sql);
            return null;
        } catch (final SQLException refused) {
            assertTrue(refused.getMessage() != null, sql);
            return refused.getErrorCode() + " " + refused.getSQLState();
        }
    }

    @Test
    public void aFailureAmongSeveralCarriesTheHelpersPair() throws SQLException {
        try (final Connection connection = DriverManager.getConnection(jdbcUrl);
             final Statement statement = connection.createStatement()) {
            statement.execute("CREATE OR REPLACE DATABASE msf_http_db");
            try {
                statement.execute("USE SCHEMA msf_http_db.PUBLIC");
                statement.execute("ALTER SESSION SET MULTI_STATEMENT_COUNT = 0");
                assertEquals("100132 P0000", pairOf(statement, "SELECT 1; SELECT * FROM no_such_table_msf"));
                assertEquals("100132 P0000", pairOf(statement, "SELECT 1; SELECT 1/0"));
                assertEquals("100132 P0000", pairOf(statement,
                    "BEGIN\n  EXECUTE IMMEDIATE 'SELECT 1; SELECT * FROM no_such_table_msf';\nEND;"));
                assertEquals("1003 42000", pairOf(statement, "SELECT 1; SELEC 2"));
                assertEquals("2003 42S02", pairOf(statement, "SELECT * FROM no_such_table_msf"));
            } finally {
                statement.execute("DROP DATABASE IF EXISTS msf_http_db");
            }
        }
    }
}
