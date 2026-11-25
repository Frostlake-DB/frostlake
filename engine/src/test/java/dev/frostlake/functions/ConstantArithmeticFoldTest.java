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
 * A tree of sums, differences, products and negations over numeric literals and casts of them is folded exactly and
 * converted once, at its root, to the type the root declares (live-verified): the refusal names the whole tree's text
 * and fires while the statement compiles, under a WHERE that keeps no row, in an IFF or CASE branch whose condition
 * reads a column, in a view's body the statement reads. A branch a constant condition never takes, a COALESCE
 * argument after the first non-NULL one and an item nobody reads stay unfolded; a cast, IS [NOT] NULL and a
 * comparison of two constants consume the exact value unconverted.
 */
public class ConstantArithmeticFoldTest extends MeasuredCellsTestSupport {

    @Test
    public void literalTrees() {
        assertCells(new String[][] {
            {"SELECT 1e17::NUMBER(38,3) * 1e17::NUMBER(38,3) WHERE 1 = 0",
                "REFUSED Numeric value '10000000000000000000000000000000000' is not recognized"},
            {"SELECT 1e17::NUMBER(38,7) * 1e17::NUMBER(38,7) WHERE 1 = 0",
                "REFUSED Numeric value '10000000000000000000000000000000000' is not recognized"},
            {"SELECT 1e17::NUMBER(38,3) * 1e17::NUMBER(38,3) FROM VALUES (1) WHERE column1 = 0",
                "REFUSED Numeric value '10000000000000000000000000000000000' is not recognized"},
            {"SELECT (1e17::NUMBER(38,3) * 1e17::NUMBER(38,3)) + 1",
                "REFUSED Numeric value '10000000000000000000000000000000001' is not recognized"},
            {"SELECT (1e17::NUMBER(38,3) * 1e17::NUMBER(38,3)) - 1",
                "REFUSED Numeric value '9999999999999999999999999999999999' is not recognized"},
            {"SELECT (1e17::NUMBER(38,3) * 1e17::NUMBER(38,3)) * 2",
                "REFUSED Numeric value '20000000000000000000000000000000000' is not recognized"},
            {"SELECT (1e17::NUMBER(38,3) * 1e17::NUMBER(38,3)) / 2",
                "REFUSED Numeric value '10000000000000000000000000000000000' is not recognized"},
            {"SELECT -(1e17::NUMBER(38,3) * 1e17::NUMBER(38,3))",
                "REFUSED Numeric value '-10000000000000000000000000000000000' is not recognized"},
            {"SELECT (1e17::NUMBER(38,3) * 1e17::NUMBER(38,3)) + 0.5",
                "REFUSED Numeric value '10000000000000000000000000000000000.5' is not recognized"},
            {"SELECT (1e17::NUMBER(38,3) * 1e17::NUMBER(38,3)) + 1e17::NUMBER(38,3)",
                "REFUSED Numeric value '10000000000000000100000000000000000' is not recognized"},
            {"SELECT 2 * 3 * 1e17::NUMBER(38,3) * 1e17::NUMBER(38,3)",
                "REFUSED Numeric value '60000000000000000000000000000000000' is not recognized"},
            {"SELECT (1e17::NUMBER(38,3) * 1e17::NUMBER(38,3)) IS NULL",
                "false"},
            {"SELECT (1e17::NUMBER(38,3) * 1e17::NUMBER(38,3)) IS NOT NULL",
                "true"},
            {"SELECT (1e17::NUMBER(38,3) * 1e17::NUMBER(38,3)) + 1 IS NULL",
                "false"},
            {"SELECT 1 WHERE (1e17::NUMBER(38,3) * 1e17::NUMBER(38,3)) IS NULL",
                ""},
            {"SELECT IFF(column1 = 0, 1e17::NUMBER(38,3) * 1e17::NUMBER(38,3), 0) FROM VALUES (1)",
                "REFUSED Numeric value '10000000000000000000000000000000000' is not recognized"},
            {"SELECT IFF(column1 = 0, 0, 1e17::NUMBER(38,3) * 1e17::NUMBER(38,3)) FROM VALUES (0)",
                "REFUSED Numeric value '10000000000000000000000000000000000' is not recognized"},
            {"SELECT IFF(1 = 0, 1e17::NUMBER(38,3) * 1e17::NUMBER(38,3), 0)",
                "0.000000"},
            {"SELECT CASE WHEN 1 = 0 THEN 1e17::NUMBER(38,3) * 1e17::NUMBER(38,3) END",
                "null"},
            {"SELECT CASE WHEN column1 = 0 THEN 1e17::NUMBER(38,3) * 1e17::NUMBER(38,3) END FROM VALUES "
                + "(1)",
                "REFUSED Numeric value '10000000000000000000000000000000000' is not recognized"},
            {"SELECT CASE column1 WHEN 0 THEN 1e17::NUMBER(38,3) * 1e17::NUMBER(38,3) END FROM VALUES (1)",
                "REFUSED Numeric value '10000000000000000000000000000000000' is not recognized"},
            {"SELECT CASE WHEN 1 = 1 THEN 0 ELSE 1e17::NUMBER(38,3) * 1e17::NUMBER(38,3) END",
                "0.000000"},
            {"SELECT COALESCE(1e17::NUMBER(38,3) * 1e17::NUMBER(38,3), 0)",
                "REFUSED Numeric value '10000000000000000000000000000000000' is not recognized"},
            {"SELECT COALESCE(1, 1e17::NUMBER(38,3) * 1e17::NUMBER(38,3))",
                "1.000000"},
            {"SELECT COALESCE(column1, 1e17::NUMBER(38,3) * 1e17::NUMBER(38,3)) FROM VALUES (1)",
                "1.000000"},
            {"SELECT COALESCE(NULL, 1e17::NUMBER(38,3) * 1e17::NUMBER(38,3))",
                "REFUSED Numeric value '10000000000000000000000000000000000' is not recognized"},
            {"SELECT 1/0 WHERE 1 = 0",
                ""},
            {"SELECT (99e15::NUMBER(38,3) * 1e15::NUMBER(38,3)) + (99e15::NUMBER(38,3) * "
                + "1e15::NUMBER(38,3))",
                "REFUSED Numeric value '198000000000000000000000000000000' is not recognized"},
            {"SELECT 5e16::NUMBER(38,3) * 2e16::NUMBER(38,3) + 5e16::NUMBER(38,3) * 2e16::NUMBER(38,3)",
                "REFUSED Numeric value '2000000000000000000000000000000000' is not recognized"},
            {"SELECT 99999999999999999999999999999999::NUMBER(38,6) + 1::NUMBER(38,6) WHERE 1 = 0",
                "REFUSED Numeric value '100000000000000000000000000000000' is out of range"},
            {"SELECT 1e37::NUMBER(38,0) + 1::NUMBER(38,6) WHERE 1 = 0",
                ""},
            {"SELECT (1e17::NUMBER(38,3) * 1e17::NUMBER(38,3))::VARCHAR",
                "10000000000000000000000000000000000.000000"},
            {"SELECT (1e17::NUMBER(38,3) * 1e17::NUMBER(38,3))::NUMBER(38,0)",
                "10000000000000000000000000000000000"},
            {"SELECT CAST(1e17::NUMBER(38,3) * 1e17::NUMBER(38,3) AS NUMBER(38,0)) + 1",
                "10000000000000000000000000000000001"},
            {"SELECT 1e17::NUMBER(38,3) * 1e17::NUMBER(38,3) - 1e17::NUMBER(38,3) * 1e17::NUMBER(38,3)",
                "0.000000"},
            {"SELECT (1e17::NUMBER(38,3) * 1e17::NUMBER(38,3) - 1e17::NUMBER(38,3) * 1e17::NUMBER(38,3)) +"
                + " 1",
                "1.000000"},
            {"SELECT (1e17::NUMBER(38,3) * 1e17::NUMBER(38,3)) = 1",
                "false"},
            {"SELECT ((1e17::NUMBER(38,3) * 1e17::NUMBER(38,3)) + 1) = 1",
                "false"},
            {"SELECT (1e17::NUMBER(38,3) * 1e17::NUMBER(38,3)) > column1 FROM VALUES (1)",
                "REFUSED Numeric value '10000000000000000000000000000000000' is not recognized"},
            {"SELECT (1e17::NUMBER(38,3) * 1e17::NUMBER(38,3)) IS DISTINCT FROM NULL",
                "true"},
            {"SELECT (1e17::NUMBER(38,3) * 1e17::NUMBER(38,3)) + NULL",
                "null"},
            {"SELECT NULL * (1e17::NUMBER(38,3) * 1e17::NUMBER(38,3))",
                "null"},
            {"SELECT (1e17::NUMBER(38,3) * 1e17::NUMBER(38,3)) - 1e34",
                "REFUSED Numeric value '10000000000000000000000000000000000' is not recognized"},
            {"SELECT 1e10::NUMBER(38,2) * 1e10::NUMBER(38,2) * 1e10::NUMBER(38,2) * 1e10::NUMBER(38,2) "
                + "WHERE 1 = 0",
                ""},
            {"SELECT 1e12::NUMBER(38,3) * 1e12::NUMBER(38,3) * 1e12::NUMBER(38,3) WHERE 1 = 0",
                "REFUSED Numeric value '1000000000000000000000000000000000000' is not recognized"},
            {"SELECT 12345678901234567.891::NUMBER(38,3) * 1e17::NUMBER(38,3)",
                "REFUSED Numeric value '1234567890123456789100000000000000.000' is not recognized"},
            {"SELECT 1e19::NUMBER(38,0) * 1e19::NUMBER(38,0) WHERE 1 = 0",
                ""},
            {"SELECT 1e17::NUMBER(38,3) * 1e17::NUMBER(38,3) * column1 FROM VALUES (1) WHERE 1 = 0",
                "REFUSED Numeric value '10000000000000000000000000000000000' is not recognized"},
            {"SELECT ABS(1e17::NUMBER(38,3) * 1e17::NUMBER(38,3)) WHERE 1 = 0",
                "REFUSED Numeric value '10000000000000000000000000000000000' is not recognized"},
            {"SELECT NULLIF(1e17::NUMBER(38,3) * 1e17::NUMBER(38,3), 1)",
                "REFUSED Numeric value '10000000000000000000000000000000000' is not recognized"},
            {"SELECT 1.5::NUMBER(38,3) * 1.5::NUMBER(38,3) * 1.5::NUMBER(38,3)",
                "3.375000000"},
            {"SELECT 1e17::NUMBER(38,3) * 1e17::NUMBER(38,3) - 1e17::NUMBER(38,3) * 1e17::NUMBER(38,3) * 1"
                + " + 5",
                "5.000000"},
            {"SELECT 2 * 1e17::NUMBER(38,3) * 1e17::NUMBER(38,3) / 4",
                "REFUSED Numeric value '20000000000000000000000000000000000' is not recognized"},
        });
    }

