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
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.Row;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DecimalText;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A node of the plan the account holds for a set operation, as a refusal echoes it (all live-verified; see
 * SetOperationPlanText): a branch - a FROM-less select of literals, or a select of one table's columns, a table of
 * one row read as its values - or a UNION ALL, a UNION, an INTERSECT or an EXCEPT over the nodes beneath it.
 *
 * <p>Branches are numbered from 0, the nodes beneath an operation before its own branches, each branch a {@code WB$n}
 * and an operation standing as a branch an {@code OB$n}. A UNION ALL whose rows alone are read is
 * {@code null AS "CNULL"} over each branch; one whose column is read, and a UNION, combines the branches' values by
 * SET_COMBINE, one value they all hold standing for it, a UNION grouped by it. An INTERSECT is its left side SEMI
 * JOINed to its right on each column's equality and grouped by its columns, a side of another type than the other's
 * cast to the type both take; an EXCEPT is its left side grouped by its columns, the right one left out. A column that
 * holds one value is read as that value: {@code (SELECT MAX(x) FROM (SELECT 1 AS x INTERSECT SELECT 1))} is
 * {@code MAX(1)}.
 *
 * <p>A UNION ALL beneath a UNION or a UNION ALL (or on the left of an EXCEPT) stands as itself, {@code (…) OB$2}, any
 * other operation beneath one under a select of its columns, {@code (SELECT OB$2.A AS "A" FROM (…) OB$2) WB$2}; an
 * operation's {@code OB$n} counts the selects and operations written before it and beneath it. A UNION of three
 * branches or more groups each of them by its columns too: {@code (SELECT 4 AS "A" FROM T2 AS T2 GROUP BY 4) WB$2}, a
 * table read as itself even where it holds one row. A UNION ALL keeps only the columns the select around reads, a
 * UNION all of them. A FROM-less branch beside a table's is {@code SELECT ENSURE_NULLABLE(1) AS "1" FROM (VALUES
 * (null)) DUAL}, read by its name. A table's column whose rows all hold one number is that number, the table a
 * GENERATOR of its rows where every column kept holds one; a filter its statistics prove false is {@code WHERE FALSE}.
 * A UNION BY NAME whose branches name their columns alike is the UNION of them, a branch ordering them otherwise
 * written in its own order, all of them, where a column read stands elsewhere in it; a literal cast by {@code ::} is
 * the literal, named by the cast's text.
 *
 * <p>The plan holds a number column in 1, 2, 4, 8 or 16 bytes by the values it holds: a literal, a table's column by
 * its rows. A UNION casts a branch's column held in fewer bytes than the union's to the union's type,
 * {@code CAST(100 AS NUMBER(3,0))} beside 200, and a table's column of another type too; an operation's column
 * beneath it is cast twice. An INTERSECT whose sides hold one type casts the narrower side in its join condition.
 */
final class SetOperationPlanNode {

    private final SetOperationKind kind;
    private final FrostlakeParser.SelectOperandContext operand;
    private final List<SetOperationPlanNode> children = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();
    private final List<String> constants = new ArrayList<>();
    private final List<DataType> types = new ArrayList<>();
    private final List<String> items = new ArrayList<>();
    private final List<String> casts = new ArrayList<>();
    private final List<BigDecimal> lows = new ArrayList<>();
    private final List<BigDecimal> highs = new ArrayList<>();
    private List<Integer> written;
    private String source;
    private String tableSource;
    private long tableRows;
    private boolean literal;
    private boolean named;
    private boolean readsTable;
    private int index = -1;
    private int nested = -1;

    private SetOperationPlanNode(final SetOperationKind kind, final FrostlakeParser.SelectOperandContext operand) {
        this.kind = kind;
        this.operand = operand;
    }

    /**
     * The plan of a set operation, its nodes numbered.
     *
     * @param executor the executor whose catalog and statistics the branches read
     * @param query    the set operation
     * @return the plan, or null where a node of it is not one of these shapes
     */
    static SetOperationPlanNode of(final QueryExecutor executor, final FrostlakeParser.SelectStatementContext query) {
        if (query.selectOperand().size() < 2 || query.orderByClause() != null || query.limitClause() != null
                || query.fetchClause() != null || query.withClause() != null) {
            return null;
        }
        final SetOperationPlanNode root = node(executor, SetOperationTree.of(query));
        if (root == null) {
            return null;
        }
        number(root, new int[] {0});
        identify(root, new int[] {0});
        return root;
    }

