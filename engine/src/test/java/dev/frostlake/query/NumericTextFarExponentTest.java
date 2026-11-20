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
 * Numeric text with an exponent far past any number (live-verified where the account answers): too large is not
 * recognized as a number, too small rounds to zero at the target's scale, and a VARIANT text fails the VARIANT's
 * cast.
 */
public class NumericTextFarExponentTest extends MeasuredCellsTestSupport {

    @Test
    public void farExponents() {
        assertCells(new String[][] {
            {"SELECT ('1e-400'::NUMBER(10,2))::VARCHAR",
                "0.00"},
            {"SELECT '1e-2147483649'::NUMBER",
                "REFUSED Numeric value '1e-2147483649' is not recognized"},
            {"SELECT '1e40'::INT",
                "REFUSED Numeric value '1e40' is not recognized"},
            {"SELECT ('0e999999999'::INT)::VARCHAR",
                "0"},
            {"SELECT '1e99999999999999999999'::INT",
                "REFUSED Numeric value '1e99999999999999999999' is not recognized"},
            {"SELECT '1e-99999999999999999999'::NUMBER(10,2)",
                "REFUSED Numeric value '1e-99999999999999999999' is not recognized"},
            {"SELECT '1e999999999'::DECFLOAT",
                "REFUSED Numeric value '1e999999999' is not recognized"},
            {"SELECT '1e-999999999' + 1",
                "REFUSED SQL compilation error:  | Invalid intermediate datatype: "
                + "NUMBER(1000000000,999999999)."},
            {"SELECT '1e-999999999' = 0",
                "REFUSED SQL compilation error:  | Invalid intermediate datatype: "
                + "NUMBER(1000000000,999999999)."},
            {"SELECT '1e-2147483648'::NUMBER",
                "REFUSED Numeric value '1e-2147483648' is not recognized"},
            {"SELECT ('1e-40'::NUMBER(38,37))::VARCHAR",
                "0.0000000000000000000000000000000000000"},
            {"SELECT '1e-999999999' * 2",
                "REFUSED SQL compilation error:  | Invalid intermediate datatype: "
                + "NUMBER(1000000000,999999999)."},
            {"SELECT '1e999999999'::VARIANT::INT",
                "REFUSED Failed to cast variant value \"1e999999999\" to FIXED"},
            {"SELECT TO_NUMBER('1e99999')",
                "REFUSED Numeric value '1e99999' is not recognized"},
            {"SELECT ('1e-99999'::NUMBER(10,2))::VARCHAR",
                "0.00"},
            {"SELECT '1e999999999'::DECFLOAT + 1",
                "REFUSED Numeric value '1e999999999' is not recognized"},
            {"SELECT '1e40'::VARIANT::INT",
                "REFUSED Failed to cast variant value \"1e40\" to FIXED"},
            {"SELECT '1e99999'::VARIANT::INT",
                "REFUSED Failed to cast variant value \"1e99999\" to FIXED"},
            {"SELECT PARSE_JSON('1e40')::INT",
                "REFUSED Number out of representable range: type FIXED, value 1e+40"},
            {"SELECT '123456789012345678901234567890123456789'::VARIANT::INT",
                "REFUSED Failed to cast variant value \"123456789012345678901234567890123456789\" to FIXED"},
            {"SELECT ('1e-99999'::VARIANT::NUMBER(10,2))::VARCHAR",
                "0.00"},
            {"SELECT '1e38'::VARIANT::INT",
                "REFUSED Failed to cast variant value \"1e38\" to FIXED"},
            {"SELECT '1e99999'::VARIANT::NUMBER(10,2)",
                "REFUSED Failed to cast variant value \"1e99999\" to FIXED"},
            {"SELECT TO_NUMBER('1e99999'::VARIANT)",
                "REFUSED Failed to cast variant value \"1e99999\" to FIXED"},
            {"SELECT TO_NUMBER('1e999999999'::VARIANT)",
                "REFUSED Failed to cast variant value \"1e999999999\" to FIXED"},
            {"SELECT '1e-99999' = 0",
                "REFUSED SQL compilation error:  | Invalid intermediate datatype: NUMBER(100000,99999)."},
            {"SELECT '1e-40' = 0",
                "REFUSED SQL compilation error:  | Invalid intermediate datatype: NUMBER(41,40)."},
        });
    }
}
