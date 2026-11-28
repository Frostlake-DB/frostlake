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

import dev.frostlake.executor.expressions.BinaryOperationExpression;
import dev.frostlake.executor.expressions.BinaryOperator;
import dev.frostlake.executor.expressions.CaseExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.LiteralType;
import dev.frostlake.executor.expressions.ParsedEchoPrinter;
import dev.frostlake.executor.expressions.UnaryOperationExpression;
import dev.frostlake.executor.expressions.UnaryOperator;
import dev.frostlake.executor.expressions.WhenClause;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * How a VALUES item refused for a drawing function (RANDOM, UNIFORM, NORMAL, ZIPF) echoes the calls and operators
 * the plan rewrites around it (live-verified):
 *
 * <pre>
 *   RANDOM() + 1                    (CAST(RANDOM() AS NUMBER(19,0))) + 1     so in ABS, ROUND and a negation
 *   UNIFORM(1, 10, 5) + 1           (CAST(UNIFORM(1, 10, 5) AS NUMBER(2,0))) + 1
 *   NORMAL(0, 1, 5) + 1             (NORMAL(0.0, 1.0, 5)) + 1.0            a FLOAT parameter or operation
 *   ZIPF(2, 10, 5)                  ZIPF(2.0, 10, 5)
 *   CONCAT('p', RANDOM())           CONCAT('p', CAST(RANDOM() AS VARCHAR(134217728)))
 *   RANDOM() || 'x', TO_VARCHAR(RANDOM())   the same cast to text
 *   IFF(TRUE, RANDOM(), 1)          RANDOM()                                a settled condition folds
 *   NVL(RANDOM(), 1)                RANDOM()                                RANDOM is never NULL
 *   CASE WHEN TRUE THEN RANDOM() END    ENSURE_NULLABLE(RANDOM())
 * </pre>
 *
 * <p>FLOOR, HASH, GREATEST and a generator argument keep RANDOM bare.
 */
final class ValuesDrawEcho {

    private static final String VARCHAR_CAST = " AS VARCHAR(134217728))";

    private static final String RANDOM_CAST = " AS NUMBER(19,0))";

    /** The calls whose argument RANDOM is cast to its NUMBER(19,0). */
    private static final Set<String> RANDOM_CASTING = Set.of("ABS", "ROUND");

    /** The drawing calls a text reads through a cast. */
    private static final Set<String> NUMBER_DRAWS = Set.of("RANDOM", "UNIFORM", "NORMAL", "ZIPF");

    /** The arithmetic operators. */
    private static final Set<BinaryOperator> ARITHMETIC = Set.of(BinaryOperator.ADD, BinaryOperator.SUBTRACT,
        BinaryOperator.MULTIPLY, BinaryOperator.DIVIDE, BinaryOperator.MODULO);

    private final ExpressionEvaluator typer;

    ValuesDrawEcho(final ExpressionEvaluator typer) {
        this.typer = typer;
    }

