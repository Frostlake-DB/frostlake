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
 * Syntax lines (live-verified): a bracket written right after a table's name in a FROM list is refused, and the
 * account's recovery skips from it to the first token that may follow the reference - a comma, a clause's or a join's
 * word, a semicolon, the end of input, and a closing bracket where the query stands in an EXISTS's, a set operation's
 * or the statement's own brackets - wherever it stands, inside the refused bracket too, and reads on from there.
 */
public class TableNameBracketLinesTest extends MeasuredCellsTestSupport {

    /** In a statement's own FROM list. */
    @Test
    public void aReferenceOfTheStatementsQuery() {
        assertCells(new String[][] {
            {"SELECT 1 FROM zz (a)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '('."},
            {"SELECT 1 FROM zz (a, b)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '('. | syntax error "
                    + "line 1 at position 22 unexpected ')'."},
            {"SELECT 1 FROM zz (a, b, c)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '('. | syntax error "
                    + "line 1 at position 25 unexpected ')'."},
            {"SELECT 1 FROM zz ((a))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '('."},
            {"SELECT 1 FROM zz (1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '('."},
            {"SELECT 1 FROM zz ()",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '('."},
            {"SELECT 1 FROM zz (a b)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '('."},
            {"SELECT 1 FROM zz (a) x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '('."},
            {"SELECT 1 FROM zz (a WHERE 1 = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '('. | syntax error "
                    + "line 1 at position 31 unexpected ')'."},
            {"SELECT 1 FROM zz (a JOIN b)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '('. | syntax error "
                    + "line 1 at position 26 unexpected ')'."},
            {"SELECT 1 FROM zz (a, b) WHERE 1 = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '('. | syntax error "
                    + "line 1 at position 22 unexpected ')'."},
            {"SELECT 1 FROM zz (a), t (b)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '('. | syntax error "
                    + "line 1 at position 24 unexpected '('."},
            {"SELECT 1 FROM zz.yy (a, b)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '('. | syntax error "
                    + "line 1 at position 25 unexpected ')'."},
            {"SELECT 1 FROM t, zz (a, b)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '('. | syntax error "
                    + "line 1 at position 25 unexpected ')'."},
            {"SELECT 1 FROM t JOIN zz (a, b)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '('. | syntax error "
                    + "line 1 at position 29 unexpected ')'."},
            {"SELECT 1 FROM t1 JOIN t2 USING (a) JOIN zz (a, b)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected '('. | syntax error "
                    + "line 1 at position 48 unexpected ')'."},
            {"SELECT 1 FROM t JOIN USING (a, b, c)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '('. | syntax error "
                    + "line 1 at position 35 unexpected ')'."},
            {"INSERT INTO t SELECT * FROM zz (a, b)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '('. | syntax error "
                    + "line 1 at position 36 unexpected ')'."},
            {"CREATE VIEW v2 AS SELECT * FROM zz (a, b)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected '('. | syntax error "
                    + "line 1 at position 40 unexpected ')'."},
        });
    }

    /** A semicolon ends the skip, and the next statement is read. */
    @Test
    public void laterStatements() {
        assertCells(new String[][] {
            {"SELECT 1 FROM zz (a); SELECT 1 x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '('. | syntax error "
                    + "line 1 at position 33 unexpected 'y'."},
            {"SELECT 1 FROM zz (a, b); SELECT 1 x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '('. | syntax error "
                    + "line 1 at position 22 unexpected ')'."},
            {"SELECT 1 FROM zz (a; SELECT 1 x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '('. | syntax error "
                    + "line 1 at position 32 unexpected 'y'."},
            {"SELECT 1 FROM zz (a, b; SELECT 1 x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '('. | syntax error "
                    + "line 1 at position 35 unexpected 'y'."},
        });
    }

    /** In an EXISTS's, a set operation's or the statement's own brackets, a closing bracket ends the skip too. */
    @Test
    public void aReferenceOfABracketedQuery() {
        assertCells(new String[][] {
            {"SELECT 1 WHERE EXISTS (SELECT 1 FROM zz (a))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '('. | syntax error "
                    + "line 1 at position 43 unexpected ')'."},
            {"SELECT 1 WHERE EXISTS (SELECT 1 FROM zz (a, b))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '('. | syntax error "
                    + "line 1 at position 46 unexpected ')'."},
            {"SELECT 1 WHERE EXISTS (SELECT 1 FROM zz (a) WHERE 1 = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '('. | syntax error "
                    + "line 1 at position 44 unexpected 'WHERE'."},
            {"SELECT 1 WHERE EXISTS (SELECT 1 FROM zz (a) x)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '('. | syntax error "
                    + "line 1 at position 44 unexpected 'x'."},
            {"SELECT 1 WHERE EXISTS (SELECT 1 FROM zz ((a)))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '('. | syntax error "
                    + "line 1 at position 44 unexpected ')'."},
            {"SELECT 1 WHERE EXISTS (SELECT 1 FROM zz ())",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '('. | syntax error "
                    + "line 1 at position 42 unexpected ')'."},
            {"SELECT 1 FROM t WHERE NOT EXISTS (SELECT 1 FROM zz (a))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected '('. | syntax error "
                    + "line 1 at position 54 unexpected ')'."},
            {"SELECT 1 FROM t WHERE EXISTS (SELECT 1 FROM t, zz (a))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected '('. | syntax error "
                    + "line 1 at position 53 unexpected ')'."},
            {"SELECT 1 FROM t WHERE EXISTS (SELECT 1 FROM zz (a, b) WHERE 1 = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected '('. | syntax error "
                    + "line 1 at position 54 unexpected 'WHERE'."},
            {"(SELECT 1 FROM zz (a))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '('. | syntax error "
                    + "line 1 at position 21 unexpected ')'."},
            {"(SELECT 1 FROM zz (a, b))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '('. | syntax error "
                    + "line 1 at position 24 unexpected ')'."},
            {"SELECT 1 FROM t UNION (SELECT 1 FROM zz (a))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '('. | syntax error "
                    + "line 1 at position 43 unexpected ')'."},
        });
    }

    /** In a derived table's brackets the reference is given up with the derived table. */
    @Test
    public void aReferenceOfADerivedTable() {
        assertCells(new String[][] {
            {"SELECT * FROM (SELECT 1 FROM zz (a))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '('."},
            {"SELECT * FROM (SELECT 1 FROM zz (a)) WHERE 1 = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '('."},
        });
    }
}
