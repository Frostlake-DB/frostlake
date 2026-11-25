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

import dev.frostlake.executor.StatementClock;
import dev.frostlake.metastore.model.Stream;
import dev.frostlake.metastore.model.StreamVersion;
import dev.frostlake.metastore.model.StreamVersionKind;
import dev.frostlake.storage.SnapshotSequence;
import dev.frostlake.storage.StorageEngine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The changes the running statement makes to the tables streams read where DML applies at once
 * ({@code transaction.deferredApply=false}): the versions each statement writes, which a commit writes from the write
 * set under deferred apply (see StatementTableChange). The statement's changes are gathered as its rows are written
 * and written out as versions once it ends.
 */
public final class ImmediateStatementChanges {

    private final Map<String, StatementTableChange> changes = new LinkedHashMap<>();
    private final Map<String, List<Stream>> capturing = new LinkedHashMap<>();

    /** A row the statement inserted into {@code table}, which {@code streams} read. */
    public synchronized void addInserted(final String table, final List<Stream> streams) {
        change(table, streams, -1L).addInserted();
    }

    /** A row the statement updated in {@code table}, which {@code streams} read. */
    public synchronized void addUpdated(final String table, final List<Stream> streams) {
        change(table, streams, -1L).addUpdated();
    }

    /** A row the statement deleted from {@code table}, which {@code streams} read. */
    public synchronized void addDeleted(final String table, final List<Stream> streams) {
        change(table, streams, -1L).addDeleted();
    }

    /** The statement emptied {@code table}, which held {@code rows} rows and which {@code streams} read. */
    public synchronized void addTruncated(final String table, final List<Stream> streams, final long rows) {
        change(table, streams, -1L).addTruncated(rows);
    }

    /**
     * The statement is a DML statement of one kind writing {@code table}, whether or not it changed a row.
     *
     * @param table     the table, fully qualified
     * @param streams   the streams that read it
     * @param kind      INSERT, UPDATE, DELETE or MERGE
     * @param alone     whether the statement commits alone, not inside a transaction that outlives it
     * @param rewritten whether the statement rewrote the table though it may have changed no row
     */
    public synchronized void noteKind(final String table, final List<Stream> streams, final StreamVersionKind kind,
                                      final boolean alone, final boolean rewritten) {
        final StatementTableChange change = change(table, streams, alone ? -1L : 0L);
        change.noteKind(kind);
        if (rewritten) {
            change.noteRewritten();
        }
    }

    /**
     * Writes the version of each table the statement changed into the streams that read it, and forgets the changes.
     * Inside a transaction that outlives the statement the versions wait for its end: its COMMIT writes them, its
     * ROLLBACK drops them.
     *
     * @param storage the tables' storage, holding each table as the statement left it
     * @param lasting the transaction that outlives the statement, or null where the statement commits alone
     */
    public synchronized void writeVersions(final StorageEngine storage, final Transaction lasting) {
        if (changes.isEmpty()) {
            return;
        }
        for (final Map.Entry<String, StatementTableChange> entry : changes.entrySet()) {
            final StatementTableChange change = entry.getValue();
            if (!storage.hasTable(change.getTable())) {
                continue;
            }
            final long after = storage.getTableStorage(change.getTable()).getRowCount();
            final StreamVersion version = change.version(change.rowsBefore(after), SnapshotSequence.mark());
            if (version == null) {
                continue;
            }
            final List<Stream> streams = capturing.get(entry.getKey());
            if (lasting == null) {
                addVersion(streams, version);
            } else {
                lasting.onOutcome(new TransactionOutcome() {
                    @Override
                    public void committed() {
                        addVersion(streams, version);
                    }

                    @Override
                    public void rolledBack() {
                        // A version of a transaction that rolled back was never written.
                    }
                });
            }
        }
        changes.clear();
        capturing.clear();
    }

    private static void addVersion(final List<Stream> streams, final StreamVersion version) {
        for (final Stream stream : streams) {
            stream.addVersion(version);
        }
    }

    private StatementTableChange change(final String table, final List<Stream> streams, final long ordinal) {
        StatementTableChange change = changes.get(table);
        if (change == null) {
            change = new StatementTableChange(ordinal, table, StatementClock.hostLocal());
            changes.put(table, change);
            capturing.put(table, new ArrayList<>(streams));
        }
        return change;
    }
}
