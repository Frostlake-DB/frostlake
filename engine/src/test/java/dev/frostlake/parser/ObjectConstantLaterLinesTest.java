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

package dev.frostlake.parser;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * An object constant with a key that is no text: a block's value in parentheses, a query assigned or returned, the
 * faults of a block's later statements, a CALL after the constant, a constant's values that are names, signed or
 * calls, and a SQL function's body refused before a constant (live-verified).
 */
public class ObjectConstantLaterLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void parenthesizedAndLater() {
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := (1 + {1: 'a'}); RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '1'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ({1: 'a'}::MAP(NUMBER, VARCHAR)); RETURN "
                + "m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'. | syntax"
                + " error line 1 at position 51 unexpected ','."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ({1: 'a'}); RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ({1: 'a'}::VARIANT); RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := (2 * 3 + {1: 'a'}); RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '2'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := (x + {1: 'a'}); RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'x'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ('s' || {1: 'a'}); RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ''s''."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ({1: 'a', 2: 'b'}); RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ({'k': 1, 2: 'b'}); RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected ''k''."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ({TRUE: 'a'}); RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'TRUE'. | "
                + "syntax error line 1 at position 38 unexpected ''a''."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ({1: x}); RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := (({1: 'a'})); RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '{'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ({1: 'a'}); m := ({2: 'b'}); RETURN m; "
                + "END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'. | syntax"
                + " error line 1 at position 49 unexpected '2'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ({1: 'a'}) + 1; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'. | syntax"
                + " error line 1 at position 41 unexpected '+'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := (1 + {1: 'a'}); m := 2 + ; RETURN m; END; "
                + "$$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '1'. | syntax"
                + " error line 1 at position 51 unexpected '2'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ({1: 'a'}::MAP(NUMBER, VARCHAR)); END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'. | syntax"
                + " error line 1 at position 51 unexpected ','."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := (OBJECT_INSERT({1: 'a'}, 'k', 1)); RETURN "
                + "m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected "
                + "'OBJECT_INSERT'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ({-1: 'a'}); RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '-'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ({1: 'a'}::OBJECT); RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := (1, {1: 'a'}); RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '1'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ({1: 'a'}:k); RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := (1 + {1: 'a'}) ; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '1'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := (1 + {1: 'a', 2: 'b'}); RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '1'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := (1 + 2 + {1: 'a'}); RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '1'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ({1: 'a'} + 1); RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := (ABS(1) + {1: 'a'}); RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'ABS'."},
            {"EXECUTE IMMEDIATE $$ DECLARE x VARIANT; BEGIN x := (SELECT {1: 'a'}); RETURN x; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '1'. | syntax"
                + " error line 1 at position 43 unexpected ''a''."},
            {"EXECUTE IMMEDIATE $$ DECLARE x VARIANT; BEGIN x := (SELECT {1: 'a', 2: 'b'}); RETURN x; END;"
                + " $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '1'. | syntax"
                + " error line 1 at position 43 unexpected ''a''. | syntax error line 1 at position 51 "
                + "unexpected ''b''."},
            {"EXECUTE IMMEDIATE $$ DECLARE x VARIANT; BEGIN x := (SELECT 1, {1: 'a'}); RETURN x; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected '1'. | syntax"
                + " error line 1 at position 46 unexpected ''a''."},
            {"EXECUTE IMMEDIATE $$ DECLARE x VARIANT; BEGIN x := (SELECT {1: 'a'} FROM t); RETURN x; END; "
                + "$$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '1'. | syntax"
                + " error line 1 at position 43 unexpected ''a''."},
            {"EXECUTE IMMEDIATE $$ BEGIN LET y OBJECT := (SELECT {1: 'a'}); RETURN y; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 35 unexpected ''a''."},
            {"EXECUTE IMMEDIATE $$ DECLARE x VARIANT; BEGIN x := (SELECT {'k': 1, 2: 'b'}); RETURN x; END;"
                + " $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected '2'. | syntax"
                + " error line 1 at position 51 unexpected ''b''."},
            {"EXECUTE IMMEDIATE $$ DECLARE x VARIANT; BEGIN x := (SELECT {1: 'a'}); x := 2 + ; RETURN x; "
                + "END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '1'. | syntax"
                + " error line 1 at position 43 unexpected ''a''. | syntax error line 1 at position 55 "
                + "unexpected '2'."},
            {"EXECUTE IMMEDIATE $$ DECLARE x VARIANT; BEGIN x := (SELECT {TRUE: 'a'}); RETURN x; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected ':'. | syntax"
                + " error line 1 at position 46 unexpected ''a''."},
            {"EXECUTE IMMEDIATE $$ DECLARE x VARIANT; BEGIN x := (SELECT {1: x}); RETURN x; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '1'. | syntax"
                + " error line 1 at position 44 unexpected '}'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'}; RETURN 1 +; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 50 unexpected ';'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'}; m := 1 +; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 45 unexpected '1'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'}; SELECT FROM; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 47 unexpected 'FROM'. | syntax error line 1 at position 51 "
                + "unexpected ';'."},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN {1: 'a'}; RETURN 1 +; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '1'. | syntax"
                + " error line 1 at position 18 unexpected ''a''. | syntax error line 1 at position 34 "
                + "unexpected ';'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'}; RETURN 1 + 2 3; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 53 unexpected '3'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'}; LET z := 1 +; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 52 unexpected ';'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN RETURN {1: 'a'}; m := 1 +; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '1'. | syntax"
                + " error line 1 at position 36 unexpected ''a''. | syntax error line 1 at position 47 "
                + "unexpected '1'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'}; RETURN (1 +; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 51 unexpected ';'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'}; x := y z; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 47 unexpected 'z'. | syntax error line 1 at position 50 unexpected"
                + " 'RETURN'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'}; IF (1 +) THEN RETURN 1; END IF; "
                + "END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 47 unexpected ')'."},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN {1: 'a'}; SELECT FROM; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '1'. | syntax"
                + " error line 1 at position 18 unexpected ''a''. | syntax error line 1 at position 31 "
                + "unexpected 'FROM'. | syntax error line 1 at position 35 unexpected ';'."},
            {"EXECUTE IMMEDIATE $$ BEGIN LET m := {1: 'a'}; RETURN 1 +; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '1'. | syntax"
                + " error line 1 at position 20 unexpected ''a''. | syntax error line 1 at position 36 "
                + "unexpected ';'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'}; RETURN ABS(1,); END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 53 unexpected ')'."},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN {1: 'a'}; RETURN ABS(1,); END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '1'. | syntax"
                + " error line 1 at position 18 unexpected ''a''. | syntax error line 1 at position 37 "
                + "unexpected ')'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'}; RETURN 1 + {2: 'b'}; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 52 unexpected '2'. | syntax error line 1 at position 55 unexpected"
                + " ''b''."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'} 'a' zz; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 47 unexpected 'RETURN'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'} 'a'; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'} 'a' 'b' zz; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 51 unexpected 'RETURN'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'} 1 zz; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 45 unexpected 'RETURN'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'} 'a' FROM; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'} 'a' zz yy; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 46 unexpected 'yy'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'} || 'a' zz; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 50 unexpected 'RETURN'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := 1 + 'a' zz; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'zz'. | "
                + "syntax error line 1 at position 42 unexpected 'RETURN'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'} 'a' \"zz\"; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 49 unexpected 'RETURN'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'} 'a' zz; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 47 unexpected 'END'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 1} CALL zz; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 1} CALL zz(); RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 1} CALL; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 43 unexpected 'RETURN'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := 1 + CALL zz; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'zz'. | "
                + "syntax error line 1 at position 43 unexpected 'RETURN'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 1} 'a' CALL zz; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'."},
            {"CREATE OR REPLACE FUNCTION fm1() RETURNS VARCHAR AS $$ BEGIN RETURN {1: 'a'}; END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 8 unexpected 'RETURN'. | syntax error line 1 at position 19 unexpected ''a''."},
            {"CREATE OR REPLACE FUNCTION fm2() RETURNS VARCHAR AS $$ BEGIN RETURN {1: 'a', 2: 'b'}; END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 8 unexpected 'RETURN'. | syntax error line 1 at position 19 unexpected ''a''. | "
                + "syntax error line 1 at position 27 unexpected ''b''."},
            {"CREATE OR REPLACE FUNCTION fm3() RETURNS VARCHAR AS $$ BEGIN LET m := {1: 'a'}; RETURN m; "
                + "END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 8 unexpected 'LET'. | syntax error line 1 at position 21 unexpected ''a''."},
            {"CREATE OR REPLACE FUNCTION fm4() RETURNS VARCHAR AS $$ BEGIN RETURN 1 +; END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 8 unexpected 'RETURN'. | syntax error line 1 at position 18 unexpected ';'."},
            {"CREATE OR REPLACE FUNCTION fm5() RETURNS VARCHAR AS $$ DECLARE m OBJECT; BEGIN m := {1: "
                + "'a'}; RETURN m; END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 10 unexpected 'm'."},
            {"CREATE OR REPLACE FUNCTION fm8() RETURNS VARCHAR AS $$ BEGIN RETURN {'k': 1, 2: 'b'}; END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 8 unexpected 'RETURN'. | syntax error line 1 at position 21 unexpected '1'. | "
                + "syntax error line 1 at position 27 unexpected ''b''."},
            {"CREATE OR REPLACE FUNCTION fm9() RETURNS VARCHAR AS 'BEGIN RETURN {1: ''a''}; END'",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 7 unexpected 'RETURN'. | syntax error line 1 at position 18 unexpected ''a''."},
            {"CREATE OR REPLACE FUNCTION fm10() RETURNS VARCHAR AS $$ {1: 'a'} $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 3 unexpected '1'. | syntax error line 1 at position 6 unexpected ''a''."},
            {"CREATE OR REPLACE FUNCTION fm11() RETURNS VARCHAR AS $$ x {1: 'a'} $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 4 unexpected '{'. | syntax error line 1 at position 8 unexpected ''a''."},
            {"CREATE OR REPLACE FUNCTION fm12() RETURNS VARCHAR AS $$ BEGIN RETURN {TRUE: 'a'}; END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 8 unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected ''a''."},
        });
    }

    @Test
    public void moreShapes() {
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 1} CALL 1; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 1} CALL \"zz\"; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 1} CALL zz zz2; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 1} CALL zz; RETURN 1 +; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 1} CALL (1); RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 42 unexpected '('."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 1} CALL ; ; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 46 unexpected 'RETURN'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 1} CALL; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 43 unexpected 'END'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ({1: 'a'}::MAP(x, y)); RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'. | syntax"
                + " error line 1 at position 46 unexpected ','."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ({1: 'a'}::MAP(VARCHAR, NUMBER)); RETURN "
                + "m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'. | syntax"
                + " error line 1 at position 52 unexpected ','."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ({1: 'a'}, ABS(1, 2)); RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ({1: 'a'}) zz; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'. | syntax"
                + " error line 1 at position 41 unexpected 'zz'. | syntax error line 1 at position 45 "
                + "unexpected 'RETURN'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ({1: 'a'}) + 1 +; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'. | syntax"
                + " error line 1 at position 41 unexpected '+'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := (1 + {1: 'a'}) + 1; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '1'. | syntax"
                + " error line 1 at position 45 unexpected '+'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ((1 + {1: 'a'})); RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ((({1: 'a'}))); RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '{'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ({'k': 1, 'j': 2, 3: 'b'}); RETURN m; END;"
                + " $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected ''k''."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ({NULL: 'a'}); RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'NULL'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ({k: 'a'}); RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'k'. | syntax"
                + " error line 1 at position 35 unexpected ''a''."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ({TRUE: 1}); RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'TRUE'. | "
                + "syntax error line 1 at position 38 unexpected '1'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ({1: 'a'}::ARRAY(NUMBER)); RETURN m; END; "
                + "$$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'. | syntax"
                + " error line 1 at position 53 unexpected ')'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ({1: 'a'}::NUMBER(10, 2)); RETURN m; END; "
                + "$$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ({1: 'a'}) ; RETURN 1 +; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'. | syntax"
                + " error line 1 at position 53 unexpected ';'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := (1 + {1: 'a'}) ; RETURN 1 +; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '1'. | syntax"
                + " error line 1 at position 57 unexpected ';'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ({1: 'a'}) 'x'; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'. | syntax"
                + " error line 1 at position 41 unexpected ''x''."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ({1: 'a'})) ; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'. | syntax"
                + " error line 1 at position 40 unexpected ')'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ({1: 'a'}::MAP(NUMBER, VARCHAR)) + 1; "
                + "RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'. | syntax"
                + " error line 1 at position 51 unexpected ','."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := (zz({1: 'a'})); RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'zz'."},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN (SELECT {1: 'a'}); END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 26 unexpected ''a''."},
            {"EXECUTE IMMEDIATE $$ DECLARE x VARIANT DEFAULT (SELECT {1: 'a'}); BEGIN RETURN x; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 39 unexpected ''a''."},
            {"EXECUTE IMMEDIATE $$ BEGIN LET y := (SELECT {1: 'a'}); RETURN y; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 28 unexpected ''a''."},
            {"EXECUTE IMMEDIATE $$ DECLARE x VARIANT; BEGIN x := (SELECT {1: 'a'}) + 1; RETURN x; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '1'. | syntax"
                + " error line 1 at position 43 unexpected ''a''. | syntax error line 1 at position 49 "
                + "unexpected '+'."},
            {"EXECUTE IMMEDIATE $$ DECLARE x VARIANT; BEGIN x := (SELECT {-1: 'a'}); RETURN x; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '-'. | syntax"
                + " error line 1 at position 44 unexpected ''a''."},
            {"EXECUTE IMMEDIATE $$ DECLARE x VARIANT; BEGIN x := (WITH c AS (SELECT 1) SELECT {1: 'a'}); "
                + "RETURN x; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected '1'. | syntax"
                + " error line 1 at position 64 unexpected ''a''."},
            {"EXECUTE IMMEDIATE $$ DECLARE x VARIANT; BEGIN x := (SELECT {1: 'a'}); RETURN 1 +; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '1'. | syntax"
                + " error line 1 at position 43 unexpected ''a''. | syntax error line 1 at position 60 "
                + "unexpected ';'."},
            {"SELECT {1: x}",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected '1'. | syntax "
                + "error line 1 at position 12 unexpected '}'."},
            {"SELECT {1: 'a'} FROM t",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected '1'. | syntax "
                + "error line 1 at position 11 unexpected ''a''."},
            {"EXECUTE IMMEDIATE $$ BEGIN LET y OBJECT := (SELECT {'k': 1, 2: 'b'}); RETURN y; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 37 unexpected '1'. | syntax error line 1 at position 43 "
                + "unexpected ''b''."},
            {"EXECUTE IMMEDIATE $$ BEGIN LET y OBJECT := (SELECT 1 +); RETURN y; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'SELECT'."},
            {"CREATE OR REPLACE FUNCTION fm13() RETURNS VARCHAR AS $$ BEGIN RETURN 1 + {1: 'a'}; END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 8 unexpected 'RETURN'. | syntax error line 1 at position 20 unexpected '1'. | "
                + "syntax error line 1 at position 23 unexpected ''a''."},
            {"CREATE OR REPLACE FUNCTION fm14() RETURNS VARCHAR AS $$ BEGIN RETURN {'k': 'a'}; RETURN 1 +;"
                + " END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 8 unexpected 'RETURN'. | syntax error line 1 at position 21 unexpected ''a''."},
            {"CREATE OR REPLACE FUNCTION fm15() RETURNS VARCHAR AS $$ BEGIN RETURN {1: x}; END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 8 unexpected 'RETURN'. | syntax error line 1 at position 20 unexpected '}'."},
            {"CREATE OR REPLACE FUNCTION fm16() RETURNS VARCHAR AS $$ BEGIN RETURN {1: 'a'}; RETURN {2: "
                + "'b'}; END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 8 unexpected 'RETURN'. | syntax error line 1 at position 19 unexpected ''a''."},
            {"CREATE OR REPLACE FUNCTION fm17() RETURNS VARCHAR AS $$ BEGIN RETURN {1: 'a'}; RETURN 1 +; "
                + "END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 8 unexpected 'RETURN'. | syntax error line 1 at position 19 unexpected ''a''."},
            {"CREATE OR REPLACE FUNCTION fm18() RETURNS VARCHAR AS $$ BEGIN RETURN {1: {2: 'a'}}; END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 8 unexpected 'RETURN'. | syntax error line 1 at position 19 unexpected '{'. | "
                + "syntax error line 1 at position 23 unexpected ''a''."},
            {"CREATE OR REPLACE FUNCTION fm19() RETURNS VARCHAR AS $$ BEGIN RETURN {1: 'a', 'k': 2}; END "
                + "$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 8 unexpected 'RETURN'. | syntax error line 1 at position 19 unexpected ''a''. | "
                + "syntax error line 1 at position 29 unexpected '2'."},
            {"CREATE OR REPLACE FUNCTION fm20(x NUMBER) RETURNS VARCHAR AS $$ x + {1: 'a'} $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 7 unexpected '1'. | syntax error line 1 at position 10 unexpected ''a''."},
            {"CREATE OR REPLACE FUNCTION fm21() RETURNS VARCHAR AS $$ BEGIN RETURN {1: 'a'}::VARCHAR; END "
                + "$$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 8 unexpected 'RETURN'. | syntax error line 1 at position 19 unexpected ''a''."},
            {"CREATE OR REPLACE FUNCTION fm22() RETURNS VARCHAR AS $$ BEGIN RETURN ABS({1: 'a'}); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 8 unexpected 'RETURN'. | syntax error line 1 at position 19 unexpected '{'."},
            {"CREATE OR REPLACE FUNCTION fm23() RETURNS VARCHAR AS $$ BEGIN RETURN {1: -2}; END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 8 unexpected 'RETURN'. | syntax error line 1 at position 19 unexpected '-'. | "
                + "syntax error line 1 at position 21 unexpected '}'."},
        });
    }

    @Test
    public void valuesAndReadOn() {
        assertCells(new String[][] {
            {"SELECT {1: -2}",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected '1'. | syntax "
                + "error line 1 at position 11 unexpected '-'. | syntax error line 1 at position 13 unexpected "
                + "'}'."},
            {"SELECT {1: -2, 3: 4}",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected '1'. | syntax "
                + "error line 1 at position 11 unexpected '-'. | syntax error line 1 at position 18 unexpected "
                + "'4'."},
            {"SELECT {1: x, 2: 'b'}",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected '1'. | syntax "
                + "error line 1 at position 17 unexpected ''b''."},
            {"SELECT {1: {2: 'a'}}",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected '1'. | syntax "
                + "error line 1 at position 11 unexpected '{'. | syntax error line 1 at position 15 unexpected "
                + "''a''."},
            {"SELECT {1: x.y}",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected '1'. | syntax "
                + "error line 1 at position 14 unexpected '}'."},
            {"SELECT {1: \"x\"}",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected '1'. | syntax "
                + "error line 1 at position 14 unexpected '}'."},
            {"SELECT {1: ABS(1)}",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected '1'. | syntax "
                + "error line 1 at position 15 unexpected '1'."},
            {"SELECT {1: 'a', 2: x}",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected '1'. | syntax "
                + "error line 1 at position 11 unexpected ''a''. | syntax error line 1 at position 20 "
                + "unexpected '}'."},
            {"SELECT {1: -x}",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected '1'. | syntax "
                + "error line 1 at position 11 unexpected '-'. | syntax error line 1 at position 13 unexpected "
                + "'}'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := (x + {1: 'a'}); RETURN 1 +; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'x'. | syntax"
                + " error line 1 at position 56 unexpected ';'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ({1: x}); RETURN 1 +; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'."},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN (SELECT {1: 'a'}); RETURN 1 +; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 26 unexpected ''a''. | syntax error line 1 at position 43 "
                + "unexpected ';'."},
            {"EXECUTE IMMEDIATE $$ BEGIN LET y := (SELECT {1: 'a'}); RETURN 1 +; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 28 unexpected ''a''. | syntax error line 1 at position 45 "
                + "unexpected ';'."},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN (SELECT 1, {1: 'a'}); END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 26 unexpected '1'. | syntax error line 1 at position 29 "
                + "unexpected ''a''."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {NULL: 'a'}; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ({FALSE: 'a'}); RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'FALSE'. | "
                + "syntax error line 1 at position 39 unexpected ''a''."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ({1: 'a'}, ABS(x, y)); RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'. | syntax"
                + " error line 1 at position 46 unexpected ','."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ({1: 'a'}::MAP(NUMBER, VARCHAR)); RETURN 1"
                + " +; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'. | syntax"
                + " error line 1 at position 51 unexpected ','."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ({TRUE: 'a'}); RETURN 1 +; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'TRUE'. | "
                + "syntax error line 1 at position 38 unexpected ''a''. | syntax error line 1 at position 55 "
                + "unexpected ';'."},
            {"EXECUTE IMMEDIATE $$ DECLARE x VARIANT; BEGIN x := (SELECT {1: 'a'}) zz; RETURN x; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '1'. | syntax"
                + " error line 1 at position 43 unexpected ''a''. | syntax error line 1 at position 49 "
                + "unexpected 'zz'. | syntax error line 1 at position 53 unexpected 'RETURN'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ({1: 'a'}) zz; RETURN 1 +; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'. | syntax"
                + " error line 1 at position 41 unexpected 'zz'. | syntax error line 1 at position 45 "
                + "unexpected 'RETURN'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ({1: 'a'}) + 1; RETURN 1 +; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'. | syntax"
                + " error line 1 at position 41 unexpected '+'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ({TRUE: x}); RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'TRUE'. | "
                + "syntax error line 1 at position 39 unexpected '}'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ({k: 'a', 2: 'b'}); RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'k'. | syntax"
                + " error line 1 at position 35 unexpected ''a''. | syntax error line 1 at position 43 "
                + "unexpected ''b''."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'}; m := {2: 'b'}; RETURN 1 +; END; "
                + "$$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 45 unexpected '{'. | syntax error line 1 at position 65 unexpected"
                + " ';'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN IF (TRUE) THEN m := {1: 'a'}; END IF; RETURN 1 "
                + "+; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected '{'. | syntax"
                + " error line 1 at position 73 unexpected ';'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'}; RETURN m; END; SELECT 1 +; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 65 unexpected ';'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'} + 1; RETURN 1 +; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 54 unexpected ';'."},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN {1: 'a'} + ; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '1'. | syntax"
                + " error line 1 at position 18 unexpected ''a''. | syntax error line 1 at position 25 "
                + "unexpected ';'."},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN {1: 'a'}; LET y := 2 +; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '1'. | syntax"
                + " error line 1 at position 18 unexpected ''a''. | syntax error line 1 at position 36 "
                + "unexpected ';'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'}; RETURN 1 + ; m := {2: 'b'}; END;"
                + " $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 51 unexpected ';'. | syntax error line 1 at position 58 unexpected"
                + " '{'."},
            {"EXECUTE IMMEDIATE $$ DECLARE x VARIANT; BEGIN x := (SELECT {1: 'a'}) + 1; RETURN 1 +; END; "
                + "$$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '1'. | syntax"
                + " error line 1 at position 43 unexpected ''a''. | syntax error line 1 at position 49 "
                + "unexpected '+'."},
            {"CREATE OR REPLACE FUNCTION fu1() RETURNS VARCHAR AS $$ BEGIN LET m := 1 +; RETURN {1: 'a'}; "
                + "END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 8 unexpected 'LET'. | syntax error line 1 at position 20 unexpected ';'."},
            {"CREATE OR REPLACE FUNCTION fu2() RETURNS VARCHAR AS $$ {1: 'a'} + 1 $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 3 unexpected '1'. | syntax error line 1 at position 6 unexpected ''a''."},
            {"CREATE OR REPLACE FUNCTION fu3() RETURNS VARCHAR AS $$ {1: 'a', 2: x} $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 3 unexpected '1'. | syntax error line 1 at position 6 unexpected ''a''. | syntax "
                + "error line 1 at position 15 unexpected '}'."},
            {"CREATE OR REPLACE FUNCTION fu4() RETURNS VARCHAR AS $$ BEGIN RETURN {1: 'a', 2: x}; END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at "
                + "position 8 unexpected 'RETURN'. | syntax error line 1 at position 19 unexpected ''a''. | "
                + "syntax error line 1 at position 28 unexpected '}'."},
        });
    }

    @Test
    public void proceduresAndLines() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE PROCEDURE pp1() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE m OBJECT; BEGIN"
                + " m := (1 + {1: 'a'}); RETURN m; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '1'."},
            {"CREATE OR REPLACE PROCEDURE pp2() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE m OBJECT; BEGIN"
                + " m := {1: 'a'}; RETURN 1 +; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 50 unexpected ';'."},
            {"CREATE OR REPLACE PROCEDURE pp3() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN (SELECT "
                + "{1: 'a'}); END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 26 unexpected ''a''."},
            {"CREATE OR REPLACE PROCEDURE pp4() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE x VARIANT; "
                + "BEGIN x := (SELECT {1: 'a'}) zz; RETURN x; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '1'. | syntax"
                + " error line 1 at position 43 unexpected ''a''. | syntax error line 1 at position 49 "
                + "unexpected 'zz'. | syntax error line 1 at position 53 unexpected 'RETURN'."},
            {"CREATE OR REPLACE PROCEDURE pp5() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE m OBJECT; BEGIN"
                + " m := {1: 1} CALL; RETURN m; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 43 unexpected 'RETURN'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT;\nBEGIN\n  m := {1: 'a'};\n  RETURN 1 +;\nEND; $$",
                "REFUSED SQL compilation error: | syntax error line 3 at position 7 unexpected '{'. | syntax "
                + "error line 4 at position 12 unexpected ';'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT;\nBEGIN\n  m := ({1: 'a'})\n  + 1;\n  RETURN m;\nEND; "
                + "$$",
                "REFUSED SQL compilation error: | syntax error line 3 at position 9 unexpected '1'. | syntax "
                + "error line 4 at position 2 unexpected '+'."},
            {"EXECUTE IMMEDIATE $$ BEGIN\n  RETURN {1: 'a'};\n  SELECT FROM;\n  RETURN {2: x};\nEND; $$",
                "REFUSED SQL compilation error: | syntax error line 2 at position 10 unexpected '1'. | syntax"
                + " error line 2 at position 13 unexpected ''a''. | syntax error line 3 at position 9 "
                + "unexpected 'FROM'. | syntax error line 3 at position 13 unexpected ';'. | syntax error line "
                + "4 at position 10 unexpected '2'. | syntax error line 4 at position 14 unexpected '}'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'}; RETURN 1 +; m := {2: 'b'}; "
                + "RETURN 2 +; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 50 unexpected ';'. | syntax error line 1 at position 57 unexpected"
                + " '{'. | syntax error line 1 at position 77 unexpected ';'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'}; LET q := (SELECT {3: 'c'}); "
                + "RETURN q; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 50 unexpected 'SELECT'. | syntax error line 1 at position 61 "
                + "unexpected ''c''."},
        });
    }
}
