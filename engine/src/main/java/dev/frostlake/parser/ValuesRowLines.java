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
 * The lines live reports for a source written after the comma that follows a VALUES list with nothing after it (see
 * ValuesListRowFault). Live reads the comma as the start of another row of the list and recovers as a parser does
 * (all live-verified):
 * <pre>
 *   FROM VALUES (1), TABLE(f(…))      'TABLE' alone: the word is dropped and the row read from its '('
 *   FROM VALUES (1), t                't', then '&lt;EOF&gt;': a '(' is taken as written and the row read on
 *   FROM VALUES (1), t u, t2          't', then 'u': where the row's values stop
 *   FROM VALUES (1), LATERAL t        'LATERAL', then 't': a word no value starts with, and the token after it
 *   FROM VALUES (1), (SELECT 2)       'SELECT' alone: a fault inside a bracket is the one line
 * </pre>
 * <p>The row's reading is rebuilt on the text: what stands before the source is blanked and a '(' opening a row
 * written just before it, every position staying where it was written, and the second line is the first a parse of
 * that text reports.
 */
final class ValuesRowLines {

    private ValuesRowLines() {
    }

    /**
     * The lines reported for the source whose fault the first line names.
     *
     * @param sql        the text parsed
     * @param spoken     its default-channel tokens, end of input included
     * @param faultIndex the token index of the token the first line names
     * @return the lines, the first line alone when no recovery reads on
     */
    static List<String> lines(final String sql, final List<Token> spoken, final int faultIndex) {
        int fault = -1;
        for (int i = 0; i < spoken.size() && fault < 0; i++) {
            if (spoken.get(i).getTokenIndex() == faultIndex) {
                fault = i;
            }
        }
        final List<String> lines = new ArrayList<>();
        if (fault < 1) {
            return null;
        }
        final Token refused = spoken.get(fault);
        lines.add(SyntaxErrorListener.sentence(refused));
        if (refused.getType() == Token.EOF || spoken.get(fault - 1).getType() != FrostlakeLexer.COMMA
                || fault + 1 >= spoken.size()) {
            // The end of the statement, or a fault inside a bracket the row was read from.
            return lines;
        }
        final Token next = spoken.get(fault + 1);
        if (next.getType() == FrostlakeLexer.LPAREN) {
            // The refused word is dropped and the row read from the bracket after it.
            return lines;
        }
        if (refused.getType() == FrostlakeLexer.AT) {
            // A stage reference is one token there.
            return lines;
        }
        final String opened = sql == null ? null : rowText(sql, refused.getStartIndex());
        if (opened != null) {
            final List<String> reported = SyntaxErrorListener.linesReportedFor(opened, true);
            if (reported.isEmpty()) {
                return lines;
            }
            if (!reported.get(0).equals(lines.get(0))) {
                lines.add(reported.get(0));
                return lines;
            }
        }
        if (next.getType() != Token.EOF) {
            // No value starts with the refused token: the token after it is refused too.
            lines.add(SyntaxErrorListener.sentence(next));
        }
        return lines;
    }

    /**
     * The text with a row of a VALUES list opened before the refused token: everything before it blanked, line breaks
     * kept, and a query's words up to the row's '(' written on the token's line or an earlier one with room for them,
     * so that every position stays where it was written; null when no line has room.
     */
    private static String rowText(final String sql, final int start) {
        final String opening = "SELECT*FROM VALUES(";
        final StringBuilder text = new StringBuilder(sql.length());
        for (int i = 0; i < start; i++) {
            final char c = sql.charAt(i);
            text.append(c == '\n' || c == '\r' ? c : ' ');
        }
        int lineEnd = start;
        while (lineEnd > 0) {
            if (text.charAt(lineEnd - 1) == '\r') {
                lineEnd--;
            }
            final int lineStart = text.lastIndexOf("\n", lineEnd - 1) + 1;
            if (lineEnd - lineStart >= opening.length()) {
                text.replace(lineEnd - opening.length(), lineEnd, opening);
                return text.append(sql, start, sql.length()).toString();
            }
            lineEnd = lineStart - 1;
        }
        return null;
    }
}
