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

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Where a BREAK, a CONTINUE, an EXIT and an ITERATE may stand, judged when a body starts to run and before any of
 * it does (live-verified). Each stands inside a loop of its own body — a block nested in the loop, and a handler of
 * such a block, are inside it — or is refused as {@code  Break used outside of loop} (92208 / P0000), the one
 * sentence for the four words. One that names a label names a loop around it, or is refused as
 * {@code  Label 'NOSUCH' not found} (92209 / P0000). Both are refused at the word, on a branch that never runs and
 * after a RETURN too. A procedure or a function whose body holds one is created all the same and refused when it is
 * called, so no body can end or skip a loop of the body that called it.
 */
public final class LoopControlPlacement {

    /** The sentence of a word standing outside every loop of its body. */
    static final String OUTSIDE_LOOP = "\n Break used outside of loop";

    /** How the sentence of a label no loop around the word carries opens. */
    static final String LABEL_OPENING = "\n Label '";

    /** How the sentence of a label no loop around the word carries closes. */
    static final String LABEL_CLOSING = "' not found";

    private LoopControlPlacement() {
    }

    /**
     * Judges every BREAK, CONTINUE and EXIT of a body.
     *
     * @param body the body's top block, or the parsed text that holds it
     */
    public static void require(final ParseTree body) {
        walk(body);
    }

    private static void walk(final ParseTree node) {
        if (node instanceof FrostlakeParser.BreakStatementContext) {
            judge((ParserRuleContext) node, ((FrostlakeParser.BreakStatementContext) node).loopLabel());
            return;
        }
        if (node instanceof FrostlakeParser.ContinueStatementContext) {
            judge((ParserRuleContext) node, ((FrostlakeParser.ContinueStatementContext) node).loopLabel());
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            walk(node.getChild(i));
        }
    }

    private static void judge(final ParserRuleContext statement, final FrostlakeParser.LoopLabelContext label) {
        final String wanted = label == null ? null : ScriptingNameValidator.canonical(label.getText());
        boolean inLoop = false;
        boolean named = wanted == null;
        for (ParserRuleContext up = statement.getParent(); up != null; up = up.getParent()) {
            if (!isLoop(up)) {
                continue;
            }
            inLoop = true;
            final FrostlakeParser.LoopLabelContext carried = labelOf(up);
            if (wanted != null && carried != null
                    && wanted.equals(ScriptingNameValidator.canonical(carried.getText()))) {
                named = true;
            }
        }
        final Token at = statement.getStart();
        if (!inLoop) {
            throw new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
                OUTSIDE_LOOP.substring(1)));
        }
        if (!named) {
            throw new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
                LABEL_OPENING.substring(1) + wanted + LABEL_CLOSING));
        }
    }

    private static boolean isLoop(final ParserRuleContext node) {
        return node instanceof FrostlakeParser.LoopStatementContext
            || node instanceof FrostlakeParser.WhileStatementContext
            || node instanceof FrostlakeParser.ForStatementContext
            || node instanceof FrostlakeParser.RepeatStatementContext;
    }

    private static FrostlakeParser.LoopLabelContext labelOf(final ParserRuleContext loop) {
        if (loop instanceof FrostlakeParser.LoopStatementContext) {
            return ((FrostlakeParser.LoopStatementContext) loop).loopLabel();
        }
        if (loop instanceof FrostlakeParser.WhileStatementContext) {
            return ((FrostlakeParser.WhileStatementContext) loop).loopLabel();
        }
        if (loop instanceof FrostlakeParser.ForStatementContext) {
            return ((FrostlakeParser.ForStatementContext) loop).loopLabel();
        }
        return ((FrostlakeParser.RepeatStatementContext) loop).loopLabel();
    }
}
