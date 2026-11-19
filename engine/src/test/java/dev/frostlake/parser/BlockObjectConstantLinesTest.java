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
 * A scripting block's object constant whose key is no text (live-verified). A plain assignment is refused at the
 * first token of its value; the account then reads on to the ';' that ends the statement, or takes the first word
 * that may be a name and refuses the token after it. A LET, a DEFAULT and a RETURN read the constant as a query does:
 * the key, then each value. A body with such a fault is refused as its procedure is created.
 */
public class BlockObjectConstantLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void assignmentsAndTheirTails() {
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ DECLARE m MAP(NUMBER, VARCHAR); BEGIN m := {1: 'a'}::MAP(NUMBER, "
                + "VARCHAR); RETURN (MAP_SIZE(m) > 0) || ' ' || TRUE; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected '{'. | syntax"
                + " error line 1 at position 57 unexpected '('."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'}; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m VARIANT; BEGIN m := {1: 'a'}::VARIANT; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '{'. | syntax"
                + " error line 1 at position 50 unexpected 'RETURN'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {'a': 1, 2: 'b'}; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {TRUE: 'a'}; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 35 unexpected ':'."},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n OBJECT := {1: 'a'}; RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '1'. | syntax"
                + " error line 1 at position 27 unexpected ''a''."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT DEFAULT {1: 'a'}; BEGIN RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '1'. | syntax"
                + " error line 1 at position 30 unexpected ''a''."},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN {1: 'a'}; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '1'. | syntax"
                + " error line 1 at position 18 unexpected ''a''."},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN {1: 'a', 'b': 2}; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '1'. | syntax"
                + " error line 1 at position 18 unexpected ''a''. | syntax error line 1 at position 28 "
                + "unexpected '2'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a', 'b': 2, 3: 4}; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {x: 'a'}; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 32 unexpected ':'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'}::OBJECT; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 48 unexpected 'END'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'}::VARCHAR(10); RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 47 unexpected '('."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'}; m := {2: 'b'}; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 45 unexpected '{'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := OBJECT_INSERT({1: 'a'}, 'k', 1); RETURN m;"
                + " END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected "
                + "'OBJECT_INSERT'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := ({1: 'a'}); RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN IF (TRUE) THEN m := {1: 'a'}; END IF; RETURN m;"
                + " END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected '{'."},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n OBJECT := {1: 'a'}::MAP(NUMBER, VARCHAR); RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '1'. | syntax"
                + " error line 1 at position 27 unexpected ''a''."},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n := {1: 'a'}; RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '1'. | syntax"
                + " error line 1 at position 20 unexpected ''a''."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {NULL: 'a'}; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1.5: 'a'} ; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'}:x; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 42 unexpected 'RETURN'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'}::VARIANT::VARCHAR; RETURN m; END;"
                + " $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 47 unexpected '::'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'}::NUMBER(10,2)::VARCHAR(3); RETURN"
                + " m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 46 unexpected '('."},
            {"CREATE OR REPLACE PROCEDURE pm1() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE m OBJECT; BEGIN"
                + " m := {1: 'a'}; RETURN m; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'."},
            {"CALL pm1()",
                "REFUSED SQL compilation error: | Unknown function PM1."},
            {"CREATE OR REPLACE PROCEDURE pm2() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN {1: 'a'}; "
                + "END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '1'. | syntax"
                + " error line 1 at position 18 unexpected ''a''."},
            {"DECLARE m OBJECT; BEGIN m := {1: 'a'}::MAP(NUMBER, VARCHAR); RETURN m; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '{'. | syntax"
                + " error line 1 at position 42 unexpected '('."},
            {"BEGIN RETURN {1: 'a'}; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '1'. | syntax"
                + " error line 1 at position 17 unexpected ''a''."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'} + 1; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'}.x; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 42 unexpected 'RETURN'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'} || 'z'; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'}['k']; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {x: 'a'}::VARIANT; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 32 unexpected ':'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {x: 'a'}::MAP(NUMBER, VARCHAR); RETURN m; "
                + "END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 32 unexpected ':'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {'a': 1, 2: 'b'}::VARIANT; RETURN m; END; "
                + "$$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 57 unexpected 'RETURN'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: {2: 'b'}}; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: {x: 'b'}}; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 36 unexpected ':'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: ABS(2)}; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 37 unexpected '('."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a', 2: 'b'}::VARIANT; RETURN m; END; "
                + "$$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 57 unexpected 'RETURN'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a', x: 'b'}; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 40 unexpected ':'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {x: 'a', y: 'b'}; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 32 unexpected ':'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {'a': 1, 'b': x}; RETURN m; END; $$",
                "REFUSED SQL compilation error: error line 1 at position 44 | invalid identifier 'X'"},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'} RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 46 unexpected 'm'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'}; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'}::VARIANT:x; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 47 unexpected ':'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'}:x:y; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 40 unexpected ':'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'}::VARIANT + 1; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 48 unexpected '+'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'}:x.y; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 40 unexpected '.'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := 1 + {1: 'a'}; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '1'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: x}; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 35 unexpected '}'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: x.y}::VARIANT; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 35 unexpected '.'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {TRUE: 'a'}::VARIANT; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 35 unexpected ':'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {x: 'a'}; m := 1; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 32 unexpected ':'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a', TRUE: 2}; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 43 unexpected ':'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {}; m := {*}; m := {1: 2}::VARIANT; END; "
                + "$$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected '{'. | syntax"
                + " error line 1 at position 66 unexpected 'END'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {-1: 'a'}; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'}::VARIANT ; m := 2; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 50 unexpected 'm'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'}::VARCHAR(10)::VARCHAR(5); RETURN "
                + "m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 47 unexpected '('."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'}::VARIANT::VARCHAR(5); RETURN m; "
                + "END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 47 unexpected '::'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {x: 1 + 2}; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 32 unexpected ':'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {x: 'a'}:k; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 32 unexpected ':'."},
            {"CREATE OR REPLACE PROCEDURE pc1() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1 +; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ';'."},
            {"CREATE OR REPLACE PROCEDURE pc2() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN (; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected ';'."},
            {"CREATE OR REPLACE PROCEDURE pc3() RETURNS INT LANGUAGE SQL AS $$ BEGIN LET x := 1 1; RETURN "
                + "x; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '1'."},
            {"CREATE OR REPLACE PROCEDURE pc4() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN [1,; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ';'."},
            {"CREATE OR REPLACE PROCEDURE pc5() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN {1: 2}; END;'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '1'. | syntax"
                + " error line 1 at position 17 unexpected '2'."},
        });
    }

    @Test
    public void wordsTakenForNames() {
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {x: 'a'}; m := {1: 'b'}; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 32 unexpected ':'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'} SELECT 1; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: \"x\"}; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 37 unexpected '}'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'}::VARIANT; m := {2: 'b'}; RETURN "
                + "m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 49 unexpected 'm'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'}::VARIANT $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'}::VARIANT; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'} $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: FALSE}; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 39 unexpected '}'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: NULL}::VARIANT; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 50 unexpected 'RETURN'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: CURRENT_DATE}; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 46 unexpected '}'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: (SELECT 1)}; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'} FROM t; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 47 unexpected 'RETURN'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'}::VARIANT; ; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 51 unexpected 'RETURN'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'}::VARIANT; RETURN; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 49 unexpected 'RETURN'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN FOR i IN 1 TO 2 DO m := {1: 'a'}; END FOR; "
                + "RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected '{'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: :m}; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 36 unexpected '}'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'}::VARIANT; EXCEPTION WHEN OTHER "
                + "THEN RETURN 1; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 49 unexpected 'EXCEPTION'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 2 = 3 AND 4 IS NULL}; RETURN m; END; "
                + "$$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: [x, y]}; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 36 unexpected ','."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := [{1: 'a'}]; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '['."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := -{1: 'a'}; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '-'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := NOT {1: 'a'}; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'NOT'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := CASE WHEN TRUE THEN {1: 'a'} END; RETURN "
                + "m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'CASE'. | "
                + "syntax error line 1 at position 40 unexpected 'TRUE'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {'a': {1: 'a'}}; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {'a': 1} || {1: 'a'}; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {'a': 1}::VARIANT || {1: 'a'}::VARIANT; "
                + "RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 48 unexpected '||'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := x.f({1: 'a'}); RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'x'. | syntax"
                + " error line 1 at position 33 unexpected '('."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := 'a' || {1: 'a'}::VARCHAR; RETURN m; END; "
                + "$$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''a''. | "
                + "syntax error line 1 at position 56 unexpected 'RETURN'."},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n OBJECT := {1: 'a'}; n := {2: 'b'}; RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '1'. | syntax"
                + " error line 1 at position 27 unexpected ''a''. | syntax error line 1 at position 38 "
                + "unexpected '{'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'}::VARIANT; RETURN {2: 'b'}; END; "
                + "$$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 49 unexpected 'RETURN'."},
        });
    }

    @Test
    public void otherStatementsOfABlock() {
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT {1: 'a'}; RETURN 1; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '1'. | syntax"
                + " error line 1 at position 18 unexpected ''a''."},
            {"EXECUTE IMMEDIATE $$ BEGIN LET c CURSOR FOR SELECT {1: 'a'}; RETURN 1; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'. | syntax"
                + " error line 1 at position 35 unexpected ''a''."},
            {"EXECUTE IMMEDIATE $$ BEGIN IF ({1: 'a'} IS NULL) THEN RETURN 1; END IF; RETURN 2; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '1'. | syntax"
                + " error line 1 at position 15 unexpected ''a''."},
            {"EXECUTE IMMEDIATE $$ DECLARE x INT; BEGIN SELECT {1: 'a'} INTO :x; RETURN x; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '1'. | syntax"
                + " error line 1 at position 33 unexpected ''a''."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT := {1: 'a'}; BEGIN RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '1'. | syntax"
                + " error line 1 at position 25 unexpected ''a''."},
            {"EXECUTE IMMEDIATE $$ BEGIN LET r RESULTSET := (SELECT {1: 'a'}); RETURN TABLE(r); END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected '1'. | syntax"
                + " error line 1 at position 38 unexpected ''a''."},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN {-1: 'a'}; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '-'. | syntax"
                + " error line 1 at position 19 unexpected ''a''."},
            {"SELECT {-1: 'a'}",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected '-'. | syntax "
                + "error line 1 at position 12 unexpected ''a''."},
            {"EXECUTE IMMEDIATE $$ BEGIN WHILE ({1: 'a'} IS NULL) DO RETURN 1; END WHILE; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '1'. | syntax"
                + " error line 1 at position 18 unexpected ''a''."},
            {"EXECUTE IMMEDIATE $$ BEGIN INSERT INTO nosuch SELECT {1: 'a'}; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '1'. | syntax"
                + " error line 1 at position 37 unexpected ''a''."},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN {1: 'a'}; RETURN {2: 'b'}; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '1'. | syntax"
                + " error line 1 at position 18 unexpected ''a''. | syntax error line 1 at position 32 "
                + "unexpected '2'. | syntax error line 1 at position 35 unexpected ''b''."},
            {"EXECUTE IMMEDIATE $$ BEGIN CALL p({1: 'a'}); END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '1'. | syntax"
                + " error line 1 at position 18 unexpected ''a''."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {1: 'a'}::VARIANT; RETURN 1 +; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 49 unexpected 'RETURN'."},
            {"EXECUTE IMMEDIATE $$ DECLARE m OBJECT; BEGIN m := {-1: 'a'}::VARIANT; RETURN m; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 50 unexpected 'RETURN'."},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n := {1: 'a'}::VARIANT; LET k := {2: 'b'}; RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '1'. | syntax"
                + " error line 1 at position 20 unexpected ''a''. | syntax error line 1 at position 45 "
                + "unexpected '2'. | syntax error line 1 at position 48 unexpected ''b''."},
            {"CREATE OR REPLACE PROCEDURE pm1() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE m OBJECT; BEGIN"
                + " m := {1: 'a'}; RETURN m; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'."},
            {"CALL pm1()",
                "REFUSED SQL compilation error: | Unknown function PM1."},
            {"CREATE OR REPLACE PROCEDURE pm3() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE m OBJECT; BEGIN"
                + " m := {1: 'a'}::VARIANT; RETURN m; END $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '{'. | syntax"
                + " error line 1 at position 49 unexpected 'RETURN'."},
        });
    }
}
