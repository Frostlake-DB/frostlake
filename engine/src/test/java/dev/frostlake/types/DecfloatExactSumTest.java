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
 * DECFLOAT SUM and AVG, and the pair statistics planned over DECFLOAT SUMs (COVAR_POP, COVAR_SAMP, CORR and the REGR_
 * family): a sum adds its values exactly whatever their magnitudes and is held to 38 significant digits once, a total
 * passing DECFLOAT's range at any step is "Value overflow in a SUM aggregate", and AVG is the held sum over the count
 * (live-verified).
 */
public class DecfloatExactSumTest extends MeasuredCellsTestSupport {

    @Test
    public void pairStatistics() {
        engine.execute("CREATE OR REPLACE TABLE ta (id INT, y DECFLOAT, x DECFLOAT)");
        engine.execute("INSERT INTO ta VALUES (1, '1'::DECFLOAT, '1e38'::DECFLOAT), (2, '2'::DECFLOAT, "
            + "'1'::DECFLOAT), (3, '3'::DECFLOAT, '-1e38'::DECFLOAT)");
        engine.execute("CREATE OR REPLACE TABLE tb (id INT, y DECFLOAT, x DECFLOAT)");
        engine.execute("INSERT INTO tb VALUES (1, '1'::DECFLOAT, '1e40'::DECFLOAT), (2, '1'::DECFLOAT, "
            + "'1'::DECFLOAT), (3, '1'::DECFLOAT, '-1e40'::DECFLOAT), (4, '2'::DECFLOAT, '1'::DECFLOAT)");
        engine.execute("CREATE OR REPLACE TABLE tc (id INT, y DECFLOAT, x DECFLOAT)");
        engine.execute("INSERT INTO tc VALUES (1, '1e20'::DECFLOAT, '0.1'::DECFLOAT), (2, '7'::DECFLOAT, "
            + "'1e30'::DECFLOAT), (3, '0.5'::DECFLOAT, '3'::DECFLOAT), (4, '1e20'::DECFLOAT, "
            + "'-1e30'::DECFLOAT), (5, '-2'::DECFLOAT, '5'::DECFLOAT)");
        engine.execute("CREATE OR REPLACE TABLE td (id INT, y DECFLOAT, x DECFLOAT)");
        engine.execute("INSERT INTO td VALUES (1, '123456789012345678901234567890.123'::DECFLOAT, "
            + "'1.000000000000000000001'::DECFLOAT), (2, '-123456789012345678901234567890.12'::DECFLOAT, "
            + "'1'::DECFLOAT), (3, '0.0000001'::DECFLOAT, '2.5'::DECFLOAT), (4, '9'::DECFLOAT, "
            + "'1e-20'::DECFLOAT)");
        engine.execute("CREATE OR REPLACE TABLE te (id INT, y DECFLOAT, x DECFLOAT)");
        engine.execute("INSERT INTO te VALUES (1, '1e30'::DECFLOAT, '1e30'::DECFLOAT), (2, '1'::DECFLOAT, "
            + "'1'::DECFLOAT), (3, '-1e30'::DECFLOAT, '-1e30'::DECFLOAT), (4, '2'::DECFLOAT, '3'::DECFLOAT)");
        engine.execute("CREATE OR REPLACE TABLE tf (id INT, y DECFLOAT, x DECFLOAT)");
        engine.execute("INSERT INTO tf VALUES (1, '5'::DECFLOAT, '1e-30'::DECFLOAT), (2, '1e10'::DECFLOAT, "
            + "'2e-30'::DECFLOAT), (3, '3'::DECFLOAT, '1e25'::DECFLOAT), (4, '-1e10'::DECFLOAT, "
            + "'-1e25'::DECFLOAT), (5, '4'::DECFLOAT, '7'::DECFLOAT)");
        assertCells(new String[][] {
            {"SELECT COVAR_POP(y, x) FROM ta",
                "-66666666666666666666666666666666666667"},
            {"SELECT COVAR_SAMP(y, x) FROM ta",
                "-1E+38"},
            {"SELECT CORR(y, x) FROM ta",
                "-1"},
            {"SELECT REGR_SLOPE(y, x) FROM ta",
                "-1E-38"},
            {"SELECT REGR_INTERCEPT(y, x) FROM ta",
                "2"},
            {"SELECT REGR_R2(y, x) FROM ta",
                "1"},
            {"SELECT REGR_SXX(y, x) FROM ta",
                "2E+76"},
            {"SELECT REGR_SYY(y, x) FROM ta",
                "2"},
            {"SELECT REGR_SXY(y, x) FROM ta",
                "-2E+38"},
            {"SELECT REGR_AVGX(y, x) FROM ta",
                "0.33333333333333333333333333333333333333"},
            {"SELECT REGR_AVGY(y, x) FROM ta",
                "2"},
            {"SELECT REGR_COUNT(y, x) FROM ta",
                "3"},
            {"SELECT COVAR_POP(y, x) FROM tb",
                "0.125"},
            {"SELECT COVAR_SAMP(y, x) FROM tb",
                "0.16666666666666666666666666666666666667"},
            {"SELECT CORR(y, x) FROM tb",
                "4.0824829046386301636621401245098189866E-41"},
            {"SELECT REGR_SLOPE(y, x) FROM tb",
                "2.5E-81"},
            {"SELECT REGR_INTERCEPT(y, x) FROM tb",
                "1.25"},
            {"SELECT REGR_R2(y, x) FROM tb",
                "1.6666666666666666666666666666666666667E-81"},
            {"SELECT REGR_SXX(y, x) FROM tb",
                "2E+80"},
            {"SELECT REGR_SYY(y, x) FROM tb",
                "0.75"},
            {"SELECT REGR_SXY(y, x) FROM tb",
                "0.5"},
            {"SELECT REGR_AVGX(y, x) FROM tb",
                "0.5"},
            {"SELECT REGR_AVGY(y, x) FROM tb",
                "1.25"},
            {"SELECT REGR_COUNT(y, x) FROM tb",
                "4"},
            {"SELECT COVAR_POP(y, x) FROM tc",
                "-1.99999999999999999986000000000628E+49"},
            {"SELECT COVAR_SAMP(y, x) FROM tc",
                "-2.49999999999999999982500000000785E+49"},
            {"SELECT CORR(y, x) FROM tc",
                "-0.64549722436790281416319354337341848436"},
            {"SELECT REGR_SLOPE(y, x) FROM tc",
                "-4.9999999999999999996500000000157E-11"},
            {"SELECT REGR_INTERCEPT(y, x) FROM tc",
                "40000000000000000001.100000000081"},
            {"SELECT REGR_R2(y, x) FROM tc",
                "0.41666666666666666662361111111372777778"},
            {"SELECT REGR_SXX(y, x) FROM tc",
                "2E+60"},
            {"SELECT REGR_SYY(y, x) FROM tc",
                "1.199999999999999999956E+40"},
            {"SELECT REGR_SXY(y, x) FROM tc",
                "-9.9999999999999999993000000000314E+49"},
            {"SELECT REGR_AVGX(y, x) FROM tc",
                "1.62"},
            {"SELECT REGR_AVGY(y, x) FROM tc",
                "40000000000000000001.1"},
            {"SELECT REGR_COUNT(y, x) FROM tc",
                "5"},
            {"SELECT COVAR_POP(y, x) FROM td",
                "30864194.721742704375000000016310437433"},
            {"SELECT COVAR_SAMP(y, x) FROM td",
                "41152259.62899027250000000002174724991"},
            {"SELECT CORR(y, x) FROM td",
                "3.9605898470767669933692366873951030211E-22"},
            {"SELECT REGR_SLOPE(y, x) FROM td",
                "38731538.474343785882629378059775081054"},
            {"SELECT REGR_INTERCEPT(y, x) FROM td",
                "-43572978.532886734118064562048051411597"},
            {"SELECT REGR_R2(y, x) FROM td",
                "1.5686271936767568558051723463744868235E-43"},
            {"SELECT REGR_SXX(y, x) FROM td",
                "3.18749999999999999997725"},
            {"SELECT REGR_SYY(y, x) FROM td",
                "3.0483157506477673500990703125132397575E+58"},
            {"SELECT REGR_SXY(y, x) FROM td",
                "123456778.88697081750000000006524174973"},
            {"SELECT REGR_AVGX(y, x) FROM td",
                "1.12500000000000000000275"},
            {"SELECT REGR_AVGY(y, x) FROM td",
                "2.250750025"},
            {"SELECT REGR_COUNT(y, x) FROM td",
                "4"},
            {"SELECT COVAR_POP(y, x) FROM te",
                "5E+59"},
            {"SELECT COVAR_SAMP(y, x) FROM te",
                "6.6666666666666666666666666666666666667E+59"},
            {"SELECT CORR(y, x) FROM te",
                "1"},
            {"SELECT REGR_SLOPE(y, x) FROM te",
                "1"},
            {"SELECT REGR_INTERCEPT(y, x) FROM te",
                "-0.25"},
            {"SELECT REGR_R2(y, x) FROM te",
                "1"},
            {"SELECT REGR_SXX(y, x) FROM te",
                "2E+60"},
            {"SELECT REGR_SYY(y, x) FROM te",
                "2E+60"},
            {"SELECT REGR_SXY(y, x) FROM te",
                "2E+60"},
            {"SELECT REGR_AVGX(y, x) FROM te",
                "1"},
            {"SELECT REGR_AVGY(y, x) FROM te",
                "0.75"},
            {"SELECT REGR_COUNT(y, x) FROM te",
                "4"},
            {"SELECT COVAR_POP(y, x) FROM tf",
                "20000000006000000000000000000000002.24"},
            {"SELECT COVAR_SAMP(y, x) FROM tf",
                "25000000007500000000000000000000002.8"},
            {"SELECT CORR(y, x) FROM tf",
                "0.500000000149999999973499999992050056"},
            {"SELECT REGR_SLOPE(y, x) FROM tf",
                "5.00000000150000000000000000000000056E-16"},
            {"SELECT REGR_INTERCEPT(y, x) FROM tf",
                "2.39999999999999929999999979"},
            {"SELECT REGR_R2(y, x) FROM tf",
                "0.250000000149999999995999999984100056"},
            {"SELECT REGR_SXX(y, x) FROM tf",
                "2E+50"},
            {"SELECT REGR_SYY(y, x) FROM tf",
                "200000000000000000021.2"},
            {"SELECT REGR_SXY(y, x) FROM tf",
                "100000000030000000000000000000000011.2"},
            {"SELECT REGR_AVGX(y, x) FROM tf",
                "1.4000000000000000000000000000006"},
            {"SELECT REGR_AVGY(y, x) FROM tf",
                "2.4"},
            {"SELECT REGR_COUNT(y, x) FROM tf",
                "5"},
        });
    }