    private static SetOperationPlanNode node(final QueryExecutor executor, final SetOperationTree tree) {
        if (tree.isLeaf()) {
            final FrostlakeParser.SelectOperandContext operand = tree.operand();
            if (operand.selectStatement() != null) {
                final FrostlakeParser.SelectStatementContext inner = operand.selectStatement();
                if (inner.selectOperand().size() > 1) {
                    return of(executor, inner);
                }
                return inner.withClause() != null || inner.orderByClause() != null || inner.limitClause() != null
                    || inner.fetchClause() != null ? null : branch(executor, inner.selectOperand(0));
            }
            return branch(executor, operand);
        }
        final FrostlakeParser.SetOperatorContext operator = tree.operator();
        if (operator.ALL() != null && operator.UNION() == null) {
            return null;
        }
        final SetOperationKind kind = operator.UNION() != null
            ? operator.ALL() != null ? SetOperationKind.UNION_ALL : SetOperationKind.UNION
            : operator.INTERSECT() != null ? SetOperationKind.INTERSECT : SetOperationKind.EXCEPT;
        final SetOperationPlanNode left = node(executor, tree.left());
        final SetOperationPlanNode right = node(executor, tree.right());
        if (left == null || right == null || left.width() != right.width()
                || operator.BY() != null && !left.labels.equals(right.labels) && !right.byName(left.labels)) {
            return null;
        }
        final SetOperationPlanNode node = new SetOperationPlanNode(kind, null);
        node.readsTable = left.readsTable || right.readsTable;
        node.named = operator.BY() != null;
        final boolean chained = left.kind == kind && left.named == node.named && !tree.left().isLeaf()
            && (kind == SetOperationKind.UNION_ALL || kind == SetOperationKind.UNION);
        if (chained) {
            node.children.addAll(left.children);
        } else {
            node.children.add(left);
        }
        node.children.add(right);
        if (node.named) {
            // A BY NAME operand is a select of its own; one beside an operation of another kind is not printed here.
            if (!chained && left.kind != SetOperationKind.BRANCH || right.kind != SetOperationKind.BRANCH) {
                return null;
            }
            left.named = true;
            right.named = true;
        }
        return node.columns() ? node : null;
    }

    /** A select standing as a branch, or null where it is no shape a branch is printed in. */
    private static SetOperationPlanNode branch(final QueryExecutor executor,
                                               final FrostlakeParser.SelectOperandContext operand) {
        final FrostlakeParser.SelectClauseContext select = operand.selectClause();
        if (select == null || select.DISTINCT() != null || select.topClause() != null
                || select.groupByClause() != null || select.havingClause() != null || select.qualifyClause() != null
                || select.connectByClause() != null || select.intoClause() != null) {
            return null;
        }
        final SetOperationPlanNode branch = new SetOperationPlanNode(SetOperationKind.BRANCH, operand);
        final FrostlakeParser.WhereClauseContext where = ParseTreeText.getWhereClause(select);
        if (select.tableExpression() == null) {
            if (where != null) {
                return null;
            }
            for (final FrostlakeParser.SelectItemContext item : select.selectList().selectItem()) {
                final ParseTree value = item instanceof FrostlakeParser.ExprItemContext
                    ? valueOf(((FrostlakeParser.ExprItemContext) item).booleanExpr()) : null;
                final FrostlakeParser.LiteralExprContext held = heldLiteral(value);
                final DataType type = held == null ? null : literalType(executor, held, value);
                if (type == null) {
                    return null;
                }
                final String text = ParseTreeText.getOriginalText(held);
                // A number with a fraction is written without its trailing zeros: 1.50 is 1.5, 100.0 is 100.
                final String written = exactNumber(type) && held.literal().FLOAT_LITERAL() != null
                    ? DecimalText.parse(text).stripTrailingZeros().toPlainString() : text;
                final String alias = SelectItemAccessors.getItemAlias(item);
                final String named = alias != null ? alias
                    : ParseTreeText.getOriginalText((ParserRuleContext) value).toUpperCase(Locale.ROOT);
                final BigDecimal number = exactNumber(type) ? DecimalText.parse(written) : null;
                branch.add(named, written, type, written, number, number);
            }
            branch.source = SetOperationPlanText.ONE_ROW;
            branch.literal = true;
            return branch;
        }
        return tableBranch(executor, select, where, branch);
    }

