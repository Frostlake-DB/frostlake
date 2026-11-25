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

package dev.frostlake.udf;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * A block-bodied SQL function's RETURN that is no constant, typed name or cast, under the type the function declares
 * (live-verified): the value is written as text — a TIME with nine fraction digits, a timestamp likewise and a
 * TIMESTAMP_TZ with its offset, a number without trailing zeros, a VARIANT, ARRAY or OBJECT as its JSON across lines at
 * the session's JSON_INDENT — and the text is read with the declared type's fixed format, a text that does not read
 * being that reader's own refusal. A format's point and fraction may be left out of the input together.
 */
public class BlockFunctionReturnTextTest extends MeasuredCellsTestSupport {

    @Test
    public void aReturnIsReadThroughItsText() {
        engine.execute("ALTER SESSION SET JSON_INDENT = 2");
        engine.execute("CREATE OR REPLACE FUNCTION rx0_0() RETURNS TIME AS $$ BEGIN RETURN TO_DATE('2020-01-15'); "
            + "END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx0_1() RETURNS TIME AS $$ BEGIN RETURN TO_TIME('10:00:00.123'); "
            + "END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx0_2() RETURNS TIME AS $$ BEGIN RETURN PARSE_JSON('1'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx0_3() RETURNS TIME AS $$ BEGIN RETURN [1, 2]; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx0_4() RETURNS TIME AS $$ BEGIN RETURN {'a': 1}; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx0_5() RETURNS TIME AS $$ BEGIN RETURN TO_BINARY('ABCD', 'HEX');"
            + " END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx0_6() RETURNS TIME AS $$ BEGIN RETURN "
            + "TO_TIMESTAMP_NTZ('2020-01-15 10:11:12.5'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx0_7() RETURNS TIME AS $$ BEGIN RETURN "
            + "TO_TIMESTAMP_TZ('2020-01-15 10:11:12 +02:00'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx0_8() RETURNS TIME AS $$ BEGIN RETURN "
            + "TO_TIMESTAMP_TZ('2020-01-15 10:11:12 Z'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx0_9() RETURNS TIME AS $$ BEGIN RETURN "
            + "PARSE_JSON('\"10:00:00\"'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx0_10() RETURNS TIME AS $$ BEGIN RETURN "
            + "PARSE_JSON('{\"b\":[1,{\"c\":null}],\"a\":\"x\"}'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx0_11() RETURNS TIME AS $$ BEGIN RETURN []; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx0_12() RETURNS TIME AS $$ BEGIN RETURN PARSE_JSON('true'); END "
            + "$$");
        engine.execute("CREATE OR REPLACE FUNCTION rx0_13() RETURNS TIME AS $$ BEGIN RETURN CONCAT('10:00:00', ''); "
            + "END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx0_14() RETURNS TIME AS $$ BEGIN RETURN CONCAT('2020-01-15 "
            + "10:11:12', ''); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx0_15() RETURNS TIME AS $$ BEGIN RETURN CONCAT('1', ''); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx0_16() RETURNS TIME AS $$ BEGIN RETURN CONCAT('ab', ''); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx0_17() RETURNS TIME AS $$ BEGIN RETURN 1 + 1; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx0_18() RETURNS TIME AS $$ BEGIN RETURN 1.50 + 0; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx0_19() RETURNS TIME AS $$ BEGIN RETURN 1 = 1; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx0_20() RETURNS TIME AS $$ BEGIN RETURN TO_DOUBLE(1.5); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx0_21() RETURNS TIME AS $$ BEGIN RETURN TO_DATE('0099-01-15'); "
            + "END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx1_0() RETURNS BINARY AS $$ BEGIN RETURN TO_DATE('2020-01-15'); "
            + "END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx1_1() RETURNS BINARY AS $$ BEGIN RETURN "
            + "TO_TIME('10:00:00.123'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx1_2() RETURNS BINARY AS $$ BEGIN RETURN PARSE_JSON('1'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx1_3() RETURNS BINARY AS $$ BEGIN RETURN [1, 2]; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx1_4() RETURNS BINARY AS $$ BEGIN RETURN {'a': 1}; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx1_5() RETURNS BINARY AS $$ BEGIN RETURN TO_BINARY('ABCD', "
            + "'HEX'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx1_6() RETURNS BINARY AS $$ BEGIN RETURN "
            + "TO_TIMESTAMP_NTZ('2020-01-15 10:11:12.5'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx1_7() RETURNS BINARY AS $$ BEGIN RETURN "
            + "TO_TIMESTAMP_TZ('2020-01-15 10:11:12 +02:00'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx1_8() RETURNS BINARY AS $$ BEGIN RETURN "
            + "TO_TIMESTAMP_TZ('2020-01-15 10:11:12 Z'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx1_9() RETURNS BINARY AS $$ BEGIN RETURN "
            + "PARSE_JSON('\"10:00:00\"'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx1_10() RETURNS BINARY AS $$ BEGIN RETURN "
            + "PARSE_JSON('{\"b\":[1,{\"c\":null}],\"a\":\"x\"}'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx1_11() RETURNS BINARY AS $$ BEGIN RETURN []; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx1_12() RETURNS BINARY AS $$ BEGIN RETURN PARSE_JSON('true'); "
            + "END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx1_13() RETURNS BINARY AS $$ BEGIN RETURN CONCAT('10:00:00', "
            + "''); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx1_14() RETURNS BINARY AS $$ BEGIN RETURN CONCAT('2020-01-15 "
            + "10:11:12', ''); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx1_15() RETURNS BINARY AS $$ BEGIN RETURN CONCAT('1', ''); END "
            + "$$");
        engine.execute("CREATE OR REPLACE FUNCTION rx1_16() RETURNS BINARY AS $$ BEGIN RETURN CONCAT('ab', ''); END "
            + "$$");
        engine.execute("CREATE OR REPLACE FUNCTION rx1_17() RETURNS BINARY AS $$ BEGIN RETURN 1 + 1; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx1_18() RETURNS BINARY AS $$ BEGIN RETURN 1.50 + 0; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx1_19() RETURNS BINARY AS $$ BEGIN RETURN 1 = 1; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx1_20() RETURNS BINARY AS $$ BEGIN RETURN TO_DOUBLE(1.5); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx1_21() RETURNS BINARY AS $$ BEGIN RETURN TO_DATE('0099-01-15');"
            + " END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx2_0() RETURNS DATE AS $$ BEGIN RETURN TO_DATE('2020-01-15'); "
            + "END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx2_1() RETURNS DATE AS $$ BEGIN RETURN TO_TIME('10:00:00.123'); "
            + "END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx2_2() RETURNS DATE AS $$ BEGIN RETURN PARSE_JSON('1'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx2_3() RETURNS DATE AS $$ BEGIN RETURN [1, 2]; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx2_4() RETURNS DATE AS $$ BEGIN RETURN {'a': 1}; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx2_5() RETURNS DATE AS $$ BEGIN RETURN TO_BINARY('ABCD', 'HEX');"
            + " END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx2_6() RETURNS DATE AS $$ BEGIN RETURN "
            + "TO_TIMESTAMP_NTZ('2020-01-15 10:11:12.5'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx2_7() RETURNS DATE AS $$ BEGIN RETURN "
            + "TO_TIMESTAMP_TZ('2020-01-15 10:11:12 +02:00'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx2_8() RETURNS DATE AS $$ BEGIN RETURN "
            + "TO_TIMESTAMP_TZ('2020-01-15 10:11:12 Z'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx2_9() RETURNS DATE AS $$ BEGIN RETURN "
            + "PARSE_JSON('\"10:00:00\"'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx2_10() RETURNS DATE AS $$ BEGIN RETURN "
            + "PARSE_JSON('{\"b\":[1,{\"c\":null}],\"a\":\"x\"}'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx2_11() RETURNS DATE AS $$ BEGIN RETURN []; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx2_12() RETURNS DATE AS $$ BEGIN RETURN PARSE_JSON('true'); END "
            + "$$");
        engine.execute("CREATE OR REPLACE FUNCTION rx2_13() RETURNS DATE AS $$ BEGIN RETURN CONCAT('10:00:00', ''); "
            + "END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx2_14() RETURNS DATE AS $$ BEGIN RETURN CONCAT('2020-01-15 "
            + "10:11:12', ''); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx2_15() RETURNS DATE AS $$ BEGIN RETURN CONCAT('1', ''); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx2_16() RETURNS DATE AS $$ BEGIN RETURN CONCAT('ab', ''); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx2_17() RETURNS DATE AS $$ BEGIN RETURN 1 + 1; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx2_18() RETURNS DATE AS $$ BEGIN RETURN 1.50 + 0; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx2_19() RETURNS DATE AS $$ BEGIN RETURN 1 = 1; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx2_20() RETURNS DATE AS $$ BEGIN RETURN TO_DOUBLE(1.5); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx2_21() RETURNS DATE AS $$ BEGIN RETURN TO_DATE('0099-01-15'); "
            + "END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx3_0() RETURNS BOOLEAN AS $$ BEGIN RETURN TO_DATE('2020-01-15');"
            + " END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx3_1() RETURNS BOOLEAN AS $$ BEGIN RETURN "
            + "TO_TIME('10:00:00.123'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx3_2() RETURNS BOOLEAN AS $$ BEGIN RETURN PARSE_JSON('1'); END "
            + "$$");
        engine.execute("CREATE OR REPLACE FUNCTION rx3_3() RETURNS BOOLEAN AS $$ BEGIN RETURN [1, 2]; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx3_4() RETURNS BOOLEAN AS $$ BEGIN RETURN {'a': 1}; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx3_5() RETURNS BOOLEAN AS $$ BEGIN RETURN TO_BINARY('ABCD', "
            + "'HEX'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx3_6() RETURNS BOOLEAN AS $$ BEGIN RETURN "
            + "TO_TIMESTAMP_NTZ('2020-01-15 10:11:12.5'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx3_7() RETURNS BOOLEAN AS $$ BEGIN RETURN "
            + "TO_TIMESTAMP_TZ('2020-01-15 10:11:12 +02:00'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx3_8() RETURNS BOOLEAN AS $$ BEGIN RETURN "
            + "TO_TIMESTAMP_TZ('2020-01-15 10:11:12 Z'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx3_9() RETURNS BOOLEAN AS $$ BEGIN RETURN "
            + "PARSE_JSON('\"10:00:00\"'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx3_10() RETURNS BOOLEAN AS $$ BEGIN RETURN "
            + "PARSE_JSON('{\"b\":[1,{\"c\":null}],\"a\":\"x\"}'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx3_11() RETURNS BOOLEAN AS $$ BEGIN RETURN []; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx3_12() RETURNS BOOLEAN AS $$ BEGIN RETURN PARSE_JSON('true'); "
            + "END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx3_13() RETURNS BOOLEAN AS $$ BEGIN RETURN CONCAT('10:00:00', "
            + "''); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx3_14() RETURNS BOOLEAN AS $$ BEGIN RETURN CONCAT('2020-01-15 "
            + "10:11:12', ''); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx3_15() RETURNS BOOLEAN AS $$ BEGIN RETURN CONCAT('1', ''); END "
            + "$$");
        engine.execute("CREATE OR REPLACE FUNCTION rx3_16() RETURNS BOOLEAN AS $$ BEGIN RETURN CONCAT('ab', ''); END"
            + " $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx3_17() RETURNS BOOLEAN AS $$ BEGIN RETURN 1 + 1; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx3_18() RETURNS BOOLEAN AS $$ BEGIN RETURN 1.50 + 0; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx3_19() RETURNS BOOLEAN AS $$ BEGIN RETURN 1 = 1; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx3_20() RETURNS BOOLEAN AS $$ BEGIN RETURN TO_DOUBLE(1.5); END "
            + "$$");
        engine.execute("CREATE OR REPLACE FUNCTION rx3_21() RETURNS BOOLEAN AS $$ BEGIN RETURN "
            + "TO_DATE('0099-01-15'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx4_0() RETURNS FLOAT AS $$ BEGIN RETURN TO_DATE('2020-01-15'); "
            + "END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx4_1() RETURNS FLOAT AS $$ BEGIN RETURN TO_TIME('10:00:00.123');"
            + " END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx4_2() RETURNS FLOAT AS $$ BEGIN RETURN PARSE_JSON('1'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx4_3() RETURNS FLOAT AS $$ BEGIN RETURN [1, 2]; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx4_4() RETURNS FLOAT AS $$ BEGIN RETURN {'a': 1}; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx4_5() RETURNS FLOAT AS $$ BEGIN RETURN TO_BINARY('ABCD', "
            + "'HEX'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx4_6() RETURNS FLOAT AS $$ BEGIN RETURN "
            + "TO_TIMESTAMP_NTZ('2020-01-15 10:11:12.5'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx4_7() RETURNS FLOAT AS $$ BEGIN RETURN "
            + "TO_TIMESTAMP_TZ('2020-01-15 10:11:12 +02:00'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx4_8() RETURNS FLOAT AS $$ BEGIN RETURN "
            + "TO_TIMESTAMP_TZ('2020-01-15 10:11:12 Z'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx4_9() RETURNS FLOAT AS $$ BEGIN RETURN "
            + "PARSE_JSON('\"10:00:00\"'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx4_10() RETURNS FLOAT AS $$ BEGIN RETURN "
            + "PARSE_JSON('{\"b\":[1,{\"c\":null}],\"a\":\"x\"}'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx4_11() RETURNS FLOAT AS $$ BEGIN RETURN []; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx4_12() RETURNS FLOAT AS $$ BEGIN RETURN PARSE_JSON('true'); END"
            + " $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx4_13() RETURNS FLOAT AS $$ BEGIN RETURN CONCAT('10:00:00', '');"
            + " END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx4_14() RETURNS FLOAT AS $$ BEGIN RETURN CONCAT('2020-01-15 "
            + "10:11:12', ''); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx4_15() RETURNS FLOAT AS $$ BEGIN RETURN CONCAT('1', ''); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx4_16() RETURNS FLOAT AS $$ BEGIN RETURN CONCAT('ab', ''); END "
            + "$$");
        engine.execute("CREATE OR REPLACE FUNCTION rx4_17() RETURNS FLOAT AS $$ BEGIN RETURN 1 + 1; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx4_18() RETURNS FLOAT AS $$ BEGIN RETURN 1.50 + 0; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx4_19() RETURNS FLOAT AS $$ BEGIN RETURN 1 = 1; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx4_20() RETURNS FLOAT AS $$ BEGIN RETURN TO_DOUBLE(1.5); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx4_21() RETURNS FLOAT AS $$ BEGIN RETURN TO_DATE('0099-01-15'); "
            + "END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx5_0() RETURNS VARCHAR AS $$ BEGIN RETURN TO_DATE('2020-01-15');"
            + " END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx5_1() RETURNS VARCHAR AS $$ BEGIN RETURN "
            + "TO_TIME('10:00:00.123'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx5_2() RETURNS VARCHAR AS $$ BEGIN RETURN PARSE_JSON('1'); END "
            + "$$");
        engine.execute("CREATE OR REPLACE FUNCTION rx5_3() RETURNS VARCHAR AS $$ BEGIN RETURN [1, 2]; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx5_4() RETURNS VARCHAR AS $$ BEGIN RETURN {'a': 1}; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx5_5() RETURNS VARCHAR AS $$ BEGIN RETURN TO_BINARY('ABCD', "
            + "'HEX'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx5_6() RETURNS VARCHAR AS $$ BEGIN RETURN "
            + "TO_TIMESTAMP_NTZ('2020-01-15 10:11:12.5'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx5_7() RETURNS VARCHAR AS $$ BEGIN RETURN "
            + "TO_TIMESTAMP_TZ('2020-01-15 10:11:12 +02:00'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx5_8() RETURNS VARCHAR AS $$ BEGIN RETURN "
            + "TO_TIMESTAMP_TZ('2020-01-15 10:11:12 Z'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx5_9() RETURNS VARCHAR AS $$ BEGIN RETURN "
            + "PARSE_JSON('\"10:00:00\"'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx5_10() RETURNS VARCHAR AS $$ BEGIN RETURN "
            + "PARSE_JSON('{\"b\":[1,{\"c\":null}],\"a\":\"x\"}'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx5_11() RETURNS VARCHAR AS $$ BEGIN RETURN []; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx5_12() RETURNS VARCHAR AS $$ BEGIN RETURN PARSE_JSON('true'); "
            + "END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx5_13() RETURNS VARCHAR AS $$ BEGIN RETURN CONCAT('10:00:00', "
            + "''); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx5_14() RETURNS VARCHAR AS $$ BEGIN RETURN CONCAT('2020-01-15 "
            + "10:11:12', ''); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx5_15() RETURNS VARCHAR AS $$ BEGIN RETURN CONCAT('1', ''); END "
            + "$$");
        engine.execute("CREATE OR REPLACE FUNCTION rx5_16() RETURNS VARCHAR AS $$ BEGIN RETURN CONCAT('ab', ''); END"
            + " $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx5_17() RETURNS VARCHAR AS $$ BEGIN RETURN 1 + 1; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx5_18() RETURNS VARCHAR AS $$ BEGIN RETURN 1.50 + 0; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx5_19() RETURNS VARCHAR AS $$ BEGIN RETURN 1 = 1; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx5_20() RETURNS VARCHAR AS $$ BEGIN RETURN TO_DOUBLE(1.5); END "
            + "$$");
        engine.execute("CREATE OR REPLACE FUNCTION rx5_21() RETURNS VARCHAR AS $$ BEGIN RETURN "
            + "TO_DATE('0099-01-15'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx6_0() RETURNS VARCHAR(5) AS $$ BEGIN RETURN "
            + "TO_DATE('2020-01-15'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx6_1() RETURNS VARCHAR(5) AS $$ BEGIN RETURN "
            + "TO_TIME('10:00:00.123'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx6_2() RETURNS VARCHAR(5) AS $$ BEGIN RETURN PARSE_JSON('1'); "
            + "END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx6_3() RETURNS VARCHAR(5) AS $$ BEGIN RETURN [1, 2]; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx6_4() RETURNS VARCHAR(5) AS $$ BEGIN RETURN {'a': 1}; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx6_5() RETURNS VARCHAR(5) AS $$ BEGIN RETURN TO_BINARY('ABCD', "
            + "'HEX'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx6_6() RETURNS VARCHAR(5) AS $$ BEGIN RETURN "
            + "TO_TIMESTAMP_NTZ('2020-01-15 10:11:12.5'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx6_7() RETURNS VARCHAR(5) AS $$ BEGIN RETURN "
            + "TO_TIMESTAMP_TZ('2020-01-15 10:11:12 +02:00'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx6_8() RETURNS VARCHAR(5) AS $$ BEGIN RETURN "
            + "TO_TIMESTAMP_TZ('2020-01-15 10:11:12 Z'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx6_9() RETURNS VARCHAR(5) AS $$ BEGIN RETURN "
            + "PARSE_JSON('\"10:00:00\"'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx6_10() RETURNS VARCHAR(5) AS $$ BEGIN RETURN "
            + "PARSE_JSON('{\"b\":[1,{\"c\":null}],\"a\":\"x\"}'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx6_11() RETURNS VARCHAR(5) AS $$ BEGIN RETURN []; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx6_12() RETURNS VARCHAR(5) AS $$ BEGIN RETURN "
            + "PARSE_JSON('true'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx6_13() RETURNS VARCHAR(5) AS $$ BEGIN RETURN CONCAT('10:00:00',"
            + " ''); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx6_14() RETURNS VARCHAR(5) AS $$ BEGIN RETURN CONCAT('2020-01-15"
            + " 10:11:12', ''); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx6_15() RETURNS VARCHAR(5) AS $$ BEGIN RETURN CONCAT('1', ''); "
            + "END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx6_16() RETURNS VARCHAR(5) AS $$ BEGIN RETURN CONCAT('ab', ''); "
            + "END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx6_17() RETURNS VARCHAR(5) AS $$ BEGIN RETURN 1 + 1; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx6_18() RETURNS VARCHAR(5) AS $$ BEGIN RETURN 1.50 + 0; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx6_19() RETURNS VARCHAR(5) AS $$ BEGIN RETURN 1 = 1; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx6_20() RETURNS VARCHAR(5) AS $$ BEGIN RETURN TO_DOUBLE(1.5); "
            + "END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx6_21() RETURNS VARCHAR(5) AS $$ BEGIN RETURN "
            + "TO_DATE('0099-01-15'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx7_0() RETURNS NUMBER(10,2) AS $$ BEGIN RETURN "
            + "TO_DATE('2020-01-15'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx7_1() RETURNS NUMBER(10,2) AS $$ BEGIN RETURN "
            + "TO_TIME('10:00:00.123'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx7_2() RETURNS NUMBER(10,2) AS $$ BEGIN RETURN PARSE_JSON('1'); "
            + "END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx7_3() RETURNS NUMBER(10,2) AS $$ BEGIN RETURN [1, 2]; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx7_4() RETURNS NUMBER(10,2) AS $$ BEGIN RETURN {'a': 1}; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx7_5() RETURNS NUMBER(10,2) AS $$ BEGIN RETURN TO_BINARY('ABCD',"
            + " 'HEX'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx7_6() RETURNS NUMBER(10,2) AS $$ BEGIN RETURN "
            + "TO_TIMESTAMP_NTZ('2020-01-15 10:11:12.5'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx7_7() RETURNS NUMBER(10,2) AS $$ BEGIN RETURN "
            + "TO_TIMESTAMP_TZ('2020-01-15 10:11:12 +02:00'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx7_8() RETURNS NUMBER(10,2) AS $$ BEGIN RETURN "
            + "TO_TIMESTAMP_TZ('2020-01-15 10:11:12 Z'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx7_9() RETURNS NUMBER(10,2) AS $$ BEGIN RETURN "
            + "PARSE_JSON('\"10:00:00\"'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx7_10() RETURNS NUMBER(10,2) AS $$ BEGIN RETURN "
            + "PARSE_JSON('{\"b\":[1,{\"c\":null}],\"a\":\"x\"}'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx7_11() RETURNS NUMBER(10,2) AS $$ BEGIN RETURN []; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx7_12() RETURNS NUMBER(10,2) AS $$ BEGIN RETURN "
            + "PARSE_JSON('true'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx7_13() RETURNS NUMBER(10,2) AS $$ BEGIN RETURN "
            + "CONCAT('10:00:00', ''); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx7_14() RETURNS NUMBER(10,2) AS $$ BEGIN RETURN "
            + "CONCAT('2020-01-15 10:11:12', ''); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx7_15() RETURNS NUMBER(10,2) AS $$ BEGIN RETURN CONCAT('1', '');"
            + " END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx7_16() RETURNS NUMBER(10,2) AS $$ BEGIN RETURN CONCAT('ab', "
            + "''); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx7_17() RETURNS NUMBER(10,2) AS $$ BEGIN RETURN 1 + 1; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx7_18() RETURNS NUMBER(10,2) AS $$ BEGIN RETURN 1.50 + 0; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx7_19() RETURNS NUMBER(10,2) AS $$ BEGIN RETURN 1 = 1; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx7_20() RETURNS NUMBER(10,2) AS $$ BEGIN RETURN TO_DOUBLE(1.5); "
            + "END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx7_21() RETURNS NUMBER(10,2) AS $$ BEGIN RETURN "
            + "TO_DATE('0099-01-15'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx8_0() RETURNS DECFLOAT AS $$ BEGIN RETURN "
            + "TO_DATE('2020-01-15'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx8_1() RETURNS DECFLOAT AS $$ BEGIN RETURN "
            + "TO_TIME('10:00:00.123'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx8_2() RETURNS DECFLOAT AS $$ BEGIN RETURN PARSE_JSON('1'); END "
            + "$$");
        engine.execute("CREATE OR REPLACE FUNCTION rx8_3() RETURNS DECFLOAT AS $$ BEGIN RETURN [1, 2]; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx8_4() RETURNS DECFLOAT AS $$ BEGIN RETURN {'a': 1}; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx8_5() RETURNS DECFLOAT AS $$ BEGIN RETURN TO_BINARY('ABCD', "
            + "'HEX'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx8_6() RETURNS DECFLOAT AS $$ BEGIN RETURN "
            + "TO_TIMESTAMP_NTZ('2020-01-15 10:11:12.5'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx8_7() RETURNS DECFLOAT AS $$ BEGIN RETURN "
            + "TO_TIMESTAMP_TZ('2020-01-15 10:11:12 +02:00'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx8_8() RETURNS DECFLOAT AS $$ BEGIN RETURN "
            + "TO_TIMESTAMP_TZ('2020-01-15 10:11:12 Z'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx8_9() RETURNS DECFLOAT AS $$ BEGIN RETURN "
            + "PARSE_JSON('\"10:00:00\"'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx8_10() RETURNS DECFLOAT AS $$ BEGIN RETURN "
            + "PARSE_JSON('{\"b\":[1,{\"c\":null}],\"a\":\"x\"}'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx8_11() RETURNS DECFLOAT AS $$ BEGIN RETURN []; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx8_12() RETURNS DECFLOAT AS $$ BEGIN RETURN PARSE_JSON('true'); "
            + "END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx8_13() RETURNS DECFLOAT AS $$ BEGIN RETURN CONCAT('10:00:00', "
            + "''); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx8_14() RETURNS DECFLOAT AS $$ BEGIN RETURN CONCAT('2020-01-15 "
            + "10:11:12', ''); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx8_15() RETURNS DECFLOAT AS $$ BEGIN RETURN CONCAT('1', ''); END"
            + " $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx8_16() RETURNS DECFLOAT AS $$ BEGIN RETURN CONCAT('ab', ''); "
            + "END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx8_17() RETURNS DECFLOAT AS $$ BEGIN RETURN 1 + 1; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx8_18() RETURNS DECFLOAT AS $$ BEGIN RETURN 1.50 + 0; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx8_19() RETURNS DECFLOAT AS $$ BEGIN RETURN 1 = 1; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx8_20() RETURNS DECFLOAT AS $$ BEGIN RETURN TO_DOUBLE(1.5); END "
            + "$$");
        engine.execute("CREATE OR REPLACE FUNCTION rx8_21() RETURNS DECFLOAT AS $$ BEGIN RETURN "
            + "TO_DATE('0099-01-15'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx9_0() RETURNS TIMESTAMP_TZ AS $$ BEGIN RETURN "
            + "TO_DATE('2020-01-15'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx9_1() RETURNS TIMESTAMP_TZ AS $$ BEGIN RETURN "
            + "TO_TIME('10:00:00.123'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx9_2() RETURNS TIMESTAMP_TZ AS $$ BEGIN RETURN PARSE_JSON('1'); "
            + "END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx9_3() RETURNS TIMESTAMP_TZ AS $$ BEGIN RETURN [1, 2]; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx9_4() RETURNS TIMESTAMP_TZ AS $$ BEGIN RETURN {'a': 1}; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx9_5() RETURNS TIMESTAMP_TZ AS $$ BEGIN RETURN TO_BINARY('ABCD',"
            + " 'HEX'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx9_6() RETURNS TIMESTAMP_TZ AS $$ BEGIN RETURN "
            + "TO_TIMESTAMP_NTZ('2020-01-15 10:11:12.5'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx9_7() RETURNS TIMESTAMP_TZ AS $$ BEGIN RETURN "
            + "TO_TIMESTAMP_TZ('2020-01-15 10:11:12 +02:00'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx9_8() RETURNS TIMESTAMP_TZ AS $$ BEGIN RETURN "
            + "TO_TIMESTAMP_TZ('2020-01-15 10:11:12 Z'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx9_9() RETURNS TIMESTAMP_TZ AS $$ BEGIN RETURN "
            + "PARSE_JSON('\"10:00:00\"'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx9_10() RETURNS TIMESTAMP_TZ AS $$ BEGIN RETURN "
            + "PARSE_JSON('{\"b\":[1,{\"c\":null}],\"a\":\"x\"}'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx9_11() RETURNS TIMESTAMP_TZ AS $$ BEGIN RETURN []; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx9_12() RETURNS TIMESTAMP_TZ AS $$ BEGIN RETURN "
            + "PARSE_JSON('true'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx9_13() RETURNS TIMESTAMP_TZ AS $$ BEGIN RETURN "
            + "CONCAT('10:00:00', ''); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx9_14() RETURNS TIMESTAMP_TZ AS $$ BEGIN RETURN "
            + "CONCAT('2020-01-15 10:11:12', ''); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx9_15() RETURNS TIMESTAMP_TZ AS $$ BEGIN RETURN CONCAT('1', '');"
            + " END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx9_16() RETURNS TIMESTAMP_TZ AS $$ BEGIN RETURN CONCAT('ab', "
            + "''); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx9_17() RETURNS TIMESTAMP_TZ AS $$ BEGIN RETURN 1 + 1; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx9_18() RETURNS TIMESTAMP_TZ AS $$ BEGIN RETURN 1.50 + 0; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx9_19() RETURNS TIMESTAMP_TZ AS $$ BEGIN RETURN 1 = 1; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx9_20() RETURNS TIMESTAMP_TZ AS $$ BEGIN RETURN TO_DOUBLE(1.5); "
            + "END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx9_21() RETURNS TIMESTAMP_TZ AS $$ BEGIN RETURN "
            + "TO_DATE('0099-01-15'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx10_0() RETURNS TIMESTAMP_NTZ AS $$ BEGIN RETURN "
            + "TO_DATE('2020-01-15'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx10_1() RETURNS TIMESTAMP_NTZ AS $$ BEGIN RETURN "
            + "TO_TIME('10:00:00.123'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx10_2() RETURNS TIMESTAMP_NTZ AS $$ BEGIN RETURN "
            + "PARSE_JSON('1'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx10_3() RETURNS TIMESTAMP_NTZ AS $$ BEGIN RETURN [1, 2]; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx10_4() RETURNS TIMESTAMP_NTZ AS $$ BEGIN RETURN {'a': 1}; END "
            + "$$");
        engine.execute("CREATE OR REPLACE FUNCTION rx10_5() RETURNS TIMESTAMP_NTZ AS $$ BEGIN RETURN "
            + "TO_BINARY('ABCD', 'HEX'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx10_6() RETURNS TIMESTAMP_NTZ AS $$ BEGIN RETURN "
            + "TO_TIMESTAMP_NTZ('2020-01-15 10:11:12.5'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx10_7() RETURNS TIMESTAMP_NTZ AS $$ BEGIN RETURN "
            + "TO_TIMESTAMP_TZ('2020-01-15 10:11:12 +02:00'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx10_8() RETURNS TIMESTAMP_NTZ AS $$ BEGIN RETURN "
            + "TO_TIMESTAMP_TZ('2020-01-15 10:11:12 Z'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx10_9() RETURNS TIMESTAMP_NTZ AS $$ BEGIN RETURN "
            + "PARSE_JSON('\"10:00:00\"'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx10_10() RETURNS TIMESTAMP_NTZ AS $$ BEGIN RETURN "
            + "PARSE_JSON('{\"b\":[1,{\"c\":null}],\"a\":\"x\"}'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx10_11() RETURNS TIMESTAMP_NTZ AS $$ BEGIN RETURN []; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx10_12() RETURNS TIMESTAMP_NTZ AS $$ BEGIN RETURN "
            + "PARSE_JSON('true'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx10_13() RETURNS TIMESTAMP_NTZ AS $$ BEGIN RETURN "
            + "CONCAT('10:00:00', ''); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx10_14() RETURNS TIMESTAMP_NTZ AS $$ BEGIN RETURN "
            + "CONCAT('2020-01-15 10:11:12', ''); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx10_15() RETURNS TIMESTAMP_NTZ AS $$ BEGIN RETURN CONCAT('1', "
            + "''); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx10_16() RETURNS TIMESTAMP_NTZ AS $$ BEGIN RETURN CONCAT('ab', "
            + "''); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx10_17() RETURNS TIMESTAMP_NTZ AS $$ BEGIN RETURN 1 + 1; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx10_18() RETURNS TIMESTAMP_NTZ AS $$ BEGIN RETURN 1.50 + 0; END "
            + "$$");
        engine.execute("CREATE OR REPLACE FUNCTION rx10_19() RETURNS TIMESTAMP_NTZ AS $$ BEGIN RETURN 1 = 1; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx10_20() RETURNS TIMESTAMP_NTZ AS $$ BEGIN RETURN "
            + "TO_DOUBLE(1.5); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rx10_21() RETURNS TIMESTAMP_NTZ AS $$ BEGIN RETURN "
            + "TO_DATE('0099-01-15'); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rn0() RETURNS VARCHAR AS $$ BEGIN IF (TRUE) THEN RETURN "
            + "TO_TIME('10:00'); END IF; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rn1() RETURNS TIME AS $$ BEGIN IF (TRUE) THEN RETURN "
            + "TO_TIME('10:00'); END IF; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rn2() RETURNS DATE AS $$ BEGIN IF (TRUE) THEN RETURN "
            + "TO_TIME('10:00'); END IF; END $$");
        assertCells(new String[][] {
            {"SELECT rx0_0()",
                "REFUSED Can't parse '2020-01-15' as time with format 'HH24:MI:SS.FF'"},
            {"SELECT TO_VARCHAR(rx0_1(), 'HH24:MI:SS.FF3')",
                "10:00:00.123"},
            {"SELECT rx0_2()",
                "REFUSED Can't parse '1' as time with format 'HH24:MI:SS.FF'"},
            {"SELECT rx0_3()",
                "REFUSED Can't parse '[ |   1, |   2 | ]' as time with format 'HH24:MI:SS.FF'"},
            {"SELECT rx0_4()",
                "REFUSED Can't parse '{ |   \"a\": 1 | }' as time with format 'HH24:MI:SS.FF'"},
            {"SELECT rx0_5()",
                "REFUSED Can't parse 'ABCD' as time with format 'HH24:MI:SS.FF'"},
            {"SELECT rx0_6()",
                "REFUSED Can't parse '2020-01-15 10:11:12.500000000' as time with format 'HH24:MI:SS.FF'"},
            {"SELECT rx0_7()",
                "REFUSED Can't parse '2020-01-15 10:11:12.000000000 +02:00' as time with format "
                + "'HH24:MI:SS.FF'"},
            {"SELECT rx0_8()",
                "REFUSED Can't parse '2020-01-15 10:11:12.000000000 -00:00' as time with format "
                + "'HH24:MI:SS.FF'"},
            {"SELECT rx0_9()",
                "REFUSED Can't parse '\"10:00:00\"' as time with format 'HH24:MI:SS.FF'"},
            {"SELECT rx0_10()",
                "REFUSED Can't parse '{ |   \"a\": \"x\", |   \"b\": [ |     1, |     { |       \"c\": null |"
                + "     } |   ] | }' as time with format 'HH24:MI:SS.FF'"},
            {"SELECT rx0_11()",
                "REFUSED Can't parse '[]' as time with format 'HH24:MI:SS.FF'"},
            {"SELECT rx0_12()",
                "REFUSED Can't parse 'true' as time with format 'HH24:MI:SS.FF'"},
            {"SELECT rx0_13()",
                "10:00"},
            {"SELECT rx0_14()",
                "REFUSED Can't parse '2020-01-15 10:11:12' as time with format 'HH24:MI:SS.FF'"},
            {"SELECT rx0_15()",
                "REFUSED Can't parse '1' as time with format 'HH24:MI:SS.FF'"},
            {"SELECT rx0_16()",
                "REFUSED Can't parse 'ab' as time with format 'HH24:MI:SS.FF'"},
            {"SELECT rx0_17()",
                "REFUSED Can't parse '2' as time with format 'HH24:MI:SS.FF'"},
            {"SELECT rx0_18()",
                "REFUSED Can't parse '1.5' as time with format 'HH24:MI:SS.FF'"},
            {"SELECT rx0_19()",
                "REFUSED Can't parse 'true' as time with format 'HH24:MI:SS.FF'"},
            {"SELECT rx0_20()",
                "REFUSED Can't parse '1.5' as time with format 'HH24:MI:SS.FF'"},
            {"SELECT rx0_21()",
                "REFUSED Can't parse '99-01-15' as time with format 'HH24:MI:SS.FF'"},
            {"SELECT rx1_0()",
                "REFUSED The following string is not a legal hex-encoded value: '2020-01-15'"},
            {"SELECT rx1_1()",
                "REFUSED The following string is not a legal hex-encoded value: '10:00:00.123000000'"},
            {"SELECT rx1_2()",
                "REFUSED The following string is not a legal hex-encoded value: '1'"},
            {"SELECT rx1_3()",
                "REFUSED The following string is not a legal hex-encoded value: '[ |   1, |   2 | ]'"},
            {"SELECT rx1_4()",
                "REFUSED The following string is not a legal hex-encoded value: '{ |   \"a\": 1 | }'"},
            {"SELECT rx1_5()",
                "ABCD"},
            {"SELECT rx1_6()",
                "REFUSED The following string is not a legal hex-encoded value: '2020-01-15 "
                + "10:11:12.500000000'"},
            {"SELECT rx1_7()",
                "REFUSED The following string is not a legal hex-encoded value: '2020-01-15 "
                + "10:11:12.000000000 +02:00'"},
            {"SELECT rx1_8()",
                "REFUSED The following string is not a legal hex-encoded value: '2020-01-15 "
                + "10:11:12.000000000 -00:00'"},
            {"SELECT rx1_9()",
                "REFUSED The following string is not a legal hex-encoded value: '\"10:00:00\"'"},
            {"SELECT rx1_10()",
                "REFUSED The following string is not a legal hex-encoded value: '{ |   \"a\": \"x\", |   "
                + "\"b\": [ |     1, |     { |       \"c\": null |     } |   ] | }'"},
            {"SELECT rx1_11()",
                "REFUSED The following string is not a legal hex-encoded value: '[]'"},
            {"SELECT rx1_12()",
                "REFUSED The following string is not a legal hex-encoded value: 'true'"},
            {"SELECT rx1_13()",
                "REFUSED The following string is not a legal hex-encoded value: '10:00:00'"},
            {"SELECT rx1_14()",
                "REFUSED The following string is not a legal hex-encoded value: '2020-01-15 10:11:12'"},
            {"SELECT rx1_15()",
                "REFUSED The following string is not a legal hex-encoded value: '1'"},
            {"SELECT rx1_16()",
                "AB"},
            {"SELECT rx1_17()",
                "REFUSED The following string is not a legal hex-encoded value: '2'"},
            {"SELECT rx1_18()",
                "REFUSED The following string is not a legal hex-encoded value: '1.5'"},
            {"SELECT rx1_19()",
                "REFUSED The following string is not a legal hex-encoded value: 'true'"},
            {"SELECT rx1_20()",
                "REFUSED The following string is not a legal hex-encoded value: '1.5'"},
            {"SELECT rx1_21()",
                "REFUSED The following string is not a legal hex-encoded value: '99-01-15'"},
            {"SELECT rx2_0()",
                "2020-01-15"},
            {"SELECT rx2_1()",
                "REFUSED Can't parse '10:00:00.123000000' as date with format 'YYYY-MM-DD'"},
            {"SELECT rx2_2()",
                "REFUSED Can't parse '1' as date with format 'YYYY-MM-DD'"},
            {"SELECT rx2_3()",
                "REFUSED Can't parse '[ |   1, |   2 | ]' as date with format 'YYYY-MM-DD'"},
            {"SELECT rx2_4()",
                "REFUSED Can't parse '{ |   \"a\": 1 | }' as date with format 'YYYY-MM-DD'"},
            {"SELECT rx2_5()",
                "REFUSED Can't parse 'ABCD' as date with format 'YYYY-MM-DD'"},
            {"SELECT rx2_6()",
                "REFUSED Can't parse '2020-01-15 10:11:12.500000000' as date with format 'YYYY-MM-DD'"},
            {"SELECT rx2_7()",
                "REFUSED Can't parse '2020-01-15 10:11:12.000000000 +02:00' as date with format 'YYYY-MM-DD'"},
            {"SELECT rx2_8()",
                "REFUSED Can't parse '2020-01-15 10:11:12.000000000 -00:00' as date with format 'YYYY-MM-DD'"},
            {"SELECT rx2_9()",
                "REFUSED Can't parse '\"10:00:00\"' as date with format 'YYYY-MM-DD'"},
            {"SELECT rx2_10()",
                "REFUSED Can't parse '{ |   \"a\": \"x\", |   \"b\": [ |     1, |     { |       \"c\": null |"
                + "     } |   ] | }' as date with format 'YYYY-MM-DD'"},
            {"SELECT rx2_11()",
                "REFUSED Can't parse '[]' as date with format 'YYYY-MM-DD'"},
            {"SELECT rx2_12()",
                "REFUSED Can't parse 'true' as date with format 'YYYY-MM-DD'"},
            {"SELECT rx2_13()",
                "REFUSED Can't parse '10:00:00' as date with format 'YYYY-MM-DD'"},
            {"SELECT rx2_14()",
                "REFUSED Can't parse '2020-01-15 10:11:12' as date with format 'YYYY-MM-DD'"},
            {"SELECT rx2_15()",
                "REFUSED Can't parse '1' as date with format 'YYYY-MM-DD'"},
            {"SELECT rx2_16()",
                "REFUSED Can't parse 'ab' as date with format 'YYYY-MM-DD'"},
            {"SELECT rx2_17()",
                "REFUSED Can't parse '2' as date with format 'YYYY-MM-DD'"},
            {"SELECT rx2_18()",
                "REFUSED Can't parse '1.5' as date with format 'YYYY-MM-DD'"},
            {"SELECT rx2_19()",
                "REFUSED Can't parse 'true' as date with format 'YYYY-MM-DD'"},
            {"SELECT rx2_20()",
                "REFUSED Can't parse '1.5' as date with format 'YYYY-MM-DD'"},
            {"SELECT rx2_21()",
                "0099-01-15"},
            {"SELECT rx3_0()",
                "REFUSED Boolean value '2020-01-15' is not recognized"},
            {"SELECT rx3_1()",
                "REFUSED Boolean value '10:00:00.123000000' is not recognized"},
            {"SELECT rx3_2()",
                "true"},
            {"SELECT rx3_3()",
                "REFUSED Boolean value '[ |   1, |   2 | ]' is not recognized"},
            {"SELECT rx3_4()",
                "REFUSED Boolean value '{ |   \"a\": 1 | }' is not recognized"},
            {"SELECT rx3_5()",
                "REFUSED Boolean value 'ABCD' is not recognized"},
            {"SELECT rx3_6()",
                "REFUSED Boolean value '2020-01-15 10:11:12.500000000' is not recognized"},
            {"SELECT rx3_7()",
                "REFUSED Boolean value '2020-01-15 10:11:12.000000000 +02:00' is not recognized"},
            {"SELECT rx3_8()",
                "REFUSED Boolean value '2020-01-15 10:11:12.000000000 -00:00' is not recognized"},
            {"SELECT rx3_9()",
                "REFUSED Boolean value '\"10:00:00\"' is not recognized"},
            {"SELECT rx3_10()",
                "REFUSED Boolean value '{ |   \"a\": \"x\", |   \"b\": [ |     1, |     { |       \"c\": null"
                + " |     } |   ] | }' is not recognized"},
            {"SELECT rx3_11()",
                "REFUSED Boolean value '[]' is not recognized"},
            {"SELECT rx3_12()",
                "true"},
            {"SELECT rx3_13()",
                "REFUSED Boolean value '10:00:00' is not recognized"},
            {"SELECT rx3_14()",
                "REFUSED Boolean value '2020-01-15 10:11:12' is not recognized"},
            {"SELECT rx3_15()",
                "true"},
            {"SELECT rx3_16()",
                "REFUSED Boolean value 'ab' is not recognized"},
            {"SELECT rx3_17()",
                "REFUSED Boolean value '2' is not recognized"},
            {"SELECT rx3_18()",
                "REFUSED Boolean value '1.5' is not recognized"},
            {"SELECT rx3_19()",
                "true"},
            {"SELECT rx3_20()",
                "REFUSED Boolean value '1.5' is not recognized"},
            {"SELECT rx3_21()",
                "REFUSED Boolean value '99-01-15' is not recognized"},
            {"SELECT rx4_0()",
                "REFUSED Numeric value '2020-01-15' is not recognized"},
            {"SELECT rx4_1()",
                "REFUSED Numeric value '10:00:00.123000000' is not recognized"},
            {"SELECT rx4_3()",
                "REFUSED Numeric value '[ |   1, |   2 | ]' is not recognized"},
            {"SELECT rx4_4()",
                "REFUSED Numeric value '{ |   \"a\": 1 | }' is not recognized"},
            {"SELECT rx4_5()",
                "REFUSED Numeric value 'ABCD' is not recognized"},
            {"SELECT rx4_6()",
                "REFUSED Numeric value '2020-01-15 10:11:12.500000000' is not recognized"},
            {"SELECT rx4_7()",
                "REFUSED Numeric value '2020-01-15 10:11:12.000000000 +02:00' is not recognized"},
            {"SELECT rx4_8()",
                "REFUSED Numeric value '2020-01-15 10:11:12.000000000 -00:00' is not recognized"},
            {"SELECT rx4_9()",
                "REFUSED Numeric value '\"10:00:00\"' is not recognized"},
            {"SELECT rx4_10()",
                "REFUSED Numeric value '{ |   \"a\": \"x\", |   \"b\": [ |     1, |     { |       \"c\": null"
                + " |     } |   ] | }' is not recognized"},
            {"SELECT rx4_11()",
                "REFUSED Numeric value '[]' is not recognized"},
            {"SELECT rx4_12()",
                "REFUSED Numeric value 'true' is not recognized"},
            {"SELECT rx4_13()",
                "REFUSED Numeric value '10:00:00' is not recognized"},
            {"SELECT rx4_14()",
                "REFUSED Numeric value '2020-01-15 10:11:12' is not recognized"},
            {"SELECT rx4_16()",
                "REFUSED Numeric value 'ab' is not recognized"},
            {"SELECT rx4_18()",
                "1.5"},
            {"SELECT rx4_19()",
                "REFUSED Numeric value 'true' is not recognized"},
            {"SELECT rx4_20()",
                "1.5"},
            {"SELECT rx4_21()",
                "REFUSED Numeric value '99-01-15' is not recognized"},
            {"SELECT rx5_0()",
                "2020-01-15"},
            {"SELECT rx5_1()",
                "10:00:00.123000000"},
            {"SELECT rx5_2()",
                "1"},
            {"SELECT rx5_4()",
                "{ |   \"a\": 1 | }"},
            {"SELECT rx5_5()",
                "ABCD"},
            {"SELECT rx5_6()",
                "2020-01-15 10:11:12.500000000"},
            {"SELECT rx5_7()",
                "2020-01-15 10:11:12.000000000 +02:00"},
            {"SELECT rx5_8()",
                "2020-01-15 10:11:12.000000000 -00:00"},
            {"SELECT rx5_9()",
                "\"10:00:00\""},
            {"SELECT rx5_11()",
                "[]"},
            {"SELECT rx5_12()",
                "true"},
            {"SELECT rx5_13()",
                "10:00:00"},
            {"SELECT rx5_14()",
                "2020-01-15 10:11:12"},
            {"SELECT rx5_15()",
                "1"},
            {"SELECT rx5_16()",
                "ab"},
            {"SELECT rx5_17()",
                "2"},
            {"SELECT rx5_18()",
                "1.5"},
            {"SELECT rx5_19()",
                "true"},
            {"SELECT rx5_20()",
                "1.5"},
            {"SELECT rx5_21()",
                "99-01-15"},
            {"SELECT rx6_0()",
                "REFUSED String '2020-01-15' is too long and would be truncated"},
            {"SELECT rx6_1()",
                "REFUSED String '10:00:00.123000000' is too long and would be truncated"},
            {"SELECT rx6_2()",
                "1"},
            {"SELECT rx6_3()",
                "REFUSED String '[ |   1, |   2 | ]' is too long and would be truncated"},
            {"SELECT rx6_4()",
                "REFUSED String '{ |   \"a\": 1 | }' is too long and would be truncated"},
            {"SELECT rx6_5()",
                "ABCD"},
            {"SELECT rx6_6()",
                "REFUSED String '2020-01-15 10:11:12.500000000' is too long and would be truncated"},
            {"SELECT rx6_7()",
                "REFUSED String '2020-01-15 10:11:12.000000000 +02:00' is too long and would be truncated"},
            {"SELECT rx6_8()",
                "REFUSED String '2020-01-15 10:11:12.000000000 -00:00' is too long and would be truncated"},
            {"SELECT rx6_9()",
                "REFUSED String '\"10:00:00\"' is too long and would be truncated"},
            {"SELECT rx6_10()",
                "REFUSED String '{ |   \"a\": \"x\", |   \"b\": [ |     1, |     { |       \"c\": null |     "
                + "} |   ] | }' is too long and would be truncated"},
            {"SELECT rx6_11()",
                "[]"},
            {"SELECT rx6_12()",
                "true"},
            {"SELECT rx6_13()",
                "REFUSED String '10:00:00' is too long and would be truncated"},
            {"SELECT rx6_14()",
                "REFUSED String '2020-01-15 10:11:12' is too long and would be truncated"},
            {"SELECT rx6_15()",
                "1"},
            {"SELECT rx6_16()",
                "ab"},
            {"SELECT rx6_17()",
                "2"},
            {"SELECT rx6_18()",
                "1.5"},
            {"SELECT rx6_19()",
                "true"},
            {"SELECT rx6_20()",
                "1.5"},
            {"SELECT rx6_21()",
                "REFUSED String '99-01-15' is too long and would be truncated"},
            {"SELECT rx7_0()",
                "REFUSED Numeric value '2020-01-15' is not recognized"},
            {"SELECT rx7_1()",
                "REFUSED Numeric value '10:00:00.123000000' is not recognized"},
            {"SELECT rx7_2()",
                "1.00"},
            {"SELECT rx7_3()",
                "REFUSED Numeric value '[ |   1, |   2 | ]' is not recognized"},
            {"SELECT rx7_4()",
                "REFUSED Numeric value '{ |   \"a\": 1 | }' is not recognized"},
            {"SELECT rx7_5()",
                "REFUSED Numeric value 'ABCD' is not recognized"},
            {"SELECT rx7_6()",
                "REFUSED Numeric value '2020-01-15 10:11:12.500000000' is not recognized"},
            {"SELECT rx7_7()",
                "REFUSED Numeric value '2020-01-15 10:11:12.000000000 +02:00' is not recognized"},
            {"SELECT rx7_8()",
                "REFUSED Numeric value '2020-01-15 10:11:12.000000000 -00:00' is not recognized"},
            {"SELECT rx7_9()",
                "REFUSED Numeric value '\"10:00:00\"' is not recognized"},
            {"SELECT rx7_10()",
                "REFUSED Numeric value '{ |   \"a\": \"x\", |   \"b\": [ |     1, |     { |       \"c\": null"
                + " |     } |   ] | }' is not recognized"},
            {"SELECT rx7_11()",
                "REFUSED Numeric value '[]' is not recognized"},
            {"SELECT rx7_12()",
                "REFUSED Numeric value 'true' is not recognized"},
            {"SELECT rx7_13()",
                "REFUSED Numeric value '10:00:00' is not recognized"},
            {"SELECT rx7_14()",
                "REFUSED Numeric value '2020-01-15 10:11:12' is not recognized"},
            {"SELECT rx7_15()",
                "1.00"},
            {"SELECT rx7_16()",
                "REFUSED Numeric value 'ab' is not recognized"},
            {"SELECT rx7_17()",
                "2.00"},
            {"SELECT rx7_18()",
                "1.50"},
            {"SELECT rx7_19()",
                "REFUSED Numeric value 'true' is not recognized"},
            {"SELECT rx7_20()",
                "1.50"},
            {"SELECT rx7_21()",
                "REFUSED Numeric value '99-01-15' is not recognized"},
            {"SELECT rx8_0()",
                "REFUSED Numeric value '2020-01-15' is not recognized"},
            {"SELECT rx8_1()",
                "REFUSED Numeric value '10:00:00.123000000' is not recognized"},
            {"SELECT rx8_2()",
                "1"},
            {"SELECT rx8_3()",
                "REFUSED Numeric value '[ |   1, |   2 | ]' is not recognized"},
            {"SELECT rx8_4()",
                "REFUSED Numeric value '{ |   \"a\": 1 | }' is not recognized"},
            {"SELECT rx8_5()",
                "REFUSED Numeric value 'ABCD' is not recognized"},
            {"SELECT rx8_6()",
                "REFUSED Numeric value '2020-01-15 10:11:12.500000000' is not recognized"},
            {"SELECT rx8_7()",
                "REFUSED Numeric value '2020-01-15 10:11:12.000000000 +02:00' is not recognized"},
            {"SELECT rx8_8()",
                "REFUSED Numeric value '2020-01-15 10:11:12.000000000 -00:00' is not recognized"},
            {"SELECT rx8_9()",
                "REFUSED Numeric value '\"10:00:00\"' is not recognized"},
            {"SELECT rx8_10()",
                "REFUSED Numeric value '{ |   \"a\": \"x\", |   \"b\": [ |     1, |     { |       \"c\": null"
                + " |     } |   ] | }' is not recognized"},
            {"SELECT rx8_11()",
                "REFUSED Numeric value '[]' is not recognized"},
            {"SELECT rx8_12()",
                "REFUSED Numeric value 'true' is not recognized"},
            {"SELECT rx8_13()",
                "REFUSED Numeric value '10:00:00' is not recognized"},
            {"SELECT rx8_14()",
                "REFUSED Numeric value '2020-01-15 10:11:12' is not recognized"},
            {"SELECT rx8_15()",
                "1"},
            {"SELECT rx8_16()",
                "REFUSED Numeric value 'ab' is not recognized"},
            {"SELECT rx8_17()",
                "2"},
            {"SELECT rx8_18()",
                "1.5"},
            {"SELECT rx8_19()",
                "REFUSED Numeric value 'true' is not recognized"},
            {"SELECT rx8_20()",
                "1.5"},
            {"SELECT rx8_21()",
                "REFUSED Numeric value '99-01-15' is not recognized"},
            {"SELECT rx9_0()",
                "REFUSED Can't parse '2020-01-15' as timestamp with format 'YYYY-MM-DD HH24:MI:SS.FF TZH:TZM'"},
            {"SELECT rx9_1()",
                "REFUSED Can't parse '10:00:00.123000000' as timestamp with format 'YYYY-MM-DD HH24:MI:SS.FF "
                + "TZH:TZM'"},
            {"SELECT rx9_2()",
                "REFUSED Can't parse '1' as timestamp with format 'YYYY-MM-DD HH24:MI:SS.FF TZH:TZM'"},
            {"SELECT rx9_3()",
                "REFUSED Can't parse '[ |   1, |   2 | ]' as timestamp with format 'YYYY-MM-DD HH24:MI:SS.FF "
                + "TZH:TZM'"},
            {"SELECT rx9_4()",
                "REFUSED Can't parse '{ |   \"a\": 1 | }' as timestamp with format 'YYYY-MM-DD HH24:MI:SS.FF "
                + "TZH:TZM'"},
            {"SELECT rx9_5()",
                "REFUSED Can't parse 'ABCD' as timestamp with format 'YYYY-MM-DD HH24:MI:SS.FF TZH:TZM'"},
            {"SELECT rx9_6()",
                "REFUSED Can't parse '2020-01-15 10:11:12.500000000' as timestamp with format 'YYYY-MM-DD "
                + "HH24:MI:SS.FF TZH:TZM'"},
            {"SELECT rx9_7()",
                "2020-01-15T10:11:12+02:00"},
            {"SELECT rx9_8()",
                "2020-01-15T10:11:12Z"},
            {"SELECT rx9_9()",
                "REFUSED Can't parse '\"10:00:00\"' as timestamp with format 'YYYY-MM-DD HH24:MI:SS.FF "
                + "TZH:TZM'"},
            {"SELECT rx9_10()",
                "REFUSED Can't parse '{ |   \"a\": \"x\", |   \"b\": [ |     1, |     { |       \"c\": null |"
                + "     } |   ] | }' as timestamp with format 'YYYY-MM-DD HH24:MI:SS.FF TZH:TZM'"},
            {"SELECT rx9_11()",
                "REFUSED Can't parse '[]' as timestamp with format 'YYYY-MM-DD HH24:MI:SS.FF TZH:TZM'"},
            {"SELECT rx9_12()",
                "REFUSED Can't parse 'true' as timestamp with format 'YYYY-MM-DD HH24:MI:SS.FF TZH:TZM'"},
            {"SELECT rx9_13()",
                "REFUSED Can't parse '10:00:00' as timestamp with format 'YYYY-MM-DD HH24:MI:SS.FF TZH:TZM'"},
            {"SELECT rx9_14()",
                "REFUSED Can't parse '2020-01-15 10:11:12' as timestamp with format 'YYYY-MM-DD HH24:MI:SS.FF"
                + " TZH:TZM'"},
            {"SELECT rx9_15()",
                "REFUSED Can't parse '1' as timestamp with format 'YYYY-MM-DD HH24:MI:SS.FF TZH:TZM'"},
            {"SELECT rx9_16()",
                "REFUSED Can't parse 'ab' as timestamp with format 'YYYY-MM-DD HH24:MI:SS.FF TZH:TZM'"},
            {"SELECT rx9_17()",
                "REFUSED Can't parse '2' as timestamp with format 'YYYY-MM-DD HH24:MI:SS.FF TZH:TZM'"},
            {"SELECT rx9_18()",
                "REFUSED Can't parse '1.5' as timestamp with format 'YYYY-MM-DD HH24:MI:SS.FF TZH:TZM'"},
            {"SELECT rx9_19()",
                "REFUSED Can't parse 'true' as timestamp with format 'YYYY-MM-DD HH24:MI:SS.FF TZH:TZM'"},
            {"SELECT rx9_20()",
                "REFUSED Can't parse '1.5' as timestamp with format 'YYYY-MM-DD HH24:MI:SS.FF TZH:TZM'"},
            {"SELECT rx9_21()",
                "REFUSED Can't parse '99-01-15' as timestamp with format 'YYYY-MM-DD HH24:MI:SS.FF TZH:TZM'"},
            {"SELECT rx10_0()",
                "REFUSED Can't parse '2020-01-15' as timestamp with format 'YYYY-MM-DD HH24:MI:SS.FF'"},
            {"SELECT rx10_1()",
                "REFUSED Can't parse '10:00:00.123000000' as timestamp with format 'YYYY-MM-DD HH24:MI:SS.FF'"},
            {"SELECT rx10_2()",
                "REFUSED Can't parse '1' as timestamp with format 'YYYY-MM-DD HH24:MI:SS.FF'"},
            {"SELECT rx10_3()",
                "REFUSED Can't parse '[ |   1, |   2 | ]' as timestamp with format 'YYYY-MM-DD HH24:MI:SS.FF'"},
            {"SELECT rx10_4()",
                "REFUSED Can't parse '{ |   \"a\": 1 | }' as timestamp with format 'YYYY-MM-DD HH24:MI:SS.FF'"},
            {"SELECT rx10_5()",
                "REFUSED Can't parse 'ABCD' as timestamp with format 'YYYY-MM-DD HH24:MI:SS.FF'"},
            {"SELECT rx10_6()",
                "2020-01-15T10:11:12.500"},
            {"SELECT rx10_7()",
                "REFUSED Can't parse '2020-01-15 10:11:12.000000000 +02:00' as timestamp with format "
                + "'YYYY-MM-DD HH24:MI:SS.FF'"},
            {"SELECT rx10_8()",
                "REFUSED Can't parse '2020-01-15 10:11:12.000000000 -00:00' as timestamp with format "
                + "'YYYY-MM-DD HH24:MI:SS.FF'"},
            {"SELECT rx10_9()",
                "REFUSED Can't parse '\"10:00:00\"' as timestamp with format 'YYYY-MM-DD HH24:MI:SS.FF'"},
            {"SELECT rx10_10()",
                "REFUSED Can't parse '{ |   \"a\": \"x\", |   \"b\": [ |     1, |     { |       \"c\": null |"
                + "     } |   ] | }' as timestamp with format 'YYYY-MM-DD HH24:MI:SS.FF'"},
            {"SELECT rx10_11()",
                "REFUSED Can't parse '[]' as timestamp with format 'YYYY-MM-DD HH24:MI:SS.FF'"},
            {"SELECT rx10_12()",
                "REFUSED Can't parse 'true' as timestamp with format 'YYYY-MM-DD HH24:MI:SS.FF'"},
            {"SELECT rx10_13()",
                "REFUSED Can't parse '10:00:00' as timestamp with format 'YYYY-MM-DD HH24:MI:SS.FF'"},
            {"SELECT rx10_14()",
                "2020-01-15T10:11:12"},
            {"SELECT rx10_15()",
                "REFUSED Can't parse '1' as timestamp with format 'YYYY-MM-DD HH24:MI:SS.FF'"},
            {"SELECT rx10_16()",
                "REFUSED Can't parse 'ab' as timestamp with format 'YYYY-MM-DD HH24:MI:SS.FF'"},
            {"SELECT rx10_17()",
                "REFUSED Can't parse '2' as timestamp with format 'YYYY-MM-DD HH24:MI:SS.FF'"},
            {"SELECT rx10_18()",
                "REFUSED Can't parse '1.5' as timestamp with format 'YYYY-MM-DD HH24:MI:SS.FF'"},
            {"SELECT rx10_19()",
                "REFUSED Can't parse 'true' as timestamp with format 'YYYY-MM-DD HH24:MI:SS.FF'"},
            {"SELECT rx10_20()",
                "REFUSED Can't parse '1.5' as timestamp with format 'YYYY-MM-DD HH24:MI:SS.FF'"},
            {"SELECT rx10_21()",
                "REFUSED Can't parse '99-01-15' as timestamp with format 'YYYY-MM-DD HH24:MI:SS.FF'"},
            {"SELECT rn0()",
                "10:00:00.000000000"},
            {"SELECT rn1()",
                "10:00"},
            {"SELECT rn2()",
                "REFUSED Can't parse '10:00:00.000000000' as date with format 'YYYY-MM-DD'"},
        });
    }

