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
 * NULL answers the account decides from the statistics of the tables a statement reads — whether a stored column
 * holds a NULL, holds nothing but NULLs or one value, and its least and greatest value — rather than from the
 * columns' declarations or the rows a query returns, measured on a live account:
 *
 * <ul>
 *   <li>a NULL subject under ALL, NOT IN or {@code <> ALL} is FALSE over a query whose column has never stored a NULL
 *       (or no longer does after a DELETE or an UPDATE), where a NULL the query's own WHERE or an inner join leaves
 *       out still makes it UNKNOWN; a correlation the statistics settle is folded away first, one they leave open
 *       keeps the UNKNOWN;</li>
 *   <li>a subject the statistics fold to NULL — {@code IFF(id > 100, 1, NULL)} over ids 1 to 5, a column holding only
 *       NULLs — spares a NOT IN or {@code <> ALL} over a nullable query the refusal, and every comparison answers
 *       NULL, except under a NOT in a WHERE;</li>
 *   <li>a ROW under ALL holding a NULL is FALSE only where no column of the query may hold a NULL by the statistics;
 *       and a constant of a ROW NOT IN against a place the statistics make one value is compared while the plan
 *       compiles, so a mismatch there, or a place holding only NULLs, keeps the row whatever NULLs stand
 *       elsewhere.</li>
 * </ul>
 */
public class StatisticsHeldNullTruthTest extends MeasuredCellsTestSupport {

