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
 * SHOW USER WORKLOAD IDENTITY AUTHENTICATION METHODS lists the workload identity federation settings of a user,
 * the session's own when none is named (live-verified). A user with no such setting lists nothing, and the engine
 * models none, so the listing is its seven columns and no rows:
 *
 * <pre>
 *   FOR USER u, FOR USER IDENTIFIER($v), IDENTIFIER('U')   the user is looked up: one that is missing is
 *                                                          User 'U' does not exist or not authorized.
 *   FOR USER a.b, a.b.c                                    Object does not exist, or operation cannot be performed.
 *   FOR USER a.b.c.d                                       user identifier 'A.B.C.D' has too many qualifiers
 *   TERSE                                                  read, and changes nothing
 *   LIKE, IN, LIMIT, STARTS WITH                           syntax errors where they stand
 *   FOR, FOR USER with nothing after                       syntax errors at what follows
 * </pre>
 */
public class ShowUserWorkloadIdentityMethodsTest extends MeasuredCellsTestSupport {

    private static final String LISTING = "SHOW USER WORKLOAD IDENTITY AUTHENTICATION METHODS";

    private static final String COLUMN = "|COLUMN|Y|null|N|N|null|null|null|null|null|null";

    private static String syntax(final int position, final String word) {
        return "REFUSED SQL compilation error: | syntax error line 1 at position " + position + " unexpected '" + word
            + "'.";
    }

    private static String missing(final String user) {
        return "REFUSED SQL compilation error: | User '" + user + "' does not exist or not authorized.";
    }

    @Test
    public void theSessionsOwnUserListsNothing() {
        engine.execute("SET wim_u = CURRENT_USER()");
        assertCells(new String[][] {
            {LISTING, ""},
            {LISTING + ";", ""},
            {"SHOW TERSE USER WORKLOAD IDENTITY AUTHENTICATION METHODS", ""},
            {LISTING + " FOR USER IDENTIFIER($wim_u)", ""},
            {LISTING + " ->> SELECT COUNT(*) FROM $1", "0"},
        });
    }

    @Test
    public void theListingHasSevenColumns() {
        final String columns = "name|VARCHAR(16777216)" + COLUMN + " ;; type|VARCHAR(16777216)" + COLUMN
            + " ;; comment|VARCHAR(16777216)" + COLUMN + " ;; last_used|TIMESTAMP_LTZ(3)" + COLUMN
            + " ;; created_on|TIMESTAMP_LTZ(3)" + COLUMN + " ;; additional_info|VARCHAR(16777216)" + COLUMN
            + " ;; status|VARCHAR(16777216)" + COLUMN;
        engine.execute(LISTING);
        assertAnswer(columns, "DESCRIBE RESULT LAST_QUERY_ID()");
        engine.execute("SHOW TERSE USER WORKLOAD IDENTITY AUTHENTICATION METHODS");
        assertAnswer(columns, "DESCRIBE RESULT LAST_QUERY_ID()");
    }

    @Test
    public void aNamedUserIsLookedUp() {
        assertCells(new String[][] {
            {LISTING + " FOR USER no_such_user_zz", missing("NO_SUCH_USER_ZZ")},
            {LISTING + " FOR USER \"no_such_user_zz\"", missing("\"no_such_user_zz\"")},
            {LISTING + " FOR USER IDENTIFIER('NO_SUCH_USER_ZZ')", missing("NO_SUCH_USER_ZZ")},
            {"SHOW TERSE USER WORKLOAD IDENTITY AUTHENTICATION METHODS FOR USER no_such_user_zz",
                missing("NO_SUCH_USER_ZZ")},
            {LISTING + " FOR USER a.b",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {LISTING + " FOR USER a.b.c",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {LISTING + " FOR USER IDENTIFIER('a.b')",
                "REFUSED SQL compilation error: | Object does not exist, or operation cannot be performed."},
            {LISTING + " FOR USER a.b.c.d",
                "REFUSED SQL compilation error: user identifier 'A.B.C.D' has too many qualifiers"},
        });
    }

    @Test
    public void nothingElseIsRead() {
        assertCells(new String[][] {
            {LISTING + " FOR USER IDENTIFIER(CURRENT_USER())", syntax(70, "(")},
            {LISTING + " FOR no_such_user_zz", syntax(55, "no_such_user_zz")},
            {LISTING + " FOR ROLE public", syntax(55, "ROLE")},
            {LISTING + " FOR", syntax(54, "<EOF>")},
            {LISTING + " FOR USER", syntax(59, "<EOF>")},
            {LISTING + " FOR USER 'abc'", syntax(60, "'abc'")},
            {LISTING + " FOR USER 5", syntax(60, "5")},
            {LISTING + " FOR USER no_such_user_zz x", syntax(76, "x")},
            {LISTING + " FOR USER no_such_user_zz LIMIT 1", syntax(76, "LIMIT")},
            {LISTING + " LIKE 'x'", syntax(51, "LIKE")},
            {LISTING + " LIMIT 1", syntax(51, "LIMIT")},
            {LISTING + " IN ACCOUNT", syntax(51, "IN")},
            {LISTING + " STARTS WITH 'A'", syntax(51, "STARTS")},
            {"SHOW USER WORKLOAD IDENTITY AUTHENTICATION METHOD", syntax(43, "METHOD")},
            {"SHOW USER WORKLOADS IDENTITY AUTHENTICATION METHODS", syntax(10, "WORKLOADS")},
            {"SHOW USER WORKLOAD IDENTITY METHODS", syntax(28, "METHODS")},
            {"SHOW USER WORKLOAD IDENTITY AUTHENTICATION", syntax(42, "<EOF>")},
            {"SHOW USER WORKLOAD IDENTITY", syntax(27, "<EOF>")},
            {"SHOW USER WORKLOAD", syntax(18, "<EOF>")},
        });
    }
}
