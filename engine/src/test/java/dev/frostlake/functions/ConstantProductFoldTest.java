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
 * A product of two constants — literals and casts of them, or columns the table's statistics pin to one value and
 * no NULL — is folded by the planner and converted through its text to the type the product declares
 * (live-verified): a raw past the 128-bit carrier is "Numeric value '…' is not recognized", one past 38 digits that
 * the carrier holds "Numeric value '…' is out of range". A product of more than 38 digits, a product of whole
 * numbers, and a product over a column that holds a NULL or a second value keep the row's own arithmetic. The
 * variance family folds the square of a pinned argument alike.
 */
public class ConstantProductFoldTest extends MeasuredCellsTestSupport {

    @Test
    public void productsOfConstants() {
        engine.execute("CREATE OR REPLACE TABLE t8 (c NUMBER(38,3), d INT) AS SELECT column1::NUMBER(38,3), column2 "
            + "FROM VALUES (13100000000000000.8, 1), (13100000000000000.8, 2)");
        engine.execute("CREATE OR REPLACE TABLE t9 (c NUMBER(38,3), d INT) AS SELECT column1::NUMBER(38,3), column2 "
            + "FROM VALUES (13100000000000000.000, 1)");
        engine.execute("CREATE OR REPLACE TABLE t10 (c NUMBER(38,0)) AS SELECT 13100000000000000000");
        assertCells(new String[][] {
            {"SELECT 1.3e13::NUMBER(38,7) * 1.3e13::NUMBER(38,7)",
                "REFUSED Numeric value '169000000000000000000000000' is out of range"},
            {"SELECT 1.31e13::NUMBER(38,7) * 1.31e13::NUMBER(38,7)",
                "REFUSED Numeric value '171610000000000000000000000' is not recognized"},
            {"SELECT 1e16::NUMBER(38,3) * 1e16::NUMBER(38,3)",
                "REFUSED Numeric value '100000000000000000000000000000000' is out of range"},
            {"SELECT 1.3e16::NUMBER(38,3) * 1.3e16::NUMBER(38,3)",
                "REFUSED Numeric value '169000000000000000000000000000000' is out of range"},
            {"SELECT 1.31e16::NUMBER(38,3) * 1.31e16::NUMBER(38,3)",
                "REFUSED Numeric value '171610000000000000000000000000000' is not recognized"},
            {"SELECT 1.3e19::NUMBER(38,0) * 1.3e19::NUMBER(38,0)",
                "169000000000000000000000000000000000000"},
            {"SELECT 1.31e19::NUMBER(38,0) * 1.31e19::NUMBER(38,0)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+38"},
            {"SELECT -1.31e16::NUMBER(38,3) * 1.31e16::NUMBER(38,3)",
                "REFUSED Numeric value '-171610000000000000000000000000000' is not recognized"},
            {"SELECT 1304381782533.2781234::NUMBER(38,7) * 1304381782533.2781234::NUMBER(38,7)",
                "1701411834604692062088601.354497025628"},
            {"SELECT 1.3e18::NUMBER(38,1) * 1.3e18::NUMBER(38,1)",
                "REFUSED Numeric value '1690000000000000000000000000000000000' is out of range"},
            {"SELECT 1.31e18::NUMBER(38,1) * 1.31e18::NUMBER(38,1)",
                "REFUSED Numeric value '1716100000000000000000000000000000000' is not recognized"},
            {"SELECT 1e12::NUMBER(38,13) * 1e12::NUMBER(38,13)",
                "1000000000000000000000000.0000000000000"},
            {"SELECT 5e12::NUMBER(38,13) * 5e12::NUMBER(38,13)",
                "REFUSED Numeric value '25000000000000000000000000' is not recognized"},
            {"SELECT 1e17::NUMBER(38,3) * 1e17",
                "10000000000000000000000000000000000.000"},
            {"SELECT 1e17::NUMBER(38,3) * 1e17::NUMBER(38,0)",
                "10000000000000000000000000000000000.000"},
            {"SELECT 1e17::NUMBER(38,3) * 2e19::NUMBER(38,0)",
                "REFUSED Numeric value '2000000000000000000000000000000000000' is not recognized"},
            {"SELECT 123456789012345678.123::NUMBER(38,3) * 123456789012345678.123::NUMBER(38,3)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.52416e+40"},
            {"SELECT 12345678901234.1234567::NUMBER(38,7) * 12345678901234.1234567::NUMBER(38,7)",
                "152415787532377393840275567.966377156775"},
            {"SELECT 1e17::NUMBER(38,3) * 1e17::NUMBER(38,3) FROM VALUES (1), (2)",
                "REFUSED Numeric value '10000000000000000000000000000000000' is not recognized"},
            {"SELECT VAR_POP(1e17::NUMBER(38,3)) FROM VALUES (1), (2)",
                "REFUSED Numeric value '10000000000000000000000000000000000' is not recognized"},
            {"SELECT 1e17::NUMBER(20,3) * 1e17::NUMBER(20,3)",
                "REFUSED Number out of representable range: type FIXED[SB16](20,3){not null}, value "
                + "100000000000000000"},
            {"SELECT 9.9e16::NUMBER(38,2) * 9.9e16::NUMBER(38,2)",
                "9801000000000000000000000000000000.0000"},
            {"SELECT 1.31e13 * 1.31e13",
                "171610000000000000000000000"},
            {"SELECT 1.31e13::NUMBER(38,7) * 1.31e13::NUMBER(38,7) * 1",
                "REFUSED Numeric value '171610000000000000000000000' is not recognized"},
            {"SELECT 12345678901234567.891::NUMBER(38,3) * 10000000000000000::NUMBER(38,3)",
                "REFUSED Numeric value '123456789012345678910000000000000.000' is out of range"},
            {"SELECT 12345678901234567.891::NUMBER(38,3) * 10000000000000001::NUMBER(38,3)",
                "REFUSED Numeric value '123456789012345691255678901234567.891' is out of range"},
            {"SELECT 12345678901234567.891::NUMBER(38,3) * 10000000000100000::NUMBER(38,3)",
                "REFUSED Numeric value '123456789013580246800123456789100.000' is out of range"},
            {"SELECT 12345678901234567.891::NUMBER(38,3) * 10000000100000000::NUMBER(38,3)",
                "REFUSED Numeric value '123456790246913569033456789100000.000' is out of range"},
            {"SELECT 12345678901234567.891::NUMBER(38,3) * 10000100000000000::NUMBER(38,3)",
                "REFUSED Numeric value '123458023580235802366789100000000.000' is out of range"},
            {"SELECT 12345678901234567.891::NUMBER(38,3) * 11000000000000000::NUMBER(38,3)",
                "REFUSED Numeric value '135802467913580246801000000000000.000' is out of range"},
            {"SELECT 12345678901234567.891::NUMBER(38,3) * 10000000000000000.001::NUMBER(38,3)",
                "123456789012345678922345678901234.567891"},
            {"SELECT 12345678901234567.891::NUMBER(38,3) * 10000000000000000.01::NUMBER(38,3)",
                "REFUSED Numeric value '123456789012345679033456789012345.67891' is out of range"},
            {"SELECT 12345678901234567.891::NUMBER(38,3) * 10000000000000000.1::NUMBER(38,3)",
                "REFUSED Numeric value '123456789012345680144567890123456.7891' is out of range"},
            {"SELECT 12345678901234567.891::NUMBER(38,3) * 10000000000000001.1::NUMBER(38,3)",
                "REFUSED Numeric value '123456789012345692490246791358024.6801' is out of range"},
            {"SELECT 12345678901234567.891::NUMBER(38,3) * 10000000000000010.001::NUMBER(38,3)",
                "123456789012345802379134691246913.477891"},
            {"SELECT 12345678901234567.891::NUMBER(38,3) * 12345678901234567.891::NUMBER(38,3)",
                "152415787532388367526596557677488.187881"},
            {"SELECT 12345678901234567.891::NUMBER(38,3) * 10000000000000000.000::NUMBER(38,3)",
                "REFUSED Numeric value '123456789012345678910000000000000.000' is out of range"},
            {"SELECT 17145678901234567.891::NUMBER(38,3) * 10000000000000000::NUMBER(38,3)",
                "REFUSED Numeric value '171456789012345678910000000000000.000' is not recognized"},
            {"SELECT 17145678901234567.891::NUMBER(38,3) * 10000000000000001::NUMBER(38,3)",
                "REFUSED Numeric value '171456789012345696055678901234567.891' is not recognized"},
            {"SELECT 17145678901234567.891::NUMBER(38,3) * 10000000000100000::NUMBER(38,3)",
                "REFUSED Numeric value '171456789014060246800123456789100.000' is not recognized"},
            {"SELECT 17145678901234567.891::NUMBER(38,3) * 10000000100000000::NUMBER(38,3)",
                "REFUSED Numeric value '171456790726913569033456789100000.000' is not recognized"},
            {"SELECT 17145678901234567.891::NUMBER(38,3) * 10000100000000000::NUMBER(38,3)",
                "REFUSED Numeric value '171458503580235802366789100000000.000' is not recognized"},
            {"SELECT 17145678901234567.891::NUMBER(38,3) * 11000000000000000::NUMBER(38,3)",
                "REFUSED Numeric value '188602467913580246801000000000000.000' is not recognized"},
            {"SELECT 17145678901234567.891::NUMBER(38,3) * 10000000000000000.001::NUMBER(38,3)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.71457e+38"},
            {"SELECT 17145678901234567.891::NUMBER(38,3) * 10000000000000000.01::NUMBER(38,3)",
                "REFUSED Numeric value '171456789012345679081456789012345.67891' is not recognized"},
            {"SELECT 17145678901234567.891::NUMBER(38,3) * 10000000000000000.1::NUMBER(38,3)",
                "REFUSED Numeric value '171456789012345680624567890123456.7891' is not recognized"},
            {"SELECT 17145678901234567.891::NUMBER(38,3) * 10000000000000001.1::NUMBER(38,3)",
                "REFUSED Numeric value '171456789012345697770246791358024.6801' is not recognized"},
            {"SELECT 17145678901234567.891::NUMBER(38,3) * 10000000000000010.001::NUMBER(38,3)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.71457e+38"},
            {"SELECT 17145678901234567.891::NUMBER(38,3) * 12345678901234567.891::NUMBER(38,3)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "2.11675e+38"},
            {"SELECT 17145678901234567.891::NUMBER(38,3) * 10000000000000000.000::NUMBER(38,3)",
                "REFUSED Numeric value '171456789012345678910000000000000.000' is not recognized"},
            {"SELECT 10000000000000000::NUMBER(38,3) * 10000000000000000::NUMBER(38,3)",
                "REFUSED Numeric value '100000000000000000000000000000000' is out of range"},
            {"SELECT 10000000000000000::NUMBER(38,3) * 10000000000000001::NUMBER(38,3)",
                "REFUSED Numeric value '100000000000000010000000000000000' is out of range"},
            {"SELECT 10000000000000000::NUMBER(38,3) * 10000000000100000::NUMBER(38,3)",
                "REFUSED Numeric value '100000000001000000000000000000000' is out of range"},
            {"SELECT 10000000000000000::NUMBER(38,3) * 10000000100000000::NUMBER(38,3)",
                "REFUSED Numeric value '100000001000000000000000000000000' is out of range"},
            {"SELECT 10000000000000000::NUMBER(38,3) * 10000100000000000::NUMBER(38,3)",
                "REFUSED Numeric value '100001000000000000000000000000000' is out of range"},
            {"SELECT 10000000000000000::NUMBER(38,3) * 11000000000000000::NUMBER(38,3)",
                "REFUSED Numeric value '110000000000000000000000000000000' is out of range"},
            {"SELECT 10000000000000000::NUMBER(38,3) * 10000000000000000.001::NUMBER(38,3)",
                "REFUSED Numeric value '100000000000000000010000000000000.000' is out of range"},
            {"SELECT 10000000000000000::NUMBER(38,3) * 10000000000000000.01::NUMBER(38,3)",
                "REFUSED Numeric value '100000000000000000100000000000000.00' is out of range"},
            {"SELECT 10000000000000000::NUMBER(38,3) * 10000000000000000.1::NUMBER(38,3)",
                "REFUSED Numeric value '100000000000000001000000000000000.0' is out of range"},
            {"SELECT 10000000000000000::NUMBER(38,3) * 10000000000000001.1::NUMBER(38,3)",
                "REFUSED Numeric value '100000000000000011000000000000000.0' is out of range"},
            {"SELECT 10000000000000000::NUMBER(38,3) * 10000000000000010.001::NUMBER(38,3)",
                "REFUSED Numeric value '100000000000000100010000000000000.000' is out of range"},
            {"SELECT 10000000000000000::NUMBER(38,3) * 12345678901234567.891::NUMBER(38,3)",
                "REFUSED Numeric value '123456789012345678910000000000000.000' is out of range"},
            {"SELECT 10000000000000000::NUMBER(38,3) * 10000000000000000.000::NUMBER(38,3)",
                "REFUSED Numeric value '100000000000000000000000000000000' is out of range"},
            {"SELECT 17100000000000000::NUMBER(38,3) * 10000000000000000::NUMBER(38,3)",
                "REFUSED Numeric value '171000000000000000000000000000000' is not recognized"},
            {"SELECT 17100000000000000::NUMBER(38,3) * 10000000000000001::NUMBER(38,3)",
                "REFUSED Numeric value '171000000000000017100000000000000' is not recognized"},
            {"SELECT 17100000000000000::NUMBER(38,3) * 10000000000100000::NUMBER(38,3)",
                "REFUSED Numeric value '171000000001710000000000000000000' is not recognized"},
            {"SELECT 17100000000000000::NUMBER(38,3) * 10000000100000000::NUMBER(38,3)",
                "REFUSED Numeric value '171000001710000000000000000000000' is not recognized"},
            {"SELECT 17100000000000000::NUMBER(38,3) * 10000100000000000::NUMBER(38,3)",
                "REFUSED Numeric value '171001710000000000000000000000000' is not recognized"},
            {"SELECT 17100000000000000::NUMBER(38,3) * 11000000000000000::NUMBER(38,3)",
                "REFUSED Numeric value '188100000000000000000000000000000' is not recognized"},
            {"SELECT 17100000000000000::NUMBER(38,3) * 10000000000000000.001::NUMBER(38,3)",
                "REFUSED Numeric value '171000000000000000017100000000000.000' is not recognized"},
            {"SELECT 17100000000000000::NUMBER(38,3) * 10000000000000000.01::NUMBER(38,3)",
                "REFUSED Numeric value '171000000000000000171000000000000.00' is not recognized"},
            {"SELECT 17100000000000000::NUMBER(38,3) * 10000000000000000.1::NUMBER(38,3)",
                "REFUSED Numeric value '171000000000000001710000000000000.0' is not recognized"},
            {"SELECT 17100000000000000::NUMBER(38,3) * 10000000000000001.1::NUMBER(38,3)",
                "REFUSED Numeric value '171000000000000018810000000000000.0' is not recognized"},
            {"SELECT 17100000000000000::NUMBER(38,3) * 10000000000000010.001::NUMBER(38,3)",
                "REFUSED Numeric value '171000000000000171017100000000000.000' is not recognized"},
            {"SELECT 17100000000000000::NUMBER(38,3) * 12345678901234567.891::NUMBER(38,3)",
                "REFUSED Numeric value '211111109211111110936100000000000.000' is not recognized"},
            {"SELECT 17100000000000000::NUMBER(38,3) * 10000000000000000.000::NUMBER(38,3)",
                "REFUSED Numeric value '171000000000000000000000000000000' is not recognized"},
            {"SELECT 1/0 WHERE 1 = 0",
                ""},
            {"SELECT 1/0 FROM VALUES (1) WHERE column1 = 0",
                ""},
            {"SELECT '171000000000000000000000000000000'::NUMBER(38,6)",
                "REFUSED Numeric value '171000000000000000000000000000000' is not recognized"},
            {"SELECT '100000000000000000000000000000000'::NUMBER(38,6)",
                "REFUSED Numeric value '100000000000000000000000000000000' is out of range"},
            {"SELECT 2e19::NUMBER(38,0) * 1.0",
                "20000000000000000000"},
            {"SELECT 2e19::NUMBER(38,0) * 1",
                "20000000000000000000"},
            {"SELECT 12345678901234567.891 * 12345678901234567.891",
                "152415787532388367526596557677488.187881"},
            {"SELECT 13100000000000000.000 * 13100000000000000.000",
                "171610000000000000000000000000000"},
            {"SELECT 0.5::NUMBER(38,3) * 0.2::NUMBER(38,3)",
                "0.100000"},
            {"SELECT 13100000000000000.5::NUMBER(38,3) * 13100000000000000.2::NUMBER(38,3)",
                "REFUSED Numeric value '171610000000000009170000000000000.10' is not recognized"},
            {"SELECT 13100000000000000.000::NUMBER(38,3) * 13100000000000000::NUMBER(38,0)",
                "171610000000000000000000000000000.000"},
            {"SELECT CASE WHEN 1 = 0 THEN 1.31e16::NUMBER(38,3) * 1.31e16::NUMBER(38,3) END",
                "null"},
            {"SELECT 1.31e16::NUMBER(38,3) * column1 FROM VALUES (1.31e16::NUMBER(38,3))",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+38"},
            {"SELECT c*c FROM t8",
                "REFUSED Numeric value '171610000000000020960000000000000.64' is not recognized"},
            {"SELECT VAR_POP(c) FROM t8",
                "REFUSED Numeric value '171610000000000020960000000000000.64' is not recognized"},
            {"SELECT c*c FROM t8 WHERE d = 5",
                ""},
            {"SELECT c*1.31e16::NUMBER(38,3) FROM t8",
                "REFUSED Numeric value '171610000000000010480000000000000.0' is not recognized"},
            {"SELECT c*c FROM t8 GROUP BY c",
                "REFUSED Numeric value '171610000000000020960000000000000.64' is not recognized"},
            {"SELECT VAR_POP(c) OVER () FROM t8",
                "REFUSED Numeric value '171610000000000020960000000000000.64' is not recognized"},
            {"SELECT VAR_POP(c) FROM t8 GROUP BY d",
                "REFUSED Numeric value '171610000000000020960000000000000.64' is not recognized"},
            {"SELECT c*c FROM t9",
                "REFUSED Numeric value '171610000000000000000000000000000' is not recognized"},
            {"SELECT c*c FROM (SELECT c FROM t9)",
                "REFUSED Numeric value '171610000000000000000000000000000' is not recognized"},
            {"SELECT a.c*b.c FROM t9 a JOIN t9 b ON a.d = b.d",
                "REFUSED Numeric value '171610000000000000000000000000000' is not recognized"},
            {"SELECT c*c FROM t10",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+38"},
            {"SELECT c*1.0 FROM t10",
                "13100000000000000000"},
        });
    }

    @Test
    public void squaresOfPinnedArguments() {
        engine.execute("CREATE OR REPLACE TABLE t1 (c NUMBER(38,3)) AS SELECT 1e17::NUMBER(38,3)");
        engine.execute("CREATE OR REPLACE TABLE t2 (c NUMBER(38,3)) AS SELECT column1::NUMBER(38,3) FROM VALUES "
            + "(1e17), (1e17)");
        engine.execute("CREATE OR REPLACE TABLE t3 (c NUMBER(38,3)) AS SELECT column1::NUMBER(38,3) FROM VALUES "
            + "(1e17), (NULL)");
        engine.execute("CREATE OR REPLACE TABLE t4 (c NUMBER(38,7)) AS SELECT 1e13::NUMBER(38,7)");
        engine.execute("CREATE OR REPLACE TABLE t5 (c NUMBER(38,6)) AS SELECT 1e14::NUMBER(38,6)");
        engine.execute("CREATE OR REPLACE TABLE t6 (c NUMBER(38,0)) AS SELECT 20000000000000000000::NUMBER(38,0)");
        engine.execute("CREATE OR REPLACE TABLE t7 (c NUMBER(38,3)) AS SELECT 1e15::NUMBER(38,3)");
        engine.execute("CREATE OR REPLACE TABLE t8 (c NUMBER(38,3), d INT) AS SELECT column1::NUMBER(38,3), column2 "
            + "FROM VALUES (13100000000000000.8, 1), (13100000000000000.8, 2)");
        engine.execute("CREATE OR REPLACE TABLE t11 (c NUMBER(38,3), d INT) AS SELECT column1::NUMBER(38,3), column2"
            + " FROM VALUES (13100000000000000.8, 1), (13100000000000000.7, 2)");
        engine.execute("CREATE OR REPLACE TABLE t12 (c NUMBER(38,7), d INT) AS SELECT column1::NUMBER(38,7), column2"
            + " FROM VALUES (13000000000000.1, 1), (13000000000000.1, 2)");
        engine.execute("CREATE OR REPLACE TABLE t13 (c NUMBER(38,3) NOT NULL, d INT) AS SELECT "
            + "column1::NUMBER(38,3), column2 FROM VALUES (1.31e16, 1)");
        engine.execute("DELETE FROM t13");
        assertCells(new String[][] {
            {"SELECT VAR_POP(c) FROM t1",
                "REFUSED Numeric value '10000000000000000000000000000000000' is not recognized"},
            {"SELECT c*c FROM t1",
                "REFUSED Numeric value '10000000000000000000000000000000000' is not recognized"},
            {"SELECT VARIANCE(c) FROM t1",
                "REFUSED Numeric value '10000000000000000000000000000000000' is not recognized"},
            {"SELECT STDDEV_POP(c) FROM t1",
                "REFUSED Numeric value '10000000000000000000000000000000000' is not recognized"},
            {"SELECT VAR_POP(c) FROM t2",
                "REFUSED Numeric value '10000000000000000000000000000000000' is not recognized"},
            {"SELECT c*c FROM t2",
                "REFUSED Numeric value '10000000000000000000000000000000000' is not recognized"},
            {"SELECT VAR_POP(c) FROM t3",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 1e+40"},
            {"SELECT c*c FROM t3",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 1e+40"},
            {"SELECT VAR_POP(c) FROM t4",
                "REFUSED Numeric value '100000000000000000000000000' is out of range"},
            {"SELECT c*c FROM t4",
                "REFUSED Numeric value '100000000000000000000000000' is out of range"},
            {"SELECT VAR_POP(c) FROM t5",
                "REFUSED Numeric value '10000000000000000000000000000' is not recognized"},
            {"SELECT c*c FROM t5",
                "REFUSED Numeric value '10000000000000000000000000000' is not recognized"},
            {"SELECT 1e17::NUMBER(38,3) * 1e17::NUMBER(38,3)",
                "REFUSED Numeric value '10000000000000000000000000000000000' is not recognized"},
            {"SELECT VAR_POP(1e17::NUMBER(38,3))",
                "REFUSED Numeric value '10000000000000000000000000000000000' is not recognized"},
            {"SELECT 1e13::NUMBER(38,7) * 1e13::NUMBER(38,7)",
                "REFUSED Numeric value '100000000000000000000000000' is out of range"},
            {"SELECT VAR_POP(1e13::NUMBER(38,7))",
                "REFUSED Numeric value '100000000000000000000000000' is out of range"},
            {"SELECT 2e19::NUMBER(38,0) * 2e19::NUMBER(38,0)",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 4e+38"},
            {"SELECT 1e14::NUMBER(38,6) * 1e14::NUMBER(38,6)",
                "REFUSED Numeric value '10000000000000000000000000000' is not recognized"},
            {"SELECT VAR_POP(column1) FROM VALUES (1e17::NUMBER(38,3))",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 1e+40"},
            {"SELECT column1*column1 FROM VALUES (1e17::NUMBER(38,3))",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 1e+40"},
            {"SELECT VAR_POP(c) FROM t6",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 4e+38"},
            {"SELECT c*c FROM t6",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value 4e+38"},
            {"SELECT c*c FROM t7",
                "1000000000000000000000000000000.000000"},
            {"SELECT VAR_POP(c) OVER () FROM t8",
                "REFUSED Numeric value '171610000000000020960000000000000.64' is not recognized"},
            {"SELECT VAR_POP(c) OVER (ORDER BY d ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) FROM t8",
                "REFUSED Numeric value '171610000000000020960000000000000.64' is not recognized"},
            {"SELECT VAR_POP(c) OVER (PARTITION BY d) FROM t8",
                "REFUSED Numeric value '171610000000000020960000000000000.64' is not recognized"},
            {"SELECT VAR_POP(c) OVER (ORDER BY d) FROM t8",
                "REFUSED Numeric value '171610000000000020960000000000000.64' is not recognized"},
            {"SELECT VAR_SAMP(c), STDDEV(c), STDDEV_SAMP(c), VARIANCE_POP(c) FROM t8",
                "REFUSED Numeric value '171610000000000020960000000000000.64' is not recognized"},
            {"SELECT VAR_POP(DISTINCT c) FROM t8",
                "REFUSED Numeric value '171610000000000020960000000000000.64' is not recognized"},
            {"SELECT VAR_POP(c) FROM t8 WHERE d = 1",
                "REFUSED Numeric value '171610000000000020960000000000000.64' is not recognized"},
            {"SELECT VAR_POP(c) FROM t8 WHERE d = 5",
                "null"},
            {"SELECT VAR_POP(-c) FROM t8",
                "REFUSED Numeric value '171610000000000020960000000000000.64' is not recognized"},
            {"SELECT VAR_POP(c + 0) FROM t8",
                "REFUSED Numeric value '171610000000000020960000000000000.64' is not recognized"},
            {"SELECT VAR_POP(c::NUMBER(38,3)) FROM t8",
                "REFUSED Numeric value '171610000000000020960000000000000.64' is not recognized"},
            {"SELECT VAR_POP(c) FROM t11",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+38"},
            {"SELECT VAR_POP(c) FROM t11 WHERE d = 1",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+38"},
            {"SELECT c*c FROM t11 WHERE d = 1",
                "REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value "
                + "1.7161e+38"},
            {"SELECT VAR_POP(c) FROM t12",
                "REFUSED Numeric value '169000000000002600000000000.01' is out of range"},
            {"SELECT c*c FROM t12",
                "REFUSED Numeric value '169000000000002600000000000.01' is out of range"},
            {"SELECT VAR_POP(c) OVER (ORDER BY d ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) FROM t12",
                "REFUSED Numeric value '169000000000002600000000000.01' is out of range"},
            {"SELECT VAR_POP(c), c*c FROM t13",
                "REFUSED SQL compilation error: | [T13.C] is not a valid group by expression"},
            {"SELECT c*c FROM t13",
                ""},
        });
    }
}
