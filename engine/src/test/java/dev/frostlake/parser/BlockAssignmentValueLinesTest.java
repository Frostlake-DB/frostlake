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
 * A block's plain assignment whose value does not parse is refused at the value's first token; the account then
 * reads on to the ';' that ends the statement, or takes the first word that may be a name and refuses the token
 * after it (live-verified). A value that ends before a word standing outside every bracket names that word, and a
 * value in parentheses is read another way.
 */
public class BlockAssignmentValueLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void valuesThatDoNotParse() {
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ DECLARE n INT; BEGIN n := 1 + ; RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '1'."},
            {"EXECUTE IMMEDIATE $$ DECLARE n INT; BEGIN n := 1 + * 2; RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '1'."},
            {"EXECUTE IMMEDIATE $$ DECLARE n INT; BEGIN n := ABS(1,); RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'ABS'."},
            {"EXECUTE IMMEDIATE $$ DECLARE n INT; BEGIN n := 1 2; RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '1'."},
            {"EXECUTE IMMEDIATE $$ DECLARE n INT; BEGIN n := a b; RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'b'. | syntax"
                + " error line 1 at position 32 unexpected 'RETURN'."},
            {"EXECUTE IMMEDIATE $$ DECLARE n INT; BEGIN n := 1 + (2; RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '1'."},
            {"EXECUTE IMMEDIATE $$ DECLARE n INT; BEGIN n := ABS(1 2); RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'ABS'."},
            {"EXECUTE IMMEDIATE $$ DECLARE n INT; BEGIN n := 1 + RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'n'. | syntax"
                + " error line 1 at position 41 unexpected 'END'."},
            {"EXECUTE IMMEDIATE $$ DECLARE n INT; BEGIN n := 1 = ; RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '1'."},
            {"EXECUTE IMMEDIATE $$ DECLARE n INT; BEGIN n := ABS(; RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'ABS'."},
            {"EXECUTE IMMEDIATE $$ DECLARE n INT; BEGIN n := 1 + ABS(,); RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '1'. | syntax"
                + " error line 1 at position 34 unexpected '('."},
            {"EXECUTE IMMEDIATE $$ DECLARE n INT; BEGIN n := x.y.; RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'x'. | syntax"
                + " error line 1 at position 30 unexpected '.'."},
            {"EXECUTE IMMEDIATE $$ DECLARE n INT; BEGIN n := [1,; RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '['."},
            {"EXECUTE IMMEDIATE $$ DECLARE n INT; BEGIN n := 1 IS; RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '1'."},
            {"EXECUTE IMMEDIATE $$ DECLARE n INT; BEGIN n := NOT; RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'NOT'."},
            {"EXECUTE IMMEDIATE $$ DECLARE n INT; BEGIN n := 'a' 'b'; RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ''a''."},
            {"EXECUTE IMMEDIATE $$ DECLARE n INT; BEGIN n := 1 + SELECT 2; RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '1'."},
            {"EXECUTE IMMEDIATE $$ DECLARE n INT; BEGIN n := 1 + CAST(2 AS); RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '1'. | syntax"
                + " error line 1 at position 35 unexpected '('."},
            {"EXECUTE IMMEDIATE $$ DECLARE n INT; BEGIN n := abc(1) def; RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'def'. | "
                + "syntax error line 1 at position 39 unexpected 'RETURN'."},
            {"EXECUTE IMMEDIATE $$ DECLARE n INT; BEGIN n := ABS(a b); RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'ABS'. | "
                + "syntax error line 1 at position 33 unexpected 'b'."},
            {"EXECUTE IMMEDIATE $$ DECLARE n INT; BEGIN n := 1 + a b; RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'b'. | syntax"
                + " error line 1 at position 36 unexpected 'RETURN'."},
            {"EXECUTE IMMEDIATE $$ DECLARE n INT; BEGIN n := a + ; RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'a'."},
            {"EXECUTE IMMEDIATE $$ DECLARE n INT; BEGIN n := a.b c; RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'c'. | syntax"
                + " error line 1 at position 34 unexpected 'RETURN'."},
            {"EXECUTE IMMEDIATE $$ DECLARE n INT; BEGIN n := 1 + x y z; RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'y'. | syntax"
                + " error line 1 at position 35 unexpected 'z'."},
            {"EXECUTE IMMEDIATE $$ DECLARE n INT; BEGIN n := a b c; RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'b'. | syntax"
                + " error line 1 at position 31 unexpected 'c'."},
            {"EXECUTE IMMEDIATE $$ DECLARE n INT; BEGIN n := ABS(1) 2; RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'ABS'."},
            {"EXECUTE IMMEDIATE $$ DECLARE n INT; BEGIN n := a 1; RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'a'."},
            {"EXECUTE IMMEDIATE $$ DECLARE n INT; BEGIN n := a + ; n := 2 + ; RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'a'."},
            {"EXECUTE IMMEDIATE $$ DECLARE n INT; BEGIN n := 1 + x.; RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '1'. | syntax"
                + " error line 1 at position 32 unexpected '.'."},
            {"EXECUTE IMMEDIATE $$ DECLARE n INT; BEGIN n := x + 1 2; RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'x'."},
            {"EXECUTE IMMEDIATE $$ DECLARE n INT; BEGIN IF (TRUE) THEN n := 1 + ; END IF; RETURN n; END; "
                + "$$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected '1'."},
            {"EXECUTE IMMEDIATE $$ DECLARE n INT; BEGIN n := 1 + 2 x; RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'x'. | syntax"
                + " error line 1 at position 36 unexpected 'RETURN'."},
            {"EXECUTE IMMEDIATE $$ DECLARE n INT; BEGIN n := 'a' b; RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'b'. | syntax"
                + " error line 1 at position 34 unexpected 'RETURN'."},
            {"EXECUTE IMMEDIATE $$ DECLARE n INT; BEGIN n := 1 + (SELECT 1 x y); RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '1'. | syntax"
                + " error line 1 at position 43 unexpected 'y'."},
            {"EXECUTE IMMEDIATE $$ DECLARE n INT; BEGIN n := :n + ; RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ':'. | syntax"
                + " error line 1 at position 30 unexpected '+'."},
            {"EXECUTE IMMEDIATE $$ DECLARE n INT; BEGIN n := a IS; RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'a'."},
            {"EXECUTE IMMEDIATE $$ DECLARE n INT; BEGIN n := a b + ; RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'b'. | syntax"
                + " error line 1 at position 31 unexpected '+'."},
        });
    }

    @Test
    public void valuesOverAResultSet() {
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := 1 + "
                + "(SELECT COUNT(*) FROM TABLE(rs)); RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 64 unexpected '1'. | syntax"
                + " error line 1 at position 81 unexpected '('."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := CASE "
                + "WHEN (SELECT COUNT(*) FROM TABLE(rs)) > 0 THEN 1 END; RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 64 unexpected 'CASE'. | "
                + "syntax error line 1 at position 74 unexpected '('."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := (SELECT "
                + "COUNT(*) FROM TABLE(rs)); RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 94 unexpected ')'. | syntax"
                + " error line 1 at position 96 unexpected ';'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := (SELECT "
                + "COUNT(*) FROM TABLE(rs)) + 1; RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 94 unexpected ')'. | syntax"
                + " error line 1 at position 97 unexpected '+'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := 2 * "
                + "(SELECT COUNT(*) FROM TABLE(rs)) + 1; RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 64 unexpected '2'. | syntax"
                + " error line 1 at position 81 unexpected '('."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := "
                + "ABS((SELECT COUNT(*) FROM TABLE(rs))); RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 64 unexpected 'ABS'. | "
                + "syntax error line 1 at position 81 unexpected '('."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := (SELECT "
                + "COUNT(*) FROM TABLE(rs)) + (SELECT 1); RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 94 unexpected ')'. | syntax"
                + " error line 1 at position 97 unexpected '+'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); LET m := 1 + "
                + "(SELECT COUNT(*) FROM TABLE(rs)); RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 73 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 102 unexpected ')'."},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n INT; BEGIN rs := (SELECT 1 AS a); n := -(SELECT"
                + " COUNT(*) FROM TABLE(rs)); RETURN n; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 64 unexpected '-'. | syntax"
                + " error line 1 at position 78 unexpected '('."},
        });
    }
}
