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

import dev.frostlake.executor.commands.DataTypeParser;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.Row;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringType;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The columns of a derived relation a grouped query reads alone - {@code (SELECT column1 AS id FROM VALUES (5), (7))
 * fz} or {@code (SELECT f.value:id::INT AS id FROM TABLE(FLATTEN(…)) f) fz} - as the decorrelated plan prints them
 * once the relation is merged into the query: each column as its definition over the relation's own source
 * (live-verified):
 *
 * <ul>
 *   <li>a column of the VALUES list by the list's name - its alias, or VALUES - as {@code VALUES.COLUMN1}, a cast of
 *       one as {@code CAST(VALUES.COLUMN1 AS VARCHAR(134217728))} or {@code CAST(VALUES.COLUMN1 AS DATE)};</li>
 *   <li>a FLATTEN's path as {@code GET(F.VALUE, 'id')}; cast to a NUMBER(38,0) as {@code TO_NUMBER(GET(F.VALUE,
 *       'id'))}, to another precision and scale as {@code TO_NUMBER(GET(F.VALUE, 'id'), 10, 2)}, to a BOOLEAN as
 *       {@code CAST(GET(F.VALUE, 'b') AS BOOLEAN)}, to a FLOAT as {@code CAST(GET(F.VALUE, 'id') AS FLOAT)} and to a
 *       text as {@code CAST(GET(F.VALUE, 'id') AS VARCHAR(134217728))}.</li>
 * </ul>
 *
 * <p>The source column a definition reads names the column where the grouping refuses it: {@code [VALUES.COLUMN1]}
 * or {@code [F.VALUE]} is not a valid group by expression.
 */
final class DerivedColumnDefinitions {

    private static final String PLAN_TEXT = "VARCHAR(134217728)";
    private static final String VALUES_NAME = "VALUES";
    private static final String FLATTEN_VALUE = "VALUE";

    /** Each column's definition as the plan prints it, by {@code RELATION.COLUMN}; null where it is not printed. */
    private final Map<String, PrintedPlanValue> definitions = new HashMap<>();
    /** The source column each column's definition reads, by {@code RELATION.COLUMN}. */
    private final Map<String, String> sources = new HashMap<>();
    /** The terms a MAX or a MIN of a column takes out of itself (see extremeTerms), by {@code RELATION.COLUMN}. */
    private final Map<String, String[]> extremes = new HashMap<>();
    /** The columns defined by arithmetic over the list, by {@code RELATION.COLUMN}. */
    private final Set<String> arithmetic = new HashSet<>();
    /** Whether the list's statistics settle that a column holds no NULL (see listedNonNull), where known. */
    private final Map<String, Boolean> nonNull = new HashMap<>();

    private DerivedColumnDefinitions() {
    }

