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
 * Where the refusal of a subquery whose own subquery reads the row two levels out is placed: at the innermost query
 * under a WHERE's MIN, MAX or SUM subquery, at the subquery judged under a COUNT, an AVG or plain rows, with no place
 * for an IN or an EXISTS over plain rows and for one reading the outer row through a FROM-less IN or EXISTS. The same
 * holds for a subquery's own subqueries judged as its rows reach them. Every cell is live-verified.
 */
public class NestedSubqueryDepthTwoPlaceTest extends MeasuredCellsTestSupport {

    @Test
    public void threeLevelChainsArePlacedByTheJudgedAggregate() {
        engine.execute("CREATE OR REPLACE TABLE o (id INT, k INT, n NUMBER(10,2), f FLOAT, s VARCHAR, d DATE, ts "
            + "TIMESTAMP_NTZ, b BOOLEAN, v VARIANT, a ARRAY, ob OBJECT)");
        engine.execute("INSERT INTO o SELECT 1, 1, 1.50, 1.5, 'a', '2020-01-01'::DATE, '2020-01-01 "
            + "10:00:00'::TIMESTAMP_NTZ, TRUE, TO_VARIANT(1), ARRAY_CONSTRUCT(1,2), OBJECT_CONSTRUCT('k',1)"
            + " UNION ALL SELECT 2, 2, 2.00, 2.0, 'B', '2020-01-02'::DATE, '2020-01-02 "
            + "00:00:00'::TIMESTAMP_NTZ, FALSE, TO_VARIANT('x'), ARRAY_CONSTRUCT(2), "
            + "OBJECT_CONSTRUCT('k',2) UNION ALL SELECT 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, "
            + "NULL, NULL UNION ALL SELECT 4, 2, 2.00, 2.0, 'b', '2020-01-03'::DATE, '2020-01-03 "
            + "12:30:00'::TIMESTAMP_NTZ, TRUE, TO_VARIANT(2), ARRAY_CONSTRUCT(3,NULL), OBJECT_CONSTRUCT() "
            + "UNION ALL SELECT 5, 5, 5.25, 5.25, 'e', '2020-01-05'::DATE, '2020-01-05 "
            + "00:00:00'::TIMESTAMP_NTZ, NULL, PARSE_JSON('null'), ARRAY_CONSTRUCT(), "
            + "OBJECT_CONSTRUCT('z',NULL)");
        engine.execute("CREATE OR REPLACE TABLE i (k INT, n NUMBER(10,2), f FLOAT, s VARCHAR, d DATE, ts "
            + "TIMESTAMP_NTZ, b BOOLEAN, v VARIANT, val INT)");
        engine.execute("INSERT INTO i SELECT 1, 1.50, 1.5, 'A', '2020-01-01'::DATE, '2020-01-01 "
            + "10:00:00'::TIMESTAMP_NTZ, TRUE, TO_VARIANT(1), 10 UNION ALL SELECT 2, 2.00, 2.0, 'b', "
            + "'2020-01-02'::DATE, '2020-01-02 00:00:00'::TIMESTAMP_NTZ, FALSE, TO_VARIANT('x'), 20 UNION "
            + "ALL SELECT 2, 2.10, 2.1, 'b', '2020-01-04'::DATE, '2020-01-04 00:00:00'::TIMESTAMP_NTZ, "
            + "TRUE, TO_VARIANT(2), 21 UNION ALL SELECT NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 30 "
            + "UNION ALL SELECT 3, 3.00, 3.0, 'c', '2020-01-03'::DATE, '2020-01-03 "
            + "12:30:00'::TIMESTAMP_NTZ, NULL, TO_VARIANT(3), NULL");
        engine.execute("CREATE OR REPLACE TABLE e (k INT, n NUMBER(10,2), f FLOAT, s VARCHAR, d DATE, ts "
            + "TIMESTAMP_NTZ, b BOOLEAN, v VARIANT, val INT)");
        engine.execute("CREATE OR REPLACE TABLE one (k INT, val INT)");
        engine.execute("INSERT INTO one VALUES (2, 7)");
        engine.execute("CREATE OR REPLACE TABLE nn (k INT)");
        engine.execute("INSERT INTO nn VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE TABLE wn (k INT)");
        engine.execute("INSERT INTO wn VALUES (1), (NULL)");
        assertCells(new String[][] {
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(k) FROM one WHERE one.k = (SELECT"
                + " MAX(k) FROM nn WHERE nn.k = i.k))) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 86"},
            {"SELECT k, (SELECT MAX(k) FROM one WHERE one.k = (SELECT MAX(k) FROM nn WHERE nn.k = i.k)) "
                + "FROM i",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 11"},
            {"SELECT k FROM i WHERE i.k = (SELECT MAX(k) FROM one WHERE one.k = (SELECT MAX(k) FROM nn "
                + "WHERE nn.k = i.k))",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 67"},
            {"SELECT k FROM i WHERE i.k = (SELECT COUNT(*) FROM one WHERE one.k = (SELECT MAX(k) FROM nn "
                + "WHERE nn.k = i.k))",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT k FROM i WHERE i.k = (SELECT MAX(k) FROM one WHERE one.k = (SELECT k FROM nn WHERE "
                + "nn.k = i.k))",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 67"},
            {"SELECT k FROM i WHERE i.k = (SELECT k FROM one WHERE one.k = (SELECT MAX(k) FROM nn WHERE "
                + "nn.k = i.k))",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT k, (SELECT COUNT(*) FROM one WHERE one.k = (SELECT MAX(k) FROM nn WHERE nn.k = i.k)) "
                + "FROM i",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 11"},
            {"SELECT k FROM i WHERE i.k = (SELECT MAX(k) FROM one WHERE one.k = (SELECT COUNT(*) FROM nn "
                + "WHERE nn.k = i.k))",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 67"},
            {"SELECT k FROM i WHERE i.k > (SELECT MAX(k) FROM one WHERE one.k = (SELECT MAX(k) FROM nn "
                + "WHERE nn.k = i.k))",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 67"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(k) FROM one WHERE one.k = (SELECT"
                + " k FROM nn WHERE nn.k = i.k))) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 86"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT k FROM one WHERE one.k = (SELECT "
                + "MAX(k) FROM nn WHERE nn.k = i.k))) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 48"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT COUNT(*) FROM one WHERE one.k = "
                + "(SELECT MAX(k) FROM nn WHERE nn.k = i.k))) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 48"},
            {"SELECT id, (SELECT MAX(k) FROM i WHERE i.k = (SELECT MAX(k) FROM one WHERE one.k = (SELECT "
                + "MAX(k) FROM nn WHERE nn.k = i.k))) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 84"},
            {"SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.k = (SELECT MAX(k) FROM one WHERE "
                + "one.k = (SELECT MAX(k) FROM nn WHERE nn.k = i.k))) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 98"},
            {"SELECT id FROM o WHERE o.k = (SELECT MAX(k) FROM i WHERE i.k = (SELECT MAX(k) FROM one WHERE"
                + " one.k = (SELECT MAX(k) FROM nn WHERE nn.k = i.k))) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 102"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(k) FROM one WHERE one.k = (SELECT"
                + " MAX(k) FROM nn WHERE nn.k = i.k) AND one.k = o.k)) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(k) FROM one WHERE one.val > "
                + "(SELECT MAX(k) FROM nn WHERE nn.k = i.k))) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 88"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(k) FROM one WHERE one.k =\n "
                + "(SELECT MAX(k) FROM nn WHERE nn.k = i.k))) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 2, "
                + "position 2"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k IN (SELECT MAX(k) FROM one WHERE one.k = "
                + "(SELECT MAX(k) FROM nn WHERE nn.k = i.k))) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 87"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE EXISTS (SELECT MAX(k) FROM one WHERE one.k = "
                + "(SELECT MAX(k) FROM nn WHERE nn.k = i.k))) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 41"},
            {"SELECT id, (SELECT COUNT(*) FROM i, one WHERE i.k = (SELECT MAX(k) FROM nn WHERE nn.k = "
                + "(SELECT MAX(k) FROM wn WHERE wn.k = i.k))) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 89"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE EXISTS (SELECT 1 WHERE i.k = o.k)) FROM o ORDER BY "
                + "id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE EXISTS (SELECT 1 WHERE i.k = o.k)) "
                + "ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT id FROM o WHERE NOT EXISTS (SELECT 1 FROM i WHERE EXISTS (SELECT 1 WHERE i.k = o.k)) "
                + "ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE NOT EXISTS (SELECT 1 WHERE i.k = o.k)) "
                + "ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT id FROM o WHERE o.k IN (SELECT k FROM i WHERE EXISTS (SELECT 1 WHERE i.k = o.k)) "
                + "ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT id FROM o WHERE (SELECT COUNT(*) FROM i WHERE EXISTS (SELECT 1 WHERE i.k = o.k)) > 0 "
                + "ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 24"},
            {"SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE EXISTS (SELECT 1 WHERE i.k = o.k) AND "
                + "i.k > 0) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT id FROM o WHERE id > 0 AND EXISTS (SELECT 1 FROM i WHERE EXISTS (SELECT 1 WHERE i.k ="
                + " o.k)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.k IN (SELECT 1 WHERE i.k = o.k)) "
                + "ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE EXISTS (SELECT 1 WHERE i.k = o.k)) AND "
                + "id > (SELECT 0) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE EXISTS (SELECT 1 FROM one WHERE i.k = "
                + "o.k)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE EXISTS (SELECT 1 WHERE i.k = o.k LIMIT "
                + "1)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.k = (SELECT 1 WHERE i.k = o.k)) ORDER"
                + " BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 60"},
            {"SELECT id FROM o WHERE o.k IN (SELECT k FROM i WHERE i.k IN (SELECT 1 WHERE i.k = o.k)) "
                + "ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.k = ANY (SELECT 1 WHERE i.k = o.k)) "
                + "ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i JOIN nn ON EXISTS (SELECT 1 WHERE i.k = o.k))"
                + " ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE EXISTS (SELECT 1 WHERE EXISTS (SELECT 1"
                + " WHERE i.k = o.k))) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.k NOT IN (SELECT 1 WHERE i.k = o.k)) "
                + "ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT id FROM o WHERE o.k IN (SELECT k FROM i WHERE EXISTS (SELECT 1 WHERE i.k = o.k) OR "
                + "i.k = 1) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE EXISTS (SELECT 1 WHERE i.k = o.k) AND "
                + "i.k = o.k) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE EXISTS (SELECT 1 WHERE i.k = o.k)) AND "
                + "EXISTS (SELECT 1 FROM i WHERE i.k = o.k) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE EXISTS (SELECT 1 WHERE i.k = o.k) AND "
                + "i.k > (SELECT MAX(k) FROM nn WHERE nn.k = o.k)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i GROUP BY i.k HAVING EXISTS (SELECT 1 WHERE "
                + "i.k = o.k)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT id FROM o WHERE NOT EXISTS (SELECT 1 FROM i WHERE i.k IN (SELECT 1 WHERE i.k = o.k)) "
                + "ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT k FROM i WHERE i.k = (SELECT SUM(k) FROM one WHERE one.k = (SELECT MAX(k) FROM nn "
                + "WHERE nn.k = i.k))",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 67"},
            {"SELECT k FROM i WHERE i.k = (SELECT MIN(k) FROM one WHERE one.k = (SELECT MAX(k) FROM nn "
                + "WHERE nn.k = i.k))",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 67"},
            {"SELECT k FROM i WHERE i.k = (SELECT COUNT(k) FROM one WHERE one.k = (SELECT MAX(k) FROM nn "
                + "WHERE nn.k = i.k))",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT k FROM i WHERE i.k = (SELECT AVG(k) FROM one WHERE one.k = (SELECT MAX(k) FROM nn "
                + "WHERE nn.k = i.k))",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT k FROM i WHERE i.k = (SELECT MAX(k) + 0 FROM one WHERE one.k = (SELECT MAX(k) FROM nn"
                + " WHERE nn.k = i.k))",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 71"},
            {"SELECT k FROM i WHERE i.k = (SELECT ANY_VALUE(k) FROM one WHERE one.k = (SELECT MAX(k) FROM "
                + "nn WHERE nn.k = i.k))",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 73"},
            {"SELECT k FROM i WHERE i.k = (SELECT MAX(k) FROM one WHERE one.k = (SELECT MAX(k) FROM nn "
                + "WHERE nn.k = i.k) GROUP BY val)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT k FROM i WHERE i.k = (SELECT COUNT(*) + 0 FROM one WHERE one.k = (SELECT MAX(k) FROM "
                + "nn WHERE nn.k = i.k))",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT k FROM i WHERE i.k = (SELECT MAX(k) FROM one WHERE one.k = (SELECT SUM(k) FROM nn "
                + "WHERE nn.k = i.k))",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 67"},
            {"SELECT i.k FROM i JOIN nn ON nn.k = (SELECT MAX(k) FROM one WHERE one.k = (SELECT MAX(k) "
                + "FROM wn WHERE wn.k = i.k))",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 37"},
            {"SELECT k FROM i WHERE i.k IN (SELECT MAX(k) FROM one WHERE one.k = (SELECT MAX(k) FROM nn "
                + "WHERE nn.k = i.k))",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 68"},
            {"SELECT k FROM i WHERE i.k NOT IN (SELECT MAX(k) FROM one WHERE one.k = (SELECT MAX(k) FROM "
                + "nn WHERE nn.k = i.k))",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 34"},
            {"SELECT k FROM i WHERE EXISTS (SELECT MAX(k) FROM one WHERE one.k = (SELECT MAX(k) FROM nn "
                + "WHERE nn.k = i.k))",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 22"},
            {"SELECT k FROM i WHERE i.k = (SELECT MAX(k) FROM one WHERE one.k > 0 AND one.k = (SELECT "
                + "MAX(k) FROM nn WHERE nn.k = i.k))",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 81"},
            {"SELECT k FROM i WHERE i.k = (SELECT MAX((SELECT MAX(k) FROM nn WHERE nn.k = i.k)) FROM one)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT k FROM i WHERE i.k = (SELECT MAX(k) FROM one WHERE one.k = (SELECT MAX(k) FROM nn "
                + "WHERE nn.k = i.k)) AND k > (SELECT 0)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 67"},
            {"SELECT k FROM i WHERE i.k = (SELECT MAX(k) FROM one WHERE one.k IN (SELECT MAX(k) FROM nn "
                + "WHERE nn.k = i.k))",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 68"},
            {"SELECT k FROM i WHERE i.k = (SELECT MAX(k) FROM one WHERE EXISTS (SELECT MAX(k) FROM nn "
                + "WHERE nn.k = i.k))",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 58"},
            {"SELECT k FROM i WHERE i.k = (SELECT MAX(k) FROM one WHERE one.k = (SELECT k FROM nn WHERE "
                + "nn.k = i.k LIMIT 1))",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 67"},
            {"SELECT id, (SELECT (SELECT MAX(k) FROM one WHERE one.k = (SELECT MAX(k) FROM nn WHERE nn.k ="
                + " i.k)) FROM i LIMIT 1) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 20"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT SUM(k) FROM one WHERE one.k = (SELECT"
                + " MAX(k) FROM nn WHERE nn.k = i.k))) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 86"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT COUNT(k) FROM one WHERE one.k = "
                + "(SELECT MAX(k) FROM nn WHERE nn.k = i.k))) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 48"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(k) FROM one WHERE one.k = (SELECT"
                + " MAX(k) FROM nn WHERE nn.k = i.k)) AND i.k > (SELECT 0)) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 86"},
            {"SELECT id, (SELECT COUNT(*) FROM i JOIN nn ON nn.k = (SELECT MAX(k) FROM one WHERE one.k = "
                + "(SELECT MAX(k) FROM wn WHERE wn.k = i.k))) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 54"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(k) FROM one WHERE one.k = (SELECT"
                + " k FROM nn WHERE nn.k = i.k LIMIT 1))) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 86"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT k FROM one WHERE one.k = (SELECT k "
                + "FROM nn WHERE nn.k = i.k))) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 48"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(k) FROM one WHERE one.k = (SELECT"
                + " MAX(k) FROM nn WHERE nn.k = i.k) AND one.k = (SELECT MAX(k) FROM nn WHERE nn.k = i.k))) "
                + "FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 139"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(k) FROM one WHERE one.k = (SELECT"
                + " MAX(k) FROM nn WHERE nn.k = i.k)) GROUP BY i.k LIMIT 1) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 86"},
            {"SELECT id FROM o WHERE id = 1 OR EXISTS (SELECT 1 FROM i WHERE EXISTS (SELECT 1 WHERE i.k = "
                + "o.k)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 33"},
        });
    }

    @Test
    public void plainRowsAndExpressionQueriesKeepTheirPlace() {
        engine.execute("CREATE OR REPLACE TABLE o (id INT, k INT, n NUMBER(10,2), f FLOAT, s VARCHAR, d DATE, ts "
            + "TIMESTAMP_NTZ, b BOOLEAN, v VARIANT, a ARRAY, ob OBJECT)");
        engine.execute("INSERT INTO o SELECT 1, 1, 1.50, 1.5, 'a', '2020-01-01'::DATE, '2020-01-01 "
            + "10:00:00'::TIMESTAMP_NTZ, TRUE, TO_VARIANT(1), ARRAY_CONSTRUCT(1,2), OBJECT_CONSTRUCT('k',1)"
            + " UNION ALL SELECT 2, 2, 2.00, 2.0, 'B', '2020-01-02'::DATE, '2020-01-02 "
            + "00:00:00'::TIMESTAMP_NTZ, FALSE, TO_VARIANT('x'), ARRAY_CONSTRUCT(2), "
            + "OBJECT_CONSTRUCT('k',2) UNION ALL SELECT 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, "
            + "NULL, NULL UNION ALL SELECT 4, 2, 2.00, 2.0, 'b', '2020-01-03'::DATE, '2020-01-03 "
            + "12:30:00'::TIMESTAMP_NTZ, TRUE, TO_VARIANT(2), ARRAY_CONSTRUCT(3,NULL), OBJECT_CONSTRUCT() "
            + "UNION ALL SELECT 5, 5, 5.25, 5.25, 'e', '2020-01-05'::DATE, '2020-01-05 "
            + "00:00:00'::TIMESTAMP_NTZ, NULL, PARSE_JSON('null'), ARRAY_CONSTRUCT(), "
            + "OBJECT_CONSTRUCT('z',NULL)");
        engine.execute("CREATE OR REPLACE TABLE i (k INT, n NUMBER(10,2), f FLOAT, s VARCHAR, d DATE, ts "
            + "TIMESTAMP_NTZ, b BOOLEAN, v VARIANT, val INT)");
        engine.execute("INSERT INTO i SELECT 1, 1.50, 1.5, 'A', '2020-01-01'::DATE, '2020-01-01 "
            + "10:00:00'::TIMESTAMP_NTZ, TRUE, TO_VARIANT(1), 10 UNION ALL SELECT 2, 2.00, 2.0, 'b', "
            + "'2020-01-02'::DATE, '2020-01-02 00:00:00'::TIMESTAMP_NTZ, FALSE, TO_VARIANT('x'), 20 UNION "
            + "ALL SELECT 2, 2.10, 2.1, 'b', '2020-01-04'::DATE, '2020-01-04 00:00:00'::TIMESTAMP_NTZ, "
            + "TRUE, TO_VARIANT(2), 21 UNION ALL SELECT NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 30 "
            + "UNION ALL SELECT 3, 3.00, 3.0, 'c', '2020-01-03'::DATE, '2020-01-03 "
            + "12:30:00'::TIMESTAMP_NTZ, NULL, TO_VARIANT(3), NULL");
        engine.execute("CREATE OR REPLACE TABLE e (k INT, n NUMBER(10,2), f FLOAT, s VARCHAR, d DATE, ts "
            + "TIMESTAMP_NTZ, b BOOLEAN, v VARIANT, val INT)");
        engine.execute("CREATE OR REPLACE TABLE one (k INT, val INT)");
        engine.execute("INSERT INTO one VALUES (2, 7)");
        engine.execute("CREATE OR REPLACE TABLE nn (k INT)");
        engine.execute("INSERT INTO nn VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE TABLE wn (k INT)");
        engine.execute("INSERT INTO wn VALUES (1), (NULL)");
        assertCells(new String[][] {
            {"SELECT id, (SELECT k FROM i WHERE i.k = (SELECT k FROM one WHERE one.k = o.k)) FROM o ORDER "
                + "BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT k FROM i WHERE i.k = (SELECT k FROM one WHERE one.k = (SELECT k FROM nn WHERE nn.k = "
                + "i.k))",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id, (SELECT MAX(k) FROM i WHERE i.k = (SELECT k FROM one WHERE one.k = o.k)) FROM o "
                + "ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 46"},
            {"SELECT k FROM i WHERE i.k = (SELECT COUNT(*) FROM one WHERE one.k = (SELECT k FROM nn WHERE "
                + "nn.k = i.k))",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 69"},
            {"SELECT k FROM i WHERE EXISTS (SELECT MAX(k) FROM one WHERE one.k = (SELECT k FROM nn WHERE "
                + "nn.k = i.k))",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 22"},
            {"SELECT k FROM i WHERE i.k IN (SELECT SUM(k) FROM one WHERE one.k = (SELECT COUNT(*) FROM nn "
                + "WHERE nn.k = i.k))",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 68"},
            {"SELECT k FROM i WHERE i.k IN (SELECT k FROM one WHERE one.k = (SELECT MAX(k) FROM nn WHERE "
                + "nn.k = i.k))",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT k FROM i WHERE i.k = (SELECT MAX(one.k) FROM one JOIN nn ON nn.k = (SELECT MAX(k) "
                + "FROM wn WHERE wn.k = i.k))",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 75"},
            {"SELECT k FROM i WHERE i.k = 0 OR i.k = (SELECT MAX(k) FROM one WHERE one.k = (SELECT MAX(k) "
                + "FROM nn WHERE nn.k = i.k))",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 40"},
            {"SELECT k FROM i WHERE i.k = (SELECT MEDIAN(k) FROM one WHERE one.k = (SELECT MAX(k) FROM nn "
                + "WHERE nn.k = i.k))",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 70"},
            {"SELECT k FROM i WHERE i.k = (SELECT COUNT(DISTINCT k) FROM one WHERE one.k = (SELECT MAX(k) "
                + "FROM nn WHERE nn.k = i.k))",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT k FROM i WHERE i.k = (SELECT MAX(DISTINCT k) FROM one WHERE one.k = (SELECT MAX(k) "
                + "FROM nn WHERE nn.k = i.k))",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 76"},
            {"SELECT k FROM i WHERE i.k = (SELECT MAX(k) + COUNT(*) FROM one WHERE one.k = (SELECT MAX(k) "
                + "FROM nn WHERE nn.k = i.k))",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id, (SELECT k FROM i WHERE i.k = (SELECT MAX(k) FROM one WHERE one.k = o.k)) FROM o "
                + "ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT k FROM i WHERE i.k = (SELECT SUM(k) FROM one WHERE one.k = (SELECT k FROM nn WHERE "
                + "nn.k = i.k))",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 67"},
            {"SELECT k FROM i WHERE i.k = (SELECT MAX(k) FROM one WHERE one.k = (SELECT MAX(k) FROM nn "
                + "WHERE nn.k = i.k) HAVING MAX(k) > 0)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 67"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT k FROM one WHERE one.k = (SELECT k "
                + "FROM nn WHERE nn.k = i.k))) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 48"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k IN (SELECT k FROM one WHERE one.k = (SELECT "
                + "MAX(k) FROM nn WHERE nn.k = i.k))) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE EXISTS (SELECT 1 FROM one WHERE one.k = (SELECT k "
                + "FROM nn WHERE nn.k = i.k))) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 82"},
            {"SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE EXISTS (SELECT 1 WHERE i.k = o.k) OR "
                + "i.k = 1) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT id FROM o WHERE NOT (EXISTS (SELECT 1 FROM i WHERE EXISTS (SELECT 1 WHERE i.k = "
                + "o.k))) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
        });
    }
}
