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
 * The syntax-error lines of a SHOW listing the parser cannot finish inside its scope, in a block and on its own: the
 * token named back in the scope after a token that is no name, the plain function listing's word after FUNCTIONS, an
 * IDENTIFIER() reference after a scope word that reads no name, and the END refused after an ELSE that opens two
 * queries (live-verified).
 */
public class ShowScopeReadBackLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void blockShowScopeLines() {
        assertCells(new String[][] {
            {"BEGIN SHOW TABLES IN COMPUTE POOL; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected ';'. | syntax"
                + " error line 1 at position 29 unexpected 'POOL'."},
            {"BEGIN SHOW TABLES IN DATASET .; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ';'. | syntax"
                + " error line 1 at position 29 unexpected '.'."},
            {"BEGIN SHOW FUNCTIONS IN WITH x y; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'WITH'. | "
                + "syntax error line 1 at position 21 unexpected 'IN'."},
            {"BEGIN SHOW TABLES IN nosuch 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '1'. | syntax"
                + " error line 1 at position 28 unexpected '1'."},
            {"BEGIN SHOW TABLES IN nosuch x 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '1'. | syntax"
                + " error line 1 at position 28 unexpected 'x'."},
            {"BEGIN SHOW TABLES IN DATASET IDENTIFIER('y') z; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'IDENTIFIER'."
                + " | syntax error line 1 at position 39 unexpected '('."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSE SELECT 1; SELECT 2; END IF; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax"
                + " error line 1 at position 47 unexpected 'ELSE'. | syntax error line 1 at position 72 "
                + "unexpected 'END'."},
            {"BEGIN SHOW TABLES IN nosuch x y; TABLE t1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax"
                + " error line 1 at position 43 unexpected 'END'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSE SELECT 1; END IF; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax"
                + " error line 1 at position 47 unexpected 'ELSE'."},
            {"SHOW TABLES IN nosuch 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '1'."},
            {"SHOW FUNCTIONS IN WITH x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'WITH'. | "
                + "syntax error line 1 at position 23 unexpected 'x'."},
            {"SHOW TABLES IN COMPUTE POOL",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '<EOF>'."},
            {"SHOW TABLES IN DATASET .",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '<EOF>'."},
            {"BEGIN SHOW TABLES IN nosuch.; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ';'. | syntax"
                + " error line 1 at position 27 unexpected '.'."},
            {"BEGIN SHOW TABLES IN nosuch x y z; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax"
                + " error line 1 at position 32 unexpected 'z'."},
            {"BEGIN SHOW TABLES IN nosuch 1 2; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '1'. | syntax"
                + " error line 1 at position 28 unexpected '1'."},
            {"BEGIN SHOW TABLES LIKE 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '1'."},
            {"BEGIN SHOW TABLES x; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'x'. | syntax"
                + " error line 1 at position 21 unexpected 'END'."},
            {"BEGIN SHOW TABLES 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '1'."},
            {"BEGIN SELECT 1 FROM t 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '1'."},
            {"BEGIN SHOW TABLES IN nosuch x 1; SELECT 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '1'. | syntax"
                + " error line 1 at position 28 unexpected 'x'."},
            {"BEGIN SHOW TABLES STARTS WITH 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '1'."},
            {"BEGIN SHOW TABLES IN DATASET y; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | syntax"
                + " error line 1 at position 32 unexpected 'END'."},
            {"SHOW TABLES IN DATASET y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'y'."},
            {"BEGIN SHOW TABLES IN DATASET y; SELECT 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | syntax"
                + " error line 1 at position 42 unexpected 'END'."},
            {"BEGIN SHOW TABLES IN DATASET y; SELECT 1; SELECT 2; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | syntax"
                + " error line 1 at position 52 unexpected 'END'."},
            {"BEGIN SHOW TABLES IN DATASET IDENTIFIER('y'); END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'IDENTIFIER'."
                + " | syntax error line 1 at position 39 unexpected '('."},
            {"SHOW TABLES IN DATASET IDENTIFIER('y') z",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'IDENTIFIER'."},
            {"BEGIN SHOW FUNCTIONS IN WITH; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'WITH'. | "
                + "syntax error line 1 at position 21 unexpected 'IN'."},
            {"BEGIN SHOW FUNCTIONS IN x y z; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'z'. | syntax"
                + " error line 1 at position 31 unexpected 'END'."},
            {"BEGIN SHOW TABLES IN COMPUTE POOL x 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '1'."},
            {"BEGIN SHOW TABLES LIMIT x; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'x'."},
            {"BEGIN LET x := 1; SHOW TABLES IN nosuch x 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected '1'. | syntax"
                + " error line 1 at position 40 unexpected 'x'."},
            {"BEGIN SHOW TABLES IN nosuch x 1; RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '1'. | syntax"
                + " error line 1 at position 28 unexpected 'x'."},
            {"BEGIN SHOW TABLES IN nosuch x 'a'; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''a''. | "
                + "syntax error line 1 at position 28 unexpected 'x'."},
            {"BEGIN SHOW TABLES IN nosuch x (; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '('. | syntax"
                + " error line 1 at position 28 unexpected 'x'."},
            {"SHOW TABLES IN nosuch x 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '1'."},
            {"BEGIN SELECT 1 FROM t x 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '1'."},
            {"BEGIN SHOW TABLES IN DATASET y; ELSE SELECT 1; SELECT 2; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'. | syntax"
                + " error line 1 at position 32 unexpected 'ELSE'."},
            {"BEGIN SHOW TABLES IN DATASET IDENTIFIER('y') z; SELECT 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'IDENTIFIER'."
                + " | syntax error line 1 at position 39 unexpected '('."},
            {"BEGIN SHOW TABLES IN DATASET IDENTIFIER('y') ; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'IDENTIFIER'."
                + " | syntax error line 1 at position 39 unexpected '('."},
            {"BEGIN SHOW TABLES IN DATASET x z; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'x'. | syntax"
                + " error line 1 at position 31 unexpected 'z'."},
            {"BEGIN SHOW TABLES LIKE 'a' x 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'x'. | syntax"
                + " error line 1 at position 29 unexpected '1'."},
            {"BEGIN SHOW TABLES IN SCHEMA s x 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'x'. | syntax"
                + " error line 1 at position 32 unexpected '1'."},
            {"BEGIN SHOW TABLES IN DATABASE d 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'. | syntax"
                + " error line 1 at position 30 unexpected 'd'."},
            {"BEGIN SHOW TABLES IN DATABASE d x 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'x'. | syntax"
                + " error line 1 at position 34 unexpected '1'."},
            {"BEGIN SHOW TABLES IN s.x 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '1'. | syntax"
                + " error line 1 at position 22 unexpected '.'."},
            {"BEGIN SHOW TABLES IN nosuch x y 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax"
                + " error line 1 at position 32 unexpected '1'."},
            {"BEGIN SHOW TABLES IN nosuch x .; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ';'."},
            {"BEGIN SHOW TABLES IN COMPUTE POOL 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '1'. | syntax"
                + " error line 1 at position 29 unexpected 'POOL'."},
            {"BEGIN SHOW TABLES IN DATASET 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '1'. | syntax"
                + " error line 1 at position 29 unexpected '1'."},
            {"BEGIN SHOW TABLES IN DATASET x 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'x'. | syntax"
                + " error line 1 at position 31 unexpected '1'."},
            {"BEGIN SHOW TABLES IN DATASET x.; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'x'. | syntax"
                + " error line 1 at position 30 unexpected '.'."},
            {"BEGIN SHOW TABLES IN ACCOUNT 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '1'. | syntax"
                + " error line 1 at position 29 unexpected '1'."},
            {"BEGIN SHOW TABLES IN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '1'."},
            {"BEGIN SHOW VIEWS IN nosuch x 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '1'. | syntax"
                + " error line 1 at position 27 unexpected 'x'."},
            {"BEGIN SHOW SCHEMAS IN nosuch x 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '1'. | syntax"
                + " error line 1 at position 29 unexpected 'x'."},
            {"BEGIN SHOW FUNCTIONS IN nosuch x 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '1'. | syntax"
                + " error line 1 at position 31 unexpected 'x'."},
            {"BEGIN SHOW VIEWS IN WITH x; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'WITH'. | "
                + "syntax error line 1 at position 25 unexpected 'x'."},
            {"BEGIN SHOW SCHEMAS IN WITH x; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'WITH'. | "
                + "syntax error line 1 at position 27 unexpected 'x'."},
            {"BEGIN SHOW TABLES IN WITH; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'WITH'. | "
                + "syntax error line 1 at position 25 unexpected ';'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES IN nosuch x 1; END IF; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected '1'. | syntax"
                + " error line 1 at position 56 unexpected 'END'."},
            {"BEGIN SHOW TABLES IN COMPUTE POOL; SELECT 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected ';'. | syntax"
                + " error line 1 at position 29 unexpected 'POOL'."},
            {"BEGIN SHOW TABLES IN nosuch x 1 2 3; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '1'. | syntax"
                + " error line 1 at position 28 unexpected 'x'."},
            {"BEGIN SHOW TABLES HISTORY IN nosuch x 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '1'. | syntax"
                + " error line 1 at position 36 unexpected 'x'."},
            {"BEGIN SHOW TABLES IN COMPUTE POOL IDENTIFIER('y') z; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected 'z'. | syntax"
                + " error line 1 at position 53 unexpected 'END'."},
            {"SHOW TABLES IN COMPUTE POOL IDENTIFIER('y') z",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'z'."},
            {"SHOW TABLES IN APPLICATION IDENTIFIER('y') z",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'z'."},
            {"SHOW TABLES IN DATABASE IDENTIFIER('y') z",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'z'."},
            {"SHOW TABLES IN nosuch IDENTIFIER('y') z",
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'z'."},
            {"SHOW TABLES IN DATASET IDENTIFIER('y')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'IDENTIFIER'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSE SELECT 1; SELECT 2; SELECT 3; END IF; "
                + "END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax"
                + " error line 1 at position 47 unexpected 'ELSE'. | syntax error line 1 at position 82 "
                + "unexpected 'END'."},
            {"BEGIN IF (TRUE) THEN SELECT 1 1; ELSE SELECT 1; SELECT 2; END IF; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '1'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSE SELECT 1; SELECT 2; END IF; SELECT 3; "
                + "END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax"
                + " error line 1 at position 47 unexpected 'ELSE'. | syntax error line 1 at position 72 "
                + "unexpected 'END'."},
            {"BEGIN SELECT 1 FROM t x 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '1'."},
            {"BEGIN SHOW TABLES IN nosuch x 1; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '1'. | syntax"
                + " error line 1 at position 28 unexpected 'x'."},
            {"BEGIN SHOW TABLES IN APPLICATION PACKAGE 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected '1'. | syntax"
                + " error line 1 at position 33 unexpected 'PACKAGE'."},
            {"BEGIN SHOW TABLES IN DATASET .; SELECT 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ';'. | syntax"
                + " error line 1 at position 29 unexpected '.'."},
            {"BEGIN SHOW TABLES IN DATASET x . 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'x'. | syntax"
                + " error line 1 at position 31 unexpected '.'."},
            {"BEGIN SHOW FUNCTIONS IN WITH; SELECT 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'WITH'. | "
                + "syntax error line 1 at position 21 unexpected 'IN'."},
            {"BEGIN SHOW FUNCTIONS IN WITH x y; SELECT 1; SELECT 2; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'WITH'. | "
                + "syntax error line 1 at position 21 unexpected 'IN'."},
            {"BEGIN SHOW TABLES IN nosuch x 1; SELECT 1; SELECT 2; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '1'. | syntax"
                + " error line 1 at position 28 unexpected 'x'."},
            {"SHOW TABLES IN WITH x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'WITH'. | "
                + "syntax error line 1 at position 20 unexpected 'x'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSE SELECT 1; SELECT 2; END IF; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax"
                + " error line 1 at position 47 unexpected 'ELSE'. | syntax error line 1 at position 72 "
                + "unexpected 'END'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSEIF (FALSE) THEN SELECT 1; SELECT 2; END "
                + "IF; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax"
                + " error line 1 at position 47 unexpected 'ELSEIF'. | syntax error line 1 at position 54 "
                + "unexpected '('."},
            {"BEGIN SHOW TABLES IN DATASET IDENTIFIER('y') z w; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'IDENTIFIER'."
                + " | syntax error line 1 at position 39 unexpected '('."},
            {"BEGIN SHOW TABLES IN DATASET IDENTIFIER('y') 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'IDENTIFIER'."
                + " | syntax error line 1 at position 39 unexpected '('."},
            {"BEGIN SELECT 1; EXCEPTION WHEN OTHER THEN SHOW TABLES IN nosuch x 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 66 unexpected '1'. | syntax"
                + " error line 1 at position 64 unexpected 'x'."},
            {"EXECUTE IMMEDIATE $$ BEGIN SHOW TABLES IN nosuch x 1; END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '1'. | syntax"
                + " error line 1 at position 29 unexpected 'x'."},
            {"DECLARE v INT; BEGIN SHOW TABLES IN nosuch x 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected '1'. | syntax"
                + " error line 1 at position 43 unexpected 'x'."},
            {"BEGIN SHOW TABLES IN a.b 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '1'. | syntax"
                + " error line 1 at position 22 unexpected '.'."},
            {"BEGIN SHOW TABLES IN a.b.c 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '1'. | syntax"
                + " error line 1 at position 22 unexpected '.'."},
            {"BEGIN SHOW TABLES IN SCHEMA a.b 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'. | syntax"
                + " error line 1 at position 32 unexpected '1'."},
            {"BEGIN SHOW TABLES IN a . ; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ';'. | syntax"
                + " error line 1 at position 23 unexpected '.'."},
            {"BEGIN SHOW TABLES IN DATABASE d .; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected ';'."},
            {"BEGIN SHOW TABLES IN COMPUTE POOL p 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '1'."},
            {"BEGIN SHOW TABLES IN nosuch x NULL; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'NULL'. | "
                + "syntax error line 1 at position 28 unexpected 'x'."},
            {"BEGIN SHOW TABLES IN nosuch x :b; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ':'. | syntax"
                + " error line 1 at position 28 unexpected 'x'."},
            {"BEGIN SHOW TABLES IN nosuch x = 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'x'. | syntax"
                + " error line 1 at position 30 unexpected '='."},
            {"BEGIN SHOW FUNCTIONS IN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '1'. | syntax"
                + " error line 1 at position 21 unexpected 'IN'."},
            {"BEGIN SHOW FUNCTIONS IN ; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected ';'. | syntax"
                + " error line 1 at position 21 unexpected 'IN'."},
            {"BEGIN SHOW TABLES IN ; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected ';'."},
            {"BEGIN SHOW FUNCTIONS IN nosuch 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '1'. | syntax"
                + " error line 1 at position 31 unexpected '1'."},
            {"BEGIN SHOW TABLES IN DATABASE 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '1'. | syntax"
                + " error line 1 at position 30 unexpected '1'."},
            {"BEGIN SHOW TABLES IN SCHEMA a.b.c 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '1'. | syntax"
                + " error line 1 at position 31 unexpected '.'."},
            {"BEGIN SHOW TABLES IN a.b x 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '1'. | syntax"
                + " error line 1 at position 22 unexpected '.'."},
            {"BEGIN SHOW TABLES IN nosuch x 1 END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '1'. | syntax"
                + " error line 1 at position 28 unexpected 'x'."},
            {"BEGIN SHOW TABLES LIKE 'a' IN nosuch x 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected '1'. | syntax"
                + " error line 1 at position 37 unexpected 'x'."},
            {"BEGIN SHOW TABLES IN nosuch x LIMIT 1 2; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '2'."},
            {"BEGIN SHOW TABLES IN COMPUTE 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '1'. | syntax"
                + " error line 1 at position 29 unexpected '1'."},
            {"BEGIN SHOW TABLES IN a .. ; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ';'."},
            {"BEGIN SHOW TABLES IN nosuch x . 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'."},
            {"BEGIN SHOW TABLES IN a.b . ; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected ';'. | syntax"
                + " error line 1 at position 22 unexpected '.'."},
            {"BEGIN SHOW TABLES IN nosuch x y . ; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax"
                + " error line 1 at position 32 unexpected '.'."},
            {"BEGIN SHOW FUNCTIONS IN SELECT; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 21 unexpected 'IN'."},
            {"BEGIN SHOW FUNCTIONS IN WITH x y; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'WITH'. | "
                + "syntax error line 1 at position 21 unexpected 'IN'."},
            {"BEGIN SHOW TERSE TABLES IN nosuch x 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '1'. | syntax"
                + " error line 1 at position 34 unexpected 'x'."},
            {"BEGIN SHOW TABLES IN COMPUTE POOL; END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected ';'. | syntax"
                + " error line 1 at position 29 unexpected 'POOL'."},
            {"BEGIN SHOW FUNCTIONS IN WITH; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'WITH'. | "
                + "syntax error line 1 at position 21 unexpected 'IN'."},
            {"BEGIN SHOW FUNCTIONS IN WITH 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'WITH'. | "
                + "syntax error line 1 at position 21 unexpected 'IN'."},
            {"BEGIN SHOW TABLES IN nosuch x (1); END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '('. | syntax"
                + " error line 1 at position 28 unexpected 'x'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSE SELECT 1; SELECT x; END IF; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax"
                + " error line 1 at position 47 unexpected 'ELSE'. | syntax error line 1 at position 72 "
                + "unexpected 'END'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSE SELECT 1; LET z := 1; END IF; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax"
                + " error line 1 at position 47 unexpected 'ELSE'."},
            {"BEGIN IF (TRUE) THEN SELECT 1 FROM; ELSE SELECT 1; SELECT 2; END IF; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ';'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSE SELECT 1; IF (TRUE) THEN SELECT 2; END "
                + "IF; END IF; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax"
                + " error line 1 at position 47 unexpected 'ELSE'."},
            {"BEGIN SHOW TABLES IN SCHEMA a 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '1'. | syntax"
                + " error line 1 at position 28 unexpected 'a'."},
            {"BEGIN SHOW TABLES IN SCHEMA a.b.c.d 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '1'. | syntax"
                + " error line 1 at position 31 unexpected '.'."},
            {"BEGIN SHOW TABLES IN SCHEMA a . ; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected ';'."},
            {"BEGIN SHOW USER FUNCTIONS IN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '1'."},
            {"BEGIN SHOW PROCEDURES IN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '1'."},
            {"BEGIN SHOW TERSE FUNCTIONS IN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '1'."},
            {"BEGIN SHOW FUNCTIONS LIKE 'a' IN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '1'. | syntax"
                + " error line 1 at position 21 unexpected 'LIKE'."},
            {"BEGIN SHOW TABLES IN nosuch \"x\" 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'. | syntax"
                + " error line 1 at position 28 unexpected '\"x\"'."},
            {"BEGIN SHOW TABLES IN \"a\".b 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '1'. | syntax"
                + " error line 1 at position 24 unexpected '.'."},
            {"BEGIN SHOW TABLES IN a.b.c.d 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '1'. | syntax"
                + " error line 1 at position 22 unexpected '.'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET IDENTIFIER('y') z; END IF; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'IDENTIFIER'."
                + " | syntax error line 1 at position 63 unexpected 'END'."},
            {"BEGIN SHOW TABLES IN USER IDENTIFIER('y') z; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'IDENTIFIER'."
                + " | syntax error line 1 at position 36 unexpected '('."},
            {"BEGIN SHOW SCHEMAS IN DATASET IDENTIFIER('y') z; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'IDENTIFIER'."
                + " | syntax error line 1 at position 40 unexpected '('."},
            {"SHOW TABLES IN DATASET IDENTIFIER('y') LIMIT 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'IDENTIFIER'."},
            {"SHOW TABLES IN DATASET IDENTIFIER($a) z",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'IDENTIFIER'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSE SELECT 1; SELECT 2; END IF; RETURN 1; "
                + "END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax"
                + " error line 1 at position 47 unexpected 'ELSE'. | syntax error line 1 at position 72 "
                + "unexpected 'END'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSE SELECT 1 FROM t; SELECT 2 FROM u; END "
                + "IF; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax"
                + " error line 1 at position 47 unexpected 'ELSE'. | syntax error line 1 at position 86 "
                + "unexpected 'END'."},
            {"BEGIN SHOW FUNCTIONS IN 1; SELECT 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '1'. | syntax"
                + " error line 1 at position 21 unexpected 'IN'."},
            {"BEGIN SHOW TABLES IN DATABASE d 'a'; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected ''a''. | "
                + "syntax error line 1 at position 30 unexpected 'd'."},
            {"BEGIN SHOW TABLES IN DATASET IDENTIFIER('y') z; SELECT 1; SELECT 2; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'IDENTIFIER'."
                + " | syntax error line 1 at position 39 unexpected '('."},
            {"SHOW TABLES IN DATASET IDENTIFIER('y') z w",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'IDENTIFIER'."},
            {"BEGIN SHOW TABLES IN nosuch x -1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '-'. | syntax"
                + " error line 1 at position 28 unexpected 'x'."},
            {"BEGIN SHOW TABLES IN nosuch x $1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '$1'. | "
                + "syntax error line 1 at position 34 unexpected 'END'."},
            {"BEGIN SHOW TABLES IN nosuch x ?; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '?'. | syntax"
                + " error line 1 at position 28 unexpected 'x'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES IN nosuch 1; END IF; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected '1'. | syntax"
                + " error line 1 at position 54 unexpected 'END'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES IN COMPUTE POOL; END IF; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected ';'. | syntax"
                + " error line 1 at position 58 unexpected 'END'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES IN nosuch x . ; END IF; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected ';'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES IN nosuch x 1; SELECT 1; END IF; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected '1'. | syntax"
                + " error line 1 at position 58 unexpected 'END'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET IDENTIFIER('y') z; SELECT 1; END IF; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'IDENTIFIER'."
                + " | syntax error line 1 at position 73 unexpected 'END'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET IDENTIFIER('y') z w; END IF; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'IDENTIFIER'."
                + " | syntax error line 1 at position 62 unexpected 'w'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET IDENTIFIER('y') z; END IF; RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'IDENTIFIER'."
                + " | syntax error line 1 at position 63 unexpected 'END'."},
            {"BEGIN SHOW TABLES IN nosuch x < 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '<'. | syntax"
                + " error line 1 at position 28 unexpected 'x'."},
            {"BEGIN SHOW TABLES IN nosuch x + 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '+'. | syntax"
                + " error line 1 at position 28 unexpected 'x'."},
            {"BEGIN SHOW TABLES IN nosuch x , y; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ','. | syntax"
                + " error line 1 at position 28 unexpected 'x'."},
            {"BEGIN SHOW TABLES IN nosuch x ); END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ')'."},
            {"BEGIN SHOW TABLES IN nosuch x * 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '*'. | syntax"
                + " error line 1 at position 28 unexpected 'x'."},
            {"BEGIN SHOW TABLES IN nosuch x != 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '!='. | "
                + "syntax error line 1 at position 28 unexpected 'x'."},
            {"BEGIN SHOW TABLES IN nosuch x || 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '||'. | "
                + "syntax error line 1 at position 28 unexpected 'x'."},
            {"BEGIN SHOW TABLES IN nosuch x :: INT; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '::'. | "
                + "syntax error line 1 at position 28 unexpected 'x'."},
            {"BEGIN SHOW TABLES IN DATASET IDENTIFIER('y') = 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'IDENTIFIER'."
                + " | syntax error line 1 at position 39 unexpected '('."},
            {"BEGIN SHOW TABLES IN nosuch = 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '='. | syntax"
                + " error line 1 at position 28 unexpected '='."},
            {"BEGIN SHOW TABLES IN COMPUTE POOL = 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'POOL'. | "
                + "syntax error line 1 at position 34 unexpected '='."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES IN nosuch x 1; END IF; RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected '1'. | syntax"
                + " error line 1 at position 56 unexpected 'RETURN'."},
            {"BEGIN SHOW TABLES IN APPLICATION PACKAGE; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ';'."},
            {"BEGIN SHOW TABLES IN APPLICATION x 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected '1'. | syntax"
                + " error line 1 at position 33 unexpected 'x'."},
            {"BEGIN SHOW TABLES IN USER LIMIT; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected ';'."},
            {"BEGIN SHOW TABLES IN REPLICATION GROUP 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected '1'. | syntax"
                + " error line 1 at position 33 unexpected 'GROUP'."},
            {"BEGIN SHOW TABLES IN APPLICATION 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '1'. | syntax"
                + " error line 1 at position 33 unexpected '1'."},
            {"BEGIN SHOW TABLES IN nosuch FROM; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'FROM'. | "
                + "syntax error line 1 at position 28 unexpected 'FROM'."},
        });
    }
}