    /**
     * The definitions of the one derived relation a grouped query reads, or null where it reads no such relation.
     *
     * @param clause    the grouped query
     * @param relations the query's relations, by the names they go by
     * @return the definitions, or null
     */
    static DerivedColumnDefinitions of(final FrostlakeParser.SelectClauseContext clause,
                                       final Map<String, Table> relations) {
        final FrostlakeParser.TableExpressionContext from = clause.tableExpression();
        if (from == null || from.tableReference().size() != 1 || !from.joinClause().isEmpty() || relations == null
                || relations.size() != 1) {
            return null;
        }
        final FrostlakeParser.TableReferenceContext reference = from.tableReference(0);
        final FrostlakeParser.TableSourceContext source = reference.tableSource();
        if (source == null || source.selectStatement() == null || source.VALUES() != null || !plain(reference)
                || reference.identifierList() != null) {
            return null;
        }
        final String written = aliasOf(reference);
        final String alias = written != null ? written : relations.keySet().iterator().next();
        final Table derived = relations.values().iterator().next();
        final FrostlakeParser.SelectStatementContext body = source.selectStatement();
        if (derived == null || body.withClause() != null || body.selectOperand().size() != 1
                || body.selectOperand(0).selectClause() == null) {
            return null;
        }
        final FrostlakeParser.SelectClauseContext inner = body.selectOperand(0).selectClause();
        final FrostlakeParser.TableExpressionContext innerFrom = inner.tableExpression();
        if (inner.groupByClause() != null || inner.havingClause() != null || inner.qualifyClause() != null
                || inner.connectByClause() != null || inner.intoClause() != null || innerFrom == null
                || innerFrom.tableReference().size() != 1 || !innerFrom.joinClause().isEmpty()
                || !plain(innerFrom.tableReference(0))) {
            return null;
        }
        final FrostlakeParser.TableReferenceContext innerReference = innerFrom.tableReference(0);
        final FrostlakeParser.TableSourceContext innerSource = innerReference.tableSource();
        final String innerAlias = aliasOf(innerReference);
        final boolean values = innerSource.VALUES() != null && innerSource.LPAREN() == null
            && innerReference.identifierList() == null;
        final boolean flatten = innerSource.TABLE() != null && innerSource.expression() != null
            && innerSource.expression().getStart().getType() == FrostlakeParser.FLATTEN && innerAlias != null;
        if (!values && !flatten) {
            return null;
        }
        final String sourceName = (innerAlias != null ? innerAlias : VALUES_NAME).toUpperCase(Locale.ROOT);
        final DerivedColumnDefinitions found = new DerivedColumnDefinitions();
        final String relation = alias.toUpperCase(Locale.ROOT);
        for (final FrostlakeParser.SelectItemContext item : inner.selectList().selectItem()) {
            if (!(item instanceof FrostlakeParser.ExprItemContext)) {
                return null;
            }
            final FrostlakeParser.ExprItemContext named = (FrostlakeParser.ExprItemContext) item;
            final ParseTree value = valueOf(named.booleanExpr());
            String name = named.aliasName() != null ? ParseTreeText.getIdentifier(named.aliasName())
                : named.identifier() != null ? ParseTreeText.getIdentifier(named.identifier())
                : named.bareAliasWord() != null ? ParseTreeText.getIdentifier(named.bareAliasWord()) : null;
            if (name == null && value instanceof FrostlakeParser.QualifiedNameExprContext) {
                final String[] parts =
                    ParseTreeText.qualifiedNameParts(((FrostlakeParser.QualifiedNameExprContext) value).qualifiedName());
                name = parts[parts.length - 1];
            }
            if (name == null) {
                continue;
            }
            final TableColumn column = derived.findColumn(name);
            if (column == null) {
                return null;
            }
            final String key = relation + "." + column.getName().toUpperCase(Locale.ROOT);
            final List<String> read = new ArrayList<>();
            collectSourceColumns(value, sourceName, innerAlias, values, read);
            if (read.size() == 1) {
                found.sources.put(key, read.get(0));
            }
            final PrintedPlanValue printed = values ? found.listed(value, sourceName, innerAlias, column, innerSource, key)
                : flattened(value, innerAlias, column.getDataType());
            if (printed != null) {
                found.definitions.put(key, printed);
            }
        }
        return found;
    }

    /**
     * The definition of a column of the relation as the plan prints it, or null where it is not printed.
     *
     * @param column the column as {@code RELATION.COLUMN}
     * @return the definition, or null
     */
    PrintedPlanValue definition(final String column) {
        return definitions.get(column);
    }

    /**
     * The source column a column of the relation reads, as {@code VALUES.COLUMN1} or {@code F.VALUE}, or null.
     *
     * @param column the column as {@code RELATION.COLUMN}
     * @return the source column, or null
     */
    String sourceColumn(final String column) {
        return sources.get(column);
    }

    /**
     * Whether the list's statistics settle that a column of the relation - one of the VALUES list's columns as it
     * stands - holds no NULL (see {@link #listedNonNull}).
     *
     * @param column the column as {@code RELATION.COLUMN}
     * @return TRUE or FALSE, or null where it is not known
     */
    Boolean settlesNonNull(final String column) {
        return nonNull.get(column);
    }

