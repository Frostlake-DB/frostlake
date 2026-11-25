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
 * The REGR_ and pair-statistic family over exact NUMBER arguments reads every value at the scale its column or
 * expression declares, not at the digits the value was written with: a NUMBER(38,7) column holding 13100000000000 is
 * refused when its square leaves the carrier at that scale, wherever the column comes from (a table, a VALUES list, a
 * UNION, a conditional, a cast or a derived expression), and REGR_SXY sums its products at the scale the two declared
 * scales give, so a NUMBER(38,13) column holding seven-decimal values rounds its products at thirteen places
 * (live-verified).
 */
public class RegrDeclaredScaleCellsTest extends MeasuredCellsTestSupport {

    @Test
    public void tableColumns() {
        engine.execute("CREATE TABLE t4 (x NUMBER(10,4), y NUMBER(10,4))");
        engine.execute("INSERT INTO t4 VALUES (1.5, 2.25), (2, 3.1), (3.125, 0.5)");
        engine.execute("CREATE TABLE t20 (x NUMBER(38,20), y NUMBER(38,20))");
        engine.execute("INSERT INTO t20 VALUES (0.1234567, 0.12345678), (0.7654321, 0.87654321), (0.3333333, "
            + "0.11111111)");
        engine.execute("CREATE TABLE t20b (x NUMBER(38,20), y NUMBER(38,20))");
        engine.execute("INSERT INTO t20b VALUES (0.0000001, 0.0000005), (0.0000003, 0.0000001), (0.0000002, "
            + "0.0000009)");
        engine.execute("CREATE TABLE t7 (x NUMBER(20,7), y NUMBER(20,7))");
        engine.execute("INSERT INTO t7 VALUES (0.0000001, 0.0000005), (0.0000003, 0.0000001), (0.0000002, 0.0000009)");
        engine.execute("CREATE TABLE tbig (x NUMBER(38,7), y NUMBER(38,7))");
        engine.execute("INSERT INTO tbig VALUES (13100000000000, 13100000000000), (1, 2)");
        engine.execute("CREATE TABLE tmix (x NUMBER(38,7), y NUMBER(38,0))");
        engine.execute("INSERT INTO tmix VALUES (13100000000000, 13100000000000), (1, 2)");
        engine.execute("CREATE TABLE tbig2 (x NUMBER(38,2), y NUMBER(38,2))");
        engine.execute("INSERT INTO tbig2 VALUES (13100000000000000, 13100000000000000), (1, 2)");
        assertCells(new String[][] {
            {"SELECT REGR_SXY(y, x) FROM tbig",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT REGR_R2(y, x) FROM tbig",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT REGR_SXY(y, x) OVER () FROM tbig",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
        });
    }

    @Test
    public void valuesColumns() {
        assertCells(new String[][] {
            {"SELECT REGR_SXY(y, x) FROM (SELECT column1 x, column2 y FROM VALUES (13100000000000, "
                + "13100000000000), (0.0000001, 0.0000001))",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT REGR_R2(y, x) FROM (SELECT column1 x, column2 y FROM VALUES (13100000000000, "
                + "13100000000000), (1, 2), (0.0000001, 0))",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT REGR_SXY(y, x) OVER (ORDER BY x) FROM (SELECT column1 x, column2 y FROM VALUES "
                + "(13100000000000, 13100000000000), (1, 2), (0.0000001, 0)) ORDER BY 1",
                "REFUSED SQL compilation error: error line 1 at position 22 | Cumulative window frame "
                + "unsupported for function REGR_SXY"},
            {"SELECT REGR_R2(y, x) OVER () FROM (SELECT column1 x, column2 y FROM VALUES (13100000000000, "
                + "13100000000000), (1, 2), (0.0000001, 0)) LIMIT 1",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
        });
    }

    @Test
    public void derivedColumns() {
        engine.execute("CREATE TABLE tc AS SELECT column1 + 0.0000000 x, column1 + 0.0000000 y FROM VALUES "
            + "(13100000000000), (1)");
        engine.execute("CREATE TABLE ti (x NUMBER(38,7), y NUMBER(38,7))");
        engine.execute("INSERT INTO ti SELECT column1, column1 FROM VALUES (13100000000000), (1)");
        engine.execute("CREATE TABLE tu (x NUMBER(38,7), y NUMBER(38,7))");
        engine.execute("INSERT INTO tu VALUES (1, 1), (2, 2)");
        engine.execute("UPDATE tu SET x = 13100000000000, y = 13100000000000 WHERE x = 1");
        engine.execute("CREATE TABLE t20 (x NUMBER(38,20), y NUMBER(38,20))");
        engine.execute("INSERT INTO t20 SELECT column1, column2 FROM VALUES (0.1234567, 0.1234567), (0.7654321, "
            + "0.87654321), (0.3, 0.1)");
        assertCells(new String[][] {
            {"SELECT REGR_SXY(y, x) FROM (SELECT 13100000000000 x, 13100000000000 y UNION ALL SELECT "
                + "0.0000001, 0.0000001)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT REGR_SXY(y, x) FROM (SELECT IFF(column1 > 5, 13100000000000, 0.0000001) x, "
                + "IFF(column1 > 5, 13100000000000, 0.0000001) y FROM VALUES (10), (1))",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT REGR_SXY(y, x) FROM (SELECT COALESCE(column1, 0.0000001) x, COALESCE(column1, "
                + "0.0000001) y FROM VALUES (13100000000000), (1))",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT REGR_SXY(column1::NUMBER(38,7), column1::NUMBER(38,7)) FROM VALUES (13100000000000), "
                + "(1)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT REGR_SXY(column1::NUMBER(38,7), column1::NUMBER(38,7)) OVER () FROM VALUES "
                + "(13100000000000), (1)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT REGR_R2(column1::NUMBER(38,7), column1::NUMBER(38,7) + 1) FROM VALUES "
                + "(13100000000000), (1)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT REGR_SXY(y, x) FROM ti",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT REGR_SXY(y, x) FROM tu",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
        });
    }

    @Test
    public void relationalSources() {
        engine.execute("CREATE TABLE bt (k INT, x NUMBER(38,7), y NUMBER(38,7))");
        engine.execute("INSERT INTO bt VALUES (1, 13100000000000, 13100000000000), (2, 1, 2), (3, 4, 4)");
        engine.execute("CREATE TABLE bs (k INT, x NUMBER(38,7), y NUMBER(38,7))");
        engine.execute("INSERT INTO bs VALUES (1, '13100000000000', '13100000000000'), (2, '1', '2'), (3, '4', 4)");
        engine.execute("CREATE VIEW bv AS SELECT * FROM bt");
        engine.execute("CREATE TABLE bk (k INT)");
        engine.execute("INSERT INTO bk VALUES (1), (2), (3)");
        engine.execute("CREATE FUNCTION f7(v NUMBER(38,0)) RETURNS NUMBER(38,7) AS 'v'");
        engine.execute("CREATE TABLE bc CLONE bt");
        engine.execute("CREATE TABLE bd (k INT, x NUMBER(38,7) DEFAULT 13100000000000, y NUMBER(38,7) DEFAULT "
            + "13100000000000)");
        engine.execute("INSERT INTO bd (k) VALUES (1)");
        engine.execute("INSERT INTO bd VALUES (2, 1, 2)");
        assertCells(new String[][] {
            {"SELECT REGR_SXY(y, x) FROM bs",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT REGR_SXY(y, x) FROM bv",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT REGR_SXY(bt.y, bt.x) FROM bt JOIN bk ON bt.k = bk.k",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT REGR_SXY(y, x) FROM (SELECT DISTINCT x, y FROM bt)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT REGR_SXY(y, x) FROM (SELECT x, y FROM bt ORDER BY x LIMIT 3)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"WITH c AS (SELECT x, y FROM bt) SELECT REGR_SXY(y, x) FROM c",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT REGR_SXY(y, x) OVER (PARTITION BY k > 5) FROM bt ORDER BY 1 LIMIT 1",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT REGR_SXY(y, x) FROM bc",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT REGR_SXY(y, x) FROM bd",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT REGR_SXY(y, x) FROM (SELECT x, y FROM bt GROUP BY ALL)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT k > 1, REGR_SXY(y, x) FROM bt GROUP BY 1 ORDER BY 1",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT REGR_SXY(y, x) FROM bt WHERE k IN (SELECT k FROM bk)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT REGR_SXY(b2.y, b1.x) FROM bt b1 JOIN bt b2 ON b1.k = b2.k",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT REGR_SXY(v.value::NUMBER(38,7), v.value::NUMBER(38,7)) FROM TABLE(FLATTEN(INPUT => "
                + "ARRAY_CONSTRUCT(13100000000000, 1, 4))) v",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT REGR_SXY(y, x) FROM (SELECT x, y FROM bt UNION SELECT x, y FROM bt)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT REGR_SXY(y, x) FROM (SELECT * FROM bt QUALIFY ROW_NUMBER() OVER (ORDER BY k) > 0)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT REGR_SXY(y, x) FROM (SELECT LAG(x) OVER (ORDER BY k) x, LAG(y) OVER (ORDER BY k) y "
                + "FROM bt)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT REGR_SXY(y, x) FROM (SELECT FIRST_VALUE(x) OVER (ORDER BY k) x, y FROM bt)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT REGR_SXY(y, x) FROM (SELECT MIN(x) x, MIN(y) y FROM bt GROUP BY k)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT REGR_R2(y, x), COVAR_SAMP(y, x) FROM bs",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
        });
    }

    @Test
    public void castsAndConditionals() {
        engine.execute("CREATE TABLE bf (x FLOAT, y FLOAT, s VARCHAR)");
        engine.execute("INSERT INTO bf VALUES (13100000000000, 13100000000000, '13100000000000'), (1, 2, '1'), (4, "
            + "4, '4')");
        assertCells(new String[][] {
            {"SELECT REGR_SXY(y::NUMBER(38,7), x::NUMBER(38,7)) FROM bf",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT REGR_SXY(s::NUMBER(38,7), s::NUMBER(38,7)) FROM bf",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT REGR_SXY(TRY_CAST(s AS NUMBER(38,7)), TRY_CAST(s AS NUMBER(38,7))) FROM bf",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT REGR_SXY(TRY_TO_NUMBER(s, 38, 7), TRY_TO_DECIMAL(s, 38, 7)) FROM bf",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT REGR_SXY(ZEROIFNULL(s::NUMBER(38,7)), NULLIF(s::NUMBER(38,7), -1)) FROM bf",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT REGR_SXY(IFNULL(s::NUMBER(38,7), 0), NVL2(s, s::NUMBER(38,7), 0)) FROM bf",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT REGR_SXY(DECODE(s, '1', 1, s::NUMBER(38,7)), CEIL(s::NUMBER(38,7), 7)) FROM bf",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT REGR_SXY(FLOOR(s::NUMBER(38,7), 7), MOD(s::NUMBER(38,7), 100000000000000)) FROM bf",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT REGR_SXY(x::NUMBER(38,7), s::NUMBER(38,7)) FROM bf",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT REGR_SXY(y, x) FROM (SELECT s::NUMBER(38,7) x, s::NUMBER(38,7) y FROM bf)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT REGR_SXY(PARSE_JSON(s)::NUMBER(38,7), PARSE_JSON(s)::NUMBER(38,7)) OVER () FROM bf "
                + "LIMIT 1",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT REGR_R2(s::NUMBER(38,7), s::NUMBER(38,7) + 1), REGR_R2(x::NUMBER(38,7), 1) FROM bf",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT REGR_R2(1, s::NUMBER(38,7)) FROM bf",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
        });
    }

    @Test
    public void productScale() {
        engine.execute("CREATE TABLE b (x NUMBER(38,7), y NUMBER(38,7))");
        engine.execute("INSERT INTO b VALUES (0.1234567, 0.1234567), (0.7654321, 0.8765437), (0.3, 0.1)");
        engine.execute("CREATE TABLE t13 (x NUMBER(38,13), y NUMBER(38,13))");
        engine.execute("INSERT INTO t13 VALUES (0.1234567, 0.1234567), (0.7654321, 0.8765437), (0.3, 0.1)");
        assertCells(new String[][] {
            {"SELECT REGR_SXY(y, x) = 0.280250189955859985::FLOAT FROM t13",
                "true"},
            {"SELECT REGR_SXY(y, x) OVER () = 0.280250189955859985::FLOAT FROM t13 LIMIT 1",
                "true"},
            {"SELECT REGR_SXY(y, x) = 0.280250189956159967::FLOAT FROM b",
                "true"},
            {"SELECT REGR_SXY(y, x) = 0.389231702918762801::FLOAT FROM (SELECT x, y FROM b UNION ALL "
                + "SELECT 0.0000000000001, 0.0000000000001)",
                "true"},
            {"SELECT REGR_SXY(y, x) = 0.389231702918762801::FLOAT FROM (SELECT column1 x, column2 y FROM "
                + "VALUES (0.1234567, 0.1234567), (0.7654321, 0.8765437), (0.3, 0.1), (0.0000000000001, "
                + "0.0000000000001))",
                "true"},
            {"SELECT REGR_SXY(y, x) = 0.38923170291911996::FLOAT FROM (SELECT column1 x, column2 y FROM "
                + "VALUES (0.1234567, 0.1234567), (0.7654321, 0.8765437), (0.3, 0.1), (0, 0))",
                "true"},
            {"SELECT REGR_SXY(y, x) = 0.280250189955859985::FLOAT FROM (SELECT COALESCE(x, "
                + "0.0000000000001) x, COALESCE(y, 0.0000000000001) y FROM b)",
                "true"},
            {"SELECT REGR_SXY(y, x) = 0.28025000907926173::FLOAT FROM (SELECT column1::NUMBER(38,20) x, "
                + "column2::NUMBER(38,20) y FROM VALUES (0.1234567, 0.1234567), (0.7654321, 0.87654321), (0.3, "
                + "0.1))",
                "true"},
            {"SELECT REGR_SXY(y, x) = 0.280250009079330675::FLOAT FROM (SELECT column1 x, column2 y FROM "
                + "VALUES (0.1234567, 0.1234567), (0.7654321, 0.87654321), (0.3, 0.1))",
                "true"},
            {"SELECT REGR_SXY(y, x) = 0.280250189955820017::FLOAT FROM (SELECT column1::NUMBER(38,14) x, "
                + "column2::NUMBER(38,14) y FROM VALUES (0.1234567, 0.1234567), (0.7654321, 0.8765437), (0.3, "
                + "0.1))",
                "true"},
            {"SELECT REGR_SXY(y, x) = 0.280250189956159967::FLOAT FROM (SELECT column1::NUMBER(38,12) x, "
                + "column2::NUMBER(38,12) y FROM VALUES (0.1234567, 0.1234567), (0.7654321, 0.8765437), (0.3, "
                + "0.1))",
                "true"},
            {"SELECT REGR_SXY(y, x) = 0.354121016688475232::FLOAT FROM (SELECT IFF(x > 0.2, x, "
                + "0.0000000000001) x, IFF(x > 0.2, y, 0.0000000000001) y FROM b)",
                "true"},
            {"SELECT REGR_SXY(y, x) = 85804999999980348300000000::FLOAT FROM (SELECT column1::NUMBER(38,5)"
                + " x, column2::NUMBER(38,0) y FROM VALUES (13100000000000, 13100000000000), (1, 2))",
                "true"},
        });
    }
}
