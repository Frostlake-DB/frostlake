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

package dev.frostlake.security;

import dev.frostlake.DatabaseEngine;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * SYSTEM$GET_TAG needs "the privileges to run a DESCRIBE <object> operation on the specified object name", and
 * TAG_REFERENCES returns results "only for a role that has access to the specified object" (documented): an object
 * the role cannot see is refused as DESCRIBE refuses it, where the lookups answered its tags and their values. An
 * object of a database the role cannot see is refused as that database, whatever its kind, a function and a
 * procedure aside, which are refused by their own words; a warehouse and a database role the role neither holds nor
 * sees are refused as missing, while a role and the account are answered (live-verified). Engine only: the live
 * account allows no CREATE ROLE, CREATE USER or GRANT.
 */
public class TagLookupVisibilityTest {

    private static final String UNSEEN = "SQL compilation error:\nTable 'D1.PRIV.SALARIES' does not exist or not "
        + "authorized. Your primary role R1 must have at least one privilege granted on TABLE D1.PRIV.SALARIES.";

    private static final String UNSEEN_DATABASE = "SQL compilation error:\nDatabase 'D1' does not exist or not "
        + "authorized. Your primary role R1 must have USAGE or any other privilege granted on DATABASE D1.";

    private DatabaseEngine engine;
    private String systemRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        systemRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE d1");
        engine.execute("CREATE SCHEMA d1.priv");
        engine.execute("CREATE TABLE d1.priv.salaries (name VARCHAR, salary NUMBER)");
        engine.execute("CREATE TAG d1.priv.cls");
        engine.execute("ALTER TABLE d1.priv.salaries SET TAG d1.priv.cls = 'restricted'");
        engine.execute("ALTER TABLE d1.priv.salaries MODIFY COLUMN salary SET TAG d1.priv.cls = 'pii'");
        engine.execute("CREATE ROLE r1");
        engine.execute("CREATE USER u1 DEFAULT_SECONDARY_ROLES = ()");
        engine.execute("GRANT ROLE r1 TO USER u1");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private void as(final String user, final String role) {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser(user);
        engine.setCurrentRole(role);
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql).getMessage();
    }

    private String cell(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void aTableOfADatabaseTheRoleCannotSeeIsRefusedAsTheDatabase() {
        as("U1", "R1");
        assertEquals(UNSEEN_DATABASE, refusal("SELECT SYSTEM$GET_TAG('d1.priv.cls', 'd1.priv.salaries', 'table')"));
        assertEquals(UNSEEN_DATABASE, refusal("SELECT SYSTEM$GET_TAG('d1.priv.cls', 'd1.priv.salaries.salary', "
            + "'column')"));
        assertEquals(UNSEEN_DATABASE, refusal("SELECT SYSTEM$GET_TAG('d1.priv.cls', 'd1.priv', 'schema')"));
        assertEquals(UNSEEN_DATABASE, refusal("SELECT SYSTEM$GET_TAG('d1.priv.cls', 'd1.priv.nosuch', 'stage')"));
    }

    @Test
    public void aTableTheRoleCannotSeeIsRefusedByEveryLookup() {
        engine.execute("GRANT USAGE ON DATABASE d1 TO ROLE r1");
        engine.execute("GRANT USAGE ON SCHEMA d1.priv TO ROLE r1");
        as("U1", "R1");
        assertEquals(UNSEEN, refusal("SELECT SYSTEM$GET_TAG('d1.priv.cls', 'd1.priv.salaries', 'table')"));
        assertEquals(UNSEEN, refusal("SELECT SYSTEM$GET_TAG('d1.priv.cls', 'd1.priv.salaries.salary', 'column')"));
        assertEquals(UNSEEN, refusal("SELECT * FROM TABLE(d1.information_schema.tag_references("
            + "'d1.priv.salaries', 'table'))"));
        assertEquals(UNSEEN, refusal("SELECT * FROM TABLE(d1.information_schema.tag_references_all_columns("
            + "'d1.priv.salaries', 'table'))"));
    }

    @Test
    public void aRoleThatSeesTheTableReadsItsTags() {
        engine.execute("GRANT USAGE ON DATABASE d1 TO ROLE r1");
        engine.execute("GRANT USAGE ON SCHEMA d1.priv TO ROLE r1");
        engine.execute("GRANT SELECT ON TABLE d1.priv.salaries TO ROLE r1");
        as("U1", "R1");
        assertEquals("restricted", cell("SELECT SYSTEM$GET_TAG('d1.priv.cls', 'd1.priv.salaries', 'table')"));
        assertEquals("pii", cell("SELECT SYSTEM$GET_TAG('d1.priv.cls', 'd1.priv.salaries.salary', 'column')"));
        as("SYSTEM", systemRole);
        assertEquals("restricted", cell("SELECT SYSTEM$GET_TAG('d1.priv.cls', 'd1.priv.salaries', 'table')"));
    }

    /** The objects of every kind the lookups resolve, in a database R1 does not see, and the tag in one it does. */
    private void hiddenObjects() {
        engine.execute("GRANT USAGE ON DATABASE d1 TO ROLE r1");
        engine.execute("GRANT USAGE ON SCHEMA d1.priv TO ROLE r1");
        engine.execute("CREATE DATABASE d2");
        engine.execute("CREATE TABLE d2.public.t (x INT)");
        engine.execute("CREATE STAGE d2.public.stg");
        engine.execute("CREATE STREAM d2.public.st ON TABLE d2.public.t");
        engine.execute("CREATE TASK d2.public.tk SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE ALERT d2.public.al SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN SELECT 1");
        engine.execute("CREATE PASSWORD POLICY d2.public.pp");
        engine.execute("CREATE SESSION POLICY d2.public.sp");
        engine.execute("CREATE FUNCTION d2.public.f(x NUMBER) RETURNS NUMBER AS 'x'");
        engine.execute("CREATE PROCEDURE d2.public.p() RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
        engine.execute("ALTER STAGE d2.public.stg SET TAG d1.priv.cls = 'v'");
        engine.execute("ALTER FUNCTION d2.public.f(NUMBER) SET TAG d1.priv.cls = 'v'");
    }

    @Test
    public void anObjectOfADatabaseTheRoleCannotSeeIsRefusedAsTheDatabase() {
        hiddenObjects();
        as("U1", "R1");
        final String database = "SQL compilation error:\nDatabase 'D2' does not exist or not authorized. Your "
            + "primary role R1 must have USAGE or any other privilege granted on DATABASE D2.";
        final String[][] objects = {
            {"d2.public.stg", "stage"}, {"d2.public.st", "stream"}, {"d2.public.tk", "task"},
            {"d2.public.al", "alert"}, {"d2.public.pp", "password policy"}, {"d2.public.sp", "session policy"},
            {"d2.public.stg", "pipe"}, {"d2.public.nosuch", "stage"}, {"d2.nosuch.x", "stage"},
            {"d2.public.t", "table"}, {"d2.public", "schema"}, {"d2.nosuch", "database role"},
        };
        for (final String[] object : objects) {
            assertEquals(database, refusal("SELECT SYSTEM$GET_TAG('d1.priv.cls', '" + object[0] + "', '"
                + object[1] + "')"));
        }
        assertEquals(database, refusal("SELECT * FROM TABLE(d1.information_schema.tag_references("
            + "'d2.public.stg', 'stage'))"));
    }

    @Test
    public void aRoutineOfADatabaseTheRoleCannotSeeIsRefusedByItsOwnWords() {
        hiddenObjects();
        as("U1", "R1");
        assertEquals("SQL compilation error:\nFunction 'd2.public.f(NUMBER)' does not exist or not authorized.",
            refusal("SELECT SYSTEM$GET_TAG('d1.priv.cls', 'd2.public.f(NUMBER)', 'function')"));
        assertEquals("SQL compilation error:\nProcedure 'd2.public.p()' does not exist or not authorized.",
            refusal("SELECT SYSTEM$GET_TAG('d1.priv.cls', 'd2.public.p()', 'procedure')"));
        assertEquals("SQL compilation error:\nFunction 'd2.public.f(NUMBER)' does not exist or not authorized.",
            refusal("SELECT * FROM TABLE(d1.information_schema.tag_references('d2.public.f(NUMBER)', "
                + "'function'))"));
    }

    @Test
    public void aTagOfADatabaseTheRoleCannotSeeIsRefusedAsTheDatabase() {
        engine.execute("CREATE WAREHOUSE w_seen");
        engine.execute("GRANT USAGE ON WAREHOUSE w_seen TO ROLE r1");
        as("U1", "R1");
        assertEquals(UNSEEN_DATABASE, refusal("SELECT SYSTEM$GET_TAG('d1.priv.cls', 'w_seen', 'warehouse')"));
        assertEquals(UNSEEN_DATABASE, refusal("SELECT SYSTEM$GET_TAG('d1.priv.nosuch', 'w_seen', 'warehouse')"));
        assertEquals(UNSEEN_DATABASE, refusal("SELECT SYSTEM$GET_TAG('d1.nosuch.nosuch', 'w_seen', 'warehouse')"));
    }

    @Test
    public void anObjectOfASeenSchemaTheRoleHoldsNothingOnIsRefusedAsMissing() {
        engine.execute("GRANT USAGE ON DATABASE d1 TO ROLE r1");
        engine.execute("GRANT USAGE ON SCHEMA d1.priv TO ROLE r1");
        engine.execute("CREATE STAGE d1.priv.stg");
        engine.execute("CREATE STAGE d1.priv.stg2");
        engine.execute("ALTER STAGE d1.priv.stg2 SET TAG d1.priv.cls = 's'");
        engine.execute("GRANT READ ON STAGE d1.priv.stg2 TO ROLE r1");
        engine.execute("CREATE TASK d1.priv.tk SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE FUNCTION d1.priv.f(x NUMBER) RETURNS NUMBER AS 'x'");
        as("U1", "R1");
        assertEquals("SQL compilation error:\nStage 'D1.PRIV.STG' does not exist or not authorized. Your primary "
            + "role R1 must have at least one privilege granted on STAGE D1.PRIV.STG.",
            refusal("SELECT SYSTEM$GET_TAG('d1.priv.cls', 'd1.priv.stg', 'stage')"));
        assertEquals("SQL compilation error:\nTask 'D1.PRIV.TK' does not exist or not authorized. Your primary "
            + "role R1 must have at least one privilege granted on TASK D1.PRIV.TK.",
            refusal("SELECT SYSTEM$GET_TAG('d1.priv.cls', 'd1.priv.tk', 'task')"));
        assertEquals("SQL compilation error:\nFunction 'd1.priv.f(NUMBER)' does not exist or not authorized.",
            refusal("SELECT SYSTEM$GET_TAG('d1.priv.cls', 'd1.priv.f(NUMBER)', 'function')"));
        assertEquals("s", cell("SELECT SYSTEM$GET_TAG('d1.priv.cls', 'd1.priv.stg2', 'stage')"));
        assertEquals("1", cell("SELECT COUNT(*) FROM TABLE(d1.information_schema.tag_references('d1.priv.stg2', "
            + "'stage'))"));
    }

    @Test
    public void aWarehouseOrDatabaseRoleTheRoleNeitherHoldsNorSeesIsRefusedAsMissing() {
        engine.execute("GRANT USAGE ON DATABASE d1 TO ROLE r1");
        engine.execute("GRANT USAGE ON SCHEMA d1.priv TO ROLE r1");
        engine.execute("CREATE WAREHOUSE w_hidden");
        engine.execute("CREATE WAREHOUSE w_seen");
        engine.execute("ALTER WAREHOUSE w_seen SET TAG d1.priv.cls = 'w'");
        engine.execute("GRANT USAGE ON WAREHOUSE w_seen TO ROLE r1");
        engine.execute("CREATE DATABASE ROLE d1.dr_hidden");
        engine.execute("CREATE DATABASE ROLE d1.dr_held");
        engine.execute("GRANT DATABASE ROLE d1.dr_held TO ROLE r1");
        as("U1", "R1");
        assertEquals("SQL compilation error:\nWarehouse 'W_HIDDEN' does not exist or not authorized. Your primary "
            + "role R1 must have at least one privilege granted on WAREHOUSE W_HIDDEN.",
            refusal("SELECT SYSTEM$GET_TAG('d1.priv.cls', 'w_hidden', 'warehouse')"));
        assertEquals("SQL compilation error:\nWarehouse 'W_HIDDEN' does not exist or not authorized. Your primary "
            + "role R1 must have at least one privilege granted on WAREHOUSE W_HIDDEN.",
            refusal("SELECT * FROM TABLE(d1.information_schema.tag_references('w_hidden', 'warehouse'))"));
        assertEquals("w", cell("SELECT SYSTEM$GET_TAG('d1.priv.cls', 'w_seen', 'warehouse')"));
        assertEquals("SQL compilation error:\nDatabase role 'D1.DR_HIDDEN' does not exist or not authorized. Your "
            + "primary role R1 must have at least one privilege granted on ROLE D1.DR_HIDDEN.",
            refusal("SELECT SYSTEM$GET_TAG('d1.priv.cls', 'd1.dr_hidden', 'database role')"));
        assertEquals("null", cell("SELECT SYSTEM$GET_TAG('d1.priv.cls', 'd1.dr_held', 'database role')"));
    }

    @Test
    public void aRoleAndTheAccountAreAnsweredWhateverTheRoleHolds() {
        engine.execute("GRANT USAGE ON DATABASE d1 TO ROLE r1");
        engine.execute("GRANT USAGE ON SCHEMA d1.priv TO ROLE r1");
        as("U1", "R1");
        assertEquals("null", cell("SELECT SYSTEM$GET_TAG('d1.priv.cls', 'ACCOUNTADMIN', 'role')"));
        assertEquals("null", cell("SELECT SYSTEM$GET_TAG('d1.priv.cls', CURRENT_ACCOUNT(), 'account')"));
        assertEquals("0", cell("SELECT COUNT(*) FROM TABLE(d1.information_schema.tag_references('ACCOUNTADMIN', "
            + "'role'))"));
    }
}
