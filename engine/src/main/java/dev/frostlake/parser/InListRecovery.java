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
 * The lines live reports for an IN whose list this parser could not read. Unable to take the predicate, the parse
 * ends the clause before the IN and refuses the IN itself; live reads the IN and refuses the first token its list
 * cannot take (all live-verified):
 *
 * <pre>
 *   SELECT a FROM t WHERE a IN (                        '&lt;EOF&gt;' at 28
 *   SELECT a FROM t WHERE a IN (1 2)                    '2' at 30
 *   SELECT a FROM t WHERE a IN ()                       ')' at 28
 *   SELECT a FROM t WHERE a IN (SELECT 1 FROM)          'SELECT' at 28
 *   SELECT a FROM t WHERE a IN (SELECT 1 foo (2))       'SELECT' at 28, then ')' at 44
 * </pre>
 *
 * <p>A list is read the way an operand in brackets is, so the text is parsed again with the IN spelled as an equals
 * sign followed by a blank, every position where it was written. A list holding a subquery that cannot be read is
 * refused at its SELECT or WITH instead; live's recovery then closes the list at the first ')' after it, and a fault
 * after that still speaks — unless the query's fault stands inside a call of a later select item, which reads as a
 * query compared in a WHERE does: {@code a IN (SELECT n, ABS(1 2) FROM t)} is 'SELECT', '2', then the 'FROM'
 * ({@link ValueSubqueryLines#inListCallFault}); one whose first item holds a window call with a frame is read past
 * that call, to its OVER: {@code a IN (SELECT SUM(v) OVER (ORDER BY v ROWS …) FROM t)} is 'SELECT', then 'OVER'
 * (live-verified). Where no operator may stand either, the line falls at the IN's own
 * place, and names the IN as written ({@code SHOW VARIABLES IN ACCOUNT} is {@code unexpected 'IN'} at 15), never the
 * equals sign read in its stead. A SHOW listing that reads no scope is that one line alone, whatever follows its
 * IN.
 */
final class InListRecovery {

    private InListRecovery() {
    }

    /**
     * The lines live reports when the parse's one line names an IN, or null when it names anything else or the text
     * reads cleanly with the IN taken.
     *
     * @param sql            the text parsed
     * @param spoken         its default-channel tokens, end of input included
     * @param namedIndex     the token index of the token the parse's line names
     * @param statementParse whether the text was parsed as a whole statement
     * @return the lines, or null
     */
    static List<String> refusal(final String sql, final List<Token> spoken, final int namedIndex,
                                final boolean statementParse) {
        if (sql == null) {
            return null;
        }
        int in = -1;
        for (int i = 1; i + 1 < spoken.size() && in < 0; i++) {
            if (spoken.get(i).getTokenIndex() == namedIndex && spoken.get(i).getType() == FrostlakeLexer.IN) {
                in = i;
            }
        }
        boolean emptyNamed = false;
        for (int i = 3; i + 1 < spoken.size() && in < 0 && statementParse; i++) {
            if (spoken.get(i).getTokenIndex() == namedIndex && spoken.get(i).getType() == FrostlakeLexer.RPAREN
                    && spoken.get(i - 1).getType() == FrostlakeLexer.LPAREN
                    && spoken.get(i - 2).getType() == FrostlakeLexer.IN) {
                // The parse named the ')' of an empty list rather than its IN.
                in = i - 2;
                emptyNamed = true;
            }
        }
        if (in < 0 || spoken.get(in).getStopIndex() != spoken.get(in).getStartIndex() + 1) {
            return null;
        }
        if (emptyNamed && opensListing(spoken, in)) {
            return null;
        }
        if (opensListing(spoken, in)) {
            // ★ A LISTING THAT READS NO SCOPE is refused at its IN and nothing more: SHOW VARIABLES IN a.b is the one
            // line, whatever the IN is followed by.
            final List<String> alone = new ArrayList<>();
            alone.add(SyntaxErrorListener.sentence(spoken.get(in)));
            return alone;
        }
        final boolean statementWord = spoken.get(in + 1).getType() == FrostlakeLexer.LPAREN
            && ValueSubqueryLines.inListStatementWord(sql, spoken, in + 1) != null;
        final List<String> enclosing = statementParse && !statementWord
            ? ValueSubqueryLines.enclosingResumed(sql, spoken, in) : null;
        if (enclosing == null && emptyNamed) {
            return emptyListLines(sql, spoken, in);
        }
        if (enclosing != null) {
            // A faulty query used as a value around the IN is refused first, and its reading resumes before the IN.
            return enclosing;
        }
        final char[] text = sql.toCharArray();
        text[spoken.get(in).getStartIndex()] = '=';
        text[spoken.get(in).getStartIndex() + 1] = ' ';
        final List<String> lines = new ArrayList<>();
        final int opener = in + 2;
        if (spoken.get(in + 1).getType() == FrostlakeLexer.LPAREN && opener < spoken.size()
                && (spoken.get(opener).getType() == FrostlakeLexer.SELECT
                    || spoken.get(opener).getType() == FrostlakeLexer.WITH)) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(opener)));
            // A window call with a frame in the query's first item is read past the call, to its OVER (live-verified).
            final int over = framedWindowOver(spoken, opener);
            if (over >= 0) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(over)));
                return lines;
            }
            final List<String> called = ValueSubqueryLines.inListCallFault(sql, spoken, in + 1);
            if (called != null) {
                lines.addAll(called);
                return lines;
            }
            // A statement its FROM list reads after a comma - or the FROM list of a query nested in it - is refused as
            // in a compared query, each faulty query around the list refused at its SELECT first.
            final List<String> worded = ValueSubqueryLines.inListStatementWord(sql, spoken, in + 1);
            if (worded != null) {
                return worded;
            }
            // A token the query's reading resumes at, before its ')', reads the statement on from there.
            final List<String> resumed = statementParse ? ValueSubqueryLines.inListResumed(sql, spoken, in + 1) : null;
            if (resumed != null) {
                lines.addAll(resumed);
                return lines;
            }
            int close = -1;
            for (int i = opener + 1; i < spoken.size() && close < 0; i++) {
                if (spoken.get(i).getType() == FrostlakeLexer.RPAREN) {
                    close = i;
                }
            }
            if (close < 0) {
                return lines;
            }
            text[spoken.get(in + 1).getStartIndex()] = '1';
            for (int i = opener; i <= close; i++) {
                for (int c = spoken.get(i).getStartIndex(); c <= spoken.get(i).getStopIndex(); c++) {
                    if (text[c] != '\n' && text[c] != '\r') {
                        text[c] = ' ';
                    }
                }
            }
            final int[] at = SyntaxErrorListener.sentenceCoordinates(lines.get(0));
            for (final String line : SyntaxErrorListener.linesReportedFor(new String(text), statementParse)) {
                final int[] place = SyntaxErrorListener.sentenceCoordinates(line);
                if (place != null && (place[0] > at[0] || place[0] == at[0] && place[1] > at[1])) {
                    lines.add(line);
                }
            }
            return lines;
        }
        if (statementParse && spoken.get(in + 1).getType() == FrostlakeLexer.LPAREN && in + 2 < spoken.size()
                && spoken.get(in + 2).getType() == FrostlakeLexer.RPAREN) {
            return emptyListLines(sql, spoken, in);
        }
        final String inPlace = SyntaxErrorListener.sentence(spoken.get(in));
        final int[] inAt = SyntaxErrorListener.sentenceCoordinates(inPlace);
        for (final String line : SyntaxErrorListener.linesReportedFor(new String(text), statementParse)) {
            final int[] place = SyntaxErrorListener.sentenceCoordinates(line);
            if (place != null && inAt != null && (place[0] < inAt[0] || place[0] == inAt[0] && place[1] < inAt[1])) {
                // The equals sign read the words before it otherwise — SELECT SHOW TABLES = a as an assignment after
                // a statement — so no predicate stands there: the IN after a select item's alias is refused alone.
                final List<String> alone = new ArrayList<>();
                alone.add(inPlace);
                return alone;
            }
            lines.add(place != null && inAt != null && place[0] == inAt[0] && place[1] == inAt[1] ? inPlace : line);
        }
        return lines.isEmpty() ? null : lines;
    }

    /**
     * The lines of an IN whose list is empty: its ')' is refused and closes the list, and the statement reads on past
     * it (live-verified): {@code a IN ())} is that ')' and the next, {@code (a IN ())} the list's ')' alone; null when
     * the reading on reports a line before it.
     */
    private static List<String> emptyListLines(final String sql, final List<Token> spoken, final int in) {
        final char[] text = sql.toCharArray();
        text[spoken.get(in).getStartIndex()] = '=';
        text[spoken.get(in).getStartIndex() + 1] = ' ';
        final Token closer = spoken.get(in + 2);
        text[spoken.get(in + 1).getStartIndex()] = '1';
        text[closer.getStartIndex()] = ' ';
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(closer));
        final int[] at = SyntaxErrorListener.sentenceCoordinates(lines.get(0));
        for (final String line : SyntaxErrorListener.linesReportedFor(new String(text), true)) {
            final int[] place = SyntaxErrorListener.sentenceCoordinates(line);
            if (place == null || at == null || place[0] < at[0] || place[0] == at[0] && place[1] <= at[1]) {
                return null;
            }
            lines.add(line);
        }
        return lines;
    }

    /** Whether the statement the token at {@code index} stands in opens on SHOW. */
    private static boolean opensListing(final List<Token> spoken, final int index) {
        int first = index;
        while (first > 0 && spoken.get(first - 1).getType() != FrostlakeLexer.SEMI) {
            first--;
        }
        return spoken.get(first).getType() == FrostlakeLexer.SHOW;
    }

    /**
     * The OVER after the first window call of the first select item of the query whose SELECT is at {@code select},
     * when its window has a frame (ROWS or RANGE), or -1.
     */
    private static int framedWindowOver(final List<Token> spoken, final int select) {
        int depth = 0;
        for (int i = select + 1; i < spoken.size(); i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                if (depth == 0) {
                    return -1;
                }
                depth--;
            } else if (depth == 0 && (type == FrostlakeLexer.COMMA || type == FrostlakeLexer.FROM
                    || type == FrostlakeLexer.SEMI || type == Token.EOF)) {
                return -1;
            } else if (depth == 0 && type == FrostlakeLexer.OVER && spoken.get(i - 1).getType() == FrostlakeLexer.RPAREN
                    && i + 1 < spoken.size() && spoken.get(i + 1).getType() == FrostlakeLexer.LPAREN) {
                int inner = 0;
                for (int j = i + 2; j < spoken.size(); j++) {
                    final int word = spoken.get(j).getType();
                    if (word == FrostlakeLexer.LPAREN) {
                        inner++;
                    } else if (word == FrostlakeLexer.RPAREN) {
                        if (inner == 0) {
                            return -1;
                        }
                        inner--;
                    } else if (inner == 0 && (word == FrostlakeLexer.ROWS || word == FrostlakeLexer.RANGE)) {
                        return i;
                    } else if (word == FrostlakeLexer.SEMI || word == Token.EOF) {
                        return -1;
                    }
                }
                return -1;
            }
        }
        return -1;
    }
}
