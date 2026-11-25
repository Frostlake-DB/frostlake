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
 * A DATE, TIME, timestamp or BINARY held in a VARIANT keeps its own kind when it is compared: it never
 * equals the variant string of its text, ranks apart from strings in the VARIANT order, and compares as
 * its text beside a VARCHAR. Every cell is live-verified.
 */
public class VariantTypedScalarEqualityTest extends MeasuredCellsTestSupport {

    @Test
    public void equalityAgainstStrings() {
        engine.execute("CREATE OR REPLACE TABLE tv (id INT, v VARIANT)");
        engine.execute("INSERT INTO tv SELECT 1, TO_VARIANT('2020-01-01'::DATE) UNION ALL SELECT 2, "
            + "PARSE_JSON('\"2020-01-01\"') UNION ALL SELECT 3, TO_VARIANT('2020-01-01'::DATE) UNION ALL "
            + "SELECT 4, TO_VARIANT('12:34:56'::TIME) UNION ALL SELECT 5, PARSE_JSON('\"12:34:56\"') UNION "
            + "ALL SELECT 6, TO_VARIANT(TO_BINARY('CAFE','HEX')) UNION ALL SELECT 7, PARSE_JSON('\"CAFE\"')");
        assertCells(new String[][] {
            {"SELECT TO_VARIANT('2020-01-01'::DATE) = PARSE_JSON('\"2020-01-01\"')",
                "false"},
            {"SELECT TO_VARIANT('2020-01-01'::DATE) <> PARSE_JSON('\"2020-01-01\"')",
                "true"},
            {"SELECT TO_VARIANT('2020-01-01'::DATE) = TO_VARIANT('2020-01-01'::DATE)",
                "true"},
            {"SELECT TO_VARIANT('2020-01-01'::DATE) = TO_VARIANT('2020-01-01')",
                "false"},
            {"SELECT TO_VARIANT('2020-01-01'::DATE) IN (PARSE_JSON('\"2020-01-01\"'))",
                "false"},
            {"SELECT PARSE_JSON('\"2020-01-01\"') = TO_VARIANT('2020-01-01'::DATE)",
                "false"},
            {"SELECT TO_VARIANT('2020-01-01'::DATE) = '2020-01-01'::DATE",
                "true"},
            {"SELECT TO_VARIANT('2020-01-01'::DATE) = '2020-01-01'",
                "true"},
            {"SELECT PARSE_JSON('\"2020-01-01\"') = '2020-01-01'::DATE",
                "true"},
            {"SELECT TO_VARIANT('2020-01-01'::DATE) < PARSE_JSON('\"2020-01-02\"')",
                "true"},
            {"SELECT TO_VARIANT('2020-01-02'::DATE) > PARSE_JSON('\"2020-01-01\"')",
                "false"},
            {"SELECT TO_VARIANT('2020-01-01'::DATE) IS DISTINCT FROM PARSE_JSON('\"2020-01-01\"')",
                "true"},
            {"SELECT EQUAL_NULL(TO_VARIANT('2020-01-01'::DATE), PARSE_JSON('\"2020-01-01\"'))",
                "false"},
            {"SELECT TO_VARIANT('2020-01-01'::DATE) = TO_VARIANT('2020-01-01'::TIMESTAMP_NTZ)",
                "false"},
            {"SELECT TO_VARIANT('2020-01-01'::DATE) = TO_VARIANT('2020-01-02'::DATE)",
                "false"},
            {"SELECT TO_VARIANT('2020-01-01'::DATE) = PARSE_JSON('{\"a\":\"2020-01-01\"}'):a",
                "false"},
            {"SELECT OBJECT_CONSTRUCT('a','2020-01-01'::DATE):a = PARSE_JSON('\"2020-01-01\"')",
                "false"},
            {"SELECT TO_VARIANT('12:34:56'::TIME) = PARSE_JSON('\"12:34:56\"')",
                "false"},
            {"SELECT TO_VARIANT('12:34:56'::TIME) = TO_VARIANT('12:34:56'::TIME)",
                "true"},
            {"SELECT TO_VARIANT('12:34:56'::TIME) <> PARSE_JSON('\"12:34:56\"')",
                "true"},
            {"SELECT TO_VARIANT('2020-01-01 01:02:03'::TIMESTAMP_NTZ) = PARSE_JSON('\"2020-01-01 "
                + "01:02:03.000\"')",
                "false"},
            {"SELECT TO_VARIANT('2020-01-01 01:02:03'::TIMESTAMP_NTZ)::VARCHAR",
                "2020-01-01 01:02:03.000"},
            {"SELECT TO_VARIANT('2020-01-01 01:02:03'::TIMESTAMP_NTZ) = PARSE_JSON('\"2020-01-01 "
                + "01:02:03\"')",
                "false"},
            {"SELECT TO_VARIANT('2020-01-01 01:02:03'::TIMESTAMP_NTZ) = TO_VARIANT('2020-01-01 "
                + "01:02:03'::TIMESTAMP_NTZ)",
                "true"},
            {"SELECT TO_VARIANT('2020-01-01 01:02:03'::TIMESTAMP_LTZ) = "
                + "PARSE_JSON(TO_JSON(TO_VARIANT('2020-01-01 01:02:03'::TIMESTAMP_LTZ)))",
                "false"},
            {"SELECT TO_VARIANT('2020-01-01 01:02:03'::TIMESTAMP_LTZ) = TO_VARIANT('2020-01-01 "
                + "01:02:03'::TIMESTAMP_LTZ)",
                "true"},
            {"SELECT TO_VARIANT('2020-01-01 01:02:03 +01:00'::TIMESTAMP_TZ)::VARCHAR",
                "2020-01-01 01:02:03.000 +0100"},
            {"SELECT TO_VARIANT('2020-01-01 01:02:03 +01:00'::TIMESTAMP_TZ) = "
                + "PARSE_JSON(TO_JSON(TO_VARIANT('2020-01-01 01:02:03 +01:00'::TIMESTAMP_TZ)))",
                "false"},
            {"SELECT TO_VARIANT('2020-01-01 01:02:03 +01:00'::TIMESTAMP_TZ) = TO_VARIANT('2020-01-01 "
                + "01:02:03 +01:00'::TIMESTAMP_TZ)",
                "true"},
            {"SELECT TO_VARIANT('2020-01-01 01:02:03'::TIMESTAMP_NTZ) = "
                + "PARSE_JSON(TO_JSON(TO_VARIANT('2020-01-01 01:02:03'::TIMESTAMP_NTZ)))",
                "false"},
            {"SELECT TO_VARIANT(TO_BINARY('CAFE','HEX')) = PARSE_JSON('\"CAFE\"')",
                "false"},
            {"SELECT TO_VARIANT(TO_BINARY('CAFE','HEX')) = TO_VARIANT(TO_BINARY('CAFE','HEX'))",
                "true"},
            {"SELECT TO_VARIANT(TO_BINARY('CAFE','HEX'))::VARCHAR",
                "CAFE"},
            {"SELECT TO_VARIANT(TO_BINARY('CAFE','HEX')) <> PARSE_JSON('\"CAFE\"')",
                "true"},
            {"SELECT TO_VARIANT('2020-01-01'::DATE) = TO_VARIANT('12:34:56'::TIME)",
                "false"},
            {"SELECT TO_VARIANT(TO_BINARY('CAFE','HEX')) = TO_VARIANT('CAFE')",
                "false"},
            {"SELECT COUNT(DISTINCT v) FROM tv",
                "6"},
            {"SELECT id FROM tv WHERE v = PARSE_JSON('\"2020-01-01\"') ORDER BY id",
                "2"},
            {"SELECT COUNT(*) FROM (SELECT DISTINCT v FROM tv)",
                "6"},
            {"SELECT COUNT(*) FROM (SELECT v FROM tv WHERE id IN (1,2) UNION SELECT v FROM tv WHERE id IN "
                + "(1,2))",
                "2"},
            {"SELECT id FROM tv WHERE v IN (SELECT PARSE_JSON('\"2020-01-01\"')) ORDER BY id",
                "2"},
        });
    }

