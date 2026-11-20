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
 * The lines live reports for a scripting block whose faults are object constants with a key that is no text.
 *
 * <p>A plain assignment, {@code m := <value>}, is refused at the first token of its value. The account then reads on
 * from there: a ';' ends the statement soundly; a word that may be a name is taken, and the token after it - past any
 * ';' - is refused as well, which ends the report (all live-verified):
 *
 * <pre>
 *   m := {1: 'a'};                           '{'
 *   m := {1: 'a'}::MAP(NUMBER, VARCHAR);     '{', then the '(' after MAP
 *   m := {1: 'a'}::VARIANT; RETURN m;        '{', then 'RETURN'
 *   m := {TRUE: 'a'};                        '{', then the ':' after TRUE
 *   m := {1: x};                             '{', then the '}' after x
 *   m := 1 + {1: 'a'};                       '1'
 *   m := {1: 'a'}; m := {2: 'b'};            '{', then the second '{'
 * </pre>
 *
 * <p>A reserved word is no such word: {@code m := {1: 'a'} FROM t; RETURN m;} names the RETURN after {@code t;}. A
 * value that opens with a parenthesis is not read here.
 *
 * <p>Anywhere else in the block - a LET, a DEFAULT, a RETURN, a statement of its own - the constant is read as a
 * query reads it (see {@link ObjectConstantKeyLines}), and the block's later statements are read on.
 */
public final class BlockObjectConstantLines {

    private BlockObjectConstantLines() {
    }

    /**
     * The lines of a block's text whose first fault is such a constant's key, for a text read outside a parse.
     *
     * @param sql the block's text
     * @return the lines, or null when the text is no block or its first fault is no such key
     */
    public static List<String> linesOf(final String sql) {
        final String first = sql == null ? null : SyntaxErrorListener.firstLineReportedFor(sql);
        if (first == null) {
            return null;
        }
        final List<Token> spoken = BlockReadOn.spoken(sql);
        final int fault = nextFault(spoken, 0);
        final List<String> lines = fault < 0 ? null : read(sql, spoken, fault);
        // The parse's first line is the key's, or already the one read here.
        return lines == null || !first.equals(SyntaxErrorListener.sentence(spoken.get(fault)))
            && !first.equals(lines.get(0)) ? null : lines;
    }

