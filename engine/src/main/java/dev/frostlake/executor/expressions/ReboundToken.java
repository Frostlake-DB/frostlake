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

package dev.frostlake.executor.expressions;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenSource;
import org.antlr.v4.runtime.misc.Pair;

/**
 * A token that can be moved onto the same token of another text: its source, offsets and position become the other
 * token's, so every node of a parse tree that holds it reads the other text from then on.
 */
final class ReboundToken extends CommonToken {

    private static final long serialVersionUID = 1L;

    /**
     * @param source             the lexer and the text the token was read from
     * @param type               the token's type
     * @param channel            the token's channel
     * @param start              the offset of its first character
     * @param stop               the offset of its last character
     */
    ReboundToken(final Pair<TokenSource, CharStream> source, final int type, final int channel, final int start,
                 final int stop) {
        super(source, type, channel, start, stop);
    }

    /**
     * Moves this token onto {@code other}, a token of the same type at the same place in another text.
     *
     * @param other the token of the other text
     */
    void rebindTo(final Token other) {
        source = new Pair<TokenSource, CharStream>(other.getTokenSource(), other.getInputStream());
        start = other.getStartIndex();
        stop = other.getStopIndex();
        line = other.getLine();
        charPositionInLine = other.getCharPositionInLine();
        text = null;
    }
}
