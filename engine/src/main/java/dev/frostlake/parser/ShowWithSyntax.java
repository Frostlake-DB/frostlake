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
import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;

/**
 * Where live refuses a SHOW listing whose WITH opens no privilege list, and a SHOW's or a DESCRIBE's name that ends
 * in a dot (all live-verified):
 *
 * <pre>
 *   SHOW TABLES WITH x AS (SELECT 1) SELECT 1                  'x': the WITH is read on, the word after it refused
 *   SHOW TABLES IN DATASET WITH x (SELECT 1)                   'x'
 *   SHOW FUNCTIONS IN WITH x y                                 'WITH', then 'x': a WITH is no scope
 *   SHOW TABLES IN WITH                                        'WITH', then the end of the statement
 *   SHOW TABLES IN WITH PRIVILEGES USAGE                       'WITH' alone
 *   SHOW TABLES IN DATASET .                                   the end of the statement: the dot is read on,
 *   DESCRIBE TABLE t1 .;                                       after a keyword as after any other name
 * </pre>
 */
final class ShowWithSyntax {

    private ShowWithSyntax() {
    }

    /**
     * The tokens live names when the parse's first fault stands in a SHOW listing at or after such a WITH, or at
     * the dot that ends a keyword scope; null for any other fault.
     *
     * @param stream   the parse's tokens
     * @param reported the token the parse's first fault names
     * @return the refused token, and the one stacked after it when there is one, or null
     */
    static List<Token> lines(final TokenStream stream, final Token reported) {
        if (stream instanceof BufferedTokenStream) {
            ((BufferedTokenStream) stream).fill();
        }
        final List<Token> statement = new ArrayList<>();
        int at = -1;
        for (int i = statementStart(stream, reported); i < stream.size(); i++) {
            final Token token = stream.get(i);
            if (token.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            if (token == reported) {
                at = statement.size();
            }
            statement.add(token);
            if (token.getType() == FrostlakeLexer.SEMI || token.getType() == Token.EOF) {
                break;
            }
        }
        final int opener = statement.isEmpty() ? Token.EOF : statement.get(0).getType();
        if (at < 0 || opener != FrostlakeLexer.SHOW && opener != FrostlakeLexer.DESCRIBE
                && opener != FrostlakeLexer.DESC) {
            return null;
        }
        final Token ended = endAfterDots(statement, at);
        if (ended != null) {
            final List<Token> lines = new ArrayList<>();
            lines.add(ended);
            return lines;
        }
        if (opener != FrostlakeLexer.SHOW) {
            return null;
        }
        for (int i = 1; i <= at && i + 1 < statement.size(); i++) {
            if (statement.get(i).getType() != FrostlakeLexer.WITH
                    || statement.get(i - 1).getType() == FrostlakeLexer.STARTS) {
                continue;
            }
            final Token next = statement.get(i + 1);
            final List<Token> lines = new ArrayList<>();
            if (statement.get(i - 1).getType() == FrostlakeLexer.IN) {
                lines.add(statement.get(i));
                if (next.getType() != FrostlakeLexer.PRIVILEGES) {
                    lines.add(next);
                }
                return lines;
            }
            if (next.getType() == FrostlakeLexer.PRIVILEGES || next.getType() == FrostlakeLexer.SEMI
                    || next.getType() == Token.EOF || next == reported) {
                // A fault already at the word after the WITH is read as it stands.
                return null;
            }
            lines.add(next);
            return lines;
        }
        return null;
    }

    /**
     * The lines of a SHOW listing refused at the word after a WITH that opens no privilege list, when that word ends
     * the statement and the statement stands in a control construct's body (all live-verified): the word, and then the next statement is
     * passed over and the rest read once the construct around it is given up ({@link BlockReadOn}) — nothing when the
     * statement passed over closes a construct. A block's own statement is read on as the parse reads it.
     *
     * <pre>
     *   BEGIN LOOP SHOW TABLES WITH x; SELECT 1; END LOOP; END;                        'x', then the last END
     *   BEGIN IF (TRUE) THEN SHOW TABLES WITH x; ELSE SELECT 1; SELECT 2; END IF; END;   'x', then the last END
     *   BEGIN IF (TRUE) THEN SHOW TABLES WITH x; END IF; END;                          'x' alone
     * </pre>
     *
     * @param spoken     the text's default-channel tokens, end of input included
     * @param faultIndex the token index of the token the parse's first line names, or -1
     * @return the lines, or null for any other fault
     */
    static List<String> wordAfterWithInBlock(final List<Token> spoken, final int faultIndex) {
        int at = -1;
        for (int i = 0; i < spoken.size(); i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                at = i;
                break;
            }
        }
        if (at < 2 || at + 2 >= spoken.size() || spoken.get(at - 1).getType() != FrostlakeLexer.WITH
                || spoken.get(at + 1).getType() != FrostlakeLexer.SEMI || !SyntaxErrorListener.isNameLike(spoken.get(at))) {
            return null;
        }
        int show = at - 2;
        while (show >= 0 && spoken.get(show).getType() != FrostlakeLexer.SHOW) {
            final int type = spoken.get(show).getType();
            if (type == FrostlakeLexer.SEMI || type == FrostlakeLexer.STARTS || type == FrostlakeLexer.IN) {
                return null;
            }
            show--;
        }
        if (show < 0 || !BlockReadOn.standsInBlock(spoken, at + 1)
                || BlockReadOn.standsDirectlyInBlock(spoken, at + 1)) {
            return null;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(at)));
        if (spoken.get(at + 2).getType() == FrostlakeLexer.END) {
            return lines;
        }
        final int next = BlockReadOn.semicolonFrom(spoken, at + 2);
        if (next >= 0) {
            lines.addAll(BlockReadOn.linesAfter(spoken, next));
        }
        return lines;
    }

    /**
     * The token that ends the statement — its semicolon, or the end of the input — when the fault is a dot written
     * after a name and nothing but dots follows it; null otherwise.
     */
    private static Token endAfterDots(final List<Token> statement, final int at) {
        if (statement.get(at).getType() != FrostlakeLexer.DOT || at < 1
                || !SyntaxErrorListener.isNameLike(statement.get(at - 1))
                    && statement.get(at - 1).getType() != FrostlakeLexer.QUOTED_IDENTIFIER
                || statement.get(at - 1).getType() == FrostlakeLexer.IN) {
            return null;
        }
        for (int i = at; i < statement.size(); i++) {
            final int type = statement.get(i).getType();
            if (type == Token.EOF || type == FrostlakeLexer.SEMI) {
                return statement.get(i);
            }
            if (type != FrostlakeLexer.DOT) {
                return null;
            }
        }
        return null;
    }

    /** The index of the first token of the statement a token stands in. */
    private static int statementStart(final TokenStream stream, final Token token) {
        int start = token.getTokenIndex();
        for (int i = token.getTokenIndex() - 1; i >= 0; i--) {
            final Token read = stream.get(i);
            if (read.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            if (read.getType() == FrostlakeLexer.SEMI || read.getType() == FrostlakeLexer.BEGIN
                    || read.getType() == FrostlakeLexer.THEN || read.getType() == FrostlakeLexer.ELSE
                    || read.getType() == FrostlakeLexer.DO || read.getType() == FrostlakeLexer.LOOP
                    || read.getType() == FrostlakeLexer.REPEAT) {
                break;
            }
            start = i;
        }
        return start;
    }
}
