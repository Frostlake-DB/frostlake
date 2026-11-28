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
 * A block-bodied function's or procedure's RETURN of a typed name of another type than the RETURNS type, converted
 * as the cast to that type converts it (live-verified): a temporal under a text type is its output-format text, its
 * width checked; a VARIANT under a scalar type is the variant's cast, refused as {@code Failed to cast variant value
 * … to …}; a JSON null is NULL; and a type no cast carries is refused while the body compiles, 92225 / P0000.
 */
public class BlockReturnConversionTest extends MeasuredCellsTestSupport {

    @Test
    public void functionReturn() {
        engine.execute("ALTER SESSION SET TIMEZONE='UTC'");
        engine.execute("CREATE OR REPLACE FUNCTION fm_0_dt() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE v DATE "
            + "DEFAULT '2020-01-15'::DATE; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_0_tm() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE v TIME "
            + "DEFAULT '10:00:00'::TIME; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_0_tf() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE v TIME(3) "
            + "DEFAULT '10:11:12.123'::TIME(3); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_0_ntz() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE v "
            + "TIMESTAMP_NTZ DEFAULT '2020-01-15 10:11:12'::TIMESTAMP_NTZ; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_0_tz() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE v "
            + "TIMESTAMP_TZ DEFAULT '2020-01-15 10:11:12 +02:00'::TIMESTAMP_TZ; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_0_va() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('[1,2]'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_0_vb() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('true'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_0_vf() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('1.5'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_0_vi() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('12'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_0_vs() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('\"abc\"'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_0_vd() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('\"2020-01-15\"'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_0_vh() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('\"414243\"'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_0_vo() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('{\"a\":1}'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_0_ar() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE v ARRAY "
            + "DEFAULT ARRAY_CONSTRUCT(1,2); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_0_ob() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE v OBJECT "
            + "DEFAULT OBJECT_CONSTRUCT('a',1); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_0_bi() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE v BINARY "
            + "DEFAULT TO_BINARY('414243'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_0_n2() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE v "
            + "NUMBER(10,2) DEFAULT 1.50; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_0_fl() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE v FLOAT "
            + "DEFAULT 1.5; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_0_bo() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE v BOOLEAN "
            + "DEFAULT TRUE; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_0_s3() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE v VARCHAR(3)"
            + " DEFAULT 'abc'; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_0_sn() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE v VARCHAR "
            + "DEFAULT '12'; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_1_dt() RETURNS VARCHAR(5) LANGUAGE SQL AS $$ DECLARE v DATE "
            + "DEFAULT '2020-01-15'::DATE; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_1_tm() RETURNS VARCHAR(5) LANGUAGE SQL AS $$ DECLARE v TIME "
            + "DEFAULT '10:00:00'::TIME; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_1_tf() RETURNS VARCHAR(5) LANGUAGE SQL AS $$ DECLARE v TIME(3)"
            + " DEFAULT '10:11:12.123'::TIME(3); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_1_ntz() RETURNS VARCHAR(5) LANGUAGE SQL AS $$ DECLARE v "
            + "TIMESTAMP_NTZ DEFAULT '2020-01-15 10:11:12'::TIMESTAMP_NTZ; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_1_tz() RETURNS VARCHAR(5) LANGUAGE SQL AS $$ DECLARE v "
            + "TIMESTAMP_TZ DEFAULT '2020-01-15 10:11:12 +02:00'::TIMESTAMP_TZ; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_1_va() RETURNS VARCHAR(5) LANGUAGE SQL AS $$ DECLARE v VARIANT"
            + " DEFAULT PARSE_JSON('[1,2]'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_1_vb() RETURNS VARCHAR(5) LANGUAGE SQL AS $$ DECLARE v VARIANT"
            + " DEFAULT PARSE_JSON('true'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_1_vf() RETURNS VARCHAR(5) LANGUAGE SQL AS $$ DECLARE v VARIANT"
            + " DEFAULT PARSE_JSON('1.5'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_1_vi() RETURNS VARCHAR(5) LANGUAGE SQL AS $$ DECLARE v VARIANT"
            + " DEFAULT PARSE_JSON('12'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_1_vs() RETURNS VARCHAR(5) LANGUAGE SQL AS $$ DECLARE v VARIANT"
            + " DEFAULT PARSE_JSON('\"abc\"'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_1_vd() RETURNS VARCHAR(5) LANGUAGE SQL AS $$ DECLARE v VARIANT"
            + " DEFAULT PARSE_JSON('\"2020-01-15\"'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_1_vh() RETURNS VARCHAR(5) LANGUAGE SQL AS $$ DECLARE v VARIANT"
            + " DEFAULT PARSE_JSON('\"414243\"'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_1_vo() RETURNS VARCHAR(5) LANGUAGE SQL AS $$ DECLARE v VARIANT"
            + " DEFAULT PARSE_JSON('{\"a\":1}'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_1_ar() RETURNS VARCHAR(5) LANGUAGE SQL AS $$ DECLARE v ARRAY "
            + "DEFAULT ARRAY_CONSTRUCT(1,2); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_1_ob() RETURNS VARCHAR(5) LANGUAGE SQL AS $$ DECLARE v OBJECT "
            + "DEFAULT OBJECT_CONSTRUCT('a',1); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_1_bi() RETURNS VARCHAR(5) LANGUAGE SQL AS $$ DECLARE v BINARY "
            + "DEFAULT TO_BINARY('414243'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_1_n2() RETURNS VARCHAR(5) LANGUAGE SQL AS $$ DECLARE v "
            + "NUMBER(10,2) DEFAULT 1.50; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_1_fl() RETURNS VARCHAR(5) LANGUAGE SQL AS $$ DECLARE v FLOAT "
            + "DEFAULT 1.5; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_1_bo() RETURNS VARCHAR(5) LANGUAGE SQL AS $$ DECLARE v BOOLEAN"
            + " DEFAULT TRUE; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_1_s3() RETURNS VARCHAR(5) LANGUAGE SQL AS $$ DECLARE v "
            + "VARCHAR(3) DEFAULT 'abc'; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_1_sn() RETURNS VARCHAR(5) LANGUAGE SQL AS $$ DECLARE v VARCHAR"
            + " DEFAULT '12'; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_2_dt() RETURNS BINARY LANGUAGE SQL AS $$ DECLARE v DATE "
            + "DEFAULT '2020-01-15'::DATE; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_2_tm() RETURNS BINARY LANGUAGE SQL AS $$ DECLARE v TIME "
            + "DEFAULT '10:00:00'::TIME; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_2_tf() RETURNS BINARY LANGUAGE SQL AS $$ DECLARE v TIME(3) "
            + "DEFAULT '10:11:12.123'::TIME(3); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_2_ntz() RETURNS BINARY LANGUAGE SQL AS $$ DECLARE v "
            + "TIMESTAMP_NTZ DEFAULT '2020-01-15 10:11:12'::TIMESTAMP_NTZ; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_2_tz() RETURNS BINARY LANGUAGE SQL AS $$ DECLARE v "
            + "TIMESTAMP_TZ DEFAULT '2020-01-15 10:11:12 +02:00'::TIMESTAMP_TZ; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_2_va() RETURNS BINARY LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('[1,2]'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_2_vb() RETURNS BINARY LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('true'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_2_vf() RETURNS BINARY LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('1.5'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_2_vi() RETURNS BINARY LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('12'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_2_vs() RETURNS BINARY LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('\"abc\"'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_2_vd() RETURNS BINARY LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('\"2020-01-15\"'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_2_vh() RETURNS BINARY LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('\"414243\"'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_2_vo() RETURNS BINARY LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('{\"a\":1}'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_2_ar() RETURNS BINARY LANGUAGE SQL AS $$ DECLARE v ARRAY "
            + "DEFAULT ARRAY_CONSTRUCT(1,2); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_2_ob() RETURNS BINARY LANGUAGE SQL AS $$ DECLARE v OBJECT "
            + "DEFAULT OBJECT_CONSTRUCT('a',1); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_2_bi() RETURNS BINARY LANGUAGE SQL AS $$ DECLARE v BINARY "
            + "DEFAULT TO_BINARY('414243'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_2_n2() RETURNS BINARY LANGUAGE SQL AS $$ DECLARE v "
            + "NUMBER(10,2) DEFAULT 1.50; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_2_fl() RETURNS BINARY LANGUAGE SQL AS $$ DECLARE v FLOAT "
            + "DEFAULT 1.5; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_2_bo() RETURNS BINARY LANGUAGE SQL AS $$ DECLARE v BOOLEAN "
            + "DEFAULT TRUE; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_2_s3() RETURNS BINARY LANGUAGE SQL AS $$ DECLARE v VARCHAR(3) "
            + "DEFAULT 'abc'; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_2_sn() RETURNS BINARY LANGUAGE SQL AS $$ DECLARE v VARCHAR "
            + "DEFAULT '12'; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_3_dt() RETURNS BOOLEAN LANGUAGE SQL AS $$ DECLARE v DATE "
            + "DEFAULT '2020-01-15'::DATE; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_3_tm() RETURNS BOOLEAN LANGUAGE SQL AS $$ DECLARE v TIME "
            + "DEFAULT '10:00:00'::TIME; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_3_tf() RETURNS BOOLEAN LANGUAGE SQL AS $$ DECLARE v TIME(3) "
            + "DEFAULT '10:11:12.123'::TIME(3); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_3_ntz() RETURNS BOOLEAN LANGUAGE SQL AS $$ DECLARE v "
            + "TIMESTAMP_NTZ DEFAULT '2020-01-15 10:11:12'::TIMESTAMP_NTZ; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_3_tz() RETURNS BOOLEAN LANGUAGE SQL AS $$ DECLARE v "
            + "TIMESTAMP_TZ DEFAULT '2020-01-15 10:11:12 +02:00'::TIMESTAMP_TZ; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_3_va() RETURNS BOOLEAN LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('[1,2]'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_3_vb() RETURNS BOOLEAN LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('true'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_3_vf() RETURNS BOOLEAN LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('1.5'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_3_vi() RETURNS BOOLEAN LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('12'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_3_vs() RETURNS BOOLEAN LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('\"abc\"'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_3_vd() RETURNS BOOLEAN LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('\"2020-01-15\"'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_3_vh() RETURNS BOOLEAN LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('\"414243\"'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_3_vo() RETURNS BOOLEAN LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('{\"a\":1}'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_3_ar() RETURNS BOOLEAN LANGUAGE SQL AS $$ DECLARE v ARRAY "
            + "DEFAULT ARRAY_CONSTRUCT(1,2); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_3_ob() RETURNS BOOLEAN LANGUAGE SQL AS $$ DECLARE v OBJECT "
            + "DEFAULT OBJECT_CONSTRUCT('a',1); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_3_bi() RETURNS BOOLEAN LANGUAGE SQL AS $$ DECLARE v BINARY "
            + "DEFAULT TO_BINARY('414243'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_3_n2() RETURNS BOOLEAN LANGUAGE SQL AS $$ DECLARE v "
            + "NUMBER(10,2) DEFAULT 1.50; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_3_fl() RETURNS BOOLEAN LANGUAGE SQL AS $$ DECLARE v FLOAT "
            + "DEFAULT 1.5; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_3_bo() RETURNS BOOLEAN LANGUAGE SQL AS $$ DECLARE v BOOLEAN "
            + "DEFAULT TRUE; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_3_s3() RETURNS BOOLEAN LANGUAGE SQL AS $$ DECLARE v VARCHAR(3)"
            + " DEFAULT 'abc'; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_3_sn() RETURNS BOOLEAN LANGUAGE SQL AS $$ DECLARE v VARCHAR "
            + "DEFAULT '12'; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_5_dt() RETURNS NUMBER LANGUAGE SQL AS $$ DECLARE v DATE "
            + "DEFAULT '2020-01-15'::DATE; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_5_tm() RETURNS NUMBER LANGUAGE SQL AS $$ DECLARE v TIME "
            + "DEFAULT '10:00:00'::TIME; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_5_tf() RETURNS NUMBER LANGUAGE SQL AS $$ DECLARE v TIME(3) "
            + "DEFAULT '10:11:12.123'::TIME(3); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_5_ntz() RETURNS NUMBER LANGUAGE SQL AS $$ DECLARE v "
            + "TIMESTAMP_NTZ DEFAULT '2020-01-15 10:11:12'::TIMESTAMP_NTZ; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_5_tz() RETURNS NUMBER LANGUAGE SQL AS $$ DECLARE v "
            + "TIMESTAMP_TZ DEFAULT '2020-01-15 10:11:12 +02:00'::TIMESTAMP_TZ; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_5_va() RETURNS NUMBER LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('[1,2]'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_5_vb() RETURNS NUMBER LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('true'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_5_vf() RETURNS NUMBER LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('1.5'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_5_vi() RETURNS NUMBER LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('12'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_5_vs() RETURNS NUMBER LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('\"abc\"'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_5_vd() RETURNS NUMBER LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('\"2020-01-15\"'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_5_vh() RETURNS NUMBER LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('\"414243\"'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_5_vo() RETURNS NUMBER LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('{\"a\":1}'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_5_ar() RETURNS NUMBER LANGUAGE SQL AS $$ DECLARE v ARRAY "
            + "DEFAULT ARRAY_CONSTRUCT(1,2); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_5_ob() RETURNS NUMBER LANGUAGE SQL AS $$ DECLARE v OBJECT "
            + "DEFAULT OBJECT_CONSTRUCT('a',1); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_5_bi() RETURNS NUMBER LANGUAGE SQL AS $$ DECLARE v BINARY "
            + "DEFAULT TO_BINARY('414243'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_5_n2() RETURNS NUMBER LANGUAGE SQL AS $$ DECLARE v "
            + "NUMBER(10,2) DEFAULT 1.50; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_5_fl() RETURNS NUMBER LANGUAGE SQL AS $$ DECLARE v FLOAT "
            + "DEFAULT 1.5; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_5_bo() RETURNS NUMBER LANGUAGE SQL AS $$ DECLARE v BOOLEAN "
            + "DEFAULT TRUE; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_5_s3() RETURNS NUMBER LANGUAGE SQL AS $$ DECLARE v VARCHAR(3) "
            + "DEFAULT 'abc'; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_5_sn() RETURNS NUMBER LANGUAGE SQL AS $$ DECLARE v VARCHAR "
            + "DEFAULT '12'; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_6_dt() RETURNS NUMBER(10,2) LANGUAGE SQL AS $$ DECLARE v DATE "
            + "DEFAULT '2020-01-15'::DATE; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_6_tm() RETURNS NUMBER(10,2) LANGUAGE SQL AS $$ DECLARE v TIME "
            + "DEFAULT '10:00:00'::TIME; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_6_tf() RETURNS NUMBER(10,2) LANGUAGE SQL AS $$ DECLARE v "
            + "TIME(3) DEFAULT '10:11:12.123'::TIME(3); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_6_ntz() RETURNS NUMBER(10,2) LANGUAGE SQL AS $$ DECLARE v "
            + "TIMESTAMP_NTZ DEFAULT '2020-01-15 10:11:12'::TIMESTAMP_NTZ; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_6_tz() RETURNS NUMBER(10,2) LANGUAGE SQL AS $$ DECLARE v "
            + "TIMESTAMP_TZ DEFAULT '2020-01-15 10:11:12 +02:00'::TIMESTAMP_TZ; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_6_va() RETURNS NUMBER(10,2) LANGUAGE SQL AS $$ DECLARE v "
            + "VARIANT DEFAULT PARSE_JSON('[1,2]'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_6_vb() RETURNS NUMBER(10,2) LANGUAGE SQL AS $$ DECLARE v "
            + "VARIANT DEFAULT PARSE_JSON('true'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_6_vf() RETURNS NUMBER(10,2) LANGUAGE SQL AS $$ DECLARE v "
            + "VARIANT DEFAULT PARSE_JSON('1.5'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_6_vi() RETURNS NUMBER(10,2) LANGUAGE SQL AS $$ DECLARE v "
            + "VARIANT DEFAULT PARSE_JSON('12'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_6_vs() RETURNS NUMBER(10,2) LANGUAGE SQL AS $$ DECLARE v "
            + "VARIANT DEFAULT PARSE_JSON('\"abc\"'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_6_vd() RETURNS NUMBER(10,2) LANGUAGE SQL AS $$ DECLARE v "
            + "VARIANT DEFAULT PARSE_JSON('\"2020-01-15\"'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_6_vh() RETURNS NUMBER(10,2) LANGUAGE SQL AS $$ DECLARE v "
            + "VARIANT DEFAULT PARSE_JSON('\"414243\"'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_6_vo() RETURNS NUMBER(10,2) LANGUAGE SQL AS $$ DECLARE v "
            + "VARIANT DEFAULT PARSE_JSON('{\"a\":1}'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_6_ar() RETURNS NUMBER(10,2) LANGUAGE SQL AS $$ DECLARE v ARRAY"
            + " DEFAULT ARRAY_CONSTRUCT(1,2); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_6_ob() RETURNS NUMBER(10,2) LANGUAGE SQL AS $$ DECLARE v "
            + "OBJECT DEFAULT OBJECT_CONSTRUCT('a',1); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_6_bi() RETURNS NUMBER(10,2) LANGUAGE SQL AS $$ DECLARE v "
            + "BINARY DEFAULT TO_BINARY('414243'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_6_n2() RETURNS NUMBER(10,2) LANGUAGE SQL AS $$ DECLARE v "
            + "NUMBER(10,2) DEFAULT 1.50; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_6_fl() RETURNS NUMBER(10,2) LANGUAGE SQL AS $$ DECLARE v FLOAT"
            + " DEFAULT 1.5; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_6_bo() RETURNS NUMBER(10,2) LANGUAGE SQL AS $$ DECLARE v "
            + "BOOLEAN DEFAULT TRUE; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_6_s3() RETURNS NUMBER(10,2) LANGUAGE SQL AS $$ DECLARE v "
            + "VARCHAR(3) DEFAULT 'abc'; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_6_sn() RETURNS NUMBER(10,2) LANGUAGE SQL AS $$ DECLARE v "
            + "VARCHAR DEFAULT '12'; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_7_dt() RETURNS DATE LANGUAGE SQL AS $$ DECLARE v DATE DEFAULT "
            + "'2020-01-15'::DATE; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_7_tm() RETURNS DATE LANGUAGE SQL AS $$ DECLARE v TIME DEFAULT "
            + "'10:00:00'::TIME; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_7_tf() RETURNS DATE LANGUAGE SQL AS $$ DECLARE v TIME(3) "
            + "DEFAULT '10:11:12.123'::TIME(3); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_7_ntz() RETURNS DATE LANGUAGE SQL AS $$ DECLARE v "
            + "TIMESTAMP_NTZ DEFAULT '2020-01-15 10:11:12'::TIMESTAMP_NTZ; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_7_tz() RETURNS DATE LANGUAGE SQL AS $$ DECLARE v TIMESTAMP_TZ "
            + "DEFAULT '2020-01-15 10:11:12 +02:00'::TIMESTAMP_TZ; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_7_va() RETURNS DATE LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('[1,2]'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_7_vb() RETURNS DATE LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('true'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_7_vf() RETURNS DATE LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('1.5'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_7_vi() RETURNS DATE LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('12'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_7_vs() RETURNS DATE LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('\"abc\"'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_7_vd() RETURNS DATE LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('\"2020-01-15\"'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_7_vh() RETURNS DATE LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('\"414243\"'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_7_vo() RETURNS DATE LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('{\"a\":1}'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_7_ar() RETURNS DATE LANGUAGE SQL AS $$ DECLARE v ARRAY DEFAULT"
            + " ARRAY_CONSTRUCT(1,2); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_7_ob() RETURNS DATE LANGUAGE SQL AS $$ DECLARE v OBJECT "
            + "DEFAULT OBJECT_CONSTRUCT('a',1); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_7_bi() RETURNS DATE LANGUAGE SQL AS $$ DECLARE v BINARY "
            + "DEFAULT TO_BINARY('414243'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_7_n2() RETURNS DATE LANGUAGE SQL AS $$ DECLARE v NUMBER(10,2) "
            + "DEFAULT 1.50; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_7_fl() RETURNS DATE LANGUAGE SQL AS $$ DECLARE v FLOAT DEFAULT"
            + " 1.5; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_7_bo() RETURNS DATE LANGUAGE SQL AS $$ DECLARE v BOOLEAN "
            + "DEFAULT TRUE; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_7_s3() RETURNS DATE LANGUAGE SQL AS $$ DECLARE v VARCHAR(3) "
            + "DEFAULT 'abc'; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_7_sn() RETURNS DATE LANGUAGE SQL AS $$ DECLARE v VARCHAR "
            + "DEFAULT '12'; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_8_dt() RETURNS TIME LANGUAGE SQL AS $$ DECLARE v DATE DEFAULT "
            + "'2020-01-15'::DATE; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_8_tm() RETURNS TIME LANGUAGE SQL AS $$ DECLARE v TIME DEFAULT "
            + "'10:00:00'::TIME; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_8_tf() RETURNS TIME LANGUAGE SQL AS $$ DECLARE v TIME(3) "
            + "DEFAULT '10:11:12.123'::TIME(3); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_8_ntz() RETURNS TIME LANGUAGE SQL AS $$ DECLARE v "
            + "TIMESTAMP_NTZ DEFAULT '2020-01-15 10:11:12'::TIMESTAMP_NTZ; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_8_tz() RETURNS TIME LANGUAGE SQL AS $$ DECLARE v TIMESTAMP_TZ "
            + "DEFAULT '2020-01-15 10:11:12 +02:00'::TIMESTAMP_TZ; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_8_va() RETURNS TIME LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('[1,2]'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_8_vb() RETURNS TIME LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('true'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_8_vf() RETURNS TIME LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('1.5'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_8_vi() RETURNS TIME LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('12'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_8_vs() RETURNS TIME LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('\"abc\"'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_8_vd() RETURNS TIME LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('\"2020-01-15\"'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_8_vh() RETURNS TIME LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('\"414243\"'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_8_vo() RETURNS TIME LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('{\"a\":1}'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_8_ar() RETURNS TIME LANGUAGE SQL AS $$ DECLARE v ARRAY DEFAULT"
            + " ARRAY_CONSTRUCT(1,2); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_8_ob() RETURNS TIME LANGUAGE SQL AS $$ DECLARE v OBJECT "
            + "DEFAULT OBJECT_CONSTRUCT('a',1); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_8_bi() RETURNS TIME LANGUAGE SQL AS $$ DECLARE v BINARY "
            + "DEFAULT TO_BINARY('414243'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_8_n2() RETURNS TIME LANGUAGE SQL AS $$ DECLARE v NUMBER(10,2) "
            + "DEFAULT 1.50; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_8_fl() RETURNS TIME LANGUAGE SQL AS $$ DECLARE v FLOAT DEFAULT"
            + " 1.5; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_8_bo() RETURNS TIME LANGUAGE SQL AS $$ DECLARE v BOOLEAN "
            + "DEFAULT TRUE; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_8_s3() RETURNS TIME LANGUAGE SQL AS $$ DECLARE v VARCHAR(3) "
            + "DEFAULT 'abc'; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_8_sn() RETURNS TIME LANGUAGE SQL AS $$ DECLARE v VARCHAR "
            + "DEFAULT '12'; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_9_dt() RETURNS TIMESTAMP_NTZ LANGUAGE SQL AS $$ DECLARE v DATE"
            + " DEFAULT '2020-01-15'::DATE; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_9_tm() RETURNS TIMESTAMP_NTZ LANGUAGE SQL AS $$ DECLARE v TIME"
            + " DEFAULT '10:00:00'::TIME; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_9_tf() RETURNS TIMESTAMP_NTZ LANGUAGE SQL AS $$ DECLARE v "
            + "TIME(3) DEFAULT '10:11:12.123'::TIME(3); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_9_ntz() RETURNS TIMESTAMP_NTZ LANGUAGE SQL AS $$ DECLARE v "
            + "TIMESTAMP_NTZ DEFAULT '2020-01-15 10:11:12'::TIMESTAMP_NTZ; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_9_tz() RETURNS TIMESTAMP_NTZ LANGUAGE SQL AS $$ DECLARE v "
            + "TIMESTAMP_TZ DEFAULT '2020-01-15 10:11:12 +02:00'::TIMESTAMP_TZ; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_9_va() RETURNS TIMESTAMP_NTZ LANGUAGE SQL AS $$ DECLARE v "
            + "VARIANT DEFAULT PARSE_JSON('[1,2]'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_9_vb() RETURNS TIMESTAMP_NTZ LANGUAGE SQL AS $$ DECLARE v "
            + "VARIANT DEFAULT PARSE_JSON('true'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_9_vf() RETURNS TIMESTAMP_NTZ LANGUAGE SQL AS $$ DECLARE v "
            + "VARIANT DEFAULT PARSE_JSON('1.5'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_9_vi() RETURNS TIMESTAMP_NTZ LANGUAGE SQL AS $$ DECLARE v "
            + "VARIANT DEFAULT PARSE_JSON('12'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_9_vs() RETURNS TIMESTAMP_NTZ LANGUAGE SQL AS $$ DECLARE v "
            + "VARIANT DEFAULT PARSE_JSON('\"abc\"'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_9_vd() RETURNS TIMESTAMP_NTZ LANGUAGE SQL AS $$ DECLARE v "
            + "VARIANT DEFAULT PARSE_JSON('\"2020-01-15\"'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_9_vh() RETURNS TIMESTAMP_NTZ LANGUAGE SQL AS $$ DECLARE v "
            + "VARIANT DEFAULT PARSE_JSON('\"414243\"'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_9_vo() RETURNS TIMESTAMP_NTZ LANGUAGE SQL AS $$ DECLARE v "
            + "VARIANT DEFAULT PARSE_JSON('{\"a\":1}'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_9_ar() RETURNS TIMESTAMP_NTZ LANGUAGE SQL AS $$ DECLARE v "
            + "ARRAY DEFAULT ARRAY_CONSTRUCT(1,2); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_9_ob() RETURNS TIMESTAMP_NTZ LANGUAGE SQL AS $$ DECLARE v "
            + "OBJECT DEFAULT OBJECT_CONSTRUCT('a',1); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_9_bi() RETURNS TIMESTAMP_NTZ LANGUAGE SQL AS $$ DECLARE v "
            + "BINARY DEFAULT TO_BINARY('414243'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_9_n2() RETURNS TIMESTAMP_NTZ LANGUAGE SQL AS $$ DECLARE v "
            + "NUMBER(10,2) DEFAULT 1.50; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_9_fl() RETURNS TIMESTAMP_NTZ LANGUAGE SQL AS $$ DECLARE v "
            + "FLOAT DEFAULT 1.5; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_9_bo() RETURNS TIMESTAMP_NTZ LANGUAGE SQL AS $$ DECLARE v "
            + "BOOLEAN DEFAULT TRUE; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_9_s3() RETURNS TIMESTAMP_NTZ LANGUAGE SQL AS $$ DECLARE v "
            + "VARCHAR(3) DEFAULT 'abc'; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fm_9_sn() RETURNS TIMESTAMP_NTZ LANGUAGE SQL AS $$ DECLARE v "
            + "VARCHAR DEFAULT '12'; BEGIN RETURN v; END $$");
        assertCells(new String[][] {
            {"SELECT fm_0_dt()",
                "2020-01-15"},
            {"SELECT fm_0_tm()",
                "10:00:00"},
            {"SELECT fm_0_tf()",
                "10:11:12"},
            {"SELECT fm_0_ntz()",
                "2020-01-15 10:11:12.000"},
            {"SELECT fm_0_tz()",
                "2020-01-15 10:11:12.000 +0200"},
            {"SELECT fm_0_va()",
                "[1,2]"},
            {"SELECT fm_0_vb()",
                "true"},
            {"SELECT fm_0_vf()",
                "1.5"},
            {"SELECT fm_0_vi()",
                "12"},
            {"SELECT fm_0_vs()",
                "abc"},
            {"SELECT fm_0_vd()",
                "2020-01-15"},
            {"SELECT fm_0_vh()",
                "414243"},
            {"SELECT fm_0_vo()",
                "{\"a\":1}"},
            {"SELECT fm_0_ar()",
                "[1,2]"},
            {"SELECT fm_0_ob()",
                "{\"a\":1}"},
            {"SELECT fm_0_bi()",
                "414243"},
            {"SELECT fm_0_n2()",
                "1.50"},
            {"SELECT fm_0_fl()",
                "1.5"},
            {"SELECT fm_0_bo()",
                "true"},
            {"SELECT fm_0_s3()",
                "abc"},
            {"SELECT fm_0_sn()",
                "12"},
            {"SELECT fm_1_dt()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 57 : String "
                + "'2020-01-15' is too long and would be truncated"},
            {"SELECT fm_1_tm()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 55 : String "
                + "'10:00:00' is too long and would be truncated"},
            {"SELECT fm_1_tf()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 65 : String "
                + "'10:11:12' is too long and would be truncated"},
            {"SELECT fm_1_ntz()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 84 : String "
                + "'2020-01-15 10:11:12.000' is too long and would be truncated"},
            {"SELECT fm_1_tz()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 89 : String "
                + "'2020-01-15 10:11:12.000 +0200' is too long and would be truncated"},
            {"SELECT fm_1_va()",
                "[1,2]"},
            {"SELECT fm_1_vb()",
                "true"},
            {"SELECT fm_1_vf()",
                "1.5"},
            {"SELECT fm_1_vi()",
                "12"},
            {"SELECT fm_1_vs()",
                "abc"},
            {"SELECT fm_1_vd()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 68 : String "
                + "'2020-01-15' is too long and would be truncated"},
            {"SELECT fm_1_vh()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 64 : String "
                + "'414243' is too long and would be truncated"},
            {"SELECT fm_1_vo()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : String "
                + "'{\"a\":1}' is too long and would be truncated"},
            {"SELECT fm_1_ar()",
                "[1,2]"},
            {"SELECT fm_1_ob()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 64 : String "
                + "'{\"a\":1}' is too long and would be truncated"},
            {"SELECT fm_1_bi()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 60 : String "
                + "'414243' is too long and would be truncated"},
            {"SELECT fm_1_n2()",
                "1.50"},
            {"SELECT fm_1_fl()",
                "1.5"},
            {"SELECT fm_1_bo()",
                "true"},
            {"SELECT fm_1_s3()",
                "abc"},
            {"SELECT fm_1_sn()",
                "12"},
            {"SELECT fm_2_dt()",
                "REFUSED SQL compilation error: error line 1 at position 50 |  Declared return type "
                + "'BINARY(67108864)' is incompatible with actual return type 'DATE'"},
            {"SELECT fm_2_tm()",
                "REFUSED SQL compilation error: error line 1 at position 48 |  Declared return type "
                + "'BINARY(67108864)' is incompatible with actual return type 'TIME(9)'"},
            {"SELECT fm_2_tf()",
                "REFUSED SQL compilation error: error line 1 at position 58 |  Declared return type "
                + "'BINARY(67108864)' is incompatible with actual return type 'TIME(3)'"},
            {"SELECT fm_2_ntz()",
                "REFUSED SQL compilation error: error line 1 at position 77 |  Declared return type "
                + "'BINARY(67108864)' is incompatible with actual return type 'TIMESTAMP_NTZ(9)'"},
            {"SELECT fm_2_tz()",
                "REFUSED SQL compilation error: error line 1 at position 82 |  Declared return type "
                + "'BINARY(67108864)' is incompatible with actual return type 'TIMESTAMP_TZ(9)'"},
            {"SELECT fm_2_va()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : Failed to "
                + "cast variant value [1,2] to BINARY"},
            {"SELECT fm_2_vb()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 60 : Failed to "
                + "cast variant value true to BINARY"},
            {"SELECT fm_2_vf()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 59 : Failed to "
                + "cast variant value 1.5 to BINARY"},
            {"SELECT fm_2_vi()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 58 : Failed to "
                + "cast variant value 12 to BINARY"},
            {"SELECT fm_2_vs()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : Failed to "
                + "cast variant value \"abc\" to BINARY"},
            {"SELECT fm_2_vd()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 68 : Failed to "
                + "cast variant value \"2020-01-15\" to BINARY"},
            {"SELECT fm_2_vh()",
                "414243"},
            {"SELECT fm_2_vo()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : Failed to "
                + "cast variant value {\"a\":1} to BINARY"},
            {"SELECT fm_2_ar()",
                "REFUSED SQL compilation error: error line 1 at position 53 |  Declared return type "
                + "'BINARY(67108864)' is incompatible with actual return type 'ARRAY'"},
            {"SELECT fm_2_ob()",
                "REFUSED SQL compilation error: error line 1 at position 57 |  Declared return type "
                + "'BINARY(67108864)' is incompatible with actual return type 'OBJECT'"},
            {"SELECT fm_2_bi()",
                "414243"},
            {"SELECT fm_2_n2()",
                "REFUSED SQL compilation error: error line 1 at position 44 |  Declared return type "
                + "'BINARY(67108864)' is incompatible with actual return type 'NUMBER(10,2)'"},
            {"SELECT fm_2_fl()",
                "REFUSED SQL compilation error: error line 1 at position 36 |  Declared return type "
                + "'BINARY(67108864)' is incompatible with actual return type 'FLOAT'"},
            {"SELECT fm_2_bo()",
                "REFUSED SQL compilation error: error line 1 at position 39 |  Declared return type "
                + "'BINARY(67108864)' is incompatible with actual return type 'BOOLEAN'"},
            {"SELECT fm_2_s3()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 50 : The "
                + "following string is not a legal hex-encoded value: 'abc'"},
            {"SELECT fm_2_sn()",
                "12"},
            {"SELECT fm_3_dt()::VARCHAR",
                "REFUSED SQL compilation error: error line 1 at position 50 |  Declared return type 'BOOLEAN'"
                + " is incompatible with actual return type 'DATE'"},
            {"SELECT fm_3_tm()::VARCHAR",
                "REFUSED SQL compilation error: error line 1 at position 48 |  Declared return type 'BOOLEAN'"
                + " is incompatible with actual return type 'TIME(9)'"},
            {"SELECT fm_3_tf()::VARCHAR",
                "REFUSED SQL compilation error: error line 1 at position 58 |  Declared return type 'BOOLEAN'"
                + " is incompatible with actual return type 'TIME(3)'"},
            {"SELECT fm_3_ntz()::VARCHAR",
                "REFUSED SQL compilation error: error line 1 at position 77 |  Declared return type 'BOOLEAN'"
                + " is incompatible with actual return type 'TIMESTAMP_NTZ(9)'"},
            {"SELECT fm_3_tz()::VARCHAR",
                "REFUSED SQL compilation error: error line 1 at position 82 |  Declared return type 'BOOLEAN'"
                + " is incompatible with actual return type 'TIMESTAMP_TZ(9)'"},
            {"SELECT fm_3_va()::VARCHAR",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : Failed to "
                + "cast variant value [1,2] to BOOLEAN"},
            {"SELECT fm_3_vb()::VARCHAR",
                "true"},
            {"SELECT fm_3_vf()::VARCHAR",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 59 : Failed to "
                + "cast variant value 1.5 to BOOLEAN"},
            {"SELECT fm_3_vi()::VARCHAR",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 58 : Failed to "
                + "cast variant value 12 to BOOLEAN"},
            {"SELECT fm_3_vs()::VARCHAR",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : Failed to "
                + "cast variant value \"abc\" to BOOLEAN"},
            {"SELECT fm_3_vd()::VARCHAR",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 68 : Failed to "
                + "cast variant value \"2020-01-15\" to BOOLEAN"},
            {"SELECT fm_3_vh()::VARCHAR",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 64 : Failed to "
                + "cast variant value \"414243\" to BOOLEAN"},
            {"SELECT fm_3_vo()::VARCHAR",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : Failed to "
                + "cast variant value {\"a\":1} to BOOLEAN"},
            {"SELECT fm_3_ar()::VARCHAR",
                "REFUSED SQL compilation error: error line 1 at position 53 |  Declared return type 'BOOLEAN'"
                + " is incompatible with actual return type 'ARRAY'"},
            {"SELECT fm_3_ob()::VARCHAR",
                "REFUSED SQL compilation error: error line 1 at position 57 |  Declared return type 'BOOLEAN'"
                + " is incompatible with actual return type 'OBJECT'"},
            {"SELECT fm_3_bi()::VARCHAR",
                "REFUSED SQL compilation error: error line 1 at position 53 |  Declared return type 'BOOLEAN'"
                + " is incompatible with actual return type 'BINARY(67108864)'"},
            {"SELECT fm_3_n2()::VARCHAR",
                "true"},
            {"SELECT fm_3_fl()::VARCHAR",
                "REFUSED SQL compilation error: error line 1 at position 36 |  Declared return type 'BOOLEAN'"
                + " is incompatible with actual return type 'FLOAT'"},
            {"SELECT fm_3_bo()::VARCHAR",
                "true"},
            {"SELECT fm_3_s3()::VARCHAR",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 50 : Boolean "
                + "value 'abc' is not recognized"},
            {"SELECT fm_3_sn()::VARCHAR",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 46 : Boolean "
                + "value '12' is not recognized"},
            {"SELECT fm_5_dt()",
                "REFUSED SQL compilation error: error line 1 at position 50 |  Declared return type "
                + "'NUMBER(38,0)' is incompatible with actual return type 'DATE'"},
            {"SELECT fm_5_tm()",
                "REFUSED SQL compilation error: error line 1 at position 48 |  Declared return type "
                + "'NUMBER(38,0)' is incompatible with actual return type 'TIME(9)'"},
            {"SELECT fm_5_tf()",
                "REFUSED SQL compilation error: error line 1 at position 58 |  Declared return type "
                + "'NUMBER(38,0)' is incompatible with actual return type 'TIME(3)'"},
            {"SELECT fm_5_ntz()",
                "REFUSED SQL compilation error: error line 1 at position 77 |  Declared return type "
                + "'NUMBER(38,0)' is incompatible with actual return type 'TIMESTAMP_NTZ(9)'"},
            {"SELECT fm_5_tz()",
                "REFUSED SQL compilation error: error line 1 at position 82 |  Declared return type "
                + "'NUMBER(38,0)' is incompatible with actual return type 'TIMESTAMP_TZ(9)'"},
            {"SELECT fm_5_va()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : Failed to "
                + "cast variant value [1,2] to FIXED"},
            {"SELECT fm_5_vb()",
                "1"},
            {"SELECT fm_5_vf()",
                "2"},
            {"SELECT fm_5_vi()",
                "12"},
            {"SELECT fm_5_vs()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : Failed to "
                + "cast variant value \"abc\" to FIXED"},
            {"SELECT fm_5_vd()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 68 : Failed to "
                + "cast variant value \"2020-01-15\" to FIXED"},
            {"SELECT fm_5_vh()",
                "414243"},
            {"SELECT fm_5_vo()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : Failed to "
                + "cast variant value {\"a\":1} to FIXED"},
            {"SELECT fm_5_ar()",
                "REFUSED SQL compilation error: error line 1 at position 53 |  Declared return type "
                + "'NUMBER(38,0)' is incompatible with actual return type 'ARRAY'"},
            {"SELECT fm_5_ob()",
                "REFUSED SQL compilation error: error line 1 at position 57 |  Declared return type "
                + "'NUMBER(38,0)' is incompatible with actual return type 'OBJECT'"},
            {"SELECT fm_5_bi()",
                "REFUSED SQL compilation error: error line 1 at position 53 |  Declared return type "
                + "'NUMBER(38,0)' is incompatible with actual return type 'BINARY(67108864)'"},
            {"SELECT fm_5_n2()",
                "2"},
            {"SELECT fm_5_fl()",
                "2"},
            {"SELECT fm_5_bo()",
                "1"},
            {"SELECT fm_5_s3()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 50 : Numeric "
                + "value 'abc' is not recognized"},
            {"SELECT fm_5_sn()",
                "12"},
            {"SELECT fm_6_dt()",
                "REFUSED SQL compilation error: error line 1 at position 50 |  Declared return type "
                + "'NUMBER(10,2)' is incompatible with actual return type 'DATE'"},
            {"SELECT fm_6_tm()",
                "REFUSED SQL compilation error: error line 1 at position 48 |  Declared return type "
                + "'NUMBER(10,2)' is incompatible with actual return type 'TIME(9)'"},
            {"SELECT fm_6_tf()",
                "REFUSED SQL compilation error: error line 1 at position 58 |  Declared return type "
                + "'NUMBER(10,2)' is incompatible with actual return type 'TIME(3)'"},
            {"SELECT fm_6_ntz()",
                "REFUSED SQL compilation error: error line 1 at position 77 |  Declared return type "
                + "'NUMBER(10,2)' is incompatible with actual return type 'TIMESTAMP_NTZ(9)'"},
            {"SELECT fm_6_tz()",
                "REFUSED SQL compilation error: error line 1 at position 82 |  Declared return type "
                + "'NUMBER(10,2)' is incompatible with actual return type 'TIMESTAMP_TZ(9)'"},
            {"SELECT fm_6_va()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : Failed to "
                + "cast variant value [1,2] to FIXED"},
            {"SELECT fm_6_vb()",
                "1.00"},
            {"SELECT fm_6_vf()",
                "1.50"},
            {"SELECT fm_6_vi()",
                "12.00"},
            {"SELECT fm_6_vs()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : Failed to "
                + "cast variant value \"abc\" to FIXED"},
            {"SELECT fm_6_vd()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 68 : Failed to "
                + "cast variant value \"2020-01-15\" to FIXED"},
            {"SELECT fm_6_vh()",
                "414243.00"},
            {"SELECT fm_6_vo()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : Failed to "
                + "cast variant value {\"a\":1} to FIXED"},
            {"SELECT fm_6_ar()",
                "REFUSED SQL compilation error: error line 1 at position 53 |  Declared return type "
                + "'NUMBER(10,2)' is incompatible with actual return type 'ARRAY'"},
            {"SELECT fm_6_ob()",
                "REFUSED SQL compilation error: error line 1 at position 57 |  Declared return type "
                + "'NUMBER(10,2)' is incompatible with actual return type 'OBJECT'"},
            {"SELECT fm_6_bi()",
                "REFUSED SQL compilation error: error line 1 at position 53 |  Declared return type "
                + "'NUMBER(10,2)' is incompatible with actual return type 'BINARY(67108864)'"},
            {"SELECT fm_6_n2()",
                "1.50"},
            {"SELECT fm_6_fl()",
                "1.50"},
            {"SELECT fm_6_bo()",
                "1.00"},
            {"SELECT fm_6_s3()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 50 : Numeric "
                + "value 'abc' is not recognized"},
            {"SELECT fm_6_sn()",
                "12.00"},
            {"SELECT fm_7_dt()::VARCHAR",
                "2020-01-15"},
            {"SELECT fm_7_tm()::VARCHAR",
                "REFUSED SQL compilation error: error line 1 at position 48 |  Declared return type 'DATE' is"
                + " incompatible with actual return type 'TIME(9)'"},
            {"SELECT fm_7_tf()::VARCHAR",
                "REFUSED SQL compilation error: error line 1 at position 58 |  Declared return type 'DATE' is"
                + " incompatible with actual return type 'TIME(3)'"},
            {"SELECT fm_7_ntz()::VARCHAR",
                "2020-01-15"},
            {"SELECT fm_7_tz()::VARCHAR",
                "2020-01-15"},
            {"SELECT fm_7_va()::VARCHAR",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : Failed to "
                + "cast variant value [1,2] to DATE"},
            {"SELECT fm_7_vb()::VARCHAR",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 60 : Failed to "
                + "cast variant value true to DATE"},
            {"SELECT fm_7_vf()::VARCHAR",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 59 : Failed to "
                + "cast variant value 1.5 to DATE"},
            {"SELECT fm_7_vi()::VARCHAR",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 58 : Failed to "
                + "cast variant value 12 to DATE"},
            {"SELECT fm_7_vs()::VARCHAR",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : Failed to "
                + "cast variant value \"abc\" to DATE"},
            {"SELECT fm_7_vd()::VARCHAR",
                "2020-01-15"},
            {"SELECT fm_7_vh()::VARCHAR",
                "1970-01-05"},
            {"SELECT fm_7_vo()::VARCHAR",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : Failed to "
                + "cast variant value {\"a\":1} to DATE"},
            {"SELECT fm_7_ar()::VARCHAR",
                "REFUSED SQL compilation error: error line 1 at position 53 |  Declared return type 'DATE' is"
                + " incompatible with actual return type 'ARRAY'"},
            {"SELECT fm_7_ob()::VARCHAR",
                "REFUSED SQL compilation error: error line 1 at position 57 |  Declared return type 'DATE' is"
                + " incompatible with actual return type 'OBJECT'"},
            {"SELECT fm_7_bi()::VARCHAR",
                "REFUSED SQL compilation error: error line 1 at position 53 |  Declared return type 'DATE' is"
                + " incompatible with actual return type 'BINARY(67108864)'"},
            {"SELECT fm_7_n2()::VARCHAR",
                "REFUSED SQL compilation error: error line 1 at position 44 |  Declared return type 'DATE' is"
                + " incompatible with actual return type 'NUMBER(10,2)'"},
            {"SELECT fm_7_fl()::VARCHAR",
                "REFUSED SQL compilation error: error line 1 at position 36 |  Declared return type 'DATE' is"
                + " incompatible with actual return type 'FLOAT'"},
            {"SELECT fm_7_bo()::VARCHAR",
                "REFUSED SQL compilation error: error line 1 at position 39 |  Declared return type 'DATE' is"
                + " incompatible with actual return type 'BOOLEAN'"},
            {"SELECT fm_7_s3()::VARCHAR",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 50 : Date 'abc' "
                + "is not recognized"},
            {"SELECT fm_7_sn()::VARCHAR",
                "1970-01-01"},
            {"SELECT fm_8_dt()::VARCHAR",
                "REFUSED SQL compilation error: error line 1 at position 50 |  Declared return type 'TIME(9)'"
                + " is incompatible with actual return type 'DATE'"},
            {"SELECT fm_8_tm()::VARCHAR",
                "10:00:00"},
            {"SELECT fm_8_tf()::VARCHAR",
                "10:11:12"},
            {"SELECT fm_8_ntz()::VARCHAR",
                "10:11:12"},
            {"SELECT fm_8_tz()::VARCHAR",
                "10:11:12"},
            {"SELECT fm_8_va()::VARCHAR",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : Failed to "
                + "cast variant value [1,2] to TIME"},
            {"SELECT fm_8_vb()::VARCHAR",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 60 : Failed to "
                + "cast variant value true to TIME"},
            {"SELECT fm_8_vf()::VARCHAR",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 59 : Failed to "
                + "cast variant value 1.5 to TIME"},
            {"SELECT fm_8_vi()::VARCHAR",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 58 : Failed to "
                + "cast variant value 12 to TIME"},
            {"SELECT fm_8_vs()::VARCHAR",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : Failed to "
                + "cast variant value \"abc\" to TIME"},
            {"SELECT fm_8_vd()::VARCHAR",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 68 : Failed to "
                + "cast variant value \"2020-01-15\" to TIME"},
            {"SELECT fm_8_vh()::VARCHAR",
                "19:04:03"},
            {"SELECT fm_8_vo()::VARCHAR",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : Failed to "
                + "cast variant value {\"a\":1} to TIME"},
            {"SELECT fm_8_ar()::VARCHAR",
                "REFUSED SQL compilation error: error line 1 at position 53 |  Declared return type 'TIME(9)'"
                + " is incompatible with actual return type 'ARRAY'"},
            {"SELECT fm_8_ob()::VARCHAR",
                "REFUSED SQL compilation error: error line 1 at position 57 |  Declared return type 'TIME(9)'"
                + " is incompatible with actual return type 'OBJECT'"},
            {"SELECT fm_8_bi()::VARCHAR",
                "REFUSED SQL compilation error: error line 1 at position 53 |  Declared return type 'TIME(9)'"
                + " is incompatible with actual return type 'BINARY(67108864)'"},
            {"SELECT fm_8_n2()::VARCHAR",
                "REFUSED SQL compilation error: error line 1 at position 44 |  Declared return type 'TIME(9)'"
                + " is incompatible with actual return type 'NUMBER(10,2)'"},
            {"SELECT fm_8_fl()::VARCHAR",
                "REFUSED SQL compilation error: error line 1 at position 36 |  Declared return type 'TIME(9)'"
                + " is incompatible with actual return type 'FLOAT'"},
            {"SELECT fm_8_bo()::VARCHAR",
                "REFUSED SQL compilation error: error line 1 at position 39 |  Declared return type 'TIME(9)'"
                + " is incompatible with actual return type 'BOOLEAN'"},
            {"SELECT fm_8_s3()::VARCHAR",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 50 : Time 'abc' "
                + "is not recognized"},
            {"SELECT fm_8_sn()::VARCHAR",
                "00:00:12"},
            {"SELECT fm_9_dt()::VARCHAR",
                "2020-01-15 00:00:00.000"},
            {"SELECT fm_9_tm()::VARCHAR",
                "REFUSED SQL compilation error: error line 1 at position 48 |  Declared return type "
                + "'TIMESTAMP_NTZ(9)' is incompatible with actual return type 'TIME(9)'"},
            {"SELECT fm_9_tf()::VARCHAR",
                "REFUSED SQL compilation error: error line 1 at position 58 |  Declared return type "
                + "'TIMESTAMP_NTZ(9)' is incompatible with actual return type 'TIME(3)'"},
            {"SELECT fm_9_ntz()::VARCHAR",
                "2020-01-15 10:11:12.000"},
            {"SELECT fm_9_tz()::VARCHAR",
                "2020-01-15 10:11:12.000"},
            {"SELECT fm_9_va()::VARCHAR",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : Failed to "
                + "cast variant value [1,2] to TIMESTAMP_NTZ"},
            {"SELECT fm_9_vb()::VARCHAR",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 60 : Failed to "
                + "cast variant value true to TIMESTAMP_NTZ"},
            {"SELECT fm_9_vf()::VARCHAR",
                "1970-01-01 00:00:01.500"},
            {"SELECT fm_9_vi()::VARCHAR",
                "1970-01-01 00:00:12.000"},
            {"SELECT fm_9_vs()::VARCHAR",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : Failed to "
                + "cast variant value \"abc\" to TIMESTAMP_NTZ"},
            {"SELECT fm_9_vd()::VARCHAR",
                "2020-01-15 00:00:00.000"},
            {"SELECT fm_9_vh()::VARCHAR",
                "1970-01-05 19:04:03.000"},
            {"SELECT fm_9_vo()::VARCHAR",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : Failed to "
                + "cast variant value {\"a\":1} to TIMESTAMP_NTZ"},
            {"SELECT fm_9_ar()::VARCHAR",
                "REFUSED SQL compilation error: error line 1 at position 53 |  Declared return type "
                + "'TIMESTAMP_NTZ(9)' is incompatible with actual return type 'ARRAY'"},
            {"SELECT fm_9_ob()::VARCHAR",
                "REFUSED SQL compilation error: error line 1 at position 57 |  Declared return type "
                + "'TIMESTAMP_NTZ(9)' is incompatible with actual return type 'OBJECT'"},
            {"SELECT fm_9_bi()::VARCHAR",
                "REFUSED SQL compilation error: error line 1 at position 53 |  Declared return type "
                + "'TIMESTAMP_NTZ(9)' is incompatible with actual return type 'BINARY(67108864)'"},
            {"SELECT fm_9_n2()::VARCHAR",
                "1970-01-01 00:00:01.500"},
            {"SELECT fm_9_fl()::VARCHAR",
                "REFUSED SQL compilation error: error line 1 at position 36 |  Declared return type "
                + "'TIMESTAMP_NTZ(9)' is incompatible with actual return type 'FLOAT'"},
            {"SELECT fm_9_bo()::VARCHAR",
                "REFUSED SQL compilation error: error line 1 at position 39 |  Declared return type "
                + "'TIMESTAMP_NTZ(9)' is incompatible with actual return type 'BOOLEAN'"},
            {"SELECT fm_9_s3()::VARCHAR",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 50 : Timestamp "
                + "'abc' is not recognized"},
            {"SELECT fm_9_sn()::VARCHAR",
                "1970-01-01 00:00:12.000"},
        });
    }

