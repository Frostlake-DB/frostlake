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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Who may set or unset a masking policy and add or drop a row access policy on a table (documented): a role holding
 * the account's APPLY MASKING POLICY or APPLY ROW ACCESS POLICY may, on any table it can reach, without MODIFY or any
 * other privilege on the table; a role holding APPLY on the one policy may only on a table it owns, so MODIFY on the
 * table stands in for nothing. And a role missing USAGE on a table's schema or database is refused as though the
 * container were not there, before anything is said about the table. Engine only: the live account allows no CREATE
 * ROLE or GRANT.
 */
public class PolicyAttachPrivilegeModelTest {

    private DatabaseEngine engine;
    private String systemRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        systemRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE d1");
        engine.execute("CREATE SCHEMA d1.s1");
        engine.execute("CREATE SCHEMA d1.s2");
        engine.execute("CREATE DATABASE d2");
        engine.execute("USE SCHEMA d1.s1");
        engine.execute("CREATE ROLE gov");
        engine.execute("CREATE ROLE r1");
        engine.execute("CREATE USER u1 DEFAULT_SECONDARY_ROLES = ()");
        engine.execute("GRANT ROLE gov TO USER u1");
        engine.execute("GRANT ROLE r1 TO USER u1");
        engine.execute("CREATE TABLE t (a STRING, b STRING)");
        engine.execute("INSERT INTO t VALUES ('EU', 'secret')");
        engine.execute("CREATE TABLE d1.s2.secret (a INT)");
        engine.execute("CREATE TABLE d2.public.x (a INT)");
        engine.execute("CREATE MASKING POLICY mp AS (v STRING) RETURNS STRING -> '***'");
        engine.execute("CREATE ROW ACCESS POLICY rap AS (r STRING) RETURNS BOOLEAN -> r = 'EU'");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private void as(final String role) {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("u1");
        engine.setCurrentRole(role);
    }

    private void asSystem() {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("SYSTEM");
        engine.setCurrentRole(systemRole);
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.append(out.length() > 0 ? "; " : "").append(row.getValue(0)).append('|').append(row.getValue(1));
        }
        return out.toString();
    }

    @Test
    public void theAccountsApplyPrivilegesAttachWithoutAnyPrivilegeOnTheTable() {
        engine.execute("GRANT USAGE ON DATABASE d1 TO ROLE gov");
        engine.execute("GRANT USAGE ON SCHEMA d1.s1 TO ROLE gov");
        engine.execute("GRANT APPLY MASKING POLICY ON ACCOUNT TO ROLE gov");
        engine.execute("GRANT APPLY ROW ACCESS POLICY ON ACCOUNT TO ROLE gov");
        as("gov");
        engine.execute("ALTER TABLE d1.s1.t MODIFY COLUMN b SET MASKING POLICY d1.s1.mp");
        engine.execute("ALTER TABLE d1.s1.t ADD ROW ACCESS POLICY d1.s1.rap ON (a)");
        engine.execute("ALTER TABLE d1.s1.t DROP ROW ACCESS POLICY d1.s1.rap");
        asSystem();
        assertEquals("EU|***", rows("SELECT * FROM d1.s1.t"));
        as("gov");
        engine.execute("ALTER TABLE d1.s1.t MODIFY COLUMN b UNSET MASKING POLICY");
        asSystem();
        assertEquals("EU|secret", rows("SELECT * FROM d1.s1.t"));
        as("gov");
        // Anything else asks MODIFY as before.
        assertEquals("SQL access control error:\nInsufficient privileges to operate on table 'T'.",
            refusal("ALTER TABLE d1.s1.t SET COMMENT = 'x'"));
    }

    @Test
    public void applyOnThePolicyAndModifyOnTheTableIsNotEnough() {
        engine.execute("ALTER TABLE t MODIFY COLUMN b SET MASKING POLICY mp");
        engine.execute("GRANT USAGE ON DATABASE d1 TO ROLE r1");
        engine.execute("GRANT USAGE, CREATE TABLE ON SCHEMA d1.s1 TO ROLE r1");
        engine.execute("GRANT SELECT, MODIFY ON TABLE t TO ROLE r1");
        engine.execute("GRANT APPLY ON MASKING POLICY mp TO ROLE r1");
        engine.execute("GRANT APPLY ON ROW ACCESS POLICY rap TO ROLE r1");
        as("r1");
        final String notOwner = "SQL access control error:\nInsufficient privileges to operate on table 'T'.";
        assertEquals(notOwner, refusal("ALTER TABLE t MODIFY COLUMN b UNSET MASKING POLICY").split(" Your")[0]);
        assertEquals(notOwner, refusal("ALTER TABLE t ADD ROW ACCESS POLICY rap ON (a)").split(" Your")[0]);
        assertEquals("EU|***", rows("SELECT * FROM t"));
        // On a table it owns, APPLY on the policy is enough.
        engine.execute("CREATE TABLE own (a STRING, b STRING)");
        engine.execute("ALTER TABLE own MODIFY COLUMN b SET MASKING POLICY mp");
        engine.execute("ALTER TABLE own ADD ROW ACCESS POLICY rap ON (a)");
        engine.execute("ALTER TABLE own DROP ALL ROW ACCESS POLICIES");
        engine.execute("ALTER TABLE own MODIFY COLUMN b UNSET MASKING POLICY");
    }

    @Test
    public void aMissingContainerUsageIsReportedBeforeTheTable() {
        engine.execute("GRANT USAGE ON DATABASE d1 TO ROLE r1");
        as("r1");
        final String schema = "Schema 'D1.S2' does not exist or not authorized.";
        assertEquals(schema, tail(refusal("SELECT * FROM d1.s2.secret")));
        assertEquals(schema, tail(refusal("SELECT * FROM d1.s2.nosuch")));
        assertEquals(schema, tail(refusal("DESCRIBE TABLE d1.s2.secret")));
        final String database = "Database 'D2' does not exist or not authorized.";
        assertEquals(database, tail(refusal("SELECT * FROM d2.public.x")));
        assertEquals(database, tail(refusal("EXECUTE IMMEDIATE 'SELECT * FROM d2.public.x'")));
        assertEquals(database, tail(refusal("SELECT * FROM d2.public.nosuch")));
        assertEquals(database, tail(refusal("EXECUTE IMMEDIATE 'SELECT * FROM d2.public.nosuch'")));
    }

    /** A refusal's first sentence, past its prefix line. */
    private static String tail(final String message) {
        final int newline = message.indexOf('\n');
        final String sentence = newline < 0 ? message : message.substring(newline + 1);
        final int hint = sentence.indexOf(" Your primary role");
        return hint < 0 ? sentence : sentence.substring(0, hint);
    }
}