    /**
     * @param call    a call
     * @param printer the printer
     * @return the call's text, or null to print it otherwise
     */
    String spell(final FunctionCallExpression call, final ParsedEchoPrinter printer) {
        if (call.getFunctionName() == null || call.getNameExpression() != null
                || call.getNameParts() != null && call.getNameParts().size() > 1) {
            return null;
        }
        final String name = call.getFunctionName().toUpperCase(Locale.ROOT);
        final List<Expression> arguments = call.getArguments();
        if ("IFF".equals(name) && arguments.size() == 3) {
            final Boolean settled = settled(arguments.get(0));
            return settled == null ? null : printer.printed(arguments.get(settled.booleanValue() ? 1 : 2));
        }
        if (("NVL".equals(name) || "COALESCE".equals(name)) && arguments.size() >= 2
                && isCall(arguments.get(0), "RANDOM")) {
            return printer.printed(arguments.get(0));
        }
        if ((name.equals("TO_VARCHAR") || name.equals("TO_CHAR")) && arguments.size() == 1
                && isNumberDraw(arguments.get(0))) {
            return "CAST(" + printer.printed(arguments.get(0)) + VARCHAR_CAST;
        }
        if (RANDOM_CASTING.contains(name) && !arguments.isEmpty() && isCall(arguments.get(0), "RANDOM")) {
            return name + "(" + randomCast(arguments.get(0), printer) + rest(arguments, 1, printer) + ")";
        }
        if ("CONCAT".equals(name) && holdsNumberDraw(arguments)) {
            final StringBuilder text = new StringBuilder("CONCAT(");
            for (int i = 0; i < arguments.size(); i++) {
                final Expression argument = arguments.get(i);
                final String printed = printer.printed(argument);
                text.append(i > 0 ? ", " : "").append(isNumberDraw(argument)
                    ? "CAST(" + printed + VARCHAR_CAST : printed);
            }
            return text.append(')').toString();
        }
        final int floats = "NORMAL".equals(name) ? 2 : "ZIPF".equals(name) ? 1 : 0;
        if (floats == 0 || arguments.size() != 3) {
            return null;
        }
        final StringBuilder text = new StringBuilder(name).append('(');
        for (int i = 0; i < arguments.size(); i++) {
            final String number = i < floats ? floatLiteral(arguments.get(i)) : null;
            text.append(i > 0 ? ", " : "").append(number != null ? number : printer.printed(arguments.get(i)));
        }
        return text.append(')').toString();
    }

    /**
     * @param operation the operation
     * @param operand   one of its operands
     * @param printer   the printer
     * @return the operand's text beside its operator, or null to print it otherwise
     */
    String spellOperand(final BinaryOperationExpression operation, final Expression operand,
                        final ParsedEchoPrinter printer) {
        if (operation.getOperator() == BinaryOperator.CONCAT) {
            return isNumberDraw(operand) ? "(CAST(" + printer.printed(operand) + VARCHAR_CAST + ")" : null;
        }
        if (!ARITHMETIC.contains(operation.getOperator())) {
            return null;
        }
        if (isCall(operand, "RANDOM")) {
            return "(" + randomCast(operand, printer) + ")";
        }
        final String uniform = uniformCast(operand, printer);
        if (uniform != null) {
            return "(" + uniform + ")";
        }
        if (holdsCall(operation, "NORMAL") && isFloat(operation)) {
            // A FLOAT operation reads its number literal as a FLOAT.
            return floatLiteral(operand);
        }
        return null;
    }

    /**
     * @param expression a cast, an operation or a CASE
     * @param printer    the printer
     * @return its text, or null to print it otherwise
     */
    String spellConstant(final Expression expression, final ParsedEchoPrinter printer) {
        if (expression instanceof UnaryOperationExpression) {
            final UnaryOperationExpression unary = (UnaryOperationExpression) expression;
            return unary.getOperator() == UnaryOperator.NEGATE && isCall(unary.getOperand(), "RANDOM")
                ? "NEGATE(" + randomCast(unary.getOperand(), printer) + ")" : null;
        }
        if (!(expression instanceof CaseExpression)) {
            return null;
        }
        final CaseExpression expr = (CaseExpression) expression;
        for (final WhenClause clause : expr.getWhenClauses()) {
            if (clause.isOperandMatch()) {
                return null;
            }
            final Boolean settled = settled(clause.getCondition());
            if (settled == null) {
                return null;
            }
            if (settled.booleanValue()) {
                if (expr.getElseExpression() != null) {
                    return printer.printed(clause.getResult());
                }
                // Without an ELSE the CASE may answer NULL, which the plan keeps.
                final String nullable = "ENSURE_NULLABLE(" + printer.printed(clause.getResult()) + ")";
                return typeOf(clause.getResult()) instanceof StringType ? "CAST(" + nullable + VARCHAR_CAST : nullable;
            }
        }
        return expr.getElseExpression() == null ? null : printer.printed(expr.getElseExpression());
    }

    /** RANDOM cast to its NUMBER(19,0). */
    private static String randomCast(final Expression random, final ParsedEchoPrinter printer) {
        return "CAST(" + printer.printed(random) + RANDOM_CAST;
    }

