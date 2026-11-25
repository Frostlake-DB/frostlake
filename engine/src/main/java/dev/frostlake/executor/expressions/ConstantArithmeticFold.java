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

import dev.frostlake.executor.RelationShapeOnly;
import dev.frostlake.executor.UnfoldedBody;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.values.ValueRange;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The arithmetic the planner folds while the statement compiles: a tree of sums, differences, products and
 * negations over constants — numeric literals, casts of them, and columns the table's statistics pin to one value
 * and no NULL — is computed exactly and converted ONCE, at its root, to the type the root declares. A root whose
 * value that type cannot hold is refused in the words of {@link FoldedArithmetic#refusal}, naming the whole tree's
 * exact text ({@code (p) + 1} names the sum). A tree of literals is refused whether or not a row ever reaches it:
 * under a WHERE no row passes, in an IFF or CASE branch whose condition reads a column, in a view's body the
 * statement reads. A tree over a pinned column is folded as its rows are read, and an item the statement never
 * reads is never folded.
 *
 * <p>A tree is left to the row where a node's exact value has more than 38 digits, or where a sum's constant
 * operand cannot be rescaled to the sum's scale within 38 digits; its operands are then roots of their own. A
 * quotient and a function call are no part of a tree. A root is not converted under a cast (the cast converts its
 * value), under IS [NOT] NULL (a constant tree is never NULL unless a NULL leaf makes it NULL), or as an operand of
 * a comparison of two constants (the comparison folds). A branch a constant condition never takes, and a COALESCE,
 * NVL or IFNULL argument after the first one that is not the NULL literal, is not folded (all live-verified).
 */
final class ConstantArithmeticFold {

    /** The digits an exact folded value holds at most before the tree is left to the row. */
    private static final int FOLDED_DIGITS = 38;

    /** The scale of a value the tree cannot type as an exact number. */
    private static final int NO_SCALE = -1;

    /** What a node that folds to no tree is recorded as. */
    private static final FoldedArithmetic ABANDONED = new FoldedArithmetic(BigDecimal.ZERO, 0, false);

    private final ExpressionEvaluatorVisitor visitor;
    /** Each fold node read so far to its folded tree, or to {@link #ABANDONED} where it folds to none. */
    private final Map<Expression, FoldedArithmetic> trees = new IdentityHashMap<Expression, FoldedArithmetic>();
    /** The roots read so far whose parent consumes the exact value instead of converting it. */
    private final Map<Expression, Boolean> unconverted = new IdentityHashMap<Expression, Boolean>();

    ConstantArithmeticFold(final ExpressionEvaluatorVisitor visitor) {
        this.visitor = visitor;
    }

    /** Whether an expression is an operation a folded tree is built of. */
    static boolean isFoldNode(final Expression expr) {
        if (expr instanceof BinaryOperationExpression) {
            final BinaryOperator operator = ((BinaryOperationExpression) expr).getOperator();
            return operator == BinaryOperator.ADD || operator == BinaryOperator.SUBTRACT
                || operator == BinaryOperator.MULTIPLY;
        }
        return expr instanceof UnaryOperationExpression
            && ((UnaryOperationExpression) expr).getOperator() == UnaryOperator.NEGATE;
    }

    /**
     * The tree a fold node folds to.
     *
     * @param expr a fold node
     * @return the folded tree, or null where the node is no constant tree or is left to the row
     */
    FoldedArithmetic of(final Expression expr) {
        FoldedArithmetic tree = trees.get(expr);
        if (tree == null) {
            try {
                tree = compute(expr);
            } catch (final RuntimeException unknown) {
                tree = null;
            }
            trees.put(expr, tree == null ? ABANDONED : tree);
        }
        return tree == ABANDONED ? null : tree;
    }

    /** Whether a root's value is converted to its own type, as every root is but those a parent consumes. */
    boolean converts(final Expression root) {
        return !unconverted.containsKey(root);
    }

    /** Records that an operand's value is consumed unconverted where it is a folded tree. */
    void markUnconverted(final Expression operand) {
        unconverted.put(operand, Boolean.TRUE);
    }

    /**
     * Records a comparison's two operands as consumed unconverted where both are constants: the comparison folds.
     *
     * @param left  one operand
     * @param right the other operand
     */
    void markConstantComparison(final Expression left, final Expression right) {
        if (!unconverted.containsKey(left) && isConstant(left) && isConstant(right)) {
            markUnconverted(left);
            markUnconverted(right);
        }
    }

    /**
     * Refuses, while the statement compiles, the first folded root its type cannot hold, in written order and
     * inside out. A body compiled for its shape alone, or compiled whole beside the text the statement reads (see
     * {@link UnfoldedBody}), is not judged: the items the statement never reads are blanked before it runs, and
     * live never folds them.
     *
     * @param expr the expression to judge
     */
    void judgeRoots(final Expression expr) {
        if (!RelationShapeOnly.isActive() && !UnfoldedBody.isActive()) {
            judge(expr);
        }
    }

    private void judge(final Expression expr) {
        if (expr == null) {
            return;
        }
        if (isFoldNode(expr)) {
            final FoldedArithmetic tree = of(expr);
            if (tree != null) {
                // A column the statistics pin is folded as the rows are read, not while the statement compiles.
                if (!readsColumn(expr)) {
                    tree.requireFits();
                }
                return;
            }
            if (expr instanceof BinaryOperationExpression) {
                judge(((BinaryOperationExpression) expr).getLeft());
                judge(((BinaryOperationExpression) expr).getRight());
            } else {
                judge(((UnaryOperationExpression) expr).getOperand());
            }
            return;
        }
        if (expr instanceof CastExpression) {
            judgeUnconverted(((CastExpression) expr).getExpression());
            return;
        }
        if (expr instanceof IsNullExpression) {
            judgeUnconverted(((IsNullExpression) expr).getOperand());
            return;
        }
        if (expr instanceof BinaryOperationExpression) {
            final BinaryOperationExpression operation = (BinaryOperationExpression) expr;
            if (isComparison(operation.getOperator()) && isConstant(operation.getLeft())
                    && isConstant(operation.getRight())) {
                judgeUnconverted(operation.getLeft());
                judgeUnconverted(operation.getRight());
                return;
            }
            judge(operation.getLeft());
            judge(operation.getRight());
            return;
        }
        if (expr instanceof UnaryOperationExpression) {
            judge(((UnaryOperationExpression) expr).getOperand());
            return;
        }
        if (expr instanceof CaseExpression) {
            judgeCase((CaseExpression) expr);
            return;
        }
        if (expr instanceof FunctionCallExpression) {
            judgeCall((FunctionCallExpression) expr);
            return;
        }
        if (expr instanceof WindowFunctionExpression) {
            judgeAll(((WindowFunctionExpression) expr).getArguments());
            return;
        }
        if (expr instanceof InExpression) {
            judge(((InExpression) expr).getValue());
            judgeAll(((InExpression) expr).getValues());
            return;
        }
        if (expr instanceof BetweenExpression) {
            judge(((BetweenExpression) expr).getValue());
            judge(((BetweenExpression) expr).getLower());
            judge(((BetweenExpression) expr).getUpper());
        }
    }

    private void judgeAll(final List<Expression> expressions) {
        if (expressions == null) {
            return;
        }
        for (final Expression each : expressions) {
            judge(each);
        }
    }

    /** Judges an operand whose value, where it is a folded tree, its parent consumes unconverted. */
    private void judgeUnconverted(final Expression operand) {
        if (isFoldNode(operand) && of(operand) != null) {
            return;
        }
        judge(operand);
    }

    /** A CASE: a branch a constant condition never takes is not folded, nor any after one it always takes. */
    private void judgeCase(final CaseExpression conditional) {
        for (final WhenClause when : conditional.getWhenClauses()) {
            final Boolean taken = constantTruth(when.getCondition());
            if (Boolean.TRUE.equals(taken)) {
                judge(when.getResult());
                return;
            }
            if (taken == null) {
                judge(when.getCondition());
                judge(when.getResult());
            }
        }
        judge(conditional.getElseExpression());
    }

    private void judgeCall(final FunctionCallExpression call) {
        final List<Expression> arguments = call.getArguments();
        final String name = call.getFunctionName() == null || call.getNameExpression() != null ? ""
            : call.getFunctionName().toUpperCase(Locale.ROOT);
        if ("IFF".equals(name) && arguments.size() == 3) {
            final Boolean taken = constantTruth(arguments.get(0));
            if (taken == null) {
                judgeAll(arguments);
            } else {
                judge(arguments.get(taken.booleanValue() ? 1 : 2));
            }
            return;
        }
        if ("COALESCE".equals(name) || "NVL".equals(name) || "IFNULL".equals(name)) {
            for (final Expression argument : arguments) {
                if (!isNullLiteral(argument)) {
                    judge(argument);
                    return;
                }
            }
            return;
        }
        if ("EQUAL_NULL".equals(name) && arguments.size() == 2 && isConstant(arguments.get(0))
                && isConstant(arguments.get(1))) {
            judgeUnconverted(arguments.get(0));
            judgeUnconverted(arguments.get(1));
            return;
        }
        judgeAll(arguments);
    }

    /** Whether a condition is written of constants alone and, if so, what it answers; null where it is not. */
    private Boolean constantTruth(final Expression condition) {
        if (!isLiteralTree(condition)) {
            return null;
        }
        try {
            final Object answer = condition.accept(visitor);
            if (answer == null) {
                return Boolean.FALSE;
            }
            return answer instanceof Boolean ? (Boolean) answer : null;
        } catch (final RuntimeException unknown) {
            return null;
        }
    }

    private static boolean isLiteralTree(final Expression expr) {
        if (expr instanceof LiteralExpression) {
            return true;
        }
        if (expr instanceof CastExpression) {
            return isLiteralTree(((CastExpression) expr).getExpression());
        }
        if (expr instanceof UnaryOperationExpression) {
            return ((UnaryOperationExpression) expr).getOperator() != UnaryOperator.EXISTS
                && isLiteralTree(((UnaryOperationExpression) expr).getOperand());
        }
        if (expr instanceof IsNullExpression) {
            return isLiteralTree(((IsNullExpression) expr).getOperand());
        }
        if (expr instanceof BinaryOperationExpression) {
            return isLiteralTree(((BinaryOperationExpression) expr).getLeft())
                && isLiteralTree(((BinaryOperationExpression) expr).getRight());
        }
        return false;
    }

    private static boolean isNullLiteral(final Expression expr) {
        return expr instanceof LiteralExpression && ((LiteralExpression) expr).getType() == LiteralType.NULL
            && ((LiteralExpression) expr).getValue() == null;
    }

    private static boolean isComparison(final BinaryOperator operator) {
        return operator == BinaryOperator.EQUAL || operator == BinaryOperator.NOT_EQUAL
            || operator == BinaryOperator.LESS_THAN || operator == BinaryOperator.LESS_THAN_OR_EQUAL
            || operator == BinaryOperator.GREATER_THAN || operator == BinaryOperator.GREATER_THAN_OR_EQUAL;
    }

    /** Whether an expression is a folded tree or a constant leaf. */
    private boolean isConstant(final Expression expr) {
        if (isFoldNode(expr)) {
            return of(expr) != null;
        }
        try {
            return leaf(expr) != null;
        } catch (final RuntimeException unknown) {
            return false;
        }
    }

    private FoldedArithmetic operand(final Expression expr) {
        return isFoldNode(expr) ? of(expr) : leaf(expr);
    }

    private FoldedArithmetic compute(final Expression expr) {
        if (expr instanceof UnaryOperationExpression) {
            final FoldedArithmetic operand = operand(((UnaryOperationExpression) expr).getOperand());
            if (operand == null || operand.isNull()) {
                return operand == null ? null : new FoldedArithmetic(null, 0, false);
            }
            final int scale = exactScale(expr);
            return scale == NO_SCALE ? null : new FoldedArithmetic(operand.exact().negate(), scale, false);
        }
        final BinaryOperationExpression operation = (BinaryOperationExpression) expr;
        final FoldedArithmetic left = operand(operation.getLeft());
        if (left == null) {
            return null;
        }
        final FoldedArithmetic right = operand(operation.getRight());
        if (right == null) {
            return null;
        }
        if (left.isNull() || right.isNull()) {
            return new FoldedArithmetic(null, 0, false);
        }
        final int scale = exactScale(expr);
        if (scale == NO_SCALE) {
            return null;
        }
        final BigDecimal exact;
        if (operation.getOperator() == BinaryOperator.MULTIPLY) {
            exact = left.exact().multiply(right.exact());
        } else {
            if (!rescales(left, scale) || !rescales(right, scale)) {
                return null;
            }
            exact = operation.getOperator() == BinaryOperator.ADD ? left.exact().add(right.exact())
                : left.exact().subtract(right.exact());
        }
        return exact.precision() > FOLDED_DIGITS ? null : new FoldedArithmetic(exact, scale, false);
    }

    /** Whether a sum's operand reaches the sum's scale: a constant leaf only within 38 digits. */
    private static boolean rescales(final FoldedArithmetic operand, final int scale) {
        return !operand.isLeaf() || operand.exact().setScale(scale, RoundingMode.HALF_UP).precision() <= FOLDED_DIGITS;
    }

    /**
     * A constant leaf at its own digits — a numeric literal, a cast of a literal its type holds, or a column the
     * statistics pin — NULL for a NULL literal or a cast of one; null where it is none. A block's variable is no
     * constant the plan folds: its value changes from one run of the expression to the next.
     */
    private FoldedArithmetic leaf(final Expression expr) {
        if (expr instanceof CastExpression && ((CastExpression) expr).isBlockBinding()
                || expr instanceof ColumnReferenceExpression
                    && visitor.boundBlockVariable((ColumnReferenceExpression) expr) != null) {
            return null;
        }
        if (isNullLiteral(expr)
                || expr instanceof CastExpression && isNullLiteral(((CastExpression) expr).getExpression())) {
            return new FoldedArithmetic(null, 0, true);
        }
        if (!isNumericLiteral(expr) && !(expr instanceof ColumnReferenceExpression)
                && !(expr instanceof CastExpression && isSignedLiteral(((CastExpression) expr).getExpression()))) {
            return null;
        }
        final DataType declared = visitor.typeInferencer().infer(expr);
        if (!(declared instanceof NumericType) || !NumericType.isExactNumber(declared)) {
            return null;
        }
        final ValueRange range = visitor.inferStaticRange(expr);
        if (!ConstantProductFold.pins(range)) {
            return null;
        }
        final NumericType type = (NumericType) declared;
        // A cast its type cannot hold is refused by the cast itself, row by row.
        if (range.getMin().setScale(type.getScale(), RoundingMode.HALF_UP).precision() > type.getPrecision()) {
            return null;
        }
        return new FoldedArithmetic(ownDigits(range.getMin()), type.getScale(), true);
    }

    /** Whether a folded tree reads a column the statistics pin. */
    private static boolean readsColumn(final Expression expr) {
        if (expr instanceof ColumnReferenceExpression) {
            return true;
        }
        if (expr instanceof BinaryOperationExpression) {
            return readsColumn(((BinaryOperationExpression) expr).getLeft())
                || readsColumn(((BinaryOperationExpression) expr).getRight());
        }
        return expr instanceof UnaryOperationExpression && readsColumn(((UnaryOperationExpression) expr).getOperand());
    }

    private static boolean isNumericLiteral(final Expression expr) {
        return expr instanceof LiteralExpression && (((LiteralExpression) expr).getType() == LiteralType.INTEGER
            || ((LiteralExpression) expr).getType() == LiteralType.DECIMAL);
    }

    private static boolean isSignedLiteral(final Expression expr) {
        if (expr instanceof UnaryOperationExpression) {
            final UnaryOperator operator = ((UnaryOperationExpression) expr).getOperator();
            return (operator == UnaryOperator.NEGATE || operator == UnaryOperator.PLUS)
                && isSignedLiteral(((UnaryOperationExpression) expr).getOperand());
        }
        return expr instanceof LiteralExpression;
    }

    private int exactScale(final Expression expr) {
        final DataType declared = visitor.typeInferencer().infer(expr);
        return declared instanceof NumericType && NumericType.isExactNumber(declared)
            ? ((NumericType) declared).getScale() : NO_SCALE;
    }

    /** A value at its own digits: trailing zeros of its fraction dropped, never a negative scale. */
    private static BigDecimal ownDigits(final BigDecimal value) {
        final BigDecimal stripped = value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
