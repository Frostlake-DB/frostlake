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

package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * A VARIANT DOUBLE beside an exact VARIANT number compares as a double, live-verified: {@code =}, {@code <>},
 * the ordering operators, IN lists, EQUAL_NULL, NULLIF, DECODE, CASE, members of arrays and objects,
 * ARRAY_CONTAINS, ARRAY_POSITION and ARRAY_REMOVE find 12345678901234567890 equal to the 1.2345678901234567e19 it
 * rounds to, while two exact numbers keep every digit and a DECFLOAT never equals a DOUBLE. DISTINCT, GROUP BY, the
 * set operations, an equi-join and a comparison against a subquery key the two exactly and keep them apart.
 */
public class VariantMixedNumberEqualityTest extends MeasuredCellsTestSupport {

    @Test
    public void scalarComparisons() {
        engine.execute("CREATE TABLE ja (v VARIANT)");
        engine.execute("CREATE TABLE jb (v VARIANT)");
        engine.execute("INSERT INTO ja SELECT PARSE_JSON('12345678901234567890')");
        engine.execute("INSERT INTO jb SELECT PARSE_JSON('1.2345678901234567e19')");
        assertCells(new String[][] {
            {"SELECT PARSE_JSON('12345678901234567890') = PARSE_JSON('1.2345678901234567e19')",
                "true"},
            {"SELECT PARSE_JSON('12345678901234567890') <> PARSE_JSON('1.2345678901234567e19')",
                "false"},
            {"SELECT PARSE_JSON('9007199254740993') = PARSE_JSON('9007199254740992')",
                "false"},
            {"SELECT PARSE_JSON('9007199254740993') = PARSE_JSON('9.007199254740992e15')",
                "true"},
            {"SELECT PARSE_JSON('9007199254740993') = PARSE_JSON('9007199254740993e0')",
                "true"},
            {"SELECT PARSE_JSON('9007199254740993') <> PARSE_JSON('9.007199254740992e15')",
                "false"},
            {"SELECT PARSE_JSON('0.1000000000000000055511151231257827') = PARSE_JSON('1e-1')",
                "true"},
            {"SELECT PARSE_JSON('0.30000000000000001') = PARSE_JSON('3e-1')",
                "true"},
            {"SELECT PARSE_JSON('0.1') = PARSE_JSON('1e-1')",
                "true"},
            {"SELECT PARSE_JSON('12345678901234567890') < PARSE_JSON('1.2345678901234567e19')",
                "false"},
            {"SELECT PARSE_JSON('12345678901234567890') > PARSE_JSON('1.2345678901234567e19')",
                "false"},
            {"SELECT PARSE_JSON('12345678901234567890') <= PARSE_JSON('1.2345678901234567e19')",
                "true"},
            {"SELECT PARSE_JSON('12345678901234567890') >= PARSE_JSON('1.2345678901234567e19')",
                "true"},
            {"SELECT PARSE_JSON('12345678901234567890') IN (PARSE_JSON('1.2345678901234567e19'))",
                "true"},
            {"SELECT PARSE_JSON('12345678901234567890') NOT IN (PARSE_JSON('1.2345678901234567e19'))",
                "false"},
            {"SELECT PARSE_JSON('12345678901234567890') IN (PARSE_JSON('1'), "
                + "PARSE_JSON('1.2345678901234567e19'))",
                "true"},
            {"SELECT EQUAL_NULL(PARSE_JSON('12345678901234567890'), PARSE_JSON('1.2345678901234567e19'))",
                "true"},
            {"SELECT PARSE_JSON('12345678901234567890') IS DISTINCT FROM "
                + "PARSE_JSON('1.2345678901234567e19')",
                "false"},
            {"SELECT CASE PARSE_JSON('12345678901234567890') WHEN PARSE_JSON('1.2345678901234567e19') THEN"
                + " 'y' ELSE 'n' END",
                "y"},
            {"SELECT DECODE(PARSE_JSON('12345678901234567890'), PARSE_JSON('1.2345678901234567e19'), 'y', "
                + "'n')",
                "y"},
            {"SELECT IFF(PARSE_JSON('12345678901234567890') = PARSE_JSON('1.2345678901234567e19'), 'y', "
                + "'n')",
                "y"},
            {"SELECT NULLIF(PARSE_JSON('12345678901234567890'), PARSE_JSON('1.2345678901234567e19'))",
                "null"},
            {"SELECT PARSE_JSON('[12345678901234567890]') = PARSE_JSON('[1.2345678901234567e19]')",
                "true"},
            {"SELECT PARSE_JSON('{\"a\":12345678901234567890}') = "
                + "PARSE_JSON('{\"a\":1.2345678901234567e19}')",
                "true"},
            {"SELECT ARRAY_CONTAINS(PARSE_JSON('12345678901234567890'), "
                + "PARSE_JSON('[1.2345678901234567e19]')::ARRAY)",
                "true"},
            {"SELECT ARRAY_POSITION(PARSE_JSON('12345678901234567890'), "
                + "PARSE_JSON('[1.2345678901234567e19]')::ARRAY)",
                "0"},
            {"SELECT TO_VARIANT(12345678901234567890) = TO_VARIANT(1.2345678901234567e19::FLOAT)",
                "true"},
            {"SELECT TO_VARIANT(9007199254740993) = TO_VARIANT(9007199254740992::FLOAT)",
                "true"},
            {"SELECT TO_VARIANT(0.1::NUMBER(38,20)) = TO_VARIANT(0.1::FLOAT)",
                "true"},
            {"SELECT TO_VARIANT(9007199254740993) = TO_VARIANT(9007199254740993)",
                "true"},
            {"SELECT TO_VARIANT(9007199254740993) = TO_VARIANT(9007199254740992)",
                "false"},
            {"SELECT PARSE_JSON('12345678901234567890') = 1.2345678901234567e19::FLOAT",
                "true"},
            {"SELECT PARSE_JSON('1.2345678901234567e19') = 12345678901234567890",
                "true"},
            {"SELECT PARSE_JSON('9007199254740993') = 9007199254740992::FLOAT",
                "true"},
            {"SELECT COUNT(DISTINCT v) FROM (SELECT PARSE_JSON('12345678901234567890') v UNION ALL SELECT "
                + "PARSE_JSON('1.2345678901234567e19'))",
                "2"},
            {"SELECT COUNT(*) FROM (SELECT DISTINCT v FROM (SELECT PARSE_JSON('12345678901234567890') v "
                + "UNION ALL SELECT PARSE_JSON('1.2345678901234567e19')))",
                "2"},
            {"SELECT COUNT(*) FROM (SELECT v FROM (SELECT PARSE_JSON('12345678901234567890') v UNION ALL "
                + "SELECT PARSE_JSON('1.2345678901234567e19')) GROUP BY v)",
                "2"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('12345678901234567890') v UNION SELECT "
                + "PARSE_JSON('1.2345678901234567e19'))",
                "2"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('12345678901234567890') v INTERSECT SELECT "
                + "PARSE_JSON('1.2345678901234567e19'))",
                "0"},
            {"SELECT COUNT(*) FROM (SELECT PARSE_JSON('12345678901234567890') v EXCEPT SELECT "
                + "PARSE_JSON('1.2345678901234567e19'))",
                "1"},
            {"SELECT COUNT(*) FROM ja JOIN jb ON ja.v = jb.v",
                "0"},
            {"SELECT COUNT(*) FROM ja, jb WHERE ja.v = jb.v",
                "0"},
            {"SELECT COUNT(*) FROM ja JOIN jb ON ja.v >= jb.v AND ja.v <= jb.v",
                "1"},
            {"SELECT COUNT(*) FROM ja WHERE v = (SELECT v FROM jb)",
                "0"},
            {"SELECT COUNT(*) FROM ja WHERE v IN (SELECT v FROM jb)",
                "0"},
            {"SELECT ja.v = jb.v FROM ja, jb",
                "true"},
        });
    }

