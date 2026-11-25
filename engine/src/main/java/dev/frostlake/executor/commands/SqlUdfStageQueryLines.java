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

package dev.frostlake.executor.commands;

import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.StageArgumentSyntax;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The refusal of a SQL UDF's query body, a table function's too, whose bare stage the syntax refuses where it stands.
 * The account then reads the body inside the expression's frame of parentheses, its places moved by one on the
 * body's first line, and reports (all live-verified):
 *
 * <ol>
 *   <li>the query's keyword;</li>
 *   <li>the token after the first opening parenthesis after it — a stage there as written — where an IN's list is
 *       read on, so a stage inside one is named where it stands;</li>
 *   <li>when that token is a stage that is not a whole select item, a whole operand of a clause, or a whole argument
 *       of such an item's call, a closing parenthesis: its bracket's own when more follows the stage inside it, else
 *       the next one closing a bracket around it, the frame's own included.</li>
 * </ol>
 *
 * <pre>
 *   SELECT @st                      'SELECT' 1
 *   SELECT (@st)                    'SELECT' 1, '@st' 9
 *   SELECT UPPER(@st), (@st)        'SELECT' 1, '@st' 14
 *   SELECT (1), (@st)               'SELECT' 1, '1' 9
 *   SELECT @st FROM (SELECT 1)      'SELECT' 1, 'SELECT' 18
 *   SELECT 1 IN (1, @st)            'SELECT' 1, '@st' 17, ')' 21
 *   SELECT (@st) + 1                'SELECT' 1, '@st' 9, ')' 17
 *   SELECT (@st || 'x')             'SELECT' 1, '@st' 9, ')' 19
 *   SELECT 1 FROM t WHERE (@st)     'SELECT' 1, '@st' 24
 * </pre>
 */
final class SqlUdfStageQueryLines {

    private SqlUdfStageQueryLines() {
    }

    /**
     * The refusal's lines for a query body whose stage breaks its syntax, or null when no stage of it does.
     *
     * @param statement the body's one statement, a query
     * @param body      the body's text
     * @return the lines, joined by line breaks, or null
     */
    static String lines(final FrostlakeParser.StatementContext statement, final String body) {
        if (body.indexOf('@') < 0 || !StageArgumentSyntax.breaksSyntax(statement)) {
            return null;
        }
        final List<Token> tokens = new ArrayList<>();
        terminals(statement, tokens);
        final Map<Integer, FrostlakeParser.StageReferenceExprContext> stages = new HashMap<>();
        for (final FrostlakeParser.StageReferenceExprContext stage : StageArgumentSyntax.stagesIn(statement)) {
            stages.put(Integer.valueOf(stage.getStart().getTokenIndex()), stage);
        }
        int keyword = -1;
        for (int i = 0; i < tokens.size() && keyword < 0; i++) {
            final int type = tokens.get(i).getType();
            if (type == FrostlakeLexer.SELECT || type == FrostlakeLexer.WITH) {
                keyword = i;
            }
        }
        if (keyword < 0) {
            return null;
        }
        final StringBuilder lines = new StringBuilder(sentence(tokens.get(keyword), tokens.get(keyword).getText()));
        int named = -1;
        for (int i = keyword + 1; i + 1 < tokens.size() && named < 0; i++) {
            if (tokens.get(i).getType() == FrostlakeLexer.LPAREN && !opensInList(tokens, i)) {
                named = i + 1;
            } else if (stages.containsKey(Integer.valueOf(tokens.get(i).getTokenIndex()))) {
                final int open = enclosingOpen(tokens, i);
                if (open >= 0 && opensInList(tokens, open)) {
                    named = i;
                }
            }
        }
        if (named < 0) {
            return lines.toString();
        }
        final Token token = tokens.get(named);
        final FrostlakeParser.StageReferenceExprContext stage = stages.get(Integer.valueOf(token.getTokenIndex()));
        lines.append('\n').append(sentence(token, stage == null ? token.getText() : unbrokenText(stage, body)));
        if (stage != null && !standsWhole(tokens, named, stageEnd(tokens, stage))) {
            final Token close = refusedClose(tokens, named, stageEnd(tokens, stage));
            lines.append('\n').append(close == null ? frameEnd(body) : sentence(close, close.getText()));
        }
        return lines.toString();
    }

    /**
     * The closing parenthesis refused after a stage that does not stand whole: its bracket's own when more follows the
     * stage inside it, else the next one closing a bracket around that one; null for the frame's own.
     */
    private static Token refusedClose(final List<Token> tokens, final int first, final int last) {
        final int open = enclosingOpen(tokens, first);
        final int close = open < 0 ? -1 : matchingClose(tokens, open);
        if (close < 0) {
            return null;
        }
        final int after = tokens.get(last + 1).getType();
        if (after != FrostlakeLexer.RPAREN && after != FrostlakeLexer.COMMA) {
            return tokens.get(close);
        }
        final int outer = enclosingOpen(tokens, open);
        final int outerClose = outer < 0 ? -1 : matchingClose(tokens, outer);
        return outerClose < 0 ? null : tokens.get(outerClose);
    }