    /** A select of one table's columns: by its name and alias, or as one row of its values where it holds one. */
    private static SetOperationPlanNode tableBranch(final QueryExecutor executor,
                                                    final FrostlakeParser.SelectClauseContext select,
                                                    final FrostlakeParser.WhereClauseContext where,
                                                    final SetOperationPlanNode branch) {
        final FrostlakeParser.TableExpressionContext from = select.tableExpression();
        if (from.tableReference().size() != 1 || !from.joinClause().isEmpty()) {
            return null;
        }
        final FrostlakeParser.TableReferenceContext reference = from.tableReference(0);
        final FrostlakeParser.TableSourceContext source = reference.tableSource();
        if (source == null || source.tableQualifiedName() == null || source.timeTravelClause() != null
                || reference.pivotClause() != null || reference.unpivotClause() != null
                || reference.sampleClause() != null || reference.matchRecognizeClause() != null) {
            return null;
        }
        final String[] parts = ParseTreeText.qualifiedNameParts(source.tableQualifiedName());
        if (parts.length == 1 && DerivedRelationPins.cteNamed(reference, parts[0]) != null) {
            return null;
        }
        final Table table;
        try {
            table = executor.getCatalog().resolveTable(ParseTreeText.getQualifiedName(source.tableQualifiedName()));
        } catch (final RuntimeException noTable) {
            return null;
        }
        if (table == null || !table.isCatalogResident()) {
            return null;
        }
        final String relation = GeneratorBounds.qualifierOf(reference);
        final List<Row> rows = executor.statisticsRowsFor(executor.getFullyQualifiedTableName(
            ParseTreeText.getQualifiedName(source.tableQualifiedName())));
        for (final FrostlakeParser.SelectItemContext item : select.selectList().selectItem()) {
            final ParseTree value = item instanceof FrostlakeParser.ExprItemContext
                ? valueOf(((FrostlakeParser.ExprItemContext) item).booleanExpr()) : null;
            if (!(value instanceof FrostlakeParser.QualifiedNameExprContext)) {
                return null;
            }
            final String[] named = ParseTreeText.qualifiedNameParts(
                ((FrostlakeParser.QualifiedNameExprContext) value).qualifiedName());
            final String column = named[named.length - 1];
            if (named.length > 2 || named.length == 2 && !named[0].toUpperCase(Locale.ROOT).equals(relation)
                    || !table.hasColumn(column)) {
                return null;
            }
            final String declared = table.getColumn(column).getName();
            final String alias = SelectItemAccessors.getItemAlias(item);
            final String label = alias != null ? alias : declared.toUpperCase(Locale.ROOT);
            final DataType type = table.getColumn(column).getDataType();
            final int at = table.getColumnIndex(declared);
            final Object held = rows.size() == 1 ? rows.get(0).getValue(at) : singleValue(rows, at);
            if (rows.size() == 1 && held == null) {
                return null;
            }
            String constant = null;
            if (held != null) {
                // A column the statistics hold one value in is read as that value.
                if (!(held instanceof Number) || where != null) {
                    return null;
                }
                constant = DecimalText.parse(held.toString()).toPlainString();
            }
            branch.add(label, constant, type, relation + "." + declared.toUpperCase(Locale.ROOT),
                bound(rows, at, true), bound(rows, at, false));
        }
        branch.readsTable = true;
        branch.tableRows = rows.size();
        branch.tableSource = table.getName().toUpperCase(Locale.ROOT) + " AS " + relation;
        if (where != null && SetOperationPlanText.provenFalse(executor, select, reference, table, where)) {
            branch.tableSource += " WHERE FALSE";
        } else if (where != null) {
            final Map<String, Table> relations = new LinkedHashMap<>();
            relations.put(relation, table);
            final List<Table> tables = new ArrayList<>();
            tables.add(table);
            final ExpressionEvaluator printer =
                new ExpressionEvaluator(table, executor.getFunctionRegistry(), executor.getCatalog(), executor);
            printer.setMultiTableContext(relations, tables);
            printer.setEnclosingQualifiers(new HashSet<String>());
            branch.tableSource += " WHERE " + printer.boundPlannedText(ExpressionEvaluator.parse(
                ParseTreeText.getOriginalText(where.booleanExpr())));
        }
        return branch;
    }

    /** The one value every row of a column holds, none of them null; null where the rows hold more or none. */
    private static Object singleValue(final List<Row> rows, final int column) {
        Object first = null;
        for (final Row row : rows) {
            final Object held = row.getValue(column);
            if (!(held instanceof Number)) {
                return null;
            }
            if (first == null) {
                first = held;
            } else if (DecimalText.parse(first.toString()).compareTo(DecimalText.parse(held.toString())) != 0) {
                return null;
            }
        }
        return first;
    }

    /** The least, or the greatest, number a column's rows hold, nulls aside; null where they hold none. */
    private static BigDecimal bound(final List<Row> rows, final int column, final boolean least) {
        BigDecimal found = null;
        for (final Row row : rows) {
            final Object held = row.getValue(column);
            if (!(held instanceof Number)) {
                continue;
            }
            final BigDecimal value = DecimalText.parse(held.toString());
            if (found == null || (least ? value.compareTo(found) < 0 : value.compareTo(found) > 0)) {
                found = value;
            }
        }
        return found;
    }

    private void add(final String label, final String constant, final DataType type, final String item,
                     final BigDecimal low, final BigDecimal high) {
        labels.add(label);
        constants.add(constant);
        types.add(type);
        items.add(item);
        casts.add(null);
        lows.add(low);
        highs.add(high);
    }

    /**
     * Whether this branch, beside an operation whose columns go by {@code names} BY NAME, holds those columns in
     * another order: its columns are then put in the operation's order, the order it writes them kept.
     */
    private boolean byName(final List<String> names) {
        if (kind != SetOperationKind.BRANCH || names.size() != labels.size()) {
            return false;
        }
        final List<Integer> order = new ArrayList<>();
        for (final String label : labels) {
            final int at = names.indexOf(label);
            if (at < 0 || order.contains(Integer.valueOf(at))) {
                return false;
            }
            order.add(Integer.valueOf(at));
        }
        written = order;
        permute(labels, order);
        permute(constants, order);
        permute(types, order);
        permute(items, order);
        permute(casts, order);
        permute(lows, order);
        permute(highs, order);
        return true;
    }

    /** Puts a list's entries in the order {@code order} gives each its place in. */
    private static <T> void permute(final List<T> list, final List<Integer> order) {
        final List<T> copy = new ArrayList<>(list);
        for (int w = 0; w < order.size(); w++) {
            list.set(order.get(w).intValue(), copy.get(w));
        }
    }

