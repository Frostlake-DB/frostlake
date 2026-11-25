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

import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.KeyContexts;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * A {@code LISTAGG} or {@code ARRAY_AGG} that takes DISTINCT values and orders them WITHIN GROUP: each sort key must
 * be computed from the distinct value alone, since the rows that collapse into one value can hold different keys
 * (live-verified):
 *
 * <pre>
 *   LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY x DESC)       ordered
 *   LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY UPPER(x))     ordered
 *   LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY 1)            a constant orders nothing
 *   LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY y)            [LA.Y] is not a valid order by expression
 *   LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY y + g)        [LA.Y + LA.G] is not a valid order by expression
 *   LISTAGG(DISTINCT UPPER(x), ',') WITHIN GROUP (ORDER BY x)     [LA.X] is not a valid order by expression
 *   LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY RANDOM())     [RANDOM()] is not a valid order by expression
 *   LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY SUM(y))       [SUM(LA.Y)] is not a valid order by expression
 * </pre>
 *
 * A key is valid when every column it reads is read through the distinct value itself, and it calls no aggregate,
 * no window and no function that answers anew for each row. The first invalid key is named whole as the plan holds
 * it - its columns qualified, a CASE as {@code CASE_FLATTENED(LA.Y = 1, 2, 1)}, a predicate cast to BOOLEAN; an unknown name in it is refused as the invalid identifier it is first, and a
 * LISTAGG delimiter that is no constant is refused by its own rule ahead of this one. The windowed call is judged the
 * same way.
 */
final class DistinctWithinGroupRule {

    /** The aggregates the rule covers. */
    private static final Set<String> ORDERED_COLLECTORS = Set.of("LISTAGG", "ARRAY_AGG", "ARRAYAGG");

    /** The functions that answer a new value for every row, which no distinct value determines. */
    private static final Set<String> PER_ROW = Set.of("RANDOM", "SEQ1", "SEQ2", "SEQ4", "SEQ8", "UUID_STRING");

    private final QueryExecutor executor;
    private final FunctionRegistry functionRegistry;
    private final Catalog catalog;

    DistinctWithinGroupRule(final QueryExecutor executor, final FunctionRegistry functionRegistry,
                            final Catalog catalog) {
        this.executor = executor;
        this.functionRegistry = functionRegistry;
        this.catalog = catalog;
    }

    /**
     * Judge every such call under {@code node}, outside the queries nested in it.
     *
     * @param node         any parse-tree node; the walk covers the statement
     * @param table        the leading relation
     * @param aliasToTable the FROM's alias map, or null
     * @param allTables    every relation in the FROM, or null
     */
    void validate(final ParseTree node, final Table table, final Map<String, Table> aliasToTable,
                  final List<Table> allTables) {
        if (node == null) {
            return;
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext call = (FrostlakeParser.FunctionCallExprContext) node;
            final String name =
                SqlIdentifiers.canonicalText(call.functionName().getText()).toUpperCase(Locale.ROOT);
            if (call.DISTINCT() != null && ORDERED_COLLECTORS.contains(name)) {
                validateCall(call, table, aliasToTable, allTables);
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            // A nested select's calls are judged against its own relations when it compiles.
            if (!(node.getChild(i) instanceof FrostlakeParser.SelectStatementContext)) {
                validate(node.getChild(i), table, aliasToTable, allTables);
            }
        }
    }

    private void validateCall(final FrostlakeParser.FunctionCallExprContext call, final Table table,
                              final Map<String, Table> aliasToTable, final List<Table> allTables) {
        final FrostlakeParser.OrderByClauseContext withinGroup = AggregateFunctions.withinGroupOrderBy(call);
        final List<FrostlakeParser.BooleanExprContext> args =
            ParseTreeText.functionBooleanArgs(call.functionArgList());
        // A delimiter that reads a column is refused as no constant first (live-verified), by its own rule.
        if (withinGroup == null || args.isEmpty() || args.size() > 1 && readsAColumn(args.get(1))) {
            return;
        }
        final PlanEcho echo = new PlanEcho(table, aliasToTable, allTables, functionRegistry, catalog);
        final String distinct = echo.print(args.get(0));
        for (final FrostlakeParser.OrderItemContext item : withinGroup.orderItem()) {
            final ParserRuleContext key = KeyContexts.of(item);
            if (!derivable(key, distinct, echo)) {
                executor.validateClauseScope(ParseTreeText.getOriginalText(key), table, aliasToTable, allTables,
                    Collections.<String>emptySet(), key);
                throw new RuntimeException(SqlCompilationError.of(
                    "[" + echo.printPlannedSortKey(key) + "] is not a valid order by expression"));
            }
        }
    }

    private static boolean readsAColumn(final ParseTree node) {
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (readsAColumn(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    /** Whether a key, or a part of one, is computed from the distinct value alone. */
    private boolean derivable(final ParseTree node, final String distinct, final PlanEcho echo) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return true;
        }
        if (node instanceof ParserRuleContext && distinct.equals(echo.print((ParserRuleContext) node))) {
            return true;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            return false;
        }
        if (node instanceof FrostlakeParser.FunctionCallStarExprContext) {
            return false;
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext call = (FrostlakeParser.FunctionCallExprContext) node;
            final String name =
                SqlIdentifiers.canonicalText(call.functionName().getText()).toUpperCase(Locale.ROOT);
            if (call.overClause() != null || functionRegistry.hasAggregateFunction(name) || PER_ROW.contains(name)) {
                return false;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (!derivable(node.getChild(i), distinct, echo)) {
                return false;
            }
        }
        return true;
    }
}
