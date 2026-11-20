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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.DatabaseEngine;
import dev.frostlake.jdbc.DatabaseDriver;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A statement waiting in SYSTEM$WAIT does not hold up another session: that session lists it RUNNING in
 * QUERY_HISTORY by its query tag while it waits and cancels it, `query [<id>] terminated.`, and the waiting statement
 * ends with `SQL execution canceled` (live-verified). The class runs embedded, over two in-process connections.
 */
public class SystemWaitCancelTest extends BaseDatabaseTest {

    private static int engineSequence = 0;

    private String engineName;
    private Connection waiter;
    private Connection watcher;

    @BeforeEach
    public void openConnections() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), "waits in an embedded engine");
        engineSequence++;
        engineName = "system_wait_cancel_" + engineSequence;
        waiter = DriverManager.getConnection("jdbc:frostlake:direct:" + engineName);
        watcher = DriverManager.getConnection("jdbc:frostlake:direct:" + engineName);
        say(waiter, "ALTER SESSION SET QUERY_TAG = 'waiting_statement'");
    }

    @AfterEach
    public void closeConnections() throws SQLException {
        if (engineName == null) {
            return;
        }
        waiter.close();
        watcher.close();
        final DatabaseEngine engine = DatabaseDriver.unregisterDirectEngine(engineName);
        if (engine != null) {
            engine.shutdown();
        }
    }

    private static void say(final Connection connection, final String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    /** The query ID of the tagged statement QUERY_HISTORY lists RUNNING, polled for up to 20 seconds, or null. */
    private String runningQuery() throws SQLException, InterruptedException {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            try (Statement statement = watcher.createStatement(); ResultSet rows = statement.executeQuery("""
                    SELECT query_id FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY())
                    WHERE execution_status = 'RUNNING' AND query_tag = 'waiting_statement'""")) {
                if (rows.next()) {
                    return rows.getString(1);
                }
            }
            Thread.sleep(50L);
        }
        return null;
    }

    @Test
    public void anotherSessionListsAndCancelsAWaitingStatement() throws SQLException, InterruptedException {
        final SQLException[] failed = new SQLException[1];
        final Thread waiting = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    say(waiter, "SELECT SYSTEM$WAIT(25)");
                } catch (final SQLException refused) {
                    failed[0] = refused;
                }
            }
        }, "system-wait-waiter");
        waiting.start();
        final String running = runningQuery();
        assertNotNull(running, "the waiting statement was never listed RUNNING");
        try (Statement statement = watcher.createStatement();
             ResultSet rows = statement.executeQuery("SELECT SYSTEM$CANCEL_QUERY('" + running + "')")) {
            assertTrue(rows.next());
            assertEquals("query [" + running + "] terminated.", rows.getString(1));
        }
        waiting.join(TimeUnit.SECONDS.toMillis(20));
        assertFalse(waiting.isAlive(), "the waiting statement kept waiting");
        assertNotNull(failed[0], "the waiting statement was not canceled");
        assertTrue(failed[0].getMessage().contains("SQL execution canceled"), failed[0].getMessage());
        assertEquals(604, failed[0].getErrorCode());
        assertEquals("57014", failed[0].getSQLState());
    }
}
