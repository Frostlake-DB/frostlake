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

package dev.frostlake.functions;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * ARRAY_SORT, ARRAY_MIN and ARRAY_MAX order mixed-type elements as VARIANT values order under ORDER BY: by type first
 * (BOOLEAN, NUMBER, STRING, OBJECT, ARRAY, JSON null), then by value within the type; the nulls_first flag moves only
 * undefined elements. Live-verified cells.
 */
public class ArrayVariantOrderTest extends MeasuredCellsTestSupport {

    @Test
    public void mixedTypesOrderAsVariants() {
        assertCells(new String[][] {
            {"SELECT TO_JSON(ARRAY_SORT(ARRAY_CONSTRUCT('a', TRUE, 5, '!', PARSE_JSON('null'), "
                + "OBJECT_CONSTRUCT('k',1), ARRAY_CONSTRUCT(2), FALSE, -3, NULL)))",
                "[false,true,-3,5,\"!\",\"a\",{\"k\":1},[2],null,undefined]"},
            {"SELECT TO_JSON(ARRAY_SORT(ARRAY_CONSTRUCT('a', TRUE, 5, '!', PARSE_JSON('null'), "
                + "OBJECT_CONSTRUCT('k',1), ARRAY_CONSTRUCT(2), FALSE, -3, NULL), FALSE))",
                "[undefined,null,[2],{\"k\":1},\"a\",\"!\",5,-3,true,false]"},
            {"SELECT TO_JSON(ARRAY_SORT(ARRAY_CONSTRUCT('a', TRUE, 5, '!', PARSE_JSON('null'), "
                + "OBJECT_CONSTRUCT('k',1), ARRAY_CONSTRUCT(2), FALSE, -3, NULL), TRUE, TRUE))",
                "[undefined,false,true,-3,5,\"!\",\"a\",{\"k\":1},[2],null]"},
            {"SELECT TO_JSON(ARRAY_SORT(ARRAY_CONSTRUCT('a', TRUE, 5, '!', PARSE_JSON('null'), "
                + "OBJECT_CONSTRUCT('k',1), ARRAY_CONSTRUCT(2), FALSE, -3, NULL), FALSE, FALSE))",
                "[null,[2],{\"k\":1},\"a\",\"!\",5,-3,true,false,undefined]"},
            {"SELECT TO_JSON(ARRAY_SORT(ARRAY_CONSTRUCT(TRUE, PARSE_JSON('null'), 1)))",
                "[true,1,null]"},
            {"SELECT TO_JSON(ARRAY_SORT(ARRAY_CONSTRUCT('a', TRUE, 5, '!')))",
                "[true,5,\"!\",\"a\"]"},
            {"SELECT TO_JSON(ARRAY_SORT(ARRAY_CONSTRUCT(ARRAY_CONSTRUCT(1), OBJECT_CONSTRUCT('a',1), "
                + "ARRAY_CONSTRUCT(0,5), OBJECT_CONSTRUCT('b',0), OBJECT_CONSTRUCT())))",
                "[{},{\"b\":0},{\"a\":1},[0,5],[1]]"},
            {"SELECT TO_JSON(ARRAY_SORT(ARRAY_CONSTRUCT(10, 9, 1.5, -2, 1e3::FLOAT)))",
                "[-2,1.5,9,10,1.000000000000000e+03]"},
            {"SELECT TO_JSON(ARRAY_SORT(ARRAY_CONSTRUCT('10', '9', 'Z', 'a', '')))",
                "[\"\",\"10\",\"9\",\"Z\",\"a\"]"},
            {"SELECT TO_JSON(ARRAY_SORT(ARRAY_CONSTRUCT('10', 9, '9', 10)))",
                "[9,10,\"10\",\"9\"]"},
            {"SELECT TO_JSON(ARRAY_SORT(PARSE_JSON('[\"b\", 2, true, null, {\"z\":1}, [0], false, 1.5, "
                + "\"A\"]')))",
                "[false,true,1.5,2,\"A\",\"b\",{\"z\":1},[0],null]"},
            {"SELECT TO_JSON(ARRAY_SORT(PARSE_JSON('[\"b\", 2, true, null, {\"z\":1}, [0], false, 1.5, "
                + "\"A\"]'), FALSE))",
                "[null,[0],{\"z\":1},\"b\",\"A\",2,1.5,true,false]"},
            {"SELECT TO_JSON(ARRAY_SORT(ARRAY_CONSTRUCT('2024-01-02'::DATE, 'x', 3, '2023-05-01'::DATE)))",
                "[3,\"2023-05-01\",\"2024-01-02\",\"x\"]"},
            {"SELECT TO_JSON(ARRAY_MAX(ARRAY_CONSTRUCT(10, TRUE)))",
                "10"},
            {"SELECT TO_JSON(ARRAY_MIN(ARRAY_CONSTRUCT(10, TRUE)))",
                "true"},
            {"SELECT TO_JSON(ARRAY_MAX(ARRAY_CONSTRUCT(5, '!')))",
                "\"!\""},
            {"SELECT TO_JSON(ARRAY_MIN(ARRAY_CONSTRUCT(5, '!')))",
                "5"},
            {"SELECT TO_JSON(ARRAY_MIN(ARRAY_CONSTRUCT('a', TRUE)))",
                "true"},
            {"SELECT TO_JSON(ARRAY_MAX(ARRAY_CONSTRUCT(OBJECT_CONSTRUCT('a',1), ARRAY_CONSTRUCT(1))))",
                "[1]"},
            {"SELECT TO_JSON(ARRAY_MIN(ARRAY_CONSTRUCT(OBJECT_CONSTRUCT('a',1), ARRAY_CONSTRUCT(1))))",
                "{\"a\":1}"},
            {"SELECT TO_JSON(ARRAY_MAX(ARRAY_CONSTRUCT('a', TRUE, 5, OBJECT_CONSTRUCT('k',1), NULL)))",
                "{\"k\":1}"},
            {"SELECT TO_JSON(ARRAY_MIN(ARRAY_CONSTRUCT('a', TRUE, 5, OBJECT_CONSTRUCT('k',1), NULL)))",
                "true"},
            {"SELECT TO_JSON(ARRAY_MAX(PARSE_JSON('[\"b\", 2, true, {\"z\":1}, [0], false]')))",
                "[0]"},
            {"SELECT TO_JSON(ARRAY_MIN(PARSE_JSON('[\"b\", 2, true, {\"z\":1}, [0], false]')))",
                "false"},
            {"SELECT TO_JSON(ARRAY_MAX(PARSE_JSON('[1,null,\"z\"]')))",
                "null"},
            {"SELECT TO_JSON(ARRAY_MIN(ARRAY_CONSTRUCT(TRUE, FALSE)))",
                "false"},
            {"SELECT TO_JSON(ARRAY_MAX(ARRAY_CONSTRUCT('10', 9)))",
                "\"10\""},
            {"SELECT TYPEOF(ARRAY_MAX(ARRAY_CONSTRUCT(10, TRUE)))",
                "INTEGER"},
            {"SELECT TO_JSON(ARRAY_MAX(ARRAY_CONSTRUCT('2024-01-02'::DATE, 'x', 3)))",
                "\"x\""},
        });
    }
}
