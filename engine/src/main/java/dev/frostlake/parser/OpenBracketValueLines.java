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
 * The lines live reports for a block's RETURN or assignment whose value opens a bracket and holds nothing: the token
 * after the brackets, not the bracket (live-verified):
 *
 * <pre>
 *   BEGIN RETURN (; END;        the ';'
 *   BEGIN RETURN (); END;       the ')'
 *   BEGIN RETURN ((; END;       the ';', then the end of the input
 *   BEGIN RETURN ((); END;      the ')', then the ';' after it
 *   BEGIN RETURN (;             the ';', then the end of the input
 *   … x := (; RETURN x; END;    the ';'
 * </pre>
 */
final class OpenBracketValueLines {

    private OpenBracketValueLines() {
    }

    /**
     * @param spoken       the text's default-channel tokens, end of input included
     * @param faultIndexes the token indexes of the tokens the parse's faults name
     * @return the lines, or null when the text is no block or none of its faults is such a bracket, the first one
     *         of the text
     */
    static List<String> lines(final List<Token> spoken, final List<Integer> faultIndexes) {
        if (spoken.size() < 4 || spoken.get(0).getType() != FrostlakeLexer.BEGIN
                && spoken.get(0).getType() != FrostlakeLexer.DECLARE) {
            return null;
        }
        int open = -1;
        for (int i = 1; i + 1 < spoken.size() && open < 0; i++) {
            if (spoken.get(i).getType() == FrostlakeLexer.LPAREN
                    && (spoken.get(i - 1).getType() == FrostlakeLexer.RETURN
                        || spoken.get(i - 1).getType() == FrostlakeLexer.COLON_EQ)) {
                open = i;
            }
        }
        if (open < 0) {
            return null;
        }
        int named = open;
        while (named < spoken.size() - 1 && spoken.get(named).getType() == FrostlakeLexer.LPAREN) {
            named++;
        }
        // The parse names the bracket, or already the token after the brackets.
        if (!faultIndexes.contains(Integer.valueOf(spoken.get(open).getTokenIndex()))
                && !faultIndexes.contains(Integer.valueOf(spoken.get(named).getTokenIndex()))) {
            return null;
        }
        for (final Integer faultIndex : faultIndexes) {
            if (faultIndex.intValue() >= 0 && faultIndex.intValue() < spoken.get(open).getTokenIndex()) {
                // An earlier fault is the text's first.
                return null;
            }
        }
        final int after = named;
        final int brackets = after - open;
        final Token told = spoken.get(after);
        final Token end = spoken.get(spoken.size() - 1);
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(told));
        if (told.getType() == FrostlakeLexer.RPAREN) {
            if (brackets > 1 && after + 1 < spoken.size()) {
                lines.add(SyntaxErrorListener.sentence(spoken.get(after + 1)));
            }
        } else if (told.getType() == FrostlakeLexer.SEMI) {
            if (brackets > 1 || spoken.get(after + 1).getType() == Token.EOF) {
                lines.add(SyntaxErrorListener.sentence(end));
            }
        } else if (told.getType() != Token.EOF) {
            return null;
        }
        return lines;
    }
}
