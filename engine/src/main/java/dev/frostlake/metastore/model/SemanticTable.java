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
 * A logical table of a semantic view: an existing table under the view's name for it, with the keys and the
 * synonyms the definition gives it. Every name is canonical.
 */
public final class SemanticTable implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String alias;
    private final boolean aliasWritten;
    private final String databaseName;
    private final String schemaName;
    private final String tableName;
    private final List<String> primaryKey;
    private final List<List<String>> uniqueKeys;
    private final List<String> synonyms;
    private final String comment;

    /**
     * @param alias        the logical table's name: the alias written, or the table's own name
     * @param aliasWritten whether the definition wrote an alias
     * @param databaseName the table's database
     * @param schemaName   the table's schema
     * @param tableName    the table's name
     * @param primaryKey   the primary key's columns, empty when none is given
     * @param uniqueKeys   the unique keys, each its columns
     * @param synonyms     the synonyms, as written
     * @param comment      the comment, or null
     */
    public SemanticTable(final String alias, final boolean aliasWritten, final String databaseName,
                         final String schemaName, final String tableName, final List<String> primaryKey,
                         final List<List<String>> uniqueKeys, final List<String> synonyms, final String comment) {
        this.alias = alias;
        this.aliasWritten = aliasWritten;
        this.databaseName = databaseName;
        this.schemaName = schemaName;
        this.tableName = tableName;
        this.primaryKey = new ArrayList<String>(primaryKey);
        this.uniqueKeys = new ArrayList<List<String>>(uniqueKeys);
        this.synonyms = new ArrayList<String>(synonyms);
        this.comment = comment;
    }

    public String getAlias() {
        return alias;
    }

    public boolean isAliasWritten() {
        return aliasWritten;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public String getTableName() {
        return tableName;
    }

    public List<String> getPrimaryKey() {
        return primaryKey;
    }

    public List<List<String>> getUniqueKeys() {
        return uniqueKeys;
    }

    public List<String> getSynonyms() {
        return synonyms;
    }

    public String getComment() {
        return comment;
    }
}
