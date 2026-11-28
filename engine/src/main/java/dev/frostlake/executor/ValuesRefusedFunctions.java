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
import dev.frostlake.executor.expressions.CaseExpression;
import dev.frostlake.executor.expressions.CastExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.ExpressionSource;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.executor.expressions.UnaryOperationExpression;
import dev.frostlake.executor.expressions.WhenClause;
import org.antlr.v4.runtime.Token;

import java.util.Locale;
import java.util.Set;

/**
 * The functions live refuses inside a VALUES clause - an INSERT's and a FROM clause's alike - beyond the
 * semi-structured families: each raises {@code Invalid expression [...] in VALUES clause} wherever it sits in the
 * item, naming the outer expression ({@code UPPER(UUID_STRING())}, {@code CONCAT('p', RANDOM())}), while TO_BINARY
 * and UPPER pass (live-verified).
 *
 * <p>Membership is by NAME, not by determinism: the two-argument {@code UUID_STRING(ns, name)} is an exact RFC 4122
 * function and is refused all the same, while {@code CURRENT_TIMESTAMP()}, {@code CURRENT_DATE()},
 * {@code CURRENT_USER()} and {@code UPPER()} all pass. {@code UNIFORM} is refused even with constant arguments
 * ({@code UNIFORM(1, 10, 5)}), so it is the function and not its randomness. SEQ2 is the one entry inferred rather
 * than measured - SEQ1, SEQ4 and SEQ8 were each refused, and the family is uniform. {@code INSERT ... SELECT} is the
 * supported route for every one of them.
 */
final class ValuesRefusedFunctions {

    /** The refused functions, by name. */
    private static final Set<String> REFUSED = Set.of(
        "COMPRESS", "MD5_BINARY", "HEX_DECODE_BINARY", "SHA2", "RANDOM", "RANDSTR",
        "UUID_STRING", "UNIFORM", "SEQ1", "SEQ2", "SEQ4", "SEQ8", "NORMAL", "ZIPF",
        "ENCRYPT", "ENCRYPT_RAW", "DECRYPT", "DECRYPT_RAW", "TRY_DECRYPT", "TRY_DECRYPT_RAW");

    private ValuesRefusedFunctions() {
    }

    /**
     * @param name a function's name, any case
     * @return true when VALUES refuses the function
     */
    static boolean refuses(final String name) {
        return name != null && REFUSED.contains(name.toUpperCase(Locale.ROOT));
    }

    /**
     * Judge the argument types of a refused item's calls, which come ahead of the VALUES refusal:
     * {@code VALUES (DECRYPT('a', 'key'))} is refused for its VARCHAR ciphertext, at the call (live-verified).
     *
     * @param typer the evaluator that types the item
     * @param item  the item, parsed
     * @param start the item's first token in the statement
     */
    static void judgeArgumentTypes(final ExpressionEvaluator typer, final Expression item, final Token start) {
        final SourcePosition displaced = ExpressionSource.begin(start == null ? null
            : new SourcePosition(start.getLine(), start.getCharPositionInLine()));
        try {
            typer.validateStrict(item);
        } finally {
            ExpressionSource.end(displaced);
        }
    }

    /**
     * Whether a refused function appears anywhere under {@code node}: as a call, or inside a call's arguments, a
     * cast or an operator.
     *
     * @param node an item, parsed, or null
     * @return true when the item holds a refused call
     */
    static boolean holdsOne(final Expression node) {
        if (node instanceof FunctionCallExpression) {
            final FunctionCallExpression call = (FunctionCallExpression) node;
            if (call.getNameExpression() == null && refuses(call.getFunctionName())) {
                return true;
            }
            final Boolean settled = call.getNameExpression() == null && "IFF".equalsIgnoreCase(call.getFunctionName())
                && call.getArguments().size() == 3 ? literalCondition(call.getArguments().get(0)) : null;
            if (settled != null) {
                // A constant condition folds the IFF to its branch: IFF(FALSE, RANDOM(), 1) is taken.
                return holdsOne(call.getArguments().get(settled.booleanValue() ? 1 : 2));
            }
            for (final Expression argument : call.getArguments()) {
                if (holdsOne(argument)) {
                    return true;
                }
            }
            return false;
        }
        if (node instanceof CastExpression) {
            return holdsOne(((CastExpression) node).getExpression());
        }
        if (node instanceof BinaryOperationExpression) {
            return holdsOne(((BinaryOperationExpression) node).getLeft())
                || holdsOne(((BinaryOperationExpression) node).getRight());
        }
        if (node instanceof UnaryOperationExpression) {
            return holdsOne(((UnaryOperationExpression) node).getOperand());
        }
        if (node instanceof CaseExpression) {
            return caseHoldsOne((CaseExpression) node);
        }
        return false;
    }

    /**
     * Whether a CASE holds a refused call in a branch it may take: a WHEN whose condition is the constant FALSE is
     * dropped and one that is TRUE ends the CASE, so {@code CASE WHEN FALSE THEN RANDOM() END} is taken while
     * {@code CASE WHEN TRUE THEN RANDOM() END} is refused (live-verified).
     */
    private static boolean caseHoldsOne(final CaseExpression node) {
        for (final WhenClause clause : node.getWhenClauses()) {
            final Boolean settled = clause.isOperandMatch() ? null : literalCondition(clause.getCondition());
            if (settled != null && !settled.booleanValue()) {
                continue;
            }
            if (settled == null && holdsOne(clause.getCondition()) || holdsOne(clause.getResult())) {
                return true;
            }
            if (settled != null) {
                return false;
            }
        }
        return node.getElseExpression() != null && holdsOne(node.getElseExpression());
    }

    /** The value of a condition written as the literal TRUE or FALSE, or null for any other condition. */
    private static Boolean literalCondition(final Expression condition) {
        return condition instanceof LiteralExpression && ((LiteralExpression) condition).getValue() instanceof Boolean
            ? (Boolean) ((LiteralExpression) condition).getValue() : null;
    }
}