    /**
     * The lines of a block's parse whose first fault is such a constant's key.
     *
     * @param sql          the text parsed
     * @param spoken       its default-channel tokens, end of input included
     * @param faultIndexes the token indexes of the tokens the parse's lines name, in order
     * @return the lines, or null when the text is no block or the first fault is no key of an object constant
     */
    static List<String> lines(final String sql, final List<Token> spoken, final List<Integer> faultIndexes) {
        if (faultIndexes.isEmpty()) {
            return null;
        }
        // The faults after the first are this parse's own way on from it: the account's are read from the tokens.
        final int faultIndex = faultIndexes.get(0).intValue();
        int first = -1;
        for (int i = 0; i < spoken.size() && first < 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                first = i;
            }
        }
        if (first < 0) {
            return null;
        }
        return read(sql, spoken, first);
    }

    /** The lines from the fault at {@code first} on, or null when the text is no block or the fault no key's. */
    private static List<String> read(final String sql, final List<Token> spoken, final int first) {
        if (spoken.isEmpty() || spoken.get(0).getType() != FrostlakeLexer.BEGIN
                && spoken.get(0).getType() != FrostlakeLexer.DECLARE || keyOf(spoken, first) < 0) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        int fault = first;
        while (fault >= 0) {
            final int open = openingBrace(spoken, keyOf(spoken, fault));
            final int assigned = BlockAssignmentValueLines.assignmentBefore(spoken, open);
            if (assigned >= 0 && spoken.get(assigned + 1).getType() == FrostlakeLexer.LPAREN) {
                // A value in parentheses is read another way: left to the parse's own lines.
                return lines.isEmpty() ? null : lines;
            }
            final int resume;
            if (assigned >= 0) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(assigned + 1)));
                resume = BlockAssignmentValueLines.readOn(spoken, assigned + 2, lines);
            } else {
                final List<String> asQuery = ObjectConstantKeyLines.lines(sql, spoken, spoken.get(fault).getTokenIndex());
                if (asQuery == null) {
                    return lines.isEmpty() ? null : lines;
                }
                lines.addAll(asQuery);
                resume = closing(spoken, open) + 1;
            }
            fault = resume < 0 ? -1 : nextFault(spoken, resume);
        }
        return lines;
    }

    /**
     * The key the fault at {@code fault} belongs to - the fault itself, or the token before a ':' the fault is - or -1
     * when the fault is no key of an object constant.
     */
    private static int keyOf(final List<Token> spoken, final int fault) {
        final int key = fault > 0 && spoken.get(fault).getType() == FrostlakeLexer.COLON ? fault - 1 : fault;
        // A signed number is a key of two tokens.
        final int last = spoken.get(key).getType() == FrostlakeLexer.MINUS ? key + 1 : key;
        if (key < 1 || last + 1 >= spoken.size() || spoken.get(last + 1).getType() != FrostlakeLexer.COLON
                || spoken.get(key - 1).getType() != FrostlakeLexer.LBRACE
                    && spoken.get(key - 1).getType() != FrostlakeLexer.COMMA
                || directlyInBrace(spoken, key) < 0) {
            return -1;
        }
        return key;
    }

    /** The index of the brace the token at {@code at} stands directly inside, no other bracket between, or -1. */
    private static int directlyInBrace(final List<Token> spoken, final int at) {
        int depth = 0;
        for (int i = at - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.RBRACKET || type == FrostlakeLexer.RBRACE) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN || type == FrostlakeLexer.LBRACKET) {
                if (depth == 0) {
                    return -1;
                }
                depth--;
            } else if (type == FrostlakeLexer.LBRACE) {
                if (depth == 0) {
                    return i;
                }
                depth--;
            }
        }
        return -1;
    }

    /** The index of the innermost brace open at the token at {@code at}, whatever else is open, or -1. */
    private static int openingBrace(final List<Token> spoken, final int at) {
        int depth = 0;
        for (int i = at - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RBRACE) {
                depth++;
            } else if (type == FrostlakeLexer.LBRACE) {
                if (depth == 0) {
                    return i;
                }
                depth--;
            }
        }
        return -1;
    }

    /** The index of the brace that closes the one at {@code open}, or the last token's when none does. */
    private static int closing(final List<Token> spoken, final int open) {
        int depth = 0;
        for (int i = open + 1; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LBRACE) {
                depth++;
            } else if (type == FrostlakeLexer.RBRACE) {
                if (depth == 0) {
                    return i;
                }
                depth--;
            }
        }
        return spoken.size() - 1;
    }

    /**
     * The fault of the first object constant from {@code from} on that has a key which is no text: the key, or the
     * ':' after a key that is a word. -1 when there is none.
     */
    private static int nextFault(final List<Token> spoken, final int from) {
        for (int i = Math.max(from, 1); i + 1 < spoken.size(); i++) {
            final int before = spoken.get(i - 1).getType();
            final int last = spoken.get(i).getType() == FrostlakeLexer.MINUS ? i + 1 : i;
            if (before != FrostlakeLexer.LBRACE && before != FrostlakeLexer.COMMA
                    || last + 1 >= spoken.size() || spoken.get(last + 1).getType() != FrostlakeLexer.COLON
                    || spoken.get(i).getType() == FrostlakeLexer.STRING_LITERAL
                    || directlyInBrace(spoken, i) < 0) {
                continue;
            }
            return BlockAssignmentValueLines.takenForName(spoken.get(i)) ? i + 1 : i;
        }
        return -1;
    }
}
