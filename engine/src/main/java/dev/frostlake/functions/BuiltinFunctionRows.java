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

package dev.frostlake.functions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The rows a Snowflake account's {@code SHOW BUILTIN FUNCTIONS} answers, in the account's order (by name, then the
 * account's own order of a name's rows): each row's flags, its counts of arguments, the {@code arguments} text
 * naming every signature it covers with its return type, and its description (live-verified). Some names have one
 * row for all their signatures, others one row each. The listing shows the rows of the names the engine
 * dispatches; the cells every built-in row shares (created at the account's epoch, no schema, not secure, ...) are
 * the listing's to add.
 */
public final class BuiltinFunctionRows {

    private static final String RESOURCE = "builtin-function-rows.tsv";

    /** The cells a resource row holds, in this order. */
    private static final int CELLS = 13;

    private static final List<String[]> ROWS = load();

    private BuiltinFunctionRows() {
    }

    /**
     * Every row, each as its cells: name, is_aggregate, is_ansi, min_num_arguments, max_num_arguments, arguments,
     * description, is_table_function, valid_for_clustering, language, is_memoizable, is_data_metric and
     * is_ai_function; a cell the account answers NULL is null.
     *
     * @return the rows, in the account's order
     */
    public static List<String[]> rows() {
        return ROWS;
    }

    private static List<String[]> load() {
        final List<String[]> rows = new ArrayList<String[]>();
        try (InputStream in = BuiltinFunctionRows.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return Collections.unmodifiableList(rows);
            }
            final BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                final String[] cells = line.split("\t", -1);
                if (cells.length != CELLS) {
                    throw new IllegalStateException(RESOURCE + ": " + cells.length + " cells in " + line);
                }
                for (int i = 0; i < cells.length; i++) {
                    cells[i] = unescape(cells[i]);
                }
                rows.add(cells);
            }
        } catch (final IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
        return Collections.unmodifiableList(rows);
    }

    /** A cell's text with its escapes undone: {@code \t}, {@code \n}, {@code \r}, {@code \\}; {@code \N} is NULL. */
    private static String unescape(final String cell) {
        if ("\\N".equals(cell)) {
            return null;
        }
        if (cell.indexOf('\\') < 0) {
            return cell;
        }
        final StringBuilder sb = new StringBuilder(cell.length());
        int i = 0;
        while (i < cell.length()) {
            final char c = cell.charAt(i);
            if (c != '\\' || i + 1 == cell.length()) {
                sb.append(c);
                i++;
                continue;
            }
            final char next = cell.charAt(i + 1);
            sb.append(next == 't' ? '\t' : next == 'n' ? '\n' : next == 'r' ? '\r' : next);
            i += 2;
        }
        return sb.toString();
    }
}
