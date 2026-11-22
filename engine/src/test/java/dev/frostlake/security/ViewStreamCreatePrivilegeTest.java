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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A stream on a view asks what a stream on a table asks (documented): SELECT on the view, and where change tracking
 * is still off on the view or a table it reads, a role allowed to turn it on there - the first stream is created by
 * the owner of the view and its tables. A refused stream turns change tracking on nowhere. Engine only: the live
 * account allows no CREATE ROLE, CREATE USER or GRANT, so the refusals' wording is the table stream's, unmeasured
 * for a view.
 */
public class ViewStreamCreatePrivilegeTest {

    private DatabaseEngine engine;
    private String systemRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        systemRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE d1");
        engine.execute("CREATE SCHEMA d1.s1");
        engine.execute("CREATE TABLE d1.s1.t (id INT, s STRING)");
        engine.execute("INSERT INTO d1.s1.t VALUES (1, 'secret')");
        engine.execute("CREATE VIEW d1.s1.v AS SELECT * FROM d1.s1.t");
        engine.execute("CREATE ROLE r1");
        engine.execute("CREATE USER u1 DEFAULT_SECONDARY_ROLES = ()");
        engine.execute("GRANT ROLE r1 TO USER u1");
        engine.execute("GRANT USAGE ON DATABASE d1 TO ROLE r1");
        engine.execute("GRANT USAGE ON SCHEMA d1.s1 TO ROLE r1");
        engine.execute("GRANT CREATE STREAM ON SCHEMA d1.s1 TO ROLE r1");
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

    /** The change_tracking column SHOW lists for the one object a pattern names. */
    private String changeTracking(final String show) {
        final ResultSet rs = engine.executeQuery(show);
        final int column = rs.getColumnIndex("change_tracking");
        return String.valueOf(rs.getRows().get(0).getValue(column));
    }

    private void assertTrackingUntouched() {
        asSystem();
        assertEquals("OFF", changeTracking("SHOW VIEWS LIKE 'V' IN SCHEMA d1.s1"));
        assertEquals("OFF", changeTracking("SHOW TABLES LIKE 'T' IN SCHEMA d1.s1"));
        assertEquals(0, engine.executeQuery("SHOW STREAMS IN SCHEMA d1.s1").getRows().size());
    }

    @Test
    public void aViewTheRoleMayNotReadIsRefused() {
        asUser();
        final String refused = refusal("CREATE STREAM d1.s1.vs ON VIEW d1.s1.v");
        assertEquals("SQL access control error:\nInsufficient privileges to operate on view 'V'. Your primary role R1"
            + " must have SELECT granted on TABLE D1.S1.V.", refused);
        assertTrue(refusal("SELECT * FROM d1.s1.v").contains("Insufficient privileges"));
        assertTrackingUntouched();
    }

    @Test
    public void aReadableViewWhoseTrackingIsOffIsLeftToItsOwner() {
        asSystem();
        engine.execute("GRANT SELECT ON VIEW d1.s1.v TO ROLE r1");
        asUser();
        final String refused = refusal("CREATE STREAM d1.s1.vs ON VIEW d1.s1.v");
        assertTrue(refused.contains("stream source without CHANGE_TRACKING enabled"), refused);
        assertTrackingUntouched();
    }

    @Test
    public void aTableBelowTheViewWhoseTrackingIsOffIsLeftToItsOwner() {
        asSystem();
        engine.execute("GRANT SELECT ON TABLE d1.s1.t TO ROLE r1");
        engine.execute("GRANT CREATE VIEW ON SCHEMA d1.s1 TO ROLE r1");
        asUser();
        engine.execute("CREATE VIEW d1.s1.own AS SELECT * FROM d1.s1.t");
        final String refused = refusal("CREATE STREAM d1.s1.vs ON VIEW d1.s1.own");
        assertEquals("SQL access control error:\nInsufficient privileges to operate on stream source without"
            + " CHANGE_TRACKING enabled 'T'. Your primary role R1 must have MODIFY granted on TABLE D1.S1.T.", refused);
        assertEquals(0, engine.executeQuery("SHOW STREAMS IN SCHEMA d1.s1").getRows().size());
        assertEquals("OFF", changeTracking("SHOW VIEWS LIKE 'OWN' IN SCHEMA d1.s1"));
        asSystem();
        assertEquals("OFF", changeTracking("SHOW TABLES LIKE 'T' IN SCHEMA d1.s1"));
    }

    @Test
    public void aReadableViewAlreadyTrackedTakesAStream() {
        asSystem();
        engine.execute("GRANT SELECT ON VIEW d1.s1.v TO ROLE r1");
        engine.execute("ALTER VIEW d1.s1.v SET CHANGE_TRACKING = TRUE");
        asUser();
        engine.execute("CREATE STREAM d1.s1.vs ON VIEW d1.s1.v");
        assertEquals(1, engine.executeQuery("SHOW STREAMS IN SCHEMA d1.s1").getRows().size());
    }

    @Test
    public void theOwnerTakesTheFirstStream() {
        asSystem();
        engine.execute("CREATE STREAM d1.s1.vs ON VIEW d1.s1.v");
        assertEquals("ON", changeTracking("SHOW VIEWS LIKE 'V' IN SCHEMA d1.s1"));
        assertEquals("ON", changeTracking("SHOW TABLES LIKE 'T' IN SCHEMA d1.s1"));
    }
}
