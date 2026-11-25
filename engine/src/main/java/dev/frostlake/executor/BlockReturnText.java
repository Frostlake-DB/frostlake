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

import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.VariantJsonFormat;
import dev.frostlake.values.VariantValue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A block-bodied SQL function's RETURN of a value that is no constant, a typed name or a cast — a call, arithmetic,
 * a concatenation, an array or object constant, a RETURN inside an IF or a loop — under the type the function
 * declares. The value is first written as text and the text is then read as the declared type (live-verified):
 *
 * <ul>
 *   <li>the text is fixed, whatever the session's output formats: a TIME is {@code 10:00:00.000000000}, a DATE
 *       {@code 2020-01-15}, a TIMESTAMP_NTZ or _LTZ {@code 2020-01-15 10:11:12.500000000}, a TIMESTAMP_TZ that
 *       followed by its offset ({@code +02:00}, a zero offset {@code -00:00}), a BINARY its hex, a number its plain
 *       digits without trailing zeros ({@code 1.50 + 0} is {@code 1.5}), a FLOAT its own text, a BOOLEAN
 *       {@code true}, and a VARIANT, ARRAY or OBJECT its JSON laid out across lines at the session's JSON_INDENT
 *       (a string held in a VARIANT keeps its quotes);</li>
 *   <li>the text is read with a fixed format: a TIME as {@code HH24:MI:SS.FF}, a DATE as {@code YYYY-MM-DD}, a
 *       TIMESTAMP_TZ as {@code YYYY-MM-DD HH24:MI:SS.FF TZH:TZM}, any other timestamp as
 *       {@code YYYY-MM-DD HH24:MI:SS.FF}, a BINARY as hex, and a BOOLEAN or a number as any text is read — so a
 *       text that does not read is that reader's own refusal, {@code Can't parse '10:00:00.000000000' as date with
 *       format 'YYYY-MM-DD'}, and a TIMESTAMP_TZ under RETURNS TIMESTAMP_NTZ is refused for its offset;</li>
 *   <li>a VARCHAR takes the text itself, its length checked against the declared one.</li>
 * </ul>
 *
 * <p>A value already of the declared type (a string under a VARCHAR, a number under a number, a TIME under a TIME,
 * a timestamp under its own flavour) is taken as it is.
 */
public final class BlockReturnText {

    private static final String TIME_FORMAT = "HH24:MI:SS.FF";
    private static final String DATE_FORMAT = "YYYY-MM-DD";
    private static final String TIMESTAMP_FORMAT = "YYYY-MM-DD HH24:MI:SS.FF";
    private static final String TIMESTAMP_TZ_FORMAT = "YYYY-MM-DD HH24:MI:SS.FF TZH:TZM";

    private static final int FULL_SCALE = 9;
    private static final int WHOLE_SECONDS = 8;

    private static final DateTimeFormatter TIME_TEXT = DateTimeFormatter.ofPattern("HH:mm:ss.SSSSSSSSS", Locale.ROOT);
    private static final DateTimeFormatter OFFSET_TEXT = DateTimeFormatter.ofPattern("xxx", Locale.ROOT);

    private BlockReturnText() {
    }

    /**
     * The RETURN's value read through its text as the declared type, before the declared type's own precision,
     * scale and length are applied.
     *
     * @param value    the value the body returned
     * @param declared the function's declared return type
     * @param registry the functions whose readers convert the text
     * @return the value under the declared type
     */
    public static Object read(final Object value, final DataType declared, final FunctionRegistry registry) {
        if (value == null || declared == null) {
            return value;
        }
        if (declared instanceof StringType) {
            return value instanceof String ? value : textOf(value);
        }
        if (declared instanceof BooleanType) {
            return value instanceof Boolean ? value : textOf(value);
        }
        if (declared instanceof NumericType) {
            return value instanceof Number ? value : textOf(value);
        }
        if (declared instanceof BinaryType) {
            return value instanceof BinaryValue ? value : textOf(value);
        }
        final String name = declared.getName() == null ? "" : declared.getName().toUpperCase(Locale.ROOT);
        if ("TIME".equals(name)) {
            return value instanceof LocalTime ? value : converted(registry, "TO_TIME", textOf(value), TIME_FORMAT);
        }
        if ("DATE".equals(name)) {
            return value instanceof LocalDate ? value : converted(registry, "TO_DATE", textOf(value), DATE_FORMAT);
        }
        if ("TIMESTAMP_TZ".equals(name)) {
            return value instanceof ZonedDateTime ? value
                : converted(registry, "TO_TIMESTAMP_TZ", textOf(value), TIMESTAMP_TZ_FORMAT);
        }
        if ("TIMESTAMP_LTZ".equals(name)) {
            return value instanceof OffsetDateTime ? value
                : converted(registry, "TO_TIMESTAMP_LTZ", textOf(value), TIMESTAMP_FORMAT);
        }
        if ("TIMESTAMP_NTZ".equals(name) || "TIMESTAMP".equals(name) || "DATETIME".equals(name)) {
            return value instanceof LocalDateTime ? value
                : converted(registry, "TO_TIMESTAMP_NTZ", textOf(value), TIMESTAMP_FORMAT);
        }
        return value;
    }

