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

import java.util.List;
import org.antlr.v4.runtime.Token;

/**
 * The lines live reports for a block's plain assignment whose value opens with a parenthesis and holds an object
 * constant with a key that is no text (all live-verified):
 *
 * <pre>
 *   m := (1 + {1: 'a'});                      '1': the first token in the parentheses, the rest passed over
 *   m := ((1 + {1: 'a'}));                    '1'
 *   m := (({1: 'a'}));                        '{'
 *   m := ({1: 'a'});                          '1': a constant the parenthesis opens on is refused at its first key
 *   m := ({'k': 1, 2: 'b'});                  ''k''
 *   m := ({k: 'a', 2: 'b'});                  'k', then its values: ''a'', ''b'' (see ObjectConstantValueLines)
 *   m := ({1: 'a'}::ARRAY(NUMBER));           '1', then the ')' after NUMBER: a name in a bracket of its own
 *   m := ({1: x});                            '1', and the report ends at the name x
 *   x := (SELECT {1: 'a'});                   '1', then ''a'': a query reads the constant as a query does
 *   m := ({1: 'a'}) + 1;                      '1', then the '+' after the parentheses, which ends the report
 *   m := ({1: 'a'}) zz; RETURN m;             '1', 'zz', then the RETURN after the name
 * </pre>
 *
 * <p>A ';' right after the parentheses ends the statement soundly, and the block is read on.
 */
final class BlockParenthesizedConstantLines {

    /** The value is no shape this reading is modelled for. */
    static final int UNMODELLED = -2;

    private BlockParenthesizedConstantLines() {
    }

    /**
     * Adds the lines of such a value.
     *
     * @param sql    the block's text
     * @param spoken the block's tokens, end of input included
     * @param open   the index of the '(' the value opens with
     * @param brace  the index of the constant's '{'
     * @param fault  the index of the constant's fault: its key, or the ':' after a key that is a word
     * @param lines  the lines, added to
     * @return the index after the ';' that ends the statement soundly, -1 once the report has ended, or
     *         {@link #UNMODELLED}
     */
    static int read(final String sql, final List<Token> spoken, final int open, final int brace, final int fault,
                    final List<String> lines) {
        final int close = closing(spoken, open);
        if (close < 0 || brace < open || brace > close) {
            return UNMODELLED;
        }
        final int first = spoken.get(open + 1).getType();
        if (first == FrostlakeLexer.SELECT || first == FrostlakeLexer.WITH) {
            final List<String> asQuery = ObjectConstantKeyLines.lines(sql, spoken, spoken.get(fault).getTokenIndex());
            if (asQuery == null) {
                return UNMODELLED;
            }
            lines.addAll(asQuery);
        } else if (brace == open + 1) {
            final Token key = spoken.get(brace + 1);
            lines.add(SyntaxErrorListener.sentence(key));
            if (BlockAssignmentValueLines.takenForName(key)
                    && spoken.get(brace + 2).getType() == FrostlakeLexer.COLON) {
                ObjectConstantValueLines.addValues(spoken, brace, brace, lines);
            } else if (!readsPastNames(spoken, brace + 2, close, lines)) {
                return -1;
            }
        } else {
            int inner = open + 1;
            while (spoken.get(inner).getType() == FrostlakeLexer.LPAREN) {
                inner++;
            }
            lines.add(SyntaxErrorListener.sentence(spoken.get(inner)));
        }
        final Token after = spoken.get(close + 1);
        if (after.getType() == FrostlakeLexer.SEMI) {
            return close + 2;
        }
        if (after.getType() != Token.EOF) {
            lines.add(SyntaxErrorListener.sentence(after));
            if (BlockAssignmentValueLines.takenForName(after)) {
                BlockAssignmentValueLines.readOn(spoken, close + 1, lines);
            }
        }
        return -1;
    }

    /**
     * Reads the parentheses on after the constant's first key: a name not opening a call ends the report, with the
     * token after it refused unless that closes the constant or the parentheses.
     *
     * @return whether the parentheses were read through
     */
    private static boolean readsPastNames(final List<Token> spoken, final int from, final int close,
                                          final List<String> lines) {
        int braces = 1;
        for (int i = from; i < close; i++) {
            final Token token = spoken.get(i);
            if (token.getType() == FrostlakeLexer.LBRACE) {
                braces++;
            } else if (token.getType() == FrostlakeLexer.RBRACE) {
                braces--;
            }
            if (BlockAssignmentValueLines.takenForName(token)
                    && spoken.get(i + 1).getType() != FrostlakeLexer.LPAREN) {
                final Token next = spoken.get(i + 1);
                final boolean closesConstant = braces == 1 && next.getType() == FrostlakeLexer.RBRACE;
                if (i + 1 != close && !closesConstant) {
                    lines.add(SyntaxErrorListener.sentence(next));
                }
                return false;
            }
        }
        return true;
    }

    /** The index of the ')' that closes the '(' at {@code open}, or -1 when none does. */
    private static int closing(final List<Token> spoken, final int open) {
        int depth = 0;
        for (int i = open + 1; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                if (depth == 0) {
                    return i + 1 < spoken.size() ? i : -1;
                }
                depth--;
            }
        }
        return -1;
    }
}
