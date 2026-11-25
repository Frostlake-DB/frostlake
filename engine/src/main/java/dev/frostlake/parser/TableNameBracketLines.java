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

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.atn.ATN;

import java.util.ArrayList;
import java.util.List;

/**
 * The lines the account reports for a bracket written right after a table's name in a query's FROM list, which no
 * reference takes: {@code SELECT 1 FROM zz (a)}. The bracket is refused, and the recovery skips from it to the first
 * token that may follow the reference — a comma, a clause's or a join's word, a semicolon or the end of input, and a
 * closing bracket where the query stands in an EXISTS's, a set operation's or the statement's own brackets — wherever
 * that token stands, inside the refused bracket too, and reads on from there as if the reference had been the name
 * alone (all live-verified):
 *
 * <pre>
 *   SELECT 1 FROM zz (a)                          '(' alone
 *   SELECT 1 FROM zz (a, b)                       '(', then the ')': the list goes on after the comma
 *   SELECT 1 FROM zz (a WHERE 1 = 1)              '(', then the ')': so does the WHERE
 *   SELECT 1 FROM zz (a b)                        '(' alone
 *   SELECT 1 FROM t JOIN USING (a)                '(' alone: after a JOIN, USING is the table's name
 *   SELECT 1 WHERE EXISTS (SELECT 1 FROM zz (a))  '(', then the last ')': the first one closes the EXISTS
 * </pre>
 *
 * <p>A query in a derived table's brackets, a value's or a common table expression's is read otherwise and is left
 * to the other rules, and so is a block's.
 */
final class TableNameBracketLines {

    private TableNameBracketLines() {
    }