    @Test
    public void containersTextAndTimestamps() {
        assertCells(new String[][] {
            {"SELECT OBJECT_CONSTRUCT('a','2020-01-01'::DATE) = PARSE_JSON('{\"a\":\"2020-01-01\"}')",
                "false"},
            {"SELECT ARRAY_CONSTRUCT('2020-01-01'::DATE) = PARSE_JSON('[\"2020-01-01\"]')",
                "false"},
            {"SELECT ARRAY_CONSTRUCT('2020-01-01'::DATE) = ARRAY_CONSTRUCT('2020-01-01'::DATE)",
                "true"},
            {"SELECT ARRAY_CONSTRUCT(TO_BINARY('CAFE','HEX')) = PARSE_JSON('[\"CAFE\"]')",
                "false"},
            {"SELECT ARRAY_CONSTRUCT('2020-01-01'::DATE) < PARSE_JSON('[\"0\"]')",
                "true"},
            {"SELECT COUNT(DISTINCT v) FROM (SELECT ARRAY_CONSTRUCT('2020-01-01'::DATE) v UNION ALL SELECT"
                + " PARSE_JSON('[\"2020-01-01\"]'))",
                "2"},
            {"SELECT ARRAY_CONTAINS('2020-01-01'::VARIANT, ARRAY_CONSTRUCT('2020-01-01'::DATE))",
                "false"},
            {"SELECT ARRAY_CONTAINS(TO_VARIANT('2020-01-01'::DATE), PARSE_JSON('[\"2020-01-01\"]'))",
                "false"},
            {"SELECT ARRAY_CONTAINS(TO_VARIANT('2020-01-01'::DATE), ARRAY_CONSTRUCT('2020-01-01'::DATE))",
                "true"},
            {"SELECT ARRAY_CONSTRUCT('2020-01-01'::DATE) = ARRAY_CONSTRUCT('2020-01-01'::TIMESTAMP_NTZ)",
                "false"},
            {"SELECT TO_VARIANT('2020-01-01'::DATE) = '2020-1-1'",
                "false"},
            {"SELECT TO_VARIANT('2020-01-01 01:02:03'::TIMESTAMP_NTZ) = '2020-01-01 01:02:03'",
                "false"},
            {"SELECT TO_VARIANT('2020-01-01 01:02:03'::TIMESTAMP_NTZ) = '2020-01-01 01:02:03.000'",
                "true"},
            {"SELECT TO_VARIANT(TO_BINARY('CAFE','HEX')) = 'CAFE'",
                "true"},
            {"SELECT TO_VARIANT('12:34:56'::TIME) = '2020-01-01'",
                "false"},
            {"SELECT TO_VARIANT('2020-01-01'::DATE) > '1'",
                "true"},
            {"SELECT TO_VARIANT('2020-01-01'::DATE) = 'abc'",
                "false"},
            {"SELECT TO_VARIANT('12:34:56'::TIME) = '12:34:56.000'",
                "false"},
            {"SELECT TO_VARIANT('2020-01-01'::DATE) IN ('2020-01-01', 'x')",
                "true"},
            {"SELECT TO_VARIANT('12:34:56'::TIME) = '12:34:56'::TIME",
                "true"},
            {"SELECT TO_VARIANT('2020-01-01 01:02:03'::TIMESTAMP_NTZ) = '2020-01-01 "
                + "01:02:03'::TIMESTAMP_NTZ",
                "true"},
            {"SELECT TO_VARIANT('2020-01-01'::DATE) = '2020-01-01 00:00:00'::TIMESTAMP_NTZ",
                "REFUSED Failed to cast variant value \"2020-01-01\" to TIMESTAMP_NTZ"},
            {"SELECT TO_VARIANT(TO_BINARY('CAFE','HEX')) = TO_BINARY('CAFE','HEX')",
                "REFUSED SQL compilation error: | Can not convert parameter 'TO_BINARY('CAFE', 'HEX')' of "
                + "type [BINARY(67108864)] into expected type [VARIANT]"},
            {"SELECT TO_VARIANT('2020-01-01 01:02:03'::TIMESTAMP_NTZ) = TO_VARIANT('2020-01-01 "
                + "01:02:03'::TIMESTAMP_LTZ)",
                "false"},
            {"SELECT TO_VARIANT('2020-01-01 01:02:03'::TIMESTAMP_NTZ) = TO_VARIANT('2020-01-01 01:02:03 "
                + "+00:00'::TIMESTAMP_TZ)",
                "false"},
            {"SELECT TO_VARIANT('2020-01-01 01:02:03 +01:00'::TIMESTAMP_TZ) = TO_VARIANT('2020-01-01 "
                + "00:02:03 +00:00'::TIMESTAMP_TZ)",
                "true"},
            {"SELECT TO_VARIANT('2020-01-01 01:02:03'::TIMESTAMP_NTZ) < TO_VARIANT('2020-01-01 "
                + "01:02:04'::TIMESTAMP_NTZ)",
                "true"},
            {"SELECT TO_VARIANT('2020-01-01 01:02:03 +01:00'::TIMESTAMP_TZ) < TO_VARIANT('2020-01-01 "
                + "00:30:00 +00:00'::TIMESTAMP_TZ)",
                "true"},
            {"SELECT TO_VARIANT('2020-01-01 01:02:03'::TIMESTAMP_NTZ) < TO_VARIANT('2020-01-01 "
                + "01:02:04'::TIMESTAMP_LTZ)",
                "false"},
            {"SELECT TO_VARIANT('2020-01-01 01:02:05'::TIMESTAMP_NTZ) < TO_VARIANT('2020-01-01 "
                + "01:02:04'::TIMESTAMP_LTZ)",
                "false"},
            {"SELECT TO_VARIANT('2020-01-01'::DATE) < TO_VARIANT('1999-01-01 00:00:00'::TIMESTAMP_NTZ)",
                "false"},
            {"SELECT TO_VARIANT('00:00:00'::TIME) > TO_VARIANT('2099-01-01'::DATE)",
                "false"},
            {"SELECT TO_VARIANT(TO_BINARY('00','HEX')) > PARSE_JSON('\"zzz\"')",
                "true"},
            {"SELECT TO_VARIANT(TO_BINARY('FF','HEX')) < PARSE_JSON('{}')",
                "true"},
            {"SELECT TO_VARIANT('2020-01-01'::DATE) IS DISTINCT FROM TO_VARIANT('2020-01-01'::DATE)",
                "false"},
        });
    }

