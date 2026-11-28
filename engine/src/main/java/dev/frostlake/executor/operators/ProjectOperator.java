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

package dev.frostlake.executor.operators;

import dev.frostlake.executor.DeferredFault;
import dev.frostlake.executor.ExpressionEvaluator;
import dev.frostlake.executor.ProjectionSlot;
import dev.frostlake.executor.RelationBody;
import dev.frostlake.executor.ViewBodyRows;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.ExpressionSource;
import dev.frostlake.executor.expressions.OuterFilters;
import dev.frostlake.executor.expressions.RowOrdinal;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.executor.expressions.UnsupportedSubqueryException;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.storage.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * PROJECT operator - projects (selects) specific columns from rows.
 *
 * This operator handles the SELECT list evaluation for non-aggregate queries.
 * It evaluates expressions for each row and creates new rows with only the
 * selected columns.
 */
public class ProjectOperator implements Operator {
    private static final Logger logger = LoggerFactory.getLogger(ProjectOperator.class);

    /** The rows the account computes one select item over before the next (see firstFaultInItemOrder). */
    private static final int BATCH_ROWS = 256;

    private final List<String> projectionExpressions;
    private List<SourcePosition> expressionOrigins;
    private List<Integer> sourceSlots;
    private final List<String> columnAliases;
    private final RowExpressionEvaluator expressionEvaluator;
    private final Map<String, Object> lateralAliasSink;
    private final Map<String, Object> baseBindings;
    private WhereAliasValues whereAliasValues;
    /** Index-aligned with the expressions: the select item each one is, or -1 for a column a star expands to. */
    private List<Integer> selectItems;
    /** The WHERE of the query this select list belongs to, where {@link #queryFilterKnown}; null for none. */
    private Expression queryFilter;
    private boolean queryFilterKnown;

    /**
     * Create a PROJECT operator.
     *
     * @param projectionExpressions List of expressions to evaluate (e.g., "name", "age + 1", "COUNT(*)")
     * @param columnAliases List of column aliases (null entries allowed for no alias)
     * @param expressionEvaluator Evaluator to evaluate expressions on a row
     */
    public ProjectOperator(final List<String> projectionExpressions,
                          final List<String> columnAliases,
                          final RowExpressionEvaluator expressionEvaluator) {
        this(projectionExpressions, columnAliases, expressionEvaluator, null);
    }

    /**
     * Create a PROJECT operator with lateral column-alias support.
     *
     * @param lateralAliasSink a map SHARED with {@code expressionEvaluator}'s lateral context: as each aliased
     *     SELECT item is evaluated left-to-right, its (uppercased) alias → value is written here so a LATER item
     *     in the same SELECT list can reference it (Snowflake lateral column aliases). Cleared per input row.
     *     When null, no lateral-alias values are exposed to later items (only the exact-whole-item-is-an-alias
     *     shortcut applies).
     */
    public ProjectOperator(final List<String> projectionExpressions,
                          final List<String> columnAliases,
                          final RowExpressionEvaluator expressionEvaluator,
                          final Map<String, Object> lateralAliasSink) {
        this(projectionExpressions, columnAliases, expressionEvaluator, lateralAliasSink, null);
    }

    /**
     * As {@link #ProjectOperator(List, List, RowExpressionEvaluator, Map)}, additionally carrying
     * the OUTER bindings of a LATERAL / correlated execution.
     *
     * @param baseBindings outer-row name → value bindings re-seeded into {@code lateralAliasSink}
     *     after its per-row reset, so a SELECT item may reference an outer alias by qualified name
     *     ({@code SELECT fa.asset_key} inside {@code LEFT JOIN LATERAL (…)}) — the sink is the
     *     evaluator's lateral context, and its per-row clear would otherwise drop them. Item
     *     aliases fill in later, so they shadow a same-named outer key; null when the query does
     *     not execute under an outer row.
     */
    public ProjectOperator(final List<String> projectionExpressions,
                          final List<String> columnAliases,
                          final RowExpressionEvaluator expressionEvaluator,
                          final Map<String, Object> lateralAliasSink,
                          final Map<String, Object> baseBindings) {
        this.projectionExpressions = projectionExpressions;
        this.columnAliases = columnAliases;
        this.expressionEvaluator = expressionEvaluator;
        this.lateralAliasSink = lateralAliasSink;
        this.baseBindings = baseBindings;

        if (columnAliases != null && projectionExpressions.size() != columnAliases.size()) {
            throw new IllegalArgumentException(
                "Number of projection expressions must match number of aliases");
        }
    }

