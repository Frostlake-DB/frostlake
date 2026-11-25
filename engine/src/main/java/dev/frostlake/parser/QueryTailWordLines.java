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

import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.misc.IntervalSet;

import java.util.ArrayList;
import java.util.List;

/**
 * The lines of a START or a FOR written straight after a query, with no semicolon before it, or as a select item's
 * alias after AS. Live reads the word as the clause it opens in a query, START WITH a hierarchy's first rows and FOR
 * UPDATE, never as a statement of its own (all live-verified):
 *
 * <pre>
 *   SELECT 1 START TRANSACTION      'TRANSACTION', where WITH belongs, then the end of input where CONNECT BY does
 *   SELECT 1 START x y              'x', then 'y'
 *   SELECT 1 START = 1              '=' alone: no condition begins with it
 *   SELECT 1 FOR i IN 1 TO 2 DO …   'i' alone, where UPDATE belongs
 *   SELECT 1 FOR                    the end of input
 *   SELECT 1 AS start x             'start', then 'x' and the end of input, as after START
 * </pre>
 */
final class QueryTailWordLines {

    /** The tokens that can be a name. */
    private static final IntervalSet NAMES =
        FrostlakeParser._ATN.nextTokens(FrostlakeParser._ATN.ruleToStartState[FrostlakeParser.RULE_identifier]);

    private QueryTailWordLines() {
    }

    /**
     * The lines of a START or a FOR that opens a statement straight after a complete query, when the first fault the
     * parse met lies in that statement; null otherwise.
     *
     * @param parser the parser that met the fault
     * @param reported the token the fault is reported at
     * @return the lines' tokens, or null
     */
    static List<Token> runOn(final Parser parser, final Token reported) {
        ParserRuleContext statement = parser.getContext();
        while (statement != null && statement.getParent() != null && statement.getParent().getParent() != null) {
            statement = statement.getParent();
        }
        final Token opener = statement == null ? null : statement.getStart();
        if (opener == null || opener.getTokenIndex() > reported.getTokenIndex() || !isClauseWord(opener)) {
            return null;
        }
        final FrostlakeParser.StatementContext before = UnseparatedStatementOpener.runOnStatement(parser, reported);
        return before == null || before.queryStatement() == null ? null : after(parser.getInputStream(), opener);
    }

    /**
     * The lines of a START or a FOR refused as a select item's alias after AS: the word, then what the clause it
     * opens refuses after it; null for any other word.
     *
     * @param parser the parser that met the fault
     * @param reported the token the fault is reported at
     * @return the lines' tokens, or null
     */
    static List<Token> asAlias(final Parser parser, final Token reported) {
        if (!isClauseWord(reported)) {
            return null;
        }
        final TokenStream stream = filled(parser.getInputStream());
        final Token before = previous(stream, reported);
        ParserRuleContext statement = parser.getContext();
        while (statement != null && statement.getParent() != null && statement.getParent().getParent() != null) {
            statement = statement.getParent();
        }
        if (before == null || before.getType() != FrostlakeLexer.AS || statement == null
                || statement.getStart() == null || statement.getStart().getType() != FrostlakeLexer.SELECT) {
            return null;
        }
        final List<Token> clause = after(stream, reported);
        if (clause == null) {
            return null;
        }
        final List<Token> lines = new ArrayList<>();
        lines.add(reported);
        lines.addAll(clause);
        return lines;
    }

    /**
     * The lines of a missing separator before a START or a FOR that follows a complete query; null for any other word.
     *
     * @param stream the statement's tokens
     * @param word the word that opens the next statement
     * @return the lines' tokens, or null
     */
    static List<Token> after(final TokenStream stream, final Token word) {
        if (!isClauseWord(word)) {
            return null;
        }
        final TokenStream tokens = filled(stream);
        final Token first = next(tokens, word);
        final boolean start = word.getType() == FrostlakeLexer.START;
        if (first.getType() == (start ? FrostlakeLexer.WITH : FrostlakeLexer.UPDATE)) {
            return null;
        }
        final List<Token> lines = new ArrayList<>();
        lines.add(first);
        if (start && opensCondition(first)) {
            final Token after = next(tokens, first);
            if (after.getType() != FrostlakeLexer.CONNECT) {
                lines.add(after);
            }
        }
        return lines;
    }

    private static boolean isClauseWord(final Token word) {
        return word.getType() == FrostlakeLexer.START || word.getType() == FrostlakeLexer.FOR;
    }

    /** Whether a token is a whole START WITH condition once WITH is taken as missing: a name or a literal. */
    private static boolean opensCondition(final Token token) {
        final int type = token.getType();
        return NAMES.contains(type) || type == FrostlakeLexer.INTEGER_LITERAL || type == FrostlakeLexer.STRING_LITERAL
            || type == FrostlakeLexer.FLOAT_LITERAL;
    }

    private static TokenStream filled(final TokenStream stream) {
        if (stream instanceof BufferedTokenStream) {
            ((BufferedTokenStream) stream).fill();
        }
        return stream;
    }

    private static Token next(final TokenStream stream, final Token token) {
        int i = token.getTokenIndex() + 1;
        while (i < stream.size() - 1 && stream.get(i).getChannel() != Token.DEFAULT_CHANNEL) {
            i++;
        }
        return stream.get(Math.min(i, stream.size() - 1));
    }

    private static Token previous(final TokenStream stream, final Token token) {
        for (int i = token.getTokenIndex() - 1; i >= 0; i--) {
            if (stream.get(i).getChannel() == Token.DEFAULT_CHANNEL) {
                return stream.get(i);
            }
        }
        return null;
    }
}
