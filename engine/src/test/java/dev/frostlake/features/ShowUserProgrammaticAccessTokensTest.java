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

/**
 * SHOW USER PROGRAMMATIC ACCESS TOKENS lists the programmatic access tokens of a user, the session's own when none
 * is named (live-verified). The engine models no token, so the listing is its ten columns and no rows:
 *
 * <pre>
 *   FOR USER u, FOR USER IDENTIFIER($v), IDENTIFIER('U')   the user is looked up: one that is missing is
 *                                                          User 'U' does not exist or not authorized.
 *   FOR USER a.b                                           Object does not exist, or operation cannot be performed.
 *   FOR USER a.b.c.d                                       user identifier 'A.B.C.D' has too many qualifiers
 *   TERSE                                                  read, and changes nothing
 *   LIKE, IN, LIMIT, STARTS WITH                           syntax errors where they stand
 *   WORKLOAD ACCESS, PROGRAMMATIC IDENTITY                 syntax errors at the keyword after the word
 * </pre>
 */
public class ShowUserProgrammaticAccessTokensTest extends MeasuredCellsTestSupport {

    private static final String COLUMN = "|COLUMN|Y|null|N|N|null|null|null|null|null|null";

    @Test
    public void theListingHasTenColumns() {
        final String columns = "name|VARCHAR(16777216)" + COLUMN
            + " ;; " + "user_name|VARCHAR(16777216)" + COLUMN
            + " ;; " + "role_restriction|VARCHAR(16777216)" + COLUMN
            + " ;; " + "expires_at|TIMESTAMP_LTZ(3)" + COLUMN
            + " ;; " + "status|VARCHAR(16777216)" + COLUMN
            + " ;; " + "comment|VARCHAR(16777216)" + COLUMN
            + " ;; " + "created_on|TIMESTAMP_LTZ(3)" + COLUMN
            + " ;; " + "created_by|VARCHAR(16777216)" + COLUMN
            + " ;; " + "mins_to_bypass_network_policy_requirement|VARCHAR(16777216)" + COLUMN
            + " ;; " + "rotated_to|VARCHAR(16777216)" + COLUMN;
        engine.execute("SHOW USER PROGRAMMATIC ACCESS TOKENS");
        assertAnswer(columns, "DESCRIBE RESULT LAST_QUERY_ID()");
        engine.execute("SHOW TERSE USER PROGRAMMATIC ACCESS TOKENS");
        assertAnswer(columns, "DESCRIBE RESULT LAST_QUERY_ID()");
    }

    @Test
    public void theListingAnswersTheUsersTokens() {
        engine.execute("SET pat_u = CURRENT_USER()");
        assertCells(new String[][] {
            {"SHOW USER PROGRAMMATIC ACCESS TOKENS",
                ""},
            {"SHOW USER PROGRAMMATIC ACCESS TOKENS;",
                ""},
            {"SHOW TERSE USER PROGRAMMATIC ACCESS TOKENS",
                ""},
            {"show user programmatic access tokens for user identifier($pat_u)",
                ""},
            {"SHOW USER PROGRAMMATIC ACCESS TOKENS ->> SELECT COUNT(*) FROM $1",
                "0"},
            {"SHOW USER PROGRAMMATIC ACCESS TOKENS FOR USER no_such_user_zz",
                "REFUSED SQL compilation error: | User 'NO_SUCH_USER_ZZ' does not exist or not authorized."},
            {"SHOW USER PROGRAMMATIC ACCESS TOKENS FOR USER \"no_such_user_zz\"",
                "REFUSED SQL compilation error: | User '\"no_such_user_zz\"' does not exist or not "
                + "authorized."},
            {"SHOW USER PROGRAMMATIC ACCESS TOKENS FOR USER IDENTIFIER('NO_SUCH_USER_ZZ')",
                "REFUSED SQL compilation error: | User 'NO_SUCH_USER_ZZ' does not exist or not authorized."},
            {"SHOW TERSE USER PROGRAMMATIC ACCESS TOKENS FOR USER no_such_user_zz",
                "REFUSED SQL compilation error: | User 'NO_SUCH_USER_ZZ' does not exist or not authorized."},
            {"SHOW USER PROGRAMMATIC ACCESS TOKENS FOR USER SNOWFLAKE",
                "REFUSED SQL compilation error: | User 'SNOWFLAKE' does not exist or not authorized."},
            {"SHOW USER PROGRAMMATIC ACCESS TOKENS FOR USER a.b",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {"SHOW USER PROGRAMMATIC ACCESS TOKENS FOR USER a.b.c.d",
                "REFUSED SQL compilation error: user identifier 'A.B.C.D' has too many qualifiers"},
            {"SHOW USER PROGRAMMATIC ACCESS TOKENS LIKE 'x'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'LIKE'."},
            {"SHOW USER PROGRAMMATIC ACCESS TOKENS LIMIT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'LIMIT'."},
            {"SHOW USER PROGRAMMATIC ACCESS TOKENS IN ACCOUNT",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'IN'."},
            {"SHOW USER PROGRAMMATIC ACCESS TOKENS STARTS WITH 'a'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'STARTS'."},
            {"SHOW USER PROGRAMMATIC ACCESS TOKENS FOR USER",
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected '<EOF>'."},
            {"SHOW USER PROGRAMMATIC ACCESS TOKENS FOR",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '<EOF>'."},
            {"SHOW USER PROGRAMMATIC ACCESS TOKENS FOR ROLE public",
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'ROLE'."},
            {"SHOW USER PROGRAMMATIC ACCESS TOKENS FOR USER no_such_user_zz x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'x'."},
            {"SHOW USER PROGRAMMATIC ACCESS TOKEN",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'TOKEN'."},
            {"SHOW USER PROGRAMMATIC TOKENS",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'TOKENS'."},
            {"SHOW USER PROGRAMMATIC ACCESS",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '<EOF>'."},
            {"SHOW USER PROGRAMMATIC",
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '<EOF>'."},
            {"SHOW USER PROGRAMMATIC ACCESS \"TOKENS\"",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '\"TOKENS\"'."},
            {"SHOW USER \"PROGRAMMATIC\" ACCESS TOKENS",
                "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected "
                + "'\"PROGRAMMATIC\"'."},
            {"SHOW USER WORKLOAD ACCESS TOKENS",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'ACCESS'."},
            {"SHOW USER PROGRAMMATIC IDENTITY AUTHENTICATION METHODS",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'IDENTITY'."},
            {"SHOW USER FOO ACCESS TOKENS",
                "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'FOO'."},
            {"SHOW USER PAT",
                "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'PAT'."},
            {"SHOW PROGRAMMATIC ACCESS TOKENS",
                "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected "
                + "'PROGRAMMATIC'."},
            {"SHOW USER PROGRAMMATIC FOO",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'FOO'."},
            {"SHOW USER WORKLOAD IDENTITY AUTHENTICATION \"METHODS\"",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected "
                + "'\"METHODS\"'."},
        });
    }
}
