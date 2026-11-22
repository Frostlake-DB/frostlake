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

package dev.frostlake.functions;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * The bitwise aggregates BITAND_AGG, BITOR_AGG and BITXOR_AGG over what they take and refuse: a BINARY, an OBJECT,
 * an ARRAY and a VECTOR are refused by their type as the statement compiles, under every spelling and however many
 * rows reach the call; a VARIANT is read as the number it holds, and one holding a binary, a date, a time or a text
 * that spells no number is refused as its cast to FIXED. Every cell is live-verified.
 */
public class BitwiseAggregateOperandsTest extends MeasuredCellsTestSupport {

    @Test
    public void operandTypes() {
        engine.execute("CREATE OR REPLACE TABLE bt (id INT, b2 BINARY(2), bv BINARY, v VARIANT, o OBJECT, a ARRAY, t"
            + " VARCHAR, i INT)");
        engine.execute("INSERT INTO bt SELECT 1, X'1010', X'BCBC', TO_VARIANT(X'FF'), OBJECT_CONSTRUCT('a',1), "
            + "ARRAY_CONSTRUCT(1), '12', 5 UNION ALL SELECT 2, X'BCBC', X'EE', TO_VARIANT(3), "
            + "OBJECT_CONSTRUCT('a',1), ARRAY_CONSTRUCT(1), '7', 3");
        assertCells(new String[][] {
            {"SELECT BITAND_AGG(bv) FROM bt",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'BITAND_AGG': (BINARY(8388608))"},
            {"SELECT BITAND_AGG(X'FF') FROM bt",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'BITAND_AGG': (BINARY(1))"},
            {"SELECT BITXOR_AGG(TO_BINARY('FF')) FROM bt",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'BITXOR_AGG': (BINARY(67108864))"},
            {"SELECT BITAND_AGG(NULL::BINARY) FROM bt",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'BITAND_AGG': (BINARY(67108864))"},
            {"SELECT BITANDAGG(b2) FROM bt",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'BITANDAGG': (BINARY(2))"},
            {"SELECT BIT_AND_AGG(b2) FROM bt",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'BIT_AND_AGG': (BINARY(2))"},
            {"SELECT BIT_ORAGG(b2) FROM bt",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'BIT_ORAGG': (BINARY(2))"},
            {"SELECT BIT_XOR_AGG(b2) FROM bt",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'BIT_XOR_AGG': (BINARY(2))"},
            {"SELECT BITAND_AGG(DISTINCT b2) FROM bt",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'BITAND_AGG': (BINARY(2))"},
            {"SELECT id, BITOR_AGG(b2) FROM bt GROUP BY id",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for "
                + "function 'BITOR_AGG': (BINARY(2))"},
            {"SELECT BITOR_AGG(b2) OVER (ORDER BY id) FROM bt",
                "REFUSED SQL compilation error: error line 1 at position 21 | Cumulative window frame "
                + "unsupported for function BITOR_AGG"},
            {"SELECT BITOR_AGG(b2) OVER (PARTITION BY id) FROM bt WHERE FALSE",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'BITOR_AGG': (BINARY(2))"},
            {"SELECT 1 FROM bt HAVING BITAND_AGG(b2) IS NULL",
                "REFUSED SQL compilation error: error line 1 at position 24 | Invalid argument types for "
                + "function 'BITAND_AGG': (BINARY(2))"},
            {"SELECT BITAND_AGG(v) FROM bt",
                "REFUSED Failed to cast variant value \"FF\" to FIXED"},
            {"SELECT BITAND_AGG(o) FROM bt",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'BITAND_AGG': (OBJECT)"},
            {"SELECT BITAND_AGG(a) FROM bt",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'BITAND_AGG': (ARRAY)"},
            {"SELECT BITAND_AGG(t) FROM bt",
                "4"},
            {"SELECT BITAND_AGG(i), BITAND_AGG(b2) FROM bt",
                "REFUSED SQL compilation error: error line 1 at position 22 | Invalid argument types for "
                + "function 'BITAND_AGG': (BINARY(2))"},
            {"SELECT  bitand_agg( b2 ) FROM bt",
                "REFUSED SQL compilation error: error line 1 at position 8 | Invalid argument types for "
                + "function 'BITAND_AGG': (BINARY(2))"},
            {"SELECT BITAND_AGG(b2) FROM bt WHERE FALSE",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'BITAND_AGG': (BINARY(2))"},
            {"SELECT BITAND_AGG([1,2]::VECTOR(INT,2)) FROM bt",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'BITAND_AGG': (VECTOR(INT, 2))"},
            {"SELECT BITAND_AGG(IFF(TRUE, X'FF', NULL)) FROM bt",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'BITAND_AGG': (BINARY(1))"},
            {"SELECT BITAND_AGG(b2) FROM bt WHERE id = 1",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'BITAND_AGG': (BINARY(2))"},
            {"SELECT BITOR_AGG(b2) WITHIN GROUP (ORDER BY id) FROM bt",
                "REFUSED SQL compilation error: | Function BITOR_AGG does not support WITHIN GROUP clause."},
        });
    }

    @Test
    public void variantMembers() {
        assertCells(new String[][] {
            {"SELECT BITAND_AGG(TO_VARIANT(X'FF'))",
                "REFUSED Failed to cast variant value \"FF\" to FIXED"},
            {"SELECT BITOR_AGG(TO_VARIANT('abc'))",
                "REFUSED Failed to cast variant value \"abc\" to FIXED"},
            {"SELECT BITXOR_AGG(TO_VARIANT('12'))",
                "12"},
            {"SELECT BITOR_AGG(TO_VARIANT(TRUE))",
                "1"},
            {"SELECT BITOR_AGG(PARSE_JSON('{\"a\":1}'))",
                "REFUSED Failed to cast variant value {\"a\":1} to FIXED"},
            {"SELECT BITOR_AGG(TO_VARIANT(4.5))",
                "5"},
            {"SELECT BITOR_AGG(TO_VARIANT('2020-01-01'::DATE))",
                "REFUSED Failed to cast variant value \"2020-01-01\" to FIXED"},
            {"SELECT BITOR_AGG(PARSE_JSON('[1]'))",
                "REFUSED Failed to cast variant value [1] to FIXED"},
            {"SELECT BITOR_AGG(PARSE_JSON('\"x\"'))",
                "REFUSED Failed to cast variant value \"x\" to FIXED"},
            {"SELECT BITOR_AGG(PARSE_JSON('1e2'))",
                "100"},
            {"SELECT BITOR_AGG(v) FROM (SELECT TO_VARIANT(X'0F') v UNION ALL SELECT TO_VARIANT(3))",
                "REFUSED Failed to cast variant value \"0F\" to FIXED"},
            {"SELECT BITOR_AGG(TO_VARIANT(X'FF')) OVER ()",
                "REFUSED Failed to cast variant value \"FF\" to FIXED"},
            {"SELECT BITOR_AGG(PARSE_JSON('null'))",
                "null"},
            {"SELECT BITOR_AGG('abc')",
                "REFUSED Numeric value 'abc' is not recognized"},
            {"SELECT BITOR_AGG(TO_VARIANT(X'FF')) FROM (SELECT 1) WHERE FALSE",
                "null"},
            {"SELECT BITOR_AGG(TO_VARIANT('2020-01-01 10:11:12.5'::TIMESTAMP_NTZ))",
                "REFUSED Failed to cast variant value \"2020-01-01 10:11:12.500\" to FIXED"},
            {"SELECT BITOR_AGG(TO_VARIANT('10:11:12'::TIME))",
                "REFUSED Failed to cast variant value \"10:11:12\" to FIXED"},
            {"SELECT BITOR_AGG(TO_VARIANT(X''))",
                "REFUSED Failed to cast variant value \"\" to FIXED"},
            {"SELECT BITOR_AGG(TO_VARIANT(1.5::FLOAT))",
                "2"},
            {"SELECT BITOR_AGG(TO_VARIANT(X'FF')) FROM (SELECT 1 UNION ALL SELECT 2)",
                "REFUSED Failed to cast variant value \"FF\" to FIXED"},
        });
    }
}
