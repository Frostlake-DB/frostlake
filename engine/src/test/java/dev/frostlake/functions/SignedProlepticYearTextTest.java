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
 * A date or a timestamp before the year 1 spells its signed proleptic year in SQL text — ::VARCHAR, TO_VARCHAR,
 * a VARIANT's string, TO_CHAR's YYYY ({@code -44}, {@code -12345}, {@code 0000} for the year 0) with YY and Y
 * the year modulo 100 — and AUTO input reads a signed year as a DATE only, never as a timestamp, while the UUUU
 * format element reads one either way (live-verified).
 */
public class SignedProlepticYearTextTest extends MeasuredCellsTestSupport {

    @Test
    public void textOfYearsBeforeTheFirst() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
        engine.execute("CREATE OR REPLACE TABLE ty (y INT)");
        engine.execute("INSERT INTO ty VALUES (-12345), (-1000), (-999), (-95), (-44), (-5), (-1), (0), (1), (5), "
            + "(95), (2024), (12345)");
        engine.execute("CREATE OR REPLACE TABLE tdd (d DATE)");
        engine.execute("INSERT INTO tdd VALUES ('-0044-03-15'), ('-0100-01-01'), ('2024-01-01'), ('+0044-03-15'), "
            + "('-4-3-5')");
        assertCells(new String[][] {
            {"SELECT LISTAGG(DATE_FROM_PARTS(y, 3, 5)::VARCHAR, '|') WITHIN GROUP (ORDER BY y) FROM ty",
                "-12345-03-05|-1000-03-05|-999-03-05|-95-03-05|-44-03-05|-5-03-05|-1-03-05|0000-03-05|0001-03-05|0005-03-05|0095-03-05|2024-03-05|12345-03-05"},
            {"SELECT LISTAGG(TO_VARCHAR(TIMESTAMP_NTZ_FROM_PARTS(y, 3, 5, 1, 2, 3)), '|') WITHIN GROUP "
                + "(ORDER BY y) FROM ty",
                "-12345-03-05 01:02:03.000|-1000-03-05 01:02:03.000|-999-03-05 01:02:03.000|-95-03-05 "
                + "01:02:03.000|-44-03-05 01:02:03.000|-5-03-05 01:02:03.000|-1-03-05 01:02:03.000|0000-03-05 "
                + "01:02:03.000|0001-03-05 01:02:03.000|0005-03-05 01:02:03.000|0095-03-05 "
                + "01:02:03.000|2024-03-05 01:02:03.000|12345-03-05 01:02:03.000"},
            {"SELECT LISTAGG(TO_VARCHAR(TIMESTAMP_NTZ_FROM_PARTS(y, 3, 5, 1, 2, 3)::TIMESTAMP_LTZ), '|') "
                + "WITHIN GROUP (ORDER BY y) FROM ty",
                "-12345-03-05 01:02:03.000 Z|-1000-03-05 01:02:03.000 Z|-999-03-05 01:02:03.000 Z|-95-03-05 "
                + "01:02:03.000 Z|-44-03-05 01:02:03.000 Z|-5-03-05 01:02:03.000 Z|-1-03-05 01:02:03.000 "
                + "Z|0000-03-05 01:02:03.000 Z|0001-03-05 01:02:03.000 Z|0005-03-05 01:02:03.000 Z|0095-03-05 "
                + "01:02:03.000 Z|2024-03-05 01:02:03.000 Z|12345-03-05 01:02:03.000 Z"},
            {"SELECT LISTAGG(TO_VARCHAR(TIMESTAMP_TZ_FROM_PARTS(y, 3, 5, 1, 2, 3, 0, 'UTC')), '|') WITHIN "
                + "GROUP (ORDER BY y) FROM ty",
                "-12345-03-05 01:02:03.000 Z|-1000-03-05 01:02:03.000 Z|-999-03-05 01:02:03.000 Z|-95-03-05 "
                + "01:02:03.000 Z|-44-03-05 01:02:03.000 Z|-5-03-05 01:02:03.000 Z|-1-03-05 01:02:03.000 "
                + "Z|0000-03-05 01:02:03.000 Z|0001-03-05 01:02:03.000 Z|0005-03-05 01:02:03.000 Z|0095-03-05 "
                + "01:02:03.000 Z|2024-03-05 01:02:03.000 Z|12345-03-05 01:02:03.000 Z"},
            {"SELECT LISTAGG(CAST(DATE_FROM_PARTS(y, 3, 5) AS STRING) || '/' || CONCAT('x', "
                + "DATE_FROM_PARTS(y, 3, 5)), '|') WITHIN GROUP (ORDER BY y) FROM ty",
                "-12345-03-05/x-12345-03-05|-1000-03-05/x-1000-03-05|-999-03-05/x-999-03-05|-95-03-05/x-95-03-05|-44-03-05/x-44-03-05|-5-03-05/x-5-03-05|-1-03-05/x-1-03-05|0000-03-05/x0000-03-05|0001-03-05/x0001-03-05|0005-03-05/x0005-03-05|0095-03-05/x0095-03-05|2024-03-05/x2024-03-05|12345-03-05/x12345-03-05"},
            {"SELECT LISTAGG(TO_CHAR(DATE_FROM_PARTS(y, 3, 5)), '|') WITHIN GROUP (ORDER BY y) FROM ty",
                "-12345-03-05|-1000-03-05|-999-03-05|-95-03-05|-44-03-05|-5-03-05|-1-03-05|0000-03-05|0001-03-05|0005-03-05|0095-03-05|2024-03-05|12345-03-05"},
            {"SELECT LISTAGG(TO_JSON(OBJECT_CONSTRUCT('d', DATE_FROM_PARTS(y, 3, 5), 't', "
                + "TIMESTAMP_NTZ_FROM_PARTS(y, 3, 5, 1, 2, 3))), '|') WITHIN GROUP (ORDER BY y) FROM ty",
                "{\"d\":\"-12345-03-05\",\"t\":\"-12345-03-05 "
                + "01:02:03.000\"}|{\"d\":\"-1000-03-05\",\"t\":\"-1000-03-05 "
                + "01:02:03.000\"}|{\"d\":\"-999-03-05\",\"t\":\"-999-03-05 "
                + "01:02:03.000\"}|{\"d\":\"-95-03-05\",\"t\":\"-95-03-05 "
                + "01:02:03.000\"}|{\"d\":\"-44-03-05\",\"t\":\"-44-03-05 "
                + "01:02:03.000\"}|{\"d\":\"-5-03-05\",\"t\":\"-5-03-05 "
                + "01:02:03.000\"}|{\"d\":\"-1-03-05\",\"t\":\"-1-03-05 "
                + "01:02:03.000\"}|{\"d\":\"0000-03-05\",\"t\":\"0000-03-05 "
                + "01:02:03.000\"}|{\"d\":\"0001-03-05\",\"t\":\"0001-03-05 "
                + "01:02:03.000\"}|{\"d\":\"0005-03-05\",\"t\":\"0005-03-05 "
                + "01:02:03.000\"}|{\"d\":\"0095-03-05\",\"t\":\"0095-03-05 "
                + "01:02:03.000\"}|{\"d\":\"2024-03-05\",\"t\":\"2024-03-05 "
                + "01:02:03.000\"}|{\"d\":\"12345-03-05\",\"t\":\"12345-03-05 01:02:03.000\"}"},
            {"SELECT LISTAGG(TIME_SLICE(DATE_FROM_PARTS(y, 3, 5), 1, 'DAY')::VARCHAR, '|') WITHIN GROUP "
                + "(ORDER BY y) FROM ty WHERE y < 2",
                "-12345-03-05|-1000-03-05|-999-03-05|-95-03-05|-44-03-05|-5-03-05|-1-03-05|0000-03-05|0001-03-05"},
            {"SELECT LISTAGG(TO_CHAR(DATE_FROM_PARTS(y, 3, 5), 'YYYY YY Y UUUU'), '|') WITHIN GROUP (ORDER"
                + " BY y) FROM ty",
                "-12345 55 55 -12345|-1000 00 0 -1000|-999 01 1 -0999|-95 05 5 -0095|-44 56 56 -0044|-5 95 95"
                + " -0005|-1 99 99 -0001|0000 00 0 0000|0001 01 1 0001|0005 05 5 0005|0095 95 95 0095|2024 24 "
                + "24 2024|12345 45 45 +12345"},
            {"SELECT LISTAGG(TO_CHAR(DATE_FROM_PARTS(y, 3, 5), 'YYYYYY|YYY|YYYYY'), '/') WITHIN GROUP "
                + "(ORDER BY y) FROM ty",
                "-1234555|5555|-1234555/-100000|000|-10000/-99901|011|-9991/-9505|055|-955/-4456|5656|-4456/-595|9595|-595/-199|9999|-199/000000|000|00000/000101|011|00011/000505|055|00055/009595|9595|009595/202424|2424|202424/1234545|4545|1234545"},
            {"SELECT LISTAGG(TO_CHAR(DATE_FROM_PARTS(y, 3, 5), 'YYYYMMDD') || ' ' || "
                + "TO_CHAR(DATE_FROM_PARTS(y, 3, 5), 'DD-MON-YYYY'), '|') WITHIN GROUP (ORDER BY y) FROM ty",
                "-123450305 05-Mar--12345|-10000305 05-Mar--1000|-9990305 05-Mar--999|-950305 "
                + "05-Mar--95|-440305 05-Mar--44|-50305 05-Mar--5|-10305 05-Mar--1|00000305 "
                + "05-Mar-0000|00010305 05-Mar-0001|00050305 05-Mar-0005|00950305 05-Mar-0095|20240305 "
                + "05-Mar-2024|123450305 05-Mar-12345"},
            {"SELECT LISTAGG(TO_CHAR(TIMESTAMP_NTZ_FROM_PARTS(y, 3, 5, 1, 2, 3), 'YYYY-MM-DD HH24:MI:SS'),"
                + " '|') WITHIN GROUP (ORDER BY y) FROM ty",
                "-12345-03-05 01:02:03|-1000-03-05 01:02:03|-999-03-05 01:02:03|-95-03-05 01:02:03|-44-03-05 "
                + "01:02:03|-5-03-05 01:02:03|-1-03-05 01:02:03|0000-03-05 01:02:03|0001-03-05 "
                + "01:02:03|0005-03-05 01:02:03|0095-03-05 01:02:03|2024-03-05 01:02:03|12345-03-05 01:02:03"},
            {"SELECT LISTAGG(TO_CHAR(TIMESTAMP_NTZ_FROM_PARTS(y, 3, 5, 1, 2, 3)::TIMESTAMP_LTZ, 'YYYY YY "
                + "Y'), '|') WITHIN GROUP (ORDER BY y) FROM ty",
                "-12345 55 55|-1000 00 0|-999 01 1|-95 05 5|-44 56 56|-5 95 95|-1 99 99|0000 00 0|0001 01 "
                + "1|0005 05 5|0095 95 95|2024 24 24|12345 45 45"},
            {"SELECT COUNT_IF(DATE_FROM_PARTS(y, 3, 5)::VARCHAR::DATE = DATE_FROM_PARTS(y, 3, 5)) FROM ty",
                "13"},
            {"SELECT LISTAGG(COALESCE(TRY_TO_TIMESTAMP_NTZ(TIMESTAMP_NTZ_FROM_PARTS(y, 3, 5, 1, 2, "
                + "3)::VARCHAR)::VARCHAR, 'null'), '|') WITHIN GROUP (ORDER BY y) FROM ty WHERE y < 10000",
                "null|null|null|null|null|null|null|0000-03-05 01:02:03.000|0001-03-05 "
                + "01:02:03.000|0005-03-05 01:02:03.000|0095-03-05 01:02:03.000|2024-03-05 01:02:03.000"},
            {"SELECT LISTAGG(TO_DATE(DATE_FROM_PARTS(y, 3, 5)::VARCHAR, 'UUUU-MM-DD')::VARCHAR, '|') "
                + "WITHIN GROUP (ORDER BY y) FROM ty",
                "-12345-03-05|-1000-03-05|-999-03-05|-95-03-05|-44-03-05|-5-03-05|-1-03-05|0000-03-05|0001-03-05|0005-03-05|0095-03-05|2024-03-05|12345-03-05"},
            {"SELECT LISTAGG(TO_DATE(TO_CHAR(DATE_FROM_PARTS(y, 3, 5), 'UUUU-MM-DD'), "
                + "'UUUU-MM-DD')::VARCHAR, '|') WITHIN GROUP (ORDER BY y) FROM ty",
                "-12345-03-05|-1000-03-05|-999-03-05|-95-03-05|-44-03-05|-5-03-05|-1-03-05|0000-03-05|0001-03-05|0005-03-05|0095-03-05|2024-03-05|12345-03-05"},
            {"SELECT LISTAGG(d::VARCHAR || ' ' || TO_CHAR(d, 'DD/MM/YYYY'), '|') WITHIN GROUP (ORDER BY d)"
                + " FROM tdd",
                "-100-01-01 01/01/-100|-44-03-15 15/03/-44|-4-03-05 05/03/-4|0044-03-15 15/03/0044|2024-01-01"
                + " 01/01/2024"},
            {"SELECT LISTAGG(d::TIMESTAMP_NTZ::VARCHAR, '|') WITHIN GROUP (ORDER BY d) FROM tdd",
                "-100-01-01 00:00:00.000|-44-03-15 00:00:00.000|-4-03-05 00:00:00.000|0044-03-15 "
                + "00:00:00.000|2024-01-01 00:00:00.000"},
            {"SELECT LISTAGG(DATEADD(year, -2068, d)::VARCHAR, '|') WITHIN GROUP (ORDER BY d) FROM tdd",
                "-2168-01-01|-2112-03-15|-2072-03-05|-2024-03-15|-44-01-01"},
        });
    }

    @Test
    public void signedYearInput() {
        assertCells(new String[][] {
            {"SELECT '-0044-03-15'::DATE::VARCHAR",
                "-44-03-15"},
            {"SELECT '-44-03-15'::DATE::VARCHAR",
                "-44-03-15"},
            {"SELECT '0000-03-15'::DATE::VARCHAR",
                "0000-03-15"},
            {"SELECT '-0044-03-15 10:11:12'::TIMESTAMP_NTZ::VARCHAR",
                "REFUSED Timestamp '-0044-03-15 10:11:12' is not recognized"},
            {"SELECT TO_DATE('-0044-03-15', 'YYYY-MM-DD')::VARCHAR",
                "REFUSED Can't parse '-0044-03-15' as date with format 'YYYY-MM-DD'"},
            {"SELECT TO_DATE('-0044-03-15', 'UUUU-MM-DD')::VARCHAR",
                "-44-03-15"},
            {"SELECT '+0044-03-15'::DATE::VARCHAR",
                "0044-03-15"},
            {"SELECT TO_TIMESTAMP('-0044-03-15 10:11:12', 'YYYY-MM-DD HH24:MI:SS')::VARCHAR",
                "REFUSED Can't parse '-0044-03-15 10:11:12' as timestamp with format 'YYYY-MM-DD HH24:MI:SS'"},
            {"SELECT TRY_TO_DATE('-0044-03-15')::VARCHAR",
                "-44-03-15"},
            {"SELECT TO_DATE('-4-3-5')::VARCHAR",
                "-4-03-05"},
            {"SELECT TO_DATE('-12345-03-15')::VARCHAR",
                "-12345-03-15"},
            {"SELECT TO_DATE('+12345-03-15')::VARCHAR",
                "12345-03-15"},
            {"SELECT TRY_TO_DATE('-0-03-15')::VARCHAR",
                "0000-03-15"},
            {"SELECT TRY_TO_DATE('-0000-03-15')::VARCHAR",
                "0000-03-15"},
            {"SELECT '-0044-03-15'::TIMESTAMP_NTZ::VARCHAR",
                "REFUSED Timestamp '-0044-03-15' is not recognized"},
            {"SELECT TRY_TO_TIMESTAMP_NTZ('-0044-03-15T10:11:12')::VARCHAR",
                "null"},
            {"SELECT TRY_TO_TIMESTAMP_NTZ('+0044-03-15 10:11:12')::VARCHAR",
                "null"},
            {"SELECT TRY_TO_TIMESTAMP_NTZ('+2024-03-15')::VARCHAR",
                "null"},
            {"SELECT TRY_TO_TIMESTAMP_LTZ('-0044-03-15')::VARCHAR",
                "null"},
            {"SELECT TRY_TO_TIMESTAMP_TZ('-0044-03-15 10:11:12 +00:00')::VARCHAR",
                "null"},
            {"SELECT TRY_TO_DATE('-0044-03-15 10:11:12')::VARCHAR",
                "null"},
            {"SELECT TRY_TO_DATE('-0044-03-15T10:11:12')::VARCHAR",
                "null"},
            {"SELECT TRY_TO_DATE('-0044-3-5')::VARCHAR",
                "-44-03-05"},
            {"SELECT TRY_TO_DATE('+2024-03-15')::VARCHAR",
                "2024-03-15"},
            {"SELECT TRY_TO_DATE('-0044/03/15')::VARCHAR",
                "null"},
            {"SELECT TRY_TO_DATE('- 0044-03-15')::VARCHAR",
                "null"},
            {"SELECT TRY_TO_DATE('--0044-03-15')::VARCHAR",
                "null"},
            {"SELECT TRY_TO_DATE('-0044-03-15Z')::VARCHAR",
                "null"},
            {"SELECT TRY_TO_DATE('-0044-02-30')::VARCHAR",
                "null"},
            {"SELECT TRY_TO_DATE('-0044-003-15')::VARCHAR",
                "null"},
            {"SELECT TRY_TO_DATE('-1234567890-03-15')::VARCHAR",
                "null"},
            {"SELECT TRY_TO_DATE('-86400')::VARCHAR",
                "1969-12-31"},
            {"SELECT TRY_TO_TIMESTAMP_NTZ('-86400')::VARCHAR",
                "1969-12-31 00:00:00.000"},
            {"SELECT TRY_TO_DATE('+0044-03-15', 'UUUU-MM-DD')::VARCHAR",
                "0044-03-15"},
            {"SELECT TRY_TO_DATE('0044-03-15', 'UUUU-MM-DD')::VARCHAR",
                "0044-03-15"},
            {"SELECT TRY_TO_DATE('-44-03-15', 'UUUU-MM-DD')::VARCHAR",
                "-44-03-15"},
            {"SELECT TRY_TO_DATE('20240315', 'UUUUMMDD')::VARCHAR",
                "null"},
            {"SELECT TRY_TO_DATE('-00440315', 'UUUUMMDD')::VARCHAR",
                "null"},
            {"SELECT TRY_TO_TIMESTAMP('-0044-03-15 10:11:12', 'UUUU-MM-DD HH24:MI:SS')::VARCHAR",
                "-44-03-15 10:11:12.000"},
            {"SELECT TRY_TO_DATE('-0044/03/15', 'UUUU/MM/DD')::VARCHAR",
                "-44-03-15"},
            {"SELECT TRY_TO_DATE('+-4-03-15', 'UUUU-MM-DD')::VARCHAR",
                "null"},
            {"SELECT TRY_TO_DATE('3/15/-0044')::VARCHAR",
                "null"},
            {"SELECT TRY_TO_DATE(' -0044-03-15')::VARCHAR",
                "-44-03-15"},
            {"SELECT YEAR('-0044-03-15'::DATE)",
                "-44"},
        });
    }
}
