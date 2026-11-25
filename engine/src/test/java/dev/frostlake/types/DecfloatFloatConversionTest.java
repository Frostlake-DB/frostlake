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

package dev.frostlake.types;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * A FLOAT converted to a DECFLOAT: a FLOAT the plan folds to a constant converts at its exact binary value, while
 * one computed as the statement runs - a column, a quotient, a function the plan does not fold - keeps 18
 * significant digits of it, a tie going to the even digit, in a cast, TO_DECFLOAT, an operation or a function over
 * a DECFLOAT, a conditional fold and a write into a DECFLOAT column (live-verified).
 */
public class DecfloatFloatConversionTest extends MeasuredCellsTestSupport {

    @Test
    public void columnsAndWrites() {
        engine.execute("CREATE TABLE tf (i INT, f FLOAT)");
        engine.execute("INSERT INTO tf VALUES (1, 0.1), (2, 1000000000000000.125), (3, 1000000000000000.625), (4, "
            + "-1000000000000000.375), (5, 1e300), (6, 1e-20), (7, 0.5), (8, 0.25)");
        engine.execute("CREATE TABLE td (i INT, d DECFLOAT)");
        engine.execute("INSERT INTO td SELECT i, f FROM tf");
        engine.execute("INSERT INTO td SELECT i + 10, f::DECFLOAT FROM tf");
        engine.execute("INSERT INTO td SELECT 20, 0.1::FLOAT");
        engine.execute("INSERT INTO td VALUES (21, 0.1::FLOAT)");
        engine.execute("INSERT INTO td SELECT 22, 1000000000000000.125::FLOAT");
        assertCells(new String[][] {
            {"SELECT (f::DECFLOAT)::VARCHAR FROM tf WHERE i = 1",
                "0.100000000000000006"},
            {"SELECT (f::DECFLOAT)::VARCHAR FROM tf WHERE i = 2",
                "1000000000000000.12"},
            {"SELECT (f::DECFLOAT)::VARCHAR FROM tf WHERE i = 3",
                "1000000000000000.62"},
            {"SELECT (f::DECFLOAT)::VARCHAR FROM tf WHERE i = 4",
                "-1000000000000000.38"},
            {"SELECT (f::DECFLOAT)::VARCHAR FROM tf WHERE i = 5",
                "1.00000000000000005e300"},
            {"SELECT (f::DECFLOAT)::VARCHAR FROM tf WHERE i = 6",
                "9.99999999999999945e-21"},
            {"SELECT (CAST(f AS DECFLOAT))::VARCHAR FROM tf WHERE i = 1",
                "0.100000000000000006"},
            {"SELECT (TO_DECFLOAT(f))::VARCHAR FROM tf WHERE i = 1",
                "0.100000000000000006"},
            {"SELECT (TO_DECFLOAT(f))::VARCHAR FROM tf WHERE i = 3",
                "1000000000000000.62"},
            {"SELECT (f * 1::DECFLOAT)::VARCHAR FROM tf WHERE i = 1",
                "0.100000000000000006"},
            {"SELECT (1::DECFLOAT * f)::VARCHAR FROM tf WHERE i = 2",
                "1000000000000000.12"},
            {"SELECT (f + 0::DECFLOAT)::VARCHAR FROM tf WHERE i = 6",
                "9.99999999999999945e-21"},
            {"SELECT (f / 1::DECFLOAT)::VARCHAR FROM tf WHERE i = 1",
                "0.100000000000000006"},
            {"SELECT (f = 0.100000000000000006::DECFLOAT)::VARCHAR FROM tf WHERE i = 1",
                "true"},
            {"SELECT (f::DECFLOAT = 0.100000000000000006::DECFLOAT)::VARCHAR FROM tf WHERE i = 1",
                "true"},
            {"SELECT (COALESCE(f, 1::DECFLOAT))::VARCHAR FROM tf WHERE i = 1",
                "0.100000000000000006"},
            {"SELECT (IFF(i > 0, f, 1::DECFLOAT))::VARCHAR FROM tf WHERE i = 1",
                "0.100000000000000006"},
            {"SELECT (POWER(2::DECFLOAT, f))::VARCHAR FROM tf WHERE i = 1",
                "1.0717734625362931686703868475593526974"},
            {"SELECT (MOD(f, 1::DECFLOAT))::VARCHAR FROM tf WHERE i = 1",
                "0.100000000000000006"},
            {"SELECT (DIV0(f, 1::DECFLOAT))::VARCHAR FROM tf WHERE i = 1",
                "0.100000000000000006"},
            {"SELECT ((f * 3)::DECFLOAT)::VARCHAR FROM tf WHERE i = 1",
                "0.300000000000000044"},
            {"SELECT (f::DECFLOAT * 3)::VARCHAR FROM tf WHERE i = 1",
                "0.300000000000000018"},
            {"SELECT (SQRT(f * f + 0::DECFLOAT))::VARCHAR FROM tf WHERE i = 1",
                "1.0000000000000000949999999999999954875e-1"},
            {"SELECT (x::DECFLOAT)::VARCHAR FROM (SELECT f AS x, i FROM tf) WHERE i = 1",
                "0.100000000000000006"},
            {"SELECT (f::DECFLOAT::NUMBER(38,37))::VARCHAR FROM tf WHERE i = 1",
                "0.1000000000000000060000000000000000000"},
            {"SELECT (f::DECFLOAT)::VARCHAR FROM tf WHERE i = 7",
                "0.5"},
            {"SELECT (d)::VARCHAR FROM td WHERE i = 1",
                "0.100000000000000006"},
            {"SELECT (d)::VARCHAR FROM td WHERE i = 2",
                "1000000000000000.12"},
            {"SELECT (d)::VARCHAR FROM td WHERE i = 3",
                "1000000000000000.62"},
            {"SELECT (d)::VARCHAR FROM td WHERE i = 4",
                "-1000000000000000.38"},
            {"SELECT (d)::VARCHAR FROM td WHERE i = 11",
                "0.100000000000000006"},
            {"SELECT (d)::VARCHAR FROM td WHERE i = 12",
                "1000000000000000.12"},
            {"SELECT (d)::VARCHAR FROM td WHERE i = 20",
                "0.100000000000000006"},
            {"SELECT (d)::VARCHAR FROM td WHERE i = 21",
                "0.100000000000000006"},
            {"SELECT (d)::VARCHAR FROM td WHERE i = 22",
                "1000000000000000.12"},
        });
    }

