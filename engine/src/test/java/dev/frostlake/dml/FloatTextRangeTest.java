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

package dev.frostlake.dml;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * A text read as a FLOAT while the statement runs — written into a FLOAT, DOUBLE or REAL column by INSERT, UPDATE,
 * MERGE or a typed CTAS, cast, passed to TO_DOUBLE, or read beside a FLOAT — refuses a finite number past the
 * double's range with "Numeric value '2e308' is not recognized" (100038/22018), the text echoed trimmed and without
 * one leading plus sign; a VARIANT holding such a text fails its cast to REAL. The TRY_ forms answer NULL, the
 * non-finite words and an underflow still read, and a constant the plan folds is the infinity of its sign
 * (live-verified).
 */
public class FloatTextRangeTest extends MeasuredCellsTestSupport {

    @Test
    public void writesRefuseTextPastDoubleRange() {
        engine.execute("CREATE OR REPLACE TABLE t (f FLOAT, d DOUBLE, r REAL, s VARCHAR, v VARIANT)");
        engine.execute("INSERT INTO t (s) VALUES ('5e308'), ('1')");
        engine.execute("INSERT INTO t (v) SELECT TO_VARIANT('2e308')");
        engine.execute("INSERT INTO t (f, s) VALUES ('1e-400', 'a1')");
        engine.execute("INSERT INTO t (f, s) VALUES ('-inf', 'a2')");
        engine.execute("INSERT INTO t (f, s) VALUES ('Infinity', 'a3')");
        engine.execute("INSERT INTO t (f, s) VALUES ('NaN', 'a4')");
        engine.execute("INSERT INTO t (f, s) VALUES ('1.7976931348623157e308', 'a5')");
        engine.execute("INSERT INTO t (f, s) VALUES ('2e308'::FLOAT, 'a6')");
        engine.execute("INSERT INTO t (f, s) VALUES (TO_DOUBLE('-1e400'), 'a7')");
        engine.execute("INSERT INTO t (f, s) VALUES (1e400, 'a8')");
        engine.execute("INSERT INTO t (f, s) SELECT '1e400'::FLOAT, 'a9'");
        engine.execute("UPDATE t SET d = '2e308'::FLOAT WHERE s = 'a9'");
        assertCells(new String[][] {
            {"INSERT INTO t (f) VALUES ('2e308')",
                "REFUSED DML operation to table T failed on column F with error: Numeric value '2e308' is not"
                + " recognized"},
            {"INSERT INTO t (f) VALUES ('-2e308')",
                "REFUSED DML operation to table T failed on column F with error: Numeric value '-2e308' is "
                + "not recognized"},
            {"INSERT INTO t (f) VALUES (' 1e400 ')",
                "REFUSED DML operation to table T failed on column F with error: Numeric value '1e400' is not"
                + " recognized"},
            {"INSERT INTO t (f) VALUES (' +5e308 ')",
                "REFUSED DML operation to table T failed on column F with error: Numeric value '5e308' is not"
                + " recognized"},
            {"INSERT INTO t (f) VALUES ('0x1p2000')",
                "REFUSED DML operation to table T failed on column F with error: Numeric value '0x1p2000' is "
                + "not recognized"},
            {"INSERT INTO t (f) VALUES ('1.797693134862316e308')",
                "REFUSED DML operation to table T failed on column F with error: Numeric value "
                + "'1.797693134862316e308' is not recognized"},
            {"INSERT INTO t (d) VALUES ('2e308')",
                "REFUSED DML operation to table T failed on column D with error: Numeric value '2e308' is not"
                + " recognized"},
            {"INSERT INTO t (r) VALUES ('2e308')",
                "REFUSED DML operation to table T failed on column R with error: Numeric value '2e308' is not"
                + " recognized"},
            {"INSERT INTO t (f) VALUES ('1'), ('2e308')",
                "REFUSED DML operation to table T failed on column F with error: Numeric value '2e308' is not"
                + " recognized"},
            {"INSERT INTO t (f) SELECT '1e400'",
                "REFUSED DML operation to table T failed on column F with error: Numeric value '1e400' is not"
                + " recognized"},
            {"INSERT INTO t (f) VALUES ('2e3' || '08')",
                "REFUSED DML operation to table T failed on column F with error: Numeric value '2e308' is not"
                + " recognized"},
            {"INSERT INTO t (f) SELECT s FROM t WHERE s = '5e308'",
                "REFUSED DML operation to table T failed on column F with error: Numeric value '5e308' is not"
                + " recognized"},
            {"UPDATE t SET f = '3e308' WHERE s = '5e308'",
                "REFUSED DML operation to table T failed on column F with error: Numeric value '3e308' is not"
                + " recognized"},
            {"UPDATE t SET f = s WHERE s = '5e308'",
                "REFUSED DML operation to table T failed on column F with error: Numeric value '5e308' is not"
                + " recognized"},
            {"MERGE INTO t USING (SELECT '4e308' AS x) src ON FALSE WHEN NOT MATCHED THEN INSERT (f) "
                + "VALUES (src.x)",
                "REFUSED DML operation to table T failed on column F with error: Numeric value '4e308' is not"
                + " recognized"},
            {"MERGE INTO t USING (SELECT '4e308' AS x) src ON t.s = '5e308' WHEN MATCHED THEN UPDATE SET f"
                + " = src.x",
                "REFUSED DML operation to table T failed on column F with error: Numeric value '4e308' is not"
                + " recognized"},
            {"CREATE OR REPLACE TABLE t5 (f FLOAT) AS SELECT s FROM t WHERE s = '5e308'",
                "REFUSED DML operation to table TEST_DB.TEST_SCHEMA.T5 failed on column F with error: Numeric "
                + "value '5e308' is not recognized"},
            {"INSERT INTO t (f) SELECT v FROM t WHERE v IS NOT NULL",
                "REFUSED DML operation to table T failed on column F with error: Failed to cast variant value"
                + " \"2e308\" to REAL"},
            {"INSERT INTO t (f) VALUES (' abc ')",
                "REFUSED DML operation to table T failed on column F with error: Numeric value 'abc' is not "
                + "recognized"},
            {"INSERT INTO t (f) VALUES ('+abc')",
                "REFUSED DML operation to table T failed on column F with error: Numeric value 'abc' is not "
                + "recognized"},
            {"INSERT INTO t (f) VALUES ('++1')",
                "REFUSED DML operation to table T failed on column F with error: Numeric value '+1' is not "
                + "recognized"},
            {"INSERT INTO t (f) VALUES ('  ')",
                "REFUSED DML operation to table T failed on column F with error: Numeric value '' is not "
                + "recognized"},
            {"SELECT f = 0 FROM t WHERE s = 'a1'",
                "true"},
            {"SELECT COUNT(*) FROM t WHERE s IN ('a2', 'a3', 'a4', 'a6', 'a7', 'a8', 'a9') AND (f IN "
                + "('inf'::FLOAT, '-inf'::FLOAT) OR f = 'NaN'::FLOAT)",
                "7"},
            {"SELECT f = 1.7976931348623157e308 FROM t WHERE s = 'a5'",
                "true"},
            {"SELECT d = 'inf'::FLOAT FROM t WHERE s = 'a9'",
                "true"},
        });
    }