    /**
     * The lines for a statement whose first fault is such a bracket, or a token inside it, or null.
     *
     * @param sql            the text parsed
     * @param spoken         its default-channel tokens, end of input included
     * @param faultIndex     the token index of the token the parse's first line names
     * @param statementParse whether the text was parsed as a whole statement
     * @param firstOnly      whether only the first line is wanted
     * @return the lines, or null
     */
    static List<String> lines(final String sql, final List<Token> spoken, final int faultIndex,
                              final boolean statementParse, final boolean firstOnly) {
        if (sql == null || !statementParse || faultIndex < 0) {
            return null;
        }
        int fault = -1;
        for (int i = 0; i < spoken.size() && fault < 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
            }
        }
        if (fault < 3) {
            return null;
        }
        for (int i = 0; i < fault; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.BEGIN || type == FrostlakeLexer.DECLARE) {
                return null;
            }
        }
        final int open = openingBracket(spoken, fault);
        // An IDENTIFIER() reference's word takes its bracket, and only the bracket itself is refused as a table's.
        final int nameStart = open < 0 ? -1 : referenceStart(spoken, open, open == fault);
        if (nameStart < 0) {
            return null;
        }
        final int select = owningSelect(spoken, nameStart);
        if (select < 0) {
            return null;
        }
        final int enclosing = enclosingBracket(spoken, select);
        boolean closeStops = false;
        if (enclosing >= 0) {
            if (!readsOn(spoken, enclosing) || inDerivedTable(spoken, enclosing)) {
                return null;
            }
            closeStops = true;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(open)));
        if (firstOnly) {
            return lines;
        }
        int resumed = -1;
        for (int i = open + 1; i < spoken.size() && resumed < 0; i++) {
            final int type = spoken.get(i).getType();
            if (type == Token.EOF || type == FrostlakeLexer.SEMI || type == FrostlakeLexer.COMMA
                    || isClauseWord(type) || isJoinWord(type) || closeStops && type == FrostlakeLexer.RPAREN) {
                resumed = i;
            }
        }
        if (resumed < 0 || spoken.get(resumed).getType() == Token.EOF) {
            return lines;
        }
        final char[] text = sql.toCharArray();
        final int from = spoken.get(nameStart).getStartIndex();
        for (int c = from; c < spoken.get(resumed).getStartIndex() && c < text.length; c++) {
            if (text[c] != '\n' && text[c] != '\r') {
                text[c] = c == from ? 'x' : ' ';
            }
        }
        final int[] at = SyntaxErrorListener.sentenceCoordinates(lines.get(0));
        for (final String line : SyntaxErrorListener.linesReportedFor(new String(text), true)) {
            final int[] place = SyntaxErrorListener.sentenceCoordinates(line);
            if (place != null && at != null && (place[0] > at[0] || place[0] == at[0] && place[1] > at[1])) {
                lines.add(line);
            }
        }
        return lines;
    }

    /** The bracket the fault is, or the innermost one open where it stands; -1 when there is none. */
    private static int openingBracket(final List<Token> spoken, final int fault) {
        if (spoken.get(fault).getType() == FrostlakeLexer.LPAREN) {
            return fault;
        }
        int depth = 0;
        for (int i = fault - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    return i;
                }
                depth--;
            } else if (type == FrostlakeLexer.SEMI) {
                return -1;
            }
        }
        return -1;
    }

    /**
     * Whether the bracket at {@code open} follows a FROM list's table name, an IDENTIFIER() reference's word included.
     *
     * @param spoken the default-channel tokens
     * @param open   the index of the bracket
     * @return whether a table name stands right before it
     */
    static boolean followsReference(final List<Token> spoken, final int open) {
        return referenceStart(spoken, open, true) >= 0;
    }

    /**
     * The index of the first token of the table name the bracket at {@code open} follows, when that name is a
     * reference of a FROM list — after its FROM, a JOIN or a comma — or -1; with {@code identifierWord} the name may
     * be IDENTIFIER's word.
     */
    private static int referenceStart(final List<Token> spoken, final int open, final boolean identifierWord) {
        if (open < 2) {
            return -1;
        }
        int start = open - 1;
        final int last = spoken.get(start).getType();
        final boolean joinedUsing = last == FrostlakeLexer.USING && start > 0
            && spoken.get(start - 1).getType() == FrostlakeLexer.JOIN;
        final boolean identifier = "IDENTIFIER".equalsIgnoreCase(spoken.get(start).getText());
        if (identifier ? !identifierWord
                : !joinedUsing && (!isName(last) || last == FrostlakeLexer.TABLE || last == FrostlakeLexer.LATERAL)) {
            return -1;
        }
        while (start >= 2 && spoken.get(start - 1).getType() == FrostlakeLexer.DOT
                && isName(spoken.get(start - 2).getType())) {
            start -= 2;
        }
        if (start < 1) {
            return -1;
        }
        final int before = spoken.get(start - 1).getType();
        return before == FrostlakeLexer.FROM || before == FrostlakeLexer.JOIN || before == FrostlakeLexer.COMMA
            ? start : -1;
    }

    /**
     * The index of the SELECT whose FROM list holds the reference starting at {@code start}, or -1 when the reference
     * stands in no such list at the same bracket depth.
     */
    private static int owningSelect(final List<Token> spoken, final int start) {
        int depth = 0;
        boolean from = false;
        for (int i = start - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    return -1;
                }
                depth--;
            } else if (depth > 0) {
                continue;
            } else if (type == FrostlakeLexer.FROM) {
                if (from) {
                    return -1;
                }
                from = true;
            } else if (type == FrostlakeLexer.SELECT) {
                return from ? i : -1;
            } else if (type == FrostlakeLexer.SEMI || !from && isClauseWord(type)) {
                return -1;
            }
        }
        return -1;
    }

    /** The bracket the query opening at {@code select} stands in, or -1 when it stands in none. */
    private static int enclosingBracket(final List<Token> spoken, final int select) {
        int depth = 0;
        for (int i = select - 1; i >= 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    return i;
                }
                depth--;
            } else if (type == FrostlakeLexer.SEMI) {
                return -1;
            }
        }
        return -1;
    }

    /**
     * Whether a query in the bracket at {@code open} is read on through a closing bracket: an EXISTS's, a set
     * operation's or the statement's own.
     */
    private static boolean readsOn(final List<Token> spoken, final int open) {
        if (open == 0) {
            return true;
        }
        int before = spoken.get(open - 1).getType();
        if (before == FrostlakeLexer.EXISTS) {
            // An EXISTS in a bracket of its own is read otherwise, but for an alert's condition.
            int word = open - 2;
            if (word >= 0 && spoken.get(word).getType() == FrostlakeLexer.NOT) {
                word--;
            }
            return word < 0 || spoken.get(word).getType() != FrostlakeLexer.LPAREN
                || word > 0 && spoken.get(word - 1).getType() == FrostlakeLexer.IF;
        }
        if (before == FrostlakeLexer.SEMI) {
            return true;
        }
        if ((before == FrostlakeLexer.ALL || before == FrostlakeLexer.DISTINCT) && open > 1) {
            before = spoken.get(open - 2).getType();
        }
        return before == FrostlakeLexer.UNION || before == FrostlakeLexer.EXCEPT || before == FrostlakeLexer.MINUS_KW
            || before == FrostlakeLexer.INTERSECT;
    }

    /** Whether a bracket around the one at {@code open} is a derived table's: one after a FROM, a JOIN or a comma. */
    private static boolean inDerivedTable(final List<Token> spoken, final int open) {
        int at = open;
        while (at > 0) {
            final int outer = enclosingBracket(spoken, at);
            if (outer < 0) {
                return false;
            }
            final int before = outer > 0 ? spoken.get(outer - 1).getType() : Token.EOF;
            if (before == FrostlakeLexer.FROM || before == FrostlakeLexer.JOIN || before == FrostlakeLexer.COMMA
                    || before == FrostlakeLexer.LATERAL) {
                return true;
            }
            at = outer;
        }
        return false;
    }

    /** Whether a token of this type can be a name. */
    private static boolean isName(final int type) {
        final ATN atn = FrostlakeParser._ATN;
        return atn.nextTokens(atn.ruleToStartState[FrostlakeParser.RULE_identifier]).contains(type);
    }

    /** Whether a word opens a clause that may follow a FROM list. */
    private static boolean isClauseWord(final int type) {
        return type == FrostlakeLexer.WHERE || type == FrostlakeLexer.GROUP || type == FrostlakeLexer.HAVING
            || type == FrostlakeLexer.QUALIFY || type == FrostlakeLexer.ORDER || type == FrostlakeLexer.LIMIT
            || type == FrostlakeLexer.UNION || type == FrostlakeLexer.EXCEPT || type == FrostlakeLexer.MINUS_KW
            || type == FrostlakeLexer.INTERSECT;
    }

    /** Whether a word opens a join of the table before it. */
    private static boolean isJoinWord(final int type) {
        return type == FrostlakeLexer.JOIN || type == FrostlakeLexer.INNER || type == FrostlakeLexer.LEFT
            || type == FrostlakeLexer.RIGHT || type == FrostlakeLexer.FULL || type == FrostlakeLexer.CROSS
            || type == FrostlakeLexer.NATURAL || type == FrostlakeLexer.ASOF;
    }
}
