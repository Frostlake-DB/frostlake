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

package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * Where a block expression's anti-join refusal is placed when variables are written before the subquery: the
 * account compiles the expression as a query that reads each name as a bind cast to its type, {@code (:n)::NUMBER(38,0)}
 * for an INT, {@code (:s)} for a text, a bind {@code :n} as {@code :n::NUMBER(38,0)}, and of several refused
 * anti-joins it places the last. Every cell is live-verified.
 */
public class BlockAntiJoinVariablePlaceTest extends MeasuredCellsTestSupport {

    @Test
    public void aVariableBeforeTheSubqueryMovesThePlace() {
        engine.execute("CREATE OR REPLACE TABLE q (g INT, k INT, s VARCHAR, v INT)");
        engine.execute("INSERT INTO q VALUES (0, 1, 'x', 10), (0, 2, 'y', 20), (1, 1, 'x', 10), (1, NULL, 'y', 20), "
            + "(2, 1, 'x', 10), (2, 2, NULL, 20), (3, NULL, NULL, NULL), (5, 1, NULL, 10), (6, NULL, 'x', "
            + "10)");
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$DECLARE n INT DEFAULT 1; r BOOLEAN; BEGIN r := n IS NOT NULL AND 1 NOT "
                + "IN (SELECT k FROM q WHERE g = 0); RETURN r; END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 47 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 60"},
            {"EXECUTE IMMEDIATE $$DECLARE nnnnn INT DEFAULT 1; r BOOLEAN; BEGIN r := nnnnn IS NOT NULL AND"
                + " 1 NOT IN (SELECT k FROM q WHERE g = 0); RETURN r; END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 51 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 64"},
            {"EXECUTE IMMEDIATE $$DECLARE n NUMBER(10,2) DEFAULT 1; r BOOLEAN; BEGIN r := n IS NOT NULL "
                + "AND 1 NOT IN (SELECT k FROM q WHERE g = 0); RETURN r; END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 56 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 60"},
            {"EXECUTE IMMEDIATE $$DECLARE n VARCHAR DEFAULT 'x'; r BOOLEAN; BEGIN r := n IS NOT NULL AND 1"
                + " NOT IN (SELECT k FROM q WHERE g = 0); RETURN r; END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 53 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 46"},
            {"EXECUTE IMMEDIATE $$DECLARE n VARCHAR(5) DEFAULT 'xyz'; r BOOLEAN; BEGIN r := n IS NOT NULL "
                + "AND 1 NOT IN (SELECT k FROM q WHERE g = 0); RETURN r; END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 58 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 46"},
            {"EXECUTE IMMEDIATE $$DECLARE n DATE DEFAULT '2020-01-01'; r BOOLEAN; BEGIN r := n IS NOT NULL"
                + " AND 1 NOT IN (SELECT k FROM q WHERE g = 0); RETURN r; END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 59 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 52"},
            {"EXECUTE IMMEDIATE $$DECLARE n BOOLEAN DEFAULT TRUE; r BOOLEAN; BEGIN r := n IS NOT NULL AND "
                + "1 NOT IN (SELECT k FROM q WHERE g = 0); RETURN r; END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 54 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 55"},
            {"EXECUTE IMMEDIATE $$DECLARE n FLOAT DEFAULT 1.5; r BOOLEAN; BEGIN r := n IS NOT NULL AND 1 "
                + "NOT IN (SELECT k FROM q WHERE g = 0); RETURN r; END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 51 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 53"},
            {"EXECUTE IMMEDIATE $$DECLARE n TIMESTAMP_NTZ DEFAULT '2020-01-01 00:00:00'; r BOOLEAN; BEGIN "
                + "r := n IS NOT NULL AND 1 NOT IN (SELECT k FROM q WHERE g = 0); RETURN r; END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 77 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 64"},
            {"EXECUTE IMMEDIATE $$DECLARE n INT DEFAULT 1; m INT DEFAULT 2; r BOOLEAN; BEGIN r := n IS NOT"
                + " NULL AND m IS NOT NULL AND 1 NOT IN (SELECT k FROM q WHERE g = 0); RETURN r; END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 64 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 95"},
            {"EXECUTE IMMEDIATE $$DECLARE r BOOLEAN; BEGIN LET n := 1; r := n IS NOT NULL AND 1 NOT IN "
                + "(SELECT k FROM q WHERE g = 0); RETURN r; END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 42 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 60"},
            {"EXECUTE IMMEDIATE $$DECLARE n DEFAULT 1; r BOOLEAN; BEGIN r := n IS NOT NULL AND 1 NOT IN "
                + "(SELECT k FROM q WHERE g = 0); RETURN r; END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 43 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 60"},
            {"EXECUTE IMMEDIATE $$DECLARE n INT DEFAULT 1; BEGIN RETURN n IS NOT NULL AND 1 NOT IN (SELECT"
                + " k FROM q WHERE g = 0); END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 52"},
            {"EXECUTE IMMEDIATE $$DECLARE n INT DEFAULT 1; BEGIN IF (n IS NOT NULL AND 1 NOT IN (SELECT k "
                + "FROM q WHERE g = 0)) THEN RETURN 'T'; END IF; RETURN 'F'; END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 35 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 52"},
            {"EXECUTE IMMEDIATE $$DECLARE n INT DEFAULT 1; r BOOLEAN; BEGIN r := n IS NOT NULL\nAND 1 NOT "
                + "IN (SELECT k FROM q WHERE g = 0); RETURN r; END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 47 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 2, position 14"},
            {"EXECUTE IMMEDIATE $$DECLARE n INT DEFAULT 1; r BOOLEAN; BEGIN r := 1 NOT IN (SELECT k FROM q"
                + " WHERE g = 0) AND n IS NOT NULL; RETURN r; END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 47 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 25"},
            {"EXECUTE IMMEDIATE $$DECLARE n INT DEFAULT 1; r BOOLEAN; BEGIN r := :n IS NOT NULL AND 1 NOT "
                + "IN (SELECT k FROM q WHERE g = 0); RETURN r; END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 47 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 58"},
            {"EXECUTE IMMEDIATE $$DECLARE n TIME DEFAULT '10:00:00'; r BOOLEAN; BEGIN r := n IS NOT NULL "
                + "AND 1 NOT IN (SELECT k FROM q WHERE g = 0); RETURN r; END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 57 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 55"},
            {"EXECUTE IMMEDIATE $$DECLARE n INT DEFAULT 1; c CURSOR FOR SELECT 1; r BOOLEAN; BEGIN r := n "
                + "IS NOT NULL AND 1 NOT IN (SELECT k FROM q WHERE g = 0); RETURN r; END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 70 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 60"},
            {"EXECUTE IMMEDIATE $$DECLARE n INT DEFAULT 1; BEGIN RETURN (n, n) NOT IN (SELECT k, v FROM q "
                + "WHERE g = 0); END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 56"},
            {"EXECUTE IMMEDIATE $$DECLARE n INT DEFAULT 1; r BOOLEAN; BEGIN r := n NOT IN (SELECT k FROM q"
                + " WHERE g = n); RETURN r; END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 47 : SQL "
                + "compilation error: error line 1 at position 68 | invalid identifier 'N'"},
            {"EXECUTE IMMEDIATE $$DECLARE n INT DEFAULT 1; BEGIN LET x BOOLEAN := n NOT IN (SELECT k FROM "
                + "q WHERE g = 0); RETURN x; END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 48 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 42"},
            {"EXECUTE IMMEDIATE $$DECLARE n INT DEFAULT 1; x BOOLEAN DEFAULT n NOT IN (SELECT k FROM q "
                + "WHERE g = 0); BEGIN RETURN x; END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 43 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 42"},
            {"EXECUTE IMMEDIATE $$DECLARE r BOOLEAN; BEGIN r := 1 NOT IN (SELECT k FROM q WHERE g = 0) AND"
                + " 2 NOT IN (SELECT k FROM q WHERE g = 1); RETURN r; END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 68"},
            {"EXECUTE IMMEDIATE $$DECLARE r BOOLEAN; BEGIN r := 1 NOT IN (SELECT k FROM q WHERE g = 0) AND"
                + " 2 NOT IN (SELECT 1); RETURN r; END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 25"},
            {"EXECUTE IMMEDIATE $$DECLARE r BOOLEAN; BEGIN r := 2 NOT IN (SELECT 1) AND 1 NOT IN (SELECT k"
                + " FROM q WHERE g = 0); RETURN r; END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 49"},
            {"EXECUTE IMMEDIATE $$DECLARE r BOOLEAN; BEGIN r := 1 NOT IN (SELECT k FROM q) OR 2 NOT IN "
                + "(SELECT k FROM q) OR 3 NOT IN (SELECT v FROM q); RETURN r; END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 85"},
            {"EXECUTE IMMEDIATE $$BEGIN RETURN 1 NOT IN (SELECT k FROM q) AND 2 <> ALL (SELECT v FROM q); "
                + "END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 43"},
            {"EXECUTE IMMEDIATE $$BEGIN RETURN 1 NOT IN (SELECT k FROM q) AND 2 NOT IN (SELECT 1) AND 3 "
                + "NOT IN (SELECT 2); END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 17"},
            {"EXECUTE IMMEDIATE $$BEGIN RETURN 1 NOT IN (SELECT k FROM q)\nAND 2 NOT IN (SELECT v FROM q);"
                + " END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 2, position 14"},
            {"EXECUTE IMMEDIATE $$BEGIN RETURN NOT (1 NOT IN (SELECT k FROM q)) AND (2 NOT IN (SELECT v "
                + "FROM q)); END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 55"},
            {"EXECUTE IMMEDIATE $$BEGIN RETURN (1, 'x') NOT IN (SELECT k, s FROM q) AND 2 NOT IN (SELECT v"
                + " FROM q); END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 58"},
            {"EXECUTE IMMEDIATE $$DECLARE n INT DEFAULT 1; r BOOLEAN; BEGIN r := :n IS NOT NULL AND 1 NOT "
                + "IN (SELECT k FROM q WHERE g = 0); RETURN r; END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 47 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 58"},
            {"EXECUTE IMMEDIATE $$DECLARE nnnnn INT DEFAULT 1; r BOOLEAN; BEGIN r := :nnnnn IS NOT NULL "
                + "AND 1 NOT IN (SELECT k FROM q WHERE g = 0); RETURN r; END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 51 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 62"},
            {"EXECUTE IMMEDIATE $$DECLARE n VARCHAR DEFAULT 'x'; r BOOLEAN; BEGIN r := :n IS NOT NULL AND "
                + "1 NOT IN (SELECT k FROM q WHERE g = 0); RETURN r; END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 53 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 44"},
            {"EXECUTE IMMEDIATE $$DECLARE n DATE DEFAULT '2020-01-01'; r BOOLEAN; BEGIN r := :n IS NOT "
                + "NULL AND 1 NOT IN (SELECT k FROM q WHERE g = 0); RETURN r; END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 59 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 50"},
            {"EXECUTE IMMEDIATE $$DECLARE n VARIANT DEFAULT TO_VARIANT(1); r BOOLEAN; BEGIN r := :n IS NOT"
                + " NULL AND 1 NOT IN (SELECT k FROM q WHERE g = 0); RETURN r; END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 65"},
            {"EXECUTE IMMEDIATE $$DECLARE n INT DEFAULT 1; m INT DEFAULT 1; r BOOLEAN; BEGIN r := :n IS "
                + "NOT NULL AND :m IS NOT NULL AND 1 NOT IN (SELECT k FROM q WHERE g = 0); RETURN r; END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 64 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 91"},
            {"EXECUTE IMMEDIATE $$DECLARE n INT DEFAULT 1; BEGIN RETURN :n NOT IN (SELECT k FROM q WHERE g"
                + " = 0); END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 32"},
            {"EXECUTE IMMEDIATE $$DECLARE n NUMBER(10,2) DEFAULT 1; BEGIN RETURN :n NOT IN (SELECT k FROM "
                + "q WHERE g = 0); END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 47 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 32"},
            {"EXECUTE IMMEDIATE $$DECLARE n INT DEFAULT 1; BEGIN RETURN n + :n NOT IN (SELECT k FROM q "
                + "WHERE g = 0); END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 53"},
            {"EXECUTE IMMEDIATE $$DECLARE n INT DEFAULT 1; BEGIN RETURN n IS NOT NULL AND :n + 1 NOT IN "
                + "(SELECT k FROM q WHERE g = 0); END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 71"},
            {"EXECUTE IMMEDIATE $$DECLARE n BOOLEAN DEFAULT TRUE; BEGIN RETURN :n AND 1 NOT IN (SELECT k "
                + "FROM q WHERE g = 0); END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 45 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 33"},
            {"EXECUTE IMMEDIATE $$DECLARE n INT DEFAULT 1; BEGIN RETURN n = 1 AND 1 NOT IN (SELECT k FROM "
                + "q WHERE g = 0) AND 2 NOT IN (SELECT k FROM q WHERE g = 0); END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 87"},
        });
    }

    @Test
    public void semiStructuredVariablesAndSeveralAntiJoins() {
        engine.execute("CREATE OR REPLACE TABLE q (g INT, k INT, s VARCHAR, v INT)");
        engine.execute("INSERT INTO q VALUES (0, 1, 'x', 10), (0, 2, 'y', 20), (1, 1, 'x', 10), (1, NULL, 'y', 20), "
            + "(2, 1, 'x', 10), (2, 2, NULL, 20), (3, NULL, NULL, NULL), (5, 1, NULL, 10), (6, NULL, 'x', "
            + "10)");
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$DECLARE r BOOLEAN; BEGIN r := TRUE AND 1 NOT IN (SELECT k FROM q WHERE g"
                + " = 0); RETURN r; END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 34"},
            {"EXECUTE IMMEDIATE $$DECLARE n VARIANT DEFAULT TO_VARIANT(1); r BOOLEAN; BEGIN r := n IS NULL"
                + " AND 1 NOT IN (SELECT k FROM q WHERE g = 0); RETURN r; END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 63"},
            {"EXECUTE IMMEDIATE $$DECLARE n INT DEFAULT 0; r BOOLEAN; BEGIN r := 1 NOT IN (SELECT k FROM q"
                + " WHERE g = n); RETURN r; END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 47 : SQL "
                + "compilation error: error line 1 at position 51 | invalid identifier 'N'"},
            {"EXECUTE IMMEDIATE $$DECLARE n ARRAY DEFAULT ARRAY_CONSTRUCT(1); r BOOLEAN; BEGIN r := n IS "
                + "NULL AND 1 NOT IN (SELECT k FROM q WHERE g = 0); RETURN r; END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 66 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 61"},
            {"EXECUTE IMMEDIATE $$DECLARE n OBJECT DEFAULT OBJECT_CONSTRUCT('a', 1); r BOOLEAN; BEGIN r :="
                + " n IS NULL AND 1 NOT IN (SELECT k FROM q WHERE g = 0); RETURN r; END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 73 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 62"},
            {"EXECUTE IMMEDIATE $$DECLARE s VARCHAR DEFAULT 'x'; r BOOLEAN; BEGIN r := (s, 1) NOT IN "
                + "(SELECT s, k FROM q WHERE g = 0); RETURN r; END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 53 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 33"},
            {"EXECUTE IMMEDIATE $$DECLARE n NUMBER(5,0) DEFAULT 1; r BOOLEAN; BEGIN r := n = 1 AND 1 NOT "
                + "IN (SELECT k FROM q WHERE g = 0); RETURN r; END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 55 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 51"},
            {"EXECUTE IMMEDIATE $$DECLARE n INT DEFAULT 1; BEGIN RETURN n + n + 1 NOT IN (SELECT k FROM q "
                + "WHERE g = 0); END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 59"},
            {"EXECUTE IMMEDIATE $$DECLARE n INT DEFAULT 1; r BOOLEAN; BEGIN r := n NOT IN (SELECT k FROM q"
                + " WHERE g = 0) AND n NOT IN (SELECT k FROM q WHERE g = 0); RETURN r; END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 47 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 102"},
            {"EXECUTE IMMEDIATE $$DECLARE n INT DEFAULT 1; r BOOLEAN; BEGIN r := n <> ALL (SELECT k FROM q"
                + " WHERE g = 0); RETURN r; END$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 47 : SQL "
                + "compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 37"},
        });
    }
}
