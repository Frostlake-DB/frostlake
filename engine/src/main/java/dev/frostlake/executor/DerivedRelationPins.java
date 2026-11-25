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
import dev.frostlake.metastore.model.View;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.DecimalText;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Whether the statistics pin a column of a derived relation - a subquery's or a CTE's - to one value on every row by
 * what the query making it computes, which the account reads a GENERATOR bound over it as (all live-verified). Over
 * {@code g1} holding 1, {@code g2} holding 2 and 3 and {@code g3} holding 5 twice:
 *
 * <ul>
 *   <li>an expression over pinned columns and literals is pinned: {@code (SELECT n + 1 AS n FROM g1) d} bounds
 *       {@code ROWCOUNT => d.n} at 2, {@code n * 2} and {@code ABS(n)} alike, {@code n + 1} over g3 bounds each of its
 *       two rows at 6, and a FROM-less select's items are pinned, {@code (SELECT 2 AS n, 3 AS m) d};</li>
 *   <li>a select of MAX, MIN or COUNT over a whole table is one row the statistics answer, {@code MAX(n)} of g2 being
 *       3 and {@code COUNT(*) + 1} of g3 being 3, where SUM, AVG, a WHERE beside the aggregate or a GROUP BY making
 *       several groups leaves the column varying;</li>
 *   <li>DISTINCT and GROUP BY keep a pinned column pinned, {@code SELECT DISTINCT n FROM g3} being 5;</li>
 *   <li>a UNION, with or without ALL, of branches pinned to one value is pinned to it, {@code 4 UNION ALL 4} bounding
 *       each of its two rows at 4 and {@code n FROM g1 UNION ALL n FROM g1} each of its two at 1, where
 *       {@code 4 UNION ALL 5} varies;</li>
 *   <li>a CTE is read as its body, a CTE over another CTE as that one's.</li>
 * </ul>
 *
 * <p>A call the account does not fold while it judges a bound, {@code LENGTH('ab')}, leaves the column varying.
 *
 * <p>Read for the outer names of a MERGE's correlated subqueries (see {@link #forOuterNames}) the account pins more:
 * a NULL is one value, so {@code SELECT NULL UNION ALL SELECT NULL} is pinned where {@code NULL UNION ALL 2} varies; a
 * call it does not fold is pinned to a value it does not know, {@code SQRT(4)} alone but not beside a 2 in a UNION;
 * a FROM-less select keeps its items pinned under a WHERE; an EXCEPT, MINUS or INTERSECT keeps its first branch's
 * value; a UNION BY NAME pairs its branches by name; GROUP BY ALL over aggregates alone is one row, and so is an
 * aggregate over a table whose WHERE the statistics show holding on every row; a constant item is pinned whatever
 * its FROM and grouping; a view is read
 * as its body; and a join reads
 * each item it does not make NULL as that item, {@code a.n} of {@code g1 a LEFT JOIN g2 b} being pinned where
 * {@code b.n} of {@code g2 a LEFT JOIN g1 b} is not. RANDOM, the SEQ functions and UUID_STRING vary in either
 * reading.
 */
final class DerivedRelationPins {

    /** How many relations deep a column is followed. */
    private static final int MAX_DEPTH = 16;

    /** The answer for a column the statistics do not pin. */
    private static final Object VARYING = new Object();

    /** The answer for a pinned column whose one value is not worked out here. */
    private static final Object UNKNOWN = new Object();

    /** The answer for a column pinned to NULL, read for outer names alone. */
    private static final Object NULL_VALUE = new Object();

    /** The aggregates whose answer over a whole table the statistics hold. */
    private static final Set<String> ANSWERED = Set.of("MAX", "MIN", "COUNT");

    private final QueryExecutor executor;
    private final StoredColumnValues values;
    /** Whether the columns are read as a correlated subquery's outer names are, not as a bound. */
    private final boolean outerNames;

    /**
     * @param executor the executor whose catalog and storage the relations are read from
     * @param values   the stored columns' summaries
     */
    DerivedRelationPins(final QueryExecutor executor, final StoredColumnValues values) {
        this(executor, values, false);
    }

    private DerivedRelationPins(final QueryExecutor executor, final StoredColumnValues values,
                                final boolean outerNames) {
        this.executor = executor;
        this.values = values;
        this.outerNames = outerNames;
    }

