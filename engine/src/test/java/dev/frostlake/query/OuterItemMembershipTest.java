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
 * IN, NOT IN, ANY and ALL whose subquery's select item reads the outer row (live-verified). An IN or an ANY is
 * refused whatever else the item reads, and over an empty table too unless its item or its WHERE reads a column of
 * its own. A NOT IN or an ALL over an item reading the outer row alone is answered; over one also reading a varying
 * column of the subquery's own, {@code = ALL}, {@code > ALL} and the other ALL comparisons but {@code <> ALL} are
 * refused, and NOT IN and {@code <> ALL} are too outside a WHERE's conjuncts. A term of outer names the statistics
 * pin to one value, {@code 0 * o.id} over an id holding no NULL, reads nothing of the outer row; an aggregating item
 * is held to the aggregate rules; a LIMIT changes nothing. An IN whose item holds a window beside the outer row is
 * refused ahead of every pruning.
 */
public class OuterItemMembershipTest extends MeasuredCellsTestSupport {

    @Test
    public void anItemReadingTheOuterRowByPlaceAndTable() {
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
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT o.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k FROM i) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k <> ANY (SELECT o.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 30"},
            {"SELECT id FROM o WHERE o.k > ANY (SELECT o.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k FROM one) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k FROM one) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT o.k FROM one) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k FROM one) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k FROM one) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k FROM one) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k <> ANY (SELECT o.k FROM one) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 30"},
            {"SELECT id FROM o WHERE o.k > ANY (SELECT o.k FROM one) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k FROM e) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k FROM e) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT o.k FROM e) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k FROM e) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k FROM e) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k FROM e) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k <> ANY (SELECT o.k FROM e) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 30"},
            {"SELECT id FROM o WHERE o.k > ANY (SELECT o.k FROM e) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k + 1 FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k + 1 FROM i) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT o.k + 1 FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k + 1 FROM i) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k + 1 FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k + 1 FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k <> ANY (SELECT o.k + 1 FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 30"},
            {"SELECT id FROM o WHERE o.k > ANY (SELECT o.k + 1 FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k + 1 FROM one) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k + 1 FROM one) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT o.k + 1 FROM one) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k + 1 FROM one) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k + 1 FROM one) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k + 1 FROM one) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k <> ANY (SELECT o.k + 1 FROM one) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 30"},
            {"SELECT id FROM o WHERE o.k > ANY (SELECT o.k + 1 FROM one) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k + 1 FROM e) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k + 1 FROM e) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT o.k + 1 FROM e) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k + 1 FROM e) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k + 1 FROM e) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k + 1 FROM e) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k <> ANY (SELECT o.k + 1 FROM e) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 30"},
            {"SELECT id FROM o WHERE o.k > ANY (SELECT o.k + 1 FROM e) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k + i.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k + i.k FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT o.k + i.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k + i.k FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k + i.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k + i.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k <> ANY (SELECT o.k + i.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 30"},
            {"SELECT id FROM o WHERE o.k > ANY (SELECT o.k + i.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k + one.k FROM one) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k + one.k FROM one) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT o.k + one.k FROM one) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k + one.k FROM one) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k + one.k FROM one) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k + one.k FROM one) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k <> ANY (SELECT o.k + one.k FROM one) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 30"},
            {"SELECT id FROM o WHERE o.k > ANY (SELECT o.k + one.k FROM one) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k + e.k FROM e) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k + e.k FROM e) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT o.k + e.k FROM e) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k + e.k FROM e) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k + e.k FROM e) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k + e.k FROM e) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k <> ANY (SELECT o.k + e.k FROM e) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k > ANY (SELECT o.k + e.k FROM e) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k IN (SELECT i.k + o.id - o.id FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT i.k + o.id - o.id FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT i.k + o.id - o.id FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT i.k + o.id - o.id FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT i.k + o.id - o.id FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT i.k + o.id - o.id FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k <> ANY (SELECT i.k + o.id - o.id FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 30"},
            {"SELECT id FROM o WHERE o.k > ANY (SELECT i.k + o.id - o.id FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k IN (SELECT one.k + o.id - o.id FROM one) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT one.k + o.id - o.id FROM one) ORDER BY id",
                "1 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT one.k + o.id - o.id FROM one) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT one.k + o.id - o.id FROM one) ORDER BY id",
                "1 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT one.k + o.id - o.id FROM one) ORDER BY id",
                "2 ;; 4"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT one.k + o.id - o.id FROM one) ORDER BY id",
                "5"},
            {"SELECT id FROM o WHERE o.k <> ANY (SELECT one.k + o.id - o.id FROM one) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 30"},
            {"SELECT id FROM o WHERE o.k > ANY (SELECT one.k + o.id - o.id FROM one) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k IN (SELECT e.k + o.id - o.id FROM e) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT e.k + o.id - o.id FROM e) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT e.k + o.id - o.id FROM e) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT e.k + o.id - o.id FROM e) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT e.k + o.id - o.id FROM e) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT e.k + o.id - o.id FROM e) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k <> ANY (SELECT e.k + o.id - o.id FROM e) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k > ANY (SELECT e.k + o.id - o.id FROM e) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k IN (SELECT i.k + 0 * o.id FROM i) ORDER BY id",
                "1 ;; 2 ;; 4"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT i.k + 0 * o.id FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT i.k + 0 * o.id FROM i) ORDER BY id",
                "1 ;; 2 ;; 4"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT i.k + 0 * o.id FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT i.k + 0 * o.id FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT i.k + 0 * o.id FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k <> ANY (SELECT i.k + 0 * o.id FROM i) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k > ANY (SELECT i.k + 0 * o.id FROM i) ORDER BY id",
                "2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k IN (SELECT one.k + 0 * o.id FROM one) ORDER BY id",
                "2 ;; 4"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT one.k + 0 * o.id FROM one) ORDER BY id",
                "1 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT one.k + 0 * o.id FROM one) ORDER BY id",
                "2 ;; 4"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT one.k + 0 * o.id FROM one) ORDER BY id",
                "1 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT one.k + 0 * o.id FROM one) ORDER BY id",
                "2 ;; 4"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT one.k + 0 * o.id FROM one) ORDER BY id",
                "5"},
            {"SELECT id FROM o WHERE o.k <> ANY (SELECT one.k + 0 * o.id FROM one) ORDER BY id",
                "1 ;; 5"},
            {"SELECT id FROM o WHERE o.k > ANY (SELECT one.k + 0 * o.id FROM one) ORDER BY id",
                "5"},
            {"SELECT id FROM o WHERE o.k IN (SELECT e.k + 0 * o.id FROM e) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT e.k + 0 * o.id FROM e) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT e.k + 0 * o.id FROM e) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT e.k + 0 * o.id FROM e) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT e.k + 0 * o.id FROM e) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT e.k + 0 * o.id FROM e) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k <> ANY (SELECT e.k + 0 * o.id FROM e) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k > ANY (SELECT e.k + 0 * o.id FROM e) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k IN (SELECT i.k FROM i WHERE i.k = o.k) ORDER BY id",
                "1 ;; 2 ;; 4"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT i.k FROM i WHERE i.k = o.k) ORDER BY id",
                "3 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT i.k FROM i WHERE i.k = o.k) ORDER BY id",
                "1 ;; 2 ;; 4"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT i.k FROM i WHERE i.k = o.k) ORDER BY id",
                "3 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT i.k FROM i WHERE i.k = o.k) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT i.k FROM i WHERE i.k = o.k) ORDER BY id",
                "3 ;; 5"},
            {"SELECT id FROM o WHERE o.k <> ANY (SELECT i.k FROM i WHERE i.k = o.k) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k > ANY (SELECT i.k FROM i WHERE i.k = o.k) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k IN (SELECT one.k FROM one WHERE one.k = o.k) ORDER BY id",
                "2 ;; 4"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT one.k FROM one WHERE one.k = o.k) ORDER BY id",
                "1 ;; 3 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT one.k FROM one WHERE one.k = o.k) ORDER BY id",
                "2 ;; 4"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT one.k FROM one WHERE one.k = o.k) ORDER BY id",
                "1 ;; 3 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT one.k FROM one WHERE one.k = o.k) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT one.k FROM one WHERE one.k = o.k) ORDER BY id",
                "1 ;; 3 ;; 5"},
            {"SELECT id FROM o WHERE o.k <> ANY (SELECT one.k FROM one WHERE one.k = o.k) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k > ANY (SELECT one.k FROM one WHERE one.k = o.k) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k IN (SELECT e.k FROM e WHERE e.k = o.k) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT e.k FROM e WHERE e.k = o.k) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT e.k FROM e WHERE e.k = o.k) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT e.k FROM e WHERE e.k = o.k) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT e.k FROM e WHERE e.k = o.k) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT e.k FROM e WHERE e.k = o.k) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k <> ANY (SELECT e.k FROM e WHERE e.k = o.k) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k > ANY (SELECT e.k FROM e WHERE e.k = o.k) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k IN (SELECT i.k FROM i) ORDER BY id",
                "1 ;; 2 ;; 4"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT i.k FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT i.k FROM i) ORDER BY id",
                "1 ;; 2 ;; 4"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT i.k FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT i.k FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT i.k FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k <> ANY (SELECT i.k FROM i) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k > ANY (SELECT i.k FROM i) ORDER BY id",
                "2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k IN (SELECT one.k FROM one) ORDER BY id",
                "2 ;; 4"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT one.k FROM one) ORDER BY id",
                "1 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT one.k FROM one) ORDER BY id",
                "2 ;; 4"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT one.k FROM one) ORDER BY id",
                "1 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT one.k FROM one) ORDER BY id",
                "2 ;; 4"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT one.k FROM one) ORDER BY id",
                "5"},
            {"SELECT id FROM o WHERE o.k <> ANY (SELECT one.k FROM one) ORDER BY id",
                "1 ;; 5"},
            {"SELECT id FROM o WHERE o.k > ANY (SELECT one.k FROM one) ORDER BY id",
                "5"},
            {"SELECT id FROM o WHERE o.k IN (SELECT e.k FROM e) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT e.k FROM e) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT e.k FROM e) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT e.k FROM e) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT e.k FROM e) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT e.k FROM e) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k <> ANY (SELECT e.k FROM e) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k > ANY (SELECT e.k FROM e) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k FROM i WHERE i.k = o.k) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k FROM i WHERE i.k = o.k) ORDER BY id",
                "3 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT o.k FROM i WHERE i.k = o.k) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k FROM i WHERE i.k = o.k) ORDER BY id",
                "3 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k FROM i WHERE i.k = o.k) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k FROM i WHERE i.k = o.k) ORDER BY id",
                "3 ;; 5"},
            {"SELECT id FROM o WHERE o.k <> ANY (SELECT o.k FROM i WHERE i.k = o.k) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 30"},
            {"SELECT id FROM o WHERE o.k > ANY (SELECT o.k FROM i WHERE i.k = o.k) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k FROM one WHERE one.k = o.k) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k FROM one WHERE one.k = o.k) ORDER BY id",
                "1 ;; 3 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT o.k FROM one WHERE one.k = o.k) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k FROM one WHERE one.k = o.k) ORDER BY id",
                "1 ;; 3 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k FROM one WHERE one.k = o.k) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k FROM one WHERE one.k = o.k) ORDER BY id",
                "1 ;; 3 ;; 5"},
            {"SELECT id FROM o WHERE o.k <> ANY (SELECT o.k FROM one WHERE one.k = o.k) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 30"},
            {"SELECT id FROM o WHERE o.k > ANY (SELECT o.k FROM one WHERE one.k = o.k) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k FROM e WHERE e.k = o.k) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k FROM e WHERE e.k = o.k) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT o.k FROM e WHERE e.k = o.k) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k FROM e WHERE e.k = o.k) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k FROM e WHERE e.k = o.k) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k FROM e WHERE e.k = o.k) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k <> ANY (SELECT o.k FROM e WHERE e.k = o.k) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k > ANY (SELECT o.k FROM e WHERE e.k = o.k) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k + i.k FROM i WHERE i.val > o.id) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k + i.k FROM i WHERE i.val > o.id) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT o.k + i.k FROM i WHERE i.val > o.id) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k + i.k FROM i WHERE i.val > o.id) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k + i.k FROM i WHERE i.val > o.id) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k + i.k FROM i WHERE i.val > o.id) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k <> ANY (SELECT o.k + i.k FROM i WHERE i.val > o.id) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 30"},
            {"SELECT id FROM o WHERE o.k > ANY (SELECT o.k + i.k FROM i WHERE i.val > o.id) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k + one.k FROM one WHERE one.val > o.id) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k + one.k FROM one WHERE one.val > o.id) ORDER "
                + "BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT o.k + one.k FROM one WHERE one.val > o.id) ORDER BY"
                + " id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k + one.k FROM one WHERE one.val > o.id) ORDER "
                + "BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k + one.k FROM one WHERE one.val > o.id) ORDER BY"
                + " id",
                ""},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k + one.k FROM one WHERE one.val > o.id) ORDER BY"
                + " id",
                ""},
            {"SELECT id FROM o WHERE o.k <> ANY (SELECT o.k + one.k FROM one WHERE one.val > o.id) ORDER "
                + "BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 30"},
            {"SELECT id FROM o WHERE o.k > ANY (SELECT o.k + one.k FROM one WHERE one.val > o.id) ORDER BY"
                + " id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k + e.k FROM e WHERE e.val > o.id) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k + e.k FROM e WHERE e.val > o.id) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT o.k + e.k FROM e WHERE e.val > o.id) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k + e.k FROM e WHERE e.val > o.id) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k + e.k FROM e WHERE e.val > o.id) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k + e.k FROM e WHERE e.val > o.id) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k <> ANY (SELECT o.k + e.k FROM e WHERE e.val > o.id) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k > ANY (SELECT o.k + e.k FROM e WHERE e.val > o.id) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.id FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.id FROM i) ORDER BY id",
                "4"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT o.id FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.id FROM i) ORDER BY id",
                "4"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.id FROM i) ORDER BY id",
                "1 ;; 2 ;; 5"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.id FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k <> ANY (SELECT o.id FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 30"},
            {"SELECT id FROM o WHERE o.k > ANY (SELECT o.id FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.id FROM one) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.id FROM one) ORDER BY id",
                "4"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT o.id FROM one) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.id FROM one) ORDER BY id",
                "4"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.id FROM one) ORDER BY id",
                "1 ;; 2 ;; 5"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.id FROM one) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k <> ANY (SELECT o.id FROM one) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 30"},
            {"SELECT id FROM o WHERE o.k > ANY (SELECT o.id FROM one) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.id FROM e) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.id FROM e) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT o.id FROM e) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.id FROM e) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.id FROM e) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.id FROM e) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k <> ANY (SELECT o.id FROM e) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 30"},
            {"SELECT id FROM o WHERE o.k > ANY (SELECT o.id FROM e) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id, o.k IN (SELECT o.k FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 19"},
            {"SELECT id, o.k = ANY (SELECT o.k FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, o.k <> ANY (SELECT o.k FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 18"},
            {"SELECT id, o.k > ANY (SELECT o.k FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, o.k IN (SELECT o.k FROM one) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 19"},
            {"SELECT id, o.k = ANY (SELECT o.k FROM one) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, o.k <> ANY (SELECT o.k FROM one) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 18"},
            {"SELECT id, o.k > ANY (SELECT o.k FROM one) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, o.k IN (SELECT o.k FROM e) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 19"},
            {"SELECT id, o.k = ANY (SELECT o.k FROM e) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, o.k <> ANY (SELECT o.k FROM e) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 18"},
            {"SELECT id, o.k > ANY (SELECT o.k FROM e) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, o.k IN (SELECT o.k + 1 FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 19"},
            {"SELECT id, o.k = ANY (SELECT o.k + 1 FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, o.k <> ANY (SELECT o.k + 1 FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 18"},
            {"SELECT id, o.k > ANY (SELECT o.k + 1 FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, o.k IN (SELECT o.k + 1 FROM one) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 19"},
            {"SELECT id, o.k = ANY (SELECT o.k + 1 FROM one) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, o.k <> ANY (SELECT o.k + 1 FROM one) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 18"},
            {"SELECT id, o.k > ANY (SELECT o.k + 1 FROM one) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, o.k IN (SELECT o.k + 1 FROM e) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 19"},
            {"SELECT id, o.k = ANY (SELECT o.k + 1 FROM e) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, o.k <> ANY (SELECT o.k + 1 FROM e) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 18"},
            {"SELECT id, o.k > ANY (SELECT o.k + 1 FROM e) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, o.k IN (SELECT o.k + i.k FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 19"},
            {"SELECT id, o.k NOT IN (SELECT o.k + i.k FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, o.k = ANY (SELECT o.k + i.k FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, o.k <> ALL (SELECT o.k + i.k FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 18"},
            {"SELECT id, o.k = ALL (SELECT o.k + i.k FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, o.k > ALL (SELECT o.k + i.k FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, o.k <> ANY (SELECT o.k + i.k FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 18"},
            {"SELECT id, o.k > ANY (SELECT o.k + i.k FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, o.k IN (SELECT o.k + one.k FROM one) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 19"},
            {"SELECT id, o.k = ANY (SELECT o.k + one.k FROM one) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, o.k <> ANY (SELECT o.k + one.k FROM one) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 18"},
            {"SELECT id, o.k > ANY (SELECT o.k + one.k FROM one) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, o.k IN (SELECT i.k + o.id - o.id FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 19"},
            {"SELECT id, o.k NOT IN (SELECT i.k + o.id - o.id FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, o.k = ANY (SELECT i.k + o.id - o.id FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, o.k <> ALL (SELECT i.k + o.id - o.id FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 18"},
            {"SELECT id, o.k = ALL (SELECT i.k + o.id - o.id FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, o.k > ALL (SELECT i.k + o.id - o.id FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, o.k <> ANY (SELECT i.k + o.id - o.id FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 18"},
            {"SELECT id, o.k > ANY (SELECT i.k + o.id - o.id FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, o.k IN (SELECT one.k + o.id - o.id FROM one) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 19"},
            {"SELECT id, o.k = ANY (SELECT one.k + o.id - o.id FROM one) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, o.k <> ANY (SELECT one.k + o.id - o.id FROM one) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 18"},
            {"SELECT id, o.k > ANY (SELECT one.k + o.id - o.id FROM one) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, o.k NOT IN (SELECT i.k FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, o.k IN (SELECT o.k FROM i WHERE i.k = o.k) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 19"},
            {"SELECT id, o.k = ANY (SELECT o.k FROM i WHERE i.k = o.k) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, o.k <> ANY (SELECT o.k FROM i WHERE i.k = o.k) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 18"},
            {"SELECT id, o.k > ANY (SELECT o.k FROM i WHERE i.k = o.k) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, o.k IN (SELECT o.k FROM one WHERE one.k = o.k) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 19"},
            {"SELECT id, o.k = ANY (SELECT o.k FROM one WHERE one.k = o.k) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, o.k <> ANY (SELECT o.k FROM one WHERE one.k = o.k) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 18"},
            {"SELECT id, o.k > ANY (SELECT o.k FROM one WHERE one.k = o.k) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, o.k IN (SELECT o.k + i.k FROM i WHERE i.val > o.id) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 19"},
            {"SELECT id, o.k NOT IN (SELECT o.k + i.k FROM i WHERE i.val > o.id) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, o.k = ANY (SELECT o.k + i.k FROM i WHERE i.val > o.id) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, o.k <> ALL (SELECT o.k + i.k FROM i WHERE i.val > o.id) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 18"},
            {"SELECT id, o.k = ALL (SELECT o.k + i.k FROM i WHERE i.val > o.id) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, o.k > ALL (SELECT o.k + i.k FROM i WHERE i.val > o.id) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, o.k <> ANY (SELECT o.k + i.k FROM i WHERE i.val > o.id) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 18"},
            {"SELECT id, o.k > ANY (SELECT o.k + i.k FROM i WHERE i.val > o.id) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, o.k IN (SELECT o.k + one.k FROM one WHERE one.val > o.id) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 19"},
            {"SELECT id, o.k = ANY (SELECT o.k + one.k FROM one WHERE one.val > o.id) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, o.k <> ANY (SELECT o.k + one.k FROM one WHERE one.val > o.id) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 18"},
            {"SELECT id, o.k > ANY (SELECT o.k + one.k FROM one WHERE one.val > o.id) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, o.k IN (SELECT o.id FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 19"},
            {"SELECT id, o.k = ANY (SELECT o.id FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, o.k <> ANY (SELECT o.id FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 18"},
            {"SELECT id, o.k > ANY (SELECT o.id FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, o.k IN (SELECT o.id FROM one) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 19"},
            {"SELECT id, o.k = ANY (SELECT o.id FROM one) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, o.k <> ANY (SELECT o.id FROM one) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 18"},
            {"SELECT id, o.k > ANY (SELECT o.id FROM one) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, o.k IN (SELECT o.id FROM e) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 19"},
            {"SELECT id, o.k = ANY (SELECT o.id FROM e) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id, o.k <> ANY (SELECT o.id FROM e) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 18"},
            {"SELECT id, o.k > ANY (SELECT o.id FROM e) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
        });
    }

    @Test
    public void neighbouringItems() {
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
        engine.execute("CREATE OR REPLACE TABLE tw (k INT, val INT)");
        engine.execute("INSERT INTO tw VALUES (2, 1), (2, 2)");
        assertCells(new String[][] {
            {"SELECT id FROM o WHERE o.k IN (SELECT i.k + o.id * 0 FROM i) ORDER BY id",
                "1 ;; 2 ;; 4"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT i.k + o.id * 0 FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT i.k + o.id * 0 FROM i) ORDER BY id",
                "1 ;; 2 ;; 4"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT i.k + o.id * 0 FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT i.k + o.id * 0 FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT i.k + o.id * 0 FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k < ALL (SELECT i.k + o.id * 0 FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k != ALL (SELECT i.k + o.id * 0 FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k >= ALL (SELECT i.k + o.id * 0 FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k IN (SELECT i.k + 0 * o.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT i.k + 0 * o.k FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT i.k + 0 * o.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT i.k + 0 * o.k FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT i.k + 0 * o.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT i.k + 0 * o.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k < ALL (SELECT i.k + 0 * o.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k != ALL (SELECT i.k + 0 * o.k FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k >= ALL (SELECT i.k + 0 * o.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 30"},
            {"SELECT id FROM o WHERE o.k IN (SELECT i.k + o.k - o.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT i.k + o.k - o.k FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT i.k + o.k - o.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT i.k + o.k - o.k FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT i.k + o.k - o.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT i.k + o.k - o.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k < ALL (SELECT i.k + o.k - o.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k != ALL (SELECT i.k + o.k - o.k FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k >= ALL (SELECT i.k + o.k - o.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 30"},
            {"SELECT id FROM o WHERE o.k IN (SELECT i.k + LEAST(o.id, 0) FROM i) ORDER BY id",
                "1 ;; 2 ;; 4"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT i.k + LEAST(o.id, 0) FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT i.k + LEAST(o.id, 0) FROM i) ORDER BY id",
                "1 ;; 2 ;; 4"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT i.k + LEAST(o.id, 0) FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT i.k + LEAST(o.id, 0) FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT i.k + LEAST(o.id, 0) FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k < ALL (SELECT i.k + LEAST(o.id, 0) FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k != ALL (SELECT i.k + LEAST(o.id, 0) FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k >= ALL (SELECT i.k + LEAST(o.id, 0) FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k IN (SELECT i.k + o.id - 1 FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT i.k + o.id - 1 FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT i.k + o.id - 1 FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT i.k + o.id - 1 FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT i.k + o.id - 1 FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT i.k + o.id - 1 FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k < ALL (SELECT i.k + o.id - 1 FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k != ALL (SELECT i.k + o.id - 1 FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k >= ALL (SELECT i.k + o.id - 1 FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 30"},
            {"SELECT id FROM o WHERE o.k IN (SELECT i.k + IFF(o.id > 0, 0, 0) FROM i) ORDER BY id",
                "1 ;; 2 ;; 4"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT i.k + IFF(o.id > 0, 0, 0) FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT i.k + IFF(o.id > 0, 0, 0) FROM i) ORDER BY id",
                "1 ;; 2 ;; 4"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT i.k + IFF(o.id > 0, 0, 0) FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT i.k + IFF(o.id > 0, 0, 0) FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT i.k + IFF(o.id > 0, 0, 0) FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k < ALL (SELECT i.k + IFF(o.id > 0, 0, 0) FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k != ALL (SELECT i.k + IFF(o.id > 0, 0, 0) FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k >= ALL (SELECT i.k + IFF(o.id > 0, 0, 0) FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k IN (SELECT 0 * o.id FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT 0 * o.id FROM i) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT 0 * o.id FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT 0 * o.id FROM i) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT 0 * o.id FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT 0 * o.id FROM i) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k < ALL (SELECT 0 * o.id FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k != ALL (SELECT 0 * o.id FROM i) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k >= ALL (SELECT 0 * o.id FROM i) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k IN (SELECT i.k + (o.id - o.id) FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT i.k + (o.id - o.id) FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT i.k + (o.id - o.id) FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT i.k + (o.id - o.id) FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT i.k + (o.id - o.id) FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT i.k + (o.id - o.id) FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k < ALL (SELECT i.k + (o.id - o.id) FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k != ALL (SELECT i.k + (o.id - o.id) FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k >= ALL (SELECT i.k + (o.id - o.id) FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 30"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k FROM i WHERE FALSE) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k FROM i WHERE FALSE) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT o.k FROM i WHERE FALSE) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k FROM i WHERE FALSE) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k FROM i WHERE FALSE) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k FROM i WHERE FALSE) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k < ALL (SELECT o.k FROM i WHERE FALSE) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k != ALL (SELECT o.k FROM i WHERE FALSE) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k >= ALL (SELECT o.k FROM i WHERE FALSE) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k FROM i WHERE i.k > 100) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k FROM i WHERE i.k > 100) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k FROM i WHERE i.k > 100) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k FROM i WHERE i.k > 100) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k < ALL (SELECT o.k FROM i WHERE i.k > 100) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k != ALL (SELECT o.k FROM i WHERE i.k > 100) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k >= ALL (SELECT o.k FROM i WHERE i.k > 100) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k FROM i LIMIT 0) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k FROM i LIMIT 0) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT o.k FROM i LIMIT 0) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k FROM i LIMIT 0) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k FROM i LIMIT 0) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k FROM i LIMIT 0) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k < ALL (SELECT o.k FROM i LIMIT 0) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k != ALL (SELECT o.k FROM i LIMIT 0) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k >= ALL (SELECT o.k FROM i LIMIT 0) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k FROM e WHERE e.val > 0) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k FROM e WHERE e.val > 0) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT o.k FROM e WHERE e.val > 0) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k FROM e WHERE e.val > 0) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k FROM e WHERE e.val > 0) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k FROM e WHERE e.val > 0) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k < ALL (SELECT o.k FROM e WHERE e.val > 0) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k != ALL (SELECT o.k FROM e WHERE e.val > 0) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k >= ALL (SELECT o.k FROM e WHERE e.val > 0) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k FROM e, i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k FROM e, i) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT o.k FROM e, i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k FROM e, i) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k FROM e, i) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k FROM e, i) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k < ALL (SELECT o.k FROM e, i) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k != ALL (SELECT o.k FROM e, i) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k >= ALL (SELECT o.k FROM e, i) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k FROM i WHERE i.val > 100) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k FROM i WHERE i.val > 100) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k FROM i WHERE i.val > 100) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k FROM i WHERE i.val > 100) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k < ALL (SELECT o.k FROM i WHERE i.val > 100) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k != ALL (SELECT o.k FROM i WHERE i.val > 100) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k >= ALL (SELECT o.k FROM i WHERE i.val > 100) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k + 0 * e.k FROM e) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k + 0 * e.k FROM e) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT o.k + 0 * e.k FROM e) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k + 0 * e.k FROM e) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k + 0 * e.k FROM e) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k + 0 * e.k FROM e) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k < ALL (SELECT o.k + 0 * e.k FROM e) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k != ALL (SELECT o.k + 0 * e.k FROM e) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k >= ALL (SELECT o.k + 0 * e.k FROM e) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k FROM e GROUP BY e.k) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k FROM e GROUP BY e.k) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT o.k FROM e GROUP BY e.k) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k FROM e GROUP BY e.k) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k FROM e GROUP BY e.k) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k FROM e GROUP BY e.k) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k < ALL (SELECT o.k FROM e GROUP BY e.k) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k != ALL (SELECT o.k FROM e GROUP BY e.k) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k >= ALL (SELECT o.k FROM e GROUP BY e.k) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k FROM (SELECT * FROM e)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k FROM (SELECT * FROM e)) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT o.k FROM (SELECT * FROM e)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k FROM (SELECT * FROM e)) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k FROM (SELECT * FROM e)) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k FROM (SELECT * FROM e)) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k < ALL (SELECT o.k FROM (SELECT * FROM e)) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k != ALL (SELECT o.k FROM (SELECT * FROM e)) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k >= ALL (SELECT o.k FROM (SELECT * FROM e)) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k + tw.k FROM tw) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k + tw.k FROM tw) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT o.k + tw.k FROM tw) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k + tw.k FROM tw) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k + tw.k FROM tw) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k + tw.k FROM tw) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k < ALL (SELECT o.k + tw.k FROM tw) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k != ALL (SELECT o.k + tw.k FROM tw) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k >= ALL (SELECT o.k + tw.k FROM tw) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k + tw.val FROM tw) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k + tw.val FROM tw) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT o.k + tw.val FROM tw) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k + tw.val FROM tw) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k + tw.val FROM tw) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k + tw.val FROM tw) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k < ALL (SELECT o.k + tw.val FROM tw) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k != ALL (SELECT o.k + tw.val FROM tw) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k >= ALL (SELECT o.k + tw.val FROM tw) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 30"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k + i.k FROM i WHERE i.k = 2) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k + i.k FROM i WHERE i.k = 2) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT o.k + i.k FROM i WHERE i.k = 2) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k + i.k FROM i WHERE i.k = 2) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k + i.k FROM i WHERE i.k = 2) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k + i.k FROM i WHERE i.k = 2) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k < ALL (SELECT o.k + i.k FROM i WHERE i.k = 2) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k != ALL (SELECT o.k + i.k FROM i WHERE i.k = 2) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k >= ALL (SELECT o.k + i.k FROM i WHERE i.k = 2) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 30"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k + one.val FROM one) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k + one.val FROM one) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT o.k + one.val FROM one) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k + one.val FROM one) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k + one.val FROM one) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k + one.val FROM one) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k < ALL (SELECT o.k + one.val FROM one) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k != ALL (SELECT o.k + one.val FROM one) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k >= ALL (SELECT o.k + one.val FROM one) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k * i.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k * i.k FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT o.k * i.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k * i.k FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k * i.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k * i.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k < ALL (SELECT o.k * i.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k != ALL (SELECT o.k * i.k FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k >= ALL (SELECT o.k * i.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 30"},
            {"SELECT id FROM o WHERE o.k IN (SELECT i.k FROM i WHERE i.val > o.id) ORDER BY id",
                "1 ;; 2 ;; 4"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT i.k FROM i WHERE i.val > o.id) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT i.k FROM i WHERE i.val > o.id) ORDER BY id",
                "1 ;; 2 ;; 4"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT i.k FROM i WHERE i.val > o.id) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT i.k FROM i WHERE i.val > o.id) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT i.k FROM i WHERE i.val > o.id) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k < ALL (SELECT i.k FROM i WHERE i.val > o.id) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k != ALL (SELECT i.k FROM i WHERE i.val > o.id) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k >= ALL (SELECT i.k FROM i WHERE i.val > o.id) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k + i.k FROM i WHERE i.k = o.k) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k + i.k FROM i WHERE i.k = o.k) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT o.k + i.k FROM i WHERE i.k = o.k) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k + i.k FROM i WHERE i.k = o.k) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k + i.k FROM i WHERE i.k = o.k) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k + i.k FROM i WHERE i.k = o.k) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k < ALL (SELECT o.k + i.k FROM i WHERE i.k = o.k) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k != ALL (SELECT o.k + i.k FROM i WHERE i.k = o.k) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k >= ALL (SELECT o.k + i.k FROM i WHERE i.k = o.k) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 30"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k FROM i WHERE i.k = 2) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k FROM i WHERE i.k = 2) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT o.k FROM i WHERE i.k = 2) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k FROM i WHERE i.k = 2) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k FROM i WHERE i.k = 2) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k FROM i WHERE i.k = 2) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k < ALL (SELECT o.k FROM i WHERE i.k = 2) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k != ALL (SELECT o.k FROM i WHERE i.k = 2) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k >= ALL (SELECT o.k FROM i WHERE i.k = 2) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
        });
    }

    @Test
    public void placesOfAMixedItem() {
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
        engine.execute("CREATE OR REPLACE TABLE tw (k INT, val INT)");
        engine.execute("INSERT INTO tw VALUES (2, 1), (2, 2)");
        engine.execute("CREATE OR REPLACE TABLE nz (k INT NOT NULL)");
        engine.execute("INSERT INTO nz VALUES (1), (2)");
        assertCells(new String[][] {
            {"SELECT id FROM o WHERE o.k IN (SELECT o.id + nn.k FROM nn) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.id + nn.k FROM nn) OR id = 3 ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE NOT (o.k IN (SELECT o.id + nn.k FROM nn)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 36"},
            {"SELECT id, o.k IN (SELECT o.id + nn.k FROM nn) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 19"},
            {"SELECT id FROM o GROUP BY id, k HAVING o.k IN (SELECT o.id + nn.k FROM nn) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 47"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.id + nn.k FROM nn) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.id + nn.k FROM nn) OR id = 3 ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 35"},
            {"SELECT id FROM o WHERE NOT (o.k NOT IN (SELECT o.id + nn.k FROM nn)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 40"},
            {"SELECT id, o.k NOT IN (SELECT o.id + nn.k FROM nn) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id FROM o GROUP BY id, k HAVING o.k NOT IN (SELECT o.id + nn.k FROM nn) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.id + nn.k FROM nn) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.id + nn.k FROM nn) OR id = 3 ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 30"},
            {"SELECT id FROM o WHERE NOT (o.k <> ALL (SELECT o.id + nn.k FROM nn)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 35"},
            {"SELECT id, o.k <> ALL (SELECT o.id + nn.k FROM nn) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 18"},
            {"SELECT id FROM o GROUP BY id, k HAVING o.k <> ALL (SELECT o.id + nn.k FROM nn) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.id + nn.k FROM nn) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.id + nn.k FROM nn) OR id = 3 ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE NOT (o.k = ALL (SELECT o.id + nn.k FROM nn)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 34"},
            {"SELECT id, o.k = ALL (SELECT o.id + nn.k FROM nn) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id FROM o GROUP BY id, k HAVING o.k = ALL (SELECT o.id + nn.k FROM nn) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 45"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.id + nn.k FROM nn) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.id + nn.k FROM nn) OR id = 3 ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE NOT (o.k > ALL (SELECT o.id + nn.k FROM nn)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 34"},
            {"SELECT id, o.k > ALL (SELECT o.id + nn.k FROM nn) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id FROM o GROUP BY id, k HAVING o.k > ALL (SELECT o.id + nn.k FROM nn) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 45"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k + nn.k FROM nn) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k + nn.k FROM nn) OR id = 3 ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE NOT (o.k IN (SELECT o.k + nn.k FROM nn)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 36"},
            {"SELECT id, o.k IN (SELECT o.k + nn.k FROM nn) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 19"},
            {"SELECT id FROM o GROUP BY id, k HAVING o.k IN (SELECT o.k + nn.k FROM nn) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 47"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k + nn.k FROM nn) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k + nn.k FROM nn) OR id = 3 ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 35"},
            {"SELECT id FROM o WHERE NOT (o.k NOT IN (SELECT o.k + nn.k FROM nn)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 40"},
            {"SELECT id, o.k NOT IN (SELECT o.k + nn.k FROM nn) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id FROM o GROUP BY id, k HAVING o.k NOT IN (SELECT o.k + nn.k FROM nn) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k + nn.k FROM nn) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k + nn.k FROM nn) OR id = 3 ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 30"},
            {"SELECT id FROM o WHERE NOT (o.k <> ALL (SELECT o.k + nn.k FROM nn)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 35"},
            {"SELECT id, o.k <> ALL (SELECT o.k + nn.k FROM nn) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 18"},
            {"SELECT id FROM o GROUP BY id, k HAVING o.k <> ALL (SELECT o.k + nn.k FROM nn) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k + nn.k FROM nn) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k + nn.k FROM nn) OR id = 3 ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE NOT (o.k = ALL (SELECT o.k + nn.k FROM nn)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 34"},
            {"SELECT id, o.k = ALL (SELECT o.k + nn.k FROM nn) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id FROM o GROUP BY id, k HAVING o.k = ALL (SELECT o.k + nn.k FROM nn) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 45"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k + nn.k FROM nn) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k + nn.k FROM nn) OR id = 3 ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE NOT (o.k > ALL (SELECT o.k + nn.k FROM nn)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 34"},
            {"SELECT id, o.k > ALL (SELECT o.k + nn.k FROM nn) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id FROM o GROUP BY id, k HAVING o.k > ALL (SELECT o.k + nn.k FROM nn) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 45"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.id + nz.k FROM nz) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.id + nz.k FROM nz) OR id = 3 ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE NOT (o.k IN (SELECT o.id + nz.k FROM nz)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 36"},
            {"SELECT id, o.k IN (SELECT o.id + nz.k FROM nz) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 19"},
            {"SELECT id FROM o GROUP BY id, k HAVING o.k IN (SELECT o.id + nz.k FROM nz) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 47"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.id + nz.k FROM nz) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.id + nz.k FROM nz) OR id = 3 ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 35"},
            {"SELECT id FROM o WHERE NOT (o.k NOT IN (SELECT o.id + nz.k FROM nz)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 40"},
            {"SELECT id, o.k NOT IN (SELECT o.id + nz.k FROM nz) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id FROM o GROUP BY id, k HAVING o.k NOT IN (SELECT o.id + nz.k FROM nz) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.id + nz.k FROM nz) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.id + nz.k FROM nz) OR id = 3 ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 30"},
            {"SELECT id FROM o WHERE NOT (o.k <> ALL (SELECT o.id + nz.k FROM nz)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 35"},
            {"SELECT id, o.k <> ALL (SELECT o.id + nz.k FROM nz) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 18"},
            {"SELECT id FROM o GROUP BY id, k HAVING o.k <> ALL (SELECT o.id + nz.k FROM nz) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.id + nz.k FROM nz) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.id + nz.k FROM nz) OR id = 3 ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE NOT (o.k = ALL (SELECT o.id + nz.k FROM nz)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 34"},
            {"SELECT id, o.k = ALL (SELECT o.id + nz.k FROM nz) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id FROM o GROUP BY id, k HAVING o.k = ALL (SELECT o.id + nz.k FROM nz) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 45"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.id + nz.k FROM nz) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.id + nz.k FROM nz) OR id = 3 ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE NOT (o.k > ALL (SELECT o.id + nz.k FROM nz)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 34"},
            {"SELECT id, o.k > ALL (SELECT o.id + nz.k FROM nz) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id FROM o GROUP BY id, k HAVING o.k > ALL (SELECT o.id + nz.k FROM nz) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 45"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.id + i.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.id + i.k FROM i) OR id = 3 ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE NOT (o.k IN (SELECT o.id + i.k FROM i)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 36"},
            {"SELECT id, o.k IN (SELECT o.id + i.k FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 19"},
            {"SELECT id FROM o GROUP BY id, k HAVING o.k IN (SELECT o.id + i.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 47"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.id + i.k FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.id + i.k FROM i) OR id = 3 ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 35"},
            {"SELECT id FROM o WHERE NOT (o.k NOT IN (SELECT o.id + i.k FROM i)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 40"},
            {"SELECT id, o.k NOT IN (SELECT o.id + i.k FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id FROM o GROUP BY id, k HAVING o.k NOT IN (SELECT o.id + i.k FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.id + i.k FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.id + i.k FROM i) OR id = 3 ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 30"},
            {"SELECT id FROM o WHERE NOT (o.k <> ALL (SELECT o.id + i.k FROM i)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 35"},
            {"SELECT id, o.k <> ALL (SELECT o.id + i.k FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 18"},
            {"SELECT id FROM o GROUP BY id, k HAVING o.k <> ALL (SELECT o.id + i.k FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.id + i.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.id + i.k FROM i) OR id = 3 ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE NOT (o.k = ALL (SELECT o.id + i.k FROM i)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 34"},
            {"SELECT id, o.k = ALL (SELECT o.id + i.k FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id FROM o GROUP BY id, k HAVING o.k = ALL (SELECT o.id + i.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 45"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.id + i.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.id + i.k FROM i) OR id = 3 ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE NOT (o.k > ALL (SELECT o.id + i.k FROM i)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 34"},
            {"SELECT id, o.k > ALL (SELECT o.id + i.k FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id FROM o GROUP BY id, k HAVING o.k > ALL (SELECT o.id + i.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 45"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k + i.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k + i.k FROM i) OR id = 3 ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE NOT (o.k IN (SELECT o.k + i.k FROM i)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 36"},
            {"SELECT id, o.k IN (SELECT o.k + i.k FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 19"},
            {"SELECT id FROM o GROUP BY id, k HAVING o.k IN (SELECT o.k + i.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 47"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k + i.k FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k + i.k FROM i) OR id = 3 ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 35"},
            {"SELECT id FROM o WHERE NOT (o.k NOT IN (SELECT o.k + i.k FROM i)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 40"},
            {"SELECT id, o.k NOT IN (SELECT o.k + i.k FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id FROM o GROUP BY id, k HAVING o.k NOT IN (SELECT o.k + i.k FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k + i.k FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k + i.k FROM i) OR id = 3 ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 30"},
            {"SELECT id FROM o WHERE NOT (o.k <> ALL (SELECT o.k + i.k FROM i)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 35"},
            {"SELECT id, o.k <> ALL (SELECT o.k + i.k FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 18"},
            {"SELECT id FROM o GROUP BY id, k HAVING o.k <> ALL (SELECT o.k + i.k FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k + i.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k + i.k FROM i) OR id = 3 ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE NOT (o.k = ALL (SELECT o.k + i.k FROM i)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 34"},
            {"SELECT id, o.k = ALL (SELECT o.k + i.k FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id FROM o GROUP BY id, k HAVING o.k = ALL (SELECT o.k + i.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 45"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k + i.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k + i.k FROM i) OR id = 3 ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE NOT (o.k > ALL (SELECT o.k + i.k FROM i)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 34"},
            {"SELECT id, o.k > ALL (SELECT o.k + i.k FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id FROM o GROUP BY id, k HAVING o.k > ALL (SELECT o.k + i.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 45"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k FROM i) OR id = 3 ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE NOT (o.k IN (SELECT o.k FROM i)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 36"},
            {"SELECT id, o.k IN (SELECT o.k FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 19"},
            {"SELECT id FROM o GROUP BY id, k HAVING o.k IN (SELECT o.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 47"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k FROM i) OR id = 3 ORDER BY id",
                "3"},
            {"SELECT id FROM o WHERE NOT (o.k NOT IN (SELECT o.k FROM i)) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o GROUP BY id, k HAVING o.k NOT IN (SELECT o.k FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k FROM i) OR id = 3 ORDER BY id",
                "3"},
            {"SELECT id FROM o WHERE NOT (o.k <> ALL (SELECT o.k FROM i)) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o GROUP BY id, k HAVING o.k <> ALL (SELECT o.k FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k FROM i) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k FROM i) OR id = 3 ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE NOT (o.k = ALL (SELECT o.k FROM i)) ORDER BY id",
                ""},
            {"SELECT id FROM o GROUP BY id, k HAVING o.k = ALL (SELECT o.k FROM i) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k FROM i) OR id = 3 ORDER BY id",
                "3"},
            {"SELECT id FROM o WHERE NOT (o.k > ALL (SELECT o.k FROM i)) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o GROUP BY id, k HAVING o.k > ALL (SELECT o.k FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k + tw.val FROM tw) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k + tw.val FROM tw) OR id = 3 ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id FROM o WHERE NOT (o.k IN (SELECT o.k + tw.val FROM tw)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 36"},
            {"SELECT id, o.k IN (SELECT o.k + tw.val FROM tw) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 19"},
            {"SELECT id FROM o GROUP BY id, k HAVING o.k IN (SELECT o.k + tw.val FROM tw) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 47"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k + tw.val FROM tw) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k + tw.val FROM tw) OR id = 3 ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 35"},
            {"SELECT id FROM o WHERE NOT (o.k NOT IN (SELECT o.k + tw.val FROM tw)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 40"},
            {"SELECT id, o.k NOT IN (SELECT o.k + tw.val FROM tw) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id FROM o GROUP BY id, k HAVING o.k NOT IN (SELECT o.k + tw.val FROM tw) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k + tw.val FROM tw) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k + tw.val FROM tw) OR id = 3 ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 30"},
            {"SELECT id FROM o WHERE NOT (o.k <> ALL (SELECT o.k + tw.val FROM tw)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 35"},
            {"SELECT id, o.k <> ALL (SELECT o.k + tw.val FROM tw) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 18"},
            {"SELECT id FROM o GROUP BY id, k HAVING o.k <> ALL (SELECT o.k + tw.val FROM tw) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k + tw.val FROM tw) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k + tw.val FROM tw) OR id = 3 ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE NOT (o.k = ALL (SELECT o.k + tw.val FROM tw)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 34"},
            {"SELECT id, o.k = ALL (SELECT o.k + tw.val FROM tw) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id FROM o GROUP BY id, k HAVING o.k = ALL (SELECT o.k + tw.val FROM tw) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 45"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k + tw.val FROM tw) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k + tw.val FROM tw) OR id = 3 ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id FROM o WHERE NOT (o.k > ALL (SELECT o.k + tw.val FROM tw)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 34"},
            {"SELECT id, o.k > ALL (SELECT o.k + tw.val FROM tw) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id FROM o GROUP BY id, k HAVING o.k > ALL (SELECT o.k + tw.val FROM tw) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 45"},
        });
    }

    @Test
    public void limitsAndAggregates() {
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
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k FROM i LIMIT 1) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id, o.k IN (SELECT o.k FROM i LIMIT 1) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 19"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k FROM i LIMIT 1) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT o.k FROM i LIMIT 1) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id, o.k = ANY (SELECT o.k FROM i LIMIT 1) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k FROM i LIMIT 1) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k FROM i LIMIT 1) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k FROM i LIMIT 1) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k + i.k FROM i LIMIT 1) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id, o.k IN (SELECT o.k + i.k FROM i LIMIT 1) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 19"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k + i.k FROM i LIMIT 1) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id, o.k NOT IN (SELECT o.k + i.k FROM i LIMIT 1) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT o.k + i.k FROM i LIMIT 1) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id, o.k = ANY (SELECT o.k + i.k FROM i LIMIT 1) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k + i.k FROM i LIMIT 1) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id, o.k <> ALL (SELECT o.k + i.k FROM i LIMIT 1) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 18"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k + i.k FROM i LIMIT 1) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id, o.k = ALL (SELECT o.k + i.k FROM i LIMIT 1) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k + i.k FROM i LIMIT 1) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id, o.k > ALL (SELECT o.k + i.k FROM i LIMIT 1) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k + i.k FROM i LIMIT 10) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id, o.k IN (SELECT o.k + i.k FROM i LIMIT 10) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 19"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k + i.k FROM i LIMIT 10) ORDER BY id",
                ""},
            {"SELECT id, o.k NOT IN (SELECT o.k + i.k FROM i LIMIT 10) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT o.k + i.k FROM i LIMIT 10) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id, o.k = ANY (SELECT o.k + i.k FROM i LIMIT 10) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k + i.k FROM i LIMIT 10) ORDER BY id",
                ""},
            {"SELECT id, o.k <> ALL (SELECT o.k + i.k FROM i LIMIT 10) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 18"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k + i.k FROM i LIMIT 10) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id, o.k = ALL (SELECT o.k + i.k FROM i LIMIT 10) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k + i.k FROM i LIMIT 10) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id, o.k > ALL (SELECT o.k + i.k FROM i LIMIT 10) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k FROM i LIMIT 10) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id, o.k IN (SELECT o.k FROM i LIMIT 10) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 19"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k FROM i LIMIT 10) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT o.k FROM i LIMIT 10) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id, o.k = ANY (SELECT o.k FROM i LIMIT 10) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k FROM i LIMIT 10) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k FROM i LIMIT 10) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k FROM i LIMIT 10) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k + i.k FROM i ORDER BY i.k LIMIT 2) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id, o.k IN (SELECT o.k + i.k FROM i ORDER BY i.k LIMIT 2) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 19"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k + i.k FROM i ORDER BY i.k LIMIT 2) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id, o.k NOT IN (SELECT o.k + i.k FROM i ORDER BY i.k LIMIT 2) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT o.k + i.k FROM i ORDER BY i.k LIMIT 2) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id, o.k = ANY (SELECT o.k + i.k FROM i ORDER BY i.k LIMIT 2) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k + i.k FROM i ORDER BY i.k LIMIT 2) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id, o.k <> ALL (SELECT o.k + i.k FROM i ORDER BY i.k LIMIT 2) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 18"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k + i.k FROM i ORDER BY i.k LIMIT 2) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id, o.k = ALL (SELECT o.k + i.k FROM i ORDER BY i.k LIMIT 2) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k + i.k FROM i ORDER BY i.k LIMIT 2) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id, o.k > ALL (SELECT o.k + i.k FROM i ORDER BY i.k LIMIT 2) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id FROM o WHERE o.k IN (SELECT MAX(i.k) + o.k FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT MAX(i.k) + o.k FROM i) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT MAX(i.k) + o.k FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT MAX(i.k) + o.k FROM i) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT MAX(i.k) + o.k FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT MAX(i.k) + o.k FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k FROM i GROUP BY i.k) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id, o.k IN (SELECT o.k FROM i GROUP BY i.k) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 19"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k FROM i GROUP BY i.k) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT o.k FROM i GROUP BY i.k) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id, o.k = ANY (SELECT o.k FROM i GROUP BY i.k) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k FROM i GROUP BY i.k) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k FROM i GROUP BY i.k) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k FROM i GROUP BY i.k) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k IN (SELECT DISTINCT o.k + i.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id, o.k IN (SELECT DISTINCT o.k + i.k FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 19"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT DISTINCT o.k + i.k FROM i) ORDER BY id",
                ""},
            {"SELECT id, o.k NOT IN (SELECT DISTINCT o.k + i.k FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT DISTINCT o.k + i.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id, o.k = ANY (SELECT DISTINCT o.k + i.k FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT DISTINCT o.k + i.k FROM i) ORDER BY id",
                ""},
            {"SELECT id, o.k <> ALL (SELECT DISTINCT o.k + i.k FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 18"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT DISTINCT o.k + i.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id, o.k = ALL (SELECT DISTINCT o.k + i.k FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT DISTINCT o.k + i.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id, o.k > ALL (SELECT DISTINCT o.k + i.k FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id FROM o WHERE o.k IN (SELECT o.k + i.k FROM i GROUP BY i.k) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 31"},
            {"SELECT id, o.k IN (SELECT o.k + i.k FROM i GROUP BY i.k) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 19"},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT o.k + i.k FROM i GROUP BY i.k) ORDER BY id",
                ""},
            {"SELECT id, o.k NOT IN (SELECT o.k + i.k FROM i GROUP BY i.k) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT o.k + i.k FROM i GROUP BY i.k) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id, o.k = ANY (SELECT o.k + i.k FROM i GROUP BY i.k) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT o.k + i.k FROM i GROUP BY i.k) ORDER BY id",
                ""},
            {"SELECT id, o.k <> ALL (SELECT o.k + i.k FROM i GROUP BY i.k) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 18"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT o.k + i.k FROM i GROUP BY i.k) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id, o.k = ALL (SELECT o.k + i.k FROM i GROUP BY i.k) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT o.k + i.k FROM i GROUP BY i.k) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 29"},
            {"SELECT id, o.k > ALL (SELECT o.k + i.k FROM i GROUP BY i.k) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 17"},
            {"SELECT id FROM o WHERE o.k IN (SELECT COUNT(*) + o.k FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k NOT IN (SELECT COUNT(*) + o.k FROM i) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ANY (SELECT COUNT(*) + o.k FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k <> ALL (SELECT COUNT(*) + o.k FROM i) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.k = ALL (SELECT COUNT(*) + o.k FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.k > ALL (SELECT COUNT(*) + o.k FROM i) ORDER BY id",
                ""},
        });
    }

    @Test
    public void aWindowItemBesideTheOuterRowIsRefusedBeforePruning() {
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
        assertCells(new String[][] {
            {"SELECT id FROM o WHERE o.id IN (SELECT SUM(val) OVER () - 70 + o.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 32"},
            {"SELECT id FROM o WHERE o.id NOT IN (SELECT SUM(val) OVER () - 70 + o.k FROM i) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.id = ANY (SELECT SUM(val) OVER () - 70 + o.k FROM i) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 30"},
            {"SELECT id FROM o WHERE o.id IN (SELECT SUM(val) OVER () + 1000 FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.id IN (SELECT ROW_NUMBER() OVER (ORDER BY val) + 100 + o.k FROM i) "
                + "ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 32"},
            {"SELECT id FROM o WHERE o.id IN (SELECT SUM(val) OVER () - 70 + o.k FROM i WHERE FALSE) ORDER"
                + " BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 32"},
            {"SELECT id, o.id IN (SELECT SUM(val) OVER () - 70 + o.k FROM i) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 20"},
            {"SELECT id FROM o WHERE o.id IN (SELECT o.k + 100 FROM i) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.id IN (SELECT o.k + one.val FROM one) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.id IN (SELECT SUM(val) OVER () - 70 + o.k FROM e) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 32"},
            {"SELECT id FROM o WHERE o.id IN (SELECT SUM(val) OVER () - 70 + o.k FROM i LIMIT 0) ORDER BY "
                + "id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 32"},
            {"SELECT id FROM o WHERE o.id NOT IN (SELECT SUM(val) OVER () - 70 + o.k FROM i WHERE FALSE) "
                + "ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM o WHERE o.id IN (SELECT SUM(val) OVER () FROM i WHERE FALSE) ORDER BY id",
                ""},
            {"SELECT id FROM o WHERE o.id IN (SELECT ROW_NUMBER() OVER (ORDER BY val) + o.k FROM i WHERE "
                + "i.k > 100) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 32"},
            {"SELECT id FROM o WHERE o.id IN (SELECT ROW_NUMBER() OVER (ORDER BY val) + o.k FROM i WHERE "
                + "FALSE) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 32"},
            {"SELECT id FROM o WHERE EXISTS (SELECT SUM(val) OVER () - 70 + o.k FROM i WHERE FALSE) ORDER "
                + "BY id",
                ""},
        });
    }
}
