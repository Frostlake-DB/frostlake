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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A subquery whose FROM a table function feeds the outer row: {@code TABLE(FLATTEN(INPUT => x.arr))},
 * {@code LATERAL FLATTEN(…)} or {@code TABLE(SPLIT_TO_TABLE(x.id, 'p'))} inside a query written for the rows of
 * {@code x}. As a value, under EXISTS, IN and ANY it is refused; under NOT IN and ALL it is computed for every
 * outer row, with the rules of its own this class pins. Every cell is live-verified.
 */
public class FedSubqueryMembershipTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE t (id VARCHAR, src VARIANT, arr ARRAY, n INT)");
        engine.execute("""
            INSERT INTO t
            SELECT 'p1', PARSE_JSON('{"defs":[{"name":"x"},{"name":"y"}]}'), ARRAY_CONSTRUCT(1,2,3), 2
            UNION ALL SELECT 'p2', PARSE_JSON('{"defs":[{"name":"z"}]}'), ARRAY_CONSTRUCT(3), 3
            UNION ALL SELECT 'p3', PARSE_JSON('{}'), ARRAY_CONSTRUCT(), 0
            UNION ALL SELECT 'p4', NULL, NULL, NULL
            UNION ALL SELECT 'p5', NULL, ARRAY_CONSTRUCT(7,8), 5
            UNION ALL SELECT 'p6', NULL, ARRAY_CONSTRUCT(7,NULL), 5
            UNION ALL SELECT 'p7', NULL, ARRAY_CONSTRUCT(7,8), NULL
            UNION ALL SELECT 'p8', NULL, PARSE_JSON('[7,null]'), 5
            """);
        engine.execute("CREATE OR REPLACE TABLE u (k INT, v VARCHAR)");
        engine.execute("INSERT INTO u VALUES (1,'x'),(2,'y'),(3,'z')");
        engine.execute("CREATE OR REPLACE TABLE one (id VARCHAR, arr ARRAY, n INT)");
        engine.execute("INSERT INTO one SELECT 'q1', ARRAY_CONSTRUCT(1,2,3), 2");
        engine.execute("CREATE OR REPLACE TABLE none (id VARCHAR, arr ARRAY, n INT)");
        engine.execute("CREATE OR REPLACE TABLE t2 (id VARCHAR, arr ARRAY, n INT)");
        engine.execute("INSERT INTO t2 SELECT 'p1', ARRAY_CONSTRUCT(1,2,3), 2 UNION ALL SELECT 'p2', ARRAY_CONSTRUCT(3), 3");
        engine.execute("CREATE OR REPLACE TABLE e1 (id INT, a INT)");
        engine.execute("INSERT INTO e1 VALUES (1, 10)");
    }

    /** Every row's cells joined as the probe prints them, "(0 rows)" for none, or the refusal on one line. */
    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder rows = new StringBuilder();
            while (rs.next()) {
                rows.append(rows.length() > 0 ? " | " : "");
                for (int c = 0; c < rs.getColumns().size(); c++) {
                    rows.append(c > 0 ? ", " : "").append(String.valueOf(rs.getValue(c)));
                }
            }
            return rows.length() > 0 ? rows.toString() : "(0 rows)";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** A value, an EXISTS, an IN and an ANY over a query a table function feeds the outer row are refused. */
    @Test
    public void aValueAnExistsAndAnInOverAFedQueryAreRefused() {
        final String[][] cells = {
            {"SELECT x.id FROM t x WHERE EXISTS (SELECT 1 FROM TABLE(FLATTEN(INPUT => x.src['defs'])) f WHERE f.value['name']::VARCHAR = 'y') ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 27"},
            {"SELECT x.id, EXISTS (SELECT 1 FROM TABLE(FLATTEN(INPUT => x.src['defs'])) f WHERE f.value['name']::VARCHAR = 'y') AS has_y FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 13"},
            {"SELECT x.id FROM t x WHERE NOT EXISTS (SELECT 1 FROM TABLE(FLATTEN(INPUT => x.src['defs'])) f WHERE f.value['name']::VARCHAR = 'y') ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 31"},
            {"SELECT x.id FROM t x WHERE EXISTS (SELECT 1 FROM TABLE(FLATTEN(INPUT => x.src['defs'])) f) ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 27"},
            {"SELECT x.id FROM t x WHERE EXISTS (SELECT 1 FROM TABLE(FLATTEN(x.arr)) f WHERE f.value = 3) ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 27"},
            {"SELECT x.id FROM t x WHERE EXISTS (SELECT 1 FROM LATERAL FLATTEN(INPUT => x.arr) f WHERE f.value = 3) ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 27"},
            {"SELECT x.id FROM t x WHERE EXISTS (SELECT 1 FROM TABLE(FLATTEN(INPUT => x.arr)) f WHERE f.value = x.n) ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 27"},
            {"SELECT x.id FROM t x WHERE x.n IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f) ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 35"},
            {"SELECT x.id, (SELECT COUNT(*) FROM TABLE(FLATTEN(INPUT => x.arr)) f) AS c FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 14"},
            {"SELECT x.id, (SELECT MAX(f.value) FROM TABLE(FLATTEN(INPUT => x.arr)) f) AS c FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 14"},
            {"SELECT x.id, (SELECT SUM(f.value::INT) FROM TABLE(FLATTEN(INPUT => x.arr)) f WHERE f.value > 1) AS c FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 14"},
            {"SELECT x.id FROM t x WHERE (SELECT COUNT(*) FROM TABLE(FLATTEN(INPUT => x.arr)) f) > 1 ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 28"},
            {"SELECT x.id, (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f) AS c FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 14"},
            {"SELECT x.id, (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f LIMIT 1) AS c FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 14"},
            {"SELECT x.id, (SELECT ARRAY_AGG(f.value) FROM TABLE(FLATTEN(INPUT => x.arr)) f) AS c FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 14"},
            {"SELECT x.id FROM t x WHERE EXISTS (SELECT 1 FROM TABLE(FLATTEN(INPUT => x.arr)) f JOIN u ON u.k = f.value WHERE u.v = 'x') ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 27"},
            {"SELECT x.id FROM t x WHERE EXISTS (SELECT 1 FROM u, TABLE(FLATTEN(INPUT => x.arr)) f WHERE u.k = f.value AND u.v = 'x') ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 27"},
            {"SELECT x.id FROM t x WHERE EXISTS (SELECT 1 FROM u, LATERAL FLATTEN(INPUT => x.arr) f WHERE u.k = f.value AND u.v = 'x') ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 27"},
            {"SELECT x.id FROM t x WHERE x.n > 0 AND EXISTS (SELECT 1 FROM TABLE(FLATTEN(INPUT => x.arr)) f WHERE f.value = 3) ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 39"},
            {"SELECT x.id FROM t x WHERE x.n = 0 OR EXISTS (SELECT 1 FROM TABLE(FLATTEN(INPUT => x.arr)) f WHERE f.value = 3) ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 38"},
            {"SELECT x.id, CASE WHEN EXISTS (SELECT 1 FROM TABLE(FLATTEN(INPUT => x.arr)) f WHERE f.value = 3) THEN 'y' ELSE 'n' END FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 23"},
            {"SELECT x.id FROM t x WHERE EXISTS (SELECT 1 FROM TABLE(FLATTEN(INPUT => x.src, PATH => 'defs')) f WHERE f.value['name'] = 'z') ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 27"},
            {"SELECT x.id FROM t x WHERE EXISTS (SELECT 1 FROM TABLE(FLATTEN(INPUT => x.src, RECURSIVE => TRUE)) f WHERE f.value = 'z') ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 27"},
            {"SELECT x.id FROM t x WHERE EXISTS (SELECT 1 FROM TABLE(FLATTEN(INPUT => x.arr, OUTER => TRUE)) f) ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 27"},
            {"SELECT x.id FROM t x WHERE EXISTS (SELECT 1 FROM TABLE(SPLIT_TO_TABLE(x.id, 'p')) s WHERE s.value = '1') ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 27"},
            {"SELECT x.id, (SELECT COUNT(*) FROM TABLE(SPLIT_TO_TABLE(x.id, 'p')) s) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 14"},
            {"SELECT x.id FROM t x WHERE EXISTS (SELECT 1 FROM (SELECT f.value v FROM TABLE(FLATTEN(INPUT => x.arr)) f) q WHERE q.v = 3) ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 27"},
            {"SELECT x.id FROM t x WHERE EXISTS (SELECT 1 FROM TABLE(FLATTEN(INPUT => x.arr)) f WHERE EXISTS (SELECT 1 FROM u WHERE u.k = f.value AND u.v = 'z')) ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 27"},
            {"SELECT x.id, ARRAY_SIZE(x.arr) s FROM t x WHERE EXISTS (SELECT 1 FROM TABLE(FLATTEN(INPUT => x.arr)) f GROUP BY f.value HAVING COUNT(*) = 1) ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 48"},
            {"SELECT x.n, COUNT(*) FROM t x WHERE EXISTS (SELECT 1 FROM TABLE(FLATTEN(INPUT => x.arr)) f WHERE f.value = 3) GROUP BY x.n ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 36"},
            {"UPDATE t x SET n = 9 WHERE EXISTS (SELECT 1 FROM TABLE(FLATTEN(INPUT => x.arr)) f WHERE f.value = 2)",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 27"},
            {"DELETE FROM t x WHERE EXISTS (SELECT 1 FROM TABLE(FLATTEN(INPUT => x.arr)) f WHERE f.value = 2)",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 22"},
            {"SELECT id FROM t WHERE EXISTS (SELECT 1 FROM TABLE(FLATTEN(INPUT => arr)) f WHERE f.value = 3) ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 23"},
            {"SELECT id FROM t WHERE EXISTS (SELECT 1 FROM TABLE(FLATTEN(INPUT => t.arr)) WHERE value = 3) ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 23"},
            {"SELECT x.id FROM t x JOIN u ON u.k = x.n WHERE EXISTS (SELECT 1 FROM TABLE(FLATTEN(INPUT => x.arr)) f WHERE f.value = u.k) ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 47"},
            {"SELECT x.id FROM t x WHERE x.n = ANY (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f) ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 33"},
            {"SELECT x.id, (SELECT LISTAGG(f.value::VARCHAR, ',') WITHIN GROUP (ORDER BY f.index DESC) FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 14"},
            {"SELECT x.id FROM t x WHERE NOT (x.n IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f)) ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 40"},
            {"SELECT x.id FROM t x WHERE NOT EXISTS (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f WHERE f.value = x.n) ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 31"},
            {"SELECT x.id FROM t x WHERE EXISTS (SELECT 1 FROM (SELECT f.value v FROM TABLE(FLATTEN(INPUT => x.arr)) f) q WHERE q.v = 3) ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 27"},
            {"SELECT x.id FROM t x WHERE EXISTS (SELECT 1 FROM (SELECT f.value v FROM TABLE(FLATTEN(INPUT => x.arr)) f) q) ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 27"},
            {"SELECT x.id, (SELECT COUNT(*) FROM (SELECT f.value v FROM TABLE(FLATTEN(INPUT => x.arr)) f) q) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 14"},
            {"SELECT x.id FROM t x WHERE x.n IN (SELECT q.v FROM (SELECT f.value v FROM TABLE(FLATTEN(INPUT => x.arr)) f) q) ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 35"},
            {"SELECT x.id FROM t x WHERE EXISTS (WITH q AS (SELECT f.value v FROM TABLE(FLATTEN(INPUT => x.arr)) f) SELECT 1 FROM q WHERE q.v = 3) ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 27"},
            {"SELECT x.id FROM t x WHERE EXISTS (SELECT 1 FROM u WHERE u.k IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f)) ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated"},
            {"SELECT x.id FROM t x WHERE EXISTS (SELECT 1 FROM u WHERE u.k NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f)) ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated"},
            {"SELECT x.id FROM t x WHERE EXISTS (SELECT 1 FROM u WHERE EXISTS (SELECT 1 FROM TABLE(FLATTEN(INPUT => x.arr)) f WHERE f.value = u.k)) ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated"},
            {"SELECT x.id FROM t x WHERE EXISTS (SELECT 1 FROM u, TABLE(FLATTEN(INPUT => x.arr)) f) ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 27"},
            {"SELECT x.id FROM t x WHERE EXISTS (SELECT 1 FROM u JOIN TABLE(FLATTEN(INPUT => x.arr)) f ON u.k = f.value) ORDER BY 1",
                "Unsupported feature 'lateral table function called with OUTER JOIN syntax or a join predicate (ON clause)'."},
            {"SELECT x.id FROM t x WHERE EXISTS (SELECT 1 FROM u CROSS JOIN TABLE(FLATTEN(INPUT => x.arr)) f) ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 27"},
            {"SELECT x.id, (SELECT COUNT(*) FROM u, TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 14"},
            {"SELECT x.id FROM t x WHERE EXISTS (SELECT 1 FROM u, TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(u.k, x.n))) f WHERE f.value = 5) ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 27"},
            {"SELECT x.id, l.c FROM t x, LATERAL (SELECT COUNT(*) c FROM t y, TABLE(FLATTEN(INPUT => x.arr)) f) l ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated"},
            {"SELECT x.id, l.c FROM t x, LATERAL (SELECT COUNT(*) c FROM TABLE(FLATTEN(INPUT => x.arr)) f) l ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated"},
            {"SELECT x.id, l.v FROM t x, LATERAL (SELECT f.value v FROM t y, TABLE(FLATTEN(INPUT => x.arr)) f WHERE y.id = 'p1') l ORDER BY 1, 2",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated"},
            {"SELECT id, NOT n IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, n = ANY (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 15"},
            {"SELECT id, n <> ANY (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 16"},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    /** A NOT IN over such a query is answered row by row, three-valued. */
    @Test
    public void aNotInOverAFedQueryIsComputedForEveryRow() {
        final String[][] cells = {
            {"SELECT x.id FROM t x WHERE x.n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f) ORDER BY 1",
                "p3 | p4 | p5 | p6 | p8"},
            {"SELECT x.id FROM t x WHERE x.n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f WHERE f.value > 2) ORDER BY 1",
                "p1 | p3 | p4 | p5 | p6 | p8"},
            {"SELECT x.id FROM t x WHERE x.n NOT IN (SELECT f.value::INT FROM TABLE(FLATTEN(INPUT => x.arr)) f) ORDER BY 1",
                "p3 | p4 | p5 | p6"},
            {"SELECT x.id FROM t x WHERE x.n::VARIANT NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f) ORDER BY 1",
                "p3 | p4 | p5 | p6 | p8"},
            {"SELECT x.id, x.n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "p1, false | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT x.id FROM t x WHERE x.n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f) AND x.id <> 'p3' ORDER BY 1",
                "p4 | p5 | p6 | p8"},
            {"SELECT x.id FROM t x WHERE x.n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f) OR x.id = 'p1' ORDER BY 1",
                "p1 | p3 | p4 | p5 | p6 | p8"},
            {"SELECT x.id FROM t x WHERE x.n NOT IN (SELECT f.index FROM TABLE(FLATTEN(INPUT => x.arr)) f) ORDER BY 1",
                "p2 | p3 | p4 | p5 | p6 | p8"},
            {"SELECT x.id FROM t x WHERE x.n NOT IN (SELECT f.value FROM LATERAL FLATTEN(INPUT => x.arr) f) ORDER BY 1",
                "p3 | p4 | p5 | p6 | p8"},
            {"SELECT x.id FROM t x WHERE x.n NOT IN (SELECT f.value FROM TABLE(FLATTEN(x.arr)) f) ORDER BY 1",
                "p3 | p4 | p5 | p6 | p8"},
            {"SELECT x.id FROM t x WHERE x.n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f WHERE f.value <> x.n + 1) ORDER BY 1",
                "p3 | p4 | p5 | p6 | p7 | p8"},
            {"SELECT x.id FROM t x WHERE (x.n, x.n) NOT IN (SELECT f.value, f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f) ORDER BY 1",
                "p3 | p4 | p5 | p6 | p8"},
            {"SELECT x.id FROM t x WHERE x.n NOT IN (SELECT MAX(f.value) FROM TABLE(FLATTEN(INPUT => x.arr)) f) ORDER BY 1",
                "p1 | p5 | p6 | p8"},
            {"SELECT x.id FROM t x WHERE x.n NOT IN (SELECT DISTINCT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f) ORDER BY 1",
                "p3 | p4 | p5 | p6 | p8"},
            {"SELECT x.id FROM t x WHERE x.n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f LIMIT 1) ORDER BY 1",
                "p1 | p3 | p4 | p5 | p6 | p8"},
            {"SELECT id FROM t WHERE n NOT IN (SELECT value FROM TABLE(FLATTEN(INPUT => arr))) ORDER BY 1",
                "p3 | p4 | p5 | p6 | p8"},
            {"SELECT x.id FROM t x WHERE 3 NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f) ORDER BY 1",
                "p3 | p4 | p5 | p6 | p7 | p8"},
            {"SELECT x.id FROM t x WHERE x.n NOT IN (SELECT q.v FROM (SELECT f.value v FROM TABLE(FLATTEN(INPUT => x.arr)) f) q) ORDER BY 1",
                "p3 | p4 | p5 | p6 | p8"},
            {"SELECT id, n, n NOT IN (SELECT f.index FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "p1, 2, false | p2, 3, true | p3, 0, true | p4, null, true | p5, 5, true | p6, 5, true | p7, null, null | p8, 5, true"},
            {"SELECT id, 3 NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "p1, false | p2, false | p3, true | p4, true | p5, true | p6, true | p7, true | p8, true"},
            {"SELECT id, n NOT IN (SELECT MAX(f.value) FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "p1, true | p2, false | p3, null | p4, null | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT f.value::INT FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "p1, false | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, null"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f LIMIT 1) FROM t x ORDER BY 1",
                "p1, true | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT COUNT(*) FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "p1, true | p2, true | p3, false | p4, null | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f ORDER BY f.index DESC LIMIT 1) FROM t x ORDER BY 1",
                "p1, true | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f GROUP BY f.value) FROM t x ORDER BY 1",
                "p1, false | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f GROUP BY f.value HAVING COUNT(*) > 0) FROM t x ORDER BY 1",
                "p1, false | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f QUALIFY ROW_NUMBER() OVER (ORDER BY f.index) = 1) FROM t x ORDER BY 1",
                "p1, true | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT x.n FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "p1, false | p2, false | p3, true | p4, true | p5, false | p6, false | p7, null | p8, false"},
            {"SELECT id, n NOT IN (SELECT s.value FROM TABLE(SPLIT_TO_TABLE(x.id, 'p')) s) FROM t x ORDER BY 1",
                "Numeric value '' is not recognized"},
            {"SELECT id, id NOT IN (SELECT s.value FROM TABLE(SPLIT_TO_TABLE(x.id, 'p')) s WHERE s.index = 2) FROM t x ORDER BY 1",
                "p1, true | p2, true | p3, true | p4, true | p5, true | p6, true | p7, true | p8, true"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr, OUTER => TRUE)) f) FROM t x ORDER BY 1",
                "p1, false | p2, false | p3, null | p4, null | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT DISTINCT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "p1, false | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT TOP 1 f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "p1, true | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, NOT (n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f)) FROM t x ORDER BY 1",
                "p1, true | p2, true | p3, false | p4, false | p5, false | p6, false | p7, null | p8, false"},
            {"SELECT id, CASE WHEN n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f) THEN 1 ELSE 0 END FROM t x ORDER BY 1",
                "p1, 0 | p2, 0 | p3, 1 | p4, 1 | p5, 1 | p6, 1 | p7, 0 | p8, 1"},
            {"SELECT x.id, y.id FROM t x JOIN t y ON y.n = x.n AND x.n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f) ORDER BY 1, 2",
                "p3, p3 | p5, p5 | p5, p6 | p5, p8 | p6, p5 | p6, p6 | p6, p8 | p8, p5 | p8, p6 | p8, p8"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f WHERE f.value IN (1, 2, 7)) FROM t x ORDER BY 1",
                "p1, false | p2, true | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f WHERE f.value = x.n) FROM t x ORDER BY 1",
                "p1, false | p2, false | p3, true | p4, true | p5, true | p6, true | p7, true | p8, true"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f WHERE x.n > 2) FROM t x ORDER BY 1",
                "p1, true | p2, false | p3, true | p4, true | p5, true | p6, true | p7, true | p8, true"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f WHERE x.id = 'p1' OR f.value > 7) FROM t x ORDER BY 1",
                "p1, false | p2, true | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT MAX(f.value) FROM TABLE(FLATTEN(INPUT => x.arr)) f GROUP BY f.index HAVING MAX(f.value) > x.n) FROM t x ORDER BY 1",
                "p1, true | p2, true | p3, true | p4, true | p5, true | p6, true | p7, true | p8, true"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f LIMIT 0) FROM t x ORDER BY 1",
                "p1, true | p2, true | p3, true | p4, true | p5, true | p6, true | p7, true | p8, true"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f ORDER BY f.value DESC LIMIT 1 OFFSET 1) FROM t x ORDER BY 1",
                "p1, false | p2, true | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id FROM t x WHERE n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f) AND n > ALL (SELECT g.index FROM TABLE(FLATTEN(INPUT => x.arr)) g) ORDER BY 1",
                "p3 | p4 | p5 | p6 | p8"},
            {"SELECT id FROM t x GROUP BY id, n, arr HAVING n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f) ORDER BY 1",
                "p3 | p4 | p5 | p6 | p8"},
            {"SELECT id FROM t x ORDER BY n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f), 1",
                "p1 | p2 | p3 | p4 | p5 | p6 | p8 | p7"},
            {"SELECT id, n NOT IN (SELECT f.value FROM LATERAL FLATTEN(INPUT => x.arr) f LIMIT 1) FROM t x ORDER BY 1",
                "p1, true | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f WHERE f.value > 1 LIMIT 1) FROM t x ORDER BY 1",
                "p1, false | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f WHERE f.value > x.n LIMIT 1) FROM t x ORDER BY 1",
                "p1, true | p2, true | p3, true | p4, true | p5, true | p6, true | p7, true | p8, true"},
            {"SELECT id, (n, n) NOT IN (SELECT f.value, f.index FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "p1, true | p2, true | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f WHERE x.n IS NULL) FROM t x ORDER BY 1",
                "p1, true | p2, true | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT x.n + 1 FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "p1, true | p2, true | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f QUALIFY ROW_NUMBER() OVER (ORDER BY f.index) = x.n) FROM t x ORDER BY 1",
                "p1, false | p2, true | p3, true | p4, true | p5, true | p6, true | p7, true | p8, true"},
            {"SELECT id, n NOT IN (SELECT 5 FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "p1, true | p2, true | p3, true | p4, true | p5, false | p6, false | p7, null | p8, false"},
            {"SELECT id, n NOT IN (SELECT DISTINCT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f LIMIT 1) FROM t x ORDER BY 1",
                "p1, true | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    /** An ALL is answered as a NOT IN is, whatever its operator. */
    @Test
    public void anAllOverAFedQueryIsComputedForEveryRow() {
        final String[][] cells = {
            {"SELECT x.id FROM t x WHERE x.n <> ALL (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f) ORDER BY 1",
                "p3 | p4 | p5 | p6 | p8"},
            {"SELECT x.id FROM t x WHERE x.n != ALL (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f) ORDER BY 1",
                "p3 | p4 | p5 | p6 | p8"},
            {"SELECT x.id FROM t x WHERE x.n > ALL (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f) ORDER BY 1",
                "p3 | p4"},
            {"SELECT x.id FROM t x WHERE x.n = ALL (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f) ORDER BY 1",
                "p2 | p3 | p4"},
            {"SELECT id, n > ALL (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "p1, false | p2, false | p3, true | p4, true | p5, false | p6, false | p7, null | p8, false"},
            {"SELECT id, n = ALL (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "p1, false | p2, true | p3, true | p4, true | p5, false | p6, false | p7, null | p8, false"},
            {"SELECT id, n <> ALL (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "p1, false | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n < ALL (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "p1, false | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n >= ALL (SELECT f.value::INT FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "p1, false | p2, true | p3, true | p4, true | p5, false | p6, false | p7, null | p8, false"},
            {"SELECT id, n > ALL (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f LIMIT 1) FROM t x ORDER BY 1",
                "p1, true | p2, false | p3, true | p4, true | p5, false | p6, false | p7, null | p8, false"},
            {"SELECT id, n > ALL (SELECT ROW_NUMBER() OVER (ORDER BY index) FROM TABLE(FLATTEN(INPUT => x.arr))) FROM t x ORDER BY 1",
                "p1, false | p2, true | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    /** After the first fed item of the FROM, only a table function reading a column is answered. */
    @Test
    public void onlyAFunctionReadingAColumnMayFollowTheFedItem() {
        final String[][] cells = {
            {"SELECT x.id FROM t x WHERE x.n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f, u WHERE u.k = 1) ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 39"},
            {"SELECT x.id FROM t x WHERE x.n NOT IN (SELECT u.k FROM u, TABLE(FLATTEN(INPUT => x.arr)) f) ORDER BY 1",
                "p3 | p4 | p5 | p6 | p8"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f, TABLE(FLATTEN(INPUT => x.arr)) g) FROM t x ORDER BY 1",
                "p1, false | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT g.value FROM TABLE(FLATTEN(INPUT => x.arr)) f, TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(f.value, 5))) g) FROM t x ORDER BY 1",
                "p1, false | p2, false | p3, true | p4, true | p5, false | p6, false | p7, null | p8, false"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f, t y) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f, t y WHERE y.n = 2) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, n NOT IN (SELECT f.value FROM t y, TABLE(FLATTEN(INPUT => x.arr)) f WHERE y.n = 2) FROM t x ORDER BY 1",
                "p1, false | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT f.value FROM t y, TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "p1, false | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f JOIN t y ON y.n = f.value) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f CROSS JOIN t y) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f LEFT JOIN t y ON y.n = f.value) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, n NOT IN (SELECT f.value FROM t y JOIN TABLE(FLATTEN(INPUT => x.arr)) f ON y.n = f.value) FROM t x ORDER BY 1",
                "Unsupported feature 'lateral table function called with OUTER JOIN syntax or a join predicate (ON clause)'."},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f, TABLE(GENERATOR(ROWCOUNT => 2)) g) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f, (SELECT 1 a) d) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, n NOT IN (SELECT f.value FROM t y, TABLE(FLATTEN(INPUT => x.arr)) f, t z) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(1))) g, TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "p1, false | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT f.value FROM (SELECT 1 a) d, TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "p1, false | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT f.value FROM t y JOIN t z ON z.id = y.id, TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "p1, false | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT f.value FROM t y, TABLE(FLATTEN(INPUT => x.arr)) f WHERE y.n = f.value) FROM t x ORDER BY 1",
                "p1, false | p2, false | p3, true | p4, true | p5, true | p6, true | p7, true | p8, true"},
            {"SELECT id, n NOT IN (SELECT f.value FROM t y CROSS JOIN TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "p1, false | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT f.value FROM t y JOIN TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "p1, false | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT f.value FROM t y LEFT JOIN TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "p1, false | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT f.value FROM t y, TABLE(FLATTEN(INPUT => y.arr)) f) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, n NOT IN (SELECT f.value FROM t y, TABLE(FLATTEN(INPUT => ARRAY_CAT(y.arr, x.arr))) f) FROM t x ORDER BY 1",
                "p1, false | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f, TABLE(FLATTEN(INPUT => x.arr)) g, t z) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f INNER JOIN TABLE(FLATTEN(INPUT => x.arr)) g) FROM t x ORDER BY 1",
                "p1, false | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n > ALL (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f, t y) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 15"},
            {"SELECT id, n NOT IN (SELECT q.v FROM (SELECT f.value v FROM TABLE(FLATTEN(INPUT => x.arr)) f) q, t y) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, n NOT IN (SELECT q.v FROM t y, (SELECT f.value v FROM TABLE(FLATTEN(INPUT => x.arr)) f) q) FROM t x ORDER BY 1",
                "p1, false | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f, TABLE(SPLIT_TO_TABLE('a,b', ',')) s) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f, TABLE(SPLIT_TO_TABLE(x.id, ',')) s) FROM t x ORDER BY 1",
                "p1, false | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f, TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(1,2))) s) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, n NOT IN (SELECT f.value FROM t y, TABLE(FLATTEN(INPUT => y.arr)) g, TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "p1, false | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT f.value::INT + s.index FROM TABLE(FLATTEN(INPUT => x.arr)) f, TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(f.value, f.value))) s) FROM t x ORDER BY 1",
                "p1, false | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, null"},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    /** A set operation, a nested membership and a nested query reading another query are refused; a nested EXISTS or value reading none is answered. */
    @Test
    public void aSetOperationAndANestedMembershipAreRefused() {
        final String[][] cells = {
            {"SELECT x.id FROM t x WHERE x.n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f UNION ALL SELECT 99) ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 39"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f WHERE f.value IN (SELECT n FROM t)) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f UNION SELECT 99) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, n NOT IN (SELECT 99 UNION ALL SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT x.id FROM t x WHERE x.n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f WHERE f.value NOT IN (SELECT g.value FROM TABLE(FLATTEN(INPUT => x.arr)) g WHERE g.index = 0)) ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 39"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f WHERE f.value = (SELECT MAX(n) FROM t)) FROM t x ORDER BY 1",
                "p1, true | p2, true | p3, true | p4, true | p5, true | p6, true | p7, true | p8, true"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f WHERE EXISTS (SELECT 1 FROM t)) FROM t x ORDER BY 1",
                "p1, false | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT (SELECT 5) FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "p1, true | p2, true | p3, true | p4, true | p5, false | p6, false | p7, null | p8, false"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f WHERE f.value IN (SELECT n FROM t y WHERE y.id = x.id)) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, n > ALL (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f UNION ALL SELECT 0) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 15"},
            {"SELECT x.id FROM t x WHERE x.n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f WHERE f.value <> ALL (SELECT g.value FROM TABLE(FLATTEN(INPUT => x.arr)) g WHERE g.index = 0)) ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 39"},
            {"SELECT x.id FROM t x WHERE x.n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f WHERE f.value NOT IN (SELECT g.value FROM TABLE(FLATTEN(INPUT => f.this)) g WHERE g.index = 0)) ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 39"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f WHERE EXISTS (SELECT 1 FROM t y WHERE y.n = f.value)) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f WHERE f.value = (SELECT MAX(y.n) FROM t y WHERE y.n = f.value)) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f WHERE NOT EXISTS (SELECT 1 FROM none)) FROM t x ORDER BY 1",
                "p1, false | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT f.value IN (SELECT n FROM t) FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f WHERE f.value IN (SELECT n FROM t WHERE n > 100)) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f HAVING COUNT(*) IN (SELECT n FROM t)) FROM t x ORDER BY 1",
                "SQL compilation error:|[F.VALUE] is not a valid group by expression"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f WHERE f.value > ANY (SELECT n FROM t)) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f WHERE f.value NOT IN (SELECT n FROM t)) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f WHERE f.value > ALL (SELECT n FROM none)) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    /** A select item reading the outer row beside a column, an aggregate or a window is refused; windows and groupings of its own are answered. */
    @Test
    public void anItemMayReadTheOuterRowAloneOrInsideAnAggregate() {
        final String[][] cells = {
            {"SELECT id, n NOT IN (SELECT f.value + x.n FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, n NOT IN (SELECT MAX(f.value) FROM TABLE(FLATTEN(INPUT => x.arr)) f GROUP BY x.n) FROM t x ORDER BY 1",
                "p1, true | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f GROUP BY f.value, x.n) FROM t x ORDER BY 1",
                "p1, false | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT MAX(f.value + x.n) FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "p1, true | p2, true | p3, null | p4, null | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT MAX(f.value) + x.n FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, n NOT IN (SELECT COUNT(*) + x.n FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, n NOT IN (SELECT ROW_NUMBER() OVER (ORDER BY f.value) + x.n FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, n NOT IN (SELECT CASE WHEN x.n > 2 THEN f.value END FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, n NOT IN (SELECT COALESCE(f.value, x.n) FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, n NOT IN (SELECT ROW_NUMBER() OVER (ORDER BY index) FROM TABLE(FLATTEN(INPUT => x.arr))) FROM t x ORDER BY 1",
                "p1, false | p2, true | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT SUM(value) OVER (ORDER BY index) FROM TABLE(FLATTEN(INPUT => x.arr))) FROM t x ORDER BY 1",
                "p1, true | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT ROW_NUMBER() OVER (PARTITION BY index ORDER BY value) FROM TABLE(FLATTEN(INPUT => x.arr))) FROM t x ORDER BY 1",
                "p1, true | p2, true | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT RANK() OVER (ORDER BY value) FROM TABLE(FLATTEN(INPUT => x.arr))) FROM t x ORDER BY 1",
                "p1, false | p2, true | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT COUNT(*) OVER () FROM TABLE(FLATTEN(INPUT => x.arr))) FROM t x ORDER BY 1",
                "p1, true | p2, true | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT SUM(value) OVER (PARTITION BY index) FROM TABLE(FLATTEN(INPUT => x.arr))) FROM t x ORDER BY 1",
                "p1, false | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, null"},
            {"SELECT id, n NOT IN (SELECT LAG(value) OVER (ORDER BY index) FROM TABLE(FLATTEN(INPUT => x.arr))) FROM t x ORDER BY 1",
                "p1, false | p2, null | p3, true | p4, true | p5, null | p6, null | p7, null | p8, null"},
            {"SELECT id, n NOT IN (SELECT FIRST_VALUE(value) OVER (ORDER BY index) FROM TABLE(FLATTEN(INPUT => x.arr))) FROM t x ORDER BY 1",
                "p1, true | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT value FROM TABLE(FLATTEN(INPUT => x.arr)) QUALIFY RANK() OVER (ORDER BY value DESC) = 1) FROM t x ORDER BY 1",
                "p1, true | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT DENSE_RANK() OVER (ORDER BY value) + 1 FROM TABLE(FLATTEN(INPUT => x.arr))) FROM t x ORDER BY 1",
                "p1, false | p2, true | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT MAX(value) OVER () + 1 FROM TABLE(FLATTEN(INPUT => x.arr))) FROM t x ORDER BY 1",
                "p1, true | p2, true | p3, true | p4, true | p5, true | p6, true | p7, null | p8, null"},
            {"SELECT id, n NOT IN (SELECT SUM(value) OVER () + x.n FROM TABLE(FLATTEN(INPUT => x.arr))) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, n NOT IN (SELECT NTILE(2) OVER (ORDER BY value) FROM TABLE(FLATTEN(INPUT => x.arr))) FROM t x ORDER BY 1",
                "p1, false | p2, true | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT SUM(value) OVER (ORDER BY index ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) FROM TABLE(FLATTEN(INPUT => x.arr))) FROM t x ORDER BY 1",
                "p1, true | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT q.r FROM (SELECT ROW_NUMBER() OVER (ORDER BY index) r FROM TABLE(FLATTEN(INPUT => x.arr))) q) FROM t x ORDER BY 1",
                "p1, false | p2, true | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT index + 1 FROM TABLE(FLATTEN(INPUT => x.arr)) ORDER BY ROW_NUMBER() OVER (ORDER BY value) LIMIT 1) FROM t x ORDER BY 1",
                "p1, true | p2, true | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    /** A derived table or a CTE fed the outer row is judged by the same rules. */
    @Test
    public void aFedDerivedTableOrCteIsJudgedAsTheQueryIs() {
        final String[][] cells = {
            {"SELECT id, n <> ALL (SELECT q.v FROM (SELECT f.value v FROM TABLE(FLATTEN(INPUT => x.arr)) f) q) FROM t x ORDER BY 1",
                "p1, false | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (WITH q AS (SELECT f.value v FROM TABLE(FLATTEN(INPUT => x.arr)) f) SELECT q.v FROM q) FROM t x ORDER BY 1",
                "p1, false | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT q.v FROM (SELECT f.value v FROM TABLE(FLATTEN(INPUT => x.arr)) f WHERE f.index > 0) q WHERE q.v > 1) FROM t x ORDER BY 1",
                "p1, false | p2, true | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT r.v FROM (SELECT q.v FROM (SELECT f.value v FROM TABLE(FLATTEN(INPUT => x.arr)) f) q) r) FROM t x ORDER BY 1",
                "p1, false | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT q.v FROM (SELECT MAX(f.value) v FROM TABLE(FLATTEN(INPUT => x.arr)) f) q) FROM t x ORDER BY 1",
                "p1, true | p2, false | p3, null | p4, null | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT q.v FROM (SELECT f.value v FROM TABLE(FLATTEN(INPUT => x.arr)) f LIMIT 1) q) FROM t x ORDER BY 1",
                "p1, true | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT q.v FROM (SELECT f.value v FROM TABLE(FLATTEN(INPUT => x.arr)) f, t y) q) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, n NOT IN (SELECT q.v FROM (SELECT f.value v FROM TABLE(FLATTEN(INPUT => x.arr)) f UNION ALL SELECT 1) q) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, n NOT IN (WITH q AS (SELECT f.value v FROM TABLE(FLATTEN(INPUT => x.arr)) f LIMIT 1) SELECT q.v FROM q) FROM t x ORDER BY 1",
                "p1, true | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n > ALL (SELECT q.v FROM (SELECT f.value v FROM TABLE(FLATTEN(INPUT => x.arr)) f) q, t y) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 15"},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    /** Over one row, a number's one value is a constant and an ARRAY's is not; a NOT IN is judged by its own rules there too. */
    @Test
    public void pruningAndOneRowTablesSpareOnlyWhatTheStatisticsHold() {
        final String[][] cells = {
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x WHERE FALSE",
                "(0 rows)"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x LIMIT 0",
                "(0 rows)"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM none x",
                "(0 rows)"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM one x",
                "q1, false"},
            {"SELECT id, n IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM one x",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 17"},
            {"SELECT id, EXISTS (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM one x",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 11"},
            {"SELECT id, (SELECT COUNT(*) FROM (SELECT f.value v FROM TABLE(FLATTEN(INPUT => x.arr)) f) q) FROM one x",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 12"},
            {"SELECT id, n IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f, one y) FROM one x",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 17"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f, one y) FROM one x",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT (SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.a, 5)))) FROM e1 E",
                "2"},
            {"SELECT (SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.n, 5)))) FROM one E",
                "2"},
            {"SELECT (SELECT COUNT(*) FROM TABLE(FLATTEN(input => E.arr))) FROM one E",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 8"},
            {"SELECT (SELECT MAX(value) FROM TABLE(FLATTEN(input => E.arr))) FROM one E",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 8"},
            {"SELECT id FROM one E WHERE EXISTS (SELECT 1 FROM TABLE(FLATTEN(input => E.arr)))",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 27"},
            {"SELECT id, EXISTS (SELECT 1 FROM TABLE(FLATTEN(input => E.arr))) FROM one E",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 11"},
            {"SELECT id FROM one E WHERE n IN (SELECT value FROM TABLE(FLATTEN(input => E.arr)))",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 33"},
            {"SELECT id, n IN (SELECT value FROM TABLE(FLATTEN(input => E.arr))) FROM one E",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 17"},
            {"SELECT id FROM e1 E WHERE EXISTS (SELECT 1 FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.a))))",
                "1"},
            {"SELECT id, EXISTS (SELECT 1 FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.a)))) FROM e1 E",
                "1, true"},
            {"SELECT id FROM e1 E WHERE a IN (SELECT value FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.a))))",
                "1"},
            {"SELECT (SELECT value FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.a)))) FROM e1 E",
                "10"},
            {"SELECT (SELECT COUNT(*) FROM (SELECT value FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.a)))) q) FROM e1 E",
                "1"},
            {"SELECT (SELECT COUNT(*) FROM e1 y, TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.a)))) FROM e1 E",
                "1"},
            {"SELECT (SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.a))), e1 y) FROM e1 E",
                "1"},
            {"SELECT id, a = ANY (SELECT value FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.a)))) FROM e1 E",
                "1, true"},
            {"SELECT id, a NOT IN (SELECT value FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.a))), e1 y) FROM e1 E",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT (SELECT COUNT(*) FROM TABLE(SPLIT_TO_TABLE(E.id::VARCHAR, ','))) FROM e1 E",
                "1"},
            {"SELECT (SELECT COUNT(*) FROM LATERAL FLATTEN(input => ARRAY_CONSTRUCT(E.a))) FROM e1 E",
                "1"},
            {"SELECT (SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.a))) f WHERE f.value > 0) FROM e1 E",
                "1"},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    /** A window over the function's own columns is answered; an argument under a qualifier the outer row does not hold is refused where it is written. */
    @Test
    public void aWindowOverTheFedRowsAndAnUnknownQualifier() {
        final String[][] cells = {
            {"SELECT id, n NOT IN (SELECT SUM(f.value) OVER () FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "p1, true | p2, false | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => zz.arr)) f) FROM t x ORDER BY 1",
                "SQL compilation error: error line 1 at position 64|invalid identifier 'ZZ.ARR'"},
            {"SELECT id, n NOT IN (SELECT f.value FROM t y, TABLE(FLATTEN(INPUT => zz.arr)) f) FROM t x ORDER BY 1",
                "SQL compilation error: error line 1 at position 69|invalid identifier 'ZZ.ARR'"},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    /** Over a subject the statistics hold no NULL of, an item that is never NULL either is refused; a subject or an item that may be NULL is computed for every row. */
    @Test
    public void neitherSideEverNullIsRefused() {
        final String[][] cells = {
            {"SELECT id, n NOT IN (SELECT ROW_NUMBER() OVER (ORDER BY f.index) FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "p1, false | p2, true | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT ROW_NUMBER() OVER (ORDER BY index) FROM TABLE(FLATTEN(INPUT => x.arr))) FROM t2 x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, n NOT IN (SELECT ROW_NUMBER() OVER (ORDER BY f.index) FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t2 x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, n NOT IN (SELECT ROW_NUMBER() OVER (ORDER BY index) FROM TABLE(FLATTEN(INPUT => x.arr))) FROM t x ORDER BY 1",
                "p1, false | p2, true | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT ROW_NUMBER() OVER (ORDER BY index) FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t2 x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, n NOT IN (SELECT RANK() OVER (ORDER BY f.value) FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t2 x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, n NOT IN (SELECT SUM(f.value) OVER (ORDER BY f.index) FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t2 x ORDER BY 1",
                "p1, true | p2, false"},
            {"SELECT id, n NOT IN (SELECT ROW_NUMBER() OVER (ORDER BY f.index) + 0 FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t2 x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, n NOT IN (SELECT f.index + 1 FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t2 x ORDER BY 1",
                "p1, false | p2, true"},
            {"SELECT id, n NOT IN (SELECT COUNT(*) OVER () FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t2 x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, n NOT IN (SELECT ROW_NUMBER() OVER (ORDER BY f.index DESC) FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t2 x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, NULLIF(n, 99) NOT IN (SELECT ROW_NUMBER() OVER (ORDER BY f.index) FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t2 x ORDER BY 1",
                "p1, false | p2, true"},
            {"SELECT id, n NOT IN (SELECT COALESCE(SUM(f.value) OVER (), 0) FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t2 x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, COALESCE(n, 0) NOT IN (SELECT ROW_NUMBER() OVER (ORDER BY f.index) FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 34"},
            {"SELECT id, n > ALL (SELECT ROW_NUMBER() OVER (ORDER BY f.index) FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t2 x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 15"},
            {"SELECT id, n <> ALL (SELECT ROW_NUMBER() OVER (ORDER BY f.index) FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t2 x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 16"},
            {"SELECT id, n NOT IN (SELECT MAX(f.value) OVER () FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t2 x ORDER BY 1",
                "p1, true | p2, false"},
            {"SELECT id, n NOT IN (SELECT 5 FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t2 x ORDER BY 1",
                "p1, true | p2, true"},
            {"SELECT id, n NOT IN (SELECT COUNT(*) FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t2 x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id FROM t2 x WHERE n NOT IN (SELECT ROW_NUMBER() OVER (ORDER BY f.index) FROM TABLE(FLATTEN(INPUT => x.arr)) f) ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 36"},
            {"SELECT id FROM t x WHERE n NOT IN (SELECT ROW_NUMBER() OVER (ORDER BY f.index) FROM TABLE(FLATTEN(INPUT => x.arr)) f) ORDER BY 1",
                "p2 | p3 | p4 | p5 | p6 | p8"},
            {"SELECT id, n NOT IN (SELECT LAG(f.value) OVER (ORDER BY f.index) FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t2 x ORDER BY 1",
                "p1, false | p2, null"},
            {"SELECT id, n NOT IN (SELECT ROW_NUMBER() OVER (ORDER BY f.index) FROM TABLE(FLATTEN(INPUT => x.arr)) f WHERE f.value > 0) FROM t2 x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, n NOT IN (SELECT q.r FROM (SELECT ROW_NUMBER() OVER (ORDER BY f.index) r FROM TABLE(FLATTEN(INPUT => x.arr)) f) q) FROM t2 x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 21"},
            {"SELECT id, n NOT IN (SELECT f.value FROM TABLE(FLATTEN(INPUT => x.arr)) f QUALIFY ROW_NUMBER() OVER (ORDER BY f.index) = 1) FROM t2 x ORDER BY 1",
                "p1, true | p2, false"},
            {"SELECT id, n NOT IN (SELECT SUM(1) OVER () FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t2 x ORDER BY 1",
                "p1, true | p2, true"},
            {"SELECT id, n NOT IN (SELECT SUM(f.index) OVER () FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t2 x ORDER BY 1",
                "p1, true | p2, true"},
            {"SELECT id, n::VARIANT NOT IN (SELECT ROW_NUMBER() OVER (ORDER BY f.index) FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t2 x ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 30"},
            {"SELECT id, n NOT IN (SELECT DENSE_RANK() OVER (ORDER BY f.value) FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t x ORDER BY 1",
                "p1, false | p2, true | p3, true | p4, true | p5, true | p6, true | p7, null | p8, true"},
            {"SELECT id, n NOT IN (SELECT ROW_NUMBER() OVER (ORDER BY f.index)::VARCHAR FROM TABLE(FLATTEN(INPUT => x.arr)) f) FROM t2 x ORDER BY 1",
                "p1, false | p2, true"},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    /** A fed table function joined with an ON is refused as a lateral table function may not be, over an outer relation of no row and in a pruned WHERE too. */
    @Test
    public void aFunctionJoinedWithAnOnIsRefusedWhileCompiling() {
        final String[][] cells = {
            {"SELECT x.id FROM none x WHERE EXISTS (SELECT 1 FROM u JOIN TABLE(FLATTEN(INPUT => x.arr)) f ON u.k = f.value) ORDER BY 1",
                "Unsupported feature 'lateral table function called with OUTER JOIN syntax or a join predicate (ON clause)'."},
            {"SELECT x.id FROM none x WHERE x.n NOT IN (SELECT f.value FROM u JOIN TABLE(FLATTEN(INPUT => x.arr)) f ON u.k = f.value) ORDER BY 1",
                "Unsupported feature 'lateral table function called with OUTER JOIN syntax or a join predicate (ON clause)'."},
            {"SELECT x.id FROM t x WHERE FALSE AND EXISTS (SELECT 1 FROM u JOIN TABLE(FLATTEN(INPUT => x.arr)) f ON u.k = f.value) ORDER BY 1",
                "Unsupported feature 'lateral table function called with OUTER JOIN syntax or a join predicate (ON clause)'."},
            {"SELECT x.id FROM none x, u JOIN TABLE(FLATTEN(INPUT => x.arr)) f ON u.k = f.value",
                "Unsupported feature 'lateral table function called with OUTER JOIN syntax or a join predicate (ON clause)'."},
            {"SELECT x.id FROM one x WHERE EXISTS (SELECT 1 FROM u JOIN TABLE(FLATTEN(INPUT => x.arr)) f ON u.k = f.value) ORDER BY 1",
                "Unsupported feature 'lateral table function called with OUTER JOIN syntax or a join predicate (ON clause)'."},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }
}
