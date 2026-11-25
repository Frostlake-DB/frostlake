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

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.parser.FrostlakeParser;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * The order of a multi-table INSERT's cases, judged as the statement compiles and before any name of it is resolved
 * (live-verified). The account reads INTO, WHEN and ELSE cases in any order after either FIRST or ALL, and refuses,
 * in this order, each at the word named (all 42601):
 *
 * <pre>
 *   INSERT ALL INTO t WHEN … THEN INTO u …   1033  Conditional multi-table insert must begin with a condition.
 *                                                  — at the first INTO, where a WHEN or an ELSE follows
 *   INSERT ALL ELSE INTO t …                 1036  Invalid multi-table insert: must provide insert case prior to ELSE.
 *                                                  — at that ELSE
 *   INSERT ALL WHEN … ELSE … WHEN …          1037  Unreachable case in multi-table insert. ELSE case must be last
 *                                                  case provided. — at the first ELSE with a case after it
 *   INSERT FIRST INTO t …                    1034  Unconditional multi-table insert must use INSERT ALL (not INSERT
 *                                                  FIRST). — at FIRST
 * </pre>
 *
 * A block's or a procedure's body is refused for such a statement as it compiles, a branch that never runs included.
 */
public final class MultiTableInsertShape {

    /** The sentence of a case standing before the first condition of a conditional insert. */
    static final String UNCONDITIONED_START = "Conditional multi-table insert must begin with a condition.";

    /** The sentence of an ELSE standing first. */
    static final String ELSE_FIRST = "Invalid multi-table insert: must provide insert case prior to ELSE.";

    /** The sentence of an ELSE with a case after it. */
    static final String ELSE_NOT_LAST =
        "Unreachable case in multi-table insert. ELSE case must be last case provided.";

    /** The sentence of an INSERT FIRST without a condition. */
    static final String UNCONDITIONAL_FIRST =
        "Unconditional multi-table insert must use INSERT ALL (not INSERT FIRST).";

    private MultiTableInsertShape() {
    }

    /**
     * Judges every multi-table INSERT a body holds, in the order written.
     *
     * @param body the body's block, or the parsed text that holds it
     */
    public static void requireThroughout(final ParseTree body) {
        if (body instanceof FrostlakeParser.MultiTableInsertStatementContext) {
            require((FrostlakeParser.MultiTableInsertStatementContext) body);
            return;
        }
        for (int i = 0; i < body.getChildCount(); i++) {
            requireThroughout(body.getChild(i));
        }
    }

    /**
     * Judges one multi-table INSERT's cases.
     *
     * @param statement the statement
     */
    public static void require(final FrostlakeParser.MultiTableInsertStatementContext statement) {
        final List<ParserRuleContext> cases = new ArrayList<>();
        boolean conditional = false;
        for (int i = 0; i < statement.getChildCount(); i++) {
            final ParseTree child = statement.getChild(i);
            if (child instanceof FrostlakeParser.MultiInsertIntoContext) {
                cases.add((ParserRuleContext) child);
            } else if (child instanceof FrostlakeParser.MultiInsertWhenContext
                    || child instanceof FrostlakeParser.MultiInsertElseContext) {
                cases.add((ParserRuleContext) child);
                conditional = true;
            }
        }
        final ParserRuleContext first = cases.get(0);
        if (conditional && first instanceof FrostlakeParser.MultiInsertIntoContext) {
            throw refusal(first.getStart(), UNCONDITIONED_START);
        }
        if (first instanceof FrostlakeParser.MultiInsertElseContext) {
            throw refusal(first.getStart(), ELSE_FIRST);
        }
        for (int i = 0; i < cases.size() - 1; i++) {
            if (cases.get(i) instanceof FrostlakeParser.MultiInsertElseContext) {
                throw refusal(cases.get(i).getStart(), ELSE_NOT_LAST);
            }
        }
        if (!conditional && statement.FIRST() != null) {
            throw refusal(statement.FIRST().getSymbol(), UNCONDITIONAL_FIRST);
        }
    }

    /**
     * The ELSE case of a statement {@link #require} accepted, or null.
     *
     * @param statement the statement
     * @return its ELSE case, or null
     */
    public static FrostlakeParser.MultiInsertElseContext elseOf(
            final FrostlakeParser.MultiTableInsertStatementContext statement) {
        final List<FrostlakeParser.MultiInsertElseContext> otherwise = statement.multiInsertElse();
        return otherwise.isEmpty() ? null : otherwise.get(otherwise.size() - 1);
    }

    private static RuntimeException refusal(final Token at, final String sentence) {
        return new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(), sentence));
    }
}
