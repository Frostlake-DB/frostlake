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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.expressions.AstPrinterVisitor;
import dev.frostlake.executor.expressions.BinaryOperationExpression;
import dev.frostlake.executor.expressions.CastExpression;
import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.LiteralType;

import java.util.List;
import java.util.Locale;

/**
 * Spells an expression of a materialized view's definition as the refusals of that definition name it
 * (live-verified): every column named by its relation, {@code T.B} or {@code X.B} under an alias, a column written
 * without a qualifier taking the one relation the view reads, an operation written out flat, {@code T.A + 1}, a cast
 * as {@code CAST(T.B AS VARIANT)} and a function's name upper-cased.
 */
final class MaterializedViewExpressionText extends AstPrinterVisitor {

    /** The name the view's one relation is read by: its alias, else its own name. */
    private final String owner;

    /**
     * @param owner the name a column written without a qualifier is spelled under, or null for none
     */
    MaterializedViewExpressionText(final String owner) {
        this.owner = owner;
    }

    /**
     * An expression as the refusal spells it.
     *
     * @param expression the expression
     * @return the text
     */
    String spell(final Expression expression) {
        return expression.accept(this);
    }

    @Override
    public String visitColumnReference(final ColumnReferenceExpression expr) {
        final String name = SqlIdentifiers.spellCanonical(expr.getColumnName());
        if (!expr.isQualified()) {
            return owner == null ? name : owner + "." + name;
        }
        final String qualifier = expr.getTableName();
        final int dot = qualifier.lastIndexOf('.');
        return SqlIdentifiers.spellCanonical(dot < 0 ? qualifier : qualifier.substring(dot + 1)) + "." + name;
    }

    @Override
    public String visitLiteral(final LiteralExpression expr) {
        if (expr.getType() == LiteralType.STRING) {
            return "'" + String.valueOf(expr.getValue()).replace("'", "''") + "'";
        }
        return super.visitLiteral(expr);
    }

    @Override
    public String visitBinaryOperation(final BinaryOperationExpression expr) {
        final String symbol = symbolOf(expr);
        if (symbol == null) {
            return super.visitBinaryOperation(expr);
        }
        return operand(expr.getLeft()) + " " + symbol + " " + operand(expr.getRight());
    }

    @Override
    public String visitCast(final CastExpression expr) {
        return "CAST(" + spell(expr.getExpression()) + " AS " + expr.getTargetType().toUpperCase(Locale.ROOT) + ")";
    }

    @Override
    public String visitFunctionCall(final FunctionCallExpression expr) {
        if (expr.getFunctionName() == null) {
            return super.visitFunctionCall(expr);
        }
        final StringBuilder text = new StringBuilder(expr.getFunctionName().toUpperCase(Locale.ROOT)).append('(');
        if (expr.isDistinct()) {
            text.append("DISTINCT ");
        }
        if (expr.isStar()) {
            text.append('*');
        } else {
            final List<Expression> arguments = expr.getArguments();
            for (int i = 0; i < arguments.size(); i++) {
                text.append(i > 0 ? ", " : "").append(spell(arguments.get(i)));
            }
        }
        return text.append(')').toString();
    }

    /** An operand of an operation: another operation in parentheses, anything else as it is. */
    private String operand(final Expression operand) {
        final String text = spell(operand);
        return operand instanceof BinaryOperationExpression && symbolOf((BinaryOperationExpression) operand) != null
            ? "(" + text + ")" : text;
    }

    /** The operator of an arithmetic, concatenation or comparison, or null for any other. */
    private static String symbolOf(final BinaryOperationExpression expr) {
        switch (expr.getOperator()) {
            case ADD:
                return "+";
            case SUBTRACT:
                return "-";
            case MULTIPLY:
                return "*";
            case DIVIDE:
                return "/";
            case MODULO:
                return "%";
            case CONCAT:
                return "||";
            case EQUAL:
                return "=";
            case NOT_EQUAL:
                return "!=";
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
}
