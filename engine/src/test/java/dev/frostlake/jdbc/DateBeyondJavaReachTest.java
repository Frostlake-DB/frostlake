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

package dev.frostlake.jdbc;

import dev.frostlake.http.DatabaseHttpServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Calendar;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A DATE past what a java.sql.Date's long of milliseconds reaches (about year 292,278,994) is refused by getDate,
 * getTimestamp and getObject, each pointing to getString, which reads it right; a Calendar would wrap it to another
 * date without a word. Only this engine keeps such a date - the account wraps it on the server - so the class runs on
 * Frostlake's own transports only. A date within reach still reads as before.
 */
public class DateBeyondJavaReachTest {

    private static final String FAR = "SELECT DATE_FROM_PARTS(999999999, 12, 31) AS d, DATE_FROM_PARTS(292278995, 1, 1)"
        + " AS e, DATE_FROM_PARTS(2026, 11, 15) AS n";

    @Test
    public void bothTransportsRefuseADateBeyondReachAndReadTheRest() throws SQLException, IOException {
        try (Connection direct = DriverManager.getConnection("jdbc:frostlake:direct:date_beyond_java_reach")) {
            assertRefusedBeyondReach(direct);
        }
        final int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        final DatabaseHttpServer server = new DatabaseHttpServer(port);
        server.start();
        try (Connection http = DriverManager.getConnection("jdbc:frostlake://localhost:" + port)) {
            assertRefusedBeyondReach(http);
        } finally {
            server.stop();
        }
    }

    private static void assertRefusedBeyondReach(final Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(FAR)) {
            rows.next();
            assertEquals("999999999-12-31", rows.getString(1));
            assertEquals("292278995-01-01", rows.getString(2));
            final Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            for (final int column : new int[] {1, 2}) {
                final String sentence = "The value " + rows.getString(column) + " is beyond the reach of a ";
                assertEquals(sentence + "java.sql.Date; read it with getString.", refusal(new Executable() {
                    @Override
                    public void execute() throws SQLException {
                        rows.getDate(column);
                    }
                }));
                assertEquals(sentence + "java.sql.Date; read it with getString.", refusal(new Executable() {
                    @Override
                    public void execute() throws SQLException {
                        rows.getDate(column, utc);
                    }
                }));
                assertEquals(sentence + "java.sql.Date; read it with getString.", refusal(new Executable() {
                    @Override
                    public void execute() throws SQLException {
                        rows.getObject(column);
                    }
                }));
                assertEquals(sentence + "java.sql.Timestamp; read it with getString.", refusal(new Executable() {
                    @Override
                    public void execute() throws SQLException {
                        rows.getTimestamp(column);
                    }
                }));
            }
            assertEquals("2026-11-15", rows.getDate(3).toString());
            assertEquals("2026-11-15", rows.getObject(3).toString());
        }
    }

    private static String refusal(final Executable read) {
        return assertThrows(SQLException.class, read).getMessage();
    }
}
