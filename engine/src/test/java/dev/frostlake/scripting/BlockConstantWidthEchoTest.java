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
 * LAST_QUERY_ID refuses an index over a block's exact NUMBER variable and echoes the operation over the variable's
 * binding as the plan types its constants (live-verified): around the variable, another variable, a folded
 * conversion or a text read as a number, the cast to that constant's own type is kept wherever the type is stored
 * at another width (1, 2, 4, 8 or 16 bytes for up to 2, 4, 9, 18 and 38 digits) than the operation computes in -
 * a sum or a difference over the larger whole digits plus one, a remainder or a comparison over the larger whole
 * digits, a product over both widths - and inside the rescale of a lower-scale operand too; a number written out
 * beside it stays as written.
 */
public class BlockConstantWidthEchoTest extends MeasuredCellsTestSupport {

    @Test
    public void besideAnotherVariable() {
        assertCells(new String[][] {
            {"BEGIN LET n NUMBER(10,2) := 2; LET i INT := 5; RETURN LAST_QUERY_ID(n + i) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 54 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(10,2))) + (CAST(5 AS NUMBER(38,2))) AS NUMBER(36,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i INT := 5; RETURN LAST_QUERY_ID(i + n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 54 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(5 AS NUMBER(38,2))) + (CAST(2 AS NUMBER(10,2))) AS NUMBER(36,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i NUMBER(1,0) := 5; RETURN LAST_QUERY_ID(n + i) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 + (CAST(5 AS NUMBER(10,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i NUMBER(1,0) := 5; RETURN LAST_QUERY_ID(i + n) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(5 AS NUMBER(10,2))) + 2 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i NUMBER(5,0) := 5; RETURN LAST_QUERY_ID(n + i) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 + (CAST(5 AS NUMBER(10,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i NUMBER(5,0) := 5; RETURN LAST_QUERY_ID(i + n) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(5 AS NUMBER(10,2))) + 2 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i NUMBER(7,0) := 5; RETURN LAST_QUERY_ID(n + i) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 + (CAST(5 AS NUMBER(10,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i NUMBER(7,0) := 5; RETURN LAST_QUERY_ID(i + n) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(5 AS NUMBER(10,2))) + 2 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i NUMBER(8,0) := 5; RETURN LAST_QUERY_ID(n + i) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 + (CAST(5 AS NUMBER(10,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i NUMBER(8,0) := 5; RETURN LAST_QUERY_ID(i + n) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(5 AS NUMBER(10,2))) + 2 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i NUMBER(9,0) := 5; RETURN LAST_QUERY_ID(n + i) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 + (CAST(5 AS NUMBER(11,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i NUMBER(9,0) := 5; RETURN LAST_QUERY_ID(i + n) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(5 AS NUMBER(11,2))) + 2 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i NUMBER(10,0) := 5; RETURN LAST_QUERY_ID(n + i) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 + (CAST(5 AS NUMBER(12,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i NUMBER(10,0) := 5; RETURN LAST_QUERY_ID(i + n) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(5 AS NUMBER(12,2))) + 2 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i NUMBER(18,0) := 5; RETURN LAST_QUERY_ID(n + i) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(10,2))) + (CAST(5 AS NUMBER(20,2))) AS NUMBER(19,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i NUMBER(18,0) := 5; RETURN LAST_QUERY_ID(i + n) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(5 AS NUMBER(20,2))) + (CAST(2 AS NUMBER(10,2))) AS NUMBER(19,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i NUMBER(38,0) := 5; RETURN LAST_QUERY_ID(n + i) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(10,2))) + (CAST(5 AS NUMBER(38,2))) AS NUMBER(36,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i NUMBER(38,0) := 5; RETURN LAST_QUERY_ID(i + n) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(5 AS NUMBER(38,2))) + (CAST(2 AS NUMBER(10,2))) AS NUMBER(36,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i INT := 1; RETURN LAST_QUERY_ID(n + i) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 54 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(10,2))) + (CAST(1 AS NUMBER(38,2))) AS NUMBER(36,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i INT := 1000; RETURN LAST_QUERY_ID(n + i) IS NOT NULL; "
                + "END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 57 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(10,2))) + (CAST(1000 AS NUMBER(38,2))) AS NUMBER(36,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i INT := 5; RETURN LAST_QUERY_ID(n - i) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 54 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(10,2))) - (CAST(5 AS NUMBER(38,2))) AS NUMBER(36,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i INT := 5; RETURN LAST_QUERY_ID(n % i) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 54 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(10,2))) % (CAST(5 AS NUMBER(38,2))) AS NUMBER(36,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i INT := 5; RETURN LAST_QUERY_ID((n > i)::INT) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 54 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST((CAST(2 AS NUMBER(10,2))) > (CAST(5 AS NUMBER(38,2))) AS BOOLEAN) AS "
                + "NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET m NUMBER(4,3) := 1; RETURN LAST_QUERY_ID(n + m) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(11,3))) + (CAST(1 AS NUMBER(4,3))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET m NUMBER(4,3) := 1; RETURN LAST_QUERY_ID(m + n) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(1 AS NUMBER(4,3))) + (CAST(2 AS NUMBER(11,3))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET m NUMBER(5,3) := 1; RETURN LAST_QUERY_ID(n + m) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(11,3))) + (CAST(1 AS NUMBER(5,3))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET m NUMBER(5,3) := 1; RETURN LAST_QUERY_ID(m + n) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(1 AS NUMBER(5,3))) + (CAST(2 AS NUMBER(11,3))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET m NUMBER(9,3) := 1; RETURN LAST_QUERY_ID(n + m) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(11,3))) + (CAST(1 AS NUMBER(9,3))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET m NUMBER(9,3) := 1; RETURN LAST_QUERY_ID(m + n) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(1 AS NUMBER(9,3))) + (CAST(2 AS NUMBER(11,3))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET m NUMBER(10,3) := 1; RETURN LAST_QUERY_ID(n + m) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(11,3))) + 1 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET m NUMBER(10,3) := 1; RETURN LAST_QUERY_ID(m + n) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(1 + (CAST(2 AS NUMBER(11,3))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET m NUMBER(11,3) := 1; RETURN LAST_QUERY_ID(n + m) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(11,3))) + 1 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET m NUMBER(11,3) := 1; RETURN LAST_QUERY_ID(m + n) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(1 + (CAST(2 AS NUMBER(11,3))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET m NUMBER(12,3) := 1; RETURN LAST_QUERY_ID(n + m) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(12,3))) + 1 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET m NUMBER(12,3) := 1; RETURN LAST_QUERY_ID(m + n) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(1 + (CAST(2 AS NUMBER(12,3))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET m NUMBER(20,3) := 1; RETURN LAST_QUERY_ID(n + m) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(20,3))) + 1 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET m NUMBER(20,3) := 1; RETURN LAST_QUERY_ID(m + n) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(1 + (CAST(2 AS NUMBER(20,3))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET m NUMBER(38,3) := 1; RETURN LAST_QUERY_ID(n + m) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(38,3))) + 1 AS NUMBER(35,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET m NUMBER(38,3) := 1; RETURN LAST_QUERY_ID(m + n) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(1 + (CAST(2 AS NUMBER(38,3))) AS NUMBER(35,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET m NUMBER(10,3) := 1000; RETURN LAST_QUERY_ID(n + m) IS "
                + "NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 66 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(11,3))) + 1000 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(5,2) := 2; LET m NUMBER(10,3) := 1; RETURN LAST_QUERY_ID(n + m) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(10,3))) + 1 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(38,2) := 2; LET m NUMBER(10,3) := 1; RETURN LAST_QUERY_ID(n + m) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(38,3))) + (CAST(1 AS NUMBER(10,3))) AS NUMBER(35,0))'"},
            {"BEGIN LET n NUMBER(20,2) := 2; LET m NUMBER(10,3) := 1; RETURN LAST_QUERY_ID(n + m) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(21,3))) + (CAST(1 AS NUMBER(10,3))) AS NUMBER(19,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET m NUMBER(38,6) := 1; RETURN LAST_QUERY_ID(n + m) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(38,6))) + 1 AS NUMBER(32,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET k NUMBER(38,2) := 3; RETURN LAST_QUERY_ID(n + k) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(10,2))) + 3 AS NUMBER(36,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET k NUMBER(5,2) := 3; RETURN LAST_QUERY_ID(n + k) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 + (CAST(3 AS NUMBER(5,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(n + TO_NUMBER('1')) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(10,2))) + (CAST(1 AS NUMBER(38,2))) AS NUMBER(36,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(TO_NUMBER('1') + n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(1 AS NUMBER(38,2))) + (CAST(2 AS NUMBER(10,2))) AS NUMBER(36,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(n + TO_NUMBER('1', 5, 0)) IS NOT NULL; "
                + "END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 + (CAST(1 AS NUMBER(10,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(TO_NUMBER('1', 5, 0) + n) IS NOT NULL; "
                + "END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(1 AS NUMBER(10,2))) + 2 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(n + TO_NUMBER('1', 12, 0)) IS NOT NULL; "
                + "END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 + (CAST(1 AS NUMBER(14,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(TO_NUMBER('1', 12, 0) + n) IS NOT NULL; "
                + "END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(1 AS NUMBER(14,2))) + 2 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(n + CAST('1' AS NUMBER(5,0))) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 + (CAST(1 AS NUMBER(10,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(CAST('1' AS NUMBER(5,0)) + n) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(1 AS NUMBER(10,2))) + 2 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(n + CAST('1' AS NUMBER(9,0))) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 + (CAST(1 AS NUMBER(11,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(CAST('1' AS NUMBER(9,0)) + n) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(1 AS NUMBER(11,2))) + 2 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(n + '1') IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(10,2))) + (CAST(1 AS NUMBER(18,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID('1' + n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(1 AS NUMBER(18,2))) + (CAST(2 AS NUMBER(10,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(n + CAST('1' AS NUMBER(5,1))) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 + (CAST(1 AS NUMBER(10,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(CAST('1' AS NUMBER(5,1)) + n) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(1 AS NUMBER(10,2))) + 2 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(n + CAST('1' AS NUMBER(12,1))) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 + (CAST(1 AS NUMBER(13,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(CAST('1' AS NUMBER(12,1)) + n) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(1 AS NUMBER(13,2))) + 2 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(n + TO_NUMBER('1.125', 10, 3)) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(11,3))) + 1.125 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(TO_NUMBER('1.125', 10, 3) + n) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(1.125 + (CAST(2 AS NUMBER(11,3))) AS NUMBER(18,0))'"},
            {"BEGIN LET i NUMBER(5,0) := 5; RETURN LAST_QUERY_ID(i + 1.25) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(5 AS NUMBER(7,2))) + 1.25 AS NUMBER(18,0))'"},
            {"BEGIN LET i NUMBER(9,0) := 5; RETURN LAST_QUERY_ID(i + 1.25) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(5 AS NUMBER(11,2))) + 1.25 AS NUMBER(18,0))'"},
            {"SELECT LAST_QUERY_ID(CAST('2' AS NUMBER(10,2)) + TO_NUMBER('1')) IS NOT NULL",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, "
                + "found 'CAST(2 + (CAST(1 AS NUMBER(38,2))) AS NUMBER(36,0))'"},
            {"SELECT LAST_QUERY_ID(CAST('2' AS NUMBER(10,2)) + CAST('1' AS NUMBER(10,3))) IS NOT NULL",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, "
                + "found 'CAST((CAST(2 AS NUMBER(11,3))) + 1 AS NUMBER(18,0))'"},
        });
    }

