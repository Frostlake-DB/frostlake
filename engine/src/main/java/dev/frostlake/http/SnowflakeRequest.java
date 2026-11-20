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

import com.sun.net.httpserver.HttpExchange;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The parts of a request to the Snowflake endpoints that {@link SnowflakeProtocolHandler} reads: its path and query,
 * its headers and its body. It comes from a socket's exchange or is handed over in process (see
 * {@link SnowflakeDirectEndpoint}); the body is read when first asked for, so a failure to read it is answered as any
 * other fault of the request.
 */
final class SnowflakeRequest {

    private final String method;
    private final String path;
    private final String rawQuery;
    private final Map<String, List<String>> headers;
    private final InputStream source;
    private byte[] body;

    private SnowflakeRequest(final String method, final URI uri, final Map<String, List<String>> headers,
                             final InputStream source) {
        this.method = method;
        this.path = uri.getPath() == null || uri.getPath().isEmpty() ? "/" : uri.getPath();
        this.rawQuery = uri.getRawQuery();
        this.headers = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);
        if (headers != null) {
            for (final Map.Entry<String, List<String>> header : headers.entrySet()) {
                if (header.getKey() == null || header.getValue() == null) {
                    continue;
                }
                List<String> values = this.headers.get(header.getKey());
                if (values == null) {
                    values = new ArrayList<String>();
                    this.headers.put(header.getKey(), values);
                }
                values.addAll(header.getValue());
            }
        }
        this.source = source;
    }

    /**
     * A request a socket's exchange carries.
     *
     * @param exchange the exchange
     * @return the request
     */
    static SnowflakeRequest of(final HttpExchange exchange) {
        return new SnowflakeRequest(exchange.getRequestMethod(), exchange.getRequestURI(),
            exchange.getRequestHeaders(), exchange.getRequestBody());
    }

    /**
     * A request handed over in process.
     *
     * @param uri     the request URI; its path and query are read, its scheme and authority are not
     * @param headers the request headers by name, the names matched without regard to case
     * @param body    the request body, null or empty when it has none
     * @return the request
     */
    static SnowflakeRequest of(final URI uri, final Map<String, List<String>> headers, final byte[] body) {
        return new SnowflakeRequest(null, uri, headers,
            new ByteArrayInputStream(body == null ? new byte[0] : body));
    }

    /** The request's method, or null for a request handed over in process, which names none. */
    String method() {
        return method;
    }

    /** The decoded path, {@code /} when the URI has none. */
    String path() {
        return path;
    }

    /** The query as the URI writes it, or null. */
    String rawQuery() {
        return rawQuery;
    }

    /** The first value of a header, or null when the request has none. */
    String header(final String name) {
        final List<String> values = headers.get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    /**
     * The body, read once and at most so far.
     *
     * @param limit the most bytes it may hold, 0 or less for no bound
     * @return the body's bytes, empty when it has none
     * @throws IOException if the body cannot be read, a {@link RequestTooLargeException} where it is larger than
     *                     the bound
     */
    byte[] body(final long limit) throws IOException {
        if (body == null) {
            body = new BoundedRequestStream(source, limit).readAllBytes();
        }
        return body;
    }
}
