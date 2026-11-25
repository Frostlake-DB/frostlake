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

import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A create over a name another kind of object holds, refused as {@code Object 'V' already exists as VIEW}, reaches the
 * driver as 1998 / 42710, and a block's handler reads the same pair; a name the same kind holds, and a rename onto any
 * taken name, stays 2002 / 42710 (all live-verified).
 */
public class OtherKindNameErrorCodeTest extends BaseJdbcTest {

    @Override
    protected void setupTest() throws SQLException {
        statement.execute("CREATE OR REPLACE TABLE okn_t (x INT)");
        statement.execute("CREATE OR REPLACE VIEW okn_v AS SELECT 1 AS x");
        statement.execute("CREATE OR REPLACE EVENT TABLE okn_et");
        statement.execute("CREATE OR REPLACE STREAM okn_st ON TABLE okn_t");
        statement.execute("CREATE OR REPLACE PROCEDURE okn_p() RETURNS INT LANGUAGE SQL AS"
            + " 'BEGIN RETURN 1; END'");
        statement.execute("CREATE OR REPLACE FUNCTION okn_f() RETURNS INT AS '1'");
    }

    /** Each statement fails with the code and the state beside it, read as {@code "code state"}. */
    private void assertFamilies(final String[][] cases) throws SQLException {
        for (final String[] failing : cases) {
            try (final Statement s = connection.createStatement()) {
                s.execute(failing[0]);
                fail("no failure: " + failing[0]);
            } catch (final SQLException refused) {
                assertEquals(failing[1], refused.getErrorCode() + " " + refused.getSQLState(), failing[0]);
            }
        }
    }

    @Test
    public void anotherKindsNameIs1998() throws SQLException {
        assertFamilies(new String[][] {
            {"CREATE TABLE okn_v (x INT)", "1998 42710"},
            {"CREATE TABLE IF NOT EXISTS okn_v (x INT)", "1998 42710"},
            {"CREATE VIEW okn_t AS SELECT 1 AS x", "1998 42710"},
            {"CREATE OR REPLACE VIEW okn_t AS SELECT 1 AS x", "1998 42710"},
            {"CREATE MATERIALIZED VIEW okn_t AS SELECT x FROM okn_t", "1998 42710"},
            {"CREATE TABLE okn_et (x INT)", "1998 42710"},
            {"CREATE OR REPLACE TABLE okn_et (x INT)", "1998 42710"},
            {"CREATE EVENT TABLE okn_t", "1998 42710"},
            {"CREATE STREAM okn_t ON TABLE okn_t", "1998 42710"},
            {"CREATE TABLE okn_st (x INT)", "1998 42710"},
            {"CREATE FUNCTION okn_p() RETURNS INT AS '1'", "1998 42710"},
            {"CREATE PROCEDURE okn_f() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'", "1998 42710"},
            {"CREATE TABLE okn_t (x INT)", "2002 42710"},
            {"ALTER TABLE okn_t RENAME TO okn_v", "2002 42710"},
            {"ALTER VIEW okn_v RENAME TO okn_t", "2002 42710"},
        });
    }

    @Test
    public void aHandlerReadsTheSamePair() throws SQLException {
        try (final Statement s = connection.createStatement()) {
            s.execute("EXECUTE IMMEDIATE $$ BEGIN CREATE TABLE okn_v (x INT); RETURN 'none'; EXCEPTION WHEN OTHER THEN"
                + " RETURN SQLCODE || ' ' || SQLSTATE; END; $$");
            s.getResultSet().next();
            assertEquals("1998 42710", s.getResultSet().getString(1));
        }
    }
}
