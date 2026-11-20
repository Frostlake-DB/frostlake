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

package dev.frostlake.persistence;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A commit the write-ahead log cannot keep fails whole: its rows are never visible, to its own session or another, so
 * nothing is read or built on that a restart would not bring back. A log a failed compaction left behind stays open
 * for the commits after it.
 */
public class WalAppendFailureTest {

    @TempDir
    Path dir;

    /** The engines a test leaves running as a process that ended would, shut down once the test is over. */
    private final List<DatabaseEngine> crashed = new ArrayList<DatabaseEngine>();

    @AfterEach
    public void tearDown() {
        for (final DatabaseEngine engine : crashed) {
            engine.shutdown();
        }
    }

    private EngineConfig walConfig() {
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_ENABLED, "true");
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_FILE, dir.resolve("wal.log").toString());
        return cfg;
    }

    /** An engine the test leaves running when it restarts, as a process that ended would leave its log. */
    private DatabaseEngine crashingEngine() {
        final DatabaseEngine engine = new DatabaseEngine(walConfig());
        crashed.add(engine);
        return engine;
    }

    /** A query's rows on one line: rows as "a, b | c, d". */
    private static String answer(final DatabaseEngine engine, final String sql) {
        final StringBuilder rows = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            final StringBuilder cells = new StringBuilder();
            for (final Object value : row.getValues()) {
                cells.append(cells.length() > 0 ? ", " : "").append(value);
            }
            rows.append(rows.length() > 0 ? " | " : "").append(cells);
        }
        return rows.toString();
    }

    /** What the engine recovering from the log answers, with no replay failing. */
    private String recovered(final String check) {
        final DatabaseEngine recovered = new DatabaseEngine(walConfig());
        try {
            assertTrue(recovered.getWalReplayFailures().isEmpty(), String.valueOf(recovered.getWalReplayFailures()));
            return answer(recovered, check);
        } finally {
            recovered.shutdown();
        }
    }

    /** Makes every later append of the engine's log fail, as a disk that refuses writes would. */
    private static void breakTheLog(final DatabaseEngine engine) throws ReflectiveOperationException, IOException {
        final Field walField = DatabaseEngine.class.getDeclaredField("wal");
        walField.setAccessible(true);
        final WriteAheadLog wal = (WriteAheadLog) walField.get(engine);
        final Field fileOutField = WriteAheadLog.class.getDeclaredField("fileOut");
        fileOutField.setAccessible(true);
        ((FileOutputStream) fileOutField.get(wal)).close();
    }

    @Test
    public void aCommitTheLogCannotKeepLeavesNoRows() throws ReflectiveOperationException, IOException {
        final DatabaseEngine engine = crashingEngine();
        engine.execute("CREATE DATABASE d");
        engine.execute("USE SCHEMA d.PUBLIC");
        engine.execute("CREATE TABLE t (i INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        breakTheLog(engine);
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("INSERT INTO t VALUES (2)");
            }
        });
        assertEquals("1", answer(engine, "SELECT i FROM t ORDER BY 1"));
        engine.execute("BEGIN");
        engine.execute("INSERT INTO t VALUES (3)");
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("COMMIT");
            }
        });
        assertEquals("1", answer(engine, "SELECT i FROM t ORDER BY 1"));
        assertFalse(engine.getTransactionManager().hasActiveTransaction(), "the failed commit ends the transaction");
        // The process ends here.
        assertEquals("1", recovered("SELECT i FROM d.PUBLIC.t ORDER BY 1"));
    }

    @Test
    public void aFailedCompactionLeavesTheLogOpen() throws IOException {
        final DatabaseEngine engine = crashingEngine();
        engine.execute("CREATE DATABASE d");
        engine.execute("USE SCHEMA d.PUBLIC");
        engine.execute("CREATE TABLE t (i INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        // The compacted log has nowhere to go: a directory holds its name.
        Files.createDirectory(dir.resolve("wal.log.compact"));
        engine.checkpoint();
        engine.execute("INSERT INTO t VALUES (2)");
        assertEquals("1 | 2", answer(engine, "SELECT i FROM t ORDER BY 1"));
        // The process ends here.
        assertEquals("1 | 2", recovered("SELECT i FROM d.PUBLIC.t ORDER BY 1"));
    }
}
