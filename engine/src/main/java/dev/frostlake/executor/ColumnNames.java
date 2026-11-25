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

package dev.frostlake.executor;

import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.metastore.model.Table;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * How a condition names a table's columns (see InsertSourcePruning): by the names the queries over the table read it
 * by, and, written in a query that reads the table through others, by the names those hand its columns on as.
 */
final class ColumnNames {

    private final Table table;
    private final List<String> qualifiers;
    private final Map<String, String> renames;

    /**
     * @param table      the table
     * @param qualifiers the names the condition may qualify a column with
     * @param renames    the column each name the condition reads stands for, upper-cased, where it differs
     */
    ColumnNames(final Table table, final List<String> qualifiers, final Map<String, String> renames) {
        this.table = table;
        this.qualifiers = qualifiers;
        this.renames = renames;
    }

    Table table() {
        return table;
    }

    /**
     * The table's column a bare or qualified column reference names.
     *
     * @param expression the expression
     * @return the column's position, or -1 where it names none
     */
    int of(final Expression expression) {
        if (!(expression instanceof ColumnReferenceExpression)) {
            return -1;
        }
        final ColumnReferenceExpression reference = (ColumnReferenceExpression) expression;
        if (reference.getTableName() != null && !qualifies(reference.getTableName())) {
            return -1;
        }
        String name = reference.getColumnName();
        if (name == null) {
            return -1;
        }
        final String renamed = renames.get(name.toUpperCase(Locale.ROOT));
        if (renamed != null) {
            name = renamed;
        }
        return !name.isEmpty() && table.hasColumn(name) ? table.getColumnIndex(name) : -1;
    }

    private boolean qualifies(final String qualifier) {
        if (qualifier.equalsIgnoreCase(table.getName())) {
            return true;
        }
        for (final String name : qualifiers) {
            if (qualifier.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }
}
