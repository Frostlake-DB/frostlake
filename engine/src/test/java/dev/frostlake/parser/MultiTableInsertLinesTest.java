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
 * The lines a multi-table INSERT is refused with where brackets that open no query, or a comma, follow an INTO (all
 * live-verified): the first token inside the brackets, then the token after them, which ends the report; brackets
 * that end the statement let the later statements be read, and a comma after a VALUES row is the statement's one
 * line.
 */
public class MultiTableInsertLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void bracketsAfterAnInto() {
        engine.execute("CREATE TABLE t (a INT)");
        engine.execute("CREATE TABLE u (a INT, b INT)");
        assertCells(new String[][] {
            {"INSERT ALL INTO t VALUES (1) ('x') SELECT 1; SELECT 1 x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''x''. | "
                + "syntax error line 1 at position 35 unexpected 'SELECT'."},
            {"INSERT ALL INTO t VALUES (1) ('x') SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''x''. | "
                + "syntax error line 1 at position 35 unexpected 'SELECT'."},
            {"INSERT ALL INTO t VALUES (1) x SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'x'."},
            {"INSERT ALL INTO t VALUES (1) x y SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'x'."},
            {"INSERT ALL INTO t VALUES (1) 2 SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '2'."},
            {"INSERT ALL INTO t VALUES (1) (2) SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '2'. | syntax"
                + " error line 1 at position 33 unexpected 'SELECT'."},
            {"INSERT ALL INTO t VALUES (1), (2) SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ','."},
            {"INSERT ALL INTO t VALUES (1) INTO t VALUES (2) ('x') SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected ''x''. | "
                + "syntax error line 1 at position 53 unexpected 'SELECT'."},
            {"INSERT ALL INTO t VALUES (1) ('x') INTO t VALUES (2) SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''x''. | "
                + "syntax error line 1 at position 35 unexpected 'INTO'."},
            {"INSERT ALL WHEN 1=1 THEN INTO t VALUES (1) ('x') SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected ''x''. | "
                + "syntax error line 1 at position 49 unexpected 'SELECT'."},
            {"INSERT ALL WHEN 1=1 THEN INTO t VALUES (1) ('x') ELSE INTO t VALUES (2) SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected ''x''. | "
                + "syntax error line 1 at position 49 unexpected 'ELSE'."},
            {"INSERT ALL INTO t VALUES (1) ('x') SELECT 1 FROM t",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''x''. | "
                + "syntax error line 1 at position 35 unexpected 'SELECT'."},
            {"INSERT ALL INTO t VALUES (1) ('x') SELECT 1 x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''x''. | "
                + "syntax error line 1 at position 35 unexpected 'SELECT'."},
            {"INSERT ALL INTO t VALUES (1) ('x')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''x''."},
            {"INSERT ALL INTO t VALUES (1) ('x');",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''x''."},
            {"INSERT ALL INTO t VALUES (1) ('x') ; SELECT 1 x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''x''. | "
                + "syntax error line 1 at position 48 unexpected 'y'."},
            {"INSERT ALL INTO t VALUES (1) ('x') y SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''x''. | "
                + "syntax error line 1 at position 35 unexpected 'y'."},
            {"INSERT ALL INTO t VALUES (1) ('x', 2) SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''x''. | "
                + "syntax error line 1 at position 38 unexpected 'SELECT'."},
            {"INSERT ALL INTO t VALUES (1) () SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ')'. | syntax"
                + " error line 1 at position 32 unexpected 'SELECT'."},
            {"INSERT ALL INTO t VALUES (1) (a) SELECT 1 a",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'a'. | syntax"
                + " error line 1 at position 33 unexpected 'SELECT'."},
            {"INSERT ALL INTO t VALUES (1) ('x') ('y') SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''x''. | "
                + "syntax error line 1 at position 35 unexpected '('."},
            {"INSERT ALL INTO t VALUES (1) ('x') WITH c AS (SELECT 1) SELECT * FROM c",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''x''. | "
                + "syntax error line 1 at position 35 unexpected 'WITH'."},
            {"INSERT ALL INTO t VALUES (1) ('x') (SELECT 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''x''. | "
                + "syntax error line 1 at position 35 unexpected '('."},
            {"INSERT ALL INTO t (a) VALUES (1) ('x') SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ''x''. | "
                + "syntax error line 1 at position 39 unexpected 'SELECT'."},
            {"INSERT ALL INTO t ('x') SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ''x''. | "
                + "syntax error line 1 at position 24 unexpected 'SELECT'."},
            {"INSERT ALL INTO t VALUES (1) SELECT 1; SELECT 1 x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected 'y'."},
            {"INSERT ALL INTO t VALUES (1) ('x') SELECT 1; SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''x''. | "
                + "syntax error line 1 at position 35 unexpected 'SELECT'."},
            {"INSERT ALL INTO t VALUES (1) ('x') SELECT 1 +; SELECT 1 x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''x''. | "
                + "syntax error line 1 at position 35 unexpected 'SELECT'."},
            {"INSERT ALL INTO t VALUES (1) (2 SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '2'."},
            {"INSERT ALL INTO t VALUES (1) 'x' SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ''x''."},
            {"INSERT ALL INTO t VALUES (1) ('x') VALUES (1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''x''. | "
                + "syntax error line 1 at position 35 unexpected 'VALUES'."},
            {"INSERT ALL INTO t VALUES (1) ('x') TABLE t",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''x''. | "
                + "syntax error line 1 at position 35 unexpected 'TABLE'."},
            {"INSERT ALL INTO t VALUES (1) ('x') SELECT",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''x''. | "
                + "syntax error line 1 at position 35 unexpected 'SELECT'."},
            {"INSERT ALL INTO t VALUES (1) ('x') FROM t",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''x''. | "
                + "syntax error line 1 at position 35 unexpected 'FROM'."},
            {"INSERT OVERWRITE ALL INTO t VALUES (1) ('x') SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ''x''. | "
                + "syntax error line 1 at position 45 unexpected 'SELECT'."},
            {"INSERT INTO t VALUES (1) ('x') SELECT 1; SELECT 1 x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '('."},
            {"INSERT ALL INTO t VALUES (1) ('x')\nSELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''x''. | "
                + "syntax error line 2 at position 0 unexpected 'SELECT'."},
        });
    }

