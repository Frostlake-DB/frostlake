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

package dev.frostlake.functions.scalar.string;

import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.functions.TextArgumentFunction;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.ObjectType;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.RoundingMode;
import java.util.List;

/**
 * PARSE_URL(url [, permissive]) — splits a URL into an OBJECT with the keys {@code fragment, host, parameters,
 * path, port, query, scheme}. The split is textual and keeps every component as written: nothing is decoded, and a
 * space, a brace, a pipe or a malformed escape is part of the component it stands in (live-verified):
 * <ul>
 *   <li>the scheme is everything before the first colon; no colon is "scheme not specified" and nothing after it
 *       "only scheme is specified";</li>
 *   <li>after {@code //} the authority runs to the first {@code /}, {@code ?} or {@code #}. Its port follows the
 *       first colon, or with a {@code @} the last colon past the first {@code @}, and must be ASCII digits, kept as
 *       text; the host keeps any user part. An empty authority has no host; an empty host is refused by what
 *       stands beside it;</li>
 *   <li>the path is what follows, up to {@code ?} or {@code #}: past the authority's slash, NULL without one;
 *       without an authority it is the rest as written, empty included;</li>
 *   <li>the query runs from the first {@code ?} to the first {@code #}, the fragment from the first {@code #} to
 *       the end; either is empty when its mark ends the text, and NULL without the mark;</li>
 *   <li>{@code parameters} is NULL without a query, else the {@code &}-separated pairs split at their first
 *       {@code =}, a pair without one holding NULL, empty pairs skipped and a repeated key keeping its last
 *       value.</li>
 * </ul>
 * An empty URL is an object of NULLs. A refusal is "Error parsing URL: ..." unless {@code permissive}, a number
 * rounded to a whole one, is non-zero, when it is an object holding the reason under {@code error}. A NULL URL or
 * flag is NULL.
 */
public class ParseUrl extends TextArgumentFunction {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ParseUrl() { super("PARSE_URL", ObjectType.OBJECT); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.size() > 1 && args.get(1) == null) return null;
        final boolean permissive = args.size() > 1
            && SharedFunctionHelpers.numberArgument(args.get(1)).setScale(0, RoundingMode.HALF_UP).signum() != 0;
        try {
            return ArrayFunctionHelper.toCanonicalVariant(parse(SharedFunctionHelpers.textOf(args.get(0))));
        } catch (final IllegalArgumentException e) {
            if (permissive) {
                final ObjectNode err = MAPPER.createObjectNode();
                err.put("error", e.getMessage());
                return ArrayFunctionHelper.toCanonicalVariant(err);
            }
            throw new RuntimeException("Error parsing URL: " + e.getMessage());
        }
    }

    /** The permissive flag is a number: a BOOLEAN there is refused by the argument types (live-verified). */
    @Override
    public SemiStructuredRejection booleanRejection(final int position) {
        return position == 1 ? SemiStructuredRejection.ARGUMENT_TYPES : SemiStructuredRejection.NONE;
    }

    private static ObjectNode parse(final String url) {
        final ObjectNode obj = MAPPER.createObjectNode();
        for (final String key : new String[] {"fragment", "host", "parameters", "path", "port", "query", "scheme"}) {
            obj.putNull(key);
        }
        if (url.isEmpty()) {
            return obj;
        }
        final int colon = url.indexOf(':');
        if (colon < 0) {
            throw new IllegalArgumentException("scheme not specified");
        }
        obj.put("scheme", url.substring(0, colon));
        final String rest = url.substring(colon + 1);
        if (rest.isEmpty()) {
            throw new IllegalArgumentException("only scheme is specified");
        }
        final boolean authority = rest.startsWith("//");
        String tail = rest;
        if (authority) {
            int end = 2;
            while (end < rest.length() && "/?#".indexOf(rest.charAt(end)) < 0) {
                end++;
            }
            readAuthority(rest.substring(2, end), obj);
            tail = rest.substring(end);
        }
        final int hash = tail.indexOf('#');
        if (hash >= 0) {
            obj.put("fragment", tail.substring(hash + 1));
            tail = tail.substring(0, hash);
        }
        final int question = tail.indexOf('?');
        if (question >= 0) {
            final String query = tail.substring(question + 1);
            obj.put("query", query);
            obj.set("parameters", parameters(query));
            tail = tail.substring(0, question);
        }
        if (!authority) {
            obj.put("path", tail);
        } else if (tail.startsWith("/")) {
            obj.put("path", tail.substring(1));
        }
        return obj;
    }

    private static void readAuthority(final String authority, final ObjectNode obj) {
        if (authority.isEmpty()) {
            return;
        }
        final int at = authority.indexOf('@');
        final String hostPort = at < 0 ? authority : authority.substring(at + 1);
        final int colon = at < 0 ? hostPort.indexOf(':') : hostPort.lastIndexOf(':');
        final String host = colon < 0 ? hostPort : hostPort.substring(0, colon);
        final String port = colon < 0 ? null : hostPort.substring(colon + 1);
        if (host.isEmpty()) {
            if (port == null || port.isEmpty() && at >= 0) {
                throw new IllegalArgumentException("host not specified");
            }
            throw new IllegalArgumentException(port.isEmpty()
                ? "neither host nor port specified" : "port specified without a host");
        }
        if (port != null) {
            if (port.isEmpty()) {
                throw new IllegalArgumentException("port not specified");
            }
            for (int i = 0; i < port.length(); i++) {
                if (port.charAt(i) < '0' || port.charAt(i) > '9') {
                    throw new IllegalArgumentException("port must be a number");
                }
            }
            obj.put("port", port);
        }
        obj.put("host", (at < 0 ? "" : authority.substring(0, at + 1)) + host);
    }

    private static ObjectNode parameters(final String query) {
        final ObjectNode parameters = MAPPER.createObjectNode();
        for (final String pair : query.split("&")) {
            if (pair.isEmpty()) continue;
            final int eq = pair.indexOf('=');
            if (eq >= 0) {
                parameters.put(pair.substring(0, eq), pair.substring(eq + 1));
            } else {
                parameters.putNull(pair);
            }
        }
        return parameters;
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 2; }
}
