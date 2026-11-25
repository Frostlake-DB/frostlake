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
 * A filter the statistics rule out spares the subquery it stands in, although a column it reads holds a NULL: a NULL
 * satisfies no comparison, so the interval of the values the column holds besides its NULLs proves the filter keeps
 * no row, and the correlation beside it, or the item reading the outer row, is never planned. Over {@code i.n}
 * holding 1 to 5 and a NULL, {@code i.n = 99}, {@code i.n > 5}, {@code i.n BETWEEN 10 AND 20}, {@code i.n IN (98,
 * 99)}, {@code NOT (i.n <= 5)} and {@code i.n + 1 = 99} prune, where {@code i.n > 4}, {@code i.n IS NULL},
 * {@code i.n <> 99} and {@code COALESCE(i.n, 99) = 99} keep the refusal (live-verified).
 */
public class NullableColumnFilterPruningTest extends MeasuredCellsTestSupport {

    @Test
    public void existsBesideMixedConjunct() {
        engine.execute("CREATE OR REPLACE TABLE o (id INT, a VARCHAR, n INT, b BOOLEAN, arr ARRAY)");
        engine.execute("INSERT INTO o SELECT 1,'a',1,TRUE,ARRAY_CONSTRUCT('a') UNION ALL SELECT "
            + "2,'b',2,FALSE,ARRAY_CONSTRUCT('x','b') UNION ALL SELECT 3,NULL,3,NULL,NULL UNION ALL SELECT "
            + "4,'z',NULL,TRUE,ARRAY_CONSTRUCT()");
        engine.execute("CREATE OR REPLACE TABLE i (id INT, x VARCHAR, n INT, arr ARRAY, s VARCHAR)");
        engine.execute("INSERT INTO i SELECT 1,'a',1,ARRAY_CONSTRUCT('a','b'),'abc' UNION ALL SELECT "
            + "2,'b',2,ARRAY_CONSTRUCT('b'),'bcd' UNION ALL SELECT 3,NULL,NULL,NULL,NULL UNION ALL SELECT "
            + "4,'a',3,ARRAY_CONSTRUCT('c'),'a%x' UNION ALL SELECT 5,'c',5,ARRAY_CONSTRUCT(),'zz'");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.n = 99 AND i.s || o.a = 'b')",
                "0"},
            {"SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.n = 99 AND CONTAINS(i.s || o.a,"
                + " 'b'))",
                "0"},
            {"SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.s || o.a = 'b')",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.n > 4 AND i.s || o.a = 'b')",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.n > 5 AND i.s || o.a = 'b')",
                "0"},
            {"SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.n >= 5 AND i.s || o.a = 'b')",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.n < 1 AND i.s || o.a = 'b')",
                "0"},
            {"SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.n BETWEEN 10 AND 20 AND i.s || "
                + "o.a = 'b')",
                "0"},
            {"SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.n IN (98, 99) AND i.s || o.a = "
                + "'b')",
                "0"},
            {"SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE (i.n = 99 OR i.n = 98) AND i.s ||"
                + " o.a = 'b')",
                "0"},
            {"SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE (i.n = 99 OR i.id = 1) AND i.s ||"
                + " o.a = 'b')",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE NOT (i.n <= 5) AND i.s || o.a = "
                + "'b')",
                "0"},
            {"SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.n IS NULL AND i.s || o.a = 'b')",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.n + 1 = 99 AND i.s || o.a = "
                + "'b')",
                "0"},
            {"SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.id = 99 AND i.s || o.a = 'b')",
                "0"},
            {"SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE COALESCE(i.n, 99) = 99 AND i.s ||"
                + " o.a = 'b')",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.n <> 99 AND i.s || o.a = 'b')",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.n NOT BETWEEN 0 AND 10 AND i.s "
                + "|| o.a = 'b')",
                "0"},
            {"SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.n NOT IN (1, 2, 3, 5) AND i.s "
                + "|| o.a = 'b')",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT COUNT(*) FROM o WHERE NOT EXISTS (SELECT 1 FROM i WHERE i.n = 99 AND i.s || o.a = "
                + "'b')",
                "4"},
            {"SELECT id, EXISTS (SELECT 1 FROM i WHERE i.n = 99 AND i.s || o.a = 'b') FROM o ORDER BY id",
                "1|false ;; 2|false ;; 3|false ;; 4|false"},
            {"SELECT id FROM o WHERE o.a IN (SELECT i.s || o.a FROM i WHERE i.n = 99) ORDER BY id",
                ""},
            {"SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.n = 99 AND i.n + o.n > 3)",
                "0"},
            {"SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.n + o.n > 100)",
                "0"},
            {"SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.n + o.n > 3)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.n + o.id > 100)",
                "0"},
            {"SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.id + o.id > 100)",
                "0"},
            {"SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.n = 99 AND i.s || o.a = 'b' OR "
                + "i.n = 98)",
                "0"},
            {"SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.n = 99 OR i.s || o.a = 'b')",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE IFF(i.n > 50, TRUE, FALSE) AND "
                + "i.s || o.a = 'b')",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.n * 2 > 50 AND i.s || o.a = "
                + "'b')",
                "0"},
            {"SELECT COUNT(*) FROM o WHERE EXISTS (SELECT 1 FROM i WHERE -i.n > 0 AND i.s || o.a = 'b')",
                "0"},
        });
    }

    @Test
    public void membershipOverPrunedScan() {
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
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k FROM i WHERE i.k > 100) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT o.k FROM i WHERE i.k > 100) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k FROM i WHERE i.val > 100) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT o.k FROM i WHERE i.val > 100) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k FROM i WHERE i.k > 100) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k FROM i WHERE i.k > 100) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k FROM i WHERE i.k > 100) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k FROM i WHERE i.k > 3) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k FROM i WHERE i.k >= 3) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k FROM i WHERE i.k < 1) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k FROM i WHERE i.k = 7) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k FROM i WHERE i.n > 100) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k FROM i WHERE i.k IS NULL) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k FROM i WHERE i.k BETWEEN 50 AND 60) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k FROM i WHERE i.k IN (8, 9)) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k FROM i WHERE i.k > 100 OR i.val > 100) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k FROM i WHERE i.k > 100 OR i.val > 25) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id, o.k IN (SELECT o.k FROM i WHERE i.k > 100) FROM o ORDER BY id",
                "1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false"},
            {"SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.k > 100 AND i.k + o.k > 0) ORDER BY "
                + "id",
                ""},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k FROM i WHERE i.k > 100 AND i.k = o.k) ORDER BY id",
                ""},
        });
    }
}
