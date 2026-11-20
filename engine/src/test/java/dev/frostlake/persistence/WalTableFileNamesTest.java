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
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A checkpoint keeps each table's rows in a file of the table's own: two tables whose names join alike with
 * underscores, or differ only in case, which a case-insensitive disk does not tell apart, each get back their own rows
 * after a restart, and a name holding a slash is a file like any other. A snapshot whose tables were kept under their
 * joined names is still restored, and a file it kept for one table is never restored into another.
 */
public class WalTableFileNamesTest {

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

    /** Creates four tables whose names join alike, and one whose name holds a slash, each with rows of its own. */
    private static void createTables(final DatabaseEngine engine) {
        engine.execute("CREATE DATABASE A_B");
        engine.execute("CREATE SCHEMA A_B.C");
        engine.execute("CREATE DATABASE A");
        engine.execute("CREATE SCHEMA A.B_C");
        engine.execute("CREATE TABLE A_B.C.T (x INT)");
        engine.execute("INSERT INTO A_B.C.T VALUES (1), (2), (3)");
        engine.execute("CREATE TABLE A.B_C.T (y STRING)");
        engine.execute("INSERT INTO A.B_C.T VALUES ('only-one')");
        engine.execute("CREATE DATABASE Q");
        engine.execute("CREATE SCHEMA Q.S");
        engine.execute("CREATE TABLE Q.S.\"t\" (x INT)");
        engine.execute("INSERT INTO Q.S.\"t\" VALUES (10)");
        engine.execute("CREATE TABLE Q.S.T (x INT)");
        engine.execute("INSERT INTO Q.S.T VALUES (20), (30)");
        engine.execute("CREATE TABLE Q.S.\"a/b\" (x INT)");
        engine.execute("INSERT INTO Q.S.\"a/b\" VALUES (40)");
    }

    /** The one checkpoint snapshot written, its catalog file there. */
    private Path writtenCheckpoint() throws IOException {
        Path found = null;
        try (DirectoryStream<Path> snapshots = Files.newDirectoryStream(dir.resolve("wal-checkpoints"), "cp-*")) {
            for (final Path snapshot : snapshots) {
                if (Files.isRegularFile(snapshot.resolve("catalog.dat"))) {
                    found = snapshot;
                }
            }
        }
        assertNotNull(found, "the checkpoint must write its snapshot");
        return found;
    }

    /** What the engine recovering from the log answers for each table, with no replay failing. */
    private void assertEachTableKeepsItsRows() {
        final DatabaseEngine recovered = new DatabaseEngine(walConfig());
        try {
            assertTrue(recovered.getWalReplayFailures().isEmpty(), String.valueOf(recovered.getWalReplayFailures()));
            assertEquals("1 | 2 | 3", answer(recovered, "SELECT x FROM A_B.C.T ORDER BY 1"));
            assertEquals("only-one", answer(recovered, "SELECT y FROM A.B_C.T"));
            assertEquals("10", answer(recovered, "SELECT x FROM Q.S.\"t\""));
            assertEquals("20 | 30", answer(recovered, "SELECT x FROM Q.S.T ORDER BY 1"));
            assertEquals("40", answer(recovered, "SELECT x FROM Q.S.\"a/b\""));
        } finally {
            recovered.shutdown();
        }
    }

    @Test
    public void tablesWhoseNamesJoinAlikeKeepTheirOwnRows() throws IOException {
        final DatabaseEngine engine = crashingEngine();
        createTables(engine);
        engine.checkpoint();
        writtenCheckpoint();
        // The process ends here: the restart reads the snapshot, the log holding nothing after its marker.
        assertEachTableKeepsItsRows();
    }

    @Test
    public void aSnapshotKeptUnderJoinedNamesIsStillRestored() throws IOException {
        final DatabaseEngine engine = crashingEngine();
        engine.execute("CREATE DATABASE D");
        engine.execute("CREATE TABLE D.PUBLIC.t1 (i INT)");
        engine.execute("INSERT INTO D.PUBLIC.t1 VALUES (1), (2)");
        engine.execute("CREATE TABLE D.PUBLIC.t2 (s STRING)");
        engine.execute("INSERT INTO D.PUBLIC.t2 VALUES ('two')");
        engine.checkpoint();
        final Path tables = writtenCheckpoint().resolve("tables");
        // As a snapshot written before the digest names kept them.
        toFormerName(tables, "D", "PUBLIC", "T1", tables);
        toFormerName(tables, "D", "PUBLIC", "T2", tables);
        final DatabaseEngine recovered = new DatabaseEngine(walConfig());
        try {
            assertTrue(recovered.getWalReplayFailures().isEmpty(), String.valueOf(recovered.getWalReplayFailures()));
            assertEquals("1 | 2", answer(recovered, "SELECT i FROM D.PUBLIC.t1 ORDER BY 1"));
            assertEquals("two", answer(recovered, "SELECT s FROM D.PUBLIC.t2"));
        } finally {
            recovered.shutdown();
        }
    }

    @Test
    public void aFileKeptForAnotherTableIsNotRestoredIntoThisOne() throws IOException {
        final DatabaseEngine engine = crashingEngine();
        engine.execute("CREATE DATABASE A_B");
        engine.execute("CREATE SCHEMA A_B.C");
        engine.execute("CREATE DATABASE A");
        engine.execute("CREATE SCHEMA A.B_C");
        engine.execute("CREATE TABLE A_B.C.T (x INT)");
        engine.execute("INSERT INTO A_B.C.T VALUES (1), (2), (3)");
        engine.execute("CREATE TABLE A.B_C.T (y STRING)");
        engine.execute("INSERT INTO A.B_C.T VALUES ('only-one')");
        engine.checkpoint();
        final Path tables = writtenCheckpoint().resolve("tables");
        // A snapshot written before the digest names kept both tables in one file, the last one written winning.
        toFormerName(tables, "A", "B_C", "T", tables);
        Files.move(tables.resolve(DiskTableDataStore.fileName("A_B", "C", "T")),
            tables.resolve(DiskTableDataStore.formerFileName("A", "B_C", "T")), StandardCopyOption.REPLACE_EXISTING);
        final DatabaseEngine recovered = new DatabaseEngine(walConfig());
        try {
            assertFalse(recovered.getWalReplayFailures().isEmpty(),
                "a table's rows written for another table must be reported, not restored");
            assertTrue(recovered.getWalReplayFailures().get(0).getMessage().contains("were written for table A_B.C.T"),
                String.valueOf(recovered.getWalReplayFailures()));
        } finally {
            recovered.shutdown();
        }
    }

    /** Renames a table's file from its digest name to the name a snapshot before the digest names gave it. */
    private static void toFormerName(final Path from, final String database, final String schema, final String table,
                                     final Path to) throws IOException {
        Files.move(from.resolve(DiskTableDataStore.fileName(database, schema, table)),
            to.resolve(DiskTableDataStore.formerFileName(database, schema, table)));
    }
}
