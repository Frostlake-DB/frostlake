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
 * The lines live reports for a name written where a block's flow arrow, {@code ->>}, wants the statement it chains
 * next. The arrow chains statements only, so the name is refused at once, where the parser that reads the arrow
 * as a statement's end would wait for an assignment (all live-verified):
 *
 * <pre>
 *   BEGIN SELECT 1 -&gt;&gt; y; END;                       'y' alone
 *   BEGIN SHOW TABLES IN nosuch x -&gt;&gt; y; SELECT 1; END;  'y', then the END after the query
 *   BEGIN SHOW TABLES IN nosuch x -&gt;&gt; y z; END;        'y', then the END
 *   BEGIN SHOW TABLES IN -&gt;&gt; y; END;                    the arrow, then 'y'
 * </pre>
 *
 * <p>A name alone before its semicolon stacks the token after the plain queries that follow, and nothing when no query
 * follows ({@code … -&gt;&gt; y; RETURN 1; END;} is 'y' alone); a name with more words after it stacks what the rest
 * refuses as {@link BlockReadOn} reads it.
 */
final class FlowArrowStatementLines {

    private static final String ARROW = "->>";

    private FlowArrowStatementLines() {
    }

    /**
     * The lines when the parse's first fault stands in the statement a flow arrow chains and that statement opens
     * with a name, or at the arrow itself with a name after it; null for any other fault.
     *
     * @param spoken     the text's default-channel tokens, end of input included
     * @param faultIndex the token index of the token the parse's first line names, or -1
     * @return the lines, or null
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex) {
        int at = -1;
        for (int i = 0; i < spoken.size(); i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                at = i;
                break;
            }
        }
        if (at < 0) {
            return null;
        }
        if (isArrow(spoken.get(at))) {
            if (at + 1 >= spoken.size() || !isName(spoken.get(at + 1))) {
                return null;
            }
            final List<String> lines = new ArrayList<>();
            lines.add(SyntaxErrorListener.sentence(spoken.get(at)));
            lines.add(SyntaxErrorListener.sentence(spoken.get(at + 1)));
            return lines;
        }
        int arrow = -1;
        for (int i = at - 1; i >= 0; i--) {
            if (spoken.get(i).getType() == FrostlakeLexer.SEMI) {
                arrow = isArrow(spoken.get(i)) ? i : -1;
                break;
            }
        }
        if (arrow < 0 || arrow + 1 >= at || !isName(spoken.get(arrow + 1))) {
            return null;
        }
        int semicolon = at;
        while (semicolon < spoken.size() && spoken.get(semicolon).getType() != FrostlakeLexer.SEMI
                && spoken.get(semicolon).getType() != Token.EOF) {
            semicolon++;
        }
        final List<String> lines = new ArrayList<>();
        lines.add(SyntaxErrorListener.sentence(spoken.get(arrow + 1)));
        if (semicolon >= spoken.size() || spoken.get(semicolon).getType() != FrostlakeLexer.SEMI) {
            return lines;
        }
        if (semicolon == arrow + 2) {
            final Token after = ShowScopeReadBackLines.afterQueries(spoken, semicolon + 1, 1);
            if (after != null) {
                lines.add(SyntaxErrorListener.sentence(after));
            }
            return lines;
        }
        lines.addAll(BlockReadOn.linesAfter(spoken, semicolon));
        return lines;
    }

    /** Whether a token is a flow arrow the lexer reads as a statement's end. */
    private static boolean isArrow(final Token token) {
        return token.getType() == FrostlakeLexer.SEMI && ARROW.equals(token.getText());
    }

    /** Whether a token is a plain or quoted name that opens no statement. */
    private static boolean isName(final Token token) {
        return token.getType() == FrostlakeLexer.IDENTIFIER || token.getType() == FrostlakeLexer.QUOTED_IDENTIFIER;
    }
}
