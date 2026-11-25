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

package dev.frostlake.transaction;

import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.http.SessionContext;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.StorageEngine;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A table replaced while another session's open transaction has written it: the transaction's changes stay with the
 * storage it wrote. Where DML applies at once, its ROLLBACK undoes them there, never in the table that took the name;
 * and the storage set aside for it is let go once the dropped version is purged and the transaction has ended.
 */
public class ReplacedTableTransactionLeftoversTest {

    private static final String SET_ASIDE_KEY = "DB.S.T#SET_ASIDE_1";

    private ConcurrentDatabaseEngine engine;

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private SessionContext[] twoSessions(final boolean deferredApply, final String tableOptions) {
        final EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_TRANSACTION_DEFERRED_APPLY, String.valueOf(deferredApply));
        engine = new ConcurrentDatabaseEngine(config);
        final SessionContext first = engine.createSession();
        final SessionContext second = engine.createSession();
        engine.execute("CREATE DATABASE db", first);
        engine.execute("CREATE SCHEMA db.s", first);
        engine.execute("USE SCHEMA db.s", first);
        engine.execute("USE SCHEMA db.s", second);
        engine.execute("CREATE TABLE t (id INTEGER)" + tableOptions, first);
        engine.execute("INSERT INTO t VALUES (5)", first);
        return new SessionContext[] {first, second};
    }

    private String ids(final SessionContext session) {
        final ResultSet rows = engine.executeQuery("SELECT id FROM t ORDER BY id", session);
        final StringBuilder text = new StringBuilder();
        for (int i = 0; i < rows.getRowCount(); i++) {
            if (i > 0) {
                text.append(',');
            }
            text.append(rows.getRows().get(i).getValue(0));
        }
        return text.toString();
    }

    @Test
    public void immediateApplyRollbackUndoesInTheReplacedTable() {
        final SessionContext[] sessions = twoSessions(false, "");
        engine.execute("BEGIN", sessions[0]);
        engine.execute("UPDATE t SET id = 6", sessions[0]);
        engine.execute("INSERT INTO t VALUES (7)", sessions[0]);
        engine.execute("CREATE OR REPLACE TABLE t (id INTEGER)", sessions[1]);
        engine.execute("INSERT INTO t VALUES (10), (20)", sessions[1]);
        engine.execute("ROLLBACK", sessions[0]);
        assertEquals("10,20", ids(sessions[1]), "the table that took the name keeps its rows");
    }

    @Test
    public void immediateApplyRollbackRestoresTheVersionItWrote() {
        final SessionContext[] sessions = twoSessions(false, "");
        engine.execute("BEGIN", sessions[0]);
        engine.execute("INSERT INTO t VALUES (7)", sessions[0]);
        engine.execute("CREATE OR REPLACE TABLE t (id INTEGER)", sessions[1]);
        engine.execute("ROLLBACK", sessions[0]);
        assertEquals("", ids(sessions[1]));
        engine.execute("ALTER TABLE t RENAME TO t_new", sessions[1]);
        engine.execute("UNDROP TABLE t", sessions[1]);
        assertEquals("5", ids(sessions[1]), "the replaced version comes back without the rolled-back row");
    }

    @Test
    public void setAsideStorageHeldForACommittingTransactionIsReleased() {
        assertHeldUntilEnd(true, "COMMIT");
    }

    @Test
    public void setAsideStorageHeldForARollingBackTransactionIsReleased() {
        assertHeldUntilEnd(true, "ROLLBACK");
    }

    @Test
    public void setAsideStorageHeldUnderImmediateApplyIsReleased() {
        assertHeldUntilEnd(false, "ROLLBACK");
    }

    private void assertHeldUntilEnd(final boolean deferredApply, final String end) {
        final SessionContext[] sessions = twoSessions(deferredApply, " DATA_RETENTION_TIME_IN_DAYS = 0");
        final StorageEngine storage = engine.getEngine().getStorageEngine();
        engine.execute("BEGIN", sessions[0]);
        engine.execute("INSERT INTO t VALUES (7)", sessions[0]);
        engine.execute("CREATE OR REPLACE TABLE t (id INTEGER)", sessions[1]);
        // Listing the history purges the version of no retention; its storage stays for the open transaction.
        engine.executeQuery("SHOW TABLES HISTORY LIKE 'T' IN SCHEMA db.s", sessions[1]);
        assertNotNull(storage.retiredStorage(SET_ASIDE_KEY), "kept while the transaction that wrote it is open");
        engine.execute(end, sessions[0]);
        assertNull(storage.retiredStorage(SET_ASIDE_KEY), "let go once that transaction ends");
        assertEquals("", ids(sessions[1]));
    }
}
