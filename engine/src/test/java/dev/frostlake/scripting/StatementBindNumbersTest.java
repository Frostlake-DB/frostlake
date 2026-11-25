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
 * The numbers of the binds a miscounted call of a user's function echoes (live-verified): what each SQL statement a
 * block runs draws before a variable's declaration - a query by its items and clauses, an INSERT, an UPDATE, a DELETE,
 * cursors, RESULTSETs, exceptions and their handlers, EXECUTE IMMEDIATE and CALL - where a called procedure's body
 * starts, set by its CALL's arguments, where the CALL stands and whether it names the procedure bare from PUBLIC,
 * the numbers its parameters take after the body, and the bind forms of TIME, timestamp, VARIANT, ARRAY, OBJECT and BINARY variables.
 */
public class StatementBindNumbersTest extends MeasuredCellsTestSupport {

    @Test
    public void sqlStatements() {
        engine.execute("CREATE OR REPLACE FUNCTION n61(a INT, b INT) RETURNS INT AS 'a + b'");
        engine.execute("CREATE OR REPLACE TABLE t(a INT, b INT)");
        engine.execute("INSERT INTO t VALUES (1, 2)");
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ BEGIN LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_5), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT 1; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 36 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_16), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT 2 + 3; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 40 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_19), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT 1, 2; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 39 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_18), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT ABS(1); LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 41 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_18), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT a FROM t; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 43 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_15), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT 1; SELECT 1; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 46 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_27), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; SELECT :x; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 49 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_20), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; SELECT :x + 1; LET y := 5; RETURN n61(y, 1, 2); END; "
                + "$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 53 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_23), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT 'a'; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_16), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT a FROM t WHERE b = 2; LET y := 5; RETURN n61(y, 1, 2); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 55 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_22), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN INSERT INTO t VALUES (1, 2); LET y := 5; RETURN n61(y, 1, 2); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 55 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_21), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN INSERT INTO t (a) VALUES (1); LET y := 5; RETURN n61(y, 1, 2); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 56 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_21), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN UPDATE t SET a = 1; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 46 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_20), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN UPDATE t SET a = 1 WHERE b = 2; LET y := 5; RETURN n61(y, 1, 2); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 58 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_27), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN DELETE FROM t WHERE a = 99; LET y := 5; RETURN n61(y, 1, 2); END;"
                + " $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 54 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_22), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN DELETE FROM t WHERE a = 99 AND b = 98; LET y := 5; RETURN n61(y, "
                + "1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 65 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_29), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE v INT; BEGIN SELECT 1 INTO :v; LET y := 5; RETURN n61(y, 1, 2);"
                + " END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 59 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_32), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT 1; BEGIN LET y := 5; RETURN n61(y, 1, 2); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 57 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_18), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT 1; v INT; BEGIN OPEN c; FETCH c INTO v; "
                + "CLOSE c; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 97 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_28), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT 1; BEGIN OPEN c; LET y := 5; RETURN n61(y, "
                + "1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 65 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_20), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT 1; v INT; BEGIN OPEN c; FETCH c INTO v; LET"
                + " y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 88 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_26), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET c CURSOR FOR SELECT 1; LET y := 5; RETURN n61(y, 1, 2); END; "
                + "$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 53 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_19), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET rs RESULTSET := (SELECT 1); LET y := 5; RETURN n61(y, 1, 2); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 58 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_19), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE e EXCEPTION (-20001, 'boom'); BEGIN LET y := 5; RETURN n61(y, "
                + "1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 64 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_8), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET y := 5; RETURN n61(y, 1, 2); EXCEPTION WHEN OTHER THEN RETURN"
                + " 0; END; $$",
                "0"},
            {"EXECUTE IMMEDIATE $$ BEGIN BEGIN SELECT 1; EXCEPTION WHEN OTHER THEN RETURN 0; END; LET y :="
                + " 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 83 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_22), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET y := 5; IF (y < 0) THEN RAISE STATEMENT_ERROR; END IF; RETURN"
                + " n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 73 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_5), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE e EXCEPTION (-20001, 'boom'); BEGIN LET y := 5; IF (y < 0) THEN"
                + " RAISE e; END IF; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 97 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_8), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT 1 FROM t; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 43 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_14), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT * FROM t; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 43 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_14), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT 1 + 2 + 3; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 44 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_22), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT ABS(-1); LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 42 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_19), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN EXECUTE IMMEDIATE 'SELECT 1'; LET y := 5; RETURN n61(y, 1, 2); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 56 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_9), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT 1 AS q; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 41 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_16), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT COUNT(*) FROM t; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 50 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_15), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT 1 UNION ALL SELECT 2; LET y := 5; RETURN n61(y, 1, 2); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 55 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_34), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT n61(1, 2); LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 44 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_19), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT 1 WHERE 1 = 1; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 48 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_22), 1, 2)] expected 2, got 3"},
        });
    }

    @Test
    public void moreSqlStatements() {
        engine.execute("CREATE OR REPLACE FUNCTION n61(a INT, b INT) RETURNS INT AS 'a + b'");
        engine.execute("CREATE OR REPLACE TABLE t(a INT, b INT)");
        engine.execute("CREATE OR REPLACE TABLE t2(a INT)");
        engine.execute("INSERT INTO t VALUES (1, 2)");
        engine.execute("CREATE OR REPLACE PROCEDURE pr0() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr1(a INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END $$");
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT a, b FROM t; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 46 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_18), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT a + b FROM t; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 47 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_19), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT 1, 2, 3; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 42 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_20), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT a FROM t ORDER BY a; LET y := 5; RETURN n61(y, 1, 2); END;"
                + " $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 54 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_19), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT a FROM t LIMIT 1; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 51 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_18), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT a FROM t GROUP BY a; LET y := 5; RETURN n61(y, 1, 2); END;"
                + " $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 54 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_19), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT COUNT(a) FROM t; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 50 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_17), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT SUM(a) FROM t; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 48 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_17), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT x.a FROM t x; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 47 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_15), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT a FROM t WHERE b = 2 AND a = 1; LET y := 5; RETURN n61(y, "
                + "1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 65 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_29), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT a FROM t WHERE b > 0; LET y := 5; RETURN n61(y, 1, 2); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 55 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_22), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT 1 FROM t WHERE TRUE; LET y := 5; RETURN n61(y, 1, 2); END;"
                + " $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 54 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_17), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT a FROM t, t u; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : SQL "
                + "compilation error: | ambiguous column name 'A'"},
            {"EXECUTE IMMEDIATE $$ BEGIN DELETE FROM t; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 41 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_15), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN INSERT INTO t VALUES (1, 2), (3, 4); LET y := 5; RETURN n61(y, 1,"
                + " 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_23), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN INSERT INTO t SELECT a, b FROM t; LET y := 5; RETURN n61(y, 1, "
                + "2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 60 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_29), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN INSERT INTO t VALUES (1 + 1, 2); LET y := 5; RETURN n61(y, 1, 2);"
                + " END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 59 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_24), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN UPDATE t SET a = 1, b = 2; LET y := 5; RETURN n61(y, 1, 2); END; "
                + "$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 53 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_25), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN UPDATE t SET a = a + 1; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 50 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_24), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN INSERT INTO t (a, b) VALUES (1, 2); LET y := 5; RETURN n61(y, 1, "
                + "2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_23), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN INSERT INTO t VALUES (1, 2), (3, 4), (5, 6); LET y := 5; RETURN "
                + "n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 71 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_25), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN MERGE INTO t USING t s ON t.a = s.a WHEN MATCHED THEN UPDATE SET "
                + "b = 1; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Duplicate row"
                + " detected during DML action | Row Values: [2, 2]"},
            {"EXECUTE IMMEDIATE $$ DECLARE v INT; BEGIN SELECT a INTO :v FROM t; LET y := 5; RETURN n61(y,"
                + " 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 : A SELECT "
                + "INTO statement expects exactly 1 returned row, but got 9."},
            {"EXECUTE IMMEDIATE $$ DECLARE v INT; w INT; BEGIN SELECT 1, 2 INTO :v, :w; LET y := 5; RETURN"
                + " n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 73 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_40), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT a FROM t; BEGIN LET y := 5; RETURN n61(y, "
                + "1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 64 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_17), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT a, b FROM t; v INT; w INT; BEGIN OPEN c; "
                + "FETCH c INTO v, w; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 108 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_32), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT a FROM t WHERE a = ?; BEGIN OPEN c USING "
                + "(1); LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 94 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_27), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT 1; BEGIN LET y := 5; RETURN n61(y, 1, 2); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 57 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_18), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT 1; d CURSOR FOR SELECT 1; BEGIN LET y := 5;"
                + " RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 80 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_31), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET DEFAULT (SELECT 1); BEGIN LET y := 5; RETURN "
                + "n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 67 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_18), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; BEGIN rs := (SELECT 1); LET y := 5; RETURN n61(y,"
                + " 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 66 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_20), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET c CURSOR FOR SELECT a FROM t; LET y := 5; RETURN n61(y, 1, "
                + "2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 60 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_18), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE v INT; BEGIN SELECT 1 INTO v; LET y := 5; RETURN n61(y, 1, 2); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 58 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_32), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE v INT; BEGIN LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 41 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_7), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT 1; BEGIN FOR r IN c DO NULL; END FOR; LET y"
                + " := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 86 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_26), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT 1; v INT; BEGIN OPEN c; FETCH c INTO v; "
                + "FETCH c INTO v; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 104 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_30), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE e EXCEPTION (-20001, 'boom'); BEGIN IF (FALSE) THEN RAISE e; "
                + "END IF; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 97 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_15), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE e EXCEPTION (-20001, 'boom'); BEGIN IF (FALSE) THEN NULL; END "
                + "IF; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 94 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_13), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE e EXCEPTION; BEGIN LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 47 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_7), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE e EXCEPTION (-20001, 'boom'); f EXCEPTION (-20002, 'x'); BEGIN "
                + "LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 91 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_11), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN BEGIN NULL; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 0; END; "
                + "LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 89 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_13), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN BEGIN NULL; EXCEPTION WHEN OTHER THEN NULL; END; LET y := 5; "
                + "RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 75 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_11), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN BEGIN NULL; EXCEPTION WHEN STATEMENT_ERROR THEN NULL; WHEN OTHER "
                + "THEN NULL; END; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 107 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_16), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN BEGIN NULL; EXCEPTION WHEN STATEMENT_ERROR OR EXPRESSION_ERROR "
                + "THEN NULL; END; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 105 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_14), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN BEGIN NULL; END; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 43 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_7), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN BEGIN NULL; EXCEPTION WHEN OTHER THEN RAISE; END; LET y := 5; "
                + "RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 76 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_11), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN EXECUTE IMMEDIATE 'SELECT 1, 2'; LET y := 5; RETURN n61(y, 1, 2);"
                + " END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 59 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_9), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET s := 'SELECT 1'; EXECUTE IMMEDIATE :s; LET y := 5; RETURN "
                + "n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 69 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_13), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN EXECUTE IMMEDIATE 'SELECT ?' USING (1); LET y := 5; RETURN n61(y,"
                + " 1, 2); END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected '1'."},
            {"EXECUTE IMMEDIATE $$ BEGIN CALL pr0(); LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_17), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET q := 7; CALL pr1(:q); LET y := 5; RETURN n61(y, 1, 2); END; "
                + "$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 52 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_22), 1, 2)] expected 2, got 3"},
            {"BEGIN SELECT a, b FROM t; LET y := 5; RETURN n61(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 45 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_22), 1, 2)] expected 2, got 3"},
            {"BEGIN INSERT INTO t VALUES (1, 2), (3, 4); LET y := 5; RETURN n61(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_35), 1, 2)] expected 2, got 3"},
            {"DECLARE c CURSOR FOR SELECT a, b FROM t; v INT; w INT; BEGIN OPEN c; FETCH c INTO v, w; LET "
                + "y := 5; RETURN n61(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 107 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_36), 1, 2)] expected 2, got 3"},
            {"BEGIN SELECT a FROM t WHERE b = 2 AND a = 1; LET y := 5; RETURN n61(y, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 64 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_37), 1, 2)] expected 2, got 3"},
        });
    }

    @Test
    public void procedureBodies() {
        engine.execute("USE SCHEMA PUBLIC");
        engine.execute("CREATE OR REPLACE FUNCTION n61(a INT, b INT) RETURNS INT AS 'a + b'");
        engine.execute("CREATE OR REPLACE PROCEDURE pr0() RETURNS INT LANGUAGE SQL AS $$ BEGIN LET x := 1; RETURN "
            + "n61(x, 1, 2); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr1(a INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN LET x := 1; "
            + "RETURN n61(x, 1, 2); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr2(a INT, b INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN LET x := "
            + "1; RETURN n61(x, 1, 2); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr3(a INT, b INT, c INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN "
            + "LET x := 1; RETURN n61(x, 1, 2); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr1s(a VARCHAR) RETURNS INT LANGUAGE SQL AS $$ BEGIN LET x := 1;"
            + " RETURN n61(x, 1, 2); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr1f(a FLOAT) RETURNS INT LANGUAGE SQL AS $$ BEGIN LET x := 1; "
            + "RETURN n61(x, 1, 2); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pra(a INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN n61(a, 1,"
            + " 2); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE prb() RETURNS INT LANGUAGE SQL AS $$ BEGIN LET y := 2; LET z := "
            + "'abc'; LET x := 1; RETURN n61(x, 1, 2); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE prc() RETURNS INT LANGUAGE SQL AS $$ DECLARE x INT; BEGIN x := "
            + "1; RETURN n61(x, 1, 2); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE prd() RETURNS INT LANGUAGE SQL EXECUTE AS CALLER AS $$ BEGIN LET"
            + " x := 1; RETURN n61(x, 1, 2); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pre() RETURNS INT LANGUAGE SQL AS 'BEGIN LET x := 1; RETURN "
            + "n61(x, 1, 2); END'");
        engine.execute("CREATE OR REPLACE PROCEDURE prf() RETURNS INT LANGUAGE SQL AS $$ BEGIN SELECT 1; LET x := 1;"
            + " RETURN n61(x, 1, 2); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE prg(a INT DEFAULT 5) RETURNS INT LANGUAGE SQL AS $$ BEGIN LET x "
            + ":= 1; RETURN n61(x, 1, 2); END $$");
        assertCells(new String[][] {
            {"CALL pr0()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_17), 1, 2)] expected 2, got 3"},
            {"CALL pr1(7)",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_21), 1, 2)] expected 2, got 3"},
            {"CALL pr1(7 + 1)",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_27), 1, 2)] expected 2, got 3"},
            {"CALL pr1(7 + 1 + 1)",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_33), 1, 2)] expected 2, got 3"},
            {"CALL pr1(-7)",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_22), 1, 2)] expected 2, got 3"},
            {"CALL pr1(ABS(7))",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_23), 1, 2)] expected 2, got 3"},
            {"CALL pr1(NULL)",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_23), 1, 2)] expected 2, got 3"},
            {"CALL pr1(7::INT)",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_23), 1, 2)] expected 2, got 3"},
            {"CALL pr1(a => 7)",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_21), 1, 2)] expected 2, got 3"},
            {"CALL pr2(7, 8)",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_25), 1, 2)] expected 2, got 3"},
            {"CALL pr3(7, 8, 9)",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_29), 1, 2)] expected 2, got 3"},
            {"CALL pr2(7 + 1, 8)",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_31), 1, 2)] expected 2, got 3"},
            {"CALL pr1s('q')",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_21), 1, 2)] expected 2, got 3"},
            {"CALL pr1f(1.5)",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_23), 1, 2)] expected 2, got 3"},
            {"CALL pra(7)",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:A_28), 1, 2)] expected 2, got 3"},
            {"CALL prb()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 54 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_25), 1, 2)] expected 2, got 3"},
            {"CALL prc()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_16), 1, 2)] expected 2, got 3"},
            {"CALL prd()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_17), 1, 2)] expected 2, got 3"},
            {"CALL pre()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 25 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_17), 1, 2)] expected 2, got 3"},
            {"CALL prf()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 36 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_28), 1, 2)] expected 2, got 3"},
            {"CALL prg()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_19), 1, 2)] expected 2, got 3"},
            {"CALL prg(7)",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_23), 1, 2)] expected 2, got 3"},
            {"CALL pr0() ",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_17), 1, 2)] expected 2, got 3"},
            {"BEGIN CALL pr0(); END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Uncaught "
                + "exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL compilation error: error"
                + " line 1 at position 7 | too many arguments for function [N61(TO_NUMBER(:X_17), 1, 2)] "
                + "expected 2, got 3"},
            {"BEGIN LET q := 7; CALL pr1(:q); END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 18 : Uncaught "
                + "exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL compilation error: error"
                + " line 1 at position 7 | too many arguments for function [N61(TO_NUMBER(:X_19), 1, 2)] "
                + "expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN CALL pr1(7); END; $$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Uncaught "
                + "exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL compilation error: error"
                + " line 1 at position 7 | too many arguments for function [N61(TO_NUMBER(:X_20), 1, 2)] "
                + "expected 2, got 3"},
            {"EXECUTE IMMEDIATE 'CALL pr1(7)'",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_20), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN CALL pr1(7); LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Uncaught "
                + "exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL compilation error: error"
                + " line 1 at position 7 | too many arguments for function [N61(TO_NUMBER(:X_20), 1, 2)] "
                + "expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN CALL pr2(7, 8); LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Uncaught "
                + "exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL compilation error: error"
                + " line 1 at position 7 | too many arguments for function [N61(TO_NUMBER(:X_23), 1, 2)] "
                + "expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN CALL pr1(7 + 1); LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Uncaught "
                + "exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL compilation error: error"
                + " line 1 at position 7 | too many arguments for function [N61(TO_NUMBER(:X_25), 1, 2)] "
                + "expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN CALL pr1(-7); LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Uncaught "
                + "exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL compilation error: error"
                + " line 1 at position 7 | too many arguments for function [N61(TO_NUMBER(:X_21), 1, 2)] "
                + "expected 2, got 3"},
        });
    }

    @Test
    public void procedureParameters() {
        engine.execute("USE SCHEMA PUBLIC");
        engine.execute("CREATE OR REPLACE FUNCTION n61(a INT, b INT) RETURNS INT AS 'a + b'");
        engine.execute("CREATE OR REPLACE PROCEDURE pr0() RETURNS INT LANGUAGE SQL AS $$ BEGIN LET x := 1; RETURN "
            + "n61(x, 1, 2); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr1(a INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN LET x := 1; "
            + "RETURN n61(x, 1, 2); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr2(a INT, b INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN LET x := "
            + "1; RETURN n61(x, 1, 2); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pA1(a INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN n61(a, 1,"
            + " 2); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pA2(a INT, b INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN "
            + "n61(a, b, 2); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pA3(a INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN LET x := 1; "
            + "RETURN n61(a, 1, 2); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pA4(a INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN LET x := 1; "
            + "RETURN n61(x, a, 2); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pA5(a VARCHAR) RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN "
            + "n61(a, 1, 2); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pA6() RETURNS INT LANGUAGE SQL AS $$ DECLARE x INT; BEGIN RETURN"
            + " n61(x, 1, 2); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pA7(a INT, b INT, c INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN "
            + "RETURN n61(c, b, a); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pA8(a INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN n61(1 + "
            + "a, 1, 2); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pA9(a FLOAT) RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN n61(a, "
            + "1, 2); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pA10(a INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN IF (a > 0) THEN"
            + " RETURN n61(a, 1, 2); END IF; RETURN 0; END $$");
        assertCells(new String[][] {
            {"CALL pA1(7)",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:A_28), 1, 2)] expected 2, got 3"},
            {"CALL pA1(7 + 1)",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:A_34), 1, 2)] expected 2, got 3"},
            {"CALL pA2(7, 8)",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:A_33), TO_NUMBER(:B_37), 2)] expected 2, got 3"},
            {"CALL pA3(7)",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:A_32), 1, 2)] expected 2, got 3"},
            {"CALL pA4(7)",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_21), TO_NUMBER(:A_33), 2)] expected 2, got 3"},
            {"CALL pA5('q')",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function [N61(:A_28, "
                + "1, 2)] expected 2, got 3"},
            {"CALL pA6()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 29 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_16), 1, 2)] expected 2, got 3"},
            {"CALL pA7(7, 8, 9)",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:C_46), TO_NUMBER(:B_42), TO_NUMBER(:A_38))] expected 2, got 3"},
            {"CALL pA8(7)",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function [N61(1 + "
                + "(TO_NUMBER(:A_31)), 1, 2)] expected 2, got 3"},
            {"CALL pA9(1.5)",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(CAST(:A_30 AS FLOAT), 1, 2)] expected 2, got 3"},
            {"CALL pA10(7)",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:A_38), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE 'CALL pr0()'",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_19), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE 'CALL pr2(7, 8)'",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_21), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE 'CALL pr1(7 + 1)'",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_23), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE 'CALL pr1(-7)'",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_21), 1, 2)] expected 2, got 3"},
            {"BEGIN CALL pr1(7); END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Uncaught "
                + "exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL compilation error: error"
                + " line 1 at position 7 | too many arguments for function [N61(TO_NUMBER(:X_20), 1, 2)] "
                + "expected 2, got 3"},
            {"BEGIN CALL pr2(7, 8); END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Uncaught "
                + "exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL compilation error: error"
                + " line 1 at position 7 | too many arguments for function [N61(TO_NUMBER(:X_23), 1, 2)] "
                + "expected 2, got 3"},
            {"BEGIN CALL pr1(7 + 1); END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Uncaught "
                + "exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL compilation error: error"
                + " line 1 at position 7 | too many arguments for function [N61(TO_NUMBER(:X_25), 1, 2)] "
                + "expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN CALL pr0(); END; $$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Uncaught "
                + "exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL compilation error: error"
                + " line 1 at position 7 | too many arguments for function [N61(TO_NUMBER(:X_17), 1, 2)] "
                + "expected 2, got 3"},
            {"BEGIN LET q := 1; LET r := 2; CALL pr0(); END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 30 : Uncaught "
                + "exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL compilation error: error"
                + " line 1 at position 7 | too many arguments for function [N61(TO_NUMBER(:X_17), 1, 2)] "
                + "expected 2, got 3"},
            {"BEGIN LET q := 7; CALL pr2(:q, :q); END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 18 : Uncaught "
                + "exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL compilation error: error"
                + " line 1 at position 7 | too many arguments for function [N61(TO_NUMBER(:X_20), 1, 2)] "
                + "expected 2, got 3"},
            {"BEGIN LET q := 7; CALL pr1(:q + 1); END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 18 : Uncaught "
                + "exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL compilation error: error"
                + " line 1 at position 7 | too many arguments for function [N61(TO_NUMBER(:X_24), 1, 2)] "
                + "expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET q := 7; CALL pr1(:q); END; $$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 19 : Uncaught "
                + "exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL compilation error: error"
                + " line 1 at position 7 | too many arguments for function [N61(TO_NUMBER(:X_19), 1, 2)] "
                + "expected 2, got 3"},
            {"BEGIN CALL pr1(-7); END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Uncaught "
                + "exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL compilation error: error"
                + " line 1 at position 7 | too many arguments for function [N61(TO_NUMBER(:X_21), 1, 2)] "
                + "expected 2, got 3"},
        });
    }

    @Test
    public void callsHandlersAndBindForms() {
        engine.execute("USE SCHEMA PUBLIC");
        engine.execute("CREATE OR REPLACE FUNCTION n61(a INT, b INT) RETURNS INT AS 'a + b'");
        engine.execute("CREATE OR REPLACE TABLE t(a INT, b INT)");
        engine.execute("CREATE OR REPLACE TABLE t2(a INT)");
        engine.execute("CREATE OR REPLACE TABLE t3(a INT, b INT, c INT)");
        engine.execute("INSERT INTO t VALUES (1, 2)");
        engine.execute("CREATE OR REPLACE PROCEDURE pr1(a INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN LET x := 1; "
            + "RETURN n61(x, 1, 2); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr2(a INT, b INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN LET x := "
            + "1; RETURN n61(x, 1, 2); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr1s(a VARCHAR) RETURNS INT LANGUAGE SQL AS $$ BEGIN LET x := 1;"
            + " RETURN n61(x, 1, 2); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr1f(a FLOAT) RETURNS INT LANGUAGE SQL AS $$ BEGIN LET x := 1; "
            + "RETURN n61(x, 1, 2); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr1n(a NUMBER(10,2)) RETURNS INT LANGUAGE SQL AS $$ BEGIN LET x "
            + ":= 1; RETURN n61(x, 1, 2); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pr1d(a DATE) RETURNS INT LANGUAGE SQL AS $$ BEGIN LET x := 1; "
            + "RETURN n61(x, 1, 2); END $$");
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ BEGIN LET q := 7; CALL pr1(:q); LET y := 5; RETURN n61(y, 1, 2); END; "
                + "$$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 19 : Uncaught "
                + "exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL compilation error: error"
                + " line 1 at position 7 | too many arguments for function [N61(TO_NUMBER(:X_19), 1, 2)] "
                + "expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET q := 7; CALL pr2(:q, :q); LET y := 5; RETURN n61(y, 1, 2); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 19 : Uncaught "
                + "exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL compilation error: error"
                + " line 1 at position 7 | too many arguments for function [N61(TO_NUMBER(:X_20), 1, 2)] "
                + "expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET q := 7; CALL pr1(:q + 1); LET y := 5; RETURN n61(y, 1, 2); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 19 : Uncaught "
                + "exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL compilation error: error"
                + " line 1 at position 7 | too many arguments for function [N61(TO_NUMBER(:X_24), 1, 2)] "
                + "expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET q := 7; LET r := 8; CALL pr2(:q, :r); LET y := 5; RETURN "
                + "n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 31 : Uncaught "
                + "exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL compilation error: error"
                + " line 1 at position 7 | too many arguments for function [N61(TO_NUMBER(:X_21), 1, 2)] "
                + "expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET q := 7; CALL pr1(q); LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 19 : SQL "
                + "compilation error: error line 1 at position 9 | invalid identifier 'Q'"},
            {"EXECUTE IMMEDIATE $$ BEGIN CALL pr2(7, 8 + 1); LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : Uncaught "
                + "exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL compilation error: error"
                + " line 1 at position 7 | too many arguments for function [N61(TO_NUMBER(:X_28), 1, 2)] "
                + "expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN INSERT INTO t2 VALUES (1); LET y := 5; RETURN n61(y, 1, 2); END; "
                + "$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 53 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_20), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN INSERT INTO t2 (a) VALUES (1); LET y := 5; RETURN n61(y, 1, 2); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 57 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_21), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN INSERT INTO t3 VALUES (1, 2, 3); LET y := 5; RETURN n61(y, 1, 2);"
                + " END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 59 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_22), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN INSERT INTO t3 (a) VALUES (1); LET y := 5; RETURN n61(y, 1, 2); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 57 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_21), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN INSERT INTO t3 (a, b, c) VALUES (1, 2, 3); LET y := 5; RETURN "
                + "n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 69 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_25), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN INSERT INTO t (a) VALUES (1), (2); LET y := 5; RETURN n61(y, 1, "
                + "2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_22), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN INSERT INTO t (a, b) VALUES (1, 2), (3, 4); LET y := 5; RETURN "
                + "n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 70 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_25), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN INSERT INTO t2 VALUES (1), (2); LET y := 5; RETURN n61(y, 1, 2); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 58 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_21), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN INSERT INTO t2 SELECT 1; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 51 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_27), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN INSERT INTO t3 (a) SELECT a FROM t; LET y := 5; RETURN n61(y, 1, "
                + "2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_27), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT a FROM t GROUP BY a, b; LET y := 5; RETURN n61(y, 1, 2); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 57 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_22), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT a FROM t ORDER BY a, b; LET y := 5; RETURN n61(y, 1, 2); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 57 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_22), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT a FROM t LIMIT 5; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 51 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_18), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT a FROM t ORDER BY a DESC; LET y := 5; RETURN n61(y, 1, 2);"
                + " END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 59 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_19), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT DISTINCT a FROM t; LET y := 5; RETURN n61(y, 1, 2); END; "
                + "$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 52 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_15), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; SELECT a FROM t WHERE a = :x; LET y := 5; RETURN "
                + "n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 68 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_26), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT COUNT(*) FROM t WHERE a = 1; LET y := 5; RETURN n61(y, 1, "
                + "2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_22), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT a FROM t GROUP BY a HAVING COUNT(*) > 1; LET y := 5; "
                + "RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 74 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_26), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT a FROM t LIMIT 1 OFFSET 1; LET y := 5; RETURN n61(y, 1, "
                + "2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 60 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_18), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT a, b, a + b FROM t; LET y := 5; RETURN n61(y, 1, 2); END; "
                + "$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 53 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_25), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT * FROM t WHERE a = 1; LET y := 5; RETURN n61(y, 1, 2); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 55 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_21), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT 1, 'a'; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 41 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_18), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT CURRENT_DATE(); LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 49 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_17), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT a FROM t AS x; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 48 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_15), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN SELECT t.a FROM t; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 45 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_15), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN UPDATE t SET a = 1, b = 2 WHERE a = 0; LET y := 5; RETURN n61(y, "
                + "1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 65 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_32), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN UPDATE t SET b = a; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 46 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_21), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN DELETE FROM t WHERE a = 1 OR b = 2; LET y := 5; RETURN n61(y, 1, "
                + "2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_29), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1; DELETE FROM t WHERE a = :x; LET y := 5; RETURN n61(y,"
                + " 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 66 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_26), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE e EXCEPTION (-20001, 'boom'); BEGIN IF (FALSE) THEN RAISE e; "
                + "END IF; IF (FALSE) THEN RAISE e; END IF; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 130 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_22), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE e EXCEPTION (-20001, 'boom'); BEGIN BEGIN NULL; EXCEPTION WHEN "
                + "e THEN NULL; END; LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 109 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_15), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN BEGIN NULL; EXCEPTION WHEN STATEMENT_ERROR THEN LET z := 1; END; "
                + "LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 91 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_15), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN BEGIN NULL; EXCEPTION WHEN STATEMENT_ERROR THEN NULL; NULL; END; "
                + "LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 91 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_13), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT a FROM t WHERE a = ?; BEGIN OPEN c USING "
                + "(1); LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 94 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_27), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT a FROM t WHERE a = 1; BEGIN OPEN c; LET y "
                + ":= 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 84 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_26), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT a FROM t WHERE a = ? AND b = ?; BEGIN OPEN "
                + "c USING (1, 2); LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 107 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_35), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT a FROM t WHERE a = ?; BEGIN LET y := 5; "
                + "RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 76 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_24), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT 1; BEGIN OPEN c; CLOSE c; LET y := 5; "
                + "RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 74 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_22), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT 1; BEGIN FOR r IN c DO LET z := 1; END FOR;"
                + " LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 92 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_29), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET DEFAULT (SELECT 1); c CURSOR FOR rs; BEGIN LET y "
                + ":= 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 84 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_21), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET rs RESULTSET := (SELECT a FROM t WHERE a = 1); LET y := 5; "
                + "RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 77 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_25), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET s := 'SELECT 1'; EXECUTE IMMEDIATE :s USING (s); LET y := 5; "
                + "RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 79 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_15), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN EXECUTE IMMEDIATE 'SELECT 1' INTO :s; LET y := 5; RETURN n61(y, "
                + "1, 2); END; $$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'INTO'. | "
                + "syntax error line 1 at position 45 unexpected 'LET'."},
            {"CALL pr1f(7)",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_23), 1, 2)] expected 2, got 3"},
            {"CALL pr1s(7)",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_23), 1, 2)] expected 2, got 3"},
            {"CALL pr1('7')",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_23), 1, 2)] expected 2, got 3"},
            {"CALL pr1n(7)",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_23), 1, 2)] expected 2, got 3"},
            {"CALL pr1n(1.5)",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_23), 1, 2)] expected 2, got 3"},
            {"CALL pr1d('2020-01-01')",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_23), 1, 2)] expected 2, got 3"},
            {"CALL pr1f(1.5e0)",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_23), 1, 2)] expected 2, got 3"},
            {"CALL pr1(1.5)",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_21), 1, 2)] expected 2, got 3"},
            {"CALL pr1(TRUE)",
                "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for "
                + "function 'PR1': (BOOLEAN)"},
            {"BEGIN CALL pr1(NULL); END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Uncaught "
                + "exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL compilation error: error"
                + " line 1 at position 7 | too many arguments for function [N61(TO_NUMBER(:X_22), 1, 2)] "
                + "expected 2, got 3"},
            {"EXECUTE IMMEDIATE 'CALL pr1f(7)'",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_22), 1, 2)] expected 2, got 3"},
            {"CALL pr2(-7, -8)",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_27), 1, 2)] expected 2, got 3"},
            {"CALL pr1(ABS(-7))",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_24), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE v TIME DEFAULT '12:00:00'::TIME; BEGIN RETURN n61(v, 1, 2); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 55 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(CAST(:V_7 AS TIME(9)), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE v TIMESTAMP_NTZ DEFAULT '2020-01-01 00:00:00'::TIMESTAMP_NTZ; "
                + "BEGIN RETURN n61(v, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 84 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(CAST(:V_7 AS TIMESTAMP_NTZ(9)), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE v TIMESTAMP_LTZ DEFAULT '2020-01-01 00:00:00'::TIMESTAMP_LTZ; "
                + "BEGIN RETURN n61(v, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 84 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(CAST(:V_7 AS TIMESTAMP_LTZ(9)), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE v TIMESTAMP_TZ DEFAULT '2020-01-01 00:00:00 "
                + "+01:00'::TIMESTAMP_TZ; BEGIN RETURN n61(v, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 89 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(CAST(:V_7 AS TIMESTAMP_TZ(9)), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE v VARIANT DEFAULT TO_VARIANT(1); BEGIN RETURN n61(v, 1, 2); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 55 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(PARSE_JSON(:V_7), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE v ARRAY DEFAULT ARRAY_CONSTRUCT(1, 2); BEGIN RETURN n61(v, 1, "
                + "2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(CAST(PARSE_JSON(:V_8) AS ARRAY), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE v OBJECT DEFAULT OBJECT_CONSTRUCT('a', 1); BEGIN RETURN n61(v, "
                + "1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 65 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(CAST(PARSE_JSON(:V_8) AS OBJECT), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE v BINARY DEFAULT TO_BINARY('AB'); BEGIN RETURN n61(v, 1, 2); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 56 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(CAST(:V_7 AS BINARY(67108864)), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE v TIMESTAMP DEFAULT '2020-01-01 00:00:00'::TIMESTAMP; BEGIN "
                + "RETURN n61(v, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 76 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(CAST(:V_7 AS TIMESTAMP_NTZ(9)), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE v NUMBER(10,2) DEFAULT 1.5; BEGIN RETURN n61(v, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 50 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:V_5, 10, 2), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE v DATE DEFAULT '2020-01-01'::DATE; BEGIN RETURN n61(v, 1, 2); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 57 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(CAST(:V_7 AS DATE), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE v TIME(3) DEFAULT '12:00:00'::TIME; BEGIN RETURN n61(v, 1, 2); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 58 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(CAST(:V_7 AS TIME(3)), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE v TIMESTAMP_NTZ(3) DEFAULT '2020-01-01 "
                + "00:00:00'::TIMESTAMP_NTZ; BEGIN RETURN n61(v, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 87 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(CAST(:V_7 AS TIMESTAMP_NTZ(3)), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE v VARCHAR(10) DEFAULT 'abc'; BEGIN RETURN n61(v, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 51 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function [N61(:V_5, "
                + "1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE v DOUBLE DEFAULT 1.5; BEGIN RETURN n61(v, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 44 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(CAST(:V_5 AS FLOAT), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE v BOOLEAN DEFAULT TRUE; BEGIN RETURN n61(v, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 46 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(CAST(:V_5 AS BOOLEAN), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE v TIMESTAMP_TZ(0) DEFAULT '2020-01-01 00:00:00 "
                + "+01:00'::TIMESTAMP_TZ; BEGIN RETURN n61(v, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 92 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(CAST(:V_7 AS TIMESTAMP_TZ(0)), 1, 2)] expected 2, got 3"},
        });
    }

    @Test
    public void callStatementsAndDefaults() {
        engine.execute("USE SCHEMA PUBLIC");
        engine.execute("CREATE OR REPLACE FUNCTION n61(a INT, b INT) RETURNS INT AS 'a + b'");
        engine.execute("CREATE OR REPLACE PROCEDURE ok0() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE ok1(a INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE ok2(a INT, b INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1;"
            + " END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE okf(a FLOAT) RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END "
            + "$$");
        engine.execute("CREATE OR REPLACE PROCEDURE prg(a INT DEFAULT 5) RETURNS INT LANGUAGE SQL AS $$ BEGIN LET x "
            + ":= 1; RETURN n61(x, 1, 2); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE prg2(a INT DEFAULT 5 + 1) RETURNS INT LANGUAGE SQL AS $$ BEGIN "
            + "LET x := 1; RETURN n61(x, 1, 2); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE prg3(b INT, a INT DEFAULT 5) RETURNS INT LANGUAGE SQL AS $$ "
            + "BEGIN LET x := 1; RETURN n61(x, 1, 2); END $$");
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ BEGIN CALL ok1(7); LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 39 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_18), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN CALL ok2(7, 8); LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 42 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_19), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN CALL ok1(7 + 1); LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 43 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_21), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN CALL ok1(-7); LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 40 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_19), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET q := 7; CALL ok2(:q, :q); LET y := 5; RETURN n61(y, 1, 2); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 56 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_23), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET q := 7; CALL ok1(:q + 1); LET y := 5; RETURN n61(y, 1, 2); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 56 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_25), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET q := 7; CALL ok1(:q); LET y := 5; RETURN n61(y, 1, 2); END; "
                + "$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 52 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_22), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN CALL ok0(); LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_17), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN CALL ok1(NULL); LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 42 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_18), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN CALL okf(7); LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 39 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_18), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET q := 7; LET r := 8; CALL ok2(:q, :r); LET y := 5; RETURN "
                + "n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 68 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_27), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET z := CURRENT_DATE(); LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 51 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_10), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET z := RANDOM(); LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 45 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_10), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET z := ABS(1); LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 43 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_11), 1, 2)] expected 2, got 3"},
            {"CALL prg()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_19), 1, 2)] expected 2, got 3"},
            {"CALL prg3(7)",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_23), 1, 2)] expected 2, got 3"},
            {"CALL prg3(7, 8)",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_27), 1, 2)] expected 2, got 3"},
            {"BEGIN CALL prg(); END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Uncaught "
                + "exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL compilation error: error"
                + " line 1 at position 7 | too many arguments for function [N61(TO_NUMBER(:X_19), 1, 2)] "
                + "expected 2, got 3"},
            {"CALL prg(a => 7)",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_23), 1, 2)] expected 2, got 3"},
        });
    }

    @Test
    public void builtInCallArguments() {
        engine.execute("CREATE OR REPLACE FUNCTION n61(a INT, b INT) RETURNS INT AS 'a + b'");
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ BEGIN LET z := CONCAT('a', 'b'); LET y := 5; RETURN n61(y, 1, 2); END; "
                + "$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 53 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_12), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET z := GREATEST(1, 2); LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 51 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_12), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET z := GREATEST(1, 2, 3); LET y := 5; RETURN n61(y, 1, 2); END;"
                + " $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 54 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_13), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET z := ARRAY_CONSTRUCT(); LET y := 5; RETURN n61(y, 1, 2); END;"
                + " $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 54 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_10), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET z := ARRAY_CONSTRUCT(1); LET y := 5; RETURN n61(y, 1, 2); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 55 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_11), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET z := ARRAY_CONSTRUCT(1, 2); LET y := 5; RETURN n61(y, 1, 2); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 58 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_12), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET z := ARRAY_CONSTRUCT(1, 2, 3); LET y := 5; RETURN n61(y, 1, "
                + "2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_13), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET z := OBJECT_CONSTRUCT('a', 1); LET y := 5; RETURN n61(y, 1, "
                + "2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_12), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET z := OBJECT_CONSTRUCT(); LET y := 5; RETURN n61(y, 1, 2); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 55 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_10), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET z := ARRAY_SIZE(ARRAY_CONSTRUCT(1)); LET y := 5; RETURN "
                + "n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 67 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_13), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET z := TO_ARRAY(1); LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 48 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_11), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET z := COALESCE(1, 2); LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 51 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_12), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET z := IFF(TRUE, 1, 2); LET y := 5; RETURN n61(y, 1, 2); END; "
                + "$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 52 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_13), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET z := SUBSTR('abc', 1, 2); LET y := 5; RETURN n61(y, 1, 2); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 56 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_13), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET z := ROUND(1.5, 0); LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 50 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_12), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET z := ARRAY_APPEND(ARRAY_CONSTRUCT(), 1); LET y := 5; RETURN "
                + "n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 71 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_13), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET z := OBJECT_CONSTRUCT_KEEP_NULL('a', 1); LET y := 5; RETURN "
                + "n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 71 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_12), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET z := TO_BINARY('AB'); LET y := 5; RETURN n61(y, 1, 2); END; "
                + "$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 52 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_11), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET z := CURRENT_TIMESTAMP(); LET y := 5; RETURN n61(y, 1, 2); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 56 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_10), 1, 2)] expected 2, got 3"},
        });
    }

    @Test
    public void subqueriesAndBinaryWidths() {
        engine.execute("CREATE OR REPLACE FUNCTION n61(a INT, b INT) RETURNS INT AS 'a + b'");
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ DECLARE x INT; BEGIN x := (SELECT 1); LET y := 5; RETURN n61(y, 1, 2); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 58 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_20), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := (SELECT 1); LET y := 5; RETURN n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 47 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_18), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; BEGIN rs := (SELECT 1, 2); LET y := 5; RETURN "
                + "n61(y, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 69 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_22), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE v BINARY(10) DEFAULT TO_BINARY('AB'); BEGIN RETURN n61(v, 1, "
                + "2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 60 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(CAST(:V_7 AS BINARY(10)), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE v DATETIME DEFAULT '2020-01-01 00:00:00'::TIMESTAMP_NTZ; BEGIN "
                + "RETURN n61(v, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 79 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(CAST(:V_7 AS TIMESTAMP_NTZ(9)), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE v VARBINARY DEFAULT TO_BINARY('AB'); BEGIN RETURN n61(v, 1, 2);"
                + " END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 59 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(CAST(:V_7 AS BINARY(67108864)), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE v TIMESTAMP_LTZ(0) DEFAULT '2020-01-01 "
                + "00:00:00'::TIMESTAMP_LTZ; BEGIN RETURN n61(v, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 87 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(CAST(:V_7 AS TIMESTAMP_LTZ(0)), 1, 2)] expected 2, got 3"},
            {"BEGIN LET v VARIANT := TO_VARIANT(1); RETURN n61(v, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 45 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(PARSE_JSON(:V_11), 1, 2)] expected 2, got 3"},
            {"BEGIN LET v TIME := '12:00:00'::TIME; RETURN n61(v, 1, 2); END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 45 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(CAST(:V_11 AS TIME(9)), 1, 2)] expected 2, got 3"},
        });
    }

    @Test
    public void subqueryValues() {
        engine.execute("CREATE OR REPLACE FUNCTION n61(a INT, b INT) RETURNS INT AS 'a + b'");
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := (SELECT 1); RETURN n61(x, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 35 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_14), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE x INT DEFAULT (SELECT 1); BEGIN RETURN n61(x, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 48 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_14), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ DECLARE x INT DEFAULT (SELECT 1); BEGIN LET y := 5; RETURN n61(y, 1, "
                + "2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 60 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:Y_17), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := (SELECT 1) + 1; RETURN n61(x, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 39 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_17), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x INT := (SELECT 1); RETURN n61(x, 1, 2); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 39 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_14), 1, 2)] expected 2, got 3"},
        });
    }

    @Test
    public void callsFromPublic() {
        engine.execute("USE SCHEMA PUBLIC");
        engine.execute("CREATE OR REPLACE FUNCTION n61(a INT, b INT) RETURNS INT AS 'a + b'");
        engine.execute("CREATE OR REPLACE PROCEDURE qp() RETURNS INT LANGUAGE SQL AS $$ BEGIN LET x := 1; RETURN "
            + "n61(x, 1, 2); END $$");
        engine.execute("CREATE SCHEMA ts");
        engine.execute("CREATE OR REPLACE FUNCTION ts.n61(a INT, b INT) RETURNS INT AS 'a + b'");
        engine.execute("CREATE OR REPLACE PROCEDURE ts.qt() RETURNS INT LANGUAGE SQL AS $$ BEGIN LET x := 1; RETURN "
            + "n61(x, 1, 2); END $$");
        engine.execute("USE SCHEMA PUBLIC");
        assertCells(new String[][] {
            {"CALL ts.qt()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_16), 1, 2)] expected 2, got 3"},
            {"CALL qp()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_17), 1, 2)] expected 2, got 3"},
            {"CALL PUBLIC.qp()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_16), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE 'CALL qp()'",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_19), 1, 2)] expected 2, got 3"},
            {"BEGIN CALL qp(); END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Uncaught "
                + "exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL compilation error: error"
                + " line 1 at position 7 | too many arguments for function [N61(TO_NUMBER(:X_17), 1, 2)] "
                + "expected 2, got 3"},
            {"EXECUTE IMMEDIATE 'CALL PUBLIC.qp()'",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_18), 1, 2)] expected 2, got 3"},
        });
    }

    @Test
    public void callsFromAnotherSchema() {
        engine.execute("USE SCHEMA PUBLIC");
        engine.execute("CREATE OR REPLACE FUNCTION n61(a INT, b INT) RETURNS INT AS 'a + b'");
        engine.execute("CREATE OR REPLACE PROCEDURE qp() RETURNS INT LANGUAGE SQL AS $$ BEGIN LET x := 1; RETURN "
            + "n61(x, 1, 2); END $$");
        engine.execute("CREATE SCHEMA ts");
        engine.execute("CREATE OR REPLACE FUNCTION ts.n61(a INT, b INT) RETURNS INT AS 'a + b'");
        engine.execute("CREATE OR REPLACE PROCEDURE ts.qt(a INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN LET x := 1; "
            + "RETURN n61(x, 1, 2); END $$");
        assertCells(new String[][] {
            {"CALL qt(7)",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_20), 1, 2)] expected 2, got 3"},
            {"CALL PUBLIC.qp()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_16), 1, 2)] expected 2, got 3"},
            {"EXECUTE IMMEDIATE 'CALL qt(7)'",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_19), 1, 2)] expected 2, got 3"},
            {"BEGIN CALL qt(7); END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Uncaught "
                + "exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL compilation error: error"
                + " line 1 at position 7 | too many arguments for function [N61(TO_NUMBER(:X_19), 1, 2)] "
                + "expected 2, got 3"},
            {"BEGIN CALL PUBLIC.qp(); END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Uncaught "
                + "exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL compilation error: error"
                + " line 1 at position 7 | too many arguments for function [N61(TO_NUMBER(:X_16), 1, 2)] "
                + "expected 2, got 3"},
            {"CALL qp()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL "
                + "compilation error: error line 1 at position 7 | too many arguments for function "
                + "[N61(TO_NUMBER(:X_16), 1, 2)] expected 2, got 3"},
        });
    }
}
