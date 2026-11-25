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
 * A positive membership standing as a value whose query tests a value of the outer row for NULL (live-verified):
 * {@code SELECT id, EXISTS (SELECT 1 FROM i WHERE o.k IS NULL) FROM o} is refused at the EXISTS, and so are NOT
 * EXISTS, IN, a row IN and any ANY in a select list, under OR or NOT in a WHERE, in a HAVING or an ORDER BY, for
 * {@code IS NULL}, {@code IS NOT NULL}, {@code NOT (o.k IS NULL)}, {@code o.k + 1 IS NULL} and the test beside an
 * equality. The same EXISTS or NOT EXISTS as a conjunct of a WHERE, a NOT IN or an ALL anywhere, a test the
 * statistics settle, a test inside a comparison or a function's argument, an IN whose item holds NULLs alone and a
 * DELETE's WHERE under OR are answered.
 */
public class MembershipNullTestValueTest extends MeasuredCellsTestSupport {

    @Test
    public void quantifiedNeighbours() {
        engine.execute("CREATE OR REPLACE TABLE p (id INT, k INT)");
        engine.execute("INSERT INTO p VALUES (1,0),(2,1),(3,2),(4,3),(5,NULL)");
        engine.execute("CREATE OR REPLACE TABLE e1 (k INT)");
        engine.execute("CREATE OR REPLACE TABLE nn (k INT)");
        engine.execute("INSERT INTO nn VALUES (1),(2)");
        engine.execute("CREATE OR REPLACE TABLE wn (k INT)");
        engine.execute("INSERT INTO wn VALUES (1),(NULL)");
        engine.execute("CREATE OR REPLACE TABLE an (k INT)");
        engine.execute("INSERT INTO an VALUES (NULL),(NULL)");
        engine.execute("CREATE OR REPLACE TABLE w3 (k INT)");
        engine.execute("INSERT INTO w3 VALUES (1),(2),(NULL)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 17",
            "SELECT id, k IN (SELECT k FROM w3 WHERE p.k IS NULL) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k IN (SELECT k FROM an WHERE p.k IS NULL) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 15",
            "SELECT id, k = ANY (SELECT k FROM w3 WHERE p.k IS NULL) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k = ANY (SELECT k FROM an WHERE p.k IS NULL) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|null",
            "SELECT id, k NOT IN (SELECT k FROM w3 WHERE p.k IS NULL) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|null",
            "SELECT id, k NOT IN (SELECT k FROM an WHERE p.k IS NULL) FROM p ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|null",
            "SELECT id, k <> ALL (SELECT k FROM w3 WHERE p.k IS NULL) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 15",
            "SELECT id, k < ANY (SELECT k FROM w3 WHERE p.k IS NULL) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 11",
            "SELECT id, EXISTS (SELECT 1 FROM w3 WHERE p.k IS NULL) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 11",
            "SELECT id, EXISTS (SELECT 1 FROM an WHERE p.k IS NULL) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 15",
            "SELECT id, NOT EXISTS (SELECT 1 FROM w3 WHERE p.k IS NULL) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 15",
            "SELECT id, NOT EXISTS (SELECT 1 FROM an WHERE p.k IS NULL) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 17",
            "SELECT id, k IN (SELECT k FROM w3 WHERE p.k IS NOT NULL) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k IN (SELECT k FROM an WHERE p.k IS NOT NULL) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 15",
            "SELECT id, k = ANY (SELECT k FROM w3 WHERE p.k IS NOT NULL) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k = ANY (SELECT k FROM an WHERE p.k IS NOT NULL) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|false ;; 3|false ;; 4|null ;; 5|true",
            "SELECT id, k NOT IN (SELECT k FROM w3 WHERE p.k IS NOT NULL) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|true",
            "SELECT id, k NOT IN (SELECT k FROM an WHERE p.k IS NOT NULL) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|false ;; 3|false ;; 4|null ;; 5|true",
            "SELECT id, k <> ALL (SELECT k FROM w3 WHERE p.k IS NOT NULL) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 15",
            "SELECT id, k < ANY (SELECT k FROM w3 WHERE p.k IS NOT NULL) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 11",
            "SELECT id, EXISTS (SELECT 1 FROM w3 WHERE p.k IS NOT NULL) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 11",
            "SELECT id, EXISTS (SELECT 1 FROM an WHERE p.k IS NOT NULL) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 15",
            "SELECT id, NOT EXISTS (SELECT 1 FROM w3 WHERE p.k IS NOT NULL) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 15",
            "SELECT id, NOT EXISTS (SELECT 1 FROM an WHERE p.k IS NOT NULL) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 17",
            "SELECT id, k IN (SELECT k FROM w3 WHERE p.id = 1 OR p.k IS NULL) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k IN (SELECT k FROM an WHERE p.id = 1 OR p.k IS NULL) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 15",
            "SELECT id, k = ANY (SELECT k FROM w3 WHERE p.id = 1 OR p.k IS NULL) FROM p ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k = ANY (SELECT k FROM an WHERE p.id = 1 OR p.k IS NULL) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|true ;; 3|true ;; 4|true ;; 5|null",
            "SELECT id, k NOT IN (SELECT k FROM w3 WHERE p.id = 1 OR p.k IS NULL) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|true ;; 3|true ;; 4|true ;; 5|null",
            "SELECT id, k NOT IN (SELECT k FROM an WHERE p.id = 1 OR p.k IS NULL) FROM p ORDER BY id");
        assertAnswer("1|null ;; 2|true ;; 3|true ;; 4|true ;; 5|null",
            "SELECT id, k <> ALL (SELECT k FROM w3 WHERE p.id = 1 OR p.k IS NULL) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 15",
            "SELECT id, k < ANY (SELECT k FROM w3 WHERE p.id = 1 OR p.k IS NULL) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 11",
            "SELECT id, EXISTS (SELECT 1 FROM w3 WHERE p.id = 1 OR p.k IS NULL) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 11",
            "SELECT id, EXISTS (SELECT 1 FROM an WHERE p.id = 1 OR p.k IS NULL) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 15",
            "SELECT id, NOT EXISTS (SELECT 1 FROM w3 WHERE p.id = 1 OR p.k IS NULL) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 15",
            "SELECT id, NOT EXISTS (SELECT 1 FROM an WHERE p.id = 1 OR p.k IS NULL) FROM p ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 30",
            "SELECT id FROM p WHERE (k IN (SELECT k FROM w3 WHERE p.k IS NULL)) IS NULL ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 28",
            "SELECT id FROM p WHERE (k = ANY (SELECT k FROM w3 WHERE p.k IS NULL)) IS NULL ORDER BY id");
        assertAnswer("5",
            "SELECT id FROM p WHERE (k NOT IN (SELECT k FROM w3 WHERE p.k IS NULL)) IS NULL ORDER BY id");
        assertAnswer("1|1 ;; 2|1 ;; 3|1 ;; 4|1 ;; 5|9",
            "SELECT id, CASE WHEN k NOT IN (SELECT k FROM w3 WHERE p.k IS NULL) THEN 1 WHEN NOT (k NOT "
            + "IN (SELECT k FROM w3 WHERE p.k IS NULL)) THEN 0 ELSE 9 END FROM p ORDER BY id");
    }

