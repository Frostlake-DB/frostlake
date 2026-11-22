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
 * A BEGIN that opens no transaction in a block is refused at the word after it, and two shapes read on (all
 * live-verified): BEGIN WORK NAME names the name, then the text's last END; a BEGIN WORK of an IF's or a WHILE's
 * statements, the end of the text. A FOR's, a nested block's, a NAME's and a bare BEGIN's stop at the first line.
 */
public class BlockBeginWorkLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void aBeginThatOpensNoTransaction() {
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$BEGIN BEGIN WORK NAME t1; ROLLBACK; END$$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'WORK'. | syntax "
                    + "error line 1 at position 22 unexpected 't1'. | syntax error line 1 at position 36 unexpected "
                    + "'END'."},
            {"EXECUTE IMMEDIATE $$BEGIN IF (TRUE) THEN BEGIN WORK; END IF; END$$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'WORK'. | syntax "
                    + "error line 1 at position 44 unexpected '<EOF>'."},
            {"EXECUTE IMMEDIATE $$BEGIN BEGIN WORK NAME t1; END$$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'WORK'. | syntax "
                    + "error line 1 at position 22 unexpected 't1'. | syntax error line 1 at position 26 unexpected "
                    + "'END'."},
            {"EXECUTE IMMEDIATE $$BEGIN BEGIN WORK NAME t1; SELECT 1; END$$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'WORK'. | syntax "
                    + "error line 1 at position 22 unexpected 't1'. | syntax error line 1 at position 36 unexpected "
                    + "'END'."},
            {"EXECUTE IMMEDIATE $$BEGIN LET x := 1; BEGIN WORK NAME t1; END$$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'WORK'. | syntax "
                    + "error line 1 at position 34 unexpected 't1'. | syntax error line 1 at position 38 unexpected "
                    + "'END'."},
            {"EXECUTE IMMEDIATE $$BEGIN IF (TRUE) THEN BEGIN WORK; END IF; RETURN 1; END$$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'WORK'. | syntax "
                    + "error line 1 at position 54 unexpected '<EOF>'."},
            {"EXECUTE IMMEDIATE $$BEGIN IF (TRUE) THEN BEGIN NAME t1; END IF; END$$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'NAME'."},
            {"EXECUTE IMMEDIATE $$BEGIN IF (TRUE) THEN BEGIN; END IF; END$$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ';'."},
            {"EXECUTE IMMEDIATE $$BEGIN WHILE (FALSE) DO BEGIN WORK; END WHILE; END$$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'WORK'. | syntax "
                    + "error line 1 at position 49 unexpected '<EOF>'."},
            {"EXECUTE IMMEDIATE $$BEGIN BEGIN NAME t1; ROLLBACK; END$$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'NAME'."},
            {"EXECUTE IMMEDIATE $$BEGIN BEGIN WORK; ROLLBACK; END$$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'WORK'."},
            {"EXECUTE IMMEDIATE $$BEGIN IF (TRUE) THEN BEGIN WORK NAME t1; END IF; END$$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'WORK'. | syntax "
                    + "error line 1 at position 37 unexpected 't1'. | syntax error line 1 at position 49 unexpected "
                    + "'END'."},
            {"EXECUTE IMMEDIATE $$BEGIN IF (TRUE) THEN LET y := 2; BEGIN WORK; END IF; END$$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'WORK'. | syntax "
                    + "error line 1 at position 56 unexpected '<EOF>'."},
            {"EXECUTE IMMEDIATE $$BEGIN FOR i IN 1 TO 2 DO BEGIN WORK; END FOR; END$$",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'WORK'."},
        });
    }
}
