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
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.LiteralType;
import dev.frostlake.executor.expressions.UnaryOperationExpression;
import dev.frostlake.executor.expressions.UnaryOperator;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.functions.aggregate.ConstantArgumentTexts;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DecimalText;
import dev.frostlake.types.NumericType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.antlr.v4.runtime.tree.ParseTree;

/**
 * The compile-time rule of the count MAX_BY and MIN_BY take as their third argument, judged over the whole
 * statement before a row is read, for the aggregate and the window call alike. The count is converted to
 * NUMBER(18,0) and must fold to a constant from 1 to 1000. The sentences, all live-verified:
 *
 * <pre>
 *   MAX_BY(x, o, 0)       Invalid value [0] for function 'MAX_BY', parameter 3: N (last argument) must be a positive number
 *   MAX_BY(x, o, 1001)    Invalid value [1001] for function 'MAX_BY', parameter 3: N (last argument) cannot be larger than 1000
 *   MAX_BY(x, o, NULL)    Invalid value [null] for function 'MAX_BY', parameter 3: N (last argument) must be non-null
 *   MAX_BY(x, o, g)       argument 3 to function MAX_BY needs to be constant, found 'T.G'
 *   MAX_BY(x, o, 2.5)     argument 3 to function MAX_BY needs to be constant, found 'CAST(2.5 AS NUMBER(18,0))'
 *   MAX_BY(x, o, 'abc')   argument 3 to function MAX_BY needs to be constant, found 'TO_NUMBER('abc', 18, 0)'
 *   MAX_BY(x, o, TRUE)    Invalid argument types for function 'MAX_BY': (VARCHAR(16777216), NUMBER(38,0), BOOLEAN)
 * </pre>
 *
 * <p>A whole number passes in any literal spelling ({@code 2.0}, {@code 1e3}, {@code (2)}, {@code -(-2)}); a
 * quoted number and a FLOAT cast of a number are rounded half away from zero ({@code '2.5'} and
 * {@code 2.5::FLOAT} are 3); a fraction written as a number literal, a cast to a NUMBER and any other
 * computation are no constant.
 */
final class MaxByCountRule {

    /** The largest count a call may ask for. */
    private static final BigDecimal LIMIT = new BigDecimal(1000);

    private final QueryExecutor queryExecutor;
    private final FunctionRegistry functionRegistry;
    private final Catalog catalog;

    /**
     * @param queryExecutor    the executor that plans a subquery a refusal re-prints
     * @param functionRegistry the functions the calls name
     * @param catalog          the catalog the names resolve in
     */
    MaxByCountRule(final QueryExecutor queryExecutor, final FunctionRegistry functionRegistry,
                   final Catalog catalog) {
        this.queryExecutor = queryExecutor;
        this.functionRegistry = functionRegistry;
        this.catalog = catalog;
    }

    /**
     * Walks a statement's tree and refuses the first MAX_BY or MIN_BY call whose count breaks the rule.
     *
     * @param node any parse-tree node; the walk covers the statement
     * @param table the leading relation, for echoing a column the way the plan names it
     * @param aliasToTable the FROM's alias map, or null
     * @param allTables every relation in the FROM, or null
     */
    void validate(final ParseTree node, final Table table, final Map<String, Table> aliasToTable,
                  final List<Table> allTables) {
        if (node == null) {
            return;
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            judge((FrostlakeParser.FunctionCallExprContext) node, table, aliasToTable, allTables);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            validate(node.getChild(i), table, aliasToTable, allTables);
        }
    }

    private void judge(final FrostlakeParser.FunctionCallExprContext call, final Table table,
                       final Map<String, Table> aliasToTable, final List<Table> allTables) {
        final String name = SqlIdentifiers.canonicalText(call.functionName().getText()).toUpperCase(Locale.ROOT);
        if (!"MAX_BY".equals(name) && !"MIN_BY".equals(name) || call.functionArgList() == null) {
            return;
        }
        final List<FrostlakeParser.BooleanExprContext> args =
            ParseTreeText.functionBooleanArgs(call.functionArgList());
        if (args.size() != 3 || call.functionArgList().functionArg().size() != 3) {
            return;
        }
        final String written = ConstantArgumentTexts.unwrap(ParseTreeText.getOriginalText(args.get(2)));
        final Expression parsed = parsed(written);
        if (parsed instanceof LiteralExpression && ((LiteralExpression) parsed).getType() == LiteralType.NULL) {
            refuse(invalidValue("null", name, "must be non-null"));
        }
        if (parsed instanceof LiteralExpression && ((LiteralExpression) parsed).getType() == LiteralType.BOOLEAN) {
            refuseArgumentTypes(call, name, args, table, aliasToTable, allTables);
        }
        final BigDecimal value = folded(written);
        if (value == null) {
            final String found = ConstantArgumentTexts.isStringLiteral(written)
                ? "TO_NUMBER(" + written + ", 18, 0)"
                : new PlanEcho(table, aliasToTable, allTables, functionRegistry, catalog, queryExecutor)
                    .printPlanned(args.get(2));
            // A cast to a NUMBER with a scale is converted once more, to the count's own NUMBER(18,0).
            final DataType target = parsed instanceof CastExpression
                ? ((CastExpression) parsed).getTargetDataType() : null;
            final boolean scaled = target instanceof NumericType && ((NumericType) target).getScale() > 0;
            refuse("argument 3 to function " + name + " needs to be constant, found '"
                + (scaled ? "CAST(" + found + " AS NUMBER(18,0))" : found) + "'");
        }
        if (value.stripTrailingZeros().scale() > 0) {
            refuse("argument 3 to function " + name + " needs to be constant, found 'CAST("
                + value.toPlainString() + " AS NUMBER(18,0))'");
        }
        final String digits = value.signum() == 0 ? "0" : value.stripTrailingZeros().toPlainString();
        if (value.signum() <= 0) {
            refuse(invalidValue(digits, name, "must be a positive number"));
        }
        if (value.compareTo(LIMIT) > 0) {
            refuse(invalidValue(digits, name, "cannot be larger than 1000"));
        }
    }

