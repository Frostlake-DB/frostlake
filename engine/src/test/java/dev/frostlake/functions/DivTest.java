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

package dev.frostlake.functions;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * DIV, a name the account lists and refuses to run: a call of two or more arguments is {@code Unsupported feature
 * 'DIV'.} once the statement compiles, before any row is read; a NULL argument folds the call to NULL; fewer than
 * two arguments, named arguments, DISTINCT and a window are refused for their shape. Each cell is a live-verified
 * answer.
 */
public class DivTest extends MeasuredCellsTestSupport {

    @Test
    public void callForms() {
        engine.execute("CREATE TABLE t (a INT, s STRING)");
        engine.execute("INSERT INTO t VALUES (10, 'x'), (7, 'y')");
        engine.execute("CREATE VIEW v AS SELECT DIV(a, 3) x FROM t");
        engine.execute("CREATE FUNCTION f(x INT) RETURNS INT AS 'DIV(x, 3)'");
        assertCells(new String[][] {
            {"SELECT DIV()",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for "
                + "function [], expected 2, got 0"},
            {"SELECT DIV(1)",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for "
                + "function [1], expected 2, got 1"},
            {"SELECT DIV(10, 3)",
                "REFUSED Unsupported feature 'DIV'."},
            {"SELECT DIV(10, 3, 1)",
                "REFUSED Unsupported feature 'DIV'."},
            {"SELECT DIV('a', 'b')",
                "REFUSED Unsupported feature 'DIV'."},
            {"SELECT DIV(NULL, NULL)",
                "null"},
            {"SELECT DIV(a, 3) FROM t",
                "REFUSED Unsupported feature 'DIV'."},
            {"SELECT a FROM t WHERE DIV(a, 3) = 3",
                "REFUSED Unsupported feature 'DIV'."},
            {"SELECT div(10, 3)",
                "REFUSED Unsupported feature 'DIV'."},
            {"SELECT \"DIV\"(10, 3)",
                "REFUSED Unsupported feature 'DIV'."},
            {"SELECT \"div\"(10, 3)",
                "REFUSED Unsupported feature 'DIV'."},
            {"SELECT * FROM v",
                "REFUSED Unsupported feature 'DIV'."},
            {"SELECT f(10)",
                "REFUSED Unsupported feature 'DIV'."},
            {"EXECUTE IMMEDIATE $$BEGIN RETURN DIV(10, 3); END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : Unsupported"
                + " feature 'DIV'."},
            {"EXECUTE IMMEDIATE $$BEGIN LET x INT := DIV(10, 3); RETURN x; EXCEPTION WHEN OTHER THEN "
                + "RETURN 'caught ' || SQLCODE || ' ' || SQLSTATE; END$$",
                "caught 2 0A000"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN RETURN DIV(10, 3); END IF; RETURN 1; END$$",
                "1"},
            {"EXECUTE IMMEDIATE $$BEGIN SELECT DIV(10, 3); RETURN 1; EXCEPTION WHEN OTHER THEN RETURN "
                + "'caught ' || SQLCODE || ' ' || SQLSTATE; END$$",
                "caught 2 0A000"},
            {"SELECT DIV(nosuch, 3) FROM t",
                "REFUSED SQL compilation error: error line 1 at position 11 | invalid identifier 'NOSUCH'"},
            {"SELECT CASE(1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '<EOF>'."},
        });
    }

