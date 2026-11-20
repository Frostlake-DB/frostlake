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
 * CORR, COVAR_POP, COVAR_SAMP and the REGR family over constant sides, one row, NULLs, exact NUMBERs, FLOATs, strings
 * and mixed sides: the double sums the account reads, the NULL a side that does not vary gives CORR and REGR_SLOPE,
 * REGR_R2's exact test of an exact side, and REGR_SXY's exact products over two exact sides (live-verified).
 */
public class PairStatisticCellsTest extends MeasuredCellsTestSupport {

    @Test
    public void pairStatistics() {
        assertCells(new String[][] {
            {"SELECT CORR(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.7,1),(0.7,2),(0.7,3),(0.7,4),(0.7,5)))",
                "null"},
            {"SELECT COVAR_POP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (0.7,1),(0.7,2),(0.7,3),(0.7,4),(0.7,5)))",
                "0.000000000000000000000000"},
            {"SELECT COVAR_SAMP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (0.7,1),(0.7,2),(0.7,3),(0.7,4),(0.7,5)))",
                "0.000000000000000000000000"},
            {"SELECT REGR_AVGX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (0.7,1),(0.7,2),(0.7,3),(0.7,4),(0.7,5)))",
                "0.699999999999999955591079"},
            {"SELECT REGR_AVGY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (0.7,1),(0.7,2),(0.7,3),(0.7,4),(0.7,5)))",
                "3.000000000000000000000000"},
            {"SELECT REGR_SXX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.7,1),(0.7,2),(0.7,3),(0.7,4),(0.7,5)))",
                "-0.000000000000000444089210"},
            {"SELECT REGR_SYY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.7,1),(0.7,2),(0.7,3),(0.7,4),(0.7,5)))",
                "10.000000000000000000000000"},
            {"SELECT REGR_SXY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.7,1),(0.7,2),(0.7,3),(0.7,4),(0.7,5)))",
                "0.000000000000000000000000"},
            {"SELECT REGR_SLOPE(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (0.7,1),(0.7,2),(0.7,3),(0.7,4),(0.7,5)))",
                "null"},
            {"SELECT REGR_INTERCEPT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (0.7,1),(0.7,2),(0.7,3),(0.7,4),(0.7,5)))",
                "null"},
            {"SELECT REGR_R2(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.7,1),(0.7,2),(0.7,3),(0.7,4),(0.7,5)))",
                "null"},
            {"SELECT REGR_COUNT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (0.7,1),(0.7,2),(0.7,3),(0.7,4),(0.7,5)))",
                "5.000000000000000000000000"},
            {"SELECT CORR(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.1,1),(0.1,2),(0.1,4)))",
                "null"},
            {"SELECT COVAR_POP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (0.1,1),(0.1,2),(0.1,4)))",
                "-0.000000000000000037007434"},
            {"SELECT COVAR_SAMP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (0.1,1),(0.1,2),(0.1,4)))",
                "-0.000000000000000055511151"},
            {"SELECT REGR_AVGX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (0.1,1),(0.1,2),(0.1,4)))",
                "0.100000000000000019428903"},
            {"SELECT REGR_AVGY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (0.1,1),(0.1,2),(0.1,4)))",
                "2.333333333333333481363070"},
            {"SELECT REGR_SXX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.1,1),(0.1,2),(0.1,4)))",
                "-0.000000000000000003469447"},
            {"SELECT REGR_SYY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.1,1),(0.1,2),(0.1,4)))",
                "4.666666666666667850904560"},
            {"SELECT REGR_SXY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.1,1),(0.1,2),(0.1,4)))",
                "-0.000000000000000222044605"},
            {"SELECT REGR_SLOPE(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (0.1,1),(0.1,2),(0.1,4)))",
                "null"},
            {"SELECT REGR_INTERCEPT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (0.1,1),(0.1,2),(0.1,4)))",
                "null"},
            {"SELECT REGR_R2(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.1,1),(0.1,2),(0.1,4)))",
                "null"},
            {"SELECT REGR_COUNT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (0.1,1),(0.1,2),(0.1,4)))",
                "3.000000000000000000000000"},
            {"SELECT CORR(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y::FLOAT y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (0.1,1),(0.1,2),(0.1,4)))",
                "null"},
            {"SELECT COVAR_POP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y::FLOAT y FROM "
                + "(SELECT column1 x, column2 y FROM VALUES (0.1,1),(0.1,2),(0.1,4)))",
                "-0.000000000000000037007434"},
            {"SELECT COVAR_SAMP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y::FLOAT y FROM "
                + "(SELECT column1 x, column2 y FROM VALUES (0.1,1),(0.1,2),(0.1,4)))",
                "-0.000000000000000055511151"},
            {"SELECT REGR_AVGX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y::FLOAT y FROM "
                + "(SELECT column1 x, column2 y FROM VALUES (0.1,1),(0.1,2),(0.1,4)))",
                "0.100000000000000019428903"},
            {"SELECT REGR_AVGY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y::FLOAT y FROM "
                + "(SELECT column1 x, column2 y FROM VALUES (0.1,1),(0.1,2),(0.1,4)))",
                "2.333333333333333481363070"},
            {"SELECT REGR_SXX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y::FLOAT y FROM "
                + "(SELECT column1 x, column2 y FROM VALUES (0.1,1),(0.1,2),(0.1,4)))",
                "-0.000000000000000003469447"},
            {"SELECT REGR_SYY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y::FLOAT y FROM "
                + "(SELECT column1 x, column2 y FROM VALUES (0.1,1),(0.1,2),(0.1,4)))",
                "4.666666666666667850904560"},
            {"SELECT REGR_SXY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y::FLOAT y FROM "
                + "(SELECT column1 x, column2 y FROM VALUES (0.1,1),(0.1,2),(0.1,4)))",
                "-0.000000000000000111022302"},
            {"SELECT REGR_SLOPE(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y::FLOAT y FROM "
                + "(SELECT column1 x, column2 y FROM VALUES (0.1,1),(0.1,2),(0.1,4)))",
                "null"},
            {"SELECT REGR_INTERCEPT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y::FLOAT y FROM"
                + " (SELECT column1 x, column2 y FROM VALUES (0.1,1),(0.1,2),(0.1,4)))",
                "null"},
            {"SELECT REGR_R2(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y::FLOAT y FROM "
                + "(SELECT column1 x, column2 y FROM VALUES (0.1,1),(0.1,2),(0.1,4)))",
                "null"},
            {"SELECT REGR_COUNT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y::FLOAT y FROM "
                + "(SELECT column1 x, column2 y FROM VALUES (0.1,1),(0.1,2),(0.1,4)))",
                "3.000000000000000000000000"},
            {"SELECT CORR(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (1,0.7),(2,0.7),(3,0.7),(4,0.7),(5,0.7)))",
                "null"},
            {"SELECT COVAR_POP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (1,0.7),(2,0.7),(3,0.7),(4,0.7),(5,0.7)))",
                "0.000000000000000000000000"},
            {"SELECT COVAR_SAMP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (1,0.7),(2,0.7),(3,0.7),(4,0.7),(5,0.7)))",
                "0.000000000000000000000000"},
            {"SELECT REGR_AVGX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (1,0.7),(2,0.7),(3,0.7),(4,0.7),(5,0.7)))",
                "3.000000000000000000000000"},
            {"SELECT REGR_AVGY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (1,0.7),(2,0.7),(3,0.7),(4,0.7),(5,0.7)))",
                "0.699999999999999955591079"},
            {"SELECT REGR_SXX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (1,0.7),(2,0.7),(3,0.7),(4,0.7),(5,0.7)))",
                "10.000000000000000000000000"},
            {"SELECT REGR_SYY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (1,0.7),(2,0.7),(3,0.7),(4,0.7),(5,0.7)))",
                "-0.000000000000000444089210"},
            {"SELECT REGR_SXY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (1,0.7),(2,0.7),(3,0.7),(4,0.7),(5,0.7)))",
                "0.000000000000000000000000"},
            {"SELECT REGR_SLOPE(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (1,0.7),(2,0.7),(3,0.7),(4,0.7),(5,0.7)))",
                "0.000000000000000000000000"},
            {"SELECT REGR_INTERCEPT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (1,0.7),(2,0.7),(3,0.7),(4,0.7),(5,0.7)))",
                "0.699999999999999955591079"},
            {"SELECT REGR_R2(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (1,0.7),(2,0.7),(3,0.7),(4,0.7),(5,0.7)))",
                "1.000000000000000000000000"},
            {"SELECT REGR_COUNT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (1,0.7),(2,0.7),(3,0.7),(4,0.7),(5,0.7)))",
                "5.000000000000000000000000"},
            {"SELECT CORR(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (1,0.1),(2,0.1),(4,0.1)))",
                "null"},
            {"SELECT COVAR_POP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (1,0.1),(2,0.1),(4,0.1)))",
                "-0.000000000000000037007434"},
            {"SELECT COVAR_SAMP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (1,0.1),(2,0.1),(4,0.1)))",
                "-0.000000000000000055511151"},
            {"SELECT REGR_AVGX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (1,0.1),(2,0.1),(4,0.1)))",
                "2.333333333333333481363070"},
            {"SELECT REGR_AVGY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (1,0.1),(2,0.1),(4,0.1)))",
                "0.100000000000000019428903"},
            {"SELECT REGR_SXX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (1,0.1),(2,0.1),(4,0.1)))",
                "4.666666666666667850904560"},
            {"SELECT REGR_SYY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (1,0.1),(2,0.1),(4,0.1)))",
                "-0.000000000000000003469447"},
            {"SELECT REGR_SXY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (1,0.1),(2,0.1),(4,0.1)))",
                "-0.000000000000000222044605"},
            {"SELECT REGR_SLOPE(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (1,0.1),(2,0.1),(4,0.1)))",
                "-0.000000000000000023790493"},
            {"SELECT REGR_INTERCEPT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (1,0.1),(2,0.1),(4,0.1)))",
                "0.100000000000000074940054"},
            {"SELECT REGR_R2(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (1,0.1),(2,0.1),(4,0.1)))",
                "1.000000000000000000000000"},
            {"SELECT REGR_COUNT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (1,0.1),(2,0.1),(4,0.1)))",
                "3.000000000000000000000000"},
            {"SELECT CORR(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "1.500000000000000000000000"},
            {"SELECT COVAR_POP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "0.000000000000000074014868"},
            {"SELECT COVAR_SAMP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "0.000000000000000111022302"},
            {"SELECT REGR_AVGX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "0.299999999999999988897770"},
            {"SELECT REGR_AVGY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "0.699999999999999844568777"},
            {"SELECT REGR_SXX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "0.000000000000000055511151"},
            {"SELECT REGR_SYY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "0.000000000000000222044605"},
            {"SELECT REGR_SXY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "0.000000000000000222044605"},
            {"SELECT REGR_SLOPE(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "3.000000000000000000000000"},
            {"SELECT REGR_INTERCEPT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "-0.200000000000000066613381"},
            {"SELECT REGR_R2(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "null"},
            {"SELECT REGR_COUNT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "3.000000000000000000000000"},
            {"SELECT CORR(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y::FLOAT y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "1.500000000000000000000000"},
            {"SELECT COVAR_POP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y::FLOAT y FROM "
                + "(SELECT column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "0.000000000000000074014868"},
            {"SELECT COVAR_SAMP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y::FLOAT y FROM "
                + "(SELECT column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "0.000000000000000111022302"},
            {"SELECT REGR_AVGX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y::FLOAT y FROM "
                + "(SELECT column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "0.299999999999999988897770"},
            {"SELECT REGR_AVGY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y::FLOAT y FROM "
                + "(SELECT column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "0.699999999999999844568777"},
            {"SELECT REGR_SXX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y::FLOAT y FROM "
                + "(SELECT column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "0.000000000000000055511151"},
            {"SELECT REGR_SYY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y::FLOAT y FROM "
                + "(SELECT column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "0.000000000000000222044605"},
            {"SELECT REGR_SXY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y::FLOAT y FROM "
                + "(SELECT column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "0.000000000000000222044605"},
            {"SELECT REGR_SLOPE(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y::FLOAT y FROM "
                + "(SELECT column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "3.000000000000000000000000"},
            {"SELECT REGR_INTERCEPT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y::FLOAT y FROM"
                + " (SELECT column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "-0.200000000000000066613381"},
            {"SELECT REGR_R2(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y::FLOAT y FROM "
                + "(SELECT column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "2.250000000000000000000000"},
            {"SELECT REGR_COUNT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y::FLOAT y FROM "
                + "(SELECT column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "3.000000000000000000000000"},
            {"SELECT CORR(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES "
                + "(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1)))",
                "null"},
            {"SELECT COVAR_POP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES "
                + "(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1)))",
                "-0.000000000000000001982541"},
            {"SELECT COVAR_SAMP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES "
                + "(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1)))",
                "-0.000000000000000002312965"},
            {"SELECT REGR_AVGX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES "
                + "(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1)))",
                "0.100000000000000005551115"},
            {"SELECT REGR_AVGY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES "
                + "(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1)))",
                "0.100000000000000005551115"},
            {"SELECT REGR_SXX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES "
                + "(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1)))",
                "-0.000000000000000013877788"},
            {"SELECT REGR_SYY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES "
                + "(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1)))",
                "-0.000000000000000013877788"},
            {"SELECT REGR_SXY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES "
                + "(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1)))",
                "-0.000000000000000013877788"},
            {"SELECT REGR_SLOPE(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES "
                + "(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1)))",
                "null"},
            {"SELECT REGR_INTERCEPT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES "
                + "(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1)))",
                "null"},
            {"SELECT REGR_R2(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES "
                + "(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1)))",
                "null"},
            {"SELECT REGR_COUNT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES "
                + "(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1),(0.1,0.1)))",
                "7.000000000000000000000000"},
            {"SELECT CORR(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.3,0.7)))",
                "null"},
            {"SELECT COVAR_POP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (0.3,0.7)))",
                "0.000000000000000000000000"},
            {"SELECT COVAR_SAMP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (0.3,0.7)))",
                "null"},
            {"SELECT REGR_AVGX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (0.3,0.7)))",
                "0.299999999999999988897770"},
            {"SELECT REGR_AVGY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (0.3,0.7)))",
                "0.699999999999999955591079"},
            {"SELECT REGR_SXX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.3,0.7)))",
                "0.000000000000000000000000"},
            {"SELECT REGR_SYY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.3,0.7)))",
                "0.000000000000000000000000"},
            {"SELECT REGR_SXY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.3,0.7)))",
                "0.000000000000000000000000"},
            {"SELECT REGR_SLOPE(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (0.3,0.7)))",
                "null"},
            {"SELECT REGR_INTERCEPT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (0.3,0.7)))",
                "null"},
            {"SELECT REGR_R2(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.3,0.7)))",
                "null"},
            {"SELECT REGR_COUNT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (0.3,0.7)))",
                "1.000000000000000000000000"},
            {"SELECT CORR(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.1,NULL),(0.2,1.5),(NULL,2.5),(0.4,2.7),(0.5,3.1),(0.9,4.9)))",
                "0.996957232450562891301615"},
            {"SELECT COVAR_POP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (0.1,NULL),(0.2,1.5),(NULL,2.5),(0.4,2.7),(0.5,3.1),(0.9,4.9)))",
                "0.309999999999999831246100"},
            {"SELECT COVAR_SAMP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (0.1,NULL),(0.2,1.5),(NULL,2.5),(0.4,2.7),(0.5,3.1),(0.9,4.9)))",
                "0.413333333333333108328134"},
            {"SELECT REGR_AVGX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (0.1,NULL),(0.2,1.5),(NULL,2.5),(0.4,2.7),(0.5,3.1),(0.9,4.9)))",
                "0.500000000000000000000000"},
            {"SELECT REGR_AVGY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (0.1,NULL),(0.2,1.5),(NULL,2.5),(0.4,2.7),(0.5,3.1),(0.9,4.9)))",
                "3.050000000000000266453526"},
            {"SELECT REGR_SXX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.1,NULL),(0.2,1.5),(NULL,2.5),(0.4,2.7),(0.5,3.1),(0.9,4.9)))",
                "0.260000000000000230926389"},
            {"SELECT REGR_SYY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.1,NULL),(0.2,1.5),(NULL,2.5),(0.4,2.7),(0.5,3.1),(0.9,4.9)))",
                "5.950000000000002842170943"},
            {"SELECT REGR_SXY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.1,NULL),(0.2,1.5),(NULL,2.5),(0.4,2.7),(0.5,3.1),(0.9,4.9)))",
                "1.239999999999999324984401"},
            {"SELECT REGR_SLOPE(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (0.1,NULL),(0.2,1.5),(NULL,2.5),(0.4,2.7),(0.5,3.1),(0.9,4.9)))",
                "4.769230769230762057020456"},
            {"SELECT REGR_INTERCEPT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES "
                + "(0.1,NULL),(0.2,1.5),(NULL,2.5),(0.4,2.7),(0.5,3.1),(0.9,4.9)))",
                "0.665384615384619237943298"},
            {"SELECT REGR_R2(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.1,NULL),(0.2,1.5),(NULL,2.5),(0.4,2.7),(0.5,3.1),(0.9,4.9)))",
                "0.993923723335485731489314"},
            {"SELECT REGR_COUNT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (0.1,NULL),(0.2,1.5),(NULL,2.5),(0.4,2.7),(0.5,3.1),(0.9,4.9)))",
                "4.000000000000000000000000"},
            {"SELECT CORR(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.1,1.5),(0.2,2.7),(0.3,3.1),(0.4,4.9),(0.7,0.2)))",
                "-0.305326118134200685805268"},
            {"SELECT COVAR_POP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (0.1,1.5),(0.2,2.7),(0.3,3.1),(0.4,4.9),(0.7,0.2)))",
                "-0.099199999999999816102658"},
            {"SELECT COVAR_SAMP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (0.1,1.5),(0.2,2.7),(0.3,3.1),(0.4,4.9),(0.7,0.2)))",
                "-0.123999999999999777067217"},
            {"SELECT REGR_AVGX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (0.1,1.5),(0.2,2.7),(0.3,3.1),(0.4,4.9),(0.7,0.2)))",
                "0.339999999999999968913755"},
            {"SELECT REGR_AVGY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (0.1,1.5),(0.2,2.7),(0.3,3.1),(0.4,4.9),(0.7,0.2)))",
                "2.479999999999999982236432"},
            {"SELECT REGR_SXX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.1,1.5),(0.2,2.7),(0.3,3.1),(0.4,4.9),(0.7,0.2)))",
                "0.212000000000000077271523"},
            {"SELECT REGR_SYY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.1,1.5),(0.2,2.7),(0.3,3.1),(0.4,4.9),(0.7,0.2)))",
                "12.448000000000007503331290"},
            {"SELECT REGR_SXY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.1,1.5),(0.2,2.7),(0.3,3.1),(0.4,4.9),(0.7,0.2)))",
                "-0.495999999999999108268867"},
            {"SELECT REGR_SLOPE(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (0.1,1.5),(0.2,2.7),(0.3,3.1),(0.4,4.9),(0.7,0.2)))",
                "-2.339622641509428557782257"},
            {"SELECT REGR_INTERCEPT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (0.1,1.5),(0.2,2.7),(0.3,3.1),(0.4,4.9),(0.7,0.2)))",
                "3.275471698113205487601363"},
            {"SELECT REGR_R2(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.1,1.5),(0.2,2.7),(0.3,3.1),(0.4,4.9),(0.7,0.2)))",
                "0.093224038414899873528086"},
            {"SELECT REGR_COUNT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (0.1,1.5),(0.2,2.7),(0.3,3.1),(0.4,4.9),(0.7,0.2)))",
                "5.000000000000000000000000"},
            {"SELECT CORR(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (100000000.1,1),(100000000.2,2),(100000000.3,4),(100000000.4,3)))",
                "null"},
            {"SELECT COVAR_POP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (100000000.1,1),(100000000.2,2),(100000000.3,4),(100000000.4,3)))",
                "0.100000023841857910156250"},
            {"SELECT COVAR_SAMP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (100000000.1,1),(100000000.2,2),(100000000.3,4),(100000000.4,3)))",
                "0.133333365122477204289808"},
            {"SELECT REGR_AVGX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (100000000.1,1),(100000000.2,2),(100000000.3,4),(100000000.4,3)))",
                "100000000.250000000000000000000000"},
            {"SELECT REGR_AVGY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (100000000.1,1),(100000000.2,2),(100000000.3,4),(100000000.4,3)))",
                "2.500000000000000000000000"},
            {"SELECT REGR_SXX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (100000000.1,1),(100000000.2,2),(100000000.3,4),(100000000.4,3)))",
                "0.000000000000000000000000"},
            {"SELECT REGR_SYY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (100000000.1,1),(100000000.2,2),(100000000.3,4),(100000000.4,3)))",
                "5.000000000000000000000000"},
            {"SELECT REGR_SXY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (100000000.1,1),(100000000.2,2),(100000000.3,4),(100000000.4,3)))",
                "0.399999976158142089843750"},
            {"SELECT REGR_SLOPE(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (100000000.1,1),(100000000.2,2),(100000000.3,4),(100000000.4,3)))",
                "null"},
            {"SELECT REGR_INTERCEPT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES "
                + "(100000000.1,1),(100000000.2,2),(100000000.3,4),(100000000.4,3)))",
                "null"},
            {"SELECT REGR_R2(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (100000000.1,1),(100000000.2,2),(100000000.3,4),(100000000.4,3)))",
                "null"},
            {"SELECT REGR_COUNT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (100000000.1,1),(100000000.2,2),(100000000.3,4),(100000000.4,3)))",
                "4.000000000000000000000000"},
            {"SELECT CORR(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y::FLOAT y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES "
                + "(100000000.1,1),(100000000.2,2),(100000000.3,4),(100000000.4,3)))",
                "null"},
            {"SELECT COVAR_POP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y::FLOAT y FROM "
                + "(SELECT column1 x, column2 y FROM VALUES "
                + "(100000000.1,1),(100000000.2,2),(100000000.3,4),(100000000.4,3)))",
                "0.100000023841857910156250"},
            {"SELECT COVAR_SAMP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y::FLOAT y FROM "
                + "(SELECT column1 x, column2 y FROM VALUES "
                + "(100000000.1,1),(100000000.2,2),(100000000.3,4),(100000000.4,3)))",
                "0.133333365122477204289808"},
            {"SELECT REGR_AVGX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y::FLOAT y FROM "
                + "(SELECT column1 x, column2 y FROM VALUES "
                + "(100000000.1,1),(100000000.2,2),(100000000.3,4),(100000000.4,3)))",
                "100000000.250000000000000000000000"},
            {"SELECT REGR_AVGY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y::FLOAT y FROM "
                + "(SELECT column1 x, column2 y FROM VALUES "
                + "(100000000.1,1),(100000000.2,2),(100000000.3,4),(100000000.4,3)))",
                "2.500000000000000000000000"},
            {"SELECT REGR_SXX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y::FLOAT y FROM "
                + "(SELECT column1 x, column2 y FROM VALUES "
                + "(100000000.1,1),(100000000.2,2),(100000000.3,4),(100000000.4,3)))",
                "0.000000000000000000000000"},
            {"SELECT REGR_SYY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y::FLOAT y FROM "
                + "(SELECT column1 x, column2 y FROM VALUES "
                + "(100000000.1,1),(100000000.2,2),(100000000.3,4),(100000000.4,3)))",
                "5.000000000000000000000000"},
            {"SELECT REGR_SXY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y::FLOAT y FROM "
                + "(SELECT column1 x, column2 y FROM VALUES "
                + "(100000000.1,1),(100000000.2,2),(100000000.3,4),(100000000.4,3)))",
                "0.400000095367431640625000"},
            {"SELECT REGR_SLOPE(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y::FLOAT y FROM "
                + "(SELECT column1 x, column2 y FROM VALUES "
                + "(100000000.1,1),(100000000.2,2),(100000000.3,4),(100000000.4,3)))",
                "null"},
            {"SELECT REGR_INTERCEPT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y::FLOAT y FROM"
                + " (SELECT column1 x, column2 y FROM VALUES "
                + "(100000000.1,1),(100000000.2,2),(100000000.3,4),(100000000.4,3)))",
                "null"},
            {"SELECT REGR_R2(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y::FLOAT y FROM "
                + "(SELECT column1 x, column2 y FROM VALUES "
                + "(100000000.1,1),(100000000.2,2),(100000000.3,4),(100000000.4,3)))",
                "null"},
            {"SELECT REGR_COUNT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y::FLOAT y FROM "
                + "(SELECT column1 x, column2 y FROM VALUES "
                + "(100000000.1,1),(100000000.2,2),(100000000.3,4),(100000000.4,3)))",
                "4.000000000000000000000000"},
            {"SELECT CORR(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.1,0.3),(0.2,0.6),(0.3,0.9)))",
                "0.999999999999999444888488"},
            {"SELECT COVAR_POP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (0.1,0.3),(0.2,0.6),(0.3,0.9)))",
                "0.020000000000000000416334"},
            {"SELECT COVAR_SAMP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (0.1,0.3),(0.2,0.6),(0.3,0.9)))",
                "0.029999999999999998889777"},
            {"SELECT REGR_AVGX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (0.1,0.3),(0.2,0.6),(0.3,0.9)))",
                "0.199999999999999983346655"},
            {"SELECT REGR_AVGY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (0.1,0.3),(0.2,0.6),(0.3,0.9)))",
                "0.599999999999999977795540"},
            {"SELECT REGR_SXX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.1,0.3),(0.2,0.6),(0.3,0.9)))",
                "0.020000000000000017763568"},
            {"SELECT REGR_SYY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.1,0.3),(0.2,0.6),(0.3,0.9)))",
                "0.179999999999999937827511"},
            {"SELECT REGR_SXY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.1,0.3),(0.2,0.6),(0.3,0.9)))",
                "0.059999999999999942268403"},
            {"SELECT REGR_SLOPE(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (0.1,0.3),(0.2,0.6),(0.3,0.9)))",
                "2.999999999999997335464741"},
            {"SELECT REGR_INTERCEPT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (0.1,0.3),(0.2,0.6),(0.3,0.9)))",
                "0.000000000000000555111512"},
            {"SELECT REGR_R2(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.1,0.3),(0.2,0.6),(0.3,0.9)))",
                "0.999999999999998889776975"},
            {"SELECT REGR_COUNT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (0.1,0.3),(0.2,0.6),(0.3,0.9)))",
                "3.000000000000000000000000"},
            {"SELECT CORR(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (100000000.1,5),(100000000.2,5),(100000000.3,5),(100000000.4,5)))",
                "null"},
            {"SELECT COVAR_POP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (100000000.1,5),(100000000.2,5),(100000000.3,5),(100000000.4,5)))",
                "0.000000000000000000000000"},
            {"SELECT COVAR_SAMP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (100000000.1,5),(100000000.2,5),(100000000.3,5),(100000000.4,5)))",
                "0.000000000000000000000000"},
            {"SELECT REGR_AVGX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (100000000.1,5),(100000000.2,5),(100000000.3,5),(100000000.4,5)))",
                "100000000.250000000000000000000000"},
            {"SELECT REGR_AVGY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (100000000.1,5),(100000000.2,5),(100000000.3,5),(100000000.4,5)))",
                "5.000000000000000000000000"},
            {"SELECT REGR_SXX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (100000000.1,5),(100000000.2,5),(100000000.3,5),(100000000.4,5)))",
                "0.000000000000000000000000"},
            {"SELECT REGR_SYY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (100000000.1,5),(100000000.2,5),(100000000.3,5),(100000000.4,5)))",
                "0.000000000000000000000000"},
            {"SELECT REGR_SXY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (100000000.1,5),(100000000.2,5),(100000000.3,5),(100000000.4,5)))",
                "0.000000000000000000000000"},
            {"SELECT REGR_SLOPE(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (100000000.1,5),(100000000.2,5),(100000000.3,5),(100000000.4,5)))",
                "null"},
            {"SELECT REGR_INTERCEPT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES "
                + "(100000000.1,5),(100000000.2,5),(100000000.3,5),(100000000.4,5)))",
                "null"},
            {"SELECT REGR_R2(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (100000000.1,5),(100000000.2,5),(100000000.3,5),(100000000.4,5)))",
                "1.000000000000000000000000"},
            {"SELECT REGR_COUNT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (100000000.1,5),(100000000.2,5),(100000000.3,5),(100000000.4,5)))",
                "4.000000000000000000000000"},
            {"SELECT CORR(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.0000001,1),(0.0000002,2),(0.0000003,4)))",
                "0.981980506061965852104834"},
            {"SELECT COVAR_POP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (0.0000001,1),(0.0000002,2),(0.0000003,4)))",
                "0.000000099999999999999956"},
            {"SELECT COVAR_SAMP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (0.0000001,1),(0.0000002,2),(0.0000003,4)))",
                "0.000000149999999999999940"},
            {"SELECT REGR_AVGX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (0.0000001,1),(0.0000002,2),(0.0000003,4)))",
                "0.000000199999999999999991"},
            {"SELECT REGR_AVGY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (0.0000001,1),(0.0000002,2),(0.0000003,4)))",
                "2.333333333333333481363070"},
            {"SELECT REGR_SXX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.0000001,1),(0.0000002,2),(0.0000003,4)))",
                "0.000000000000020000000000"},
            {"SELECT REGR_SYY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.0000001,1),(0.0000002,2),(0.0000003,4)))",
                "4.666666666666667850904560"},
            {"SELECT REGR_SXY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.0000001,1),(0.0000002,2),(0.0000003,4)))",
                "0.000000300000000000000092"},
            {"SELECT REGR_SLOPE(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (0.0000001,1),(0.0000002,2),(0.0000003,4)))",
                "15000000.000000005587935447692871"},
            {"SELECT REGR_INTERCEPT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (0.0000001,1),(0.0000002,2),(0.0000003,4)))",
                "-0.666666666666667406815350"},
            {"SELECT REGR_R2(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.0000001,1),(0.0000002,2),(0.0000003,4)))",
                "null"},
            {"SELECT REGR_COUNT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (0.0000001,1),(0.0000002,2),(0.0000003,4)))",
                "3.000000000000000000000000"},
            {"SELECT CORR(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (1,0.0000001),(2,0.0000002),(4,0.0000003)))",
                "0.981980506061965852104834"},
            {"SELECT COVAR_POP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (1,0.0000001),(2,0.0000002),(4,0.0000003)))",
                "0.000000099999999999999956"},
            {"SELECT COVAR_SAMP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (1,0.0000001),(2,0.0000002),(4,0.0000003)))",
                "0.000000149999999999999940"},
            {"SELECT REGR_AVGX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (1,0.0000001),(2,0.0000002),(4,0.0000003)))",
                "2.333333333333333481363070"},
            {"SELECT REGR_AVGY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (1,0.0000001),(2,0.0000002),(4,0.0000003)))",
                "0.000000199999999999999991"},
            {"SELECT REGR_SXX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (1,0.0000001),(2,0.0000002),(4,0.0000003)))",
                "4.666666666666667850904560"},
            {"SELECT REGR_SYY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (1,0.0000001),(2,0.0000002),(4,0.0000003)))",
                "0.000000000000020000000000"},
            {"SELECT REGR_SXY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (1,0.0000001),(2,0.0000002),(4,0.0000003)))",
                "0.000000300000000000000092"},
            {"SELECT REGR_SLOPE(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (1,0.0000001),(2,0.0000002),(4,0.0000003)))",
                "0.000000064285714285714251"},
            {"SELECT REGR_INTERCEPT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (1,0.0000001),(2,0.0000002),(4,0.0000003)))",
                "0.000000050000000000000051"},
            {"SELECT REGR_R2(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (1,0.0000001),(2,0.0000002),(4,0.0000003)))",
                "1.000000000000000000000000"},
            {"SELECT REGR_COUNT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (1,0.0000001),(2,0.0000002),(4,0.0000003)))",
                "3.000000000000000000000000"},
            {"SELECT CORR(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y::FLOAT y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "1.500000000000000000000000"},
            {"SELECT COVAR_POP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y::FLOAT y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "0.000000000000000074014868"},
            {"SELECT COVAR_SAMP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y::FLOAT y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "0.000000000000000111022302"},
            {"SELECT REGR_AVGX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y::FLOAT y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "0.299999999999999988897770"},
            {"SELECT REGR_AVGY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y::FLOAT y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "0.699999999999999844568777"},
            {"SELECT REGR_SXX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y::FLOAT y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "0.000000000000000055511151"},
            {"SELECT REGR_SYY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y::FLOAT y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "0.000000000000000222044605"},
            {"SELECT REGR_SXY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y::FLOAT y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "0.000000000000000222044605"},
            {"SELECT REGR_SLOPE(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y::FLOAT y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "3.000000000000000000000000"},
            {"SELECT REGR_INTERCEPT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y::FLOAT y FROM "
                + "(SELECT column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "-0.200000000000000066613381"},
            {"SELECT REGR_R2(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y::FLOAT y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "null"},
            {"SELECT REGR_COUNT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y::FLOAT y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "3.000000000000000000000000"},
            {"SELECT CORR(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "1.500000000000000000000000"},
            {"SELECT COVAR_POP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "0.000000000000000074014868"},
            {"SELECT COVAR_SAMP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "0.000000000000000111022302"},
            {"SELECT REGR_AVGX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "0.299999999999999988897770"},
            {"SELECT REGR_AVGY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "0.699999999999999844568777"},
            {"SELECT REGR_SXX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "0.000000000000000055511151"},
            {"SELECT REGR_SYY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "0.000000000000000222044605"},
            {"SELECT REGR_SXY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "0.000000000000000222044605"},
            {"SELECT REGR_SLOPE(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "3.000000000000000000000000"},
            {"SELECT REGR_INTERCEPT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y y FROM "
                + "(SELECT column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "-0.200000000000000066613381"},
            {"SELECT REGR_R2(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "1.000000000000000000000000"},
            {"SELECT REGR_COUNT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::FLOAT x, y y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "3.000000000000000000000000"},
            {"SELECT CORR(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y::FLOAT y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (0.1,1),(0.1,2),(0.1,4)))",
                "null"},
            {"SELECT COVAR_POP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y::FLOAT y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (0.1,1),(0.1,2),(0.1,4)))",
                "-0.000000000000000037007434"},
            {"SELECT COVAR_SAMP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y::FLOAT y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (0.1,1),(0.1,2),(0.1,4)))",
                "-0.000000000000000055511151"},
            {"SELECT REGR_AVGX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y::FLOAT y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (0.1,1),(0.1,2),(0.1,4)))",
                "0.100000000000000019428903"},
            {"SELECT REGR_AVGY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y::FLOAT y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (0.1,1),(0.1,2),(0.1,4)))",
                "2.333333333333333481363070"},
            {"SELECT REGR_SXX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y::FLOAT y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (0.1,1),(0.1,2),(0.1,4)))",
                "-0.000000000000000003469447"},
            {"SELECT REGR_SYY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y::FLOAT y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (0.1,1),(0.1,2),(0.1,4)))",
                "4.666666666666667850904560"},
            {"SELECT REGR_SXY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y::FLOAT y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (0.1,1),(0.1,2),(0.1,4)))",
                "-0.000000000000000111022302"},
            {"SELECT REGR_SLOPE(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y::FLOAT y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (0.1,1),(0.1,2),(0.1,4)))",
                "null"},
            {"SELECT REGR_INTERCEPT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y::FLOAT y FROM "
                + "(SELECT column1 x, column2 y FROM VALUES (0.1,1),(0.1,2),(0.1,4)))",
                "null"},
            {"SELECT REGR_R2(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y::FLOAT y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (0.1,1),(0.1,2),(0.1,4)))",
                "null"},
            {"SELECT REGR_COUNT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y::FLOAT y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES (0.1,1),(0.1,2),(0.1,4)))",
                "3.000000000000000000000000"},
            {"SELECT CORR(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::VARCHAR x, y::VARCHAR y FROM "
                + "(SELECT column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "1.500000000000000000000000"},
            {"SELECT COVAR_POP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::VARCHAR x, y::VARCHAR y FROM "
                + "(SELECT column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "0.000000000000000074014868"},
            {"SELECT COVAR_SAMP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::VARCHAR x, y::VARCHAR y FROM"
                + " (SELECT column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "0.000000000000000111022302"},
            {"SELECT REGR_AVGX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::VARCHAR x, y::VARCHAR y FROM "
                + "(SELECT column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "0.299999999999999988897770"},
            {"SELECT REGR_AVGY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::VARCHAR x, y::VARCHAR y FROM "
                + "(SELECT column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "0.699999999999999844568777"},
            {"SELECT REGR_SXX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::VARCHAR x, y::VARCHAR y FROM "
                + "(SELECT column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "0.000000000000000055511151"},
            {"SELECT REGR_SYY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::VARCHAR x, y::VARCHAR y FROM "
                + "(SELECT column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "0.000000000000000222044605"},
            {"SELECT REGR_SXY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::VARCHAR x, y::VARCHAR y FROM "
                + "(SELECT column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "0.000000000000000222044605"},
            {"SELECT REGR_SLOPE(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::VARCHAR x, y::VARCHAR y FROM"
                + " (SELECT column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "3.000000000000000000000000"},
            {"SELECT REGR_INTERCEPT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::VARCHAR x, y::VARCHAR y "
                + "FROM (SELECT column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "-0.200000000000000066613381"},
            {"SELECT REGR_R2(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::VARCHAR x, y::VARCHAR y FROM "
                + "(SELECT column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "2.250000000000000000000000"},
            {"SELECT REGR_COUNT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x::VARCHAR x, y::VARCHAR y FROM"
                + " (SELECT column1 x, column2 y FROM VALUES (0.3,0.7),(0.3,0.7),(0.3,0.7)))",
                "3.000000000000000000000000"},
            {"SELECT CORR(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.0000001,0.0000005),(0.0000003,0.0000001),(0.0000002,0.0000009)))",
                "-0.500000000000000222044605"},
            {"SELECT COVAR_POP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (0.0000001,0.0000005),(0.0000003,0.0000001),(0.0000002,0.0000009)))",
                "-0.000000000000013333333333"},
            {"SELECT COVAR_SAMP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (0.0000001,0.0000005),(0.0000003,0.0000001),(0.0000002,0.0000009)))",
                "-0.000000000000020000000000"},
            {"SELECT REGR_AVGX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (0.0000001,0.0000005),(0.0000003,0.0000001),(0.0000002,0.0000009)))",
                "0.000000199999999999999991"},
            {"SELECT REGR_AVGY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES (0.0000001,0.0000005),(0.0000003,0.0000001),(0.0000002,0.0000009)))",
                "0.000000499999999999999977"},
            {"SELECT REGR_SXX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.0000001,0.0000005),(0.0000003,0.0000001),(0.0000002,0.0000009)))",
                "0.000000000000020000000000"},
            {"SELECT REGR_SYY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.0000001,0.0000005),(0.0000003,0.0000001),(0.0000002,0.0000009)))",
                "0.000000000000320000000000"},
            {"SELECT REGR_SXY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.0000001,0.0000005),(0.0000003,0.0000001),(0.0000002,0.0000009)))",
                "-0.000000000000300000000000"},
            {"SELECT REGR_SLOPE(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (0.0000001,0.0000005),(0.0000003,0.0000001),(0.0000002,0.0000009)))",
                "-2.000000000000000888178420"},
            {"SELECT REGR_INTERCEPT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES "
                + "(0.0000001,0.0000005),(0.0000003,0.0000001),(0.0000002,0.0000009)))",
                "0.000000900000000000000065"},
            {"SELECT REGR_R2(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES (0.0000001,0.0000005),(0.0000003,0.0000001),(0.0000002,0.0000009)))",
                "null"},
            {"SELECT REGR_COUNT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES (0.0000001,0.0000005),(0.0000003,0.0000001),(0.0000002,0.0000009)))",
                "3.000000000000000000000000"},
            {"SELECT CORR(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES "
                + "(0.0000001234567,0.0000005),(0.0000003,0.000000177777),(0.0000002,0.0000009)))",
                "-0.512474348454919348405667"},
            {"SELECT COVAR_POP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES "
                + "(0.0000001234567,0.0000005),(0.0000003,0.000000177777),(0.0000002,0.0000009)))",
                "-0.000000000000010943476862"},
            {"SELECT COVAR_SAMP(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES "
                + "(0.0000001234567,0.0000005),(0.0000003,0.000000177777),(0.0000002,0.0000009)))",
                "-0.000000000000016415215293"},
            {"SELECT REGR_AVGX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES "
                + "(0.0000001234567,0.0000005),(0.0000003,0.000000177777),(0.0000002,0.0000009)))",
                "0.000000207818899999999998"},
            {"SELECT REGR_AVGY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x,"
                + " column2 y FROM VALUES "
                + "(0.0000001234567,0.0000005),(0.0000003,0.000000177777),(0.0000002,0.0000009)))",
                "0.000000525925666666666651"},
            {"SELECT REGR_SXX(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES "
                + "(0.0000001234567,0.0000005),(0.0000003,0.000000177777),(0.0000002,0.0000009)))",
                "0.000000000000015675471183"},
            {"SELECT REGR_SYY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES "
                + "(0.0000001234567,0.0000005),(0.0000003,0.000000177777),(0.0000002,0.0000009)))",
                "0.000000000000261811241153"},
            {"SELECT REGR_SXY(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES "
                + "(0.0000001234567,0.0000005),(0.0000003,0.000000177777),(0.0000002,0.0000009)))",
                "0.000000000000072108119415"},
            {"SELECT REGR_SLOPE(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES "
                + "(0.0000001234567,0.0000005),(0.0000003,0.000000177777),(0.0000002,0.0000009)))",
                "-2.094382376228665965101072"},
            {"SELECT REGR_INTERCEPT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT "
                + "column1 x, column2 y FROM VALUES "
                + "(0.0000001234567,0.0000005),(0.0000003,0.000000177777),(0.0000002,0.0000009)))",
                "0.000000961177908273894155"},
            {"SELECT REGR_R2(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 x, "
                + "column2 y FROM VALUES "
                + "(0.0000001234567,0.0000005),(0.0000003,0.000000177777),(0.0000002,0.0000009)))",
                "null"},
            {"SELECT REGR_COUNT(y, x)::NUMBER(38,24)::VARCHAR FROM (SELECT x x, y y FROM (SELECT column1 "
                + "x, column2 y FROM VALUES "
                + "(0.0000001234567,0.0000005),(0.0000003,0.000000177777),(0.0000002,0.0000009)))",
                "3.000000000000000000000000"},
            {"SELECT REGR_SXY(y, x) FROM (SELECT column1 x, column2 y FROM VALUES (12345678901234567890, "
                + "12345678901234567890), (22345678901234567890, 22345678901234567890))",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "4.99329e+38"},
            {"SELECT REGR_R2(y, x) FROM (SELECT column1 x, column2 y FROM VALUES (12345678901234567890, "
                + "12345678901234567890), (22345678901234567890, 22345678901234567890))",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "4.99329e+38"},
            {"SELECT REGR_SXX(y, x)::NUMBER(38,0)::VARCHAR FROM (SELECT column1 x, column2 y FROM VALUES "
                + "(12345678901234567890, 12345678901234567890), (22345678901234567890, 22345678901234567890))",
                "50000000000000140545399397817373425664"},
            {"SELECT LISTAGG(g || ':' || COALESCE(c::VARCHAR, 'n') || ':' || COALESCE(r::VARCHAR, 'n') || "
                + "':' || s::NUMBER(38,24), ' ') WITHIN GROUP (ORDER BY g) FROM (SELECT g, CORR(y, x) c, "
                + "REGR_R2(y, x) r, REGR_SXX(y, x) s FROM (SELECT column1 g, column2 x, column3 y FROM VALUES "
                + "(1, 0.7, 1), (1, 0.7, 2), (2, 0.1, 1), (2, 0.2, 1), (2, 0.4, 1), (3, 0.3, 1), (3, 0.3, 2), "
                + "(3, 0.3, 4)) GROUP BY g)",
                "1:n:n:0.000000000000000000000000 2:n:1:0.046666666666666689611276 "
                + "3:2.389501708e-08:n:0.000000000000000055511151"},
            {"SELECT LISTAGG(COALESCE(r::VARCHAR, 'n') || ':' || s::NUMBER(38,24) || ':' || "
                + "COALESCE(c::NUMBER(38,24)::VARCHAR, 'n'), ' ') WITHIN GROUP (ORDER BY y) FROM (SELECT y, "
                + "REGR_R2(y, x) OVER (PARTITION BY g) r, REGR_SXY(y, x) OVER (PARTITION BY g) s, CORR(y, x) "
                + "OVER (PARTITION BY g) c FROM (SELECT column1 g, column2 x, column3 y FROM VALUES (1, 0.3, "
                + "1), (1, 0.3, 2), (1, 0.3, 4), (2, 0.1, 5), (2, 0.2, 6), (2, 0.4, 8)))",
                "n:0.000000000000000444089210:0.000000023895017079629695 "
                + "n:0.000000000000000444089210:0.000000023895017079629695 "
                + "n:0.000000000000000444089210:0.000000023895017079629695 "
                + "1:0.466666666666666785090456:1.000000000000000222044605 "
                + "1:0.466666666666666785090456:1.000000000000000222044605 "
                + "1:0.466666666666666785090456:1.000000000000000222044605"},
        });
    }
}