    @Test
    public void aNullSubjectIsFalseOverAColumnThatNeverHeldANull() {
        engine.execute("CREATE OR REPLACE TABLE p (id INT, k INT)");
        engine.execute("INSERT INTO p VALUES (1,0),(2,1),(3,2),(4,3),(5,NULL)");
        engine.execute("CREATE OR REPLACE TABLE o (id INT, k INT)");
        engine.execute("INSERT INTO o VALUES (1,1),(2,2),(3,NULL),(4,2),(5,5)");
        engine.execute("CREATE OR REPLACE TABLE nn (k INT)");
        engine.execute("INSERT INTO nn VALUES (1),(2)");
        engine.execute("CREATE OR REPLACE TABLE wn (k INT)");
        engine.execute("INSERT INTO wn VALUES (1),(NULL)");
        engine.execute("CREATE OR REPLACE TABLE w3 (k INT)");
        engine.execute("INSERT INTO w3 VALUES (1),(2),(NULL)");
        engine.execute("CREATE OR REPLACE TABLE nk (k INT NOT NULL)");
        engine.execute("INSERT INTO nk VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE TABLE nd (k INT)");
        engine.execute("INSERT INTO nd VALUES (1), (2), (NULL)");
        engine.execute("DELETE FROM nd WHERE k IS NULL");
        engine.execute("CREATE OR REPLACE TABLE nu (k INT)");
        engine.execute("INSERT INTO nu VALUES (1), (NULL)");
        engine.execute("UPDATE nu SET k = 2 WHERE k IS NULL");
        engine.execute("CREATE OR REPLACE TABLE pn (id INT, n INT)");
        engine.execute("INSERT INTO pn VALUES (1, NULL), (2, NULL)");
        engine.execute("CREATE OR REPLACE TABLE gk (g INT, k INT)");
        engine.execute("INSERT INTO gk VALUES (1, 1), (1, 2), (2, 3)");
        assertCells(new String[][] {
            {"SELECT id, k = ALL (SELECT k FROM nn) FROM p ORDER BY id",
                "1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false"},
            {"SELECT id, k = ALL (SELECT k FROM nd) FROM p ORDER BY id",
                "1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false"},
            {"SELECT id, k = ALL (SELECT k FROM nu) FROM p ORDER BY id",
                "1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false"},
            {"SELECT id, k = ALL (SELECT k FROM w3 WHERE k IS NOT NULL) FROM p ORDER BY id",
                "1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|null"},
            {"SELECT id, k = ALL (SELECT k FROM nn WHERE k = 2) FROM p ORDER BY id",
                "1|false ;; 2|false ;; 3|true ;; 4|false ;; 5|false"},
            {"SELECT id, k = ALL (SELECT k FROM nn WHERE k > 0) FROM p ORDER BY id",
                "1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false"},
            {"SELECT id, k NOT IN (SELECT k FROM nn) FROM p ORDER BY id",
                "1|true ;; 2|false ;; 3|false ;; 4|true ;; 5|false"},
            {"SELECT id, k <> ALL (SELECT k FROM nn) FROM p ORDER BY id",
                "1|true ;; 2|false ;; 3|false ;; 4|true ;; 5|false"},
            {"SELECT id, k >= ALL (SELECT k FROM nn WHERE p.id > 0) FROM p ORDER BY id",
                "1|false ;; 2|false ;; 3|true ;; 4|true ;; 5|false"},
            {"SELECT id, k = ALL (SELECT k FROM nn WHERE nn.k <= p.id) FROM p ORDER BY id",
                "1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|null"},
            {"SELECT id, k = ALL (SELECT nn.k FROM nn JOIN w3 ON nn.k = w3.k) FROM p ORDER BY id",
                "1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false"},
            {"SELECT id, k = ALL (SELECT w3.k FROM w3 JOIN nn ON nn.k = w3.k) FROM p ORDER BY id",
                "1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|null"},
            {"SELECT id, k = ALL (SELECT w3.k FROM nn LEFT JOIN w3 ON nn.k = w3.k) FROM p ORDER BY id",
                "1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|null"},
            {"SELECT id, k = ALL (SELECT k FROM nn UNION ALL SELECT k FROM nd) FROM p ORDER BY id",
                "1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false"},
            {"SELECT id, k = ALL (SELECT ABS(k) FROM nn) FROM p ORDER BY id",
                "1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false"},
            {"SELECT id, k = ALL (SELECT k * 2 FROM nn) FROM p ORDER BY id",
                "1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false"},
            {"SELECT id, k = ALL (SELECT k FROM (SELECT k FROM nn)) FROM p ORDER BY id",
                "1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false"},
            {"SELECT id FROM p WHERE NOT (k = ALL (SELECT k FROM nn)) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM p WHERE NOT (k NOT IN (SELECT k FROM nn)) ORDER BY id",
                "2 ;; 3 ;; 5"},
            {"SELECT id, (k NOT IN (SELECT k FROM nn)) IS NULL FROM p ORDER BY id",
                "1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false"},
            {"SELECT id, o.k NOT IN (SELECT k FROM nn) FROM o ORDER BY id",
                "1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|true"},
            {"SELECT NULL::INT = ALL (SELECT k FROM nn)",
                "null"},
            {"SELECT id, k < ALL (SELECT k FROM nn LIMIT 5) FROM p ORDER BY id",
                "1|true ;; 2|false ;; 3|false ;; 4|false ;; 5|false"},
            {"SELECT id, k = ALL (SELECT k FROM nn WHERE k IS NULL) FROM p ORDER BY id",
                "1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true"},
            {"SELECT id, o.k < ALL (SELECT k FROM nn) FROM o ORDER BY id",
                "1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false"},
            {"SELECT id, k = ALL (SELECT DISTINCT k FROM nn) FROM p ORDER BY id",
                "1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false"},
            {"SELECT id, k = ALL (SELECT k FROM nn ORDER BY k) FROM p ORDER BY id",
                "1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false"},
            {"SELECT id, k = ALL (SELECT k FROM nk WHERE k > 0) FROM p ORDER BY id",
                "1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false"},
            {"SELECT id, k > ALL (SELECT k + 0 FROM nn) FROM p ORDER BY id",
                "1|false ;; 2|false ;; 3|false ;; 4|true ;; 5|false"},
            {"SELECT id, k = ALL (SELECT k FROM nn WHERE k < 2) FROM p ORDER BY id",
                "1|false ;; 2|true ;; 3|false ;; 4|false ;; 5|false"},
            {"SELECT id, k NOT IN (SELECT k FROM nd) FROM p ORDER BY id",
                "1|true ;; 2|false ;; 3|false ;; 4|true ;; 5|false"},
            {"SELECT id, k NOT IN (SELECT k FROM nn WHERE nn.k <= p.id) FROM p ORDER BY id",
                "1|true ;; 2|false ;; 3|false ;; 4|true ;; 5|null"},
            {"SELECT id, k = ANY (SELECT k FROM nn) FROM p ORDER BY id",
                "1|false ;; 2|true ;; 3|true ;; 4|false ;; 5|null"},
            {"SELECT id, k = ALL (SELECT nn.k FROM nn, w3 WHERE w3.k IS NULL) FROM p ORDER BY id",
                "1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false"},
            {"SELECT id, k = ALL (SELECT MIN(k) FROM nn GROUP BY k) FROM p ORDER BY id",
                "1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|null"},
            {"SELECT id, k = ALL (SELECT k FROM nn WHERE nn.k > p.id - 100) FROM p ORDER BY id",
                "1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false"},
            {"SELECT id, k NOT IN (SELECT k FROM nn WHERE p.id > 0) FROM p ORDER BY id",
                "1|true ;; 2|false ;; 3|false ;; 4|true ;; 5|false"},
            {"SELECT id, k = ALL (SELECT k FROM nn WHERE nn.k < p.id + 100) FROM p ORDER BY id",
                "1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false"},
            {"SELECT id, k = ALL (SELECT k FROM nn WHERE p.k IS NULL) FROM p ORDER BY id",
                "1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|null"},
            {"SELECT id, k NOT IN (SELECT k FROM nk) FROM p ORDER BY id",
                "1|true ;; 2|false ;; 3|false ;; 4|true ;; 5|false"},
            {"SELECT id, k = ALL (SELECT k FROM nn WHERE nn.k = 2 OR p.id > 0) FROM p ORDER BY id",
                "1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false"},
            {"SELECT id, k = ALL (SELECT g FROM gk WHERE gk.g = 1) FROM p ORDER BY id",
                "1|false ;; 2|true ;; 3|false ;; 4|false ;; 5|false"},
            {"SELECT id, k = ALL (SELECT k FROM gk WHERE gk.g = p.id - 4) FROM p ORDER BY id",
                "1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|null"},
            {"SELECT id, k = ALL (SELECT MAX(k) FROM gk GROUP BY g) FROM p ORDER BY id",
                "1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|null"},
            {"SELECT id, k = ALL (SELECT COUNT(*) FROM gk GROUP BY g) FROM p ORDER BY id",
                "1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false"},
            {"SELECT id, k NOT IN (SELECT k FROM nn WHERE nn.k <= p.id + 10) FROM p ORDER BY id",
                "1|true ;; 2|false ;; 3|false ;; 4|true ;; 5|false"},
            {"SELECT id, k = ALL (SELECT nn.k FROM nn JOIN gk ON gk.k = nn.k AND gk.g = p.id - 4) FROM p "
                + "ORDER BY id",
                "1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|null"},
            {"SELECT id, n <> ALL (SELECT k FROM wn) FROM pn ORDER BY id",
                "1|null ;; 2|null"},
            {"SELECT id, n + 1 <> ALL (SELECT k FROM wn) FROM pn ORDER BY id",
                "1|null ;; 2|null"},
            {"SELECT id, n = ALL (SELECT k FROM nn) FROM pn ORDER BY id",
                "1|null ;; 2|null"},
            {"SELECT id, k = ALL (SELECT k FROM nn WHERE nn.k <= 2 AND p.id > 0) FROM p ORDER BY id",
                "1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false"},
            {"SELECT id, k = ALL (SELECT k FROM nn LIMIT 1) FROM p ORDER BY id",
                "1|false ;; 2|true ;; 3|false ;; 4|false ;; 5|false"},
            {"SELECT id, k = ALL (SELECT k FROM nd WHERE k = 1) FROM p ORDER BY id",
                "1|false ;; 2|true ;; 3|false ;; 4|false ;; 5|false"},
        });
    }

