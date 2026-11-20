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
package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Setting and unsetting an authentication or a session policy on the account and on a user: the attachment is
 * recorded and never enforced, listed by SHOW … POLICIES ON and, for a user, by POLICY_REFERENCES, and a policy that
 * is set anywhere is neither dropped nor replaced. Embedded only: the account and its users are not altered live.
 */
public class HeldPolicyAttachmentTest extends BaseDatabaseTest {

    private static final String EMBEDDED_ONLY = "the test alters the account and its users";

    @BeforeEach
    public void createPolicies() {
        assumeFalse(isLiveSnowflake(), EMBEDDED_ONLY);
        engine.execute("CREATE AUTHENTICATION POLICY ap_one AUTHENTICATION_METHODS = ('PASSWORD')");
        engine.execute("CREATE AUTHENTICATION POLICY ap_two");
        engine.execute("CREATE SESSION POLICY \"sp_one\" SESSION_IDLE_TIMEOUT_MINS = 30");
        engine.execute("CREATE SESSION POLICY sp_two");
        engine.execute("CREATE USER held_u");
        engine.execute("CREATE USER held_svc TYPE = SERVICE");
    }

    @AfterEach
    public void unsetPolicies() {
        if (isLiveSnowflake()) {
            return;
        }
        for (final String sql : new String[] {"ALTER ACCOUNT UNSET AUTHENTICATION POLICY",
            "ALTER ACCOUNT UNSET SESSION POLICY", "ALTER ACCOUNT UNSET SESSION POLICY FOR ALL PERSON USERS",
            "ALTER ACCOUNT UNSET AUTHENTICATION POLICY FOR ALL SERVICE USERS", "DROP USER IF EXISTS held_u",
            "DROP USER IF EXISTS held_svc"}) {
            engine.execute(sql);
        }
    }

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return refused.getMessage();
    }

    /** The rows of a listing as {@code name|set_on|options}, in order. */
    private List<String> heldOn(final String sql) {
        final ResultSet listed = engine.executeQuery(sql);
        final List<String> rows = new ArrayList<>();
        for (final Row row : listed.getRows()) {
            rows.add(cell(listed, row, "name") + "|" + cell(listed, row, "set_on") + "|" + cell(listed, row,
                "options"));
        }
        return rows;
    }

    private List<String> references(final String user) {
        final ResultSet listed = engine.executeQuery("SELECT POLICY_NAME, POLICY_KIND FROM TABLE("
            + "INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => '" + user + "', REF_ENTITY_DOMAIN => 'USER'))");
        final List<String> rows = new ArrayList<>();
        for (final Row row : listed.getRows()) {
            rows.add(row.getValue(0) + "|" + row.getValue(1));
        }
        return rows;
    }

    @Test
    public void aUsersPoliciesAreRecordedListedAndHeldOneAtATime() {
        engine.execute("ALTER USER held_u SET AUTHENTICATION POLICY test_db.test_schema.ap_one");
        engine.execute("ALTER USER held_u SET SESSION POLICY test_db.test_schema.\"sp_one\"");
        assertEquals(List.of("AP_ONE|AUTHENTICATION_POLICY", "sp_one|SESSION_POLICY"), references("held_u"));
        assertEquals(List.of("AP_ONE|USER|"), heldOn("SHOW AUTHENTICATION POLICIES ON USER held_u"));
        assertEquals(List.of("sp_one|USER|"), heldOn("SHOW SESSION POLICIES LIKE 'sp%' ON USER held_u"));
        assertEquals("Object HELD_U already has a AUTHENTICATION_POLICY. Only one AUTHENTICATION_POLICY is allowed at a"
            + " time.", refusal("ALTER USER held_u SET AUTHENTICATION POLICY ap_two"));
        engine.execute("ALTER USER held_u SET AUTHENTICATION POLICY ap_two FORCE");
        assertEquals(List.of("AP_TWO|AUTHENTICATION_POLICY", "sp_one|SESSION_POLICY"), references("held_u"));
        engine.execute("ALTER USER held_u UNSET AUTHENTICATION POLICY");
        engine.execute("ALTER USER held_u UNSET SESSION POLICY");
        engine.execute("ALTER USER held_u UNSET SESSION POLICY");
        assertEquals(List.of(), references("held_u"));
        assertEquals(List.of(), heldOn("SHOW SESSION POLICIES ON USER held_u"));
        engine.execute("ALTER USER IF EXISTS nobody SET SESSION POLICY sp_two");
    }

    @Test
    public void theAccountHoldsAPolicyForEveryUserAndForEachTypeOfUser() {
        assertEquals(List.of(), heldOn("SHOW SESSION POLICIES ON ACCOUNT"));
        engine.execute("ALTER ACCOUNT SET SESSION POLICY test_db.test_schema.\"sp_one\"");
        assertEquals(List.of("sp_one|ACCOUNT|{\"target_scopes\":[\"ACCOUNT\"]}"),
            heldOn("SHOW SESSION POLICIES ON ACCOUNT"));
        assertEquals("Object " + engine.executeQuery("SELECT CURRENT_ACCOUNT()").getRows().get(0).getValue(0)
            + " already has a SESSION_POLICY. Only one SESSION_POLICY is allowed at a time.",
            refusal("ALTER ACCOUNT SET SESSION POLICY sp_two"));
        engine.execute("ALTER ACCOUNT SET SESSION POLICY sp_two FORCE");
        engine.execute("ALTER ACCOUNT SET SESSION POLICY sp_two FOR ALL PERSON USERS");
        assertEquals(List.of("SP_TWO|ACCOUNT|{\"target_scopes\":[\"ACCOUNT\",\"PERSON_USERS\"]}"),
            heldOn("SHOW SESSION POLICIES ON ACCOUNT"));
        engine.execute("ALTER ACCOUNT SET AUTHENTICATION POLICY ap_one FOR ALL SERVICE USERS");
        assertEquals(List.of("AP_ONE|ACCOUNT|{\"target_scopes\":[\"SERVICE_USERS\"]}"),
            heldOn("SHOW AUTHENTICATION POLICIES ON USER held_svc"));
        assertEquals(List.of(), heldOn("SHOW AUTHENTICATION POLICIES ON USER held_u"));
        assertEquals(List.of("SP_TWO|ACCOUNT|{\"target_scopes\":[\"PERSON_USERS\"]}"),
            heldOn("SHOW SESSION POLICIES ON USER held_u"));
        engine.execute("ALTER ACCOUNT UNSET SESSION POLICY FOR ALL PERSON USERS");
        assertEquals(List.of("SP_TWO|ACCOUNT|{\"target_scopes\":[\"ACCOUNT\"]}"),
            heldOn("SHOW SESSION POLICIES ON USER held_u"));
        engine.execute("ALTER ACCOUNT UNSET SESSION POLICY");
        assertEquals(List.of(), heldOn("SHOW SESSION POLICIES ON ACCOUNT"));
    }

    @Test
    public void aHeldPolicyIsNeitherDroppedNorReplaced() {
        engine.execute("ALTER ACCOUNT SET AUTHENTICATION POLICY ap_one");
        engine.execute("ALTER USER held_u SET SESSION POLICY sp_two");
        final String inUse = " cannot be dropped/replaced as it is associated with one or more entities.";
        assertEquals("SQL compilation error: Policy AP_ONE" + inUse, refusal("DROP AUTHENTICATION POLICY ap_one"));
        assertEquals("SQL compilation error: Policy SP_TWO" + inUse, refusal("DROP SESSION POLICY IF EXISTS sp_two"));
        assertEquals("SQL compilation error: Policy SP_TWO" + inUse, refusal("CREATE OR REPLACE SESSION POLICY sp_two"));
        engine.execute("ALTER ACCOUNT UNSET AUTHENTICATION POLICY");
        engine.execute("DROP USER held_u");
        engine.execute("DROP AUTHENTICATION POLICY ap_one");
        engine.execute("DROP SESSION POLICY sp_two");
    }

    @Test
    public void aMissingPolicyAndAWrongTypeOfUserAreRefused() {
        assertEquals("SQL compilation error:\nSession policy 'TEST_DB.TEST_SCHEMA.AP_ONE' does not exist or not"
            + " authorized.", firstLines(refusal("ALTER USER held_u SET SESSION POLICY ap_one")));
        assertEquals("SQL compilation error:\nUser 'NOBODY' does not exist or not authorized.",
            refusal("ALTER USER nobody SET AUTHENTICATION POLICY ap_one"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 55 unexpected 'ROBOT'.",
            refusal("ALTER ACCOUNT SET AUTHENTICATION POLICY ap_one FOR ALL ROBOT USERS"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 44 unexpected 'FOR'.",
            refusal("ALTER USER held_u SET SESSION POLICY sp_two FOR ALL PERSON USERS"));
    }

    @Test
    public void aPasswordPolicyNamedInLowerCaseIsFoundWhereItIsSet() {
        engine.execute("CREATE PASSWORD POLICY \"pp_low\" PASSWORD_MIN_LENGTH = 20");
        engine.execute("CREATE USER held_pw PASSWORD = 'Abcdefghijklm0'");
        try {
            engine.execute("ALTER USER held_pw SET PASSWORD POLICY test_db.test_schema.\"pp_low\"");
            assertEquals(List.of("pp_low|USER|"), heldOn("SHOW PASSWORD POLICIES ON USER held_pw"));
            assertEquals("SQL execution error:\nNew password rejected by current password policy. Reason: 'MIN_LENGTH'",
                refusal("ALTER USER held_pw SET PASSWORD = 'Short1abc'"));
            engine.execute("ALTER USER held_pw UNSET PASSWORD POLICY");
        } finally {
            engine.execute("DROP USER IF EXISTS held_pw");
        }
    }

    /** A refusal without the sentence about privileges that may follow its first two lines. */
    private static String firstLines(final String message) {
        final int second = message.indexOf('\n');
        final int end = second < 0 ? -1 : message.indexOf(' ', message.indexOf("authorized.", second));
        return end < 0 ? message : message.substring(0, end);
    }
}
