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

package dev.frostlake.stream;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.metastore.model.Stream;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A stream holds the change records from its offset on: the ones a committed read consumed are read by nothing again
 * and are dropped, so a stream that is read as fast as its table changes holds no history, however long it runs. What
 * the stream lists is unchanged: a transaction's own changes stay for the next read, and a column added or dropped
 * reshapes the records still held.
 *
 * <p>Engine internals (the records a stream holds), so the class is embedded-only.
 */
public class StreamConsumedRecordsTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE scr");
        engine.execute("USE SCHEMA scr.PUBLIC");
        engine.execute("CREATE TABLE t (i INT, v VARCHAR) DATA_RETENTION_TIME_IN_DAYS = 0");
        engine.execute("CREATE STREAM s ON TABLE t");
        engine.execute("CREATE TABLE sink (n INT)");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private Stream stream() {
        return engine.getCatalog().getDatabase("SCR").getSchema("PUBLIC").getStream("S");
    }

    private String answer(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.append(out.length() > 0 ? " ; " : "").append(row.getValues());
        }
        return out.toString();
    }

    @Test
    public void consumedRecordsAreDropped() {
        for (int i = 0; i < 50; i++) {
            engine.execute("INSERT INTO t VALUES (" + i + ", REPEAT('x', 1000))");
            engine.execute("DELETE FROM t");
            engine.execute("INSERT INTO sink SELECT COUNT(*) FROM s");
        }
        assertEquals(0, stream().heldRecordCount(), "every record was consumed");
        assertEquals("[0]", answer("SELECT COUNT(*) FROM s"));
        engine.execute("INSERT INTO t VALUES (7, 'a')");
        assertEquals(1, stream().heldRecordCount());
        assertEquals("[7, a, INSERT, false]",
            answer("SELECT i, v, METADATA$ACTION, METADATA$ISUPDATE FROM s"));
        engine.execute("INSERT INTO sink SELECT i FROM s");
        assertEquals(0, stream().heldRecordCount());
        assertEquals("[0]", answer("SELECT COUNT(*) FROM s"));
    }

    @Test
    public void aTransactionsOwnChangesStayHeldForTheNextRead() {
        engine.execute("INSERT INTO t VALUES (1, 'a')");
        engine.execute("BEGIN");
        engine.execute("INSERT INTO sink SELECT i FROM s");
        engine.execute("INSERT INTO t VALUES (2, 'b')");
        engine.execute("COMMIT");
        assertEquals(1, stream().heldRecordCount(), "the record read before the transaction's own one is dropped");
        assertEquals("[2, b, INSERT]", answer("SELECT i, v, METADATA$ACTION FROM s"));
        engine.execute("INSERT INTO sink SELECT i FROM s");
        assertEquals("[1] ; [2]", answer("SELECT n FROM sink ORDER BY n"));
        assertEquals(0, stream().heldRecordCount());
    }

    @Test
    public void columnsAddedAndDroppedReshapeTheHeldRecords() {
        engine.execute("INSERT INTO t VALUES (1, 'a')");
        engine.execute("INSERT INTO sink SELECT i FROM s");
        engine.execute("INSERT INTO t VALUES (2, 'b')");
        engine.execute("ALTER TABLE t ADD COLUMN w INT DEFAULT 5");
        engine.execute("ALTER TABLE t DROP COLUMN v");
        assertEquals(1, stream().heldRecordCount());
        assertEquals("[2, 5, INSERT]", answer("SELECT i, w, METADATA$ACTION FROM s"));
    }
}
