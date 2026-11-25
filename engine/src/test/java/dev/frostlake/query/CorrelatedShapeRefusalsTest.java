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
 * Correlated shapes the account refuses as "Unsupported subquery type cannot be evaluated" beside their answered
 * neighbours: a membership in an outer join's ON reading both sides of the join (any correlated one under FULL), a
 * scalar aggregate with a HAVING over a filter that is no equality with the outer row, a scalar aggregate testing an
 * outer value for NULL, and a NOT IN of an UPDATE's or a DELETE's WHERE keyed by equalities alone over a query that
 * may answer NULL. Each cell is the live-verified answer; the cells where the account's answer is defective — an outer
 * join dropping its preserved rows, a HAVING-filtered COUNT answering 0 — are left out.
 */
public class CorrelatedShapeRefusalsTest extends MeasuredCellsTestSupport {

    @Test
    public void outerJoinMembershipsAndScalarAggregates() {
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
            {"SELECT o.id, i.val FROM o LEFT JOIN i ON i.k = o.k AND i.val IN (SELECT val FROM i i2 WHERE "
                + "i2.k = o.k AND i2.val > 20) ORDER BY o.id, i.val",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 65"},
            {"SELECT o.id, i.val FROM o JOIN i ON i.k = o.k AND i.val IN (SELECT val FROM i i2 WHERE i2.k "
                + "= o.k AND i2.val > 20) ORDER BY o.id, i.val",
                "2|21 ;; 4|21"},
            {"SELECT o.id, i.val FROM o LEFT JOIN i ON i.k = o.k AND i.val IN (SELECT val FROM i i2 WHERE "
                + "i2.val > 20) ORDER BY o.id, i.val",
                "1|null ;; 2|21 ;; 3|null ;; 4|21 ;; 5|null"},
            {"SELECT o.id, i.val FROM o LEFT JOIN i ON i.k = o.k AND i.val = (SELECT MAX(val) FROM i i2 "
                + "WHERE i2.k = o.k) ORDER BY o.id, i.val",
                "1|10 ;; 2|21 ;; 3|null ;; 4|21 ;; 5|null"},
            {"SELECT o.id, i.val FROM i RIGHT JOIN o ON i.k = o.k AND i.val IN (SELECT val FROM i i2 WHERE"
                + " i2.k = o.k AND i2.val > 20) ORDER BY o.id, i.val",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 66"},
            {"SELECT o.id, i.val FROM o FULL JOIN i ON i.k = o.k AND i.val IN (SELECT val FROM i i2 WHERE "
                + "i2.k = o.k AND i2.val > 20) ORDER BY o.id, i.val",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 65"},
            {"SELECT o.id, i.val FROM o LEFT JOIN i ON i.k = o.k AND i.val IN (SELECT val FROM i i2 WHERE "
                + "i2.k = i.k AND i2.val > 20) ORDER BY o.id, i.val",
                "1|null ;; 2|21 ;; 3|null ;; 4|21 ;; 5|null"},
            {"SELECT o.id, i.val FROM o RIGHT JOIN i ON i.k = o.k AND i.val IN (SELECT val FROM i i2 WHERE"
                + " i2.k = o.k AND i2.val > 20) ORDER BY o.id, i.val",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 66"},
            {"SELECT o.id, i.val FROM o FULL JOIN i ON i.k = o.k AND i.val = (SELECT MAX(val) FROM i i2 "
                + "WHERE i2.k = o.k) ORDER BY o.id, i.val",
                "1|10 ;; 2|21 ;; 3|null ;; 4|21 ;; 5|null ;; null|20 ;; null|30 ;; null|null"},
            {"SELECT o.id, i.val FROM o FULL JOIN i ON i.k = o.k AND EXISTS (SELECT 1 FROM i i2 WHERE i2.k"
                + " = o.k AND i2.val > 20) ORDER BY o.id, i.val",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 55"},
            {"SELECT o.id, i.val FROM o LEFT JOIN i ON i.k = o.k AND i.val > ANY (SELECT val FROM i i2 "
                + "WHERE i2.k = o.k) ORDER BY o.id, i.val",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 63"},
            {"SELECT o.id, i.val FROM o LEFT JOIN i ON i.k = o.k AND o.id IN (SELECT val FROM i i2 WHERE "
                + "i2.k = o.k) ORDER BY o.id, i.val",
                "1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null"},
            {"SELECT o.id, i.val FROM o LEFT JOIN i ON i.k = o.k AND i.val IN (SELECT val FROM i i2 WHERE "
                + "i2.k = o.k AND i2.val > 20) WHERE o.id > 0 ORDER BY o.id, i.val",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 65"},
            {"SELECT o.id, i.val FROM o LEFT JOIN i ON i.k = o.k AND i.val IN (SELECT val FROM i i2 WHERE "
                + "i2.val > o.id * 5) ORDER BY o.id, i.val",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 65"},
            {"SELECT o.id, i.val FROM o JOIN i ON i.k = o.k AND EXISTS (SELECT 1 FROM i i2 WHERE i2.k = "
                + "o.k AND i2.val > 20) ORDER BY o.id, i.val",
                "2|20 ;; 2|21 ;; 4|20 ;; 4|21"},
            {"SELECT o.id, i.val FROM o LEFT JOIN i ON i.k = o.k AND i.val IN (SELECT val FROM i i2 WHERE "
                + "i2.k = i.k + o.k - o.k) ORDER BY o.id, i.val",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 65"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k < o.k HAVING COUNT(*) > 1) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k < o.k) FROM o ORDER BY id",
                "1|0 ;; 2|1 ;; 3|0 ;; 4|1 ;; 5|4"},
            {"SELECT id, (SELECT MAX(val) FROM i WHERE i.k < o.k HAVING COUNT(*) > 1) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT SUM(val) FROM i WHERE i.k < o.k HAVING SUM(val) > 1) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT COUNT(val) FROM i WHERE i.k < o.k HAVING COUNT(val) > 1) FROM o ORDER BY "
                + "id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id FROM o WHERE (SELECT COUNT(*) FROM i WHERE i.k < o.k HAVING COUNT(*) > 1) > 0 "
                + "ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 24"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = o.k AND i.val < o.id * 10 HAVING COUNT(*) > "
                + "0) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k <> o.k HAVING COUNT(*) > 1) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k BETWEEN o.k AND o.k + 1 HAVING COUNT(*) > 1) "
                + "FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k < o.k HAVING MAX(val) > 1) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT MAX(val) FROM i WHERE i.k < o.k HAVING MAX(val) > 1) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT COUNT(*) + 1 FROM i WHERE i.k < o.k HAVING COUNT(*) > 1) FROM o ORDER BY "
                + "id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE o.k IS NULL) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE o.k = 2) FROM o ORDER BY id",
                "1|0 ;; 2|5 ;; 3|0 ;; 4|5 ;; 5|0"},
            {"SELECT id, (SELECT MAX(val) FROM i WHERE o.k = 2) FROM o ORDER BY id",
                "1|null ;; 2|30 ;; 3|null ;; 4|30 ;; 5|null"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE o.k IS NOT NULL) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT MAX(val) FROM i WHERE o.k IS NULL) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE o.k BETWEEN 1 AND 2) FROM o ORDER BY id",
                "1|5 ;; 2|5 ;; 3|0 ;; 4|5 ;; 5|0"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE o.k IN (1, 2)) FROM o ORDER BY id",
                "1|5 ;; 2|5 ;; 3|0 ;; 4|5 ;; 5|0"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE o.s LIKE 'b%') FROM o ORDER BY id",
                "1|0 ;; 2|0 ;; 3|0 ;; 4|5 ;; 5|0"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.val > 15 AND o.k IS NULL) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = o.k OR o.k IS NULL) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE o.k > 1) FROM o ORDER BY id",
                "1|0 ;; 2|5 ;; 3|0 ;; 4|5 ;; 5|5"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE COALESCE(o.k, 0) = 0) FROM o ORDER BY id",
                "1|0 ;; 2|0 ;; 3|5 ;; 4|0 ;; 5|0"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE o.k = 2 OR o.k = 1) FROM o ORDER BY id",
                "1|5 ;; 2|5 ;; 3|0 ;; 4|5 ;; 5|0"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE o.k <> 2) FROM o ORDER BY id",
                "1|5 ;; 2|0 ;; 3|0 ;; 4|0 ;; 5|5"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE NOT (o.k = 2)) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE o.b) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE o.k IS NULL AND i.k = o.id) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE o.k IS NULL) ORDER BY id",
                "3"},
            {"SELECT id FROM o WHERE (SELECT COUNT(*) FROM i WHERE o.k IS NULL) > 0 ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 24"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE o.k = o.id) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT SUM(val) FROM i WHERE o.k IS NULL) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE o.k IS DISTINCT FROM 2) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.val IS NULL) FROM o ORDER BY id",
                "1|1 ;; 2|1 ;; 3|1 ;; 4|1 ;; 5|1"},
        });
    }

    @Test
    public void dmlAntiJoins() {
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
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
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
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t2 (id INT, k INT)");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        assertCells(new String[][] {
            {"UPDATE t SET k = -1 WHERE t.id NOT IN (SELECT val FROM i WHERE i.k = t.k)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 39"},
            {"UPDATE t SET k = -1 WHERE t.k NOT IN (SELECT k FROM i WHERE i.s = t.s)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 38"},
            {"DELETE FROM t WHERE t.id NOT IN (SELECT val FROM i WHERE i.k = t.k)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 33"},
            {"UPDATE t SET k = -1 WHERE t.id <> ALL (SELECT val FROM i WHERE i.k = t.k)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 34"},
            {"DELETE FROM t WHERE t.id <> ALL (SELECT val FROM i WHERE i.k = t.k)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 28"},
            {"MERGE INTO t USING o ON t.id = o.id WHEN MATCHED AND t.id NOT IN (SELECT val FROM i) THEN "
                + "UPDATE SET k = -1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 66"},
            {"UPDATE t SET k = -1 WHERE t.id NOT IN (SELECT val FROM i WHERE i.k = t.k) OR t.id = 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 39"},
            {"UPDATE t SET k = -1 FROM one WHERE t.k = one.k AND t.id NOT IN (SELECT val FROM i WHERE i.k "
                + "= t.k)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 64"},
            {"UPDATE t SET k = -1 WHERE t.id + 0 NOT IN (SELECT val FROM i WHERE i.k = t.k)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 43"},
            {"DELETE FROM t USING one WHERE t.k = one.k AND t.id NOT IN (SELECT val FROM i WHERE i.k = "
                + "t.k)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 59"},
            {"UPDATE t SET k = -1 WHERE t.id NOT IN (SELECT val FROM i WHERE i.k = t.k AND i.val IS NOT "
                + "NULL)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 39"},
            {"UPDATE t SET k = -1 WHERE t.id NOT IN (SELECT val FROM i WHERE i.k = t.k AND i.val IS NOT "
                + "NULL) AND t.id IS NOT NULL",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 39"},
            {"UPDATE t SET k = -1 WHERE t.id NOT IN (SELECT val FROM i WHERE t.k = 2)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 39"},
            {"UPDATE t SET k = -1 WHERE t.id NOT IN (SELECT k FROM wn WHERE wn.k = t.k)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 39"},
            {"UPDATE t SET k = -1 WHERE t.id NOT IN (SELECT val FROM i JOIN one ON one.k = i.k WHERE i.k ="
                + " t.k)",
                "REFUSED SQL compilation error: | ambiguous column name 'VAL'"},
            {"UPDATE t SET k = -1 WHERE t.id NOT IN (SELECT val FROM i WHERE i.k = t.k AND i.s = t.s)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 39"},
            {"UPDATE t SET k = -1 WHERE t.s NOT IN (SELECT s FROM i WHERE i.k = t.k)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 38"},
            {"UPDATE t SET k = -1 WHERE t.k NOT IN (SELECT i.k FROM i WHERE i.k = t.id)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 38"},
            {"UPDATE t SET k = -1 WHERE t.id NOT IN (SELECT val FROM i WHERE i.k = t.k) OR t.id NOT IN "
                + "(SELECT 1)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 39"},
        });
    }

    @Test
    public void outerJoinSidesHavingAndNullTests() {
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
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        assertCells(new String[][] {
            {"SELECT o.id, i.val FROM i RIGHT JOIN o ON i.k = o.k AND i.val IN (SELECT val FROM i i2 WHERE"
                + " i2.k = i.k AND i2.val > 20) ORDER BY o.id, i.val",
                "1|null ;; 2|21 ;; 3|null ;; 4|21 ;; 5|null"},
            {"SELECT o.id, i.val FROM o FULL JOIN i ON i.k = o.k AND i.val IN (SELECT val FROM i i2 WHERE "
                + "i2.k = i.k AND i2.val > 20) ORDER BY o.id, i.val",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 65"},
            {"SELECT o.id, i.val FROM o FULL JOIN i ON i.k = o.k AND o.id IN (SELECT val FROM i i2 WHERE "
                + "i2.k = o.k) ORDER BY o.id, i.val",
                "1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null ;; null|10 ;; null|20 ;; null|21 ;; null|30 "
                + ";; null|null"},
            {"SELECT o.id, i.val FROM o LEFT JOIN i ON i.k = o.k AND EXISTS (SELECT 1 FROM i i2 WHERE i2.k"
                + " = i.k AND i2.val > 20) ORDER BY o.id, i.val",
                "1|null ;; 2|20 ;; 2|21 ;; 3|null ;; 4|20 ;; 4|21 ;; 5|null"},
            {"SELECT o.id, i.val FROM o RIGHT JOIN i ON i.k = o.k AND EXISTS (SELECT 1 FROM i i2 WHERE "
                + "i2.k = o.k AND i2.val > 20) ORDER BY o.id, i.val",
                "2|20 ;; 2|21 ;; 4|20 ;; 4|21 ;; null|10 ;; null|30 ;; null|null"},
            {"SELECT o.id, i.val FROM o FULL JOIN i ON i.k = o.k AND EXISTS (SELECT 1 FROM i i2 WHERE i2.k"
                + " = i.k AND i2.val > 20) ORDER BY o.id, i.val",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 55"},
            {"SELECT o.id, i.val FROM o RIGHT JOIN i ON i.k = o.k AND o.id IN (SELECT val FROM i i2 WHERE "
                + "i2.k = o.k) ORDER BY o.id, i.val",
                "null|10 ;; null|20 ;; null|21 ;; null|30 ;; null|null"},
            {"SELECT o.id, i.val FROM i RIGHT JOIN o ON i.k = o.k AND o.id IN (SELECT val FROM i i2 WHERE "
                + "i2.k = o.k) ORDER BY o.id, i.val",
                "1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null"},
            {"SELECT o.id, i.val FROM o LEFT JOIN i ON i.k = o.k AND o.id IN (SELECT val FROM i i2 WHERE "
                + "i2.k = i.k) ORDER BY o.id, i.val",
                "1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null"},
            {"SELECT o.id, i.val FROM o LEFT JOIN i ON i.k = o.k AND i.val IN (SELECT val FROM i i2 WHERE "
                + "i2.val > 20) AND o.id IN (SELECT val FROM i i3 WHERE i3.k = o.k) ORDER BY o.id, i.val",
                "1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null"},
            {"SELECT o.id, i.val FROM o JOIN i ON i.k = o.k AND i.val > ANY (SELECT val FROM i i2 WHERE "
                + "i2.k = o.k) ORDER BY o.id, i.val",
                "2|21 ;; 4|21"},
            {"SELECT o.id, i.val FROM o LEFT JOIN i ON i.k = o.k AND o.id = ANY (SELECT val FROM i i2 "
                + "WHERE i2.k = o.k) ORDER BY o.id, i.val",
                "1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null"},
            {"SELECT o.id, i.val FROM o LEFT JOIN i ON i.k = o.k AND i.val IN (SELECT val FROM i i2 WHERE "
                + "i2.k = 2 AND o.k = 2) ORDER BY o.id, i.val",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 65"},
            {"SELECT o.id, i.val FROM o LEFT JOIN i ON i.k = o.k AND i.val IN (SELECT o.id + val FROM i "
                + "i2) ORDER BY o.id, i.val",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 65"},
            {"SELECT o.id, i.val FROM o LEFT JOIN i ON i.k = o.k AND i.val + o.id IN (SELECT val FROM i i2"
                + " WHERE i2.val > 20) ORDER BY o.id, i.val",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 72"},
            {"SELECT o.id, i.val FROM o LEFT JOIN i ON i.k = o.k AND i.val IN (SELECT val FROM i i2 WHERE "
                + "i2.k = o.k) ORDER BY o.id, i.val",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 65"},
            {"SELECT o.id, nn.k FROM o LEFT JOIN nn ON nn.k = o.k AND nn.k IN (SELECT k FROM i i2 WHERE "
                + "i2.val > o.id) ORDER BY o.id, nn.k",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 65"},
            {"SELECT o.id, i.val FROM o LEFT JOIN i ON i.k = o.k AND i.val IN (SELECT val FROM i i2 WHERE "
                + "i2.k = o.k AND i2.k = i.k) ORDER BY o.id, i.val",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 65"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE o.k > 1 HAVING COUNT(*) > 1) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE o.k = 2 HAVING COUNT(*) > 1) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = o.k AND o.k > 1 HAVING COUNT(*) > 1) FROM o "
                + "ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = o.k OR i.k = o.id HAVING COUNT(*) > 1) FROM o"
                + " ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k IN (o.k, o.id) HAVING COUNT(*) > 1) FROM o "
                + "ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id FROM o WHERE EXISTS (SELECT COUNT(*) FROM i WHERE i.k < o.k HAVING COUNT(*) > 1) "
                + "ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id FROM o WHERE id IN (SELECT COUNT(*) FROM i WHERE i.k < o.k HAVING COUNT(*) > 1) "
                + "ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 30"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k LIKE o.s HAVING COUNT(*) > 1) FROM o ORDER BY "
                + "id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k IS NOT DISTINCT FROM o.k HAVING COUNT(*) > 1) "
                + "FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT COUNT(*) FROM i JOIN one ON one.k = i.k WHERE i.k < o.k HAVING COUNT(*) >"
                + " 0) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE o.k < i.k HAVING COUNT(*) > 1) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = o.k AND o.s IS NULL) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id FROM o WHERE id IN (SELECT val FROM i WHERE o.k IS NULL) ORDER BY id",
                ""},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE o.k + 1 IS NULL) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE o.k IS NULL OR o.k = 1) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT 1 WHERE o.k IS NULL) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT COUNT(*) FROM i HAVING o.k IS NULL) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT COUNT(*) FROM i JOIN one ON o.k IS NULL) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT COUNT(*) FROM nn WHERE o.k IS NULL) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE o.s IS NOT NULL AND i.s = o.s) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT MAX(val) FROM i WHERE i.k = o.k AND o.k IS NOT NULL) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id FROM o WHERE id = ANY (SELECT val FROM i WHERE o.k IS NULL) ORDER BY id",
                ""},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = 2 AND o.k IS NULL) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT o.id, i.val FROM o LEFT JOIN i ON i.k = o.k JOIN one ON one.k = o.k AND one.val IN "
                + "(SELECT val FROM i i2 WHERE i2.k = o.k) ORDER BY o.id, i.val",
                ""},
            {"SELECT o.id, i.val FROM o LEFT JOIN i ON i.k = o.k LEFT JOIN one ON one.k = i.k AND one.val "
                + "IN (SELECT val FROM i i2 WHERE i2.k = o.k) ORDER BY o.id, i.val",
                "1|10 ;; 2|20 ;; 2|21 ;; 3|null ;; 4|20 ;; 4|21 ;; 5|null"},
            {"SELECT id FROM o WHERE EXISTS (SELECT COUNT(*) FROM i WHERE i.k = o.k HAVING COUNT(*) > 1) "
                + "ORDER BY id",
                "2 ;; 4"},
            {"SELECT id FROM o WHERE NOT EXISTS (SELECT 1 FROM i WHERE o.k IS NULL) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE o.k IS NULL OR i.k = 1) FROM o ORDER BY id",
                "1|1 ;; 2|1 ;; 3|5 ;; 4|1 ;; 5|1"},
            {"SELECT k, (SELECT COUNT(*) FROM i WHERE nn.k IS NULL) FROM nn ORDER BY k",
                "1|0 ;; 2|0"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE o.id IS NULL) FROM o ORDER BY id",
                "1|0 ;; 2|0 ;; 3|0 ;; 4|0 ;; 5|0"},
            {"DELETE FROM t WHERE t.s NOT IN (SELECT s FROM i WHERE i.k = t.k) AND t.id > 0",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 32"},
        });
    }
}
