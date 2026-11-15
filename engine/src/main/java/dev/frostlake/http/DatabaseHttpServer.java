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

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.executor.SqlScriptSplitter;
import dev.frostlake.functions.scalar.file.BuildStageFileUrl;
import dev.frostlake.functions.scalar.file.PresignedUrls;
import dev.frostlake.http.rest.RestApiHandler;
import dev.frostlake.http.rest.RestResults;
import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/**
 * HTTP server for Frostlake SQL Engine
 * Provides REST API for SQL execution with concurrent multi-user support.
 *
 * <p>The server listens on {@code http.port}; port 0 takes a free port, which {@link #getPort} reports once the
 * server is created. It runs an engine of its own, or serves one that already runs in the JVM - the engine of
 * {@code jdbc:frostlake:direct:<name>} connections - so its clients and those connections share one database.
 */
public class DatabaseHttpServer {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseHttpServer.class);

    private final HttpServer server;
    private final ConcurrentDatabaseEngine engine;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String NO_DELAY_PROPERTY = "sun.net.httpserver.nodelay";
    /** The longest a stop waits for the requests in flight to be answered. */
    private static final long STOP_GRACE_MILLIS = 3000;
    private final int port;
    private final EngineConfig config;
    private final RestResults restResults;
    private final SnowflakeProtocolHandler snowflake;
    /** The HTTPS listener, or null where http.tls.port names none. */
    private final HttpsServer tlsServer;
    /** The threads that serve requests, the HTTPS listener's included, shut down when the server stops. */
    private final List<ExecutorService> executors = new ArrayList<>();
    /** The requests the listeners are answering, which a stop lets finish. */
    private final InFlightRequests inFlight = new InFlightRequests();

    public DatabaseHttpServer(final int port) throws IOException {
        this(createConfigWithPort(port));
    }

    private static EngineConfig createConfigWithPort(final int port) {
        final EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_HTTP_PORT, String.valueOf(port));
        return config;
    }

    public DatabaseHttpServer(final EngineConfig config) throws IOException {
        // The engine runs on the server's own configuration, so a file named on the command line sets the engine's
        // properties (database.default, http.session.idleTimeoutMinutes, ...) as well as the server's.
        this(config, new ConcurrentDatabaseEngine(config));
    }

    /**
     * A server over an engine that already runs in this JVM, such as the one {@code jdbc:frostlake:direct:<name>}
     * connections share (see {@code DatabaseDriver.directEngine}): the server's clients and the engine's other
     * clients read and write one database, each session its own. The configuration sets the server - its port, host
     * and listeners - and the engine keeps its own. Every request runs alone on the engine, as a direct connection's
     * statement does (see {@link ConcurrentDatabaseEngine#sharing}), and stopping the server ends its sessions and
     * leaves the engine running.
     *
     * @param config the server's configuration
     * @param engine the engine to serve
     * @throws IOException where a listener cannot be opened
     */
    public DatabaseHttpServer(final EngineConfig config, final DatabaseEngine engine) throws IOException {
        this(config, ConcurrentDatabaseEngine.sharing(engine));
    }

    private DatabaseHttpServer(final EngineConfig config, final ConcurrentDatabaseEngine engine) throws IOException {
        this.config = config;
        this.engine = engine;
        enableNoDelay();

        final String host = config.getHttpHost();
        this.server = HttpServer.create(new InetSocketAddress(host, config.getHttpPort()), 0);
        // Port 0 takes a free port: the port bound is the one the server is reached at.
        this.port = server.getAddress().getPort();

        // Set up handlers. Each is wrapped, so a fault its own handling does not answer is still answered
        // instead of leaving the client to time out.
        // Every request passes the guard first: the host it names, the origin it comes from, and for the SQL
        // endpoints the kind of body it carries (see RequestGuard).
        this.restResults = new RestResults(config.getRestSyncWaitMs(), config.getRestResultRetentionMs());
        // The Snowflake JDBC driver's own endpoints, so that driver connects here unchanged; one handler serves both
        // listeners, so a query run over one is followed by its ID over the other.
        this.snowflake = new SnowflakeProtocolHandler(engine);
        registerContexts(server, host, config);
        // The engine names this server in the presigned and file URLs it hands out.
        engine.getEngine().getConfig().setProperty(EngineConfig.PROP_HTTP_HOST, host);
        engine.getEngine().getConfig().setProperty(EngineConfig.PROP_HTTP_PORT, String.valueOf(port));

        // Use a thread pool for handling requests
        final int maxConnections = config.getMaxConnections();
        server.setExecutor(pool("frostlake-http-" + port, maxConnections));

        // An HTTPS listener beside the plain one, where http.tls.port names one: the same endpoints, for a client
        // that speaks TLS only - the Snowflake driver's default.
        if (config.getHttpTlsPort() >= 0) {
            final HttpsServer https = HttpsServer.create(new InetSocketAddress(host, config.getHttpTlsPort()), 0);
            https.setHttpsConfigurator(new HttpsConfigurator(sslContext(config)));
            registerContexts(https, host, config);
            https.setExecutor(pool("frostlake-https-" + https.getAddress().getPort(), maxConnections));
            this.tlsServer = https;
        } else {
            this.tlsServer = null;
        }

        logger.info("HTTP server created on {}:{} with max {} connections", host, port, maxConnections);
    }

    /**
     * A pool of request threads named after their listener - {@code frostlake-http-<port>-<n>} - kept so that stopping
     * the server ends them.
     */
    private ExecutorService pool(final String name, final int threads) {
        final AtomicInteger made = new AtomicInteger();
        final ExecutorService pool = Executors.newFixedThreadPool(threads, new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable work) {
                return new Thread(work, name + "-" + made.incrementAndGet());
            }
        });
        executors.add(pool);
        return pool;
    }

    /**
     * Registers every endpoint on a listener. Each handler is wrapped, so a fault its own handling does not answer is
     * still answered instead of leaving the client to time out, and every request passes the guard first: the host it
     * names, the origin it comes from, and for the SQL endpoints the kind of body it carries (see RequestGuard). Each
     * request is counted while it runs, which a stop waits for.
     */
    private void registerContexts(final HttpServer target, final String host, final EngineConfig config) {
        final RequestGuard guard = new RequestGuard(target.getAddress().getAddress(), host,
            config.getCorsAllowedOrigins());
        target.createContext("/api/execute", counted(new ExecuteSqlHandler(engine), guard, true));
        target.createContext("/api/health", counted(new HealthCheckHandler(engine), guard, false));
        target.createContext("/api/sessions", counted(new SessionInfoHandler(engine), guard, true));
        target.createContext(RestApiHandler.CONTEXT, counted(new RestApiHandler(engine, restResults), guard, false));
        target.createContext(PresignedUrls.CONTEXT, counted(new PresignedFileHandler(), guard, false));
        target.createContext(BuildStageFileUrl.CONTEXT, counted(new StageFileUrlHandler(engine), guard, false));
        for (final String context : SnowflakeProtocolHandler.CONTEXTS) {
            target.createContext(context, counted(snowflake, guard, true));
        }
    }

    /** A handler wrapped to answer its faults and pass the guard (see ErrorAnsweringHandler), and counted. */
    private HttpHandler counted(final HttpHandler handler, final RequestGuard guard, final boolean jsonBody) {
        return new CountedHandler(new ErrorAnsweringHandler(handler, guard, jsonBody), inFlight);
    }

    /** The HTTPS listener's TLS context, from the keystore http.tls.keystore names. */
    private static SSLContext sslContext(final EngineConfig config) throws IOException {
        final String keystore = config.getHttpTlsKeystore();
        if (keystore.isEmpty()) {
            throw new IOException(EngineConfig.PROP_HTTP_TLS_PORT + " needs " + EngineConfig.PROP_HTTP_TLS_KEYSTORE
                + ", the keystore holding the server's key and certificate");
        }
        final char[] password = config.getHttpTlsKeystorePassword().toCharArray();
        try (InputStream in = Files.newInputStream(Paths.get(keystore))) {
            final KeyStore store = KeyStore.getInstance(config.getHttpTlsKeystoreType());
            store.load(in, password);
            final KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keys.init(store, password);
            final SSLContext context = SSLContext.getInstance("TLS");
            context.init(keys.getKeyManagers(), null, null);
            return context;
        } catch (final GeneralSecurityException unreadable) {
            throw new IOException("The keystore " + keystore + " cannot be read: " + unreadable.getMessage(),
                unreadable);
        }
    }

    /**
     * Start the HTTP server
     */
    public void start() {
        server.start();
        logger.info("Frostlake HTTP server started on port {}", port);
        if (tlsServer != null) {
            tlsServer.start();
            logger.info("Frostlake HTTPS listener started on port {}", getTlsPort());
        }
        logger.info("API endpoints:");
        logger.info("  POST /api/execute - Execute SQL");
        logger.info("  GET  /api/health - Health check");
        logger.info("  POST /session/v1/login-request, /queries/v1/query-request - Snowflake JDBC driver");
        logger.info("  GET  /api/sessions - Session information");
        logger.info("  POST /api/sessions - Start a session");
        logger.info("  DELETE /api/sessions/{id} - Release a session");
        logger.info("  /api/v2/... - Snowflake REST APIs for resource management");
    }

    /**
     * TCP no-delay for every socket the server accepts. The JDK server leaves Nagle's algorithm on and
     * writes a response's headers and body as two segments, so on a kept-alive connection the body waits
     * for the client's delayed ACK — about 40 ms per statement on Linux and macOS. The JDK reads the
     * property once, when its server configuration first loads, so it is set before the first server is
     * created; a value the launcher chose is left alone.
     */
    private static void enableNoDelay() {
        if (System.getProperty(NO_DELAY_PROPERTY) == null) {
            System.setProperty(NO_DELAY_PROPERTY, "true");
        }
    }

    /**
     * Stop the HTTP server: the requests in flight are answered first, for at most three seconds, and the listeners
     * then stop at once, so a server with none open stops without waiting.
     */
    public void stop() {
        logger.info("Stopping Frostlake HTTP server");
        inFlight.awaitNone(STOP_GRACE_MILLIS);
        server.stop(0);
        if (tlsServer != null) {
            tlsServer.stop(0);
        }
        // A listener's stop leaves the threads it was given; ended here, they no longer keep the JVM alive.
        for (final ExecutorService pool : executors) {
            pool.shutdown();
        }
        restResults.shutdown();
        engine.shutdown();
        logger.info("Frostlake HTTP server stopped");
    }

    public ConcurrentDatabaseEngine getEngine() {
        return engine;
    }

    /**
     * The port the server listens on: the one {@code http.port} names, or the free port it took for port 0.
     *
     * @return the port
     */
    public int getPort() {
        return port;
    }

    /**
     * The port the HTTPS listener took, or -1 where the server has none.
     *
     * @return the port
     */
    public int getTlsPort() {
        return tlsServer == null ? -1 : tlsServer.getAddress().getPort();
    }

    /**
     * Execute an init SQL file statement by statement. The split comes from the engine's own
     * grammar ({@link SqlScriptSplitter}) — the previous character-level $$-parity/trailing-';'
     * scan mis-grouped everything after {@code SELECT '$$';} and cut multi-line literals. Each
     * statement still executes independently so USE DATABASE / USE SCHEMA changes are visible to
     * subsequent statements and one failure does not stop the file.
     */
    private static void executeInitFile(final DatabaseHttpServer server,
                                        final SessionContext initSession,
                                        final String filePath) throws Exception {
        final String script = Files.readString(Path.of(filePath));
        for (final String stmt : SqlScriptSplitter.split(script)) {
            try {
                server.getEngine().execute(stmt, initSession);
            } catch (final Exception e) {
                logger.warn("Init file statement failed (continuing): {} — {}",
                    stmt.length() > 60 ? stmt.substring(0, 60) + "..." : stmt, e.getMessage());
            }
        }
    }

    /**
     * Main method to start the server
     */
    public static void main(final String[] args) {
        try {
            // Parse arguments: [port] [configFile] [-f|--file <sqlFile>]
            final ServerArguments arguments;
            try {
                arguments = ServerArguments.parse(args);
            } catch (final IllegalArgumentException malformed) {
                System.err.println("Error: " + malformed.getMessage());
                System.exit(1);
                return;
            }
            final EngineConfig config = arguments.config();
            final String initFile = arguments.initFile();

            final DatabaseHttpServer server = new DatabaseHttpServer(config);

            // Execute init file before starting to accept requests
            if (initFile != null) {
                final File f = new File(initFile);
                if (!f.exists()) {
                    System.err.println("Error: init file not found: " + initFile);
                    System.exit(1);
                }
                logger.info("Executing init file: {}", initFile);
                final SessionContext initSession = server.getEngine().getOrCreateSession("init");
                executeInitFile(server, initSession, initFile);
                logger.info("Init file executed successfully: {}", initFile);
            }

            server.start();

            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    logger.info("Shutdown hook triggered");
                    server.stop();
                }
            }));

            logger.info("Server is running. Press Ctrl+C to stop.");

        } catch (final Exception e) {
            logger.error("Failed to start server", e);
            System.exit(1);
        }
    }
}
