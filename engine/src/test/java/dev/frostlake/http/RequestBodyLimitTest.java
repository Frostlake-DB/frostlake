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
import dev.frostlake.LiveSnowflake;
import dev.frostlake.config.EngineConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * A request body is read at most to the bound {@code http.maxRequestBytes} sets, as sent and, for a gzip-compressed
 * one, once unzipped: a larger one is answered 413 before it is held whole, nothing of it run, and each driver reads
 * the 413 as the statement's failure. Engine only: the server under test is the local one.
 */
public class RequestBodyLimitTest {

    private static final int LIMIT = 64 * 1024;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private static DatabaseHttpServer server;
    private static int port;

    @BeforeAll
    public static void startServer() throws IOException {
        assumeFalse(LiveSnowflake.enabled(), "the server under test is the local one");
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        final EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_HTTP_PORT, String.valueOf(port));
        config.setProperty(EngineConfig.PROP_HTTP_MAX_REQUEST_BYTES, String.valueOf(LIMIT));
        server = new DatabaseHttpServer(config);
        server.start();
    }

    @AfterAll
    public static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    private static HttpResponse<String> post(final String path, final HttpRequest.BodyPublisher body,
                                             final String... headers) throws Exception {
        final HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .POST(body).header("Content-Type", "application/json");
        for (int i = 0; i + 1 < headers.length; i += 2) {
            request.header(headers[i], headers[i + 1]);
        }
        return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** The status line of a POST to /api/execute whose body is sent in chunks, without a length. */
    private static String chunked(final String body) throws IOException {
        final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        try (Socket socket = new Socket("127.0.0.1", port)) {
            final OutputStream out = socket.getOutputStream();
            out.write(("POST /api/execute HTTP/1.1\r\nHost: 127.0.0.1\r\nContent-Type: application/json\r\n"
                + "Transfer-Encoding: chunked\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            for (int at = 0; at < bytes.length; at += 4096) {
                final int length = Math.min(4096, bytes.length - at);
                out.write((Integer.toHexString(length) + "\r\n").getBytes(StandardCharsets.UTF_8));
                out.write(bytes, at, length);
                out.write("\r\n".getBytes(StandardCharsets.UTF_8));
            }
            out.write("0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            final BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                StandardCharsets.UTF_8));
            return in.readLine();
        }
    }

    private static String selectOfLength(final int length) {
        return "{\"sql\":\"SELECT '" + "x".repeat(length) + "'\"}";
    }

    private static byte[] gzip(final String text) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream zip = new GZIPOutputStream(out)) {
            zip.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    @Test
    public void aBodyLargerThanTheBoundIsAnswered413() throws Exception {
        final int sessions = server.getEngine().getActiveSessionCount();
        assertEquals(200, post("/api/execute", HttpRequest.BodyPublishers.ofString(selectOfLength(100)))
            .statusCode());
        // Declared too long, it is refused unread; sent without a length, it stops being read at the bound.
        final HttpResponse<String> declared = post("/api/execute",
            HttpRequest.BodyPublishers.ofString(selectOfLength(LIMIT)));
        assertEquals(413, declared.statusCode());
        assertEquals("The request body exceeds the " + LIMIT + " bytes the server takes (http.maxRequestBytes).",
            MAPPER.readTree(declared.body()).path("errorMessage").asString());
        assertTrue(chunked(selectOfLength(LIMIT)).startsWith("HTTP/1.1 413 "));
        assertEquals(413, post("/api/sessions", HttpRequest.BodyPublishers.ofString(selectOfLength(LIMIT)))
            .statusCode());
        assertEquals(sessions + 1, server.getEngine().getActiveSessionCount(), "only the request served opened one");
    }

    @Test
    public void aCompressedBodyIsBoundOnceUnzipped() throws Exception {
        final JsonNode login = MAPPER.readTree(post("/session/v1/login-request",
            HttpRequest.BodyPublishers.ofString("{\"data\":{}}")).body());
        final String token = login.path("data").path("token").asString();
        final String auth = "Snowflake Token=\"" + token + "\"";
        final byte[] small = gzip("{\"sqlText\":\"SELECT 41 + 1\"}");
        final HttpResponse<String> served = post("/queries/v1/query-request",
            HttpRequest.BodyPublishers.ofByteArray(small), "Content-Encoding", "gzip", "Authorization", auth);
        assertEquals(200, served.statusCode());
        assertTrue(MAPPER.readTree(served.body()).path("success").asBoolean(), served.body());
        // A few kilobytes that unzip past the bound.
        final byte[] bomb = gzip("{\"sqlText\":\"SELECT '" + "x".repeat(LIMIT * 4) + "'\"}");
        assertTrue(bomb.length < LIMIT / 8, "compressed to " + bomb.length);
        final HttpResponse<String> refused = post("/queries/v1/query-request",
            HttpRequest.BodyPublishers.ofByteArray(bomb), "Content-Encoding", "gzip", "Authorization", auth);
        assertEquals(413, refused.statusCode());
        assertEquals("The request body exceeds the " + LIMIT + " bytes the server takes (http.maxRequestBytes).",
            MAPPER.readTree(refused.body()).path("message").asString());
    }

    @Test
    public void eachDriverReadsThe413AsTheStatementsFailure() throws Exception {
        final String tooLong = "SELECT '" + "x".repeat(LIMIT * 2) + "'";
        final Properties properties = new Properties();
        properties.put("user", "any_user");
        properties.put("password", "any_password");
        properties.put("disablePlatformDetection", "true");
        try (Connection connection = DriverManager.getConnection(
                "jdbc:snowflake://127.0.0.1:" + port + "/?account=frostlake&ssl=off", properties);
             Statement statement = connection.createStatement()) {
            final SQLException refused = assertThrows(SQLException.class, new Executable() {
                @Override
                public void execute() throws SQLException {
                    statement.execute(tooLong);
                }
            });
            assertTrue(refused.getMessage().contains("413"), refused.getMessage());
        }
        try (Connection connection = DriverManager.getConnection("jdbc:frostlake://127.0.0.1:" + port + "/");
             Statement statement = connection.createStatement()) {
            final SQLException refused = assertThrows(SQLException.class, new Executable() {
                @Override
                public void execute() throws SQLException {
                    statement.execute(tooLong);
                }
            });
            assertTrue(refused.getMessage().contains("413"), refused.getMessage());
            // The connection goes on serving a request within the bound.
            statement.execute("SELECT 1");
        }
    }

    @Test
    public void anInProcessEndpointIsBoundAlike() throws Exception {
        final DatabaseEngine engine = new DatabaseEngine();
        engine.getConfig().setProperty(EngineConfig.PROP_HTTP_MAX_REQUEST_BYTES, "1024");
        try (SnowflakeDirectEndpoint endpoint = SnowflakeDirectEndpoint.of(engine)) {
            final Map<String, List<String>> zipped = Collections.singletonMap("Content-Encoding",
                Collections.singletonList("gzip"));
            final URI login = URI.create("http://frostlake/session/v1/login-request");
            assertEquals(200, endpoint.answer(login, zipped, gzip("{\"data\":{}}")).getStatus());
            assertEquals(413, endpoint.answer(login, zipped,
                gzip("{\"data\":{\"LOGIN_NAME\":\"" + "x".repeat(2048) + "\"}}")).getStatus());
        } finally {
            engine.shutdown();
        }
    }
}
