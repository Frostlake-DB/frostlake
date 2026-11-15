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
import dev.frostlake.config.EngineConfig;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A privilege granted to one grantee by several grantors (live-verified): each grantor's grant is an instance of its
 * own, listed on its own row with its own grant option, and revoked on its own. A REVOKE takes back the instances
 * the session's roles granted - every instance when the session decides every grant, only its own for an owner
 * without MANAGE GRANTS - and counts each instance, and each dependent grant a CASCADE takes with them. Under
 * RESTRICT an instance whose grant option other grants stand on is skipped, the rest revoked, and the statement
 * reports itself partially executed. A grant through a grant option is recorded in the name of the role that holds
 * the option, inherited or secondary; a bulk grant passes over the objects the session may not grant on.
 */
public class GrantInstancesTest {

    private static final String DONE = "Statement executed successfully. ";

    @TempDir
    Path dir;

    private DatabaseEngine engine;
    private String systemRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        prepare(engine);
    }

    private void prepare(final DatabaseEngine target) {
        engine = target;
        systemRole = engine.getSessionContext().getCurrentRole();
        run("CREATE DATABASE p", "GRANT USAGE ON DATABASE p TO ROLE PUBLIC",
            "GRANT USAGE ON SCHEMA p.public TO ROLE PUBLIC", "CREATE TABLE p.public.t (a INT)",
            "CREATE TABLE p.public.u (a INT)");
        for (final String role : new String[] {"g1", "g2", "c", "d", "o", "h", "h2", "pr", "n", "k", "e", "w", "x"}) {
            run("CREATE ROLE " + role, "CREATE USER u_" + role, "GRANT ROLE " + role + " TO USER u_" + role);
        }
        run("GRANT ROLE h TO ROLE pr", "GRANT ROLE h2 TO USER u_n");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private void run(final String... statements) {
        for (final String sql : statements) {
            engine.execute(sql);
        }
    }

    /** The session as one role's user, with that role in use. */
    private void as(final String role) {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("U_" + role.toUpperCase(Locale.ROOT));
        engine.setCurrentRole(systemRole);
        engine.execute("USE ROLE " + role);
    }

    private void asSystem() {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("SYSTEM");
        engine.setCurrentRole(systemRole);
    }

    private String status(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    /** One table's grants of a privilege, each as grantee, grant option and grantor, in that order. */
    private String grants(final String table, final String privilege) {
        final ResultSet rs = engine.executeQuery("SHOW GRANTS ON TABLE p.public." + table + " ->> SELECT"
            + " LISTAGG(\"grantee_name\" || ':' || \"grant_option\" || ':' || \"granted_by\", ' ')"
            + " WITHIN GROUP (ORDER BY \"grantee_name\", \"granted_by\") FROM $1 WHERE \"privilege\" = '" + privilege
            + "'");
        final Object listed = rs.getRows().get(0).getValue(0);
        return listed == null ? "" : String.valueOf(listed);
    }

    private static String partially(final int revoked, final int skipped) {
        return "Revoke partially executed: " + revoked + " grant(s) revoked, " + skipped + " grant(s) skipped due to"
            + " dependent grants and no CASCADE is specified.";
    }

    private void optionsToBothGrantors() {
        run("GRANT SELECT ON TABLE p.public.t TO ROLE g1 WITH GRANT OPTION",
            "GRANT SELECT ON TABLE p.public.t TO ROLE g2 WITH GRANT OPTION");
    }

    @Test
    public void twoGrantorsGrantTwoInstancesListedAndRevokedApart() {
        optionsToBothGrantors();
        as("g1");
        run("GRANT SELECT ON TABLE p.public.t TO ROLE c");
        as("g2");
        run("GRANT SELECT ON TABLE p.public.t TO ROLE c");
        as("g1");
        run("GRANT SELECT ON TABLE p.public.t TO ROLE c");
        asSystem();
        assertEquals("C:false:G1 C:false:G2 G1:true:SYSADMIN G2:true:SYSADMIN", grants("t", "SELECT"));
        assertEquals("G1,G2", status("SELECT LISTAGG(grantor, ',') WITHIN GROUP (ORDER BY grantor)"
            + " FROM p.information_schema.table_privileges WHERE grantee = 'C'"));
        as("g2");
        assertEquals(DONE + "1 objects affected.", status("REVOKE SELECT ON TABLE p.public.t FROM ROLE c"));
        assertEquals(DONE + "0 objects affected.", status("REVOKE SELECT ON TABLE p.public.t FROM ROLE c"));
        asSystem();
        assertEquals("C:false:G1 G1:true:SYSADMIN G2:true:SYSADMIN", grants("t", "SELECT"));
        as("g2");
        run("GRANT SELECT ON TABLE p.public.t TO ROLE c");
        asSystem();
        assertEquals(DONE + "2 objects affected.", status("REVOKE SELECT ON TABLE p.public.t FROM ROLE c"));
        assertEquals("G1:true:SYSADMIN G2:true:SYSADMIN", grants("t", "SELECT"));
    }

    @Test
    public void eachInstanceCarriesItsOwnGrantOptionAndADroppedGrantorTakesOnlyItsOwn() {
        optionsToBothGrantors();
        as("g2");
        run("GRANT SELECT ON TABLE p.public.t TO ROLE c");
        as("g1");
        run("GRANT SELECT ON TABLE p.public.t TO ROLE c WITH GRANT OPTION");
        asSystem();
        assertEquals("C:true:G1 C:false:G2", grants("t", "SELECT").substring(0, "C:true:G1 C:false:G2".length()));
        run("DROP ROLE g2");
        assertEquals("C:true:G1 G1:true:SYSADMIN", grants("t", "SELECT"));
    }

    @Test
    public void restrictRevokesTheInstancesNoGrantStandsOnAndReportsTheRest() {
        optionsToBothGrantors();
        as("g1");
        run("GRANT SELECT ON TABLE p.public.t TO ROLE c WITH GRANT OPTION");
        as("g2");
        run("GRANT SELECT ON TABLE p.public.t TO ROLE c");
        as("c");
        run("GRANT SELECT ON TABLE p.public.t TO ROLE d");
        as("g1");
        assertEquals(partially(0, 1), refusal("REVOKE SELECT ON TABLE p.public.t FROM ROLE c"));
        assertEquals(partially(0, 1), refusal("REVOKE GRANT OPTION FOR SELECT ON TABLE p.public.t FROM ROLE c"));
        asSystem();
        assertEquals(partially(1, 1), refusal("REVOKE SELECT ON TABLE p.public.t FROM ROLE c"));
        assertEquals("C:true:G1 D:false:C G1:true:SYSADMIN G2:true:SYSADMIN", grants("t", "SELECT"));
        assertEquals("3935 23001", status("EXECUTE IMMEDIATE $$ BEGIN REVOKE SELECT ON TABLE p.public.t FROM ROLE c;"
            + " RETURN 'none'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE; END; $$"));
        as("g1");
        assertEquals(DONE + "2 objects affected.", status("REVOKE SELECT ON TABLE p.public.t FROM ROLE c CASCADE"));
        asSystem();
        assertEquals("G1:true:SYSADMIN G2:true:SYSADMIN", grants("t", "SELECT"));
    }

    @Test
    public void cascadeCountsEveryGrantItTakes() {
        run("GRANT SELECT, INSERT ON TABLE p.public.t TO ROLE c WITH GRANT OPTION");
        as("c");
        run("GRANT SELECT, INSERT ON TABLE p.public.t TO ROLE d");
        asSystem();
        assertEquals(partially(0, 2), refusal("REVOKE SELECT, INSERT ON TABLE p.public.t FROM ROLE c"));
        assertEquals(DONE + "4 objects affected.",
            status("REVOKE SELECT, INSERT ON TABLE p.public.t FROM ROLE c CASCADE"));
        assertEquals("", grants("t", "SELECT") + grants("t", "INSERT"));
    }

    @Test
    public void anOwnerWithoutManageGrantsRevokesOnlyItsOwnInstance() {
        run("GRANT CREATE TABLE ON SCHEMA p.public TO ROLE o");
        as("o");
        run("CREATE TABLE p.public.t3 (a INT)", "GRANT SELECT ON TABLE p.public.t3 TO ROLE g1 WITH GRANT OPTION");
        as("g1");
        run("GRANT SELECT ON TABLE p.public.t3 TO ROLE c");
        as("o");
        run("GRANT SELECT ON TABLE p.public.t3 TO ROLE c");
        assertEquals(DONE + "1 objects affected.", status("REVOKE SELECT ON TABLE p.public.t3 FROM ROLE c"));
        assertEquals(DONE + "0 objects affected.", status("REVOKE SELECT ON TABLE p.public.t3 FROM ROLE c"));
        asSystem();
        assertEquals("C:false:G1 G1:true:O", grants("t3", "SELECT"));
    }

    @Test
    public void aGrantThroughAnOptionIsRecordedInTheNameOfTheRoleHoldingIt() {
        run("GRANT SELECT ON TABLE p.public.u TO ROLE h WITH GRANT OPTION",
            "GRANT SELECT ON TABLE p.public.u TO ROLE h2 WITH GRANT OPTION");
        as("pr");
        run("GRANT SELECT ON TABLE p.public.u TO ROLE x");
        as("n");
        run("USE SECONDARY ROLES ALL", "GRANT SELECT ON TABLE p.public.u TO ROLE w");
        asSystem();
        assertEquals("H:true:SYSADMIN H2:true:SYSADMIN W:false:H2 X:false:H", grants("u", "SELECT"));
    }

    @Test
    public void aBulkGrantPassesOverTheObjectsTheSessionMayNotGrantOn() {
        run("GRANT SELECT ON TABLE p.public.t TO ROLE k",
            "GRANT SELECT ON TABLE p.public.u TO ROLE k WITH GRANT OPTION");
        as("k");
        assertEquals(DONE + "1 objects affected.",
            status("GRANT SELECT ON ALL TABLES IN SCHEMA p.public TO ROLE w"));
        assertEquals(DONE + "1 objects affected.",
            status("GRANT SELECT, INSERT ON ALL TABLES IN SCHEMA p.public TO ROLE w"));
        as("e");
        assertEquals(DONE + "0 objects affected.",
            status("GRANT SELECT ON ALL TABLES IN SCHEMA p.public TO ROLE w"));
        assertEquals(DONE + "0 objects affected.", status("GRANT SELECT ON ALL TABLES IN DATABASE p TO ROLE w"));
        assertEquals(DONE + "0 objects affected.",
            status("REVOKE SELECT ON ALL TABLES IN SCHEMA p.public FROM ROLE w"));
        asSystem();
        assertEquals("K:false:SYSADMIN", grants("t", "SELECT"));
        assertEquals("K:true:SYSADMIN W:false:K", grants("u", "SELECT"));
        assertEquals("", grants("u", "INSERT"));
    }

    @Test
    public void theInstancesSurviveARestart() {
        engine.shutdown();
        final EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_PERSISTENCE_ENABLED, "true");
        config.setProperty(EngineConfig.PROP_PERSISTENCE_DIRECTORY, dir.toString());
        config.setProperty(EngineConfig.PROP_PERSISTENCE_AUTO_SAVE, "false");
        prepare(new DatabaseEngine(config));
        optionsToBothGrantors();
        as("g1");
        run("GRANT SELECT ON TABLE p.public.t TO ROLE c WITH GRANT OPTION");
        as("g2");
        run("GRANT SELECT ON TABLE p.public.t TO ROLE c");
        asSystem();
        final String before = grants("t", "SELECT");
        final String moments = status("SHOW GRANTS ON TABLE p.public.t ->> SELECT LISTAGG(\"created_on\"::VARCHAR, ' ')"
            + " WITHIN GROUP (ORDER BY \"grantee_name\", \"granted_by\") FROM $1");
        engine.shutdown();
        engine = new DatabaseEngine(config);
        systemRole = engine.getSessionContext().getCurrentRole();
        assertEquals("C:true:G1 C:false:G2 G1:true:SYSADMIN G2:true:SYSADMIN", before);
        assertEquals(before, grants("t", "SELECT"));
        assertEquals(moments, status("SHOW GRANTS ON TABLE p.public.t ->> SELECT LISTAGG(\"created_on\"::VARCHAR,"
            + " ' ') WITHIN GROUP (ORDER BY \"grantee_name\", \"granted_by\") FROM $1"));
        as("g2");
        assertEquals(DONE + "1 objects affected.", status("REVOKE SELECT ON TABLE p.public.t FROM ROLE c"));
        asSystem();
        assertEquals("C:true:G1 G1:true:SYSADMIN G2:true:SYSADMIN", grants("t", "SELECT"));
    }
}
