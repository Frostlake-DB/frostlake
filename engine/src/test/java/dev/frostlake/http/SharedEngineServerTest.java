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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * An HTTP server over the engine {@code jdbc:frostlake:direct:<name>} connections share: the Snowflake driver's
 * connections to it and the direct connections read and write one database. Each session stays its own - a
 * transaction on one transport is unseen on the other until it commits - writers on both run together, a writer
 * waiting for the other transport's table lock runs once that transaction ends, and stopping the server rolls back
 * what its sessions left open and leaves the engine serving its direct connections. Engine only: the server under test
 * is local.
 */
public class SharedEngineServerTest {

    private static final Logger logger = LoggerFactory.getLogger(SharedEngineServerTest.class);
    private static final long WAIT_LIMIT_MS = 60_000L;

    private String name;
    private DatabaseHttpServer server;

    @BeforeEach
    public void startServer() throws IOException, SQLException {
        assumeFalse(LiveSnowflake.enabled(), "the server under test is local");
        name = "shared-" + UUID.randomUUID();
        final EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_HTTP_PORT, "0");
        server = new DatabaseHttpServer(config, DatabaseDriver.directEngine(name));
        server.start();
        try (Connection direct = direct(); Statement statement = direct.createStatement()) {
            statement.execute("CREATE OR REPLACE DATABASE shared_db");
            statement.execute("CREATE TABLE shared_db.public.t (id INT, src VARCHAR)");
            statement.execute("INSERT INTO shared_db.public.t VALUES (1, 'direct')");
        }
    }

    @AfterEach
    public void stopServer() {
        if (server != null) {
            server.stop();
        }
        if (name != null) {
            final DatabaseEngine engine = DatabaseDriver.unregisterDirectEngine(name);
            if (engine != null) {
                engine.shutdown();
            }
        }
    }

    @Test
    public void theServerServesTheDirectNamesEngine() throws SQLException {
        assertSame(DatabaseDriver.directEngine(name), server.getEngine().getEngine());
        try (Connection snowflake = snowflake(); Statement statement = snowflake.createStatement()) {
            assertEquals(List.of("1|direct"), rows(statement, "SELECT id, src FROM t ORDER BY id"));
            assertEquals(1, statement.executeUpdate("INSERT INTO t VALUES (2, 'snowflake')"));
        }
        try (Connection direct = direct(); Statement statement = direct.createStatement()) {
            assertEquals(List.of("1|direct", "2|snowflake"),
                rows(statement, "SELECT id, src FROM shared_db.public.t ORDER BY id"));
        }
    }

    @Test
    public void aTransactionIsUnseenOnTheOtherTransportUntilItCommits() throws SQLException {
        try (Connection direct = direct(); Statement onDirect = direct.createStatement();
             Connection snowflake = snowflake(); Statement onSnowflake = snowflake.createStatement()) {
            onDirect.execute("BEGIN");
            onDirect.execute("INSERT INTO shared_db.public.t VALUES (10, 'pending')");
            assertEquals(List.of("0"), rows(onSnowflake, "SELECT COUNT(*) FROM t WHERE id = 10"));
            onDirect.execute("COMMIT");
            assertEquals(List.of("1"), rows(onSnowflake, "SELECT COUNT(*) FROM t WHERE id = 10"));

            onSnowflake.execute("BEGIN");
            onSnowflake.execute("INSERT INTO t VALUES (20, 'pending')");
            assertEquals(List.of("0"), rows(onDirect, "SELECT COUNT(*) FROM shared_db.public.t WHERE id = 20"));
            onSnowflake.execute("COMMIT");
            assertEquals(List.of("1"), rows(onDirect, "SELECT COUNT(*) FROM shared_db.public.t WHERE id = 20"));
        }
    }

    @Test
    public void writersOnBothTransportsRunTogether() throws InterruptedException, SQLException {
        final List<Throwable> failures = Collections.synchronizedList(new ArrayList<Throwable>());
        final List<Thread> writers = new ArrayList<>();
        for (int w = 0; w < 8; w++) {
            final int writer = w;
            writers.add(new Thread(new Runnable() {
                @Override
                public void run() {
                    try (Connection connection = writer % 2 == 0 ? direct() : snowflake();
                         Statement statement = connection.createStatement()) {
                        for (int i = 0; i < 25; i++) {
                            statement.executeUpdate("INSERT INTO shared_db.public.t VALUES ("
                                + (1000 + writer * 100 + i) + ", 'w" + writer + "')");
                        }
                    } catch (final SQLException | RuntimeException failed) {
                        failures.add(failed);
                    }
                }
            }));
        }
        for (final Thread writer : writers) {
            writer.start();
        }
        for (final Thread writer : writers) {
            writer.join(WAIT_LIMIT_MS);
            assertFalse(writer.isAlive(), "a writer is still running");
        }
        assertEquals(List.of(), failures);
        try (Connection direct = direct(); Statement statement = direct.createStatement()) {
            assertEquals(List.of("200|8"), rows(statement,
                "SELECT COUNT(*), COUNT(DISTINCT src) FROM shared_db.public.t WHERE id >= 1000"));
        }
    }

    @Test
    public void aWriterWaitingForTheOtherTransportsLockRunsOnceItCommits() throws Exception {
        try (Connection direct = direct(); Statement holder = direct.createStatement()) {
            holder.execute("BEGIN");
            holder.execute("UPDATE shared_db.public.t SET src = 'held' WHERE id = 1");
            final List<Throwable> failures = Collections.synchronizedList(new ArrayList<Throwable>());
            final Thread waiter = updateIn(snowflake(), "UPDATE t SET src = 'snowflake' WHERE id = 1", failures);
            awaitWaitingLock(holder);
            holder.execute("COMMIT");
            waiter.join(WAIT_LIMIT_MS);
            assertFalse(waiter.isAlive(), "the waiting writer did not run");
            assertEquals(List.of(), failures);
            assertEquals(List.of("snowflake"), rows(holder, "SELECT src FROM shared_db.public.t WHERE id = 1"));
        }
        try (Connection snowflake = snowflake(); Statement holder = snowflake.createStatement()) {
            holder.execute("BEGIN");
            holder.execute("UPDATE t SET src = 'held' WHERE id = 1");
            final List<Throwable> failures = Collections.synchronizedList(new ArrayList<Throwable>());
            final Thread waiter = updateIn(direct(), "UPDATE shared_db.public.t SET src = 'direct' WHERE id = 1",
                failures);
            awaitWaitingLock(holder);
            holder.execute("COMMIT");
            waiter.join(WAIT_LIMIT_MS);
            assertFalse(waiter.isAlive(), "the waiting writer did not run");
            assertEquals(List.of(), failures);
            assertEquals(List.of("direct"), rows(holder, "SELECT src FROM t WHERE id = 1"));
        }
    }

    @Test
    public void stoppingTheServerEndsItsSessionsAndLeavesTheEngineRunning() throws SQLException {
        // Left unclosed: closing it once the server is gone would only retry against the closed port.
        final Connection snowflake = snowflake();
        final Statement statement = snowflake.createStatement();
        statement.execute("BEGIN");
        statement.execute("INSERT INTO t VALUES (99, 'left open')");
        statement.execute("UPDATE t SET src = 'left open' WHERE id = 1");
        server.stop();
        server = null;
        logger.debug("Server stopped with a transaction open in {}", snowflake);
        try (Connection direct = direct(); Statement onDirect = direct.createStatement()) {
            assertEquals(List.of("0"), rows(onDirect, "SELECT COUNT(*) FROM shared_db.public.t WHERE id = 99"));
            // Its table lock went with the rolled-back transaction: a write runs at once.
            assertEquals(1, onDirect.executeUpdate("UPDATE shared_db.public.t SET src = 'after' WHERE id = 1"));
            assertEquals(List.of("1|after"), rows(onDirect, "SELECT id, src FROM shared_db.public.t ORDER BY id"));
        }
    }

    /** Runs an UPDATE on its own thread and connection, which it closes. */
    private static Thread updateIn(final Connection connection, final String sql, final List<Throwable> failures) {
        final Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate(sql);
                } catch (final SQLException | RuntimeException failed) {
                    failures.add(failed);
                } finally {
                    try {
                        connection.close();
                    } catch (final SQLException closing) {
                        failures.add(closing);
                    }
                }
            }
        });
        thread.start();
        return thread;
    }

    /** Waits until SHOW LOCKS lists a statement waiting for a table lock. */
    private static void awaitWaitingLock(final Statement statement) throws SQLException, InterruptedException {
        final long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WAIT_LIMIT_MS);
        while (System.nanoTime() < until) {
            try (ResultSet rs = statement.executeQuery("SHOW LOCKS IN ACCOUNT")) {
                while (rs.next()) {
                    if ("WAITING".equals(rs.getString("status"))) {
                        return;
                    }
                }
            }
            Thread.sleep(20);
        }
        throw new AssertionError("no statement waited for the lock");
    }

    private Connection direct() throws SQLException {
        return DriverManager.getConnection("jdbc:frostlake:direct:" + name);
    }

    private Connection snowflake() throws SQLException {
        final Properties properties = new Properties();
        properties.put("user", "any_user");
        properties.put("password", "any_password");
        properties.put("db", "shared_db");
        properties.put("schema", "public");
        properties.put("disablePlatformDetection", "true");
        return DriverManager.getConnection("jdbc:snowflake://127.0.0.1:" + server.getPort()
            + "/?account=frostlake&ssl=off", properties);
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
