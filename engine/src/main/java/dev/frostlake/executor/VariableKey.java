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

import java.util.Locale;

/**
 * The key a Snowflake Scripting variable is held under: its name as an identifier's value. A quoted name keeps its
 * case and an unquoted one folds to upper case, so {@code "low"} and {@code low} are two variables while
 * {@code "LOW"} and {@code low} are one (live-verified). The block's statements carry their names in this form
 * already; a name read from a token's own text is turned into it here.
 */
public final class VariableKey {

    private VariableKey() {
    }

    /**
     * The key of a name the block's statements carry. A record's field, written after the record's name and a dot,
     * is found whatever its case.
     *
     * @param name the name, an identifier's value
     * @return the key, or the name itself where it is null
     */
    public static String of(final String name) {
        if (name == null) {
            return null;
        }
        final int dot = name.indexOf('.');
        return dot < 0 ? name : name.substring(0, dot) + name.substring(dot).toUpperCase(Locale.ROOT);
    }

    /**
     * A key as a block would write the name: bare where the name folds to itself, quoted where it keeps a case of
     * its own.
     *
     * @param key the key
     * @return the name as written
     */
    public static String written(final String key) {
        if (key == null || key.equals(key.toUpperCase(Locale.ROOT))) {
            return key;
        }
        return '"' + key.replace("\"", "\"\"") + '"';
    }

    /**
     * The key of a name as a token writes it.
     *
     * @param written the token's text, quotes included
     * @return the name's key, or the text itself where it is null or empty
     */
    public static String ofWritten(final String written) {
        if (written == null || written.isEmpty()) {
            return written;
        }
        if (written.length() > 1 && written.charAt(0) == '"' && written.charAt(written.length() - 1) == '"') {
            return written.substring(1, written.length() - 1).replace("\"\"", "\"");
        }
        return written.toUpperCase(Locale.ROOT);
    }
}
