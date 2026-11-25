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
 * What each conversion weighs when a call chooses among a routine's overloads, measured in whole steps of a
 * NUMBER read as a FLOAT: pairs of overloads called by name whose arguments disagree, an ambiguity refusal marking
 * equal sums. Pairs of one-argument overloads, called by position and by name, give the order inside each row and
 * which conversions weigh alike; a narrowing conversion (a FLOAT read as a NUMBER, a VARIANT as a FLOAT, a
 * timestamp as a DATE or a TIME) outweighs forty steps, and two of them weigh what a VARIANT read as a NUMBER does.
 * A conversion the account refuses keeps its overload out of the running beside one that takes the call. Every
 * answer is live-verified.
 */
public class OverloadWeightMeasuredTest extends MeasuredCellsTestSupport {

    @Test
    public void oneArgumentPairs() {
        engine.execute("CREATE OR REPLACE FUNCTION p1_NUM_FLO_BOO(a FLOAT) RETURNS VARCHAR AS $$'FLOAT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_NUM_FLO_BOO(a BOOLEAN) RETURNS VARCHAR AS $$'BOOLEAN'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_NUM_FLO_VT(a FLOAT) RETURNS VARCHAR AS $$'FLOAT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_NUM_FLO_VT(a VARIANT) RETURNS VARCHAR AS $$'VARIANT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_NUM_FLO_VC(a FLOAT) RETURNS VARCHAR AS $$'FLOAT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_NUM_FLO_VC(a VARCHAR) RETURNS VARCHAR AS $$'VARCHAR'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_NUM_BOO_VT(a BOOLEAN) RETURNS VARCHAR AS $$'BOOLEAN'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_NUM_BOO_VT(a VARIANT) RETURNS VARCHAR AS $$'VARIANT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_NUM_BOO_VC(a BOOLEAN) RETURNS VARCHAR AS $$'BOOLEAN'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_NUM_BOO_VC(a VARCHAR) RETURNS VARCHAR AS $$'VARCHAR'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_NUM_VT_VC(a VARIANT) RETURNS VARCHAR AS $$'VARIANT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_NUM_VT_VC(a VARCHAR) RETURNS VARCHAR AS $$'VARCHAR'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_ARR_OBJ(a ARRAY) RETURNS VARCHAR AS $$'ARRAY'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_ARR_OBJ(a OBJECT) RETURNS VARCHAR AS $$'OBJECT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_ARR_BOO(a ARRAY) RETURNS VARCHAR AS $$'ARRAY'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_ARR_BOO(a BOOLEAN) RETURNS VARCHAR AS $$'BOOLEAN'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_ARR_VC(a ARRAY) RETURNS VARCHAR AS $$'ARRAY'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_ARR_VC(a VARCHAR) RETURNS VARCHAR AS $$'VARCHAR'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_ARR_NUM(a ARRAY) RETURNS VARCHAR AS $$'ARRAY'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_ARR_NUM(a NUMBER) RETURNS VARCHAR AS $$'NUMBER'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_ARR_FLO(a ARRAY) RETURNS VARCHAR AS $$'ARRAY'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_ARR_FLO(a FLOAT) RETURNS VARCHAR AS $$'FLOAT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_ARR_DAT(a ARRAY) RETURNS VARCHAR AS $$'ARRAY'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_ARR_DAT(a DATE) RETURNS VARCHAR AS $$'DATE'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_ARR_TIM(a ARRAY) RETURNS VARCHAR AS $$'ARRAY'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_ARR_TIM(a TIME) RETURNS VARCHAR AS $$'TIME'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_ARR_TS(a ARRAY) RETURNS VARCHAR AS $$'ARRAY'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_ARR_TS(a TIMESTAMP_NTZ) RETURNS VARCHAR AS "
            + "$$'TIMESTAMP_NTZ'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_OBJ_BOO(a OBJECT) RETURNS VARCHAR AS $$'OBJECT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_OBJ_BOO(a BOOLEAN) RETURNS VARCHAR AS $$'BOOLEAN'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_OBJ_VC(a OBJECT) RETURNS VARCHAR AS $$'OBJECT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_OBJ_VC(a VARCHAR) RETURNS VARCHAR AS $$'VARCHAR'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_OBJ_NUM(a OBJECT) RETURNS VARCHAR AS $$'OBJECT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_OBJ_NUM(a NUMBER) RETURNS VARCHAR AS $$'NUMBER'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_OBJ_FLO(a OBJECT) RETURNS VARCHAR AS $$'OBJECT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_OBJ_FLO(a FLOAT) RETURNS VARCHAR AS $$'FLOAT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_OBJ_DAT(a OBJECT) RETURNS VARCHAR AS $$'OBJECT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_OBJ_DAT(a DATE) RETURNS VARCHAR AS $$'DATE'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_OBJ_TIM(a OBJECT) RETURNS VARCHAR AS $$'OBJECT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_OBJ_TIM(a TIME) RETURNS VARCHAR AS $$'TIME'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_OBJ_TS(a OBJECT) RETURNS VARCHAR AS $$'OBJECT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_OBJ_TS(a TIMESTAMP_NTZ) RETURNS VARCHAR AS "
            + "$$'TIMESTAMP_NTZ'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_BOO_VC(a BOOLEAN) RETURNS VARCHAR AS $$'BOOLEAN'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_BOO_VC(a VARCHAR) RETURNS VARCHAR AS $$'VARCHAR'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_BOO_NUM(a BOOLEAN) RETURNS VARCHAR AS $$'BOOLEAN'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_BOO_NUM(a NUMBER) RETURNS VARCHAR AS $$'NUMBER'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_BOO_FLO(a BOOLEAN) RETURNS VARCHAR AS $$'BOOLEAN'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_BOO_FLO(a FLOAT) RETURNS VARCHAR AS $$'FLOAT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_BOO_DAT(a BOOLEAN) RETURNS VARCHAR AS $$'BOOLEAN'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_BOO_DAT(a DATE) RETURNS VARCHAR AS $$'DATE'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_BOO_TIM(a BOOLEAN) RETURNS VARCHAR AS $$'BOOLEAN'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_BOO_TIM(a TIME) RETURNS VARCHAR AS $$'TIME'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_BOO_TS(a BOOLEAN) RETURNS VARCHAR AS $$'BOOLEAN'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_BOO_TS(a TIMESTAMP_NTZ) RETURNS VARCHAR AS "
            + "$$'TIMESTAMP_NTZ'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_VC_NUM(a VARCHAR) RETURNS VARCHAR AS $$'VARCHAR'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_VC_NUM(a NUMBER) RETURNS VARCHAR AS $$'NUMBER'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_VC_FLO(a VARCHAR) RETURNS VARCHAR AS $$'VARCHAR'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_VC_FLO(a FLOAT) RETURNS VARCHAR AS $$'FLOAT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_VC_DAT(a VARCHAR) RETURNS VARCHAR AS $$'VARCHAR'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_VC_DAT(a DATE) RETURNS VARCHAR AS $$'DATE'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_VC_TIM(a VARCHAR) RETURNS VARCHAR AS $$'VARCHAR'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_VC_TIM(a TIME) RETURNS VARCHAR AS $$'TIME'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_VC_TS(a VARCHAR) RETURNS VARCHAR AS $$'VARCHAR'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_VC_TS(a TIMESTAMP_NTZ) RETURNS VARCHAR AS "
            + "$$'TIMESTAMP_NTZ'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_NUM_FLO(a NUMBER) RETURNS VARCHAR AS $$'NUMBER'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_NUM_FLO(a FLOAT) RETURNS VARCHAR AS $$'FLOAT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_NUM_DAT(a NUMBER) RETURNS VARCHAR AS $$'NUMBER'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_NUM_DAT(a DATE) RETURNS VARCHAR AS $$'DATE'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_NUM_TIM(a NUMBER) RETURNS VARCHAR AS $$'NUMBER'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_NUM_TIM(a TIME) RETURNS VARCHAR AS $$'TIME'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_NUM_TS(a NUMBER) RETURNS VARCHAR AS $$'NUMBER'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_NUM_TS(a TIMESTAMP_NTZ) RETURNS VARCHAR AS "
            + "$$'TIMESTAMP_NTZ'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_FLO_DAT(a FLOAT) RETURNS VARCHAR AS $$'FLOAT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_FLO_DAT(a DATE) RETURNS VARCHAR AS $$'DATE'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_FLO_TIM(a FLOAT) RETURNS VARCHAR AS $$'FLOAT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_FLO_TIM(a TIME) RETURNS VARCHAR AS $$'TIME'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_FLO_TS(a FLOAT) RETURNS VARCHAR AS $$'FLOAT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_FLO_TS(a TIMESTAMP_NTZ) RETURNS VARCHAR AS "
            + "$$'TIMESTAMP_NTZ'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_DAT_TIM(a DATE) RETURNS VARCHAR AS $$'DATE'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_DAT_TIM(a TIME) RETURNS VARCHAR AS $$'TIME'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_DAT_TS(a DATE) RETURNS VARCHAR AS $$'DATE'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_DAT_TS(a TIMESTAMP_NTZ) RETURNS VARCHAR AS "
            + "$$'TIMESTAMP_NTZ'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_TIM_TS(a TIME) RETURNS VARCHAR AS $$'TIME'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VT_TIM_TS(a TIMESTAMP_NTZ) RETURNS VARCHAR AS "
            + "$$'TIMESTAMP_NTZ'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_FLO_NUM_BOO(a NUMBER) RETURNS VARCHAR AS $$'NUMBER'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_FLO_NUM_BOO(a BOOLEAN) RETURNS VARCHAR AS $$'BOOLEAN'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_FLO_NUM_VT(a NUMBER) RETURNS VARCHAR AS $$'NUMBER'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_FLO_NUM_VT(a VARIANT) RETURNS VARCHAR AS $$'VARIANT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_FLO_NUM_VC(a NUMBER) RETURNS VARCHAR AS $$'NUMBER'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_FLO_NUM_VC(a VARCHAR) RETURNS VARCHAR AS $$'VARCHAR'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_FLO_BOO_VT(a BOOLEAN) RETURNS VARCHAR AS $$'BOOLEAN'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_FLO_BOO_VT(a VARIANT) RETURNS VARCHAR AS $$'VARIANT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_FLO_BOO_VC(a BOOLEAN) RETURNS VARCHAR AS $$'BOOLEAN'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_FLO_BOO_VC(a VARCHAR) RETURNS VARCHAR AS $$'VARCHAR'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_FLO_VT_VC(a VARIANT) RETURNS VARCHAR AS $$'VARIANT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_FLO_VT_VC(a VARCHAR) RETURNS VARCHAR AS $$'VARCHAR'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_TS_DAT_VC(a DATE) RETURNS VARCHAR AS $$'DATE'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_TS_DAT_VC(a VARCHAR) RETURNS VARCHAR AS $$'VARCHAR'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_TS_DAT_TIM(a DATE) RETURNS VARCHAR AS $$'DATE'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_TS_DAT_TIM(a TIME) RETURNS VARCHAR AS $$'TIME'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_TS_VC_TIM(a VARCHAR) RETURNS VARCHAR AS $$'VARCHAR'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_TS_VC_TIM(a TIME) RETURNS VARCHAR AS $$'TIME'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_BOO_VC_VT(a VARCHAR) RETURNS VARCHAR AS $$'VARCHAR'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_BOO_VC_VT(a VARIANT) RETURNS VARCHAR AS $$'VARIANT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_DAT_VC_TS(a VARCHAR) RETURNS VARCHAR AS $$'VARCHAR'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_DAT_VC_TS(a TIMESTAMP_NTZ) RETURNS VARCHAR AS "
            + "$$'TIMESTAMP_NTZ'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VC_BOO_FLO(a BOOLEAN) RETURNS VARCHAR AS $$'BOOLEAN'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VC_BOO_FLO(a FLOAT) RETURNS VARCHAR AS $$'FLOAT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VC_BOO_DAT(a BOOLEAN) RETURNS VARCHAR AS $$'BOOLEAN'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VC_BOO_DAT(a DATE) RETURNS VARCHAR AS $$'DATE'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VC_BOO_TS(a BOOLEAN) RETURNS VARCHAR AS $$'BOOLEAN'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VC_BOO_TS(a TIMESTAMP_NTZ) RETURNS VARCHAR AS "
            + "$$'TIMESTAMP_NTZ'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VC_BOO_NUM(a BOOLEAN) RETURNS VARCHAR AS $$'BOOLEAN'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VC_BOO_NUM(a NUMBER) RETURNS VARCHAR AS $$'NUMBER'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VC_BOO_TIM(a BOOLEAN) RETURNS VARCHAR AS $$'BOOLEAN'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VC_BOO_TIM(a TIME) RETURNS VARCHAR AS $$'TIME'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VC_FLO_DAT(a FLOAT) RETURNS VARCHAR AS $$'FLOAT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VC_FLO_DAT(a DATE) RETURNS VARCHAR AS $$'DATE'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VC_FLO_TS(a FLOAT) RETURNS VARCHAR AS $$'FLOAT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VC_FLO_TS(a TIMESTAMP_NTZ) RETURNS VARCHAR AS "
            + "$$'TIMESTAMP_NTZ'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VC_FLO_NUM(a FLOAT) RETURNS VARCHAR AS $$'FLOAT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VC_FLO_NUM(a NUMBER) RETURNS VARCHAR AS $$'NUMBER'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VC_FLO_TIM(a FLOAT) RETURNS VARCHAR AS $$'FLOAT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VC_FLO_TIM(a TIME) RETURNS VARCHAR AS $$'TIME'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VC_DAT_TS(a DATE) RETURNS VARCHAR AS $$'DATE'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VC_DAT_TS(a TIMESTAMP_NTZ) RETURNS VARCHAR AS "
            + "$$'TIMESTAMP_NTZ'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VC_DAT_NUM(a DATE) RETURNS VARCHAR AS $$'DATE'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VC_DAT_NUM(a NUMBER) RETURNS VARCHAR AS $$'NUMBER'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VC_DAT_TIM(a DATE) RETURNS VARCHAR AS $$'DATE'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VC_DAT_TIM(a TIME) RETURNS VARCHAR AS $$'TIME'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VC_TS_NUM(a TIMESTAMP_NTZ) RETURNS VARCHAR AS "
            + "$$'TIMESTAMP_NTZ'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VC_TS_NUM(a NUMBER) RETURNS VARCHAR AS $$'NUMBER'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VC_TS_TIM(a TIMESTAMP_NTZ) RETURNS VARCHAR AS "
            + "$$'TIMESTAMP_NTZ'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VC_TS_TIM(a TIME) RETURNS VARCHAR AS $$'TIME'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VC_NUM_TIM(a NUMBER) RETURNS VARCHAR AS $$'NUMBER'$$");
        engine.execute("CREATE OR REPLACE FUNCTION p1_VC_NUM_TIM(a TIME) RETURNS VARCHAR AS $$'TIME'$$");
        engine.execute("CREATE OR REPLACE FUNCTION wf_VAR_TSN_1(a VARIANT, b FLOAT, c NUMBER, d NUMBER) RETURNS "
            + "VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION wf_VAR_TSN_1(a TIMESTAMP_NTZ, b NUMBER, c NUMBER, d NUMBER) "
            + "RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION wf_VAR_TSN_2(a VARIANT, b BOOLEAN, c NUMBER, d NUMBER) RETURNS "
            + "VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION wf_VAR_TSN_2(a TIMESTAMP_NTZ, b NUMBER, c NUMBER, d NUMBER) "
            + "RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION wf_VAR_TSN_3(a VARIANT, b VARIANT, c NUMBER, d NUMBER) RETURNS "
            + "VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION wf_VAR_TSN_3(a TIMESTAMP_NTZ, b NUMBER, c NUMBER, d NUMBER) "
            + "RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION wf_VAR_TSN_4(a VARIANT, b VARCHAR, c NUMBER, d NUMBER) RETURNS "
            + "VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION wf_VAR_TSN_4(a TIMESTAMP_NTZ, b NUMBER, c NUMBER, d NUMBER) "
            + "RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION wf_VAR_TSN_5(a VARIANT, b VARCHAR, c FLOAT, d NUMBER) RETURNS "
            + "VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION wf_VAR_TSN_5(a TIMESTAMP_NTZ, b NUMBER, c NUMBER, d NUMBER) "
            + "RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION wf_VAR_TSN_6(a VARIANT, b VARCHAR, c BOOLEAN, d NUMBER) RETURNS "
            + "VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION wf_VAR_TSN_6(a TIMESTAMP_NTZ, b NUMBER, c NUMBER, d NUMBER) "
            + "RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION wf_VAR_TSN_7(a VARIANT, b VARCHAR, c VARIANT, d NUMBER) RETURNS "
            + "VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION wf_VAR_TSN_7(a TIMESTAMP_NTZ, b NUMBER, c NUMBER, d NUMBER) "
            + "RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION wf_VAR_TSN_8(a VARIANT, b VARCHAR, c VARCHAR, d NUMBER) RETURNS "
            + "VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION wf_VAR_TSN_8(a TIMESTAMP_NTZ, b NUMBER, c NUMBER, d NUMBER) "
            + "RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION wf_VAR_TSN_9(a VARIANT, b VARCHAR, c VARCHAR, d FLOAT) RETURNS "
            + "VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION wf_VAR_TSN_9(a TIMESTAMP_NTZ, b NUMBER, c NUMBER, d NUMBER) "
            + "RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION wf_VAR_TSN_10(a VARIANT, b VARCHAR, c VARCHAR, d BOOLEAN) RETURNS"
            + " VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION wf_VAR_TSN_10(a TIMESTAMP_NTZ, b NUMBER, c NUMBER, d NUMBER) "
            + "RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION wf_VAR_TSN_11(a VARIANT, b VARCHAR, c VARCHAR, d VARIANT) RETURNS"
            + " VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION wf_VAR_TSN_11(a TIMESTAMP_NTZ, b NUMBER, c NUMBER, d NUMBER) "
            + "RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION wf_VAR_TSN_12(a VARIANT, b VARCHAR, c VARCHAR, d VARCHAR) RETURNS"
            + " VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION wf_VAR_TSN_12(a TIMESTAMP_NTZ, b NUMBER, c NUMBER, d NUMBER) "
            + "RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE PROCEDURE wp_VAR_TSN_1(a VARIANT, b FLOAT, c NUMBER, d NUMBER) RETURNS "
            + "VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE wp_VAR_TSN_1(a TIMESTAMP_NTZ, b NUMBER, c NUMBER, d NUMBER) "
            + "RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE wp_VAR_TSN_2(a VARIANT, b BOOLEAN, c NUMBER, d NUMBER) RETURNS "
            + "VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE wp_VAR_TSN_2(a TIMESTAMP_NTZ, b NUMBER, c NUMBER, d NUMBER) "
            + "RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE wp_VAR_TSN_3(a VARIANT, b VARIANT, c NUMBER, d NUMBER) RETURNS "
            + "VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE wp_VAR_TSN_3(a TIMESTAMP_NTZ, b NUMBER, c NUMBER, d NUMBER) "
            + "RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE wp_VAR_TSN_4(a VARIANT, b VARCHAR, c NUMBER, d NUMBER) RETURNS "
            + "VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE wp_VAR_TSN_4(a TIMESTAMP_NTZ, b NUMBER, c NUMBER, d NUMBER) "
            + "RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE wp_VAR_TSN_5(a VARIANT, b VARCHAR, c FLOAT, d NUMBER) RETURNS "
            + "VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE wp_VAR_TSN_5(a TIMESTAMP_NTZ, b NUMBER, c NUMBER, d NUMBER) "
            + "RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE wp_VAR_TSN_6(a VARIANT, b VARCHAR, c BOOLEAN, d NUMBER) RETURNS "
            + "VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE wp_VAR_TSN_6(a TIMESTAMP_NTZ, b NUMBER, c NUMBER, d NUMBER) "
            + "RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE wp_VAR_TSN_7(a VARIANT, b VARCHAR, c VARIANT, d NUMBER) RETURNS "
            + "VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE wp_VAR_TSN_7(a TIMESTAMP_NTZ, b NUMBER, c NUMBER, d NUMBER) "
            + "RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE wp_VAR_TSN_8(a VARIANT, b VARCHAR, c VARCHAR, d NUMBER) RETURNS "
            + "VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE wp_VAR_TSN_8(a TIMESTAMP_NTZ, b NUMBER, c NUMBER, d NUMBER) "
            + "RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE wp_VAR_TSN_9(a VARIANT, b VARCHAR, c VARCHAR, d FLOAT) RETURNS "
            + "VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE wp_VAR_TSN_9(a TIMESTAMP_NTZ, b NUMBER, c NUMBER, d NUMBER) "
            + "RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE wp_VAR_TSN_10(a VARIANT, b VARCHAR, c VARCHAR, d BOOLEAN) "
            + "RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE wp_VAR_TSN_10(a TIMESTAMP_NTZ, b NUMBER, c NUMBER, d NUMBER) "
            + "RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE wp_VAR_TSN_11(a VARIANT, b VARCHAR, c VARCHAR, d VARIANT) "
            + "RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE wp_VAR_TSN_11(a TIMESTAMP_NTZ, b NUMBER, c NUMBER, d NUMBER) "
            + "RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE wp_VAR_TSN_12(a VARIANT, b VARCHAR, c VARCHAR, d VARCHAR) "
            + "RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE wp_VAR_TSN_12(a TIMESTAMP_NTZ, b NUMBER, c NUMBER, d NUMBER) "
            + "RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        assertCells(new String[][] {
            {"SELECT p1_NUM_FLO_BOO(CAST(NULL AS NUMBER))",
                "FLOAT"},
            {"SELECT p1_NUM_FLO_BOO(a => CAST(NULL AS NUMBER))",
                "FLOAT"},
            {"SELECT p1_NUM_FLO_VT(CAST(NULL AS NUMBER))",
                "FLOAT"},
            {"SELECT p1_NUM_FLO_VT(a => CAST(NULL AS NUMBER))",
                "FLOAT"},
            {"SELECT p1_NUM_FLO_VC(CAST(NULL AS NUMBER))",
                "FLOAT"},
            {"SELECT p1_NUM_FLO_VC(a => CAST(NULL AS NUMBER))",
                "FLOAT"},
            {"SELECT p1_NUM_BOO_VT(CAST(NULL AS NUMBER))",
                "BOOLEAN"},
            {"SELECT p1_NUM_BOO_VT(a => CAST(NULL AS NUMBER))",
                "REFUSED SQL compilation error: | More than one function of P1_NUM_BOO_VT are resolved in "
                + "prepare state."},
            {"SELECT p1_NUM_BOO_VC(CAST(NULL AS NUMBER))",
                "BOOLEAN"},
            {"SELECT p1_NUM_BOO_VC(a => CAST(NULL AS NUMBER))",
                "BOOLEAN"},
            {"SELECT p1_NUM_VT_VC(CAST(NULL AS NUMBER))",
                "VARIANT"},
            {"SELECT p1_NUM_VT_VC(a => CAST(NULL AS NUMBER))",
                "VARIANT"},
            {"SELECT p1_VT_ARR_OBJ(CAST(NULL AS VARIANT))",
                "ARRAY"},
            {"SELECT p1_VT_ARR_OBJ(a => CAST(NULL AS VARIANT))",
                "REFUSED SQL compilation error: | More than one function of P1_VT_ARR_OBJ are resolved in "
                + "prepare state."},
            {"SELECT p1_VT_ARR_BOO(CAST(NULL AS VARIANT))",
                "ARRAY"},
            {"SELECT p1_VT_ARR_BOO(a => CAST(NULL AS VARIANT))",
                "REFUSED SQL compilation error: | More than one function of P1_VT_ARR_BOO are resolved in "
                + "prepare state."},
            {"SELECT p1_VT_ARR_VC(CAST(NULL AS VARIANT))",
                "ARRAY"},
            {"SELECT p1_VT_ARR_VC(a => CAST(NULL AS VARIANT))",
                "REFUSED SQL compilation error: | More than one function of P1_VT_ARR_VC are resolved in "
                + "prepare state."},
            {"SELECT p1_VT_ARR_NUM(CAST(NULL AS VARIANT))",
                "ARRAY"},
            {"SELECT p1_VT_ARR_NUM(a => CAST(NULL AS VARIANT))",
                "ARRAY"},
            {"SELECT p1_VT_ARR_FLO(CAST(NULL AS VARIANT))",
                "ARRAY"},
            {"SELECT p1_VT_ARR_FLO(a => CAST(NULL AS VARIANT))",
                "ARRAY"},
            {"SELECT p1_VT_ARR_DAT(CAST(NULL AS VARIANT))",
                "ARRAY"},
            {"SELECT p1_VT_ARR_DAT(a => CAST(NULL AS VARIANT))",
                "ARRAY"},
            {"SELECT p1_VT_ARR_TIM(CAST(NULL AS VARIANT))",
                "ARRAY"},
            {"SELECT p1_VT_ARR_TIM(a => CAST(NULL AS VARIANT))",
                "ARRAY"},
            {"SELECT p1_VT_ARR_TS(CAST(NULL AS VARIANT))",
                "ARRAY"},
            {"SELECT p1_VT_ARR_TS(a => CAST(NULL AS VARIANT))",
                "ARRAY"},
            {"SELECT p1_VT_OBJ_BOO(CAST(NULL AS VARIANT))",
                "BOOLEAN"},
            {"SELECT p1_VT_OBJ_BOO(a => CAST(NULL AS VARIANT))",
                "REFUSED SQL compilation error: | More than one function of P1_VT_OBJ_BOO are resolved in "
                + "prepare state."},
            {"SELECT p1_VT_OBJ_VC(CAST(NULL AS VARIANT))",
                "OBJECT"},
            {"SELECT p1_VT_OBJ_VC(a => CAST(NULL AS VARIANT))",
                "REFUSED SQL compilation error: | More than one function of P1_VT_OBJ_VC are resolved in "
                + "prepare state."},
            {"SELECT p1_VT_OBJ_NUM(CAST(NULL AS VARIANT))",
                "OBJECT"},
            {"SELECT p1_VT_OBJ_NUM(a => CAST(NULL AS VARIANT))",
                "OBJECT"},
            {"SELECT p1_VT_OBJ_FLO(CAST(NULL AS VARIANT))",
                "OBJECT"},
            {"SELECT p1_VT_OBJ_FLO(a => CAST(NULL AS VARIANT))",
                "OBJECT"},
            {"SELECT p1_VT_OBJ_DAT(CAST(NULL AS VARIANT))",
                "OBJECT"},
            {"SELECT p1_VT_OBJ_DAT(a => CAST(NULL AS VARIANT))",
                "OBJECT"},
            {"SELECT p1_VT_OBJ_TIM(CAST(NULL AS VARIANT))",
                "OBJECT"},
            {"SELECT p1_VT_OBJ_TIM(a => CAST(NULL AS VARIANT))",
                "OBJECT"},
            {"SELECT p1_VT_OBJ_TS(CAST(NULL AS VARIANT))",
                "OBJECT"},
            {"SELECT p1_VT_OBJ_TS(a => CAST(NULL AS VARIANT))",
                "OBJECT"},
            {"SELECT p1_VT_BOO_VC(CAST(NULL AS VARIANT))",
                "BOOLEAN"},
            {"SELECT p1_VT_BOO_VC(a => CAST(NULL AS VARIANT))",
                "REFUSED SQL compilation error: | More than one function of P1_VT_BOO_VC are resolved in "
                + "prepare state."},
            {"SELECT p1_VT_BOO_NUM(CAST(NULL AS VARIANT))",
                "BOOLEAN"},
            {"SELECT p1_VT_BOO_NUM(a => CAST(NULL AS VARIANT))",
                "BOOLEAN"},
            {"SELECT p1_VT_BOO_FLO(CAST(NULL AS VARIANT))",
                "BOOLEAN"},
            {"SELECT p1_VT_BOO_FLO(a => CAST(NULL AS VARIANT))",
                "BOOLEAN"},
            {"SELECT p1_VT_BOO_DAT(CAST(NULL AS VARIANT))",
                "BOOLEAN"},
            {"SELECT p1_VT_BOO_DAT(a => CAST(NULL AS VARIANT))",
                "BOOLEAN"},
            {"SELECT p1_VT_BOO_TIM(CAST(NULL AS VARIANT))",
                "BOOLEAN"},
            {"SELECT p1_VT_BOO_TIM(a => CAST(NULL AS VARIANT))",
                "BOOLEAN"},
            {"SELECT p1_VT_BOO_TS(CAST(NULL AS VARIANT))",
                "BOOLEAN"},
            {"SELECT p1_VT_BOO_TS(a => CAST(NULL AS VARIANT))",
                "BOOLEAN"},
            {"SELECT p1_VT_VC_NUM(CAST(NULL AS VARIANT))",
                "VARCHAR"},
            {"SELECT p1_VT_VC_NUM(a => CAST(NULL AS VARIANT))",
                "VARCHAR"},
            {"SELECT p1_VT_VC_FLO(CAST(NULL AS VARIANT))",
                "VARCHAR"},
            {"SELECT p1_VT_VC_FLO(a => CAST(NULL AS VARIANT))",
                "VARCHAR"},
            {"SELECT p1_VT_VC_DAT(CAST(NULL AS VARIANT))",
                "VARCHAR"},
            {"SELECT p1_VT_VC_DAT(a => CAST(NULL AS VARIANT))",
                "VARCHAR"},
            {"SELECT p1_VT_VC_TIM(CAST(NULL AS VARIANT))",
                "VARCHAR"},
            {"SELECT p1_VT_VC_TIM(a => CAST(NULL AS VARIANT))",
                "VARCHAR"},
            {"SELECT p1_VT_VC_TS(CAST(NULL AS VARIANT))",
                "VARCHAR"},
            {"SELECT p1_VT_VC_TS(a => CAST(NULL AS VARIANT))",
                "VARCHAR"},
            {"SELECT p1_VT_NUM_FLO(CAST(NULL AS VARIANT))",
                "FLOAT"},
            {"SELECT p1_VT_NUM_FLO(a => CAST(NULL AS VARIANT))",
                "FLOAT"},
            {"SELECT p1_VT_NUM_DAT(CAST(NULL AS VARIANT))",
                "DATE"},
            {"SELECT p1_VT_NUM_DAT(a => CAST(NULL AS VARIANT))",
                "DATE"},
            {"SELECT p1_VT_NUM_TIM(CAST(NULL AS VARIANT))",
                "TIME"},
            {"SELECT p1_VT_NUM_TIM(a => CAST(NULL AS VARIANT))",
                "TIME"},
            {"SELECT p1_VT_NUM_TS(CAST(NULL AS VARIANT))",
                "TIMESTAMP_NTZ"},
            {"SELECT p1_VT_NUM_TS(a => CAST(NULL AS VARIANT))",
                "TIMESTAMP_NTZ"},
            {"SELECT p1_VT_FLO_DAT(CAST(NULL AS VARIANT))",
                "DATE"},
            {"SELECT p1_VT_FLO_DAT(a => CAST(NULL AS VARIANT))",
                "DATE"},
            {"SELECT p1_VT_FLO_TIM(CAST(NULL AS VARIANT))",
                "TIME"},
            {"SELECT p1_VT_FLO_TIM(a => CAST(NULL AS VARIANT))",
                "TIME"},
            {"SELECT p1_VT_FLO_TS(CAST(NULL AS VARIANT))",
                "TIMESTAMP_NTZ"},
            {"SELECT p1_VT_FLO_TS(a => CAST(NULL AS VARIANT))",
                "TIMESTAMP_NTZ"},
            {"SELECT p1_VT_DAT_TIM(CAST(NULL AS VARIANT))",
                "DATE"},
            {"SELECT p1_VT_DAT_TIM(a => CAST(NULL AS VARIANT))",
                "REFUSED SQL compilation error: | More than one function of P1_VT_DAT_TIM are resolved in "
                + "prepare state."},
            {"SELECT p1_VT_DAT_TS(CAST(NULL AS VARIANT))",
                "DATE"},
            {"SELECT p1_VT_DAT_TS(a => CAST(NULL AS VARIANT))",
                "REFUSED SQL compilation error: | More than one function of P1_VT_DAT_TS are resolved in "
                + "prepare state."},
            {"SELECT p1_VT_TIM_TS(CAST(NULL AS VARIANT))",
                "TIME"},
            {"SELECT p1_VT_TIM_TS(a => CAST(NULL AS VARIANT))",
                "REFUSED SQL compilation error: | More than one function of P1_VT_TIM_TS are resolved in "
                + "prepare state."},
            {"SELECT p1_FLO_NUM_BOO(CAST(NULL AS FLOAT))",
                "BOOLEAN"},
            {"SELECT p1_FLO_NUM_BOO(a => CAST(NULL AS FLOAT))",
                "BOOLEAN"},
            {"SELECT p1_FLO_NUM_VT(CAST(NULL AS FLOAT))",
                "VARIANT"},
            {"SELECT p1_FLO_NUM_VT(a => CAST(NULL AS FLOAT))",
                "VARIANT"},
            {"SELECT p1_FLO_NUM_VC(CAST(NULL AS FLOAT))",
                "VARCHAR"},
            {"SELECT p1_FLO_NUM_VC(a => CAST(NULL AS FLOAT))",
                "VARCHAR"},
            {"SELECT p1_FLO_BOO_VT(CAST(NULL AS FLOAT))",
                "BOOLEAN"},
            {"SELECT p1_FLO_BOO_VT(a => CAST(NULL AS FLOAT))",
                "REFUSED SQL compilation error: | More than one function of P1_FLO_BOO_VT are resolved in "
                + "prepare state."},
            {"SELECT p1_FLO_BOO_VC(CAST(NULL AS FLOAT))",
                "BOOLEAN"},
            {"SELECT p1_FLO_BOO_VC(a => CAST(NULL AS FLOAT))",
                "BOOLEAN"},
            {"SELECT p1_FLO_VT_VC(CAST(NULL AS FLOAT))",
                "VARIANT"},
            {"SELECT p1_FLO_VT_VC(a => CAST(NULL AS FLOAT))",
                "VARIANT"},
            {"SELECT p1_TS_DAT_VC(CAST(NULL AS TIMESTAMP_NTZ))",
                "VARCHAR"},
            {"SELECT p1_TS_DAT_VC(a => CAST(NULL AS TIMESTAMP_NTZ))",
                "VARCHAR"},
            {"SELECT p1_TS_DAT_TIM(CAST(NULL AS TIMESTAMP_NTZ))",
                "DATE"},
            {"SELECT p1_TS_DAT_TIM(a => CAST(NULL AS TIMESTAMP_NTZ))",
                "REFUSED SQL compilation error: | More than one function of P1_TS_DAT_TIM are resolved in "
                + "prepare state."},
            {"SELECT p1_TS_VC_TIM(CAST(NULL AS TIMESTAMP_NTZ))",
                "VARCHAR"},
            {"SELECT p1_TS_VC_TIM(a => CAST(NULL AS TIMESTAMP_NTZ))",
                "VARCHAR"},
            {"SELECT p1_BOO_VC_VT(CAST(NULL AS BOOLEAN))",
                "VARCHAR"},
            {"SELECT p1_BOO_VC_VT(a => CAST(NULL AS BOOLEAN))",
                "REFUSED SQL compilation error: | More than one function of P1_BOO_VC_VT are resolved in "
                + "prepare state."},
            {"SELECT p1_DAT_VC_TS(CAST(NULL AS DATE))",
                "TIMESTAMP_NTZ"},
            {"SELECT p1_DAT_VC_TS(a => CAST(NULL AS DATE))",
                "TIMESTAMP_NTZ"},
            {"SELECT p1_VC_BOO_FLO(CAST(NULL AS VARCHAR))",
                "BOOLEAN"},
            {"SELECT p1_VC_BOO_FLO(a => CAST(NULL AS VARCHAR))",
                "BOOLEAN"},
            {"SELECT p1_VC_BOO_DAT(CAST(NULL AS VARCHAR))",
                "BOOLEAN"},
            {"SELECT p1_VC_BOO_DAT(a => CAST(NULL AS VARCHAR))",
                "BOOLEAN"},
            {"SELECT p1_VC_BOO_TS(CAST(NULL AS VARCHAR))",
                "BOOLEAN"},
            {"SELECT p1_VC_BOO_TS(a => CAST(NULL AS VARCHAR))",
                "BOOLEAN"},
            {"SELECT p1_VC_BOO_NUM(CAST(NULL AS VARCHAR))",
                "BOOLEAN"},
            {"SELECT p1_VC_BOO_NUM(a => CAST(NULL AS VARCHAR))",
                "BOOLEAN"},
            {"SELECT p1_VC_BOO_TIM(CAST(NULL AS VARCHAR))",
                "BOOLEAN"},
            {"SELECT p1_VC_BOO_TIM(a => CAST(NULL AS VARCHAR))",
                "BOOLEAN"},
            {"SELECT p1_VC_FLO_DAT(CAST(NULL AS VARCHAR))",
                "DATE"},
            {"SELECT p1_VC_FLO_DAT(a => CAST(NULL AS VARCHAR))",
                "REFUSED SQL compilation error: | More than one function of P1_VC_FLO_DAT are resolved in "
                + "prepare state."},
            {"SELECT p1_VC_FLO_TS(CAST(NULL AS VARCHAR))",
                "FLOAT"},
            {"SELECT p1_VC_FLO_TS(a => CAST(NULL AS VARCHAR))",
                "REFUSED SQL compilation error: | More than one function of P1_VC_FLO_TS are resolved in "
                + "prepare state."},
            {"SELECT p1_VC_FLO_NUM(CAST(NULL AS VARCHAR))",
                "FLOAT"},
            {"SELECT p1_VC_FLO_NUM(a => CAST(NULL AS VARCHAR))",
                "FLOAT"},
            {"SELECT p1_VC_FLO_TIM(CAST(NULL AS VARCHAR))",
                "FLOAT"},
            {"SELECT p1_VC_FLO_TIM(a => CAST(NULL AS VARCHAR))",
                "FLOAT"},
            {"SELECT p1_VC_DAT_TS(CAST(NULL AS VARCHAR))",
                "DATE"},
            {"SELECT p1_VC_DAT_TS(a => CAST(NULL AS VARCHAR))",
                "REFUSED SQL compilation error: | More than one function of P1_VC_DAT_TS are resolved in "
                + "prepare state."},
            {"SELECT p1_VC_DAT_NUM(CAST(NULL AS VARCHAR))",
                "DATE"},
            {"SELECT p1_VC_DAT_NUM(a => CAST(NULL AS VARCHAR))",
                "DATE"},
            {"SELECT p1_VC_DAT_TIM(CAST(NULL AS VARCHAR))",
                "DATE"},
            {"SELECT p1_VC_DAT_TIM(a => CAST(NULL AS VARCHAR))",
                "DATE"},
            {"SELECT p1_VC_TS_NUM(CAST(NULL AS VARCHAR))",
                "TIMESTAMP_NTZ"},
            {"SELECT p1_VC_TS_NUM(a => CAST(NULL AS VARCHAR))",
                "TIMESTAMP_NTZ"},
            {"SELECT p1_VC_TS_TIM(CAST(NULL AS VARCHAR))",
                "TIMESTAMP_NTZ"},
            {"SELECT p1_VC_TS_TIM(a => CAST(NULL AS VARCHAR))",
                "TIMESTAMP_NTZ"},
            {"SELECT p1_VC_NUM_TIM(CAST(NULL AS VARCHAR))",
                "NUMBER"},
            {"SELECT p1_VC_NUM_TIM(a => CAST(NULL AS VARCHAR))",
                "REFUSED SQL compilation error: | More than one function of P1_VC_NUM_TIM are resolved in "
                + "prepare state."},
            {"SELECT wf_VAR_TSN_1(a => CAST(NULL AS VARIANT), b => 1, c => 1, d => 1)",
                "A"},
            {"SELECT wf_VAR_TSN_2(a => CAST(NULL AS VARIANT), b => 1, c => 1, d => 1)",
                "REFUSED SQL compilation error: | More than one function of WF_VAR_TSN_2 are resolved in "
                + "prepare state."},
            {"SELECT wf_VAR_TSN_3(a => CAST(NULL AS VARIANT), b => 1, c => 1, d => 1)",
                "REFUSED SQL compilation error: | More than one function of WF_VAR_TSN_3 are resolved in "
                + "prepare state."},
            {"SELECT wf_VAR_TSN_4(a => CAST(NULL AS VARIANT), b => 1, c => 1, d => 1)",
                "B"},
            {"SELECT wf_VAR_TSN_5(a => CAST(NULL AS VARIANT), b => 1, c => 1, d => 1)",
                "B"},
            {"SELECT wf_VAR_TSN_6(a => CAST(NULL AS VARIANT), b => 1, c => 1, d => 1)",
                "B"},
            {"SELECT wf_VAR_TSN_7(a => CAST(NULL AS VARIANT), b => 1, c => 1, d => 1)",
                "B"},
            {"SELECT wf_VAR_TSN_8(a => CAST(NULL AS VARIANT), b => 1, c => 1, d => 1)",
                "B"},
            {"SELECT wf_VAR_TSN_9(a => CAST(NULL AS VARIANT), b => 1, c => 1, d => 1)",
                "B"},
            {"SELECT wf_VAR_TSN_10(a => CAST(NULL AS VARIANT), b => 1, c => 1, d => 1)",
                "B"},
            {"SELECT wf_VAR_TSN_11(a => CAST(NULL AS VARIANT), b => 1, c => 1, d => 1)",
                "B"},
            {"SELECT wf_VAR_TSN_12(a => CAST(NULL AS VARIANT), b => 1, c => 1, d => 1)",
                "B"},
            {"CALL wp_VAR_TSN_1(a => CAST(NULL AS VARIANT), b => 1, c => 1, d => 1)",
                "A"},
            {"CALL wp_VAR_TSN_2(a => CAST(NULL AS VARIANT), b => 1, c => 1, d => 1)",
                "REFUSED SQL compilation error: | More than one function of WP_VAR_TSN_2 are resolved in "
                + "prepare state."},
            {"CALL wp_VAR_TSN_3(a => CAST(NULL AS VARIANT), b => 1, c => 1, d => 1)",
                "REFUSED SQL compilation error: | More than one function of WP_VAR_TSN_3 are resolved in "
                + "prepare state."},
            {"CALL wp_VAR_TSN_4(a => CAST(NULL AS VARIANT), b => 1, c => 1, d => 1)",
                "B"},
            {"CALL wp_VAR_TSN_5(a => CAST(NULL AS VARIANT), b => 1, c => 1, d => 1)",
                "B"},
            {"CALL wp_VAR_TSN_6(a => CAST(NULL AS VARIANT), b => 1, c => 1, d => 1)",
                "B"},
            {"CALL wp_VAR_TSN_7(a => CAST(NULL AS VARIANT), b => 1, c => 1, d => 1)",
                "B"},
            {"CALL wp_VAR_TSN_8(a => CAST(NULL AS VARIANT), b => 1, c => 1, d => 1)",
                "B"},
            {"CALL wp_VAR_TSN_9(a => CAST(NULL AS VARIANT), b => 1, c => 1, d => 1)",
                "B"},
            {"CALL wp_VAR_TSN_10(a => CAST(NULL AS VARIANT), b => 1, c => 1, d => 1)",
                "B"},
            {"CALL wp_VAR_TSN_11(a => CAST(NULL AS VARIANT), b => 1, c => 1, d => 1)",
                "B"},
            {"CALL wp_VAR_TSN_12(a => CAST(NULL AS VARIANT), b => 1, c => 1, d => 1)",
                "B"},
        });
    }

    @Test
    public void weightsAgainstSteps() {
        engine.execute("CREATE OR REPLACE FUNCTION uf_NUM_BOO_1(a NUMBER, r0 FLOAT, r1 NUMBER, r2 NUMBER, r3 NUMBER,"
            + " r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_NUM_BOO_1(a BOOLEAN, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_NUM_BOO_2(a NUMBER, r0 FLOAT, r1 FLOAT, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_NUM_BOO_2(a BOOLEAN, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_NUM_BOO_3(a NUMBER, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_NUM_BOO_3(a BOOLEAN, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_NUM_VT_1(a NUMBER, r0 FLOAT, r1 NUMBER, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_NUM_VT_1(a VARIANT, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_NUM_VT_2(a NUMBER, r0 FLOAT, r1 FLOAT, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_NUM_VT_2(a VARIANT, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_NUM_VT_3(a NUMBER, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 NUMBER, r4"
            + " NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_NUM_VT_3(a VARIANT, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_NUM_VC_3(a NUMBER, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 NUMBER, r4"
            + " NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_NUM_VC_3(a VARCHAR, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_NUM_VC_4(a NUMBER, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 FLOAT, r4 "
            + "NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_NUM_VC_4(a VARCHAR, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_NUM_VC_5(a NUMBER, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 FLOAT, r4 "
            + "FLOAT, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_NUM_VC_5(a VARCHAR, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_FLO_BOO_1(a FLOAT, r0 FLOAT, r1 NUMBER, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_FLO_BOO_1(a BOOLEAN, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_FLO_BOO_2(a FLOAT, r0 FLOAT, r1 FLOAT, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_FLO_BOO_2(a BOOLEAN, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_FLO_BOO_3(a FLOAT, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 NUMBER, r4"
            + " NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_FLO_BOO_3(a BOOLEAN, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_FLO_VT_1(a FLOAT, r0 FLOAT, r1 NUMBER, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_FLO_VT_1(a VARIANT, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_FLO_VT_2(a FLOAT, r0 FLOAT, r1 FLOAT, r2 NUMBER, r3 NUMBER, r4"
            + " NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_FLO_VT_2(a VARIANT, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_FLO_VT_3(a FLOAT, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 NUMBER, r4 "
            + "NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_FLO_VT_3(a VARIANT, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_FLO_VC_3(a FLOAT, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 NUMBER, r4 "
            + "NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_FLO_VC_3(a VARCHAR, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_FLO_VC_4(a FLOAT, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 FLOAT, r4 "
            + "NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_FLO_VC_4(a VARCHAR, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_FLO_VC_5(a FLOAT, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 FLOAT, r4 "
            + "FLOAT, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_FLO_VC_5(a VARCHAR, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VC_BOO_1(a VARCHAR, r0 FLOAT, r1 NUMBER, r2 NUMBER, r3 NUMBER,"
            + " r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VC_BOO_1(a BOOLEAN, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VC_BOO_2(a VARCHAR, r0 FLOAT, r1 FLOAT, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VC_BOO_2(a BOOLEAN, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VC_FLO_1(a VARCHAR, r0 FLOAT, r1 NUMBER, r2 NUMBER, r3 NUMBER,"
            + " r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VC_FLO_1(a FLOAT, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VC_FLO_2(a VARCHAR, r0 FLOAT, r1 FLOAT, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VC_FLO_2(a FLOAT, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VC_FLO_3(a VARCHAR, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VC_FLO_3(a FLOAT, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VC_DAT_1(a VARCHAR, r0 FLOAT, r1 NUMBER, r2 NUMBER, r3 NUMBER,"
            + " r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VC_DAT_1(a DATE, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VC_DAT_2(a VARCHAR, r0 FLOAT, r1 FLOAT, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VC_DAT_2(a DATE, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VC_DAT_3(a VARCHAR, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VC_DAT_3(a DATE, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VC_TS_1(a VARCHAR, r0 FLOAT, r1 NUMBER, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VC_TS_1(a TIMESTAMP_NTZ, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VC_TS_2(a VARCHAR, r0 FLOAT, r1 FLOAT, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VC_TS_2(a TIMESTAMP_NTZ, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VC_TS_3(a VARCHAR, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 NUMBER, r4"
            + " NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VC_TS_3(a TIMESTAMP_NTZ, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VC_NUM_2(a VARCHAR, r0 FLOAT, r1 FLOAT, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VC_NUM_2(a NUMBER, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 NUMBER,"
            + " r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VC_NUM_3(a VARCHAR, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VC_NUM_3(a NUMBER, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 NUMBER,"
            + " r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VC_NUM_4(a VARCHAR, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 FLOAT, r4"
            + " NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VC_NUM_4(a NUMBER, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 NUMBER,"
            + " r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VC_TIM_2(a VARCHAR, r0 FLOAT, r1 FLOAT, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VC_TIM_2(a TIME, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VC_TIM_3(a VARCHAR, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VC_TIM_3(a TIME, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VC_TIM_4(a VARCHAR, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 FLOAT, r4"
            + " NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VC_TIM_4(a TIME, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_BOO_VC_1(a BOOLEAN, r0 FLOAT, r1 NUMBER, r2 NUMBER, r3 NUMBER,"
            + " r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_BOO_VC_1(a VARCHAR, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_BOO_VC_2(a BOOLEAN, r0 FLOAT, r1 FLOAT, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_BOO_VC_2(a VARCHAR, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_BOO_VT_1(a BOOLEAN, r0 FLOAT, r1 NUMBER, r2 NUMBER, r3 NUMBER,"
            + " r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_BOO_VT_1(a VARIANT, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_BOO_VT_2(a BOOLEAN, r0 FLOAT, r1 FLOAT, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_BOO_VT_2(a VARIANT, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_DAT_TS_1(a DATE, r0 FLOAT, r1 NUMBER, r2 NUMBER, r3 NUMBER, r4"
            + " NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_DAT_TS_1(a TIMESTAMP_NTZ, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_DAT_TS_2(a DATE, r0 FLOAT, r1 FLOAT, r2 NUMBER, r3 NUMBER, r4 "
            + "NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_DAT_TS_2(a TIMESTAMP_NTZ, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_DAT_VC_3(a DATE, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 NUMBER, r4 "
            + "NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_DAT_VC_3(a VARCHAR, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_DAT_VC_4(a DATE, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 FLOAT, r4 "
            + "NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_DAT_VC_4(a VARCHAR, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_DAT_VC_5(a DATE, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 FLOAT, r4 "
            + "FLOAT, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_DAT_VC_5(a VARCHAR, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_TIM_VC_3(a TIME, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 NUMBER, r4 "
            + "NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_TIM_VC_3(a VARCHAR, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_TIM_VC_4(a TIME, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 FLOAT, r4 "
            + "NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_TIM_VC_4(a VARCHAR, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_TIM_VC_5(a TIME, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 FLOAT, r4 "
            + "FLOAT, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_TIM_VC_5(a VARCHAR, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_TS_VC_3(a TIMESTAMP_NTZ, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_TS_VC_3(a VARCHAR, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 NUMBER,"
            + " r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_TS_VC_4(a TIMESTAMP_NTZ, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 "
            + "FLOAT, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_TS_VC_4(a VARCHAR, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 NUMBER,"
            + " r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_TS_VC_5(a TIMESTAMP_NTZ, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 "
            + "FLOAT, r4 FLOAT, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_TS_VC_5(a VARCHAR, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 NUMBER,"
            + " r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VT_ARR_1(a VARIANT, r0 FLOAT, r1 NUMBER, r2 NUMBER, r3 NUMBER,"
            + " r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VT_ARR_1(a ARRAY, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VT_ARR_2(a VARIANT, r0 FLOAT, r1 FLOAT, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VT_ARR_2(a ARRAY, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VT_OBJ_1(a VARIANT, r0 FLOAT, r1 NUMBER, r2 NUMBER, r3 NUMBER,"
            + " r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VT_OBJ_1(a OBJECT, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 NUMBER,"
            + " r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VT_OBJ_2(a VARIANT, r0 FLOAT, r1 FLOAT, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VT_OBJ_2(a OBJECT, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 NUMBER,"
            + " r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VT_BOO_1(a VARIANT, r0 FLOAT, r1 NUMBER, r2 NUMBER, r3 NUMBER,"
            + " r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VT_BOO_1(a BOOLEAN, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VT_BOO_2(a VARIANT, r0 FLOAT, r1 FLOAT, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VT_BOO_2(a BOOLEAN, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VT_VC_1(a VARIANT, r0 FLOAT, r1 NUMBER, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VT_VC_1(a VARCHAR, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 NUMBER,"
            + " r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VT_VC_2(a VARIANT, r0 FLOAT, r1 FLOAT, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VT_VC_2(a VARCHAR, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 NUMBER,"
            + " r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VT_DAT_1(a VARIANT, r0 FLOAT, r1 NUMBER, r2 NUMBER, r3 NUMBER,"
            + " r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VT_DAT_1(a DATE, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VT_DAT_2(a VARIANT, r0 FLOAT, r1 FLOAT, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VT_DAT_2(a DATE, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VT_DAT_3(a VARIANT, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VT_DAT_3(a DATE, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VT_TIM_1(a VARIANT, r0 FLOAT, r1 NUMBER, r2 NUMBER, r3 NUMBER,"
            + " r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VT_TIM_1(a TIME, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VT_TIM_2(a VARIANT, r0 FLOAT, r1 FLOAT, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VT_TIM_2(a TIME, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VT_TIM_3(a VARIANT, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VT_TIM_3(a TIME, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VT_TS_1(a VARIANT, r0 FLOAT, r1 NUMBER, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VT_TS_1(a TIMESTAMP_NTZ, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VT_TS_2(a VARIANT, r0 FLOAT, r1 FLOAT, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VT_TS_2(a TIMESTAMP_NTZ, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VT_TS_3(a VARIANT, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 NUMBER, r4"
            + " NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VT_TS_3(a TIMESTAMP_NTZ, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_ARR_VT_1(a ARRAY, r0 FLOAT, r1 NUMBER, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_ARR_VT_1(a VARIANT, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_ARR_VT_2(a ARRAY, r0 FLOAT, r1 FLOAT, r2 NUMBER, r3 NUMBER, r4"
            + " NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_ARR_VT_2(a VARIANT, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_OBJ_VT_1(a OBJECT, r0 FLOAT, r1 NUMBER, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_OBJ_VT_1(a VARIANT, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_OBJ_VT_2(a OBJECT, r0 FLOAT, r1 FLOAT, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_OBJ_VT_2(a VARIANT, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR AS $$'B'$$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_DAT_TS_1(a DATE, r0 FLOAT, r1 NUMBER, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_DAT_TS_1(a TIMESTAMP_NTZ, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3"
            + " NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_DAT_TS_2(a DATE, r0 FLOAT, r1 FLOAT, r2 NUMBER, r3 NUMBER, r4"
            + " NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_DAT_TS_2(a TIMESTAMP_NTZ, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3"
            + " NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_DAT_VC_3(a DATE, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 NUMBER, r4 "
            + "NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_DAT_VC_3(a VARCHAR, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_DAT_VC_4(a DATE, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 FLOAT, r4 "
            + "NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_DAT_VC_4(a VARCHAR, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_DAT_VC_5(a DATE, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 FLOAT, r4 "
            + "FLOAT, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_DAT_VC_5(a VARCHAR, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_TS_VC_3(a TIMESTAMP_NTZ, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_TS_VC_3(a VARCHAR, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_TS_VC_4(a TIMESTAMP_NTZ, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 "
            + "FLOAT, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_TS_VC_4(a VARCHAR, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_TS_VC_5(a TIMESTAMP_NTZ, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 "
            + "FLOAT, r4 FLOAT, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_TS_VC_5(a VARCHAR, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_VT_ARR_1(a VARIANT, r0 FLOAT, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_VT_ARR_1(a ARRAY, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 NUMBER,"
            + " r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_VT_ARR_2(a VARIANT, r0 FLOAT, r1 FLOAT, r2 NUMBER, r3 NUMBER,"
            + " r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_VT_ARR_2(a ARRAY, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 NUMBER,"
            + " r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_VT_OBJ_1(a VARIANT, r0 FLOAT, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_VT_OBJ_1(a OBJECT, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_VT_OBJ_2(a VARIANT, r0 FLOAT, r1 FLOAT, r2 NUMBER, r3 NUMBER,"
            + " r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_VT_OBJ_2(a OBJECT, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_VT_BOO_1(a VARIANT, r0 FLOAT, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_VT_BOO_1(a BOOLEAN, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_VT_BOO_2(a VARIANT, r0 FLOAT, r1 FLOAT, r2 NUMBER, r3 NUMBER,"
            + " r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_VT_BOO_2(a BOOLEAN, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_VT_VC_1(a VARIANT, r0 FLOAT, r1 NUMBER, r2 NUMBER, r3 NUMBER,"
            + " r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_VT_VC_1(a VARCHAR, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_VT_VC_2(a VARIANT, r0 FLOAT, r1 FLOAT, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_VT_VC_2(a VARCHAR, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_VT_DAT_1(a VARIANT, r0 FLOAT, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_VT_DAT_1(a DATE, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_VT_DAT_2(a VARIANT, r0 FLOAT, r1 FLOAT, r2 NUMBER, r3 NUMBER,"
            + " r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_VT_DAT_2(a DATE, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_VT_DAT_3(a VARIANT, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_VT_DAT_3(a DATE, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_VT_TIM_1(a VARIANT, r0 FLOAT, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_VT_TIM_1(a TIME, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_VT_TIM_2(a VARIANT, r0 FLOAT, r1 FLOAT, r2 NUMBER, r3 NUMBER,"
            + " r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_VT_TIM_2(a TIME, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_VT_TIM_3(a VARIANT, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_VT_TIM_3(a TIME, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_VT_TS_1(a VARIANT, r0 FLOAT, r1 NUMBER, r2 NUMBER, r3 NUMBER,"
            + " r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_VT_TS_1(a TIMESTAMP_NTZ, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_VT_TS_2(a VARIANT, r0 FLOAT, r1 FLOAT, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_VT_TS_2(a TIMESTAMP_NTZ, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_VT_TS_3(a VARIANT, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_VT_TS_3(a TIMESTAMP_NTZ, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_ARR_VT_1(a ARRAY, r0 FLOAT, r1 NUMBER, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_ARR_VT_1(a VARIANT, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_ARR_VT_2(a ARRAY, r0 FLOAT, r1 FLOAT, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_ARR_VT_2(a VARIANT, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_OBJ_VT_1(a OBJECT, r0 FLOAT, r1 NUMBER, r2 NUMBER, r3 NUMBER,"
            + " r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_OBJ_VT_1(a VARIANT, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_OBJ_VT_2(a OBJECT, r0 FLOAT, r1 FLOAT, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_OBJ_VT_2(a VARIANT, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VT_NUM_40(a VARIANT, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 FLOAT, "
            + "r4 FLOAT, r5 FLOAT, r6 FLOAT, r7 FLOAT, r8 FLOAT, r9 FLOAT, r10 FLOAT, r11 FLOAT, r12 FLOAT,"
            + " r13 FLOAT, r14 FLOAT, r15 FLOAT, r16 FLOAT, r17 FLOAT, r18 FLOAT, r19 FLOAT, r20 FLOAT, r21"
            + " FLOAT, r22 FLOAT, r23 FLOAT, r24 FLOAT, r25 FLOAT, r26 FLOAT, r27 FLOAT, r28 FLOAT, r29 "
            + "FLOAT, r30 FLOAT, r31 FLOAT, r32 FLOAT, r33 FLOAT, r34 FLOAT, r35 FLOAT, r36 FLOAT, r37 "
            + "FLOAT, r38 FLOAT, r39 FLOAT) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VT_NUM_40(a NUMBER, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER, r8 NUMBER, r9 NUMBER, r10 NUMBER, r11 "
            + "NUMBER, r12 NUMBER, r13 NUMBER, r14 NUMBER, r15 NUMBER, r16 NUMBER, r17 NUMBER, r18 NUMBER, "
            + "r19 NUMBER, r20 NUMBER, r21 NUMBER, r22 NUMBER, r23 NUMBER, r24 NUMBER, r25 NUMBER, r26 "
            + "NUMBER, r27 NUMBER, r28 NUMBER, r29 NUMBER, r30 NUMBER, r31 NUMBER, r32 NUMBER, r33 NUMBER, "
            + "r34 NUMBER, r35 NUMBER, r36 NUMBER, r37 NUMBER, r38 NUMBER, r39 NUMBER) RETURNS VARCHAR AS "
            + "$$'B'$$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_VT_NUM_40(a VARIANT, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 FLOAT, "
            + "r4 FLOAT, r5 FLOAT, r6 FLOAT, r7 FLOAT, r8 FLOAT, r9 FLOAT, r10 FLOAT, r11 FLOAT, r12 FLOAT,"
            + " r13 FLOAT, r14 FLOAT, r15 FLOAT, r16 FLOAT, r17 FLOAT, r18 FLOAT, r19 FLOAT, r20 FLOAT, r21"
            + " FLOAT, r22 FLOAT, r23 FLOAT, r24 FLOAT, r25 FLOAT, r26 FLOAT, r27 FLOAT, r28 FLOAT, r29 "
            + "FLOAT, r30 FLOAT, r31 FLOAT, r32 FLOAT, r33 FLOAT, r34 FLOAT, r35 FLOAT, r36 FLOAT, r37 "
            + "FLOAT, r38 FLOAT, r39 FLOAT) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_VT_NUM_40(a NUMBER, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER, r8 NUMBER, r9 NUMBER, r10 NUMBER, r11 "
            + "NUMBER, r12 NUMBER, r13 NUMBER, r14 NUMBER, r15 NUMBER, r16 NUMBER, r17 NUMBER, r18 NUMBER, "
            + "r19 NUMBER, r20 NUMBER, r21 NUMBER, r22 NUMBER, r23 NUMBER, r24 NUMBER, r25 NUMBER, r26 "
            + "NUMBER, r27 NUMBER, r28 NUMBER, r29 NUMBER, r30 NUMBER, r31 NUMBER, r32 NUMBER, r33 NUMBER, "
            + "r34 NUMBER, r35 NUMBER, r36 NUMBER, r37 NUMBER, r38 NUMBER, r39 NUMBER) RETURNS VARCHAR "
            + "LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VT_FLO_40(a VARIANT, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 FLOAT, "
            + "r4 FLOAT, r5 FLOAT, r6 FLOAT, r7 FLOAT, r8 FLOAT, r9 FLOAT, r10 FLOAT, r11 FLOAT, r12 FLOAT,"
            + " r13 FLOAT, r14 FLOAT, r15 FLOAT, r16 FLOAT, r17 FLOAT, r18 FLOAT, r19 FLOAT, r20 FLOAT, r21"
            + " FLOAT, r22 FLOAT, r23 FLOAT, r24 FLOAT, r25 FLOAT, r26 FLOAT, r27 FLOAT, r28 FLOAT, r29 "
            + "FLOAT, r30 FLOAT, r31 FLOAT, r32 FLOAT, r33 FLOAT, r34 FLOAT, r35 FLOAT, r36 FLOAT, r37 "
            + "FLOAT, r38 FLOAT, r39 FLOAT) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_VT_FLO_40(a FLOAT, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 NUMBER,"
            + " r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER, r8 NUMBER, r9 NUMBER, r10 NUMBER, r11 NUMBER, "
            + "r12 NUMBER, r13 NUMBER, r14 NUMBER, r15 NUMBER, r16 NUMBER, r17 NUMBER, r18 NUMBER, r19 "
            + "NUMBER, r20 NUMBER, r21 NUMBER, r22 NUMBER, r23 NUMBER, r24 NUMBER, r25 NUMBER, r26 NUMBER, "
            + "r27 NUMBER, r28 NUMBER, r29 NUMBER, r30 NUMBER, r31 NUMBER, r32 NUMBER, r33 NUMBER, r34 "
            + "NUMBER, r35 NUMBER, r36 NUMBER, r37 NUMBER, r38 NUMBER, r39 NUMBER) RETURNS VARCHAR AS "
            + "$$'B'$$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_VT_FLO_40(a VARIANT, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 FLOAT, "
            + "r4 FLOAT, r5 FLOAT, r6 FLOAT, r7 FLOAT, r8 FLOAT, r9 FLOAT, r10 FLOAT, r11 FLOAT, r12 FLOAT,"
            + " r13 FLOAT, r14 FLOAT, r15 FLOAT, r16 FLOAT, r17 FLOAT, r18 FLOAT, r19 FLOAT, r20 FLOAT, r21"
            + " FLOAT, r22 FLOAT, r23 FLOAT, r24 FLOAT, r25 FLOAT, r26 FLOAT, r27 FLOAT, r28 FLOAT, r29 "
            + "FLOAT, r30 FLOAT, r31 FLOAT, r32 FLOAT, r33 FLOAT, r34 FLOAT, r35 FLOAT, r36 FLOAT, r37 "
            + "FLOAT, r38 FLOAT, r39 FLOAT) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_VT_FLO_40(a FLOAT, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER, r8 NUMBER, r9 NUMBER, r10 NUMBER, r11 "
            + "NUMBER, r12 NUMBER, r13 NUMBER, r14 NUMBER, r15 NUMBER, r16 NUMBER, r17 NUMBER, r18 NUMBER, "
            + "r19 NUMBER, r20 NUMBER, r21 NUMBER, r22 NUMBER, r23 NUMBER, r24 NUMBER, r25 NUMBER, r26 "
            + "NUMBER, r27 NUMBER, r28 NUMBER, r29 NUMBER, r30 NUMBER, r31 NUMBER, r32 NUMBER, r33 NUMBER, "
            + "r34 NUMBER, r35 NUMBER, r36 NUMBER, r37 NUMBER, r38 NUMBER, r39 NUMBER) RETURNS VARCHAR "
            + "LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_FLO_NUM_40(a FLOAT, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 FLOAT, r4"
            + " FLOAT, r5 FLOAT, r6 FLOAT, r7 FLOAT, r8 FLOAT, r9 FLOAT, r10 FLOAT, r11 FLOAT, r12 FLOAT, "
            + "r13 FLOAT, r14 FLOAT, r15 FLOAT, r16 FLOAT, r17 FLOAT, r18 FLOAT, r19 FLOAT, r20 FLOAT, r21 "
            + "FLOAT, r22 FLOAT, r23 FLOAT, r24 FLOAT, r25 FLOAT, r26 FLOAT, r27 FLOAT, r28 FLOAT, r29 "
            + "FLOAT, r30 FLOAT, r31 FLOAT, r32 FLOAT, r33 FLOAT, r34 FLOAT, r35 FLOAT, r36 FLOAT, r37 "
            + "FLOAT, r38 FLOAT, r39 FLOAT) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_FLO_NUM_40(a NUMBER, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER, r8 NUMBER, r9 NUMBER, r10 NUMBER, r11 "
            + "NUMBER, r12 NUMBER, r13 NUMBER, r14 NUMBER, r15 NUMBER, r16 NUMBER, r17 NUMBER, r18 NUMBER, "
            + "r19 NUMBER, r20 NUMBER, r21 NUMBER, r22 NUMBER, r23 NUMBER, r24 NUMBER, r25 NUMBER, r26 "
            + "NUMBER, r27 NUMBER, r28 NUMBER, r29 NUMBER, r30 NUMBER, r31 NUMBER, r32 NUMBER, r33 NUMBER, "
            + "r34 NUMBER, r35 NUMBER, r36 NUMBER, r37 NUMBER, r38 NUMBER, r39 NUMBER) RETURNS VARCHAR AS "
            + "$$'B'$$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_FLO_NUM_40(a FLOAT, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 FLOAT, "
            + "r4 FLOAT, r5 FLOAT, r6 FLOAT, r7 FLOAT, r8 FLOAT, r9 FLOAT, r10 FLOAT, r11 FLOAT, r12 FLOAT,"
            + " r13 FLOAT, r14 FLOAT, r15 FLOAT, r16 FLOAT, r17 FLOAT, r18 FLOAT, r19 FLOAT, r20 FLOAT, r21"
            + " FLOAT, r22 FLOAT, r23 FLOAT, r24 FLOAT, r25 FLOAT, r26 FLOAT, r27 FLOAT, r28 FLOAT, r29 "
            + "FLOAT, r30 FLOAT, r31 FLOAT, r32 FLOAT, r33 FLOAT, r34 FLOAT, r35 FLOAT, r36 FLOAT, r37 "
            + "FLOAT, r38 FLOAT, r39 FLOAT) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_FLO_NUM_40(a NUMBER, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER, r8 NUMBER, r9 NUMBER, r10 NUMBER, r11 "
            + "NUMBER, r12 NUMBER, r13 NUMBER, r14 NUMBER, r15 NUMBER, r16 NUMBER, r17 NUMBER, r18 NUMBER, "
            + "r19 NUMBER, r20 NUMBER, r21 NUMBER, r22 NUMBER, r23 NUMBER, r24 NUMBER, r25 NUMBER, r26 "
            + "NUMBER, r27 NUMBER, r28 NUMBER, r29 NUMBER, r30 NUMBER, r31 NUMBER, r32 NUMBER, r33 NUMBER, "
            + "r34 NUMBER, r35 NUMBER, r36 NUMBER, r37 NUMBER, r38 NUMBER, r39 NUMBER) RETURNS VARCHAR "
            + "LANGUAGE SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_TS_DAT_40(a TIMESTAMP_NTZ, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 "
            + "FLOAT, r4 FLOAT, r5 FLOAT, r6 FLOAT, r7 FLOAT, r8 FLOAT, r9 FLOAT, r10 FLOAT, r11 FLOAT, r12"
            + " FLOAT, r13 FLOAT, r14 FLOAT, r15 FLOAT, r16 FLOAT, r17 FLOAT, r18 FLOAT, r19 FLOAT, r20 "
            + "FLOAT, r21 FLOAT, r22 FLOAT, r23 FLOAT, r24 FLOAT, r25 FLOAT, r26 FLOAT, r27 FLOAT, r28 "
            + "FLOAT, r29 FLOAT, r30 FLOAT, r31 FLOAT, r32 FLOAT, r33 FLOAT, r34 FLOAT, r35 FLOAT, r36 "
            + "FLOAT, r37 FLOAT, r38 FLOAT, r39 FLOAT) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_TS_DAT_40(a DATE, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER, r8 NUMBER, r9 NUMBER, r10 NUMBER, r11 NUMBER, "
            + "r12 NUMBER, r13 NUMBER, r14 NUMBER, r15 NUMBER, r16 NUMBER, r17 NUMBER, r18 NUMBER, r19 "
            + "NUMBER, r20 NUMBER, r21 NUMBER, r22 NUMBER, r23 NUMBER, r24 NUMBER, r25 NUMBER, r26 NUMBER, "
            + "r27 NUMBER, r28 NUMBER, r29 NUMBER, r30 NUMBER, r31 NUMBER, r32 NUMBER, r33 NUMBER, r34 "
            + "NUMBER, r35 NUMBER, r36 NUMBER, r37 NUMBER, r38 NUMBER, r39 NUMBER) RETURNS VARCHAR AS "
            + "$$'B'$$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_TS_DAT_40(a TIMESTAMP_NTZ, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 "
            + "FLOAT, r4 FLOAT, r5 FLOAT, r6 FLOAT, r7 FLOAT, r8 FLOAT, r9 FLOAT, r10 FLOAT, r11 FLOAT, r12"
            + " FLOAT, r13 FLOAT, r14 FLOAT, r15 FLOAT, r16 FLOAT, r17 FLOAT, r18 FLOAT, r19 FLOAT, r20 "
            + "FLOAT, r21 FLOAT, r22 FLOAT, r23 FLOAT, r24 FLOAT, r25 FLOAT, r26 FLOAT, r27 FLOAT, r28 "
            + "FLOAT, r29 FLOAT, r30 FLOAT, r31 FLOAT, r32 FLOAT, r33 FLOAT, r34 FLOAT, r35 FLOAT, r36 "
            + "FLOAT, r37 FLOAT, r38 FLOAT, r39 FLOAT) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A';"
            + " END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_TS_DAT_40(a DATE, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 NUMBER,"
            + " r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER, r8 NUMBER, r9 NUMBER, r10 NUMBER, r11 NUMBER, "
            + "r12 NUMBER, r13 NUMBER, r14 NUMBER, r15 NUMBER, r16 NUMBER, r17 NUMBER, r18 NUMBER, r19 "
            + "NUMBER, r20 NUMBER, r21 NUMBER, r22 NUMBER, r23 NUMBER, r24 NUMBER, r25 NUMBER, r26 NUMBER, "
            + "r27 NUMBER, r28 NUMBER, r29 NUMBER, r30 NUMBER, r31 NUMBER, r32 NUMBER, r33 NUMBER, r34 "
            + "NUMBER, r35 NUMBER, r36 NUMBER, r37 NUMBER, r38 NUMBER, r39 NUMBER) RETURNS VARCHAR LANGUAGE"
            + " SQL AS $$ BEGIN RETURN 'B'; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_TS_TIM_40(a TIMESTAMP_NTZ, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 "
            + "FLOAT, r4 FLOAT, r5 FLOAT, r6 FLOAT, r7 FLOAT, r8 FLOAT, r9 FLOAT, r10 FLOAT, r11 FLOAT, r12"
            + " FLOAT, r13 FLOAT, r14 FLOAT, r15 FLOAT, r16 FLOAT, r17 FLOAT, r18 FLOAT, r19 FLOAT, r20 "
            + "FLOAT, r21 FLOAT, r22 FLOAT, r23 FLOAT, r24 FLOAT, r25 FLOAT, r26 FLOAT, r27 FLOAT, r28 "
            + "FLOAT, r29 FLOAT, r30 FLOAT, r31 FLOAT, r32 FLOAT, r33 FLOAT, r34 FLOAT, r35 FLOAT, r36 "
            + "FLOAT, r37 FLOAT, r38 FLOAT, r39 FLOAT) RETURNS VARCHAR AS $$'A'$$");
        engine.execute("CREATE OR REPLACE FUNCTION uf_TS_TIM_40(a TIME, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 NUMBER, "
            + "r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER, r8 NUMBER, r9 NUMBER, r10 NUMBER, r11 NUMBER, "
            + "r12 NUMBER, r13 NUMBER, r14 NUMBER, r15 NUMBER, r16 NUMBER, r17 NUMBER, r18 NUMBER, r19 "
            + "NUMBER, r20 NUMBER, r21 NUMBER, r22 NUMBER, r23 NUMBER, r24 NUMBER, r25 NUMBER, r26 NUMBER, "
            + "r27 NUMBER, r28 NUMBER, r29 NUMBER, r30 NUMBER, r31 NUMBER, r32 NUMBER, r33 NUMBER, r34 "
            + "NUMBER, r35 NUMBER, r36 NUMBER, r37 NUMBER, r38 NUMBER, r39 NUMBER) RETURNS VARCHAR AS "
            + "$$'B'$$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_TS_TIM_40(a TIMESTAMP_NTZ, r0 FLOAT, r1 FLOAT, r2 FLOAT, r3 "
            + "FLOAT, r4 FLOAT, r5 FLOAT, r6 FLOAT, r7 FLOAT, r8 FLOAT, r9 FLOAT, r10 FLOAT, r11 FLOAT, r12"
            + " FLOAT, r13 FLOAT, r14 FLOAT, r15 FLOAT, r16 FLOAT, r17 FLOAT, r18 FLOAT, r19 FLOAT, r20 "
            + "FLOAT, r21 FLOAT, r22 FLOAT, r23 FLOAT, r24 FLOAT, r25 FLOAT, r26 FLOAT, r27 FLOAT, r28 "
            + "FLOAT, r29 FLOAT, r30 FLOAT, r31 FLOAT, r32 FLOAT, r33 FLOAT, r34 FLOAT, r35 FLOAT, r36 "
            + "FLOAT, r37 FLOAT, r38 FLOAT, r39 FLOAT) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A';"
            + " END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE up_TS_TIM_40(a TIME, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 NUMBER,"
            + " r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER, r8 NUMBER, r9 NUMBER, r10 NUMBER, r11 NUMBER, "
            + "r12 NUMBER, r13 NUMBER, r14 NUMBER, r15 NUMBER, r16 NUMBER, r17 NUMBER, r18 NUMBER, r19 "
            + "NUMBER, r20 NUMBER, r21 NUMBER, r22 NUMBER, r23 NUMBER, r24 NUMBER, r25 NUMBER, r26 NUMBER, "
            + "r27 NUMBER, r28 NUMBER, r29 NUMBER, r30 NUMBER, r31 NUMBER, r32 NUMBER, r33 NUMBER, r34 "
            + "NUMBER, r35 NUMBER, r36 NUMBER, r37 NUMBER, r38 NUMBER, r39 NUMBER) RETURNS VARCHAR LANGUAGE"
            + " SQL AS $$ BEGIN RETURN 'B'; END $$");
        assertCells(new String[][] {
            {"SELECT uf_NUM_BOO_1(a => CAST(NULL AS NUMBER), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "A"},
            {"SELECT uf_NUM_BOO_2(a => CAST(NULL AS NUMBER), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "REFUSED SQL compilation error: | More than one function of UF_NUM_BOO_2 are resolved in "
                + "prepare state."},
            {"SELECT uf_NUM_BOO_3(a => CAST(NULL AS NUMBER), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "B"},
            {"SELECT uf_NUM_VT_1(a => CAST(NULL AS NUMBER), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "A"},
            {"SELECT uf_NUM_VT_2(a => CAST(NULL AS NUMBER), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "REFUSED SQL compilation error: | More than one function of UF_NUM_VT_2 are resolved in "
                + "prepare state."},
            {"SELECT uf_NUM_VT_3(a => CAST(NULL AS NUMBER), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "B"},
            {"SELECT uf_NUM_VC_3(a => CAST(NULL AS NUMBER), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "A"},
            {"SELECT uf_NUM_VC_4(a => CAST(NULL AS NUMBER), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "REFUSED SQL compilation error: | More than one function of UF_NUM_VC_4 are resolved in "
                + "prepare state."},
            {"SELECT uf_NUM_VC_5(a => CAST(NULL AS NUMBER), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "B"},
            {"SELECT uf_FLO_BOO_1(a => CAST(NULL AS FLOAT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "A"},
            {"SELECT uf_FLO_BOO_2(a => CAST(NULL AS FLOAT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "REFUSED SQL compilation error: | More than one function of UF_FLO_BOO_2 are resolved in "
                + "prepare state."},
            {"SELECT uf_FLO_BOO_3(a => CAST(NULL AS FLOAT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "B"},
            {"SELECT uf_FLO_VT_1(a => CAST(NULL AS FLOAT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5"
                + " => 1)",
                "A"},
            {"SELECT uf_FLO_VT_2(a => CAST(NULL AS FLOAT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5"
                + " => 1)",
                "REFUSED SQL compilation error: | More than one function of UF_FLO_VT_2 are resolved in "
                + "prepare state."},
            {"SELECT uf_FLO_VT_3(a => CAST(NULL AS FLOAT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5"
                + " => 1)",
                "B"},
            {"SELECT uf_FLO_VC_3(a => CAST(NULL AS FLOAT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5"
                + " => 1)",
                "A"},
            {"SELECT uf_FLO_VC_4(a => CAST(NULL AS FLOAT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5"
                + " => 1)",
                "REFUSED SQL compilation error: | More than one function of UF_FLO_VC_4 are resolved in "
                + "prepare state."},
            {"SELECT uf_FLO_VC_5(a => CAST(NULL AS FLOAT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5"
                + " => 1)",
                "B"},
            {"SELECT uf_VC_BOO_1(a => CAST(NULL AS VARCHAR), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "REFUSED SQL compilation error: | More than one function of UF_VC_BOO_1 are resolved in "
                + "prepare state."},
            {"SELECT uf_VC_BOO_2(a => CAST(NULL AS VARCHAR), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "B"},
            {"SELECT uf_VC_FLO_1(a => CAST(NULL AS VARCHAR), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "A"},
            {"SELECT uf_VC_FLO_2(a => CAST(NULL AS VARCHAR), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "REFUSED SQL compilation error: | More than one function of UF_VC_FLO_2 are resolved in "
                + "prepare state."},
            {"SELECT uf_VC_FLO_3(a => CAST(NULL AS VARCHAR), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "B"},
            {"SELECT uf_VC_DAT_1(a => CAST(NULL AS VARCHAR), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "A"},
            {"SELECT uf_VC_DAT_2(a => CAST(NULL AS VARCHAR), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "REFUSED SQL compilation error: | More than one function of UF_VC_DAT_2 are resolved in "
                + "prepare state."},
            {"SELECT uf_VC_DAT_3(a => CAST(NULL AS VARCHAR), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "B"},
            {"SELECT uf_VC_TS_1(a => CAST(NULL AS VARCHAR), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "A"},
            {"SELECT uf_VC_TS_2(a => CAST(NULL AS VARCHAR), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "REFUSED SQL compilation error: | More than one function of UF_VC_TS_2 are resolved in "
                + "prepare state."},
            {"SELECT uf_VC_TS_3(a => CAST(NULL AS VARCHAR), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "B"},
            {"SELECT uf_VC_NUM_2(a => CAST(NULL AS VARCHAR), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "A"},
            {"SELECT uf_VC_NUM_3(a => CAST(NULL AS VARCHAR), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "REFUSED SQL compilation error: | More than one function of UF_VC_NUM_3 are resolved in "
                + "prepare state."},
            {"SELECT uf_VC_NUM_4(a => CAST(NULL AS VARCHAR), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "B"},
            {"SELECT uf_VC_TIM_2(a => CAST(NULL AS VARCHAR), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "A"},
            {"SELECT uf_VC_TIM_3(a => CAST(NULL AS VARCHAR), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "REFUSED SQL compilation error: | More than one function of UF_VC_TIM_3 are resolved in "
                + "prepare state."},
            {"SELECT uf_VC_TIM_4(a => CAST(NULL AS VARCHAR), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "B"},
            {"SELECT uf_BOO_VC_1(a => CAST(NULL AS BOOLEAN), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "REFUSED SQL compilation error: | More than one function of UF_BOO_VC_1 are resolved in "
                + "prepare state."},
            {"SELECT uf_BOO_VC_2(a => CAST(NULL AS BOOLEAN), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "B"},
            {"SELECT uf_BOO_VT_1(a => CAST(NULL AS BOOLEAN), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "REFUSED SQL compilation error: | More than one function of UF_BOO_VT_1 are resolved in "
                + "prepare state."},
            {"SELECT uf_BOO_VT_2(a => CAST(NULL AS BOOLEAN), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "B"},
            {"SELECT uf_DAT_TS_1(a => CAST(NULL AS DATE), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5 "
                + "=> 1)",
                "REFUSED SQL compilation error: | More than one function of UF_DAT_TS_1 are resolved in "
                + "prepare state."},
            {"SELECT uf_DAT_TS_2(a => CAST(NULL AS DATE), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5 "
                + "=> 1)",
                "B"},
            {"SELECT uf_DAT_VC_3(a => CAST(NULL AS DATE), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5 "
                + "=> 1)",
                "A"},
            {"SELECT uf_DAT_VC_4(a => CAST(NULL AS DATE), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5 "
                + "=> 1)",
                "REFUSED SQL compilation error: | More than one function of UF_DAT_VC_4 are resolved in "
                + "prepare state."},
            {"SELECT uf_DAT_VC_5(a => CAST(NULL AS DATE), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5 "
                + "=> 1)",
                "B"},
            {"SELECT uf_TIM_VC_3(a => CAST(NULL AS TIME), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5 "
                + "=> 1)",
                "A"},
            {"SELECT uf_TIM_VC_4(a => CAST(NULL AS TIME), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5 "
                + "=> 1)",
                "REFUSED SQL compilation error: | More than one function of UF_TIM_VC_4 are resolved in "
                + "prepare state."},
            {"SELECT uf_TIM_VC_5(a => CAST(NULL AS TIME), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5 "
                + "=> 1)",
                "B"},
            {"SELECT uf_TS_VC_3(a => CAST(NULL AS TIMESTAMP_NTZ), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 "
                + "=> 1, r5 => 1)",
                "A"},
            {"SELECT uf_TS_VC_4(a => CAST(NULL AS TIMESTAMP_NTZ), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 "
                + "=> 1, r5 => 1)",
                "REFUSED SQL compilation error: | More than one function of UF_TS_VC_4 are resolved in "
                + "prepare state."},
            {"SELECT uf_TS_VC_5(a => CAST(NULL AS TIMESTAMP_NTZ), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 "
                + "=> 1, r5 => 1)",
                "B"},
            {"SELECT uf_VT_ARR_1(a => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "REFUSED SQL compilation error: | More than one function of UF_VT_ARR_1 are resolved in "
                + "prepare state."},
            {"SELECT uf_VT_ARR_2(a => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "B"},
            {"SELECT uf_VT_OBJ_1(a => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "REFUSED SQL compilation error: | More than one function of UF_VT_OBJ_1 are resolved in "
                + "prepare state."},
            {"SELECT uf_VT_OBJ_2(a => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "B"},
            {"SELECT uf_VT_BOO_1(a => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "REFUSED SQL compilation error: | More than one function of UF_VT_BOO_1 are resolved in "
                + "prepare state."},
            {"SELECT uf_VT_BOO_2(a => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "B"},
            {"SELECT uf_VT_VC_1(a => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "REFUSED SQL compilation error: | More than one function of UF_VT_VC_1 are resolved in "
                + "prepare state."},
            {"SELECT uf_VT_VC_2(a => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "B"},
            {"SELECT uf_VT_DAT_1(a => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "A"},
            {"SELECT uf_VT_DAT_2(a => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "REFUSED SQL compilation error: | More than one function of UF_VT_DAT_2 are resolved in "
                + "prepare state."},
            {"SELECT uf_VT_DAT_3(a => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "B"},
            {"SELECT uf_VT_TIM_1(a => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "A"},
            {"SELECT uf_VT_TIM_2(a => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "REFUSED SQL compilation error: | More than one function of UF_VT_TIM_2 are resolved in "
                + "prepare state."},
            {"SELECT uf_VT_TIM_3(a => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "B"},
            {"SELECT uf_VT_TS_1(a => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "A"},
            {"SELECT uf_VT_TS_2(a => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "REFUSED SQL compilation error: | More than one function of UF_VT_TS_2 are resolved in "
                + "prepare state."},
            {"SELECT uf_VT_TS_3(a => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "B"},
            {"SELECT uf_ARR_VT_1(a => CAST(NULL AS ARRAY), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5"
                + " => 1)",
                "REFUSED SQL compilation error: | More than one function of UF_ARR_VT_1 are resolved in "
                + "prepare state."},
            {"SELECT uf_ARR_VT_2(a => CAST(NULL AS ARRAY), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5"
                + " => 1)",
                "B"},
            {"SELECT uf_OBJ_VT_1(a => CAST(NULL AS OBJECT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "REFUSED SQL compilation error: | More than one function of UF_OBJ_VT_1 are resolved in "
                + "prepare state."},
            {"SELECT uf_OBJ_VT_2(a => CAST(NULL AS OBJECT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1)",
                "B"},
            {"CALL up_DAT_TS_1(a => CAST(NULL AS DATE), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5 =>"
                + " 1)",
                "REFUSED SQL compilation error: | More than one function of UP_DAT_TS_1 are resolved in "
                + "prepare state."},
            {"CALL up_DAT_TS_2(a => CAST(NULL AS DATE), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5 =>"
                + " 1)",
                "B"},
            {"CALL up_DAT_VC_3(a => CAST(NULL AS DATE), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5 =>"
                + " 1)",
                "A"},
            {"CALL up_DAT_VC_4(a => CAST(NULL AS DATE), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5 =>"
                + " 1)",
                "REFUSED SQL compilation error: | More than one function of UP_DAT_VC_4 are resolved in "
                + "prepare state."},
            {"CALL up_DAT_VC_5(a => CAST(NULL AS DATE), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5 =>"
                + " 1)",
                "B"},
            {"CALL up_TS_VC_3(a => CAST(NULL AS TIMESTAMP_NTZ), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => "
                + "1, r5 => 1)",
                "A"},
            {"CALL up_TS_VC_4(a => CAST(NULL AS TIMESTAMP_NTZ), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => "
                + "1, r5 => 1)",
                "REFUSED SQL compilation error: | More than one function of UP_TS_VC_4 are resolved in "
                + "prepare state."},
            {"CALL up_TS_VC_5(a => CAST(NULL AS TIMESTAMP_NTZ), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => "
                + "1, r5 => 1)",
                "B"},
            {"CALL up_VT_ARR_1(a => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5"
                + " => 1)",
                "REFUSED SQL compilation error: | More than one function of UP_VT_ARR_1 are resolved in "
                + "prepare state."},
            {"CALL up_VT_ARR_2(a => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5"
                + " => 1)",
                "B"},
            {"CALL up_VT_OBJ_1(a => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5"
                + " => 1)",
                "REFUSED SQL compilation error: | More than one function of UP_VT_OBJ_1 are resolved in "
                + "prepare state."},
            {"CALL up_VT_OBJ_2(a => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5"
                + " => 1)",
                "B"},
            {"CALL up_VT_BOO_1(a => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5"
                + " => 1)",
                "REFUSED SQL compilation error: | More than one function of UP_VT_BOO_1 are resolved in "
                + "prepare state."},
            {"CALL up_VT_BOO_2(a => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5"
                + " => 1)",
                "B"},
            {"CALL up_VT_VC_1(a => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5 "
                + "=> 1)",
                "REFUSED SQL compilation error: | More than one function of UP_VT_VC_1 are resolved in "
                + "prepare state."},
            {"CALL up_VT_VC_2(a => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5 "
                + "=> 1)",
                "B"},
            {"CALL up_VT_DAT_1(a => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5"
                + " => 1)",
                "A"},
            {"CALL up_VT_DAT_2(a => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5"
                + " => 1)",
                "REFUSED SQL compilation error: | More than one function of UP_VT_DAT_2 are resolved in "
                + "prepare state."},
            {"CALL up_VT_DAT_3(a => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5"
                + " => 1)",
                "B"},
            {"CALL up_VT_TIM_1(a => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5"
                + " => 1)",
                "A"},
            {"CALL up_VT_TIM_2(a => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5"
                + " => 1)",
                "REFUSED SQL compilation error: | More than one function of UP_VT_TIM_2 are resolved in "
                + "prepare state."},
            {"CALL up_VT_TIM_3(a => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5"
                + " => 1)",
                "B"},
            {"CALL up_VT_TS_1(a => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5 "
                + "=> 1)",
                "A"},
            {"CALL up_VT_TS_2(a => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5 "
                + "=> 1)",
                "REFUSED SQL compilation error: | More than one function of UP_VT_TS_2 are resolved in "
                + "prepare state."},
            {"CALL up_VT_TS_3(a => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5 "
                + "=> 1)",
                "B"},
            {"CALL up_ARR_VT_1(a => CAST(NULL AS ARRAY), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5 "
                + "=> 1)",
                "REFUSED SQL compilation error: | More than one function of UP_ARR_VT_1 are resolved in "
                + "prepare state."},
            {"CALL up_ARR_VT_2(a => CAST(NULL AS ARRAY), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5 "
                + "=> 1)",
                "B"},
            {"CALL up_OBJ_VT_1(a => CAST(NULL AS OBJECT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5 "
                + "=> 1)",
                "REFUSED SQL compilation error: | More than one function of UP_OBJ_VT_1 are resolved in "
                + "prepare state."},
            {"CALL up_OBJ_VT_2(a => CAST(NULL AS OBJECT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5 "
                + "=> 1)",
                "B"},
            {"SELECT uf_VT_NUM_40(a => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1,"
                + " r5 => 1, r6 => 1, r7 => 1, r8 => 1, r9 => 1, r10 => 1, r11 => 1, r12 => 1, r13 => 1, r14 =>"
                + " 1, r15 => 1, r16 => 1, r17 => 1, r18 => 1, r19 => 1, r20 => 1, r21 => 1, r22 => 1, r23 => "
                + "1, r24 => 1, r25 => 1, r26 => 1, r27 => 1, r28 => 1, r29 => 1, r30 => 1, r31 => 1, r32 => 1,"
                + " r33 => 1, r34 => 1, r35 => 1, r36 => 1, r37 => 1, r38 => 1, r39 => 1)",
                "A"},
            {"CALL up_VT_NUM_40(a => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1, r6 => 1, r7 => 1, r8 => 1, r9 => 1, r10 => 1, r11 => 1, r12 => 1, r13 => 1, r14 => "
                + "1, r15 => 1, r16 => 1, r17 => 1, r18 => 1, r19 => 1, r20 => 1, r21 => 1, r22 => 1, r23 => 1,"
                + " r24 => 1, r25 => 1, r26 => 1, r27 => 1, r28 => 1, r29 => 1, r30 => 1, r31 => 1, r32 => 1, "
                + "r33 => 1, r34 => 1, r35 => 1, r36 => 1, r37 => 1, r38 => 1, r39 => 1)",
                "A"},
            {"SELECT uf_VT_FLO_40(a => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1,"
                + " r5 => 1, r6 => 1, r7 => 1, r8 => 1, r9 => 1, r10 => 1, r11 => 1, r12 => 1, r13 => 1, r14 =>"
                + " 1, r15 => 1, r16 => 1, r17 => 1, r18 => 1, r19 => 1, r20 => 1, r21 => 1, r22 => 1, r23 => "
                + "1, r24 => 1, r25 => 1, r26 => 1, r27 => 1, r28 => 1, r29 => 1, r30 => 1, r31 => 1, r32 => 1,"
                + " r33 => 1, r34 => 1, r35 => 1, r36 => 1, r37 => 1, r38 => 1, r39 => 1)",
                "A"},
            {"CALL up_VT_FLO_40(a => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1, r6 => 1, r7 => 1, r8 => 1, r9 => 1, r10 => 1, r11 => 1, r12 => 1, r13 => 1, r14 => "
                + "1, r15 => 1, r16 => 1, r17 => 1, r18 => 1, r19 => 1, r20 => 1, r21 => 1, r22 => 1, r23 => 1,"
                + " r24 => 1, r25 => 1, r26 => 1, r27 => 1, r28 => 1, r29 => 1, r30 => 1, r31 => 1, r32 => 1, "
                + "r33 => 1, r34 => 1, r35 => 1, r36 => 1, r37 => 1, r38 => 1, r39 => 1)",
                "A"},
            {"SELECT uf_FLO_NUM_40(a => CAST(NULL AS FLOAT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, "
                + "r5 => 1, r6 => 1, r7 => 1, r8 => 1, r9 => 1, r10 => 1, r11 => 1, r12 => 1, r13 => 1, r14 => "
                + "1, r15 => 1, r16 => 1, r17 => 1, r18 => 1, r19 => 1, r20 => 1, r21 => 1, r22 => 1, r23 => 1,"
                + " r24 => 1, r25 => 1, r26 => 1, r27 => 1, r28 => 1, r29 => 1, r30 => 1, r31 => 1, r32 => 1, "
                + "r33 => 1, r34 => 1, r35 => 1, r36 => 1, r37 => 1, r38 => 1, r39 => 1)",
                "A"},
            {"CALL up_FLO_NUM_40(a => CAST(NULL AS FLOAT), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5"
                + " => 1, r6 => 1, r7 => 1, r8 => 1, r9 => 1, r10 => 1, r11 => 1, r12 => 1, r13 => 1, r14 => 1,"
                + " r15 => 1, r16 => 1, r17 => 1, r18 => 1, r19 => 1, r20 => 1, r21 => 1, r22 => 1, r23 => 1, "
                + "r24 => 1, r25 => 1, r26 => 1, r27 => 1, r28 => 1, r29 => 1, r30 => 1, r31 => 1, r32 => 1, "
                + "r33 => 1, r34 => 1, r35 => 1, r36 => 1, r37 => 1, r38 => 1, r39 => 1)",
                "A"},
            {"SELECT uf_TS_DAT_40(a => CAST(NULL AS TIMESTAMP_NTZ), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4"
                + " => 1, r5 => 1, r6 => 1, r7 => 1, r8 => 1, r9 => 1, r10 => 1, r11 => 1, r12 => 1, r13 => 1, "
                + "r14 => 1, r15 => 1, r16 => 1, r17 => 1, r18 => 1, r19 => 1, r20 => 1, r21 => 1, r22 => 1, "
                + "r23 => 1, r24 => 1, r25 => 1, r26 => 1, r27 => 1, r28 => 1, r29 => 1, r30 => 1, r31 => 1, "
                + "r32 => 1, r33 => 1, r34 => 1, r35 => 1, r36 => 1, r37 => 1, r38 => 1, r39 => 1)",
                "A"},
            {"CALL up_TS_DAT_40(a => CAST(NULL AS TIMESTAMP_NTZ), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 "
                + "=> 1, r5 => 1, r6 => 1, r7 => 1, r8 => 1, r9 => 1, r10 => 1, r11 => 1, r12 => 1, r13 => 1, "
                + "r14 => 1, r15 => 1, r16 => 1, r17 => 1, r18 => 1, r19 => 1, r20 => 1, r21 => 1, r22 => 1, "
                + "r23 => 1, r24 => 1, r25 => 1, r26 => 1, r27 => 1, r28 => 1, r29 => 1, r30 => 1, r31 => 1, "
                + "r32 => 1, r33 => 1, r34 => 1, r35 => 1, r36 => 1, r37 => 1, r38 => 1, r39 => 1)",
                "A"},
            {"SELECT uf_TS_TIM_40(a => CAST(NULL AS TIMESTAMP_NTZ), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4"
                + " => 1, r5 => 1, r6 => 1, r7 => 1, r8 => 1, r9 => 1, r10 => 1, r11 => 1, r12 => 1, r13 => 1, "
                + "r14 => 1, r15 => 1, r16 => 1, r17 => 1, r18 => 1, r19 => 1, r20 => 1, r21 => 1, r22 => 1, "
                + "r23 => 1, r24 => 1, r25 => 1, r26 => 1, r27 => 1, r28 => 1, r29 => 1, r30 => 1, r31 => 1, "
                + "r32 => 1, r33 => 1, r34 => 1, r35 => 1, r36 => 1, r37 => 1, r38 => 1, r39 => 1)",
                "A"},
            {"CALL up_TS_TIM_40(a => CAST(NULL AS TIMESTAMP_NTZ), r0 => 1, r1 => 1, r2 => 1, r3 => 1, r4 "
                + "=> 1, r5 => 1, r6 => 1, r7 => 1, r8 => 1, r9 => 1, r10 => 1, r11 => 1, r12 => 1, r13 => 1, "
                + "r14 => 1, r15 => 1, r16 => 1, r17 => 1, r18 => 1, r19 => 1, r20 => 1, r21 => 1, r22 => 1, "
                + "r23 => 1, r24 => 1, r25 => 1, r26 => 1, r27 => 1, r28 => 1, r29 => 1, r30 => 1, r31 => 1, "
                + "r32 => 1, r33 => 1, r34 => 1, r35 => 1, r36 => 1, r37 => 1, r38 => 1, r39 => 1)",
                "A"},
        });
    }

    @Test
    public void narrowingConversions() {
        engine.execute("CREATE OR REPLACE FUNCTION h_VF_VN_m1(a FLOAT, b VARIANT, r0 NUMBER, r1 NUMBER, r2 NUMBER, "
            + "r3 NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'X'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h_VF_VN_m1(a VARIANT, b NUMBER, r0 FLOAT, r1 NUMBER, r2 NUMBER, "
            + "r3 NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'Y'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h_VF_VN_0(a FLOAT, b VARIANT, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3"
            + " NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'X'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h_VF_VN_0(a VARIANT, b NUMBER, r0 NUMBER, r1 NUMBER, r2 NUMBER, "
            + "r3 NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'Y'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h_VF_VN_1(a FLOAT, b VARIANT, r0 FLOAT, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'X'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h_VF_VN_1(a VARIANT, b NUMBER, r0 NUMBER, r1 NUMBER, r2 NUMBER, "
            + "r3 NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'Y'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h_VF_FN_m1(a FLOAT, b FLOAT, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'X'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h_VF_FN_m1(a VARIANT, b NUMBER, r0 FLOAT, r1 NUMBER, r2 NUMBER, "
            + "r3 NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'Y'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h_VF_FN_0(a FLOAT, b FLOAT, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'X'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h_VF_FN_0(a VARIANT, b NUMBER, r0 NUMBER, r1 NUMBER, r2 NUMBER, "
            + "r3 NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'Y'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h_VF_FN_1(a FLOAT, b FLOAT, r0 FLOAT, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'X'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h_VF_FN_1(a VARIANT, b NUMBER, r0 NUMBER, r1 NUMBER, r2 NUMBER, "
            + "r3 NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'Y'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h_VN_FN_m1(a NUMBER, b FLOAT, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3"
            + " NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'X'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h_VN_FN_m1(a VARIANT, b NUMBER, r0 FLOAT, r1 NUMBER, r2 NUMBER, "
            + "r3 NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'Y'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h_VN_FN_0(a NUMBER, b FLOAT, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'X'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h_VN_FN_0(a VARIANT, b NUMBER, r0 NUMBER, r1 NUMBER, r2 NUMBER, "
            + "r3 NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'Y'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h_VN_FN_1(a NUMBER, b FLOAT, r0 FLOAT, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'X'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h_VN_FN_1(a VARIANT, b NUMBER, r0 NUMBER, r1 NUMBER, r2 NUMBER, "
            + "r3 NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'Y'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h_FN_TD_m1(a NUMBER, b TIMESTAMP_NTZ, r0 NUMBER, r1 NUMBER, r2 "
            + "NUMBER, r3 NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'X'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h_FN_TD_m1(a FLOAT, b DATE, r0 FLOAT, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'Y'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h_FN_TD_0(a NUMBER, b TIMESTAMP_NTZ, r0 NUMBER, r1 NUMBER, r2 "
            + "NUMBER, r3 NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'X'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h_FN_TD_0(a FLOAT, b DATE, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'Y'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h_FN_TD_1(a NUMBER, b TIMESTAMP_NTZ, r0 FLOAT, r1 NUMBER, r2 "
            + "NUMBER, r3 NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'X'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h_FN_TD_1(a FLOAT, b DATE, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'Y'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h_VF_TD_m1(a FLOAT, b TIMESTAMP_NTZ, r0 NUMBER, r1 NUMBER, r2 "
            + "NUMBER, r3 NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'X'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h_VF_TD_m1(a VARIANT, b DATE, r0 FLOAT, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'Y'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h_VF_TD_0(a FLOAT, b TIMESTAMP_NTZ, r0 NUMBER, r1 NUMBER, r2 "
            + "NUMBER, r3 NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'X'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h_VF_TD_0(a VARIANT, b DATE, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'Y'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h_VF_TD_1(a FLOAT, b TIMESTAMP_NTZ, r0 FLOAT, r1 NUMBER, r2 "
            + "NUMBER, r3 NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'X'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h_VF_TD_1(a VARIANT, b DATE, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'Y'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h_VN_TD_m1(a NUMBER, b TIMESTAMP_NTZ, r0 NUMBER, r1 NUMBER, r2 "
            + "NUMBER, r3 NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'X'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h_VN_TD_m1(a VARIANT, b DATE, r0 FLOAT, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'Y'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h_VN_TD_0(a NUMBER, b TIMESTAMP_NTZ, r0 NUMBER, r1 NUMBER, r2 "
            + "NUMBER, r3 NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'X'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h_VN_TD_0(a VARIANT, b DATE, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'Y'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h_VN_TD_1(a NUMBER, b TIMESTAMP_NTZ, r0 FLOAT, r1 NUMBER, r2 "
            + "NUMBER, r3 NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'X'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h_VN_TD_1(a VARIANT, b DATE, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'Y'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h_TD_TT_m1(a DATE, b TIMESTAMP_NTZ, r0 NUMBER, r1 NUMBER, r2 "
            + "NUMBER, r3 NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'X'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h_TD_TT_m1(a TIMESTAMP_NTZ, b TIME, r0 FLOAT, r1 NUMBER, r2 "
            + "NUMBER, r3 NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'Y'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h_TD_TT_0(a DATE, b TIMESTAMP_NTZ, r0 NUMBER, r1 NUMBER, r2 "
            + "NUMBER, r3 NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'X'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h_TD_TT_0(a TIMESTAMP_NTZ, b TIME, r0 NUMBER, r1 NUMBER, r2 "
            + "NUMBER, r3 NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'Y'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h_TD_TT_1(a DATE, b TIMESTAMP_NTZ, r0 FLOAT, r1 NUMBER, r2 "
            + "NUMBER, r3 NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'X'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h_TD_TT_1(a TIMESTAMP_NTZ, b TIME, r0 NUMBER, r1 NUMBER, r2 "
            + "NUMBER, r3 NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'Y'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h2_0(a FLOAT, b FLOAT, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'X'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h2_0(a NUMBER, b VARIANT, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'Y'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h2_1(a FLOAT, b FLOAT, r0 FLOAT, r1 NUMBER, r2 NUMBER, r3 NUMBER,"
            + " r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'X'$$");
        engine.execute("CREATE OR REPLACE FUNCTION h2_1(a NUMBER, b VARIANT, r0 NUMBER, r1 NUMBER, r2 NUMBER, r3 "
            + "NUMBER, r4 NUMBER, r5 NUMBER, r6 NUMBER, r7 NUMBER) RETURNS VARCHAR AS $$'Y'$$");
        assertCells(new String[][] {
            {"SELECT h_VF_VN_m1(a => CAST(NULL AS VARIANT), b => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, "
                + "r2 => 1, r3 => 1, r4 => 1, r5 => 1, r6 => 1, r7 => 1)",
                "X"},
            {"SELECT h_VF_VN_0(a => CAST(NULL AS VARIANT), b => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, "
                + "r2 => 1, r3 => 1, r4 => 1, r5 => 1, r6 => 1, r7 => 1)",
                "X"},
            {"SELECT h_VF_VN_1(a => CAST(NULL AS VARIANT), b => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, "
                + "r2 => 1, r3 => 1, r4 => 1, r5 => 1, r6 => 1, r7 => 1)",
                "X"},
            {"SELECT h_VF_FN_m1(a => CAST(NULL AS VARIANT), b => CAST(NULL AS FLOAT), r0 => 1, r1 => 1, r2"
                + " => 1, r3 => 1, r4 => 1, r5 => 1, r6 => 1, r7 => 1)",
                "X"},
            {"SELECT h_VF_FN_0(a => CAST(NULL AS VARIANT), b => CAST(NULL AS FLOAT), r0 => 1, r1 => 1, r2 "
                + "=> 1, r3 => 1, r4 => 1, r5 => 1, r6 => 1, r7 => 1)",
                "REFUSED SQL compilation error: | More than one function of H_VF_FN_0 are resolved in prepare"
                + " state."},
            {"SELECT h_VF_FN_1(a => CAST(NULL AS VARIANT), b => CAST(NULL AS FLOAT), r0 => 1, r1 => 1, r2 "
                + "=> 1, r3 => 1, r4 => 1, r5 => 1, r6 => 1, r7 => 1)",
                "Y"},
            {"SELECT h_VN_FN_m1(a => CAST(NULL AS VARIANT), b => CAST(NULL AS FLOAT), r0 => 1, r1 => 1, r2"
                + " => 1, r3 => 1, r4 => 1, r5 => 1, r6 => 1, r7 => 1)",
                "Y"},
            {"SELECT h_VN_FN_0(a => CAST(NULL AS VARIANT), b => CAST(NULL AS FLOAT), r0 => 1, r1 => 1, r2 "
                + "=> 1, r3 => 1, r4 => 1, r5 => 1, r6 => 1, r7 => 1)",
                "Y"},
            {"SELECT h_VN_FN_1(a => CAST(NULL AS VARIANT), b => CAST(NULL AS FLOAT), r0 => 1, r1 => 1, r2 "
                + "=> 1, r3 => 1, r4 => 1, r5 => 1, r6 => 1, r7 => 1)",
                "Y"},
            {"SELECT h_FN_TD_m1(a => CAST(NULL AS FLOAT), b => CAST(NULL AS TIMESTAMP_NTZ), r0 => 1, r1 =>"
                + " 1, r2 => 1, r3 => 1, r4 => 1, r5 => 1, r6 => 1, r7 => 1)",
                "X"},
            {"SELECT h_FN_TD_0(a => CAST(NULL AS FLOAT), b => CAST(NULL AS TIMESTAMP_NTZ), r0 => 1, r1 => "
                + "1, r2 => 1, r3 => 1, r4 => 1, r5 => 1, r6 => 1, r7 => 1)",
                "REFUSED SQL compilation error: | More than one function of H_FN_TD_0 are resolved in prepare"
                + " state."},
            {"SELECT h_FN_TD_1(a => CAST(NULL AS FLOAT), b => CAST(NULL AS TIMESTAMP_NTZ), r0 => 1, r1 => "
                + "1, r2 => 1, r3 => 1, r4 => 1, r5 => 1, r6 => 1, r7 => 1)",
                "Y"},
            {"SELECT h_VF_TD_m1(a => CAST(NULL AS VARIANT), b => CAST(NULL AS TIMESTAMP_NTZ), r0 => 1, r1 "
                + "=> 1, r2 => 1, r3 => 1, r4 => 1, r5 => 1, r6 => 1, r7 => 1)",
                "X"},
            {"SELECT h_VF_TD_0(a => CAST(NULL AS VARIANT), b => CAST(NULL AS TIMESTAMP_NTZ), r0 => 1, r1 "
                + "=> 1, r2 => 1, r3 => 1, r4 => 1, r5 => 1, r6 => 1, r7 => 1)",
                "REFUSED SQL compilation error: | More than one function of H_VF_TD_0 are resolved in prepare"
                + " state."},
            {"SELECT h_VF_TD_1(a => CAST(NULL AS VARIANT), b => CAST(NULL AS TIMESTAMP_NTZ), r0 => 1, r1 "
                + "=> 1, r2 => 1, r3 => 1, r4 => 1, r5 => 1, r6 => 1, r7 => 1)",
                "Y"},
            {"SELECT h_VN_TD_m1(a => CAST(NULL AS VARIANT), b => CAST(NULL AS TIMESTAMP_NTZ), r0 => 1, r1 "
                + "=> 1, r2 => 1, r3 => 1, r4 => 1, r5 => 1, r6 => 1, r7 => 1)",
                "Y"},
            {"SELECT h_VN_TD_0(a => CAST(NULL AS VARIANT), b => CAST(NULL AS TIMESTAMP_NTZ), r0 => 1, r1 "
                + "=> 1, r2 => 1, r3 => 1, r4 => 1, r5 => 1, r6 => 1, r7 => 1)",
                "Y"},
            {"SELECT h_VN_TD_1(a => CAST(NULL AS VARIANT), b => CAST(NULL AS TIMESTAMP_NTZ), r0 => 1, r1 "
                + "=> 1, r2 => 1, r3 => 1, r4 => 1, r5 => 1, r6 => 1, r7 => 1)",
                "Y"},
            {"SELECT h_TD_TT_m1(a => CAST(NULL AS TIMESTAMP_NTZ), b => CAST(NULL AS TIMESTAMP_NTZ), r0 => "
                + "1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5 => 1, r6 => 1, r7 => 1)",
                "X"},
            {"SELECT h_TD_TT_0(a => CAST(NULL AS TIMESTAMP_NTZ), b => CAST(NULL AS TIMESTAMP_NTZ), r0 => "
                + "1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5 => 1, r6 => 1, r7 => 1)",
                "REFUSED SQL compilation error: | More than one function of H_TD_TT_0 are resolved in prepare"
                + " state."},
            {"SELECT h_TD_TT_1(a => CAST(NULL AS TIMESTAMP_NTZ), b => CAST(NULL AS TIMESTAMP_NTZ), r0 => "
                + "1, r1 => 1, r2 => 1, r3 => 1, r4 => 1, r5 => 1, r6 => 1, r7 => 1)",
                "Y"},
            {"SELECT h2_0(a => CAST(NULL AS VARIANT), b => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, r2 => "
                + "1, r3 => 1, r4 => 1, r5 => 1, r6 => 1, r7 => 1)",
                "REFUSED SQL compilation error: | More than one function of H2_0 are resolved in prepare "
                + "state."},
            {"SELECT h2_1(a => CAST(NULL AS VARIANT), b => CAST(NULL AS VARIANT), r0 => 1, r1 => 1, r2 => "
                + "1, r3 => 1, r4 => 1, r5 => 1, r6 => 1, r7 => 1)",
                "Y"},
        });
    }

    @Test
    public void refusedConversions() {
        engine.execute("CREATE OR REPLACE FUNCTION x0_NUM(p TIMESTAMP_NTZ, q NUMBER) RETURNS VARCHAR AS "
            + "$$'TIMESTAMP_NTZ'$$");
        engine.execute("CREATE OR REPLACE FUNCTION x0_NUM(p VARCHAR, q VARCHAR) RETURNS VARCHAR AS $$'VARCHAR'$$");
        engine.execute("CREATE OR REPLACE FUNCTION y0_NUM(p TIMESTAMP_NTZ) RETURNS VARCHAR AS $$'TIMESTAMP_NTZ'$$");
        engine.execute("CREATE OR REPLACE FUNCTION y0_NUM(p BINARY) RETURNS VARCHAR AS $$'BINARY'$$");
        engine.execute("CREATE OR REPLACE PROCEDURE xp0_NUM(p TIMESTAMP_NTZ, q NUMBER) RETURNS VARCHAR LANGUAGE SQL "
            + "AS $$ BEGIN RETURN 'TIMESTAMP_NTZ'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE xp0_NUM(p VARCHAR, q VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$"
            + " BEGIN RETURN 'VARCHAR'; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION x1_VAR(p VARIANT, q NUMBER) RETURNS VARCHAR AS $$'VARIANT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION x1_VAR(p TIME, q VARCHAR) RETURNS VARCHAR AS $$'TIME'$$");
        engine.execute("CREATE OR REPLACE FUNCTION y1_VAR(p VARIANT) RETURNS VARCHAR AS $$'VARIANT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION y1_VAR(p BINARY) RETURNS VARCHAR AS $$'BINARY'$$");
        engine.execute("CREATE OR REPLACE PROCEDURE xp1_VAR(p VARIANT, q NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ "
            + "BEGIN RETURN 'VARIANT'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE xp1_VAR(p TIME, q VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ "
            + "BEGIN RETURN 'TIME'; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION x2_BOO(p FLOAT, q NUMBER) RETURNS VARCHAR AS $$'FLOAT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION x2_BOO(p VARCHAR, q VARCHAR) RETURNS VARCHAR AS $$'VARCHAR'$$");
        engine.execute("CREATE OR REPLACE FUNCTION y2_BOO(p FLOAT) RETURNS VARCHAR AS $$'FLOAT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION y2_BOO(p BINARY) RETURNS VARCHAR AS $$'BINARY'$$");
        engine.execute("CREATE OR REPLACE PROCEDURE xp2_BOO(p FLOAT, q NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ "
            + "BEGIN RETURN 'FLOAT'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE xp2_BOO(p VARCHAR, q VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$"
            + " BEGIN RETURN 'VARCHAR'; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION x3_BOO(p NUMBER, q NUMBER) RETURNS VARCHAR AS $$'NUMBER'$$");
        engine.execute("CREATE OR REPLACE FUNCTION x3_BOO(p VARIANT, q VARCHAR) RETURNS VARCHAR AS $$'VARIANT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION y3_BOO(p NUMBER) RETURNS VARCHAR AS $$'NUMBER'$$");
        engine.execute("CREATE OR REPLACE FUNCTION y3_BOO(p BINARY) RETURNS VARCHAR AS $$'BINARY'$$");
        engine.execute("CREATE OR REPLACE PROCEDURE xp3_BOO(p NUMBER, q NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ "
            + "BEGIN RETURN 'NUMBER'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE xp3_BOO(p VARIANT, q VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$"
            + " BEGIN RETURN 'VARIANT'; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION x4_ARR(p OBJECT, q NUMBER) RETURNS VARCHAR AS $$'OBJECT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION x4_ARR(p VARIANT, q VARCHAR) RETURNS VARCHAR AS $$'VARIANT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION y4_ARR(p OBJECT) RETURNS VARCHAR AS $$'OBJECT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION y4_ARR(p BINARY) RETURNS VARCHAR AS $$'BINARY'$$");
        engine.execute("CREATE OR REPLACE PROCEDURE xp4_ARR(p OBJECT, q NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ "
            + "BEGIN RETURN 'OBJECT'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE xp4_ARR(p VARIANT, q VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$"
            + " BEGIN RETURN 'VARIANT'; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION x5_ARR(p VARCHAR, q NUMBER) RETURNS VARCHAR AS $$'VARCHAR'$$");
        engine.execute("CREATE OR REPLACE FUNCTION x5_ARR(p VARIANT, q VARCHAR) RETURNS VARCHAR AS $$'VARIANT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION y5_ARR(p VARCHAR) RETURNS VARCHAR AS $$'VARCHAR'$$");
        engine.execute("CREATE OR REPLACE FUNCTION y5_ARR(p BINARY) RETURNS VARCHAR AS $$'BINARY'$$");
        engine.execute("CREATE OR REPLACE PROCEDURE xp5_ARR(p VARCHAR, q NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ "
            + "BEGIN RETURN 'VARCHAR'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE xp5_ARR(p VARIANT, q VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$"
            + " BEGIN RETURN 'VARIANT'; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION x6_OBJ(p ARRAY, q NUMBER) RETURNS VARCHAR AS $$'ARRAY'$$");
        engine.execute("CREATE OR REPLACE FUNCTION x6_OBJ(p VARIANT, q VARCHAR) RETURNS VARCHAR AS $$'VARIANT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION y6_OBJ(p ARRAY) RETURNS VARCHAR AS $$'ARRAY'$$");
        engine.execute("CREATE OR REPLACE FUNCTION y6_OBJ(p BINARY) RETURNS VARCHAR AS $$'BINARY'$$");
        engine.execute("CREATE OR REPLACE PROCEDURE xp6_OBJ(p ARRAY, q NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ "
            + "BEGIN RETURN 'ARRAY'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE xp6_OBJ(p VARIANT, q VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$"
            + " BEGIN RETURN 'VARIANT'; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION x7_OBJ(p VARCHAR, q NUMBER) RETURNS VARCHAR AS $$'VARCHAR'$$");
        engine.execute("CREATE OR REPLACE FUNCTION x7_OBJ(p VARIANT, q VARCHAR) RETURNS VARCHAR AS $$'VARIANT'$$");
        engine.execute("CREATE OR REPLACE FUNCTION y7_OBJ(p VARCHAR) RETURNS VARCHAR AS $$'VARCHAR'$$");
        engine.execute("CREATE OR REPLACE FUNCTION y7_OBJ(p BINARY) RETURNS VARCHAR AS $$'BINARY'$$");
        engine.execute("CREATE OR REPLACE PROCEDURE xp7_OBJ(p VARCHAR, q NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ "
            + "BEGIN RETURN 'VARCHAR'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE xp7_OBJ(p VARIANT, q VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$"
            + " BEGIN RETURN 'VARIANT'; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION x8_TIM(p TIMESTAMP_NTZ, q NUMBER) RETURNS VARCHAR AS "
            + "$$'TIMESTAMP_NTZ'$$");
        engine.execute("CREATE OR REPLACE FUNCTION x8_TIM(p VARCHAR, q VARCHAR) RETURNS VARCHAR AS $$'VARCHAR'$$");
        engine.execute("CREATE OR REPLACE FUNCTION y8_TIM(p TIMESTAMP_NTZ) RETURNS VARCHAR AS $$'TIMESTAMP_NTZ'$$");
        engine.execute("CREATE OR REPLACE FUNCTION y8_TIM(p BINARY) RETURNS VARCHAR AS $$'BINARY'$$");
        engine.execute("CREATE OR REPLACE PROCEDURE xp8_TIM(p TIMESTAMP_NTZ, q NUMBER) RETURNS VARCHAR LANGUAGE SQL "
            + "AS $$ BEGIN RETURN 'TIMESTAMP_NTZ'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE xp8_TIM(p VARCHAR, q VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$"
            + " BEGIN RETURN 'VARCHAR'; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION x9_NUM(p DATE, q NUMBER) RETURNS VARCHAR AS $$'DATE'$$");
        engine.execute("CREATE OR REPLACE FUNCTION x9_NUM(p VARCHAR, q VARCHAR) RETURNS VARCHAR AS $$'VARCHAR'$$");
        engine.execute("CREATE OR REPLACE FUNCTION y9_NUM(p DATE) RETURNS VARCHAR AS $$'DATE'$$");
        engine.execute("CREATE OR REPLACE FUNCTION y9_NUM(p BINARY) RETURNS VARCHAR AS $$'BINARY'$$");
        engine.execute("CREATE OR REPLACE PROCEDURE xp9_NUM(p DATE, q NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ "
            + "BEGIN RETURN 'DATE'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE xp9_NUM(p VARCHAR, q VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$"
            + " BEGIN RETURN 'VARCHAR'; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION x10_VAR(p BINARY, q NUMBER) RETURNS VARCHAR AS $$'BINARY'$$");
        engine.execute("CREATE OR REPLACE FUNCTION x10_VAR(p NUMBER, q VARCHAR) RETURNS VARCHAR AS $$'NUMBER'$$");
        engine.execute("CREATE OR REPLACE FUNCTION y10_VAR(p BINARY) RETURNS VARCHAR AS $$'BINARY'$$");
        engine.execute("CREATE OR REPLACE FUNCTION y10_VAR(p BINARY) RETURNS VARCHAR AS $$'BINARY'$$");
        engine.execute("CREATE OR REPLACE PROCEDURE xp10_VAR(p BINARY, q NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ "
            + "BEGIN RETURN 'BINARY'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE xp10_VAR(p NUMBER, q VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$"
            + " BEGIN RETURN 'NUMBER'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE zp(p NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'n'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE zp(p VARCHAR, q NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN"
            + " RETURN 'v'; END $$");
        engine.execute("CREATE OR REPLACE FUNCTION zf(p NUMBER) RETURNS VARCHAR AS $$'n'$$");
        engine.execute("CREATE OR REPLACE FUNCTION amb(p ARRAY) RETURNS VARCHAR AS $$'a'$$");
        engine.execute("CREATE OR REPLACE FUNCTION amb(p OBJECT) RETURNS VARCHAR AS $$'o'$$");
        assertCells(new String[][] {
            {"SELECT x0_NUM(CAST(NULL AS NUMBER), 1)",
                "VARCHAR"},
            {"SELECT x0_NUM(p => CAST(NULL AS NUMBER), q => 1)",
                "VARCHAR"},
            {"SELECT x0_NUM(1, 1)",
                "VARCHAR"},
            {"SELECT x0_NUM(p => 1, q => 1)",
                "VARCHAR"},
            {"SELECT y0_NUM(CAST(NULL AS NUMBER))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'Y0_NUM': (NUMBER(38,0))"},
            {"SELECT y0_NUM(p => CAST(NULL AS NUMBER))",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [P] do not match"
                + " any signature for function Y0_NUM"},
            {"SELECT y0_NUM(1)",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'Y0_NUM': (NUMBER(1,0))"},
            {"CALL xp0_NUM(CAST(NULL AS NUMBER), 1)",
                "VARCHAR"},
            {"CALL xp0_NUM(p => CAST(NULL AS NUMBER), q => 1)",
                "VARCHAR"},
            {"SELECT x1_VAR(CAST(NULL AS VARCHAR), 1)",
                "TIME"},
            {"SELECT x1_VAR(p => CAST(NULL AS VARCHAR), q => 1)",
                "TIME"},
            {"SELECT x1_VAR('1', 1)",
                "TIME"},
            {"SELECT x1_VAR(p => '1', q => 1)",
                "TIME"},
            {"SELECT y1_VAR(CAST(NULL AS VARCHAR))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'Y1_VAR': (VARCHAR(134217728))"},
            {"SELECT y1_VAR(p => CAST(NULL AS VARCHAR))",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [P] do not match"
                + " any signature for function Y1_VAR"},
            {"SELECT y1_VAR('1')",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'Y1_VAR': (VARCHAR(1))"},
            {"CALL xp1_VAR(CAST(NULL AS VARCHAR), 1)",
                "TIME"},
            {"CALL xp1_VAR(p => CAST(NULL AS VARCHAR), q => 1)",
                "TIME"},
            {"SELECT x2_BOO(CAST(NULL AS BOOLEAN), 1)",
                "VARCHAR"},
            {"SELECT x2_BOO(p => CAST(NULL AS BOOLEAN), q => 1)",
                "VARCHAR"},
            {"SELECT x2_BOO(TRUE, 1)",
                "VARCHAR"},
            {"SELECT x2_BOO(p => TRUE, q => 1)",
                "VARCHAR"},
            {"SELECT y2_BOO(CAST(NULL AS BOOLEAN))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'Y2_BOO': (BOOLEAN)"},
            {"SELECT y2_BOO(p => CAST(NULL AS BOOLEAN))",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [P] do not match"
                + " any signature for function Y2_BOO"},
            {"SELECT y2_BOO(TRUE)",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'Y2_BOO': (BOOLEAN)"},
            {"CALL xp2_BOO(CAST(NULL AS BOOLEAN), 1)",
                "VARCHAR"},
            {"CALL xp2_BOO(p => CAST(NULL AS BOOLEAN), q => 1)",
                "VARCHAR"},
            {"SELECT x3_BOO(CAST(NULL AS BOOLEAN), 1)",
                "VARIANT"},
            {"SELECT x3_BOO(p => CAST(NULL AS BOOLEAN), q => 1)",
                "VARIANT"},
            {"SELECT x3_BOO(TRUE, 1)",
                "VARIANT"},
            {"SELECT x3_BOO(p => TRUE, q => 1)",
                "VARIANT"},
            {"SELECT y3_BOO(CAST(NULL AS BOOLEAN))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'Y3_BOO': (BOOLEAN)"},
            {"SELECT y3_BOO(p => CAST(NULL AS BOOLEAN))",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [P] do not match"
                + " any signature for function Y3_BOO"},
            {"SELECT y3_BOO(TRUE)",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'Y3_BOO': (BOOLEAN)"},
            {"CALL xp3_BOO(CAST(NULL AS BOOLEAN), 1)",
                "VARIANT"},
            {"CALL xp3_BOO(p => CAST(NULL AS BOOLEAN), q => 1)",
                "VARIANT"},
            {"SELECT x4_ARR(CAST(NULL AS ARRAY), 1)",
                "VARIANT"},
            {"SELECT x4_ARR(p => CAST(NULL AS ARRAY), q => 1)",
                "VARIANT"},
            {"SELECT x4_ARR(ARRAY_CONSTRUCT(1), 1)",
                "VARIANT"},
            {"SELECT x4_ARR(p => ARRAY_CONSTRUCT(1), q => 1)",
                "VARIANT"},
            {"SELECT y4_ARR(CAST(NULL AS ARRAY))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'Y4_ARR': (ARRAY)"},
            {"SELECT y4_ARR(p => CAST(NULL AS ARRAY))",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [P] do not match"
                + " any signature for function Y4_ARR"},
            {"SELECT y4_ARR(ARRAY_CONSTRUCT(1))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'Y4_ARR': (ARRAY)"},
            {"CALL xp4_ARR(CAST(NULL AS ARRAY), 1)",
                "VARIANT"},
            {"CALL xp4_ARR(p => CAST(NULL AS ARRAY), q => 1)",
                "VARIANT"},
            {"SELECT x5_ARR(CAST(NULL AS ARRAY), 1)",
                "VARIANT"},
            {"SELECT x5_ARR(p => CAST(NULL AS ARRAY), q => 1)",
                "VARIANT"},
            {"SELECT x5_ARR(ARRAY_CONSTRUCT(1), 1)",
                "VARIANT"},
            {"SELECT x5_ARR(p => ARRAY_CONSTRUCT(1), q => 1)",
                "VARIANT"},
            {"SELECT y5_ARR(CAST(NULL AS ARRAY))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'Y5_ARR': (ARRAY)"},
            {"SELECT y5_ARR(p => CAST(NULL AS ARRAY))",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [P] do not match"
                + " any signature for function Y5_ARR"},
            {"SELECT y5_ARR(ARRAY_CONSTRUCT(1))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'Y5_ARR': (ARRAY)"},
            {"CALL xp5_ARR(CAST(NULL AS ARRAY), 1)",
                "VARIANT"},
            {"CALL xp5_ARR(p => CAST(NULL AS ARRAY), q => 1)",
                "VARIANT"},
            {"SELECT x6_OBJ(CAST(NULL AS OBJECT), 1)",
                "VARIANT"},
            {"SELECT x6_OBJ(p => CAST(NULL AS OBJECT), q => 1)",
                "VARIANT"},
            {"SELECT x6_OBJ(OBJECT_CONSTRUCT('a',1), 1)",
                "VARIANT"},
            {"SELECT x6_OBJ(p => OBJECT_CONSTRUCT('a',1), q => 1)",
                "VARIANT"},
            {"SELECT y6_OBJ(CAST(NULL AS OBJECT))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'Y6_OBJ': (OBJECT)"},
            {"SELECT y6_OBJ(p => CAST(NULL AS OBJECT))",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [P] do not match"
                + " any signature for function Y6_OBJ"},
            {"SELECT y6_OBJ(OBJECT_CONSTRUCT('a',1))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'Y6_OBJ': (OBJECT)"},
            {"CALL xp6_OBJ(CAST(NULL AS OBJECT), 1)",
                "VARIANT"},
            {"CALL xp6_OBJ(p => CAST(NULL AS OBJECT), q => 1)",
                "VARIANT"},
            {"SELECT x7_OBJ(CAST(NULL AS OBJECT), 1)",
                "VARIANT"},
            {"SELECT x7_OBJ(p => CAST(NULL AS OBJECT), q => 1)",
                "VARIANT"},
            {"SELECT x7_OBJ(OBJECT_CONSTRUCT('a',1), 1)",
                "VARIANT"},
            {"SELECT x7_OBJ(p => OBJECT_CONSTRUCT('a',1), q => 1)",
                "VARIANT"},
            {"SELECT y7_OBJ(CAST(NULL AS OBJECT))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'Y7_OBJ': (OBJECT)"},
            {"SELECT y7_OBJ(p => CAST(NULL AS OBJECT))",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [P] do not match"
                + " any signature for function Y7_OBJ"},
            {"SELECT y7_OBJ(OBJECT_CONSTRUCT('a',1))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'Y7_OBJ': (OBJECT)"},
            {"CALL xp7_OBJ(CAST(NULL AS OBJECT), 1)",
                "VARIANT"},
            {"CALL xp7_OBJ(p => CAST(NULL AS OBJECT), q => 1)",
                "VARIANT"},
            {"SELECT x8_TIM(CAST(NULL AS TIME), 1)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT x8_TIM(p => CAST(NULL AS TIME), q => 1)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT x8_TIM('10:00:00'::TIME, 1)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT x8_TIM(p => '10:00:00'::TIME, q => 1)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"CALL xp8_TIM(CAST(NULL AS TIME), 1)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"CALL xp8_TIM(p => CAST(NULL AS TIME), q => 1)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT x9_NUM(CAST(NULL AS NUMBER), 1)",
                "VARCHAR"},
            {"SELECT x9_NUM(p => CAST(NULL AS NUMBER), q => 1)",
                "VARCHAR"},
            {"SELECT x9_NUM(1, 1)",
                "VARCHAR"},
            {"SELECT x9_NUM(p => 1, q => 1)",
                "VARCHAR"},
            {"SELECT y9_NUM(CAST(NULL AS NUMBER))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'Y9_NUM': (NUMBER(38,0))"},
            {"SELECT y9_NUM(p => CAST(NULL AS NUMBER))",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [P] do not match"
                + " any signature for function Y9_NUM"},
            {"SELECT y9_NUM(1)",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'Y9_NUM': (NUMBER(1,0))"},
            {"CALL xp9_NUM(CAST(NULL AS NUMBER), 1)",
                "VARCHAR"},
            {"CALL xp9_NUM(p => CAST(NULL AS NUMBER), q => 1)",
                "VARCHAR"},
            {"SELECT x10_VAR(CAST(NULL AS VARIANT), 1)",
                "NUMBER"},
            {"SELECT x10_VAR(p => CAST(NULL AS VARIANT), q => 1)",
                "NUMBER"},
            {"SELECT x10_VAR(PARSE_JSON('1'), 1)",
                "NUMBER"},
            {"SELECT x10_VAR(p => PARSE_JSON('1'), q => 1)",
                "NUMBER"},
            {"SELECT y10_VAR(CAST(NULL AS VARIANT))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'Y10_VAR': (VARIANT)"},
            {"SELECT y10_VAR(p => CAST(NULL AS VARIANT))",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [P] do not match"
                + " any signature for function Y10_VAR"},
            {"SELECT y10_VAR(PARSE_JSON('1'))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'Y10_VAR': (VARIANT)"},
            {"CALL xp10_VAR(CAST(NULL AS VARIANT), 1)",
                "NUMBER"},
            {"CALL xp10_VAR(p => CAST(NULL AS VARIANT), q => 1)",
                "NUMBER"},
            {"CALL zp(z => 1)",
                "REFUSED SQL compilation error: error line 0 at position -1 | named arguments [Z] do not "
                + "match any signature for function ZP"},
            {"SELECT zf(z => 1)",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [Z] do not match"
                + " any signature for function ZF"},
            {"EXECUTE IMMEDIATE $$ BEGIN CALL zp(z => 1); EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' "
                + "|| SQLSTATE; END $$",
                "944 42601"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET r VARCHAR := (SELECT x10_var(p => CAST(NULL AS BOOLEAN), q =>"
                + " 1)); RETURN r; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE; END $$",
                "944 42601"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET r VARCHAR := (SELECT amb(p => CAST(NULL AS VARIANT))); RETURN"
                + " r; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE; END $$",
                "2237 22000"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET r VARCHAR := (SELECT x8_tim('10:00:00'::TIME, 1)); RETURN r; "
                + "EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE; END $$",
                "1010 42846"},
        });
    }
}