    @Test
    public void storageWidths() {
        assertCells(new String[][] {
            {"BEGIN LET n NUMBER(2,1) := 1; RETURN LAST_QUERY_ID(n + 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(1 AS NUMBER(2,1))) + 1 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(3,1) := 1; RETURN LAST_QUERY_ID(n + 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(1 + 1 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(4,2) := 1; RETURN LAST_QUERY_ID(n + 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(1 AS NUMBER(4,2))) + 1 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(5,2) := 1; RETURN LAST_QUERY_ID(n + 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(1 + 1 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(7,2) := 1; RETURN LAST_QUERY_ID(n + 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(1 + 1 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(8,2) := 1; RETURN LAST_QUERY_ID(n + 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(1 + 1 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 1; RETURN LAST_QUERY_ID(n + 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(1 AS NUMBER(9,2))) + 1 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(17,2) := 1; RETURN LAST_QUERY_ID(n + 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(1 + 1 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(18,2) := 1; RETURN LAST_QUERY_ID(n + 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(1 AS NUMBER(18,2))) + 1 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(19,2) := 1; RETURN LAST_QUERY_ID(n + 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(1 + 1 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 1; RETURN LAST_QUERY_ID(n + 10) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(1 AS NUMBER(9,2))) + 10 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(4,2) := 1; RETURN LAST_QUERY_ID(n + 99) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(1 AS NUMBER(4,2))) + 99 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(4,2) := 1; RETURN LAST_QUERY_ID(n + 100) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(1 AS NUMBER(4,2))) + 100 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(4,2) := 1; RETURN LAST_QUERY_ID(n + 0.5) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(1 AS NUMBER(4,2))) + 0.5 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i NUMBER(14,0) := 5; RETURN LAST_QUERY_ID(n + i) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 + (CAST(5 AS NUMBER(16,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i NUMBER(15,0) := 5; RETURN LAST_QUERY_ID(n + i) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 + (CAST(5 AS NUMBER(17,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i NUMBER(16,0) := 5; RETURN LAST_QUERY_ID(n + i) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(10,2))) + (CAST(CAST(5 AS NUMBER(18,2)) AS NUMBER(18,2))) AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i NUMBER(17,0) := 5; RETURN LAST_QUERY_ID(n + i) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(10,2))) + (CAST(5 AS NUMBER(19,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; LET i NUMBER(5,0) := 5; RETURN LAST_QUERY_ID(n + i) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(9,2))) + (CAST(CAST(5 AS NUMBER(9,2)) AS NUMBER(9,2))) AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(8,2) := 2; LET i NUMBER(5,0) := 5; RETURN LAST_QUERY_ID(n + i) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 + (CAST(5 AS NUMBER(8,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(8,2) := 2; LET i NUMBER(6,0) := 5; RETURN LAST_QUERY_ID(n + i) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 + (CAST(5 AS NUMBER(8,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; LET k NUMBER(9,2) := 3; RETURN LAST_QUERY_ID(n + k) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(9,2))) + (CAST(3 AS NUMBER(9,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(8,2) := 2; LET k NUMBER(8,2) := 3; RETURN LAST_QUERY_ID(n + k) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 + 3 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(17,2) := 2; LET k NUMBER(17,2) := 3; RETURN LAST_QUERY_ID(n + k) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 + 3 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(18,2) := 2; LET k NUMBER(5,2) := 3; RETURN LAST_QUERY_ID(n + k) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(18,2))) + (CAST(3 AS NUMBER(5,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n INT := 2; LET k INT := 3; RETURN LAST_QUERY_ID(n + k) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 45 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found '2 + "
                + "3'"},
            {"BEGIN LET n NUMBER(5,0) := 2; LET k NUMBER(5,0) := 3; RETURN LAST_QUERY_ID(n + k) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found '2 + "
                + "3'"},
            {"BEGIN LET n NUMBER(9,0) := 2; LET k NUMBER(9,0) := 3; RETURN LAST_QUERY_ID(n + k) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'(CAST(2 AS NUMBER(9,0))) + (CAST(3 AS NUMBER(9,0)))'"},
            {"BEGIN LET n NUMBER(5,0) := 2; LET k INT := 3; RETURN LAST_QUERY_ID(n + k) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 53 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'(CAST(2 AS NUMBER(5,0))) + 3'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET k NUMBER(5,2) := 3; RETURN LAST_QUERY_ID(n - k) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 - (CAST(3 AS NUMBER(5,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(5,0) := 2; RETURN LAST_QUERY_ID(n + 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found '2 + "
                + "1'"},
            {"BEGIN LET n NUMBER(9,0) := 2; RETURN LAST_QUERY_ID(n + 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'(CAST(2 AS NUMBER(9,0))) + 1'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i NUMBER(1,0) := 5; RETURN LAST_QUERY_ID(n % i) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 % (CAST(5 AS NUMBER(10,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i NUMBER(5,0) := 5; RETURN LAST_QUERY_ID(n % i) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 % (CAST(5 AS NUMBER(10,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i NUMBER(10,0) := 5; RETURN LAST_QUERY_ID(n % i) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 % (CAST(5 AS NUMBER(12,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i NUMBER(18,0) := 5; RETURN LAST_QUERY_ID(n % i) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(10,2))) % (CAST(5 AS NUMBER(20,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i NUMBER(38,0) := 5; RETURN LAST_QUERY_ID(n % i) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(10,2))) % (CAST(5 AS NUMBER(38,2))) AS NUMBER(36,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET m NUMBER(9,3) := 1; RETURN LAST_QUERY_ID(n % m) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(11,3))) % (CAST(1 AS NUMBER(9,3))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET m NUMBER(10,3) := 1; RETURN LAST_QUERY_ID(n % m) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(11,3))) % 1 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(4,2) := 2; RETURN LAST_QUERY_ID(n % 1000) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(4,2))) % 1000 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET k NUMBER(5,2) := 3; RETURN LAST_QUERY_ID(n % k) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 % (CAST(3 AS NUMBER(5,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i NUMBER(1,0) := 5; RETURN LAST_QUERY_ID((n > i)::INT) IS"
                + " NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(2 > (CAST(5 AS NUMBER(10,2))) AS BOOLEAN) AS NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i NUMBER(5,0) := 5; RETURN LAST_QUERY_ID((n > i)::INT) IS"
                + " NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(2 > (CAST(5 AS NUMBER(10,2))) AS BOOLEAN) AS NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i NUMBER(10,0) := 5; RETURN LAST_QUERY_ID((n > i)::INT) "
                + "IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(2 > (CAST(5 AS NUMBER(12,2))) AS BOOLEAN) AS NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i NUMBER(18,0) := 5; RETURN LAST_QUERY_ID((n > i)::INT) "
                + "IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST((CAST(2 AS NUMBER(10,2))) > (CAST(5 AS NUMBER(20,2))) AS BOOLEAN) AS "
                + "NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID((n > 1)::INT) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(2 > 1 AS BOOLEAN) AS NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(4,2) := 2; RETURN LAST_QUERY_ID((n > 1000)::INT) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST((CAST(2 AS NUMBER(4,2))) > 1000 AS BOOLEAN) AS NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET k NUMBER(5,2) := 3; RETURN LAST_QUERY_ID((n > k)::INT) IS"
                + " NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(2 > (CAST(3 AS NUMBER(5,2))) AS BOOLEAN) AS NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET m NUMBER(10,3) := 1; RETURN LAST_QUERY_ID((n > m)::INT) "
                + "IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST((CAST(2 AS NUMBER(11,3))) > 1 AS BOOLEAN) AS NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET m NUMBER(9,3) := 1; RETURN LAST_QUERY_ID((n > m)::INT) IS"
                + " NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST((CAST(2 AS NUMBER(11,3))) > (CAST(1 AS NUMBER(9,3))) AS BOOLEAN) AS NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(5,2) := 2; RETURN LAST_QUERY_ID(n + '1') IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(5,2))) + (CAST(1 AS NUMBER(18,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(n + '1') IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(10,2))) + (CAST(1 AS NUMBER(18,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(17,2) := 2; RETURN LAST_QUERY_ID(n + '1') IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(17,2))) + (CAST(1 AS NUMBER(18,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(38,2) := 2; RETURN LAST_QUERY_ID(n + '1') IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 + 1 AS NUMBER(36,0))'"},
            {"BEGIN LET n NUMBER(10,3) := 2; RETURN LAST_QUERY_ID(n + '1') IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(10,3))) + (CAST(1 AS NUMBER(18,3))) AS NUMBER(18,0))'"},
            {"BEGIN LET n INT := 2; RETURN LAST_QUERY_ID(n + '1') IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 29 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found '2 + "
                + "1'"},
            {"BEGIN LET n NUMBER(5,0) := 2; RETURN LAST_QUERY_ID(n + '1') IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'(CAST(2 AS NUMBER(5,0))) + (CAST(1 AS NUMBER(18,0)))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(n + '1.5') IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(10,2))) + (CAST(1.5 AS NUMBER(18,2))) AS NUMBER(18,0))'"},
            {"SELECT LAST_QUERY_ID(CAST('2' AS NUMBER(10,2)) + '1') IS NOT NULL",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, "
                + "found 'CAST(2 + 1 AS NUMBER(18,0))'"},
            {"SELECT LAST_QUERY_ID(1 + '1') IS NOT NULL",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, "
                + "found '1 + 1'"},
            {"SELECT LAST_QUERY_ID(1.5 + '1') IS NOT NULL",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, "
                + "found 'CAST(1.5 + 1 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i INT := 5; RETURN LAST_QUERY_ID(n * i) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 54 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(10,2))) * 5 AS NUMBER(36,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET k NUMBER(5,2) := 3; RETURN LAST_QUERY_ID(n * k) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 * (CAST(3 AS NUMBER(5,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i INT := 5; RETURN LAST_QUERY_ID((n + 1) + i) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 54 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 + 1 AS NUMBER(11,2))) + (CAST(5 AS NUMBER(38,2))) AS NUMBER(36,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i INT := 5; RETURN LAST_QUERY_ID(ABS(n) + i) IS NOT NULL;"
                + " END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 54 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(ABS(2) AS NUMBER(10,2))) + (CAST(5 AS NUMBER(38,2))) AS NUMBER(36,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i INT := 5; RETURN LAST_QUERY_ID(-n + i) IS NOT NULL; "
                + "END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 54 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(NEGATE(2) AS NUMBER(10,2))) + (CAST(5 AS NUMBER(38,2))) AS NUMBER(36,0))'"},
        });
    }

