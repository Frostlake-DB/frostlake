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

import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.expressions.StrayArguments;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * The refusals a block's statements and expressions earn by their shape alone, judged while the block compiles: on
 * a branch that never runs, in an exception handler and in a nested block too, so a handler of the block never
 * catches one. An anonymous block, the block EXECUTE IMMEDIATE runs and a procedure's body (at CREATE, and when it
 * is called) are judged alike, each placed in the block's own text (live-verified):
 *
 * <pre>
 *   f(a =&gt; 1, a =&gt; 2)                               942  duplicate named argument A for function F
 *   f(a =&gt; 1, 2)                                    953  positional argument cannot follow named argument …
 *   SUM(y) OVER (ROWS 1 PRECEDING)                   2305 Window frame requires an ORDER BY clause.
 *   … ROWS BETWEEN 2000 PRECEDING AND CURRENT ROW    2311 Unsupported window frame: window frame size …
 *   CORR(y, x) OVER (ORDER BY y ROWS 1 PRECEDING)    2303 Sliding window frame unsupported for function CORR
 *   MEDIAN(y) OVER (ORDER BY y)                      2303 Cumulative window frame unsupported for function MEDIAN
 *   SUM(DISTINCT y) OVER (ORDER BY y)                2336 distinct cannot be used with a window frame or an order.
 *   ARRAY_AGG(y) WITHIN GROUP (ORDER BY y)
 *       OVER (ORDER BY y)                            10059 WITHIN GROUP clause is not supported when …
 *   CREATE TABLE d (x INT) COMMENT = 'a' COMMENT = 'b'   1424 duplicate property 'COMMENT';
 * </pre>
 *
 * <p>They outrank every name the block reads, and of several the first the walk meets is refused: a call after
 * the calls it holds, a WITH clause's queries after the statement they head. A table function a {@code TABLE( … )}
 * names is its own to judge, and so is a window of a MATCH_RECOGNIZE clause. A window function's ORDER BY that is
 * missing is no part of this: it is refused when its statement runs.
 */
public final class BlockCompiledShapes {

    private BlockCompiledShapes() {
    }

    /**
     * Refuse the first statement or expression under {@code root} whose shape is refused.
     *
     * @param root          the block
     * @param queryExecutor the executor whose window rules judge the windows
     */
    public static void require(final ParseTree root, final QueryExecutor queryExecutor) {
        walk(root, queryExecutor, false);
    }

    private static void walk(final ParseTree tree, final QueryExecutor queryExecutor, final boolean ownWindows) {
        if (!(tree instanceof ParserRuleContext)) {
            return;
        }
        final ParserRuleContext node = (ParserRuleContext) tree;
        final boolean skipWindows = ownWindows || node instanceof FrostlakeParser.MatchRecognizeClauseContext;
        if (node.children != null) {
            ParseTree heading = null;
            for (final ParseTree child : node.children) {
                if (child instanceof FrostlakeParser.WithClauseContext) {
                    heading = child;
                } else {
                    walk(child, queryExecutor, skipWindows);
                }
            }
            walk(heading, queryExecutor, skipWindows);
        }
        StrayArguments.requireCall(node);
        if (!skipWindows && (node instanceof FrostlakeParser.OverClauseContext
                || node instanceof FrostlakeParser.FunctionCallExprContext)) {
            queryExecutor.rejectWindowShapes(node);
        }
        if (node instanceof FrostlakeParser.CreateStatementContext) {
            CreateTableHandler.requireOneComment((FrostlakeParser.CreateStatementContext) node);
        }
    }
}
