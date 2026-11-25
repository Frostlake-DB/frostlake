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
 * Row-value {@code NOT IN} and quantified comparisons over a query, measured on a live account: over a query that
 * reads the outer row each pair of rows compares as two written-out rows do and the NOT IN keeps the three values;
 * over one that reads nothing of it the set is matched as a whole, a NULL in the row or in any row of the set
 * leaving the NOT IN not TRUE, and such a NOT IN over a query one of whose columns holds a NULL is refused anywhere
 * but as a filter. {@code = ANY} and {@code IN} stay two-valued; {@code <> ALL} answers as NOT IN does.
 */
public class RowValueNotInSubqueryTest extends MeasuredCellsTestSupport {

    @Test
    public void uncorrelatedAndCorrelated() {
        engine.execute("CREATE OR REPLACE TABLE o (id INT, k INT, n NUMBER(10,2), f FLOAT, s VARCHAR, d DATE, ts"
            + " TIMESTAMP_NTZ, b BOOLEAN, v VARIANT, a ARRAY, ob OBJECT)");
        engine.execute("INSERT INTO o SELECT 1, 1, 1.50, 1.5, 'a', '2020-01-01'::DATE, '2020-01-01 "
            + "10:00:00'::TIMESTAMP_NTZ, TRUE, TO_VARIANT(1), ARRAY_CONSTRUCT(1,2), "
            + "OBJECT_CONSTRUCT('k',1) UNION ALL SELECT 2, 2, 2.00, 2.0, 'B', '2020-01-02'::DATE, "
            + "'2020-01-02 00:00:00'::TIMESTAMP_NTZ, FALSE, TO_VARIANT('x'), ARRAY_CONSTRUCT(2), "
            + "OBJECT_CONSTRUCT('k',2) UNION ALL SELECT 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, "
            + "NULL, NULL, NULL UNION ALL SELECT 4, 2, 2.00, 2.0, 'b', '2020-01-03'::DATE, '2020-01-03 "
            + "12:30:00'::TIMESTAMP_NTZ, TRUE, TO_VARIANT(2), ARRAY_CONSTRUCT(3,NULL), "
            + "OBJECT_CONSTRUCT() UNION ALL SELECT 5, 5, 5.25, 5.25, 'e', '2020-01-05'::DATE, "
            + "'2020-01-05 00:00:00'::TIMESTAMP_NTZ, NULL, PARSE_JSON('null'), ARRAY_CONSTRUCT(), "
            + "OBJECT_CONSTRUCT('z',NULL)");
        engine.execute("CREATE OR REPLACE TABLE i (k INT, n NUMBER(10,2), f FLOAT, s VARCHAR, d DATE, ts "
            + "TIMESTAMP_NTZ, b BOOLEAN, v VARIANT, val INT)");
        engine.execute("INSERT INTO i SELECT 1, 1.50, 1.5, 'A', '2020-01-01'::DATE, '2020-01-01 "
            + "10:00:00'::TIMESTAMP_NTZ, TRUE, TO_VARIANT(1), 10 UNION ALL SELECT 2, 2.00, 2.0, 'b', "
            + "'2020-01-02'::DATE, '2020-01-02 00:00:00'::TIMESTAMP_NTZ, FALSE, TO_VARIANT('x'), 20 "
            + "UNION ALL SELECT 2, 2.10, 2.1, 'b', '2020-01-04'::DATE, '2020-01-04 "
            + "00:00:00'::TIMESTAMP_NTZ, TRUE, TO_VARIANT(2), 21 UNION ALL SELECT NULL, NULL, NULL, "
            + "NULL, NULL, NULL, NULL, NULL, 30 UNION ALL SELECT 3, 3.00, 3.0, 'c', '2020-01-03'::DATE,"
            + " '2020-01-03 12:30:00'::TIMESTAMP_NTZ, NULL, TO_VARIANT(3), NULL");
        engine.execute("CREATE OR REPLACE TABLE e (k INT, n NUMBER(10,2), f FLOAT, s VARCHAR, d DATE, ts "
            + "TIMESTAMP_NTZ, b BOOLEAN, v VARIANT, val INT)");
        engine.execute("CREATE OR REPLACE TABLE one (k INT, val INT)");
        engine.execute("INSERT INTO one VALUES (2, 7)");
        engine.execute("CREATE OR REPLACE TABLE nn (k INT)");
        engine.execute("INSERT INTO nn VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE TABLE wn (k INT)");
        engine.execute("INSERT INTO wn VALUES (1), (NULL)");
        engine.execute("CREATE OR REPLACE TABLE p (id INT, a INT, b VARCHAR, c INT)");
        engine.execute("INSERT INTO p VALUES (1, 1, 'x', 10), (2, 2, 'y', 20), (3, NULL, 'x', 10), (4, 1, NULL, "
            + "10), (5, NULL, NULL, NULL), (6, 3, 'z', 30), (7, 1, 'x', NULL), (8, NULL, 'q', 20), (9, "
            + "9, NULL, 99)");
        engine.execute("CREATE OR REPLACE TABLE q (g INT, k INT, s VARCHAR, v INT)");
        engine.execute("INSERT INTO q VALUES (0, 1, 'x', 10), (0, 2, 'y', 20), (1, 1, 'x', 10), (1, NULL, 'y', "
            + "20), (2, 1, 'x', 10), (2, 2, NULL, 20), (3, NULL, NULL, NULL), (5, 1, NULL, 10), (6, "
            + "NULL, 'x', 10)");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 5|false ;; 6|false ;; 7|true ;; 8|false ;; "
            + "9|false",
            "SELECT id, (a, b) IN (SELECT k, s FROM q WHERE g = 0) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 26",
            "SELECT id, (a, b) NOT IN (SELECT k, s FROM q WHERE g = 0) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 5|false ;; 6|false ;; 7|true ;; 8|false ;; "
            + "9|false",
            "SELECT id, (a, b) = ANY (SELECT k, s FROM q WHERE g = 0) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 21",
            "SELECT id, (a, b) <> ALL (SELECT k, s FROM q WHERE g = 0) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 5|false ;; 6|false ;; 7|false ;; 8|false ;; "
            + "9|false",
            "SELECT id, (a, b, c) IN (SELECT k, s, v FROM q WHERE g = 0) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 29",
            "SELECT id, (a, b, c) NOT IN (SELECT k, s, v FROM q WHERE g = 0) FROM p ORDER BY id");
        assertAnswer("6",
            "SELECT id FROM p WHERE (a, b) NOT IN (SELECT k, s FROM q WHERE g = 0) ORDER BY id");
        assertAnswer("1 ;; 2 ;; 7",
            "SELECT id FROM p WHERE (a, b) IN (SELECT k, s FROM q WHERE g = 0) ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 43",
            "SELECT id FROM p WHERE NOT ((a, b) NOT IN (SELECT k, s FROM q WHERE g = 0)) ORDER BY id");
        assertAnswer("6",
            "SELECT id FROM p WHERE (a, b, c) NOT IN (SELECT k, s, v FROM q WHERE g = 0) ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|false ;; 4|false ;; 5|false ;; 6|false ;; 7|true ;; 8|false ;; "
            + "9|false",
            "SELECT id, (a, b) IN (SELECT k, s FROM q WHERE g = 1) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 26",
            "SELECT id, (a, b) NOT IN (SELECT k, s FROM q WHERE g = 1) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|false ;; 4|false ;; 5|false ;; 6|false ;; 7|true ;; 8|false ;; "
            + "9|false",
            "SELECT id, (a, b) = ANY (SELECT k, s FROM q WHERE g = 1) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 21",
            "SELECT id, (a, b) <> ALL (SELECT k, s FROM q WHERE g = 1) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|false ;; 4|false ;; 5|false ;; 6|false ;; 7|false ;; 8|false ;; "
            + "9|false",
            "SELECT id, (a, b, c) IN (SELECT k, s, v FROM q WHERE g = 1) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 29",
            "SELECT id, (a, b, c) NOT IN (SELECT k, s, v FROM q WHERE g = 1) FROM p ORDER BY id");
        assertAnswer("",
            "SELECT id FROM p WHERE (a, b) NOT IN (SELECT k, s FROM q WHERE g = 1) ORDER BY id");
        assertAnswer("1 ;; 7",
            "SELECT id FROM p WHERE (a, b) IN (SELECT k, s FROM q WHERE g = 1) ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 43",
            "SELECT id FROM p WHERE NOT ((a, b) NOT IN (SELECT k, s FROM q WHERE g = 1)) ORDER BY id");
        assertAnswer("",
            "SELECT id FROM p WHERE (a, b, c) NOT IN (SELECT k, s, v FROM q WHERE g = 1) ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|false ;; 4|false ;; 5|false ;; 6|false ;; 7|true ;; 8|false ;; "
            + "9|false",
            "SELECT id, (a, b) IN (SELECT k, s FROM q WHERE g = 2) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 26",
            "SELECT id, (a, b) NOT IN (SELECT k, s FROM q WHERE g = 2) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|false ;; 4|false ;; 5|false ;; 6|false ;; 7|true ;; 8|false ;; "
            + "9|false",
            "SELECT id, (a, b) = ANY (SELECT k, s FROM q WHERE g = 2) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 21",
            "SELECT id, (a, b) <> ALL (SELECT k, s FROM q WHERE g = 2) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|false ;; 4|false ;; 5|false ;; 6|false ;; 7|false ;; 8|false ;; "
            + "9|false",
            "SELECT id, (a, b, c) IN (SELECT k, s, v FROM q WHERE g = 2) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 29",
            "SELECT id, (a, b, c) NOT IN (SELECT k, s, v FROM q WHERE g = 2) FROM p ORDER BY id");
        assertAnswer("",
            "SELECT id FROM p WHERE (a, b) NOT IN (SELECT k, s FROM q WHERE g = 2) ORDER BY id");
        assertAnswer("1 ;; 7",
            "SELECT id FROM p WHERE (a, b) IN (SELECT k, s FROM q WHERE g = 2) ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 43",
            "SELECT id FROM p WHERE NOT ((a, b) NOT IN (SELECT k, s FROM q WHERE g = 2)) ORDER BY id");
        assertAnswer("",
            "SELECT id FROM p WHERE (a, b, c) NOT IN (SELECT k, s, v FROM q WHERE g = 2) ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false ;; 6|false ;; 7|false ;; 8|false ;; "
            + "9|false",
            "SELECT id, (a, b) IN (SELECT k, s FROM q WHERE g = 3) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 26",
            "SELECT id, (a, b) NOT IN (SELECT k, s FROM q WHERE g = 3) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false ;; 6|false ;; 7|false ;; 8|false ;; "
            + "9|false",
            "SELECT id, (a, b) = ANY (SELECT k, s FROM q WHERE g = 3) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 21",
            "SELECT id, (a, b) <> ALL (SELECT k, s FROM q WHERE g = 3) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false ;; 6|false ;; 7|false ;; 8|false ;; "
            + "9|false",
            "SELECT id, (a, b, c) IN (SELECT k, s, v FROM q WHERE g = 3) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 29",
            "SELECT id, (a, b, c) NOT IN (SELECT k, s, v FROM q WHERE g = 3) FROM p ORDER BY id");
        assertAnswer("",
            "SELECT id FROM p WHERE (a, b) NOT IN (SELECT k, s FROM q WHERE g = 3) ORDER BY id");
        assertAnswer("",
            "SELECT id FROM p WHERE (a, b) IN (SELECT k, s FROM q WHERE g = 3) ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 43",
            "SELECT id FROM p WHERE NOT ((a, b) NOT IN (SELECT k, s FROM q WHERE g = 3)) ORDER BY id");
        assertAnswer("",
            "SELECT id FROM p WHERE (a, b, c) NOT IN (SELECT k, s, v FROM q WHERE g = 3) ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false ;; 6|false ;; 7|false ;; 8|false ;; "
            + "9|false",
            "SELECT id, (a, b) IN (SELECT k, s FROM q WHERE g = 4) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 26",
            "SELECT id, (a, b) NOT IN (SELECT k, s FROM q WHERE g = 4) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false ;; 6|false ;; 7|false ;; 8|false ;; "
            + "9|false",
            "SELECT id, (a, b) = ANY (SELECT k, s FROM q WHERE g = 4) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 21",
            "SELECT id, (a, b) <> ALL (SELECT k, s FROM q WHERE g = 4) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false ;; 6|false ;; 7|false ;; 8|false ;; "
            + "9|false",
            "SELECT id, (a, b, c) IN (SELECT k, s, v FROM q WHERE g = 4) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 29",
            "SELECT id, (a, b, c) NOT IN (SELECT k, s, v FROM q WHERE g = 4) FROM p ORDER BY id");
        assertAnswer("1 ;; 2 ;; 3 ;; 4 ;; 5 ;; 6 ;; 7 ;; 8 ;; 9",
            "SELECT id FROM p WHERE (a, b) NOT IN (SELECT k, s FROM q WHERE g = 4) ORDER BY id");
        assertAnswer("",
            "SELECT id FROM p WHERE (a, b) IN (SELECT k, s FROM q WHERE g = 4) ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 43",
            "SELECT id FROM p WHERE NOT ((a, b) NOT IN (SELECT k, s FROM q WHERE g = 4)) ORDER BY id");
        assertAnswer("1 ;; 2 ;; 3 ;; 4 ;; 5 ;; 6 ;; 7 ;; 8 ;; 9",
            "SELECT id FROM p WHERE (a, b, c) NOT IN (SELECT k, s, v FROM q WHERE g = 4) ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false ;; 6|false ;; 7|false ;; 8|false ;; "
            + "9|false",
            "SELECT id, (a, b) IN (SELECT k, s FROM q WHERE g = 5) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 26",
            "SELECT id, (a, b) NOT IN (SELECT k, s FROM q WHERE g = 5) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false ;; 6|false ;; 7|false ;; 8|false ;; "
            + "9|false",
            "SELECT id, (a, b) = ANY (SELECT k, s FROM q WHERE g = 5) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 21",
            "SELECT id, (a, b) <> ALL (SELECT k, s FROM q WHERE g = 5) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false ;; 6|false ;; 7|false ;; 8|false ;; "
            + "9|false",
            "SELECT id, (a, b, c) IN (SELECT k, s, v FROM q WHERE g = 5) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 29",
            "SELECT id, (a, b, c) NOT IN (SELECT k, s, v FROM q WHERE g = 5) FROM p ORDER BY id");
        assertAnswer("",
            "SELECT id FROM p WHERE (a, b) NOT IN (SELECT k, s FROM q WHERE g = 5) ORDER BY id");
        assertAnswer("",
            "SELECT id FROM p WHERE (a, b) IN (SELECT k, s FROM q WHERE g = 5) ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 43",
            "SELECT id FROM p WHERE NOT ((a, b) NOT IN (SELECT k, s FROM q WHERE g = 5)) ORDER BY id");
        assertAnswer("",
            "SELECT id FROM p WHERE (a, b, c) NOT IN (SELECT k, s, v FROM q WHERE g = 5) ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false ;; 6|false ;; 7|false ;; 8|false ;; "
            + "9|false",
            "SELECT id, (a, b) IN (SELECT k, s FROM q WHERE g = 6) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 26",
            "SELECT id, (a, b) NOT IN (SELECT k, s FROM q WHERE g = 6) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false ;; 6|false ;; 7|false ;; 8|false ;; "
            + "9|false",
            "SELECT id, (a, b) = ANY (SELECT k, s FROM q WHERE g = 6) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 21",
            "SELECT id, (a, b) <> ALL (SELECT k, s FROM q WHERE g = 6) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false ;; 6|false ;; 7|false ;; 8|false ;; "
            + "9|false",
            "SELECT id, (a, b, c) IN (SELECT k, s, v FROM q WHERE g = 6) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 29",
            "SELECT id, (a, b, c) NOT IN (SELECT k, s, v FROM q WHERE g = 6) FROM p ORDER BY id");
        assertAnswer("",
            "SELECT id FROM p WHERE (a, b) NOT IN (SELECT k, s FROM q WHERE g = 6) ORDER BY id");
        assertAnswer("",
            "SELECT id FROM p WHERE (a, b) IN (SELECT k, s FROM q WHERE g = 6) ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 43",
            "SELECT id FROM p WHERE NOT ((a, b) NOT IN (SELECT k, s FROM q WHERE g = 6)) ORDER BY id");
        assertAnswer("",
            "SELECT id FROM p WHERE (a, b, c) NOT IN (SELECT k, s, v FROM q WHERE g = 6) ORDER BY id");
        assertAnswer("1|false ;; 2|null ;; 3|null ;; 4|null ;; 5|true ;; 6|true ;; 7|true ;; 8|null ;; 9|true",
            "SELECT id, (a, b) NOT IN (SELECT k, s FROM q WHERE q.v = p.c AND g IN (1, 2)) FROM p "
            + "ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|false ;; 4|false ;; 5|false ;; 6|false ;; 7|false ;; 8|false ;; "
            + "9|false",
            "SELECT id, (a, b) IN (SELECT k, s FROM q WHERE q.v = p.c AND g IN (1, 2)) FROM p ORDER "
            + "BY id");
        assertAnswer("5 ;; 6 ;; 7 ;; 8 ;; 9",
            "SELECT id FROM p WHERE (a, b) NOT IN (SELECT k, s FROM q WHERE q.v = p.c AND g = 0) "
            + "ORDER BY id");
        assertAnswer("1 ;; 2 ;; 3 ;; 4 ;; 5 ;; 6 ;; 7 ;; 8 ;; 9",
            "SELECT id FROM p WHERE (a, b) NOT IN (SELECT k, s FROM q WHERE q.v = p.c AND g = 3) "
            + "ORDER BY id");
        assertAnswer("1|false ;; 2|null ;; 3|null ;; 4|null ;; 5|true ;; 6|true ;; 7|null ;; 8|true ;; 9|true",
            "SELECT id, (b, a) NOT IN (SELECT s, k FROM q WHERE q.g = p.id - 1) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|null ;; 4|null ;; 5|true ;; 6|true ;; 7|true ;; 8|null ;; 9|true",
            "SELECT id, (a, b) <> ALL (SELECT k, s FROM q WHERE q.v = p.c) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|false ;; 5|false ;; 6|false ;; 7|false ;; 8|false ;; "
            + "9|false",
            "SELECT id, (a, b) = ANY (SELECT k, s FROM q WHERE q.v = p.c) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|null ;; 4|null ;; 5|null ;; 6|false ;; 7|true ;; 8|false ;; "
            + "9|false",
            "SELECT id, (a, b) IN ((1, 'x'), (2, 'y')) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|null ;; 4|null ;; 5|null ;; 6|true ;; 7|false ;; 8|true ;; "
            + "9|true",
            "SELECT id, (a, b) NOT IN ((1, 'x'), (2, 'y')) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|null ;; 3|null ;; 4|null ;; 5|null ;; 6|true ;; 7|false ;; 8|true ;; 9|null",
            "SELECT id, (a, b) NOT IN ((1, 'x'), (NULL, 'y')) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|null ;; 3|null ;; 4|null ;; 5|null ;; 6|true ;; 7|false ;; 8|null ;; 9|true",
            "SELECT id, (a, b) NOT IN ((1, 'x'), (2, NULL)) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|null ;; 3|null ;; 4|null ;; 5|null ;; 6|false ;; 7|true ;; 8|null ;; 9|false",
            "SELECT id, (a, b) IN ((1, 'x'), (2, NULL)) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|null ;; 4|null ;; 5|null ;; 6|false ;; 7|true ;; 8|false ;; "
            + "9|false",
            "SELECT id, (a, b) = (1, 'x') FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|null ;; 4|null ;; 5|null ;; 6|true ;; 7|false ;; 8|true ;; 9|true",
            "SELECT id, (a, b) <> (1, 'x') FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|null ;; 3|null ;; 4|null ;; 5|null ;; 6|true ;; 7|null ;; 8|true ;; 9|true",
            "SELECT id, (a, b, c) NOT IN ((1, 'x', 10), (2, 'y', NULL)) FROM p ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t2 AS SELECT * FROM p");
        engine.execute("DELETE FROM t2 WHERE (a, b) NOT IN (SELECT k, s FROM q WHERE g = 0)");
        assertAnswer("1 ;; 2 ;; 3 ;; 4 ;; 5 ;; 7 ;; 8 ;; 9",
            "SELECT id FROM t2 ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t2 AS SELECT * FROM p");
        engine.execute("DELETE FROM t2 WHERE (a, b) NOT IN (SELECT k, s FROM q WHERE g = 2)");
        assertAnswer("1 ;; 2 ;; 3 ;; 4 ;; 5 ;; 6 ;; 7 ;; 8 ;; 9",
            "SELECT id FROM t2 ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t2 AS SELECT * FROM p");
        engine.execute("UPDATE t2 SET c = -1 WHERE (a, b) NOT IN (SELECT k, s FROM q WHERE g = 1)");
        assertAnswer("1|10 ;; 2|20 ;; 3|10 ;; 4|10 ;; 5|null ;; 6|30 ;; 7|null ;; 8|20 ;; 9|99",
            "SELECT id, c FROM t2 ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t2 AS SELECT * FROM p");
        engine.execute("UPDATE t2 SET c = -1 WHERE (a, b) IN (SELECT k, s FROM q WHERE g = 2)");
        assertAnswer("1|-1 ;; 2|20 ;; 3|10 ;; 4|10 ;; 5|null ;; 6|30 ;; 7|-1 ;; 8|20 ;; 9|99",
            "SELECT id, c FROM t2 ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t2 AS SELECT * FROM p");
        engine.execute("DELETE FROM t2 WHERE (a, b) NOT IN (SELECT k, s FROM q WHERE g = 4)");
        assertAnswer("",
            "SELECT id FROM t2 ORDER BY id");
        engine.execute("DELETE FROM t2 WHERE (t2.a, t2.b) NOT IN (SELECT k, s FROM q WHERE q.v = t2.c AND g = 0)");
        assertAnswer("",
            "SELECT id FROM t2 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 142 | invalid identifier 'X.ID'",
            "EXECUTE IMMEDIATE $$DECLARE r VARCHAR DEFAULT ''; BEGIN FOR x IN (SELECT id FROM p WHERE"
            + " (a, b) NOT IN (SELECT k, s FROM q WHERE g = 0) ORDER BY id) DO r := r || x.id || ','; "
            + "END FOR; RETURN r; END$$");
        assertAnswer("0",
            "EXECUTE IMMEDIATE $$BEGIN DELETE FROM t2 WHERE (a, b) NOT IN (SELECT k, s FROM q WHERE g"
            + " = 1); RETURN SQLROWCOUNT; END$$");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("DELETE FROM t WHERE (t.k, t.s) NOT IN (SELECT k, s FROM i WHERE k IS NOT NULL)");
        assertAnswer("3 ;; 4",
            "SELECT id FROM t ORDER BY id");
        assertAnswer("",
            "SELECT id FROM o WHERE (o.k, o.s) NOT IN (SELECT k, s FROM i) ORDER BY id");
        assertAnswer("1 ;; 2 ;; 5",
            "SELECT id FROM o WHERE (o.k, o.s) NOT IN (SELECT k, s FROM i WHERE k IS NOT NULL) ORDER "
            + "BY id");
        assertAnswer("",
            "SELECT id FROM o WHERE (o.k, o.s) NOT IN (SELECT column1, column2 FROM VALUES (NULL, "
            + "NULL)) ORDER BY id");
        assertAnswer("",
            "SELECT id FROM o WHERE (o.k, o.s) NOT IN (SELECT column1, column2 FROM VALUES (2, NULL))"
            + " ORDER BY id");
        assertAnswer("1 ;; 2 ;; 5",
            "SELECT id FROM o WHERE (o.k, o.s) NOT IN (SELECT column1, column2 FROM VALUES (2, 'b')) "
            + "ORDER BY id");
    }

    @Test
    public void contexts() {
        engine.execute("CREATE OR REPLACE TABLE p (id INT, a INT, b VARCHAR, c INT)");
        engine.execute("INSERT INTO p VALUES (1, 1, 'x', 10), (2, 2, 'y', 20), (3, NULL, 'x', 10), (4, 1, NULL, "
            + "10), (5, NULL, NULL, NULL), (6, 3, 'z', 30), (7, 1, 'x', NULL), (8, NULL, 'q', 20), (9, "
            + "9, NULL, 99)");
        engine.execute("CREATE OR REPLACE TABLE q (g INT, k INT, s VARCHAR, v INT)");
        engine.execute("INSERT INTO q VALUES (0, 1, 'x', 10), (0, 2, 'y', 20), (1, 1, 'x', 10), (1, NULL, 'y', "
            + "20), (2, 1, 'x', 10), (2, 2, NULL, 20), (3, NULL, NULL, NULL), (5, 1, NULL, 10), (6, "
            + "NULL, 'x', 10)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 38",
            "SELECT id FROM p WHERE (a, b) NOT IN (SELECT k, s FROM q WHERE g = 0) OR id = 1 ORDER BY"
            + " id");
        assertAnswer("6",
            "SELECT id FROM p WHERE (a, b) NOT IN (SELECT k, s FROM q WHERE g = 0) AND id > 1 ORDER "
            + "BY id");
        assertAnswer("6",
            "SELECT id FROM p WHERE id > 1 AND ((a, b) NOT IN (SELECT k, s FROM q WHERE g = 0)) ORDER"
            + " BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 48",
            "SELECT id FROM p WHERE CASE WHEN (a, b) NOT IN (SELECT k, s FROM q WHERE g = 0) THEN "
            + "TRUE ELSE FALSE END ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 42",
            "SELECT id FROM p WHERE IFF((a, b) NOT IN (SELECT k, s FROM q WHERE g = 0), TRUE, FALSE) "
            + "ORDER BY id");
        assertAnswer("6",
            "SELECT p.id FROM p JOIN q ON (p.a, p.b) NOT IN (SELECT k, s FROM q WHERE g = 0) AND q.g "
            + "= 0 AND q.k = 1 ORDER BY p.id");
        assertAnswer("2|1 ;; 3|1 ;; 9|1",
            "SELECT a, COUNT(*) FROM p GROUP BY a HAVING (a, 'x') NOT IN (SELECT k, s FROM q WHERE g "
            + "= 0) ORDER BY a");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 17 | found QUALIFY clause but no"
            + " window function.",
            "SELECT id FROM p QUALIFY (a, b) NOT IN (SELECT k, s FROM q WHERE g = 0) ORDER BY id");
        assertAnswer("6",
            "SELECT id FROM (SELECT * FROM p WHERE (a, b) NOT IN (SELECT k, s FROM q WHERE g = 0)) "
            + "ORDER BY id");
        assertAnswer("6",
            "WITH c AS (SELECT * FROM p WHERE (a, b) NOT IN (SELECT k, s FROM q WHERE g = 0)) SELECT "
            + "id FROM c ORDER BY id");
        assertAnswer("6",
            "SELECT id FROM p WHERE (a, b) <> ALL (SELECT k, s FROM q WHERE g = 0) ORDER BY id");
        assertAnswer("3 ;; 4 ;; 5 ;; 6 ;; 8 ;; 9",
            "SELECT id FROM p WHERE NOT ((a, b) IN (SELECT k, s FROM q WHERE g = 0)) ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|true ;; 4|true ;; 5|true ;; 6|true ;; 7|false ;; 8|true ;; "
            + "9|true",
            "SELECT id, NOT ((a, b) IN (SELECT k, s FROM q WHERE g = 0)) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 21",
            "SELECT id, a NOT IN (SELECT k FROM q WHERE g = 1) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 38",
            "SELECT id FROM p WHERE (a, b) NOT IN (SELECT k, s FROM q WHERE g = 0) = TRUE ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 39",
            "SELECT id FROM p WHERE ((a, b) NOT IN (SELECT k, s FROM q WHERE g = 0)) IS NULL ORDER BY"
            + " id");
        assertAnswer("6",
            "SELECT id FROM p WHERE (a, b) NOT IN (SELECT k, s FROM q WHERE g = 0) AND (a, c) NOT IN "
            + "(SELECT k, v FROM q WHERE g = 2) ORDER BY id");
        assertAnswer("1",
            "SELECT COUNT(*) FROM p WHERE (a, b) NOT IN (SELECT k, s FROM q WHERE g = 0)");
        assertAnswer("2 ;; 6 ;; 9",
            "SELECT id FROM p WHERE (a, 'x') NOT IN (SELECT k, s FROM q WHERE g = 0) ORDER BY id");
        assertAnswer("",
            "SELECT id FROM p WHERE (1, 'x') NOT IN (SELECT k, s FROM q WHERE g = 0) ORDER BY id");
        assertAnswer("1 ;; 2 ;; 3 ;; 4 ;; 5 ;; 6 ;; 7 ;; 8 ;; 9",
            "SELECT id FROM p WHERE (NULL, 'x') NOT IN (SELECT k, s FROM q WHERE g = 4) ORDER BY id");
        assertAnswer("1 ;; 2 ;; 3 ;; 4 ;; 5 ;; 6 ;; 7 ;; 8 ;; 9",
            "SELECT id FROM p WHERE (a, b) NOT IN (SELECT k, s FROM q WHERE g = 9 AND k IS NULL) "
            + "ORDER BY id");
        assertAnswer("",
            "SELECT id FROM p WHERE (a, b) NOT IN (SELECT DISTINCT k, s FROM q) ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 24",
            "SELECT (1, 'z') NOT IN (SELECT k, s FROM q WHERE g = 0)");
        assertAnswer("1 ;; 2 ;; 3 ;; 4 ;; 5 ;; 6 ;; 7 ;; 8 ;; 9",
            "SELECT id FROM p WHERE (a, b) NOT IN (SELECT k, s FROM q WHERE g = 0 AND p.id > 100) "
            + "ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|null ;; 4|null ;; 5|true ;; 6|true ;; 7|true ;; 8|null ;; 9|true",
            "SELECT id, (a, b) < ALL (SELECT k, s FROM q WHERE q.v = p.c) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|true ;; 6|true ;; 7|true ;; 8|false ;; 9|true",
            "SELECT id, (a, b) = ALL (SELECT k, s FROM q WHERE q.v = p.c) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false ;; 6|false ;; 7|false ;; 8|true ;; "
            + "9|false",
            "SELECT id, (a, b) <> ANY (SELECT k, s FROM q WHERE q.v = p.c) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false ;; 6|false ;; 7|false ;; 8|false ;; "
            + "9|false",
            "SELECT id, (a, b) > ANY (SELECT k, s FROM q WHERE q.v = p.c) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|true ;; 6|true ;; 7|true ;; 8|null ;; 9|true",
            "SELECT id, (a, b) >= ALL (SELECT k, s FROM q WHERE q.v = p.c) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|null ;; 4|null ;; 5|true ;; 6|true ;; 7|true ;; 8|null ;; 9|true",
            "SELECT id, (a, b, c) NOT IN (SELECT k, s, v FROM q WHERE q.v = p.c) FROM p ORDER BY id");
        assertAnswer("5 ;; 6 ;; 8 ;; 9",
            "SELECT id FROM p WHERE (a, b, c) NOT IN (SELECT k, s, v FROM q WHERE q.g = p.id - 1) "
            + "ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true ;; 6|true ;; 7|true ;; 8|true ;; 9|true",
            "SELECT id, (a, b) NOT IN (SELECT k, s FROM q WHERE q.g = 4 AND q.v = p.c) FROM p ORDER "
            + "BY id");
        assertAnswer("1|true ;; 2|true ;; 3|null ;; 4|null ;; 5|false ;; 6|false ;; 7|false ;; 8|null ;; "
            + "9|false",
            "SELECT id, NOT ((a, b) NOT IN (SELECT k, s FROM q WHERE q.v = p.c)) FROM p ORDER BY id");
        assertAnswer("1 ;; 2",
            "SELECT id FROM p WHERE NOT ((a, b) NOT IN (SELECT k, s FROM q WHERE q.v = p.c)) ORDER BY"
            + " id");
        assertAnswer("3 ;; 4 ;; 8",
            "SELECT id FROM p WHERE ((a, b) NOT IN (SELECT k, s FROM q WHERE q.v = p.c)) IS NULL "
            + "ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 26",
            "SELECT id, (a, b) NOT IN (SELECT k, s FROM q WHERE q.g = 6 AND q.k IS NULL AND p.id > 0)"
            + " FROM p ORDER BY id");
    }

    @Test
    public void refusalsAndQuantifiers() {
        engine.execute("CREATE OR REPLACE TABLE p (id INT, a INT, b VARCHAR, c INT)");
        engine.execute("INSERT INTO p VALUES (1, 1, 'x', 10), (2, 2, 'y', 20), (3, NULL, 'x', 10), (4, 1, NULL, "
            + "10), (5, NULL, NULL, NULL), (6, 3, 'z', 30), (7, 1, 'x', NULL), (8, NULL, 'q', 20), (9, "
            + "9, NULL, 99)");
        engine.execute("CREATE OR REPLACE TABLE q (g INT, k INT, s VARCHAR, v INT)");
        engine.execute("INSERT INTO q VALUES (0, 1, 'x', 10), (0, 2, 'y', 20), (1, 1, 'x', 10), (1, NULL, 'y', "
            + "20), (2, 1, 'x', 10), (2, 2, NULL, 20), (3, NULL, NULL, NULL), (5, 1, NULL, 10), (6, "
            + "NULL, 'x', 10)");
        engine.execute("CREATE OR REPLACE TABLE qn (g INT NOT NULL, k INT NOT NULL, s VARCHAR NOT NULL)");
        engine.execute("INSERT INTO qn VALUES (0, 1, 'x'), (0, 2, 'y')");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false ;; 6|true ;; 7|false ;; 8|false ;; "
            + "9|false",
            "SELECT id, (a, b) NOT IN (SELECT k, s FROM qn) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|false ;; 4|false ;; 5|false ;; 6|true ;; 7|false ;; 8|false ;; "
            + "9|false",
            "SELECT id, (a, b) NOT IN (SELECT 1, 'x') FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true ;; 6|true ;; 7|true ;; 8|true ;; 9|true",
            "SELECT id, (a, b) NOT IN (SELECT k, s FROM q WHERE 1 = 0) FROM p ORDER BY id");
        assertAnswer("1 ;; 6",
            "SELECT id FROM p WHERE (a, b) NOT IN (SELECT k, s FROM qn) OR id = 1 ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false ;; 6|true ;; 7|false ;; 8|false ;; "
            + "9|false",
            "SELECT id, (a, b) NOT IN (SELECT k, s FROM qn WHERE k IS NOT NULL) FROM p ORDER BY id");
        assertAnswer("6",
            "SELECT id FROM p WHERE (a, b) NOT IN (SELECT k, s FROM qn) ORDER BY id");
        assertAnswer("1 ;; 5 ;; 6 ;; 7 ;; 9",
            "SELECT id FROM p WHERE (a, b) NOT IN (SELECT k, s FROM q WHERE q.v = p.c) OR id = 1 "
            + "ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|T ;; 6|T ;; 7|T ;; 8|null ;; 9|T",
            "SELECT id, CASE WHEN (a, b) NOT IN (SELECT k, s FROM q WHERE q.v = p.c) THEN 'T' END "
            + "FROM p ORDER BY id");
        assertAnswer("1 ;; 2 ;; 3 ;; 7",
            "SELECT id FROM p WHERE (a, b) IN (SELECT k, s FROM q WHERE g = 0) OR id = 3 ORDER BY id");
        assertAnswer("",
            "SELECT id FROM p WHERE (a, b) < ALL (SELECT k, s FROM q WHERE g = 1) ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 41",
            "SELECT id FROM p ORDER BY (a, b) NOT IN (SELECT k, s FROM q WHERE g = 0), id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|false ;; 5|false ;; 6|true ;; 7|true ;; 8|true ;; 9|true",
            "SELECT id, (a, b) <> ANY (SELECT k, s FROM q WHERE g = 1) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|false ;; 4|false ;; 5|false ;; 6|true ;; 7|false ;; 8|false ;; "
            + "9|true",
            "SELECT id, (a, b) > ANY (SELECT k, s FROM q WHERE g = 1) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false ;; 6|false ;; 7|false ;; 8|false ;; "
            + "9|false",
            "SELECT id, (a, b) IN (SELECT k, s FROM q WHERE q.v > p.c) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|true ;; 4|null ;; 5|true ;; 6|true ;; 7|true ;; 8|true ;; 9|true",
            "SELECT id, (a, b) NOT IN (SELECT k, s FROM q WHERE q.v = p.c AND q.k > p.a - 5) FROM p "
            + "ORDER BY id");
        assertAnswer("1 ;; 2 ;; 5 ;; 6 ;; 7 ;; 8 ;; 9",
            "SELECT id FROM p WHERE (a, b) NOT IN (SELECT k, s FROM q WHERE q.v > p.c) ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|null ;; 4|null ;; 5|true ;; 6|true ;; 7|true ;; 8|true ;; 9|true",
            "SELECT id, (a, b) <> ALL (SELECT k, s FROM q WHERE q.v > p.c) FROM p ORDER BY id");
        assertAnswer("",
            "SELECT id FROM p WHERE (a, b) <> ALL (SELECT k, s FROM q WHERE g = 2) ORDER BY id");
        assertAnswer("",
            "SELECT id FROM p WHERE (a, b) < ALL (SELECT k, s FROM q WHERE g = 0) ORDER BY id");
        assertAnswer("1 ;; 7",
            "SELECT id FROM p WHERE (a, b) = ALL (SELECT k, s FROM q WHERE g = 0 AND k = 1) ORDER BY "
            + "id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 26",
            "SELECT id, (a, b) NOT IN (SELECT k, s FROM q WHERE g = 0) AS r FROM p WHERE id = 1");
        assertAnswer("6",
            "SELECT id FROM p WHERE id IN (1, 6) AND (a, b) NOT IN (SELECT k, s FROM q WHERE g = 0) "
            + "ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 40",
            "UPDATE p SET c = c WHERE (a, b) NOT IN (SELECT k, s FROM q WHERE g = 0) OR id = 1");
        engine.execute("DELETE FROM p WHERE (a, b) NOT IN (SELECT k, s FROM q WHERE g = 0) OR id = 100");
    }

    @Test
    public void edges() {
        engine.execute("CREATE OR REPLACE TABLE p (id INT, a INT, b VARCHAR, c INT)");
        engine.execute("INSERT INTO p VALUES (1, 1, 'x', 10), (2, 2, 'y', 20), (3, NULL, 'x', 10), (4, 1, NULL, "
            + "10), (5, NULL, NULL, NULL), (6, 3, 'z', 30), (7, 1, 'x', NULL), (8, NULL, 'q', 20), (9, "
            + "9, NULL, 99)");
        engine.execute("CREATE OR REPLACE TABLE q (g INT, k INT, s VARCHAR, v INT)");
        engine.execute("INSERT INTO q VALUES (0, 1, 'x', 10), (0, 2, 'y', 20), (1, 1, 'x', 10), (1, NULL, 'y', "
            + "20), (2, 1, 'x', 10), (2, 2, NULL, 20), (3, NULL, NULL, NULL), (5, 1, NULL, 10), (6, "
            + "NULL, 'x', 10)");
        engine.execute("CREATE OR REPLACE TABLE qn (g INT NOT NULL, k INT NOT NULL, s VARCHAR NOT NULL)");
        engine.execute("INSERT INTO qn VALUES (0, 1, 'x'), (0, 2, 'y')");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 17",
            "SELECT 1 NOT IN (SELECT k FROM q WHERE g = 1)");
        assertAnswer("false",
            "SELECT (1, 'x') NOT IN (SELECT k, s FROM qn)");
        assertAnswer("6",
            "SELECT id FROM p WHERE (a, b) NOT IN (SELECT k, s FROM q WHERE g = 0) OR id = 100 ORDER "
            + "BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 35",
            "DELETE FROM p WHERE (a, b) NOT IN (SELECT k, s FROM q WHERE g = 0) OR id = 5");
        assertAnswer("1 ;; 2 ;; 3 ;; 4 ;; 5 ;; 7 ;; 8 ;; 9",
            "SELECT id FROM p WHERE NOT ((a, b) NOT IN (SELECT k, s FROM qn)) ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true ;; 6|false ;; 7|true ;; 8|true ;; 9|true",
            "SELECT id, NOT ((a, b) NOT IN (SELECT k, s FROM qn)) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false ;; 6|true ;; 7|false ;; 8|false ;; "
            + "9|false",
            "SELECT id, (a, b) <> ALL (SELECT k, s FROM qn) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 26",
            "SELECT id, (a, b) NOT IN (SELECT 1, NULL) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 64",
            "SELECT b, COUNT(*) FROM p GROUP BY b HAVING (MAX(a), b) NOT IN (SELECT k, s FROM q WHERE"
            + " g = 0) ORDER BY b");
        assertAnswer("1 ;; 2 ;; 3 ;; 4 ;; 5 ;; 7 ;; 8 ;; 9 ;; 6",
            "SELECT id FROM p ORDER BY (a, b) NOT IN (SELECT k, s FROM qn), id");
        assertAnswer("false",
            "EXECUTE IMMEDIATE $$DECLARE r BOOLEAN; BEGIN r := (1, 'x') NOT IN (SELECT k, s FROM qn);"
            + " RETURN r; END$$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL "
            + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
            + "25",
            "EXECUTE IMMEDIATE $$DECLARE r BOOLEAN; BEGIN r := 1 NOT IN (SELECT k FROM q WHERE g = "
            + "1); RETURN r; END$$");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 26",
            "SELECT id, (a, b) NOT IN (SELECT k, s FROM q WHERE g = 0 AND k IS NOT NULL AND s IS NOT "
            + "NULL) FROM p ORDER BY id");
        assertAnswer("",
            "SELECT id FROM p WHERE (a, b) NOT IN (SELECT k, s FROM q WHERE g = 0) AND (a, b) IN "
            + "(SELECT k, s FROM q WHERE g = 2) OR id = 100 ORDER BY id");
    }

    @Test
    public void literalsAndNullFreeSets() {
        engine.execute("CREATE OR REPLACE TABLE p (id INT, a INT, b VARCHAR, c INT)");
        engine.execute("INSERT INTO p VALUES (1, 1, 'x', 10), (2, 2, 'y', 20), (3, NULL, 'x', 10), (4, 1, NULL, "
            + "10), (5, NULL, NULL, NULL), (6, 3, 'z', 30), (7, 1, 'x', NULL), (8, NULL, 'q', 20), (9, "
            + "9, NULL, 99)");
        engine.execute("CREATE OR REPLACE TABLE q (g INT, k INT, s VARCHAR, v INT)");
        engine.execute("INSERT INTO q VALUES (0, 1, 'x', 10), (0, 2, 'y', 20), (1, 1, 'x', 10), (1, NULL, 'y', "
            + "20), (2, 1, 'x', 10), (2, 2, NULL, 20), (3, NULL, NULL, NULL), (5, 1, NULL, 10), (6, "
            + "NULL, 'x', 10)");
        engine.execute("CREATE OR REPLACE TABLE r (id INT, a INT, b VARCHAR, c INT)");
        engine.execute("INSERT INTO r VALUES (1, 0, NULL, 10), (2, 2, NULL, 10), (3, NULL, 'a', 10), (4, 0, 'a',"
            + " 10), (5, 5, 'z', 10)");
        engine.execute("CREATE OR REPLACE TABLE rq (k INT, s VARCHAR, v INT)");
        engine.execute("INSERT INTO rq VALUES (1, 'x', 10)");
        engine.execute("CREATE OR REPLACE TABLE rn (k INT, s VARCHAR, v INT)");
        engine.execute("INSERT INTO rn VALUES (1, NULL, 10)");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|true ;; 5|false",
            "SELECT id, (a, b) < ALL (SELECT k, s FROM rq) FROM r ORDER BY id");
        assertAnswer("4",
            "SELECT id FROM r WHERE (a, b) < ALL (SELECT k, s FROM rq) ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|true ;; 5|false",
            "SELECT id, (a, b) < ALL (SELECT k, s FROM rq WHERE rq.v = r.c) FROM r ORDER BY id");
        assertAnswer("4",
            "SELECT id FROM r WHERE (a, b) < ALL (SELECT k, s FROM rq WHERE rq.v = r.c) ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|true",
            "SELECT id, (a, b) > ALL (SELECT k, s FROM rq) FROM r ORDER BY id");
        assertAnswer("5",
            "SELECT id FROM r WHERE (a, b) > ALL (SELECT k, s FROM rq) ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|null ;; 4|true ;; 5|false",
            "SELECT id, (a, b) < ALL (SELECT k, s FROM rn) FROM r ORDER BY id");
        assertAnswer("1 ;; 4",
            "SELECT id FROM r WHERE (a, b) < ALL (SELECT k, s FROM rn) ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|false ;; 4|true ;; 5|false",
            "SELECT id, (a, b) < ANY (SELECT k, s FROM rq) FROM r ORDER BY id");
        assertAnswer("1 ;; 4",
            "SELECT id FROM r WHERE (a, b) < ANY (SELECT k, s FROM rn) ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, (a, b) = ALL (SELECT k, s FROM rq) FROM r ORDER BY id");
        assertAnswer("1 ;; 2 ;; 3 ;; 5",
            "SELECT id FROM r WHERE NOT ((a, b) < ALL (SELECT k, s FROM rq)) ORDER BY id");
        assertAnswer("5",
            "SELECT id FROM r WHERE (a, b) > ALL (SELECT k, s FROM rq WHERE rq.v = r.c) ORDER BY id");
        assertAnswer("1",
            "SELECT 1 WHERE (1,2) NOT IN (SELECT 1, NULL)");
        assertAnswer("1",
            "SELECT 1 WHERE (NULL,1) NOT IN (SELECT 1, 1)");
        assertAnswer("",
            "SELECT 1 WHERE (1,2) NOT IN (SELECT 1, 2)");
        assertAnswer("1",
            "SELECT 1 WHERE (3,2) NOT IN (SELECT 1, NULL)");
        assertAnswer("1 ;; 2 ;; 3 ;; 4 ;; 5",
            "SELECT id FROM r WHERE (1, 2) NOT IN (SELECT 1, NULL) ORDER BY id");
        assertAnswer("1 ;; 2 ;; 3 ;; 4 ;; 5",
            "SELECT id FROM r WHERE (NULL, 1) NOT IN (SELECT 1, 1) ORDER BY id");
        assertAnswer("1 ;; 2 ;; 3 ;; 4 ;; 5",
            "SELECT id FROM r WHERE (id, 2) NOT IN (SELECT 1, NULL) ORDER BY id");
        assertAnswer("1 ;; 2 ;; 3 ;; 4 ;; 5",
            "SELECT id FROM r WHERE (1, 2) NOT IN (SELECT k, v FROM rq) ORDER BY id");
        assertAnswer("",
            "SELECT id FROM r WHERE (1, NULL) NOT IN (SELECT k, s FROM rq) ORDER BY id");
        assertAnswer("1 ;; 2 ;; 4 ;; 5",
            "SELECT id FROM r WHERE (1, a) NOT IN (SELECT k, v FROM rq) ORDER BY id");
        assertAnswer("1",
            "SELECT 1 WHERE (1, 2) NOT IN (SELECT k, v FROM rn)");
        assertAnswer("",
            "SELECT 1 WHERE (NULL, 2) NOT IN (SELECT k, v FROM rq)");
        assertAnswer("1",
            "SELECT 1 WHERE (1, 2) > ALL (SELECT 0, NULL)");
        assertAnswer("1",
            "SELECT 1 WHERE (1, NULL) > ALL (SELECT 0, 1)");
    }

    @Test
    public void heldNulls() {
        engine.execute("CREATE OR REPLACE TABLE p (id INT, a INT, b VARCHAR, c INT)");
        engine.execute("INSERT INTO p VALUES (1, 1, 'x', 10), (2, 2, 'y', 20), (3, NULL, 'x', 10), (4, 1, NULL, "
            + "10), (5, NULL, NULL, NULL), (6, 3, 'z', 30), (7, 1, 'x', NULL), (8, NULL, 'q', 20), (9, "
            + "9, NULL, 99)");
        engine.execute("CREATE OR REPLACE TABLE q (g INT, k INT, s VARCHAR, v INT)");
        engine.execute("INSERT INTO q VALUES (0, 1, 'x', 10), (0, 2, 'y', 20), (1, 1, 'x', 10), (1, NULL, 'y', "
            + "20), (2, 1, 'x', 10), (2, 2, NULL, 20), (3, NULL, NULL, NULL), (5, 1, NULL, 10), (6, "
            + "NULL, 'x', 10)");
        engine.execute("CREATE OR REPLACE TABLE r (id INT, a INT, b VARCHAR, c INT)");
        engine.execute("INSERT INTO r VALUES (1, 0, NULL, 10), (2, 2, NULL, 10), (3, NULL, 'a', 10), (4, 0, 'a',"
            + " 10), (5, 5, 'z', 10)");
        engine.execute("CREATE OR REPLACE TABLE rq2 (k INT, s VARCHAR, v INT)");
        engine.execute("INSERT INTO rq2 VALUES (1, 'x', 10), (2, 'y', 20)");
        engine.execute("CREATE OR REPLACE TABLE rn2 (k INT, s VARCHAR, v INT)");
        engine.execute("INSERT INTO rn2 VALUES (1, NULL, 10), (2, 'y', 20)");
        engine.execute("CREATE OR REPLACE TABLE rq1 (k INT, s VARCHAR)");
        engine.execute("INSERT INTO rq1 VALUES (1, NULL)");
        engine.execute("CREATE OR REPLACE TABLE rd (k INT, s VARCHAR)");
        engine.execute("INSERT INTO rd VALUES (1, NULL), (2, 'y')");
        engine.execute("DELETE FROM rd WHERE s IS NULL");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|true ;; 5|true",
            "SELECT id, (a, b) NOT IN (SELECT k, s FROM rq2) FROM r ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 26",
            "SELECT id, (a, b) NOT IN (SELECT k, s FROM rn2) FROM r ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|true ;; 5|true",
            "SELECT id, (a, c) NOT IN (SELECT k, v FROM rn2) FROM r ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|true ;; 5|true",
            "SELECT id, (a, b) <> ALL (SELECT k, s FROM rq2) FROM r ORDER BY id");
        assertAnswer("1 ;; 4 ;; 5",
            "SELECT id FROM r WHERE (a, b) NOT IN (SELECT k, s FROM rq2) OR id = 1 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 38",
            "SELECT id FROM r WHERE (a, b) NOT IN (SELECT k, s FROM rn2) OR id = 1 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 26",
            "SELECT id, (a, b) NOT IN (SELECT k, s FROM rq1) FROM r ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|true ;; 5|true",
            "SELECT id, (a, b) NOT IN (SELECT k, s FROM rd) FROM r ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 26",
            "SELECT id, (a, b) NOT IN (SELECT k, s FROM rn2 WHERE s IS NOT NULL) FROM r ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|true ;; 5|true",
            "SELECT id, (a, b) NOT IN (SELECT 1, 'x' UNION ALL SELECT 2, 'y') FROM r ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 26",
            "SELECT id, (a, b) NOT IN (SELECT MAX(k), MAX(s) FROM rn2) FROM r ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null",
            "SELECT id, a NOT IN (SELECT NULL) FROM r ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|true ;; 5|true",
            "SELECT id, (a, b) NOT IN (SELECT k + 1, s FROM rq2) FROM r ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 26",
            "SELECT id, (a, b) NOT IN (SELECT k, UPPER(s) FROM rq2) FROM r ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 36",
            "SELECT id, CASE WHEN (a, b) NOT IN (SELECT k, s FROM rn2) THEN 1 END FROM r ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line "
            + "1, position 26",
            "SELECT id, (a, b) NOT IN (SELECT 1, NULL) FROM r ORDER BY id");
    }

    @Test
    public void blockExpressions() {
        engine.execute("CREATE OR REPLACE TABLE p (id INT, a INT, b VARCHAR, c INT)");
        engine.execute("INSERT INTO p VALUES (1, 1, 'x', 10), (2, 2, 'y', 20), (3, NULL, 'x', 10), (4, 1, NULL, "
            + "10), (5, NULL, NULL, NULL), (6, 3, 'z', 30), (7, 1, 'x', NULL), (8, NULL, 'q', 20), (9, "
            + "9, NULL, 99)");
        engine.execute("CREATE OR REPLACE TABLE q (g INT, k INT, s VARCHAR, v INT)");
        engine.execute("INSERT INTO q VALUES (0, 1, 'x', 10), (0, 2, 'y', 20), (1, 1, 'x', 10), (1, NULL, 'y', "
            + "20), (2, 1, 'x', 10), (2, 2, NULL, 20), (3, NULL, NULL, NULL), (5, 1, NULL, 10), (6, "
            + "NULL, 'x', 10)");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL "
            + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
            + "32",
            "EXECUTE IMMEDIATE $$DECLARE r BOOLEAN; BEGIN r := (1, 'x') NOT IN (SELECT k, s FROM q "
            + "WHERE g = 0); RETURN r; END$$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL "
            + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
            + "24",
            "EXECUTE IMMEDIATE $$BEGIN RETURN (1, 'x') NOT IN (SELECT k, s FROM q WHERE g = 0); END$$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL "
            + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
            + "27",
            "EXECUTE IMMEDIATE $$DECLARE r BOOLEAN; BEGIN r := (1, 'x') <> ALL (SELECT k, s FROM q "
            + "WHERE g = 0); RETURN r; END$$");
    }
}
