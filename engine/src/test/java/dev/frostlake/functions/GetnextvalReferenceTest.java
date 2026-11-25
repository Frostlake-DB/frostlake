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
 * Names that read a sequence without NEXTVAL, GETNEXTVAL's argument and column list, a view over GETNEXTVAL read
 * after its sequence is gone, and a BREAK or a CONTINUE run into an END (live-verified): a name that no relation
 * answers but a sequence does is {@code Invalid reference to sequence object without nextval: PUBLIC.SQ9.} wherever
 * it stands, a {@code seq.NEXTVAL} handed to GETNEXTVAL is an unexpected argument placed nowhere, a column list
 * longer than GETNEXTVAL's one column is refused before any name is resolved, a view's refusal is placed in its
 * CREATE statement, and END FOR after such an END is refused at FOR, which no label can be.
 */
public class GetnextvalReferenceTest extends MeasuredCellsTestSupport {

    @Test
    public void sequenceNamesWithoutNextval() {
        engine.execute("CREATE OR REPLACE SEQUENCE s");
        engine.execute("CREATE OR REPLACE SEQUENCE gs5");
        engine.execute("CREATE OR REPLACE TABLE gt (a INT)");
        engine.execute("INSERT INTO gt VALUES (1)");
        engine.execute("CREATE OR REPLACE TABLE st (s INT)");
        engine.execute("INSERT INTO st VALUES (7)");
        engine.execute("CREATE OR REPLACE SEQUENCE sq9");
        assertCells(new String[][] {
            {"SELECT test_schema.day",
                "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'TEST_SCHEMA.DAY'"},
            {"SELECT test_schema.sq9",
                "REFUSED SQL compilation error: | Invalid reference to sequence object without nextval: "
                + "TEST_SCHEMA.SQ9."},
            {"SELECT \"TEST_SCHEMA\".\"SQ9\"",
                "REFUSED SQL compilation error: | Invalid reference to sequence object without nextval: "
                + "TEST_SCHEMA.SQ9."},
            {"SELECT * FROM TABLE(GETNEXTVAL(test_schema.s.nextval))",
                "REFUSED SQL compilation error: error line 0 at position -1 | invalid argument for function "
                + "[TEST_SCHEMA.S.NEXTVAL] unexpected argument [TEST_SCHEMA.S.NEXTVAL] at position 0,"},
            {"SELECT GETNEXTVAL(s)",
                "REFUSED SQL compilation error: | Invalid reference to sequence object without nextval: S."},
            {"SELECT s",
                "REFUSED SQL compilation error: | Invalid reference to sequence object without nextval: S."},
            {"SELECT gs5",
                "REFUSED SQL compilation error: | Invalid reference to sequence object without nextval: GS5."},
            {"SELECT ABS(gs5)",
                "REFUSED SQL compilation error: | Invalid reference to sequence object without nextval: GS5."},
            {"SELECT s FROM gt",
                "REFUSED SQL compilation error: | Invalid reference to sequence object without nextval: S."},
            {"SELECT a FROM gt WHERE s = 1",
                "REFUSED SQL compilation error: | Invalid reference to sequence object without nextval: S."},
            {"SELECT \"S\"",
                "REFUSED SQL compilation error: | Invalid reference to sequence object without nextval: S."},
            {"SELECT \"s\"",
                "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier '\"s\"'"},
            {"SELECT * FROM TABLE(GETNEXTVAL(s.nextval))",
                "REFUSED SQL compilation error: error line 0 at position -1 | invalid argument for function "
                + "[S.NEXTVAL] unexpected argument [S.NEXTVAL] at position 0,"},
            {"SELECT * FROM TABLE(GETNEXTVAL(gs5.nextval))",
                "REFUSED SQL compilation error: error line 0 at position -1 | invalid argument for function "
                + "[GS5.NEXTVAL] unexpected argument [GS5.NEXTVAL] at position 0,"},
            {"SELECT * FROM TABLE(GETNEXTVAL(nosuch))",
                "REFUSED SQL compilation error: error line 1 at position 31 | invalid identifier 'NOSUCH'"},
            {"SELECT v FROM TABLE(GETNEXTVAL(gs5)) s(v, w)",
                "REFUSED SQL compilation error: | Mismatch between number of columns produced by 'S' and the "
                + "number of aliases specified."},
            {"SELECT w FROM TABLE(GETNEXTVAL(gs5)) s(v, w)",
                "REFUSED SQL compilation error: | Mismatch between number of columns produced by 'S' and the "
                + "number of aliases specified."},
            {"SELECT zz FROM TABLE(GETNEXTVAL(gs5)) s(v, w)",
                "REFUSED SQL compilation error: | Mismatch between number of columns produced by 'S' and the "
                + "number of aliases specified."},
            {"SELECT a FROM TABLE(FLATTEN([1])) f(a,b,c,d,e,f,g)",
                "REFUSED SQL compilation error: | Mismatch between number of columns produced by 'F' and the "
                + "number of aliases specified."},
            {"SELECT zz FROM TABLE(FLATTEN([1])) f(a,b,c,d,e,f,g)",
                "REFUSED SQL compilation error: | Mismatch between number of columns produced by 'F' and the "
                + "number of aliases specified."},
            {"SELECT v FROM gt, TABLE(GETNEXTVAL(gs5)) s(v, w)",
                "REFUSED SQL compilation error: | Mismatch between number of columns produced by 'S' and the "
                + "number of aliases specified."},
            {"SELECT s FROM st",
                "7"},
            {"SELECT s + 1 FROM st",
                "8"},
            {"SELECT * FROM TABLE(GETNEXTVAL(nosuch.nextval))",
                "REFUSED SQL compilation error: error line 1 at position 31 | invalid identifier "
                + "'NOSUCH.NEXTVAL'"},
            {"SELECT * FROM TABLE(GETNEXTVAL(s.foo))",
                "REFUSED SQL compilation error: error line 1 at position 31 | invalid identifier 'S.FOO'"},
            {"SELECT * FROM TABLE(GETNEXTVAL(s.NextVal))",
                "REFUSED SQL compilation error: error line 0 at position -1 | invalid argument for function "
                + "[S.NEXTVAL] unexpected argument [S.NEXTVAL] at position 0,"},
            {"SELECT * FROM TABLE(GETNEXTVAL(SEQ => s.nextval))",
                "REFUSED SQL compilation error: error line 0 at position -1 | invalid argument for function "
                + "[S.NEXTVAL] unexpected argument [S.NEXTVAL] at position 0,"},
            {"SELECT * FROM gt, TABLE(GETNEXTVAL(s.nextval))",
                "REFUSED SQL compilation error: error line 0 at position -1 | invalid argument for function "
                + "[S.NEXTVAL] unexpected argument [S.NEXTVAL] at position 0,"},
            {"SELECT a FROM gt ORDER BY s",
                "REFUSED SQL compilation error: | Invalid reference to sequence object without nextval: S."},
            {"SELECT COUNT(*) FROM gt GROUP BY s",
                "REFUSED SQL compilation error: | Invalid reference to sequence object without nextval: S."},
            {"INSERT INTO gt VALUES (s)",
                "REFUSED SQL compilation error: | Invalid reference to sequence object without nextval: S."},
            {"UPDATE gt SET a = s",
                "REFUSED SQL compilation error: | Invalid reference to sequence object without nextval: S."},
            {"SELECT (SELECT s)",
                "REFUSED SQL compilation error: | Invalid reference to sequence object without nextval: S."},
            {"SELECT zz, s",
                "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'ZZ'"},
            {"SELECT GETNEXTVAL(s)",
                "REFUSED SQL compilation error: | Invalid reference to sequence object without nextval: S."},
            {"WITH c AS (SELECT 1 x) SELECT s FROM c",
                "REFUSED SQL compilation error: | Invalid reference to sequence object without nextval: S."},
            {"SELECT s FROM gt JOIN st ON TRUE",
                "7"},
            {"SELECT s.currval",
                "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'S.CURRVAL'"},
            {"SELECT a FROM gt WHERE a = s.nextval",
                "1"},
            {"BEGIN\n  RETURN 1 1;\nEND",
                "REFUSED SQL compilation error: | syntax error line 2 at position 11 unexpected '1'."},
            {"SELECT * FROM VALUES (sq9)",
                "REFUSED SQL compilation error: | Invalid reference to sequence object without nextval: SQ9."},
            {"SELECT x.s FROM gt x",
                "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'X.S'"},
            {"INSERT INTO gt VALUES (sq9)",
                "REFUSED SQL compilation error: | Invalid reference to sequence object without nextval: SQ9."},
            {"INSERT INTO gt SELECT sq9",
                "REFUSED SQL compilation error: | Invalid reference to sequence object without nextval: SQ9."},
            {"UPDATE gt SET a = sq9",
                "REFUSED SQL compilation error: | Invalid reference to sequence object without nextval: SQ9."},
            {"SELECT gt.sq9 FROM gt",
                "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'GT.SQ9'"},
            {"SELECT \"sq9\"",
                "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier '\"sq9\"'"},
            {"SELECT sq9.nextval, sq9",
                "REFUSED SQL compilation error: | Invalid reference to sequence object without nextval: SQ9."},
            {"DELETE FROM gt WHERE a = sq9",
                "REFUSED SQL compilation error: | Invalid reference to sequence object without nextval: SQ9."},
        });
    }

