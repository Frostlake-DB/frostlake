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
 * A Java handler's {@code String[]} element is a scaled NUMBER written at the scale its type gives it, wherever the
 * array comes from - ARRAY_CONSTRUCT, a stored ARRAY column, ARRAY_AGG, a VARIANT of the number - while a literal, a
 * parsed JSON number and an object's member keep none, and every other surface reads the descaled number (all
 * live-verified).
 */
public class JavaArrayElementScaleTest extends MeasuredCellsTestSupport {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE FUNCTION jfa984(a ARRAY) RETURNS VARCHAR LANGUAGE JAVA HANDLER = 'H984.run' "
            + "AS $$ class H984 { public static String run(String[] a) { if (a == null) { return \"null\"; } "
            + "StringBuilder b = new StringBuilder(); for (String s : a) { b.append('[').append(s).append(']'); } "
            + "return b.toString(); } } $$");
        engine.execute("CREATE OR REPLACE TABLE ta984 (a ARRAY)");
        engine.execute("INSERT INTO ta984 SELECT ARRAY_CONSTRUCT(12.50::NUMBER(10,2), 3.00::NUMBER(10,2), "
            + "1.5::NUMBER(5,3))");
    }

    @Test
    public void anElementAtItsTypesScale() {
        assertCells(new String[][] {
            {"SELECT jfa984(ARRAY_CONSTRUCT(12.50::NUMBER(10,2), 3.00::NUMBER(10,2), 1.5::NUMBER(5,3), "
                + "7::NUMBER(10,2), 0.10::NUMBER(4,2)))", "[12.50][3.00][1.500][7.00][0.10]"},
            {"SELECT jfa984(a) FROM ta984", "[12.50][3.00][1.500]"},
            {"SELECT jfa984(PARSE_JSON('[12.50, 3.00, 1.500]')::ARRAY)", "[12.5][3][1.5]"},
            {"SELECT jfa984(ARRAY_AGG(x)) FROM (SELECT 12.50::NUMBER(10,2) AS x UNION ALL SELECT 3.00::NUMBER(10,2))",
                "[12.50][3.00]"},
            {"SELECT jfa984(ARRAY_CONSTRUCT(12.5, 12.50, 1.0))", "[12.5][12.5][1]"},
            {"SELECT jfa984(ARRAY_CONSTRUCT(OBJECT_CONSTRUCT('k', 12.50::NUMBER(10,2))))", "[{\"k\":12.5}]"},
            {"SELECT jfa984(ARRAY_CONSTRUCT(12.50::NUMBER(10,2)::VARIANT))", "[12.50]"},
            {"SELECT jfa984(ARRAY_CONSTRUCT(-0.50::NUMBER(10,2), 100::NUMBER(38,5)))", "[-0.50][100.00000]"},
        });
    }

    @Test
    public void everyOtherSurfaceReadsTheDescaledNumber() {
        assertCells(new String[][] {
            {"SELECT ARRAY_CONSTRUCT(12.50::NUMBER(10,2), 3.00::NUMBER(10,2))::VARCHAR", "[12.5,3]"},
            {"SELECT TYPEOF(ARRAY_CONSTRUCT(12.50::NUMBER(10,2))[0]), TYPEOF(ARRAY_CONSTRUCT(3.00::NUMBER(10,2))[0])",
                "DECIMAL|DECIMAL"},
            {"SELECT a::VARCHAR, TO_JSON(a) FROM ta984", "[12.5,3,1.5]|[12.5,3,1.5]"},
        });
    }
}
