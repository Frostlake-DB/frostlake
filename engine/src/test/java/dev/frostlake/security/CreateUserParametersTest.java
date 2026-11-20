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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * CREATE USER takes the session's and the object's parameters among its properties, as the documentation lists
 * them, and the new user holds them as one that ALTER USER … SET gave them to. The statement is not run on the
 * live account, so every answer here is the engine's own reading of the documentation: the refusals are the ones
 * ALTER USER … SET makes for the same list, and a name that is no parameter's stays the syntax error it was.
 */
public class CreateUserParametersTest extends MeasuredCellsTestSupport {

    private static final String NOT_LIVE = "CREATE USER is not run on the live account";

    private String level(final String parameter) {
        engine.execute("SHOW PARAMETERS LIKE '" + parameter + "' IN USER cup_u");
        return answer("SELECT \"value\" || '/' || \"level\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
    }

    @Test
    public void theNewUserHoldsTheParameters() {
        assumeFalse(isLiveSnowflake(), NOT_LIVE);
        engine.execute("CREATE OR REPLACE USER cup_u LOGIN_NAME = cup_login TIMEZONE = 'Europe/Warsaw' "
            + "QUERY_TAG = 'tagged' COMMENT = 'c' STATEMENT_TIMEOUT_IN_SECONDS = 600 ROWS_PER_RESULTSET = 5 "
            + "ABORT_DETACHED_QUERY = TRUE DISPLAY_NAME = 'D'");
        try {
            assertEquals("Europe/Warsaw/USER", level("TIMEZONE"));
            assertEquals("tagged/USER", level("QUERY_TAG"));
            assertEquals("600/USER", level("STATEMENT_TIMEOUT_IN_SECONDS"));
            assertEquals("5/USER", level("ROWS_PER_RESULTSET"));
            assertEquals("true/USER", level("ABORT_DETACHED_QUERY"));
            assertEquals("c", engine.getCatalog().getUserExact("CUP_U").getComment());
            engine.execute("ALTER USER cup_u UNSET TIMEZONE");
            assertEquals("tagged/USER", level("QUERY_TAG"));
        } finally {
            engine.execute("DROP USER IF EXISTS cup_u");
        }
    }

    @Test
    public void aRefusedListMakesNoUser() {
        assumeFalse(isLiveSnowflake(), NOT_LIVE);
        assertCells(new String[][] {
            {"CREATE USER cup_r TIMEZONE = 'Nowhere/None'",
                "REFUSED SQL compilation error: | invalid value [Nowhere/None] for parameter 'TIMEZONE'"},
            {"CREATE USER cup_r TIMEZONE = 5",
                "REFUSED SQL compilation error: | invalid value [5] for parameter 'TIMEZONE'"},
            {"CREATE USER cup_r QUERY_TAG = 'a' QUERY_TAG = 'b'",
                "REFUSED SQL compilation error: | duplicate property 'QUERY_TAG';"},
            {"CREATE USER cup_r NOSUCH_PARAMETER = 5",
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'NOSUCH_PARAMETER'."},
            {"CREATE USER cup_r COMMENT = 'c' FIRST_NAME = word",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'FIRST_NAME'."},
            {"CREATE USER cup_r MINS_TO_BYPASS_NETWORK_POLICY = 5", "REFUSED SQL compilation error: | "
                + "syntax error line 1 at position 18 unexpected 'MINS_TO_BYPASS_NETWORK_POLICY'."},
            {"SHOW USERS LIKE 'CUP_R'", ""},
        });
    }
}
