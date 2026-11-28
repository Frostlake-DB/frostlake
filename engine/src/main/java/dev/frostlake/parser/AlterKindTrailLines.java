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

import java.util.Collections;
import java.util.List;
import org.antlr.v4.runtime.Token;

/**
 * The line the account reports for an ALTER whose kind, or whose SET list, is not read whole (all live-verified):
 *
 * <pre>
 *   ALTER MATERIALIZED VIEW v SET COMMENT = 'c' CLUSTER BY (a)    the list's first name alone
 *   ALTER MATERIALIZED VIEW v SET SECURE CLUSTER BY (a)           the name after SECURE, which stands alone
 *   ALTER DYNAMIC TABLE d SET COMMENT = 'c' FOO BAR               the list's first name alone
 *   ALTER EXTERNAL TABLE e SET AUTO_REFRESH = TRUE FOO            the SET alone
 *   ALTER TABLE t SET COMMENT = 'c' TAG x = 'v'                   the TAG, which names no property after a value
 *   ALTER HYBRID TABLE h SET COMMENT = 'c' FOO = 1                the HYBRID alone
 *   ALTER DATA METRIC FUNCTION f(TABLE(INT)) SET COMMENT = 'c'    the token after DATA alone
 * </pre>
 */
final class AlterKindTrailLines {

    private AlterKindTrailLines() {
    }

    /**
     * The lines for a statement whose first fault is one of those shapes, or {@code lines} unchanged for any other.
     *
     * @param spoken     the default-channel tokens, end of input included
     * @param faultIndex the token index of the statement's first fault, negative for the end of input
     * @param lines      the lines the parse reports
     * @return the lines
     */
    static List<String> lines(final List<Token> spoken, final int faultIndex, final List<String> lines) {
        int at = -1;
        for (int i = 0; i < spoken.size() && at < 0; i++) {
            if (faultIndex < 0 ? spoken.get(i).getType() == Token.EOF : spoken.get(i).getTokenIndex() == faultIndex) {
                at = i;
            }
        }
        int start = at;
        while (start > 0 && spoken.get(start - 1).getType() != FrostlakeLexer.SEMI) {
            start--;
        }
        if (at < 1 || type(spoken, start) != FrostlakeLexer.ALTER) {
            return lines;
        }
        final int kind = type(spoken, start + 1);
        if (at == start + 1 && (kind == FrostlakeLexer.HYBRID || kind == FrostlakeLexer.DATA)) {
            return Collections.singletonList(SyntaxErrorListener.sentence(spoken.get(
                kind == FrostlakeLexer.DATA ? at + 1 : at)));
        }
        final int second = type(spoken, start + 2);
        final boolean materialized = kind == FrostlakeLexer.MATERIALIZED && second == FrostlakeLexer.VIEW;
        final boolean dynamic = kind == FrostlakeLexer.DYNAMIC && second == FrostlakeLexer.TABLE;
        final boolean external = kind == FrostlakeLexer.EXTERNAL && second == FrostlakeLexer.TABLE;
        final boolean table = kind == FrostlakeLexer.TABLE;
        if (!materialized && !dynamic && !external && !table) {
            return lines;
        }
        final int verb = verb(spoken, table ? start + 2 : start + 3, at);
        if (verb < 0 || at <= verb + 1) {
            return lines;
        }
        final Token first = spoken.get(verb + 1);
        if (table) {
            // A TAG after a property's value names no property, told where it stands.
            final int tag = at - 1;
            return type(spoken, tag) == FrostlakeLexer.TAG && tag > verb + 1
                && type(spoken, tag - 1) != FrostlakeLexer.COMMA
                ? Collections.singletonList(SyntaxErrorListener.sentence(spoken.get(tag))) : lines;
        }
        if (external) {
            return Collections.singletonList(SyntaxErrorListener.sentence(spoken.get(verb)));
        }
        if (first.getType() == FrostlakeLexer.TAG) {
            return lines;
        }
        if (type(spoken, verb + 2) == FrostlakeLexer.EQ) {
            return Collections.singletonList(SyntaxErrorListener.sentence(first));
        }
        if (materialized && first.getType() == FrostlakeLexer.SECURE && at > verb + 2) {
            return Collections.singletonList(SyntaxErrorListener.sentence(spoken.get(verb + 2)));
        }
        return lines;
    }

    /**
     * The index of the SET that follows the object's name, between {@code from} and the fault, or -1 where something
     * else stands there.
     */
    private static int verb(final List<Token> spoken, final int from, final int fault) {
        for (int i = from; i < fault; i++) {
            final int type = type(spoken, i);
            if (type == FrostlakeLexer.SET) {
                return i;
            }
            if (type != FrostlakeLexer.DOT && type != FrostlakeLexer.IF && type != FrostlakeLexer.EXISTS
                    && !SyntaxErrorListener.isIdentifierToken(spoken.get(i))) {
                return -1;
            }
        }
        return -1;
    }

    private static int type(final List<Token> spoken, final int at) {
        return at >= 0 && at < spoken.size() ? spoken.get(at).getType() : Token.EOF;
    }
}
