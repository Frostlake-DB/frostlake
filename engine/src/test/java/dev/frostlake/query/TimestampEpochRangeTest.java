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
 * Epoch numbers and digit strings past the timestamps there are (live-verified): a number of seconds past
 * 999999999-12-31 23:59:59 or before -999999999-01-01 is refused naming the seconds, with the session offset for a
 * TIMESTAMP_TZ, and a digit string past a long still reads in nanoseconds.
 */
public class TimestampEpochRangeTest extends MeasuredCellsTestSupport {

    @Test
    public void epochNumbers() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        assertCells(new String[][] {
            {"SELECT TO_TIMESTAMP(1e17)",
                "REFUSED Timestamp '(seconds_since_epoch=100000000000000000)' is not recognized"},
            {"SELECT TO_TIMESTAMP(9223372036854775807)",
                "REFUSED Timestamp '(seconds_since_epoch=9223372036854775807)' is not recognized"},
            {"SELECT TO_TIMESTAMP(1e19)",
                "REFUSED Timestamp '(seconds_since_epoch=10000000000000000000)' is not recognized"},
            {"SELECT 1e19::TIMESTAMP",
                "REFUSED Timestamp '(seconds_since_epoch=10000000000000000000)' is not recognized"},
            {"SELECT TO_TIMESTAMP_LTZ(1e19)",
                "REFUSED Timestamp '(seconds_since_epoch=10000000000000000000)' is not recognized"},
            {"SELECT TO_TIMESTAMP(1e30, 9)",
                "REFUSED Timestamp '(seconds_since_epoch=1000000000000000000000)' is not recognized"},
            {"SELECT TO_TIMESTAMP(100000000000000000)",
                "REFUSED Timestamp '(seconds_since_epoch=100000000000000000)' is not recognized"},
            {"SELECT TO_TIMESTAMP(-9223372036854775808)",
                "REFUSED Timestamp '(seconds_since_epoch=-9223372036854775808)' is not recognized"},
            {"SELECT (TO_TIMESTAMP(1e15))::VARCHAR",
                "31690708-07-05 01:46:40.000"},
            {"SELECT TRY_TO_TIMESTAMP(1e17)",
                "REFUSED SQL compilation error: | Function TRY_CAST cannot be used with arguments of types "
                + "NUMBER(18,0) and TIMESTAMP_NTZ(0)"},
            {"SELECT TO_TIMESTAMP(1e17::FLOAT)",
                "REFUSED SQL compilation error: | invalid type [TO_TIMESTAMP(TO_DOUBLE(100000000000000000))] "
                + "for parameter 'TO_TIMESTAMP_NTZ'"},
            {"SELECT TO_TIMESTAMP(1e19::FLOAT)",
                "REFUSED SQL compilation error: | invalid type "
                + "[TO_TIMESTAMP(TO_DOUBLE(10000000000000000000))] for parameter 'TO_TIMESTAMP_NTZ'"},
            {"SELECT TO_TIMESTAMP_NTZ(1e19)",
                "REFUSED Timestamp '(seconds_since_epoch=10000000000000000000)' is not recognized"},
            {"SELECT TO_TIMESTAMP_TZ(1e19)",
                "REFUSED Timestamp '(seconds_since_epoch=10000000000000000000)tz=0960' is not recognized"},
            {"SELECT (TO_TIMESTAMP(9223372036854775807, 9))::VARCHAR",
                "2262-04-11 23:47:16.854"},
            {"SELECT (TO_TIMESTAMP(9223372036854775807, 3))::VARCHAR",
                "292278994-08-17 07:12:55.807"},
            {"SELECT TO_TIMESTAMP(1e30, 3)",
                "REFUSED Timestamp '(seconds_since_epoch=1000000000000000000000000000)' is not recognized"},
            {"SELECT 9223372036854775807::TIMESTAMP",
                "REFUSED Timestamp '(seconds_since_epoch=9223372036854775807)' is not recognized"},
            {"SELECT (TO_TIMESTAMP('100000000000000000'))::VARCHAR",
                "1973-03-03 09:46:40.000"},
            {"SELECT (TO_TIMESTAMP('10000000000000000000'))::VARCHAR",
                "2286-11-20 17:46:40.000"},
            {"SELECT TO_DATE(1e17)",
                "REFUSED SQL compilation error: | invalid type [TO_DATE(100000000000000000)] for parameter "
                + "'TO_DATE'"},
            {"SELECT 1e17::TIMESTAMP_TZ",
                "REFUSED Timestamp '(seconds_since_epoch=100000000000000000)tz=1020' is not recognized"},
            {"SELECT (TO_TIMESTAMP(253402300800))::VARCHAR",
                "10000-01-01 00:00:00.000"},
            {"SELECT (TO_TIMESTAMP(253402300799999))::VARCHAR",
                "8031969-03-31 23:59:59.000"},
            {"SELECT TO_TIMESTAMP(31556889864403199)",
                "REFUSED Timestamp '(seconds_since_epoch=31556889864403199)' is not recognized"},
            {"SELECT TO_TIMESTAMP(31556889864403200)",
                "REFUSED Timestamp '(seconds_since_epoch=31556889864403200)' is not recognized"},
            {"SELECT TO_TIMESTAMP(-31557014167219200)",
                "REFUSED Timestamp '(seconds_since_epoch=-31557014167219200)' is not recognized"},
            {"SELECT TO_TIMESTAMP(-1e17)",
                "REFUSED Timestamp '(seconds_since_epoch=-100000000000000000)' is not recognized"},
            {"SELECT TO_TIME(1e19)",
                "REFUSED SQL compilation error: | invalid type [TO_TIME(10000000000000000000)] for parameter "
                + "'TO_TIME'"},
            {"SELECT TO_TIMESTAMP(1e17, 0)",
                "REFUSED Timestamp '(seconds_since_epoch=100000000000000000)' is not recognized"},
            {"SELECT TO_TIMESTAMP(1e38-1)",
                "REFUSED SQL compilation error: | invalid type [TO_TIMESTAMP(1.0E38 - (TO_DOUBLE(1)))] for "
                + "parameter 'TO_TIMESTAMP_NTZ'"},
            {"SELECT TO_TIMESTAMP(1.5e17)",
                "REFUSED Timestamp '(seconds_since_epoch=150000000000000000)' is not recognized"},
            {"SELECT (TO_TIMESTAMP(20000000000000000)::VARCHAR)::VARCHAR",
                "633776740-02-20 11:33:20.000"},
            {"SELECT (TO_TIMESTAMP(30000000000000000)::VARCHAR)::VARCHAR",
                "950664125-03-16 05:20:00.000"},
            {"SELECT TO_TIMESTAMP(50000000000000000)::VARCHAR",
                "REFUSED Timestamp '(seconds_since_epoch=50000000000000000)' is not recognized"},
            {"SELECT TO_TIMESTAMP(70000000000000000)::VARCHAR",
                "REFUSED Timestamp '(seconds_since_epoch=70000000000000000)' is not recognized"},
            {"SELECT TO_TIMESTAMP(90000000000000000)::VARCHAR",
                "REFUSED Timestamp '(seconds_since_epoch=90000000000000000)' is not recognized"},
            {"SELECT TO_TIMESTAMP(-50000000000000000)::VARCHAR",
                "REFUSED Timestamp '(seconds_since_epoch=-50000000000000000)' is not recognized"},
            {"SELECT TO_TIMESTAMP(-90000000000000000)::VARCHAR",
                "REFUSED Timestamp '(seconds_since_epoch=-90000000000000000)' is not recognized"},
            {"SELECT (TO_TIMESTAMP(10000000000000000)::VARCHAR)::VARCHAR",
                "316889355-01-25 17:46:40.000"},
            {"SELECT (TO_TIMESTAMP(31556889832780799)::VARCHAR)::VARCHAR",
                "999999999-12-31 23:59:59.000"},
            {"SELECT TO_TIMESTAMP(31556889832780800)::VARCHAR",
                "REFUSED Timestamp '(seconds_since_epoch=31556889832780800)' is not recognized"},
            {"SELECT TO_TIMESTAMP(-31557014135596801)::VARCHAR",
                "REFUSED Timestamp '(seconds_since_epoch=-31557014135596801)' is not recognized"},
            {"SELECT TO_TIMESTAMP(31556889832780800000, 3)::VARCHAR",
                "REFUSED Timestamp '(seconds_since_epoch=31556889832780800)' is not recognized"},
            {"SELECT (TO_TIMESTAMP(31556889832780799.5)::VARCHAR)::VARCHAR",
                "999999999-12-31 23:59:59.500"},
            {"SELECT (TO_TIMESTAMP(31000000000000000)::VARCHAR)::VARCHAR",
                "982352863-09-17 07:06:40.000"},
            {"SELECT (TO_TIMESTAMP(31500000000000000)::VARCHAR)::VARCHAR",
                "998197232-12-18 08:00:00.000"},
            {"SELECT (TO_TIMESTAMP(31556000000000000)::VARCHAR)::VARCHAR",
                "999971802-04-28 11:33:20.000"},
        });
    }

    @Test
    public void digitStrings() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        assertCells(new String[][] {
            {"SELECT (TO_DATE('10000000000000000000'))::VARCHAR",
                "2286-11-20"},
            {"SELECT (TRY_TO_DATE('10000000000000000000'))::VARCHAR",
                "2286-11-20"},
            {"SELECT (TO_TIMESTAMP('100000000000000000000'))::VARCHAR",
                "5138-11-16 09:46:40.000"},
            {"SELECT ('10000000000000000000'::DATE)::VARCHAR",
                "2286-11-20"},
            {"SELECT (TO_TIMESTAMP('10000000000000000000'))::VARCHAR",
                "2286-11-20 17:46:40.000"},
            {"SELECT (TO_TIMESTAMP('9223372036854775807'))::VARCHAR",
                "2262-04-11 23:47:16.854"},
            {"SELECT (TO_TIMESTAMP('9223372036854775808'))::VARCHAR",
                "2262-04-11 23:47:16.854"},
            {"SELECT (TO_TIMESTAMP('18446744073709551615'))::VARCHAR",
                "2554-07-21 23:34:33.709"},
            {"SELECT (TO_TIMESTAMP('18446744073709551616'))::VARCHAR",
                "2554-07-21 23:34:33.709"},
            {"SELECT (TO_DATE('9223372036854775807'))::VARCHAR",
                "2262-04-11"},
            {"SELECT (TO_DATE('9223372036854775808'))::VARCHAR",
                "2262-04-11"},
            {"SELECT ('100000000000000000000'::TIMESTAMP)::VARCHAR",
                "5138-11-16 09:46:40.000"},
            {"SELECT (TRY_TO_TIMESTAMP('100000000000000000000'))::VARCHAR",
                "5138-11-16 09:46:40.000"},
            {"SELECT (TO_TIMESTAMP_TZ('100000000000000000000'))::VARCHAR",
                "5138-11-16 09:46:40.000 Z"},
            {"SELECT (TO_TIMESTAMP_LTZ('100000000000000000000'))::VARCHAR",
                "5138-11-16 01:46:40.000 -0800"},
            {"SELECT (TO_TIME('100000000000000000000'))::VARCHAR",
                "09:46:40"},
            {"SELECT (TO_DATE('100000000000000000000'))::VARCHAR",
                "5138-11-16"},
            {"SELECT ('-10000000000000000000'::TIMESTAMP)::VARCHAR",
                "1653-02-10 06:13:20.000"},
            {"SELECT (TO_TIMESTAMP('-9223372036854775808'))::VARCHAR",
                "1677-09-21 00:12:43.145"},
            {"SELECT (TO_TIMESTAMP('-9223372036854775809'))::VARCHAR",
                "1677-09-21 00:12:43.145"},
            {"SELECT (TO_DATE('31536000000'))::VARCHAR",
                "1971-01-01"},
            {"SELECT ('18446744073709551616'::DATE)::VARCHAR",
                "2554-07-21"},
            {"SELECT TO_TIMESTAMP_NTZ('99999999999999999999999999999999999999999')",
                "REFUSED Timestamp '99999999999999999999999999999999999999999' is not recognized"},
            {"SELECT (TO_TIME('10000000000000000000'))::VARCHAR",
                "17:46:40"},
            {"SELECT ('10000000000000000000'::TIME)::VARCHAR",
                "17:46:40"},
            {"SELECT (TO_TIMESTAMP('1000000000000000000000000'))::VARCHAR",
                "31690708-07-05 01:46:40.000"},
            {"SELECT TO_TIMESTAMP('1000000000000000000000000000000')",
                "REFUSED Timestamp '1000000000000000000000000000000' is not recognized"},
            {"SELECT TO_TIMESTAMP('99999999999999999999999999999999999999')",
                "REFUSED Timestamp '99999999999999999999999999999999999999' is not recognized"},
            {"SELECT TO_TIMESTAMP('999999999999999999999999999999999999999')",
                "REFUSED Timestamp '999999999999999999999999999999999999999' is not recognized"},
            {"SELECT TO_DATE('1000000000000000000000000000000')",
                "REFUSED Date '1000000000000000000000000000000' is not recognized"},
            {"SELECT TO_TIMESTAMP('170141183460469231731687303715884105727')",
                "REFUSED Timestamp '170141183460469231731687303715884105727' is not recognized"},
            {"SELECT TO_TIMESTAMP('170141183460469231731687303715884105728')",
                "REFUSED Timestamp '170141183460469231731687303715884105728' is not recognized"},
            {"SELECT (TO_TIME('31556889832780800000000000'))::VARCHAR",
                "00:00:00"},
        });
    }
}
