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
 * Two readings of a block (live-verified). A DECLARE section's cursor over {@code SELECT TOP ?} is refused at each
 * such '?' and nowhere else, whatever is declared after it. An EXECUTE IMMEDIATE whose text closes early, so that a
 * word stands after the string, names the word, and then the '(' of the statement's USING.
 */
public class BlockCursorTopBindLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void topBindsAndEarlyClosedTexts() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        assertCells(new String[][] {
            {"DECLARE x INT DEFAULT 1; c CURSOR FOR SELECT TOP ? id FROM fz ORDER BY id; r VARCHAR; BEGIN "
                + "OPEN c USING (x); FETCH c INTO r; RETURN r; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected '?'."},
            {"DECLARE x INT DEFAULT 1; c CURSOR FOR SELECT TOP ? id FROM fz; r VARCHAR; BEGIN OPEN c USING"
                + " (x); FETCH c INTO r; RETURN r; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected '?'."},
            {"DECLARE x INT DEFAULT 1; c CURSOR FOR SELECT TOP ? id, b FROM fz WHERE b; r VARCHAR; BEGIN "
                + "OPEN c USING (x); FETCH c INTO r; RETURN r; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected '?'."},
            {"DECLARE x INT DEFAULT 1; c CURSOR FOR SELECT TOP ? id FROM fz WHERE b ORDER BY id LIMIT 1; r"
                + " VARCHAR; BEGIN OPEN c USING (x); FETCH c INTO r; RETURN r; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected '?'."},
            {"DECLARE x INT DEFAULT 1; c CURSOR FOR SELECT TOP ?; r VARCHAR; BEGIN OPEN c USING (x); FETCH"
                + " c INTO r; RETURN r; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected '?'."},
            {"DECLARE x INT DEFAULT 1; BEGIN LET c CURSOR FOR SELECT TOP ? id FROM fz ORDER BY id; OPEN c "
                + "USING (x); RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 59 unexpected '?'. | syntax"
                + " error line 1 at position 64 unexpected 'FROM'."},
            {"SELECT TOP ? id FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '?'."},
            {"DECLARE c CURSOR FOR SELECT TOP ? id FROM fz; BEGIN RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '?'."},
            {"DECLARE x INT DEFAULT 1; c CURSOR FOR SELECT TOP ? id FROM fz; BEGIN OPEN c USING (x); "
                + "RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected '?'."},
            {"DECLARE c CURSOR FOR SELECT TOP ? id FROM fz; d CURSOR FOR SELECT TOP ? id FROM fz; BEGIN "
                + "RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '?'. | syntax"
                + " error line 1 at position 70 unexpected '?'."},
            {"EXECUTE IMMEDIATE $$ DECLARE x INT DEFAULT 1; c CURSOR FOR SELECT TOP ? id FROM fz; r "
                + "VARCHAR; BEGIN OPEN c USING (x); FETCH c INTO r; RETURN r; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected '?'."},
            {"DECLARE x INT DEFAULT 1; c CURSOR FOR SELECT id FROM fz LIMIT ?; r VARCHAR; BEGIN OPEN c "
                + "USING (x); FETCH c INTO r; RETURN r; END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 82 : SQL "
                + "compilation error: | Invalid row count '?' in limit clause"},
            {"DECLARE x INT DEFAULT 1; c CURSOR FOR SELECT TOP 1 id FROM fz WHERE id = ?; r VARCHAR; BEGIN"
                + " OPEN c USING (x); FETCH c INTO r; RETURN r; END;",
                "null"},
            {"DECLARE x INT DEFAULT 1; c CURSOR FOR SELECT TOP ? id FROM fz; r VARCHAR; s INT; BEGIN "
                + "RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected '?'."},
            {"DECLARE c CURSOR FOR SELECT TOP ? id FROM fz; e EXCEPTION; BEGIN RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '?'."},
            {"DECLARE c CURSOR FOR SELECT TOP :x id FROM fz; r INT; BEGIN RETURN 1; END;",
                "1"},
            {"DECLARE x INT; res RESULTSET; BEGIN res := (EXECUTE IMMEDIATE 'SELECT ? || 'a' v' USING "
                + "(x)); RETURN TABLE(res); END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 76 unexpected 'a'. | syntax"
                + " error line 1 at position 88 unexpected '('."},
            {"DECLARE x INT; res RESULTSET; BEGIN res := (EXECUTE IMMEDIATE 'SELECT 'a' v' USING (x)); "
                + "RETURN TABLE(res); END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 71 unexpected 'a'. | syntax"
                + " error line 1 at position 83 unexpected '('."},
            {"DECLARE x INT; res RESULTSET; BEGIN res := (EXECUTE IMMEDIATE 'SELECT ? || 'a' v'); RETURN "
                + "TABLE(res); END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 76 unexpected 'a'."},
            {"DECLARE x INT; res RESULTSET; BEGIN res := (EXECUTE IMMEDIATE 'SELECT 'a' v w' USING (x)); "
                + "RETURN TABLE(res); END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 71 unexpected 'a'. | syntax"
                + " error line 1 at position 85 unexpected '('."},
            {"DECLARE x INT; res RESULTSET; BEGIN res := (EXECUTE IMMEDIATE 'SELECT 'a'' USING (x)); "
                + "RETURN TABLE(res); END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 71 unexpected 'a'. | syntax"
                + " error line 1 at position 81 unexpected '('."},
            {"BEGIN EXECUTE IMMEDIATE 'SELECT 'a' v'; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'a'."},
            {"DECLARE x INT; BEGIN LET res RESULTSET := (EXECUTE IMMEDIATE 'SELECT 'a' v' USING (x)); "
                + "RETURN TABLE(res); END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 70 unexpected 'a'. | syntax"
                + " error line 1 at position 82 unexpected '('."},
            {"BEGIN EXECUTE IMMEDIATE 'SELECT 'a' v' USING (x); END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'a'. | syntax"
                + " error line 1 at position 45 unexpected '('."},
            {"BEGIN EXECUTE IMMEDIATE 'SELECT 'a''; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'a'."},
            {"BEGIN EXECUTE IMMEDIATE 'SELECT 'a' v w'; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'a'."},
            {"DECLARE x INT; res RESULTSET; BEGIN res := (EXECUTE IMMEDIATE 'SELECT 'a' v'); RETURN "
                + "TABLE(res); END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 71 unexpected 'a'."},
            {"DECLARE x INT; res RESULTSET; BEGIN res := (EXECUTE IMMEDIATE 'SELECT 'a' v' USING (x, x)); "
                + "RETURN TABLE(res); END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 71 unexpected 'a'. | syntax"
                + " error line 1 at position 83 unexpected '('."},
            {"EXECUTE IMMEDIATE 'SELECT 'a' v'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'a'."},
            {"EXECUTE IMMEDIATE 'SELECT 'a' v' USING (1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'a'."},
        });
    }
}
