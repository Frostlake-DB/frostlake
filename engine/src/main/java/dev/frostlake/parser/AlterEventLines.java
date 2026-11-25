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
 * The line live reports for an ALTER EVENT TABLE it cannot read: the statement is read only as a SET of properties,
 * and any other shape, or a fault anywhere in it, is reported at the TABLE after EVENT, {@code syntax error line 1 at
 * position 12 unexpected 'TABLE'.} (live-verified).
 */
final class AlterEventLines {

    private AlterEventLines() {
    }

    /**
     * The line for a statement opened by ALTER EVENT TABLE whose first fault follows its ALTER, or null.
     *
     * @param spoken     the default-channel tokens, end of input included
     * @param faultIndex the token index of the statement's first fault, negative for the end of input
     * @return the line, or null
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex) {
        int at = faultIndex < 0 ? spoken.size() - 1 : -1;
        for (int i = 0; i < spoken.size() && at < 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                at = i;
            }
        }
        int start = at;
        while (start > 0 && spoken.get(start - 1).getType() != FrostlakeLexer.SEMI) {
            start--;
        }
        if (at < 1 || start + 2 >= spoken.size() || at <= start
                || spoken.get(start).getType() != FrostlakeLexer.ALTER
                || spoken.get(start + 1).getType() != FrostlakeLexer.EVENT
                || spoken.get(start + 2).getType() != FrostlakeLexer.TABLE) {
            return null;
        }
        return Collections.singletonList(SyntaxErrorListener.sentence(spoken.get(start + 2)));
    }
}
