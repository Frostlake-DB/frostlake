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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.LiveSnowflake;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.jdbc.DatabaseDriver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * {@link SnowflakeEndpoint} starts an in-process endpoint for Snowflake's own JDBC driver in one call: the driver
 * connects by its URL, runs DDL, DML, queries and bound statements, and sees what {@code jdbc:frostlake:direct:<name>}
 * connections write; closing it leaves no server listening and the direct name's engine running. Engine only: the
 * endpoint under test is local.
 */
public class SnowflakeEndpointTest {

    @Test
    public void theDriverConnectsByTheEndpointsUrlAndSharesTheDirectNamesData()
            throws IOException, SQLException, InterruptedException {
        assumeFalse(LiveSnowflake.enabled(), "the endpoint under test is local");
        final String name = "endpoint-" + UUID.randomUUID();
        final int port;
        try {
            try (Connection direct = DriverManager.getConnection("jdbc:frostlake:direct:" + name);
                 Statement statement = direct.createStatement()) {
                statement.execute("CREATE OR REPLACE DATABASE ep_db");
                statement.execute("CREATE TABLE ep_db.public.t (id INT, s VARCHAR)");
                statement.execute("INSERT INTO ep_db.public.t VALUES (1, 'direct')");
            }
            try (SnowflakeEndpoint endpoint = SnowflakeEndpoint.start(name)) {
                port = endpoint.getPort();
                assertTrue(endpoint.jdbcUrl().startsWith("jdbc:snowflake://127.0.0.1:" + port + "/?account="),
                    endpoint.jdbcUrl());
                try (Connection connection = DriverManager.getConnection(endpoint.jdbcUrl(), "any", "any");
                     Statement statement = connection.createStatement()) {
                    statement.execute("USE SCHEMA ep_db.public");
                    assertEquals(List.of("1|direct"), rows(statement, "SELECT id, s FROM t ORDER BY id"));
                    assertEquals(0, statement.executeUpdate("CREATE TABLE u (n INT)"));
                    assertEquals(2, statement.executeUpdate("INSERT INTO u VALUES (1), (2)"));
                    try (PreparedStatement insert = connection.prepareStatement("INSERT INTO t VALUES (?, ?)")) {
                        insert.setInt(1, 2);
                        insert.setString(2, "snowflake");
                        assertEquals(1, insert.executeUpdate());
                    }
                    assertEquals(List.of("3"), rows(statement, "SELECT SUM(n) FROM u"));
                }
            }
            // The endpoint's writes are the direct name's, and its engine runs on once the endpoint is closed.
            try (Connection direct = DriverManager.getConnection("jdbc:frostlake:direct:" + name);
                 Statement statement = direct.createStatement()) {
                assertEquals(List.of("1|direct", "2|snowflake"),
                    rows(statement, "SELECT id, s FROM ep_db.public.t ORDER BY id"));
            }
        } finally {
            final DatabaseEngine engine = DatabaseDriver.unregisterDirectEngine(name);
            if (engine != null) {
                engine.shutdown();
            }
        }
        assertThrows(ConnectException.class, new Executable() {
            @Override
            public void execute() throws IOException {
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress("127.0.0.1", port), 5000);
                }
            }
        });
        // Nor do the threads that served its requests stay to keep the JVM alive.
        awaitNoThreadNamed("frostlake-http-" + port + "-");
    }

    /** Waits until no live thread's name starts with a prefix. */
    private static void awaitNoThreadNamed(final String prefix) throws InterruptedException {
        final long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (System.nanoTime() < until) {
            boolean alive = false;
            for (final Thread thread : Thread.getAllStackTraces().keySet()) {
                alive |= thread.isAlive() && thread.getName().startsWith(prefix);
            }
            if (!alive) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("threads named " + prefix + "* still run");
    }

    @Test
    public void anEndpointOfItsOwnRunsOnTheConfigurationGiven() throws IOException, SQLException {
        assumeFalse(LiveSnowflake.enabled(), "the endpoint under test is local");
        final EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_DATABASE_DEFAULT, "own_db");
        config.setProperty(EngineConfig.PROP_HTTP_PORT, "1");
        try (SnowflakeEndpoint endpoint = SnowflakeEndpoint.start(config);
             Connection connection = DriverManager.getConnection(endpoint.jdbcUrl(), "any", "any");
             Statement statement = connection.createStatement()) {
            assertTrue(endpoint.getPort() > 1, String.valueOf(endpoint.getPort()));
            assertEquals(List.of("OWN_DB"), rows(statement, "SELECT CURRENT_DATABASE()"));
        }
        // The configuration given is left as it was.
        assertEquals("1", config.getProperty(EngineConfig.PROP_HTTP_PORT, ""));
    }

    private static List<String> rows(final Statement statement, final String sql) throws SQLException {
        final List<String> rows = new ArrayList<>();
        try (ResultSet rs = statement.executeQuery(sql)) {
            final int columns = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                final StringBuilder row = new StringBuilder();
                for (int i = 1; i <= columns; i++) {
                    row.append(i > 1 ? "|" : "").append(rs.getString(i));
                }
                rows.add(row.toString());
            }
        }
        return rows;
    }
}
