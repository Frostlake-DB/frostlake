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
 * The syntax-error lines of a SHOW listing refused inside its scope in a block of another block's list and in the
 * bodies of WHILE, LOOP, FOR, REPEAT, CASE and IF, of a WITH written as the scope, of a word after a listing's WITH
 * that ends the statement, and of a name where a block's flow arrow wants a statement (live-verified).
 */
public class ShowScopeBlockReadOnTest extends MeasuredCellsTestSupport {

    @Test
    public void nestedBlock() {
        assertCells(new String[][] {
            {"BEGIN BEGIN SHOW TABLES IN nosuch x 1; END; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '1'. | syntax"
                + " error line 1 at position 34 unexpected 'x'. | syntax error line 1 at position 36 unexpected"
                + " '1'."},
            {"BEGIN BEGIN SHOW TABLES IN nosuch 1; END; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '1'. | syntax"
                + " error line 1 at position 34 unexpected '1'."},
            {"BEGIN BEGIN SHOW TABLES IN nosuch x y; END; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'y'. | syntax"
                + " error line 1 at position 44 unexpected 'END'."},
            {"BEGIN BEGIN SHOW TABLES IN COMPUTE POOL; END; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected ';'. | syntax"
                + " error line 1 at position 35 unexpected 'POOL'. | syntax error line 1 at position 41 "
                + "unexpected 'END'."},
            {"BEGIN BEGIN SHOW TABLES IN nosuch x 1; END; SELECT 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '1'. | syntax"
                + " error line 1 at position 34 unexpected 'x'. | syntax error line 1 at position 36 unexpected"
                + " '1'."},
            {"BEGIN BEGIN SHOW TABLES IN nosuch x 1; SELECT 1; END; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '1'. | syntax"
                + " error line 1 at position 34 unexpected 'x'. | syntax error line 1 at position 36 unexpected"
                + " '1'."},
            {"BEGIN BEGIN BEGIN SHOW TABLES IN nosuch x 1; END; END; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected '1'. | syntax"
                + " error line 1 at position 40 unexpected 'x'. | syntax error line 1 at position 42 unexpected"
                + " '1'."},
            {"BEGIN SELECT 1; BEGIN SHOW TABLES IN nosuch x 1; END; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected '1'. | syntax"
                + " error line 1 at position 44 unexpected 'x'. | syntax error line 1 at position 46 unexpected"
                + " '1'."},
            {"BEGIN BEGIN SHOW TABLES IN DATASET y; END; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'y'. | syntax"
                + " error line 1 at position 43 unexpected 'END'."},
            {"BEGIN BEGIN SHOW TABLES IN USER x y; END; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'x'. | syntax"
                + " error line 1 at position 34 unexpected 'y'. | syntax error line 1 at position 37 unexpected"
                + " 'END'."},
            {"BEGIN BEGIN SHOW TABLES IN nosuch x 1; RETURN 1; END; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '1'. | syntax"
                + " error line 1 at position 34 unexpected 'x'. | syntax error line 1 at position 36 unexpected"
                + " '1'."},
            {"BEGIN BEGIN SHOW TABLES IN nosuch x . ; END; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected ';'."},
            {"BEGIN BEGIN SHOW TABLES IN a.b.c 1; END; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '1'. | syntax"
                + " error line 1 at position 28 unexpected '.'. | syntax error line 1 at position 30 unexpected"
                + " '.'."},
            {"BEGIN BEGIN SHOW TABLES IN nosuch x = 1; END; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'x'. | syntax"
                + " error line 1 at position 36 unexpected '='."},
            {"BEGIN BEGIN SHOW TABLES IN WITH x y; END; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'WITH'. | "
                + "syntax error line 1 at position 32 unexpected 'x'. | syntax error line 1 at position 42 "
                + "unexpected 'END'."},
            {"BEGIN BEGIN SHOW TABLES IN DATASET 1; END; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected '1'. | syntax"
                + " error line 1 at position 35 unexpected '1'."},
            {"BEGIN BEGIN SHOW TABLES IN USER x y z; END; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'x'. | syntax"
                + " error line 1 at position 34 unexpected 'y'. | syntax error line 1 at position 36 unexpected"
                + " 'z'."},
            {"BEGIN BEGIN SHOW TABLES IN COMPUTE POOL; SELECT 1; END; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected ';'. | syntax"
                + " error line 1 at position 35 unexpected 'POOL'. | syntax error line 1 at position 51 "
                + "unexpected 'END'."},
            {"BEGIN BEGIN SHOW TABLES IN USER x y; SELECT 1; END; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'x'. | syntax"
                + " error line 1 at position 34 unexpected 'y'. | syntax error line 1 at position 47 unexpected"
                + " 'END'."},
            {"BEGIN BEGIN SHOW FUNCTIONS IN 1; END; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '1'. | syntax"
                + " error line 1 at position 27 unexpected 'IN'."},
            {"BEGIN BEGIN SHOW TABLES IN DATASET IDENTIFIER('y') z; END; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'IDENTIFIER'."
                + " | syntax error line 1 at position 45 unexpected '('. | syntax error line 1 at position 54 "
                + "unexpected 'END'."},
            {"BEGIN BEGIN SHOW TABLES IN nosuch x 1 2; END; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '1'. | syntax"
                + " error line 1 at position 34 unexpected 'x'. | syntax error line 1 at position 36 unexpected"
                + " '1'."},
            {"BEGIN BEGIN SHOW TABLES IN COMPUTE POOL 1; END; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '1'. | syntax"
                + " error line 1 at position 35 unexpected 'POOL'. | syntax error line 1 at position 40 "
                + "unexpected '1'."},
            {"BEGIN BEGIN SHOW TABLES IN nosuch x y; RETURN 1; END; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'y'. | syntax"
                + " error line 1 at position 54 unexpected 'END'."},
            {"BEGIN BEGIN SHOW VIEWS IN nosuch x 1; END; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected '1'. | syntax"
                + " error line 1 at position 33 unexpected 'x'. | syntax error line 1 at position 35 unexpected"
                + " '1'."},
            {"BEGIN BEGIN SHOW TABLES IN USER x y; RETURN 1; END; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'x'. | syntax"
                + " error line 1 at position 34 unexpected 'y'. | syntax error line 1 at position 37 unexpected"
                + " 'RETURN'."},
            {"BEGIN BEGIN SHOW TABLES IN nosuch x.y 1; END; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '1'."},
            {"BEGIN BEGIN SHOW TABLES IN nosuch x 1; END; RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected '1'. | syntax"
                + " error line 1 at position 34 unexpected 'x'. | syntax error line 1 at position 36 unexpected"
                + " '1'."},
        });
    }

