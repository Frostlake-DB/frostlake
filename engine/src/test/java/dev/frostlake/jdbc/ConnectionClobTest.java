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

import dev.frostlake.BaseJdbcTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.io.Writer;
import java.sql.Clob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * A connection makes a CLOB, as the account's driver does: it starts empty, is written through {@code setString}
 * or a stream, and binds its text with {@code setClob}. A statement given no text at all is refused with a
 * {@link SQLException}; the account's driver fails there with a NullPointerException of its own, which is not
 * imitated.
 */
public class ConnectionClobTest extends BaseJdbcTest {

    @Test
    public void aNewClobStartsEmptyAndTakesText() throws SQLException {
        final Clob clob = connection.createClob();
        assertEquals(0L, clob.length());
        assertEquals(5, clob.setString(1, "hello"));
        assertEquals(5L, clob.length());
    }

    @Test
    public void aClobIsRewrittenSearchedAndTruncated() throws SQLException, IOException {
        assumeFalse(isSnowflakeDriver(), "the account driver's CLOB keeps its own rules for these calls");
        final Clob clob = connection.createClob();
        clob.setString(1, "hello");
        assertEquals("hello", clob.getSubString(1, 5));
        clob.setString(6, " world");
        clob.setString(7, "there");
        assertEquals("hello there", clob.getSubString(1, 20));
        assertEquals(7L, clob.position("there", 1));
        assertEquals(-1L, clob.position("absent", 1));
        try (Writer writer = clob.setCharacterStream(clob.length() + 1)) {
            writer.write("!!");
        }
        assertEquals("hello there!!", clob.getSubString(1, 20));
        clob.truncate(5);
        assertEquals("hello", clob.getSubString(1, 20));
        final SQLException outside = assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                clob.setString(9, "x");
            }
        });
        assertTrue(outside.getMessage().contains("Invalid position 9"), outside.getMessage());
        clob.free();
        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                clob.length();
            }
        });
    }

    @Test
    public void aClobBindsItsText() throws SQLException {
        assumeFalse(isSnowflakeDriver(), "the bind is this driver's");
        final Clob clob = connection.createClob();
        clob.setString(1, "bound text");
        try (PreparedStatement prepared = connection.prepareStatement("SELECT ?")) {
            prepared.setClob(1, clob);
            try (ResultSet rows = prepared.executeQuery()) {
                assertTrue(rows.next());
                assertEquals("bound text", rows.getString(1));
            }
        }
    }

    @Test
    public void aStatementGivenNoTextIsRefused() {
        assumeFalse(isSnowflakeDriver(), "the account's driver fails with a NullPointerException of its own");
        final SQLException refused = assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                statement.execute(null);
            }
        });
        assertEquals("Invalid SQL text: null", refused.getMessage());
        assertEquals("03000", refused.getSQLState());
    }
}