    @Test
    public void existsAsValue() {
        engine.execute("CREATE OR REPLACE TABLE p (id INT, k INT)");
        engine.execute("INSERT INTO p VALUES (1,0),(2,1),(3,2),(4,3),(5,NULL)");
        engine.execute("CREATE OR REPLACE TABLE e1 (k INT)");
        engine.execute("CREATE OR REPLACE TABLE nn (k INT)");
        engine.execute("INSERT INTO nn VALUES (1),(2)");
        engine.execute("CREATE OR REPLACE TABLE wn (k INT)");
        engine.execute("INSERT INTO wn VALUES (1),(NULL)");
        engine.execute("CREATE OR REPLACE TABLE an (k INT)");
        engine.execute("INSERT INTO an VALUES (NULL),(NULL)");
        engine.execute("CREATE OR REPLACE TABLE w3 (k INT)");
        engine.execute("INSERT INTO w3 VALUES (1),(2),(NULL)");
        engine.execute("CREATE OR REPLACE TABLE o (id INT, k INT, n NUMBER(10,2), f FLOAT, s VARCHAR, d DATE, ts "
            + "TIMESTAMP_NTZ, b BOOLEAN, v VARIANT, a ARRAY, ob OBJECT)");
        engine.execute("INSERT INTO o SELECT 1, 1, 1.50, 1.5, 'a', '2020-01-01'::DATE, '2020-01-01 "
            + "10:00:00'::TIMESTAMP_NTZ, TRUE, TO_VARIANT(1), ARRAY_CONSTRUCT(1,2), "
            + "OBJECT_CONSTRUCT('k',1) UNION ALL SELECT 2, 2, 2.00, 2.0, 'B', '2020-01-02'::DATE, "
            + "'2020-01-02 00:00:00'::TIMESTAMP_NTZ, FALSE, TO_VARIANT('x'), ARRAY_CONSTRUCT(2), "
            + "OBJECT_CONSTRUCT('k',2) UNION ALL SELECT 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, "
            + "NULL, NULL, NULL UNION ALL SELECT 4, 2, 2.00, 2.0, 'b', '2020-01-03'::DATE, '2020-01-03 "
            + "12:30:00'::TIMESTAMP_NTZ, TRUE, TO_VARIANT(2), ARRAY_CONSTRUCT(3,NULL), OBJECT_CONSTRUCT()"
            + " UNION ALL SELECT 5, 5, 5.25, 5.25, 'e', '2020-01-05'::DATE, '2020-01-05 "
            + "00:00:00'::TIMESTAMP_NTZ, NULL, PARSE_JSON('null'), ARRAY_CONSTRUCT(), "
            + "OBJECT_CONSTRUCT('z',NULL)");
        engine.execute("CREATE OR REPLACE TABLE i (k INT, n NUMBER(10,2), f FLOAT, s VARCHAR, d DATE, ts "
            + "TIMESTAMP_NTZ, b BOOLEAN, v VARIANT, val INT)");
        engine.execute("INSERT INTO i SELECT 1, 1.50, 1.5, 'A', '2020-01-01'::DATE, '2020-01-01 "
            + "10:00:00'::TIMESTAMP_NTZ, TRUE, TO_VARIANT(1), 10 UNION ALL SELECT 2, 2.00, 2.0, 'b', "
            + "'2020-01-02'::DATE, '2020-01-02 00:00:00'::TIMESTAMP_NTZ, FALSE, TO_VARIANT('x'), 20 UNION"
            + " ALL SELECT 2, 2.10, 2.1, 'b', '2020-01-04'::DATE, '2020-01-04 00:00:00'::TIMESTAMP_NTZ, "
            + "TRUE, TO_VARIANT(2), 21 UNION ALL SELECT NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, "
            + "30 UNION ALL SELECT 3, 3.00, 3.0, 'c', '2020-01-03'::DATE, '2020-01-03 "
            + "12:30:00'::TIMESTAMP_NTZ, NULL, TO_VARIANT(3), NULL");
        engine.execute("CREATE OR REPLACE TABLE e (k INT, n NUMBER(10,2), f FLOAT, s VARCHAR, d DATE, ts "
            + "TIMESTAMP_NTZ, b BOOLEAN, v VARIANT, val INT)");
        engine.execute("CREATE OR REPLACE TABLE one (k INT, val INT)");
        engine.execute("INSERT INTO one VALUES (2, 7)");
        engine.execute("CREATE OR REPLACE TABLE nn (k INT)");
        engine.execute("INSERT INTO nn VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE TABLE wn (k INT)");
        engine.execute("INSERT INTO wn VALUES (1), (NULL)");
        engine.execute("CREATE OR REPLACE TABLE pz (id INT, k INT)");
        engine.execute("INSERT INTO pz VALUES (0,0),(1,1),(2,2),(3,3),(4,NULL)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 11",
            "SELECT id, EXISTS (SELECT 1 FROM i WHERE o.k IS NULL) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 15",
            "SELECT id, NOT EXISTS (SELECT 1 FROM i WHERE o.k IS NULL) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 11",
            "SELECT id, EXISTS (SELECT 1 FROM i WHERE o.k IS NOT NULL) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 15",
            "SELECT id, NOT EXISTS (SELECT 1 FROM i WHERE o.k IS NOT NULL) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 11",
            "SELECT id, EXISTS (SELECT 1 FROM i WHERE NOT (o.k IS NULL)) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 15",
            "SELECT id, NOT EXISTS (SELECT 1 FROM i WHERE NOT (o.k IS NULL)) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 11",
            "SELECT id, EXISTS (SELECT 1 FROM i WHERE o.k + 1 IS NULL) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 15",
            "SELECT id, NOT EXISTS (SELECT 1 FROM i WHERE o.k + 1 IS NULL) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 11",
            "SELECT id, EXISTS (SELECT 1 FROM i WHERE i.k = o.k AND o.k IS NULL) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 15",
            "SELECT id, NOT EXISTS (SELECT 1 FROM i WHERE i.k = o.k AND o.k IS NULL) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 11",
            "SELECT id, EXISTS (SELECT 1 FROM i WHERE i.k = o.k AND o.k IS NOT NULL) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 15",
            "SELECT id, NOT EXISTS (SELECT 1 FROM i WHERE i.k = o.k AND o.k IS NOT NULL) FROM o ORDER "
            + "BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, EXISTS (SELECT 1 FROM i WHERE o.k IS NULL OR i.k = 1) FROM o ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, NOT EXISTS (SELECT 1 FROM i WHERE o.k IS NULL OR i.k = 1) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 11",
            "SELECT id, EXISTS (SELECT 1 FROM i WHERE o.k IS NULL OR o.k = 1) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 15",
            "SELECT id, NOT EXISTS (SELECT 1 FROM i WHERE o.k IS NULL OR o.k = 1) FROM o ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, EXISTS (SELECT 1 FROM i WHERE IFF(o.k IS NULL, 1, 0) = 1) FROM o ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|true ;; 5|true",
            "SELECT id, NOT EXISTS (SELECT 1 FROM i WHERE IFF(o.k IS NULL, 1, 0) = 1) FROM o ORDER BY "
            + "id");
        assertAnswer("1|false ;; 2|false ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, EXISTS (SELECT 1 FROM i WHERE o.k IS NOT DISTINCT FROM NULL) FROM o ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|true ;; 5|true",
            "SELECT id, NOT EXISTS (SELECT 1 FROM i WHERE o.k IS NOT DISTINCT FROM NULL) FROM o ORDER "
            + "BY id");
        assertAnswer("1|false ;; 2|false ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, EXISTS (SELECT 1 FROM i WHERE EQUAL_NULL(o.k, NULL)) FROM o ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|true ;; 5|true",
            "SELECT id, NOT EXISTS (SELECT 1 FROM i WHERE EQUAL_NULL(o.k, NULL)) FROM o ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, EXISTS (SELECT 1 FROM i WHERE COALESCE(o.k, 0) = 0) FROM o ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|false ;; 4|true ;; 5|true",
            "SELECT id, NOT EXISTS (SELECT 1 FROM i WHERE COALESCE(o.k, 0) = 0) FROM o ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, EXISTS (SELECT 1 FROM i WHERE o.id IS NULL) FROM o ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, NOT EXISTS (SELECT 1 FROM i WHERE o.id IS NULL) FROM o ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, EXISTS (SELECT 1 FROM i WHERE o.id IS NOT NULL) FROM o ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, NOT EXISTS (SELECT 1 FROM i WHERE o.id IS NOT NULL) FROM o ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|false ;; 4|true ;; 5|false",
            "SELECT id, EXISTS (SELECT 1 FROM i WHERE o.k = 2) FROM o ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|true ;; 4|false ;; 5|true",
            "SELECT id, NOT EXISTS (SELECT 1 FROM i WHERE o.k = 2) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 11",
            "SELECT id, EXISTS (SELECT 1 FROM i WHERE o.s IS NULL) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 15",
            "SELECT id, NOT EXISTS (SELECT 1 FROM i WHERE o.s IS NULL) FROM o ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, EXISTS (SELECT 1 FROM i WHERE i.k IS NULL) FROM o ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, NOT EXISTS (SELECT 1 FROM i WHERE i.k IS NULL) FROM o ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, id IN (SELECT val FROM i WHERE o.k IS NULL) FROM o ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|null ;; 4|true ;; 5|true",
            "SELECT id, id NOT IN (SELECT val FROM i WHERE o.k IS NULL) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 16",
            "SELECT id, id < ANY (SELECT val FROM i WHERE o.k IS NULL) FROM o ORDER BY id");
        assertAnswer("3",
            "SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE o.k IS NULL) ORDER BY id");
        assertAnswer("1 ;; 2 ;; 4 ;; 5",
            "SELECT id FROM o WHERE NOT EXISTS (SELECT 1 FROM i WHERE o.k IS NULL) ORDER BY id");
        assertAnswer("1 ;; 2 ;; 4 ;; 5",
            "SELECT id FROM o WHERE NOT (EXISTS (SELECT 1 FROM i WHERE o.k IS NULL)) ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 23",
            "SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE o.k IS NULL) OR id = 1 ORDER BY id");
        assertAnswer("3",
            "SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE o.k IS NULL) AND id > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 66 unexpected '='.",
            "SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE o.k IS NULL) = TRUE ORDER BY id");
        assertAnswer("1",
            "SELECT id FROM o WHERE id IN (SELECT val FROM i WHERE o.k IS NULL) OR id = 1 ORDER BY id");
        assertAnswer("1 ;; 2 ;; 3 ;; 4 ;; 5",
            "SELECT id FROM o WHERE NOT (id IN (SELECT val FROM i WHERE o.k IS NULL)) ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 44",
            "SELECT k, COUNT(*) FROM o GROUP BY k HAVING EXISTS (SELECT 1 FROM i WHERE o.k IS NULL) "
            + "ORDER BY k");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 17 | found QUALIFY clause but no "
            + "window function.",
            "SELECT id FROM o QUALIFY EXISTS (SELECT 1 FROM i WHERE o.k IS NULL) ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 26",
            "SELECT id FROM o ORDER BY EXISTS (SELECT 1 FROM i WHERE o.k IS NULL), id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, id IN (SELECT val FROM i WHERE o.k IS NOT NULL) FROM o ORDER BY id");
        assertAnswer("1|null ;; 2|null ;; 3|true ;; 4|null ;; 5|null",
            "SELECT id, id NOT IN (SELECT val FROM i WHERE o.k IS NOT NULL) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 16",
            "SELECT id, id < ANY (SELECT val FROM i WHERE o.k IS NOT NULL) FROM o ORDER BY id");
        assertAnswer("1 ;; 2 ;; 4 ;; 5",
            "SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE o.k IS NOT NULL) ORDER BY id");
        assertAnswer("3",
            "SELECT id FROM o WHERE NOT EXISTS (SELECT 1 FROM i WHERE o.k IS NOT NULL) ORDER BY id");
        assertAnswer("3",
            "SELECT id FROM o WHERE NOT (EXISTS (SELECT 1 FROM i WHERE o.k IS NOT NULL)) ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 23",
            "SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE o.k IS NOT NULL) OR id = 1 ORDER BY "
            + "id");
        assertAnswer("1 ;; 2 ;; 4 ;; 5",
            "SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE o.k IS NOT NULL) AND id > 0 ORDER BY "
            + "id");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 70 unexpected '='.",
            "SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE o.k IS NOT NULL) = TRUE ORDER BY id");
        assertAnswer("1",
            "SELECT id FROM o WHERE id IN (SELECT val FROM i WHERE o.k IS NOT NULL) OR id = 1 ORDER BY "
            + "id");
        assertAnswer("1 ;; 2 ;; 3 ;; 4 ;; 5",
            "SELECT id FROM o WHERE NOT (id IN (SELECT val FROM i WHERE o.k IS NOT NULL)) ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 44",
            "SELECT k, COUNT(*) FROM o GROUP BY k HAVING EXISTS (SELECT 1 FROM i WHERE o.k IS NOT NULL)"
            + " ORDER BY k");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 17 | found QUALIFY clause but no "
            + "window function.",
            "SELECT id FROM o QUALIFY EXISTS (SELECT 1 FROM i WHERE o.k IS NOT NULL) ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 26",
            "SELECT id FROM o ORDER BY EXISTS (SELECT 1 FROM i WHERE o.k IS NOT NULL), id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, id IN (SELECT val FROM i WHERE o.k + 1 IS NULL) FROM o ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|null ;; 4|true ;; 5|true",
            "SELECT id, id NOT IN (SELECT val FROM i WHERE o.k + 1 IS NULL) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 16",
            "SELECT id, id < ANY (SELECT val FROM i WHERE o.k + 1 IS NULL) FROM o ORDER BY id");
        assertAnswer("3",
            "SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE o.k + 1 IS NULL) ORDER BY id");
        assertAnswer("1 ;; 2 ;; 4 ;; 5",
            "SELECT id FROM o WHERE NOT EXISTS (SELECT 1 FROM i WHERE o.k + 1 IS NULL) ORDER BY id");
        assertAnswer("1 ;; 2 ;; 4 ;; 5",
            "SELECT id FROM o WHERE NOT (EXISTS (SELECT 1 FROM i WHERE o.k + 1 IS NULL)) ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 23",
            "SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE o.k + 1 IS NULL) OR id = 1 ORDER BY "
            + "id");
        assertAnswer("3",
            "SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE o.k + 1 IS NULL) AND id > 0 ORDER BY "
            + "id");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 70 unexpected '='.",
            "SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE o.k + 1 IS NULL) = TRUE ORDER BY id");
        assertAnswer("1",
            "SELECT id FROM o WHERE id IN (SELECT val FROM i WHERE o.k + 1 IS NULL) OR id = 1 ORDER BY "
            + "id");
        assertAnswer("1 ;; 2 ;; 3 ;; 4 ;; 5",
            "SELECT id FROM o WHERE NOT (id IN (SELECT val FROM i WHERE o.k + 1 IS NULL)) ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 44",
            "SELECT k, COUNT(*) FROM o GROUP BY k HAVING EXISTS (SELECT 1 FROM i WHERE o.k + 1 IS NULL)"
            + " ORDER BY k");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 17 | found QUALIFY clause but no "
            + "window function.",
            "SELECT id FROM o QUALIFY EXISTS (SELECT 1 FROM i WHERE o.k + 1 IS NULL) ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 26",
            "SELECT id FROM o ORDER BY EXISTS (SELECT 1 FROM i WHERE o.k + 1 IS NULL), id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, id IN (SELECT val FROM i WHERE i.k = o.k AND o.k IS NULL) FROM o ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, id NOT IN (SELECT val FROM i WHERE i.k = o.k AND o.k IS NULL) FROM o ORDER BY "
            + "id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 16",
            "SELECT id, id < ANY (SELECT val FROM i WHERE i.k = o.k AND o.k IS NULL) FROM o ORDER BY id");
        assertAnswer("",
            "SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.k = o.k AND o.k IS NULL) ORDER BY "
            + "id");
        assertAnswer("1 ;; 2 ;; 3 ;; 4 ;; 5",
            "SELECT id FROM o WHERE NOT EXISTS (SELECT 1 FROM i WHERE i.k = o.k AND o.k IS NULL) ORDER "
            + "BY id");
        assertAnswer("1 ;; 2 ;; 3 ;; 4 ;; 5",
            "SELECT id FROM o WHERE NOT (EXISTS (SELECT 1 FROM i WHERE i.k = o.k AND o.k IS NULL)) "
            + "ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 23",
            "SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.k = o.k AND o.k IS NULL) OR id = 1 "
            + "ORDER BY id");
        assertAnswer("",
            "SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.k = o.k AND o.k IS NULL) AND id > 0"
            + " ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 80 unexpected '='.",
            "SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE i.k = o.k AND o.k IS NULL) = TRUE "
            + "ORDER BY id");
        assertAnswer("1",
            "SELECT id FROM o WHERE id IN (SELECT val FROM i WHERE i.k = o.k AND o.k IS NULL) OR id = 1"
            + " ORDER BY id");
        assertAnswer("1 ;; 2 ;; 3 ;; 4 ;; 5",
            "SELECT id FROM o WHERE NOT (id IN (SELECT val FROM i WHERE i.k = o.k AND o.k IS NULL)) "
            + "ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 44",
            "SELECT k, COUNT(*) FROM o GROUP BY k HAVING EXISTS (SELECT 1 FROM i WHERE i.k = o.k AND "
            + "o.k IS NULL) ORDER BY k");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 17 | found QUALIFY clause but no "
            + "window function.",
            "SELECT id FROM o QUALIFY EXISTS (SELECT 1 FROM i WHERE i.k = o.k AND o.k IS NULL) ORDER BY"
            + " id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 26",
            "SELECT id FROM o ORDER BY EXISTS (SELECT 1 FROM i WHERE i.k = o.k AND o.k IS NULL), id");
        assertAnswer("1|y ;; 2|y ;; 3|y ;; 4|y ;; 5|y",
            "SELECT id, CASE WHEN EXISTS (SELECT 1 FROM i WHERE o.k IS NULL OR i.k = 1) THEN 'y' ELSE "
            + "'n' END FROM o ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, id IN (SELECT val FROM i WHERE o.k IS NULL OR i.k = 1) FROM o ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|null ;; 4|true ;; 5|true",
            "SELECT id, id NOT IN (SELECT val FROM i WHERE o.k IS NULL OR i.k = 1) FROM o ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, id < ANY (SELECT val FROM i WHERE o.k IS NULL OR i.k = 1) FROM o ORDER BY id");
        assertAnswer("1 ;; 2 ;; 3 ;; 4 ;; 5",
            "SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE o.k IS NULL OR i.k = 1) ORDER BY id");
        assertAnswer("",
            "SELECT id FROM o WHERE NOT EXISTS (SELECT 1 FROM i WHERE o.k IS NULL OR i.k = 1) ORDER BY "
            + "id");
        assertAnswer("",
            "SELECT id FROM o WHERE NOT (EXISTS (SELECT 1 FROM i WHERE o.k IS NULL OR i.k = 1)) ORDER "
            + "BY id");
        assertAnswer("1 ;; 2 ;; 3 ;; 4 ;; 5",
            "SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE o.k IS NULL OR i.k = 1) OR id = 1 "
            + "ORDER BY id");
        assertAnswer("1 ;; 2 ;; 3 ;; 4 ;; 5",
            "SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE o.k IS NULL OR i.k = 1) AND id > 0 "
            + "ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 77 unexpected '='.",
            "SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE o.k IS NULL OR i.k = 1) = TRUE ORDER "
            + "BY id");
        assertAnswer("1",
            "SELECT id FROM o WHERE id IN (SELECT val FROM i WHERE o.k IS NULL OR i.k = 1) OR id = 1 "
            + "ORDER BY id");
        assertAnswer("1 ;; 2 ;; 3 ;; 4 ;; 5",
            "SELECT id FROM o WHERE NOT (id IN (SELECT val FROM i WHERE o.k IS NULL OR i.k = 1)) ORDER "
            + "BY id");
        assertAnswer("1|1 ;; 2|2 ;; 5|1 ;; null|1",
            "SELECT k, COUNT(*) FROM o GROUP BY k HAVING EXISTS (SELECT 1 FROM i WHERE o.k IS NULL OR "
            + "i.k = 1) ORDER BY k");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 17 | found QUALIFY clause but no "
            + "window function.",
            "SELECT id FROM o QUALIFY EXISTS (SELECT 1 FROM i WHERE o.k IS NULL OR i.k = 1) ORDER BY id");
        assertAnswer("1 ;; 2 ;; 3 ;; 4 ;; 5",
            "SELECT id FROM o ORDER BY EXISTS (SELECT 1 FROM i WHERE o.k IS NULL OR i.k = 1), id");
        assertAnswer("1 ;; 2 ;; 3 ;; 4 ;; 5",
            "SELECT o.id FROM o JOIN nn ON nn.k = 1 AND EXISTS (SELECT 1 FROM i WHERE o.k IS NULL OR "
            + "i.k = 1) ORDER BY o.id");
        assertAnswer("1|n ;; 2|n ;; 3|n ;; 4|n ;; 5|n",
            "SELECT id, CASE WHEN EXISTS (SELECT 1 FROM i WHERE o.id IS NULL) THEN 'y' ELSE 'n' END "
            + "FROM o ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, id IN (SELECT val FROM i WHERE o.id IS NULL) FROM o ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, id NOT IN (SELECT val FROM i WHERE o.id IS NULL) FROM o ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, id < ANY (SELECT val FROM i WHERE o.id IS NULL) FROM o ORDER BY id");
        assertAnswer("",
            "SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE o.id IS NULL) ORDER BY id");
        assertAnswer("1 ;; 2 ;; 3 ;; 4 ;; 5",
            "SELECT id FROM o WHERE NOT EXISTS (SELECT 1 FROM i WHERE o.id IS NULL) ORDER BY id");
        assertAnswer("1 ;; 2 ;; 3 ;; 4 ;; 5",
            "SELECT id FROM o WHERE NOT (EXISTS (SELECT 1 FROM i WHERE o.id IS NULL)) ORDER BY id");
        assertAnswer("1",
            "SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE o.id IS NULL) OR id = 1 ORDER BY id");
        assertAnswer("",
            "SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE o.id IS NULL) AND id > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 67 unexpected '='.",
            "SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE o.id IS NULL) = TRUE ORDER BY id");
        assertAnswer("1",
            "SELECT id FROM o WHERE id IN (SELECT val FROM i WHERE o.id IS NULL) OR id = 1 ORDER BY id");
        assertAnswer("1 ;; 2 ;; 3 ;; 4 ;; 5",
            "SELECT id FROM o WHERE NOT (id IN (SELECT val FROM i WHERE o.id IS NULL)) ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 44",
            "SELECT k, COUNT(*) FROM o GROUP BY k HAVING EXISTS (SELECT 1 FROM i WHERE o.k IS NULL) "
            + "ORDER BY k");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 17 | found QUALIFY clause but no "
            + "window function.",
            "SELECT id FROM o QUALIFY EXISTS (SELECT 1 FROM i WHERE o.id IS NULL) ORDER BY id");
        assertAnswer("1 ;; 2 ;; 3 ;; 4 ;; 5",
            "SELECT id FROM o ORDER BY EXISTS (SELECT 1 FROM i WHERE o.id IS NULL), id");
        assertAnswer("",
            "SELECT o.id FROM o JOIN nn ON nn.k = 1 AND EXISTS (SELECT 1 FROM i WHERE o.id IS NULL) "
            + "ORDER BY o.id");
        assertAnswer("1|n ;; 2|y ;; 3|n ;; 4|y ;; 5|n",
            "SELECT id, CASE WHEN EXISTS (SELECT 1 FROM i WHERE o.k = 2) THEN 'y' ELSE 'n' END FROM o "
            + "ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, id IN (SELECT val FROM i WHERE o.k = 2) FROM o ORDER BY id");
        assertAnswer("1|true ;; 2|null ;; 3|true ;; 4|null ;; 5|true",
            "SELECT id, id NOT IN (SELECT val FROM i WHERE o.k = 2) FROM o ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|false ;; 4|true ;; 5|false",
            "SELECT id, id < ANY (SELECT val FROM i WHERE o.k = 2) FROM o ORDER BY id");
        assertAnswer("2 ;; 4",
            "SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE o.k = 2) ORDER BY id");
        assertAnswer("1 ;; 3 ;; 5",
            "SELECT id FROM o WHERE NOT EXISTS (SELECT 1 FROM i WHERE o.k = 2) ORDER BY id");
        assertAnswer("1 ;; 3 ;; 5",
            "SELECT id FROM o WHERE NOT (EXISTS (SELECT 1 FROM i WHERE o.k = 2)) ORDER BY id");
        assertAnswer("1 ;; 2 ;; 4",
            "SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE o.k = 2) OR id = 1 ORDER BY id");
        assertAnswer("2 ;; 4",
            "SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE o.k = 2) AND id > 0 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected '='.",
            "SELECT id FROM o WHERE EXISTS (SELECT 1 FROM i WHERE o.k = 2) = TRUE ORDER BY id");
        assertAnswer("1",
            "SELECT id FROM o WHERE id IN (SELECT val FROM i WHERE o.k = 2) OR id = 1 ORDER BY id");
        assertAnswer("1 ;; 2 ;; 3 ;; 4 ;; 5",
            "SELECT id FROM o WHERE NOT (id IN (SELECT val FROM i WHERE o.k = 2)) ORDER BY id");
        assertAnswer("2|2",
            "SELECT k, COUNT(*) FROM o GROUP BY k HAVING EXISTS (SELECT 1 FROM i WHERE o.k = 2) ORDER "
            + "BY k");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 17 | found QUALIFY clause but no "
            + "window function.",
            "SELECT id FROM o QUALIFY EXISTS (SELECT 1 FROM i WHERE o.k = 2) ORDER BY id");
        assertAnswer("1 ;; 3 ;; 5 ;; 2 ;; 4",
            "SELECT id FROM o ORDER BY EXISTS (SELECT 1 FROM i WHERE o.k = 2), id");
        assertAnswer("2 ;; 4",
            "SELECT o.id FROM o JOIN nn ON nn.k = 1 AND EXISTS (SELECT 1 FROM i WHERE o.k = 2) ORDER BY"
            + " o.id");
    }

    @Test
    public void testShapes() {
        engine.execute("CREATE OR REPLACE TABLE p (id INT, k INT)");
        engine.execute("INSERT INTO p VALUES (1,0),(2,1),(3,2),(4,3),(5,NULL)");
        engine.execute("CREATE OR REPLACE TABLE e1 (k INT)");
        engine.execute("CREATE OR REPLACE TABLE nn (k INT)");
        engine.execute("INSERT INTO nn VALUES (1),(2)");
        engine.execute("CREATE OR REPLACE TABLE wn (k INT)");
        engine.execute("INSERT INTO wn VALUES (1),(NULL)");
        engine.execute("CREATE OR REPLACE TABLE an (k INT)");
        engine.execute("INSERT INTO an VALUES (NULL),(NULL)");
        engine.execute("CREATE OR REPLACE TABLE w3 (k INT)");
        engine.execute("INSERT INTO w3 VALUES (1),(2),(NULL)");
        engine.execute("CREATE OR REPLACE TABLE o (id INT, k INT, n NUMBER(10,2), f FLOAT, s VARCHAR, d DATE, ts "
            + "TIMESTAMP_NTZ, b BOOLEAN, v VARIANT, a ARRAY, ob OBJECT)");
        engine.execute("INSERT INTO o SELECT 1, 1, 1.50, 1.5, 'a', '2020-01-01'::DATE, '2020-01-01 "
            + "10:00:00'::TIMESTAMP_NTZ, TRUE, TO_VARIANT(1), ARRAY_CONSTRUCT(1,2), "
            + "OBJECT_CONSTRUCT('k',1) UNION ALL SELECT 2, 2, 2.00, 2.0, 'B', '2020-01-02'::DATE, "
            + "'2020-01-02 00:00:00'::TIMESTAMP_NTZ, FALSE, TO_VARIANT('x'), ARRAY_CONSTRUCT(2), "
            + "OBJECT_CONSTRUCT('k',2) UNION ALL SELECT 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, "
            + "NULL, NULL, NULL UNION ALL SELECT 4, 2, 2.00, 2.0, 'b', '2020-01-03'::DATE, '2020-01-03 "
            + "12:30:00'::TIMESTAMP_NTZ, TRUE, TO_VARIANT(2), ARRAY_CONSTRUCT(3,NULL), OBJECT_CONSTRUCT()"
            + " UNION ALL SELECT 5, 5, 5.25, 5.25, 'e', '2020-01-05'::DATE, '2020-01-05 "
            + "00:00:00'::TIMESTAMP_NTZ, NULL, PARSE_JSON('null'), ARRAY_CONSTRUCT(), "
            + "OBJECT_CONSTRUCT('z',NULL)");
        engine.execute("CREATE OR REPLACE TABLE i (k INT, n NUMBER(10,2), f FLOAT, s VARCHAR, d DATE, ts "
            + "TIMESTAMP_NTZ, b BOOLEAN, v VARIANT, val INT)");
        engine.execute("INSERT INTO i SELECT 1, 1.50, 1.5, 'A', '2020-01-01'::DATE, '2020-01-01 "
            + "10:00:00'::TIMESTAMP_NTZ, TRUE, TO_VARIANT(1), 10 UNION ALL SELECT 2, 2.00, 2.0, 'b', "
            + "'2020-01-02'::DATE, '2020-01-02 00:00:00'::TIMESTAMP_NTZ, FALSE, TO_VARIANT('x'), 20 UNION"
            + " ALL SELECT 2, 2.10, 2.1, 'b', '2020-01-04'::DATE, '2020-01-04 00:00:00'::TIMESTAMP_NTZ, "
            + "TRUE, TO_VARIANT(2), 21 UNION ALL SELECT NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, "
            + "30 UNION ALL SELECT 3, 3.00, 3.0, 'c', '2020-01-03'::DATE, '2020-01-03 "
            + "12:30:00'::TIMESTAMP_NTZ, NULL, TO_VARIANT(3), NULL");
        engine.execute("CREATE OR REPLACE TABLE e (k INT, n NUMBER(10,2), f FLOAT, s VARCHAR, d DATE, ts "
            + "TIMESTAMP_NTZ, b BOOLEAN, v VARIANT, val INT)");
        engine.execute("CREATE OR REPLACE TABLE one (k INT, val INT)");
        engine.execute("INSERT INTO one VALUES (2, 7)");
        engine.execute("CREATE OR REPLACE TABLE nn (k INT)");
        engine.execute("INSERT INTO nn VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE TABLE wn (k INT)");
        engine.execute("INSERT INTO wn VALUES (1), (NULL)");
        engine.execute("CREATE OR REPLACE TABLE pz (id INT, k INT)");
        engine.execute("INSERT INTO pz VALUES (0,0),(1,1),(2,2),(3,3),(4,NULL)");
        assertAnswer("1|0 ;; 2|0 ;; 3|5 ;; 4|0 ;; 5|0",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE IFF(o.k IS NULL, 1, 0) = 1) FROM o ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, EXISTS (SELECT 1 FROM i WHERE IFF(o.k IS NULL, 1, 0) = 1) FROM o ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k IN (SELECT k FROM i WHERE IFF(o.k IS NULL, 1, 0) = 1) FROM o ORDER BY id");
        assertAnswer("1|0 ;; 2|0 ;; 3|5 ;; 4|0 ;; 5|0",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE CASE WHEN o.k IS NULL THEN 1 ELSE 0 END = 1) FROM"
            + " o ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, EXISTS (SELECT 1 FROM i WHERE CASE WHEN o.k IS NULL THEN 1 ELSE 0 END = 1) FROM"
            + " o ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k IN (SELECT k FROM i WHERE CASE WHEN o.k IS NULL THEN 1 ELSE 0 END = 1) FROM o"
            + " ORDER BY id");
        assertAnswer("1|0 ;; 2|0 ;; 3|5 ;; 4|0 ;; 5|0",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE (o.k IS NULL) = TRUE) FROM o ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, EXISTS (SELECT 1 FROM i WHERE (o.k IS NULL) = TRUE) FROM o ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k IN (SELECT k FROM i WHERE (o.k IS NULL) = TRUE) FROM o ORDER BY id");
        assertAnswer("1|0 ;; 2|0 ;; 3|5 ;; 4|0 ;; 5|0",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE NVL2(o.k, 0, 1) = 1) FROM o ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, EXISTS (SELECT 1 FROM i WHERE NVL2(o.k, 0, 1) = 1) FROM o ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false",
            "SELECT id, k IN (SELECT k FROM i WHERE NVL2(o.k, 0, 1) = 1) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 12",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE IFF(o.k + 1 IS NULL, TRUE, FALSE)) FROM o ORDER "
            + "BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 11",
            "SELECT id, EXISTS (SELECT 1 FROM i WHERE IFF(o.k + 1 IS NULL, TRUE, FALSE)) FROM o ORDER "
            + "BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 17",
            "SELECT id, k IN (SELECT k FROM i WHERE IFF(o.k + 1 IS NULL, TRUE, FALSE)) FROM o ORDER BY "
            + "id");
        assertAnswer("1|5 ;; 2|5 ;; 3|5 ;; 4|5 ;; 5|5",
            "SELECT id, (SELECT COUNT(*) FROM i WHERE (o.k IS NULL) IS NOT NULL) FROM o ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, EXISTS (SELECT 1 FROM i WHERE (o.k IS NULL) IS NOT NULL) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 17",
            "SELECT id, k IN (SELECT k FROM i WHERE o.k IS NULL) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 15",
            "SELECT id, k = ANY (SELECT k FROM i WHERE o.k IS NULL) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 16",
            "SELECT id, k >= ANY (SELECT k FROM i WHERE o.k IS NULL) FROM o ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|null ;; 4|true ;; 5|true",
            "SELECT id, k = ALL (SELECT k FROM i WHERE o.k IS NULL) FROM o ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|null ;; 4|true ;; 5|true",
            "SELECT id, k NOT IN (SELECT k FROM i WHERE o.k IS NULL) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 22",
            "SELECT id, NOT (k IN (SELECT k FROM i WHERE o.k IS NULL)) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 22",
            "SELECT id, (k, k) IN (SELECT k, k FROM i WHERE o.k IS NULL) FROM o ORDER BY id");
        assertAnswer("",
            "SELECT id FROM o WHERE k IN (SELECT k FROM i WHERE o.k IS NULL) ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 29",
            "SELECT id FROM o WHERE k IN (SELECT k FROM i WHERE o.k IS NULL) OR id = 1 ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("DELETE FROM t WHERE EXISTS (SELECT 1 FROM i WHERE t.k IS NULL) OR id = 99");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 17",
            "SELECT id, k IN (SELECT k FROM i WHERE o.k IS NOT NULL) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 15",
            "SELECT id, k = ANY (SELECT k FROM i WHERE o.k IS NOT NULL) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 16",
            "SELECT id, k >= ANY (SELECT k FROM i WHERE o.k IS NOT NULL) FROM o ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|true ;; 4|false ;; 5|false",
            "SELECT id, k = ALL (SELECT k FROM i WHERE o.k IS NOT NULL) FROM o ORDER BY id");
        assertAnswer("1|false ;; 2|false ;; 3|true ;; 4|false ;; 5|null",
            "SELECT id, k NOT IN (SELECT k FROM i WHERE o.k IS NOT NULL) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 22",
            "SELECT id, NOT (k IN (SELECT k FROM i WHERE o.k IS NOT NULL)) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 22",
            "SELECT id, (k, k) IN (SELECT k, k FROM i WHERE o.k IS NOT NULL) FROM o ORDER BY id");
        assertAnswer("1 ;; 2 ;; 4",
            "SELECT id FROM o WHERE k IN (SELECT k FROM i WHERE o.k IS NOT NULL) ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 29",
            "SELECT id FROM o WHERE k IN (SELECT k FROM i WHERE o.k IS NOT NULL) OR id = 1 ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("DELETE FROM t WHERE EXISTS (SELECT 1 FROM i WHERE t.k IS NOT NULL) OR id = 99");
        assertAnswer("1|false ;; 2|true ;; 3|false ;; 4|true ;; 5|false",
            "SELECT id, k IN (SELECT k FROM i WHERE o.k = 2) FROM o ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|false ;; 4|true ;; 5|false",
            "SELECT id, k = ANY (SELECT k FROM i WHERE o.k = 2) FROM o ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|false ;; 4|true ;; 5|false",
            "SELECT id, k >= ANY (SELECT k FROM i WHERE o.k = 2) FROM o ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|true ;; 4|false ;; 5|true",
            "SELECT id, k = ALL (SELECT k FROM i WHERE o.k = 2) FROM o ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|true ;; 4|false ;; 5|true",
            "SELECT id, k NOT IN (SELECT k FROM i WHERE o.k = 2) FROM o ORDER BY id");
        assertAnswer("1|true ;; 2|false ;; 3|true ;; 4|false ;; 5|true",
            "SELECT id, NOT (k IN (SELECT k FROM i WHERE o.k = 2)) FROM o ORDER BY id");
        assertAnswer("1|false ;; 2|true ;; 3|false ;; 4|true ;; 5|false",
            "SELECT id, (k, k) IN (SELECT k, k FROM i WHERE o.k = 2) FROM o ORDER BY id");
        assertAnswer("2 ;; 4",
            "SELECT id FROM o WHERE k IN (SELECT k FROM i WHERE o.k = 2) ORDER BY id");
        assertAnswer("1 ;; 2 ;; 4",
            "SELECT id FROM o WHERE k IN (SELECT k FROM i WHERE o.k = 2) OR id = 1 ORDER BY id");
        assertAnswer("2 ;; 4",
            "SELECT o.id FROM o JOIN nn ON nn.k = 1 AND o.k IN (SELECT k FROM i WHERE o.k = 2) ORDER BY"
            + " o.id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("UPDATE t SET b = EXISTS (SELECT 1 FROM i WHERE t.k = 2)");
        assertAnswer("1|false ;; 2|true ;; 3|false ;; 4|true ;; 5|false",
            "SELECT id, b FROM t ORDER BY id");
        engine.execute("DELETE FROM t WHERE EXISTS (SELECT 1 FROM i WHERE t.k = 2) OR id = 99");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 17",
            "SELECT id, k IN (SELECT k FROM i WHERE i.k = o.k AND o.k IS NULL) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 15",
            "SELECT id, k = ANY (SELECT k FROM i WHERE i.k = o.k AND o.k IS NULL) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 16",
            "SELECT id, k >= ANY (SELECT k FROM i WHERE i.k = o.k AND o.k IS NULL) FROM o ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, k = ALL (SELECT k FROM i WHERE i.k = o.k AND o.k IS NULL) FROM o ORDER BY id");
        assertAnswer("1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true",
            "SELECT id, k NOT IN (SELECT k FROM i WHERE i.k = o.k AND o.k IS NULL) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 22",
            "SELECT id, NOT (k IN (SELECT k FROM i WHERE i.k = o.k AND o.k IS NULL)) FROM o ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 22",
            "SELECT id, (k, k) IN (SELECT k, k FROM i WHERE i.k = o.k AND o.k IS NULL) FROM o ORDER BY "
            + "id");
        assertAnswer("",
            "SELECT id FROM o WHERE k IN (SELECT k FROM i WHERE i.k = o.k AND o.k IS NULL) ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
            + "position 29",
            "SELECT id FROM o WHERE k IN (SELECT k FROM i WHERE i.k = o.k AND o.k IS NULL) OR id = 1 "
            + "ORDER BY id");
    }

    @Test
    public void statements() {
        engine.execute("CREATE OR REPLACE TABLE p (id INT, k INT)");
        engine.execute("INSERT INTO p VALUES (1,0),(2,1),(3,2),(4,3),(5,NULL)");
        engine.execute("CREATE OR REPLACE TABLE e1 (k INT)");
        engine.execute("CREATE OR REPLACE TABLE nn (k INT)");
        engine.execute("INSERT INTO nn VALUES (1),(2)");
        engine.execute("CREATE OR REPLACE TABLE wn (k INT)");
        engine.execute("INSERT INTO wn VALUES (1),(NULL)");
        engine.execute("CREATE OR REPLACE TABLE an (k INT)");
        engine.execute("INSERT INTO an VALUES (NULL),(NULL)");
        engine.execute("CREATE OR REPLACE TABLE w3 (k INT)");
        engine.execute("INSERT INTO w3 VALUES (1),(2),(NULL)");
        engine.execute("CREATE OR REPLACE TABLE o (id INT, k INT, n NUMBER(10,2), f FLOAT, s VARCHAR, d DATE, ts "
            + "TIMESTAMP_NTZ, b BOOLEAN, v VARIANT, a ARRAY, ob OBJECT)");
        engine.execute("INSERT INTO o SELECT 1, 1, 1.50, 1.5, 'a', '2020-01-01'::DATE, '2020-01-01 "
            + "10:00:00'::TIMESTAMP_NTZ, TRUE, TO_VARIANT(1), ARRAY_CONSTRUCT(1,2), "
            + "OBJECT_CONSTRUCT('k',1) UNION ALL SELECT 2, 2, 2.00, 2.0, 'B', '2020-01-02'::DATE, "
            + "'2020-01-02 00:00:00'::TIMESTAMP_NTZ, FALSE, TO_VARIANT('x'), ARRAY_CONSTRUCT(2), "
            + "OBJECT_CONSTRUCT('k',2) UNION ALL SELECT 3, NULL, NULL, NULL, NULL, NULL, NULL, NULL, "
            + "NULL, NULL, NULL UNION ALL SELECT 4, 2, 2.00, 2.0, 'b', '2020-01-03'::DATE, '2020-01-03 "
            + "12:30:00'::TIMESTAMP_NTZ, TRUE, TO_VARIANT(2), ARRAY_CONSTRUCT(3,NULL), OBJECT_CONSTRUCT()"
            + " UNION ALL SELECT 5, 5, 5.25, 5.25, 'e', '2020-01-05'::DATE, '2020-01-05 "
            + "00:00:00'::TIMESTAMP_NTZ, NULL, PARSE_JSON('null'), ARRAY_CONSTRUCT(), "
            + "OBJECT_CONSTRUCT('z',NULL)");
        engine.execute("CREATE OR REPLACE TABLE i (k INT, n NUMBER(10,2), f FLOAT, s VARCHAR, d DATE, ts "
            + "TIMESTAMP_NTZ, b BOOLEAN, v VARIANT, val INT)");
        engine.execute("INSERT INTO i SELECT 1, 1.50, 1.5, 'A', '2020-01-01'::DATE, '2020-01-01 "
            + "10:00:00'::TIMESTAMP_NTZ, TRUE, TO_VARIANT(1), 10 UNION ALL SELECT 2, 2.00, 2.0, 'b', "
            + "'2020-01-02'::DATE, '2020-01-02 00:00:00'::TIMESTAMP_NTZ, FALSE, TO_VARIANT('x'), 20 UNION"
            + " ALL SELECT 2, 2.10, 2.1, 'b', '2020-01-04'::DATE, '2020-01-04 00:00:00'::TIMESTAMP_NTZ, "
            + "TRUE, TO_VARIANT(2), 21 UNION ALL SELECT NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, "
            + "30 UNION ALL SELECT 3, 3.00, 3.0, 'c', '2020-01-03'::DATE, '2020-01-03 "
            + "12:30:00'::TIMESTAMP_NTZ, NULL, TO_VARIANT(3), NULL");
        engine.execute("CREATE OR REPLACE TABLE e (k INT, n NUMBER(10,2), f FLOAT, s VARCHAR, d DATE, ts "
            + "TIMESTAMP_NTZ, b BOOLEAN, v VARIANT, val INT)");
        engine.execute("CREATE OR REPLACE TABLE one (k INT, val INT)");
        engine.execute("INSERT INTO one VALUES (2, 7)");
        engine.execute("CREATE OR REPLACE TABLE nn (k INT)");
        engine.execute("INSERT INTO nn VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE TABLE wn (k INT)");
        engine.execute("INSERT INTO wn VALUES (1), (NULL)");
        engine.execute("CREATE OR REPLACE TABLE pz (id INT, k INT)");
        engine.execute("INSERT INTO pz VALUES (0,0),(1,1),(2,2),(3,3),(4,NULL)");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("DELETE FROM t WHERE EXISTS (SELECT 1 FROM i WHERE t.k IS NULL) OR id = 99");
        assertAnswer("1|1|true ;; 2|2|false ;; 4|2|true ;; 5|5|null",
            "SELECT id, k, b FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("DELETE FROM t WHERE k IN (SELECT k FROM i WHERE t.k IS NULL) OR id = 99");
        assertAnswer("1|1|true ;; 2|2|false ;; 3|null|null ;; 4|2|true ;; 5|5|null",
            "SELECT id, k, b FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("DELETE FROM t WHERE NOT (k IN (SELECT k FROM i WHERE t.k IS NULL))");
        assertAnswer("",
            "SELECT id, k, b FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("DELETE FROM t WHERE NOT EXISTS (SELECT 1 FROM i WHERE t.k IS NULL)");
        assertAnswer("3|null|null",
            "SELECT id, k, b FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        assertAnswer("1|1|true ;; 2|2|false ;; 3|null|null ;; 4|2|true ;; 5|5|null",
            "SELECT id, k, b FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("UPDATE t SET k = -1 WHERE EXISTS (SELECT 1 FROM i WHERE t.k IS NULL) OR id = 99");
        assertAnswer("1|1|true ;; 2|2|false ;; 3|-1|null ;; 4|2|true ;; 5|5|null",
            "SELECT id, k, b FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("UPDATE t SET k = -1 WHERE k IN (SELECT k FROM i WHERE t.k IS NULL) OR id = 99");
        assertAnswer("1|1|true ;; 2|2|false ;; 3|null|null ;; 4|2|true ;; 5|5|null",
            "SELECT id, k, b FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        assertAnswer("1|1|true ;; 2|2|false ;; 3|null|null ;; 4|2|true ;; 5|5|null",
            "SELECT id, k, b FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        assertAnswer("1|1|true ;; 2|2|false ;; 3|null|null ;; 4|2|true ;; 5|5|null",
            "SELECT id, k, b FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("DELETE FROM t WHERE EXISTS (SELECT 1 FROM i WHERE t.k IS NOT NULL) OR id = 99");
        assertAnswer("3|null|null",
            "SELECT id, k, b FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("DELETE FROM t WHERE k IN (SELECT k FROM i WHERE t.k IS NOT NULL) OR id = 99");
        assertAnswer("3|null|null ;; 5|5|null",
            "SELECT id, k, b FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("DELETE FROM t WHERE NOT (k IN (SELECT k FROM i WHERE t.k IS NOT NULL))");
        assertAnswer("1|1|true ;; 2|2|false ;; 4|2|true",
            "SELECT id, k, b FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("DELETE FROM t WHERE NOT EXISTS (SELECT 1 FROM i WHERE t.k IS NOT NULL)");
        assertAnswer("1|1|true ;; 2|2|false ;; 4|2|true ;; 5|5|null",
            "SELECT id, k, b FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        assertAnswer("1|1|true ;; 2|2|false ;; 3|null|null ;; 4|2|true ;; 5|5|null",
            "SELECT id, k, b FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("UPDATE t SET k = -1 WHERE EXISTS (SELECT 1 FROM i WHERE t.k IS NOT NULL) OR id = 99");
        assertAnswer("1|-1|true ;; 2|-1|false ;; 3|null|null ;; 4|-1|true ;; 5|-1|null",
            "SELECT id, k, b FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("UPDATE t SET k = -1 WHERE k IN (SELECT k FROM i WHERE t.k IS NOT NULL) OR id = 99");
        assertAnswer("1|-1|true ;; 2|-1|false ;; 3|null|null ;; 4|-1|true ;; 5|5|null",
            "SELECT id, k, b FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        assertAnswer("1|1|true ;; 2|2|false ;; 3|null|null ;; 4|2|true ;; 5|5|null",
            "SELECT id, k, b FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        assertAnswer("1|1|true ;; 2|2|false ;; 3|null|null ;; 4|2|true ;; 5|5|null",
            "SELECT id, k, b FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("DELETE FROM t WHERE EXISTS (SELECT 1 FROM i WHERE i.k = t.k AND t.k IS NULL) OR id = 99");
        assertAnswer("1|1|true ;; 2|2|false ;; 3|null|null ;; 4|2|true ;; 5|5|null",
            "SELECT id, k, b FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("DELETE FROM t WHERE k IN (SELECT k FROM i WHERE i.k = t.k AND t.k IS NULL) OR id = 99");
        assertAnswer("1|1|true ;; 2|2|false ;; 3|null|null ;; 4|2|true ;; 5|5|null",
            "SELECT id, k, b FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("DELETE FROM t WHERE NOT (k IN (SELECT k FROM i WHERE i.k = t.k AND t.k IS NULL))");
        assertAnswer("",
            "SELECT id, k, b FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("DELETE FROM t WHERE NOT EXISTS (SELECT 1 FROM i WHERE i.k = t.k AND t.k IS NULL)");
        assertAnswer("",
            "SELECT id, k, b FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        assertAnswer("1|1|true ;; 2|2|false ;; 3|null|null ;; 4|2|true ;; 5|5|null",
            "SELECT id, k, b FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("UPDATE t SET k = -1 WHERE EXISTS (SELECT 1 FROM i WHERE i.k = t.k AND t.k IS NULL) OR id ="
            + " 99");
        assertAnswer("1|1|true ;; 2|2|false ;; 3|null|null ;; 4|2|true ;; 5|5|null",
            "SELECT id, k, b FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("UPDATE t SET k = -1 WHERE k IN (SELECT k FROM i WHERE i.k = t.k AND t.k IS NULL) OR id = "
            + "99");
        assertAnswer("1|1|true ;; 2|2|false ;; 3|null|null ;; 4|2|true ;; 5|5|null",
            "SELECT id, k, b FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        assertAnswer("1|1|true ;; 2|2|false ;; 3|null|null ;; 4|2|true ;; 5|5|null",
            "SELECT id, k, b FROM t ORDER BY id");
        engine.execute("CREATE OR REPLACE TABLE t AS SELECT * FROM o");
        assertAnswer("1|1|true ;; 2|2|false ;; 3|null|null ;; 4|2|true ;; 5|5|null",
            "SELECT id, k, b FROM t ORDER BY id");
    }
}
