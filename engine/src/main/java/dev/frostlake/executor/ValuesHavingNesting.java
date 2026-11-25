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

import dev.frostlake.executor.expressions.ExpressionSource;
import dev.frostlake.executor.expressions.PlannedCorrelations;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.executor.expressions.SubqueryAnchor;
import dev.frostlake.executor.expressions.UnsupportedSubqueryException;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * A grouped query over a VALUES list - written with an alias and a column list, or without parentheses as
 * {@code FROM VALUES (…), (…)}, alone or beside tables - or over one derived relation whose source is a VALUES list or
 * a FLATTEN (see {@link DerivedColumnDefinitions}), whose HAVING aggregates a subquery that aggregates the list's own
 * column: refused as the same query over a table is where no statistics answer it (see
 * {@link HavingAggregateSubqueries}), the column named by the list's alias - VALUES for a list without one - and typed
 * as the list types it, a derived relation's column as its definition. {@code SELECT b FROM (VALUES (5, TRUE), (7,
 * FALSE)) fz(id, b) GROUP BY b HAVING SUM((SELECT MAX(fz.id) FROM g)) = 14} is {@code Aggregate functions cannot be
 * nested: [MAX(FZ.ID)] nested in [SUM(CORRELATION(CAST(MAX(FZ.ID) AS NUMBER(1,0))))]} (live-verified).
 *
 * <p>A COUNT of a list's column - beside a literal or alone, over a subquery with no WHERE - counts the subquery's
 * rows where the list's statistics settle that the column holds no NULL (see
 * {@link DerivedColumnDefinitions#listedNonNull}), and is answered under a MAX or a MIN (live-verified), and under a
 * SUM or a COUNT with the group's own value (the account answers a value there that ignores the group's rows, which is
 * not followed); under an AVG it is refused at the subquery, as an AVG over an aggregate of the list's column alone is.
 * Where the statistics settle nothing - a NULL or a BOOLEAN in the column, a FLATTEN's path - or the subquery filters
 * its rows, it nests as any other aggregate does (live-verified). A subquery reading nothing of the outer row is
 * answered as it stands.
 */
final class ValuesHavingNesting {

    private ValuesHavingNesting() {
    }

    /**
     * The refusal of the first such subquery of the HAVING, or null where the query is no such query.
     *
     * @param executor  the executor
     * @param clause    the grouped query
     * @param relations the FROM clause's relations, by the names they go by
     * @param refused   given the key of the subquery refused, which no row then evaluates, and the keys of the
     *                  subqueries answered (see PlannedCorrelations)
     * @return the refusal, or null
     */
    static RuntimeException refusal(final QueryExecutor executor, final FrostlakeParser.SelectClauseContext clause,
                                    final Map<String, Table> relations, final Set<String> refused) {
        if (clause.havingClause() == null || clause.tableExpression() == null || relations == null
                || !clause.tableExpression().joinClause().isEmpty()) {
            return null;
        }
        final DerivedColumnDefinitions definitions = DerivedColumnDefinitions.of(clause, relations);
        // Each list by the name it goes by, upper-cased, with its column names (none for COLUMN1, COLUMN2, ...).
        final Map<String, FrostlakeParser.TableSourceContext> lists = new HashMap<>();
        final Map<String, List<String>> listNames = new HashMap<>();
        if (definitions == null && !collectLists(clause, lists, listNames)) {
            return null;
        }
        final List<FrostlakeParser.ScalarSubqueryExprContext> subqueries = new ArrayList<>();
        collect(clause.havingClause().booleanExpr(), subqueries);
        for (final FrostlakeParser.ScalarSubqueryExprContext subquery : subqueries) {
            final FrostlakeParser.FunctionCallExprContext aggregate =
                HavingAggregateSubqueries.enclosingAggregate(subquery, executor);
            if (aggregate == null || !HavingAggregateSubqueries.readsOuterRow(executor, relations, subquery, false)) {
                // A subquery reading nothing of the outer row is answered as it stands.
                continue;
            }
            final ParseTree count = countCall(subquery);
            if (count != null) {
                final FrostlakeParser.QualifiedNameExprContext counted = countedName(count);
                final String derived = counted == null || definitions == null ? null : derivedKey(counted, relations);
                final Boolean settled = counted == null ? null : definitions != null
                    ? definitions.settlesNonNull(derived) : listedNonNull(counted, lists, listNames, executor);
                if (settled == null && (derived == null || definitions.definition(derived) == null)) {
                    // A COUNT of anything else is judged where a row reaches it, as before.
                    continue;
                }
                // A derived column no list statistics settle - a FLATTEN's path - nests as any aggregate does.
                if (settled != null && settled && !filtered(subquery)) {
                    final String outer = aggregate.functionName().getText().toUpperCase(Locale.ROOT);
                    if ("AVG".equals(outer)) {
                        refused.add(PlannedCorrelations.valueKey(
                            ParseTreeText.getOriginalText(subquery.selectStatement())));
                        return placedRefusal(subquery);
                    }
                    if ("MAX".equals(outer) || "MIN".equals(outer) || "SUM".equals(outer)
                            || "COUNT".equals(outer)) {
                        refused.add(PlannedCorrelations.answeredKey(
                            ParseTreeText.getOriginalText(subquery.selectStatement())));
                    }
                    continue;
                }
            }
            final String nested =
                HavingAggregateSubqueries.refusal(executor, relations, aggregate, subquery, definitions);
            if (nested != null) {
                refused.add(PlannedCorrelations.valueKey(ParseTreeText.getOriginalText(subquery.selectStatement())));
                return nested.isEmpty() ? placedRefusal(subquery) : new RuntimeException(nested);
            }
            if ("AVG".equalsIgnoreCase(aggregate.functionName().getText())
                    && HavingAggregateSubqueries.readsOuterRow(executor, relations, subquery, true)) {
                // An AVG over an aggregate of the outer row alone is no decorrelation the plan takes.
                refused.add(PlannedCorrelations.valueKey(ParseTreeText.getOriginalText(subquery.selectStatement())));
                return placedRefusal(subquery);
            }
        }
        return null;
    }

