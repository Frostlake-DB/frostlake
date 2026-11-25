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
 * A VALUES list with nothing after it reads the comma that follows as the start of another row (live-verified): only a
 * row may follow, and any other source is refused where the row's reading stops, while a list with an alias, a
 * clause or brackets around it is joined as any source is. LATERAL takes no VALUES list outside brackets. Rows of
 * one list hold as many values each; two unaliased VALUES derived tables share one name; and an alert on new data
 * compiles its condition's CALL before judging its shape, so a procedure that is not there is refused first.
 */
public class BareValuesListCommaTest extends MeasuredCellsTestSupport {

    @Test
    public void commaAfterValuesList() {
        engine.execute("CREATE OR REPLACE TABLE t(a INT)");
        engine.execute("INSERT INTO t VALUES (5)");
        assertCells(new String[][] {
            {"SELECT * FROM VALUES (1), TABLE(FLATTEN(INPUT => PARSE_JSON('[7]')))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'TABLE'."},
            {"SELECT COUNT(*) FROM VALUES (1) v, TABLE(FLATTEN(INPUT => PARSE_JSON('[7]')))",
                "1"},
            {"SELECT COUNT(*) FROM VALUES (1) AS v, TABLE(FLATTEN(INPUT => PARSE_JSON('[7]')))",
                "1"},
            {"SELECT COUNT(*) FROM VALUES (1) v(x), TABLE(FLATTEN(INPUT => PARSE_JSON('[7]')))",
                "1"},
            {"SELECT COUNT(*) FROM (VALUES (1)), TABLE(FLATTEN(INPUT => PARSE_JSON('[7]')))",
                "1"},
            {"SELECT COUNT(*) FROM VALUES (1) JOIN t ON TRUE, TABLE(FLATTEN(INPUT => PARSE_JSON('[7]')))",
                "1"},
            {"SELECT * FROM VALUES (1), TABLE(FLATTEN(INPUT => PARSE_JSON('[7]'))) f",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'TABLE'."},
            {"SELECT * FROM t, VALUES (1), TABLE(FLATTEN(INPUT => PARSE_JSON('[7]')))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'TABLE'."},
            {"SELECT * FROM VALUES (1), TABLE(t)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'TABLE'."},
            {"SELECT * FROM VALUES (1), TABLE(GENERATOR(ROWCOUNT => 1))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'TABLE'."},
            {"SELECT * FROM VALUES (1), t",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 't'. | syntax"
                + " error line 1 at position 27 unexpected '<EOF>'."},
            {"SELECT SUM(a) FROM VALUES (1) v, t",
                "5"},
            {"SELECT * FROM VALUES (1), t AS u",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 't'. | syntax"
                + " error line 1 at position 28 unexpected 'AS'."},
            {"SELECT * FROM VALUES (1), (2), t",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 't'. | syntax"
                + " error line 1 at position 32 unexpected '<EOF>'."},
            {"SELECT SUM(a) FROM VALUES (1), (2) v, t",
                "10"},
            {"SELECT * FROM VALUES (1), t, t",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 't'. | syntax"
                + " error line 1 at position 30 unexpected '<EOF>'."},
            {"SELECT * FROM t, VALUES (1), t u",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 't'. | syntax"
                + " error line 1 at position 31 unexpected 'u'."},
            {"SELECT * FROM t JOIN VALUES (1), t u",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 't'. | syntax"
                + " error line 1 at position 35 unexpected 'u'."},
            {"SELECT * FROM t CROSS JOIN VALUES (1), t u",
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 't'. | syntax"
                + " error line 1 at position 41 unexpected 'u'."},
            {"SELECT SUM(a) FROM t JOIN VALUES (1) ON TRUE, t u",
                "REFUSED SQL compilation error: | ambiguous column name 'A'"},
            {"SELECT SUM(a) FROM VALUES (1) SAMPLE (100), t",
                "5"},
            {"SELECT * FROM VALUES (1), t WHERE a = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 't'. | syntax"
                + " error line 1 at position 28 unexpected 'WHERE'."},
            {"SELECT * FROM VALUES (1), t ORDER BY 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 't'. | syntax"
                + " error line 1 at position 28 unexpected 'ORDER'."},
            {"SELECT * FROM VALUES (1), t.a",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 't'. | syntax"
                + " error line 1 at position 29 unexpected '<EOF>'."},
            {"SELECT * FROM VALUES (1), 'x'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ''x''. | "
                + "syntax error line 1 at position 29 unexpected '<EOF>'."},
            {"SELECT * FROM VALUES (1), \"t\"",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '\"t\"'. | "
                + "syntax error line 1 at position 29 unexpected '<EOF>'."},
            {"SELECT * FROM VALUES (1), t JOIN t u ON TRUE",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 't'. | syntax"
                + " error line 1 at position 28 unexpected 'JOIN'."},
            {"SELECT * FROM VALUES (1), t, VALUES (2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 't'. | syntax"
                + " error line 1 at position 29 unexpected 'VALUES'."},
            {"SELECT * FROM VALUES (1), t u, t",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 't'. | syntax"
                + " error line 1 at position 28 unexpected 'u'."},
            {"SELECT * FROM VALUES (1), t LIMIT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 't'. | syntax"
                + " error line 1 at position 28 unexpected 'LIMIT'."},
            {"SELECT * FROM VALUES (1),",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '<EOF>'."},
            {"SELECT * FROM VALUES (1),\n  t",
                "REFUSED SQL compilation error: | syntax error line 2 at position 2 unexpected 't'. | syntax "
                + "error line 2 at position 3 unexpected '<EOF>'."},
            {"SELECT * FROM VALUES (1), (SELECT 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'."},
            {"SELECT SUM($1) FROM VALUES (1) v, (SELECT 2)",
                "REFUSED SQL compilation error: | ambiguous column name '$1'"},
            {"SELECT * FROM VALUES (1), (SELECT 2) q",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'."},
            {"SELECT * FROM VALUES (1), (SELECT 2) q, t",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'."},
            {"SELECT * FROM VALUES (1), (t u)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'u'."},
            {"SELECT * FROM VALUES (1), (WITH x AS (SELECT 1) SELECT * FROM x)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'WITH'."},
            {"SELECT * FROM VALUES (1), (TABLE(FLATTEN(INPUT => [1])))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'TABLE'."},
            {"SELECT * FROM VALUES (1), LATERAL FLATTEN(INPUT => PARSE_JSON('[7]'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'LATERAL'. | "
                + "syntax error line 1 at position 34 unexpected 'FLATTEN'."},
            {"SELECT COUNT(*) FROM VALUES (1) v, LATERAL FLATTEN(INPUT => PARSE_JSON('[7]'))",
                "1"},
            {"SELECT * FROM VALUES (1), LATERAL (SELECT 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'LATERAL'."},
            {"SELECT SUM($1) FROM VALUES (1) v, LATERAL (SELECT 2)",
                "REFUSED SQL compilation error: | ambiguous column name '$1'"},
            {"SELECT * FROM VALUES (1), LATERAL t",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'LATERAL'. | "
                + "syntax error line 1 at position 34 unexpected 't'."},
            {"SELECT * FROM VALUES (1), VALUES (2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'VALUES'."},
            {"SELECT SUM($1) FROM VALUES (1) v, VALUES (2)",
                "REFUSED SQL compilation error: | ambiguous column name '$1'"},
            {"SELECT * FROM VALUES (1), VALUES (2) w",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'VALUES'."},
            {"SELECT * FROM VALUES (1), IDENTIFIER('t')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'IDENTIFIER'."},
            {"SELECT * FROM VALUES (1), @~",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '@~'."},
            {"SELECT * FROM VALUES (1), FLATTEN(INPUT => PARSE_JSON('[7]'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'FLATTEN'."},
            {"SELECT * FROM VALUES (1), SPLIT_TO_TABLE('a', ',')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected "
                + "'SPLIT_TO_TABLE'."},
            {"SELECT * FROM VALUES (1), SELECT",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'SELECT'."},
            {"SELECT SUM($1) FROM VALUES (1), ((2))",
                "3"},
            {"UPDATE t SET a = 1 FROM VALUES (1), t u",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 't'. | syntax"
                + " error line 1 at position 38 unexpected 'u'."},
            {"DELETE FROM t USING VALUES (1), t u",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 't'. | syntax"
                + " error line 1 at position 34 unexpected 'u'."},
            {"SELECT * FROM t, LATERAL VALUES (1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'VALUES'."},
            {"SELECT COUNT(*) FROM t, LATERAL (VALUES (1))",
                "1"},
            {"SELECT * FROM t JOIN LATERAL VALUES (1) ON TRUE",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'VALUES'."},
            {"SELECT * FROM LATERAL VALUES (1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'VALUES'."},
        });
    }

    @Test
    public void rowWidthsAndAliases() {
        engine.execute("CREATE OR REPLACE TABLE t(a INT)");
        assertCells(new String[][] {
            {"SELECT * FROM VALUES (1), (1, 2)",
                "REFUSED SQL compilation error: | Inconsistent number of values in the VALUES clause "
                + "expecting 1 but got 2"},
            {"SELECT * FROM VALUES (1, 2), (1)",
                "REFUSED SQL compilation error: | Inconsistent number of values in the VALUES clause "
                + "expecting 2 but got 1"},
            {"SELECT * FROM (VALUES (1), (1, 2))",
                "REFUSED SQL compilation error: | Inconsistent number of values in the VALUES clause "
                + "expecting 1 but got 2"},
            {"SELECT * FROM VALUES (1), (1, 2) v",
                "REFUSED SQL compilation error: | Inconsistent number of values in the VALUES clause "
                + "expecting 1 but got 2"},
            {"SELECT * FROM VALUES (1, 2), (3, 4), (5)",
                "REFUSED SQL compilation error: | Inconsistent number of values in the VALUES clause "
                + "expecting 2 but got 1"},
            {"SELECT COUNT(*) FROM VALUES (1, 2), (3, 4)",
                "2"},
            {"SELECT * FROM (SELECT * FROM VALUES (1)), (SELECT * FROM VALUES (2))",
                "REFUSED SQL compilation error: | duplicate alias 'values'"},
            {"SELECT COUNT(*) FROM (SELECT * FROM VALUES (1)), (SELECT * FROM VALUES (2))",
                "REFUSED SQL compilation error: | duplicate alias 'values'"},
            {"SELECT * FROM VALUES (1), (2) v, (SELECT * FROM VALUES (3)) v",
                "REFUSED SQL compilation error: | duplicate alias 'V'"},
            {"SELECT * FROM t WHERE EXISTS (SELECT * FROM (SELECT * FROM VALUES (1)), (SELECT * FROM "
                + "VALUES (2)))",
                "REFUSED SQL compilation error: | duplicate alias 'values'"},
        });
    }

    @Test
    public void newDataAlertCall() {
        engine.execute("CREATE OR REPLACE TABLE t(a INT)");
        engine.execute("CREATE OR REPLACE PROCEDURE p1() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
        engine.execute("CREATE OR REPLACE PROCEDURE p2(x INT) RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN x; END'");
        assertCells(new String[][] {
            {"CREATE OR REPLACE ALERT ay1 WAREHOUSE = COMPUTE_WH IF (EXISTS (CALL p1())) THEN SELECT 1",
                "REFUSED Streaming alert condition must not call a stored procedure or query from a VALUES "
                + "clause"},
            {"CREATE OR REPLACE ALERT ay4 WAREHOUSE = COMPUTE_WH IF (EXISTS (CALL p2('abc'))) THEN SELECT "
                + "1",
                "REFUSED Streaming alert condition must not call a stored procedure or query from a VALUES "
                + "clause"},
            {"CREATE OR REPLACE ALERT ay6 WAREHOUSE = COMPUTE_WH IF (EXISTS (CALL pi())) THEN SELECT 1",
                "REFUSED Streaming alert condition must not call a stored procedure or query from a VALUES "
                + "clause"},
            {"CREATE OR REPLACE ALERT ay7 WAREHOUSE = COMPUTE_WH IF (EXISTS (CALL nosuchproc())) THEN "
                + "SELECT 1",
                "REFUSED SQL compilation error: | Unknown function NOSUCHPROC."},
            {"CREATE OR REPLACE ALERT ay7 WAREHOUSE = COMPUTE_WH IF (EXISTS (CALL nosuchproc(1, 'a'))) "
                + "THEN SELECT * FROM nosuch",
                "REFUSED SQL compilation error: | Unknown function NOSUCHPROC."},
            {"CREATE OR REPLACE ALERT ay9 WAREHOUSE = COMPUTE_WH IF (EXISTS (CALL PUBLIC.nosuchproc())) "
                + "THEN SELECT 1",
                "REFUSED SQL compilation error: | Unknown user-defined function PUBLIC.NOSUCHPROC."},
            {"CREATE OR REPLACE ALERT ay10 WAREHOUSE = COMPUTE_WH IF (EXISTS (CALL p2(1/0))) THEN SELECT 1",
                "REFUSED Streaming alert condition must not call a stored procedure or query from a VALUES "
                + "clause"},
            {"CREATE OR REPLACE ALERT ay12 WAREHOUSE = COMPUTE_WH IF (EXISTS (CALL "
                + "nosuchdb.nosuchsch.nosuchproc())) THEN SELECT 1",
                "REFUSED SQL compilation error: | Unknown user-defined function "
                + "NOSUCHDB.NOSUCHSCH.NOSUCHPROC."},
        });
    }
}
