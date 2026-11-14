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
package dev.frostlake.http;

import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.ColumnLengths;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.GeographyType;
import dev.frostlake.types.GeometryType;
import dev.frostlake.types.IntervalDayTimeType;
import dev.frostlake.types.IntervalYearMonthType;
import dev.frostlake.types.MapType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.StructuredArrayType;
import dev.frostlake.types.StructuredObjectType;
import dev.frostlake.types.VariantType;
import dev.frostlake.types.VectorElementType;
import dev.frostlake.types.VectorType;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;

/**
 * A result set as a Snowflake driver reads a JSON result: {@code rowtype}, one column's metadata each, and
 * {@code rowset}, each cell a text or null. The cells are written in the driver's internal form: a fixed-point number
 * with its column's scale, a boolean as 1 or 0, a DATE as the days since the epoch, a TIME as the seconds since
 * midnight, a TIMESTAMP as the seconds since the epoch, both with the column's fraction digits, a TIMESTAMP_TZ followed
 * by its offset in minutes plus 1440, a FLOAT as its SQL text, and everything else as Frostlake's own wire writes it
 * (see {@link ResultSetData}): a semi-structured value as its JSON, a binary as its hex digits, a VECTOR as its text,
 * an interval as its months or nanoseconds. A structured ARRAY, OBJECT or MAP is described and written as the
 * semi-structured value it holds, as the account answers a driver: a MAP as an OBJECT. A VECTOR's metadata carries
 * its dimension and its element type, an interval's the digits of its leading field (in the low four bits) and of its
 * fraction (in the next four) as the precision, and the fields it spans as the scale. A session that takes Arrow
 * results reads the rows as an Arrow stream instead (see {@link SnowflakeArrowRowset}).
 */
final class SnowflakeRowset {

    private static final int ZONE_INDEX_BASE = 1440;
    /** The digits a DECFLOAT column is described with. */
    private static final int DECFLOAT_DIGITS = 38;

    private SnowflakeRowset() {
    }

    /**
     * Writes a result set's {@code rowtype} and {@code rowset}, with {@code total} and {@code returned}, into a
     * query's answer.
     *
     * @param rs   the result set
     * @param data the answer's {@code data}
     */
    static void write(final ResultSet rs, final ObjectNode data) {
        write(rs, data, false);
    }

    /**
     * Writes a result set's {@code rowtype}, its rows - as an Arrow stream in {@code rowsetBase64} where {@code arrow}
     * asks for one and the result has an Arrow form (see {@link SnowflakeArrowRowset}), else as the JSON
     * {@code rowset} - with {@code total}, {@code returned} and the {@code queryResultFormat} it took.
     *
     * @param rs    the result set
     * @param data  the answer's {@code data}
     * @param arrow whether the session takes Arrow results
     */
    static void write(final ResultSet rs, final ObjectNode data, final boolean arrow) {
        final List<ResultSetColumn> columns = rs.getColumns();
        final String[] kinds = new String[columns.size()];
        final ArrayNode rowtype = data.putArray("rowtype");
        for (int i = 0; i < columns.size(); i++) {
            kinds[i] = kindOf(columns.get(i).getDataType());
            rowtype.add(columnOf(columns.get(i), kinds[i]));
        }
        final ResultSetData rendered = ResultSetData.from(rs);
        if (arrow && SnowflakeArrowRowset.encodes(kinds)) {
            data.put("rowsetBase64", SnowflakeArrowRowset.rows(rs, kinds, rendered));
            data.put("total", rendered.getRows().size());
            data.put("returned", rendered.getRows().size());
            data.put("queryResultFormat", "arrow");
            return;
        }
        data.put("queryResultFormat", "json");
        final ArrayNode rowset = data.putArray("rowset");
        rs.reset();
        int row = 0;
        while (rs.next()) {
            final ArrayNode cells = rowset.addArray();
            final List<Object> wire = rendered.getRows().get(row);
            for (int i = 0; i < columns.size(); i++) {
                final String text = cellOf(rs.getValue(i), wire.get(i), kinds[i], columns.get(i).getDataType());
                if (text == null) {
                    cells.addNull();
                } else {
                    cells.add(text);
                }
            }
            row++;
        }
        data.put("total", row);
        data.put("returned", row);
    }