    /** Where item {@code index} began in the statement, or null when it was synthesised. */
    private SourcePosition originOf(final int index) {
        return expressionOrigins != null && index < expressionOrigins.size()
            ? expressionOrigins.get(index) : null;
    }

    /**
     * Where each projection item began in the statement, index-aligned with the expressions. Null
     * entries are synthesised items (star expansion), which nobody wrote and which therefore report
     * no position.
     */
    public void setExpressionOrigins(final List<SourcePosition> expressionOrigins) {
        this.expressionOrigins = expressionOrigins;
    }

    /**
     * The input-row slot each item reads instead of evaluating its expression, index-aligned with the
     * expressions and -1 where the expression is evaluated as usual. A star over a relation holding two
     * columns of one name fills these: each expanded column names itself, and the name alone would read
     * the first one twice, where the star projects them by position.
     */
    public void setSourceSlots(final List<Integer> sourceSlots) {
        this.sourceSlots = sourceSlots;
    }

    /**
     * The values the WHERE computed for the aliases it read, for the rows it kept: an aliased item it computed
     * for a row shows that value instead of being computed again. Null when none are carried.
     */
    public void carryWhereAliasValues(final WhereAliasValues whereAliasValues) {
        this.whereAliasValues = whereAliasValues;
    }

    /**
     * Names the select item each expression is, so a value a stage kept for an item with no alias — one a sort read
     * by its position — is the value shown (see WhereAliasValues).
     *
     * @param selectItems index-aligned with the expressions: the item's place in the select list, from 0, or -1 for
     *                    a column a star expands to
     */
    public void setSelectItems(final List<Integer> selectItems) {
        this.selectItems = selectItems;
    }

    /**
     * Names the WHERE of the query this select list belongs to, which a correlated subquery among its items reads
     * (see OuterFilters).
     *
     * @param queryFilter the query's WHERE condition, or null where it has none
     */
    public void setQueryFilter(final Expression queryFilter) {
        this.queryFilter = queryFilter;
        this.queryFilterKnown = true;
    }

    /** The input-row slot item {@code index} reads, or -1 when it evaluates its expression. */
    private int slotOf(final int index) {
        return sourceSlots != null && index < sourceSlots.size() ? sourceSlots.get(index) : -1;
    }

    @Override
    public List<Row> execute(final List<Row> input, final OperatorContext context) {
        if (projectionExpressions.isEmpty()) {
            logger.debug("No projection expressions, returning input as-is");
            return input;
        }

        logger.debug("Projecting {} expressions across {} rows",
            projectionExpressions.size(), input.size());

        final List<Row> projectedRows = new ArrayList<>();

        // Parse each projection once; evaluate the AST per row (no per-row re-parse).
        final List<Expression> parsedExpressions = new ArrayList<>(projectionExpressions.size());
        for (final String e : projectionExpressions) {
            parsedExpressions.add(ExpressionEvaluator.parse(e.trim()));
        }

        for (int rowIndex = 0; rowIndex < input.size(); rowIndex++) {
            final Row row = input.get(rowIndex);
            // Number the rows this operator was handed, so SEQ1/2/4/8 read one value per row rather
            // than one per call. Restored after each row because a scalar subquery inside the
            // projection runs its own operators, which number their own rows.
            final Long displacedOrdinal = RowOrdinal.begin(rowIndex);
            final Object[] displacedFilters = queryFilterKnown ? OuterFilters.beginSelectList(queryFilter) : null;
            try {
            final List<Object> projectedValues = new ArrayList<>();
            final Map<String, Object> rowAliasValues = beginRowAliases();

            for (int i = 0; i < projectionExpressions.size(); i++) {
                Object value;
                try {
                    value = cellValue(i, row, rowAliasValues, parsedExpressions, context);
                } catch (final RuntimeException failed) {
                    if (RelationBody.isActive() && ViewBodyRows.current() != null
                            && failed instanceof UnsupportedSubqueryException) {
                        // A view's subquery the account cannot evaluate waits for its reader too, and is
                        // refused in the view's name (see ViewBodyRows).
                        value = ViewBodyRows.hold((UnsupportedSubqueryException) failed);
                    } else if (!RelationBody.isActive() || !DeferredFault.deferrable(failed)) {
                        throw firstFaultInItemOrder(failed, i, input, rowIndex, parsedExpressions, context);
                    } else {
                        // A relation's item waits for its reader: the fault stays in the cell.
                        value = new DeferredFault(failed);
                    }
                }
                projectedValues.add(value);
                if (columnAliases != null && columnAliases.get(i) != null) {
                    rowAliasValues.put(columnAliases.get(i).toUpperCase(Locale.ROOT), value);
                }
            }

            projectedRows.add(Row.of(projectedValues));
            } finally {
                RowOrdinal.end(displacedOrdinal);
                if (displacedFilters != null) {
                    OuterFilters.endSelectList(displacedFilters);
                }
            }
        }

        logger.debug("Projection: {} rows -> {} rows with {} columns",
            input.size(), projectedRows.size(), projectionExpressions.size());

        return projectedRows;
    }

