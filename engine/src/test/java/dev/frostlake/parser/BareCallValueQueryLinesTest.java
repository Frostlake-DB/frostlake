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

package dev.frostlake.parser;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A table function written bare in the FROM of a query used as a value is read as that query's fault (all
 * live-verified): compared in a WHERE, its SELECT, the first token in its first item's call and the token after it,
 * then the bare call's first token; as an IN's list, its SELECT and the bare call's closing parenthesis, in a
 * statement and in a SQL function's body alike; SPLIT_TO_TABLE read as FLATTEN is. An EXISTS, a derived table and a
 * select list's query keep the bare call's own lines.
 */
public class BareCallValueQueryLinesTest extends MeasuredCellsTestSupport {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE ft (a INT)");
    }

    @Test
    public void aBareCallInAValueQuery() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION fa() RETURNS TABLE (v VARIANT) AS $$SELECT a FROM ft WHERE a IN (SELECT "
                + "value FROM FLATTEN(INPUT => PARSE_JSON('[1]')))$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 1 "
                    + "unexpected 'SELECT'. | syntax error line 1 at position 30 unexpected 'SELECT'. | syntax "
                    + "error line 1 at position 82 unexpected ')'."},
            {"SELECT * FROM ft WHERE a = (SELECT COUNT(*) FROM FLATTEN(INPUT => PARSE_JSON('[1]')))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'. | syntax "
                    + "error line 1 at position 41 unexpected '*'. | syntax error line 1 at position 42 unexpected "
                    + "')'. | syntax error line 1 at position 57 unexpected 'INPUT'."},
            {"SELECT * FROM ft WHERE a IN (SELECT value FROM FLATTEN(INPUT => PARSE_JSON('[1]')))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'SELECT'. | syntax "
                    + "error line 1 at position 81 unexpected ')'."},
            {"SELECT * FROM ft WHERE EXISTS (SELECT 1 FROM FLATTEN(INPUT => PARSE_JSON('[1]')))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected '('. | syntax error "
                    + "line 1 at position 79 unexpected ')'."},
            {"SELECT * FROM ft WHERE a = (SELECT COUNT(*) FROM FLATTEN(PARSE_JSON('[1]')))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'. | syntax "
                    + "error line 1 at position 41 unexpected '*'. | syntax error line 1 at position 42 unexpected "
                    + "')'. | syntax error line 1 at position 57 unexpected 'PARSE_JSON'."},
            {"SELECT * FROM ft WHERE a = (SELECT COUNT(*) FROM SPLIT_TO_TABLE('a,b', ','))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'. | syntax "
                    + "error line 1 at position 41 unexpected '*'. | syntax error line 1 at position 42 unexpected "
                    + "')'. | syntax error line 1 at position 64 unexpected ''a,b''."},
            {"SELECT (SELECT COUNT(*) FROM FLATTEN(INPUT => PARSE_JSON('[1]')))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                    + "error line 1 at position 20 unexpected '('."},
            {"SELECT * FROM (SELECT value FROM FLATTEN(INPUT => PARSE_JSON('[1]')))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '('."},
            {"SELECT value FROM FLATTEN(INPUT => PARSE_JSON('[1]'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '('."},
            {"SELECT * FROM ft WHERE a > (SELECT COUNT(*) FROM FLATTEN(INPUT => PARSE_JSON('[1]')))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'. | syntax "
                    + "error line 1 at position 41 unexpected '*'. | syntax error line 1 at position 42 unexpected "
                    + "')'. | syntax error line 1 at position 57 unexpected 'INPUT'."},
            {"SELECT * FROM ft WHERE a IN (SELECT value FROM SPLIT_TO_TABLE('a,b', ','))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'SELECT'. | syntax "
                    + "error line 1 at position 73 unexpected ')'."},
            {"SELECT * FROM ft WHERE EXISTS (SELECT 1 FROM SPLIT_TO_TABLE('a,b', ','))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected '('. | syntax error "
                    + "line 1 at position 71 unexpected ')'."},
            {"SELECT * FROM ft WHERE a IN (SELECT value FROM FLATTEN(PARSE_JSON('[1]')))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'SELECT'. | syntax "
                    + "error line 1 at position 72 unexpected ')'."},
            {"SELECT * FROM ft WHERE a = (SELECT COUNT(*) FROM FLATTEN(INPUT => PARSE_JSON('[1]'), OUTER => TRUE))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'. | syntax "
                    + "error line 1 at position 41 unexpected '*'. | syntax error line 1 at position 42 unexpected "
                    + "')'. | syntax error line 1 at position 57 unexpected 'INPUT'."},
            {"SELECT * FROM ft WHERE a = (SELECT COUNT(*) FROM SPLIT_TO_TABLE('a,b', 1))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'. | syntax "
                    + "error line 1 at position 41 unexpected '*'. | syntax error line 1 at position 42 unexpected "
                    + "')'. | syntax error line 1 at position 64 unexpected ''a,b''."},
            {"SELECT * FROM ft WHERE a = (SELECT COUNT(*) FROM nosuch_fn_x(1, 2))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'. | syntax "
                    + "error line 1 at position 41 unexpected '*'. | syntax error line 1 at position 42 unexpected "
                    + "')'. | syntax error line 1 at position 61 unexpected '1'."},
        });
    }
}
