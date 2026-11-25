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
 * Where two refusals are placed (live-verified): the refusal of a subquery whose own non-aggregated scalar subquery,
 * written in its filter, reads the outer row points at that nested subquery; and an invalid identifier inside a
 * subquery of a multi-table INSERT's WHEN condition points at the reference, counted from the statement's start.
 */
public class NestedSubqueryRefusalPositionTest extends MeasuredCellsTestSupport {

    @Test
    public void aNestedReadOfTheOuterRowIsPlacedAtTheNestedQuery() {
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
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o WHERE FALSE");
        engine.execute("CREATE OR REPLACE TABLE t3 AS SELECT id, k FROM o WHERE FALSE");
        assertCells(new String[][] {
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = o.k AND i.k = (SELECT k FROM one WHERE one.k "
                + "= o.k)) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 62"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT k FROM one WHERE one.k = o.k)) FROM o"
                + " ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 48"},
            {"SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.k = (SELECT k FROM one WHERE one.k = "
                + "o.k)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 60"},
            {"SELECT id FROM o WHERE o.k IN (SELECT k FROM i WHERE i.k = (SELECT k FROM one WHERE one.k = "
                + "o.k)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 60"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(k) FROM one WHERE one.k = (SELECT"
                + " MAX(k) FROM nn WHERE nn.k = o.k))) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = o.k AND i.val > (SELECT MAX(val) FROM one "
                + "WHERE one.k = i.k) AND i.k = (SELECT k FROM one WHERE one.k = o.k)) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 119"},
            {"SELECT id,\n  (SELECT COUNT(*) FROM i\n   WHERE i.k = (SELECT k FROM one WHERE one.k = o.k))"
                + " FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 3, "
                + "position 16"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT k FROM one WHERE one.k = o.k) AND i.k"
                + " = (SELECT k FROM one WHERE one.k = o.k)) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 48"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE EXISTS (SELECT 1 WHERE i.k = o.k)) FROM o ORDER BY "
                + "id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.k = (SELECT MAX(k) FROM one WHERE "
                + "one.k = (SELECT MAX(k) FROM nn WHERE nn.k = o.k))) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id FROM o WHERE o.k = (SELECT MAX(k) FROM i WHERE i.k = (SELECT k FROM one WHERE "
                + "one.k = o.k)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 64"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.val > (SELECT MAX(val) FROM one WHERE one.k = "
                + "o.k) AND i.k = o.k) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT MAX((SELECT one.val FROM one WHERE one.k = o.k)) FROM i) FROM o ORDER BY "
                + "id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id FROM o ORDER BY (SELECT COUNT(*) FROM i WHERE i.k = (SELECT k FROM one WHERE one.k"
                + " = o.k)), id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 63"},
            {"SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.k = (SELECT k FROM one WHERE one.k = "
                + "o.k)) AND id > (SELECT 0) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 60"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT MAX(k) FROM one WHERE one.k = o.k)) "
                + "FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k > (SELECT k FROM one WHERE one.k = o.k)) FROM o"
                + " ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 48"},
            {"SELECT id, (SELECT (SELECT one.val FROM one WHERE one.k = o.k) FROM i LIMIT 1) FROM o ORDER "
                + "BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT k FROM one WHERE one.k = o.k GROUP BY"
                + " k)) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 48"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT k FROM one WHERE one.k = o.k LIMIT "
                + "1)) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 48"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT k FROM one WHERE one.k = i.k + o.k)) "
                + "FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 48"},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = (SELECT COUNT(*) FROM one WHERE one.k = o.k))"
                + " FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT COUNT(*) FROM i GROUP BY i.k HAVING i.k = (SELECT k FROM one WHERE one.k "
                + "= o.k) LIMIT 1) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT COUNT(*) FROM i JOIN one ON one.k = i.k AND one.val = (SELECT val FROM "
                + "one o2 WHERE o2.k = o.k)) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 74"},
        });
    }

    @Test
    public void aWhenConditionsSubqueryReferenceIsPlacedInTheStatement() {
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
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT id, k FROM o WHERE FALSE");
        engine.execute("CREATE OR REPLACE TABLE t3 AS SELECT id, k FROM o WHERE FALSE");
        assertCells(new String[][] {
            {"INSERT ALL WHEN EXISTS (SELECT 1 FROM i WHERE i.k = src.k) THEN INTO t ELSE INTO t3 SELECT "
                + "id, k FROM o src",
                "REFUSED SQL compilation error: error line 1 at position 52 | invalid identifier 'SRC.K'"},
            {"INSERT FIRST WHEN EXISTS (SELECT 1 FROM i WHERE i.k = src.k) THEN INTO t ELSE INTO t3 SELECT"
                + " id, k FROM o src",
                "REFUSED SQL compilation error: error line 1 at position 54 | invalid identifier 'SRC.K'"},
            {"INSERT ALL WHEN src.k > 1 THEN INTO t ELSE INTO t3 SELECT id, k FROM o src",
                "REFUSED SQL compilation error: error line 1 at position 16 | invalid identifier 'SRC.K'"},
            {"INSERT ALL WHEN zz > 1 THEN INTO t ELSE INTO t3 SELECT id, k FROM o",
                "REFUSED SQL compilation error: error line 1 at position 16 | invalid identifier 'ZZ'"},
            {"INSERT ALL WHEN k > 1 AND zz > 1 THEN INTO t ELSE INTO t3 SELECT id, k FROM o",
                "REFUSED SQL compilation error: error line 1 at position 26 | invalid identifier 'ZZ'"},
            {"INSERT ALL WHEN k > 1 THEN INTO t WHEN zz > 1 THEN INTO t3 SELECT id, k FROM o",
                "REFUSED SQL compilation error: error line 1 at position 39 | invalid identifier 'ZZ'"},
            {"INSERT ALL WHEN (SELECT COUNT(*) FROM i WHERE i.k = zz) > 0 THEN INTO t SELECT id, k FROM o",
                "REFUSED SQL compilation error: error line 1 at position 52 | invalid identifier 'ZZ'"},
            {"INSERT ALL WHEN k IN (SELECT k FROM i WHERE i.val = qq) THEN INTO t SELECT id, k FROM o",
                "REFUSED SQL compilation error: error line 1 at position 52 | invalid identifier 'QQ'"},
            {"INSERT ALL WHEN zz > 1 THEN INTO t SELECT * FROM (VALUES (1, 2), (3, 4))",
                "REFUSED SQL compilation error: error line 1 at position 16 | invalid identifier 'ZZ'"},
            {"INSERT ALL WHEN EXISTS (SELECT 1 FROM i WHERE i.k = zz) THEN INTO t SELECT id, k FROM o",
                "REFUSED SQL compilation error: error line 1 at position 52 | invalid identifier 'ZZ'"},
            {"INSERT FIRST WHEN k > 1 THEN INTO t WHEN EXISTS (SELECT 1 FROM i WHERE i.k = src.k) THEN "
                + "INTO t3 SELECT id, k FROM o src",
                "REFUSED SQL compilation error: error line 1 at position 77 | invalid identifier 'SRC.K'"},
            {"INSERT ALL\n  WHEN EXISTS (SELECT 1 FROM i\n    WHERE i.k = src.k) THEN INTO t SELECT id, k "
                + "FROM o src",
                "REFUSED SQL compilation error: error line 3 at position 16 | invalid identifier 'SRC.K'"},
            {"INSERT ALL WHEN i.k > 1 THEN INTO t SELECT id, k FROM o",
                "REFUSED SQL compilation error: error line 1 at position 16 | invalid identifier 'I.K'"},
            {"INSERT ALL WHEN EXISTS (SELECT 1 FROM i WHERE i.k = o.k) THEN INTO t SELECT id, k FROM o",
                "REFUSED SQL compilation error: error line 1 at position 52 | invalid identifier 'O.K'"},
        });
    }
}
