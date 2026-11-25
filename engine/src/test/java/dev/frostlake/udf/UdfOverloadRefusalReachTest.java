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
 * When no overload of a user-defined function accepts a call, the call is refused at the overload its arguments'
 * families reach first, by position and by name: a TIME offered {@code y(TIMESTAMP_NTZ)} beside {@code y(BINARY)}
 * reaches the timestamp one and is refused there as {@code incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]},
 * timestamp overloads it reaches alike are refused as ambiguous when named, and a call whose arguments reach no
 * overload keeps the refusal naming every argument's type. Built-in functions are unaffected.
 */
public class UdfOverloadRefusalReachTest extends MeasuredCellsTestSupport {

    @Test
    public void refusalReachesFirstOverloadByFamily() {
        engine.execute("CREATE OR REPLACE FUNCTION ov_a(p TIMESTAMP_NTZ) RETURNS VARCHAR AS $$ 'TNTZ' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ov_a(p BINARY) RETURNS VARCHAR AS $$ 'BINARY' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ov_a2(p BINARY) RETURNS VARCHAR AS $$ 'BINARY' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ov_a2(p TIMESTAMP_NTZ) RETURNS VARCHAR AS $$ 'TNTZ' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ov_b(p DATE) RETURNS VARCHAR AS $$ 'DATE' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ov_b(p BINARY) RETURNS VARCHAR AS $$ 'BINARY' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ov_q(p TIMESTAMP_LTZ) RETURNS VARCHAR AS $$ 'TLTZ' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ov_q(p TIMESTAMP_TZ) RETURNS VARCHAR AS $$ 'TTZ' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ov_r(p BOOLEAN) RETURNS VARCHAR AS $$ 'BOOLEAN' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ov_r(p TIMESTAMP_NTZ) RETURNS VARCHAR AS $$ 'TNTZ' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ov_s(p FLOAT) RETURNS VARCHAR AS $$ 'FLOAT' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ov_s(p TIMESTAMP_NTZ) RETURNS VARCHAR AS $$ 'TNTZ' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ov_t1(p TIMESTAMP_NTZ) RETURNS VARCHAR AS $$ 'TNTZ' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ov_t1(p BINARY) RETURNS VARCHAR AS $$ 'BINARY' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ov_t1(p NUMBER) RETURNS VARCHAR AS $$ 'NUMBER' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ov_t2(p TIMESTAMP_LTZ) RETURNS VARCHAR AS $$ 'TLTZ' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ov_t2(p TIMESTAMP_TZ) RETURNS VARCHAR AS $$ 'TTZ' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ov_t2(p BINARY) RETURNS VARCHAR AS $$ 'BINARY' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ov_t3(p DATE) RETURNS VARCHAR AS $$ 'DATE' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ov_t3(p TIME) RETURNS VARCHAR AS $$ 'TIME' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ov_t3(p BINARY) RETURNS VARCHAR AS $$ 'BINARY' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ov_t5(p BINARY) RETURNS VARCHAR AS $$ 'BINARY' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ov_t5(p DATE) RETURNS VARCHAR AS $$ 'DATE' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ov_t5(p VARIANT) RETURNS VARCHAR AS $$ 'VARIANT' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ov_vt(p TIME) RETURNS VARCHAR AS $$ 'TIME' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ov_vt(p NUMBER) RETURNS VARCHAR AS $$ 'NUMBER' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ow_u(a NUMBER, b TIMESTAMP_NTZ) RETURNS VARCHAR AS $$ '0' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ow_u(a NUMBER, b BINARY) RETURNS VARCHAR AS $$ '1' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ow_v(a VARCHAR, b TIMESTAMP_NTZ) RETURNS VARCHAR AS $$ '0' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ow_v(a NUMBER, b BINARY) RETURNS VARCHAR AS $$ '1' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ow_w(a NUMBER, b BINARY) RETURNS VARCHAR AS $$ '0' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ow_w(a BINARY, b NUMBER) RETURNS VARCHAR AS $$ '1' $$");
        assertCells(new String[][] {
            {"SELECT ov_a('10:00'::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT ov_a(p => '10:00'::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT ov_a(12)",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'OV_A': (NUMBER(2,0))"},
            {"SELECT ov_a(p => 12)",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [P] do not match"
                + " any signature for function OV_A"},
            {"SELECT ov_a(TRUE)",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'OV_A': (BOOLEAN)"},
            {"SELECT ov_a(p => TRUE)",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [P] do not match"
                + " any signature for function OV_A"},
            {"SELECT ov_a([1])",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'OV_A': (ARRAY)"},
            {"SELECT ov_a(p => [1])",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [P] do not match"
                + " any signature for function OV_A"},
            {"SELECT ov_a({'a':1})",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'OV_A': (OBJECT)"},
            {"SELECT ov_a(p => {'a':1})",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [P] do not match"
                + " any signature for function OV_A"},
            {"SELECT ov_a(PARSE_JSON('\"x\"'))",
                "TNTZ"},
            {"SELECT ov_a(p => PARSE_JSON('\"x\"'))",
                "TNTZ"},
            {"SELECT ov_a2('10:00'::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT ov_a2(p => '10:00'::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT ov_a2(12)",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'OV_A2': (NUMBER(2,0))"},
            {"SELECT ov_a2(p => 12)",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [P] do not match"
                + " any signature for function OV_A2"},
            {"SELECT ov_b('10:00'::TIME)",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'OV_B': (TIME(9))"},
            {"SELECT ov_b(p => '10:00'::TIME)",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [P] do not match"
                + " any signature for function OV_B"},
            {"SELECT ov_b(12)",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'OV_B': (NUMBER(2,0))"},
            {"SELECT ov_b(p => 12)",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [P] do not match"
                + " any signature for function OV_B"},
            {"SELECT ov_b(TRUE)",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'OV_B': (BOOLEAN)"},
            {"SELECT ov_b(p => TRUE)",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [P] do not match"
                + " any signature for function OV_B"},
            {"SELECT ov_b([1])",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'OV_B': (ARRAY)"},
            {"SELECT ov_b(p => [1])",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [P] do not match"
                + " any signature for function OV_B"},
            {"SELECT ov_q('10:00'::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_LTZ(9)]"},
            {"SELECT ov_q(p => '10:00'::TIME)",
                "REFUSED SQL compilation error: | More than one function of OV_Q are resolved in prepare "
                + "state."},
            {"SELECT ov_q(TO_BINARY('AB'))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'OV_Q': (BINARY(67108864))"},
            {"SELECT ov_q(p => TO_BINARY('AB'))",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [P] do not match"
                + " any signature for function OV_Q"},
            {"SELECT ov_r('10:00'::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT ov_r(p => '10:00'::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT ov_r(1.5::FLOAT)",
                "BOOLEAN"},
            {"SELECT ov_r(p => 1.5::FLOAT)",
                "BOOLEAN"},
            {"SELECT ov_r(TO_BINARY('AB'))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'OV_R': (BINARY(67108864))"},
            {"SELECT ov_r(p => TO_BINARY('AB'))",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [P] do not match"
                + " any signature for function OV_R"},
            {"SELECT ov_s('10:00'::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT ov_s(p => '10:00'::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT ov_s(TRUE)",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'OV_S': (BOOLEAN)"},
            {"SELECT ov_s(p => TRUE)",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [P] do not match"
                + " any signature for function OV_S"},
            {"SELECT ov_t1('10:00'::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT ov_t1(p => '10:00'::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT ov_t1(TRUE)",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'OV_T1': (BOOLEAN)"},
            {"SELECT ov_t1(p => TRUE)",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [P] do not match"
                + " any signature for function OV_T1"},
            {"SELECT ov_t1([1])",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'OV_T1': (ARRAY)"},
            {"SELECT ov_t1(p => [1])",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [P] do not match"
                + " any signature for function OV_T1"},
            {"SELECT ov_t2('10:00'::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_LTZ(9)]"},
            {"SELECT ov_t2(p => '10:00'::TIME)",
                "REFUSED SQL compilation error: | More than one function of OV_T2 are resolved in prepare "
                + "state."},
            {"SELECT ov_t3(12)",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'OV_T3': (NUMBER(2,0))"},
            {"SELECT ov_t3(p => 12)",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [P] do not match"
                + " any signature for function OV_T3"},
            {"SELECT ov_t3(TRUE)",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'OV_T3': (BOOLEAN)"},
            {"SELECT ov_t3(p => TRUE)",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [P] do not match"
                + " any signature for function OV_T3"},
            {"SELECT ov_t3([1])",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'OV_T3': (ARRAY)"},
            {"SELECT ov_t3(p => [1])",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [P] do not match"
                + " any signature for function OV_T3"},
            {"SELECT ov_t3('2020-01-02 10:00'::TIMESTAMP_NTZ)",
                "DATE"},
            {"SELECT ov_t3(p => '2020-01-02 10:00'::TIMESTAMP_NTZ)",
                "REFUSED SQL compilation error: | More than one function of OV_T3 are resolved in prepare "
                + "state."},
            {"SELECT ov_t5('10:00'::TIME)",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'OV_T5': (TIME(9))"},
            {"SELECT ov_t5(p => '10:00'::TIME)",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [P] do not match"
                + " any signature for function OV_T5"},
            {"SELECT ov_t5('2020-01-02 10:00'::TIMESTAMP_NTZ)",
                "DATE"},
            {"SELECT ov_t5(p => '2020-01-02 10:00'::TIMESTAMP_NTZ)",
                "DATE"},
            {"SELECT ov_vt('abc')",
                "NUMBER"},
            {"SELECT ov_vt(p => 'abc')",
                "REFUSED SQL compilation error: | More than one function of OV_VT are resolved in prepare "
                + "state."},
            {"SELECT ow_u(12, '10:00'::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT ow_u(a => 12, b => '10:00'::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT ow_u(b => '10:00'::TIME, a => 12)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT ow_u(12, TRUE)",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'OW_U': (NUMBER(2,0), BOOLEAN)"},
            {"SELECT ow_u(a => 12, b => TRUE)",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [A, B] do not "
                + "match any signature for function OW_U"},
            {"SELECT ow_u(b => TRUE, a => 12)",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [A, B] do not "
                + "match any signature for function OW_U"},
            {"SELECT ow_v(12, '10:00'::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT ow_v(a => 12, b => '10:00'::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT ow_v(b => '10:00'::TIME, a => 12)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT ow_v(TRUE, '10:00'::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT ow_v(a => TRUE, b => '10:00'::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT ow_v(b => '10:00'::TIME, a => TRUE)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT ow_w(12, '10:00'::TIME)",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'OW_W': (NUMBER(2,0), TIME(9))"},
            {"SELECT ow_w(a => 12, b => '10:00'::TIME)",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [A, B] do not "
                + "match any signature for function OW_W"},
            {"SELECT ow_w(b => '10:00'::TIME, a => 12)",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [A, B] do not "
                + "match any signature for function OW_W"},
            {"SELECT ow_w('10:00'::TIME, 12)",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'OW_W': (TIME(9), NUMBER(2,0))"},
            {"SELECT ow_w(a => '10:00'::TIME, b => 12)",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [A, B] do not "
                + "match any signature for function OW_W"},
            {"SELECT ow_w(b => 12, a => '10:00'::TIME)",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [A, B] do not "
                + "match any signature for function OW_W"},
            {"SELECT ow_w('10:00'::TIME, '10:00'::TIME)",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'OW_W': (TIME(9), TIME(9))"},
            {"SELECT ow_w(a => '10:00'::TIME, b => '10:00'::TIME)",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [A, B] do not "
                + "match any signature for function OW_W"},
            {"SELECT ow_w(b => '10:00'::TIME, a => '10:00'::TIME)",
                "REFUSED SQL compilation error: error line 1 at position 7 | named arguments [A, B] do not "
                + "match any signature for function OW_W"},
            {"SELECT ABS(TO_BINARY('AB'))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'ABS': (BINARY(67108864))"},
            {"SELECT UPPER('10:00'::TIME)",
                "10:00:00"},
            {"SELECT DATEADD(day, 1, TO_BINARY('AB'))",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'DATE_ADDDAYSTOTIMESTAMP': (NUMBER(1,0), BINARY(67108864))"},
            {"SELECT TO_DATE('10:00'::TIME)",
                "REFUSED SQL compilation error: | invalid type [TO_DATE(TO_TIME('10:00'))] for parameter "
                + "'TO_DATE'"},
            {"SELECT ARRAY_SIZE({'a':1})",
                "null"},
            {"SELECT LEFT(TO_BINARY('AB'), 1)",
                "AB"},
        });
    }

    @Test
    public void refusalReachInColumnsAndMixedCalls() {
        engine.execute("CREATE OR REPLACE FUNCTION ov_a(p TIMESTAMP_NTZ) RETURNS VARCHAR AS $$ 'TNTZ' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ov_a(p BINARY) RETURNS VARCHAR AS $$ 'BINARY' $$");
        engine.execute("CREATE OR REPLACE TABLE tt(t TIME, n NUMBER)");
        engine.execute("INSERT INTO tt VALUES ('10:00', 1)");
        engine.execute("CREATE OR REPLACE FUNCTION ov_b(p TIMESTAMP_TZ) RETURNS VARCHAR AS $$ 'TTZ' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ov_b(p VARCHAR) RETURNS VARCHAR AS $$ 'VARCHAR' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ov_b(p BINARY) RETURNS VARCHAR AS $$ 'BINARY' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ov_c(a TIMESTAMP_NTZ, b NUMBER) RETURNS VARCHAR AS $$ 'T' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ov_c(a BINARY, b NUMBER) RETURNS VARCHAR AS $$ 'B' $$");
        engine.execute("CREATE OR REPLACE FUNCTION ov_d(a TIMESTAMP_NTZ, b TIMESTAMP_LTZ) RETURNS VARCHAR AS $$ 'NL'"
            + " $$");
        engine.execute("CREATE OR REPLACE FUNCTION ov_d(a TIMESTAMP_TZ, b BINARY) RETURNS VARCHAR AS $$ 'TB' $$");
        assertCells(new String[][] {
            {"SELECT ov_a(t) FROM tt",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT ov_a(p => t) FROM tt",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT ov_a(t) FROM tt WHERE n = 5",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT ov_a((SELECT '10:00'::TIME))",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT ov_a('10:00'::TIME(3))",
                "REFUSED SQL compilation error: | incompatible types: [TIME(3)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT ov_b('10:00'::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_TZ(9)]"},
            {"SELECT ov_b(p => '10:00'::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_TZ(9)]"},
            {"SELECT ov_c('10:00'::TIME, NULL)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT ov_c(NULL, '10:00'::TIME)",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'OV_C': (NULL, TIME(9))"},
            {"SELECT ov_c(b => 1, a => '10:00'::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT ov_d('10:00'::TIME, '10:00'::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT ov_d(a => '10:00'::TIME, b => '10:00'::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]"},
            {"SELECT ov_d('10:00'::TIME, TO_BINARY('AB'))",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_TZ(9)]"},
            {"SELECT ov_d(b => TO_BINARY('AB'), a => '10:00'::TIME)",
                "REFUSED SQL compilation error: | incompatible types: [TIME(9)] and [TIMESTAMP_TZ(9)]"},
        });
    }
}
