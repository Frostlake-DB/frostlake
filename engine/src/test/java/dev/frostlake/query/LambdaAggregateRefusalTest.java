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
 * An aggregate or a window call written in a lambda's body is refused at the call's place, named without its OVER
 * clause and an AVG by its SUM half, in the select list, ORDER BY or HAVING, in a subquery too and ahead of the outer
 * aggregate rule; a WHERE's aggregate refusal and a QUALIFY with no window function are raised first, and a subquery
 * in the body is judged as itself (all live-verified).
 */
public class LambdaAggregateRefusalTest extends MeasuredCellsTestSupport {

    @Test
    public void aggregatesInsideALambda() {
        engine.execute("CREATE OR REPLACE TABLE o (i INT)");
        engine.execute("INSERT INTO o VALUES (1)");
        engine.execute("CREATE OR REPLACE TABLE g (v INT)");
        engine.execute("INSERT INTO g VALUES (7), (8)");
        assertCells(new String[][] {
            {"SELECT TRANSFORM([1], x INT -> x + MAX(o.i)) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 35 | Function 'MAX(O.I)' is not "
                + "allowed inside lambda expression. "},
            {"SELECT TRANSFORM([1], x INT -> x + COUNT(*)) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 35 | Function 'COUNT(*)' is not "
                + "allowed inside lambda expression. "},
            {"SELECT FILTER([1], x INT -> x > SUM(o.i)) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 32 | Function 'SUM(O.I)' is not "
                + "allowed inside lambda expression. "},
            {"SELECT (SELECT TRANSFORM([1], x INT -> x + MAX(v)) FROM g) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 43 | Function 'MAX(G.V)' is not "
                + "allowed inside lambda expression. "},
            {"SELECT TRANSFORM([1], x INT -> x + (SELECT MAX(o.i) FROM g)) FROM o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 36"},
            {"SELECT TRANSFORM([1], x INT -> x + MAX(o.i) OVER ()) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 35 | Function 'MAX(O.I)' is not "
                + "allowed inside lambda expression. "},
            {"SELECT TRANSFORM([1],   x INT ->   x + max(o.i  )) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 39 | Function 'MAX(O.I)' is not "
                + "allowed inside lambda expression. "},
            {"SELECT o.i, TRANSFORM([1], x INT -> x + MAX(o.i)) FROM o GROUP BY o.i",
                "REFUSED SQL compilation error: error line 1 at position 40 | Function 'MAX(O.I)' is not "
                + "allowed inside lambda expression. "},
            {"SELECT 1 FROM o WHERE ARRAY_SIZE(TRANSFORM([1], x INT -> x + MAX(o.i))) > 0",
                "REFUSED SQL compilation error: | Invalid aggregate function in where clause [MAX(O.I)]"},
            {"SELECT TRANSFORM([1], x INT -> x + ARRAY_AGG(o.i)[0]) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 35 | Function 'ARRAY_AGG(O.I)' is "
                + "not allowed inside lambda expression. "},
            {"SELECT TRANSFORM([1],\n x INT -> x + MAX(o.i)) FROM o",
                "REFUSED SQL compilation error: error line 2 at position 14 | Function 'MAX(O.I)' is not "
                + "allowed inside lambda expression. "},
            {"SELECT TRANSFORM([1], x INT -> x + AVG(o.i)) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 35 | Function 'SUM(O.I)' is not "
                + "allowed inside lambda expression. "},
            {"SELECT TRANSFORM([1], x INT -> x + COUNT(*))",
                "REFUSED SQL compilation error: error line 1 at position 35 | Function 'COUNT(*)' is not "
                + "allowed inside lambda expression. "},
            {"SELECT (SELECT v FROM g WHERE ARRAY_SIZE(TRANSFORM([1], x INT -> x + MAX(v))) > 0) FROM o",
                "REFUSED SQL compilation error: | Invalid aggregate function in where clause [MAX(G.V)]"},
            {"SELECT 1 FROM o ORDER BY TRANSFORM([1], x INT -> x + MAX(o.i))[0]",
                "REFUSED SQL compilation error: error line 1 at position 53 | Function 'MAX(O.I)' is not "
                + "allowed inside lambda expression. "},
            {"SELECT 1 FROM o QUALIFY TRANSFORM([1], x INT -> x + MAX(o.i))[0] > 0",
                "REFUSED SQL compilation error: error line 1 at position 16 | found QUALIFY clause but no "
                + "window function."},
            {"SELECT 1 FROM o HAVING TRANSFORM([1], x INT -> x + MAX(o.i))[0] > 0",
                "REFUSED SQL compilation error: error line 1 at position 51 | Function 'MAX(O.I)' is not "
                + "allowed inside lambda expression. "},
            {"SELECT TRANSFORM([1], x INT -> x + ROW_NUMBER() OVER (ORDER BY o.i)) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 35 | Function 'ROW_NUMBER()' is not "
                + "allowed inside lambda expression. "},
            {"SELECT COUNT(*), TRANSFORM([1], x INT -> x + MAX(o.i)) FROM o",
                "REFUSED SQL compilation error: error line 1 at position 45 | Function 'MAX(O.I)' is not "
                + "allowed inside lambda expression. "},
            {"SELECT TRANSFORM([1], x INT -> x + nosuchfn(1) + MAX(o.i)) FROM o",
                "REFUSED SQL compilation error: | Unknown function NOSUCHFN."},
            {"SELECT TRANSFORM([1], x INT -> x + MAX(o.i)), nosuch FROM o",
                "REFUSED SQL compilation error: error line 1 at position 46 | invalid identifier 'NOSUCH'"},
        });
    }

    @Test
    public void theCodesAHandlerReads() {
        engine.execute("CREATE OR REPLACE TABLE o (i INT)");
        engine.execute("INSERT INTO o VALUES (1)");
        engine.execute("CREATE OR REPLACE TABLE g (v INT)");
        engine.execute("INSERT INTO g VALUES (7), (8)");
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$BEGIN EXECUTE IMMEDIATE 'SELECT TRANSFORM([1], x INT -> x + MAX(o.i)) "
                + "FROM o'; RETURN 'none'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE; END$$",
                "1156 42601"},
            {"EXECUTE IMMEDIATE $$BEGIN EXECUTE IMMEDIATE 'SELECT (SELECT MAX(o.i) OVER () FROM g LIMIT 1)"
                + " FROM o'; RETURN 'none'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE; END$$",
                "2085 42601"},
            {"EXECUTE IMMEDIATE $$BEGIN EXECUTE IMMEDIATE 'SELECT 1 FROM o WHERE MAX(o.i) > 0'; RETURN "
                + "'none'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE; END$$",
                "2035 42601"},
            {"EXECUTE IMMEDIATE $$BEGIN EXECUTE IMMEDIATE 'SELECT 1 FROM o QUALIFY o.i > 0'; RETURN "
                + "'none'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE; END$$",
                "992 42601"},
            {"EXECUTE IMMEDIATE $$BEGIN EXECUTE IMMEDIATE 'SELECT (SELECT ZEROIFNULL(MAX(o.i)) FROM g) "
                + "FROM o GROUP BY o.i'; RETURN 'none'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || "
                + "SQLSTATE; END$$",
                "2031 42601"},
            {"EXECUTE IMMEDIATE $$BEGIN EXECUTE IMMEDIATE 'SELECT (SELECT (SELECT MAX(o.i) FROM g) FROM g)"
                + " FROM o'; RETURN 'none'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE; END$$",
                "2031 42601"},
        });
    }
}
