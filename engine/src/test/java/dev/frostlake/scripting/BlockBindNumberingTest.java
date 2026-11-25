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
 * The numbers of the binds a miscounted call of a user's function echoes for a block's variables (live-verified): a
 * block run directly starts past two for each literal it writes, a NULL statement among them, one run by EXECUTE
 * IMMEDIATE from the text's own start; a declaration without a value takes its number where it stands, and every
 * statement before a variable's declaration draws its own count, a range FOR's counter after the loop's statements.
 */
public class BlockBindNumberingTest extends MeasuredCellsTestSupport {

    @Test
    public void declarationsWithoutAValue() {
        engine.execute("CREATE OR REPLACE FUNCTION n61(a INT, b INT) RETURNS INT AS 'a + b'");
        assertCells(new String[][] {
            {"DECLARE x INT; BEGIN x := 1; RETURN n61(x, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 36 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_8), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE x INT; BEGIN x := 1; RETURN n61(x, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_4), 1, 2)] expected 2, got 3"},
            {"DECLARE x INT; y INT; BEGIN x := 1; y := 2; RETURN n61(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 51 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_12), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE x INT; y INT; BEGIN x := 1; y := 2; RETURN n61(y, 1, 2); END; "
                + "$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 52 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_6), 1, 2)] expected 2, got 3"},
            {"DECLARE x INT; BEGIN x := 1 + 2; RETURN n61(x, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 40 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_10), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE x INT; BEGIN x := 1 + 2; RETURN n61(x, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 41 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_4), 1, 2)] expected 2, got 3"},
            {"DECLARE x INT; BEGIN LET y := 5; x := 1; RETURN n61(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 48 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_13), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE x INT; BEGIN LET y := 5; x := 1; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 49 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_7), 1, 2)] expected 2, got 3"},
            {"DECLARE x INT; y INT DEFAULT 5; BEGIN x := 1; RETURN n61(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 53 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_13), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE x INT; y INT DEFAULT 5; BEGIN x := 1; RETURN n61(y, 1, 2); END;"
                + " $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 54 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_7), 1, 2)] expected 2, got 3"},
            {"DECLARE y INT DEFAULT 5; x INT; BEGIN x := 1; RETURN n61(x, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 53 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_13), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE y INT DEFAULT 5; x INT; BEGIN x := 1; RETURN n61(x, 1, 2); END;"
                + " $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 54 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_7), 1, 2)] expected 2, got 3"},
            {"DECLARE x INT; BEGIN x := 1; LET y := 5; RETURN n61(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 48 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_16), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE x INT; BEGIN x := 1; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 49 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_10), 1, 2)] expected 2, got 3"},
            {"DECLARE x INT; BEGIN x := 1; LET y := x; RETURN n61(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 48 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_15), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE x INT; BEGIN x := 1; LET y := x; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 49 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_11), 1, 2)] expected 2, got 3"},
            {"DECLARE x INT; y INT DEFAULT 1 + 2; BEGIN x := 1; RETURN n61(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 57 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_18), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE x INT; y INT DEFAULT 1 + 2; BEGIN x := 1; RETURN n61(y, 1, 2); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 58 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_10), 1, 2)] expected 2, got 3"},
            {"DECLARE x INT; y INT; z INT; BEGIN x := 1; y := 2; z := 3; RETURN n61(z, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 66 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Z_16), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE x INT; y INT; z INT; BEGIN x := 1; y := 2; z := 3; RETURN "
                + "n61(z, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 67 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Z_8), 1, 2)] expected 2, got 3"},
            {"DECLARE x INT; BEGIN x := 1; RETURN n61(x, x, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 36 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_6), TO_NUMBER(:X_6), 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE x INT; BEGIN x := 1; RETURN n61(x, x, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_4), TO_NUMBER(:X_4), 2)] expected 2, got 3"},
            {"BEGIN LET x := 1; RETURN n61(x, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 25 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_9), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; RETURN n61(x, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_5), 1, 2)] expected 2, got 3"},
            {"BEGIN LET x := 1 + 2; RETURN n61(x, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 29 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_14), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1 + 2; RETURN n61(x, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_8), 1, 2)] expected 2, got 3"},
            {"BEGIN LET y := 1; LET x := 2; RETURN n61(x, y, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_13), TO_NUMBER(:Y_9), 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET y := 1; LET x := 2; RETURN n61(x, y, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_9), TO_NUMBER(:Y_5), 2)] expected 2, got 3"},
            {"DECLARE x INT DEFAULT 1 + 2; BEGIN RETURN n61(x, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 42 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_14), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE x INT DEFAULT 1 + 2; BEGIN RETURN n61(x, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 43 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_8), 1, 2)] expected 2, got 3"},
            {"DECLARE x INT DEFAULT 1; BEGIN RETURN n61(x, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_9), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE x INT DEFAULT 1; BEGIN RETURN n61(x, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 39 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_5), 1, 2)] expected 2, got 3"},
            {"DECLARE y INT DEFAULT 1; x INT DEFAULT 2; BEGIN RETURN n61(x, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 55 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_14), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE y INT DEFAULT 1; x INT DEFAULT 2; BEGIN RETURN n61(x, 1, 2); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 56 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_8), 1, 2)] expected 2, got 3"},
            {"BEGIN LET x INT := 1; LET y INT := 2; RETURN n61(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 45 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_15), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x INT := 1; LET y INT := 2; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 46 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_9), 1, 2)] expected 2, got 3"},
            {"DECLARE x INT; BEGIN x := 1; x := 2; RETURN n61(x, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 44 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_10), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE x INT; BEGIN x := 1; x := 2; RETURN n61(x, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 45 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_4), 1, 2)] expected 2, got 3"},
            {"DECLARE x INT; y INT; BEGIN x := 1; y := x; RETURN n61(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 51 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_10), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE x INT; y INT; BEGIN x := 1; y := x; RETURN n61(y, 1, 2); END; "
                + "$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 52 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_6), 1, 2)] expected 2, got 3"},
            {"DECLARE x INT; y INT; BEGIN x := 1 + 2; RETURN n61(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 47 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_12), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE x INT; y INT; BEGIN x := 1 + 2; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 48 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_6), 1, 2)] expected 2, got 3"},
            {"DECLARE x INT; y INT; BEGIN x := 1; RETURN n61(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 43 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_10), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE x INT; y INT; BEGIN x := 1; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 44 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_6), 1, 2)] expected 2, got 3"},
            {"DECLARE x INT; y INT; BEGIN RETURN n61(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 35 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_8), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE x INT; y INT; BEGIN RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 36 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_6), 1, 2)] expected 2, got 3"},
            {"DECLARE x INT; y INT; BEGIN x := ABS(1); RETURN n61(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 48 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_10), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE x INT; y INT; BEGIN x := ABS(1); RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 49 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_6), 1, 2)] expected 2, got 3"},
            {"DECLARE x INT; BEGIN x := 1; LET y := 1 + 2; RETURN n61(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 52 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_21), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE x INT; BEGIN x := 1; LET y := 1 + 2; RETURN n61(y, 1, 2); END; "
                + "$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 53 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_13), 1, 2)] expected 2, got 3"},
            {"DECLARE x INT; BEGIN x := 1 + 2; LET y := 5; RETURN n61(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 52 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_21), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE x INT; BEGIN x := 1 + 2; LET y := 5; RETURN n61(y, 1, 2); END; "
                + "$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 53 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_13), 1, 2)] expected 2, got 3"},
            {"DECLARE x INT; BEGIN x := ABS(1); LET y := 5; RETURN n61(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 53 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_18), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE x INT; BEGIN x := ABS(1); LET y := 5; RETURN n61(y, 1, 2); END;"
                + " $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 54 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_12), 1, 2)] expected 2, got 3"},
            {"BEGIN LET x := 1; x := 2; LET y := 5; RETURN n61(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 45 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_20), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; x := 2; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 46 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_12), 1, 2)] expected 2, got 3"},
            {"BEGIN LET x := 1; x := 2 + 3; LET y := 5; RETURN n61(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 49 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_25), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; x := 2 + 3; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 50 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_15), 1, 2)] expected 2, got 3"},
            {"DECLARE x INT; BEGIN LET y := 5; RETURN n61(x, y, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 40 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_6), TO_NUMBER(:Y_9), 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE x INT; BEGIN LET y := 5; RETURN n61(x, y, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 41 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_4), TO_NUMBER(:Y_7), 2)] expected 2, got 3"},
            {"DECLARE x INT DEFAULT 1; y INT; BEGIN y := 2; RETURN n61(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 53 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_13), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE x INT DEFAULT 1; y INT; BEGIN y := 2; RETURN n61(y, 1, 2); END;"
                + " $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 54 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_7), 1, 2)] expected 2, got 3"},
            {"DECLARE x INT DEFAULT 1 + 2; y INT; BEGIN y := 2; RETURN n61(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 57 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_18), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE x INT DEFAULT 1 + 2; y INT; BEGIN y := 2; RETURN n61(y, 1, 2); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 58 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_10), 1, 2)] expected 2, got 3"},
            {"BEGIN LET x := 1 + 2; LET y := 5; RETURN n61(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 41 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_20), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1 + 2; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 42 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_12), 1, 2)] expected 2, got 3"},
            {"DECLARE x INT; BEGIN x := 1; RETURN n61(x, 1, 2, 3); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 36 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_10), 1, 2, 3)] expected 2, got 4"},
            {"EXECUTE IMMEDIATE $$ DECLARE x INT; BEGIN x := 1; RETURN n61(x, 1, 2, 3); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_4), 1, 2, 3)] expected 2, got 4"},
            {"DECLARE x INT; BEGIN x := -1; LET y := 5; RETURN n61(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 49 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_17), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE x INT; BEGIN x := -1; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 50 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_11), 1, 2)] expected 2, got 3"},
        });
    }