    /** Whether the bracket at {@code open} is an IN's list. */
    private static boolean opensInList(final List<Token> tokens, final int open) {
        return open > 0 && tokens.get(open - 1).getType() == FrostlakeLexer.IN;
    }

    /** The innermost bracket open where the token at {@code at} stands, or -1. */
    private static int enclosingOpen(final List<Token> tokens, final int at) {
        int depth = 0;
        for (int i = at - 1; i >= 0; i--) {
            final int type = tokens.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    return i;
                }
                depth--;
            }
        }
        return -1;
    }

    /** The bracket closing the one at {@code open}, or -1. */
    private static int matchingClose(final List<Token> tokens, final int open) {
        int depth = 0;
        for (int i = open; i < tokens.size(); i++) {
            final int type = tokens.get(i).getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /** The index of the stage's last token. */
    private static int stageEnd(final List<Token> tokens, final FrostlakeParser.StageReferenceExprContext stage) {
        final int stop = stage.getStop().getTokenIndex();
        for (int i = tokens.size() - 1; i >= 0; i--) {
            if (tokens.get(i).getTokenIndex() <= stop) {
                return i;
            }
        }
        return tokens.size() - 1;
    }

    /**
     * Whether the stage from {@code first} to {@code last} is a whole element of its bracket, and that bracket — with
     * the call it opens — a whole select item or clause operand, in a body that does not open with a bracket.
     */
    private static boolean standsWhole(final List<Token> tokens, final int first, final int last) {
        if (tokens.get(0).getType() == FrostlakeLexer.LPAREN) {
            return false;
        }
        final int open = enclosingOpen(tokens, first);
        final int close = open < 0 ? -1 : matchingClose(tokens, open);
        if (close < 0 || last + 1 >= tokens.size()) {
            return false;
        }
        final int before = tokens.get(first - 1).getType();
        final int after = tokens.get(last + 1).getType();
        if (before != FrostlakeLexer.LPAREN && before != FrostlakeLexer.COMMA
                || after != FrostlakeLexer.RPAREN && after != FrostlakeLexer.COMMA) {
            return false;
        }
        final int start = open > 0 && isName(tokens.get(open - 1)) ? open - 1 : open;
        if (start == 0 || !opensItem(tokens.get(start - 1).getType())) {
            return false;
        }
        return close + 1 == tokens.size() || endsItem(tokens.get(close + 1));
    }

    /** Whether a token of this type stands before a whole select item or clause operand. */
    private static boolean opensItem(final int type) {
        return type == FrostlakeLexer.SELECT || type == FrostlakeLexer.DISTINCT || type == FrostlakeLexer.COMMA
            || type == FrostlakeLexer.WHERE || type == FrostlakeLexer.BY || type == FrostlakeLexer.HAVING
            || type == FrostlakeLexer.QUALIFY;
    }

    /** Whether a token ends a whole select item or clause operand: a comma, a clause's word, an alias. */
    private static boolean endsItem(final Token token) {
        final int type = token.getType();
        return type == FrostlakeLexer.COMMA || type == FrostlakeLexer.FROM || type == FrostlakeLexer.WHERE
            || type == FrostlakeLexer.GROUP || type == FrostlakeLexer.HAVING || type == FrostlakeLexer.QUALIFY
            || type == FrostlakeLexer.ORDER || type == FrostlakeLexer.LIMIT || type == FrostlakeLexer.AS
            || isName(token);
    }

    private static boolean isName(final Token token) {
        return token.getType() == FrostlakeLexer.IDENTIFIER || token.getType() == FrostlakeLexer.QUOTED_IDENTIFIER;
    }

    /** The stage as written up to the first blank inside it: the account's stage is one word. */
    private static String unbrokenText(final FrostlakeParser.StageReferenceExprContext stage, final String body) {
        final int from = stage.getStart().getStartIndex();
        int to = stage.getStop().getStopIndex();
        for (int c = from; c <= to && c < body.length(); c++) {
            if (Character.isWhitespace(body.charAt(c))) {
                to = c - 1;
            }
        }
        return body.substring(from, Math.min(to + 1, body.length()));
    }

    /** A line naming {@code text} at the token's place in the frame: one further on the body's first line. */
    private static String sentence(final Token token, final String text) {
        final int position = token.getCharPositionInLine() + (token.getLine() == 1 ? 1 : 0);
        return "syntax error line " + token.getLine() + " at position " + position + " unexpected '" + text + "'.";
    }

    /** The line naming the frame's closing parenthesis, right after the body. */
    private static String frameEnd(final String body) {
        int line = 1;
        int lineStart = 0;
        for (int c = 0; c < body.length(); c++) {
            if (body.charAt(c) == '\n') {
                line++;
                lineStart = c + 1;
            }
        }
        final int position = body.length() - lineStart + (line == 1 ? 1 : 0);
        return "syntax error line " + line + " at position " + position + " unexpected ')'.";
    }

    private static void terminals(final ParseTree tree, final List<Token> into) {
        if (tree instanceof TerminalNode) {
            final Token token = ((TerminalNode) tree).getSymbol();
            if (token.getType() != Token.EOF && token.getTokenIndex() >= 0) {
                into.add(token);
            }
            return;
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            terminals(tree.getChild(i), into);
        }
    }
}
