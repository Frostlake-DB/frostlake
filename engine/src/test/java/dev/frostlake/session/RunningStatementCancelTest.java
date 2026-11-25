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
package dev.frostlake.session;

import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.DatabaseEngine;
import dev.frostlake.http.SessionContext;
import dev.frostlake.jdbc.DatabaseDriver;
import dev.frostlake.metastore.QueryHistory;
import dev.frostlake.storage.Row;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A statement that holds the engine while it runs - a block's loop, which gives the engine's lock up nowhere - is
 * reached by another session's SYSTEM$CANCEL_QUERY and SYSTEM$ABORT_SESSION while it runs, over HTTP sessions and
 * in-process connections alike: the call is carried out as its request is read, the statement ends at its next
 * checkpoint, and the call answers once it may run, that it terminated the statement. Before, the call waited for
 * the statement to end - here at its session's statement timeout - and then found nothing running to cancel.
 * Engine only: the calls end statements of the embedded engine's sessions.
 */
public class RunningStatementCancelTest {

    /** A block that runs until something ends it. */
    private static final String ENDLESS = """
        EXECUTE IMMEDIATE $$
        DECLARE
          i INT DEFAULT 0;
        BEGIN
          WHILE (TRUE) DO
            i := i + 1;
          END WHILE;
          RETURN i;
        END;
        $$""";

    /** What ends the endless block when nothing else does, so a broken cancel fails rather than hangs. */
    private static final String BACKSTOP = "ALTER SESSION SET STATEMENT_TIMEOUT_IN_SECONDS = 60";

    private static final long LONG_WAIT_MILLIS = 120000L;

    private static int engineSequence = 0;

    /** A statement's answer over an HTTP session, on one line: rows as "a|b ;; c|d", or "REFUSED " and the refusal. */
    private static String say(final ConcurrentDatabaseEngine engine, final SessionContext session, final String sql) {
        try {
            final StringBuilder out = new StringBuilder();
            for (final Row row : engine.executeQuery(sql, session).getRows()) {
                out.append(out.length() == 0 ? "" : " ;; ");
                for (int i = 0; i < row.getValues().size(); i++) {
                    out.append(i == 0 ? "" : "|").append(row.getValue(i));
                }
            }
            return out.toString();
        } catch (final RuntimeException refused) {
            return "REFUSED " + refused.getMessage();
        }
    }

    /** A statement's answer over an in-process connection, as {@link #say(ConcurrentDatabaseEngine, ...)} gives it. */
    private static String say(final Connection connection, final String sql) {
        try (Statement statement = connection.createStatement()) {
            if (!statement.execute(sql)) {
                return "count " + statement.getUpdateCount();
            }
            final java.sql.ResultSet rows = statement.getResultSet();
            final ResultSetMetaData columns = rows.getMetaData();
            final StringBuilder out = new StringBuilder();
            while (rows.next()) {
                out.append(out.length() == 0 ? "" : " ;; ");
                for (int i = 1; i <= columns.getColumnCount(); i++) {
                    out.append(i == 1 ? "" : "|").append(rows.getString(i));
                }
            }
            return out.toString();
        } catch (final SQLException refused) {
            return "REFUSED " + refused.getMessage();
        }
    }

    /**
     * The query ID of the oldest statement the session of that number runs, looked at for up to thirty seconds.
     *
     * @return the ID, or null when none ran
     */
    private static String runningIn(final DatabaseEngine engine, final long session) throws InterruptedException {
        for (int attempt = 0; attempt < 1500; attempt++) {
            for (final QueryHistory running : engine.getExecutor().getQueryHistoryTracker().getRunning()) {
                if (running.getSessionId() != null && running.getSessionId().longValue() == session) {
                    return running.getQueryId();
                }
            }
            Thread.sleep(20L);
        }
        return null;
    }

    /** Starts a statement on a thread of its own. */
    private static Thread start(final Runnable statement) {
        final Thread thread = new Thread(statement, "endless-statement");
        thread.start();
        return thread;
    }

    @Test
    public void aCancelOverHttpEndsAStatementThatHoldsTheEngine() throws InterruptedException {
        final ConcurrentDatabaseEngine engine = new ConcurrentDatabaseEngine();
        try {
            final SessionContext runner = engine.createSession();
            final SessionContext other = engine.createSession();
            say(engine, runner, BACKSTOP);
            final long session = Long.parseLong(say(engine, runner, "SELECT CURRENT_SESSION()"));
            final String[] answered = new String[1];
            final Thread thread = start(new Runnable() {
                @Override
                public void run() {
                    answered[0] = say(engine, runner, ENDLESS);
                }
            });
            final String id = runningIn(engine.getEngine(), session);
            assertNotNull(id, "the block runs");
            assertEquals("query [" + id + "] terminated.",
                say(engine, other, "SELECT SYSTEM$CANCEL_QUERY('" + id + "')"));
            thread.join(LONG_WAIT_MILLIS);
            assertFalse(thread.isAlive(), "the block ended");
            assertTrue(answered[0].startsWith("REFUSED") && answered[0].contains("SQL execution canceled"),
                answered[0]);
            // The session runs on.
            assertEquals("1", say(engine, runner, "SELECT 1"));
        } finally {
            engine.shutdown();
        }
    }

