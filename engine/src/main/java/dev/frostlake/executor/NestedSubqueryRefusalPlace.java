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

import dev.frostlake.executor.expressions.SubqueryAnchor;
import dev.frostlake.parser.FrostlakeParser;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Where the refusal of a subquery whose own subquery reads the outer row is placed (all live-verified).
 *
 * <p>The nested query takes the place when it stands in the filter of the subquery judged (its WHERE or a join's
 * ON), has a FROM of its own and reads the outer row, and either
 * <ul>
 *   <li>it is a scalar one whose rows are not aggregated into one, inside a subquery that aggregates them or an IN or
 *       an EXISTS over rows: in {@code (SELECT COUNT(*) FROM i WHERE i.k = (SELECT k FROM one WHERE one.k = o.k))}
 *       the refusal points at the inner query, where an inner {@code MAX(k)} or {@code COUNT(*)}, a judged scalar
 *       query of plain rows and an EXISTS over an aggregate keep the judged place; or</li>
 *   <li>the subquery judged is a scalar one or an IN standing as a conjunct of its query's WHERE whose rows are
 *       aggregated into one by calls that count no rows, MIN, MAX, SUM, ANY_VALUE or MEDIAN: then any nested query
 *       takes it, aggregated, an IN or an EXISTS too, the last of several — {@code WHERE i.k = (SELECT MAX(k) FROM one WHERE one.k =
 *       (SELECT MAX(k) FROM nn WHERE nn.k = i.k))} is placed at the innermost query, where a COUNT or an AVG, a
 *       GROUP BY, an OR around the subquery, a join's ON and a select list keep the judged place.</li>
 * </ul>
 *
 * <p>An anti-join of the outer row's bare columns in the subquery's filter, over a query that reads the outer row and
 * is left a level of its own, takes the place as a nested query does (see also {@link #antiJoinOf}).
 *
 * <p>Every other shape keeps the place of the subquery judged — an inner query in the select list or the HAVING, or
 * one reading the outer row from three levels in — except an IN or an EXISTS over plain rows standing in its query's
 * WHERE, which is refused with no place given.
 */
public final class NestedSubqueryRefusalPlace {

    private NestedSubqueryRefusalPlace() {
    }

    /**
     * The token the refusal of {@code subquery} is placed at when a nested query takes the place, else null.
     *
     * @param judged   the expression judged: a scalar subquery, an IN, a quantified comparison or an EXISTS
     * @param subquery the subquery judged, which reads the outer row from two levels in
     * @param rule     the judgement of the outer names
     * @return the nested query's anchor, the first in the text (the last where any nested query takes it), or null
     */
    public static Token of(final ParserRuleContext judged, final FrostlakeParser.SelectStatementContext subquery,
                           final CorrelatedSubqueryRule rule) {
        final FrostlakeParser.SelectClauseContext clause = CorrelatedSubqueryRule.mainClause(subquery);
        if (clause == null) {
            return null;
        }
        final boolean aggregated = rule.aggregatesUngrouped(clause);
        final boolean exists = judged instanceof FrostlakeParser.ExistsExprContext;
        final boolean scalar = isScalar(judged);
        if (exists && aggregated || scalar && !aggregated) {
            return null;
        }
        final boolean anyNested = !exists && aggregated && !negated(judged) && filtersAsConjunct(judged)
            && !countsRows(clause);
        return firstPlaced(clause, clause, rule, anyNested);
    }

    /**
     * The token the refusal of an EXISTS's or a membership's subquery standing in a WHERE is placed at when an
     * anti-join of the outer row takes the place, else null: a NOT IN or a {@code <> ALL} in the subquery's filter
     * whose subject is the outer row's bare columns is placed at its query's SELECT or its quantifier, wherever its
     * query reads from — {@code WHERE EXISTS (SELECT 1 FROM i WHERE i.k = o.k AND o.id NOT IN (SELECT val FROM i i2 WHERE i2.k =
     * o.k))} points at the NOT IN's query, for a NOT EXISTS, an IN and a DELETE's or an UPDATE's WHERE too, and so
     * does a query correlated with {@code i} instead (live-verified).
     *
     * @param subquery the subquery judged
     * @param rule     the judgement of the outer names
     * @return the anti-join's anchor, the first in the text, or null
     */
    static Token antiJoinOf(final FrostlakeParser.SelectStatementContext subquery, final CorrelatedSubqueryRule rule) {
        final FrostlakeParser.SelectClauseContext clause = CorrelatedSubqueryRule.mainClause(subquery);
        return clause == null ? null : firstAntiJoin(clause, clause, subquery, rule);
    }

    private static Token firstAntiJoin(final ParseTree node, final FrostlakeParser.SelectClauseContext clause,
                                       final FrostlakeParser.SelectStatementContext subquery,
                                       final CorrelatedSubqueryRule rule) {
        if (NullableAntiJoins.isAntiJoin(node) && filters((ParserRuleContext) node, clause)
                && rule.comparesOuterColumns(subquery, (ParserRuleContext) node)) {
            return NullableAntiJoins.anchor(node);
        }
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return null;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final Token found = firstAntiJoin(node.getChild(i), clause, subquery, rule);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Whether the refusal of a subquery reading the outer row from two levels in gives no place: an IN, a quantified
     * comparison or an EXISTS over rows its query does not aggregate into one (see the class comment).
     *
     * @param judged   the expression judged
     * @param subquery the subquery judged
     * @param rule     the judgement of the outer names
     * @return true for no place
     */
    public static boolean unplacedAtDepthTwo(final ParserRuleContext judged,
                                             final FrostlakeParser.SelectStatementContext subquery,
                                             final CorrelatedSubqueryRule rule) {
        final FrostlakeParser.SelectClauseContext clause = CorrelatedSubqueryRule.mainClause(subquery);
        return !isScalar(judged) && (clause == null || !rule.aggregatesUngrouped(clause));
    }

    /**
     * Whether an IN, a quantified comparison or an EXISTS standing as a conjunct of its query's WHERE reads the
     * outer row through a FROM-less IN, quantified comparison or EXISTS of its own filter or HAVING, whose refusal
     * gives no place: {@code WHERE EXISTS (SELECT 1 FROM i WHERE EXISTS (SELECT 1 WHERE i.k = o.k))}, where the same
     * EXISTS beside an OR, a scalar subquery, one in a select list and a FROM-less query holding a query of its own are placed
     * (live-verified).
     *
     * @param judged   the expression judged
     * @param subquery the subquery judged
     * @param rule     the judgement of the outer names
     * @return true for no place
     */
    public static boolean readsThroughAnExpressionQuery(final ParserRuleContext judged,
                                                        final FrostlakeParser.SelectStatementContext subquery,
                                                        final CorrelatedSubqueryRule rule) {
        final FrostlakeParser.SelectClauseContext clause = CorrelatedSubqueryRule.mainClause(subquery);
        return clause != null && !isScalar(judged) && filtersAsConjunct(judged)
            && readsThroughAnExpressionQuery(clause, clause, rule);
    }

    private static boolean readsThroughAnExpressionQuery(final ParseTree node,
                                                         final FrostlakeParser.SelectClauseContext clause,
                                                         final CorrelatedSubqueryRule rule) {
        final FrostlakeParser.SelectStatementContext query = membershipQuery(node);
        if (query != null) {
            final FrostlakeParser.SelectClauseContext own = CorrelatedSubqueryRule.mainClause(query);
            if (own != null && own.FROM() == null && !holdsAQuery(own) && (filters((ParserRuleContext) node, clause)
                    || inHaving((ParserRuleContext) node, clause)) && rule.readsOuterName(query)) {
                return true;
            }
        }
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return false;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (readsThroughAnExpressionQuery(node.getChild(i), clause, rule)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a query reads a name qualified by a relation that the query holding {@code judged} does not name in its
     * FROM: a name of a row further out than that query's own.
     *
     * @param judged the expression holding the query
     * @param query  the query
     * @return true where it reads such a name
     */
    public static boolean readsBeyondItsHolder(final ParserRuleContext judged,
                                               final FrostlakeParser.SelectClauseContext query) {
        ParserRuleContext holder = judged.getParent();
        while (holder != null && !(holder instanceof FrostlakeParser.SelectClauseContext)) {
            holder = holder.getParent();
        }
        if (holder == null || ((FrostlakeParser.SelectClauseContext) holder).tableExpression() == null) {
            return false;
        }
        final Set<String> named = new HashSet<>();
        relationNames(((FrostlakeParser.SelectClauseContext) holder).tableExpression(), named);
        return readsBeyond(query, named);
    }

    private static void relationNames(final ParseTree node, final Set<String> into) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return;
        }
        if (node instanceof FrostlakeParser.TableReferenceContext) {
            final FrostlakeParser.TableReferenceContext relation = (FrostlakeParser.TableReferenceContext) node;
            if (relation.aliasName() != null) {
                into.add(ParseTreeText.getIdentifier(relation.aliasName()));
            } else if (relation.nonJoinKeywordIdentifier() != null
                    && relation.nonJoinKeywordIdentifier().identifier() != null) {
                into.add(ParseTreeText.getIdentifier(relation.nonJoinKeywordIdentifier().identifier()));
            } else if (relation.tableSource() != null && relation.tableSource().tableQualifiedName() != null) {
                final String[] parts = ParseTreeText.qualifiedNameParts(relation.tableSource().tableQualifiedName());
                into.add(parts[parts.length - 1]);
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            relationNames(node.getChild(i), into);
        }
    }

    private static boolean readsBeyond(final ParseTree node, final Set<String> named) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return false;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            final String[] parts =
                ParseTreeText.qualifiedNameParts(((FrostlakeParser.QualifiedNameExprContext) node).qualifiedName());
            if (parts.length >= 2 && !named.contains(parts[parts.length - 2])) {
                return true;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (readsBeyond(node.getChild(i), named)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a select clause holds a query of its own. */
    private static boolean holdsAQuery(final ParseTree node) {
        for (int i = 0; i < node.getChildCount(); i++) {
            final ParseTree child = node.getChild(i);
            if (child instanceof FrostlakeParser.SelectStatementContext || holdsAQuery(child)) {
                return true;
            }
        }
        return false;
    }

    /** The anchor of the first nested query taking the place, or with {@code anyNested} the last. */
    private static Token firstPlaced(final ParseTree node, final FrostlakeParser.SelectClauseContext clause,
                                     final CorrelatedSubqueryRule rule, final boolean anyNested) {
        Token found = null;
        final Token anchor = anchorOf(node, anyNested);
        if (anchor != null && takesThePlace((ParserRuleContext) node, clause, rule, anyNested)) {
            if (!anyNested) {
                return anchor;
            }
            found = anchor;
        } else if (NullableAntiJoins.isAntiJoin(node) && filters((ParserRuleContext) node, clause)
                && rule.comparesOuterColumns(enclosingStatement(clause), (ParserRuleContext) node)
                && rule.readsOuterName(NullableAntiJoins.queryOf(node))) {
            // An anti-join of the outer row's bare columns over a query that reads the outer row and is left a level
            // of its own — a set operation — takes the place too (live-verified).
            if (!anyNested) {
                return NullableAntiJoins.anchor(node);
            }
            found = NullableAntiJoins.anchor(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final Token inner = firstPlaced(node.getChild(i), clause, rule, anyNested);
            if (inner != null) {
                if (!anyNested) {
                    return inner;
                }
                found = inner;
            }
        }
        return found;
    }

    /** Where a nested query that may take the place is placed, or null for a node that is none. */
    private static Token anchorOf(final ParseTree node, final boolean anyNested) {
        if (node instanceof FrostlakeParser.ScalarSubqueryExprContext) {
            return SubqueryAnchor.of((FrostlakeParser.ScalarSubqueryExprContext) node);
        }
        if (!anyNested) {
            return null;
        }
        if (node instanceof FrostlakeParser.InSubqueryExprContext) {
            return ((FrostlakeParser.InSubqueryExprContext) node).selectStatement().getStart();
        }
        if (node instanceof FrostlakeParser.TupleInSubqueryExprContext) {
            return ((FrostlakeParser.TupleInSubqueryExprContext) node).selectStatement().getStart();
        }
        if (node instanceof FrostlakeParser.QuantifiedComparisonExprContext) {
            return ((FrostlakeParser.QuantifiedComparisonExprContext) node).quantifier().getStart();
        }
        if (node instanceof FrostlakeParser.ExistsExprContext) {
            return ((FrostlakeParser.ExistsExprContext) node).EXISTS().getSymbol();
        }
        return null;
    }

    private static boolean takesThePlace(final ParserRuleContext nested, final FrostlakeParser.SelectClauseContext clause,
                                         final CorrelatedSubqueryRule rule, final boolean anyNested) {
        if (!filters(nested, clause)) {
            return false;
        }
        final FrostlakeParser.SelectStatementContext query = nested instanceof FrostlakeParser.ScalarSubqueryExprContext
            ? ((FrostlakeParser.ScalarSubqueryExprContext) nested).selectStatement() : membershipQuery(nested);
        final FrostlakeParser.SelectClauseContext own = query == null ? null : CorrelatedSubqueryRule.mainClause(query);
        return own != null && own.FROM() != null && (anyNested || !rule.aggregatesUngrouped(own))
            && rule.readsOuterName(query);
    }

    /** The query of an IN, a quantified comparison or an EXISTS, or null for a node that is none. */
    private static FrostlakeParser.SelectStatementContext membershipQuery(final ParseTree node) {
        if (node instanceof FrostlakeParser.InSubqueryExprContext) {
            return ((FrostlakeParser.InSubqueryExprContext) node).selectStatement();
        }
        if (node instanceof FrostlakeParser.TupleInSubqueryExprContext) {
            return ((FrostlakeParser.TupleInSubqueryExprContext) node).selectStatement();
        }
        if (node instanceof FrostlakeParser.QuantifiedComparisonExprContext) {
            return ((FrostlakeParser.QuantifiedComparisonExprContext) node).selectStatement();
        }
        if (node instanceof FrostlakeParser.ExistsExprContext) {
            return ((FrostlakeParser.ExistsExprContext) node).selectStatement();
        }
        return null;
    }

    /** Whether the expression is a NOT IN or a comparison with ALL, the anti-joins. */
    private static boolean negated(final ParserRuleContext judged) {
        if (judged instanceof FrostlakeParser.InSubqueryExprContext) {
            return ((FrostlakeParser.InSubqueryExprContext) judged).NOT() != null;
        }
        if (judged instanceof FrostlakeParser.TupleInSubqueryExprContext) {
            return ((FrostlakeParser.TupleInSubqueryExprContext) judged).NOT() != null;
        }
        return judged instanceof FrostlakeParser.QuantifiedComparisonExprContext
            && ((FrostlakeParser.QuantifiedComparisonExprContext) judged).quantifier().ALL() != null;
    }

    private static boolean isScalar(final ParserRuleContext judged) {
        return judged instanceof FrostlakeParser.ScalarSubqueryExprContext
            || judged instanceof FrostlakeParser.FunctionArgContext;
    }

    /** Whether the select list counts rows: a COUNT or an AVG outside the queries nested in it. */
    private static boolean countsRows(final FrostlakeParser.SelectClauseContext clause) {
        return clause.selectList() != null && countsRows((ParseTree) clause.selectList());
    }

    private static boolean countsRows(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return false;
        }
        if (node instanceof FrostlakeParser.FunctionCallStarExprContext) {
            return true;
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final String[] parts =
                ParseTreeText.functionNameParts(((FrostlakeParser.FunctionCallExprContext) node).functionName());
            final String name = parts[parts.length - 1].toUpperCase(Locale.ROOT);
            if ("COUNT".equals(name) || "AVG".equals(name)) {
                return true;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (countsRows(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether an expression stands in its query's WHERE.
     *
     * @param judged the expression
     * @return true where a WHERE holds it
     */
    public static boolean standsInWhere(final ParserRuleContext judged) {
        for (ParserRuleContext at = judged.getParent(); at != null; at = at.getParent()) {
            if (at instanceof FrostlakeParser.WhereClauseContext) {
                return true;
            }
            if (at instanceof FrostlakeParser.SelectClauseContext || at instanceof FrostlakeParser.SelectStatementContext) {
                return false;
            }
        }
        return false;
    }

    /** Whether an expression stands in its query's WHERE with no OR above it there. */
    private static boolean filtersAsConjunct(final ParserRuleContext judged) {
        for (ParserRuleContext at = judged.getParent(); at != null; at = at.getParent()) {
            if (at instanceof FrostlakeParser.WhereClauseContext) {
                return true;
            }
            if (at instanceof FrostlakeParser.OrExprContext || at instanceof FrostlakeParser.SelectClauseContext
                    || at instanceof FrostlakeParser.SelectStatementContext) {
                return false;
            }
        }
        return false;
    }

    /** The statement a select is the one select of. */
    private static FrostlakeParser.SelectStatementContext enclosingStatement(
            final FrostlakeParser.SelectClauseContext clause) {
        ParseTree at = clause.getParent();
        while (at != null && !(at instanceof FrostlakeParser.SelectStatementContext)) {
            at = at.getParent();
        }
        return (FrostlakeParser.SelectStatementContext) at;
    }

    /** Whether a nested query stands in the WHERE of {@code clause} or in one of its joins' ON. */
    private static boolean filters(final ParserRuleContext nested, final FrostlakeParser.SelectClauseContext clause) {
        boolean filter = false;
        for (ParserRuleContext at = nested.getParent(); at != null; at = at.getParent()) {
            if (at instanceof FrostlakeParser.WhereClauseContext || at instanceof FrostlakeParser.JoinClauseContext) {
                filter = true;
            } else if (at instanceof FrostlakeParser.SelectClauseContext
                    || at instanceof FrostlakeParser.SelectStatementContext) {
                return filter && at == clause;
            }
        }
        return false;
    }

    /** Whether a nested query stands in the HAVING of {@code clause}. */
    private static boolean inHaving(final ParserRuleContext nested, final FrostlakeParser.SelectClauseContext clause) {
        boolean having = false;
        for (ParserRuleContext at = nested.getParent(); at != null; at = at.getParent()) {
            if (at instanceof FrostlakeParser.HavingClauseContext) {
                having = true;
            } else if (at instanceof FrostlakeParser.SelectClauseContext
                    || at instanceof FrostlakeParser.SelectStatementContext) {
                return having && at == clause;
            }
        }
        return false;
    }
}
