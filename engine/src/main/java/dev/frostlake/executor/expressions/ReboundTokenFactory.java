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
import org.antlr.v4.runtime.TokenFactory;
import org.antlr.v4.runtime.TokenSource;
import org.antlr.v4.runtime.misc.Pair;

/** Makes a lexer's tokens {@link ReboundToken}s. */
final class ReboundTokenFactory implements TokenFactory<ReboundToken> {

    @Override
    public ReboundToken create(final Pair<TokenSource, CharStream> source, final int type, final String text,
                               final int channel, final int start, final int stop, final int line,
                               final int charPositionInLine) {
        final ReboundToken token = new ReboundToken(source, type, channel, start, stop);
        token.setLine(line);
        token.setCharPositionInLine(charPositionInLine);
        if (text != null) {
            token.setText(text);
        }
        return token;
    }

    @Override
    public ReboundToken create(final int type, final String text) {
        final ReboundToken token = new ReboundToken(new Pair<TokenSource, CharStream>(null, null), type,
            ReboundToken.DEFAULT_CHANNEL, -1, -1);
        token.setText(text);
        return token;
    }
}