    /** A column's type as the driver names it. */
    private static String kindOf(final DataType type) {
        final String name = type.getName() == null ? "" : type.getName().toUpperCase(Locale.ROOT);
        if (type instanceof NumericType) {
            return NumericType.isDecfloat(type) ? "decfloat" : NumericType.isApproximate(type) ? "real" : "fixed";
        }
        if (type instanceof BooleanType) {
            return "boolean";
        }
        if (type instanceof DateTimeType) {
            if ("DATE".equals(name)) {
                return "date";
            }
            if ("TIME".equals(name)) {
                return "time";
            }
            // TIMESTAMP_NTZ holds TZ in its own name, so the zoneless flavour is matched first.
            return name.contains("NTZ") ? "timestamp_ntz" : name.contains("LTZ") ? "timestamp_ltz"
                : name.contains("TZ") ? "timestamp_tz" : "timestamp_ntz";
        }
        if (type instanceof BinaryType) {
            return "binary";
        }
        if (type instanceof VariantType) {
            return "variant";
        }
        if (type instanceof ObjectType || type instanceof StructuredObjectType || type instanceof MapType) {
            return "object";
        }
        if (type instanceof ArrayType || type instanceof StructuredArrayType) {
            return "array";
        }
        if (type instanceof GeographyType) {
            return "geography";
        }
        if (type instanceof GeometryType) {
            return "geometry";
        }
        if (type instanceof VectorType) {
            return "vector";
        }
        if (type instanceof IntervalYearMonthType) {
            return "interval_year_month";
        }
        if (type instanceof IntervalDayTimeType) {
            return "interval_day_time";
        }
        return "text";
    }

    private static ObjectNode columnOf(final ResultSetColumn column, final String kind) {
        final ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("name", column.getName());
        node.put("type", kind);
        // Whether the column can hold NULL as the account's metadata tells it: worked out from the projection's
        // expression where the engine did, a NOT NULL column read as it is staying NOT NULL.
        final Boolean reported = column.getReportedNullable();
        node.put("nullable", reported != null ? reported.booleanValue()
            : !column.isNullabilityKnown() || column.isNullable());
        final DataType type = column.getDataType();
        if ("fixed".equals(kind)) {
            node.put("precision", ((NumericType) type).getPrecision());
            node.put("scale", ((NumericType) type).getScale());
        } else if ("decfloat".equals(kind)) {
            // A DECFLOAT is described as 38 digits at scale 0, which the driver reads as a display size of 40.
            node.put("precision", DECFLOAT_DIGITS);
            node.put("scale", 0);
        } else if (type instanceof DateTimeType && !"date".equals(kind)) {
            node.put("precision", 0);
            node.put("scale", ((DateTimeType) type).getPrecision());
        } else if (kind.startsWith("interval_")) {
            node.put("precision", intervalPrecision(type));
            node.put("scale", intervalScale(type));
        } else {
            node.putNull("precision");
            node.putNull("scale");
        }
        final Integer length = "text".equals(kind) || "binary".equals(kind) ? ColumnLengths.of(type) : null;
        if (length == null) {
            node.putNull("length");
            node.putNull("byteLength");
        } else {
            node.put("length", length.intValue());
            node.put("byteLength", type instanceof StringType ? Math.min(length.longValue() * 4L, 16777216L)
                : length.longValue());
        }
        node.put("database", column.getSource() == null ? "" : column.getSource().getDatabase());
        node.put("schema", column.getSource() == null ? "" : column.getSource().getSchema());
        node.put("table", column.getSource() == null ? "" : column.getSource().getTable());
        node.putNull("collation");
        if (type instanceof VectorType) {
            vectorOf((VectorType) type, node);
        }
        return node;
    }

    /** A VECTOR's dimension, and its element type as the one entry of its {@code fields}. */
    private static void vectorOf(final VectorType type, final ObjectNode node) {
        node.put("vectorDimension", type.getDimension());
        final ObjectNode element = node.putArray("fields").addObject();
        final boolean whole = type.getElementType() == VectorElementType.INT;
        element.put("type", whole ? "fixed" : "real");
        element.put("nullable", false);
        if (whole) {
            element.put("precision", 38);
            element.put("scale", 0);
        } else {
            element.putNull("precision");
            element.putNull("scale");
        }
        element.putNull("length");
        element.putNull("byteLength");
        element.putNull("collation");
    }

    /**
     * An interval column's precision as the driver reads it: the leading field's digits, plus sixteen times the
     * fraction digits of a type ending in SECOND - {@code INTERVAL DAY(9) TO SECOND(9)} is 153,
     * {@code INTERVAL HOUR(4)} 4.
     *
     * @param type an interval type
     * @return the precision
     */
    static int intervalPrecision(final DataType type) {
        if (type instanceof IntervalDayTimeType) {
            final IntervalDayTimeType dayTime = (IntervalDayTimeType) type;
            return dayTime.getFractionalPrecision() << 4 | dayTime.getLeadingPrecision();
        }
        return ((IntervalYearMonthType) type).getLeadingPrecision();
    }

