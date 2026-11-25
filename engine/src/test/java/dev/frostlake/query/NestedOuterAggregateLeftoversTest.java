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
 * Subqueries over an outer query's own aggregate nested in another subquery, measured cell by cell
 * (live-verified): an EXISTS body's select item whose value no row of the body reads is compiled but
 * never planned; a scalar subquery holding such a subquery in any of its clauses is refused at its own
 * position unless it aggregates its rows into one, ungrouped; a settled conjunct or disjunct, an ANY
 * body and an IN body filtering rows spare the subquery; an invalid function carries 1005 / 42P13; and an
 * aggregate in a lambda's body names the lambda's parameters as the call converts them.
 */
public class NestedOuterAggregateLeftoversTest extends MeasuredCellsTestSupport {

    @Test
    public void existsBodySelectItems() {
        engine.execute("CREATE TABLE e (i INT)");
        engine.execute("CREATE TABLE o (i INT)");
        engine.execute("INSERT INTO o VALUES (1)");
        engine.execute("CREATE TABLE g (v INT)");
        engine.execute("INSERT INTO g VALUES (7), (8)");
        engine.execute("CREATE TABLE s (x VARCHAR)");
        engine.execute("INSERT INTO s VALUES ('3')");
        assertCells(new String[][] {
            {"SELECT EXISTS (SELECT (SELECT MAX(o.i) FROM g) FROM g) FROM o",
                "true"},
            {"SELECT NOT EXISTS (SELECT (SELECT MAX(o.i) FROM g) FROM g) FROM o",
                "false"},
            {"SELECT EXISTS (SELECT (SELECT MAX(e.i) FROM g) FROM g) FROM e",
                ""},
            {"SELECT EXISTS (SELECT MAX(o.i) FROM g) FROM o",
                "true"},
            {"SELECT EXISTS (SELECT 1 FROM g WHERE v > (SELECT MAX(o.i) FROM g)) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 42"},
            {"SELECT 1 IN (SELECT (SELECT MAX(o.i) FROM g) FROM g) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 21"},
            {"SELECT 1 = ANY (SELECT (SELECT MAX(o.i) FROM g) FROM g) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 24"},
            {"SELECT 1 FROM o WHERE EXISTS (SELECT (SELECT MAX(o.i) FROM g) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[MAX(O.I)] can only appear in having or select clause"},
            {"SELECT EXISTS (SELECT (SELECT MAX(o.i) FROM g) FROM g WHERE FALSE) FROM o",
                "false"},
            {"SELECT EXISTS (SELECT (SELECT (SELECT MAX(o.i) FROM g) FROM g) FROM g) FROM o",
                "true"},
            {"SELECT 7 IN (SELECT (SELECT MAX(o.i) FROM g) FROM g) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 21"},
            {"SELECT EXISTS (SELECT 1 FROM g HAVING (SELECT MAX(o.i) FROM g) > 0) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 39"},
            {"SELECT 1 FROM o WHERE NOT EXISTS (SELECT (SELECT MAX(o.i) FROM g) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[MAX(O.I)] can only appear in having or select clause"},
            {"SELECT 1 FROM o HAVING EXISTS (SELECT (SELECT MAX(o.i) FROM g) FROM g)",
                "1"},
            {"SELECT 1 IN (SELECT 1 FROM g WHERE v > (SELECT MAX(o.i) FROM g)) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 40"},
            {"SELECT (SELECT g.v FROM g WHERE g.v = (SELECT MAX(o.i) FROM g g2)) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT (SELECT MAX(v) FROM g WHERE v > (SELECT MAX(o.i) FROM g g2)) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 40"},
            {"SELECT (SELECT g.v FROM g HAVING g.v = (SELECT MAX(o.i) FROM g g2)) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT (SELECT MAX(g.v) FROM g HAVING MAX(g.v) > (SELECT MAX(o.i) FROM g g2)) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 50"},
            {"SELECT (SELECT g.v FROM g ORDER BY (SELECT MAX(o.i) FROM g g2) LIMIT 1) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT (SELECT g.v FROM g WHERE g.v > 0 AND g.v = (SELECT MAX(o.i) FROM g g2)) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT o.i, (SELECT g.v FROM g WHERE g.v = (SELECT MAX(o.i) FROM g g2)) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 13"},
            {"SELECT  (SELECT g.v FROM g WHERE g.v = (SELECT MAX(o.i) FROM g g2) LIMIT 1) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 9"},
            {"SELECT 1 FROM o WHERE 1 = (SELECT g.v FROM g WHERE g.v = (SELECT MAX(o.i) FROM g g2))",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[MAX(O.I)] can only appear in having or select clause"},
            {"SELECT (SELECT g.v FROM g WHERE (SELECT MAX(o.i) FROM g g2) > 0 LIMIT 1) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT (SELECT COUNT(*) FROM g WHERE g.v = (SELECT MAX(o.i) FROM g g2)) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 44"},
            {"SELECT (SELECT g.v FROM g GROUP BY g.v HAVING g.v = (SELECT MAX(o.i) FROM g g2)) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT 1, (SELECT g.v FROM g WHERE g.v = (SELECT MAX(o.i) FROM g g2)) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 11"},
            {"SELECT (SELECT g.v FROM g WHERE g.v = (SELECT MAX(e.i) FROM g g2)) FROM e",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT (SELECT (SELECT g.v FROM g WHERE g.v = (SELECT MAX(o.i) FROM g g2)) FROM g LIMIT 1) "
                + "FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT (SELECT g.v FROM g WHERE g.v IN (SELECT MAX(o.i) FROM g g2)) FROM o",
                "null"},
            {"SELECT (SELECT g.v FROM g WHERE EXISTS (SELECT MAX(o.i) FROM g g2)) FROM o",
                "REFUSED Single-row subquery returns more than one row."},
            {"SELECT (SELECT g.v FROM g JOIN g g3 ON g.v = (SELECT MAX(o.i) FROM g g2)) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT (SELECT MAX(o.i) FROM g) OVER () FROM o",
                "REFUSED SQL compilation error: | invalid function '(SELECT MAX(O.I) AS \"MAX(O.I)\" FROM G "
                + "AS G)'"},
            {"SELECT (SELECT MAX(e.i) FROM g) OVER () FROM e",
                "REFUSED SQL compilation error: | invalid function '(SELECT MAX(E.I) AS \"MAX(E.I)\" FROM G "
                + "AS G)'"},
            {"SELECT (SELECT MAX(v) FROM g) OVER () FROM o",
                "REFUSED SQL compilation error: | invalid function '(SELECT MAX(V) AS \"MAX(V)\" FROM G AS "
                + "G)'"},
            {"SELECT (SELECT 1) OVER () FROM o",
                "REFUSED SQL compilation error: | invalid function '(SELECT 1 AS \"1\" FROM (VALUES (null)) "
                + "DUAL)'"},
            {"SELECT (SELECT MAX(o.i) FROM g) OVER (ORDER BY o.i) FROM o",
                "REFUSED SQL compilation error: | invalid function '(SELECT MAX(O.I) AS \"MAX(O.I)\" FROM G "
                + "AS G)'"},
            {"SELECT (o.i) OVER () FROM o",
                "REFUSED SQL compilation error: | invalid function 'O.I'"},
            {"SELECT (SELECT v FROM g LIMIT 1) OVER () FROM o",
                "REFUSED SQL compilation error: | invalid function '(SELECT V AS \"V\" FROM G AS G LIMIT 1 "
                + "OFFSET 0)'"},
        });
    }

    @Test
    public void middleClausePlaces() {
        engine.execute("CREATE TABLE e (i INT)");
        engine.execute("CREATE TABLE o (i INT)");
        engine.execute("INSERT INTO o VALUES (1)");
        engine.execute("CREATE TABLE g (v INT)");
        engine.execute("INSERT INTO g VALUES (7), (8)");
        assertCells(new String[][] {
            {"SELECT (SELECT MAX(v) + (SELECT MAX(o.i) FROM g g2) FROM g) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 25"},
            {"SELECT (SELECT COUNT(*) FROM g WHERE (SELECT MAX(o.i) FROM g g2) > 0) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 38"},
            {"SELECT (SELECT g.v FROM g WHERE FALSE AND g.v = (SELECT MAX(o.i) FROM g g2)) FROM o",
                "null"},
            {"SELECT (SELECT g.v FROM g WHERE g.v > 100 AND g.v = (SELECT MAX(o.i) FROM g g2)) FROM o",
                "null"},
            {"SELECT (SELECT g.v FROM g WHERE IFF(FALSE, (SELECT MAX(o.i) FROM g g2), 0) = 1) FROM o",
                "null"},
            {"SELECT (SELECT g.v FROM g WHERE g.v = (SELECT MAX(o.i) FROM g g2)) FROM e",
                "REFUSED SQL compilation error: error line 1 at position 50 | invalid identifier 'O.I'"},
            {"SELECT (SELECT g.v FROM g QUALIFY g.v = (SELECT MAX(o.i) FROM g g2)) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 26 | found QUALIFY clause but no "
                + "window function."},
            {"SELECT (SELECT 1 FROM g GROUP BY (SELECT MAX(o.i) FROM g g2) LIMIT 1) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT (SELECT DISTINCT g.v FROM g WHERE g.v = (SELECT MAX(o.i) FROM g g2)) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT (SELECT ROW_NUMBER() OVER (ORDER BY v) FROM g WHERE g.v = (SELECT MAX(o.i) FROM g g2)"
                + " LIMIT 1) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT 1 FROM o HAVING (SELECT (SELECT MAX(o.i) FROM g) FROM g) > 0",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 24"},
            {"SELECT 1 FROM o ORDER BY (SELECT (SELECT MAX(o.i) FROM g) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[MAX(O.I)] can only appear in having or select clause"},
            {"SELECT 1 FROM o HAVING (SELECT g.v FROM g WHERE g.v = (SELECT MAX(o.i) FROM g g2)) > 0",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 24"},
            {"SELECT 1 FROM o HAVING  (SELECT (SELECT MAX(o.i) FROM g) FROM g LIMIT 1) > 0",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 25"},
            {"SELECT (SELECT g.v FROM g, g g3 WHERE g.v = (SELECT MAX(o.i) FROM g g2)) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT (SELECT g.v FROM g GROUP BY g.v HAVING COUNT(*) = (SELECT MAX(o.i) FROM g g2)) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT (SELECT MAX(v) FROM g ORDER BY (SELECT MAX(o.i) FROM g g2)) FROM o",
                "8"},
            {"SELECT (SELECT g.v FROM g WHERE g.v = (SELECT MAX(v) FROM g g2)) FROM o",
                "8"},
            {"SELECT (SELECT g.v FROM g WHERE g.v = (SELECT COUNT(*) + MAX(o.i) FROM g g2)) FROM o",
                "null"},
            {"SELECT (SELECT g.v FROM g WHERE g.v = (SELECT MAX(o.i) + MAX(g2.v) FROM g g2)) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT (SELECT g.v FROM g WHERE g.v = (SELECT (SELECT MAX(o.i) FROM g g3) FROM g g2)) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT (SELECT g.v FROM g WHERE g.v = (SELECT MAX(o.i) FROM g g2) LIMIT 1) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT (SELECT g.v FROM g WHERE g.v IN (1, (SELECT MAX(o.i) FROM g g2))) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT (SELECT g.v FROM g WHERE CASE WHEN g.v > 100 THEN (SELECT MAX(o.i) FROM g g2) END = "
                + "1) FROM o",
                "null"},
            {"SELECT (SELECT 1 FROM g LIMIT 1) FROM o WHERE (SELECT g.v FROM g WHERE g.v = (SELECT "
                + "MAX(o.i) FROM g g2)) > 0",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[MAX(O.I)] can only appear in having or select clause"},
            {"SELECT (SELECT g.v FROM g WHERE g.v = (SELECT MAX(g.v) FROM g g2)) FROM o",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[MAX(G.V)] can only appear in having or select clause"},
        });
    }

    @Test
    public void existsValueRead() {
        engine.execute("CREATE TABLE e (i INT)");
        engine.execute("CREATE TABLE o (i INT)");
        engine.execute("INSERT INTO o VALUES (1)");
        engine.execute("CREATE TABLE g (v INT)");
        engine.execute("INSERT INTO g VALUES (7), (8)");
        assertCells(new String[][] {
            {"SELECT EXISTS (SELECT (SELECT v FROM g) FROM g) FROM o",
                "true"},
            {"SELECT EXISTS (SELECT (SELECT MAX(o.i) FROM g), COUNT(*) FROM g) FROM o",
                "true"},
            {"SELECT EXISTS (SELECT (SELECT MAX(o.i) FROM g) AS m FROM g ORDER BY m) FROM o",
                "true"},
            {"SELECT EXISTS (SELECT (SELECT MAX(o.i) FROM g) FROM g UNION ALL SELECT 1) FROM o",
                "true"},
            {"SELECT EXISTS (SELECT 1 + (SELECT MAX(o.i) FROM g) FROM g) FROM o",
                "true"},
            {"SELECT EXISTS (SELECT (SELECT MAX(o.i) FROM g) FROM g LIMIT 1) FROM o",
                "true"},
            {"SELECT EXISTS (SELECT (SELECT MAX(o.i) FROM g) FROM g WHERE g.v = o.i) FROM o",
                "false"},
            {"SELECT EXISTS (SELECT (SELECT 1/0) FROM g) FROM o",
                "true"},
            {"SELECT EXISTS (SELECT (SELECT v FROM g WHERE v = o.i + g2.v) FROM g g2) FROM o",
                "true"},
            {"SELECT EXISTS (SELECT (SELECT MAX(o.i) FROM g) FROM g QUALIFY ROW_NUMBER() OVER (ORDER BY v)"
                + " = 1) FROM o",
                "true"},
            {"SELECT EXISTS (SELECT (SELECT MAX(o.i) FROM g) FROM g) FROM o GROUP BY o.i",
                "true"},
            {"SELECT o.i FROM o WHERE NOT EXISTS (SELECT (SELECT v FROM g) FROM g)",
                ""},
        });
    }

    @Test
    public void existsCompileErrors() {
        engine.execute("CREATE TABLE e (i INT)");
        engine.execute("CREATE TABLE o (i INT)");
        engine.execute("INSERT INTO o VALUES (1)");
        engine.execute("CREATE TABLE g (v INT)");
        engine.execute("INSERT INTO g VALUES (7), (8)");
        assertCells(new String[][] {
            {"SELECT EXISTS (SELECT (SELECT nosuch FROM g) FROM g) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 30 | invalid identifier 'NOSUCH'"},
            {"SELECT EXISTS (SELECT 1 FROM g ORDER BY nosuch) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 40 | invalid identifier 'NOSUCH'"},
            {"SELECT EXISTS (SELECT 1 FROM g ORDER BY (SELECT nosuch FROM g)) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 48 | invalid identifier 'NOSUCH'"},
            {"SELECT EXISTS (SELECT (SELECT 'a'::INT) FROM g) FROM o",
                "true"},
            {"SELECT EXISTS (SELECT (SELECT MAX(o.i) FROM g) FROM g GROUP BY g.v) FROM o",
                "true"},
            {"SELECT EXISTS (SELECT (SELECT MAX(o.i) FROM g) FROM g UNION SELECT 1) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 23"},
            {"SELECT EXISTS (SELECT (SELECT MAX(o.i) FROM g) FROM g WHERE FALSE) FROM e",
                "REFUSED SQL compilation error: error line 1 at position 34 | invalid identifier 'O.I'"},
            {"SELECT 1 FROM o WHERE EXISTS (SELECT (SELECT g.v FROM g WHERE g.v = (SELECT MAX(o.i) FROM g "
                + "g2)) FROM g)",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[MAX(O.I)] can only appear in having or select clause"},
            {"SELECT EXISTS (SELECT (SELECT g.v FROM g WHERE g.v = (SELECT MAX(o.i) FROM g g2)) FROM g) "
                + "FROM o",
                "true"},
            {"SELECT EXISTS (SELECT (SELECT MAX(o.i) FROM g) FROM g) FROM o WHERE FALSE",
                ""},
            {"SELECT EXISTS (SELECT (SELECT 1 FROM g g2 WHERE g2.v > o.i) FROM g) FROM o",
                "true"},
        });
    }

    @Test
    public void settledConditions() {
        engine.execute("CREATE TABLE e (i INT)");
        engine.execute("CREATE TABLE o (i INT)");
        engine.execute("INSERT INTO o VALUES (1)");
        engine.execute("CREATE TABLE g (v INT)");
        engine.execute("INSERT INTO g VALUES (7), (8)");
        assertCells(new String[][] {
            {"SELECT (SELECT MAX(v) FROM g WHERE v = (SELECT MAX(o.i) FROM g g2) GROUP BY v) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT (SELECT MAX(v) + (SELECT MAX(o.i) FROM g g2) FROM g GROUP BY v) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT (SELECT COUNT(*) OVER () FROM g WHERE v = (SELECT MAX(o.i) FROM g g2) LIMIT 1) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT (SELECT g.v FROM g WHERE g.v = (SELECT MAX(o.i) FROM g g2) OR g.v > 100) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT (SELECT g.v FROM g WHERE g.v > 100 OR g.v = (SELECT MAX(o.i) FROM g g2)) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT (SELECT g.v FROM g WHERE TRUE OR g.v = (SELECT MAX(o.i) FROM g g2) LIMIT 1) FROM o",
                "7"},
            {"SELECT (SELECT ANY_VALUE(v) FROM g WHERE v = (SELECT MAX(o.i) FROM g g2)) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 46"},
            {"SELECT (SELECT g.v FROM g WHERE g.v > 100 AND (g.v = (SELECT MAX(o.i) FROM g g2))) FROM o",
                "null"},
            {"SELECT (SELECT g.v > 100 AND (SELECT MAX(o.i) FROM g g2) > 0 FROM g LIMIT 1) FROM o",
                "false"},
            {"SELECT (SELECT g.v FROM g HAVING FALSE AND g.v = (SELECT MAX(o.i) FROM g g2)) FROM o",
                "null"},
            {"SELECT 1 FROM o ORDER BY (SELECT g.v FROM g WHERE g.v = (SELECT MAX(o.i) FROM g g2))",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                + "[MAX(O.I)] can only appear in having or select clause"},
            {"SELECT (SELECT g.v FROM g WHERE g.v = (SELECT MAX(o.i) FROM g g2)) FROM o WHERE FALSE",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT (SELECT g.v FROM g WHERE g.v = (SELECT MAX(o.i) FROM g g2)) FROM o, e",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT (SELECT g.v FROM g WHERE g.v = 1 AND g.v = (SELECT MAX(o.i) FROM g g2)) FROM o",
                "null"},
            {"SELECT (SELECT g.v FROM g WHERE g.v = (SELECT MAX(o.i) FROM g g2 WHERE FALSE)) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT (SELECT g.v FROM g WHERE g.v = IFF(FALSE, (SELECT MAX(o.i) FROM g g2), 0)) FROM o",
                "null"},
            {"SELECT (SELECT g.v FROM g WHERE g.v = CASE WHEN g.v > 7 THEN (SELECT MAX(o.i) FROM g g2) "
                + "END) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
        });
    }

    @Test
    public void membershipBodies() {
        engine.execute("CREATE TABLE e (i INT)");
        engine.execute("CREATE TABLE o (i INT)");
        engine.execute("INSERT INTO o VALUES (1)");
        engine.execute("CREATE TABLE g (v INT)");
        engine.execute("INSERT INTO g VALUES (7), (8)");
        assertCells(new String[][] {
            {"SELECT (SELECT 1 IN (SELECT MAX(o.i) FROM g g2) FROM g LIMIT 1) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT (SELECT g.v = ANY (SELECT MAX(o.i) FROM g g2) FROM g LIMIT 1) FROM o",
                "false"},
            {"SELECT (SELECT g.v FROM g WHERE g.v = ANY (SELECT MAX(o.i) FROM g g2)) FROM o",
                "null"},
            {"SELECT (SELECT g.v FROM g WHERE g.v NOT IN (SELECT MAX(o.i) FROM g g2) LIMIT 1) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 8"},
            {"SELECT (SELECT MAX(v) + (SELECT MAX(o.i) FROM g g2) FROM g) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 25"},
            {"SELECT (SELECT MAX(v) + (SELECT MAX(e.i) FROM g g2) FROM g) FROM e",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 25"},
            {"SELECT (SELECT COUNT(*) + (SELECT MAX(o.i) FROM g g2) FROM g) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 27"},
            {"SELECT (SELECT g.v FROM g WHERE g.v = (SELECT MAX(o.i) FROM g g2) AND FALSE) FROM o",
                "null"},
        });
    }

    @Test
    public void invalidFunctionCodes() {
        engine.execute("CREATE TABLE o (i INT)");
        engine.execute("INSERT INTO o VALUES (1)");
        assertCells(new String[][] {
            {"SELECT UPPER(DISTINCT 'a')",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'UPPER(DISTINCT "
                + "'a')'"},
            {"SELECT -~1",
                "REFUSED SQL compilation error: | invalid function '-'"},
            {"SELECT ~1",
                "REFUSED SQL compilation error: | Unknown function \"~\"."},
            {"SELECT * FROM o PIVOT (nosuch_fn(i) FOR i IN (1))",
                "REFUSED SQL compilation error: | invalid function 'nosuch_fn'"},
            {"SELECT (1) OVER ()",
                "REFUSED SQL compilation error: | invalid function '1'"},
            {"SELECT 1 + 1 OVER ()",
                "REFUSED SQL compilation error: | invalid function '1'"},
            {"SELECT NOSUCHFN(ALL 1)",
                "REFUSED SQL compilation error: | Unknown function NOSUCHFN."},
            {"SELECT * FROM o PIVOT (\"SUM\"(i) FOR i IN (1))",
                "REFUSED SQL compilation error: | invalid function '\"SUM\"'"},
            {"SELECT (o.i + 1) OVER () FROM o",
                "REFUSED SQL compilation error: | Invalid function type [+] for window function."},
        });
    }

    @Test
    public void handlerErrorCodes() {
        engine.execute("CREATE TABLE o (i INT)");
        engine.execute("CREATE TABLE g (v INT)");
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$BEGIN EXECUTE IMMEDIATE 'SELECT (SELECT MAX(o.i) FROM g) OVER () FROM "
                + "o'; RETURN 'none'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE; END$$",
                "1005 42P13"},
            {"EXECUTE IMMEDIATE $$BEGIN EXECUTE IMMEDIATE 'SELECT -~1'; RETURN 'none'; EXCEPTION WHEN "
                + "OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE; END$$",
                "1005 42P13"},
            {"EXECUTE IMMEDIATE $$BEGIN EXECUTE IMMEDIATE 'SELECT UPPER(DISTINCT ''a'')'; RETURN 'none'; "
                + "EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE; END$$",
                "1014 42601"},
            {"EXECUTE IMMEDIATE $$BEGIN EXECUTE IMMEDIATE 'SELECT (o.i + 1) OVER () FROM o'; RETURN "
                + "'none'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE; END$$",
                "2060 42601"},
            {"EXECUTE IMMEDIATE $$BEGIN EXECUTE IMMEDIATE 'SELECT (1) OVER ()'; RETURN 'none'; EXCEPTION "
                + "WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE; END$$",
                "1005 42P13"},
        });
    }

    @Test
    public void lambdaParameterNames() {
        engine.execute("CREATE TABLE o (i INT, s VARCHAR, f FLOAT)");
        engine.execute("INSERT INTO o VALUES (1, 'a', 1.5)");
        assertCells(new String[][] {
            {"SELECT TRANSFORM([1], x INT -> MAX(x)) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 31 | Function 'MAX(TO_NUMBER(X))' is"
                + " not allowed inside lambda expression. "},
            {"SELECT TRANSFORM([1], x INT -> MIN(x)) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 31 | Function 'MIN(TO_NUMBER(X))' is"
                + " not allowed inside lambda expression. "},
            {"SELECT TRANSFORM([1], x INT -> SUM(x)) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 31 | Function 'SUM(TO_NUMBER(X))' is"
                + " not allowed inside lambda expression. "},
            {"SELECT TRANSFORM([1], x INT -> AVG(x)) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 31 | Function 'SUM(TO_NUMBER(X))' is"
                + " not allowed inside lambda expression. "},
            {"SELECT TRANSFORM([1], x INT -> COUNT(x)) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 31 | Function 'COUNT(TO_NUMBER(X))' "
                + "is not allowed inside lambda expression. "},
            {"SELECT TRANSFORM([1], x -> MAX(x)) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 27 | Function 'MAX(X)' is not "
                + "allowed inside lambda expression. "},
            {"SELECT TRANSFORM([1], x -> AVG(x)) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 27 | Function 'SUM(CAST(X AS "
                + "FLOAT))' is not allowed inside lambda expression. "},
            {"SELECT TRANSFORM(['a'], x VARCHAR -> MAX(x)) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 37 | Function 'MAX(CAST(X AS "
                + "VARCHAR(134217728)))' is not allowed inside lambda expression. "},
            {"SELECT TRANSFORM([1.5], x FLOAT -> MAX(x)) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 35 | Function 'MAX(CAST(X AS "
                + "FLOAT))' is not allowed inside lambda expression. "},
            {"SELECT TRANSFORM([1.5], x NUMBER(10,2) -> MAX(x)) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 42 | Function 'MAX(TO_NUMBER(X, 10, "
                + "2))' is not allowed inside lambda expression. "},
            {"SELECT TRANSFORM([1], x INT -> MAX(x + 1)) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 31 | Function 'MAX((TO_NUMBER(X)) + "
                + "1)' is not allowed inside lambda expression. "},
            {"SELECT FILTER([1], x INT -> MAX(x) > 0) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 28 | Function 'MAX(TO_NUMBER(X))' is"
                + " not allowed inside lambda expression. "},
            {"SELECT REDUCE([1], 0, (acc INT, x INT) -> acc + AVG(x)) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 48 | Function 'SUM(X)' is not "
                + "allowed inside lambda expression. "},
            {"SELECT REDUCE([1], 0, (acc INT, x INT) -> acc + MAX(x)) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 48 | Function 'MAX(X)' is not "
                + "allowed inside lambda expression. "},
            {"SELECT REDUCE([1], 0, (acc, x) -> acc + MAX(x)) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 40 | Function 'MAX(X)' is not "
                + "allowed inside lambda expression. "},
            {"SELECT REDUCE([1], 0, (acc INT, x INT) -> MAX(acc)) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 42 | Function 'MAX(ACC)' is not "
                + "allowed inside lambda expression. "},
            {"SELECT TRANSFORM([1], x INT -> SUM(x)) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 31 | Function 'SUM(TO_NUMBER(X))' is"
                + " not allowed inside lambda expression. "},
            {"SELECT TRANSFORM([1.5], x FLOAT -> AVG(x)) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 35 | Function 'SUM(CAST(X AS "
                + "FLOAT))' is not allowed inside lambda expression. "},
            {"SELECT TRANSFORM(['a'], x VARCHAR -> COUNT(x)) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 37 | Function 'COUNT(CAST(X AS "
                + "VARCHAR(134217728)))' is not allowed inside lambda expression. "},
            {"SELECT TRANSFORM([1], x INT -> MAX(o.i + x)) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 31 | Function 'MAX(O.I + "
                + "(TO_NUMBER(X)))' is not allowed inside lambda expression. "},
            {"SELECT TRANSFORM([1], x INT -> AVG(o.f)) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 31 | Function 'SUM(O.F)' is not "
                + "allowed inside lambda expression. "},
            {"SELECT TRANSFORM([1], x INT -> MAX(o.s)) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 31 | Function 'MAX(O.S)' is not "
                + "allowed inside lambda expression. "},
            {"SELECT TRANSFORM([1], x BOOLEAN -> MAX(x)) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 35 | Function 'MAX(CAST(X AS "
                + "BOOLEAN))' is not allowed inside lambda expression. "},
            {"SELECT TRANSFORM([1], x INT -> COUNT(*)) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 31 | Function 'COUNT(*)' is not "
                + "allowed inside lambda expression. "},
            {"SELECT TRANSFORM([1], x NUMBER -> MAX(x)) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 34 | Function 'MAX(TO_NUMBER(X))' is"
                + " not allowed inside lambda expression. "},
            {"SELECT TRANSFORM([1], x VARIANT -> MAX(x)) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 35 | Function 'MAX(X)' is not "
                + "allowed inside lambda expression. "},
            {"SELECT TRANSFORM([1], x DATE -> MAX(x)) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 32 | Function 'MAX(CAST(X AS DATE))'"
                + " is not allowed inside lambda expression. "},
            {"SELECT TRANSFORM([1], x INT -> MEDIAN(x)) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 31 | Function "
                + "'PERCENTILE_CONT(CAST(TO_NUMBER(X) AS NUMBER(38,3)), 0.5)' is not allowed inside lambda "
                + "expression. "},
            {"SELECT TRANSFORM([1], x INT -> ARRAY_AGG(x)) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 31 | Function "
                + "'ARRAY_AGG(TO_NUMBER(X))' is not allowed inside lambda expression. "},
            {"SELECT TRANSFORM([1], x INT -> MAX(x::VARCHAR)) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 31 | Function 'MAX(CAST(TO_NUMBER(X)"
                + " AS VARCHAR(134217728)))' is not allowed inside lambda expression. "},
        });
    }
}