    @Test
    public void aSubjectTheStatisticsFoldToNullIsNotRefused() {
        engine.execute("CREATE OR REPLACE TABLE p (id INT, k INT)");
        engine.execute("INSERT INTO p VALUES (1,0),(2,1),(3,2),(4,3),(5,NULL)");
        engine.execute("CREATE OR REPLACE TABLE nn (k INT)");
        engine.execute("INSERT INTO nn VALUES (1),(2)");
        engine.execute("CREATE OR REPLACE TABLE wn (k INT)");
        engine.execute("INSERT INTO wn VALUES (1),(NULL)");
        engine.execute("CREATE OR REPLACE TABLE w3 (k INT)");
        engine.execute("INSERT INTO w3 VALUES (1),(2),(NULL)");
        engine.execute("CREATE OR REPLACE TABLE ps (id INT, s VARCHAR, d DATE)");
        engine.execute("INSERT INTO ps VALUES (1, 'a', '2020-01-01'), (2, 'b', '2020-01-05')");
        assertCells(new String[][] {
            {"SELECT id, IFF(id > 100, 1, NULL) <> ALL (SELECT k FROM wn) FROM p ORDER BY id",
                "1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null"},
            {"SELECT id, IFF(id > 100, 1, NULL) NOT IN (SELECT k FROM wn) FROM p ORDER BY id",
                "1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null"},
            {"SELECT id, CASE WHEN id > 100 THEN 1 END <> ALL (SELECT k FROM wn) FROM p ORDER BY id",
                "1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null"},
            {"SELECT id, IFF(id > 3, 1, NULL) <> ALL (SELECT k FROM wn) FROM p ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 35"},
            {"SELECT id, IFF(id < 100, NULL, 1) <> ALL (SELECT k FROM wn) FROM p ORDER BY id",
                "1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null"},
            {"SELECT id, IFF(id > 100, 1, NULL) <> ALL (SELECT k FROM w3) FROM p ORDER BY id",
                "1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null"},
            {"SELECT id, IFF(k > 100, 1, NULL) <> ALL (SELECT k FROM wn) FROM p ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 36"},
            {"SELECT id, IFF(id > 0, 1, NULL) <> ALL (SELECT k FROM wn) FROM p ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 35"},
            {"SELECT id, IFF(id > 100, 1, NULL) = ALL (SELECT k FROM wn) FROM p ORDER BY id",
                "1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null"},
            {"SELECT id, IFF(id > 100, k, NULL) NOT IN (SELECT k FROM wn) FROM p ORDER BY id",
                "1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null"},
            {"SELECT id, CASE WHEN id BETWEEN 1 AND 5 THEN NULL ELSE 1 END <> ALL (SELECT k FROM wn) FROM "
                + "p ORDER BY id",
                "1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null"},
            {"SELECT id, IFF(id >= 5, 1, NULL) <> ALL (SELECT k FROM wn) FROM p ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 36"},
            {"SELECT id, IFF(id > 5, 1, NULL) <> ALL (SELECT k FROM wn) FROM p ORDER BY id",
                "1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null"},
            {"SELECT id, CASE WHEN id > 100 THEN 1 WHEN id > 200 THEN 2 END <> ALL (SELECT k FROM wn) FROM"
                + " p ORDER BY id",
                "1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null"},
            {"SELECT id FROM p WHERE NOT (IFF(id > 100, 1, NULL) NOT IN (SELECT k FROM wn)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 59"},
            {"SELECT id, IFF(id > 100, 1, NULL) <> ALL (SELECT k FROM nn) FROM p ORDER BY id",
                "1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null"},
            {"SELECT id, IFF(id > 100, 1, NULL) = ALL (SELECT k FROM nn) FROM p ORDER BY id",
                "1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null"},
            {"SELECT id, IFF(id IS NULL, 1, NULL) <> ALL (SELECT k FROM wn) FROM p ORDER BY id",
                "1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null"},
            {"SELECT id, IFF(id > 100, 1, NULL) + 1 <> ALL (SELECT k FROM wn) FROM p ORDER BY id",
                "1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null"},
            {"SELECT id, IFF(id > 100 AND k > 0, 1, NULL) <> ALL (SELECT k FROM wn) FROM p ORDER BY id",
                "1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null"},
            {"SELECT id, IFF(id > 100, 1, NULL) <> ALL (SELECT k FROM wn), IFF(id > 100, 1, NULL) != ALL "
                + "(SELECT k FROM wn), IFF(id > 100, 1, NULL) < ALL (SELECT k FROM wn), IFF(id > 100, 1, NULL) "
                + ">= ALL (SELECT k FROM w3) FROM p ORDER BY id",
                "1|null|null|null|null ;; 2|null|null|null|null ;; 3|null|null|null|null ;; "
                + "4|null|null|null|null ;; 5|null|null|null|null"},
            {"SELECT IFF(id > 100, 1, NULL) <> ALL (SELECT k FROM wn) FROM p WHERE id = 1",
                "null"},
            {"SELECT id, IFF(id > 100, NULL, 1) <> ALL (SELECT k FROM wn) FROM p ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 37"},
            {"SELECT id, IFF(id > 100, 1, NULL) = ALL (SELECT k FROM wn), IFF(id > 100, 1, NULL) <> ALL "
                + "(SELECT k FROM wn), IFF(id > 100, 1, NULL) != ALL (SELECT k FROM wn), IFF(id > 100, 1, NULL)"
                + " < ALL (SELECT k FROM wn), IFF(id > 100, 1, NULL) <= ALL (SELECT k FROM wn), IFF(id > 100, "
                + "1, NULL) > ALL (SELECT k FROM wn), IFF(id > 100, 1, NULL) >= ALL (SELECT k FROM wn) FROM p "
                + "ORDER BY id",
                "1|null|null|null|null|null|null|null ;; 2|null|null|null|null|null|null|null ;; "
                + "3|null|null|null|null|null|null|null ;; 4|null|null|null|null|null|null|null ;; "
                + "5|null|null|null|null|null|null|null"},
            {"SELECT id, IFF(id > 100, 1, NULL) = ALL (SELECT k FROM w3), IFF(id > 100, 1, NULL) <> ALL "
                + "(SELECT k FROM w3), IFF(id > 100, 1, NULL) != ALL (SELECT k FROM w3), IFF(id > 100, 1, NULL)"
                + " < ALL (SELECT k FROM w3), IFF(id > 100, 1, NULL) <= ALL (SELECT k FROM w3), IFF(id > 100, "
                + "1, NULL) > ALL (SELECT k FROM w3), IFF(id > 100, 1, NULL) >= ALL (SELECT k FROM w3) FROM p "
                + "ORDER BY id",
                "1|null|null|null|null|null|null|null ;; 2|null|null|null|null|null|null|null ;; "
                + "3|null|null|null|null|null|null|null ;; 4|null|null|null|null|null|null|null ;; "
                + "5|null|null|null|null|null|null|null"},
            {"SELECT id FROM p WHERE NOT (NULL NOT IN (SELECT k FROM wn)) ORDER BY id",
                ""},
            {"SELECT id FROM p ORDER BY IFF(id > 100, 1, NULL) NOT IN (SELECT k FROM wn), id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id, CASE WHEN IFF(id > 100, 1, NULL) NOT IN (SELECT k FROM wn) THEN 1 END FROM p "
                + "ORDER BY id",
                "1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null"},
            {"SELECT id FROM p WHERE (IFF(id > 100, 1, NULL) NOT IN (SELECT k FROM wn)) IS NULL ORDER BY "
                + "id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id FROM p WHERE NOT (IFF(id > 100, 1, NULL) <> ALL (SELECT k FROM wn)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 54"},
            {"SELECT id, NOT (IFF(id > 100, 1, NULL) NOT IN (SELECT k FROM wn)) FROM p ORDER BY id",
                "1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null"},
            {"SELECT id FROM p WHERE NOT (CAST(NULL AS INT) NOT IN (SELECT k FROM wn)) ORDER BY id",
                ""},
            {"SELECT id, (IFF(id > 100, 1, NULL) NOT IN (SELECT k FROM wn)) IS NULL FROM p ORDER BY id",
                "1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true"},
            {"SELECT id FROM p WHERE id > 0 OR IFF(id > 100, 1, NULL) NOT IN (SELECT k FROM wn) ORDER BY "
                + "id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5"},
            {"SELECT id, COALESCE(IFF(id > 100, 1, NULL) NOT IN (SELECT k FROM wn), TRUE) FROM p ORDER BY "
                + "id",
                "1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true"},
            {"SELECT id FROM ps WHERE NOT (IFF(id > 100, 1, NULL) NOT IN (SELECT k FROM wn)) ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 60"},
        });
    }

