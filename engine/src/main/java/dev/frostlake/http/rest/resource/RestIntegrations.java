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

package dev.frostlake.http.rest.resource;

import dev.frostlake.http.rest.RestCall;
import dev.frostlake.http.rest.RestCreateMode;
import dev.frostlake.http.rest.RestException;
import dev.frostlake.http.rest.RestIdentifier;
import dev.frostlake.http.rest.RestJson;
import dev.frostlake.http.rest.RestResponse;
import dev.frostlake.http.rest.RestRow;
import dev.frostlake.http.rest.RestShow;
import dev.frostlake.http.rest.RestSql;
import dev.frostlake.http.rest.RestTags;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the three integration resources share: the SHOW row and the DESCRIBE properties of one integration,
 * the list and object values DESCRIBE renders, the CREATE head, and the tag endpoints on the INTEGRATION domain.
 */
final class RestIntegrations {

    private RestIntegrations() {
    }

    /** The SHOW row of the integration of that kind and exact name, or {@code 404}. */
    static RestRow row(final RestCall call, final String kindWords, final RestIdentifier name) {
        final List<RestRow> rows = call.sql().showNamed("SHOW " + kindWords + " INTEGRATIONS" + RestShow.likeName(name),
            name);
        if (rows.isEmpty()) {
            throw RestException.missing("Integration", name);
        }
        return rows.get(0);
    }

    /** Every SHOW row of the kind, narrowed by the {@code like} parameter. */
    static List<RestRow> rows(final RestCall call, final String kindWords) {
        return call.sql().show("SHOW " + kindWords + " INTEGRATIONS" + RestShow.like(call));
    }

    /** The integration's DESCRIBE answer as property → value. */
    static Map<String, String> describe(final RestCall call, final String kindWords, final String resolvedName) {
        final Map<String, String> out = new LinkedHashMap<>();
        for (final RestRow row : call.sql().show("DESCRIBE " + kindWords + " INTEGRATION "
                + RestIdentifier.quote(resolvedName))) {
            out.put(row.string("property"), row.string("property_value"));
        }
        return out;
    }

    /** The properties every integration schema shares: name, enabled, comment and created_on. */
    static ObjectNode base(final RestRow row) {
        final ObjectNode node = RestJson.object();
        RestJson.name(node, "name", row, "name");
        RestJson.bool(node, "enabled", row, "enabled");
        RestJson.string(node, "comment", row, "comment");
        RestJson.timestamp(node, "created_on", row, "created_on");
        return node;
    }

    /** A text property; null when the value is absent or empty. */
    static void text(final ObjectNode node, final String property, final String value) {
        RestJson.put(node, property, value == null || value.isEmpty() ? null : value);
    }

    /** A list DESCRIBE shows comma-joined, as a JSON array; null when the value is absent or empty. */
    static void list(final ObjectNode node, final String property, final String value) {
        if (value == null || value.isEmpty()) {
            node.putNull(property);
            return;
        }
        final ArrayNode items = RestJson.array();
        if (!value.isEmpty()) {
            for (final String item : value.split(",", -1)) {
                items.add(item);
            }
        }
        node.set(property, items);
    }

    /**
     * A value DESCRIBE shows as an object, parsed: a JSON object, or the members of a nested property list,
     * {@code {NAME=value, LIST=[a, b]}}; an empty object when it is absent or neither.
     */
    static JsonNode object(final String value) {
        if (value == null || value.isEmpty()) {
            return RestJson.object();
        }
        try {
            final JsonNode parsed = RestJson.mapper().readTree(value);
            return parsed != null && parsed.isObject() ? parsed : RestJson.object();
        } catch (final JacksonException notJson) {
            return members(value);
        }
    }

    /**
     * The members of a nested property list as DESCRIBE shows them, {@code {NAME=value, LIST=[a, b]}}: each a text,
     * a bracketed one an array of texts, and one shown as {@code null} left out.
     */
    private static ObjectNode members(final String value) {
        final ObjectNode out = RestJson.object();
        if (!value.startsWith("{") || !value.endsWith("}")) {
            return out;
        }
        for (final String member : outerItems(value.substring(1, value.length() - 1))) {
            final int equals = member.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            final String text = member.substring(equals + 1);
            if (text.startsWith("[") && text.endsWith("]")) {
                final ArrayNode items = RestJson.array();
                for (final String item : outerItems(text.substring(1, text.length() - 1))) {
                    if (!item.isEmpty()) {
                        items.add(item);
                    }
                }
                out.set(member.substring(0, equals), items);
            } else if (!"null".equals(text)) {
                out.put(member.substring(0, equals), text);
            }
        }
        return out;
    }

    /** The items between the commas that stand outside every bracket, each trimmed. */
    private static List<String> outerItems(final String list) {
        final List<String> items = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < list.length(); i++) {
            final char c = list.charAt(i);
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
            } else if (c == ',' && depth <= 0) {
                items.add(list.substring(start, i).trim());
                start = i + 1;
            }
        }
        items.add(list.substring(start).trim());
        return items;
    }

    /** A JSON object's text member, or null. */
    static String member(final JsonNode object, final String name) {
        final JsonNode value = object.get(name);
        return value == null || value.isNull() ? null : value.isString() ? value.stringValue() : value.toString();
    }

    /** {@code CREATE [OR REPLACE] <kind> INTEGRATION [IF NOT EXISTS] "NAME"} for the call's createMode. */
    static String createHead(final RestCall call, final String kindWords, final RestIdentifier name) {
        final RestCreateMode mode = call.createMode();
        return "CREATE" + mode.orReplace() + " " + kindWords + " INTEGRATION" + mode.ifNotExists() + " " + name.sql();
    }

    /** {@code ('a', 'b')}: a list of string literals. */
    static String literalList(final List<String> values) {
        final StringBuilder out = new StringBuilder("(");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(RestSql.literal(values.get(i)));
        }
        return out.append(')').toString();
    }

    /** The tag endpoints of an integration: ALTER … INTEGRATION SET/UNSET TAG and the INTEGRATION domain. */
    static RestResponse tags(final RestCall call, final String kindWords) {
        final RestIdentifier name = call.identifier("name");
        return RestTags.handle(call, "ALTER " + kindWords + " INTEGRATION", name.sql(), "INTEGRATION",
            RestIdentifier.display(name.name()), "SNOWFLAKE");
    }

    /** The body's name, which the path's must match when a path names the integration too. */
    static RestIdentifier bodyName(final JsonNode body, final RestIdentifier pathName) {
        final RestIdentifier named = RestJson.identifier(body, "name", pathName == null);
        if (pathName != null && named != null && !named.equals(pathName)) {
            throw RestException.badRequest("The body names integration '" + named + "' but the path names '"
                + pathName + "'.");
        }
        return named != null ? named : pathName;
    }
}
