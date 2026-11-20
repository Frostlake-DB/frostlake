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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * The clauses after a ROLLUP, CUBE or GROUPING SETS grouping - HAVING, ORDER BY, QUALIFY and window calls - over a
 * table whose grouping keys hold real NULLs (live-verified): an aggregate reads a super-group's rows as stored, a
 * rolled-up key read outside an aggregate is NULL, GROUPING and GROUPING_ID read the grouping set rather than whether
 * a value is NULL, and HAVING reads a grouping key the select list does not project.
 */
public class SuperGroupMaskCellsTest extends MeasuredCellsTestSupport {

    @Test
    public void clausesAfterSuperGroups() {
        engine.execute("CREATE OR REPLACE TABLE t (g INT, x INT, y VARCHAR)");
        engine.execute("INSERT INTO t VALUES "
            + "(1,1,'a'),(1,2,'b'),(1,NULL,NULL),(2,NULL,NULL),(2,NULL,'c'),(3,5,'a'),(3,5,'a'),(NULL,7,'d'),(NULL,8,NULL)");
        assertCells(new String[][] {
            {"SELECT g, COUNT(*) FROM t GROUP BY ROLLUP(g) HAVING MAX(g) > 1 ORDER BY g NULLS LAST",
                "2|2 ;; 3|2 ;; null|9"},
            {"SELECT g, SUM(g) s FROM t GROUP BY ROLLUP(g) HAVING SUM(g) > 5 ORDER BY g NULLS LAST",
                "3|6 ;; null|13"},
            {"SELECT g, y, COUNT(*) FROM t GROUP BY g, ROLLUP(y) HAVING MAX(y) = 'b' ORDER BY g, y NULLS "
                + "LAST",
                "1|b|1 ;; 1|null|3"},
            {"SELECT g, y, COUNT(*) FROM t GROUP BY ROLLUP(g, y) HAVING COUNT(y) >= 3 ORDER BY g NULLS "
                + "LAST, y NULLS LAST",
                "null|null|9"},
            {"SELECT g, COUNT(*) FROM t GROUP BY CUBE(g) HAVING COUNT(g) > 0 ORDER BY g NULLS LAST",
                "1|3 ;; 2|2 ;; 3|2 ;; null|9"},
            {"SELECT g, y, COUNT(*) FROM t GROUP BY GROUPING SETS ((g), (y), ()) HAVING MIN(x) = 1 ORDER "
                + "BY g NULLS LAST, y NULLS LAST",
                "1|null|3 ;; null|a|3 ;; null|null|9"},
            {"SELECT g, COUNT(*) FROM t GROUP BY ROLLUP(g) HAVING COUNT(*) = 2 ORDER BY g NULLS LAST",
                "2|2 ;; 3|2 ;; null|2"},
            {"SELECT g, COUNT(*), SUM(SUM(g)) OVER () FROM t GROUP BY ROLLUP(g) ORDER BY g NULLS LAST, 2",
                "1|3|26 ;; 2|2|26 ;; 3|2|26 ;; null|2|26 ;; null|9|26"},
            {"SELECT g, COUNT(*), RANK() OVER (ORDER BY MAX(g) DESC NULLS LAST) r FROM t GROUP BY "
                + "ROLLUP(g) ORDER BY g NULLS LAST, 2",
                "1|3|4 ;; 2|2|3 ;; 3|2|1 ;; null|2|5 ;; null|9|1"},
            {"SELECT g, y, SUM(COUNT(y)) OVER (PARTITION BY g) FROM t GROUP BY ROLLUP(g, y) ORDER BY g "
                + "NULLS LAST, y NULLS LAST",
                "1|a|4 ;; 1|b|4 ;; 1|null|4 ;; 1|null|4 ;; 2|c|2 ;; 2|null|2 ;; 2|null|2 ;; 3|a|4 ;; 3|null|4"
                + " ;; null|d|8 ;; null|null|8 ;; null|null|8 ;; null|null|8"},
            {"SELECT g, MAX(SUM(x)) OVER () FROM t GROUP BY CUBE(g) ORDER BY g NULLS LAST",
                "1|28 ;; 2|28 ;; 3|28 ;; null|28 ;; null|28"},
            {"SELECT g, SUM(g) FROM t GROUP BY ROLLUP(g) ORDER BY SUM(g) NULLS FIRST, g",
                "null|null ;; 1|3 ;; 2|4 ;; 3|6 ;; null|13"},
            {"SELECT g, COUNT(*) FROM t GROUP BY ROLLUP(g) ORDER BY MAX(g) DESC NULLS LAST, COUNT(*)",
                "3|2 ;; null|9 ;; 2|2 ;; 1|3 ;; null|2"},
            {"SELECT g, y FROM t GROUP BY ROLLUP(g, y) ORDER BY COUNT(y) DESC, g NULLS LAST, y NULLS LAST",
                "null|null ;; 1|null ;; 3|a ;; 3|null ;; 1|a ;; 1|b ;; 2|c ;; 2|null ;; null|d ;; null|null "
                + ";; 1|null ;; 2|null ;; null|null"},
            {"SELECT g, COUNT(*) FROM t GROUP BY ROLLUP(g) QUALIFY RANK() OVER (ORDER BY MAX(g) DESC NULLS"
                + " LAST) = 1 ORDER BY g NULLS LAST",
                "3|2 ;; null|9"},
            {"SELECT g, y, COUNT(*) FROM t GROUP BY ROLLUP(g, y) HAVING GROUPING(y) = 1 ORDER BY g NULLS "
                + "LAST, 3",
                "1|null|3 ;; 2|null|2 ;; 3|null|2 ;; null|null|2 ;; null|null|9"},
            {"SELECT g, y, COUNT(*) FROM t GROUP BY ROLLUP(g, y) HAVING GROUPING_ID(g, y) = 1 ORDER BY g "
                + "NULLS LAST",
                "1|null|3 ;; 2|null|2 ;; 3|null|2 ;; null|null|2"},
            {"SELECT g, y, COUNT(*) FROM t GROUP BY ROLLUP(g, y) ORDER BY GROUPING(y), g NULLS LAST, y "
                + "NULLS LAST, 3",
                "1|a|1 ;; 1|b|1 ;; 1|null|1 ;; 2|c|1 ;; 2|null|1 ;; 3|a|2 ;; null|d|1 ;; null|null|1 ;; "
                + "1|null|3 ;; 2|null|2 ;; 3|null|2 ;; null|null|2 ;; null|null|9"},
            {"SELECT g, y, RANK() OVER (PARTITION BY GROUPING(y) ORDER BY g NULLS LAST, y NULLS LAST) FROM"
                + " t GROUP BY ROLLUP(g, y) ORDER BY g NULLS LAST, y NULLS LAST, 3",
                "1|a|1 ;; 1|b|2 ;; 1|null|1 ;; 1|null|3 ;; 2|c|4 ;; 2|null|2 ;; 2|null|5 ;; 3|a|6 ;; 3|null|3"
                + " ;; null|d|7 ;; null|null|4 ;; null|null|4 ;; null|null|8"},
            {"SELECT g, y, COUNT(*) FROM t GROUP BY CUBE(g, y) HAVING GROUPING_ID(g, y) = 2 ORDER BY y "
                + "NULLS LAST",
                "null|a|3 ;; null|b|1 ;; null|c|1 ;; null|d|1 ;; null|null|3"},
            {"SELECT g, COUNT(*) FROM t GROUP BY ROLLUP(g) HAVING GROUPING(g) = 0 ORDER BY g NULLS LAST",
                "1|3 ;; 2|2 ;; 3|2 ;; null|2"},
            {"SELECT g, y, GROUPING_ID(g, y) FROM t GROUP BY ROLLUP(g, y) ORDER BY GROUPING_ID(g, y) DESC,"
                + " g NULLS LAST, y NULLS LAST",
                "null|null|3 ;; 1|null|1 ;; 2|null|1 ;; 3|null|1 ;; null|null|1 ;; 1|a|0 ;; 1|b|0 ;; 1|null|0"
                + " ;; 2|c|0 ;; 2|null|0 ;; 3|a|0 ;; null|d|0 ;; null|null|0"},
            {"SELECT COUNT(*) FROM t GROUP BY ROLLUP(g) HAVING g > 1 ORDER BY 1",
                "2 ;; 2"},
            {"SELECT COUNT(*) FROM t GROUP BY ROLLUP(g) HAVING GROUPING(g) = 1",
                "9"},
            {"SELECT COUNT(*) FROM t GROUP BY GROUPING SETS ((g, y), ()) HAVING g = 1 ORDER BY 1",
                "1 ;; 1 ;; 1"},
            {"SELECT COUNT(*) FROM t GROUP BY g HAVING g > 1 ORDER BY 1",
                "2 ;; 2"},
            {"SELECT COUNT(*) FROM t GROUP BY ROLLUP(g) HAVING g IS NULL ORDER BY 1",
                "2 ;; 9"},
            {"SELECT COUNT(*) c FROM t GROUP BY ROLLUP(g, y) ORDER BY g NULLS LAST, y NULLS LAST, c",
                "1 ;; 1 ;; 1 ;; 3 ;; 1 ;; 1 ;; 2 ;; 2 ;; 2 ;; 1 ;; 1 ;; 2 ;; 9"},
            {"SELECT COUNT(*) FROM t GROUP BY ROLLUP(g) HAVING GROUPING(g) = 0 AND g IS NULL",
                "2"},
            {"SELECT g, MAX(g), SUM(g), GROUPING(g) FROM t GROUP BY ROLLUP(g) ORDER BY g NULLS LAST, 4",
                "1|1|3|0 ;; 2|2|4|0 ;; 3|3|6|0 ;; null|null|null|0 ;; null|3|13|1"},
            {"SELECT g, y, GROUPING_ID(g, y), IFF(GROUPING(y) = 1, 'sub', 'base') FROM t GROUP BY "
                + "ROLLUP(g, y) ORDER BY g NULLS LAST, y NULLS LAST, 3",
                "1|a|0|base ;; 1|b|0|base ;; 1|null|0|base ;; 1|null|1|sub ;; 2|c|0|base ;; 2|null|0|base ;; "
                + "2|null|1|sub ;; 3|a|0|base ;; 3|null|1|sub ;; null|d|0|base ;; null|null|0|base ;; "
                + "null|null|1|sub ;; null|null|3|sub"},
            {"SELECT g, SUM(GROUPING(g)) OVER () FROM t GROUP BY g ORDER BY g",
                "1|0 ;; 2|0 ;; 3|0 ;; null|0"},
            {"SELECT g, GROUPING(g) gg, SUM(GROUPING(g)) OVER () FROM t GROUP BY ROLLUP(g) ORDER BY g "
                + "NULLS LAST, 2",
                "1|0|1 ;; 2|0|1 ;; 3|0|1 ;; null|0|1 ;; null|1|1"},
            {"SELECT g, RANK() OVER (ORDER BY GROUPING(g) DESC, g) FROM t GROUP BY ROLLUP(g) ORDER BY g "
                + "NULLS LAST, 2",
                "1|2 ;; 2|3 ;; 3|4 ;; null|1 ;; null|5"},
            {"SELECT g, FIRST_VALUE(GROUPING(g)) OVER (ORDER BY COUNT(*) DESC) FROM t GROUP BY ROLLUP(g) "
                + "ORDER BY g NULLS LAST, 2",
                "1|1 ;; 2|1 ;; 3|1 ;; null|1 ;; null|1"},
            {"SELECT g, COUNT(GROUPING(g)) OVER () FROM t GROUP BY ROLLUP(g) ORDER BY g NULLS LAST, 2",
                "1|5 ;; 2|5 ;; 3|5 ;; null|5 ;; null|5"},
            {"SELECT g, GROUPING(g) FROM t GROUP BY g ORDER BY g",
                "1|0 ;; 2|0 ;; 3|0 ;; null|0"},
            {"SELECT g, COUNT(*) FROM t GROUP BY g HAVING GROUPING(g) = 0 ORDER BY g",
                "1|3 ;; 2|2 ;; 3|2 ;; null|2"},
            {"SELECT g, COUNT(*) FROM t GROUP BY g ORDER BY GROUPING(g), g",
                "1|3 ;; 2|2 ;; 3|2 ;; null|2"},
            {"SELECT g, COUNT(*) FROM t GROUP BY ROLLUP(g) QUALIFY ROW_NUMBER() OVER (PARTITION BY "
                + "GROUPING(g) ORDER BY g) = 1 ORDER BY g NULLS LAST",
                "1|3 ;; null|9"},
            {"SELECT g, COUNT(*) FROM t GROUP BY ROLLUP(g) QUALIFY GROUPING(g) = 1 OR ROW_NUMBER() OVER "
                + "(ORDER BY g) = 1 ORDER BY g NULLS LAST",
                "1|3 ;; null|9"},
            {"SELECT g, COUNT(*) FROM t GROUP BY ROLLUP(g) QUALIFY SUM(COUNT(*)) OVER () = 18 AND MAX(x) >"
                + " 4 ORDER BY g NULLS LAST, 2",
                "3|2 ;; null|2 ;; null|9"},
            {"SELECT DISTINCT GROUPING(g) FROM t GROUP BY ROLLUP(g) ORDER BY 1",
                "0 ;; 1"},
            {"SELECT g, y, COUNT(*) FROM t GROUP BY ROLLUP(g, UPPER(y)) HAVING MAX(UPPER(y)) = 'B' ORDER "
                + "BY 1, 2",
                "REFUSED SQL compilation error: error line 1 at position 10 | 'T.Y' in select clause is "
                + "neither an aggregate nor in the group by clause."},
            {"SELECT COUNT(*) FROM t GROUP BY ROLLUP(UPPER(y)) HAVING UPPER(y) IS NULL ORDER BY 1",
                "3 ;; 9"},
            {"SELECT COUNT(*) FROM t GROUP BY ROLLUP(UPPER(y)) ORDER BY UPPER(y) NULLS FIRST, 1",
                "3 ;; 9 ;; 3 ;; 1 ;; 1 ;; 1"},
            {"SELECT COUNT(*), GROUPING(UPPER(y)) FROM t GROUP BY ROLLUP(UPPER(y)) HAVING "
                + "GROUPING(UPPER(y)) = 1",
                "9|1"},
            {"SELECT UPPER(y), COUNT(*) FROM t GROUP BY ROLLUP(UPPER(y)) HAVING MAX(UPPER(y)) = 'D' ORDER "
                + "BY 1 NULLS LAST",
                "D|1 ;; null|9"},
            {"SELECT g + 1 k, COUNT(*) FROM t GROUP BY ROLLUP(g + 1) HAVING SUM(g + 1) > 4 ORDER BY 1 "
                + "NULLS LAST, 2",
                "2|3 ;; 3|2 ;; 4|2 ;; null|9"},
            {"SELECT COUNT(*) FROM t GROUP BY ROLLUP(g + 1) HAVING g + 1 > 2 ORDER BY 1",
                "2 ;; 2"},
            {"SELECT COUNT(*) c FROM t GROUP BY ROLLUP(g + 1) ORDER BY g + 1 NULLS FIRST, c",
                "2 ;; 9 ;; 3 ;; 2 ;; 2"},
            {"SELECT g, COUNT(*) FROM t GROUP BY ROLLUP(g) ORDER BY g + 0 NULLS FIRST, 2",
                "null|2 ;; null|9 ;; 1|3 ;; 2|2 ;; 3|2"},
            {"SELECT g, COUNT(*) FROM t GROUP BY ROLLUP(g) HAVING COALESCE(g, 0) = 0 ORDER BY 2",
                "null|2 ;; null|9"},
            {"SELECT g, COUNT(*) FROM t GROUP BY ROLLUP(g) HAVING COALESCE(g, MAX(g)) = 3 ORDER BY 2",
                "3|2 ;; null|9"},
            {"SELECT g, COUNT(*), SUM(MAX(g)) OVER (PARTITION BY g) FROM t GROUP BY ROLLUP(g) ORDER BY g "
                + "NULLS LAST, 2",
                "1|3|1 ;; 2|2|2 ;; 3|2|3 ;; null|2|3 ;; null|9|3"},
            {"SELECT t.g, COUNT(*) FROM t GROUP BY ROLLUP(t.g) HAVING MAX(t.g) >= 3 ORDER BY 2",
                "3|2 ;; null|9"},
            {"SELECT a.g, COUNT(*) FROM t a JOIN t b ON a.g = b.g GROUP BY ROLLUP(a.g) HAVING MAX(a.g) >= "
                + "3 ORDER BY 2",
                "3|4 ;; null|17"},
            {"SELECT a.g, COUNT(*) FROM t a JOIN t b ON a.g = b.g GROUP BY ROLLUP(a.g) HAVING "
                + "GROUPING(a.g) = 1 OR a.g = 2 ORDER BY 2",
                "2|4 ;; null|17"},
            {"SELECT g, COUNT(*) FROM t GROUP BY GROUPING SETS (g, ()) HAVING COUNT(DISTINCT g) = 3 ORDER "
                + "BY 2",
                "null|9"},
            {"SELECT g, COUNT(*) FROM t GROUP BY ROLLUP(g) HAVING LISTAGG(g, ',') WITHIN GROUP (ORDER BY "
                + "g) = '1,1,1,2,2,3,3' ORDER BY 2",
                "null|9"},
        });
    }