    @Test
    public void bracketShapesAndCommas() {
        engine.execute("CREATE TABLE t (a INT)");
        assertCells(new String[][] {
            {"INSERT ALL INTO t VALUES (1) (1, 'x') SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '1'. | syntax"
                + " error line 1 at position 38 unexpected 'SELECT'."},
            {"INSERT ALL INTO t VALUES (1) ((1)) SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '1'. | syntax"
                + " error line 1 at position 35 unexpected 'SELECT'."},
            {"INSERT ALL INTO t VALUES (1) (2) (3) SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '2'. | syntax"
                + " error line 1 at position 33 unexpected '('."},
            {"INSERT ALL INTO t VALUES (1 2) SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '2'."},
            {"INSERT ALL INTO t VALUES () SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ')'."},
            {"INSERT ALL INTO t VALUES ('x' y) SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'."},
            {"INSERT ALL INTO t ('x') VALUES (1) SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ''x''. | "
                + "syntax error line 1 at position 24 unexpected 'VALUES'."},
            {"INSERT ALL INTO t (a) (b) SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'b'. | syntax"
                + " error line 1 at position 26 unexpected 'SELECT'."},
            {"INSERT ALL INTO t (a, 'x') VALUES (1) SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected ''x''."},
            {"INSERT ALL INTO t (1) SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '1'. | syntax"
                + " error line 1 at position 22 unexpected 'SELECT'."},
            {"INSERT ALL INTO t () SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ')'. | syntax"
                + " error line 1 at position 21 unexpected 'SELECT'."},
            {"INSERT ALL INTO t VALUES (1) (SELECT 1) SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'SELECT'."},
            {"INSERT ALL INTO t VALUES (1) ('x')) SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''x''. | "
                + "syntax error line 1 at position 34 unexpected ')'."},
            {"INSERT ALL INTO t VALUES (1) ('x') ) SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''x''. | "
                + "syntax error line 1 at position 35 unexpected ')'."},
            {"INSERT ALL INTO t VALUES (1) ('x') , SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''x''. | "
                + "syntax error line 1 at position 35 unexpected ','."},
            {"INSERT ALL INTO t VALUES (1) ('x') SELECT 1; SELECT 1 +",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''x''. | "
                + "syntax error line 1 at position 35 unexpected 'SELECT'."},
            {"INSERT ALL INTO t VALUES (1) ('x') 3; SELECT 1 +",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''x''. | "
                + "syntax error line 1 at position 35 unexpected '3'."},
            {"INSERT ALL INTO t VALUES (1), (2); SELECT 1 +",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ','. | syntax"
                + " error line 1 at position 45 unexpected '<EOF>'."},
            {"INSERT ALL INTO t VALUES (1), (2) x SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ','."},
            {"INSERT ALL INTO t VALUES (1),",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ','."},
            {"INSERT ALL INTO t VALUES (1), SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ','."},
            {"INSERT ALL WHEN 1=1 THEN INTO t VALUES (1), (2) SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected ','."},
            {"INSERT ALL INTO t VALUES (1) (x.y, 2) SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'x'. | syntax"
                + " error line 1 at position 38 unexpected 'SELECT'."},
            {"INSERT ALL INTO t VALUES (1) (1 + 2) SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '1'. | syntax"
                + " error line 1 at position 37 unexpected 'SELECT'."},
            {"INSERT FIRST WHEN 1=1 THEN INTO t VALUES (1) ('x') SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected ''x''. | "
                + "syntax error line 1 at position 51 unexpected 'SELECT'."},
            {"INSERT ALL INTO t VALUES (1) INTO t ('x') SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected ''x''. | "
                + "syntax error line 1 at position 42 unexpected 'SELECT'."},
            {"INSERT ALL INTO t VALUES (1) ('x') SELECT 1 FROM (SELECT 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''x''. | "
                + "syntax error line 1 at position 35 unexpected 'SELECT'."},
            {"INSERT ALL INTO t VALUES (1) ('x') ((SELECT 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''x''. | "
                + "syntax error line 1 at position 35 unexpected '('."},
            {"INSERT ALL INTO t VALUES (1) ((1) SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '1'. | syntax"
                + " error line 1 at position 34 unexpected 'SELECT'."},
            {"INSERT ALL INTO t VALUES (1) (1)) SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '1'. | syntax"
                + " error line 1 at position 32 unexpected ')'."},
            {"INSERT ALL INTO t VALUES (a) ('x') SELECT 1 a",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''x''. | "
                + "syntax error line 1 at position 35 unexpected 'SELECT'."},
            {"INSERT ALL INTO t VALUES (1) (NULL) SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'NULL'. | "
                + "syntax error line 1 at position 36 unexpected 'SELECT'."},
            {"INSERT ALL INTO t VALUES (1) (DEFAULT) SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'DEFAULT'. | "
                + "syntax error line 1 at position 39 unexpected 'SELECT'."},
            {"INSERT ALL INTO t VALUES (1) ('x') ; SELECT 1 +",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''x''. | "
                + "syntax error line 1 at position 47 unexpected '<EOF>'."},
            {"INSERT INTO t VALUES (1), (2) ('x') SELECT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '('."},
        });
    }
}
