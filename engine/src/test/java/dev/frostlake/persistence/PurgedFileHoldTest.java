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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hold a purge keeps its files in until a checkpoint holds their load: a recovery puts back only what a checkpoint
 * of a later generation does not hold, never over a file staged since, and a replayed purge takes back only what the
 * recovery put back.
 */
public class PurgedFileHoldTest {

    @TempDir
    Path dir;

    private Path staged(final String name, final String content) throws IOException {
        final Path file = Files.createDirectories(dir.resolve("stage")).resolve(name);
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static String read(final Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    @Test
    public void aRecoveryPutsBackWhatTheCheckpointItStartsFromDoesNotHold() throws IOException {
        final PurgedFileHold hold = new PurgedFileHold(dir.resolve("hold"));
        final Path before = staged("before.csv", "1");
        final Path after = staged("after.csv", "2");
        hold.setGeneration(3);
        hold.hold(before);
        hold.setGeneration(4);
        hold.hold(after);
        assertFalse(Files.exists(before));
        assertFalse(Files.exists(after));
        // Recovering from cp-4: it holds the load purged under generation 3.
        assertEquals(1, hold.restore(4));
        assertFalse(Files.exists(before));
        assertEquals("2", read(after));
        hold.purgedOnReplay(before);
        hold.purgedOnReplay(after);
        assertFalse(Files.exists(after), "the replayed load purged it again");
        hold.replayEnded();
        // The hold still keeps it until a checkpoint does.
        assertEquals(1, hold.restore(4));
        hold.replayEnded();
        hold.release(5);
        Files.delete(after);
        assertEquals(0, hold.restore(0));
    }

    @Test
    public void aFileStagedSinceUnderTheSameNameStays() throws IOException {
        final PurgedFileHold hold = new PurgedFileHold(dir.resolve("hold"));
        final Path file = staged("rows.csv", "old");
        hold.hold(file);
        staged("rows.csv", "new");
        assertEquals(0, hold.restore(0));
        hold.purgedOnReplay(file);
        assertTrue(Files.exists(file));
        assertEquals("new", read(file));
    }
}
