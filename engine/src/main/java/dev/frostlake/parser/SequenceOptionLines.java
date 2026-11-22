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
 * The lines the account reports for a fault in the options of a CREATE SEQUENCE or of an ALTER SEQUENCE, with or
 * without its SET. The options stand one after another, with or without commas. An option after the first that does
 * not read whole is refused as follows (all live-verified):
 *
 * <pre>
 *   SET INCREMENT = 2 x               'x': with no comma before it, the option's first token is named
 *   SET INCREMENT = 2 x y             'x'
 *   SET INCREMENT = 2 x =             'x'
 *   SET INCREMENT = 2 INCREMENT x     'INCREMENT'
 *   SET INCREMENT = 2 CASE            'CASE'
 *   SET INCREMENT = 2 COMMENT x       'x': a COMMENT names the token after it,
 *   SET INCREMENT = 2 COMMENT =       '=': or its '=' where no value follows
 *   SET INCREMENT = 2 DEFAULT         the end of input: a DEFAULT that ends the statement names the token after it
 *   SET INCREMENT = 2, x y            ',': after a comma, a name before another name or a comma names the comma,
 *                                     and no later statement is read
 *   SET INCREMENT = 2, x 3            '3': a name before any other token names that token
 *   SET INCREMENT = 2, x =            '=': a name whose '=' no value follows names the '='
 *   SET INCREMENT = 2, INCREMENT x    'INCREMENT'
 * </pre>
 *
 * <p>The first option is told as the parse tells it, but for a name where the number of an INCREMENT or a START is
 * meant, which is told twice unless an '=' follows it: {@code SET INCREMENT = x} is 'x' and 'x' again.
 */
final class SequenceOptionLines {

    /** How far past an option's first token the parse's own fault may stand for the option to be the faulty one. */
    private static final int REACH = 6;

    private SequenceOptionLines() {
    }

    /**
     * The lines for a statement whose first fault stands in such options, or {@code lines} unchanged for any other.
     *
     * @param spoken     the default-channel tokens, end of input included
     * @param faultIndex the token index of the statement's first fault, negative for the end of input
     * @param lines      the lines the parse reports
     * @return the lines
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex, final List<String> lines) {
        int at = -1;
        for (int i = 0; i < spoken.size() && at < 0; i++) {
            if (faultIndex < 0 ? spoken.get(i).getType() == Token.EOF : spoken.get(i).getTokenIndex() == faultIndex) {
                at = i;
            }
        }
        int start = at;
        while (start > 0 && spoken.get(start - 1).getType() != FrostlakeLexer.SEMI) {
            start--;
        }
        final int first = at < 0 ? -1 : firstOption(spoken, start);
        if (first < 0 || at < first) {
            return lines;
        }
        int end = optionEnd(spoken, first);
        if (end < 0) {
            final int number = numberSlot(spoken, first);
            if (number != at || !SyntaxErrorListener.isIdentifierToken(spoken.get(at))
                    || type(spoken, at + 1) == FrostlakeLexer.EQ) {
                return lines;
            }
            final List<String> twice = told(spoken, at, lines, true);
            twice.add(1, SyntaxErrorListener.sentence(spoken.get(at)));
            return twice;
        }
        while (!ends(spoken.get(end))) {
            final boolean comma = spoken.get(end).getType() == FrostlakeLexer.COMMA;
            final int option = comma ? end + 1 : end;
            final int next = optionEnd(spoken, option);
            if (next >= 0) {
                end = next;
                continue;
            }
            if (at < end || at > end + REACH) {
                return lines;
            }
            return comma ? afterComma(spoken, option, lines) : told(spoken, runIn(spoken, option), lines, true);
        }
        return lines;
    }

    /** The token a faulty option names where no comma stands before it. */
    private static int runIn(final List<Token> spoken, final int option) {
        final int type = spoken.get(option).getType();
        // A COMMENT names its '=' where no value follows, and any other token after it.
        if (type == FrostlakeLexer.COMMENT) {
            return option + 1;
        }
        return type == FrostlakeLexer.DEFAULT && ends(spoken.get(option + 1)) ? option + 1 : option;
    }

    /** The lines of a faulty option after a comma. */
    private static List<String> afterComma(final List<Token> spoken, final int option, final List<String> lines) {
        final Token first = spoken.get(option);
        final int type = first.getType();
        if (ends(first) || type == FrostlakeLexer.INCREMENT || type == FrostlakeLexer.START || !isPropertyName(first)) {
            return told(spoken, option, lines, true);
        }
        final Token after = spoken.get(option + 1);
        if (SyntaxErrorListener.isIdentifierToken(after) || after.getType() == FrostlakeLexer.COMMA) {
            return told(spoken, option - 1, lines, false);
        }
        return told(spoken, option + 1, lines, true);
    }

    /** The line of the token at {@code named}, then - where later statements are read - the lines after the first. */
    private static List<String> told(final List<Token> spoken, final int named, final List<String> lines,
            final boolean readsOn) {
        final List<String> told = new ArrayList<>();
        told.add(SyntaxErrorListener.sentence(spoken.get(named)));
        if (readsOn) {
            told.addAll(MultiTableInsertLines.laterLines(spoken, named, lines));
        }
        return told;
    }

