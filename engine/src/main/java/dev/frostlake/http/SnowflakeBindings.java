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

import dev.frostlake.jdbc.JdbcMarshaling;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The values a Snowflake driver binds with a query, as the values the engine's own bindings take (see
 * {@link JdbcMarshaling#bindPrepared}). Each binding is {@code {"type": ..., "value": ...}} by its 1-based number, the
 * value written as the driver writes it: a number, a text or {@code true}/{@code false} as itself, a DATE as the
 * milliseconds since the epoch of its midnight in UTC, a TIME as the nanoseconds since midnight, a TIMESTAMP as the
 * nanoseconds since the epoch - a TIMESTAMP_NTZ the wall clock those name in UTC, the others the moment - and a
 * BINARY as its hex digits. A batch bound as arrays (see {@link SnowflakeArrayBinds}) holds one such value for each
 * row.
 */
final class SnowflakeBindings {

    private static final long NANOS = 1_000_000_000L;
    private static final long DAY_MILLIS = 86_400_000L;

    private SnowflakeBindings() {
    }

    /**
     * @param bindings the request's {@code bindings}, or a missing node
     * @return the values by 1-based number
     */
    static Map<Integer, Object> of(final JsonNode bindings) {
        final Map<Integer, Object> values = new HashMap<>();
        if (bindings == null || !bindings.isObject()) {
            return values;
        }
        final Iterator<Map.Entry<String, JsonNode>> fields = bindings.properties().iterator();
        while (fields.hasNext()) {
            final Map.Entry<String, JsonNode> field = fields.next();
            values.put(Integer.valueOf(field.getKey()), valueOf(field.getValue()));
        }
        return values;
    }

    /**
     * Whether a request binds a batch as arrays, one value for each row.
     *
     * @param bindings the request's {@code bindings}, or a missing node
     * @return true where a binding's value is an array
     */
    static boolean isArray(final JsonNode bindings) {
        if (bindings == null || !bindings.isObject()) {
            return false;
        }
        for (final Map.Entry<String, JsonNode> field : bindings.properties()) {
            if (field.getValue().path("value").isArray()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The rows of a batch bound as arrays, each row's values by 1-based number.
     *
     * @param bindings the request's {@code bindings}
     * @return the rows, in order
     */
    static List<Map<Integer, Object>> rows(final JsonNode bindings) {
        int count = -1;
        for (final Map.Entry<String, JsonNode> field : bindings.properties()) {
            final JsonNode value = field.getValue().path("value");
            final int size = value.isArray() ? value.size() : 1;
            if (count >= 0 && size != count) {
                throw new IllegalArgumentException("Every array of a batch binding holds one value for each row.");
            }
            count = size;
        }
        final List<Map<Integer, Object>> rows = new ArrayList<>();
        for (int row = 0; row < Math.max(count, 0); row++) {
            final Map<Integer, Object> values = new HashMap<>();
            for (final Map.Entry<String, JsonNode> field : bindings.properties()) {
                final JsonNode binding = field.getValue();
                final JsonNode value = binding.path("value");
                values.put(Integer.valueOf(field.getKey()),
                    valueOf(binding.path("type"), value.isArray() ? value.get(row) : value));
            }
            rows.add(values);
        }
        return rows;
    }

    private static Object valueOf(final JsonNode binding) {
        return valueOf(binding.path("type"), binding.path("value"));
    }

    private static Object valueOf(final JsonNode typeNode, final JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        final String text = value.asString();
        final String type = typeNode.asString("TEXT").toUpperCase(Locale.ROOT);
        switch (type) {
            case "FIXED":
                return new BigDecimal(text);
            case "REAL":
                return Double.valueOf(text);
            case "BOOLEAN":
                return Boolean.valueOf("true".equalsIgnoreCase(text) || "1".equals(text));
            case "DATE":
                return Date.valueOf(LocalDate.ofEpochDay(Math.floorDiv(Long.parseLong(text), DAY_MILLIS)));
            case "TIME":
                return timeOf(LocalTime.ofNanoOfDay(Long.parseLong(text)));
            case "TIMESTAMP_NTZ":
                return Timestamp.valueOf(wallClockOf(new BigDecimal(text)));
            case "TIMESTAMP_LTZ":
            case "TIMESTAMP_TZ":
                return JdbcMarshaling.boundMoment(Timestamp.from(momentOf(new BigDecimal(text))));
            case "BINARY":
                return bytesOf(text);
            default:
                return text;
        }
    }

    /** A time of day as a Time whose moment carries its milliseconds, as the engine's bindings read one. */
    private static Time timeOf(final LocalTime clock) {
        final Time time = Time.valueOf(clock.withNano(0));
        time.setTime(time.getTime() + clock.getNano() / 1_000_000);
        return time;
    }

    private static Instant momentOf(final BigDecimal nanos) {
        final BigDecimal[] split = nanos.divideAndRemainder(BigDecimal.valueOf(NANOS));
        long seconds = split[0].longValue();
        long fraction = split[1].longValue();
        if (fraction < 0) {
            seconds--;
            fraction += NANOS;
        }
        return Instant.ofEpochSecond(seconds, fraction);
    }

    private static LocalDateTime wallClockOf(final BigDecimal nanos) {
        return LocalDateTime.ofInstant(momentOf(nanos), ZoneOffset.UTC);
    }

    private static byte[] bytesOf(final String hex) {
        final byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return bytes;
    }
}
