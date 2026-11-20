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


package dev.frostlake.task;

import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.DatabaseEngine;
import dev.frostlake.SessionWork;
import dev.frostlake.http.SessionContext;
import dev.frostlake.metastore.model.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A scheduled run of a task takes the engine as a request of the concurrent engine does: while a request holds the
 * engine, the run waits, and the request reads nothing the run wrote; the run goes on once the request is done. A
 * run that interleaved with the request would lose one of two read-modify-write updates of the same row.
 *
 * <p>Engine internals - the scheduler is driven on a thread of the test's own - so the class is embedded-only.
 */
public class ScheduledRunLockTest {

    private ConcurrentDatabaseEngine concurrent;
    private SessionContext session;

    @BeforeEach
    public void setUp() {
        concurrent = new ConcurrentDatabaseEngine();
        session = concurrent.createSession();
        concurrent.execute("CREATE DATABASE IF NOT EXISTS srl_db", session);
        concurrent.execute("USE DATABASE srl_db", session);
        concurrent.execute("CREATE TABLE counter (x INT)", session);
        concurrent.execute("INSERT INTO counter VALUES (0)", session);
        concurrent.execute("CREATE TASK bump SCHEDULE = '60 MINUTE' AS UPDATE counter SET x = x + 1", session);
        concurrent.execute("ALTER TASK bump RESUME", session);
    }

    @AfterEach
    public void tearDown() {
        concurrent.shutdown();
    }

    @Test
    public void aScheduledRunWaitsForTheRequestHoldingTheEngine() throws Exception {
        final DatabaseEngine engine = concurrent.getEngine();
        final Task task = engine.getCatalog().getDatabase("SRL_DB").getSchema("PUBLIC").getTask("BUMP");
        final String key = TaskScheduler.schedulerKey("SRL_DB", "PUBLIC", "BUMP");
        final CountDownLatch runDone = new CountDownLatch(1);
        final AtomicReference<Throwable> runFailure = new AtomicReference<Throwable>();
        final Thread run = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    engine.getTaskScheduler().fireScheduled(key, task);
                } catch (final Throwable failed) {
                    runFailure.set(failed);
                } finally {
                    runDone.countDown();
                }
            }
        }, "scheduled-run");
        final AtomicLong readWhileHeld = new AtomicLong(-1L);
        // A read-modify-write the request does while it holds the engine: it reads x, lets the run try to start,
        // and writes what it read plus ten. A run that slipped in between would have its increment overwritten.
        concurrent.inSession(session, new SessionWork<Void>() {
            @Override
            public Void run(final DatabaseEngine held) {
                final long read = value(held);
                run.start();
                awaitBlockedOrDone(run);
                readWhileHeld.set(value(held));
                held.execute("UPDATE srl_db.public.counter SET x = " + (read + 10L));
                return null;
            }
        });
        assertEquals(0L, readWhileHeld.get());
        assertTrue(runDone.await(60, TimeUnit.SECONDS));
        assertNull(runFailure.get());
        assertEquals(11L, value(engine));
        assertEquals(1, task.getExecutionHistory().size());
    }

    /** Waits until the thread blocks on a lock or ends: a run that does not wait for the request just ends. */
    private static void awaitBlockedOrDone(final Thread thread) {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (System.nanoTime() < deadline) {
            final Thread.State state = thread.getState();
            if (state == Thread.State.TERMINATED || state == Thread.State.WAITING || state == Thread.State.BLOCKED) {
                return;
            }
            Thread.yield();
        }
    }

    private static long value(final DatabaseEngine engine) {
        return ((Number) engine.execute("SELECT x FROM srl_db.public.counter").getResultSets().get(0).getRows().get(0).getValue(0))
            .longValue();
    }
}
