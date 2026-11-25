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
 * Column refusals and DISTINCT WITHIN GROUP leftovers, cell by cell (live-verified): a column a RENAME COLUMN or a
 * COMMENT ON COLUMN does not find is a missing object read as 2001 / 02000, and a view's column is refused for its
 * kind, 2203 / 02000; a DISTINCT window call with an order or a frame is refused for its DISTINCT, 2336 / 42601, ahead
 * of the frame a whole-partition aggregate does not take; a LISTAGG delimiter that is no constant is 1015 / 22023;
 * and a sort key no distinct value determines is named as the plan holds it, a CASE as CASE_FLATTENED and a
 * predicate cast to BOOLEAN. A MERGE ... ALL BY NAME counts no virtual column of its target.
 */
public class ColumnObjectWithinGroupCellsTest extends MeasuredCellsTestSupport {

    @Test
    public void columnObjects() {
        engine.execute("CREATE OR REPLACE TABLE t(id INT, \"Bee\" INT, cc INT)");
        engine.execute("INSERT INTO t VALUES (1, 1, 1), (2, NULL, NULL)");
        engine.execute("CREATE OR REPLACE TABLE la(g INT, x VARCHAR, y INT, \"Xq\" VARCHAR)");
        engine.execute("INSERT INTO la VALUES (1,'a',3,'a'),(1,'b',1,'b'),(1,'a',2,'a'),(2,'c',5,'c')");
        engine.execute("CREATE OR REPLACE VIEW vw AS SELECT id, cc FROM t");
        assertCells(new String[][] {
            {"ALTER TABLE t RENAME COLUMN bee TO zz",
                "REFUSED SQL compilation error: | Object 'BEE' does not exist or not authorized."},
            {"ALTER TABLE t RENAME COLUMN \"bee\" TO zz",
                "REFUSED SQL compilation error: | Object '\"bee\"' does not exist or not authorized."},
            {"ALTER TABLE t RENAME COLUMN \"cc\" TO zz",
                "REFUSED SQL compilation error: | Object '\"cc\"' does not exist or not authorized."},
            {"ALTER TABLE t RENAME COLUMN nosuch TO zz",
                "REFUSED SQL compilation error: | Object 'NOSUCH' does not exist or not authorized."},
            {"ALTER TABLE IF EXISTS t RENAME COLUMN nosuch TO zz",
                "REFUSED SQL compilation error: | Object 'NOSUCH' does not exist or not authorized."},
            {"COMMENT ON COLUMN t.bee IS 'x'",
                "REFUSED SQL compilation error: | Object 'BEE' does not exist or not authorized."},
            {"COMMENT ON COLUMN t.\"bee\" IS 'x'",
                "REFUSED SQL compilation error: | Object 'bee' does not exist or not authorized."},
            {"COMMENT ON COLUMN t.\"cc\" IS 'x'",
                "REFUSED SQL compilation error: | Object 'cc' does not exist or not authorized."},
            {"COMMENT ON COLUMN vw.nosuch IS 'x'",
                "REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type 'TABLE'."},
            {"COMMENT ON COLUMN test_schema.t.nosuch IS 'x'",
                "REFUSED SQL compilation error: | Object 'NOSUCH' does not exist or not authorized."},
            {"SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY CASE WHEN y = 1 THEN 2 ELSE 1 END) "
                + "FROM la",
                "REFUSED SQL compilation error: | [CASE_FLATTENED(LA.Y = 1, 2, 1)] is not a valid order by "
                + "expression"},
            {"SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY CASE y WHEN 1 THEN 2 ELSE 1 END) FROM"
                + " la",
                "REFUSED SQL compilation error: | [CASE LA.Y WHEN CAST(1 AS NUMBER(38,0)) THEN 2 ELSE 1 END] "
                + "is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY CASE WHEN y = 1 THEN 2 END) FROM la",
                "REFUSED SQL compilation error: | [CASE_FLATTENED(LA.Y = 1, 2, SYSTEM$NULL_TO_FIXED(null))] "
                + "is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY CASE WHEN y = 1 THEN 2 WHEN y = 2 "
                + "THEN 3 ELSE 1 END) FROM la",
                "REFUSED SQL compilation error: | [CASE_FLATTENED(LA.Y = 1, 2, LA.Y = 2, 3, 1)] is not a "
                + "valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY IFF(y = 1, 2, 1)) FROM la",
                "REFUSED SQL compilation error: | [IFF(LA.Y = 1, 2, 1)] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY CASE WHEN x = 'a' THEN y ELSE 1 END) "
                + "FROM la",
                "REFUSED SQL compilation error: | [CASE_FLATTENED(LA.X = 'a', LA.Y, 1)] is not a valid order "
                + "by expression"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY x) OVER (ORDER BY y) FROM la",
                "REFUSED SQL compilation error: error line 1 at position 58 | distinct cannot be used with a "
                + "window frame or an order."},
            {"SELECT LISTAGG(DISTINCT x, ',') OVER (ORDER BY y) FROM la",
                "REFUSED SQL compilation error: error line 1 at position 32 | distinct cannot be used with a "
                + "window frame or an order."},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY x) OVER (PARTITION BY g ORDER BY y) "
                + "FROM la",
                "REFUSED SQL compilation error: error line 1 at position 58 | distinct cannot be used with a "
                + "window frame or an order."},
            {"SELECT ARRAY_AGG(DISTINCT x) WITHIN GROUP (ORDER BY x) OVER (ORDER BY y) FROM la",
                "REFUSED SQL compilation error: error line 1 at position 55 | distinct cannot be used with a "
                + "window frame or an order."},
            {"SELECT LISTAGG(x, ',') WITHIN GROUP (ORDER BY x) OVER (ORDER BY y) FROM la",
                "REFUSED SQL compilation error: error line 1 at position 49 | Cumulative window frame "
                + "unsupported for function LISTAGG"},
            {"SELECT LISTAGG(DISTINCT x, ',') OVER (PARTITION BY g ORDER BY y ROWS BETWEEN 1 PRECEDING AND"
                + " CURRENT ROW) FROM la",
                "REFUSED SQL compilation error: error line 1 at position 32 | distinct cannot be used with a "
                + "window frame or an order."},
            {"SELECT LISTAGG(DISTINCT x, y::VARCHAR) WITHIN GROUP (ORDER BY x) FROM la",
                "REFUSED SQL compilation error: | argument 2 to function LISTAGG needs to be constant, found "
                + "'CAST(LA.Y AS VARCHAR(134217728))'"},
            {"SELECT LISTAGG(x, y::VARCHAR) FROM la",
                "REFUSED SQL compilation error: | argument 2 to function LISTAGG needs to be constant, found "
                + "'CAST(LA.Y AS VARCHAR(134217728))'"},
            {"SELECT LISTAGG(x, x) FROM la",
                "REFUSED SQL compilation error: | argument 2 to function LISTAGG needs to be constant, found "
                + "'LA.X'"},
            {"SELECT LISTAGG(x, x) WITHIN GROUP (ORDER BY x) FROM la",
                "REFUSED SQL compilation error: | argument 2 to function LISTAGG needs to be constant, found "
                + "'LA.X'"},
            {"SELECT LISTAGG(x, x) OVER (PARTITION BY g) FROM la",
                "REFUSED SQL compilation error: | argument 2 to function LISTAGG needs to be constant, found "
                + "'LA.X'"},
            {"SELECT LISTAGG(x, ',' || 'q') FROM la",
                "a,qb,qa,qc"},
            {"SELECT LISTAGG(x, UPPER(x)) FROM la",
                "REFUSED SQL compilation error: | argument 2 to function LISTAGG needs to be constant, found "
                + "'UPPER(LA.X)'"},
            {"SELECT LISTAGG(x, RANDOM()::VARCHAR) FROM la",
                "REFUSED SQL compilation error: | argument 2 to function LISTAGG needs to be constant, found "
                + "'CAST(RANDOM() AS VARCHAR(134217728))'"},
            {"SELECT LISTAGG(x, (SELECT ',')) FROM la",
                "REFUSED SQL compilation error: | argument 2 to function LISTAGG needs to be constant, found "
                + "'(SELECT ',' AS \"','\" FROM (VALUES (null)) DUAL)'"},
            {"SELECT LISTAGG(x, \"Xq\") FROM la",
                "REFUSED SQL compilation error: | argument 2 to function LISTAGG needs to be constant, found "
                + "'LA.\"Xq\"'"},
            {"SELECT g, LISTAGG(x, g::VARCHAR) FROM la GROUP BY g",
                "REFUSED SQL compilation error: | argument 2 to function LISTAGG needs to be constant, found "
                + "'CAST(LA.G AS VARCHAR(134217728))'"},
            {"SELECT LISTAGG(x, NULL) FROM la",
                "abac"},
            {"SELECT LISTAGG(x, 1) FROM la",
                "a1b1a1c"},
            {"SELECT LISTAGG(x, CURRENT_DATE()::VARCHAR) IS NOT NULL FROM la",
                "REFUSED SQL compilation error: | argument 2 to function LISTAGG needs to be constant, found "
                + "'CAST(CURRENT_DATE() AS VARCHAR(134217728))'"},
        });
    }

    @Test
    public void windowDistinct() {
        engine.execute("CREATE OR REPLACE TABLE la(g INT, x VARCHAR, y INT, \"Xq\" VARCHAR)");
        engine.execute("INSERT INTO la VALUES (1,'a',3,'a'),(1,'b',1,'b'),(1,'a',2,'a'),(2,'c',5,'c')");
        engine.execute("CREATE OR REPLACE TABLE t(id INT, cc INT)");
        engine.execute("CREATE OR REPLACE VIEW vw AS SELECT id, cc FROM t");
        assertCells(new String[][] {
            {"SELECT MEDIAN(DISTINCT y) OVER (ORDER BY y) FROM la",
                "REFUSED SQL compilation error: error line 1 at position 26 | distinct cannot be used with a "
                + "window frame or an order."},
            {"SELECT HASH_AGG(DISTINCT x) OVER (ORDER BY y) FROM la",
                "REFUSED SQL compilation error: error line 1 at position 28 | distinct cannot be used with a "
                + "window frame or an order."},
            {"SELECT BITAND_AGG(DISTINCT y) OVER (ORDER BY y) FROM la",
                "REFUSED SQL compilation error: error line 1 at position 30 | distinct cannot be used with a "
                + "window frame or an order."},
            {"SELECT KURTOSIS(DISTINCT y) OVER (ORDER BY y) FROM la",
                "REFUSED SQL compilation error: error line 1 at position 28 | distinct cannot be used with a "
                + "window frame or an order."},
            {"SELECT APPROX_COUNT_DISTINCT(DISTINCT x) OVER (ORDER BY y) FROM la",
                "REFUSED SQL compilation error: error line 1 at position 41 | distinct cannot be used with a "
                + "window frame or an order."},
            {"SELECT LISTAGG(DISTINCT x, ',') OVER (ORDER BY y RANGE BETWEEN UNBOUNDED PRECEDING AND "
                + "UNBOUNDED FOLLOWING) FROM la",
                "REFUSED SQL compilation error: error line 1 at position 32 | distinct cannot be used with a "
                + "window frame or an order."},
            {"SELECT LISTAGG(DISTINCT x, ',') OVER (PARTITION BY g ORDER BY y ROWS BETWEEN 1 PRECEDING AND"
                + " CURRENT ROW) FROM la",
                "REFUSED SQL compilation error: error line 1 at position 32 | distinct cannot be used with a "
                + "window frame or an order."},
            {"SELECT LISTAGG(DISTINCT x, ',') OVER (PARTITION BY g ORDER BY y ROWS UNBOUNDED PRECEDING) "
                + "FROM la",
                "REFUSED SQL compilation error: error line 1 at position 32 | distinct cannot be used with a "
                + "window frame or an order."},
            {"SELECT MEDIAN(DISTINCT y) OVER (PARTITION BY g ORDER BY y ROWS BETWEEN 1 PRECEDING AND 1 "
                + "FOLLOWING) FROM la",
                "REFUSED SQL compilation error: error line 1 at position 26 | distinct cannot be used with a "
                + "window frame or an order."},
            {"SELECT ANY_VALUE(DISTINCT x) OVER (ORDER BY y) FROM la",
                "REFUSED SQL compilation error: error line 1 at position 29 | distinct cannot be used with a "
                + "window frame or an order."},
            {"SELECT CORR(DISTINCT y, g) OVER (ORDER BY y) FROM la",
                "REFUSED SQL compilation error: error line 1 at position 27 | distinct cannot be used with a "
                + "window frame or an order."},
            {"SELECT OBJECT_AGG(DISTINCT x, y::VARIANT) OVER (ORDER BY y) FROM la",
                "REFUSED SQL compilation error: error line 1 at position 42 | distinct cannot be used with a "
                + "window frame or an order."},
            {"SELECT KURTOSIS(DISTINCT y) OVER (ORDER BY y RANGE BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED"
                + " FOLLOWING) FROM la",
                "REFUSED SQL compilation error: error line 1 at position 28 | distinct cannot be used with a "
                + "window frame or an order."},
            {"SELECT MEDIAN(DISTINCT y) OVER (ORDER BY y RANGE BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED "
                + "FOLLOWING) FROM la",
                "REFUSED SQL compilation error: error line 1 at position 26 | distinct cannot be used with a "
                + "window frame or an order."},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY y) OVER (ORDER BY y) FROM la",
                "REFUSED SQL compilation error: error line 1 at position 58 | distinct cannot be used with a "
                + "window frame or an order."},
            {"SELECT LISTAGG(DISTINCT x, y::VARCHAR) OVER (ORDER BY y) FROM la",
                "REFUSED SQL compilation error: error line 1 at position 39 | distinct cannot be used with a "
                + "window frame or an order."},
            {"SELECT SUM(DISTINCT y) OVER (ORDER BY y) FROM la",
                "REFUSED SQL compilation error: error line 1 at position 23 | distinct cannot be used with a "
                + "window frame or an order."},
            {"SELECT APPROX_TOP_K(x, y) FROM la",
                "REFUSED SQL compilation error: | argument 2 to function APPROX_TOP_K needs to be constant, "
                + "found 'LA.Y'"},
            {"SELECT PERCENTILE_CONT(y) WITHIN GROUP (ORDER BY y) FROM la",
                "REFUSED SQL compilation error: | argument 1 to function PERCENTILE_CONT needs to be "
                + "constant, found 'LA.Y'"},
            {"SELECT APPROX_PERCENTILE_ESTIMATE(APPROX_PERCENTILE_ACCUMULATE(y), y) FROM la",
                "REFUSED SQL compilation error: | argument 2 to function APPROX_PERCENTILE_ESTIMATE needs to "
                + "be constant, found 'LA.Y'"},
            {"COMMENT ON COLUMN vw.id IS 'x'",
                "REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type 'TABLE'."},
            {"COMMENT ON COLUMN vw.nosuch IS 'x'",
                "REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type 'TABLE'."},
            {"SELECT comment FROM information_schema.columns WHERE table_name = 'VW' AND column_name = "
                + "'ID' AND table_schema = CURRENT_SCHEMA()",
                "null"},
            {"ALTER VIEW vw ALTER COLUMN nosuch COMMENT 'x'",
                "REFUSED SQL compilation error: error line 1 at position 27 | invalid identifier 'NOSUCH'"},
            {"ALTER VIEW vw RENAME COLUMN id TO zz",
                "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'RENAME'."},
        });
    }

    @Test
    public void sortKeyEcho() {
        engine.execute("CREATE OR REPLACE TABLE la(g INT, x VARCHAR, y INT, \"Xq\" VARCHAR)");
        engine.execute("INSERT INTO la VALUES (1,'a',3,'a'),(1,'b',1,'b'),(1,'a',2,'a'),(2,'c',5,'c')");
        engine.execute("CREATE OR REPLACE TABLE \"lower t\"(x VARCHAR, y INT)");
        engine.execute("CREATE OR REPLACE TABLE t(id INT, cc INT)");
        engine.execute("CREATE OR REPLACE VIEW vw AS SELECT id, cc FROM t");
        assertCells(new String[][] {
            {"SELECT LAG(DISTINCT x) OVER (ORDER BY y) FROM la",
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'DISTINCT'. |"
                + " syntax error line 1 at position 10 unexpected '('."},
            {"SELECT FIRST_VALUE(DISTINCT x) OVER (ORDER BY y ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) "
                + "FROM la",
                "REFUSED SQL compilation error: error line 1 at position 31 | distinct cannot be used with a "
                + "window frame or an order."},
            {"SELECT NTILE(DISTINCT 2) OVER (ORDER BY y ROWS 1 PRECEDING) FROM la",
                "REFUSED SQL compilation error: error line 1 at position 25 | distinct cannot be used with a "
                + "window frame or an order."},
            {"SELECT LAG(DISTINCT x) OVER (ORDER BY y ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) FROM la",
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'DISTINCT'. |"
                + " syntax error line 1 at position 10 unexpected '('."},
            {"SELECT ROW_NUMBER(DISTINCT x) OVER (ORDER BY y ROWS 1 PRECEDING) FROM la",
                "REFUSED SQL compilation error: error line 1 at position 30 | distinct cannot be used with a "
                + "window frame or an order."},
            {"SELECT CUME_DIST(DISTINCT x) OVER (ORDER BY y ROWS 1 PRECEDING) FROM la",
                "REFUSED SQL compilation error: error line 1 at position 29 | distinct cannot be used with a "
                + "window frame or an order."},
            {"COMMENT IF EXISTS ON COLUMN vw.id IS 'x'",
                "REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type 'TABLE'."},
            {"COMMENT ON COLUMN test_db.test_schema.vw.id IS 'x'",
                "REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type 'TABLE'."},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY y) FROM la",
                "REFUSED SQL compilation error: | [LA.Y] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT UPPER(x), ',') WITHIN GROUP (ORDER BY x) FROM la",
                "REFUSED SQL compilation error: | [LA.X] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY \"Xq\") FROM la",
                "REFUSED SQL compilation error: | [LA.\"Xq\"] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY y) FROM (SELECT column1 x, column2 y "
                + "FROM VALUES ('a',3),('b',1),('a',2))",
                "REFUSED SQL compilation error: | [\"values\".Y] is not a valid order by expression"},
            {"SELECT ARRAY_AGG(DISTINCT x) WITHIN GROUP (ORDER BY y) FROM la",
                "REFUSED SQL compilation error: | [LA.Y] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY y) FROM la t",
                "REFUSED SQL compilation error: | [T.Y] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY y) FROM la \"tt\"",
                "REFUSED SQL compilation error: | [\"tt\".Y] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY y) FROM (SELECT x, y FROM la) sq",
                "REFUSED SQL compilation error: | [SQ.Y] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY y) FROM (SELECT x, y + 1 y FROM la) "
                + "sq",
                "REFUSED SQL compilation error: | [SQ.Y] is not a valid order by expression"},
            {"WITH c AS (SELECT x, y FROM la) SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY y) "
                + "FROM c",
                "REFUSED SQL compilation error: | [C.Y] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT a.x, ',') WITHIN GROUP (ORDER BY b.y) FROM la a JOIN la b ON a.g = "
                + "b.g",
                "REFUSED SQL compilation error: | [B.Y] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY y) FROM \"lower t\"",
                "REFUSED SQL compilation error: | [\"lower t\".Y] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY y + g) FROM la",
                "REFUSED SQL compilation error: | [LA.Y + LA.G] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY g, y) FROM la",
                "REFUSED SQL compilation error: | [LA.G] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY x || y) FROM la",
                "REFUSED SQL compilation error: | [LA.X || (CAST(LA.Y AS VARCHAR(134217728)))] is not a valid"
                + " order by expression"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY y) FROM (SELECT column1 x, column2 y "
                + "FROM VALUES ('a',3)) v",
                "REFUSED SQL compilation error: | [V.Y] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x || 'q', ',') WITHIN GROUP (ORDER BY UPPER(x) || 'q') FROM la",
                "REFUSED SQL compilation error: | [(UPPER(LA.X)) || 'q'] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY SUM(y)) FROM la",
                "REFUSED SQL compilation error: | [SUM(LA.Y)] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY RANDOM()) FROM la",
                "REFUSED SQL compilation error: | [RANDOM()] is not a valid order by expression"},
            {"SELECT ARRAY_AGG(DISTINCT x) WITHIN GROUP (ORDER BY y) OVER () FROM la t",
                "REFUSED SQL compilation error: | [T.Y] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY CASE WHEN y = 1 THEN 2 ELSE 1 END) "
                + "FROM la",
                "REFUSED SQL compilation error: | [CASE_FLATTENED(LA.Y = 1, 2, 1)] is not a valid order by "
                + "expression"},
            {"SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY ROW_NUMBER() OVER (ORDER BY x)) FROM "
                + "la",
                "REFUSED SQL compilation error: | [ROW_NUMBER() OVER (ORDER BY LA.X ASC NULLS LAST)] is not a"
                + " valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY CASE y WHEN 1 THEN 2 ELSE 1 END) FROM"
                + " la",
                "REFUSED SQL compilation error: | [CASE LA.Y WHEN CAST(1 AS NUMBER(38,0)) THEN 2 ELSE 1 END] "
                + "is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY CASE WHEN y = 1 THEN 2 END) FROM la",
                "REFUSED SQL compilation error: | [CASE_FLATTENED(LA.Y = 1, 2, SYSTEM$NULL_TO_FIXED(null))] "
                + "is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY CASE WHEN y = 1 THEN 2 WHEN y = 2 "
                + "THEN 3 ELSE 1 END) FROM la",
                "REFUSED SQL compilation error: | [CASE_FLATTENED(LA.Y = 1, 2, LA.Y = 2, 3, 1)] is not a "
                + "valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY IFF(y = 1, 2, 1)) FROM la",
                "REFUSED SQL compilation error: | [IFF(LA.Y = 1, 2, 1)] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY CASE WHEN x = 'a' THEN y ELSE 1 END) "
                + "FROM la",
                "REFUSED SQL compilation error: | [CASE_FLATTENED(LA.X = 'a', LA.Y, 1)] is not a valid order "
                + "by expression"},
            {"SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY CASE WHEN y > 1 THEN 'p' ELSE x END) "
                + "FROM la",
                "REFUSED SQL compilation error: | [CASE_FLATTENED(LA.Y > 1, 'p', LA.X)] is not a valid order "
                + "by expression"},
            {"SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY y::VARCHAR) FROM la",
                "REFUSED SQL compilation error: | [CAST(LA.Y AS VARCHAR(134217728))] is not a valid order by "
                + "expression"},
            {"SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY y / 2) FROM la",
                "REFUSED SQL compilation error: | [SCALED_ROUND_INT_DIVIDE(LA.Y, 2)] is not a valid order by "
                + "expression"},
            {"SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY y IN (1, 2)) FROM la",
                "REFUSED SQL compilation error: | [CAST(LA.Y IN (1, 2) AS BOOLEAN)] is not a valid order by "
                + "expression"},
            {"SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY COALESCE(y, 0)) FROM la",
                "REFUSED SQL compilation error: | [IFNULL(LA.Y, 0)] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY AVG(y)) FROM la",
                "REFUSED SQL compilation error: | [SCALED_ROUND_INT_DIVIDE(SUM(LA.Y), COUNT(LA.Y))] is not a "
                + "valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY y * 1.5) FROM la",
                "REFUSED SQL compilation error: | [LA.Y * 1.5] is not a valid order by expression"},
            {"SELECT ARRAY_AGG(DISTINCT x) WITHIN GROUP (ORDER BY CASE WHEN y = 1 THEN NULL ELSE 1 END) "
                + "FROM la",
                "REFUSED SQL compilation error: | [CASE_FLATTENED(LA.Y = 1, SYSTEM$NULL_TO_FIXED(null), 1)] "
                + "is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY TO_VARCHAR(y)) FROM la",
                "REFUSED SQL compilation error: | [CAST(LA.Y AS VARCHAR(134217728))] is not a valid order by "
                + "expression"},
            {"SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY x LIKE 'a%' AND y > 0) FROM la",
                "REFUSED SQL compilation error: | [CAST((LA.X LIKE 'a%') AND (LA.Y > 0) AS BOOLEAN)] is not a"
                + " valid order by expression"},
        });
    }

    @Test
    public void predicateKeys() {
        engine.execute("CREATE OR REPLACE TABLE la(g INT, x VARCHAR, y INT, b BOOLEAN)");
        engine.execute("INSERT INTO la VALUES (1,'a',3,TRUE),(1,'b',1,FALSE),(1,'a',2,NULL),(2,'c',5,TRUE)");
        engine.execute("CREATE OR REPLACE TABLE t(id INT, cc INT)");
        engine.execute("CREATE OR REPLACE VIEW vw AS SELECT id, cc FROM t");
        assertCells(new String[][] {
            {"SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY y > 1) FROM la",
                "REFUSED SQL compilation error: | [CAST(LA.Y > 1 AS BOOLEAN)] is not a valid order by "
                + "expression"},
            {"SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY y = 1) FROM la",
                "REFUSED SQL compilation error: | [CAST(LA.Y = 1 AS BOOLEAN)] is not a valid order by "
                + "expression"},
            {"SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY NOT (y > 1)) FROM la",
                "REFUSED SQL compilation error: | [CAST(NOT(LA.Y > 1) AS BOOLEAN)] is not a valid order by "
                + "expression"},
            {"SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY y IS NULL) FROM la",
                "REFUSED SQL compilation error: | [CAST(LA.Y IS NULL AS BOOLEAN)] is not a valid order by "
                + "expression"},
            {"SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY y BETWEEN 1 AND 2) FROM la",
                "REFUSED SQL compilation error: | [CAST((LA.Y >= 1) AND (LA.Y <= 2) AS BOOLEAN)] is not a "
                + "valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY y > 1 OR g = 1) FROM la",
                "REFUSED SQL compilation error: | [CAST((LA.Y > 1) OR (LA.G = 1) AS BOOLEAN)] is not a valid "
                + "order by expression"},
            {"SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY b) FROM la",
                "REFUSED SQL compilation error: | [LA.B] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY y IN (1)) FROM la",
                "REFUSED SQL compilation error: | [CAST(LA.Y IN (1) AS BOOLEAN)] is not a valid order by "
                + "expression"},
            {"SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY y IN (1, 2) DESC) FROM la",
                "REFUSED SQL compilation error: | [CAST(LA.Y IN (1, 2) AS BOOLEAN)] is not a valid order by "
                + "expression"},
            {"SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY IFF(y IN (1, 2), 1, 0)) FROM la",
                "REFUSED SQL compilation error: | [IFF(LA.Y IN (1, 2), 1, 0)] is not a valid order by "
                + "expression"},
            {"SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY NOT b) FROM la",
                "REFUSED SQL compilation error: | [NOT(LA.B)] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY y NOT IN (1, 2)) FROM la",
                "REFUSED SQL compilation error: | [CAST(NOT(LA.Y IN (1, 2)) AS BOOLEAN)] is not a valid order"
                + " by expression"},
            {"SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY y > 1 AND g = 1) FROM la",
                "REFUSED SQL compilation error: | [CAST((LA.Y > 1) AND (LA.G = 1) AS BOOLEAN)] is not a valid"
                + " order by expression"},
            {"COMMENT IF EXISTS ON COLUMN vw.id IS 'x'",
                "REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type 'TABLE'."},
            {"COMMENT ON COLUMN test_db.test_schema.vw.id IS 'x'",
                "REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type 'TABLE'."},
            {"COMMENT ON COLUMN vw.id IS 'x'",
                "REFUSED SQL compilation error: Object found is of type 'VIEW', not specified type 'TABLE'."},
        });
    }

    @Test
    public void handlerCodes() {
        engine.execute("CREATE OR REPLACE TABLE t(id INT, \"Bee\" INT, cc INT)");
        engine.execute("CREATE OR REPLACE TABLE la(g INT, x VARCHAR, y INT)");
        engine.execute("INSERT INTO la VALUES (1,'a',3),(1,'b',1),(1,'a',2),(2,'c',5)");
        engine.execute("CREATE OR REPLACE VIEW vw AS SELECT id, cc FROM t");
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ BEGIN ALTER TABLE t RENAME COLUMN nosuch TO zz; RETURN 'ok'; EXCEPTION "
                + "WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE; END $$",
                "2001 02000"},
            {"EXECUTE IMMEDIATE $$ BEGIN COMMENT ON COLUMN t.bee IS 'x'; RETURN 'ok'; EXCEPTION WHEN OTHER"
                + " THEN RETURN SQLCODE || ' ' || SQLSTATE; END $$",
                "2001 02000"},
            {"EXECUTE IMMEDIATE $$ BEGIN COMMENT ON COLUMN vw.id IS 'x'; RETURN 'ok'; EXCEPTION WHEN OTHER"
                + " THEN RETURN SQLCODE || ' ' || SQLSTATE; END $$",
                "2203 02000"},
            {"EXECUTE IMMEDIATE $$ DECLARE r VARCHAR; BEGIN SELECT LISTAGG(x, x) INTO :r FROM la; RETURN "
                + "r; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE; END $$",
                "1015 22023"},
            {"EXECUTE IMMEDIATE $$ DECLARE r VARCHAR; BEGIN SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP "
                + "(ORDER BY CASE WHEN y = 1 THEN 2 ELSE 1 END) INTO :r FROM la; RETURN r; EXCEPTION WHEN OTHER"
                + " THEN RETURN SQLCODE || ' ' || SQLSTATE; END $$",
                "2024 42601"},
            {"EXECUTE IMMEDIATE $$ DECLARE r VARCHAR; BEGIN SELECT APPROX_TOP_K(x, y)::VARCHAR INTO :r "
                + "FROM la; RETURN r; EXCEPTION WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE; END $$",
                "1015 22023"},
            {"EXECUTE IMMEDIATE $$ BEGIN ALTER TABLE t RENAME COLUMN cc TO \"Bee\"; RETURN 'ok'; EXCEPTION"
                + " WHEN OTHER THEN RETURN SQLCODE || ' ' || SQLSTATE; END $$",
                "2002 42710"},
        });
    }

    @Test
    public void mergeVirtualColumns() {
        engine.execute("CREATE OR REPLACE TABLE tv(id INT, v INT AS (id * 2))");
        engine.execute("CREATE OR REPLACE TABLE sv(id INT)");
        engine.execute("INSERT INTO sv VALUES (1)");
        engine.execute("CREATE OR REPLACE TABLE sv2(id INT, v INT)");
        engine.execute("INSERT INTO sv2 VALUES (2, 9)");
        assertCells(new String[][] {
            {"MERGE INTO tv USING sv2 ON tv.id = sv2.id WHEN NOT MATCHED THEN INSERT ALL BY NAME",
                "REFUSED SQL compilation error: error line 1 at position 0 | Column count mismatch in MERGE "
                + "statement with ALL BY NAME subclause, source table has (2) columns but target table has (1)."},
            {"MERGE INTO tv USING sv2 ON tv.id = sv2.id WHEN MATCHED THEN UPDATE ALL BY NAME",
                "REFUSED SQL compilation error: error line 1 at position 0 | Column count mismatch in MERGE "
                + "statement with ALL BY NAME subclause, source table has (2) columns but target table has (1)."},
        });
    }
}
