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

package dev.frostlake.procedural;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * A CALL of a procedure the CALL defines for itself, {@code (WITH p AS PROCEDURE … CALL p())}, standing as a value: in a
 * select list, a WHERE, a CASE, a view's body, a RETURN, an IF and under an operator, the value being the wrapper the
 * answer travels in, and a statement holding one answering the procedure's answer as its one value; a CALL after a
 * WITH of common table expressions only is refused by the name it calls (live-verified).
 */
public class AnonymousProcedureValueTest extends MeasuredCellsTestSupport {

    @Test
    public void valueForms() {
        engine.execute("CREATE OR REPLACE PROCEDURE realp() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 3; END'");
        engine.execute("CREATE OR REPLACE TABLE t1 (a VARCHAR)");
        engine.execute("CREATE OR REPLACE VIEW vv AS SELECT (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS "
            + "'BEGIN RETURN 7; END' CALL p()) AS a");
        assertCells(new String[][] {
            {"SELECT (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END', c AS "
                + "(SELECT 1) CALL p())",
                "7"},
            {"SELECT (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END' CALL p())",
                "7"},
            {"SELECT (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END' CALL p()) "
                + "AS v",
                "7"},
            {"SELECT 1 + (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END' CALL "
                + "p())",
                "REFUSED Numeric value "
                + "'{\"type\":\"SCALAR\",\"scalarValue\":\"7\",\"scalarDataType\":\"NUMBER\",\"scalarScale\":0,\"scalarPrecision\":38}'"
                + " is not recognized"},
            {"SELECT (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END' CALL p()) "
                + "|| 'x'",
                "7"},
            {"SELECT ((WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END' CALL p()))",
                "7"},
            {"SELECT (WITH p AS PROCEDURE () RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN ''ab''; END' "
                + "CALL p())",
                "ab"},
            {"SELECT (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END' CALL p()) "
                + "FROM (VALUES (1), (2)) v(c)",
                "7"},
            {"SELECT (WITH p AS PROCEDURE (a INT) RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN a; END' CALL "
                + "p(5))",
                "5"},
            {"SELECT (WITH p AS PROCEDURE (a INT) RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN a; END' CALL "
                + "p(c)) FROM (VALUES (4)) v(c)",
                "4"},
            {"SELECT (WITH p AS PROCEDURE (a INT) RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN a; END' CALL "
                + "p(c + 1)) FROM (VALUES (4)) v(c)",
                "5"},
            {"SELECT (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END' CALL p()) ="
                + " 7",
                "REFUSED Numeric value "
                + "'{\"type\":\"SCALAR\",\"scalarValue\":\"7\",\"scalarDataType\":\"NUMBER\",\"scalarScale\":0,\"scalarPrecision\":38}'"
                + " is not recognized"},
            {"SELECT (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END' CALL "
                + "p())::INT + 1",
                "REFUSED Numeric value "
                + "'{\"type\":\"SCALAR\",\"scalarValue\":\"7\",\"scalarDataType\":\"NUMBER\",\"scalarScale\":0,\"scalarPrecision\":38}'"
                + " is not recognized"},
            {"SELECT 'y' WHERE (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END' "
                + "CALL p()) = 7",
                "REFUSED Numeric value "
                + "'{\"type\":\"SCALAR\",\"scalarValue\":\"7\",\"scalarDataType\":\"NUMBER\",\"scalarScale\":0,\"scalarPrecision\":38}'"
                + " is not recognized"},
            {"SELECT CASE WHEN TRUE THEN (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN"
                + " 7; END' CALL p()) END",
                "7"},
            {"SELECT CASE (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END' CALL "
                + "p()) WHEN 7 THEN 'y' ELSE 'n' END",
                "REFUSED Numeric value "
                + "'{\"type\":\"SCALAR\",\"scalarValue\":\"7\",\"scalarDataType\":\"NUMBER\",\"scalarScale\":0,\"scalarPrecision\":38}'"
                + " is not recognized"},
            {"SELECT a FROM t1",
                ""},
            {"SELECT * FROM vv",
                "7"},
            {"SELECT (WITH p AS PROCEDURE () RETURNS TABLE (a INT) LANGUAGE SQL AS 'DECLARE r RESULTSET "
                + "DEFAULT (SELECT 1 a); BEGIN RETURN TABLE(r); END' CALL p())",
                "1"},
            {"WITH p AS PROCEDURE () RETURNS TABLE (a INT) LANGUAGE SQL AS 'DECLARE r RESULTSET DEFAULT "
                + "(SELECT 1 a); BEGIN RETURN TABLE(r); END' CALL p()",
                "1"},
            {"WITH c AS (SELECT 1) CALL p7()",
                "REFUSED SQL compilation error: | Unknown function P7."},
            {"SELECT (WITH c AS (SELECT 1) CALL p7())",
                "REFUSED SQL compilation error: | Unknown function P7."},
            {"WITH c AS (SELECT 1) CALL realp()",
                "REFUSED SQL compilation error: | Unknown function REALP."},
            {"SELECT (WITH c AS (SELECT 1) CALL realp())",
                "REFUSED SQL compilation error: | Unknown function REALP."},
            {"SELECT (CALL realp())",
                "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'realp'. | "
                + "syntax error line 1 at position 18 unexpected '('."},
            {"SELECT WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END' CALL p()",
                "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected 'WITH'."},
            {"SELECT (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END' CALL p() "
                + "CALL p())",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'WITH'. | "
                + "syntax error line 1 at position 15 unexpected 'AS'."},
            {"WITH c AS (SELECT 1) CALL s.p7()",
                "REFUSED SQL compilation error: | Unknown user-defined function S.P7."},
            {"BEGIN RETURN (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END' CALL "
                + "p()); END",
                "7"},
            {"BEGIN LET x := 1 + (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END'"
                + " CALL p()); RETURN x; END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 15 : Numeric "
                + "value "
                + "'{\"type\":\"SCALAR\",\"scalarValue\":\"7\",\"scalarDataType\":\"NUMBER\",\"scalarScale\":0,\"scalarPrecision\":38}'"
                + " is not recognized"},
            {"BEGIN LET x INT := 1 + (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; "
                + "END' CALL p()); RETURN x; END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 19 : Numeric "
                + "value "
                + "'{\"type\":\"SCALAR\",\"scalarValue\":\"7\",\"scalarDataType\":\"NUMBER\",\"scalarScale\":0,\"scalarPrecision\":38}'"
                + " is not recognized"},
            {"BEGIN IF ((WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END' CALL "
                + "p()) = 7) THEN RETURN 'y'; END IF; RETURN 'n'; END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 10 : Numeric "
                + "value "
                + "'{\"type\":\"SCALAR\",\"scalarValue\":\"7\",\"scalarDataType\":\"NUMBER\",\"scalarScale\":0,\"scalarPrecision\":38}'"
                + " is not recognized"},
            {"BEGIN LET y := 4; RETURN (WITH p AS PROCEDURE (a INT) RETURNS INT LANGUAGE SQL AS 'BEGIN "
                + "RETURN a; END' CALL p(:y)); END",
                "4"},
            {"BEGIN LET x := (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END' "
                + "CALL p()) || 'z'; RETURN x; END",
                "7"},
            {"BEGIN RETURN (WITH c AS (SELECT 1) CALL p7()); END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL "
                + "compilation error: | Unknown function P7."},
            {"BEGIN LET x := (WITH c AS (SELECT 1) CALL p7()); RETURN x; END",
                "REFUSED SQL compilation error: | Unknown function P7."},
            {"BEGIN WITH c AS (SELECT 1) CALL p7(); RETURN 1; END",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : SQL "
                + "compilation error: | Unknown function P7."},
            {"DECLARE x INT; BEGIN x := 1 + (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN "
                + "RETURN 7; END' CALL p()); RETURN x; END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : Numeric "
                + "value "
                + "'{\"type\":\"SCALAR\",\"scalarValue\":\"7\",\"scalarDataType\":\"NUMBER\",\"scalarScale\":0,\"scalarPrecision\":38}'"
                + " is not recognized"},
            {"BEGIN RETURN (WITH p AS PROCEDURE () RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN ''ab''; "
                + "END' CALL p()); END",
                "ab"},
            {"SELECT (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END' CALL p()) "
                + "|| 'x', 2",
                "7"},
            {"SELECT CONCAT((WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END' CALL"
                + " p()), 'x')",
                "7"},
            {"SELECT TO_VARCHAR((WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END' "
                + "CALL p()))",
                "7"},
            {"SELECT (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END' CALL "
                + "p())::VARCHAR",
                "7"},
            {"SELECT COALESCE((WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END' "
                + "CALL p()), 'q')",
                "7"},
            {"SELECT IFF(TRUE, (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END' "
                + "CALL p()), 'q')",
                "7"},
            {"SELECT (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN NULL; END' CALL "
                + "p())",
                "null"},
            {"SELECT (WITH p AS PROCEDURE () RETURNS DATE LANGUAGE SQL AS 'BEGIN RETURN "
                + "''2024-01-02''::DATE; END' CALL p())",
                "2024-01-02"},
            {"SELECT (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END' CALL p()) "
                + "FROM (VALUES (1)) v(c) WHERE c = 1",
                "7"},
            {"SELECT (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END' CALL p()) "
                + "AS a ORDER BY a",
                "7"},
            {"SELECT a || '!' FROM (SELECT (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN "
                + "RETURN 7; END' CALL p()) AS a)",
                "7"},
            {"SELECT (WITH p AS PROCEDURE () RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN ''ab''; END' "
                + "CALL p()) || 'tail' AS c",
                "ab"},
            {"SELECT (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END' CALL p()) "
                + "|| 'x' || 'y'",
                "7"},
            {"BEGIN LET x := (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END' "
                + "CALL p()) || 'z'; RETURN LENGTH(x); END",
                "1"},
            {"BEGIN RETURN (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END' CALL "
                + "p()) || 'q'; END",
                "7"},
            {"BEGIN RETURN (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN NULL; END' "
                + "CALL p()); END",
                "null"},
            {"BEGIN RETURN (WITH p AS PROCEDURE () RETURNS DATE LANGUAGE SQL AS 'BEGIN RETURN "
                + "''2024-01-02''::DATE; END' CALL p()); END",
                "2024-01-02"},
            {"BEGIN LET x := (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END' "
                + "CALL p()); RETURN x || 'z'; END",
                "7z"},
        });
    }
}
