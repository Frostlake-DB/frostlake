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
package dev.frostlake;

import dev.frostlake.http.SnowflakeEndpoint;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Properties;

/**
 * The Snowflake-driver-over-Frostlake test switch. With {@code SF_JDBC_FL=1} in the environment, the test base
 * classes and the corpus send their SQL through Snowflake's own JDBC driver to a {@link SnowflakeEndpoint} serving
 * the test's engine — the same suite, flipped by a switch, auditing the endpoint and how the account's driver reads
 * what it answers: the protocol, the result metadata, the values and the refusals. {@code SF_LIVE} takes precedence,
 * then this switch, then {@code FL_JDBC}.
 *
 * <p>Results arrive in JSON, as the live harness asks for them; {@code SF_JDBC_FL_RESULTS=ARROW} asks for the
 * driver's default Arrow results instead.
 */
public final class SnowflakeJdbcFl {

    private SnowflakeJdbcFl() {
    }

    /** Whether the mode is on ({@code SF_JDBC_FL=1} and not overridden by {@code SF_LIVE}). */
    public static boolean enabled() {
        return "1".equals(System.getenv("SF_JDBC_FL")) && !LiveSnowflake.enabled();
    }

    /**
     * A new connection of Snowflake's driver to an endpoint (the caller closes it), its role the engine's own session's
     * until it runs a USE ROLE — a login role would give it roles of its own — its results in JSON
     * unless {@code SF_JDBC_FL_RESULTS=ARROW} asks for Arrow, and its semi-structured values compact, as the live
     * harness sets its sessions: {@code JSON_INDENT = 0}, where the account and the endpoint lay them out at two
     * spaces.
     *
     * @param endpoint the endpoint
     * @return the connection
     * @throws SQLException when the driver cannot log in
     */
    public static Connection connect(final SnowflakeEndpoint endpoint) throws SQLException {
        final Properties properties = new Properties();
        properties.put("user", "frostlake");
        properties.put("password", "frostlake");
        final String format = System.getenv("SF_JDBC_FL_RESULTS");
        final String results = format == null || format.isBlank() ? "JSON" : format.trim().toUpperCase(Locale.ROOT);
        // The driver matches its property names in the default locale, which a test may have moved (a Turkish
        // locale upper-cases an i to a dotted I), so the login runs in the root locale.
        final Locale locale = Locale.getDefault();
        final Connection connection;
        Locale.setDefault(Locale.ROOT);
        try {
            connection = DriverManager.getConnection(endpoint.jdbcUrl() + "&JDBC_QUERY_RESULT_FORMAT=" + results,
                properties);
        } finally {
            Locale.setDefault(locale);
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER SESSION SET JSON_INDENT = 0");
        } catch (final SQLException refused) {
            connection.close();
            throw refused;
        }
        return connection;
    }

    /**
     * Closes an endpoint once its driver sessions are closed, on a thread named as a server's, so a statement the stop
     * runs reaches the engine directly, and waits for it.
     *
     * @param endpoint the endpoint
     */
    public static void closeOnServerThread(final SnowflakeEndpoint endpoint) {
        final Thread closing = new Thread(new Runnable() {
            @Override
            public void run() {
                endpoint.close();
            }
        }, "frostlake-http-closing-" + endpoint.getPort());
        closing.setDaemon(true);
        closing.start();
        try {
            closing.join();
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