    /** UNIFORM of whole bounds cast to the NUMBER of the wider bound's digits, or null for another operand. */
    private static String uniformCast(final Expression operand, final ParsedEchoPrinter printer) {
        if (!isCall(operand, "UNIFORM") || ((FunctionCallExpression) operand).getArguments().size() != 3) {
            return null;
        }
        final List<Expression> arguments = ((FunctionCallExpression) operand).getArguments();
        final BigDecimal low = wholeLiteral(arguments.get(0));
        final BigDecimal high = wholeLiteral(arguments.get(1));
        if (low == null || high == null) {
            return null;
        }
        final int digits = Math.max(low.abs().toPlainString().length(), high.abs().toPlainString().length());
        return "CAST(" + printer.printed(operand) + " AS NUMBER(" + digits + ",0))";
    }

    private static BigDecimal wholeLiteral(final Expression expression) {
        if (!(expression instanceof LiteralExpression)
                || ((LiteralExpression) expression).getType() != LiteralType.INTEGER) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(((LiteralExpression) expression).getValue()));
        } catch (final NumberFormatException notNumber) {
            return null;
        }
    }

    /** A number literal, a negated one included, as a FLOAT prints it; null for any other expression. */
    private static String floatLiteral(final Expression expression) {
        if (expression instanceof UnaryOperationExpression
                && ((UnaryOperationExpression) expression).getOperator() == UnaryOperator.NEGATE) {
            final String operand = floatLiteral(((UnaryOperationExpression) expression).getOperand());
            return operand == null ? null : "-" + operand;
        }
        if (!(expression instanceof LiteralExpression)) {
            return null;
        }
        final LiteralExpression literal = (LiteralExpression) expression;
        if (literal.getType() != LiteralType.INTEGER && literal.getType() != LiteralType.DECIMAL) {
            return null;
        }
        final BigDecimal number;
        try {
            number = new BigDecimal(String.valueOf(literal.getValue()));
        } catch (final NumberFormatException notNumber) {
            return null;
        }
        return number.scale() <= 0 ? number.toBigInteger() + ".0" : number.toPlainString();
    }

    private static String rest(final List<Expression> arguments, final int from, final ParsedEchoPrinter printer) {
        final StringBuilder text = new StringBuilder();
        for (int i = from; i < arguments.size(); i++) {
            text.append(", ").append(printer.printed(arguments.get(i)));
        }
        return text.toString();
    }

    private static boolean holdsNumberDraw(final List<Expression> arguments) {
        for (final Expression argument : arguments) {
            if (isNumberDraw(argument)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNumberDraw(final Expression expression) {
        if (!(expression instanceof FunctionCallExpression)) {
            return false;
        }
        final FunctionCallExpression call = (FunctionCallExpression) expression;
        return call.getNameExpression() == null && call.getFunctionName() != null
            && NUMBER_DRAWS.contains(call.getFunctionName().toUpperCase(Locale.ROOT));
    }

    private static boolean isCall(final Expression expression, final String name) {
        return expression instanceof FunctionCallExpression
            && ((FunctionCallExpression) expression).getNameExpression() == null
            && name.equalsIgnoreCase(((FunctionCallExpression) expression).getFunctionName());
    }

    private static boolean holdsCall(final BinaryOperationExpression operation, final String name) {
        return isCall(operation.getLeft(), name) || isCall(operation.getRight(), name);
    }

    /** A condition the statement settles while it compiles: TRUE, FALSE, or null where it is not settled. */
    private Boolean settled(final Expression condition) {
        if (condition instanceof LiteralExpression && ((LiteralExpression) condition).getValue() instanceof Boolean) {
            return (Boolean) ((LiteralExpression) condition).getValue();
        }
        try {
            return typer.settledCondition(condition);
        } catch (final RuntimeException unsettled) {
            return null;
        }
    }

    private boolean isFloat(final Expression expression) {
        final DataType type = typeOf(expression);
        return type instanceof NumericType && NumericType.isApproximate(type);
    }

    private DataType typeOf(final Expression expression) {
        try {
            return typer.inferStaticType(expression);
        } catch (final RuntimeException untyped) {
            return null;
        }
    }
}
