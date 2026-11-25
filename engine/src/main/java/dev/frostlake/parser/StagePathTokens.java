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

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.IntStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;

/**
 * Where a stage reference's path ends: at the first token not written straight after the one before it. A path is
 * one word up to a blank, so in {@code FROM @st/data/ t} the {@code t} is the stage's alias, and
 * {@code FROM (SELECT t.$1 FROM @mystage/sales.csv.gz t)} reads the file sales.csv.gz under the alias t, as the
 * COPY transformation examples write it (documented). A parse predicate asks before the path takes another token, so a
 * word after a blank is never joined to it: it was, and the alias became the path's last characters.
 */
public final class StagePathTokens {

    private StagePathTokens() {
    }

    /**
     * Whether the token ahead is written straight after the token read last, with nothing between them.
     *
     * @param input the parser's tokens
     * @return whether the token ahead may continue the path
     */
    public static boolean continues(final TokenStream input) {
        final Token last = input.LT(-1);
        final Token ahead = input.LT(1);
        return last != null && ahead != null && ahead.getType() != Token.EOF
            && last.getStopIndex() + 1 == ahead.getStartIndex();
    }

    /**
     * Whether the '#' or '&amp;' the lexer has just read stands inside a stage path: written straight after a run of
     * characters that holds an '@' and, after it, a '/' ({@code @st/a#b/}, {@code @~/x&y}), with nothing in it that
     * ends a stage reference's word for the account - a blank, a bracket, a comma, a semicolon, a quote, a backtick,
     * an angle bracket, a bar, a character outside ASCII, or a '~' anywhere but straight after the '@'.
     *
     * @param input the lexer's characters, positioned just after the character read (or at it)
     * @return whether the character belongs to a stage path
     */
    public static boolean markInPath(final CharStream input) {
        boolean slash = false;
        final int read = input.LA(-1);
        for (int back = read == '#' || read == '&' ? 2 : 1; ; back++) {
            final int c = input.LA(-back);
            if (c == IntStream.EOF || endsStageWord(c) || (c == '~' && input.LA(-back - 1) != '@')) {
                return false;
            }
            if (c == '/') {
                slash = true;
            } else if (c == '@' && slash) {
                return true;
            }
        }
    }

    private static boolean endsStageWord(final int c) {
        return Character.isWhitespace(c) || c > 0x7F || c == '(' || c == ')' || c == '[' || c == ']' || c == '{'
            || c == '}' || c == ',' || c == ';' || c == '\'' || c == '"' || c == '`' || c == '<' || c == '>'
            || c == '|';
    }
}
