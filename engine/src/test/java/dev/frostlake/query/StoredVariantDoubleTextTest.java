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
 * A VARIANT holding a whole double converts to its FLOAT text once it is stored or computed: a table cell written
 * from {@code TO_VARIANT(1::FLOAT)} by INSERT, CTAS, UPDATE or MERGE reads back through {@code ::VARCHAR} as
 * {@code 1}, as does such a value after a UNION of different arms, MIN, MAX, ANY_VALUE, LAG, LEAD or NTH_VALUE, while
 * an unstored folded constant keeps {@code 1.0}. A VARIANT compared with a text compares that {@code ::VARCHAR}
 * text. Every cell agrees with the account (live-verified).
 */
public class StoredVariantDoubleTextTest extends MeasuredCellsTestSupport {

    @Test
    public void storedAndComputedDoublesReadAsFloatText() {
        engine.execute("CREATE TABLE t (id INT, v VARIANT)");
        engine.execute("INSERT INTO t SELECT 1, TO_VARIANT(1::FLOAT)");
        engine.execute("INSERT INTO t SELECT 2, PARSE_JSON('1.0e0')");
        engine.execute("INSERT INTO t SELECT 3, TO_VARIANT(1e20::FLOAT)");
        engine.execute("INSERT INTO t SELECT 4, TO_VARIANT(-0::FLOAT)");
        engine.execute("INSERT INTO t SELECT 5, TO_VARIANT(123456.0::FLOAT)");
        engine.execute("INSERT INTO t SELECT 6, TO_VARIANT(1e-4::FLOAT)");
        engine.execute("INSERT INTO t SELECT 7, OBJECT_CONSTRUCT('a', 1::FLOAT)");
        engine.execute("INSERT INTO t (id, v) SELECT 8, 2::FLOAT");
        engine.execute("INSERT INTO t (id, v) SELECT 9, PARSE_JSON('0')");
        engine.execute("UPDATE t SET v = TO_VARIANT(3::FLOAT) WHERE id = 9");
        engine.execute("MERGE INTO t USING (SELECT 10 id) s ON t.id = s.id WHEN NOT MATCHED THEN INSERT (id, v) "
            + "VALUES (10, TO_VARIANT(5::FLOAT))");
        engine.execute("MERGE INTO t USING (SELECT 1 id) s ON t.id = s.id WHEN MATCHED THEN UPDATE SET v = "
            + "TO_VARIANT(1::FLOAT)");
        engine.execute("CREATE TABLE c AS SELECT TO_VARIANT(6::FLOAT) v");
        assertCells(new String[][] {
            {"SELECT v::VARCHAR FROM t WHERE id = 1",
                "1"},
            {"SELECT v::VARCHAR FROM t WHERE id = 2",
                "1"},
            {"SELECT v::VARCHAR FROM t WHERE id = 3",
                "1e+20"},
            {"SELECT v::VARCHAR FROM t WHERE id = 4",
                "-0"},
            {"SELECT v::VARCHAR FROM t WHERE id = 5",
                "123456"},
            {"SELECT v::VARCHAR FROM t WHERE id = 6",
                "0.0001"},
            {"SELECT v::VARCHAR FROM t WHERE id = 7",
                "{\"a\":1.000000000000000e+00}"},
            {"SELECT v::VARCHAR FROM t WHERE id = 8",
                "2"},
            {"SELECT v::VARCHAR FROM t WHERE id = 9",
                "3"},
            {"SELECT v::VARCHAR FROM t WHERE id = 10",
                "5"},
            {"SELECT v::VARCHAR FROM c",
                "6"},
            {"SELECT TO_VARCHAR(v) FROM t WHERE id = 1",
                "1"},
            {"SELECT TO_CHAR(v) FROM t WHERE id = 1",
                "1"},
            {"SELECT v || 'x' FROM t WHERE id = 1",
                "1x"},
            {"SELECT v = '1' FROM t WHERE id = 1",
                "true"},
            {"SELECT v = '1.0' FROM t WHERE id = 1",
                "false"},
            {"SELECT '1' = v FROM t WHERE id = 1",
                "true"},
            {"SELECT v = '1' FROM t WHERE id = 2",
                "true"},
            {"SELECT v = '1e+20' FROM t WHERE id = 3",
                "true"},
            {"SELECT v = '-0' FROM t WHERE id = 4",
                "true"},
            {"SELECT v = '6' FROM c",
                "true"},
            {"SELECT v <> '1' FROM t WHERE id = 1",
                "false"},
            {"SELECT v IN ('1', 'x') FROM t WHERE id = 1",
                "true"},
            {"SELECT COUNT(*) FROM t WHERE v = '1'",
                "2"},
            {"SELECT TO_JSON(v) FROM t WHERE id = 1",
                "1.000000000000000e+00"},
            {"SELECT TYPEOF(v) FROM t WHERE id = 1",
                "DOUBLE"},
            {"SELECT SYSTEM$TYPEOF(v) FROM t WHERE id = 1",
                "VARIANT[LOB]"},
            {"SELECT TO_VARIANT(1::FLOAT)::VARCHAR",
                "1.0"},
            {"SELECT TO_VARIANT(1::FLOAT) = '1'",
                "false"},
            {"SELECT TO_VARIANT(1::FLOAT) = '1.0'",
                "true"},
            {"SELECT x::VARCHAR FROM (SELECT TO_VARIANT(1::FLOAT) x)",
                "1.0"},
            {"WITH q AS (SELECT TO_VARIANT(9::FLOAT) v) SELECT v::VARCHAR FROM q",
                "9.0"},
            {"SELECT PARSE_JSON('1e0') = '1'",
                "true"},
            {"SELECT PARSE_JSON('1e0') = '1.0'",
                "false"},
            {"SELECT PARSE_JSON('1e20') = '1e+20'",
                "true"},
            {"SELECT PARSE_JSON('1e-5') = '1e-05'",
                "true"},
            {"SELECT PARSE_JSON('-0e0') = '-0'",
                "true"},
            {"SELECT PARSE_JSON('2e0') > '10'",
                "true"},
            {"SELECT PARSE_JSON('1e0') BETWEEN '0' AND '1'",
                "true"},
            {"SELECT PARSE_JSON('{\"a\":1e0}') = '{\"a\":1.000000000000000e+00}'",
                "true"},
            {"SELECT TO_VARIANT(SQRT(2)) = '1.414213562'",
                "true"},
            {"SELECT MIN(v::VARCHAR) FROM (SELECT TO_VARIANT(1::FLOAT) v UNION ALL SELECT "
                + "TO_VARIANT(2::FLOAT))",
                "1"},
            {"SELECT MIN(v::VARCHAR) FROM (SELECT TO_VARIANT(1::FLOAT) v UNION SELECT "
                + "TO_VARIANT(2::FLOAT))",
                "1"},
            {"SELECT v::VARCHAR FROM (SELECT TO_VARIANT(1::FLOAT) v INTERSECT SELECT TO_VARIANT(1::FLOAT))",
                "1.0"},
            {"SELECT v::VARCHAR FROM (SELECT TO_VARIANT(1::FLOAT) v EXCEPT SELECT TO_VARIANT(2::FLOAT))",
                "1.0"},
            {"SELECT ANY_VALUE(v)::VARCHAR FROM (SELECT TO_VARIANT(1::FLOAT) v)",
                "1"},
            {"SELECT MIN(v)::VARCHAR FROM (SELECT TO_VARIANT(1::FLOAT) v)",
                "1"},
            {"SELECT MAX(v)::VARCHAR FROM (SELECT TO_VARIANT(1::FLOAT) v)",
                "1"},
            {"SELECT MAX(v)::VARCHAR FROM (SELECT 1 g, TO_VARIANT(1::FLOAT) v) GROUP BY g",
                "1"},
            {"SELECT v::VARCHAR FROM (SELECT TO_VARIANT(1::FLOAT) v) GROUP BY v",
                "1.0"},
            {"SELECT v::VARCHAR FROM (SELECT DISTINCT TO_VARIANT(1::FLOAT) v)",
                "1.0"},
            {"SELECT FIRST_VALUE(v) OVER (ORDER BY 1)::VARCHAR FROM (SELECT TO_VARIANT(1::FLOAT) v)",
                "1.0"},
            {"SELECT LAG(v, 0) OVER (ORDER BY 1)::VARCHAR FROM (SELECT TO_VARIANT(1::FLOAT) v)",
                "1"},
            {"SELECT LEAD(v, 0) OVER (ORDER BY 1)::VARCHAR FROM (SELECT TO_VARIANT(1::FLOAT) v)",
                "1"},
            {"SELECT MAX(v) OVER ()::VARCHAR FROM (SELECT TO_VARIANT(1::FLOAT) v)",
                "1"},
            {"SELECT ANY_VALUE(v) OVER ()::VARCHAR FROM (SELECT TO_VARIANT(1::FLOAT) v)",
                "1"},
            {"SELECT COALESCE(TO_VARIANT(1::FLOAT), PARSE_JSON('2'))::VARCHAR",
                "1.0"},
            {"SELECT MIN(v::VARCHAR) FROM (SELECT TO_VARIANT(1::FLOAT) v UNION ALL SELECT "
                + "TO_VARIANT(1::FLOAT))",
                "1.0"},
            {"SELECT v::VARCHAR FROM (SELECT TO_VARIANT(1::FLOAT) v UNION SELECT TO_VARIANT(1::FLOAT))",
                "1.0"},
            {"WITH c AS (SELECT TO_VARIANT(1::FLOAT) v) SELECT MIN(v::VARCHAR) FROM (SELECT v FROM c UNION"
                + " ALL SELECT v FROM c)",
                "1.0"},
            {"SELECT MIN(v::VARCHAR) FROM (SELECT TO_VARIANT(1::FLOAT) v UNION ALL SELECT "
                + "1::FLOAT::VARIANT)",
                "1.0"},
            {"SELECT MAX(v::VARCHAR) FROM (SELECT TO_VARIANT(1::FLOAT) v UNION ALL SELECT "
                + "TO_VARIANT(2::FLOAT))",
                "2"},
            {"SELECT v::VARCHAR FROM (SELECT TO_VARIANT(1::FLOAT) v UNION ALL SELECT TO_VARIANT(1::FLOAT))"
                + " LIMIT 1",
                "1.0"},
            {"SELECT LAST_VALUE(v) OVER (ORDER BY 1)::VARCHAR FROM (SELECT TO_VARIANT(1::FLOAT) v)",
                "1.0"},
            {"SELECT NTH_VALUE(v, 1) OVER (ORDER BY 1)::VARCHAR FROM (SELECT TO_VARIANT(1::FLOAT) v)",
                "1"},
            {"SELECT MIN(v::VARCHAR) FROM (SELECT TO_VARIANT(1::FLOAT) v UNION ALL SELECT "
                + "TO_VARIANT(1::FLOAT) UNION ALL SELECT TO_VARIANT(2::FLOAT))",
                "1"},
            {"SELECT MIN(v::VARCHAR) FROM (SELECT TO_VARIANT(1::FLOAT) v UNION ALL SELECT "
                + "PARSE_JSON('1e0'))",
                "1"},
        });
    }
}
