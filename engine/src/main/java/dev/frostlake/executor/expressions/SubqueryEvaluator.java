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

package dev.frostlake.executor.expressions;

import dev.frostlake.executor.CorrelatedSubqueryRule;
import dev.frostlake.executor.DeferredFault;
import dev.frostlake.executor.DerivedColumnLineage;
import dev.frostlake.executor.DmlWriteTarget;
import dev.frostlake.executor.EqualityCorrelation;
import dev.frostlake.executor.ExpressionEvaluator;
import dev.frostlake.executor.NestedSubqueryRefusalPlace;
import dev.frostlake.executor.OuterNameConstancy;
import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.PrivilegeRefusal;
import dev.frostlake.executor.ProjectionLineage;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.RelationBody;
import dev.frostlake.executor.RelationShapeOnly;
import dev.frostlake.executor.ScalarBodyRowItems;
import dev.frostlake.executor.SelectItemAccessors;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.StoredColumnValues;
import dev.frostlake.executor.SubqueryAhead;
import dev.frostlake.executor.SubqueryCompilation;
import dev.frostlake.executor.UnfoldedBody;
import dev.frostlake.executor.UnreadExistsSubqueries;
import dev.frostlake.executor.UnreadRowItems;
import dev.frostlake.functions.CollationMatching;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.JoinedRelations;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.KeyContexts;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.VariantType;
import dev.frostlake.values.ValueRange;
import dev.frostlake.values.VariantValue;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.math.BigDecimal;
import java.text.CollationKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Subquery / correlated-subquery evaluation extracted from {@link ExpressionEvaluatorVisitor}: EXISTS,
 * scalar and IN subqueries, the uncorrelated-result memoization in {@link #executeSubquery(String)}, and
 * the lateral-context assembly for the outer row.
 *
 * <p>All of the mutable per-row state this needs — the current {@code row}, the (settable) lateral and
 * subquery-memo context, and the query executor — lives on the owning visitor and changes as evaluation
 * walks each outer row, so this holds only a back-reference and reads every value live through the
 * visitor's package-private accessors rather than snapshotting it.
 */
final class SubqueryEvaluator {

    /** What {@link #keyedMembership} answers where the keys cannot settle the membership, and the subquery runs. */
    private static final Object NOT_KEYED = new Object();

    private final ExpressionEvaluatorVisitor visitor;
    /** The joined relations {@link #carriers} was read from, and their columns by name: the first relation's. */
    private List<Table> carriersOf;
    private Map<String, TableColumn> carriers;
    /** The subqueries whose correlation this evaluator has judged supported, keyed by use and text. */
    private final Set<String> judgedCorrelations = new HashSet<>();
    /** Whether each membership query judged so far joins a lateral query, by its text. */
    private final Map<String, Boolean> lateralJoins = new HashMap<>();
    /** Each EXISTS met, by its copy's key, as it is run (see {@link #countedBody}). */
    private final Map<String, SubqueryExpression> countedBodies = new HashMap<>();
    /** The subqueries left unread in each EXISTS body's select items, by the EXISTS's key. */
    private final Map<String, Set<String>> unreadItems = new HashMap<>();
    /** Scalar subqueries by key, each as it is run (see {@link #statisticsBody}). */
    private final Map<String, SubqueryExpression> statisticsBodies = new HashMap<>();
    /** Each subquery's columns as the statistics tell their NULLs (see {@link #heldColumns}), null where unread. */
    private final Map<String, List<ResultSetColumn>> heldShapes = new HashMap<>();
    /** Whether each correlated subquery reads the outer row only in conditions the statistics settle. */
    private final Map<String, Boolean> settledCorrelations = new HashMap<>();
    /** Each subquery's places that are one value on every row (see {@link QueryExecutor#placeIsConstant}). */
    private final Map<String, boolean[]> constantPlaces = new HashMap<>();
    /** Each copy's equality correlation, by key, as analyzed once (see {@link #equalityCorrelationOf}). */
    private final Map<String, EqualityCorrelation> equalityCorrelations = new HashMap<>();
    /** The outer keys of each copy's equality correlation, parsed, by key. */
    private final Map<String, List<Expression>> outerKeys = new HashMap<>();
    /** The copies run for each row: no equality correlation, or one that could not be computed once for all. */
    private final Set<String> perRowOnly = new HashSet<>();
    /** The once-for-all query whose prepared keys each copy's scalar aggregate reads, by the copy's memo key. */
    private final Map<String, String> preparedQueries = new HashMap<>();
    /** Each copy's residual over its keyed rows, by its residual key (see {@link #residualExists}), parsed once. */
    private final Map<String, Expression> residuals = new HashMap<>();
    /** The evaluator of each copy's residual over its keyed rows, by its residual key, built once. */
    private final Map<String, ExpressionEvaluator> residualEvaluators = new HashMap<>();
    /** The relation each copy's keyed rows form, by its residual key, built once. */
    private final Map<String, Table> residualTables = new HashMap<>();
    /** Each copy's aggregate item over its arguments' columns, by its residual key, parsed once. */
    private final Map<String, FrostlakeParser.ExpressionContext> residualAggregates = new HashMap<>();
    /** Whether the quantified comparison being judged is an ALL whose operator is no inequality. */
    private boolean orderedUniversal;

    /** Each residual copy's reach (see {@link #residualReach}), by its residual key, judged once. */
    private final Map<String, String> reaches = new HashMap<>();
    /** Whether a residual copy's residual holds for no pair of rows (see {@link #residualNeverHolds}), judged once. */
    private final Map<String, Boolean> neverHeld = new HashMap<>();
    /** The residual copies whose IN members have been computed for their faults. */
    private final Set<String> membersRaised = new HashSet<>();
    /** What a residual key is followed by in the key its IN members are computed under. */
    private static final String MEMBERS = "\u0000members";

    /** What the name of each column a residual reads begins with (see {@link EqualityCorrelation#getResidual}). */
    private static final String RESIDUAL_COLUMN = "$R";
    /** What a copy's memo key is followed by in the keys of its residual's analysis and rows. */
    private static final String RESIDUAL = "\u0000residual";
    /** The name of the relation a residual is evaluated over. */
    private static final String RESIDUAL_ROWS = "$RESIDUAL_ROWS";

    SubqueryEvaluator(final ExpressionEvaluatorVisitor visitor) {
        this.visitor = visitor;
    }

    Object evaluateExists(final SubqueryExpression expr) {
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        if (queryExecutor == null) {
            throw new RuntimeException("Cannot evaluate EXISTS: QueryExecutor not available");
        }

        final SubqueryExpression body = countedBody(expr, null);
        if (requireSupportedCorrelation(expr, body, false, false, false, null, expr.getPosition())) {
            return false;
        }
        final String unread = unreadSelectList(expr);
        final Set<String> unreadItems = unreadItemSubqueries(expr);
        String waived = unread != null ? UnreadRowItems.begin(unread) : null;
        Set<String> waivedItems = unreadItems.isEmpty() ? null : UnreadExistsSubqueries.begin(unreadItems);
        // A select list nobody reads holds no constant the plan folds (see UnfoldedBody).
        final boolean unfolded = unread != null ? UnfoldedBody.begin() : UnfoldedBody.isActive();
        final Boolean answered;
        try {
            answered = probedExists(expr, body);
        } finally {
            if (!unreadItems.isEmpty()) {
                UnreadExistsSubqueries.end(waivedItems);
            }
            if (unread != null) {
                UnreadRowItems.end(waived);
                UnfoldedBody.end(unfolded);
            }
        }
        if (answered != null) {
            return answered;
        }
        waived = unread != null ? UnreadRowItems.begin(unread) : null;
        waivedItems = unreadItems.isEmpty() ? null : UnreadExistsSubqueries.begin(unreadItems);
        final boolean unfoldedRows = unread != null ? UnfoldedBody.begin() : UnfoldedBody.isActive();
        final List<ResultSet> results;
        try {
            results = countedSubquery(body);
        } finally {
            if (!unreadItems.isEmpty()) {
                UnreadExistsSubqueries.end(waivedItems);
            }
            if (unread != null) {
                UnreadRowItems.end(waived);
                UnfoldedBody.end(unfoldedRows);
            }
        }

        if (results.isEmpty()) {
            return false;
        }

        return results.get(0).getRowCount() > 0;
    }

    /**
     * The subqueries in the select items of an EXISTS's body whose value no row of the body reads (see
     * {@link CorrelatedSubqueryRule#unreadItemSubqueries}), by their text; empty for any other subquery.
     */
    private Set<String> unreadItemSubqueries(final SubqueryExpression expr) {
        final String key = memoKey(expr);
        Set<String> texts = unreadItems.get(key);
        if (texts == null) {
            texts = Collections.emptySet();
            final QueryExecutor queryExecutor = visitor.getQueryExecutor();
            final FrostlakeParser.SelectStatementContext parsed = expr.isExistence()
                ? queryExecutor.subqueryStatement(expr.getSubquery()) : null;
            if (parsed != null) {
                texts = new CorrelatedSubqueryRule(queryExecutor, Collections.<String>emptySet(),
                    visitor.scopeRelations()).unreadItemSubqueries(parsed);
            }
            unreadItems.put(key, texts);
        }
        return texts;
    }

    /**
     * The select list an EXISTS's body leaves unread: that of a body of one SELECT that reads no relation and filters
     * nothing, which answers its one row whatever its items are (see {@link UnreadRowItems}); null for any other.
     */
    private String unreadSelectList(final SubqueryExpression expr) {
        if (!expr.isExistence()) {
            return null;
        }
        final FrostlakeParser.SelectStatementContext parsed =
            visitor.getQueryExecutor().subqueryStatement(expr.getSubquery());
        return SelectItemAccessors.readsNoRelation(parsed)
            ? ParseTreeText.getOriginalText(parsed.selectOperand(0).selectClause().selectList()) : null;
    }

    /**
     * An EXISTS's body as it is run: an aggregate of its select list over the outer row alone is the outer
     * query's value, so the body counts its rows with a constant in its place (see
     * {@link CorrelatedSubqueryRule#countedBodyText}). Every other subquery is run as written.
     *
     * @param expr  the subquery
     * @param names the names the scope offers the subquery where it is compiling, or null for the row's own
     * @return the subquery to run, positioned as {@code expr} is
     */
    private SubqueryExpression countedBody(final SubqueryExpression expr, final Map<String, Object> names) {
        if (!expr.isExistence()) {
            return expr;
        }
        final String key = memoKey(expr);
        SubqueryExpression body = names == null ? countedBodies.get(key) : null;
        if (body != null) {
            return body;
        }
        body = expr;
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        final FrostlakeParser.SelectStatementContext parsed = queryExecutor.subqueryStatement(expr.getSubquery());
        if (parsed != null) {
            final Set<String> outerNames = new HashSet<>();
            for (final String name : (names == null ? buildLateralContext() : names).keySet()) {
                outerNames.add(name.toUpperCase(Locale.ROOT));
            }
            final String text = new CorrelatedSubqueryRule(queryExecutor, outerNames, visitor.scopeRelations())
                .countedBodyText(parsed);
            if (text != null) {
                body = new SubqueryExpression(text);
                body.setPosition(expr.getPosition());
                body.setQueryPosition(expr.getQueryPosition());
                body.setExistence(true);
            }
        }
        if (names == null) {
            countedBodies.put(key, body);
        }
        return body;
    }

    /**
     * A subquery other than an EXISTS as it is compiled: an aggregate over the outer row alone in a derived table's
     * select list written as the value it runs with (see {@link CorrelatedSubqueryRule#derivedBodyText}), so the
     * derived table is planned with no aggregate of its own. Every other subquery is compiled as written.
     *
     * @param expr  the subquery
     * @param names the names the scope offers the subquery where it is compiling
     * @return the subquery to compile, positioned as {@code expr} is
     */
    private SubqueryExpression derivedBody(final SubqueryExpression expr, final Map<String, Object> names) {
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        final FrostlakeParser.SelectStatementContext parsed = queryExecutor.subqueryStatement(expr.getSubquery());
        if (parsed == null) {
            return expr;
        }
        final Set<String> outerNames = new HashSet<>();
        for (final String name : names.keySet()) {
            outerNames.add(name.toUpperCase(Locale.ROOT));
        }
        final String text = new CorrelatedSubqueryRule(queryExecutor, outerNames, visitor.scopeRelations())
            .derivedBodyText(parsed);
        if (text == null) {
            return expr;
        }
        final SubqueryExpression body = new SubqueryExpression(text);
        body.setPosition(expr.getPosition());
        body.setQueryPosition(expr.getQueryPosition());
        return body;
    }

    /**
     * A scalar subquery as it is run: an aggregate of its select list that the outer table's statistics answer is
     * a value, written in its place (see {@link CorrelatedSubqueryRule#statisticsBodyText}). Every other subquery
     * is run as written.
     *
     * @param expr the subquery
     * @return the subquery to run, positioned as {@code expr} is
     */
    private SubqueryExpression statisticsBody(final SubqueryExpression expr) {
        final String key = memoKey(expr);
        SubqueryExpression body = statisticsBodies.get(key);
        if (body != null) {
            return body;
        }
        body = expr;
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        final FrostlakeParser.SelectStatementContext parsed = queryExecutor.subqueryStatement(expr.getSubquery());
        if (parsed != null) {
            final Set<String> outerNames = new HashSet<>();
            for (final String name : buildLateralContext().keySet()) {
                outerNames.add(name.toUpperCase(Locale.ROOT));
            }
            final String text = new CorrelatedSubqueryRule(queryExecutor, outerNames, visitor.scopeRelations())
                .statisticsBodyText(parsed);
            if (text != null) {
                body = new SubqueryExpression(text);
                body.setPosition(expr.getPosition());
                body.setQueryPosition(expr.getQueryPosition());
            }
        }
        statisticsBodies.put(key, body);
        return body;
    }

    /**
     * An IN's subquery as it is run: a condition the statistics settle inside an aggregate's argument written as its
     * value (see {@link CorrelatedSubqueryRule#settledBodyText}). Every other subquery is run as written.
     *
     * @param expr the subquery
     * @return the subquery to run, positioned as {@code expr} is
     */
    private SubqueryExpression settledBody(final SubqueryExpression expr) {
        final String key = "in\u0000" + memoKey(expr);
        SubqueryExpression body = statisticsBodies.get(key);
        if (body != null) {
            return body;
        }
        body = expr;
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        final FrostlakeParser.SelectStatementContext parsed = queryExecutor.subqueryStatement(expr.getSubquery());
        if (parsed != null) {
            final Set<String> outerNames = new HashSet<>();
            for (final String name : buildLateralContext().keySet()) {
                outerNames.add(name.toUpperCase(Locale.ROOT));
            }
            final String text = new CorrelatedSubqueryRule(queryExecutor, outerNames, visitor.scopeRelations())
                .settledBodyText(parsed);
            if (text != null) {
                body = new SubqueryExpression(text);
                body.setPosition(expr.getPosition());
                body.setQueryPosition(expr.getQueryPosition());
            }
        }
        statisticsBodies.put(key, body);
        return body;
    }

    Object evaluateScalarSubquery(final SubqueryExpression expr) {
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        if (queryExecutor == null) {
            throw new RuntimeException("Cannot evaluate subquery: QueryExecutor not available");
        }
        final String body = SelectItemAccessors.soleSelectListText(queryExecutor.subqueryStatement(expr.getSubquery()));
        final String enclosingBody = body != null ? ScalarBodyRowItems.begin(body) : null;
        try {
            return scalarSubqueryOf(expr);
        } finally {
            if (body != null) {
                ScalarBodyRowItems.end(enclosingBody);
            }
        }
    }

    /** {@link #evaluateScalarSubquery} with its body's select list named. */
    private Object scalarSubqueryOf(final SubqueryExpression expr) {

        if (requireSupportedCorrelation(expr, true, false, false, null)) {
            return null;
        }
        // An aggregate over an equality correlation reads its value by the outer row's key from values computed once
        // for every key; a row no key equals runs the subquery once, and its value is the one every such row reads.
        final PreparedKeyedValues keyed = keyedValuesOf(expr);
        if (keyed == null) {
            final Object residual = residualAggregate(expr);
            if (residual != NOT_KEYED) {
                return residual;
            }
        }
        boolean missed = false;
        if (keyed != null) {
            final List<Object> probe = preparedProbe(memoKey(expr), preparedQueries.get(memoKey(expr)));
            if (probe != null && (holdsNull(probe) || keyed.answers(probe))) {
                if (!holdsNull(probe) && keyed.holds(probe)) {
                    final Object cell = keyed.valueOf(probe);
                    if (!(cell instanceof DeferredFault)) {
                        return cell;
                    }
                } else if (keyed.knowsEmptyValue()) {
                    return keyed.emptyValue();
                } else {
                    missed = true;
                }
            }
        }
        final Object value = scalarSubqueryValue(expr);
        if (missed) {
            keyed.recordEmptyValue(value);
        }
        return value;
    }

    /**
     * An EXISTS over an equality correlation (see {@link EqualityCorrelation}), answered by probing its inner keys,
     * computed once for the statement, with the outer row's key: the answer running the subquery for the row gives,
     * in time that grows with the two relations rather than with their product. Null where the probe cannot settle
     * it — no such correlation, a key of a kind hashing does not compare, a collation — and the subquery runs.
     */
    private Boolean probedExists(final SubqueryExpression expr, final SubqueryExpression body) {
        final SubqueryMemo memo = visitor.getSubqueryMemo();
        final String key = memoKey(expr);
        final EqualityCorrelation correlation = memo == null ? null : equalityCorrelationOf(key, body);
        if (correlation == null) {
            return memo == null ? null : residualExists(key, expr, body, memo);
        }
        if (!correlation.answersARowPerKeptRow()) {
            return null;
        }
        // Computed over the keys the outer table holds (see heldKeysConjunct); a row's fault there runs the subquery
        // per outer row instead.
        final String keysQuery = correlation.keysQuery(heldKeysConjunct(correlation, key));
        PreparedKeySet keys = memo.cachedKeySet(keysQuery);
        if (keys == null) {
            final ResultSet computed = computedOnce(key, keysQuery, correlation.keyCount(), correlation.keyCount());
            if (computed == null) {
                return null;
            }
            final ExpressionCollation[] collations = keyCollations(computed, correlation.keyCount());
            keys = PreparedKeySet.build(collatedRows(computed.getRows(), collations), correlation.keyCount());
            memo.recordKeySet(keysQuery, keys);
            memo.recordKeyCollations(keysQuery, collations);
        }
        final List<Object> probe = preparedProbe(key, keysQuery);
        if (probe == null) {
            return null;
        }
        if (holdsNull(probe)) {
            return Boolean.FALSE;
        }
        return keys.answers(probe) ? Boolean.valueOf(keys.contains(probe)) : null;
    }

    /**
     * An EXISTS whose subquery reads the outer row through equalities and through other conjuncts as well (see
     * {@link CorrelatedSubqueryRule#residualCorrelation}), answered by testing the residual over the inner rows of the
     * outer row's keys, which are computed once for the statement: the answer running the subquery for the row gives,
     * in time that grows with the rows of each key rather than with the whole inner relation. Null where it cannot be
     * settled that way, and the subquery runs.
     *
     * <p>Which inner rows are computed follows the account (live-verified). Under EXISTS the residual's columns are
     * computed over every inner row the subquery's other conjuncts keep, a row's fault raised whatever its key, unless
     * the outer WHERE filters the key itself: such a filter, a comparison of the outer key column with a constant, a
     * list or a range of constants beside the EXISTS, bounds the inner key the same way. Under NOT EXISTS they are
     * computed over the inner rows whose key the outer table holds, a fault of another key's row never met.
     */
    private Boolean residualExists(final String copy, final SubqueryExpression expr, final SubqueryExpression body,
                                   final SubqueryMemo memo) {
        final String key = copy + RESIDUAL;
        if (perRowOnly.contains(key)) {
            return null;
        }
        EqualityCorrelation correlation = equalityCorrelations.get(key);
        if (correlation == null) {
            correlation = analyzedResidual(key, body);
            if (correlation == null) {
                perRowOnly.add(key);
                return null;
            }
            equalityCorrelations.put(key, correlation);
        }
        final boolean negated = expr.isNegatedExistence();
        final String reach = negated && OuterFilters.current() != null ? null
            : reachOf(correlation, key, expr, false);
        final String rowsQuery = reach != null ? correlation.residualRowsQuery(reach.isEmpty() ? null : reach)
            : correlation.residualRowsQuery(negated ? heldKeysConjunct(correlation, key)
                : transferredKeyFilter(expr, correlation, key));
        PreparedKeyedRows rows = memo.cachedKeyedRows(rowsQuery);
        if (rows == null) {
            final int keys = correlation.keyCount();
            final ResultSet computed = computedOnce(key, rowsQuery, keys + correlation.residualItemCount(), keys,
                reach != null || !negated);
            if (computed == null || residualColumnsCollate(computed, keys)) {
                perRowOnly.add(key);
                return null;
            }
            final ExpressionCollation[] collations = keyCollations(computed, keys);
            rows = PreparedKeyedRows.build(collatedRows(computed.getRows(), collations), keys, computed.getColumns());
            memo.recordKeyedRows(rowsQuery, rows);
            memo.recordKeyCollations(rowsQuery, collations);
        }
        final List<Object> probe = preparedProbe(key, rowsQuery);
        if (probe == null) {
            return null;
        }
        if (holdsNull(probe)) {
            return Boolean.FALSE;
        }
        if (!rows.answers(probe)) {
            return null;
        }
        final List<Row> group = rows.rowsOf(probe);
        if (group.isEmpty()) {
            return Boolean.FALSE;
        }
        final ExpressionEvaluator evaluator = residualEvaluatorOf(key, rows);
        evaluator.setOuterLateralContext(buildLateralContext());
        final Expression residual = residuals.get(key);
        for (final Row row : group) {
            if (SqlTruth.isTrue(evaluator.evaluate(residual, row))) {
                return Boolean.TRUE;
            }
        }
        return Boolean.FALSE;
    }

    /**
     * The outer WHERE's filters on an EXISTS's key column, written over the inner key: the conjuncts beside the
     * EXISTS that compare the column with a constant, hold it in a list of constants or between two, each with the
     * inner key in its place. Null where the EXISTS is no conjunct of the WHERE being filtered, its correlation has
     * more than one key or its outer key is no column, or no conjunct filters the key.
     */
    private String transferredKeyFilter(final SubqueryExpression expr, final EqualityCorrelation correlation,
                                        final String key) {
        final List<Expression> outer = outerKeys.get(key);
        final Expression condition = OuterFilters.current();
        if (correlation.keyCount() != 1 || outer == null || outer.size() != 1
                || !(outer.get(0) instanceof ColumnReferenceExpression) || condition == null) {
            return null;
        }
        final List<Expression> conjuncts = new ArrayList<>();
        filterConjuncts(condition, conjuncts);
        boolean beside = false;
        for (final Expression conjunct : conjuncts) {
            beside |= conjunct instanceof UnaryOperationExpression
                && ((UnaryOperationExpression) conjunct).getOperator() == UnaryOperator.EXISTS
                && ((UnaryOperationExpression) conjunct).getOperand() == expr;
        }
        if (!beside) {
            return null;
        }
        final ColumnReferenceExpression column = (ColumnReferenceExpression) outer.get(0);
        final String inner = "(" + correlation.getInnerKeys().get(0) + ")";
        final List<String> filters = new ArrayList<>();
        for (final Expression conjunct : conjuncts) {
            final String filter = keyFilterOf(conjunct, column, inner);
            if (filter != null) {
                filters.add(filter);
            }
        }
        return filters.isEmpty() ? null : String.join(" AND ", filters);
    }

    /** Which rows a residual correlation's columns are computed over: every row the other conjuncts keep. */
    private static final String EVERY_ROW = "";

    /**
     * The inner rows a residual correlation's columns and its aggregates' arguments are computed over, a fault met
     * there refusing the statement (live-verified), as a conjunct over the inner rows: {@link #EVERY_ROW} for every
     * row the other conjuncts keep, or null where the copy runs for each outer row instead.
     *
     * <p>A filter of the query's on the outer key column by constants, beside the subquery in its WHERE or in the
     * WHERE of the query whose select item holds it, bounds the inner key the same way. A NOT IN beside in the WHERE
     * reads the rows of the keys the outer table holds. Otherwise the rows follow the two relations' sizes: an outer
     * table of fewer rows than the inner relation keeps the inner rows to the keys it holds, and one of more rows
     * reads every inner row; for equal sizes an aggregate and an EXISTS in the WHERE read every row, any other
     * subquery the rows of the held keys. A select item of a query whose WHERE does not filter the key, and an outer
     * key read through a derived relation, run for each row. An IN's members, read where no residual stands beside
     * the equalities, are never bounded by the WHERE of the query whose select item holds the IN: they follow the
     * sizes.
     *
     * @param aggregate whether the subquery is a scalar aggregate
     * @param members   whether the rows are an IN's members under equalities alone
     */
    private String residualReach(final EqualityCorrelation correlation, final String key,
                                 final SubqueryExpression expr, final boolean aggregate, final boolean members) {
        final Expression where = OuterFilters.current();
        if (where != null) {
            final List<Expression> conjuncts = new ArrayList<>();
            filterConjuncts(where, conjuncts);
            final Expression beside = conjunctHolding(conjuncts, expr);
            if (beside instanceof InExpression && ((InExpression) beside).isNot()) {
                return heldKeysConjunct(correlation, key);
            }
            if (beside != null) {
                final String transferred = keyFilters(conjuncts, correlation, key);
                if (transferred != null) {
                    return transferred;
                }
            }
            return bySize(correlation, key, !aggregate && !(beside instanceof UnaryOperationExpression));
        }
        final List<Expression> selected = OuterFilters.selectList();
        if (selected == null) {
            return null;
        }
        if (selected.get(0) != null && !members) {
            final List<Expression> conjuncts = new ArrayList<>();
            filterConjuncts(selected.get(0), conjuncts);
            return keyFilters(conjuncts, correlation, key);
        }
        return bySize(correlation, key, !aggregate);
    }

    /**
     * The WHERE conjunct holding a subquery itself: its EXISTS, its IN, or a comparison with it; null where none
     * does.
     */
    private static Expression conjunctHolding(final List<Expression> conjuncts, final SubqueryExpression expr) {
        for (final Expression conjunct : conjuncts) {
            if (conjunct instanceof UnaryOperationExpression
                    && ((UnaryOperationExpression) conjunct).getOperator() == UnaryOperator.EXISTS
                    && ((UnaryOperationExpression) conjunct).getOperand() == expr
                    || conjunct instanceof InExpression && ((InExpression) conjunct).getSubquery() == expr
                    || conjunct instanceof BinaryOperationExpression
                    && comparisonSymbol(((BinaryOperationExpression) conjunct).getOperator()) != null
                    && (((BinaryOperationExpression) conjunct).getLeft() == expr
                        || ((BinaryOperationExpression) conjunct).getRight() == expr)) {
                return conjunct;
            }
        }
        return null;
    }

    /**
     * The conjuncts filtering an equality correlation's one outer key column by constants, written over the inner
     * key and joined by AND; null where the correlation has more than one key, its outer key is no column, or no
     * conjunct filters it.
     */
    private String keyFilters(final List<Expression> conjuncts, final EqualityCorrelation correlation,
                              final String key) {
        final List<Expression> outer = outerKeys.get(key);
        if (correlation.keyCount() != 1 || outer == null || outer.size() != 1
                || !(outer.get(0) instanceof ColumnReferenceExpression)) {
            return null;
        }
        final ColumnReferenceExpression column = (ColumnReferenceExpression) outer.get(0);
        final String inner = "(" + correlation.getInnerKeys().get(0) + ")";
        final List<String> filters = new ArrayList<>();
        for (final Expression conjunct : conjuncts) {
            final String filter = keyFilterOf(conjunct, column, inner);
            if (filter != null) {
                filters.add(filter);
            }
        }
        return filters.isEmpty() ? null : String.join(" AND ", filters);
    }

    /**
     * The inner rows by the two relations' sizes (see {@link #residualReach}): the rows of the keys the outer table
     * holds where it has fewer rows than the inner relation keeps, or as many and {@code tieHeld} is set; every row
     * otherwise; null where the outer key is not a column of a stored table read directly, or a size is not read.
     */
    private String bySize(final EqualityCorrelation correlation, final String key, final boolean tieHeld) {
        final String[] held = heldKeyColumn(correlation, key);
        if (held == null || !readsStoredTable(key)) {
            return null;
        }
        final long outer = countOf("SELECT COUNT(*) FROM " + held[0]);
        final long inner = countOf(correlation.countQuery());
        if (outer < 0 || inner < 0) {
            return null;
        }
        return outer < inner || outer == inner && tieHeld ? heldKeysConjunct(correlation, key) : EVERY_ROW;
    }

    /**
     * Whether a copy's one outer key is traced to a stored table of one row, whose IN reads only the inner rows of
     * that row's key (live-verified).
     */
    private boolean readsOneRowTable(final EqualityCorrelation correlation, final String key) {
        final String[] held = heldKeyColumn(correlation, key);
        return held != null && countOf("SELECT COUNT(*) FROM " + held[0]) == 1;
    }

    /**
     * Whether a copy's one outer key is a column of a stored table the outer query reads directly and alone: a join
     * changes the outer rows the sizes are weighed by.
     */
    private boolean readsStoredTable(final String key) {
        final ColumnReferenceExpression reference = (ColumnReferenceExpression) outerKeys.get(key).get(0);
        final List<Table> relations = visitor.getMultiTableAllTables();
        if (relations != null && relations.size() > 1) {
            return false;
        }
        try {
            final Table owner = scopeRelationCarrying(reference.getTableName(), reference.getColumnName());
            return owner != null && owner.residentSource() != null;
        } catch (final RuntimeException untraced) {
            return false;
        }
    }

    /** The one count a query answers, or -1 where it does not run or answers anything else. */
    private long countOf(final String query) {
        try {
            final List<ResultSet> results = executeSubquery(query);
            if (results.isEmpty() || results.get(0) == null || results.get(0).getRows().size() != 1) {
                return -1;
            }
            final Object count = results.get(0).getRows().get(0).getValue(0);
            return count instanceof Number ? ((Number) count).longValue() : -1;
        } catch (final RuntimeException failed) {
            return -1;
        }
    }

    /**
     * A residual correlation's rows computed once over the inner rows its reach keeps, each select item's row fault
     * held in its cell: a fault in a column from {@code raisedFrom} to before {@code raisedTo} is raised, one in a key
     * has the copy run for each row (null), and one in a later column waits in its cell until it is read.
     */
    private ResultSet computedReached(final String key, final String query, final int width, final int keys,
                                      final int raisedFrom, final int raisedTo) {
        final boolean outerBody = RelationBody.begin();
        final ResultSet computed;
        try {
            computed = computedOnce(key, query, width, keys);
        } finally {
            RelationBody.end(outerBody);
        }
        if (computed == null) {
            return null;
        }
        for (final Row row : computed.getRows()) {
            for (int i = 0; i < keys; i++) {
                if (row.getValue(i) instanceof DeferredFault) {
                    perRowOnly.add(key);
                    return null;
                }
            }
        }
        try {
            for (final Row row : computed.getRows()) {
                for (int i = raisedFrom; i < raisedTo; i++) {
                    DeferredFault.read(row.getValue(i));
                }
            }
        } catch (final RuntimeException fault) {
            if (!residualNeverHolds(key)) {
                throw fault;
            }
            perRowOnly.add(key);
            return null;
        }
        return computed;
    }

    /**
     * Whether some conjunct of a residual copy's residual holds for no row of the subquery's relations beside any row
     * of the outer table, whatever their keys: the account then reads no inner row for the subquery, and meets no
     * fault in its columns (live-verified). False where it is not settled: the copy has no residual, its outer key is
     * not traced to a stored table, or no conjunct's pairs are read cleanly.
     *
     * @param key the copy's residual key, or that key followed by {@link #MEMBERS}
     */
    private boolean residualNeverHolds(final String key) {
        final String copy = key.endsWith(MEMBERS) ? key.substring(0, key.length() - MEMBERS.length()) : key;
        Boolean judged = neverHeld.get(copy);
        if (judged == null) {
            judged = residualPairsMissing(copy);
            neverHeld.put(copy, judged);
        }
        return judged;
    }

    /** {@link #residualNeverHolds}, judged. */
    private boolean residualPairsMissing(final String key) {
        final EqualityCorrelation correlation = equalityCorrelations.get(key);
        if (correlation == null || correlation.getResidual() == null) {
            return false;
        }
        final String[] held = heldKeyColumn(correlation, key);
        if (held == null) {
            return false;
        }
        final String alias = ((ColumnReferenceExpression) outerKeys.get(key).get(0)).getTableName();
        final String outerRelation = alias == null ? held[0]
            : held[0] + " AS " + SqlIdentifiers.spellCanonicalEscaped(alias);
        for (final String conjunct : correlation.residualConjuncts()) {
            try {
                final ColumnReferenceCollectWalk names = new ColumnReferenceCollectWalk();
                ExpressionEvaluator.parse(conjunct).accept(names);
                final Set<Integer> items = new TreeSet<>();
                for (final ColumnReferenceExpression name : names.references()) {
                    if (!name.isQualified() && name.getColumnName().startsWith(RESIDUAL_COLUMN)) {
                        items.add(Integer.valueOf(name.getColumnName().substring(RESIDUAL_COLUMN.length())));
                    }
                }
                final List<ResultSet> pairs = executeSubquery(correlation.residualPairQuery(conjunct,
                    new ArrayList<>(items), outerRelation));
                if (!pairs.isEmpty() && pairs.get(0) != null && pairs.get(0).getRows().isEmpty()) {
                    return true;
                }
            } catch (final RuntimeException unread) {
                // A conjunct whose pairs are not read cleanly settles nothing.
            }
        }
        return false;
    }

    /** {@link #residualReach}, judged once for a copy. */
    private String reachOf(final EqualityCorrelation correlation, final String key, final SubqueryExpression expr,
                           final boolean aggregate) {
        return reachOf(correlation, key, expr, aggregate, false);
    }

    /** As above, for an IN's members under equalities alone where {@code members} is set. */
    private String reachOf(final EqualityCorrelation correlation, final String key, final SubqueryExpression expr,
                           final boolean aggregate, final boolean members) {
        if (!reaches.containsKey(key)) {
            reaches.put(key, residualReach(correlation, key, expr, aggregate, members));
        }
        return reaches.get(key);
    }

    /**
     * The filters on a subquery's outer key column by constants beside it in the WHERE being evaluated, written over
     * the inner key (see {@link #keyFilters}); null where it is no conjunct of such a WHERE or none filters the key.
     */
    private String besideKeyFilter(final SubqueryExpression expr, final EqualityCorrelation correlation,
                                   final String key) {
        final Expression where = OuterFilters.current();
        if (where == null) {
            return null;
        }
        final List<Expression> conjuncts = new ArrayList<>();
        filterConjuncts(where, conjuncts);
        return conjunctHolding(conjuncts, expr) == null ? null : keyFilters(conjuncts, correlation, key);
    }

    /** The conjuncts of a condition, through AND. */
    private static void filterConjuncts(final Expression condition, final List<Expression> conjuncts) {
        if (condition instanceof BinaryOperationExpression
                && ((BinaryOperationExpression) condition).getOperator() == BinaryOperator.AND) {
            filterConjuncts(((BinaryOperationExpression) condition).getLeft(), conjuncts);
            filterConjuncts(((BinaryOperationExpression) condition).getRight(), conjuncts);
            return;
        }
        conjuncts.add(condition);
    }

    /**
     * A conjunct filtering the outer key column by constants, written over the inner key: {@code a.id < 3} as
     * {@code (b.k) < 3}, {@code a.id IN (1, 2)}, {@code a.id BETWEEN 1 AND 2}; null for any other conjunct.
     */
    private static String keyFilterOf(final Expression conjunct, final ColumnReferenceExpression column,
                                      final String inner) {
        if (conjunct instanceof BinaryOperationExpression) {
            final BinaryOperationExpression comparison = (BinaryOperationExpression) conjunct;
            final String symbol = comparisonSymbol(comparison.getOperator());
            if (symbol == null) {
                return null;
            }
            if (sameColumn(comparison.getLeft(), column) && constantText(comparison.getRight()) != null) {
                return inner + " " + symbol + " " + constantText(comparison.getRight());
            }
            if (sameColumn(comparison.getRight(), column) && constantText(comparison.getLeft()) != null) {
                return constantText(comparison.getLeft()) + " " + symbol + " " + inner;
            }
            return null;
        }
        if (conjunct instanceof InExpression) {
            final InExpression in = (InExpression) conjunct;
            if (in.isNot() || in.getSubquery() != null || in.getValues() == null || in.getValues().isEmpty()
                    || !sameColumn(in.getValue(), column)) {
                return null;
            }
            final List<String> members = new ArrayList<>();
            for (final Expression member : in.getValues()) {
                final String text = constantText(member);
                if (text == null) {
                    return null;
                }
                members.add(text);
            }
            return inner + " IN (" + String.join(", ", members) + ")";
        }
        if (conjunct instanceof BetweenExpression) {
            final BetweenExpression between = (BetweenExpression) conjunct;
            final String lower = constantText(between.getLower());
            final String upper = constantText(between.getUpper());
            return between.isNot() || lower == null || upper == null || !sameColumn(between.getValue(), column) ? null
                : inner + " BETWEEN " + lower + " AND " + upper;
        }
        return null;
    }

    /** A comparison operator's symbol, or null for any other operator. */
    private static String comparisonSymbol(final BinaryOperator operator) {
        switch (operator) {
            case EQUAL:
                return "=";
            case NOT_EQUAL:
                return "<>";
            case LESS_THAN:
                return "<";
            case LESS_THAN_OR_EQUAL:
                return "<=";
            case GREATER_THAN:
                return ">";
            case GREATER_THAN_OR_EQUAL:
                return ">=";
            default:
                return null;
        }
    }

    /** Whether an expression is a reference to the given column, by name and, where both give one, qualifier. */
    private static boolean sameColumn(final Expression expression, final ColumnReferenceExpression column) {
        if (!(expression instanceof ColumnReferenceExpression)) {
            return false;
        }
        final ColumnReferenceExpression reference = (ColumnReferenceExpression) expression;
        return reference.getColumnName().equalsIgnoreCase(column.getColumnName())
            && (!reference.isQualified() || !column.isQualified()
                || reference.getTableName().equalsIgnoreCase(column.getTableName()));
    }

    /** A numeric or text literal as written again, or null for any other expression. */
    private static String constantText(final Expression expression) {
        if (!(expression instanceof LiteralExpression) || ((LiteralExpression) expression).getValue() == null) {
            return null;
        }
        final LiteralExpression literal = (LiteralExpression) expression;
        if (literal.getType() == LiteralType.INTEGER || literal.getType() == LiteralType.DECIMAL) {
            return literal.getValue() instanceof BigDecimal ? ((BigDecimal) literal.getValue()).toPlainString()
                : String.valueOf(literal.getValue());
        }
        return literal.getType() == LiteralType.STRING
            ? "'" + String.valueOf(literal.getValue()).replace("'", "''") + "'" : null;
    }

    /**
     * A copy's correlation with a residual, analyzed once as {@link #analyzedCorrelation} analyzes an equality
     * correlation: null where it has none, where a part of it answers anew each time it is computed, or where an
     * outer name it reads carries a collation.
     */
    private EqualityCorrelation analyzedResidual(final String key, final SubqueryExpression body) {
        return analyzedResidual(key, body, false);
    }

    /**
     * As above, for a subquery answering one row per row it keeps or, where {@code aggregated} is set, for a subquery
     * whose one select item aggregates them.
     */
    private EqualityCorrelation analyzedResidual(final String key, final SubqueryExpression body,
                                                 final boolean aggregated) {
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        final FrostlakeParser.SelectStatementContext parsed = queryExecutor.subqueryStatement(body.getSubquery());
        if (parsed == null) {
            return null;
        }
        final Set<String> outerNames = new HashSet<>();
        for (final String name : buildLateralContext().keySet()) {
            outerNames.add(name.toUpperCase(Locale.ROOT));
        }
        final EqualityCorrelation correlation =
            new CorrelatedSubqueryRule(queryExecutor, outerNames).residualCorrelation(parsed);
        if (correlation == null || (aggregated ? correlation.getAggregateItem() == null
                : !correlation.answersARowPerKeptRow())) {
            return null;
        }
        try {
            for (final String text : correlation.innerTexts()) {
                if (VolatileCalls.writes(ExpressionEvaluator.parse(text))) {
                    return null;
                }
            }
            final List<Expression> outer = new ArrayList<>();
            for (final String text : correlation.getOuterKeys()) {
                final Expression outerKey = ExpressionEvaluator.parse(text);
                if (VolatileCalls.writes(outerKey) || visitor.collationOf(outerKey).toRules() != null) {
                    return null;
                }
                outer.add(outerKey);
            }
            final Expression residual = ExpressionEvaluator.parse(correlation.getResidual());
            if (VolatileCalls.writes(residual)) {
                return null;
            }
            final ColumnReferenceCollectWalk names = new ColumnReferenceCollectWalk();
            residual.accept(names);
            for (final ColumnReferenceExpression name : names.references()) {
                if (!name.isQualified() && name.getColumnName().startsWith("$R")) {
                    continue;
                }
                if (visitor.collationOf(name).toRules() != null) {
                    return null;
                }
            }
            outerKeys.put(key, outer);
            residuals.put(key, residual);
        } catch (final RuntimeException unparsed) {
            return null;
        }
        return correlation;
    }

    /** Whether a column of the residual's, after the keys, carries a collation, which the residual would then apply. */
    private static boolean residualColumnsCollate(final ResultSet computed, final int keys) {
        for (int i = keys; i < computed.getColumns().size(); i++) {
            if (columnCollation(computed, i).toRules() != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * The values by key of a scalar aggregate over an equality correlation, computed once for the statement, or null
     * where the subquery runs for each row.
     */
    private PreparedKeyedValues keyedValuesOf(final SubqueryExpression expr) {
        final SubqueryMemo memo = visitor.getSubqueryMemo();
        final String key = memoKey(expr);
        if (memo == null || perRowOnly.contains(key) || statisticsBody(expr) != expr) {
            return null;
        }
        final EqualityCorrelation correlation = equalityCorrelationOf(key, expr);
        if (correlation == null || correlation.getAggregateItem() == null) {
            perRowOnly.add(key);
            return null;
        }
        // Computed over the inner rows the relations' sizes and the outer key's filters reach (see residualReach),
        // a fault there refusing the statement; elsewhere a row's fault runs the subquery per outer row instead.
        final String reach = reachOf(correlation, key, expr, true);
        final String groupedQuery = correlation.groupedQuery(reach == null || reach.isEmpty() ? null : reach);
        PreparedKeyedValues values = memo.cachedKeyedValues(groupedQuery);
        if (values == null) {
            final ResultSet computed = computedOnce(key, groupedQuery, correlation.keyCount() + 1,
                correlation.keyCount(), reach != null);
            if (computed == null) {
                return null;
            }
            final ResultSetColumn valueColumn = computed.getColumns().get(correlation.keyCount());
            if (valueColumn.isUncheckedConstant() || valueColumn.isFoldedDouble()) {
                perRowOnly.add(key);
                return null;
            }
            final ExpressionCollation[] collations = keyCollations(computed, correlation.keyCount());
            values = PreparedKeyedValues.build(collatedRows(computed.getRows(), collations), correlation.keyCount());
            memo.recordKeyedValues(groupedQuery, values);
            memo.recordKeyCollations(groupedQuery, collations);
        }
        preparedQueries.put(key, groupedQuery);
        return values;
    }

    /**
     * A copy's equality correlation, analyzed once: null where it has none, or where a part of it answers anew each
     * time it is computed, or its outer key carries a collation.
     */
    private EqualityCorrelation equalityCorrelationOf(final String key, final SubqueryExpression body) {
        if (perRowOnly.contains(key)) {
            return null;
        }
        EqualityCorrelation correlation = equalityCorrelations.get(key);
        if (correlation != null) {
            return correlation;
        }
        correlation = analyzedCorrelation(key, body);
        if (correlation == null) {
            perRowOnly.add(key);
            return null;
        }
        equalityCorrelations.put(key, correlation);
        return correlation;
    }

    private EqualityCorrelation analyzedCorrelation(final String key, final SubqueryExpression body) {
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        final FrostlakeParser.SelectStatementContext parsed = queryExecutor.subqueryStatement(body.getSubquery());
        if (parsed == null) {
            return null;
        }
        final Set<String> outerNames = new HashSet<>();
        for (final String name : buildLateralContext().keySet()) {
            outerNames.add(name.toUpperCase(Locale.ROOT));
        }
        final EqualityCorrelation correlation =
            new CorrelatedSubqueryRule(queryExecutor, outerNames).equalityCorrelation(parsed);
        if (correlation == null) {
            return null;
        }
        try {
            final List<String> innerTexts = correlation.innerTexts();
            if (correlation.getAggregateItem() != null) {
                innerTexts.add(correlation.getAggregateItem());
            }
            for (final String text : innerTexts) {
                if (VolatileCalls.writes(ExpressionEvaluator.parse(text))) {
                    return null;
                }
            }
            final List<Expression> outer = new ArrayList<>();
            for (final String text : correlation.getOuterKeys()) {
                final Expression outerKey = ExpressionEvaluator.parse(text);
                if (VolatileCalls.writes(outerKey) || visitor.collationOf(outerKey).toRules() != null) {
                    return null;
                }
                outer.add(outerKey);
            }
            outerKeys.put(key, outer);
        } catch (final RuntimeException unparsed) {
            return null;
        }
        return correlation;
    }

    /**
     * A correlation's once-for-all query, run as a subquery of its own; null — and the copy runs for each row from
     * then on — where it does not run, reads the outer row after all, or answers a key under a collation.
     *
     * @param width how many columns the query answers
     * @param keys  how many of them, from the first, are the correlation's keys
     */
    private ResultSet computedOnce(final String key, final String query, final int width, final int keys) {
        return computedOnce(key, query, width, keys, false);
    }

    /**
     * As above, told whether a row's fault the query meets is the statement's own. An IN's members are computed over
     * every inner row the subquery's other conditions keep, whatever the outer row's key, so a member's fault in the
     * row of another key refuses the statement: {@code ka.id IN (SELECT 10 / kb.z FROM kb WHERE kb.k = ka.v)} is
     * "Division by zero" over an inner row of z 0 that no outer row's key reads (live-verified).
     *
     * @param raisesRowFaults whether a row's fault is raised, where a failure of another kind has the copy run per row
     */
    private ResultSet computedOnce(final String key, final String query, final int width, final int keys,
                                   final boolean raisesRowFaults) {
        final SubqueryMemo memo = visitor.getSubqueryMemo();
        final List<ResultSet> results;
        try {
            results = executeSubquery(query);
        } catch (final RuntimeException failed) {
            if (raisesRowFaults && (failed.getClass() == RuntimeException.class || DeferredFault.isHeld(failed))
                    && DmlWriteTarget.isRowTimeFailure(failed) && !residualNeverHolds(key)) {
                throw failed;
            }
            perRowOnly.add(key);
            return null;
        }
        if (memo.cachedResult(query) == null || results.isEmpty() || results.get(0) == null
                || results.get(0).getColumns().size() != width) {
            perRowOnly.add(key);
            return null;
        }
        return results.get(0);
    }

    /** The collation each key column of a once-for-all query's answer carries. */
    private static ExpressionCollation[] keyCollations(final ResultSet computed, final int keys) {
        final ExpressionCollation[] collations = new ExpressionCollation[keys];
        for (int i = 0; i < keys; i++) {
            collations[i] = columnCollation(computed, i);
        }
        return collations;
    }

    /**
     * The rows of a once-for-all query with each text key replaced by its form under its column's collation, so equal
     * keys under the collation hash alike ({@code 'A'} and {@code 'a'} under {@code 'en-ci'}); the rows as they are
     * where no key column carries a collation.
     */
    private static List<Row> collatedRows(final List<Row> rows, final ExpressionCollation[] collations) {
        final CollationSpec[] rules = new CollationSpec[collations.length];
        boolean any = false;
        for (int i = 0; i < collations.length; i++) {
            rules[i] = collations[i].toRules();
            any |= rules[i] != null;
        }
        if (!any) {
            return rows;
        }
        final List<Row> collated = new ArrayList<>(rows.size());
        for (final Row row : rows) {
            final List<Object> values = new ArrayList<>(row.getValues());
            for (int i = 0; i < rules.length; i++) {
                values.set(i, collatedForm(values.get(i), rules[i]));
            }
            collated.add(new Row(values));
        }
        return collated;
    }

    /**
     * The outer row's keys as a copy's prepared keys hold them: each under the collation its comparison with the inner
     * key settles on, which must be the inner key column's own (live: an explicit COLLATE or a collated outer column
     * over a plain inner one compares otherwise). Null where the copy runs for each row: the keys cannot be read, the
     * collations cannot be combined, or they settle on another collation.
     */
    private List<Object> preparedProbe(final String key, final String query) {
        final List<Object> values = outerKeyValues(key);
        final SubqueryMemo memo = visitor.getSubqueryMemo();
        final ExpressionCollation[] collations = memo == null || query == null ? null
            : memo.cachedKeyCollations(query);
        final List<Expression> outer = outerKeys.get(key);
        if (values == null || collations == null || outer == null || outer.size() != collations.length) {
            return null;
        }
        final List<Object> probe = new ArrayList<>(values.size());
        for (int i = 0; i < collations.length; i++) {
            final ExpressionCollation settled;
            try {
                settled = ExpressionCollation.combine(collations[i], visitor.collationOf(outer.get(i)));
            } catch (final RuntimeException incompatible) {
                return null;
            }
            if (settled.getSpec() == null ? collations[i].getSpec() != null
                    : !settled.getSpec().equals(collations[i].getSpec())) {
                return null;
            }
            final CollationSpec rules = collations[i].toRules();
            probe.add(rules == null ? values.get(i) : collatedForm(values.get(i), rules));
        }
        return probe;
    }

    /** A text key's form under a collation, equal for texts the collation finds equal; any other value as it is. */
    private static Object collatedForm(final Object value, final CollationSpec rules) {
        return value instanceof String ? equalityForm(rules.equalityKey((String) value)) : value;
    }

    /** An equality key as a text: a collator's key by its bytes, a pair by its parts, a text as itself. */
    private static String equalityForm(final Object equality) {
        if (equality instanceof CollationKey) {
            final StringBuilder hex = new StringBuilder("#");
            for (final byte b : ((CollationKey) equality).toByteArray()) {
                hex.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            }
            return hex.toString();
        }
        if (equality instanceof List) {
            final StringBuilder parts = new StringBuilder("[");
            for (final Object part : (List<?>) equality) {
                parts.append(equalityForm(part)).append('|');
            }
            return parts.append(']').toString();
        }
        return "=" + equality;
    }

    /** The outer row's keys of a copy's equality correlation, or null where they cannot be read for the row. */
    private List<Object> outerKeyValues(final String key) {
        final List<Object> values = new ArrayList<>();
        try {
            for (final Expression outer : outerKeys.get(key)) {
                values.add(DeferredFault.read(outer.accept(visitor)));
            }
        } catch (final RuntimeException unread) {
            return null;
        }
        return values;
    }

    /**
     * The conjunct keeping an equality correlation's inner rows to the keys its outer relation's table holds, or null
     * where the key is not traced. Where live reads an IN's members by the keys the outer TABLE holds (see
     * {@link #residualReach}), it does so before the outer query's filters and those of a derived table over it: a
     * member's fault in a row of a held key refuses the statement, whichever outer rows the query keeps, and one in a
     * row of a key the table does not hold is never met (live-verified). A key traced is one stored column of the
     * outer row, read directly or passed through a derived relation; any other key leaves every inner row. A table a
     * row access policy guards, and a key column a masking or a projection policy reads, are not read for it.
     */
    private String heldKeysConjunct(final EqualityCorrelation correlation, final String key) {
        final String[] held = heldKeyColumn(correlation, key);
        return held == null ? null
            : "(" + correlation.getInnerKeys().get(0) + ") IN (SELECT " + held[1] + " FROM " + held[0] + ")";
    }

    /**
     * The stored table and column an equality correlation's one outer key is traced to (see
     * {@link #heldKeysConjunct}), each spelled for a query, or null where the key is not traced.
     */
    private String[] heldKeyColumn(final EqualityCorrelation correlation, final String key) {
        final List<Expression> outer = outerKeys.get(key);
        if (correlation.keyCount() != 1 || outer == null || outer.size() != 1
                || !(outer.get(0) instanceof ColumnReferenceExpression)) {
            return null;
        }
        final ColumnReferenceExpression reference = (ColumnReferenceExpression) outer.get(0);
        try {
            final Table owner = scopeRelationCarrying(reference.getTableName(), reference.getColumnName());
            if (owner == null) {
                return null;
            }
            Table stored = owner.residentSource();
            String column = reference.getColumnName();
            if (stored == null) {
                final DerivedColumnLineage lineage = DerivedColumnLineage.of(owner, column);
                if (lineage == null || lineage.storedTable() == null) {
                    return null;
                }
                stored = lineage.storedTable();
                column = lineage.storedColumn();
            }
            final String table = stored.getQualifiedName();
            if (table == null || column == null || stored.hasRowAccessPolicy()) {
                // A table a row access policy guards is read through it, which may run this very membership again.
                return null;
            }
            final TableColumn keyColumn = stored.columnAt(stored.getColumnIndex(column));
            if (keyColumn.hasMaskingPolicy() || keyColumn.hasProjectionPolicy()) {
                return null;
            }
            return new String[] {SqlIdentifiers.spellAlreadyCanonicalPath(table),
                SqlIdentifiers.spellCanonical(keyColumn.getName())};
        } catch (final RuntimeException untraced) {
            return null;
        }
    }

    /** Whether one of a probe's keys is NULL, which no inner key equals. */
    private static boolean holdsNull(final List<Object> probe) {
        for (final Object value : probe) {
            if (value == null) {
                return true;
            }
        }
        return false;
    }

    /** A scalar subquery's value for the row, the subquery run for it. */
    private Object scalarSubqueryValue(final SubqueryExpression expr) {
        // What an uncorrelated subquery raises is raised ahead of the rows that read it (see SubqueryAhead).
        final long outerReads = visitor.lateralReadCount();
        final List<ResultSet> results;
        try {
            results = executeSubquery(statisticsBody(expr));
        } catch (final RuntimeException failed) {
            throw SubqueryAhead.raised(visitor.getQueryExecutor(), expr.getSubquery(), failed,
                visitor.lateralReadCount() == outerReads);
        }

        // More than one column is refused before any row is counted: a set operation's rows must not
        // surface as the single-row fault, and a single row must not answer with its first column.
        if (!results.isEmpty() && results.get(0) != null && results.get(0).getColumns().size() > 1) {
            throw visitor.multiColumnRefusal(expr);
        }
        if (results.isEmpty() || results.get(0).getRowCount() == 0) {
            return null;
        }

        final ResultSet resultSet = results.get(0);
        if (resultSet.getRowCount() > 1) {
            // Live's own sentence, full stop included.
            throw SubqueryAhead.raised(visitor.getQueryExecutor(), expr.getSubquery(),
                new RuntimeException("Single-row subquery returns more than one row."),
                visitor.lateralReadCount() == outerReads);
        }
        if (!resultSet.getColumns().isEmpty() && resultSet.getColumns().get(0).isUncheckedConstant()
                && readsOneProjection(expr)) {
            visitor.noteUncheckedConstantSubquery(expr);
        }
        if (!resultSet.getColumns().isEmpty() && resultSet.getColumns().get(0).isFoldedDouble()) {
            visitor.noteFoldedDoubleSubquery(expr);
        }

        return DeferredFault.read(resultSet.getRows().get(0).getValue(0));
    }

    /**
     * Whether a scalar subquery is one projection the plan folds with its source. A set operator or an ORDER
     * BY at its top keeps it apart, and so does a LIMIT, FETCH or TOP over a FROM: a cast of
     * {@code (SELECT v FROM c)} over a folded wrap converts unchecked, and one of
     * {@code (SELECT v FROM c LIMIT 1)}, {@code (SELECT TO_VARIANT(123) ORDER BY 1)} or a UNION of such arms
     * is checked, while {@code (SELECT TO_VARIANT(123) LIMIT 1)} is not (live-verified).
     */
    private boolean readsOneProjection(final SubqueryExpression expr) {
        final FrostlakeParser.SelectStatementContext parsed =
            visitor.getQueryExecutor().subqueryStatement(expr.getSubquery());
        if (parsed == null || !parsed.setOperator().isEmpty() || parsed.orderByClause() != null) {
            return false;
        }
        final FrostlakeParser.SelectClauseContext select = parsed.selectOperand(0).selectClause();
        if (select == null) {
            return false;
        }
        final boolean limited = parsed.limitClause() != null || parsed.fetchClause() != null
            || select.topClause() != null;
        return !limited || select.tableExpression() == null;
    }

    Object evaluateInSubquery(final Object value, final SubqueryExpression expr, final boolean not,
                              final ExpressionCollation subjectCollation, final Expression subject) {
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        if (queryExecutor == null) {
            throw new RuntimeException("Cannot evaluate IN subquery: QueryExecutor not available");
        }

        // The COPY's key, so the membership index is looked up under the same name its result was
        // cached under.
        final String subquery = memoKey(expr);
        if (requireSupportedCorrelation(expr, false, true, not, subject)) {
            return null;
        }
        final Object keyed = keyedMembership(value, expr, not, subjectCollation, subject);
        if (keyed != NOT_KEYED) {
            return keyed;
        }
        final List<ResultSet> results = executeSubquery(settledBody(expr));

        if (results.isEmpty()) {
            return not;
        }

        final ResultSet resultSet = results.get(0);
        // The members compare under the collation the probe and the query's column settle on, as a comparison of
        // the two does: the column's own COLLATE counts (live-verified).
        final CollationSpec collation = ExpressionCollation.combine(columnCollation(resultSet), subjectCollation)
            .toRules();

        final SubqueryMemo subqueryMemo = visitor.getSubqueryMemo();
        // A query that reads the row around it is matched row against row, as a join is: its IN is TRUE for a
        // match and FALSE for everything else, a NULL probe and a NULL in the set included, where its NOT IN keeps
        // the three values (live-verified).
        // Not where the query reads that row only in filters the statistics settle: the plan folds them away and the
        // IN is three-valued as an uncorrelated one is, while its NOT IN reads a set of NULLs alone as empty (see
        // correlationSettledAway).
        final boolean correlated = subqueryMemo != null && subqueryMemo.isCorrelated(subquery);
        final boolean settledAway = correlated && correlationSettledAway(expr);
        final boolean twoValued = !not && (correlated && !settledAway || joinsLateralQuery(expr));
        final boolean nullsPassed = not && settledAway;
        // A NULL probe that reads no column is folded with its membership, which is UNKNOWN whatever the set
        // holds: NULL IN (…) over an empty set and over a query reading the row around it (live-verified).
        if (value == null && nullBeforeAnyRow(subject)) {
            return null;
        }
        // An empty set holds nothing: FALSE for IN and TRUE for NOT IN, a NULL probe read from a row included
        // (live-verified).
        if (resultSet.getRowCount() == 0) {
            return not;
        }
        // Three-valued logic otherwise (live-verified), mirroring the literal-list IN in
        // ExpressionEvaluatorVisitor.visitIn: a NULL probe is UNKNOWN, and a miss over a set that contains a
        // NULL member is UNKNOWN for both.
        if (value == null) {
            // A NOT IN is FALSE for a NULL probe over a set that holds no NULL and cannot answer one, as an ALL is.
            return twoValued || nullsPassed
                || not && !holdsNullMember(resultSet) && answersNoNull(expr, resultSet)
                && !queryExecutor.aggregatesToOneRow(expr.getSubquery()) ? Boolean.FALSE : null;
        }
        // Uncorrelated subquery (result cached, identical for every outer row): build a membership
        // index once and probe O(1) per outer row, instead of an O(subqueryRows) linear scan per row.
        // A collated probe cannot use the hash index: membership is what the collation calls equal,
        // not what hashes alike, so the scan below settles it.
        if (collation == null && subqueryMemo != null && subqueryMemo.cachedResult(subquery) != null) {
            PreparedInSet set = subqueryMemo.cachedInSet(subquery);
            if (set == null) {
                set = PreparedInSet.build(resultSet.getRows());
                subqueryMemo.recordInSet(subquery, set);
            }
            if (set.answers(value)) {
                if (set.contains(value)) {
                    return !not;
                }
                return set.hasNull() ? null : not;
            }
        }

        // Correlated, or a probe the index cannot settle: each member compared as value = member compares them.
        // A probe that is no text converts every member, as the account converts the query's column, so a member
        // that does not read refuses the statement wherever the match stands: 2 IN ('02', 'x') raises for 'x'
        // (live-verified).
        final boolean convertsEvery = !(value instanceof String);
        // A DATE, TIME, timestamp or BINARY a VARIANT holds arrives bare; beside a VARIANT or a text member it is
        // the VARIANT it is, never equal to the string of its text (live-verified).
        final VariantValue typedProbe = subject != null && ExpressionEvaluatorVisitor.typedVariantScalar(value)
            && visitor.inferStaticType(subject) instanceof VariantType
            ? ExpressionEvaluatorVisitor.asVariantValue(value) : null;
        boolean anyNull = false;
        boolean anyValue = false;
        boolean matched = false;
        for (final Row subRow : resultSet.getRows()) {
            final Object subValue = DeferredFault.read(subRow.getValue(0));
            if (subValue == null) {
                anyNull = true;
                continue;
            }
            anyValue = true;
            final Object probe = typedProbe != null
                && (subValue instanceof VariantValue || subValue instanceof CharSequence) ? typedProbe : value;
            if (collation != null ? CollationMatching.equalUnder(collation, probe, subValue)
                    : ExpressionArithmetic.keyEquals(probe, subValue)) {
                matched = true;
                if (!convertsEvery) {
                    break;
                }
            }
        }
        if (matched) {
            return !not;
        }
        return anyNull && !twoValued && !(nullsPassed && !anyValue) ? null : not;
    }

    /**
     * An IN over an equality correlation (see {@link EqualityCorrelation}), answered from the members the inner rows of
     * the outer row's keys hold, computed once for the statement: the answer running the subquery for the row gives,
     * each member compared with the value as the subquery's own row is, in time that grows with the two relations
     * rather than with their product. {@link #NOT_KEYED} where the keys cannot settle it — no such correlation, a key
     * of a kind hashing does not compare, a collation — and the subquery runs.
     */
    private Object keyedMembership(final Object value, final SubqueryExpression expr, final boolean not,
                            final ExpressionCollation subjectCollation, final Expression subject) {
        final SubqueryMemo memo = visitor.getSubqueryMemo();
        final String key = memoKey(expr);
        final EqualityCorrelation correlation = memo == null ? null : equalityCorrelationOf(key, settledBody(expr));
        if (correlation == null) {
            return memo == null ? NOT_KEYED : residualMembership(value, expr, not, subjectCollation, subject, memo);
        }
        if (!correlation.answersARowPerKeptRow() || correlation.getMemberItem() == null) {
            return NOT_KEYED;
        }
        final String reach = reachOf(correlation, key, expr, false, true);
        final String membersQuery = correlation.membersQuery(reach == null ? heldKeysConjunct(correlation, key)
            : reach.isEmpty() ? null : reach);
        PreparedKeyedMembers members = memo.cachedKeyedMembers(membersQuery);
        if (members == null) {
            final ResultSet computed = computedOnce(key, membersQuery, correlation.keyCount() + 1,
                correlation.keyCount(), true);
            if (computed == null) {
                return NOT_KEYED;
            }
            final ExpressionCollation[] collations = keyCollations(computed, correlation.keyCount());
            members = PreparedKeyedMembers.build(collatedRows(computed.getRows(), collations), correlation.keyCount(),
                columnCollation(computed, correlation.keyCount()));
            memo.recordKeyedMembers(membersQuery, members);
            memo.recordKeyCollations(membersQuery, collations);
        }
        final List<Object> outer = preparedProbe(key, membersQuery);
        if (outer == null || !holdsNull(outer) && !members.answers(outer)) {
            return NOT_KEYED;
        }
        memo.recordCorrelated(key);
        // An outer key that is NULL equals no inner one: the subquery keeps no row for it.
        final List<Object> held = holdsNull(outer) ? Collections.emptyList() : members.membersOf(outer);
        // The membership then answers as the subquery's own rows would: it reads the row around it, so a positive
        // membership is two-valued, where NOT IN keeps the three values (see evaluateInSubquery).
        final boolean twoValued = !not;
        if (value == null && nullBeforeAnyRow(subject)) {
            return null;
        }
        if (held.isEmpty()) {
            return not;
        }
        if (value == null) {
            return twoValued ? Boolean.FALSE : null;
        }
        final CollationSpec collation = ExpressionCollation.combine(members.collation(), subjectCollation).toRules();
        final boolean convertsEvery = !(value instanceof String);
        boolean anyNull = false;
        boolean matched = false;
        for (final Object member : held) {
            if (member == null) {
                anyNull = true;
                continue;
            }
            if (collation != null ? CollationMatching.equalUnder(collation, value, member)
                    : ExpressionArithmetic.keyEquals(value, member)) {
                matched = true;
                if (!convertsEvery) {
                    break;
                }
            }
        }
        if (matched) {
            return !not;
        }
        return anyNull && !twoValued ? null : not;
    }

    /**
     * An IN whose subquery reads the outer row through equalities and through other conjuncts as well (see
     * {@link CorrelatedSubqueryRule#residualCorrelation}), answered from the members of the inner rows of the outer
     * row's keys whose residual holds, those rows computed once for the statement with the residual's columns and the
     * member: the answer running the subquery for the row gives, in time that grows with the rows of each key rather
     * than with the whole inner relation. {@link #NOT_KEYED} where it cannot be settled that way, and the subquery
     * runs.
     *
     * <p>The rows are computed over the inner rows {@link #residualReach} names, a fault in a residual's column there
     * refusing the statement; a positive IN's members are computed over every inner row first (live-verified), and
     * a NOT IN's member is read only for a row whose residual holds.
     */
    private Object residualMembership(final Object value, final SubqueryExpression expr, final boolean not,
                                      final ExpressionCollation subjectCollation, final Expression subject,
                                      final SubqueryMemo memo) {
        final String key = memoKey(expr) + RESIDUAL;
        if (perRowOnly.contains(key)) {
            return NOT_KEYED;
        }
        EqualityCorrelation correlation = equalityCorrelations.get(key);
        if (correlation == null) {
            correlation = Boolean.FALSE.equals(memo.cachedComputedOnceVerdict(key)) ? null
                : analyzedResidual(key, settledBody(expr));
            if (correlation == null || correlation.getMemberItem() == null
                    || writesOrUnparsed(correlation.getMemberItem())) {
                return runsPerRow(key, memo);
            }
            equalityCorrelations.put(key, correlation);
        }
        final int keys = correlation.keyCount();
        if (!not && membersRaised.add(key) && !readsOneRowTable(correlation, key)) {
            // An IN's members are computed over every inner row the other conjuncts keep, bounded only by a filter
            // on the key beside it in the WHERE, a fault there refusing the statement (live-verified).
            computedOnce(key + MEMBERS, correlation.membersQuery(besideKeyFilter(expr, correlation, key)), keys + 1,
                keys, true);
        }
        final String reach = reachOf(correlation, key, expr, false);
        if (reach == null) {
            return runsPerRow(key, memo);
        }
        final String rowsQuery = correlation.residualMembersQuery(reach.isEmpty() ? null : reach);
        final int memberColumn = correlation.residualItemCount();
        PreparedKeyedRows rows = memo.cachedKeyedRows(rowsQuery);
        if (rows == null) {
            // A NOT IN's member is computed only for the rows of the outer row's keys whose residual holds.
            final ResultSet computed = computedReached(key, rowsQuery, keys + memberColumn + 1, keys, keys,
                keys + memberColumn + (not ? 0 : 1));
            if (computed == null || residualColumnsCollate(computed, keys)) {
                return runsPerRow(key, memo);
            }
            memo.recordComputedOnceVerdict(key, true);
            final ExpressionCollation[] collations = keyCollations(computed, keys);
            rows = PreparedKeyedRows.build(collatedRows(computed.getRows(), collations), keys, computed.getColumns());
            memo.recordKeyedRows(rowsQuery, rows);
            memo.recordKeyCollations(rowsQuery, collations);
        }
        final List<Object> outer = preparedProbe(key, rowsQuery);
        if (outer == null || !holdsNull(outer) && !rows.answers(outer)) {
            return NOT_KEYED;
        }
        memo.recordCorrelated(memoKey(expr));
        final List<Object> held = new ArrayList<>();
        if (!holdsNull(outer)) {
            final List<Row> group = rows.rowsOf(outer);
            if (!group.isEmpty()) {
                final ExpressionEvaluator evaluator = residualEvaluatorOf(key, rows);
                evaluator.setOuterLateralContext(buildLateralContext());
                final Expression residual = residuals.get(key);
                for (final Row row : group) {
                    if (SqlTruth.isTrue(evaluator.evaluate(residual, row))) {
                        held.add(DeferredFault.read(row.getValue(memberColumn)));
                    }
                }
            }
        }
        // As the subquery's own rows answer (see keyedMembership): it reads the row around it, so a positive
        // membership is two-valued, where NOT IN keeps the three values.
        if (value == null && nullBeforeAnyRow(subject)) {
            return null;
        }
        if (held.isEmpty()) {
            return not;
        }
        if (value == null) {
            return not ? null : Boolean.FALSE;
        }
        // The member's column carries no collation (see residualColumnsCollate): the probe's own settles it.
        final CollationSpec collation = ExpressionCollation.combine(ExpressionCollation.NONE, subjectCollation)
            .toRules();
        final boolean convertsEvery = !(value instanceof String);
        boolean anyNull = false;
        boolean matched = false;
        for (final Object member : held) {
            if (member == null) {
                anyNull = true;
                continue;
            }
            if (collation != null ? CollationMatching.equalUnder(collation, value, member)
                    : ExpressionArithmetic.equals(value, member)) {
                matched = true;
                if (!convertsEvery) {
                    break;
                }
            }
        }
        if (matched) {
            return !not;
        }
        return anyNull && not ? null : not;
    }

    /**
     * Notes that a copy runs for each row, here and for every evaluator of the statement, which then skips judging it
     * again.
     *
     * @return {@link #NOT_KEYED}
     */
    private Object runsPerRow(final String key, final SubqueryMemo memo) {
        perRowOnly.add(key);
        memo.recordComputedOnceVerdict(key, false);
        return NOT_KEYED;
    }

    /** The evaluator of a copy's residual over its keyed rows, built once. */
    private ExpressionEvaluator residualEvaluatorOf(final String key, final PreparedKeyedRows rows) {
        ExpressionEvaluator evaluator = residualEvaluators.get(key);
        if (evaluator == null) {
            final QueryExecutor queryExecutor = visitor.getQueryExecutor();
            evaluator = new ExpressionEvaluator(residualRowsOf(key, rows), queryExecutor.getFunctionRegistry(),
                queryExecutor.getCatalog(), queryExecutor);
            residualEvaluators.put(key, evaluator);
        }
        return evaluator;
    }

    /** The relation a copy's keyed rows form, its columns those of the rows, built once. */
    private Table residualRowsOf(final String key, final PreparedKeyedRows rows) {
        Table table = residualTables.get(key);
        if (table == null) {
            final List<TableColumn> columns = new ArrayList<>();
            for (final ResultSetColumn column : rows.itemColumns()) {
                columns.add(new TableColumn(column.getName(), column.getDataType(), true, null, false, false, false));
            }
            table = new Table(RESIDUAL_ROWS, columns, false);
            residualTables.put(key, table);
        }
        return table;
    }

    /**
     * A scalar aggregate whose subquery reads the outer row through equalities and through other conjuncts as well
     * (see {@link CorrelatedSubqueryRule#residualCorrelation}), answered by aggregating the inner rows of the outer
     * row's keys whose residual holds, those rows computed once for the statement with the residual's columns and the
     * aggregates' arguments: the value running the subquery for the row gives, the aggregate computed as a grouped
     * query computes it over a group, in time that grows with the rows of each key rather than with the whole inner
     * relation. {@link #NOT_KEYED} where it cannot be settled that way, and the subquery runs.
     *
     * <p>The first outer row runs the subquery, whose answer tells whether its value is one the plan folds (see
     * {@link #scalarSubqueryValue}); the rows are computed over the inner rows {@link #residualReach} names, a fault
     * there refusing the statement.
     */
    private Object residualAggregate(final SubqueryExpression expr) {
        final SubqueryMemo memo = visitor.getSubqueryMemo();
        final String key = memoKey(expr) + RESIDUAL;
        if (memo == null || perRowOnly.contains(key) || statisticsBody(expr) != expr) {
            return NOT_KEYED;
        }
        EqualityCorrelation correlation = equalityCorrelations.get(key);
        if (correlation == null) {
            final Boolean verdict = memo.cachedComputedOnceVerdict(key);
            correlation = Boolean.FALSE.equals(verdict) ? null : analyzedResidual(key, expr, true);
            final FrostlakeParser.ExpressionContext aggregate = correlation == null
                || correlation.getAggregateOverArguments() == null ? null
                : aggregateOverArgumentsOf(correlation.getAggregateOverArguments());
            if (aggregate == null || verdict == null && !answersAsComputed(expr)) {
                return runsPerRow(key, memo);
            }
            residualAggregates.put(key, aggregate);
            equalityCorrelations.put(key, correlation);
        }
        final String reach = reachOf(correlation, key, expr, true);
        if (reach == null) {
            return runsPerRow(key, memo);
        }
        final String rowsQuery = correlation.residualAggregateRowsQuery(reach.isEmpty() ? null : reach);
        final int keys = correlation.keyCount();
        PreparedKeyedRows rows = memo.cachedKeyedRows(rowsQuery);
        if (rows == null) {
            final int width = keys + correlation.residualItemCount() + correlation.aggregateArgumentCount();
            final ResultSet computed = computedReached(key, rowsQuery, width, keys, keys, width);
            if (computed == null || residualColumnsCollate(computed, keys) || readsADecfloat(computed, keys)) {
                return runsPerRow(key, memo);
            }
            memo.recordComputedOnceVerdict(key, true);
            final ExpressionCollation[] collations = keyCollations(computed, keys);
            rows = PreparedKeyedRows.build(collatedRows(computed.getRows(), collations), keys, computed.getColumns());
            memo.recordKeyedRows(rowsQuery, rows);
            memo.recordKeyCollations(rowsQuery, collations);
        }
        final List<Object> probe = preparedProbe(key, rowsQuery);
        if (probe == null || !holdsNull(probe) && !rows.answers(probe)) {
            return NOT_KEYED;
        }
        memo.recordCorrelated(memoKey(expr));
        final List<Row> kept = new ArrayList<>();
        if (!holdsNull(probe)) {
            final List<Row> group = rows.rowsOf(probe);
            if (!group.isEmpty()) {
                final ExpressionEvaluator evaluator = residualEvaluatorOf(key, rows);
                evaluator.setOuterLateralContext(buildLateralContext());
                final Expression residual = residuals.get(key);
                for (final Row row : group) {
                    if (SqlTruth.isTrue(evaluator.evaluate(residual, row))) {
                        kept.add(row);
                    }
                }
            }
        }
        return DeferredFault.read(visitor.getQueryExecutor().aggregateOverRows(residualAggregates.get(key), kept,
            residualRowsOf(key, rows)));
    }

    /**
     * An aggregate item written over its arguments' columns, parsed, or null where it reads anything but those
     * columns or does not parse.
     */
    private FrostlakeParser.ExpressionContext aggregateOverArgumentsOf(final String text) {
        try {
            final Expression parsed = ExpressionEvaluator.parse(text);
            if (VolatileCalls.writes(parsed)) {
                return null;
            }
            final ColumnReferenceCollectWalk names = new ColumnReferenceCollectWalk();
            parsed.accept(names);
            for (final ColumnReferenceExpression name : names.references()) {
                if (name.isQualified() || !name.getColumnName().startsWith("$A")) {
                    return null;
                }
            }
            return visitor.getQueryExecutor().parsedExpression(text);
        } catch (final RuntimeException unparsed) {
            return null;
        }
    }

    /**
     * Whether a scalar subquery's value, run for the row at hand, is one the plan reads as computed: not a constant it
     * converts unchecked nor a double it folds (see {@link #scalarSubqueryValue}). False where it does not run.
     */
    private boolean answersAsComputed(final SubqueryExpression expr) {
        final List<ResultSet> results;
        try {
            results = executeSubquery(expr);
        } catch (final RuntimeException failed) {
            return false;
        }
        if (results.isEmpty() || results.get(0) == null || results.get(0).getColumns().size() != 1) {
            return false;
        }
        // A DECFLOAT answer takes the form of the subquery's projection, which a value aggregated here would not.
        final ResultSetColumn column = results.get(0).getColumns().get(0);
        return !column.isUncheckedConstant() && !column.isFoldedDouble()
            && !NumericType.isDecfloat(column.getDataType());
    }

    /** Whether a text fails to parse as an expression or computes a value that answers anew each time. */
    private static boolean writesOrUnparsed(final String text) {
        try {
            return VolatileCalls.writes(ExpressionEvaluator.parse(text));
        } catch (final RuntimeException unparsed) {
            return true;
        }
    }

    /** Whether a column after the keys is a DECFLOAT, whose aggregate takes the form of the subquery's projection. */
    private static boolean readsADecfloat(final ResultSet computed, final int keys) {
        for (int i = keys; i < computed.getColumns().size(); i++) {
            if (NumericType.isDecfloat(computed.getColumns().get(i).getDataType())) {
                return true;
            }
        }
        return false;
    }

    /** The collation a query's one column carries, as a comparison with it would read it. */
    static ExpressionCollation columnCollation(final ResultSet resultSet) {
        return columnCollation(resultSet, 0);
    }

    /** The collation one column of a query carries, as a comparison with it would read it. */
    static ExpressionCollation columnCollation(final ResultSet resultSet, final int index) {
        if (resultSet.getColumns().size() <= index) {
            return ExpressionCollation.NONE;
        }
        final ResultSetColumn column = resultSet.getColumns().get(index);
        if (column.getCollation() == null) {
            return ExpressionCollation.NONE;
        }
        return column.isCollationExplicit() ? ExpressionCollation.explicit(column.getCollation())
            : ExpressionCollation.column(column.getCollation());
    }

    /**
     * Whether an operand reads no column of the row it is evaluated over, so the plan folds it before any row
     * is read; false where the operand is not known.
     */
    boolean readsNoColumn(final Expression operand) {
        if (operand == null) {
            return false;
        }
        final ColumnReferenceCollectWalk walk = new ColumnReferenceCollectWalk();
        operand.accept(walk);
        return walk.references().isEmpty();
    }

    /**
     * Whether a probe is NULL before any row is read: it reads no column (see {@link #readsNoColumn}), or the plan
     * knows it holds NULL alone — a derived relation's NULL constant, its CAST(NULL AS INT) or MAX(NULL), through a
     * LIMIT or a join — which a membership folds with as it folds a NULL written there (live-verified). An aggregate
     * of a type over NULLs alone — SUM(NULL), AVG(NULL), MAX(NULL::INT) — is no such NULL.
     *
     * @param operand the probe
     * @return whether it is NULL whatever row is read
     */
    boolean nullBeforeAnyRow(final Expression operand) {
        if (readsNoColumn(operand)) {
            return true;
        }
        final ValueRange range = visitor.inferStaticRange(operand);
        // An aggregate's NULL is folded only when the aggregate is itself of no type, MAX(NULL); a SUM(NULL) or a
        // MAX(NULL::INT) is NULL only as the rows are aggregated (live-verified).
        return range != null && range.isEmpty() && range.isNullable()
            && (!range.isComputedNull() || visitor.inferStaticType(operand) == null);
    }

    /**
     * The literal each place of a query writes where the query is one SELECT with no FROM: TRUE for a literal that is
     * not NULL, FALSE for a NULL, null for a place that is no literal — every place null for any other query.
     *
     * @param width the number of places asked for
     */
    Boolean[] literalPlaces(final SubqueryExpression expr, final int width) {
        final Boolean[] places = new Boolean[width];
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        final FrostlakeParser.SelectStatementContext parsed =
            queryExecutor == null ? null : queryExecutor.subqueryStatement(expr.getSubquery());
        if (parsed == null || parsed.selectOperand().size() != 1 || parsed.selectOperand(0).selectClause() == null
                || parsed.selectOperand(0).selectClause().FROM() != null) {
            return places;
        }
        final List<FrostlakeParser.SelectItemContext> items =
            parsed.selectOperand(0).selectClause().selectList().selectItem();
        for (int i = 0; i < width && i < items.size(); i++) {
            final FrostlakeParser.ExpressionContext value = SelectItemAccessors.getItemValueExpr(items.get(i));
            if (value instanceof FrostlakeParser.LiteralExprContext) {
                places[i] = Boolean.valueOf(!"NULL".equalsIgnoreCase(value.getText()));
            }
        }
        return places;
    }

    /**
     * Whether a membership's query joins a lateral query somewhere in it (see
     * {@link CorrelatedSubqueryRule#joinsLateralQueryAnywhere}), judged once for each query text.
     */
    private boolean joinsLateralQuery(final SubqueryExpression expr) {
        Boolean joins = lateralJoins.get(expr.getSubquery());
        if (joins == null) {
            final QueryExecutor queryExecutor = visitor.getQueryExecutor();
            final FrostlakeParser.SelectStatementContext parsed = queryExecutor.subqueryStatement(expr.getSubquery());
            joins = Boolean.valueOf(parsed != null
                && new CorrelatedSubqueryRule(queryExecutor, new HashSet<String>()).joinsLateralQueryAnywhere(parsed));
            lateralJoins.put(expr.getSubquery(), joins);
        }
        return joins.booleanValue();
    }

    /**
     * Whether a one-value quantified query, or a NOT IN's, cannot answer NULL, which makes a NULL subject FALSE
     * where no row it answers is NULL (live-verified): by its projection — a NOT NULL column, an expression over one
     * that no NULL reaches as {@code COALESCE(k, 0)}, a VALUES column no row of which is NULL — or, for a query that
     * reads nothing of the outer row once the conditions the statistics settle are folded, by the statistics of the
     * tables it reads: a column whose rows hold no NULL, though declared nullable, and a number computed from such
     * columns. A NULL its WHERE or an inner join leaves out still counts, one a DELETE or an UPDATE removed does not.
     * A column of one value, {@code SELECT 5 FROM t} or a column of a VALUES clause of one row,
     * is compared as that value alone.
     *
     * @param expr     the query
     * @param answered the rows it answered
     * @return whether it cannot answer NULL
     */
    boolean answersNoNull(final SubqueryExpression expr, final ResultSet answered) {
        // A query a table function feeds the outer row is computed for that row, and a NULL beside its rows is
        // UNKNOWN as beside any list of values (live-verified).
        if (answered.getColumns().isEmpty() || PlannedCorrelations.fed(expr.getSubquery())) {
            return false;
        }
        final ResultSetColumn own = answered.getColumns().get(0);
        if (visitor.getQueryExecutor().readsOneValuesRow(expr.getSubquery())) {
            return false;   // a VALUES clause of one row: its column is one value
        }
        if (columnAnswersNoNull(own, answered.getRowCount())) {
            return true;
        }
        if (Boolean.FALSE.equals(own.getDerivedNullable()) || Boolean.FALSE.equals(own.getReportedNullable())) {
            return false;   // no NULL by its projection, but a column of one value
        }
        if (readsOuterRow(expr) && !correlationSettledAway(expr)) {
            return false;
        }
        final List<ResultSetColumn> held = heldColumns(expr);
        return held != null && !held.isEmpty() && columnAnswersNoNull(held.get(0), 0);
    }

    /**
     * Whether a column cannot answer NULL by what its projection is (see {@link #answersNoNull}).
     *
     * @param column   the column
     * @param rowCount the rows answered
     */
    private static boolean columnAnswersNoNull(final ResultSetColumn column, final int rowCount) {
        if (!Boolean.FALSE.equals(column.getDerivedNullable())) {
            // A VALUES column no row of which is NULL tells it so; one of a single row is a column of one value.
            return Boolean.FALSE.equals(column.getReportedNullable()) && rowCount > 1;
        }
        final ValueRange range = column.getValueRange();
        return range == null || range.isEmpty() || range.getMin() == null || range.getMax() == null
            || range.getMin().compareTo(range.getMax()) != 0;
    }

    /**
     * Whether one of the first {@code width} columns of a ROW quantified query may answer NULL as the statistics of
     * the tables it reads tell it: a column whose rows hold a NULL anywhere, whatever its WHERE keeps. False where
     * the query's shape cannot be read.
     *
     * @param expr  the query
     * @param width the places compared
     * @return whether a place may answer NULL
     */
    boolean placeMayAnswerNull(final SubqueryExpression expr, final int width) {
        final List<ResultSetColumn> held = heldColumns(expr);
        if (held == null) {
            return false;
        }
        for (int i = 0; i < width && i < held.size(); i++) {
            if (!Boolean.FALSE.equals(held.get(i).getDerivedNullable())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the place at {@code index} of a query is one value on every row (see
     * {@link QueryExecutor#placeIsConstant}).
     *
     * @param expr  the query
     * @param index the place
     * @param width the places compared
     * @return whether the place is a constant
     */
    boolean placeIsConstant(final SubqueryExpression expr, final int index, final int width) {
        final String key = memoKey(expr);
        boolean[] places = constantPlaces.get(key);
        if (places == null) {
            places = new boolean[width];
            final QueryExecutor queryExecutor = visitor.getQueryExecutor();
            for (int i = 0; i < width; i++) {
                places[i] = queryExecutor.placeIsConstant(expr.getSubquery(), i);
            }
            constantPlaces.put(key, places);
        }
        return index < places.length && places[index];
    }

    /**
     * A query's columns, read for their shape alone with every catalog column answering NULL by what its rows hold
     * (see {@link QueryExecutor#beginHeldNullReading}); null where the query cannot be read so.
     */
    private List<ResultSetColumn> heldColumns(final SubqueryExpression expr) {
        final String key = memoKey(expr);
        if (heldShapes.containsKey(key)) {
            return heldShapes.get(key);
        }
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        List<ResultSetColumn> columns = null;
        final boolean shapeOnly = RelationShapeOnly.begin();
        LateConstantRefusal.beginProbe();
        final StoredColumnValues previous = queryExecutor.beginHeldNullReading();
        try {
            final List<ResultSet> shapes = executeInScope(queryExecutor, expr.getSubquery(), buildLateralContext());
            if (!shapes.isEmpty() && shapes.get(0) != null) {
                columns = shapes.get(0).getColumns();
            }
        } catch (final RuntimeException unread) {
            columns = null;
        } finally {
            queryExecutor.endHeldNullReading(previous);
            LateConstantRefusal.endProbe();
            RelationShapeOnly.end(shapeOnly);
        }
        heldShapes.put(key, columns);
        return columns;
    }


    /** Whether a row of a query's answer is NULL in its first column. */
    private static boolean holdsNullMember(final ResultSet answered) {
        for (final Row row : answered.getRows()) {
            if (DeferredFault.read(row.getValue(0)) == null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a query read the row around it when it last ran: one matched row against row, whose positive
     * membership is two-valued (see {@link #evaluateInSubquery}).
     */
    boolean readsOuterRow(final SubqueryExpression expr) {
        final SubqueryMemo subqueryMemo = visitor.getSubqueryMemo();
        return subqueryMemo != null && subqueryMemo.isCorrelated(memoKey(expr));
    }

    /**
     * Whether a query that read the row around it is matched row against row: not where it reads that row only in
     * filters the statistics settle, which the plan folds away (see
     * {@link CorrelatedSubqueryRule#correlationSettledAway}), so the query is a set as an uncorrelated one is.
     */
    boolean matchesRowAgainstRow(final SubqueryExpression expr) {
        return readsOuterRow(expr) && !correlationSettledAway(expr);
    }

    /**
     * Whether a query read the row around it only in filters the statistics settle (see
     * {@link CorrelatedSubqueryRule#correlationSettledAway}). Its negated membership — NOT IN, {@code <> ALL} — is
     * FALSE for a NULL subject against a set of a row at least, and reads a set of NULLs alone as holding nothing:
     * {@code k NOT IN (SELECT k FROM an WHERE p.id > 0)} over ids 1 to 5 and a column of NULLs alone is TRUE for
     * every k but a NULL one, where its uncorrelated twin is NULL; a NULL beside a value leaves a miss NULL, as
     * {@code o.k NOT IN (SELECT i.k + IFF(o.id > 0, 0, 0) FROM i)} keeps no row (live-verified).
     */
    boolean correlationSettledAway(final SubqueryExpression expr) {
        if (!readsOuterRow(expr)) {
            return false;
        }
        final String key = memoKey(expr);
        Boolean settled = settledCorrelations.get(key);
        if (settled == null) {
            settled = Boolean.FALSE;
            final QueryExecutor queryExecutor = visitor.getQueryExecutor();
            final FrostlakeParser.SelectStatementContext parsed =
                queryExecutor == null ? null : queryExecutor.subqueryStatement(expr.getSubquery());
            if (parsed != null) {
                final Set<String> outerNames = new HashSet<>();
                for (final String name : buildLateralContext().keySet()) {
                    outerNames.add(name.toUpperCase(Locale.ROOT));
                }
                settled = Boolean.valueOf(new CorrelatedSubqueryRule(queryExecutor, outerNames,
                    visitor.scopeRelations()).correlationSettledAway(parsed));
            }
            settledCorrelations.put(key, settled);
        }
        return settled.booleanValue();
    }

    /**
     * Execute a subquery for the row at hand (see {@link #executeSubquery(String)}), counting every position
     * inside it from where its own text begins.
     */
    List<ResultSet> executeSubquery(final SubqueryExpression expr) {
        // A subquery whose values are read raises its own items' faults as it computes them (see RelationBody).
        final boolean outerBody = RelationBody.suspend();
        LateConstantRefusal.beginRowValue();
        try {
            return positionedSubquery(expr);
        } finally {
            LateConstantRefusal.endRowValue();
            RelationBody.end(outerBody);
        }
    }

    /** The rows an EXISTS counts: nothing reads their items, so a row fault waits in its cell. */
    private List<ResultSet> countedSubquery(final SubqueryExpression expr) {
        final boolean outerBody = RelationBody.begin();
        LateConstantRefusal.beginRowValue();
        try {
            return positionedSubquery(expr);
        } finally {
            LateConstantRefusal.endRowValue();
            RelationBody.end(outerBody);
        }
    }

    private List<ResultSet> positionedSubquery(final SubqueryExpression expr) {
        if (expr.getQueryPosition() == null || !ExpressionSource.hasOrigin()) {
            return executeSubquery(expr.getSubquery(), memoKey(expr));
        }
        final SourcePosition displaced = ExpressionSource.beginNested(expr.getQueryPosition());
        try {
            return executeSubquery(expr.getSubquery(), memoKey(expr));
        } finally {
            ExpressionSource.end(displaced);
        }
    }

    /**
     * Compile a subquery before any row reaches it: plan it for its shape alone (see
     * {@link RelationShapeOnly}) with this scope's names bound to NULL as its outer names, inside
     * {@link SubqueryCompilation}, so every plan-time rule judges it as it judges a statement. A name it
     * cannot resolve is refused here. Any other refusal of its compilation is handed back for the caller
     * to raise once the statement around it has been judged, which is the order live reports them in: an
     * outer call's arity before a subquery's QUALIFY without a window. Under {@link SubqueryTypeRanking} a
     * refusal about types is raised here too, ahead of the enclosing query's own. What only a row can raise waits for
     * the row, and so does the refusal of a correlation live cannot evaluate, which live makes only where
     * its optimizer keeps the subquery (see {@link #requireSupportedCorrelation}).
     *
     * @param expr           the subquery
     * @param enclosingNames the names of the query around this scope's query when that one is compiling, or null
     * @return the subquery's refusal that waits for the statement around it, or null
     */
    RuntimeException compile(final SubqueryExpression expr, final Map<String, Object> enclosingNames) {
        final Map<String, Object> outerNames = compileScope(expr, enclosingNames);
        final boolean ranksTypes = SubqueryTypeRanking.isActive();
        final SourcePosition displaced = ExpressionSource.beginNested(expr.getQueryPosition());
        final boolean shapeOnly = RelationShapeOnly.begin();
        final String unread = unreadSelectList(expr);
        final String waived = unread != null ? UnreadRowItems.begin(unread) : null;
        final String body = expr.isExistence() ? null
            : SelectItemAccessors.soleSelectListText(visitor.getQueryExecutor().subqueryStatement(expr.getSubquery()));
        final String enclosingBody = body != null ? ScalarBodyRowItems.begin(body) : null;
        final Map<String, Object> enclosing = SubqueryCompilation.begin(outerNames);
        final ExpressionEvaluatorVisitor enclosingScope = SubqueryCompilation.beginScope(visitor);
        final boolean enclosingRanking = SubqueryTypeRanking.begin(false);
        try {
            // The compiled shape says whether the subquery's select list hands out values a projection policy
            // restricts, which the select list holding it then hands out too (ProjectionLineage).
            final SubqueryExpression compiled = expr.isExistence() ? countedBody(expr, outerNames)
                : derivedBody(expr, outerNames);
            ProjectionLineage.recordSubquery(expr.getSubquery(), visitor.getQueryExecutor()
                .executeWithLateralContext(compiled.getSubquery(), outerNames));
            return null;
        } catch (final UnsupportedSubqueryException judgedPerRow) {
            return null;
        } catch (final PrivilegeRefusal refused) {
            // A user's function the role may not use is refused as the statement compiles, whether or not a row
            // reaches the subquery: raised once the statement around it has been judged.
            return refused;
        } catch (final RuntimeException refused) {
            if (!SqlCompilationError.isCompilationError(refused.getMessage())) {
                return null;
            }
            if (groupsBesideOuterAggregate(expr, outerNames, refused.getMessage())) {
                // The outer query's aggregate is a value here, so the subquery groups nothing of its own: the
                // statement's correlation judgement refuses it, not its own grouping.
                return null;
            }
            if (refusesAName(refused.getMessage()) || refusesACorrelatedWindow(refused.getMessage())
                    || refusesAnAggregatePlace(refused.getMessage())
                    || ranksTypes && SubqueryTypeRanking.ranksAheadOfEnclosingTypes(refused.getMessage())) {
                throw refused;
            }
            return refused;
        } finally {
            SubqueryTypeRanking.end(enclosingRanking);
            SubqueryCompilation.endScope(enclosingScope);
            SubqueryCompilation.end(enclosing);
            if (body != null) {
                ScalarBodyRowItems.end(enclosingBody);
            }
            if (unread != null) {
                UnreadRowItems.end(waived);
            }
            RelationShapeOnly.end(shapeOnly);
            ExpressionSource.end(displaced);
        }
    }

    /**
     * Whether a grouping refusal of a subquery stands beside an aggregate over the outer row alone in its own select
     * list: {@code (SELECT MAX(fz.id) + g.v FROM g)} is refused as a subquery live cannot evaluate, not for its
     * {@code g.v} (live-verified).
     */
    private boolean groupsBesideOuterAggregate(final SubqueryExpression expr, final Map<String, Object> names,
                                               final String message) {
        if (message == null || !message.contains("is not a valid group by expression") || expr.isExistence()) {
            return false;
        }
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        final FrostlakeParser.SelectStatementContext parsed = queryExecutor.subqueryStatement(expr.getSubquery());
        if (parsed == null || parsed.selectOperand().size() != 1 || parsed.selectOperand(0).selectClause() == null) {
            return false;
        }
        final FrostlakeParser.SelectClauseContext own = parsed.selectOperand(0).selectClause();
        final Set<String> outerNames = new HashSet<>();
        for (final String name : names.keySet()) {
            outerNames.add(name.toUpperCase(Locale.ROOT));
        }
        for (final ParserRuleContext call : new CorrelatedSubqueryRule(queryExecutor, outerNames,
                visitor.scopeRelations()).outerAggregates(parsed)) {
            ParseTree at = call.getParent();
            while (at != null && !(at instanceof FrostlakeParser.SelectListContext)
                    && !(at instanceof FrostlakeParser.SelectClauseContext)) {
                at = at.getParent();
            }
            if (at instanceof FrostlakeParser.SelectListContext && at.getParent() == own) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a compilation refusal names a window call that reads the outer row, which live raises as it
     * compiles the statement, ahead of what the statement's rows would raise (see {@code WindowCorrelationRule}).
     */
    private static boolean refusesACorrelatedWindow(final String message) {
        return message.contains("Window function [") && message.contains("] contains a correlation.");
    }

    /**
     * Whether a compilation refusal is of a subquery standing where the aggregate it holds does not let it, which
     * live raises as it compiles the statement, once the names have resolved (see {@code CorrelatedAggregatePlace}).
     */
    private static boolean refusesAnAggregatePlace(final String message) {
        return message.contains("Subquery containing correlated aggregate function [");
    }

    /** Whether a compilation refusal is about a name: a column, a function or a relation nothing resolves. */
    static boolean refusesAName(final String message) {
        // A USING column either side lacks is its own capitalised sentence: "Invalid identifier N".
        return message.contains("invalid identifier") || message.contains("Invalid identifier ")
            || message.contains("Unknown function")
            || message.contains("does not exist or not authorized") || message.contains("ambiguous column name");
    }

    /**
     * The names a subquery compiled in this scope may read from it, each bound to NULL: every column of
     * every relation in scope, qualified by the name the query gives that relation (an alias hides the
     * table's own name) and bare, and the names of any query around this one. A LATERAL derived table reads
     * the relations to its left one by one, so a USING or NATURAL key, a column of each, is ambiguous there
     * bare, where a subquery in an expression reads the merged key (live-verified).
     */
    private Map<String, Object> compileScope(final SubqueryExpression expr, final Map<String, Object> enclosingNames) {
        final Map<String, Object> names = new HashMap<>();
        if (enclosingNames != null) {
            names.putAll(enclosingNames);
        }
        final Table table = visitor.getTable();
        final Map<String, Table> aliasToTable = visitor.getMultiTableAliasToTable();
        final Set<String> columns = new HashSet<>();
        if (aliasToTable != null && !aliasToTable.isEmpty()) {
            for (final Map.Entry<String, Table> entry : aliasToTable.entrySet()) {
                for (final TableColumn column : entry.getValue().getColumns()) {
                    names.put(entry.getKey() + "." + column.getName(), null);
                    names.put(column.getName(), null);
                    columns.add(column.getName());
                }
            }
        } else if (table != null) {
            for (final TableColumn column : table.getColumns()) {
                names.put(table.getName() + "." + column.getName(), null);
            }
        }
        if (table != null) {
            for (final TableColumn column : table.getColumns()) {
                names.put(column.getName(), null);
                columns.add(column.getName());
            }
            for (final String shared : sharedBareNames(table, visitor.getMultiTableAllTables())) {
                names.put(shared, OuterNameBindings.AMBIGUOUS);
            }
            if (expr.isLateral() && table.getJoinKeyNames() != null) {
                for (final String key : table.getJoinKeyNames()) {
                    if (names.containsKey(key)) {
                        names.put(key, OuterNameBindings.AMBIGUOUS);
                    }
                }
            }
        }
        OuterNameBindings.recordColumns(names, columns);
        // Named by their FROM aliases, the relations hold every qualifier a reference to them may carry: a
        // table function's argument under any other names no column, and is refused as the statement compiles.
        OuterNameBindings.recordAliasesBound(names, aliasToTable != null && !aliasToTable.isEmpty()
            && (enclosingNames == null || OuterNameBindings.aliasesBound(enclosingNames)));
        return names;
    }

    /**
     * The bare names two relations of a joined row both carry, which live refuses as ambiguous: every one an ON
     * or a comma join repeats, a table joined to itself included, none of a USING or NATURAL join's, which read the
     * left relation's. Relations are told apart by instance, since a self-join's two instances share one name.
     */
    private static Set<String> sharedBareNames(final Table table, final List<Table> relations) {
        final Set<String> shared = new HashSet<>();
        if (relations == null || relations.size() < 2
                || table.getJoinKeyNames() != null && !table.getJoinKeyNames().isEmpty()) {
            return shared;
        }
        final Set<String> seen = new HashSet<>();
        final List<Table> counted = new ArrayList<>();
        for (final Table relation : relations) {
            if (countedAlready(counted, relation)) {
                continue;
            }
            counted.add(relation);
            final Set<String> own = new HashSet<>();
            for (final TableColumn column : relation.getColumns()) {
                own.add(column.getName());
            }
            for (final String name : own) {
                if (!seen.add(name)) {
                    shared.add(name);
                }
            }
        }
        return shared;
    }

    /** Whether a list holds this very relation instance. */
    private static boolean countedAlready(final List<Table> counted, final Table relation) {
        for (final Table seen : counted) {
            if (seen == relation) {
                return true;
            }
        }
        return false;
    }

    /**
     * Execute a subquery, memoizing the result of uncorrelated subqueries (those that read no outer
     * value during execution) so they run once per outer query instead of once per outer row. The
     * first execution probes the per-thread lateral-read counter; a zero delta proves the subquery is
     * uncorrelated and its result reusable. Correlated subqueries always re-execute. With no memo
     * configured this is exactly the previous behaviour (build context, execute).
     */
    List<ResultSet> executeSubquery(final String subquery) {
        return executeSubquery(subquery, subquery);
    }

    /**
     * Execute a subquery, memoizing it under {@code memoKey} — the COPY, where the text alone would make
     * two copies of one text share an answer the account computes twice.
     */
    private List<ResultSet> executeSubquery(final String subquery, final String memoKey) {
        final SubqueryMemo subqueryMemo = visitor.getSubqueryMemo();
        if (subqueryMemo != null) {
            final List<ResultSet> cached = subqueryMemo.cachedResult(memoKey);
            if (cached != null) {
                return cached;
            }
        }
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        final Map<String, Object> context = buildLateralContext();
        if (subqueryMemo == null || subqueryMemo.isCorrelated(memoKey)
                || subqueryMemo.isRedrawnPerRow(memoKey)) {
            return executeInScope(queryExecutor, subquery, context);
        }
        final long before = visitor.lateralReadCount();
        final long drawnBefore = visitor.rowDrawCount();
        final List<ResultSet> results = executeInScope(queryExecutor, subquery, context);
        if (visitor.rowDrawCount() != drawnBefore) {
            // It drew a value of its own: the next row gets another one, so no answer of its is reusable.
            subqueryMemo.recordRedrawnPerRow(memoKey);
        } else if (visitor.lateralReadCount() == before) {
            subqueryMemo.recordUncorrelated(memoKey, results);
        } else {
            subqueryMemo.recordCorrelated(memoKey);
        }
        return results;
    }

    /**
     * The memo key for one COPY of a subquery: its text and the place that copy was written at. Two
     * copies of one text are two subqueries, and a copy keeps its key across the outer rows.
     */
    private static String memoKey(final SubqueryExpression expr) {
        final SourcePosition at = expr.getQueryPosition() != null ? expr.getQueryPosition() : expr.getPosition();
        return at == null ? expr.getSubquery()
            : expr.getSubquery() + "@" + at.getLine() + ":" + at.getCharPositionInLine();
    }

    /** Run a subquery for a row with this scope named as the one around it (see {@link SubqueryCompilation#outerScope}). */
    private List<ResultSet> executeInScope(final QueryExecutor queryExecutor, final String subquery,
                                           final Map<String, Object> context) {
        final ExpressionEvaluatorVisitor enclosingScope = SubqueryCompilation.beginScope(visitor);
        final Object[] enclosingText = RunningSubqueryText.begin(subquery);
        final Object[] enclosingFilters = OuterFilters.beginSubquery();
        try {
            return queryExecutor.executeWithLateralContext(subquery, context);
        } finally {
            OuterFilters.endSelectList(enclosingFilters);
            RunningSubqueryText.end(enclosingText);
            SubqueryCompilation.endScope(enclosingScope);
        }
    }

    /**
     * Refuse a correlated subquery whose shape live cannot evaluate (see {@link CorrelatedSubqueryRule}) the
     * first time a row reaches it, so an outer query that reads no row never meets the refusal, as live's
     * never does. Each subquery is judged once per use for this evaluator. The refusal is positioned at
     * the subquery's SELECT, or at the EXISTS keyword of an EXISTS. A subquery the statement's plan already
     * refused (see {@link PlannedCorrelations}) is not run: its refusal waits for the statement.
     *
     * @param in      whether a membership is an IN rather than an EXISTS, which live limits differently
     * @param not     whether the IN is a NOT IN, which no semi-join answers
     * @param subject the IN's left operand, or null
     * @return true when the statement's plan refused the subquery, which the caller then reads as empty
     */
    private boolean requireSupportedCorrelation(final SubqueryExpression expr, final boolean scalar,
                                                final boolean in, final boolean not, final Expression subject) {
        return requireSupportedCorrelation(expr, expr, scalar, in, not, subject, expr.getPosition());
    }

    /**
     * {@link #requireSupportedCorrelation(SubqueryExpression, boolean, boolean, boolean, Expression)} for a
     * quantified comparison's query, judged as an IN's is: under ANY or SOME as a membership, under ALL as a NOT IN,
     * and refused at the quantifier, whatever the comparison operator (live-verified).
     *
     * @param expr the quantified comparison
     * @return true when the statement's plan refused the query, which the caller then reads as empty
     */
    boolean requireSupportedQuantified(final QuantifiedComparisonExpression expr) {
        final SubqueryExpression query = (SubqueryExpression) expr.getSubquery();
        final boolean all = expr.getQuantifier() == Quantifier.ALL;
        final boolean equality = !all && expr.getOperator() == BinaryOperator.EQUAL;
        final boolean orderedBefore = orderedUniversal;
        orderedUniversal = all && expr.getOperator() != BinaryOperator.NOT_EQUAL;
        try {
            return requireSupportedCorrelation(query, query, false, true, all, equality ? expr.getLeft() : null,
                expr.getQuantifierPosition());
        } finally {
            orderedUniversal = orderedBefore;
        }
    }

    /**
     * {@link #requireSupportedCorrelation(SubqueryExpression, boolean, boolean, boolean, Expression)} for a
     * subquery run as {@code run} where it is written as {@code expr}: the shape judged is the one run.
     *
     * @param place where a refusal is placed
     */
    private boolean requireSupportedCorrelation(final SubqueryExpression expr, final SubqueryExpression run,
                                                final boolean scalar, final boolean in, final boolean not,
                                                final Expression subject, final SourcePosition place) {
        if (UnreadExistsSubqueries.waives(expr.getSubquery())) {
            // Inside an EXISTS body's select item no row reads: compiled, never planned.
            return false;
        }
        final String key = scalar ? PlannedCorrelations.valueKey(expr.getSubquery())
            : PlannedCorrelations.membershipKey(expr.getSubquery());
        if (PlannedCorrelations.refused(key)) {
            return true;
        }
        if (judgedCorrelations.contains(key) || PlannedCorrelations.answered(key)) {
            return false;
        }
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        final FrostlakeParser.SelectStatementContext parsed = queryExecutor.subqueryStatement(run.getSubquery());
        if (parsed != null && scalar && opensWithOuterAggregate(queryExecutor, parsed)) {
            throw unsupported(true, place);
        }
        if (parsed != null && !(scalar && visitor.isCorrelationsPlanned()) && !outerHoldsOneRowAtMost(queryExecutor)) {
            final Set<String> outerNames = new HashSet<>();
            for (final String name : buildLateralContext().keySet()) {
                outerNames.add(name.toUpperCase(Locale.ROOT));
            }
            final Map<String, Boolean> pinned = new HashMap<>();
            final Map<String, Boolean> varying = new HashMap<>();
            final OuterNameConstancy constancy = new OuterNameConstancy() {
                @Override
                public boolean holdsOneValue(final String qualifier, final String column) {
                    final String key = qualifier == null ? column : qualifier + "." + column;
                    Boolean holds = pinned.get(key);
                    if (holds == null) {
                        holds = Boolean.valueOf(holdsOneValueInScope(qualifier, column));
                        pinned.put(key, holds);
                    }
                    return holds.booleanValue();
                }

                @Override
                public boolean variesAcrossRows(final String qualifier, final String column) {
                    if (holdsOneValue(qualifier, column)) {
                        return false;
                    }
                    final String key = qualifier == null ? column : qualifier + "." + column;
                    Boolean varies = varying.get(key);
                    if (varies == null) {
                        varies = Boolean.valueOf(variesInScope(qualifier, column));
                        varying.put(key, varies);
                    }
                    return varies.booleanValue();
                }

                @Override
                public Boolean settledBoolean(final String qualifier, final String column) {
                    return settledBooleanInScope(qualifier, column);
                }
            };
            final CorrelatedSubqueryRule rule =
                new CorrelatedSubqueryRule(queryExecutor, outerNames, visitor.scopeRelations(), constancy);
            // Where the IN stands is not known here, so a positive one is read as the semi-join a WHERE makes of it;
            // the statement's plan has refused it already where it stands anywhere else.
            rule.quantifiedAs(in && not, orderedUniversal, false);
            if (scalar ? rule.refusesScalar(parsed)
                    : rule.refusesMembership(parsed, in, in && !not, subject == null ? null : printed(subject),
                        in && not)) {
                throw nestedReadRefusal(run, rule, scalar, place);
            }
        }
        judgedCorrelations.add(key);
        return false;
    }

    /**
     * The refusal of a correlated subquery live cannot turn into a join, judged as a row of the subquery holding it
     * reaches it: one whose own subquery reads that row is placed as the statement's plan places it (see
     * {@link NestedSubqueryRefusalPlace}), found where it stands in the running subquery's text, and any other as
     * {@link #unsupported} places it.
     */
    private UnsupportedSubqueryException nestedReadRefusal(final SubqueryExpression run,
                                                           final CorrelatedSubqueryRule rule, final boolean scalar,
                                                           final SourcePosition place) {
        if (scalar && visitor.sqlFunctionBeingCalled() != null) {
            return unsupported(true, place);
        }
        final ParserRuleContext judged =
            RunningSubqueryText.holderOf(visitor.getQueryExecutor(), ExpressionSource.resolve(run.getQueryPosition()));
        final FrostlakeParser.SelectStatementContext query = judged == null ? null : holdersQuery(judged);
        if (query == null) {
            return unsupported(scalar, place);
        }
        final FrostlakeParser.SelectClauseContext clause = CorrelatedSubqueryRule.mainClause(query);
        if (!scalar && clause != null && clause.FROM() == null
                && NestedSubqueryRefusalPlace.readsBeyondItsHolder(judged, clause)) {
            // An IN, a quantified comparison or an EXISTS with no FROM inside the subquery running, reading a row
            // further out than that subquery's, gives no place (live-verified), where a scalar one, and one reading
            // the running subquery's own row alone, is placed.
            return new UnsupportedSubqueryException(
                SqlCompilationError.of("Unsupported subquery type cannot be evaluated"));
        }
        if (rule.outerReadDepth() != 2) {
            return unsupported(scalar, place);
        }
        final Token nested = NestedSubqueryRefusalPlace.of(judged, query, rule);
        if (nested != null) {
            final SourcePosition at = RunningSubqueryText.place(nested);
            return new UnsupportedSubqueryException(SqlCompilationError.of(
                "Unsupported subquery type cannot be evaluated"
                    + (at == null ? "" : " at line " + at.getLine() + ", position " + at.getCharPositionInLine())));
        }
        if (NestedSubqueryRefusalPlace.standsInWhere(judged)
                && NestedSubqueryRefusalPlace.unplacedAtDepthTwo(judged, query, rule)) {
            return new UnsupportedSubqueryException(
                SqlCompilationError.of("Unsupported subquery type cannot be evaluated"));
        }
        return unsupported(scalar, place);
    }

    /** The query of a subquery-holding expression {@link RunningSubqueryText#holderOf} found. */
    private static FrostlakeParser.SelectStatementContext holdersQuery(final ParserRuleContext judged) {
        if (judged instanceof FrostlakeParser.ScalarSubqueryExprContext) {
            return ((FrostlakeParser.ScalarSubqueryExprContext) judged).selectStatement();
        }
        if (judged instanceof FrostlakeParser.InSubqueryExprContext) {
            return ((FrostlakeParser.InSubqueryExprContext) judged).selectStatement();
        }
        if (judged instanceof FrostlakeParser.TupleInSubqueryExprContext) {
            return ((FrostlakeParser.TupleInSubqueryExprContext) judged).selectStatement();
        }
        if (judged instanceof FrostlakeParser.QuantifiedComparisonExprContext) {
            return ((FrostlakeParser.QuantifiedComparisonExprContext) judged).selectStatement();
        }
        if (judged instanceof FrostlakeParser.ExistsExprContext) {
            return ((FrostlakeParser.ExistsExprContext) judged).selectStatement();
        }
        return null;
    }

    /**
     * The refusal of a correlated subquery live cannot turn into a join: placed where it is written, or naming the SQL
     * function whose body holds a scalar one.
     */
    private UnsupportedSubqueryException unsupported(final boolean scalar, final SourcePosition place) {
        final Function called = scalar ? visitor.sqlFunctionBeingCalled() : null;
        if (called != null) {
            return UnsupportedSubqueryException.inside(called);
        }
        final SourcePosition at = ExpressionSource.resolve(place);
        return new UnsupportedSubqueryException(SqlCompilationError.of("Unsupported subquery type cannot be evaluated"
            + (at == null ? "" : " at line " + at.getLine() + ", position " + at.getCharPositionInLine())));
    }

    /** An expression written back as text the rule can read, or null where it cannot be printed. */
    private static String printed(final Expression expression) {
        try {
            return AstPrinterVisitor.print(expression);
        } catch (final RuntimeException unprintable) {
            return null;
        }
    }

    /**
     * Whether this scope's statistics pin a name of its row to one value that is never NULL (see
     * {@link OuterNameConstancy}): the interval a number carries, or a catalog column of any scalar family whose
     * rows hold one value (see {@link StoredColumnValues}) on a relation no outer join extends with NULLs. A derived
     * relation's column is one where it passes such a column through or projects a literal that is not NULL (see
     * {@link DerivedColumnLineage}). A name a row further out binds carries no statistics here and is never one.
     */
    private boolean holdsOneValueInScope(final String qualifier, final String column) {
        if (visitor.getTable() == null || visitor.isScopeOpaqueToSubqueries()) {
            return false;
        }
        try {
            if (OuterNameConstancy.pinsOneValue(
                    visitor.inferStaticRange(new ColumnReferenceExpression(qualifier, column)))) {
                return true;
            }
            final Table owner = scopeRelationCarrying(qualifier, column);
            if (owner == null || mayBeNullExtended(owner)) {
                return false;
            }
            if (owner.residentSource() != null) {
                return new StoredColumnValues(visitor.getQueryExecutor()).holdsOneValue(owner, column);
            }
            final DerivedColumnLineage lineage = DerivedColumnLineage.of(owner, column);
            return lineage != null && (lineage.isLiteral() || lineage.storedTable() != null
                && new StoredColumnValues(visitor.getQueryExecutor())
                    .holdsOneValue(lineage.storedTable(), lineage.storedColumn()));
        } catch (final RuntimeException unresolved) {
            return false;
        }
    }

    /**
     * Whether this scope's statistics show a name of its row taking more than one value (see
     * {@link OuterNameConstancy#variesAcrossRows}), asked of a name they do not pin. A catalog table's column does,
     * and so does a name not placed in this scope. A derived relation's column does only where it passes a stored
     * column holding more than one value through (see {@link DerivedColumnLineage}), whatever a WHERE on the way
     * keeps: an expression or a column of a relation the plan keeps whole is a value the statistics do not reach.
     */
    private boolean variesInScope(final String qualifier, final String column) {
        if (visitor.getTable() == null || visitor.isScopeOpaqueToSubqueries()) {
            return true;
        }
        try {
            final Table owner = scopeRelationCarrying(qualifier, column);
            if (owner == null || owner.residentSource() != null) {
                return true;
            }
            final DerivedColumnLineage lineage = DerivedColumnLineage.of(owner, column);
            return lineage != null && !lineage.isLiteral() && !new StoredColumnValues(visitor.getQueryExecutor())
                .holdsOneValue(lineage.storedTable(), lineage.storedColumn());
        } catch (final RuntimeException unresolved) {
            return false;
        }
    }

    /**
     * The BOOLEAN this scope's statistics settle a stored number of its row to (see
     * {@link OuterNameConstancy#settledBoolean}): null for a name of no catalog table, or one a NULL may extend.
     */
    private Boolean settledBooleanInScope(final String qualifier, final String column) {
        if (visitor.getTable() == null || visitor.isScopeOpaqueToSubqueries()) {
            return null;
        }
        try {
            final Table owner = scopeRelationCarrying(qualifier, column);
            return owner == null || mayBeNullExtended(owner) || !owner.hasColumn(column) ? null
                : visitor.getQueryExecutor().columnSettledBoolean(owner, owner.getColumn(column).getName());
        } catch (final RuntimeException unresolved) {
            return null;
        }
    }

    /**
     * The relation of this scope a name reads: the one its qualifier names, else the one relation carrying the
     * column, a join's merged relation aside; null where none or several do.
     */
    private Table scopeRelationCarrying(final String qualifier, final String column) {
        Table owner = null;
        for (final Map.Entry<String, Table> relation : visitor.scopeRelations().entrySet()) {
            final Table candidate = relation.getValue();
            if (qualifier != null) {
                if (relation.getKey().equalsIgnoreCase(qualifier)) {
                    return candidate;
                }
                continue;
            }
            if (candidate.getJoinedRelations() != null || !candidate.hasColumn(column) || candidate == owner) {
                continue;
            }
            if (owner != null) {
                return null;
            }
            owner = candidate;
        }
        return owner;
    }

    /** Whether the scope joins relations and an outer join may extend {@code owner} with NULLs its rows do not hold. */
    private boolean mayBeNullExtended(final Table owner) {
        final List<Table> relations = visitor.getMultiTableAllTables();
        if (relations == null || relations.size() < 2) {
            return false;
        }
        final JoinedRelations joined = visitor.getTable() == null ? null : visitor.getTable().getJoinedRelations();
        return joined == null || !joined.includes(owner) || joined.isNullExtended(owner);
    }

    /**
     * The refusal of a scalar subquery standing alone as a select item that opens with the outer query's aggregate
     * over a row drawn from catalog tables (see {@link #opensWithOuterAggregate}), made as the statement compiles: live
     * refuses it before any row reaches it, over an empty table and under a WHERE no row passes (live-verified). It
     * waits for the rest of the statement to be judged, and a row reaching the subquery is refused the same way first.
     *
     * @param expr the select item's subquery
     * @return the refusal, or null
     */
    UnsupportedSubqueryException openingOuterAggregateRefusal(final SubqueryExpression expr) {
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        if (queryExecutor == null || expr.isExistence()) {
            return null;
        }
        final FrostlakeParser.SelectStatementContext parsed = queryExecutor.subqueryStatement(expr.getSubquery());
        // No row is bound yet: the outer names are the scope's own.
        return parsed != null && opensWithOuterAggregate(queryExecutor, parsed, outerNamesContext().keySet())
            ? unsupported(true, expr.getPosition()) : null;
    }

    /**
     * Whether a scalar subquery opens with the outer query's aggregate (see
     * {@link CorrelatedSubqueryRule#opensWithOuterAggregate}) over a row drawn from catalog tables, which live refuses
     * whatever the tables hold: neither {@link #outerHoldsOneRowAtMost} nor the statistics pinning the names it reads
     * spare it. Over a derived table's row the call is judged as any other (live-verified).
     */
    private boolean opensWithOuterAggregate(final QueryExecutor queryExecutor,
                                            final FrostlakeParser.SelectStatementContext parsed) {
        return opensWithOuterAggregate(queryExecutor, parsed, buildLateralContext().keySet());
    }

    /** Whether a scalar subquery opens with the outer query's aggregate, read over the given outer names. */
    private boolean opensWithOuterAggregate(final QueryExecutor queryExecutor,
                                            final FrostlakeParser.SelectStatementContext parsed,
                                            final Set<String> names) {
        final List<Table> relations = new ArrayList<>();
        if (visitor.getMultiTableAllTables() != null && !visitor.getMultiTableAllTables().isEmpty()) {
            relations.addAll(visitor.getMultiTableAllTables());
        } else if (visitor.getTable() != null) {
            relations.add(visitor.getTable());
        }
        if (relations.isEmpty()) {
            return false;
        }
        for (final Table relation : relations) {
            if (!relation.isCatalogResident()) {
                return false;
            }
        }
        final Set<String> outerNames = new HashSet<>();
        for (final String name : names) {
            outerNames.add(name.toUpperCase(Locale.ROOT));
        }
        final CorrelatedSubqueryRule rule =
            new CorrelatedSubqueryRule(queryExecutor, outerNames, visitor.scopeRelations());
        if (rule.opensWithOuterAggregate(parsed)) {
            return true;
        }
        final FrostlakeParser.SelectClauseContext clause = CorrelatedSubqueryRule.mainClause(parsed);
        return clause != null && clause.tableExpression() != null
            && nestedOpensWithOuterAggregate(queryExecutor, rule, new ArrayList<FrostlakeParser.SelectClauseContext>(),
                clause);
    }

    /**
     * What settles a condition of {@code clause}'s select items from the statistics of the one catalog table it reads,
     * or null where only a constant does: it reads anything else, its table holds no row, or its WHERE keeps no row
     * (see {@link UnguardedSubqueryWalk}). Over g holding 7 and 8, {@code (SELECT CASE WHEN g.v > 100 THEN (SELECT
     * MAX(o.i) FROM g g2) END FROM g LIMIT 1)} is answered (live-verified).
     *
     * @param filtered whether the rows are those the WHERE keeps, rather than every row of the table
     */
    private ExpressionEvaluatorVisitor rowStatistics(final QueryExecutor queryExecutor,
                                                     final FrostlakeParser.SelectClauseContext clause,
                                                     final boolean filtered) {
        final FrostlakeParser.TableExpressionContext from = clause.tableExpression();
        if (from == null || !from.joinClause().isEmpty() || from.tableReference().size() != 1) {
            return null;
        }
        final FrostlakeParser.TableReferenceContext relation = from.tableReference(0);
        final FrostlakeParser.TableSourceContext source = relation.tableSource();
        if (source == null || source.tableQualifiedName() == null) {
            return null;
        }
        final Table table;
        try {
            table = queryExecutor.getCatalog().resolveTable(
                SqlIdentifiers.canonicalText(source.tableQualifiedName().getText()));
        } catch (final RuntimeException notATable) {
            return null;
        }
        if (table == null || !table.isCatalogResident()) {
            return null;
        }
        final Long rows = queryExecutor.baseTableRowCount(table);
        if (rows == null || rows.longValue() == 0L) {
            return null;
        }
        final String alias = relation.aliasName() != null
            ? ParseTreeText.getIdentifier(relation.aliasName())
            : relation.nonJoinKeywordIdentifier() != null
                ? SqlIdentifiers.canonicalText(relation.nonJoinKeywordIdentifier().getText()) : table.getName();
        final ExpressionEvaluator scope =
            new ExpressionEvaluator(table, queryExecutor.getFunctionRegistry(), queryExecutor.getCatalog(), queryExecutor);
        final Map<String, Table> aliasToTable = new HashMap<>();
        aliasToTable.put(alias.toUpperCase(Locale.ROOT), table);
        scope.setMultiTableContext(aliasToTable, Collections.singletonList(table));
        if (filtered && clause.whereClause() != null) {
            try {
                if (Boolean.FALSE.equals(scope.settledCondition(
                        ExpressionEvaluator.parse(ParseTreeText.getOriginalText(clause.whereClause().booleanExpr()))))) {
                    return null;
                }
            } catch (final RuntimeException unsettled) {
                return scope.scopeVisitor();
            }
        }
        return scope.scopeVisitor();
    }

    /**
     * The outer query's own statistics, which settle a condition over the outer row that the inner query's leave
     * open, where each relation it reads is a catalog table holding rows: {@code CASE WHEN o.i > 100 THEN …} over o
     * holding 1 is settled (live-verified). Null otherwise.
     */
    private ExpressionEvaluatorVisitor outerStatistics(final QueryExecutor queryExecutor) {
        final List<Table> relations = new ArrayList<>();
        if (visitor.getMultiTableAllTables() != null && !visitor.getMultiTableAllTables().isEmpty()) {
            relations.addAll(visitor.getMultiTableAllTables());
        } else if (visitor.getTable() != null) {
            relations.add(visitor.getTable());
        }
        if (relations.isEmpty()) {
            return null;
        }
        for (final Table relation : relations) {
            final Long rows = relation != null && relation.isCatalogResident()
                ? queryExecutor.baseTableRowCount(relation) : null;
            if (rows == null || rows.longValue() == 0L) {
                return null;
            }
        }
        return visitor;
    }

    /**
     * Whether {@code clause} evaluates, whatever its row holds (see {@link UnguardedSubqueryWalk}, only a constant or
     * the statistics settling a branch or a conjunction), a scalar subquery opening with the outer query's aggregate,
     * at any depth: in a select item, its WHERE, a join's ON, its GROUP BY, its HAVING or its ORDER BY. Live refuses
     * the outer query's subquery reading a table in its place, at its own position, whatever the tables hold:
     * {@code (SELECT (SELECT MAX(e.i) FROM g) FROM g)}, {@code (SELECT 1 + (SELECT MAX(e.i) FROM g) FROM g)} and
     * {@code (SELECT g.v FROM g WHERE g.v = (SELECT MAX(e.i) FROM g g2))} over an empty {@code e} are refused at the
     * outer subquery, also under its own WHERE FALSE, while {@code IFF(FALSE, (SELECT MAX(e.i) FROM g), 0)} spares it.
     * A select that aggregates its rows into one, ungrouped, plans its subqueries itself: {@code (SELECT MAX(v) +
     * (SELECT MAX(e.i) FROM g) FROM g)} is refused at the inner subquery (all live-verified).
     *
     * @param enclosing the queries around {@code clause} inside the outer query's subquery, outermost first
     */
    private boolean nestedOpensWithOuterAggregate(final QueryExecutor queryExecutor, final CorrelatedSubqueryRule rule,
                                                  final List<FrostlakeParser.SelectClauseContext> enclosing,
                                                  final FrostlakeParser.SelectClauseContext clause) {
        if (clause.selectList() == null || rule.aggregatesUngrouped(clause)) {
            return false;
        }
        final List<FrostlakeParser.SelectClauseContext> around = new ArrayList<>(enclosing);
        around.add(clause);
        final List<ParserRuleContext> items = new ArrayList<>();
        for (final FrostlakeParser.SelectItemContext item : clause.selectList().selectItem()) {
            final ParserRuleContext value = SelectItemAccessors.getItemExpression(item);
            if (value != null) {
                items.add(value);
            }
        }
        final List<ParserRuleContext> filters = new ArrayList<>();
        if (clause.whereClause() != null) {
            filters.add(clause.whereClause().booleanExpr());
        }
        if (clause.tableExpression() != null) {
            for (final FrostlakeParser.JoinClauseContext join : clause.tableExpression().joinClause()) {
                if (join.booleanExpr() != null) {
                    filters.add(join.booleanExpr());
                }
            }
        }
        if (clause.groupByClause() != null) {
            for (final FrostlakeParser.GroupByElementContext element : clause.groupByClause().groupByElement()) {
                if (KeyContexts.of(element) != null) {
                    filters.add(KeyContexts.of(element));
                }
            }
        }
        if (clause.havingClause() != null) {
            filters.add(clause.havingClause().booleanExpr());
        }
        if (clause.getParent() != null
                && clause.getParent().getParent() instanceof FrostlakeParser.SelectStatementContext) {
            final FrostlakeParser.SelectStatementContext statement =
                (FrostlakeParser.SelectStatementContext) clause.getParent().getParent();
            if (statement.selectOperand().size() == 1 && statement.orderByClause() != null) {
                for (final FrostlakeParser.OrderItemContext key : statement.orderByClause().orderItem()) {
                    if (KeyContexts.of(key) != null) {
                        filters.add(KeyContexts.of(key));
                    }
                }
            }
        }
        // The select items are judged over the rows the WHERE keeps; the filters over the table's own.
        final ExpressionEvaluatorVisitor kept = items.isEmpty() ? null : rowStatistics(queryExecutor, clause, true);
        final ExpressionEvaluatorVisitor all = filters.isEmpty() ? null : rowStatistics(queryExecutor, clause, false);
        final ExpressionEvaluatorVisitor outer = kept == null && all == null ? null : outerStatistics(queryExecutor);
        return nestedOpensIn(queryExecutor, rule, around, items, kept, kept == null ? null : outer, false)
            || nestedOpensIn(queryExecutor, rule, around, filters, all, all == null ? null : outer, true);
    }

    /**
     * {@link #nestedOpensWithOuterAggregate} for some places of a select, settled by the given statistics. The body
     * of an ANY comparison is no value the select plans, nor is that of an IN filtering its rows, while a NOT IN's
     * and a select item's IN are: {@code g.v = ANY (SELECT MAX(o.i) FROM g g2)} and a WHERE's {@code g.v IN (…)}
     * are answered, a WHERE's {@code NOT IN} and an item's {@code 1 IN (…)} refused (live-verified).
     *
     * @param filter whether the places filter the select's rows rather than being its items
     */
    private boolean nestedOpensIn(final QueryExecutor queryExecutor, final CorrelatedSubqueryRule rule,
                                  final List<FrostlakeParser.SelectClauseContext> around,
                                  final List<ParserRuleContext> places, final ExpressionEvaluatorVisitor statistics,
                                  final ExpressionEvaluatorVisitor outer, final boolean filter) {
        for (final ParserRuleContext place : places) {
            final Expression parsedPlace;
            try {
                parsedPlace = ExpressionEvaluator.parse(ParseTreeText.getOriginalText(place));
            } catch (final RuntimeException unparsed) {
                continue;   // left to the evaluation, which reports it
            }
            final UnguardedSubqueryWalk walk = new UnguardedSubqueryWalk(statistics, outer, true, filter);
            parsedPlace.accept(walk);
            for (final SubqueryExpression nested : walk.subqueries()) {
                if (nested.isExistence()) {
                    continue;
                }
                final FrostlakeParser.SelectStatementContext statement =
                    queryExecutor.subqueryStatement(nested.getSubquery());
                if (statement == null) {
                    continue;
                }
                if (rule.opensWithOuterAggregate(around, statement)) {
                    return true;
                }
                final FrostlakeParser.SelectClauseContext inner = CorrelatedSubqueryRule.mainClause(statement);
                if (inner != null && nestedOpensWithOuterAggregate(queryExecutor, rule, around, inner)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether every catalog table the evaluated row is drawn from holds at most one row. Live evaluates a
     * correlated subquery row by row over such an outer relation instead of planning it as a join, so no
     * shape is refused there, and a second inner row surfaces as the single-row fault instead. A table
     * function beside the table does not count. A scalar subquery opening with the outer query's aggregate is refused
     * all the same (see {@link #opensWithOuterAggregate}).
     */
    private boolean outerHoldsOneRowAtMost(final QueryExecutor queryExecutor) {
        final List<Table> relations = new ArrayList<>();
        if (visitor.getMultiTableAllTables() != null && !visitor.getMultiTableAllTables().isEmpty()) {
            relations.addAll(visitor.getMultiTableAllTables());
        } else if (visitor.getTable() != null) {
            relations.add(visitor.getTable());
        }
        boolean sawCatalogTable = false;
        for (final Table relation : relations) {
            if (!relation.isCatalogResident()) {
                continue;
            }
            final long rows = queryExecutor.storedRowCount(relation.getQualifiedName());
            if (rows < 0 || rows > 1) {
                return false;
            }
            sawCatalogTable = true;
        }
        return sawCatalogTable;
    }

    /**
     * The outer names a correlated subquery may read, each bound to NULL: the scope its SHAPE is planned
     * in before any outer row exists. The keys are the ones {@link #buildLateralContext} binds per row.
     */
    Map<String, Object> outerNamesContext() {
        final Map<String, Object> context = new HashMap<>();
        final Map<String, Object> lateralContext = visitor.getLateralContext();
        if (lateralContext != null) {
            context.putAll(lateralContext);
        }
        final Table table = visitor.getTable();
        if (table != null) {
            for (final TableColumn column : table.getColumns()) {
                final String colName = column.getName();
                context.put(table.getName() + "." + colName, null);
                context.put(table.getName().toUpperCase(Locale.ROOT) + "." + colName.toUpperCase(Locale.ROOT), null);
                context.put(colName, null);
                context.put(colName.toUpperCase(Locale.ROOT), null);
            }
        }
        final Map<String, Table> aliasToTable = visitor.getMultiTableAliasToTable();
        if (aliasToTable != null) {
            for (final Map.Entry<String, Table> entry : aliasToTable.entrySet()) {
                for (final TableColumn column : entry.getValue().getColumns()) {
                    context.put((entry.getKey() + "." + column.getName()).toUpperCase(Locale.ROOT), null);
                    context.put(entry.getKey() + "." + column.getName(), null);
                }
            }
        }
        return context;
    }

    private Map<String, Object> buildLateralContext() {
        final Map<String, Object> context = new HashMap<>();

        final Map<String, Object> lateralContext = visitor.getLateralContext();
        if (lateralContext != null) {
            context.putAll(lateralContext);
        }
        final Map<String, Object> outerRow = visitor.getSubqueryOuterRow();
        if (outerRow != null) {
            context.putAll(outerRow);
        }
        addRowBindings(context);
        return context;
    }

    /** The names a subquery reads the visitor's row by: bare, relation-qualified and alias-qualified. */
    /** A join key's merged value over a joined row: the first of the key's copies that is not NULL. */
    private static Object mergedKeyValue(final List<TableColumn> columns, final Row row, final String key) {
        for (int i = 0; i < columns.size() && i < row.getValues().size(); i++) {
            if (columns.get(i).getName().equals(key) && row.getValue(i) != null) {
                return row.getValue(i);
            }
        }
        return null;
    }

    Map<String, Object> rowBindings() {
        final Map<String, Object> bindings = new HashMap<>();
        addRowBindings(bindings);
        return bindings;
    }

    private void addRowBindings(final Map<String, Object> context) {
        final Table table = visitor.getTable();
        final Row row = visitor.getRow();
        // The collated columns among them, recorded under the same keys, and the keys a row further out recorded
        // one under that this row shadows (see OuterNameBindings).
        final Map<String, TableColumn> collated = new HashMap<>();
        final Set<String> bound = OuterNameBindings.recordsCollations(context) ? new HashSet<String>() : null;
        if (table != null && row != null && !visitor.isScopeOpaqueToSubqueries()) {
            final List<TableColumn> columns = table.getColumns();
            // Every column under its EXACT spelling: live resolves "x" and X to two columns, and neither reaches
            // a column spelled the other way. This row's bindings replace any a wider scope already bound under the
            // same key. BARE names too — a correlated subquery may reference an outer column unqualified, notably
            // FLATTEN outputs (WHERE EXISTS (... WHERE k = VALUE:field)), which have no natural alias; the
            // subquery's own columns still win, since this context is consulted only after its own relations. A
            // bare name reads the FIRST column that carries it, the left relation's, and a name two relations of a
            // join carry is ambiguous.
            final Set<String> shared = sharedBareNames(table, visitor.getMultiTableAllTables());
            final Set<String> bare = new HashSet<>();
            // A relation its FROM clause aliased is known by the alias alone - live, the alias replaces the name - so
            // its name binds no qualified key: a subquery's own relation of that name answers for n.g, though the
            // query around it reads n under an alias (FROM n o). The alias keys are bound below.
            final Map<String, Table> fromKeys = visitor.getMultiTableAliasToTable();
            final boolean knownByName = fromKeys == null || fromKeys.isEmpty() || fromKeys.containsKey(table.getName())
                || !fromKeys.containsValue(table);
            for (int i = 0; i < columns.size(); i++) {
                final String colName = columns.get(i).getName();
                final Object value = row.getValue(i);
                if (knownByName) {
                    context.put(table.getName() + "." + colName, value);
                    noteCollation(collated, bound, table.getName() + "." + colName, columns.get(i));
                }
                if (bare.add(colName)) {
                    context.put(colName, shared.contains(colName) ? OuterNameBindings.AMBIGUOUS : value);
                    noteCollation(collated, bound, colName, collationCarrier(columns.get(i)));
                }
            }
            // A bare USING or NATURAL key reads the merged column, as it does beside the subquery: the first of its
            // copies that is not NULL, so a row only the right side holds reads its own key (live-verified).
            if (table.getJoinKeyNames() != null) {
                for (final String key : table.getJoinKeyNames()) {
                    if (bare.contains(key)) {
                        context.put(key, mergedKeyValue(columns, row, key));
                    }
                }
            }
            OuterNameBindings.recordColumns(context, bare);
        }

        // ALIAS-qualified keys: a correlated subquery references the outer row by its FROM alias
        // (WHERE t.id = s.id), which is not the table's name. Only TABLENAME.col keys were assembled, so the
        // alias-qualified lookup missed and the strip-qualifier fallback bound the reference to the INNER
        // table's same-named column — turning the correlation into t.id = t.id (always true): EXISTS matched
        // every outer row and NOT EXISTS none. Values are read positionally; the combined row lays the
        // tables out in allTables order, exactly as the executor's lateral join assembles it.
        final Map<String, Table> aliasToTable = visitor.getMultiTableAliasToTable();
        final List<Table> allTables = visitor.getMultiTableAllTables();
        if (aliasToTable != null && allTables != null && row != null) {
            for (final Map.Entry<String, Table> entry : aliasToTable.entrySet()) {
                int offset = 0;
                for (final Table t : allTables) {
                    if (t == entry.getValue()) {
                        break;
                    }
                    offset += t.getColumns().size();
                }
                final Table aliased = entry.getValue();
                for (int i = 0; i < aliased.getColumns().size() && offset + i < row.getValues().size(); i++) {
                    context.put(entry.getKey() + "." + aliased.getColumns().get(i).getName(),
                        row.getValue(offset + i));
                    noteCollation(collated, bound, entry.getKey() + "." + aliased.getColumns().get(i).getName(),
                        aliased.getColumns().get(i));
                }
            }
        }
        OuterNameBindings.recordAliasesBound(context,
            aliasToTable != null && !aliasToTable.isEmpty() && allTables != null && row != null);
        OuterNameBindings.recordCollations(context, collated, bound);
    }

    /**
     * The column a bare outer name reads its collation from: the column itself, else — where the row is a join's,
     * whose relation carries every side's column without its declaration — the first joined relation's that
     * carries the name, as the bare name reads that relation's value.
     */
    private TableColumn collationCarrier(final TableColumn column) {
        if (column.getCollation() != null) {
            return column;
        }
        final List<Table> joined = visitor.getMultiTableAllTables();
        if (joined != carriersOf) {
            carriersOf = joined;
            carriers = joined == null ? null : firstCarriers(joined);
        }
        final TableColumn carrier = carriers == null ? null : carriers.get(column.getName());
        return carrier != null ? carrier : column;
    }

    /** Each name's first carrier among joined relations, or null where none of their columns has a collation. */
    private static Map<String, TableColumn> firstCarriers(final List<Table> joined) {
        boolean collated = false;
        final Map<String, TableColumn> first = new HashMap<>();
        for (final Table relation : joined) {
            for (final TableColumn candidate : relation.getColumns()) {
                first.putIfAbsent(candidate.getName(), candidate);
                collated |= candidate.getCollation() != null;
            }
        }
        return collated ? first : null;
    }

    /** Note a key this row binds, and the column under it where the column carries a collation. */
    private static void noteCollation(final Map<String, TableColumn> collated, final Set<String> bound,
                                      final String key, final TableColumn column) {
        if (bound != null) {
            bound.add(key);
        }
        if (column.getCollation() != null) {
            collated.put(key, column);
        }
    }
}
