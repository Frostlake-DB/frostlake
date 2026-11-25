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
 * A TIMESTAMP_TZ keeps an offset of any two-digit hours as written, past the 18 hours a zone offset spans
 * (live-verified): it reads and shows the wall clock and offset written, converts to TIMESTAMP_NTZ, DATE and TIME
 * by that wall clock, compares and converts to TIMESTAMP_LTZ by its instant, moves by DATEADD and intervals on its
 * wall clock, and keeps the offset in a table.
 */
public class TimestampTzWideOffsetTest extends MeasuredCellsTestSupport {

    @Test
    public void parsedAndShown() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        assertCells(new String[][] {
            {"SELECT '2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ::VARCHAR",
                "2024-01-05 10:00:00.000 +2500"},
            {"SELECT '2024-01-05 10:00:00 +2500'::TIMESTAMP_TZ::VARCHAR",
                "2024-01-05 10:00:00.000 +2500"},
            {"SELECT '2024-01-05 10:00:00 +99:59'::TIMESTAMP_TZ::VARCHAR",
                "2024-01-05 10:00:00.000 +9959"},
            {"SELECT '2024-01-05 10:00:00 +18:01'::TIMESTAMP_TZ::VARCHAR",
                "2024-01-05 10:00:00.000 +1801"},
            {"SELECT '2024-01-05 10:00:00 +19'::TIMESTAMP_TZ::VARCHAR",
                "2024-01-05 10:00:00.000 +1900"},
            {"SELECT '2024-01-05 10:00:00 +25:60'::TIMESTAMP_TZ::VARCHAR",
                "2024-01-05 10:00:00.000 +2600"},
            {"SELECT '2024-01-05T10:00:00+25:00'::TIMESTAMP_TZ::VARCHAR",
                "2024-01-05 10:00:00.000 +2500"},
            {"SELECT '2024-01-05 10:00:00.123456789 +25:30'::TIMESTAMP_TZ(9)::VARCHAR",
                "2024-01-05 10:00:00.123 +2530"},
            {"SELECT '2024-01-05 +25:00'::TIMESTAMP_TZ::VARCHAR",
                "REFUSED Timestamp '2024-01-05 +25:00' is not recognized"},
            {"SELECT '2024-01-05 10:00:00 +100:00'::TIMESTAMP_TZ::VARCHAR",
                "REFUSED Timestamp '2024-01-05 10:00:00 +100:00' is not recognized"},
            {"SELECT TO_TIMESTAMP_TZ('2024-01-05 10:00:00 +20:00', 'YYYY-MM-DD HH24:MI:SS "
                + "TZH:TZM')::VARCHAR",
                "2024-01-05 10:00:00.000 +2000"},
            {"SELECT TO_TIMESTAMP_TZ('2024-01-05 10:00:00 +2000', 'YYYY-MM-DD HH24:MI:SS TZHTZM')::VARCHAR",
                "2024-01-05 10:00:00.000 +2000"},
            {"SELECT TO_TIMESTAMP_TZ('2024-01-05 10:00:00 +20', 'YYYY-MM-DD HH24:MI:SS TZH')::VARCHAR",
                "2024-01-05 10:00:00.000 +2000"},
            {"SELECT TO_TIMESTAMP_TZ('2024-01-05 10:00:00 +99:59', 'YYYY-MM-DD HH24:MI:SS "
                + "TZH:TZM')::VARCHAR",
                "2024-01-05 10:00:00.000 +9959"},
            {"SELECT TO_TIMESTAMP_TZ('2024-01-05 10:00:00 +25:00')::VARCHAR",
                "2024-01-05 10:00:00.000 +2500"},
            {"SELECT TRY_TO_TIMESTAMP_TZ('2024-01-05 10:00:00 +25:00')::VARCHAR",
                "2024-01-05 10:00:00.000 +2500"},
            {"SELECT TO_CHAR('2024-01-05 10:00:00 +25:30'::TIMESTAMP_TZ, 'YYYY-MM-DD HH24:MI:SS TZH:TZM')",
                "2024-01-05 10:00:00 +25:30"},
            {"SELECT TO_CHAR('2024-01-05 10:00:00 +25:30'::TIMESTAMP_TZ, 'TZHTZM')",
                "+2530"},
            {"SELECT TO_CHAR('2024-01-05 10:00:00 +25:30'::TIMESTAMP_TZ, 'TZH')",
                "+25"},
            {"SELECT TO_CHAR('2024-01-05 10:00:00 +25:30'::TIMESTAMP_TZ, "
                + "'YYYY-MM-DD\"T\"HH24:MI:SS.FF3TZH:TZM')",
                "2024-01-05T10:00:00.000+25:30"},
            {"SELECT TO_CHAR('2024-01-05 10:00:00 +25:30'::TIMESTAMP_TZ)",
                "2024-01-05 10:00:00.000 +2530"},
            {"SELECT '2024-01-05 10:00:00 +25:30'::TIMESTAMP_TZ::TEXT",
                "2024-01-05 10:00:00.000 +2530"},
            {"SELECT '2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ = '2024-01-04 09:00:00 Z'::TIMESTAMP_TZ",
                "true"},
            {"SELECT '2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ < '2024-01-04 09:00:01 Z'::TIMESTAMP_TZ",
                "true"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT '2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ x UNION ALL"
                + " SELECT '2024-01-04 09:00:00 Z'::TIMESTAMP_TZ)",
                "1"},
            {"SELECT GREATEST('2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ, '2024-01-04 08:00:00 "
                + "Z'::TIMESTAMP_TZ)::VARCHAR",
                "2024-01-05 10:00:00.000 +2500"},
            {"SELECT MAX(x)::VARCHAR FROM (SELECT '2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ x UNION ALL "
                + "SELECT '2024-01-04 08:00:00 Z'::TIMESTAMP_TZ)",
                "2024-01-05 10:00:00.000 +2500"},
            {"SELECT '2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ::TIMESTAMP_NTZ::VARCHAR",
                "2024-01-05 10:00:00.000"},
            {"SELECT '2024-01-05 10:00:00 +99:59'::TIMESTAMP_TZ::TIMESTAMP_NTZ::VARCHAR",
                "2024-01-05 10:00:00.000"},
            {"SELECT '2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ::TIMESTAMP_LTZ::VARCHAR",
                "2024-01-04 01:00:00.000 -0800"},
            {"SELECT ('2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ::DATE)::VARCHAR",
                "2024-01-05"},
            {"SELECT ('2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ::TIME)::VARCHAR",
                "10:00:00"},
            {"SELECT TO_TIMESTAMP_NTZ('2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ)::VARCHAR",
                "2024-01-05 10:00:00.000"},
            {"SELECT '2024-01-05 10:00:00 +25:00'::TIMESTAMP_NTZ::VARCHAR",
                "2024-01-05 10:00:00.000"},
            {"SELECT '2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ::VARIANT::VARCHAR",
                "2024-01-05 10:00:00.000 +2500"},
            {"SELECT TO_JSON(OBJECT_CONSTRUCT('a', '2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ))",
                "{\"a\":\"2024-01-05 10:00:00.000 +2500\"}"},
            {"SELECT DATE_PART(EPOCH_SECOND, '2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ)",
                "1704358800"},
            {"SELECT CONVERT_TIMEZONE('UTC', '2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ)::VARCHAR",
                "2024-01-04 09:00:00.000 Z"},
            {"SELECT CONVERT_TIMEZONE('UTC', '2024-01-05 10:00:00 Z'::TIMESTAMP_TZ)::VARCHAR",
                "2024-01-05 10:00:00.000 Z"},
            {"SELECT '2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ::TIMESTAMP_TZ(3)::VARCHAR",
                "2024-01-05 10:00:00.000 +2500"},
            {"SELECT TO_TIMESTAMP_TZ('2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ)::VARCHAR",
                "2024-01-05 10:00:00.000 +2500"},
            {"SELECT TO_TIMESTAMP_TZ('2024-01-05 10:00:00 +25:00'::TIMESTAMP_NTZ)::VARCHAR",
                "2024-01-05 10:00:00.000 -0800"},
            {"SELECT DATEADD(hour, 1, '2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ)::VARCHAR",
                "2024-01-05 11:00:00.000 +2500"},
            {"SELECT DATEADD(day, 1, '2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ)::VARCHAR",
                "2024-01-06 10:00:00.000 +2500"},
            {"SELECT DATEADD(month, 1, '2024-01-31 10:00:00 +25:00'::TIMESTAMP_TZ)::VARCHAR",
                "2024-02-29 10:00:00.000 +2500"},
            {"SELECT ('2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ + INTERVAL '2 hours')::VARCHAR",
                "2024-01-05 12:00:00.000 +2500"},
            {"SELECT DATEDIFF(hour, '2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ, '2024-01-05 10:00:00 "
                + "Z'::TIMESTAMP_TZ)",
                "25"},
            {"SELECT DATEDIFF(day, '2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ, '2024-01-05 10:00:00 "
                + "Z'::TIMESTAMP_TZ)",
                "0"},
            {"SELECT DATE_TRUNC(day, '2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ)::VARCHAR",
                "2024-01-05 00:00:00.000 +2500"},
            {"SELECT DATE_TRUNC(hour, '2024-01-05 10:30:00 +25:00'::TIMESTAMP_TZ)::VARCHAR",
                "2024-01-05 10:00:00.000 +2500"},
            {"SELECT EXTRACT(hour FROM '2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ)",
                "10"},
            {"SELECT (LAST_DAY('2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ))::VARCHAR",
                "2024-01-31"},
            {"SELECT TIMESTAMPADD(minute, 5, '2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ)::VARCHAR",
                "2024-01-05 10:05:00.000 +2500"},
            {"SELECT '2024-01-05 10:00:00 -18:01'::TIMESTAMP_TZ::VARCHAR",
                "2024-01-05 10:00:00.000 -1801"},
            {"SELECT '2024-01-05 10:00:00 -19:00'::TIMESTAMP_TZ::VARCHAR",
                "2024-01-05 10:00:00.000 -1900"},
            {"SELECT '2024-01-05 10:00:00 -23:59'::TIMESTAMP_TZ::VARCHAR",
                "2024-01-05 10:00:00.000 -2359"},
            {"SELECT '2024-01-05 10:00:00 -24:00'::TIMESTAMP_TZ::VARCHAR",
                "2024-01-05 10:00:00.000 -2400"},
            {"SELECT '2024-01-05 10:00:00 -19:00'::TIMESTAMP_TZ::TIMESTAMP_NTZ::VARCHAR",
                "2024-01-05 10:00:00.000"},
            {"SELECT TO_CHAR('2024-01-05 10:00:00 -19:30'::TIMESTAMP_TZ, 'TZH:TZM')",
                "-19:30"},
            {"SELECT '2024-01-05 10:00:00 -24:00'::TIMESTAMP_TZ::TIMESTAMP_LTZ::VARCHAR",
                "2024-01-06 02:00:00.000 -0800"},
            {"SELECT '2024-01-05 10:00:00 +18:00'::TIMESTAMP_TZ::VARCHAR",
                "2024-01-05 10:00:00.000 +1800"},
            {"SELECT '2024-01-05 10:00:00 -18:00'::TIMESTAMP_TZ::VARCHAR",
                "2024-01-05 10:00:00.000 -1800"},
            {"SELECT DATEDIFF(day, '2024-01-05 10:00:00 +10:00'::TIMESTAMP_TZ, '2024-01-05 10:00:00 "
                + "Z'::TIMESTAMP_TZ)",
                "0"},
            {"SELECT DATEDIFF(day, '2024-01-05 10:00:00 +23:00'::TIMESTAMP_TZ, '2024-01-05 10:00:00 "
                + "Z'::TIMESTAMP_TZ)",
                "0"},
            {"SELECT DATEDIFF(day, '2024-01-05 10:00:00 Z'::TIMESTAMP_TZ, '2024-01-05 10:00:00 "
                + "+23:00'::TIMESTAMP_TZ)",
                "0"},
            {"SELECT DATEDIFF(day, '2024-01-05 01:00:00 +10:00'::TIMESTAMP_TZ, '2024-01-05 10:00:00 "
                + "Z'::TIMESTAMP_TZ)",
                "0"},
            {"SELECT DATEDIFF(minute, '2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ, '2024-01-05 10:00:00 "
                + "Z'::TIMESTAMP_TZ)",
                "1500"},
            {"SELECT DATE_TRUNC(month, '2024-01-01 10:00:00 +25:00'::TIMESTAMP_TZ)::VARCHAR",
                "2024-01-01 00:00:00.000 +2500"},
            {"SELECT DATEADD(month, 1, '2024-01-31 23:00:00 +25:00'::TIMESTAMP_TZ)::VARCHAR",
                "2024-02-29 23:00:00.000 +2500"},
            {"SELECT TYPEOF('2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ::VARIANT)",
                "TIMESTAMP_TZ"},
            {"SELECT '2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ::VARIANT::TIMESTAMP_TZ::VARCHAR",
                "2024-01-05 10:00:00.000 +2500"},
            {"SELECT TIMESTAMP_TZ_FROM_PARTS(2024, 1, 5, 10, 0, 0, 0, '+25:00')::VARCHAR",
                "REFUSED Unknown timezone: '+25:00'"},
            {"SELECT TO_TIMESTAMP_TZ('2024-01-05 10:00:00 +25:00')::TIMESTAMP_LTZ::TIMESTAMP_TZ::VARCHAR",
                "2024-01-04 01:00:00.000 -0800"},
            {"SELECT CONVERT_TIMEZONE('+25:00', '2024-01-05 10:00:00 Z'::TIMESTAMP_TZ)::VARCHAR",
                "REFUSED Unknown timezone: '+25:00'"},
            {"SELECT ('2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ - INTERVAL '1 day')::VARCHAR",
                "2024-01-04 10:00:00.000 +2500"},
            {"SELECT TO_CHAR('2024-01-05 10:00:00 +25:30'::TIMESTAMP_TZ, 'YYYY-MM-DD HH12 AM DY MON')",
                "2024-01-05 10 AM Fri Jan"},
            {"SELECT '2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ::TIMESTAMP_TZ::TIMESTAMP_NTZ::VARCHAR",
                "2024-01-05 10:00:00.000"},
            {"SELECT GREATEST('2024-01-05 10:00:00 +01:00'::TIMESTAMP_TZ, '2024-01-04 08:00:00 "
                + "Z'::TIMESTAMP_TZ)::VARCHAR",
                "2024-01-05 10:00:00.000 +0100"},
            {"SELECT LEAST('2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ, '2024-01-04 08:00:00 "
                + "Z'::TIMESTAMP_TZ)::VARCHAR",
                "2024-01-04 08:00:00.000 Z"},
            {"SELECT GREATEST('2024-01-04 08:00:00 Z'::TIMESTAMP_TZ, '2024-01-05 10:00:00 "
                + "+25:00'::TIMESTAMP_TZ)::VARCHAR",
                "2024-01-05 10:00:00.000 +2500"},
            {"SELECT COALESCE(NULL, '2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ)::VARCHAR",
                "2024-01-05 10:00:00.000 +2500"},
            {"SELECT IFF(TRUE, '2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ, NULL)::VARCHAR",
                "2024-01-05 10:00:00.000 +2500"},
            {"SELECT (TO_TIME('2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ))::VARCHAR",
                "10:00:00"},
            {"SELECT (TO_DATE('2024-01-05 23:00:00 +25:00'::TIMESTAMP_TZ))::VARCHAR",
                "2024-01-05"},
            {"SELECT DATE_TRUNC(week, '2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ)::VARCHAR",
                "2024-01-01 00:00:00.000 +2500"},
            {"SELECT ADD_MONTHS('2024-01-31 10:00:00 +25:00'::TIMESTAMP_TZ, 1)::VARCHAR",
                "2024-02-29 10:00:00.000 +2500"},
            {"SELECT DATEADD(nanosecond, 5, '2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ)::VARCHAR",
                "2024-01-05 10:00:00.000 +2500"},
            {"SELECT TO_CHAR('2024-01-05 10:00:00 +25:30'::TIMESTAMP_TZ, 'TZD')",
                "GMT+25:30"},
            {"SELECT '2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ::TIMESTAMP_TZ(0)::VARCHAR",
                "2024-01-05 10:00:00.000 +2500"},
            {"SELECT ('2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ)::STRING || 'x'",
                "2024-01-05 10:00:00.000 +2500x"},
            {"SELECT TO_VARIANT('2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ)::TIMESTAMP_NTZ::VARCHAR",
                "2024-01-05 10:00:00.000"},
            {"SELECT TO_TIMESTAMP_TZ(PARSE_JSON('\"2024-01-05 10:00:00 +25:00\"'))::VARCHAR",
                "2024-01-05 10:00:00.000 +2500"},
            {"SELECT DATEDIFF(second, '2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ, '2024-01-05 10:00:00 "
                + "+19:00'::TIMESTAMP_TZ)",
                "21600"},
            {"SELECT TIMEDIFF(day, '2024-01-05 23:00:00 +25:00'::TIMESTAMP_TZ, '2024-01-06 01:00:00 "
                + "Z'::TIMESTAMP_TZ)",
                "1"},
            {"SELECT HASH('2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ) = HASH('2024-01-04 09:00:00 "
                + "Z'::TIMESTAMP_TZ)",
                "true"},
        });
    }

    @Test
    public void stored() {
        engine.execute("CREATE OR REPLACE TABLE t1 (x TIMESTAMP_TZ, s VARCHAR)");
        engine.execute("INSERT INTO t1 VALUES ('2024-01-05 10:00:00 +25:00', 'a'), ('2024-01-05 10:00:00 -23:30', "
            + "'b'), ('2024-01-05 10:00:00 +03:00', 'c')");
        assertCells(new String[][] {
            {"SELECT x::VARCHAR FROM t1 WHERE s = 'a'",
                "2024-01-05 10:00:00.000 +2500"},
            {"SELECT x::VARCHAR FROM t1 WHERE s = 'b'",
                "2024-01-05 10:00:00.000 -2330"},
            {"SELECT x::TIMESTAMP_NTZ::VARCHAR FROM t1 WHERE s = 'a'",
                "2024-01-05 10:00:00.000"},
            {"SELECT COUNT(*) FROM t1 WHERE x = '2024-01-04 09:00:00 Z'::TIMESTAMP_TZ",
                "1"},
            {"SELECT LISTAGG(s, ',') WITHIN GROUP (ORDER BY x) FROM t1",
                "a,c,b"},
            {"SELECT MAX(x)::VARCHAR FROM t1",
                "2024-01-05 10:00:00.000 -2330"},
            {"SELECT TO_CHAR(x, 'YYYY-MM-DD HH24:MI TZH:TZM') FROM t1 WHERE s = 'b'",
                "2024-01-05 10:00 -23:30"},
        });
    }

    @Test
    public void hourOutputFormat() {
        engine.execute("ALTER SESSION SET TIMESTAMP_TZ_OUTPUT_FORMAT = 'YYYY-MM-DD HH24:MI TZH'");
        assertCells(new String[][] {
            {"SELECT '2024-01-05 10:00:00 +25:00'::TIMESTAMP_TZ::VARCHAR",
                "2024-01-05 10:00 +25"},
            {"SELECT '2024-01-05 10:00:00 -19:00'::TIMESTAMP_TZ::VARCHAR",
                "2024-01-05 10:00 -19"},
            {"SELECT TO_VARCHAR('2024-01-05 10:00:00 +99:59'::TIMESTAMP_TZ)",
                "2024-01-05 10:00 +99"},
        });
    }

    @Test
    public void minuteOutputFormat() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        engine.execute("ALTER SESSION SET TIMESTAMP_TZ_OUTPUT_FORMAT = 'YYYY-MM-DD HH24:MI TZH:TZM'");
        engine.execute("ALTER SESSION SET TIMESTAMP_INPUT_FORMAT = 'YYYY-MM-DD HH24:MI:SS TZH:TZM'");
        assertCells(new String[][] {
            {"SELECT '2024-01-05 10:00:00 +25:30'::TIMESTAMP_TZ::VARCHAR",
                "2024-01-05 10:00 +25:30"},
            {"SELECT '2024-01-05 10:00:00 -20:15'::TIMESTAMP_TZ::VARCHAR",
                "2024-01-05 10:00 -20:15"},
            {"SELECT '2024-01-05 10:00:00 +25:30'::TIMESTAMP_TZ::VARCHAR",
                "2024-01-05 10:00 +25:30"},
            {"SELECT '2024-01-05 10:00:00 +25:30'::TIMESTAMP_NTZ::VARCHAR",
                "2024-01-05 10:00:00.000"},
            {"SELECT '2024-01-05 10:00:00 +25:30'::TIMESTAMP_LTZ::VARCHAR",
                "2024-01-04 00:30:00.000 -0800"},
        });
    }

    @Test
    public void fromPartsZone() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        assertCells(new String[][] {
            {"SELECT TIMESTAMP_TZ_FROM_PARTS(2024, 1, 5, 10, 0, 0, 0, '+05:00')::VARCHAR",
                "REFUSED Unknown timezone: '+05:00'"},
            {"SELECT TIMESTAMP_TZ_FROM_PARTS(2024, 1, 5, 10, 0, 0, 0, 'Europe/Warsaw')::VARCHAR",
                "2024-01-05 10:00:00.000 +0100"},
            {"SELECT TIMESTAMP_TZ_FROM_PARTS(2024, 1, 5, 10, 0, 0, 0, 'Nowhere/City')::VARCHAR",
                "REFUSED Unknown timezone: 'Nowhere/City'"},
            {"SELECT TIMESTAMP_TZ_FROM_PARTS(2024, 1, 5, 10, 0, 0, 0, 'UTC')::VARCHAR",
                "2024-01-05 10:00:00.000 Z"},
            {"SELECT TIMESTAMP_TZ_FROM_PARTS(2024, 1, 5, 10, 0, 0, 0, 'utc')::VARCHAR",
                "REFUSED Unknown timezone: 'utc'"},
            {"SELECT TIMESTAMP_TZ_FROM_PARTS(2024, 1, 5, 10, 0, 0, 0, "
                + "'Frostlake/WideOffset+2500')::VARCHAR",
                "REFUSED Unknown timezone: 'Frostlake/WideOffset+2500'"},
            {"SELECT TIMESTAMP_TZ_FROM_PARTS(2024, 1, 5, 10, 0, 0, 0, 'GMT+2')::VARCHAR",
                "REFUSED Unknown timezone: 'GMT+2'"},
            {"SELECT TIMESTAMP_TZ_FROM_PARTS(2024, 1, 5, 10, 0, 0, 0, 'Z')::VARCHAR",
                "REFUSED Unknown timezone: 'Z'"},
            {"SELECT TIMESTAMP_TZ_FROM_PARTS(2024, 1, 5, 10, 0, 0, 0, 'Etc/GMT+2')::VARCHAR",
                "2024-01-05 10:00:00.000 -0200"},
            {"SELECT TIMESTAMP_TZ_FROM_PARTS(2024, 1, 5, 10, 0, 0, 0, 'America/New_York')::VARCHAR",
                "2024-01-05 10:00:00.000 -0500"},
        });
    }
}