    @Test
    public void loopAndBranchBodies() {
        assertCells(new String[][] {
            {"BEGIN WHILE (TRUE) DO SHOW TABLES IN nosuch x 1; END WHILE; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected '1'. | syntax"
                + " error line 1 at position 46 unexpected '1'. | syntax error line 1 at position 60 unexpected"
                + " 'END'."},
            {"BEGIN WHILE (TRUE) DO SHOW TABLES IN nosuch 1; END WHILE; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected '1'. | syntax"
                + " error line 1 at position 58 unexpected 'END'."},
            {"BEGIN WHILE (TRUE) DO SHOW TABLES IN nosuch x y; END WHILE; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected 'y'. | syntax"
                + " error line 1 at position 60 unexpected 'END'."},
            {"BEGIN WHILE (TRUE) DO SHOW TABLES IN nosuch x 1; SELECT 1; END WHILE; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected '1'. | syntax"
                + " error line 1 at position 46 unexpected '1'. | syntax error line 1 at position 59 unexpected"
                + " 'END'."},
            {"BEGIN WHILE (TRUE) DO SHOW TABLES IN COMPUTE POOL; END WHILE; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected ';'. | syntax"
                + " error line 1 at position 62 unexpected 'END'."},
            {"BEGIN LOOP SHOW TABLES IN nosuch x 1; END LOOP; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected '1'. | syntax"
                + " error line 1 at position 35 unexpected '1'. | syntax error line 1 at position 48 unexpected"
                + " 'END'."},
            {"BEGIN LOOP SHOW TABLES IN nosuch 1; END LOOP; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '1'. | syntax"
                + " error line 1 at position 46 unexpected 'END'."},
            {"BEGIN LOOP SHOW TABLES IN nosuch x 1; SELECT 1; END LOOP; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected '1'. | syntax"
                + " error line 1 at position 35 unexpected '1'. | syntax error line 1 at position 48 unexpected"
                + " 'END'."},
            {"BEGIN FOR i IN 1 TO 2 DO SHOW TABLES IN nosuch x 1; END FOR; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected '1'. | syntax"
                + " error line 1 at position 49 unexpected '1'."},
            {"BEGIN FOR i IN 1 TO 2 DO SHOW TABLES IN nosuch 1; END FOR; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected '1'. | syntax"
                + " error line 1 at position 54 unexpected 'FOR'."},
            {"BEGIN FOR i IN 1 TO 2 DO SHOW TABLES IN nosuch x 1; SELECT 1; END FOR; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected '1'. | syntax"
                + " error line 1 at position 49 unexpected '1'. | syntax error line 1 at position 62 unexpected"
                + " 'END'."},
            {"BEGIN REPEAT SHOW TABLES IN nosuch x 1; UNTIL (TRUE) END REPEAT; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '1'."},
            {"BEGIN REPEAT SHOW TABLES IN nosuch 1; UNTIL (TRUE) END REPEAT; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected '1'. | syntax"
                + " error line 1 at position 38 unexpected 'UNTIL'. | syntax error line 1 at position 44 "
                + "unexpected '('."},
            {"BEGIN CASE WHEN TRUE THEN SHOW TABLES IN nosuch x 1; END CASE; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected '1'. | syntax"
                + " error line 1 at position 63 unexpected 'END'."},
            {"BEGIN CASE WHEN TRUE THEN SHOW TABLES IN nosuch 1; END CASE; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected '1'. | syntax"
                + " error line 1 at position 61 unexpected 'END'."},
            {"BEGIN CASE WHEN TRUE THEN SHOW TABLES IN nosuch x 1; ELSE SELECT 1; END CASE; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected '1'."},
            {"BEGIN CASE 1 WHEN 1 THEN SHOW TABLES IN nosuch x 1; END CASE; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected '1'. | syntax"
                + " error line 1 at position 62 unexpected 'END'."},
            {"BEGIN WHILE (TRUE) DO SHOW TABLES IN USER x y; END WHILE; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected 'x'. | syntax"
                + " error line 1 at position 58 unexpected 'END'."},
            {"BEGIN WHILE (TRUE) DO SHOW TABLES IN DATASET y; END WHILE; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'y'. | syntax"
                + " error line 1 at position 59 unexpected 'END'."},
            {"BEGIN LOOP SHOW TABLES IN USER x y; END LOOP; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'x'. | syntax"
                + " error line 1 at position 46 unexpected 'END'."},
            {"BEGIN WHILE (TRUE) DO SHOW TABLES IN nosuch x 1; END WHILE; SELECT 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected '1'. | syntax"
                + " error line 1 at position 46 unexpected '1'. | syntax error line 1 at position 70 unexpected"
                + " 'END'."},
            {"BEGIN REPEAT SHOW TABLES IN nosuch x 1; SELECT 1; UNTIL (TRUE) END REPEAT; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '1'. | syntax"
                + " error line 1 at position 50 unexpected 'UNTIL'."},
            {"BEGIN CASE WHEN TRUE THEN SHOW TABLES IN nosuch x 1; SELECT 1; END CASE; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected '1'. | syntax"
                + " error line 1 at position 63 unexpected 'END'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES IN nosuch x 1; END IF; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected '1'. | syntax"
                + " error line 1 at position 56 unexpected 'END'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES IN nosuch x 1; SELECT 1; END IF; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected '1'. | syntax"
                + " error line 1 at position 58 unexpected 'END'."},
            {"BEGIN WHILE (TRUE) DO SHOW TABLES IN nosuch x 1; RETURN 1; END WHILE; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected '1'. | syntax"
                + " error line 1 at position 46 unexpected '1'."},
            {"BEGIN WHILE (TRUE) DO SHOW TABLES IN nosuch x 1; SELECT 1; SELECT 2; END WHILE; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected '1'. | syntax"
                + " error line 1 at position 46 unexpected '1'. | syntax error line 1 at position 69 unexpected"
                + " 'END'."},
            {"BEGIN WHILE (TRUE) DO SHOW TABLES IN COMPUTE POOL; SELECT 1; END WHILE; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected ';'. | syntax"
                + " error line 1 at position 72 unexpected 'END'."},
            {"BEGIN WHILE (TRUE) DO SHOW TABLES IN WITH x y; END WHILE; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'WITH'. | "
                + "syntax error line 1 at position 42 unexpected 'x'. | syntax error line 1 at position 58 "
                + "unexpected 'END'."},
            {"BEGIN WHILE (TRUE) DO SHOW TABLES IN a.b.c 1; END WHILE; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected '1'. | syntax"
                + " error line 1 at position 40 unexpected '.'. | syntax error line 1 at position 43 unexpected"
                + " '1'."},
            {"BEGIN WHILE (TRUE) DO SHOW TABLES IN nosuch x = 1; END WHILE; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'x'. | syntax"
                + " error line 1 at position 62 unexpected 'END'."},
            {"BEGIN WHILE (TRUE) DO SHOW TABLES IN COMPUTE POOL 1; END WHILE; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected '1'. | syntax"
                + " error line 1 at position 50 unexpected '1'. | syntax error line 1 at position 64 unexpected"
                + " 'END'."},
            {"BEGIN WHILE (TRUE) DO SHOW FUNCTIONS IN 1; END WHILE; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '1'. | syntax"
                + " error line 1 at position 54 unexpected 'END'."},
            {"BEGIN WHILE (TRUE) DO SHOW TABLES IN nosuch 1; SELECT 1; END WHILE; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected '1'. | syntax"
                + " error line 1 at position 68 unexpected 'END'."},
            {"BEGIN WHILE (TRUE) DO SHOW TABLES IN nosuch x 1; BREAK; END WHILE; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected '1'. | syntax"
                + " error line 1 at position 46 unexpected '1'."},
            {"BEGIN WHILE (TRUE) DO SHOW TABLES IN nosuch x 1 2; END WHILE; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected '1'. | syntax"
                + " error line 1 at position 46 unexpected '1'. | syntax error line 1 at position 62 unexpected"
                + " 'END'."},
            {"BEGIN FOR i IN 1 TO 2 DO SHOW TABLES IN nosuch x 1; RETURN 1; END FOR; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected '1'. | syntax"
                + " error line 1 at position 49 unexpected '1'."},
            {"BEGIN FOR i IN 1 TO 2 DO SHOW TABLES IN COMPUTE POOL; END FOR; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected ';'. | syntax"
                + " error line 1 at position 58 unexpected 'FOR'."},
            {"BEGIN FOR i IN 1 TO 2 DO SHOW TABLES IN nosuch x 1; END FOR; RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected '1'. | syntax"
                + " error line 1 at position 49 unexpected '1'."},
            {"BEGIN FOR i IN 1 TO 2 DO SHOW TABLES IN nosuch x 1; END FOR; SELECT 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected '1'. | syntax"
                + " error line 1 at position 49 unexpected '1'."},
            {"BEGIN FOR i IN 1 TO 2 DO SHOW TABLES IN WITH x y; END FOR; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'WITH'. | "
                + "syntax error line 1 at position 45 unexpected 'x'. | syntax error line 1 at position 54 "
                + "unexpected 'FOR'."},
            {"BEGIN LOOP SHOW TABLES IN nosuch x 1; END LOOP; RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected '1'. | syntax"
                + " error line 1 at position 35 unexpected '1'. | syntax error line 1 at position 48 unexpected"
                + " 'RETURN'."},
            {"BEGIN WHILE (TRUE) DO SHOW TABLES IN nosuch x 1; END WHILE; RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected '1'. | syntax"
                + " error line 1 at position 46 unexpected '1'. | syntax error line 1 at position 60 unexpected"
                + " 'RETURN'."},
            {"BEGIN REPEAT SHOW TABLES IN nosuch x 1; UNTIL (TRUE) END REPEAT; RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '1'."},
            {"BEGIN REPEAT SHOW TABLES IN COMPUTE POOL; UNTIL (TRUE) END REPEAT; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ';'."},
            {"BEGIN REPEAT SHOW TABLES IN nosuch x 1; SELECT 1; SELECT 2; UNTIL (TRUE) END REPEAT; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '1'. | syntax"
                + " error line 1 at position 60 unexpected 'UNTIL'."},
            {"BEGIN CASE WHEN TRUE THEN SHOW TABLES IN nosuch x 1; WHEN FALSE THEN SELECT 1; END CASE; "
                + "END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected '1'."},
            {"BEGIN CASE WHEN TRUE THEN SHOW TABLES IN COMPUTE POOL; END CASE; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected ';'. | syntax"
                + " error line 1 at position 65 unexpected 'END'."},
            {"BEGIN CASE WHEN TRUE THEN SHOW TABLES IN nosuch x 1; RETURN 1; END CASE; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected '1'."},
            {"BEGIN CASE WHEN TRUE THEN SHOW TABLES IN nosuch x 1; ELSE SELECT 1; SELECT 2; END CASE; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected '1'."},
            {"BEGIN CASE WHEN TRUE THEN SHOW TABLES IN WITH x y; END CASE; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'WITH'. | "
                + "syntax error line 1 at position 46 unexpected 'x'. | syntax error line 1 at position 61 "
                + "unexpected 'END'."},
            {"BEGIN LOOP SHOW TABLES IN a.b.c 1; END LOOP; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '1'. | syntax"
                + " error line 1 at position 29 unexpected '.'. | syntax error line 1 at position 32 unexpected"
                + " '1'."},
            {"BEGIN FOR i IN 1 TO 2 DO SHOW TABLES IN nosuch x 1; SELECT 1; SELECT 2; END FOR; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected '1'. | syntax"
                + " error line 1 at position 49 unexpected '1'. | syntax error line 1 at position 72 unexpected"
                + " 'END'."},
            {"BEGIN CASE WHEN TRUE THEN SHOW TABLES IN COMPUTE POOL 1; END CASE; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 54 unexpected '1'. | syntax"
                + " error line 1 at position 67 unexpected 'END'."},
            {"BEGIN REPEAT SHOW TABLES IN nosuch x 1; SELECT 1; UNTIL (TRUE) END REPEAT; RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '1'. | syntax"
                + " error line 1 at position 50 unexpected 'UNTIL'."},
            {"BEGIN LOOP SHOW TABLES WITH x; SELECT 1; END LOOP; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'x'. | syntax"
                + " error line 1 at position 51 unexpected 'END'."},
            {"BEGIN WHILE (TRUE) DO SHOW TABLES IN nosuch x.y 1; END WHILE; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected '1'."},
            {"BEGIN CASE WHEN TRUE THEN SHOW TABLES IN nosuch 1; SELECT 1; END CASE; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected '1'. | syntax"
                + " error line 1 at position 71 unexpected 'END'."},
        });
    }

