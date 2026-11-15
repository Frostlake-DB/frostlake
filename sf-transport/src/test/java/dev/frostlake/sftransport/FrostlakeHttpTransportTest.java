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
import dev.frostlake.jdbc.DatabaseDriver;
import net.snowflake.client.api.http.HttpTransport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Snowflake's JDBC driver reaching an embedded engine through the transport, no socket opened. */
public class FrostlakeHttpTransportTest {

    private static final Logger logger = LoggerFactory.getLogger(FrostlakeHttpTransportTest.class);

    /** The options every URL carries: platform detection would probe cloud metadata addresses over the network. */
    private static final String OPTIONS = "/?account=frostlake&disablePlatformDetection=true";

    private final List<String> directNames = new ArrayList<String>();

    @AfterEach
    public void releaseEngines() {
        for (final String name : directNames) {
            SnowflakeDirectEndpoint.direct(name).close();
            final DatabaseEngine engine = DatabaseDriver.unregisterDirectEngine(name);
            if (engine != null) {
                engine.shutdown();
            }
        }
    }

    @Test
    public void theTransportIsRegisteredUnderItsName() {
        boolean found = false;
        for (final HttpTransport transport : ServiceLoader.load(HttpTransport.class)) {
            found |= transport instanceof FrostlakeHttpTransport
                && FrostlakeHttpTransport.NAME.equals(transport.getName());
        }
        assertTrue(found);
    }

    @Test
    public void aConnectionUrlReachesTheDirectEngineItsHostNames() throws SQLException {
        final String name = directName();
        try (Connection connection = DriverManager.getConnection(
                "jdbc:snowflake://" + name + OPTIONS + "&httpTransport=frostlake", credentials());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE readings (id INT, label VARCHAR)");
            statement.execute("INSERT INTO readings VALUES (1, 'one'), (2, 'two')");
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO readings VALUES (?, ?)")) {
                insert.setInt(1, 3);
                insert.setString(2, "three");
                assertEquals(1, insert.executeUpdate());
            }
            try (ResultSet rows = statement.executeQuery("SELECT SUM(id), LISTAGG(label, ',') WITHIN GROUP"
                    + " (ORDER BY id) FROM readings")) {
                assertTrue(rows.next());
                assertEquals(6, rows.getInt(1));
                assertEquals("one,two,three", rows.getString(2));
            }
            final DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet tables = metaData.getTables(null, null, "READINGS", null)) {
                assertTrue(tables.next());
                assertEquals("READINGS", tables.getString("TABLE_NAME"));
            }
        }
        try (Connection direct = DriverManager.getConnection("jdbc:frostlake:direct:" + name);
             Statement statement = direct.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM readings")) {
            assertTrue(rows.next());
            assertEquals(3, rows.getInt(1));
        }
    }

    @Test
    public void theHostNamesTheEngineAsTheUrlWritesIt() throws SQLException {
        final String name = directName("MixedCase");
        try (Connection direct = DriverManager.getConnection("jdbc:frostlake:direct:" + name);
             Statement statement = direct.createStatement()) {
            statement.execute("CREATE TABLE marker (n INT)");
        }
        try (Connection connection = DriverManager.getConnection(
                "jdbc:snowflake://" + name + OPTIONS + "&httpTransport=frostlake", credentials());
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM marker")) {
            assertTrue(rows.next());
            assertEquals(0, rows.getInt(1));
        }
    }

    @Test
    public void arrowAndJsonResultsReadAlike() throws SQLException {
        final String name = directName();
        final String select = "SELECT 42::NUMBER(10, 0) AS n, 12.34::NUMBER(12, 2) AS d, 'text' AS s, 1.5::FLOAT AS f,"
            + " '2026-11-15'::DATE AS day, '2026-11-15 10:11:12.123'::TIMESTAMP_NTZ(3) AS ts, TRUE AS b";
        final List<String> arrow = row(name, "", select);
        final List<String> json = row(name, "&JDBC_QUERY_RESULT_FORMAT=JSON", select);

        assertEquals(json, arrow);
        assertEquals("42", arrow.get(0));
        assertEquals("12.34", arrow.get(1));
        assertEquals("text", arrow.get(2));
        assertEquals("2026-11-15", arrow.get(4));
    }

    @Test
    public void aTransportMadeOverAnEngineAnswersForItWhateverTheHost() throws SQLException {
        final DatabaseEngine engine = new DatabaseEngine();
        try (FrostlakeHttpTransport transport = new FrostlakeHttpTransport(engine)) {
            final Properties properties = credentials();
            properties.put("httpTransport", transport);
            try (Connection connection = DriverManager.getConnection("jdbc:snowflake://first.invalid" + OPTIONS,
                    properties);
                 Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE own (n INT)");
                statement.execute("INSERT INTO own VALUES (7)");
            }
            try (Connection connection = DriverManager.getConnection("jdbc:snowflake://second.invalid" + OPTIONS,
                    properties);
                 Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("SELECT n FROM own")) {
                assertTrue(rows.next());
                assertEquals(7, rows.getInt(1));
                assertFalse(rows.next());
            }
            final dev.frostlake.storage.ResultSet counted = engine.executeQuery("SELECT COUNT(*) FROM own");
            assertTrue(counted.next());
            assertEquals(1L, ((Number) counted.getValue(0)).longValue());
        } finally {
            engine.shutdown();
        }
    }

    @Test
    public void aRefusalReachesTheDriverWithItsCodeAndState() throws SQLException {
        final String name = directName();
        try (Connection connection = DriverManager.getConnection(
                "jdbc:snowflake://" + name + OPTIONS + "&httpTransport=frostlake", credentials());
             Statement statement = connection.createStatement()) {
            final SQLException refusal = assertThrows(SQLException.class, new Executable() {
                @Override
                public void execute() throws SQLException {
                    statement.executeQuery("SELECT no_such_column FROM (SELECT 1 AS n)");
                }
            });
            logger.debug("Refused: {}", refusal.getMessage());
            assertEquals("42000", refusal.getSQLState());
            assertEquals(904, refusal.getErrorCode());
        }
    }

    private static List<String> row(final String name, final String options, final String select)
            throws SQLException {
        final List<String> values = new ArrayList<String>();
        try (Connection connection = DriverManager.getConnection(
                "jdbc:snowflake://" + name + OPTIONS + "&httpTransport=frostlake" + options, credentials());
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(select)) {
            assertTrue(rows.next());
            for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) {
                values.add(rows.getString(i));
            }
        }
        return values;
    }

    private String directName() {
        return directName("fl");
    }

    /** A direct engine's name that is also a host name: letters and digits, a letter first. */
    private String directName(final String prefix) {
        final String name = prefix + UUID.randomUUID().toString().replace("-", "");
        directNames.add(name);
        return name;
    }

    private static Properties credentials() {
        final Properties properties = new Properties();
        properties.put("user", "frostlake");
        properties.put("password", "frostlake");
        return properties;
    }
}
