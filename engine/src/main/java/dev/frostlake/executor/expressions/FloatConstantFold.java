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

import dev.frostlake.metastore.model.TableColumn;

import java.util.Locale;
import java.util.Set;

/**
 * Whether a FLOAT expression is one the plan folds to a constant while the statement compiles, which decides how it
 * converts to a DECFLOAT: a folded FLOAT converts at its exact binary value, one computed while the statement runs
 * keeps 18 significant digits of it (see {@code Decfloats.computed}). Live-verified:
 *
 * <ul>
 *   <li>folded — a literal, a session variable, a cast of a folded value ({@code 0.1::FLOAT}, {@code '0.1'::FLOAT}),
 *       a sign before one, a sum, a difference, a product or a comparison of folded values, a CASE over them, and
 *       ABS, GREATEST, LEAST, COALESCE, NVL, NVL2, NULLIF, ZEROIFNULL, IFF, DECODE, TO_DOUBLE, TO_NUMBER and PI over
 *       them: {@code 0.1::FLOAT::DECFLOAT} is 1.0000000000000000555111512312578270212e-1; and a column of a derived
 *       relation the plan merges into the query over it that stands for a folded item ({@link MergedDerivedColumn}):
 *       {@code x::DECFLOAT} over {@code (SELECT 0.1::FLOAT x FROM t)} converts exactly too;</li>
 *   <li>computed — a column, a quotient ({@code (1::FLOAT / 10)::DECFLOAT} is 0.100000000000000006, and so is a
 *       quotient of exact numbers made a FLOAT), MOD, DIV0, ROUND, TRUNC, CEIL, POWER, SQUARE, SQRT, EXP, LN, SIN,
 *       an aggregate, a subquery, a column of a relation the plan keeps whole (a VALUES clause, a UNION ALL) and any
 *       other call.</li>
 * </ul>
 */
final class FloatConstantFold {

    /** The calls the plan folds over folded arguments. */
    private static final Set<String> FOLDED_CALLS = Set.of("ABS", "GREATEST", "LEAST", "COALESCE", "NVL", "NVL2",
        "NULLIF", "ZEROIFNULL", "IFF", "DECODE", "TO_DOUBLE", "TO_NUMBER", "TO_DECIMAL", "TO_NUMERIC", "PI");

    /**
     * The calls a constant text read as a FLOAT is not folded through: those that draw a new value, and the
     * document parsers, after which the text is read while the statement runs.
     */
    private static final Set<String> UNFOLDED_TEXT_CALLS = Set.of("RANDOM", "RANDSTR", "UUID_STRING", "UNIFORM",
        "NORMAL", "ZIPF", "SEQ1", "SEQ2", "SEQ4", "SEQ8", "PARSE_JSON", "TRY_PARSE_JSON", "PARSE_XML");

    private FloatConstantFold() {
    }

    /**
     * Whether a text the plan reads as a FLOAT is a constant it folds, which decides whether a finite text past the
     * double's range is the infinity or refused (see {@code ApproximateText}). Live-verified: a literal, a session
     * variable, a cast, a sign, an operator, a CASE and any built-in scalar call over constants fold
     * ({@code UPPER('2e308')::FLOAT}, {@code NVL(NULL, '2e308')::FLOAT} and {@code TO_VARIANT('2e308')::FLOAT}
     * are inf), and so does a column of a merged derived relation standing for one; a column, a VALUES clause,
     * an aggregate and {@code PARSE_JSON('"2e308"')} do not. A column of a derived relation the plan keeps whole that projects such
     * a constant folds too: {@code x::FLOAT} over {@code (SELECT '2e308' AS x)} is inf.
     *
     * @param expression the expression the text comes from
     * @param scope      the scope it is read in
     * @return whether the plan folds it
     */
    static boolean textFolds(final Expression expression, final ExpressionEvaluatorVisitor scope) {
        if (expression == null || expression instanceof LiteralExpression || expression instanceof SessionVarExpression) {
            return true;
        }
        if (expression instanceof ColumnReferenceExpression) {
            final ColumnReferenceExpression ref = (ColumnReferenceExpression) expression;
            final MergedDerivedColumn merged = MergedDerivedColumn.of(ref, scope);
            if (merged != null && !merged.isColumn() && textFolds(merged.getItem(), merged.getScope())) {
                return true;
            }
            // A derived relation's column projecting a folded constant, a FROM-less one's among them.
            final TableColumn declared = scope.resolveDeclaredColumn(ref);
            return declared != null && declared.isFoldedText();
        }
        if (expression instanceof CastExpression) {
            return textFolds(((CastExpression) expression).getExpression(), scope);
        }
        if (expression instanceof UnaryOperationExpression) {
            final UnaryOperationExpression unary = (UnaryOperationExpression) expression;
            return unary.getOperator() != UnaryOperator.EXISTS && textFolds(unary.getOperand(), scope);
        }
        if (expression instanceof BinaryOperationExpression) {
            final BinaryOperationExpression binary = (BinaryOperationExpression) expression;
            return binary.getEscape() == null && textFolds(binary.getLeft(), scope)
                && textFolds(binary.getRight(), scope);
        }
        if (expression instanceof CaseExpression) {
            final CaseExpression conditional = (CaseExpression) expression;
            for (final WhenClause when : conditional.getWhenClauses()) {
                if (!textFolds(when.getCondition(), scope) || !textFolds(when.getResult(), scope)) {
                    return false;
                }
            }
            return textFolds(conditional.getElseExpression(), scope);
        }
        if (expression instanceof FunctionCallExpression) {
            return textFoldsCall((FunctionCallExpression) expression, scope);
        }
        return false;
    }

