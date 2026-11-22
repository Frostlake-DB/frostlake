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
 * The lines live reports for a block's EXECUTE IMMEDIATE whose text closes early, so that a word stands after the
 * string: the word, then the '(' of the statement's USING, when it has one (live-verified):
 *
 * <pre>
 *   BEGIN EXECUTE IMMEDIATE 'SELECT 'a' v'; END;                               'a'
 *   BEGIN EXECUTE IMMEDIATE 'SELECT 'a' v' USING (x); END;                     'a', then the '(' after USING
 *   … res := (EXECUTE IMMEDIATE 'SELECT 'a' v' USING (x)); …                   'a', then the '(' after USING
 * </pre>
 *
 * <p>The statement on its own, outside a block, names the word alone.
 */
final class ImmediateStringWordLines {

    private ImmediateStringWordLines() {
    }

    /**
     * @param spoken     the text's default-channel tokens, end of input included
     * @param faultIndex the token index of the token the parse's first line names
     * @return the lines, or null when the text is no block or its first fault is no such statement's
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex) {
        if (spoken.isEmpty() || spoken.get(0).getType() != FrostlakeLexer.BEGIN
                && spoken.get(0).getType() != FrostlakeLexer.DECLARE) {
            return null;
        }
        for (int i = 1; i + 3 < spoken.size(); i++) {
            if (spoken.get(i).getType() != FrostlakeLexer.EXECUTE
                    || spoken.get(i + 1).getType() != FrostlakeLexer.IMMEDIATE
                    || spoken.get(i + 2).getType() != FrostlakeLexer.STRING_LITERAL) {
                continue;
            }
            final Token word = spoken.get(i + 3);
            if (!SyntaxErrorListener.isNameLike(word) || word.getType() == FrostlakeLexer.USING) {
                // A sound statement: the fault lies further on, or in another reading.
                return null;
            }
            final int fault = spoken.get(i - 1).getType() == FrostlakeLexer.LPAREN ? i - 1 : i + 3;
            if (spoken.get(fault).getTokenIndex() != faultIndex && word.getTokenIndex() != faultIndex) {
                return null;
            }
            final List<String> lines = new ArrayList<>();
            lines.add(SyntaxErrorListener.sentence(word));
            for (int k = i + 4; k + 1 < spoken.size() && spoken.get(k).getType() != FrostlakeLexer.SEMI; k++) {
                if (spoken.get(k).getType() == FrostlakeLexer.USING
                        && spoken.get(k + 1).getType() == FrostlakeLexer.LPAREN) {
                    lines.add(SyntaxErrorListener.sentence(spoken.get(k + 1)));
                    break;
                }
            }
            return lines;
        }
        return null;
    }
}
