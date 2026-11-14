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
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * An HTTP server started on port 0 takes a free port and reports it, so servers in one JVM - or in parallel test
 * forks - never collide: two such servers each answer the health check and the Snowflake driver's connection on the
 * port {@link DatabaseHttpServer#getPort} names. Engine only: the servers under test are local.
 */
public class HttpServerFreePortTest {

    @Test
    public void portZeroTakesAFreePortAndReportsIt() throws IOException, SQLException {
        assumeFalse(LiveSnowflake.enabled(), "the servers under test are local");
        final DatabaseHttpServer first = new DatabaseHttpServer(onFreePort());
        final DatabaseHttpServer second = new DatabaseHttpServer(onFreePort());
        first.start();
        second.start();
        try {
            assertTrue(first.getPort() > 0, String.valueOf(first.getPort()));
            assertTrue(second.getPort() > 0, String.valueOf(second.getPort()));
            assertNotEquals(first.getPort(), second.getPort());
            for (final DatabaseHttpServer server : new DatabaseHttpServer[] {first, second}) {
                assertEquals(200, health(server.getPort()));
                try (Connection connection = snowflake(server.getPort());
                     Statement statement = connection.createStatement();
                     ResultSet rs = statement.executeQuery("SELECT 41 + 1")) {
                    assertTrue(rs.next());
                    assertEquals(42, rs.getInt(1));
                }
            }
            // The port the engine names this server by in the URLs it hands out is the one it took.
            assertEquals(String.valueOf(first.getPort()), first.getEngine().getEngine().getConfig()
                .getProperty(EngineConfig.PROP_HTTP_PORT, ""));
        } finally {
            first.stop();
            second.stop();
        }
    }

    @Test
    public void aServerOnAPortItNamesReportsThatPort() throws IOException {
        assumeFalse(LiveSnowflake.enabled(), "the server under test is local");
        final int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        final DatabaseHttpServer server = new DatabaseHttpServer(port);
        server.start();
        try {
            assertEquals(port, server.getPort());
            assertEquals(200, health(port));
        } finally {
            server.stop();
        }
    }

    private static EngineConfig onFreePort() {
        final EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_HTTP_PORT, "0");
        return config;
    }

    private static int health(final int port) throws IOException {
        final HttpURLConnection connection = (HttpURLConnection) URI.create("http://127.0.0.1:" + port
            + "/api/health").toURL().openConnection();
        try (InputStream in = connection.getInputStream()) {
            final String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(body.contains("healthy"), body);
        }
        return connection.getResponseCode();
    }

    private static Connection snowflake(final int port) throws SQLException {
        final Properties properties = new Properties();
        properties.put("user", "any_user");
        properties.put("password", "any_password");
        properties.put("disablePlatformDetection", "true");
        return DriverManager.getConnection("jdbc:snowflake://127.0.0.1:" + port + "/?account=frostlake&ssl=off",
            properties);
    }
}
