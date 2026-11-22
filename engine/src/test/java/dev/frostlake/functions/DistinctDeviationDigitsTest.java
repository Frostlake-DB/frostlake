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
 * The standard deviation over DISTINCT values is the FLOAT its plain spelling is, whole to the last digit: the
 * root of the variance rounded to its declared scale, a population's from one value on and a sample's from two,
 * over a NUMBER, a FLOAT, a text and a VARIANT, grouped and windowed (live-verified). Each deviation is read as a
 * NUMBER of twenty decimals, since a FLOAT read as text shows nine.
 */
public class DistinctDeviationDigitsTest extends MeasuredCellsTestSupport {

    @Test
    public void deviationOverDistinctValuesIsWhole() {
        engine.execute("CREATE OR REPLACE TABLE vdd (x NUMBER(38,0), n5 NUMBER(5,2), f FLOAT, g INT)");
        engine.execute("INSERT INTO vdd VALUES (1, 1.25, 1, 1), (3, 3.5, 3, 1), (3, 3.5, 3, 2)");
        assertCells(new String[][] {
            {"SELECT STDDEV(DISTINCT x)::NUMBER(38,20)::VARCHAR FROM vdd",
                "1.41421356237309514547"},
            {"SELECT STDDEV(x)::NUMBER(38,20)::VARCHAR FROM vdd",
                "1.15470039404167534869"},
            {"SELECT STDDEV_SAMP(DISTINCT x)::NUMBER(38,20)::VARCHAR FROM vdd",
                "1.41421356237309514547"},
            {"SELECT STDDEV_SAMP(x)::NUMBER(38,20)::VARCHAR FROM vdd",
                "1.15470039404167534869"},
            {"SELECT STDDEV_POP(DISTINCT x)::NUMBER(38,20)::VARCHAR FROM vdd",
                "1.00000000000000000000"},
            {"SELECT STDDEV_POP(x)::NUMBER(38,20)::VARCHAR FROM vdd",
                "0.94280910050762667396"},
            {"SELECT STDDEV(DISTINCT n5)::NUMBER(38,20)::VARCHAR FROM vdd",
                "1.59099025766973189988"},
            {"SELECT STDDEV(n5)::NUMBER(38,20)::VARCHAR FROM vdd",
                "1.29903810567665800590"},
            {"SELECT STDDEV_SAMP(DISTINCT n5)::NUMBER(38,20)::VARCHAR FROM vdd",
                "1.59099025766973189988"},
            {"SELECT STDDEV_SAMP(n5)::NUMBER(38,20)::VARCHAR FROM vdd",
                "1.29903810567665800590"},
            {"SELECT STDDEV_POP(DISTINCT n5)::NUMBER(38,20)::VARCHAR FROM vdd",
                "1.12500000000000000000"},
            {"SELECT STDDEV_POP(n5)::NUMBER(38,20)::VARCHAR FROM vdd",
                "1.06066017177982119257"},
            {"SELECT STDDEV(DISTINCT f)::NUMBER(38,20)::VARCHAR FROM vdd",
                "1.41421356237309514547"},
            {"SELECT STDDEV(f)::NUMBER(38,20)::VARCHAR FROM vdd",
                "1.15470053837925146212"},
            {"SELECT STDDEV_SAMP(DISTINCT f)::NUMBER(38,20)::VARCHAR FROM vdd",
                "1.41421356237309514547"},
            {"SELECT STDDEV_SAMP(f)::NUMBER(38,20)::VARCHAR FROM vdd",
                "1.15470053837925146212"},
            {"SELECT STDDEV_POP(DISTINCT f)::NUMBER(38,20)::VARCHAR FROM vdd",
                "1.00000000000000000000"},
            {"SELECT STDDEV_POP(f)::NUMBER(38,20)::VARCHAR FROM vdd",
                "0.94280904158206335630"},
            {"SELECT (STDDEV(DISTINCT x) OVER ())::NUMBER(38,20)::VARCHAR FROM vdd LIMIT 1",
                "1.41421356237309514547"},
            {"SELECT STDDEV(DISTINCT x)::NUMBER(38,20)::VARCHAR FROM vdd WHERE g = 2",
                "null"},
            {"SELECT STDDEV(DISTINCT x)::VARCHAR FROM vdd",
                "1.414213562"},
            {"SELECT IFF(STDDEV(DISTINCT x) = STDDEV(DISTINCT f), 'same', 'other') FROM vdd",
                "same"},
            {"SELECT (STDDEV_SAMP(DISTINCT x) OVER ())::NUMBER(38,20)::VARCHAR FROM vdd LIMIT 1",
                "1.41421356237309514547"},
            {"SELECT STDDEV_SAMP(DISTINCT x)::NUMBER(38,20)::VARCHAR FROM vdd WHERE g = 2",
                "null"},
            {"SELECT STDDEV_SAMP(DISTINCT x)::VARCHAR FROM vdd",
                "1.414213562"},
            {"SELECT IFF(STDDEV_SAMP(DISTINCT x) = STDDEV_SAMP(DISTINCT f), 'same', 'other') FROM vdd",
                "same"},
            {"SELECT (STDDEV_POP(DISTINCT x) OVER ())::NUMBER(38,20)::VARCHAR FROM vdd LIMIT 1",
                "1.00000000000000000000"},
            {"SELECT STDDEV_POP(DISTINCT x)::NUMBER(38,20)::VARCHAR FROM vdd WHERE g = 2",
                "0.00000000000000000000"},
            {"SELECT STDDEV_POP(DISTINCT x)::VARCHAR FROM vdd",
                "1"},
            {"SELECT IFF(STDDEV_POP(DISTINCT x) = STDDEV_POP(DISTINCT f), 'same', 'other') FROM vdd",
                "same"},
            {"SELECT IFF(STDDEV(DISTINCT x) = SQRT(2), 'same', 'other') FROM vdd",
                "same"},
            {"SELECT IFF(STDDEV(DISTINCT x) = 1.414213562, 'same', 'other') FROM vdd",
                "other"},
            {"SELECT VAR_SAMP(DISTINCT x) FROM vdd",
                "2.000000"},
            {"SELECT VAR_POP(DISTINCT x) FROM vdd",
                "1.000000"},
            {"SELECT VARIANCE_SAMP(DISTINCT x) FROM vdd",
                "2.000000"},
            {"SELECT VARIANCE_POP(DISTINCT n5) FROM vdd",
                "1.2656250000"},
            {"SELECT STDDEV(DISTINCT x)::NUMBER(38,20)::VARCHAR FROM (SELECT column1 x FROM VALUES "
                + "(1.5),(2.25),(4),(4),(7))",
                "2.44416822933692490238"},
            {"SELECT STDDEV(DISTINCT x)::NUMBER(38,20)::VARCHAR FROM (SELECT column1::VARCHAR x FROM "
                + "VALUES (1.5),(2.25),(4),(4),(7))",
                "2.44416822934374389220"},
            {"SELECT STDDEV(DISTINCT x)::NUMBER(38,20)::VARCHAR FROM (SELECT TO_VARIANT(column1) x FROM "
                + "VALUES (1),(2),(4),(4),(7))",
                "2.64575131106459071617"},
            {"SELECT STDDEV(DISTINCT x)::NUMBER(38,10)::VARCHAR FROM (SELECT column1 x FROM VALUES "
                + "(100000000000),(200000000001),(4),(4),(7))",
                "95742710773.1967315674"},
        });
    }
}
