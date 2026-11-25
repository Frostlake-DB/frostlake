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

import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.VariantType;
import dev.frostlake.values.TemporalText;
import dev.frostlake.values.VariantJsonFormat;
import dev.frostlake.values.VariantValue;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import tools.jackson.databind.node.StringNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Locale;

/**
 * A variable's value given by a CALL of a procedure the CALL defines for itself, {@code LET x := (WITH p AS
 * PROCEDURE … CALL p())}. The account calls the procedure as a function of a query, and the value it hands back is
 * a text: a variable declared without a type, or of a text type, takes the procedure's answer as a text - an INT
 * answer 7 is the text 7, a DATE answer reads 2024-01-02 - while a variable of any other type converts the answer's
 * wrapper, {@code {"type":"SCALAR","scalarValue":"7","scalarDataType":"NUMBER","scalarScale":0,"scalarPrecision":38}},
 * and is refused in that conversion's words, {@code Numeric value '{…}' is not recognized}; a text of a length the
 * wrapper exceeds is refused so too, {@code String '{…}' is too long and would be truncated}, and an OBJECT by the
 * cast of the query the call stands for. A CALL naming any other routine there names a function no one defined
 * (all live-verified). Given to a variable declared without a type, the value is compiled with the block, where a
 * CALL naming another routine and an argument that reads a bind are refused before anything runs (see
 * {@link dev.frostlake.executor.commands.ScriptingNameValidator}).
 */
public final class AnonymousCallValues {

    private static final int DEFAULT_PRECISION = 38;
    private static final int FRACTION_DIGITS = 9;

    private AnonymousCallValues() {
    }

    /**
     * The refusal of a call naming another routine than the procedure the call defines, or null when it names that
     * procedure: {@code Unknown function P7.}, under a qualifier {@code Unknown user-defined function S.P.}.
     *
     * @param defined the procedure the call defines
     * @param called  the name the call calls
     * @return the refusal's message, or null
     */
    public static String unknownRoutine(final FrostlakeParser.AnonymousProcedureContext defined,
                                 final FrostlakeParser.QualifiedNameContext called) {
        final String[] parts = ParseTreeText.qualifiedNameParts(called);
        if (parts.length == 1 && parts[0].equals(ParseTreeText.getIdentifier(defined.identifier()))) {
            return null;
        }
        return unknownRoutine(called);
    }

    /**
     * The refusal of a call naming a routine the statement does not define: {@code Unknown function P7.}, under a
     * qualifier {@code Unknown user-defined function S.P.}.
     *
     * @param called the name the call calls
     * @return the refusal's message
     */
    public static String unknownRoutine(final FrostlakeParser.QualifiedNameContext called) {
        final String[] parts = ParseTreeText.qualifiedNameParts(called);
        final StringBuilder spelled = new StringBuilder();
        for (final String part : parts) {
            spelled.append(spelled.length() > 0 ? "." : "").append(SqlIdentifiers.spellCanonical(part));
        }
        return SqlCompilationError.of((parts.length > 1 ? "Unknown user-defined function " : "Unknown function ")
            + spelled + ".");
    }

    /**
     * The statement that runs the call: the text from the procedure's definition to the call's closing parenthesis.
     *
     * @param defined the procedure the call defines
     * @param close   the call's closing parenthesis
     * @return the CALL statement's text
     */
    public static String callText(final FrostlakeParser.AnonymousProcedureContext defined, final Token close) {
        return defined.getStart().getInputStream().getText(
            Interval.of(defined.getStart().getStartIndex(), close.getStopIndex()));
    }

    /**
     * The refusal of the value given to an OBJECT: the cast of the query the call stands for, {@code invalid type
     * [CAST((SELECT P() AS "P" FROM (VALUES (NULL)) DUAL) AS OBJECT)] for parameter 'TO_OBJECT'}.
     *
     * @param called    the name the call calls
     * @param arguments the call's arguments, or null
     * @return the refusal's message
     */
    static String objectRefusal(final FrostlakeParser.QualifiedNameContext called,
                                final FrostlakeParser.CallArgumentsContext arguments) {
        final String name = SqlIdentifiers.spellCanonical(ParseTreeText.qualifiedNameParts(called)[0]);
        final String written = arguments == null ? "" : ParseTreeText.getOriginalText(arguments);
        return SqlCompilationError.of("invalid type [CAST((SELECT " + name + "(" + written + ") AS \"" + name
            + "\" FROM (VALUES (NULL)) DUAL) AS OBJECT)] for parameter 'TO_OBJECT'");
    }

    /**
     * Whether a variable of the type takes the procedure's answer as a text: one without a type, one of a text type
     * the wrapper fits, and a VARIANT or an ARRAY (which the account fails on internally, so the answer is held as
     * the text).
     *
     * @param declared the variable's type, or null
     * @param wrapper  the answer's wrapper
     * @return whether the answer is taken as a text
     */
    static boolean takesText(final DataType declared, final String wrapper) {
        if (declared == null || declared instanceof VariantType || declared instanceof ArrayType) {
            return true;
        }
        return declared instanceof StringType
            && ((StringType) declared).getMaxLength() >= wrapper.codePointCount(0, wrapper.length());
    }

