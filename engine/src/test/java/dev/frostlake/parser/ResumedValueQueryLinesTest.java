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

package dev.frostlake.parser;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * Live-verified syntax-error lines of a faulty query used as a value in a clause: the reading resumes inside the
 * query at its first operator, comma or following clause; a query in a bracket, a call's or an EXISTS's has its ')'
 * close that bracket; an empty IN list's ')' closes the list; and a statement word read in a derived table or an
 * EXISTS's query.
 */
public class ResumedValueQueryLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void resumedAfterOperator() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        assertCells(new String[][] {
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a = 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 55 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a = 1 x)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 55 unexpected 'x'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a = 1 FROM)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 55 unexpected 'FROM'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a =)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a = 1 AND)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 58 unexpected ')'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a = 1 2 3)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 55 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t x y)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a = 1 GROUP BY a 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 66 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t GROUP BY a 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 54 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a = 1 2) AND a > 0",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 55 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a = 1 'x')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 55 unexpected ''x''."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a = 1 ORDER)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 60 unexpected ')'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a = 1 HAVING a = 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 68 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a = 1 QUALIFY a = 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 69 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t AS u WHERE a = 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 60 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t, t WHERE a = 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 46 unexpected 'WHERE'. | syntax error line 1 at position 58 "
                + "unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t JOIN t u ON TRUE WHERE a = 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 72 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a IN (1 2))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 57 unexpected '2'. | syntax error line 1 at position 59 "
                + "unexpected ')'."},
            {"SELECT 1 FROM t WHERE a IN (SELECT 1 FROM t WHERE a = 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 56 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = ANY (SELECT 1 FROM t WHERE a = 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 59 unexpected '2'."},
            {"SELECT 1 FROM t WHERE EXISTS (SELECT 1 FROM t WHERE a = 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected '2'."},
            {"SELECT (SELECT 1 FROM t WHERE a = 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 36 unexpected '2'."},
            {"SELECT ABS((SELECT 1 FROM t WHERE a = 1 2))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 40 unexpected '2'."},
            {"SELECT 1 FROM (SELECT 1 FROM t WHERE a = 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected '2'."},
            {"SELECT 1 FROM t HAVING a = (SELECT 1 FROM t WHERE a = 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 56 unexpected '2'."},
            {"SELECT 1 FROM t ORDER BY (SELECT 1 FROM t WHERE a = 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 54 unexpected '2'."},
            {"UPDATE t SET a = (SELECT 1 FROM t WHERE a = 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 46 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a = 1 2))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 54 unexpected 'SELECT'. | syntax error line 1 at position 82"
                + " unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a = (SELECT 1 "
                + "FROM t WHERE a = 1 2)))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 54 unexpected 'SELECT'. | syntax error line 1 at position 81"
                + " unexpected 'SELECT'. | syntax error line 1 at position 109 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a = (SELECT 1 x y))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 54 unexpected 'SELECT'. | syntax error line 1 at position 67"
                + " unexpected ')'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a = (SELECT 1 FROM t x y))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 54 unexpected 'SELECT'. | syntax error line 1 at position 74"
                + " unexpected ')'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a 2))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 54 unexpected 'SELECT'. | syntax error line 1 at position 80"
                + " unexpected ')'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a =))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 54 unexpected 'SELECT'."},
            {"SELECT (SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a = 1 2))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 35 unexpected 'SELECT'. | syntax error line 1 at position 63"
                + " unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a = 1 2) OR a = (SELECT 2 FROM t WHERE a = "
                + "1 3)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 55 unexpected '2'. | syntax error line 1 at position 66 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 94 unexpected '3'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a = 1 2); SELECT 1 x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 55 unexpected '2'. | syntax error line 1 at position 70 "
                + "unexpected 'y'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a = 1 2 FROM)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 55 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = 1 2",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a = 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 55 unexpected '2'."},
            {"SELECT (SELECT 1 FROM t WHERE a = 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 36 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = 1 FROM",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'FROM'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a = 1 FROM)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 55 unexpected 'FROM'."},
            {"SELECT (SELECT 1 FROM t WHERE a = 1 FROM)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 36 unexpected 'FROM'."},
            {"SELECT 1 FROM t WHERE a =",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '<EOF>'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a =)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'."},
            {"SELECT 1 FROM t WHERE a = 1 AND",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '<EOF>'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a = 1 AND)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 58 unexpected ')'."},
            {"SELECT 1 FROM t WHERE a 2",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'."},
            {"SELECT (SELECT 1 FROM t WHERE a 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 32 unexpected '2'."},
            {"SELECT 1 FROM t x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'y'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t x y)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'."},
            {"SELECT (SELECT 1 FROM t x y)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 26 unexpected 'y'."},
            {"SELECT 1 FROM t GROUP BY a 2",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t GROUP BY a 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 54 unexpected '2'."},
            {"SELECT (SELECT 1 FROM t GROUP BY a 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 35 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = 1 ORDER",
                "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected '<EOF>'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a = 1 ORDER)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 60 unexpected ')'."},
            {"SELECT 1 x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'y'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'."},
            {"SELECT (SELECT 1 x y)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 19 unexpected 'y'."},
            {"SELECT 1 FROM t WHERE (a = 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'x'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a x)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'."},
            {"SELECT (SELECT 1 FROM t WHERE a x)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 32 unexpected 'x'."},
            {"SELECT 1 FROM t WHERE a = b c",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'c'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a = b c)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 55 unexpected 'c'."},
            {"SELECT (SELECT 1 FROM t WHERE a = b c)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 36 unexpected 'c'."},
            {"SELECT 1 FROM t WHERE a IS 2",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'IS'."},
            {"SELECT 1 FROM t WHERE NOT 2 3",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '3'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE NOT 2 3)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'."},
            {"SELECT (SELECT 1 FROM t WHERE NOT 2 3)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 36 unexpected '3'."},
            {"SELECT 1 FROM t WHERE TRUE 2",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE TRUE 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'."},
            {"SELECT (SELECT 1 FROM t WHERE TRUE 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 35 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a.b 2",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a.b 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'."},
            {"SELECT (SELECT 1 FROM t WHERE a.b 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 34 unexpected '2'."},
            {"SELECT 1 FROM t WHERE ABS(a) 2",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '2'."},
            {"SELECT (SELECT 1 FROM t WHERE ABS(a) 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 37 unexpected '2'."},
            {"SELECT 1 FROM t WHERE 1 2",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'."},
            {"SELECT (SELECT 1 FROM t WHERE 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 32 unexpected '2'."},
            {"SELECT 1 FROM t GROUP BY 1 2",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t GROUP BY 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 54 unexpected '2'."},
            {"SELECT (SELECT 1 FROM t GROUP BY 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 35 unexpected '2'."},
            {"SELECT 1 FROM t ORDER BY a 2",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t ORDER BY a 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 54 unexpected '2'."},
            {"SELECT (SELECT 1 FROM t ORDER BY a 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 35 unexpected '2'."},
            {"SELECT 1 FROM t LIMIT 1 2",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t LIMIT 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 51 unexpected '2'."},
            {"SELECT (SELECT 1 FROM t LIMIT 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 32 unexpected '2'."},
            {"SELECT 1 FROM t u v",
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'v'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t u v)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'."},
            {"SELECT (SELECT 1 FROM t u v)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 26 unexpected 'v'."},
            {"SELECT 1 + 2 3",
                "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '3'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 + 2 3)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 40 unexpected '3'."},
            {"SELECT (SELECT 1 + 2 3)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 21 unexpected '3'."},
            {"SELECT 1 FROM t WHERE a > 1 2",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a > 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 55 unexpected '2'."},
            {"SELECT (SELECT 1 FROM t WHERE a > 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 36 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y = 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 44 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y <> 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 45 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y != 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 45 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y < 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 44 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y >= 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 45 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y + 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 44 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y - 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 44 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y * 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 44 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y / 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 44 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y % 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 44 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y || 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 45 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y AND 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 46 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y OR 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 45 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y LIKE 'a' 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 49 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y ILIKE 'a' 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 50 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y RLIKE 'a' 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 50 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y IN (1) 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 47 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y BETWEEN 1 AND 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 56 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y NOT LIKE 'a' 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 53 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y ::INT 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 46 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y COLLATE 'en' 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 53 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y [0] 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 44 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y :k 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 43 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y .b 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y GROUP BY a 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 51 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y HAVING a = 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 53 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y QUALIFY a = 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 54 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y ORDER BY a 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 51 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y LIMIT 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 48 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y FETCH FIRST 1 ROWS ONLY 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 64 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y UNION SELECT 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 55 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y MINUS SELECT 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 55 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y WINDOW w AS () 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y , 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 44 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y FROM t 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y WHERE a = 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 52 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y AS z 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y JOIN t 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y ON a = 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 49 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y ASC 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y THEN 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y ELSE 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y WHEN 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y END 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y ] 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y REGEXP 'a' 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 51 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y == 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 45 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y INTO 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y ^ 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 44 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y LIKE ANY ('a') 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 55 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y NOT IN (1) 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 51 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y -> 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y MATCH_RECOGNIZE 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y PIVOT 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y EXISTS 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 x y CASE 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'."},
        });
    }

    @Test
    public void nestingAndBrackets() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        assertCells(new String[][] {
            {"SELECT 1 FROM t WHERE a = (SELECT -1 FROM t x y)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 37 unexpected 'FROM'."},
            {"SELECT 1 FROM t WHERE a = (SELECT a, b FROM t x y)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 39 unexpected 'FROM'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a = 1 2) AND a > 0",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 55 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a IN (SELECT 1 FROM t GROUP BY a 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 55 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a IN (SELECT 1 FROM t WHERE a = 1 2) AND a IN (SELECT 1 FROM t WHERE a"
                + " = 1 3)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 56 unexpected '2'. | syntax error line 1 at position 69 "
                + "unexpected 'SELECT'. | syntax error line 1 at position 97 unexpected '3'."},
            {"SELECT 1 FROM t WHERE a IN (SELECT 1 FROM t WHERE a IN (SELECT 1 FROM t WHERE a = 1 2))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 56 unexpected 'SELECT'. | syntax error line 1 at position 84"
                + " unexpected '2'."},
            {"SELECT 1 FROM t WHERE a IN (SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a = 1 2))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 55 unexpected 'SELECT'. | syntax error line 1 at position 83"
                + " unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a IN (SELECT 1 FROM t WHERE a = 1 2))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 55 unexpected 'SELECT'. | syntax error line 1 at position 83"
                + " unexpected '2'."},
            {"SELECT ABS((SELECT 1 FROM t WHERE a = 1 2))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 40 unexpected '2'."},
            {"SELECT ABS((SELECT 1 FROM t WHERE a = 1 2)) FROM t",
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 40 unexpected '2'."},
            {"SELECT COALESCE((SELECT 1 FROM t WHERE a = 1 2), 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 45 unexpected '2'."},
            {"SELECT 1 FROM t WHERE ABS((SELECT 1 FROM t WHERE a = 1 2)) = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 55 unexpected '2'. | syntax error line 1 at position 57 "
                + "unexpected ')'."},
            {"SELECT 1 FROM t WHERE a = ANY (SELECT 1 FROM t WHERE a = 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 59 unexpected '2'."},
            {"SELECT 1 FROM t HAVING a = (SELECT 1 FROM t WHERE a = 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 56 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a = 1 2 3 4)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 55 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a = 1 2) OR a = 3 4",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 55 unexpected '2'. | syntax error line 1 at position 67 "
                + "unexpected '4'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a = 1 x) GROUP BY a",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 55 unexpected 'x'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a = 1 AND b 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 61 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a = 1 ORDER BY a 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 66 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a = 1 LIMIT 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 63 unexpected '2'."},
            {"SELECT 1 FROM t WHERE (a = (SELECT 1 FROM t WHERE a = 1 2))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 56 unexpected '2'. | syntax error line 1 at position 58 "
                + "unexpected ')'."},
            {"SELECT 1 FROM t WHERE NOT a = (SELECT 1 FROM t WHERE a = 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 59 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a =)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a =) x",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a =) AND a = 1 2",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 64 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a IN (SELECT 1 FROM t, t WHERE a = 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 47 unexpected 'WHERE'. | syntax error line 1 at position 59 "
                + "unexpected '2'."},
            {"SELECT 1 FROM t WHERE a IN (SELECT a, 1 x y)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 40 unexpected 'x'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a = 1 2) = (SELECT 1 x y)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 55 unexpected '2'. | syntax error line 1 at position 61 "
                + "unexpected 'SELECT'."},
            {"UPDATE t SET a = (SELECT 1 FROM t WHERE a = 1 2) WHERE a = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 46 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a = 1 2);  SELECT 1 x y",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 55 unexpected '2'. | syntax error line 1 at position 71 "
                + "unexpected 'y'."},
            {"SELECT 1 FROM t WHERE a IN (SELECT 1 FROM t WHERE a = 1 2) OR a IN (1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 56 unexpected '2'. | syntax error line 1 at position 70 "
                + "unexpected '2'."},
            {"SELECT 1 FROM t WHERE a IN ())",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected ')'. | syntax"
                + " error line 1 at position 29 unexpected ')'."},
            {"SELECT 1 FROM t WHERE a IN (1 2))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '2'. | syntax"
                + " error line 1 at position 32 unexpected ')'."},
            {"SELECT 1 FROM t WHERE (a = 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '2'."},
            {"SELECT 1 FROM t WHERE ((a = 1 2))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected '2'."},
            {"SELECT 1 FROM t WHERE a = ((SELECT 1 FROM t WHERE a = 1 2))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 56 unexpected '2'. | syntax error line 1 at position 58 "
                + "unexpected ')'."},
            {"SELECT 1 FROM t WHERE (a = (SELECT 1 x y))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'."},
            {"SELECT 1 FROM t WHERE a IN (SELECT 1 FROM t WHERE a IN ()) AND a = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 56 unexpected ')'. | syntax error line 1 at position 57 "
                + "unexpected ')'."},
            {"SELECT 1 FROM t WHERE a IN (SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a IN ()))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 55 unexpected 'SELECT'. | syntax error line 1 at position 83"
                + " unexpected ')'. | syntax error line 1 at position 84 unexpected ')'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a IN ())",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 55 unexpected ')'. | syntax error line 1 at position 56 "
                + "unexpected ')'."},
            {"SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a IN (1 2))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 57 unexpected '2'. | syntax error line 1 at position 59 "
                + "unexpected ')'."},
            {"SELECT 1 FROM t WHERE a IN (SELECT 1 FROM t WHERE a IN (SELECT 1 FROM t WHERE a IN ()))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 56 unexpected 'SELECT'. | syntax error line 1 at position 84"
                + " unexpected ')'. | syntax error line 1 at position 85 unexpected ')'."},
            {"SELECT 1 FROM t WHERE (a IN ())",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected ')'."},
            {"SELECT 1 FROM t WHERE a = 1 AND (a = (SELECT 1 FROM t WHERE a = 1 2))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 66 unexpected '2'. | syntax error line 1 at position 68 "
                + "unexpected ')'."},
            {"SELECT 1 FROM t WHERE ABS((SELECT 1 FROM t WHERE a = 1 2) + 1) = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 55 unexpected '2'. | syntax error line 1 at position 61 "
                + "unexpected ')'."},
            {"SELECT 1 FROM t WHERE (a = (SELECT 1 FROM t WHERE a = 1 2) AND a = 1)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 56 unexpected '2'. | syntax error line 1 at position 68 "
                + "unexpected ')'."},
        });
    }

    @Test
    public void statementWordFrames() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        assertCells(new String[][] {
            {"SELECT 1 FROM t WHERE EXISTS (SELECT 1 FROM t WHERE a IN (SELECT 1 FROM t, (CALL p())))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 81 unexpected 'p'. | syntax error line 1 at position 83 "
                + "unexpected ')'. | syntax error line 1 at position 86 unexpected ')'."},
            {"SELECT 1 FROM t WHERE EXISTS (SELECT 1 FROM t WHERE a = (SELECT 1 FROM t, (CALL p())))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 80 unexpected 'p'. | syntax error line 1 at position 82 "
                + "unexpected ')'. | syntax error line 1 at position 85 unexpected ')'."},
            {"SELECT 1 FROM t WHERE EXISTS (SELECT 1 FROM t WHERE a IN (SELECT 1 FROM t, (CALL p()))) AND "
                + "a = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 81 unexpected 'p'. | syntax error line 1 at position 83 "
                + "unexpected ')'. | syntax error line 1 at position 86 unexpected ')'."},
            {"SELECT 1 FROM t WHERE a = 1 AND EXISTS (SELECT 1 FROM t WHERE a IN (SELECT 1 FROM t, (CALL "
                + "p())))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 68 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 91 unexpected 'p'. | syntax error line 1 at position 93 "
                + "unexpected ')'. | syntax error line 1 at position 96 unexpected ')'."},
            {"SELECT 1 FROM t WHERE EXISTS (SELECT 1 FROM t WHERE a IN (SELECT 1 FROM t, (DESCRIBE TABLE "
                + "t)))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 85 unexpected 'TABLE'."},
            {"SELECT 1 FROM t WHERE NOT EXISTS (SELECT 1 FROM t WHERE a IN (SELECT 1 FROM t, (CALL p())))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 85 unexpected 'p'. | syntax error line 1 at position 87 "
                + "unexpected ')'. | syntax error line 1 at position 90 unexpected ')'."},
            {"SELECT 1 FROM t WHERE EXISTS (SELECT 1 FROM t WHERE a IN (SELECT 1 FROM t, (CALL p(1))))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 81 unexpected 'p'. | syntax error line 1 at position 83 "
                + "unexpected '1'. | syntax error line 1 at position 86 unexpected ')'."},
            {"SELECT 1 FROM t WHERE EXISTS (SELECT 1 FROM t WHERE a IN (SELECT 1 FROM t WHERE a = 1 2))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 86 unexpected '2'. | syntax error line 1 at position 88 "
                + "unexpected ')'."},
            {"SELECT 1 FROM t WHERE EXISTS (SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a = 1 2))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 85 unexpected '2'. | syntax error line 1 at position 87 "
                + "unexpected ')'."},
            {"SELECT 1 FROM t WHERE EXISTS (SELECT 1 FROM t WHERE a = (SELECT 1 x y))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 57 unexpected 'SELECT'."},
            {"SELECT 1 FROM (SELECT 1 FROM t WHERE a = (SELECT 1 FROM t WHERE a = 1 2))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 70 unexpected '2'."},
            {"SELECT 1 FROM (SELECT 1 FROM t WHERE a IN (SELECT 1 FROM t, (CALL p())))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 61 unexpected 'CALL'."},
            {"SELECT 1 FROM (SELECT 1 FROM t WHERE a = (SELECT 1 FROM t, (CALL p())))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 60 unexpected 'CALL'."},
            {"SELECT 1 FROM (SELECT 1 FROM t WHERE a IN (SELECT 1 FROM t, (CALL p()))) AS d",
                "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 61 unexpected 'CALL'."},
            {"SELECT 1 FROM t, (SELECT 1 FROM t WHERE a IN (SELECT 1 FROM t, (CALL p())))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 64 unexpected 'CALL'."},
            {"SELECT 1 FROM t WHERE ABS((SELECT 1 FROM t WHERE a IN (SELECT 1 FROM t, (CALL p())))) = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 55 unexpected 'SELECT'. | syntax error line 1 at position 78"
                + " unexpected 'p'. | syntax error line 1 at position 80 unexpected ')'. | syntax error line 1 "
                + "at position 83 unexpected ')'."},
        });
    }

    @Test
    public void nestedInLists() {
        engine.execute("CREATE OR REPLACE TABLE rt955 (a INT)");
        assertCells(new String[][] {
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955, (CALL "
                + "p955())))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 64 unexpected 'SELECT'. | syntax error line 1 at position 91"
                + " unexpected 'p955'. | syntax error line 1 at position 96 unexpected ')'. | syntax error line"
                + " 1 at position 98 unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955, (CALL p955()))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 59 unexpected 'p955'. | syntax error line 1 at position 64 "
                + "unexpected ')'. | syntax error line 1 at position 66 unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a = (SELECT 1 FROM rt955, (CALL "
                + "p955())))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 63 unexpected 'SELECT'. | syntax error line 1 at position 90"
                + " unexpected 'p955'. | syntax error line 1 at position 95 unexpected ')'. | syntax error line"
                + " 1 at position 97 unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a "
                + "IN (SELECT 1 FROM rt955, (CALL p955()))))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 64 unexpected 'SELECT'. | syntax error line 1 at position 96"
                + " unexpected 'SELECT'. | syntax error line 1 at position 123 unexpected 'p955'. | syntax "
                + "error line 1 at position 128 unexpected ')'. | syntax error line 1 at position 130 "
                + "unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955, "
                + "(DESCRIBE TABLE rt955)))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 64 unexpected 'SELECT'. | syntax error line 1 at position 95"
                + " unexpected 'TABLE'. | syntax error line 1 at position 108 unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955))",
                ""},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 64 unexpected 'SELECT'. | syntax error line 1 at position 78"
                + " unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955, (CALL "
                + "p955())) AND a > 0)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 64 unexpected 'SELECT'. | syntax error line 1 at position 91"
                + " unexpected 'p955'. | syntax error line 1 at position 96 unexpected ')'. | syntax error line"
                + " 1 at position 98 unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955, (CALL "
                + "p955()))) AND a > 0",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 64 unexpected 'SELECT'. | syntax error line 1 at position 91"
                + " unexpected 'p955'. | syntax error line 1 at position 96 unexpected ')'. | syntax error line"
                + " 1 at position 98 unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a = (SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955, (CALL "
                + "p955())))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 63 unexpected 'SELECT'. | syntax error line 1 at position 90"
                + " unexpected 'p955'. | syntax error line 1 at position 95 unexpected ')'. | syntax error line"
                + " 1 at position 97 unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a "
                + "= (SELECT 1 FROM rt955, (CALL p955()))))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 64 unexpected 'SELECT'. | syntax error line 1 at position 95"
                + " unexpected 'SELECT'. | syntax error line 1 at position 122 unexpected 'p955'. | syntax "
                + "error line 1 at position 127 unexpected ')'. | syntax error line 1 at position 129 "
                + "unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT a FROM rt955 WHERE a IN (SELECT 1 FROM rt955, (CALL "
                + "p955())))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 64 unexpected 'SELECT'. | syntax error line 1 at position 91"
                + " unexpected 'p955'. | syntax error line 1 at position 96 unexpected ')'. | syntax error line"
                + " 1 at position 98 unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 , (CALL "
                + "p955()) , rt955))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 64 unexpected 'SELECT'. | syntax error line 1 at position 92"
                + " unexpected 'p955'. | syntax error line 1 at position 97 unexpected ')'. | syntax error line"
                + " 1 at position 100 unexpected ','."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a IN (SELECT 2 FROM rt955, "
                + "(DESCRIBE TABLE rt955)))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 64 unexpected 'SELECT'. | syntax error line 1 at position 95"
                + " unexpected 'TABLE'. | syntax error line 1 at position 108 unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 r WHERE r.a IN (SELECT 1 FROM rt955, "
                + "(CALL p955())))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 68 unexpected 'SELECT'. | syntax error line 1 at position 95"
                + " unexpected 'p955'. | syntax error line 1 at position 100 unexpected ')'. | syntax error "
                + "line 1 at position 102 unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955, (CALL p955())) AND a IN (SELECT 1 FROM "
                + "rt955, (CALL p955()))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 59 unexpected 'p955'. | syntax error line 1 at position 64 "
                + "unexpected ')'. | syntax error line 1 at position 66 unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a "
                + "= 1 2))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 64 unexpected 'SELECT'. | syntax error line 1 at position 96"
                + " unexpected '2'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a > (SELECT 1 FROM rt955, (CALL "
                + "p955())))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 63 unexpected 'SELECT'. | syntax error line 1 at position 90"
                + " unexpected 'p955'. | syntax error line 1 at position 95 unexpected ')'. | syntax error line"
                + " 1 at position 97 unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a = (SELECT 1 FROM rt955, (CALL p955()))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 58 unexpected 'p955'. | syntax error line 1 at position 63 "
                + "unexpected ')'. | syntax error line 1 at position 65 unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a = (SELECT 1 FROM rt955 WHERE a = (SELECT 1 FROM rt955, (CALL "
                + "p955())))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 62 unexpected 'SELECT'. | syntax error line 1 at position 89"
                + " unexpected 'p955'. | syntax error line 1 at position 94 unexpected ')'. | syntax error line"
                + " 1 at position 96 unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a = (SELECT 1 FROM rt955 WHERE a = (SELECT 1 FROM rt955 WHERE a IN"
                + " (SELECT 1 FROM rt955, (CALL p955()))))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 62 unexpected 'SELECT'. | syntax error line 1 at position 94"
                + " unexpected 'SELECT'. | syntax error line 1 at position 121 unexpected 'p955'. | syntax "
                + "error line 1 at position 126 unexpected ')'. | syntax error line 1 at position 128 "
                + "unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a = (SELECT 1 FROM rt955 WHERE a ="
                + " (SELECT 1 FROM rt955, (CALL p955()))))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 63 unexpected 'SELECT'. | syntax error line 1 at position 94"
                + " unexpected 'SELECT'. | syntax error line 1 at position 121 unexpected 'p955'. | syntax "
                + "error line 1 at position 126 unexpected ')'. | syntax error line 1 at position 128 "
                + "unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a = (SELECT 1 FROM rt955) AND a IN"
                + " (SELECT 1 FROM rt955, (CALL p955())))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 94 unexpected 'SELECT'. | syntax error line 1 at position "
                + "121 unexpected 'p955'. | syntax error line 1 at position 126 unexpected ')'. | syntax error "
                + "line 1 at position 128 unexpected ')'."},
            {"SELECT (SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955, (CALL p955())))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 40 unexpected 'SELECT'. | syntax error line 1 at position 67"
                + " unexpected 'p955'. | syntax error line 1 at position 72 unexpected ')'. | syntax error line"
                + " 1 at position 74 unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE EXISTS (SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955, (CALL"
                + " p955())))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 66 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 93 unexpected 'p955'. | syntax error line 1 at position 98 "
                + "unexpected ')'. | syntax error line 1 at position 101 unexpected ')'."},
            {"SELECT 1 FROM (SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955, (CALL p955())))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 47 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 69 unexpected 'CALL'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a = 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 64 unexpected '2'."},
            {"SELECT 1 FROM rt955 WHERE a = (SELECT 1 FROM rt955 WHERE a = 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 63 unexpected '2'."},
            {"SELECT 1 FROM rt955 WHERE a = (SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a ="
                + " 1 2))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 63 unexpected 'SELECT'. | syntax error line 1 at position 95"
                + " unexpected '2'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a = (SELECT 1 FROM rt955 WHERE a ="
                + " 1 2))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 63 unexpected 'SELECT'. | syntax error line 1 at position 95"
                + " unexpected '2'."},
            {"SELECT 1 FROM rt955 WHERE a = (SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 63 unexpected 'SELECT'. | syntax error line 1 at position 77"
                + " unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a "
                + "= 1 2) AND a > 0)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 64 unexpected 'SELECT'. | syntax error line 1 at position 96"
                + " unexpected '2'. | syntax error line 1 at position 108 unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a IN (1 2))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 66 unexpected '2'. | syntax error line 1 at position 68 "
                + "unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a IN ())",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 64 unexpected ')'. | syntax error line 1 at position 65 "
                + "unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a "
                + "IN (SELECT 1 FROM rt955 WHERE a = 1 2)))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 64 unexpected 'SELECT'. | syntax error line 1 at position 96"
                + " unexpected 'SELECT'. | syntax error line 1 at position 128 unexpected '2'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955) AND a ="
                + " 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 95 unexpected '2'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955, (CALL "
                + "p955())) AND a = 1 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 64 unexpected 'SELECT'. | syntax error line 1 at position 91"
                + " unexpected 'p955'. | syntax error line 1 at position 96 unexpected ')'. | syntax error line"
                + " 1 at position 98 unexpected ')'."},
        });
    }
}
