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
 * A timestamp text written with an offset past 18 hours names the instant at that offset (live-verified): an LTZ
 * expresses it in the session's zone, and a TIMESTAMP_TZ compares, converts and counts by it.
 */
public class TimestampOffsetRangeTest extends MeasuredCellsTestSupport {

    @Test
    public void offsets() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        assertCells(new String[][] {
            {"SELECT ('2024-01-05 10:00:00 +25:00'::TIMESTAMP_LTZ)::VARCHAR",
                "2024-01-04 01:00:00.000 -0800"},
            {"SELECT ('2024-01-05 10:00:00 +25:00'::TIMESTAMP_NTZ)::VARCHAR",
                "2024-01-05 10:00:00.000"},
            {"SELECT ('2024-01-05 10:00:00 +25:00'::DATE)::VARCHAR",
                "2024-01-05"},
            {"SELECT (TO_TIMESTAMP_LTZ('2024-01-05 10:00:00 +20:00'))::VARCHAR",
                "2024-01-04 06:00:00.000 -0800"},
            {"SELECT ('2024-01-05 10:00:00 +20:00'::TIMESTAMP)::VARCHAR",
                "2024-01-05 10:00:00.000"},
            {"SELECT ('2024-01-05 10:00:00 +18:00'::TIMESTAMP_LTZ)::VARCHAR",
                "2024-01-04 08:00:00.000 -0800"},
            {"SELECT ('2024-01-05 10:00:00 +20:00'::TIMESTAMP_LTZ)::VARCHAR",
                "2024-01-04 06:00:00.000 -0800"},
            {"SELECT '2024-01-05 10:00:00 +20:00'::TIMESTAMP_TZ = '2024-01-04 14:00:00 Z'::TIMESTAMP_TZ",
                "true"},
            {"SELECT '2024-01-05 +25:00'::TIMESTAMP_TZ",
                "REFUSED Timestamp '2024-01-05 +25:00' is not recognized"},
        });
    }

    @Test
    public void instants() {
        assertCells(new String[][] {
            {"SELECT '2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ = '2024-01-04 09:00:00 Z'::TIMESTAMP_TZ",
                "true"},
            {"SELECT (DATE_PART(epoch_second, '2024-01-05 10:00:00 -23:59'::TIMESTAMP_TZ))::VARCHAR",
                "1704535140"},
            {"SELECT (DATEDIFF(minute, '2024-01-05 10:00:00 +19:30'::TIMESTAMP_TZ, '2024-01-05 10:00:00 "
                + "Z'::TIMESTAMP_TZ))::VARCHAR",
                "1170"},
        });
    }
}
