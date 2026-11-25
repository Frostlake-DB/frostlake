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
 * SHOW USER KEY PAIRS lists the key pairs of a user, the session's own when none is named (live-verified). The
 * engine models none, so the listing is its eleven columns and no rows:
 *
 * <pre>
 *   FOR USER u, FOR USER IDENTIFIER($v), IDENTIFIER('U')   the user is looked up: one that is missing is
 *                                                          User 'U' does not exist or not authorized.
 *   FOR USER a.b                                           Object does not exist, or operation cannot be performed.
 *   FOR USER a.b.c.d                                       user identifier 'A.B.C.D' has too many qualifiers
 *   TERSE                                                  read, and changes nothing
 *   LIKE, IN, LIMIT, STARTS WITH, HISTORY                  syntax errors where they stand
 * </pre>
 *
 * <p>The words of the user listings are keywords of the account's: written alone after SHOW or DROP, or as a
 * listing's scope, they are refused where the account refuses them, and they still name a column or a table.
 */
public class ShowUserKeyPairsTest extends MeasuredCellsTestSupport {

    private static final String COLUMN = "|COLUMN|Y|null|N|N|null|null|null|null|null|null";

    @Test
    public void theListingHasElevenColumns() {
        final String columns = "name|VARCHAR(16777216)" + COLUMN
            + " ;; " + "user_name|VARCHAR(16777216)" + COLUMN
            + " ;; " + "fingerprint|VARCHAR(16777216)" + COLUMN
            + " ;; " + "role_scope|VARCHAR(16777216)" + COLUMN
            + " ;; " + "status|VARCHAR(16777216)" + COLUMN
            + " ;; " + "comment|VARCHAR(16777216)" + COLUMN
            + " ;; " + "created_on|TIMESTAMP_LTZ(3)" + COLUMN
            + " ;; " + "created_by|VARCHAR(16777216)" + COLUMN
            + " ;; " + "last_used_on|TIMESTAMP_LTZ(3)" + COLUMN
            + " ;; " + "expires_at|TIMESTAMP_LTZ(3)" + COLUMN
            + " ;; " + "rotated_to|VARCHAR(16777216)" + COLUMN;
        engine.execute("SHOW USER KEY PAIRS");
        assertAnswer(columns, "DESCRIBE RESULT LAST_QUERY_ID()");
        engine.execute("SHOW TERSE USER KEY PAIRS");
        assertAnswer(columns, "DESCRIBE RESULT LAST_QUERY_ID()");
    }

    @Test
    public void theListingAnswersTheUsersKeyPairs() {
        engine.execute("SET kp_u = CURRENT_USER()");
        assertCells(new String[][] {
            {"SHOW USER KEY PAIRS",
                ""},
            {"SHOW USER KEY PAIRS;",
                ""},
            {"SHOW TERSE USER KEY PAIRS",
                ""},
            {"show user key pairs for user identifier($kp_u)",
                ""},
            {"SHOW USER KEY PAIRS ->> SELECT COUNT(*) FROM $1",
                "0"},
            {"SHOW USER KEY PAIRS FOR USER no_such_user_zz",
                "REFUSED SQL compilation error: | User 'NO_SUCH_USER_ZZ' does not exist or not authorized."},
            {"SHOW USER KEY PAIRS FOR USER \"no_such_user_zz\"",
                "REFUSED SQL compilation error: | User '\"no_such_user_zz\"' does not exist or not "
                + "authorized."},
            {"SHOW USER KEY PAIRS FOR USER IDENTIFIER('NO_SUCH_USER_ZZ')",
                "REFUSED SQL compilation error: | User 'NO_SUCH_USER_ZZ' does not exist or not authorized."},
            {"SHOW TERSE USER KEY PAIRS FOR USER no_such_user_zz",
                "REFUSED SQL compilation error: | User 'NO_SUCH_USER_ZZ' does not exist or not authorized."},
            {"SHOW USER KEY PAIRS FOR USER SNOWFLAKE",
                "REFUSED SQL compilation error: | User 'SNOWFLAKE' does not exist or not authorized."},
            {"SHOW USER KEY PAIRS FOR USER a.b",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {"SHOW USER KEY PAIRS FOR USER a.b.c.d",
                "REFUSED SQL compilation error: user identifier 'A.B.C.D' has too many qualifiers"},
            {"SHOW USER KEY PAIRS LIKE 'x'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'LIKE'."},
            {"SHOW USER KEY PAIRS LIMIT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'LIMIT'."},
            {"SHOW USER KEY PAIRS IN ACCOUNT",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'IN'."},
            {"SHOW USER KEY PAIRS STARTS WITH 'a'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'STARTS'."},
            {"SHOW USER KEY PAIRS FOR USER",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '<EOF>'."},
            {"SHOW USER KEY PAIRS FOR",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '<EOF>'."},
            {"SHOW USER KEY PAIRS FOR USER no_such_user_zz x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'x'."},
            {"SHOW USER KEY PAIR",
                "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'PAIR'."},
            {"SHOW USER KEYS",
                "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'KEYS'."},
            {"SHOW USER KEY",
                "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '<EOF>'."},
            {"SHOW USER KEY \"PAIRS\"",
                "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '\"PAIRS\"'."},
            {"SHOW USERS KEY PAIRS",
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'KEY'."},
            {"SHOW USER KEY PAIRS HISTORY",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'HISTORY'."},
        });
    }

    @Test
    public void theUserListingWordsAreKeywords() {
        assertCells(new String[][] {
            {"SHOW WORKLOAD",
                "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected 'WORKLOAD'."},
            {"SHOW PROGRAMMATIC",
                "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected "
                + "'PROGRAMMATIC'."},
            {"SHOW METHODS",
                "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected 'METHODS'."},
            {"SHOW TOKENS",
                "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected 'TOKENS'."},
            {"SHOW PAIRS",
                "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected 'PAIRS'."},
            {"SHOW WORKLOAD IDENTITY AUTHENTICATION METHODS",
                "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected 'WORKLOAD'."},
            {"SHOW KEY",
                "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected 'KEY'."},
            {"SHOW KEY PAIRS",
                "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected 'KEY'."},
            {"SHOW TERSE KEY",
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'KEY'."},
            {"DROP KEY x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected 'KEY'. | "
                + "syntax error line 1 at position 9 unexpected 'x'."},
            {"DROP WORKLOAD x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 5 unexpected 'WORKLOAD'. | "
                + "syntax error line 1 at position 14 unexpected 'x'."},
            {"SHOW TABLES IN WORKLOAD x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'x'."},
            {"SHOW TABLES IN PAIRS x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'x'."},
            {"SHOW FUNCTIONS IN METHODS x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'."},
            {"EXECUTE IMMEDIATE $$BEGIN RETURN 1; END WORKLOAD$$",
                "1"},
            {"EXECUTE IMMEDIATE $$BEGIN RETURN 1; END TOKENS$$",
                "1"},
        });
    }
}
