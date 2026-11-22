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

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The name arguments of {@code DatabaseMetaData}: a pattern honours {@code getSearchStringEscape()} — {@code \_}
 * and {@code \%} match the character itself, and a pattern ending in a lone escape matches nothing — and every
 * name, pattern or exact, is compared case-sensitively against the name as stored, so a quoted {@code "mixed"}
 * table and an unquoted {@code MIXED} one never answer for each other (live-verified through the account's
 * driver).
 */
public class MetadataPatternMatchingTest extends BaseJdbcTest {

    @Override
    protected void setupTest() throws SQLException {
        statement.execute("CREATE TABLE A_B (id INT)");
        statement.execute("CREATE TABLE AXB (id INT)");
        statement.execute("CREATE TABLE PT (N38 INT, N_1 INT)");
        statement.execute("CREATE TABLE \"mixed\" (id INT PRIMARY KEY)");
        statement.execute("CREATE TABLE MIXED (id2 INT PRIMARY KEY)");
        statement.execute("CREATE FUNCTION FN_A() RETURNS INT AS '1'");
        statement.execute("CREATE FUNCTION FNXA() RETURNS INT AS '2'");
    }

    /** One column of a metadata answer, its values joined with commas. */
    private static String column(final ResultSet rs, final String label) throws SQLException {
        final StringBuilder out = new StringBuilder();
        try (rs) {
            while (rs.next()) {
                out.append(out.length() == 0 ? "" : ",").append(rs.getString(label));
            }
        }
        return out.toString();
    }

    private String tables(final String catalog, final String schema, final String pattern) throws SQLException {
        final DatabaseMetaData md = connection.getMetaData();
        return column(md.getTables(catalog, schema, pattern, null), "TABLE_NAME");
    }

    private String tables(final String pattern) throws SQLException {
        return tables("TEST_DB", "PUBLIC", pattern);
    }

    /** An escaped underscore matches only an underscore; an unescaped one matches any character. */
    @Test
    public void anEscapedUnderscoreMatchesItself() throws SQLException {
        assertEquals("A_B", tables("A\\_B"));
        assertEquals("AXB,A_B", tables("A_B"));
    }

    /**
     * A pattern ending in a lone escape matches nothing rather than failing. Snowflake's driver hands such a
     * pattern to the server inside a SHOW ... LIKE, which the server refuses as a parse error; JDBC leaves the case
     * undefined, and Frostlake's drivers answer it as the name no object has.
     */
    @Test
    public void aTrailingEscapeMatchesNothing() throws SQLException {
        if (isSnowflakeDriver()) {
            assertThrows(SQLException.class, new Executable() {
                @Override
                public void execute() throws SQLException {
                    tables("A\\");
                }
            });
            return;
        }
        assertEquals("", tables("A\\"));
    }

    /** Column patterns honour the escape too. */
    @Test
    public void columnPatternsHonourTheEscape() throws SQLException {
        final DatabaseMetaData md = connection.getMetaData();
        assertEquals("N_1", column(md.getColumns("TEST_DB", "PUBLIC", "PT", "N\\_%"), "COLUMN_NAME"));
        assertEquals("N38,N_1", column(md.getColumns("TEST_DB", "PUBLIC", "PT", "N_%"), "COLUMN_NAME"));
    }

    /** Function patterns honour the escape too. */
    @Test
    public void functionPatternsHonourTheEscape() throws SQLException {
        final DatabaseMetaData md = connection.getMetaData();
        assertEquals("FN_A", column(md.getFunctions("TEST_DB", "PUBLIC", "FN\\_A"), "FUNCTION_NAME"));
    }

    /** A table name pattern is compared case-sensitively. */
    @Test
    public void tableNamesAreCaseSensitive() throws SQLException {
        assertEquals("mixed", tables("mixed"));
        assertEquals("MIXED", tables("MIXED"));
        assertEquals("", tables("Mixed"));
        assertEquals("", tables("a\\_b"));
    }

    /** The columns of a quoted lower-case table are its own only. */
    @Test
    public void columnsOfAQuotedTableAreItsOwn() throws SQLException {
        final DatabaseMetaData md = connection.getMetaData();
        assertEquals("ID", column(md.getColumns("TEST_DB", "PUBLIC", "mixed", "%"), "COLUMN_NAME"));
        assertEquals("ID2", column(md.getColumns("TEST_DB", "PUBLIC", "MIXED", null), "COLUMN_NAME"));
        assertEquals("", column(md.getColumns("TEST_DB", "PUBLIC", "PT", "n38"), "COLUMN_NAME"));
    }

    /** getPrimaryKeys takes exact names, compared case-sensitively. */
    @Test
    public void primaryKeysTakeExactNames() throws SQLException {
        final DatabaseMetaData md = connection.getMetaData();
        assertEquals("ID", column(md.getPrimaryKeys("TEST_DB", "PUBLIC", "mixed"), "COLUMN_NAME"));
        assertEquals("ID2", column(md.getPrimaryKeys("TEST_DB", "PUBLIC", "MIXED"), "COLUMN_NAME"));
        assertEquals("", column(md.getPrimaryKeys("TEST_DB", "PUBLIC", "Mixed"), "COLUMN_NAME"));
    }

    /** A schema pattern and a catalog are compared case-sensitively as well. */
    @Test
    public void schemaAndCatalogAreCaseSensitive() throws SQLException {
        assertEquals("", tables("TEST_DB", "public", "A\\_B"));
        assertEquals("", tables("test_db", "PUBLIC", "A\\_B"));
    }
}
