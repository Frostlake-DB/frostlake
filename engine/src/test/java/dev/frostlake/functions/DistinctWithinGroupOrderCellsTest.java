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
 * {@code LISTAGG} and {@code ARRAY_AGG} over DISTINCT values ordered WITHIN GROUP: a sort key must be computed from the
 * distinct value alone, and any other key is refused as no valid order by expression, named whole as the plan holds
 * it. Answers measured on a live account.
 */
public class DistinctWithinGroupOrderCellsTest extends MeasuredCellsTestSupport {

    @Test
    public void keysTheDistinctValueDetermines() {
        engine.execute("CREATE OR REPLACE TABLE la(g INT, x VARCHAR, y INT, \"Xq\" VARCHAR)");
        engine.execute("INSERT INTO la VALUES (1,'a',3,'a'),(1,'b',1,'b'),(1,'a',2,'a'),(2,'c',5,'c')");
        engine.execute("CREATE OR REPLACE TABLE la(g INT, x VARCHAR, y INT, \"Xq\" VARCHAR)");
        engine.execute("INSERT INTO la VALUES (1,'a',3,'a'),(1,'b',1,'b'),(1,'a',2,'a'),(2,'c',5,'c')");
        engine.execute("CREATE OR REPLACE TABLE \"lower t\"(x VARCHAR, y INT)");
        engine.execute("CREATE OR REPLACE TABLE la(g INT, x VARCHAR, y INT, \"Xq\" VARCHAR)");
        engine.execute("INSERT INTO la VALUES (1,'a',3,'a'),(1,'b',1,'b'),(1,'a',2,'a'),(2,'c',5,'c')");
        assertCells(new String[][] {
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY y) FROM la",
                "REFUSED SQL compilation error: | [LA.Y] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY x) FROM la",
                "a,b,c"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY x DESC) FROM la",
                "c,b,a"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY la.x) FROM la",
                "a,b,c"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY X) FROM la",
                "a,b,c"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY x, y) FROM la",
                "REFUSED SQL compilation error: | [LA.Y] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY UPPER(x)) FROM la",
                "a,b,c"},
            {"SELECT LISTAGG(DISTINCT UPPER(x), ',') WITHIN GROUP (ORDER BY UPPER(x)) FROM la",
                "A,B,C"},
            {"SELECT LISTAGG(DISTINCT UPPER(x), ',') WITHIN GROUP (ORDER BY x) FROM la",
                "REFUSED SQL compilation error: | [LA.X] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY \"Xq\") FROM la",
                "REFUSED SQL compilation error: | [LA.\"Xq\"] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x) WITHIN GROUP (ORDER BY y) FROM la",
                "REFUSED SQL compilation error: | [LA.Y] is not a valid order by expression"},
            {"SELECT g, LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY y) FROM la GROUP BY g ORDER BY g",
                "REFUSED SQL compilation error: | [LA.Y] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY y) OVER (PARTITION BY g) FROM la",
                "REFUSED SQL compilation error: | [LA.Y] is not a valid order by expression"},
            {"SELECT LISTAGG(x, ',') WITHIN GROUP (ORDER BY y) FROM la",
                "b,a,a,c"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY 1) FROM la",
                "a,b,c"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY 'k') FROM la",
                "a,b,c"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY x NULLS FIRST) FROM la",
                "a,b,c"},
            {"SELECT LISTAGG(DISTINCT y, ',') WITHIN GROUP (ORDER BY y::VARCHAR) FROM la",
                "1,2,3,5"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY (x)) FROM la",
                "a,b,c"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY x COLLATE 'en-ci') FROM la",
                "a,b,c"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY y) FROM (SELECT column1 x, column2 y "
                + "FROM VALUES ('a',3),('b',1),('a',2))",
                "REFUSED SQL compilation error: | [\"values\".Y] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x || '', ',') WITHIN GROUP (ORDER BY x || '') FROM la",
                "a,b,c"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY y) FROM la WHERE 1 = 0",
                "REFUSED SQL compilation error: | [LA.Y] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT 'q', ',') WITHIN GROUP (ORDER BY 'q') FROM la",
                "q"},
            {"SELECT ARRAY_AGG(DISTINCT x) WITHIN GROUP (ORDER BY y) FROM la",
                "REFUSED SQL compilation error: | [LA.Y] is not a valid order by expression"},
            {"SELECT ARRAY_AGG(DISTINCT x) WITHIN GROUP (ORDER BY x, y) FROM la",
                "REFUSED SQL compilation error: | [LA.Y] is not a valid order by expression"},
            {"SELECT ARRAY_AGG(DISTINCT x) WITHIN GROUP (ORDER BY y) OVER (PARTITION BY g) FROM la",
                "REFUSED SQL compilation error: | [LA.Y] is not a valid order by expression"},
            {"SELECT g, ARRAY_AGG(DISTINCT x) WITHIN GROUP (ORDER BY y) FROM la GROUP BY g ORDER BY g",
                "REFUSED SQL compilation error: | [LA.Y] is not a valid order by expression"},
            {"SELECT ARRAYAGG(DISTINCT x) WITHIN GROUP (ORDER BY y) FROM la",
                "REFUSED SQL compilation error: | [LA.Y] is not a valid order by expression"},
            {"SELECT ARRAY_AGG(DISTINCT x) WITHIN GROUP (ORDER BY y) FROM (SELECT column1 x, column2 y "
                + "FROM VALUES ('a',3),('b',1),('a',2))",
                "REFUSED SQL compilation error: | [\"values\".Y] is not a valid order by expression"},
            {"SELECT ARRAY_UNIQUE_AGG(x) WITHIN GROUP (ORDER BY y) FROM la",
                "REFUSED SQL compilation error: | Function ARRAY_UNIQUE_AGG does not support WITHIN GROUP "
                + "clause."},
            {"SELECT ARRAY_AGG(DISTINCT x) WITHIN GROUP (ORDER BY \"Xq\") FROM la",
                "REFUSED SQL compilation error: | [LA.\"Xq\"] is not a valid order by expression"},
            {"SELECT COUNT(DISTINCT x) FROM la",
                "3"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY y) FROM la t",
                "REFUSED SQL compilation error: | [T.Y] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY y) FROM la \"tt\"",
                "REFUSED SQL compilation error: | [\"tt\".Y] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY y) FROM (SELECT x, y FROM la) sq",
                "REFUSED SQL compilation error: | [SQ.Y] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY y) FROM (SELECT x, y + 1 y FROM la) "
                + "sq",
                "REFUSED SQL compilation error: | [SQ.Y] is not a valid order by expression"},
            {"WITH c AS (SELECT x, y FROM la) SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY y) "
                + "FROM c",
                "REFUSED SQL compilation error: | [C.Y] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT a.x, ',') WITHIN GROUP (ORDER BY b.y) FROM la a JOIN la b ON a.g = "
                + "b.g",
                "REFUSED SQL compilation error: | [B.Y] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY y) FROM \"lower t\"",
                "REFUSED SQL compilation error: | [\"lower t\".Y] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY y + g) FROM la",
                "REFUSED SQL compilation error: | [LA.Y + LA.G] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY g, y) FROM la",
                "REFUSED SQL compilation error: | [LA.G] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY x || y) FROM la",
                "REFUSED SQL compilation error: | [LA.X || (CAST(LA.Y AS VARCHAR(134217728)))] is not a valid"
                + " order by expression"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY y) FROM (SELECT column1 x, column2 y "
                + "FROM VALUES ('a',3)) v",
                "REFUSED SQL compilation error: | [V.Y] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x || 'q', ',') WITHIN GROUP (ORDER BY x || 'q') FROM la",
                "aq,bq,cq"},
            {"SELECT LISTAGG(DISTINCT x || 'q', ',') WITHIN GROUP (ORDER BY UPPER(x || 'q')) FROM la",
                "aq,bq,cq"},
            {"SELECT LISTAGG(DISTINCT x || 'q', ',') WITHIN GROUP (ORDER BY UPPER(x) || 'q') FROM la",
                "REFUSED SQL compilation error: | [(UPPER(LA.X)) || 'q'] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY y) FROM la WHERE y > 100",
                "REFUSED SQL compilation error: | [LA.Y] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY nosuch) FROM la",
                "REFUSED SQL compilation error: error line 1 at position 55 | invalid identifier 'NOSUCH'"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY SUM(y)) FROM la",
                "REFUSED SQL compilation error: | [SUM(LA.Y)] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY RANDOM()) FROM la",
                "REFUSED SQL compilation error: | [RANDOM()] is not a valid order by expression"},
            {"SELECT ARRAY_AGG(DISTINCT x) WITHIN GROUP (ORDER BY y) FROM la t",
                "REFUSED SQL compilation error: | [T.Y] is not a valid order by expression"},
            {"SELECT ARRAY_AGG(DISTINCT x) WITHIN GROUP (ORDER BY y) OVER () FROM la t",
                "REFUSED SQL compilation error: | [T.Y] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY y) FROM la, (SELECT 1 z) k",
                "REFUSED SQL compilation error: | [LA.Y] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY y) FROM la GROUP BY g HAVING g = 1",
                "REFUSED SQL compilation error: | [LA.Y] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, ',') WITHIN GROUP (ORDER BY la.y) FROM la",
                "REFUSED SQL compilation error: | [LA.Y] is not a valid order by expression"},
            {"SELECT TO_JSON(ARRAY_AGG(DISTINCT x) WITHIN GROUP (ORDER BY x)) FROM la",
                "[\"a\",\"b\",\"c\"]"},
            {"SELECT TO_JSON(ARRAY_AGG(DISTINCT x) WITHIN GROUP (ORDER BY x DESC)) FROM la",
                "[\"c\",\"b\",\"a\"]"},
            {"SELECT TO_JSON(ARRAY_AGG(DISTINCT x) WITHIN GROUP (ORDER BY UPPER(x))) FROM la",
                "[\"a\",\"b\",\"c\"]"},
            {"SELECT TO_JSON(ARRAY_AGG(DISTINCT x) WITHIN GROUP (ORDER BY 1)) FROM la",
                "[\"a\",\"b\",\"c\"]"},
            {"SELECT TO_JSON(ARRAY_AGG(DISTINCT x) WITHIN GROUP (ORDER BY la.x)) FROM la",
                "[\"a\",\"b\",\"c\"]"},
            {"SELECT TO_JSON(ARRAY_AGG(DISTINCT y) WITHIN GROUP (ORDER BY -y)) FROM la",
                "[5,3,2,1]"},
            {"SELECT LISTAGG(DISTINCT y, '|') WITHIN GROUP (ORDER BY y DESC) FROM la",
                "5|3|2|1"},
            {"SELECT LISTAGG(x, '|') WITHIN GROUP (ORDER BY y DESC) FROM la",
                "c|a|a|b"},
            {"SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY x, UPPER(x) DESC) FROM la",
                "a|b|c"},
            {"SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY x, y) FROM la",
                "REFUSED SQL compilation error: | [LA.Y] is not a valid order by expression"},
            {"SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY CASE WHEN x = 'a' THEN 2 ELSE 1 END) "
                + "FROM la",
                "b|c|a"},
            {"SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY ROW_NUMBER() OVER (ORDER BY x)) FROM "
                + "la",
                "REFUSED SQL compilation error: | [ROW_NUMBER() OVER (ORDER BY LA.X ASC NULLS LAST)] is not a"
                + " valid order by expression"},
            {"SELECT ARRAY_AGG(DISTINCT x) WITHIN GROUP (ORDER BY SUM(y)) FROM la",
                "REFUSED SQL compilation error: | [SUM(LA.Y)] is not a valid order by expression"},
            {"SELECT ARRAY_AGG(DISTINCT x) WITHIN GROUP (ORDER BY nosuch) FROM la",
                "REFUSED SQL compilation error: error line 1 at position 52 | invalid identifier 'NOSUCH'"},
            {"SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY (SELECT 1)) FROM la",
                "a|b|c"},
            {"SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY y) FROM la WHERE x = 'zz'",
                "REFUSED SQL compilation error: | [LA.Y] is not a valid order by expression"},
            {"SELECT (SELECT LISTAGG(DISTINCT x, '|') WITHIN GROUP (ORDER BY y) FROM la)",
                "REFUSED SQL compilation error: | [LA.Y] is not a valid order by expression"},
        });
    }
}
