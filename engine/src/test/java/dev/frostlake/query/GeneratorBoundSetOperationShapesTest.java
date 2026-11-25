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
 * A GENERATOR bound over a set operation echoed as the plan holds it, beyond the shapes of
 * GeneratorBoundSetOperationPlanTest: a UNION BY NAME read as the UNION of its columns; a mixed chain nesting its
 * first run as an operation, a UNION ALL beneath a UNION as itself and anything else selected from as a branch, the
 * WB$/OB$ names numbered as the plan numbers them; a UNION ALL keeping only the columns read, a UNION all of them; a
 * FROM-less branch beside a table's read through ENSURE_NULLABLE from one row of VALUES; a column whose rows hold one
 * value folded to it, over a GENERATOR of the table's rows where every column kept holds one; a filter the statistics
 * prove false as FALSE, or no row where only the rows are counted; SUM of one value, AVG as the plan divides; a literal
 * cast by :: named by its text and read as the literal; an INTERSECT side cast where its type is not the one both
 * sides take (all live-verified).
 */
public class GeneratorBoundSetOperationShapesTest extends MeasuredCellsTestSupport {

    @Test
    public void byNameChainsColumnsAndConstants() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        engine.execute("INSERT INTO t1 VALUES (1), (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE t2 (a INT)");
        engine.execute("INSERT INTO t2 VALUES (4)");
        engine.execute("CREATE OR REPLACE TABLE t3 (a INT)");
        engine.execute("INSERT INTO t3 VALUES (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE tw (a INT, b INT, c INT)");
        engine.execute("INSERT INTO tw VALUES (1, 10, 100), (2, 20, 200)");
        engine.execute("CREATE OR REPLACE TABLE t7 (a INT)");
        engine.execute("INSERT INTO t7 VALUES (7), (7), (7)");
        engine.execute("CREATE OR REPLACE TABLE tn (a INT, b INT)");
        engine.execute("INSERT INTO tn VALUES (1, 5), (2, 6), (3, 7)");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a FROM t1 "
                + "UNION BY NAME SELECT a FROM t3))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A) AS \"A\" "
                + "FROM (SELECT T1.A AS \"A\" FROM T1 AS T1) WB$0 UNION ALL (SELECT T3.A AS \"A\" FROM T3 AS "
                + "T3) WB$1 GROUP BY A) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a FROM t1 "
                + "UNION ALL BY NAME SELECT a FROM t3))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT null AS \"CNULL\" FROM (SELECT null AS "
                + "\"CNULL\" FROM TABLE (GENERATOR(ROWCOUNT => 3, rowCount => 3))) WB$0 UNION ALL (SELECT null "
                + "AS \"CNULL\" FROM TABLE (GENERATOR(ROWCOUNT => 2, rowCount => 2))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 UNION"
                + " ALL BY NAME SELECT a FROM t3))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A) AS "
                + "\"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1) WB$0 UNION ALL (SELECT T3.A AS \"A\" FROM T3"
                + " AS T3) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 1 AS a UNION "
                + "BY NAME SELECT 2 AS a))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT SET_COMBINE(1, 2) AS \"A\" FROM (SELECT"
                + " 1 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL (SELECT 2 "
                + "AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 GROUP BY A) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 1 AS a UNION "
                + "ALL BY NAME SELECT 2 AS a))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT null AS \"CNULL\" FROM (SELECT null AS "
                + "\"CNULL\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL (SELECT null "
                + "AS \"CNULL\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 UNION"
                + " BY NAME SELECT a FROM t3))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A) AS "
                + "\"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1) WB$0 UNION ALL (SELECT T3.A AS \"A\" FROM T3"
                + " AS T3) WB$1 GROUP BY A) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a FROM t1 "
                + "UNION BY NAME SELECT a FROM t2))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT SET_COMBINE(WB$0.A, 4) AS \"A\" FROM "
                + "(SELECT T1.A AS \"A\" FROM T1 AS T1) WB$0 UNION ALL (SELECT 4 AS \"A\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 GROUP BY A) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a FROM t1 "
                + "UNION ALL SELECT a FROM t3 UNION SELECT a FROM t2))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT SET_COMBINE(OB$2.A, 4) AS \"A\" FROM "
                + "(SELECT SET_COMBINE(WB$0.A, WB$1.A) AS \"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1) WB$0 "
                + "UNION ALL (SELECT T3.A AS \"A\" FROM T3 AS T3) WB$1) OB$2 UNION ALL (SELECT 4 AS \"A\" FROM "
                + "TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$2 GROUP BY A) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 1 UNION ALL "
                + "SELECT 2 UNION SELECT 3))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT SET_COMBINE(OB$2.\"1\", 3) AS \"1\" "
                + "FROM (SELECT SET_COMBINE(1, 2) AS \"1\" FROM (SELECT 1 AS \"1\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL (SELECT 2 AS \"2\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) OB$2 UNION ALL (SELECT 3 AS \"3\" FROM "
                + "TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$2 GROUP BY 1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 1 UNION "
                + "SELECT 2 UNION ALL SELECT 3))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT null AS \"CNULL\" FROM (SELECT null AS "
                + "\"CNULL\" FROM (SELECT SET_COMBINE(1, 2) AS \"1\" FROM (SELECT 1 AS \"1\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL (SELECT 2 AS \"2\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 GROUP BY 1) OB$2) WB$2 UNION ALL (SELECT "
                + "null AS \"CNULL\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$3) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a FROM t1 "
                + "UNION SELECT a FROM t3 UNION ALL SELECT a FROM t2))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT null AS \"CNULL\" FROM (SELECT null AS "
                + "\"CNULL\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A) AS \"A\" FROM (SELECT T1.A AS \"A\" FROM "
                + "T1 AS T1) WB$0 UNION ALL (SELECT T3.A AS \"A\" FROM T3 AS T3) WB$1 GROUP BY A) OB$2) WB$2 "
                + "UNION ALL (SELECT null AS \"CNULL\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) "
                + "WB$3) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 UNION"
                + " ALL SELECT a FROM t3 UNION SELECT a FROM t2))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(OB$2.A, 4) AS \"A\" "
                + "FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A) AS \"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1) "
                + "WB$0 UNION ALL (SELECT T3.A AS \"A\" FROM T3 AS T3) WB$1) OB$2 UNION ALL (SELECT 4 AS \"A\" "
                + "FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$2 GROUP BY A) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 UNION"
                + " SELECT a FROM t3 UNION ALL SELECT a FROM t2))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$2.A, 4) AS \"A\" "
                + "FROM (SELECT OB$2.A AS \"A\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A) AS \"A\" FROM (SELECT "
                + "T1.A AS \"A\" FROM T1 AS T1) WB$0 UNION ALL (SELECT T3.A AS \"A\" FROM T3 AS T3) WB$1 GROUP "
                + "BY A) OB$2) WB$2 UNION ALL (SELECT 4 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount "
                + "=> 1))) WB$3) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a, b FROM tw "
                + "UNION ALL SELECT a, b FROM tn))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A) AS "
                + "\"A\" FROM (SELECT TW.A AS \"A\" FROM TW AS TW) WB$0 UNION ALL (SELECT TN.A AS \"A\" FROM TN"
                + " AS TN) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a, b FROM tw "
                + "UNION SELECT a, b FROM tn))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A) AS \"A\", "
                + "SET_COMBINE(WB$0.B, WB$1.B) AS \"B\" FROM (SELECT TW.A AS \"A\", TW.B AS \"B\" FROM TW AS "
                + "TW) WB$0 UNION ALL (SELECT TN.A AS \"A\", TN.B AS \"B\" FROM TN AS TN) WB$1 GROUP BY A, B) "
                + "values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(b) FROM (SELECT a, b FROM tw "
                + "UNION ALL SELECT a, b FROM tn))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".B) AS \"MAX(B)\" FROM (SELECT SET_COMBINE(WB$0.B, WB$1.B) AS "
                + "\"B\" FROM (SELECT TW.B AS \"B\" FROM TW AS TW) WB$0 UNION ALL (SELECT TN.B AS \"B\" FROM TN"
                + " AS TN) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a, b FROM tw "
                + "UNION SELECT a, b FROM tn))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A) AS "
                + "\"A\", SET_COMBINE(WB$0.B, WB$1.B) AS \"B\" FROM (SELECT TW.A AS \"A\", TW.B AS \"B\" FROM "
                + "TW AS TW) WB$0 UNION ALL (SELECT TN.A AS \"A\", TN.B AS \"B\" FROM TN AS TN) WB$1 GROUP BY "
                + "A, B) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a, b FROM tw "
                + "UNION ALL SELECT a, b FROM tn))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT null AS \"CNULL\" FROM (SELECT null AS "
                + "\"CNULL\" FROM TABLE (GENERATOR(ROWCOUNT => 2, rowCount => 2))) WB$0 UNION ALL (SELECT null "
                + "AS \"CNULL\" FROM TABLE (GENERATOR(ROWCOUNT => 3, rowCount => 3))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a, b, c FROM tw"
                + " UNION ALL SELECT a, b, b FROM tn))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A) AS "
                + "\"A\" FROM (SELECT TW.A AS \"A\" FROM TW AS TW) WB$0 UNION ALL (SELECT TN.A AS \"A\" FROM TN"
                + " AS TN) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 UNION"
                + " ALL (SELECT a FROM t3 UNION ALL SELECT a FROM t1)))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$2.A, OB$3.A) AS "
                + "\"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1) WB$2 UNION ALL (SELECT SET_COMBINE(WB$0.A, "
                + "WB$1.A) AS \"A\" FROM (SELECT T3.A AS \"A\" FROM T3 AS T3) WB$0 UNION ALL (SELECT T1.A AS "
                + "\"A\" FROM T1 AS T1) WB$1) OB$3) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 UNION"
                + " ALL (SELECT a FROM t3 INTERSECT SELECT a FROM t1)))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$2.A, WB$3.A) AS "
                + "\"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1) WB$2 UNION ALL (SELECT OB$3.A AS \"A\" FROM "
                + "(SELECT WB$0.A AS \"A\" FROM (SELECT T3.A AS \"A\" FROM T3 AS T3) WB$0 SEMI JOIN (SELECT "
                + "T1.A AS \"A\" FROM T1 AS T1) WB$1 ON (WB$0.A = WB$1.A) GROUP BY A) OB$3) WB$3) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a FROM t1 "
                + "UNION SELECT 1))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.\"1\") AS "
                + "\"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1) WB$0 UNION ALL (SELECT ENSURE_NULLABLE(1) AS"
                + " \"1\" FROM (VALUES (null)) DUAL) WB$1 GROUP BY A) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 UNION"
                + " ALL SELECT 1))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.\"1\") "
                + "AS \"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1) WB$0 UNION ALL (SELECT ENSURE_NULLABLE(1)"
                + " AS \"1\" FROM (VALUES (null)) DUAL) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a FROM t1 "
                + "UNION SELECT 5))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.\"5\") AS "
                + "\"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1) WB$0 UNION ALL (SELECT ENSURE_NULLABLE(5) AS"
                + " \"5\" FROM (VALUES (null)) DUAL) WB$1 GROUP BY A) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 1 UNION "
                + "SELECT a FROM t1))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT SET_COMBINE(WB$0.\"1\", WB$1.A) AS "
                + "\"1\" FROM (SELECT ENSURE_NULLABLE(1) AS \"1\" FROM (VALUES (null)) DUAL) WB$0 UNION ALL "
                + "(SELECT T1.A AS \"A\" FROM T1 AS T1) WB$1 GROUP BY 1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 UNION"
                + " ALL SELECT 22))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.\"22\")"
                + " AS \"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1) WB$0 UNION ALL (SELECT "
                + "ENSURE_NULLABLE(22) AS \"22\" FROM (VALUES (null)) DUAL) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a FROM t7 "
                + "UNION ALL SELECT a FROM t1))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT null AS \"CNULL\" FROM (SELECT null AS "
                + "\"CNULL\" FROM TABLE (GENERATOR(ROWCOUNT => 3, rowCount => 3))) WB$0 UNION ALL (SELECT null "
                + "AS \"CNULL\" FROM TABLE (GENERATOR(ROWCOUNT => 3, rowCount => 3))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t7 UNION"
                + " ALL SELECT a FROM t1))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(7, WB$1.A) AS \"A\" "
                + "FROM (SELECT 7 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 3, rowCount => 3))) WB$0 UNION ALL"
                + " (SELECT T1.A AS \"A\" FROM T1 AS T1) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a FROM t7 "
                + "UNION SELECT a FROM t1))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT SET_COMBINE(7, WB$1.A) AS \"A\" FROM "
                + "(SELECT 7 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 3, rowCount => 3))) WB$0 UNION ALL "
                + "(SELECT T1.A AS \"A\" FROM T1 AS T1) WB$1 GROUP BY A) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a FROM t1 "
                + "WHERE a > 10 UNION ALL SELECT a FROM t3))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT null AS \"CNULL\" FROM (SELECT null AS "
                + "\"CNULL\" FROM TABLE (GENERATOR(ROWCOUNT => 0, rowCount => 0))) WB$0 UNION ALL (SELECT null "
                + "AS \"CNULL\" FROM TABLE (GENERATOR(ROWCOUNT => 2, rowCount => 2))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 WHERE"
                + " a > 10 UNION ALL SELECT a FROM t3))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A) AS "
                + "\"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1 WHERE FALSE) WB$0 UNION ALL (SELECT T3.A AS "
                + "\"A\" FROM T3 AS T3) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t7 UNION"
                + " ALL SELECT a FROM t7))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(7) AS \"MAX(A)\" FROM (SELECT 7 AS \"A\" FROM (SELECT 7 AS \"A\" FROM "
                + "TABLE (GENERATOR(ROWCOUNT => 3, rowCount => 3))) WB$0 UNION ALL (SELECT 7 AS \"A\" FROM "
                + "TABLE (GENERATOR(ROWCOUNT => 3, rowCount => 3))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT SUM(b) FROM (SELECT a, b FROM tw "
                + "UNION ALL SELECT a, b FROM tn))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT SUM(CAST(\"values\".B AS NUMBER(38,0))) AS \"SUM(B)\" FROM (SELECT "
                + "SET_COMBINE(WB$0.B, WB$1.B) AS \"B\" FROM (SELECT TW.B AS \"B\" FROM TW AS TW) WB$0 UNION "
                + "ALL (SELECT TN.B AS \"B\" FROM TN AS TN) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT SUM(a) FROM (SELECT a FROM t1 UNION"
                + " ALL SELECT a FROM t3))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT SUM(CAST(\"values\".A AS NUMBER(38,0))) AS \"SUM(A)\" FROM (SELECT "
                + "SET_COMBINE(WB$0.A, WB$1.A) AS \"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1) WB$0 UNION "
                + "ALL (SELECT T3.A AS \"A\" FROM T3 AS T3) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT SUM(a) FROM (SELECT 4 AS a "
                + "INTERSECT SELECT 4))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT SUM(4) AS \"SUM(A)\" FROM (SELECT 4 AS \"A\" FROM (SELECT 4 AS \"A\" FROM "
                + "TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 SEMI JOIN (SELECT 4 AS \"4\" FROM "
                + "TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 ON (WB$0.A = WB$1.\"4\") GROUP BY 4) "
                + "values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT a FROM t1 UNION"
                + " ALL SELECT a FROM t3))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT SCALED_ROUND_INT_DIVIDE(CAST(SUM(CAST(\"values\".A AS NUMBER(38,0))) AS "
                + "NUMBER(38,0)), CAST(COUNT(\"values\".A) AS NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT "
                + "SET_COMBINE(WB$0.A, WB$1.A) AS \"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1) WB$0 UNION "
                + "ALL (SELECT T3.A AS \"A\" FROM T3 AS T3) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT 1 AS a UNION "
                + "ALL SELECT 2))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT (CAST(CAST(SUM(CAST(\"values\".A AS NUMBER(1,0))) AS NUMBER(19,6)) AS "
                + "NUMBER(19,6))) / (CAST(COUNT(\"values\".A) AS NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT "
                + "SET_COMBINE(1, 2) AS \"A\" FROM (SELECT 1 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, "
                + "rowCount => 1))) WB$0 UNION ALL (SELECT 2 AS \"2\" FROM TABLE (GENERATOR(ROWCOUNT => 1, "
                + "rowCount => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT SUM(a) FROM (SELECT a FROM t1 UNION"
                + " SELECT a FROM t3))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT SUM(CAST(\"values\".A AS NUMBER(38,0))) AS \"SUM(A)\" FROM (SELECT "
                + "SET_COMBINE(WB$0.A, WB$1.A) AS \"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1) WB$0 UNION "
                + "ALL (SELECT T3.A AS \"A\" FROM T3 AS T3) WB$1 GROUP BY A) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT a FROM t1 UNION"
                + " SELECT a FROM t3))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT SCALED_ROUND_INT_DIVIDE(CAST(SUM(CAST(\"values\".A AS NUMBER(38,0))) AS "
                + "NUMBER(38,0)), CAST(COUNT(\"values\".A) AS NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT "
                + "SET_COMBINE(WB$0.A, WB$1.A) AS \"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1) WB$0 UNION "
                + "ALL (SELECT T3.A AS \"A\" FROM T3 AS T3) WB$1 GROUP BY A) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT SUM(a) FROM (SELECT 4 AS a UNION "
                + "SELECT 4))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT SUM(4) AS \"SUM(A)\" FROM (SELECT 4 AS \"A\" FROM (SELECT 4 AS \"A\" FROM "
                + "TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL (SELECT 4 AS \"4\" FROM "
                + "TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 GROUP BY 4) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 'a' INTERSECT"
                + " SELECT 'a'::VARCHAR(5)))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT 'a' AS \"'A'\" FROM (SELECT 'a' AS "
                + "\"'A'\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 SEMI JOIN (SELECT 'a' AS "
                + "\"'A'::VARCHAR(5)\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 ON "
                + "(WB$0.\"'A'\" = WB$1.\"'A'::VARCHAR(5)\") GROUP BY 'a') values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 'ab' "
                + "INTERSECT SELECT 'ab'::VARCHAR(5)))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT 'ab' AS \"'AB'\" FROM (SELECT 'ab' AS "
                + "\"'AB'\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 SEMI JOIN (SELECT 'ab' "
                + "AS \"'AB'::VARCHAR(5)\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 ON "
                + "(WB$0.\"'AB'\" = WB$1.\"'AB'::VARCHAR(5)\") GROUP BY 'ab') values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT "
                + "'a'::VARCHAR(5) INTERSECT SELECT 'a'))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT 'a' AS \"'A'::VARCHAR(5)\" FROM (SELECT"
                + " 'a' AS \"'A'::VARCHAR(5)\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 SEMI "
                + "JOIN (SELECT 'a' AS \"'A'\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 ON "
                + "(WB$0.\"'A'::VARCHAR(5)\" = WB$1.\"'A'\") GROUP BY 'a') values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 1 INTERSECT "
                + "SELECT 1::NUMBER(5,0)))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT 1 AS \"1\" FROM (SELECT 1 AS \"1\" FROM"
                + " TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 SEMI JOIN (SELECT 1 AS "
                + "\"1::NUMBER(5,0)\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 ON (WB$0.\"1\""
                + " = WB$1.\"1::NUMBER(5,0)\") GROUP BY 1) values)'"},
        });
    }

    @Test
    public void nestingFoldingAndAverages() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        engine.execute("INSERT INTO t1 VALUES (1), (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE t2 (a INT)");
        engine.execute("INSERT INTO t2 VALUES (4)");
        engine.execute("CREATE OR REPLACE TABLE t3 (a INT)");
        engine.execute("INSERT INTO t3 VALUES (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE tw (a INT, b INT, c INT)");
        engine.execute("INSERT INTO tw VALUES (1, 10, 100), (2, 20, 200)");
        engine.execute("CREATE OR REPLACE TABLE t7 (a INT)");
        engine.execute("INSERT INTO t7 VALUES (7), (7), (7)");
        engine.execute("CREATE OR REPLACE TABLE tn (a INT, b INT)");
        engine.execute("INSERT INTO tn VALUES (1, 5), (2, 6), (3, 7)");
        engine.execute("CREATE OR REPLACE TABLE tx (a INT, b INT)");
        engine.execute("INSERT INTO tx VALUES (7, 1), (7, 2), (7, 3)");
        engine.execute("CREATE OR REPLACE TABLE tz (a INT)");
        engine.execute("INSERT INTO tz VALUES (7), (NULL), (7)");
        engine.execute("CREATE OR REPLACE TABLE td (d NUMBER(10,2), e NUMBER(20,0))");
        engine.execute("INSERT INTO td VALUES (1.5, 10), (2.5, 20)");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(b) FROM (SELECT a, b FROM tx "
                + "UNION ALL SELECT a, b FROM tn))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".B) AS \"MAX(B)\" FROM (SELECT SET_COMBINE(WB$0.B, WB$1.B) AS "
                + "\"B\" FROM (SELECT TX.B AS \"B\" FROM TX AS TX) WB$0 UNION ALL (SELECT TN.B AS \"B\" FROM TN"
                + " AS TN) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a, b FROM tx "
                + "UNION ALL SELECT a, b FROM tn))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(7, WB$1.A) AS \"A\" "
                + "FROM (SELECT 7 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 3, rowCount => 3))) WB$0 UNION ALL"
                + " (SELECT TN.A AS \"A\" FROM TN AS TN) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a, b FROM tx "
                + "UNION SELECT a, b FROM tn))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT SET_COMBINE(7, WB$1.A) AS \"A\", "
                + "SET_COMBINE(WB$0.B, WB$1.B) AS \"B\" FROM (SELECT 7 AS \"A\", TX.B AS \"B\" FROM TX AS TX) "
                + "WB$0 UNION ALL (SELECT TN.A AS \"A\", TN.B AS \"B\" FROM TN AS TN) WB$1 GROUP BY A, B) "
                + "values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM tx UNION"
                + " ALL SELECT a FROM t1))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(7, WB$1.A) AS \"A\" "
                + "FROM (SELECT 7 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 3, rowCount => 3))) WB$0 UNION ALL"
                + " (SELECT T1.A AS \"A\" FROM T1 AS T1) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 UNION"
                + " ALL SELECT a FROM t3 UNION SELECT 9))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(OB$2.A, WB$2.\"9\") "
                + "AS \"A\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A) AS \"A\" FROM (SELECT T1.A AS \"A\" FROM "
                + "T1 AS T1) WB$0 UNION ALL (SELECT T3.A AS \"A\" FROM T3 AS T3) WB$1) OB$2 UNION ALL (SELECT "
                + "ENSURE_NULLABLE(9) AS \"9\" FROM (VALUES (null)) DUAL) WB$2 GROUP BY A) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t2 UNION"
                + " ALL SELECT 9))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(4, WB$1.\"9\") AS "
                + "\"A\" FROM (SELECT 4 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 "
                + "UNION ALL (SELECT ENSURE_NULLABLE(9) AS \"9\" FROM (VALUES (null)) DUAL) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t7 UNION"
                + " ALL SELECT 9))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(7, WB$1.\"9\") AS "
                + "\"A\" FROM (SELECT 7 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 3, rowCount => 3))) WB$0 "
                + "UNION ALL (SELECT ENSURE_NULLABLE(9) AS \"9\" FROM (VALUES (null)) DUAL) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(x) FROM (SELECT 1 AS x UNION "
                + "ALL SELECT 1 UNION SELECT 3))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".X) AS \"MAX(X)\" FROM (SELECT SET_COMBINE(1, 3) AS \"X\" FROM "
                + "(SELECT 1 AS \"X\" FROM (SELECT 1 AS \"X\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => "
                + "1))) WB$0 UNION ALL (SELECT 1 AS \"1\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1)))"
                + " WB$1) OB$2 UNION ALL (SELECT 3 AS \"3\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => "
                + "1))) WB$2 GROUP BY X) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a FROM t1 "
                + "UNION ALL (SELECT a FROM t3 UNION ALL SELECT a FROM t1)))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT null AS \"CNULL\" FROM (SELECT null AS "
                + "\"CNULL\" FROM TABLE (GENERATOR(ROWCOUNT => 3, rowCount => 3))) WB$2 UNION ALL (SELECT null "
                + "AS \"CNULL\" FROM (SELECT null AS \"CNULL\" FROM TABLE (GENERATOR(ROWCOUNT => 2, rowCount =>"
                + " 2))) WB$0 UNION ALL (SELECT null AS \"CNULL\" FROM TABLE (GENERATOR(ROWCOUNT => 3, rowCount"
                + " => 3))) WB$1) OB$3) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 UNION"
                + " ALL SELECT a FROM t3 UNION SELECT a FROM t2 UNION ALL SELECT a FROM t3))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$3.A, WB$4.A) AS "
                + "\"A\" FROM (SELECT OB$4.A AS \"A\" FROM (SELECT SET_COMBINE(OB$2.A, 4) AS \"A\" FROM (SELECT"
                + " SET_COMBINE(WB$0.A, WB$1.A) AS \"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1) WB$0 UNION "
                + "ALL (SELECT T3.A AS \"A\" FROM T3 AS T3) WB$1) OB$2 UNION ALL (SELECT 4 AS \"A\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$2 GROUP BY A) OB$4) WB$3 UNION ALL (SELECT "
                + "T3.A AS \"A\" FROM T3 AS T3) WB$4) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a, b FROM tw "
                + "UNION ALL SELECT 1, 2))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.\"1\") "
                + "AS \"A\" FROM (SELECT TW.A AS \"A\" FROM TW AS TW) WB$0 UNION ALL (SELECT ENSURE_NULLABLE(1)"
                + " AS \"1\" FROM (VALUES (null)) DUAL) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a, b FROM tw "
                + "UNION SELECT 1, 2))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.\"1\") AS "
                + "\"A\", SET_COMBINE(WB$0.B, WB$1.\"2\") AS \"B\" FROM (SELECT TW.A AS \"A\", TW.B AS \"B\" "
                + "FROM TW AS TW) WB$0 UNION ALL (SELECT ENSURE_NULLABLE(1) AS \"1\", ENSURE_NULLABLE(2) AS "
                + "\"2\" FROM (VALUES (null)) DUAL) WB$1 GROUP BY A, B) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT 10 AS a UNION "
                + "ALL SELECT 20))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT (CAST(SUM(CAST(\"values\".A AS NUMBER(2,0))) AS NUMBER(20,6))) / "
                + "(CAST(COUNT(\"values\".A) AS NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT SET_COMBINE(10, 20) "
                + "AS \"A\" FROM (SELECT 10 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0"
                + " UNION ALL (SELECT 20 AS \"20\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) "
                + "values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(d) FROM (SELECT d FROM td UNION"
                + " ALL SELECT d FROM td))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT (CAST(SUM(CAST(\"values\".D AS NUMBER(10,2))) AS NUMBER(28,8))) / "
                + "(CAST(COUNT(\"values\".D) AS NUMBER(18,0))) AS \"AVG(D)\" FROM (SELECT SET_COMBINE(WB$0.D, "
                + "WB$1.D) AS \"D\" FROM (SELECT TD.D AS \"D\" FROM TD AS TD) WB$0 UNION ALL (SELECT TD.D AS "
                + "\"D\" FROM TD AS TD) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(e) FROM (SELECT e FROM td UNION"
                + " ALL SELECT e FROM td))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT (CAST(SUM(CAST(\"values\".E AS NUMBER(20,0))) AS NUMBER(38,6))) / "
                + "(CAST(COUNT(\"values\".E) AS NUMBER(18,0))) AS \"AVG(E)\" FROM (SELECT SET_COMBINE(WB$0.E, "
                + "WB$1.E) AS \"E\" FROM (SELECT TD.E AS \"E\" FROM TD AS TD) WB$0 UNION ALL (SELECT TD.E AS "
                + "\"E\" FROM TD AS TD) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT 4 AS a "
                + "INTERSECT SELECT 4))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT (CAST(CAST(SUM(4) AS NUMBER(19,6)) AS NUMBER(19,6))) / (CAST(COUNT(4) AS "
                + "NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT 4 AS \"A\" FROM (SELECT 4 AS \"A\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 SEMI JOIN (SELECT 4 AS \"4\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 ON (WB$0.A = WB$1.\"4\") GROUP BY 4) "
                + "values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT SUM(d) FROM (SELECT d FROM td UNION"
                + " ALL SELECT d FROM td))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT SUM(CAST(\"values\".D AS NUMBER(10,2))) AS \"SUM(D)\" FROM (SELECT "
                + "SET_COMBINE(WB$0.D, WB$1.D) AS \"D\" FROM (SELECT TD.D AS \"D\" FROM TD AS TD) WB$0 UNION "
                + "ALL (SELECT TD.D AS \"D\" FROM TD AS TD) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 'a' UNION "
                + "SELECT 'a'::VARCHAR(5)))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT 'a' AS \"'A'\" FROM (SELECT 'a' AS "
                + "\"'A'\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL (SELECT 'a' AS "
                + "\"'A'::VARCHAR(5)\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 GROUP BY 'a')"
                + " values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a FROM t1 "
                + "INTERSECT SELECT 1::NUMBER(5,0)))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT WB$0.A AS \"A\" FROM (SELECT T1.A AS "
                + "\"A\" FROM T1 AS T1) WB$0 SEMI JOIN (SELECT 1 AS \"1::NUMBER(5,0)\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 ON (WB$0.A = WB$1.\"1::NUMBER(5,0)\") GROUP "
                + "BY A) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 'a' INTERSECT"
                + " SELECT 'b'::VARCHAR(5)))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT 'a' AS \"'A'\" FROM (SELECT 'a' AS "
                + "\"'A'\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 SEMI JOIN (SELECT 'b' AS "
                + "\"'B'::VARCHAR(5)\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 ON "
                + "(WB$0.\"'A'\" = WB$1.\"'B'::VARCHAR(5)\") GROUP BY 'a') values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM tz UNION"
                + " ALL SELECT a FROM t1))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A) AS "
                + "\"A\" FROM (SELECT TZ.A AS \"A\" FROM TZ AS TZ) WB$0 UNION ALL (SELECT T1.A AS \"A\" FROM T1"
                + " AS T1) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a FROM t7 "
                + "EXCEPT SELECT a FROM t1))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT 7 AS \"A\" FROM (SELECT 7 AS \"A\" FROM"
                + " TABLE (GENERATOR(ROWCOUNT => 3, rowCount => 3))) WB$0 GROUP BY 7) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a FROM t7 "
                + "INTERSECT SELECT a FROM t1))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT 7 AS \"A\" FROM (SELECT 7 AS \"A\" FROM"
                + " TABLE (GENERATOR(ROWCOUNT => 3, rowCount => 3))) WB$0 SEMI JOIN (SELECT T1.A AS \"A\" FROM "
                + "T1 AS T1) WB$1 ON (WB$0.A = WB$1.A) GROUP BY 7) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a FROM t1 "
                + "WHERE a = 5 UNION ALL SELECT a FROM t3))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT null AS \"CNULL\" FROM (SELECT null AS "
                + "\"CNULL\" FROM TABLE (GENERATOR(ROWCOUNT => 0, rowCount => 0))) WB$0 UNION ALL (SELECT null "
                + "AS \"CNULL\" FROM TABLE (GENERATOR(ROWCOUNT => 2, rowCount => 2))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a FROM t1 "
                + "WHERE a = 2 UNION ALL SELECT a FROM t3))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT null AS \"CNULL\" FROM (SELECT null AS "
                + "\"CNULL\" FROM T1 AS T1 WHERE T1.A = 2) WB$0 UNION ALL (SELECT null AS \"CNULL\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 2, rowCount => 2))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT SUM(a) FROM (SELECT a FROM t1 UNION"
                + " ALL SELECT 1))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT SUM(CAST(\"values\".A AS NUMBER(38,0))) AS \"SUM(A)\" FROM (SELECT "
                + "SET_COMBINE(WB$0.A, WB$1.\"1\") AS \"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1) WB$0 "
                + "UNION ALL (SELECT ENSURE_NULLABLE(1) AS \"1\" FROM (VALUES (null)) DUAL) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a FROM t1 "
                + "UNION ALL BY NAME SELECT a FROM t3 UNION SELECT a FROM t2))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT SET_COMBINE(OB$2.A, 4) AS \"A\" FROM "
                + "(SELECT SET_COMBINE(WB$0.A, WB$1.A) AS \"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1) WB$0 "
                + "UNION ALL (SELECT T3.A AS \"A\" FROM T3 AS T3) WB$1) OB$2 UNION ALL (SELECT 4 AS \"A\" FROM "
                + "TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$2 GROUP BY A) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a, b FROM tw "
                + "UNION SELECT a, b FROM tn UNION ALL SELECT a, b FROM tn))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$2.A, WB$3.A) AS "
                + "\"A\" FROM (SELECT OB$2.A AS \"A\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A) AS \"A\", "
                + "SET_COMBINE(WB$0.B, WB$1.B) AS \"B\" FROM (SELECT TW.A AS \"A\", TW.B AS \"B\" FROM TW AS "
                + "TW) WB$0 UNION ALL (SELECT TN.A AS \"A\", TN.B AS \"B\" FROM TN AS TN) WB$1 GROUP BY A, B) "
                + "OB$2) WB$2 UNION ALL (SELECT TN.A AS \"A\" FROM TN AS TN) WB$3) values)'"},
        });
    }

    @Test
    public void filtersCastsAndNumbering() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        engine.execute("INSERT INTO t1 VALUES (1), (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE t2 (a INT)");
        engine.execute("INSERT INTO t2 VALUES (4)");
        engine.execute("CREATE OR REPLACE TABLE t3 (a INT)");
        engine.execute("INSERT INTO t3 VALUES (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE tw (a INT, b INT, c INT)");
        engine.execute("INSERT INTO tw VALUES (1, 10, 100), (2, 20, 200)");
        engine.execute("CREATE OR REPLACE TABLE t7 (a INT)");
        engine.execute("INSERT INTO t7 VALUES (7), (7), (7)");
        engine.execute("CREATE OR REPLACE TABLE tn (a INT, b INT)");
        engine.execute("INSERT INTO tn VALUES (1, 5), (2, 6), (3, 7)");
        engine.execute("CREATE OR REPLACE TABLE tx (a INT, b INT)");
        engine.execute("INSERT INTO tx VALUES (7, 1), (7, 2), (7, 3)");
        engine.execute("CREATE OR REPLACE TABLE tz (a INT)");
        engine.execute("INSERT INTO tz VALUES (7), (NULL), (7)");
        engine.execute("CREATE OR REPLACE TABLE td (d NUMBER(10,2), e NUMBER(20,0))");
        engine.execute("INSERT INTO td VALUES (1.5, 10), (2.5, 20)");
        engine.execute("CREATE OR REPLACE TABLE tb (a NUMBER(25,0))");
        engine.execute("INSERT INTO tb VALUES (1), (2)");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a FROM t1 "
                + "WHERE a < 1 UNION ALL SELECT a FROM t3))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT null AS \"CNULL\" FROM (SELECT null AS "
                + "\"CNULL\" FROM TABLE (GENERATOR(ROWCOUNT => 0, rowCount => 0))) WB$0 UNION ALL (SELECT null "
                + "AS \"CNULL\" FROM TABLE (GENERATOR(ROWCOUNT => 2, rowCount => 2))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 WHERE"
                + " a >= 4 UNION ALL SELECT a FROM t3))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A) AS "
                + "\"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1 WHERE FALSE) WB$0 UNION ALL (SELECT T3.A AS "
                + "\"A\" FROM T3 AS T3) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a FROM t1 "
                + "WHERE a = 2 AND a > 5 UNION ALL SELECT a FROM t3))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT null AS \"CNULL\" FROM (SELECT null AS "
                + "\"CNULL\" FROM TABLE (GENERATOR(ROWCOUNT => 0, rowCount => 0))) WB$0 UNION ALL (SELECT null "
                + "AS \"CNULL\" FROM TABLE (GENERATOR(ROWCOUNT => 2, rowCount => 2))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a FROM t1 "
                + "WHERE a IS NULL UNION ALL SELECT a FROM t3))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT null AS \"CNULL\" FROM (SELECT null AS "
                + "\"CNULL\" FROM TABLE (GENERATOR(ROWCOUNT => 0, rowCount => 0))) WB$0 UNION ALL (SELECT null "
                + "AS \"CNULL\" FROM TABLE (GENERATOR(ROWCOUNT => 2, rowCount => 2))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 WHERE"
                + " a > 2 UNION ALL SELECT a FROM t3))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A) AS "
                + "\"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1 WHERE T1.A > 2) WB$0 UNION ALL (SELECT T3.A "
                + "AS \"A\" FROM T3 AS T3) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a FROM t1 "
                + "UNION ALL SELECT a FROM t3 WHERE a > 3))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT null AS \"CNULL\" FROM (SELECT null AS "
                + "\"CNULL\" FROM TABLE (GENERATOR(ROWCOUNT => 3, rowCount => 3))) WB$0 UNION ALL (SELECT null "
                + "AS \"CNULL\" FROM TABLE (GENERATOR(ROWCOUNT => 0, rowCount => 0))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 UNION"
                + " ALL (SELECT a FROM t3 INTERSECT SELECT a FROM t1) UNION ALL SELECT a FROM t3))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$2.A, WB$3.A, "
                + "WB$4.A) AS \"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1) WB$2 UNION ALL (SELECT OB$3.A AS "
                + "\"A\" FROM (SELECT WB$0.A AS \"A\" FROM (SELECT T3.A AS \"A\" FROM T3 AS T3) WB$0 SEMI JOIN "
                + "(SELECT T1.A AS \"A\" FROM T1 AS T1) WB$1 ON (WB$0.A = WB$1.A) GROUP BY A) OB$3) WB$3 UNION "
                + "ALL (SELECT T3.A AS \"A\" FROM T3 AS T3) WB$4) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a FROM t1 "
                + "UNION ALL (SELECT a FROM t3 UNION ALL SELECT a FROM t1) UNION ALL SELECT a FROM t3))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT null AS \"CNULL\" FROM (SELECT null AS "
                + "\"CNULL\" FROM TABLE (GENERATOR(ROWCOUNT => 3, rowCount => 3))) WB$2 UNION ALL (SELECT null "
                + "AS \"CNULL\" FROM (SELECT null AS \"CNULL\" FROM TABLE (GENERATOR(ROWCOUNT => 2, rowCount =>"
                + " 2))) WB$0 UNION ALL (SELECT null AS \"CNULL\" FROM TABLE (GENERATOR(ROWCOUNT => 3, rowCount"
                + " => 3))) WB$1) OB$3 UNION ALL (SELECT null AS \"CNULL\" FROM TABLE (GENERATOR(ROWCOUNT => 2,"
                + " rowCount => 2))) WB$3) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 UNION"
                + " ALL (SELECT a FROM t3 UNION ALL SELECT a FROM t1) UNION ALL SELECT a FROM t3))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$2.A, OB$3.A, "
                + "WB$3.A) AS \"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1) WB$2 UNION ALL (SELECT "
                + "SET_COMBINE(WB$0.A, WB$1.A) AS \"A\" FROM (SELECT T3.A AS \"A\" FROM T3 AS T3) WB$0 UNION "
                + "ALL (SELECT T1.A AS \"A\" FROM T1 AS T1) WB$1) OB$3 UNION ALL (SELECT T3.A AS \"A\" FROM T3 "
                + "AS T3) WB$3) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT 1.5 AS a UNION "
                + "ALL SELECT 2.5))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT (CAST(SUM(CAST(\"values\".A AS NUMBER(2,1))) AS NUMBER(20,7))) / "
                + "(CAST(COUNT(\"values\".A) AS NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT SET_COMBINE(1.5, 2.5)"
                + " AS \"A\" FROM (SELECT 1.5 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) "
                + "WB$0 UNION ALL (SELECT 2.5 AS \"2.5\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) "
                + "WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT a FROM tb UNION"
                + " ALL SELECT a FROM tb))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT SCALED_ROUND_INT_DIVIDE(CAST(SUM(CAST(\"values\".A AS NUMBER(25,0))) AS "
                + "NUMBER(37,0)), CAST(COUNT(\"values\".A) AS NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT "
                + "SET_COMBINE(WB$0.A, WB$1.A) AS \"A\" FROM (SELECT TB.A AS \"A\" FROM TB AS TB) WB$0 UNION "
                + "ALL (SELECT TB.A AS \"A\" FROM TB AS TB) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a, b FROM tx "
                + "UNION ALL SELECT 9, 9))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(7, WB$1.\"9\") AS "
                + "\"A\" FROM (SELECT 7 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 3, rowCount => 3))) WB$0 "
                + "UNION ALL (SELECT ENSURE_NULLABLE(9) AS \"9\" FROM (VALUES (null)) DUAL) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT "
                + "'a'::VARCHAR(5) EXCEPT SELECT 'b'))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT 'a' AS \"'A'::VARCHAR(5)\" FROM (SELECT"
                + " 'a' AS \"'A'::VARCHAR(5)\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 GROUP"
                + " BY 'a') values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(x) FROM (SELECT 1 AS x UNION "
                + "ALL SELECT 2::NUMBER(5,0)))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".X) AS \"MAX(X)\" FROM (SELECT SET_COMBINE(1, 2) AS \"X\" FROM "
                + "(SELECT 1 AS \"X\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL "
                + "(SELECT 2 AS \"2::NUMBER(5,0)\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) "
                + "values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a FROM t1 "
                + "UNION (SELECT a FROM t3 UNION SELECT a FROM t2)))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT SET_COMBINE(WB$2.A, WB$3.A) AS \"A\" "
                + "FROM (SELECT T1.A AS \"A\" FROM T1 AS T1) WB$2 UNION ALL (SELECT OB$3.A AS \"A\" FROM "
                + "(SELECT SET_COMBINE(WB$0.A, 4) AS \"A\" FROM (SELECT T3.A AS \"A\" FROM T3 AS T3) WB$0 UNION"
                + " ALL (SELECT 4 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 GROUP BY "
                + "A) OB$3) WB$3 GROUP BY A) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 UNION"
                + " SELECT a FROM t3 UNION ALL SELECT 9))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$2.A, WB$3.\"9\") "
                + "AS \"A\" FROM (SELECT OB$2.A AS \"A\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A) AS \"A\" FROM"
                + " (SELECT T1.A AS \"A\" FROM T1 AS T1) WB$0 UNION ALL (SELECT T3.A AS \"A\" FROM T3 AS T3) "
                + "WB$1 GROUP BY A) OB$2) WB$2 UNION ALL (SELECT ENSURE_NULLABLE(9) AS \"9\" FROM (VALUES "
                + "(null)) DUAL) WB$3) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT SUM(a) FROM (SELECT a, b FROM tx "
                + "UNION ALL SELECT a, b FROM tx))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT SUM(7) AS \"SUM(A)\" FROM (SELECT 7 AS \"A\" FROM (SELECT 7 AS \"A\" FROM "
                + "TABLE (GENERATOR(ROWCOUNT => 3, rowCount => 3))) WB$0 UNION ALL (SELECT 7 AS \"A\" FROM "
                + "TABLE (GENERATOR(ROWCOUNT => 3, rowCount => 3))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT a FROM t7 UNION"
                + " ALL SELECT a FROM t7))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT SCALED_ROUND_INT_DIVIDE(CAST(SUM(7) AS NUMBER(38,0)), CAST(COUNT(7) AS "
                + "NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT 7 AS \"A\" FROM (SELECT 7 AS \"A\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 3, rowCount => 3))) WB$0 UNION ALL (SELECT 7 AS \"A\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 3, rowCount => 3))) WB$1) values)'"},
        });
    }

    @Test
    public void constantBesideTable() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        engine.execute("INSERT INTO t1 VALUES (1), (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE t2 (a INT)");
        engine.execute("INSERT INTO t2 VALUES (4)");
        engine.execute("CREATE OR REPLACE TABLE t3 (a INT)");
        engine.execute("INSERT INTO t3 VALUES (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE tw (a INT, b INT, c INT)");
        engine.execute("INSERT INTO tw VALUES (1, 10, 100), (2, 20, 200)");
        engine.execute("CREATE OR REPLACE TABLE t7 (a INT)");
        engine.execute("INSERT INTO t7 VALUES (7), (7), (7)");
        engine.execute("CREATE OR REPLACE TABLE tn (a INT, b INT)");
        engine.execute("INSERT INTO tn VALUES (1, 5), (2, 6), (3, 7)");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(x) FROM (SELECT a AS x FROM t1 "
                + "UNION ALL SELECT 5))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".X) AS \"MAX(X)\" FROM (SELECT SET_COMBINE(WB$0.X, WB$1.\"5\") "
                + "AS \"X\" FROM (SELECT T1.A AS \"X\" FROM T1 AS T1) WB$0 UNION ALL (SELECT ENSURE_NULLABLE(5)"
                + " AS \"5\" FROM (VALUES (null)) DUAL) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 UNION"
                + " ALL SELECT 5))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.\"5\") "
                + "AS \"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1) WB$0 UNION ALL (SELECT ENSURE_NULLABLE(5)"
                + " AS \"5\" FROM (VALUES (null)) DUAL) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(x) FROM (SELECT a AS x FROM t1 "
                + "UNION ALL SELECT 1))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".X) AS \"MAX(X)\" FROM (SELECT SET_COMBINE(WB$0.X, WB$1.\"1\") "
                + "AS \"X\" FROM (SELECT T1.A AS \"X\" FROM T1 AS T1) WB$0 UNION ALL (SELECT ENSURE_NULLABLE(1)"
                + " AS \"1\" FROM (VALUES (null)) DUAL) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(x) FROM (SELECT 5 AS x UNION "
                + "ALL SELECT a FROM t1))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".X) AS \"MAX(X)\" FROM (SELECT SET_COMBINE(WB$0.X, WB$1.A) AS "
                + "\"X\" FROM (SELECT ENSURE_NULLABLE(5) AS \"X\" FROM (VALUES (null)) DUAL) WB$0 UNION ALL "
                + "(SELECT T1.A AS \"A\" FROM T1 AS T1) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(x) FROM (SELECT a AS x FROM t1 "
                + "UNION SELECT 5))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".X) AS \"MAX(X)\" FROM (SELECT SET_COMBINE(WB$0.X, WB$1.\"5\") "
                + "AS \"X\" FROM (SELECT T1.A AS \"X\" FROM T1 AS T1) WB$0 UNION ALL (SELECT ENSURE_NULLABLE(5)"
                + " AS \"5\" FROM (VALUES (null)) DUAL) WB$1 GROUP BY X) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(x) FROM (SELECT a AS x FROM "
                + "t1 UNION ALL SELECT 5))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(\"values\".X) AS \"COUNT(X)\" FROM (SELECT SET_COMBINE(WB$0.X, "
                + "WB$1.\"5\") AS \"X\" FROM (SELECT T1.A AS \"X\" FROM T1 AS T1) WB$0 UNION ALL (SELECT "
                + "ENSURE_NULLABLE(5) AS \"5\" FROM (VALUES (null)) DUAL) WB$1) values)'"},
        });
    }

    @Test
    public void intersectCasts() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        engine.execute("INSERT INTO t1 VALUES (1), (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE t2 (a INT)");
        engine.execute("INSERT INTO t2 VALUES (4)");
        engine.execute("CREATE OR REPLACE TABLE t3 (a INT)");
        engine.execute("INSERT INTO t3 VALUES (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE tw (a INT, b INT, c INT)");
        engine.execute("INSERT INTO tw VALUES (1, 10, 100), (2, 20, 200)");
        engine.execute("CREATE OR REPLACE TABLE t7 (a INT)");
        engine.execute("INSERT INTO t7 VALUES (7), (7), (7)");
        engine.execute("CREATE OR REPLACE TABLE tn (a INT, b INT)");
        engine.execute("INSERT INTO tn VALUES (1, 5), (2, 6), (3, 7)");
        engine.execute("CREATE OR REPLACE TABLE tx (a INT, b INT)");
        engine.execute("INSERT INTO tx VALUES (7, 1), (7, 2), (7, 3)");
        engine.execute("CREATE OR REPLACE TABLE tz (a INT)");
        engine.execute("INSERT INTO tz VALUES (7), (NULL), (7)");
        engine.execute("CREATE OR REPLACE TABLE td (d NUMBER(10,2), e NUMBER(20,0))");
        engine.execute("INSERT INTO td VALUES (1.5, 10), (2.5, 20)");
        engine.execute("CREATE OR REPLACE TABLE tb (a NUMBER(25,0))");
        engine.execute("INSERT INTO tb VALUES (1), (2)");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT d FROM td "
                + "INTERSECT SELECT a FROM t1))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT WB$0.D AS \"D\" FROM (SELECT CAST(TD.D "
                + "AS NUMBER(38,2)) AS \"D\" FROM TD AS TD) WB$0 SEMI JOIN (SELECT CAST(T1.A AS NUMBER(38,2)) "
                + "AS \"A\" FROM T1 AS T1) WB$1 ON (WB$0.D = WB$1.A) GROUP BY D) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a FROM t1 "
                + "INTERSECT SELECT d FROM td))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT WB$0.A AS \"A\" FROM (SELECT CAST(T1.A "
                + "AS NUMBER(38,2)) AS \"A\" FROM T1 AS T1) WB$0 SEMI JOIN (SELECT CAST(TD.D AS NUMBER(38,2)) "
                + "AS \"D\" FROM TD AS TD) WB$1 ON (WB$0.A = WB$1.D) GROUP BY A) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 1.5 INTERSECT"
                + " SELECT a FROM t1))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT 1.5 AS \"1.5\" FROM (SELECT 1.5 AS "
                + "\"1.5\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 SEMI JOIN (SELECT "
                + "CAST(T1.A AS NUMBER(38,1)) AS \"A\" FROM T1 AS T1) WB$1 ON (WB$0.\"1.5\" = WB$1.A) GROUP BY "
                + "1.5) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a FROM t1 "
                + "INTERSECT SELECT 1.5))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT WB$0.A AS \"A\" FROM (SELECT CAST(T1.A "
                + "AS NUMBER(38,1)) AS \"A\" FROM T1 AS T1) WB$0 SEMI JOIN (SELECT 1.5 AS \"1.5\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 ON (WB$0.A = WB$1.\"1.5\") GROUP BY A) "
                + "values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 1 INTERSECT "
                + "SELECT a FROM t1))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT 1 AS \"1\" FROM (SELECT 1 AS \"1\" FROM"
                + " TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 SEMI JOIN (SELECT T1.A AS \"A\" FROM "
                + "T1 AS T1) WB$1 ON (WB$0.\"1\" = WB$1.A) GROUP BY 1) values)'"},
        });
    }
}
