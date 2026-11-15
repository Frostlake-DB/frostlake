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

import java.util.Collections;
import java.util.List;

/**
 * The lines the account reports for an ALTER STAGE whose SET TAG or UNSET TAG does not read whole. It takes the tag
 * action only when the statement is one to its end, and otherwise reads TAG as the name of the first property the
 * stage sets or unsets, so the tag's name after it is the token named and the rest is read as properties are
 * (live-verified):
 *
 * <pre>
 *   ALTER STAGE st1 SET TAG tg1 = 'x' COMMENT = 'c'    'tg1'
 *   ALTER STAGE st1 SET TAG tg1 = 'x' URL = '…'        'tg1'
 *   ALTER STAGE st1 SET TAG tg1 'x'                    'tg1', ''x''
 *   ALTER STAGE st1 SET TAG tg1 = 'x' 'y'              'tg1', ''y''
 *   ALTER STAGE st1 UNSET TAG tg1 COMMENT              'tg1'
 * </pre>
 *
 * The statement is read again with the word TAG written as a plain name of its length, every other character where it
 * was.
 */
final class StageTagLines {

    /** A plain name as long as the word TAG, which no keyword spells. */
    private static final String PLAIN_WORD = "T_G";

    private StageTagLines() {
    }

    /**
     * The lines for a text whose first fault stands after the TAG of an ALTER STAGE's SET TAG or UNSET TAG, or null
     * for any other text.
     *
     * @param sql        the text parsed
     * @param spoken     its default-channel tokens, end of input included
     * @param faultIndex the token index of the first fault, negative for the end of input
     * @param firstOnly  whether only the first line is wanted
     * @return the lines, or null
     */
    static List<String> lines(final String sql, final List<Token> spoken, final int faultIndex,
                              final boolean firstOnly) {
        final int fault = faultIndex >= 0 ? faultIndex : spoken.size() - 1;
        int tag = -1;
        for (int i = fault - 1; i >= 0 && tag < 0; i--) {
            final int type = spoken.get(i).getType();
            if (type == FrostlakeLexer.SEMI) {
                return null;
            }
            if (type == FrostlakeLexer.TAG) {
                tag = i;
            }
        }
        if (tag < 4 || !opensStageTagAction(spoken, tag)) {
            return null;
        }
        final Token word = spoken.get(tag);
        if (word.getStopIndex() + 1 - word.getStartIndex() != PLAIN_WORD.length()) {
            return null;
        }
        final String read = sql.substring(0, word.getStartIndex()) + PLAIN_WORD
            + sql.substring(word.getStopIndex() + 1);
        if (firstOnly) {
            final String first = SyntaxErrorListener.firstLineReportedFor(read);
            return first == null ? null : Collections.singletonList(first);
        }
        final List<String> lines = SyntaxErrorListener.linesReportedFor(read, true);
        return lines.isEmpty() ? null : lines;
    }

    /** Whether the TAG at {@code tag} follows a SET or an UNSET that follows ALTER STAGE [IF EXISTS] and a name. */
    private static boolean opensStageTagAction(final List<Token> spoken, final int tag) {
        final int action = spoken.get(tag - 1).getType();
        if (action != FrostlakeLexer.SET && action != FrostlakeLexer.UNSET) {
            return false;
        }
        int i = tag - 2;
        if (!SyntaxErrorListener.isIdentifierToken(spoken.get(i))) {
            return false;
        }
        while (i >= 2 && spoken.get(i - 1).getType() == FrostlakeLexer.DOT
                && SyntaxErrorListener.isIdentifierToken(spoken.get(i - 2))) {
            i -= 2;
        }
        i--;
        if (i >= 1 && spoken.get(i).getType() == FrostlakeLexer.EXISTS
                && spoken.get(i - 1).getType() == FrostlakeLexer.IF) {
            i -= 2;
        }
        return i >= 1 && spoken.get(i).getType() == FrostlakeLexer.STAGE
            && spoken.get(i - 1).getType() == FrostlakeLexer.ALTER;
    }
}
