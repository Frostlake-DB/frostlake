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
 * A GENERATOR bound over a set operation echoed as the plan holds it (live-verified): a UNION of three branches or
 * more groups each branch by its columns, a table holding one row read as itself; a branch of a UNION held in fewer
 * bytes than the union's column is cast to its type, an operation's column twice, and an INTERSECT casts the narrower
 * side in its join condition; AVG widens its sum by the column's scale, a column held in sixteen bytes summed as it
 * stands; a BY NAME branch writing a column read elsewhere than the first, and every branch after it, writes all its
 * columns; an operation is named after the selects and operations written before and beneath it.
 */
public class GeneratorBoundSetOperationWidthTest extends MeasuredCellsTestSupport {

    @Test
    public void threeBranchUnions() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        engine.execute("INSERT INTO t1 VALUES (1), (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE t2 (a INT)");
        engine.execute("INSERT INTO t2 VALUES (4)");
        engine.execute("CREATE OR REPLACE TABLE t3 (a INT)");
        engine.execute("INSERT INTO t3 VALUES (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE tw (a INT, b INT, c INT)");
        engine.execute("INSERT INTO tw VALUES (1, 10, 100), (2, 20, 200)");
        engine.execute("CREATE OR REPLACE TABLE tn (a INT, b INT)");
        engine.execute("INSERT INTO tn VALUES (1, 5), (2, 6), (3, 7)");
        engine.execute("CREATE OR REPLACE TABLE tx (a INT, b INT)");
        engine.execute("INSERT INTO tx VALUES (7, 1), (7, 2), (7, 3)");
        engine.execute("CREATE OR REPLACE TABLE t1r (a INT, b INT)");
        engine.execute("INSERT INTO t1r VALUES (8, 9)");
        engine.execute("CREATE OR REPLACE TABLE t7 (a INT)");
        engine.execute("INSERT INTO t7 VALUES (7), (7), (7)");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 UNION"
                + " SELECT a FROM t3 UNION SELECT a FROM t1))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A, "
                + "WB$2.A) AS \"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1 GROUP BY A) WB$0 UNION ALL (SELECT"
                + " T3.A AS \"A\" FROM T3 AS T3 GROUP BY A) WB$1 UNION ALL (SELECT T1.A AS \"A\" FROM T1 AS T1 "
                + "GROUP BY A) WB$2 GROUP BY A) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 UNION"
                + " SELECT a FROM t2 UNION SELECT a FROM t3))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, 4, WB$2.A) "
                + "AS \"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1 GROUP BY A) WB$0 UNION ALL (SELECT 4 AS "
                + "\"A\" FROM T2 AS T2 GROUP BY 4) WB$1 UNION ALL (SELECT T3.A AS \"A\" FROM T3 AS T3 GROUP BY "
                + "A) WB$2 GROUP BY A) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a FROM t1 "
                + "UNION SELECT a FROM t3 UNION SELECT a FROM t1))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A, WB$2.A) AS "
                + "\"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1 GROUP BY A) WB$0 UNION ALL (SELECT T3.A AS "
                + "\"A\" FROM T3 AS T3 GROUP BY A) WB$1 UNION ALL (SELECT T1.A AS \"A\" FROM T1 AS T1 GROUP BY "
                + "A) WB$2 GROUP BY A) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 UNION"
                + " SELECT a FROM t3 UNION SELECT 9))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A, "
                + "WB$2.\"9\") AS \"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1 GROUP BY A) WB$0 UNION ALL "
                + "(SELECT T3.A AS \"A\" FROM T3 AS T3 GROUP BY A) WB$1 UNION ALL (SELECT ENSURE_NULLABLE(9) AS"
                + " \"9\" FROM (VALUES (null)) DUAL GROUP BY 9) WB$2 GROUP BY A) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT 9 AS a UNION "
                + "SELECT a FROM t3 UNION SELECT a FROM t1))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A, "
                + "WB$2.A) AS \"A\" FROM (SELECT ENSURE_NULLABLE(9) AS \"A\" FROM (VALUES (null)) DUAL GROUP BY"
                + " A) WB$0 UNION ALL (SELECT T3.A AS \"A\" FROM T3 AS T3 GROUP BY A) WB$1 UNION ALL (SELECT "
                + "T1.A AS \"A\" FROM T1 AS T1 GROUP BY A) WB$2 GROUP BY A) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t7 UNION"
                + " SELECT a FROM t3 UNION SELECT a FROM t1))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(7, WB$1.A, WB$2.A) "
                + "AS \"A\" FROM (SELECT 7 AS \"A\" FROM T7 AS T7 GROUP BY 7) WB$0 UNION ALL (SELECT T3.A AS "
                + "\"A\" FROM T3 AS T3 GROUP BY A) WB$1 UNION ALL (SELECT T1.A AS \"A\" FROM T1 AS T1 GROUP BY "
                + "A) WB$2 GROUP BY A) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a, b FROM tn "
                + "UNION SELECT a, b FROM tx UNION SELECT a, b FROM t1r))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, 7, 8) AS "
                + "\"A\", SET_COMBINE(WB$0.B, WB$1.B, 9) AS \"B\" FROM (SELECT TN.A AS \"A\", TN.B AS \"B\" "
                + "FROM TN AS TN GROUP BY A, B) WB$0 UNION ALL (SELECT 7 AS \"A\", TX.B AS \"B\" FROM TX AS TX "
                + "GROUP BY 7, B) WB$1 UNION ALL (SELECT 8 AS \"A\", 9 AS \"B\" FROM T1R AS T1R GROUP BY 8, 9) "
                + "WB$2 GROUP BY A, B) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(z) FROM (SELECT a AS z FROM t1 "
                + "UNION SELECT a FROM t3 UNION SELECT a AS y FROM t2))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".Z) AS \"MAX(Z)\" FROM (SELECT SET_COMBINE(WB$0.Z, WB$1.A, 4) "
                + "AS \"Z\" FROM (SELECT T1.A AS \"Z\" FROM T1 AS T1 GROUP BY Z) WB$0 UNION ALL (SELECT T3.A AS"
                + " \"A\" FROM T3 AS T3 GROUP BY A) WB$1 UNION ALL (SELECT 4 AS \"Y\" FROM T2 AS T2 GROUP BY 4)"
                + " WB$2 GROUP BY Z) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 UNION"
                + " SELECT a FROM t3 WHERE a > 2 UNION SELECT a FROM t1))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A, "
                + "WB$2.A) AS \"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1 GROUP BY A) WB$0 UNION ALL (SELECT"
                + " T3.A AS \"A\" FROM T3 AS T3 WHERE T3.A > 2 GROUP BY A) WB$1 UNION ALL (SELECT T1.A AS \"A\""
                + " FROM T1 AS T1 GROUP BY A) WB$2 GROUP BY A) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t2 UNION"
                + " SELECT a FROM t2 UNION SELECT a FROM t2))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(4) AS \"MAX(A)\" FROM (SELECT 4 AS \"A\" FROM (SELECT 4 AS \"A\" FROM T2 "
                + "AS T2 GROUP BY 4) WB$0 UNION ALL (SELECT 4 AS \"A\" FROM T2 AS T2 GROUP BY 4) WB$1 UNION ALL"
                + " (SELECT 4 AS \"A\" FROM T2 AS T2 GROUP BY 4) WB$2 GROUP BY 4) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t2 UNION"
                + " SELECT 4 UNION SELECT 4))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(4, WB$1.\"4\", "
                + "WB$2.\"4\") AS \"A\" FROM (SELECT 4 AS \"A\" FROM T2 AS T2 GROUP BY 4) WB$0 UNION ALL "
                + "(SELECT ENSURE_NULLABLE(4) AS \"4\" FROM (VALUES (null)) DUAL GROUP BY 4) WB$1 UNION ALL "
                + "(SELECT ENSURE_NULLABLE(4) AS \"4\" FROM (VALUES (null)) DUAL GROUP BY 4) WB$2 GROUP BY A) "
                + "values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t2 UNION"
                + " SELECT 9 UNION SELECT 8))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(4, WB$1.\"9\", "
                + "WB$2.\"8\") AS \"A\" FROM (SELECT 4 AS \"A\" FROM T2 AS T2 GROUP BY 4) WB$0 UNION ALL "
                + "(SELECT ENSURE_NULLABLE(9) AS \"9\" FROM (VALUES (null)) DUAL GROUP BY 9) WB$1 UNION ALL "
                + "(SELECT ENSURE_NULLABLE(8) AS \"8\" FROM (VALUES (null)) DUAL GROUP BY 8) WB$2 GROUP BY A) "
                + "values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT 1 AS a UNION "
                + "SELECT 2 UNION SELECT 3))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(1, 2, 3) AS \"A\" "
                + "FROM (SELECT 1 AS \"A\" FROM (VALUES (null)) DUAL GROUP BY 1) WB$0 UNION ALL (SELECT 2 AS "
                + "\"2\" FROM (VALUES (null)) DUAL GROUP BY 2) WB$1 UNION ALL (SELECT 3 AS \"3\" FROM (VALUES "
                + "(null)) DUAL GROUP BY 3) WB$2 GROUP BY A) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 UNION"
                + " SELECT a FROM t3 UNION SELECT a FROM t2 UNION SELECT a FROM t1))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A, 4, "
                + "WB$3.A) AS \"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1 GROUP BY A) WB$0 UNION ALL (SELECT"
                + " T3.A AS \"A\" FROM T3 AS T3 GROUP BY A) WB$1 UNION ALL (SELECT 4 AS \"A\" FROM T2 AS T2 "
                + "GROUP BY 4) WB$2 UNION ALL (SELECT T1.A AS \"A\" FROM T1 AS T1 GROUP BY A) WB$3 GROUP BY A) "
                + "values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 UNION"
                + " ALL SELECT a FROM t3 UNION ALL SELECT a FROM t2 UNION SELECT a FROM t1))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(OB$3.A, WB$3.A) AS "
                + "\"A\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A, 4) AS \"A\" FROM (SELECT T1.A AS \"A\" FROM "
                + "T1 AS T1) WB$0 UNION ALL (SELECT T3.A AS \"A\" FROM T3 AS T3) WB$1 UNION ALL (SELECT 4 AS "
                + "\"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$2) OB$3 UNION ALL (SELECT "
                + "T1.A AS \"A\" FROM T1 AS T1) WB$3 GROUP BY A) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 UNION"
                + " SELECT a FROM t3 UNION ALL SELECT a FROM t2 UNION SELECT a FROM t1))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(OB$4.A, WB$4.A) AS "
                + "\"A\" FROM (SELECT SET_COMBINE(WB$2.A, 4) AS \"A\" FROM (SELECT OB$2.A AS \"A\" FROM (SELECT"
                + " SET_COMBINE(WB$0.A, WB$1.A) AS \"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1) WB$0 UNION "
                + "ALL (SELECT T3.A AS \"A\" FROM T3 AS T3) WB$1 GROUP BY A) OB$2) WB$2 UNION ALL (SELECT 4 AS "
                + "\"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$3) OB$4 UNION ALL (SELECT "
                + "T1.A AS \"A\" FROM T1 AS T1) WB$4 GROUP BY A) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 UNION"
                + " (SELECT a FROM t3 INTERSECT SELECT a FROM t1) UNION SELECT a FROM t2))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$2.A, WB$3.A, 4) "
                + "AS \"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1 GROUP BY A) WB$2 UNION ALL (SELECT OB$3.A "
                + "AS \"A\" FROM (SELECT WB$0.A AS \"A\" FROM (SELECT T3.A AS \"A\" FROM T3 AS T3) WB$0 SEMI "
                + "JOIN (SELECT T1.A AS \"A\" FROM T1 AS T1) WB$1 ON (WB$0.A = WB$1.A) GROUP BY A) OB$3 GROUP "
                + "BY A) WB$3 UNION ALL (SELECT 4 AS \"A\" FROM T2 AS T2 GROUP BY 4) WB$4 GROUP BY A) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 UNION"
                + " (SELECT a FROM t3 UNION SELECT a FROM t1) UNION SELECT a FROM t2))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$2.A, WB$3.A, 4) "
                + "AS \"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1 GROUP BY A) WB$2 UNION ALL (SELECT OB$3.A "
                + "AS \"A\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A) AS \"A\" FROM (SELECT T3.A AS \"A\" FROM "
                + "T3 AS T3) WB$0 UNION ALL (SELECT T1.A AS \"A\" FROM T1 AS T1) WB$1 GROUP BY A) OB$3 GROUP BY"
                + " A) WB$3 UNION ALL (SELECT 4 AS \"A\" FROM T2 AS T2 GROUP BY 4) WB$4 GROUP BY A) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 UNION"
                + " ALL SELECT a FROM t3 UNION ALL SELECT a FROM t2))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A, 4) "
                + "AS \"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1) WB$0 UNION ALL (SELECT T3.A AS \"A\" FROM"
                + " T3 AS T3) WB$1 UNION ALL (SELECT 4 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount "
                + "=> 1))) WB$2) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 "
                + "INTERSECT SELECT a FROM t3 INTERSECT SELECT a FROM t2))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT WB$2.A AS \"A\" FROM (SELECT "
                + "OB$2.A AS \"A\" FROM (SELECT WB$0.A AS \"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1) WB$0 "
                + "SEMI JOIN (SELECT T3.A AS \"A\" FROM T3 AS T3) WB$1 ON (WB$0.A = WB$1.A) GROUP BY A) OB$2) "
                + "WB$2 SEMI JOIN (SELECT 4 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$3"
                + " ON (WB$2.A = WB$3.A) GROUP BY A) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 "
                + "EXCEPT SELECT a FROM t3 EXCEPT SELECT a FROM t2))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT WB$2.A AS \"A\" FROM (SELECT "
                + "OB$2.A AS \"A\" FROM (SELECT WB$0.A AS \"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1) WB$0 "
                + "GROUP BY A) OB$2) WB$2 GROUP BY A) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 UNION"
                + " SELECT a FROM t3 UNION SELECT a FROM t1 UNION ALL SELECT 5))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$3.A, WB$4.\"5\") "
                + "AS \"A\" FROM (SELECT OB$3.A AS \"A\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A, WB$2.A) AS "
                + "\"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1 GROUP BY A) WB$0 UNION ALL (SELECT T3.A AS "
                + "\"A\" FROM T3 AS T3 GROUP BY A) WB$1 UNION ALL (SELECT T1.A AS \"A\" FROM T1 AS T1 GROUP BY "
                + "A) WB$2 GROUP BY A) OB$3) WB$3 UNION ALL (SELECT ENSURE_NULLABLE(5) AS \"5\" FROM (VALUES "
                + "(null)) DUAL) WB$4) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a, b FROM tn "
                + "UNION SELECT b, a FROM tx UNION SELECT 1, 2))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.B, "
                + "WB$2.\"1\") AS \"A\", SET_COMBINE(WB$0.B, 7, WB$2.\"2\") AS \"B\" FROM (SELECT TN.A AS "
                + "\"A\", TN.B AS \"B\" FROM TN AS TN GROUP BY A, B) WB$0 UNION ALL (SELECT TX.B AS \"B\", 7 AS"
                + " \"A\" FROM TX AS TX GROUP BY B, 7) WB$1 UNION ALL (SELECT ENSURE_NULLABLE(1) AS \"1\", "
                + "ENSURE_NULLABLE(2) AS \"2\" FROM (VALUES (null)) DUAL GROUP BY 1, 2) WB$2 GROUP BY A, B) "
                + "values)'"},
        });
    }

    @Test
    public void byNameAndAverages() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        engine.execute("INSERT INTO t1 VALUES (1), (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE t2 (a INT)");
        engine.execute("INSERT INTO t2 VALUES (4)");
        engine.execute("CREATE OR REPLACE TABLE t3 (a INT)");
        engine.execute("INSERT INTO t3 VALUES (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE tw (a INT, b INT, c INT)");
        engine.execute("INSERT INTO tw VALUES (1, 10, 100), (2, 20, 200)");
        engine.execute("CREATE OR REPLACE TABLE tn (a INT, b INT)");
        engine.execute("INSERT INTO tn VALUES (1, 5), (2, 6), (3, 7)");
        engine.execute("CREATE OR REPLACE TABLE tx (a INT, b INT)");
        engine.execute("INSERT INTO tx VALUES (7, 1), (7, 2), (7, 3)");
        engine.execute("CREATE OR REPLACE TABLE t1r (a INT, b INT)");
        engine.execute("INSERT INTO t1r VALUES (8, 9)");
        engine.execute("CREATE OR REPLACE TABLE t7 (a INT)");
        engine.execute("INSERT INTO t7 VALUES (7), (7), (7)");
        engine.execute("CREATE OR REPLACE TABLE tb (b INT, a INT)");
        engine.execute("INSERT INTO tb VALUES (5, 1), (6, 2)");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a, b FROM tw "
                + "UNION ALL BY NAME SELECT b, a FROM tn))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A) AS "
                + "\"A\" FROM (SELECT TW.A AS \"A\" FROM TW AS TW) WB$0 UNION ALL (SELECT TN.B AS \"B\", TN.A "
                + "AS \"A\" FROM TN AS TN) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a, b FROM tw "
                + "UNION BY NAME SELECT b, a FROM tn))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A) AS \"A\", "
                + "SET_COMBINE(WB$0.B, WB$1.B) AS \"B\" FROM (SELECT TW.A AS \"A\", TW.B AS \"B\" FROM TW AS "
                + "TW) WB$0 UNION ALL (SELECT TN.B AS \"B\", TN.A AS \"A\" FROM TN AS TN) WB$1 GROUP BY A, B) "
                + "values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a, b FROM tw "
                + "UNION ALL BY NAME SELECT b, a FROM tn))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT null AS \"CNULL\" FROM (SELECT null AS "
                + "\"CNULL\" FROM TABLE (GENERATOR(ROWCOUNT => 2, rowCount => 2))) WB$0 UNION ALL (SELECT null "
                + "AS \"CNULL\" FROM TABLE (GENERATOR(ROWCOUNT => 3, rowCount => 3))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a, b FROM tw "
                + "UNION ALL BY NAME SELECT a, b FROM tn UNION ALL BY NAME SELECT b, a FROM tn))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A, "
                + "WB$2.A) AS \"A\" FROM (SELECT TW.A AS \"A\" FROM TW AS TW) WB$0 UNION ALL (SELECT TN.A AS "
                + "\"A\" FROM TN AS TN) WB$1 UNION ALL (SELECT TN.B AS \"B\", TN.A AS \"A\" FROM TN AS TN) "
                + "WB$2) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a, b FROM tw "
                + "UNION ALL BY NAME SELECT b, a FROM t1r))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, 8) AS \"A\" "
                + "FROM (SELECT TW.A AS \"A\" FROM TW AS TW) WB$0 UNION ALL (SELECT 9 AS \"B\", 8 AS \"A\" FROM"
                + " TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT 1 AS a, 2 AS b "
                + "UNION ALL BY NAME SELECT 3 AS b, 4 AS a))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(1, 4) AS \"A\" FROM "
                + "(SELECT 1 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL "
                + "(SELECT 3 AS \"B\", 4 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) "
                + "values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a, b FROM tw "
                + "UNION ALL BY NAME SELECT 3 AS b, 4 AS a))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, 4) AS \"A\" "
                + "FROM (SELECT TW.A AS \"A\" FROM TW AS TW) WB$0 UNION ALL (SELECT 3 AS \"B\", 4 AS \"A\" FROM"
                + " TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT b, a FROM tw "
                + "UNION ALL BY NAME SELECT a, b FROM tn))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A) AS "
                + "\"A\" FROM (SELECT TW.A AS \"A\" FROM TW AS TW) WB$0 UNION ALL (SELECT TN.A AS \"A\", TN.B "
                + "AS \"B\" FROM TN AS TN) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a, b, c FROM tw"
                + " UNION ALL BY NAME SELECT a, c, b FROM tw))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A) AS "
                + "\"A\" FROM (SELECT TW.A AS \"A\" FROM TW AS TW) WB$0 UNION ALL (SELECT TW.A AS \"A\" FROM TW"
                + " AS TW) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a, b FROM tw "
                + "UNION BY NAME SELECT a, b FROM tn UNION BY NAME SELECT b, a FROM tn))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A, "
                + "WB$2.A) AS \"A\", SET_COMBINE(WB$0.B, WB$1.B, WB$2.B) AS \"B\" FROM (SELECT TW.A AS \"A\", "
                + "TW.B AS \"B\" FROM TW AS TW GROUP BY A, B) WB$0 UNION ALL (SELECT TN.A AS \"A\", TN.B AS "
                + "\"B\" FROM TN AS TN GROUP BY A, B) WB$1 UNION ALL (SELECT TN.B AS \"B\", TN.A AS \"A\" FROM "
                + "TN AS TN GROUP BY B, A) WB$2 GROUP BY A, B) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(b) FROM (SELECT a, b FROM tw "
                + "UNION ALL BY NAME SELECT b, a FROM tn))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".B) AS \"MAX(B)\" FROM (SELECT SET_COMBINE(WB$0.B, WB$1.B) AS "
                + "\"B\" FROM (SELECT TW.B AS \"B\" FROM TW AS TW) WB$0 UNION ALL (SELECT TN.B AS \"B\", TN.A "
                + "AS \"A\" FROM TN AS TN) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a, b FROM tw "
                + "UNION ALL BY NAME SELECT b, a FROM tx))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, 7) AS \"A\" "
                + "FROM (SELECT TW.A AS \"A\" FROM TW AS TW) WB$0 UNION ALL (SELECT TX.B AS \"B\", 7 AS \"A\" "
                + "FROM TX AS TX) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a, b FROM tw "
                + "UNION ALL BY NAME SELECT b AS a, a AS b FROM tn))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A) AS "
                + "\"A\" FROM (SELECT TW.A AS \"A\" FROM TW AS TW) WB$0 UNION ALL (SELECT TN.B AS \"A\" FROM TN"
                + " AS TN) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a, b FROM tn "
                + "UNION ALL BY NAME SELECT b, a FROM tb))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A) AS "
                + "\"A\" FROM (SELECT TN.A AS \"A\" FROM TN AS TN) WB$0 UNION ALL (SELECT TB.B AS \"B\", TB.A "
                + "AS \"A\" FROM TB AS TB) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a, b FROM tw "
                + "INTERSECT SELECT b, a FROM tb))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT WB$0.A AS \"A\", WB$0.B AS \"B\""
                + " FROM (SELECT TW.A AS \"A\", TW.B AS \"B\" FROM TW AS TW) WB$0 SEMI JOIN (SELECT TB.B AS "
                + "\"B\", TB.A AS \"A\" FROM TB AS TB) WB$1 ON ((WB$0.A = WB$1.B) AND (WB$0.B = WB$1.A)) GROUP "
                + "BY A, B) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT 10 AS a UNION "
                + "ALL SELECT 20))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT (CAST(SUM(CAST(\"values\".A AS NUMBER(2,0))) AS NUMBER(20,6))) / "
                + "(CAST(COUNT(\"values\".A) AS NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT SET_COMBINE(10, 20) "
                + "AS \"A\" FROM (SELECT 10 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0"
                + " UNION ALL (SELECT 20 AS \"20\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) "
                + "values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT 100 AS a UNION "
                + "ALL SELECT 200))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT (CAST(SUM(CAST(\"values\".A AS NUMBER(3,0))) AS NUMBER(21,6))) / "
                + "(CAST(COUNT(\"values\".A) AS NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT SET_COMBINE(100, 200)"
                + " AS \"A\" FROM (SELECT CAST(100 AS NUMBER(3,0)) AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => "
                + "1, rowCount => 1))) WB$0 UNION ALL (SELECT 200 AS \"200\" FROM TABLE (GENERATOR(ROWCOUNT => "
                + "1, rowCount => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT 1000 AS a UNION"
                + " ALL SELECT 2000))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT (CAST(SUM(CAST(\"values\".A AS NUMBER(4,0))) AS NUMBER(22,6))) / "
                + "(CAST(COUNT(\"values\".A) AS NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT SET_COMBINE(1000, "
                + "2000) AS \"A\" FROM (SELECT 1000 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => "
                + "1))) WB$0 UNION ALL (SELECT 2000 AS \"2000\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount "
                + "=> 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT 100 AS a UNION "
                + "ALL SELECT 2))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT (CAST(SUM(CAST(\"values\".A AS NUMBER(3,0))) AS NUMBER(21,6))) / "
                + "(CAST(COUNT(\"values\".A) AS NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT SET_COMBINE(100, 2) "
                + "AS \"A\" FROM (SELECT 100 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) "
                + "WB$0 UNION ALL (SELECT 2 AS \"2\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) "
                + "WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT 2 AS a UNION "
                + "ALL SELECT 100))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT (CAST(SUM(CAST(\"values\".A AS NUMBER(3,0))) AS NUMBER(21,6))) / "
                + "(CAST(COUNT(\"values\".A) AS NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT SET_COMBINE(2, 100) "
                + "AS \"A\" FROM (SELECT 2 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 "
                + "UNION ALL (SELECT 100 AS \"100\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1)"
                + " values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT 120 AS a UNION "
                + "ALL SELECT 200))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT (CAST(SUM(CAST(\"values\".A AS NUMBER(3,0))) AS NUMBER(21,6))) / "
                + "(CAST(COUNT(\"values\".A) AS NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT SET_COMBINE(120, 200)"
                + " AS \"A\" FROM (SELECT CAST(120 AS NUMBER(3,0)) AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => "
                + "1, rowCount => 1))) WB$0 UNION ALL (SELECT 200 AS \"200\" FROM TABLE (GENERATOR(ROWCOUNT => "
                + "1, rowCount => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT 110 AS a UNION "
                + "ALL SELECT 20))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT (CAST(SUM(CAST(\"values\".A AS NUMBER(3,0))) AS NUMBER(21,6))) / "
                + "(CAST(COUNT(\"values\".A) AS NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT SET_COMBINE(110, 20) "
                + "AS \"A\" FROM (SELECT 110 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) "
                + "WB$0 UNION ALL (SELECT 20 AS \"20\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) "
                + "WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT 100 AS a UNION "
                + "ALL SELECT 200))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(100, 200) AS \"A\" "
                + "FROM (SELECT CAST(100 AS NUMBER(3,0)) AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount"
                + " => 1))) WB$0 UNION ALL (SELECT 200 AS \"200\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount"
                + " => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT SUM(a) FROM (SELECT 100 AS a UNION "
                + "ALL SELECT 200))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT SUM(CAST(\"values\".A AS NUMBER(3,0))) AS \"SUM(A)\" FROM (SELECT "
                + "SET_COMBINE(100, 200) AS \"A\" FROM (SELECT CAST(100 AS NUMBER(3,0)) AS \"A\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL (SELECT 200 AS \"200\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT 1.5 AS a UNION "
                + "ALL SELECT 2.5))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT (CAST(SUM(CAST(\"values\".A AS NUMBER(2,1))) AS NUMBER(20,7))) / "
                + "(CAST(COUNT(\"values\".A) AS NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT SET_COMBINE(1.5, 2.5)"
                + " AS \"A\" FROM (SELECT 1.5 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) "
                + "WB$0 UNION ALL (SELECT 2.5 AS \"2.5\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) "
                + "WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT 1.234 AS a "
                + "UNION ALL SELECT 2.5))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT (CAST(SUM(CAST(\"values\".A AS NUMBER(4,3))) AS NUMBER(22,9))) / "
                + "(CAST(COUNT(\"values\".A) AS NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT SET_COMBINE(1.234, "
                + "2.5) AS \"A\" FROM (SELECT 1.234 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => "
                + "1))) WB$0 UNION ALL (SELECT 2.5 AS \"2.5\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => "
                + "1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT 1.23456 AS a "
                + "UNION ALL SELECT 2))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT (CAST(SUM(CAST(\"values\".A AS NUMBER(6,5))) AS NUMBER(24,11))) / "
                + "(CAST(COUNT(\"values\".A) AS NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT SET_COMBINE(1.23456, "
                + "2) AS \"A\" FROM (SELECT 1.23456 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => "
                + "1))) WB$0 UNION ALL (SELECT 2 AS \"2\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1)))"
                + " WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT "
                + "123456789012345678901.5 AS a UNION ALL SELECT 1))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT SCALED_ROUND_INT_DIVIDE(SUM(\"values\".A), CAST(COUNT(\"values\".A) AS "
                + "NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT SET_COMBINE(123456789012345678901.5, 1) AS \"A\" "
                + "FROM (SELECT 123456789012345678901.5 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount "
                + "=> 1))) WB$0 UNION ALL (SELECT CAST(1 AS NUMBER(22,1)) AS \"1\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT 100 AS a UNION "
                + "SELECT 200))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT (CAST(SUM(CAST(\"values\".A AS NUMBER(3,0))) AS NUMBER(21,6))) / "
                + "(CAST(COUNT(\"values\".A) AS NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT SET_COMBINE(100, 200)"
                + " AS \"A\" FROM (SELECT CAST(100 AS NUMBER(3,0)) AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => "
                + "1, rowCount => 1))) WB$0 UNION ALL (SELECT 200 AS \"200\" FROM TABLE (GENERATOR(ROWCOUNT => "
                + "1, rowCount => 1))) WB$1 GROUP BY A) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT 100 AS a UNION "
                + "ALL SELECT a FROM t1))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT SCALED_ROUND_INT_DIVIDE(CAST(SUM(CAST(\"values\".A AS NUMBER(38,0))) AS "
                + "NUMBER(38,0)), CAST(COUNT(\"values\".A) AS NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT "
                + "SET_COMBINE(WB$0.A, WB$1.A) AS \"A\" FROM (SELECT ENSURE_NULLABLE(100) AS \"A\" FROM (VALUES"
                + " (null)) DUAL) WB$0 UNION ALL (SELECT T1.A AS \"A\" FROM T1 AS T1) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT 5 AS a UNION "
                + "ALL SELECT 100))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT (CAST(SUM(CAST(\"values\".A AS NUMBER(3,0))) AS NUMBER(21,6))) / "
                + "(CAST(COUNT(\"values\".A) AS NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT SET_COMBINE(5, 100) "
                + "AS \"A\" FROM (SELECT 5 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 "
                + "UNION ALL (SELECT 100 AS \"100\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1)"
                + " values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT 0 AS a UNION "
                + "ALL SELECT 10))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT (CAST(SUM(CAST(\"values\".A AS NUMBER(2,0))) AS NUMBER(20,6))) / "
                + "(CAST(COUNT(\"values\".A) AS NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT SET_COMBINE(0, 10) AS"
                + " \"A\" FROM (SELECT 0 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 "
                + "UNION ALL (SELECT 10 AS \"10\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) "
                + "values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT 100.0 AS a "
                + "UNION ALL SELECT 200))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT (CAST(SUM(CAST(\"values\".A AS NUMBER(3,0))) AS NUMBER(21,6))) / "
                + "(CAST(COUNT(\"values\".A) AS NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT SET_COMBINE(100, 200)"
                + " AS \"A\" FROM (SELECT CAST(100 AS NUMBER(3,0)) AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => "
                + "1, rowCount => 1))) WB$0 UNION ALL (SELECT 200 AS \"200\" FROM TABLE (GENERATOR(ROWCOUNT => "
                + "1, rowCount => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT 30 AS a UNION "
                + "ALL SELECT 20))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT (CAST(SUM(CAST(\"values\".A AS NUMBER(2,0))) AS NUMBER(20,6))) / "
                + "(CAST(COUNT(\"values\".A) AS NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT SET_COMBINE(30, 20) "
                + "AS \"A\" FROM (SELECT 30 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0"
                + " UNION ALL (SELECT 20 AS \"20\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) "
                + "values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT 99 AS a UNION "
                + "ALL SELECT 999))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT (CAST(SUM(CAST(\"values\".A AS NUMBER(3,0))) AS NUMBER(21,6))) / "
                + "(CAST(COUNT(\"values\".A) AS NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT SET_COMBINE(99, 999) "
                + "AS \"A\" FROM (SELECT CAST(99 AS NUMBER(3,0)) AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, "
                + "rowCount => 1))) WB$0 UNION ALL (SELECT 999 AS \"999\" FROM TABLE (GENERATOR(ROWCOUNT => 1, "
                + "rowCount => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT 999 AS a UNION "
                + "ALL SELECT 99))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT (CAST(SUM(CAST(\"values\".A AS NUMBER(3,0))) AS NUMBER(21,6))) / "
                + "(CAST(COUNT(\"values\".A) AS NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT SET_COMBINE(999, 99) "
                + "AS \"A\" FROM (SELECT 999 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) "
                + "WB$0 UNION ALL (SELECT CAST(99 AS NUMBER(3,0)) AS \"99\" FROM TABLE (GENERATOR(ROWCOUNT => "
                + "1, rowCount => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT 100 AS a UNION "
                + "ALL SELECT 200 UNION ALL SELECT 300))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT (CAST(SUM(CAST(\"values\".A AS NUMBER(3,0))) AS NUMBER(21,6))) / "
                + "(CAST(COUNT(\"values\".A) AS NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT SET_COMBINE(100, 200,"
                + " 300) AS \"A\" FROM (SELECT CAST(100 AS NUMBER(3,0)) AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT"
                + " => 1, rowCount => 1))) WB$0 UNION ALL (SELECT 200 AS \"200\" FROM TABLE (GENERATOR(ROWCOUNT"
                + " => 1, rowCount => 1))) WB$1 UNION ALL (SELECT 300 AS \"300\" FROM TABLE (GENERATOR(ROWCOUNT"
                + " => 1, rowCount => 1))) WB$2) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT 100 AS a, 1 AS "
                + "b UNION ALL SELECT 200, 2))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT (CAST(SUM(CAST(\"values\".A AS NUMBER(3,0))) AS NUMBER(21,6))) / "
                + "(CAST(COUNT(\"values\".A) AS NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT SET_COMBINE(100, 200)"
                + " AS \"A\" FROM (SELECT CAST(100 AS NUMBER(3,0)) AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => "
                + "1, rowCount => 1))) WB$0 UNION ALL (SELECT 200 AS \"200\" FROM TABLE (GENERATOR(ROWCOUNT => "
                + "1, rowCount => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT 1.25 AS a UNION"
                + " ALL SELECT 2.123))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT (CAST(SUM(CAST(\"values\".A AS NUMBER(4,3))) AS NUMBER(22,9))) / "
                + "(CAST(COUNT(\"values\".A) AS NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT SET_COMBINE(1.25, "
                + "2.123) AS \"A\" FROM (SELECT 1.25 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => "
                + "1))) WB$0 UNION ALL (SELECT 2.123 AS \"2.123\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount"
                + " => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT 1.2345678 AS a "
                + "UNION ALL SELECT 2))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT (CAST(SUM(CAST(\"values\".A AS NUMBER(8,7))) AS NUMBER(25,12))) / "
                + "(CAST(COUNT(\"values\".A) AS NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT "
                + "SET_COMBINE(1.2345678, 2) AS \"A\" FROM (SELECT 1.2345678 AS \"A\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL (SELECT 2 AS \"2\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT "
                + "12345678901234567890.12 AS a UNION ALL SELECT 1))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT SCALED_ROUND_INT_DIVIDE(SUM(\"values\".A), CAST(COUNT(\"values\".A) AS "
                + "NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT SET_COMBINE(12345678901234567890.12, 1) AS \"A\" "
                + "FROM (SELECT 12345678901234567890.12 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount "
                + "=> 1))) WB$0 UNION ALL (SELECT CAST(1 AS NUMBER(22,2)) AS \"1\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT 1 AS a UNION "
                + "ALL SELECT 12345678901234567890.12))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT SCALED_ROUND_INT_DIVIDE(SUM(\"values\".A), CAST(COUNT(\"values\".A) AS "
                + "NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT SET_COMBINE(1, 12345678901234567890.12) AS \"A\" "
                + "FROM (SELECT CAST(1 AS NUMBER(22,2)) AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount "
                + "=> 1))) WB$0 UNION ALL (SELECT 12345678901234567890.12 AS \"12345678901234567890.12\" FROM "
                + "TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT 4 AS a "
                + "INTERSECT SELECT 4))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT (CAST(CAST(SUM(4) AS NUMBER(19,6)) AS NUMBER(19,6))) / (CAST(COUNT(4) AS "
                + "NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT 4 AS \"A\" FROM (SELECT 4 AS \"A\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 SEMI JOIN (SELECT 4 AS \"4\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 ON (WB$0.A = WB$1.\"4\") GROUP BY 4) "
                + "values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT 400 AS a "
                + "INTERSECT SELECT 400))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT (CAST(SUM(400) AS NUMBER(21,6))) / (CAST(COUNT(400) AS NUMBER(18,0))) AS "
                + "\"AVG(A)\" FROM (SELECT 400 AS \"A\" FROM (SELECT 400 AS \"A\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 SEMI JOIN (SELECT 400 AS \"400\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 ON (WB$0.A = WB$1.\"400\") GROUP BY 400) "
                + "values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 UNION"
                + " ALL SELECT a FROM t3 UNION SELECT a FROM t2 UNION (SELECT a FROM t3 INTERSECT SELECT a FROM"
                + " t1)))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(OB$2.A, 4, WB$5.A) "
                + "AS \"A\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A) AS \"A\" FROM (SELECT T1.A AS \"A\" FROM "
                + "T1 AS T1) WB$0 UNION ALL (SELECT T3.A AS \"A\" FROM T3 AS T3) WB$1 GROUP BY A) OB$2 UNION "
                + "ALL (SELECT 4 AS \"A\" FROM T2 AS T2 GROUP BY 4) WB$4 UNION ALL (SELECT OB$6.A AS \"A\" FROM"
                + " (SELECT WB$2.A AS \"A\" FROM (SELECT T3.A AS \"A\" FROM T3 AS T3) WB$2 SEMI JOIN (SELECT "
                + "T1.A AS \"A\" FROM T1 AS T1) WB$3 ON (WB$2.A = WB$3.A) GROUP BY A) OB$6 GROUP BY A) WB$5 "
                + "GROUP BY A) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 UNION"
                + " ALL SELECT a FROM t3 UNION SELECT a FROM t2 UNION ALL (SELECT a FROM t3 INTERSECT SELECT a "
                + "FROM t1)))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$5.A, WB$6.A) AS "
                + "\"A\" FROM (SELECT OB$4.A AS \"A\" FROM (SELECT SET_COMBINE(OB$2.A, 4) AS \"A\" FROM (SELECT"
                + " SET_COMBINE(WB$0.A, WB$1.A) AS \"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1) WB$0 UNION "
                + "ALL (SELECT T3.A AS \"A\" FROM T3 AS T3) WB$1) OB$2 UNION ALL (SELECT 4 AS \"A\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$2 GROUP BY A) OB$4) WB$5 UNION ALL (SELECT "
                + "OB$7.A AS \"A\" FROM (SELECT WB$3.A AS \"A\" FROM (SELECT T3.A AS \"A\" FROM T3 AS T3) WB$3 "
                + "SEMI JOIN (SELECT T1.A AS \"A\" FROM T1 AS T1) WB$4 ON (WB$3.A = WB$4.A) GROUP BY A) OB$7) "
                + "WB$6) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 UNION"
                + " ALL SELECT a FROM t2 UNION SELECT a FROM t3 UNION SELECT a FROM t1))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(OB$2.A, WB$2.A, "
                + "WB$3.A) AS \"A\" FROM (SELECT SET_COMBINE(WB$0.A, 4) AS \"A\" FROM (SELECT T1.A AS \"A\" "
                + "FROM T1 AS T1) WB$0 UNION ALL (SELECT 4 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, "
                + "rowCount => 1))) WB$1 GROUP BY A) OB$2 UNION ALL (SELECT T3.A AS \"A\" FROM T3 AS T3 GROUP "
                + "BY A) WB$2 UNION ALL (SELECT T1.A AS \"A\" FROM T1 AS T1 GROUP BY A) WB$3 GROUP BY A) "
                + "values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 UNION"
                + " ALL SELECT a FROM t3 EXCEPT SELECT a FROM t2))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT OB$2.A AS \"A\" FROM (SELECT "
                + "SET_COMBINE(WB$0.A, WB$1.A) AS \"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1) WB$0 UNION "
                + "ALL (SELECT T3.A AS \"A\" FROM T3 AS T3) WB$1) OB$2 GROUP BY A) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 UNION"
                + " SELECT a FROM t3 EXCEPT SELECT a FROM t2))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT WB$2.A AS \"A\" FROM (SELECT "
                + "OB$2.A AS \"A\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A) AS \"A\" FROM (SELECT T1.A AS \"A\""
                + " FROM T1 AS T1) WB$0 UNION ALL (SELECT T3.A AS \"A\" FROM T3 AS T3) WB$1 GROUP BY A) OB$2) "
                + "WB$2 GROUP BY A) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 "
                + "INTERSECT SELECT a FROM t3 EXCEPT SELECT a FROM t2))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT WB$2.A AS \"A\" FROM (SELECT "
                + "OB$2.A AS \"A\" FROM (SELECT WB$0.A AS \"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1) WB$0 "
                + "SEMI JOIN (SELECT T3.A AS \"A\" FROM T3 AS T3) WB$1 ON (WB$0.A = WB$1.A) GROUP BY A) OB$2) "
                + "WB$2 GROUP BY A) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 "
                + "EXCEPT SELECT a FROM t3 EXCEPT SELECT a FROM t2))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT WB$2.A AS \"A\" FROM (SELECT "
                + "OB$2.A AS \"A\" FROM (SELECT WB$0.A AS \"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1) WB$0 "
                + "GROUP BY A) OB$2) WB$2 GROUP BY A) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 "
                + "EXCEPT SELECT a FROM t3 UNION SELECT a FROM t2))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$2.A, 4) AS \"A\" "
                + "FROM (SELECT OB$2.A AS \"A\" FROM (SELECT WB$0.A AS \"A\" FROM (SELECT T1.A AS \"A\" FROM T1"
                + " AS T1) WB$0 GROUP BY A) OB$2) WB$2 UNION ALL (SELECT 4 AS \"A\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$3 GROUP BY A) values)'"},
        });
    }

    @Test
    public void widthsAndScales() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        engine.execute("INSERT INTO t1 VALUES (1), (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE t2 (a INT)");
        engine.execute("INSERT INTO t2 VALUES (4)");
        engine.execute("CREATE OR REPLACE TABLE t3 (a INT)");
        engine.execute("INSERT INTO t3 VALUES (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE tw (a INT, b INT, c INT)");
        engine.execute("INSERT INTO tw VALUES (1, 10, 100), (2, 20, 200)");
        engine.execute("CREATE OR REPLACE TABLE tn (a INT, b INT)");
        engine.execute("INSERT INTO tn VALUES (1, 5), (2, 6), (3, 7)");
        engine.execute("CREATE OR REPLACE TABLE tx (a INT, b INT)");
        engine.execute("INSERT INTO tx VALUES (7, 1), (7, 2), (7, 3)");
        engine.execute("CREATE OR REPLACE TABLE t1r (a INT, b INT)");
        engine.execute("INSERT INTO t1r VALUES (8, 9)");
        engine.execute("CREATE OR REPLACE TABLE t7 (a INT)");
        engine.execute("INSERT INTO t7 VALUES (7), (7), (7)");
        engine.execute("CREATE OR REPLACE TABLE tb (b INT, a INT)");
        engine.execute("INSERT INTO tb VALUES (5, 1), (6, 2)");
        engine.execute("CREATE OR REPLACE TABLE tp (d NUMBER(22,1), e NUMBER(20,10), f NUMBER(15,14), g "
            + "NUMBER(30,5), h NUMBER(25,0), i NUMBER(21,0), j NUMBER(10,8))");
        engine.execute("INSERT INTO tp VALUES (1.5, 2.5, 3.5, 4.5, 5, 6, 7.5), (2.5, 3.5, 4.5, 5.5, 6, 7, 8.5)");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT "
                + "12345678901234567890123 AS a UNION ALL SELECT 1))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT SCALED_ROUND_INT_DIVIDE(SUM(\"values\".A), CAST(COUNT(\"values\".A) AS "
                + "NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT SET_COMBINE(12345678901234567890123, 1) AS \"A\" "
                + "FROM (SELECT 12345678901234567890123 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount "
                + "=> 1))) WB$0 UNION ALL (SELECT CAST(1 AS NUMBER(23,0)) AS \"1\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT "
                + "1234567890123456789.5 AS a UNION ALL SELECT 1))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT (CAST(SUM(\"values\".A) AS NUMBER(38,7))) / (CAST(COUNT(\"values\".A) AS "
                + "NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT SET_COMBINE(1234567890123456789.5, 1) AS \"A\" "
                + "FROM (SELECT 1234567890123456789.5 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount =>"
                + " 1))) WB$0 UNION ALL (SELECT CAST(1 AS NUMBER(20,1)) AS \"1\" FROM TABLE (GENERATOR(ROWCOUNT"
                + " => 1, rowCount => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT "
                + "123456789012345678.5 AS a UNION ALL SELECT 1))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT (CAST(SUM(CAST(\"values\".A AS NUMBER(19,1))) AS NUMBER(37,7))) / "
                + "(CAST(COUNT(\"values\".A) AS NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT "
                + "SET_COMBINE(123456789012345678.5, 1) AS \"A\" FROM (SELECT 123456789012345678.5 AS \"A\" "
                + "FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL (SELECT CAST(1 AS "
                + "NUMBER(19,1)) AS \"1\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT "
                + "12345678901234567890 AS a UNION ALL SELECT 1))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT (CAST(SUM(\"values\".A) AS NUMBER(38,6))) / (CAST(COUNT(\"values\".A) AS "
                + "NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT SET_COMBINE(12345678901234567890, 1) AS \"A\" FROM"
                + " (SELECT 12345678901234567890 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1)))"
                + " WB$0 UNION ALL (SELECT CAST(1 AS NUMBER(20,0)) AS \"1\" FROM TABLE (GENERATOR(ROWCOUNT => "
                + "1, rowCount => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT "
                + "1234567890123456789 AS a UNION ALL SELECT 1))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT (CAST(SUM(CAST(\"values\".A AS NUMBER(19,0))) AS NUMBER(37,6))) / "
                + "(CAST(COUNT(\"values\".A) AS NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT "
                + "SET_COMBINE(1234567890123456789, 1) AS \"A\" FROM (SELECT 1234567890123456789 AS \"A\" FROM "
                + "TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL (SELECT CAST(1 AS "
                + "NUMBER(19,0)) AS \"1\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(d) FROM (SELECT d FROM tp UNION"
                + " ALL SELECT d FROM tp))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT SCALED_ROUND_INT_DIVIDE(CAST(SUM(CAST(\"values\".D AS NUMBER(22,1))) AS "
                + "NUMBER(34,1)), CAST(COUNT(\"values\".D) AS NUMBER(18,0))) AS \"AVG(D)\" FROM (SELECT "
                + "SET_COMBINE(WB$0.D, WB$1.D) AS \"D\" FROM (SELECT TP.D AS \"D\" FROM TP AS TP) WB$0 UNION "
                + "ALL (SELECT TP.D AS \"D\" FROM TP AS TP) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(e) FROM (SELECT e FROM tp UNION"
                + " ALL SELECT e FROM tp))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT (CAST(SUM(CAST(\"values\".E AS NUMBER(20,10))) AS NUMBER(34,12))) / "
                + "(CAST(COUNT(\"values\".E) AS NUMBER(18,0))) AS \"AVG(E)\" FROM (SELECT SET_COMBINE(WB$0.E, "
                + "WB$1.E) AS \"E\" FROM (SELECT TP.E AS \"E\" FROM TP AS TP) WB$0 UNION ALL (SELECT TP.E AS "
                + "\"E\" FROM TP AS TP) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(f) FROM (SELECT f FROM tp UNION"
                + " ALL SELECT f FROM tp))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT (CAST(SUM(CAST(\"values\".F AS NUMBER(15,14))) AS NUMBER(27,14))) / "
                + "(CAST(COUNT(\"values\".F) AS NUMBER(18,0))) AS \"AVG(F)\" FROM (SELECT SET_COMBINE(WB$0.F, "
                + "WB$1.F) AS \"F\" FROM (SELECT TP.F AS \"F\" FROM TP AS TP) WB$0 UNION ALL (SELECT TP.F AS "
                + "\"F\" FROM TP AS TP) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT 1.2345678901234"
                + " AS a UNION ALL SELECT 2))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT (CAST(SUM(CAST(\"values\".A AS NUMBER(14,13))) AS NUMBER(26,13))) / "
                + "(CAST(COUNT(\"values\".A) AS NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT "
                + "SET_COMBINE(1.2345678901234, 2) AS \"A\" FROM (SELECT 1.2345678901234 AS \"A\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL (SELECT 2 AS \"2\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(g) FROM (SELECT g FROM tp UNION"
                + " ALL SELECT g FROM tp))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT SCALED_ROUND_INT_DIVIDE(CAST(SUM(CAST(\"values\".G AS NUMBER(30,5))) AS "
                + "NUMBER(38,5)), CAST(COUNT(\"values\".G) AS NUMBER(18,0))) AS \"AVG(G)\" FROM (SELECT "
                + "SET_COMBINE(WB$0.G, WB$1.G) AS \"G\" FROM (SELECT TP.G AS \"G\" FROM TP AS TP) WB$0 UNION "
                + "ALL (SELECT TP.G AS \"G\" FROM TP AS TP) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(h) FROM (SELECT h FROM tp UNION"
                + " ALL SELECT h FROM tp))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT SCALED_ROUND_INT_DIVIDE(CAST(SUM(CAST(\"values\".H AS NUMBER(25,0))) AS "
                + "NUMBER(37,0)), CAST(COUNT(\"values\".H) AS NUMBER(18,0))) AS \"AVG(H)\" FROM (SELECT "
                + "SET_COMBINE(WB$0.H, WB$1.H) AS \"H\" FROM (SELECT TP.H AS \"H\" FROM TP AS TP) WB$0 UNION "
                + "ALL (SELECT TP.H AS \"H\" FROM TP AS TP) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(i) FROM (SELECT i FROM tp UNION"
                + " ALL SELECT i FROM tp))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT SCALED_ROUND_INT_DIVIDE(CAST(SUM(CAST(\"values\".I AS NUMBER(21,0))) AS "
                + "NUMBER(33,0)), CAST(COUNT(\"values\".I) AS NUMBER(18,0))) AS \"AVG(I)\" FROM (SELECT "
                + "SET_COMBINE(WB$0.I, WB$1.I) AS \"I\" FROM (SELECT TP.I AS \"I\" FROM TP AS TP) WB$0 UNION "
                + "ALL (SELECT TP.I AS \"I\" FROM TP AS TP) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(j) FROM (SELECT j FROM tp UNION"
                + " ALL SELECT j FROM tp))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT (CAST(SUM(CAST(\"values\".J AS NUMBER(10,8))) AS NUMBER(26,12))) / "
                + "(CAST(COUNT(\"values\".J) AS NUMBER(18,0))) AS \"AVG(J)\" FROM (SELECT SET_COMBINE(WB$0.J, "
                + "WB$1.J) AS \"J\" FROM (SELECT TP.J AS \"J\" FROM TP AS TP) WB$0 UNION ALL (SELECT TP.J AS "
                + "\"J\" FROM TP AS TP) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT 1.23456789 AS a"
                + " UNION ALL SELECT 2))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT (CAST(SUM(CAST(\"values\".A AS NUMBER(9,8))) AS NUMBER(25,12))) / "
                + "(CAST(COUNT(\"values\".A) AS NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT "
                + "SET_COMBINE(1.23456789, 2) AS \"A\" FROM (SELECT 1.23456789 AS \"A\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL (SELECT 2 AS \"2\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT 12.345678901 AS"
                + " a UNION ALL SELECT 2))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT (CAST(SUM(CAST(\"values\".A AS NUMBER(11,9))) AS NUMBER(26,12))) / "
                + "(CAST(COUNT(\"values\".A) AS NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT "
                + "SET_COMBINE(12.345678901, 2) AS \"A\" FROM (SELECT 12.345678901 AS \"A\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL (SELECT CAST(2 AS NUMBER(11,9)) AS"
                + " \"2\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT 1234567890123.5"
                + " AS a UNION ALL SELECT 2))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT (CAST(SUM(CAST(\"values\".A AS NUMBER(14,1))) AS NUMBER(32,7))) / "
                + "(CAST(COUNT(\"values\".A) AS NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT "
                + "SET_COMBINE(1234567890123.5, 2) AS \"A\" FROM (SELECT 1234567890123.5 AS \"A\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL (SELECT CAST(2 AS NUMBER(14,1)) AS"
                + " \"2\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(d) FROM (SELECT d FROM tp UNION"
                + " ALL SELECT 1))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT SCALED_ROUND_INT_DIVIDE(CAST(SUM(CAST(\"values\".D AS NUMBER(22,1))) AS "
                + "NUMBER(34,1)), CAST(COUNT(\"values\".D) AS NUMBER(18,0))) AS \"AVG(D)\" FROM (SELECT "
                + "SET_COMBINE(WB$0.D, WB$1.\"1\") AS \"D\" FROM (SELECT TP.D AS \"D\" FROM TP AS TP) WB$0 "
                + "UNION ALL (SELECT ENSURE_NULLABLE(1) AS \"1\" FROM (VALUES (null)) DUAL) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT 127 AS a UNION "
                + "ALL SELECT 128))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(127, 128) AS \"A\" "
                + "FROM (SELECT CAST(127 AS NUMBER(3,0)) AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount"
                + " => 1))) WB$0 UNION ALL (SELECT 128 AS \"128\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount"
                + " => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT 128 AS a UNION "
                + "ALL SELECT 127))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(128, 127) AS \"A\" "
                + "FROM (SELECT 128 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION "
                + "ALL (SELECT CAST(127 AS NUMBER(3,0)) AS \"127\" FROM TABLE (GENERATOR(ROWCOUNT => 1, "
                + "rowCount => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT 32767 AS a "
                + "UNION ALL SELECT 32768))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(32767, 32768) AS "
                + "\"A\" FROM (SELECT CAST(32767 AS NUMBER(5,0)) AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, "
                + "rowCount => 1))) WB$0 UNION ALL (SELECT 32768 AS \"32768\" FROM TABLE (GENERATOR(ROWCOUNT =>"
                + " 1, rowCount => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT 1.27 AS a UNION"
                + " ALL SELECT 1.28))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(1.27, 1.28) AS \"A\""
                + " FROM (SELECT CAST(1.27 AS NUMBER(3,2)) AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, "
                + "rowCount => 1))) WB$0 UNION ALL (SELECT 1.28 AS \"1.28\" FROM TABLE (GENERATOR(ROWCOUNT => "
                + "1, rowCount => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT 0.5 AS a UNION "
                + "ALL SELECT 1.28))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(0.5, 1.28) AS \"A\" "
                + "FROM (SELECT CAST(0.5 AS NUMBER(3,2)) AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount"
                + " => 1))) WB$0 UNION ALL (SELECT 1.28 AS \"1.28\" FROM TABLE (GENERATOR(ROWCOUNT => 1, "
                + "rowCount => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT 2147483647 AS a"
                + " UNION ALL SELECT 2147483648))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(2147483647, "
                + "2147483648) AS \"A\" FROM (SELECT CAST(2147483647 AS NUMBER(10,0)) AS \"A\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL (SELECT 2147483648 AS "
                + "\"2147483648\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT 1 AS a UNION "
                + "ALL SELECT 200 UNION ALL SELECT 70000))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(1, 200, 70000) AS "
                + "\"A\" FROM (SELECT CAST(1 AS NUMBER(5,0)) AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, "
                + "rowCount => 1))) WB$0 UNION ALL (SELECT CAST(200 AS NUMBER(5,0)) AS \"200\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 UNION ALL (SELECT 70000 AS \"70000\" FROM "
                + "TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$2) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT 100 AS a UNION "
                + "SELECT 200 UNION SELECT 300))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(100, 200, 300) AS "
                + "\"A\" FROM (SELECT CAST(100 AS NUMBER(3,0)) AS \"A\" FROM (VALUES (null)) DUAL GROUP BY 100)"
                + " WB$0 UNION ALL (SELECT 200 AS \"200\" FROM (VALUES (null)) DUAL GROUP BY 200) WB$1 UNION "
                + "ALL (SELECT 300 AS \"300\" FROM (VALUES (null)) DUAL GROUP BY 300) WB$2 GROUP BY A) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT 100 AS a "
                + "INTERSECT SELECT 200))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(100) AS \"MAX(A)\" FROM (SELECT 100 AS \"A\" FROM (SELECT 100 AS \"A\" "
                + "FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 SEMI JOIN (SELECT 200 AS \"200\" "
                + "FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 ON ((CAST(WB$0.A AS NUMBER(3,0)))"
                + " = WB$1.\"200\") GROUP BY 100) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 UNION"
                + " ALL SELECT 1000))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, "
                + "WB$1.\"1000\") AS \"A\" FROM (SELECT CAST(T1.A AS NUMBER(38,0)) AS \"A\" FROM T1 AS T1) WB$0"
                + " UNION ALL (SELECT ENSURE_NULLABLE(1000) AS \"1000\" FROM (VALUES (null)) DUAL) WB$1) "
                + "values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT 1 AS a UNION "
                + "ALL SELECT 9223372036854775808))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(1, "
                + "9223372036854775808) AS \"A\" FROM (SELECT CAST(1 AS NUMBER(19,0)) AS \"A\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL (SELECT 9223372036854775808 AS "
                + "\"9223372036854775808\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT 0.001 AS a "
                + "UNION ALL SELECT 1000))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(0.001, 1000) AS "
                + "\"A\" FROM (SELECT CAST(0.001 AS NUMBER(7,3)) AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, "
                + "rowCount => 1))) WB$0 UNION ALL (SELECT 1000 AS \"1000\" FROM TABLE (GENERATOR(ROWCOUNT => "
                + "1, rowCount => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT 100 AS a, 1 AS "
                + "b UNION ALL SELECT 2, 300))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(100, 2) AS \"A\" "
                + "FROM (SELECT 100 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION "
                + "ALL (SELECT 2 AS \"2\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 100 AS a "
                + "UNION ALL SELECT 200))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT null AS \"CNULL\" FROM (SELECT null AS "
                + "\"CNULL\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL (SELECT null "
                + "AS \"CNULL\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT 100 AS a EXCEPT"
                + " SELECT 200))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(100) AS \"MAX(A)\" FROM (SELECT 100 AS \"A\" FROM (SELECT 100 AS \"A\" "
                + "FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 GROUP BY 100) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT "
                + "100::NUMBER(5,0) AS a UNION ALL SELECT 200))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(100, 200) AS \"A\" "
                + "FROM (SELECT CAST(100 AS NUMBER(5,0)) AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount"
                + " => 1))) WB$0 UNION ALL (SELECT 200 AS \"200\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount"
                + " => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT 1 AS a UNION "
                + "ALL SELECT 2 UNION ALL SELECT 32768))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(1, 2, 32768) AS "
                + "\"A\" FROM (SELECT CAST(1 AS NUMBER(5,0)) AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, "
                + "rowCount => 1))) WB$0 UNION ALL (SELECT CAST(2 AS NUMBER(5,0)) AS \"2\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 UNION ALL (SELECT 32768 AS \"32768\" FROM "
                + "TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$2) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT 1 AS a UNION "
                + "ALL (SELECT 2 UNION ALL SELECT 300)))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(1, OB$3.\"2\") AS "
                + "\"A\" FROM (SELECT CAST(1 AS NUMBER(3,0)) AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, "
                + "rowCount => 1))) WB$2 UNION ALL (SELECT SET_COMBINE(2, 300) AS \"2\" FROM (SELECT CAST(2 AS "
                + "NUMBER(3,0)) AS \"2\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL "
                + "(SELECT 300 AS \"300\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) OB$3) "
                + "values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t2 UNION"
                + " ALL SELECT 1000))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(4, WB$1.\"1000\") AS"
                + " \"A\" FROM (SELECT CAST(4 AS NUMBER(38,0)) AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, "
                + "rowCount => 1))) WB$0 UNION ALL (SELECT ENSURE_NULLABLE(1000) AS \"1000\" FROM (VALUES "
                + "(null)) DUAL) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(c) FROM (SELECT a, b, c FROM tw"
                + " UNION ALL BY NAME SELECT b, a, c FROM tw))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".C) AS \"MAX(C)\" FROM (SELECT SET_COMBINE(WB$0.C, WB$1.C) AS "
                + "\"C\" FROM (SELECT TW.C AS \"C\" FROM TW AS TW) WB$0 UNION ALL (SELECT TW.C AS \"C\" FROM TW"
                + " AS TW) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a, b FROM tw "
                + "UNION ALL BY NAME SELECT b, a FROM tn UNION ALL BY NAME SELECT a, b FROM tn))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A, "
                + "WB$2.A) AS \"A\" FROM (SELECT TW.A AS \"A\" FROM TW AS TW) WB$0 UNION ALL (SELECT TN.B AS "
                + "\"B\", TN.A AS \"A\" FROM TN AS TN) WB$1 UNION ALL (SELECT TN.A AS \"A\", TN.B AS \"B\" FROM"
                + " TN AS TN) WB$2) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a, b FROM tw "
                + "UNION ALL BY NAME SELECT b, a FROM tn UNION ALL SELECT a, b FROM tn))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(OB$2.A, WB$2.A) AS "
                + "\"A\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A) AS \"A\" FROM (SELECT TW.A AS \"A\" FROM TW "
                + "AS TW) WB$0 UNION ALL (SELECT TN.B AS \"B\", TN.A AS \"A\" FROM TN AS TN) WB$1) OB$2 UNION "
                + "ALL (SELECT TN.A AS \"A\" FROM TN AS TN) WB$2) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 UNION"
                + " ALL SELECT a FROM t2 UNION SELECT a FROM t3 UNION SELECT a FROM t1))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(OB$2.A, WB$2.A, "
                + "WB$3.A) AS \"A\" FROM (SELECT SET_COMBINE(WB$0.A, 4) AS \"A\" FROM (SELECT T1.A AS \"A\" "
                + "FROM T1 AS T1) WB$0 UNION ALL (SELECT 4 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, "
                + "rowCount => 1))) WB$1 GROUP BY A) OB$2 UNION ALL (SELECT T3.A AS \"A\" FROM T3 AS T3 GROUP "
                + "BY A) WB$2 UNION ALL (SELECT T1.A AS \"A\" FROM T1 AS T1 GROUP BY A) WB$3 GROUP BY A) "
                + "values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a, b FROM tw "
                + "UNION BY NAME SELECT b, a FROM t1r))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, 8) AS \"A\","
                + " SET_COMBINE(WB$0.B, 9) AS \"B\" FROM (SELECT TW.A AS \"A\", TW.B AS \"B\" FROM TW AS TW) "
                + "WB$0 UNION ALL (SELECT 9 AS \"B\", 8 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount "
                + "=> 1))) WB$1 GROUP BY A, B) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a, b FROM tw "
                + "UNION ALL BY NAME SELECT b, a FROM tn WHERE a > 1))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A) AS "
                + "\"A\" FROM (SELECT TW.A AS \"A\" FROM TW AS TW) WB$0 UNION ALL (SELECT TN.B AS \"B\", TN.A "
                + "AS \"A\" FROM TN AS TN WHERE TN.A > 1) WB$1) values)'"},
        });
    }

    @Test
    public void intersectAndNestedWidths() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        engine.execute("INSERT INTO t1 VALUES (1), (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE t2 (a INT)");
        engine.execute("INSERT INTO t2 VALUES (4)");
        engine.execute("CREATE OR REPLACE TABLE t3 (a INT)");
        engine.execute("INSERT INTO t3 VALUES (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE tw (a INT, b INT, c INT)");
        engine.execute("INSERT INTO tw VALUES (1, 10, 100), (2, 20, 200)");
        engine.execute("CREATE OR REPLACE TABLE tn (a INT, b INT)");
        engine.execute("INSERT INTO tn VALUES (1, 5), (2, 6), (3, 7)");
        engine.execute("CREATE OR REPLACE TABLE tx (a INT, b INT)");
        engine.execute("INSERT INTO tx VALUES (7, 1), (7, 2), (7, 3)");
        engine.execute("CREATE OR REPLACE TABLE t1r (a INT, b INT)");
        engine.execute("INSERT INTO t1r VALUES (8, 9)");
        engine.execute("CREATE OR REPLACE TABLE t7 (a INT)");
        engine.execute("INSERT INTO t7 VALUES (7), (7), (7)");
        engine.execute("CREATE OR REPLACE TABLE tb (b INT, a INT)");
        engine.execute("INSERT INTO tb VALUES (5, 1), (6, 2)");
        engine.execute("CREATE OR REPLACE TABLE tp (d NUMBER(22,1), e NUMBER(20,10), f NUMBER(15,14), g "
            + "NUMBER(30,5), h NUMBER(25,0), i NUMBER(21,0), j NUMBER(10,8))");
        engine.execute("INSERT INTO tp VALUES (1.5, 2.5, 3.5, 4.5, 5, 6, 7.5), (2.5, 3.5, 4.5, 5.5, 6, 7, 8.5)");
        engine.execute("CREATE OR REPLACE TABLE tl (a INT)");
        engine.execute("INSERT INTO tl VALUES (1000), (2000)");
        engine.execute("CREATE OR REPLACE TABLE tz (a INT)");
        engine.execute("INSERT INTO tz VALUES (7), (NULL), (7)");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT 200 AS a "
                + "INTERSECT SELECT 100))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(200) AS \"MAX(A)\" FROM (SELECT 200 AS \"A\" FROM (SELECT 200 AS \"A\" "
                + "FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 SEMI JOIN (SELECT 100 AS \"100\" "
                + "FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 ON (WB$0.A = (CAST(WB$1.\"100\" "
                + "AS NUMBER(3,0)))) GROUP BY 200) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 "
                + "INTERSECT SELECT 1000))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT WB$0.A AS \"A\" FROM (SELECT "
                + "T1.A AS \"A\" FROM T1 AS T1) WB$0 SEMI JOIN (SELECT 1000 AS \"1000\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 ON ((CAST(WB$0.A AS NUMBER(38,0))) = "
                + "WB$1.\"1000\") GROUP BY A) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 "
                + "INTERSECT SELECT a FROM tl))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT WB$0.A AS \"A\" FROM (SELECT "
                + "T1.A AS \"A\" FROM T1 AS T1) WB$0 SEMI JOIN (SELECT TL.A AS \"A\" FROM TL AS TL) WB$1 ON "
                + "((CAST(WB$0.A AS NUMBER(38,0))) = WB$1.A) GROUP BY A) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM ((SELECT 1 AS a UNION "
                + "ALL SELECT 2) UNION ALL SELECT 300))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(OB$2.A, 300) AS "
                + "\"A\" FROM (SELECT CAST(CAST(SET_COMBINE(1, 2) AS NUMBER(3,0)) AS NUMBER(3,0)) AS \"A\" FROM"
                + " (SELECT 1 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL "
                + "(SELECT 2 AS \"2\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) OB$2 UNION "
                + "ALL (SELECT 300 AS \"300\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$2) "
                + "values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM tl UNION"
                + " ALL SELECT 1))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.\"1\") "
                + "AS \"A\" FROM (SELECT TL.A AS \"A\" FROM TL AS TL) WB$0 UNION ALL (SELECT "
                + "CAST(ENSURE_NULLABLE(1) AS NUMBER(38,0)) AS \"1\" FROM (VALUES (null)) DUAL) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT SUM(a) FROM (SELECT "
                + "12345678901234567890 AS a UNION ALL SELECT 1))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT SUM(\"values\".A) AS \"SUM(A)\" FROM (SELECT "
                + "SET_COMBINE(12345678901234567890, 1) AS \"A\" FROM (SELECT 12345678901234567890 AS \"A\" "
                + "FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL (SELECT CAST(1 AS "
                + "NUMBER(20,0)) AS \"1\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 UNION"
                + " SELECT a FROM t3 UNION SELECT 1000))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A, "
                + "WB$2.\"1000\") AS \"A\" FROM (SELECT CAST(T1.A AS NUMBER(38,0)) AS \"A\" FROM T1 AS T1 GROUP"
                + " BY A) WB$0 UNION ALL (SELECT CAST(T3.A AS NUMBER(38,0)) AS \"A\" FROM T3 AS T3 GROUP BY A) "
                + "WB$1 UNION ALL (SELECT ENSURE_NULLABLE(1000) AS \"1000\" FROM (VALUES (null)) DUAL GROUP BY "
                + "1000) WB$2 GROUP BY A) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT 100 AS a, 1 AS "
                + "b UNION SELECT 2, 300))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(100, 2) AS \"A\", "
                + "SET_COMBINE(1, 300) AS \"B\" FROM (SELECT 100 AS \"A\", CAST(1 AS NUMBER(3,0)) AS \"B\" FROM"
                + " TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL (SELECT 2 AS \"2\", 300 AS "
                + "\"300\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 GROUP BY A, B) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(d) FROM (SELECT d FROM tp UNION"
                + " ALL SELECT 1.25))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".D) AS \"MAX(D)\" FROM (SELECT SET_COMBINE(WB$0.D, "
                + "WB$1.\"1.25\") AS \"D\" FROM (SELECT CAST(TP.D AS NUMBER(23,2)) AS \"D\" FROM TP AS TP) WB$0"
                + " UNION ALL (SELECT CAST(ENSURE_NULLABLE(1.25) AS NUMBER(23,2)) AS \"1.25\" FROM (VALUES "
                + "(null)) DUAL) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 UNION"
                + " ALL SELECT a FROM tl))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A) AS "
                + "\"A\" FROM (SELECT CAST(T1.A AS NUMBER(38,0)) AS \"A\" FROM T1 AS T1) WB$0 UNION ALL (SELECT"
                + " TL.A AS \"A\" FROM TL AS TL) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM tz UNION"
                + " ALL SELECT 1000))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, "
                + "WB$1.\"1000\") AS \"A\" FROM (SELECT CAST(TZ.A AS NUMBER(38,0)) AS \"A\" FROM TZ AS TZ) WB$0"
                + " UNION ALL (SELECT ENSURE_NULLABLE(1000) AS \"1000\" FROM (VALUES (null)) DUAL) WB$1) "
                + "values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM ((SELECT 1 AS a UNION "
                + "SELECT 2) UNION ALL SELECT 300))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$2.A, 300) AS "
                + "\"A\" FROM (SELECT CAST(CAST(OB$2.A AS NUMBER(3,0)) AS NUMBER(3,0)) AS \"A\" FROM (SELECT "
                + "SET_COMBINE(1, 2) AS \"A\" FROM (SELECT 1 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, "
                + "rowCount => 1))) WB$0 UNION ALL (SELECT 2 AS \"2\" FROM TABLE (GENERATOR(ROWCOUNT => 1, "
                + "rowCount => 1))) WB$1 GROUP BY A) OB$2) WB$2 UNION ALL (SELECT 300 AS \"300\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$3) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT 300 AS a UNION "
                + "ALL (SELECT 1 UNION ALL SELECT 2)))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(300, OB$3.\"1\") AS "
                + "\"A\" FROM (SELECT 300 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$2 "
                + "UNION ALL (SELECT CAST(CAST(SET_COMBINE(1, 2) AS NUMBER(3,0)) AS NUMBER(3,0)) AS \"1\" FROM "
                + "(SELECT 1 AS \"1\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL "
                + "(SELECT 2 AS \"2\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) OB$3) "
                + "values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT SUM(a) FROM (SELECT a FROM t1 UNION"
                + " ALL SELECT a FROM tl))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT SUM(CAST(\"values\".A AS NUMBER(38,0))) AS \"SUM(A)\" FROM (SELECT "
                + "SET_COMBINE(WB$0.A, WB$1.A) AS \"A\" FROM (SELECT CAST(T1.A AS NUMBER(38,0)) AS \"A\" FROM "
                + "T1 AS T1) WB$0 UNION ALL (SELECT TL.A AS \"A\" FROM TL AS TL) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT AVG(a) FROM (SELECT "
                + "12345678901234567890 AS a UNION ALL SELECT a FROM t1))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT SCALED_ROUND_INT_DIVIDE(SUM(\"values\".A), CAST(COUNT(\"values\".A) AS "
                + "NUMBER(18,0))) AS \"AVG(A)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A) AS \"A\" FROM (SELECT "
                + "ENSURE_NULLABLE(12345678901234567890) AS \"A\" FROM (VALUES (null)) DUAL) WB$0 UNION ALL "
                + "(SELECT CAST(T1.A AS NUMBER(38,0)) AS \"A\" FROM T1 AS T1) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t2 UNION"
                + " ALL SELECT a FROM tl))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(4, WB$1.A) AS \"A\" "
                + "FROM (SELECT CAST(4 AS NUMBER(38,0)) AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount "
                + "=> 1))) WB$0 UNION ALL (SELECT TL.A AS \"A\" FROM TL AS TL) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a FROM t1 UNION"
                + " ALL SELECT 1.5))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, "
                + "WB$1.\"1.5\") AS \"A\" FROM (SELECT CAST(T1.A AS NUMBER(38,1)) AS \"A\" FROM T1 AS T1) WB$0 "
                + "UNION ALL (SELECT ENSURE_NULLABLE(1.5) AS \"1.5\" FROM (VALUES (null)) DUAL) WB$1) values)'"},
        });
    }

    @Test
    public void byNameChains() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        engine.execute("INSERT INTO t1 VALUES (1), (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE t2 (a INT)");
        engine.execute("INSERT INTO t2 VALUES (4)");
        engine.execute("CREATE OR REPLACE TABLE t3 (a INT)");
        engine.execute("INSERT INTO t3 VALUES (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE tw (a INT, b INT, c INT)");
        engine.execute("INSERT INTO tw VALUES (1, 10, 100), (2, 20, 200)");
        engine.execute("CREATE OR REPLACE TABLE tn (a INT, b INT)");
        engine.execute("INSERT INTO tn VALUES (1, 5), (2, 6), (3, 7)");
        engine.execute("CREATE OR REPLACE TABLE tx (a INT, b INT)");
        engine.execute("INSERT INTO tx VALUES (7, 1), (7, 2), (7, 3)");
        engine.execute("CREATE OR REPLACE TABLE t1r (a INT, b INT)");
        engine.execute("INSERT INTO t1r VALUES (8, 9)");
        engine.execute("CREATE OR REPLACE TABLE t7 (a INT)");
        engine.execute("INSERT INTO t7 VALUES (7), (7), (7)");
        engine.execute("CREATE OR REPLACE TABLE tb (b INT, a INT)");
        engine.execute("INSERT INTO tb VALUES (5, 1), (6, 2)");
        engine.execute("CREATE OR REPLACE TABLE tp (d NUMBER(22,1), e NUMBER(20,10), f NUMBER(15,14), g "
            + "NUMBER(30,5), h NUMBER(25,0), i NUMBER(21,0), j NUMBER(10,8))");
        engine.execute("INSERT INTO tp VALUES (1.5, 2.5, 3.5, 4.5, 5, 6, 7.5), (2.5, 3.5, 4.5, 5.5, 6, 7, 8.5)");
        engine.execute("CREATE OR REPLACE TABLE tl (a INT)");
        engine.execute("INSERT INTO tl VALUES (1000), (2000)");
        engine.execute("CREATE OR REPLACE TABLE tz (a INT)");
        engine.execute("INSERT INTO tz VALUES (7), (NULL), (7)");
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        engine.execute("INSERT INTO t1 VALUES (1), (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE t2 (a INT)");
        engine.execute("INSERT INTO t2 VALUES (4)");
        engine.execute("CREATE OR REPLACE TABLE t3 (a INT)");
        engine.execute("INSERT INTO t3 VALUES (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE tw (a INT, b INT, c INT)");
        engine.execute("INSERT INTO tw VALUES (1, 10, 100), (2, 20, 200)");
        engine.execute("CREATE OR REPLACE TABLE tn (a INT, b INT)");
        engine.execute("INSERT INTO tn VALUES (1, 5), (2, 6), (3, 7)");
        engine.execute("CREATE OR REPLACE TABLE tx (a INT, b INT)");
        engine.execute("INSERT INTO tx VALUES (7, 1), (7, 2), (7, 3)");
        engine.execute("CREATE OR REPLACE TABLE t1r (a INT, b INT)");
        engine.execute("INSERT INTO t1r VALUES (8, 9)");
        engine.execute("CREATE OR REPLACE TABLE t7 (a INT)");
        engine.execute("INSERT INTO t7 VALUES (7), (7), (7)");
        engine.execute("CREATE OR REPLACE TABLE tb (b INT, a INT)");
        engine.execute("INSERT INTO tb VALUES (5, 1), (6, 2)");
        engine.execute("CREATE OR REPLACE TABLE tp (d NUMBER(22,1), e NUMBER(20,10), f NUMBER(15,14), g "
            + "NUMBER(30,5), h NUMBER(25,0), i NUMBER(21,0), j NUMBER(10,8))");
        engine.execute("INSERT INTO tp VALUES (1.5, 2.5, 3.5, 4.5, 5, 6, 7.5), (2.5, 3.5, 4.5, 5.5, 6, 7, 8.5)");
        engine.execute("CREATE OR REPLACE TABLE tl (a INT)");
        engine.execute("INSERT INTO tl VALUES (1000), (2000)");
        engine.execute("CREATE OR REPLACE TABLE tz (a INT)");
        engine.execute("INSERT INTO tz VALUES (7), (NULL), (7)");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a, b FROM tw "
                + "UNION ALL BY NAME SELECT b, a FROM tn UNION ALL BY NAME SELECT b, a FROM tn))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A, "
                + "WB$2.A) AS \"A\" FROM (SELECT TW.A AS \"A\" FROM TW AS TW) WB$0 UNION ALL (SELECT TN.B AS "
                + "\"B\", TN.A AS \"A\" FROM TN AS TN) WB$1 UNION ALL (SELECT TN.B AS \"B\", TN.A AS \"A\" FROM"
                + " TN AS TN) WB$2) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(b) FROM (SELECT a, b, c FROM tw"
                + " UNION ALL BY NAME SELECT c, b, a FROM tw UNION ALL BY NAME SELECT a, b, c FROM tw))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".B) AS \"MAX(B)\" FROM (SELECT SET_COMBINE(WB$0.B, WB$1.B, "
                + "WB$2.B) AS \"B\" FROM (SELECT TW.B AS \"B\" FROM TW AS TW) WB$0 UNION ALL (SELECT TW.B AS "
                + "\"B\" FROM TW AS TW) WB$1 UNION ALL (SELECT TW.B AS \"B\" FROM TW AS TW) WB$2) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a, b FROM tw "
                + "UNION BY NAME SELECT 3 AS b, 4 AS a))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, 4) AS \"A\","
                + " SET_COMBINE(WB$0.B, 3) AS \"B\" FROM (SELECT TW.A AS \"A\", TW.B AS \"B\" FROM TW AS TW) "
                + "WB$0 UNION ALL (SELECT 3 AS \"B\", 4 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount "
                + "=> 1))) WB$1 GROUP BY A, B) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a, b FROM tw "
                + "UNION ALL BY NAME SELECT 4 AS a, 3 AS b))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, 4) AS \"A\" "
                + "FROM (SELECT TW.A AS \"A\" FROM TW AS TW) WB$0 UNION ALL (SELECT 4 AS \"A\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(c) FROM (SELECT a, b, c FROM tw"
                + " UNION ALL BY NAME SELECT b, a, c FROM tw UNION ALL BY NAME SELECT a, b, c FROM tw))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".C) AS \"MAX(C)\" FROM (SELECT SET_COMBINE(WB$0.C, WB$1.C, "
                + "WB$2.C) AS \"C\" FROM (SELECT TW.C AS \"C\" FROM TW AS TW) WB$0 UNION ALL (SELECT TW.C AS "
                + "\"C\" FROM TW AS TW) WB$1 UNION ALL (SELECT TW.C AS \"C\" FROM TW AS TW) WB$2) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a, b FROM tw "
                + "UNION ALL BY NAME SELECT b, a FROM tn UNION ALL BY NAME SELECT a, b FROM tn UNION ALL BY "
                + "NAME SELECT a, b FROM tn))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A, "
                + "WB$2.A, WB$3.A) AS \"A\" FROM (SELECT TW.A AS \"A\" FROM TW AS TW) WB$0 UNION ALL (SELECT "
                + "TN.B AS \"B\", TN.A AS \"A\" FROM TN AS TN) WB$1 UNION ALL (SELECT TN.A AS \"A\", TN.B AS "
                + "\"B\" FROM TN AS TN) WB$2 UNION ALL (SELECT TN.A AS \"A\", TN.B AS \"B\" FROM TN AS TN) "
                + "WB$3) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a, b FROM tw "
                + "UNION ALL BY NAME SELECT a, b FROM tn UNION ALL BY NAME SELECT b, a FROM tn UNION ALL BY "
                + "NAME SELECT a, b FROM tn))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A, "
                + "WB$2.A, WB$3.A) AS \"A\" FROM (SELECT TW.A AS \"A\" FROM TW AS TW) WB$0 UNION ALL (SELECT "
                + "TN.A AS \"A\" FROM TN AS TN) WB$1 UNION ALL (SELECT TN.B AS \"B\", TN.A AS \"A\" FROM TN AS "
                + "TN) WB$2 UNION ALL (SELECT TN.A AS \"A\", TN.B AS \"B\" FROM TN AS TN) WB$3) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT 1.50 AS a UNION"
                + " ALL SELECT 200))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(1.5, 200) AS \"A\" "
                + "FROM (SELECT CAST(1.5 AS NUMBER(4,1)) AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount"
                + " => 1))) WB$0 UNION ALL (SELECT 200 AS \"200\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount"
                + " => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT 100.0 AS a "
                + "UNION ALL SELECT 2.5))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(100, 2.5) AS \"A\" "
                + "FROM (SELECT 100 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION "
                + "ALL (SELECT CAST(2.5 AS NUMBER(4,1)) AS \"2.5\" FROM TABLE (GENERATOR(ROWCOUNT => 1, "
                + "rowCount => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT 2.5 AS a UNION "
                + "ALL SELECT 100.0))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(2.5, 100) AS \"A\" "
                + "FROM (SELECT CAST(2.5 AS NUMBER(4,1)) AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount"
                + " => 1))) WB$0 UNION ALL (SELECT 100 AS \"100.0\" FROM TABLE (GENERATOR(ROWCOUNT => 1, "
                + "rowCount => 1))) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT 4 AS a, 3 AS b "
                + "UNION ALL BY NAME SELECT a, b FROM tw))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(4, WB$1.A) AS \"A\" "
                + "FROM (SELECT 4 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL"
                + " (SELECT TW.A AS \"A\" FROM TW AS TW) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a, b FROM tw "
                + "UNION ALL BY NAME SELECT a, b FROM tn UNION ALL SELECT a, b FROM tn))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(OB$2.A, WB$2.A) AS "
                + "\"A\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A) AS \"A\" FROM (SELECT TW.A AS \"A\" FROM TW "
                + "AS TW) WB$0 UNION ALL (SELECT TN.A AS \"A\" FROM TN AS TN) WB$1) OB$2 UNION ALL (SELECT TN.A"
                + " AS \"A\" FROM TN AS TN) WB$2) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT 4 AS a, 3 AS b "
                + "UNION ALL BY NAME SELECT b, a FROM tw))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(4, WB$1.A) AS \"A\" "
                + "FROM (SELECT 4 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL"
                + " (SELECT TW.B AS \"B\", TW.A AS \"A\" FROM TW AS TW) WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a, b FROM tw "
                + "UNION ALL BY NAME SELECT 3 AS b, 4 AS a UNION ALL BY NAME SELECT a, b FROM tn))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, 4, WB$2.A) "
                + "AS \"A\" FROM (SELECT TW.A AS \"A\" FROM TW AS TW) WB$0 UNION ALL (SELECT 3 AS \"B\", 4 AS "
                + "\"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 UNION ALL (SELECT TN.A AS "
                + "\"A\", TN.B AS \"B\" FROM TN AS TN) WB$2) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(a) FROM (SELECT a, b FROM tw "
                + "UNION ALL BY NAME SELECT a, b FROM tn UNION ALL BY NAME SELECT 4 AS a, 3 AS b))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, "
                + "found '(SELECT MAX(\"values\".A) AS \"MAX(A)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A, 4) "
                + "AS \"A\" FROM (SELECT TW.A AS \"A\" FROM TW AS TW) WB$0 UNION ALL (SELECT TN.A AS \"A\" FROM"
                + " TN AS TN) WB$1 UNION ALL (SELECT 4 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount "
                + "=> 1))) WB$2) values)'"},
        });
    }
}
