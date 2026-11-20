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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A record the log cannot read before its end — a zero-filled tail a crash left when the file grew before its bytes
 * were written, or damage — ends the replay there. Recovery cuts it off the file, so what the restarted engine
 * commits follows the last readable record and is read back on every later start; appended behind the unreadable
 * bytes, it was never read again. Bytes other than zeros are kept in a copy beside the log and reported.
 */
public class WalUnreadableTailTest {

    @TempDir
    Path dir;

    private EngineConfig walConfig() {
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_ENABLED, "true");
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_FILE, dir.resolve("wal.log").toString());
        return cfg;
    }

    private static String values(final DatabaseEngine engine) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery("SELECT i FROM d.PUBLIC.t ORDER BY i").getRows()) {
            out.append(out.length() > 0 ? "," : "").append(row.getValue(0));
        }
        return out.toString();
    }

    private void append(final byte[] bytes) throws IOException {
        Files.write(dir.resolve("wal.log"), bytes, StandardOpenOption.APPEND);
    }

    private List<Path> setAsideCopies() throws IOException {
        final List<Path> copies = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(dir, "wal.log.damaged*")) {
            for (final Path entry : entries) {
                copies.add(entry);
            }
        }
        return copies;
    }

    /** Commits one row, then leaves the log with the given bytes behind its last record. */
    private void firstRunThenAppend(final byte[] tail) throws IOException {
        final DatabaseEngine first = new DatabaseEngine(walConfig());
        first.execute("CREATE DATABASE d");
        first.execute("CREATE TABLE d.PUBLIC.t (i INT)");
        first.execute("INSERT INTO d.PUBLIC.t VALUES (1)");
        first.shutdown();
        append(tail);
    }

    /** The second run commits a row; the third must read it back. */
    private void secondAndThirdRun(final int failures) {
        final DatabaseEngine second = new DatabaseEngine(walConfig());
        try {
            assertEquals("1", values(second));
            assertEquals(failures, second.getWalReplayFailures().size(), String.valueOf(second.getWalReplayFailures()));
            second.execute("INSERT INTO d.PUBLIC.t VALUES (2)");
            assertEquals("1,2", values(second));
        } finally {
            second.shutdown();
        }
        final DatabaseEngine third = new DatabaseEngine(walConfig());
        try {
            assertEquals("1,2", values(third), "what the second run committed is read back");
            assertEquals(0, third.getWalReplayFailures().size(), String.valueOf(third.getWalReplayFailures()));
        } finally {
            third.shutdown();
        }
    }

    @Test
    public void aZeroFilledTailIsCutAndLaterCommitsSurvive() throws IOException {
        firstRunThenAppend(new byte[64]);
        secondAndThirdRun(0);
        assertEquals(Collections.<Path>emptyList(), setAsideCopies(), "zeros hold nothing worth keeping");
    }

    @Test
    public void anUnknownRecordIsSetAsideCutAndReported() throws IOException {
        final byte[] tail = new byte[32];
        Arrays.fill(tail, (byte) 0x5A);
        firstRunThenAppend(tail);
        final byte[] before = Files.readAllBytes(dir.resolve("wal.log"));
        secondAndThirdRun(1);
        final List<Path> copies = setAsideCopies();
        assertEquals(1, copies.size(), String.valueOf(copies));
        assertArrayEquals(before, Files.readAllBytes(copies.get(0)), "the copy keeps the log as it was");
    }

    @Test
    public void readingTheLogCutsAnUnreadableRecordOffTheFile() throws IOException {
        final Path walFile = dir.resolve("wal.log");
        final WriteAheadLog log = new WriteAheadLog(walFile);
        try {
            log.appendTransaction(Collections.singletonList("SELECT 1"));
            final long whole = Files.size(walFile);
            append(new byte[] {9, 1, 2, 3});
            assertEquals(1, log.readAll().size());
            assertEquals(whole, Files.size(walFile), "the file ends where its last readable record ends");
            assertEquals(1, log.takeSetAside().size());
            assertTrue(log.takeSetAside().isEmpty(), "taken once");
            log.appendTransaction(Collections.singletonList("SELECT 2"));
            assertEquals(2, log.readAll().size());
            assertEquals("SELECT 2", log.readAll().get(1).getStatements().get(0));
        } finally {
            log.close();
        }
    }
}