    @Test
    public void readersOfTheTree() {
        engine.execute("CREATE OR REPLACE TABLE FOLD_R (c NUMBER(38,3))");
        engine.execute("INSERT INTO FOLD_R VALUES (1.5), (1.5)");
        engine.execute("CREATE OR REPLACE VIEW FOLD_V AS SELECT 1e17::NUMBER(38,3) * 1e17::NUMBER(38,3) AS p, 1 AS y");
        assertCells(new String[][] {
            {"SELECT p FROM FOLD_V",
                "REFUSED Numeric value '10000000000000000000000000000000000' is not recognized"},
            {"SELECT p FROM FOLD_V WHERE 1 = 0",
                "REFUSED Numeric value '10000000000000000000000000000000000' is not recognized"},
            {"SELECT y FROM FOLD_V",
                "1"},
            {"SELECT COUNT(*) FROM FOLD_V",
                "1"},
            {"SELECT y FROM (SELECT 1e17::NUMBER(38,3) * 1e17::NUMBER(38,3) AS x, 1 AS y)",
                "1"},
            {"WITH c AS (SELECT 1e17::NUMBER(38,3) * 1e17::NUMBER(38,3) AS x, 1 AS y) SELECT y FROM c",
                "1"},
            {"SELECT y FROM (SELECT 1e17::NUMBER(38,3) * 1e17::NUMBER(38,3) AS x, 1 AS y) WHERE 1 = 0",
                ""},
            {"SELECT * FROM (SELECT 1e17::NUMBER(38,3) * 1e17::NUMBER(38,3) AS x) WHERE 1 = 0",
                "REFUSED Numeric value '10000000000000000000000000000000000' is not recognized"},
            {"SELECT 1 WHERE EXISTS (SELECT 1e17::NUMBER(38,3) * 1e17::NUMBER(38,3))",
                "1"},
            {"SELECT (SELECT 1e17::NUMBER(38,3) * 1e17::NUMBER(38,3))",
                "REFUSED Numeric value '10000000000000000000000000000000000' is not recognized"},
            {"SELECT 1e17::NUMBER(38,3) * 1e17::NUMBER(38,3) FROM FOLD_R WHERE c > 5",
                "REFUSED Numeric value '10000000000000000000000000000000000' is not recognized"},
            {"SELECT 1e17::NUMBER(38,3) * 1e17::NUMBER(38,3) FROM FOLD_R LIMIT 0",
                "REFUSED Numeric value '10000000000000000000000000000000000' is not recognized"},
            {"SELECT 1 FROM FOLD_R GROUP BY c HAVING MAX(c) = 1e17::NUMBER(38,3) * 1e17::NUMBER(38,3)",
                "REFUSED Numeric value '10000000000000000000000000000000000' is not recognized"},
            {"INSERT INTO FOLD_R SELECT 1e17::NUMBER(38,3) * 1e17::NUMBER(38,3) WHERE 1 = 0",
                "REFUSED Numeric value '10000000000000000000000000000000000' is not recognized"},
        });
    }

    @Test
    public void constantOrderKeys() {
        assertCells(new String[][] {
            {"SELECT y FROM (SELECT 1e17::NUMBER(38,3) * 1e17::NUMBER(38,3) AS x, 1 AS y) ORDER BY x",
                "1"},
            {"WITH c AS (SELECT 1e17::NUMBER(38,3) * 1e17::NUMBER(38,3) AS x, 1 AS y) SELECT y FROM c "
                + "ORDER BY x",
                "1"},
            {"SELECT x FROM (SELECT 1e17::NUMBER(38,3) * 1e17::NUMBER(38,3) AS x, 1 AS y) ORDER BY x",
                "REFUSED Numeric value '10000000000000000000000000000000000' is not recognized"},
            {"SELECT y FROM (SELECT 2 + 3 AS x, 1 AS y) ORDER BY x DESC NULLS FIRST",
                "1"},
        });
    }
}
