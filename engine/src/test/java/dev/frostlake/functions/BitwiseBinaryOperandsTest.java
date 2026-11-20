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
 * The bitwise functions over BINARY operands: BITAND, BITOR and BITXOR combine two binaries byte by byte, with a
 * padding side for two lengths; BITNOT inverts every byte; the shifts move bits within the value's own length. Each
 * answers a BINARY; a BINARY beside any other operand is refused as a whole signature. Every cell is live-verified.
 */
public class BitwiseBinaryOperandsTest extends MeasuredCellsTestSupport {

    @Test
    public void binaryOperands() {
        engine.execute("CREATE OR REPLACE TABLE bt (id INT, b2 BINARY(2), b4 BINARY(4), bv BINARY)");
        engine.execute("INSERT INTO bt SELECT 1, X'1010', X'11001010', X'BCBC' UNION ALL SELECT 2, X'BCBC', "
            + "X'ABCDABCD', X'EE' UNION ALL SELECT 3, NULL, NULL, NULL");
        engine.execute("CREATE OR REPLACE TABLE bty AS SELECT BITOR(b2, b4, 'LEFT') c FROM bt");
        assertCells(new String[][] {
            {"SELECT BITAND(X'1010', X'0101')",
                "0000"},
            {"SELECT BITOR(X'1010', X'0101')",
                "1111"},
            {"SELECT BITXOR(X'1010', X'1111')",
                "0101"},
            {"SELECT BITAND(X'FF00', X'0F', 'LEFT')",
                "0000"},
            {"SELECT BITAND(X'FF00', X'0F', 'RIGHT')",
                "0F00"},
            {"SELECT BITOR(X'FF00', X'0F', 'left')",
                "FF0F"},
            {"SELECT BITXOR(X'0F00', X'FF', 'Right')",
                "F000"},
            {"SELECT BIT_OR(X'FF', X'0F', 'LEFT')",
                "FF"},
            {"SELECT BITAND(X'FF00', X'0F', CONCAT('LE', 'FT'))",
                "0000"},
            {"SELECT BITOR(X'FF', X'0F', TO_VARIANT('LEFT'))",
                "FF"},
            {"SELECT TO_VARCHAR(BITOR(X'', X'01', 'LEFT'))",
                "01"},
            {"SELECT LISTAGG(TO_VARCHAR(BITXOR(b2, bv, 'LEFT')), '/') WITHIN GROUP (ORDER BY id) FROM bt",
                "ACAC/BC52"},
            {"SELECT LISTAGG(TO_VARCHAR(BITAND(b2, b4, 'RIGHT')), '/') WITHIN GROUP (ORDER BY id) FROM bt",
                "10000000/A88C0000"},
            {"SELECT BITAND(X'FF', NULL) IS NULL",
                "true"},
            {"SELECT BITAND(NULL::BINARY, X'FF', 'MIDDLE') IS NULL",
                "true"},
            {"SELECT BITAND(X'FF00', X'0F', NULL) IS NULL",
                "true"},
            {"SELECT BITAND(NULL, NULL, 'LEFT') IS NULL",
                "true"},
            {"SELECT BITAND(X'FF00', X'0F')",
                "REFUSED The lengths of two variable-sized fields do not match: first length 2, second length"
                + " 1"},
            {"SELECT COUNT(BITAND(b2, b4)) FROM bt",
                "REFUSED The lengths of two variable-sized fields do not match: first length 2, second length"
                + " 4"},
            {"SELECT BITOR(X'0F', X'FF00', 'MIDDLE')",
                "REFUSED Invalid parameter value: MIDDLE. Reason: Only [left | right] is allowed."},
            {"SELECT BITOR(X'0F', X'FF', ' right ')",
                "REFUSED Invalid parameter value:  RIGHT . Reason: Only [left | right] is allowed."},
            {"SELECT BITAND(X'FF', X'0F', 'LEFT ')",
                "REFUSED Invalid parameter value: LEFT . Reason: Only [left | right] is allowed."},
            {"SELECT BITAND(X'FF', X'0F', 7)",
                "REFUSED Invalid parameter value: 7. Reason: Only [left | right] is allowed."},
            {"SELECT BITAND(X'FF', X'0F', TRUE)",
                "REFUSED Invalid parameter value: TRUE. Reason: Only [left | right] is allowed."},
            {"SELECT BITAND(X'FF', X'0F', 1.5::FLOAT)",
                "REFUSED Invalid parameter value: 1.5. Reason: Only [left | right] is allowed."},
            {"SELECT BITAND(X'FF00', X'0F', '')",
                "REFUSED Invalid parameter value: . Reason: Only [left | right] is allowed."},
            {"SELECT BITAND(1, 3, 'LEFT')",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'BITAND': (NUMBER(1,0), NUMBER(1,0), VARCHAR(4))"},
            {"SELECT BITAND(1, 3, NULL)",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'BITAND': (NUMBER(1,0), NUMBER(1,0), NULL)"},
            {"SELECT BITAND(X'FF', 1)",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'BITAND': (BINARY(1), NUMBER(1,0))"},
            {"SELECT BITOR(1, X'FF')",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'BITOR': (NUMBER(1,0), BINARY(1))"},
            {"SELECT BITXOR(X'FF', '0F')",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'BITXOR': (BINARY(1), VARCHAR(2))"},
            {"SELECT BITAND(X'FF', TO_VARIANT(1))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'BITAND': (BINARY(1), VARIANT)"},
            {"SELECT BITAND(X'FF', X'0F', X'00')",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'BITAND': (BINARY(1), BINARY(1), BINARY(1))"},
            {"SELECT BITAND(X'FF', X'0F', OBJECT_CONSTRUCT('a', 1))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'BITAND': (BINARY(1), BINARY(1), OBJECT)"},
            {"SELECT BITAND(X'FF', X'0F', ARRAY_CONSTRUCT('LEFT'))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'BITAND': (BINARY(1), BINARY(1), ARRAY)"},
            {"SELECT BITAND(X'FF', X'0F', 'LEFT', 1)",
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[BITAND(X'FF', X'0F', 'LEFT', 1)] expected 3, got 4"},
            {"SELECT BITNOT(X'0F', 'LEFT')",
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[BITNOT(X'0F', 'LEFT')] expected 1, got 2"},
            {"SELECT BITSHIFTLEFT(X'01', X'01')",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'BITSHIFTLEFT': (BINARY(1), BINARY(1))"},
            {"SELECT BITSHIFTLEFT(X'01', TRUE)",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'BITSHIFTLEFT': (BINARY(1), BOOLEAN)"},
            {"SELECT BITSHIFTLEFT(X'01', 1, 'LEFT')",
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[BITSHIFTLEFT(X'01', 1, 'LEFT')] expected 2, got 3"},
            {"SELECT BITNOT(X'0F00')",
                "F0FF"},
            {"SELECT TO_VARCHAR(BITNOT(X''))",
                ""},
            {"SELECT LISTAGG(TO_VARCHAR(BITNOT(b4)), '/') WITHIN GROUP (ORDER BY id) FROM bt",
                "EEFFEFEF/54325432"},
            {"SELECT BITNOT(TO_VARIANT(X'FF'))",
                "REFUSED Failed to cast variant value \"FF\" to FIXED"},
            {"SELECT BITSHIFTLEFT(X'0102', 1)",
                "0204"},
            {"SELECT BITSHIFTRIGHT(X'0102', 1)",
                "0081"},
            {"SELECT BITSHIFTLEFT(X'8001', 1)",
                "0002"},
            {"SELECT BITSHIFTRIGHT(X'8001', 1)",
                "4000"},
            {"SELECT BITSHIFTLEFT(X'8001', 15)",
                "8000"},
            {"SELECT BITSHIFTRIGHT(X'8001', 16)",
                "0000"},
            {"SELECT BITSHIFTLEFT(X'FFFF', 3)",
                "FFF8"},
            {"SELECT BITSHIFTLEFT(X'01', '3')",
                "08"},
            {"SELECT BITSHIFTLEFT(X'01', 2.5)",
                "08"},
            {"SELECT BITSHIFTLEFT(X'01', 2147483647)",
                "00"},
            {"SELECT BITSHIFTLEFT(X'01', 2147483648)",
                "REFUSED Numeric value '2147483648' is out of range"},
            {"SELECT BITSHIFTRIGHT(X'01', 2147483647.6)",
                "REFUSED Numeric value '2147483648' is out of range"},
            {"SELECT BITSHIFTRIGHT(X'01', -2147483649)",
                "REFUSED Numeric value '-2147483649' is out of range"},
            {"SELECT BITSHIFTRIGHT(X'8001', -1)",
                "REFUSED Invalid parameter value: -1. Reason: shift parameter must be greater or equal to "
                + "zero"},
            {"SELECT BITSHIFTLEFT(X'01', 'x'::VARIANT)",
                "REFUSED Failed to cast variant value \"x\" to FIXED"},
            {"SELECT BITSHIFTLEFT(NULL::BINARY, -1) IS NULL",
                "true"},
            {"SELECT LISTAGG(TO_VARCHAR(BITSHIFTRIGHT(b4, 4)), '/') WITHIN GROUP (ORDER BY id) FROM bt",
                "01100101/0ABCDABC"},
            {"SELECT SYSTEM$TYPEOF(BITAND(X'FF', X'0F'))",
                "BINARY[LOB]"},
            {"SELECT SYSTEM$TYPEOF(BITNOT(X'FF'))",
                "BINARY[LOB]"},
            {"SELECT SYSTEM$TYPEOF(BITSHIFTRIGHT(X'FF', 1))",
                "BINARY[LOB]"},
            {"SELECT SYSTEM$TYPEOF(BITAND(NULL, X'FF'))",
                "BINARY[LOB]"},
            {"SELECT SYSTEM$TYPEOF(BITAND(NULL, NULL))",
                "NUMBER(2,0)[SB1]"},
            {"SELECT DATA_TYPE || '/' || CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS WHERE "
                + "TABLE_NAME = 'BTY'",
                "null"},
            {"SELECT SYSTEM$TYPEOF(BIT_SHIFTLEFT(X'01', 1))",
                "BINARY[LOB]"},
            {"SELECT BIT_SHIFTRIGHT(X'80', 1)",
                "40"},
            {"SELECT SYSTEM$TYPEOF(BIT_SHIFTRIGHT(1, 2))",
                "NUMBER(2,0)[SB1]"},
            {"SELECT BIT_SHIFTLEFT(X'01', X'01')",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function"
                + " 'BIT_SHIFTLEFT': (BINARY(1), BINARY(1))"},
        });
    }
}
