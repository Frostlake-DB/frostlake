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
 * A session output format's FF without a digit count prints the scale of the value's own type, as TO_CHAR's does
 * (live-verified): a TIMESTAMP_NTZ(3)'s three digits, a TIMESTAMP_NTZ(6)'s six, a TIMESTAMP_NTZ(0)'s none after the
 * dot, a TIME(1)'s one, a TIMESTAMP_TZ(2)'s two, and a cast's own scale - nine for a bare TIMESTAMP_NTZ and a
 * DATEADD - through TO_VARCHAR, a cast to text, and a member of an OBJECT built from the value.
 */
public class SessionFormatFractionScaleTest extends MeasuredCellsTestSupport {

    private static final String[] SET = {
        "TIMEZONE = 'Europe/Warsaw'",
        "TIMESTAMP_NTZ_OUTPUT_FORMAT = 'YYYY-MM-DD HH24:MI:SS.FF'",
        "TIMESTAMP_LTZ_OUTPUT_FORMAT = 'YYYY-MM-DD HH24:MI:SS.FF'",
        "TIMESTAMP_TZ_OUTPUT_FORMAT = 'YYYY-MM-DD HH24:MI:SS.FF TZHTZM'",
        "TIME_OUTPUT_FORMAT = 'HH24:MI:SS.FF'",
    };

    private static final String[] UNSET = {
        "TIMEZONE", "TIMESTAMP_NTZ_OUTPUT_FORMAT", "TIMESTAMP_LTZ_OUTPUT_FORMAT", "TIMESTAMP_TZ_OUTPUT_FORMAT",
        "TIME_OUTPUT_FORMAT",
    };

    @Test
    public void aBareFfPrintsTheTypesScale() {
        for (final String parameter : SET) {
            engine.execute("ALTER SESSION SET " + parameter);
        }
        try {
            engine.execute("CREATE OR REPLACE TABLE ff_t (n3 TIMESTAMP_NTZ(3), n6 TIMESTAMP_NTZ(6), "
                + "n0 TIMESTAMP_NTZ(0), l3 TIMESTAMP_LTZ(3), t1 TIME(1), z2 TIMESTAMP_TZ(2))");
            engine.execute("INSERT INTO ff_t SELECT '2024-01-02 03:04:05.006', '2024-01-02 03:04:05.006', "
                + "'2024-01-02 03:04:05', '2024-01-02 03:04:05.006', '03:04:05.5', '2024-01-02 03:04:05.006 +01:00'");
            assertCells(new String[][] {
                {"SELECT SPLIT_PART(TO_VARCHAR(n3), '.', 2), SPLIT_PART(TO_VARCHAR(n6), '.', 2), '[' || "
                    + "SPLIT_PART(TO_VARCHAR(n0), '.', 2) || ']', SPLIT_PART(TO_VARCHAR(l3), '.', 2), "
                    + "SPLIT_PART(TO_VARCHAR(t1), '.', 2), SPLIT_PART(TO_VARCHAR(z2), '.', 2) FROM ff_t",
                    "006|006000|[]|006|5|00 +0100"},
                {"SELECT SPLIT_PART(n3::VARCHAR, '.', 2), SPLIT_PART(n6::VARCHAR, '.', 2), "
                    + "SPLIT_PART(l3::VARCHAR, '.', 2), SPLIT_PART(t1::VARCHAR, '.', 2), "
                    + "SPLIT_PART(z2::VARCHAR, '.', 2) FROM ff_t", "006|006000|006|5|00 +0100"},
                {"SELECT SPLIT_PART(TO_VARCHAR('2024-01-02 03:04:05.006'::TIMESTAMP_NTZ(3)), '.', 2), "
                    + "SPLIT_PART(TO_VARCHAR('2024-01-02 03:04:05.006'::TIMESTAMP_NTZ), '.', 2), "
                    + "SPLIT_PART(TO_VARCHAR(DATEADD(ms, 1, n3)), '.', 2) FROM ff_t", "006|006000000|007000000"},
                {"SELECT SPLIT_PART(TO_JSON(OBJECT_CONSTRUCT('t', n3)), '.', 2), "
                    + "SPLIT_PART(OBJECT_CONSTRUCT('t', l3)::VARCHAR, '.', 2) FROM ff_t", "006\"}|006\"}"},
            });
        } finally {
            for (final String parameter : UNSET) {
                engine.execute("ALTER SESSION UNSET " + parameter);
            }
        }
    }
}
