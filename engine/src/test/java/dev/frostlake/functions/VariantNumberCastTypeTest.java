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
 * AS_DECIMAL and AS_NUMBER declare the precision and scale written after the VARIANT, a number written with a
 * fraction read as its whole part, as the value is: {@code AS_DECIMAL(v, 10, 2.0)} is a NUMBER(10,2) holding the
 * value rounded to two places (live-verified).
 */
public class VariantNumberCastTypeTest extends MeasuredCellsTestSupport {

    @Test
    public void aPrecisionOrScaleWrittenWithAFractionIsReadAsItsWholePart() {
        assertAnswer("NUMBER(10,2)[SB1]|1.26",
            "SELECT SYSTEM$TYPEOF(AS_DECIMAL(TO_VARIANT(1.256), 10, 2.0)), AS_DECIMAL(TO_VARIANT(1.256), 10, 2.0)");
        assertAnswer("NUMBER(10,2)[SB1]|NUMBER(10,2)[SB1]",
            "SELECT SYSTEM$TYPEOF(AS_NUMBER(TO_VARIANT(1.256), 10, 2.0)), "
            + "SYSTEM$TYPEOF(AS_DECIMAL(TO_VARIANT(1.256), 10.0, 2.0))");
        assertShape("D:NUMBER(10,2)", "SELECT AS_DECIMAL(TO_VARIANT(1.256), 10, 2.0) AS d");
        assertAnswer("NUMBER(10,2)[SB1]|1.26",
            "SELECT SYSTEM$TYPEOF(AS_DECIMAL(TO_VARIANT(1.256), 10, 2)), AS_DECIMAL(TO_VARIANT(1.256), 10, 2)");
    }
}
