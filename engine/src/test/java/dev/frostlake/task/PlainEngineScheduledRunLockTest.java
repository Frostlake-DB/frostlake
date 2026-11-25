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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.types.NumericType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A plain engine, driven by its callers directly from several threads, runs one statement at a time: a scheduled run
 * of a task waits for a caller's statement, and so does another caller's statement, rather than run beside it. A
 * run that interleaved with the caller's read-modify-write of a row would have its update overwritten.
 *
 * <p>Engine internals - the scheduler is driven on a thread of the test's own - so the class is embedded-only.
 */
public class PlainEngineScheduledRunLockTest {

    private DatabaseEngine engine;
    /** What the statement under test does while it runs, between reading the row and writing it. */
    private final AtomicReference<Thread> contender = new AtomicReference<Thread>();
    private final AtomicReference<Thread.State> contenderState = new AtomicReference<Thread.State>();

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE IF NOT EXISTS pesr_db");
        engine.execute("USE DATABASE pesr_db");
        engine.execute("CREATE TABLE counter (x INT)");
        engine.execute("INSERT INTO counter VALUES (0)");
        engine.execute("CREATE TASK bump SCHEDULE = '60 MINUTE' AS UPDATE counter SET x = x + 1");
        engine.execute("ALTER TASK bump RESUME");
        // Starts the contender inside the statement that calls it and waits until it blocks or ends.
        engine.getFunctionRegistry().register(new BuiltInFunction("FL_LET_CONTENDER_TRY", NumericType.NUMBER) {
            @Override
            public Object evaluate(final List<Object> args) {
                final Thread thread = contender.get();
                thread.start();
                contenderState.set(awaitBlockedOrDone(thread));
                return Integer.valueOf(0);
            }

            @Override
            public int getMinArgCount() {
                return 0;
            }

            @Override
            public int getMaxArgCount() {
                return 0;
            }
        });
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    @Test
    public void aScheduledRunWaitsForACallersStatement() throws Exception {
        final Task task = engine.getCatalog().getDatabase("PESR_DB").getSchema("PUBLIC").getTask("BUMP");
        final String key = TaskScheduler.schedulerKey("PESR_DB", "PUBLIC", "BUMP");
        final CountDownLatch runDone = new CountDownLatch(1);
        final AtomicReference<Throwable> runFailure = new AtomicReference<Throwable>();
        contender.set(new Thread(new Runnable() {
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
        }, "scheduled-run"));
        // The statement reads x, lets the run try to start, and writes what it read plus ten.
        engine.execute("UPDATE pesr_db.public.counter SET x = x + 10 + FL_LET_CONTENDER_TRY()");
        assertEquals(Thread.State.BLOCKED, contenderState.get());
        assertTrue(runDone.await(60, TimeUnit.SECONDS));
        assertNull(runFailure.get());
        assertEquals(11L, value());
        assertEquals(1, task.getExecutionHistory().size());
    }

    @Test
    public void anotherCallersStatementWaitsForTheRunningOne() throws Exception {
        final CountDownLatch otherDone = new CountDownLatch(1);
        final AtomicReference<Throwable> otherFailure = new AtomicReference<Throwable>();
        contender.set(new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    engine.execute("UPDATE pesr_db.public.counter SET x = x + 1");
                } catch (final Throwable failed) {
                    otherFailure.set(failed);
                } finally {
                    otherDone.countDown();
                }
            }
        }, "other-caller"));
        engine.execute("UPDATE pesr_db.public.counter SET x = x + 10 + FL_LET_CONTENDER_TRY()");
        assertEquals(Thread.State.BLOCKED, contenderState.get());
        assertTrue(otherDone.await(60, TimeUnit.SECONDS));
        assertNull(otherFailure.get());
        assertEquals(11L, value());
    }

    /** Waits until the thread blocks on a lock or ends, and answers the state it reached. */
    private static Thread.State awaitBlockedOrDone(final Thread thread) {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (System.nanoTime() < deadline) {
            final Thread.State state = thread.getState();
            if (state == Thread.State.TERMINATED || state == Thread.State.WAITING || state == Thread.State.BLOCKED) {
                return state;
            }
            Thread.yield();
        }
        return thread.getState();
    }

    private long value() {
        return ((Number) engine.execute("SELECT x FROM pesr_db.public.counter").getResultSets().get(0).getRows().get(0)
            .getValue(0)).longValue();
    }
}
