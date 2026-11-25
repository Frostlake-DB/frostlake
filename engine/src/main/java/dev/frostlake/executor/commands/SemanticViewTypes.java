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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.metastore.model.SemanticExpression;
import dev.frostlake.metastore.model.SemanticExpressionKind;
import dev.frostlake.metastore.model.SemanticTable;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.types.SqlTypeNames;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The types of a semantic view's expressions, each the type the engine answers for the expression as written: a
 * fact or a dimension over its logical table, whose rows also carry the facts and dimensions already typed, a metric
 * over the same rows, and a derived metric over the metrics of the tables. A fact may name a fact defined after it,
 * so the row-level expressions of a table are typed in rounds until none is left or a round types none; the first
 * one left then reports its fault.
 *
 * <p>Each expression is run where it stands in the statement — the query is laid out so that its text keeps its line
 * and position — so a fault in it is reported at its place in the CREATE.
 */
final class SemanticViewTypes {

    private final QueryExecutor queryExecutor;

    SemanticViewTypes(final QueryExecutor queryExecutor) {
        this.queryExecutor = queryExecutor;
    }

    /**
     * Types every expression.
     *
     * @param tables      the logical tables, by name
     * @param expressions the expressions, in the definition's order
     * @param written     the expressions' parse trees, in the same order
     */
    void type(final Map<String, SemanticTable> tables, final List<SemanticExpression> expressions,
              final List<FrostlakeParser.SemanticExpressionContext> written) {
        final Map<String, String> rowRelations = new LinkedHashMap<String, String>();
        for (final SemanticTable table : tables.values()) {
            final String[] relation = {SqlIdentifiers.spellCanonical(table.getDatabaseName()) + "."
                + SqlIdentifiers.spellCanonical(table.getSchemaName()) + "."
                + SqlIdentifiers.spellCanonical(table.getTableName()) + " AS "
                + SqlIdentifiers.spellCanonical(table.getAlias())};
            final List<Integer> pending = new ArrayList<Integer>();
            for (int i = 0; i < expressions.size(); i++) {
                final SemanticExpression expression = expressions.get(i);
                if (table.getAlias().equals(expression.getTable())
                        && (expression.getKind() == SemanticExpressionKind.FACT
                        || expression.getKind() == SemanticExpressionKind.DIMENSION)) {
                    pending.add(i);
                }
            }
            final List<String> baseColumns = columnsOf(relation[0]);
            typeInRounds(pending, expressions, written, new RelationSource() {
                @Override
                public String relation(final SemanticExpression expression) {
                    return relation[0];
                }

                @Override
                public void typed(final SemanticExpression expression) {
                    if (!baseColumns.contains(expression.getName())) {
                        relation[0] = layer(relation[0], table.getAlias(), expression);
                    }
                }
            });
            rowRelations.put(table.getAlias(), relation[0]);
        }
        // The metrics that aggregate, each over its table's rows; the rows of a table's metrics are then one row.
        final Map<String, String> metricLists = new LinkedHashMap<String, String>();
        final List<Integer> pending = new ArrayList<Integer>();
        for (int i = 0; i < expressions.size(); i++) {
            final SemanticExpression expression = expressions.get(i);
            if (expression.getKind() == SemanticExpressionKind.METRIC
                    && queryExecutor.hasAggregateFunctionInExpression(written.get(i).expression())) {
                expression.setDataType(typeOf(written.get(i), rowRelations.get(expression.getTable())));
                final String list = metricLists.get(expression.getTable());
                metricLists.put(expression.getTable(), (list == null ? "" : list + ", ")
                    + expression.getExpression() + " AS " + SqlIdentifiers.spellCanonical(expression.getName()));
            } else if (expression.getKind() == SemanticExpressionKind.METRIC
                    || expression.getKind() == SemanticExpressionKind.DERIVED_METRIC) {
                pending.add(i);
            }
        }
        final Map<String, String> metricRelations = new LinkedHashMap<String, String>();
        for (final Map.Entry<String, String> entry : metricLists.entrySet()) {
            metricRelations.put(entry.getKey(), "(SELECT " + entry.getValue() + " FROM "
                + rowRelations.get(entry.getKey()) + ") AS " + SqlIdentifiers.spellCanonical(entry.getKey()));
        }
        // The metrics over other metrics and the derived metrics, over the metrics of every table.
        typeInRounds(pending, expressions, written, new RelationSource() {
            @Override
            public String relation(final SemanticExpression expression) {
                final StringBuilder joined = new StringBuilder();
                for (final String relation : metricRelations.values()) {
                    joined.append(joined.length() > 0 ? ", " : "").append(relation);
                }
                return joined.length() > 0 ? joined.toString() : "(SELECT 1 AS ONE) AS NO_METRICS";
            }

            @Override
            public void typed(final SemanticExpression expression) {
                final String table = expression.getTable();
                if (table != null && metricRelations.containsKey(table)) {
                    metricRelations.put(table, layer(metricRelations.get(table), table, expression));
                }
            }
        });
    }

