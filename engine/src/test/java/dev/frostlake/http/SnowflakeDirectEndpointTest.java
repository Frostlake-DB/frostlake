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
import dev.frostlake.jdbc.DatabaseDriver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** The Snowflake endpoints answered in process, as a driver that carries its own requests reaches them. */
public class SnowflakeDirectEndpointTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** A host that never resolves: the requests name it, and nothing connects to it. */
    private static final String SERVER = "https://frostlake.invalid";
    private static final String EXPIRED = "390112";

    private final List<String> directNames = new ArrayList<String>();
    private final List<AutoCloseable> opened = new ArrayList<AutoCloseable>();

    @BeforeEach
    public void localOnly() {
        assumeFalse(LiveSnowflake.enabled(), "the endpoint under test is the embedded engine's");
    }

    @AfterEach
    public void release() throws Exception {
        for (final AutoCloseable resource : opened) {
            resource.close();
        }
        for (final String name : directNames) {
            final DatabaseEngine engine = DatabaseDriver.unregisterDirectEngine(name);
            if (engine != null) {
                engine.shutdown();
            }
        }
    }

    @Test
    public void aLoginAndAStatementAreAnsweredWithoutASocket() {
        final DatabaseEngine engine = new DatabaseEngine();
        final SnowflakeDirectEndpoint endpoint = keep(SnowflakeDirectEndpoint.of(engine));

        final String token = login(endpoint);
        final JsonNode data = run(endpoint, token, "SELECT 1 + 1 AS TWO");

        assertEquals("TWO", data.path("rowtype").path(0).path("name").asString());
        assertEquals("2", data.path("rowset").path(0).path(0).asString());
        assertSame(engine, endpoint.getEngine());
    }

    @Test
    public void aGzipCompressedBodyIsReadAsTheDriverSendsIt() throws IOException {
        final SnowflakeDirectEndpoint endpoint = keep(SnowflakeDirectEndpoint.of(new DatabaseEngine()));
        final String token = login(endpoint);
        final Map<String, List<String>> headers = headers(token);
        headers.put("Content-Encoding", Collections.singletonList("gzip"));

        final SnowflakeDirectResponse response = endpoint.answer(URI.create(SERVER + "/queries/v1/query-request"),
            headers, gzip(MAPPER.writeValueAsBytes(statement("SELECT 'zipped' AS T"))));

        final JsonNode answer = MAPPER.readTree(response.getBody());
        assertTrue(answer.path("success").asBoolean(), answer.toString());
        assertEquals("zipped", answer.path("data").path("rowset").path(0).path(0).asString());
    }

    @Test
    public void aDirectEngineIsSharedWithItsJdbcConnections() throws SQLException {
        final String name = directName();
        try (Connection connection = DriverManager.getConnection("jdbc:frostlake:direct:" + name);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE shared_rows (n INT)");
            statement.execute("INSERT INTO shared_rows VALUES (1), (2), (3)");
        }
        final SnowflakeDirectEndpoint endpoint = keep(SnowflakeDirectEndpoint.direct(name));

        final JsonNode data = run(endpoint, login(endpoint), "SELECT SUM(n) FROM shared_rows");
        run(endpoint, login(endpoint), "INSERT INTO shared_rows VALUES (4)");

        assertEquals("6", data.path("rowset").path(0).path(0).asString());
        assertSame(endpoint, SnowflakeDirectEndpoint.direct(" " + name + " "));
        assertSame(DatabaseDriver.directEngine(name), endpoint.getEngine());
        try (Connection connection = DriverManager.getConnection("jdbc:frostlake:direct:" + name);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM shared_rows")) {
            assertTrue(rows.next());
            assertEquals(4, rows.getInt(1));
        }
    }

    @Test
    public void anEngineRegisteredAnewUnderTheNameGetsANewEndpoint() {
        final String name = directName();
        final SnowflakeDirectEndpoint first = keep(SnowflakeDirectEndpoint.direct(name));
        final String token = login(first);
        final DatabaseEngine replaced = DatabaseDriver.unregisterDirectEngine(name);
        replaced.shutdown();

        final SnowflakeDirectEndpoint second = keep(SnowflakeDirectEndpoint.direct(name));

        assertNotSame(first, second);
        assertEquals(EXPIRED, post(second, "/queries/v1/query-request", token, statement("SELECT 1"))
            .path("code").asString());
        assertEquals(EXPIRED, post(first, "/queries/v1/query-request", token, statement("SELECT 1"))
            .path("code").asString());
    }

    @Test
    public void closingEndsTheSessionsAndLeavesTheEngineRunning() throws SQLException {
        final String name = directName();
        final SnowflakeDirectEndpoint endpoint = SnowflakeDirectEndpoint.direct(name);
        final String token = login(endpoint);
        run(endpoint, token, "CREATE TABLE kept (n INT)");

        endpoint.close();

        assertEquals(EXPIRED, post(endpoint, "/queries/v1/query-request", token, statement("SELECT 1"))
            .path("code").asString());
        final SnowflakeDirectEndpoint next = keep(SnowflakeDirectEndpoint.direct(name));
        assertNotSame(endpoint, next);
        assertEquals("0", run(next, login(next), "SELECT COUNT(*) FROM kept").path("rowset").path(0).path(0)
            .asString());
    }

    @Test
    public void anUnknownPathIsNotFoundAndAMissingTokenIsAnExpiredSession() {
        final SnowflakeDirectEndpoint endpoint = keep(SnowflakeDirectEndpoint.of(new DatabaseEngine()));

        final SnowflakeDirectResponse unknown = endpoint.answer(URI.create(SERVER + "/no/such/path"),
            Collections.<String, List<String>>emptyMap(), null);
        final JsonNode refused = post(endpoint, "/queries/v1/query-request", null, statement("SELECT 1"));

        assertEquals(404, unknown.getStatus());
        assertEquals(SnowflakeDirectEndpoint.CONTENT_TYPE, unknown.getContentType());
        assertEquals("390400", MAPPER.readTree(unknown.getBody()).path("code").asString());
        assertFalse(refused.path("success").asBoolean());
        assertEquals(EXPIRED, refused.path("code").asString());
    }

    private <T extends AutoCloseable> T keep(final T resource) {
        opened.add(resource);
        return resource;
    }

    private String directName() {
        final String name = "snowflake_direct_" + UUID.randomUUID().toString().replace("-", "");
        directNames.add(name);
        return name;
    }

    /** Logs in with the results asked for as JSON, as a connection naming JDBC_QUERY_RESULT_FORMAT=JSON does. */
    private static String login(final SnowflakeDirectEndpoint endpoint) {
        final ObjectNode body = MAPPER.createObjectNode();
        final ObjectNode data = body.putObject("data");
        data.put("LOGIN_NAME", "frostlake");
        data.put("PASSWORD", "frostlake");
        data.putObject("SESSION_PARAMETERS").put("JDBC_QUERY_RESULT_FORMAT", "JSON");
        final JsonNode answer = post(endpoint, "/session/v1/login-request", null, body);
        assertTrue(answer.path("success").asBoolean(), answer.toString());
        return answer.path("data").path("token").asString();
    }

    /** A statement's answer's {@code data}, the statement having succeeded. */
    private static JsonNode run(final SnowflakeDirectEndpoint endpoint, final String token, final String sql) {
        final JsonNode answer = post(endpoint, "/queries/v1/query-request", token, statement(sql));
        assertTrue(answer.path("success").asBoolean(), answer.toString());
        return answer.path("data");
    }

    private static ObjectNode statement(final String sql) {
        final ObjectNode body = MAPPER.createObjectNode();
        body.put("sqlText", sql);
        body.put("sequenceId", 1);
        return body;
    }

    private static JsonNode post(final SnowflakeDirectEndpoint endpoint, final String path, final String token,
                                 final ObjectNode body) {
        final SnowflakeDirectResponse response = endpoint.answer(URI.create(SERVER + path), headers(token),
            MAPPER.writeValueAsBytes(body));
        assertEquals(200, response.getStatus());
        assertEquals(SnowflakeDirectEndpoint.CONTENT_TYPE, response.getContentType());
        return MAPPER.readTree(response.getBody());
    }

    private static Map<String, List<String>> headers(final String token) {
        final Map<String, List<String>> headers = new HashMap<String, List<String>>();
        headers.put("Content-Type", Collections.singletonList("application/json"));
        if (token != null) {
            headers.put("authorization", Collections.singletonList("Snowflake Token=\"" + token + "\""));
        }
        return headers;
    }

    private static byte[] gzip(final byte[] bytes) throws IOException {
        final ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(compressed)) {
            out.write(bytes);
        }
        return compressed.toByteArray();
    }
}