    /**
     * The pins a correlated subquery's outer names read, as the class comment lists them.
     *
     * @param executor the executor whose catalog and storage the relations are read from
     * @return the reading
     */
    static DerivedRelationPins forOuterNames(final QueryExecutor executor) {
        return new DerivedRelationPins(executor, new StoredColumnValues(executor), true);
    }

    /**
     * Whether a query's column, by its name, holds one value on every row.
     *
     * @param query  the query
     * @param column the column's name
     * @return true where the statistics pin it
     */
    boolean pinsColumn(final FrostlakeParser.SelectStatementContext query, final String column) {
        return queryValue(query, column, 0) != VARYING;
    }

    /**
     * Whether a column of a query whose columns a list renames holds one value on every row. The name is read as the
     * query's own column of that name where it has one, and as the column at the place the list gives it otherwise:
     * over {@code (SELECT 2 sid, n o FROM g) s (o, sid)}, {@code s.sid} reads the 2 (live-verified).
     *
     * @param query  the query
     * @param column the column's name as the list gives it
     * @param index  the column's place in the list, from 0
     * @return true where the statistics pin it
     */
    boolean pinsRenamedColumn(final FrostlakeParser.SelectStatementContext query, final String column,
                              final int index) {
        final FrostlakeParser.SelectClauseContext first = query.selectOperand(0).selectClause();
        if (first != null && labelIndex(first, column) >= 0) {
            return pinsColumn(query, column);
        }
        return queryValueAt(query, index, 0) != VARYING;
    }

    /**
     * Whether a derived relation's column holds one value on every row.
     *
     * @param node      a node of the query whose FROM holds the relation
     * @param qualifier the name the relation answers to, upper-cased
     * @param column    the column's name
     * @return true where the statistics pin it
     */
    boolean pins(final ParseTree node, final String qualifier, final String column) {
        final FrostlakeParser.TableReferenceContext item = fromItem(node, qualifier);
        return item != null && sourceValue(item, column, 0) != VARYING;
    }

    /**
     * The FROM item a name answers to, in the nearest FROM around a node that holds one so named.
     *
     * @param node      the node
     * @param qualifier the name, upper-cased
     * @return the item, or null
     */
    static FrostlakeParser.TableReferenceContext fromItem(final ParseTree node, final String qualifier) {
        for (ParseTree at = node; at != null; at = at.getParent()) {
            if (!(at instanceof FrostlakeParser.TableExpressionContext)) {
                continue;
            }
            final FrostlakeParser.TableExpressionContext from = (FrostlakeParser.TableExpressionContext) at;
            for (final FrostlakeParser.TableReferenceContext item : fromItems(from)) {
                if (qualifier.equals(GeneratorBounds.qualifierOf(item))) {
                    return item;
                }
            }
        }
        return null;
    }

    /**
     * The items of a FROM in the order written, those a join adds included.
     *
     * @param from the FROM
     * @return the items
     */
    static List<FrostlakeParser.TableReferenceContext> fromItems(final FrostlakeParser.TableExpressionContext from) {
        final List<FrostlakeParser.TableReferenceContext> items = new ArrayList<>();
        for (int i = 0; i < from.getChildCount(); i++) {
            final ParseTree child = from.getChild(i);
            if (child instanceof FrostlakeParser.TableReferenceContext) {
                items.add((FrostlakeParser.TableReferenceContext) child);
            } else if (child instanceof FrostlakeParser.JoinClauseContext
                    && ((FrostlakeParser.JoinClauseContext) child).tableReference() != null) {
                items.add(((FrostlakeParser.JoinClauseContext) child).tableReference());
            }
        }
        return items;
    }

    /**
     * The CTE a one-part name reads where it stands: the nearest WITH around it defining one so named.
     *
     * @param node the node the name is read at
     * @param name the name
     * @return the CTE, or null
     */
    static FrostlakeParser.CteDefinitionContext cteNamed(final ParseTree node, final String name) {
        for (ParseTree at = node; at != null; at = at.getParent()) {
            if (!(at instanceof FrostlakeParser.SelectStatementContext)
                    || ((FrostlakeParser.SelectStatementContext) at).withClause() == null) {
                continue;
            }
            for (final FrostlakeParser.CteDefinitionContext cte
                    : ((FrostlakeParser.SelectStatementContext) at).withClause().cteDefinition()) {
                if (name.equalsIgnoreCase(ParseTreeText.namePartText(cte.nameStartPart()))) {
                    return cte;
                }
            }
        }
        return null;
    }

