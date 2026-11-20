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
 * The lines live reports for a block whose DECLARE section declares a cursor over {@code SELECT TOP ?}: the '?' of
 * each such cursor, and nothing after it (live-verified):
 *
 * <pre>
 *   DECLARE c CURSOR FOR SELECT TOP ? id FROM t; r VARCHAR; BEGIN … END;       the '?'
 *   DECLARE c CURSOR FOR SELECT TOP ? id FROM t; d CURSOR FOR SELECT TOP ? …   both '?'
 * </pre>
 *
 * <p>A cursor a LET declares inside the block is read on as a query is, to the token after the name.
 */
final class DeclaredCursorTopBindLines {

    private DeclaredCursorTopBindLines() {
    }

    /**
     * @param spoken the text's default-channel tokens, end of input included
     * @return the lines, or null when the text opens with no DECLARE section holding such a cursor
     */
    static List<String> lines(final List<Token> spoken) {
        if (spoken.isEmpty() || spoken.get(0).getType() != FrostlakeLexer.DECLARE) {
            return null;
        }
        List<String> lines = null;
        for (int i = 1; i + 4 < spoken.size() && spoken.get(i).getType() != FrostlakeLexer.BEGIN; i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.CURSOR && spoken.get(i + 1).getType() == FrostlakeLexer.FOR
                    && spoken.get(i + 2).getType() == FrostlakeLexer.SELECT
                    && spoken.get(i + 3).getType() == FrostlakeLexer.TOP
                    && spoken.get(i + 4).getType() == FrostlakeLexer.QUESTION) {
                if (lines == null) {
                    lines = new ArrayList<>();
                }
                lines.add(SyntaxErrorListener.sentence(spoken.get(i + 4)));
            }
        }
        return lines;
    }
}
