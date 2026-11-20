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
 * MAX_BY and MIN_BY with a count, as an aggregate and as a window call: the ARRAY of up to N values ordered by
 * their keys, a NULL key skipped and a NULL value kept as the ARRAY's undefined element, NULL where no row has a
 * key, DISTINCT dropping a repeated value-and-key pair; the count's compile-time rule; and the running counts of
 * CONDITIONAL_TRUE_EVENT and CONDITIONAL_CHANGE_EVENT over large partitions. Every answer is live-verified.
 */
public class MaxByMinByCountTest extends MeasuredCellsTestSupport {

    @Test
    public void answersAndRefusals() {
        engine.execute("CREATE OR REPLACE TABLE t (g INT, o INT, x VARCHAR)");
        engine.execute("INSERT INTO t VALUES "
            + "(1,1,'a'),(1,2,'b'),(1,3,'c'),(2,5,'d'),(2,4,'e'),(2,NULL,'f'),(1,NULL,NULL),(2,6,NULL)");
        engine.execute("CREATE OR REPLACE TABLE d (v INT, k INT, g INT)");
        engine.execute("INSERT INTO d VALUES "
            + "(1,10,1),(1,9,1),(2,8,1),(3,7,1),(NULL,6,1),(5,NULL,1),(7,1,2),(7,2,2),(8,3,2),(NULL,NULL,2)");
        engine.execute("CREATE OR REPLACE TABLE cpd (g INTEGER, ref VARCHAR, k INT)");
        engine.execute("INSERT INTO cpd VALUES "
            + "(1,'ref:1.0:a',1),(1,'ref:1.0:a',1),(1,'ref:2.0:b',2),(1,'ref:1.0:a',3),(1,NULL,4),(1,NULL,4)");
        assertCells(new String[][] {
            {"SELECT TO_JSON(MAX_BY(x, o, 1)) FROM t WHERE g = 1",
                "[\"c\"]"},
            {"SELECT TO_JSON(MAX_BY(x, o, 2)) FROM t WHERE g = 2",
                "[undefined,\"d\"]"},
            {"SELECT TO_JSON(MIN_BY(x, o, 2)) FROM t WHERE g = 2",
                "[\"e\",\"d\"]"},
            {"SELECT TO_JSON(MAX_BY(x, o, 1000)) FROM t",
                "[undefined,\"d\",\"e\",\"c\",\"b\",\"a\"]"},
            {"SELECT TO_JSON(MIN_BY(o, x, 10)) FROM t",
                "[1,2,3,5,4,undefined]"},
            {"SELECT TO_JSON(MAX_BY(x, o, 2)) FROM t WHERE 1 = 0",
                "null"},
            {"SELECT TO_JSON(MAX_BY(x, o, 2)) FROM t WHERE o IS NULL",
                "null"},
            {"SELECT TO_JSON(MAX_BY(DISTINCT v, k, 3)) FROM d",
                "[1,1,2]"},
            {"SELECT TO_JSON(MIN_BY(DISTINCT v, k, 3)) FROM d",
                "[7,7,8]"},
            {"SELECT TO_JSON(MAX_BY(OBJECT_CONSTRUCT('o', o), o, 2)) FROM t",
                "[{\"o\":6},{\"o\":5}]"},
            {"SELECT MAX_BY(x, o, 0) FROM t",
                "REFUSED SQL compilation error: | Invalid value [0] for function 'MAX_BY', parameter 3: N "
                + "(last argument) must be a positive number"},
            {"SELECT MAX_BY(x, o, -1) FROM t",
                "REFUSED SQL compilation error: | Invalid value [-1] for function 'MAX_BY', parameter 3: N "
                + "(last argument) must be a positive number"},
            {"SELECT MAX_BY(x, o, 1001) FROM t",
                "REFUSED SQL compilation error: | Invalid value [1001] for function 'MAX_BY', parameter 3: N "
                + "(last argument) cannot be larger than 1000"},
            {"SELECT MIN_BY(x, o, NULL) FROM t",
                "REFUSED SQL compilation error: | Invalid value [null] for function 'MIN_BY', parameter 3: N "
                + "(last argument) must be non-null"},
            {"SELECT MAX_BY(x, o, g) FROM t",
                "REFUSED SQL compilation error: | argument 3 to function MAX_BY needs to be constant, found "
                + "'T.G'"},
            {"SELECT MAX_BY(x, o, 2.5) FROM t",
                "REFUSED SQL compilation error: | argument 3 to function MAX_BY needs to be constant, found "
                + "'CAST(2.5 AS NUMBER(18,0))'"},
            {"SELECT MAX_BY(x, o, 'abc') FROM t",
                "REFUSED SQL compilation error: | argument 3 to function MAX_BY needs to be constant, found "
                + "'TO_NUMBER('abc', 18, 0)'"},
            {"SELECT MAX_BY(x, o, 1 + 1) FROM t",
                "REFUSED SQL compilation error: | argument 3 to function MAX_BY needs to be constant, found "
                + "'1 + 1'"},
            {"SELECT MAX_BY(x, o, 2::INT) FROM t",
                "REFUSED SQL compilation error: | argument 3 to function MAX_BY needs to be constant, found "
                + "'CAST(2 AS NUMBER(38,0))'"},
            {"SELECT MAX_BY(x, o, 2::NUMBER(10,2)) FROM t",
                "REFUSED SQL compilation error: | argument 3 to function MAX_BY needs to be constant, found "
                + "'CAST(CAST(2 AS NUMBER(10,2)) AS NUMBER(18,0))'"},
            {"SELECT MAX_BY(x, o, TRUE) FROM t",
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for "
                + "function 'MAX_BY': (VARCHAR(16777216), NUMBER(38,0), BOOLEAN)"},
            {"SELECT MAX_BY(x, o, '1001') FROM t",
                "REFUSED SQL compilation error: | Invalid value [1001] for function 'MAX_BY', parameter 3: N "
                + "(last argument) cannot be larger than 1000"},
            {"SELECT MAX_BY(x, o, -0) FROM t",
                "REFUSED SQL compilation error: | Invalid value [0] for function 'MAX_BY', parameter 3: N "
                + "(last argument) must be a positive number"},
            {"SELECT MAX_BY(x, o, 99999999999999999999) FROM t",
                "REFUSED SQL compilation error: | Invalid value [99999999999999999999] for function 'MAX_BY',"
                + " parameter 3: N (last argument) cannot be larger than 1000"},
            {"SELECT MAX_BY(x, o, 0) FROM t WHERE 1 = 0",
                "REFUSED SQL compilation error: | Invalid value [0] for function 'MAX_BY', parameter 3: N "
                + "(last argument) must be a positive number"},
            {"SELECT MAX_BY(x, o, (SELECT 2)) FROM t",
                "REFUSED SQL compilation error: | argument 3 to function MAX_BY needs to be constant, found "
                + "'(SELECT 2 AS \"2\" FROM (VALUES (null)) DUAL)'"},
            {"SELECT MAX_BY(x, o, NULL::INT) FROM t",
                "REFUSED SQL compilation error: | argument 3 to function MAX_BY needs to be constant, found "
                + "'SYSTEM$NULL_TO_FIXED(null)'"},
            {"SELECT MAX_BY(x, o, 1.5e0) FROM t",
                "REFUSED SQL compilation error: | argument 3 to function MAX_BY needs to be constant, found "
                + "'CAST(1.5 AS NUMBER(18,0))'"},
            {"SELECT MIN_BY(x, o, 0) OVER () FROM t",
                "REFUSED SQL compilation error: | Invalid value [0] for function 'MIN_BY', parameter 3: N "
                + "(last argument) must be a positive number"},
            {"SELECT MAX_BY(x, o, 1001) OVER (PARTITION BY g) FROM t",
                "REFUSED SQL compilation error: | Invalid value [1001] for function 'MAX_BY', parameter 3: N "
                + "(last argument) cannot be larger than 1000"},
            {"SELECT MAX_BY(x, o, NULL) OVER () FROM t",
                "REFUSED SQL compilation error: | Invalid value [null] for function 'MAX_BY', parameter 3: N "
                + "(last argument) must be non-null"},
            {"SELECT MAX_BY(x, o, g) OVER () FROM t",
                "REFUSED SQL compilation error: | argument 3 to function MAX_BY needs to be constant, found "
                + "'T.G'"},
            {"SELECT TO_JSON(MAX_BY(x, o, 2::FLOAT)) FROM t",
                "[undefined,\"d\"]"},
            {"SELECT TO_JSON(MAX_BY(x, o, 2.5::FLOAT)) FROM t",
                "[undefined,\"d\",\"e\"]"},
            {"SELECT TO_JSON(MAX_BY(x, o, '2.5')) FROM t",
                "[undefined,\"d\",\"e\"]"},
            {"SELECT TO_JSON(MAX_BY(x, o, ' 2 ')) FROM t",
                "[undefined,\"d\"]"},
            {"SELECT TO_JSON(MAX_BY(x, o, 2.0)) FROM t",
                "[undefined,\"d\"]"},
            {"SELECT TO_JSON(MAX_BY(x, o, (2))) FROM t",
                "[undefined,\"d\"]"},
            {"SELECT TO_JSON(MAX_BY(x, o, 1e0)) FROM t",
                "[undefined]"},
            {"SELECT TO_JSON(MAX_BY(x, o, -(-2))) FROM t",
                "[undefined,\"d\"]"},
            {"SELECT TO_JSON(MAX_BY(x, o, 1000.0)) FROM t",
                "[undefined,\"d\",\"e\",\"c\",\"b\",\"a\"]"},
            {"SELECT LISTAGG(DISTINCT j, ' ; ') FROM (SELECT TO_JSON(MIN_BY(x, o, 2) OVER ()) j FROM t)",
                "[\"a\",\"b\"]"},
            {"SELECT TO_JSON(MAX_BY(x, o, 2) OVER (PARTITION BY g ORDER BY o ROWS BETWEEN UNBOUNDED "
                + "PRECEDING AND UNBOUNDED FOLLOWING)) FROM t WHERE g = 1 LIMIT 1",
                "[\"c\",\"b\"]"},
            {"SELECT LISTAGG(DISTINCT j, ' ; ') FROM (SELECT TO_JSON(MAX_BY(DISTINCT g, o, 3) OVER ()) j "
                + "FROM t)",
                "[2,2,2]"},
            {"SELECT DISTINCT SYSTEM$TYPEOF(MIN_BY(x, o, 2) OVER (PARTITION BY g)) FROM t",
                "ARRAY[LOB]"},
            {"SELECT TO_JSON(MAX_BY(x, o, 2) OVER (ORDER BY o)) FROM t",
                "REFUSED SQL compilation error: error line 1 at position 31 | Cumulative window frame "
                + "unsupported for function MAX_BY"},
            {"SELECT TO_JSON(MAX_BY(x, o, 2) OVER (ORDER BY o ROWS BETWEEN 1 PRECEDING AND CURRENT ROW)) "
                + "FROM t",
                "REFUSED SQL compilation error: error line 1 at position 48 | Sliding window frame "
                + "unsupported for function MAX_BY"},
            {"SELECT TO_JSON(MAX_BY(x, o, 2) OVER (PARTITION BY g ORDER BY o RANGE BETWEEN UNBOUNDED "
                + "PRECEDING AND UNBOUNDED FOLLOWING)) FROM t",
                "REFUSED SQL compilation error: | Aggregate window function with order by clause is not "
                + "supported: [MAX_BY(T.X, T.O, 2) OVER (PARTITION BY T.G ORDER BY T.O ASC NULLS LAST)]."},
            {"SELECT TO_JSON(ARRAY_CONSTRUCT(COUNT(*), MAX(c), SUM(c))) FROM (SELECT "
                + "CONDITIONAL_TRUE_EVENT(SEQ4() % 7 = 0) OVER (ORDER BY SEQ4()) c FROM "
                + "TABLE(GENERATOR(ROWCOUNT => 20000)))",
                "[20000,2858,28581429]"},
            {"SELECT TO_JSON(ARRAY_CONSTRUCT(COUNT(*), MAX(c), SUM(c))) FROM (SELECT "
                + "CONDITIONAL_CHANGE_EVENT(FLOOR(SEQ4() / 3)) OVER (ORDER BY SEQ4()) c FROM "
                + "TABLE(GENERATOR(ROWCOUNT => 20000)))",
                "[20000,6666,66656667]"},
            {"SELECT TO_JSON(ARRAY_CONSTRUCT(COUNT(*), MAX(c), SUM(c))) FROM (SELECT "
                + "CONDITIONAL_TRUE_EVENT(SEQ4() % 2 = 0) OVER (PARTITION BY SEQ4() % 3 ORDER BY SEQ4()) c FROM"
                + " TABLE(GENERATOR(ROWCOUNT => 20000)))",
                "[20000,3334,33340000]"},
            {"SELECT TO_JSON(MIN_BY(DISTINCT ref, ref, 100)) FROM cpd",
                "[\"ref:1.0:a\",\"ref:2.0:b\"]"},
            {"SELECT TO_JSON(MIN_BY(DISTINCT ref, k, 100)) FROM cpd",
                "[\"ref:1.0:a\",\"ref:2.0:b\",\"ref:1.0:a\",undefined]"},
            {"SELECT TO_JSON(MIN_BY(ref, k, 100)) FROM cpd",
                "[\"ref:1.0:a\",\"ref:1.0:a\",\"ref:2.0:b\",\"ref:1.0:a\",undefined,undefined]"},
            {"SELECT TO_JSON(MAX_BY(DISTINCT ref, k, 100)) FROM cpd",
                "[undefined,\"ref:1.0:a\",\"ref:2.0:b\",\"ref:1.0:a\"]"},
            {"SELECT TO_JSON(MIN_BY(DISTINCT ref, ref, 100) OVER ()) FROM cpd LIMIT 1",
                "[\"ref:1.0:a\",\"ref:2.0:b\"]"},
            {"SELECT TO_JSON(MIN_BY(DISTINCT ref, k, 100) OVER ()) FROM cpd LIMIT 1",
                "[\"ref:1.0:a\",\"ref:2.0:b\",\"ref:1.0:a\",undefined]"},
            {"SELECT TO_JSON(MIN_BY(DISTINCT k, k, 100)) FROM cpd",
                "[1,2,3,4]"},
            {"SELECT MIN_BY(DISTINCT ref, k) FROM cpd",
                "ref:1.0:a"},
        });
    }
}
