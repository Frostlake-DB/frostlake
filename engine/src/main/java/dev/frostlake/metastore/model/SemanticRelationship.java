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
package dev.frostlake.metastore.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * A relationship of a semantic view: the columns of one logical table that reference the key of another. A
 * relationship written without a name is given a system name, which the view's DDL leaves out.
 */
public final class SemanticRelationship implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String name;
    private final boolean nameWritten;
    private final String table;
    private final List<String> foreignKey;
    private final String refTable;
    private final List<String> refKey;

    /**
     * @param name        the relationship's name
     * @param nameWritten whether the definition wrote the name
     * @param table       the referencing logical table
     * @param foreignKey  its columns
     * @param refTable    the referenced logical table
     * @param refKey      the referenced columns: the ones written, or the referenced table's primary key
     */
    public SemanticRelationship(final String name, final boolean nameWritten, final String table,
                                final List<String> foreignKey, final String refTable, final List<String> refKey) {
        this.name = name;
        this.nameWritten = nameWritten;
        this.table = table;
        this.foreignKey = new ArrayList<String>(foreignKey);
        this.refTable = refTable;
        this.refKey = new ArrayList<String>(refKey);
    }

    public String getName() {
        return name;
    }

    public boolean isNameWritten() {
        return nameWritten;
    }

    public String getTable() {
        return table;
    }

    public List<String> getForeignKey() {
        return foreignKey;
    }

    public String getRefTable() {
        return refTable;
    }

    public List<String> getRefKey() {
        return refKey;
    }
}