    @Test
    public void operandsAndPlaces() {
        engine.execute("CREATE TABLE t (a INT, s STRING, n INT)");
        engine.execute("INSERT INTO t VALUES (10, 'x', NULL), (7, 'y', NULL)");
        engine.execute("CREATE TABLE div (div INT)");
        engine.execute("CREATE FUNCTION h() RETURNS TABLE (x INT) AS 'SELECT DIV(1, 2)'");
        assertCells(new String[][] {
            {"SELECT DIV(NULL, 3)",
                "null"},
            {"SELECT DIV(3, NULL)",
                "null"},
            {"SELECT DIV(NULL)",
                "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for "
                + "function [null], expected 2, got 1"},
            {"SELECT DIV(NULL, NULL, NULL)",
                "null"},
            {"SELECT DIV(1, NULL, NULL)",
                "null"},
            {"SELECT DIV(n, n) FROM t",
                "REFUSED Unsupported feature 'DIV'."},
            {"SELECT DIV(NULL::INT, 3)",
                "null"},
            {"SELECT DIV(a, 3) FROM t WHERE FALSE",
                "REFUSED Unsupported feature 'DIV'."},
            {"SELECT DIV(a, 3) FROM t LIMIT 0",
                "REFUSED Unsupported feature 'DIV'."},
            {"SELECT CASE WHEN FALSE THEN DIV(1, 2) END",
                "REFUSED Unsupported feature 'DIV'."},
            {"SELECT IFF(a > 100, DIV(a, 3), 0) FROM t",
                "REFUSED Unsupported feature 'DIV'."},
            {"SELECT DIV(a, 3) FROM t WHERE a > 100",
                "REFUSED Unsupported feature 'DIV'."},
            {"SELECT DIV(1, 2, 3, 4)",
                "REFUSED Unsupported feature 'DIV'."},
            {"SELECT DIV(1.5, 2)",
                "REFUSED Unsupported feature 'DIV'."},
            {"SELECT 1 AS div",
                "1"},
            {"SELECT div FROM (SELECT 1 AS div)",
                "1"},
            {"SELECT div FROM div",
                ""},
            {"EXECUTE IMMEDIATE $$BEGIN SELECT DIV(); RETURN 1; END$$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL "
                + "compilation error: error line 1 at position 7 | not enough arguments for function [], "
                + "expected 2, got 0"},
            {"EXECUTE IMMEDIATE $$BEGIN SELECT DIV(); RETURN 1; EXCEPTION WHEN OTHER THEN RETURN 'caught '"
                + " || SQLCODE; END$$",
                "caught 938"},
            {"EXECUTE IMMEDIATE $$BEGIN RETURN DIV(); END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL "
                + "compilation error: error line 1 at position 0 | not enough arguments for function [], "
                + "expected 2, got 0"},
            {"EXECUTE IMMEDIATE $$BEGIN RETURN DIV(1); EXCEPTION WHEN OTHER THEN RETURN 'caught ' || "
                + "SQLCODE; END$$",
                "caught 938"},
            {"EXECUTE IMMEDIATE $$BEGIN RETURN DIV(NULL, NULL); END$$",
                "null"},
            {"EXECUTE IMMEDIATE $$BEGIN SELECT DIV(10, 3); RETURN 1; END$$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Unsupported "
                + "feature 'DIV'."},
            {"CREATE FUNCTION g() RETURNS INT AS 'DIV()'",
                "REFUSED SQL compilation error: error line 1 at position 1 | not enough arguments for "
                + "function [], expected 2, got 0"},
            {"SELECT * FROM TABLE(h())",
                "REFUSED Unsupported feature 'DIV'."},
        });
    }