    /** The index of the first option of the CREATE SEQUENCE or ALTER SEQUENCE that starts at {@code start}, or -1. */
    private static int firstOption(final List<Token> spoken, final int start) {
        final int verb = spoken.get(start).getType();
        if (verb != FrostlakeLexer.ALTER && verb != FrostlakeLexer.CREATE) {
            return -1;
        }
        int i = start + 1;
        if (verb == FrostlakeLexer.CREATE && type(spoken, i) == FrostlakeLexer.OR
                && type(spoken, i + 1) == FrostlakeLexer.REPLACE) {
            i += 2;
        }
        if (type(spoken, i) != FrostlakeLexer.SEQUENCE) {
            return -1;
        }
        i++;
        if (type(spoken, i) == FrostlakeLexer.IF) {
            i++;
            if (verb == FrostlakeLexer.CREATE && type(spoken, i) == FrostlakeLexer.NOT) {
                i++;
            }
            if (type(spoken, i) != FrostlakeLexer.EXISTS) {
                return -1;
            }
            i++;
        }
        if (!isWord(spoken, i)) {
            return -1;
        }
        i++;
        while (type(spoken, i) == FrostlakeLexer.DOT && isWord(spoken, i + 1)) {
            i += 2;
        }
        final int next = type(spoken, i);
        if (next == FrostlakeLexer.UNSET || next == FrostlakeLexer.RENAME || next == FrostlakeLexer.CLONE) {
            return -1;
        }
        return next == (verb == FrostlakeLexer.ALTER ? FrostlakeLexer.SET : FrostlakeLexer.WITH) ? i + 1 : i;
    }

    /** The index after the option that starts at {@code at}, or -1 where no whole option stands there. */
    private static int optionEnd(final List<Token> spoken, final int at) {
        final int type = type(spoken, at);
        if (type == FrostlakeLexer.ORDER || type == FrostlakeLexer.NOORDER) {
            return at + 1;
        }
        if (type == FrostlakeLexer.INCREMENT || type == FrostlakeLexer.START) {
            final int number = numberSlot(spoken, at);
            return type(spoken, number) == FrostlakeLexer.INTEGER_LITERAL ? number + 1 : -1;
        }
        if (at >= spoken.size() || !isPropertyName(spoken.get(at)) || type(spoken, at + 1) != FrostlakeLexer.EQ) {
            return -1;
        }
        return valueEnd(spoken, at + 2);
    }

    /** The index where the number of the INCREMENT or START at {@code at} is meant. */
    private static int numberSlot(final List<Token> spoken, final int at) {
        final int type = type(spoken, at);
        if (type != FrostlakeLexer.INCREMENT && type != FrostlakeLexer.START) {
            return -1;
        }
        int i = at + 1;
        if (type(spoken, i) == (type == FrostlakeLexer.INCREMENT ? FrostlakeLexer.BY : FrostlakeLexer.WITH)) {
            i++;
        }
        if (type(spoken, i) == FrostlakeLexer.EQ) {
            i++;
        }
        return type(spoken, i) == FrostlakeLexer.MINUS ? i + 1 : i;
    }

    /** The index after the property value that starts at {@code at}, or -1 where no value stands there. */
    private static int valueEnd(final List<Token> spoken, final int at) {
        final int type = type(spoken, at);
        if (type == FrostlakeLexer.LPAREN) {
            int depth = 0;
            for (int i = at; i < spoken.size() && !ends(spoken.get(i)); i++) {
                if (spoken.get(i).getType() == FrostlakeLexer.LPAREN) {
                    depth++;
                } else if (spoken.get(i).getType() == FrostlakeLexer.RPAREN) {
                    depth--;
                    if (depth == 0) {
                        return i + 1;
                    }
                }
            }
            return -1;
        }
        if (type == FrostlakeLexer.MINUS) {
            final int number = type(spoken, at + 1);
            return number == FrostlakeLexer.INTEGER_LITERAL || number == FrostlakeLexer.FLOAT_LITERAL ? at + 2 : -1;
        }
        if (at >= spoken.size() || ends(spoken.get(at)) || type == FrostlakeLexer.COMMA || type == FrostlakeLexer.EQ
                || type == FrostlakeLexer.RPAREN) {
            return -1;
        }
        int end = at + 1;
        while (isWord(spoken, end - 1) && type(spoken, end) == FrostlakeLexer.DOT && isWord(spoken, end + 1)) {
            end += 2;
        }
        return end;
    }

    /** Whether the token reads as a property's name: what an identifier may be, or a word read as one there. */
    private static boolean isPropertyName(final Token token) {
        final int type = token.getType();
        return SyntaxErrorListener.isNameLike(token) || type == FrostlakeLexer.COMMENT
            || type == FrostlakeLexer.DEFAULT || type == FrostlakeLexer.CAST || type == FrostlakeLexer.CONSTRAINT
            || type == FrostlakeLexer.CROSS || type == FrostlakeLexer.INNER || type == FrostlakeLexer.JOIN;
    }

    private static boolean isWord(final List<Token> spoken, final int at) {
        return at < spoken.size() && isPropertyName(spoken.get(at));
    }

    private static int type(final List<Token> spoken, final int at) {
        return at >= 0 && at < spoken.size() ? spoken.get(at).getType() : Token.INVALID_TYPE;
    }

    private static boolean ends(final Token token) {
        return token.getType() == Token.EOF || token.getType() == FrostlakeLexer.SEMI;
    }
}