    /**
     * The running map of a row's already-computed aliases, emptied for the row. When a shared sink was provided it IS
     * that map (the evaluator reads it as its lateral context, so a later item's expression can reference an earlier
     * alias, e.g. `x + 1 AS y` after `... AS x`); otherwise a private map that still powers the
     * exact-whole-item-is-an-alias shortcut. Reset per row so aliases don't leak.
     */
    private Map<String, Object> beginRowAliases() {
        final Map<String, Object> rowAliasValues = lateralAliasSink != null ? lateralAliasSink : new HashMap<>();
        rowAliasValues.clear();
        if (baseBindings != null) {
            rowAliasValues.putAll(baseBindings);
        }
        return rowAliasValues;
    }

    /** The value item {@code i} shows for a row, computed or read; what its computation raises is thrown. */
    private Object cellValue(final int i, final Row row, final Map<String, Object> rowAliasValues,
                             final List<Expression> parsedExpressions, final OperatorContext context) {
        final String expr = projectionExpressions.get(i).trim();
        final int slot = slotOf(i);
        // A star column that reads its own slot: its name is shared with a sibling, so only the
        // position tells the two apart.
        if (slot >= 0 && slot < row.getValues().size()) {
            return row.getValue(slot);
        }
        // If the expression is exactly a previously-defined alias, reuse that value — but only
        // when no FROM-source column has that name: the real column takes precedence over a
        // same-named sibling alias (what makes a swap projection `SELECT t AS s, s AS t` read
        // both values from the input row instead of collapsing to t, t).
        if (rowAliasValues.containsKey(expr.toUpperCase(Locale.ROOT)) && !isInputColumn(expr, context)
                && !readsOuterRow(i, expr.toUpperCase(Locale.ROOT))) {
            return rowAliasValues.get(expr.toUpperCase(Locale.ROOT));
        }
        // The WHERE computed this item for the row: the value it tested is the value shown.
        if (whereAliasValues != null && columnAliases != null && columnAliases.get(i) != null
                && whereAliasValues.holds(row, columnAliases.get(i).toUpperCase(Locale.ROOT))) {
            return whereAliasValues.valueOf(row, columnAliases.get(i).toUpperCase(Locale.ROOT));
        }
        // A stage kept this item's value for the row by its place: the value it read is the value shown.
        if (whereAliasValues != null && selectItems != null && selectItems.get(i).intValue() >= 0
                && whereAliasValues.holdsItem(row, selectItems.get(i).intValue())) {
            return whereAliasValues.itemValueOf(row, selectItems.get(i).intValue());
        }
        // Note where this item began in the statement, so a message about an unresolvable
        // column inside it can carry the position live always reports. A star-expanded
        // item has no origin — nobody wrote it — and reports none.
        final SourcePosition displaced = ExpressionSource.beginNested(originOf(i));
        try {
            return evaluateExpression(parsedExpressions.get(i), row);
        } finally {
            ExpressionSource.end(displaced);
        }
    }

