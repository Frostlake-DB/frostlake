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

import dev.frostlake.ConcurrentDatabaseEngine;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The session parameters a Snowflake driver reads from a login's and a query's answer: the output formats, the zone
 * and the type mapping it renders values by, each the session's own value where it has one and else the account's
 * default, and the client settings that keep the driver on what this server answers - JSON results, no telemetry, no
 * binding through a stage.
 */
final class SnowflakeSessionParameters {

    /** The parameters read from the session, and the default each takes where nothing sets it. */
    private static final String[][] SESSION = {
        {"TIMEZONE", "America/Los_Angeles"},
        {"TIMESTAMP_OUTPUT_FORMAT", "YYYY-MM-DD HH24:MI:SS.FF3 TZHTZM"},
        {"TIMESTAMP_NTZ_OUTPUT_FORMAT", "YYYY-MM-DD HH24:MI:SS.FF3"},
        {"TIMESTAMP_LTZ_OUTPUT_FORMAT", ""},
        {"TIMESTAMP_TZ_OUTPUT_FORMAT", ""},
        {"DATE_OUTPUT_FORMAT", "YYYY-MM-DD"},
        {"TIME_OUTPUT_FORMAT", "HH24:MI:SS"},
        {"BINARY_OUTPUT_FORMAT", "HEX"},
        {"TIMESTAMP_TYPE_MAPPING", "TIMESTAMP_NTZ"},
        {"CLIENT_TIMESTAMP_TYPE_MAPPING", "TIMESTAMP_LTZ"},
        {"JDBC_QUERY_RESULT_FORMAT", "ARROW"},
    };

    /** The client settings this server answers whatever the session sets, so the driver stays on what it serves. */
    private static final Set<String> FIXED = new HashSet<>(Arrays.asList("AUTOCOMMIT",
        "CLIENT_RESULT_CHUNK_SIZE", "CLIENT_PREFETCH_THREADS", "CLIENT_SESSION_KEEP_ALIVE", "CLIENT_TELEMETRY_ENABLED",
        "CLIENT_OUT_OF_BAND_TELEMETRY_ENABLED", "CLIENT_STAGE_ARRAY_BINDING_THRESHOLD", "JDBC_TREAT_DECIMAL_AS_INT",
        "JDBC_USE_SESSION_TIMEZONE", "CLIENT_HONOR_CLIENT_TZ_FOR_TIMESTAMP_NTZ", "CLIENT_DISABLE_INCIDENTS"));

    private SnowflakeSessionParameters() {
    }

    /**
     * The parameters of a session as the driver reads them, each {@code {"name": ..., "value": ...}}.
     *
     * @param engine  the engine whose session it is
     * @param session the session
     * @return the list
     */
    static ArrayNode of(final ConcurrentDatabaseEngine engine, final SessionContext session) {
        final ArrayNode parameters = JsonNodeFactory.instance.arrayNode();
        final Set<String> named = new HashSet<>();
        for (final String[] parameter : SESSION) {
            Object value = null;
            try {
                value = engine.sessionParameter(session, parameter[0]);
            } catch (final RuntimeException unread) {
                value = null;
            }
            add(parameters, parameter[0]).put("value", value == null ? parameter[1] : value.toString());
            named.add(parameter[0]);
        }
        // Whether the driver matches a column label ignoring case, as the session, its user or the account sets it.
        add(parameters, "CLIENT_RESULT_COLUMN_CASE_INSENSITIVE").put("value",
            engine.resultColumnsCaseInsensitive(session));
        named.add("CLIENT_RESULT_COLUMN_CASE_INSENSITIVE");
        // Every other parameter the session has set, as it set it.
        for (final Map.Entry<String, Object> parameter : session.getSettings().setParameters().entrySet()) {
            if (!named.contains(parameter.getKey()) && !FIXED.contains(parameter.getKey())
                    && parameter.getValue() != null) {
                final ObjectNode added = add(parameters, parameter.getKey());
                final Object value = parameter.getValue();
                if (value instanceof Boolean) {
                    added.put("value", ((Boolean) value).booleanValue());
                } else if (value instanceof Number) {
                    added.put("value", ((Number) value).longValue());
                } else {
                    added.put("value", value.toString());
                }
            }
        }
        // The driver starts the connection in the session's autocommit mode.
        add(parameters, "AUTOCOMMIT").put("value", session.isAutoCommit());
        add(parameters, "CLIENT_RESULT_CHUNK_SIZE").put("value", 160);
        add(parameters, "CLIENT_PREFETCH_THREADS").put("value", 4);
        // The session's keep-alive, under which the driver sends its heartbeat.
        final Object keepAlive = session.getSettings().getParameter("CLIENT_SESSION_KEEP_ALIVE");
        add(parameters, "CLIENT_SESSION_KEEP_ALIVE").put("value", keepAlive != null
            && "TRUE".equalsIgnoreCase(keepAlive.toString().trim()));
        add(parameters, "CLIENT_TELEMETRY_ENABLED").put("value", false);
        add(parameters, "CLIENT_OUT_OF_BAND_TELEMETRY_ENABLED").put("value", false);
        add(parameters, "CLIENT_STAGE_ARRAY_BINDING_THRESHOLD").put("value", Integer.MAX_VALUE);
        add(parameters, "JDBC_TREAT_DECIMAL_AS_INT").put("value", true);
        add(parameters, "JDBC_USE_SESSION_TIMEZONE").put("value", true);
        add(parameters, "CLIENT_HONOR_CLIENT_TZ_FOR_TIMESTAMP_NTZ").put("value", true);
        add(parameters, "CLIENT_DISABLE_INCIDENTS").put("value", true);
        return parameters;
    }

    private static ObjectNode add(final ArrayNode parameters, final String name) {
        final ObjectNode parameter = parameters.addObject();
        parameter.put("name", name);
        return parameter;
    }
}
