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

package dev.frostlake.udf;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * An argument of another type than text reaches a SQL function's VARCHAR parameter converted to text, so a body that
 * hands the parameter back is a VARCHAR whatever the argument was - a number, a boolean, a FLOAT and a DATE alike, a
 * constant or a column: one of no length where the parameter is declared without one, the unbounded width where it
 * is declared with one. A text argument keeps its own width (live-verified). A text constant is folded into the body
 * (see UdfArgumentTypeTest).
 */
public class UdfTextParameterTypeTest extends MeasuredCellsTestSupport {

    @Test
    public void aConstantOfAnotherTypeReachesATextParameterAsText() {
        engine.execute("CREATE OR REPLACE FUNCTION tp_echo(p VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ p $$");
        assertAnswer("VARCHAR[LOB]|VARCHAR[LOB]|VARCHAR[LOB]|VARCHAR[LOB]",
            "SELECT SYSTEM$TYPEOF(tp_echo(1)), SYSTEM$TYPEOF(tp_echo(TRUE)), SYSTEM$TYPEOF(tp_echo(1.5::FLOAT)), "
            + "SYSTEM$TYPEOF(tp_echo('2024-01-01'::DATE))");
        assertAnswer("1|true|1.5|2024-01-01",
            "SELECT tp_echo(1), tp_echo(TRUE), tp_echo(1.5::FLOAT), tp_echo('2024-01-01'::DATE)");
        assertShape("A:VARCHAR(134217728), B:VARCHAR(134217728)",
            "SELECT tp_echo(1) AS a, tp_echo('2024-01-01'::DATE) AS b");
        assertAnswer("VARCHAR[LOB]|VARCHAR[LOB]|VARCHAR(3)[LOB]", "SELECT SYSTEM$TYPEOF(tp_echo(n)), "
            + "SYSTEM$TYPEOF(tp_echo(d)), SYSTEM$TYPEOF(tp_echo(s)) "
            + "FROM (SELECT 1 AS n, CURRENT_DATE() AS d, 'abc' AS s)");
    }

    @Test
    public void aSizedTextParameterReadsAnotherTypeAtTheUnboundedWidth() {
        engine.execute("CREATE OR REPLACE FUNCTION tp5(p VARCHAR(5)) RETURNS VARCHAR LANGUAGE SQL AS $$ p $$");
        assertAnswer("VARCHAR(134217728)[LOB]|VARCHAR(134217728)[LOB]|VARCHAR(134217728)[LOB]|VARCHAR(8)[LOB]",
            "SELECT SYSTEM$TYPEOF(tp5(1)), SYSTEM$TYPEOF(tp5(n)), SYSTEM$TYPEOF(tp5(TRUE)), "
            + "SYSTEM$TYPEOF(tp5('abcdefgh')) FROM (SELECT 1 AS n)");
    }
}
