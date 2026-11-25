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

package dev.frostlake.scripting;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * A CALL's answer given to a variable, {@code x := (CALL p())} of a named or an anonymous procedure: a VARCHAR name
 * holds the answer's fixed text whatever the session's output formats - a timestamp with as many fractional digits as
 * its type's precision, a TIMESTAMP_TZ with its offset, a FLOAT in its own text, a BOOLEAN {@code true}, a VARIANT its
 * JSON at the JSON_INDENT in force at the assignment - while a NUMBER or a VARIANT name holds the answer as it came,
 * and a RETURN of it answers in the answer's own type. A declaration whose initialiser gives it no type is refused
 * 92228 / P0000, one with neither a type nor an initialiser 92229 / P0000 (live-verified).
 */
public class CallAnswerVariableTextTest extends MeasuredCellsTestSupport {

    private static final String RESCAN =
        "SELECT SYSTEM$TYPEOF($1) || ' ' || $1::VARCHAR FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))";

    @Test
    public void callAnswersInVariables() {
        engine.execute("CREATE OR REPLACE PROCEDURE p_num() RETURNS NUMBER(10,2) LANGUAGE SQL AS $$ BEGIN RETURN "
            + "12.5; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE p_int() RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 42; END "
            + "$$");
        engine.execute("CREATE OR REPLACE PROCEDURE p_flt() RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN 1e20; END "
            + "$$");
        engine.execute("CREATE OR REPLACE PROCEDURE p_flt2() RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN 1.5e-7; "
            + "END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE p_flt3() RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN "
            + "2.5::FLOAT; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE p_nan() RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'NaN'::FLOAT; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE p_inf() RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'-inf'::FLOAT; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE p_bool() RETURNS BOOLEAN LANGUAGE SQL AS $$ BEGIN RETURN TRUE; "
            + "END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE p_date() RETURNS DATE LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'2024-01-02'::DATE; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE p_time() RETURNS TIME LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'03:04:05.123'::TIME; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE p_ntz() RETURNS TIMESTAMP_NTZ LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'2024-01-02 03:04:05'::TIMESTAMP_NTZ; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE p_ltz() RETURNS TIMESTAMP_LTZ LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'2024-01-02 03:04:05'::TIMESTAMP_LTZ; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE p_tz() RETURNS TIMESTAMP_TZ LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'2024-01-02 03:04:05 +02:00'::TIMESTAMP_TZ; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE p_var() RETURNS VARIANT LANGUAGE SQL AS $$ BEGIN RETURN "
            + "PARSE_JSON('{\"a\":1,\"b\":[1,2]}'); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE p_vstr() RETURNS VARIANT LANGUAGE SQL AS $$ BEGIN RETURN "
            + "TO_VARIANT('hi'); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE p_arr() RETURNS ARRAY LANGUAGE SQL AS $$ BEGIN RETURN "
            + "ARRAY_CONSTRUCT(1,'x',NULL); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE p_obj() RETURNS OBJECT LANGUAGE SQL AS $$ BEGIN RETURN "
            + "OBJECT_CONSTRUCT('k','v','n',2); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE p_bin() RETURNS BINARY LANGUAGE SQL AS $$ BEGIN RETURN "
            + "TO_BINARY('ABCD','HEX'); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE p_vc() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'abc'; "
            + "END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE f1() RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN 0.1; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE f2() RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN 1/3; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE f3() RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN 1e15; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE f4() RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN 1e16; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE f5() RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN 123456.789; "
            + "END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE f6() RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN -0.0::FLOAT; "
            + "END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE f7() RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN 1e-5; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE f8() RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN "
            + "12345678901234567890.0::FLOAT; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE f9() RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN 100::FLOAT; "
            + "END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE f10() RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN 1e-4; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE f11() RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN "
            + "0.000123456789; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE t9() RETURNS TIME(9) LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'03:04:05.123456789'::TIME(9); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE t0() RETURNS TIME(0) LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'03:04:05'::TIME(0); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE n3() RETURNS TIMESTAMP_NTZ(3) LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'2024-01-02 03:04:05.5'::TIMESTAMP_NTZ(3); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE tzn() RETURNS TIMESTAMP_TZ LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'2024-01-02 03:04:05 -05:30'::TIMESTAMP_TZ; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE ltz2() RETURNS TIMESTAMP_LTZ LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'2024-07-02 03:04:05.25'::TIMESTAMP_LTZ; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE vnum() RETURNS VARIANT LANGUAGE SQL AS $$ BEGIN RETURN "
            + "TO_VARIANT(1.5::FLOAT); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE vnull() RETURNS VARIANT LANGUAGE SQL AS $$ BEGIN RETURN "
            + "PARSE_JSON('null'); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE vbool() RETURNS VARIANT LANGUAGE SQL AS $$ BEGIN RETURN "
            + "TO_VARIANT(TRUE); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE vts() RETURNS VARIANT LANGUAGE SQL AS $$ BEGIN RETURN "
            + "TO_VARIANT('2024-01-02 03:04:05'::TIMESTAMP_NTZ); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE vemp() RETURNS OBJECT LANGUAGE SQL AS $$ BEGIN RETURN "
            + "OBJECT_CONSTRUCT(); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE bfalse() RETURNS BOOLEAN LANGUAGE SQL AS $$ BEGIN RETURN FALSE; "
            + "END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE n0() RETURNS NUMBER(38,0) LANGUAGE SQL AS $$ BEGIN RETURN 7; END"
            + " $$");
        engine.execute("CREATE OR REPLACE PROCEDURE n5() RETURNS NUMBER(12,5) LANGUAGE SQL AS $$ BEGIN RETURN -1.5; "
            + "END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE dnull() RETURNS DATE LANGUAGE SQL AS $$ BEGIN RETURN NULL; END "
            + "$$");
        engine.execute("CREATE OR REPLACE PROCEDURE vc5() RETURNS VARCHAR(5) LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'abcde'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE bn() RETURNS BINARY LANGUAGE SQL AS $$ BEGIN RETURN "
            + "TO_BINARY('','HEX'); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pvar() RETURNS VARIANT LANGUAGE SQL AS $$ BEGIN RETURN "
            + "PARSE_JSON('{\"a\":1,\"b\":[1,2]}'); END $$");
        engine.execute("ALTER SESSION SET TIMEZONE='America/Los_Angeles'");
        engine.execute("ALTER SESSION SET JSON_INDENT=2");
        assertCells(new String[][] {
            {"DECLARE x VARCHAR; BEGIN x := (CALL p_num()); RETURN '[' || x || ']'; END;",
                "[12.50]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL p_int()); RETURN '[' || x || ']'; END;",
                "[42]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL p_flt()); RETURN '[' || x || ']'; END;",
                "[1e+20]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL p_flt2()); RETURN '[' || x || ']'; END;",
                "[1.5e-07]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL p_flt3()); RETURN '[' || x || ']'; END;",
                "[2.5]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL p_nan()); RETURN '[' || x || ']'; END;",
                "[NaN]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL p_inf()); RETURN '[' || x || ']'; END;",
                "[-inf]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL p_bool()); RETURN '[' || x || ']'; END;",
                "[true]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL p_date()); RETURN '[' || x || ']'; END;",
                "[2024-01-02]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL p_time()); RETURN '[' || x || ']'; END;",
                "[03:04:05.123000000]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL p_ntz()); RETURN '[' || x || ']'; END;",
                "[2024-01-02 03:04:05.000000000]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL p_ltz()); RETURN '[' || x || ']'; END;",
                "[2024-01-02 03:04:05.000000000]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL p_tz()); RETURN '[' || x || ']'; END;",
                "[2024-01-02 03:04:05.000000000 +02:00]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL p_var()); RETURN '[' || x || ']'; END;",
                "[{ |   \"a\": 1, |   \"b\": [ |     1, |     2 |   ] | }]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL p_vstr()); RETURN '[' || x || ']'; END;",
                "[\"hi\"]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL p_arr()); RETURN '[' || x || ']'; END;",
                "[[ |   1, |   \"x\", |   undefined | ]]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL p_obj()); RETURN '[' || x || ']'; END;",
                "[{ |   \"k\": \"v\", |   \"n\": 2 | }]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL p_bin()); RETURN '[' || x || ']'; END;",
                "[ABCD]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL p_vc()); RETURN '[' || x || ']'; END;",
                "[abc]"},
        });
        engine.execute("ALTER SESSION SET TIMESTAMP_NTZ_OUTPUT_FORMAT='YYYY/MM/DD HH24:MI', "
                + "TIMESTAMP_LTZ_OUTPUT_FORMAT='DD.MM.YYYY HH24:MI TZH', "
                + "TIMESTAMP_TZ_OUTPUT_FORMAT='YYYY-MM-DD\"T\"HH24:MI:SSTZH:TZM', "
                + "DATE_OUTPUT_FORMAT='DD/MM/YYYY', TIME_OUTPUT_FORMAT='HH24.MI', JSON_INDENT=2, "
                + "BINARY_OUTPUT_FORMAT='BASE64'");
        assertCells(new String[][] {
            {"DECLARE x VARCHAR; BEGIN x := (CALL p_date()); RETURN '[' || x || ']'; END;",
                "[2024-01-02]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL p_time()); RETURN '[' || x || ']'; END;",
                "[03:04:05.123000000]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL p_ntz()); RETURN '[' || x || ']'; END;",
                "[2024-01-02 03:04:05.000000000]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL p_ltz()); RETURN '[' || x || ']'; END;",
                "[2024-01-02 03:04:05.000000000]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL p_tz()); RETURN '[' || x || ']'; END;",
                "[2024-01-02 03:04:05.000000000 +02:00]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL p_var()); RETURN '[' || x || ']'; END;",
                "[{ |   \"a\": 1, |   \"b\": [ |     1, |     2 |   ] | }]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL p_arr()); RETURN '[' || x || ']'; END;",
                "[[ |   1, |   \"x\", |   undefined | ]]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL p_obj()); RETURN '[' || x || ']'; END;",
                "[{ |   \"k\": \"v\", |   \"n\": 2 | }]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL p_bin()); RETURN '[' || x || ']'; END;",
                "[ABCD]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL p_flt()); RETURN '[' || x || ']'; END;",
                "[1e+20]"},
        });
        engine.execute("ALTER SESSION UNSET TIMESTAMP_NTZ_OUTPUT_FORMAT, TIMESTAMP_LTZ_OUTPUT_FORMAT, "
                + "TIMESTAMP_TZ_OUTPUT_FORMAT, DATE_OUTPUT_FORMAT, TIME_OUTPUT_FORMAT, BINARY_OUTPUT_FORMAT");
        assertCells(new String[][] {
            {"DECLARE x VARCHAR; BEGIN x := (CALL f1()); RETURN '[' || x || ']'; END;",
                "[0.1]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL f2()); RETURN '[' || x || ']'; END;",
                "[0.333333]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL f3()); RETURN '[' || x || ']'; END;",
                "[1e+15]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL f4()); RETURN '[' || x || ']'; END;",
                "[1e+16]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL f5()); RETURN '[' || x || ']'; END;",
                "[123456.789]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL f6()); RETURN '[' || x || ']'; END;",
                "[-0]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL f7()); RETURN '[' || x || ']'; END;",
                "[1e-05]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL f8()); RETURN '[' || x || ']'; END;",
                "[1.23456789012346e+19]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL f9()); RETURN '[' || x || ']'; END;",
                "[100]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL f10()); RETURN '[' || x || ']'; END;",
                "[0.0001]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL f11()); RETURN '[' || x || ']'; END;",
                "[0.000123456789]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL t9()); RETURN '[' || x || ']'; END;",
                "[03:04:05.123456789]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL t9()); RETURN LENGTH(x); END;",
                "18"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL t0()); RETURN '[' || x || ']'; END;",
                "[03:04:05]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL t0()); RETURN LENGTH(x); END;",
                "8"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL n3()); RETURN '[' || x || ']'; END;",
                "[2024-01-02 03:04:05.500]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL n3()); RETURN LENGTH(x); END;",
                "23"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL tzn()); RETURN '[' || x || ']'; END;",
                "[2024-01-02 03:04:05.000000000 -05:30]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL tzn()); RETURN LENGTH(x); END;",
                "36"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL ltz2()); RETURN '[' || x || ']'; END;",
                "[2024-07-02 03:04:05.250000000]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL ltz2()); RETURN LENGTH(x); END;",
                "29"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL vnum()); RETURN '[' || x || ']'; END;",
                "[1.500000000000000e+00]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL vnum()); RETURN LENGTH(x); END;",
                "21"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL vnull()); RETURN '[' || x || ']'; END;",
                "[null]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL vnull()); RETURN LENGTH(x); END;",
                "4"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL vbool()); RETURN '[' || x || ']'; END;",
                "[true]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL vbool()); RETURN LENGTH(x); END;",
                "4"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL vts()); RETURN '[' || x || ']'; END;",
                "[\"2024-01-02 03:04:05.000\"]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL vts()); RETURN LENGTH(x); END;",
                "25"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL vemp()); RETURN '[' || x || ']'; END;",
                "[{}]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL vemp()); RETURN LENGTH(x); END;",
                "2"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL bfalse()); RETURN '[' || x || ']'; END;",
                "[false]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL bfalse()); RETURN LENGTH(x); END;",
                "5"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL n0()); RETURN '[' || x || ']'; END;",
                "[7]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL n0()); RETURN LENGTH(x); END;",
                "1"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL n5()); RETURN '[' || x || ']'; END;",
                "[-1.50000]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL n5()); RETURN LENGTH(x); END;",
                "8"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL dnull()); RETURN '[' || x || ']'; END;",
                "null"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL dnull()); RETURN LENGTH(x); END;",
                "null"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL vc5()); RETURN '[' || x || ']'; END;",
                "[abcde]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL vc5()); RETURN LENGTH(x); END;",
                "5"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL bn()); RETURN '[' || x || ']'; END;",
                "[]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL bn()); RETURN LENGTH(x); END;",
                "0"},
        });
        engine.execute("ALTER SESSION SET JSON_INDENT=0");
        assertCells(new String[][] {
            {"DECLARE x VARCHAR; BEGIN x := (CALL pvar()); RETURN '[' || x || ']'; END;",
                "[{ | \"a\": 1, | \"b\": [ | 1, | 2 | ] | }]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL pvar()); ALTER SESSION SET JSON_INDENT=2; RETURN '[' || "
                + "x || ']'; END;",
                "[{ | \"a\": 1, | \"b\": [ | 1, | 2 | ] | }]"},
        });
        engine.execute("ALTER SESSION SET JSON_INDENT=4");
        assertCells(new String[][] {
            {"DECLARE x VARCHAR; BEGIN x := (CALL pvar()); RETURN '[' || x || ']'; END;",
                "[{ |     \"a\": 1, |     \"b\": [ |         1, |         2 |     ] | }]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL pvar()); ALTER SESSION SET JSON_INDENT=2; RETURN '[' || "
                + "x || ']'; END;",
                "[{ |     \"a\": 1, |     \"b\": [ |         1, |         2 |     ] | }]"},
            {"DECLARE x VARCHAR; BEGIN x := (WITH ap AS PROCEDURE() RETURNS TIMESTAMP_NTZ LANGUAGE SQL AS "
                + "$$BEGIN RETURN '2024-01-02 03:04:05'::TIMESTAMP_NTZ; END$$ CALL ap()); RETURN '[' || x || "
                + "']'; END;",
                "[2024-01-02 03:04:05.000000000]"},
            {"DECLARE x VARCHAR; BEGIN x := (WITH ap AS PROCEDURE() RETURNS FLOAT LANGUAGE SQL AS $$BEGIN "
                + "RETURN 1e20; END$$ CALL ap()); RETURN '[' || x || ']'; END;",
                "[1e+20]"},
            {"DECLARE x VARCHAR DEFAULT (WITH ap AS PROCEDURE() RETURNS BOOLEAN LANGUAGE SQL AS $$BEGIN "
                + "RETURN TRUE; END$$ CALL ap()); BEGIN RETURN '[' || x || ']'; END;",
                "[true]"},
            {"DECLARE x VARCHAR; BEGIN x := (WITH ap AS PROCEDURE() RETURNS VARIANT LANGUAGE SQL AS "
                + "$$BEGIN RETURN TO_VARIANT('hi'); END$$ CALL ap()); RETURN '[' || x || ']'; END;",
                "[\"hi\"]"},
            {"BEGIN LET x := (WITH ap AS PROCEDURE() RETURNS FLOAT LANGUAGE SQL AS $$BEGIN RETURN 1e20; "
                + "END$$ CALL ap()); RETURN '[' || x || ']'; END;",
                "[1e+20]"},
            {"BEGIN LET x VARCHAR := (WITH ap AS PROCEDURE() RETURNS TIME LANGUAGE SQL AS $$BEGIN RETURN "
                + "'03:04:05'::TIME; END$$ CALL ap()); RETURN '[' || x || ']'; END;",
                "[03:04:05.000000000]"},
            {"BEGIN LET x := NULL; RETURN 1; END;",
                "REFUSED SQL compilation error: error line 1 at position 6 |  variable 'X' cannot have its "
                + "type inferred from initializer"},
            {"BEGIN EXECUTE IMMEDIATE 'BEGIN LET x := NULL; RETURN 1; END;'; RETURN 'ran'; EXCEPTION WHEN "
                + "OTHER THEN RETURN SQLCODE || '/' || SQLSTATE || '/' || SQLERRM; END;",
                "92228/P0000/SQL compilation error: error line 1 at position 6 |  variable 'X' cannot have "
                + "its type inferred from initializer"},
            {"DECLARE y INT := 1; BEGIN LET x := (SELECT :y); RETURN x; END;",
                "REFUSED SQL compilation error: error line 1 at position 26 |  variable 'X' cannot have its "
                + "type inferred from initializer"},
            {"BEGIN EXECUTE IMMEDIATE 'DECLARE y INT := 1; BEGIN LET x := (SELECT :y); RETURN x; END;'; "
                + "RETURN 'ran'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || '/' || SQLSTATE || '/' || SQLERRM;"
                + " END;",
                "92228/P0000/SQL compilation error: error line 1 at position 26 |  variable 'X' cannot have "
                + "its type inferred from initializer"},
            {"BEGIN LET x := (CALL f1()); RETURN x; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'f1'."},
            {"BEGIN EXECUTE IMMEDIATE 'BEGIN LET x := (CALL f1()); RETURN x; END;'; RETURN 'ran'; "
                + "EXCEPTION WHEN OTHER THEN RETURN SQLCODE || '/' || SQLSTATE || '/' || SQLERRM; END;",
                "1003/42000/SQL compilation error: | syntax error line 1 at position 21 unexpected 'f1'."},
            {"DECLARE x := (CALL f1()); BEGIN RETURN x; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'f1'."},
            {"BEGIN EXECUTE IMMEDIATE 'DECLARE x := (CALL f1()); BEGIN RETURN x; END;'; RETURN 'ran'; "
                + "EXCEPTION WHEN OTHER THEN RETURN SQLCODE || '/' || SQLSTATE || '/' || SQLERRM; END;",
                "1003/42000/SQL compilation error: | syntax error line 1 at position 19 unexpected 'f1'."},
            {"DECLARE x DEFAULT NULL; BEGIN RETURN 1; END;",
                "REFUSED SQL compilation error: error line 1 at position 8 |  variable 'X' cannot have its "
                + "type inferred from initializer"},
            {"BEGIN EXECUTE IMMEDIATE 'DECLARE x DEFAULT NULL; BEGIN RETURN 1; END;'; RETURN 'ran'; "
                + "EXCEPTION WHEN OTHER THEN RETURN SQLCODE || '/' || SQLSTATE || '/' || SQLERRM; END;",
                "92228/P0000/SQL compilation error: error line 1 at position 8 |  variable 'X' cannot have "
                + "its type inferred from initializer"},
            {"DECLARE y INT := 1; BEGIN LET x := (WITH ap AS PROCEDURE(a INT) RETURNS FLOAT LANGUAGE SQL "
                + "AS $$BEGIN RETURN a; END$$ CALL ap(:y)); RETURN x; END;",
                "REFUSED SQL compilation error: error line 1 at position 26 |  variable 'X' cannot have its "
                + "type inferred from initializer"},
            {"BEGIN EXECUTE IMMEDIATE 'DECLARE y INT := 1; BEGIN LET x := (WITH ap AS PROCEDURE(a INT) "
                + "RETURNS FLOAT LANGUAGE SQL AS $$BEGIN RETURN a; END$$ CALL ap(:y)); RETURN x; END;'; RETURN "
                + "'ran'; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || '/' || SQLSTATE || '/' || SQLERRM; END;",
                "92228/P0000/SQL compilation error: error line 1 at position 26 |  variable 'X' cannot have "
                + "its type inferred from initializer"},
            {"BEGIN LET x := PARSE_JSON('1'); RETURN 1; END;",
                "REFUSED SQL compilation error: error line 1 at position 6 |  variable 'X' cannot have its "
                + "type inferred from initializer"},
            {"BEGIN EXECUTE IMMEDIATE 'BEGIN LET x := PARSE_JSON(''1''); RETURN 1; END;'; RETURN 'ran'; "
                + "EXCEPTION WHEN OTHER THEN RETURN SQLCODE || '/' || SQLSTATE || '/' || SQLERRM; END;",
                "92228/P0000/SQL compilation error: error line 1 at position 6 |  variable 'X' cannot have "
                + "its type inferred from initializer"},
            {"BEGIN LET x := (SELECT 1, 2); RETURN 1; END;",
                "REFUSED SQL compilation error: error line 1 at position 6 |  variable 'X' cannot have its "
                + "type inferred from initializer"},
            {"BEGIN EXECUTE IMMEDIATE 'BEGIN LET x := (SELECT 1, 2); RETURN 1; END;'; RETURN 'ran'; "
                + "EXCEPTION WHEN OTHER THEN RETURN SQLCODE || '/' || SQLSTATE || '/' || SQLERRM; END;",
                "92228/P0000/SQL compilation error: error line 1 at position 6 |  variable 'X' cannot have "
                + "its type inferred from initializer"},
            {"DECLARE x; BEGIN RETURN 1; END;",
                "REFUSED SQL compilation error: error line 1 at position 8 |  variable 'X' must have either a"
                + " type or an initializer to infer a type from"},
            {"BEGIN EXECUTE IMMEDIATE 'DECLARE x; BEGIN RETURN 1; END;'; RETURN 'ran'; EXCEPTION WHEN "
                + "OTHER THEN RETURN SQLCODE || '/' || SQLSTATE || '/' || SQLERRM; END;",
                "92229/P0000/SQL compilation error: error line 1 at position 8 |  variable 'X' must have "
                + "either a type or an initializer to infer a type from"},
        });
    }

    /** A RETURN of the answer a NUMBER or a VARIANT name holds answers in the answer's own type. */
    @Test
    public void heldAnswersReturnInTheirOwnType() {
        engine.execute("CREATE OR REPLACE PROCEDURE p_num() RETURNS NUMBER(10,2) LANGUAGE SQL AS $$ BEGIN RETURN "
            + "12.5; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE p_int() RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN 42; END "
            + "$$");
        engine.execute("CREATE OR REPLACE PROCEDURE p_flt() RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN 1e20; END "
            + "$$");
        engine.execute("CREATE OR REPLACE PROCEDURE p_flt2() RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN 1.5e-7; "
            + "END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE p_flt3() RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN "
            + "2.5::FLOAT; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE p_nan() RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'NaN'::FLOAT; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE p_inf() RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'-inf'::FLOAT; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE p_bool() RETURNS BOOLEAN LANGUAGE SQL AS $$ BEGIN RETURN TRUE; "
            + "END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE p_date() RETURNS DATE LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'2024-01-02'::DATE; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE p_time() RETURNS TIME LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'03:04:05.123'::TIME; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE p_ntz() RETURNS TIMESTAMP_NTZ LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'2024-01-02 03:04:05'::TIMESTAMP_NTZ; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE p_ltz() RETURNS TIMESTAMP_LTZ LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'2024-01-02 03:04:05'::TIMESTAMP_LTZ; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE p_tz() RETURNS TIMESTAMP_TZ LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'2024-01-02 03:04:05 +02:00'::TIMESTAMP_TZ; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE p_var() RETURNS VARIANT LANGUAGE SQL AS $$ BEGIN RETURN "
            + "PARSE_JSON('{\"a\":1,\"b\":[1,2]}'); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE p_vstr() RETURNS VARIANT LANGUAGE SQL AS $$ BEGIN RETURN "
            + "TO_VARIANT('hi'); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE p_arr() RETURNS ARRAY LANGUAGE SQL AS $$ BEGIN RETURN "
            + "ARRAY_CONSTRUCT(1,'x',NULL); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE p_obj() RETURNS OBJECT LANGUAGE SQL AS $$ BEGIN RETURN "
            + "OBJECT_CONSTRUCT('k','v','n',2); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE p_bin() RETURNS BINARY LANGUAGE SQL AS $$ BEGIN RETURN "
            + "TO_BINARY('ABCD','HEX'); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE p_vc() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'abc'; "
            + "END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE f1() RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN 0.1; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE f2() RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN 1/3; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE f3() RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN 1e15; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE f4() RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN 1e16; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE f5() RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN 123456.789; "
            + "END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE f6() RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN -0.0::FLOAT; "
            + "END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE f7() RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN 1e-5; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE f8() RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN "
            + "12345678901234567890.0::FLOAT; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE f9() RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN 100::FLOAT; "
            + "END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE f10() RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN 1e-4; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE f11() RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN "
            + "0.000123456789; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE t9() RETURNS TIME(9) LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'03:04:05.123456789'::TIME(9); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE t0() RETURNS TIME(0) LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'03:04:05'::TIME(0); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE n3() RETURNS TIMESTAMP_NTZ(3) LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'2024-01-02 03:04:05.5'::TIMESTAMP_NTZ(3); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE tzn() RETURNS TIMESTAMP_TZ LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'2024-01-02 03:04:05 -05:30'::TIMESTAMP_TZ; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE ltz2() RETURNS TIMESTAMP_LTZ LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'2024-07-02 03:04:05.25'::TIMESTAMP_LTZ; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE vnum() RETURNS VARIANT LANGUAGE SQL AS $$ BEGIN RETURN "
            + "TO_VARIANT(1.5::FLOAT); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE vnull() RETURNS VARIANT LANGUAGE SQL AS $$ BEGIN RETURN "
            + "PARSE_JSON('null'); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE vbool() RETURNS VARIANT LANGUAGE SQL AS $$ BEGIN RETURN "
            + "TO_VARIANT(TRUE); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE vts() RETURNS VARIANT LANGUAGE SQL AS $$ BEGIN RETURN "
            + "TO_VARIANT('2024-01-02 03:04:05'::TIMESTAMP_NTZ); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE vemp() RETURNS OBJECT LANGUAGE SQL AS $$ BEGIN RETURN "
            + "OBJECT_CONSTRUCT(); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE bfalse() RETURNS BOOLEAN LANGUAGE SQL AS $$ BEGIN RETURN FALSE; "
            + "END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE n0() RETURNS NUMBER(38,0) LANGUAGE SQL AS $$ BEGIN RETURN 7; END"
            + " $$");
        engine.execute("CREATE OR REPLACE PROCEDURE n5() RETURNS NUMBER(12,5) LANGUAGE SQL AS $$ BEGIN RETURN -1.5; "
            + "END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE dnull() RETURNS DATE LANGUAGE SQL AS $$ BEGIN RETURN NULL; END "
            + "$$");
        engine.execute("CREATE OR REPLACE PROCEDURE vc5() RETURNS VARCHAR(5) LANGUAGE SQL AS $$ BEGIN RETURN "
            + "'abcde'; END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE bn() RETURNS BINARY LANGUAGE SQL AS $$ BEGIN RETURN "
            + "TO_BINARY('','HEX'); END $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pvar() RETURNS VARIANT LANGUAGE SQL AS $$ BEGIN RETURN "
            + "PARSE_JSON('{\"a\":1,\"b\":[1,2]}'); END $$");
        engine.execute("ALTER SESSION SET TIMEZONE='America/Los_Angeles'");
        engine.execute("DECLARE x NUMBER(20,4); BEGIN x := (CALL p_num()); RETURN x; END;");
        assertCells(new String[][] {
            {RESCAN,
                "NUMBER(10,2)[SB8] 12.50"},
        });
        engine.execute("DECLARE x VARIANT; BEGIN x := (CALL p_num()); RETURN x; END;");
        assertCells(new String[][] {
            {RESCAN,
                "NUMBER(10,2)[SB8] 12.50"},
        });
        engine.execute("DECLARE x NUMBER(20,4); BEGIN x := (CALL p_int()); RETURN x; END;");
        assertCells(new String[][] {
            {RESCAN,
                "NUMBER(38,0)[SB16] 42"},
        });
        engine.execute("DECLARE x VARIANT; BEGIN x := (CALL p_int()); RETURN x; END;");
        assertCells(new String[][] {
            {RESCAN,
                "NUMBER(38,0)[SB16] 42"},
        });
        engine.execute("DECLARE x NUMBER(20,4); BEGIN x := (CALL p_flt()); RETURN x; END;");
        assertCells(new String[][] {
            {RESCAN,
                "FLOAT[DOUBLE] 1e+20"},
        });
        engine.execute("DECLARE x VARIANT; BEGIN x := (CALL p_flt()); RETURN x; END;");
        assertCells(new String[][] {
            {RESCAN,
                "FLOAT[DOUBLE] 1e+20"},
        });
        engine.execute("DECLARE x NUMBER(20,4); BEGIN x := (CALL p_flt2()); RETURN x; END;");
        assertCells(new String[][] {
            {RESCAN,
                "FLOAT[DOUBLE] 1.5e-07"},
        });
        engine.execute("DECLARE x VARIANT; BEGIN x := (CALL p_flt2()); RETURN x; END;");
        assertCells(new String[][] {
            {RESCAN,
                "FLOAT[DOUBLE] 1.5e-07"},
        });
        engine.execute("DECLARE x NUMBER(20,4); BEGIN x := (CALL p_flt3()); RETURN x; END;");
        assertCells(new String[][] {
            {RESCAN,
                "FLOAT[DOUBLE] 2.5"},
        });
        engine.execute("DECLARE x VARIANT; BEGIN x := (CALL p_flt3()); RETURN x; END;");
        assertCells(new String[][] {
            {RESCAN,
                "FLOAT[DOUBLE] 2.5"},
        });
        engine.execute("DECLARE x NUMBER(20,4); BEGIN x := (CALL p_nan()); RETURN x; END;");
        assertCells(new String[][] {
            {RESCAN,
                "FLOAT[DOUBLE] NaN"},
        });
        engine.execute("DECLARE x VARIANT; BEGIN x := (CALL p_nan()); RETURN x; END;");
        assertCells(new String[][] {
            {RESCAN,
                "FLOAT[DOUBLE] NaN"},
        });
        engine.execute("DECLARE x NUMBER(20,4); BEGIN x := (CALL p_inf()); RETURN x; END;");
        assertCells(new String[][] {
            {RESCAN,
                "FLOAT[DOUBLE] -inf"},
        });
        engine.execute("DECLARE x VARIANT; BEGIN x := (CALL p_inf()); RETURN x; END;");
        assertCells(new String[][] {
            {RESCAN,
                "FLOAT[DOUBLE] -inf"},
        });
        engine.execute("DECLARE x NUMBER(20,4); BEGIN x := (CALL p_bool()); RETURN x; END;");
        assertCells(new String[][] {
            {RESCAN,
                "BOOLEAN[SB1] true"},
        });
        engine.execute("DECLARE x VARIANT; BEGIN x := (CALL p_bool()); RETURN x; END;");
        assertCells(new String[][] {
            {RESCAN,
                "BOOLEAN[SB1] true"},
        });
        engine.execute("DECLARE x NUMBER(20,4); BEGIN x := (CALL p_date()); RETURN x; END;");
        assertCells(new String[][] {
            {RESCAN,
                "DATE[SB4] 2024-01-02"},
        });
        engine.execute("DECLARE x VARIANT; BEGIN x := (CALL p_date()); RETURN x; END;");
        assertCells(new String[][] {
            {RESCAN,
                "DATE[SB4] 2024-01-02"},
        });
        engine.execute("DECLARE x NUMBER(20,4); BEGIN x := (CALL p_time()); RETURN x; END;");
        assertCells(new String[][] {
            {RESCAN,
                "TIME(9)[SB8] 03:04:05"},
        });
        engine.execute("DECLARE x VARIANT; BEGIN x := (CALL p_time()); RETURN x; END;");
        assertCells(new String[][] {
            {RESCAN,
                "TIME(9)[SB8] 03:04:05"},
        });
        engine.execute("DECLARE x NUMBER(20,4); BEGIN x := (CALL p_ntz()); RETURN x; END;");
        assertCells(new String[][] {
            {RESCAN,
                "TIMESTAMP_NTZ(9)[SB16] 2024-01-02 03:04:05.000"},
        });
        engine.execute("DECLARE x VARIANT; BEGIN x := (CALL p_ntz()); RETURN x; END;");
        assertCells(new String[][] {
            {RESCAN,
                "TIMESTAMP_NTZ(9)[SB16] 2024-01-02 03:04:05.000"},
        });
        engine.execute("DECLARE x NUMBER(20,4); BEGIN x := (CALL p_ltz()); RETURN x; END;");
        assertCells(new String[][] {
            {RESCAN,
                "TIMESTAMP_LTZ(9)[SB16] 2024-01-02 03:04:05.000 -0800"},
        });
        engine.execute("DECLARE x VARIANT; BEGIN x := (CALL p_ltz()); RETURN x; END;");
        assertCells(new String[][] {
            {RESCAN,
                "TIMESTAMP_LTZ(9)[SB16] 2024-01-02 03:04:05.000 -0800"},
        });
        engine.execute("DECLARE x NUMBER(20,4); BEGIN x := (CALL p_tz()); RETURN x; END;");
        assertCells(new String[][] {
            {RESCAN,
                "TIMESTAMP_TZ(9)[SB16] 2024-01-02 03:04:05.000 +0200"},
        });
        engine.execute("DECLARE x VARIANT; BEGIN x := (CALL p_tz()); RETURN x; END;");
        assertCells(new String[][] {
            {RESCAN,
                "TIMESTAMP_TZ(9)[SB16] 2024-01-02 03:04:05.000 +0200"},
        });
        engine.execute("DECLARE x NUMBER(20,4); BEGIN x := (CALL p_vstr()); RETURN x; END;");
        assertCells(new String[][] {
            {RESCAN,
                "VARIANT[LOB] hi"},
        });
        engine.execute("DECLARE x VARIANT; BEGIN x := (CALL p_vstr()); RETURN x; END;");
        assertCells(new String[][] {
            {RESCAN,
                "VARIANT[LOB] hi"},
        });
        engine.execute("DECLARE x NUMBER(20,4); BEGIN x := (CALL p_bin()); RETURN x; END;");
        assertCells(new String[][] {
            {RESCAN,
                "BINARY(67108864)[LOB] ABCD"},
        });
        engine.execute("DECLARE x VARIANT; BEGIN x := (CALL p_bin()); RETURN x; END;");
        assertCells(new String[][] {
            {RESCAN,
                "BINARY(67108864)[LOB] ABCD"},
        });
        engine.execute("DECLARE x NUMBER(20,4); BEGIN x := (CALL p_vc()); RETURN x; END;");
        assertCells(new String[][] {
            {RESCAN,
                "VARCHAR(134217728)[LOB] abc"},
        });
        engine.execute("DECLARE x VARIANT; BEGIN x := (CALL p_vc()); RETURN x; END;");
        assertCells(new String[][] {
            {RESCAN,
                "VARCHAR(134217728)[LOB] abc"},
        });
    }

    @AfterEach
    public void restoreSession() {
        engine.execute("ALTER SESSION UNSET TIMEZONE, JSON_INDENT");
    }
}
