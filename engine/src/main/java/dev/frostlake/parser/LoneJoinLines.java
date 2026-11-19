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
 * The lines live reports for a JOIN standing where a table is named - after FROM, after a comma of the FROM list,
 * or after a join's own JOIN: the token after it is told twice, once for each way the word is read
 * (live-verified):
 *
 * <pre>
 *   SELECT * FROM JOIN                          the end of the input, twice
 *   WITH JOIN AS (SELECT 1 a) SELECT * FROM JOIN WHERE a = 1     'WHERE' twice
 *   SELECT * FROM fz, JOIN                      the end of the input, twice
 *   SELECT * FROM fz JOIN JOIN ON 1 = 1         'ON' twice
 *   SELECT * FROM JOIN LIMIT 1                  'LIMIT', then the '1'
 *   SELECT * FROM JOIN JOIN fz                  the second 'JOIN', then 'fz'
 * </pre>
 *
 * <p>A name after the word is its alias and is told once, by the parse.
 */
final class LoneJoinLines {

    private LoneJoinLines() {
    }

    /**
     * @param spoken     the text's default-channel tokens, end of input included
     * @param faultIndex the token index of the token the parse's first line names
     * @return the lines, or null when the fault stands after no such JOIN
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex) {
        int fault = -1;
        for (int i = 0; i < spoken.size() && fault < 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
            }
        }
        if (fault < 0 && faultIndex < 0) {
            // A fault at the end of the input.
            fault = spoken.size() - 1;
        }
        if (fault < 2) {
            return null;
        }
        final int join;
        if (spoken.get(fault - 1).getType() == FrostlakeLexer.JOIN && namesTable(spoken, fault - 1)) {
            join = fault - 1;
        } else if (fault > 2 && spoken.get(fault - 2).getType() == FrostlakeLexer.JOIN
                && namesTable(spoken, fault - 2)) {
            join = fault - 2;
        } else {
            return null;
        }
        int depth = 0;
        for (int i = 0; i < join; i++) {
            final int bracket = spoken.get(i).getType();
            if (bracket == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (bracket == FrostlakeLexer.RPAREN) {
                depth--;
            }
        }
        if (depth != 0) {
            // A query inside brackets is read another way.
            return null;
        }
        final Token after = spoken.get(join + 1);
        final int type = after.getType();
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(after));
        if (type == FrostlakeLexer.LIMIT || type == FrostlakeLexer.JOIN) {
            if (join + 2 >= spoken.size()) {
                return null;
            }
            lines.add(SyntaxErrorListener.sentence(spoken.get(join + 2)));
            return lines;
        }
        if (join + 1 != fault || type != Token.EOF && type != FrostlakeLexer.COMMA && type != FrostlakeLexer.ORDER
                && type != FrostlakeLexer.AS && type != FrostlakeLexer.ON && type != FrostlakeLexer.WHERE
                && type != FrostlakeLexer.GROUP && type != FrostlakeLexer.RPAREN && type != FrostlakeLexer.SEMI
                && type != FrostlakeLexer.UNION) {
            return null;
        }
        lines.add(SyntaxErrorListener.sentence(after));
        return lines;
    }

    /** Whether the JOIN at {@code at} stands where a table is named. */
    private static boolean namesTable(final List<Token> spoken, final int at) {
        final int before = spoken.get(at - 1).getType();
        if (before == FrostlakeLexer.FROM || before == FrostlakeLexer.JOIN) {
            return true;
        }
        if (before != FrostlakeLexer.COMMA) {
            return false;
        }
        int depth = 0;
        for (int i = at - 2; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    return false;
                }
                depth--;
            } else if (depth == 0) {
                if (type == FrostlakeLexer.FROM) {
                    return true;
                }
                if (type == FrostlakeLexer.SELECT || type == FrostlakeLexer.WHERE || type == FrostlakeLexer.ON
                        || type == FrostlakeLexer.GROUP || type == FrostlakeLexer.ORDER
                        || type == FrostlakeLexer.HAVING || type == FrostlakeLexer.SEMI) {
                    return false;
                }
            }
        }
        return false;
    }
}