    @Test
    public void foldedConstants() {
        assertCells(new String[][] {
            {"SELECT (0.1::FLOAT::DECFLOAT)::VARCHAR",
                "1.0000000000000000555111512312578270212e-1"},
            {"SELECT ((1/3)::FLOAT::DECFLOAT)::VARCHAR",
                "0.33333299999999999"},
            {"SELECT (1e-20::FLOAT::DECFLOAT)::VARCHAR",
                "9.9999999999999994515327145420957165173e-21"},
            {"SELECT (1e300::FLOAT::DECFLOAT)::VARCHAR",
                "1.0000000000000000525047602552044202487e300"},
            {"SELECT (0.5::FLOAT::DECFLOAT)::VARCHAR",
                "0.5"},
            {"SELECT (1.5::FLOAT::DECFLOAT)::VARCHAR",
                "1.5"},
            {"SELECT (123456789.123::FLOAT::DECFLOAT)::VARCHAR",
                "123456789.1229999959468841552734375"},
            {"SELECT (2::FLOAT::DECFLOAT)::VARCHAR",
                "2"},
            {"SELECT ((-0.0)::FLOAT::DECFLOAT)::VARCHAR",
                "0"},
            {"SELECT ('-0'::FLOAT::DECFLOAT)::VARCHAR",
                "0"},
            {"SELECT (TO_DECFLOAT(0.1::FLOAT))::VARCHAR",
                "1.0000000000000000555111512312578270212e-1"},
            {"SELECT (CAST(0.1::FLOAT AS DECFLOAT))::VARCHAR",
                "1.0000000000000000555111512312578270212e-1"},
            {"SELECT (TO_DECFLOAT(1e300::FLOAT))::VARCHAR",
                "1.0000000000000000525047602552044202487e300"},
            {"SELECT (1.7976931348623157e308::FLOAT::DECFLOAT)::VARCHAR",
                "1.797693134862315708145274237317043568e308"},
            {"SELECT (4.9e-324::FLOAT::DECFLOAT)::VARCHAR",
                "4.9406564584124654417656879286822137237e-324"},
            {"SELECT (2.2250738585072014e-308::FLOAT::DECFLOAT)::VARCHAR",
                "2.2250738585072013830902327173324040642e-308"},
            {"SELECT (9007199254740993::FLOAT::DECFLOAT)::VARCHAR",
                "9007199254740992"},
            {"SELECT (123456789012345678::FLOAT::DECFLOAT)::VARCHAR",
                "123456789012345680"},
            {"SELECT (1e18::FLOAT::DECFLOAT)::VARCHAR",
                "1000000000000000000"},
            {"SELECT (1e17::FLOAT::DECFLOAT)::VARCHAR",
                "100000000000000000"},
            {"SELECT (1e38::FLOAT::DECFLOAT)::VARCHAR",
                "99999999999999997748809823456034029568"},
            {"SELECT (1e39::FLOAT::DECFLOAT)::VARCHAR",
                "9.9999999999999993970916637160317858611e38"},
            {"SELECT (0.3::FLOAT::DECFLOAT)::VARCHAR",
                "2.9999999999999998889776975374843459576e-1"},
            {"SELECT ((0.1::FLOAT + 0.2::FLOAT)::DECFLOAT)::VARCHAR",
                "3.0000000000000004440892098500626161695e-1"},
            {"SELECT (3.14159265358979::FLOAT::DECFLOAT)::VARCHAR",
                "3.1415926535897900073734945181058719754"},
            {"SELECT (PI()::DECFLOAT)::VARCHAR",
                "3.1415926535897931159979634685441851616"},
            {"SELECT (1e-5::FLOAT::DECFLOAT)::VARCHAR",
                "1.0000000000000000818030539140313095459e-5"},
            {"SELECT (1e-7::FLOAT::DECFLOAT)::VARCHAR",
                "9.9999999999999995474811182588625868561e-8"},
            {"SELECT (1e16::FLOAT::DECFLOAT)::VARCHAR",
                "10000000000000000"},
            {"SELECT (12345678901234567890::FLOAT::DECFLOAT)::VARCHAR",
                "12345678901234567168"},
            {"SELECT (0.000123::FLOAT::DECFLOAT)::VARCHAR",
                "1.2300000000000000819830314746639032819e-4"},
            {"SELECT (-2.5e-10::FLOAT::DECFLOAT)::VARCHAR",
                "-2.5000000000000001557039786444496410472e-10"},
            {"SELECT (2.5e-10::FLOAT::DECFLOAT)::VARCHAR",
                "2.5000000000000001557039786444496410472e-10"},
            {"SELECT (1e100::FLOAT::DECFLOAT)::VARCHAR",
                "1.0000000000000000159028911097599180468e100"},
            {"SELECT (1.23e-100::FLOAT::DECFLOAT)::VARCHAR",
                "1.2300000000000000144382694079389499591e-100"},
            {"SELECT (TO_DECFLOAT(1/3::FLOAT))::VARCHAR",
                "0.333333333333333315"},
            {"SELECT (0.1::FLOAT::DECFLOAT * 3)::VARCHAR",
                "3.0000000000000001665334536937734810636e-1"},
            {"SELECT (0.1::FLOAT::DECFLOAT + 0.2::FLOAT::DECFLOAT)::VARCHAR",
                "3.0000000000000001665334536937734810636e-1"},
            {"SELECT (0.1::FLOAT::DECFLOAT - 0.1::DECFLOAT)::VARCHAR",
                "5.55111512312578270212e-18"},
            {"SELECT ((1/3)::FLOAT::DECFLOAT * 3)::VARCHAR",
                "0.99999899999999997"},
            {"SELECT (0.1::FLOAT::DECFLOAT * 10000000000000000000)::VARCHAR",
                "1000000000000000055.5111512312578270212"},
            {"SELECT (0.1::FLOAT::DECFLOAT = 0.1::DECFLOAT)::VARCHAR",
                "false"},
            {"SELECT (0.1::DECFLOAT + 0.1::FLOAT)::VARCHAR",
                "2.0000000000000000555111512312578270212e-1"},
            {"SELECT (0.1::FLOAT + 0.1::DECFLOAT)::VARCHAR",
                "2.0000000000000000555111512312578270212e-1"},
            {"SELECT (SYSTEM$TYPEOF(0.1::FLOAT + 0.1::DECFLOAT))::VARCHAR",
                "DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (0.1::DECFLOAT * 0.1::FLOAT)::VARCHAR",
                "1.0000000000000000555111512312578270212e-2"},
            {"SELECT (1::DECFLOAT / 3::FLOAT)::VARCHAR",
                "3.3333333333333333333333333333333333333e-1"},
            {"SELECT (0.1::FLOAT::DECFLOAT::VARCHAR)::VARCHAR",
                "1.0000000000000000555111512312578270212e-1"},
            {"SELECT (TO_CHAR(0.1::FLOAT::DECFLOAT))::VARCHAR",
                "1.0000000000000000555111512312578270212e-1"},
            {"SELECT (0.1::FLOAT::DECFLOAT::NUMBER(38,37))::VARCHAR",
                "0.1000000000000000055511151231257827021"},
            {"SELECT (1e-20::FLOAT::DECFLOAT * 1e20::DECFLOAT)::VARCHAR",
                "9.9999999999999994515327145420957165173e-1"},
        });
    }

