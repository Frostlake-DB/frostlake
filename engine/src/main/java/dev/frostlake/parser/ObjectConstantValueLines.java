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
 * The lines live reports for the values of an object constant it reads after a fault: the account reads each ':' as
 * a path's, which a name follows. The value after each ':' is told by its first token, unless that is a name (all
 * live-verified):
 *
 * <pre>
 *   {1: 'a'}           ''a''
 *   {1: x}             the constant's '}': a name is passed over, and the brace after the last value refused
 *   {1: x, 2: 'b'}     ''b'' alone: the ',' after a name is no fault
 *   {1: -2}            '-', then the constant's '}'
 *   {1: ABS(1)}        the '1' inside the call
 *   {1: {2: 'a'}}      '{', then the inner constant's values: ''a''
 * </pre>
 *
 * <p>A query tells them from the faulty pair on, after the key's own line. A SQL function's body the account reads
 * as an expression, refused before the constant, tells them from the constant's first pair - a constant whose keys
 * are all text included - when nothing stands between the refused token and the constant's brace, and from the
 * faulty pair on, after the key's line, when the refused token is that key.
 */
public final class ObjectConstantValueLines {

    private ObjectConstantValueLines() {
    }

    /**
     * Adds the lines of the values of the constant whose '{' is at {@code open}, from the pair whose ':' stands at
     * {@code from} or later.
     *
     * @param spoken the text's tokens, end of input included
     * @param open   the index of the constant's '{'
     * @param from   the index the pairs are read from
     * @param lines  the lines, added to
     */
    static void addValues(final List<Token> spoken, final int open, final int from, final List<String> lines) {
        int depth = 0;
        for (int i = open + 1; i + 1 < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN || type == FrostlakeLexer.LBRACKET || type == FrostlakeLexer.LBRACE) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.RBRACKET) {
                depth--;
            } else if (type == FrostlakeLexer.RBRACE) {
                if (depth == 0) {
                    return;
                }
                depth--;
            } else if (type == FrostlakeLexer.COLON && depth == 0 && i >= from) {
                addValue(spoken, i + 1, lines);
            }
        }
    }

    /** Adds the line, or lines, of the value whose first token is at {@code value}. */
    private static void addValue(final List<Token> spoken, final int value, final List<String> lines) {
        final Token first = spoken.get(value);
        if (first.getType() == Token.EOF) {
            return;
        }
        if (first.getType() == FrostlakeLexer.LBRACE) {
            lines.add(SyntaxErrorListener.sentence(first));
            addValues(spoken, value, value, lines);
            return;
        }
        final int end = valueEnd(spoken, value);
        final boolean last = end < spoken.size() && spoken.get(end).getType() == FrostlakeLexer.RBRACE;
        if (first.getType() == FrostlakeLexer.MINUS) {
            lines.add(SyntaxErrorListener.sentence(first));
            if (last) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(end)));
            }
            return;
        }
        if (BlockAssignmentValueLines.takenForName(first)) {
            if (value + 2 < spoken.size() && spoken.get(value + 1).getType() == FrostlakeLexer.LPAREN) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(value + 2)));
            } else if (last) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(end)));
            }
            return;
        }
        lines.add(SyntaxErrorListener.sentence(first));
    }

    /** The index of the ',' or '}' that ends the value whose first token is at {@code value}. */
    private static int valueEnd(final List<Token> spoken, final int value) {
        int depth = 0;
        for (int i = value; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN || type == FrostlakeLexer.LBRACKET || type == FrostlakeLexer.LBRACE) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.RBRACKET) {
                depth--;
            } else if (type == FrostlakeLexer.RBRACE) {
                if (depth == 0) {
                    return i;
                }
                depth--;
            } else if (type == FrostlakeLexer.COMMA && depth == 0) {
                return i;
            }
        }
        return spoken.size();
    }

    /**
     * The lines a SQL function's body tells for an object constant after the lines its reading as an expression
     * reports, or none: the values of the constant the last line's token stands before, or of the constant it is
     * the faulty key of.
     *
     * @param framed  the body's tokens in their frame, end of input included or not
     * @param refused the last line the reading reports
     * @return the further lines, possibly none
     */
    public static List<String> afterRefusal(final List<Token> framed, final String refused) {
        final List<String> lines = new ArrayList<>();
        final int[] at = refused == null ? null : SyntaxErrorListener.sentenceCoordinates(refused);
        if (at == null) {
            return lines;
        }
        int fault = -1;
        for (int i = 0; i < framed.size() && fault < 0; i++) {
            final Token token = framed.get(i);
            if (token.getType() != Token.EOF && SyntaxErrorListener.sentence(token).equals(refused)) {
                fault = i;
            }
        }
        if (fault < 0 || fault + 1 >= framed.size()) {
            return lines;
        }
        final int type = framed.get(fault).getType();
        if (type == FrostlakeLexer.LBRACE) {
            // A brace refused where a call's argument opens is not read as a constant.
            if (fault > 0 && framed.get(fault - 1).getType() != FrostlakeLexer.LPAREN) {
                addValues(framed, fault, fault, lines);
            }
        } else if (framed.get(fault + 1).getType() == FrostlakeLexer.LBRACE) {
            addValues(framed, fault + 1, fault + 1, lines);
        } else if (statementOpener(framed.get(fault)) && braceInStatement(framed, fault) >= 0) {
            addValues(framed, braceInStatement(framed, fault), fault + 1, lines);
        } else if (fault > 0 && framed.get(fault - 1).getType() == FrostlakeLexer.LBRACE
                || fault > 0 && framed.get(fault - 1).getType() == FrostlakeLexer.COMMA) {
            final int open = openBrace(framed, fault);
            if (open >= 0 && fault + 1 < framed.size() && framed.get(fault + 1).getType() == FrostlakeLexer.COLON
                    && framed.get(fault).getType() != FrostlakeLexer.STRING_LITERAL) {
                addValues(framed, open, fault + 1, lines);
            }
        }
        return lines;
    }

    /** Whether a refused token opens a block's statement: a LET or a RETURN. */
    private static boolean statementOpener(final Token token) {
        return token.getType() == FrostlakeLexer.LET || token.getType() == FrostlakeLexer.RETURN;
    }

    /** The index of the first '{' after the token at {@code at} in its statement, or -1. */
    private static int braceInStatement(final List<Token> framed, final int at) {
        for (int i = at + 1; i < framed.size(); i++) {
            final int type = framed.get(i).getType();
            if (type == FrostlakeLexer.SEMI || type == Token.EOF) {
                return -1;
            }
            if (type == FrostlakeLexer.LBRACE) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The index of the brace the token at {@code at} stands directly inside, no other bracket between, or -1.
     *
     * @param spoken the text's tokens
     * @param at     the index of a token
     * @return the brace's index, or -1
     */
    static int openBrace(final List<Token> spoken, final int at) {
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
}