    @Test
    public void ifBodies() {
        assertCells(new String[][] {
            {"BEGIN IF (TRUE) THEN SHOW TABLES IN USER x y; ELSE SELECT 1; SELECT 2; END IF; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'x'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES WITH x; ELSE SELECT 1; SELECT 2; END IF; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'x'. | syntax"
                + " error line 1 at position 74 unexpected 'END'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES IN USER x y; ELSE SELECT 1; END IF; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'x'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES WITH x; ELSE SELECT 1; END IF; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'x'. | syntax"
                + " error line 1 at position 64 unexpected 'END'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES WITH x; END IF; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'x'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES IN USER x y; END IF; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'x'. | syntax"
                + " error line 1 at position 54 unexpected 'END'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES IN nosuch x 1; ELSE SELECT 1; SELECT 2; END IF; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected '1'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES IN USER x y; ELSE SELECT 1; SELECT 2; SELECT 3; END IF; "
                + "END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'x'."},
            {"BEGIN SHOW TABLES WITH x; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'x'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES IN USER x y; ELSEIF (TRUE) THEN SELECT 1; SELECT 2; END IF;"
                + " END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'x'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES IN nosuch x 1; RETURN 1; END IF; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected '1'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES IN COMPUTE POOL; END IF; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected ';'. | syntax"
                + " error line 1 at position 58 unexpected 'END'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES IN WITH x y; END IF; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'WITH'. | "
                + "syntax error line 1 at position 41 unexpected 'x'. | syntax error line 1 at position 54 "
                + "unexpected 'END'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES IN WITH x y; ELSE SELECT 1; SELECT 2; END IF; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'WITH'. | "
                + "syntax error line 1 at position 41 unexpected 'x'. | syntax error line 1 at position 46 "
                + "unexpected 'ELSE'. | syntax error line 1 at position 71 unexpected 'END'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES IN nosuch x 1; ELSE SELECT 1; END IF; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected '1'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES IN USER x y z; ELSE SELECT 1; END IF; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'x'. | syntax"
                + " error line 1 at position 48 unexpected 'ELSE'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES WITH x; RETURN 1; END IF; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'x'. | syntax"
                + " error line 1 at position 59 unexpected 'END'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES WITH x; SELECT 1; END IF; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'x'. | syntax"
                + " error line 1 at position 59 unexpected 'END'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES WITH x y; ELSE SELECT 1; END IF; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'x'. | syntax"
                + " error line 1 at position 43 unexpected 'ELSE'."},
            {"BEGIN WHILE (TRUE) DO SHOW TABLES WITH x; END WHILE; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'x'."},
            {"BEGIN BEGIN SHOW TABLES WITH x; END; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'x'."},
            {"BEGIN SHOW TABLES WITH x; SELECT 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'x'. | syntax"
                + " error line 1 at position 36 unexpected 'END'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET y; ELSE SELECT 1; SELECT 2; END IF; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'y'. | syntax"
                + " error line 1 at position 47 unexpected 'ELSE'. | syntax error line 1 at position 72 "
                + "unexpected 'END'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES IN USER x; ELSE SELECT 1; SELECT 2; END IF; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'x'. | syntax"
                + " error line 1 at position 44 unexpected 'ELSE'. | syntax error line 1 at position 69 "
                + "unexpected 'END'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES IN DATASET x y; ELSE SELECT 1; END IF; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 44 unexpected 'x'."},
            {"BEGIN IF (TRUE) THEN SHOW VIEWS WITH x; ELSE SELECT 1; SELECT 2; SELECT 3; END IF; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'x'. | syntax"
                + " error line 1 at position 83 unexpected 'END'."},
            {"BEGIN IF (TRUE) THEN SHOW TABLES IN WITH x y z; ELSE SELECT 1; END IF; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'WITH'. | "
                + "syntax error line 1 at position 41 unexpected 'x'."},
        });
    }

