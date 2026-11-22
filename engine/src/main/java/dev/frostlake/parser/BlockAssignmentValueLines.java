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
 * The lines live reports for a scripting block's plain assignment, {@code n := <value>}, whose value does not
 * parse: the first token of the value, whatever token the value fails at. The account then reads on from there: a
 * ';' ends the statement soundly, and the next statement is read as the first was; a word that may be a name is
 * taken, and the token after it - past any ';' - is refused as well, which ends the report (all live-verified):
 *
 * <pre>
 *   n := 1 + ;                                     '1'
 *   n := ABS(1,);                                  'ABS'
 *   n := 1 + ABS(,);                               '1', then the '(' after ABS
 *   n := 1 + x.;                                   '1', then the '.' after x
 *   n := 1 + (SELECT COUNT(*) FROM TABLE(rs));     '1', then the '(' after COUNT
 *   n := 1 + ; n := 2 + ;                          '1', then '2'
 *   n := a + ; n := 2 + ;                          'a': a value that opens with a name ends the report
 * </pre>
 *
 * <p>A value the parse leaves at a word standing outside every bracket is another reading - the value ended before
 * the word, {@code n := a b;} names the {@code b} - and so is a value that opens with a parenthesis; neither is
 * read here.
 */
final class BlockAssignmentValueLines {

    private BlockAssignmentValueLines() {
    }

    /**
     * @param spoken       the text's default-channel tokens, end of input included
     * @param faultIndexes the token indexes of the tokens the parse's faults name, in order
     * @return the lines, or null when the text is no block or its first fault stands in no such value
     */
    static List<String> lines(final List<Token> spoken, final List<Integer> faultIndexes) {
        if (spoken.isEmpty() || spoken.get(0).getType() != FrostlakeLexer.BEGIN
                && spoken.get(0).getType() != FrostlakeLexer.DECLARE) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        int resume = 0;
        for (final Integer faultIndex : faultIndexes) {
            int fault = -1;
            for (int i = 0; i < spoken.size() && fault < 0; i++) {
                if (spoken.get(i).getTokenIndex() == faultIndex.intValue()) {
                    fault = i;
                }
            }
            if (fault < resume) {
                // A fault of a statement read already: this parse's own way on from its first.
                if (fault < 0 && lines.isEmpty()) {
                    return null;
                }
                continue;
            }
            final int assigned = assignmentBefore(spoken, fault);
            if (assigned < 0 || spoken.get(assigned + 1).getType() == FrostlakeLexer.LPAREN
                    || standsOutsideBrackets(spoken, assigned + 1, fault)) {
                return lines.isEmpty() ? null : lines;
            }
            final Token value = spoken.get(assigned + 1);
            lines.add(SyntaxErrorListener.sentence(value));
            resume = readOn(spoken, assigned + 2, lines);
            if (resume < 0 || takenForName(value)) {
                return lines;
            }
        }
        return lines.isEmpty() ? null : lines;
    }

    /**
     * Whether the fault is a word that may be a name, past the value's first token and outside every bracket the
     * value opens: the value ended before it.
     */
    private static boolean standsOutsideBrackets(final List<Token> spoken, final int value, final int fault) {
        if (fault == value || !takenForName(spoken.get(fault))) {
            return false;
        }
        int depth = 0;
        for (int i = value; i < fault; i++) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.LPAREN || type == FrostlakeLexer.LBRACKET || type == FrostlakeLexer.LBRACE) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.RBRACKET
                    || type == FrostlakeLexer.RBRACE) {
                depth--;
            }
        }
        return depth <= 0;
    }

    /**
     * Reads on after an assignment's refused value: the index after the ';' that ends the statement soundly, or -1
     * once the report has ended - at the end of input, at a CALL, or with the token after a word taken for a name,
     * whose line is added.
     *
     * @param spoken the text's tokens
     * @param from   the index to read on from
     * @param lines  the lines so far, which the token after a word is added to
     * @return the index to resume at, or -1
     */
    static int readOn(final List<Token> spoken, final int from, final List<String> lines) {
        for (int i = from; i < spoken.size(); i++) {
            final Token token = spoken.get(i);
            if (token.getType() == Token.EOF || token.getType() == FrostlakeLexer.CALL) {
                return -1;
            }
            if (token.getType() == FrostlakeLexer.SEMI) {
                return i + 1;
            }
            if (takenForName(token)) {
                int next = i + 1;
                while (next < spoken.size() && spoken.get(next).getType() == FrostlakeLexer.SEMI) {
                    next++;
                }
                if (next < spoken.size() && spoken.get(next).getType() != Token.EOF) {
                    lines.add(SyntaxErrorListener.sentence(spoken.get(next)));
                }
                return -1;
            }
        }
        return -1;
    }

    /**
     * Whether the account's reading takes a token for a name: a quoted name, or a word it does not pass over.
     *
     * @param token a token
     * @return true for such a word
     */
    static boolean takenForName(final Token token) {
        final String text = token.getText();
        if (text == null || text.isEmpty()) {
            return false;
        }
        if (text.charAt(0) == '"') {
            return true;
        }
        if (!Character.isLetter(text.charAt(0)) && text.charAt(0) != '_') {
            return false;
        }
        for (int i = 1; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '$') {
                return false;
            }
        }
        return !RecoveryPassedWords.holds(token);
    }

    /**
     * The index of the ':=' of the plain assignment whose value holds the token at {@code at}, or -1 when the token
     * stands in no such assignment: a LET's or a declaration's value is none.
     *
     * @param spoken the text's tokens
     * @param at     the index of a token
     * @return the index of the ':=', or -1
     */
    static int assignmentBefore(final List<Token> spoken, final int at) {
        for (int i = at - 1; i >= 1; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.SEMI) {
                return -1;
            }
            if (type == FrostlakeLexer.COLON_EQ) {
                if (!takenForName(spoken.get(i - 1)) || i < 2) {
                    return -1;
                }
                final int before = spoken.get(i - 2).getType();
                return before == FrostlakeLexer.SEMI || before == FrostlakeLexer.BEGIN || before == FrostlakeLexer.THEN
                    || before == FrostlakeLexer.ELSE || before == FrostlakeLexer.DO || before == FrostlakeLexer.LOOP
                    || before == FrostlakeLexer.REPEAT ? i : -1;
            }
        }
        return -1;
    }
}
