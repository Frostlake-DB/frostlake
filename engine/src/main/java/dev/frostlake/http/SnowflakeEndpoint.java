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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.jdbc.DatabaseDriver;

import java.io.IOException;

/**
 * An in-process endpoint Snowflake's own JDBC driver connects to, in one call: an HTTP server on a free port of
 * 127.0.0.1, plain HTTP, and the {@code jdbc:snowflake:} URL that reaches it.
 *
 * <pre>{@code
 * try (SnowflakeEndpoint endpoint = SnowflakeEndpoint.start("mydb")) {
 *     Connection connection = DriverManager.getConnection(endpoint.jdbcUrl(), "any", "any");
 *     ...
 * }
 * }</pre>
 *
 * <p>{@link #start(String)} serves the engine {@code jdbc:frostlake:direct:<name>} connections share, so the driver's
 * connections and the direct ones read and write one database; {@link #start(EngineConfig)} runs an engine of its own.
 * {@link #close} stops the server; a direct name's engine stays with its name.
 */
public final class SnowflakeEndpoint implements AutoCloseable {

    private static final String LOOPBACK = "127.0.0.1";

    private final DatabaseHttpServer server;

    private SnowflakeEndpoint(final DatabaseHttpServer server) {
        this.server = server;
    }

    /**
     * An endpoint serving the engine {@code jdbc:frostlake:direct:<name>} connections share, created as the first such
     * connection creates it where none runs yet (see {@link DatabaseDriver#directEngine}).
     *
     * @param directName the direct name
     * @return the started endpoint
     * @throws IOException where the server cannot listen
     */
    public static SnowflakeEndpoint start(final String directName) throws IOException {
        return start(DatabaseDriver.directEngine(directName));
    }

    /**
     * An endpoint serving an engine that already runs in this JVM, its clients and the endpoint's reading and writing
     * one database (see {@link DatabaseHttpServer#DatabaseHttpServer(EngineConfig, DatabaseEngine)}).
     *
     * @param engine the engine
     * @return the started endpoint
     * @throws IOException where the server cannot listen
     */
    public static SnowflakeEndpoint start(final DatabaseEngine engine) throws IOException {
        return started(new DatabaseHttpServer(onLoopback(new EngineConfig()), engine));
    }

    /**
     * An endpoint running an engine of its own on a configuration - its database, schema and other engine settings;
     * the endpoint takes a free port of 127.0.0.1 and plain HTTP whatever the configuration names, and leaves the
     * configuration as it was. Closing the endpoint ends the engine.
     *
     * @param config the configuration
     * @return the started endpoint
     * @throws IOException where the server cannot listen
     */
    public static SnowflakeEndpoint start(final EngineConfig config) throws IOException {
        return started(new DatabaseHttpServer(onLoopback(new EngineConfig(config.getProperties()))));
    }

    private static EngineConfig onLoopback(final EngineConfig config) {
        config.setProperty(EngineConfig.PROP_HTTP_HOST, LOOPBACK);
        config.setProperty(EngineConfig.PROP_HTTP_PORT, "0");
        config.setProperty(EngineConfig.PROP_HTTP_TLS_PORT, "-1");
        return config;
    }

    private static SnowflakeEndpoint started(final DatabaseHttpServer server) {
        server.start();
        return new SnowflakeEndpoint(server);
    }

    /**
     * The URL Snowflake's driver connects by:
     * {@code jdbc:snowflake://127.0.0.1:<port>/?account=frostlake&ssl=off&disablePlatformDetection=true}, the last
     * sparing the driver its cloud-metadata lookups at connect. Any user and password log in.
     *
     * @return the URL
     */
    public String jdbcUrl() {
        return "jdbc:snowflake://" + LOOPBACK + ":" + server.getPort()
            + "/?account=frostlake&ssl=off&disablePlatformDetection=true";
    }

    /**
     * The port the endpoint listens on.
     *
     * @return the port
     */
    public int getPort() {
        return server.getPort();
    }

    /**
     * The endpoint's server.
     *
     * @return the server
     */
    public DatabaseHttpServer getServer() {
        return server;
    }

    /** Stops the server: its sessions end, what they left open rolled back. A direct name's engine runs on. */
    @Override
    public void close() {
        server.stop();
    }
}