    /** The refusal of a subquery no decorrelation takes, placed where the subquery is written. */
    private static RuntimeException placedRefusal(final FrostlakeParser.ScalarSubqueryExprContext subquery) {
        final Token at = SubqueryAnchor.of(subquery);
        final SourcePosition place = ExpressionSource.place(at.getLine(), at.getCharPositionInLine());
        return new UnsupportedSubqueryException(SqlCompilationError.of("Unsupported subquery type cannot be evaluated"
            + " at line " + place.getLine() + ", position " + place.getCharPositionInLine()));
    }

    /** Collects the query's VALUES lists; false where a relation is of another shape or no list is read. */
    private static boolean collectLists(final FrostlakeParser.SelectClauseContext clause,
                                        final Map<String, FrostlakeParser.TableSourceContext> lists,
                                        final Map<String, List<String>> listNames) {
        boolean values = false;
        for (final FrostlakeParser.TableReferenceContext reference : clause.tableExpression().tableReference()) {
            final FrostlakeParser.TableSourceContext source = reference.tableSource();
            if (source == null || reference.LATERAL() != null || reference.pivotClause() != null
                    || reference.unpivotClause() != null || reference.matchRecognizeClause() != null
                    || reference.sampleClause() != null) {
                return false;
            }
            final String alias = reference.aliasName() != null ? ParseTreeText.getIdentifier(reference.aliasName())
                : reference.nonJoinKeywordIdentifier() != null
                    && reference.nonJoinKeywordIdentifier().identifier() != null
                    ? ParseTreeText.getIdentifier(reference.nonJoinKeywordIdentifier().identifier()) : null;
            final List<String> names = new ArrayList<>();
            if (source.VALUES() != null && source.LPAREN() != null) {
                // A list named by an alias with its columns, inside its parentheses or after them.
                final String name;
                if (reference.identifierList() != null && alias != null) {
                    for (final FrostlakeParser.IdentifierContext column : reference.identifierList().identifier()) {
                        names.add(ParseTreeText.getIdentifier(column).toUpperCase(Locale.ROOT));
                    }
                    name = alias;
                } else if (source.columnListOptional() != null && source.identifier() != null) {
                    for (final FrostlakeParser.NamePartContext column : source.columnListOptional().namePart()) {
                        names.add(SqlIdentifiers.canonicalText(column.getText()).toUpperCase(Locale.ROOT));
                    }
                    name = ParseTreeText.getIdentifier(source.identifier());
                } else {
                    return false;
                }
                lists.put(name.toUpperCase(Locale.ROOT), source);
                listNames.put(name.toUpperCase(Locale.ROOT), names);
                values = true;
            } else if (source.VALUES() != null) {
                // A list written without parentheses, its columns COLUMN1, COLUMN2, ... of VALUES or of its alias.
                if (reference.identifierList() != null) {
                    return false;
                }
                final String name = alias != null ? alias.toUpperCase(Locale.ROOT) : "VALUES";
                lists.put(name, source);
                listNames.put(name, names);
                values = true;
            } else if (source.tableQualifiedName() == null || source.timeTravelClause() != null
                    || reference.identifierList() != null) {
                return false;
            }
        }
        return values;
    }