    @Test
    public void computedValues() {
        engine.execute("CREATE TABLE tf (i INT, f FLOAT)");
        engine.execute("INSERT INTO tf VALUES (1, 0.1), (2, 0.333333333333333314829616256247), (3, 1e-20), (4, "
            + "1e300), (5, -0.0), (6, 0.5), (7, 123456789.123), (8, 1.7976931348623157e308), (9, 4.9e-324),"
            + " (10, 12345678901234567890), (11, 1e38), (12, 2.5e-10)");
        engine.execute("CREATE TABLE td (i INT, d DECFLOAT)");
        engine.execute("INSERT INTO td SELECT i, f FROM tf");
        engine.execute("CREATE TABLE td2 (i INT, d DECFLOAT)");
        engine.execute("INSERT INTO td2 SELECT i, f::DECFLOAT FROM tf");
        assertCells(new String[][] {
            {"SELECT ((1::FLOAT/10)::DECFLOAT)::VARCHAR",
                "0.100000000000000006"},
            {"SELECT ((0.1::FLOAT * 1)::DECFLOAT)::VARCHAR",
                "1.0000000000000000555111512312578270212e-1"},
            {"SELECT (SQRT(2::FLOAT)::DECFLOAT)::VARCHAR",
                "1.41421356237309515"},
            {"SELECT (ABS(0.1::FLOAT)::DECFLOAT)::VARCHAR",
                "1.0000000000000000555111512312578270212e-1"},
            {"SELECT ((0.3::FLOAT - 0.2::FLOAT)::DECFLOAT)::VARCHAR",
                "9.9999999999999977795539507496869191527e-2"},
            {"SELECT (column1::DECFLOAT)::VARCHAR FROM VALUES (0.1::FLOAT)",
                "0.100000000000000006"},
            {"SELECT ((1/3)::FLOAT)::VARCHAR",
                "0.333333"},
            {"SELECT ((1/3)::FLOAT::DECFLOAT)::VARCHAR",
                "0.33333299999999999"},
            {"SELECT (0.333333::FLOAT::DECFLOAT)::VARCHAR",
                "0.3333329999999999904147784945962484926"},
            {"SELECT ((2/4)::FLOAT::DECFLOAT)::VARCHAR",
                "0.5"},
            {"SELECT ((1::FLOAT/3)::DECFLOAT)::VARCHAR",
                "0.333333333333333315"},
            {"SELECT ((10::FLOAT/3)::DECFLOAT)::VARCHAR",
                "3.33333333333333348"},
            {"SELECT ((1::FLOAT/3e20)::DECFLOAT)::VARCHAR",
                "3.33333333333333328e-21"},
            {"SELECT ((1e300::FLOAT/3)::DECFLOAT)::VARCHAR",
                "3.33333333333333351e299"},
            {"SELECT ((1::FLOAT/4)::DECFLOAT)::VARCHAR",
                "0.25"},
            {"SELECT (TO_DECFLOAT(1::FLOAT/3, 2))::VARCHAR",
                "REFUSED SQL compilation error: error line 1 at position 8 | too many arguments for function "
                + "[TO_DECFLOAT((TO_DOUBLE(1)) / (TO_DOUBLE(3)), 2)] expected 1, got 2"},
            {"SELECT (EXP(1::FLOAT)::DECFLOAT)::VARCHAR",
                "2.71828182845904509"},
            {"SELECT (LN(2::FLOAT)::DECFLOAT)::VARCHAR",
                "0.693147180559945286"},
            {"SELECT ((1::FLOAT/3) + 0::DECFLOAT)::VARCHAR",
                "0.333333333333333315"},
            {"SELECT ((1::FLOAT/3) * 1::DECFLOAT)::VARCHAR",
                "0.333333333333333315"},
            {"SELECT ((0.1::FLOAT + 0.2::FLOAT)::DECFLOAT)::VARCHAR",
                "3.0000000000000004440892098500626161695e-1"},
            {"SELECT (('0.1'::FLOAT)::DECFLOAT)::VARCHAR",
                "1.0000000000000000555111512312578270212e-1"},
            {"SELECT (TO_DOUBLE('0.1')::DECFLOAT)::VARCHAR",
                "1.0000000000000000555111512312578270212e-1"},
            {"SELECT ((1::FLOAT/3)::DECFLOAT = (1::FLOAT/3)::DECFLOAT)::VARCHAR",
                "true"},
            {"SELECT ((1::FLOAT/-3)::DECFLOAT)::VARCHAR",
                "-0.333333333333333315"},
            {"SELECT ((-0.0::FLOAT/1)::DECFLOAT)::VARCHAR",
                "0"},
            {"SELECT ((1::FLOAT/0.7)::DECFLOAT)::VARCHAR",
                "1.4285714285714286"},
            {"SELECT ((123456789::FLOAT/1000)::DECFLOAT)::VARCHAR",
                "123456.789000000004"},
            {"SELECT ((1::FLOAT/1e10)::DECFLOAT)::VARCHAR",
                "0.000000000100000000000000004"},
            {"SELECT ((2::FLOAT/3e-5)::DECFLOAT)::VARCHAR",
                "66666.6666666666715"},
            {"SELECT (x)::VARCHAR FROM (SELECT (1::FLOAT/3)::DECFLOAT AS x)",
                "0.333333333333333315"},
            {"SELECT (SYSTEM$TYPEOF((1::FLOAT/3)::DECFLOAT))::VARCHAR",
                "DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT ((1::FLOAT/3)::DECFLOAT::NUMBER(38,37))::VARCHAR",
                "0.3333333333333333150000000000000000000"},
            {"SELECT ((1::FLOAT/3)::VARCHAR::DECFLOAT)::VARCHAR",
                "0.3333333333"},
        });
    }

