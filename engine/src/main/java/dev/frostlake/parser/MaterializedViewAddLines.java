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
 * The line the account reports for an ALTER MATERIALIZED VIEW or an ALTER EXTERNAL TABLE that adds a row access
 * policy, which neither takes an ADD of: {@code ALTER MATERIALIZED VIEW mv ADD ROW ACCESS POLICY rp ON (a)} is refused
 * at the ADD alone and {@code ALTER EXTERNAL TABLE et ADD ROW ACCESS POLICY rp ON (VALUE)} at the ROW alone, the
 * policy's column list not read on (live-verified). An external table takes its row access policy only when it is
 * created.
 */
final class MaterializedViewAddLines {

    private MaterializedViewAddLines() {
    }

    /**
     * The one line for such a statement whose first fault is the ADD, or the ROW after it, or the lines worked out
     * so far.
     *
     * @param spoken the text's default-channel tokens, end of input included
     * @param faults the token indexes of the faults, in order, -1 for the end of input
     * @param lines  the lines worked out so far
     * @return the lines
     */
    static List<String> lines(final List<Token> spoken, final List<Integer> faults, final List<String> lines) {
        if (faults.isEmpty() || lines == null || lines.size() < 2) {
            return lines;
        }
        final int fault = spokenIndex(spoken, faults.get(0).intValue());
        if (fault < 4 || type(spoken, 0) != FrostlakeLexer.ALTER) {
            return lines;
        }
        final boolean materializedView = type(spoken, 1) == FrostlakeLexer.MATERIALIZED
            && type(spoken, 2) == FrostlakeLexer.VIEW && type(spoken, fault) == FrostlakeLexer.ADD
            && addsRowAccessPolicy(spoken, fault + 1);
        final boolean externalTable = type(spoken, 1) == FrostlakeLexer.EXTERNAL
            && type(spoken, 2) == FrostlakeLexer.TABLE && type(spoken, fault - 1) == FrostlakeLexer.ADD
            && addsRowAccessPolicy(spoken, fault);
        return materializedView || externalTable ? Collections.singletonList(lines.get(0)) : lines;
    }

    /** Whether ROW ACCESS POLICY is spoken from that index on. */
    private static boolean addsRowAccessPolicy(final List<Token> spoken, final int at) {
        return type(spoken, at) == FrostlakeLexer.ROW && type(spoken, at + 1) == FrostlakeLexer.ACCESS
            && type(spoken, at + 2) == FrostlakeLexer.POLICY;
    }

    private static int type(final List<Token> spoken, final int at) {
        return at >= 0 && at < spoken.size() ? spoken.get(at).getType() : Token.EOF;
    }

    private static int spokenIndex(final List<Token> spoken, final int tokenIndex) {
        if (tokenIndex < 0) {
            return -1;
        }
        for (int i = 0; i < spoken.size(); i++) {
            if (spoken.get(i).getTokenIndex() == tokenIndex) {
                return i;
            }
        }
        return -1;
    }
}
