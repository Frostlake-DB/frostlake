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
import java.util.Arrays;
import java.util.List;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.IntervalSet;

/**
 * The lines live reports for a fault inside a parenthesized query used as a value — a scalar subquery, or the
 * query of an IN, ANY, ALL or SOME — as an operand, a call's argument or a comparison's side. Live first refuses
 * that query's SELECT, once for each such query around the fault, and then depends on where the query stands (all
 * live-verified):
 *
 * <pre>
 *   SELECT (SELECT 1 x y) FROM t; SELECT 1 x y     'SELECT' at 8, 'y' at 19: in a select list the fault inside is
 *                                                  reported next, and the report ends
 *   SELECT (SELECT (SELECT 1 x y))                 'SELECT' at 8 and at 16, then 'y'
 *   SELECT (SELECT 1 +), SELECT (SELECT)           'SELECT' alone: the query's own ')' is no line after an
 *                                                  operator, a FROM or the SELECT
 *   SELECT (SELECT 1 FROM t WHERE)                 'SELECT', then the ')': it is one after WHERE, JOIN and BY
 *   SELECT UPPER((SELECT 1 x y))                   'SELECT' alone: a call's argument, or inside three brackets
 *   SELECT COALESCE((SELECT 1 x y), 1)             'SELECT', then the call's ')' when more of the call follows
 *   SELECT 1 FROM t WHERE a = (SELECT 1 x y); …    'SELECT' alone, and the later statements are read: in a WHERE,
 *                                                  HAVING, QUALIFY, GROUP BY, ORDER BY, an UPDATE's SET, a VALUES
 *                                                  row or a CALL the fault inside is not reported - unless the
 *                                                  query's reading resumes inside it ({@link #resumedLines})
 *   … WHERE a = (SELECT 1 x y) OR a = (SELECT 2 z w)   both SELECTs
 * </pre>
 *
 * <p>A query whose first item is a call is read another way, wherever its fault stands, and with no faulty query
 * nested in it:
 *
 * <pre>
 *   SELECT (SELECT SUM(n IGNORE NULLS) FROM t)          'SELECT', then the call's '(': in a select list
 *   SELECT (SELECT ABS(1) 2 3), SELECT (SELECT SUM(n) FROM t u v)    the same, the fault past the call
 *   … WHERE a = (SELECT ABS(1 2)), … HAVING a = (…)     'SELECT', then '1': the first token in the call, in a WHERE
 *                                                       compared or not, a HAVING, a QUALIFY, an ORDER BY, a GROUP
 *                                                       BY, an UPDATE's SET and a select list's call argument
 *   SELECT COALESCE((SELECT ABS(1 2)), 1)               'SELECT', '1', then the call's ')'
 *   SELECT UPPER((SELECT ABS(1 2) FROM t))              'SELECT', '1', then the query's ')': past a FROM after the
 *                                                       call, whatever follows the query
 *   SELECT UPPER((SELECT SUM(n IGNORE NULLS)))          'SELECT', 'n', then the 'IGNORE': a word after the call's
 *                                                       first token, in a call's argument, ends the report
 *   … WHERE a &gt; (SELECT COUNT(x =&gt; *, 1) FROM t)          'SELECT', 'x', then the ',' after the star
 *   … WHERE a IN (SELECT SUM(n IGNORE NULLS) FROM t)    'SELECT', then the 'FROM': an IN's list, the token past
 *                                                       the call
 * </pre>
 *
 * <p>A window call whose frame holds the fault reads on (all live-verified): in a call's argument to the token after
 * the first one in its parentheses, whatever it is - {@code UPPER((SELECT SUM(v) OVER (… v PRECEDING …) …))} is
 * 'SELECT', 'v', then the ')' after it - and compared in a clause to its frame's ROWS or RANGE. A select list's query
 * whose first item opens with a name and an operator is read at that operator.
 *
 * <p>A fault inside a call of a later item reads on past the query's own reading (all live-verified):
 *
 * <pre>
 *   SELECT (SELECT n, ABS(1 2) FROM t)                  'SELECT', '2', then the query's ')': in a select list
 *   … WHERE a = (SELECT n, ABS(1 2) FROM t)             'SELECT', '2', then the 'FROM' past the call, and the
 *                                                       query's ')' after a clause the FROM's relation is followed
 *                                                       by; the same in an IN's list and an ORDER BY
 *   SELECT UPPER((SELECT n, ABS(1 2) FROM t))           'SELECT', '2', 'FROM', then the query's ')'
 * </pre>
 *
 * <p>A select list that opens with a name given an alias, or with a literal and then a name given one, is refused
 * at the alias first ({@link #leadingAliasLines}).
 *
 * <p>A derived table, a common table expression's body and an EXISTS are no such query: their own fault is the
 * line. The readings are rebuilt on the text — the query parsed on its own for the fault inside, the statement
 * with the query replaced by a value for what follows — every position staying where it was written.
 */
final class ValueSubqueryLines {

    /** The depth of calls whose query's CALL ends its lines at the name's parenthesis. */
    private static final int THREE_CALLS = 3;

    private static final IntervalSet WORDS =
        FrostlakeParser._ATN.nextTokens(FrostlakeParser._ATN.ruleToStartState[FrostlakeParser.RULE_identifier]);

    /** Where a faulty query stands: a select list, a call's argument there, too deep in brackets, or another clause. */
    private static final int ITEM = 0;
    private static final int ARGUMENT = 1;
    private static final int DEEP = 2;
    private static final int CLAUSE = 3;

    private ValueSubqueryLines() {
    }