    /** Whether a cell of constants folds to a value that is neither NULL nor a BOOLEAN, or null where it is none. */
    private static Boolean foldedNonNull(final FrostlakeParser.BooleanExprContext cell, final QueryExecutor executor) {
        final String text = ParseTreeText.getOriginalText(cell);
        try {
            if (!ConstantHaving.isConstant(ExpressionEvaluator.parse(text), executor.getFunctionRegistry())) {
                return null;
            }
            final Object value = new ExpressionEvaluator(null, executor.getFunctionRegistry(), executor.getCatalog(),
                executor).evaluate(text, new Row(new ArrayList<Object>()));
            return value != null && !(value instanceof Boolean);
        } catch (final RuntimeException faulted) {
            return null;
        }
    }

    /**
     * Whether the statistics of a VALUES list's column settle that it holds no NULL, read from the cells the list
     * writes: TRUE where every cell is a number or a text written out, FALSE where a cell is NULL or a BOOLEAN - whose
     * statistics settle nothing (live-verified) - and null where a cell is anything else.
     *
     * @param list  the list's source
     * @param index the column's position
     * @return TRUE or FALSE, or null where it is not known
     */
    static Boolean listedNonNull(final FrostlakeParser.TableSourceContext list, final int index) {
        return listedNonNull(list, index, null);
    }

    /**
     * {@link #listedNonNull(FrostlakeParser.TableSourceContext, int)} where a cell written as an expression of
     * constants is read as the value it folds to: {@code 5 + 1}, {@code ABS(5)} or {@code 'a' || 'b'} settle as a
     * number or a text written out does, {@code NULL + 1} or {@code NULLIF(5, 5)} as a NULL (live-verified).
     *
     * @param executor the executor folding a cell, or null to read literals alone
     */
    static Boolean listedNonNull(final FrostlakeParser.TableSourceContext list, final int index,
                                 final QueryExecutor executor) {
        if (list.valueTupleList() == null) {
            return null;
        }
        boolean known = true;
        for (final FrostlakeParser.ValueTupleContext tuple : list.valueTupleList().valueTuple()) {
            final List<FrostlakeParser.BooleanExprContext> cells = tuple.valueList().booleanExpr();
            if (index >= cells.size()) {
                return null;
            }
            ParseTree cell = valueOf(cells.get(index));
            if (cell instanceof FrostlakeParser.UnaryExprContext
                    && ((FrostlakeParser.UnaryExprContext) cell).op.getType() == FrostlakeParser.MINUS) {
                cell = valueOf(((FrostlakeParser.UnaryExprContext) cell).expression());
            }
            if (!(cell instanceof FrostlakeParser.LiteralExprContext)) {
                final Boolean folded = executor == null ? null : foldedNonNull(cells.get(index), executor);
                if (folded == null) {
                    known = false;
                } else if (!folded) {
                    return Boolean.FALSE;
                }
                continue;
            }
            final FrostlakeParser.LiteralContext literal = ((FrostlakeParser.LiteralExprContext) cell).literal();
            if (literal.NULL() != null || literal.TRUE() != null || literal.FALSE() != null) {
                return Boolean.FALSE;
            }
        }
        return known ? Boolean.TRUE : null;
    }

    /** A definition over the VALUES list: one of its columns, or a cast of one to a text. */
    private PrintedPlanValue listed(final ParseTree value, final String sourceName, final String innerAlias,
                                    final TableColumn column, final FrostlakeParser.TableSourceContext list,
                                    final String key) {
        if (value instanceof FrostlakeParser.QualifiedNameExprContext) {
            final int index = listIndex((FrostlakeParser.QualifiedNameExprContext) value, innerAlias);
            if (index < 0) {
                return null;
            }
            final Boolean settled = listedNonNull(list, index);
            if (settled != null) {
                nonNull.put(key, settled);
            }
            return new PrintedPlanValue(sourceName + ".COLUMN" + (index + 1), column.getDataType(), true, false,
                false, false, null);
        }
        final PrintedPlanValue computed = computed(value, sourceName, innerAlias, column, key);
        if (computed != null) {
            return computed;
        }
        final FrostlakeParser.ExpressionContext operand = castOperand(value);
        final String target = castName(castTarget(value));
        if (operand == null || target == null
                || !(valueOf(operand) instanceof FrostlakeParser.QualifiedNameExprContext)) {
            return null;
        }
        final int index = listIndex((FrostlakeParser.QualifiedNameExprContext) valueOf(operand), innerAlias);
        return index < 0 ? null : new PrintedPlanValue("CAST(" + sourceName + ".COLUMN" + (index + 1) + " AS "
            + target + ")", column.getDataType(), true, false, true, false, null);
    }