    /** The one value a FROM item's column holds, UNKNOWN where it is not worked out, or VARYING. */
    private Object sourceValue(final FrostlakeParser.TableReferenceContext item, final String column, final int depth) {
        final FrostlakeParser.TableSourceContext source = item.tableSource();
        if (depth > MAX_DEPTH || source == null || item.pivotClause() != null || item.unpivotClause() != null
                || item.sampleClause() != null || item.matchRecognizeClause() != null) {
            return VARYING;
        }
        if (source.selectStatement() != null) {
            return queryValue(source.selectStatement(), column, depth + 1);
        }
        if (source.tableQualifiedName() == null || source.timeTravelClause() != null) {
            return VARYING;
        }
        final String[] parts = ParseTreeText.qualifiedNameParts(source.tableQualifiedName());
        final FrostlakeParser.CteDefinitionContext cte = parts.length == 1 ? cteNamed(item, parts[0]) : null;
        if (cte != null) {
            return cteValue(cte, column, depth + 1);
        }
        final FrostlakeParser.SelectStatementContext view = outerNames
            ? viewBody(ParseTreeText.getQualifiedName(source.tableQualifiedName())) : null;
        if (view != null) {
            return queryValue(view, column, depth + 1);
        }
        final Table table = catalogTable(ParseTreeText.getQualifiedName(source.tableQualifiedName()));
        if (table == null || !table.hasColumn(column)) {
            return VARYING;
        }
        final String declared = table.getColumn(column).getName();
        if (values.holdsOneValue(table, declared)) {
            final Object one = values.oneValueOf(table, declared);
            return one == null ? UNKNOWN : one;
        }
        if (values.holdsOnlyNulls(table, declared)) {
            return outerNames ? NULL_VALUE : UNKNOWN;
        }
        return VARYING;
    }

    /**
     * The body of the view a name reads, where its columns are its body's own.
     *
     * @param written the name as written
     * @return the body, or null where the name reads no such view
     */
    FrostlakeParser.SelectStatementContext viewBody(final String written) {
        try {
            final View view = executor.getCatalog().resolveView(written);
            if (view == null || view.getDefinition() == null
                    || view.getColumnNames() != null && !view.getColumnNames().isEmpty()) {
                return null;
            }
            return executor.subqueryStatement(view.getDefinition());
        } catch (final RuntimeException notAView) {
            return null;
        }
    }

    private Table catalogTable(final String written) {
        try {
            final Table table = executor.getCatalog().resolveTable(written);
            return table != null && table.isCatalogResident() ? table : null;
        } catch (final RuntimeException noTable) {
            return null;
        }
    }

    private Object cteValue(final FrostlakeParser.CteDefinitionContext cte, final String column, final int depth) {
        if (cte.columnListOptional() == null) {
            return queryValue(cte.selectStatement(), column, depth);
        }
        final List<FrostlakeParser.NamePartContext> names = cte.columnListOptional().namePart();
        for (int i = 0; i < names.size(); i++) {
            if (column.equalsIgnoreCase(ParseTreeText.namePartText(names.get(i)))) {
                return queryValueAt(cte.selectStatement(), i, depth);
            }
        }
        return VARYING;
    }

    /** A query's column by its name: the item so named, or the column a lone star passes through. */
    private Object queryValue(final FrostlakeParser.SelectStatementContext query, final String column,
                              final int depth) {
        final FrostlakeParser.SelectOperandContext first = query.selectOperand(0);
        if (first.selectStatement() != null && query.selectOperand().size() == 1) {
            return queryValue(first.selectStatement(), column, depth + 1);
        }
        if (outerNames && byName(query)) {
            return byNameValue(query, column, depth);
        }
        final FrostlakeParser.SelectClauseContext select = first.selectClause();
        if (select == null) {
            return VARYING;
        }
        final List<FrostlakeParser.SelectItemContext> items = select.selectList().selectItem();
        if (items.size() == 1 && items.get(0) instanceof FrostlakeParser.StarItemContext
                && ((FrostlakeParser.StarItemContext) items.get(0)).starModifier().isEmpty()
                && query.selectOperand().size() == 1 && singleSource(select) != null && plainSelect(select)
                && select.groupByClause() == null && select.DISTINCT() == null) {
            return sourceValue(singleSource(select), column, depth + 1);
        }
        for (int i = 0; i < items.size(); i++) {
            if (!(items.get(i) instanceof FrostlakeParser.ExprItemContext)) {
                return VARYING;
            }
            if (column.equalsIgnoreCase(labelOf((FrostlakeParser.ExprItemContext) items.get(i)))) {
                return queryValueAt(query, i, depth);
            }
        }
        return VARYING;
    }

