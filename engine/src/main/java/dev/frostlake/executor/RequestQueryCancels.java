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

import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The SYSTEM$CANCEL_QUERY calls of a request's queries that name a query ID by a string written out, carried out as
 * the request is read, before it waits for the engine: a statement of another session that holds the engine while it
 * runs ends at its next checkpoint (see {@link StatementCancellation}), where waiting for the engine would let it run
 * on to its end, when nothing is left to cancel. Each call is carried out again in its turn and answers that it
 * terminated the statement it canceled ahead, as it would have answered had it run at once.
 */
public final class RequestQueryCancels {

    private static final String NAME = "SYSTEM$CANCEL_QUERY";

    private RequestQueryCancels() {
    }

    /**
     * Carry out the cancels a request names, for the request about to run on this thread.
     *
     * @param sql the request's text
     * @return what an enclosing request on this thread canceled ahead, to put back with {@link #end}
     */
    public static Set<String> carryOut(final String sql) {
        final Set<String> displaced = StatementCancellation.beginCanceledAhead();
        final FrostlakeParser.SqlScriptContext script;
        try {
            script = QueryExecutor.parsedScriptQuietly(sql);
        } catch (final RuntimeException doesNotParse) {
            // A text that does not parse is refused for its syntax in its turn, and cancels nothing.
            return displaced;
        }
        final List<String> named = new ArrayList<String>();
        for (final FrostlakeParser.FlowChainContext chain : script.flowChain()) {
            for (final FrostlakeParser.StatementContext statement : chain.statement()) {
                if (statement.queryStatement() != null) {
                    collect(statement.queryStatement(), named);
                }
            }
        }
        for (final String id : named) {
            StatementCancellation.cancelAhead(id);
        }
        return displaced;
    }

    /**
     * The request {@link #carryOut} read has ended.
     *
     * @param displaced what {@link #carryOut} answered
     */
    public static void end(final Set<String> displaced) {
        StatementCancellation.endCanceledAhead(displaced);
    }

    /** The query IDs the calls under {@code node} name by a string written out, in the order written. */
    private static void collect(final ParseTree node, final List<String> into) {
        if (node instanceof FrostlakeParser.SystemFuncExprContext) {
            final FrostlakeParser.SystemFuncExprContext call = (FrostlakeParser.SystemFuncExprContext) node;
            if (NAME.equalsIgnoreCase(call.SYSTEM_FUNC().getText()) && call.booleanExprList() != null
                    && call.booleanExprList().booleanExpr().size() == 1) {
                final Token start = call.booleanExprList().booleanExpr(0).getStart();
                if (start == call.booleanExprList().booleanExpr(0).getStop()
                        && start.getType() == FrostlakeLexer.STRING_LITERAL) {
                    final String id = SqlStringLiterals.decode(start.getText()).trim();
                    if (isUuid(id)) {
                        into.add(id);
                    }
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collect(node.getChild(i), into);
        }
    }

    /** Whether a text is a UUID, as the call requires of the ID it cancels. */
    private static boolean isUuid(final String text) {
        try {
            UUID.fromString(text);
            return true;
        } catch (final IllegalArgumentException notAUuid) {
            return false;
        }
    }
}