    @Test
    public void scopeWith() {
        assertCells(new String[][] {
            {"BEGIN SHOW TABLES IN WITH x y; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'WITH'. | "
                + "syntax error line 1 at position 26 unexpected 'x'. | syntax error line 1 at position 31 "
                + "unexpected 'END'."},
            {"BEGIN SHOW VIEWS IN WITH x y; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'WITH'. | "
                + "syntax error line 1 at position 25 unexpected 'x'. | syntax error line 1 at position 30 "
                + "unexpected 'END'."},
            {"BEGIN SHOW PROCEDURES IN WITH x y; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'WITH'. | "
                + "syntax error line 1 at position 30 unexpected 'x'. | syntax error line 1 at position 35 "
                + "unexpected 'END'."},
            {"BEGIN SHOW USER FUNCTIONS IN WITH x y; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'WITH'. | "
                + "syntax error line 1 at position 34 unexpected 'x'. | syntax error line 1 at position 39 "
                + "unexpected 'END'."},
            {"BEGIN SHOW TABLES IN WITH x y; SELECT 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'WITH'. | "
                + "syntax error line 1 at position 26 unexpected 'x'. | syntax error line 1 at position 41 "
                + "unexpected 'END'."},
            {"BEGIN SHOW TABLES IN WITH x; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'WITH'. | "
                + "syntax error line 1 at position 26 unexpected 'x'."},
            {"BEGIN SHOW TABLES IN WITH x y z; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'WITH'. | "
                + "syntax error line 1 at position 26 unexpected 'x'. | syntax error line 1 at position 30 "
                + "unexpected 'z'."},
            {"BEGIN SHOW TABLES IN WITH 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'WITH'. | "
                + "syntax error line 1 at position 26 unexpected '1'."},
            {"BEGIN SHOW TABLES IN WITH x 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'WITH'. | "
                + "syntax error line 1 at position 26 unexpected 'x'."},
            {"BEGIN SHOW BUILTIN FUNCTIONS IN WITH x y; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'WITH'. | "
                + "syntax error line 1 at position 37 unexpected 'x'. | syntax error line 1 at position 42 "
                + "unexpected 'END'."},
            {"BEGIN SHOW FUNCTIONS IN WITH x y; SELECT 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'WITH'. | "
                + "syntax error line 1 at position 21 unexpected 'IN'."},
            {"BEGIN SHOW TABLES IN WITH x y; RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'WITH'. | "
                + "syntax error line 1 at position 26 unexpected 'x'. | syntax error line 1 at position 31 "
                + "unexpected 'RETURN'."},
            {"BEGIN WHILE (TRUE) DO SHOW VIEWS IN WITH x y; SELECT 1; END WHILE; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'WITH'. | "
                + "syntax error line 1 at position 41 unexpected 'x'. | syntax error line 1 at position 67 "
                + "unexpected 'END'."},
        });
    }