    @Test
    public void containersAndArrays() {
        assertCells(new String[][] {
            {"SELECT TO_VARIANT(CAST('12345678901234567890' AS DECFLOAT)) = "
                + "PARSE_JSON('1.2345678901234567e19')",
                "false"},
            {"SELECT TO_VARIANT(CAST('1.5' AS DECFLOAT)) = PARSE_JSON('1.5e0')",
                "false"},
            {"SELECT TO_VARIANT(CAST('1.5' AS DECFLOAT)) = TO_VARIANT(1.5::FLOAT)",
                "false"},
            {"SELECT PARSE_JSON('-0.0e0') = PARSE_JSON('0')",
                "true"},
            {"SELECT PARSE_JSON('[-0.0e0]') = PARSE_JSON('[0]')",
                "true"},
            {"SELECT ARRAYS_OVERLAP(PARSE_JSON('[12345678901234567890]')::ARRAY, "
                + "PARSE_JSON('[1.2345678901234567e19]')::ARRAY)",
                "false"},
            {"SELECT ARRAY_CONTAINS(PARSE_JSON('9007199254740993'), "
                + "PARSE_JSON('[9007199254740992]')::ARRAY)",
                "false"},
            {"SELECT ARRAY_CONTAINS(PARSE_JSON('0.1000000000000000055511151231257827'), "
                + "PARSE_JSON('[1e-1]')::ARRAY)",
                "true"},
            {"SELECT ARRAY_POSITION(PARSE_JSON('1.2345678901234567e19'), PARSE_JSON('[1, "
                + "12345678901234567890]')::ARRAY)",
                "1"},
            {"SELECT GREATEST(PARSE_JSON('12345678901234567890'), "
                + "PARSE_JSON('1.2345678901234567e19'))::VARCHAR",
                "12345678901234567890"},
            {"SELECT PARSE_JSON('[9007199254740993]') = PARSE_JSON('[9007199254740992]')",
                "false"},
            {"SELECT PARSE_JSON('[12345678901234567890]') > PARSE_JSON('[1.2345678901234567e19]')",
                "false"},
            {"SELECT OBJECT_CONSTRUCT('a', 9007199254740993) = OBJECT_CONSTRUCT('a', "
                + "9007199254740992::FLOAT)",
                "true"},
            {"SELECT ARRAY_CONSTRUCT(9007199254740993) = ARRAY_CONSTRUCT(9007199254740992::FLOAT)",
                "true"},
            {"SELECT PARSE_JSON('12345678901234567890') = ANY (SELECT PARSE_JSON('1.2345678901234567e19'))",
                "true"},
        });
    }