    /** An operation's columns from its children's: false where their types do not combine. */
    private boolean columns() {
        final SetOperationPlanNode first = children.get(0);
        for (int c = 0; c < first.width(); c++) {
            final boolean combining = kind == SetOperationKind.UNION_ALL || kind == SetOperationKind.UNION;
            String constant = combining ? combinedConstant(first, c) : first.constants.get(c);
            DataType type = first.types.get(c);
            if (combining) {
                for (int i = 1; i < children.size(); i++) {
                    final SetOperationPlanNode child = children.get(i);
                    constant = constant != null && constant.equals(combinedConstant(child, c)) ? constant : null;
                    type = combinedType(type, child.types.get(c));
                }
            } else if (kind == SetOperationKind.INTERSECT) {
                type = combinedType(type, children.get(1).types.get(c));
            }
            if (type == null) {
                return false;
            }
            labels.add(first.labels.get(c));
            constants.add(constant);
            types.add(type);
            items.add(null);
            casts.add(null);
            BigDecimal low = first.lows.get(c);
            BigDecimal high = first.highs.get(c);
            for (int i = 1; i < children.size() && kind != SetOperationKind.INTERSECT
                    && kind != SetOperationKind.EXCEPT; i++) {
                final SetOperationPlanNode child = children.get(i);
                low = low == null || child.lows.get(c) == null ? null : low.min(child.lows.get(c));
                high = high == null || child.highs.get(c) == null ? null : high.max(child.highs.get(c));
            }
            lows.add(low);
            highs.add(high);
        }
        return true;
    }

    /**
     * How many bytes the plan holds a number column in, its values scaled to {@code scale}: 1, 2, 4, 8 or 16 by the
     * least and greatest value it holds; 0 where these are not known.
     */
    private int bytes(final int column, final int scale) {
        if (lows.get(column) == null || highs.get(column) == null) {
            return 0;
        }
        final int bits = Math.max(lows.get(column).movePointRight(scale).toBigInteger().bitLength(),
            highs.get(column).movePointRight(scale).toBigInteger().bitLength());
        int size = 1;
        while (size < 16 && bits > size * 8 - 1) {
            size *= 2;
        }
        return size;
    }

    /** @return whether the plan holds the number column in sixteen bytes, the widest it takes */
    boolean widest(final int column) {
        final NumericType type = number(column);
        return type != null && bytes(column, type.getScale()) == 16;
    }

    /**
     * The type a child's column is cast to where the plan combines it into this operation's: none where it is held
     * alike. A literal takes any scale as it stands, cast only where it is held in fewer bytes than the operation's
     * column; a table's column, and an operation's, is cast also where its type is another.
     */
    private String castOf(final SetOperationPlanNode child, final int column) {
        final NumericType target = number(column);
        if (target == null || child.number(column) == null) {
            return null;
        }
        final int own = child.bytes(column, target.getScale());
        final int whole = bytes(column, target.getScale());
        final boolean narrower = own > 0 && whole > 0 && own < whole;
        final boolean literalValue = child.kind == SetOperationKind.BRANCH && child.literal;
        return narrower || !literalValue && !sameType(child.types.get(column), target) ? typeText(target) : null;
    }

    /**
     * The one value a child's column holds as a UNION combines it: none for a FROM-less branch beside a table's, which
     * the plan reads through ENSURE_NULLABLE.
     */
    private String combinedConstant(final SetOperationPlanNode child, final int column) {
        return nullable(child) ? null : child.constants.get(column);
    }

    /** Whether a child is a FROM-less branch beside a table's, not a BY NAME operand, read through ENSURE_NULLABLE. */
    private boolean nullable(final SetOperationPlanNode child) {
        return readsTable && child.literal && !child.named;
    }

    /**
     * Numbers the branches beneath a node: the operations beneath it first, then its own children in order. A branch,
     * and an operation the plan selects from as a branch, takes the next {@code WB$n}.
     */
    private static void number(final SetOperationPlanNode node, final int[] next) {
        for (final SetOperationPlanNode child : node.children) {
            if (child.kind != SetOperationKind.BRANCH) {
                number(child, next);
            }
        }
        for (int i = 0; i < node.children.size(); i++) {
            final SetOperationPlanNode child = node.children.get(i);
            if (child.kind == SetOperationKind.BRANCH || !node.inlines(child, i)) {
                child.index = next[0]++;
            }
        }
    }

    /**
     * Names the operations {@code OB$n}: every select and every operation counted in the order written, an operation
     * after the nodes beneath it, a chain of one operation counted once.
     */
    private static void identify(final SetOperationPlanNode node, final int[] next) {
        for (final SetOperationPlanNode child : node.children) {
            identify(child, next);
        }
        node.nested = next[0]++;
    }

