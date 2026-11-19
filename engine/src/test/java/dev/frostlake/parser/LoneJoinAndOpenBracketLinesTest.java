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
 * Words and brackets the account reads its own way (live-verified). A JOIN standing where a table is named tells
 * the token after it twice. A block's RETURN or assignment whose value opens a bracket and holds nothing names the
 * token after the brackets. A DESCRIBE whose bracket opens with a word that is no name names the word, and not the
 * token after it.
 */
public class LoneJoinAndOpenBracketLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void wordsAndBrackets() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        assertCells(new String[][] {
            {"BEGIN SELECT 1 FROM FZ LIMIT CAST(1, 2); END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'CAST'. | "
                + "syntax error line 1 at position 29 unexpected 'CAST'."},
            {"SELECT 1 FROM FZ LIMIT CAST(1, 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'CAST'. | "
                + "syntax error line 1 at position 23 unexpected 'CAST'."},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT TOP CAST(1, 2) 1 FROM FZ; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '('. | syntax"
                + " error line 1 at position 40 unexpected 'END'."},
            {"CREATE OR REPLACE PROCEDURE pk(case INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN SELECT case "
                + "INTO :case; RETURN 1; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'INTO'. | "
                + "syntax error line 1 at position 31 unexpected 'RETURN'."},
            {"CREATE OR REPLACE PROCEDURE pk2(x INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN SELECT case INTO"
                + " :x; RETURN 1; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'INTO'. | "
                + "syntax error line 1 at position 28 unexpected 'RETURN'."},
            {"EXECUTE IMMEDIATE $$ DECLARE x INT; BEGIN SELECT case INTO :x; RETURN 1; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'INTO'. | "
                + "syntax error line 1 at position 43 unexpected 'RETURN'."},
            {"CREATE OR REPLACE PROCEDURE pk6(x INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN SELECT case, 1 "
                + "INTO :x, :x; RETURN 1; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected ','. | syntax"
                + " error line 1 at position 29 unexpected ','."},
            {"CREATE OR REPLACE PROCEDURE pk7(x INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN SELECT cast INTO"
                + " :x; RETURN 1; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'INTO'. | "
                + "syntax error line 1 at position 28 unexpected 'RETURN'."},
            {"CREATE OR REPLACE PROCEDURE pk8(x INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN SELECT case INTO"
                + " :x; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'INTO'. | "
                + "syntax error line 1 at position 28 unexpected 'END'."},
            {"CREATE OR REPLACE PROCEDURE p1() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN (; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected ';'."},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN (; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected ';'."},
            {"BEGIN RETURN (; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected ';'."},
            {"CREATE OR REPLACE PROCEDURE p1() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN (1 + ; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ';'."},
            {"CREATE OR REPLACE PROCEDURE p1() RETURNS INT LANGUAGE SQL AS $$ BEGIN LET x := (; RETURN x; "
                + "END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ';'."},
            {"CREATE OR REPLACE PROCEDURE p1() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN ((; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ';'. | syntax"
                + " error line 1 at position 23 unexpected '<EOF>'."},
            {"CREATE OR REPLACE PROCEDURE p1() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN (); END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected ')'."},
            {"CREATE OR REPLACE PROCEDURE p1() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN ( ; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ';'."},
            {"CREATE OR REPLACE PROCEDURE p1() RETURNS INT LANGUAGE SQL AS $$ BEGIN IF ((;) THEN RETURN 1;"
                + " END IF; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected ';'."},
            {"CREATE OR REPLACE PROCEDURE p1() RETURNS INT LANGUAGE SQL AS $$ DECLARE x INT; BEGIN x := (;"
                + " RETURN x; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ';'."},
            {"DESCRIBE TABLE fz (TRIGGER)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'TRIGGER'."},
            {"DESCRIBE TABLE fz (TRIGGER, x)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'TRIGGER'. | "
                + "syntax error line 1 at position 29 unexpected ')'."},
            {"DESCRIBE TABLE fz (ON)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'ON'."},
            {"DESCRIBE TABLE fz (",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '<EOF>'."},
            {"DESC TABLE fz (TRIGGER)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'TRIGGER'."},
            {"DESCRIBE VIEW fz (TRIGGER)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'TRIGGER'."},
            {"WITH JOIN AS (SELECT 1 a) SELECT * FROM JOIN",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected '<EOF>'. | "
                + "syntax error line 1 at position 44 unexpected '<EOF>'."},
            {"WITH JOIN AS (SELECT 1 a) SELECT * FROM JOIN j",
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'j'."},
            {"WITH x AS (SELECT 1 a) SELECT * FROM JOIN",
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected '<EOF>'. | "
                + "syntax error line 1 at position 41 unexpected '<EOF>'."},
            {"SELECT * FROM JOIN",
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '<EOF>'. | "
                + "syntax error line 1 at position 18 unexpected '<EOF>'."},
            {"WITH JOIN AS (SELECT 1 a) SELECT * FROM JOIN WHERE a = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'WHERE'. | "
                + "syntax error line 1 at position 45 unexpected 'WHERE'."},
            {"WITH JOIN AS (SELECT 1 a) SELECT a FROM JOIN",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected '<EOF>'. | "
                + "syntax error line 1 at position 44 unexpected '<EOF>'."},
            {"WITH LEFT AS (SELECT 1 a) SELECT * FROM LEFT",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected '<EOF>'."},
            {"WITH JOIN AS (SELECT 1 a), y AS (SELECT 2 b) SELECT * FROM JOIN",
                "REFUSED SQL compilation error: | syntax error line 1 at position 63 unexpected '<EOF>'. | "
                + "syntax error line 1 at position 63 unexpected '<EOF>'."},
            {"WITH y AS (SELECT 2 b), JOIN AS (SELECT 1 a) SELECT * FROM JOIN",
                "REFUSED SQL compilation error: | syntax error line 1 at position 63 unexpected '<EOF>'. | "
                + "syntax error line 1 at position 63 unexpected '<EOF>'."},
            {"SELECT * FROM fz, JOIN",
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '<EOF>'. | "
                + "syntax error line 1 at position 22 unexpected '<EOF>'."},
            {"WITH JOIN AS (SELECT 1 a) SELECT * FROM fz JOIN JOIN",
                "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected '<EOF>'. | "
                + "syntax error line 1 at position 52 unexpected '<EOF>'."},
            {"SELECT * FROM fz JOIN",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '<EOF>'."},
            {"SELECT * FROM JOIN, fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected ','. | syntax"
                + " error line 1 at position 18 unexpected ','."},
            {"SELECT * FROM JOIN ORDER BY 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'ORDER'. | "
                + "syntax error line 1 at position 19 unexpected 'ORDER'."},
            {"SELECT * FROM (SELECT 1) JOIN",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '<EOF>'."},
            {"SELECT * FROM JOIN JOIN fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'JOIN'. | "
                + "syntax error line 1 at position 24 unexpected 'fz'."},
            {"SELECT * FROM JOIN AS j",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'AS'. | "
                + "syntax error line 1 at position 19 unexpected 'AS'."},
            {"SELECT * FROM JOIN ON",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'ON'. | "
                + "syntax error line 1 at position 19 unexpected 'ON'."},
            {"SELECT * FROM fz, JOIN WHERE 1 = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'WHERE'. | "
                + "syntax error line 1 at position 23 unexpected 'WHERE'."},
            {"SELECT * FROM JOIN LIMIT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'LIMIT'. | "
                + "syntax error line 1 at position 25 unexpected '1'."},
            {"SELECT * FROM JOIN GROUP BY 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'GROUP'. | "
                + "syntax error line 1 at position 19 unexpected 'GROUP'."},
            {"SELECT * FROM fz LEFT JOIN JOIN",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '<EOF>'. | "
                + "syntax error line 1 at position 31 unexpected '<EOF>'."},
            {"SELECT * FROM JOIN )",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ')'. | syntax"
                + " error line 1 at position 19 unexpected ')'."},
            {"SELECT (SELECT 1 FROM JOIN)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 26 unexpected ')'. | syntax error line 1 at position 26 "
                + "unexpected ')'."},
            {"SELECT * FROM JOIN;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected ';'. | syntax"
                + " error line 1 at position 18 unexpected ';'."},
            {"SELECT * FROM fz JOIN WHERE 1 = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'WHERE'."},
            {"SELECT * FROM JOIN UNION SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'UNION'. | "
                + "syntax error line 1 at position 19 unexpected 'UNION'."},
            {"SELECT * FROM fz JOIN JOIN ON 1 = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'ON'. | "
                + "syntax error line 1 at position 27 unexpected 'ON'."},
            {"DESCRIBE TABLE fz (TRIGGER x)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'TRIGGER'. | "
                + "syntax error line 1 at position 28 unexpected ')'."},
            {"DESCRIBE TABLE fz (x TRIGGER)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'TRIGGER'."},
            {"DESCRIBE TABLE fz (x,)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected ')'."},
            {"DESCRIBE TABLE fz (QUALIFY)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'QUALIFY'."},
            {"DESCRIBE TABLE fz (TRIGGER) x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'TRIGGER'. | "
                + "syntax error line 1 at position 29 unexpected '<EOF>'."},
            {"DESCRIBE TABLE fz (x y)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'y'."},
            {"DESCRIBE TABLE fz (1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '1'."},
            {"BEGIN RETURN ((; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected ';'. | syntax"
                + " error line 1 at position 21 unexpected '<EOF>'."},
            {"BEGIN RETURN (); END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected ')'."},
            {"BEGIN RETURN ( ; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected ';'."},
            {"DECLARE x INT; BEGIN x := (; RETURN x; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ';'."},
            {"BEGIN RETURN (",
                "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '<EOF>'."},
            {"BEGIN RETURN (;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected ';'. | syntax"
                + " error line 1 at position 15 unexpected '<EOF>'."},
            {"BEGIN RETURN (; RETURN 2; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected ';'."},
            {"BEGIN IF (TRUE) THEN RETURN (; END IF; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ';'."},
            {"BEGIN RETURN (; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected ';'."},
            {"DECLARE x INT; BEGIN x := (); RETURN x; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ')'."},
            {"BEGIN RETURN ((); END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected ')'. | syntax"
                + " error line 1 at position 16 unexpected ';'."},
            {"BEGIN RETURN (1,; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ';'."},
            {"SELECT 1 FROM FZ LIMIT 1 OFFSET CAST(1, 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'CAST'."},
        });
    }
}
