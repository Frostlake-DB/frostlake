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

import java.util.List;

/**
 * How a constant argument's arithmetic over a block's exact NUMBER variable keeps the variable's type (all
 * live-verified). The plan stores an exact number in one of five widths - one, two, four, eight or sixteen bytes,
 * for up to 2, 4, 9, 18 and 38 digits - and an operation over such a variable keeps, around each typed constant
 * beside it, the cast to the constant's own type wherever that type is stored at another width than the operation
 * computes in: {@code n + 1} over a NUMBER(10,2) is {@code 2 + 1}, a NUMBER(11,2) sum stored as the variable is,
 * where over a NUMBER(9,2) it is {@code (CAST(2 AS NUMBER(9,2))) + 1}, a NUMBER(10,2) sum stored wider. A call
 * over numbers written out that the plan does not fold - {@code ROUND(1.26, 1)}, {@code CEIL(1.5)},
 * {@code SIGN(5)} - is such a typed constant too, where one it folds to its value prints as written.
 */
final class BlockConstantTyping {

    private static final int ONE_BYTE_DIGITS = 2;
    private static final int TWO_BYTE_DIGITS = 4;
    private static final int FOUR_BYTE_DIGITS = 9;
    private static final int EIGHT_BYTE_DIGITS = 18;

    private BlockConstantTyping() {
    }

    /**
     * Whether an expression holds a block's folded exact NUMBER variable, under operators, casts and calls.
     *
     * @param expression the expression
     * @return whether a variable's binding is in it
     */
    static boolean holdsBinding(final Expression expression) {
        return holds(expression, true);
    }

    /**
     * Whether an expression holds a typed constant the plan carries - a block's variable or any other folded exact
     * constant, such as {@code TO_NUMBER('1', 5, 0)} - under operators, casts and calls.
     *
     * @param expression the expression
     * @return whether such a constant is in it
     */
    static boolean holdsTypedConstant(final Expression expression) {
        return holds(expression, false);
    }

    private static boolean holds(final Expression expression, final boolean bindingOnly) {
        if (expression instanceof FoldedConstantExpression) {
            final FoldedConstantExpression constant = (FoldedConstantExpression) expression;
            return constant.isBlockBinding() || !bindingOnly && constant.exactValue() != null && !constant.isWritten();
        }
        if (expression instanceof BinaryOperationExpression) {
            return holds(((BinaryOperationExpression) expression).getLeft(), bindingOnly)
                || holds(((BinaryOperationExpression) expression).getRight(), bindingOnly);
        }
        if (expression instanceof UnaryOperationExpression) {
            return holds(((UnaryOperationExpression) expression).getOperand(), bindingOnly);
        }
        if (expression instanceof CastExpression) {
            return holds(((CastExpression) expression).getExpression(), bindingOnly);
        }
        if (expression instanceof FunctionCallExpression) {
            final List<Expression> arguments = ((FunctionCallExpression) expression).getArguments();
            for (final Expression argument : arguments) {
                if (holds(argument, bindingOnly)) {
                    return true;
                }
            }
            return !bindingOnly && overWrittenNumbers(arguments);
        }
        return false;
    }

    /** Whether a call's arguments are all numbers written out, a minus before one included. */
    private static boolean overWrittenNumbers(final List<Expression> arguments) {
        if (arguments.isEmpty()) {
            return false;
        }
        for (final Expression argument : arguments) {
            final Expression number = argument instanceof UnaryOperationExpression
                && ((UnaryOperationExpression) argument).getOperator() == UnaryOperator.NEGATE
                ? ((UnaryOperationExpression) argument).getOperand() : argument;
            if (!(number instanceof LiteralExpression) || number instanceof FoldedConstantExpression
                    || ((LiteralExpression) number).getType() != LiteralType.INTEGER
                    && ((LiteralExpression) number).getType() != LiteralType.DECIMAL) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether two exact-number precisions are stored at the same width.
     *
     * @param precision one precision
     * @param other     the other
     * @return whether both take the same number of bytes
     */
    static boolean sameStorage(final int precision, final int other) {
        return storageBytes(precision) == storageBytes(other);
    }

    private static int storageBytes(final int precision) {
        if (precision <= ONE_BYTE_DIGITS) {
            return 1;
        }
        if (precision <= TWO_BYTE_DIGITS) {
            return 2;
        }
        if (precision <= FOUR_BYTE_DIGITS) {
            return 4;
        }
        return precision <= EIGHT_BYTE_DIGITS ? 8 : 16;
    }
}
