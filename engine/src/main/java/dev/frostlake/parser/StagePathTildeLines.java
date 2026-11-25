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

import dev.frostlake.executor.SqlCompilationError;

import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import java.util.Collections;
import java.util.List;

/**
 * A '~' inside a stage path ends the stage reference's word for the account, and is refused where it stands:
 * {@code LIST @st/t~d/} is {@code unexpected '~'} at 10, {@code LIST @st/~x} at 9 (live-verified). The parse meets
 * the fault earlier, at the path word before the '~' or at the path's '/', because its prediction gives the path up
 * there; a first fault inside the same stage reference is refused at the '~' instead.
 */
public final class StagePathTildeLines {

    private StagePathTildeLines() {
    }

    /**
     * Refuse a text whose first fault lies in a stage reference before a '~' in its path, at that '~'.
     *
     * @param sql      the script's source text
     * @param parsed   the parse's tokens
     * @param listener the parse's error listener
     */
    public static void requireBefore(final String sql, final CommonTokenStream parsed,
                                     final SyntaxErrorListener listener) {
        if (sql == null || !listener.hasErrors() || listener.firstRaisedAt() == null) {
            return;
        }
        final int fault = listener.firstRaisedAt().getStartIndex();
        parsed.fill();
        final List<Token> tokens = parsed.getTokens();
        for (int i = 0; i < tokens.size(); i++) {
            final Token tilde = tokens.get(i);
            if (tilde.getType() != FrostlakeLexer.TILDE || tilde.getStartIndex() < fault) {
                continue;
            }
            final Token at = pathOpener(tokens, i);
            if (at != null && at.getStartIndex() <= fault) {
                final List<String> lines = Collections.singletonList(SyntaxErrorListener.sentence(tilde));
                throw new SqlSyntaxException(SqlCompilationError.of(lines.get(0)), lines, sql);
            }
            return;
        }
    }

    /**
     * The '@' of the stage reference whose path the '~' at {@code index} is written in, or null: walking back over
     * tokens written straight after one another, a '/' and then an '@' (a '~' straight after an '@' names the user's
     * stage, and opens no path).
     */
    private static Token pathOpener(final List<Token> tokens, final int index) {
        boolean slash = false;
        for (int i = index; i > 0; i--) {
            final Token before = tokens.get(i - 1);
            if (before.getChannel() != Token.DEFAULT_CHANNEL
                    || before.getStopIndex() + 1 != tokens.get(i).getStartIndex()) {
                return null;
            }
            if (before.getType() == FrostlakeLexer.SLASH) {
                slash = true;
            } else if (before.getType() == FrostlakeLexer.AT) {
                return slash ? before : null;
            } else if (before.getType() == FrostlakeLexer.TILDE
                    && (i < 2 || tokens.get(i - 2).getType() != FrostlakeLexer.AT)) {
                return null;
            }
        }
        return null;
    }
}
