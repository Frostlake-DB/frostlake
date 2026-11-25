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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * A JavaScript procedure's answer given to a VARCHAR name, {@code x := (CALL p())}, is held as the same fixed text a
 * SQL procedure's is: a FLOAT {@code 1e+20}, a BOOLEAN {@code true}, a TIMESTAMP_NTZ made of a Date the session's wall
 * clock with nine fractional digits, a VARIANT its JSON at the JSON_INDENT in force (live-verified).
 */
public class JavaScriptCallAnswerTextTest extends MeasuredCellsTestSupport {

    @Test
    public void answersHeldByAVarcharName() {
        engine.execute("CREATE OR REPLACE PROCEDURE j_flt() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ return 1e20; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE j_bool() RETURNS BOOLEAN LANGUAGE JAVASCRIPT AS $$ return true; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE j_ntz() RETURNS TIMESTAMP_NTZ LANGUAGE JAVASCRIPT AS $$ return "
            + "new Date(Date.UTC(2024,0,2,3,4,5)); $$");
        engine.execute("CREATE OR REPLACE PROCEDURE j_var() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return "
            + "{a:1,b:[1,2]}; $$");
        engine.execute("ALTER SESSION SET TIMEZONE='America/Los_Angeles'");
        engine.execute("ALTER SESSION SET JSON_INDENT=2");
        assertCells(new String[][] {
            {"DECLARE x VARCHAR; BEGIN x := (CALL j_flt()); RETURN '[' || x || ']'; END;",
                "[1e+20]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL j_bool()); RETURN '[' || x || ']'; END;",
                "[true]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL j_ntz()); RETURN '[' || x || ']'; END;",
                "[2024-01-01 19:04:05.000000000]"},
            {"DECLARE x VARCHAR; BEGIN x := (CALL j_var()); RETURN '[' || x || ']'; END;",
                "[{ |   \"a\": 1, |   \"b\": [ |     1, |     2 |   ] | }]"},
        });
    }

    @AfterEach
    public void restoreSession() {
        engine.execute("ALTER SESSION UNSET TIMEZONE, JSON_INDENT");
    }
}
