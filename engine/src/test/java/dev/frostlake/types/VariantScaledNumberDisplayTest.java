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

package dev.frostlake.types;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * A VARIANT of a scaled NUMBER displays without the zeros of its scale - 2.500 out of a NUMBER(10,3) is 2.5 through the
 * cast, its text, TO_JSON, TO_VARCHAR, a VARIANT column and a block's RETURN - while ARRAY_TO_STRING writes each member
 * read from a scaled NUMBER at that scale, 2.500 (live-verified).
 */
public class VariantScaledNumberDisplayTest extends MeasuredCellsTestSupport {

    @Test
    public void scaledNumberVariantDisplay() {
        engine.execute("CREATE TABLE t (a ARRAY, v VARIANT, n NUMBER(10,3), f VARIANT)");
        engine.execute("INSERT INTO t SELECT ARRAY_CONSTRUCT(2.500::NUMBER(10,3)), 2.500::NUMBER(10,3), 2.5, "
            + "2.500::NUMBER(10,3)::VARIANT");
        engine.execute("UPDATE t SET f = n");
        engine.execute("CREATE TABLE t5 (v VARIANT)");
        engine.execute("INSERT INTO t5 (v) VALUES (2.000::NUMBER(10,3))");
        assertCells(new String[][] {
            {"SELECT 2.500::NUMBER(10,3)::VARIANT",
                "2.5"},
            {"SELECT 2.500::NUMBER(10,3)::VARIANT::VARCHAR",
                "2.5"},
            {"SELECT TO_JSON(2.500::NUMBER(10,3)::VARIANT)",
                "2.5"},
            {"SELECT TO_VARIANT(2.500::NUMBER(10,3))",
                "2.5"},
            {"SELECT TYPEOF(2.500::NUMBER(10,3)::VARIANT)",
                "DECIMAL"},
            {"SELECT 2.000::NUMBER(10,3)::VARIANT",
                "2"},
            {"SELECT TO_VARCHAR(2.500::NUMBER(10,3)::VARIANT)",
                "2.5"},
            {"SELECT 2.500::NUMBER(10,3)::VARIANT::NUMBER(10,3)",
                "2.500"},
            {"SELECT 1.23456789012345678901234567890::NUMBER(38,29)::VARIANT::VARCHAR",
                "1.2345678901234567890123456789"},
            {"SELECT 12345678901234567890.100::NUMBER(38,3)::VARIANT::VARCHAR",
                "12345678901234567890.1"},
            {"SELECT TO_JSON(12345678901234567890.100::NUMBER(38,3)::VARIANT)",
                "12345678901234567890.1"},
            {"SELECT CAST(2.500::NUMBER(10,3) AS VARIANT)",
                "2.5"},
            {"SELECT 2.500::DECIMAL(10,3)::VARIANT",
                "2.5"},
            {"SELECT IFF(TRUE, 2.500::NUMBER(10,3)::VARIANT, NULL)",
                "2.5"},
            {"SELECT STRIP_NULL_VALUE(2.500::NUMBER(10,3)::VARIANT)",
                "2.5"},
            {"SELECT ARRAY_CONSTRUCT(2.500::NUMBER(10,3))::VARCHAR",
                "[2.5]"},
            {"SELECT TO_JSON(OBJECT_CONSTRUCT('k', 2.500::NUMBER(10,3)))",
                "{\"k\":2.5}"},
            {"EXECUTE IMMEDIATE $$ BEGIN RETURN 2.500::NUMBER(10,3)::VARIANT; END; $$",
                "2.5"},
            {"SELECT ARRAY_TO_STRING(ARRAY_CONSTRUCT(2.500::NUMBER(10,3)), ',')",
                "2.500"},
            {"SELECT ARRAY_TO_STRING(ARRAY_CONSTRUCT(2.000::NUMBER(10,3)), ',')",
                "2.000"},
            {"SELECT ARRAY_TO_STRING(PARSE_JSON('[2.500]'), ',')",
                "2.5"},
            {"SELECT ARRAY_TO_STRING(ARRAY_CONSTRUCT(2.500::NUMBER(10,3), 1.10::NUMBER(5,2), 3), ',')",
                "2.500,1.10,3"},
            {"SELECT ARRAY_TO_STRING(ARRAY_CONSTRUCT(2.500::NUMBER(10,3)::VARIANT), ',')",
                "2.500"},
            {"SELECT ARRAY_TO_STRING([2.500::NUMBER(10,3)], ',')",
                "2.500"},
            {"SELECT ARRAY_TO_STRING(ARRAY_AGG(x), ',') FROM (SELECT 2.500::NUMBER(10,3) x)",
                "2.500"},
            {"SELECT ARRAY_TO_STRING(TO_ARRAY(2.500::NUMBER(10,3)), ',')",
                "2.500"},
            {"SELECT ARRAY_TO_STRING(ARRAY_CONSTRUCT(0.10::NUMBER(4,2)), '|')",
                "0.10"},
            {"SELECT ARRAY_CONSTRUCT(2.500::NUMBER(10,3))[0]::VARCHAR",
                "2.5"},
            {"SELECT v FROM t",
                "2.5"},
            {"SELECT v::VARCHAR FROM t",
                "2.5"},
            {"SELECT TO_JSON(v) FROM t",
                "2.5"},
            {"SELECT TYPEOF(v) FROM t",
                "DECIMAL"},
            {"SELECT n::VARIANT FROM t",
                "2.5"},
            {"SELECT ARRAY_TO_STRING(a, ',') FROM t",
                "2.500"},
            {"SELECT ARRAY_TO_STRING(ARRAY_CONSTRUCT(n), ',') FROM t",
                "2.500"},
            {"SELECT f FROM t",
                "2.5"},
            {"SELECT v FROM t5",
                "2"},
        });
    }
}
