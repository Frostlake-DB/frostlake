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
package dev.frostlake.sftransport;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.http.SnowflakeDirectEndpoint;
import dev.frostlake.http.SnowflakeDirectResponse;
import net.snowflake.client.api.http.HttpTransport;
import net.snowflake.client.api.http.HttpTransportRequest;
import net.snowflake.client.api.http.HttpTransportResponse;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Carries Snowflake's JDBC driver's requests to an embedded engine's Snowflake endpoints in process, so the driver
 * reaches the engine without a socket.
 *
 * <p>Registered for {@link java.util.ServiceLoader} as {@value #NAME}: with this module on the class path, a
 * connection URL that names it reaches the engine {@code jdbc:frostlake:direct:<name>} connections share, the URL's
 * host naming the engine:
 *
 * <pre>
 *   jdbc:snowflake://mydb/?account=frostlake&amp;httpTransport=frostlake&amp;disablePlatformDetection=true
 * </pre>
 *
 * <p>A transport made over an engine ({@link #FrostlakeHttpTransport(DatabaseEngine)}) and passed in the connection
 * properties under {@code httpTransport} answers for that engine whatever host the URL names. Either way the engine
 * answers as its HTTP endpoint does (see {@link SnowflakeDirectEndpoint}).
 */
public final class FrostlakeHttpTransport implements HttpTransport, AutoCloseable {

    /** The name a connection URL selects this transport by. */
    public static final String NAME = "frostlake";

    /** The endpoint of the engine this transport was made over, or null when the URL's host names the engine. */
    private final SnowflakeDirectEndpoint endpoint;

    /** The transport {@link java.util.ServiceLoader} makes: the URL's host names the direct engine. */
    public FrostlakeHttpTransport() {
        this.endpoint = null;
    }

    /**
     * A transport that answers for one engine, whatever host the connection URL names.
     *
     * @param engine the engine
     */
    public FrostlakeHttpTransport(final DatabaseEngine engine) {
        this.endpoint = SnowflakeDirectEndpoint.of(engine);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public HttpTransportResponse execute(final HttpTransportRequest request) throws IOException {
        final SnowflakeDirectEndpoint target = endpoint != null ? endpoint
            : SnowflakeDirectEndpoint.direct(engineName(request.getUri()));
        final SnowflakeDirectResponse response = target.answer(request.getUri(), request.getHeaders(),
            request.getBody());
        final Map<String, List<String>> headers = new HashMap<String, List<String>>();
        headers.put("Content-Type", Collections.singletonList(response.getContentType()));
        return new HttpTransportResponse(response.getStatus(), headers, response.getBody());
    }

    /**
     * Ends the sessions of a transport made over an engine and leaves the engine running; a transport that names
     * engines by host leaves their endpoints to them.
     */
    @Override
    public void close() {
        if (endpoint != null) {
            endpoint.close();
        }
    }

    /** The direct engine a request names: its URI's host, as the connection URL writes it. */
    private static String engineName(final URI uri) throws IOException {
        final String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            throw new IOException("The request " + uri + " names no host; the connection URL's host names the"
                + " Frostlake engine (letters, digits, hyphens and dots)");
        }
        return host;
    }
}
