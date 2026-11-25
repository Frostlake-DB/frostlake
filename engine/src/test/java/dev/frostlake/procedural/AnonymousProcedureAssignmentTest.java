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
 * A variable's value given by a CALL of a procedure the CALL defines for itself, {@code LET x := (WITH p AS PROCEDURE
 * … CALL p())}, in a LET, a DECLARE section and an assignment: a variable without a type or of a text type takes the
 * procedure's answer as a text, any other type converts the answer's wrapper and is refused in that conversion's words,
 * and a CALL naming another routine names a function no one defined (live-verified).
 */
public class AnonymousProcedureAssignmentTest extends MeasuredCellsTestSupport {

    @Test
    public void typedAndUntyped() {
        engine.execute("CREATE OR REPLACE PROCEDURE p7() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END'");
        engine.execute("CREATE OR REPLACE PROCEDURE pv() RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN ''abc''; END'");
        engine.execute("CREATE OR REPLACE PROCEDURE p7() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END'");
        assertCells(new String[][] {
            {"BEGIN LET x INT := (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END'"
                + " CALL p()); RETURN x; END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 19 : Numeric "
                + "value "
                + "'{\"type\":\"SCALAR\",\"scalarValue\":\"7\",\"scalarDataType\":\"NUMBER\",\"scalarScale\":0,\"scalarPrecision\":38}'"
                + " is not recognized"},
            {"BEGIN LET x VARCHAR := (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; "
                + "END' CALL p()); RETURN x; END",
                "7"},
            {"BEGIN LET x := (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END' "
                + "CALL p()); RETURN x; END",
                "7"},
            {"DECLARE x INT; BEGIN x := (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN "
                + "7; END' CALL p()); RETURN x; END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : Numeric "
                + "value "
                + "'{\"type\":\"SCALAR\",\"scalarValue\":\"7\",\"scalarDataType\":\"NUMBER\",\"scalarScale\":0,\"scalarPrecision\":38}'"
                + " is not recognized"},
            {"BEGIN LET x INT := (CALL p7()); RETURN x; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'p7'."},
            {"BEGIN LET x := (CALL p7()); RETURN x; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'p7'."},
            {"BEGIN LET x VARCHAR := (CALL pv()); RETURN x; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'pv'."},
            {"BEGIN LET x VARCHAR := (WITH p AS PROCEDURE () RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN"
                + " ''abc''; END' CALL p()); RETURN x; END",
                "abc"},
            {"BEGIN LET rs RESULTSET := (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN "
                + "7; END' CALL p()); LET c CURSOR FOR rs; FOR r IN c DO RETURN r.p; END FOR; END",
                "7"},
            {"DECLARE x INT; BEGIN x := (CALL p7()); RETURN x; END",
                "7"},
            {"DECLARE x INT; BEGIN x := (CALL p7()); RETURN 1; END",
                "1"},
            {"DECLARE x INT; BEGIN x := (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN "
                + "7; END' CALL p()); RETURN 1; END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : Numeric "
                + "value "
                + "'{\"type\":\"SCALAR\",\"scalarValue\":\"7\",\"scalarDataType\":\"NUMBER\",\"scalarScale\":0,\"scalarPrecision\":38}'"
                + " is not recognized"},
            {"BEGIN LET x INT := (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END'"
                + " CALL p()); RETURN 1; END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 19 : Numeric "
                + "value "
                + "'{\"type\":\"SCALAR\",\"scalarValue\":\"7\",\"scalarDataType\":\"NUMBER\",\"scalarScale\":0,\"scalarPrecision\":38}'"
                + " is not recognized"},
            {"BEGIN LET x := (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END' "
                + "CALL p()); RETURN x + 1; END",
                "8.00000"},
            {"BEGIN LET x := (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END' "
                + "CALL p()); RETURN SYSTEM$TYPEOF(x); END",
                "VARCHAR[LOB]"},
            {"BEGIN LET x VARCHAR := (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; "
                + "END' CALL p()); RETURN x || '|' || LENGTH(x); END",
                "7|1"},
            {"BEGIN LET x FLOAT := (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; "
                + "END' CALL p()); RETURN x; END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 21 : Numeric "
                + "value "
                + "'{\"type\":\"SCALAR\",\"scalarValue\":\"7\",\"scalarDataType\":\"NUMBER\",\"scalarScale\":0,\"scalarPrecision\":38}'"
                + " is not recognized"},
            {"BEGIN LET x NUMBER(10,2) := (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN "
                + "RETURN 7; END' CALL p()); RETURN x; END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 28 : Numeric "
                + "value "
                + "'{\"type\":\"SCALAR\",\"scalarValue\":\"7\",\"scalarDataType\":\"NUMBER\",\"scalarScale\":0,\"scalarPrecision\":38}'"
                + " is not recognized"},
            {"BEGIN LET x BOOLEAN := (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; "
                + "END' CALL p()); RETURN x; END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 23 : Boolean "
                + "value "
                + "'{\"type\":\"SCALAR\",\"scalarValue\":\"7\",\"scalarDataType\":\"NUMBER\",\"scalarScale\":0,\"scalarPrecision\":38}'"
                + " is not recognized"},
            {"BEGIN LET x INT := (WITH p AS PROCEDURE () RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN "
                + "''abc''; END' CALL p()); RETURN x; END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 19 : Numeric "
                + "value "
                + "'{\"type\":\"SCALAR\",\"scalarValue\":\"abc\",\"scalarDataType\":\"VARCHAR\",\"scalarScale\":0,\"scalarPrecision\":38}'"
                + " is not recognized"},
            {"BEGIN LET x := (WITH p AS PROCEDURE () RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN "
                + "''abc''; END' CALL p()); RETURN x; END",
                "abc"},
            {"BEGIN LET x INT := (WITH p AS PROCEDURE () RETURNS FLOAT LANGUAGE SQL AS 'BEGIN RETURN 1.5; "
                + "END' CALL p()); RETURN x; END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 19 : Numeric "
                + "value "
                + "'{\"type\":\"SCALAR\",\"scalarValue\":\"1.5\",\"scalarDataType\":\"FLOAT\",\"scalarScale\":0,\"scalarPrecision\":38}'"
                + " is not recognized"},
            {"BEGIN LET x VARCHAR := (WITH p AS PROCEDURE () RETURNS FLOAT LANGUAGE SQL AS 'BEGIN RETURN "
                + "1.5; END' CALL p()); RETURN x; END",
                "1.5"},
            {"BEGIN LET x OBJECT := (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; "
                + "END' CALL p()); RETURN x; END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 22 : SQL "
                + "compilation error: | invalid type [CAST((SELECT P() AS \"P\" FROM (VALUES (NULL)) DUAL) AS "
                + "OBJECT)] for parameter 'TO_OBJECT'"},
            {"BEGIN LET x DATE := (WITH p AS PROCEDURE () RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN "
                + "''abc''; END' CALL p()); RETURN x; END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 20 : Date "
                + "'{\"type\":\"SCALAR\",\"scalarValue\":\"abc\",\"scalarDataType\":\"VARCHAR\",\"scalarScale\":0,\"scalarPrecision\":38}'"
                + " is not recognized"},
            {"BEGIN LET x INT DEFAULT (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7;"
                + " END' CALL p()); RETURN x; END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 24 : Numeric "
                + "value "
                + "'{\"type\":\"SCALAR\",\"scalarValue\":\"7\",\"scalarDataType\":\"NUMBER\",\"scalarScale\":0,\"scalarPrecision\":38}'"
                + " is not recognized"},
            {"BEGIN LET x VARCHAR := (WITH p AS PROCEDURE (a INT) RETURNS INT LANGUAGE SQL AS 'BEGIN "
                + "RETURN a * 2; END' CALL p(21)); RETURN x; END",
                "42"},
            {"DECLARE x INT DEFAULT (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; "
                + "END' CALL p()); BEGIN RETURN x; END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 22 : Numeric "
                + "value "
                + "'{\"type\":\"SCALAR\",\"scalarValue\":\"7\",\"scalarDataType\":\"NUMBER\",\"scalarScale\":0,\"scalarPrecision\":38}'"
                + " is not recognized"},
            {"DECLARE x VARCHAR DEFAULT (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN "
                + "7; END' CALL p()); BEGIN RETURN x; END",
                "7"},
            {"BEGIN LET x := (CALL p7()); RETURN x; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'p7'."},
        });
    }