    @Test
    public void flowArrow() {
        assertCells(new String[][] {
            {"BEGIN SHOW TABLES IN nosuch x ->> y; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'y'."},
            {"BEGIN SHOW TABLES IN nosuch x ->> 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '1'."},
            {"BEGIN SHOW TABLES IN nosuch ->> y; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'y'."},
            {"SHOW TABLES IN nosuch x ->> y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'y'."},
            {"BEGIN SHOW TABLES IN nosuch x -> y; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '->'. | "
                + "syntax error line 1 at position 28 unexpected 'x'."},
            {"BEGIN SHOW TABLES IN nosuch x || y; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '||'. | "
                + "syntax error line 1 at position 28 unexpected 'x'."},
            {"BEGIN SHOW TABLES IN nosuch x ->> y; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'y'."},
            {"BEGIN SHOW TABLES IN nosuch x ->> y z; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'y'. | syntax"
                + " error line 1 at position 39 unexpected 'END'."},
            {"BEGIN SHOW TABLES IN nosuch ->> y; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'y'."},
            {"BEGIN SHOW TABLES IN nosuch x y ->> z; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'. | syntax"
                + " error line 1 at position 36 unexpected 'z'."},
            {"BEGIN SELECT 1 ->> y; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'y'."},
            {"BEGIN SHOW TABLES IN nosuch x ->> 'y'; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ''y''."},
            {"BEGIN SHOW TABLES IN ->> y; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '->>'. | "
                + "syntax error line 1 at position 25 unexpected 'y'."},
            {"BEGIN SHOW TABLES IN nosuch x ->> y; SELECT 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'y'. | syntax"
                + " error line 1 at position 47 unexpected 'END'."},
            {"BEGIN SHOW TABLES IN USER ->> y; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'y'."},
            {"BEGIN SHOW TABLES IN nosuch x->>y; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'y'."},
            {"SHOW TABLES IN nosuch ->> y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'y'."},
            {"BEGIN SHOW TABLES IN DATASET x ->> y; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'x'. | syntax"
                + " error line 1 at position 35 unexpected 'y'."},
            {"BEGIN SELECT 1 ->> y; SELECT 2; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'y'. | syntax"
                + " error line 1 at position 32 unexpected 'END'."},
            {"BEGIN SELECT 1 ->> \"y\"; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '\"y\"'."},
            {"BEGIN SHOW TABLES IN nosuch x ->> y; RETURN 1; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected 'y'."},
        });
    }

