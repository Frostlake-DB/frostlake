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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Only a POST opens a session or runs anything on the Snowflake driver's endpoints: a GET - what a page can make of
 * any address it names, an image's source for one - is answered 405 and changes nothing. The endpoints that only read
 * a query's status or result take a GET, as the driver sends them. Engine only: the server under test is the local
 * one.
 */
public class SnowflakeEndpointMethodTest {

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
        server = new DatabaseHttpServer(config);
        server.start();
    }

    @AfterAll
    public static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    private static HttpResponse<String> send(final String method, final String path) throws Exception {
        final HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .method(method, "GET".equals(method) ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString("{\"data\":{}}"))
            .header("Content-Type", "application/json").build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    public void onlyAPostLogsIn() throws Exception {
        final int sessions = server.getEngine().getActiveSessionCount();
        assertEquals(405, send("GET", "/session/v1/login-request?roleName=PUBLIC").statusCode());
        assertEquals(405, send("PUT", "/session/v1/login-request").statusCode());
        assertEquals(sessions, server.getEngine().getActiveSessionCount(), "no session opened");
        assertEquals(200, send("POST", "/session/v1/login-request").statusCode());
        assertEquals(sessions + 1, server.getEngine().getActiveSessionCount());
    }

    @Test
    public void theOtherEndpointsThatChangeStateTakeOnlyAPost() throws Exception {
        for (final String path : new String[] {"/queries/v1/query-request", "/session?delete=true",
                "/session/heartbeat", "/session/token-request", "/queries/v1/abort-request", "/telemetry/send"}) {
            assertEquals(405, send("GET", path).statusCode(), path);
        }
        // What only reads is served on a GET: here an unknown session's answer.
        assertEquals(200, send("GET", "/monitoring/queries/q1").statusCode());
        assertEquals(200, send("GET", "/queries/q1/result").statusCode());
    }
}
