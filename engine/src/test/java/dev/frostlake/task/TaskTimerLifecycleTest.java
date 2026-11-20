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
import dev.frostlake.metastore.model.Task;
import dev.frostlake.metastore.model.TaskState;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A started task holds exactly one timer, and only for as long as the catalog holds the task: a second RESUME arms
 * nothing more, so one SUSPEND disarms it; DROP TASK, CREATE OR REPLACE TASK and the drop of the schema or the
 * database the task is in disarm it; an UNDROP that brings the task back started arms it again. A timer that fires
 * for a task the catalog no longer holds runs nothing.
 *
 * <p>Engine internals - the scheduler's pending timers - so the class is embedded-only. The schedules are an hour
 * long: nothing here waits for a timer to fire.
 */
public class TaskTimerLifecycleTest {

    private static final String KEY = TaskScheduler.schedulerKey("TT_DB", "PUBLIC", "TK");

    private DatabaseEngine engine;
    private TaskScheduler scheduler;
    private int idle;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        scheduler = engine.getTaskScheduler();
        scheduler.start();
        // The running scheduler's own timer: the dynamic-table sweep.
        idle = scheduler.pendingTimerCount();
        engine.execute("CREATE DATABASE keep_db");
        engine.execute("CREATE TABLE keep_db.public.log (src STRING)");
        engine.execute("CREATE DATABASE tt_db");
        engine.execute("USE DATABASE tt_db");
        engine.execute("CREATE TASK tt_db.public.tk SCHEDULE = '60 MINUTE' AS INSERT INTO keep_db.public.log SELECT 'old'");
        engine.execute("ALTER TASK tt_db.public.tk RESUME");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    @Test
    public void aResumedTaskHoldsOneTimer() {
        assertEquals(idle + 1, scheduler.pendingTimerCount());
        assertTrue(scheduler.isScheduled(KEY));
    }

    @Test
    public void resumingAStartedTaskArmsNoSecondTimer() {
        engine.execute("ALTER TASK tt_db.public.tk RESUME");
        assertEquals(idle + 1, scheduler.pendingTimerCount());
        engine.execute("ALTER TASK tt_db.public.tk SUSPEND");
        assertEquals(idle, scheduler.pendingTimerCount());
        assertFalse(scheduler.isScheduled(KEY));
    }

    @Test
    public void resumingAStartedCronTaskArmsNoSecondTimer() {
        engine.execute("CREATE TASK tt_db.public.tc SCHEDULE = 'USING CRON 0 0 1 1 * UTC' AS SELECT 1");
        engine.execute("ALTER TASK tt_db.public.tc RESUME");
        engine.execute("ALTER TASK tt_db.public.tc RESUME");
        assertEquals(idle + 2, scheduler.pendingTimerCount());
        engine.execute("ALTER TASK tt_db.public.tc SUSPEND");
        assertEquals(idle + 1, scheduler.pendingTimerCount());
    }

    @Test
    public void droppingAStartedTaskDisarmsIt() {
        engine.execute("DROP TASK tt_db.public.tk");
        assertEquals(idle, scheduler.pendingTimerCount());
        assertFalse(scheduler.isScheduled(KEY));
    }

    @Test
    public void replacingAStartedTaskDisarmsIt() {
        engine.execute("CREATE OR REPLACE TASK tt_db.public.tk SCHEDULE = '60 MINUTE' AS INSERT INTO keep_db.public.log"
            + " SELECT 'new'");
        assertEquals(TaskState.SUSPENDED, engine.getCatalog().getDatabase("TT_DB").getSchema("PUBLIC")
            .getTask("TK").getState());
        assertEquals(idle, scheduler.pendingTimerCount());
        assertFalse(scheduler.isScheduled(KEY));
    }

    @Test
    public void droppingTheSchemaDisarmsItsStartedTask() {
        engine.execute("DROP SCHEMA tt_db.public");
        assertEquals(idle, scheduler.pendingTimerCount());
        assertFalse(scheduler.isScheduled(KEY));
    }

    @Test
    public void droppingTheDatabaseDisarmsItsStartedTask() {
        engine.execute("USE DATABASE keep_db");
        engine.execute("DROP DATABASE tt_db");
        assertEquals(idle, scheduler.pendingTimerCount());
        assertFalse(scheduler.isScheduled(KEY));
    }

    @Test
    public void undroppingTheSchemaArmsItsStartedTaskAgain() {
        engine.execute("DROP SCHEMA tt_db.public");
        engine.execute("UNDROP SCHEMA tt_db.public");
        assertEquals(TaskState.STARTED, engine.getCatalog().getDatabase("TT_DB").getSchema("PUBLIC")
            .getTask("TK").getState());
        assertEquals(idle + 1, scheduler.pendingTimerCount());
        assertTrue(scheduler.isScheduled(KEY));
    }

    @Test
    public void undroppingTheDatabaseArmsItsStartedTaskAgain() {
        engine.execute("USE DATABASE keep_db");
        engine.execute("DROP DATABASE tt_db");
        engine.execute("UNDROP DATABASE tt_db");
        assertEquals(idle + 1, scheduler.pendingTimerCount());
        assertTrue(scheduler.isScheduled(KEY));
    }

    @Test
    public void renamingTheSchemaMovesTheTimerWithTheTask() {
        engine.execute("CREATE SCHEMA tt_db.other");
        engine.execute("CREATE TASK tt_db.other.tk SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("ALTER TASK tt_db.other.tk RESUME");
        engine.execute("ALTER SCHEMA tt_db.other RENAME TO moved");
        assertEquals(idle + 2, scheduler.pendingTimerCount());
        assertTrue(scheduler.isScheduled(TaskScheduler.schedulerKey("TT_DB", "MOVED", "TK")));
        assertFalse(scheduler.isScheduled(TaskScheduler.schedulerKey("TT_DB", "OTHER", "TK")));
        assertTrue(scheduler.isScheduled(KEY));
    }

    @Test
    public void aTimerOfADroppedTaskRunsNothing() {
        final Task dropped = engine.getCatalog().getDatabase("TT_DB").getSchema("PUBLIC").getTask("TK");
        engine.execute("CREATE OR REPLACE TASK tt_db.public.tk SCHEDULE = '60 MINUTE' AS INSERT INTO keep_db.public.log"
            + " SELECT 'new'");
        scheduler.fireScheduled(KEY, dropped);
        assertEquals(0L, count("SELECT COUNT(*) FROM keep_db.public.log"));
        assertTrue(dropped.getExecutionHistory().isEmpty());
    }

    @Test
    public void aTimerOfAStartedTaskRunsItsBody() {
        final Task started = engine.getCatalog().getDatabase("TT_DB").getSchema("PUBLIC").getTask("TK");
        scheduler.fireScheduled(KEY, started);
        assertEquals(1L, count("SELECT COUNT(*) FROM keep_db.public.log WHERE src = 'old'"));
        assertSame(started, engine.getCatalog().getDatabase("TT_DB").getSchema("PUBLIC").getTask("TK"));
    }

    private long count(final String sql) {
        final List<ResultSet> results = engine.execute(sql).getResultSets();
        return ((Number) results.get(0).getRows().get(0).getValue(0)).longValue();
    }
}