    @Test
    public void answerTypes() {
        engine.execute("CREATE OR REPLACE PROCEDURE p7() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END'");
        assertCells(new String[][] {
            {"BEGIN LET x INT := (WITH p AS PROCEDURE () RETURNS BOOLEAN LANGUAGE SQL AS 'BEGIN RETURN "
                + "TRUE; END' CALL p()); RETURN x; END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 19 : Numeric "
                + "value "
                + "'{\"type\":\"SCALAR\",\"scalarValue\":\"1\",\"scalarDataType\":\"BOOLEAN\",\"scalarScale\":0,\"scalarPrecision\":38}'"
                + " is not recognized"},
            {"BEGIN LET x INT := (WITH p AS PROCEDURE () RETURNS DATE LANGUAGE SQL AS 'BEGIN RETURN "
                + "''2024-01-02''::DATE; END' CALL p()); RETURN x; END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 19 : Numeric "
                + "value "
                + "'{\"type\":\"SCALAR\",\"scalarValue\":\"19724\",\"scalarDataType\":\"DATE\",\"scalarScale\":0,\"scalarPrecision\":38}'"
                + " is not recognized"},
            {"BEGIN LET x INT := (WITH p AS PROCEDURE () RETURNS NUMBER(10,2) LANGUAGE SQL AS 'BEGIN "
                + "RETURN 3.25; END' CALL p()); RETURN x; END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 19 : Numeric "
                + "value "
                + "'{\"type\":\"SCALAR\",\"scalarValue\":\"3.25\",\"scalarDataType\":\"NUMBER\",\"scalarScale\":2,\"scalarPrecision\":10}'"
                + " is not recognized"},
            {"BEGIN LET x INT := (WITH p AS PROCEDURE () RETURNS TIMESTAMP_NTZ LANGUAGE SQL AS 'BEGIN "
                + "RETURN ''2024-01-02 03:04:05''::TIMESTAMP_NTZ; END' CALL p()); RETURN x; END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 19 : Numeric "
                + "value "
                + "'{\"type\":\"SCALAR\",\"scalarValue\":\"1704164645.000000000\",\"scalarDataType\":\"TIMESTAMP_NTZ\",\"scalarScale\":9,\"scalarPrecision\":0}'"
                + " is not recognized"},
            {"BEGIN LET x INT := (WITH p AS PROCEDURE () RETURNS VARIANT LANGUAGE SQL AS 'BEGIN RETURN "
                + "PARSE_JSON(''{\"a\":1}''); END' CALL p()); RETURN x; END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 19 : Numeric "
                + "value '{\"type\":\"SCALAR\",\"scalarValue\":\"{\\n\\\"a\\\": "
                + "1\\n}\",\"scalarDataType\":\"VARIANT\",\"scalarScale\":0,\"scalarPrecision\":38}' is not "
                + "recognized"},
            {"BEGIN LET x INT := (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN NULL; "
                + "END' CALL p()); RETURN x; END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 19 : Numeric "
                + "value "
                + "'{\"type\":\"SCALAR\",\"scalarValue\":null,\"scalarDataType\":\"NUMBER\",\"scalarScale\":0,\"scalarPrecision\":0}'"
                + " is not recognized"},
            {"BEGIN LET x VARCHAR := (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN "
                + "NULL; END' CALL p()); RETURN COALESCE(x, 'isnull'); END",
                "isnull"},
            {"BEGIN LET x VARCHAR := (WITH p AS PROCEDURE () RETURNS NUMBER(10,2) LANGUAGE SQL AS 'BEGIN "
                + "RETURN 3.25; END' CALL p()); RETURN x; END",
                "3.25"},
            {"BEGIN LET x VARCHAR := (WITH p AS PROCEDURE () RETURNS DATE LANGUAGE SQL AS 'BEGIN RETURN "
                + "''2024-01-02''::DATE; END' CALL p()); RETURN x; END",
                "2024-01-02"},
            {"BEGIN LET x TIMESTAMP_NTZ := (WITH p AS PROCEDURE () RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN "
                + "RETURN ''x''; END' CALL p()); RETURN x; END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 29 : Timestamp "
                + "'{\"type\":\"SCALAR\",\"scalarValue\":\"x\",\"scalarDataType\":\"VARCHAR\",\"scalarScale\":0,\"scalarPrecision\":38}'"
                + " is not recognized"},
            {"BEGIN LET x TIME := (WITH p AS PROCEDURE () RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN "
                + "''x''; END' CALL p()); RETURN x; END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 20 : Time "
                + "'{\"type\":\"SCALAR\",\"scalarValue\":\"x\",\"scalarDataType\":\"VARCHAR\",\"scalarScale\":0,\"scalarPrecision\":38}'"
                + " is not recognized"},
            {"BEGIN LET x VARCHAR(2) := (WITH p AS PROCEDURE () RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN "
                + "RETURN ''abcdef''; END' CALL p()); RETURN x; END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : String "
                + "'{\"type\":\"SCALAR\",\"scalarValue\":\"abcdef\",\"scalarDataType\":\"VARCHAR\",\"scalarScale\":0,\"scalarPrecision\":38}'"
                + " is too long and would be truncated"},
            {"BEGIN LET x BINARY := (WITH p AS PROCEDURE () RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN "
                + "''ab''; END' CALL p()); RETURN x; END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 22 : The "
                + "following string is not a legal hex-encoded value: "
                + "'{\"type\":\"SCALAR\",\"scalarValue\":\"ab\",\"scalarDataType\":\"VARCHAR\",\"scalarScale\":0,\"scalarPrecision\":38}'"},
            {"BEGIN LET x := '7'; RETURN x + 1; END",
                "8.00000"},
            {"BEGIN LET x := (WITH p AS PROCEDURE () RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN ''7''; "
                + "END' CALL p()); RETURN x + 1; END",
                "8.00000"},
            {"BEGIN LET x VARCHAR := (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; "
                + "END', c AS (SELECT 1) CALL p()); RETURN x; END",
                "7"},
            {"BEGIN LET x VARCHAR := (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; "
                + "END' CALL p7()); RETURN x; END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 23 : SQL "
                + "compilation error: | Unknown function P7."},
            {"BEGIN LET x VARCHAR := (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN "
                + "1/0; END' CALL p()); RETURN x; END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 23 : Uncaught "
                + "exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : Division by zero"},
            {"BEGIN LET x VARCHAR := (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; "
                + "END' CALL s.p()); RETURN x; END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 23 : SQL "
                + "compilation error: | Unknown user-defined function S.P."},
        });
    }

