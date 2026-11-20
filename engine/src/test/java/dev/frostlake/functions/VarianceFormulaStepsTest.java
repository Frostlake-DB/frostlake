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
 * The variance family over exact NUMBERs follows the formula the account plans, step by step (live-verified): a
 * square is a product at the scale a product declares and is refused when its raw leaves the 128-bit carrier, a
 * sum that leaves it is a SUM aggregate's overflow, {@code n·Σx²} is refused as a product is, and the difference
 * is divided by a rounding division below a scale of twelve — whose quotient may fill the carrier — and by a plain
 * one from twelve on, whose dividend must fit 38 digits. A series of seven or more decimals is read through
 * squares rounded to twelve decimals, or to its own scale past that, which shows in the last digit.
 */
public class VarianceFormulaStepsTest extends MeasuredCellsTestSupport {

    @Test
    public void stepsRefuseAsTheirArithmeticDoes() {
        engine.execute("CREATE OR REPLACE TABLE r3a (c NUMBER(38,3), g INT) AS SELECT column1::NUMBER(38,3), column2"
            + " FROM VALUES (1.3e16, 0), (NULL, 1)");
        engine.execute("CREATE OR REPLACE TABLE r3b (c NUMBER(38,3), g INT) AS SELECT column1::NUMBER(38,3), column2"
            + " FROM VALUES (1.31e16, 0), (NULL, 1)");
        engine.execute("CREATE OR REPLACE TABLE r3c (c NUMBER(38,3), g INT) AS SELECT column1::NUMBER(38,3), column2"
            + " FROM VALUES (-1.31e16, 0), (NULL, 1)");
        engine.execute("CREATE OR REPLACE TABLE r7a (c NUMBER(38,7), g INT) AS SELECT column1::NUMBER(38,7), column2"
            + " FROM VALUES (1.3e13, 0), (NULL, 1)");
        engine.execute("CREATE OR REPLACE TABLE r7b (c NUMBER(38,7), g INT) AS SELECT column1::NUMBER(38,7), column2"
            + " FROM VALUES (1.31e13, 0), (NULL, 1)");
        engine.execute("CREATE OR REPLACE TABLE r7c (c NUMBER(38,7), g INT) AS SELECT column1::NUMBER(38,7), column2"
            + " FROM VALUES (1304381782533.2781234, 0), (NULL, 1)");
        engine.execute("CREATE OR REPLACE TABLE r13a (c NUMBER(38,13), g INT) AS SELECT column1::NUMBER(38,13), "
            + "column2 FROM VALUES (1e12, 0), (NULL, 1)");
        engine.execute("CREATE OR REPLACE TABLE r13b (c NUMBER(38,13), g INT) AS SELECT column1::NUMBER(38,13), "
            + "column2 FROM VALUES (5e12, 0), (NULL, 1)");
        engine.execute("CREATE OR REPLACE TABLE r1a (c NUMBER(38,1), g INT) AS SELECT column1::NUMBER(38,1), column2"
            + " FROM VALUES (1.3e18, 0), (NULL, 1)");
        engine.execute("CREATE OR REPLACE TABLE r1b (c NUMBER(38,1), g INT) AS SELECT column1::NUMBER(38,1), column2"
            + " FROM VALUES (1.31e18, 0), (NULL, 1)");
        engine.execute("CREATE OR REPLACE TABLE s3a (c NUMBER(38,3), g INT) AS SELECT column1::NUMBER(38,3), column2"
            + " FROM VALUES (1.2e16, 0), (1.2e16, 1), (NULL, 2)");
        engine.execute("CREATE OR REPLACE TABLE s3b (c NUMBER(38,3), g INT) AS SELECT column1::NUMBER(38,3), column2"
            + " FROM VALUES (1e16, 0), (0, 1)");
        engine.execute("CREATE OR REPLACE TABLE s3d (c NUMBER(38,3), g INT) AS SELECT column1::NUMBER(38,3), column2"
            + " FROM VALUES (9e15, 0), (9e15, 1), (0, 2), (NULL, 3)");
        engine.execute("CREATE OR REPLACE TABLE s3e (c NUMBER(38,3), g INT) AS SELECT column1::NUMBER(38,3), column2"
            + " FROM VALUES (6e15, 0), (6e15, 1), (6e15, 2), (6e15, 3), (6e15, 4), (NULL, 5)");
        engine.execute("CREATE OR REPLACE TABLE b3 (c NUMBER(38,3), g INT) AS SELECT column1::NUMBER(38,3), column2 "
            + "FROM VALUES (1e15, 0), (2e15, 1), (3e15, 2), (-4e15, 3)");
        engine.execute("CREATE OR REPLACE TABLE b7 (c NUMBER(38,7), g INT) AS SELECT column1::NUMBER(38,7), column2 "
            + "FROM VALUES (1e12, 0), (2e12, 1), (3e12, 2), (-4e12, 3)");
        engine.execute("CREATE OR REPLACE TABLE n7a (c NUMBER(38,7), g INT) AS SELECT column1::NUMBER(38,7), column2"
            + " FROM VALUES (4.975e12, 0), (-4.975e12, 1)");
        engine.execute("CREATE OR REPLACE TABLE n7b (c NUMBER(38,7), g INT) AS SELECT column1::NUMBER(38,7), column2"
            + " FROM VALUES (5.385e12, 0), (-5.385e12, 1)");
        engine.execute("CREATE OR REPLACE TABLE n7c (c NUMBER(38,7), g INT) AS SELECT column1::NUMBER(38,7), column2"
            + " FROM VALUES (5e12, 0), (-5e12, 1)");
        engine.execute("CREATE OR REPLACE TABLE n6a (c NUMBER(38,6), g INT) AS SELECT column1::NUMBER(38,6), column2"
            + " FROM VALUES (4.975e12, 0), (-4.975e12, 1)");
        engine.execute("CREATE OR REPLACE TABLE n6b (c NUMBER(38,6), g INT) AS SELECT column1::NUMBER(38,6), column2"
            + " FROM VALUES (5.385e12, 0), (-5.385e12, 1)");
        engine.execute("CREATE OR REPLACE TABLE n13a (c NUMBER(38,13), g INT) AS SELECT column1::NUMBER(38,13), "
            + "column2 FROM VALUES (1.5e12, 0), (-1.5e12, 1)");
        engine.execute("CREATE OR REPLACE TABLE n13b (c NUMBER(38,13), g INT) AS SELECT column1::NUMBER(38,13), "
            + "column2 FROM VALUES (1.7e12, 0), (-1.7e12, 1)");
        engine.execute("CREATE OR REPLACE TABLE n5a (c NUMBER(38,5), g INT) AS SELECT column1::NUMBER(38,5), column2"
            + " FROM VALUES (1e13, 0), (-1e13, 1), (NULL, 2)");
        engine.execute("CREATE OR REPLACE TABLE n5b (c NUMBER(38,5), g INT) AS SELECT column1::NUMBER(38,5), column2"
            + " FROM VALUES (3e13, 0), (-3e13, 1), (NULL, 2)");
        engine.execute("CREATE OR REPLACE TABLE w0a (c NUMBER(38,0), g INT) AS SELECT column1::NUMBER(38,0), column2"
            + " FROM VALUES (12247448713915890, 0), (-12247448713915890, 1), (NULL, 2)");
        engine.execute("CREATE OR REPLACE TABLE w0b (c NUMBER(38,0), g INT) AS SELECT column1::NUMBER(38,0), column2"
            + " FROM VALUES (13100000000000000, 0), (-13100000000000000, 1), (NULL, 2)");
        engine.execute("CREATE OR REPLACE TABLE w0c (c NUMBER(38,0), g INT) AS SELECT column1::NUMBER(38,0), column2"
            + " FROM VALUES (2e19, 0), (NULL, 1)");
        engine.execute("CREATE OR REPLACE TABLE w2a (c NUMBER(38,2), g INT) AS SELECT column1::NUMBER(38,2), column2"
            + " FROM VALUES (12247448713915890, 0), (-12247448713915890, 1), (NULL, 2)");
        assertCells(new String[][] {
            {"SELECT (VAR_POP(c))::VARCHAR FROM r3a",
                "0.000000000000"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM r3a",
                "null"},
            {"SELECT (VARIANCE(c))::VARCHAR FROM r3a",
                "null"},
            {"SELECT (VARIANCE_POP(c))::VARCHAR FROM r3a",
                "0.000000000000"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,4))::VARCHAR FROM r3a",
                "0.0000"},
            {"SELECT (STDDEV(c)::NUMBER(38,4))::VARCHAR FROM r3a",
                "null"},
            {"SELECT (SUM(c * c))::VARCHAR FROM r3a",
                "169000000000000000000000000000000.000000"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM r3a ORDER BY g LIMIT 1",
                "0.000000000000"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_POP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM r3a)",
                "0.000000000000"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM r3a GROUP BY ALL",
                "0.000000000000"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM r3b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+38"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM r3b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+38"},
            {"SELECT (VARIANCE(c))::VARCHAR FROM r3b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+38"},
            {"SELECT (VARIANCE_POP(c))::VARCHAR FROM r3b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+38"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,4))::VARCHAR FROM r3b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+38"},
            {"SELECT (STDDEV(c)::NUMBER(38,4))::VARCHAR FROM r3b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+38"},
            {"SELECT (SUM(c * c))::VARCHAR FROM r3b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+38"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM r3b ORDER BY g LIMIT 1",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+38"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_POP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM r3b)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+38"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM r3b GROUP BY ALL",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+38"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM r3c",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+38"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM r3c",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+38"},
            {"SELECT (VARIANCE(c))::VARCHAR FROM r3c",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+38"},
            {"SELECT (VARIANCE_POP(c))::VARCHAR FROM r3c",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+38"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,4))::VARCHAR FROM r3c",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+38"},
            {"SELECT (STDDEV(c)::NUMBER(38,4))::VARCHAR FROM r3c",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+38"},
            {"SELECT (SUM(c * c))::VARCHAR FROM r3c",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+38"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM r3c ORDER BY g LIMIT 1",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+38"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_POP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM r3c)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+38"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM r3c GROUP BY ALL",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+38"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM r7a",
                "0.000000000000"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM r7a",
                "null"},
            {"SELECT (VARIANCE(c))::VARCHAR FROM r7a",
                "null"},
            {"SELECT (VARIANCE_POP(c))::VARCHAR FROM r7a",
                "0.000000000000"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,4))::VARCHAR FROM r7a",
                "0.0000"},
            {"SELECT (STDDEV(c)::NUMBER(38,4))::VARCHAR FROM r7a",
                "null"},
            {"SELECT (SUM(c * c))::VARCHAR FROM r7a",
                "169000000000000000000000000.000000000000"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM r7a ORDER BY g LIMIT 1",
                "0.000000000000"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_POP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM r7a)",
                "0.000000000000"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM r7a GROUP BY ALL",
                "0.000000000000"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM r7b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM r7b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT (VARIANCE(c))::VARCHAR FROM r7b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT (VARIANCE_POP(c))::VARCHAR FROM r7b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,4))::VARCHAR FROM r7b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT (STDDEV(c)::NUMBER(38,4))::VARCHAR FROM r7b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT (SUM(c * c))::VARCHAR FROM r7b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM r7b ORDER BY g LIMIT 1",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_POP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM r7b)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM r7b GROUP BY ALL",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+40"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM r7c",
                "0.000000000000"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM r7c",
                "null"},
            {"SELECT (VARIANCE(c))::VARCHAR FROM r7c",
                "null"},
            {"SELECT (VARIANCE_POP(c))::VARCHAR FROM r7c",
                "0.000000000000"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,4))::VARCHAR FROM r7c",
                "0.0000"},
            {"SELECT (STDDEV(c)::NUMBER(38,4))::VARCHAR FROM r7c",
                "null"},
            {"SELECT (SUM(c * c))::VARCHAR FROM r7c",
                "1701411834604692062088601.354497025628"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM r7c ORDER BY g LIMIT 1",
                "0.000000000000"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_POP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM r7c)",
                "0.000000000000"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM r7c GROUP BY ALL",
                "0.000000000000"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM r13a",
                "0.0000000000000"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM r13a",
                "null"},
            {"SELECT (VARIANCE(c))::VARCHAR FROM r13a",
                "null"},
            {"SELECT (VARIANCE_POP(c))::VARCHAR FROM r13a",
                "0.0000000000000"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,4))::VARCHAR FROM r13a",
                "0.0000"},
            {"SELECT (STDDEV(c)::NUMBER(38,4))::VARCHAR FROM r13a",
                "null"},
            {"SELECT (SUM(c * c))::VARCHAR FROM r13a",
                "1000000000000000000000000.0000000000000"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM r13a ORDER BY g LIMIT 1",
                "0.0000000000000"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_POP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM r13a)",
                "0.0000000000000"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM r13a GROUP BY ALL",
                "0.0000000000000"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM r13b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 2.5e+51"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM r13b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 2.5e+51"},
            {"SELECT (VARIANCE(c))::VARCHAR FROM r13b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 2.5e+51"},
            {"SELECT (VARIANCE_POP(c))::VARCHAR FROM r13b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 2.5e+51"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,4))::VARCHAR FROM r13b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 2.5e+51"},
            {"SELECT (STDDEV(c)::NUMBER(38,4))::VARCHAR FROM r13b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 2.5e+51"},
            {"SELECT (SUM(c * c))::VARCHAR FROM r13b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 2.5e+51"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM r13b ORDER BY g LIMIT 1",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 2.5e+51"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_POP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM r13b)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 2.5e+51"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM r13b GROUP BY ALL",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 2.5e+51"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM r1a",
                "0.00000000"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM r1a",
                "null"},
            {"SELECT (VARIANCE(c))::VARCHAR FROM r1a",
                "null"},
            {"SELECT (VARIANCE_POP(c))::VARCHAR FROM r1a",
                "0.00000000"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,4))::VARCHAR FROM r1a",
                "0.0000"},
            {"SELECT (STDDEV(c)::NUMBER(38,4))::VARCHAR FROM r1a",
                "null"},
            {"SELECT (SUM(c * c))::VARCHAR FROM r1a",
                "1690000000000000000000000000000000000.00"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM r1a ORDER BY g LIMIT 1",
                "0.00000000"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_POP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM r1a)",
                "0.00000000"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM r1a GROUP BY ALL",
                "0.00000000"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM r1b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+38"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM r1b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+38"},
            {"SELECT (VARIANCE(c))::VARCHAR FROM r1b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+38"},
            {"SELECT (VARIANCE_POP(c))::VARCHAR FROM r1b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+38"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,4))::VARCHAR FROM r1b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+38"},
            {"SELECT (STDDEV(c)::NUMBER(38,4))::VARCHAR FROM r1b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+38"},
            {"SELECT (SUM(c * c))::VARCHAR FROM r1b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+38"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM r1b ORDER BY g LIMIT 1",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+38"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_POP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM r1b)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+38"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM r1b GROUP BY ALL",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+38"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM s3a",
                "REFUSED Value overflow in a SUM aggregate"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM s3a",
                "REFUSED Value overflow in a SUM aggregate"},
            {"SELECT (VARIANCE(c))::VARCHAR FROM s3a",
                "REFUSED Value overflow in a SUM aggregate"},
            {"SELECT (VARIANCE_POP(c))::VARCHAR FROM s3a",
                "REFUSED Value overflow in a SUM aggregate"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,4))::VARCHAR FROM s3a",
                "REFUSED Value overflow in a SUM aggregate"},
            {"SELECT (STDDEV(c)::NUMBER(38,4))::VARCHAR FROM s3a",
                "REFUSED Value overflow in a SUM aggregate"},
            {"SELECT (SUM(c * c))::VARCHAR FROM s3a",
                "REFUSED Value overflow in a SUM aggregate"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM s3a ORDER BY g LIMIT 1",
                "REFUSED Value overflow in a SUM aggregate"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_POP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM s3a)",
                "REFUSED Numeric value is out of range, error: Window function intermediate result too large"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM s3a GROUP BY ALL",
                "REFUSED Value overflow in a SUM aggregate"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM s3b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 2e+38"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM s3b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 2e+38"},
            {"SELECT (VARIANCE(c))::VARCHAR FROM s3b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 2e+38"},
            {"SELECT (VARIANCE_POP(c))::VARCHAR FROM s3b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 2e+38"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,4))::VARCHAR FROM s3b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 2e+38"},
            {"SELECT (STDDEV(c)::NUMBER(38,4))::VARCHAR FROM s3b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 2e+38"},
            {"SELECT (SUM(c * c))::VARCHAR FROM s3b",
                "100000000000000000000000000000000.000000"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM s3b ORDER BY g LIMIT 1",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 2e+38"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_POP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM s3b)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 2e+38"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM s3b GROUP BY ALL",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 2e+38"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM s3d",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 4.86e+38"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM s3d",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 4.86e+38"},
            {"SELECT (VARIANCE(c))::VARCHAR FROM s3d",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 4.86e+38"},
            {"SELECT (VARIANCE_POP(c))::VARCHAR FROM s3d",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 4.86e+38"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,4))::VARCHAR FROM s3d",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 4.86e+38"},
            {"SELECT (STDDEV(c)::NUMBER(38,4))::VARCHAR FROM s3d",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 4.86e+38"},
            {"SELECT (SUM(c * c))::VARCHAR FROM s3d",
                "162000000000000000000000000000000.000000"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM s3d ORDER BY g LIMIT 1",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 4.86e+38"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_POP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM s3d)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 3.24e+38"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM s3d GROUP BY ALL",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 4.86e+38"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM s3e",
                "REFUSED Value overflow in a SUM aggregate"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM s3e",
                "REFUSED Value overflow in a SUM aggregate"},
            {"SELECT (VARIANCE(c))::VARCHAR FROM s3e",
                "REFUSED Value overflow in a SUM aggregate"},
            {"SELECT (VARIANCE_POP(c))::VARCHAR FROM s3e",
                "REFUSED Value overflow in a SUM aggregate"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,4))::VARCHAR FROM s3e",
                "REFUSED Value overflow in a SUM aggregate"},
            {"SELECT (STDDEV(c)::NUMBER(38,4))::VARCHAR FROM s3e",
                "REFUSED Value overflow in a SUM aggregate"},
            {"SELECT (SUM(c * c))::VARCHAR FROM s3e",
                "REFUSED Value overflow in a SUM aggregate"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM s3e ORDER BY g LIMIT 1",
                "REFUSED Value overflow in a SUM aggregate"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_POP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM s3e)",
                "0.000000000000"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM s3e GROUP BY ALL",
                "REFUSED Value overflow in a SUM aggregate"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM b3",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value 7.25e+30"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM b3",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value "
                + "9.66667e+30"},
            {"SELECT (VARIANCE(c))::VARCHAR FROM b3",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value "
                + "9.66667e+30"},
            {"SELECT (VARIANCE_POP(c))::VARCHAR FROM b3",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value 7.25e+30"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,4))::VARCHAR FROM b3",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value 7.25e+30"},
            {"SELECT (STDDEV(c)::NUMBER(38,4))::VARCHAR FROM b3",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value "
                + "9.66667e+30"},
            {"SELECT (SUM(c * c))::VARCHAR FROM b3",
                "30000000000000000000000000000000.000000"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM b3 ORDER BY g LIMIT 1",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value 7.25e+30"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_POP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM b3)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value 2.5e+29"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM b3 GROUP BY ALL",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value 7.25e+30"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM b7",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value "
                + "116000000000000000000000000.000000000000"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM b7",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value "
                + "116000000000000000000000000.000000000000"},
            {"SELECT (VARIANCE(c))::VARCHAR FROM b7",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value "
                + "116000000000000000000000000.000000000000"},
            {"SELECT (VARIANCE_POP(c))::VARCHAR FROM b7",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value "
                + "116000000000000000000000000.000000000000"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,4))::VARCHAR FROM b7",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value "
                + "116000000000000000000000000.000000000000"},
            {"SELECT (STDDEV(c)::NUMBER(38,4))::VARCHAR FROM b7",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value "
                + "116000000000000000000000000.000000000000"},
            {"SELECT (SUM(c * c))::VARCHAR FROM b7",
                "30000000000000000000000000.000000000000"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM b7 ORDER BY g LIMIT 1",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value "
                + "116000000000000000000000000.000000000000"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_POP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM b7)",
                "12250000000000000000000000.000000000000"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM b7 GROUP BY ALL",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value "
                + "116000000000000000000000000.000000000000"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM n7a",
                "24750625000000000000000000.000000000000"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM n7a",
                "49501250000000000000000000.000000000000"},
            {"SELECT (VARIANCE(c))::VARCHAR FROM n7a",
                "49501250000000000000000000.000000000000"},
            {"SELECT (VARIANCE_POP(c))::VARCHAR FROM n7a",
                "24750625000000000000000000.000000000000"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,4))::VARCHAR FROM n7a",
                "4975000000000.0000"},
            {"SELECT (STDDEV(c)::NUMBER(38,4))::VARCHAR FROM n7a",
                "7035712472806.1475"},
            {"SELECT (SUM(c * c))::VARCHAR FROM n7a",
                "49501250000000000000000000.000000000000"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM n7a ORDER BY g LIMIT 1",
                "24750625000000000000000000.000000000000"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_POP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM n7a)",
                "24750625000000000000000000.000000000000"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM n7a GROUP BY ALL",
                "24750625000000000000000000.000000000000"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM n7b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value "
                + "115992900000000000000000000.000000000000"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM n7b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value "
                + "115992900000000000000000000.000000000000"},
            {"SELECT (VARIANCE(c))::VARCHAR FROM n7b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value "
                + "115992900000000000000000000.000000000000"},
            {"SELECT (VARIANCE_POP(c))::VARCHAR FROM n7b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value "
                + "115992900000000000000000000.000000000000"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,4))::VARCHAR FROM n7b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value "
                + "115992900000000000000000000.000000000000"},
            {"SELECT (STDDEV(c)::NUMBER(38,4))::VARCHAR FROM n7b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value "
                + "115992900000000000000000000.000000000000"},
            {"SELECT (SUM(c * c))::VARCHAR FROM n7b",
                "57996450000000000000000000.000000000000"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM n7b ORDER BY g LIMIT 1",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value "
                + "115992900000000000000000000.000000000000"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_POP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM n7b)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value "
                + "115992900000000000000000000.000000000000"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM n7b GROUP BY ALL",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value "
                + "115992900000000000000000000.000000000000"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM n7c",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value "
                + "100000000000000000000000000.000000000000"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM n7c",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value "
                + "100000000000000000000000000.000000000000"},
            {"SELECT (VARIANCE(c))::VARCHAR FROM n7c",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value "
                + "100000000000000000000000000.000000000000"},
            {"SELECT (VARIANCE_POP(c))::VARCHAR FROM n7c",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value "
                + "100000000000000000000000000.000000000000"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,4))::VARCHAR FROM n7c",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value "
                + "100000000000000000000000000.000000000000"},
            {"SELECT (STDDEV(c)::NUMBER(38,4))::VARCHAR FROM n7c",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value "
                + "100000000000000000000000000.000000000000"},
            {"SELECT (SUM(c * c))::VARCHAR FROM n7c",
                "50000000000000000000000000.000000000000"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM n7c ORDER BY g LIMIT 1",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value "
                + "100000000000000000000000000.000000000000"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_POP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM n7c)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value "
                + "100000000000000000000000000.000000000000"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM n7c GROUP BY ALL",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value "
                + "100000000000000000000000000.000000000000"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM n6a",
                "24750625000000000000000000.000000000000"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM n6a",
                "49501250000000000000000000.000000000000"},
            {"SELECT (VARIANCE(c))::VARCHAR FROM n6a",
                "49501250000000000000000000.000000000000"},
            {"SELECT (VARIANCE_POP(c))::VARCHAR FROM n6a",
                "24750625000000000000000000.000000000000"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,4))::VARCHAR FROM n6a",
                "4975000000000.0000"},
            {"SELECT (STDDEV(c)::NUMBER(38,4))::VARCHAR FROM n6a",
                "7035712472806.1475"},
            {"SELECT (SUM(c * c))::VARCHAR FROM n6a",
                "49501250000000000000000000.000000000000"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM n6a ORDER BY g LIMIT 1",
                "24750625000000000000000000.000000000000"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_POP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM n6a)",
                "24750625000000000000000000.000000000000"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM n6a GROUP BY ALL",
                "24750625000000000000000000.000000000000"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM n6b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value "
                + "115992900000000000000000000.000000000000"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM n6b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value "
                + "115992900000000000000000000.000000000000"},
            {"SELECT (VARIANCE(c))::VARCHAR FROM n6b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value "
                + "115992900000000000000000000.000000000000"},
            {"SELECT (VARIANCE_POP(c))::VARCHAR FROM n6b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value "
                + "115992900000000000000000000.000000000000"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,4))::VARCHAR FROM n6b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value "
                + "115992900000000000000000000.000000000000"},
            {"SELECT (STDDEV(c)::NUMBER(38,4))::VARCHAR FROM n6b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value "
                + "115992900000000000000000000.000000000000"},
            {"SELECT (SUM(c * c))::VARCHAR FROM n6b",
                "57996450000000000000000000.000000000000"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM n6b ORDER BY g LIMIT 1",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value "
                + "115992900000000000000000000.000000000000"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_POP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM n6b)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value "
                + "115992900000000000000000000.000000000000"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM n6b GROUP BY ALL",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value "
                + "115992900000000000000000000.000000000000"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM n13a",
                "2250000000000000000000000.0000000000000"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM n13a",
                "4500000000000000000000000.0000000000000"},
            {"SELECT (VARIANCE(c))::VARCHAR FROM n13a",
                "4500000000000000000000000.0000000000000"},
            {"SELECT (VARIANCE_POP(c))::VARCHAR FROM n13a",
                "2250000000000000000000000.0000000000000"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,4))::VARCHAR FROM n13a",
                "1500000000000.0000"},
            {"SELECT (STDDEV(c)::NUMBER(38,4))::VARCHAR FROM n13a",
                "2121320343559.6426"},
            {"SELECT (SUM(c * c))::VARCHAR FROM n13a",
                "4500000000000000000000000.0000000000000"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM n13a ORDER BY g LIMIT 1",
                "2250000000000000000000000.0000000000000"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_POP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM n13a)",
                "2250000000000000000000000.0000000000000"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM n13a GROUP BY ALL",
                "2250000000000000000000000.0000000000000"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM n13b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,13){nullable}, value "
                + "11560000000000000000000000.0000000000000"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM n13b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,13){nullable}, value "
                + "11560000000000000000000000.0000000000000"},
            {"SELECT (VARIANCE(c))::VARCHAR FROM n13b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,13){nullable}, value "
                + "11560000000000000000000000.0000000000000"},
            {"SELECT (VARIANCE_POP(c))::VARCHAR FROM n13b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,13){nullable}, value "
                + "11560000000000000000000000.0000000000000"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,4))::VARCHAR FROM n13b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,13){nullable}, value "
                + "11560000000000000000000000.0000000000000"},
            {"SELECT (STDDEV(c)::NUMBER(38,4))::VARCHAR FROM n13b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,13){nullable}, value "
                + "11560000000000000000000000.0000000000000"},
            {"SELECT (SUM(c * c))::VARCHAR FROM n13b",
                "5780000000000000000000000.0000000000000"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM n13b ORDER BY g LIMIT 1",
                "REFUSED Number out of representable range: type FIXED[SB16](38,13){nullable}, value "
                + "11560000000000000000000000.0000000000000"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_POP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM n13b)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,13){nullable}, value "
                + "11560000000000000000000000.0000000000000"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM n13b GROUP BY ALL",
                "REFUSED Number out of representable range: type FIXED[SB16](38,13){nullable}, value "
                + "11560000000000000000000000.0000000000000"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM n5a",
                "100000000000000000000000000.000000000000"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM n5a",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value 2e+26"},
            {"SELECT (VARIANCE(c))::VARCHAR FROM n5a",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value 2e+26"},
            {"SELECT (VARIANCE_POP(c))::VARCHAR FROM n5a",
                "100000000000000000000000000.000000000000"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,4))::VARCHAR FROM n5a",
                "10000000000000.0000"},
            {"SELECT (STDDEV(c)::NUMBER(38,4))::VARCHAR FROM n5a",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value 2e+26"},
            {"SELECT (SUM(c * c))::VARCHAR FROM n5a",
                "200000000000000000000000000.0000000000"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM n5a ORDER BY g LIMIT 1",
                "100000000000000000000000000.000000000000"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_POP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM n5a)",
                "100000000000000000000000000.000000000000"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM n5a GROUP BY ALL",
                "100000000000000000000000000.000000000000"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM n5b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value 9e+26"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM n5b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value 1.8e+27"},
            {"SELECT (VARIANCE(c))::VARCHAR FROM n5b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value 1.8e+27"},
            {"SELECT (VARIANCE_POP(c))::VARCHAR FROM n5b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value 9e+26"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,4))::VARCHAR FROM n5b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value 9e+26"},
            {"SELECT (STDDEV(c)::NUMBER(38,4))::VARCHAR FROM n5b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value 1.8e+27"},
            {"SELECT (SUM(c * c))::VARCHAR FROM n5b",
                "1800000000000000000000000000.0000000000"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM n5b ORDER BY g LIMIT 1",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value 9e+26"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_POP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM n5b)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value 9e+26"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM n5b GROUP BY ALL",
                "REFUSED Number out of representable range: type FIXED[SB16](38,12){nullable}, value 9e+26"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM w0a",
                "149999999999999987973337994492100.000000"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM w0a",
                "REFUSED Number out of representable range: type FIXED[SB16](38,6){nullable}, value 3e+32"},
            {"SELECT (VARIANCE(c))::VARCHAR FROM w0a",
                "REFUSED Number out of representable range: type FIXED[SB16](38,6){nullable}, value 3e+32"},
            {"SELECT (VARIANCE_POP(c))::VARCHAR FROM w0a",
                "149999999999999987973337994492100.000000"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,4))::VARCHAR FROM w0a",
                "12247448713915890.0000"},
            {"SELECT (STDDEV(c)::NUMBER(38,4))::VARCHAR FROM w0a",
                "REFUSED Number out of representable range: type FIXED[SB16](38,6){nullable}, value 3e+32"},
            {"SELECT (SUM(c * c))::VARCHAR FROM w0a",
                "299999999999999975946675988984200"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM w0a ORDER BY g LIMIT 1",
                "149999999999999987973337994492100.000000"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_POP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM w0a)",
                "149999999999999987973337994492100.000000"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM w0a GROUP BY ALL",
                "149999999999999987973337994492100.000000"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM w0b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,6){nullable}, value "
                + "1.7161e+32"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM w0b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,6){nullable}, value "
                + "3.4322e+32"},
            {"SELECT (VARIANCE(c))::VARCHAR FROM w0b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,6){nullable}, value "
                + "3.4322e+32"},
            {"SELECT (VARIANCE_POP(c))::VARCHAR FROM w0b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,6){nullable}, value "
                + "1.7161e+32"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,4))::VARCHAR FROM w0b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,6){nullable}, value "
                + "1.7161e+32"},
            {"SELECT (STDDEV(c)::NUMBER(38,4))::VARCHAR FROM w0b",
                "REFUSED Number out of representable range: type FIXED[SB16](38,6){nullable}, value "
                + "3.4322e+32"},
            {"SELECT (SUM(c * c))::VARCHAR FROM w0b",
                "343220000000000000000000000000000"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM w0b ORDER BY g LIMIT 1",
                "REFUSED Number out of representable range: type FIXED[SB16](38,6){nullable}, value "
                + "1.7161e+32"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_POP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM w0b)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,6){nullable}, value "
                + "1.7161e+32"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM w0b GROUP BY ALL",
                "REFUSED Number out of representable range: type FIXED[SB16](38,6){nullable}, value "
                + "1.7161e+32"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM w0c",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 4e+38"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM w0c",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 4e+38"},
            {"SELECT (VARIANCE(c))::VARCHAR FROM w0c",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 4e+38"},
            {"SELECT (VARIANCE_POP(c))::VARCHAR FROM w0c",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 4e+38"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,4))::VARCHAR FROM w0c",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 4e+38"},
            {"SELECT (STDDEV(c)::NUMBER(38,4))::VARCHAR FROM w0c",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 4e+38"},
            {"SELECT (SUM(c * c))::VARCHAR FROM w0c",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 4e+38"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM w0c ORDER BY g LIMIT 1",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 4e+38"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_POP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM w0c)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 4e+38"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM w0c GROUP BY ALL",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 4e+38"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM w2a",
                "REFUSED Number out of representable range: type FIXED[SB16](38,10){nullable}, value 1.5e+32"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM w2a",
                "REFUSED Number out of representable range: type FIXED[SB16](38,10){nullable}, value 3e+32"},
            {"SELECT (VARIANCE(c))::VARCHAR FROM w2a",
                "REFUSED Number out of representable range: type FIXED[SB16](38,10){nullable}, value 3e+32"},
            {"SELECT (VARIANCE_POP(c))::VARCHAR FROM w2a",
                "REFUSED Number out of representable range: type FIXED[SB16](38,10){nullable}, value 1.5e+32"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,4))::VARCHAR FROM w2a",
                "REFUSED Number out of representable range: type FIXED[SB16](38,10){nullable}, value 1.5e+32"},
            {"SELECT (STDDEV(c)::NUMBER(38,4))::VARCHAR FROM w2a",
                "REFUSED Number out of representable range: type FIXED[SB16](38,10){nullable}, value 3e+32"},
            {"SELECT (SUM(c * c))::VARCHAR FROM w2a",
                "299999999999999975946675988984200.0000"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM w2a ORDER BY g LIMIT 1",
                "REFUSED Number out of representable range: type FIXED[SB16](38,10){nullable}, value 1.5e+32"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_POP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM w2a)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,10){nullable}, value 1.5e+32"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM w2a GROUP BY ALL",
                "REFUSED Number out of representable range: type FIXED[SB16](38,10){nullable}, value 1.5e+32"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM (SELECT column1::NUMBER(38,3) c FROM VALUES (1e17),(5))",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 1e+40"},
            {"SELECT (VAR_POP(c / 10))::VARCHAR FROM b7 WHERE c < 3e12",
                "68888888888888888888888.888888888889"},
            {"SELECT (VAR_POP(c / 2))::VARCHAR FROM b7",
                "1812500000000000000000000.000000000000"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM b7 WHERE c < 3e12",
                "10333333333333333333333333.333333333333"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM b7 WHERE c > 0",
                "666666666666666666666666.666666666667"},
        });
    }

    @Test
    public void squaresAreRoundedAtTheProductScale() {
        engine.execute("CREATE OR REPLACE TABLE m0 (c NUMBER(38,7), g INT) AS SELECT column1::NUMBER(38,7), column2 "
            + "FROM VALUES (89.9975904, 0), (87.0302523, 1), (99.0580830, 2), (30.7811516, 3), (29.6555068,"
            + " 4), (99.9315425, 5)");
        engine.execute("CREATE OR REPLACE TABLE m1 (c NUMBER(38,7), g INT) AS SELECT column1::NUMBER(38,7), column2 "
            + "FROM VALUES (-6.7786174, 0), (3.5645637, 1), (2.7885033, 2)");
        engine.execute("CREATE OR REPLACE TABLE m2 (c NUMBER(38,7), g INT) AS SELECT column1::NUMBER(38,7), column2 "
            + "FROM VALUES (80.1495175, 0), (5.6848650, 1), (32.6254318, 2), (47.9338668, 3), (62.3471862, "
            + "4), (8.7140745, 5)");
        engine.execute("CREATE OR REPLACE TABLE m3 (c NUMBER(38,7), g INT) AS SELECT column1::NUMBER(38,7), column2 "
            + "FROM VALUES (-0.4512285, 0), (17.8194588, 1), (63.3610632, 2), (65.5557970, 3), (8.2378958, "
            + "4)");
        engine.execute("CREATE OR REPLACE TABLE m4 (c NUMBER(38,7), g INT) AS SELECT column1::NUMBER(38,7), column2 "
            + "FROM VALUES (10.0192784, 0), (25.8138487, 1), (4.1519661, 2), (88.5555093, 3), (78.8573084, "
            + "4), (76.7925658, 5)");
        engine.execute("CREATE OR REPLACE TABLE m5 (c NUMBER(38,8), g INT) AS SELECT column1::NUMBER(38,8), column2 "
            + "FROM VALUES (81.45967276, 0), (-9.02135118, 1), (-1.66318487, 2), (-0.23928624, 3), "
            + "(62.45756004, 4)");
        engine.execute("CREATE OR REPLACE TABLE m6 (c NUMBER(38,8), g INT) AS SELECT column1::NUMBER(38,8), column2 "
            + "FROM VALUES (98.83467554, 0), (85.27713841, 1), (99.21849645, 2)");
        engine.execute("CREATE OR REPLACE TABLE m7 (c NUMBER(38,8), g INT) AS SELECT column1::NUMBER(38,8), column2 "
            + "FROM VALUES (94.21495103, 0), (48.17834696, 1), (84.92243362, 2), (6.02683974, 3), "
            + "(-1.39842729, 4), (50.47124328, 5)");
        engine.execute("CREATE OR REPLACE TABLE m8 (c NUMBER(38,8), g INT) AS SELECT column1::NUMBER(38,8), column2 "
            + "FROM VALUES (33.35665480, 0), (44.09592461, 1)");
        engine.execute("CREATE OR REPLACE TABLE m9 (c NUMBER(38,8), g INT) AS SELECT column1::NUMBER(38,8), column2 "
            + "FROM VALUES (67.56312609, 0), (25.81262837, 1)");
        engine.execute("CREATE OR REPLACE TABLE m10 (c NUMBER(38,9), g INT) AS SELECT column1::NUMBER(38,9), column2"
            + " FROM VALUES (67.128106843, 0), (-7.250046871, 1), (39.494585299, 2), (-6.950202093, 3), "
            + "(6.330554109, 4), (20.692649925, 5)");
        engine.execute("CREATE OR REPLACE TABLE m11 (c NUMBER(38,9), g INT) AS SELECT column1::NUMBER(38,9), column2"
            + " FROM VALUES (80.155703099, 0), (81.807169195, 1), (33.669747645, 2)");
        engine.execute("CREATE OR REPLACE TABLE m12 (c NUMBER(38,9), g INT) AS SELECT column1::NUMBER(38,9), column2"
            + " FROM VALUES (4.227296278, 0), (12.323626456, 1)");
        engine.execute("CREATE OR REPLACE TABLE m13 (c NUMBER(38,9), g INT) AS SELECT column1::NUMBER(38,9), column2"
            + " FROM VALUES (32.851465846, 0), (80.969828357, 1), (25.330193553, 2)");
        engine.execute("CREATE OR REPLACE TABLE m14 (c NUMBER(38,9), g INT) AS SELECT column1::NUMBER(38,9), column2"
            + " FROM VALUES (-4.582599593, 0), (72.474141191, 1), (88.658118192, 2), (67.534966606, 3), "
            + "(15.257191615, 4), (8.575968776, 5)");
        engine.execute("CREATE OR REPLACE TABLE m15 (c NUMBER(38,10), g INT) AS SELECT column1::NUMBER(38,10), "
            + "column2 FROM VALUES (99.3535409865, 0), (85.6945230473, 1)");
        engine.execute("CREATE OR REPLACE TABLE m16 (c NUMBER(38,10), g INT) AS SELECT column1::NUMBER(38,10), "
            + "column2 FROM VALUES (67.6054252041, 0), (16.9150971039, 1)");
        engine.execute("CREATE OR REPLACE TABLE m17 (c NUMBER(38,10), g INT) AS SELECT column1::NUMBER(38,10), "
            + "column2 FROM VALUES (4.7215043211, 0), (92.1470375362, 1)");
        engine.execute("CREATE OR REPLACE TABLE m18 (c NUMBER(38,10), g INT) AS SELECT column1::NUMBER(38,10), "
            + "column2 FROM VALUES (99.7510112736, 0), (69.5639695711, 1), (58.1631828192, 2), "
            + "(-1.2036069442, 3), (80.9958789217, 4)");
        engine.execute("CREATE OR REPLACE TABLE m19 (c NUMBER(38,10), g INT) AS SELECT column1::NUMBER(38,10), "
            + "column2 FROM VALUES (60.4781533261, 0), (34.9947179498, 1), (60.5124377398, 2), "
            + "(13.8753257343, 3), (84.6273769095, 4), (38.5067993834, 5)");
        engine.execute("CREATE OR REPLACE TABLE m20 (c NUMBER(38,13), g INT) AS SELECT column1::NUMBER(38,13), "
            + "column2 FROM VALUES (77.8670457660445, 0), (89.9545001438853, 1), (81.4884632320279, 2), "
            + "(-2.1246305555818, 3), (33.9219149208187, 4), (93.6834945341975, 5)");
        engine.execute("CREATE OR REPLACE TABLE m21 (c NUMBER(38,13), g INT) AS SELECT column1::NUMBER(38,13), "
            + "column2 FROM VALUES (69.2208924649017, 0), (16.4653384519901, 1), (55.2197194398816, 2), "
            + "(22.0740072644940, 3), (88.2504362790828, 4)");
        engine.execute("CREATE OR REPLACE TABLE m22 (c NUMBER(38,13), g INT) AS SELECT column1::NUMBER(38,13), "
            + "column2 FROM VALUES (53.9366302488953, 0), (4.6047089553164, 1)");
        engine.execute("CREATE OR REPLACE TABLE m23 (c NUMBER(38,13), g INT) AS SELECT column1::NUMBER(38,13), "
            + "column2 FROM VALUES (49.7871944380262, 0), (33.5412857139064, 1), (3.2342918087324, 2), "
            + "(10.9668412007611, 3), (59.7995549791768, 4), (66.4972769513585, 5)");
        engine.execute("CREATE OR REPLACE TABLE m24 (c NUMBER(38,13), g INT) AS SELECT column1::NUMBER(38,13), "
            + "column2 FROM VALUES (4.8118650757234, 0), (44.5912946096582, 1), (20.6935868206272, 2), "
            + "(22.0932965817054, 3), (21.2535293804785, 4)");
        engine.execute("CREATE OR REPLACE TABLE m25 (c NUMBER(38,18), g INT) AS SELECT column1::NUMBER(38,18), "
            + "column2 FROM VALUES (15.634764265595494592, 0), (79.644032934300682953, 1), "
            + "(66.201703364401037848, 2)");
        engine.execute("CREATE OR REPLACE TABLE m26 (c NUMBER(38,18), g INT) AS SELECT column1::NUMBER(38,18), "
            + "column2 FROM VALUES (46.342472874010014221, 0), (41.280970015641713490, 1), "
            + "(78.309784412306660624, 2), (54.213392715864469905, 3), (33.727586093957846281, 4)");
        engine.execute("CREATE OR REPLACE TABLE m27 (c NUMBER(38,18), g INT) AS SELECT column1::NUMBER(38,18), "
            + "column2 FROM VALUES (23.752859897737547067, 0), (74.941774352904340627, 1)");
        engine.execute("CREATE OR REPLACE TABLE m28 (c NUMBER(38,18), g INT) AS SELECT column1::NUMBER(38,18), "
            + "column2 FROM VALUES (-3.152018686290387599, 0), (23.096539742482949065, 1)");
        engine.execute("CREATE OR REPLACE TABLE m29 (c NUMBER(38,18), g INT) AS SELECT column1::NUMBER(38,18), "
            + "column2 FROM VALUES (96.839059975503688767, 0), (5.965148329746066484, 1), "
            + "(20.754602682890127504, 2), (61.413848539655955482, 3), (13.136398850998498557, 4)");
        engine.execute("CREATE OR REPLACE TABLE m30 (c NUMBER(38,1), g INT) AS SELECT column1::NUMBER(38,1), column2"
            + " FROM VALUES (43.5, 0), (15.6, 1), (1.9, 2), (79.3, 3)");
        engine.execute("CREATE OR REPLACE TABLE m31 (c NUMBER(38,3), g INT) AS SELECT column1::NUMBER(38,3), column2"
            + " FROM VALUES (43.420, 0), (90.747, 1), (41.705, 2), (92.938, 3)");
        engine.execute("CREATE OR REPLACE TABLE m32 (c NUMBER(38,5), g INT) AS SELECT column1::NUMBER(38,5), column2"
            + " FROM VALUES (-9.57948, 0), (85.48704, 1), (34.36659, 2), (72.83591, 3)");
        engine.execute("CREATE OR REPLACE TABLE m33 (c NUMBER(38,6), g INT) AS SELECT column1::NUMBER(38,6), column2"
            + " FROM VALUES (75.781513, 0), (56.025679, 1), (20.195425, 2), (41.603218, 3)");
        assertCells(new String[][] {
            {"SELECT (VAR_POP(c))::VARCHAR FROM m0",
                "925.145948938486"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM m0",
                "1110.175138726183"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM m0 LIMIT 1",
                "925.145948938486"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_SAMP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM m0)",
                "2469.360596853837"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,15))::VARCHAR FROM m0",
                "30.416211942621750"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM m1",
                "22.123718166691"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM m1",
                "33.185577250036"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM m1 LIMIT 1",
                "22.123718166691"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_SAMP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM m1)",
                "53.490697633699"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,15))::VARCHAR FROM m1",
                "4.703585671239655"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM m2",
                "730.661509332735"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM m2",
                "876.793811199283"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM m2 LIMIT 1",
                "730.661509332735"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_SAMP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM m2)",
                "2772.492235972878"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,15))::VARCHAR FROM m2",
                "27.030751179586833"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM m3",
                "784.463167484274"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM m3",
                "980.578959355343"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM m3 LIMIT 1",
                "784.463167484274"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_SAMP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM m3)",
                "1642.670898986481"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,15))::VARCHAR FROM m3",
                "28.008269626741921"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM m4",
                "1213.487752775384"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM m4",
                "1456.185303330460"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM m4 LIMIT 1",
                "1213.487752775384"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_SAMP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM m4)",
                "3561.979052357133"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,15))::VARCHAR FROM m4",
                "34.835151108835227"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM m5",
                "1416.676772685910"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM m5",
                "1770.845965857388"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM m5 LIMIT 1",
                "1416.676772685910"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_SAMP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM m5)",
                "4093.407846615426"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,15))::VARCHAR FROM m5",
                "37.638766885830755"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM m6",
                "42.035066169084"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM m6",
                "63.052599253626"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM m6 LIMIT 1",
                "42.035066169084"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_SAMP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM m6)",
                "97.180731999737"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,15))::VARCHAR FROM m6",
                "6.483445547630056"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM m7",
                "1283.663245326507"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM m7",
                "1540.395894391809"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM m7 LIMIT 1",
                "1283.663245326507"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_SAMP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM m7)",
                "3112.257366838946"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,15))::VARCHAR FROM m7",
                "35.828246472950738"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM m8",
                "28.832979012995"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM m8",
                "57.665958025989"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM m8 LIMIT 1",
                "28.832979012995"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_SAMP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM m8)",
                "57.665958025989"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,15))::VARCHAR FROM m8",
                "5.369634905000059"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM m9",
                "435.776014966932"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM m9",
                "871.552029933863"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM m9 LIMIT 1",
                "435.776014966932"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_SAMP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM m9)",
                "871.552029933863"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,15))::VARCHAR FROM m9",
                "20.875248860000017"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM m10",
                "709.543009160073"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM m10",
                "851.451610992087"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM m10 LIMIT 1",
                "709.543009160073"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_SAMP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM m10)",
                "2766.054874951706"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,15))::VARCHAR FROM m10",
                "26.637248528331018"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM m11",
                "497.875860913324"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM m11",
                "746.813791369986"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM m11 LIMIT 1",
                "497.875860913324"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_SAMP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM m11)",
                "1158.605676741202"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,15))::VARCHAR FROM m11",
                "22.313132028321888"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM m12",
                "16.387640587799"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM m12",
                "32.775281175597"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM m12 LIMIT 1",
                "16.387640587799"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_SAMP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM m12)",
                "32.775281175597"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,15))::VARCHAR FROM m12",
                "4.048165089000077"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM m13",
                "607.523923196438"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM m13",
                "911.285884794658"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM m13 LIMIT 1",
                "607.523923196438"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_SAMP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM m13)",
                "1547.884480561244"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,15))::VARCHAR FROM m13",
                "24.648000389411674"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM m14",
                "1292.865438625438"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM m14",
                "1551.438526350526"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM m14 LIMIT 1",
                "1292.865438625438"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_SAMP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM m14)",
                "2968.870650126285"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,15))::VARCHAR FROM m14",
                "35.956438069217008"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM m15",
                "46.642192765847"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM m15",
                "93.284385531694"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM m15 LIMIT 1",
                "46.642192765847"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_SAMP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM m15)",
                "93.284385531694"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,15))::VARCHAR FROM m15",
                "6.829508969600011"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM m16",
                "642.377340726482"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM m16",
                "1284.754681452963"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM m16 LIMIT 1",
                "642.377340726482"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_SAMP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM m16)",
                "1284.754681452963"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,15))::VARCHAR FROM m16",
                "25.345164050100010"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM m17",
                "1910.805964486139"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM m17",
                "3821.611928972277"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM m17 LIMIT 1",
                "1910.805964486139"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_SAMP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM m17)",
                "3821.611928972277"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,15))::VARCHAR FROM m17",
                "43.712766607550009"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM m18",
                "1170.224579275629"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM m18",
                "1462.780724094537"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM m18 LIMIT 1",
                "1170.224579275629"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_SAMP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM m18)",
                "3378.377738309146"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,15))::VARCHAR FROM m18",
                "34.208545413034287"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM m19",
                "512.237289648770"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM m19",
                "614.684747578525"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM m19 LIMIT 1",
                "512.237289648770"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_SAMP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM m19)",
                "2502.926372749060"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,15))::VARCHAR FROM m19",
                "22.632659800579560"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM m20",
                "1219.3183778837438"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM m20",
                "1463.1820534604925"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM m20 LIMIT 1",
                "1219.3183778837438"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_SAMP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM m20)",
                "3495.5747263678079"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,15))::VARCHAR FROM m20",
                "34.918739637675124"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM m21",
                "752.7831750583340"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM m21",
                "940.9789688229176"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM m21 LIMIT 1",
                "752.7831750583340"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_SAMP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM m21)",
                "2189.6598785614552"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,15))::VARCHAR FROM m21",
                "27.436894413514331"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM m22",
                "608.4096146289659"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM m22",
                "1216.8192292579317"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM m22 LIMIT 1",
                "608.4096146289659"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_SAMP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM m22)",
                "1216.8192292579317"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,15))::VARCHAR FROM m22",
                "24.665960646789451"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM m23",
                "563.7794206690447"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM m23",
                "676.5353048028536"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM m23 LIMIT 1",
                "563.7794206690447"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_SAMP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM m23)",
                "1192.3169674823353"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,15))::VARCHAR FROM m23",
                "23.744039687236135"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM m24",
                "161.1399148159950"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM m24",
                "201.4248935199938"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM m24 LIMIT 1",
                "161.1399148159950"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_SAMP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM m24)",
                "791.2015070226421"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,15))::VARCHAR FROM m24",
                "12.694089759253911"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM m25",
                "759.433114398954535738"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM m25",
                "1139.149671598431803606"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM m25 LIMIT 1",
                "759.433114398954535738"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_SAMP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM m25)",
                "2048.593237751241838373"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,15))::VARCHAR FROM m25",
                "27.557814035205233"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM m26",
                "234.077023985022929658"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM m26",
                "292.596279981278662073"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM m26 LIMIT 1",
                "234.077023985022929658"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_SAMP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM m26)",
                "685.566547811330626416"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,15))::VARCHAR FROM m26",
                "15.299575941346314"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM m27",
                "655.076240774595977394"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM m27",
                "1310.152481549191954787"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM m27 LIMIT 1",
                "655.076240774595977394"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_SAMP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM m27)",
                "1310.152481549191954787"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,15))::VARCHAR FROM m27",
                "25.594457227583394"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM m28",
                "172.246704897181944102"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM m28",
                "344.493409794363888203"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM m28 LIMIT 1",
                "172.246704897181944102"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_SAMP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM m28)",
                "344.493409794363888203"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,15))::VARCHAR FROM m28",
                "13.124279214386668"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM m29",
                "1187.785205787502188845"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM m29",
                "1484.731507234377736057"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM m29 LIMIT 1",
                "1187.785205787502188845"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_SAMP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM m29)",
                "4129.033908900481403419"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,15))::VARCHAR FROM m29",
                "34.464259832288612"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM m30",
                "876.67187500"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM m30",
                "1168.89583333"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM m30 LIMIT 1",
                "876.67187500"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_SAMP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM m30)",
                "2995.38000000"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,15))::VARCHAR FROM m30",
                "29.608645274649092"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM m31",
                "608.097313250000"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM m31",
                "810.796417666667"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM m31 LIMIT 1",
                "608.097313250000"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_SAMP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM m31)",
                "1312.410144500000"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,15))::VARCHAR FROM m31",
                "24.659629219637509"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM m32",
                "1375.902305121825"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM m32",
                "1834.536406829100"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM m32 LIMIT 1",
                "1375.902305121825"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_SAMP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM m32)",
                "4518.821612455200"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,15))::VARCHAR FROM m32",
                "37.093157119903196"},
            {"SELECT (VAR_POP(c))::VARCHAR FROM m33",
                "412.398130588638"},
            {"SELECT (VAR_SAMP(c))::VARCHAR FROM m33",
                "549.864174118184"},
            {"SELECT (VAR_POP(c) OVER ())::VARCHAR FROM m33 LIMIT 1",
                "412.398130588638"},
            {"SELECT (MAX(x))::VARCHAR FROM (SELECT VAR_SAMP(c) OVER (ORDER BY g ROWS BETWEEN 1 PRECEDING "
                + "AND CURRENT ROW) x FROM m33)",
                "641.903550852258"},
            {"SELECT (STDDEV_POP(c)::NUMBER(38,15))::VARCHAR FROM m33",
                "20.307588005192493"},
        });
    }
}
