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

import dev.frostlake.executor.expressions.BetweenExpression;
import dev.frostlake.executor.expressions.BinaryOperationExpression;
import dev.frostlake.executor.expressions.BinaryOperator;
import dev.frostlake.executor.expressions.ColumnReferenceCollectWalk;
import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.InExpression;
import dev.frostlake.executor.expressions.IsNullExpression;
import dev.frostlake.executor.expressions.SubqueryExpression;
import dev.frostlake.executor.expressions.UnaryOperationExpression;
import dev.frostlake.executor.expressions.UnaryOperator;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.View;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Whether the account settles an INSERT's source empty while the statement compiles, before a row is read
 * (live-verified). A zero-row INSERT whose source it settles so writes a version of the target that adds and removes
 * no row; one whose source it reads anyway rewrites the target, a version adding and removing every row it holds.
 *
 * <p>The source is settled empty when it is one SELECT - no set operation, DISTINCT, grouping, HAVING, QUALIFY, ORDER
 * BY or LIMIT, and a select list reading a column - over one relation, joined to nothing, and that relation is pruned:
 * a catalog table that holds no row, or whose WHERE holds a conjunct the table's statistics rule out - a {@code FALSE}
 * or {@code NULL}, a constant that folds to either, a comparison of a column with a constant, a session variable or a
 * scalar subquery known while compiling (see {@link #settledBeforeRead}) whose value lies outside the column's least
 * and greatest value or is NULL, an equality of a whole-number column with a constant that has a fraction, an IN list
 * or an IN subquery none of whose values can match, an IS NULL over a column holding no NULL, a LIKE whose fixed
 * prefix lies outside the column's texts, a condition the intervals of exact-numeric expressions settle false, or two
 * equalities of one column with different values; or a derived relation, a common table expression or a view of that
 * shape, a condition over one that hands its table's columns on judged over the table. A source that reads no table
 * at all ({@code SELECT 1 WHERE FALSE}, a VALUES list, a generator), a join, an EXISTS, a volatile call and a value
 * inside the column's range are read.
 */
final class InsertSourcePruning {

    /** How deep derived relations, common table expressions and views are followed. */
    private static final int MAX_DEPTH = 16;

    private final QueryExecutor executor;
    private final Map<String, FrostlakeParser.SelectStatementContext> commons = new HashMap<>();

    private InsertSourcePruning(final QueryExecutor executor) {
        this.executor = executor;
    }

    /**
     * Whether a source the statement read no row from is settled empty before it is read.
     *
     * @param executor the executor
     * @param source   the INSERT's source query, or null where it has none
     * @return true where the account prunes it
     */
    static boolean settledEmpty(final QueryExecutor executor, final FrostlakeParser.SelectStatementContext source) {
        if (source == null) {
            return false;
        }
        try {
            return new InsertSourcePruning(executor).pruned(source, 0, new ArrayList<FrostlakeParser.BooleanExprContext>(),
                new ArrayList<Map<String, String>>(), new ArrayList<String>());
        } catch (final RuntimeException unjudged) {
            return false;
        }
    }

    /**
     * @param body      the query
     * @param depth     how deep it lies in the source
     * @param outer     the WHERE conjuncts of the queries reading it, where each passes its columns through
     * @param renames   for each of them, the column of this query each name it reads stands for, where it differs
     * @param outerName the names those queries read it by
     */
    private boolean pruned(final FrostlakeParser.SelectStatementContext body, final int depth,
                           final List<FrostlakeParser.BooleanExprContext> outer,
                           final List<Map<String, String>> renames, final List<String> outerName) {
        if (depth > MAX_DEPTH) {
            return false;
        }
        if (body.withClause() != null) {
            if (body.withClause().RECURSIVE() != null) {
                return false;
            }
            for (final FrostlakeParser.CteDefinitionContext common : body.withClause().cteDefinition()) {
                if (common.columnListOptional() == null) {
                    commons.put(SqlIdentifiers.canonicalText(common.nameStartPart().getText()),
                        common.selectStatement());
                }
            }
        }
        if (body.selectOperand().size() != 1 || body.orderByClause() != null || body.limitClause() != null
                || body.fetchClause() != null) {
            return false;
        }
        final FrostlakeParser.SelectOperandContext operand = body.selectOperand(0);
        if (operand.selectStatement() != null) {
            return pruned(operand.selectStatement(), depth + 1, outer, renames, outerName);
        }
        final FrostlakeParser.SelectClauseContext select = operand.selectClause();
        if (select == null || select.DISTINCT() != null || select.topClause() != null || select.intoClause() != null
                || select.connectByClause() != null || select.groupByClause() != null
                || select.havingClause() != null || select.qualifyClause() != null
                || select.tableExpression() == null || !readsColumn(select)) {
            return false;
        }
        final FrostlakeParser.TableExpressionContext from = select.tableExpression();
        if (from.tableReference().size() != 1 || !from.joinClause().isEmpty()) {
            return false;
        }
        final FrostlakeParser.TableReferenceContext reference = from.tableReference(0);
        final FrostlakeParser.TableSourceContext source = reference.tableSource();
        if (source == null || reference.LATERAL() != null || reference.identifierList() != null
                || reference.pivotClause() != null || reference.unpivotClause() != null
                || reference.matchRecognizeClause() != null || reference.sampleClause() != null) {
            return false;
        }
        final List<FrostlakeParser.BooleanExprContext> conjuncts = new ArrayList<>();
        final FrostlakeParser.WhereClauseContext where = ParseTreeText.getWhereClause(select);
        if (where != null) {
            DerivedRelationJudgement.collectConjuncts(where.booleanExpr(), conjuncts);
        }
        final Map<String, String> passed = passedThrough(select);
        final List<FrostlakeParser.BooleanExprContext> inherited = new ArrayList<>();
        final List<Map<String, String>> inheritedRenames = new ArrayList<>();
        final List<String> inheritedNames = new ArrayList<>();
        if (passed != null) {
            inherited.addAll(outer);
            for (final Map<String, String> rename : renames) {
                inheritedRenames.add(composed(rename, passed, starred(select)));
            }
            inheritedNames.addAll(outerName);
        }
        for (final FrostlakeParser.BooleanExprContext conjunct : conjuncts) {
            inherited.add(conjunct);
            inheritedRenames.add(new HashMap<String, String>());
        }
        final String referenceName = referenceName(reference);
        if (referenceName != null) {
            inheritedNames.add(referenceName);
        }
        if (source.selectStatement() != null) {
            return foldsFalse(conjuncts) || pruned(source.selectStatement(), depth + 1, inherited, inheritedRenames,
                inheritedNames);
        }
        if (source.tableQualifiedName() == null || source.timeTravelClause() != null) {
            return false;
        }
        final String written = SqlIdentifiers.canonicalText(source.tableQualifiedName().getText());
        final FrostlakeParser.SelectStatementContext common = commons.get(written);
        if (common != null) {
            return foldsFalse(conjuncts) || pruned(common, depth + 1, inherited, inheritedRenames,
                withName(inheritedNames, written));
        }
        final View view = view(written);
        if (view != null) {
            return view.getDefinition() != null && (view.getColumnNames() == null || view.getColumnNames().isEmpty())
                && (foldsFalse(conjuncts) || pruned(executor.subqueryStatement(view.getDefinition()), depth + 1,
                    inherited, inheritedRenames, withName(inheritedNames, written)));
        }
        final Table table = table(written);
        if (table == null || table.getQualifiedName() == null
                || !executor.getStorageEngine().hasTable(table.getQualifiedName())) {
            return false;
        }
        final List<Row> rows = executor.statisticsRowsFor(table.getQualifiedName());
        if (rows.isEmpty()) {
            return true;
        }
        final String[] parts = ParseTreeText.qualifiedNameParts(source.tableQualifiedName());
        final List<String> names = withName(inheritedNames, parts[parts.length - 1]);
        return ruledOut(inherited, inheritedRenames, table, names, rows);
    }

    /** The name a FROM item's alias gives it, or null. */
    private static String referenceName(final FrostlakeParser.TableReferenceContext reference) {
        if (reference.aliasName() != null) {
            return ParseTreeText.getIdentifier(reference.aliasName());
        }
        return reference.nonJoinKeywordIdentifier() != null
            ? SqlIdentifiers.canonicalText(reference.nonJoinKeywordIdentifier().getText()) : null;
    }

    private static List<String> withName(final List<String> names, final String name) {
        final List<String> all = new ArrayList<>(names);
        all.add(name);
        return all;
    }

    /**
     * The columns a select hands its relation's columns on as - each item a bare column, under its own name or an
     * alias, or a star - by the name it hands each on under, upper-cased; null where an item computes a value, so a
     * condition a query reading it writes is no condition over its relation.
     */
    private static Map<String, String> passedThrough(final FrostlakeParser.SelectClauseContext select) {
        final Map<String, String> passed = new HashMap<>();
        for (final FrostlakeParser.SelectItemContext item : select.selectList().selectItem()) {
            if (item instanceof FrostlakeParser.StarItemContext) {
                if (((FrostlakeParser.StarItemContext) item).starModifier().isEmpty()) {
                    continue;
                }
                return null;
            }
            if (!(item instanceof FrostlakeParser.ExprItemContext)) {
                return null;
            }
            final FrostlakeParser.ExprItemContext expression = (FrostlakeParser.ExprItemContext) item;
            final Expression parsed = ExpressionEvaluator.parse(ParseTreeText.getOriginalText(expression.booleanExpr()));
            if (!(parsed instanceof ColumnReferenceExpression)) {
                return null;
            }
            final String column = ((ColumnReferenceExpression) parsed).getColumnName().toUpperCase(Locale.ROOT);
            final String name = expression.aliasName() != null
                ? ParseTreeText.getIdentifier(expression.aliasName())
                : expression.identifier() != null ? SqlIdentifiers.canonicalText(expression.identifier().getText())
                    : column;
            passed.put(name.toUpperCase(Locale.ROOT), column);
        }
        return passed;
    }

    private static boolean starred(final FrostlakeParser.SelectClauseContext select) {
        for (final FrostlakeParser.SelectItemContext item : select.selectList().selectItem()) {
            if (item instanceof FrostlakeParser.StarItemContext) {
                return true;
            }
        }
        return false;
    }

    /**
     * What each name a query reading a select reads stands for below the select: the names it read stood for the
     * select's columns, which stand for its relation's.
     */
    private static Map<String, String> composed(final Map<String, String> above, final Map<String, String> passed,
                                                final boolean star) {
        final Map<String, String> below = new HashMap<>(passed);
        for (final Map.Entry<String, String> name : above.entrySet()) {
            if (passed.containsKey(name.getValue())) {
                below.put(name.getKey(), passed.get(name.getValue()));
            } else if (star) {
                below.put(name.getKey(), name.getValue());
            } else {
                below.put(name.getKey(), "");
            }
        }
        return below;
    }

    /** Whether a select's list reads a column of its relation: a star, or an item naming one. */
    private static boolean readsColumn(final FrostlakeParser.SelectClauseContext select) {
        for (final FrostlakeParser.SelectItemContext item : select.selectList().selectItem()) {
            if (!(item instanceof FrostlakeParser.ExprItemContext)) {
                return true;
            }
            final Expression parsed = ExpressionEvaluator.parse(
                ParseTreeText.getOriginalText(((FrostlakeParser.ExprItemContext) item).booleanExpr()));
            final ColumnReferenceCollectWalk walk = new ColumnReferenceCollectWalk();
            parsed.accept(walk);
            if (!walk.references().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Whether a constant conjunct folds to FALSE or NULL. */
    private boolean foldsFalse(final List<FrostlakeParser.BooleanExprContext> conjuncts) {
        for (final FrostlakeParser.BooleanExprContext conjunct : conjuncts) {
            final Expression parsed = ExpressionEvaluator.parse(ParseTreeText.getOriginalText(conjunct));
            if (ConstantHaving.isConstant(parsed, executor.getFunctionRegistry(), true)) {
                final Object value = constant(parsed);
                if (value == null || Boolean.FALSE.equals(value)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether a WHERE's conjuncts rule out every row of a table by its statistics. */
    private boolean ruledOut(final List<FrostlakeParser.BooleanExprContext> conjuncts,
                             final List<Map<String, String>> renames, final Table table, final List<String> alias,
                             final List<Row> rows) {
        if (foldsFalse(conjuncts)) {
            return true;
        }
        final Map<String, Table> scope = new LinkedHashMap<>();
        for (final String name : alias) {
            scope.put(name, table);
        }
        final CorrelatedFilterFold fold = new CorrelatedFilterFold(executor, scope);
        // The value each column is compared equal to, where a conjunct pins one.
        final Map<Integer, Object> pinned = new HashMap<>();
        for (int c = 0; c < conjuncts.size(); c++) {
            final String text = ParseTreeText.getOriginalText(conjuncts.get(c));
            final Map<String, String> rename = renames.get(c);
            if (rename.isEmpty() && Boolean.FALSE.equals(fold.settles(text))) {
                return true;
            }
            final Expression parsed = ExpressionEvaluator.parse(text);
            final ColumnNames names = new ColumnNames(table, alias, rename);
            if (ruledOut(parsed, names, rows)) {
                return true;
            }
            if (parsed instanceof BinaryOperationExpression
                    && ((BinaryOperationExpression) parsed).getOperator() == BinaryOperator.EQUAL) {
                final BinaryOperationExpression equality = (BinaryOperationExpression) parsed;
                final int column = names.of(equality.getLeft());
                final Expression other = column >= 0 ? equality.getRight() : equality.getLeft();
                final int pinnedColumn = column >= 0 ? column : names.of(equality.getRight());
                if (pinnedColumn >= 0) {
                    final Object[] value = value(other);
                    if (value != null && value[0] != null) {
                        if (pinned.containsKey(pinnedColumn)
                                && comparable(pinned.get(pinnedColumn), value[0])
                                && ValueComparisons.compareValues(pinned.get(pinnedColumn), value[0]) != 0) {
                            return true;
                        }
                        pinned.put(pinnedColumn, value[0]);
                    }
                }
            }
        }
        return false;
    }

    /** Whether one conjunct rules out every row of the table by the least and greatest value of a column. */
    private boolean ruledOut(final Expression conjunct, final ColumnNames names, final List<Row> rows) {
        if (conjunct instanceof IsNullExpression) {
            final IsNullExpression test = (IsNullExpression) conjunct;
            final int column = names.of(test.getOperand());
            return column >= 0 && !test.isNot() && new ColumnExtremes(rows, column).nulls == 0;
        }
        if (conjunct instanceof BetweenExpression) {
            final BetweenExpression between = (BetweenExpression) conjunct;
            final int column = names.of(between.getValue());
            if (column < 0 || between.isNot()) {
                return false;
            }
            final ColumnExtremes extremes = new ColumnExtremes(rows, column);
            return outside(extremes, between.getLower(), BinaryOperator.GREATER_THAN_OR_EQUAL)
                || outside(extremes, between.getUpper(), BinaryOperator.LESS_THAN_OR_EQUAL);
        }
        if (conjunct instanceof InExpression) {
            final InExpression in = (InExpression) conjunct;
            final int column = names.of(in.getValue());
            if (column < 0 || in.isNot()) {
                return false;
            }
            final ColumnExtremes extremes = new ColumnExtremes(rows, column);
            final List<Object> candidates = new ArrayList<>();
            if (in.getSubquery() != null) {
                final List<Object> read = subqueryValues(in.getSubquery());
                if (read == null) {
                    return false;
                }
                candidates.addAll(read);
            } else if (in.getValues() != null) {
                for (final Expression candidate : in.getValues()) {
                    final Object[] value = value(candidate);
                    if (value == null) {
                        return false;
                    }
                    candidates.add(value[0]);
                }
            } else {
                return false;
            }
            for (final Object candidate : candidates) {
                if (!extremes.rulesOut(candidate, BinaryOperator.EQUAL)) {
                    return false;
                }
            }
            return true;
        }
        if (!(conjunct instanceof BinaryOperationExpression)) {
            return false;
        }
        final BinaryOperationExpression comparison = (BinaryOperationExpression) conjunct;
        final BinaryOperator operator = comparison.getOperator();
        if (operator == BinaryOperator.LIKE) {
            final int column = names.of(comparison.getLeft());
            final Object[] pattern = comparison.getEscape() == null ? value(comparison.getRight()) : null;
            return column >= 0 && pattern != null && (pattern[0] == null
                || pattern[0] instanceof String && new ColumnExtremes(rows, column).rulesOutLike((String) pattern[0]));
        }
        if (operator != BinaryOperator.EQUAL && operator != BinaryOperator.LESS_THAN
                && operator != BinaryOperator.LESS_THAN_OR_EQUAL && operator != BinaryOperator.GREATER_THAN
                && operator != BinaryOperator.GREATER_THAN_OR_EQUAL) {
            return false;
        }
        final int left = names.of(comparison.getLeft());
        if (left >= 0) {
            return outside(new ColumnExtremes(rows, left), comparison.getRight(), operator)
                || operator == BinaryOperator.EQUAL && fractionOfWhole(names.table(), left, comparison.getRight());
        }
        final int right = names.of(comparison.getRight());
        return right >= 0 && (outside(new ColumnExtremes(rows, right), comparison.getLeft(), mirrored(operator))
            || operator == BinaryOperator.EQUAL && fractionOfWhole(names.table(), right, comparison.getLeft()));
    }

    /** Whether an equality compares a column of whole numbers with a value that has a fraction, which none equals. */
    private boolean fractionOfWhole(final Table table, final int column, final Expression operand) {
        final DataType type = table.getColumns().get(column).getDataType();
        if (!(type instanceof NumericType) || ((NumericType) type).getScale() != 0
                || !ConstantHaving.isConstant(operand, executor.getFunctionRegistry(), true)) {
            return false;
        }
        final Object[] value = value(operand);
        return value != null && value[0] instanceof BigDecimal
            && ((BigDecimal) value[0]).stripTrailingZeros().scale() > 0;
    }

    /** Whether {@code column operator operand} holds on no row, the operand's value known before a row is read. */
    private boolean outside(final ColumnExtremes extremes, final Expression operand, final BinaryOperator operator) {
        final Object[] value = value(operand);
        return value != null && extremes.rulesOut(value[0], operator);
    }

    private static BinaryOperator mirrored(final BinaryOperator operator) {
        switch (operator) {
            case LESS_THAN:
                return BinaryOperator.GREATER_THAN;
            case LESS_THAN_OR_EQUAL:
                return BinaryOperator.GREATER_THAN_OR_EQUAL;
            case GREATER_THAN:
                return BinaryOperator.LESS_THAN;
            case GREATER_THAN_OR_EQUAL:
                return BinaryOperator.LESS_THAN_OR_EQUAL;
            default:
                return operator;
        }
    }

    /**
     * The value an operand holds before a row is read - a constant, a session variable or an uncorrelated scalar
     * subquery - in a one-element array, or null where it is no such operand.
     */
    private Object[] value(final Expression operand) {
        if (operand instanceof SubqueryExpression) {
            final SubqueryExpression subquery = (SubqueryExpression) operand;
            if (subquery.isExistence() || subquery.isLateral() || !settledBeforeRead(subquery)) {
                return null;
            }
            final List<Object> read = subqueryValues(subquery);
            if (read == null || read.size() > 1) {
                return null;
            }
            return new Object[] {read.isEmpty() ? null : read.get(0)};
        }
        if (!ConstantHaving.isConstant(operand, executor.getFunctionRegistry(), true) && !runsAlone(operand)) {
            return null;
        }
        try {
            return new Object[] {constant(operand)};
        } catch (final RuntimeException notConstant) {
            return null;
        }
    }

    /**
     * Whether an operand is arithmetic over constants and scalar subqueries that each run alone, such as
     * {@code (SELECT MAX(a) FROM t) + 1}.
     */
    private boolean runsAlone(final Expression operand) {
        if (operand instanceof SubqueryExpression) {
            final SubqueryExpression subquery = (SubqueryExpression) operand;
            final List<Object> read = subquery.isExistence() || subquery.isLateral() || !settledBeforeRead(subquery)
                ? null : subqueryValues(subquery);
            return read != null && read.size() <= 1;
        }
        if (operand instanceof BinaryOperationExpression) {
            final BinaryOperationExpression arithmetic = (BinaryOperationExpression) operand;
            final BinaryOperator operator = arithmetic.getOperator();
            return arithmetic.getEscape() == null && (operator == BinaryOperator.ADD
                    || operator == BinaryOperator.SUBTRACT || operator == BinaryOperator.MULTIPLY
                    || operator == BinaryOperator.DIVIDE)
                && runsAlone(arithmetic.getLeft()) && runsAlone(arithmetic.getRight());
        }
        if (operand instanceof UnaryOperationExpression) {
            return ((UnaryOperationExpression) operand).getOperator() == UnaryOperator.NEGATE
                && runsAlone(((UnaryOperationExpression) operand).getOperand());
        }
        return ConstantHaving.isConstant(operand, executor.getFunctionRegistry(), true);
    }

    /**
     * Whether a scalar subquery's value is known while the statement compiles: it is itself settled empty, it reads
     * no relation and computes from constants, or it aggregates one table by MIN, MAX and COUNT, whatever its WHERE
     * keeps, and so answers one row. Any other subquery is read with the statement, so a comparison with it prunes
     * nothing.
     */
    private boolean settledBeforeRead(final SubqueryExpression subquery) {
        final FrostlakeParser.SelectStatementContext body = executor.subqueryStatement(subquery.getSubquery());
        if (new InsertSourcePruning(executor).pruned(body, 0, new ArrayList<FrostlakeParser.BooleanExprContext>(),
                new ArrayList<Map<String, String>>(), new ArrayList<String>())) {
            return true;
        }
        if (body.withClause() != null || body.selectOperand().size() != 1 || body.orderByClause() != null
                || body.limitClause() != null || body.fetchClause() != null
                || body.selectOperand(0).selectClause() == null) {
            return false;
        }
        final FrostlakeParser.SelectClauseContext select = body.selectOperand(0).selectClause();
        if (select.DISTINCT() != null || select.topClause() != null || select.connectByClause() != null
                || select.groupByClause() != null || select.havingClause() != null || select.qualifyClause() != null) {
            return false;
        }
        final boolean fromless = select.tableExpression() == null;
        if (!fromless && !oneTable(select)) {
            return false;
        }
        for (final FrostlakeParser.SelectItemContext item : select.selectList().selectItem()) {
            if (!(item instanceof FrostlakeParser.ExprItemContext)) {
                return false;
            }
            final Expression parsed = ExpressionEvaluator.parse(
                ParseTreeText.getOriginalText(((FrostlakeParser.ExprItemContext) item).booleanExpr()));
            if (fromless ? !ConstantHaving.isConstant(parsed, executor.getFunctionRegistry(), true)
                    : !answeredByStatistics(parsed)) {
                return false;
            }
        }
        return true;
    }

    /** Whether a select reads one catalog table, joined to nothing. */
    private boolean oneTable(final FrostlakeParser.SelectClauseContext select) {
        final FrostlakeParser.TableExpressionContext from = select.tableExpression();
        if (from.tableReference().size() != 1 || !from.joinClause().isEmpty()) {
            return false;
        }
        final FrostlakeParser.TableSourceContext source = from.tableReference(0).tableSource();
        if (source == null || source.tableQualifiedName() == null || source.timeTravelClause() != null) {
            return false;
        }
        final String written = SqlIdentifiers.canonicalText(source.tableQualifiedName().getText());
        return !commons.containsKey(written) && table(written) != null;
    }

    /** Whether an item is MIN, MAX and COUNT calls under arithmetic and constants, which statistics answer. */
    private boolean answeredByStatistics(final Expression item) {
        if (item instanceof FunctionCallExpression) {
            final FunctionCallExpression call = (FunctionCallExpression) item;
            final String name = call.getFunctionName() == null ? "" : call.getFunctionName().toUpperCase(Locale.ROOT);
            return !call.isDistinct() && ("MIN".equals(name) || "MAX".equals(name) || "COUNT".equals(name));
        }
        if (item instanceof BinaryOperationExpression) {
            final BinaryOperationExpression arithmetic = (BinaryOperationExpression) item;
            final BinaryOperator operator = arithmetic.getOperator();
            return (operator == BinaryOperator.ADD || operator == BinaryOperator.SUBTRACT
                    || operator == BinaryOperator.MULTIPLY) && answeredByStatistics(arithmetic.getLeft())
                && answeredByStatistics(arithmetic.getRight());
        }
        if (item instanceof UnaryOperationExpression) {
            return ((UnaryOperationExpression) item).getOperator() == UnaryOperator.NEGATE
                && answeredByStatistics(((UnaryOperationExpression) item).getOperand());
        }
        return ConstantHaving.isConstant(item, executor.getFunctionRegistry(), true);
    }

    private Object constant(final Expression expression) {
        return new ExpressionEvaluator(null, executor.getFunctionRegistry(), executor.getCatalog(), executor)
            .evaluate(expression, null);
    }

    /** The first column of an uncorrelated subquery's rows, or null where it does not run alone. */
    private List<Object> subqueryValues(final SubqueryExpression subquery) {
        try {
            final ResultSet result = executor.executeSelectFromContext(
                executor.subqueryStatement(subquery.getSubquery()));
            if (result.getColumns().size() != 1) {
                return null;
            }
            final List<Object> values = new ArrayList<>();
            for (final Row row : result.getRows()) {
                values.add(row.getValue(0));
            }
            return values;
        } catch (final RuntimeException correlated) {
            return null;
        }
    }

    /** Whether two values are ordered alike by their statistics: two numbers or two texts. */
    static boolean comparable(final Object left, final Object right) {
        return left instanceof Number && right instanceof Number && !(left instanceof Double)
                && !(right instanceof Double) && !(left instanceof Float) && !(right instanceof Float)
            || left instanceof String && right instanceof String
            || left instanceof BigDecimal && right instanceof BigDecimal
            || left instanceof Comparable && left.getClass() == right.getClass() && !(left instanceof Double)
                && !(left instanceof Float) && !(left instanceof Boolean);
    }

    private View view(final String written) {
        try {
            return executor.getCatalog().resolveView(written);
        } catch (final RuntimeException notAView) {
            return null;
        }
    }

    private Table table(final String written) {
        try {
            final Table table = executor.getCatalog().resolveTable(written);
            return table != null && table.isCatalogResident() ? table : null;
        } catch (final RuntimeException notATable) {
            return null;
        }
    }
}
