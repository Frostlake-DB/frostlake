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

import dev.frostlake.executor.expressions.IntervalCells;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.IntervalDayTimeType;
import dev.frostlake.types.IntervalYearMonthType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.VectorElementType;
import dev.frostlake.types.VectorType;
import dev.frostlake.values.VectorValue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * A result set's rows as an Arrow stream, the {@code rowsetBase64} of an Arrow result the Snowflake driver reads as it
 * reads the account's. Each column is the vector the driver's reader takes for its type, its field's metadata
 * carrying the type's {@code logicalType}, {@code precision} and {@code scale}:
 *
 * <ul>
 *   <li>a fixed-point number as 64-bit integers holding the value times ten to its scale, or as 128-bit decimals
 *       where one does not fit;</li>
 *   <li>a FLOAT as doubles, a BOOLEAN as bits, a DATE as days since the epoch, a TIME as the time of day in units of
 *       its fraction digits;</li>
 *   <li>a TIMESTAMP as a struct of its seconds since the epoch and its nanoseconds - a TIMESTAMP_NTZ the wall clock
 *       those name in UTC, the others the moment - a TIMESTAMP_TZ with its offset in minutes plus 1440 beside;</li>
 *   <li>a DECFLOAT as a struct of its significand's bytes and its exponent;</li>
 *   <li>a VECTOR as a fixed-size list of its elements, 32-bit floats or 32-bit integers;</li>
 *   <li>an interval as its months or nanoseconds, in the width its type is stored in: 16-, 32- or 64-bit integers, or
 *       128-bit decimals for a day-time interval wider than 64 bits;</li>
 *   <li>a BINARY as its bytes, and every other column as the text a JSON result writes for it.</li>
 * </ul>
 * A result holding a column of a kind this does not write - a geospatial value - is answered as JSON.
 */
final class SnowflakeArrowRowset {

    private static final int ZONE_INDEX_BASE = 1440;
    private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private SnowflakeArrowRowset() {
    }