    @Test
    public void keyedComparisons() {
        engine.execute("CREATE TABLE ja (v VARIANT, n INT)");
        engine.execute("CREATE TABLE jb (v VARIANT, n INT)");
        engine.execute("INSERT INTO ja SELECT PARSE_JSON('12345678901234567890'), 1");
        engine.execute("INSERT INTO jb SELECT PARSE_JSON('1.2345678901234567e19'), 1");
        engine.execute("CREATE TABLE jf (f FLOAT)");
        engine.execute("INSERT INTO jf SELECT 1.2345678901234567e19::FLOAT");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM ja WHERE v = PARSE_JSON('1.2345678901234567e19')",
                "1"},
            {"SELECT COUNT(*) FROM ja WHERE v IN (PARSE_JSON('1.2345678901234567e19'), PARSE_JSON('2'))",
                "1"},
            {"SELECT COUNT(*) FROM ja LEFT JOIN jb ON ja.v = jb.v WHERE jb.n IS NOT NULL",
                "0"},
            {"SELECT ja.v = (SELECT v FROM jb) FROM ja",
                "false"},
            {"SELECT COUNT(*) FROM ja JOIN jb ON ja.n = jb.n AND ja.v = jb.v",
                "0"},
            {"SELECT COUNT(*) FROM ja JOIN jf ON ja.v = jf.f",
                "1"},
            {"SELECT COUNT(*) FROM jb WHERE v = PARSE_JSON('12345678901234567890')",
                "1"},
            {"SELECT COUNT(*) FROM ja NATURAL JOIN jb",
                "0"},
            {"SELECT COUNT(*) FROM ja JOIN jb USING (v)",
                "0"},
        });
    }
}
