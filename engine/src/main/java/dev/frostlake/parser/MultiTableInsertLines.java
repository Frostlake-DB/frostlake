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
 * The lines the account reports for a multi-table INSERT whose INTO is followed by brackets that open no query, or
 * by a comma. Brackets after an INTO's VALUES row, its column list or its table are read as the INSERT's query in
 * brackets: the first token inside that opens no query is refused, the text is skipped to the bracket that closes
 * it, and the token after is refused as well, which ends the report - no later statement is read. Where the
 * brackets end the statement, the later statements are read (all live-verified):
 *
 * <pre>
 *   INSERT ALL INTO t VALUES (1) ('x') SELECT 1; SELECT 1 x y    ''x'', then the first 'SELECT'
 *   INSERT ALL INTO t VALUES (1) ('x') INTO t VALUES (2) …       ''x'', then 'INTO'
 *   INSERT ALL INTO t VALUES (1) ((1)) SELECT 1                  '1', then 'SELECT'
 *   INSERT ALL INTO t VALUES (1) (1)) SELECT 1                   '1', then the second ')'
 *   INSERT ALL INTO t (a) (b) SELECT 1                           'b', then 'SELECT'
 *   INSERT ALL INTO t VALUES (1) ('x'); SELECT 1 x y             ''x'', then the later statement's 'y'
 *   INSERT ALL INTO t VALUES (1), (2) x SELECT 1                 ',' alone: a comma after a VALUES row ends the
 *                                                                statement's report
 * </pre>
 */
final class MultiTableInsertLines {

    private MultiTableInsertLines() {
    }

    /**
     * The lines for a statement whose first fault is one of those, or {@code lines} unchanged for any other.
     *
     * @param spoken     the default-channel tokens, end of input included
     * @param faultIndex the token index of the statement's first fault, negative for the end of input
     * @param lines      the lines the parse reports
     * @return the lines
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex, final List<String> lines) {
        int at = -1;
        for (int i = 0; i < spoken.size() && at < 0; i++) {
            if (faultIndex >= 0 && spoken.get(i).getTokenIndex() == faultIndex) {
                at = i;
            }
        }
        int start = at;
        while (start > 0 && spoken.get(start - 1).getType() != FrostlakeLexer.SEMI) {
            start--;
        }
        if (at < 0 || start + 3 > at || spoken.get(start).getType() != FrostlakeLexer.INSERT) {
            return lines;
        }
        final int kind = spoken.get(start + 1).getType() == FrostlakeLexer.OVERWRITE ? start + 2 : start + 1;
        if (spoken.get(kind).getType() != FrostlakeLexer.ALL && spoken.get(kind).getType() != FrostlakeLexer.FIRST) {
            return lines;
        }
        final List<String> told = new ArrayList<>();
        told.add(SyntaxErrorListener.sentence(spoken.get(at)));
        if (spoken.get(at).getType() == FrostlakeLexer.COMMA) {
            if (!closesValuesRow(spoken, at - 1)) {
                return lines;
            }
            told.addAll(laterLines(spoken, at, lines));
            return told;
        }
        int open = at;
        while (spoken.get(open - 1).getType() == FrostlakeLexer.LPAREN) {
            open--;
        }
        if (open == at || !endsInto(spoken, open - 1)) {
            return lines;
        }
        int depth = at - open;
        int close = at;
        while (depth >= at - open && !ends(spoken.get(close))) {
            final int type = spoken.get(close).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                depth--;
            }
            close++;
        }
        if (depth >= at - open) {
            return lines;
        }
        while (depth > 0 && spoken.get(close).getType() == FrostlakeLexer.RPAREN) {
            depth--;
            close++;
        }
        if (ends(spoken.get(close))) {
            told.addAll(laterLines(spoken, close, lines));
        } else {
            told.add(SyntaxErrorListener.sentence(spoken.get(close)));
        }
        return told;
    }

    /**
     * The lines that tell a token of a later statement: those of {@code lines} that name a token after the
     * semicolon that ends the statement {@code from} stands in.
     */
    static List<String> laterLines(final List<Token> spoken, final int from, final List<String> lines) {
        int end = from;
        while (!ends(spoken.get(end))) {
            end++;
        }
        final List<String> later = new ArrayList<>();
        for (final String line : lines) {
            boolean named = false;
            for (int i = end + 1; i < spoken.size() && !named; i++) {
                named = line.equals(SyntaxErrorListener.sentence(spoken.get(i)));
            }
            if (named) {
                later.add(line);
            }
        }
        return later;
    }

    /** Whether the token at {@code at} ends an INTO: its VALUES row's or column list's ')' or its table's name. */
    private static boolean endsInto(final List<Token> spoken, final int at) {
        if (spoken.get(at).getType() == FrostlakeLexer.RPAREN) {
            final int open = opening(spoken, at);
            return open > 0 && (spoken.get(open - 1).getType() == FrostlakeLexer.VALUES
                || namesTable(spoken, open - 1));
        }
        return namesTable(spoken, at);
    }

    /** Whether the ')' at {@code at} closes the row of an INTO's VALUES. */
    private static boolean closesValuesRow(final List<Token> spoken, final int at) {
        if (spoken.get(at).getType() != FrostlakeLexer.RPAREN) {
            return false;
        }
        final int open = opening(spoken, at);
        return open > 0 && spoken.get(open - 1).getType() == FrostlakeLexer.VALUES;
    }

    /** The index of the '(' the ')' at {@code close} closes, or -1. */
    private static int opening(final List<Token> spoken, final int close) {
        int depth = 0;
        for (int i = close; i > 0 && spoken.get(i).getType() != FrostlakeLexer.SEMI; i--) {
            if (spoken.get(i).getType() == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (spoken.get(i).getType() == FrostlakeLexer.LPAREN) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** Whether the token at {@code at} is the last part of the name an INTO is written with. */
    private static boolean namesTable(final List<Token> spoken, final int at) {
        int i = at;
        while (i > 1 && SyntaxErrorListener.isNameLike(spoken.get(i))
                && spoken.get(i - 1).getType() == FrostlakeLexer.DOT) {
            i -= 2;
        }
        return i > 0 && SyntaxErrorListener.isNameLike(spoken.get(i))
            && spoken.get(i - 1).getType() == FrostlakeLexer.INTO;
    }

    private static boolean ends(final Token token) {
        return token.getType() == Token.EOF || token.getType() == FrostlakeLexer.SEMI;
    }
}