    @Test
    public void foldBoundary() {
        engine.execute("CREATE TABLE tf (i INT, f FLOAT)");
        engine.execute("INSERT INTO tf VALUES (1, 0.1)");
        assertCells(new String[][] {
            {"SELECT ((-(0.1::FLOAT))::DECFLOAT)::VARCHAR",
                "-1.0000000000000000555111512312578270212e-1"},
            {"SELECT (ROUND(0.1::FLOAT, 5)::DECFLOAT)::VARCHAR",
                "0.100000000000000006"},
            {"SELECT (FLOOR(0.1::FLOAT)::DECFLOAT)::VARCHAR",
                "0"},
            {"SELECT (GREATEST(0.1::FLOAT, 0.05::FLOAT)::DECFLOAT)::VARCHAR",
                "1.0000000000000000555111512312578270212e-1"},
            {"SELECT (COALESCE(0.1::FLOAT, 1)::DECFLOAT)::VARCHAR",
                "1.0000000000000000555111512312578270212e-1"},
            {"SELECT (IFF(TRUE, 0.1::FLOAT, 1::FLOAT)::DECFLOAT)::VARCHAR",
                "1.0000000000000000555111512312578270212e-1"},
            {"SELECT ((CASE WHEN 1=1 THEN 0.1::FLOAT END)::DECFLOAT)::VARCHAR",
                "1.0000000000000000555111512312578270212e-1"},
            {"SELECT (NULLIF(0.1::FLOAT, 2)::DECFLOAT)::VARCHAR",
                "1.0000000000000000555111512312578270212e-1"},
            {"SELECT (TO_DOUBLE(0.1)::DECFLOAT)::VARCHAR",
                "1.0000000000000000555111512312578270212e-1"},
            {"SELECT (POWER(0.1::FLOAT, 1)::DECFLOAT)::VARCHAR",
                "0.100000000000000006"},
            {"SELECT (MOD(10.1::FLOAT, 10)::DECFLOAT)::VARCHAR",
                "0.0999999999999996447"},
            {"SELECT ((0.1::FLOAT / 1)::DECFLOAT)::VARCHAR",
                "0.100000000000000006"},
            {"SELECT ((0.1::FLOAT * 2)::DECFLOAT)::VARCHAR",
                "2.0000000000000001110223024625156540424e-1"},
            {"SELECT ((0.1 * 2::FLOAT)::DECFLOAT)::VARCHAR",
                "2.0000000000000001110223024625156540424e-1"},
            {"SELECT ((0.1::FLOAT + 1)::DECFLOAT)::VARCHAR",
                "1.1000000000000000888178419700125232339"},
            {"SELECT (x::DECFLOAT)::VARCHAR FROM (SELECT 0.1::FLOAT AS x FROM tf)",
                "1.0000000000000000555111512312578270212e-1"},
            {"SELECT (0.1::FLOAT::DECFLOAT)::VARCHAR FROM tf",
                "1.0000000000000000555111512312578270212e-1"},
            {"SELECT (0.1::FLOAT::VARCHAR::DECFLOAT)::VARCHAR",
                "0.1"},
            {"SELECT (0.1::FLOAT::NUMBER(10,3)::DECFLOAT)::VARCHAR",
                "0.1"},
            {"SELECT (SQRT(4::FLOAT)::DECFLOAT)::VARCHAR",
                "2"},
            {"SELECT (SIN(1::FLOAT)::DECFLOAT)::VARCHAR",
                "0.841470984807896505"},
            {"SELECT (DIV0(1::FLOAT, 3)::DECFLOAT)::VARCHAR",
                "0.333333333333333315"},
            {"SELECT (TRUNC(0.123456::FLOAT, 3)::DECFLOAT)::VARCHAR",
                "0.122999999999999998"},
            {"SELECT (SIGN(-0.1::FLOAT)::DECFLOAT)::VARCHAR",
                "-1"},
            {"SELECT (ABS(-0.1::FLOAT)::DECFLOAT)::VARCHAR",
                "1.0000000000000000555111512312578270212e-1"},
            {"SELECT (MAX(f)::DECFLOAT)::VARCHAR FROM tf",
                "0.100000000000000006"},
            {"SELECT (SUM(f)::DECFLOAT)::VARCHAR FROM tf",
                "0.100000000000000006"},
            {"SELECT (TO_DECFLOAT('0.1'::FLOAT))::VARCHAR",
                "1.0000000000000000555111512312578270212e-1"},
            {"SELECT (1000000000000000.125::FLOAT::DECFLOAT)::VARCHAR",
                "1000000000000000.125"},
            {"SELECT ((1000000000000000.125::FLOAT / 1)::DECFLOAT)::VARCHAR",
                "1000000000000000.12"},
            {"SELECT ((1000000000000000.625::FLOAT / 1)::DECFLOAT)::VARCHAR",
                "1000000000000000.62"},
            {"SELECT ((1000000000000000.375::FLOAT / 1)::DECFLOAT)::VARCHAR",
                "1000000000000000.38"},
            {"SELECT ((-1000000000000000.125::FLOAT / 1)::DECFLOAT)::VARCHAR",
                "-1000000000000000.12"},
            {"SELECT (ARRAY_CONSTRUCT((0.1::FLOAT / 1)::DECFLOAT))::VARCHAR",
                "[\"0.100000000000000006\"]"},
            {"SELECT (1::VARIANT::DECFLOAT)::VARCHAR",
                "1"},
            {"SELECT (PARSE_JSON('0.1')::DECFLOAT)::VARCHAR",
                "0.1"},
        });
    }