    /**
     * The fault the projection reports, item by item rather than row by row: the account computes one select item
     * over a batch of {@value #BATCH_ROWS} rows before the next, so of the faults a batch's rows raise the first
     * item's first row's is reported — {@code SELECT column1::NUMBER(10,4), column2::NUMBER(10,4) FROM VALUES
     * (1, 7654321.4321), (1234567.1234, 1)} names 1234567.1234, and a division by zero in a later item gives way to
     * it, while over a table whose row 10 faults the second item and row 256 the first the second item's fault is
     * reported (live-verified). Rows are still computed row by row, so only once a row fault has fired are the
     * earlier items of the batch's later rows computed, to find one that faults first in that order. A fault that is
     * no row fault is reported as it is.
     *
     * @param failed            the fault item {@code item} raised for row {@code rowIndex}
     * @param item              the item that raised it
     * @param input             the rows projected
     * @param rowIndex          the row it raised it for
     * @param parsedExpressions the items' expressions
     * @param context           the operator's context
     * @return the fault to report, its item noted (see ProjectionSlot)
     */
    private RuntimeException firstFaultInItemOrder(final RuntimeException failed, final int item,
                                                   final List<Row> input, final int rowIndex,
                                                   final List<Expression> parsedExpressions,
                                                   final OperatorContext context) {
        RuntimeException first = failed;
        int firstItem = item;
        if (DeferredFault.deferrable(failed)) {
            final int batchEnd = Math.min(input.size(), (rowIndex / BATCH_ROWS + 1) * BATCH_ROWS);
            for (int r = rowIndex + 1; r < batchEnd && firstItem > 0; r++) {
                final Row row = input.get(r);
                final Long displacedOrdinal = RowOrdinal.begin(r);
                try {
                    final Map<String, Object> rowAliasValues = beginRowAliases();
                    for (int i = 0; i < firstItem; i++) {
                        final Object value;
                        try {
                            value = cellValue(i, row, rowAliasValues, parsedExpressions, context);
                        } catch (final RuntimeException earlier) {
                            if (DeferredFault.deferrable(earlier)) {
                                first = earlier;
                                firstItem = i;
                            }
                            break;
                        }
                        if (columnAliases != null && columnAliases.get(i) != null) {
                            rowAliasValues.put(columnAliases.get(i).toUpperCase(Locale.ROOT), value);
                        }
                    }
                } finally {
                    RowOrdinal.end(displacedOrdinal);
                }
            }
        }
        // Note which item this was on the way out, so a DML wrapping this query can
        // name the target column the projection was feeding — see ProjectionSlot.
        ProjectionSlot.failedAt(firstItem);
        return first;
    }

    @Override
    public String getDescription() {
        if (projectionExpressions.size() <= 3) {
            return String.format(Locale.ROOT, "PROJECT[%s]", String.join(", ", projectionExpressions));
        }
        return String.format(Locale.ROOT, "PROJECT[%d columns]", projectionExpressions.size());
    }

    /**
     * Whether an item written as a bare name reads the outer row's binding rather than an earlier item's alias: it
     * is evaluated then, so the read is seen as one of the outer row and the subquery is run again for every row.
     */
    private boolean readsOuterRow(final int item, final String name) {
        if (baseBindings == null || !baseBindings.containsKey(name)) {
            return false;
        }
        if (columnAliases != null) {
            for (int j = 0; j < item && j < columnAliases.size(); j++) {
                if (columnAliases.get(j) != null && name.equals(columnAliases.get(j).toUpperCase(Locale.ROOT))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Whether {@code name} is a real column of the FROM sources feeding this projection (the single/combined
     * input table, or any joined table in scope).
     */
    private boolean isInputColumn(final String name, final OperatorContext context) {
        if (context == null) {
            return false;
        }
        if (context.getTable() != null && context.getTable().hasColumn(name)) {
            return true;
        }
        if (context.getAllTables() != null) {
            for (final Table joined : context.getAllTables()) {
                if (joined != null && joined.hasColumn(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Evaluate an expression on a row.
     */
    private Object evaluateExpression(final Expression expr, final Row row) {
        if (expressionEvaluator == null) {
            throw new IllegalStateException("No expression evaluator provided");
        }

        return expressionEvaluator.evaluate(expr, row);
    }
}