    @Test
    public void readsRefuseTextPastDoubleRange() {
        engine.execute("CREATE OR REPLACE TABLE t (f FLOAT, s VARCHAR, v VARIANT)");
        engine.execute("INSERT INTO t (f, s) VALUES (1, '5e308'), (2, ' -1e400'), (3, '0x1p2000'), (4, 'inf'), (5, "
            + "'1')");
        engine.execute("INSERT INTO t (f, v) SELECT 6, TO_VARIANT('2e308')");
        engine.execute("INSERT INTO t (f, v) SELECT 7, TO_VARIANT('1')");
        assertCells(new String[][] {
            {"SELECT s::FLOAT FROM t WHERE f = 1",
                "REFUSED Numeric value '5e308' is not recognized"},
            {"SELECT s::FLOAT FROM t WHERE f = 2",
                "REFUSED Numeric value '-1e400' is not recognized"},
            {"SELECT s::DOUBLE FROM t WHERE f = 3",
                "REFUSED Numeric value '0x1p2000' is not recognized"},
            {"SELECT s::FLOAT = 'inf'::FLOAT FROM t WHERE f = 4",
                "true"},
            {"SELECT s::FLOAT FROM t ORDER BY f",
                "REFUSED Numeric value '5e308' is not recognized"},
            {"SELECT TO_DOUBLE(s) FROM t WHERE f = 1",
                "REFUSED Numeric value '5e308' is not recognized"},
            {"SELECT TRY_TO_DOUBLE(s) FROM t WHERE f = 1",
                "null"},
            {"SELECT TRY_CAST(s AS REAL) FROM t WHERE f = 1",
                "null"},
            {"SELECT f + s FROM t WHERE f IN (1, 5) ORDER BY f",
                "REFUSED Numeric value '5e308' is not recognized"},
            {"SELECT COUNT(*) FROM t WHERE f IN (1, 5) AND f < s",
                "REFUSED Numeric value '5e308' is not recognized"},
            {"SELECT v::FLOAT FROM t WHERE f = 6",
                "REFUSED Failed to cast variant value \"2e308\" to REAL"},
            {"SELECT TO_DOUBLE(v) FROM t WHERE f = 6",
                "REFUSED Failed to cast variant value \"2e308\" to REAL"},
            {"SELECT f + v FROM t WHERE f = 6",
                "REFUSED Failed to cast variant value \"2e308\" to REAL"},
            {"SELECT TRY_TO_DOUBLE(v::STRING) FROM t WHERE f = 6",
                "null"},
            {"SELECT '2e308'::FLOAT = 'inf'::FLOAT",
                "true"},
            {"SELECT '-2e308'::DOUBLE = '-inf'::FLOAT",
                "true"},
            {"SELECT ' 1e400 '::FLOAT = 'inf'::FLOAT",
                "true"},
            {"SELECT '0x1p2000'::FLOAT = 'inf'::FLOAT",
                "true"},
            {"SELECT TO_DOUBLE('2e308') = 'inf'::FLOAT",
                "true"},
            {"SELECT TRY_TO_DOUBLE('1e400') = 'inf'::FLOAT",
                "true"},
            {"SELECT TRY_CAST('2e308' AS FLOAT) = 'inf'::FLOAT",
                "true"},
            {"SELECT TO_VARIANT('2e308')::FLOAT = 'inf'::FLOAT",
                "true"},
            {"SELECT TO_DOUBLE(TO_VARIANT('2e308')) = 'inf'::FLOAT",
                "true"},
            {"SELECT PARSE_JSON('\"2e308\"')::FLOAT",
                "REFUSED Failed to cast variant value \"2e308\" to REAL"},
            {"SELECT ('2e3' || '08')::FLOAT = 'inf'::FLOAT",
                "true"},
            {"SELECT NVL(NULL, '2e308')::FLOAT = 'inf'::FLOAT",
                "true"},
            {"SELECT TO_DOUBLE(UPPER('2e308')) = 'inf'::FLOAT",
                "true"},
            {"SELECT 1.0::FLOAT + '2e308' = 'inf'::FLOAT",
                "true"},
            {"SELECT 1.0::FLOAT < '2e308'",
                "true"},
            {"SELECT COUNT(*) FROM t WHERE '2e308'::FLOAT = 'inf'::FLOAT",
                "7"},
            {"SELECT COUNT(*) FROM t WHERE f + '2e308' = 'inf'::FLOAT",
                "7"},
            {"SELECT COUNT(*) FROM (SELECT '+5e308' AS x FROM t) WHERE x::FLOAT = 'inf'::FLOAT",
                "7"},
        });
    }