    /**
     * The count a call that passed the rule asks for.
     *
     * @param text the third argument's text
     * @return the count, from 1 to 1000
     */
    static int count(final String text) {
        final BigDecimal value = folded(ConstantArgumentTexts.unwrap(text));
        if (value == null || value.stripTrailingZeros().scale() > 0) {
            throw new RuntimeException(SqlCompilationError.of(
                "argument 3 to function MAX_BY needs to be constant, found '" + text + "'"));
        }
        return value.intValue();
    }

    /**
     * The value a count folds to, or null when it is no constant. A quoted number and a FLOAT cast are rounded; a
     * number literal keeps its fraction, which the rule refuses.
     */
    private static BigDecimal folded(final String written) {
        if (written == null) {
            return null;
        }
        final BigDecimal literal = ConstantArgumentTexts.numericConstant(written);
        if (literal != null) {
            return ConstantArgumentTexts.isStringLiteral(written)
                ? literal.setScale(0, RoundingMode.HALF_UP) : literal;
        }
        return folded(parsed(written));
    }

    private static BigDecimal folded(final Expression expression) {
        if (expression instanceof LiteralExpression) {
            final LiteralExpression literal = (LiteralExpression) expression;
            if (literal.getType() != LiteralType.INTEGER && literal.getType() != LiteralType.DECIMAL
                    || !(literal.getValue() instanceof Number)) {
                return null;
            }
            try {
                return DecimalText.parse(literal.getValue().toString());
            } catch (final NumberFormatException notANumber) {
                return null;
            }
        }
        if (expression instanceof UnaryOperationExpression) {
            final UnaryOperationExpression unary = (UnaryOperationExpression) expression;
            final BigDecimal operand = folded(unary.getOperand());
            if (operand == null) {
                return null;
            }
            if (unary.getOperator() == UnaryOperator.NEGATE) {
                return operand.negate();
            }
            return unary.getOperator() == UnaryOperator.PLUS ? operand : null;
        }
        if (expression instanceof CastExpression) {
            final CastExpression cast = (CastExpression) expression;
            final BigDecimal operand = folded(cast.getExpression());
            return operand != null && NumericType.isApproximateName(cast.getTargetBaseName())
                ? operand.setScale(0, RoundingMode.HALF_UP) : null;
        }
        return null;
    }

    private static Expression parsed(final String written) {
        if (written == null) {
            return null;
        }
        try {
            return ExpressionEvaluator.parse(written);
        } catch (final RuntimeException notAnExpression) {
            return null;
        }
    }

    /** A BOOLEAN count is an argument-type complaint naming all three types, at the call's place. */
    private void refuseArgumentTypes(final FrostlakeParser.FunctionCallExprContext call, final String name,
                                     final List<FrostlakeParser.BooleanExprContext> args, final Table table,
                                     final Map<String, Table> aliasToTable, final List<Table> allTables) {
        final ExpressionEvaluator typer = new ExpressionEvaluator(table, functionRegistry, catalog, queryExecutor);
        if (allTables != null && !allTables.isEmpty()) {
            typer.setMultiTableContext(aliasToTable, allTables);
        }
        final StringBuilder types = new StringBuilder();
        for (final FrostlakeParser.BooleanExprContext arg : args) {
            final String type;
            try {
                type = typer.argumentTypeText(ExpressionEvaluator.parse(ParseTreeText.getOriginalText(arg)));
            } catch (final RuntimeException notTypeable) {
                return;
            }
            if (type == null) {
                return;
            }
            types.append(types.length() == 0 ? "" : ", ").append(type);
        }
        throw new RuntimeException(SqlCompilationError.at(call.getStart().getLine(),
            call.getStart().getCharPositionInLine(),
            "Invalid argument types for function '" + name + "': (" + types + ")"));
    }

    /**
     * What DISTINCT compares in the counted form: the value and its key together, a NULL value equal to another, so
     * MIN_BY(DISTINCT v, k, 10) over (1, 1), (1, 1), (1, 3) is [1,1] (live-verified).
     *
     * @param value the row's value
     * @param key the row's key
     * @return the pair's comparison key
     */
    static List<Object> distinctPair(final Object value, final Object key) {
        final List<Object> pair = new ArrayList<>(2);
        pair.add(value == null ? null : ValueComparisons.normalizeValueForDistinct(value));
        pair.add(key == null ? null : ValueComparisons.normalizeValueForDistinct(key));
        return pair;
    }

    private static String invalidValue(final String echo, final String name, final String sentence) {
        return "Invalid value [" + echo + "] for function '" + name + "', parameter 3: N (last argument) " + sentence;
    }

    private static void refuse(final String detail) {
        throw new RuntimeException(SqlCompilationError.of(detail));
    }
}
