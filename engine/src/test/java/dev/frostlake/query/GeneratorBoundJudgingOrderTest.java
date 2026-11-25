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
 * GENERATOR's ROWCOUNT and TIMELIMIT are judged in the order the call writes them, each for its constancy before its
 * type, and a refusal numbers the argument by that place (live-verified): a subquery that does not fold is no constant
 * whatever it answers, an aggregate over a table of one row folds, a subquery selecting one item and nothing else is
 * judged as that item, and a DATE, TIME, TIMESTAMP or BINARY bound is refused for its type.
 */
public class GeneratorBoundJudgingOrderTest extends MeasuredCellsTestSupport {

    @Test
    public void rowCountBounds() {
        engine.execute("CREATE OR REPLACE TABLE gt (n INT, v VARCHAR, f FLOAT, b BOOLEAN)");
        engine.execute("INSERT INTO gt VALUES (2, '2', 2.0, TRUE)");
        engine.execute("SET sv = '3'");
        engine.execute("SET sn = 3");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT 3)))",
                "3"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT '3')))",
                "REFUSED SQL compilation error: | invalid type [VARCHAR(1)] for parameter 'ROWCOUNT'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT 3.0::FLOAT)))",
                "3"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT TRUE)))",
                "REFUSED SQL compilation error: | invalid type [BOOLEAN] for parameter 'ROWCOUNT'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT NULL)))",
                "0"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT 'a')))",
                "REFUSED SQL compilation error: | invalid type [VARCHAR(1)] for parameter 'ROWCOUNT'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(x) FROM (SELECT 1 AS x UNION "
                + "ALL SELECT 2))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".X) AS \"MAX(X)\" FROM (SELECT SET_COMBINE(1, 2) AS \"X\" FROM "
                + "(SELECT 1 AS \"X\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL "
                + "(SELECT 2 AS \"2\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(x) FROM (SELECT 'a' AS x UNION "
                + "ALL SELECT 'b'))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".X) AS \"MAX(X)\" FROM (SELECT SET_COMBINE('a', 'b') AS \"X\" "
                + "FROM (SELECT 'a' AS \"X\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION "
                + "ALL (SELECT 'b' AS \"'B'\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) "
                + "values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM gt)))",
                "2"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(v) FROM gt)))",
                "REFUSED SQL compilation error: | invalid type [VARCHAR(16777216)] for parameter 'ROWCOUNT'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(f) FROM gt)))",
                "2"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(b) FROM gt)))",
                "REFUSED SQL compilation error: | invalid type [BOOLEAN] for parameter 'ROWCOUNT'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT v FROM gt)))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT '2' AS \"V\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1)))'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT NULL FROM gt)))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT null AS \"NULL\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1)))'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(v) FROM gt) || 'x'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 73 unexpected '||'. | "
                + "syntax error line 1 at position 80 unexpected ')'."},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM gt) + 1))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 73 unexpected '+'. | syntax"
                + " error line 1 at position 77 unexpected ')'."},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => UPPER((SELECT MAX(v) FROM gt))))",
                "REFUSED SQL compilation error: | invalid type [VARCHAR(50331648)] for parameter 'ROWCOUNT'"},
            {"SELECT COUNT(*) FROM gt, TABLE(GENERATOR(ROWCOUNT => gt.n))",
                "2"},
            {"SELECT COUNT(*) FROM gt, TABLE(GENERATOR(ROWCOUNT => gt.v))",
                "REFUSED SQL compilation error: | invalid type [VARCHAR(16777216)] for parameter 'ROWCOUNT'"},
            {"SELECT COUNT(*) FROM gt, TABLE(GENERATOR(ROWCOUNT => gt.f))",
                "2"},
            {"SELECT COUNT(*) FROM gt, TABLE(GENERATOR(ROWCOUNT => gt.b))",
                "REFUSED SQL compilation error: | invalid type [BOOLEAN] for parameter 'ROWCOUNT'"},
            {"SELECT COUNT(*) FROM gt, TABLE(GENERATOR(ROWCOUNT => gt.v || 'x'))",
                "REFUSED SQL compilation error: | invalid type [VARCHAR(16777217)] for parameter 'ROWCOUNT'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => $sv))",
                "REFUSED SQL compilation error: | invalid type [VARCHAR(1)] for parameter 'ROWCOUNT'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => $sn))",
                "3"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => RANDOM()))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found 'RANDOM()'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => UPPER('1')))",
                "REFUSED SQL compilation error: | invalid type [VARCHAR(3)] for parameter 'ROWCOUNT'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => RANDOM()::VARCHAR))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found 'CAST(RANDOM() AS VARCHAR(134217728))'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => UUID_STRING()))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found 'UUID_STRING()'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => LENGTH('abc')))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found 'LENGTH('abc')'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => LENGTH('abc')::VARCHAR))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found 'CAST(LENGTH('abc') AS VARCHAR(134217728))'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 'a'))",
                "REFUSED SQL compilation error: | invalid type [VARCHAR(1)] for parameter 'ROWCOUNT'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => TRUE))",
                "REFUSED SQL compilation error: | invalid type [BOOLEAN] for parameter 'ROWCOUNT'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 2.5::FLOAT))",
                "2"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => NULL))",
                "0"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => TO_DATE('2024-01-01')))",
                "REFUSED SQL compilation error: | invalid type [DATE] for parameter 'ROWCOUNT'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(x) FROM (SELECT 'a' AS x UNION "
                + "ALL SELECT 'b')), TIMELIMIT => 'x'))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".X) AS \"MAX(X)\" FROM (SELECT SET_COMBINE('a', 'b') AS \"X\" "
                + "FROM (SELECT 'a' AS \"X\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION "
                + "ALL (SELECT 'b' AS \"'B'\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) "
                + "values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 'x', TIMELIMIT => (SELECT MAX(x) FROM "
                + "(SELECT 'a' AS x UNION ALL SELECT 'b'))))",
                "REFUSED SQL compilation error: | invalid type [VARCHAR(1)] for parameter 'ROWCOUNT'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(TIMELIMIT => 'x', ROWCOUNT => (SELECT MAX(x) FROM "
                + "(SELECT 'a' AS x UNION ALL SELECT 'b'))))",
                "REFUSED SQL compilation error: | invalid type [VARCHAR(1)] for parameter 'TIMELIMIT'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(TIMELIMIT => (SELECT MAX(x) FROM (SELECT 'a' AS x UNION"
                + " ALL SELECT 'b')), ROWCOUNT => 'x'))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".X) AS \"MAX(X)\" FROM (SELECT SET_COMBINE('a', 'b') AS \"X\" "
                + "FROM (SELECT 'a' AS \"X\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION "
                + "ALL (SELECT 'b' AS \"'B'\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) "
                + "values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 3, TIMELIMIT => (SELECT MAX(x) FROM (SELECT"
                + " 'a' AS x UNION ALL SELECT 'b'))))",
                "REFUSED SQL compilation error: | argument 2 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".X) AS \"MAX(X)\" FROM (SELECT SET_COMBINE('a', 'b') AS \"X\" "
                + "FROM (SELECT 'a' AS \"X\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION "
                + "ALL (SELECT 'b' AS \"'B'\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) "
                + "values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(x) FROM (SELECT 1 AS x UNION "
                + "ALL SELECT 2)), TIMELIMIT => 'x'))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".X) AS \"MAX(X)\" FROM (SELECT SET_COMBINE(1, 2) AS \"X\" FROM "
                + "(SELECT 1 AS \"X\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL "
                + "(SELECT 2 AS \"2\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => RANDOM(), TIMELIMIT => 'x'))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found 'RANDOM()'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 'x', TIMELIMIT => RANDOM()))",
                "REFUSED SQL compilation error: | invalid type [VARCHAR(1)] for parameter 'ROWCOUNT'"},
            {"EXECUTE IMMEDIATE $$ DECLARE v VARCHAR DEFAULT '3'; r INT; BEGIN SELECT COUNT(*) INTO :r "
                + "FROM TABLE(GENERATOR(ROWCOUNT => :v)); RETURN r; END; $$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 45 : SQL "
                + "compilation error: | invalid type [VARCHAR(1)] for parameter 'ROWCOUNT'"},
            {"EXECUTE IMMEDIATE 'SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => ?))' USING ('3')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 80 unexpected ''3''."},
        });
    }

    @Test
    public void timeLimitBounds() {
        engine.execute("CREATE OR REPLACE TABLE gt (n INT, v VARCHAR, f FLOAT, b BOOLEAN)");
        engine.execute("INSERT INTO gt VALUES (2, '2', 2.0, TRUE)");
        engine.execute("SET sv = '3'");
        engine.execute("SET sn = 3");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(TIMELIMIT => (SELECT '3')))",
                "REFUSED SQL compilation error: | invalid type [VARCHAR(1)] for parameter 'TIMELIMIT'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(TIMELIMIT => (SELECT TRUE)))",
                "REFUSED SQL compilation error: | invalid type [BOOLEAN] for parameter 'TIMELIMIT'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(TIMELIMIT => (SELECT NULL)))",
                "0"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(TIMELIMIT => (SELECT 'a')))",
                "REFUSED SQL compilation error: | invalid type [VARCHAR(1)] for parameter 'TIMELIMIT'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(TIMELIMIT => (SELECT MAX(x) FROM (SELECT 1 AS x UNION "
                + "ALL SELECT 2))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".X) AS \"MAX(X)\" FROM (SELECT SET_COMBINE(1, 2) AS \"X\" FROM "
                + "(SELECT 1 AS \"X\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL "
                + "(SELECT 2 AS \"2\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(TIMELIMIT => (SELECT MAX(x) FROM (SELECT 'a' AS x UNION"
                + " ALL SELECT 'b'))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".X) AS \"MAX(X)\" FROM (SELECT SET_COMBINE('a', 'b') AS \"X\" "
                + "FROM (SELECT 'a' AS \"X\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION "
                + "ALL (SELECT 'b' AS \"'B'\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) "
                + "values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(TIMELIMIT => (SELECT MAX(v) FROM gt)))",
                "REFUSED SQL compilation error: | invalid type [VARCHAR(16777216)] for parameter 'TIMELIMIT'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(TIMELIMIT => (SELECT MAX(b) FROM gt)))",
                "REFUSED SQL compilation error: | invalid type [BOOLEAN] for parameter 'TIMELIMIT'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(TIMELIMIT => (SELECT v FROM gt)))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT '2' AS \"V\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1)))'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(TIMELIMIT => (SELECT NULL FROM gt)))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT null AS \"NULL\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1)))'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(TIMELIMIT => (SELECT MAX(v) FROM gt) || 'x'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 74 unexpected '||'. | "
                + "syntax error line 1 at position 81 unexpected ')'."},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(TIMELIMIT => (SELECT MAX(n) FROM gt) + 1))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 74 unexpected '+'. | syntax"
                + " error line 1 at position 78 unexpected ')'."},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(TIMELIMIT => UPPER((SELECT MAX(v) FROM gt))))",
                "REFUSED SQL compilation error: | invalid type [VARCHAR(50331648)] for parameter 'TIMELIMIT'"},
            {"SELECT COUNT(*) FROM gt, TABLE(GENERATOR(TIMELIMIT => gt.v))",
                "REFUSED SQL compilation error: | invalid type [VARCHAR(16777216)] for parameter 'TIMELIMIT'"},
            {"SELECT COUNT(*) FROM gt, TABLE(GENERATOR(TIMELIMIT => gt.b))",
                "REFUSED SQL compilation error: | invalid type [BOOLEAN] for parameter 'TIMELIMIT'"},
            {"SELECT COUNT(*) FROM gt, TABLE(GENERATOR(TIMELIMIT => gt.v || 'x'))",
                "REFUSED SQL compilation error: | invalid type [VARCHAR(16777217)] for parameter 'TIMELIMIT'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(TIMELIMIT => $sv))",
                "REFUSED SQL compilation error: | invalid type [VARCHAR(1)] for parameter 'TIMELIMIT'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(TIMELIMIT => RANDOM()))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found 'RANDOM()'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(TIMELIMIT => UPPER('1')))",
                "REFUSED SQL compilation error: | invalid type [VARCHAR(3)] for parameter 'TIMELIMIT'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(TIMELIMIT => RANDOM()::VARCHAR))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found 'CAST(RANDOM() AS VARCHAR(134217728))'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(TIMELIMIT => UUID_STRING()))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found 'UUID_STRING()'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(TIMELIMIT => LENGTH('abc')))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found 'LENGTH('abc')'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(TIMELIMIT => LENGTH('abc')::VARCHAR))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found 'CAST(LENGTH('abc') AS VARCHAR(134217728))'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(TIMELIMIT => 'a'))",
                "REFUSED SQL compilation error: | invalid type [VARCHAR(1)] for parameter 'TIMELIMIT'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(TIMELIMIT => TRUE))",
                "REFUSED SQL compilation error: | invalid type [BOOLEAN] for parameter 'TIMELIMIT'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(TIMELIMIT => NULL))",
                "0"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(TIMELIMIT => TO_DATE('2024-01-01')))",
                "REFUSED SQL compilation error: | invalid type [DATE] for parameter 'TIMELIMIT'"},
        });
    }

    @Test
    public void placesAndSubqueryFolding() {
        engine.execute("CREATE OR REPLACE TABLE gt (n INT, v VARCHAR, f FLOAT, b BOOLEAN, d DATE)");
        engine.execute("INSERT INTO gt VALUES (2, '2', 2.0, TRUE, '2024-01-01')");
        engine.execute("CREATE OR REPLACE TABLE ge (v VARCHAR, f FLOAT)");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(TIMELIMIT => 1, ROWCOUNT => RANDOM()))",
                "REFUSED SQL compilation error: | argument 2 to function GENERATOR needs to be constant, "
                + "found 'RANDOM()'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(TIMELIMIT => RANDOM(), ROWCOUNT => 1))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found 'RANDOM()'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(TIMELIMIT => 1, ROWCOUNT => LENGTH('a')))",
                "REFUSED SQL compilation error: | argument 2 to function GENERATOR needs to be constant, "
                + "found 'LENGTH('a')'"},
            {"SELECT COUNT(*) FROM gt, TABLE(GENERATOR(TIMELIMIT => 1, ROWCOUNT => gt.n))",
                "2"},
            {"SELECT COUNT(*) FROM gt, TABLE(GENERATOR(TIMELIMIT => gt.n, ROWCOUNT => 1))",
                "1"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(TIMELIMIT => 1, ROWCOUNT => (SELECT MAX(x) FROM (SELECT"
                + " 1 AS x UNION ALL SELECT 2))))",
                "REFUSED SQL compilation error: | argument 2 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".X) AS \"MAX(X)\" FROM (SELECT SET_COMBINE(1, 2) AS \"X\" FROM "
                + "(SELECT 1 AS \"X\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL "
                + "(SELECT 2 AS \"2\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => RANDOM(), TIMELIMIT => RANDOM()))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found 'RANDOM()'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 1, TIMELIMIT => RANDOM()))",
                "REFUSED SQL compilation error: | argument 2 to function GENERATOR needs to be constant, "
                + "found 'RANDOM()'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 'x', TIMELIMIT => 'y'))",
                "REFUSED SQL compilation error: | invalid type [VARCHAR(1)] for parameter 'ROWCOUNT'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(TIMELIMIT => 'y', ROWCOUNT => 'x'))",
                "REFUSED SQL compilation error: | invalid type [VARCHAR(1)] for parameter 'TIMELIMIT'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(f) FROM gt)))",
                "2"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(f) FROM ge)))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(GE.F) AS \"MAX(F)\" FROM GE AS GE)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(v) FROM ge)))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(GE.V) AS \"MAX(V)\" FROM GE AS GE)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(f) + 1 FROM gt)))",
                "3"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(f * 2) FROM gt)))",
                "4"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(d) FROM gt)))",
                "REFUSED SQL compilation error: | invalid type [DATE] for parameter 'ROWCOUNT'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(b) FROM gt)))",
                "REFUSED SQL compilation error: | invalid type [BOOLEAN] for parameter 'ROWCOUNT'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(v) FROM gt WHERE f > 0)))",
                "REFUSED SQL compilation error: | invalid type [VARCHAR(16777216)] for parameter 'ROWCOUNT'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(f) FROM gt WHERE n > 0)))",
                "2"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(v || 'a') FROM gt)))",
                "REFUSED SQL compilation error: | invalid type [VARCHAR(16777217)] for parameter 'ROWCOUNT'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT v FROM gt LIMIT 1)))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT '2' AS \"V\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1)))'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT 'a' FROM gt LIMIT 1)))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT 'a' AS \"'A'\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1)))'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT 'a' || 'b')))",
                "REFUSED SQL compilation error: | invalid type [VARCHAR(2)] for parameter 'ROWCOUNT'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT UPPER('a'))))",
                "REFUSED SQL compilation error: | invalid type [VARCHAR(3)] for parameter 'ROWCOUNT'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT RANDOM()::VARCHAR)))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found 'CAST(RANDOM() AS VARCHAR(134217728))'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT 'a' FROM gt)))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT 'a' AS \"'A'\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1)))'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT TRUE FROM gt)))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT TRUE AS \"TRUE\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1)))'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT n FROM gt)))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT 2 AS \"N\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1)))'"},
        });
    }

    @Test
    public void oneRowTableFolding() {
        engine.execute("CREATE OR REPLACE TABLE gt (n INT, v VARCHAR, f FLOAT, b BOOLEAN, d DATE)");
        engine.execute("INSERT INTO gt VALUES (2, '2', 2.0, TRUE, '2024-01-01')");
        engine.execute("CREATE OR REPLACE TABLE g2 (n INT, v VARCHAR, f FLOAT)");
        engine.execute("INSERT INTO g2 SELECT 1, REPEAT('z', 100), 1.0 UNION ALL SELECT 3, 'a', 3.0");
        engine.execute("CREATE OR REPLACE TABLE ge (n INT, v VARCHAR, f FLOAT)");
        engine.execute("CREATE OR REPLACE TABLE g1n (n INT, v VARCHAR, f FLOAT)");
        engine.execute("INSERT INTO g1n VALUES (NULL, NULL, NULL)");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM ge)))",
                "0"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM ge)))",
                "0"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(f) FROM ge)))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(GE.F) AS \"MAX(F)\" FROM GE AS GE)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(v) FROM ge)))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(GE.V) AS \"MAX(V)\" FROM GE AS GE)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(v) FROM g2)))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(G2.V) AS \"MAX(V)\" FROM G2 AS G2)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MIN(v) FROM g2)))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MIN(G2.V) AS \"MIN(V)\" FROM G2 AS G2)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(f) FROM g2)))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(G2.F) AS \"MAX(F)\" FROM G2 AS G2)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM g2)))",
                "3"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT SUM(n) FROM g2)))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT SUM(CAST(G2.N AS NUMBER(38,0))) AS \"SUM(N)\" FROM G2 AS G2)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(v) || 'x' FROM g2)))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT (MAX(G2.V)) || 'x' AS \"MAX(V) || 'X'\" FROM G2 AS G2)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT SUM(n) FROM gt)))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT SUM(2) AS \"SUM(N)\" FROM GT AS GT)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(v) FROM gt)))",
                "1"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(RANDOM()) FROM gt)))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(RANDOM()) AS \"MAX(RANDOM())\" FROM GT AS GT)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(f) FROM gt WHERE f > 5)))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(GT.F) AS \"MAX(F)\" FROM GT AS GT WHERE FALSE)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(v) FROM gt WHERE v = 'q')))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(GT.V) AS \"MAX(V)\" FROM GT AS GT WHERE FALSE)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(f) FROM g1n)))",
                "0"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(v) FROM g1n)))",
                "REFUSED SQL compilation error: | invalid type [VARCHAR(16777216)] for parameter 'ROWCOUNT'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(d) FROM gt)))",
                "REFUSED SQL compilation error: | invalid type [DATE] for parameter 'ROWCOUNT'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(b) FROM gt)))",
                "REFUSED SQL compilation error: | invalid type [BOOLEAN] for parameter 'ROWCOUNT'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(f) FROM gt x)))",
                "2"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(gt.f) FROM gt)))",
                "2"},
        });
    }

    @Test
    public void boundTypes() {
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => '2024-01-01 00:00:00'::TIMESTAMP_NTZ))",
                "REFUSED SQL compilation error: | invalid type [TIMESTAMP_NTZ(9)] for parameter 'ROWCOUNT'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => '10:00:00'::TIME))",
                "REFUSED SQL compilation error: | invalid type [TIME(9)] for parameter 'ROWCOUNT'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => TO_BINARY('0A', 'HEX')))",
                "REFUSED SQL compilation error: | invalid type [BINARY(67108864)] for parameter 'ROWCOUNT'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => '2024-01-01'::DATE))",
                "REFUSED SQL compilation error: | invalid type [DATE] for parameter 'ROWCOUNT'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => '2024-01-01 00:00:00'::TIMESTAMP_TZ))",
                "REFUSED SQL compilation error: | invalid type [TIMESTAMP_TZ(9)] for parameter 'ROWCOUNT'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(TIMELIMIT => '2024-01-01'::DATE))",
                "REFUSED SQL compilation error: | invalid type [DATE] for parameter 'TIMELIMIT'"},
        });
    }

    @Test
    public void inlinedSubqueries() {
        engine.execute("CREATE OR REPLACE TABLE gt (n INT, v VARCHAR, f FLOAT, b BOOLEAN, d DATE)");
        engine.execute("INSERT INTO gt VALUES (2, '2', 2.0, TRUE, '2024-01-01')");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT LENGTH('abc'))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found 'LENGTH('abc')'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT ABS(-3))))",
                "3"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT RANDOM())))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found 'RANDOM()'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT (SELECT RANDOM()))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found 'RANDOM()'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT 2 AS x)))",
                "2"},
            {"SELECT COUNT(*) FROM gt, TABLE(GENERATOR(ROWCOUNT => (SELECT gt.n)))",
                "2"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT 'a' || 'b')))",
                "REFUSED SQL compilation error: | invalid type [VARCHAR(2)] for parameter 'ROWCOUNT'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT UUID_STRING())))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found 'UUID_STRING()'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT (SELECT MAX(x) FROM (SELECT 'a' AS "
                + "x UNION ALL SELECT 'b')))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".X) AS \"MAX(X)\" FROM (SELECT SET_COMBINE('a', 'b') AS \"X\" "
                + "FROM (SELECT 'a' AS \"X\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION "
                + "ALL (SELECT 'b' AS \"'B'\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) "
                + "values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT 3 + LENGTH('a'))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '3 + (CAST(LENGTH('a') AS NUMBER(18,0)))'"},
        });
    }
}
