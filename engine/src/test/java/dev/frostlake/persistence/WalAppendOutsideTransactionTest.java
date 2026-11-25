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

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A statement that runs in no transaction (DDL) applies before it is logged. When its append fails, a checkpoint
 * written at once keeps its work and the statement stands; when none can be written, the statement fails and no
 * statement that writes runs until a checkpoint is, so nothing is built on work a restart would not bring back.
 */
public class WalAppendOutsideTransactionTest {

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

    private static WriteAheadLog walOf(final DatabaseEngine engine) throws ReflectiveOperationException {
        final Field walField = DatabaseEngine.class.getDeclaredField("wal");
        walField.setAccessible(true);
        return (WriteAheadLog) walField.get(engine);
    }

    /** Makes every later append of the engine's log fail, checkpoint markers too, as a disk that refuses writes would. */
    private static void breakTheLog(final DatabaseEngine engine) throws ReflectiveOperationException, IOException {
        final Field fileOutField = WriteAheadLog.class.getDeclaredField("fileOut");
        fileOutField.setAccessible(true);
        ((FileOutputStream) fileOutField.get(walOf(engine))).close();
    }

    /** Opens the broken log for appending again, as a disk that accepts writes again would. */
    private static void mendTheLog(final DatabaseEngine engine) throws ReflectiveOperationException {
        final Method open = WriteAheadLog.class.getDeclaredMethod("openForAppend");
        open.setAccessible(true);
        open.invoke(walOf(engine));
    }

    /** Makes the log's next append fail only: a failed append takes its stream back, so the one after it writes. */
    private static void failTheNextAppend(final DatabaseEngine engine) throws ReflectiveOperationException {
        final Field outField = WriteAheadLog.class.getDeclaredField("out");
        outField.setAccessible(true);
        outField.set(walOf(engine), new DataOutputStream(new OutputStream() {
            @Override
            public void write(final int b) throws IOException {
                throw new IOException("injected append failure");
            }
        }));
    }

    private static DatabaseEngine started(final DatabaseEngine engine) {
        engine.execute("CREATE DATABASE d");
        engine.execute("USE SCHEMA d.PUBLIC");
        engine.execute("CREATE TABLE t (i INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        return engine;
    }

    @Test
    public void aStatementTheLogCannotKeepRefusesTheWritesAfterIt() throws ReflectiveOperationException, IOException {
        final DatabaseEngine engine = started(crashingEngine());
        breakTheLog(engine);
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE u (i INT)");
            }
        });
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("INSERT INTO t VALUES (2)");
            }
        });
        assertTrue(refused.getMessage().contains("no statement that writes runs"), refused.getMessage());
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE v (i INT)");
            }
        });
        // A read still runs.
        assertEquals("1", answer(engine, "SELECT i FROM t ORDER BY 1"));
        // The process ends here: what it holds is what ran before the log broke.
        assertEquals("1", recovered("SELECT i FROM d.PUBLIC.t ORDER BY 1"));
        assertEquals("0", recovered(
            "SELECT COUNT(*) FROM d.INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME IN ('U', 'V')"));
    }

    @Test
    public void aCheckpointOnceTheLogMendsLetsWritesRunAgain() throws ReflectiveOperationException, IOException {
        final DatabaseEngine engine = started(crashingEngine());
        breakTheLog(engine);
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE u (i INT)");
            }
        });
        mendTheLog(engine);
        engine.execute("INSERT INTO u VALUES (7)");
        engine.execute("INSERT INTO t VALUES (2)");
        // The process ends here: the checkpoint holds the table the log could not, and the log the rows after it.
        assertEquals("7", recovered("SELECT i FROM d.PUBLIC.u ORDER BY 1"));
        assertEquals("1 | 2", recovered("SELECT i FROM d.PUBLIC.t ORDER BY 1"));
    }

    @Test
    public void aCheckpointWrittenAtOnceKeepsTheStatement() throws ReflectiveOperationException {
        final DatabaseEngine engine = started(crashingEngine());
        failTheNextAppend(engine);
        engine.execute("CREATE TABLE u (i INT)");
        engine.execute("INSERT INTO u VALUES (7)");
        // The process ends here.
        assertEquals("7", recovered("SELECT i FROM d.PUBLIC.u ORDER BY 1"));
        assertEquals("1", recovered("SELECT i FROM d.PUBLIC.t ORDER BY 1"));
    }
}