    /**
     * The lines live reports when the statement holding the parse's first fault holds a faulty query used as a value
     * with no fault before it, or null.
     *
     * @param sql            the text parsed
     * @param spoken         its default-channel tokens, end of input included
     * @param faultIndex     the token index of the token the first line names, or -1 for the end of the input
     * @param statementParse whether the text was parsed as a whole statement
     * @return the lines, or null
     */
    static List<String> lines(final String sql, final List<Token> spoken, final int faultIndex,
                              final boolean statementParse) {
        if (sql == null || !statementParse || spoken.size() < 4) {
            return null;
        }
        int fault = spoken.size() - 1;
        for (int i = 0; i < spoken.size() && faultIndex >= 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
                break;
            }
        }
        int start = fault;
        while (start > 0 && spoken.get(start - 1).getType() != FrostlakeLexer.SEMI) {
            start--;
        }
        int end = fault;
        while (end < spoken.size() - 1 && spoken.get(end).getType() != FrostlakeLexer.SEMI) {
            end++;
        }
        final int[] found = faultyQuery(sql, spoken, start, end, start);
        if (found == null) {
            return null;
        }
        final int open = found[0];
        final int close = found[1];
        final int place = found[2];
        // The statement with the query replaced by a value: a fault before the query is not this reading's.
        final char[] valued = sql.toCharArray();
        blank(valued, spoken.get(open + 1).getStartIndex(), spoken.get(close - 1).getStopIndex());
        valued[spoken.get(open + 1).getStartIndex()] = '1';
        final int[] at = SyntaxErrorListener.sentenceCoordinates(SyntaxErrorListener.sentence(spoken.get(open)));
        final List<String> after = new ArrayList<>();
        for (final String line : SyntaxErrorListener.linesReportedAfresh(new String(valued))) {
            final int[] where = SyntaxErrorListener.sentenceCoordinates(line);
            if (where == null || where[0] < at[0] || where[0] == at[0] && where[1] < at[1]) {
                return null;
            }
            after.add(line);
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(open + 1)));
        final int firstCall = firstItemCall(spoken, open + 1, close);
        if (firstCall >= 0 && faultyQuery(sql, spoken, open + 2, close, -1) == null) {
            final List<String> called = afterCallFirst(spoken, open, close, place, found[4], firstCall);
            if (called != null) {
                lines.addAll(called);
                if (place == ARGUMENT) {
                    lines.addAll(argumentCallTail(spoken, close, firstCall, found[3], end));
                }
                return lines;
            }
        }
        if (firstCall < 0 && (place == ITEM || place == ARGUMENT)
                && faultyQuery(sql, spoken, open + 2, close, -1) == null) {
            final List<String> aliased = leadingAliasLines(sql, spoken, open, close, place);
            if (aliased != null) {
                lines.addAll(aliased);
                return lines;
            }
        }
        if (place == ITEM && firstCall < 0 && open + 3 < close && SyntaxErrorListener.isNameLike(spoken.get(open + 2))
                && isArithmetic(spoken.get(open + 3).getType()) && FrameOffsetLines.standsInFrame(spoken, fault)
                && faultyQuery(sql, spoken, open + 2, close, -1) == null) {
            // A frame's fault in a query of a select list whose first item opens with a name and an operator is read
            // as that operator (live-verified).
            lines.add(SyntaxErrorListener.sentence(spoken.get(open + 3)));
            return lines;
        }
        if (firstCall < 0 && faultyQuery(sql, spoken, open + 2, close, -1) == null) {
            final List<String> later = laterCallFault(sql, spoken, open, close, place);
            if (later != null) {
                lines.addAll(later);
                return lines;
            }
        }
        if (place == CLAUSE || place == ITEM) {
            final List<String> nested = nestedListedWordLines(sql, spoken, open);
            if (nested != null) {
                lines.addAll(nested);
                return framedWordLines(spoken, open, close, lines);
            }
        }
        final List<String> worded = statementWordLines(sql, spoken, open, close, found, end);
        if (worded != null) {
            lines.addAll(worded);
            return place == CLAUSE ? framedWordLines(spoken, open, close, lines) : lines;
        }
        if (place == CLAUSE && open >= 1 && spoken.get(open - 1).getType() == FrostlakeLexer.IN) {
            // An IN's query whose FROM writes a table function bare is read to that call's ')' - or, for a call whose
            // later arguments are texts, which carries the FROM list on, to the query's own ')' (live-verified).
            final int bare = bareCall(spoken, open, close);
            final int bareClose = bare > 0 ? closing(spoken, bare, close) : -1;
            if (bareClose > bare && bareClose < close) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(textsAfterFirst(spoken, bare, bareClose)
                    ? close : bareClose)));
                return lines;
            }
        }
        if (place == CLAUSE || place == ARGUMENT) {
            final int clause = found[4];
            final boolean direct = place == CLAUSE && found[5] == 0 && (clause == FrostlakeLexer.WHERE
                || clause == FrostlakeLexer.HAVING || clause == FrostlakeLexer.QUALIFY);
            final List<String> resumed = resumedLines(sql, spoken, open, close, direct, place == CLAUSE);
            if (resumed != null) {
                lines.addAll(resumed);
                return lines;
            }
        }
        if (place == CLAUSE) {
            // The queries inside it, each refused at its SELECT, then what follows the query.
            addNested(sql, spoken, open, close, lines);
            lines.addAll(after);
            return lines;
        }
        if (place == DEEP) {
            return lines;
        }
        if (place == ARGUMENT) {
            final int call = found[3];
            final int callClose = closing(spoken, call, end);
            if (callClose > close + 1 && callClose < end) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(callClose)));
            }
            return lines;
        }
        // The fault inside, read from the innermost faulty query parsed on its own.
        int innerOpen = open;
        int innerClose = close;
        int[] deeper = faultyQuery(sql, spoken, innerOpen + 2, innerClose, -1);
        while (deeper != null) {
            innerOpen = deeper[0];
            innerClose = deeper[1];
            lines.add(SyntaxErrorListener.sentence(spoken.get(innerOpen + 1)));
            deeper = faultyQuery(sql, spoken, innerOpen + 2, innerClose, -1);
        }
        final char[] alone = new char[sql.length()];
        Arrays.fill(alone, ' ');
        final int from = spoken.get(innerOpen + 1).getStartIndex();
        final int to = spoken.get(innerClose - 1).getStopIndex();
        for (int c = 0; c < alone.length; c++) {
            final char written = sql.charAt(c);
            if (c >= from && c <= to || written == '\n' || written == '\r') {
                alone[c] = written;
            }
        }
        final boolean closed = spoken.get(innerClose).getType() == FrostlakeLexer.RPAREN;
        final int last = spoken.get(innerClose - 1).getType();
        final List<String> inside = SyntaxErrorListener.linesReportedFor(new String(alone), true);
        final List<String> aliased = closed ? literalAliasLines(spoken, innerOpen, innerClose, inside) : null;
        if (aliased != null) {
            lines.addAll(aliased);
            return lines;
        }
        if (!inside.isEmpty() && innerOpen + 2 < spoken.size()
                && inside.get(0).equals(SyntaxErrorListener.sentence(spoken.get(innerOpen + 2)))) {
            // A query with no select list is refused at its SELECT alone, and its ')' is one more line unless the
            // FROM or the SELECT stands right before it: (SELECT FROM t) is the SELECT and the ')', (SELECT FROM)
            // the SELECT (live-verified).
            if (closed && last != FrostlakeLexer.FROM && last != FrostlakeLexer.SELECT) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(innerClose)));
            }
            return lines;
        }
        for (final String line : inside) {
            if (!line.contains("'<EOF>'")) {
                lines.add(line);
            } else if (!closed) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(innerClose)));
            } else if (last == FrostlakeLexer.WHERE || last == FrostlakeLexer.JOIN || last == FrostlakeLexer.BY) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(innerClose)));
            }
        }
        return lines;
    }

    /**
     * The lines of a literal written as a select item's alias in the query between {@code open} and {@code close},
     * when that is its one fault: the literal, after it the query's ')' when anything else follows it, and before it
     * the AS when the item before it has no alias (all live-verified); null for any other fault.
     *
     * <pre>
     *   SELECT (SELECT 1 AS 1)              'SELECT', '1'
     *   SELECT (SELECT 1 AS 1, 2)           'SELECT', '1', then the query's ')'
     *   SELECT (SELECT 1, 2 AS 2)           'SELECT', 'AS', '2'
     *   SELECT (SELECT 1 AS x, 2 AS 2)      'SELECT', '2'
     *   SELECT (SELECT 1, 2 AS 2 FROM t)    'SELECT', 'AS', '2', then the query's ')'
     * </pre>
     */
    private static List<String> literalAliasLines(final List<Token> spoken, final int open, final int close,
                                                  final List<String> inside) {
        if (inside.size() != 1) {
            return null;
        }
        int literal = -1;
        for (int i = open + 3; i < close && literal < 0; i++) {
            if (SyntaxErrorListener.sentence(spoken.get(i)).equals(inside.get(0))) {
                literal = i;
            }
        }
        if (literal < 0 || spoken.get(literal - 1).getType() != FrostlakeLexer.AS) {
            return null;
        }
        final int type = spoken.get(literal).getType();
        if (type != FrostlakeLexer.INTEGER_LITERAL && type != FrostlakeLexer.FLOAT_LITERAL
                && type != FrostlakeLexer.STRING_LITERAL) {
            return null;
        }
        // The comma before the item, and whether the item before that comma has an alias of its own.
        int comma = -1;
        int depth = 0;
        for (int i = open + 2; i < literal - 1; i++) {
            final int at = spoken.get(i).getType();
            depth += at == FrostlakeLexer.LPAREN ? 1 : at == FrostlakeLexer.RPAREN ? -1 : 0;
            if (depth == 0 && at == FrostlakeLexer.COMMA) {
                comma = i;
            }
        }
        final boolean unaliasedBefore = comma > open + 2 && !aliasedItemEndingAt(spoken, open + 2, comma - 1);
        final List<String> lines = new ArrayList<>();
        if (unaliasedBefore) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(literal - 1)));
        }
        lines.add(inside.get(0));
        if (literal + 1 < close) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(close)));
        }
        return lines;
    }

    /** Whether the select item ending at {@code last}, which opens at or after {@code from}, ends in an alias. */
    private static boolean aliasedItemEndingAt(final List<Token> spoken, final int from, final int last) {
        if (last <= from || !StarModifierResync.isName(spoken.get(last))) {
            return false;
        }
        final Token before = spoken.get(last - 1);
        final int type = before.getType();
        return type == FrostlakeLexer.AS || type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.INTEGER_LITERAL
            || type == FrostlakeLexer.FLOAT_LITERAL || type == FrostlakeLexer.STRING_LITERAL
            || StarModifierResync.isName(before);
    }

    /** Adds the SELECT of each faulty value query nested in the one at {@code open}, outermost first. */
    private static void addNested(final String sql, final List<Token> spoken, final int open, final int close,
                                  final List<String> lines) {
        int[] deeper = faultyQuery(sql, spoken, open + 2, close, -1);
        while (deeper != null) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(deeper[0] + 1)));
            deeper = faultyQuery(sql, spoken, deeper[0] + 2, deeper[1], -1);
        }
    }

    /**
     * The first query used as a value among the tokens from {@code start} up to {@code end} that does not parse:
     * the positions of its '(' and of its ')' (or of the token that ends the statement when it is not closed), where
     * it stands, and the call's '(' for an argument — or null. A bracket at {@code statementStart} opens the
     * statement itself and is no value.
     */
    private static int[] faultyQuery(final String sql, final List<Token> spoken, final int start, final int end,
                                     final int statementStart) {
        // Per open bracket: its position, whether it is plain (0), a call's (1), a query's (2) or a CAST's (3), and
        // the clause last opened at its level.
        final List<int[]> frames = new ArrayList<>();
        frames.add(new int[] {-1, 2, -1});
        for (int i = start; i < end; i++) {
            final int type = spoken.get(i).getType();
            final int[] top = frames.get(frames.size() - 1);
            if (type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.RBRACKET || type == FrostlakeLexer.RBRACE) {
                if (frames.size() > 1) {
                    frames.remove(frames.size() - 1);
                }
                continue;
            }
            if (isClause(type)) {
                top[2] = type;
                continue;
            }
            if (type != FrostlakeLexer.LPAREN && type != FrostlakeLexer.LBRACKET && type != FrostlakeLexer.LBRACE) {
                continue;
            }
            final int before = i == 0 ? Token.EOF : spoken.get(i - 1).getType();
            final boolean query = type == FrostlakeLexer.LPAREN && i + 1 < end
                && spoken.get(i + 1).getType() == FrostlakeLexer.SELECT;
            if (query && isValuePlace(before, governing(frames), i == statementStart)) {
                final int close = closing(spoken, i, end);
                if (close > i + 1 && !parses(sql, spoken, i + 1, close - 1)) {
                    final int[] place = placeOf(frames);
                    return place == null ? null
                        : new int[] {i, close, place[0], place[1], place[2], place[3], place[4]};
                }
            }
            final int kind;
            if (type != FrostlakeLexer.LPAREN) {
                kind = 0;
            } else if (WORDS.contains(before) || before == FrostlakeLexer.QUOTED_IDENTIFIER) {
                kind = 1;
            } else if (before == FrostlakeLexer.CAST || before == FrostlakeLexer.TRY_CAST) {
                kind = 3;
            } else {
                kind = query ? 2 : 0;
            }
            frames.add(new int[] {i, kind, -1});
        }
        return null;
    }

    /**
     * Where a value query opened under the given frames stands — a select list, a call's argument in one, more than
     * one bracket deep in one, or another clause — the position of the call's '(' for an argument, the clause, how
     * many calls the query stands in and the outermost one's '(' (-1 for none); null inside a CAST, a join's ON or a
     * LIMIT, whose readings are their own.
     */
    private static int[] placeOf(final List<int[]> frames) {
        int level = frames.size() - 1;
        while (level > 0 && frames.get(level)[2] == -1) {
            level--;
        }
        final int clause = frames.get(level)[2];
        if (clause == FrostlakeLexer.ON || clause == FrostlakeLexer.LIMIT || clause == FrostlakeLexer.OFFSET
                || clause == FrostlakeLexer.FETCH) {
            return null;
        }
        int plain = 0;
        int call = -1;
        int calls = 0;
        int outermost = -1;
        for (int f = level + 1; f < frames.size(); f++) {
            final int kind = frames.get(f)[1];
            if (kind == 3) {
                return null;
            }
            if (kind == 1) {
                call = frames.get(f)[0];
                outermost = calls++ == 0 ? call : outermost;
            } else {
                plain++;
            }
        }
        if (clause != FrostlakeLexer.SELECT) {
            return new int[] {CLAUSE, -1, clause, calls, outermost};
        }
        if (call >= 0) {
            return new int[] {ARGUMENT, call, clause, calls, outermost};
        }
        return new int[] {plain > 1 ? DEEP : ITEM, -1, clause, calls, outermost};
    }

    /**
     * The lines after the SELECT of a faulty query used as a call's argument, or standing in a clause, whose FROM
     * reads a statement in its parentheses and is refused at its first word (see StatementWordInFromLines); null for
     * any other query, and for the one an IN's list holds unless the statement follows a comma, which reads as a
     * comparison's query does (all live-verified):
     *
     * <pre>
     *   SELECT UPPER((SELECT 1 FROM (DESCRIBE TABLE t)))           'SELECT', 'DESCRIBE', then 'TABLE': the token
     *                                                              after the word, the SET after UPDATE's name, the
     *                                                              token after INSERT INTO and its name; a LIST,
     *                                                              which its FROM reads as a table, the same
     *   SELECT UPPER(UPPER((SELECT 1 FROM (DESCRIBE TABLE t))))    'SELECT', 'DESCRIBE': two calls deep, or a query
     *                                                              whose select list opens with no literal
     *   SELECT ABS((SELECT 1 FROM ((DESCRIBE TABLE t))))           'SELECT', the inner '(', then 'TABLE'
     *   SELECT ABS((SELECT 1 FROM t JOIN (DESCRIBE TABLE t) ON TRUE))   'SELECT', then the 'JOIN'
     *   SELECT ABS((SELECT 1 FROM t, (DESCRIBE TABLE t)))          after a comma, see listedWordLines
     *   … WHERE ABS((SELECT 1 FROM (DESCRIBE TABLE t))) = 1        'SELECT', 'DESCRIBE', then the ')' before the
     *                                                              outermost call's own; a HAVING, an ORDER BY and a
     *                                                              GROUP BY alike
     *   … WHERE (SELECT 1 FROM (DESCRIBE TABLE t)) = 1             'SELECT', 'DESCRIBE'
     * </pre>
     */
    private static List<String> statementWordLines(final String sql, final List<Token> spoken, final int open,
                                                   final int close, final int[] found, final int end) {
        final int place = found[2];
        if (place != ARGUMENT && place != CLAUSE || open < 1) {
            return null;
        }
        final boolean inList = spoken.get(open - 1).getType() == FrostlakeLexer.IN;
        final List<String> inside = queryReadAlone(sql, spoken, open, close);
        int word = -1;
        for (int i = open + 4; !inside.isEmpty() && i + 1 < close && word < 0; i++) {
            final Token at = spoken.get(i);
            final boolean named = StatementWordInFromLines.opensStatement(at)
                && inside.get(0).equals(SyntaxErrorListener.sentence(at))
                || at.getType() == FrostlakeLexer.LIST
                    && inside.get(0).equals(SyntaxErrorListener.sentence(spoken.get(i + 1)));
            int before = i - 1;
            while (before > i - 3 && spoken.get(before).getType() == FrostlakeLexer.LPAREN) {
                before--;
            }
            final int reads = spoken.get(before).getType();
            if (named && before < i - 1 && (reads == FrostlakeLexer.FROM || reads == FrostlakeLexer.JOIN
                    || reads == FrostlakeLexer.COMMA && inFromList(spoken, open, before))) {
                word = i;
            }
        }
        if (word < 0) {
            return null;
        }
        final boolean doubled = spoken.get(word - 2).getType() == FrostlakeLexer.LPAREN;
        final int reads = spoken.get(doubled ? word - 3 : word - 2).getType();
        final boolean literalFirst = isLiteral(spoken.get(open + 2));
        final int calls = found[5];
        final List<String> lines = new ArrayList<>();
        if (reads == FrostlakeLexer.COMMA) {
            return doubled ? null : listedWordLines(spoken, word, place == ARGUMENT && calls == 1 && literalFirst,
                place == CLAUSE && calls == 0, place == ARGUMENT && calls == 1 && !literalFirst,
                place == ARGUMENT && literalFirst ? calls : 0);
        }
        if (inList) {
            return null;
        }
        if (place == ARGUMENT && calls == 1 && literalFirst) {
            if (reads == FrostlakeLexer.JOIN) {
                // A join's bracket is refused at the JOIN itself (live-verified).
                lines.add(SyntaxErrorListener.sentence(spoken.get(doubled ? word - 3 : word - 2)));
                return lines;
            }
            final Token after = StatementWordInFromLines.afterWord(spoken, word);
            lines.add(SyntaxErrorListener.sentence(spoken.get(doubled ? word - 1 : word)));
            if (after != null) {
                lines.add(SyntaxErrorListener.sentence(after));
            }
            return lines;
        }
        if (doubled) {
            return null;
        }
        lines.add(inside.get(0));
        if (place == CLAUSE && calls > 0 && literalFirst) {
            final int outerClose = closing(spoken, found[6], end);
            if (outerClose < end && spoken.get(outerClose - 1).getType() == FrostlakeLexer.RPAREN) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(outerClose - 1)));
            }
        }
        return lines;
    }

    /**
     * The lines after the SELECT of a query whose FROM list reads a statement in its parentheses after a comma, the
     * statement word no word of the query to live (all live-verified):
     *
     * <pre>
     *   SELECT ABS((SELECT 1 FROM t, (DESCRIBE TABLE t)))     'SELECT', then 'TABLE' twice: the token after DESCRIBE,
     *                                                         DESC or EXPLAIN
     *   SELECT ABS((SELECT 1 FROM t, (UPDATE t SET a = 1)))   'SELECT', 'UPDATE', then 'SET', as after FROM
     *   SELECT ABS((SELECT 1 FROM t, (CALL p())))             'SELECT', the name after CALL, then the token after it
     *   SELECT ABS((SELECT a FROM t, (CALL p())))             'SELECT', 'CALL': a select list opening with no literal
     *   … WHERE 1 = (SELECT 1 FROM t, (DESCRIBE TABLE t))     'SELECT', then 'TABLE' once, as EXPLAIN's next word;
     *                                                         a USING there, then the token after the first ')'
     *   … WHERE 1 = (SELECT 1 FROM t, (UPDATE t SET a = 1))   'SELECT', 'UPDATE', then the token after the first ')'
     *   … WHERE 1 = (SELECT 1 FROM t, (CALL p(1)))            'SELECT', 'p', '1', then the token after the first ')'
     *                                                         past it; an IN's query (NOT IN too) alike, and
     *                                                         whatever the compared select list opens with
     *   SELECT ABS(ABS((SELECT 1 FROM t, (CALL p()))))        'SELECT', 'p', 'CALL', then 'p' again: two calls deep
     *                                                         the token after the word, the word, that token again; a
     *                                                         DESCRIBE's 'TABLE' alike
     *   SELECT ABS(ABS(ABS((SELECT 1 FROM t, (CALL p())))))   'SELECT', 'p', 'CALL', then the '(' after the name
     * </pre>
     *
     * Null for any other word or place.
     */
    private static List<String> listedWordLines(final List<Token> spoken, final int word, final boolean argument,
                                                final boolean compared, final boolean plain, final int calls) {
        final int type = spoken.get(word).getType();
        final Token after = StatementWordInFromLines.afterWord(spoken, word);
        final boolean described = type == FrostlakeLexer.DESCRIBE || type == FrostlakeLexer.DESC;
        final List<String> lines = new ArrayList<>();
        if (plain) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(word)));
            return lines;
        }
        if (after != null && (calls == 2 && (described || type == FrostlakeLexer.CALL)
                || calls == THREE_CALLS && type == FrostlakeLexer.CALL && word + 2 < spoken.size())) {
            lines.add(SyntaxErrorListener.sentence(after));
            lines.add(SyntaxErrorListener.sentence(spoken.get(word)));
            lines.add(SyntaxErrorListener.sentence(calls == 2 ? after : spoken.get(word + 2)));
            return lines;
        }
        if (after == null || !argument && !compared) {
            return null;
        }
        if (argument && (described || type == FrostlakeLexer.EXPLAIN)) {
            lines.add(SyntaxErrorListener.sentence(after));
            lines.add(SyntaxErrorListener.sentence(after));
        } else if (argument && type == FrostlakeLexer.UPDATE) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(word)));
            lines.add(SyntaxErrorListener.sentence(after));
        } else if (argument && type == FrostlakeLexer.CALL) {
            lines.add(SyntaxErrorListener.sentence(after));
            if (word + 2 < spoken.size() && spoken.get(word + 2).getType() != Token.EOF) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(word + 2)));
            }
        } else if (argument) {
            return null;
        } else if (described || type == FrostlakeLexer.EXPLAIN) {
            lines.add(SyntaxErrorListener.sentence(after));
            final Token close = after.getType() == FrostlakeLexer.USING
                ? StatementWordInFromLines.afterFirstClose(spoken, word + 1) : null;
            if (close != null) {
                lines.add(SyntaxErrorListener.sentence(close));
            }
        } else if (type == FrostlakeLexer.UPDATE) {
            final Token close = StatementWordInFromLines.afterFirstClose(spoken, word);
            lines.add(SyntaxErrorListener.sentence(spoken.get(word)));
            if (close != null) {
                lines.add(SyntaxErrorListener.sentence(close));
            }
        } else if (type == FrostlakeLexer.CALL && word + 3 < spoken.size()
                && spoken.get(word + 2).getType() == FrostlakeLexer.LPAREN) {
            final Token close = StatementWordInFromLines.afterFirstClose(spoken, word + 3);
            lines.add(SyntaxErrorListener.sentence(after));
            lines.add(SyntaxErrorListener.sentence(spoken.get(word + 3)));
            if (close != null) {
                lines.add(SyntaxErrorListener.sentence(close));
            }
        } else {
            return null;
        }
        return lines;
    }

    /** Whether the comma at {@code comma} stands in the FROM list of the query opened at {@code open}. */
    private static boolean inFromList(final List<Token> spoken, final int open, final int comma) {
        int depth = 0;
        boolean from = false;
        for (int i = open + 1; i < comma; i++) {
            final int type = spoken.get(i).getType();
            depth += type == FrostlakeLexer.LPAREN ? 1 : type == FrostlakeLexer.RPAREN ? -1 : 0;
            if (depth == 0 && type == FrostlakeLexer.FROM) {
                from = true;
            } else if (depth == 0 && (type == FrostlakeLexer.WHERE || type == FrostlakeLexer.GROUP
                    || type == FrostlakeLexer.HAVING || type == FrostlakeLexer.ORDER)) {
                from = false;
            }
        }
        return from && depth == 0;
    }

    /**
     * The '(' of the call a query's select list opens with — after its DISTINCT or ALL — or -1 when it opens with
     * anything else.
     */
    private static int firstItemCall(final List<Token> spoken, final int select, final int close) {
        int at = select + 1;
        if (at < close && (spoken.get(at).getType() == FrostlakeLexer.DISTINCT
                || spoken.get(at).getType() == FrostlakeLexer.ALL)) {
            at++;
        }
        if (at + 1 >= close) {
            return -1;
        }
        final int type = spoken.get(at).getType();
        return (WORDS.contains(type) || type == FrostlakeLexer.QUOTED_IDENTIFIER)
            && spoken.get(at + 1).getType() == FrostlakeLexer.LPAREN ? at + 1 : -1;
    }

    /**
     * The lines after the SELECT of a faulty query whose first item is the call at {@code call}, or null where the
     * query stands in no place read that way: in a select list the call's '(', compared in a WHERE or a HAVING the
     * first token in the call's parentheses (and the comma after a named star there), and as an IN's list in one of
     * them the token past the call.
     */
    private static List<String> afterCallFirst(final List<Token> spoken, final int open, final int close,
                                               final int place, final int clause, final int call) {
        final List<String> lines = new ArrayList<>();
        if (place == ITEM) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(call)));
            return lines;
        }
        final boolean clauseRead = place == CLAUSE && (clause == FrostlakeLexer.WHERE || clause == FrostlakeLexer.HAVING
            || clause == FrostlakeLexer.QUALIFY || clause == FrostlakeLexer.ORDER || clause == FrostlakeLexer.GROUP
            || clause == FrostlakeLexer.SET);
        if (place != ARGUMENT && !clauseRead || open < 1) {
            return null;
        }
        final int before = spoken.get(open - 1).getType();
        if (before == FrostlakeLexer.IN && (clause == FrostlakeLexer.WHERE || clause == FrostlakeLexer.HAVING)) {
            final int callClose = closing(spoken, call, close);
            if (callClose + 1 >= close) {
                return null;
            }
            lines.add(SyntaxErrorListener.sentence(spoken.get(callClose + 1)));
            return lines;
        }
        if (spoken.get(call + 1).getType() == FrostlakeLexer.RPAREN) {
            return null;
        }
        lines.add(SyntaxErrorListener.sentence(spoken.get(call + 1)));
        // A window call compared in a clause is read on to its frame's ROWS or RANGE (live-verified).
        final int callClose = closing(spoken, call, close);
        if (place == CLAUSE && callClose + 1 < close && spoken.get(callClose + 1).getType() == FrostlakeLexer.OVER) {
            for (int i = callClose + 2; i < close; i++) {
                final int type = spoken.get(i).getType();
                if (type == FrostlakeLexer.ROWS || type == FrostlakeLexer.RANGE) {
                    lines.add(SyntaxErrorListener.sentence(spoken.get(i)));
                    break;
                }
            }
        }
        // A query whose FROM writes a table function bare reads on, compared in a clause, to the token after the first
        // one in the first item's call and then the bare call's first token (live-verified).
        final int bare = place == CLAUSE ? bareCall(spoken, open, close) : -1;
        if (bare > 0 && call + 2 < close && bare + 1 < close) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(call + 2)));
            lines.add(SyntaxErrorListener.sentence(spoken.get(bare + 1)));
            return lines;
        }
        if (call + 4 < close && spoken.get(call + 2).getType() == FrostlakeLexer.ARROW
                && spoken.get(call + 3).getType() == FrostlakeLexer.STAR
                && spoken.get(call + 4).getType() == FrostlakeLexer.COMMA) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(call + 4)));
        }
        return lines;
    }

    /**
     * The lines after the SELECT of a faulty query whose fault stands inside a call that does not open its select
     * list, or null for a fault anywhere else: the fault, then in a select list the query's own ')'; elsewhere the
     * FROM past the call, and the query's ')' after a clause the FROM's relation is followed by — or, for a select
     * list's call argument, after the FROM alone.
     */
    private static List<String> laterCallFault(final String sql, final List<Token> spoken, final int open,
                                               final int close, final int place) {
        if (place == DEEP || spoken.get(close).getType() != FrostlakeLexer.RPAREN) {
            return null;
        }
        final List<String> inside = queryReadAlone(sql, spoken, open, close);
        final int[] at = inside.isEmpty() ? null : SyntaxErrorListener.sentenceCoordinates(inside.get(0));
        int fault = -1;
        for (int i = open + 2; at != null && i < close && fault < 0; i++) {
            if (spoken.get(i).getLine() == at[0] && spoken.get(i).getCharPositionInLine() == at[1]) {
                fault = i;
            }
        }
        // The innermost bracket open at the fault must be a call's, its ')' inside the query.
        int callOpen = -1;
        final List<Integer> brackets = new ArrayList<>();
        for (int i = open + 1; i < fault; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                brackets.add(i);
            } else if (type == FrostlakeLexer.RPAREN && !brackets.isEmpty()) {
                brackets.remove(brackets.size() - 1);
            }
        }
        if (!brackets.isEmpty()) {
            final int innermost = brackets.get(brackets.size() - 1);
            final int before = spoken.get(innermost - 1).getType();
            callOpen = WORDS.contains(before) || before == FrostlakeLexer.QUOTED_IDENTIFIER ? innermost : -1;
        }
        final int callClose = callOpen < 0 ? -1 : closing(spoken, callOpen, close);
        if (callClose < 0 || callClose >= close) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(inside.get(0));
        if (place == ITEM) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(close)));
            return lines;
        }
        if (spoken.get(callClose + 1).getType() != FrostlakeLexer.FROM) {
            return lines;
        }
        lines.add(SyntaxErrorListener.sentence(spoken.get(callClose + 1)));
        boolean clauseAfter = false;
        for (int i = callClose + 2; i < close && !clauseAfter; i++) {
            clauseAfter = isClause(spoken.get(i).getType()) && spoken.get(i).getType() != FrostlakeLexer.FROM;
        }
        if (place == ARGUMENT || clauseAfter) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(close)));
        }
        return lines;
    }

    /**
     * The lines of the query an IN's list holds, its SELECT first, when its FROM list reads a statement in its
     * parentheses after a comma, refused at its word: as a compared query's (see listedWordLines), {@code a IN
     * (SELECT 1 FROM t, (DESCRIBE TABLE t))} 'SELECT', then 'TABLE' — or when that query holds such a query nested in
     * it ({@link #nestedListedWordLines}). Each faulty query around the IN's, compared in a clause or standing in a
     * select list, is refused at its SELECT before it (all live-verified):
     *
     * <pre>
     *   … WHERE a = (SELECT 1 FROM t WHERE a IN (SELECT 1 FROM t, (CALL p())))    'SELECT' at both, 'p', then the
     *                                                                            ')' after it and the IN's
     * </pre>
     *
     * Null for any other query.
     *
     * @param sql    the text parsed
     * @param spoken its default-channel tokens, end of input included
     * @param open   the list's '('
     * @return the lines, or null
     */
    static List<String> inListStatementWord(final String sql, final List<Token> spoken, final int open) {
        int start = open;
        while (start > 0 && spoken.get(start - 1).getType() != FrostlakeLexer.SEMI) {
            start--;
        }
        int end = open;
        while (end < spoken.size() - 1 && spoken.get(end).getType() != FrostlakeLexer.SEMI) {
            end++;
        }
        final List<String> lines = new ArrayList<>();
        int around = -1;
        int[] outer = faultyQuery(sql, spoken, start, end, start);
        while (outer != null && outer[0] < open) {
            if (outer[1] < open || outer[2] != CLAUSE && outer[2] != ITEM || outer[5] != 0) {
                break;
            }
            lines.add(SyntaxErrorListener.sentence(spoken.get(outer[0] + 1)));
            around = outer[1];
            outer = faultyQuery(sql, spoken, outer[0] + 2, outer[1], -1);
        }
        if (outer == null || outer[0] != open) {
            lines.clear();
            around = -1;
        }
        lines.add(SyntaxErrorListener.sentence(spoken.get(open + 1)));
        final List<String> worded = wordedChain(sql, spoken, open, around, false);
        if (worded == null) {
            return null;
        }
        lines.addAll(worded);
        final int outermost = around >= 0 ? openingOf(spoken, around) : open;
        return outermost < 0 ? lines : framedWordLines(spoken, outermost, closing(spoken, outermost,
            spoken.size() - 1), lines);
    }

    /** Whether the token at {@code index} stands in a select list: SELECT is the clause last opened at its level. */
    private static boolean inSelectList(final List<Token> spoken, final int index) {
        int depth = 0;
        for (int i = index - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.SEMI) {
                return false;
            }
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                depth = Math.max(0, depth - 1);
            } else if (depth == 0 && isClause(type)) {
                return type == FrostlakeLexer.SELECT;
            }
        }
        return false;
    }

    /** The position of the '(' the ')' at {@code close} closes, or -1. */
    private static int openingOf(final List<Token> spoken, final int close) {
        int depth = 0;
        for (int i = close; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The statement-word lines of the faulty value query between {@code open} and {@code close}, as the query the
     * value query stands in reads them (all live-verified): in an EXISTS's query the last ')' named is the next
     * ')' after it - {@code … WHERE EXISTS (SELECT 1 FROM t WHERE a IN (SELECT 1 FROM t, (CALL p())))} is 'SELECT',
     * 'p', the ')' after the name's '(', then the EXISTS's ')' - and in a derived table each faulty query's SELECT is
     * followed by the statement word alone: {@code SELECT 1 FROM (SELECT 1 FROM t WHERE a IN (SELECT 1 FROM t,
     * (CALL p())))} is 'SELECT', then 'CALL'. A query that is a call's argument in a clause other than a select list,
     * its FROM list reading the statement after a comma, names the ')' after it as an EXISTS's query does. Elsewhere the lines as given.
     */
    private static List<String> framedWordLines(final List<Token> spoken, final int open, final int close,
                                                final List<String> lines) {
        int frame = -1;
        int depth = 0;
        for (int i = open - 1; i >= 0 && frame < 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.SEMI) {
                return lines;
            }
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    frame = i;
                }
                depth--;
            }
        }
        if (frame < 1 || lines.isEmpty()) {
            return lines;
        }
        final int before = spoken.get(frame - 1).getType();
        final boolean called = frame + 1 == open && (WORDS.contains(before)
            || before == FrostlakeLexer.QUOTED_IDENTIFIER) && !inSelectList(spoken, frame);
        if (!called && (frame + 1 >= spoken.size() || spoken.get(frame + 1).getType() != FrostlakeLexer.SELECT)) {
            return lines;
        }
        int word = -1;
        for (int i = open + 3; i < close && word < 0; i++) {
            if (StatementWordInFromLines.opensStatement(spoken.get(i))
                    && spoken.get(i - 1).getType() == FrostlakeLexer.LPAREN
                    && spoken.get(i - 2).getType() == FrostlakeLexer.COMMA) {
                word = i;
            }
        }
        if (before == FrostlakeLexer.EXISTS || called && word >= 0) {
            final String last = lines.get(lines.size() - 1);
            for (int i = open; i + 1 < spoken.size(); i++) {
                if (spoken.get(i).getType() == FrostlakeLexer.RPAREN
                        && last.equals(SyntaxErrorListener.sentence(spoken.get(i)))) {
                    if (spoken.get(i + 1).getType() == FrostlakeLexer.RPAREN) {
                        lines.set(lines.size() - 1, SyntaxErrorListener.sentence(spoken.get(i + 1)));
                    }
                    return lines;
                }
            }
            return lines;
        }
        if (before != FrostlakeLexer.FROM && before != FrostlakeLexer.JOIN && before != FrostlakeLexer.COMMA) {
            return lines;
        }
        if (word < 0) {
            return lines;
        }
        final List<String> framed = new ArrayList<>();
        for (int i = 0; i < lines.size() && lines.get(i).endsWith("unexpected 'SELECT'."); i++) {
            framed.add(lines.get(i));
        }
        framed.add(SyntaxErrorListener.sentence(spoken.get(word)));
        return framed;
    }

    /**
     * The lines after the SELECT of a faulty query whose fault lies in queries nested in it, each compared or listed
     * by an IN in a clause of the query around it, when the innermost one's FROM list reads a statement in its
     * parentheses after a comma: each nested query's SELECT, outermost first, then the innermost one's lines as a
     * compared query's (see listedWordLines), whatever the outer queries are followed by — after a DESCRIBE, DESC
     * or EXPLAIN its next word, then the ')' of the query around the innermost one (all live-verified):
     *
     * <pre>
     *   … WHERE a IN (SELECT 1 FROM t WHERE a IN (SELECT 1 FROM t, (CALL p())))     'SELECT' at both, 'p', then the
     *                                                                              ')' after it and the inner query's
     *   … WHERE a = (SELECT 1 FROM t WHERE a &gt; (SELECT 1 FROM t, (DESCRIBE TABLE t)))   'SELECT' at both, 'TABLE',
     *                                                                              then the outer query's ')'
     *   SELECT (SELECT 1 FROM t WHERE a IN (SELECT 1 FROM t, (CALL p())))          the same in a select list
     * </pre>
     *
     * Null for any other query, one nested as a call's argument among them.
     *
     * @param sql    the text parsed
     * @param spoken its default-channel tokens, end of input included
     * @param open   the outer query's '('
     * @return the lines, or null
     */
    static List<String> nestedListedWordLines(final String sql, final List<Token> spoken, final int open) {
        return wordedChain(sql, spoken, open, -1, true);
    }

    /**
     * The lines after the SELECT of the query at {@code open}: the SELECT of each faulty query nested in it, then
     * the innermost one's statement-word lines; null when a nested query stands elsewhere than compared or listed in
     * a clause, when the innermost reads no statement after a comma in its FROM list, or — with {@code nestedOnly} —
     * when no query is nested.
     *
     * @param around the ')' of the faulty query around the one at {@code open}, or -1
     */
    private static List<String> wordedChain(final String sql, final List<Token> spoken, final int open,
                                            final int around, final boolean nestedOnly) {
        final int close = closing(spoken, open, spoken.size() - 1);
        if (close >= spoken.size() - 1 || spoken.get(close).getType() != FrostlakeLexer.RPAREN) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        int innerOpen = open;
        int innerClose = close;
        int enclosing = around;
        int[] deeper = faultyQuery(sql, spoken, open + 2, close, -1);
        while (deeper != null) {
            if (deeper[2] != CLAUSE || deeper[5] != 0 || spoken.get(deeper[1]).getType() != FrostlakeLexer.RPAREN) {
                return null;
            }
            enclosing = innerClose;
            innerOpen = deeper[0];
            innerClose = deeper[1];
            lines.add(SyntaxErrorListener.sentence(spoken.get(innerOpen + 1)));
            deeper = faultyQuery(sql, spoken, innerOpen + 2, innerClose, -1);
        }
        if (nestedOnly && lines.isEmpty()) {
            return null;
        }
        final int word = listedWord(sql, spoken, innerOpen, innerClose);
        final List<String> worded = word < 0 ? null : listedWordLines(spoken, word, false, true, false, 0);
        if (worded == null) {
            return null;
        }
        final int type = spoken.get(word).getType();
        if (enclosing >= 0 && (type == FrostlakeLexer.DESCRIBE || type == FrostlakeLexer.DESC
                || type == FrostlakeLexer.EXPLAIN)) {
            // Inside another faulty query, the word's next word is followed by that query's ')' (live-verified).
            lines.add(worded.get(0));
            lines.add(SyntaxErrorListener.sentence(spoken.get(enclosing)));
            return lines;
        }
        lines.addAll(worded);
        return lines;
    }

    /**
     * The position of the statement word the FROM list of the query between {@code open} and {@code close} reads in
     * its parentheses after a comma, refused there, or -1.
     */
    private static int listedWord(final String sql, final List<Token> spoken, final int open, final int close) {
        if (open + 2 >= close || spoken.get(open + 1).getType() != FrostlakeLexer.SELECT) {
            return -1;
        }
        final List<String> inside = queryReadAlone(sql, spoken, open, close);
        for (int i = open + 4; !inside.isEmpty() && i + 1 < close; i++) {
            final Token at = spoken.get(i);
            if (StatementWordInFromLines.opensStatement(at) && inside.get(0).equals(SyntaxErrorListener.sentence(at))
                    && spoken.get(i - 1).getType() == FrostlakeLexer.LPAREN
                    && spoken.get(i - 2).getType() == FrostlakeLexer.COMMA && inFromList(spoken, open, i - 2)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The lines after the SELECT of the query an IN's list holds, when its fault stands inside a call of a later
     * select item: the fault, the FROM past the call, and the query's ')' after a clause the FROM's relation is
     * followed by, as for a query compared in a WHERE (live-verified); null for any other fault, one past the select
     * list among them.
     *
     * @param sql    the text parsed
     * @param spoken its default-channel tokens, end of input included
     * @param open   the list's '('
     * @return the lines, or null
     */
    static List<String> inListCallFault(final String sql, final List<Token> spoken, final int open) {
        final int close = closing(spoken, open, spoken.size() - 1);
        if (close >= spoken.size() - 1 || open + 2 >= close || spoken.get(open + 1).getType() != FrostlakeLexer.SELECT
                || firstItemCall(spoken, open + 1, close) >= 0
                || faultyQuery(sql, spoken, open + 2, close, -1) != null) {
            return null;
        }
        final List<String> inside = queryReadAlone(sql, spoken, open, close);
        final int[] at = inside.isEmpty() ? null : SyntaxErrorListener.sentenceCoordinates(inside.get(0));
        int depth = 0;
        for (int i = open + 2; at != null && i < close; i++) {
            final Token token = spoken.get(i);
            if (token.getLine() == at[0] && token.getCharPositionInLine() == at[1]) {
                break;
            }
            depth += token.getType() == FrostlakeLexer.LPAREN ? 1 : token.getType() == FrostlakeLexer.RPAREN ? -1 : 0;
            if (depth == 0 && token.getType() == FrostlakeLexer.FROM) {
                return null;
            }
        }
        return laterCallFault(sql, spoken, open, close, CLAUSE);
    }

    /**
     * What follows the first token in the parentheses of a first-item call, in a query that is a call's argument
     * (all live-verified): a word after that token is named and ends the report, {@code SUM(n IGNORE NULLS)} the
     * IGNORE; past a FROM after the call, the query's ')'; otherwise the enclosing call's ')' when more of that call
     * follows the query.
     *
     * @param call  the first-item call's '('
     * @param outer the enclosing call's '('
     */
    private static List<String> argumentCallTail(final List<Token> spoken, final int close, final int call,
                                                 final int outer, final int end) {
        final List<String> tail = new ArrayList<>();
        final int callClose = closing(spoken, call, close);
        // A window call is read on to the token after the first one in its parentheses, whatever it is
        // (live-verified).
        final boolean window = callClose + 1 < close && spoken.get(callClose + 1).getType() == FrostlakeLexer.OVER;
        if (call + 2 < close && (window || StarModifierResync.isName(spoken.get(call + 2)))) {
            tail.add(SyntaxErrorListener.sentence(spoken.get(call + 2)));
            return tail;
        }
        if (callClose + 1 < close && spoken.get(callClose + 1).getType() == FrostlakeLexer.FROM) {
            tail.add(SyntaxErrorListener.sentence(spoken.get(close)));
            return tail;
        }
        final int outerClose = closing(spoken, outer, end);
        if (outerClose > close + 1 && outerClose < end) {
            tail.add(SyntaxErrorListener.sentence(spoken.get(outerClose)));
        }
        return tail;
    }

    /**
     * The lines after the SELECT of a faulty query whose select list opens with a name given an alias, or with a
     * literal and then a name given one, each item followed by a comma; null for any other (all live-verified):
     *
     * <pre>
     *   SELECT (SELECT n AS m, ABS(1 2) FROM t)           'SELECT', 'AS': the alias, whatever fault follows
     *   SELECT (SELECT n m, ABS(1 2) FROM t)              'SELECT', 'm'
     *   SELECT (SELECT 1, n AS m, 1 2 FROM t)             'SELECT', 'AS', '2', then the query's ')'
     *   SELECT UPPER((SELECT n AS m, ABS(1 2) FROM t))    'SELECT', '2', then the query's ')': a call's argument
     *                                                     names no alias, and no FROM
     * </pre>
     *
     * A list that opens with a name given no alias, {@code (SELECT n, m AS k, ABS(1 2) FROM t)}, reads as one with
     * no alias does, and one that opens with a literal given an alias as {@link #literalAliasLines} reads it.
     */
    private static List<String> leadingAliasLines(final String sql, final List<Token> spoken, final int open,
                                                  final int close, final int place) {
        final int first = open + 2;
        int alias = -1;
        boolean literalFirst = false;
        if (first < close && StarModifierResync.isName(spoken.get(first))) {
            alias = aliasAfter(spoken, first, close);
        } else if (first + 2 < close && isLiteral(spoken.get(first))
                && spoken.get(first + 1).getType() == FrostlakeLexer.COMMA
                && StarModifierResync.isName(spoken.get(first + 2))) {
            alias = aliasAfter(spoken, first + 2, close);
            literalFirst = true;
        }
        if (alias < 0 || place == ARGUMENT && literalFirst) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        if (place == ITEM) {
            lines.add(SyntaxErrorListener.sentence(spoken.get(alias)));
            if (!literalFirst) {
                return lines;
            }
        }
        final List<String> inside = queryReadAlone(sql, spoken, open, close);
        if (inside.isEmpty() || spoken.get(close).getType() != FrostlakeLexer.RPAREN) {
            return null;
        }
        lines.add(inside.get(0));
        lines.add(SyntaxErrorListener.sentence(spoken.get(close)));
        return lines;
    }

    /**
     * The alias of the item that opens with the name at {@code name}, when the item is that name and its alias alone
     * and a comma follows: its AS, or the name written without one; -1 otherwise.
     */
    private static int aliasAfter(final List<Token> spoken, final int name, final int close) {
        if (name + 3 < close && spoken.get(name + 1).getType() == FrostlakeLexer.AS
                && StarModifierResync.isName(spoken.get(name + 2))
                && spoken.get(name + 3).getType() == FrostlakeLexer.COMMA) {
            return name + 1;
        }
        return name + 2 < close && StarModifierResync.isName(spoken.get(name + 1))
            && spoken.get(name + 2).getType() == FrostlakeLexer.COMMA ? name + 1 : -1;
    }

    /**
     * The '(' of the table function a query's FROM writes bare, {@code FROM FLATTEN(…)}, or -1.
     *
     * @param open  the query's '('
     * @param close the query's ')'
     */
    static int bareCall(final List<Token> spoken, final int open, final int close) {
        int depth = 0;
        for (int i = open + 1; i + 2 < close; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                depth--;
            } else if (depth == 0 && type == FrostlakeLexer.FROM) {
                return StarModifierResync.isName(spoken.get(i + 1))
                    && spoken.get(i + 2).getType() == FrostlakeLexer.LPAREN ? i + 2 : -1;
            }
        }
        return -1;
    }

    /** Whether the call between {@code open} and {@code close} has two arguments or more, the later ones texts. */
    private static boolean textsAfterFirst(final List<Token> spoken, final int open, final int close) {
        int depth = 0;
        int arguments = 1;
        for (int i = open + 1; i < close; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                depth--;
            } else if (type == FrostlakeLexer.COMMA && depth == 0) {
                arguments++;
                if (spoken.get(i + 1).getType() != FrostlakeLexer.STRING_LITERAL
                        || spoken.get(i + 2).getType() != FrostlakeLexer.COMMA && i + 2 != close) {
                    return false;
                }
            }
        }
        return arguments >= 2;
    }

    /** Whether a token is an arithmetic operator between two values. */
    private static boolean isArithmetic(final int type) {
        return type == FrostlakeLexer.PLUS || type == FrostlakeLexer.MINUS || type == FrostlakeLexer.STAR
            || type == FrostlakeLexer.SLASH;
    }

    /**
     * The lines after the SELECT of a faulty query standing in a clause other than a select list when the query
     * holds, before its own ')', a token the reading resumes at: an operator, a comma, or a clause or set operator
     * that may follow the clause the query stands in. The tokens from the SELECT up to that token are skipped as one
     * value, a WHERE or ON among them skipped too, and the statement read on from that token - so the query's fault
     * after the token is reported, and a faulty query nested after it is refused at its SELECT in turn; null when
     * no such token comes first, when it is followed by the query's ')', or for a bracket, IS, OFFSET, SAMPLE,
     * START, CONNECT or OVER (all live-verified):
     *
     * <pre>
     *   … WHERE a = (SELECT 1 FROM t WHERE a = 1 2)           'SELECT', '2'
     *   … WHERE a = (SELECT 1 FROM t GROUP BY a 2)            'SELECT', '2': at the GROUP, which follows a WHERE
     *   … WHERE a = (SELECT 1 FROM t WHERE a = 1 AND)         'SELECT', then the query's ')'
     *   … WHERE a = (SELECT 1 FROM t WHERE a 2)               'SELECT' alone: no such token before the ')'
     *   … WHERE a = (SELECT 1 FROM t WHERE a = (SELECT 1 x y))   'SELECT', 'SELECT', then the outer query's ')'
     * </pre>
     */
    private static List<String> resumedLines(final String sql, final List<Token> spoken, final int open,
                                             final int close, final boolean direct, final boolean clause) {
        if (close >= spoken.size() || spoken.get(close).getType() != FrostlakeLexer.RPAREN) {
            return null;
        }
        final int resume = resumeAt(spoken, open, close, direct, clause);
        if (resume < 0) {
            return null;
        }
        final int select = spoken.get(open + 1).getStartIndex();
        final int[] at = SyntaxErrorListener.sentenceCoordinates(SyntaxErrorListener.sentence(spoken.get(open)));
        if (resume + 1 == close) {
            // The token is dropped, the ')' closes the query, and the token after it is named only when it does
            // not follow a value.
            return readPastClose(sql, spoken, open, close, clause, at, new ArrayList<String>());
        }
        final char[] resumed = sql.toCharArray();
        blank(resumed, select, spoken.get(resume - 1).getStopIndex());
        resumed[select] = '1';
        final boolean tupled = spoken.get(resume).getType() == FrostlakeLexer.COMMA;
        if (!tupled) {
            // The bracket is closed before the token the reading resumes at, which reads on from there.
            resumed[select + 1] = ')';
        }
        final List<String> lines = linesFrom(new String(resumed), at);
        if (lines == null || lines.isEmpty()) {
            return lines;
        }
        if (spoken.get(close - 1).getType() != FrostlakeLexer.RPAREN
                && lines.get(0).equals(SyntaxErrorListener.sentence(spoken.get(close - 1)))) {
            // A token right before the query's ')' is dropped, the ')' closes the query, and the statement reads on
            // past it.
            final List<String> read = new ArrayList<>();
            read.add(lines.get(0));
            return readPastClose(sql, spoken, open, close, clause, at, read);
        }
        if (tupled) {
            // A WHERE after the list's next item is named, and the reading resumes at the next operator.
            for (int w = resume + 1; w < close; w++) {
                if (spoken.get(w).getType() == FrostlakeLexer.WHERE
                        && lines.get(0).equals(SyntaxErrorListener.sentence(spoken.get(w)))) {
                    int again = -1;
                    for (int i = w + 1; i < close && again < 0; i++) {
                        final int type = spoken.get(i).getType();
                        if (type == FrostlakeLexer.LPAREN || type == FrostlakeLexer.RPAREN) {
                            return lines;
                        }
                        again = resumesAt(type) && type != FrostlakeLexer.COMMA ? i : -1;
                    }
                    if (again < 0 || again + 1 == close) {
                        return lines;
                    }
                    blank(resumed, spoken.get(w).getStartIndex(), spoken.get(again - 1).getStopIndex());
                    final List<String> later = linesFrom(new String(resumed), at);
                    if (later == null) {
                        return null;
                    }
                    final List<String> read = new ArrayList<>();
                    read.add(lines.get(0));
                    read.addAll(later);
                    return read;
                }
            }
        }
        return lines;
    }

    /**
     * The given lines followed by those of the statement read with the query between {@code open} and its ')' at
     * {@code close} taken as a value; null when a line stands before the given coordinates. The ')' closes a reading
     * that has dropped a token: in a clause, a query standing in a bracket of its own or a call's has its ')' close
     * that bracket - the bracket's own ')' then one too many - and otherwise the token after the ')' is left out when
     * it is the first line.
     */
    private static List<String> readPastClose(final String sql, final List<Token> spoken, final int open,
                                              final int close, final boolean clause, final int[] at,
                                              final List<String> read) {
        final int select = spoken.get(open + 1).getStartIndex();
        final char[] valued = sql.toCharArray();
        final boolean shifted = clause && inBracket(spoken, open);
        blank(valued, shifted ? spoken.get(open).getStartIndex() : select, spoken.get(close - 1).getStopIndex());
        valued[select] = '1';
        if (shifted && spoken.get(open - 1).getType() == FrostlakeLexer.IN) {
            // The IN's list is gone with its bracket: an equals sign takes its place.
            valued[spoken.get(open - 1).getStartIndex()] = '=';
            valued[spoken.get(open - 1).getStartIndex() + 1] = ' ';
        }
        final List<String> after = linesFrom(new String(valued), at);
        if (after == null) {
            return null;
        }
        final String next = close + 1 < spoken.size() ? SyntaxErrorListener.sentence(spoken.get(close + 1)) : null;
        for (int i = 0; i < after.size(); i++) {
            if (shifted || i > 0 || !after.get(i).equals(next)) {
                read.add(after.get(i));
            }
        }
        return read;
    }

    /**
     * Whether the '(' at {@code open} stands in a bracket opened after the clause it stands in, or in the query of an
     * EXISTS.
     */
    private static boolean inBracket(final List<Token> spoken, final int open) {
        final int before = spoken.get(open - 1).getType();
        if (before == FrostlakeLexer.ANY || before == FrostlakeLexer.ALL || before == FrostlakeLexer.SOME) {
            return false;
        }
        int depth = 0;
        boolean clause = false;
        for (int i = open - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.SEMI) {
                return false;
            }
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    return !clause || i > 0 && spoken.get(i - 1).getType() == FrostlakeLexer.EXISTS;
                }
                depth--;
            } else if (depth == 0 && isClause(type)) {
                clause = true;
            }
        }
        return false;
    }

    /** The lines a fresh reading of {@code text} reports, or null when one stands before the given coordinates. */
    private static List<String> linesFrom(final String text, final int[] at) {
        final List<String> lines = new ArrayList<>();
        for (final String line : SyntaxErrorListener.linesReportedAfresh(text)) {
            final int[] where = SyntaxErrorListener.sentenceCoordinates(line);
            if (where == null || where[0] < at[0] || where[0] == at[0] && where[1] < at[1]) {
                return null;
            }
            lines.add(line);
        }
        return lines;
    }

    /**
     * The lines after the SELECT of a faulty query an IN's list holds when its reading resumes at a token before its
     * own ')' (see {@link #resumedLines}), or null.
     *
     * @param sql    the text parsed
     * @param spoken its default-channel tokens, end of input included
     * @param open   the position of the list's '('
     * @return the lines, or null
     */
    static List<String> inListResumed(final String sql, final List<Token> spoken, final int open) {
        int end = open;
        while (end < spoken.size() - 1 && spoken.get(end).getType() != FrostlakeLexer.SEMI) {
            end++;
        }
        final int close = closing(spoken, open, end);
        // The clause the IN stands in directly, no bracket between.
        int depth = 0;
        int clause = -1;
        for (int i = open - 1; i >= 0 && clause < 0 && depth >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.SEMI) {
                break;
            }
            depth += type == FrostlakeLexer.RPAREN ? 1 : type == FrostlakeLexer.LPAREN ? -1 : 0;
            if (depth == 0 && isClause(type)) {
                clause = type;
            }
        }
        final boolean direct = clause == FrostlakeLexer.WHERE || clause == FrostlakeLexer.HAVING
            || clause == FrostlakeLexer.QUALIFY;
        return resumedLines(sql, spoken, open, close, direct, true);
    }

    /**
     * The lines of a faulty query used as a value in a clause other than a select list that holds the token at
     * {@code index}, when its reading resumes at a token before its own ')' (see {@link #resumedLines}): its SELECT,
     * then what that reading reports; null otherwise.
     *
     * @param sql    the text parsed
     * @param spoken its default-channel tokens, end of input included
     * @param index  the position of the token among them
     * @return the lines, or null
     */
    static List<String> enclosingResumed(final String sql, final List<Token> spoken, final int index) {
        int start = index;
        while (start > 0 && spoken.get(start - 1).getType() != FrostlakeLexer.SEMI) {
            start--;
        }
        int end = index;
        while (end < spoken.size() - 1 && spoken.get(end).getType() != FrostlakeLexer.SEMI) {
            end++;
        }
        final int[] found = faultyQuery(sql, spoken, start, end, start);
        if (found == null || found[2] != CLAUSE || found[0] >= index || found[1] <= index) {
            return null;
        }
        final int clause = found[4];
        final boolean direct = found[5] == 0 && (clause == FrostlakeLexer.WHERE || clause == FrostlakeLexer.HAVING
            || clause == FrostlakeLexer.QUALIFY);
        final List<String> resumed = resumedLines(sql, spoken, found[0], found[1], direct, true);
        if (resumed == null) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(found[0] + 1)));
        lines.addAll(resumed);
        return lines;
    }

    /**
     * The position of the token a faulty query's reading resumes at, between its '(' at {@code open} and its ')' at
     * {@code close}, or -1 for none or a token whose reading has no rule here.
     */
    private static int resumeAt(final List<Token> spoken, final int open, final int close, final boolean direct,
                                final boolean clause) {
        for (int i = open + 2; i < close; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.WHERE || type == FrostlakeLexer.ON) {
                continue;
            }
            if (type == FrostlakeLexer.LPAREN || type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.IS
                    || type == FrostlakeLexer.OFFSET || type == FrostlakeLexer.SAMPLE
                    || type == FrostlakeLexer.START || type == FrostlakeLexer.CONNECT
                    || type == FrostlakeLexer.OVER || type == FrostlakeLexer.COMMA && !clause) {
                return -1;
            }
            final int prior = spoken.get(i - 1).getType();
            if (type == FrostlakeLexer.STAR && (prior == FrostlakeLexer.SELECT || prior == FrostlakeLexer.COMMA
                    || prior == FrostlakeLexer.DOT)) {
                // A star that stands for the columns is no operator.
                return -1;
            }
            if (resumesAt(type) || direct && followsClause(type)) {
                return i;
            }
        }
        return -1;
    }

    /** Whether the reading of a faulty query's tokens resumes at a token of this kind wherever the query stands. */
    private static boolean resumesAt(final int type) {
        return type == FrostlakeLexer.EQ || type == FrostlakeLexer.EQ_EQ || type == FrostlakeLexer.NEQ
            || type == FrostlakeLexer.LT || type == FrostlakeLexer.LTE || type == FrostlakeLexer.GT
            || type == FrostlakeLexer.GTE || type == FrostlakeLexer.PLUS || type == FrostlakeLexer.MINUS
            || type == FrostlakeLexer.STAR || type == FrostlakeLexer.SLASH || type == FrostlakeLexer.PERCENT
            || type == FrostlakeLexer.PIPE_PIPE || type == FrostlakeLexer.CARET || type == FrostlakeLexer.AND
            || type == FrostlakeLexer.OR || type == FrostlakeLexer.LIKE || type == FrostlakeLexer.ILIKE
            || type == FrostlakeLexer.RLIKE || type == FrostlakeLexer.REGEXP || type == FrostlakeLexer.IN
            || type == FrostlakeLexer.BETWEEN || type == FrostlakeLexer.DOUBLE_COLON
            || type == FrostlakeLexer.LBRACKET || type == FrostlakeLexer.COLON || type == FrostlakeLexer.COLLATE
            || type == FrostlakeLexer.COMMA;
    }

    /** Whether a token opens what may follow a WHERE, HAVING or QUALIFY: a later clause or a set operator. */
    private static boolean followsClause(final int type) {
        return type == FrostlakeLexer.GROUP || type == FrostlakeLexer.HAVING || type == FrostlakeLexer.QUALIFY
            || type == FrostlakeLexer.ORDER || type == FrostlakeLexer.LIMIT || type == FrostlakeLexer.FETCH
            || type == FrostlakeLexer.UNION || type == FrostlakeLexer.EXCEPT || type == FrostlakeLexer.INTERSECT
            || type == FrostlakeLexer.MINUS_KW || type == FrostlakeLexer.SEMI;
    }

    /** Whether a token is a number or a text written as a literal. */
    private static boolean isLiteral(final Token token) {
        final int type = token.getType();
        return type == FrostlakeLexer.INTEGER_LITERAL || type == FrostlakeLexer.FLOAT_LITERAL
            || type == FrostlakeLexer.STRING_LITERAL;
    }

    /** The lines of the query between {@code open} and {@code close} parsed on its own, every position kept. */
    private static List<String> queryReadAlone(final String sql, final List<Token> spoken, final int open,
                                               final int close) {
        final char[] alone = new char[sql.length()];
        Arrays.fill(alone, ' ');
        final int from = spoken.get(open + 1).getStartIndex();
        final int to = spoken.get(close - 1).getStopIndex();
        for (int c = 0; c < alone.length; c++) {
            final char written = sql.charAt(c);
            if (c >= from && c <= to || written == '\n' || written == '\r') {
                alone[c] = written;
            }
        }
        return SyntaxErrorListener.linesReportedFor(new String(alone), true);
    }

    /** The clause last opened at the innermost level that has opened one, or -1. */
    private static int governing(final List<int[]> frames) {
        for (int f = frames.size() - 1; f >= 0; f--) {
            if (frames.get(f)[2] != -1) {
                return frames.get(f)[2];
            }
        }
        return -1;
    }

    /**
     * Whether a '(' that opens a query stands where a value is read: not in a FROM list (a derived table, however
     * many brackets it stands in), not after AS (a common table expression's body), EXISTS, a set operator or a name
     * (a call's own bracket), and not at the statement's start.
     */
    private static boolean isValuePlace(final int before, final int clause, final boolean first) {
        if (first || clause == FrostlakeLexer.FROM || clause == FrostlakeLexer.JOIN
                || WORDS.contains(before) || before == FrostlakeLexer.QUOTED_IDENTIFIER
                || before == FrostlakeLexer.CAST || before == FrostlakeLexer.TRY_CAST
                || before == FrostlakeLexer.FROM || before == FrostlakeLexer.JOIN || before == FrostlakeLexer.AS
                || before == FrostlakeLexer.EXISTS || before == FrostlakeLexer.UNION
                || before == FrostlakeLexer.INTERSECT || before == FrostlakeLexer.EXCEPT
                || before == FrostlakeLexer.MINUS_KW || before == FrostlakeLexer.ALL && clause != FrostlakeLexer.WHERE
                    && clause != FrostlakeLexer.HAVING && clause != FrostlakeLexer.QUALIFY
                    && clause != FrostlakeLexer.SELECT
                || before == FrostlakeLexer.LATERAL || before == FrostlakeLexer.SEMI) {
            return false;
        }
        return true;
    }

    /** Whether the tokens from {@code first} to {@code last} parse as a statement of their own. */
    private static boolean parses(final String sql, final List<Token> spoken, final int first, final int last) {
        return SyntaxErrorListener.cleanParse(
            sql.substring(spoken.get(first).getStartIndex(), spoken.get(last).getStopIndex() + 1)) != null;
    }

    /** The position of the ')' closing the '(' at {@code open}, or {@code end} when the statement ends first. */
    private static int closing(final List<Token> spoken, final int open, final int end) {
        int depth = 0;
        for (int i = open; i < end; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN && --depth == 0) {
                return i;
            }
        }
        return end;
    }

    private static void blank(final char[] text, final int from, final int to) {
        for (int c = from; c <= to && c < text.length; c++) {
            if (text[c] != '\n' && text[c] != '\r') {
                text[c] = ' ';
            }
        }
    }

    /** Whether a token opens a clause a value query may stand in. */
    private static boolean isClause(final int type) {
        return type == FrostlakeLexer.SELECT || type == FrostlakeLexer.FROM || type == FrostlakeLexer.JOIN
            || type == FrostlakeLexer.ON || type == FrostlakeLexer.WHERE || type == FrostlakeLexer.GROUP
            || type == FrostlakeLexer.HAVING || type == FrostlakeLexer.QUALIFY || type == FrostlakeLexer.ORDER
            || type == FrostlakeLexer.LIMIT || type == FrostlakeLexer.OFFSET || type == FrostlakeLexer.FETCH
            || type == FrostlakeLexer.SET || type == FrostlakeLexer.VALUES;
    }
}