    @Test
    public void sumAndAverage() {
        assertCells(new String[][] {
            {"SELECT SUM(v) FROM (SELECT column1::DECFLOAT v FROM VALUES ('9e16384'), ('9e16384'), "
                + "('-9e16384'))",
                "9E+16384"},
            {"SELECT SUM(v) FROM (SELECT column1::DECFLOAT v FROM VALUES ('9e16384'), ('9e16384'))",
                "1.8E+16385"},
            {"SELECT SUM(v) FROM (SELECT column1::DECFLOAT v FROM VALUES ('1e38'), ('1'), ('-1e38'))",
                "1"},
            {"SELECT SUM(v) FROM (SELECT column1::DECFLOAT v FROM VALUES "
                + "('12345678901234567890123456789012345678'), ('0.5'), ('0.5'))",
                "12345678901234567890123456789012345679"},
            {"SELECT SUM(v) FROM (SELECT column1::DECFLOAT v FROM VALUES "
                + "('12345678901234567890123456789012345678'), ('0.4'), ('0.4'))",
                "12345678901234567890123456789012345679"},
            {"SELECT SUM(v) FROM (SELECT column1::DECFLOAT v FROM VALUES "
                + "('12345678901234567890123456789012345678'), ('0.5'))",
                "12345678901234567890123456789012345679"},
            {"SELECT SUM(v) FROM (SELECT column1::DECFLOAT v FROM VALUES "
                + "('12345678901234567890123456789012345675'), ('0.5'))",
                "12345678901234567890123456789012345676"},
            {"SELECT SUM(v) FROM (SELECT column1::DECFLOAT v FROM VALUES "
                + "('-12345678901234567890123456789012345675'), ('-0.5'))",
                "-12345678901234567890123456789012345676"},
            {"SELECT SUM(v) FROM (SELECT column1::DECFLOAT v FROM VALUES ('1e16384'), ('9.9e16384'), "
                + "('-9.9e16384'))",
                "1E+16384"},
            {"SELECT SUM(v) FROM (SELECT column1::DECFLOAT v FROM VALUES "
                + "('99999999999999999999999999999999999999e16384'), "
                + "('99999999999999999999999999999999999999e16384'), "
                + "('-99999999999999999999999999999999999999e16384'))",
                "REFUSED Value overflow in a SUM aggregate"},
            {"SELECT SUM(v) FROM (SELECT column1::DECFLOAT v FROM VALUES "
                + "('99999999999999999999999999999999999999e16384'), "
                + "('99999999999999999999999999999999999999e16384'))",
                "REFUSED Value overflow in a SUM aggregate"},
            {"SELECT AVG(v) FROM (SELECT column1::DECFLOAT v FROM VALUES "
                + "('99999999999999999999999999999999999999e16384'), "
                + "('99999999999999999999999999999999999999e16384'))",
                "REFUSED Value overflow in a SUM aggregate"},
            {"SELECT AVG(v) FROM (SELECT column1::DECFLOAT v FROM VALUES "
                + "('99999999999999999999999999999999999999e16384'), "
                + "('99999999999999999999999999999999999999e16384'), "
                + "('-99999999999999999999999999999999999999e16384'))",
                "REFUSED Value overflow in a SUM aggregate"},
            {"SELECT COVAR_POP(v, w) FROM (SELECT column1::DECFLOAT v, 1::DECFLOAT w FROM VALUES "
                + "('99999999999999999999999999999999999999e16384'), "
                + "('99999999999999999999999999999999999999e16384'), "
                + "('-99999999999999999999999999999999999999e16384'))",
                "REFUSED Value overflow in a SUM aggregate"},
            {"SELECT REGR_AVGX(w, v) FROM (SELECT column1::DECFLOAT v, 1::DECFLOAT w FROM VALUES "
                + "('99999999999999999999999999999999999999e16384'), "
                + "('99999999999999999999999999999999999999e16384'))",
                "REFUSED Value overflow in a SUM aggregate"},
            {"SELECT DISTINCT SUM(v) OVER () FROM (SELECT column1::DECFLOAT v FROM VALUES "
                + "('99999999999999999999999999999999999999e16384'), "
                + "('99999999999999999999999999999999999999e16384'), "
                + "('-99999999999999999999999999999999999999e16384'))",
                "REFUSED Value overflow in a SUM aggregate"},
            {"SELECT SUM(v) FROM (SELECT column1::DECFLOAT v FROM VALUES "
                + "('99999999999999999999999999999999999999e16384'), ('1'))",
                "9.9999999999999999999999999999999999999E+16421"},
            {"SELECT VAR_POP(v) FROM (SELECT column1::DECFLOAT v FROM VALUES "
                + "('99999999999999999999999999999999999999e16384'), "
                + "('99999999999999999999999999999999999999e16384'), "
                + "('-99999999999999999999999999999999999999e16384'))",
                "REFUSED Decfloat out of representable range, operation: "
                + "99999999999999999999999999999999999999e16384 * 99999999999999999999999999999999999999e16384"},
            {"SELECT SKEW(v) FROM (SELECT column1::DECFLOAT v FROM VALUES "
                + "('99999999999999999999999999999999999999e16384'), "
                + "('99999999999999999999999999999999999999e16384'), "
                + "('-99999999999999999999999999999999999999e16384'))",
                "REFUSED Decfloat out of representable range, operation: "
                + "99999999999999999999999999999999999999e16384 * 99999999999999999999999999999999999999e16384"},
            {"SELECT k, COVAR_POP(v, w) OVER (ORDER BY k), REGR_AVGX(w, v) OVER (ORDER BY k) FROM (SELECT "
                + "column1 k, column2::DECFLOAT v, 1::DECFLOAT w FROM VALUES (1, '1e100'), (2, '1'), (3, "
                + "'-1e100'), (4, '1e-50')) ORDER BY k",
                "REFUSED SQL compilation error: error line 1 at position 26 | Cumulative window frame "
                + "unsupported for function COVAR_POP"},
            {"SELECT AVG(v) FROM (SELECT column1::DECFLOAT v FROM VALUES "
                + "('12345678901234567890123456789012345678'), ('0.4'), ('0.4'))",
                "4115226300411522630041152263004115226.3"},
            {"SELECT AVG(v) FROM (SELECT column1::DECFLOAT v FROM VALUES ('1'), "
                + "('0.00000000000000000000000000000000000001'), ('1e-50'))",
                "0.33333333333333333333333333333333333333"},
        });
    }
}