    /**
     * The COUNT call of the subquery's one item - alone or beside a literal - or null where the item is none.
     */
    private static ParseTree countCall(final FrostlakeParser.ScalarSubqueryExprContext subquery) {
        final FrostlakeParser.SelectStatementContext query = subquery.selectStatement();
        if (query.selectOperand().size() != 1 || query.selectOperand(0).selectClause() == null) {
            return null;
        }
        final List<FrostlakeParser.SelectItemContext> items =
            query.selectOperand(0).selectClause().selectList().selectItem();
        if (items.size() != 1 || !(items.get(0) instanceof FrostlakeParser.ExprItemContext)) {
            return null;
        }
        ParseTree value = unwrapped(((FrostlakeParser.ExprItemContext) items.get(0)).booleanExpr());
        if (value instanceof FrostlakeParser.AdditiveExprContext
                && unwrapped(((FrostlakeParser.AdditiveExprContext) value).expression(1))
                    instanceof FrostlakeParser.LiteralExprContext) {
            value = unwrapped(((FrostlakeParser.AdditiveExprContext) value).expression(0));
        }
        return value instanceof FrostlakeParser.FunctionCallExprContext
            && ((FrostlakeParser.FunctionCallExprContext) value).overClause() == null
            && "COUNT".equalsIgnoreCase(((FrostlakeParser.FunctionCallExprContext) value).functionName().getText())
            ? value : null;
    }

    private static ParseTree unwrapped(final ParseTree node) {
        ParseTree value = node;
        while (value.getChildCount() == 1 && !(value instanceof FrostlakeParser.FunctionCallExprContext)
                && !(value instanceof FrostlakeParser.QualifiedNameExprContext)
                && !(value instanceof FrostlakeParser.LiteralExprContext)) {
            value = value.getChild(0);
        }
        return value;
    }

    /** The one name a COUNT counts, or null where it counts anything else. */
    private static FrostlakeParser.QualifiedNameExprContext countedName(final ParseTree count) {
        final FrostlakeParser.FunctionCallExprContext call = (FrostlakeParser.FunctionCallExprContext) count;
        if (call.DISTINCT() != null || call.functionArgList() == null
                || call.functionArgList().functionArg().size() != 1
                || call.functionArgList().functionArg(0).booleanExpr() == null) {
            return null;
        }
        final ParseTree argument = unwrapped(call.functionArgList().functionArg(0).booleanExpr());
        return argument instanceof FrostlakeParser.QualifiedNameExprContext
            ? (FrostlakeParser.QualifiedNameExprContext) argument : null;
    }

    /** The derived relation's column a name reads, as {@code RELATION.COLUMN}, or null. */
    private static String derivedKey(final FrostlakeParser.QualifiedNameExprContext name,
                                     final Map<String, Table> relations) {
        final String[] parts = ParseTreeText.qualifiedNameParts(name.qualifiedName());
        final String relation = relations.keySet().iterator().next();
        if (parts.length > 2 || parts.length == 2 && !parts[0].equalsIgnoreCase(relation)) {
            return null;
        }
        return relation.toUpperCase(Locale.ROOT) + "." + parts[parts.length - 1].toUpperCase(Locale.ROOT);
    }

    /** Whether the statistics settle the list's column a name reads holds no NULL (see listedNonNull), or null. */
    private static Boolean listedNonNull(final FrostlakeParser.QualifiedNameExprContext name,
                                      final Map<String, FrostlakeParser.TableSourceContext> lists,
                                      final Map<String, List<String>> listNames,
                                      final QueryExecutor executor) {
        final String[] parts = ParseTreeText.qualifiedNameParts(name.qualifiedName());
        if (parts.length > 2) {
            return null;
        }
        final String column = parts[parts.length - 1].toUpperCase(Locale.ROOT);
        for (final Map.Entry<String, FrostlakeParser.TableSourceContext> list : lists.entrySet()) {
            if (parts.length == 2 && !list.getKey().equalsIgnoreCase(parts[0])) {
                continue;
            }
            final List<String> names = listNames.get(list.getKey());
            int index = names.indexOf(column);
            if (names.isEmpty() && column.startsWith("COLUMN")) {
                try {
                    index = Integer.parseInt(column.substring("COLUMN".length())) - 1;
                } catch (final NumberFormatException notPositional) {
                    index = -1;
                }
            }
            if (index >= 0) {
                return DerivedColumnDefinitions.listedNonNull(list.getValue(), index, executor);
            }
        }
        return null;
    }

    /** Whether the subquery filters its rows: a WHERE, a join, or anything beyond one relation read whole. */
    private static boolean filtered(final FrostlakeParser.ScalarSubqueryExprContext subquery) {
        final FrostlakeParser.SelectClauseContext select = subquery.selectStatement().selectOperand(0).selectClause();
        return ParseTreeText.getWhereClause(select) != null || select.tableExpression() == null
            || select.tableExpression().tableReference().size() != 1
            || !select.tableExpression().joinClause().isEmpty() || select.groupByClause() != null
            || select.havingClause() != null || select.qualifyClause() != null;
    }

    private static void collect(final ParseTree node, final List<FrostlakeParser.ScalarSubqueryExprContext> found) {
        if (node instanceof FrostlakeParser.ScalarSubqueryExprContext) {
            found.add((FrostlakeParser.ScalarSubqueryExprContext) node);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collect(node.getChild(i), found);
        }
    }
}
