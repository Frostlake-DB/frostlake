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
 * A block's variable standing where a date function reads its unit (live-verified). A variable holding a text is
 * the unit, in a RETURN's value, an IF's condition and as a bind of a statement. A variable of another type is
 * refused as the conversion the block writes for it: {@code Date/time component [TO_NUMBER('1') ]for function
 * DATEADD needs to be an identifier or a string literal.}, inside a larger expression too.
 */
public class BlockVariableAsDateUnitTest extends MeasuredCellsTestSupport {

    @Test
    public void aVariableInTheUnitSlot() {
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ DECLARE u INT DEFAULT 1; BEGIN RETURN DATEADD(u, 1, CURRENT_DATE()) IS "
                + "NULL; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 39 : SQL "
                + "compilation error: Date/time component [TO_NUMBER('1') ]for function DATEADD needs to be an "
                + "identifier or a string literal."},
            {"EXECUTE IMMEDIATE $$ DECLARE u INT DEFAULT 1; BEGIN RETURN DATEADD(u, 1, CURRENT_DATE()); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 39 : SQL "
                + "compilation error: Date/time component [TO_NUMBER('1') ]for function DATEADD needs to be an "
                + "identifier or a string literal."},
            {"EXECUTE IMMEDIATE $$ DECLARE u INT DEFAULT 1; BEGIN RETURN DATEDIFF(u, CURRENT_DATE(), "
                + "CURRENT_DATE()); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 39 : SQL "
                + "compilation error: Date/time component [TO_NUMBER('1') ]for function DATEDIFF needs to be an"
                + " identifier or a string literal."},
            {"EXECUTE IMMEDIATE $$ DECLARE u INT DEFAULT 1; BEGIN RETURN DATE_TRUNC(u, CURRENT_DATE()); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 39 : SQL "
                + "compilation error: Date/time component [TO_NUMBER('1') ]for function DATE_TRUNC needs to be "
                + "an identifier or a string literal."},
            {"EXECUTE IMMEDIATE $$ DECLARE u INT DEFAULT 1; BEGIN RETURN DATE_PART(u, CURRENT_DATE()); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 39 : SQL "
                + "compilation error: | invalid parameter 'TO_NUMBER('1') is not a constant'"},
            {"EXECUTE IMMEDIATE $$ DECLARE u INT DEFAULT 1; BEGIN RETURN TIMEADD(u, 1, "
                + "CURRENT_TIMESTAMP()) IS NULL; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 39 : SQL "
                + "compilation error: Date/time component [TO_NUMBER('1') ]for function TIMEADD needs to be an "
                + "identifier or a string literal."},
            {"EXECUTE IMMEDIATE $$ DECLARE u INT DEFAULT 1; BEGIN RETURN LAST_DAY(CURRENT_DATE(), u); END;"
                + " $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 39 : SQL "
                + "compilation error: Date/time component [TO_NUMBER('1') ]for function LAST_DAY needs to be an"
                + " identifier or a string literal."},
            {"EXECUTE IMMEDIATE $$ DECLARE u VARCHAR DEFAULT 'day'; BEGIN RETURN DATE_TRUNC(u, "
                + "'2024-01-15'::DATE); END; $$",
                "2024-01-15"},
            {"EXECUTE IMMEDIATE $$ DECLARE u VARCHAR DEFAULT 'day'; BEGIN RETURN DATEDIFF(u, "
                + "'2024-01-15'::DATE, '2024-01-17'::DATE); END; $$",
                "2"},
            {"EXECUTE IMMEDIATE $$ DECLARE u VARCHAR DEFAULT 'year'; BEGIN RETURN DATE_PART(u, "
                + "'2024-01-15'::DATE); END; $$",
                "2024"},
            {"EXECUTE IMMEDIATE $$ DECLARE u VARCHAR DEFAULT 'month'; BEGIN RETURN "
                + "LAST_DAY('2024-01-15'::DATE, u); END; $$",
                "2024-01-31"},
            {"EXECUTE IMMEDIATE $$ DECLARE u FLOAT DEFAULT 1.5; BEGIN RETURN DATEADD(u, 1, "
                + "CURRENT_DATE()); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 43 : SQL "
                + "compilation error: Date/time component [TO_DOUBLE('1.5') ]for function DATEADD needs to be "
                + "an identifier or a string literal."},
            {"EXECUTE IMMEDIATE $$ DECLARE u BOOLEAN DEFAULT TRUE; BEGIN RETURN DATEADD(u, 1, "
                + "CURRENT_DATE()); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 46 : SQL "
                + "compilation error: Date/time component [TO_BOOLEAN('true') ]for function DATEADD needs to be"
                + " an identifier or a string literal."},
            {"EXECUTE IMMEDIATE $$ DECLARE u DATE DEFAULT '2024-01-01'; BEGIN RETURN DATEADD(u, 1, "
                + "CURRENT_DATE()); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 51 : SQL "
                + "compilation error: Date/time component [TO_DATE('2024-01-01') ]for function DATEADD needs to"
                + " be an identifier or a string literal."},
            {"EXECUTE IMMEDIATE $$ DECLARE u VARCHAR DEFAULT 'day'; d DATE; BEGIN SELECT DATEADD(:u, 1, "
                + "'2024-01-01'::DATE) INTO :d; RETURN d; END; $$",
                "2024-01-02"},
            {"EXECUTE IMMEDIATE $$ DECLARE u VARCHAR DEFAULT 'nosuch'; BEGIN RETURN DATEADD(u, 1, "
                + "'2024-01-01'::DATE); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 50 : SQL "
                + "compilation error: ['nosuch'] is not a valid date/time component for function DATEADD."},
            {"EXECUTE IMMEDIATE $$ DECLARE day INT DEFAULT 1; BEGIN RETURN DATEADD(day, 1, "
                + "'2024-01-01'::DATE); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 41 : SQL "
                + "compilation error: Date/time component [TO_NUMBER('1') ]for function DATEADD needs to be an "
                + "identifier or a string literal."},
            {"EXECUTE IMMEDIATE $$ DECLARE u NUMBER(10,2) DEFAULT 1.5; BEGIN RETURN DATEADD(u, 1, "
                + "CURRENT_DATE()); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 50 : SQL "
                + "compilation error: Date/time component [TO_NUMBER('1.50', 10, 2) ]for function DATEADD needs"
                + " to be an identifier or a string literal."},
            {"EXECUTE IMMEDIATE $$ DECLARE u VARCHAR DEFAULT 'day'; BEGIN IF (DATEADD(u, 1, "
                + "'2024-01-01'::DATE) IS NULL) THEN RETURN 1; END IF; RETURN 2; END; $$",
                "2"},
            {"SELECT DATEADD(nosuch_unit_x9, 1, CURRENT_DATE())",
                "REFUSED SQL compilation error: ['NOSUCH_UNIT_X9'] is not a valid date/time component for "
                + "function DATEADD."},
            {"EXECUTE IMMEDIATE $$ DECLARE u VARCHAR DEFAULT 'day'; BEGIN RETURN EXTRACT(u FROM "
                + "'2024-01-15'::DATE); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 47 : SQL "
                + "compilation error: | invalid value [u] for parameter 'EXTRACT date/time part'"},
            {"EXECUTE IMMEDIATE $$ DECLARE u INT DEFAULT 1; BEGIN RETURN DATEADD('u', 1, CURRENT_DATE()); "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 39 : SQL "
                + "compilation error: ['u'] is not a valid date/time component for function DATEADD."},
        });
    }
}
