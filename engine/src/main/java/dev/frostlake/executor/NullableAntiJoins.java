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

import dev.frostlake.executor.expressions.CastExpression;
import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.ExpressionSource;
import dev.frostlake.executor.expressions.LateConstantRefusal;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.LiteralType;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.executor.expressions.UnsupportedSubqueryException;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.types.NumericType;
import dev.frostlake.values.ValueRange;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The anti-joins live cannot evaluate: a NOT IN over a query, and its spellings {@code <> ALL} and {@code != ALL},
 * standing anywhere but as a filter, over a query that reads nothing of the outer row and whose one column may
 * answer NULL. {@code SELECT id, k NOT IN (SELECT k FROM wn) FROM o} is "Unsupported subquery type cannot be
 * evaluated", placed at the query's SELECT — at the quantifier for {@code <> ALL} — where {@code NOT (k IN …)},
 * {@code NOT (k = ANY …)}, {@code < ALL} and a correlated query are answered (live-verified).
 *
 * <p>A filter is a conjunct of a WHERE or a join's ON, through AND and parentheses, or of a HAVING when the
 * conjunct holds no aggregate; anything else — a select item, a CASE or IFF, an OR, a comparison with TRUE, a
 * NOT, the ORDER BY, a MERGE's WHEN condition or SET value, an UPDATE's SET value — is a value.
 *
 * <p>"May answer NULL" is judged by the rows the tables hold, as the account's statistics tell them, not by the
 * columns' declarations (see {@link HeldNullReading}): a column declared nullable whose rows hold no NULL answers
 * none, one holding a NULL answers one whatever the query's WHERE or an inner join keeps, and a NULL a table had
 * once and lost to a DELETE or an UPDATE is gone. A number computed from such columns answers NULL only where an
 * operand does, a NULLIF or an outer join's extended side may answer one, and a text computed by a function is judged
 * by its declaration, as is a text column a numeric subject converts. A UNION may answer NULL where a branch may, or where two branches reading tables differ in
 * what they declare; an INTERSECT where every branch may; an EXCEPT where its first branch may. Spared are a query
 * the statistics prove reads no row, one of a single row — an aggregate without GROUP BY, a select without FROM,
 * tables of at most one row — a column holding nothing but NULLs, and a subject the statistics fold to NULL
 * outside a negated filter (live-verified).
 *
 * <p>A ROW anti-join — {@code (a, b) NOT IN (…)}, {@code (a, b) <> ALL (…)} — is judged place by place: refused where
 * any column of its query may answer NULL, judged as one column is, but spared only by a query the statistics prove
 * reads no row: one of a single row, a select without FROM writing a NULL and a one-row table holding one are
 * refused. An OR whose other side the statistics prove false leaves it a filter (live-verified).
 */
final class NullableAntiJoins {

    private final QueryExecutor executor;
    private final CorrelatedSubqueryRule rule;
    private final Map<String, Table> relations;

    /**
     * @param executor  the executor that reads the tables
     * @param rule      the rule that tells whether a query reads the outer row and whether it reads any row
     * @param relations the relations the statement reads, by the names it gives them upper-cased, or null for none
     */
    NullableAntiJoins(final QueryExecutor executor, final CorrelatedSubqueryRule rule,
                      final Map<String, Table> relations) {
        this.executor = executor;
        this.rule = rule;
        this.relations = relations;
    }

    /** The judgement of the statement whose statistics may prove a side of an OR false, or null for none. */
    private CorrelationPlanJudgement within;

    /**
     * Reads the statistics of a statement's judgement, which may prove false the other side of an OR a ROW anti-join
     * stands beside.
     *
     * @param judgement the statement's judgement
     * @return this
     */
    NullableAntiJoins within(final CorrelationPlanJudgement judgement) {
        this.within = judgement;
        return this;
    }

    /** Whether an anti-join compares a ROW: {@code (a, b) NOT IN (…)} or {@code (a, b) <> ALL (…)}. */
    static boolean isRowForm(final ParseTree node) {
        return node instanceof FrostlakeParser.TupleInSubqueryExprContext
            || node instanceof FrostlakeParser.QuantifiedComparisonExprContext
                && ((FrostlakeParser.QuantifiedComparisonExprContext) node).expression(0)
                    instanceof FrostlakeParser.RowExprContext;
    }

    /** Whether a node is a NOT IN over a query, or a {@code <> ALL} / {@code != ALL} over one. */
    static boolean isAntiJoin(final ParseTree node) {
        if (node instanceof FrostlakeParser.InSubqueryExprContext) {
            return ((FrostlakeParser.InSubqueryExprContext) node).NOT() != null;
        }
        if (node instanceof FrostlakeParser.TupleInSubqueryExprContext) {
            // One value in parentheses is no ROW: (2) NOT IN (…) is a value's membership, judged where it runs.
            final FrostlakeParser.TupleInSubqueryExprContext tuple = (FrostlakeParser.TupleInSubqueryExprContext) node;
            return tuple.NOT() != null && tuple.expressionList().expression().size() > 1;
        }
        if (node instanceof FrostlakeParser.QuantifiedComparisonExprContext) {
            final FrostlakeParser.QuantifiedComparisonExprContext quantified =
                (FrostlakeParser.QuantifiedComparisonExprContext) node;
            return quantified.quantifier().ALL() != null && quantified.op != null
                && quantified.op.getType() == FrostlakeParser.NEQ && quantified.selectStatement() != null;
        }
        return false;
    }