    /**
     * Whether an operation beneath this one stands in its plan as itself, no select of its columns around it: a UNION
     * ALL beneath a UNION, a UNION ALL or (on the left, holding no one value) an EXCEPT, and an INTERSECT's right side.
     */
    private boolean inlines(final SetOperationPlanNode child, final int position) {
        switch (kind) {
            case UNION:
            case UNION_ALL:
                return child.kind == SetOperationKind.UNION_ALL;
            case INTERSECT:
                return position == 1;
            case EXCEPT:
                if (position != 0 || child.kind != SetOperationKind.UNION_ALL) {
                    return false;
                }
                for (int c = 0; c < child.width(); c++) {
                    if (child.constants.get(c) != null) {
                        return false;
                    }
                }
                return true;
            default:
                return false;
        }
    }

    /** @return how many columns the node answers */
    int width() {
        return labels.size();
    }

    /** @return the name the node's column at {@code column} answers to */
    String label(final int column) {
        return labels.get(column);
    }

    /** @return the one value the column holds, as the plan writes it, or null */
    String constant(final int column) {
        return constants.get(column);
    }

    /** @return the column's exact number type, or null for another type */
    NumericType number(final int column) {
        return exactNumber(types.get(column)) ? (NumericType) types.get(column) : null;
    }

    /** @return the column's type as the plan writes it, NUMBER(p,s) for a number, or null for another */
    String type(final int column) {
        return typeText(types.get(column));
    }

    /**
     * The node's text as the plan holds it beneath a select.
     *
     * @param executor the executor whose statistics a counted branch reads
     * @param counted  whether the select reads the node's rows alone, no column of it
     * @param read     the names of the columns the select reads
     * @return the text, without its parentheses, or null where it is not printed here
     */
    String text(final QueryExecutor executor, final boolean counted, final Set<String> read) {
        final List<Integer> kept = new ArrayList<>();
        for (int c = 0; c < width(); c++) {
            if (kind != SetOperationKind.UNION_ALL || containsIgnoreCase(read, labels.get(c))) {
                kept.add(Integer.valueOf(c));
            }
        }
        return text(executor, counted, kept);
    }

    private static boolean containsIgnoreCase(final Set<String> read, final String label) {
        for (final String name : read) {
            if (name.equalsIgnoreCase(label)) {
                return true;
            }
        }
        return false;
    }

    /** The node's text, a UNION ALL's columns those at {@code kept}; every other operation keeps all of them. */
    private String text(final QueryExecutor executor, final boolean counted, final List<Integer> kept) {
        switch (kind) {
            case BRANCH:
                return branchText(all(), false, false);
            case UNION_ALL:
                return counted ? countedText(executor) : combinedText(executor, false, kept, null);
            case UNION:
                return combinedText(executor, true, all(), null);
            case INTERSECT:
                return intersectText(executor);
            default:
                return exceptText(executor);
        }
    }

    private List<Integer> all() {
        final List<Integer> every = new ArrayList<>();
        for (int c = 0; c < width(); c++) {
            every.add(Integer.valueOf(c));
        }
        return every;
    }

    /**
     * A branch's select of the columns at {@code kept}: each one value it holds, else its column, cast where the
     * operation above casts it; FROM its table, or a GENERATOR of the table's rows where every column kept holds one
     * value. A FROM-less branch beside a table's ({@code nullable}) reads its values through ENSURE_NULLABLE from one
     * row of VALUES. A branch a UNION of three or more groups ({@code grouped}) reads its table, or one row of VALUES,
     * grouped by its columns, each one value it holds standing for it. With {@code kept} null the branch writes all its
     * columns, in its own order where it orders them otherwise than the operation BY NAME.
     */
    private String branchText(final List<Integer> kept, final boolean nullable, final boolean grouped) {
        final StringBuilder text = new StringBuilder("SELECT ");
        final List<String> keys = new ArrayList<>();
        boolean folded = true;
        for (final Integer at : printed(kept)) {
            final int c = at.intValue();
            String value;
            String key = labels.get(c);
            if (literal && nullable) {
                value = "ENSURE_NULLABLE(" + items.get(c) + ")";
            } else if (constants.get(c) != null) {
                value = constants.get(c);
                key = value;
            } else {
                value = items.get(c);
                folded = false;
            }
            if (casts.get(c) != null) {
                value = "CAST(" + value + " AS " + casts.get(c) + ")";
            }
            keys.add(key);
            text.append(text.length() > "SELECT ".length() ? ", " : "").append(value).append(" AS \"")
                .append(labels.get(c)).append('"');
        }
        final String from;
        if (literal) {
            from = nullable || grouped ? "(VALUES (null)) DUAL" : source;
        } else {
            from = folded && !grouped
                ? "TABLE (GENERATOR(ROWCOUNT => " + tableRows + ", rowCount => " + tableRows + "))" : tableSource;
        }
        text.append(" FROM ").append(from);
        return grouped ? text.append(" GROUP BY ").append(String.join(", ", keys)).toString() : text.toString();
    }

    /**
     * The columns a branch writes: those at {@code kept}, or where that is null all of them, in its own order where it
     * orders them otherwise than the operation BY NAME.
     */
    private List<Integer> printed(final List<Integer> kept) {
        return kept != null ? kept : written != null ? written : all();
    }

