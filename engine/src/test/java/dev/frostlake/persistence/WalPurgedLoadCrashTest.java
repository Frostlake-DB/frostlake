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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A load that purged its stage files is kept across a crash that comes before the checkpoint it owes is written: while
 * another session's transaction is open no checkpoint may be written, so the purged files are held beside the log
 * until one is, and a recovery replays the load over them. The crash is an engine left running while a new one starts
 * on the same log.
 */
public class WalPurgedLoadCrashTest {

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
        // Written once the engine has started, in a directory of its own: a file in a data directory with no stages of
        // its own yet reads as a former run's data.
        final Path input = Files.createDirectories(dir.resolve("input"));
        final Path file = input.resolve("rows.csv");
        Files.write(file, "1\n2\n3\n".getBytes(StandardCharsets.UTF_8));
        run(engine, session, "PUT 'file://" + file + "' @st AUTO_COMPRESS = FALSE");
    }

    /** Another session opens a transaction with a logged statement, then the load purges the stage. */
    private ConcurrentDatabaseEngine loadWhileAnotherTransactionIsOpen(final boolean commitAfter) throws IOException {
        final ConcurrentDatabaseEngine engine = crashingEngine();
        final SessionContext loader = engine.createSession();
        final SessionContext other = engine.createSession();
        prepare(engine, loader);
        run(engine, other, "USE SCHEMA d.PUBLIC", "BEGIN", "INSERT INTO o VALUES (99)");
        run(engine, loader, "COPY INTO t FROM @st PURGE = TRUE");
        assertEquals("3", run(engine, loader, "SELECT COUNT(*) FROM t"));
        assertNull(run(engine, loader, "LIST @st"), "the load purged the stage");
        if (commitAfter) {
            run(engine, other, "COMMIT");
        }
        return engine;
    }

    /** The files the hold beside the log keeps. */
    private int heldFiles() throws IOException {
        final Path hold = dir.resolve("wal-purged");
        if (!Files.isDirectory(hold)) {
            return 0;
        }
        int count = 0;
        try (DirectoryStream<Path> generations = Files.newDirectoryStream(hold)) {
            for (final Path generation : generations) {
                try (DirectoryStream<Path> entries = Files.newDirectoryStream(generation)) {
                    for (final Path ignored : entries) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /** What an engine recovering from the log answers to each check, with no replay failing; it is left running. */
    private List<String> recovered(final String... checks) {
        final ConcurrentDatabaseEngine engine = crashingEngine();
        assertTrue(engine.getEngine().getWalReplayFailures().isEmpty(),
            String.valueOf(engine.getEngine().getWalReplayFailures()));
        final SessionContext session = engine.createSession();
        final List<String> answers = new ArrayList<String>();
        for (final String check : checks) {
            answers.add(run(engine, session, check));
        }
        return answers;
    }

    @Test
    public void aCrashWhileAnotherSessionsTransactionIsOpenKeepsThePurgedLoad() throws IOException {
        loadWhileAnotherTransactionIsOpen(false);
        // The process ends here, the other session's transaction still open.
        final List<String> answers = recovered("SELECT COUNT(*) FROM d.PUBLIC.t", "SELECT COUNT(*) FROM d.PUBLIC.o",
            "LIST @d.PUBLIC.st");
        assertEquals("3", answers.get(0));
        assertEquals("0", answers.get(1), "the open transaction's insert never committed");
        assertNull(answers.get(2), "the purged files stay purged");
        assertEquals(0, heldFiles(), "the recovery wrote the checkpoint the load owed");
        // A second crash, straight after the recovery, still finds the load.
        final List<String> again = recovered("SELECT SUM(a) FROM d.PUBLIC.t", "LIST @d.PUBLIC.st");
        assertEquals("6", again.get(0));
        assertNull(again.get(1));
    }

    @Test
    public void thePurgedFileIsHeldWhileTheCheckpointWaits() throws IOException {
        final ConcurrentDatabaseEngine engine = loadWhileAnotherTransactionIsOpen(false);
        assertTrue(heldFiles() > 0, "the purged file waits for the checkpoint beside the log");
        assertEquals(0, engine.getEngine().getWalReplayFailures().size());
        final SessionContext other = engine.createSession();
        run(engine, other, "SELECT 1");
        assertTrue(heldFiles() > 0, "no checkpoint while the other transaction is open");
    }

    @Test
    public void aCrashAfterTheOtherTransactionCommitsKeepsBoth() throws IOException {
        loadWhileAnotherTransactionIsOpen(true);
        assertEquals(0, heldFiles(), "the owed checkpoint was written when the transaction ended");
        // The process ends here.
        final List<String> answers = recovered("SELECT COUNT(*) FROM d.PUBLIC.t", "SELECT SUM(a) FROM d.PUBLIC.o",
            "LIST @d.PUBLIC.st");
        assertEquals("3", answers.get(0));
        assertEquals("99", answers.get(1));
        assertNull(answers.get(2));
    }
}