    /** Whether every set operator of a query is a UNION BY NAME. */
    private static boolean byName(final FrostlakeParser.SelectStatementContext query) {
        if (query.setOperator().isEmpty()) {
            return false;
        }
        for (final FrostlakeParser.SetOperatorContext operator : query.setOperator()) {
            if (operator.UNION() == null || operator.BY() == null) {
                return false;
            }
        }
        return true;
    }

    /** A UNION BY NAME's column: one value where every branch's column of that name holds the same one. */
    private Object byNameValue(final FrostlakeParser.SelectStatementContext query, final String column,
                               final int depth) {
        Object common = VARYING;
        boolean first = true;
        for (final FrostlakeParser.SelectOperandContext operand : query.selectOperand()) {
            final Object value;
            if (operand.selectStatement() != null) {
                value = queryValue(operand.selectStatement(), column, depth + 1);
            } else if (operand.selectClause() == null) {
                return VARYING;
            } else {
                final int index = labelIndex(operand.selectClause(), column);
                value = index < 0 ? VARYING : operandValueAt(operand, index, depth + 1);
            }
            if (value == VARYING || value == UNKNOWN || !first && !same(common, value)) {
                return VARYING;
            }
            common = value;
            first = false;
        }
        return common;
    }

    /** The place of a select's item of a name, or -1 where it has none or a star stands among its items. */
    private static int labelIndex(final FrostlakeParser.SelectClauseContext select, final String column) {
        final List<FrostlakeParser.SelectItemContext> items = select.selectList().selectItem();
        int found = -1;
        for (int i = 0; i < items.size(); i++) {
            if (!(items.get(i) instanceof FrostlakeParser.ExprItemContext)) {
                return -1;
            }
            if (column.equalsIgnoreCase(labelOf((FrostlakeParser.ExprItemContext) items.get(i)))) {
                if (found >= 0) {
                    return -1;
                }
                found = i;
            }
        }
        return found;
    }

    /** A query's column by its place: one value where every branch of a UNION holds the same one. */
    private Object queryValueAt(final FrostlakeParser.SelectStatementContext query, final int index, final int depth) {
        final List<FrostlakeParser.SelectOperandContext> operands = query.selectOperand();
        if (operands.size() == 1) {
            return operandValueAt(operands.get(0), index, depth);
        }
        if (outerNames && subtractsOnly(query)) {
            // Every row of an EXCEPT, a MINUS or an INTERSECT is a row of its first branch.
            return operandValueAt(operands.get(0), index, depth);
        }
        for (final FrostlakeParser.SetOperatorContext operator : query.setOperator()) {
            if (operator.UNION() == null || operator.BY() != null) {
                return VARYING;
            }
        }
        Object common = VARYING;
        for (int i = 0; i < operands.size(); i++) {
            final Object value = operandValueAt(operands.get(i), index, depth);
            if (value == VARYING || value == UNKNOWN || i > 0 && !same(common, value)) {
                return VARYING;
            }
            common = value;
        }
        return common;
    }

    /** Whether every set operator of a query is an EXCEPT, a MINUS or an INTERSECT. */
    private static boolean subtractsOnly(final FrostlakeParser.SelectStatementContext query) {
        for (final FrostlakeParser.SetOperatorContext operator : query.setOperator()) {
            if (operator.UNION() != null) {
                return false;
            }
        }
        return true;
    }

