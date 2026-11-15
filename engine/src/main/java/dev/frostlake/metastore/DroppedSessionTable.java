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

package dev.frostlake.metastore;

import dev.frostlake.metastore.model.Table;
import dev.frostlake.storage.TableStorage;

/**
 * A session's own temporary table that waits with the schema or database dropped beneath it, its rows with it: an
 * UNDROP in the same session brings it back, as the account does (see TemporaryObjects#setOwnSessionTablesAside).
 */
public final class DroppedSessionTable {

    private final String schemaName;
    private final Table table;
    private final TableStorage storage;

    /**
     * @param schemaName the name of the schema that held it
     * @param table      the table
     * @param storage    its rows
     */
    public DroppedSessionTable(final String schemaName, final Table table, final TableStorage storage) {
        this.schemaName = schemaName;
        this.table = table;
        this.storage = storage;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public Table getTable() {
        return table;
    }

    public TableStorage getStorage() {
        return storage;
    }
}