    @Test
    public void procedureReturn() {
        engine.execute("ALTER SESSION SET TIMEZONE='UTC'");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_0_tm() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE v TIME "
            + "DEFAULT '10:00:00'::TIME; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_0_ntz() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE v "
            + "TIMESTAMP_NTZ DEFAULT '2020-01-15 10:11:12'::TIMESTAMP_NTZ; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_0_tz() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE v "
            + "TIMESTAMP_TZ DEFAULT '2020-01-15 10:11:12 +02:00'::TIMESTAMP_TZ; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_0_va() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('[1,2]'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_0_vb() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('true'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_0_vs() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('\"abc\"'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_0_vh() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('\"414243\"'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_0_vo() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('{\"a\":1}'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_0_bi() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE v BINARY "
            + "DEFAULT TO_BINARY('414243'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_0_n2() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE v "
            + "NUMBER(10,2) DEFAULT 1.50; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_1_tm() RETURNS VARCHAR(5) LANGUAGE SQL AS $$ DECLARE v TIME "
            + "DEFAULT '10:00:00'::TIME; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_1_ntz() RETURNS VARCHAR(5) LANGUAGE SQL AS $$ DECLARE v "
            + "TIMESTAMP_NTZ DEFAULT '2020-01-15 10:11:12'::TIMESTAMP_NTZ; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_1_tz() RETURNS VARCHAR(5) LANGUAGE SQL AS $$ DECLARE v "
            + "TIMESTAMP_TZ DEFAULT '2020-01-15 10:11:12 +02:00'::TIMESTAMP_TZ; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_1_va() RETURNS VARCHAR(5) LANGUAGE SQL AS $$ DECLARE v "
            + "VARIANT DEFAULT PARSE_JSON('[1,2]'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_1_vb() RETURNS VARCHAR(5) LANGUAGE SQL AS $$ DECLARE v "
            + "VARIANT DEFAULT PARSE_JSON('true'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_1_vs() RETURNS VARCHAR(5) LANGUAGE SQL AS $$ DECLARE v "
            + "VARIANT DEFAULT PARSE_JSON('\"abc\"'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_1_vh() RETURNS VARCHAR(5) LANGUAGE SQL AS $$ DECLARE v "
            + "VARIANT DEFAULT PARSE_JSON('\"414243\"'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_1_vo() RETURNS VARCHAR(5) LANGUAGE SQL AS $$ DECLARE v "
            + "VARIANT DEFAULT PARSE_JSON('{\"a\":1}'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_1_bi() RETURNS VARCHAR(5) LANGUAGE SQL AS $$ DECLARE v BINARY"
            + " DEFAULT TO_BINARY('414243'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_1_n2() RETURNS VARCHAR(5) LANGUAGE SQL AS $$ DECLARE v "
            + "NUMBER(10,2) DEFAULT 1.50; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_2_tm() RETURNS BINARY LANGUAGE SQL AS $$ DECLARE v TIME "
            + "DEFAULT '10:00:00'::TIME; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_2_ntz() RETURNS BINARY LANGUAGE SQL AS $$ DECLARE v "
            + "TIMESTAMP_NTZ DEFAULT '2020-01-15 10:11:12'::TIMESTAMP_NTZ; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_2_tz() RETURNS BINARY LANGUAGE SQL AS $$ DECLARE v "
            + "TIMESTAMP_TZ DEFAULT '2020-01-15 10:11:12 +02:00'::TIMESTAMP_TZ; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_2_va() RETURNS BINARY LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('[1,2]'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_2_vb() RETURNS BINARY LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('true'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_2_vs() RETURNS BINARY LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('\"abc\"'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_2_vh() RETURNS BINARY LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('\"414243\"'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_2_vo() RETURNS BINARY LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('{\"a\":1}'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_2_bi() RETURNS BINARY LANGUAGE SQL AS $$ DECLARE v BINARY "
            + "DEFAULT TO_BINARY('414243'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_2_n2() RETURNS BINARY LANGUAGE SQL AS $$ DECLARE v "
            + "NUMBER(10,2) DEFAULT 1.50; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_3_tm() RETURNS BOOLEAN LANGUAGE SQL AS $$ DECLARE v TIME "
            + "DEFAULT '10:00:00'::TIME; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_3_ntz() RETURNS BOOLEAN LANGUAGE SQL AS $$ DECLARE v "
            + "TIMESTAMP_NTZ DEFAULT '2020-01-15 10:11:12'::TIMESTAMP_NTZ; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_3_tz() RETURNS BOOLEAN LANGUAGE SQL AS $$ DECLARE v "
            + "TIMESTAMP_TZ DEFAULT '2020-01-15 10:11:12 +02:00'::TIMESTAMP_TZ; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_3_va() RETURNS BOOLEAN LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('[1,2]'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_3_vs() RETURNS BOOLEAN LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('\"abc\"'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_3_vh() RETURNS BOOLEAN LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('\"414243\"'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_3_vo() RETURNS BOOLEAN LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('{\"a\":1}'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_3_bi() RETURNS BOOLEAN LANGUAGE SQL AS $$ DECLARE v BINARY "
            + "DEFAULT TO_BINARY('414243'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_9_tm() RETURNS TIMESTAMP_NTZ LANGUAGE SQL AS $$ DECLARE v "
            + "TIME DEFAULT '10:00:00'::TIME; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_9_va() RETURNS TIMESTAMP_NTZ LANGUAGE SQL AS $$ DECLARE v "
            + "VARIANT DEFAULT PARSE_JSON('[1,2]'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_9_vb() RETURNS TIMESTAMP_NTZ LANGUAGE SQL AS $$ DECLARE v "
            + "VARIANT DEFAULT PARSE_JSON('true'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_9_vs() RETURNS TIMESTAMP_NTZ LANGUAGE SQL AS $$ DECLARE v "
            + "VARIANT DEFAULT PARSE_JSON('\"abc\"'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_9_vo() RETURNS TIMESTAMP_NTZ LANGUAGE SQL AS $$ DECLARE v "
            + "VARIANT DEFAULT PARSE_JSON('{\"a\":1}'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_9_bi() RETURNS TIMESTAMP_NTZ LANGUAGE SQL AS $$ DECLARE v "
            + "BINARY DEFAULT TO_BINARY('414243'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_14_tm() RETURNS OBJECT LANGUAGE SQL AS $$ DECLARE v TIME "
            + "DEFAULT '10:00:00'::TIME; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_14_ntz() RETURNS OBJECT LANGUAGE SQL AS $$ DECLARE v "
            + "TIMESTAMP_NTZ DEFAULT '2020-01-15 10:11:12'::TIMESTAMP_NTZ; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_14_tz() RETURNS OBJECT LANGUAGE SQL AS $$ DECLARE v "
            + "TIMESTAMP_TZ DEFAULT '2020-01-15 10:11:12 +02:00'::TIMESTAMP_TZ; BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_14_va() RETURNS OBJECT LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('[1,2]'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_14_vb() RETURNS OBJECT LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('true'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_14_vs() RETURNS OBJECT LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('\"abc\"'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_14_vh() RETURNS OBJECT LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('\"414243\"'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_14_vo() RETURNS OBJECT LANGUAGE SQL AS $$ DECLARE v VARIANT "
            + "DEFAULT PARSE_JSON('{\"a\":1}'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_14_bi() RETURNS OBJECT LANGUAGE SQL AS $$ DECLARE v BINARY "
            + "DEFAULT TO_BINARY('414243'); BEGIN RETURN v; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pm_14_n2() RETURNS OBJECT LANGUAGE SQL AS $$ DECLARE v "
            + "NUMBER(10,2) DEFAULT 1.50; BEGIN RETURN v; END $$");
        assertCells(new String[][] {
            {"CALL pm_0_tm()",
                "10:00:00"},
            {"CALL pm_0_ntz()",
                "2020-01-15 10:11:12.000"},
            {"CALL pm_0_tz()",
                "2020-01-15 10:11:12.000 +0200"},
            {"CALL pm_0_va()",
                "[1,2]"},
            {"CALL pm_0_vb()",
                "true"},
            {"CALL pm_0_vs()",
                "abc"},
            {"CALL pm_0_vh()",
                "414243"},
            {"CALL pm_0_vo()",
                "{\"a\":1}"},
            {"CALL pm_0_bi()",
                "414243"},
            {"CALL pm_0_n2()",
                "1.50"},
            {"CALL pm_1_tm()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 55 : String "
                + "'10:00:00' is too long and would be truncated"},
            {"CALL pm_1_ntz()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 84 : String "
                + "'2020-01-15 10:11:12.000' is too long and would be truncated"},
            {"CALL pm_1_tz()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 89 : String "
                + "'2020-01-15 10:11:12.000 +0200' is too long and would be truncated"},
            {"CALL pm_1_va()",
                "[1,2]"},
            {"CALL pm_1_vb()",
                "true"},
            {"CALL pm_1_vs()",
                "abc"},
            {"CALL pm_1_vh()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 64 : String "
                + "'414243' is too long and would be truncated"},
            {"CALL pm_1_vo()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : String "
                + "'{\"a\":1}' is too long and would be truncated"},
            {"CALL pm_1_n2()",
                "1.50"},
            {"CALL pm_2_tm()",
                "REFUSED SQL compilation error: error line 1 at position 48 |  Declared return type "
                + "'BINARY(67108864)' is incompatible with actual return type 'TIME(9)'"},
            {"CALL pm_2_ntz()",
                "REFUSED SQL compilation error: error line 1 at position 77 |  Declared return type "
                + "'BINARY(67108864)' is incompatible with actual return type 'TIMESTAMP_NTZ(9)'"},
            {"CALL pm_2_tz()",
                "REFUSED SQL compilation error: error line 1 at position 82 |  Declared return type "
                + "'BINARY(67108864)' is incompatible with actual return type 'TIMESTAMP_TZ(9)'"},
            {"CALL pm_2_va()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : Failed to "
                + "cast variant value [1,2] to BINARY"},
            {"CALL pm_2_vb()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 60 : Failed to "
                + "cast variant value true to BINARY"},
            {"CALL pm_2_vs()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : Failed to "
                + "cast variant value \"abc\" to BINARY"},
            {"CALL pm_2_vh()",
                "414243"},
            {"CALL pm_2_vo()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : Failed to "
                + "cast variant value {\"a\":1} to BINARY"},
            {"CALL pm_2_bi()",
                "414243"},
            {"CALL pm_2_n2()",
                "REFUSED SQL compilation error: error line 1 at position 44 |  Declared return type "
                + "'BINARY(67108864)' is incompatible with actual return type 'NUMBER(10,2)'"},
            {"CALL pm_3_tm()",
                "REFUSED SQL compilation error: error line 1 at position 48 |  Declared return type 'BOOLEAN'"
                + " is incompatible with actual return type 'TIME(9)'"},
            {"CALL pm_3_ntz()",
                "REFUSED SQL compilation error: error line 1 at position 77 |  Declared return type 'BOOLEAN'"
                + " is incompatible with actual return type 'TIMESTAMP_NTZ(9)'"},
            {"CALL pm_3_tz()",
                "REFUSED SQL compilation error: error line 1 at position 82 |  Declared return type 'BOOLEAN'"
                + " is incompatible with actual return type 'TIMESTAMP_TZ(9)'"},
            {"CALL pm_3_va()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : Failed to "
                + "cast variant value [1,2] to BOOLEAN"},
            {"CALL pm_3_vs()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : Failed to "
                + "cast variant value \"abc\" to BOOLEAN"},
            {"CALL pm_3_vh()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 64 : Failed to "
                + "cast variant value \"414243\" to BOOLEAN"},
            {"CALL pm_3_vo()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : Failed to "
                + "cast variant value {\"a\":1} to BOOLEAN"},
            {"CALL pm_3_bi()",
                "REFUSED SQL compilation error: error line 1 at position 53 |  Declared return type 'BOOLEAN'"
                + " is incompatible with actual return type 'BINARY(67108864)'"},
            {"CALL pm_9_tm()",
                "REFUSED SQL compilation error: error line 1 at position 48 |  Declared return type "
                + "'TIMESTAMP_NTZ(9)' is incompatible with actual return type 'TIME(9)'"},
            {"CALL pm_9_va()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : Failed to "
                + "cast variant value [1,2] to TIMESTAMP_NTZ"},
            {"CALL pm_9_vb()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 60 : Failed to "
                + "cast variant value true to TIMESTAMP_NTZ"},
            {"CALL pm_9_vs()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : Failed to "
                + "cast variant value \"abc\" to TIMESTAMP_NTZ"},
            {"CALL pm_9_vo()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 63 : Failed to "
                + "cast variant value {\"a\":1} to TIMESTAMP_NTZ"},
            {"CALL pm_9_bi()",
                "REFUSED SQL compilation error: error line 1 at position 53 |  Declared return type "
                + "'TIMESTAMP_NTZ(9)' is incompatible with actual return type 'BINARY(67108864)'"},
            {"CALL pm_14_tm()",
                "REFUSED SQL compilation error: error line 1 at position 48 |  Declared return type 'OBJECT' "
                + "is incompatible with actual return type 'TIME(9)'"},
            {"CALL pm_14_ntz()",
                "REFUSED SQL compilation error: error line 1 at position 77 |  Declared return type 'OBJECT' "
                + "is incompatible with actual return type 'TIMESTAMP_NTZ(9)'"},
            {"CALL pm_14_tz()",
                "REFUSED SQL compilation error: error line 1 at position 82 |  Declared return type 'OBJECT' "
                + "is incompatible with actual return type 'TIMESTAMP_TZ(9)'"},
            {"CALL pm_14_va()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : Failed to "
                + "cast variant value [1,2] to OBJECT"},
            {"CALL pm_14_vb()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 60 : Failed to "
                + "cast variant value true to OBJECT"},
            {"CALL pm_14_vs()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 61 : Failed to "
                + "cast variant value \"abc\" to OBJECT"},
            {"CALL pm_14_vh()",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 64 : Failed to "
                + "cast variant value \"414243\" to OBJECT"},
            {"CALL pm_14_bi()",
                "REFUSED SQL compilation error: error line 1 at position 53 |  Declared return type 'OBJECT' "
                + "is incompatible with actual return type 'BINARY(67108864)'"},
            {"CALL pm_14_n2()",
                "REFUSED SQL compilation error: error line 1 at position 44 |  Declared return type 'OBJECT' "
                + "is incompatible with actual return type 'NUMBER(10,2)'"},
        });
    }

    @Test
    public void parameterReturn() {
        engine.execute("ALTER SESSION SET TIMEZONE='UTC'");
        engine.execute("CREATE OR REPLACE FUNCTION fp1(t TIME) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN t; "
            + "END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fp2(t TIMESTAMP_NTZ) RETURNS VARCHAR(5) LANGUAGE SQL AS $$ BEGIN "
            + "RETURN t; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fp3(v VARIANT) RETURNS BOOLEAN LANGUAGE SQL AS $$ BEGIN RETURN v;"
            + " END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fp4(v VARIANT) RETURNS BINARY LANGUAGE SQL AS $$ BEGIN RETURN v; "
            + "END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pp5(v VARIANT) RETURNS NUMBER(10,2) LANGUAGE SQL AS $$ BEGIN "
            + "RETURN v; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fp6() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'10:00:00'::TIME; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION fp8() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE t TIMESTAMP_LTZ "
            + "DEFAULT '2020-01-15 10:11:12'::TIMESTAMP_LTZ; BEGIN RETURN t; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pp9() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE t TIME DEFAULT "
            + "'10:00:00'; BEGIN RETURN t; END $$");
        assertCells(new String[][] {
            {"SELECT fp1('10:00:00'::TIME)",
                "10:00:00"},
            {"SELECT fp2('2020-01-15 10:11:12'::TIMESTAMP_NTZ)",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : String "
                + "'2020-01-15 10:11:12.000' is too long and would be truncated"},
            {"SELECT fp3(PARSE_JSON('[1,2]'))::VARCHAR",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : Failed to "
                + "cast variant value [1,2] to BOOLEAN"},
            {"SELECT fp3(PARSE_JSON('\"true\"'))::VARCHAR",
                "true"},
            {"SELECT fp3(PARSE_JSON('null'))::VARCHAR",
                "null"},
            {"SELECT fp3(PARSE_JSON('0'))::VARCHAR",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : Failed to "
                + "cast variant value 0 to BOOLEAN"},
            {"SELECT fp4(PARSE_JSON('null'))",
                "null"},
            {"SELECT fp4(PARSE_JSON('\"41\"'))",
                "41"},
            {"SELECT fp4(PARSE_JSON('\"4\"'))",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : Failed to "
                + "cast variant value \"4\" to BINARY"},
            {"CALL pp5(PARSE_JSON('\"x\"'))",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : Failed to "
                + "cast variant value \"x\" to FIXED"},
            {"CALL pp5(PARSE_JSON('\"1.234\"'))",
                "1.23"},
            {"CALL pp5(PARSE_JSON('true'))",
                "1.00"},
            {"SELECT fp6()",
                "10:00:00"},
            {"SELECT fp8()",
                "2020-01-15 10:11:12.000 Z"},
            {"CALL pp9()",
                "10:00:00"},
            {"SELECT PARSE_JSON('null')::BINARY",
                "null"},
            {"SELECT PARSE_JSON('\"zz\"')::BINARY",
                "REFUSED Failed to cast variant value \"zz\" to BINARY"},
        });
    }

    @Test
    public void incompatibleReturn() {
        engine.execute("CREATE OR REPLACE PROCEDURE pi1() RETURNS DATE LANGUAGE SQL AS $$ BEGIN RETURN 1631711999; "
            + "END; $$");
        engine.execute("CREATE OR REPLACE FUNCTION fi2() RETURNS DATE LANGUAGE SQL AS $$ BEGIN RETURN 1631711999; "
            + "END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pi6() RETURNS TABLE (x INT) LANGUAGE SQL AS $$ BEGIN RETURN 1; "
            + "END; $$");
        assertCells(new String[][] {
            {"CALL pi1()",
                "REFUSED SQL compilation error: error line 1 at position 7 |  Declared return type 'DATE' is "
                + "incompatible with actual return type 'NUMBER(10,0)'"},
            {"SELECT fi2()",
                "REFUSED SQL compilation error: error line 1 at position 7 |  Declared return type 'DATE' is "
                + "incompatible with actual return type 'NUMBER(10,0)'"},
            {"EXECUTE IMMEDIATE $$ BEGIN CALL pi1(); EXCEPTION WHEN OTHER THEN RETURN SQLCODE || '/' || "
                + "SQLSTATE || '/' || SQLERRM; END; $$",
                "92225/P0000/SQL compilation error: error line 1 at position 7 |  Declared return type 'DATE'"
                + " is incompatible with actual return type 'NUMBER(10,0)'"},
            {"CREATE OR REPLACE FUNCTION fi4() RETURNS TABLE (x DATE) LANGUAGE SQL AS $$ SELECT 1 $$",
                "REFUSED Declared return type 'DATE' for column 'X' is incompatible with actual return type "
                + "'NUMBER(1,0)'"},
            {"CALL pi6()",
                "REFUSED SQL compilation error: error line 1 at position 7 |  Declared return type 'TABLE' is"
                + " incompatible with actual return type 'SCALAR'"},
            {"CREATE OR REPLACE FUNCTION fi7() RETURNS OBJECT LANGUAGE SQL AS $$ DECLARE v VARCHAR DEFAULT"
                + " 'x'; BEGIN RETURN v; END; $$",
                "REFUSED Unsupported return type for Snowscript UDF: OBJECT"},
            {"SELECT fi7()",
                "REFUSED SQL compilation error: | Unknown function FI7."},
        });
    }
}