    @Test
    public void orderOfKinds() {
        engine.execute("CREATE OR REPLACE TABLE tw (id INT, v VARIANT)");
        engine.execute("INSERT INTO tw SELECT 1, TO_VARIANT('2020-01-01'::DATE) UNION ALL SELECT 2, "
            + "PARSE_JSON('\"2020-01-01\"') UNION ALL SELECT 3, TO_VARIANT('12:34:56'::TIME) UNION ALL "
            + "SELECT 4, PARSE_JSON('\"12:34:56\"') UNION ALL SELECT 5, TO_VARIANT(TO_BINARY('CAFE','HEX'))"
            + " UNION ALL SELECT 6, PARSE_JSON('\"CAFE\"') UNION ALL SELECT 7, TO_VARIANT('2020-01-01 "
            + "01:02:03'::TIMESTAMP_NTZ) UNION ALL SELECT 8, TO_VARIANT('2020-01-01 01:02:03 "
            + "+01:00'::TIMESTAMP_TZ) UNION ALL SELECT 9, TO_VARIANT('2020-01-01 01:02:03'::TIMESTAMP_LTZ) "
            + "UNION ALL SELECT 10, PARSE_JSON('1') UNION ALL SELECT 11, PARSE_JSON('true') UNION ALL "
            + "SELECT 12, PARSE_JSON('{\"a\":1}') UNION ALL SELECT 13, PARSE_JSON('[1]') UNION ALL SELECT "
            + "14, PARSE_JSON('null') UNION ALL SELECT 15, PARSE_JSON('\"zzz\"') UNION ALL SELECT 16, "
            + "PARSE_JSON('\"!\"') UNION ALL SELECT 17, TO_VARIANT('1999-01-01'::DATE) UNION ALL SELECT 18,"
            + " TO_VARIANT('00:00:01'::TIME) UNION ALL SELECT 19, TO_VARIANT(TO_BINARY('00','HEX'))");
        assertCells(new String[][] {
            {"SELECT TO_VARIANT('2020-01-01'::DATE) > PARSE_JSON('1')",
                "true"},
            {"SELECT TO_VARIANT('2020-01-01'::DATE) < PARSE_JSON('{\"a\":1}')",
                "true"},
        });
    }

