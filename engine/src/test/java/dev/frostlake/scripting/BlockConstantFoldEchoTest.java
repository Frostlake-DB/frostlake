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
 * LAST_QUERY_ID refuses an index over a block's exact NUMBER variable with 1015 / 22023 and echoes it as the plan types
 * its constants (live-verified): a quotient computes in the storage of the wider of the rescaled dividend, the divisor
 * and the quotient, a variable keeping the cast to its own type where stored narrower, and a dividend written out is not
 * rescaled; arithmetic over numbers written out beside the variable - and ABS, FLOOR, MOD, GREATEST and an exact
 * quotient - folds to its value, printed as written; a text compared with the variable reads as the number it spells; a
 * text term raises a lower-scale variable from its own spelled width; an IFF, a CASE with an ELSE, NVL, IFNULL or
 * COALESCE whose choice is a constant reading no variable is the branch it picks.
 */
public class BlockConstantFoldEchoTest extends MeasuredCellsTestSupport {

    @Test
    public void quotientsTextsAndFolds() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, c NUMBER(10,2))");
        assertCells(new String[][] {
            {"SELECT LAST_QUERY_ID(SEQ4()) FROM TABLE(GENERATOR(ROWCOUNT=>1))",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, "
                + "found 'SEQ4()'"},
            {"SELECT LAST_QUERY_ID(a) FROM t",
                "REFUSED SQL compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, "
                + "found 'T.A'"},
            {"SELECT SYSTEM$WAIT(a) FROM t",
                "REFUSED SQL compilation error: | argument 0 to function SqlIdentifier{qualifierNames=[], "
                + "identifierName=SYSTEM$WAIT} needs to be constant, found 'T.A'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i INT := 5; RETURN LAST_QUERY_ID(n / i) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 54 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(CAST(2 AS NUMBER(16,8)) AS NUMBER(16,8))) / 5 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i INT := 5; RETURN LAST_QUERY_ID(i / n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 54 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(SCALED_ROUND_INT_DIVIDE(5, CAST(2 AS NUMBER(10,2))) AS NUMBER(32,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(n / 5) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(16,8))) / 5 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(5 / n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(5 / 2 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(n / 5) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(15,8))) / 5 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(4,2) := 2; RETURN LAST_QUERY_ID(n / 5) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(10,8))) / 5 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(2,1) := 2; RETURN LAST_QUERY_ID(n / 5) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(8,7))) / 5 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(18,2) := 2; RETURN LAST_QUERY_ID(n / 5) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(24,8))) / 5 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(20,2) := 2; RETURN LAST_QUERY_ID(n / 5) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(26,8))) / 5 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(30,5) := 2; RETURN LAST_QUERY_ID(n / 5) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(36,11))) / 5 AS NUMBER(25,0))'"},
            {"BEGIN LET n NUMBER(10,0) := 2; RETURN LAST_QUERY_ID(n / 5) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(16,6))) / 5 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(5,0) := 2; RETURN LAST_QUERY_ID(n / 5) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(11,6))) / 5 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(5,0) := 2; LET i INT := 5; RETURN LAST_QUERY_ID(n / i) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 53 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(CAST(2 AS NUMBER(11,6)) AS NUMBER(11,6))) / 5 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET m NUMBER(4,3) := 1; RETURN LAST_QUERY_ID(n / m) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(22,11))) / (CAST(1 AS NUMBER(4,3))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET m NUMBER(12,3) := 1; RETURN LAST_QUERY_ID(n / m) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(22,11))) / (CAST(1 AS NUMBER(12,3))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(n / 1.5) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(18,9))) / 1.5 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(n / '5') IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(16,8))) / 5 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i NUMBER(5,0) := 5; RETURN LAST_QUERY_ID(n / i) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(16,8))) / (CAST(5 AS NUMBER(5,0))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i NUMBER(12,0) := 5; RETURN LAST_QUERY_ID(n / i) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(16,8))) / 5 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i INT := 5; RETURN LAST_QUERY_ID(n * i) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 54 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(10,2))) * 5 AS NUMBER(36,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID((n > '1')::INT) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(2 > 1 AS BOOLEAN) AS NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(('1' < n)::INT) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(1 < 2 AS BOOLEAN) AS NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID((n > '1.5')::INT) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(2 > 1.5 AS BOOLEAN) AS NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID((n = '1')::INT) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(FALSE AS BOOLEAN) AS NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(10,0) := 2; RETURN LAST_QUERY_ID((n > '1')::INT) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(2 > 1 AS BOOLEAN) AS NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID((n > '1')::INT) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(2 > 1 AS BOOLEAN) AS NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(20,2) := 2; RETURN LAST_QUERY_ID((n > '1')::INT) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(2 > 1 AS BOOLEAN) AS NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID((n > '1.234')::INT) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST((CAST(2 AS NUMBER(11,3))) > 1.234 AS BOOLEAN) AS NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(10,0) := 2; RETURN LAST_QUERY_ID(n + '1.5') IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(CAST(2 AS NUMBER(11,1)) AS NUMBER(11,1))) + (CAST(1.5 AS NUMBER(18,1))) AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,0) := 2; RETURN LAST_QUERY_ID(n + '1') IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'(CAST(2 AS NUMBER(10,0))) + (CAST(1 AS NUMBER(18,0)))'"},
            {"BEGIN LET n NUMBER(10,0) := 2; RETURN LAST_QUERY_ID(n + '1.25') IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(CAST(2 AS NUMBER(12,2)) AS NUMBER(12,2))) + (CAST(1.25 AS NUMBER(18,2))) AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,0) := 2; RETURN LAST_QUERY_ID(n + '1.2345') IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(CAST(2 AS NUMBER(14,4)) AS NUMBER(14,4))) + (CAST(1.2345 AS NUMBER(18,4))) AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,0) := 2; RETURN LAST_QUERY_ID(n - '1.5') IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(CAST(2 AS NUMBER(11,1)) AS NUMBER(11,1))) - (CAST(1.5 AS NUMBER(18,1))) AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,0) := 2; RETURN LAST_QUERY_ID('1.5' + n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(1.5 AS NUMBER(18,1))) + (CAST(CAST(2 AS NUMBER(11,1)) AS NUMBER(11,1))) AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(5,0) := 2; RETURN LAST_QUERY_ID(n + '1.5') IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(CAST(2 AS NUMBER(6,1)) AS NUMBER(6,1))) + (CAST(1.5 AS NUMBER(18,1))) AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,0) := 2; RETURN LAST_QUERY_ID(n + '1.5') IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(CAST(2 AS NUMBER(10,1)) AS NUMBER(10,1))) + (CAST(1.5 AS NUMBER(18,1))) AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(20,0) := 2; RETURN LAST_QUERY_ID(n + '1.5') IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(21,1))) + 1.5 AS NUMBER(21,0))'"},
            {"BEGIN LET n NUMBER(10,1) := 2; RETURN LAST_QUERY_ID(n + '1.25') IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(CAST(2 AS NUMBER(11,2)) AS NUMBER(11,2))) + (CAST(1.25 AS NUMBER(18,2))) AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(n + '1.234') IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(CAST(2 AS NUMBER(11,3)) AS NUMBER(11,3))) + (CAST(1.234 AS NUMBER(18,3))) AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,0) := 2; RETURN LAST_QUERY_ID(n * '1.5') IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(10,0))) * (CAST(1.5 AS NUMBER(18,1))) AS NUMBER(27,0))'"},
            {"BEGIN LET n NUMBER(10,0) := 2; RETURN LAST_QUERY_ID(n % '1.5') IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(11,1))) % 1.5 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,0) := 2; RETURN LAST_QUERY_ID((n > '1.5')::INT) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST((CAST(2 AS NUMBER(11,1))) > 1.5 AS BOOLEAN) AS NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(IFF(TRUE, n, 0) + 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(9,2))) + 1 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(IFF(FALSE, 0, n) + 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(9,2))) + 1 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(IFF(1 = 1, n, 0) + 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(9,2))) + 1 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(IFF(TRUE, n, 0)) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(CASE WHEN TRUE THEN n ELSE 0 END + 1) IS "
                + "NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(9,2))) + 1 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(CASE WHEN FALSE THEN 0 ELSE n END + 1) IS"
                + " NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(9,2))) + 1 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(COALESCE(n, 0) + 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(9,2))) + 1 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(NVL(n, 0) + 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(9,2))) + 1 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID((1 + 2) + n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(3 + (CAST(2 AS NUMBER(9,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(n + (1 + 2)) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(9,2))) + 3 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(ABS(1) + n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(1 + (CAST(2 AS NUMBER(9,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(ABS(-3) + n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(3 + (CAST(2 AS NUMBER(9,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID((2 * 3) + n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(6 + (CAST(2 AS NUMBER(9,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(((1 + 2) * 3) + n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(9 + (CAST(2 AS NUMBER(9,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID((1.5 + 2) + n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(3.5 + (CAST(2 AS NUMBER(9,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID((6 / 3) + n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 + (CAST(2 AS NUMBER(13,6))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(ROUND(1.26, 1) + n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(CAST(ROUND(1.26, 1) AS NUMBER(9,2)) AS NUMBER(9,2))) + (CAST(2 AS NUMBER(9,2))) "
                + "AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(LENGTH('abc') + n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(LENGTH('abc') AS NUMBER(20,2))) + (CAST(2 AS NUMBER(9,2))) AS NUMBER(19,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(-(1 + 2) + n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(-3 + (CAST(2 AS NUMBER(9,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID((100 + 200) + n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(300 + (CAST(2 AS NUMBER(9,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(20,2) := 2; RETURN LAST_QUERY_ID((1 + 2) + n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(3 + 2 AS NUMBER(19,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(TO_NUMBER('1', 5, 0) + TO_NUMBER('2', 5, "
                + "0) + n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(CAST(1 + 2 AS NUMBER(9,2)) AS NUMBER(9,2))) + (CAST(2 AS NUMBER(9,2))) AS "
                + "NUMBER(18,0))'"},
        });
    }

    @Test
    public void foldingCalls() {
        assertCells(new String[][] {
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(SIGN(5) + n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(CAST(SIGN(5) AS NUMBER(9,2)) AS NUMBER(9,2))) + (CAST(2 AS NUMBER(9,2))) AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(FLOOR(1.5) + n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(1 + (CAST(2 AS NUMBER(9,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(CEIL(1.5) + n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(CAST(CEIL(1.5) AS NUMBER(9,2)) AS NUMBER(9,2))) + (CAST(2 AS NUMBER(9,2))) AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(ROUND(1.5) + n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(CAST(ROUND(1.5) AS NUMBER(9,2)) AS NUMBER(9,2))) + (CAST(2 AS NUMBER(9,2))) AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(ROUND(1.26, 1) + n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(CAST(ROUND(1.26, 1) AS NUMBER(9,2)) AS NUMBER(9,2))) + (CAST(2 AS NUMBER(9,2))) "
                + "AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(TRUNC(1.5) + n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(CAST(TRUNC(1.5) AS NUMBER(9,2)) AS NUMBER(9,2))) + (CAST(2 AS NUMBER(9,2))) AS "
                + "NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(MOD(5, 3) + n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 + (CAST(2 AS NUMBER(9,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID((5 % 3) + n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 + (CAST(2 AS NUMBER(9,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(GREATEST(1, 2) + n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 + (CAST(2 AS NUMBER(9,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(ABS(1.5) + n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(1.5 + (CAST(2 AS NUMBER(9,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(ABS(-1.5) + n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(1.5 + (CAST(2 AS NUMBER(9,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID((1 - 2) + n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(-1 + (CAST(2 AS NUMBER(9,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(ABS(1 + 2) + n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(3 + (CAST(2 AS NUMBER(9,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(ABS(-(1 + 2)) + n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(3 + (CAST(2 AS NUMBER(9,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID((1 + 2) * n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(3 * (CAST(2 AS NUMBER(9,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(((1 + 2) > n)::INT) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(3 > 2 AS BOOLEAN) AS NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(n / (1 + 2)) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(15,8))) / 3 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID((1 + 2) / n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(3 / (CAST(2 AS NUMBER(9,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID((99999999999 * 99999999999) + n) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(9999999999800000000001 + (CAST(2 AS NUMBER(9,2))) AS NUMBER(23,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID((0 * 5) + n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(0 + (CAST(2 AS NUMBER(9,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID((1 * 5) + n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(5 + (CAST(2 AS NUMBER(9,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID((n > '1.50')::INT) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(2 > 1.5 AS BOOLEAN) AS NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID((n > ' 1 ')::INT) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST((CAST(2 AS NUMBER(18,5))) > 1 AS BOOLEAN) AS NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID((n > '-1')::INT) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(2 > -1 AS BOOLEAN) AS NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID((n > '1e2')::INT) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(2 > 100 AS BOOLEAN) AS NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID((n > '12345678901')::INT) IS NOT NULL; "
                + "END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST((CAST(2 AS NUMBER(9,2))) > 12345678901 AS BOOLEAN) AS NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(IFF(TRUE, n, 0) * 2) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(9,2))) * 2 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(IFF(TRUE, 1, n) + n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(1 + (CAST(2 AS NUMBER(9,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(IFF(NULL, 0, n) + 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(9,2))) + 1 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(CASE WHEN 1 = 1 THEN n ELSE 0 END + 1) IS"
                + " NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(9,2))) + 1 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(CASE WHEN TRUE THEN n ELSE 0 END) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(COALESCE(n, 0)) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(COALESCE(n, 0, 1) + 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(9,2))) + 1 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(IFNULL(n, 0) + 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(9,2))) + 1 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(IFF(TRUE, IFF(FALSE, 0, n), 0) + 1) IS "
                + "NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(9,2))) + 1 AS NUMBER(18,0))'"},
        });
    }

    @Test
    public void neighbours() {
        assertCells(new String[][] {
            {"BEGIN LET n NUMBER(20,5) := 2; LET i NUMBER(3,0) := 5; RETURN LAST_QUERY_ID(n / i) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(26,11))) / (CAST(5 AS NUMBER(3,0))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(4,1) := 2; LET i NUMBER(3,0) := 5; RETURN LAST_QUERY_ID(n / i) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(10,7))) / (CAST(5 AS NUMBER(3,0))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i NUMBER(5,0) := 5; RETURN LAST_QUERY_ID(i / n) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 62 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(5 AS NUMBER(15,8))) / 2 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(1.5 / n) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(1.5 / 2 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET m NUMBER(20,4) := 3; RETURN LAST_QUERY_ID(n / m) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(24,12))) / 3 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(3,0) := 2; LET m NUMBER(3,0) := 3; RETURN LAST_QUERY_ID(n / m) IS NOT "
                + "NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(9,6))) / (CAST(3 AS NUMBER(3,0))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(30,2) := 2; RETURN LAST_QUERY_ID(n / 7) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(36,8))) / 7 AS NUMBER(28,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID(n / -5) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(16,8))) / -5 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID((n + 1) / 5) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 + 1 AS NUMBER(17,8))) / 5 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(10,2) := 2; LET i INT := 5; RETURN LAST_QUERY_ID(i / 5 + n) IS NOT NULL; "
                + "END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 54 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((SCALED_ROUND_INT_DIVIDE(5, 5)) + (CAST(2 AS NUMBER(38,6))) AS NUMBER(32,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(CASE WHEN FALSE THEN 0 WHEN NULL THEN 1 "
                + "ELSE n END + 1) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(9,2))) + 1 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(IFF(FALSE, n, 1 + 2) + n) IS NOT NULL; "
                + "END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(3 + (CAST(2 AS NUMBER(9,2))) AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(n - (2 * 3)) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(9,2))) - 6 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(n % (2 + 3)) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(2 % 5 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID((n <= (2 + 3))::INT) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(2 <= 5 AS BOOLEAN) AS NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(n + GREATEST(1.5, 2)) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(9,2))) + 2 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(n + MOD(7, 4) * 2) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(9,2))) + 6 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(n * (1.25 + 1)) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(9,2))) * 2.25 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(n + (1.234 * 2)) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(10,3))) + 2.468 AS NUMBER(18,0))'"},
            {"BEGIN LET n NUMBER(38,0) := 2; RETURN LAST_QUERY_ID((n > '1.5')::INT) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST((CAST(2 AS NUMBER(38,1))) > 1.5 AS BOOLEAN) AS NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID(('0.5' >= n)::INT) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(0.5 >= 2 AS BOOLEAN) AS NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(9,2) := 2; RETURN LAST_QUERY_ID((n <> '2.000')::INT) IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(CAST(2 <> 2 AS BOOLEAN) AS NUMBER(2,0))'"},
            {"BEGIN LET n NUMBER(30,0) := 2; RETURN LAST_QUERY_ID(n + '1.5') IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(2 AS NUMBER(31,1))) + 1.5 AS NUMBER(31,0))'"},
            {"BEGIN LET n NUMBER(3,0) := 2; RETURN LAST_QUERY_ID(n - '0.25') IS NOT NULL; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL "
                + "compilation error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST((CAST(CAST(2 AS NUMBER(5,2)) AS NUMBER(5,2))) - (CAST(0.25 AS NUMBER(18,2))) AS "
                + "NUMBER(18,0))'"},
        });
    }

    @Test
    public void handlerCodes() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, c NUMBER(10,2))");
        assertCells(new String[][] {
            {"BEGIN LET n NUMBER(10,2) := 2; LET i INT := 5; RETURN LAST_QUERY_ID(n / i) IS NOT NULL; "
                + "EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE; END;",
                "1015 22023"},
            {"BEGIN LET n NUMBER(10,2) := 2; RETURN LAST_QUERY_ID((n > '1')::INT) IS NOT NULL; EXCEPTION "
                + "WHEN EXPRESSION_ERROR THEN RETURN SQLCODE || ' ' || SQLSTATE; END;",
                "1015 22023"},
            {"DECLARE r VARCHAR; BEGIN SELECT LAST_QUERY_ID(a) INTO :r FROM t; RETURN r; EXCEPTION WHEN "
                + "OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE; END;",
                "1015 22023"},
            {"BEGIN EXECUTE IMMEDIATE 'SELECT LAST_QUERY_ID(SEQ4()) FROM TABLE(GENERATOR(ROWCOUNT=>1))'; "
                + "RETURN 'ok'; EXCEPTION WHEN STATEMENT_ERROR THEN RETURN SQLCODE || ' ' || SQLSTATE; END;",
                "1015 22023"},
        });
    }
}