    /**
     * Whether every column of a result has an Arrow form here.
     *
     * @param kinds the columns' kinds, as the rowtype names them
     * @return true where the result can be answered as Arrow
     */
    static boolean encodes(final String[] kinds) {
        for (final String kind : kinds) {
            if ("geography".equals(kind) || "geometry".equals(kind)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The rows of a result as an Arrow stream in base64, or the empty text for a result of no rows.
     *
     * @param rs       the result, read from its first row
     * @param kinds    the columns' kinds
     * @param rendered the result as the engine's wire writes it, for the cells written as text
     * @return the stream
     */
    static String rows(final ResultSet rs, final String[] kinds, final ResultSetData rendered) {
        final List<ResultSetColumn> columns = rs.getColumns();
        final int count = rendered.getRows().size();
        if (count == 0) {
            return "";
        }
        final List<ArrowField> fields = new ArrayList<>();
        for (int c = 0; c < columns.size(); c++) {
            fields.add(fieldOf(columns.get(c), kinds[c], rs, c, rendered));
        }
        return Base64.getEncoder().encodeToString(ArrowStream.of(fields, count));
    }

    private static ArrowField fieldOf(final ResultSetColumn column, final String kind, final ResultSet rs,
                                      final int c, final ResultSetData rendered) {
        final DataType type = column.getDataType();
        final String name = column.getName();
        final List<Object> cells = new ArrayList<>();
        final List<Object> wires = new ArrayList<>();
        rs.reset();
        int row = 0;
        while (rs.next()) {
            final Object wire = rendered.getRows().get(row).get(c);
            cells.add(wire == null ? null : rs.getValue(c));
            wires.add(wire);
            row++;
        }
        switch (kind) {
            case "fixed":
                return fixed(name, (NumericType) type, cells, wires);
            case "real":
                return real(name, cells, wires);
            case "boolean":
                return logical(name, cells);
            case "date":
                return date(name, cells, wires);
            case "time":
                return time(name, scaleOf(type), cells, wires);
            case "timestamp_ntz":
            case "timestamp_ltz":
            case "timestamp_tz":
                return timestamp(name, kind, scaleOf(type), cells);
            case "binary":
                return binary(name, cells, wires);
            case "decfloat":
                return decfloat(name, cells, wires);
            case "vector":
                return vector(name, (VectorType) type, cells);
            case "interval_year_month":
            case "interval_day_time":
                return interval(name, type, kind, cells);
            default:
                return text(name, kind, cells, wires);
        }
    }

    private static ArrowField typed(final ArrowField field, final String logicalType, final int precision,
                                    final int scale) {
        return field.meta("logicalType", logicalType).meta("precision", String.valueOf(precision))
            .meta("scale", String.valueOf(scale));
    }

    private static ArrowField fixed(final String name, final NumericType type, final List<Object> cells,
                                    final List<Object> wires) {
        final int scale = type.getScale();
        final List<BigInteger> unscaled = new ArrayList<>();
        boolean fitsLong = true;
        for (int i = 0; i < cells.size(); i++) {
            final BigDecimal number = numberOf(cells.get(i), wires.get(i));
            final BigInteger value = number == null ? null
                : number.setScale(scale, RoundingMode.HALF_UP).unscaledValue();
            fitsLong &= value == null || value.compareTo(LONG_MIN) >= 0 && value.compareTo(LONG_MAX) <= 0;
            unscaled.add(value);
        }
        final ArrowField field = fitsLong ? ArrowField.integer(name, 64) : ArrowField.decimal(name, 38, scale);
        typed(field, "FIXED", type.getPrecision(), scale);
        for (final BigInteger value : unscaled) {
            if (value == null) {
                field.addNull();
            } else if (fitsLong) {
                field.addLong(value.longValue());
            } else {
                field.addDecimal(value.toByteArray());
            }
        }
        return field;
    }

    private static BigDecimal numberOf(final Object cell, final Object wire) {
        if (cell == null) {
            return null;
        }
        if (cell instanceof BigDecimal) {
            return (BigDecimal) cell;
        }
        return new BigDecimal((cell instanceof Number ? cell : wire).toString());
    }

    private static ArrowField real(final String name, final List<Object> cells, final List<Object> wires) {
        final ArrowField field = typed(ArrowField.float64(name), "REAL", 0, 0);
        for (int i = 0; i < cells.size(); i++) {
            final Object cell = cells.get(i);
            if (cell == null) {
                field.addNull();
            } else {
                field.addDouble(cell instanceof Number ? ((Number) cell).doubleValue()
                    : Double.parseDouble(wires.get(i).toString()));
            }
        }
        return field;
    }

    private static ArrowField logical(final String name, final List<Object> cells) {
        final ArrowField field = typed(ArrowField.bool(name), "BOOLEAN", 0, 0);
        for (final Object cell : cells) {
            if (cell == null) {
                field.addNull();
            } else {
                field.addBoolean(Boolean.TRUE.equals(cell) || "true".equalsIgnoreCase(cell.toString()));
            }
        }
        return field;
    }

    private static ArrowField date(final String name, final List<Object> cells, final List<Object> wires) {
        final ArrowField field = typed(ArrowField.dateDays(name), "DATE", 0, 0);
        for (int i = 0; i < cells.size(); i++) {
            final Object cell = cells.get(i);
            if (cell == null) {
                field.addNull();
            } else {
                final LocalDate day = cell instanceof LocalDate ? (LocalDate) cell
                    : LocalDate.parse(wires.get(i).toString());
                field.addLong(day.toEpochDay());
            }
        }
        return field;
    }

    private static ArrowField time(final String name, final int scale, final List<Object> cells,
                                   final List<Object> wires) {
        final ArrowField field = typed(ArrowField.integer(name, 64), "TIME", 0, scale);
        final long unit = pow10(9 - scale);
        for (int i = 0; i < cells.size(); i++) {
            final Object cell = cells.get(i);
            if (cell == null) {
                field.addNull();
            } else {
                final LocalTime clock = cell instanceof LocalTime ? (LocalTime) cell
                    : LocalTime.parse(wires.get(i).toString());
                field.addLong(clock.toNanoOfDay() / unit);
            }
        }
        return field;
    }

    private static ArrowField timestamp(final String name, final String kind, final int scale,
                                        final List<Object> cells) {
        final ArrowField field = typed(ArrowField.struct(name), kind.toUpperCase(Locale.ROOT), 0, scale);
        final ArrowField epochs = field.child(ArrowField.integer("epoch", 64));
        final ArrowField fractions = field.child(ArrowField.integer("fraction", 32));
        final ArrowField zones = "timestamp_tz".equals(kind) ? field.child(ArrowField.integer("timezone", 32)) : null;
        final long unit = pow10(9 - scale);
        for (final Object cell : cells) {
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
                moment = null;
            }
            if (moment == null) {
                field.addNull();
                continue;
            }
            field.addPresent();
            epochs.addLong(moment.getEpochSecond());
            fractions.addLong(moment.getNano() / unit * unit);
            if (zones != null) {
                zones.addLong(offsetMinutes + ZONE_INDEX_BASE);
            }
        }
        return field;
    }

    private static ArrowField binary(final String name, final List<Object> cells, final List<Object> wires) {
        final ArrowField field = typed(ArrowField.binary(name), "BINARY", 0, 0);
        for (int i = 0; i < cells.size(); i++) {
            final Object cell = cells.get(i);
            if (cell == null) {
                field.addNull();
            } else {
                field.addBytes(cell instanceof byte[] ? (byte[]) cell : hexBytes(wires.get(i).toString()));
            }
        }
        return field;
    }

    private static ArrowField decfloat(final String name, final List<Object> cells, final List<Object> wires) {
        final ArrowField field = typed(ArrowField.struct(name), "DECFLOAT", 38, 0);
        final ArrowField significands = field.child(ArrowField.binary("significand"));
        final ArrowField exponents = field.child(ArrowField.integer("exponent", 16));
        for (int i = 0; i < cells.size(); i++) {
            final BigDecimal number = numberOf(cells.get(i), wires.get(i));
            if (number == null) {
                field.addNull();
                continue;
            }
            field.addPresent();
            significands.addBytes(number.unscaledValue().toByteArray());
            exponents.addLong(-number.scale());
        }
        return field;
    }

    private static ArrowField vector(final String name, final VectorType type, final List<Object> cells) {
        final ArrowField field = typed(ArrowField.fixedSizeList(name, type.getDimension()), "VECTOR", 38, 0);
        final boolean whole = type.getElementType() == VectorElementType.INT;
        final ArrowField elements = field.child(whole ? typed(ArrowField.integer("element", 32), "FIXED", 38, 0)
            : typed(ArrowField.float32("element"), "REAL", 38, 0));
        for (final Object cell : cells) {
            if (cell == null) {
                field.addNull();
                continue;
            }
            final VectorValue vector = cell instanceof VectorValue ? (VectorValue) cell : VectorValue.cast(cell, type);
            field.addPresent();
            for (int i = 0; i < vector.dimension(); i++) {
                if (whole) {
                    elements.addLong((int) vector.element(i));
                } else {
                    elements.addFloat((float) vector.element(i));
                }
            }
        }
        return field;
    }

    private static ArrowField interval(final String name, final DataType type, final String kind,
                                       final List<Object> cells) {
        final String storage = type instanceof IntervalDayTimeType ? ((IntervalDayTimeType) type).storageTag()
            : ((IntervalYearMonthType) type).storageTag();
        final boolean wide = "SB16".equals(storage);
        final ArrowField field = wide ? ArrowField.decimal(name, 38, 0)
            : ArrowField.integer(name, "SB2".equals(storage) ? 16 : "SB4".equals(storage) ? 32 : 64);
        typed(field, kind.toUpperCase(Locale.ROOT), SnowflakeRowset.intervalPrecision(type),
            SnowflakeRowset.intervalScale(type));
        for (final Object cell : cells) {
            if (cell == null) {
                field.addNull();
            } else if (wide) {
                field.addDecimal(IntervalCells.number(cell).toByteArray());
            } else {
                field.addLong(IntervalCells.number(cell).longValue());
            }
        }
        return field;
    }

    private static ArrowField text(final String name, final String kind, final List<Object> cells,
                                   final List<Object> wires) {
        final String logical = "text".equals(kind) ? "TEXT" : kind.toUpperCase(Locale.ROOT);
        final ArrowField field = typed(ArrowField.utf8(name), logical, 0, 0);
        for (int i = 0; i < cells.size(); i++) {
            if (cells.get(i) == null) {
                field.addNull();
            } else {
                field.addText(wires.get(i).toString());
            }
        }
        return field;
    }

    private static int scaleOf(final DataType type) {
        return type instanceof DateTimeType ? Math.max(0, Math.min(((DateTimeType) type).getPrecision(), 9)) : 9;
    }

    private static long pow10(final int exponent) {
        long value = 1;
        for (int i = 0; i < exponent; i++) {
            value *= 10;
        }
        return value;
    }

    private static byte[] hexBytes(final String hex) {
        final byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return bytes;
    }
}
