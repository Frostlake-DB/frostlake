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
import dev.frostlake.metastore.model.Alert;
import dev.frostlake.metastore.model.AlertState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A started alert holds exactly one timer, and only for as long as the catalog holds the alert: a second RESUME arms
 * nothing more, so one SUSPEND disarms it; DROP ALERT, CREATE OR REPLACE ALERT and the drop or the replacement of the
 * schema or the database the alert is in disarm it; an UNDROP of the schema or the database brings the alert back
 * started, and arms it again.
 *
 * <p>Engine internals - the scheduler's pending timers - so the class is embedded-only. The schedules are an hour
 * long: nothing here waits for a timer to fire.
 */
public class AlertTimerLifecycleTest {

    private static final String KEY = TaskScheduler.alertKey("AT_DB", "PUBLIC", "AL");

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
        engine.execute("CREATE DATABASE at_db");
        engine.execute("USE DATABASE at_db");
        engine.execute("CREATE ALERT at_db.public.al WAREHOUSE = COMPUTE_WH SCHEDULE = '60 MINUTE'"
            + " IF (EXISTS (SELECT 1)) THEN SELECT 1");
        engine.execute("ALTER ALERT at_db.public.al RESUME");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    @Test
    public void aResumedAlertHoldsOneTimer() {
        assertEquals(idle + 1, scheduler.pendingTimerCount());
        assertTrue(scheduler.isScheduled(KEY));
    }

    @Test
    public void resumingAStartedAlertArmsNoSecondTimer() {
        engine.execute("ALTER ALERT at_db.public.al RESUME");
        assertEquals(idle + 1, scheduler.pendingTimerCount());
        engine.execute("ALTER ALERT at_db.public.al SUSPEND");
        assertEquals(idle, scheduler.pendingTimerCount());
        assertFalse(scheduler.isScheduled(KEY));
    }

    @Test
    public void resumingAStartedCronAlertArmsNoSecondTimer() {
        engine.execute("CREATE ALERT at_db.public.ac WAREHOUSE = COMPUTE_WH SCHEDULE = 'USING CRON 0 0 1 1 * UTC'"
            + " IF (EXISTS (SELECT 1)) THEN SELECT 1");
        engine.execute("ALTER ALERT at_db.public.ac RESUME");
        engine.execute("ALTER ALERT at_db.public.ac RESUME");
        assertEquals(idle + 2, scheduler.pendingTimerCount());
        engine.execute("ALTER ALERT at_db.public.ac SUSPEND");
        assertEquals(idle + 1, scheduler.pendingTimerCount());
    }

    @Test
    public void droppingAStartedAlertDisarmsIt() {
        engine.execute("DROP ALERT at_db.public.al");
        assertEquals(idle, scheduler.pendingTimerCount());
        assertFalse(scheduler.isScheduled(KEY));
    }

    @Test
    public void replacingAStartedAlertDisarmsIt() {
        engine.execute("CREATE OR REPLACE ALERT at_db.public.al WAREHOUSE = COMPUTE_WH SCHEDULE = '60 MINUTE'"
            + " IF (EXISTS (SELECT 1)) THEN SELECT 2");
        assertEquals(AlertState.SUSPENDED, alert("AT_DB", "PUBLIC").getState());
        assertEquals(idle, scheduler.pendingTimerCount());
        assertFalse(scheduler.isScheduled(KEY));
    }

    @Test
    public void droppingTheSchemaDisarmsItsStartedAlert() {
        engine.execute("DROP SCHEMA at_db.public");
        assertEquals(idle, scheduler.pendingTimerCount());
        assertFalse(scheduler.isScheduled(KEY));
    }

    @Test
    public void replacingTheSchemaDisarmsItsStartedAlert() {
        engine.execute("CREATE OR REPLACE SCHEMA at_db.public");
        assertEquals(idle, scheduler.pendingTimerCount());
        assertFalse(scheduler.isScheduled(KEY));
    }

    @Test
    public void droppingTheDatabaseDisarmsItsStartedAlert() {
        engine.execute("USE DATABASE keep_db");
        engine.execute("DROP DATABASE at_db");
        assertEquals(idle, scheduler.pendingTimerCount());
        assertFalse(scheduler.isScheduled(KEY));
    }

    @Test
    public void replacingTheDatabaseDisarmsItsStartedAlert() {
        engine.execute("CREATE OR REPLACE DATABASE at_db");
        assertEquals(idle, scheduler.pendingTimerCount());
        assertFalse(scheduler.isScheduled(KEY));
    }

    @Test
    public void undroppingTheSchemaArmsItsStartedAlertAgain() {
        engine.execute("DROP SCHEMA at_db.public");
        engine.execute("UNDROP SCHEMA at_db.public");
        assertEquals(AlertState.STARTED, alert("AT_DB", "PUBLIC").getState());
        assertEquals(idle + 1, scheduler.pendingTimerCount());
        assertTrue(scheduler.isScheduled(KEY));
    }

    @Test
    public void undroppingTheDatabaseArmsItsStartedAlertAgain() {
        engine.execute("USE DATABASE keep_db");
        engine.execute("DROP DATABASE at_db");
        engine.execute("UNDROP DATABASE at_db");
        assertEquals(AlertState.STARTED, alert("AT_DB", "PUBLIC").getState());
        assertEquals(idle + 1, scheduler.pendingTimerCount());
        assertTrue(scheduler.isScheduled(KEY));
    }

    @Test
    public void renamingTheSchemaMovesTheTimerWithTheAlert() {
        engine.execute("CREATE SCHEMA at_db.other");
        engine.execute("CREATE ALERT at_db.other.al WAREHOUSE = COMPUTE_WH SCHEDULE = '60 MINUTE'"
            + " IF (EXISTS (SELECT 1)) THEN SELECT 1");
        engine.execute("ALTER ALERT at_db.other.al RESUME");
        engine.execute("ALTER SCHEMA at_db.other RENAME TO moved");
        assertEquals(idle + 2, scheduler.pendingTimerCount());
        assertTrue(scheduler.isScheduled(TaskScheduler.alertKey("AT_DB", "MOVED", "AL")));
        assertFalse(scheduler.isScheduled(TaskScheduler.alertKey("AT_DB", "OTHER", "AL")));
        assertTrue(scheduler.isScheduled(KEY));
    }

    private Alert alert(final String database, final String schema) {
        return engine.getCatalog().getDatabase(database).getSchema(schema).getAlertExact("AL");
    }
}