    private static Object converted(final FunctionRegistry registry, final String function, final String text,
                                    final String format) {
        final List<Object> args = new ArrayList<Object>();
        args.add(text);
        args.add(format);
        return registry.getFunction(function).evaluate(args);
    }

    /**
     * The text a value is written as before it is read as the declared type.
     *
     * @param value the value, never null
     * @return its text
     */
    static String textOf(final Object value) {
        return textOf(value, FULL_SCALE);
    }

    /**
     * The text a VARCHAR name holds of a CALL's answer, {@code x := (CALL p())}: the fixed text above, whatever the
     * session's output formats, with the answer's type deciding what the type alone tells — a number keeps the
     * scale its type declares ({@code 12.50} of a NUMBER(10,2)) and a time or a timestamp writes as many fractional
     * digits as its type's precision, none at precision 0 (live-verified).
     *
     * @param value the answer, never null
     * @param type  the answer's type, or null where the CALL reports none
     * @return its text
     */
    public static String answerText(final Object value, final DataType type) {
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).toPlainString();
        }
        return textOf(value, type instanceof DateTimeType ? ((DateTimeType) type).getPrecision() : FULL_SCALE);
    }

    private static String textOf(final Object value, final int scale) {
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof BigDecimal) {
            final BigDecimal stripped = ((BigDecimal) value).stripTrailingZeros();
            return (stripped.signum() == 0 ? BigDecimal.ZERO : stripped).toPlainString();
        }
        if (value instanceof Double || value instanceof Float) {
            return SharedFunctionHelpers.floatText(((Number) value).doubleValue());
        }
        if (value instanceof LocalTime) {
            return timeText((LocalTime) value, scale);
        }
        if (value instanceof LocalDate) {
            return dateText((LocalDate) value);
        }
        if (value instanceof LocalDateTime) {
            final LocalDateTime wallClock = (LocalDateTime) value;
            return dateText(wallClock.toLocalDate()) + " " + timeText(wallClock.toLocalTime(), scale);
        }
        if (value instanceof OffsetDateTime) {
            // A TIMESTAMP_LTZ is written as the session's wall clock, with no offset.
            return textOf(((OffsetDateTime) SharedFunctionHelpers.inSessionZone(value)).toLocalDateTime(), scale);
        }
        if (value instanceof ZonedDateTime) {
            final ZonedDateTime written = (ZonedDateTime) value;
            final String offset = written.getOffset().getTotalSeconds() == 0 ? "-00:00"
                : OFFSET_TEXT.format(written);
            return textOf(written.toLocalDateTime(), scale) + " " + offset;
        }
        if (value instanceof BinaryValue) {
            return ((BinaryValue) value).toHex();
        }
        if (value instanceof VariantValue) {
            return VariantJsonFormat.indented(((VariantValue) value).node(), VariantJsonFormat.indent());
        }
        return value.toString();
    }

    /** A time as its digits, with as many fractional digits as the scale asks for and no point at scale 0. */
    private static String timeText(final LocalTime time, final int scale) {
        final String full = TIME_TEXT.format(time);
        return scale <= 0 ? full.substring(0, WHOLE_SECONDS) : full.substring(0, WHOLE_SECONDS + 1 + Math.min(scale,
            FULL_SCALE));
    }

    /** A date as its digits: the year as many as it takes, the month and day two each. */
    private static String dateText(final LocalDate date) {
        return String.format(Locale.ROOT, "%d-%02d-%02d", date.getYear(), date.getMonthValue(), date.getDayOfMonth());
    }
}