    @Test
    public void textTargets() {
        assertCells(new String[][] {
            {"BEGIN LET x VARCHAR(1000) := (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN "
                + "RETURN 7; END' CALL p()); RETURN x; END",
                "7"},
            {"BEGIN LET x VARCHAR(16777216) := (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN "
                + "RETURN 7; END' CALL p()); RETURN x; END",
                "7"},
            {"BEGIN LET x STRING := (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; "
                + "END' CALL p()); RETURN x; END",
                "7"},
            {"BEGIN LET x TEXT := (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; "
                + "END' CALL p()); RETURN x; END",
                "7"},
            {"BEGIN LET x CHAR := (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; "
                + "END' CALL p()); RETURN x; END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 20 : String "
                + "'{\"type\":\"SCALAR\",\"scalarValue\":\"7\",\"scalarDataType\":\"NUMBER\",\"scalarScale\":0,\"scalarPrecision\":38}'"
                + " is too long and would be truncated"},
            {"BEGIN LET x OBJECT := 'abc'::VARCHAR; RETURN x; END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 22 : SQL "
                + "compilation error: | invalid type [CAST(identity('abc') AS OBJECT)] for parameter "
                + "'TO_OBJECT'"},
            {"DECLARE x := (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END' CALL "
                + "p()); BEGIN RETURN x; END",
                "7"},
            {"DECLARE x VARCHAR := (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; "
                + "END' CALL p()); BEGIN RETURN x; END",
                "7"},
            {"DECLARE x VARCHAR; BEGIN x := (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN "
                + "RETURN 7; END' CALL p()); RETURN x; END",
                "7"},
            {"BEGIN LET x := 0; x := (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; "
                + "END' CALL p()); RETURN x; END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 23 : Numeric "
                + "value "
                + "'{\"type\":\"SCALAR\",\"scalarValue\":\"7\",\"scalarDataType\":\"NUMBER\",\"scalarScale\":0,\"scalarPrecision\":38}'"
                + " is not recognized"},
            {"BEGIN LET x := (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END' "
                + "CALL p()); LET y := (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; "
                + "END' CALL p()); RETURN x || y; END",
                "77"},
            {"BEGIN LET x := (WITH p AS PROCEDURE () RETURNS TABLE (a INT) LANGUAGE SQL AS 'DECLARE r "
                + "RESULTSET DEFAULT (SELECT 5 a); BEGIN RETURN TABLE(r); END' CALL p()); RETURN x; END",
                "5"},
        });
    }