    /**
     * An interval column's scale as the driver reads it: the code of the fields it spans.
     *
     * @param type an interval type
     * @return the scale
     */
    static int intervalScale(final DataType type) {
        return type instanceof IntervalDayTimeType ? ((IntervalDayTimeType) type).getQualifier().driverScale()
            : ((IntervalYearMonthType) type).getQualifier().driverScale();
    }

    /** A cell as the driver reads it, or null for NULL. */
    private static String cellOf(final Object cell, final Object wire, final String kind, final DataType type) {
        if (cell == null || wire == null) {
            return null;
        }
        switch (kind) {
            case "fixed":
                return fixedText(cell, ((NumericType) type).getScale(), wire);
            case "real":
                // A FLOAT's JSON cell is its SQL text, TO_VARCHAR's, which is what the account's JSON results carry:
                // 0.1 + 0.2 reads as 0.3, SQRT(2) as 1.414213562 (live-verified).
                return cell instanceof Number ? SharedFunctionHelpers.floatText(((Number) cell).doubleValue())
                    : wire.toString();
            case "boolean":
                return Boolean.TRUE.equals(cell) || "true".equalsIgnoreCase(cell.toString()) ? "1" : "0";
            case "date":
                return cell instanceof LocalDate ? String.valueOf(((LocalDate) cell).toEpochDay()) : wire.toString();
            case "time":
                return cell instanceof LocalTime ? secondsText(((LocalTime) cell).toSecondOfDay(),
                    ((LocalTime) cell).getNano(), scaleOf(type)) : wire.toString();
            case "timestamp_ntz":
            case "timestamp_ltz":
            case "timestamp_tz":
                return timestampText(cell, kind, scaleOf(type), wire);
            default:
                return textOf(cell, wire, kind);
        }
    }

    /**
     * A cell the driver reads as text, as the wire writes it, a decimal a semi-structured column holds with its digits
     * in place - {@code 0.0000001}, never BigDecimal's {@code 1E-7} - as the account's text has them.
     *
     * @param cell the cell, not null
     * @param wire the cell as the wire writes it, not null
     * @param kind the column's kind
     * @return its text
     */
    static String textOf(final Object cell, final Object wire, final String kind) {
        final boolean semiStructured = "variant".equals(kind) || "object".equals(kind) || "array".equals(kind);
        return semiStructured && cell instanceof BigDecimal ? ((BigDecimal) cell).toPlainString() : wire.toString();
    }

    private static int scaleOf(final DataType type) {
        return type instanceof DateTimeType ? ((DateTimeType) type).getPrecision() : 9;
    }

    private static String fixedText(final Object cell, final int scale, final Object wire) {
        final BigDecimal number;
        if (cell instanceof BigDecimal) {
            number = (BigDecimal) cell;
        } else if (cell instanceof Number) {
            number = new BigDecimal(cell.toString());
        } else {
            return wire.toString();
        }
        return scale > 0 && number.scale() < scale ? number.setScale(scale, RoundingMode.UNNECESSARY).toPlainString()
            : number.toPlainString();
    }

    private static String timestampText(final Object cell, final String kind, final int scale, final Object wire) {
        final Instant moment;
        int offsetMinutes = 0;
        if (cell instanceof LocalDateTime) {
            moment = ((LocalDateTime) cell).toInstant(ZoneOffset.UTC);
        } else if (cell instanceof OffsetDateTime) {
            moment = ((OffsetDateTime) cell).toInstant();
            offsetMinutes = ((OffsetDateTime) cell).getOffset().getTotalSeconds() / 60;
        } else if (cell instanceof ZonedDateTime) {
            moment = ((ZonedDateTime) cell).toInstant();
            offsetMinutes = ((ZonedDateTime) cell).getOffset().getTotalSeconds() / 60;
        } else if (cell instanceof Instant) {
            moment = (Instant) cell;
        } else {
            return wire.toString();
        }
        final String seconds = secondsText(moment.getEpochSecond(), moment.getNano(), scale);
        return "timestamp_tz".equals(kind) ? seconds + " " + (offsetMinutes + ZONE_INDEX_BASE) : seconds;
    }

    /**
     * The seconds as a decimal of {@code scale} fraction digits, {@code 123.456}, a moment before the epoch negative as
     * a whole ({@code -1.500}); no point where the scale is 0.
     */
    private static String secondsText(final long seconds, final int nanos, final int scale) {
        return BigDecimal.valueOf(seconds).add(BigDecimal.valueOf(nanos, 9))
            .setScale(Math.max(0, Math.min(scale, 9)), RoundingMode.FLOOR).toPlainString();
    }
}