    @Test
    public void viewOverDroppedSequence() {
        engine.execute("CREATE OR REPLACE SEQUENCE gs5");
        engine.execute("CREATE OR REPLACE TABLE gt (a INT)");
        engine.execute("CREATE OR REPLACE VIEW gnv AS SELECT nextval FROM TABLE(GETNEXTVAL(gs5))");
        engine.execute("CREATE OR REPLACE VIEW gnv2 AS SELECT s.nextval AS n FROM gt, TABLE(GETNEXTVAL(gs5)) s");
        engine.execute("CREATE OR REPLACE VIEW gnv3 AS SELECT gs5.nextval AS n");
        engine.execute("CREATE OR REPLACE VIEW gnv5 AS\n  SELECT nextval\n  FROM TABLE(GETNEXTVAL(gs5))");
        engine.execute("DROP SEQUENCE gs5");
        assertCells(new String[][] {
            {"SELECT * FROM gnv",
                "REFUSED SQL compilation error: error line 1 at position 67 | invalid identifier 'GS5'"},
            {"SELECT * FROM gnv2",
                "REFUSED SQL compilation error: error line 1 at position 79 | invalid identifier 'GS5'"},
            {"SELECT * FROM gnv3",
                "REFUSED SQL compilation error: error line 1 at position 38 | invalid identifier "
                + "'GS5.NEXTVAL'"},
            {"SELECT * FROM gnv5",
                "REFUSED SQL compilation error: error line 3 at position 24 | invalid identifier 'GS5'"},
        });
    }