    /** {@link #textFolds} for a call: a built-in scalar one, every argument folded. */
    private static boolean textFoldsCall(final FunctionCallExpression call, final ExpressionEvaluatorVisitor scope) {
        final String name = call.getFunctionName();
        if (name == null || call.getNameExpression() != null || call.getMethodName() != null || call.isStar() || call.isDistinct()
                || call.getNameParts() != null && call.getNameParts().size() > 1 || UNFOLDED_TEXT_CALLS.contains(name.toUpperCase(Locale.ROOT))
                || scope.getFunctionRegistry() == null || scope.getFunctionRegistry().getFunction(name) == null
                || scope.getFunctionRegistry().hasAggregateFunction(name)) {
            return false;
        }
        for (final Expression argument : call.getArguments()) {
            if (!textFolds(argument, scope)) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param expression the expression
     * @param scope      the scope it is read in
     * @return whether the plan folds it to a constant
     */
    static boolean folds(final Expression expression, final ExpressionEvaluatorVisitor scope) {
        if (expression == null || expression instanceof LiteralExpression || expression instanceof SessionVarExpression) {
            return true;
        }
        if (expression instanceof ColumnReferenceExpression) {
            final MergedDerivedColumn merged = MergedDerivedColumn.of((ColumnReferenceExpression) expression, scope);
            return merged != null && !merged.isColumn() && folds(merged.getItem(), merged.getScope());
        }
        if (expression instanceof CastExpression) {
            return folds(((CastExpression) expression).getExpression(), scope);
        }
        if (expression instanceof UnaryOperationExpression) {
            final UnaryOperationExpression unary = (UnaryOperationExpression) expression;
            return unary.getOperator() != UnaryOperator.EXISTS && folds(unary.getOperand(), scope);
        }
        if (expression instanceof BinaryOperationExpression) {
            final BinaryOperationExpression binary = (BinaryOperationExpression) expression;
            return binary.getOperator() != BinaryOperator.DIVIDE && binary.getOperator() != BinaryOperator.MODULO
                && folds(binary.getLeft(), scope) && folds(binary.getRight(), scope);
        }
        if (expression instanceof CaseExpression) {
            final CaseExpression conditional = (CaseExpression) expression;
            for (final WhenClause when : conditional.getWhenClauses()) {
                if (!folds(when.getCondition(), scope) || !folds(when.getResult(), scope)) {
                    return false;
                }
            }
            return folds(conditional.getElseExpression(), scope);
        }
        if (expression instanceof FunctionCallExpression) {
            final FunctionCallExpression call = (FunctionCallExpression) expression;
            if (call.getFunctionName() == null
                    || !FOLDED_CALLS.contains(call.getFunctionName().toUpperCase(Locale.ROOT))) {
                return false;
            }
            for (final Expression argument : call.getArguments()) {
                if (!folds(argument, scope)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
}
