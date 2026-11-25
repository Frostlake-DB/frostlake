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

package dev.frostlake.executor;

import dev.frostlake.parser.FrostlakeParser;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * A CALL written after a WITH that defines no procedure, {@code WITH c AS (SELECT 1) CALL p()}: the account reads it
 * where a query stands, as a statement and as a value, and refuses it while the statement compiles by the name it
 * calls, {@code Unknown function P.} - a procedure the schema holds included - and under a qualifier
 * {@code Unknown user-defined function S.P.} (live-verified). Inside a block each statement and each value compiles
 * as it is reached, so a block is left to them.
 */
public final class WithCalls {

    private WithCalls() {
    }

    /**
     * Refuses the first such CALL a statement writes outside a block.
     *
     * @param tree the statement's parse tree
     */
    public static void requireNone(final ParseTree tree) {
        final String refusal = firstRefusal(tree);
        if (refusal != null) {
            throw new RuntimeException(refusal);
        }
    }

    private static String firstRefusal(final ParseTree node) {
        if (node instanceof FrostlakeParser.BeginEndBlockContext || node instanceof FrostlakeParser.DeclareSectionContext) {
            return null;
        }
        if (node instanceof FrostlakeParser.SelectStatementContext
                && ((FrostlakeParser.SelectStatementContext) node).withCall() != null) {
            return AnonymousCallValues.unknownRoutine(
                ((FrostlakeParser.SelectStatementContext) node).withCall().qualifiedName());
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final String refusal = firstRefusal(node.getChild(i));
            if (refusal != null) {
                return refusal;
            }
        }
        return null;
    }
}
