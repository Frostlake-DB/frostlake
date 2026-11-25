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

package dev.frostlake.scripting;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * The refusals a block's calls, windows and CREATE TABLE comments earn by their shape alone: a name written twice
 * among a call's named arguments, a positional argument after a named one, a window frame or ordering its function
 * does not take, and a table's comment written twice. Inside a block each is refused while the block compiles, on a
 * branch that never runs, in a handler and in a nested block too, so the block's own handler never catches it, and
 * it is placed in the block's text; a procedure's body is refused so at CREATE. Measured on a live account.
 */
public class BlockCompiledShapesTest extends MeasuredCellsTestSupport {

    @Test
    public void inBlocks() {
        engine.execute("CREATE TABLE t (x INT, y INT)");
        engine.execute("INSERT INTO t VALUES (1,2),(2,3),(3,5)");
        engine.execute("CREATE FUNCTION f(a INT, b INT) RETURNS INT AS 'a+b'");
        assertCells(new String[][] {
            {"SELECT f(a=>1, a=>2)",
                "REFUSED SQL compilation error: error line 1 at position 18 | duplicate named argument A for "
                + "function F"},
            {"SELECT f(a=>1, 2)",
                "REFUSED SQL compilation error: error line 1 at position 15 | positional argument cannot "
                + "follow named argument in function call 'F'"},
            {"SELECT * FROM TABLE(FLATTEN(INPUT => [1], 'a'))",
                ""},
            {"CREATE TABLE d (x INT) COMMENT='a' COMMENT='b'",
                "REFUSED SQL compilation error: | duplicate property 'COMMENT';"},
            {"SELECT CORR(y,x) OVER (ORDER BY y ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) FROM t",
                "REFUSED SQL compilation error: error line 1 at position 34 | Sliding window frame "
                + "unsupported for function CORR"},
            {"SELECT SUM(DISTINCT y) OVER (ORDER BY y) FROM t",
                "REFUSED SQL compilation error: error line 1 at position 23 | distinct cannot be used with a "
                + "window frame or an order."},
            {"SELECT MEDIAN(y) OVER (ORDER BY y) FROM t",
                "REFUSED SQL compilation error: error line 1 at position 17 | Cumulative window frame "
                + "unsupported for function MEDIAN"},
            {"SELECT ARRAY_AGG(y) WITHIN GROUP (ORDER BY y) OVER (ORDER BY y) FROM t",
                "REFUSED SQL compilation error: error line 1 at position 46 | WITHIN GROUP clause is not "
                + "supported when the OVER clause contains an ORDER BY clause."},
            {"SELECT g(a=>1, a=>2)",
                "REFUSED SQL compilation error: error line 1 at position 18 | duplicate named argument A for "
                + "function G"},
            {"EXECUTE IMMEDIATE $$BEGIN LET r INT := (SELECT f(a=>1, a=>2)); RETURN r; EXCEPTION WHEN "
                + "OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 38 | duplicate named argument A for "
                + "function F"},
            {"EXECUTE IMMEDIATE $$BEGIN LET r INT := (SELECT f(a=>1, 2)); RETURN r; EXCEPTION WHEN OTHER "
                + "THEN RETURN SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 35 | positional argument cannot "
                + "follow named argument in function call 'F'"},
            {"EXECUTE IMMEDIATE $$BEGIN SELECT COUNT(*) INTO :r FROM TABLE(FLATTEN(INPUT => [1], INPUT => "
                + "[2])); RETURN 'ok'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || ' ' || "
                + "SQLERRM; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 28 | invalid identifier 'R'"},
            {"EXECUTE IMMEDIATE $$DECLARE r INT; BEGIN SELECT COUNT(*) INTO :r FROM TABLE(FLATTEN(INPUT =>"
                + " [1], 'a')); RETURN 'ok'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || ' '"
                + " || SQLERRM; END;$$",
                "ok"},
            {"EXECUTE IMMEDIATE $$BEGIN CREATE TABLE d (x INT) COMMENT='a' COMMENT='b'; RETURN 'ok'; "
                + "EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END;$$",
                "REFUSED SQL compilation error: | duplicate property 'COMMENT';"},
            {"EXECUTE IMMEDIATE $$BEGIN LET r VARIANT := (SELECT CORR(y,x) OVER (ORDER BY y ROWS BETWEEN 1"
                + " PRECEDING AND CURRENT ROW) FROM t LIMIT 1); RETURN r; EXCEPTION WHEN OTHER THEN RETURN "
                + "SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 58 | Sliding window frame "
                + "unsupported for function CORR"},
            {"EXECUTE IMMEDIATE $$DECLARE r INT; BEGIN SELECT SUM(DISTINCT y) OVER (ORDER BY y) INTO :r "
                + "FROM t LIMIT 1; RETURN r; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || ' '"
                + " || SQLERRM; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 44 | distinct cannot be used with a "
                + "window frame or an order."},
            {"EXECUTE IMMEDIATE $$DECLARE r INT; BEGIN SELECT MEDIAN(y) OVER (ORDER BY y) INTO :r FROM t "
                + "LIMIT 1; RETURN r; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || ' ' || "
                + "SQLERRM; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 38 | Cumulative window frame "
                + "unsupported for function MEDIAN"},
            {"EXECUTE IMMEDIATE $$DECLARE r INT; BEGIN SELECT RANK() OVER (ORDER BY y ROWS BETWEEN 1 "
                + "PRECEDING AND CURRENT ROW) INTO :r FROM t LIMIT 1; RETURN r; EXCEPTION WHEN OTHER THEN "
                + "RETURN SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END;$$",
                "1"},
            {"EXECUTE IMMEDIATE $$DECLARE r VARIANT; BEGIN SELECT ARRAY_AGG(y) WITHIN GROUP (ORDER BY y) "
                + "OVER (ORDER BY y) INTO :r FROM t LIMIT 1; RETURN r; EXCEPTION WHEN OTHER THEN RETURN SQLCODE"
                + " || ' ' || SQLSTATE || ' ' || SQLERRM; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 71 | WITHIN GROUP clause is not "
                + "supported when the OVER clause contains an ORDER BY clause."},
            {"EXECUTE IMMEDIATE $$BEGIN LET r INT := (SELECT g(a=>1, a=>2)); RETURN r; EXCEPTION WHEN "
                + "OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE || ' ' || SQLERRM; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 38 | duplicate named argument A for "
                + "function G"},
            {"EXECUTE IMMEDIATE $$BEGIN LET r VARIANT := (SELECT CORR(y,x) OVER (ORDER BY y ROWS UNBOUNDED"
                + " PRECEDING) FROM t LIMIT 1); RETURN r; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || "
                + "SQLSTATE || ' ' || SQLERRM; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 58 | Cumulative window frame "
                + "unsupported for function CORR"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN LET r INT := (SELECT f(a=>1, a=>2)); END IF; "
                + "RETURN 'ok'; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 54 | duplicate named argument A for "
                + "function F"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN LET r INT := (SELECT f(a=>1, 2)); END IF; RETURN "
                + "'ok'; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 51 | positional argument cannot "
                + "follow named argument in function call 'F'"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN CREATE TABLE d (x INT) COMMENT='a' COMMENT='b'; "
                + "END IF; RETURN 'ok'; END;$$",
                "REFUSED SQL compilation error: | duplicate property 'COMMENT';"},
            {"EXECUTE IMMEDIATE $$DECLARE r INT; BEGIN IF (FALSE) THEN SELECT MEDIAN(y) OVER (ORDER BY y) "
                + "INTO :r FROM t; END IF; RETURN 'ok'; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 54 | Cumulative window frame "
                + "unsupported for function MEDIAN"},
            {"EXECUTE IMMEDIATE $$DECLARE r INT; BEGIN IF (FALSE) THEN SELECT SUM(DISTINCT y) OVER (ORDER "
                + "BY y) INTO :r FROM t; END IF; RETURN 'ok'; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 60 | distinct cannot be used with a "
                + "window frame or an order."},
            {"EXECUTE IMMEDIATE $$DECLARE r INT; BEGIN IF (FALSE) THEN SELECT RANK() OVER (ORDER BY y ROWS"
                + " BETWEEN 1 PRECEDING AND CURRENT ROW) INTO :r FROM t; END IF; RETURN 'ok'; END;$$",
                "ok"},
            {"EXECUTE IMMEDIATE $$DECLARE r VARIANT; BEGIN IF (FALSE) THEN SELECT ARRAY_AGG(y) WITHIN "
                + "GROUP (ORDER BY y) OVER (ORDER BY y) INTO :r FROM t; END IF; RETURN 'ok'; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 87 | WITHIN GROUP clause is not "
                + "supported when the OVER clause contains an ORDER BY clause."},
            {"EXECUTE IMMEDIATE $$DECLARE r VARIANT; BEGIN IF (FALSE) THEN SELECT CORR(y,x) OVER (ORDER BY"
                + " y ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) INTO :r FROM t; END IF; RETURN 'ok'; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 75 | Sliding window frame "
                + "unsupported for function CORR"},
            {"EXECUTE IMMEDIATE $$DECLARE r INT; BEGIN IF (FALSE) THEN SELECT COUNT(*) INTO :r FROM "
                + "TABLE(FLATTEN(INPUT => [1], INPUT => [2])); END IF; RETURN 'ok'; END;$$",
                "ok"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN LET r INT := (SELECT g(a=>1, a=>2)); END IF; "
                + "RETURN 'ok'; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 54 | duplicate named argument A for "
                + "function G"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN RETURN (SELECT MEDIAN(y) OVER (ORDER BY y) FROM t "
                + "LIMIT 1); END IF; RETURN 'ok'; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 47 | Cumulative window frame "
                + "unsupported for function MEDIAN"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN INSERT INTO t SELECT MEDIAN(y) OVER (ORDER BY y), "
                + "1 FROM t; END IF; RETURN 'ok'; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 53 | Cumulative window frame "
                + "unsupported for function MEDIAN"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN LET c CURSOR FOR SELECT MEDIAN(y) OVER (ORDER BY "
                + "y) FROM t; END IF; RETURN 'ok'; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 56 | Cumulative window frame "
                + "unsupported for function MEDIAN"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN EXECUTE IMMEDIATE 'SELECT MEDIAN(y) OVER (ORDER BY"
                + " y) FROM t'; END IF; RETURN 'ok'; END;$$",
                "ok"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN LET rs RESULTSET := (SELECT MEDIAN(y) OVER (ORDER "
                + "BY y) FROM t); END IF; RETURN 'ok'; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 60 | Cumulative window frame "
                + "unsupported for function MEDIAN"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN UPDATE t SET x = f(a=>1, a=>2); END IF; RETURN "
                + "'ok'; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 50 | duplicate named argument A for "
                + "function F"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN ALTER TABLE t SET COMMENT='a' COMMENT='b'; END IF;"
                + " RETURN 'ok'; END;$$",
                "ok"},
            {"EXECUTE IMMEDIATE $$BEGIN\n  IF (FALSE) THEN\n    LET r INT := (SELECT f(a=>1, a=>2));\n  "
                + "END IF;\n  RETURN 'ok';\nEND;$$",
                "REFUSED SQL compilation error: error line 3 at position 36 | duplicate named argument A for "
                + "function F"},
            {"EXECUTE IMMEDIATE $$DECLARE r INT;\nBEGIN\n  IF (FALSE) THEN\n    SELECT MEDIAN(y) OVER "
                + "(ORDER BY y) INTO :r FROM t;\n  END IF;\n  RETURN 'ok';\nEND;$$",
                "REFUSED SQL compilation error: error line 4 at position 21 | Cumulative window frame "
                + "unsupported for function MEDIAN"},
            {"CREATE PROCEDURE p1() RETURNS VARCHAR LANGUAGE SQL AS $$BEGIN IF (FALSE) THEN LET r INT := "
                + "(SELECT f(a=>1, a=>2)); END IF; RETURN 'ok'; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 54 | duplicate named argument A for "
                + "function F"},
            {"CALL p1()",
                "REFUSED SQL compilation error: | Unknown function P1."},
            {"CREATE PROCEDURE p2() RETURNS VARCHAR LANGUAGE SQL AS $$DECLARE r INT; BEGIN IF (FALSE) THEN"
                + " SELECT MEDIAN(y) OVER (ORDER BY y) INTO :r FROM t; END IF; RETURN 'ok'; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 54 | Cumulative window frame "
                + "unsupported for function MEDIAN"},
            {"CALL p2()",
                "REFUSED SQL compilation error: | Unknown function P2."},
            {"CREATE PROCEDURE p3() RETURNS VARCHAR LANGUAGE SQL AS $$BEGIN CREATE TABLE d (x INT) "
                + "COMMENT='a' COMMENT='b'; RETURN 'ok'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || "
                + "SQLERRM; END;$$",
                "REFUSED SQL compilation error: | duplicate property 'COMMENT';"},
            {"CALL p3()",
                "REFUSED SQL compilation error: | Unknown function P3."},
        });
    }

    @Test
    public void namedArgumentsAndOrder() {
        engine.execute("CREATE TABLE t (x INT, y INT)");
        engine.execute("INSERT INTO t VALUES (1,2),(2,3),(3,5)");
        engine.execute("CREATE FUNCTION f(a INT, b INT) RETURNS INT AS 'a+b'");
        assertCells(new String[][] {
            {"SELECT UPPER(x=>'a', x=>'b')",
                "REFUSED SQL compilation error: error line 1 at position 24 | duplicate named argument X for "
                + "function UPPER"},
            {"SELECT SYSTEM$TYPEOF(a=>1, a=>2)",
                "REFUSED SQL compilation error: error line 1 at position 30 | duplicate named argument A for "
                + "function SYSTEM$TYPEOF"},
            {"SELECT f(a=>1, A=>2)",
                "REFUSED SQL compilation error: error line 1 at position 18 | duplicate named argument A for "
                + "function F"},
            {"SELECT f(a=>1, \"a\"=>2)",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [A, a] do not "
                + "match any signature for function F"},
            {"SELECT f(a=>f(b=>1, b=>2), a=>2)",
                "REFUSED SQL compilation error: error line 1 at position 23 | duplicate named argument B for "
                + "function F"},
            {"SELECT f(a=>1, a=>2, 3)",
                "REFUSED SQL compilation error: error line 1 at position 18 | duplicate named argument A for "
                + "function F"},
            {"SELECT * FROM TABLE(f(a=>1, a=>2))",
                "REFUSED SQL compilation error: | Unknown table function F"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN LET r VARCHAR := (SELECT UPPER(x=>'a', x=>'b')); "
                + "END IF; RETURN 'ok'; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 64 | duplicate named argument X for "
                + "function UPPER"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN LET r INT := f(a=>1, a=>2); END IF; RETURN 'ok'; "
                + "END;$$",
                "REFUSED SQL compilation error: error line 1 at position 46 | duplicate named argument A for "
                + "function F"},
            {"EXECUTE IMMEDIATE $$DECLARE r INT DEFAULT f(a=>1, a=>2); BEGIN RETURN 'ok'; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 33 | duplicate named argument A for "
                + "function F"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN RETURN f(a=>1, a=>2); END IF; RETURN 'ok'; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 40 | duplicate named argument A for "
                + "function F"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN LET r INT := (SELECT f(a=>1, a=>2, 3)); END IF; "
                + "RETURN 'ok'; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 54 | duplicate named argument A for "
                + "function F"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN LET r INT := (SELECT f(a=>f(b=>1, b=>2), a=>2)); "
                + "END IF; RETURN 'ok'; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 59 | duplicate named argument B for "
                + "function F"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN LET r INT := (SELECT COUNT(*) FROM TABLE(f(a=>1, "
                + "a=>2))); END IF; RETURN 'ok'; END;$$",
                "ok"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN LET r INT := (SELECT SYSTEM$TYPEOF(a=>1, a=>2)); "
                + "END IF; RETURN 'ok'; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 66 | duplicate named argument A for "
                + "function SYSTEM$TYPEOF"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN RETURN missing; END IF; IF (FALSE) THEN LET r INT "
                + ":= (SELECT f(a=>1, a=>2)); END IF; RETURN 'ok'; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 94 | duplicate named argument A for "
                + "function F"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN LET r INT := (SELECT f(a=>1, a=>2)); END IF; "
                + "RETURN missing; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 54 | duplicate named argument A for "
                + "function F"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN RETURN missing; END IF; IF (FALSE) THEN LET r "
                + "VARIANT := (SELECT MEDIAN(y) OVER (ORDER BY y) FROM t); END IF; RETURN 'ok'; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 97 | Cumulative window frame "
                + "unsupported for function MEDIAN"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN LET r VARIANT := (SELECT MEDIAN(y) OVER (ORDER BY "
                + "y) FROM t); END IF; RETURN missing; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 57 | Cumulative window frame "
                + "unsupported for function MEDIAN"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN LET r VARIANT := (SELECT MEDIAN(y) OVER (ORDER BY "
                + "y) FROM t); END IF; IF (FALSE) THEN LET q INT := (SELECT f(a=>1, a=>2)); END IF; RETURN "
                + "'ok'; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 57 | Cumulative window frame "
                + "unsupported for function MEDIAN"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN LET q INT := (SELECT f(a=>1, a=>2)); END IF; IF "
                + "(FALSE) THEN LET r VARIANT := (SELECT MEDIAN(y) OVER (ORDER BY y) FROM t); END IF; RETURN "
                + "'ok'; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 54 | duplicate named argument A for "
                + "function F"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN CREATE TABLE d (x INT) COMMENT='a' COMMENT='b'; "
                + "END IF; IF (FALSE) THEN LET q INT := (SELECT f(a=>1, a=>2)); END IF; RETURN 'ok'; END;$$",
                "REFUSED SQL compilation error: | duplicate property 'COMMENT';"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN LET q INT := (SELECT f(a=>1, a=>2)); END IF; IF "
                + "(FALSE) THEN CREATE TABLE d (x INT) COMMENT='a' COMMENT='b'; END IF; RETURN 'ok'; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 54 | duplicate named argument A for "
                + "function F"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN LET r VARIANT := (SELECT MEDIAN(y) OVER (ORDER BY "
                + "y) FROM nosuch); END IF; RETURN 'ok'; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 57 | Cumulative window frame "
                + "unsupported for function MEDIAN"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN RETURN missing; END IF; IF (FALSE) THEN CREATE "
                + "TABLE d (x INT) COMMENT='a' COMMENT='b'; END IF; RETURN 'ok'; END;$$",
                "REFUSED SQL compilation error: | duplicate property 'COMMENT';"},
            {"SELECT SUM(y) OVER (ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) FROM t",
                "REFUSED SQL compilation error: error line 1 at position 14 | Window frame requires an ORDER "
                + "BY clause."},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN LET r VARIANT := (SELECT SUM(y) OVER (ROWS BETWEEN"
                + " 1 PRECEDING AND CURRENT ROW) FROM t); END IF; RETURN 'ok'; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 54 | Window frame requires an ORDER "
                + "BY clause."},
            {"SELECT LAG(y) OVER (PARTITION BY x) FROM t",
                "REFUSED SQL compilation error: | Window function type [LAG] requires ORDER BY in window "
                + "specification."},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN LET r VARIANT := (SELECT LAG(y) OVER (PARTITION BY"
                + " x) FROM t); END IF; RETURN 'ok'; END;$$",
                "ok"},
            {"SELECT ROW_NUMBER() OVER (ORDER BY y ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) FROM t",
                "REFUSED SQL compilation error: error line 1 at position 37 | Sliding window frame "
                + "unsupported for function ROW_NUMBER"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN LET r VARIANT := (SELECT ROW_NUMBER() OVER (ORDER "
                + "BY y ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) FROM t); END IF; RETURN 'ok'; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 77 | Sliding window frame "
                + "unsupported for function ROW_NUMBER"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN LET r VARIANT := (SELECT MEDIAN(y) OVER (ORDER BY "
                + "y) FROM t); END IF; RETURN 'ok'; EXCEPTION WHEN OTHER THEN LET z VARIANT := (SELECT "
                + "MEDIAN(y) OVER (ORDER BY x) FROM t); RETURN 'h'; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 57 | Cumulative window frame "
                + "unsupported for function MEDIAN"},
            {"EXECUTE IMMEDIATE $$BEGIN RETURN 'ok'; EXCEPTION WHEN OTHER THEN LET z VARIANT := (SELECT "
                + "MEDIAN(y) OVER (ORDER BY x) FROM t); RETURN 'h'; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 80 | Cumulative window frame "
                + "unsupported for function MEDIAN"},
            {"EXECUTE IMMEDIATE $$BEGIN BEGIN LET z VARIANT := (SELECT MEDIAN(y) OVER (ORDER BY x) FROM "
                + "t); END; RETURN 'ok'; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 47 | Cumulative window frame "
                + "unsupported for function MEDIAN"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN LET r VARIANT := (SELECT 1 FROM t QUALIFY "
                + "MEDIAN(y) OVER (ORDER BY y) > 1); END IF; RETURN 'ok'; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 74 | Cumulative window frame "
                + "unsupported for function MEDIAN"},
            {"CREATE VIEW dv COMMENT='a' COMMENT='b' AS SELECT 1 x",
                "REFUSED SQL compilation error: | duplicate property 'COMMENT';"},
            {"ALTER SESSION SET TIMEZONE='UTC' TIMEZONE='UTC'",
                "REFUSED SQL compilation error: | duplicate property 'TIMEZONE';"},
            {"CREATE TABLE dt (x INT) DATA_RETENTION_TIME_IN_DAYS=1 DATA_RETENTION_TIME_IN_DAYS=1",
                "REFUSED SQL compilation error: | duplicate property 'DATA_RETENTION_TIME_IN_DAYS';"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN CREATE OR REPLACE TABLE d COMMENT='a' COMMENT='b' "
                + "AS SELECT 1 x; END IF; RETURN 'ok'; END;$$",
                "REFUSED SQL compilation error: | duplicate property 'COMMENT';"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN CREATE TABLE d COMMENT='a' COMMENT='b' (x INT); "
                + "END IF; RETURN 'ok'; END;$$",
                "REFUSED SQL compilation error: | duplicate property 'COMMENT';"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN CREATE STAGE dst COMMENT='a' COMMENT='b'; END IF; "
                + "RETURN 'ok'; END;$$",
                "ok"},
        });
    }

    @Test
    public void udfsAndProcedures() {
        engine.execute("CREATE TABLE t (x INT, y INT)");
        engine.execute("INSERT INTO t VALUES (1,2),(2,3),(3,5)");
        engine.execute("CREATE FUNCTION f(a INT, b INT) RETURNS INT AS 'a+b'");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM t, LATERAL FLATTEN(INPUT=>[1], INPUT=>[2])",
                "3"},
            {"SELECT COUNT(*) FROM t, LATERAL FLATTEN(INPUT=>[1], 'a')",
                "0"},
            {"SELECT * FROM TABLE(GENERATOR(ROWCOUNT=>1, ROWCOUNT=>2))",
                "REFUSED SQL compilation error: error line 1 at position 0 | SELECT with no columns"},
            {"CREATE FUNCTION uf() RETURNS INT AS 'f(a=>1, a=>2)'",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: error line 1 at position 12 | "
                + "duplicate named argument A for function F"},
            {"CREATE FUNCTION uf3() RETURNS INT AS $$ BEGIN IF (FALSE) THEN RETURN f(a=>1, a=>2); END IF; "
                + "RETURN 1; END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 8 unexpected 'IF'. | syntax error line 1 at position 12 unexpected 'FALSE'."},
            {"CREATE OR REPLACE TABLE d COMMENT='a' COMMENT='b' AS SELECT 1 x",
                "REFUSED SQL compilation error: | duplicate property 'COMMENT';"},
            {"CREATE TABLE d COMMENT='a' COMMENT='b' (x INT)",
                "REFUSED SQL compilation error: | duplicate property 'COMMENT';"},
            {"CREATE TABLE d (x INT) COMMENT='a' COMMENT='b' AS SELECT 1",
                "REFUSED SQL compilation error: | duplicate property 'COMMENT';"},
            {"CREATE TABLE d CLONE t COMMENT='a' COMMENT='b'",
                "REFUSED SQL compilation error: | duplicate property 'COMMENT';"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN CREATE TABLE d CLONE t COMMENT='a' COMMENT='b'; "
                + "END IF; RETURN 'ok'; END;$$",
                "REFUSED SQL compilation error: | duplicate property 'COMMENT';"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN CREATE TABLE d (x INT) COMMENT='a' COMMENT='b' AS "
                + "SELECT 1; END IF; RETURN 'ok'; END;$$",
                "REFUSED SQL compilation error: | duplicate property 'COMMENT';"},
            {"EXECUTE IMMEDIATE $$DECLARE a INT; a INT; BEGIN IF (FALSE) THEN LET q INT := (SELECT f(a=>1,"
                + " a=>2)); END IF; RETURN 'ok'; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 15 |  Variable with name 'A' "
                + "declared twice."},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN LET q INT := (SELECT f(a=>1, a=>2) FROM nosuch); "
                + "END IF; RETURN 'ok'; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 54 | duplicate named argument A for "
                + "function F"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN LET q VARIANT := (SELECT MEDIAN(y) OVER (ORDER BY "
                + "y) FROM t MATCH_RECOGNIZE (ORDER BY x MEASURES 1 AS m PATTERN (a) DEFINE a AS TRUE)); END "
                + "IF; RETURN 'ok'; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 57 | Cumulative window frame "
                + "unsupported for function MEDIAN"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN LET q VARIANT := (SELECT SUM(y) OVER (ORDER BY y "
                + "ROWS BETWEEN 2000 PRECEDING AND CURRENT ROW) FROM t); END IF; RETURN 'ok'; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 71 | Unsupported window frame: "
                + "window frame size of 2001 rows exceeds limit (1000 rows)"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN LET q INT := (WITH c AS (SELECT f(a=>1, a=>2) z) "
                + "SELECT MEDIAN(y) OVER (ORDER BY y) FROM t); END IF; RETURN 'ok'; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 88 | Cumulative window frame "
                + "unsupported for function MEDIAN"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN LET q INT := (SELECT f(a=>1, a=>2, 3)); END IF; "
                + "RETURN 'ok'; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 54 | duplicate named argument A for "
                + "function F"},
            {"EXECUTE IMMEDIATE $$BEGIN IF (FALSE) THEN LET q INT := (SELECT f(a=>1, 3, a=>2)); END IF; "
                + "RETURN 'ok'; END;$$",
                "REFUSED SQL compilation error: error line 1 at position 57 | duplicate named argument A for "
                + "function F"},
            {"SELECT f(a=>1, 3, a=>2)",
                "REFUSED SQL compilation error: error line 1 at position 21 | duplicate named argument A for "
                + "function F"},
        });
    }
}
