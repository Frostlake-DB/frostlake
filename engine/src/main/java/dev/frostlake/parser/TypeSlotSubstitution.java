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

package dev.frostlake.parser;

import java.util.ArrayDeque;
import java.util.Deque;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * The substitution check behind the unsupported-data-type sentence: a doubtful name replaced by a known type must make
 * the statement parse cleanly AND be read as a type there. A known type name is also an ordinary identifier, so in a
 * select list the substitute reads as a column: {@code SELECT ABS((SELECT 1 foo (2)))} parses once {@code ABS(…)} becomes
 * {@code NUMBER}, yet nothing there is a type slot, and live refuses it as a syntax error (live-verified).
 */
final class TypeSlotSubstitution {

    private TypeSlotSubstitution() {
    }

    /**
     * Whether {@code substituted} parses cleanly with the token starting at character {@code start} read as a data type.
     *
     * @param substituted the text with the known type in place
     * @param start       the character where the known type begins
     * @return true when the substitute stands in a type slot of a clean parse
     */
    static boolean readsAsType(final String substituted, final int start) {
        final FrostlakeParser.SqlScriptContext tree = SyntaxErrorListener.cleanParse(substituted);
        return tree != null && typeAt(tree, start);
    }

    /**
     * Whether a parse of {@code text}, whose first fault stands past character {@code start}, reads the token starting
     * there as a data type: the tree up to that fault is the one a clean parse would build, so a short word widened to
     * a type is judged where the rest of its statement still faults ({@code ALTER TABLE t DROP COLUMN c INT;} reads no
     * type, {@code CREATE FUNCTION f(a INT c)} does).
     *
     * @param text  the text with the known type in place
     * @param start the character where the known type begins
     * @return true when the substitute stands in a type slot
     */
    static boolean readsAsTypeBeforeFault(final String text, final int start) {
        return typeAt(SyntaxErrorListener.anyParse(text), start);
    }

    /** Whether the token of a tree starting at character {@code start} stands within a data type. */
    private static boolean typeAt(final ParseTree tree, final int start) {
        final Deque<ParseTree> pending = new ArrayDeque<>();
        pending.push(tree);
        while (!pending.isEmpty()) {
            final ParseTree node = pending.pop();
            if (node instanceof TerminalNode && ((TerminalNode) node).getSymbol().getStartIndex() == start) {
                for (ParseTree up = node.getParent(); up != null; up = up.getParent()) {
                    if (up instanceof FrostlakeParser.DataTypeNameContext) {
                        return true;
                    }
                }
                return false;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                pending.push(node.getChild(i));
            }
        }
        return false;
    }
}
