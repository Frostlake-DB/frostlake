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
 * Window functions in a correlated subquery or a LATERAL body (live-verified): a window over rows a filter picks by the
 * outer row is refused as "Unsupported subquery type cannot be evaluated" where something reads its values — a
 * QUALIFY, an IN's or a scalar subquery's item, a LATERAL body's ORDER BY or a column the query reads — while an
 * EXISTS item, an unread derived or lateral column and a filter reading no outer name leave it answered. A QUALIFY
 * and a window item read the outer row as the WHERE does, and a nested query's ORDER BY may hold a window.
 */
public class CorrelatedWindowFilterTest extends MeasuredCellsTestSupport {

    @Test
    public void existsInAndScalar() {
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
            {"SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.k = o.k QUALIFY ROW_NUMBER() OVER "
                + "(ORDER BY val) = 2) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i QUALIFY ROW_NUMBER() OVER (PARTITION BY o.k "
                + "ORDER BY val) = 1) ORDER BY id",
                "REFUSED SQL compilation error: | Window function [ROW_NUMBER() OVER (PARTITION BY O.K ORDER "
                + "BY I.VAL ASC NULLS LAST)] contains a correlation."},
            {"SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.k = o.k QUALIFY ROW_NUMBER() OVER "
                + "(ORDER BY val) = o.id) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id FROM o WHERE NOT EXISTS (SELECT 1 FROM i WHERE i.k = o.k QUALIFY ROW_NUMBER() OVER"
                + " (ORDER BY val) = 2) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 27"},
            {"SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.k > o.k QUALIFY SUM(val) OVER () > 0)"
                + " ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.k = o.k AND i.val IN (SELECT MAX(val)"
                + " OVER () FROM i)) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE EXISTS (SELECT 1 FROM (SELECT k, ROW_NUMBER() OVER (ORDER BY val) r "
                + "FROM i WHERE i.k = o.k) d WHERE d.r = 2) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id FROM o WHERE id > 9 AND EXISTS (SELECT 1 FROM i WHERE i.k = o.k QUALIFY "
                + "ROW_NUMBER() OVER (ORDER BY val) = 2) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.k = o.k QUALIFY COUNT(*) OVER () = 2)"
                + " ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i HAVING COUNT(*) = o.id) ORDER BY id",
                "5"},
            {"SELECT id FROM o WHERE o.id IN (SELECT ROW_NUMBER() OVER (ORDER BY val) FROM i WHERE i.k = "
                + "o.k) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 32"},
            {"SELECT id FROM o WHERE o.id IN (SELECT LAG(val) OVER (ORDER BY val) FROM i WHERE i.k = o.k) "
                + "ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k IN (SELECT k FROM i QUALIFY ROW_NUMBER() OVER (ORDER BY val) = "
                + "o.id) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k IN (SELECT k FROM i WHERE i.val > o.id QUALIFY ROW_NUMBER() OVER "
                + "(ORDER BY val) = 1) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k IN (SELECT RANK() OVER (PARTITION BY o.k ORDER BY val) FROM i) "
                + "ORDER BY id",
                "REFUSED SQL compilation error: | Window function [RANK() OVER (PARTITION BY O.K ORDER BY "
                + "I.VAL ASC NULLS LAST)] contains a correlation."},
            {"SELECT id, (SELECT MAX(r) FROM (SELECT ROW_NUMBER() OVER (ORDER BY val) r FROM i WHERE i.k ="
                + " o.k)) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT MAX(r) FROM (SELECT ROW_NUMBER() OVER (ORDER BY val) + o.id r FROM i)) "
                + "FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT MAX(r) FROM (SELECT ROW_NUMBER() OVER (PARTITION BY o.k ORDER BY val) r "
                + "FROM i)) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Window function [ROW_NUMBER() OVER (PARTITION BY O.K ORDER "
                + "BY I.VAL ASC NULLS LAST)] contains a correlation."},
            {"SELECT id, (SELECT MAX(val) OVER () FROM i WHERE i.k = o.k LIMIT 1) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT MAX(r) FROM (SELECT SUM(val) OVER (ORDER BY val) r FROM i WHERE i.k = "
                + "o.k)) FROM o WHERE id > 9 ORDER BY id",
                ""},
            {"SELECT id, (SELECT COUNT(*) FROM i WHERE i.k = o.k QUALIFY ROW_NUMBER() OVER (ORDER BY val) "
                + "= 1) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | [I.VAL] is not a valid group by expression"},
            {"SELECT id FROM o WHERE id = (SELECT MAX(r) FROM (SELECT ROW_NUMBER() OVER (ORDER BY val) r "
                + "FROM i WHERE i.k = o.k)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT o.id, x.r FROM o, LATERAL (SELECT ROW_NUMBER() OVER (ORDER BY val) r FROM i WHERE i.k"
                + " = o.k) x ORDER BY o.id, x.r",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT o.id, x.r FROM o, LATERAL (SELECT SUM(val) OVER () r FROM i WHERE i.k = o.k) x ORDER "
                + "BY o.id, x.r",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT o.id, x.r FROM o, LATERAL (SELECT ROW_NUMBER() OVER (ORDER BY val) r FROM i WHERE i.k"
                + " = 2 AND o.id > 3) x ORDER BY o.id, x.r",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT o.id, x.r FROM o, LATERAL (SELECT LAG(val) OVER (ORDER BY val) r FROM i WHERE i.k = "
                + "o.k) x ORDER BY o.id, x.r",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT o.id, x.r FROM o, LATERAL (SELECT ROW_NUMBER() OVER (PARTITION BY o.k ORDER BY val) r"
                + " FROM i WHERE i.k = 2) x ORDER BY o.id, x.r",
                "REFUSED SQL compilation error: | Window function [ROW_NUMBER() OVER (PARTITION BY O.K ORDER "
                + "BY I.VAL ASC NULLS LAST)] contains a correlation."},
            {"SELECT o.id, x.val FROM o, LATERAL (SELECT val FROM i WHERE i.k = o.k ORDER BY ROW_NUMBER() "
                + "OVER (ORDER BY val)) x ORDER BY o.id, x.val",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT o.id, x.r FROM o JOIN LATERAL (SELECT ROW_NUMBER() OVER (ORDER BY val) r FROM i WHERE"
                + " i.k = o.k) x ORDER BY o.id, x.r",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT o.id, x.r FROM o, LATERAL (SELECT RANK() OVER (ORDER BY val) r FROM i WHERE i.k = "
                + "o.k) x WHERE o.id > 9 ORDER BY o.id, x.r",
                ""},
            {"SELECT o.id, x.r FROM o, LATERAL (SELECT ROW_NUMBER() OVER (ORDER BY val) r FROM i WHERE i.k"
                + " = o.k) x WHERE FALSE ORDER BY o.id",
                ""},
        });
    }

    @Test
    public void neighbours() {
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
            {"SELECT id FROM o WHERE EXISTS (SELECT ROW_NUMBER() OVER (ORDER BY val) r FROM i WHERE i.k = "
                + "o.k QUALIFY r = 1) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, (SELECT MAX(val) FROM (SELECT val, ROW_NUMBER() OVER (ORDER BY val) r FROM i "
                + "WHERE i.k = o.k) WHERE r = 1) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id, (SELECT MAX(val) FROM (SELECT val FROM i WHERE i.k = o.k QUALIFY ROW_NUMBER() "
                + "OVER (ORDER BY val) = 1)) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i JOIN one ON one.k = i.k AND one.k = o.k "
                + "QUALIFY ROW_NUMBER() OVER (ORDER BY i.val) = 1) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE o.id > 100 QUALIFY ROW_NUMBER() OVER "
                + "(ORDER BY val) = 1) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.id IN (SELECT ROW_NUMBER() OVER (ORDER BY val) + o.k FROM i) ORDER "
                + "BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 32"},
            {"SELECT id FROM o WHERE EXISTS (SELECT 1 FROM e WHERE e.k = o.k QUALIFY ROW_NUMBER() OVER "
                + "(ORDER BY val) = 1) ORDER BY id",
                ""},
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT k FROM i WHERE i.k = o.k QUALIFY ROW_NUMBER() OVER "
                + "(ORDER BY val) = 1)) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT COUNT(*) FROM o, LATERAL (SELECT ROW_NUMBER() OVER (ORDER BY val) r FROM i WHERE i.k "
                + "= o.k) x",
                "5"},
            {"SELECT o.id, x.r FROM o, LATERAL (SELECT ROW_NUMBER() OVER (ORDER BY i.val) r FROM i JOIN "
                + "one ON one.k = i.k AND one.k = o.k) x ORDER BY o.id, x.r",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT o.id FROM o, LATERAL (SELECT val FROM i WHERE i.k = o.k LIMIT 1) x WHERE o.id > 9",
                ""},
            {"SELECT o.id FROM o, LATERAL (SELECT val FROM i WHERE i.k = o.k LIMIT 1) x WHERE FALSE",
                ""},
            {"SELECT o.id, x.r FROM o LEFT JOIN LATERAL (SELECT ROW_NUMBER() OVER (ORDER BY val) r FROM i "
                + "WHERE i.k = o.k) x ON TRUE ORDER BY o.id, x.r",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT o.id, x.val FROM o, LATERAL (SELECT val FROM i WHERE i.k = o.k QUALIFY ROW_NUMBER() "
                + "OVER (ORDER BY val) = 1) x ORDER BY o.id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT o.id, x.r FROM o, LATERAL (SELECT r FROM (SELECT ROW_NUMBER() OVER (ORDER BY val) r "
                + "FROM i WHERE i.k = o.k)) x ORDER BY o.id, x.r",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE o.k = 2 QUALIFY ROW_NUMBER() OVER "
                + "(ORDER BY val) = 1) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.k = o.k QUALIFY ROW_NUMBER() OVER "
                + "(ORDER BY val) = 1) OR id = 5 ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, EXISTS (SELECT 1 FROM i WHERE i.k = o.k QUALIFY ROW_NUMBER() OVER (ORDER BY val) "
                + "= 1) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 11"},
            {"SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.k = o.k QUALIFY FIRST_VALUE(val) OVER"
                + " (ORDER BY val) = 10) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.k = o.k QUALIFY LAG(val) OVER (ORDER "
                + "BY val) IS NULL) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.k = o.k QUALIFY RANK() OVER "
                + "(PARTITION BY i.k ORDER BY val) = 1) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT ROW_NUMBER() OVER (ORDER BY val) FROM i WHERE i.k ="
                + " o.k) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE id = 1 AND EXISTS (SELECT 1 FROM i WHERE i.k = o.k QUALIFY "
                + "ROW_NUMBER() OVER (ORDER BY val) = 1) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 34"},
            {"SELECT id, (SELECT MAX(r) FROM (SELECT ROW_NUMBER() OVER (ORDER BY val) r FROM i WHERE i.k ="
                + " o.k)) FROM o WHERE id = 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12"},
            {"SELECT o.id, x.r FROM o, LATERAL (SELECT ROW_NUMBER() OVER (ORDER BY val) r FROM i WHERE i.k"
                + " = o.k) x WHERE o.id = 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT o.id, x.r FROM (SELECT * FROM o WHERE id = 1) o, LATERAL (SELECT ROW_NUMBER() OVER "
                + "(ORDER BY val) r FROM i WHERE i.k = o.k) x",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.k = o.k QUALIFY ROW_NUMBER() OVER "
                + "(ORDER BY val) = 1) AND FALSE",
                ""},
        });
    }

    @Test
    public void inQualify() {
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
            {"SELECT id FROM o WHERE o.k IN (SELECT k FROM i QUALIFY ROW_NUMBER() OVER (ORDER BY val) <= "
                + "o.id) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id, o.k IN (SELECT k FROM i QUALIFY ROW_NUMBER() OVER (ORDER BY val) = o.id) FROM o "
                + "ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 19"},
            {"SELECT id FROM o WHERE o.k IN (SELECT k FROM i QUALIFY ROW_NUMBER() OVER (ORDER BY val) > "
                + "o.id) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k IN (SELECT val FROM i QUALIFY ROW_NUMBER() OVER (ORDER BY val) = "
                + "o.id) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.id IN (SELECT k FROM i QUALIFY ROW_NUMBER() OVER (ORDER BY val) = "
                + "o.id) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 32"},
            {"SELECT id FROM o WHERE o.k IN (SELECT k + 0 FROM i QUALIFY ROW_NUMBER() OVER (ORDER BY val) "
                + "= o.id) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT k FROM i QUALIFY ROW_NUMBER() OVER (ORDER BY val) "
                + "= o.id) ORDER BY id",
                "5"},
            {"SELECT id FROM o WHERE o.id IN (SELECT val + o.k FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.id IN (SELECT SUM(val) OVER () - 70 + o.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 32"},
            {"SELECT id FROM o WHERE o.k IN (SELECT k FROM i QUALIFY SUM(val) OVER () > o.id) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
        });
    }

