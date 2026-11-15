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

import dev.frostlake.LiveSnowflake;
import dev.frostlake.config.EngineConfig;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * The format a statement's answer takes at the Snowflake driver endpoints, read off the wire as a driver reads it:
 * only a query's rows go back as Arrow where the session takes Arrow, every other answer - a status, a DML
 * statement's counts, a listing, a description, a plan, a block's answer - as a JSON rowset, as does each result of a
 * text of several; a DML answer carries its counts in {@code stats} when it changed a row. Snowflake's Python connector
 * reads a DML statement's rowcount off that JSON rowset alone.
 */
public class SnowflakeResultFormatTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private static DatabaseHttpServer server;
    private static String base;

    @BeforeAll
    public static void startServer() throws IOException {
        assumeFalse(LiveSnowflake.enabled(), "the server under test is the local one");
        final int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        final EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_HTTP_PORT, String.valueOf(port));
        server = new DatabaseHttpServer(config);
        server.start();
        base = "http://127.0.0.1:" + port;
    }

    @AfterAll
    public static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    private static JsonNode post(final String path, final String token, final ObjectNode body)
            throws IOException, InterruptedException {
        final HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)));
        if (token != null) {
            request.header("Authorization", "Snowflake Token=\"" + token + "\"");
        }
        return MAPPER.readTree(CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString()).body());
    }

    private static JsonNode get(final String path, final String token) throws IOException, InterruptedException {
        final HttpRequest request = HttpRequest.newBuilder(URI.create(base + path))
            .header("Authorization", "Snowflake Token=\"" + token + "\"").GET().build();
        return MAPPER.readTree(CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).body());
    }

    private static String login() throws IOException, InterruptedException {
        final ObjectNode body = MAPPER.createObjectNode();
        body.putObject("data").put("LOGIN_NAME", "any_user").put("PASSWORD", "any_password");
        final JsonNode answer = post("/session/v1/login-request", null, body);
        assertTrue(answer.path("success").asBoolean(), answer.toString());
        return answer.path("data").path("token").asString();
    }

    /** A statement's answer's {@code data}, the statement having succeeded. */
    private static JsonNode run(final String token, final String sql, final int statements)
            throws IOException, InterruptedException {
        final ObjectNode body = MAPPER.createObjectNode();
        body.put("sqlText", sql);
        body.put("sequenceId", 1);
        if (statements != 1) {
            body.putObject("parameters").put("MULTI_STATEMENT_COUNT", statements);
        }
        final JsonNode answer = post("/queries/v1/query-request?requestId=" + UUID.randomUUID(), token, body);
        assertTrue(answer.path("success").asBoolean(), sql + ": " + answer);
        return answer.path("data");
    }

    /** The format an answer took, with the rows it carries in that format. */
    private static String format(final JsonNode data) {
        final String format = data.path("queryResultFormat").asString();
        if ("arrow".equals(format)) {
            assertFalse(data.has("rowset"), data.toString());
        } else {
            assertTrue(data.path("rowset").isArray(), data.toString());
            assertFalse(data.has("rowsetBase64"), data.toString());
        }
        return format;
    }

    @Test
    public void onlyAQuerysRowsGoBackAsArrow() throws IOException, InterruptedException {
        final String token = login();
        assertEquals("json", format(run(token, "CREATE OR REPLACE DATABASE fmt_db", 1)));
        assertEquals("json", format(run(token, "USE SCHEMA fmt_db.public", 1)));
        assertEquals("json", format(run(token, "CREATE TABLE fmt_t (id INT, name VARCHAR)", 1)));
        final JsonNode inserted = run(token, "INSERT INTO fmt_t VALUES (1, 'a'), (2, 'b')", 1);
        assertEquals("json", format(inserted));
        assertEquals("[[\"2\"]]", inserted.path("rowset").toString());
        assertEquals("{\"numRowsInserted\":2,\"numRowsDeleted\":0,\"numRowsUpdated\":0,\"numDmlDuplicates\":0}",
            inserted.path("stats").toString());
        final JsonNode updated = run(token, "UPDATE fmt_t SET name = 'z' WHERE id = 2", 1);
        assertEquals("json", format(updated));
        assertEquals(1, updated.path("stats").path("numRowsUpdated").asInt());
        final JsonNode unchanged = run(token, "UPDATE fmt_t SET name = 'q' WHERE FALSE", 1);
        assertEquals("json", format(unchanged));
        assertFalse(unchanged.has("stats"), unchanged.toString());
        final JsonNode deleted = run(token, "DELETE FROM fmt_t WHERE id = 2", 1);
        assertEquals(1, deleted.path("stats").path("numRowsDeleted").asInt());
        final JsonNode queried = run(token, "SELECT * FROM fmt_t ORDER BY id", 1);
        assertEquals("arrow", format(queried));
        assertFalse(queried.has("stats"), queried.toString());
        assertEquals("arrow", format(run(token, "EXECUTE IMMEDIATE 'SELECT 2 AS two'", 1)));
        assertEquals("json", format(run(token, "SHOW TABLES LIKE 'FMT_T'", 1)));
        assertEquals("json", format(run(token, "DESCRIBE TABLE fmt_t", 1)));
        assertEquals("json", format(run(token, "EXPLAIN SELECT * FROM fmt_t", 1)));
        assertEquals("json", format(run(token, "EXECUTE IMMEDIATE $$BEGIN RETURN 5; END$$", 1)));
        run(token, "CREATE OR REPLACE PROCEDURE fmt_p() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'", 1);
        assertEquals("json", format(run(token, "CALL fmt_p()", 1)));
        assertEquals("json", format(run(token, "SET fmt_var = 1", 1)));
        // A session that asks for JSON takes a query's rows that way too.
        run(token, "ALTER SESSION SET JDBC_QUERY_RESULT_FORMAT = 'JSON'", 1);
        assertEquals("json", format(run(token, "SELECT * FROM fmt_t ORDER BY id", 1)));
        run(token, "DROP DATABASE fmt_db", 1);
    }

    @Test
    public void eachResultOfSeveralGoesBackAsJson() throws IOException, InterruptedException {
        final String token = login();
        run(token, "CREATE OR REPLACE DATABASE fmt_db2", 1);
        run(token, "CREATE TABLE fmt_db2.public.m (id INT)", 1);
        final JsonNode parent = run(token, "SELECT 1 AS one; INSERT INTO fmt_db2.public.m VALUES (1), (2)", 2);
        final String[] ids = parent.path("resultIds").asString().split(",");
        assertEquals(2, ids.length);
        final JsonNode first = get("/queries/" + ids[0] + "/result", token).path("data");
        assertEquals("json", format(first));
        final JsonNode second = get("/queries/" + ids[1] + "/result", token).path("data");
        assertEquals("json", format(second));
        assertEquals(2, second.path("stats").path("numRowsInserted").asInt());
        run(token, "DROP DATABASE fmt_db2", 1);
    }
}
