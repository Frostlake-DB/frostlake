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
 * A time or timestamp a VARIANT holds keeps the scale of the type it was wrapped from: a session output format's FF
 * without a digit count prints that many digits once the VARIANT is rendered as text - TO_VARIANT, a cast to VARIANT,
 * a member of an OBJECT or ARRAY read back, a FLATTEN value, a derived relation's column - and a cast to a time or
 * timestamp type prints that type's scale instead (live-verified).
 */
public class VariantFractionScaleTest extends MeasuredCellsTestSupport {

    @Test
    public void wrappedValues() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'Europe/Warsaw'");
        engine.execute("ALTER SESSION SET TIMESTAMP_NTZ_OUTPUT_FORMAT = 'YYYY-MM-DD HH24:MI:SS.FF'");
        engine.execute("ALTER SESSION SET TIMESTAMP_LTZ_OUTPUT_FORMAT = 'YYYY-MM-DD HH24:MI:SS.FF'");
        engine.execute("ALTER SESSION SET TIMESTAMP_TZ_OUTPUT_FORMAT = 'YYYY-MM-DD HH24:MI:SS.FF TZH:TZM'");
        engine.execute("ALTER SESSION SET TIME_OUTPUT_FORMAT = 'HH24:MI:SS.FF'");
        try {
            engine.execute("CREATE OR REPLACE TABLE tq (n3 TIMESTAMP_NTZ(3), l3 TIMESTAMP_LTZ(3), "
                + "z3 TIMESTAMP_TZ(3), t1 TIME(1), n0 TIMESTAMP_NTZ(0), n9 TIMESTAMP_NTZ(9), n6 TIMESTAMP_NTZ(6))");
            engine.execute("INSERT INTO tq SELECT '2024-01-02 03:04:05.006', '2024-01-02 03:04:05.006', '2024-01-02 "
                + "03:04:05.006 +01:00', '03:04:05.1', '2024-01-02 03:04:05', '2024-01-02 03:04:05.006', "
                + "'2024-01-02 03:04:05.006'");
            assertCells(new String[][] {
                {"SELECT TO_VARIANT(n3)::VARCHAR FROM tq",
                    "2024-01-02 03:04:05.006"},
                {"SELECT TO_VARIANT(l3)::VARCHAR FROM tq",
                    "2024-01-02 03:04:05.006"},
                {"SELECT TO_VARIANT(z3)::VARCHAR FROM tq",
                    "2024-01-02 03:04:05.006 +01:00"},
                {"SELECT TO_VARIANT(t1)::VARCHAR FROM tq",
                    "03:04:05.1"},
                {"SELECT TO_VARIANT(n0)::VARCHAR FROM tq",
                    "2024-01-02 03:04:05."},
                {"SELECT TO_VARIANT(n9)::VARCHAR FROM tq",
                    "2024-01-02 03:04:05.006000000"},
                {"SELECT TO_VARIANT(n6)::VARCHAR FROM tq",
                    "2024-01-02 03:04:05.006000"},
                {"SELECT n3::VARIANT::VARCHAR FROM tq",
                    "2024-01-02 03:04:05.006"},
                {"SELECT n6::VARIANT::VARCHAR FROM tq",
                    "2024-01-02 03:04:05.006000"},
                {"SELECT TO_VARCHAR(TO_VARIANT(n3)) FROM tq",
                    "2024-01-02 03:04:05.006"},
                {"SELECT OBJECT_CONSTRUCT('t', n3):t::VARCHAR FROM tq",
                    "2024-01-02 03:04:05.006"},
                {"SELECT OBJECT_CONSTRUCT('t', l3):t::VARCHAR FROM tq",
                    "2024-01-02 03:04:05.006"},
                {"SELECT OBJECT_CONSTRUCT('t', t1):t::VARCHAR FROM tq",
                    "03:04:05.1"},
                {"SELECT OBJECT_CONSTRUCT('t', n0):t::VARCHAR FROM tq",
                    "2024-01-02 03:04:05."},
                {"SELECT OBJECT_CONSTRUCT('t', n6):t::VARCHAR FROM tq",
                    "2024-01-02 03:04:05.006000"},
                {"SELECT TO_VARCHAR(OBJECT_CONSTRUCT('t', n3):t) FROM tq",
                    "2024-01-02 03:04:05.006"},
                {"SELECT OBJECT_CONSTRUCT('t', z3):t::VARCHAR FROM tq",
                    "2024-01-02 03:04:05.006 +01:00"},
                {"SELECT ARRAY_CONSTRUCT(l3)[0]::VARCHAR FROM tq",
                    "2024-01-02 03:04:05.006"},
                {"SELECT ARRAY_CONSTRUCT(n3, n6)[1]::VARCHAR FROM tq",
                    "2024-01-02 03:04:05.006000"},
                {"SELECT ARRAY_CONSTRUCT(t1)[0]::VARCHAR FROM tq",
                    "03:04:05.1"},
                {"SELECT GET(ARRAY_CONSTRUCT(n3), 0)::VARCHAR FROM tq",
                    "2024-01-02 03:04:05.006"},
                {"SELECT x.value::VARCHAR FROM tq, LATERAL FLATTEN(ARRAY_CONSTRUCT(n3)) x",
                    "2024-01-02 03:04:05.006"},
                {"SELECT q.v::VARCHAR FROM (SELECT TO_VARIANT(n3) v FROM tq) q",
                    "2024-01-02 03:04:05.006"},
                {"SELECT q.v:t::VARCHAR FROM (SELECT OBJECT_CONSTRUCT('t', n3) v FROM tq) q",
                    "2024-01-02 03:04:05.006"},
                {"SELECT TO_VARIANT(n3::TIMESTAMP_NTZ(9))::VARCHAR FROM tq",
                    "2024-01-02 03:04:05.006000000"},
            });
        } finally {
            engine.execute("ALTER SESSION UNSET TIMEZONE");
            engine.execute("ALTER SESSION UNSET TIMESTAMP_NTZ_OUTPUT_FORMAT");
            engine.execute("ALTER SESSION UNSET TIMESTAMP_LTZ_OUTPUT_FORMAT");
            engine.execute("ALTER SESSION UNSET TIMESTAMP_TZ_OUTPUT_FORMAT");
            engine.execute("ALTER SESSION UNSET TIME_OUTPUT_FORMAT");
        }
    }

    @Test
    public void sharedValues() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'Europe/Warsaw'");
        engine.execute("ALTER SESSION SET TIMESTAMP_NTZ_OUTPUT_FORMAT = 'YYYY-MM-DD HH24:MI:SS.FF'");
        engine.execute("ALTER SESSION SET TIME_OUTPUT_FORMAT = 'HH24:MI:SS.FF'");
        try {
            engine.execute("CREATE OR REPLACE TABLE tr (t1 TIME(1), t9 TIME(9), n3 TIMESTAMP_NTZ(3))");
            engine.execute("INSERT INTO tr SELECT '03:00:00', '03:00:00', '2024-01-02 03:04:05.006'");
            assertCells(new String[][] {
                {"SELECT TO_VARIANT(t1)::VARCHAR || '|' || TO_VARIANT(t9)::VARCHAR FROM tr",
                    "03:00:00.0|03:00:00.000000000"},
                {"SELECT TO_VARIANT(t9)::VARCHAR || '|' || TO_VARIANT(t1)::VARCHAR FROM tr",
                    "03:00:00.000000000|03:00:00.0"},
                {"SELECT t9::VARCHAR || '|' || TO_VARIANT(t1)::VARCHAR FROM tr",
                    "03:00:00.000000000|03:00:00.0"},
                {"SELECT TO_VARIANT(n3):: TIMESTAMP_NTZ(6)::VARCHAR FROM tr",
                    "2024-01-02 03:04:05.006000"},
                {"SELECT IFF(TRUE, TO_VARIANT(n3), NULL)::VARCHAR FROM tr",
                    "2024-01-02 03:04:05.006"},
                {"SELECT (SELECT TO_VARIANT(n3) FROM tr)::VARCHAR",
                    "2024-01-02 03:04:05.006"},
            });
        } finally {
            engine.execute("ALTER SESSION UNSET TIMEZONE");
            engine.execute("ALTER SESSION UNSET TIMESTAMP_NTZ_OUTPUT_FORMAT");
            engine.execute("ALTER SESSION UNSET TIME_OUTPUT_FORMAT");
        }
    }
}
