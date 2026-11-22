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

import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.Token;

/**
 * The lines the account reports where this parse names the token next to a word that opens what must follow (all
 * live-verified):
 *
 * <pre>
 *   SELECT * FROM fz LEFT            '&lt;EOF&gt;' at 21: LEFT, RIGHT, FULL and NATURAL alone after a relation, or
 *   SELECT * FROM fz LEFT WHERE …    'WHERE' at 22    right after FROM, are read on, and the token after is named
 *   SELECT 1 IS FROM fz              'IS' at 9: a query's IS, or IS NOT, that no NULL, DISTINCT or truth value follows
 *   SELECT * FROM fz AS QUALIFY      'QUALIFY' at 20, then '&lt;EOF&gt;' at 27: a word no alias may be after an AS,
 *                                    then the token after it
 * </pre>
 */
final class DanglingWordLines {

    private DanglingWordLines() {
    }

    /**
     * The lines for a text whose first fault is one of those, or null.
     *
     * @param spoken     the text's default-channel tokens, end of input included
     * @param faultIndex the token index of the token the parse's first line names, or -1 for the end of the input
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex) {
        int fault = faultIndex < 0 ? spoken.size() - 1 : -1;
        for (int i = 1; i < spoken.size() && fault < 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
            }
        }
        if (fault < 2) {
            return null;
        }
        final int type = spoken.get(fault).getType();
        final List<String> lines = new ArrayList<>();
        if (isLoneJoinWord(spoken, fault)) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(fault + 1)));
            return lines;
        }
        final int is = isAt(spoken, fault);
        if (is >= 0) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(is)));
            return lines;
        }
        if (type == FrostlakeLexer.AS && fault + 2 < spoken.size()
                && (spoken.get(fault + 1).getType() == FrostlakeLexer.QUALIFY
                    || spoken.get(fault + 1).getType() == FrostlakeLexer.ORDER)
                && SyntaxErrorListener.isNameLike(spoken.get(fault - 1)) && afterFrom(spoken, fault)) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(fault + 1)));
            lines.add(SyntaxErrorListener.sentence(spoken.get(fault + 2)));
            return lines;
        }
        return null;
    }

    /** Whether the fault is LEFT, RIGHT, FULL or NATURAL standing alone after a relation or a FROM. */
    private static boolean isLoneJoinWord(final List<Token> spoken, final int fault) {
        final int type = spoken.get(fault).getType();
        if (type != FrostlakeLexer.LEFT && type != FrostlakeLexer.RIGHT && type != FrostlakeLexer.FULL
                && type != FrostlakeLexer.NATURAL || fault + 1 >= spoken.size()) {
            return false;
        }
        final int next = spoken.get(fault + 1).getType();
        if (next == FrostlakeLexer.OUTER || next == FrostlakeLexer.JOIN || next == FrostlakeLexer.LEFT
                || next == FrostlakeLexer.RIGHT || next == FrostlakeLexer.FULL || next == FrostlakeLexer.INNER) {
            return false;
        }
        final Token before = spoken.get(fault - 1);
        return before.getType() == FrostlakeLexer.FROM
            || SyntaxErrorListener.isNameLike(before) && afterFrom(spoken, fault);
    }

    /** The IS, or the IS of an IS NOT, of a query's value that the fault stands right after, or -1. */
    private static int isAt(final List<Token> spoken, final int fault) {
        final int type = spoken.get(fault).getType();
        // A COMMENT's IS, and every other statement's own, is no operator.
        if (spoken.get(0).getType() != FrostlakeLexer.SELECT || type == FrostlakeLexer.NULL
                || type == FrostlakeLexer.DISTINCT || type == FrostlakeLexer.TRUE
                || type == FrostlakeLexer.FALSE || type == FrostlakeLexer.NOT) {
            return -1;
        }
        if (spoken.get(fault - 1).getType() == FrostlakeLexer.IS) {
            return fault - 1;
        }
        return spoken.get(fault - 1).getType() == FrostlakeLexer.NOT
            && spoken.get(fault - 2).getType() == FrostlakeLexer.IS ? fault - 2 : -1;
    }

    /** Whether a FROM stands before the token at {@code at} at its own depth of brackets, in the same statement. */
    private static boolean afterFrom(final List<Token> spoken, final int at) {
        int depth = 0;
        for (int i = at - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    return false;
                }
                depth--;
            } else if (type == FrostlakeLexer.SEMI) {
                return false;
            } else if (depth == 0 && type == FrostlakeLexer.FROM) {
                return true;
            }
        }
        return false;
    }
}
