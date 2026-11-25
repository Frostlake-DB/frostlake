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
package dev.frostlake.executor.commands;

import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.parser.FrostlakeParser;

/** The text a COMMENT = '...' clause gives. */
final class CommentClauses {

    private CommentClauses() {
    }

    /**
     * The comment written.
     *
     * @param clause the clause, or null
     * @return its text, or null when there is no clause or it names a session variable
     */
    static String text(final FrostlakeParser.CommentClauseContext clause) {
        if (clause == null) {
            return null;
        }
        if (clause.DOLLAR_QUOTED_STRING() != null) {
            final String raw = clause.DOLLAR_QUOTED_STRING().getText();
            return raw.substring(2, raw.length() - 2);
        }
        return clause.STRING_LITERAL() == null ? null : SqlStringLiterals.decode(clause.STRING_LITERAL().getText());
    }

    /**
     * The comment a column's {@code COMMENT '<text>'} writes.
     *
     * @param clause the clause, or null
     * @return its text, or null when there is no clause
     */
    static String text(final FrostlakeParser.ColumnCommentClauseContext clause) {
        if (clause == null) {
            return null;
        }
        if (clause.DOLLAR_QUOTED_STRING() != null) {
            final String raw = clause.DOLLAR_QUOTED_STRING().getText();
            return raw.substring(2, raw.length() - 2);
        }
        return clause.STRING_LITERAL() == null ? null : SqlStringLiterals.decode(clause.STRING_LITERAL().getText());
    }
}
