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

package dev.frostlake.transaction;

import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.http.SessionContext;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two transactions that each hold a table the other's statement waits for are deadlocked. The most recent waiting
 * statement is refused once it has waited the deadlock detection time - 628 / 40P01, naming the lock it waited for -
 * its transaction stays open with what it did before, and the other statement goes on once that transaction ends. A
 * LOCK_TIMEOUT shorter than the detection time refuses both waiters first (live-verified, with the account's detection
 * time of about five minutes).
 *
 * <p>The detection time is the engine's configuration, so the class is embedded-only: two sessions of one concurrent
 * engine.
 */
public class DeadlockTest {

    private static final String LOCKED_A = "Statement '<QID>' has locked table 'A' in transaction <TXN> and this lock"
        + " has not yet been released.\nYour statement '<QID>' was aborted because ";

    private ConcurrentDatabaseEngine concurrent;
    private SessionContext sessionA;
    private SessionContext sessionB;

    private void open(final String detectionSeconds) {
        final EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_TRANSACTION_DEADLOCK_DETECTION_SECONDS, detectionSeconds);
        concurrent = new ConcurrentDatabaseEngine(config);
        sessionA = concurrent.createSession();
        sessionB = concurrent.createSession();
        say("A", "CREATE DATABASE dl");
        say("A", "USE SCHEMA dl.PUBLIC");
        say("B", "USE SCHEMA dl.PUBLIC");
        say("A", "CREATE TABLE a (x INT)");
        say("A", "CREATE TABLE b (x INT)");
        say("A", "INSERT INTO a VALUES (1)");
        say("A", "INSERT INTO b VALUES (1)");
    }

    @AfterEach
    public void tearDown() {
        say("A", "ROLLBACK");
        say("B", "ROLLBACK");
        concurrent.shutdown();
    }

    /** Runs a statement in one session: its rows as {@code a|b ;; c}, or {@code REFUSED} and the masked refusal. */
    private String say(final String side, final String sql) {
        try {
            final ResultSet rows = concurrent.executeQuery(sql, "A".equals(side) ? sessionA : sessionB);
            final StringBuilder out = new StringBuilder();
            while (rows.next()) {
                out.append(out.length() == 0 ? "" : " ;; ");
                for (int i = 0; i < rows.getColumnCount(); i++) {
                    out.append(i == 0 ? "" : "|").append(rows.getValue(i));
                }
            }
            return out.toString();
        } catch (final RuntimeException refused) {
            return "REFUSED " + String.valueOf(refused.getMessage())
                .replaceAll("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "<QID>")
                .replaceAll("\\b\\d{19}\\b", "<TXN>");
        }
    }

    /** Session A, holding table A, starts waiting for table B, which session B holds. */
    private Thread aWaitsForB(final String[] answered) {
        say("A", "BEGIN");
        say("B", "BEGIN");
        say("A", "UPDATE a SET x = 2");
        say("B", "UPDATE b SET x = 2");
        final Thread waiting = new Thread(new Runnable() {
            @Override
            public void run() {
                answered[0] = say("A", "UPDATE b SET x = 3");
            }
        }, "deadlock-a");
        waiting.start();
        awaitWaiting(waiting);
        return waiting;
    }

    /** Waits until a statement of some transaction waits for a lock, or the thread ends. */
    private void awaitWaiting(final Thread thread) {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (System.nanoTime() < deadline && thread.isAlive()) {
            for (final Transaction open : concurrent.getEngine().getTransactionManager().getActiveTransactions()) {
                if (open.getWaitingFor() != null) {
                    return;
                }
            }
            Thread.yield();
        }
    }

    @Test
    public void theMostRecentWaiterIsRefusedAndTheOtherGoesOn() throws InterruptedException {
        open("0");
        say("A", "ALTER SESSION SET LOCK_TIMEOUT = 60");
        say("B", "ALTER SESSION SET LOCK_TIMEOUT = 60");
        final String[] answered = new String[1];
        final Thread waiting = aWaitsForB(answered);
        final String transactionB = say("B", "SELECT CURRENT_TRANSACTION()");
        assertEquals("REFUSED " + LOCKED_A + "a deadlock with this lock was detected.", say("B", "UPDATE a SET x = 3"));
        // The refused statement's transaction stays open, with what it did before.
        assertEquals(transactionB, say("B", "SELECT CURRENT_TRANSACTION()"));
        assertEquals("1", say("B", "SELECT x FROM a"));
        assertEquals("2", say("B", "SELECT x FROM b"));
        assertTrue(waiting.isAlive(), "the older waiter waits on");
        say("B", "COMMIT");
        waiting.join(TimeUnit.SECONDS.toMillis(60));
        assertFalse(answered[0] == null || answered[0].startsWith("REFUSED"), String.valueOf(answered[0]));
        say("A", "COMMIT");
        assertEquals("2", say("B", "SELECT x FROM a"));
        assertEquals("3", say("B", "SELECT x FROM b"));
    }

    @Test
    public void aHandlerReadsTheDeadlockAs628() throws InterruptedException {
        open("0");
        say("A", "ALTER SESSION SET LOCK_TIMEOUT = 60");
        say("B", "ALTER SESSION SET LOCK_TIMEOUT = 60");
        final String[] answered = new String[1];
        final Thread waiting = aWaitsForB(answered);
        assertEquals("628|40P01", say("B", "EXECUTE IMMEDIATE $$ BEGIN UPDATE a SET x = 3; RETURN 'done';"
            + " EXCEPTION WHEN OTHER THEN RETURN SQLCODE || '|' || SQLSTATE; END $$"));
        say("B", "ROLLBACK");
        waiting.join(TimeUnit.SECONDS.toMillis(60));
        assertFalse(answered[0] == null || answered[0].startsWith("REFUSED"), String.valueOf(answered[0]));
    }

    @Test
    public void aLockTimeoutShorterThanDetectionRefusesBothWaiters() throws InterruptedException {
        open(String.valueOf(EngineConfig.DEFAULT_DEADLOCK_DETECTION_SECONDS));
        say("A", "ALTER SESSION SET LOCK_TIMEOUT = 1");
        say("B", "ALTER SESSION SET LOCK_TIMEOUT = 1");
        final String[] answered = new String[1];
        final Thread waiting = aWaitsForB(answered);
        assertEquals("REFUSED " + LOCKED_A + "waiting for this lock is limited to 1 seconds.",
            say("B", "UPDATE a SET x = 3"));
        waiting.join(TimeUnit.SECONDS.toMillis(60));
        assertTrue(answered[0] != null && answered[0].endsWith("waiting for this lock is limited to 1 seconds."),
            String.valueOf(answered[0]));
    }
}
