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

import java.util.Collections;
import java.util.List;
import org.antlr.v4.runtime.Token;

/**
 * The line live reports for a replication listing whose WITH PRIMARY is not followed by a name and the statement's
 * end: the token where the name is due, or the token after the name - past a WITH written there, which the account
 * reads as a modifier's (live-verified):
 *
 * <pre>
 *   SHOW REPLICATION DATABASES WITH PRIMARY 'a'                    the text
 *   SHOW REPLICATION DATABASES WITH PRIMARY a.b.c IN ACCOUNT       the IN
 *   SHOW REPLICATION DATABASES WITH PRIMARY a.b.c LIKE 'X'         the LIKE
 *   SHOW REPLICATION DATABASES WITH PRIMARY a.b.c WITH PRIMARY x   the second PRIMARY
 * </pre>
 */
final class ReplicationPrimaryLines {

    private ReplicationPrimaryLines() {
    }

    /**
     * @param spoken the text's default-channel tokens, end of input included
     * @return the line, or null when the text is no such listing or its primary's name ends the statement
     */
    static List<String> lines(final List<Token> spoken) {
        if (spoken.size() < 6 || spoken.get(0).getType() != FrostlakeLexer.SHOW
                || spoken.get(1).getType() != FrostlakeLexer.IDENTIFIER
                || spoken.get(2).getType() != FrostlakeLexer.DATABASES
                    && spoken.get(2).getType() != FrostlakeLexer.GROUPS) {
            return null;
        }
        int at = 3;
        if (spoken.get(at).getType() == FrostlakeLexer.LIKE
                && spoken.get(at + 1).getType() == FrostlakeLexer.STRING_LITERAL) {
            at += 2;
        }
        if (at + 2 >= spoken.size() || spoken.get(at).getType() != FrostlakeLexer.WITH
                || spoken.get(at + 1).getType() != FrostlakeLexer.PRIMARY) {
            return null;
        }
        int after = nameEnd(spoken, at + 2);
        if (after == at + 2) {
            // No name stands where it is due.
            return Collections.singletonList(SyntaxErrorListener.sentence(spoken.get(after)));
        }
        final int type = spoken.get(after).getType();
        if (type == Token.EOF || type == FrostlakeLexer.SEMI || type == FrostlakeLexer.FLOW_ARROW) {
            return null;
        }
        if (type == FrostlakeLexer.WITH && after + 1 < spoken.size()) {
            after++;
        }
        return Collections.singletonList(SyntaxErrorListener.sentence(spoken.get(after)));
    }

    /** The index after the name that starts at {@code from}: its parts and dots, or an IDENTIFIER() call. */
    private static int nameEnd(final List<Token> spoken, final int from) {
        int at = from;
        if (spoken.get(at).getType() == FrostlakeLexer.KW_IDENTIFIER && at + 3 < spoken.size()
                && spoken.get(at + 1).getType() == FrostlakeLexer.LPAREN
                && spoken.get(at + 3).getType() == FrostlakeLexer.RPAREN) {
            return at + 4;
        }
        while (at < spoken.size() && isPart(spoken.get(at))) {
            at++;
            if (at >= spoken.size() || spoken.get(at).getType() != FrostlakeLexer.DOT) {
                break;
            }
            while (at < spoken.size() && spoken.get(at).getType() == FrostlakeLexer.DOT) {
                at++;
            }
        }
        return at;
    }

    private static boolean isPart(final Token token) {
        return token.getType() == FrostlakeLexer.IDENTIFIER || token.getType() == FrostlakeLexer.QUOTED_IDENTIFIER;
    }
}
