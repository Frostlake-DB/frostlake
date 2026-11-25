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

package dev.frostlake.session;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * What a request of several statements leaves in QUERY_HISTORY (live-verified): the request is a statement of its
 * own, the parent of the statements it runs, with an ID of its own - its row lists the whole text as MULTI_STATEMENT,
 * answering one row on the session's warehouse in no transaction of its own - and each statement it runs has an ID
 * and a row of its own, listed under its own text without the semicolon that ends it, or the space and comments
 * around it - an EXECUTE IMMEDIATE's too, listed with the kind of the statement its text holds. The parent ends
 * last: LAST_QUERY_ID names it, and RESULT_SCAN of its ID reads the request's own answer. A statement that fails
 * ends the request, which fails with it; the statements after it never run.
 */
public class MultiStatementHistoryRowsTest extends MeasuredCellsTestSupport {

    /** The rows of the statements whose text holds a word, the request's first: kind, status, rows, text. */
    private static String listing(final String word) {
        return "SELECT QUERY_TYPE, EXECUTION_STATUS, ROWS_PRODUCED, REPLACE(QUERY_TEXT, CHR(10), ' ') FROM "
            + "TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION(RESULT_LIMIT => 1000)) WHERE CONTAINS(QUERY_TEXT, '"
            + word.substring(0, 2) + "' || '" + word.substring(2) + "') ORDER BY QUERY_TYPE = 'MULTI_STATEMENT' DESC, 4";
    }

    @Test
    public void theRequestAndEachOfItsStatementsHaveRows() {
        engine.execute("CREATE OR REPLACE TABLE msh_rows (a INT)");
        engine.execute("INSERT INTO msh_rows VALUES (336101), (336102) /* a */ ;\n -- b\n SELECT 336103 -- c\n;"
            + " SELECT a FROM msh_rows WHERE a > 336100 ORDER BY a  ");
        assertCells(new String[][] {
            {listing("33610"),
                "MULTI_STATEMENT|SUCCESS|1|INSERT INTO msh_rows VALUES (336101), (336102) /* a */ ;  -- b  SELECT "
                + "336103 -- c ; SELECT a FROM msh_rows WHERE a > 336100 ORDER BY a   ;; INSERT|SUCCESS|2|INSERT INTO "
                + "msh_rows VALUES (336101), (336102) ;; SELECT|SUCCESS|0|SELECT 336103 ;; SELECT|SUCCESS|2|SELECT a "
                + "FROM msh_rows WHERE a > 336100 ORDER BY a"},
            {"SELECT TRANSACTION_ID = 0, WAREHOUSE_SIZE IS NOT NULL, ROWS_INSERTED FROM "
                + "TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION(RESULT_LIMIT => 1000)) WHERE QUERY_TYPE = "
                + "'MULTI_STATEMENT' AND CONTAINS(QUERY_TEXT, '33' || '6101')",
                "true|true|0"},
        });
    }

    @Test
    public void anExecuteImmediateOfTheRequestKeepsItsText() {
        engine.execute("SELECT 336401; EXECUTE IMMEDIATE 'SELECT 336402'");
        assertCells(new String[][] {
            {listing("33640"),
                "MULTI_STATEMENT|SUCCESS|1|SELECT 336401; EXECUTE IMMEDIATE 'SELECT 336402' ;; SELECT|SUCCESS|0|EXECUTE "
                + "IMMEDIATE 'SELECT 336402' ;; SELECT|SUCCESS|0|SELECT 336401"},
        });
    }

    @Test
    public void theRequestEndsLast() {
        engine.execute("SELECT 336301; SELECT 336302");
        assertCells(new String[][] {
            {"SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))", "Multiple statements executed successfully."},
        });
        engine.execute("SELECT 336303; SELECT 336304");
        assertCells(new String[][] {
            {"SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID(-2)))", "336304"},
        });
        engine.execute("SELECT 336305; SELECT 336306");
        assertCells(new String[][] {
            {"SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID(-3)))", "336305"},
        });
        // A statement of the request reads the one before it as the session's last.
        engine.execute("SELECT 336307; SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertCells(new String[][] {
            {"SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID(-2)))", "336307"},
        });
    }

    @Test
    public void aStatementThatFailsEndsTheRequest() {
        engine.execute("CREATE OR REPLACE TABLE msh_failed (a INT)");
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("INSERT INTO msh_failed VALUES (336201); SELECT 336202 / 0; SELECT 336203");
            }
        });
        assertCells(new String[][] {
            {listing("33620"),
                "MULTI_STATEMENT|FAILED_WITH_ERROR|0|INSERT INTO msh_failed VALUES (336201); SELECT 336202 / 0; "
                + "SELECT 336203 ;; INSERT|SUCCESS|1|INSERT INTO msh_failed VALUES (336201) ;; "
                + "SELECT|FAILED_WITH_ERROR|0|SELECT 336202 / 0"},
            {"SELECT ERROR_CODE, ERROR_MESSAGE FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION(RESULT_LIMIT => "
                + "1000)) WHERE QUERY_TEXT = 'SELECT 336202 / 0'",
                "100051|Division by zero"},
            {"SELECT * FROM msh_failed", "336201"},
        });
    }
}
