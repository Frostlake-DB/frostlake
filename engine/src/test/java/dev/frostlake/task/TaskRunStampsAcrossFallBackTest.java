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
import dev.frostlake.ExecutionResult;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.metastore.model.TaskExecution;
import dev.frostlake.metastore.model.TaskExecutionState;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A task's runs keep the instants they were stamped at. On a host whose clock fell back, a run in the repeated hour
 * is told at its own offset: TASK_HISTORY and COMPLETE_TASK_GRAPHS report its true times and run id and order it
 * after a run of the hour's first pass, and a snapshot restart reads the same. A task's creation time is an instant
 * too. Each run and creation time is set on the engine directly, since no SQL reaches a past night.
 */
public class TaskRunStampsAcrossFallBackTest {

    /** A host zone whose clock fell back from 2:00 PDT to 1:00 PST on 2026-11-01, so 1:00 to 2:00 came twice. */
    private static final String HOST_ZONE = "America/Los_Angeles";

    /** 01:40 PDT, the hour's first pass. */
    private static final Instant FIRST_PASS = Instant.parse("2026-11-01T08:40:00Z");

    /** 01:10 PST, the hour's second pass: half an hour after the first run, though its wall clock reads earlier. */
    private static final Instant SECOND_PASS = Instant.parse("2026-11-01T09:10:00Z");

    private static final String HISTORY = "SELECT scheduled_time, query_start_time, completed_time, run_id"
        + " FROM TABLE(d1.INFORMATION_SCHEMA.TASK_HISTORY()) ORDER BY query_start_time";

    private static final String GRAPHS = "SELECT scheduled_time, query_start_time, completed_time, run_id"
        + " FROM TABLE(d1.INFORMATION_SCHEMA.COMPLETE_TASK_GRAPHS()) ORDER BY query_start_time";

    private static final String CREATED = "SHOW TASKS LIKE 'TK1' IN SCHEMA d1.s1 ->> SELECT \"created_on\" FROM $1";

    private static final String RUNS = "2026-11-01T08:40:00Z|2026-11-01T08:40:00.250Z|2026-11-01T08:40:01Z"
        + "|1793522400000 ;; 2026-11-01T09:10:00Z|2026-11-01T09:10:00.500Z|2026-11-01T09:10:02Z|1793524200000";

    private static TimeZone priorZone;

    private Path dir;

    @BeforeAll
    public static void pinHostZone() {
        priorZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone(HOST_ZONE));
    }

    @AfterAll
    public static void restoreHostZone() {
        TimeZone.setDefault(priorZone);
    }

    @BeforeEach
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("task_run_stamps_");
    }

    @AfterEach
    public void tearDown() throws IOException {
        deleteRecursively(dir);
    }

    @Test
    public void runsInTheRepeatedHourKeepTheirOffsetAcrossARestart() {
        final DatabaseEngine first = snapshotEngine();
        first.execute("CREATE DATABASE d1");
        first.execute("CREATE SCHEMA d1.s1");
        first.execute("CREATE TASK d1.s1.tk1 SCHEDULE = '60 MINUTE' AS SELECT 1");
        final Task task = first.getCatalog().getDatabase("D1").getSchema("S1").getTask("TK1");
        task.recordExecution(run(FIRST_PASS, 250, 1000));
        task.recordExecution(run(SECOND_PASS, 500, 2000));
        task.restoreCreatedTime(Instant.parse("2026-11-01T09:20:00Z"));
        first.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
        assertEquals(RUNS, cells(first, HISTORY));
        assertEquals(RUNS, cells(first, GRAPHS));
        assertEquals("2026-11-01T09:20:00Z", cells(first, CREATED));
        first.shutdown();

        final DatabaseEngine second = snapshotEngine();
        try {
            second.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
            assertEquals(RUNS, cells(second, HISTORY), "after a snapshot restart");
            assertEquals(RUNS, cells(second, GRAPHS), "after a snapshot restart");
            assertEquals("2026-11-01T09:20:00Z", cells(second, CREATED), "after a snapshot restart");
        } finally {
            second.shutdown();
        }
    }

    /** A scheduled run that succeeded, started and completed the given milliseconds after its scheduled time. */
    private static TaskExecution run(final Instant scheduled, final long startMillis, final long endMillis) {
        return new TaskExecution(scheduled, scheduled.plusMillis(startMillis), scheduled.plusMillis(endMillis),
            TaskExecutionState.SUCCEEDED, null, 0, TaskTrigger.SCHEDULE, 1);
    }

    private DatabaseEngine snapshotEngine() {
        final EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_PERSISTENCE_ENABLED, "true");
        config.setProperty(EngineConfig.PROP_PERSISTENCE_DIRECTORY, dir.resolve("data").toString());
        config.setProperty(EngineConfig.PROP_PERSISTENCE_AUTO_SAVE, "false");
        return new DatabaseEngine(config);
    }

    /** A query's cells, a timestamp as its instant, joined by {@code |} within a row and {@code ;;} between rows. */
    private static String cells(final DatabaseEngine engine, final String sql) {
        final ExecutionResult executed = engine.execute(sql);
        assertTrue(executed.isSuccess(), executed.getErrorMessage());
        final List<ResultSet> results = executed.getResultSets();
        final ResultSet result = results.get(results.size() - 1);
        final StringBuilder text = new StringBuilder();
        for (final Row row : result.getRows()) {
            if (text.length() > 0) {
                text.append(" ;; ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                final Object cell = row.getValue(i);
                if (i > 0) {
                    text.append('|');
                }
                text.append(cell instanceof OffsetDateTime ? ((OffsetDateTime) cell).toInstant() : cell);
            }
        }
        return text.toString();
    }

    private static void deleteRecursively(final Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> children = Files.newDirectoryStream(path)) {
                for (final Path child : children) {
                    deleteRecursively(child);
                }
            }
        }
        Files.deleteIfExists(path);
    }
}
