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
 * A scalar subquery of the outer query that reads a table is refused at its own place, as the statement compiles,
 * where a select item of it evaluates, at any depth, a subquery opening with the outer query's aggregate: the inner
 * query's WHERE FALSE or empty table spares it no more than the outer's, a FROM-less level passes the judgement on, and
 * a branch the inner query's constants or statistics, or the outer table's, settle against the subquery spares it.
 * A window or an aggregate over the outer row in a place of its own is refused with its own sentence and code
 * (all live-verified).
 */
public class NestedOuterAggregateTest extends MeasuredCellsTestSupport {

    @Test
    public void nestedSubqueriesAndPlaces() {
        engine.execute("CREATE OR REPLACE TABLE e (i INT)");
        engine.execute("CREATE OR REPLACE TABLE o (i INT)");
        engine.execute("INSERT INTO o VALUES (1)");
        engine.execute("CREATE OR REPLACE TABLE g (v INT)");
        engine.execute("INSERT INTO g VALUES (7), (8)");
        engine.execute("CREATE OR REPLACE TABLE m (i INT)");
        engine.execute("INSERT INTO m VALUES (1), (10)");
        assertCells(new String[][] {
            {"SELECT (SELECT (SELECT MAX(e.i) FROM g) FROM g) FROM e",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT (SELECT (SELECT MAX(o.i) FROM g) FROM g) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT (SELECT (SELECT MAX(o.i) FROM g LIMIT 1) FROM g LIMIT 1) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT (SELECT 1 + (SELECT MAX(e.i) FROM g) FROM g) FROM e",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT (SELECT (SELECT MAX(e.i) FROM g) FROM g LIMIT 1) FROM e",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT (SELECT v + (SELECT MAX(o.i) FROM g) FROM g LIMIT 1) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT (SELECT (SELECT (SELECT MAX(e.i) FROM g)) FROM g) FROM e",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT (SELECT (SELECT (SELECT MAX(e.i) FROM g) FROM g)) FROM e",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 16"},
            {"SELECT (SELECT (SELECT MAX(e.i) FROM g) FROM g WHERE FALSE) FROM e",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT (SELECT IFF(FALSE, (SELECT MAX(e.i) FROM g), 0) FROM g LIMIT 1) FROM e",
                ""},
            {"SELECT (SELECT (SELECT MAX(e.i) FROM g) FROM e AS e2) FROM e",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT (SELECT ZEROIFNULL(MAX(o.i)) FROM g) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT (SELECT ZEROIFNULL(MAX(e.i)) FROM g) FROM e",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT (SELECT ZEROIFNULL(MAX(m.i)) FROM g) FROM m GROUP BY m.i ORDER BY 1",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[MAX(M.I)] can only appear in having or select clause"},
            {"SELECT m.i, (SELECT ZEROIFNULL(m.i) FROM g LIMIT 1) FROM m GROUP BY m.i ORDER BY 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 13"},
            {"SELECT (SELECT MAX(o.i) OVER () FROM g LIMIT 1) FROM o",
                "REFUSED SQL compilation error: | Window function [MAX(O.I) OVER ()] contains a correlation."},
            {"SELECT (SELECT MAX(e.i) OVER () FROM g LIMIT 1) FROM e",
                "REFUSED SQL compilation error: | Window function [MAX(E.I) OVER ()] contains a correlation."},
            {"SELECT (SELECT TRANSFORM([1], x INT -> x + MAX(o.i)) FROM g) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 43 | Function 'MAX(O.I)' is not "
                + "allowed inside lambda expression. "},
            {"SELECT (SELECT TRANSFORM([1], x INT -> x + MAX(e.i)) FROM g) FROM e",
                "REFUSED SQL compilation error: error line 1 at position 43 | Function 'MAX(E.I)' is not "
                + "allowed inside lambda expression. "},
        });
    }

    @Test
    public void shapesTheRuleSpares() {
        engine.execute("CREATE OR REPLACE TABLE e (i INT)");
        engine.execute("CREATE OR REPLACE TABLE o (i INT)");
        engine.execute("INSERT INTO o VALUES (1)");
        engine.execute("CREATE OR REPLACE TABLE g (v INT)");
        engine.execute("INSERT INTO g VALUES (7), (8)");
        assertCells(new String[][] {
            {"SELECT (SELECT (SELECT MAX(g2.v) FROM g g2) FROM g LIMIT 1) FROM o",
                "8"},
            {"SELECT (SELECT (SELECT COUNT(*) + MAX(o.i) FROM g) FROM g LIMIT 1) FROM o",
                "3"},
            {"SELECT (SELECT (SELECT MAX(g.v) FROM g g2) FROM g LIMIT 1) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 16"},
            {"SELECT (SELECT (SELECT MAX(g.v + o.i) FROM g g2) FROM g LIMIT 1) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 16"},
            {"SELECT (SELECT CASE WHEN g.v > 100 THEN (SELECT MAX(o.i) FROM g g2) END FROM g LIMIT 1) FROM"
                + " o",
                "null"},
            {"SELECT (SELECT COALESCE(g.v, (SELECT MAX(o.i) FROM g g2)) FROM g LIMIT 1) FROM o",
                "7"},
            {"SELECT (SELECT MAX(v) FROM g WHERE v > (SELECT MAX(o.i) FROM g g2)) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 40"},
            {"SELECT (SELECT (SELECT MAX(i) FROM g) FROM g LIMIT 1) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT o.i, (SELECT (SELECT MAX(o.i) FROM g) FROM g LIMIT 1) FROM o GROUP BY o.i",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 13"},
            {"SELECT (SELECT TRANSFORM([1], x INT -> x + MAX(v)) FROM g) FROM e",
                "REFUSED SQL compilation error: error line 1 at position 43 | Function 'MAX(G.V)' is not "
                + "allowed inside lambda expression. "},
        });
    }

    @Test
    public void guardsTheStatisticsSettle() {
        engine.execute("CREATE OR REPLACE TABLE e (i INT)");
        engine.execute("CREATE OR REPLACE TABLE o (i INT)");
        engine.execute("INSERT INTO o VALUES (1)");
        engine.execute("CREATE OR REPLACE TABLE g (v INT)");
        engine.execute("INSERT INTO g VALUES (7), (8)");
        assertCells(new String[][] {
            {"SELECT (SELECT CASE WHEN g.v > 100 THEN (SELECT MAX(o.i) FROM g g2) END FROM g LIMIT 1) FROM"
                + " o",
                "null"},
            {"SELECT (SELECT CASE WHEN h.v > 100 THEN (SELECT MAX(o.i) FROM g g2) END FROM g h LIMIT 1) "
                + "FROM o",
                "null"},
            {"SELECT (SELECT CASE WHEN v > 100 THEN (SELECT MAX(o.i) FROM g g2) END FROM g LIMIT 1) FROM o",
                "null"},
            {"SELECT (SELECT CASE WHEN g.v > 7 THEN (SELECT MAX(o.i) FROM g g2) END FROM g LIMIT 1) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT (SELECT CASE WHEN g.v > 100 THEN (SELECT MAX(e.i) FROM g g2) END FROM g LIMIT 1) FROM"
                + " e",
                ""},
            {"SELECT (SELECT IFF(v < 100, 0, (SELECT MAX(o.i) FROM g g2)) FROM g LIMIT 1) FROM o",
                "0"},
            {"SELECT (SELECT CASE WHEN g.v > 100 THEN (SELECT MAX(o.i) FROM g g2) END FROM g WHERE FALSE) "
                + "FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT (SELECT CASE WHEN g.v > 100 THEN (SELECT MAX(o.i) FROM g g2) END FROM g WHERE g.v > "
                + "7) FROM o",
                "null"},
            {"SELECT (SELECT CASE WHEN e2.i > 100 THEN (SELECT MAX(o.i) FROM g g2) END FROM e e2) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT (SELECT CASE WHEN o.i > 100 THEN (SELECT MAX(o.i) FROM g g2) END FROM g LIMIT 1) FROM"
                + " o",
                "null"},
        });
    }
}