    @Test
    public void variablesAndWrites() {
        engine.execute("CREATE TABLE tf (i INT, f FLOAT)");
        engine.execute("INSERT INTO tf VALUES (1, 0.1), (2, 1000000000000000.125), (3, 1000000000000000.625), (4, "
            + "-1000000000000000.375)");
        engine.execute("SET v = 0.1::FLOAT");
        engine.execute("CREATE TABLE td (i INT, d DECFLOAT)");
        engine.execute("INSERT INTO td SELECT i, f FROM tf");
        engine.execute("INSERT INTO td SELECT 10, 0.1::FLOAT");
        engine.execute("INSERT INTO td VALUES (11, 0.1::FLOAT)");
        engine.execute("INSERT INTO td SELECT 12, 1000000000000000.125::FLOAT");
        assertCells(new String[][] {
            {"SELECT (LEAST(0.1::FLOAT, 1)::DECFLOAT)::VARCHAR",
                "1.0000000000000000555111512312578270212e-1"},
            {"SELECT (NVL(0.1::FLOAT, 1)::DECFLOAT)::VARCHAR",
                "1.0000000000000000555111512312578270212e-1"},
            {"SELECT (NVL2(1, 0.1::FLOAT, 1)::DECFLOAT)::VARCHAR",
                "1.0000000000000000555111512312578270212e-1"},
            {"SELECT (ZEROIFNULL(0.1::FLOAT)::DECFLOAT)::VARCHAR",
                "1.0000000000000000555111512312578270212e-1"},
            {"SELECT (DECODE(1, 1, 0.1::FLOAT, 2::FLOAT)::DECFLOAT)::VARCHAR",
                "1.0000000000000000555111512312578270212e-1"},
            {"SELECT (SQUARE(0.1::FLOAT)::DECFLOAT)::VARCHAR",
                "0.0100000000000000019"},
            {"SELECT (('0.1'::FLOAT * '3')::DECFLOAT)::VARCHAR",
                "3.0000000000000004440892098500626161695e-1"},
            {"SELECT (TO_NUMBER(0.1::FLOAT, 10, 3)::FLOAT::DECFLOAT)::VARCHAR",
                "1.0000000000000000555111512312578270212e-1"},
            {"SELECT (CEIL(0.1::FLOAT, 2)::DECFLOAT)::VARCHAR",
                "0.100000000000000006"},
            {"SELECT ((0.1::FLOAT - -0.2::FLOAT)::DECFLOAT)::VARCHAR",
                "3.0000000000000004440892098500626161695e-1"},
            {"SELECT ((0 - 0.1::FLOAT)::DECFLOAT)::VARCHAR",
                "-1.0000000000000000555111512312578270212e-1"},
            {"SELECT ((-(0.2::FLOAT))::DECFLOAT)::VARCHAR",
                "-2.0000000000000001110223024625156540424e-1"},
            {"SELECT ((-(1e-20::FLOAT))::DECFLOAT)::VARCHAR",
                "-9.9999999999999994515327145420957165173e-21"},
            {"SELECT ((-(1e300::FLOAT))::DECFLOAT)::VARCHAR",
                "-1.0000000000000000525047602552044202487e300"},
            {"SELECT ((-(0.5::FLOAT))::DECFLOAT)::VARCHAR",
                "-0.5"},
            {"SELECT ((-(123456789.123::FLOAT))::DECFLOAT)::VARCHAR",
                "-123456789.1229999959468841552734375"},
            {"SELECT ((-(1::FLOAT/3))::DECFLOAT)::VARCHAR",
                "-0.333333333333333315"},
            {"SELECT (ABS(-(0.1::FLOAT))::DECFLOAT)::VARCHAR",
                "1.0000000000000000555111512312578270212e-1"},
            {"SELECT ((-ABS(0.1::FLOAT))::DECFLOAT)::VARCHAR",
                "-1.0000000000000000555111512312578270212e-1"},
            {"SELECT ($v::DECFLOAT)::VARCHAR",
                "1.0000000000000000555111512312578270212e-1"},
            {"EXECUTE IMMEDIATE $$DECLARE x FLOAT := 0.1; BEGIN RETURN (x::DECFLOAT)::VARCHAR; END;$$",
                "0.100000000000000006"},
            {"EXECUTE IMMEDIATE $$DECLARE x FLOAT := 1/3; BEGIN RETURN (x::DECFLOAT)::VARCHAR; END;$$",
                "0.33333299999999999"},
            {"EXECUTE IMMEDIATE $$DECLARE x DECFLOAT; BEGIN x := 0.1::FLOAT; RETURN (x)::VARCHAR; END;$$",
                "1.0000000000000000555111512312578270212e-1"},
        });
    }

    @Test
    public void decfloatOperands() {
        engine.execute("CREATE TABLE tf (i INT, f FLOAT)");
        engine.execute("INSERT INTO tf VALUES (1, 0.1), (2, 0.25)");
        assertCells(new String[][] {
            {"SELECT (POWER(2::DECFLOAT, 0.1::FLOAT))::VARCHAR",
                "1.0717734625362931683369117297193341112"},
            {"SELECT (COALESCE(0.1::FLOAT, 1::DECFLOAT))::VARCHAR",
                "1.0000000000000000555111512312578270212e-1"},
            {"SELECT (IFF(TRUE, 0.1::FLOAT, 1::DECFLOAT))::VARCHAR",
                "1.0000000000000000555111512312578270212e-1"},
            {"SELECT (0.1::FLOAT = 0.100000000000000006::DECFLOAT)::VARCHAR",
                "false"},
        });
    }
}
