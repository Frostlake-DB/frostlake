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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A checkpoint snapshot recovery cannot read is a recovery failure: a strict engine refuses to start over it, and
 * another lists it among its replay failures and keeps the snapshot, the only copy of what the log no longer holds,
 * whatever checkpoints it writes later.
 */
public class WalUnreadableCheckpointTest {

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

    private EngineConfig walConfig(final boolean strict) {
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_ENABLED, "true");
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_FILE, dir.resolve("wal.log").toString());
        cfg.setProperty(EngineConfig.PROP_DURABILITY_WAL_REPLAY_STRICT, String.valueOf(strict));
        return cfg;
    }

    /** Writes a checkpoint of a table of three rows, leaves the engine running as a crash would, and tears it. */
    private Path tornCheckpoint() throws IOException {
        final DatabaseEngine engine = new DatabaseEngine(walConfig(false));
        crashed.add(engine);
        engine.execute("CREATE DATABASE D");
        engine.execute("CREATE TABLE D.PUBLIC.t (i INT)");
        engine.execute("INSERT INTO D.PUBLIC.t VALUES (1), (2), (3)");
        engine.checkpoint();
        Path snapshot = null;
        try (DirectoryStream<Path> snapshots = Files.newDirectoryStream(dir.resolve("wal-checkpoints"), "cp-*")) {
            for (final Path written : snapshots) {
                snapshot = written;
            }
        }
        assertNotNull(snapshot, "the checkpoint must write its snapshot");
        final Path catalogFile = snapshot.resolve("catalog.dat");
        try (RandomAccessFile file = new RandomAccessFile(catalogFile.toFile(), "rw")) {
            file.setLength(file.length() / 2);
        }
        return snapshot;
    }

    @Test
    public void aStrictEngineRefusesToStart() throws IOException {
        tornCheckpoint();
        final IllegalStateException refused = assertThrows(IllegalStateException.class, new Executable() {
            @Override
            public void execute() {
                crashed.add(new DatabaseEngine(walConfig(true)));
            }
        });
        assertTrue(refused.getMessage().contains("could not be restored"), refused.getMessage());
    }

    @Test
    public void anotherEngineListsTheFailureAndKeepsTheSnapshot() throws IOException {
        final Path snapshot = tornCheckpoint();
        final DatabaseEngine recovered = new DatabaseEngine(walConfig(false));
        try {
            assertEquals(1, recovered.getWalReplayFailures().size(), String.valueOf(recovered.getWalReplayFailures()));
            assertTrue(recovered.getWalReplayFailures().get(0).getMessage().contains(
                "checkpoint snapshot " + snapshot.getFileName()), String.valueOf(recovered.getWalReplayFailures()));
            recovered.checkpoint();
            assertTrue(Files.isRegularFile(snapshot.resolve("catalog.dat")),
                "a later checkpoint must keep the snapshot recovery could not read");
        } finally {
            recovered.shutdown();
        }
    }
}