    /** Whether this branch writes a column at {@code kept} elsewhere than the operation BY NAME does. */
    private boolean moves(final List<Integer> kept) {
        if (written == null) {
            return false;
        }
        for (final Integer at : kept) {
            if (written.indexOf(at) != at.intValue()) {
                return true;
            }
        }
        return false;
    }

    /**
     * A UNION ALL whose rows alone are read: {@code null AS "CNULL"} over each branch, a UNION ALL beneath it counted
     * the same way, another operation selected from as a branch.
     */
    private String countedText(final QueryExecutor executor) {
        final StringBuilder text = new StringBuilder();
        for (int i = 0; i < children.size(); i++) {
            final SetOperationPlanNode child = children.get(i);
            final String branch;
            final String name;
            if (child.kind == SetOperationKind.BRANCH) {
                branch = SetOperationPlanText.countedBranch(executor, child.operand);
                name = "WB$" + child.index;
            } else if (child.kind == SetOperationKind.UNION_ALL) {
                branch = child.countedText(executor);
                name = "OB$" + child.nested;
            } else {
                final String inner = child.text(executor, false, child.all());
                branch = inner == null ? null
                    : "SELECT null AS \"CNULL\" FROM (" + inner + ") OB$" + child.nested;
                name = "WB$" + child.index;
            }
            if (branch == null) {
                return null;
            }
            text.append(i == 0 ? "SELECT null AS \"CNULL\" FROM (" : " UNION ALL (").append(branch).append(") ")
                .append(name);
        }
        return text.toString();
    }

    /**
     * A UNION, with or without ALL, of its children's columns at {@code kept} combined:
     * {@code SET_COMBINE(WB$0.X, 4) AS "X"}, one value they all hold standing for it; a UNION ALL beneath it as
     * itself, another operation selected from as a branch; a UNION grouped by its columns, and each of its children
     * grouped too where it has three or more. A BY NAME branch writing a column kept elsewhere than the operation does,
     * and every branch after it, writes all its columns (see printed). A child's column is cast where it is held otherwise than this one's
     * (see castOf): a branch's once, an operation's twice ({@code wrap}, a type per column kept or null).
     */
    private String combinedText(final QueryExecutor executor, final boolean grouped, final List<Integer> kept,
                                final List<String> wrap) {
        final boolean grouping = grouped && children.size() > 2;
        boolean whole = false;
        final StringBuilder branches = new StringBuilder();
        final List<List<String>> combined = new ArrayList<>();
        for (int k = 0; k < kept.size(); k++) {
            combined.add(new ArrayList<String>());
        }
        for (int i = 0; i < children.size(); i++) {
            final SetOperationPlanNode child = children.get(i);
            final List<String> childCasts = new ArrayList<>();
            for (final Integer at : kept) {
                childCasts.add(castOf(child, at.intValue()));
            }
            final String relation;
            final String input;
            if (child.kind == SetOperationKind.BRANCH) {
                for (int k = 0; k < kept.size(); k++) {
                    child.casts.set(kept.get(k).intValue(), childCasts.get(k));
                }
                relation = "WB$" + child.index;
                whole = whole || child.moves(kept);
                input = "(" + child.branchText(whole ? null : kept, nullable(child), grouping) + ") " + relation;
            } else if (child.kind == SetOperationKind.UNION_ALL) {
                relation = "OB$" + child.nested;
                final String inner = child.combinedText(executor, false, kept, childCasts);
                input = inner == null ? null
                    : "(" + inner + (grouping ? " GROUP BY " + child.keys(kept) : "") + ") " + relation;
            } else {
                relation = "WB$" + child.index;
                final String inner = child.text(executor, false, child.all());
                final String items = child.outputItems("OB$" + child.nested, kept, childCasts);
                input = inner == null || items == null ? null : "(SELECT " + items + " FROM (" + inner + ") OB$"
                    + child.nested + (grouping ? " GROUP BY " + child.keys(kept) : "") + ") " + relation;
            }
            if (input == null) {
                return null;
            }
            for (int k = 0; k < kept.size(); k++) {
                final int c = kept.get(k).intValue();
                final String constant = combinedConstant(child, c);
                combined.get(k).add(constant != null ? constant : relation + "." + reference(child.labels.get(c)));
            }
            branches.append(i == 0 ? "" : " UNION ALL ").append(input);
        }
        final StringBuilder text = new StringBuilder("SELECT ");
        for (int k = 0; k < kept.size(); k++) {
            final int c = kept.get(k).intValue();
            final String value = constants.get(c) != null ? constants.get(c)
                : "SET_COMBINE(" + String.join(", ", combined.get(k)) + ")";
            final String cast = wrap == null ? null : wrap.get(k);
            if (cast != null && constants.get(c) != null) {
                return null;
            }
            text.append(k > 0 ? ", " : "").append(cast == null ? value : twice(value, cast)).append(" AS \"")
                .append(labels.get(c)).append('"');
        }
        text.append(" FROM ").append(branches);
        return grouped ? text.append(" GROUP BY ").append(keys(kept)).toString() : text.toString();
    }

