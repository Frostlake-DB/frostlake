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

package dev.frostlake.features;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * INFORMATION_SCHEMA.POLICY_REFERENCES over a user: the password policy set on the user is its one row, naming the
 * policy where it lives and the user as the entity, with no database or schema of its own. The rows follow the
 * function's page and the rows of the other domains; no account was asked, since the test sets a policy on a user.
 */
public class PolicyReferencesUserTest extends MeasuredCellsTestSupport {

    private static final String EMBEDDED_ONLY = "the test makes users and sets a password policy on them";

    private static String references(final String user, final String domain) {
        return "SELECT * FROM TABLE(SNOWFLAKE.INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => '" + user
            + "', REF_ENTITY_DOMAIN => '" + domain + "'))";
    }

    @Test
    public void aUsersPasswordPolicyIsItsRow() {
        assumeFalse(isLiveSnowflake(), EMBEDDED_ONLY);
        engine.execute("CREATE PASSWORD POLICY pp PASSWORD_MIN_LENGTH = 10");
        engine.execute("CREATE PASSWORD POLICY \"pp two\" PASSWORD_MIN_LENGTH = 10");
        engine.execute("CREATE USER pr_user_u");
        engine.execute("CREATE USER \"pr user low\"");
        assertAnswer("", references("PR_USER_U", "USER"));
        engine.execute("ALTER USER pr_user_u SET PASSWORD POLICY test_db.test_schema.pp");
        engine.execute("ALTER USER \"pr user low\" SET PASSWORD POLICY \"TEST_DB\".\"TEST_SCHEMA\".\"pp two\"");
        final String row = "TEST_DB|TEST_SCHEMA|PP|PASSWORD_POLICY|null|null|PR_USER_U|USER|null|null|null|null|null|"
            + "ACTIVE";
        assertCells(new String[][] {
            {references("\"PR_USER_U\"", "USER"), row},
            {references("pr_user_u", "user"), row},
            {references("\"pr user low\"", "USER"), "TEST_DB|TEST_SCHEMA|pp two|PASSWORD_POLICY|null|null|"
                + "pr user low|USER|null|null|null|null|null|ACTIVE"},
            {"SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'PR_USER_U', "
                + "REF_ENTITY_DOMAIN => 'USER')) WHERE REF_DATABASE_NAME IS NULL AND REF_COLUMN_NAME IS NULL", "1"},
        });
        engine.execute("ALTER USER pr_user_u SET PASSWORD POLICY test_db.test_schema.\"pp two\" FORCE");
        assertAnswer("pp two", "SELECT POLICY_NAME FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES("
            + "REF_ENTITY_NAME => 'PR_USER_U', REF_ENTITY_DOMAIN => 'USER'))");
        engine.execute("ALTER USER pr_user_u UNSET PASSWORD POLICY");
        assertAnswer("", references("PR_USER_U", "USER"));
        engine.execute("DROP USER pr_user_u");
        engine.execute("DROP USER \"pr user low\"");
    }
}
