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

package dev.frostlake.executor.udf;

import com.snowflake.snowpark_java.types.Variant;
import dev.frostlake.executor.expressions.ValueCaster;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DecimalText;
import dev.frostlake.types.TypeCategory;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.VariantUndefined;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.JsonNode;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAccessor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A routine's arguments as its Java or Scala handler method takes them (live-verified). Each arrives converted to
 * its parameter's SQL type; here it becomes the class the handler declares: a NUMBER an {@code int}, a
 * {@code long}, a {@code short}, their boxes, a {@code BigDecimal} at the declared scale or a {@code BigInteger};
 * a FLOAT a {@code double} or a {@code float}; a BOOLEAN a {@code boolean}; any scalar a {@code String}; a DATE a
 * {@code java.sql.Date} at midnight UTC, a TIME a {@code java.sql.Time} and a TIMESTAMP a
 * {@code java.sql.Timestamp} with its nanoseconds; an ARRAY a {@code String[]}, or an {@code int[]}, a
 * {@code long[]}, a {@code short[]}, a {@code float[]}, a {@code double[]} or a {@code boolean[]} of its elements
 * (see {@link #primitives}), an OBJECT a {@code Map<String, String>} and a BINARY a {@code byte[]}.
 *
 * <p>A NULL reaches a box or an object as {@code null} and is refused for a primitive, and a number its class
 * cannot hold is refused, each naming the function and its handler.
 */
public final class HandlerArguments {

    private HandlerArguments() {
    }

    /**
     * Converts the argument values in place of the handler's parameters after the session. A semi-structured
     * argument handed to a {@code String} is its JSON (see {@link HandlerTexts#json}).
     *
     * @param classes    the handler method's parameter classes, the session's first
     * @param args       the call's values, the session first, each argument as its SQL type carries it
     * @param parameters the routine's parameters
     * @param routine    the routine's name, as the refusals spell it
     * @param handler    the handler as declared, {@code Class.method}
     * @return the values the method is invoked with
     */
    public static Object[] adapt(final Class<?>[] classes, final Object[] args, final List<Parameter> parameters,
                                 final String routine, final String handler) {
        final Object[] adapted = args.clone();
        final String where = " in function " + routine + " with handler " + handler;
        for (int i = 1; i < args.length && i < classes.length; i++) {
            final DataType type = i - 1 < parameters.size() ? parameters.get(i - 1).getDataType() : null;
            adapted[i] = classes[i] == String.class && args[i] != null && type != null
                && type.getCategory() == TypeCategory.SEMI_STRUCTURED ? HandlerTexts.json(args[i])
                : convert(classes[i], args[i], where);
        }
        return adapted;
    }

    /**
     * One value as the class a handler's parameter declares.
     *
     * @param target the parameter's class
     * @param value  the value, as its parameter's SQL type carries it
     * @param where  the end of a refusal's sentence, {@code  in function F with handler H.f}
     * @return the value the method is invoked with
     */
    public static Object convert(final Class<?> target, final Object value, final String where) {
        if (value == null) {
            if (target.isPrimitive()) {
                throw new RuntimeException("Cannot pass a NULL value as input to a Java type of " + target.getName()
                    + where);
            }
            return null;
        }
        if (target == long.class || target == Long.class) {
            return Long.valueOf(whole(value, Long.SIZE, "int64", where).longValueExact());
        }
        if (target == int.class || target == Integer.class) {
            return Integer.valueOf(whole(value, Integer.SIZE, "int32", where).intValueExact());
        }
        if (target == short.class || target == Short.class) {
            return Short.valueOf(whole(value, Short.SIZE, "int16", where).shortValueExact());
        }
        if (target == byte.class || target == Byte.class) {
            return Byte.valueOf(whole(value, Byte.SIZE, "int8", where).byteValueExact());
        }
        if (target == BigDecimal.class && value instanceof Number) {
            return value instanceof BigDecimal ? value : DecimalText.parse(value.toString());
        }
        if (target == BigInteger.class && value instanceof Number) {
            return DecimalText.parse(value.toString()).toBigInteger();
        }
        if ((target == double.class || target == Double.class) && value instanceof Number) {
            return Double.valueOf(((Number) value).doubleValue());
        }
        if ((target == float.class || target == Float.class) && value instanceof Number) {
            return Float.valueOf(((Number) value).floatValue());
        }
        if (target == String.class) {
            return value instanceof String ? value : String.valueOf(ValueCaster.castValue(value, "VARCHAR"));
        }
        if (target == Date.class && value instanceof LocalDate) {
            return new Date(((LocalDate) value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli());
        }
        if (target == Time.class && value instanceof LocalTime) {
            return new Time(((LocalTime) value).toNanoOfDay() / 1_000_000L);
        }
        if (target == Timestamp.class && value instanceof TemporalAccessor) {
            return timestamp((TemporalAccessor) value);
        }
        if (target == String[].class) {
            return strings(value);
        }
        if (target.isArray() && target.getComponentType().isPrimitive() && target != byte[].class
                && target != char[].class) {
            return primitives(target, value, where);
        }
        if (target == Map.class) {
            return map(value);
        }
        if (target == byte[].class && value instanceof BinaryValue) {
            return ((BinaryValue) value).bytes();
        }
        if (target == Variant.class) {
            return SnowparkVariants.ofSql(value, where);
        }
        return value;
    }

    /** A number as a whole one of at most {@code bits}: rounded as the SQL type rounds, refused past the class. */
    private static BigInteger whole(final Object value, final int bits, final String name, final String where) {
        final BigDecimal number = value instanceof BigDecimal ? (BigDecimal) value
            : value instanceof Boolean ? (((Boolean) value).booleanValue() ? BigDecimal.ONE : BigDecimal.ZERO)
            : DecimalText.parse(String.valueOf(value));
        final BigInteger rounded = number.setScale(0, RoundingMode.HALF_UP).toBigInteger();
        if (rounded.bitLength() >= bits) {
            throw new RuntimeException("Number out of representable range: type " + name + ", value "
                + number.toPlainString() + where);
        }
        return rounded;
    }

    /** A timestamp at its wall clock read as UTC, a zoned one at its instant, nanoseconds kept. */
    private static Timestamp timestamp(final TemporalAccessor value) {
        final Instant instant;
        if (value instanceof LocalDateTime) {
            instant = ((LocalDateTime) value).toInstant(ZoneOffset.UTC);
        } else if (value instanceof LocalDate) {
            instant = ((LocalDate) value).atStartOfDay(ZoneOffset.UTC).toInstant();
        } else {
            instant = Instant.from(value);
        }
        final Timestamp timestamp = new Timestamp(instant.getEpochSecond() * 1000L);
        timestamp.setNanos(instant.getNano());
        return timestamp;
    }

    /** An ARRAY's elements as texts (see {@link HandlerTexts#member}). */
    private static Object strings(final Object value) {
        final JsonNode array = json(value);
        if (array == null || !array.isArray()) {
            return value;
        }
        final String[] out = new String[array.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = HandlerTexts.member(array.get(i));
        }
        return out;
    }

    /**
     * An ARRAY's elements as a primitive array, each read as the element class takes it, in order: a NULL element
     * is refused, {@code Argument type int[] cannot have a null element}; for a number class a number with
     * decimals is refused, {@code Failed to cast variant value with non-zero scale to FIXED}, as is an element of
     * another kind, named by its storage type ({@code Failed to cast variant value of type TEXT to REAL}); a whole
     * number past the class is refused as a scalar argument is.
     */
    private static Object primitives(final Class<?> target, final Object value, final String where) {
        final JsonNode array = json(value);
        if (array == null || !array.isArray()) {
            return value;
        }
        final Class<?> element = target.getComponentType();
        final Object out = Array.newInstance(element, array.size());
        for (int i = 0; i < array.size(); i++) {
            final JsonNode node = array.get(i);
            if (node == null || node.isNull() || VariantUndefined.isUndefined(node)) {
                throw new RuntimeException("Argument type " + element.getName() + "[] cannot have a null element"
                    + where);
            }
            if (element == boolean.class) {
                if (!node.isBoolean()) {
                    throw new RuntimeException(notCast(node, "BOOLEAN") + where);
                }
                Array.setBoolean(out, i, node.booleanValue());
            } else if (element == float.class || element == double.class) {
                if (!node.isNumber()) {
                    throw new RuntimeException(notCast(node, "REAL") + where);
                }
                if (element == float.class) {
                    Array.setFloat(out, i, (float) node.doubleValue());
                } else {
                    Array.setDouble(out, i, node.doubleValue());
                }
            } else {
                Array.set(out, i, convert(element, fixed(node, where), where));
            }
        }
        return out;
    }

    /** An element read as a whole number, refused when it is of another kind or has decimals. */
    private static BigDecimal fixed(final JsonNode node, final String where) {
        if (!node.isIntegralNumber() && !node.isBigDecimal()) {
            throw new RuntimeException(notCast(node, "FIXED") + where);
        }
        final BigDecimal number = node.decimalValue();
        if (number.signum() != 0 && number.stripTrailingZeros().scale() > 0) {
            throw new RuntimeException("Failed to cast variant value with non-zero scale to FIXED" + where);
        }
        return number;
    }

    /** The refusal of an element no cast to {@code storage} takes, naming the element's own storage type. */
    private static String notCast(final JsonNode node, final String storage) {
        final String kind = node.isTextual() ? "TEXT" : node.isBoolean() ? "BOOLEAN" : node.isArray() ? "ARRAY"
            : node.isObject() ? "OBJECT" : node.isIntegralNumber() || node.isBigDecimal() ? "FIXED" : "REAL";
        return "Failed to cast variant value of type " + kind + " to " + storage;
    }

    /** An OBJECT's values as texts (see {@link HandlerTexts#member}), by key, in the object's order. */
    private static Object map(final Object value) {
        final JsonNode object = json(value);
        if (object == null || !object.isObject()) {
            return value;
        }
        final Map<String, String> out = new LinkedHashMap<String, String>();
        for (final Map.Entry<String, JsonNode> entry : object.properties()) {
            out.put(entry.getKey(), HandlerTexts.member(entry.getValue()));
        }
        return out;
    }

    private static JsonNode json(final Object value) {
        if (value instanceof VariantValue) {
            return ((VariantValue) value).node();
        }
        return value instanceof String ? ArrayFunctionHelper.parseNode(value) : null;
    }
}