    @Test
    public void foldedConstantsThroughRelationsAndCtas() {
        engine.execute("CREATE OR REPLACE TABLE t (f FLOAT, s VARCHAR)");
        engine.execute("INSERT INTO t (f, s) VALUES (1, '5e308'), (2, '1')");
        engine.execute("CREATE OR REPLACE TABLE t3 (f FLOAT) AS SELECT '2e308'");
        engine.execute("CREATE OR REPLACE TABLE t4 (f DOUBLE, n INT) AS SELECT UPPER('-2e308'), 1");
        engine.execute("CREATE OR REPLACE TABLE t6 (f FLOAT) AS SELECT '2e308' FROM t");
        engine.execute("CREATE OR REPLACE TABLE t7 (f FLOAT) AS SELECT x FROM (SELECT '2e308' AS x)");
        assertCells(new String[][] {
            {"SELECT f = 'inf'::FLOAT FROM t3",
                "true"},
            {"SELECT f = '-inf'::FLOAT FROM t4",
                "true"},
            {"CREATE OR REPLACE TABLE t5 (f FLOAT) AS SELECT s FROM t WHERE f = 1",
                "REFUSED DML operation to table TEST_DB.TEST_SCHEMA.T5 failed on column F with error: Numeric "
                + "value '5e308' is not recognized"},
            {"SELECT COUNT(*) FROM t6 WHERE f = 'inf'::FLOAT",
                "2"},
            {"SELECT f = 'inf'::FLOAT FROM t7",
                "true"},
            {"SELECT x::FLOAT = 'inf'::FLOAT FROM (SELECT '2e308' AS x)",
                "true"},
            {"SELECT TO_DOUBLE(x) = 'inf'::FLOAT FROM (SELECT '2e308' AS x)",
                "true"},
            {"SELECT x + 1.0::FLOAT = 'inf'::FLOAT FROM (SELECT '2e308' AS x)",
                "true"},
            {"WITH c AS (SELECT '2e308' AS x) SELECT x::FLOAT = 'inf'::FLOAT FROM c",
                "true"},
            {"SELECT y::FLOAT = 'inf'::FLOAT FROM (SELECT '2e308' AS x) AS d (y)",
                "true"},
            {"SELECT COUNT(*) FROM (SELECT DISTINCT '2e308' AS x FROM t) WHERE x::FLOAT = 'inf'::FLOAT",
                "1"},
            {"SELECT column1::FLOAT FROM VALUES ('2e308')",
                "REFUSED Numeric value '2e308' is not recognized"},
            {"SELECT x::FLOAT FROM (SELECT s AS x FROM t) ORDER BY 1",
                "REFUSED Numeric value '5e308' is not recognized"},
            {"INSERT INTO t (f) SELECT x FROM (SELECT '2e308' AS x)",
                "REFUSED DML operation to table T failed on column F with error: Numeric value '2e308' is not"
                + " recognized"},
        });
    }
}