    private Object operandValueAt(final FrostlakeParser.SelectOperandContext operand, final int index,
                                  final int depth) {
        if (depth > MAX_DEPTH) {
            return VARYING;
        }
        if (operand.selectStatement() != null) {
            return queryValueAt(operand.selectStatement(), index, depth + 1);
        }
        final FrostlakeParser.SelectClauseContext select = operand.selectClause();
        if (select == null || !plainSelect(select)) {
            return VARYING;
        }
        final List<FrostlakeParser.SelectItemContext> items = select.selectList().selectItem();
        for (final FrostlakeParser.SelectItemContext item : items) {
            if (!(item instanceof FrostlakeParser.ExprItemContext)) {
                return VARYING;
            }
        }
        if (index < 0 || index >= items.size()) {
            return VARYING;
        }
        final ParserRuleContext expression = ((FrostlakeParser.ExprItemContext) items.get(index)).booleanExpr();
        if (select.tableExpression() == null) {
            return !outerNames && ParseTreeText.getWhereClause(select) != null || select.groupByClause() != null
                ? VARYING : constantValue(expression);
        }
        final FrostlakeParser.TableReferenceContext source = singleSource(select);
        if (source == null) {
            return outerNames && !holdsAggregate(select.selectList()) && select.groupByClause() == null
                ? joinedValue(expression, select.tableExpression(), depth) : VARYING;
        }
        if (select.groupByClause() != null && !(outerNames && aggregatesAlone(select))) {
            return groupedValue(select, expression, source, depth);
        }
        if (holdsAggregate(select.selectList())) {
            // One row the statistics answer: an aggregate over the whole table, no WHERE beside it.
            return keepsEveryRow(select, source) && answeredAggregates(expression, source) ? UNKNOWN : VARYING;
        }
        return projectedValue(expression, source, depth);
    }

    /** Whether a select stands without TOP, HAVING, QUALIFY or a hierarchy, which the rules here do not read. */
    private static boolean plainSelect(final FrostlakeParser.SelectClauseContext select) {
        return select.topClause() == null && select.havingClause() == null && select.qualifyClause() == null
            && select.connectByClause() == null && select.intoClause() == null;
    }

    /** The FROM's one item where it holds one and no join, or null. */
    private static FrostlakeParser.TableReferenceContext singleSource(
            final FrostlakeParser.SelectClauseContext select) {
        final FrostlakeParser.TableExpressionContext from = select.tableExpression();
        return from == null || from.tableReference().size() != 1 || !from.joinClause().isEmpty() ? null
            : from.tableReference(0);
    }

    /**
     * The name a select item answers to: its alias, or the column it reads.
     *
     * @param item the item
     * @return the name, or null for an unnamed expression
     */
    static String labelOf(final FrostlakeParser.ExprItemContext item) {
        final String alias = SelectItemAccessors.getItemAlias(item);
        if (alias != null) {
            return alias;
        }
        final String[] parts = columnParts(item.booleanExpr());
        return parts == null ? null : parts[parts.length - 1];
    }

    /** The parts of the column an expression is, or null where it is no column. */
    private static String[] columnParts(final ParseTree written) {
        final ParseTree node = valueOf(written);
        return node instanceof FrostlakeParser.QualifiedNameExprContext
            ? ParseTreeText.qualifiedNameParts(((FrostlakeParser.QualifiedNameExprContext) node).qualifiedName())
            : null;
    }

    /** A FROM-less item: the value it folds to before any row is read, or VARYING; a NULL is no pinned value. */
    private Object constantValue(final ParserRuleContext expression) {
        if (readsAColumn(expression) || holdsQuery(expression) || !folds(expression)) {
            return VARYING;
        }
        if (outerNames && !GeneratorBounds.foldsItsCalls(expression)) {
            return UNKNOWN;
        }
        try {
            final ExpressionEvaluator evaluator = new ExpressionEvaluator(null, executor.getFunctionRegistry(),
                executor.getCatalog(), executor);
            final Object value = evaluator.evaluate(ExpressionEvaluator.parse(
                ParseTreeText.getOriginalText(expression)), null);
            if (value == null) {
                return outerNames ? NULL_VALUE : VARYING;
            }
            return value;
        } catch (final RuntimeException unfoldable) {
            return VARYING;
        }
    }

    /**
     * Whether a select's WHERE keeps every row of its table: it has none, or, read for outer names, the statistics
     * show it holding on every row ({@code WHERE n >= 1} over n holding 1 and 2).
     */
    private boolean keepsEveryRow(final FrostlakeParser.SelectClauseContext select,
                                  final FrostlakeParser.TableReferenceContext source) {
        final FrostlakeParser.WhereClauseContext where = ParseTreeText.getWhereClause(select);
        if (where == null) {
            return true;
        }
        if (!outerNames || !wholeTable(source) || holdsQuery(where)) {
            return false;
        }
        final Table table = catalogTable(ParseTreeText.getQualifiedName(source.tableSource().tableQualifiedName()));
        final Map<String, Table> scope = new HashMap<>();
        scope.put(GeneratorBounds.qualifierOf(source), table);
        try {
            return Boolean.TRUE.equals(new CorrelatedFilterFold(executor, scope)
                .settles(ParseTreeText.getOriginalText(where.booleanExpr())));
        } catch (final RuntimeException unsettled) {
            return false;
        }
    }

