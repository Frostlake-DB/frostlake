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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Three surfaces where a zoned timestamp meets the session's TIMEZONE, each measured in more than one zone
 * (live-verified): a TIMESTAMP_TZ or TIMESTAMP_LTZ compared with text reads the text as an instant (an offset,
 * an RFC or ISO form and an epoch fix it, a bare wall clock is in the session's zone) and text that is no
 * timestamp is refused; CURRENT_DATE, CURRENT_TIME, LOCALTIME, GETDATE, SYSTIMESTAMP and LOCALTIMESTAMP answer
 * in the session's zone while SYSDATE answers UTC, and NOW is no function; and ADD_MONTHS keeps a zoned
 * timestamp's flavour, a TIMESTAMP_TZ at its written offset and a TIMESTAMP_LTZ in the session's zone.
 */
public class ZonedTextAndClockTest extends MeasuredCellsTestSupport {

    /** Puts the session back on its default zone, which each test moved. */
    @AfterEach
    public void restoreZone() {
        engine.execute("ALTER SESSION UNSET TIMEZONE");
    }

    @Test
    public void offsetTextComparesAsInstantUnderUtc() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
        engine.execute("CREATE OR REPLACE TABLE tt(id INT, l TIMESTAMP_LTZ, z TIMESTAMP_TZ, n TIMESTAMP_NTZ)");
        engine.execute("INSERT INTO tt VALUES (1, '2024-01-05 10:00:00 +00:00', '2024-01-05 10:00:00 +02:00', "
            + "'2024-01-05 10:00:00')");
        assertCells(new String[][] {
            {"SELECT z = '2024-01-05 10:00:00 +02:00' FROM tt",
                "true"},
            {"SELECT z = '2024-01-05 08:00:00 +00:00' FROM tt",
                "true"},
            {"SELECT z = '2024-01-05 08:00:00' FROM tt",
                "true"},
            {"SELECT z = '2024-01-05 10:00:00' FROM tt",
                "false"},
            {"SELECT l = '2024-01-05 10:00:00 +00:00' FROM tt",
                "true"},
            {"SELECT l = '2024-01-05 12:00:00 +02:00' FROM tt",
                "true"},
            {"SELECT l = '2024-01-05' FROM tt",
                "false"},
            {"SELECT z > '2024-01-05 09:00:00 +00:00' FROM tt",
                "false"},
            {"SELECT z < '2024-01-05 09:00:00 +00:00' FROM tt",
                "true"},
            {"SELECT z >= '2024-01-05 08:00:00 Z' FROM tt",
                "true"},
            {"SELECT z <> '2024-01-05T08:00:00Z' FROM tt",
                "false"},
            {"SELECT '2024-01-05 09:00:00 +00:00' < z FROM tt",
                "false"},
            {"SELECT '2024-01-05 08:00:00 +00:00' = z FROM tt",
                "true"},
            {"SELECT COUNT(*) FROM tt WHERE z BETWEEN '2024-01-05 07:00:00 +00:00' AND '2024-01-05 "
                + "09:00:00 +00:00'",
                "1"},
            {"SELECT COUNT(*) FROM tt WHERE l BETWEEN '2024-01-05 11:00:00 +02:00' AND '2024-01-05 "
                + "13:00:00 +02:00'",
                "1"},
            {"SELECT COUNT(*) FROM tt WHERE z NOT BETWEEN '2024-01-05 07:00:00 +00:00' AND '2024-01-05 "
                + "09:00:00 +00:00'",
                "0"},
            {"SELECT z IN ('2024-01-05 08:00:00 +00:00', '2024-01-06') FROM tt",
                "true"},
            {"SELECT l IN ('2024-01-05 12:00:00 +02:00') FROM tt",
                "true"},
            {"SELECT l NOT IN ('2024-01-05 11:00:00 +01:00') FROM tt",
                "false"},
            {"SELECT z = 'abc' FROM tt",
                "REFUSED Timestamp 'abc' is not recognized"},
            {"SELECT l = 'abc' FROM tt",
                "REFUSED Timestamp 'abc' is not recognized"},
            {"SELECT l < 'abc' FROM tt",
                "REFUSED Timestamp 'abc' is not recognized"},
            {"SELECT COUNT(*) FROM tt WHERE z BETWEEN 'abc' AND '2024-01-05 09:00:00 +00:00'",
                "REFUSED Timestamp 'abc' is not recognized"},
            {"SELECT z IN ('abc') FROM tt",
                "REFUSED Timestamp 'abc' is not recognized"},
            {"SELECT z = '' FROM tt",
                "REFUSED Timestamp '' is not recognized"},
            {"SELECT z = '2024-13-01 10:00:00 +00:00' FROM tt",
                "REFUSED Timestamp '2024-13-01 10:00:00 +00:00' is not recognized"},
            {"SELECT CASE WHEN l = 'nope' THEN 1 ELSE 0 END FROM tt",
                "REFUSED Timestamp 'nope' is not recognized"},
            {"SELECT CASE z WHEN '2024-01-05 08:00:00 +00:00' THEN 'hit' ELSE 'miss' END FROM tt",
                "hit"},
            {"SELECT DECODE(l, '2024-01-05 12:00:00 +02:00', 'hit', 'miss') FROM tt",
                "hit"},
            {"SELECT NULLIF(z, '2024-01-05 08:00:00 +00:00') FROM tt",
                "null"},
            {"SELECT z = 'Fri, 05 Jan 2024 08:00:00 +0000' FROM tt",
                "true"},
            {"SELECT l = '2024-01-05T10:00:00Z' FROM tt",
                "true"},
            {"SELECT l = '1704448800' FROM tt",
                "true"},
            {"SELECT n = '2024-01-05 10:00:00 +05:00' FROM tt",
                "true"},
        });
    }

    @Test
    public void offsetTextComparesAsInstantUnderKolkata() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'Asia/Kolkata'");
        engine.execute("CREATE OR REPLACE TABLE tt(id INT, l TIMESTAMP_LTZ, z TIMESTAMP_TZ, n TIMESTAMP_NTZ)");
        engine.execute("INSERT INTO tt VALUES (1, '2024-01-05 10:00:00 +00:00', '2024-01-05 10:00:00 +02:00', "
            + "'2024-01-05 10:00:00')");
        assertCells(new String[][] {
            {"SELECT l = '2024-01-05 10:00:00 +00:00' FROM tt",
                "true"},
            {"SELECT l = '2024-01-05 15:30:00' FROM tt",
                "true"},
            {"SELECT l = '2024-01-05 10:00:00' FROM tt",
                "false"},
            {"SELECT z = '2024-01-05 13:30:00' FROM tt",
                "true"},
            {"SELECT l > '2024-01-05 15:00:00' FROM tt",
                "true"},
            {"SELECT z < '2024-01-05 14:00:00' FROM tt",
                "true"},
            {"SELECT COUNT(*) FROM tt WHERE l BETWEEN '2024-01-05 15:00:00' AND '2024-01-05 16:00:00'",
                "1"},
            {"SELECT l IN ('2024-01-05 15:30:00', 'x') FROM tt",
                "true"},
            {"SELECT l = 'abc' FROM tt",
                "REFUSED Timestamp 'abc' is not recognized"},
        });
    }

    @Test
    public void clockFollowsKolkata() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'Asia/Kolkata'");
        engine.execute("CREATE OR REPLACE TABLE dz(id INT, d DATE DEFAULT CURRENT_DATE(), z TIMESTAMP_TZ DEFAULT "
            + "CURRENT_TIMESTAMP(), t TIME DEFAULT CURRENT_TIME(), n TIMESTAMP_NTZ DEFAULT "
            + "CURRENT_TIMESTAMP, l TIMESTAMP_LTZ DEFAULT CURRENT_TIMESTAMP())");
        engine.execute("INSERT INTO dz(id) VALUES (1)");
        assertCells(new String[][] {
            {"SELECT CURRENT_DATE() = CONVERT_TIMEZONE('Asia/Kolkata', CURRENT_TIMESTAMP())::DATE",
                "true"},
            {"SELECT CURRENT_DATE() = CURRENT_TIMESTAMP()::DATE",
                "true"},
            {"SELECT CURRENT_TIME() = CURRENT_TIMESTAMP()::TIME",
                "true"},
            {"SELECT LOCALTIME() = CURRENT_TIMESTAMP()::TIME",
                "true"},
            {"SELECT CURRENT_TIME = CONVERT_TIMEZONE('Asia/Kolkata', CURRENT_TIMESTAMP())::TIME",
                "true"},
            {"SELECT GETDATE() = CURRENT_TIMESTAMP()",
                "true"},
            {"SELECT LOCALTIMESTAMP() = CURRENT_TIMESTAMP()",
                "true"},
            {"SELECT SYSTIMESTAMP() = CURRENT_TIMESTAMP()",
                "true"},
            {"SELECT TO_VARCHAR(GETDATE()) = TO_VARCHAR(CURRENT_TIMESTAMP())",
                "true"},
            {"SELECT TO_VARCHAR(SYSTIMESTAMP()) = TO_VARCHAR(CURRENT_TIMESTAMP())",
                "true"},
            {"SELECT TO_VARCHAR(LOCALTIMESTAMP()) = TO_VARCHAR(CURRENT_TIMESTAMP())",
                "true"},
            {"SELECT RIGHT(TO_VARCHAR(GETDATE()), 5) = RIGHT(TO_VARCHAR(CONVERT_TIMEZONE('Asia/Kolkata', "
                + "CURRENT_TIMESTAMP())), 5)",
                "true"},
            {"SELECT SYSDATE() = CONVERT_TIMEZONE('UTC', CURRENT_TIMESTAMP())::TIMESTAMP_NTZ",
                "true"},
            {"SELECT DATE_PART(epoch_second, GETDATE()) = DATE_PART(epoch_second, CURRENT_TIMESTAMP())",
                "true"},
            {"SELECT DATE_PART(epoch_second, SYSDATE()) = DATE_PART(epoch_second, CURRENT_TIMESTAMP())",
                "true"},
            {"SELECT DATEDIFF(second, CURRENT_TIMESTAMP()::TIMESTAMP_NTZ, (CURRENT_DATE()::VARCHAR || ' ' "
                + "|| CURRENT_TIME()::VARCHAR)::TIMESTAMP_NTZ)",
                "0"},
            {"SELECT SYSTEM$TYPEOF(CURRENT_DATE())",
                "DATE[SB4]"},
            {"SELECT SYSTEM$TYPEOF(LOCALTIME())",
                "TIME(9)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(GETDATE())",
                "TIMESTAMP_LTZ(9)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SYSTIMESTAMP())",
                "TIMESTAMP_LTZ(9)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SYSDATE())",
                "TIMESTAMP_NTZ(9)[SB16]"},
            {"SELECT NOW()",
                "REFUSED SQL compilation error: | Unknown function NOW."},
            {"SELECT d = CONVERT_TIMEZONE('Asia/Kolkata', z)::DATE FROM dz",
                "true"},
            {"SELECT n = l::TIMESTAMP_NTZ FROM dz",
                "true"},
            {"SELECT DATEDIFF(minute, n, CURRENT_TIMESTAMP()::TIMESTAMP_NTZ) < 5 FROM dz",
                "true"},
            {"SELECT RIGHT(TO_VARCHAR(z), 5) = RIGHT(TO_VARCHAR(CONVERT_TIMEZONE('Asia/Kolkata', z)), 5) "
                + "FROM dz",
                "true"},
            {"SELECT ABS(DATEDIFF(minute, t, CONVERT_TIMEZONE('Asia/Kolkata', l)::TIME)) < 2 FROM dz",
                "true"},
        });
    }

    @Test
    public void clockFollowsLosAngeles() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        engine.execute("CREATE OR REPLACE TABLE dz(id INT, d DATE DEFAULT CURRENT_DATE(), z TIMESTAMP_TZ DEFAULT "
            + "CURRENT_TIMESTAMP(), t TIME DEFAULT CURRENT_TIME(), n TIMESTAMP_NTZ DEFAULT "
            + "CURRENT_TIMESTAMP, l TIMESTAMP_LTZ DEFAULT CURRENT_TIMESTAMP())");
        engine.execute("INSERT INTO dz(id) VALUES (1)");
        assertCells(new String[][] {
            {"SELECT CURRENT_DATE() = CONVERT_TIMEZONE('America/Los_Angeles', CURRENT_TIMESTAMP())::DATE",
                "true"},
            {"SELECT CURRENT_DATE() = CURRENT_TIMESTAMP()::DATE",
                "true"},
            {"SELECT CURRENT_TIME() = CURRENT_TIMESTAMP()::TIME",
                "true"},
            {"SELECT LOCALTIME() = CURRENT_TIMESTAMP()::TIME",
                "true"},
            {"SELECT CURRENT_TIME = CONVERT_TIMEZONE('America/Los_Angeles', CURRENT_TIMESTAMP())::TIME",
                "true"},
            {"SELECT GETDATE() = CURRENT_TIMESTAMP()",
                "true"},
            {"SELECT LOCALTIMESTAMP() = CURRENT_TIMESTAMP()",
                "true"},
            {"SELECT SYSTIMESTAMP() = CURRENT_TIMESTAMP()",
                "true"},
            {"SELECT TO_VARCHAR(GETDATE()) = TO_VARCHAR(CURRENT_TIMESTAMP())",
                "true"},
            {"SELECT TO_VARCHAR(SYSTIMESTAMP()) = TO_VARCHAR(CURRENT_TIMESTAMP())",
                "true"},
            {"SELECT TO_VARCHAR(LOCALTIMESTAMP()) = TO_VARCHAR(CURRENT_TIMESTAMP())",
                "true"},
            {"SELECT RIGHT(TO_VARCHAR(GETDATE()), 5) = "
                + "RIGHT(TO_VARCHAR(CONVERT_TIMEZONE('America/Los_Angeles', CURRENT_TIMESTAMP())), 5)",
                "true"},
            {"SELECT SYSDATE() = CONVERT_TIMEZONE('UTC', CURRENT_TIMESTAMP())::TIMESTAMP_NTZ",
                "true"},
            {"SELECT DATE_PART(epoch_second, GETDATE()) = DATE_PART(epoch_second, CURRENT_TIMESTAMP())",
                "true"},
            {"SELECT DATE_PART(epoch_second, SYSDATE()) = DATE_PART(epoch_second, CURRENT_TIMESTAMP())",
                "true"},
            {"SELECT DATEDIFF(second, CURRENT_TIMESTAMP()::TIMESTAMP_NTZ, (CURRENT_DATE()::VARCHAR || ' ' "
                + "|| CURRENT_TIME()::VARCHAR)::TIMESTAMP_NTZ)",
                "0"},
            {"SELECT SYSTEM$TYPEOF(CURRENT_DATE())",
                "DATE[SB4]"},
            {"SELECT SYSTEM$TYPEOF(LOCALTIME())",
                "TIME(9)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(GETDATE())",
                "TIMESTAMP_LTZ(9)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SYSTIMESTAMP())",
                "TIMESTAMP_LTZ(9)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SYSDATE())",
                "TIMESTAMP_NTZ(9)[SB16]"},
            {"SELECT NOW()",
                "REFUSED SQL compilation error: | Unknown function NOW."},
            {"SELECT d = CONVERT_TIMEZONE('America/Los_Angeles', z)::DATE FROM dz",
                "true"},
            {"SELECT n = l::TIMESTAMP_NTZ FROM dz",
                "true"},
            {"SELECT DATEDIFF(minute, n, CURRENT_TIMESTAMP()::TIMESTAMP_NTZ) < 5 FROM dz",
                "true"},
            {"SELECT RIGHT(TO_VARCHAR(z), 5) = RIGHT(TO_VARCHAR(CONVERT_TIMEZONE('America/Los_Angeles', "
                + "z)), 5) FROM dz",
                "true"},
            {"SELECT ABS(DATEDIFF(minute, t, CONVERT_TIMEZONE('America/Los_Angeles', l)::TIME)) < 2 FROM "
                + "dz",
                "true"},
        });
    }

    @Test
    public void clockFollowsUtc() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
        engine.execute("CREATE OR REPLACE TABLE dz(id INT, d DATE DEFAULT CURRENT_DATE(), z TIMESTAMP_TZ DEFAULT "
            + "CURRENT_TIMESTAMP(), t TIME DEFAULT CURRENT_TIME(), n TIMESTAMP_NTZ DEFAULT "
            + "CURRENT_TIMESTAMP, l TIMESTAMP_LTZ DEFAULT CURRENT_TIMESTAMP())");
        engine.execute("INSERT INTO dz(id) VALUES (1)");
        assertCells(new String[][] {
            {"SELECT CURRENT_DATE() = CONVERT_TIMEZONE('UTC', CURRENT_TIMESTAMP())::DATE",
                "true"},
            {"SELECT CURRENT_DATE() = CURRENT_TIMESTAMP()::DATE",
                "true"},
            {"SELECT CURRENT_TIME() = CURRENT_TIMESTAMP()::TIME",
                "true"},
            {"SELECT LOCALTIME() = CURRENT_TIMESTAMP()::TIME",
                "true"},
            {"SELECT CURRENT_TIME = CONVERT_TIMEZONE('UTC', CURRENT_TIMESTAMP())::TIME",
                "true"},
            {"SELECT GETDATE() = CURRENT_TIMESTAMP()",
                "true"},
            {"SELECT LOCALTIMESTAMP() = CURRENT_TIMESTAMP()",
                "true"},
            {"SELECT SYSTIMESTAMP() = CURRENT_TIMESTAMP()",
                "true"},
            {"SELECT TO_VARCHAR(GETDATE()) = TO_VARCHAR(CURRENT_TIMESTAMP())",
                "true"},
            {"SELECT TO_VARCHAR(SYSTIMESTAMP()) = TO_VARCHAR(CURRENT_TIMESTAMP())",
                "true"},
            {"SELECT TO_VARCHAR(LOCALTIMESTAMP()) = TO_VARCHAR(CURRENT_TIMESTAMP())",
                "true"},
            {"SELECT RIGHT(TO_VARCHAR(GETDATE()), 5) = RIGHT(TO_VARCHAR(CONVERT_TIMEZONE('UTC', "
                + "CURRENT_TIMESTAMP())), 5)",
                "true"},
            {"SELECT SYSDATE() = CONVERT_TIMEZONE('UTC', CURRENT_TIMESTAMP())::TIMESTAMP_NTZ",
                "true"},
            {"SELECT DATE_PART(epoch_second, GETDATE()) = DATE_PART(epoch_second, CURRENT_TIMESTAMP())",
                "true"},
            {"SELECT DATE_PART(epoch_second, SYSDATE()) = DATE_PART(epoch_second, CURRENT_TIMESTAMP())",
                "true"},
            {"SELECT DATEDIFF(second, CURRENT_TIMESTAMP()::TIMESTAMP_NTZ, (CURRENT_DATE()::VARCHAR || ' ' "
                + "|| CURRENT_TIME()::VARCHAR)::TIMESTAMP_NTZ)",
                "0"},
            {"SELECT SYSTEM$TYPEOF(CURRENT_DATE())",
                "DATE[SB4]"},
            {"SELECT SYSTEM$TYPEOF(LOCALTIME())",
                "TIME(9)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(GETDATE())",
                "TIMESTAMP_LTZ(9)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SYSTIMESTAMP())",
                "TIMESTAMP_LTZ(9)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(SYSDATE())",
                "TIMESTAMP_NTZ(9)[SB16]"},
            {"SELECT NOW()",
                "REFUSED SQL compilation error: | Unknown function NOW."},
            {"SELECT d = CONVERT_TIMEZONE('UTC', z)::DATE FROM dz",
                "true"},
            {"SELECT n = l::TIMESTAMP_NTZ FROM dz",
                "true"},
            {"SELECT DATEDIFF(minute, n, CURRENT_TIMESTAMP()::TIMESTAMP_NTZ) < 5 FROM dz",
                "true"},
            {"SELECT RIGHT(TO_VARCHAR(z), 5) = RIGHT(TO_VARCHAR(CONVERT_TIMEZONE('UTC', z)), 5) FROM dz",
                "true"},
            {"SELECT ABS(DATEDIFF(minute, t, CONVERT_TIMEZONE('UTC', l)::TIME)) < 2 FROM dz",
                "true"},
        });
    }

    @Test
    public void addMonthsKeepsZoneUnderNewYork() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/New_York'");
        engine.execute("CREATE OR REPLACE TABLE am(id INT, z TIMESTAMP_TZ, l TIMESTAMP_LTZ, n TIMESTAMP_NTZ)");
        engine.execute("INSERT INTO am VALUES (1, '2024-03-15 02:00:00 +09:00', '2024-03-15 02:00:00 +00:00', "
            + "'2024-03-15 02:00:00'), (2, '2024-01-31 23:30:00 -05:00', '2024-01-31 23:30:00 +00:00', "
            + "'2024-01-31 23:30:00'), (3, '2024-02-29 10:00:00 +05:30', '2024-02-29 10:00:00 +05:30', "
            + "'2024-02-29 10:00:00')");
        engine.execute("CREATE OR REPLACE TABLE am2 AS SELECT id, ADD_MONTHS(z, 1) az, ADD_MONTHS(l, 1) al FROM am");
        assertCells(new String[][] {
            {"SELECT TO_VARCHAR(ADD_MONTHS(z, 1)) FROM am WHERE id = 1",
                "2024-04-15 02:00:00.000 +0900"},
            {"SELECT TO_VARCHAR(ADD_MONTHS(l, 1)) FROM am WHERE id = 1",
                "2024-04-14 22:00:00.000 -0400"},
            {"SELECT ADD_MONTHS(z, 1) = DATEADD(month, 1, z) FROM am WHERE id = 1",
                "true"},
            {"SELECT ADD_MONTHS(l, 1) = DATEADD(month, 1, l) FROM am WHERE id = 1",
                "true"},
            {"SELECT DATE_PART(epoch_second, ADD_MONTHS(z, 1)) FROM am WHERE id = 1",
                "1713114000"},
            {"SELECT DATE_PART(epoch_second, ADD_MONTHS(l, 1)) FROM am WHERE id = 1",
                "1713146400"},
            {"SELECT TO_VARCHAR(ADD_MONTHS(z, -1)) FROM am WHERE id = 1",
                "2024-02-15 02:00:00.000 +0900"},
            {"SELECT TO_VARCHAR(ADD_MONTHS(l, -13)) FROM am WHERE id = 1",
                "2023-02-14 22:00:00.000 -0500"},
            {"SELECT TO_VARCHAR(ADD_MONTHS(n, 1)) FROM am WHERE id = 1",
                "2024-04-15 02:00:00.000"},
            {"SELECT TO_VARCHAR(ADD_MONTHS(z, 12)) FROM am WHERE id = 1",
                "2025-03-15 02:00:00.000 +0900"},
            {"SELECT TO_VARCHAR(DATE_TRUNC(month, z)) FROM am WHERE id = 1",
                "2024-03-01 00:00:00.000 +0900"},
            {"SELECT TO_VARCHAR(DATE_TRUNC(day, l)) FROM am WHERE id = 1",
                "2024-03-14 00:00:00.000 -0400"},
            {"SELECT LAST_DAY(z) FROM am WHERE id = 1",
                "2024-03-31"},
            {"SELECT LAST_DAY(l) FROM am WHERE id = 1",
                "2024-03-31"},
            {"SELECT TO_VARCHAR(az) FROM am2 WHERE id = 1",
                "2024-04-15 02:00:00.000 +0900"},
            {"SELECT TO_VARCHAR(al) FROM am2 WHERE id = 1",
                "2024-04-14 22:00:00.000 -0400"},
            {"SELECT SYSTEM$TYPEOF(az) FROM am2 WHERE id = 1",
                "TIMESTAMP_TZ(9)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(al) FROM am2 WHERE id = 1",
                "TIMESTAMP_LTZ(9)[SB16]"},
            {"SELECT TO_VARCHAR(ADD_MONTHS(z, 1)) FROM am WHERE id = 2",
                "2024-02-29 23:30:00.000 -0500"},
            {"SELECT TO_VARCHAR(ADD_MONTHS(l, 1)) FROM am WHERE id = 2",
                "2024-02-29 18:30:00.000 -0500"},
            {"SELECT ADD_MONTHS(z, 1) = DATEADD(month, 1, z) FROM am WHERE id = 2",
                "true"},
            {"SELECT ADD_MONTHS(l, 1) = DATEADD(month, 1, l) FROM am WHERE id = 2",
                "true"},
            {"SELECT DATE_PART(epoch_second, ADD_MONTHS(z, 1)) FROM am WHERE id = 2",
                "1709267400"},
            {"SELECT DATE_PART(epoch_second, ADD_MONTHS(l, 1)) FROM am WHERE id = 2",
                "1709249400"},
            {"SELECT TO_VARCHAR(ADD_MONTHS(z, -1)) FROM am WHERE id = 2",
                "2023-12-31 23:30:00.000 -0500"},
            {"SELECT TO_VARCHAR(ADD_MONTHS(l, -13)) FROM am WHERE id = 2",
                "2022-12-31 18:30:00.000 -0500"},
            {"SELECT TO_VARCHAR(ADD_MONTHS(n, 1)) FROM am WHERE id = 2",
                "2024-02-29 23:30:00.000"},
            {"SELECT TO_VARCHAR(ADD_MONTHS(z, 12)) FROM am WHERE id = 2",
                "2025-01-31 23:30:00.000 -0500"},
            {"SELECT TO_VARCHAR(DATE_TRUNC(month, z)) FROM am WHERE id = 2",
                "2024-01-01 00:00:00.000 -0500"},
            {"SELECT TO_VARCHAR(DATE_TRUNC(day, l)) FROM am WHERE id = 2",
                "2024-01-31 00:00:00.000 -0500"},
            {"SELECT LAST_DAY(z) FROM am WHERE id = 2",
                "2024-01-31"},
            {"SELECT LAST_DAY(l) FROM am WHERE id = 2",
                "2024-01-31"},
            {"SELECT TO_VARCHAR(az) FROM am2 WHERE id = 2",
                "2024-02-29 23:30:00.000 -0500"},
            {"SELECT TO_VARCHAR(al) FROM am2 WHERE id = 2",
                "2024-02-29 18:30:00.000 -0500"},
            {"SELECT SYSTEM$TYPEOF(az) FROM am2 WHERE id = 2",
                "TIMESTAMP_TZ(9)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(al) FROM am2 WHERE id = 2",
                "TIMESTAMP_LTZ(9)[SB16]"},
            {"SELECT TO_VARCHAR(ADD_MONTHS(z, 1)) FROM am WHERE id = 3",
                "2024-03-31 10:00:00.000 +0530"},
            {"SELECT TO_VARCHAR(ADD_MONTHS(l, 1)) FROM am WHERE id = 3",
                "2024-03-28 23:30:00.000 -0400"},
            {"SELECT ADD_MONTHS(z, 1) = DATEADD(month, 1, z) FROM am WHERE id = 3",
                "false"},
            {"SELECT ADD_MONTHS(l, 1) = DATEADD(month, 1, l) FROM am WHERE id = 3",
                "true"},
            {"SELECT DATE_PART(epoch_second, ADD_MONTHS(z, 1)) FROM am WHERE id = 3",
                "1711859400"},
            {"SELECT DATE_PART(epoch_second, ADD_MONTHS(l, 1)) FROM am WHERE id = 3",
                "1711683000"},
            {"SELECT TO_VARCHAR(ADD_MONTHS(z, -1)) FROM am WHERE id = 3",
                "2024-01-31 10:00:00.000 +0530"},
            {"SELECT TO_VARCHAR(ADD_MONTHS(l, -13)) FROM am WHERE id = 3",
                "2023-01-28 23:30:00.000 -0500"},
            {"SELECT TO_VARCHAR(ADD_MONTHS(n, 1)) FROM am WHERE id = 3",
                "2024-03-31 10:00:00.000"},
            {"SELECT TO_VARCHAR(ADD_MONTHS(z, 12)) FROM am WHERE id = 3",
                "2025-02-28 10:00:00.000 +0530"},
            {"SELECT TO_VARCHAR(DATE_TRUNC(month, z)) FROM am WHERE id = 3",
                "2024-02-01 00:00:00.000 +0530"},
            {"SELECT TO_VARCHAR(DATE_TRUNC(day, l)) FROM am WHERE id = 3",
                "2024-02-28 00:00:00.000 -0500"},
            {"SELECT LAST_DAY(z) FROM am WHERE id = 3",
                "2024-02-29"},
            {"SELECT LAST_DAY(l) FROM am WHERE id = 3",
                "2024-02-29"},
            {"SELECT TO_VARCHAR(az) FROM am2 WHERE id = 3",
                "2024-03-31 10:00:00.000 +0530"},
            {"SELECT TO_VARCHAR(al) FROM am2 WHERE id = 3",
                "2024-03-28 23:30:00.000 -0400"},
            {"SELECT SYSTEM$TYPEOF(az) FROM am2 WHERE id = 3",
                "TIMESTAMP_TZ(9)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(al) FROM am2 WHERE id = 3",
                "TIMESTAMP_LTZ(9)[SB16]"},
            {"SELECT TO_VARCHAR(ADD_MONTHS('2024-01-31 10:00:00 +02:00'::TIMESTAMP_TZ, 1))",
                "2024-02-29 10:00:00.000 +0200"},
            {"SELECT TO_VARCHAR(ADD_MONTHS('2024-02-29 10:00:00 +02:00'::TIMESTAMP_TZ, 12))",
                "2025-02-28 10:00:00.000 +0200"},
            {"SELECT TO_VARCHAR(ADD_MONTHS('2024-04-30 10:00:00'::TIMESTAMP_LTZ, 1))",
                "2024-05-31 10:00:00.000 -0400"},
            {"SELECT TO_VARCHAR(ADD_MONTHS('2024-02-10 02:30:00'::TIMESTAMP_LTZ, 1))",
                "2024-03-10 03:30:00.000 -0400"},
            {"SELECT TO_VARCHAR(ADD_MONTHS('2024-10-03 01:30:00'::TIMESTAMP_LTZ, 1))",
                "2024-11-03 01:30:00.000 -0400"},
            {"SELECT ADD_MONTHS(NULL::TIMESTAMP_LTZ, 1)",
                "null"},
            {"SELECT SYSTEM$TYPEOF(ADD_MONTHS('2024-01-31 10:00:00 +02:00'::TIMESTAMP_TZ, 1))",
                "TIMESTAMP_TZ(9)[SB16]"},
        });
    }

    @Test
    public void addMonthsKeepsZoneUnderKolkata() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'Asia/Kolkata'");
        engine.execute("CREATE OR REPLACE TABLE am(id INT, z TIMESTAMP_TZ, l TIMESTAMP_LTZ, n TIMESTAMP_NTZ)");
        engine.execute("INSERT INTO am VALUES (1, '2024-03-15 02:00:00 +09:00', '2024-03-15 02:00:00 +00:00', "
            + "'2024-03-15 02:00:00'), (2, '2024-01-31 23:30:00 -05:00', '2024-01-31 23:30:00 +00:00', "
            + "'2024-01-31 23:30:00'), (3, '2024-02-29 10:00:00 +05:30', '2024-02-29 10:00:00 +05:30', "
            + "'2024-02-29 10:00:00')");
        assertCells(new String[][] {
            {"SELECT TO_VARCHAR(ADD_MONTHS(z, 2)) FROM am WHERE id = 1",
                "2024-05-15 02:00:00.000 +0900"},
            {"SELECT TO_VARCHAR(ADD_MONTHS(l, 2)) FROM am WHERE id = 1",
                "2024-05-15 07:30:00.000 +0530"},
            {"SELECT ADD_MONTHS(l, 1) = DATEADD(month, 1, l) FROM am WHERE id = 1",
                "true"},
            {"SELECT TO_VARCHAR(DATE_TRUNC(month, l)) FROM am WHERE id = 1",
                "2024-03-01 00:00:00.000 +0530"},
            {"SELECT TO_VARCHAR(ADD_MONTHS(z, 2)) FROM am WHERE id = 2",
                "2024-03-31 23:30:00.000 -0500"},
            {"SELECT TO_VARCHAR(ADD_MONTHS(l, 2)) FROM am WHERE id = 2",
                "2024-04-01 05:00:00.000 +0530"},
            {"SELECT ADD_MONTHS(l, 1) = DATEADD(month, 1, l) FROM am WHERE id = 2",
                "true"},
            {"SELECT TO_VARCHAR(DATE_TRUNC(month, l)) FROM am WHERE id = 2",
                "2024-02-01 00:00:00.000 +0530"},
            {"SELECT TO_VARCHAR(ADD_MONTHS(z, 2)) FROM am WHERE id = 3",
                "2024-04-30 10:00:00.000 +0530"},
            {"SELECT TO_VARCHAR(ADD_MONTHS(l, 2)) FROM am WHERE id = 3",
                "2024-04-30 10:00:00.000 +0530"},
            {"SELECT ADD_MONTHS(l, 1) = DATEADD(month, 1, l) FROM am WHERE id = 3",
                "false"},
            {"SELECT TO_VARCHAR(DATE_TRUNC(month, l)) FROM am WHERE id = 3",
                "2024-02-01 00:00:00.000 +0530"},
            {"SELECT TO_VARCHAR(ADD_MONTHS('2024-05-31 21:00:00 +00:00'::TIMESTAMP_LTZ, 1))",
                "2024-07-01 02:30:00.000 +0530"},
        });
    }
}