    /**
     * Whether the variable's type is OBJECT, which the value is refused for.
     *
     * @param declared the variable's type, or null
     * @return whether it is an OBJECT
     */
    static boolean isObject(final DataType declared) {
        return declared instanceof ObjectType;
    }

    /**
     * The wrapper the account converts a procedure's answer from: its value as a text in the type's own form - a
     * NUMBER at its scale, a FLOAT as the account prints one, a BOOLEAN 1 or 0, a DATE its days since the epoch, a
     * TIME its seconds since midnight and a timestamp its seconds since the epoch, both with nine fractional digits,
     * a semi-structured value its JSON laid out across lines at the session's JSON_INDENT - with the type's name, its scale and its precision.
     *
     * @param value the procedure's answer
     * @param type  the type of the answer, or null
     * @return the wrapper's JSON text
     */
    public static String wrapper(final Object value, final DataType type) {
        final String typeName = typeName(type);
        int scale = 0;
        int precision = DEFAULT_PRECISION;
        if (type instanceof NumericType && "NUMBER".equals(typeName)) {
            scale = ((NumericType) type).getScale();
            precision = ((NumericType) type).getPrecision();
        } else if (type instanceof DateTimeType && !"DATE".equals(typeName)) {
            scale = FRACTION_DIGITS;
            precision = 0;
        }
        if (value == null) {
            scale = 0;
            precision = 0;
        }
        final String scalar = value == null ? "null" : StringNode.valueOf(valueText(value, typeName, scale)).toString();
        return "{\"type\":\"SCALAR\",\"scalarValue\":" + scalar + ",\"scalarDataType\":\"" + typeName
            + "\",\"scalarScale\":" + scale + ",\"scalarPrecision\":" + precision + "}";
    }

    /**
     * The text a statement hands out for the procedure's answer: a NUMBER at its scale, a FLOAT as the account prints
     * one, a BOOLEAN in capitals, a date or a time as the session prints it, a semi-structured value its JSON laid
     * out across lines.
     *
     * @param value the procedure's answer
     * @param type  the type of the answer, or null
     * @return the text, or null for a NULL answer
     */
    public static String answerText(final Object value, final DataType type) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return ((Boolean) value) ? "TRUE" : "FALSE";
        }
        if (value instanceof VariantValue || value instanceof Double || value instanceof Float
                || value instanceof Number && "NUMBER".equals(typeName(type))) {
            final int scale = type instanceof NumericType ? ((NumericType) type).getScale() : 0;
            return valueText(value, typeName(type), scale);
        }
        if (type instanceof DateTimeType) {
            return TemporalText.render(value, type);
        }
        return value.toString();
    }

    private static String typeName(final DataType type) {
        if (type == null || type instanceof StringType) {
            return "VARCHAR";
        }
        if (type instanceof NumericType) {
            if (NumericType.isDecfloat(type)) {
                return "DECFLOAT";
            }
            return NumericType.isApproximate(type) ? "FLOAT" : "NUMBER";
        }
        if (type instanceof BooleanType) {
            return "BOOLEAN";
        }
        final String name = type.getName().toUpperCase(Locale.ROOT);
        if (type instanceof DateTimeType && ("TIMESTAMP".equals(name) || "DATETIME".equals(name))) {
            return "TIMESTAMP_NTZ";
        }
        return name;
    }

    private static String valueText(final Object value, final String typeName, final int scale) {
        if (value instanceof VariantValue) {
            return VariantJsonFormat.indented(((VariantValue) value).node(), VariantJsonFormat.indent());
        }
        if (value instanceof Boolean) {
            return ((Boolean) value) ? "1" : "0";
        }
        if (value instanceof Double || value instanceof Float) {
            return SharedFunctionHelpers.floatText(((Number) value).doubleValue());
        }
        if (value instanceof Number && "NUMBER".equals(typeName)) {
            final BigDecimal exact = value instanceof BigDecimal ? (BigDecimal) value : new BigDecimal(value.toString());
            return exact.setScale(scale, RoundingMode.HALF_UP).toPlainString();
        }
        if (value instanceof LocalDate) {
            return Long.toString(((LocalDate) value).toEpochDay());
        }
        if (value instanceof LocalTime) {
            final LocalTime time = (LocalTime) value;
            return fractional(time.toSecondOfDay(), time.getNano());
        }
        if (value instanceof LocalDateTime) {
            return instantText(((LocalDateTime) value).toInstant(ZoneOffset.UTC));
        }
        if (value instanceof ZonedDateTime) {
            return instantText(((ZonedDateTime) value).toInstant());
        }
        if (value instanceof OffsetDateTime) {
            return instantText(((OffsetDateTime) value).toInstant());
        }
        if (value instanceof Instant) {
            return instantText((Instant) value);
        }
        return value.toString();
    }

    private static String instantText(final Instant instant) {
        return fractional(instant.getEpochSecond(), instant.getNano());
    }

    private static String fractional(final long seconds, final int nanos) {
        final String digits = Integer.toString(nanos);
        final StringBuilder text = new StringBuilder().append(seconds).append('.');
        for (int i = digits.length(); i < FRACTION_DIGITS; i++) {
            text.append('0');
        }
        return text.append(digits).toString();
    }
}