    @Test
    public void blockedLoopEnd() {
        assertCells(new String[][] {
            {"BEGIN\n  FOR i IN 1 TO 3 DO\n    BREAK END;\n  END FOR;\n  RETURN 1;\nEND",
                "REFUSED SQL compilation error: | syntax error line 3 at position 10 unexpected 'END'. | "
                + "syntax error line 3 at position 13 unexpected ';'. | syntax error line 4 at position 6 "
                + "unexpected 'FOR'."},
            {"BEGIN\n  FOR i IN 1 TO 3 DO\n    CONTINUE END;\n  END FOR;\n  RETURN 1;\nEND",
                "REFUSED SQL compilation error: | syntax error line 3 at position 13 unexpected 'END'. | "
                + "syntax error line 3 at position 16 unexpected ';'. | syntax error line 4 at position 6 "
                + "unexpected 'FOR'."},
            {"BEGIN\n  FOR i IN 1 TO 3 DO\n    ITERATE END;\n  END FOR;\n  RETURN 1;\nEND",
                "REFUSED SQL compilation error: | syntax error line 3 at position 12 unexpected 'END'. | "
                + "syntax error line 3 at position 15 unexpected ';'. | syntax error line 4 at position 6 "
                + "unexpected 'FOR'."},
            {"BEGIN\n  WHILE (TRUE) DO\n    BREAK END;\n  END WHILE;\n  RETURN 1;\nEND",
                "REFUSED SQL compilation error: | syntax error line 3 at position 10 unexpected 'END'. | "
                + "syntax error line 3 at position 13 unexpected ';'. | syntax error line 5 at position 2 "
                + "unexpected 'RETURN'."},
            {"BEGIN\n  LOOP\n    BREAK END;\n  END LOOP;\n  RETURN 1;\nEND",
                "REFUSED SQL compilation error: | syntax error line 3 at position 10 unexpected 'END'. | "
                + "syntax error line 3 at position 13 unexpected ';'. | syntax error line 5 at position 2 "
                + "unexpected 'RETURN'."},
            {"BEGIN\n  FOR i IN 1 TO 3 DO\n    BREAK END;\n  END FOR;\nEND",
                "REFUSED SQL compilation error: | syntax error line 3 at position 10 unexpected 'END'. | "
                + "syntax error line 3 at position 13 unexpected ';'. | syntax error line 4 at position 6 "
                + "unexpected 'FOR'."},
            {"BEGIN\n  FOR i IN 1 TO 3 DO\n    BREAK END;\n    LET x := 1;\n  END FOR;\n  RETURN 1;\nEND",
                "REFUSED SQL compilation error: | syntax error line 3 at position 10 unexpected 'END'. | "
                + "syntax error line 3 at position 13 unexpected ';'. | syntax error line 5 at position 6 "
                + "unexpected 'FOR'."},
            {"BEGIN\n  FOR i IN 1 TO 3 DO\n    BREAK foo;\n  END FOR;\n  RETURN 1;\nEND",
                "REFUSED SQL compilation error: error line 3 at position 4 |  Label 'FOO' not found"},
        });
    }