    @Test
    public void operatorsAndTexts() {
        assertCells(new String[][] {
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID((n > 1)::INT) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(2 > 1 AS BOOLEAN) AS NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; LET k NUMBER(9,2) := 3; RETURN LAST_QUERY_ID((n > k)::INT) IS "
                + "NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(2 > 3 AS BOOLEAN) AS NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; LET i NUMBER(5,0) := 5; RETURN LAST_QUERY_ID((n > i)::INT) IS "
                + "NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(2 > (CAST(5 AS NUMBER(9,2))) AS BOOLEAN) AS NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(4,2) := 2; RETURN LAST_QUERY_ID((n > 99)::INT) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(2 > 99 AS BOOLEAN) AS NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID((n = 1)::INT) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(FALSE AS BOOLEAN) AS NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(n % 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 % 1 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; LET k NUMBER(9,2) := 3; RETURN LAST_QUERY_ID(n % k) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 % 3 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(4,2) := 2; RETURN LAST_QUERY_ID(n % 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 % 1 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(4,2) := 2; RETURN LAST_QUERY_ID(n % 99) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 % 99 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(n * 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(CAST(2 AS NUMBER(9,2)) AS NUMBER(10,2)) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(4,2) := 2; RETURN LAST_QUERY_ID(n * 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(CAST(2 AS NUMBER(4,2)) AS NUMBER(5,2)) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(3,2) := 2; RETURN LAST_QUERY_ID(n * 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(2 AS NUMBER(4,2)) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(2,1) := 2; RETURN LAST_QUERY_ID(n * 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(CAST(2 AS NUMBER(2,1)) AS NUMBER(3,1)) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(5,2) := 2; LET k NUMBER(5,2) := 3; RETURN LAST_QUERY_ID(n * k) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(5,2))) * (CAST(3 AS NUMBER(5,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(2,1) := 2; LET k NUMBER(2,1) := 3; RETURN LAST_QUERY_ID(n * k) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(2,1))) * (CAST(3 AS NUMBER(2,1))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(n - 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(9,2))) - 1 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(1 - n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(1 - (CAST(2 AS NUMBER(9,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(18,2) := 2; RETURN LAST_QUERY_ID(n + '1') IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(18,2))) + (CAST(1 AS NUMBER(18,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(19,2) := 2; RETURN LAST_QUERY_ID(n + '1') IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 + 1 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(20,2) := 2; RETURN LAST_QUERY_ID(n + '1') IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 + 1 AS NUMBER(19,0))'"},
            {"BEGIN LET n NUMBER(30,2) := 2; RETURN LAST_QUERY_ID(n + '1') IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 + 1 AS NUMBER(29,0))'"},
            {"BEGIN LET n NUMBER(38,0) := 2; RETURN LAST_QUERY_ID(n + '1') IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found '2 + "
                + "1'"},
            {"BEGIN LET n NUMBER(20,0) := 2; RETURN LAST_QUERY_ID(n + '1') IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found '2 + "
                + "1'"},
            {"BEGIN LET n NUMBER(1,0) := 2; RETURN LAST_QUERY_ID(n + '1') IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'(CAST(2 AS NUMBER(1,0))) + (CAST(1 AS NUMBER(18,0)))'"},
            {"BEGIN LET n NUMBER(9,5) := 2; RETURN LAST_QUERY_ID(n + '1') IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(9,5))) + (CAST(1 AS NUMBER(18,5))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(30,20) := 2; RETURN LAST_QUERY_ID(n + '1') IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 39 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 + 1 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(n * '1') IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(10,2))) * (CAST(1 AS NUMBER(18,0))) AS NUMBER(26,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; LET i NUMBER(5,0) := 5; RETURN LAST_QUERY_ID((i + 1) + n) IS "
                + "NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(CAST(5 + 1 AS NUMBER(9,2)) AS NUMBER(9,2))) + (CAST(2 AS NUMBER(9,2))) AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; LET i NUMBER(5,0) := 5; RETURN LAST_QUERY_ID(-i + n) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(CAST(NEGATE(5) AS NUMBER(9,2)) AS NUMBER(9,2))) + (CAST(2 AS NUMBER(9,2))) AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(ABS(n) + 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(ABS(2) AS NUMBER(9,2))) + 1 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(n + 1 + 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(((CAST(2 AS NUMBER(9,2))) + 1) + 1 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(8,2) := 2; RETURN LAST_QUERY_ID(n + 1 + 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 + 1 AS NUMBER(9,2))) + 1 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(n + 1000000000) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 + 1000000000 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(n + 10000000000000000) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(10,2))) + 10000000000000000 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(n + 1.125) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(10,3))) + 1.125 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(4,2) := 2; RETURN LAST_QUERY_ID(n + 1.125) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(5,3))) + 1.125 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(n + 1::NUMBER(4,1)) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(9,2))) + 1 AS NUMBER(18,0))'"},
            {"BEGIN LET i NUMBER(5,0) := 5; RETURN LAST_QUERY_ID(i + 1.25) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(5 AS NUMBER(7,2))) + 1.25 AS NUMBER(18,0))'"},
            {"BEGIN LET i NUMBER(9,0) := 5; RETURN LAST_QUERY_ID(i + 1.25) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(5 AS NUMBER(11,2))) + 1.25 AS NUMBER(18,0))'"},
            {"BEGIN LET i NUMBER(8,0) := 5; RETURN LAST_QUERY_ID(i + 1.25) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(5 AS NUMBER(10,2))) + 1.25 AS NUMBER(18,0))'"},
            {"BEGIN LET i NUMBER(2,0) := 5; RETURN LAST_QUERY_ID(i + 1.5) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(5 AS NUMBER(3,1))) + 1.5 AS NUMBER(18,0))'"},
        });
    }

    @Test
    public void castsAndEqualities() {
        assertCells(new String[][] {
            {"BEGIN LET i NUMBER(7,0) := 5; RETURN LAST_QUERY_ID(i + 1.25) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(CAST(5 AS NUMBER(9,2)) AS NUMBER(9,2))) + 1.25 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(n + TO_NUMBER('1', 5, 0)) IS NOT NULL; "
                + "END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(9,2))) + (CAST(CAST(1 AS NUMBER(9,2)) AS NUMBER(9,2))) AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,0) := 2; RETURN LAST_QUERY_ID(n + TO_NUMBER('1', 5, 2)) IS NOT NULL; "
                + "END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(11,2))) + (CAST(1 AS NUMBER(5,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(n::NUMBER(20,2)) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(2 AS NUMBER(20,2)) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(n::NUMBER(12,2)) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(2 AS NUMBER(12,2)) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(4,2) := 2; RETURN LAST_QUERY_ID(n::NUMBER(10,2)) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(2 AS NUMBER(10,2)) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(n::INT) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 AS NUMBER(38,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; LET k NUMBER(9,2) := 3; RETURN LAST_QUERY_ID((n = k)::INT) IS "
                + "NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(FALSE AS BOOLEAN) AS NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID((n = 2)::INT) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(TRUE AS BOOLEAN) AS NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID((n <> 1)::INT) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(2 <> 1 AS BOOLEAN) AS NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i INT := 5; RETURN LAST_QUERY_ID((n = i)::INT) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 54 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST((CAST(2 AS NUMBER(10,2))) = (CAST(5 AS NUMBER(38,2))) AS BOOLEAN) AS "
                + "NUMBER(2,0))'"},
            {"SELECT LAST_QUERY_ID((1 = 2)::INT) IS NOT NULL",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, "
                + "found 'CAST(CAST(1 = 2 AS BOOLEAN) AS NUMBER(2,0))'"},
            {"SELECT LAST_QUERY_ID((1 > 2)::INT) IS NOT NULL",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, "
                + "found 'CAST(CAST(1 > 2 AS BOOLEAN) AS NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i INT := 5; RETURN LAST_QUERY_ID((n < i)::INT) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 54 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST((CAST(2 AS NUMBER(10,2))) < (CAST(5 AS NUMBER(38,2))) AS BOOLEAN) AS "
                + "NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(4,2) := 2; RETURN LAST_QUERY_ID((n >= 1000)::INT) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST((CAST(2 AS NUMBER(4,2))) >= 1000 AS BOOLEAN) AS NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; LET i NUMBER(5,0) := 5; RETURN LAST_QUERY_ID(n % i) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 % (CAST(5 AS NUMBER(9,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; LET i NUMBER(5,0) := 5; RETURN LAST_QUERY_ID(n * i) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(9,2))) * (CAST(5 AS NUMBER(5,0))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(n * 2) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(9,2))) * 2 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; LET i NUMBER(5,0) := 5; RETURN LAST_QUERY_ID(i - n) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(CAST(5 AS NUMBER(9,2)) AS NUMBER(9,2))) - (CAST(2 AS NUMBER(9,2))) AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(4,2) := 2; RETURN LAST_QUERY_ID('1' + n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(1 AS NUMBER(18,2))) + (CAST(2 AS NUMBER(4,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET t VARCHAR := 1; RETURN LAST_QUERY_ID(n + t) IS NOT NULL; "
                + "END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 58 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(10,2))) + (CAST(1 AS NUMBER(18,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; LET k NUMBER(9,2) := 3; LET i INT := 5; RETURN LAST_QUERY_ID(n"
                + " + k + i) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 77 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST((CAST(2 AS NUMBER(9,2))) + (CAST(3 AS NUMBER(9,2))) AS NUMBER(10,2))) + (CAST(5 "
                + "AS NUMBER(38,2))) AS NUMBER(36,0))'"},
            {"SELECT LAST_QUERY_ID(2 * '1') IS NOT NULL",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, "
                + "found 'CAST(2 AS NUMBER(19,0))'"},
            {"SELECT LAST_QUERY_ID(CAST('2' AS NUMBER(10,2)) * '1') IS NOT NULL",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, "
                + "found 'CAST(CAST(2 AS NUMBER(28,2)) AS NUMBER(26,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(n - '1') IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(10,2))) - (CAST(1 AS NUMBER(18,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(n % '1') IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 % 1 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(1 * n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(CAST(2 AS NUMBER(9,2)) AS NUMBER(10,2)) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(n * 0) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(0 AS NUMBER(10,2)) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; LET i NUMBER(20,0) := 5; RETURN LAST_QUERY_ID((n <= i)::INT) "
                + "IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST((CAST(2 AS NUMBER(9,2))) <= (CAST(5 AS NUMBER(22,2))) AS BOOLEAN) AS "
                + "NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; LET k NUMBER(9,2) := 3; RETURN LAST_QUERY_ID((n <> k)::INT) IS"
                + " NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(2 <> 3 AS BOOLEAN) AS NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET m NUMBER(10,3) := 1; RETURN LAST_QUERY_ID((n = m)::INT) "
                + "IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST((CAST(2 AS NUMBER(11,3))) = 1 AS BOOLEAN) AS NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(4,2) := 2; RETURN LAST_QUERY_ID((n = 1000)::INT) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST((CAST(2 AS NUMBER(4,2))) = 1000 AS BOOLEAN) AS NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(20,2) := 2; LET m NUMBER(20,3) := 1; RETURN LAST_QUERY_ID(n * m) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(2 AS NUMBER(38,5)) AS NUMBER(33,0))'"},
            {"BEGIN LET n NUMBER(30,5) := 2; LET i NUMBER(3,0) := 5; RETURN LAST_QUERY_ID(n - i) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 - (CAST(5 AS NUMBER(30,5))) AS NUMBER(26,0))'"},
            {"BEGIN LET n NUMBER(16,2) := 2; LET i NUMBER(17,0) := 5; RETURN LAST_QUERY_ID(n % i) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(16,2))) % (CAST(5 AS NUMBER(19,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := -1.5; RETURN LAST_QUERY_ID(n + 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 40 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(-1.5 AS NUMBER(9,2))) + 1 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(3,1) := 2; RETURN LAST_QUERY_ID(n + 1 + 1 + 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(((CAST(2 + 1 AS NUMBER(4,1))) + 1) + 1 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; LET i NUMBER(5,0) := 5; RETURN LAST_QUERY_ID(ABS(n) - i) IS "
                + "NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(ABS(2) AS NUMBER(9,2))) - (CAST(CAST(5 AS NUMBER(9,2)) AS NUMBER(9,2))) AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(TO_NUMBER('1', 5, 0) + TO_NUMBER('2', 5, "
                + "0) + n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(CAST(1 + 2 AS NUMBER(9,2)) AS NUMBER(9,2))) + (CAST(2 AS NUMBER(9,2))) AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,0) := 2; RETURN LAST_QUERY_ID(TO_NUMBER('1', 5, 0) + n) IS NOT NULL; "
                + "END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'(CAST(1 AS NUMBER(5,0))) + (CAST(2 AS NUMBER(9,0)))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(TO_NUMBER('3', 5, 0) * n) IS NOT NULL; "
                + "END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(3 AS NUMBER(5,0))) * (CAST(2 AS NUMBER(9,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID((TO_NUMBER('3', 5, 0) > n)::INT) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST((CAST(3 AS NUMBER(9,2))) > 2 AS BOOLEAN) AS NUMBER(2,0))'"},
        });
    }
}