    /**
     * Types the pending expressions in rounds, each over the relation the source gives it, until none is left. A
     * round that types none ends it: expressions that name each other in a cycle are refused as such, and otherwise
     * the first one left reports its fault.
     */
    private void typeInRounds(final List<Integer> pending, final List<SemanticExpression> expressions,
                              final List<FrostlakeParser.SemanticExpressionContext> written,
                              final RelationSource source) {
        while (!pending.isEmpty()) {
            RuntimeException first = null;
            final List<Integer> left = new ArrayList<Integer>();
            for (final Integer index : pending) {
                final SemanticExpression expression = expressions.get(index);
                try {
                    expression.setDataType(typeOf(written.get(index), source.relation(expression)));
                } catch (final RuntimeException fault) {
                    if (first == null) {
                        first = fault;
                    }
                    left.add(index);
                    continue;
                }
                source.typed(expression);
            }
            if (left.size() == pending.size()) {
                final String cycle = cycle(left, expressions, written);
                if (cycle != null) {
                    throw new RuntimeException(SqlCompilationError.of(
                        "Cyclic reference of expressions is not allowed. Cycle detected: [" + cycle + "]"));
                }
                throw first;
            }
            pending.clear();
            pending.addAll(left);
        }
    }

    /** The cycle the first of the expressions left starts, as the refusal lists it, or null when it starts none. */
    private static String cycle(final List<Integer> left, final List<SemanticExpression> expressions,
                                final List<FrostlakeParser.SemanticExpressionContext> written) {
        final List<Integer> path = new ArrayList<Integer>();
        Integer current = left.get(0);
        while (current != null && !path.contains(current)) {
            path.add(current);
            Integer next = null;
            for (final String[] reference : references(written.get(current).expression())) {
                for (final Integer candidate : left) {
                    final SemanticExpression named = expressions.get(candidate);
                    if (reference[0].equals(named.getTable()) && reference[1].equals(named.getName())) {
                        next = candidate;
                        break;
                    }
                }
                if (next != null) {
                    break;
                }
            }
            current = next;
        }
        if (current == null) {
            return null;
        }
        final StringBuilder out = new StringBuilder();
        for (int i = path.indexOf(current); i < path.size(); i++) {
            final SemanticExpression expression = expressions.get(path.get(i));
            out.append(out.length() > 0 ? "," : "").append(SqlIdentifiers.spellCanonical(expression.getTable()))
                .append('.').append(SqlIdentifiers.spellCanonical(expression.getName()));
        }
        return out.toString();
    }

    /**
     * The qualified names an expression reads, {@code table.name}, each as canonical parts, off its tokens.
     *
     * @param expression the expression
     * @return the table and the name of each
     */
    static List<String[]> references(final FrostlakeParser.ExpressionContext expression) {
        final List<Token> tokens = new ArrayList<Token>();
        terminals(expression, tokens);
        final List<String[]> found = new ArrayList<String[]>();
        for (int i = 0; i + 2 < tokens.size(); i++) {
            if (tokens.get(i + 1).getType() == FrostlakeParser.DOT && tokens.get(i).getType() != FrostlakeParser.DOT
                    && tokens.get(i + 2).getType() != FrostlakeParser.DOT
                    && (i == 0 || tokens.get(i - 1).getType() != FrostlakeParser.DOT)
                    && (i + 3 >= tokens.size() || tokens.get(i + 3).getType() != FrostlakeParser.DOT)) {
                found.add(new String[] {SqlIdentifiers.canonicalText(tokens.get(i).getText()),
                    SqlIdentifiers.canonicalText(tokens.get(i + 2).getText())});
            }
        }
        return found;
    }

    private static void terminals(final ParseTree node, final List<Token> tokens) {
        if (node instanceof TerminalNode) {
            tokens.add(((TerminalNode) node).getSymbol());
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            terminals(node.getChild(i), tokens);
        }
    }

    /** A relation that also carries the expression, under its name. */
    private static String layer(final String relation, final String alias, final SemanticExpression expression) {
        final String spelled = SqlIdentifiers.spellCanonical(alias);
        return "(SELECT " + spelled + ".*, " + expression.getExpression() + " AS "
            + SqlIdentifiers.spellCanonical(expression.getName()) + " FROM " + relation + ") AS " + spelled;
    }

    private List<String> columnsOf(final String relation) {
        final List<String> names = new ArrayList<String>();
        for (final ResultSetColumn column
                : queryExecutor.executeUnrecorded("SELECT * FROM " + relation + " LIMIT 0").get(0).getColumns()) {
            names.add(column.getName());
        }
        return names;
    }

    /** The type of one expression over a relation, run where the expression stands in the statement. */
    private String typeOf(final FrostlakeParser.SemanticExpressionContext written, final String relation) {
        final FrostlakeParser.ExpressionContext expression = written.expression();
        final Token start = expression.getStart();
        final String text = ParseTreeText.getOriginalText(expression);
        final StringBuilder sql = new StringBuilder("SELECT");
        final int position = start.getCharPositionInLine();
        if (start.getLine() == 1 && position > 6) {
            sql.append(" ".repeat(position - 6));
        } else if (start.getLine() > 1) {
            sql.append("\n".repeat(start.getLine() - 1)).append(" ".repeat(position));
        } else {
            sql.append(' ');
        }
        sql.append(text).append("\nFROM ").append(relation).append("\nLIMIT 0");
        final ResultSetColumn column = queryExecutor.executeUnrecorded(sql.toString()).get(0).getColumns().get(0);
        return SqlTypeNames.canonical(column.getDataType());
    }
}
