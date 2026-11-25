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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A statement that fails among several of one request reaches the client's driver with the multi-statement helper's
 * code and state, 100132 / P0000, whatever it failed with - a compilation error, a runtime error, a block's uncaught
 * exception - and wherever it sits in the request; a block's handler reads the same pair as SQLCODE and SQLSTATE,
 * and a block that lets such a failure out uncaught carries it too. A request that does not parse, and a statement
 * sent on its own, keep their own pair (all live-verified).
 */
public class MultiStatementFailureCodeTest extends BaseJdbcTest {

    /** Each request fails with the code and the state beside it, read as {@code "code state"}. */
    private void assertFamilies(final String[][] cases) throws SQLException {
        statement.execute("ALTER SESSION SET MULTI_STATEMENT_COUNT = 0");
        try {
            for (final String[] failing : cases) {
                try (final Statement s = connection.createStatement()) {
                    s.execute(failing[0]);
                    fail("no failure: " + failing[0]);
                } catch (final SQLException refused) {
                    assertEquals(failing[1], refused.getErrorCode() + " " + refused.getSQLState(), failing[0]);
                }
            }
        } finally {
            statement.execute("ALTER SESSION UNSET MULTI_STATEMENT_COUNT");
        }
    }

    /** Each block answers the text beside it. */
    private void assertAnswers(final String[][] cases) throws SQLException {
        statement.execute("ALTER SESSION SET MULTI_STATEMENT_COUNT = 0");
        try {
            for (final String[] block : cases) {
                try (final Statement s = connection.createStatement()) {
                    assertTrue(s.execute(block[0]), block[0]);
                    try (final ResultSet rs = s.getResultSet()) {
                        assertTrue(rs.next(), block[0]);
                        assertEquals(block[1], rs.getString(1), block[0]);
                    }
                }
            }
        } finally {
            statement.execute("ALTER SESSION UNSET MULTI_STATEMENT_COUNT");
        }
    }

    @Test
    public void aFailureAmongSeveralCarriesTheHelpersPair() throws SQLException {
        statement.execute("CREATE TABLE msf1 (a INT NOT NULL)");
        assertFamilies(new String[][] {
            {"SELECT nocol1 FROM msf1; SELECT 1", "100132 P0000"},
            {"SELECT 1; SELECT nocol2 FROM msf1; SELECT 2", "100132 P0000"},
            {"SELECT 1; SELECT 2; SELECT nocol3 FROM msf1", "100132 P0000"},
            {"SELECT 1; SELECT 1/0", "100132 P0000"},
            {"SELECT 1; SELECT 'abc'::INT", "100132 P0000"},
            {"SELECT 1; INSERT INTO msf1 VALUES (NULL)", "100132 P0000"},
            {"SELECT 1; SELECT no_such_fn_msf(1)", "100132 P0000"},
            {"CREATE TABLE msf1 (a INT); SELECT 1", "100132 P0000"},
            {"SELECT 1; SELECT * FROM no_such_table_msf", "100132 P0000"},
            {"EXECUTE IMMEDIATE 'SELECT 1; SELECT nocol6 FROM msf1'", "100132 P0000"},
        });
    }

    @Test
    public void aBlockAmongSeveralCarriesTheHelpersPair() throws SQLException {
        assertFamilies(new String[][] {
            {"SELECT 1; BEGIN\n  LET x INT := 1/0;\n  RETURN x;\nEND;", "100132 P0000"},
            {"SELECT 1; DECLARE\n  e EXCEPTION (-20001, 'boom');\nBEGIN\n  RAISE e;\nEND;", "100132 P0000"},
            {"BEGIN\n  SELECT * FROM no_such_table_msf;\nEND; SELECT 1", "100132 P0000"},
            {"BEGIN\n  EXECUTE IMMEDIATE 'SELECT 1; SELECT * FROM no_such_table_msf';\nEND;", "100132 P0000"},
        });
    }

    @Test
    public void aHandlerReadsTheHelpersPair() throws SQLException {
        assertAnswers(new String[][] {
            {"BEGIN\n  EXECUTE IMMEDIATE 'SELECT 1; SELECT * FROM no_such_table_msf';\n  RETURN 'none';\n"
                + "EXCEPTION\n  WHEN OTHER THEN RETURN sqlcode || '/' || sqlstate;\nEND;", "100132/P0000"},
            {"BEGIN\n  EXECUTE IMMEDIATE 'SELECT 1; SELECT 1/0';\n  RETURN 'none';\n"
                + "EXCEPTION\n  WHEN OTHER THEN RETURN sqlcode || '/' || sqlstate;\nEND;", "100132/P0000"},
            {"BEGIN\n  EXECUTE IMMEDIATE 'SELECT 1; BEGIN SELECT * FROM no_such_table_msf; END;';\n  RETURN 'none';\n"
                + "EXCEPTION\n  WHEN OTHER THEN RETURN sqlcode || '/' || sqlstate;\nEND;", "100132/P0000"},
        });
    }

    @Test
    public void aSyntaxErrorOrASingleStatementKeepsItsOwnPair() throws SQLException {
        assertFamilies(new String[][] {
            {"SELEC 1; SELECT 2", "1003 42000"},
            {"SELECT 1; SELEC 2", "1003 42000"},
            {"SELECT * FROM no_such_table_msf", "2003 42S02"},
        });
    }
}