    /**
     * A definition computed over the list's columns: a minus as {@code NEGATE(VALUES.COLUMN1)}, arithmetic of columns
     * and numbers as written ({@code VALUES.COLUMN1 * 2}, {@code VALUES.COLUMN1 + VALUES.COLUMN1}); a column plus or
     * minus a number, or a number plus a column, is also recorded as the term a MAX or a MIN takes out of itself (see
     * {@link #extremeTerms}).
     */
    private PrintedPlanValue computed(final ParseTree value, final String sourceName, final String innerAlias,
                                      final TableColumn column, final String key) {
        if (value instanceof FrostlakeParser.UnaryExprContext
                && ((FrostlakeParser.UnaryExprContext) value).op.getType() == FrostlakeParser.MINUS) {
            final ParseTree operand = valueOf(((FrostlakeParser.UnaryExprContext) value).expression());
            final String term = listTerm(operand, sourceName, innerAlias);
            if (term == null || operand instanceof FrostlakeParser.LiteralExprContext) {
                return null;
            }
            arithmetic.add(key);
            return new PrintedPlanValue("NEGATE(" + term + ")", column.getDataType(), true, false, false, false, null);
        }
        final FrostlakeParser.ExpressionContext leftSide;
        final FrostlakeParser.ExpressionContext rightSide;
        final Token op;
        if (value instanceof FrostlakeParser.AdditiveExprContext) {
            leftSide = ((FrostlakeParser.AdditiveExprContext) value).expression(0);
            rightSide = ((FrostlakeParser.AdditiveExprContext) value).expression(1);
            op = ((FrostlakeParser.AdditiveExprContext) value).op;
        } else if (value instanceof FrostlakeParser.MultiplicativeExprContext) {
            leftSide = ((FrostlakeParser.MultiplicativeExprContext) value).expression(0);
            rightSide = ((FrostlakeParser.MultiplicativeExprContext) value).expression(1);
            op = ((FrostlakeParser.MultiplicativeExprContext) value).op;
        } else {
            return null;
        }
        if (op.getType() == FrostlakeParser.PIPE_PIPE) {
            return null;
        }
        final ParseTree left = valueOf(leftSide);
        final ParseTree right = valueOf(rightSide);
        final String leftTerm = listTerm(left, sourceName, innerAlias);
        final String rightTerm = listTerm(right, sourceName, innerAlias);
        final boolean leftNumber = left instanceof FrostlakeParser.LiteralExprContext;
        final boolean rightNumber = right instanceof FrostlakeParser.LiteralExprContext;
        if (leftTerm == null || rightTerm == null || leftNumber && rightNumber) {
            return null;
        }
        final String sign = " " + op.getText() + " ";
        arithmetic.add(key);
        if (value instanceof FrostlakeParser.AdditiveExprContext && rightNumber) {
            extremes.put(key, new String[] {leftTerm, "", sign + rightTerm});
        } else if (value instanceof FrostlakeParser.AdditiveExprContext && leftNumber
                && op.getType() == FrostlakeParser.PLUS) {
            extremes.put(key, new String[] {rightTerm, leftTerm + sign, ""});
        }
        return new PrintedPlanValue(leftTerm + sign + rightTerm, column.getDataType(), true, false, false, false,
            null);
    }

    /** A column of the list as the plan prints it, or a number as written; null for anything else. */
    private static String listTerm(final ParseTree term, final String sourceName, final String innerAlias) {
        if (term instanceof FrostlakeParser.QualifiedNameExprContext) {
            final int index = listIndex((FrostlakeParser.QualifiedNameExprContext) term, innerAlias);
            return index < 0 ? null : sourceName + ".COLUMN" + (index + 1);
        }
        if (term instanceof FrostlakeParser.LiteralExprContext
                && ((FrostlakeParser.LiteralExprContext) term).literal().INTEGER_LITERAL() != null) {
            return term.getText();
        }
        return null;
    }