    @Test
    public void answeredShapes() {
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
            {"SELECT LISTAGG(id, ',') WITHIN GROUP (ORDER BY id) FROM o WHERE EXISTS (SELECT ROW_NUMBER() "
                + "OVER (ORDER BY val) FROM i WHERE i.k = o.k)",
                "1,2,4"},
            {"SELECT LISTAGG(id, ',') WITHIN GROUP (ORDER BY id) FROM o WHERE EXISTS (SELECT 1 FROM i "
                + "QUALIFY ROW_NUMBER() OVER (ORDER BY val) = o.id)",
                "1,2,3,4,5"},
            {"SELECT LISTAGG(id, ',') WITHIN GROUP (ORDER BY id) FROM o WHERE EXISTS (SELECT 1 FROM i "
                + "WHERE i.k = o.k ORDER BY ROW_NUMBER() OVER (ORDER BY val))",
                "1,2,4"},
            {"SELECT LISTAGG(id, ',') WITHIN GROUP (ORDER BY id) FROM o WHERE EXISTS (SELECT 1 FROM i "
                + "WHERE i.k = 2 QUALIFY RANK() OVER (ORDER BY val) = o.id)",
                "1,2"},
            {"SELECT LISTAGG(id, ',') WITHIN GROUP (ORDER BY id) FROM o WHERE EXISTS (SELECT SUM(val) OVER"
                + " () + o.id FROM i)",
                "1,2,3,4,5"},
            {"SELECT LISTAGG(id, ',') WITHIN GROUP (ORDER BY id) FROM o WHERE EXISTS (SELECT 1 FROM i "
                + "QUALIFY LAG(val) OVER (ORDER BY val) > o.id * 10)",
                "1,2"},
            {"SELECT LISTAGG(id, ',') WITHIN GROUP (ORDER BY id) FROM o WHERE EXISTS (SELECT 1 FROM "
                + "(SELECT k, ROW_NUMBER() OVER (ORDER BY val) r FROM i) d WHERE d.k = o.k AND d.r = 2)",
                "2,4"},
            {"SELECT LISTAGG(id, ',') WITHIN GROUP (ORDER BY id) FROM o WHERE EXISTS (SELECT 1 FROM i "
                + "QUALIFY ROW_NUMBER() OVER (ORDER BY val) = o.id AND i.k = o.k)",
                "1,2"},
            {"SELECT LISTAGG(id, ',') WITHIN GROUP (ORDER BY id) FROM o WHERE EXISTS (SELECT 1 FROM i "
                + "QUALIFY ROW_NUMBER() OVER (ORDER BY val) = o.k)",
                "1,2,4,5"},
            {"SELECT LISTAGG(id, ',') WITHIN GROUP (ORDER BY id) FROM o WHERE EXISTS (SELECT 1 FROM i "
                + "QUALIFY ROW_NUMBER() OVER (ORDER BY val) = 2 AND o.id > 3)",
                "4,5"},
            {"SELECT LISTAGG(id, ',') WITHIN GROUP (ORDER BY id) FROM o WHERE o.k NOT IN (SELECT "
                + "ROW_NUMBER() OVER (ORDER BY val) FROM i WHERE i.k = o.k)",
                "3,5"},
            {"SELECT LISTAGG(id || ':' || c, ',') WITHIN GROUP (ORDER BY id) FROM (SELECT id, (SELECT "
                + "COUNT(*) FROM (SELECT FIRST_VALUE(val) OVER (ORDER BY val) r FROM i WHERE i.k = o.k)) c FROM"
                + " o)",
                "1:1,2:2,3:0,4:2,5:0"},
            {"SELECT LISTAGG(id || ':' || COALESCE(c::VARCHAR, '-'), ',') WITHIN GROUP (ORDER BY id) FROM "
                + "(SELECT id, (SELECT MAX(r) FROM (SELECT k, ROW_NUMBER() OVER (ORDER BY val) r FROM i) d "
                + "WHERE d.k = o.k) c FROM o)",
                "1:1,2:3,3:-,4:3,5:-"},
            {"SELECT LISTAGG(id || ':' || c, ',') WITHIN GROUP (ORDER BY id) FROM (SELECT id, (SELECT "
                + "MAX(r) FROM (SELECT ROW_NUMBER() OVER (ORDER BY val) r FROM i QUALIFY r <= o.id)) c FROM o)",
                "1:1,2:2,3:3,4:4,5:5"},
            {"SELECT LISTAGG(id || ':' || COALESCE(c::VARCHAR, '-'), ',') WITHIN GROUP (ORDER BY id) FROM "
                + "(SELECT id, (SELECT MAX(val) FROM (SELECT val, ROW_NUMBER() OVER (ORDER BY val) r FROM i "
                + "WHERE i.k = o.k)) c FROM o)",
                "1:10,2:21,3:-,4:21,5:-"},
            {"SELECT LISTAGG(o.id || ':' || x.r, ',') WITHIN GROUP (ORDER BY o.id, x.r) FROM o, LATERAL "
                + "(SELECT ROW_NUMBER() OVER (ORDER BY val) r FROM i WHERE i.k = 2) x",
                "1:1,1:2,2:1,2:2,3:1,3:2,4:1,4:2,5:1,5:2"},
            {"SELECT LISTAGG(o.id || ':' || x.r, ',') WITHIN GROUP (ORDER BY o.id, x.r) FROM o, LATERAL "
                + "(SELECT ROW_NUMBER() OVER (ORDER BY val) + o.id r FROM i WHERE i.k = 2) x",
                "1:2,1:3,2:3,2:4,3:4,3:5,4:5,4:6,5:6,5:7"},
            {"SELECT LISTAGG(o.id || ':' || x.val, ',') WITHIN GROUP (ORDER BY o.id, x.val) FROM o, "
                + "LATERAL (SELECT val FROM i WHERE i.k = 2 QUALIFY ROW_NUMBER() OVER (ORDER BY val) = o.id) x",
                "1:20,2:21"},
            {"SELECT LISTAGG(o.id || ':' || x.r, ',') WITHIN GROUP (ORDER BY o.id, x.r) FROM o, LATERAL "
                + "(SELECT r FROM (SELECT k, ROW_NUMBER() OVER (ORDER BY val) r FROM i) d WHERE d.k = o.k) x",
                "1:1,2:2,2:3,4:2,4:3"},
            {"SELECT LISTAGG(o.id || ':' || x.val, ',') WITHIN GROUP (ORDER BY o.id, x.val) FROM o, "
                + "LATERAL (SELECT val FROM i WHERE i.k = 2 QUALIFY ROW_NUMBER() OVER (ORDER BY val) = 1 AND "
                + "o.id > 3) x",
                "4:20,5:20"},
            {"SELECT LISTAGG(o.id || ':' || x.val, ',') WITHIN GROUP (ORDER BY o.id, x.val) FROM o, "
                + "LATERAL (SELECT val, ROW_NUMBER() OVER (ORDER BY val) r FROM i WHERE i.k = o.k) x",
                "1:10,2:20,2:21,4:20,4:21"},
            {"SELECT COUNT(*) FROM o, LATERAL (SELECT ROW_NUMBER() OVER (ORDER BY val) r FROM i WHERE i.k "
                + "= o.k) x",
                "5"},
            {"SELECT LISTAGG(id, ',') WITHIN GROUP (ORDER BY id) FROM o WHERE EXISTS (SELECT 1 FROM "
                + "(SELECT k, ROW_NUMBER() OVER (ORDER BY val) r FROM i WHERE i.k = o.k) d)",
                "1,2,4"},
            {"SELECT LISTAGG(id, ',') WITHIN GROUP (ORDER BY id) FROM o WHERE EXISTS (SELECT * FROM "
                + "(SELECT ROW_NUMBER() OVER (ORDER BY val) r FROM i WHERE i.k = o.k) d)",
                "1,2,4"},
            {"SELECT LISTAGG(id, ',') WITHIN GROUP (ORDER BY id) FROM o WHERE EXISTS (SELECT 1 FROM i "
                + "WHERE o.id < 100 QUALIFY ROW_NUMBER() OVER (ORDER BY val) = 1)",
                "1,2,3,4,5"},
            {"SELECT LISTAGG(id, ',') WITHIN GROUP (ORDER BY id) FROM o WHERE o.id IN (SELECT ROW_NUMBER()"
                + " OVER (ORDER BY val) FROM i QUALIFY ROW_NUMBER() OVER (ORDER BY val) <= o.k)",
                "1,2,5"},
            {"SELECT LISTAGG(id, ',') WITHIN GROUP (ORDER BY id) FROM o WHERE o.id IN (SELECT ROW_NUMBER()"
                + " OVER (ORDER BY val) FROM i QUALIFY ROW_NUMBER() OVER (ORDER BY val) = o.k)",
                "1,2,5"},
            {"SELECT LISTAGG(id, ',') WITHIN GROUP (ORDER BY id) FROM o WHERE o.k NOT IN (SELECT k FROM i "
                + "QUALIFY ROW_NUMBER() OVER (ORDER BY val) = o.id)",
                "5"},
            {"SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i ORDER BY ROW_NUMBER() OVER (ORDER BY "
                + "val))",
                "5"},
            {"SELECT LISTAGG(d.val, ',') WITHIN GROUP (ORDER BY d.val) FROM (SELECT val FROM i ORDER BY "
                + "ROW_NUMBER() OVER (ORDER BY val)) d",
                "10,20,21,30"},
            {"SELECT COUNT(*) FROM o, LATERAL (SELECT RANK() OVER (ORDER BY val) r FROM i WHERE i.k = o.k)"
                + " x WHERE o.id > 9",
                "0"},
            {"SELECT COUNT(*) FROM o, LATERAL (SELECT ROW_NUMBER() OVER (ORDER BY val) r FROM i WHERE i.k "
                + "= o.k) x WHERE FALSE",
                "0"},
            {"SELECT COUNT(*) FROM o, LATERAL (SELECT val FROM i WHERE i.k = o.k LIMIT 1) x WHERE o.id > 9",
                "0"},
            {"SELECT COUNT(*) FROM o, LATERAL (SELECT val FROM i WHERE i.k = o.k LIMIT 1) x WHERE FALSE",
                "0"},
            {"SELECT COUNT(*) FROM o, LATERAL (SELECT RANK() OVER (ORDER BY val) r FROM i WHERE i.k = o.k)"
                + " x WHERE o.id > 3",
                "2"},
        });
    }
}
