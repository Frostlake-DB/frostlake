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

import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.storage.TableStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A table's virtual columns where its rows are written. No statement names one as a target: {@code INSERT INTO t (a,
 * b)} and {@code UPDATE t SET b = 3} over a virtual B are "Virtual column 'B' is invalid target." (live-verified). A
 * positional INSERT fills the other columns, in order, so {@code INSERT INTO t VALUES (5)} writes a table of a column
 * and a virtual one (live-verified). Each virtual column's value is computed from the row as written, in column
 * order, so one reads the virtual columns defined before it.
 *
 * <p>The account computes the value as a query reads the column, from the expression as it is told back, at that
 * expression's type (see {@link TableColumn#getVirtualRead()}). A write computes it too, for the statements that
 * read a table's stored rows; a query's FROM computes it again as it reads the rows (see {@link #read}), so a text of
 * a TIMESTAMP_LTZ reads in the reading session's time zone and output format (live-verified). A row fault - {@code 1
 * / a} over a zero - therefore never refuses the write: the cell holds it (see {@link DeferredFault}), and only a
 * statement that reads the column raises it, while one that reads the row's other columns answers (live-verified).
 */
public final class VirtualColumns {

    private VirtualColumns() {
    }

    /**
     * Whether a table has a virtual column.
     *
     * @param table the table
     * @return whether one of its columns is virtual
     */
    public static boolean any(final Table table) {
        for (final TableColumn column : table.columnsView()) {
            if (column.isVirtual()) {
                return true;
            }
        }
        return false;
    }

    /**
     * How many columns a positional INSERT fills: the table's own, but for its virtual ones.
     *
     * @param table the table
     * @return the number of columns that are not virtual
     */
    public static int writtenCount(final Table table) {
        int count = 0;
        for (final TableColumn column : table.columnsView()) {
            if (!column.isVirtual()) {
                count++;
            }
        }
        return count;
    }

    /**
     * The columns a positional INSERT fills, in order, where the table has a virtual column.
     *
     * @param table the table
     * @return the names of its columns that are not virtual, or null when none of its columns is virtual
     */
    public static List<String> writtenNames(final Table table) {
        if (!any(table)) {
            return null;
        }
        final List<String> names = new ArrayList<>();
        for (final TableColumn column : table.columnsView()) {
            if (!column.isVirtual()) {
                names.add(column.getName());
            }
        }
        return names;
    }

    /**
     * The columns of a list a query or a positional INSERT fills: all but the virtual ones, in order.
     *
     * @param columns the columns
     * @return those that are not virtual
     */
    public static List<TableColumn> writtenColumns(final List<TableColumn> columns) {
        final List<TableColumn> written = new ArrayList<>();
        for (final TableColumn column : columns) {
            if (!column.isVirtual()) {
                written.add(column);
            }
        }
        return written;
    }

    /**
     * A row of a table that has virtual columns, from the values of its other columns in order: a virtual column's
     * slot holds null until the row is written.
     *
     * @param table  the table
     * @param values the values of its columns that are not virtual
     * @return one value per column of the table
     */
    public static List<Object> spread(final Table table, final List<Object> values) {
        final List<Object> slots = new ArrayList<>(table.columnCount());
        int next = 0;
        for (final TableColumn column : table.columnsView()) {
            slots.add(column.isVirtual() || next >= values.size() ? null : values.get(next++));
        }
        return slots;
    }

    /**
     * Refuses a statement that names a virtual column as a target.
     *
     * @param table      the table written
     * @param columnName the column the statement names, which the table has
     */
    public static void refuseTarget(final Table table, final String columnName) {
        if (table.hasColumn(columnName) && table.getColumn(columnName).isVirtual()) {
            throw invalidTarget(table.getColumn(columnName).getName());
        }
    }

    /**
     * The refusal of a virtual column named as a target.
     *
     * @param name the column's name
     * @return the refusal
     */
    public static RuntimeException invalidTarget(final String name) {
        return new RuntimeException(SqlCompilationError.of("Virtual column '" + name + "' is invalid target."));
    }

    /**
     * Computes a row's virtual columns from its other values, in column order, each converted to its column's type.
     *
     * @param executor the executor whose functions the expressions call
     * @param table    the table the row is written to
     * @param row      the row, its other values already converted to their columns' types
     */
    static void compute(final QueryExecutor executor, final Table table, final Row row) {
        if (any(table)) {
            compute(executor, evaluatorFor(executor, table), table, row);
        }
    }

    /** The evaluator a table's virtual columns are computed with. */
    private static ExpressionEvaluator evaluatorFor(final QueryExecutor executor, final Table table) {
        final ExpressionEvaluator evaluator = new ExpressionEvaluator(table, executor.getFunctionRegistry(),
            executor.getCatalog(), executor);
        final Map<String, Table> aliases = new HashMap<>();
        aliases.put(table.getName().toUpperCase(Locale.ROOT), table);
        final List<Table> relations = new ArrayList<>();
        relations.add(table);
        evaluator.setDeclaredTypeBase(table, aliases, relations);
        return evaluator;
    }

    private static void compute(final QueryExecutor executor, final ExpressionEvaluator evaluator, final Table table,
                                final Row row) {
        final List<TableColumn> columns = table.columnsView();
        for (int i = 0; i < columns.size() && i < row.getValues().size(); i++) {
            final TableColumn column = columns.get(i);
            if (!column.isVirtual()) {
                continue;
            }
            try {
                row.setValue(i, executor.writtenValue(evaluator.evaluate(column.getVirtualRead(), row),
                    column.getDataType()));
            } catch (final RuntimeException fault) {
                if (!DeferredFault.deferrable(fault)) {
                    throw fault;
                }
                row.setValue(i, new DeferredFault(fault));
            }
        }
    }

    /**
     * A table's rows as a query reads them: each virtual column computed again from the row's other values, in the
     * reading session.
     *
     * @param executor the executor whose functions the expressions call
     * @param relation the relation the rows are read as: the table, or its changes, whose leading columns are the
     *                 table's
     * @param table    the table
     * @param rows     the rows read
     * @return the rows, each a copy where the table has a virtual column
     */
    static List<Row> read(final QueryExecutor executor, final Table relation, final Table table,
                          final List<Row> rows) {
        if (!any(table) || relation.columnCount() < table.columnCount()) {
            return rows;
        }
        for (int i = 0; i < table.columnCount(); i++) {
            if (!relation.columnAt(i).getName().equals(table.columnAt(i).getName())) {
                return rows;
            }
        }
        final ExpressionEvaluator evaluator = evaluatorFor(executor, table);
        final List<Row> computed = new ArrayList<>(rows.size());
        for (final Row row : rows) {
            final Row copy = row.copy();
            compute(executor, evaluator, table, copy);
            computed.add(copy);
        }
        return computed;
    }

    /**
     * Computes the virtual columns of every row a table holds, as ALTER TABLE … ADD COLUMN adds one.
     *
     * @param executor the executor whose functions the expressions call
     * @param table    the table, the column already added
     * @param storage  its rows
     */
    static void fill(final QueryExecutor executor, final Table table, final TableStorage storage) {
        for (int i = 0; i < storage.getRowCount(); i++) {
            final Row row = storage.getRow(i).copy();
            compute(executor, table, row);
            storage.replaceRow(i, row);
        }
    }
}