    /** An operation's column cast as the plan casts it into a wider one: twice, to the same type. */
    private static String twice(final String value, final String type) {
        return "CAST(CAST(" + value + " AS " + type + ") AS " + type + ")";
    }

    /**
     * An INTERSECT: the left side SEMI JOINed to the right on each column's equality, grouped by its columns, a
     * column of another type than the one both sides take cast to it.
     */
    private String intersectText(final QueryExecutor executor) {
        final SetOperationPlanNode left = children.get(0);
        final SetOperationPlanNode right = children.get(1);
        for (int c = 0; c < width(); c++) {
            if (!sameType(left.types.get(c), right.types.get(c))) {
                if (left.kind != SetOperationKind.BRANCH || right.kind != SetOperationKind.BRANCH
                        || typeText(types.get(c)) == null) {
                    return null;
                }
                // A side is cast where its own type is not the one both take; a literal takes it as it stands.
                if (!sameType(left.types.get(c), types.get(c)) && left.constants.get(c) == null) {
                    left.casts.set(c, typeText(types.get(c)));
                }
                if (!sameType(right.types.get(c), types.get(c)) && right.constants.get(c) == null) {
                    right.casts.set(c, typeText(types.get(c)));
                }
            }
        }
        final String leftAlias = "WB$" + left.index;
        final String leftText = inputText(executor, left, true);
        final String rightText = inputText(executor, right, false);
        if (leftText == null || rightText == null) {
            return null;
        }
        final String rightAlias = right.kind == SetOperationKind.BRANCH ? "WB$" + right.index : "OB$" + right.nested;
        final List<String> conditions = new ArrayList<>();
        for (int c = 0; c < width(); c++) {
            final String leftRead = leftAlias + "." + reference(left.labels.get(c));
            final String rightRead = rightAlias + "." + reference(right.labels.get(c));
            final int narrower = narrowerSide(left, right, c);
            conditions.add("(" + (narrower == 0 ? "(CAST(" + leftRead + " AS " + typeText(types.get(c)) + "))"
                : leftRead) + " = " + (narrower == 1 ? "(CAST(" + rightRead + " AS " + typeText(types.get(c))
                + "))" : rightRead) + ")");
        }
        final String on = conditions.size() == 1 ? conditions.get(0) : "(" + String.join(" AND ", conditions) + ")";
        return "SELECT " + outputItems(leftAlias, all()) + " FROM " + leftText + " SEMI JOIN " + rightText + " ON "
            + on + " GROUP BY " + keys();
    }

    /**
     * Which side of an INTERSECT of two branches the join condition casts, the column holding the same type on both:
     * the one the plan holds in fewer bytes (0 the left, 1 the right), or -1 for neither.
     */
    private int narrowerSide(final SetOperationPlanNode left, final SetOperationPlanNode right, final int column) {
        final NumericType target = number(column);
        if (target == null || left.kind != SetOperationKind.BRANCH || right.kind != SetOperationKind.BRANCH
                || left.casts.get(column) != null || right.casts.get(column) != null) {
            return -1;
        }
        final int one = left.bytes(column, target.getScale());
        final int other = right.bytes(column, target.getScale());
        if (one == 0 || other == 0 || one == other) {
            return -1;
        }
        return one < other ? 0 : 1;
    }

    /**
     * A side of an INTERSECT: a branch as itself, an operation on the left under a select of its columns, an
     * operation on the right as itself.
     */
    private static String inputText(final QueryExecutor executor, final SetOperationPlanNode side, final boolean left) {
        if (side.kind == SetOperationKind.BRANCH) {
            return "(" + side.branchText(side.all(), false, false) + ") WB$" + side.index;
        }
        final String inner = side.text(executor, false, side.all());
        if (inner == null) {
            return null;
        }
        final String nested = "OB$" + side.nested;
        return left ? "(SELECT " + side.outputItems(nested, side.all()) + " FROM (" + inner + ") " + nested
            + ") WB$" + side.index : "(" + inner + ") " + nested;
    }

    /** An EXCEPT: its left side grouped by its columns, the right one left out. */
    private String exceptText(final QueryExecutor executor) {
        final SetOperationPlanNode left = children.get(0);
        if (left.kind == SetOperationKind.BRANCH) {
            final String alias = "WB$" + left.index;
            return "SELECT " + outputItems(alias, all()) + " FROM (" + left.branchText(left.all(), false, false) + ") "
                + alias + " GROUP BY " + keys();
        }
        final String inner = left.text(executor, false, left.all());
        if (inner == null) {
            return null;
        }
        final String nested = "OB$" + left.nested;
        if (inlines(left, 0)) {
            return "SELECT " + outputItems(nested, all()) + " FROM (" + inner + ") " + nested + " GROUP BY " + keys();
        }
        final String alias = "WB$" + left.index;
        return "SELECT " + outputItems(alias, all()) + " FROM (SELECT " + left.outputItems(nested, left.all())
            + " FROM (" + inner + ") " + nested + ") " + alias + " GROUP BY " + keys();
    }

    /** The node's columns at {@code kept} read from a relation so named: each one value it holds, else its column. */
    private String outputItems(final String relation, final List<Integer> kept) {
        return outputItems(relation, kept, null);
    }

