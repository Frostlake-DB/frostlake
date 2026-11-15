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
import dev.frostlake.DatabaseEngine;
import dev.frostlake.jdbc.DatabaseDriver;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Snowflake endpoints of an engine answered in process: the requests Snowflake's drivers send to
 * {@code /session}, {@code /queries}, {@code /monitoring} and {@code /telemetry}, handed over as their parts instead of
 * through a socket. It answers as {@link SnowflakeEndpoint} answers over HTTP, so a driver that can carry its requests
 * itself - Snowflake's JDBC driver through an HTTP transport - reaches an embedded engine without a port.
 *
 * <p>An endpoint keeps the sessions its logins open, so a driver sends every request of a session to the same
 * endpoint. {@link #direct(String)} keeps one endpoint per direct engine for that reason; {@link #of(DatabaseEngine)}
 * makes a new one, which its caller keeps and closes. Either one's sessions read and write the engine's one database
 * beside its other clients, as {@code jdbc:frostlake:direct} connections do.
 */
public final class SnowflakeDirectEndpoint implements AutoCloseable {

    /** The content type of every answer. */
    public static final String CONTENT_TYPE = "application/json";

    /** The endpoints of the direct engines, by name. */
    private static final Map<String, SnowflakeDirectEndpoint> DIRECT = new HashMap<String, SnowflakeDirectEndpoint>();

    private final DatabaseEngine engine;
    private final ConcurrentDatabaseEngine sessions;
    private final SnowflakeProtocolHandler handler;
    private final String directName;

    private SnowflakeDirectEndpoint(final DatabaseEngine engine, final String directName) {
        this.engine = engine;
        this.sessions = ConcurrentDatabaseEngine.sharing(engine);
        this.handler = new SnowflakeProtocolHandler(sessions);
        this.directName = directName;
    }

    /**
     * A new endpoint of an engine.
     *
     * @param engine the engine
     * @return the endpoint, which the caller closes
     */
    public static SnowflakeDirectEndpoint of(final DatabaseEngine engine) {
        return new SnowflakeDirectEndpoint(engine, null);
    }

    /**
     * The endpoint of the engine {@code jdbc:frostlake:direct:<name>} connections share (see
     * {@link DatabaseDriver#directEngine(String)}): made when first asked for, and kept while that engine stays the one
     * registered under the name. An engine registered anew under the name gets a new endpoint, the old one's sessions
     * ended.
     *
     * @param name the direct engine's name, trimmed as the driver trims it
     * @return the endpoint
     */
    public static SnowflakeDirectEndpoint direct(final String name) {
        final String key = name.trim();
        synchronized (DIRECT) {
            final DatabaseEngine engine = DatabaseDriver.directEngine(key);
            SnowflakeDirectEndpoint endpoint = DIRECT.get(key);
            if (endpoint != null && endpoint.engine != engine) {
                endpoint.sessions.shutdown();
                endpoint = null;
            }
            if (endpoint == null) {
                endpoint = new SnowflakeDirectEndpoint(engine, key);
                DIRECT.put(key, endpoint);
            }
            return endpoint;
        }
    }

    /**
     * Answers one request as the endpoint answers it over HTTP; a request it cannot serve is answered with the refusal
     * a driver reads, not thrown.
     *
     * @param uri     the request URI; its path and query are read, its scheme and authority are not
     * @param headers the request headers by name, matched without regard to case: {@code Authorization} names the
     *                session, {@code Content-Encoding} the body's compression
     * @param body    the request body as a driver sends it; null when it has none
     * @return the response
     */
    public SnowflakeDirectResponse answer(final URI uri, final Map<String, List<String>> headers, final byte[] body) {
        final SnowflakeReply reply = handler.answer(SnowflakeRequest.of(uri, headers, body));
        return new SnowflakeDirectResponse(reply.status(), CONTENT_TYPE,
            reply.json().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The engine the endpoint answers for.
     *
     * @return the engine
     */
    public DatabaseEngine getEngine() {
        return engine;
    }

    /**
     * Ends the endpoint's sessions, as their logouts would, and leaves the engine running. A direct engine's endpoint
     * is made anew when next asked for.
     */
    @Override
    public void close() {
        if (directName != null) {
            synchronized (DIRECT) {
                if (DIRECT.get(directName) == this) {
                    DIRECT.remove(directName);
                }
            }
        }
        sessions.shutdown();
    }
}
