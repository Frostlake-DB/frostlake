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

import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * The text of the subquery running on this thread for a row of the query around it, so a refusal raised while it
 * runs can find where the refused subquery stands in it: a subquery's own subquery is judged only as a row reaches
 * it, with nothing but its own text in hand.
 */
final class RunningSubqueryText {

    private static final ThreadLocal<String> TEXT = new ThreadLocal<String>();

    /** Where the running subquery's text begins in its statement. */
    private static final ThreadLocal<SourcePosition> ORIGIN = new ThreadLocal<SourcePosition>();

    private RunningSubqueryText() {
    }


    /**
     * Note the subquery about to run, at the origin in force (see {@link ExpressionSource#origin}), returning the
     * one it displaced.
     *
     * @param text the subquery's text
     * @return the text and origin displaced, for {@link #end}
     */
    static Object[] begin(final String text) {
        final Object[] previous = {TEXT.get(), ORIGIN.get()};
        TEXT.set(text);
        ORIGIN.set(ExpressionSource.origin());
        return previous;
    }

    /**
     * Put back what {@link #begin} displaced.
     *
     * @param previous the text and origin displaced
     */
    static void end(final Object[] previous) {
        if (previous[0] == null) {
            TEXT.remove();
        } else {
            TEXT.set((String) previous[0]);
        }
        if (previous[1] == null) {
            ORIGIN.remove();
        } else {
            ORIGIN.set((SourcePosition) previous[1]);
        }
    }

    /**
     * Where a token of the running subquery's parse tree stands in the statement, or null where its origin is not
     * known.
     *
     * @param token the token
     * @return the place, or null
     */
    static SourcePosition place(final Token token) {
        final SourcePosition origin = ORIGIN.get();
        if (origin == null) {
            return null;
        }
        final int line = origin.getLine() + token.getLine() - 1;
        final int column = token.getLine() == 1
            ? origin.getCharPositionInLine() + token.getCharPositionInLine() : token.getCharPositionInLine();
        return new SourcePosition(line, column);
    }

    /**
     * The expression of the running subquery that holds the subquery whose own text begins at {@code at}: a scalar
     * subquery, an IN, a quantified comparison or an EXISTS. Null where no subquery runs or none begins there.
     *
     * @param queryExecutor the executor that parses the text
     * @param at            where the subquery's text begins in the statement
     * @return the expression, or null
     */
    static ParserRuleContext holderOf(final QueryExecutor queryExecutor, final SourcePosition at) {
        final String text = TEXT.get();
        if (text == null || at == null || ORIGIN.get() == null) {
            return null;
        }
        final FrostlakeParser.SelectStatementContext running = queryExecutor.subqueryStatement(text);
        return running == null ? null : holderOf(running, at);
    }

    private static ParserRuleContext holderOf(final ParseTree node, final SourcePosition at) {
        final FrostlakeParser.SelectStatementContext query = queryOf(node);
        if (query != null) {
            final SourcePosition start = place(query.getStart());
            if (start.getLine() == at.getLine() && start.getCharPositionInLine() == at.getCharPositionInLine()) {
                return (ParserRuleContext) node;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final ParserRuleContext found = holderOf(node.getChild(i), at);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static FrostlakeParser.SelectStatementContext queryOf(final ParseTree node) {
        if (node instanceof FrostlakeParser.ScalarSubqueryExprContext) {
            return ((FrostlakeParser.ScalarSubqueryExprContext) node).selectStatement();
        }
        if (node instanceof FrostlakeParser.InSubqueryExprContext) {
            return ((FrostlakeParser.InSubqueryExprContext) node).selectStatement();
        }
        if (node instanceof FrostlakeParser.TupleInSubqueryExprContext) {
            return ((FrostlakeParser.TupleInSubqueryExprContext) node).selectStatement();
        }
        if (node instanceof FrostlakeParser.QuantifiedComparisonExprContext) {
            return ((FrostlakeParser.QuantifiedComparisonExprContext) node).selectStatement();
        }
        if (node instanceof FrostlakeParser.ExistsExprContext) {
            return ((FrostlakeParser.ExistsExprContext) node).selectStatement();
        }
        return null;
    }
}
