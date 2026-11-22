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
 * A view over a correlated subquery the account cannot evaluate is judged where the reading statement reaches the
 * subquery's column (live-verified): a statement that reads the column is refused in the view's name, under a
 * constant-false WHERE or HAVING and a LIMIT 0 too, while a row the statement's own filter drops and a column it
 * does not read raise nothing. A view read through another view is named by the outer one.
 */
public class ViewSubqueryExpansionTest extends MeasuredCellsTestSupport {

    @Test
    public void filtersAndLimits() {
        engine.execute("CREATE OR REPLACE TABLE u51 (id INT, v INT)");
        engine.execute("INSERT INTO u51 VALUES (1, 10), (2, 20)");
        engine.execute("CREATE OR REPLACE VIEW v51 AS SELECT u51.id, (SELECT v FROM u51 x WHERE x.id = u51.id) y "
            + "FROM u51");
        assertCells(new String[][] {
            {"SELECT * FROM v51",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW "
                + "object: V51"},
            {"SELECT COUNT(y) FROM v51",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW "
                + "object: V51"},
            {"SELECT * FROM v51 WHERE y IS NULL",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW "
                + "object: V51"},
            {"SELECT * FROM v51 WHERE FALSE",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW "
                + "object: V51"},
            {"SELECT y FROM v51 WHERE FALSE",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW "
                + "object: V51"},
            {"EXPLAIN SELECT * FROM v51 WHERE FALSE",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW "
                + "object: V51"},
            {"SELECT * FROM v51 LIMIT 0",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW "
                + "object: V51"},
            {"SELECT y FROM v51 WHERE id > 5",
                ""},
            {"SELECT COUNT(*) FROM v51",
                "2"},
            {"SELECT id FROM v51 WHERE id > 5",
                ""},
            {"SELECT * FROM v51 WHERE id > 5",
                ""},
            {"SELECT * FROM v51 WHERE id = 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW "
                + "object: V51"},
            {"SELECT y FROM v51 WHERE id < 0",
                ""},
            {"SELECT y FROM v51 WHERE 1 = 0",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW "
                + "object: V51"},
            {"SELECT y FROM v51 LIMIT 0",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW "
                + "object: V51"},
            {"SELECT id FROM v51 WHERE FALSE",
                ""},
            {"SELECT id FROM v51 LIMIT 0",
                ""},
            {"SELECT * FROM (SELECT * FROM v51)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW "
                + "object: V51"},
            {"SELECT y FROM (SELECT * FROM v51)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW "
                + "object: V51"},
            {"SELECT id FROM v51 ORDER BY y",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW "
                + "object: V51"},
            {"SELECT id FROM v51 WHERE y > 0",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW "
                + "object: V51"},
            {"SELECT COUNT(*) FROM v51 WHERE FALSE",
                "0"},
            {"SELECT COUNT(y) FROM v51 WHERE id > 5",
                "0"},
            {"SELECT y FROM v51 WHERE id > 5 AND id < 100",
                ""},
            {"SELECT y FROM v51 WHERE id > 5 OR id < 0",
                ""},
            {"SELECT y FROM v51 WHERE id > 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW "
                + "object: V51"},
            {"SELECT y FROM v51 WHERE id IS NULL",
                ""},
            {"SELECT y FROM v51 WHERE id IN (7, 8)",
                ""},
            {"SELECT y FROM v51 WHERE id = 5",
                ""},
            {"SELECT y FROM v51 WHERE id BETWEEN 5 AND 9",
                ""},
            {"SELECT y FROM v51 WHERE NULL",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW "
                + "object: V51"},
            {"SELECT id FROM v51 GROUP BY id HAVING MAX(y) > 0",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW "
                + "object: V51"},
            {"SELECT y FROM v51 WHERE id + 0 > 5",
                ""},
            {"SELECT id FROM v51 WHERE EXISTS (SELECT 1 FROM v51 z WHERE z.y = 1)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW "
                + "object: V51"},
            {"SELECT y FROM v51 WHERE id > 5 LIMIT 0",
                ""},
        });
    }

    @Test
    public void nestedViewsAndOtherShapes() {
        engine.execute("CREATE OR REPLACE TABLE u51 (id INT, v INT)");
        engine.execute("INSERT INTO u51 VALUES (1, 10), (2, 20)");
        engine.execute("CREATE OR REPLACE VIEW v51 AS SELECT u51.id, (SELECT v FROM u51 x WHERE x.id = u51.id) y "
            + "FROM u51");
        engine.execute("CREATE OR REPLACE VIEW w51 AS SELECT * FROM v51");
        engine.execute("CREATE OR REPLACE TABLE e51 (id INT, v INT)");
        engine.execute("CREATE OR REPLACE VIEW ve51 AS SELECT e51.id, (SELECT v FROM u51 x WHERE x.id = e51.id) y "
            + "FROM e51");
        assertCells(new String[][] {
            {"SELECT * FROM w51",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW "
                + "object: W51"},
            {"SELECT y FROM w51 WHERE id > 5",
                ""},
            {"SELECT * FROM w51 WHERE FALSE",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW "
                + "object: W51"},
            {"SELECT * FROM ve51",
                ""},
            {"SELECT * FROM ve51 WHERE FALSE",
                ""},
            {"EXPLAIN SELECT * FROM v51",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW "
                + "object: V51"},
            {"EXPLAIN SELECT y FROM v51 LIMIT 0",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW "
                + "object: V51"},
            {"SELECT y FROM v51 WHERE id > 5 AND FALSE",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW "
                + "object: V51"},
            {"SELECT y FROM v51 WHERE FALSE AND id = 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW "
                + "object: V51"},
            {"SELECT y FROM v51 WHERE FALSE OR id > 5",
                ""},
            {"SELECT y FROM v51 WHERE TRUE",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW "
                + "object: V51"},
            {"SELECT TOP 0 y FROM v51",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW "
                + "object: V51"},
            {"SELECT y FROM v51 FETCH FIRST 0 ROWS ONLY",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW "
                + "object: V51"},
            {"SELECT y FROM v51 LIMIT 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW "
                + "object: V51"},
            {"SELECT y FROM v51 LIMIT 0 OFFSET 0",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW "
                + "object: V51"},
            {"SELECT COUNT(*) FROM (SELECT y FROM v51) WHERE FALSE",
                "0"},
            {"SELECT 1 WHERE EXISTS (SELECT y FROM v51 WHERE FALSE)",
                ""},
            {"SELECT y FROM v51 UNION ALL SELECT 1 WHERE FALSE",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW "
                + "object: V51"},
            {"SELECT a.y FROM v51 a JOIN u51 b ON a.id = b.id WHERE a.id > 5",
                ""},
            {"SELECT (SELECT COUNT(y) FROM v51)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW "
                + "object: V51"},
            {"SELECT y FROM v51 HAVING FALSE",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside VIEW "
                + "object: V51"},
            {"SELECT id, y FROM v51 WHERE id = 3",
                ""},
        });
    }
}
