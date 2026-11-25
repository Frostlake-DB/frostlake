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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * A HAVING aggregate over a correlated subquery that reads one relation whole: a COUNT of an outer column the
 * statistics settle holds no NULL counts the subquery's rows (a VALUES cell written as an expression of constants is
 * read as the value it folds to), an aggregate of the subquery's own beside an outer column is answered row by row,
 * an AVG over an aggregate of the outer row alone is refused at the subquery, and a derived relation's column defined
 * by arithmetic over a VALUES list is printed in the nesting as the plan prints it (all live-verified). The account
 * answers a SUM or a COUNT over a settled COUNT with a value that ignores the group's rows; the engine answers the
 * group's own value, asserted on the embedded engine alone.
 */
public class HavingSubqueryRowValuesTest extends MeasuredCellsTestSupport {

    @Test
    public void settledCount() {
        engine.execute("CREATE OR REPLACE TABLE g (v INT)");
        engine.execute("INSERT INTO g VALUES (5), (7)");
        engine.execute("CREATE OR REPLACE TABLE t (id INT NOT NULL, b BOOLEAN)");
        engine.execute("INSERT INTO t VALUES (5, TRUE), (7, FALSE), (8, FALSE)");
        assertCells(new String[][] {
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE), (8, FALSE)) fz(id, b) GROUP BY b HAVING "
                + "SUM((SELECT COUNT(fz.id) FROM g)) = 1 ORDER BY 1",
                ""},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE), (8, FALSE)) fz(id, b) GROUP BY b HAVING "
                + "SUM((SELECT COUNT(fz.id) FROM g)) = 3 ORDER BY 1",
                ""},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE), (8, FALSE)) fz(id, b) GROUP BY b HAVING "
                + "SUM((SELECT COUNT(fz.id) FROM g)) = 5 ORDER BY 1",
                ""},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE), (8, FALSE)) fz(id, b) GROUP BY b HAVING "
                + "SUM((SELECT COUNT(fz.id) FROM g)) = 6 ORDER BY 1",
                ""},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE), (8, FALSE)) fz(id, b) GROUP BY b HAVING "
                + "SUM((SELECT COUNT(fz.id) FROM g)) = 7 ORDER BY 1",
                ""},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE), (8, FALSE)) fz(id, b) GROUP BY b HAVING "
                + "SUM((SELECT COUNT(fz.id) FROM g)) = 8 ORDER BY 1",
                ""},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE), (8, FALSE)) fz(id, b) GROUP BY b HAVING "
                + "SUM((SELECT COUNT(fz.id) FROM g)) = 9 ORDER BY 1",
                ""},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE), (8, FALSE)) fz(id, b) GROUP BY b HAVING "
                + "SUM((SELECT COUNT(fz.id) FROM g)) = 10 ORDER BY 1",
                ""},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE), (8, FALSE)) fz(id, b) GROUP BY b HAVING "
                + "SUM((SELECT COUNT(fz.id) FROM g)) = 11 ORDER BY 1",
                ""},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE), (8, FALSE)) fz(id, b) GROUP BY b HAVING "
                + "SUM((SELECT COUNT(fz.id) FROM g)) = 12 ORDER BY 1",
                ""},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE), (8, FALSE)) fz(id, b) GROUP BY b HAVING "
                + "COUNT((SELECT COUNT(fz.id) FROM g)) = 3 ORDER BY 1",
                ""},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE), (8, FALSE)) fz(id, b) GROUP BY b HAVING "
                + "COUNT((SELECT COUNT(fz.id) FROM g)) = 4 ORDER BY 1",
                ""},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE), (8, FALSE)) fz(id, b) GROUP BY b HAVING "
                + "COUNT((SELECT COUNT(fz.id) FROM g)) = 5 ORDER BY 1",
                ""},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE), (8, FALSE)) fz(id, b) GROUP BY b HAVING "
                + "COUNT((SELECT COUNT(fz.id) FROM g)) = 6 ORDER BY 1",
                ""},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE), (8, FALSE)) fz(id, b) GROUP BY b HAVING "
                + "AVG((SELECT COUNT(fz.id) FROM g)) = 1 ORDER BY 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 89"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE), (8, FALSE)) fz(id, b) GROUP BY b HAVING "
                + "AVG((SELECT COUNT(fz.id) FROM g)) = 2 ORDER BY 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 89"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE), (8, FALSE)) fz(id, b) GROUP BY b HAVING "
                + "AVG((SELECT COUNT(fz.id) FROM g)) = 3 ORDER BY 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 89"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE), (8, FALSE)) fz(id, b) GROUP BY b HAVING "
                + "MAX((SELECT COUNT(fz.id) FROM g)) = 2 ORDER BY 1",
                "false ;; true"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE), (8, FALSE)) fz(id, b) GROUP BY b HAVING "
                + "MIN((SELECT COUNT(fz.id) FROM g)) = 2 ORDER BY 1",
                "false ;; true"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE), (8, FALSE)) fz(id, b) GROUP BY b HAVING "
                + "SUM(DISTINCT (SELECT COUNT(fz.id) FROM g)) = 2 ORDER BY 1",
                "false ;; true"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE), (8, FALSE)) fz(id, b) GROUP BY b HAVING "
                + "COUNT(*) = 2 ORDER BY 1",
                "false"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE), (8, FALSE)) fz(id, b) GROUP BY b HAVING "
                + "SUM((SELECT COUNT(fz.id) + 1 FROM g)) = 9 ORDER BY 1",
                ""},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE), (8, FALSE)) fz(id, b) GROUP BY b HAVING AVG( "
                + "(SELECT COUNT(fz.id) FROM g)) = 2 ORDER BY 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 90"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE), (8, FALSE)) fz(id, b) GROUP BY b HAVING AVG((  "
                + "SELECT COUNT(fz.id) FROM g)) = 2 ORDER BY 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 89"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE), (8, FALSE)) fz(id, b) GROUP BY b HAVING "
                + "AVG((SELECT MAX(fz.id) FROM g)) = 2 ORDER BY 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 89"},
            {"SELECT b FROM t GROUP BY b HAVING AVG((SELECT COUNT(t.id) FROM g)) = 2 ORDER BY 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 38"},
            {"SELECT b FROM t GROUP BY b HAVING SUM((SELECT COUNT(t.id) FROM g)) = 6 ORDER BY 1",
                ""},
            {"SELECT b FROM t GROUP BY b HAVING MAX((SELECT COUNT(t.id) FROM g)) = 2 ORDER BY 1",
                "false ;; true"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE)) fz(id, b) GROUP BY b HAVING AVG((SELECT MAX(v) "
                + "FROM g)) = 7 ORDER BY 1",
                "false ;; true"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE)) fz(id, b) GROUP BY b HAVING AVG((SELECT "
                + "COUNT(*) + fz.id FROM g)) = 7 ORDER BY 1",
                "true"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE)) fz(id, b) GROUP BY b HAVING AVG((SELECT "
                + "COUNT(fz.b) FROM g)) = 2 ORDER BY 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 77"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE)) fz(id, b) GROUP BY b HAVING AVG((SELECT "
                + "MAX(fz.id) FROM g)) + 1 = 7 ORDER BY 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 77"},
            {"SELECT column2 FROM VALUES (5, TRUE), (7, FALSE) GROUP BY column2 HAVING AVG((SELECT "
                + "MAX(column1) FROM g)) = 7 ORDER BY 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 77"},
            {"SELECT b FROM (SELECT column1 AS id, column2 AS b FROM VALUES (5, TRUE), (7, FALSE)) fz "
                + "GROUP BY b HAVING AVG((SELECT MAX(fz.id) FROM g)) = 7 ORDER BY 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 110"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE)) fz(id, b) GROUP BY b HAVING AVG(( SELECT "
                + "MAX(fz.id) FROM g)) = 7 ORDER BY 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 77"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE)) fz(id, b) GROUP BY b HAVING AVG((SELECT "
                + "MAX(fz.id) FROM g) + 1) = 7 ORDER BY 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 78"},
            {"SELECT b, MAX((SELECT COUNT(*) FROM g WHERE g.v = fz.id)) FROM (VALUES (5, TRUE), (7, "
                + "FALSE)) fz(id, b) GROUP BY b ORDER BY 1",
                "false|1 ;; true|1"},
            {"SELECT b, MAX((SELECT COUNT(v) FROM g)) FROM (VALUES (5, TRUE), (7, FALSE)) fz(id, b) GROUP "
                + "BY b ORDER BY 1",
                "false|2 ;; true|2"},
        });
    }

    @Test
    public void rowValues() {
        engine.execute("CREATE OR REPLACE TABLE g (v INT)");
        engine.execute("INSERT INTO g VALUES (5), (7)");
        engine.execute("CREATE OR REPLACE TABLE t (id INT NOT NULL, b BOOLEAN)");
        engine.execute("INSERT INTO t VALUES (5, TRUE), (7, FALSE), (8, FALSE)");
        assertCells(new String[][] {
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE)) fz(id, b) GROUP BY b HAVING (SELECT COUNT(*) + "
                + "fz.id FROM g) > 0",
                "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE)) fz(id, b) GROUP BY b HAVING MAX((SELECT "
                + "COUNT(*) FROM g WHERE g.v = fz.id)) = 1",
                "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE)) fz(id, b) GROUP BY b HAVING MAX((SELECT "
                + "COUNT(*) + fz.id FROM g WHERE g.v > 5)) = 6",
                "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE)) fz(id, b) GROUP BY b HAVING MAX((SELECT CASE "
                + "WHEN COUNT(*) > 1 THEN fz.id END FROM g)) = 5",
                "true"},
            {"SELECT b FROM (SELECT column1 AS id, column2 AS b FROM VALUES (5, TRUE), (7, FALSE)) fz "
                + "GROUP BY b HAVING MAX((SELECT COUNT(*) + fz.id FROM g)) = 7",
                "true"},
            {"SELECT b, MAX((SELECT COUNT(*) + fz.id FROM g)) FROM (VALUES (5, TRUE), (7, FALSE)) fz(id, "
                + "b) GROUP BY b ORDER BY 1",
                "false|9 ;; true|7"},
            {"SELECT b, MAX((SELECT COUNT(*) + t.id FROM g)) FROM t GROUP BY b ORDER BY 1",
                "false|10 ;; true|7"},
            {"SELECT b FROM t GROUP BY b HAVING MIN((SELECT COUNT(*) + t.id FROM g)) = 9 ORDER BY 1",
                "false"},
            {"SELECT b FROM t GROUP BY b HAVING SUM((SELECT COUNT(*) + t.id FROM g)) = 19 ORDER BY 1",
                "false"},
            {"SELECT b FROM t GROUP BY b HAVING COUNT((SELECT COUNT(*) + t.id FROM g)) = 2 ORDER BY 1",
                "false"},
            {"SELECT b FROM t GROUP BY b HAVING AVG((SELECT COUNT(*) + t.id FROM g)) = 9.5 ORDER BY 1",
                "false"},
            {"SELECT b FROM t GROUP BY b HAVING MAX((SELECT COUNT(*) * t.id FROM g)) = 16 ORDER BY 1",
                "false"},
            {"SELECT b FROM t GROUP BY b HAVING MAX((SELECT COUNT(*) + t.id + t.id FROM g)) = 18 ORDER BY "
                + "1",
                "false"},
            {"SELECT b FROM t GROUP BY b HAVING MAX((SELECT t.id FROM g WHERE g.v = 5)) = 8 ORDER BY 1",
                "REFUSED SQL compilation error: | [T.ID] is not a valid group by expression"},
            {"SELECT b FROM t GROUP BY b HAVING MAX((SELECT COUNT(*) + t.id FROM g)) + 1 = 11 ORDER BY 1",
                "false"},
            {"SELECT b FROM t GROUP BY b HAVING MAX((SELECT COUNT(*) + t.id FROM g WHERE g.v > 100)) = 8 "
                + "ORDER BY 1",
                "REFUSED SQL compilation error: | [T.ID] is not a valid group by expression"},
            {"SELECT b FROM t GROUP BY b HAVING MAX((SELECT MAX(g.v) - t.id FROM g)) = 2 ORDER BY 1",
                "true"},
            {"SELECT b FROM t GROUP BY b HAVING MAX((SELECT COUNT(*) FROM g) + t.id) = 10 ORDER BY 1",
                "false"},
            {"SELECT b FROM (SELECT f.value:id::INT AS id, f.value:b::BOOLEAN AS b FROM "
                + "TABLE(FLATTEN(PARSE_JSON('[{\"id\":5,\"b\":true}]'))) f) fz GROUP BY b HAVING MAX((SELECT "
                + "COUNT(*) + fz.id FROM g)) = 7",
                "true"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE)) fz(id, b) GROUP BY b HAVING MAX((SELECT "
                + "COUNT(*) + fz.id FROM g)) = 7",
                "true"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE)) fz(id, b) GROUP BY b HAVING MAX((SELECT "
                + "COUNT(*) + fz.id FROM g)) = 9",
                "false"},
            {"SELECT b FROM t GROUP BY b HAVING MAX((SELECT COUNT(*) + t.id FROM g)) = 7 ORDER BY 1",
                "true"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE), (8, FALSE)) fz(id, b) GROUP BY b HAVING "
                + "SUM((SELECT COUNT(*) + fz.id FROM g)) = 19 ORDER BY 1",
                "false"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE)) fz(id, b) GROUP BY b HAVING MAX((SELECT fz.id +"
                + " COUNT(*) FROM g)) = 7",
                "true"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE)) fz(id, b) GROUP BY b HAVING MAX((SELECT "
                + "MAX(g.v) + fz.id FROM g)) = 12",
                "true"},
        });
    }

    @Test
    public void listCells() {
        engine.execute("CREATE OR REPLACE TABLE g (v INT)");
        engine.execute("INSERT INTO g VALUES (5), (7)");
        engine.execute("CREATE OR REPLACE TABLE t (id INT NOT NULL, b BOOLEAN)");
        engine.execute("INSERT INTO t VALUES (5, TRUE), (7, FALSE), (8, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE u (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO u VALUES (5, TRUE), (7, FALSE), (8, FALSE)");
        assertCells(new String[][] {
            {"SELECT b FROM (VALUES (5 + 1, TRUE), (7, FALSE)) fz(id, b) GROUP BY b HAVING MAX((SELECT "
                + "COUNT(fz.id) FROM g)) = 2 ORDER BY 1",
                "false ;; true"},
            {"SELECT b FROM (VALUES (5 * 2, TRUE), (7, FALSE)) fz(id, b) GROUP BY b HAVING MAX((SELECT "
                + "COUNT(fz.id) FROM g)) = 2 ORDER BY 1",
                "false ;; true"},
            {"SELECT b FROM (VALUES (NULL + 1, TRUE), (7, FALSE)) fz(id, b) GROUP BY b HAVING MAX((SELECT "
                + "COUNT(fz.id) FROM g)) = 2 ORDER BY 1",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [COUNT(FZ.ID)] "
                + "nested in [MAX(CORRELATION(COUNT(FZ.ID)))]"},
            {"SELECT b FROM (VALUES (ABS(5), TRUE), (7, FALSE)) fz(id, b) GROUP BY b HAVING MAX((SELECT "
                + "COUNT(fz.id) FROM g)) = 2 ORDER BY 1",
                "false ;; true"},
            {"SELECT b FROM (VALUES (5::INT, TRUE), (7, FALSE)) fz(id, b) GROUP BY b HAVING MAX((SELECT "
                + "COUNT(fz.id) FROM g)) = 2 ORDER BY 1",
                "false ;; true"},
            {"SELECT b FROM (VALUES ('a' || 'b', TRUE), ('c', FALSE)) fz(id, b) GROUP BY b HAVING "
                + "MAX((SELECT COUNT(fz.id) FROM g)) = 2 ORDER BY 1",
                "false ;; true"},
            {"SELECT b FROM (VALUES ((5), TRUE), (7, FALSE)) fz(id, b) GROUP BY b HAVING MAX((SELECT "
                + "COUNT(fz.id) FROM g)) = 2 ORDER BY 1",
                "false ;; true"},
            {"SELECT b FROM (VALUES (5 / 0, TRUE), (7, FALSE)) fz(id, b) GROUP BY b HAVING MAX((SELECT "
                + "COUNT(fz.id) FROM g)) = 2 ORDER BY 1",
                "REFUSED SQL compilation error: | Invalid expression [5 / 0] in VALUES clause"},
            {"SELECT b FROM (VALUES (NULLIF(5, 5), TRUE), (7, FALSE)) fz(id, b) GROUP BY b HAVING "
                + "MAX((SELECT COUNT(fz.id) FROM g)) = 2 ORDER BY 1",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [COUNT(FZ.ID)] "
                + "nested in [MAX(CORRELATION(COUNT(FZ.ID)))]"},
            {"SELECT b FROM (VALUES (5 + 1, TRUE), (7 - 1, FALSE)) fz(id, b) GROUP BY b HAVING MIN((SELECT"
                + " COUNT(fz.id) FROM g)) = 2 ORDER BY 1",
                "false ;; true"},
            {"SELECT column2 FROM VALUES (5 + 1, TRUE), (7, FALSE) GROUP BY column2 HAVING MAX((SELECT "
                + "COUNT(column1) FROM g)) = 2 ORDER BY 1",
                "false ;; true"},
            {"SELECT b FROM (SELECT column1 + 1 AS id, column2 AS b FROM VALUES (5, TRUE), (7, FALSE)) fz "
                + "GROUP BY b HAVING MAX((SELECT MAX(fz.id) FROM g)) = 7",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(VALUES.COLUMN1)] nested in [MAX(CORRELATION((MAX(VALUES.COLUMN1)) + 1))]"},
            {"SELECT b FROM (SELECT 1 + column1 AS id, column2 AS b FROM VALUES (5, TRUE), (7, FALSE)) fz "
                + "GROUP BY b HAVING MAX((SELECT MAX(fz.id) FROM g)) = 7",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(VALUES.COLUMN1)] nested in [MAX(CORRELATION(1 + (MAX(VALUES.COLUMN1))))]"},
            {"SELECT b FROM (SELECT column1 - 1 AS id, column2 AS b FROM VALUES (5, TRUE), (7, FALSE)) fz "
                + "GROUP BY b HAVING MAX((SELECT MAX(fz.id) FROM g)) = 7",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(VALUES.COLUMN1)] nested in [MAX(CORRELATION((MAX(VALUES.COLUMN1)) - 1))]"},
            {"SELECT b FROM (SELECT column1 * 2 AS id, column2 AS b FROM VALUES (5, TRUE), (7, FALSE)) fz "
                + "GROUP BY b HAVING MAX((SELECT MAX(fz.id) FROM g)) = 7",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(VALUES.COLUMN1 "
                + "* 2)] nested in [MAX(CORRELATION(MAX(VALUES.COLUMN1 * 2)))]"},
            {"SELECT b FROM (SELECT -column1 AS id, column2 AS b FROM VALUES (5, TRUE), (7, FALSE)) fz "
                + "GROUP BY b HAVING MAX((SELECT MAX(fz.id) FROM g)) = 7",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(NEGATE(VALUES.COLUMN1))] nested in [MAX(CORRELATION(MAX(NEGATE(VALUES.COLUMN1))))]"},
            {"SELECT b FROM (SELECT column1 + column1 AS id, column2 AS b FROM VALUES (5, TRUE), (7, "
                + "FALSE)) fz GROUP BY b HAVING MAX((SELECT MAX(fz.id) FROM g)) = 7",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(VALUES.COLUMN1 "
                + "+ VALUES.COLUMN1)] nested in [MAX(CORRELATION(MAX(VALUES.COLUMN1 + VALUES.COLUMN1)))]"},
            {"SELECT b FROM (SELECT column1 + 1 AS id, column2 AS b FROM VALUES (5, TRUE), (7, FALSE)) fz "
                + "GROUP BY b HAVING SUM((SELECT MAX(fz.id) FROM g)) = 7",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(VALUES.COLUMN1)] nested in [SUM(CORRELATION(CAST((MAX(VALUES.COLUMN1)) + 1 AS "
                + "NUMBER(2,0))))]"},
            {"SELECT b FROM (SELECT column1 + 1 AS id, column2 AS b FROM VALUES (5, TRUE), (7, FALSE)) fz "
                + "GROUP BY b HAVING MIN((SELECT MIN(fz.id) FROM g)) = 7",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MIN(VALUES.COLUMN1)] nested in [MIN(CORRELATION((MIN(VALUES.COLUMN1)) + 1))]"},
            {"SELECT b FROM (SELECT column1 + 1 AS id, column2 AS b FROM VALUES (5, TRUE), (7, FALSE)) fz "
                + "GROUP BY b HAVING MAX((SELECT COUNT(fz.id) FROM g)) = 2",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[COUNT(VALUES.COLUMN1 + 1)] nested in [MAX(CORRELATION(COUNT(VALUES.COLUMN1 + 1)))]"},
            {"SELECT b FROM (SELECT column1 + 1 AS id, column2 AS b FROM VALUES (5, TRUE), (7, FALSE)) fz "
                + "GROUP BY b HAVING MAX((SELECT MAX(fz.id) FROM g)) + 1 = 7",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(VALUES.COLUMN1)] nested in [MAX(CORRELATION((MAX(VALUES.COLUMN1)) + 1))]"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE)) fz(id, b) GROUP BY b HAVING MAX((SELECT "
                + "COUNT(*) + fz.id FROM g)) = 7",
                "true"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE)) fz(id, b) GROUP BY b HAVING MAX((SELECT "
                + "COUNT(*) + fz.id FROM g)) = 9",
                "false"},
            {"SELECT b FROM t GROUP BY b HAVING MAX((SELECT COUNT(*) + t.id FROM g)) = 7 ORDER BY 1",
                "true"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE), (8, FALSE)) fz(id, b) GROUP BY b HAVING "
                + "SUM((SELECT COUNT(*) + fz.id FROM g)) = 19 ORDER BY 1",
                "false"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE)) fz(id, b) GROUP BY b HAVING MAX((SELECT fz.id +"
                + " COUNT(*) FROM g)) = 7",
                "true"},
            {"SELECT b FROM (VALUES (5, TRUE), (7, FALSE)) fz(id, b) GROUP BY b HAVING MAX((SELECT "
                + "MAX(g.v) + fz.id FROM g)) = 12",
                "true"},
            {"SELECT b, MAX((SELECT COUNT(t.id) FROM g)) FROM t GROUP BY b ORDER BY 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 14"},
        });
    }

    /**
     * The group's own value of a SUM or a COUNT over a settled COUNT: the subquery counts g's two rows for each row
     * of the group. The account answers a value that ignores the group's rows (SUM 4 and COUNT 2 for both groups),
     * which is not followed.
     */
    @Test
    public void settledCountUnderSumOrCountTakesTheGroupsRows() {
        Assumptions.assumeFalse(isLiveSnowflake(), "the account ignores the group's rows here");
        engine.execute("CREATE OR REPLACE TABLE g (v INT)");
        engine.execute("INSERT INTO g VALUES (5), (7)");
        engine.execute("CREATE OR REPLACE TABLE t (id INT NOT NULL, b BOOLEAN)");
        engine.execute("INSERT INTO t VALUES (5, TRUE), (7, FALSE), (8, FALSE)");
        final String list = "SELECT b FROM (VALUES (5, TRUE), (7, FALSE), (8, FALSE)) fz(id, b) GROUP BY b HAVING ";
        assertCells(new String[][] {
            {list + "SUM((SELECT COUNT(fz.id) FROM g)) = 2 ORDER BY 1", "true"},
            {list + "SUM((SELECT COUNT(fz.id) FROM g)) = 4 ORDER BY 1", "false"},
            {list + "COUNT((SELECT COUNT(fz.id) FROM g)) = 1 ORDER BY 1", "true"},
            {list + "COUNT((SELECT COUNT(fz.id) FROM g)) = 2 ORDER BY 1", "false"},
            {list + "SUM((SELECT COUNT(fz.id) + 1 FROM g)) = 6 ORDER BY 1", "false"},
            {"SELECT b FROM t GROUP BY b HAVING SUM((SELECT COUNT(t.id) FROM g)) = 2 ORDER BY 1", "true"},
            {"SELECT b FROM t GROUP BY b HAVING SUM((SELECT COUNT(t.id) FROM g)) = 4 ORDER BY 1", "false"},
        });
    }
}