    /**
     * The anti-joins a node holds outside any nested query, in the order written.
     *
     * @param node the node
     * @param into where they are added
     */
    static void collect(final ParseTree node, final List<ParserRuleContext> into) {
        if (node == null || node instanceof FrostlakeParser.SelectStatementContext) {
            return;
        }
        if (isAntiJoin(node)) {
            into.add((ParserRuleContext) node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collect(node.getChild(i), into);
        }
    }

    /**
     * The refusal of the first anti-joins the nodes hold that live cannot evaluate (see {@link #refuses}), or null.
     *
     * @param roots the expressions or clauses, in the order they are judged
     * @return the refusal, placed, or null
     */
    RuntimeException refusal(final List<? extends ParseTree> roots) {
        final List<ParserRuleContext> found = new ArrayList<>();
        for (final ParseTree root : roots) {
            collect(root, found);
        }
        for (final ParserRuleContext node : found) {
            if (refuses(node)) {
                return placed(anchor(node));
            }
        }
        return null;
    }

    /**
     * The refusal of the first anti-join of SET values that live cannot evaluate as an UPDATE's or a MERGE's value:
     * one {@link #refusesKeyedInDml} refuses in a DELETE's or an UPDATE's WHERE, whatever the target holds — over
     * {@code i.val} holding a NULL, {@code UPDATE t SET k = IFF(t.id NOT IN (SELECT val FROM i WHERE i.k = t.k), 1,
     * 0)} is refused at the query's SELECT over a target of one row too, with {@code … GROUP BY val} as well, and so is
     * a MERGE's SET value, where an item never NULL, an aggregate item, a correlation reading a further non-equality
     * and the same anti-join inside a value's subquery are answered (live-verified).
     *
     * @param values the SET values, in the order they are judged
     * @return the refusal, placed, or null
     */
    RuntimeException keyedValueRefusal(final List<? extends ParseTree> values) {
        final List<ParserRuleContext> found = new ArrayList<>();
        for (final ParseTree value : values) {
            collect(value, found);
        }
        for (final ParserRuleContext node : found) {
            if (refusesKeyed(node, true)) {
                return placed(anchor(node));
            }
        }
        return null;
    }

    /**
     * Whether live refuses an anti-join it cannot evaluate: one standing as a value over a query that reads nothing
     * of the outer row and may answer NULL.
     *
     * @param node the node judged
     * @return whether it is refused; false for a node that is no anti-join
     */
    boolean refuses(final ParseTree node) {
        if (!isAntiJoin(node) || standsAsFilter((ParserRuleContext) node)) {
            return false;
        }
        final String scopeBefore = withScope;
        final Map<String, FrostlakeParser.CteDefinitionContext> namesBefore = new HashMap<>(withNames);
        withNames.clear();
        withScope = enclosingWith(node, queryOf(node), withNames);
        try {
            return refusesJudged(node);
        } finally {
            withScope = scopeBefore;
            withNames.clear();
            withNames.putAll(namesBefore);
        }
    }

    /** {@link #refuses} of an anti-join standing as a value, judged in the scope of its statement's WITH. */
    private boolean refusesJudged(final ParseTree node) {
        final FrostlakeParser.SelectStatementContext query = queryOf(node);
        if (isRowForm(node)) {
            numericSubject = false;
            return rule.readsNoOuterName(query) && !rule.provesNoRow(query) && rowAnswersNull(query, width(node));
        }
        if (!rule.readsNoOuterName(query) || rule.provesNoRow(query) || readsOneRow(query) || holdsOnlyNulls(query)
                || foldsToNull(subjectOf(node)) || statisticsFoldToNull(subjectOf(node)) && !negatedInFilter((ParserRuleContext) node) || projectsNullLiteral(query)) {
            return false;
        }
        numericSubject = subjectIsNumeric(node);
        return answersNull(query);
    }

    /**
     * Whether an UPDATE's or a DELETE's WHERE holds an anti-join the account cannot evaluate: a NOT IN or a
     * {@code <> ALL}, anywhere in the WHERE, over one ungrouped select reading the outer row only through equalities
     * (see {@link CorrelatedSubqueryRule#keyedByEqualityAlone}) whose item may answer NULL as the rows of its FROM
     * hold them, whatever its WHERE keeps: over {@code i.val} holding a NULL, {@code t.id NOT IN (SELECT val FROM i
     * WHERE i.k = t.k)} is refused, with {@code AND i.val IS NOT NULL} too, and so are {@code t.id NOT IN (SELECT val
     * FROM i WHERE t.k = 2)} and {@code t.s NOT IN (SELECT s FROM i WHERE i.k = t.k)}; the same filter of a query,
     * {@code NOT (t.id IN (…))}, a MERGE's condition, {@code … GROUP BY val}, an aggregate item, an item never NULL
     * and a correlation reading a further non-equality ({@code AND i.val < t.id * 10}) are answered (live-verified).
     *
     * @param node the node judged
     * @return whether it is refused; false for a node that is no anti-join
     */
    boolean refusesKeyedInDml(final ParseTree node) {
        return refusesKeyed(node, false);
    }

    /** {@link #refusesKeyedInDml}, a query grouped by its item refused too where {@code grouped} says so. */
    private boolean refusesKeyed(final ParseTree node, final boolean grouped) {
        if (!isAntiJoin(node) || isRowForm(node)) {
            return false;
        }
        final FrostlakeParser.SelectStatementContext query = queryOf(node);
        final FrostlakeParser.SelectClauseContext clause = onlyClause(query);
        final FrostlakeParser.ExpressionContext subject = node instanceof FrostlakeParser.InSubqueryExprContext
            ? ((FrostlakeParser.InSubqueryExprContext) node).expression()
            : ((FrostlakeParser.QuantifiedComparisonExprContext) node).expression(0);
        if (clause == null || clause.groupByClause() != null && !grouped || clause.tableExpression() == null
                || clause.selectList().selectItem().size() != 1 || rule.readsNoOuterName(query)
                || rule.provesNoRow(query) || readsOneRow(query) || holdsOnlyNulls(query)
                || !rule.keyedByEqualityAlone(query, subject)) {
            return false;
        }
        final FrostlakeParser.SelectItemContext item = clause.selectList().selectItem(0);
        final FrostlakeParser.ExpressionContext value = SelectItemAccessors.getItemValueExpr(item);
        if (value == null) {
            return false;
        }
        numericSubject = subjectIsNumeric(node);
        return judged("SELECT " + ParseTreeText.getOriginalText(value) + " FROM "
            + ParseTreeText.getOriginalText(clause.tableExpression()), readsBare(item));
    }

    /**
     * Whether an anti-join is never TRUE: a NOT IN or a {@code <> ALL} over a query that reads nothing of the outer
     * row, holds a row and may answer NULL.
     *
     * @param node the node judged
     * @return whether no row passes it; false for a node that is no anti-join
     */
    boolean neverTrue(final ParseTree node) {
        if (!isAntiJoin(node)) {
            return false;
        }
        final FrostlakeParser.SelectStatementContext query = queryOf(node);
        if (!rule.readsNoOuterName(query) || rule.provesNoRow(query)) {
            return false;
        }
        numericSubject = subjectIsNumeric(node);
        return answersNull(query);
    }

    /**
     * The WITH clause the query being judged is read under, as written — the common table expressions of the
     * statements around it that it names — or null where it names none. A query reading a common table expression
     * of its statement is judged as the expression's own query reads its tables: over {@code nn (k)} holding no
     * NULL, {@code WITH c AS (SELECT k FROM nn) SELECT o.k NOT IN (SELECT k FROM c) FROM o} is answered, over a
     * column holding one refused (live-verified).
     */
    private String withScope;

    /** The expressions {@link #withScope} defines, by name. */
    private final Map<String, FrostlakeParser.CteDefinitionContext> withNames = new HashMap<>();

    /**
     * The WITH clause of the common table expressions the statements around an anti-join define, innermost
     * definitions shadowing outer ones of the same name, or null where its query names none of them or holds a WITH
     * of its own; the expressions it defines are added to {@code names}.
     */
    private static String enclosingWith(final ParseTree node, final FrostlakeParser.SelectStatementContext query,
                                        final Map<String, FrostlakeParser.CteDefinitionContext> names) {
        if (query.withClause() != null) {
            return null;
        }
        final List<FrostlakeParser.CteDefinitionContext> definitions = new ArrayList<>();
        boolean recursive = false;
        for (ParseTree at = node.getParent(); at != null; at = at.getParent()) {
            if (!(at instanceof FrostlakeParser.SelectStatementContext)
                    || ((FrostlakeParser.SelectStatementContext) at).withClause() == null) {
                continue;
            }
            final FrostlakeParser.WithClauseContext with = ((FrostlakeParser.SelectStatementContext) at).withClause();
            recursive |= with.RECURSIVE() != null;
            final List<FrostlakeParser.CteDefinitionContext> level = new ArrayList<>();
            for (final FrostlakeParser.CteDefinitionContext definition : with.cteDefinition()) {
                final String name = ParseTreeText.namePartText(definition.nameStartPart());
                if (!names.containsKey(name)) {
                    names.put(name, definition);
                    level.add(definition);
                }
            }
            // An outer statement's expressions come first, so that an inner one may read them.
            definitions.addAll(0, level);
        }
        if (definitions.isEmpty() || !namesAny(query, names.keySet())) {
            names.clear();
            return null;
        }
        final StringBuilder clause = new StringBuilder(recursive ? "WITH RECURSIVE " : "WITH ");
        for (int i = 0; i < definitions.size(); i++) {
            clause.append(i == 0 ? "" : ", ").append(ParseTreeText.getOriginalText(definitions.get(i)));
        }
        return clause.toString();
    }

    /** Whether a part of a query reads a relation by one of the names, written as one bare name. */
    private static boolean namesAny(final ParseTree node, final Set<String> names) {
        if (node instanceof FrostlakeParser.TableSourceContext
                && ((FrostlakeParser.TableSourceContext) node).tableQualifiedName() != null) {
            final String[] parts =
                ParseTreeText.qualifiedNameParts(((FrostlakeParser.TableSourceContext) node).tableQualifiedName());
            if (parts.length == 1 && names.contains(parts[0])) {
                return true;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (namesAny(node.getChild(i), names)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the anti-join being judged compares a number, which converts a query's text column to one. */
    private boolean numericSubject;

    /**
     * Whether an anti-join's subject is a number: a numeric literal, or a column of the statement's relations
     * declared numeric.
     */
    private boolean subjectIsNumeric(final ParseTree node) {
        if (!(node instanceof FrostlakeParser.InSubqueryExprContext)
                && !(node instanceof FrostlakeParser.QuantifiedComparisonExprContext)) {
            return false;   // a row's places are judged one by one, not as one number
        }
        final FrostlakeParser.ExpressionContext subject = node instanceof FrostlakeParser.InSubqueryExprContext
            ? ((FrostlakeParser.InSubqueryExprContext) node).expression()
            : ((FrostlakeParser.QuantifiedComparisonExprContext) node).expression(0);
        final Expression parsed;
        try {
            parsed = ExpressionEvaluator.parse(ParseTreeText.getOriginalText(subject));
        } catch (final RuntimeException unparsed) {
            return false;
        }
        if (parsed instanceof LiteralExpression) {
            final LiteralType type = ((LiteralExpression) parsed).getType();
            return type == LiteralType.INTEGER || type == LiteralType.DECIMAL;
        }
        if (!(parsed instanceof ColumnReferenceExpression) || relations == null) {
            return false;
        }
        final ColumnReferenceExpression column = (ColumnReferenceExpression) parsed;
        for (final Map.Entry<String, Table> relation : relations.entrySet()) {
            if ((!column.isQualified() || relation.getKey().equalsIgnoreCase(column.getTableName()))
                    && relation.getValue().hasColumn(column.getColumnName())) {
                return relation.getValue().getColumn(column.getColumnName()).getDataType() instanceof NumericType;
            }
        }
        return false;
    }

    /** The value a one-value anti-join compares. */
    private static FrostlakeParser.ExpressionContext subjectOf(final ParseTree node) {
        return node instanceof FrostlakeParser.InSubqueryExprContext
            ? ((FrostlakeParser.InSubqueryExprContext) node).expression()
            : ((FrostlakeParser.QuantifiedComparisonExprContext) node).expression(0);
    }

    /**
     * Whether a subject is a constant the plan folds to NULL — a NULL literal, a cast of one, {@code NULLIF(1, 1)} —
     * which folds its anti-join to NULL: {@code NULL <> ALL (SELECT k FROM wn)} (live-verified).
     */
    private boolean foldsToNull(final FrostlakeParser.ExpressionContext subject) {
        if (isNullLiteral(subject)) {
            return true;
        }
        try {
            return PlanConstantFold.foldsToNull(executor, ExpressionEvaluator.parse(ParseTreeText.getOriginalText(subject)));
        } catch (final RuntimeException unparsed) {
            return false;
        }
    }

    /**
     * Whether the statistics of the relations the statement reads make a subject NULL on every row, which folds its
     * anti-join to NULL as a NULL written there does: over ids 1 to 5, {@code IFF(id > 100, 1, NULL)}, {@code CASE
     * WHEN id > 100 THEN 1 END} and a column holding nothing but NULLs. A condition over a column holding a NULL is
     * not settled, so {@code IFF(k > 100, 1, NULL)} over such a column is still refused (live-verified).
     */
    private boolean statisticsFoldToNull(final FrostlakeParser.ExpressionContext subject) {
        if (subject == null || relations == null || relations.isEmpty()) {
            return false;
        }
        final ValueRange range =
            new CorrelatedFilterFold(executor, relations).rangeOf(ParseTreeText.getOriginalText(subject));
        return range != null && range.isEmpty() && range.isNullable() && !range.isComputedNull();
    }

    /**
     * Whether an anti-join stands under a NOT in a WHERE, a join's ON or a HAVING, where a subject the statistics
     * fold to NULL spares nothing: {@code WHERE NOT (IFF(id > 100, 1, NULL) NOT IN (SELECT k FROM wn))} is refused,
     * where the same anti-join under a NOT in a select list, or tested with IS NULL in a WHERE, is answered
     * (live-verified).
     */
    private static boolean negatedInFilter(final ParserRuleContext node) {
        boolean negated = false;
        for (ParserRuleContext at = node.getParent(); at != null; at = at.getParent()) {
            if (at instanceof FrostlakeParser.SelectStatementContext) {
                return false;
            }
            if (at instanceof FrostlakeParser.NotExprContext) {
                negated = true;
            } else if (at instanceof FrostlakeParser.WhereClauseContext || at instanceof FrostlakeParser.JoinClauseContext
                    || at instanceof FrostlakeParser.HavingClauseContext) {
                return negated;
            }
        }
        return false;
    }

    /** Whether an expression is a NULL literal, through casts. */
    private static boolean isNullLiteral(final FrostlakeParser.ExpressionContext value) {
        if (value == null) {
            return false;
        }
        Expression parsed;
        try {
            parsed = ExpressionEvaluator.parse(ParseTreeText.getOriginalText(value));
        } catch (final RuntimeException unparsed) {
            return false;
        }
        while (parsed instanceof CastExpression && !((CastExpression) parsed).isTryMode()) {
            parsed = ((CastExpression) parsed).getExpression();
        }
        return parsed instanceof LiteralExpression && ((LiteralExpression) parsed).getValue() == null;
    }

    /**
     * Whether a query's one item is a NULL literal: its column holds nothing but NULLs, as a column of only NULLs
     * does, {@code k <> ALL (SELECT NULL::INT FROM nn)} (live-verified).
     */
    private static boolean projectsNullLiteral(final FrostlakeParser.SelectStatementContext query) {
        final FrostlakeParser.SelectClauseContext clause = onlyClause(query);
        return clause != null && clause.selectList().selectItem().size() == 1
            && isNullLiteral(SelectItemAccessors.getItemValueExpr(clause.selectList().selectItem(0)));
    }

    /** The query an anti-join reads. */
    static FrostlakeParser.SelectStatementContext queryOf(final ParseTree node) {
        if (node instanceof FrostlakeParser.TupleInSubqueryExprContext) {
            return ((FrostlakeParser.TupleInSubqueryExprContext) node).selectStatement();
        }
        return node instanceof FrostlakeParser.InSubqueryExprContext
            ? ((FrostlakeParser.InSubqueryExprContext) node).selectStatement()
            : ((FrostlakeParser.QuantifiedComparisonExprContext) node).selectStatement();
    }

    /** Where a refusal is placed: a NOT IN's query's SELECT, a quantified comparison's quantifier. */
    static Token anchor(final ParseTree node) {
        return node instanceof FrostlakeParser.QuantifiedComparisonExprContext
            ? ((FrostlakeParser.QuantifiedComparisonExprContext) node).quantifier().getStart()
            : queryOf(node).getStart();
    }

    /** The values a ROW anti-join compares. */
    private static List<FrostlakeParser.ExpressionContext> rowValues(final ParseTree node) {
        if (node instanceof FrostlakeParser.TupleInSubqueryExprContext) {
            return ((FrostlakeParser.TupleInSubqueryExprContext) node).expressionList().expression();
        }
        return ((FrostlakeParser.RowExprContext) ((FrostlakeParser.QuantifiedComparisonExprContext) node)
            .expression(0)).rowValues;
    }

    /** How many places a ROW anti-join compares. */
    private static int width(final ParseTree node) {
        return rowValues(node).size();
    }

    /** The refusal placed at a token of the text being compiled. */
    static UnsupportedSubqueryException placed(final Token at) {
        final SourcePosition place = ExpressionSource.place(at.getLine(), at.getCharPositionInLine());
        return new UnsupportedSubqueryException(SqlCompilationError.of("Unsupported subquery type cannot be evaluated"
            + " at line " + place.getLine() + ", position " + place.getCharPositionInLine()));
    }

    /**
     * Whether an anti-join stands as a filter: a conjunct, through AND and parentheses, of a WHERE, of a join's ON,
     * or of a HAVING when its subject holds no aggregate.
     */
    private boolean standsAsFilter(final ParserRuleContext node) {
        final boolean row = isRowForm(node);
        ParserRuleContext at = node.getParent();
        while (at instanceof FrostlakeParser.ValueExprContext || at instanceof FrostlakeParser.AndExprContext
                || at instanceof FrostlakeParser.ParenExprContext
                || row && at instanceof FrostlakeParser.OrExprContext && othersProvenFalse(node, at)) {
            at = at.getParent();
        }
        if (at instanceof FrostlakeParser.WhereClauseContext || at instanceof FrostlakeParser.JoinClauseContext) {
            return true;
        }
        if (at instanceof FrostlakeParser.HavingClauseContext) {
            if (row) {
                for (final FrostlakeParser.ExpressionContext value : rowValues(node)) {
                    if (executor.hasAggregateFunctionInExpression(value)) {
                        return false;
                    }
                }
                return true;
            }
            final FrostlakeParser.ExpressionContext subject = node instanceof FrostlakeParser.InSubqueryExprContext
                ? ((FrostlakeParser.InSubqueryExprContext) node).expression()
                : ((FrostlakeParser.QuantifiedComparisonExprContext) node).expression(0);
            return !executor.hasAggregateFunctionInExpression(subject);
        }
        return false;
    }

    /** Whether every side of an OR but the one holding {@code node} is proven false by the statement's statistics. */
    private boolean othersProvenFalse(final ParseTree node, final ParserRuleContext or) {
        if (within == null) {
            return false;
        }
        for (final FrostlakeParser.BooleanExprContext side : ((FrostlakeParser.OrExprContext) or).booleanExpr()) {
            if (!isWithin(node, side) && !within.provesFalse(side)) {
                return false;
            }
        }
        return true;
    }

    /** Whether {@code node} is {@code within} or a node inside it. */
    private static boolean isWithin(final ParseTree node, final ParseTree within) {
        for (ParseTree at = node; at != null; at = at.getParent()) {
            if (at == within) {
                return true;
            }
        }
        return false;
    }

    /** The one select a query is, or null for a set operation or a parenthesised query. */
    private static FrostlakeParser.SelectClauseContext onlyClause(final FrostlakeParser.SelectStatementContext query) {
        return query.selectOperand().size() == 1 ? query.selectOperand(0).selectClause() : null;
    }

    /**
     * Whether a query answers one row at most as the plan reads it: an aggregate without GROUP BY, a select without
     * FROM, or one over tables of at most one row each and derived tables of one row.
     */
    private boolean readsOneRow(final FrostlakeParser.SelectStatementContext query) {
        final FrostlakeParser.SelectClauseContext clause = onlyClause(query);
        return clause != null && readsOneRow(clause);
    }

    private boolean readsOneRow(final FrostlakeParser.SelectClauseContext clause) {
        if (clause.groupByClause() == null
                && UnreadColumnPruning.containsAggregate(clause.selectList(), executor.getFunctionRegistry())) {
            return true;
        }
        final FrostlakeParser.TableExpressionContext from = clause.tableExpression();
        if (from == null) {
            return true;
        }
        final List<FrostlakeParser.TableReferenceContext> references = new ArrayList<>(from.tableReference());
        for (final FrostlakeParser.JoinClauseContext join : from.joinClause()) {
            references.add(join.tableReference());
        }
        for (final FrostlakeParser.TableReferenceContext reference : references) {
            if (!readsOneRow(reference)) {
                return false;
            }
        }
        return true;
    }

    private boolean readsOneRow(final FrostlakeParser.TableReferenceContext reference) {
        final FrostlakeParser.TableSourceContext source = reference.tableSource();
        if (source == null || reference.LATERAL() != null || reference.pivotClause() != null
                || reference.unpivotClause() != null || reference.matchRecognizeClause() != null
                || reference.sampleClause() != null) {
            return false;
        }
        if (source.selectStatement() != null) {
            final FrostlakeParser.SelectClauseContext body = onlyClause(source.selectStatement());
            return body != null && body.groupByClause() == null && source.selectStatement().limitClause() == null
                && readsOneRow(body);
        }
        final FrostlakeParser.CteDefinitionContext expression = expressionNamed(source);
        if (expression != null) {
            // A common table expression of the statement reads as its own query does.
            final FrostlakeParser.SelectClauseContext body = onlyClause(expression.selectStatement());
            return body != null && body.groupByClause() == null && expression.selectStatement().limitClause() == null
                && readsOneRow(body);
        }
        final Table table = catalogTable(source);
        return table != null && executor.storedRowCount(table.getQualifiedName()) >= 0
            && executor.storedRowCount(table.getQualifiedName()) <= 1;
    }

    /** The common table expression of the statement a FROM source names, or null for any other source. */
    private FrostlakeParser.CteDefinitionContext expressionNamed(final FrostlakeParser.TableSourceContext source) {
        if (withNames.isEmpty() || source.tableQualifiedName() == null || source.timeTravelClause() != null) {
            return null;
        }
        final String[] parts = ParseTreeText.qualifiedNameParts(source.tableQualifiedName());
        return parts.length == 1 ? withNames.get(parts[0]) : null;
    }

    /** The catalog table a FROM source names, or null for any other source. */
    private Table catalogTable(final FrostlakeParser.TableSourceContext source) {
        if (source.tableQualifiedName() == null || source.timeTravelClause() != null) {
            return null;
        }
        try {
            final String name = ParseTreeText.getQualifiedName(source.tableQualifiedName());
            final Table table = executor.getCatalog().resolveTableAsWritten(name, "Object");
            return table != null && table.isCatalogResident() && table.getQualifiedName() != null ? table : null;
        } catch (final RuntimeException unresolved) {
            return null;
        }
    }

    /** Whether a query's one item is a column of its one catalog table whose rows hold nothing but NULLs. */
    private boolean holdsOnlyNulls(final FrostlakeParser.SelectStatementContext query) {
        final FrostlakeParser.SelectClauseContext clause = onlyClause(query);
        return clause != null && clause.selectList().selectItem().size() == 1 && storedOnlyNulls(clause, 0);
    }

    /**
     * Whether the item at {@code index} of a query's one select is one value on every row the statistics know of,
     * which the plan compares with a constant while it compiles: a literal, NULL included, or a column read bare from
     * its one catalog table whose rows hold one value or nothing but NULLs.
     *
     * @param query the query
     * @param index the item's place
     * @return whether the place is a constant; false for any other query or item
     */
    boolean placeIsConstant(final FrostlakeParser.SelectStatementContext query, final int index) {
        final FrostlakeParser.SelectClauseContext clause = onlyClause(query);
        if (clause == null || index >= clause.selectList().selectItem().size()) {
            return false;
        }
        return SelectItemAccessors.getItemValueExpr(clause.selectList().selectItem(index))
                instanceof FrostlakeParser.LiteralExprContext
            || storedOnlyNulls(clause, index) || storedOneValue(clause, index);
    }

    /** Whether the item at {@code index} is a column of the select's one catalog table holding one value alone. */
    private boolean storedOneValue(final FrostlakeParser.SelectClauseContext clause, final int index) {
        final Table table = onlyCatalogTable(clause);
        final ColumnReferenceExpression column = bareColumn(clause.selectList().selectItem(index));
        return table != null && column != null && table.hasColumn(column.getColumnName())
            && new StoredColumnValues(executor).holdsOneValue(table, column.getColumnName());
    }

    /** The select's one catalog table, read without a join; null for any other FROM. */
    private Table onlyCatalogTable(final FrostlakeParser.SelectClauseContext clause) {
        if (clause.tableExpression() == null || clause.tableExpression().tableReference().size() != 1
                || !clause.tableExpression().joinClause().isEmpty()) {
            return null;
        }
        final FrostlakeParser.TableSourceContext source = clause.tableExpression().tableReference(0).tableSource();
        return source == null ? null : catalogTable(source);
    }

    /** Whether the item at {@code index} is a column of the select's one catalog table holding nothing but NULLs. */
    private boolean storedOnlyNulls(final FrostlakeParser.SelectClauseContext clause, final int index) {
        final Table table = onlyCatalogTable(clause);
        final ColumnReferenceExpression column = bareColumn(clause.selectList().selectItem(index));
        return table != null && column != null && table.hasColumn(column.getColumnName())
            && new StoredColumnValues(executor).holdsOnlyNulls(table, column.getColumnName());
    }

    /**
     * The column a select item reads bare — through parentheses, a cast, and a TRY_CAST to a text every text fits —
     * or null for any other item.
     */
    private static ColumnReferenceExpression bareColumn(final FrostlakeParser.SelectItemContext item) {
        final FrostlakeParser.ExpressionContext value = SelectItemAccessors.getItemValueExpr(item);
        if (value == null) {
            return null;
        }
        Expression parsed;
        try {
            parsed = ExpressionEvaluator.parse(ParseTreeText.getOriginalText(value));
        } catch (final RuntimeException unparsed) {
            return null;
        }
        while (parsed instanceof CastExpression && (!((CastExpression) parsed).isTryMode()
                || ResultNullability.holdsEveryText((CastExpression) parsed))) {
            parsed = ((CastExpression) parsed).getExpression();
        }
        return parsed instanceof ColumnReferenceExpression ? (ColumnReferenceExpression) parsed : null;
    }

    /** Whether a select item is a star or a column read bare. */
    private static boolean readsBare(final FrostlakeParser.SelectItemContext item) {
        return SelectItemAccessors.getItemValueExpr(item) == null || bareColumn(item) != null;
    }

    /** Whether any of the first {@code width} columns a query answers may hold NULL (see {@link #placeAnswersNull}). */
    private boolean rowAnswersNull(final FrostlakeParser.SelectStatementContext query, final int width) {
        for (int i = 0; i < width; i++) {
            if (placeAnswersNull(query, i, width)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the column a query answers at {@code index} may hold NULL, read as one column is (see
     * {@link #judged}), a literal by itself, and over a set operation by its rule over the branches.
     */
    private boolean placeAnswersNull(final FrostlakeParser.SelectStatementContext query, final int index,
                                     final int width) {
        final List<FrostlakeParser.SelectOperandContext> operands = query.selectOperand();
        if (operands.size() == 1) {
            return operandPlaceAnswersNull(operands.get(0), query, index, width);
        }
        boolean answers = operandPlaceAnswersNull(operands.get(0), null, index, width);
        for (int i = 1; i < operands.size(); i++) {
            final FrostlakeParser.SetOperatorContext operator = query.setOperator(i - 1);
            final boolean branch = operandPlaceAnswersNull(operands.get(i), null, index, width);
            if (operator.UNION() != null) {
                answers |= branch;
            } else if (operator.INTERSECT() != null) {
                answers &= branch;
            }
        }
        return answers;
    }

    private boolean operandPlaceAnswersNull(final FrostlakeParser.SelectOperandContext operand,
                                            final FrostlakeParser.SelectStatementContext whole, final int index,
                                            final int width) {
        final FrostlakeParser.SelectClauseContext clause = operand.selectClause();
        if (clause == null) {
            return operand.selectStatement() != null ? placeAnswersNull(operand.selectStatement(), index, width)
                : judgedAt(ParseTreeText.getOriginalText(operand), true, index, width);
        }
        final List<FrostlakeParser.SelectItemContext> items = clause.selectList().selectItem();
        final FrostlakeParser.ExpressionContext value =
            index < items.size() ? SelectItemAccessors.getItemValueExpr(items.get(index)) : null;
        if (value instanceof FrostlakeParser.LiteralExprContext) {
            return "NULL".equalsIgnoreCase(value.getText());
        }
        final String text = ParseTreeText.getOriginalText(whole != null ? whole : operand);
        return judgedAt(text, index < items.size() && readsBare(items.get(index)), index, width);
    }

    /** {@link #judged} for the column at {@code index} of a query answering {@code width} of them. */
    private boolean judgedAt(final String text, final boolean bare, final int index, final int width) {
        final ResultSetColumn held = shape(text, true, index, width);
        if (held == null) {
            return false;
        }
        if (held.getDataType() instanceof NumericType || bare) {
            return answersNull(held);
        }
        final ResultSetColumn declared = shape(text, false, index, width);
        return declared != null && answersNull(declared);
    }

    /** Whether the one column a query answers may hold NULL, read by the set operation's rule over its branches. */
    private boolean answersNull(final FrostlakeParser.SelectStatementContext query) {
        final List<FrostlakeParser.SelectOperandContext> operands = query.selectOperand();
        if (operands.size() == 1) {
            return operandAnswersNull(operands.get(0), query);
        }
        boolean answers = operandAnswersNull(operands.get(0), null);
        Boolean declared = declaredNullable(operands.get(0));
        for (int i = 1; i < operands.size(); i++) {
            final FrostlakeParser.SetOperatorContext operator = query.setOperator(i - 1);
            final boolean branch = operandAnswersNull(operands.get(i), null);
            if (operator.UNION() != null) {
                final Boolean own = declaredNullable(operands.get(i));
                answers |= branch || declared != null && own != null && !declared.equals(own);
                declared = declared != null ? declared : own;
            } else if (operator.INTERSECT() != null) {
                answers &= branch;
            }
        }
        return answers;
    }

    /**
     * Whether a branch's column may hold NULL; {@code whole} is the query to read where the branch is all of it, so
     * that its WITH clause is read too.
     */
    private boolean operandAnswersNull(final FrostlakeParser.SelectOperandContext operand,
                                       final FrostlakeParser.SelectStatementContext whole) {
        final FrostlakeParser.SelectClauseContext clause = operand.selectClause();
        if (clause == null) {
            return operand.selectStatement() != null ? answersNull(operand.selectStatement())
                : judged(ParseTreeText.getOriginalText(operand), true);
        }
        final List<FrostlakeParser.SelectItemContext> items = clause.selectList().selectItem();
        final FrostlakeParser.ExpressionContext value =
            items.size() == 1 ? SelectItemAccessors.getItemValueExpr(items.get(0)) : null;
        if (value instanceof FrostlakeParser.LiteralExprContext) {
            return "NULL".equalsIgnoreCase(value.getText());
        }
        final String text = ParseTreeText.getOriginalText(whole != null ? whole : operand);
        return judged(text, items.size() == 1 && readsBare(items.get(0)));
    }

    /**
     * Whether a branch's column is declared to hold NULL, as its projection derives it from the columns' NOT NULL;
     * null for a branch of a literal, which reads no table.
     */
    private Boolean declaredNullable(final FrostlakeParser.SelectOperandContext operand) {
        final FrostlakeParser.SelectClauseContext clause = operand.selectClause();
        if (clause == null) {
            return null;
        }
        final List<FrostlakeParser.SelectItemContext> items = clause.selectList().selectItem();
        if (items.size() == 1 && SelectItemAccessors.getItemValueExpr(items.get(0))
                instanceof FrostlakeParser.LiteralExprContext) {
            return null;
        }
        final ResultSetColumn column = shape(ParseTreeText.getOriginalText(operand), false);
        return column == null ? null : Boolean.valueOf(answersNull(column));
    }

    /**
     * Whether a query's one column may hold NULL: by the rows the tables hold where it reads a column bare or
     * computes a number, by the columns' declarations for any other value. A query that cannot be planned here on
     * its own is taken as one that cannot, so that its own fault is met where it runs.
     */
    private boolean judged(final String text, final boolean bare) {
        final ResultSetColumn held = shape(text, true);
        if (held == null) {
            return false;
        }
        final boolean number = held.getDataType() instanceof NumericType;
        if (number || bare && !numericSubject) {
            return answersNull(held);
        }
        final ResultSetColumn declared = shape(text, false);
        return declared != null && answersNull(declared);
    }

    private static boolean answersNull(final ResultSetColumn column) {
        final Boolean derived = column.getDerivedNullable();
        return derived != null ? derived.booleanValue() : !column.isNullabilityKnown() || column.isNullable();
    }

    /** The one column a query answers, read for its shape alone; null for none or a query that fails. */
    private ResultSetColumn shape(final String text, final boolean held) {
        return shape(text, held, 0, 1);
    }

    /**
     * The column at {@code index} of a query answering {@code width} columns, read for its shape alone; null for
     * another width or a query that fails.
     */
    private ResultSetColumn shape(final String text, final boolean held, final int index, final int width) {
        final boolean previous = RelationShapeOnly.begin();
        LateConstantRefusal.beginProbe();
        final StoredColumnValues reading = held ? HeldNullReading.begin(executor) : null;
        try {
            final String scoped = withScope == null ? text : withScope + " " + text;
            final List<ResultSet> shapes;
            final Map<String, ResultSet> ctes = executor.getCurrentCteContext();
            if (withScope != null && ctes != null) {
                // The statement's own results of the expressions would win over their definitions read here.
                final Map<String, ResultSet> others = new HashMap<>(ctes);
                others.keySet().removeAll(withNames.keySet());
                executor.setCurrentCteContext(others);
                try {
                    shapes = executor.executeWithLateralContext(scoped, new HashMap<String, Object>());
                } finally {
                    executor.setCurrentCteContext(ctes);
                }
            } else {
                shapes = executor.executeWithLateralContext(scoped, new HashMap<String, Object>());
            }
            if (shapes.isEmpty() || shapes.get(0) == null || shapes.get(0).getColumns().size() != width) {
                return null;
            }
            return shapes.get(0).getColumns().get(index);
        } catch (final RuntimeException unplanned) {
            return null;
        } finally {
            if (held) {
                HeldNullReading.end(reading);
            }
            LateConstantRefusal.endProbe();
            RelationShapeOnly.end(previous);
        }
    }
}