    /** EXPLAIN's Result over GETNEXTVAL, and a view's column over it, NOT NULL where a table's made from it is not. */
    @Test
    public void planAndShape() {
        engine.execute("CREATE OR REPLACE SEQUENCE gs5");
        engine.execute("CREATE OR REPLACE TABLE gt (a INT)");
        engine.execute("INSERT INTO gt VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE VIEW gnv AS SELECT nextval FROM TABLE(GETNEXTVAL(gs5))");
        engine.execute("CREATE OR REPLACE VIEW gnv2 AS SELECT a, nextval FROM gt, TABLE(GETNEXTVAL(gs5))");
        engine.execute("CREATE OR REPLACE TABLE gc AS SELECT nextval FROM TABLE(GETNEXTVAL(gs5))");
        assertCells(new String[][] {
            {"EXPLAIN SELECT * FROM TABLE(GETNEXTVAL(gs5))",
                plan("VALUES.COLUMN1, GETNEXTVAL.NEXTVAL", "(null)")},
            {"EXPLAIN SELECT nextval FROM TABLE(GETNEXTVAL(gs5))",
                plan("GETNEXTVAL.NEXTVAL", "()")},
            {"EXPLAIN SELECT * FROM TABLE(GETNEXTVAL(gs5)) s",
                plan("VALUES.COLUMN1, S.NEXTVAL", "(null)")},
            {"EXPLAIN SELECT s.* FROM TABLE(GETNEXTVAL(gs5)) s",
                plan("S.NEXTVAL", "()")},
            {"EXPLAIN SELECT s.nextval FROM TABLE(GETNEXTVAL(gs5)) s",
                plan("S.NEXTVAL", "()")},
            {"EXPLAIN SELECT 1 FROM TABLE(GETNEXTVAL(gs5))",
                plan("1", "()")},
            {"EXPLAIN SELECT nextval + 1 AS x FROM TABLE(GETNEXTVAL(gs5))",
                plan("(GETNEXTVAL.NEXTVAL) + 1", "()")},
            {"EXPLAIN SELECT * FROM TABLE(GETNEXTVAL(gs5)) s(v)",
                plan("VALUES.COLUMN1, S.NEXTVAL", "(null)")},
            {"EXPLAIN SELECT GETNEXTVAL.nextval FROM TABLE(GETNEXTVAL(gs5))",
                plan("GETNEXTVAL.NEXTVAL", "()")},
            {"DESCRIBE VIEW gnv",
                described("NEXTVAL|NUMBER(19,0)", "N")},
            {"DESCRIBE VIEW gnv2",
                described("A|NUMBER(38,0)", "Y") + " ;; " + described("NEXTVAL|NUMBER(19,0)", "N")},
            {"SELECT column_name, is_nullable FROM information_schema.columns WHERE table_name = 'GNV'",
                "NEXTVAL|NO"},
            {"DESCRIBE TABLE gc",
                described("NEXTVAL|NUMBER(19,0)", "Y")},
        });
    }

    /** The plan of a query over a GETNEXTVAL written first: its Result's expressions and its row's VALUES list. */
    private static String plan(final String result, final String values) {
        return "null|null|null|GlobalStats|null|null|null|0|0|0 ;; 1|0|null|Result|null|null|" + result
            + "|null|null|null ;; 1|1|[0]|Getnextval|null|null|GS5|null|null|null ;; 1|2|[1]|ValuesClause|null|null|"
            + values + "|null|null|null";
    }

    /** A described column: its name and type, then whether it is nullable. */
    private static String described(final String nameAndType, final String nullable) {
        return nameAndType + "|COLUMN|" + nullable + "|null|N|N|null|null|null|null|null|null";
    }
}