    @Test
    public void arrayFunctions() {
        assertCells(new String[][] {
            {"SELECT ARRAYS_OVERLAP(ARRAY_CONSTRUCT('2020-01-01'::DATE), ARRAY_CONSTRUCT('2020-01-01'))",
                "false"},
            {"SELECT ARRAY_POSITION('2020-01-01'::VARIANT, ARRAY_CONSTRUCT('2020-01-01'::DATE, "
                + "'2020-01-01'))",
                "1"},
            {"SELECT ARRAY_SIZE(ARRAY_INTERSECTION(ARRAY_CONSTRUCT(TO_BINARY('CAFE','HEX')), "
                + "ARRAY_CONSTRUCT('CAFE')))",
                "0"},
            {"SELECT ARRAY_SIZE(ARRAY_EXCEPT(ARRAY_CONSTRUCT('12:34:56'::TIME), "
                + "ARRAY_CONSTRUCT('12:34:56')))",
                "1"},
            {"SELECT ARRAY_CONTAINS(TO_VARIANT('2020-01-01'::DATE), ARRAY_CONSTRUCT('2020-01-01'::DATE, "
                + "'x'))",
                "true"},
            {"SELECT ARRAY_MIN(ARRAY_CONSTRUCT('a', '2020-01-01'::DATE))::VARCHAR",
                "2020-01-01"},
            {"SELECT ARRAY_MAX(ARRAY_CONSTRUCT('a', TO_BINARY('00','HEX')))::VARCHAR",
                "00"},
        });
    }

