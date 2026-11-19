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
 * The lines live reports for a DESCRIBE whose bracket after the name opens with a word no name may be: the word,
 * and not the token after it, which the account's reading takes with the word (live-verified):
 *
 * <pre>
 *   DESCRIBE TABLE t (TRIGGER)         'TRIGGER'
 *   DESCRIBE TABLE t (TRIGGER, x)      'TRIGGER', then the ')'
 *   DESCRIBE TABLE t (TRIGGER x)       'TRIGGER', then the ')'
 *   DESCRIBE TABLE t (TRIGGER) x       'TRIGGER', then the end of the input
 *   DESCRIBE TABLE t (TYPE)            'TYPE', then the ')': the word may be a name
 * </pre>
 */
final class DescribeBracketWordLines {

    private DescribeBracketWordLines() {
    }

    /**
     * @param spoken the text's default-channel tokens, end of input included
     * @param lines  the lines worked out for the text
     * @return {@code lines}, or the lines without the one that names the token after the bracket's first word, where
     *         the text is a DESCRIBE refused first at that word
     */
    static List<String> withoutTheTokenAfter(final List<Token> spoken, final List<String> lines) {
        if (lines.size() < 2 || spoken.size() < 6 || spoken.get(0).getType() != FrostlakeLexer.DESCRIBE
                && spoken.get(0).getType() != FrostlakeLexer.DESC) {
            return lines;
        }
        int open = -1;
        for (int i = 1; i + 2 < spoken.size() && open < 0; i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.LPAREN) {
                open = i;
            }
        }
        // The bracket stands straight after the name, and its first token is a word no name may be: a word that may
        // be a name, as TYPE and VIEW may, keeps the token after it.
        if (open < 0 || !SyntaxErrorListener.isNameLike(spoken.get(open - 1))
                || !RecoveryPassedWords.holds(spoken.get(open + 1))) {
            return lines;
        }
        if (!lines.get(0).equals(SyntaxErrorListener.sentence(spoken.get(open + 1)))
                || !lines.get(1).equals(SyntaxErrorListener.sentence(spoken.get(open + 2)))) {
            return lines;
        }
        final List<String> told = new ArrayList<>(lines);
        told.remove(1);
        return told;
    }
}
