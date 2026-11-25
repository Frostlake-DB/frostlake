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
 * A window call's argument or key beside an aggregate is named as the plan holds it: a conversion to DECFLOAT is its
 * CAST, an operand beside a DECFLOAT is moved to DECFLOAT, a value a VARIANT function reads is cast to VARIANT, and a
 * predicate is cast to the BOOLEAN it yields; a fault of a value the group computes for a window over it names the
 * value (live-verified).
 */
public class WindowArgumentConversionEchoTest extends MeasuredCellsTestSupport {

    @Test
    public void conversionsAndDecfloatOperations() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN, s VARCHAR, d DECFLOAT, f FLOAT, n "
            + "NUMBER(10,2), v VARIANT, dt DATE)");
        engine.execute("INSERT INTO fz SELECT 5, TRUE, 'x', 1.5, 2.5, 3.25, PARSE_JSON('[1]'), '2024-01-01'");
        assertCells(new String[][] {
            {"SELECT MAX(id), MAX(TO_DECFLOAT(id)) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.ID AS DECFLOAT(38))] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), MAX(TO_NUMBER(id)) OVER () FROM fz",
                "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression"},
            {"SELECT MAX(id), MAX(TO_VARCHAR(id)) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.ID AS VARCHAR(134217728))] is not a valid group by"
                + " expression"},
            {"SELECT MAX(id), MAX(TO_DOUBLE(id)) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.ID AS FLOAT)] is not a valid group by expression"},
            {"SELECT MAX(id), MAX(TO_BOOLEAN(id)) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.ID <> 0 AS BOOLEAN)] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), MAX(TO_DATE(s)) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.S AS DATE)] is not a valid group by expression"},
            {"SELECT MAX(id), MAX(TO_CHAR(id)) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.ID AS VARCHAR(134217728))] is not a valid group by"
                + " expression"},
            {"SELECT MAX(id), MAX(TO_DECIMAL(id, 10, 2)) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.ID AS NUMBER(10,2))] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), MAX(TO_NUMBER(s)) OVER () FROM fz",
                "REFUSED SQL compilation error: | [TO_NUMBER(FZ.S)] is not a valid group by expression"},
            {"SELECT MAX(id), MAX(TO_DECFLOAT(s)) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.S AS DECFLOAT(38))] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), MAX(id::DECFLOAT) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.ID AS DECFLOAT(38))] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), MAX(TO_VARIANT(id)) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.ID AS VARIANT)] is not a valid group by expression"},
            {"SELECT MAX(id), MAX(TO_TIMESTAMP(s)) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.S AS TIMESTAMP_NTZ(9))] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), MAX(TO_DECFLOAT(d)) OVER () FROM fz",
                "REFUSED SQL compilation error: | [FZ.D] is not a valid group by expression"},
            {"SELECT MAX(id), MAX(TO_DOUBLE(s)) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.S AS FLOAT)] is not a valid group by expression"},
            {"SELECT MAX(id), MAX(id::DECFLOAT + 1) OVER () FROM fz",
                "REFUSED SQL compilation error: | [(CAST(FZ.ID AS DECFLOAT(38))) + (CAST(1 AS DECFLOAT(38)))]"
                + " is not a valid group by expression"},
            {"SELECT MAX(id), MAX(d + 1) OVER () FROM fz",
                "REFUSED SQL compilation error: | [FZ.D + (CAST(1 AS DECFLOAT(38)))] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), MAX(d + id) OVER () FROM fz",
                "REFUSED SQL compilation error: | [FZ.D + (CAST(FZ.ID AS DECFLOAT(38)))] is not a valid group"
                + " by expression"},
            {"SELECT MAX(id), MAX(d * 2) OVER () FROM fz",
                "REFUSED SQL compilation error: | [FZ.D * (CAST(2 AS DECFLOAT(38)))] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), MAX(d + 1.5) OVER () FROM fz",
                "REFUSED SQL compilation error: | [FZ.D + (CAST(1.5 AS DECFLOAT(38)))] is not a valid group "
                + "by expression"},
            {"SELECT MAX(id), MAX(f + 1) OVER () FROM fz",
                "REFUSED SQL compilation error: | [FZ.F + (CAST(1 AS FLOAT))] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), MAX(n + 1) OVER () FROM fz",
                "REFUSED SQL compilation error: | [FZ.N + (CAST(1 AS NUMBER(10,2)))] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), MAX(d - 1) OVER () FROM fz",
                "REFUSED SQL compilation error: | [FZ.D - (CAST(1 AS DECFLOAT(38)))] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), MAX(1 + d) OVER () FROM fz",
                "REFUSED SQL compilation error: | [(CAST(1 AS DECFLOAT(38))) + FZ.D] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), MAX(d / 2) OVER () FROM fz",
                "REFUSED SQL compilation error: | [FZ.D / (CAST(2 AS DECFLOAT(38)))] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), MAX(d + f) OVER () FROM fz",
                "REFUSED SQL compilation error: | [FZ.D + (CAST(FZ.F AS DECFLOAT(38)))] is not a valid group "
                + "by expression"},
            {"SELECT MAX(id), MAX(f * 2) OVER () FROM fz",
                "REFUSED SQL compilation error: | [FZ.F * (CAST(2 AS FLOAT))] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), MAX(id + d) OVER () FROM fz",
                "REFUSED SQL compilation error: | [(CAST(FZ.ID AS DECFLOAT(38))) + FZ.D] is not a valid group"
                + " by expression"},
            {"SELECT MAX(id), MAX(d + n) OVER () FROM fz",
                "REFUSED SQL compilation error: | [FZ.D + (CAST(FZ.N AS DECFLOAT(38)))] is not a valid group "
                + "by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY ARRAY_SIZE(id)) FROM fz",
                "REFUSED SQL compilation error: | [ARRAY_SIZE(CAST(FZ.ID AS VARIANT))] is not a valid group "
                + "by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY ARRAY_SIZE(v)) FROM fz",
                "REFUSED SQL compilation error: | [ARRAY_SIZE(FZ.V)] is not a valid group by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY TYPEOF(id)) FROM fz",
                "REFUSED SQL compilation error: | [TYPEOF(CAST(FZ.ID AS VARIANT))] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY GET(v, 0)) FROM fz",
                "REFUSED SQL compilation error: | [GET(FZ.V, 0)] is not a valid group by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY TYPEOF(s)) FROM fz",
                "REFUSED SQL compilation error: error line 1 at position 38 | Invalid argument types for "
                + "function 'TYPEOF': (VARCHAR(16777216))"},
            {"SELECT MAX(id), MAX(ARRAY_SIZE(s)) OVER () FROM fz",
                "REFUSED SQL compilation error: error line 1 at position 20 | Invalid argument types for "
                + "function 'ARRAY_SIZE': (VARCHAR(16777216))"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY TYPEOF(v)) FROM fz",
                "REFUSED SQL compilation error: | [TYPEOF(FZ.V)] is not a valid group by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY IS_ARRAY(id)) FROM fz",
                "REFUSED SQL compilation error: | [CAST(IS_ARRAY(CAST(FZ.ID AS VARIANT)) AS BOOLEAN)] is not "
                + "a valid group by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY ARRAY_SIZE(PARSE_JSON(s))) FROM fz",
                "REFUSED SQL compilation error: | [ARRAY_SIZE(PARSE_JSON(FZ.S))] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY ARRAY_SIZE(b)) FROM fz",
                "REFUSED SQL compilation error: | [ARRAY_SIZE(CAST(FZ.B AS VARIANT))] is not a valid group by"
                + " expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY ARRAY_CONTAINS(id, v)) FROM fz",
                "REFUSED SQL compilation error: | [CAST(ARRAY_CONTAINS(CAST(FZ.ID AS VARIANT), CAST(FZ.V AS "
                + "ARRAY)) AS BOOLEAN)] is not a valid group by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY GET(v, id)) FROM fz",
                "REFUSED SQL compilation error: | [GET(FZ.V, FZ.ID)] is not a valid group by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY TO_ARRAY(id)) FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.ID AS ARRAY)] is not a valid group by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY IS_INTEGER(id)) FROM fz",
                "REFUSED SQL compilation error: | [CAST(IS_INTEGER(CAST(FZ.ID AS VARIANT)) AS BOOLEAN)] is "
                + "not a valid group by expression"},
            {"SELECT RANK() OVER (ORDER BY SUM(s::INT)) FROM fz",
                "REFUSED Numeric value 'x' is not recognized"},
            {"SELECT SUM(SUM(s::INT)) OVER () FROM fz",
                "REFUSED Numeric value 'x' is not recognized"},
            {"SELECT SUM(s::INT) FROM fz",
                "REFUSED Numeric value 'x' is not recognized"},
            {"SELECT AVG(s::INT) FROM fz",
                "REFUSED Numeric value 'x' is not recognized"},
            {"SELECT MAX(s::INT) FROM fz",
                "REFUSED Numeric value 'x' is not recognized"},
            {"SELECT SUM(s::INT) OVER () FROM fz",
                "REFUSED Numeric value 'x' is not recognized"},
            {"SELECT MAX(s::INT) OVER () FROM fz",
                "REFUSED Numeric value 'x' is not recognized"},
            {"SELECT AVG(s::INT) OVER () FROM fz",
                "REFUSED Numeric value 'x' is not recognized"},
            {"SELECT s::INT FROM fz",
                "REFUSED Numeric value 'x' is not recognized"},
            {"SELECT SUM(TO_NUMBER(s)) FROM fz",
                "REFUSED Numeric value 'x' is not recognized"},
            {"SELECT SUM(s::DOUBLE) FROM fz",
                "REFUSED Numeric value 'x' is not recognized"},
            {"SELECT SUM(s::INT + 1) FROM fz",
                "REFUSED Numeric value 'x' is not recognized"},
            {"SELECT RANK() OVER (ORDER BY MAX(s::INT)) FROM fz",
                "REFUSED Numeric value 'x' is not recognized"},
            {"SELECT MAX(MAX(s::INT)) OVER () FROM fz",
                "REFUSED Numeric value 'x' is not recognized"},
            {"SELECT SUM(s::INT) FROM fz GROUP BY b",
                "REFUSED Numeric value 'x' is not recognized"},
            {"SELECT b, SUM(s::INT) OVER (PARTITION BY b) FROM fz",
                "REFUSED Numeric value 'x' is not recognized"},
            {"SELECT SUM(s::DATE) FROM fz",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'SUM': (DATE)"},
            {"SELECT SUM(s) FROM fz",
                "REFUSED Numeric value 'x' is not recognized"},
        });
    }

    @Test
    public void neighbourConversions() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN, s VARCHAR, d DECFLOAT, f FLOAT, n "
            + "NUMBER(10,2), v VARIANT, dt DATE, a ARRAY, o OBJECT, ts TIMESTAMP_NTZ)");
        engine.execute("INSERT INTO fz SELECT 5, TRUE, 'x', 1.5, 2.5, 3.25, PARSE_JSON('[1]'), '2024-01-01', [1], "
            + "{'a':1}, '2024-01-01 00:00:00'");
        assertCells(new String[][] {
            {"SELECT MAX(id), MAX(TO_DECFLOAT(f)) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.F AS DECFLOAT(38))] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), MAX(TO_DECFLOAT(n)) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.N AS DECFLOAT(38))] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), MAX(TO_DECFLOAT(v)) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.V AS DECFLOAT(38))] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), MAX(TRY_TO_DECFLOAT(s)) OVER () FROM fz",
                "REFUSED SQL compilation error: | [TRY_CAST(FZ.S AS DECFLOAT(38))] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), MAX(TO_DECFLOAT(b)) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.B AS DECFLOAT(38))] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), MAX(TO_DECFLOAT(s, '999')) OVER () FROM fz",
                "REFUSED SQL compilation error: | [TO_DECFLOAT(FZ.S, '999')] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), MAX(TO_DECFLOAT(id) + 1) OVER () FROM fz",
                "REFUSED SQL compilation error: | [(CAST(FZ.ID AS DECFLOAT(38))) + (CAST(1 AS DECFLOAT(38)))]"
                + " is not a valid group by expression"},
            {"SELECT MAX(id), MAX(ABS(TO_DECFLOAT(id))) OVER () FROM fz",
                "REFUSED SQL compilation error: | [ABS(CAST(FZ.ID AS DECFLOAT(38)))] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), MAX(d + d) OVER () FROM fz",
                "REFUSED SQL compilation error: | [FZ.D + FZ.D] is not a valid group by expression"},
            {"SELECT MAX(id), MAX(d / d) OVER () FROM fz",
                "REFUSED SQL compilation error: | [FZ.D / FZ.D] is not a valid group by expression"},
            {"SELECT MAX(id), MAX(d % 2) OVER () FROM fz",
                "REFUSED SQL compilation error: | [FZ.D % (CAST(2 AS DECFLOAT(38)))] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), MAX(d > 1) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.D > (CAST(1 AS DECFLOAT(38))) AS BOOLEAN)] is not "
                + "a valid group by expression"},
            {"SELECT MAX(id), MAX(d = id) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.D = (CAST(FZ.ID AS DECFLOAT(38))) AS BOOLEAN)] is "
                + "not a valid group by expression"},
            {"SELECT MAX(id), MAX(id / d) OVER () FROM fz",
                "REFUSED SQL compilation error: | [(CAST(FZ.ID AS DECFLOAT(38))) / FZ.D] is not a valid group"
                + " by expression"},
            {"SELECT MAX(id), MAX(d + s) OVER () FROM fz",
                "REFUSED SQL compilation error: | [FZ.D + (CAST(FZ.S AS DECFLOAT(38)))] is not a valid group "
                + "by expression"},
            {"SELECT MAX(id), MAX(-d) OVER () FROM fz",
                "REFUSED SQL compilation error: | [NEGATE(FZ.D)] is not a valid group by expression"},
            {"SELECT MAX(id), MAX(d * n) OVER () FROM fz",
                "REFUSED SQL compilation error: | [FZ.D * (CAST(FZ.N AS DECFLOAT(38)))] is not a valid group "
                + "by expression"},
            {"SELECT MAX(id), MAX(d + NULL) OVER () FROM fz",
                "REFUSED SQL compilation error: | [FZ.D + (SYSTEM$NULL_TO_DECFLOAT(null))] is not a valid "
                + "group by expression"},
            {"SELECT MAX(id), MAX(d - f) OVER () FROM fz",
                "REFUSED SQL compilation error: | [FZ.D - (CAST(FZ.F AS DECFLOAT(38)))] is not a valid group "
                + "by expression"},
            {"SELECT MAX(id), MAX(id::DECFLOAT * id::DECFLOAT) OVER () FROM fz",
                "REFUSED SQL compilation error: | [(CAST(FZ.ID AS DECFLOAT(38))) * (CAST(FZ.ID AS "
                + "DECFLOAT(38)))] is not a valid group by expression"},
            {"SELECT MAX(id), MAX(d + 1e2) OVER () FROM fz",
                "REFUSED SQL compilation error: | [FZ.D + (CAST(100 AS DECFLOAT(38)))] is not a valid group "
                + "by expression"},
            {"SELECT MAX(id), MAX(d < f) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.D < (CAST(FZ.F AS DECFLOAT(38))) AS BOOLEAN)] is "
                + "not a valid group by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY ARRAY_SIZE(a)) FROM fz",
                "REFUSED SQL compilation error: | [ARRAY_SIZE(FZ.A)] is not a valid group by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY TYPEOF(a)) FROM fz",
                "REFUSED SQL compilation error: | [TYPEOF(CAST(FZ.A AS VARIANT))] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY TYPEOF(f)) FROM fz",
                "REFUSED SQL compilation error: | [TYPEOF(CAST(FZ.F AS VARIANT))] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY TYPEOF(dt)) FROM fz",
                "REFUSED SQL compilation error: error line 1 at position 38 | Invalid argument types for "
                + "function 'TYPEOF': (DATE)"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY TYPEOF(b)) FROM fz",
                "REFUSED SQL compilation error: | [TYPEOF(CAST(FZ.B AS VARIANT))] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY TYPEOF(o)) FROM fz",
                "REFUSED SQL compilation error: | [TYPEOF(CAST(FZ.O AS VARIANT))] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY TYPEOF(n)) FROM fz",
                "REFUSED SQL compilation error: | [TYPEOF(CAST(FZ.N AS VARIANT))] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY TYPEOF(d)) FROM fz",
                "REFUSED SQL compilation error: | [TYPEOF(CAST(FZ.D AS VARIANT))] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY TYPEOF(ts)) FROM fz",
                "REFUSED SQL compilation error: error line 1 at position 38 | Invalid argument types for "
                + "function 'TYPEOF': (TIMESTAMP_NTZ(9))"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY TYPEOF(id + 1)) FROM fz",
                "REFUSED SQL compilation error: | [TYPEOF(CAST(FZ.ID + 1 AS VARIANT))] is not a valid group "
                + "by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY ARRAY_SIZE(f)) FROM fz",
                "REFUSED SQL compilation error: | [ARRAY_SIZE(CAST(FZ.F AS VARIANT))] is not a valid group by"
                + " expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY ARRAY_SIZE(dt)) FROM fz",
                "REFUSED SQL compilation error: error line 1 at position 38 | Invalid argument types for "
                + "function 'ARRAY_SIZE': (DATE)"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY ARRAY_SIZE(o)) FROM fz",
                "REFUSED SQL compilation error: | [ARRAY_SIZE(CAST(FZ.O AS VARIANT))] is not a valid group by"
                + " expression"},
            {"SELECT MAX(id), MAX(TYPEOF(id)) OVER () FROM fz",
                "REFUSED SQL compilation error: | [TYPEOF(CAST(FZ.ID AS VARIANT))] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), MAX(ARRAY_SIZE(id)) OVER () FROM fz",
                "REFUSED SQL compilation error: | [ARRAY_SIZE(CAST(FZ.ID AS VARIANT))] is not a valid group "
                + "by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY IS_ARRAY(v)) FROM fz",
                "REFUSED SQL compilation error: | [CAST(IS_ARRAY(FZ.V) AS BOOLEAN)] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY STARTSWITH(s, 'x')) FROM fz",
                "REFUSED SQL compilation error: | [CAST(STARTSWITH(FZ.S, 'x') AS BOOLEAN)] is not a valid "
                + "group by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY IS_NULL_VALUE(id)) FROM fz",
                "REFUSED SQL compilation error: | [CAST(IS_NULL_VALUE(CAST(FZ.ID AS VARIANT)) AS BOOLEAN)] is"
                + " not a valid group by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY AS_INTEGER(id)) FROM fz",
                "REFUSED SQL compilation error: | invalid type [NUMBER(38,0)] for parameter "
                + "'AS_INTEGER(variantValue...)'"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY IS_VARCHAR(id)) FROM fz",
                "REFUSED SQL compilation error: | [CAST(IS_VARCHAR(CAST(FZ.ID AS VARIANT)) AS BOOLEAN)] is "
                + "not a valid group by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY ARRAY_SIZE(TO_ARRAY(id))) FROM fz",
                "REFUSED SQL compilation error: | [ARRAY_SIZE(CAST(FZ.ID AS ARRAY))] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), MAX(IS_INTEGER(id)) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(IS_INTEGER(CAST(FZ.ID AS VARIANT)) AS BOOLEAN)] is "
                + "not a valid group by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY id > 1) FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.ID > 1 AS BOOLEAN)] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY ARRAY_CONTAINS(id, a)) FROM fz",
                "REFUSED SQL compilation error: | [CAST(ARRAY_CONTAINS(CAST(FZ.ID AS VARIANT), FZ.A) AS "
                + "BOOLEAN)] is not a valid group by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY IS_OBJECT(o)) FROM fz",
                "REFUSED SQL compilation error: | [CAST(IS_OBJECT(CAST(FZ.O AS VARIANT)) AS BOOLEAN)] is not "
                + "a valid group by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY TYPEOF(id)), RANK() OVER (ORDER BY id) FROM fz",
                "REFUSED SQL compilation error: | [TYPEOF(CAST(FZ.ID AS VARIANT))] is not a valid group by "
                + "expression"},
        });
    }

    @Test
    public void predicatesAndGroupFaults() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN, s VARCHAR, d DECFLOAT, f FLOAT, n "
            + "NUMBER(10,2), v VARIANT, dt DATE, a ARRAY, o OBJECT, ts TIMESTAMP_NTZ)");
        engine.execute("INSERT INTO fz SELECT 5, TRUE, 'x', 1.5, 2.5, 3.25, PARSE_JSON('[1]'), '2024-01-01', [1], "
            + "{'a':1}, '2024-01-01 00:00:00'");
        assertCells(new String[][] {
            {"SELECT MAX(id), MAX(id > 1) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.ID > 1 AS BOOLEAN)] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), MAX(b AND b) OVER () FROM fz",
                "REFUSED SQL compilation error: | [FZ.B AND FZ.B] is not a valid group by expression"},
            {"SELECT MAX(id), MAX(NOT b) OVER () FROM fz",
                "REFUSED SQL compilation error: | [NOT(FZ.B)] is not a valid group by expression"},
            {"SELECT MAX(id), MAX(id IS NULL) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.ID IS NULL AS BOOLEAN)] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), MAX(id IN (1, 2)) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.ID IN (1, 2) AS BOOLEAN)] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), LAG(id > 1) OVER (ORDER BY 1) FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.ID > 1 AS BOOLEAN)] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), ROW_NUMBER() OVER (PARTITION BY id > 1 ORDER BY 1) FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.ID > 1 AS BOOLEAN)] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), MAX(STARTSWITH(s, 'x')) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(STARTSWITH(FZ.S, 'x') AS BOOLEAN)] is not a valid "
                + "group by expression"},
            {"SELECT MAX(id), MAX(NOT (id > 1)) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(NOT(FZ.ID > 1) AS BOOLEAN)] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), MAX(id BETWEEN 1 AND 2) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST((FZ.ID >= 1) AND (FZ.ID <= 2) AS BOOLEAN)] is not a "
                + "valid group by expression"},
            {"SELECT MAX(id), MAX(s LIKE 'x%') OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.S LIKE 'x%' AS BOOLEAN)] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), MAX((id > 1) AND b) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST((FZ.ID > 1) AND (CAST(FZ.B AS BOOLEAN)) AS BOOLEAN)] "
                + "is not a valid group by expression"},
            {"SELECT MAX(id), MAX(IFF(id > 1, 1, 0)) OVER () FROM fz",
                "REFUSED SQL compilation error: | [IFF(FZ.ID > 1, 1, 0)] is not a valid group by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY NOT b) FROM fz",
                "REFUSED SQL compilation error: | [NOT(FZ.B)] is not a valid group by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY b OR b) FROM fz",
                "REFUSED SQL compilation error: | [FZ.B OR FZ.B] is not a valid group by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY id > 1 DESC) FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.ID > 1 AS BOOLEAN)] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), MAX(TRUE AND b) OVER () FROM fz",
                "REFUSED SQL compilation error: | [TRUE AND FZ.B] is not a valid group by expression"},
            {"SELECT MAX(id), MAX(id::BOOLEAN) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.ID <> 0 AS BOOLEAN)] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY IS_BOOLEAN(id)) FROM fz",
                "REFUSED SQL compilation error: | [CAST(IS_BOOLEAN(CAST(FZ.ID AS VARIANT)) AS BOOLEAN)] is "
                + "not a valid group by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY IS_DECIMAL(id)) FROM fz",
                "REFUSED SQL compilation error: | [CAST(IS_DECIMAL(CAST(FZ.ID AS VARIANT)) AS BOOLEAN)] is "
                + "not a valid group by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY IS_DOUBLE(id)) FROM fz",
                "REFUSED SQL compilation error: | [CAST(IS_DOUBLE(CAST(FZ.ID AS VARIANT)) AS BOOLEAN)] is not"
                + " a valid group by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY IS_REAL(id)) FROM fz",
                "REFUSED SQL compilation error: | [CAST(IS_REAL(CAST(FZ.ID AS VARIANT)) AS BOOLEAN)] is not a"
                + " valid group by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY IS_CHAR(id)) FROM fz",
                "REFUSED SQL compilation error: | [CAST(IS_CHAR(CAST(FZ.ID AS VARIANT)) AS BOOLEAN)] is not a"
                + " valid group by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY IS_BINARY(id)) FROM fz",
                "REFUSED SQL compilation error: | [CAST(IS_BINARY(CAST(FZ.ID AS VARIANT)) AS BOOLEAN)] is not"
                + " a valid group by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY IS_DATE(id)) FROM fz",
                "REFUSED SQL compilation error: | [CAST(IS_DATE(CAST(FZ.ID AS VARIANT)) AS BOOLEAN)] is not a"
                + " valid group by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY IS_DATE_VALUE(id)) FROM fz",
                "REFUSED SQL compilation error: | [CAST(IS_DATE_VALUE(CAST(FZ.ID AS VARIANT)) AS BOOLEAN)] is"
                + " not a valid group by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY IS_TIME(id)) FROM fz",
                "REFUSED SQL compilation error: | [CAST(IS_TIME(CAST(FZ.ID AS VARIANT)) AS BOOLEAN)] is not a"
                + " valid group by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY IS_TIMESTAMP_NTZ(id)) FROM fz",
                "REFUSED SQL compilation error: | [CAST(IS_TIMESTAMP_NTZ(CAST(FZ.ID AS VARIANT)) AS BOOLEAN)]"
                + " is not a valid group by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY IS_TIMESTAMP_LTZ(id)) FROM fz",
                "REFUSED SQL compilation error: | [CAST(IS_TIMESTAMP_LTZ(CAST(FZ.ID AS VARIANT)) AS BOOLEAN)]"
                + " is not a valid group by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY IS_TIMESTAMP_TZ(id)) FROM fz",
                "REFUSED SQL compilation error: | [CAST(IS_TIMESTAMP_TZ(CAST(FZ.ID AS VARIANT)) AS BOOLEAN)] "
                + "is not a valid group by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY IS_ARRAY(a)) FROM fz",
                "REFUSED SQL compilation error: | [CAST(IS_ARRAY(CAST(FZ.A AS VARIANT)) AS BOOLEAN)] is not a"
                + " valid group by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY IS_INTEGER(b)) FROM fz",
                "REFUSED SQL compilation error: | [CAST(IS_INTEGER(CAST(FZ.B AS VARIANT)) AS BOOLEAN)] is not"
                + " a valid group by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY IS_INTEGER(f)) FROM fz",
                "REFUSED SQL compilation error: | [CAST(IS_INTEGER(CAST(FZ.F AS VARIANT)) AS BOOLEAN)] is not"
                + " a valid group by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY IS_INTEGER(s)) FROM fz",
                "REFUSED SQL compilation error: error line 1 at position 38 | Invalid argument types for "
                + "function 'IS_INTEGER': (VARCHAR(16777216))"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY STRIP_NULL_VALUE(id)) FROM fz",
                "REFUSED SQL compilation error: | [STRIP_NULL_VALUE(CAST(FZ.ID AS VARIANT))] is not a valid "
                + "group by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY ARRAY_CONTAINS(id, v)) FROM fz",
                "REFUSED SQL compilation error: | [CAST(ARRAY_CONTAINS(CAST(FZ.ID AS VARIANT), CAST(FZ.V AS "
                + "ARRAY)) AS BOOLEAN)] is not a valid group by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY ARRAY_CONTAINS(v, a)) FROM fz",
                "REFUSED SQL compilation error: | [CAST(ARRAY_CONTAINS(FZ.V, FZ.A) AS BOOLEAN)] is not a "
                + "valid group by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY ARRAY_CONTAINS(s, a)) FROM fz",
                "REFUSED SQL compilation error: error line 1 at position 38 | Invalid argument types for "
                + "function 'ARRAY_CONTAINS': (VARCHAR(16777216), ARRAY)"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY ARRAY_CONTAINS(b, a)) FROM fz",
                "REFUSED SQL compilation error: | [CAST(ARRAY_CONTAINS(CAST(FZ.B AS VARIANT), FZ.A) AS "
                + "BOOLEAN)] is not a valid group by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY TYPEOF(v:a)) FROM fz",
                "REFUSED SQL compilation error: | [TYPEOF(GET(FZ.V, 'a'))] is not a valid group by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY ARRAY_SIZE(v:a)) FROM fz",
                "REFUSED SQL compilation error: | [ARRAY_SIZE(GET(FZ.V, 'a'))] is not a valid group by "
                + "expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY TYPEOF(TO_DECFLOAT(id))) FROM fz",
                "REFUSED SQL compilation error: | [TYPEOF(CAST(CAST(FZ.ID AS DECFLOAT(38)) AS VARIANT))] is "
                + "not a valid group by expression"},
            {"SELECT MAX(id), RANK() OVER (ORDER BY TYPEOF(id::VARIANT)) FROM fz",
                "REFUSED SQL compilation error: | [TYPEOF(CAST(FZ.ID AS VARIANT))] is not a valid group by "
                + "expression"},
            {"SELECT SUM(SUM(s::INT)) OVER () FROM fz",
                "REFUSED Numeric value 'x' is not recognized"},
            {"SELECT MAX(MAX(s::INT)) OVER () FROM fz",
                "REFUSED Numeric value 'x' is not recognized"},
            {"SELECT SUM(SUM(TO_NUMBER(s))) OVER () FROM fz",
                "REFUSED Numeric value 'x' is not recognized"},
            {"SELECT SUM(MAX(s::INT)) OVER () FROM fz",
                "REFUSED Numeric value 'x' is not recognized"},
            {"SELECT SUM(SUM(s::INT)) OVER (PARTITION BY b) FROM fz GROUP BY b",
                "REFUSED Numeric value 'x' is not recognized"},
            {"SELECT MAX(id), SUM(SUM(s::INT)) OVER () FROM fz",
                "REFUSED Numeric value 'x' is not recognized"},
            {"SELECT SUM(SUM(s::DOUBLE)) OVER () FROM fz",
                "REFUSED Numeric value 'x' is not recognized"},
            {"SELECT SUM(SUM(s::INT + 1)) OVER () FROM fz",
                "REFUSED Numeric value 'x' is not recognized"},
            {"SELECT SUM(SUM(s)) OVER () FROM fz",
                "REFUSED Numeric value 'x' is not recognized"},
            {"SELECT SUM(AVG(s::INT)) OVER () FROM fz",
                "REFUSED Numeric value 'x' is not recognized"},
            {"SELECT SUM(SUM(s::DATE)) OVER () FROM fz",
                "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for "
                + "function 'SUM': (DATE)"},
            {"SELECT COUNT(*), SUM(SUM(s::INT)) OVER () FROM fz",
                "REFUSED Numeric value 'x' is not recognized"},
        });
    }

    @Test
    public void decfloatBesideNull() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, d DECFLOAT, s VARCHAR)");
        engine.execute("INSERT INTO fz SELECT 5, 1.5, 'x'");
        assertCells(new String[][] {
            {"SELECT MAX(id), MAX(d + NULL) OVER () FROM fz",
                "REFUSED SQL compilation error: | [FZ.D + (SYSTEM$NULL_TO_DECFLOAT(null))] is not a valid "
                + "group by expression"},
            {"SELECT MAX(id), MAX(NULL - d) OVER () FROM fz",
                "REFUSED SQL compilation error: | [(SYSTEM$NULL_TO_DECFLOAT(null)) - FZ.D] is not a valid "
                + "group by expression"},
            {"SELECT MAX(id), MAX(d / NULL) OVER () FROM fz",
                "REFUSED SQL compilation error: | [FZ.D / (SYSTEM$NULL_TO_DECFLOAT(null))] is not a valid "
                + "group by expression"},
            {"SELECT MAX(id), MAX(d * NULL) OVER () FROM fz",
                "REFUSED SQL compilation error: | [FZ.D * (SYSTEM$NULL_TO_DECFLOAT(null))] is not a valid "
                + "group by expression"},
            {"SELECT MAX(id), MAX(NVL(d, NULL)) OVER () FROM fz",
                "REFUSED SQL compilation error: | [NVL(FZ.D, SYSTEM$NULL_TO_DECFLOAT(null))] is not a valid "
                + "group by expression"},
            {"SELECT MAX(id), MAX(d % 2.5) OVER () FROM fz",
                "REFUSED SQL compilation error: | [FZ.D % (CAST(2.5 AS DECFLOAT(38)))] is not a valid group "
                + "by expression"},
            {"SELECT MAX(id), MAX(2.5 / d) OVER () FROM fz",
                "REFUSED SQL compilation error: | [(CAST(2.5 AS DECFLOAT(38))) / FZ.D] is not a valid group "
                + "by expression"},
            {"SELECT MAX(id), MAX(d <> s) OVER () FROM fz",
                "REFUSED SQL compilation error: | [CAST(FZ.D <> (CAST(FZ.S AS DECFLOAT(38))) AS BOOLEAN)] is "
                + "not a valid group by expression"},
        });
    }

    @Test
    public void faultsOfGroupedValues() {
        engine.execute("CREATE OR REPLACE TABLE fy (id INT, s VARCHAR, b BOOLEAN)");
        engine.execute("INSERT INTO fy VALUES (5, '7', TRUE), (6, '8', FALSE), (0, 'x', TRUE)");
        assertCells(new String[][] {
            {"SELECT b, SUM(SUM(s::INT)) OVER () FROM fy GROUP BY b ORDER BY b",
                "REFUSED Numeric value 'x' is not recognized"},
            {"SELECT b FROM fy GROUP BY b ORDER BY SUM(s::INT)",
                "REFUSED Numeric value 'x' is not recognized"},
            {"SELECT b, RANK() OVER (ORDER BY SUM(s::INT)) FROM fy GROUP BY b",
                "REFUSED Numeric value 'x' is not recognized"},
            {"SELECT b, MAX(id) FROM fy GROUP BY b QUALIFY SUM(SUM(s::INT)) OVER () > 0",
                "REFUSED Numeric value 'x' is not recognized"},
            {"SELECT b, SUM(SUM(id / 0)) OVER () FROM fy GROUP BY b",
                "REFUSED Division by zero"},
            {"SELECT b, SUM(MAX(id)) OVER (ORDER BY MAX(s::INT)) FROM fy GROUP BY b",
                "REFUSED Numeric value 'x' is not recognized"},
            {"SELECT MAX(id) FROM fy ORDER BY SUM(SUM(s::INT)) OVER ()",
                "6"},
            {"SELECT b, LAG(SUM(s::INT)) OVER (ORDER BY b) FROM fy GROUP BY b",
                "REFUSED Numeric value 'x' is not recognized"},
        });
    }
}