    /**
     * The node's columns at {@code kept} read from a relation so named, each cast twice to its type in {@code casts}
     * (one per column kept, or null); null where a column holding one value would be cast.
     */
    private String outputItems(final String relation, final List<Integer> kept, final List<String> casts) {
        final StringBuilder text = new StringBuilder();
        for (int k = 0; k < kept.size(); k++) {
            final int c = kept.get(k).intValue();
            final String cast = casts == null ? null : casts.get(k);
            if (cast != null && constants.get(c) != null) {
                return null;
            }
            final String value = constants.get(c) != null ? constants.get(c)
                : relation + "." + reference(labels.get(c));
            text.append(text.length() > 0 ? ", " : "").append(cast == null ? value : twice(value, cast))
                .append(" AS \"").append(labels.get(c)).append('"');
        }
        return text.toString();
    }

    private String keys() {
        return keys(all());
    }

    private String keys(final List<Integer> kept) {
        final List<String> keys = new ArrayList<>();
        for (final Integer at : kept) {
            keys.add(key(at.intValue()));
        }
        return String.join(", ", keys);
    }

    /** A grouping key: the one value a column holds, else its name as written bare. */
    private String key(final int column) {
        return constants.get(column) != null ? constants.get(column) : labels.get(column);
    }

    /**
     * A column's name as a qualified read writes it: bare where it could be written so, else between quotes.
     *
     * @param label the name
     * @return the text
     */
    static String reference(final String label) {
        return SqlIdentifiers.spellCanonical(label);
    }

    /**
     * The literal a FROM-less branch's item selects: a number or a text written out, alone or cast by {@code ::} to a
     * NUMBER or a VARCHAR, which the plan reads as the literal itself; null for any other item.
     */
    private static FrostlakeParser.LiteralExprContext heldLiteral(final ParseTree value) {
        if (value instanceof FrostlakeParser.LiteralExprContext) {
            return (FrostlakeParser.LiteralExprContext) value;
        }
        if (value instanceof FrostlakeParser.CastExpr2Context
                && ((FrostlakeParser.CastExpr2Context) value).expression()
                    instanceof FrostlakeParser.LiteralExprContext) {
            return (FrostlakeParser.LiteralExprContext) ((FrostlakeParser.CastExpr2Context) value).expression();
        }
        return null;
    }

    /**
     * The type an item holding a literal is planned as, by the item's own typing: a number or a text written out, a
     * whole number cast to a NUMBER of no scale and a text cast to a VARCHAR; null for another.
     */
    private static DataType literalType(final QueryExecutor executor, final FrostlakeParser.LiteralExprContext literal,
                                        final ParseTree item) {
        final FrostlakeParser.LiteralContext written = literal.literal();
        if (written.INTEGER_LITERAL() == null && written.FLOAT_LITERAL() == null
                && written.STRING_LITERAL() == null) {
            return null;
        }
        final DataType type;
        try {
            type = new ExpressionEvaluator(null, executor.getFunctionRegistry(), executor.getCatalog(), executor)
                .inferStaticType(ExpressionEvaluator.parse(ParseTreeText.getOriginalText((ParserRuleContext) item)));
        } catch (final RuntimeException untyped) {
            return null;
        }
        if (item == literal) {
            return type;
        }
        if (written.INTEGER_LITERAL() != null) {
            return exactNumber(type) && ((NumericType) type).getScale() == 0 ? type : null;
        }
        return written.STRING_LITERAL() != null && type instanceof StringType ? type : null;
    }

    /** A type as the plan writes it: NUMBER(p,s) for an exact number; null for an approximate one or a DECFLOAT. */
    private static String typeText(final DataType type) {
        if (exactNumber(type)) {
            final NumericType number = (NumericType) type;
            return "NUMBER(" + number.getPrecision() + "," + number.getScale() + ")";
        }
        return type == null || type instanceof NumericType ? null : type.toString();
    }

    private static boolean exactNumber(final DataType type) {
        return type instanceof NumericType && !NumericType.isApproximate(type) && !NumericType.isDecfloat(type);
    }

    private static boolean sameType(final DataType first, final DataType second) {
        final String one = typeText(first);
        return one != null && one.equals(typeText(second));
    }

    /** The type two columns take together: a NUMBER wide enough for both, or the one both hold; null for another. */
    private static DataType combinedType(final DataType first, final DataType second) {
        if (first == null || second == null) {
            return null;
        }
        if (sameType(first, second)) {
            return first;
        }
        if (!exactNumber(first) || !exactNumber(second)) {
            return null;
        }
        final NumericType one = (NumericType) first;
        final NumericType other = (NumericType) second;
        final int scale = Math.max(one.getScale(), other.getScale());
        final int whole = Math.max(one.getPrecision() - one.getScale(), other.getPrecision() - other.getScale());
        return new NumericType("NUMBER", Math.min(38, whole + scale), scale);
    }

    private static ParseTree valueOf(final ParseTree written) {
        return written instanceof FrostlakeParser.ValueExprContext
            ? ((FrostlakeParser.ValueExprContext) written).expression() : written;
    }
}