    @Test
    public void foldsAndOrder() {
        engine.execute("CREATE TABLE t (a INT, s STRING, n INT)");
        engine.execute("INSERT INTO t VALUES (10, 'x', NULL), (7, 'y', NULL)");
        engine.execute("CREATE TABLE t2 (a INT DEFAULT DIV(1, 2))");
        assertCells(new String[][] {
            {"SELECT DIV(1, 2, NULL)",
                "null"},
            {"SELECT DIV(1 + NULL, 2)",
                "null"},
            {"SELECT DIV(CAST(NULL AS INT), 2)",
                "null"},
            {"SELECT DIV(NULLIF(1, 1), 2)",
                "REFUSED Unsupported feature 'DIV'."},
            {"SELECT DIV('NULL', 2)",
                "REFUSED Unsupported feature 'DIV'."},
            {"SELECT DIV(1, 2), nosuchfn(1)",
                "REFUSED SQL compilation error: | Unknown function NOSUCHFN."},
            {"SELECT nosuchfn(1), DIV(1, 2)",
                "REFUSED SQL compilation error: | Unknown function NOSUCHFN."},
            {"SELECT a, DIV(1, 2) FROM t GROUP BY 1, 2",
                "REFUSED Unsupported feature 'DIV'."},
            {"SELECT (SELECT DIV(1, 2))",
                "REFUSED Unsupported feature 'DIV'."},
            {"WITH c AS (SELECT DIV(a, 2) d FROM t) SELECT 1",
                "REFUSED Unsupported feature 'DIV'."},
            {"WITH c AS (SELECT DIV(a, 2) d FROM t) SELECT * FROM c",
                "REFUSED Unsupported feature 'DIV'."},
            {"SELECT a FROM t ORDER BY DIV(a, 2)",
                "REFUSED Unsupported feature 'DIV'."},
            {"SELECT 1 FROM t GROUP BY DIV(a, 2)",
                "REFUSED Unsupported feature 'DIV'."},
            {"SELECT 1 FROM t GROUP BY a HAVING DIV(a, 2) > 0",
                "REFUSED Unsupported feature 'DIV'."},
            {"INSERT INTO t (a) SELECT DIV(1, 2)",
                "REFUSED Unsupported feature 'DIV'."},
            {"UPDATE t SET a = DIV(a, 2)",
                "REFUSED Unsupported feature 'DIV'."},
            {"UPDATE t SET a = DIV(a, 2) WHERE FALSE",
                "REFUSED Unsupported feature 'DIV'."},
            {"DELETE FROM t WHERE DIV(a, 2) = 1",
                "REFUSED Unsupported feature 'DIV'."},
            {"SELECT DIV(x => 1, y => 2)",
                "REFUSED SQL compilation error: error line 1 at position 7 | function DIV does not support "
                + "named arguments"},
            {"SELECT DIV(DISTINCT 1, 2)",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function '1 DIV 2'"},
            {"SELECT DIV(1, 2) OVER ()",
                "REFUSED SQL compilation error: | Invalid function type [DIV] for window function."},
            {"SELECT DIV(*) FROM t",
                "REFUSED Unsupported feature 'DIV'."},
            {"SELECT IDENTIFIER('DIV')(1, 2)",
                "REFUSED Unsupported feature 'DIV'."},
        });
    }

    @Test
    public void shapesAndCodes() {
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$BEGIN RETURN ABS(); EXCEPTION WHEN OTHER THEN RETURN 'caught ' || "
                + "SQLCODE || ' ' || SQLSTATE; END$$",
                "caught 938 22023"},
            {"EXECUTE IMMEDIATE $$BEGIN RETURN ABS(1, 2); EXCEPTION WHEN OTHER THEN RETURN 'caught ' || "
                + "SQLCODE || ' ' || SQLSTATE; END$$",
                "caught 939 22023"},
            {"EXECUTE IMMEDIATE $$BEGIN RETURN DIV(1); EXCEPTION WHEN OTHER THEN RETURN 'caught ' || "
                + "SQLCODE || ' ' || SQLSTATE; END$$",
                "caught 938 22023"},
            {"SELECT ABS(DISTINCT 1)",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'ABS(DISTINCT 1)'"},
            {"SELECT ABS(1) OVER ()",
                "REFUSED SQL compilation error: | Invalid function type [ABS] for window function."},
            {"SELECT DIV(DISTINCT a, 2) FROM (SELECT 1 a)",
                "REFUSED SQL compilation error: | invalid use of 'distinct' for function '\"values\".A DIV 2'"},
            {"SELECT DIV(a, 2) OVER (ORDER BY a) FROM (SELECT 1 a)",
                "REFUSED SQL compilation error: | Invalid function type [DIV] for window function."},
            {"SELECT DIV(1, 2) FROM (SELECT 1 a) QUALIFY TRUE",
                "REFUSED SQL compilation error: error line 1 at position 35 | found QUALIFY clause but no "
                + "window function."},
            {"SELECT \"abs\"(1)",
                "1"},
        });
    }

    @Test
    public void foldsAndBlocks() {
        engine.execute("CREATE TABLE t (a INT, s STRING, n INT)");
        engine.execute("INSERT INTO t VALUES (10, 'x', NULL), (7, 'y', NULL)");
        assertCells(new String[][] {
            {"SELECT DIV(-NULL, 2)",
                "null"},
            {"SELECT DIV((NULL), 2)",
                "null"},
            {"SELECT DIV('a' || NULL, 2)",
                "null"},
            {"SELECT DIV(TRY_CAST(NULL AS INT), 2)",
                "null"},
            {"SELECT DIV(DIV(NULL, 1), 2)",
                "null"},
            {"SELECT DIV(IFF(TRUE, NULL, 1), 2)",
                "REFUSED Unsupported feature 'DIV'."},
            {"EXECUTE IMMEDIATE $$DECLARE v INT; BEGIN RETURN DIV(v, 2); END$$",
                "null"},
            {"EXECUTE IMMEDIATE $$DECLARE v INT DEFAULT 4; BEGIN RETURN DIV(v, 2); EXCEPTION WHEN OTHER "
                + "THEN RETURN 'caught ' || SQLCODE; END$$",
                "caught 2"},
            {"EXECUTE IMMEDIATE $$DECLARE v INT; r INT; BEGIN SELECT DIV(:v, 2) INTO :r; RETURN r; END$$",
                "null"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN SELECT DIV(1, 2); END IF; RETURN 1; END$$",
                "1"},
            {"EXPLAIN SELECT DIV(1, 2)",
                "REFUSED Unsupported feature 'DIV'."},
            {"SELECT COUNT(*) FROM t WHERE DIV(a, NULL) IS NULL",
                "2"},
        });
    }

    @Test
    public void userFunctionAndCtas() {
        engine.execute("CREATE FUNCTION div(x INT, y INT) RETURNS INT AS 'x + y'");
        engine.execute("CREATE TABLE src (a INT)");
        engine.execute("INSERT INTO src VALUES (1)");
        engine.execute("CREATE TABLE t5 AS SELECT DIV(a, NULL) d FROM src");
        assertCells(new String[][] {
            {"SELECT div(1, 2)",
                "REFUSED Unsupported feature 'DIV'."},
            {"SELECT \"DIV\"(1, 2)",
                "REFUSED Unsupported feature 'DIV'."},
            {"CREATE TABLE t3 AS SELECT DIV(a, 2) d FROM src",
                "REFUSED Unsupported feature 'DIV'."},
            {"CREATE TABLE t4 AS SELECT DIV(a, 2) d FROM src WHERE FALSE",
                "REFUSED Unsupported feature 'DIV'."},
            {"SELECT COUNT(*) FROM t5",
                "1"},
        });
    }
}
