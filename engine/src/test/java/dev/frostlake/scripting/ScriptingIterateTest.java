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
 * ITERATE, CONTINUE's other spelling in Snowflake Scripting (live-verified): with or without a loop's label it skips
 * to the next iteration of a LOOP, a WHILE, a REPEAT and a FOR over a range or a cursor, from an IF, a CASE, a nested
 * block and an exception handler alike. Outside every loop it is refused as {@code Break used outside of loop}, and
 * naming no loop around it as {@code Label 'NOSUCH' not found}, at the word, on a branch that never runs too; a
 * procedure or a function holding one is created and refused when called. Standing alone outside a block it is a
 * syntax error at itself, and the word stays an ordinary name everywhere else: a column, a table, an alias, a
 * variable and a loop's label.
 */
public class ScriptingIterateTest extends MeasuredCellsTestSupport {

    @Test
    public void iterateInLoops() {
        engine.execute("CREATE OR REPLACE TABLE it_t (n INT)");
        engine.execute("INSERT INTO it_t VALUES (1),(2),(3),(4)");
        engine.execute("CREATE OR REPLACE PROCEDURE it_p() RETURNS INT LANGUAGE SQL AS $$\nBEGIN\n  ITERATE;\n  "
            + "RETURN 1;\nEND\n$$");
        engine.execute("CREATE OR REPLACE TABLE iterate (iterate INT)");
        engine.execute("CREATE OR REPLACE FUNCTION it_f() RETURNS INT AS $$\nBEGIN\n  FOR i IN 1 TO 3 DO\n    "
            + "ITERATE;\n  END FOR;\n  RETURN 7;\nEND\n$$");
        engine.execute("CREATE OR REPLACE FUNCTION it_f2() RETURNS INT AS $$\nBEGIN\n  ITERATE;\n  RETURN "
            + "7;\nEND\n$$");
        engine.execute("CREATE OR REPLACE PROCEDURE it_p2() RETURNS INT LANGUAGE SQL AS $$\nDECLARE s INT DEFAULT "
            + "0;\nBEGIN\n  FOR i IN 1 TO 4 DO\n    IF (i = 3) THEN ITERATE; END IF;\n    s := s + "
            + "i;\n  END FOR;\n  RETURN s;\nEND\n$$");
        engine.execute("CREATE OR REPLACE PROCEDURE it_p3() RETURNS INT LANGUAGE SQL AS\nDECLARE s INT DEFAULT "
            + "0;\nBEGIN\n  WHILE (s < 10) LOOP\n    s := s + 3;\n    ITERATE;\n  END LOOP;\n  RETURN"
            + " s;\nEND");
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$\nDECLARE s INT DEFAULT 0;\nBEGIN\n  FOR i IN 1 TO 5 DO\n    IF (i = 2) "
                + "THEN ITERATE; END IF;\n    s := s + i;\n  END FOR;\n  RETURN s;\nEND;\n$$",
                "13"},
            {"DECLARE s INT DEFAULT 0;\nBEGIN\n  FOR i IN 1 TO 5 DO\n    IF (i = 2) THEN ITERATE; END "
                + "IF;\n    s := s + i;\n  END FOR;\n  RETURN s;\nEND",
                "13"},
            {"DECLARE s INT DEFAULT 0; i INT DEFAULT 0;\nBEGIN\n  WHILE (i < 5) DO\n    i := i + 1;\n    "
                + "IF (i = 3) THEN ITERATE; END IF;\n    s := s + i;\n  END WHILE;\n  RETURN s;\nEND",
                "12"},
            {"DECLARE s INT DEFAULT 0; i INT DEFAULT 0;\nBEGIN\n  LOOP\n    i := i + 1;\n    IF (i > 5) "
                + "THEN BREAK; END IF;\n    IF (i = 4) THEN ITERATE; END IF;\n    s := s + i;\n  END LOOP;\n  "
                + "RETURN s;\nEND",
                "11"},
            {"DECLARE s INT DEFAULT 0; i INT DEFAULT 0;\nBEGIN\n  REPEAT\n    i := i + 1;\n    IF (i = 2) "
                + "THEN ITERATE; END IF;\n    s := s + i;\n  UNTIL (i >= 5)\n  END REPEAT;\n  RETURN s;\nEND",
                "13"},
            {"DECLARE s INT DEFAULT 0; c CURSOR FOR SELECT n FROM it_t ORDER BY n;\nBEGIN\n  FOR r IN c "
                + "DO\n    IF (r.n = 3) THEN ITERATE; END IF;\n    s := s + r.n;\n  END FOR;\n  RETURN s;\nEND",
                "7"},
            {"DECLARE s INT DEFAULT 0;\nBEGIN\n  FOR i IN 1 TO 3 DO\n    FOR j IN 1 TO 3 DO\n      IF (j ="
                + " 2) THEN ITERATE outer_l; END IF;\n      s := s + 10*i + j;\n    END FOR inner_l;\n  END FOR"
                + " outer_l;\n  RETURN s;\nEND",
                "63"},
            {"DECLARE s INT DEFAULT 0;\nBEGIN\n  FOR i IN 1 TO 3 DO\n    FOR j IN 1 TO 3 DO\n      IF (j ="
                + " 2) THEN ITERATE inner_l; END IF;\n      s := s + 10*i + j;\n    END FOR inner_l;\n  END FOR"
                + " outer_l;\n  RETURN s;\nEND",
                "132"},
            {"DECLARE s INT DEFAULT 0;\nBEGIN\n  FOR i IN 1 TO 5 DO\n    IF (i = 2) THEN iterate; END "
                + "IF;\n    s := s + i;\n  END FOR;\n  RETURN s;\nEND",
                "13"},
            {"BEGIN\n  ITERATE;\n  RETURN 1;\nEND",
                "REFUSED SQL compilation error: error line 2 at position 2 |  Break used outside of loop"},
            {"BEGIN\n  IF (1 = 2) THEN ITERATE; END IF;\n  RETURN 1;\nEND",
                "REFUSED SQL compilation error: error line 2 at position 18 |  Break used outside of loop"},
            {"BEGIN\n  ITERATE lbl;\n  RETURN 1;\nEND",
                "REFUSED SQL compilation error: error line 2 at position 2 |  Break used outside of loop"},
            {"BEGIN\n  FOR i IN 1 TO 3 DO\n    ITERATE nosuch;\n  END FOR lp;\n  RETURN 1;\nEND",
                "REFUSED SQL compilation error: error line 3 at position 4 |  Label 'NOSUCH' not found"},
            {"BEGIN\n  FOR i IN 1 TO 3 DO\n    IF (1 = 2) THEN ITERATE nosuch; END IF;\n  END FOR lp;\n  "
                + "RETURN 1;\nEND",
                "REFUSED SQL compilation error: error line 3 at position 20 |  Label 'NOSUCH' not found"},
            {"BEGIN\n  RETURN 1;\n  ITERATE;\nEND",
                "REFUSED SQL compilation error: error line 3 at position 2 |  Break used outside of loop"},
            {"BEGIN\n  FOR i IN 1 TO 3 DO\n    ITERATE\n  END FOR;\n  RETURN 1;\nEND",
                "REFUSED SQL compilation error: | syntax error line 4 at position 2 unexpected 'END'."},
            {"ITERATE",
                "REFUSED SQL compilation error: | syntax error line 1 at position 0 unexpected 'ITERATE'."},
            {"ITERATE;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 0 unexpected 'ITERATE'."},
            {"BEGIN\n  FOR i IN 1 TO 3 DO\n    ITERATE a b;\n  END FOR;\n  RETURN 1;\nEND",
                "REFUSED SQL compilation error: | syntax error line 3 at position 14 unexpected 'b'. | syntax"
                + " error line 4 at position 6 unexpected 'FOR'."},
            {"BEGIN\n  FOR i IN 1 TO 3 DO\n    ITERATE WHEN i = 2;\n  END FOR;\n  RETURN 1;\nEND",
                "REFUSED SQL compilation error: | syntax error line 3 at position 17 unexpected 'i'. | syntax"
                + " error line 3 at position 22 unexpected ';'."},
            {"DECLARE s INT DEFAULT 0;\nBEGIN\n  FOR i IN 1 TO 5 DO\n    IF (i = 2) THEN ITERATE \"LP\"; "
                + "END IF;\n    s := s + i;\n  END FOR lp;\n  RETURN s;\nEND",
                "13"},
            {"DECLARE s INT DEFAULT 0;\nBEGIN\n  FOR i IN 1 TO 5 DO\n    IF (i = 2) THEN ITERATE \"lp\"; "
                + "END IF;\n    s := s + i;\n  END FOR lp;\n  RETURN s;\nEND",
                "REFUSED SQL compilation error: error line 4 at position 20 |  Label 'lp' not found"},
            {"DECLARE s INT DEFAULT 0;\nBEGIN\n  FOR i IN 1 TO 4 DO\n    BEGIN\n      IF (i = 2) THEN "
                + "SELECT 1/0; END IF;\n      s := s + i;\n    EXCEPTION WHEN OTHER THEN ITERATE;\n    END;\n  "
                + "END FOR;\n  RETURN s;\nEND",
                "8"},
            {"DECLARE s INT DEFAULT 0;\nBEGIN\n  FOR i IN 1 TO 4 DO\n    BEGIN\n      IF (i = 2) THEN "
                + "ITERATE; END IF;\n      s := s + i;\n    END;\n  END FOR;\n  RETURN s;\nEND",
                "8"},
            {"CALL it_p()",
                "REFUSED SQL compilation error: error line 3 at position 2 |  Break used outside of loop"},
            {"SELECT iterate FROM (SELECT 1 AS iterate)",
                "1"},
            {"SELECT iterate.iterate FROM iterate",
                ""},
            {"SELECT iterate.x FROM (SELECT 1 x) iterate",
                "1"},
            {"DECLARE iterate INT DEFAULT 3;\nBEGIN\n  RETURN iterate;\nEND",
                "3"},
            {"DECLARE iterate INT DEFAULT 3;\nBEGIN\n  iterate := 4;\n  RETURN iterate;\nEND",
                "4"},
            {"BEGIN\n  LET iterate := 5;\n  RETURN iterate;\nEND",
                "5"},
            {"DECLARE s INT DEFAULT 0;\nBEGIN\n  FOR i IN 1 TO 3 DO\n    IF (i = 2) THEN ITERATE iterate; "
                + "END IF;\n    s := s + i;\n  END FOR iterate;\n  RETURN s;\nEND",
                "4"},
            {"BEGIN\n  FOR i IN 1 TO 3 DO\n    CONTINUE a b;\n  END FOR;\n  RETURN 1;\nEND",
                "REFUSED SQL compilation error: | syntax error line 3 at position 15 unexpected 'b'. | syntax"
                + " error line 4 at position 6 unexpected 'FOR'."},
            {"BEGIN\n  FOR i IN 1 TO 3 DO\n    CONTINUE WHEN i = 2;\n  END FOR;\n  RETURN 1;\nEND",
                "REFUSED SQL compilation error: | syntax error line 3 at position 18 unexpected 'i'. | syntax"
                + " error line 3 at position 23 unexpected ';'."},
            {"BEGIN\n  FOR i IN 1 TO 3 DO\n    ITERATE lp WHEN i = 2;\n  END FOR lp;\n  RETURN 1;\nEND",
                "REFUSED SQL compilation error: | syntax error line 3 at position 20 unexpected 'i'. | syntax"
                + " error line 3 at position 25 unexpected ';'."},
            {"SELECT 1 iterate",
                "1"},
            {"SELECT 1 x iterate",
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'iterate'."},
            {"BEGIN\n  ITERATE;\nEND",
                "REFUSED SQL compilation error: error line 2 at position 2 |  Break used outside of loop"},
            {"SELECT it_f()",
                "7"},
            {"SELECT it_f2()",
                "REFUSED SQL compilation error: error line 3 at position 2 |  Break used outside of loop"},
            {"CALL it_p2()",
                "7"},
            {"CALL it_p3()",
                "12"},
            {"DECLARE s INT DEFAULT 0;\nBEGIN\n  FOR i IN 1 TO 4 DO\n    CASE WHEN i % 2 = 0 THEN ITERATE;"
                + " ELSE s := s + i; END CASE;\n  END FOR;\n  RETURN s;\nEND",
                "4"},
        });
    }
}