    @Test
    public void namedAndAnonymousTexts() {
        engine.execute("CREATE OR REPLACE PROCEDURE pts() RETURNS TIMESTAMP_NTZ LANGUAGE SQL AS 'BEGIN RETURN "
            + "''2024-01-02 03:04:05''::TIMESTAMP_NTZ; END'");
        engine.execute("CREATE OR REPLACE PROCEDURE pvar() RETURNS VARIANT LANGUAGE SQL AS 'BEGIN RETURN "
            + "PARSE_JSON(''{\"a\":1,\"b\":[1,2]}''); END'");
        engine.execute("CREATE OR REPLACE PROCEDURE pd() RETURNS DATE LANGUAGE SQL AS 'BEGIN RETURN "
            + "''2024-01-02''::DATE; END'");
        engine.execute("CREATE OR REPLACE PROCEDURE pb() RETURNS BOOLEAN LANGUAGE SQL AS 'BEGIN RETURN FALSE; END'");
        engine.execute("CREATE OR REPLACE PROCEDURE pn() RETURNS NUMBER(10,2) LANGUAGE SQL AS 'BEGIN RETURN 3.2; "
            + "END'");
        engine.execute("CREATE OR REPLACE PROCEDURE pf() RETURNS FLOAT LANGUAGE SQL AS 'BEGIN RETURN 1e20; END'");
        engine.execute("CREATE OR REPLACE PROCEDURE pobj() RETURNS OBJECT LANGUAGE SQL AS 'BEGIN RETURN "
            + "OBJECT_CONSTRUCT(''a'',1); END'");
        assertCells(new String[][] {
            {"DECLARE x VARCHAR; BEGIN x := (CALL pd()); RETURN x; END",
                "2024-01-02"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL pn()); RETURN x; END",
                "3.20"},
            {"DECLARE x VARCHAR; BEGIN x := (WITH q AS PROCEDURE () RETURNS DATE LANGUAGE SQL AS 'BEGIN "
                + "RETURN ''2024-01-02''::DATE; END' CALL q()); RETURN x; END",
                "2024-01-02"},
            {"DECLARE x VARCHAR; BEGIN x := (WITH q AS PROCEDURE () RETURNS NUMBER(10,2) LANGUAGE SQL AS "
                + "'BEGIN RETURN 3.2; END' CALL q()); RETURN x; END",
                "3.20"},
            {"DECLARE x INT; BEGIN x := (WITH q AS PROCEDURE () RETURNS BOOLEAN LANGUAGE SQL AS 'BEGIN "
                + "RETURN FALSE; END' CALL q()); RETURN x; END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : Numeric "
                + "value "
                + "'{\"type\":\"SCALAR\",\"scalarValue\":\"0\",\"scalarDataType\":\"BOOLEAN\",\"scalarScale\":0,\"scalarPrecision\":38}'"
                + " is not recognized"},
            {"DECLARE x INT; BEGIN x := (WITH q AS PROCEDURE () RETURNS NUMBER(10,2) LANGUAGE SQL AS "
                + "'BEGIN RETURN 3.2; END' CALL q()); RETURN x; END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : Numeric "
                + "value "
                + "'{\"type\":\"SCALAR\",\"scalarValue\":\"3.20\",\"scalarDataType\":\"NUMBER\",\"scalarScale\":2,\"scalarPrecision\":10}'"
                + " is not recognized"},
            {"DECLARE x INT; BEGIN x := (WITH q AS PROCEDURE () RETURNS FLOAT LANGUAGE SQL AS 'BEGIN "
                + "RETURN 1e20; END' CALL q()); RETURN x; END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : Numeric "
                + "value "
                + "'{\"type\":\"SCALAR\",\"scalarValue\":\"1e+20\",\"scalarDataType\":\"FLOAT\",\"scalarScale\":0,\"scalarPrecision\":38}'"
                + " is not recognized"},
            {"DECLARE x INT; BEGIN x := (WITH q AS PROCEDURE () RETURNS OBJECT LANGUAGE SQL AS 'BEGIN "
                + "RETURN OBJECT_CONSTRUCT(''a'',1); END' CALL q()); RETURN x; END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : Numeric "
                + "value '{\"type\":\"SCALAR\",\"scalarValue\":\"{\\n\\\"a\\\": "
                + "1\\n}\",\"scalarDataType\":\"OBJECT\",\"scalarScale\":0,\"scalarPrecision\":38}' is not "
                + "recognized"},
            {"DECLARE x INT; BEGIN x := (WITH q AS PROCEDURE () RETURNS VARCHAR(10) LANGUAGE SQL AS 'BEGIN"
                + " RETURN ''it''''s \"q\"''; END' CALL q()); RETURN x; END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : Numeric "
                + "value '{\"type\":\"SCALAR\",\"scalarValue\":\"it's "
                + "\\\"q\\\"\",\"scalarDataType\":\"VARCHAR\",\"scalarScale\":0,\"scalarPrecision\":38}' is not"
                + " recognized"},
            {"DECLARE x INT; BEGIN x := (WITH q AS PROCEDURE () RETURNS TIME LANGUAGE SQL AS 'BEGIN RETURN"
                + " ''03:04:05''::TIME; END' CALL q()); RETURN x; END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : Numeric "
                + "value "
                + "'{\"type\":\"SCALAR\",\"scalarValue\":\"11045.000000000\",\"scalarDataType\":\"TIME\",\"scalarScale\":9,\"scalarPrecision\":0}'"
                + " is not recognized"},
            {"DECLARE x INT; BEGIN x := (WITH q AS PROCEDURE (a INT) RETURNS INT LANGUAGE SQL AS 'BEGIN "
                + "RETURN a; END' CALL q(5)); RETURN x; END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : Numeric "
                + "value "
                + "'{\"type\":\"SCALAR\",\"scalarValue\":\"5\",\"scalarDataType\":\"NUMBER\",\"scalarScale\":0,\"scalarPrecision\":38}'"
                + " is not recognized"},
            {"BEGIN LET x := (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END' "
                + "CALL p7()); RETURN x; END",
                "REFUSED SQL compilation error: | Unknown function P7."},
            {"DECLARE x VARCHAR; BEGIN x := (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN "
                + "RETURN 7; END' CALL pd()); RETURN x; END",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL "
                + "compilation error: | Unknown function PD."},
        });
    }

    @Test
    public void refusalsAndBinds() {
        assertCells(new String[][] {
            {"SELECT public.nosuch()",
                "REFUSED SQL compilation error: | Unknown user-defined function PUBLIC.NOSUCH."},
            {"SELECT nosuchsch.nosuch()",
                "REFUSED SQL compilation error: | Unknown user-defined function NOSUCHSCH.NOSUCH."},
            {"CALL public.nosuchp()",
                "REFUSED SQL compilation error: | Unknown user-defined function PUBLIC.NOSUCHP."},
            {"WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END' CALL public.p()",
                "REFUSED SQL compilation error: | Unknown user-defined function PUBLIC.P."},
            {"BEGIN LET x := (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END' "
                + "CALL s.p()); RETURN x; END",
                "REFUSED SQL compilation error: | Unknown user-defined function S.P."},
            {"DECLARE x := (WITH p AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END' CALL "
                + "q()); BEGIN RETURN x; END",
                "REFUSED SQL compilation error: | Unknown function Q."},
            {"BEGIN LET x := (WITH \"p\" AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END'"
                + " CALL \"p\"()); RETURN x; END",
                "7"},
            {"BEGIN LET x := (WITH \"p\" AS PROCEDURE () RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 7; END'"
                + " CALL p()); RETURN x; END",
                "REFUSED SQL compilation error: | Unknown function P."},
        });
    }
}