    @Test
    public void loopsBlocksAndTexts() {
        engine.execute("CREATE OR REPLACE FUNCTION n61(a INT, b INT) RETURNS INT AS 'a + b'");
        assertCells(new String[][] {
            {"BEGIN FOR i IN 1 TO 2 DO RETURN n61(i, 1, 2); END FOR; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 32 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:I_21, 9, 0), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN FOR i IN 1 TO 2 DO RETURN n61(i, 1, 2); END FOR; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 33 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:I_15, 9, 0), 1, 2)] expected 2, got 3"},
            {"BEGIN FOR i IN 1 TO 3 + 4 DO RETURN n61(i, 1, 2); END FOR; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 36 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:I_26, 9, 0), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN FOR i IN 1 TO 3 + 4 DO RETURN n61(i, 1, 2); END FOR; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:I_18, 9, 0), 1, 2)] expected 2, got 3"},
            {"BEGIN LET x := 1; FOR i IN 1 TO 2 DO RETURN n61(x, 1, 2); END FOR; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 44 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_13), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; FOR i IN 1 TO 2 DO RETURN n61(x, 1, 2); END FOR; END;"
                + " $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 45 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_5), 1, 2)] expected 2, got 3"},
            {"BEGIN FOR i IN 1 TO 2 DO LET y := 5; RETURN n61(y, 1, 2); END FOR; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 44 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_15), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN FOR i IN 1 TO 2 DO LET y := 5; RETURN n61(y, 1, 2); END FOR; END;"
                + " $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 45 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_7), 1, 2)] expected 2, got 3"},
            {"BEGIN FOR i IN 1 TO 2 DO RETURN n61(i, i, 2); END FOR; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 32 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:I_20, 9, 0), TO_NUMBER(:I_20, 9, 0), 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN FOR i IN 1 TO 2 DO RETURN n61(i, i, 2); END FOR; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 33 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:I_16, 9, 0), TO_NUMBER(:I_16, 9, 0), 2)] expected 2, got 3"},
            {"BEGIN FOR i IN 1 TO 2 DO NULL; END FOR; LET y := 5; RETURN n61(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 59 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_23), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN FOR i IN 1 TO 2 DO NULL; END FOR; LET y := 5; RETURN n61(y, 1, "
                + "2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 60 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_13), 1, 2)] expected 2, got 3"},
            {"BEGIN LET y := 5; FOR i IN 1 TO 2 DO RETURN n61(i, 1, 2); END FOR; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 44 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:I_27, 9, 0), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET y := 5; FOR i IN 1 TO 2 DO RETURN n61(i, 1, 2); END FOR; END;"
                + " $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 45 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:I_19, 9, 0), 1, 2)] expected 2, got 3"},
            {"BEGIN LET x := 1; BEGIN LET y := 2; RETURN n61(y, 1, 2); END; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 43 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_16), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; BEGIN LET y := 2; RETURN n61(y, 1, 2); END; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 44 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_10), 1, 2)] expected 2, got 3"},
            {"BEGIN LET x := 1; IF (x > 0) THEN RETURN n61(x, 1, 2); END IF; RETURN 0; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 41 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_13), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; IF (x > 0) THEN RETURN n61(x, 1, 2); END IF; RETURN "
                + "0; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 42 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_5), 1, 2)] expected 2, got 3"},
            {"DECLARE x INT; BEGIN LET y := 5; RETURN n61(y, x, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 40 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_9), TO_NUMBER(:X_6), 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE x INT; BEGIN LET y := 5; RETURN n61(y, x, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 41 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_7), TO_NUMBER(:X_4), 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE 'BEGIN LET x := 1; RETURN n61(x, 1, 2); END;'",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 25 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_5), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE 'BEGIN LET x := 1; ' || 'RETURN n61(x, 1, 2); END;'",
                "REFUSED SQL compilation error: | syntax error line 1 at position 0 unexpected '||'."},
            {"BEGIN EXECUTE IMMEDIATE 'BEGIN LET x := 1; RETURN n61(x, 1, 2); END;'; RETURN 0; END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Uncaught "
                + "exception of type 'EXPRESSION_ERROR' on line 1 at position 25 : SQL compilation error: error"
                + " line 1 at position 7 | too many arguments for function [N61(TO_NUMBER(:X_6), 1, 2)] "
                + "expected 2, got 3"},
            {"BEGIN LET q := 3; EXECUTE IMMEDIATE 'BEGIN LET x := 1; RETURN n61(x, 1, 2); END;'; RETURN 0;"
                + " END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 18 : Uncaught "
                + "exception of type 'EXPRESSION_ERROR' on line 1 at position 25 : SQL compilation error: error"
                + " line 1 at position 7 | too many arguments for function [N61(TO_NUMBER(:X_6), 1, 2)] "
                + "expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE x INT; BEGIN x := 1; RETURN n61(x, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_4), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE 'DECLARE x INT; BEGIN x := 1; RETURN n61(x, 1, 2); END;'",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 36 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_4), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN FOR i IN REVERSE 1 TO 2 DO FOR j IN 1 TO 3 DO RETURN n61(j, i, "
                + "2); END FOR; END FOR; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 60 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:J_18, 9, 0), TO_NUMBER(:I_23, 9, 0), 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN FOR i IN 1 TO 2 DO FOR j IN i TO 3 DO RETURN n61(j, i, 2); END "
                + "FOR; END FOR; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 52 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:J_19, 9, 0), TO_NUMBER(:I_24, 9, 0), 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; FOR i IN 1 TO x + 2 DO FOR j IN 1 TO 3 DO RETURN "
                + "n61(j, i, 2); END FOR; END FOR; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 68 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:J_22, 9, 0), TO_NUMBER(:I_31, 9, 0), 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN FOR i IN 1 TO 2 DO FOR j IN 1 TO i DO RETURN n61(j, i, 2); END "
                + "FOR; END FOR; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 52 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:J_19, 9, 0), TO_NUMBER(:I_24, 9, 0), 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN FOR i IN 1 TO 2 DO LET z := i; RETURN n61(z, 1, 2); END FOR; END;"
                + " $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 45 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Z_8), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN FOR i IN 1 TO 2 DO FOR j IN 1 TO 3 DO RETURN n61(j, 1, 2); END "
                + "FOR; END FOR; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 52 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:J_17, 9, 0), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; FOR i IN x TO 2 DO RETURN n61(i, 1, 2); END FOR; END;"
                + " $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 45 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:I_20, 9, 0), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN FOR i IN 1 TO 2 DO FOR j IN i + 0 TO 3 DO RETURN n61(j, i, 2); "
                + "END FOR; END FOR; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 56 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:J_22, 9, 0), TO_NUMBER(:I_27, 9, 0), 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN FOR i IN 1 TO 2 DO LET k := 0; FOR j IN k TO 3 DO RETURN n61(j, "
                + "i, 2); END FOR; END FOR; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 64 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:J_23, 9, 0), TO_NUMBER(:I_28, 9, 0), 2)] expected 2, got 3"},
        });
    }

    @Test
    public void statementsDrawTheirCounts() {
        engine.execute("CREATE OR REPLACE FUNCTION n61(a INT, b INT) RETURNS INT AS 'a + b'");
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ BEGIN NULL; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 32 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_6), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN IF (TRUE) THEN NULL; END IF; LET y := 5; RETURN n61(y, 1, 2); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 55 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_10), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN IF (1 > 0) THEN NULL; END IF; LET y := 5; RETURN n61(y, 1, 2); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 56 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_13), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; IF (x > 0) THEN NULL; END IF; LET y := 5; RETURN "
                + "n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 68 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_18), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; IF (x > 0) THEN LET y := 5; RETURN n61(y, 1, 2); END "
                + "IF; RETURN 0; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 54 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_17), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; WHILE (x < 3) DO x := x + 1; END WHILE; LET y := 5; "
                + "RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 78 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_24), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN FOR i IN 1 TO 2 DO NULL; END FOR; FOR j IN 1 TO 2 DO RETURN "
                + "n61(j, 1, 2); END FOR; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 67 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:J_23, 9, 0), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN FOR i IN 1 TO 2 DO LET z := 3; RETURN n61(i, 1, 2); END FOR; END;"
                + " $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 45 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:I_19, 9, 0), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN FOR i IN 1 TO 2 DO RETURN n61(i, 1, ABS(2)); END FOR; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 33 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:I_17, 9, 0), 1, ABS(2))] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; FOR i IN 1 TO x DO RETURN n61(i, 1, 2); END FOR; END;"
                + " $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 45 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:I_20, 9, 0), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN FOR i IN 1 TO 2 DO NULL; NULL; END FOR; LET y := 5; RETURN n61(y,"
                + " 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 66 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_14), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN FOR i IN REVERSE 1 TO 2 DO RETURN n61(i, 1, 2); END FOR; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 41 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:I_15, 9, 0), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN FOR i IN 1 TO 2 DO IF (i > 0) THEN RETURN n61(i, 1, 2); END IF; "
                + "END FOR; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 49 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:I_23, 9, 0), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; LET r := 0; r := n61(x, 1, 2); RETURN r; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 36 : SQL "
                + "compilation error: error line 1 at position 15 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_5), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; CASE WHEN x > 0 THEN RETURN n61(x, 1, 2); END CASE; "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 47 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_5), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; RETURN 0; END; $$",
                "0"},
            {"EXECUTE IMMEDIATE $$ BEGIN NULL; NULL; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_7), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; RETURN n61(x, 1, 2); LET y := 5; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_5), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; IF (x > 0) THEN NULL; ELSE NULL; END IF; LET y := 5; "
                + "RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 79 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_20), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; IF (x > 0 AND x < 5) THEN NULL; END IF; LET y := 5; "
                + "RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 78 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_25), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN FOR i IN 1 TO 2 DO RETURN n61(i, 1, 2) + 1; END FOR; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 33 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:I_18, 9, 0), 1, 2)] expected 2, got 3"},
            {"BEGIN IF (1 > 0) THEN NULL; END IF; LET y := 5; RETURN n61(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 55 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_23), 1, 2)] expected 2, got 3"},
            {"BEGIN FOR i IN 1 TO 2 DO LET z := 3; RETURN n61(i, 1, 2); END FOR; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 44 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:I_27, 9, 0), 1, 2)] expected 2, got 3"},
            {"BEGIN LET x := 1; WHILE (x < 3) DO x := x + 1; END WHILE; LET y := 5; RETURN n61(y, 1, 2); "
                + "END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 77 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_34), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; IF (x > 0) THEN RETURN 0; END IF; LET y := 5; RETURN "
                + "n61(y, 1, 2); END; $$",
                "0"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; IF (x > 0) THEN RETURN x + 1; END IF; LET y := 5; "
                + "RETURN n61(y, 1, 2); END; $$",
                "2"},
            {"EXECUTE IMMEDIATE $$ BEGIN FOR i IN 1 TO 2 DO RETURN n61(i, 1, 2); NULL; END FOR; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 33 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:I_16, 9, 0), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN FOR i IN 1 TO 2 DO IF (i > 0) THEN RETURN n61(i, 1, 2); END IF; "
                + "NULL; END FOR; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 49 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:I_24, 9, 0), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; IF (x > 0) THEN NULL; ELSEIF (x < 0) THEN NULL; END "
                + "IF; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 94 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_27), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LOOP BREAK; END LOOP; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 48 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_10), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; REPEAT x := x + 1; UNTIL (x > 2) END REPEAT; LET y :="
                + " 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 83 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_24), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; CASE WHEN x > 0 THEN NULL; END CASE; LET y := 5; "
                + "RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 75 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_17), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; BEGIN BEGIN LET y := 2; RETURN n61(y, 1, 2); END; "
                + "END; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 50 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_11), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; DECLARE z INT DEFAULT 3; BEGIN RETURN n61(z, 1, 2); "
                + "END; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 57 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Z_10), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN BEGIN NULL; END; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 43 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_7), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN FOR i IN 1 TO 2 DO FOR j IN 1 TO 2 DO RETURN n61(i, j, 2); END "
                + "FOR; END FOR; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 52 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:I_23, 9, 0), TO_NUMBER(:J_18, 9, 0), 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN FOR i IN 1 TO 2 DO NULL; RETURN n61(i, 1, 2); END FOR; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 39 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:I_16, 9, 0), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; IF (x > 0) THEN RETURN x; END IF; LET y := 5; RETURN "
                + "n61(y, 1, 2); END; $$",
                "1"},
            {"EXECUTE IMMEDIATE $$ BEGIN FOR i IN 1 TO 2 DO IF (i > 5) THEN BREAK; END IF; RETURN n61(i, "
                + "1, 2); END FOR; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 64 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:I_24, 9, 0), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET y := 5; RETURN n61(y, 1, 2); EXCEPTION WHEN OTHER THEN RETURN"
                + " 0; END; $$",
                "0"},
            {"EXECUTE IMMEDIATE $$ BEGIN BEGIN LET y := 2; END; LET z := 5; RETURN n61(z, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 49 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Z_10), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE x INT; BEGIN BEGIN x := 1; END; RETURN n61(x, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 48 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_4), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; IF (x > 0) THEN NULL; ELSEIF (x < 0) THEN NULL; ELSE "
                + "NULL; END IF; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 105 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_29), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; IF (x < 0) THEN RETURN 0; END IF; LET y := 5; RETURN "
                + "n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 72 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_19), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; IF (x < 0) THEN RETURN x + 1; END IF; LET y := 5; "
                + "RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 76 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_23), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; CASE WHEN x > 0 THEN NULL; WHEN x < 0 THEN NULL; END "
                + "CASE; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 97 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_24), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; CASE x WHEN 1 THEN NULL; END CASE; LET y := 5; RETURN"
                + " n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 73 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_15), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; CASE WHEN x > 0 THEN NULL; ELSE NULL; END CASE; LET y"
                + " := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 86 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_19), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LOOP NULL; BREAK; END LOOP; LET y := 5; RETURN n61(y, 1, 2); END;"
                + " $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 54 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_11), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN FOR i IN 1 TO 2 DO IF (i > 5) THEN CONTINUE; END IF; RETURN "
                + "n61(i, 1, 2); END FOR; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 67 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:I_24, 9, 0), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN WHILE (TRUE) DO BREAK; END WHILE; LET y := 5; RETURN n61(y, 1, "
                + "2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 60 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_10), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN FOR i IN 1 TO 2 DO RETURN n61(i, 1, 2); LET z := 3; END FOR; END;"
                + " $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 33 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:I_19, 9, 0), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; IF (x < 0) THEN RETURN n61(1, 2); END IF; LET y := 5;"
                + " RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 80 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_22), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; WHILE (x < 0) DO RETURN 0; END WHILE; LET y := 5; "
                + "RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 76 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_19), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; IF (x < 0) THEN RETURN 0; RETURN 0; END IF; LET y := "
                + "5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 82 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_21), 1, 2)] expected 2, got 3"},
        });
    }

    @Test
    public void mixedBlocks() {
        engine.execute("CREATE OR REPLACE FUNCTION n61(a INT, b INT) RETURNS INT AS 'a + b'");
        assertCells(new String[][] {
            {"BEGIN LET z := n61(1, 2); LET y := 5; RETURN n61(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 45 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_20), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET z := n61(1, 2); LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 46 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_12), 1, 2)] expected 2, got 3"},
            {"BEGIN LET x := 1; IF (x < 0) THEN RETURN ABS(1); END IF; LET y := 5; RETURN n61(y, 1, 2); "
                + "END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 76 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_31), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; IF (x < 0) THEN RETURN ABS(1); END IF; LET y := 5; "
                + "RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 77 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_21), 1, 2)] expected 2, got 3"},
            {"BEGIN LET x := 1; WHILE (x < 3) DO LET z := x; x := z + 1; END WHILE; FOR i IN x TO 5 DO IF "
                + "(i > 3) THEN BREAK; ELSE NULL; END IF; RETURN n61(i, x, 2); END FOR; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 138 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:I_64, 9, 0), TO_NUMBER(:X_17), 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; WHILE (x < 3) DO LET z := x; x := z + 1; END WHILE; "
                + "FOR i IN x TO 5 DO IF (i > 3) THEN BREAK; ELSE NULL; END IF; RETURN n61(i, x, 2); END FOR; "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 139 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:I_52, 9, 0), TO_NUMBER(:X_5), 2)] expected 2, got 3"},
            {"DECLARE a INT; b VARCHAR DEFAULT 'q'; BEGIN a := LENGTH(b); CASE a WHEN 1 THEN NULL; ELSE a "
                + ":= a + 1; END CASE; LET c := a * 2; RETURN n61(c, a, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 135 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:C_44), TO_NUMBER(:A_14), 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE a INT; b VARCHAR DEFAULT 'q'; BEGIN a := LENGTH(b); CASE a WHEN"
                + " 1 THEN NULL; ELSE a := a + 1; END CASE; LET c := a * 2; RETURN n61(c, a, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 136 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:C_34), TO_NUMBER(:A_4), 2)] expected 2, got 3"},
            {"BEGIN LET x := 1; LOOP x := x + 1; IF (x > 3) THEN BREAK; END IF; END LOOP; LET y := x; "
                + "RETURN n61(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 95 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_38), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; LOOP x := x + 1; IF (x > 3) THEN BREAK; END IF; END "
                + "LOOP; LET y := x; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 96 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_30), 1, 2)] expected 2, got 3"},
            {"BEGIN LET x := 1; REPEAT x := x + 1; UNTIL (x > 3) END REPEAT; RETURN n61(x, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 70 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_13), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; REPEAT x := x + 1; UNTIL (x > 3) END REPEAT; RETURN "
                + "n61(x, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 71 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_5), 1, 2)] expected 2, got 3"},
            {"DECLARE x INT; BEGIN x := 2; DECLARE y INT DEFAULT x + 1; BEGIN RETURN n61(y, x, 2); END; "
                + "END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 71 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_19), TO_NUMBER(:X_8), 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE x INT; BEGIN x := 2; DECLARE y INT DEFAULT x + 1; BEGIN RETURN "
                + "n61(y, x, 2); END; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 72 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_15), TO_NUMBER(:X_4), 2)] expected 2, got 3"},
            {"BEGIN LET x := 1; FOR i IN REVERSE 1 TO x + 2 DO FOR j IN i TO 3 DO RETURN n61(j, i, 2); END"
                + " FOR; END FOR; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 75 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:J_31, 9, 0), TO_NUMBER(:I_40, 9, 0), 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; FOR i IN REVERSE 1 TO x + 2 DO FOR j IN i TO 3 DO "
                + "RETURN n61(j, i, 2); END FOR; END FOR; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 76 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:J_23, 9, 0), TO_NUMBER(:I_32, 9, 0), 2)] expected 2, got 3"},
            {"BEGIN LET s := UPPER('a'); RETURN n61(s, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 34 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function [N61(:S_11, "
                + "1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET s := UPPER('a'); RETURN n61(s, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 35 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function [N61(:S_7, "
                + "1, 2)] expected 2, got 3"},
            {"BEGIN LET x := 1; CASE WHEN x > 5 THEN RETURN 0; WHEN x > 0 THEN LET y := 4; RETURN n61(y, "
                + "x, 2); END CASE; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 84 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_34), TO_NUMBER(:X_15), 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; CASE WHEN x > 5 THEN RETURN 0; WHEN x > 0 THEN LET y "
                + ":= 4; RETURN n61(y, x, 2); END CASE; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 85 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_24), TO_NUMBER(:X_5), 2)] expected 2, got 3"},
            {"BEGIN LET x := 2; IF (x = 1) THEN NULL; ELSEIF (x = 2) THEN LET y := x * 3; RETURN n61(y, 1,"
                + " 2); END IF; RETURN 0; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 83 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_44), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 2; IF (x = 1) THEN NULL; ELSEIF (x = 2) THEN LET y := x "
                + "* 3; RETURN n61(y, 1, 2); END IF; RETURN 0; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 84 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_30), 1, 2)] expected 2, got 3"},
            {"DECLARE x INT; y INT DEFAULT 3; z FLOAT; BEGIN z := 1.5; x := y; RETURN n61(x, z, y); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 72 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_6), CAST(:Z_11 AS FLOAT), TO_NUMBER(:Y_9))] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE x INT; y INT DEFAULT 3; z FLOAT; BEGIN z := 1.5; x := y; RETURN"
                + " n61(x, z, y); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 73 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_4), CAST(:Z_9 AS FLOAT), TO_NUMBER(:Y_7))] expected 2, got 3"},
            {"BEGIN LET t := TRUE; WHILE (NOT t) DO NULL; END WHILE; RETURN n61(t, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(CAST(:T_11 AS BOOLEAN), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET t := TRUE; WHILE (NOT t) DO NULL; END WHILE; RETURN n61(t, 1,"
                + " 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(CAST(:T_5 AS BOOLEAN), 1, 2)] expected 2, got 3"},
            {"BEGIN NULL; LET y := 5; RETURN n61(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 31 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_12), 1, 2)] expected 2, got 3"},
            {"BEGIN IF (TRUE) THEN NULL; END IF; LET y := 5; RETURN n61(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 54 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_18), 1, 2)] expected 2, got 3"},
            {"BEGIN LET x := 1; IF (x > 0) THEN NULL; END IF; LET y := 5; RETURN n61(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 67 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_28), 1, 2)] expected 2, got 3"},
            {"BEGIN LET x := 1; IF (x > 0) THEN LET y := 5; RETURN n61(y, 1, 2); END IF; RETURN 0; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 53 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_27), 1, 2)] expected 2, got 3"},
            {"BEGIN LET x := 1; WHILE (x < 3) DO x := x + 1; END WHILE; LET y := 5; RETURN n61(y, 1, 2); "
                + "END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 77 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_34), 1, 2)] expected 2, got 3"},
            {"BEGIN FOR i IN 1 TO 2 DO NULL; NULL; END FOR; LET y := 5; RETURN n61(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 65 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_26), 1, 2)] expected 2, got 3"},
            {"BEGIN LET x := 1; IF (x > 0) THEN NULL; ELSE NULL; END IF; LET y := 5; RETURN n61(y, 1, 2); "
                + "END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 78 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_32), 1, 2)] expected 2, got 3"},
            {"BEGIN LET x := 1; IF (x > 0) THEN NULL; ELSEIF (x < 0) THEN NULL; END IF; LET y := 5; RETURN"
                + " n61(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 93 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_41), 1, 2)] expected 2, got 3"},
            {"BEGIN LOOP BREAK; END LOOP; LET y := 5; RETURN n61(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 47 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_14), 1, 2)] expected 2, got 3"},
            {"BEGIN LET x := 1; REPEAT x := x + 1; UNTIL (x > 2) END REPEAT; LET y := 5; RETURN n61(y, 1, "
                + "2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 82 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_34), 1, 2)] expected 2, got 3"},
            {"BEGIN LET x := 1; CASE WHEN x > 0 THEN NULL; END CASE; LET y := 5; RETURN n61(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 74 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_27), 1, 2)] expected 2, got 3"},
            {"BEGIN LET x := 1; CASE WHEN x > 0 THEN NULL; WHEN x < 0 THEN NULL; END CASE; LET y := 5; "
                + "RETURN n61(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 96 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_38), 1, 2)] expected 2, got 3"},
            {"BEGIN LET x := 1; CASE x WHEN 1 THEN NULL; END CASE; LET y := 5; RETURN n61(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 72 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_25), 1, 2)] expected 2, got 3"},
            {"BEGIN LET x := 1; CASE WHEN x > 0 THEN NULL; ELSE NULL; END CASE; LET y := 5; RETURN n61(y, "
                + "1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 85 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_31), 1, 2)] expected 2, got 3"},
            {"BEGIN LOOP NULL; BREAK; END LOOP; LET y := 5; RETURN n61(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 53 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_17), 1, 2)] expected 2, got 3"},
            {"BEGIN FOR i IN 1 TO 2 DO IF (i > 5) THEN CONTINUE; END IF; RETURN n61(i, 1, 2); END FOR; "
                + "END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 66 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:I_32, 9, 0), 1, 2)] expected 2, got 3"},
            {"BEGIN WHILE (TRUE) DO BREAK; END WHILE; LET y := 5; RETURN n61(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 59 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_16), 1, 2)] expected 2, got 3"},
            {"BEGIN FOR i IN 1 TO 2 DO RETURN n61(i, 1, 2); LET z := 3; END FOR; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 32 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:I_27, 9, 0), 1, 2)] expected 2, got 3"},
            {"BEGIN LET x := 1; IF (x < 0) THEN RETURN n61(1, 2); END IF; LET y := 5; RETURN n61(y, 1, 2);"
                + " END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 79 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_34), 1, 2)] expected 2, got 3"},
            {"BEGIN LET x := 1; WHILE (x < 0) DO RETURN 0; END WHILE; LET y := 5; RETURN n61(y, 1, 2); "
                + "END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 75 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_29), 1, 2)] expected 2, got 3"},
            {"BEGIN LET x := 1; BEGIN BEGIN LET y := 2; RETURN n61(y, 1, 2); END; END; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 49 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_17), 1, 2)] expected 2, got 3"},
            {"BEGIN LET x := 1; DECLARE z INT DEFAULT 3; BEGIN RETURN n61(z, 1, 2); END; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 56 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Z_16), 1, 2)] expected 2, got 3"},
            {"BEGIN BEGIN NULL; END; LET y := 5; RETURN n61(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 42 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_13), 1, 2)] expected 2, got 3"},
            {"BEGIN FOR i IN 1 TO 2 DO FOR j IN 1 TO 2 DO RETURN n61(i, j, 2); END FOR; END FOR; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 51 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:I_31, 9, 0), TO_NUMBER(:J_26, 9, 0), 2)] expected 2, got 3"},
            {"BEGIN FOR i IN 1 TO 2 DO IF (i > 5) THEN BREAK; END IF; RETURN n61(i, 1, 2); END FOR; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:I_32, 9, 0), 1, 2)] expected 2, got 3"},
            {"BEGIN BEGIN LET y := 2; END; LET z := 5; RETURN n61(z, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 48 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Z_16), 1, 2)] expected 2, got 3"},
            {"BEGIN FOR i IN 1 TO 2 DO RETURN n61(i, 1, 2); END FOR; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 32 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:I_21, 9, 0), 1, 2)] expected 2, got 3"},
            {"BEGIN FOR i IN 1 TO 3 + 4 DO RETURN n61(i, 1, 2); END FOR; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 36 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:I_26, 9, 0), 1, 2)] expected 2, got 3"},
            {"BEGIN FOR i IN 1 TO 2 DO RETURN n61(i, i, 2); END FOR; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 32 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:I_20, 9, 0), TO_NUMBER(:I_20, 9, 0), 2)] expected 2, got 3"},
            {"BEGIN FOR i IN 1 TO 2 DO RETURN n61(i, 1, ABS(2)); END FOR; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 32 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:I_23, 9, 0), 1, ABS(2))] expected 2, got 3"},
            {"BEGIN FOR i IN 1 TO 2 DO RETURN n61(i, 1, 2) + 1; END FOR; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 32 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:I_26, 9, 0), 1, 2)] expected 2, got 3"},
            {"BEGIN FOR i IN 1 TO 2 DO IF (i > 0) THEN RETURN n61(i, 1, 2); END IF; END FOR; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 48 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:I_31, 9, 0), 1, 2)] expected 2, got 3"},
        });
    }
}
