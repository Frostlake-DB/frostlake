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

package dev.frostlake.values;

import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * How two VARIANT values order and compare, live-verified member by member.
 *
 * <p>The KIND decides first: BOOLEAN &lt; NUMBER &lt; TIMESTAMP &lt; TIME &lt; DATE &lt; STRING &lt;
 * BINARY &lt; OBJECT &lt; ARRAY &lt; JSON null (with an array's {@code undefined} hole after the null, so
 * {@code [1,undefined]} and {@code [1,null]} stay unequal). A DATE, TIME, timestamp or BINARY held in a
 * variant is its own kind and never equals the variant STRING of its text: {@code
 * TO_VARIANT('2020-01-01'::DATE) = PARSE_JSON('"2020-01-01"')} is FALSE, and a DATE ranks below every
 * string whatever the text. Within a kind: {@code false} before {@code true}; numbers by VALUE whatever their
 * notation ({@code 10} after {@code 9}, {@code 1} equal to {@code 1.0} and {@code 1e0}), with
 * {@code -Infinity} below every finite number and {@code NaN} above {@code Infinity}; strings by their
 * code points ({@code "10"} before {@code "9"}, {@code "Z"} before {@code "a"}); arrays element by
 * element in this same order, a prefix first ({@code []} &lt; {@code [0,5]} &lt; {@code [1]} &lt;
 * {@code [1,2]} &lt; {@code ["a"]}); objects member by member over their keys in sorted order, where at
 * each position the LARGER key sorts first and equal keys compare their values, a prefix first
 * ({@code {}} &lt; {@code {"b":0}} &lt; {@code {"a":0}} &lt; {@code {"a":0,"c":0}} &lt;
 * {@code {"a":0,"b":0}} &lt; {@code {"a":0,"b":1}} &lt; {@code {"a":1,"b":0}}); a JSON null equals a
 * JSON null. Equality is this comparison answering zero, so {@code {"a":1,"b":2}} equals
 * {@code {"b":2,"a":1}} and {@code [1]} equals {@code [1.0]}, while a variant STRING never equals the
 * number or object it spells.
 *
 * <p>A DOUBLE beside any other number but a DECFLOAT compares as a double, the exact side converted, so
 * {@code 12345678901234567890} equals the {@code 1.2345678901234567e19} it rounds to and
 * {@code 9007199254740993} equals {@code 9007199254740992e0}, while two exact numbers keep every digit
 * ({@code 9007199254740993} and {@code 9007199254740992} stay apart). That comparison is not transitive, so
 * a SORT orders by {@link #sortOrder} instead: by the double first, then by the exact value. The
 * {@link #comparisonKey} that keys DISTINCT, GROUP BY and the set operations stays exact (live-verified:
 * they keep the integer and the double apart where {@code =} finds them equal).
 */
public final class VariantOrder {

    private static final int BOOLEAN = 0;
    private static final int NUMBER = 1;
    private static final int TIMESTAMP = 2;
    private static final int TIME = 3;
    private static final int DATE = 4;
    private static final int STRING = 5;
    private static final int BINARY = 6;
    private static final int OBJECT = 7;
    private static final int ARRAY = 8;
    private static final int NULL = 9;
    private static final int UNDEFINED = 10;

    private VariantOrder() {
    }

    /**
     * Orders two members.
     *
     * @param left one member, null read as a JSON null
     * @param right the other
     * @return negative, zero or positive
     */
    public static int compare(final JsonNode left, final JsonNode right) {
        return compare(left, right, false);
    }

    /**
     * Orders two members for a sort: as {@link #compare}, except that numbers order by their double first
     * and their exact value second, which is a total order where the comparison's double reading is not.
     *
     * @param left one member, null read as a JSON null
     * @param right the other
     * @return negative, zero or positive
     */
    public static int sortOrder(final JsonNode left, final JsonNode right) {
        return compare(left, right, true);
    }

    private static int compare(final JsonNode left, final JsonNode right, final boolean sorting) {
        final int leftKind = kind(left);
        final int rightKind = kind(right);
        if (leftKind != rightKind) {
            return Integer.compare(leftKind, rightKind);
        }
        switch (leftKind) {
            case BOOLEAN:
                return Boolean.compare(left.booleanValue(), right.booleanValue());
            case NUMBER:
                // A DECFLOAT ranks among the numbers by its value and never equals a NUMBER of the same value
                // (live-verified: TO_VARIANT(CAST(1.5 AS DECFLOAT)) = TO_VARIANT(1.5) is FALSE), so the tie is broken.
                final int byValue = sorting ? sortNumbers(left, right) : compareNumbers(left, right);
                return byValue != 0 ? byValue
                    : Boolean.compare(DecfloatTextNode.holds(left), DecfloatTextNode.holds(right));
            case STRING:
                // A UUID never equals the string of its text, so the tie between the two is broken.
                final int byText = left.asText().compareTo(right.asText());
                return byText != 0 ? byText
                    : Boolean.compare(UuidTextNode.holds(left), UuidTextNode.holds(right));
            case TIMESTAMP:
                return compareTimestamps(typedOf(left), typedOf(right));
            case TIME:
                return ((LocalTime) typedOf(left))
                    .compareTo((LocalTime) typedOf(right));
            case DATE:
                return ((LocalDate) typedOf(left))
                    .compareTo((LocalDate) typedOf(right));
            case BINARY:
                return ((BinaryValue) typedOf(left))
                    .compareTo((BinaryValue) typedOf(right));
            case OBJECT:
                return compareObjects(left, right, sorting);
            case ARRAY:
                return compareArrays(left, right, sorting);
            default:
                return 0;
        }
    }

    /**
     * Two timestamps: a TIMESTAMP_TZ and a TIMESTAMP_LTZ by the instant they name, so the two equal each other
     * at the same instant; a TIMESTAMP_NTZ beside another NTZ by its wall clock, and beside a zoned one never
     * equal and after it.
     */
    private static int compareTimestamps(final Object left, final Object right) {
        final Instant leftInstant = instantOf(left);
        final Instant rightInstant = instantOf(right);
        if (leftInstant != null && rightInstant != null) {
            return leftInstant.compareTo(rightInstant);
        }
        if (leftInstant != null || rightInstant != null) {
            return leftInstant != null ? -1 : 1;
        }
        return ((LocalDateTime) left).compareTo((LocalDateTime) right);
    }

    /** The instant a zoned timestamp names, or null for a TIMESTAMP_NTZ. */
    private static Instant instantOf(final Object timestamp) {
        if (timestamp instanceof ZonedDateTime) {
            return ((ZonedDateTime) timestamp).toInstant();
        }
        return timestamp instanceof OffsetDateTime ? ((OffsetDateTime) timestamp).toInstant() : null;
    }

    /**
     * A text that is the same for two members exactly when {@link #compare} answers zero — the key
     * equality and hashing use, so a DISTINCT, a GROUP BY or a set operation sees {@code 1} and
     * {@code 1.0} as one value and {@code {"a":1,"b":2}} and {@code {"b":2,"a":1}} as one object.
     *
     * @param node the member
     * @return its key
     */
    public static String comparisonKey(final JsonNode node) {
        final StringBuilder key = new StringBuilder();
        appendKey(key, node);
        return key.toString();
    }

    private static void appendKey(final StringBuilder key, final JsonNode node) {
        switch (kind(node)) {
            case BOOLEAN:
                key.append(node.booleanValue() ? "true" : "false");
                return;
            case NUMBER:
                if (DecfloatTextNode.holds(node)) {
                    key.append('D').append(DecfloatTextNode.valueOf(node).toPlainString());
                } else if (isFinite(node)) {
                    key.append('N').append(node.decimalValue().stripTrailingZeros().toPlainString());
                } else {
                    key.append('N').append(node.doubleValue());
                }
                return;
            case TIMESTAMP:
                final Object timestamp = typedOf(node);
                final Instant instant = instantOf(timestamp);
                key.append(instant != null ? "TZ" : "TN").append(instant != null ? instant : timestamp);
                return;
            case TIME:
                key.append('H').append(typedOf(node));
                return;
            case DATE:
                key.append('A').append(typedOf(node));
                return;
            case BINARY:
                key.append('B').append(((BinaryValue) typedOf(node)).toHex());
                return;
            case STRING:
                final String text = node.asText();
                // A UUID never equals the string of the same text (live-verified), so it keys apart.
                key.append(UuidTextNode.holds(node) ? 'U' : 'S').append(text.length()).append(':').append(text);
                return;
            case OBJECT:
                key.append('{');
                for (final String name : sortedKeys(node)) {
                    key.append(name.length()).append(':').append(name).append('=');
                    appendKey(key, node.get(name));
                    key.append(',');
                }
                key.append('}');
                return;
            case ARRAY:
                key.append('[');
                for (int i = 0; i < node.size(); i++) {
                    appendKey(key, node.get(i));
                    key.append(',');
                }
                key.append(']');
                return;
            case NULL:
                key.append("null");
                return;
            default:
                key.append("undefined");
        }
    }

    private static int kind(final JsonNode node) {
        // The hole is read before the null it is modelled on, or it would rank as one.
        if (VariantUndefined.isUndefined(node)) {
            return UNDEFINED;
        }
        if (node == null || node.isNull()) {
            return NULL;
        }
        if (node.isBoolean()) {
            return BOOLEAN;
        }
        if (node.isNumber() || DecfloatTextNode.holds(node)) {
            return NUMBER;
        }
        if (node.isObject()) {
            return OBJECT;
        }
        if (node.isArray()) {
            return ARRAY;
        }
        return typedKind(typedOf(node));
    }

    /**
     * The typed value a member carries, else null — read without {@link TypedScalarNode#typedValueOf}, whose
     * rendering bookkeeping a comparison has no use for.
     */
    private static Object typedOf(final JsonNode node) {
        return node instanceof TypedScalarNode ? ((TypedScalarNode) node).typedValue() : null;
    }

    /** The kind of a typed scalar member by its value; a plain string, or a value of no listed type, is a STRING. */
    private static int typedKind(final Object typed) {
        if (typed instanceof LocalDate) {
            return DATE;
        }
        if (typed instanceof LocalTime) {
            return TIME;
        }
        if (typed instanceof LocalDateTime || typed instanceof OffsetDateTime || typed instanceof ZonedDateTime) {
            return TIMESTAMP;
        }
        return typed instanceof BinaryValue ? BINARY : STRING;
    }

    private static boolean isFinite(final JsonNode node) {
        return DecfloatTextNode.holds(node) || !(node.isDouble() || node.isFloat())
            || Double.isFinite(node.doubleValue());
    }

    /** A numeric member's value: a DECFLOAT's own, any other number's as its node reads it. */
    private static BigDecimal decimalOf(final JsonNode node) {
        return DecfloatTextNode.holds(node) ? DecfloatTextNode.valueOf(node) : node.decimalValue();
    }

    private static double doubleOf(final JsonNode node) {
        return DecfloatTextNode.holds(node) ? DecfloatTextNode.valueOf(node).doubleValue() : node.doubleValue();
    }

    private static int compareNumbers(final JsonNode left, final JsonNode right) {
        if (isDouble(left) || isDouble(right)) {
            return ApproximateValues.compare(doubleOf(left), doubleOf(right));
        }
        if (isFinite(left) && isFinite(right)) {
            return decimalOf(left).compareTo(decimalOf(right));
        }
        return Double.compare(doubleOf(left), doubleOf(right));
    }

    /** The numbers' sort order: by the double each converts to, a tie broken by the exact value. */
    private static int sortNumbers(final JsonNode left, final JsonNode right) {
        final int byDouble = ApproximateValues.compare(doubleOf(left), doubleOf(right));
        if (byDouble != 0 || !isFinite(left) || !isFinite(right)) {
            return byDouble;
        }
        return decimalOf(left).compareTo(decimalOf(right));
    }

    /** Whether a member is a DOUBLE: one that compares as a double beside any number but a DECFLOAT. */
    private static boolean isDouble(final JsonNode node) {
        return node.isDouble() || node.isFloat();
    }

    private static int compareArrays(final JsonNode left, final JsonNode right, final boolean sorting) {
        final int shared = Math.min(left.size(), right.size());
        for (int i = 0; i < shared; i++) {
            final int element = compare(left.get(i), right.get(i), sorting);
            if (element != 0) {
                return element;
            }
        }
        return Integer.compare(left.size(), right.size());
    }

    private static int compareObjects(final JsonNode left, final JsonNode right, final boolean sorting) {
        final List<String> leftKeys = sortedKeys(left);
        final List<String> rightKeys = sortedKeys(right);
        final int shared = Math.min(leftKeys.size(), rightKeys.size());
        for (int i = 0; i < shared; i++) {
            // The larger key sorts FIRST at a position (live-verified: {"c":0} < {"b":0} < {"a":0}).
            final int byKey = rightKeys.get(i).compareTo(leftKeys.get(i));
            if (byKey != 0) {
                return byKey;
            }
            final int byValue = compare(left.get(leftKeys.get(i)), right.get(rightKeys.get(i)), sorting);
            if (byValue != 0) {
                return byValue;
            }
        }
        return Integer.compare(leftKeys.size(), rightKeys.size());
    }

    private static List<String> sortedKeys(final JsonNode object) {
        final List<String> keys = new ArrayList<>();
        for (final String name : object.propertyNames()) {
            keys.add(name);
        }
        Collections.sort(keys);
        return keys;
    }
}