    @Test
    public void zonedAndPathOperands() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        engine.execute("CREATE OR REPLACE TABLE tg (id INT, v VARIANT)");
        engine.execute("INSERT INTO tg SELECT 1, OBJECT_CONSTRUCT('a','2020-01-01'::DATE)::VARIANT UNION ALL SELECT "
            + "2, PARSE_JSON('{\"a\":\"2020-01-01\"}') UNION ALL SELECT 3, "
            + "ARRAY_CONSTRUCT(TO_BINARY('CAFE','HEX'))::VARIANT UNION ALL SELECT 4, "
            + "PARSE_JSON('[\"CAFE\"]') UNION ALL SELECT 5, "
            + "OBJECT_CONSTRUCT('a','2020-01-01'::DATE)::VARIANT");
        assertCells(new String[][] {
            {"SELECT TO_VARIANT('2020-01-01 01:02:03 -08:00'::TIMESTAMP_TZ) = TO_VARIANT('2020-01-01 "
                + "01:02:03'::TIMESTAMP_LTZ)",
                "true"},
            {"SELECT TO_VARIANT('2020-01-01 01:02:03 +01:00'::TIMESTAMP_TZ) < TO_VARIANT('2020-01-01 "
                + "01:02:03'::TIMESTAMP_LTZ)",
                "true"},
            {"SELECT TO_VARIANT('2020-01-01 01:02:03'::TIMESTAMP_LTZ) = PARSE_JSON('\"2020-01-01 "
                + "01:02:03.000 -0800\"')",
                "false"},
            {"SELECT TO_VARIANT('2020-01-01 01:02:03'::TIMESTAMP_LTZ) = '2020-01-01 01:02:03.000 -0800'",
                "true"},
            {"SELECT TO_VARIANT('2020-01-01 01:02:03 +01:00'::TIMESTAMP_TZ) = PARSE_JSON('\"2020-01-01 "
                + "01:02:03.000 +0100\"')",
                "false"},
            {"SELECT TO_VARIANT('2020-01-01 01:02:03 +01:00'::TIMESTAMP_TZ) = '2020-01-01 01:02:03.000 "
                + "+0100'",
                "true"},
            {"SELECT TO_VARIANT('2020-01-01 01:02:03'::TIMESTAMP_NTZ) = TO_VARIANT('2020-01-01 "
                + "01:02:03'::TIMESTAMP_LTZ)",
                "false"},
            {"SELECT TO_VARIANT('2020-01-01 01:02:03'::TIMESTAMP_LTZ) < TO_VARIANT('2020-01-01 "
                + "01:02:03'::TIMESTAMP_NTZ)",
                "true"},
            {"SELECT TO_VARIANT('2020-01-01 01:02:03'::TIMESTAMP_LTZ) IN (PARSE_JSON('\"2020-01-01 "
                + "01:02:03.000 -0800\"'), PARSE_JSON('1'))",
                "false"},
            {"SELECT COUNT(DISTINCT v) FROM tg",
                "4"},
            {"SELECT COUNT(*) FROM (SELECT v FROM tg GROUP BY v)",
                "4"},
            {"SELECT id FROM tg WHERE v = PARSE_JSON('{\"a\":\"2020-01-01\"}') ORDER BY id",
                "2"},
            {"SELECT id FROM tg WHERE v IN (SELECT ARRAY_CONSTRUCT(TO_BINARY('CAFE','HEX'))) ORDER BY id",
                "3"},
            {"SELECT id FROM tg WHERE v:a = PARSE_JSON('\"2020-01-01\"') ORDER BY id",
                "2"},
            {"SELECT id FROM tg WHERE v[0] = PARSE_JSON('\"CAFE\"') ORDER BY id",
                "4"},
            {"SELECT id FROM tg WHERE v:a IN (PARSE_JSON('\"2020-01-01\"')) ORDER BY id",
                "2"},
            {"SELECT id FROM tg WHERE v:a IN (SELECT PARSE_JSON('\"2020-01-01\"')) ORDER BY id",
                "2"},
            {"SELECT id FROM tg WHERE v:a BETWEEN PARSE_JSON('\"2019\"') AND PARSE_JSON('\"2021\"') ORDER "
                + "BY id",
                "2"},
            {"SELECT id FROM tg WHERE DECODE(v:a, PARSE_JSON('\"2020-01-01\"'), TRUE, FALSE) ORDER BY id",
                "2"},
            {"SELECT id FROM tg WHERE EQUAL_NULL(v:a, PARSE_JSON('\"2020-01-01\"')) ORDER BY id",
                "2"},
            {"SELECT id FROM tg WHERE v:a IS NOT DISTINCT FROM PARSE_JSON('\"2020-01-01\"') ORDER BY id",
                "2"},
            {"SELECT TYPEOF(NULLIF(TO_VARIANT('2020-01-01'::DATE), PARSE_JSON('\"2020-01-01\"')))",
                "DATE"},
            {"SELECT NULLIF(TO_VARIANT('2020-01-01'::DATE), TO_VARIANT('2020-01-01'::DATE)) IS NULL",
                "true"},
            {"SELECT DECODE(TO_VARIANT('2020-01-01'::DATE), PARSE_JSON('\"2020-01-01\"'), 'a', "
                + "TO_VARIANT('2020-01-01'::DATE), 'b', 'c')",
                "b"},
            {"SELECT DECODE(TO_VARIANT('2020-01-01'::DATE), '2020-01-01', 'a', 'c')",
                "a"},
            {"SELECT NULLIF(TO_VARIANT('2020-01-01'::DATE), '2020-01-01') IS NULL",
                "true"},
        });
    }

    @Test
    public void storedRowsAcrossOperators() {
        engine.execute("CREATE OR REPLACE TABLE tv (id INT, v VARIANT)");
        engine.execute("INSERT INTO tv SELECT 1, TO_VARIANT('2020-01-01'::DATE) UNION ALL SELECT 2, "
            + "PARSE_JSON('\"2020-01-01\"') UNION ALL SELECT 3, TO_VARIANT('2020-01-01'::DATE) UNION ALL "
            + "SELECT 4, TO_VARIANT('12:34:56'::TIME) UNION ALL SELECT 5, PARSE_JSON('\"12:34:56\"') UNION "
            + "ALL SELECT 6, TO_VARIANT(TO_BINARY('CAFE','HEX')) UNION ALL SELECT 7, PARSE_JSON('\"CAFE\"')");
        assertCells(new String[][] {
            {"SELECT id, TYPEOF(v) FROM tv ORDER BY id",
                "1|DATE ;; 2|VARCHAR ;; 3|DATE ;; 4|TIME ;; 5|VARCHAR ;; 6|BINARY ;; 7|VARCHAR"},
            {"SELECT COUNT(DISTINCT v) FROM tv",
                "6"},
            {"SELECT TYPEOF(v), COUNT(*) FROM tv GROUP BY v ORDER BY 1, 2",
                "BINARY|1 ;; DATE|2 ;; TIME|1 ;; VARCHAR|1 ;; VARCHAR|1 ;; VARCHAR|1"},
            {"SELECT a.id, b.id FROM tv a JOIN tv b ON a.v = b.v AND a.id < b.id ORDER BY 1, 2",
                "1|3"},
            {"SELECT id FROM tv WHERE v = PARSE_JSON('\"2020-01-01\"') ORDER BY id",
                "2"},
            {"SELECT id FROM tv WHERE v = TO_VARIANT('2020-01-01'::DATE) ORDER BY id",
                "1 ;; 3"},
            {"SELECT id FROM tv WHERE v IN (PARSE_JSON('\"12:34:56\"'), PARSE_JSON('\"CAFE\"')) ORDER BY id",
                "5 ;; 7"},
            {"SELECT COUNT(*) FROM (SELECT DISTINCT v FROM tv)",
                "6"},
            {"SELECT id FROM tv WHERE v = '2020-01-01' ORDER BY id",
                "1 ;; 2 ;; 3"},
            {"SELECT a.id, b.id FROM tv a LEFT JOIN tv b ON a.v = b.v AND a.id <> b.id ORDER BY 1, 2",
                "1|3 ;; 2|null ;; 3|1 ;; 4|null ;; 5|null ;; 6|null ;; 7|null"},
            {"SELECT COUNT(*) FROM (SELECT v FROM tv WHERE id IN (1,2) UNION SELECT v FROM tv WHERE id IN (1,2))",
                "2"},
            {"SELECT id FROM tv WHERE v IN (SELECT PARSE_JSON('\"2020-01-01\"')) ORDER BY id",
                "2"},
        });
    }

    @Test
    public void storedContainersAndPathMembers() {
        engine.execute("CREATE OR REPLACE TABLE tg (id INT, v VARIANT)");
        engine.execute("INSERT INTO tg SELECT 1, OBJECT_CONSTRUCT('a','2020-01-01'::DATE)::VARIANT UNION ALL SELECT "
            + "2, PARSE_JSON('{\"a\":\"2020-01-01\"}') UNION ALL SELECT 3, "
            + "ARRAY_CONSTRUCT(TO_BINARY('CAFE','HEX'))::VARIANT UNION ALL SELECT 4, "
            + "PARSE_JSON('[\"CAFE\"]') UNION ALL SELECT 5, "
            + "OBJECT_CONSTRUCT('a','2020-01-01'::DATE)::VARIANT");
        assertCells(new String[][] {
            {"SELECT COUNT(DISTINCT v) FROM tg",
                "4"},
            {"SELECT COUNT(*) FROM (SELECT v FROM tg GROUP BY v)",
                "4"},
            {"SELECT a.id, b.id FROM tg a JOIN tg b ON a.v = b.v AND a.id < b.id ORDER BY 1, 2",
                "1|5"},
            {"SELECT id FROM tg WHERE v = PARSE_JSON('{\"a\":\"2020-01-01\"}') ORDER BY id",
                "2"},
            {"SELECT id FROM tg WHERE v IN (SELECT ARRAY_CONSTRUCT(TO_BINARY('CAFE','HEX'))) ORDER BY id",
                "3"},
            {"SELECT id FROM tg WHERE v:a = PARSE_JSON('\"2020-01-01\"') ORDER BY id",
                "2"},
            {"SELECT id FROM tg WHERE v:a = '2020-01-01' ORDER BY id",
                "1 ;; 2 ;; 5"},
            {"SELECT id FROM tg WHERE v[0] = PARSE_JSON('\"CAFE\"') ORDER BY id",
                "4"},
            {"SELECT id FROM tg WHERE v:a = TO_VARIANT('2020-01-01'::DATE) ORDER BY id",
                "1 ;; 5"},
            {"SELECT id FROM tg WHERE v:a IN (PARSE_JSON('\"2020-01-01\"')) ORDER BY id",
                "2"},
            {"SELECT id FROM tg WHERE v:a IN (SELECT PARSE_JSON('\"2020-01-01\"')) ORDER BY id",
                "2"},
            {"SELECT id FROM tg WHERE v:a IN (SELECT '2020-01-01') ORDER BY id",
                "1 ;; 2 ;; 5"},
            {"SELECT id FROM tg WHERE v:a BETWEEN PARSE_JSON('\"2019\"') AND PARSE_JSON('\"2021\"') ORDER BY id",
                "2"},
            {"SELECT id FROM tg WHERE DECODE(v:a, PARSE_JSON('\"2020-01-01\"'), TRUE, FALSE) ORDER BY id",
                "2"},
            {"SELECT id FROM tg WHERE NULLIF(v:a, PARSE_JSON('\"2020-01-01\"')) IS NULL ORDER BY id",
                "2 ;; 3 ;; 4"},
            {"SELECT id FROM tg WHERE EQUAL_NULL(v:a, PARSE_JSON('\"2020-01-01\"')) ORDER BY id",
                "2"},
            {"SELECT id FROM tg WHERE v:a IS NOT DISTINCT FROM PARSE_JSON('\"2020-01-01\"') ORDER BY id",
                "2"},
            {"SELECT id, CASE v:a WHEN PARSE_JSON('\"2020-01-01\"') THEN 'y' ELSE 'n' END FROM tg ORDER BY id",
                "1|n ;; 2|y ;; 3|n ;; 4|n ;; 5|n"},
        });
    }
}