    /** A grouped select's item: a grouping column keeps the value its source pins it to. */
    private Object groupedValue(final FrostlakeParser.SelectClauseContext select, final ParserRuleContext expression,
                                final FrostlakeParser.TableReferenceContext source, final int depth) {
        if (outerNames && !readsAColumn(expression) && !holdsAggregate(expression)) {
            return constantValue(expression);
        }
        final String[] parts = columnParts(expression);
        if (parts == null || !readsSource(parts, source)) {
            return VARYING;
        }
        final String column = parts[parts.length - 1];
        final FrostlakeParser.GroupByClauseContext grouping = select.groupByClause();
        boolean grouped = grouping.ALL() != null;
        for (final FrostlakeParser.GroupByElementContext element : grouping.groupByElement()) {
            final String[] key = element.booleanExpr() == null ? null : columnParts(element.booleanExpr());
            grouped |= key != null && readsSource(key, source) && column.equalsIgnoreCase(key[key.length - 1]);
        }
        return grouped ? sourceValue(source, column, depth + 1) : VARYING;
    }

    /** A projected item: pinned where every column it reads is; its value where it is one such column. */
    private Object projectedValue(final ParserRuleContext expression,
                                  final FrostlakeParser.TableReferenceContext source, final int depth) {
        if (holdsQuery(expression) || !folds(expression) || callsWindow(expression)) {
            return VARYING;
        }
        final List<FrostlakeParser.QualifiedNameExprContext> columns = new ArrayList<>();
        collectColumns(expression, columns);
        if (outerNames && columns.isEmpty()) {
            return constantValue(expression);
        }
        Object only = UNKNOWN;
        for (final FrostlakeParser.QualifiedNameExprContext reference : columns) {
            final String[] parts = ParseTreeText.qualifiedNameParts(reference.qualifiedName());
            if (!readsSource(parts, source)) {
                return VARYING;
            }
            final Object value = sourceValue(source, parts[parts.length - 1], depth + 1);
            if (value == VARYING) {
                return VARYING;
            }
            only = value;
        }
        return columns.size() == 1 && columnParts(expression) != null ? only : UNKNOWN;
    }

