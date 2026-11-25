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

package dev.frostlake.metastore;

import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.View;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.ColumnTypeJson;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.StructuredArrayType;
import dev.frostlake.types.VariantType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The views of the SNOWFLAKE database's ACCOUNT_USAGE schema: secure views no role owns, each with the account's
 * columns in the account's order, types and nullability (live-verified, the list in {@code account-usage-columns.tsv}).
 * The account fills them with up to a few hours' latency, so an object or a statement just made is not in them yet;
 * here they answer no rows.
 */
final class AccountUsageViews {

    private static final String RESOURCE = "/dev/frostlake/metadata/account-usage-columns.tsv";

    /** The declared width of a text column the account declares at the default width. */
    private static final String DEFAULT_TEXT = "VARCHAR(16777216)";

    /** The type of a text a query answers at the full text width. */
    private static final String FULL_TEXT = "VARCHAR(134217728)";

    /** The type of a column a query answers as the type of a bare NULL. */
    private static final String ZERO_WIDTH_TEXT = "VARCHAR(0)";

    /** The descriptor mark of a text whose SHOW COLUMNS byte budget is one byte per character. */
    private static final String ONE_BYTE_PER_CHARACTER = "B";

    /** The descriptor mark of a VARIANT, OBJECT or ARRAY whose SHOW COLUMNS descriptor carries "fixed":false. */
    private static final String FIXED_KEY = "F";

    /**
     * Each view's columns, in order: the column's name, its declared type, Y or N for whether it takes NULL, the
     * type a query's column answers where that is not the declared one, or null, and the mark of a SHOW COLUMNS
     * descriptor of the account's own, or null.
     */
    private static volatile Map<String, List<String[]>> columns;

    private AccountUsageViews() {
    }

    /**
     * Registers the views in the ACCOUNT_USAGE schema: owned by nobody, and secure. Each column is declared as
     * DESCRIBE VIEW reads it and described in SHOW COLUMNS as the account describes it, and a query's column answers
     * as the account's does: a text declared at the default width mostly at the full text width, some as a bare
     * NULL's {@code VARCHAR(0)}, any other column mostly at its declared type, and every one taking NULL
     * (live-verified).
     *
     * @param accountUsage the SNOWFLAKE database's ACCOUNT_USAGE schema
     */
    static void seed(final Schema accountUsage) {
        for (final Map.Entry<String, List<String[]>> described : loaded().entrySet()) {
            final StringBuilder definition = new StringBuilder("SELECT ");
            final List<TableColumn> resolved = new ArrayList<>();
            boolean first = true;
            for (final String[] column : described.getValue()) {
                definition.append(first ? "" : ", ").append(answered(column)).append(" AS \"").append(column[0])
                    .append('"');
                first = false;
                final DataType type = typeOf(column[1]);
                final boolean nullable = "Y".equals(column[2]);
                final TableColumn declared = new TableColumn(column[0], type, nullable, null, false, false, false);
                if (column[4] != null) {
                    declared.setDescribedType(ColumnTypeJson.renderSystemViewColumn(type, nullable,
                        ONE_BYTE_PER_CHARACTER.equals(column[4]), FIXED_KEY.equals(column[4])));
                }
                resolved.add(declared);
            }
            definition.append(" WHERE FALSE");
            final View view = new View(described.getKey(), definition.toString());
            view.setOwner(null);
            view.setSecure(true);
            view.setResolvedColumns(resolved);
            accountUsage.addView(view);
        }
    }

    /**
     * The item of a view's body that answers a column at the type a query reads it at. A column the account answers
     * as {@code VARCHAR(0)} is a bare NULL, the one item typed that way; quoted names follow, as a column named ROWS
     * or INCREMENT is no alias otherwise.
     */
    private static String answered(final String[] column) {
        if (ZERO_WIDTH_TEXT.equals(column[3])) {
            return "NULL";
        }
        final String type = column[3] == null ? column[1].replace(DEFAULT_TEXT, "VARCHAR")
            : column[3].replace(FULL_TEXT, "VARCHAR");
        return "CAST(NULL AS " + type + ")";
    }

    /** The type a declared spelling of the list names. */
    private static DataType typeOf(final String declared) {
        final int open = declared.indexOf('(');
        final String name = open < 0 ? declared : declared.substring(0, open);
        final String inside = open < 0 ? "" : declared.substring(open + 1, declared.lastIndexOf(')'));
        switch (name) {
            case "VARCHAR":
                return new StringType("VARCHAR", Integer.parseInt(inside));
            case "NUMBER":
                final int comma = inside.indexOf(',');
                return new NumericType("NUMBER", Integer.parseInt(inside.substring(0, comma).trim()),
                    Integer.parseInt(inside.substring(comma + 1).trim()));
            case "FLOAT":
                return NumericType.FLOAT;
            case "BOOLEAN":
                return BooleanType.BOOLEAN;
            case "DATE":
                return DateTimeType.DATE;
            case "TIMESTAMP_LTZ":
            case "TIMESTAMP_TZ":
                return new DateTimeType(name, Integer.parseInt(inside), true);
            case "TIMESTAMP_NTZ":
                return new DateTimeType(name, Integer.parseInt(inside), false);
            case "BINARY":
                return new BinaryType("BINARY", Integer.parseInt(inside));
            case "VARIANT":
                return VariantType.VARIANT;
            case "OBJECT":
                return ObjectType.OBJECT;
            case "ARRAY":
                return inside.isEmpty() ? ArrayType.ARRAY : new StructuredArrayType(typeOf(inside));
            default:
                throw new IllegalStateException("no type for " + declared);
        }
    }

    /** A list cell that may be left out or empty, null then. */
    private static String present(final String[] cells, final int at) {
        return cells.length > at && !cells[at].isEmpty() ? cells[at] : null;
    }

    private static Map<String, List<String[]>> loaded() {
        Map<String, List<String[]>> known = columns;
        if (known == null) {
            known = load();
            columns = known;
        }
        return known;
    }

    private static Map<String, List<String[]>> load() {
        final Map<String, List<String[]>> byView = new LinkedHashMap<>();
        try (InputStream in = AccountUsageViews.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("missing column list: account usage");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                while (line != null) {
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        final String[] cells = line.split("\t", -1);
                        List<String[]> described = byView.get(cells[0]);
                        if (described == null) {
                            described = new ArrayList<>();
                            byView.put(cells[0], described);
                        }
                        described.add(new String[] {cells[1], cells[2], cells[3], present(cells, 4),
                            present(cells, 5)});
                    }
                    line = reader.readLine();
                }
            }
        } catch (final IOException e) {
            throw new IllegalStateException("cannot load column list: account usage", e);
        }
        return Collections.unmodifiableMap(byView);
    }
}