    /**
     * How a MAX or a MIN of a column defined as a list's column plus or minus a number is printed once decorrelated:
     * the aggregate takes the column alone and the number stands beside it - {@code (MAX(VALUES.COLUMN1)) + 1},
     * {@code 1 + (MAX(VALUES.COLUMN1))} (live-verified).
     *
     * @param column the column as {@code RELATION.COLUMN}
     * @return the list's column, the text before the aggregate and the text after it; null where it is no such column
     */
    String[] extremeTerms(final String column) {
        return extremes.get(column);
    }

    /**
     * Whether a column is defined by arithmetic over the list, whose SUM the plan rewrites in a way not printed here.
     *
     * @param column the column as {@code RELATION.COLUMN}
     * @return true for such a column
     */
    boolean computedByArithmetic(final String column) {
        return arithmetic.contains(column);
    }

    /** A definition over a FLATTEN: a path into its VALUE, as it stands or cast to a whole number, a BOOLEAN or a text. */
    private static PrintedPlanValue flattened(final ParseTree value, final String innerAlias, final DataType type) {
        final String path = pathOf(value, innerAlias);
        if (path != null) {
            return new PrintedPlanValue(path, type, true, false, false, false, null);
        }
        final FrostlakeParser.ExpressionContext operand = castOperand(value);
        final DataType target = castTarget(value);
        final String castPath = operand == null ? null : pathOf(valueOf(operand), innerAlias);
        if (castPath == null || target == null) {
            return null;
        }
        if (target instanceof NumericType && !NumericType.isApproximate(target)) {
            // NUMBER(38,0) as the bare conversion, any other precision and scale as its arguments.
            // The bare one is added by an outer SUM as it stands; one with arguments is cast to its type first.
            final NumericType number = (NumericType) target;
            final boolean bare = number.getScale() == 0 && number.getPrecision() == 38;
            return new PrintedPlanValue("TO_NUMBER(" + castPath + (bare ? "" : ", " + number.getPrecision() + ", "
                + number.getScale()) + ")", type, true, false, false, bare, null);
        }
        final String name = castName(target);
        return name == null ? null : new PrintedPlanValue("CAST(" + castPath + " AS " + name + ")", type, true, false,
            true, false, null);
    }

    /** The type a cast names as the plan prints it - a text, a BOOLEAN, a FLOAT or a DATE - or null for another. */
    private static String castName(final DataType target) {
        if (target instanceof StringType) {
            return PLAN_TEXT;
        }
        if (target instanceof BooleanType) {
            return "BOOLEAN";
        }
        final String name = SqlTypeNames.canonical(target);
        return "FLOAT".equals(name) || "DATE".equals(name) ? name : null;
    }

    /** A path of one key into the FLATTEN's VALUE as {@code GET(F.VALUE, 'key')}, or null for any other value. */
    private static String pathOf(final ParseTree value, final String innerAlias) {
        if (!(value instanceof FrostlakeParser.ObjectAccessExprContext)) {
            return null;
        }
        final FrostlakeParser.ObjectAccessExprContext access = (FrostlakeParser.ObjectAccessExprContext) value;
        if (access.variantPathKey().size() != 1 || access.variantPathKey(0).identifier() == null
                || access.variantPathKey(0).getText().startsWith("\"")
                || !(valueOf(access.expression()) instanceof FrostlakeParser.QualifiedNameExprContext)) {
            return null;
        }
        final String[] parts = ParseTreeText.qualifiedNameParts(
            ((FrostlakeParser.QualifiedNameExprContext) valueOf(access.expression())).qualifiedName());
        if (!FLATTEN_VALUE.equalsIgnoreCase(parts[parts.length - 1]) || parts.length > 2
                || parts.length == 2 && !parts[0].equalsIgnoreCase(innerAlias)) {
            return null;
        }
        return "GET(" + innerAlias.toUpperCase(Locale.ROOT) + "." + FLATTEN_VALUE + ", '"
            + access.variantPathKey(0).getText() + "')";
    }