    @Test
    public void anIndentOfZeroStillBreaksLines() {
        engine.execute("ALTER SESSION SET JSON_INDENT = 0");
        engine.execute("CREATE OR REPLACE FUNCTION rz1() RETURNS VARCHAR AS $$ BEGIN RETURN {'a': [1, 2]}; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rz2() RETURNS TIME AS $$ BEGIN RETURN {'a': [1, 2]}; END $$");
        assertCells(new String[][] {
            {"SELECT rz2()",
                "REFUSED Can't parse '{ | \"a\": [ | 1, | 2 | ] | }' as time with format 'HH24:MI:SS.FF'"},
        });
    }

    @Test
    public void anIndentOfFourLaysOutWider() {
        engine.execute("ALTER SESSION SET JSON_INDENT = 4");
        engine.execute("CREATE OR REPLACE FUNCTION rf1() RETURNS VARCHAR AS $$ BEGIN RETURN {'a': [1, 2]}; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION rf2() RETURNS BINARY AS $$ BEGIN RETURN [{}, 'x']; END $$");
        assertCells(new String[][] {
            {"SELECT rf2()",
                "REFUSED The following string is not a legal hex-encoded value: '[ |     {}, |     \"x\" | ]'"},
        });
    }

    @Test
    public void aFractionsPointMayBeLeftOut() {
        assertCells(new String[][] {
            {"SELECT TO_TIME('10:00:00', 'HH24:MI:SS.FF')::VARCHAR",
                "10:00:00"},
            {"SELECT TO_TIMESTAMP_NTZ('2020-01-15 10:11:12', 'YYYY-MM-DD HH24:MI:SS.FF')::VARCHAR",
                "2020-01-15 10:11:12.000"},
            {"SELECT TO_TIME('10:00:00.', 'HH24:MI:SS.FF')::VARCHAR",
                "10:00:00"},
            {"SELECT TO_TIME('10:00', 'HH24:MI:SS.FF')::VARCHAR",
                "REFUSED Can't parse '10:00' as time with format 'HH24:MI:SS.FF'"},
            {"SELECT TO_TIMESTAMP_TZ('2020-01-15 10:11:12 +02:00', 'YYYY-MM-DD HH24:MI:SS.FF "
                + "TZH:TZM')::VARCHAR",
                "2020-01-15 10:11:12.000 +0200"},
            {"SELECT TO_TIME('10:00:00x', 'HH24:MI:SS.FF')::VARCHAR",
                "REFUSED Can't parse '10:00:00x' as time with format 'HH24:MI:SS.FF'"},
            {"SELECT TO_TIME('10:00:00', 'HH24:MI:SS.FF3')::VARCHAR",
                "10:00:00"},
            {"SELECT TO_TIME('10:00:00', 'HH24:MI:SS:FF')::VARCHAR",
                "REFUSED Can't parse '10:00:00' as time with format 'HH24:MI:SS:FF'"},
            {"SELECT TO_TIME('10:00:00', 'HH24:MI:SSFF')::VARCHAR",
                "10:00:00"},
            {"SELECT TO_TIME('10:00', 'HH24:MI:SS')::VARCHAR",
                "REFUSED Can't parse '10:00' as time with format 'HH24:MI:SS'"},
            {"SELECT TO_TIME('10:00:00', 'HH24:MI:SS.FF9')::VARCHAR",
                "10:00:00"},
            {"SELECT TO_TIMESTAMP_NTZ('2020-01-15 10:11', 'YYYY-MM-DD HH24:MI:SS.FF')::VARCHAR",
                "REFUSED Can't parse '2020-01-15 10:11' as timestamp with format 'YYYY-MM-DD HH24:MI:SS.FF'"},
            {"SELECT TO_TIMESTAMP_TZ('2020-01-15 10:11:12', 'YYYY-MM-DD HH24:MI:SS.FF TZH:TZM')::VARCHAR",
                "REFUSED Can't parse '2020-01-15 10:11:12' as timestamp with format 'YYYY-MM-DD HH24:MI:SS.FF"
                + " TZH:TZM'"},
            {"SELECT TO_TIME('10:00:00 ', 'HH24:MI:SS.FF')::VARCHAR",
                "10:00:00"},
        });
    }
}
