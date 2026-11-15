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

package dev.frostlake.executor;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * EXPLAIN spells a whole-number column beside a whole-number literal in a sum, a difference or a product in
 * parentheses where the account converts it to a wider integer: it stores a column's whole numbers in the fewest bytes
 * they need - one for -128 to 127, then two, four, eight and sixteen - and converts the column where the literal, or the
 * result over the stored values, needs more. A view's or a derived table's column standing for an operation is in
 * parentheses as an operand, and one standing for an exact number keeps its decimals, so that AVG of a whole one is
 * SCALED_ROUND_INT_DIVIDE (all live-verified).
 */
public class ExplainOperandWidthTest extends MeasuredCellsTestSupport {

    private static final String PLAN = " ->> SELECT \"operation\", \"expressions\" FROM $1";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE fz977 (id INT)");
        engine.execute("INSERT INTO fz977 VALUES (5), (7), (9)");
        engine.execute("CREATE OR REPLACE TABLE fw977 (id INT)");
        engine.execute("INSERT INTO fw977 VALUES (100), (200)");
        engine.execute("CREATE OR REPLACE VIEW vmul977 AS SELECT id * 2 AS id FROM fz977");
        engine.execute("CREATE OR REPLACE VIEW vone977 AS SELECT id + 1 AS id FROM fz977");
        engine.execute("CREATE OR REPLACE VIEW vbig977 AS SELECT id + 123456789012345678 AS id FROM fz977");
        engine.execute("CREATE OR REPLACE VIEW vdec977 AS SELECT id + 1.5 AS id FROM fz977");
    }

    /** A plan of one Result over a scan reading ID. */
    private static String scan(final String result) {
        return "GlobalStats|null ;; Result|" + result + " ;; TableScan|ID";
    }

    /** A plan of a Result over an Aggregate over a scan reading ID. */
    private static String aggregated(final String result, final String aggregate) {
        return "GlobalStats|null ;; Result|" + result + " ;; Aggregate|" + aggregate + " ;; TableScan|ID";
    }

    @Test
    public void aColumnConvertedForItsLiteral() {
        assertCells(new String[][] {
            {"EXPLAIN SELECT (id + 1) % 3 FROM fz977" + PLAN, scan("(FZ977.ID + 1) % 3")},
            {"EXPLAIN SELECT (id + 118) % 3 FROM fz977" + PLAN, scan("(FZ977.ID + 118) % 3")},
            {"EXPLAIN SELECT (id + 119) % 3 FROM fz977" + PLAN, scan("((FZ977.ID) + 119) % 3")},
            {"EXPLAIN SELECT (id * 14) % 3 FROM fz977" + PLAN, scan("(FZ977.ID * 14) % 3")},
            {"EXPLAIN SELECT (id * 15) % 3 FROM fz977" + PLAN, scan("((FZ977.ID) * 15) % 3")},
            {"EXPLAIN SELECT (id - 133) % 3 FROM fz977" + PLAN, scan("((FZ977.ID) - 133) % 3")},
            {"EXPLAIN SELECT (2147483648 + id) % 3 FROM fz977" + PLAN, scan("(2147483648 + (FZ977.ID)) % 3")},
            {"EXPLAIN SELECT (id + 99999999999999999999) % 3 FROM fz977" + PLAN,
                scan("((FZ977.ID) + 99999999999999999999) % 3")},
            {"EXPLAIN SELECT (-id) % 3 FROM fz977" + PLAN, scan("(NEGATE(FZ977.ID)) % 3")},
            {"EXPLAIN SELECT (id + id) % 3 FROM fz977" + PLAN, scan("(FZ977.ID + FZ977.ID) % 3")},
            {"EXPLAIN SELECT (id + 1) % 3 FROM fw977" + PLAN, "GlobalStats|null ;; Result|(FW977.ID + 1) % 3 ;; "
                + "TableScan|ID"},
            {"EXPLAIN SELECT (id + 32567) % 3 FROM fw977" + PLAN, "GlobalStats|null ;; Result|(FW977.ID + 32567) % 3 "
                + ";; TableScan|ID"},
            {"EXPLAIN SELECT (id + 32568) % 3 FROM fw977" + PLAN, "GlobalStats|null ;; Result|((FW977.ID) + 32568) % "
                + "3 ;; TableScan|ID"},
            {"EXPLAIN SELECT (id - 32868) % 3 FROM fw977" + PLAN, "GlobalStats|null ;; Result|((FW977.ID) - 32868) % "
                + "3 ;; TableScan|ID"},
        });
    }

    @Test
    public void aViewsColumnStandingForAnOperation() {
        assertCells(new String[][] {
            {"EXPLAIN SELECT id FROM vmul977 WHERE id > 12" + PLAN,
                "GlobalStats|null ;; Result|FZ977.ID * 2 ;; Filter|(FZ977.ID * 2) > 12 ;; TableScan|ID"},
            {"EXPLAIN SELECT SUM(id) FROM vmul977 GROUP BY id % 3" + PLAN, aggregated("SUM(FZ977.ID * 2)",
                "aggExprs: [SUM(FZ977.ID * 2)], groupKeys: [(FZ977.ID * 2) % 3]")},
            {"EXPLAIN SELECT id % 3 FROM vmul977" + PLAN, scan("(FZ977.ID * 2) % 3")},
            {"EXPLAIN SELECT id FROM vone977 WHERE id > 7" + PLAN,
                "GlobalStats|null ;; Result|FZ977.ID + 1 ;; Filter|(FZ977.ID + 1) > 7 ;; TableScan|ID"},
            {"EXPLAIN SELECT id % 3 FROM vbig977" + PLAN, scan("((FZ977.ID) + 123456789012345678) % 3")},
            {"EXPLAIN SELECT x % 3 FROM (SELECT id * 2 AS x FROM fz977)" + PLAN, scan("(FZ977.ID * 2) % 3")},
            {"EXPLAIN SELECT x % 3 FROM (SELECT id + 1 AS x FROM fz977)" + PLAN, scan("(FZ977.ID + 1) % 3")},
            {"EXPLAIN SELECT COUNT(id) FROM vone977" + PLAN,
                aggregated("COUNT(FZ977.ID + 1)", "aggExprs: [COUNT(FZ977.ID + 1)]")},
            {"EXPLAIN SELECT COUNT(id) FROM vbig977" + PLAN, aggregated("COUNT((FZ977.ID) + 123456789012345678)",
                "aggExprs: [COUNT((FZ977.ID) + 123456789012345678)]")},
            {"EXPLAIN SELECT AVG(DISTINCT id) FROM vbig977" + PLAN,
                aggregated("AVG(DISTINCT (FZ977.ID) + 123456789012345678)",
                    "aggExprs: [AVG(DISTINCT (FZ977.ID) + 123456789012345678)]")},
            {"EXPLAIN SELECT AVG(id) FROM vbig977" + PLAN,
                aggregated("SCALED_ROUND_INT_DIVIDE(SUM((FZ977.ID) + 123456789012345678), COUNT((FZ977.ID) + "
                    + "123456789012345678))", "aggExprs: [SUM((FZ977.ID) + 123456789012345678), COUNT((FZ977.ID) + "
                    + "123456789012345678)]")},
            {"EXPLAIN SELECT SUM(id) FROM vmul977" + PLAN,
                aggregated("SUM(FZ977.ID * 2)", "aggExprs: [SUM(FZ977.ID * 2)]")},
            {"EXPLAIN SELECT SUM(id) FROM vdec977" + PLAN,
                aggregated("SUM((FZ977.ID) + 1.5)", "aggExprs: [SUM((FZ977.ID) + 1.5)]")},
        });
    }
}
