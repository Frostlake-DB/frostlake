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
 * A JavaScript Date is a TIMESTAMP_LTZ(3): a session output format's FF without a digit count prints three digits of
 * it, in a procedure's VARIANT answer and a function's alike, while a procedure whose answer is declared TIMESTAMP_LTZ
 * prints its type's nine (live-verified).
 */
public class JavaScriptDateFractionScaleTest extends MeasuredCellsTestSupport {

    @Test
    public void aDatePrintsThreeDigitsOfABareFf() {
        engine.execute("ALTER SESSION SET TIMEZONE = 'Europe/Warsaw'");
        engine.execute("ALTER SESSION SET TIMESTAMP_LTZ_OUTPUT_FORMAT = 'YYYY-MM-DD HH24:MI:SS.FF'");
        try {
            engine.execute("CREATE OR REPLACE PROCEDURE ffp() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return {t: "
                + "new Date(Date.UTC(2024, 0, 2, 3, 4, 5, 6))}; $$");
            engine.execute("CREATE OR REPLACE PROCEDURE ffd() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return new "
                + "Date(Date.UTC(2024, 0, 2, 3, 4, 5, 6)); $$");
            engine.execute("CREATE OR REPLACE FUNCTION fff() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return {t: "
                + "new Date(Date.UTC(2024, 0, 2, 3, 4, 5, 6))}; $$");
            engine.execute("CREATE OR REPLACE PROCEDURE ffs() RETURNS TIMESTAMP_LTZ LANGUAGE JAVASCRIPT AS $$ "
                + "return new Date(Date.UTC(2024, 0, 2, 3, 4, 5, 6)); $$");
            assertCells(new String[][] {
                {"CALL ffp() ->> SELECT SPLIT_PART($1:t::VARCHAR, '.', 2), SPLIT_PART($1::VARCHAR, '.', 2) FROM $1",
                    "006|006\"}"},
                {"CALL ffd() ->> SELECT SPLIT_PART($1::VARCHAR, '.', 2) FROM $1", "006"},
                {"SELECT SPLIT_PART(fff()::VARCHAR, '.', 2), SPLIT_PART(TO_JSON(fff()), '.', 2)", "006\"}|006\"}"},
                {"CALL ffs() ->> SELECT SPLIT_PART($1::VARCHAR, '.', 2) FROM $1", "006000000"},
            });
        } finally {
            engine.execute("ALTER SESSION UNSET TIMESTAMP_LTZ_OUTPUT_FORMAT");
            engine.execute("ALTER SESSION UNSET TIMEZONE");
        }
    }
}
