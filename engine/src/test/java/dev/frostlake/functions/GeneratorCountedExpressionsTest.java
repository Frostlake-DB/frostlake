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
 * A GENERATOR read alone past 2147483647 rows answers aggregates computed from its row count (live-verified): a WHERE
 * that is FALSE reads none, {@code COUNT(*) + 1} over three billion rows is 3000000001 and {@code SUM(1)}
 * 3000000000, {@code AVG(1)} is 1.000000, and a HAVING over the counts is judged over them. A derived table of
 * literals passes the rows on to the select counting them, and a SEQ ordered under a LIMIT answers the wrapped
 * sequence's first values: SEQ4 wraps past 2147483647, so its lowest values come twice, and SEQ8 never wraps.
 */
public class GeneratorCountedExpressionsTest extends MeasuredCellsTestSupport {

    @Test
    public void aggregatesComputedFromTheRowCount() {
        assertCells(new String[][] {
            {"SELECT 1 FROM TABLE(GENERATOR(ROWCOUNT => 3000000000)) WHERE FALSE", ""},
            {"SELECT COUNT(*) + 1 FROM TABLE(GENERATOR(ROWCOUNT => 3000000000))", "3000000001"},
            {"SELECT SUM(1) FROM TABLE(GENERATOR(ROWCOUNT => 3000000000))", "3000000000"},
            {"SELECT AVG(1) FROM TABLE(GENERATOR(ROWCOUNT => 3000000000))", "1.000000"},
            {"SELECT COUNT(*), MIN(1), MAX(2) FROM TABLE(GENERATOR(ROWCOUNT => 3000000000))", "3000000000|1|2"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 3000000000)) HAVING COUNT(*) > 5", "3000000000"},
        });
    }

    @Test
    public void aDerivedTableCountedThroughAndAnOrderedSequence() {
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM (SELECT 1 AS x FROM TABLE(GENERATOR(ROWCOUNT => 3000000000)))", "3000000000"},
            {"SELECT COUNT(*) + 1 FROM (SELECT 1 AS x FROM TABLE(GENERATOR(ROWCOUNT => 3000000000)))", "3000000001"},
            {"SELECT COUNT(*), MAX(1) FROM (SELECT 1 AS x, 'a' AS y FROM TABLE(GENERATOR(ROWCOUNT => 3000000000)))",
                "3000000000|1"},
            {"SELECT SEQ4() AS s FROM TABLE(GENERATOR(ROWCOUNT => 3000000000)) ORDER BY s LIMIT 2", "0 ;; 0"},
            {"SELECT SEQ4() AS s FROM TABLE(GENERATOR(ROWCOUNT => 3000000000)) ORDER BY s DESC LIMIT 2",
                "2147483647 ;; 2147483646"},
            {"SELECT SEQ8() AS s FROM TABLE(GENERATOR(ROWCOUNT => 3000000000)) ORDER BY s DESC LIMIT 2",
                "2999999999 ;; 2999999998"},
            {"SELECT SEQ8() AS s FROM TABLE(GENERATOR(ROWCOUNT => 3000000000)) ORDER BY s LIMIT 3", "0 ;; 1 ;; 2"},
            {"SELECT SEQ4() FROM TABLE(GENERATOR(ROWCOUNT => 3000000000)) ORDER BY 1 DESC LIMIT 2 OFFSET 1",
                "2147483646 ;; 2147483645"},
        });
    }
}