    /** Whether an aggregate query's item reads its table through the aggregates the statistics answer alone. */
    private boolean answeredAggregates(final ParseTree node, final FrostlakeParser.TableReferenceContext source) {
        if (node instanceof FrostlakeParser.SelectStatementContext
                || node instanceof FrostlakeParser.QualifiedNameExprContext) {
            return false;
        }
        if (node instanceof FrostlakeParser.FunctionCallStarExprContext) {
            final FrostlakeParser.FunctionCallStarExprContext star = (FrostlakeParser.FunctionCallStarExprContext) node;
            return star.functionName() != null && "COUNT".equalsIgnoreCase(star.functionName().getText())
                && star.DISTINCT() == null && star.starQualifiedName() == null && star.starArgumentModifier() == null
                && wholeTable(source);
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext call = (FrostlakeParser.FunctionCallExprContext) node;
            final String name = call.functionName().getText().toUpperCase(Locale.ROOT);
            if (call.overClause() != null) {
                return false;
            }
            if (executor.getFunctionRegistry().hasAggregateFunction(name)) {
                if (!ANSWERED.contains(name) || call.DISTINCT() != null || call.functionArgList() == null
                        || call.functionArgList().functionArg().size() != 1
                        || call.functionArgList().functionArg(0).booleanExpr() == null) {
                    return false;
                }
                final String[] parts = columnParts(call.functionArgList().functionArg(0).booleanExpr());
                return parts != null && readsSource(parts, source) && wholeTable(source);
            }
            if (!folds(call)) {
                return false;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (!answeredAggregates(node.getChild(i), source)) {
                return false;
            }
        }
        return true;
    }

    /** Whether an expression's calls leave it a constant: per reading, see the class comment. */
    private boolean folds(final ParseTree node) {
        return outerNames ? !GeneratorBounds.callsPerRow(node) : GeneratorBounds.foldsItsCalls(node);
    }

    /** Whether a select groups by ALL and every item holds an aggregate: one group, as no GROUP BY makes. */
    private boolean aggregatesAlone(final FrostlakeParser.SelectClauseContext select) {
        if (select.groupByClause().ALL() == null) {
            return false;
        }
        for (final FrostlakeParser.SelectItemContext item : select.selectList().selectItem()) {
            if (!holdsAggregate(item)) {
                return false;
            }
        }
        return true;
    }

    /**
     * A projected item over a FROM of several items: pinned where every column it reads is a pinned column of an item
     * the joins do not make NULL; its value where it is one such column.
     */
    private Object joinedValue(final ParserRuleContext expression,
                               final FrostlakeParser.TableExpressionContext from, final int depth) {
        if (holdsQuery(expression) || !folds(expression) || callsWindow(expression)) {
            return VARYING;
        }
        final List<FrostlakeParser.TableReferenceContext> items = fromItems(from);
        final List<FrostlakeParser.QualifiedNameExprContext> columns = new ArrayList<>();
        collectColumns(expression, columns);
        if (outerNames && columns.isEmpty()) {
            return constantValue(expression);
        }
        Object only = UNKNOWN;
        for (final FrostlakeParser.QualifiedNameExprContext reference : columns) {
            final String[] parts = ParseTreeText.qualifiedNameParts(reference.qualifiedName());
            final FrostlakeParser.TableReferenceContext item = itemReading(parts, items);
            if (item == null || nullExtended(from, item)) {
                return VARYING;
            }
            final Object value = sourceValue(item, parts[parts.length - 1], depth + 1);
            if (value == VARYING) {
                return VARYING;
            }
            only = value;
        }
        return columns.size() == 1 && columnParts(expression) != null ? only : UNKNOWN;
    }

    /** The FROM item a column reads: the one its qualifier names, else the one item known to carry it, or null. */
    private FrostlakeParser.TableReferenceContext itemReading(final String[] parts,
                                                              final List<FrostlakeParser.TableReferenceContext> items) {
        if (parts.length > 2) {
            return null;
        }
        FrostlakeParser.TableReferenceContext found = null;
        for (final FrostlakeParser.TableReferenceContext item : items) {
            final boolean reads;
            if (parts.length == 2) {
                reads = parts[0].toUpperCase(Locale.ROOT).equals(GeneratorBounds.qualifierOf(item));
            } else {
                final Boolean carries = carries(item, parts[0]);
                if (carries == null) {
                    return null;
                }
                reads = carries.booleanValue();
            }
            if (reads) {
                if (found != null) {
                    return null;
                }
                found = item;
            }
        }
        return found;
    }

    /** Whether a FROM item carries a column of the name; null where this cannot tell. */
    private Boolean carries(final FrostlakeParser.TableReferenceContext item, final String column) {
        final FrostlakeParser.TableSourceContext source = item.tableSource();
        if (source == null || item.identifierList() != null || item.pivotClause() != null
                || item.unpivotClause() != null || item.matchRecognizeClause() != null) {
            return null;
        }
        FrostlakeParser.SelectStatementContext body = source.selectStatement();
        if (body == null && source.tableQualifiedName() != null) {
            final String[] parts = ParseTreeText.qualifiedNameParts(source.tableQualifiedName());
            final FrostlakeParser.CteDefinitionContext cte = parts.length == 1 ? cteNamed(item, parts[0]) : null;
            if (cte == null) {
                final Table table = catalogTable(ParseTreeText.getQualifiedName(source.tableQualifiedName()));
                return table == null ? null : Boolean.valueOf(table.hasColumn(column));
            }
            if (cte.columnListOptional() != null) {
                for (final FrostlakeParser.NamePartContext name : cte.columnListOptional().namePart()) {
                    if (column.equalsIgnoreCase(ParseTreeText.namePartText(name))) {
                        return Boolean.TRUE;
                    }
                }
                return Boolean.FALSE;
            }
            body = cte.selectStatement();
        }
        if (body == null || body.selectOperand(0).selectClause() == null) {
            return null;
        }
        final List<FrostlakeParser.SelectItemContext> items = body.selectOperand(0).selectClause().selectList()
            .selectItem();
        for (final FrostlakeParser.SelectItemContext selectItem : items) {
            if (!(selectItem instanceof FrostlakeParser.ExprItemContext)) {
                return null;
            }
            if (column.equalsIgnoreCase(labelOf((FrostlakeParser.ExprItemContext) selectItem))) {
                return Boolean.TRUE;
            }
        }
        return Boolean.FALSE;
    }

    /**
     * Whether a join of the FROM may make an item's columns NULL: the right of a LEFT or an ASOF join, the left of a
     * RIGHT join - every item before it - and either side of a FULL join.
     */
    private static boolean nullExtended(final FrostlakeParser.TableExpressionContext from,
                                        final FrostlakeParser.TableReferenceContext item) {
        boolean seen = false;
        for (int i = 0; i < from.getChildCount(); i++) {
            final ParseTree child = from.getChild(i);
            if (child == item) {
                seen = true;
                continue;
            }
            if (!(child instanceof FrostlakeParser.JoinClauseContext)) {
                continue;
            }
            final FrostlakeParser.JoinClauseContext join = (FrostlakeParser.JoinClauseContext) child;
            final FrostlakeParser.JoinTypeContext type = join.joinType();
            final boolean full = type != null && type.FULL() != null;
            if (join.tableReference() == item) {
                if (full || join.ASOF() != null || type != null && type.LEFT() != null) {
                    return true;
                }
                seen = true;
            } else if (seen && (full || type != null && type.RIGHT() != null)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a FROM item is a catalog table read whole, whose statistics answer an aggregate over it. */
    private boolean wholeTable(final FrostlakeParser.TableReferenceContext source) {
        final FrostlakeParser.TableSourceContext table = source.tableSource();
        if (table == null || table.tableQualifiedName() == null || table.timeTravelClause() != null
                || source.sampleClause() != null) {
            return false;
        }
        final String[] parts = ParseTreeText.qualifiedNameParts(table.tableQualifiedName());
        return (parts.length > 1 || cteNamed(source, parts[0]) == null)
            && catalogTable(ParseTreeText.getQualifiedName(table.tableQualifiedName())) != null;
    }

    /** Whether a column's qualifier, where it has one, names the FROM's one item. */
    private static boolean readsSource(final String[] parts, final FrostlakeParser.TableReferenceContext source) {
        return parts.length == 1
            || parts.length == 2 && parts[0].toUpperCase(Locale.ROOT).equals(GeneratorBounds.qualifierOf(source));
    }

    /** Whether a select list holds an aggregate call outside the queries nested in it and any window. */
    private boolean holdsAggregate(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return false;
        }
        if (node instanceof FrostlakeParser.FunctionCallStarExprContext) {
            return true;
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext call = (FrostlakeParser.FunctionCallExprContext) node;
            if (call.overClause() == null && executor.getFunctionRegistry().hasAggregateFunction(
                    call.functionName().getText().toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (holdsAggregate(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean callsWindow(final ParseTree node) {
        if (node instanceof FrostlakeParser.OverClauseContext) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (callsWindow(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    private static void collectColumns(final ParseTree node,
                                       final List<FrostlakeParser.QualifiedNameExprContext> into) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            into.add((FrostlakeParser.QualifiedNameExprContext) node);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectColumns(node.getChild(i), into);
        }
    }

    private static boolean readsAColumn(final ParseTree node) {
        final List<FrostlakeParser.QualifiedNameExprContext> columns = new ArrayList<>();
        collectColumns(node, columns);
        return !columns.isEmpty();
    }

    private static boolean holdsQuery(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (holdsQuery(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    /** Whether two pinned values are one value: numbers by their value, anything else by equality. */
    private static boolean same(final Object first, final Object second) {
        if (first instanceof Number && second instanceof Number) {
            return DecimalText.parse(first.toString()).compareTo(DecimalText.parse(second.toString())) == 0;
        }
        return first == null ? second == null : first.equals(second);
    }

    private static ParseTree valueOf(final ParseTree written) {
        ParseTree node = written;
        while (true) {
            if (node instanceof FrostlakeParser.ValueExprContext) {
                node = ((FrostlakeParser.ValueExprContext) node).expression();
            } else if (node instanceof FrostlakeParser.ParenExprContext) {
                node = ((FrostlakeParser.ParenExprContext) node).booleanExpr();
            } else {
                return node;
            }
        }
    }
}
