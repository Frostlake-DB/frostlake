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
 * A word that opens what must follow is read as the account reads it (all live-verified): LEFT, RIGHT, FULL and
 * NATURAL alone after a relation, or right after FROM, are read on and the token after them is named; an IS or an IS
 * NOT that no NULL, DISTINCT or truth value follows is named itself; a word no alias may be after an AS is named with
 * the token after it. A join word followed by what continues it keeps its own reading.
 */
public class DanglingWordLinesTest extends MeasuredCellsTestSupport {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE fz (a INT)");
    }

    @Test
    public void aWordThatOpensWhatMustFollow() {
        assertCells(new String[][] {
            {"SELECT * FROM fz LEFT",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '<EOF>'."},
            {"SELECT * FROM fz LEFT WHERE a = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'WHERE'."},
            {"SELECT * FROM fz LEFT OUTER",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '<EOF>'."},
            {"SELECT * FROM fz NATURAL LEFT",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '<EOF>'."},
            {"SELECT * FROM fz RIGHT ORDER BY a",
                "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'ORDER'."},
            {"SELECT * FROM fz FULL OUTER WHERE TRUE",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'WHERE'."},
            {"SELECT * FROM fz INNER",
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '<EOF>'."},
            {"SELECT * FROM fz CROSS",
                "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '<EOF>'."},
            {"SELECT 1 IS",
                "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected 'IS'."},
            {"SELECT 1 IS FROM fz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected 'IS'."},
            {"SELECT a IS WHERE TRUE",
                "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected 'IS'."},
            {"SELECT 1 IS NOT",
                "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected 'IS'."},
            {"DESCRIBE TABLE fz TRIGGER",
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'TRIGGER'."},
            {"SELECT * FROM fz AS QUALIFY",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'QUALIFY'. | syntax "
                    + "error line 1 at position 27 unexpected '<EOF>'."},
            {"SELECT * FROM fz AS QUALIFY WHERE TRUE",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'QUALIFY'. | syntax "
                    + "error line 1 at position 28 unexpected 'WHERE'."},
            {"SELECT * FROM fz AS ORDER",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'ORDER'. | syntax "
                    + "error line 1 at position 25 unexpected '<EOF>'."},
            {"WITH LEFT AS (SELECT 1 AS a) SELECT * FROM LEFT",
                "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected '<EOF>'."},
            {"SELECT * FROM fz NATURAL",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '<EOF>'."},
            {"SELECT * FROM fz FULL",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected '<EOF>'."},
        });
    }
}
