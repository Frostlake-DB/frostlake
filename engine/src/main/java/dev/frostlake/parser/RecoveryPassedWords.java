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

import java.util.Locale;
import java.util.Set;
import org.antlr.v4.runtime.Token;

/**
 * The words the account's reading passes over once a statement has been refused: none of them is taken for a name
 * there (live-verified, each by the token named after it). They are the account's reserved words and the two that
 * frame a block.
 */
final class RecoveryPassedWords {

    private static final Set<String> WORDS = Set.of("THEN", "ELSE", "END", "BEGIN", "AS", "IN", "ON", "SELECT",
        "FROM", "WHERE", "AND", "OR", "NOT", "NULL", "IS", "INSERT", "UPDATE", "DELETE", "CREATE", "DROP", "SET",
        "WITH", "TABLE", "VALUES", "BY", "ORDER", "GROUP", "LIKE", "BETWEEN", "EXISTS", "ALL", "ANY", "DISTINCT",
        "FOR", "ROW", "ROWS", "TO", "INTO", "OF", "QUALIFY", "HAVING", "UNION", "SOME", "START", "ILIKE", "RLIKE",
        "REGEXP", "SAMPLE", "TABLESAMPLE", "UNIQUE", "WHENEVER", "CHECK", "COLUMN", "CONNECT", "CURRENT", "GRANT",
        "REVOKE", "INCREMENT", "MINUS", "INTERSECT", "TRIGGER", "ALTER");

    private RecoveryPassedWords() {
    }

    /**
     * @param token a token
     * @return true when the token is one of the words
     */
    static boolean holds(final Token token) {
        final String text = token.getText();
        return text != null && WORDS.contains(text.toUpperCase(Locale.ROOT));
    }
}
