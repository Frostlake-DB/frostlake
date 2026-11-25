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
 * A NOT IN standing as a value over a query whose column cannot answer NULL as the tables hold their rows: a common
 * table expression of the statement is judged as its own query reads its tables, a TRY_CAST to a text every text fits
 * as the column itself, and a filter the statistics rule out spares the query although its column holds a NULL
 * (live-verified).
 */
public class AntiJoinExpressionScopeTest extends MeasuredCellsTestSupport {

    @Test
    public void commonTableExpressionsAndTryCast() {
        engine.execute("CREATE OR REPLACE TABLE o (id INT, k INT, s VARCHAR)");
        engine.execute("INSERT INTO o VALUES (1, 1, 'a'), (2, 2, 'b'), (3, NULL, NULL), (4, 2, 'b'), (5, 5, 'e')");
        engine.execute("CREATE OR REPLACE TABLE nn (k INT, s VARCHAR)");
        engine.execute("INSERT INTO nn VALUES (1, 'a'), (2, 'b')");
        engine.execute("CREATE OR REPLACE TABLE nd (k INT NOT NULL)");
        engine.execute("INSERT INTO nd VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE TABLE wn (k INT, s VARCHAR)");
        engine.execute("INSERT INTO wn VALUES (1, 'a'), (NULL, NULL)");
        engine.execute("CREATE OR REPLACE TABLE e (k INT, s VARCHAR)");
        assertCells(new String[][] {
            {"WITH c AS (SELECT k FROM wn) SELECT id, o.k NOT IN (SELECT k FROM c) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 52"},
            {"WITH c AS (SELECT k FROM wn WHERE k IS NOT NULL) SELECT id, o.k NOT IN (SELECT k FROM c) "
                + "FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 72"},
            {"WITH c AS (SELECT NULLIF(k, 1) AS k FROM nn) SELECT id, o.k NOT IN (SELECT k FROM c) FROM o "
                + "ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 68"},
            {"WITH c AS (SELECT k FROM e) SELECT id, o.k NOT IN (SELECT k FROM c) FROM o ORDER BY id",
                "1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true"},
            {"WITH c AS (SELECT k FROM nn UNION ALL SELECT k FROM wn) SELECT id, o.k NOT IN (SELECT k FROM"
                + " c) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 79"},
            {"WITH c AS (SELECT k FROM nn) SELECT id FROM o WHERE o.k NOT IN (SELECT k FROM c) ORDER BY id",
                "5"},
            {"WITH c AS (SELECT MAX(k) AS k FROM nn) SELECT id, o.k NOT IN (SELECT k FROM c) FROM o ORDER "
                + "BY id",
                "1|true ;; 2|false ;; 3|null ;; 4|false ;; 5|true"},
            {"WITH c AS (SELECT b.k FROM nn a LEFT JOIN nd b ON a.k = b.k + 5) SELECT id, o.k NOT IN "
                + "(SELECT k FROM c) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 88"},
            {"SELECT id, o.k NOT IN (SELECT k FROM wn WHERE k > 100) FROM o ORDER BY id",
                "1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true"},
            {"SELECT id, o.k NOT IN (SELECT k FROM wn WHERE k > 1) FROM o ORDER BY id",
                "1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true"},
            {"SELECT id, o.k NOT IN (SELECT k FROM wn WHERE k >= 1) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, o.k NOT IN (SELECT k FROM wn WHERE k < 1) FROM o ORDER BY id",
                "1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true"},
            {"SELECT id, o.k NOT IN (SELECT k FROM wn WHERE k = 5) FROM o ORDER BY id",
                "1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true"},
            {"SELECT id, o.k NOT IN (SELECT k FROM wn WHERE k BETWEEN 5 AND 9) FROM o ORDER BY id",
                "1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true"},
            {"SELECT id, o.k NOT IN (SELECT k FROM wn WHERE k IN (5, 9)) FROM o ORDER BY id",
                "1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true"},
            {"SELECT id, o.k NOT IN (SELECT k FROM wn WHERE k <> 1) FROM o ORDER BY id",
                "1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true"},
            {"SELECT id, o.k NOT IN (SELECT k FROM wn WHERE NOT (k <= 1)) FROM o ORDER BY id",
                "1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true"},
            {"SELECT id, o.k NOT IN (SELECT k FROM wn WHERE k > 100 OR k IS NULL) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, o.k NOT IN (SELECT k FROM wn WHERE COALESCE(k, 200) > 100) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, o.k <> ALL (SELECT k FROM wn WHERE k > 100) FROM o ORDER BY id",
                "1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true"},
            {"SELECT id, o.k NOT IN (SELECT k FROM wn WHERE k + 1 > 100) FROM o ORDER BY id",
                "1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true"},
            {"SELECT id, o.k NOT IN (SELECT k FROM wn WHERE k > 100 AND s = 'a') FROM o ORDER BY id",
                "1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true"},
            {"SELECT id, o.k NOT IN (SELECT k FROM wn WHERE s > 'zz') FROM o ORDER BY id",
                "1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true"},
            {"SELECT id, o.k NOT IN (SELECT k FROM wn WHERE k NOT BETWEEN 0 AND 10) FROM o ORDER BY id",
                "1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true"},
            {"SELECT id, o.k NOT IN (SELECT k FROM wn WHERE k NOT IN (1, 2)) FROM o ORDER BY id",
                "1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true"},
            {"SELECT id, o.k NOT IN (SELECT k FROM wn WHERE k IS NULL AND k > 100) FROM o ORDER BY id",
                "1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true"},
            {"SELECT id, o.k NOT IN (SELECT k FROM nn WHERE k > 100) FROM o ORDER BY id",
                "1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true"},
            {"SELECT id, o.s NOT IN (SELECT s FROM wn) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, o.s NOT IN (SELECT UPPER(s) FROM nn) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, o.s NOT IN (SELECT TRY_CAST(s AS VARCHAR) FROM wn) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, o.s NOT IN (SELECT TRY_CAST(s AS VARCHAR(5)) FROM nn) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, o.k NOT IN (SELECT TRY_CAST(s AS INT) FROM nn) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, o.k NOT IN (SELECT TRY_CAST(TO_VARCHAR(k) AS INT) FROM nn) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, o.s NOT IN (SELECT TRY_CAST(s AS CHAR(1)) FROM nn) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, o.s NOT IN (SELECT TRY_CAST(s AS VARCHAR(1)) FROM nn) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, o.s NOT IN (SELECT TRY_CAST(s AS VARCHAR) FROM e) FROM o ORDER BY id",
                "1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true"},
            {"SELECT id, o.s NOT IN (SELECT TRY_CAST(UPPER(s) AS VARCHAR) FROM nn) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT id, o.s NOT IN (SELECT TRY_CAST(s || 'x' AS VARCHAR) FROM nn) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
        });
    }

    @Test
    public void commonTableExpressionNeighbours() {
        engine.execute("CREATE OR REPLACE TABLE o (id INT, k INT, s VARCHAR)");
        engine.execute("INSERT INTO o VALUES (1, 1, 'a'), (2, 2, 'b'), (3, NULL, NULL), (4, 2, 'b'), (5, 5, 'e')");
        engine.execute("CREATE OR REPLACE TABLE nn (k INT, s VARCHAR)");
        engine.execute("INSERT INTO nn VALUES (1, 'a'), (2, 'b')");
        engine.execute("CREATE OR REPLACE TABLE nd (k INT NOT NULL)");
        engine.execute("INSERT INTO nd VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE TABLE wn (k INT, s VARCHAR)");
        engine.execute("INSERT INTO wn VALUES (1, 'a'), (NULL, NULL)");
        engine.execute("CREATE OR REPLACE TABLE e (k INT, s VARCHAR)");
        assertCells(new String[][] {
            {"SELECT id, o.k NOT IN (SELECT k FROM (SELECT MAX(k) AS k FROM nn)) FROM o ORDER BY id",
                "1|true ;; 2|false ;; 3|null ;; 4|false ;; 5|true"},
            {"WITH c AS (SELECT * FROM wn) SELECT id, o.k NOT IN (SELECT k FROM c) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 52"},
            {"WITH c (z) AS (SELECT k FROM wn) SELECT id, o.k NOT IN (SELECT z FROM c) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 56"},
            {"WITH c AS (SELECT MAX(k) AS k FROM wn) SELECT id, o.k NOT IN (SELECT k FROM c) FROM o ORDER "
                + "BY id",
                "1|false ;; 2|true ;; 3|null ;; 4|true ;; 5|true"},
            {"WITH c AS (SELECT MAX(k) AS k FROM e) SELECT id, o.k NOT IN (SELECT k FROM c) FROM o ORDER "
                + "BY id",
                "1|null ;; 2|null ;; 3|null ;; 4|null ;; 5|null"},
            {"WITH c AS (SELECT k FROM nn) SELECT id, o.k NOT IN (SELECT k FROM c WHERE k > 100) FROM o "
                + "ORDER BY id",
                "1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true"},
            {"WITH c AS (SELECT k FROM wn) SELECT id, o.k NOT IN (SELECT k FROM c WHERE k > 100) FROM o "
                + "ORDER BY id",
                "1|true ;; 2|true ;; 3|true ;; 4|true ;; 5|true"},
            {"WITH c AS (SELECT k FROM nn) SELECT id, o.k NOT IN (SELECT k FROM c UNION ALL SELECT k FROM "
                + "nd) FROM o ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 52"},
            {"WITH c AS (SELECT k FROM nn) SELECT id, CASE WHEN o.k NOT IN (SELECT k FROM c) THEN 1 ELSE 0"
                + " END FROM o ORDER BY id",
                "1|0 ;; 2|0 ;; 3|0 ;; 4|0 ;; 5|1"},
        });
    }
}
