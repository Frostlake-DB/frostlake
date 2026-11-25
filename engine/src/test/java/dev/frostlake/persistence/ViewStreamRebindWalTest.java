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
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A view stream re-bound by a read or SYSTEM$STREAM_HAS_DATA, after its view's table was dropped and created again or
 * renamed away and brought back by UNDROP, is recovered from the write-ahead log as it was bound: a crash after the
 * re-bind keeps the changes it captured since, and the tables SHOW STREAMS lists it on. The crash is an engine left
 * running while a new one starts on its log.
 */
public class ViewStreamRebindWalTest {

    private final List<DatabaseEngine> engines = new ArrayList<>();
    private Path dir;

    @AfterEach
    public void cleanup() {
        for (final DatabaseEngine engine : engines) {
            engine.shutdown();
        }
        if (dir != null) {
            deleteRecursively(dir.toFile());
        }
    }

    private DatabaseEngine walEngine() throws Exception {
        if (dir == null) {
            dir = Files.createTempDirectory("view_stream_rebind_wal_");
        }
        final EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_DURABILITY_WAL_ENABLED, "true");
        config.setProperty(EngineConfig.PROP_DURABILITY_WAL_FILE, dir.resolve("wal.log").toString());
        final DatabaseEngine engine = new DatabaseEngine(config);
        engines.add(engine);
        return engine;
    }

    private static void run(final DatabaseEngine engine, final String... statements) {
        for (final String sql : statements) {
            engine.execute(sql);
        }
    }

    /** The answer of a statement, its rows joined by " ;; " and their cells by "|", or its refusal. */
    private static String answer(final DatabaseEngine engine, final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder out = new StringBuilder();
            for (final Row row : rs.getRows()) {
                out.append(out.length() > 0 ? " ;; " : "");
                final List<Object> values = row.getValues();
                for (int i = 0; i < values.size(); i++) {
                    out.append(i > 0 ? "|" : "").append(values.get(i));
                }
            }
            return out.toString();
        } catch (final RuntimeException refused) {
            return "REFUSED " + refused.getMessage();
        }
    }

    /** The streams of D.PUBLIC as SHOW STREAMS lists them: name, base tables, stale. */
    private static String listing(final DatabaseEngine engine) {
        engine.execute("SHOW STREAMS IN SCHEMA d.public");
        return answer(engine, "SELECT \"name\", \"base_tables\", \"stale\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"
            + " ORDER BY 1");
    }

    @Test
    public void aReadsRebindIsRecoveredWithTheChangesCapturedSince() throws Exception {
        final DatabaseEngine first = walEngine();
        run(first, "CREATE DATABASE d", "USE SCHEMA d.public", "CREATE TABLE t (a INT)",
            "CREATE VIEW v AS SELECT a FROM t", "CREATE STREAM vs ON VIEW v", "DROP TABLE t", "CREATE TABLE t (a INT)");
        assertEquals("REFUSED SQL compilation error: Change tracking is not enabled or has been missing for the time"
            + " range requested on table 'D.PUBLIC.T'.", answer(first, "SELECT a FROM d.public.vs"));
        run(first, "INSERT INTO d.public.t VALUES (2)", "INSERT INTO d.public.t VALUES (3)");
        assertEquals("2 ;; 3", answer(first, "SELECT a FROM d.public.vs ORDER BY 1"));
        assertEquals("VS|D.PUBLIC.T|false", listing(first));
        final DatabaseEngine recovered = walEngine();
        assertEquals("VS|D.PUBLIC.T|false", listing(recovered));
        assertEquals("2 ;; 3", answer(recovered, "SELECT a FROM d.public.vs ORDER BY 1"));
        assertEquals("ON", answer(recovered, "SHOW TABLES LIKE 'T' IN SCHEMA d.public ->> SELECT \"change_tracking\""
            + " FROM $1"));
    }

    @Test
    public void aHasDataRebindKeepsTheStreamsChangesAcrossACrash() throws Exception {
        final DatabaseEngine first = walEngine();
        run(first, "CREATE DATABASE d", "USE SCHEMA d.public", "CREATE TABLE t (a INT)",
            "CREATE VIEW v AS SELECT a FROM t", "CREATE STREAM vs ON VIEW v", "CREATE STREAM fresh ON VIEW v",
            "INSERT INTO t VALUES (1)", "CREATE TABLE sink (a INT)", "INSERT INTO sink SELECT a FROM fresh",
            "DROP TABLE t", "CREATE TABLE t (a INT)");
        answer(first, "SELECT SYSTEM$STREAM_HAS_DATA('d.public.vs')");
        assertEquals("false", answer(first, "SELECT SYSTEM$STREAM_HAS_DATA('d.public.fresh')"));
        run(first, "INSERT INTO d.public.t VALUES (4)");
        final String listed = listing(first);
        assertEquals("FRESH|D.PUBLIC.T|false ;; VS|D.PUBLIC.T|false", listed);
        final String fresh = answer(first, "SELECT a FROM d.public.fresh ORDER BY 1");
        assertEquals("4", fresh);
        final String kept = answer(first, "SELECT a FROM d.public.vs ORDER BY 1");
        final DatabaseEngine recovered = walEngine();
        assertEquals(listed, listing(recovered));
        assertEquals(fresh, answer(recovered, "SELECT a FROM d.public.fresh ORDER BY 1"));
        assertEquals(kept, answer(recovered, "SELECT a FROM d.public.vs ORDER BY 1"));
    }

    @Test
    public void anUndroppedTableIsListedAsBeforeTheCrash() throws Exception {
        final DatabaseEngine first = walEngine();
        run(first, "CREATE DATABASE d", "USE SCHEMA d.public", "CREATE TABLE t (a INT)",
            "CREATE VIEW v AS SELECT a FROM t", "CREATE STREAM vs ON VIEW v", "INSERT INTO t VALUES (1)",
            "DROP TABLE t", "CREATE TABLE t (a INT)", "INSERT INTO t VALUES (2)");
        assertEquals("REFUSED SQL compilation error: Change tracking is not enabled or has been missing for the time"
            + " range requested on table 'D.PUBLIC.T'.", answer(first, "SELECT a FROM d.public.vs"));
        run(first, "ALTER TABLE d.public.t RENAME TO d.public.t_new", "UNDROP TABLE d.public.t");
        assertEquals("VS|D.PUBLIC.T_NEW|false", listing(first));
        final DatabaseEngine recovered = walEngine();
        assertEquals("VS|D.PUBLIC.T_NEW|false", listing(recovered));
        assertEquals("1", answer(recovered, "SELECT a FROM d.public.vs ORDER BY 1"));
        run(recovered, "INSERT INTO d.public.t VALUES (3)");
        assertEquals("1 ;; 3", answer(recovered, "SELECT a FROM d.public.vs ORDER BY 1"));
        run(recovered, "INSERT INTO d.public.t_new VALUES (4)");
        assertEquals("1 ;; 3", answer(recovered, "SELECT a FROM d.public.vs ORDER BY 1"));
    }

    @Test
    public void aStreamFollowingItsViewsNameKeepsWhatItCarriedAcrossACrash() throws Exception {
        final DatabaseEngine first = walEngine();
        run(first, "CREATE DATABASE d", "USE SCHEMA d.public", "CREATE TABLE t (a INT)",
            "CREATE TABLE u (a INT) CHANGE_TRACKING = TRUE", "CREATE VIEW v AS SELECT a FROM t",
            "CREATE STREAM vs ON VIEW v", "INSERT INTO t VALUES (1)", "INSERT INTO u VALUES (7)",
            "ALTER TABLE t SWAP WITH u");
        final String before = answer(first, "SELECT a FROM d.public.vs ORDER BY 1");
        run(first, "INSERT INTO d.public.t VALUES (8)", "INSERT INTO d.public.u VALUES (9)");
        final String after = answer(first, "SELECT a FROM d.public.vs ORDER BY 1");
        final String listed = listing(first);
        final DatabaseEngine recovered = walEngine();
        assertEquals(listed, listing(recovered));
        assertEquals(after, answer(recovered, "SELECT a FROM d.public.vs ORDER BY 1"));
        assertEquals("7 ;; 8", after, "before the crash the stream read " + before);
    }

    private static void deleteRecursively(final File file) {
        final File[] children = file.listFiles();
        if (children != null) {
            for (final File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }
}
