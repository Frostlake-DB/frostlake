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
import dev.frostlake.executor.expressions.BindVariableExpression;
import dev.frostlake.executor.expressions.CastExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.UnaryOperationExpression;
import dev.frostlake.executor.expressions.UnaryOperator;
import dev.frostlake.functions.scalar.math.Div;
import dev.frostlake.parser.FrostlakeParser;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * The DIV calls a statement writes, which the account refuses as it plans the statement: {@code Unsupported
 * feature 'DIV'.}, before a row is read — over an empty table, in a WHERE no row passes, under LIMIT 0, in a CASE
 * branch never taken, in a CTE nothing reads, and under EXPLAIN alike — once the statement has compiled, so a missing
 * table, a column that is not there or an unknown function speaks first (live-verified).
 *
 * <p>A query, the EXPLAIN of one, a DML statement and a table created from a query are judged; a view, a function
 * or a column DEFAULT stores its DIV unjudged, and the statement that reads it is refused. A call that is not
 * judged here — one with fewer than two arguments, named ones, DISTINCT or a window — is left to the refusal of its
 * own shape, and so is one the account folds to NULL before it plans it: an argument that is a NULL, or a cast, a
 * sign or an arithmetic over one, makes the whole call NULL (see {@link Div}). A block's variable is bound as a
 * constant too, so a call over one is judged by the variable's value as it runs.
 */
final class DivCalls {

    private static final String NAME = "DIV";

    /** Set while a statement's query compiles on its own, ahead of the refusal; the refusal waits for it. */
    private static final ThreadLocal<Boolean> COMPILING = new ThreadLocal<Boolean>();

    /** The statements found to write no such call: a cached tree is walked once. */
    private static final Map<ParseTree, Boolean> CLEAR =
        Collections.synchronizedMap(new WeakHashMap<ParseTree, Boolean>());

    private DivCalls() {
    }

    /**
     * Refuse a statement that writes a DIV call the account does not fold.
     *
     * @param ctx      the statement
     * @param executor the engine's query executor, which compiles a query's shape first
     */
    static void requireNone(final FrostlakeParser.StatementContext ctx, final QueryExecutor executor) {
        if (Boolean.TRUE.equals(COMPILING.get())) {
            return;
        }
        final ParserRuleContext query;
        final ParserRuleContext judged;
        if (ctx.queryStatement() != null) {
            query = ctx.queryStatement();
            judged = query;
        } else if (ctx.explainStatement() != null) {
            query = ctx.explainStatement().selectStatement();
            judged = ctx.explainStatement();
        } else if (ctx.dmlStatement() != null) {
            query = null;
            judged = ctx.dmlStatement();
        } else if (ctx.ddlStatement() != null && ctx.ddlStatement().createStatement() != null
                && ctx.ddlStatement().createStatement().TABLE() != null
                && ctx.ddlStatement().createStatement().AS() != null
                && ctx.ddlStatement().createStatement().selectStatement() != null) {
            query = ctx.ddlStatement().createStatement().selectStatement();
            judged = query;
        } else {
            return;
        }
        if (CLEAR.containsKey(judged)) {
            return;
        }
        if (!writesUnfolded(judged)) {
            CLEAR.put(judged, Boolean.TRUE);
            return;
        }
        if (query != null && executor != null) {
            // The query compiles first, no row read: what it fails to compile for is its answer.
            COMPILING.set(Boolean.TRUE);
            try {
                executor.resolveRelationShape(ParseTreeText.getOriginalText(query), null);
            } finally {
                COMPILING.remove();
            }
        }
        throw new RuntimeException(Div.UNSUPPORTED);
    }

    /** Whether a DIV call the account does not fold stands anywhere under {@code node}. */
    private static boolean writesUnfolded(final ParseTree node) {
        if (node instanceof FrostlakeParser.FunctionCallExprContext && isUnfolded(
                (FrostlakeParser.FunctionCallExprContext) node)) {
            return true;
        }
        if (node instanceof FrostlakeParser.FunctionCallStarExprContext) {
            final FrostlakeParser.FunctionCallStarExprContext star = (FrostlakeParser.FunctionCallStarExprContext) node;
            if (namesDiv(star.functionName()) && star.DISTINCT() == null) {
                return true;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (writesUnfolded(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isUnfolded(final FrostlakeParser.FunctionCallExprContext call) {
        if (!namesDiv(call.functionName()) || call.overClause() != null || call.DISTINCT() != null) {
            return false;
        }
        final Expression parsed;
        try {
            parsed = ExpressionEvaluator.parse(ParseTreeText.getOriginalText(call));
        } catch (final RuntimeException unreadAlone) {
            return false;
        }
        if (!(parsed instanceof FunctionCallExpression)) {
            return false;
        }
        final FunctionCallExpression div = (FunctionCallExpression) parsed;
        if (div.getArguments().size() < 2 || div.getArgumentNames() != null && hasName(div)) {
            return false;
        }
        return !foldsToNull(div);
    }

    private static boolean hasName(final FunctionCallExpression call) {
        for (final String name : call.getArgumentNames()) {
            if (name != null) {
                return true;
            }
        }
        return false;
    }

    /** Whether a call is named DIV by one word, quoted or not. */
    private static boolean namesDiv(final FrostlakeParser.FunctionNameContext name) {
        return name != null && name.identifier().size() == 1
            && NAME.equalsIgnoreCase(ParseTreeText.getIdentifier(name.identifier(0)));
    }

    /**
     * Whether the account folds an expression to NULL before it plans the statement: a NULL, a cast, a sign or an
     * arithmetic or a concatenation over one, and a DIV call over one; a block's variable may be one.
     */
    private static boolean foldsToNull(final Expression expr) {
        if (expr instanceof LiteralExpression) {
            return ((LiteralExpression) expr).getValue() == null;
        }
        if (expr instanceof BindVariableExpression) {
            // A block's variable is bound as the constant it holds, which may be NULL: the call is judged by its
            // value as it runs (see Div).
            return true;
        }
        if (expr instanceof CastExpression) {
            return foldsToNull(((CastExpression) expr).getExpression());
        }
        if (expr instanceof UnaryOperationExpression) {
            final UnaryOperationExpression unary = (UnaryOperationExpression) expr;
            return (unary.getOperator() == UnaryOperator.NEGATE || unary.getOperator() == UnaryOperator.PLUS)
                && foldsToNull(unary.getOperand());
        }
        if (expr instanceof BinaryOperationExpression) {
            final BinaryOperationExpression binary = (BinaryOperationExpression) expr;
            final BinaryOperator operator = binary.getOperator();
            return (operator == BinaryOperator.ADD || operator == BinaryOperator.SUBTRACT
                || operator == BinaryOperator.MULTIPLY || operator == BinaryOperator.DIVIDE
                || operator == BinaryOperator.MODULO || operator == BinaryOperator.CONCAT)
                && (foldsToNull(binary.getLeft()) || foldsToNull(binary.getRight()));
        }
        if (expr instanceof FunctionCallExpression) {
            final FunctionCallExpression call = (FunctionCallExpression) expr;
            if (call.getNameExpression() == null && NAME.equalsIgnoreCase(call.getFunctionName())
                    && call.getArguments().size() >= 2) {
                for (final Expression argument : call.getArguments()) {
                    if (foldsToNull(argument)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