    @Test
    public void scopeClassName() {
        assertCells(new String[][] {
            {"BEGIN SHOW TABLES IN nosuch LIMIT; END;",
                "REFUSED syntax error line 1 at position 21 unexpected 'nosuch'."},
            {"BEGIN SHOW TABLES IN nosuch STARTS; END;",
                "REFUSED syntax error line 1 at position 21 unexpected 'nosuch'."},
            {"BEGIN SHOW TABLES IN ACCOUNT x; END;",
                "REFUSED SQL compilation error: | Must specify the full search path starting from database "
                + "for X"},
            {"SHOW TABLES IN nosuch LIMIT",
                "REFUSED syntax error line 1 at position 15 unexpected 'nosuch'."},
            {"SHOW TABLES IN ACCOUNT x",
                "REFUSED SQL compilation error: | Must specify the full search path starting from database "
                + "for X"},
            {"BEGIN SHOW TABLES IN ACCOUNT x y; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'y'. | syntax"
                + " error line 1 at position 34 unexpected 'END'."},
            {"BEGIN SHOW TABLES IN nosuch x LIMIT; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected ';'."},
            {"BEGIN SHOW VIEWS IN ACCOUNT x; END;",
                "REFUSED SQL compilation error: | Must specify the full search path starting from database "
                + "for X"},
            {"SHOW TABLES IN nosuch x",
                "REFUSED syntax error line 1 at position 15 unexpected 'nosuch'."},
            {"SHOW SCHEMAS IN nosuch x",
                "REFUSED syntax error line 1 at position 16 unexpected 'nosuch'."},
            {"SHOW STAGES IN nosuch x",
                "REFUSED syntax error line 1 at position 15 unexpected 'nosuch'."},
            {"SHOW TABLES IN nosuch IDENTIFIER('a')",
                "REFUSED syntax error line 1 at position 15 unexpected 'nosuch'."},
            {"BEGIN SHOW TABLES IN nosuch x; END;",
                "REFUSED syntax error line 1 at position 21 unexpected 'nosuch'."},
            {"SHOW TABLES IN nosuch x LIMIT 1",
                "REFUSED syntax error line 1 at position 15 unexpected 'nosuch'."},
            {"EXECUTE IMMEDIATE 'SHOW TABLES IN nosuch x'",
                "REFUSED syntax error line 1 at position 15 unexpected 'nosuch'."},
        });
    }
}
