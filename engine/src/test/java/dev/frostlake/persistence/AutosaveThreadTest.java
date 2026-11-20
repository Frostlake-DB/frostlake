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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Snapshot persistence saves on a thread of its own every interval. The thread is a daemon, so an engine that is never
 * shut down does not keep the JVM alive, and a save holds the engine as a checkpoint does, so no statement changes
 * the catalog or the tables while the snapshot walks them.
 *
 * <p>Engine internals - the autosave thread is watched from the test - so the class is embedded-only.
 */
public class AutosaveThreadTest {

    @TempDir
    Path dir;

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        final EngineConfig cfg = new EngineConfig();
        cfg.setProperty(EngineConfig.PROP_PERSISTENCE_ENABLED, "true");
        cfg.setProperty(EngineConfig.PROP_PERSISTENCE_DIRECTORY, dir.toString());
        cfg.setProperty(EngineConfig.PROP_PERSISTENCE_SAVE_INTERVAL_SECONDS, "1");
        engine = new DatabaseEngine(cfg);
        engine.execute("CREATE DATABASE ats");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private static List<Thread> autosaveThreads() {
        final List<Thread> found = new ArrayList<>();
        for (final Thread thread : Thread.getAllStackTraces().keySet()) {
            if (PersistenceManager.AUTOSAVE_THREAD.equals(thread.getName())) {
                found.add(thread);
            }
        }
        return found;
    }

    @Test
    public void theAutosaveThreadIsADaemon() {
        final List<Thread> threads = autosaveThreads();
        assertFalse(threads.isEmpty(), "the engine saves on a thread of its own");
        for (final Thread thread : threads) {
            assertTrue(thread.isDaemon(), thread.getName());
        }
    }

    @Test
    public void aSaveWaitsForTheEngine() {
        boolean waited = false;
        synchronized (engine) {
            // A statement run while the test holds the engine leaves the next interval a save to write.
            engine.execute("CREATE TABLE ats.PUBLIC.t (i INT)");
            final ThreadMXBean threads = ManagementFactory.getThreadMXBean();
            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
            while (!waited && System.nanoTime() < deadline) {
                for (final Thread thread : autosaveThreads()) {
                    final ThreadInfo info = threads.getThreadInfo(thread.getId());
                    final LockInfo lock = info == null ? null : info.getLockInfo();
                    if (info != null && info.getThreadState() == Thread.State.BLOCKED && lock != null
                            && lock.getIdentityHashCode() == System.identityHashCode(engine)) {
                        waited = true;
                    }
                }
                Thread.yield();
            }
        }
        assertTrue(waited, "the save waits while the engine is held");
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (!Files.exists(dir.resolve("catalog.dat")) && System.nanoTime() < deadline) {
            Thread.yield();
        }
        assertTrue(Files.exists(dir.resolve("catalog.dat")), "the save goes on once the engine is free");
    }
}
