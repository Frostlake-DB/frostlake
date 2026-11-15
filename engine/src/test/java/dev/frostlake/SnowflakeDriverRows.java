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

package dev.frostlake;

import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.IntervalDayTimeType;
import dev.frostlake.types.IntervalQualifier;
import dev.frostlake.types.IntervalYearMonthType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.VariantType;
import dev.frostlake.types.VectorElementType;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.VectorValue;
import net.snowflake.client.internal.jdbc.SnowflakeTimestampWithTimezone;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.StringNode;

import java.math.BigDecimal;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * How a result read through Snowflake's own JDBC driver becomes an engine result, for the harnesses whose client is
 * that driver: {@link LiveSnowflakeEngine} on a live account and {@link SnowflakeJdbcFlEngine} on Frostlake's
 * endpoint. The driver's metadata gives each column's type, and its values are coerced toward the engine's runtime
 * shapes: temporals become {@code java.time} values, BINARY a {@link BinaryValue}, whole {@code BigDecimal}s
 * {@code Long}s, and a semi-structured column's JSON text its scalar or its compact text.
 */
final class SnowflakeDriverRows {

    private static final ObjectMapper JSON = new ObjectMapper();

    private SnowflakeDriverRows() {
    }

    /**
     * Gives a DML statement's count grid the affected-row mark the embedded engine gives its own, so
     * {@link DatabaseEngine#executeUpdate} answers the same number on both sides: the rows inserted,
     * updated or deleted, summed across MERGE's actions and a multi-table INSERT's targets, and without
     * UPDATE's multi-joined column — the engine's rule. Only a DML statement's grid is marked, read off
     * its leading keyword (an EXECUTE IMMEDIATE by the grid it answers), so a query that merely NAMES
     * its columns alike counts nothing.
     */
    static ResultSet markAffectedRows(final String sql, final ResultSet result) {
        final String keyword = leadingKeyword(sql);
        final boolean dml = keyword.equals("INSERT") || keyword.equals("UPDATE") || keyword.equals("DELETE")
            || keyword.equals("MERGE");
        if (!dml && !keyword.equals("EXECUTE")) {
            return result;
        }
        if (result.getRows().size() != 1 || result.getColumns().isEmpty()) {
            return result;
        }
        long affected = 0;
        for (int i = 0; i < result.getColumns().size(); i++) {
            final String name = result.getColumns().get(i).getName();
            if (!name.startsWith("number of ")) {
                return result;
            }
            final Object cell = result.getRows().get(0).getValue(i);
            if (!name.startsWith("number of multi-joined ") && cell instanceof Number) {
                affected += ((Number) cell).longValue();
            }
        }
        return result.markUpdateCount(affected);
    }