    @Test
    public void anAbortOverHttpEndsAStatementThatHoldsTheEngine() throws InterruptedException {
        final ConcurrentDatabaseEngine engine = new ConcurrentDatabaseEngine();
        try {
            final SessionContext runner = engine.createSession();
            final SessionContext other = engine.createSession();
            say(engine, runner, BACKSTOP);
            final long session = Long.parseLong(say(engine, runner, "SELECT CURRENT_SESSION()"));
            final String[] answered = new String[1];
            final Thread thread = start(new Runnable() {
                @Override
                public void run() {
                    answered[0] = say(engine, runner, ENDLESS);
                }
            });
            assertNotNull(runningIn(engine.getEngine(), session), "the block runs");
            assertEquals("session [" + session + "] terminated.",
                say(engine, other, "SELECT SYSTEM$ABORT_SESSION(" + session + ")"));
            thread.join(LONG_WAIT_MILLIS);
            assertFalse(thread.isAlive(), "the block ended");
            assertTrue(answered[0].startsWith("REFUSED") && answered[0].contains("SQL execution canceled"),
                answered[0]);
        } finally {
            engine.shutdown();
        }
    }

    /** A fresh engine the in-process driver serves under the name. */
    private static DatabaseEngine directEngine(final String name) {
        final DatabaseEngine engine = new DatabaseEngine();
        DatabaseDriver.registerDirectEngine(name, engine);
        return engine;
    }

    @Test
    public void aCancelOverAnInProcessConnectionEndsAStatementThatHoldsTheEngine()
            throws InterruptedException, SQLException {
        engineSequence++;
        final String name = "running_cancel_" + engineSequence;
        final DatabaseEngine engine = directEngine(name);
        final Connection runner = DriverManager.getConnection("jdbc:frostlake:direct:" + name);
        final Connection other = DriverManager.getConnection("jdbc:frostlake:direct:" + name);
        try {
            say(runner, BACKSTOP);
            final long session = Long.parseLong(say(runner, "SELECT CURRENT_SESSION()"));
            final String[] answered = new String[1];
            final Thread thread = start(new Runnable() {
                @Override
                public void run() {
                    answered[0] = say(runner, ENDLESS);
                }
            });
            final String id = runningIn(engine, session);
            assertNotNull(id, "the block runs");
            assertEquals("query [" + id + "] terminated.", say(other, "SELECT SYSTEM$CANCEL_QUERY('" + id + "')"));
            thread.join(LONG_WAIT_MILLIS);
            assertFalse(thread.isAlive(), "the block ended");
            assertTrue(answered[0].startsWith("REFUSED") && answered[0].contains("SQL execution canceled"),
                answered[0]);
            assertEquals("1", say(runner, "SELECT 1"));
            // A query ID that names no running statement is still answered so.
            assertEquals("Identified SQL statement is not currently executing.",
                say(other, "SELECT SYSTEM$CANCEL_QUERY('" + id + "')"));
        } finally {
            runner.close();
            other.close();
            DatabaseDriver.unregisterDirectEngine(name);
            engine.shutdown();
        }
    }

    @Test
    public void anAbortOverAnInProcessConnectionEndsAStatementThatHoldsTheEngine()
            throws InterruptedException, SQLException {
        engineSequence++;
        final String name = "running_cancel_" + engineSequence;
        final DatabaseEngine engine = directEngine(name);
        final Connection runner = DriverManager.getConnection("jdbc:frostlake:direct:" + name);
        final Connection other = DriverManager.getConnection("jdbc:frostlake:direct:" + name);
        try {
            say(runner, BACKSTOP);
            final long session = Long.parseLong(say(runner, "SELECT CURRENT_SESSION()"));
            final String[] answered = new String[1];
            final Thread thread = start(new Runnable() {
                @Override
                public void run() {
                    answered[0] = say(runner, ENDLESS);
                }
            });
            assertNotNull(runningIn(engine, session), "the block runs");
            assertEquals("session [" + session + "] terminated.",
                say(other, "SELECT SYSTEM$ABORT_SESSION(" + session + ")"));
            thread.join(LONG_WAIT_MILLIS);
            assertFalse(thread.isAlive(), "the block ended");
            assertTrue(answered[0].startsWith("REFUSED") && answered[0].contains("SQL execution canceled"),
                answered[0]);
            assertTrue(say(runner, "SELECT 1").startsWith("REFUSED Session no longer exists."));
        } finally {
            runner.close();
            other.close();
            DatabaseDriver.unregisterDirectEngine(name);
            engine.shutdown();
        }
    }
}
