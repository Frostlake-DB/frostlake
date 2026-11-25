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

package dev.frostlake.expressions;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * GREATEST, LEAST and their IGNORE_NULLS spellings beside a TIMESTAMP_TZ or TIMESTAMP_LTZ read a text argument as a
 * timestamp of that flavour (live-verified): an offset, an RFC or ISO form and an epoch fix the instant, a bare wall
 * clock or a date is read in the session's zone, a TIMESTAMP_TZ keeps the offset the text was written with, and text
 * that is no timestamp is refused as one. A naive timestamp or a date beside a zoned one is read in the session's zone
 * too, and a TIMESTAMP_LTZ beside a TIMESTAMP_TZ answers as a TIMESTAMP_TZ at the session's offset.
 */
public class ZonedTimestampTextExtremeTest extends MeasuredCellsTestSupport {

    @Test
    public void sessionZoneLosAngeles() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        engine.execute("CREATE OR REPLACE TABLE t(z TIMESTAMP_TZ, l TIMESTAMP_LTZ, n TIMESTAMP_NTZ, d DATE, tm TIME,"
            + " s VARCHAR, v VARIANT)");
        engine.execute("INSERT INTO t SELECT '2024-07-01 12:00:00 +00:00', '2024-07-01 12:00:00 +00:00', '2024-07-01"
            + " 12:00:00', '2024-07-01', '12:00:00', '2024-07-01 13:00:00 +00:00', TO_VARIANT('2024-07-01 "
            + "13:00:00 +00:00')");
        assertCells(new String[][] {
            {"SELECT (GREATEST(z, '2024-07-01 13:00:00 +00:00'))::VARCHAR FROM t",
                "2024-07-01 13:00:00.000 Z"},
            {"SELECT (LEAST(z, '2024-07-01 13:00:00 +00:00'))::VARCHAR FROM t",
                "2024-07-01 12:00:00.000 Z"},
            {"SELECT (GREATEST('2024-07-01 13:00:00 +00:00', z))::VARCHAR FROM t",
                "2024-07-01 13:00:00.000 Z"},
            {"SELECT (GREATEST(z, '2024-07-01 06:00:00'))::VARCHAR FROM t",
                "2024-07-01 06:00:00.000 -0700"},
            {"SELECT (LEAST(z, '2024-07-01 04:00:00'))::VARCHAR FROM t",
                "2024-07-01 04:00:00.000 -0700"},
            {"SELECT (GREATEST(z, '2024-07-02'))::VARCHAR FROM t",
                "2024-07-02 00:00:00.000 -0700"},
            {"SELECT (LEAST(z, '2024-06-30'))::VARCHAR FROM t",
                "2024-06-30 00:00:00.000 -0700"},
            {"SELECT (GREATEST(z, '2024-07-01T13:00:00Z'))::VARCHAR FROM t",
                "2024-07-01 13:00:00.000 Z"},
            {"SELECT (GREATEST(z, '2024-07-01 15:00:00 +02:00'))::VARCHAR FROM t",
                "2024-07-01 15:00:00.000 +0200"},
            {"SELECT (GREATEST(z, 'nope'))::VARCHAR FROM t",
                "REFUSED Timestamp 'nope' is not recognized"},
            {"SELECT (GREATEST(z, s))::VARCHAR FROM t",
                "2024-07-01 13:00:00.000 Z"},
            {"SELECT (LEAST(z, s))::VARCHAR FROM t",
                "2024-07-01 12:00:00.000 Z"},
            {"SELECT SYSTEM$TYPEOF(GREATEST('2024-07-01 13:00:00 +00:00', z)) FROM t",
                "TIMESTAMP_TZ(9)[SB16]"},
            {"SELECT (GREATEST(z, '2024-07-01 13:00:00 +00:00', '2024-07-01 14:00:00 +00:00'))::VARCHAR "
                + "FROM t",
                "2024-07-01 14:00:00.000 Z"},
            {"SELECT (LEAST(z, NULL, '2024-07-01'))::VARCHAR FROM t",
                "null"},
            {"SELECT (GREATEST(z, ''))::VARCHAR FROM t",
                "REFUSED Timestamp '' is not recognized"},
            {"SELECT (LEAST(z, '1720000000'))::VARCHAR FROM t",
                "2024-07-01 12:00:00.000 Z"},
            {"SELECT (GREATEST(z, '2024-07-01 15:00:00 +02:00', '2024-07-01 14:00:00 +01:00'))::VARCHAR "
                + "FROM t",
                "2024-07-01 15:00:00.000 +0200"},
            {"SELECT (GREATEST(z, 'Mon, 01 Jul 2024 13:00:00 +0000'))::VARCHAR FROM t",
                "2024-07-01 13:00:00.000 Z"},
            {"SELECT (LEAST(z, '2024-07-01 11:00:00.5 -00:00'))::VARCHAR FROM t",
                "2024-07-01 11:00:00.500 Z"},
            {"SELECT (GREATEST(z, v))::VARCHAR FROM t",
                "2024-07-01 13:00:00.000 Z"},
            {"SELECT (GREATEST(l, '2024-07-01 13:00:00 +00:00'))::VARCHAR FROM t",
                "2024-07-01 06:00:00.000 -0700"},
            {"SELECT (LEAST(l, '2024-07-01 04:00:00'))::VARCHAR FROM t",
                "2024-07-01 04:00:00.000 -0700"},
            {"SELECT (GREATEST('2024-07-01 06:00:00', l))::VARCHAR FROM t",
                "2024-07-01 06:00:00.000 -0700"},
            {"SELECT (LEAST('2024-07-01', l))::VARCHAR FROM t",
                "2024-07-01 00:00:00.000 -0700"},
            {"SELECT SYSTEM$TYPEOF(LEAST('x', l)) FROM t",
                "TIMESTAMP_LTZ(9)[SB16]"},
            {"SELECT (GREATEST(l, s))::VARCHAR FROM t",
                "2024-07-01 06:00:00.000 -0700"},
            {"SELECT (GREATEST(l, 'nope'))::VARCHAR FROM t",
                "REFUSED Timestamp 'nope' is not recognized"},
            {"SELECT (GREATEST(l, '2024-07-01 15:00:00 +02:00'))::VARCHAR FROM t",
                "2024-07-01 06:00:00.000 -0700"},
            {"SELECT (LEAST(l, v))::VARCHAR FROM t",
                "2024-07-01 05:00:00.000 -0700"},
            {"SELECT (GREATEST_IGNORE_NULLS(z, '2024-07-01 13:00:00 +00:00', NULL))::VARCHAR FROM t",
                "2024-07-01 13:00:00.000 Z"},
            {"SELECT (LEAST_IGNORE_NULLS(l, NULL, '2024-07-01 04:00:00'))::VARCHAR FROM t",
                "2024-07-01 04:00:00.000 -0700"},
            {"SELECT (LEAST_IGNORE_NULLS(z, s))::VARCHAR FROM t",
                "2024-07-01 12:00:00.000 Z"},
            {"SELECT (GREATEST_IGNORE_NULLS(l, 'nope'))::VARCHAR FROM t",
                "REFUSED Timestamp 'nope' is not recognized"},
            {"SELECT SYSTEM$TYPEOF(GREATEST_IGNORE_NULLS(z, '2024-07-01 13:00:00 +00:00')) FROM t",
                "TIMESTAMP_TZ(9)[SB16]"},
            {"SELECT (GREATEST('2024-07-01 12:00:00 +00:00'::TIMESTAMP_TZ, '2024-07-01 13:00:00 "
                + "+00:00'))::VARCHAR",
                "2024-07-01 13:00:00.000 Z"},
            {"SELECT (LEAST('2024-07-01 12:00:00'::TIMESTAMP_LTZ, '2024-07-01 04:00:00'))::VARCHAR",
                "2024-07-01 04:00:00.000 -0700"},
            {"SELECT (GREATEST(z, l, '2024-07-01 13:00:00'))::VARCHAR FROM t",
                "2024-07-01 13:00:00.000 -0700"},
            {"SELECT (GREATEST(z, n, '2024-07-01 13:00:00'))::VARCHAR FROM t",
                "2024-07-01 13:00:00.000 -0700"},
            {"SELECT (GREATEST(d, z, '2024-07-01 13:00:00'))::VARCHAR FROM t",
                "2024-07-01 13:00:00.000 -0700"},
            {"SELECT (LEAST('2024-07-01 12:00:00 +02:00', z, l))::VARCHAR FROM t",
                "2024-07-01 12:00:00.000 +0200"},
            {"SELECT SYSTEM$TYPEOF(GREATEST(z, l, '2024-07-01 13:00:00')) FROM t",
                "TIMESTAMP_TZ(9)[SB16]"},
            {"SELECT (GREATEST(z, n))::VARCHAR FROM t",
                "2024-07-01 12:00:00.000 -0700"},
            {"SELECT (LEAST(z, n))::VARCHAR FROM t",
                "2024-07-01 12:00:00.000 Z"},
            {"SELECT (GREATEST(l, n))::VARCHAR FROM t",
                "2024-07-01 12:00:00.000 -0700"},
            {"SELECT (LEAST(n, l))::VARCHAR FROM t",
                "2024-07-01 05:00:00.000 -0700"},
            {"SELECT (LEAST(l, d))::VARCHAR FROM t",
                "2024-07-01 00:00:00.000 -0700"},
            {"SELECT (GREATEST(z, d))::VARCHAR FROM t",
                "2024-07-01 12:00:00.000 Z"},
            {"SELECT (GREATEST(n, '2024-07-01 13:00:00 +02:00'))::VARCHAR FROM t",
                "2024-07-01 13:00:00.000"},
            {"SELECT (GREATEST(d, '2024-07-02 10:00:00'))::VARCHAR FROM t",
                "2024-07-02"},
            {"SELECT (GREATEST(tm, '13:00:00'))::VARCHAR FROM t",
                "13:00:00"},
            {"SELECT (GREATEST(tm, '2024-07-01 13:00:00'))::VARCHAR FROM t",
                "REFUSED Time '2024-07-01 13:00:00' is not recognized"},
        });
    }

    @Test
    public void sessionZoneTokyo() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'Asia/Tokyo'");
        engine.execute("CREATE OR REPLACE TABLE t(z TIMESTAMP_TZ, l TIMESTAMP_LTZ, n TIMESTAMP_NTZ, d DATE, tm TIME,"
            + " s VARCHAR, v VARIANT)");
        engine.execute("INSERT INTO t SELECT '2024-07-01 12:00:00 +00:00', '2024-07-01 12:00:00 +00:00', '2024-07-01"
            + " 12:00:00', '2024-07-01', '12:00:00', '2024-07-01 13:00:00 +00:00', TO_VARIANT('2024-07-01 "
            + "13:00:00 +00:00')");
        assertCells(new String[][] {
            {"SELECT (GREATEST(z, '2024-07-01 13:00:00'))::VARCHAR FROM t",
                "2024-07-01 12:00:00.000 Z"},
            {"SELECT (LEAST(l, '2024-07-01 22:00:00'))::VARCHAR FROM t",
                "2024-07-01 21:00:00.000 +0900"},
            {"SELECT (GREATEST(l, '2024-07-01 13:00:00 +00:00'))::VARCHAR FROM t",
                "2024-07-01 22:00:00.000 +0900"},
            {"SELECT (LEAST(z, '2024-07-01'))::VARCHAR FROM t",
                "2024-07-01 00:00:00.000 +0900"},
            {"SELECT (GREATEST(z, n))::VARCHAR FROM t",
                "2024-07-01 12:00:00.000 Z"},
        });
    }

    @Test
    public void timestampInputFormat() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
        engine.execute("CREATE OR REPLACE TABLE t(z TIMESTAMP_TZ, l TIMESTAMP_LTZ, n TIMESTAMP_NTZ, d DATE, tm TIME,"
            + " s VARCHAR, v VARIANT)");
        engine.execute("INSERT INTO t SELECT '2024-07-01 12:00:00 +00:00', '2024-07-01 12:00:00 +00:00', '2024-07-01"
            + " 12:00:00', '2024-07-01', '12:00:00', '2024-07-01 13:00:00 +00:00', TO_VARIANT('2024-07-01 "
            + "13:00:00 +00:00')");
        engine.execute("ALTER SESSION SET TIMESTAMP_INPUT_FORMAT = 'DD/MM/YYYY HH24:MI:SS'");
        assertCells(new String[][] {
            {"SELECT (GREATEST(z, '01/07/2024 13:00:00'))::VARCHAR FROM t",
                "2024-07-01 13:00:00.000 Z"},
            {"SELECT (LEAST(l, '01/07/2024 04:00:00'))::VARCHAR FROM t",
                "2024-07-01 04:00:00.000 Z"},
            {"SELECT (GREATEST(z, '2024-07-01 13:00:00'))::VARCHAR FROM t",
                "REFUSED Timestamp '2024-07-01 13:00:00' is not recognized"},
        });
    }

    @Test
    public void resultTypes() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        engine.execute("CREATE OR REPLACE TABLE t(z TIMESTAMP_TZ, l TIMESTAMP_LTZ, n TIMESTAMP_NTZ, d DATE, tm TIME,"
            + " s VARCHAR, v VARIANT)");
        engine.execute("INSERT INTO t SELECT '2024-07-01 12:00:00 +00:00', '2024-07-01 12:00:00 +00:00', '2024-07-01"
            + " 12:00:00', '2024-07-01', '12:00:00', '2024-07-01 13:00:00 +00:00', TO_VARIANT('2024-07-01 "
            + "13:00:00 +00:00')");
        assertCells(new String[][] {
            {"SELECT SYSTEM$TYPEOF(LEAST_IGNORE_NULLS(z, s)) FROM t",
                "TIMESTAMP_TZ(9)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(GREATEST_IGNORE_NULLS(s, z)) FROM t",
                "TIMESTAMP_TZ(9)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(LEAST(z, s)) FROM t",
                "TIMESTAMP_TZ(9)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(LEAST_IGNORE_NULLS(l, s)) FROM t",
                "TIMESTAMP_LTZ(9)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(LEAST_IGNORE_NULLS(n, s)) FROM t",
                "TIMESTAMP_NTZ(9)[SB16]"},
            {"SELECT SYSTEM$TYPEOF(LEAST_IGNORE_NULLS(d, s)) FROM t",
                "DATE[SB4]"},
            {"SELECT SYSTEM$TYPEOF(LEAST_IGNORE_NULLS(z, '2024-07-01')) FROM t",
                "TIMESTAMP_TZ(9)[SB16]"},
            {"SELECT (LEAST_IGNORE_NULLS(z, s))::VARCHAR FROM t",
                "2024-07-01 12:00:00.000 Z"},
            {"SELECT SYSTEM$TYPEOF(LEAST_IGNORE_NULLS(tm, s)) FROM t",
                "TIME(9)[SB8]"},
            {"SELECT SYSTEM$TYPEOF(LEAST_IGNORE_NULLS(z, v)) FROM t",
                "TIMESTAMP_TZ(9)[SB16]"},
        });
    }

    @Test
    public void ignoreNullsFlavour() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        engine.execute("CREATE OR REPLACE TABLE t(z TIMESTAMP_TZ, l TIMESTAMP_LTZ, n TIMESTAMP_NTZ, d DATE, tm TIME,"
            + " s VARCHAR, v VARIANT)");
        engine.execute("INSERT INTO t SELECT '2024-07-01 12:00:00 +00:00', '2024-07-01 12:00:00 +00:00', '2024-07-01"
            + " 12:00:00', '2024-07-01', '12:00:00', '2024-07-01 13:00:00 +00:00', TO_VARIANT('2024-07-01 "
            + "13:00:00 +00:00')");
        engine.execute("ALTER SESSION SET TIMEZONE = 'Asia/Tokyo'");
        assertCells(new String[][] {
            {"SELECT (GREATEST_IGNORE_NULLS(z, z))::VARCHAR FROM t",
                "2024-07-01 12:00:00.000 Z"},
            {"SELECT (LEAST_IGNORE_NULLS(z, s))::VARCHAR FROM t",
                "2024-07-01 12:00:00.000 Z"},
            {"SELECT (GREATEST_IGNORE_NULLS(z, l))::VARCHAR FROM t",
                "2024-07-01 12:00:00.000 Z"},
            {"SELECT (GREATEST_IGNORE_NULLS(l, z))::VARCHAR FROM t",
                "2024-07-01 21:00:00.000 +0900"},
            {"SELECT (GREATEST(l, z))::VARCHAR FROM t",
                "2024-07-01 21:00:00.000 +0900"},
            {"SELECT (GREATEST(l, '2024-07-01 22:00:00 +00:00'))::VARCHAR FROM t",
                "2024-07-02 07:00:00.000 +0900"},
            {"SELECT (GREATEST_IGNORE_NULLS(l, '2024-07-01 22:00:00 +00:00'))::VARCHAR FROM t",
                "2024-07-02 07:00:00.000 +0900"},
            {"SELECT (GREATEST_IGNORE_NULLS(l, z, '2024-07-01 22:00:00 +00:00'))::VARCHAR FROM t",
                "2024-07-01 22:00:00.000 Z"},
            {"SELECT (GREATEST_IGNORE_NULLS(l, n))::VARCHAR FROM t",
                "2024-07-01 21:00:00.000 +0900"},
            {"SELECT (GREATEST_IGNORE_NULLS(z, n))::VARCHAR FROM t",
                "2024-07-01 12:00:00.000 Z"},
            {"SELECT (LEAST_IGNORE_NULLS(l, z))::VARCHAR FROM t",
                "2024-07-01 21:00:00.000 +0900"},
            {"SELECT (GREATEST_IGNORE_NULLS(z, d))::VARCHAR FROM t",
                "2024-07-01 12:00:00.000 Z"},
            {"SELECT (LEAST_IGNORE_NULLS(z, d, NULL))::VARCHAR FROM t",
                "2024-07-01 00:00:00.000 +0900"},
            {"SELECT SYSTEM$TYPEOF(GREATEST_IGNORE_NULLS(l, z)) FROM t",
                "TIMESTAMP_TZ(9)[SB16]"},
            {"SELECT (GREATEST_IGNORE_NULLS(l, l))::VARCHAR FROM t",
                "2024-07-01 21:00:00.000 +0900"},
        });
    }
}
