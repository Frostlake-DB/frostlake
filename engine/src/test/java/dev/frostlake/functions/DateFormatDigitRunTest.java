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
 * A date or time format read against a text whose digits run out (live-verified): a two-digit element reads two
 * digits when its field holds them and gives none back to what follows, and a space of the format left out of the
 * text never splits a run of digits.
 */
public class DateFormatDigitRunTest extends MeasuredCellsTestSupport {

    @Test
    public void digitRuns() {
        assertCells(new String[][] {
            {"SELECT TO_TIME('1000', 'HH24 MI')::VARCHAR",
                "REFUSED Can't parse '1000' as time with format 'HH24 MI'"},
            {"SELECT TO_TIME('10 00', 'HH24 MI')::VARCHAR",
                "10:00:00"},
            {"SELECT TO_TIME('10:0005', 'HH24:MISS')::VARCHAR",
                "10:00:05"},
            {"SELECT TO_DATE('2020-01-15Jan', 'YYYY-MM-DD MON')::VARCHAR",
                "2020-01-15"},
            {"SELECT TO_DATE('Jan2020', 'MON YYYY')::VARCHAR",
                "2020-01-01"},
            {"SELECT TO_DATE('202001', 'YYYY MM')::VARCHAR",
                "REFUSED Can't parse '202001' as date with format 'YYYY MM'"},
            {"SELECT TO_TIME('10:00 PM', 'HH12:MI AM')::VARCHAR",
                "22:00:00"},
            {"SELECT TO_TIME('10:00PM', 'HH12:MI AM')::VARCHAR",
                "22:00:00"},
            {"SELECT TO_TIME('1:5', 'HH24:MI')::VARCHAR",
                "01:05:00"},
            {"SELECT TO_TIME('105', 'HH24MI')::VARCHAR",
                "10:05:00"},
            {"SELECT TO_TIME('10 :00', 'HH24:MI')::VARCHAR",
                "REFUSED Can't parse '10 :00' as time with format 'HH24:MI'"},
            {"SELECT TO_DATE('2020-01-15 ', 'YYYY-MM-DD')::VARCHAR",
                "2020-01-15"},
            {"SELECT TO_TIME('099', 'HH24MI')::VARCHAR",
                "09:09:00"},
            {"SELECT TO_TIME('10:00:00.5', 'HH24:MI:SS.FF')::VARCHAR",
                "10:00:00"},
            {"SELECT TO_TIME('10:005', 'HH24:MISS')::VARCHAR",
                "10:00:05"},
            {"SELECT TO_TIME('123', 'HH24MISS')::VARCHAR",
                "REFUSED Can't parse '123' as time with format 'HH24MISS'"},
            {"SELECT TO_TIMESTAMP_NTZ('2020011510', 'YYYYMMDDHH24MI')::VARCHAR",
                "REFUSED Can't parse '2020011510' as timestamp with format 'YYYYMMDDHH24MI'"},
            {"SELECT TO_DATE('202011', 'YYYYMMDD')::VARCHAR",
                "REFUSED Can't parse '202011' as date with format 'YYYYMMDD'"},
            {"SELECT TO_DATE('1152020', 'MMDDYYYY')::VARCHAR",
                "2020-11-05"},
            {"SELECT TO_TIME('1234', 'HH24MISS')::VARCHAR",
                "REFUSED Can't parse '1234' as time with format 'HH24MISS'"},
            {"SELECT TO_TIME('12:3', 'HH24:MISS')::VARCHAR",
                "REFUSED Can't parse '12:3' as time with format 'HH24:MISS'"},
            {"SELECT TO_DATE('2020-1-15', 'YYYY-MMDD')::VARCHAR",
                "REFUSED Can't parse '2020-1-15' as date with format 'YYYY-MMDD'"},
            {"SELECT TO_DATE('2020-11-5', 'YYYY-MMDD')::VARCHAR",
                "REFUSED Can't parse '2020-11-5' as date with format 'YYYY-MMDD'"},
            {"SELECT TO_DATE('2020-115', 'YYYY-MMDD')::VARCHAR",
                "2020-11-05"},
            {"SELECT TO_TIME('10:00', 'HH24:MI SS')::VARCHAR",
                "REFUSED Can't parse '10:00' as time with format 'HH24:MI SS'"},
            {"SELECT TO_TIME('10:005', 'HH24:MI SS')::VARCHAR",
                "REFUSED Can't parse '10:005' as time with format 'HH24:MI SS'"},
            {"SELECT TO_TIME('10:00 5', 'HH24:MI SS')::VARCHAR",
                "10:00:05"},
            {"SELECT TO_DATE('2020-01-15', 'YYYY-MM-DD DY')::VARCHAR",
                "REFUSED Can't parse '2020-01-15' as date with format 'YYYY-MM-DD DY'"},
            {"SELECT TO_TIMESTAMP_NTZ('2020-01-15 10', 'YYYY-MM-DD HH24:MI')::VARCHAR",
                "REFUSED Can't parse '2020-01-15 10' as timestamp with format 'YYYY-MM-DD HH24:MI'"},
            {"SELECT TO_TIMESTAMP_NTZ('2020-01-15 10:3', 'YYYY-MM-DD HH24:MISS')::VARCHAR",
                "REFUSED Can't parse '2020-01-15 10:3' as timestamp with format 'YYYY-MM-DD HH24:MISS'"},
            {"SELECT TO_DATE('20200115', 'YYYYMMDD')::VARCHAR",
                "2020-01-15"},
            {"SELECT TO_DATE('2020015', 'YYYYMMDD')::VARCHAR",
                "2020-01-05"},
            {"SELECT TO_DATE('2020151', 'YYYYDDMM')::VARCHAR",
                "2020-01-15"},
            {"SELECT TO_DATE('2020-01-15', 'YYYY-MM-DD HH24')::VARCHAR",
                "REFUSED Can't parse '2020-01-15' as date with format 'YYYY-MM-DD HH24'"},
            {"SELECT TO_DATE('2020-01-15 10', 'YYYY-MM-DD HH24')::VARCHAR",
                "2020-01-15"},
            {"SELECT TO_DATE('2020-01-15', 'YYYY-MM-DD HH24:MI')::VARCHAR",
                "REFUSED Can't parse '2020-01-15' as date with format 'YYYY-MM-DD HH24:MI'"},
            {"SELECT TO_DATE('2020-01-15', 'YYYY-MM-DDHH24')::VARCHAR",
                "REFUSED Can't parse '2020-01-15' as date with format 'YYYY-MM-DDHH24'"},
            {"SELECT TO_TIMESTAMP_NTZ('2020-01-15', 'YYYY-MM-DD HH24')::VARCHAR",
                "REFUSED Can't parse '2020-01-15' as timestamp with format 'YYYY-MM-DD HH24'"},
            {"SELECT TO_DATE('2020-01-15 ', 'YYYY-MM-DD HH24')::VARCHAR",
                "REFUSED Can't parse '2020-01-15 ' as date with format 'YYYY-MM-DD HH24'"},
            {"SELECT TO_DATE('2020-01-15', 'YYYY-MM-DD MI')::VARCHAR",
                "REFUSED Can't parse '2020-01-15' as date with format 'YYYY-MM-DD MI'"},
            {"SELECT TO_TIME('10:00', 'HH24:MI:SS')::VARCHAR",
                "REFUSED Can't parse '10:00' as time with format 'HH24:MI:SS'"},
            {"SELECT TO_DATE('2020-01', 'YYYY-MM-DD')::VARCHAR",
                "REFUSED Can't parse '2020-01' as date with format 'YYYY-MM-DD'"},
            {"SELECT TO_DATE('2020-01-15', 'YYYY-MM-DD AM')::VARCHAR",
                "REFUSED Can't parse '2020-01-15' as date with format 'YYYY-MM-DD AM'"},
            {"SELECT TRY_TO_DATE('2020-01-15', 'YYYY-MM-DD HH24')::VARCHAR",
                "null"},
            {"SELECT TO_DATE('2020-01-1510', 'YYYY-MM-DDHH24')::VARCHAR",
                "2020-01-15"},
        });
    }
}
