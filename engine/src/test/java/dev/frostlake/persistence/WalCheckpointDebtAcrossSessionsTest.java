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

import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.http.SessionContext;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A load that purged its stage files owes a checkpoint, since a restart cannot read those files again. The debt is
 * kept until a checkpoint is written: one held off by another session's open transaction is written once that
 * transaction ends, and one owed inside a transaction falls due at the transaction's end, whichever thread its session
 * sends the COMMIT on.
 */
public class WalCheckpointDebtAcrossSessionsTest {

    @TempDir
    Path dir;

    /** The engines a test leaves running as a process that ended would, shut down once the test is over. */
    private final List<ConcurrentDatabaseEngine> crashed = new ArrayList<ConcurrentDatabaseEngine>();

    @AfterEach
    public void tearDown() {
        for (final ConcurrentDatabaseEngine engine : crashed) {
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
    private ConcurrentDatabaseEngine crashingEngine() {
        final ConcurrentDatabaseEngine engine = new ConcurrentDatabaseEngine(walConfig());
        crashed.add(engine);
        return engine;
    }

    /** A session's statements, answering the last one's first cell. */
    private static String run(final ConcurrentDatabaseEngine engine, final SessionContext session,
                              final String... statements) {
        String last = null;
        for (final String statement : statements) {
            final List<Row> rows = engine.executeQuery(statement, session).getRows();
            last = rows.isEmpty() ? null : String.valueOf(rows.get(0).getValue(0));
        }
        return last;
    }

    /** Creates the tables and the stage, and puts a file of three rows on the stage. */
    private void prepare(final ConcurrentDatabaseEngine engine, final SessionContext session) throws IOException {
        run(engine, session, "CREATE DATABASE d", "USE SCHEMA d.PUBLIC", "CREATE TABLE t (a INT)",
            "CREATE TABLE o (a INT)", "CREATE STAGE st");
        // Written once the engine has started: a file in a data directory with no stages of its own yet reads as a
        // former run's data.
        final Path file = dir.resolve("rows.csv");
        Files.write(file, "1\n2\n3\n".getBytes(StandardCharsets.UTF_8));
        run(engine, session, "PUT 'file://" + file + "' @st AUTO_COMPRESS = FALSE");
    }

    /** What the engine recovering from the log answers, with no replay failing. */
    private static String recovered(final ConcurrentDatabaseEngine engine, final String check) {
        try {
            assertTrue(engine.getEngine().getWalReplayFailures().isEmpty(),
                String.valueOf(engine.getEngine().getWalReplayFailures()));
            return run(engine, engine.createSession(), check);
        } finally {
            engine.shutdown();
        }
    }

    @Test
    public void aCheckpointHeldOffByAnotherSessionsTransactionIsWrittenWhenItEnds() throws IOException {
        final ConcurrentDatabaseEngine engine = crashingEngine();
        final SessionContext loader = engine.createSession();
        final SessionContext other = engine.createSession();
        prepare(engine, loader);
        run(engine, other, "USE SCHEMA d.PUBLIC", "BEGIN", "INSERT INTO o VALUES (99)");
        run(engine, loader, "COPY INTO t FROM @st PURGE = TRUE");
        run(engine, other, "COMMIT");
        assertEquals("3", run(engine, loader, "SELECT COUNT(*) FROM t"));
        assertNull(run(engine, loader, "LIST @st"), "the load purged the stage");
        // The process ends here.
        assertEquals("3", recovered(new ConcurrentDatabaseEngine(walConfig()), "SELECT COUNT(*) FROM d.PUBLIC.t"));
        assertEquals("99", recovered(new ConcurrentDatabaseEngine(walConfig()), "SELECT SUM(a) FROM d.PUBLIC.o"));
    }

    @Test
    public void aLoadInsideATransactionCommittedOnAnotherThreadIsKept() throws IOException, InterruptedException {
        final ConcurrentDatabaseEngine engine = crashingEngine();
        final SessionContext session = engine.createSession();
        prepare(engine, session);
        run(engine, session, "BEGIN");
        final RuntimeException[] failed = {null};
        final Thread loader = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WalCheckpointDebtAcrossSessionsTest.run(engine, session, "COPY INTO t FROM @st PURGE = TRUE");
                } catch (final RuntimeException e) {
                    failed[0] = e;
                }
            }
        });
        loader.start();
        loader.join();
        if (failed[0] != null) {
            throw failed[0];
        }
        run(engine, session, "COMMIT");
        assertEquals("3", run(engine, session, "SELECT COUNT(*) FROM t"));
        // The process ends here.
        assertEquals("3", recovered(new ConcurrentDatabaseEngine(walConfig()), "SELECT COUNT(*) FROM d.PUBLIC.t"));
    }
}
