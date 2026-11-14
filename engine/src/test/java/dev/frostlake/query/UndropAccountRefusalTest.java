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
package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * UNDROP ACCOUNT is the organization's administrator's: any other role is refused as one that may not operate on the
 * account, naming no privilege, whether the account is there or not; to the administrator, an account that is not
 * there does not exist, its name as resolved and unquoted. A qualified name is refused for its qualifier, and the
 * statement's own faults where they stand. Every answer is live-verified.
 */
public class UndropAccountRefusalTest extends MeasuredCellsTestSupport {

    @Test
    public void anAccountThatIsNotThereIsNotTheRolesToRestore() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT)");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on account "
                + "'NOSUCH_ACCT_W7'.",
            "UNDROP ACCOUNT nosuch_acct_w7");
        assertAnswer("REFUSED SQL access control error: | Insufficient privileges to operate on account "
                + "'nosuch_acct_w7'.",
            "UNDROP ACCOUNT \"nosuch_acct_w7\"");
        assertAnswer(
            "REFUSED Cannot specify qualifier for account.",
            "UNDROP ACCOUNT nosuch_s.nosuch_acct_w7");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '<EOF>'.",
            "UNDROP ACCOUNT");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'x'.",
            "UNDROP ACCOUNT nosuch_acct_w7 x");
    }

    @Test
    public void toTheOrganizationsAdministratorAMissingAccountDoesNotExist() {
        engine.execute("USE ROLE ORGADMIN");
        assertAnswer("REFUSED SQL compilation error: | Account 'NOSUCH_ACCT_W7' does not exist or not authorized.",
            "UNDROP ACCOUNT nosuch_acct_w7");
        assertAnswer("REFUSED SQL compilation error: | Account 'nosuch_acct_w7' does not exist or not authorized.",
            "UNDROP ACCOUNT \"nosuch_acct_w7\"");
    }
}