    /** The source columns a definition reads, as {@code VALUES.COLUMN1} or {@code F.VALUE}, once each. */
    private static void collectSourceColumns(final ParseTree node, final String sourceName, final String innerAlias,
                                             final boolean values, final List<String> into) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            final FrostlakeParser.QualifiedNameExprContext name = (FrostlakeParser.QualifiedNameExprContext) node;
            final String read;
            if (values) {
                final int index = listIndex(name, innerAlias);
                read = index < 0 ? null : sourceName + ".COLUMN" + (index + 1);
            } else {
                final String[] parts = ParseTreeText.qualifiedNameParts(name.qualifiedName());
                read = parts.length <= 2 && (parts.length == 1 || parts[0].equalsIgnoreCase(innerAlias))
                    ? sourceName + "." + parts[parts.length - 1].toUpperCase(Locale.ROOT) : null;
            }
            if (read != null && !into.contains(read)) {
                into.add(read);
            }
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectSourceColumns(node.getChild(i), sourceName, innerAlias, values, into);
        }
    }

    /** The position of the VALUES list's column a name reads, COLUMN1 as 0, or -1 for any other name. */
    private static int listIndex(final FrostlakeParser.QualifiedNameExprContext name, final String innerAlias) {
        final String[] parts = ParseTreeText.qualifiedNameParts(name.qualifiedName());
        if (parts.length > 2 || parts.length == 2 && (innerAlias == null || !parts[0].equalsIgnoreCase(innerAlias))) {
            return -1;
        }
        final String column = parts[parts.length - 1].toUpperCase(Locale.ROOT);
        if (!column.startsWith("COLUMN") || column.length() == "COLUMN".length()) {
            return -1;
        }
        int index = 0;
        for (int i = "COLUMN".length(); i < column.length(); i++) {
            final char digit = column.charAt(i);
            if (digit < '0' || digit > '9' || index > 100000) {
                return -1;
            }
            index = index * 10 + (digit - '0');
        }
        return index - 1;
    }

    private static FrostlakeParser.ExpressionContext castOperand(final ParseTree value) {
        if (value instanceof FrostlakeParser.CastExpr2Context) {
            return ((FrostlakeParser.CastExpr2Context) value).expression();
        }
        if (value instanceof FrostlakeParser.CastExprContext && ((FrostlakeParser.CastExprContext) value).FIELDS() == null) {
            return ((FrostlakeParser.CastExprContext) value).expression();
        }
        return null;
    }

    private static DataType castTarget(final ParseTree value) {
        try {
            if (value instanceof FrostlakeParser.CastExpr2Context) {
                final FrostlakeParser.CastExpr2Context cast = (FrostlakeParser.CastExpr2Context) value;
                return DataTypeParser.parse(cast.dataTypeName(), cast.typeParameters(),
                    DataTypeParser.CAST_STRING_DEFAULT);
            }
            if (value instanceof FrostlakeParser.CastExprContext) {
                final FrostlakeParser.CastExprContext cast = (FrostlakeParser.CastExprContext) value;
                return DataTypeParser.parse(cast.dataTypeName(), cast.typeParameters(),
                    DataTypeParser.CAST_STRING_DEFAULT);
            }
        } catch (final RuntimeException unknownType) {
            return null;
        }
        return null;
    }

    /** A relation reference read as it stands: no LATERAL, PIVOT, UNPIVOT, MATCH_RECOGNIZE or SAMPLE. */
    private static boolean plain(final FrostlakeParser.TableReferenceContext reference) {
        return reference.tableSource() != null && reference.LATERAL() == null && reference.pivotClause() == null
            && reference.unpivotClause() == null && reference.matchRecognizeClause() == null
            && reference.sampleClause() == null;
    }

    private static String aliasOf(final FrostlakeParser.TableReferenceContext reference) {
        if (reference.aliasName() != null) {
            return ParseTreeText.getIdentifier(reference.aliasName());
        }
        return reference.nonJoinKeywordIdentifier() != null && reference.nonJoinKeywordIdentifier().identifier() != null
            ? ParseTreeText.getIdentifier(reference.nonJoinKeywordIdentifier().identifier()) : null;
    }

    private static ParseTree valueOf(final ParseTree written) {
        return written instanceof FrostlakeParser.ValueExprContext
            ? ((FrostlakeParser.ValueExprContext) written).expression() : written;
    }
}