    @Test
    public void aRowIsJudgedByThePlacesTheStatisticsProve() {
        engine.execute("CREATE OR REPLACE TABLE p (id INT, a INT, b VARCHAR, c INT)");
        engine.execute("INSERT INTO p VALUES (1, 1, 'x', 10), (2, 2, 'y', 20), (3, NULL, 'x', 10), (4, 1, NULL, 10),"
            + " (5, NULL, NULL, NULL), (6, 3, 'z', 30), (7, 1, 'x', NULL), (8, NULL, 'q', 20), (9, 9, NULL,"
            + " 99)");
        engine.execute("CREATE OR REPLACE TABLE q (g INT, k INT, s VARCHAR, v INT)");
        engine.execute("INSERT INTO q VALUES (0, 1, 'x', 10), (0, 2, 'y', 20), (1, 1, 'x', 10), (1, NULL, 'y', 20), "
            + "(2, 1, 'x', 10), (2, 2, NULL, 20), (3, NULL, NULL, NULL), (5, 1, NULL, 10), (6, NULL, 'x', "
            + "10)");
        engine.execute("CREATE OR REPLACE TABLE rq2 (k INT, s VARCHAR, v INT)");
        engine.execute("INSERT INTO rq2 VALUES (1, 'x', 10), (2, 'y', 20)");
        engine.execute("CREATE OR REPLACE TABLE rn (k INT, s VARCHAR, v INT)");
        engine.execute("INSERT INTO rn VALUES (1, NULL, 10)");
        engine.execute("CREATE OR REPLACE TABLE rn2 (k INT, s VARCHAR, v INT)");
        engine.execute("INSERT INTO rn2 VALUES (1, NULL, 10), (2, 'y', 20)");
        engine.execute("CREATE OR REPLACE TABLE rn3 (k INT, s VARCHAR, v INT)");
        engine.execute("INSERT INTO rn3 VALUES (1, NULL, 10), (3, NULL, 20)");
        engine.execute("CREATE OR REPLACE TABLE qk (k INT, s VARCHAR)");
        engine.execute("INSERT INTO qk VALUES (1, 'x'), (2, 'y'), (NULL, 'z')");
        engine.execute("CREATE OR REPLACE TABLE qs (k INT, s VARCHAR)");
        engine.execute("INSERT INTO qs VALUES (1, 'x'), (2, 'y'), (3, NULL)");
        engine.execute("CREATE OR REPLACE TABLE qd (k INT, s VARCHAR)");
        engine.execute("INSERT INTO qd VALUES (1, 'x'), (2, 'y'), (NULL, NULL)");
        engine.execute("DELETE FROM qd WHERE k IS NULL");
        assertCells(new String[][] {
            {"SELECT id, (a, b) < ALL (SELECT k, s FROM q WHERE g = 0) FROM p ORDER BY id",
                "1|false ;; 2|false ;; 3|null ;; 4|null ;; 5|null ;; 6|false ;; 7|false ;; 8|null ;; 9|false"},
            {"SELECT id, (a, b) = ALL (SELECT k, s FROM q WHERE g = 0) FROM p ORDER BY id",
                "1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|null ;; 6|false ;; 7|false ;; 8|false ;; "
                + "9|false"},
            {"SELECT 1 WHERE (5, 2) NOT IN (SELECT k, s FROM rn)",
                "1"},
            {"SELECT id, (a, b) < ALL (SELECT k, s FROM qk WHERE k IS NOT NULL) FROM p ORDER BY id",
                "1|false ;; 2|false ;; 3|null ;; 4|null ;; 5|null ;; 6|false ;; 7|false ;; 8|null ;; 9|false"},
            {"SELECT id, (a, b) < ALL (SELECT k, s FROM qs WHERE s IS NOT NULL) FROM p ORDER BY id",
                "1|false ;; 2|false ;; 3|null ;; 4|null ;; 5|null ;; 6|false ;; 7|false ;; 8|null ;; 9|false"},
            {"SELECT id, (a, b) = ALL (SELECT k, s FROM qk WHERE k IS NOT NULL) FROM p ORDER BY id",
                "1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|null ;; 6|false ;; 7|false ;; 8|false ;; "
                + "9|false"},
            {"SELECT id, (a, b) = ALL (SELECT k, s FROM qs WHERE s IS NOT NULL) FROM p ORDER BY id",
                "1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|null ;; 6|false ;; 7|false ;; 8|false ;; "
                + "9|false"},
            {"SELECT id, (a, b) > ALL (SELECT k, s FROM q WHERE g = 0) FROM p ORDER BY id",
                "1|false ;; 2|false ;; 3|null ;; 4|false ;; 5|null ;; 6|true ;; 7|false ;; 8|null ;; 9|true"},
            {"SELECT id FROM p WHERE NOT ((a, b) < ALL (SELECT k, s FROM q WHERE g = 0)) ORDER BY id",
                "1 ;; 2 ;; 6 ;; 7 ;; 9"},
            {"SELECT id, (a, b) < ALL (SELECT k, s FROM rq2) FROM p ORDER BY id",
                "1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false ;; 6|false ;; 7|false ;; 8|false ;; "
                + "9|false"},
            {"SELECT id, (a, b) < ALL (SELECT k, s FROM qd) FROM p ORDER BY id",
                "1|false ;; 2|false ;; 3|false ;; 4|false ;; 5|false ;; 6|false ;; 7|false ;; 8|false ;; "
                + "9|false"},
            {"SELECT 1 WHERE (5, 'q') NOT IN (SELECT k, s FROM rn2)",
                ""},
            {"SELECT 1 WHERE (5, 2) NOT IN (SELECT k, NULL FROM rn)",
                "1"},
            {"SELECT 1 WHERE (1, 2) NOT IN (SELECT k, s FROM rn)",
                "1"},
            {"SELECT 1 WHERE (5, 2) NOT IN (SELECT k, s FROM rn3)",
                "1"},
            {"SELECT id FROM p WHERE (a, b) NOT IN (SELECT k, s FROM q WHERE g = 2) ORDER BY id",
                ""},
            {"SELECT id FROM p WHERE (a, b) NOT IN (SELECT k, s FROM rn) ORDER BY id",
                ""},
            {"SELECT 1 WHERE (5, 'q') NOT IN (SELECT k, s FROM q WHERE g = 2)",
                ""},
            {"SELECT 1 WHERE (5, 2) NOT IN (SELECT 1, NULL FROM rn)",
                "1"},
            {"SELECT 1 WHERE (5, 'q') NOT IN (SELECT k, s FROM rn)",
                "1"},
            {"SELECT 1 WHERE (5, 2) NOT IN (SELECT k, v FROM rn3)",
                "1"},
            {"SELECT id FROM p WHERE (a, c) NOT IN (SELECT k, v FROM rn) ORDER BY id",
                "2 ;; 6 ;; 9"},
            {"SELECT id FROM p WHERE (a, b) NOT IN (SELECT k, s FROM rn2) ORDER BY id",
                ""},
            {"SELECT id, (a, b) = ALL (SELECT k, s FROM qk WHERE k IS NOT NULL AND k < 2) FROM p ORDER BY "
                + "id",
                "1|true ;; 2|false ;; 3|null ;; 4|null ;; 5|null ;; 6|false ;; 7|true ;; 8|false ;; 9|false"},
            {"SELECT id, (a, b) < ALL (SELECT k, s FROM q WHERE g = 0 AND q.v = 10 + 0 * p.id) FROM p "
                + "ORDER BY id",
                "1|false ;; 2|false ;; 3|null ;; 4|null ;; 5|null ;; 6|false ;; 7|false ;; 8|null ;; 9|false"},
            {"SELECT id, (a, b) <= ALL (SELECT k, s FROM qs WHERE s IS NOT NULL) FROM p ORDER BY id",
                "1|true ;; 2|false ;; 3|null ;; 4|null ;; 5|null ;; 6|false ;; 7|true ;; 8|null ;; 9|false"},
            {"SELECT 1 WHERE (5, 2) NOT IN (SELECT k, s FROM rn WHERE k > 0)",
                "1"},
            {"SELECT id FROM p WHERE (1, 2) NOT IN (SELECT k, s FROM rn) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5 ;; 6 ;; 7 ;; 8 ;; 9"},
            {"SELECT id FROM p WHERE (1, 2) NOT IN (SELECT 1, NULL FROM rn) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5 ;; 6 ;; 7 ;; 8 ;; 9"},
            {"SELECT 1 FROM rn WHERE (5, 2) NOT IN (SELECT k, s FROM rn)",
                "1"},
            {"SELECT (5, 2) NOT IN (SELECT k, s FROM rn)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 22"},
            {"SELECT 1 WHERE (5, 2) NOT IN (SELECT k, s FROM q WHERE g = 3)",
                ""},
            {"SELECT 1 WHERE (1, 'x') NOT IN (SELECT k, s FROM rn)",
                "1"},
            {"SELECT 1 WHERE (NULL, 2) NOT IN (SELECT k, s FROM rn)",
                ""},
            {"SELECT 1 WHERE (5, 2) NOT IN (SELECT v, s FROM rn)",
                "1"},
            {"SELECT 1 WHERE (5, 2) NOT IN (SELECT k, s FROM rn3 WHERE k = 1)",
                "1"},
            {"SELECT id FROM p WHERE id = 1 AND (5, 2) NOT IN (SELECT k, s FROM rn) ORDER BY id",
                "1"},
            {"SELECT 1 FROM (SELECT 1) WHERE (5, 2) NOT IN (SELECT k, s FROM rn)",
                "1"},
            {"SELECT 1 WHERE (5, 'q') NOT IN (SELECT k, s FROM q WHERE g = 3)",
                ""},
            {"SELECT 1 WHERE (1, 'x') NOT IN (SELECT k, s FROM q WHERE g = 5)",
                ""},
            {"SELECT 1 WHERE (5, 'q') NOT IN (SELECT k, s FROM q WHERE g = 5)",
                ""},
            {"SELECT id FROM p WHERE (5, 'q') NOT IN (SELECT k, s FROM rn) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5 ;; 6 ;; 7 ;; 8 ;; 9"},
            {"SELECT 1 WHERE (5, 'q') NOT IN (SELECT k, s FROM rn3)",
                "1"},
            {"SELECT 1 WHERE (5, 'q') NOT IN (SELECT k, s FROM qd)",
                "1"},
            {"SELECT 1 WHERE (5, 'q') <> ALL (SELECT k, s FROM rn)",
                "1"},
            {"SELECT id FROM p WHERE (id, 2) NOT IN (SELECT k, s FROM rn) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5 ;; 6 ;; 7 ;; 8 ;; 9"},
            {"SELECT id FROM p WHERE (5, b) NOT IN (SELECT k, s FROM rn) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 6 ;; 7 ;; 8"},
            {"SELECT id FROM p WHERE (id, 2) NOT IN (SELECT 1, NULL FROM rn) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5 ;; 6 ;; 7 ;; 8 ;; 9"},
            {"SELECT id FROM p WHERE (5, id) NOT IN (SELECT 1, NULL FROM rn) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5 ;; 6 ;; 7 ;; 8 ;; 9"},
            {"SELECT id FROM p WHERE (id, 'q') NOT IN (SELECT k, s FROM rn) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5 ;; 6 ;; 7 ;; 8 ;; 9"},
            {"SELECT id FROM p WHERE (a, b) NOT IN (SELECT 1, NULL FROM rn) ORDER BY id",
                ""},
            {"SELECT 1 WHERE (1, 'a') NOT IN (SELECT 2, NULL FROM p)",
                "1"},
            {"SELECT id FROM p WHERE (id, b) NOT IN (SELECT k, s FROM rn) ORDER BY id",
                ""},
            {"SELECT id FROM p WHERE (a, 'q') NOT IN (SELECT k, s FROM rn) ORDER BY id",
                "1 ;; 2 ;; 4 ;; 6 ;; 7 ;; 9"},
            {"SELECT id FROM p WHERE (id, 2) NOT IN (SELECT k, s FROM rn3) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5 ;; 6 ;; 7 ;; 8 ;; 9"},
            {"SELECT id FROM p WHERE (id, 2) NOT IN (SELECT k, s FROM q WHERE g = 3) ORDER BY id",
                ""},
            {"SELECT id FROM p WHERE (id + 0, 2) NOT IN (SELECT k, s FROM rn) ORDER BY id",
                "1 ;; 2 ;; 3 ;; 4 ;; 5 ;; 6 ;; 7 ;; 8 ;; 9"},
        });
    }
}