    /**
     * GROUPING inside a window call's argument reads the row's grouping set as it does anywhere else. The account
     * answers such a call as if every key were rolled up, unless the same call is also a select item - a defect of
     * its own, measured and not mirrored - so these cells are the engine's alone.
     */
    @Test
    public void groupingInsideWindowArgumentsReadsTheGroupingSet() {
        Assumptions.assumeFalse(isLiveSnowflake(), "the account answers these calls as if every key were rolled up");
        engine.execute("CREATE OR REPLACE TABLE t (g INT, x INT, y VARCHAR)");
        engine.execute("INSERT INTO t VALUES "
            + "(1,1,'a'),(1,2,'b'),(1,NULL,NULL),(2,NULL,NULL),(2,NULL,'c'),(3,5,'a'),(3,5,'a'),(NULL,7,'d'),(NULL,8,NULL)");
        assertCells(new String[][] {
            {"SELECT g, SUM(GROUPING(g)) OVER () FROM t GROUP BY ROLLUP(g) ORDER BY g NULLS LAST, 2",
                "1|1 ;; 2|1 ;; 3|1 ;; null|1 ;; null|1"},
            {"SELECT g, MAX(GROUPING(g)) OVER (PARTITION BY g) FROM t GROUP BY ROLLUP(g) ORDER BY g NULLS LAST, 2",
                "1|0 ;; 2|0 ;; 3|0 ;; null|1 ;; null|1"},
            {"SELECT g, y, SUM(GROUPING_ID(g, y)) OVER (PARTITION BY g) FROM t GROUP BY ROLLUP(g, y) "
                + "ORDER BY g NULLS LAST, y NULLS LAST, 3",
                "1|a|1 ;; 1|b|1 ;; 1|null|1 ;; 1|null|1 ;; 2|c|1 ;; 2|null|1 ;; 2|null|1 ;; 3|a|1 ;; 3|null|1 ;; "
                    + "null|d|4 ;; null|null|4 ;; null|null|4 ;; null|null|4"},
        });
    }
}