    static String leadingKeyword(final String sql) {
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(sql));
        final Token first = nextDefaultToken(lexer);
        return first.getType() == Token.EOF ? "" : first.getText().toUpperCase(Locale.ROOT);
    }

    /** The next token of the default channel, the end of input included. */
    static Token nextDefaultToken(final FrostlakeLexer lexer) {
        Token token = lexer.nextToken();
        while (token.getType() != Token.EOF && token.getChannel() != Token.DEFAULT_CHANNEL) {
            token = lexer.nextToken();
        }
        return token;
    }

    static ResultSet convert(final java.sql.ResultSet rs) throws SQLException {
        final ResultSetMetaData metaData = rs.getMetaData();
        final int columnCount = metaData.getColumnCount();
        final List<ResultSetColumn> columns = new ArrayList<ResultSetColumn>();
        final boolean[] semiStructured = new boolean[columnCount];
        final boolean[] interval = new boolean[columnCount];
        final boolean[] vector = new boolean[columnCount];
        final boolean[] localZone = new boolean[columnCount];
        final boolean[] writtenZone = new boolean[columnCount];
        for (int i = 1; i <= columnCount; i++) {
            columns.add(new ResultSetColumn(metaData.getColumnLabel(i), declaredType(metaData, i)));
            final String typeName = String.valueOf(metaData.getColumnTypeName(i)).toUpperCase(Locale.ROOT);
            semiStructured[i - 1] = typeName.equals("VARIANT") || typeName.equals("OBJECT")
                || typeName.equals("ARRAY");
            interval[i - 1] = typeName.startsWith("INTERVAL");
            vector[i - 1] = typeName.equals("VECTOR");
            localZone[i - 1] = typeName.equals("TIMESTAMPLTZ") || typeName.equals("TIMESTAMP_LTZ");
            writtenZone[i - 1] = typeName.equals("TIMESTAMPTZ") || typeName.equals("TIMESTAMP_TZ");
        }
        final List<Row> rows = new ArrayList<Row>();
        while (rs.next()) {
            final List<Object> values = new ArrayList<Object>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                // Over JSON results the driver cannot materialize an interval as an object — getObject refuses
                // it — so its text is read: the nanoseconds or months the wire carries.
                values.add(interval[i - 1] ? rs.getString(i)
                    : vector[i - 1] ? floatVectorText(rs.getString(i))
                    : localZone[i - 1] || writtenZone[i - 1] ? zonedCell(rs.getObject(i), localZone[i - 1])
                    : coerce(rs.getObject(i), semiStructured[i - 1]));
            }
            rows.add(new Row(values));
        }
        return new ResultSet(columns, rows);
    }

    /**
     * A VECTOR cell as the engine displays it. The account's driver writes a FLOAT vector's elements itself, at six
     * decimals in one run and as Java's shortest float text in the next - [1.000000,2.000000] or [1.0,2.0] for the
     * same cell - so the elements are read back as the float32 values they name and printed at six decimals; an INT
     * vector's text is the same on both sides.
     */
    private static String floatVectorText(final String cell) {
        if (cell == null || cell.length() < 2 || !cell.startsWith("[") || !cell.endsWith("]")
                || cell.indexOf('.') < 0 && cell.indexOf('E') < 0 && cell.indexOf('e') < 0) {
            return cell;
        }
        final String[] elements = cell.substring(1, cell.length() - 1).split(",");
        final double[] values = new double[elements.length];
        try {
            for (int i = 0; i < elements.length; i++) {
                values[i] = Float.parseFloat(elements[i].trim().replace("\"", ""));
            }
            return VectorValue.of(VectorElementType.FLOAT, values).toString();
        } catch (final RuntimeException noFloatVector) {
            return cell;
        }
    }

    /**
     * A TIMESTAMP_LTZ or TIMESTAMP_TZ cell in the carrier the engine hands it back in. The driver's timestamp keeps
     * the zone it was read in, the session's for an LTZ and the cell's own offset for a TZ, so an LTZ becomes the
     * OffsetDateTime at the session's offset for that instant and a TZ the ZonedDateTime at its fixed offset. Read as
     * a plain timestamp, either one lost its offset and compared as a wall clock alone.
     */
    private static Object zonedCell(final Object value, final boolean localZone) {
        if (!(value instanceof SnowflakeTimestampWithTimezone)) {
            return coerce(value, false);
        }
        final ZonedDateTime zoned = ((SnowflakeTimestampWithTimezone) value).toZonedDateTime();
        return localZone ? zoned.toOffsetDateTime() : zoned.withFixedOffsetZone();
    }

    private static Object coerce(final Object value, final boolean semiStructured) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) value).toLocalDateTime();
        }
        if (value instanceof java.sql.Date) {
            return ((java.sql.Date) value).toLocalDate();
        }
        if (value instanceof java.sql.Time) {
            return ((java.sql.Time) value).toLocalTime();
        }
        if (value instanceof byte[]) {
            return BinaryValue.of((byte[]) value);
        }
        if (value instanceof BigDecimal) {
            // Coerce to Long only when the value carries NO scale — a NUMBER(8,6) result like
            // 5.000000 must stay a scaled BigDecimal, exactly as the engine now produces for
            // division/AVG (stripping trailing zeros here erased Snowflake's result scale).
            final BigDecimal decimal = (BigDecimal) value;
            if (decimal.scale() <= 0 && decimal.precision() - decimal.scale() <= 18) {
                return decimal.longValueExact();
            }
            return decimal;
        }
        if (semiStructured && value instanceof String) {
            // A semi-structured column arrives as JSON text. Snowflake keeps a path result VARIANT —
            // so a string leaf comes back QUOTED ("txt") and a number as its JSON text — while the
            // engine unwraps path results to plain runtime values. Unwrap JSON SCALARS to the same
            // Java shapes the engine produces so value comparisons line up; objects and arrays come
            // back compact, each number token spelled exactly as the account sent it.
            try {
                final JsonNode node = JSON.readTree((String) value);
                if (node.isTextual()) {
                    return node.asText();
                }
                if (node.isBoolean()) {
                    return Boolean.valueOf(node.booleanValue());
                }
                if (node.isNumber() && !node.isIntegralNumber()) {
                    // A DOUBLE leaf stays a double, the shape the engine unwraps a path result to. It
                    // used to hop through BigDecimal.valueOf, which has no negative zero: the account
                    // hands GET(PARSE_JSON('[-0e0]'), 0) back as -0.000000000000000e+00, and the hop
                    // read it as 0.0 — a sign lost in this harness, never on the account.
                    return Double.valueOf(node.doubleValue());
                }
                if (node.isNumber()) {
                    return coerce(node.decimalValue(), false);
                }
                return compactKeepingNumbers((String) value);
            } catch (final RuntimeException notJson) {
                return value;
            }
        }
        return value;
    }

    /**
     * The document compacted, with every number token spelled exactly as the account sent it. A tree's
     * re-serialisation respells a DOUBLE in Java's shortest form — 1.000000000000000e+00 as 1.0 — which
     * is the one place the account's display and the engine's canonical text differ.
     */
    private static String compactKeepingNumbers(final String text) {
        final StringBuilder out = new StringBuilder();
        boolean separate = false;
        try (JsonParser parser = JSON.createParser(text)) {
            JsonToken token = parser.nextToken();
            while (token != null) {
                if (token == JsonToken.END_OBJECT || token == JsonToken.END_ARRAY) {
                    out.append(token == JsonToken.END_OBJECT ? '}' : ']');
                    separate = true;
                } else {
                    if (separate) {
                        out.append(',');
                    }
                    if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
                        out.append(token == JsonToken.START_OBJECT ? '{' : '[');
                        separate = false;
                    } else if (token == JsonToken.PROPERTY_NAME) {
                        out.append(StringNode.valueOf(parser.currentName()).toString()).append(':');
                        separate = false;
                    } else {
                        out.append(token == JsonToken.VALUE_STRING
                            ? StringNode.valueOf(parser.getString()).toString() : parser.getString());
                        separate = true;
                    }
                }
                token = parser.nextToken();
            }
        }
        return out.toString();
    }

    /**
     * The account's own type for a result column, read from the driver's metadata. The JDBC type INT
     * is not enough on its own — VARIANT, OBJECT and ARRAY all arrive as {@code Types.VARCHAR} — so the
     * TYPE NAME decides the family and precision/scale carry the parameters. Measured on the account,
     * over the JSON result format this session forces:
     *
     * <pre>
     *   INT              NUMBER precision 38 scale 0        TIME           TIME  scale 9
     *   NUMBER(10,2)     NUMBER precision 10 scale 2        TIMESTAMP_NTZ  TIMESTAMPNTZ scale 9
     *   FLOAT            DOUBLE                             TIMESTAMP_LTZ  TIMESTAMPLTZ scale 9
     *   VARCHAR(5)       VARCHAR precision 5                TIMESTAMP_TZ   TIMESTAMPTZ  scale 9
     *   TEXT             VARCHAR precision 16777216         VARIANT/OBJECT/ARRAY by NAME
     *   BINARY(8)        BINARY precision 8                 DATE           DATE
     * </pre>
     *
     * <p>Without this every column read VARCHAR, so no test could compare a declared type against the
     * account at all.
     */
    private static DataType declaredType(final ResultSetMetaData metaData, final int index) throws SQLException {
        final String typeName = String.valueOf(metaData.getColumnTypeName(index)).toUpperCase(Locale.ROOT);
        final int precision = metaData.getPrecision(index);
        final int scale = metaData.getScale(index);
        switch (typeName) {
            case "NUMBER":
            case "NUMERIC":
                return new NumericType("NUMBER", precision, scale);
            case "DECIMAL":
                // The one column the driver names so is a file transfer's size, which the client answers itself.
                return new NumericType("DECIMAL", precision, scale);
            case "DECFLOAT":
                return NumericType.DECFLOAT;
            case "DOUBLE":
            case "DOUBLE PRECISION":
            case "FLOAT":
            case "REAL":
                // The driver carries NO precision or scale for this family (both read 0), so only the
                // family itself is measurable here — compare the name, not the parameters.
                return new NumericType("FLOAT", precision, scale);
            case "VARCHAR":
            case "TEXT":
            case "STRING":
            case "CHAR":
                return new StringType("VARCHAR", precision);
            case "BINARY":
            case "VARBINARY":
                return new BinaryType("BINARY", precision);
            case "BOOLEAN":
                return new BooleanType();
            case "DATE":
                return new DateTimeType("DATE", 0, false);
            case "TIME":
                return new DateTimeType("TIME", scale, false);
            case "TIMESTAMPNTZ":
            case "TIMESTAMP_NTZ":
            case "TIMESTAMP":
            case "DATETIME":
                return new DateTimeType("TIMESTAMP_NTZ", scale, false);
            case "TIMESTAMPLTZ":
            case "TIMESTAMP_LTZ":
                return new DateTimeType("TIMESTAMP_LTZ", scale, true);
            case "TIMESTAMPTZ":
            case "TIMESTAMP_TZ":
                return new DateTimeType("TIMESTAMP_TZ", scale, true);
            case "VARIANT":
                return new VariantType();
            case "OBJECT":
                return new ObjectType();
            case "ARRAY":
                return new ArrayType(new VariantType());
            case "INTERVAL_DAY_TIME":
                // The driver says which fields the interval spans through the scale.
                return IntervalDayTimeType.of(IntervalQualifier.ofDriverScale(true, scale));
            case "INTERVAL_YEAR_MONTH":
                return IntervalYearMonthType.of(IntervalQualifier.ofDriverScale(false, scale));
            default:
                return StringType.VARCHAR;
        }
    }
}
