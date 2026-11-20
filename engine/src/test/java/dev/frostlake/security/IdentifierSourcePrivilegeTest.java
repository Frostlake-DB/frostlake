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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A relation named through {@code IDENTIFIER(…)} or a {@code TABLE(…)} literal asks what the name written in FROM
 * asks: SELECT on the table, view, materialized view or stream, and USAGE on its database and schema. A role without
 * them is refused in the very words the written name earns, and a statement that copies the rows - a CREATE TABLE AS
 * SELECT - is refused with it. Engine only: the live account allows no CREATE ROLE, CREATE USER or GRANT.
 */
public class IdentifierSourcePrivilegeTest {

    private DatabaseEngine engine;
    private String systemRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        systemRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE d1");
        engine.execute("CREATE SCHEMA d1.s1");
        engine.execute("CREATE SCHEMA d1.s2");
        engine.execute("CREATE TABLE d1.s1.t (id INT, s STRING)");
        engine.execute("INSERT INTO d1.s1.t VALUES (1, 'secret')");
        engine.execute("CREATE VIEW d1.s1.v AS SELECT * FROM d1.s1.t");
        engine.execute("CREATE MATERIALIZED VIEW d1.s1.mv AS SELECT id FROM d1.s1.t");
        engine.execute("CREATE STREAM d1.s1.st ON TABLE d1.s1.t");
        engine.execute("CREATE TABLE d1.s2.secret (s STRING)");
        engine.execute("INSERT INTO d1.s2.secret VALUES ('s2 secret')");
        engine.execute("CREATE DATABASE d2");
        engine.execute("CREATE TABLE d2.public.x (s STRING)");
        engine.execute("INSERT INTO d2.public.x VALUES ('d2 secret')");
        engine.execute("CREATE ROLE r1");
        engine.execute("CREATE USER u1 DEFAULT_SECONDARY_ROLES = ()");
        engine.execute("GRANT ROLE r1 TO USER u1");
        engine.execute("GRANT USAGE ON DATABASE d1 TO ROLE r1");
        engine.execute("GRANT USAGE ON SCHEMA d1.s1 TO ROLE r1");
        engine.execute("GRANT CREATE TABLE ON SCHEMA d1.s1 TO ROLE r1");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private void asUser() {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("u1");
        engine.setCurrentRole("r1");
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

    private String firstCell(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.append(out.length() > 0 ? "; " : "").append(row.getValue(0));
        }
        return out.toString();
    }

    /** Asserts a name is refused through IDENTIFIER() and a TABLE() literal as it is written in FROM. */
    private void assertRefusedAsWritten(final String select, final String name) {
        final String written = refusal(select + " FROM " + name);
        assertEquals(written, refusal(select + " FROM IDENTIFIER('" + name + "')"), name);
        assertEquals(written, refusal(select + " FROM TABLE('" + name + "')"), name);
        engine.execute("SET tn = '" + name + "'");
        assertEquals(written, refusal(select + " FROM IDENTIFIER($tn)"), name);
        assertEquals(written, refusal(select + " FROM TABLE($tn)"), name);
    }

    @Test
    public void aDatabaseTheRoleMayNotUseIsRefusedThroughADynamicName() {
        asUser();
        assertRefusedAsWritten("SELECT *", "d2.public.x");
        assertTrue(refusal("SELECT * FROM IDENTIFIER('d2.public.x')").contains("Database 'D2' does not exist"));
    }

    @Test
    public void aSchemaTheRoleMayNotUseIsRefusedThroughADynamicName() {
        asSystem();
        engine.execute("GRANT SELECT ON TABLE d1.s2.secret TO ROLE r1");
        asUser();
        assertRefusedAsWritten("SELECT *", "d1.s2.secret");
        assertTrue(refusal("SELECT * FROM TABLE('d1.s2.secret')").contains("Schema 'D1.S2' does not exist"));
    }

    @Test
    public void everyRelationKindAsksSelectThroughADynamicName() {
        asUser();
        assertRefusedAsWritten("SELECT *", "d1.s1.t");
        assertRefusedAsWritten("SELECT *", "d1.s1.v");
        assertRefusedAsWritten("SELECT *", "d1.s1.mv");
        assertRefusedAsWritten("SELECT COUNT(*)", "d1.s1.st");
    }

    @Test
    public void rowsADynamicNameMayNotReadAreNotCopied() {
        asUser();
        final String written = refusal("CREATE TABLE d1.s1.mine AS SELECT * FROM d1.s1.t");
        assertEquals(written, refusal("CREATE TABLE d1.s1.mine AS SELECT * FROM IDENTIFIER('d1.s1.t')"));
        assertEquals(written, refusal("CREATE TABLE d1.s1.mine AS SELECT * FROM TABLE('d1.s1.t')"));
        assertEquals(refusal("CREATE TABLE d1.s1.mine AS SELECT * FROM d2.public.x"),
            refusal("CREATE TABLE d1.s1.mine AS SELECT * FROM IDENTIFIER('d2.public.x')"));
        asSystem();
        assertEquals("0", firstCell("SELECT COUNT(*) FROM d1.information_schema.tables WHERE table_name = 'MINE'"));
    }

    @Test
    public void aGrantedReadReadsThroughADynamicName() {
        asSystem();
        engine.execute("GRANT SELECT ON TABLE d1.s1.t TO ROLE r1");
        engine.execute("GRANT SELECT ON VIEW d1.s1.v TO ROLE r1");
        engine.execute("GRANT SELECT ON STREAM d1.s1.st TO ROLE r1");
        asUser();
        assertEquals("secret", firstCell("SELECT s FROM IDENTIFIER('d1.s1.t')"));
        assertEquals("secret", firstCell("SELECT s FROM TABLE('d1.s1.t')"));
        assertEquals("secret", firstCell("SELECT s FROM IDENTIFIER('d1.s1.v')"));
        assertEquals("secret", firstCell("SELECT s FROM TABLE('d1.s1.v')"));
        assertEquals("0", firstCell("SELECT COUNT(*) FROM IDENTIFIER('d1.s1.st')"));
    }
}
